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

