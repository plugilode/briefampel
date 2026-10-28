# Abrechnung: Briefpakete, Startguthaben, Google-Konto

Entscheidungen (28.10.2026):
- **3 Analysen kostenlos**, einmalig als Startguthaben.
- Danach **Pakete als Einmalkauf** (kein Abo):
  - 1,99 € für 10 Analysen
  - 4,99 € für 30 Analysen
  - 9,99 € für 100 Analysen
- **Später eventuell**: 4,99 € im Monat unbegrenzt mit Fair-Use-Grenze.
- Eine Analyse ist eine Briefprüfung samt den Antworten dazu, siehe „Was zählt als Analyse“.
- **Anmeldung mit Google-Konto.** Das Guthaben überlebt Neuinstallation und Handywechsel. Briefe und Verlauf bleiben trotzdem nur auf dem Handy.

## Was ein Brief kostet

Preise aus Googles Preisliste (ai.google.dev/gemini-api/docs/pricing), Stand 28.10.2026, Bezahlstufe:

| Modell | Eingabe je 1 Mio. Token | Ausgabe je 1 Mio. Token |
|---|---|---|
| Gemini 3.6 / 3.8 Flash bis 31.12.2026 | 0,75 $ | 3,75 $ |
| Gemini 3.6 / 3.8 Flash ab 01.01.2027 | 1,50 $ | 7,50 $ |
| Gemini 3.5 Flash-Lite | 0,30 $ | 2,50 $ |

Schätzung für einen Brief mit 3 Seiten: Die Prüfung hat etwa 6.000 Token Eingabe und 2.500 Token Ausgabe, eine Antwort etwa 8.000 Token Eingabe und 2.500 Token Ausgabe.

| | Prüfung | + 1 Antwort | zusammen |
|---|---|---|---|
| Flash 2026 | ≈ 1,4 ct | ≈ 1,5 ct | **≈ 3 ct** |
| Flash ab 2027 | ≈ 2,8 ct | ≈ 3,1 ct | **≈ 6 ct** |
| Flash-Lite | ≈ 0,8 ct | ≈ 0,9 ct | ≈ 1,7 ct |

Das sind Schätzungen. Die echten Werte messen wir im geschlossenen Test, die Token-Zahlen stehen in jeder Gemini-Antwort.

**Rechnung pro Paket.** Google behält 15 %. Angenommen ist je Analyse eine Antwort, also etwa 3 ct (2026) oder 6 ct (ab 2027):

| Paket | dir bleiben nach Google | Gemini-Kosten 2026 | ab 2027 | Rest 2026 | Rest ab 2027 |
|---|---|---|---|---|---|
| 10 für 1,99 € | 1,69 € | 0,30 € | 0,60 € | ≈ 1,40 € | ≈ 1,10 € |
| 30 für 4,99 € | 4,24 € | 0,90 € | 1,80 € | ≈ 3,35 € | ≈ 2,45 € |
| 100 für 9,99 € | 8,49 € | 3,00 € | 6,00 € | ≈ 5,50 € | ≈ 2,50 € |

Davon gehen noch Steuern ab, siehe unten. Das 100er-Paket bleibt auch 2027 im Plus, aber knapp. Dort lohnt sich ab 2027 eventuell Flash-Lite für die Prüfung (≈ 0,8 ct statt 2,8 ct), wenn die Qualität reicht. Das testen wir.

**Monatsabo „unbegrenzt“ (später):** Von 4,99 € bleiben dir 4,24 €. Ab 2027 sind das etwa 70 Analysen, bevor du draufzahlst. Fair-Use-Vorschlag: **50 Analysen im Monat**, danach ein freundlicher Hinweis. Kaum jemand bekommt so viel Post.

### Was zählt als Analyse

Eine Analyse = eine Briefprüfung **plus bis zu 3 Antwortentwürfe** zu diesem Brief („Neu formulieren“ inklusive). Weitere Entwürfe zum selben Brief zählen wieder als Analyse. So bleiben die Kosten pro Analyse planbar, und niemand fühlt sich beim Umformulieren gegängelt. Schlägt eine Prüfung bei Gemini fehl, wird nichts abgezogen.

**Startguthaben:** 3 Analysen kosten dich pro neuem Nutzer höchstens etwa 10 bis 20 Cent.

Gemini 3.5 Flash, das die Test-APK nutzt, steht nicht mehr in der Preisliste. Es wird nicht abgeschaltet, ist aber nicht mehr wählbar. Für den Store wechseln wir auf 3.6 oder 3.8 Flash (beim Test im Oktober zeitweise überlastet), mit 3.5 Flash-Lite als Ausweichmodell.

## Warum ein eigener kleiner Server nötig ist

