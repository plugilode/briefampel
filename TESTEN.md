# Briefampel testen

Test-APK: `ausgabe/Briefampel-0.3.0-test.apk` (Debug-Version 0.3.0, Android 8 oder neuer)
SHA-256: `5c3826f0151ec1ebd8a3dc4e8916bec119f82912062edbbdd8689a4254050309`

## 1. Aufs Handy bringen

**Variante A – Datei aufs Handy (am einfachsten)**
1. Die APK aufs Handy bekommen, z. B. per Google Drive, per Mail an dich selbst oder per USB-Kabel in den Ordner „Download“.
2. Auf dem Handy die Datei antippen.
3. Android fragt, ob die App (z. B. „Dateien“ oder „Chrome“) Apps installieren darf: **Einstellungen → Zulassen**, dann zurück und **Installieren**.
4. Google Play Protect warnt eventuell vor einer unbekannten App. Das ist bei selbst gebauten Test-Apps normal: **Trotzdem installieren**.

**Variante B – per USB mit adb** (Entwickleroptionen und USB-Debugging am Handy an)

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r ausgabe\Briefampel-0.3.0-test.apk
```

## 2. Was du ohne KI-Schlüssel testen kannst

- [ ] **Intro** beim ersten Start: Briefe regnen, die Ampel schwebt mit dem Schirm herunter, „Los geht’s“ und „Überspringen“ funktionieren.
- [ ] **Startbildschirm:** Die Figur atmet und blinzelt, der Hut sitzt richtig.
- [ ] **Beispiele** „Mahnung“, „Rückfrage“, „Termin“: Die richtige Lampe zündet, der Schimmer erscheint, das Handy vibriert (Rot fest, Gelb doppelt, Grün weich).
- [ ] Bei **Rot** schaut die Figur mitfühlend, bei **Grün** lächelt sie.
- [ ] **Frist-Karte:** Die Tage stimmen zum heutigen Datum, die Leiste ist passend gefüllt.
- [ ] **Brief doch selbst lesen** öffnet das Original erst auf Antippen.
- [ ] Bei Beispielen ist „Antwort schreiben“ gesperrt, mit Hinweis.
- [ ] **Datei** und **Brief scannen**: Die Seiten erscheinen im Umschlag, beim Hinzufügen lüpft die Figur den Hut, „Ampel prüfen“ erscheint.
- [ ] **Teilen:** Ein PDF aus der Mail-App mit „Briefampel“ teilen, es landet im Umschlag.
- [ ] **Text einfügen** übernimmt Text.
- [ ] **Hell/Dunkel** (Einstellungen) und große Schrift (Android-Einstellungen) sehen ordentlich aus, nichts wird abgeschnitten.
- [ ] Mit „Animationen entfernen“ (Android → Bedienungshilfen) steht alles still, und die Lampe leuchtet trotzdem.

## Sperre und Verlauf

- [ ] Nach dem Intro: 6-stellige PIN festlegen, bestätigen, dann „Ja, gern“ für Fingerabdruck/Gesicht.
- [ ] App schließen und neu öffnen: Die Biometrie-Abfrage kommt von selbst, „PIN eingeben“ führt zum Ziffernblock.
- [ ] Falsche PIN: Die Punkte wackeln und eine Meldung erscheint. Nach 5 Fehlversuchen kommt eine 30-Sekunden-Pause.
- [ ] Länger als eine Minute in einer anderen App, dann zurück: Die App ist wieder gesperrt.
- [ ] Einen eigenen Brief prüfen und eine Antwort schreiben, dann das Uhr-Symbol oben: Der Brief steht im Verlauf, mit Frist und „1 Antwort“.
- [ ] Eintrag öffnen: Ergebnis und gespeicherte Antwort mit Kopieren und Teilen. Das Original ist bewusst nicht gespeichert.
- [ ] „Aus dem Verlauf löschen“ und „Ganzen Verlauf löschen“ fragen vorher nach.
- [ ] Beispiele landen nicht im Verlauf.
- [ ] „PIN vergessen?“ löscht nach Rückfrage PIN und Verlauf.

## 3. Mit echter KI testen

Standard ist Gemini. In `local.properties`:

```
briefampel.geminiKey=DEIN_SCHLÜSSEL
```

Mistral (Paris, EU) statt Gemini: `briefampel.ki=mistral` und `briefampel.mistralKey=…` eintragen. Das Mistral-Konto braucht mindestens 10 $ Guthaben.

1. Der Schlüssel ist in der Test-APK schon eingebaut und wird beim ersten Start übernommen. Nichts eintippen nötig.
2. Anderen Schlüssel nutzen: **Einstellungen** (Zahnrad oben rechts) → **Gemini-API-Schlüssel** (bzw. Mistral) → einfügen → **Schlüssel speichern**.
3. Einen eigenen Testbrief laden und **Ampel prüfen**. Vor dem ersten Aufruf erscheint die Einwilligung.
4. Danach: **Antwort schreiben**, Stichworte wählen, Ton wählen, schreiben lassen, **Stopp** testen, **Kopieren** und **Teilen**.

Wichtig:
- Zum Start erfundene oder geschwärzte Briefe verwenden, keine echten persönlichen Daten.
- Für echte Briefe ein Google-Projekt mit Abrechnung nutzen; mit einem kostenlosen Schlüssel kann Google Inhalte zur Produktverbesserung verwenden. Bei Mistral das Training im Konto ausschalten.
- Der Schlüssel bleibt verschlüsselt auf dem Handy. Nicht in Chats oder ins Repository schreiben.
- Modelle: `gemini-3.5-flash`, bei Überlastung `gemini-3.5-flash-lite`. Bei Mistral: `mistral-small-latest` und `mistral-medium-latest`, PDFs vorher durch Mistrals Texterkennung.

### Gemini und Mistral vergleichen

Mit beiden Schlüsseln in `local.properties` schickt dieser Befehl fünf typische Testbriefe an beide KIs und schreibt eine Tabelle nach `briefe-test-ergebnis.md`:

```bash
cd functions && VERGLEICH=1 npx vitest run test/vergleich.test.ts
```

Eigene Briefe (PDF, JPG, PNG) dazu in den Ordner `briefe-test/` legen. Ordner und Ergebnis kommen nie nach GitHub.

## 4. Was noch nicht geprüft ist

- Die App lief bisher nur in automatischen Tests und gerenderten Screenshots, **noch nie auf einem echten Gerät**. Animationen, Vibration, Scanner und Teilen-Menü sind daher ungetestet.
- Der Emulator startet auf diesem Rechner nicht (keine Hardware-Virtualisierung).
- Es ist eine Debug-Version: nicht für den Play Store, nicht für andere Nutzer gedacht.

Fehler bitte mit Screenshot und kurzer Beschreibung melden: Was hast du getan, was ist passiert, was hättest du erwartet?
