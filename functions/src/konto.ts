import { ANTWORTEN_JE_ANALYSE, START_GUTHABEN } from "./regeln";

/**
 * Guthaben pro Google-Konto. Gespeichert werden nur Zahlen und Kauf-Tokens, nie Briefe.
 * Ablauf: erst reservieren, dann Gemini fragen, bei Fehlern zurückbuchen.
 */
export interface KontoSpeicher {
  guthaben(uid: string): Promise<number>;
  /** Zieht eine Analyse ab. false, wenn nichts mehr da ist. Neue Konten starten mit dem Startguthaben. */
  analyseReservieren(uid: string): Promise<boolean>;
  zurueckbuchen(uid: string): Promise<void>;
  /** Merkt sich eine bezahlte Analyse, damit die zugehörigen 3 Antworten gratis sind. */
  analyseAnlegen(uid: string, analyseId: string): Promise<void>;
  /**
   * Vor einer Antwort: "frei" (in der Analyse enthalten), "bezahlt" (eine weitere Analyse abgezogen),
   * "leer" (kein Guthaben) oder "unbekannt" (keine solche Analyse für dieses Konto).
   */
  antwortReservieren(uid: string, analyseId: string): Promise<"frei" | "bezahlt" | "leer" | "unbekannt">;
  /** Nach der Antwort: zählt sie oder bucht bei Fehlern zurück. */
  antwortAbschliessen(uid: string, analyseId: string, erfolg: boolean, warBezahlt: boolean): Promise<void>;
  /** Schreibt ein Paket gut. Derselbe Kauf zählt nur einmal; dann null. */
  gutschreiben(uid: string, kaufToken: string, produktId: string, menge: number): Promise<number | null>;
  /** Globale Notbremse gegen Kostenexplosion: true, solange das Tageslimit nicht erreicht ist. */
  tagesKontingent(tag: string, limit: number): Promise<boolean>;
}

interface Konto { guthaben: number }
interface Analyse { bezahlt: number; antworten: number }

/** Für Tests: alles im Arbeitsspeicher. */
export class SpeicherImArbeitsspeicher implements KontoSpeicher {
  konten = new Map<string, Konto>();
  analysen = new Map<string, Analyse>();
  kaeufe = new Set<string>();
  tage = new Map<string, number>();

  private konto(uid: string): Konto {
    let k = this.konten.get(uid);
    if (!k) { k = { guthaben: START_GUTHABEN }; this.konten.set(uid, k); }
    return k;
  }
  async guthaben(uid: string) { return this.konto(uid).guthaben; }
  async analyseReservieren(uid: string) {
    const k = this.konto(uid);
    if (k.guthaben <= 0) return false;
    k.guthaben -= 1;
    return true;
  }
  async zurueckbuchen(uid: string) { this.konto(uid).guthaben += 1; }
  async analyseAnlegen(uid: string, id: string) { this.analysen.set(`${uid}/${id}`, { bezahlt: 1, antworten: 0 }); }
  async antwortReservieren(uid: string, id: string) {
    const a = this.analysen.get(`${uid}/${id}`);
    if (!a) return "unbekannt" as const;
    if (a.antworten < a.bezahlt * ANTWORTEN_JE_ANALYSE) return "frei" as const;
    const k = this.konto(uid);
    if (k.guthaben <= 0) return "leer" as const;
    k.guthaben -= 1;
    a.bezahlt += 1;
    return "bezahlt" as const;
  }
  async antwortAbschliessen(uid: string, id: string, erfolg: boolean, warBezahlt: boolean) {
    const a = this.analysen.get(`${uid}/${id}`);
    if (!a) return;
    if (erfolg) a.antworten += 1;
    else if (warBezahlt) { a.bezahlt -= 1; this.konto(uid).guthaben += 1; }
  }
  async gutschreiben(uid: string, token: string, _produkt: string, menge: number) {
    if (this.kaeufe.has(token)) return null;
    this.kaeufe.add(token);
    const k = this.konto(uid);
    k.guthaben += menge;
    return k.guthaben;
  }
  async tagesKontingent(tag: string, limit: number) {
    const n = (this.tage.get(tag) ?? 0) + 1;
    this.tage.set(tag, n);
    return n <= limit;
  }
}
