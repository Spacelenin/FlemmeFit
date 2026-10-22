package com.spacelenin.flemmefit.model

/** Un badge : une famille (pompes, Boss vaincus…) et le palier atteint. */
data class Badge(
    val family: String,
    /** Unité affichée après le palier (« pompes », « h de boxe »…). */
    val unit: String,
    val value: Int,
    /** Palier atteint (index dans [Badges.TIERS]), -1 si aucun. */
    val tier: Int,
) {
    val reached: Int? get() = Badges.TIERS.getOrNull(tier)
    val next: Int? get() = Badges.TIERS.getOrNull(tier + 1)
    val medal: String get() = Badges.MEDALS.getOrElse(tier) { "·" }
}

/** Badges de gros paliers, exponentiels : 10, 50, 100, 500, 1 000, 10 000. */
object Badges {
    val TIERS = listOf(10, 50, 100, 500, 1000, 10000)
    val MEDALS = listOf("🥉", "🥈", "🥇", "🏅", "🏆", "👑")

    fun tierOf(value: Int): Int = TIERS.indexOfLast { value >= it }

    /**
     * Les badges de tout ce qui se compte : séances faites, Boss vaincus, chaque compteur en reps,
     * et les compteurs en temps comptés en heures.
     */
    fun of(stats: Stats): List<Badge> = buildList {
        add(badge("Séances", "séances", stats.sessionsDone))
        add(badge("Boss", "Boss vaincus", stats.bossDefeated))
        stats.counters.forEach { c ->
            if (c.isTime) add(badge(c.name, "h de ${c.name.lowercase()}", c.seconds / 3600))
            else add(badge(c.name, c.name.lowercase(), c.reps))
        }
    }

    private fun badge(family: String, unit: String, value: Int) = Badge(family, unit, value, tierOf(value))
}
