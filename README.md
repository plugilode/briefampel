# Briefampel für Android

Native Kotlin/Compose-App nach dem beigefügten Briefampel-Entwurf. Importiert PDF, JPEG, PNG und WebP sowie geteilte Dokumente und Text. Zeigt eine Ampel-Zusammenfassung, hält das Original geschlossen und erstellt auf Wunsch einen Antwortentwurf als Stream.

## Starten

Android Studio mit JDK 17 oder neuer und Android SDK 36. Das Projekt verwendet das JDK aus Android Studio; unter PowerShell zum Beispiel:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Ohne eingerichteten KI-Zugang startet eine ausdrücklich markierte Vorschau mit drei interaktiven Beispielbriefen. Die **Debug-Testversion unterstützt jetzt einen eigenen Gemini-API-Schlüssel direkt auf dem Pixel, ohne Firebase**. Es erfolgt keine simulierte KI-Analyse; Antworten auf Beispiele bleiben gesperrt.

Aktueller Prüfnachweis und Grenzen der Tests: [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Screenshots aller App-Ansichten

[Galerie mit 23 Screenshots](docs/screenshots/index.html) · [ZIP mit allen Bildern](docs/briefampel-screenshots.zip)

Die Screenshots werden mit Android Layoutlib direkt aus den Compose-Komponenten gerendert und enthalten ausschließlich Beispieldaten. Lange Ergebnisse und Dialoge sind zusätzlich im unteren Scrollbereich abgebildet. Die Dialoginhalte verwenden im Renderer einen statischen Material-Rahmen, da separate Android-Dialogfenster nicht erfasst werden. Android-Dateiauswahl und Google-Dokument-Scanner benötigen weiterhin ein laufendes Gerät und sind nicht Teil dieser Galerie.

Neu erzeugen:

```powershell
.\gradlew.bat :app:updateDebugScreenshotTest
.\tools\Export-Screenshots.ps1
```

## Auf dem Pixel mit der Gemini API testen (ohne Firebase)

1. Die neue Debug-APK auf dem Pixel installieren; eine bereits installierte Testversion lässt sich mit derselben Signatur aktualisieren.
2. Einen eigenen [Gemini-API-Schlüssel in Google AI Studio](https://aistudio.google.com/apikey) erstellen. Den Schlüssel nicht in den Chat oder ins Repository schreiben.
3. In der App **Einstellungen → Gemini-API-Schlüssel → Schlüssel speichern** wählen. Es ist kein Neubau nach der Schlüsseleingabe nötig.
4. Einen eigenen Testbrief importieren und **Ampel prüfen** wählen. Die App erklärt vor dem ersten Aufruf die direkte Verarbeitung durch Google. Zum Einstieg erfundene oder geschwärzte Daten verwenden.
5. Bei Bedarf den Schlüssel über **API-Schlüssel löschen** vom Gerät entfernen. Ein Wechsel des Schlüssels löscht den Analyse-Cache und setzt die Zustimmung zurück.

Technik: `generateContent` für die JSON-Analyse, `streamGenerateContent?alt=sse` für Antworten. PDFs und normalisierte Fotos werden inline übertragen. Der persönliche Schlüssel liegt mit AES-256-GCM verschlüsselt im privaten App-Speicher; der Verschlüsselungsschlüssel liegt in Android Keystore. Der API-Schlüssel wird nur im `x-goog-api-key`-Header gesendet, nicht in URLs oder APK-Ressourcen. **Stopp** bricht auch die laufende HTTP-Verbindung ab. Keine automatischen Wiederholungen.

Die direkte Schlüsselverwaltung und der direkte HTTP-Provider sind ausschließlich im **Debug-Quellensatz** enthalten. Die Release-Variante verwendet weiterhin Firebase/App Check. Ein persönlicher Testschlüssel wird nicht an andere Nutzer verteilt; für eine Veröffentlichung bleibt ein verwalteter Zugang erforderlich. Google empfiehlt, Produktionsschlüssel nicht in mobilen Clients einzubetten: [API-Schlüssel schützen](https://ai.google.dev/gemini-api/docs/api-key).

**Daten und Kosten:** Der direkte Zugang verwendet dein Google-API-Projekt, kann API-Kosten verursachen und legt keine EU-Region fest. Die Datenbedingungen unterscheiden sich nach Projekt, Abrechnung und Region. Nach Googles Bedingungen können unbezahlte Dienste Inhalte zur Produktverbesserung verwenden; für EWR, Schweiz und Vereinigtes Königreich gelten gesonderte Regelungen. Vor persönlichen Briefen die [Gemini-API-Bedingungen](https://ai.google.dev/gemini-api/terms) für das eigene Projekt prüfen. Das lokale Guthaben ist kein serverseitiger Kostenschutz.

Ohne echten Schlüssel wurden nur lokale HTTP-Tests durchgeführt. Diese bestätigen Anfrageformat, Streaming und Abbruch, aber weder den Zugriff deines Projekts auf das voreingestellte Modell noch die Antwortqualität.

## Echte KI für die Release-Variante aktivieren (Firebase)

1. Ein eigenes Firebase-Projekt anlegen und die Android-App `de.briefampel.app` registrieren. Die Paket-ID vor einer Veröffentlichung festlegen.
2. Die heruntergeladene **google-services.json** nach `app/google-services.json` kopieren. Die Datei ist von Git ausgeschlossen. Der Google-Services-Gradle-Plugin wird nur bei vorhandener Datei angewendet.
3. Firebase AI Logic für den **Vertex-AI-Backenddienst** aktivieren und die benötigte Abrechnung/API-Konfiguration im eigenen Google-Projekt vornehmen. Keinen Gemini-API-Schlüssel in die App schreiben.
4. Ein im eigenen Projekt verfügbares Modell mit passender EU-Region auswählen. Standard dieser Version: `gemini-3.5-flash`, Region `eu`. Beide sind Build-Einstellungen, keine Garantie für Zugriff oder Verfügbarkeit. Überschreiben zum Beispiel mit `-Pbriefampel.model=MODELL -Pbriefampel.region=REGION`. Das SDK ist bewusst auf Firebase BoM 34.6.0 fixiert und nutzt die dazugehörige `vertexAI`-Initialisierung.
5. Firebase App Check konfigurieren. Debug-Builds verwenden den Debug-Provider: den lokalen Debug-Token aus Logcat im Firebase-Projekt registrieren. **Release** verwendet Play Integrity: App samt SHA-256 registrieren und Integrität/Signatur korrekt einrichten. Die App selbst setzt keine serverseitige App-Check-Erzwingung; diese in Firebase aktivieren.
6. Neu bauen und einen eigenen Testbrief prüfen. Der erste Upload erfordert Zustimmung. Erst ein erfolgreicher Live-Test bestätigt Projekt, Berechtigungen, Region und Modell.

Referenzen: [Firebase einrichten](https://firebase.google.com/docs/ai-logic/get-started), [Regionen](https://firebase.google.com/docs/ai-logic/locations), [Dokumente verarbeiten](https://firebase.google.com/docs/ai-logic/analyze-documents), [App Check](https://firebase.google.com/docs/app-check/android/play-integrity-provider).

## Bedienung

- **Brief scannen** öffnet den ML-Kit-Dokument-Scanner. Die Scan-Komponente wird bei Bedarf durch Google Play-Dienste geladen; auf Geräten ohne Unterstützung stattdessen Datei verwenden.
- **Datei** importiert mehrere Seiten als einen Brief. Höchstens 12 Seiten insgesamt, davon maximal 10 Bilder; 15 MB binäre Gesamtdaten. PDF-Seiten werden vor dem Upload gezählt. Geschützte/ungültige PDFs werden abgelehnt.
- Fotos werden verkleinert, anhand von EXIF ausgerichtet und als neues JPEG ohne EXIF-Metadaten kodiert. PDFs werden unverändert an den KI-Dienst gesendet und können eigene Metadaten enthalten.
- **Text einfügen** übernimmt bis 60.000 Zeichen; die Zwischenablage wird erst nach Antippen gelesen.
- Nach der Analyse sind Originalseiten nur über **Brief doch selbst lesen** sichtbar. Rückkehr zum Eingang erhält die Eingabe; **Neuen Brief prüfen** löscht den aktuellen Brief aus dem App-Zustand.
- Vorschläge lassen sich als ganze Zeilen aktivieren/entfernen. Stichworte, Ton und Absender bestimmen den Entwurf. **Stopp** bricht den Stream ab und erhält den Teiltext. Kopieren/Teilen sind nach Ende oder Abbruch verfügbar. **Neu formulieren** ruft die KI erneut auf.
- Absender, Darstellung, Tageszähler und Zustimmung werden privat lokal gespeichert; im persönlichen Debug-Test zusätzlich der verschlüsselte API-Schlüssel. Geprüfte Ergebnisse und Antworten liegen im lokalen Verlauf (AES-256-GCM, Schlüssel im Android Keystore, App-Sperre per PIN oder Biometrie); Originalseiten werden nicht gespeichert. Keine Inhaltsprotokolle im App-Code und keine App-Backups. Android-Dateianbieter/Scanner verwalten gegebenenfalls eigene Kopien.
- Erfolgreiche Analysen werden fünf Minuten im Arbeitsspeicher wiederverwendet. Guthaben: 3 Gratis-Analysen pro Installation; eine Analyse deckt einen geprüften Brief plus bis zu 3 Antwortentwürfe, die vierte Antwort kostet eine weitere Analyse. Fehler und Cachetreffer verbrauchen nichts. Pakete (10/30/100) werden angezeigt, Kaufen folgt mit Google Play Billing. Das Guthaben liegt vorerst lokal und lässt sich durch Neuinstallation umgehen; es ist **kein Kostenschutz**, bis der Server es zählt (siehe ABRECHNUNG.md).

## Vor Veröffentlichung noch erforderlich

- Eigenes Firebase-Projekt samt Live-Tests mit Text-PDF, Scan-PDF, Fotos, mehrseitigen Briefen, englischem Brief und Streaming-Abbruch. Ohne Projektkonfiguration sind diese Netzwerkpfade nicht nachgewiesen.
- Serverseitige Quoten/App-Check-Erzwingung und Kostenüberwachung für **Analyse und Antwort**. Ein Abrechnungsalarm allein begrenzt keine Ausgaben.
- Betreiberangaben und eine veröffentlichte, zu den tatsächlich eingesetzten Google-Diensten passende Datenschutzerklärung. Die App enthält vorerst nur eine technische Datenschutzübersicht ohne Lösch-/Aufbewahrungsversprechen für Google.
- Release-Signatur, Prüfung des minimierten Release auf einem echten Gerät und Play-Console-/Test-/Store-Eintrag. Release-Ausgaben aus diesem Projekt sind ohne eigenen Signaturschlüssel nicht veröffentlichungsfertig.
- Fachliche Prüfung der Zusammenfassungen, Fristen und Antwortentwürfe. Die App berechnet keine gesetzlichen Rechtsbehelfsfristen aus vermuteten Zustelldaten.
- Die Oberfläche ist nativ in Compose gebaut: animierte Ampel-Figur mit Gesicht und Hut (`ui/figur/AmpelFreundin.kt`) und ein kurzes Intro mit Regenschirm beim ersten Start. Store-Video und Marketingmaterial fehlen noch.

Bestehende Konzeptunterlagen: `PLAN.md`, `docs/briefampel-spec.md` und `docs/briefampel.html`. Sie beschreiben auch frühere Entscheidungen und sind kein Nachweis aktueller Anbieterbedingungen, Preise oder Store-Regeln.
