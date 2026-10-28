package de.briefampel.app.domain

data class Sender(val name: String = "", val address: String = "", val email: String = "") {
    fun forPrompt() = listOf(name.ifBlank { "[Name]" }, address.ifBlank { "[Anschrift]" }, email.ifBlank { "[E-Mail, falls gewünscht]" }).joinToString("\n")
}

enum class Tone(val label: String) { FREUNDLICH("Freundlich"), SACHLICH("Sachlich"), BESTIMMT("Bestimmt"), SCHARF("Scharf") }

/** Whole lines only: deselecting a chip must never alter a user's sentence. */
fun toggleKeyword(text: String, keyword: String): String {
    val lines = text.lines()
    return if (lines.any { it.trim() == keyword }) lines.filterNot { it.trim() == keyword }.joinToString("\n").trim()
    else listOf(text.trim(), keyword).filter(String::isNotEmpty).joinToString("\n")
}

data class Usage(val date: String = "", val count: Int = 0) {
    fun remaining(today: String): Int = (3 - if (date == today) count else 0).coerceIn(0, 3)
    fun consume(today: String) = Usage(today, if (date == today) count + 1 else 1)
}
