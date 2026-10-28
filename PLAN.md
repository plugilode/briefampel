# Briefampel Android – Umsetzungsplan

**Umsetzungsstand 26.10.2026:** Die native App mit Eingang, Scan-/Datei-/Textimport, Teilen-Ziel, Ampelergebnis, Originalvorschau, Antwort-Werkbank, Streaming-Abbruch, lokalen Einstellungen und Firebase-Anbindung ist implementiert. Die Debug-Testversion unterstützt außerdem einen eigenen, auf dem Gerät verschlüsselt gespeicherten Gemini-API-Schlüssel für den direkten Pixel-Test ohne Firebase. Ohne eingerichteten KI-Zugang läuft eine markierte Vorschau. Aktuelle Einrichtung, technische Grenzen und noch offene Veröffentlichungsschritte stehen in [README.md](README.md). Die folgenden Abschnitte bleiben als ursprünglicher Konzeptplan erhalten; Anbieter-, Preis- und Store-Angaben darin sind nicht als aktuell verifiziert zu verstehen.

Grundlage: [docs/briefampel-spec.md](docs/briefampel-spec.md) und der HTML-Prototyp [docs/briefampel.html](docs/briefampel.html).

**Was es ist:** eine kleine, eigenständige App. Sie hat nichts mit der Hauptapp „Virtuelle Rechtsassistenz“ zu tun. Sie ist die erste eigene App im Play Store: ausprobieren, Feedback sammeln, vielleicht ein, zwei Euro verdienen.

**Leitlinie:** Die Funktion bleibt so schlicht wie in der Spezifikation. Der Wow-Faktor kommt aus Bewegung, Haptik und Feinschliff, nicht aus mehr Funktionen. Technik so einfach wie möglich, damit du sie allein betreiben kannst.

---

## 1. Entscheidungen auf einen Blick

| Thema | Entscheidung | Warum |
|---|---|---|
| App | **Kotlin + Jetpack Compose**, ein einziges App-Modul | Nativ, flüssige Animationen; ein Modul reicht für eine kleine App |
| Min / Target SDK | minSdk 26 (Android 8), targetSdk 35 | Läuft auf ca. 97 % der Geräte und erfüllt die Play-Store-Vorgaben |
| KI-Anbindung | **Firebase AI Logic (Gemini über Vertex AI, Region EU)**, kein eigener Server | Der API-Schlüssel liegt nie in der App, Firebase leitet die Aufrufe weiter. Du musst keinen Server betreiben, absichern und bezahlen |
| Absicherung | Firebase App Check (Play Integrity) + Limit pro Nutzer | Nur deine echte App aus dem Play Store darf die KI aufrufen; niemand kann über deine App auf deine Kosten Anfragen stellen |
| Modell | Gemini Flash (schnell, sehr günstig) | Eine Brief-Prüfung kostet Bruchteile eines Cents |
| Kamera / Scan | **ML Kit Document Scanner** | Erkennt die Ränder, richtet die Seite gerade aus, mehrere Seiten, fertige Oberfläche |
| PDF | Gemini liest PDFs direkt (auch Scans) | Der ganze Teil „Text auslesen, Scan erkennen, Seiten rendern“ aus Spec §3 fällt weg |
| Lokale Daten | DataStore (Absender, Einwilligung, Zähler) | Spec: Absender nur auf dem Gerät |
| Struktur | MVVM, ein `StateFlow` pro Screen, einfache manuelle Abhängigkeiten statt Hilt | Weniger Bausteine, die man verstehen muss |

**Abweichung von der Spec, bewusst:** Die Spec verlangt „eigenes Backend“. Der Sinn dahinter ist, dass der Schlüssel nicht in der App steht. Das leistet Firebase AI Logic auch, ohne dass du einen Server pflegst. Die Spec will außerdem austauschbare Anbieter (Gemini, Claude, Mistral). Dafür bleibt in der App ein kleines `LlmProvider`-Interface. Ein Wechsel zu Claude bräuchte später doch einen kleinen Server, für v1 ist das unnötig.

---

## 2. Architektur

