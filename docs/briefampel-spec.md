# Briefampel – Spezifikation für die Umsetzung

> Mini-App / Modul: Nutzer lädt einen Brief hoch (PDF, Foto, Screenshot oder eingefügter Text). Die App sagt per Ampel, ob der Brief harmlos oder unangenehm ist, fasst ihn in einfachen Worten zusammen, damit man ihn **nicht selbst lesen muss**, und schreibt auf Wunsch aus Stichworten eine fertige Antwort.

Ein funktionsfähiger HTML-Prototyp liegt bei (`briefampel.html`). Er nutzt die Claude-Artifact-Runtime (`window.claude.use("sample")`) und dient nur als **Referenz für UI, Ablauf und Prompts**. In der echten App wird der LLM-Aufruf über ein eigenes Backend gemacht (siehe Abschnitt 4).

---

## 1. Ziel und Abgrenzung

- **Ziel:** Angst und Aufwand beim Öffnen von Post nehmen. Der Nutzer sieht zuerst nur die Ampel und eine ruhige Zusammenfassung.
- **Zielgruppe:** Privatpersonen und Selbstvertreter, später auch Kanzleien (passt zur App „Virtuelle Rechtsassistenz“).
- **Keine Rechtsberatung (RDG):** Die App sortiert, fasst zusammen und entwirft. Hinweis dauerhaft sichtbar im Footer:
  > „Die Briefampel sortiert und entwirft. Sie ersetzt keine Rechtsberatung (RDG). Fristen immer selbst gegenprüfen, bevor du dich darauf verlässt.“
- **Sprache der UI:** Deutsch. Antwortschreiben in der Sprache des eingegangenen Briefs.

---

## 2. Ablauf (User Flow)

1. **Brief rein**
   - Drag & Drop oder Dateiauswahl: PDF, PNG, JPEG, WebP, mehrere Dateien möglich (mehrere Seiten).
   - Einfügen per Zwischenablage (Screenshot) unterstützen.
   - Alternativ: Text einfügen (E-Mail, Fax-Text) in ein einklappbares Textfeld.
   - Jede Datei erscheint als Chip mit „×“ zum Entfernen.
   - Button **„Ampel prüfen“**.
2. **Ergebnis (Ampel-Karte)**
   - Große Ampel (rot / gelb / grün leuchtet), Pill-Label, Titel, Metazeile (Absender · Datum · Az.), 2–3 Sätze Kurzfassung.
   - Faktenliste: *Was wollen sie*, *Frist*, *Ton*, *Risiko*, *Empfehlung*.
   - Frist wird **rot hervorgehoben**, wenn weniger als 7 Tage bis zum Datum.
   - Volltext des Briefs **versteckt** hinter „Brief doch komplett lesen“ (aufklappbar).
3. **„Soll ich antworten?“**
   - Vorschlags-Chips (vom Modell geliefert). Antippen fügt sie ins Stichwortfeld ein / entfernt sie wieder.
   - Freitextfeld „Deine Stichworte“.
   - Ton-Auswahl (Segmented Control): **Freundlich · Sachlich (Default) · Bestimmt · Scharf**.
   - Absenderdaten (Name, Anschrift, E-Mail), einklappbar, lokal gespeichert.
   - Button **„Antwort schreiben“** → Text streamt live in die Vorschau. **Stopp**-Button während des Streamens.
   - Danach: **Kopieren**, **Neu formulieren** (gleiche Eingaben, neuer Aufruf ohne Cache). Später: **Als PDF**, **Per Fax senden** (Anbindung an vorhandenes Fax-Modul der Hauptapp).
   - Wenn `antwort_noetig = false`: Überschrift wird zu „Antwort nicht nötig – trotzdem eine?“.

Beim ersten Öffnen zeigt die App ein **Beispielergebnis** (deutlich als „Beispiel“ markiert), damit klar ist, was passiert. „Antwort schreiben“ ist beim Beispiel gesperrt mit Hinweis „Lade zuerst deinen eigenen Brief hoch.“

---

## 3. Dokumentverarbeitung

| Eingabe | Verarbeitung |
|---|---|
| PDF mit Textebene | Text mit pdf.js (Web) bzw. PdfBox / PDFium (Android/Backend) extrahieren, max. 12 Seiten |
| PDF ohne Textebene (Scan) | Erkennen: weniger als ~150 Zeichen pro Seite → Seiten 1–5 als JPEG rendern (Scale ~1,7, Qualität 0,85) und als Bilder an das Modell senden |
| Fotos / Screenshots | Direkt als Bilder senden (vorher auf ca. 1,2 MP verkleinern, EXIF-Rotation anwenden, Metadaten entfernen) |
| Eingefügter Text | Direkt verwenden |

