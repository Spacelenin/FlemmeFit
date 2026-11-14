package com.spacelenin.flemmefit.montre

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Contrat d'échange avec l'app du téléphone (Wearable Data Layer, Bluetooth uniquement).
 *  - Téléphone → montre : `/flemmefit/today`, clé "json" = [WatchToday]. Tout est déjà calculé par le téléphone.
 *  - Montre → téléphone : `/flemmefit/log/<id>`, clé "json" = [WatchLog] (un exo fini, ou la fin de séance).
 *  - Montre → téléphone : message `/flemmefit/sync` = « renvoie-moi la séance du jour ».
 * Champs facultatifs avec valeur par défaut : une version plus récente d'un côté ne casse pas l'autre.
 */

val WatchJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/** La séance du jour et ce qu'il faut pour les pages autour de l'accueil. */
@Serializable
data class WatchToday(
    val v: Int = 1,
    /** Jour concerné, « 2026-11-14 ». */
    val date: String,
    val program: String = "",
    /** Programme concerné, renvoyé dans les [WatchLog]. */
    val programId: String = "",
    /** normal, boss, burst, deload, chill, form, rest (repos), none (pas de programme en cours). */
    val mode: String = MODE_NORMAL,
    /** Titre de la séance (« Pompes et Beat Saber »), null un jour de repos. */
    val title: String? = null,
    /** « Semaine 3 · séance 4/5 ». */
    val position: String? = null,
    /** Indice de la séance dans le programme, renvoyé tel quel dans les [WatchLog]. */
    val session: Int = -1,
    val streak: Int = 0,
    /** Durée estimée en minutes. */
    val minutes: Int? = null,
    /** « Prochaine : samedi · Squats » un jour de repos. */
    val next: String? = null,
    val bossTomorrow: Boolean = false,
    val exercises: List<WatchExercise> = emptyList(),
    val week: WatchWeek? = null,
    val friends: List<WatchFriend> = emptyList(),
    val friendNews: String? = null,
    val stats: List<WatchStat> = emptyList(),
    /** Les ressentis du téléphone, dans l'ordre de leurs indices. */
    val feelings: List<String> = emptyList(),
    /** Version montre minimale pour suivre ce téléphone : en dessous, mise à jour requise. */
    val minWatch: Int = 0,
    /** Heure d'envoi par le téléphone (ms). */
    val sentAt: Long = 0,
) {
    val isSessionDay get() = title != null && exercises.isNotEmpty()

    companion object {
        const val MODE_NORMAL = "normal"
        const val MODE_BOSS = "boss"
        const val MODE_BURST = "burst"
        const val MODE_DELOAD = "deload"
        const val MODE_CHILL = "chill"
        const val MODE_FORM = "form"
        const val MODE_REST = "rest"
        const val MODE_NONE = "none"
    }
}

@Serializable
data class WatchExercise(
    /** Indice de l'exercice dans la séance (renvoyé dans les [WatchLog]). */
    val i: Int,
    val name: String,
    /** Consigne courte. */
    val how: String? = null,
    val sets: Int = 1,
    /** Répétitions par série (exercice en reps). */
    val reps: Int? = null,
    /** Secondes par série (exercice en durée). */
    val seconds: Int? = null,
    val restSec: Int = 60,
    /** Dernière série à fond (Boss, max) : on saisit ce qu'on a fait. */
    val allOut: Boolean = false,
    /** Objectif de la série à fond. */
    val target: Int? = null,
    /** Palier (à partir de 1) et nombre de paliers, pour les étoiles. */
    val level: Int? = null,
    val levelCount: Int? = null,
    val optional: Boolean = false,
    /** Une série par côté, en alternance (gauche puis droite). */
    val sided: Boolean = false,
    /** Mention courte : « excentrique », « crevé », « variante »… */
    val tag: String? = null,
    /** Déjà fait (sur le téléphone ou plus tôt sur la montre). */
    val done: Boolean = false,
) {
    val timed get() = seconds != null && reps == null
    /** Rien de chiffré : un simple « Série faite ». */
    val plain get() = reps == null && seconds == null
    /** La valeur prévue d'une série. */
    val planned get() = reps ?: seconds ?: 0
}