```
┌─────────────────────── Android App ───────────────────────┐
│ ui/         Compose Screens, Ampel, Animationen           │
│ domain/     AnalyzeLetter, DraftReply, Prompts (Spec 4.2/4.3)
│ data/       LetterInput (PDF/Bild/Text), GeminiProvider,  │      ┌──── Firebase ────┐
│             SenderStore, ConsentStore, UsageCounter       │─────►│ App Check        │
└───────────────────────────────────────────────────────────┘ TLS  │ AI Logic → Gemini│
                                                                   │ (Vertex AI, EU)  │
                                                                   └──────────────────┘
```

- **Analyse:** PDF und/oder Bilder plus Prompt in **einem** Aufruf, Antwort als JSON nach Schema (Structured Output). Danach tolerant parsen und Felder prüfen; unbekannte Ampel → `gelb`.
- **Antwortschreiben:** `generateContentStream` → Text erscheint live. Stopp = Coroutine abbrechen, der Teiltext bleibt stehen.
- **Cache:** Analyse-Ergebnis 5 Minuten im Speicher, Schlüssel = Hash der Eingabe. „Neu formulieren“ nie aus dem Cache.
- **Bilder:** vor dem Senden EXIF-Drehung anwenden, auf ca. 1,2 MP verkleinern und als neues JPEG ohne Metadaten schreiben (Spec §3).
- Grenzen: Text bis 60.000 Zeichen, PDF bis 12 Seiten, höchstens 10 Bilder.

### Projektstruktur

```
ampel/
├── app/src/main/java/.../briefampel/
│   ├── ui/theme/        Farben, Schriften, Motion-Tokens
│   ├── ui/components/   TrafficLight, AmpelPill, FactRow, DeadlineBadge, Chips, ToneSegmented
│   ├── ui/screen/       BriefampelScreen + ViewModel, ConsentSheet, PasteSheet
│   ├── domain/          LetterAnalysis, Prompts, Fehler-Mapping (Spec §6)
│   └── data/            GeminiProvider, ImageNormalizer, Stores
├── docs/                Spec, Prototyp, Datenschutzerklärung
└── PLAN.md
```

---

## 3. Screens und Ablauf

Ein Screen mit drei Zuständen, keine verschachtelte Navigation:

1. **Eingang**
   - Drei große Kacheln: **Scannen**, **Datei** (PDF/Bilder, mehrere), **Text einfügen** (Sheet mit „Aus Zwischenablage einfügen“).
   - **Teilen-Ziel:** Briefampel erscheint im Teilen-Menü von Android (PDF aus der Mail-App, Screenshot aus der Galerie).
   - Seiten als kleine Vorschaubilder mit ×, Button **„Ampel prüfen“**.
   - Beim ersten Start ein Beispielergebnis, klar als „Beispiel“ markiert.
2. **Ergebnis:** Ampel-Karte wie Spec §2.2. Der Volltext („Brief doch komplett lesen“) zeigt die Originalseiten oder den eingefügten Text, standardmäßig eingeklappt.
3. **Antwort-Werkbank:** Vorschlags-Chips, Stichwortfeld, Ton-Auswahl, Absender (einklappbar), Live-Vorschau mit Stopp, danach **Kopieren**, **Teilen**, **Neu formulieren**.

Dazu:
- **Einwilligung vor dem ersten Upload:** an wen die Daten gehen (Google, Gemini, EU-Server), dass nichts gespeichert wird, Link zur Datenschutzerklärung.
- **RDG-Hinweis** als feste, ruhige Zeile am unteren Bildschirmrand.

---

## 4. Wow-Faktor – wo genau

Alles respektiert die Systemeinstellung „Animationen entfernen“.

