package de.briefampel.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    private val today = LocalDate.of(2026, 10, 26)

    @Test fun `invalid dates are rejected instead of silently corrected`() {
        assertNull(Fristen.parse("31.02.2026"))
        assertNull(Fristen.parse("29.02.2025"))
        assertNull(Fristen.parse("32.10.2026"))
        assertNull(Fristen.parse("2026-10-26"))
        assertNull(Fristen.parse(null))
        assertEquals(LocalDate.of(2024, 2, 29), Fristen.parse("29.02.2024"))
    }
    @Test fun `urgency boundary includes overdue and excludes seven days`() {
        assertTrue(Fristen.istDringend(today.plusDays(6), today))
        assertFalse(Fristen.istDringend(today.plusDays(7), today))
        assertTrue(Fristen.istDringend(today.minusDays(1), today))
        assertFalse(Fristen.istDringend(null, today))
    }
    @Test fun `deadline descriptions cover today tomorrow and overdue`() {
        assertEquals("heute fällig", Fristen.restText(today, today))
        assertEquals("noch 1 Tag", Fristen.restText(today.plusDays(1), today))
        assertEquals("seit gestern vorbei", Fristen.restText(today.minusDays(1), today))
        assertEquals("seit 3 Tagen vorbei", Fristen.restText(today.minusDays(3), today))
        assertEquals("noch 18 Tage", Fristen.restText(today.plusDays(18), today))
    }
    @Test fun `unknown signals default to yellow`() {
        assertEquals(Ampel.GELB, Ampel.fromRaw(null))
        assertEquals(Ampel.GELB, Ampel.fromRaw("blue"))
        assertEquals(Ampel.GRUEN, Ampel.fromRaw(" GRÜN "))
        assertEquals(Ampel.ROT, Ampel.fromRaw("ROT"))
    }
    @Test fun `chip removal preserves user sentences containing the same phrase`() {
        val text = "Ich möchte die Forderung bestreiten, weil ich den Vertrag nicht kenne."
        val withChip = toggleKeyword(text, "Forderung bestreiten")
        assertEquals("$text\nForderung bestreiten", withChip)
        assertEquals(text, toggleKeyword(withChip, "Forderung bestreiten"))
        assertEquals("", toggleKeyword("  Forderung bestreiten  ", "Forderung bestreiten"))
    }
    @Test fun `one analysis covers three replies and never goes negative`() {
        assertEquals(3, Guthaben.freieAntworten(1, 0))
        assertEquals(0, Guthaben.freieAntworten(1, 3))
        assertEquals(2, Guthaben.freieAntworten(2, 4))
        assertEquals(0, Guthaben.freieAntworten(1, 7))
    }
    @Test fun `examples keep appointment separate from response deadline`() {
        assertFalse(Beispiele.fuer(Ampel.GRUEN, today).analyse.antwortNoetig)
        assertNull(Beispiele.fuer(Ampel.GRUEN, today).analyse.frist)
        assertEquals(today.plusDays(3), Beispiele.fuer(Ampel.ROT, today).analyse.frist?.datum)
    }
    @Test fun `missing sender data remains placeholders`() {
        val prompt = Prompts.draft(Beispiele.fuer(Ampel.ROT, today).analyse, "Vertrag anfordern", Tone.SACHLICH, Sender(), today)
        assertTrue(prompt.contains("[Name]"))
        assertTrue(prompt.contains("[Anschrift]"))
        assertTrue(prompt.contains("26.10.2026"))
        assertTrue(prompt.contains("Vertrag anfordern"))
    }
}
