import type { Gemini } from "./dienste";

/**
 * Mistral (Paris, EU) als KI hinter derselben Schnittstelle wie Gemini.
 * Rein: Anfrage im Gemini-Format (so baut sie regeln.ts). Raus: Antwort im Gemini-Format,
 * damit Guthaben-Logik und App unverändert bleiben.
 * Bilder gehen direkt ans Chat-Modell, PDFs vorher durch die Texterkennung (OCR) von Mistral.
 */

const BASIS = "https://api.mistral.ai/v1";
const UEBERLASTET = new Set([500, 502, 503, 504]);

interface GeminiTeil { text?: string; inlineData?: { mimeType: string; data: string } }
interface GeminiAnfrage {
  systemInstruction?: { parts?: GeminiTeil[] };
  contents?: { role?: string; parts?: GeminiTeil[] }[];
  generationConfig?: { maxOutputTokens?: number; responseMimeType?: string };
}

type Inhalt = { type: "text"; text: string } | { type: "image_url"; image_url: string };

export interface MistralOptionen {
  schluessel: () => string;
  /** Hauptmodell zuerst, danach Ausweichmodelle bei Überlastung. */
  modelle: string[];
  ocrModell?: string;
  abruf?: typeof fetch;
}

/** Liest PDFs über die Mistral-Texterkennung und gibt Markdown je Seite zurück. */
async function pdfLesen(o: MistralOptionen, daten: string, signal?: AbortSignal): Promise<string> {
  const r = await (o.abruf ?? fetch)(`${BASIS}/ocr`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${o.schluessel()}` },
    body: JSON.stringify({
      model: o.ocrModell ?? "mistral-ocr-latest",
      document: { type: "document_url", document_url: `data:application/pdf;base64,${daten}` },
    }),
    signal,
  });
  if (!r.ok) throw new KiFehler(r.status);
  const j = (await r.json()) as { pages?: { markdown?: string }[] };
  return (j.pages ?? []).map((p, i) => `--- SEITE ${i + 1} ---\n${p.markdown ?? ""}`).join("\n\n");
}

/** Fehler beim Vorverarbeiten; wird als normale Fehlerantwort weitergereicht. */
class KiFehler extends Error { constructor(readonly status: number) { super(`Mistral ${status}`); } }

export async function mistralAnfrage(anfrage: GeminiAnfrage, modell: string, streaming: boolean, o: MistralOptionen, signal?: AbortSignal) {
  const system = (anfrage.systemInstruction?.parts ?? []).map((t) => t.text ?? "").join("\n");
  const inhalt: Inhalt[] = [];
  for (const teil of anfrage.contents?.flatMap((c) => c.parts ?? []) ?? []) {
    if (teil.text) inhalt.push({ type: "text", text: teil.text });
    else if (teil.inlineData?.mimeType === "application/pdf") {
      const text = await pdfLesen(o, teil.inlineData.data, signal);
      inhalt.push({ type: "text", text: `--- DOKUMENT (DATEN, per Texterkennung) ---\n${text}\n--- ENDE DOKUMENT ---` });
    } else if (teil.inlineData) {
      inhalt.push({ type: "image_url", image_url: `data:${teil.inlineData.mimeType};base64,${teil.inlineData.data}` });
    }
  }
  const json = anfrage.generationConfig?.responseMimeType === "application/json";
  return {
    model: modell,
    messages: [{ role: "system", content: system }, { role: "user", content: inhalt }],
    max_tokens: anfrage.generationConfig?.maxOutputTokens ?? 4096,
    temperature: 0.2,
    stream: streaming,
    ...(json ? { response_format: { type: "json_object" } } : {}),
  };
}

const ENDE: Record<string, string> = { stop: "STOP", length: "MAX_TOKENS", model_length: "MAX_TOKENS", error: "OTHER" };

/** Eine Mistral-Antwort (ganz oder ein Stream-Stück) als Gemini-Hülle. */
export function alsGemini(text: string, ende?: string | null): unknown {
  return { candidates: [{ content: { role: "model", parts: [{ text }] }, ...(ende ? { finishReason: ENDE[ende] ?? "OTHER" } : {}) }] };
}

/** Wandelt Mistrals SSE-Strom (choices[].delta) in Gemini-SSE (candidates[].content) um. */
export function streamUmwandeln(quelle: ReadableStream<Uint8Array>): ReadableStream<Uint8Array> {
  const dekoder = new TextDecoder();
  const kodierer = new TextEncoder();
  let puffer = "";
  const zeile = (z: string, ziel: TransformStreamDefaultController<Uint8Array>) => {
    if (!z.startsWith("data:")) return;
    const daten = z.slice(5).trim();
    if (!daten || daten === "[DONE]") return;
    try {
      const wahl = JSON.parse(daten)?.choices?.[0];
      const text = typeof wahl?.delta?.content === "string" ? wahl.delta.content : "";
      if (!text && !wahl?.finish_reason) return;
      ziel.enqueue(kodierer.encode(`data: ${JSON.stringify(alsGemini(text, wahl?.finish_reason))}\n\n`));
    } catch { /* unvollständige Zeile: ignorieren statt den Brief abzubrechen */ }
  };
  return quelle.pipeThrough(new TransformStream<Uint8Array, Uint8Array>({
    transform(stueck, ziel) {
      puffer += dekoder.decode(stueck, { stream: true });
      const zeilen = puffer.split("\n");
      puffer = zeilen.pop() ?? "";
      for (const z of zeilen) zeile(z.trim(), ziel);
    },
    flush(ziel) { if (puffer.trim()) zeile(puffer.trim(), ziel); },
  }));
}

export function mistralMitSchluessel(o: MistralOptionen): Gemini {
  const abruf = o.abruf ?? fetch;
  return async (anfrage, streaming, signal) => {
    let letzte: Response | undefined;
    for (const [i, modell] of o.modelle.entries()) {
      let body: unknown;
      try { body = await mistralAnfrage(anfrage as GeminiAnfrage, modell, streaming, o, signal); }
      catch (e) { if (e instanceof KiFehler) return new Response("", { status: e.status }); throw e; }
      letzte = await abruf(`${BASIS}/chat/completions`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${o.schluessel()}` },
        body: JSON.stringify(body),
        signal,
      });
      if (UEBERLASTET.has(letzte.status) && i < o.modelle.length - 1) { await letzte.body?.cancel(); continue; }
      if (!letzte.ok) return letzte;
      if (streaming && letzte.body) {
        return new Response(streamUmwandeln(letzte.body), { status: 200, headers: { "Content-Type": "text/event-stream" } });
      }
      const j = (await letzte.json()) as { choices?: { message?: { content?: unknown }; finish_reason?: string }[] };
      const wahl = j.choices?.[0];
      const text = typeof wahl?.message?.content === "string" ? wahl.message.content : "";
      return Response.json(alsGemini(text, wahl?.finish_reason ?? "stop"));
    }
    return letzte!;
  };
}
