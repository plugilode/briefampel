package de.briefampel.app.domain

enum class Ampel {
    ROT, GELB, GRUEN;

    companion object {
        /** Unbekannte oder fehlende Werte gelten als Gelb (Spezifikation 4.1). */
        fun fromRaw(raw: String?): Ampel = when (raw?.trim()?.lowercase()) {
            "rot" -> ROT
            "gruen", "grün" -> GRUEN
            else -> GELB
        }
    }
}