Heute steckt dein Gemini-Schlüssel in der Test-APK. Das geht nur auf deinem eigenen Handy. Für den Store gilt:

1. **Der Schlüssel darf nicht in die App**, sonst kann ihn jeder auslesen und auf deine Kosten nutzen.
2. **Das Guthaben muss auf dem Server gezählt werden.** Der Zähler auf dem Handy lässt sich durch Neuinstallation umgehen.
3. **Käufe müssen auf dem Server geprüft werden**, bevor Guthaben gutgeschrieben wird.

Deshalb kommt in dein Firebase-Projekt ein kleines Backend:

```
Handy (App)                         Firebase (dein Projekt, Region Frankfurt)
───────────                         ─────────────────────────────────────────
Anmelden mit Google      ───────►   Firebase Auth (wer bist du?)
App Check (Play Integrity) ─────►   nur echte Briefampel-Apps dürfen rein
Paket kaufen (Play Billing) ────►   Funktion „kaufBestaetigen“: prüft den Kauf bei Google Play,
                                    schreibt Guthaben gut (Firestore), quittiert den Kauf
Brief prüfen / Antwort  ────────►   Funktion „pruefen“ / „antworten“: zieht 1 Brief ab,
                                    ruft Gemini mit dem Server-Schlüssel auf, streamt zurück
```

Gespeichert wird pro Nutzer nur: Konto-ID, Guthaben, Käufe (Bestell-ID). **Keine Briefe, keine Antworten, keine Protokolle mit Inhalt.** Briefe gehen nur durch, an Gemini (Bezahlstufe, Google nutzt die Daten dann nicht zum Training).

Eine Analyse kostet 1 Guthaben (Prüfung plus bis zu 3 Antworten). Bei einem Gemini-Fehler wird das Guthaben wieder gutgeschrieben.

## Was du anlegen musst (nur du kannst das)

Reihenfolge, damit nichts blockiert:

1. **Google-Play-Entwicklerkonto**: play.google.com/console, einmalig 25 $ und Identitätsprüfung (dauert ein paar Tage). Am besten sofort starten.
2. **Zahlungsprofil (Händlerkonto)** in der Play Console, damit du In-App-Käufe verkaufen darfst. Achtung: Als Privatperson mit kostenpflichtigen Inhalten wird deine Adresse im Store angezeigt.
3. **Firebase-Projekt** auf console.firebase.google.com anlegen, **Blaze-Tarif** (Zahlen nach Verbrauch) mit deiner Karte aktivieren.
4. **Budget-Alarm** in Google Cloud Billing, zum Beispiel bei 10 € im Monat, plus Benachrichtigung. Zusätzlich setze ich im Server eine harte Tagesgrenze.
5. In der Play Console die **Produkte anlegen** als „Verbrauchsartikel“: `analysen_10` (1,99 €), `analysen_30` (4,99 €), `analysen_100` (9,99 €). Das Abo kommt später.
6. Mir sagen, wenn das steht. Dann brauche ich die `google-services.json` (die ist nicht geheim, kommt aber nicht in Git) und richte den Rest mit dir ein.

Steuern: Einnahmen sind steuerpflichtig. Für den Anfang reicht meist die Kleinunternehmerregelung; Google führt die Umsatzsteuer gegenüber Käufern in der EU selbst ab. Im Zweifel kurz einen Steuerberater fragen.

## Was ich baue (geht schon vor deinen Konten)

| Schritt | Inhalt |
|---|---|
| A ✅ | **Ehrliche Kostenanzeige in der App**: „Noch 3 von 3 Gratis-Briefen“, Guthaben im Kopf, ein Paket-Bildschirm mit Preisen. Vor dem letzten Gratis-Brief kommt ein freundlicher Hinweis. |
| B | **Anmelden mit Google** (Android Credential Manager) als eigener Schritt vor dem ersten Kauf, nicht vor dem ersten Brief. Die Gratis-Briefe gehen ohne Anmeldung. |
| C | **Google Play Billing** in der App: Pakete laden, kaufen, Kauf an den Server schicken. |
| D | **Firebase Functions** (TypeScript): Kauf prüfen, Guthaben buchen, Gemini über den Server aufrufen, Tagesgrenze, Rückbuchung bei Fehlern. Dazu Tests mit dem Firebase-Emulator. |
| E | **Release-Variante** nutzt nur noch den Server. Der eigene Schlüssel bleibt allein in der Debug-Test-APK. |

A, B (ohne echte Konten), C (gegen Testprodukte) und D (lokal im Emulator) baue und teste ich, bevor deine Konten fertig sind. Live geht es erst mit deinen Konten und einem geschlossenen Test (12 Tester, 14 Tage).
