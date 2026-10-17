package com.spacelenin.flemmefit.model

import java.util.Locale

/** Lecture et écriture des valeurs chiffrées écrites en texte libre dans le format. */
object Measures {

    /** "12" → 12. Tout autre texte ("8-10", "max") n'est pas chiffré. */
    fun reps(text: String?): Int? = text?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    private val minSec = Regex("""^(\d+)\s*(?:min|mn|m)\s*(?:(\d+)\s*s?)?$""")
    private val secOnly = Regex("""^(\d+)\s*(?:s|sec|secs|secondes?)?$""")
    private val clock = Regex("""^(\d+):(\d{1,2})$""")

    /** "45s", "45", "2min", "1min30", "1m30s", "1:30" → secondes. */
    fun seconds(text: String?): Int? {
        val t = text?.trim()?.lowercase()?.replace(" ", "") ?: return null
        clock.matchEntire(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        minSec.matchEntire(t)?.let { m ->
            return m.groupValues[1].toInt() * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        }
        secOnly.matchEntire(t)?.let { return it.groupValues[1].toInt() }
        return null
    }

    fun formatSeconds(total: Int): String {
        val m = total / 60
        val s = total % 60
        return when {
            m == 0 -> "${s}s"
            s == 0 -> "${m}min"
            else -> "${m}min${s.toString().padStart(2, '0')}"
        }
    }

    data class Load(val value: Double, val unit: String)

    private val loadPattern = Regex("""^(\d+(?:[.,]\d+)?)\s*([a-zA-Z]*)$""")

    /** "8 kg", "2.5kg", "10" → valeur + unité. "élastique rouge" n'est pas chiffré. */
    fun load(text: String?): Load? {
        val m = loadPattern.matchEntire(text?.trim() ?: return null) ?: return null
        return Load(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2].ifEmpty { "kg" })
    }

    fun formatLoad(load: Load): String {
        val v = if (load.value % 1.0 == 0.0) load.value.toLong().toString() else String.format(Locale.FRENCH, "%.1f", load.value)
        return "$v ${load.unit}"
    }
}