@Serializable
data class WatchWeek(
    /** « sem. 3 ». */
    val label: String = "",
    val days: List<WatchDay> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    /** « récup la semaine prochaine ». */
    val note: String? = null,
)

@Serializable
data class WatchDay(
    /** « L », « M »… */
    val letter: String,
    /** done, rest, today, planned, boss, missed. */
    val state: String,
)

@Serializable
data class WatchFriend(
    val name: String,
    val points: Int,
    val me: Boolean = false,
    /** Mini-fiche : le jour (« Séance faite », « 2/3 exos », « Repos »), la série, les Boss, les exploits. */
    val today: String? = null,
    val streak: Int = 0,
    val boss: Int = 0,
    val exploits: Int = 0,
    val lastExploit: String? = null,
)

@Serializable
data class WatchStat(val label: String, val value: String)

/** Ce que la montre renvoie au téléphone. */
@Serializable
data class WatchLog(
    val id: String,
    val date: String,
    val session: Int,
    val programId: String = "",
    /** Exercice fini (indice [WatchExercise.i]), ou null pour la fin de séance. */
    val exercise: Int? = null,
    /** Valeur de chaque série, dans l'ordre (reps ou secondes). */
    val values: List<Int> = emptyList(),
    val seconds: Boolean = false,
    /** Passé sans le faire. */
    val skipped: Boolean = false,
    /** Ressenti de la séance (0 à 3), sur la fin de séance. */
    val feeling: Int? = null,
    val minutes: Int? = null,
    /** Batterie consommée pendant la séance, en points de %. */
    val battery: Int? = null,
    /** Seulement le ressenti de l'exo [exercise] (choisi sur l'écran « Exo suivant »). */
    val onlyFeeling: Boolean = false,
    /** Case cochée (true) ou décochée (false) depuis la liste des exos de la montre. */
    val check: Boolean? = null,
    val at: Long,
)

/** État de la montre, publié pour l'encart « Montre » des réglages du téléphone. */
@Serializable
data class WatchStatus(
    val version: Int,
    val battery: Int? = null,
    val pending: Int = 0,
    val inSession: Boolean = false,
    val todayDate: String? = null,
    val at: Long,
)

/** Séance d'exemple, pour essayer l'app avant que le téléphone ne lui parle. Rien n'est envoyé. */
fun demoToday(date: String) = WatchToday(
    date = date,
    program = "Routine maison",
    mode = WatchToday.MODE_NORMAL,
    title = "Pompes et Beat Saber",
    position = "Semaine 3 · séance 4/5",
    session = -1,
    streak = 13,
    minutes = 55,
    exercises = listOf(
        WatchExercise(0, "Pompes classiques", "Mains sous les épaules, corps gainé. Descends jusqu'à 5 cm du sol, remonte sans à-coups.", sets = 3, reps = 12, restSec = 60, level = 3, levelCount = 4),
        WatchExercise(1, "Pompes diamant", "Mains collées en losange sous la poitrine, coudes le long du corps.", sets = 3, reps = 1, restSec = 60, allOut = true, target = 6),
        WatchExercise(2, "Beat Saber", "Niveau libre, on bouge les bras et les jambes.", sets = 1, seconds = 40 * 60, restSec = 0),
    ),
    week = WatchWeek(
        label = "sem. 3",
        days = listOf(
            WatchDay("L", "done"), WatchDay("M", "done"), WatchDay("M", "rest"), WatchDay("J", "done"),
            WatchDay("V", "today"), WatchDay("S", "planned"), WatchDay("D", "rest"),
        ),
        done = 3, total = 5, note = "récup la semaine prochaine",
    ),
    friends = listOf(WatchFriend("Ninjarque", 31), WatchFriend("Toi", 30, me = true), WatchFriend("Levi", 30)),
    friendNews = "Ninjarque : séance faite",
    stats = listOf(
        WatchStat("série", "🔥 13"), WatchStat("séances", "13"), WatchStat("pompes", "268"),
        WatchStat("Beat Saber", "101 min"), WatchStat("Boss vaincus", "💀 1"), WatchStat("exploits", "🏆 4"),
    ),
)
