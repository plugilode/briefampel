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

## 3. Mit echter KI testen (eigener Gemini-Schlüssel)

1. In Google AI Studio einen API-Schlüssel erstellen: https://aistudio.google.com/apikey
2. In der App: **Einstellungen** (Figur-Symbol oben rechts) → **Gemini-API-Schlüssel** → einfügen → **Schlüssel speichern**.
3. Einen eigenen Testbrief laden und **Ampel prüfen**. Vor dem ersten Aufruf erscheint die Einwilligung.
4. Danach: **Antwort schreiben**, Stichworte wählen, Ton wählen, schreiben lassen, **Stopp** testen, **Kopieren** und **Teilen**.

Wichtig:
- Zum Start erfundene oder geschwärzte Briefe verwenden, keine echten persönlichen Daten.
- Mit einem kostenlosen Schlüssel kann Google die Inhalte zur Produktverbesserung verwenden. Für echte Briefe ein Projekt mit Abrechnung nutzen und die Gemini-Bedingungen prüfen.
- Der Schlüssel bleibt verschlüsselt auf dem Handy. Nicht in Chats oder ins Repository schreiben.
- Das Modell ist auf `gemini-3.8-flash` voreingestellt. Ob dein Projekt Zugriff darauf hat, zeigt erst der erste echte Aufruf. Bei einem Fehler: Modell beim Bauen mit `-Pbriefampel.model=…` ändern.

## 4. Was noch nicht geprüft ist

- Die App lief bisher nur in automatischen Tests und gerenderten Screenshots, **noch nie auf einem echten Gerät**. Animationen, Vibration, Scanner und Teilen-Menü sind daher ungetestet.
- Der Emulator startet auf diesem Rechner nicht (keine Hardware-Virtualisierung).
- Es ist eine Debug-Version: nicht für den Play Store, nicht für andere Nutzer gedacht.

Fehler bitte mit Screenshot und kurzer Beschreibung melden: Was hast du getan, was ist passiert, was hättest du erwartet?
