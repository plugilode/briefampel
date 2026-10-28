package de.briefampel.app.domain

import java.time.LocalDate

/**
 * Beispielbriefe für den ersten Start und den Offline-Ablauf (M1).
 * Die Daten hängen vom heutigen Tag ab, damit die Fristen immer stimmig wirken.
 */
object Beispiele {

    fun fuer(ampel: Ampel, heute: LocalDate): GepruefterBrief = when (ampel) {
        Ampel.ROT -> mahnung(heute)
        Ampel.GELB -> finanzamt(heute)
        Ampel.GRUEN -> buergeramt(heute)
    }

    private fun mahnung(heute: LocalDate): GepruefterBrief {
        val datum = heute.minusDays(11)
        val frist = heute.plusDays(3)
        return GepruefterBrief(
            analyse = LetterAnalysis(
                ampel = Ampel.ROT,
                titel = "Mahnung mit Klageandrohung",
                absender = "Inkasso Nordwest GmbH",
                datum = datum,
                aktenzeichen = "INK-55821/26",
                kurz = "Ein Inkassobüro will 312,40 € für eine angeblich offene Handyrechnung und droht mit dem gerichtlichen Mahnverfahren.",
                verlangt = "312,40 € – davon 189,90 € Hauptforderung, der Rest Inkassokosten und Zinsen.",
                frist = Frist(frist, "Zahlungsfrist aus dem Schreiben, keine gesetzliche Frist. Trotzdem nicht verstreichen lassen."),
                ton = "drohend",
                tonnotiz = "„Letzte Aufforderung“, Hinweis auf die SCHUFA.",
                risiko = "Wahrscheinlich kommt ein Mahnbescheid. Die Inkassokosten wirken überhöht.",
                antwortNoetig = true,
                empfehlung = "Forderung bestreiten und Vertrag plus Rechnung als Nachweis verlangen.",
                stichwortVorschlaege = listOf(
                    "Forderung bestreiten",
                    "Vertrag und Rechnung anfordern",
                    "Inkassokosten zurückweisen",
                    "SCHUFA-Eintrag widersprechen",
                ),
                sprache = "de",
            ),
            volltext = """
                Inkasso Nordwest GmbH · Hafenstraße 3 · 28217 Bremen

                Ihr Zeichen: INK-55821/26
                Datum: ${Fristen.format(datum)}

                LETZTE AUFFORDERUNG VOR GERICHTLICHEM MAHNVERFAHREN

                Sehr geehrte Frau Becker,

                trotz unserer Zahlungserinnerung haben Sie die Forderung unserer Mandantin, der Mobilfunk Süd GmbH, bisher nicht beglichen. Wir fordern Sie hiermit letztmalig auf, den Gesamtbetrag von 312,40 € bis zum ${Fristen.format(frist)} auf das unten genannte Konto zu überweisen.

                Hauptforderung: 189,90 €
                Inkassovergütung: 98,50 €
                Zinsen und Auslagen: 24,00 €

                Sollte der Betrag nicht fristgerecht eingehen, werden wir ohne weitere Ankündigung das gerichtliche Mahnverfahren einleiten. Wir weisen darauf hin, dass unbestrittene Forderungen an Auskunfteien wie die SCHUFA gemeldet werden können.

                Mit freundlichen Grüßen
                Inkasso Nordwest GmbH
            """.trimIndent(),
            seiten = 3,
        )
    }

    private fun finanzamt(heute: LocalDate): GepruefterBrief {
        val datum = heute.minusDays(4)
        val frist = heute.plusDays(18)
        return GepruefterBrief(
            analyse = LetterAnalysis(
                ampel = Ampel.GELB,
                titel = "Rückfrage zur Steuererklärung 2025",
                absender = "Finanzamt Köln-Altstadt",
                datum = datum,
                aktenzeichen = "214/5873/0912",
                kurz = "Das Finanzamt möchte zwei Belege zu deinen Werbungskosten sehen. Kein Vorwurf, nur eine Nachfrage.",
                verlangt = "Nachweis der Fahrtkosten und die Rechnung für den Laptop (1.249,00 €).",
                frist = Frist(frist, "Frist steht im Schreiben."),
                ton = "neutral",
                tonnotiz = "Sachlich und freundlich formuliert.",
                risiko = "Ohne Belege werden die Kosten vermutlich nicht anerkannt.",
                antwortNoetig = true,
                empfehlung = "Belege fotografieren, kurz anschreiben, fertig.",
                stichwortVorschlaege = listOf(
                    "Belege liegen bei",
                    "Fahrtkosten erläutern",
                    "Laptop beruflich genutzt",
                ),
                sprache = "de",
            ),
            volltext = """
                Finanzamt Köln-Altstadt
                Steuernummer 214/5873/0912
                Datum: ${Fristen.format(datum)}

                Einkommensteuererklärung 2025 – Rückfrage

                Sehr geehrte Frau Becker,

                zur Bearbeitung Ihrer Einkommensteuererklärung 2025 bitte ich um Vorlage folgender Unterlagen:

                1. Nachweis der geltend gemachten Fahrtkosten
                2. Rechnung über den erworbenen Laptop (1.249,00 €)

                Bitte reichen Sie die Unterlagen bis zum ${Fristen.format(frist)} ein, gern auch über ELSTER.

                Mit freundlichen Grüßen
                Im Auftrag
            """.trimIndent(),
            seiten = 1,
        )
    }

    private fun buergeramt(heute: LocalDate): GepruefterBrief {
        val datum = heute.minusDays(3)
        val termin = heute.plusDays(10)
        return GepruefterBrief(
            analyse = LetterAnalysis(
                ampel = Ampel.GRUEN,
                titel = "Terminbestätigung Personalausweis",
                absender = "Stadt Köln · Bürgeramt Innenstadt",
                datum = datum,
                aktenzeichen = null,
                kurz = "Dein Termin am ${Fristen.format(termin)} um 09:40 Uhr ist bestätigt. Altes Dokument und ein biometrisches Foto mitbringen.",
                verlangt = null,
                frist = null,
                ton = "freundlich",
                tonnotiz = "Reine Information.",
                risiko = "Keins. Wer den Termin nicht schafft, sagt ihn einfach ab.",
                antwortNoetig = false,
                empfehlung = "Termin vormerken und hingehen.",
                stichwortVorschlaege = listOf("Termin absagen", "Termin verschieben"),
                sprache = "de",
            ),
            volltext = """
                Stadt Köln · Bürgeramt Innenstadt · Laurenzplatz 4

                Terminbestätigung

                Guten Tag,

                hiermit bestätigen wir Ihren Termin zur Beantragung eines Personalausweises am ${Fristen.format(termin)} um 09:40 Uhr.

                Bitte bringen Sie Ihr bisheriges Ausweisdokument und ein aktuelles biometrisches Passfoto mit.

                Ihr Bürgeramt
            """.trimIndent(),
            seiten = 1,
        )
    }
}
