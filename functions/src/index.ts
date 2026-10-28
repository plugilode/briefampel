import { randomUUID } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAppCheck } from "firebase-admin/app-check";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { defineInt, defineSecret, defineString } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";
import { Abhaengigkeiten, antworten, guthaben, kaufen, pruefen } from "./api";
import { PlayKaufPruefer, geminiMitSchluessel } from "./dienste";
import { FirestoreSpeicher } from "./firestore";

initializeApp();

const GEMINI_SCHLUESSEL = defineSecret("GEMINI_SCHLUESSEL");
const MODELLE = defineString("BRIEFAMPEL_MODELLE", { default: "gemini-3.8-flash,gemini-3.5-flash-lite" });
const TAGESLIMIT = defineInt("BRIEFAMPEL_TAGESLIMIT", { default: 2000 });
const PAKETNAME = defineString("BRIEFAMPEL_PAKET", { default: "de.briefampel.app" });
const APPCHECK = defineString("BRIEFAMPEL_APPCHECK", { default: "pflicht" });

let abhaengigkeiten: Abhaengigkeiten | undefined;
function deps(): Abhaengigkeiten {
  abhaengigkeiten ??= {
    speicher: new FirestoreSpeicher(getFirestore()),
    gemini: geminiMitSchluessel(() => GEMINI_SCHLUESSEL.value(), MODELLE.value().split(",").map((m) => m.trim()).filter(Boolean)),
    kauf: new PlayKaufPruefer(PAKETNAME.value()),
    tageslimit: TAGESLIMIT.value(),
    tag: () => new Date().toISOString().slice(0, 10).replace(/-/g, ""),
    neueId: () => randomUUID().replace(/-/g, ""),
  };
  return abhaengigkeiten;
}

class NichtAngemeldet extends Error {}

/** Nur angemeldete Google-Konten aus der echten Briefampel-App (App Check) kommen durch. */
async function anmelden(kopf: (n: string) => string | undefined): Promise<string> {
  if (APPCHECK.value() !== "aus") {
    const appCheck = kopf("x-firebase-appcheck");
    if (!appCheck) throw new NichtAngemeldet();
    try { await getAppCheck().verifyToken(appCheck); } catch { throw new NichtAngemeldet(); }
  }
  const bearer = kopf("authorization")?.match(/^Bearer (.+)$/)?.[1];
  if (!bearer) throw new NichtAngemeldet();
  try {
    const token = await getAuth().verifyIdToken(bearer);
    if (token.firebase?.sign_in_provider === "anonymous") throw new NichtAngemeldet();
    return token.uid;
  } catch {
    throw new NichtAngemeldet();
  }
}

/**
 * Eine einzige HTTP-Funktion in Frankfurt mit vier Wegen:
 * POST /pruefen, POST /antworten (Stream), POST /kauf, GET /guthaben.
 * Es wird nie ein Briefinhalt gespeichert oder protokolliert, nur Weg, Status und Dauer.
 */
export const api = onRequest(
  { region: "europe-west3", secrets: [GEMINI_SCHLUESSEL], timeoutSeconds: 180, memory: "512MiB", maxInstances: 20, concurrency: 40 },
  async (req, res) => {
    const start = Date.now();
    const weg = req.path.replace(/\/+$/, "");
    try {
      const uid = await anmelden((n) => req.get(n));
      const d = deps();
      if (req.method === "GET" && weg === "/guthaben") {
        const e = await guthaben(d, uid);
        res.status(e.status).json(e.json);
      } else if (req.method === "POST" && weg === "/pruefen") {
        const e = await pruefen(d, uid, req.body);
        res.status(e.status).json(e.json);
      } else if (req.method === "POST" && weg === "/antworten") {
        const abbruch = new AbortController();
        req.on("close", () => { if (!res.writableEnded) abbruch.abort(); });
        const e = await antworten(d, uid, req.body, {
          beginnen: () => {
            res.status(200).set({ "Content-Type": "text/event-stream", "Cache-Control": "no-cache", "X-Accel-Buffering": "no" });
            res.flushHeaders();
          },
          schreiben: (teil) => { res.write(teil); },
        }, abbruch.signal);
        if (e) res.status(e.status).json(e.json); else res.end();
      } else if (req.method === "POST" && weg === "/kauf") {
        const e = await kaufen(d, uid, req.body);
        res.status(e.status).json(e.json);
      } else {
        res.status(404).json({ fehler: "unbekannt", text: "Unbekannter Weg." });
      }
    } catch (e) {
      if (e instanceof NichtAngemeldet) {
        res.status(401).json({ fehler: "anmeldung", text: "Bitte mit Google anmelden." });
      } else {
        logger.error("Fehler", { weg, art: e instanceof Error ? e.name : typeof e });
        if (!res.headersSent) res.status(500).json({ fehler: "intern", text: "Etwas ist schiefgelaufen. Bitte nochmal versuchen." });
        else res.end();
      }
    } finally {
      logger.info("Anfrage", { weg, status: res.statusCode, ms: Date.now() - start });
    }
  },
);