- Text auf ca. 60.000 Zeichen kappen.
- Bildanzahl auf das Limit des Modells begrenzen.
- Optional später: eigene OCR (z. B. Tesseract / ML Kit) statt Bildversand, um Kosten zu sparen.

---

## 4. LLM-Anbindung

- Aufrufe laufen über ein **eigenes Backend** (API-Key nie im Client).
- **Provider-Abstraktion**, damit Gemini (aktuell bevorzugt zum Testen), Claude oder ein feingetuntes Mistral austauschbar sind:

```ts
interface LlmProvider {
  analyzeLetter(input: { text?: string; images?: Blob[]; today: string }): Promise<LetterAnalysis>;
  draftReply(input: DraftInput, onToken: (fullTextSoFar: string) => void, signal: AbortSignal): Promise<string>;
}
```

- **Analyse:** JSON-Modus / Structured Output des Providers verwenden. Antwort tolerant parsen (ganzer Text → Codeblock → erstes `{` bis letztes `}`); Felder nach dem Parsen prüfen.
- **Antwortschreiben:** Streaming (SSE) an den Client.
- Analyse-Ergebnis für denselben Brief 5 Minuten cachen; „Neu formulieren“ nie cachen.

### 4.1 Datenmodell `LetterAnalysis`

```json
{
  "ampel": "gruen | gelb | rot",
  "titel": "string",
  "absender": "string | null",
  "datum": "TT.MM.JJJJ | null",
  "aktenzeichen": "string | null",
  "kurz": "string (2-3 Sätze)",
  "verlangt": "string | null",
  "frist": { "datum": "TT.MM.JJJJ | null", "hinweis": "string" },
  "ton": "freundlich | neutral | fordernd | drohend | feindselig",
  "tonnotiz": "string",
  "risiko": "string",
  "antwort_noetig": true,
  "empfehlung": "string",
  "stichwort_vorschlaege": ["string"],
  "sprache": "de | en | es | ..."
}
```

Unbekannter `ampel`-Wert → als `gelb` behandeln.

### 4.2 Prompt: Analyse

```
Du bist eine erfahrene deutsche Rechtsanwaltsfachangestellte. Ein Mandant hat einen Brief bekommen und will NICHT lesen, ob er unangenehm ist. Prüfe den Brief ({Bilder und/oder extrahierter Text}). Heute ist der {TT.MM.JJJJ}.

Ampel:
- "gruen": harmlos, freundlich oder reine Info; kann man entspannt lesen, keine Handlung nötig.
- "gelb": Handlung oder Antwort nötig, aber sachlich/neutral im Ton.
- "rot": unangenehm, fordernd, drohend oder feindselig ODER gefährlich (Klage, Mahnbescheid, Vollstreckung, Kündigung, Bescheid mit Rechtsbehelfsfrist, Strafanzeige, Fristsetzung mit Konsequenz).

Beschreibe in einfacher, ruhiger Sprache, damit der Mandant den Brief nicht selbst lesen muss. Erfinde nichts; was nicht im Brief steht ist null. Bei Fristen: nenne das Datum, wenn es im Brief steht oder sich sicher aus Zustellung + gesetzlicher Frist ergibt (z. B. 1 Monat Widerspruch, 2 Wochen Einspruch gegen Mahnbescheid/Vollstreckungsbescheid); sonst null und im hinweis sagen, was fehlt.

Antworte NUR mit JSON in genau dieser Form:
{ ...Schema aus 4.1... }

--- BRIEFTEXT ---
{text}
```

### 4.3 Prompt: Antwortschreiben

```
Du bist eine erfahrene deutsche Rechtsanwaltsfachangestellte und schreibst für den Mandanten (der selbst schreibt, ohne Anwalt) eine Antwort auf den Brief unten. Heute ist der {TT.MM.JJJJ}.

Ton: {freundlich|sachlich|bestimmt|scharf}. Sprache: die Sprache des eingegangenen Briefs ({sprache}).
Was der Mandant sagen will (Stichworte, nur diese Punkte, nichts dazuerfinden):
{stichworte}

Absender: {Name / Anschrift / E-Mail oder [Platzhalter]}

Regeln:
- Fertiger Brief: Absender, Empfänger (aus dem Brief), Ort/Datum, Betreff mit Aktenzeichen/Zeichen des Briefs, Anrede, Text, Grußformel, Name.
- Fehlende Angaben als [Platzhalter in eckigen Klammern], keine erfundenen Tatsachen, Daten oder Beträge.
- Normen nur nennen, wenn sicher passend. Keine Zugeständnisse oder Anerkenntnisse, die nicht in den Stichworten stehen.
- Bei "scharf": deutlich, aber ohne Beleidigung.
- Gib NUR den Brief aus, ohne Vorbemerkung.

Zusammenfassung des Briefs: {LetterAnalysis als JSON}
--- BRIEFTEXT ---
{text}
```