0. **Die Ampel ist eine warmherzige Beschützerin** (Kindermädchen-Stil, eigene Figur). Sie hat runde Augen mit Pupillen, sanfte Brauen, rosige Wangen, ein Lächeln und einen kleinen cremefarbenen Hut mit rotem Band und Blume. Sie blinzelt und atmet leicht, beim Lesen wandern die Pupillen hin und her, und sie lüpft den Hut, wenn ein Brief kommt. Bei Rot schaut sie mitfühlend (Brauen innen hoch, sanftes Lächeln: „Ich kümmere mich“), bei Grün strahlt sie. Ein 14-Sekunden-Kurzfilm erzählt die Geschichte: Briefe regnen herab, die Ampel schwebt mit einem Regenschirm herunter und schirmt sie ab, nimmt den Mahnbrief unter den Hut, liest ihn und gibt mitfühlend Bescheid. Der Film dient als Intro beim ersten Start und als Store-Video.
1. **Die Ampel als Held.** Selbst gezeichnet (Canvas): Gehäuse mit leichtem Verlauf, Lampen mit Leuchtschein. Während der Prüfung pulsiert sie ruhig durch alle drei Farben, statt einen Ladekreis zu zeigen.
2. **Der Moment der Wahrheit.** Die richtige Lampe zündet, der Bildschirm bekommt kurz einen ganz leichten Schimmer in dieser Farbe. Dazu vibriert das Handy unterschiedlich: grün weich, gelb doppelt, rot fester. Danach gleiten Titel, Kurzfassung und Fakten nacheinander herein.
3. **Versiegelter Umschlag.** Die hochgeladenen Seiten rutschen als kleiner Stapel in einen Umschlag. Den Volltext gibt es nur, wenn man ihn bewusst öffnet. Das ist die Idee der App in einem Bild.
4. **Frist-Countdown:** „noch 3 Tage“ in Mono-Schrift mit einem Ring, der sich leert; unter 7 Tagen rot.
5. **Schreiben live:** Der Text erscheint Wort für Wort mit weichem Einblenden und scrollt automatisch mit, bis der Nutzer selbst scrollt.
6. **Chips mit Physik:** Ein angetippter Vorschlag fliegt ins Stichwortfeld, mit kurzer Vibration.
7. **Feinschliff:** Edge-to-Edge, Predictive Back, eigene Schriften (Bricolage Grotesque, Source Sans 3, IBM Plex Mono), Dark Mode, Startbildschirm mit Ampel, die einmal durchschaltet, Themed Icon.

Bewusst **nicht**: Konfetti, Maskottchen, Glas-Effekte. Die App soll Angst nehmen; ruhig wirkt hier hochwertiger.

---

## 5. Design

