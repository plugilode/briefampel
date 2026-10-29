import { describe, expect, it } from "vitest";
import { mistralMitSchluessel } from "../src/mistral";
import { analyseAnfrage, antwortAnfrage, antwortText, pruefeAntwortWunsch, pruefeBrief } from "../src/regeln";

const brief = { text: "Bitte zahlen Sie 50 €.", heute: "28.10.2026", sprache: "es", dateien: [
  { mime: "image/jpeg", data: "QUJD" },
  { mime: "application/pdf", data: "UERG" },
] };

function sse(...zeilen: string[]) {
  const text = zeilen.map((z) => `data: ${z}\n\n`).join("");
  return new Response(new ReadableStream({
    start(c) {
      // absichtlich mitten in einer Zeile geteilt, wie es im Netz passiert
      const mitte = Math.floor(text.length / 2);
      c.enqueue(new TextEncoder().encode(text.slice(0, mitte)));
      c.enqueue(new TextEncoder().encode(text.slice(mitte)));
      c.close();
    },
  }), { status: 200 });
}

describe("Mistral-Aufruf", () => {
  it("schickt Bilder direkt, PDFs über die Texterkennung und den Schlüssel nur im Kopf", async () => {
    const aufrufe: { url: string; body: any; kopf: Record<string, string> }[] = [];
    const abruf = (async (url: string, init: RequestInit) => {
      aufrufe.push({ url, body: JSON.parse(String(init.body)), kopf: init.headers as Record<string, string> });
      if (url.endsWith("/ocr")) return Response.json({ pages: [{ markdown: "# Mahnung\nSeite eins" }] });
      return Response.json({ choices: [{ message: { content: '{"titel":"Reclamación"}' }, finish_reason: "stop" }] });
    }) as typeof fetch;
    const ki = mistralMitSchluessel({ schluessel: () => "geheim", modelle: ["mistral-small-latest"], abruf });
    const r = await ki(analyseAnfrage(pruefeBrief(brief)), false);
    expect(antwortText(await r.json())).toBe('{"titel":"Reclamación"}');

    expect(aufrufe.map((a) => a.url)).toEqual(["https://api.mistral.ai/v1/ocr", "https://api.mistral.ai/v1/chat/completions"]);
    expect(aufrufe[0].body.document.document_url).toBe("data:application/pdf;base64,UERG");
    const chat = aufrufe[1].body;
    expect(chat.model).toBe("mistral-small-latest");
    expect(chat.response_format).toEqual({ type: "json_object" });
    expect(chat.messages[0].role).toBe("system");
    expect(chat.messages[0].content).toContain("auf Spanisch");
    const teile = chat.messages[1].content;
    expect(teile).toContainEqual({ type: "image_url", image_url: "data:image/jpeg;base64,QUJD" });
    expect(teile.some((t: any) => t.type === "text" && t.text.includes("Seite eins"))).toBe(true);
    expect(aufrufe.every((a) => a.kopf.Authorization === "Bearer geheim")).toBe(true);
    expect(aufrufe.map((a) => a.url).join()).not.toContain("geheim");
  });

  it("nimmt bei Überlastung einmal das Ausweichmodell und meldet abgeschnittene Antworten wie Gemini", async () => {
    const modelle: string[] = [];
    const abruf = (async (_url: string, init: RequestInit) => {
      modelle.push(JSON.parse(String(init.body)).model);
      if (modelle.length === 1) return new Response("", { status: 503 });
      return Response.json({ choices: [{ message: { content: "{" }, finish_reason: "length" }] });
    }) as typeof fetch;
    const r = await mistralMitSchluessel({ schluessel: () => "k", modelle: ["klein", "mittel"], abruf })({ contents: [] }, false);
    expect(modelle).toEqual(["klein", "mittel"]);
    expect(((await r.json()) as any).candidates[0].finishReason).toBe("MAX_TOKENS");
  });

  it("gibt Fehler der Texterkennung als Fehlerstatus weiter", async () => {
    const abruf = (async () => new Response("", { status: 422 })) as unknown as typeof fetch;
    const r = await mistralMitSchluessel({ schluessel: () => "k", modelle: ["m"], abruf })(analyseAnfrage(pruefeBrief(brief)), false);
    expect(r.status).toBe(422);
  });

  it("wandelt den Antwort-Strom in Gemini-Ereignisse um, auch wenn Zeilen zerteilt ankommen", async () => {
    const abruf = (async () => sse(
      JSON.stringify({ choices: [{ delta: { role: "assistant", content: "" } }] }),
      JSON.stringify({ choices: [{ delta: { content: "Sehr geehrte " } }] }),
      JSON.stringify({ choices: [{ delta: { content: "Damen und Herren" }, finish_reason: "stop" }] }),
      "[DONE]",
    )) as unknown as typeof fetch;
    const wunsch = pruefeAntwortWunsch({ ...brief, dateien: [], analyseId: "analyse123", analyse: { sprache: "de" }, stichworte: "zahle", ton: "SACHLICH", absender: {} });
    const r = await mistralMitSchluessel({ schluessel: () => "k", modelle: ["m"], abruf })(antwortAnfrage(wunsch), true);
    const text = await r.text();
    const ereignisse = text.split("\n\n").filter(Boolean).map((z) => JSON.parse(z.replace(/^data: /, "")));
    expect(ereignisse.map((e) => antwortText(e)).join("")).toBe("Sehr geehrte Damen und Herren");
    expect(ereignisse.at(-1).candidates[0].finishReason).toBe("STOP");
    expect(text).not.toContain("[DONE]");
  });
});
