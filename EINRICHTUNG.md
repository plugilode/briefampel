# Einrichtung für den Store-Start

Was du einmal klicken musst, damit die Store-Version über deinen eigenen Server läuft.
Reihenfolge einhalten. Bis Schritt 6 kostet nichts außer den 25 $ für Google Play.

Solange `google-services.json` und `briefampel.server` fehlen, baut die App wie bisher die Test-Version mit deinem eigenen Schlüssel.

## 1. Google Play Console

1. Entwicklerkonto anlegen (25 $ einmalig), mit dem Google-Konto, dessen Zahlungsprofil auf **Mexiko** steht.
2. App anlegen. Paketname: `de.briefampel.app` (danach nicht mehr änderbar).
3. Unter **Monetarisieren › Produkte › In-App-Produkte** drei Produkte anlegen, alle aktiv:

   | Produkt-ID | Name | Preis |
   |---|---|---|
   | `analysen_10` | 10 Analysen | 1,99 € |
   | `analysen_30` | 30 Analysen | 4,99 € |
   | `analysen_100` | 100 Analysen | 9,99 € |

   Die IDs müssen exakt so heißen, sonst findet die App die Pakete nicht.
4. Unter **Einrichtung › Lizenztests** dein eigenes Google-Konto eintragen. Dann sind Testkäufe kostenlos.

## 2. Firebase-Projekt

1. https://console.firebase.google.com → Projekt anlegen (z. B. `briefampel`). Google Analytics brauchst du nicht.
2. **Tarif Blaze** wählen (nötig für Functions und Secrets). Ein Budget-Alarm von z. B. 10 € unter *Abrechnung › Budgets* schützt vor Überraschungen.
3. **Authentication › Anmeldemethode › Google** aktivieren.
4. **Firestore** anlegen, Standort `eur3 (Europe)`, im Produktionsmodus.
5. **Android-App hinzufügen** mit Paketname `de.briefampel.app`. Dort beide SHA-256-Fingerabdrücke eintragen:
   - Debug: `./gradlew :app:signingReport` (Zeile „SHA-256“ bei `debug`)
   - Store: Play Console › *Test und Release › App-Integrität › App-Signaturschlüssel*
6. `google-services.json` herunterladen und nach `app/google-services.json` legen. Die Datei steht in `.gitignore` und kommt nicht nach GitHub.

## 3. App Check

1. Firebase › **App Check** › Android-App › **Play Integrity** registrieren.
2. Für die Test-APK: Die App einmal starten, dann im Logcat nach `DebugAppCheckProvider` suchen. Dort steht ein Debug-Token. Unter *App Check › Apps › Debug-Tokens verwalten* eintragen.

## 4. Optional: Mistral statt Gemini (Paris, EU)

Standard ist Gemini; dann diesen Schritt überspringen. Für Mistral muss das Konto mindestens 10 $ Guthaben haben, und App und Server werden mit `briefampel.ki=mistral` bzw. `BRIEFAMPEL_KI=mistral` umgestellt.


1. https://console.mistral.ai → Konto anlegen, auf **Pay-as-you-go** (bezahlt) umstellen. Im kostenlosen Tarif darf Mistral Eingaben zum Training nutzen, das geht für echte Briefe nicht.
2. Unter **Privacy** (Datenschutz) das **Training mit deinen Daten ausschalten**.
3. Beim Support **Zero Data Retention** beantragen. Begründung zum Beispiel: „App für Privatpersonen, die Briefe von Behörden und Gläubigern prüft; die Briefe enthalten sensible personenbezogene Daten (DSGVO).“ Mistral entscheidet selbst darüber.
4. Den **Auftragsverarbeitungsvertrag (DPA)** von Mistral annehmen bzw. ablegen. Den brauchst du für deine Datenschutzerklärung.
5. Unter **API Keys** einen Schlüssel erstellen. Nicht in Chats oder Git schreiben.
6. Ein Budget-Limit im Mistral-Konto setzen, zum Beispiel 10 € im Monat.

## 5. Server hochladen

```bash
npm install -g firebase-tools
```
```bash
firebase login
```
```bash
firebase use --add
```
```bash
firebase functions:secrets:set KI_SCHLUESSEL
```
Hier fügst du deinen **Gemini**-Schlüssel ein (oder den Mistral-Schlüssel, wenn du Schritt 4 gewählt hast). Er landet nur in Google Secret Manager und nie in der App oder in Git.
```bash
firebase deploy --only functions,firestore
```
Am Ende steht die Adresse der Funktion, etwa `https://europe-west3-briefampel.cloudfunctions.net/api`.

Einstellbar über `functions/.env` (optional):

| Name | Standard | Zweck |
|---|---|---|
| `BRIEFAMPEL_KI` | `gemini` | `mistral` für Mistral (dann die App mit `briefampel.ki=mistral` bauen, damit die Datenschutztexte stimmen) |
| `BRIEFAMPEL_MODELLE` | leer = `gemini-3.8-flash,gemini-3.5-flash-lite` (bei Mistral `mistral-small-latest,mistral-medium-latest`) | Hauptmodell und Ausweichmodell |
| `BRIEFAMPEL_TAGESLIMIT` | `2000` | Notbremse: höchstens so viele KI-Aufrufe pro Tag für alle zusammen |
| `BRIEFAMPEL_APPCHECK` | `pflicht` | `aus` nur zum Ausprobieren, nie im Store |

## 6. Käufe prüfen dürfen

Der Server fragt bei Google Play nach, ob ein Kauf echt ist. Dafür braucht sein Dienstkonto Zugriff:

1. Google Cloud Console (gleiches Projekt) › *APIs* › **Google Play Android Developer API** aktivieren.
2. Play Console › **Nutzer und Berechtigungen** › Nutzer einladen: das Dienstkonto `PROJEKT-ID@appspot.gserviceaccount.com`
   (genauer Name in der Cloud Console unter *IAM*, Spalte „Compute/App Engine default service account“).
   Rechte: **Finanzdaten ansehen** und **Bestellungen und Abos verwalten**.

## 7. App gegen den Server bauen

In `local.properties` ergänzen:

```
briefampel.server=https://europe-west3-briefampel.cloudfunctions.net/api
```

Danach:

- `./gradlew :app:assembleDebug` → Test-APK, die schon über den Server läuft
- `./gradlew :app:bundleRelease` → AAB für die Play Console (vorher Upload-Schlüssel anlegen, Android Studio › *Build › Generate Signed Bundle*)

## 8. Vor dem Live-Gang prüfen

- [ ] Anmelden mit Google klappt, Einstellungen zeigen die E-Mail
- [ ] Neues Konto hat 3 Analysen
- [ ] Brief scannen → Ampel → Guthaben sinkt um 1
- [ ] 3 Antworten gratis, die 4. kostet 1 Analyse
- [ ] Testkauf (Lizenztester) → Guthaben steigt sofort
- [ ] App während des Kaufs schließen → beim nächsten Start wird nachgebucht
- [ ] Flugmodus → verständliche Meldung, kein Guthaben weg
- [ ] Firestore enthält nur Zähler, keinen Brieftext

Für den Store braucht es danach noch einen **geschlossenen Test mit 12 Testern über 14 Tage** (Pflicht für neue private Entwicklerkonten), Datenschutzerklärung und Formular zur Datensicherheit.