- Farben 1:1 aus dem Prototyp (Light + Dark). Ampelfarben sind Bedeutung, nicht Deko, und bleiben getrennt vom blauen Akzent.
- Schriften: Bricolage Grotesque für Überschriften, Source Sans 3 für Text, IBM Plex Mono für Datum, Frist und Aktenzeichen.
- Touch-Ziele ≥ 48 dp, sichtbarer Fokus, TalkBack liest „Ampel: Rot – lieber nicht lesen“.
- **Konzept „Nur die Ampel hat Farbe“:** Die App ist sonst in Asphalt-Grau (#121417) und Kreideweiß (#F3F0E8) gehalten, Hauptknöpfe sind kreideweiß. Farbe kommt ausschließlich aus den drei Lampen (Rot #FF5A47, Gelb #F5B544, Grün #3DD68C; im hellen Modus dunklere Textvarianten für Kontrast). Das Ampel-Gehäuse bleibt auch im hellen Modus dunkel.
- Design-Entwurf (M0): https://claude.ai/artifact/W3FxiyNnmWU9t6h9tNtvkr

---

## 6. Veröffentlichen im Play Store – was auf dich zukommt

Das ist für eine erste App der größte Brocken, mehr als der Code.

1. **Google-Play-Entwicklerkonto** anlegen: einmalig **25 US-Dollar**, Identitätsnachweis mit Ausweis. Die Prüfung dauert ein paar Tage.
2. **Pflicht für neue private Konten: geschlossener Test mit mindestens 12 Testern über 14 Tage am Stück**, bevor du die App öffentlich freischalten darfst. Freunde, Familie oder Kollegen mit Android-Handy brauchst du also früh. Plane die zwei Wochen fest ein.
3. **Store-Eintrag:** App-Name, Kurzbeschreibung, Beschreibung, Icon 512×512, Feature-Grafik 1024×500, mindestens 2 Screenshots. Die machen wir aus der App selbst; dabei hilft der Wow-Faktor am meisten.
4. **Datenschutzerklärung** als öffentliche Webseite (z. B. kostenlos über GitHub Pages) und das Formular **„Datensicherheit“** in der Play Console: Dokumente werden zur Verarbeitung an Google gesendet und nicht gespeichert.
5. **Inhaltsfragebogen, Zielgruppe** (ab 18), Hinweis „keine Rechtsberatung“ in der Beschreibung.
6. **Gut zu wissen:** Bei einem privaten Konto wird dein Name als Entwickler angezeigt. **Sobald du Geld verlangst, wird auch deine Adresse öffentlich.** Außerdem brauchst du dann in Deutschland ein Impressum, und Einnahmen sind steuerlich relevant (für den Anfang meist Kleinunternehmerregelung).

---

## 7. Kosten und Geld verdienen

**Deine laufenden Kosten:** Firebase auf dem Blaze-Tarif (zahlen nach Verbrauch, Kreditkarte nötig). Gemini Flash kostet pro Brief-Prüfung deutlich unter einem Cent. 1.000 Prüfungen im Monat bleiben im Bereich von ein bis zwei Euro. **Budget-Alarm bei z. B. 5 € einrichten** und das Limit pro Nutzer setzen, dann gibt es keine bösen Überraschungen.

**Vorschlag für Geld (Reihenfolge):**
1. **Start kostenlos**, mit einem Limit von 3 Prüfungen pro Tag. So siehst du ohne Hürde, ob die Leute die App annehmen, und deine Adresse bleibt privat.
2. Wenn sie ankommt: **einmaliger Kauf „Unbegrenzt“ für 1,99 €** in der App (Google Play Billing). Das ist eine überschaubare Erweiterung, die wir dann einbauen.

Eine Bezahl-App ab dem ersten Tag empfehle ich nicht: Bei unbekannten Apps lädt kaum jemand vorher etwas herunter, und du bekommst kein Feedback.

---

## 8. Tests und Abnahme

- **Unit-Tests:** toleranter JSON-Parser, Ampel-Fallback, Frist „heiß“ (< 7 Tage, Zeitzone Europe/Berlin), Fehler-Mapping, Chip ↔ Stichwortfeld, Tageslimit.
- **Echte Testbriefe:** Text-PDF, Scan-PDF, schiefes Handyfoto, Screenshot, drei Fotos als ein Brief, Mahnbescheid → rot, Terminbestätigung → grün ohne Antwortbedarf, englischer Brief → englische Antwort, Antwort ohne erfundene Beträge.
- **Oberfläche:** Light und Dark, 360 dp Breite, Schriftgröße 200 %, Streaming mit Stopp.
- Die Checkliste aus Spec §8 ist die Abnahme vor dem Release.

---

## 9. Meilensteine

| # | Ergebnis |
|---|---|
| M0 | Design-Entwurf der drei Zustände inkl. Reveal-Animation, Light/Dark – du nimmst ab |
| M1 | Android-Projekt, Theme, Ampel-Komponente mit Animationen, Beispielergebnis (noch ohne KI) |
| M2 | Firebase-Projekt, App Check, echte Analyse mit Scan, Datei, Teilen und Text |
| M3 | Antwort-Werkbank mit Live-Schreiben, Stopp, Kopieren, Teilen, Neu formulieren |
| M4 | Einwilligung, Tageslimit, Fehlertexte, Barrierefreiheit, Haptik und Feinschliff |
| M5 | Store-Eintrag, Screenshots, Datenschutzerklärung, signiertes App-Bundle |
| M6 | Geschlossener Test (12 Tester, 14 Tage), Feedback einarbeiten, dann öffentlich |

**Parallel sofort machen:** Entwicklerkonto anlegen (Prüfung dauert) und Tester sammeln.

Später, wenn die App ankommt: Kauf „Unbegrenzt“, Frist in den Kalender, Antwort als PDF.

---

## 10. Offene Fragen an dich

1. Hast du **Android Studio** schon installiert und ein **Android-Handy** zum Testen?
2. **App-Name** im Store: „Briefampel“ oder anders? Und eine Paket-ID, z. B. `de.deinname.briefampel` (lässt sich später nicht mehr ändern).
3. Ist dir das **kostenlose Modell mit Tageslimit** zum Start recht?
