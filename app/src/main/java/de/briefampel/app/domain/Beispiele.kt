package de.briefampel.app.domain

import java.time.LocalDate

/**
 * Beispielbriefe für den ersten Start und den Offline-Ablauf (M1).
 * Die Daten hängen vom heutigen Tag ab, damit die Fristen immer stimmig wirken.
 */
object Beispiele {

    /** Der Originalbrief bleibt deutsch, die Auswertung kommt in der gewählten Sprache – wie im echten Einsatz. */
    fun fuer(ampel: Ampel, heute: LocalDate, sprache: Sprache = Sprache.DE): GepruefterBrief = when (ampel) {
        Ampel.ROT -> mahnung(heute, sprache)
        Ampel.GELB -> finanzamt(heute, sprache)
        Ampel.GRUEN -> buergeramt(heute, sprache)
    }

    private fun mahnung(heute: LocalDate, sp: Sprache): GepruefterBrief {
        val datum = heute.minusDays(11)
        val frist = heute.plusDays(3)
        return GepruefterBrief(
            analyse = LetterAnalysis(
                ampel = Ampel.ROT,
                titel = sp.text("Mahnung mit Klageandrohung"),
                absender = "Inkasso Nordwest GmbH",
                datum = datum,
                aktenzeichen = "INK-55821/26",
                kurz = sp.text("Ein Inkassobüro will 312,40 € für eine angeblich offene Handyrechnung und droht mit dem gerichtlichen Mahnverfahren."),
                verlangt = sp.text("312,40 € – davon 189,90 € Hauptforderung, der Rest Inkassokosten und Zinsen."),
                frist = Frist(frist, sp.text("Zahlungsfrist aus dem Schreiben, keine gesetzliche Frist. Trotzdem nicht verstreichen lassen.")),
                ton = sp.text("drohend"),
                tonnotiz = sp.text("„Letzte Aufforderung“, Hinweis auf die SCHUFA."),
                risiko = sp.text("Wahrscheinlich kommt ein Mahnbescheid. Die Inkassokosten wirken überhöht."),
                antwortNoetig = true,
                empfehlung = sp.text("Forderung bestreiten und Vertrag plus Rechnung als Nachweis verlangen."),
                stichwortVorschlaege = listOf(
                    sp.text("Forderung bestreiten"),
                    sp.text("Vertrag und Rechnung anfordern"),
                    sp.text("Inkassokosten zurückweisen"),
                    sp.text("SCHUFA-Eintrag widersprechen"),
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

    private fun finanzamt(heute: LocalDate, sp: Sprache): GepruefterBrief {
        val datum = heute.minusDays(4)
        val frist = heute.plusDays(18)
        return GepruefterBrief(
            analyse = LetterAnalysis(
                ampel = Ampel.GELB,
                titel = sp.text("Rückfrage zur Steuererklärung 2025"),
                absender = "Finanzamt Köln-Altstadt",
                datum = datum,
                aktenzeichen = "214/5873/0912",
                kurz = sp.text("Das Finanzamt möchte zwei Belege zu deinen Werbungskosten sehen. Kein Vorwurf, nur eine Nachfrage."),
                verlangt = sp.text("Nachweis der Fahrtkosten und die Rechnung für den Laptop (1.249,00 €)."),
                frist = Frist(frist, sp.text("Frist steht im Schreiben.")),
                ton = sp.text("neutral"),
                tonnotiz = sp.text("Sachlich und freundlich formuliert."),
                risiko = sp.text("Ohne Belege werden die Kosten vermutlich nicht anerkannt."),
                antwortNoetig = true,
                empfehlung = sp.text("Belege fotografieren, kurz anschreiben, fertig."),
                stichwortVorschlaege = listOf(
                    sp.text("Belege liegen bei"),
                    sp.text("Fahrtkosten erläutern"),
                    sp.text("Laptop beruflich genutzt"),
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

    private fun buergeramt(heute: LocalDate, sp: Sprache): GepruefterBrief {
        val datum = heute.minusDays(3)
        val termin = heute.plusDays(10)
        return GepruefterBrief(
            analyse = LetterAnalysis(
                ampel = Ampel.GRUEN,
                titel = sp.text("Terminbestätigung Personalausweis"),
                absender = "Stadt Köln · Bürgeramt Innenstadt",
                datum = datum,
                aktenzeichen = null,
                kurz = sp.text("Dein Termin am %s um 09:40 Uhr ist bestätigt. Altes Dokument und ein biometrisches Foto mitbringen.", Fristen.format(termin)),
                verlangt = null,
                frist = null,
                ton = sp.text("freundlich"),
                tonnotiz = sp.text("Reine Information."),
                risiko = sp.text("Keins. Wer den Termin nicht schafft, sagt ihn einfach ab."),
                antwortNoetig = false,
                empfehlung = sp.text("Termin vormerken und hingehen."),
                stichwortVorschlaege = listOf(sp.text("Termin absagen"), sp.text("Termin verschieben")),
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
