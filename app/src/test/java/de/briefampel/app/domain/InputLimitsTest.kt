package de.briefampel.app.domain

import de.briefampel.app.data.Attachment
import de.briefampel.app.data.LetterInput
import org.junit.Assert.*
import org.junit.Test

class InputLimitsTest {
    private fun image(id: Int) = Attachment("$id", "$id.jpg", "image/jpeg", byteArrayOf(id.toByte()), 1)
    @Test(expected = IllegalArgumentException::class) fun `empty input cannot be sent`() { LetterInput().validate() }
    @Test(expected = IllegalArgumentException::class) fun `oversized text is rejected`() { LetterInput(text = "a".repeat(60_001)).validate() }
    @Test(expected = IllegalArgumentException::class) fun `eleven images are rejected`() { LetterInput(attachments = (1..11).map(::image)).validate() }
    @Test(expected = IllegalArgumentException::class) fun `page limit spans all attached files`() {
        LetterInput(attachments = listOf(Attachment("pdf", "letter.pdf", "application/pdf", byteArrayOf(1), 12), image(1))).validate()
    }
    @Test(expected = IllegalArgumentException::class) fun `aggregate byte limit includes every file`() {
        LetterInput(attachments = (1..2).map { Attachment("$it", "letter.pdf", "application/pdf", ByteArray(8 * 1024 * 1024), 1) }).validate()
    }
    @Test fun `cache key detects changed document bytes and text`() {
        val input = LetterInput("Brief", listOf(image(1)))
        assertEquals(input.fingerprint(), input.copy().fingerprint())
        assertNotEquals(input.fingerprint(), input.copy(text = "Anderer Brief").fingerprint())
        assertNotEquals(input.fingerprint(), input.copy(attachments = listOf(image(2))).fingerprint())
    }
    @Test fun `maximum valid mixed input is accepted`() {
        LetterInput("a".repeat(60_000), (1..10).map(::image) + Attachment("pdf", "letter.pdf", "application/pdf", byteArrayOf(1), 2)).validate()
    }
}
