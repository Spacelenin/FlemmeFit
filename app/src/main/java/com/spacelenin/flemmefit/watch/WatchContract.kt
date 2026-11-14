package com.spacelenin.flemmefit.watch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Contrat d'échange avec l'app montre (Wearable Data Layer, Bluetooth). Copie conforme de wear/…/Model.kt :
 * toute modification se fait des deux côtés, en champs facultatifs pour qu'une version plus ancienne suive.
 *  - Téléphone → montre : `/flemmefit/today`, clé "json" = [WatchToday].
 *  - Montre → téléphone : `/flemmefit/log/<id>` = [WatchLog] (un exo fini, ou la fin de séance).
 *  - Montre → téléphone : `/flemmefit/watch` = [WatchStatus] (version, batterie, envois en attente).
 *  - Montre → téléphone : message `/flemmefit/sync` = « renvoie la séance du jour ».
 *  - Téléphone → montre : canal `/flemmefit/apk` = l'APK de la dernière version montre.
 */

val WatchJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

object WatchPaths {
    const val TODAY = "/flemmefit/today"
    const val LOG = "/flemmefit/log/"
    const val STATUS = "/flemmefit/watch"
    const val SYNC = "/flemmefit/sync"
    const val APK = "/flemmefit/apk"
    /** Message envoyé juste avant l'APK : sa taille en octets, pour la progression côté montre. */
    const val APK_SIZE = "/flemmefit/apk-size"
    const val KEY_JSON = "json"
}

@Serializable
data class WatchToday(
    val v: Int = 1,
    val date: String,
    val program: String = "",
    /** Programme concerné, renvoyé dans les [WatchLog]. */
    val programId: String = "",
    val mode: String = MODE_NORMAL,
    val title: String? = null,
    val position: String? = null,
    /** Indice du jour dans le programme, renvoyé dans les [WatchLog]. */
    val session: Int = -1,
    val streak: Int = 0,
    val minutes: Int? = null,
    val next: String? = null,
    val bossTomorrow: Boolean = false,
    val exercises: List<WatchExercise> = emptyList(),
    val week: WatchWeek? = null,
    val friends: List<WatchFriend> = emptyList(),
    val friendNews: String? = null,
    val stats: List<WatchStat> = emptyList(),
    /** Les ressentis du téléphone, dans l'ordre de leurs indices. */
    val feelings: List<String> = emptyList(),
    /** Version montre minimale pour suivre ce téléphone : en dessous, la montre demande sa mise à jour. */
    val minWatch: Int = 0,
    val sentAt: Long = 0,
) {
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
    val i: Int,
    val name: String,
    val how: String? = null,
    val sets: Int = 1,
    val reps: Int? = null,
    val seconds: Int? = null,
    val restSec: Int = 60,
    val allOut: Boolean = false,
    val target: Int? = null,
    val level: Int? = null,
    val levelCount: Int? = null,
    val optional: Boolean = false,
    val sided: Boolean = false,
    val tag: String? = null,
    val done: Boolean = false,
)

@Serializable
data class WatchWeek(
    val label: String = "",
    val days: List<WatchDay> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    val note: String? = null,
)

@Serializable
data class WatchDay(val letter: String, val state: String)

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

@Serializable
data class WatchLog(
    val id: String,
    val date: String,
    val session: Int,
    val programId: String = "",
    val exercise: Int? = null,
    val values: List<Int> = emptyList(),
    val seconds: Boolean = false,
    val skipped: Boolean = false,
    val feeling: Int? = null,
    val minutes: Int? = null,
    val battery: Int? = null,
    /** Seulement le ressenti de l'exo [exercise] (choisi sur l'écran « Exo suivant »). */
    val onlyFeeling: Boolean = false,
    /** Case cochée (true) ou décochée (false) depuis la liste des exos de la montre. */
    val check: Boolean? = null,
    val at: Long,
)

/** État de la montre, publié par elle : de quoi remplir l'encart « Montre » des réglages. */
@Serializable
data class WatchStatus(
    val version: Int,
    val battery: Int? = null,
    /** Envois en attente (téléphone pas à portée). */
    val pending: Int = 0,
    val inSession: Boolean = false,
    /** Dernière séance du jour reçue par la montre (« 2026-11-14 »). */
    val todayDate: String? = null,
    val at: Long,
)
