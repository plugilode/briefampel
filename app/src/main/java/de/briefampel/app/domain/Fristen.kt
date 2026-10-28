package de.briefampel.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

object Fristen {
    val DEUTSCH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

    /** Unter dieser Anzahl Tage wird eine Frist rot hervorgehoben. */
    const val DRINGEND_AB_TAGEN = 7

    fun parse(text: String?): LocalDate? = try {
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it, DEUTSCH) }
    } catch (e: DateTimeParseException) {
        null
    }

    fun format(datum: LocalDate): String = datum.format(DEUTSCH)

    fun tageBis(datum: LocalDate, heute: LocalDate): Long = ChronoUnit.DAYS.between(heute, datum)

    fun istDringend(datum: LocalDate?, heute: LocalDate): Boolean =
        datum != null && tageBis(datum, heute) < DRINGEND_AB_TAGEN

    fun restText(datum: LocalDate, heute: LocalDate, sprache: Sprache = Sprache.DE): String {
        val tage = tageBis(datum, heute)
        return when {
            tage < -1 -> sprache.text("seit %d Tagen vorbei", -tage)
            tage == -1L -> sprache.text("seit gestern vorbei")
            tage == 0L -> sprache.text("heute fällig")
            tage == 1L -> sprache.text("noch 1 Tag")
            else -> sprache.text("noch %d Tage", tage)
        }
    }
}