Wenn kein Text extrahierbar war (Scan/Foto), die Bilder erneut mitsenden.

---

## 5. Design

- **Layout:** eine schmale Spalte (max. 680 px), mobil zuerst, wie ein Sortiertisch: oben Brief rein, darunter Ampel-Karte, darunter Antwort-Werkbank.
- **Schriften:** Bricolage Grotesque (Überschriften), Source Sans 3 (Fließtext), IBM Plex Mono (Daten, Fristen, Aktenzeichen).
- **Farben (Light):** Hintergrund `#eef0f3`, Karten `#ffffff`, Text `#18212e`, Akzent `#2b4bd6`. Ampel: Rot `#d33a2c`, Gelb `#c77d00`, Grün `#1f8a4c` (jeweils mit hellem Pill-Hintergrund).
- **Dark Mode** vollständig (Werte im Prototyp unter `prefers-color-scheme: dark`).
- Ampelfarben sind Semantik, nicht Akzent. Aktive Lampe leuchtet mit `box-shadow`-Glow, inaktive grau.
- Touch-Ziele ≥ 44 px, sichtbarer Fokus, `prefers-reduced-motion` respektieren.
- Kann in die Hauptapp „Virtuelle Rechtsassistenz“ als eigener Screen / Schnellaktion „Brief prüfen“ eingebunden werden; dann deren Design-Tokens übernehmen.

---

## 6. Fehlertexte

| Fall | Text |
|---|---|
| Nichts hochgeladen | Erst einen Brief hochladen oder Text einfügen. |
| Keine Stichworte | Ein paar Stichworte brauche ich. |
| Rate Limit | Gerade zu viele Anfragen. Bitte gleich nochmal versuchen. |
| Datei unlesbar | Eine Datei konnte nicht gelesen werden. Andere Datei probieren. |
| Zu lang | Der Brief ist zu lang. Weniger Seiten hochladen. |
| JSON kaputt | Antwort war unvollständig. Bitte nochmal prüfen. |
| Abgeschnitten | Wurde abgeschnitten. Weniger Stichworte probieren. |
| Sonst | Etwas ist schiefgelaufen. Bitte nochmal versuchen. |

Niemals automatisch in einer Schleife wiederholen; der Nutzer entscheidet.

---

## 7. Datenschutz

- Briefe enthalten sensible Daten: nur über TLS, serverseitig **nicht dauerhaft speichern** (oder nur mit ausdrücklicher Zustimmung für eine E-Akte).
- Absenderdaten nur lokal (Gerät / localStorage / DataStore).
- Hinweis vor dem ersten Upload, an welchen KI-Anbieter die Daten gehen.
- Logs ohne Briefinhalt.

---

## 8. Abnahmekriterien

- [ ] PDF mit Text, gescanntes PDF, Handyfoto und Screenshot werden jeweils korrekt analysiert.
- [ ] Mehrseitige Uploads (mehrere Bilder) werden als ein Brief behandelt.
- [ ] Mahnbescheid / Vollstreckungsbescheid / Bescheid mit Rechtsbehelfsbelehrung → **rot**, Frist korrekt oder als unklar markiert.
- [ ] Reine Info (z. B. Terminbestätigung, Kontoauszug-Info) → **grün**, `antwort_noetig = false`.
- [ ] Volltext ist standardmäßig verborgen.
- [ ] Antwort streamt, Stopp funktioniert, Teiltext bleibt stehen.
- [ ] Antwortschreiben enthält keine erfundenen Beträge/Daten; Lücken als `[Platzhalter]`.
- [ ] Antwort in der Sprache des Eingangsbriefs.
- [ ] Light und Dark Mode, Breite 360 px ohne horizontales Scrollen.
- [ ] RDG-Hinweis immer sichtbar.

---

## 9. Später (nicht Teil von v1)

- Frist direkt in Kalender / „Termine & Fristen“ der Hauptapp übernehmen.
- Antwort als PDF im Briefformat (DIN 5008) exportieren und über das Fax-Modul versenden.
- Verlauf aller geprüften Briefe pro Verfahren (E-Akte).
- Fristberechnung regelbasiert gegenprüfen (§§ 187 ff. BGB, § 222 ZPO, Feiertage), statt nur dem Modell zu vertrauen.
