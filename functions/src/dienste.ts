import { GoogleAuth } from "google-auth-library";

/** Serverseitige Überlastung bei Google: einmal mit dem Ausweichmodell versuchen, dann aufgeben. */
const UEBERLASTET = new Set([500, 502, 503, 504]);

export type Gemini = (anfrage: unknown, streaming: boolean, signal?: AbortSignal) => Promise<Response>;

export function geminiMitSchluessel(schluessel: () => string, modelle: string[], abruf: typeof fetch = fetch): Gemini {
  return async (anfrage, streaming, signal) => {
    let letzte: Response | undefined;
    for (const [i, modell] of modelle.entries()) {
      const pfad = streaming ? `${modell}:streamGenerateContent?alt=sse` : `${modell}:generateContent`;
      letzte = await abruf(`https://generativelanguage.googleapis.com/v1beta/models/${pfad}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": schluessel() },
        body: JSON.stringify(anfrage),
        signal,
      });
      if (!UEBERLASTET.has(letzte.status) || i === modelle.length - 1) return letzte;
      await letzte.body?.cancel();
    }
    return letzte!;
  };
}

export interface KaufPruefer {
  /** Fragt Google Play, ob der Kauf echt und bezahlt ist. */
  pruefe(produktId: string, token: string): Promise<boolean>;
  /** Markiert den Kauf als eingelöst, damit dasselbe Paket wieder gekauft werden kann. */
  verbrauche(produktId: string, token: string): Promise<void>;
}

/**
 * Prüfung über die Google Play Developer API. Das Dienstkonto der Functions braucht in der Play Console
 * die Berechtigung „Finanzdaten ansehen“ bzw. „Bestellungen verwalten“ (siehe EINRICHTUNG.md).
 */
export class PlayKaufPruefer implements KaufPruefer {
  private readonly auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });
  constructor(private readonly paket: string) {}

  private url(produktId: string, token: string, zusatz = "") {
    return `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(this.paket)}` +
      `/purchases/products/${encodeURIComponent(produktId)}/tokens/${encodeURIComponent(token)}${zusatz}`;
  }

  async pruefe(produktId: string, token: string) {
    const client = await this.auth.getClient();
    try {
      const r = await client.request<{ purchaseState?: number; consumptionState?: number }>({ url: this.url(produktId, token) });
      // 0 = gekauft. Ausstehende (2) oder stornierte (1) Käufe werden nicht gutgeschrieben.
      return r.data.purchaseState === 0;
    } catch {
      return false;
    }
  }

  async verbrauche(produktId: string, token: string) {
    const client = await this.auth.getClient();
    await client.request({ url: this.url(produktId, token, ":consume"), method: "POST" });
  }
}
