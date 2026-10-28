import { describe, expect, it } from "vitest";
import { geminiMitSchluessel } from "../src/dienste";

describe("Gemini-Aufruf", () => {
  it("versucht bei Überlastung genau einmal das Ausweichmodell und schickt den Schlüssel nur im Kopf", async () => {
    const urls: string[] = [];
    const koepfe: Record<string, string>[] = [];
    const abruf = (async (url: string, init: RequestInit) => {
      urls.push(url);
      koepfe.push(init.headers as Record<string, string>);
      return new Response("{}", { status: urls.length === 1 ? 503 : 200 });
    }) as typeof fetch;
    const gemini = geminiMitSchluessel(() => "geheim", ["haupt", "ausweich"], abruf);
    const r = await gemini({}, false);
    expect(r.status).toBe(200);
    expect(urls).toEqual([
      "https://generativelanguage.googleapis.com/v1beta/models/haupt:generateContent",
      "https://generativelanguage.googleapis.com/v1beta/models/ausweich:generateContent",
    ]);
    expect(urls.join()).not.toContain("geheim");
    expect(koepfe[0]["x-goog-api-key"]).toBe("geheim");
  });

  it("gibt die zweite Überlastung weiter, statt endlos zu wiederholen", async () => {
    let n = 0;
    const abruf = (async () => { n++; return new Response("", { status: 503 }); }) as unknown as typeof fetch;
    const r = await geminiMitSchluessel(() => "k", ["a", "b"], abruf)({}, true);
    expect(r.status).toBe(503);
    expect(n).toBe(2);
  });
});
