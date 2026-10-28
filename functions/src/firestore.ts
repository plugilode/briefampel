import type { Firestore } from "firebase-admin/firestore";
import { FieldValue } from "firebase-admin/firestore";
import type { KontoSpeicher } from "./konto";
import { ANTWORTEN_JE_ANALYSE, START_GUTHABEN } from "./regeln";

/**
 * Firestore-Umsetzung. Alle Änderungen am Guthaben laufen in Transaktionen,
 * damit zwei gleichzeitige Prüfungen nie dieselbe Analyse verbrauchen.
 * Sammlungen: konten/{uid}, konten/{uid}/analysen/{id}, kaeufe/{token}, tage/{JJJJMMTT}.
 */
export class FirestoreSpeicher implements KontoSpeicher {
  constructor(private readonly db: Firestore) {}

  private konto(uid: string) { return this.db.collection("konten").doc(uid); }

  async guthaben(uid: string) {
    const d = await this.konto(uid).get();
    return d.exists ? (d.get("guthaben") as number) : START_GUTHABEN;
  }

  async analyseReservieren(uid: string) {
    return this.db.runTransaction(async (tx) => {
      const ref = this.konto(uid);
      const d = await tx.get(ref);
      const g = d.exists ? (d.get("guthaben") as number) : START_GUTHABEN;
      if (g <= 0) return false;
      tx.set(ref, { guthaben: g - 1, geaendert: FieldValue.serverTimestamp() }, { merge: true });
      return true;
    });
  }

  async zurueckbuchen(uid: string) {
    await this.konto(uid).set({ guthaben: FieldValue.increment(1) }, { merge: true });
  }

  async analyseAnlegen(uid: string, id: string) {
    await this.konto(uid).collection("analysen").doc(id).set({ bezahlt: 1, antworten: 0, am: FieldValue.serverTimestamp() });
  }

  async antwortReservieren(uid: string, id: string) {
    return this.db.runTransaction(async (tx) => {
      const aRef = this.konto(uid).collection("analysen").doc(id);
      const kRef = this.konto(uid);
      const [a, k] = await Promise.all([tx.get(aRef), tx.get(kRef)]);
      if (!a.exists) return "unbekannt" as const;
      const bezahlt = a.get("bezahlt") as number;
      const antworten = a.get("antworten") as number;
      if (antworten < bezahlt * ANTWORTEN_JE_ANALYSE) return "frei" as const;
      const g = k.exists ? (k.get("guthaben") as number) : START_GUTHABEN;
      if (g <= 0) return "leer" as const;
      tx.set(kRef, { guthaben: g - 1 }, { merge: true });
      tx.update(aRef, { bezahlt: bezahlt + 1 });
      return "bezahlt" as const;
    });
  }

  async antwortAbschliessen(uid: string, id: string, erfolg: boolean, warBezahlt: boolean) {
    const aRef = this.konto(uid).collection("analysen").doc(id);
    if (erfolg) { await aRef.update({ antworten: FieldValue.increment(1) }); return; }
    if (!warBezahlt) return;
    const batch = this.db.batch();
    batch.update(aRef, { bezahlt: FieldValue.increment(-1) });
    batch.set(this.konto(uid), { guthaben: FieldValue.increment(1) }, { merge: true });
    await batch.commit();
  }

  async gutschreiben(uid: string, token: string, produktId: string, menge: number) {
    return this.db.runTransaction(async (tx) => {
      // Der Token selbst ist geheim genug, aber als Dokument-ID zu lang; deshalb gehasht.
      const kaufRef = this.db.collection("kaeufe").doc(await hash(token));
      const kRef = this.konto(uid);
      const [kauf, k] = await Promise.all([tx.get(kaufRef), tx.get(kRef)]);
      if (kauf.exists) return null;
      const g = (k.exists ? (k.get("guthaben") as number) : START_GUTHABEN) + menge;
      tx.set(kaufRef, { uid, produktId, menge, am: FieldValue.serverTimestamp() });
      tx.set(kRef, { guthaben: g }, { merge: true });
      return g;
    });
  }

  async tagesKontingent(tag: string, limit: number) {
    return this.db.runTransaction(async (tx) => {
      const ref = this.db.collection("tage").doc(tag);
      const d = await tx.get(ref);
      const n = (d.exists ? (d.get("aufrufe") as number) : 0) + 1;
      if (n > limit) return false;
      tx.set(ref, { aufrufe: n }, { merge: true });
      return true;
    });
  }
}

async function hash(text: string): Promise<string> {
  const { createHash } = await import("node:crypto");
  return createHash("sha256").update(text).digest("hex");
}
