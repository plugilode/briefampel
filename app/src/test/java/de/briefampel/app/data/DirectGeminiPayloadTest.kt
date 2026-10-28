package de.briefampel.app.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class DirectGeminiPayloadTest {
    @Test fun `multiple PDFs and images are sent inline as the same letter`() {
        val input = LetterInput("Original", listOf(
            Attachment("1", "one.pdf", "application/pdf", byteArrayOf(1, 2, 3), 1),
            Attachment("2", "two.jpg", "image/jpeg", byteArrayOf(4, 5, 6), 1),
        ))
        val payload = DirectGeminiProvider.payload(input, "Regeln", "Prüfen", true)
        val parts = payload.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(4, parts.length())
        assertEquals("application/pdf", parts.getJSONObject(2).getJSONObject("inlineData").getString("mimeType"))
        assertEquals("AQID", parts.getJSONObject(2).getJSONObject("inlineData").getString("data"))
        assertEquals("image/jpeg", parts.getJSONObject(3).getJSONObject("inlineData").getString("mimeType"))
    }
}
