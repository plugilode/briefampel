# Prüfnachweis – Version 0.2.0

## Automatisch geprüft

**54 Tests erfolgreich, keine Fehler.** Debug-APK 0.2.0 erstellt und Signatur erneut mit `apksigner` geprüft.

- **Basissuite erfolgreich:** JSON-Verarbeitung und Typprüfung, ungültige/überfällige Fristen, Eingabegrenzen und Cache-Schlüssel, Vorschlagszeilen, Tageslimit, Einwilligung, Fehler/Retry, Abbruch und unmittelbarer Neustart des Antwortstreams, Absender-Speicherung/-Löschung, Rückkehr aus Beispielen, echter Activity-/ViewModel-Start und Android-Text-Teilen.
- **Direkte Gemini-API gegen lokalen HTTP-Testserver:** JSON-Analyse, Authentifizierung nur im Header, Originaltext im Request, SSE-Antwort samt Zusammenführung, neuer Aufruf beim Neuformulieren, Abbruch einer blockierten Verbindung, HTTP-403 ohne Ausgabe von Serverdetails/Schlüssel, Behandlung von abgeschnittenen/abgelehnten Antworten, SSE-Ereignisgrenzen, Ausschluss von Thought-Parts, keine Anfrage ohne Schlüssel. Inline-Daten mehrerer PDF-/Bildseiten separat mit Robolectric geprüft.
- **Persönlicher API-Zugang:** Speichern/Löschen mit kontrolliertem Speicher, Aktivieren ohne Firebase, Zurücksetzen der Zustimmung bei Schlüsselwechsel, Speicherfehler ohne Geheimnis im UI-Zustand, keine Schlüsselverwaltung bei einer Release-artigen Konfiguration; der komplette Einstellungsablauf ist außerdem als Compose-Bedienungstest geprüft.
- **Compose-Bedienung mit Robolectric:** Text übernehmen, fehlende KI-Konfiguration anzeigen, Beispiel öffnen, Original erst nach bewusster Auswahl zeigen, Antworten auf Beispiele sperren, heller Modus und Absender-Einstellungen, scrollbarer Eingang bei 360 dp und 200 % Schrift, Einwilligung → Ergebnis → Antwort → Stopp → Kopieren verfügbar. Für KI-Aufrufe verwendet dieser Test einen kontrollierten Test-Provider.
- **Debug-APK gebaut.** Android `apksigner verify --verbose` bestätigt die v2-Signatur. Diese APK ist mit einem Debug-Schlüssel signiert und für lokale Installation auf einem Android-Gerät bestimmt.
- **Android Lint erfolgreich, keine Fehler.** Hinweise zu neueren SDK-/Bibliotheksversionen und zur Kompatibilität einzelner Manifestattribute bleiben bestehen.
- **23 native Layoutlib-Renderings erstellt:** Start, Import, Prüfung, Rot/Gelb/Grün, Stichworte, Streaming und Antwortentwurf, Textdialog, Einwilligung, Gemini-Einstellungen, Datenschutz, Original, Fehler, heller Modus sowie zusätzliche untere Scrollbereiche. Die Galerie und der ZIP-Export liegen unter `docs/screenshots/` bzw. `docs/briefampel-screenshots.zip`. Die Renderings werden visuell geprüft; Dialoginhalte verwenden denselben App-Code in einem statischen Material-Vorschau-Rahmen.

Reproduzierbare Prüfung mit dem in README beschriebenen JDK:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease
```

Testbericht: `app/build/reports/tests/testDebugUnitTest/index.html`.
Lintbericht: `app/build/reports/lint-results-debug.html`.

## Grenzen dieser Prüfung

- Keine `app/google-services.json` und kein echter Gemini-Schlüssel vorhanden. **Keine echte Gemini-Anfrage wurde durchgeführt.** Der direkte Debug-Zugang kann mit einem eigenen Schlüssel auf dem Pixel aktiviert werden; Modellzugriff, Kontingente und Antwortqualität müssen dann real geprüft werden. Für Release zusätzlich Firebase/App Check einrichten.
- Der vorhandene Android-Emulator startet auf diesem Host wegen fehlender Hardware-Virtualisierung nicht; auch der Software-Fallback beendet sich. Kein angeschlossenes Android-Gerät wurde gefunden.
- Die Robolectric-Tests prüfen Verhalten und zugängliche Compose-Elemente. Die zusätzlichen Layoutlib-Screenshots ermöglichen eine visuelle Prüfung der nativen Komponenten. Beides ersetzt keinen Gerätetest von Systemfenstern, Animationen, Tastatur, Haptik und Kamera.
- Dokument-Scanner, echte PDF-Verarbeitung/-Vorschau, EXIF-Ausrichtung, Fotoqualität und Datei-Teilen mit einem echten Android-Dateianbieter benötigen noch einen Gerätetest. Die automatischen Eingabetests prüfen Grenzen und Zusammenführung, nicht die Kamera oder die Modellinterpretation eines PDFs.
- Der echte Android-Keystore und das Speichern/Entschlüsseln des persönlichen Schlüssels müssen auf einem Gerät geprüft werden. Zustands-/UI-Tests verwenden dafür eine kontrollierte Speicherimplementierung; sie ersetzen keine Hardwareprüfung der Verschlüsselung.
- Die minimierte Release-Variante wird ohne eigenen Signaturschlüssel erstellt. Eine erfolgreiche Erstellung ist keine Play-Store-Freigabe.

Die noch offenen Konfigurations- und Veröffentlichungsschritte stehen im README. Die App kennzeichnet den Zustand ohne KI-Zugang ausdrücklich als Vorschau.
