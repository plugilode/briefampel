import type { Gemini, KaufPruefer } from "./dienste";
import type { KontoSpeicher } from "./konto";
import {
  Eingabefehler, PAKETE, analyseAnfrage, antwortAnfrage, antwortText, istBrauchbareAnalyse, pruefeAntwortWunsch, pruefeBrief,
} from "./regeln";

export interface Abhaengigkeiten {
  speicher: KontoSpeicher;
  gemini: Gemini;
  kauf: KaufPruefer;
  /** Höchstzahl Gemini-Aufrufe pro Tag über alle Nutzer, als Notbremse gegen Kostenexplosion. */
  tageslimit: number;
  tag: () => string;
  neueId: () => string;
}

export interface Ergebnis { status: number; json: Record<string, unknown> }

/** Fehlertexte, die die App direkt anzeigen kann. Nie Gemini-Rohtexte oder Briefinhalte zurückgeben. */
const fehler = (status: number, code: string, text: string): Ergebnis => ({ status, json: { fehler: code, text } });
const ueberlastet = fehler(503, "ueberlastet", "Gemini ist gerade überlastet. Bitte in ein paar Sekunden nochmal versuchen.");
const tagesgrenze = fehler(503, "tagesgrenze", "Die Briefampel hat heute sehr viel zu tun. Bitte später nochmal versuchen.");
const leer = fehler(402, "guthaben_leer", "Dein Guthaben ist aufgebraucht.");

function geminiFehler(status: number): Ergebnis {
  if (status === 429) return fehler(429, "zu_viele", "Gerade zu viele Anfragen. Bitte gleich nochmal versuchen.");
  if (status === 400) return fehler(422, "abgelehnt", "Gemini konnte diesen Brief nicht verarbeiten. Bitte Datei prüfen.");
  if (status >= 500) return ueberlastet;
  return fehler(502, "ki_fehler", "Etwas ist schiefgelaufen. Bitte nochmal versuchen.");
}

function eingabe<T>(f: () => T): T | Ergebnis {
  try { return f(); } catch (e) {
    if (e instanceof Eingabefehler) return fehler(400, "eingabe", e.message);
    throw e;
  }
}
const istErgebnis = (x: unknown): x is Ergebnis => typeof x === "object" && x !== null && "status" in x && "json" in x;

/** Brief prüfen: 1 Analyse reservieren, Gemini fragen, nur bei brauchbarem Ergebnis behalten. */
export async function pruefen(d: Abhaengigkeiten, uid: string, body: unknown): Promise<Ergebnis> {
  const brief = eingabe(() => pruefeBrief(body));
  if (istErgebnis(brief)) return brief;
  if (!(await d.speicher.tagesKontingent(d.tag(), d.tageslimit))) return tagesgrenze;
  if (!(await d.speicher.analyseReservieren(uid))) return { ...leer, json: { ...leer.json, guthaben: 0 } };
  try {
    const r = await d.gemini(analyseAnfrage(brief), false);
    if (!r.ok) { await d.speicher.zurueckbuchen(uid); return geminiFehler(r.status); }
    const antwort = (await r.json()) as Record<string, unknown>;
    if (!istBrauchbareAnalyse(antwortText(antwort))) {
      await d.speicher.zurueckbuchen(uid);
      return fehler(502, "unvollstaendig", "Antwort war unvollständig. Bitte nochmal prüfen.");
    }
    const analyseId = d.neueId();
    await d.speicher.analyseAnlegen(uid, analyseId);
    return { status: 200, json: { ...antwort, briefampel: { analyseId, guthaben: await d.speicher.guthaben(uid) } } };
  } catch (e) {
    await d.speicher.zurueckbuchen(uid);
    throw e;
  }
}

export interface Strom {
  /** Wird genau einmal aufgerufen, bevor der erste Teil gesendet wird (Status 200, text/event-stream). */
  beginnen(): void;
  schreiben(teil: string): void;
}

/**
 * Antwort schreiben: Die ersten 3 Antworten je Analyse sind enthalten, jede weitere kostet eine Analyse.
 * Gemini streamt im SSE-Format durch; am Ende folgt ein Ereignis mit dem neuen Guthaben.
 * Ein vom Nutzer gestoppter Entwurf mit Text zählt als Antwort, ein leerer nicht.
 */
export async function antworten(d: Abhaengigkeiten, uid: string, body: unknown, strom: Strom, signal?: AbortSignal): Promise<Ergebnis | null> {
  const wunsch = eingabe(() => pruefeAntwortWunsch(body));
  if (istErgebnis(wunsch)) return wunsch;
  if (!(await d.speicher.tagesKontingent(d.tag(), d.tageslimit))) return tagesgrenze;
  const art = await d.speicher.antwortReservieren(uid, wunsch.analyseId);
  if (art === "unbekannt") return fehler(404, "unbekannt", "Diese Analyse kenne ich nicht. Bitte den Brief neu prüfen.");
  if (art === "leer") return { ...leer, json: { ...leer.json, guthaben: 0 } };
  const bezahlt = art === "bezahlt";
  let mitText = false;
  try {
    const r = await d.gemini(antwortAnfrage(wunsch), true, signal);
    if (!r.ok || !r.body) {
      await d.speicher.antwortAbschliessen(uid, wunsch.analyseId, false, bezahlt);
      return geminiFehler(r.status);
    }
    strom.beginnen();
    const leser = r.body.getReader();
    const dekoder = new TextDecoder();
    for (;;) {
      const { done, value } = await leser.read();
      if (done) break;
      const teil = dekoder.decode(value, { stream: true });
      if (teil.includes("\"text\"")) mitText = true;
      strom.schreiben(teil);
    }
  } catch (e) {
    if (!signal?.aborted) {
      await d.speicher.antwortAbschliessen(uid, wunsch.analyseId, mitText, bezahlt);
      throw e;
    }
  }
  await d.speicher.antwortAbschliessen(uid, wunsch.analyseId, mitText, bezahlt);
  if (!signal?.aborted) strom.schreiben(`\n\ndata: ${JSON.stringify({ briefampel: { guthaben: await d.speicher.guthaben(uid) } })}\n\n`);
  return null;
}

/** Kauf einlösen: bei Google Play prüfen, einmalig gutschreiben, dann als eingelöst markieren. */
export async function kaufen(d: Abhaengigkeiten, uid: string, body: unknown): Promise<Ergebnis> {
  const o = (body ?? {}) as Record<string, unknown>;
  const produktId = typeof o.produktId === "string" ? o.produktId : "";
  const token = typeof o.token === "string" ? o.token : "";
  const menge = PAKETE[produktId];
  if (!menge || !token || token.length > 4096) return fehler(400, "eingabe", "Unbekanntes Paket.");
  if (!(await d.kauf.pruefe(produktId, token))) return fehler(402, "kauf_ungueltig", "Google Play hat den Kauf nicht bestätigt.");
  const neu = await d.speicher.gutschreiben(uid, token, produktId, menge);
  try { await d.kauf.verbrauche(produktId, token); } catch { /* bereits gutgeschrieben; beim nächsten Versuch erneut */ }
  return { status: 200, json: { guthaben: neu ?? (await d.speicher.guthaben(uid)), gutgeschrieben: neu !== null } };
}

export async function guthaben(d: Abhaengigkeiten, uid: string): Promise<Ergebnis> {
  return { status: 200, json: { guthaben: await d.speicher.guthaben(uid) } };
}
