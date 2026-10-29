/**
 * Qualitätsvergleich Gemini gegen Mistral mit echten Aufrufen. Läuft nur auf Wunsch:
 *   VERGLEICH=1 npx vitest run test/vergleich.test.ts
 * Die Schlüssel kommen aus ../local.properties (briefampel.geminiKey, briefampel.mistralKey) und werden nie ausgegeben.
 * Eigene Briefe (PDF, JPG, PNG) können in ../briefe-test/ liegen; der Ordner steht in .gitignore.
 */
import { readFileSync, existsSync, readdirSync, writeFileSync } from "node:fs";
import { join, extname } from "node:path";
import { describe, it } from "vitest";
import { geminiMitSchluessel } from "../src/dienste";
import { mistralMitSchluessel } from "../src/mistral";
import { analyseAnfrage, antwortText, pruefeBrief } from "../src/regeln";

const wurzel = join(__dirname, "..", "..");
function einstellung(name: string): string {
  const datei = join(wurzel, "local.properties");
  if (!existsSync(datei)) return "";
  const zeile = readFileSync(datei, "utf8").split(/\r?\n/).find((z) => z.trim().startsWith(name + "="));
  return zeile ? zeile.slice(zeile.indexOf("=") + 1).trim() : "";
}

/** Testbriefe mit erwarteter Ampel. Erfunden, aber typisch für deutsche Post. */
const BRIEFE: { name: string; erwartet: string; text: string }[] = [
  { name: "Mahnbescheid vom Amtsgericht", erwartet: "rot", text: `Amtsgericht Hagen – Zentrales Mahngericht
Mahnbescheid vom 20.10.2026, Geschäftsnummer 26-1234567-0-4
Antragsteller: Inkasso Nordwest GmbH. Hauptforderung 189,90 € aus Mobilfunkvertrag, Kosten 98,50 €.
Das Gericht hat nicht geprüft, ob dem Antragsteller der Anspruch zusteht.
Sie können gegen den Anspruch insgesamt oder teilweise Widerspruch erheben. Wenn Sie nicht innerhalb von zwei Wochen
ab Zustellung Widerspruch erheben, kann der Antragsteller einen Vollstreckungsbescheid beantragen.` },
  { name: "Jobcenter-Einladung", erwartet: "gelb", text: `Jobcenter Köln, Einladung
Sehr geehrte Frau Becker, bitte kommen Sie am 05.11.2026 um 10:30 Uhr zu einem Gespräch über Ihre berufliche Situation.
Bringen Sie Ihren Lebenslauf mit. Wenn Sie ohne wichtigen Grund nicht erscheinen, kann Ihr Bürgergeld um 10 Prozent gemindert werden.` },
  { name: "Stromabschlag-Info", erwartet: "gruen", text: `Stadtwerke Köln. Ihr neuer Abschlag
Guten Tag, ab Januar 2027 beträgt Ihr monatlicher Abschlag 62 € statt 65 €. Sie müssen nichts tun, die Abbuchung passen wir an.
Freundliche Grüße, Ihre Stadtwerke` },
  { name: "Kündigung Wohnung (Eigenbedarf)", erwartet: "rot", text: `Kündigung des Mietverhältnisses
Sehr geehrter Herr Yilmaz, hiermit kündige ich das Mietverhältnis über die Wohnung Lindenstraße 5, 2. OG links, fristgerecht
zum 31.07.2027 wegen Eigenbedarfs für meine Tochter. Sie können der Kündigung bis zum 31.05.2027 schriftlich widersprechen (§ 574 BGB).
Mit freundlichen Grüßen, Klaus Berger` },
  { name: "Krankenkasse fragt nach Einkommen", erwartet: "gelb", text: `AOK Rheinland. Familienversicherung – Überprüfung
Bitte teilen Sie uns bis zum 30.11.2026 das monatliche Einkommen Ihres Sohnes Leon mit. Nutzen Sie dafür den beiliegenden Fragebogen.
Ohne Rückmeldung können wir die Familienversicherung nicht weiterführen.` },
];

const MIME: Record<string, string> = { ".pdf": "application/pdf", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp" };

describe.skipIf(!process.env.VERGLEICH)("Vergleich Gemini – Mistral (echte Aufrufe)", () => {
  it("prüft alle Testbriefe mit beiden Anbietern", async () => {
    const ki: Record<string, ReturnType<typeof mistralMitSchluessel>> = {};
    const g = einstellung("briefampel.geminiKey");
    const m = einstellung("briefampel.mistralKey");
    if (g) ki.gemini = geminiMitSchluessel(() => g, ["gemini-3.5-flash", "gemini-3.5-flash-lite"]);
    if (m) ki.mistral = mistralMitSchluessel({ schluessel: () => m, modelle: ["mistral-small-latest", "mistral-medium-latest"] });
    if (!Object.keys(ki).length) throw new Error("Kein Schlüssel in local.properties gefunden.");

    const faelle = BRIEFE.map((b) => ({ name: b.name, erwartet: b.erwartet, roh: { text: b.text, heute: "28.10.2026" } as Record<string, unknown> }));
    const ordner = join(wurzel, "briefe-test");
    if (existsSync(ordner)) for (const f of readdirSync(ordner)) {
      const mime = MIME[extname(f).toLowerCase()];
      if (mime) faelle.push({ name: f, erwartet: "?", roh: { text: "", heute: "28.10.2026", dateien: [{ mime, data: readFileSync(join(ordner, f)).toString("base64") }] } });
    }

    const zeilen: string[] = ["| Brief | erwartet | Anbieter | Ampel | Sekunden | Titel | Frist |", "|---|---|---|---|---|---|---|"];
    for (const fall of faelle) for (const [name, aufruf] of Object.entries(ki)) {
      const start = Date.now();
      let ampel = "–", titel = "", frist = "";
      try {
        const r = await aufruf(analyseAnfrage(pruefeBrief(fall.roh)), false);
        if (!r.ok) titel = `Fehler HTTP ${r.status}`;
        else {
          const text = antwortText(await r.json());
          const j = JSON.parse(text.slice(text.indexOf("{"), text.lastIndexOf("}") + 1));
          ampel = j.ampel; titel = j.titel; frist = j.frist?.datum ?? "";
        }
      } catch (e) { titel = `Fehler: ${(e as Error).message}`; }
      const ok = fall.erwartet === "?" ? "" : ampel === fall.erwartet ? " ✅" : " ❌";
      zeilen.push(`| ${fall.name} | ${fall.erwartet} | ${name} | ${ampel}${ok} | ${((Date.now() - start) / 1000).toFixed(1)} | ${titel} | ${frist} |`);
    }
    const bericht = zeilen.join("\n");
    writeFileSync(join(wurzel, "briefe-test-ergebnis.md"), bericht + "\n");
    console.log("\n" + bericht);
  }, 600_000);
});
