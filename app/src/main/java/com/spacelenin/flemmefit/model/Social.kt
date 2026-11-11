package com.spacelenin.flemmefit.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * Carte publiée dans le canal commun : uniquement un pseudo et des totaux, rien d'autre.
 * Champs nouveaux = optionnels, pour que des versions différentes de l'app se comprennent.
 */
@Serializable
data class FriendCard(
    val v: Int = 1,
    val id: String,
    val pseudo: String,
    /** Date (ISO) des chiffres « du jour ». */
    val date: String,
    val updatedAt: Long,
    val todayDone: Int = 0,
    val todayTotal: Int = 0,
    val todayTitle: String? = null,
    val streak: Int = 0,
    val sessionsDone: Int = 0,
    val bossDefeated: Int = 0,
    val exploits: Int = 0,
    val lastExploit: String? = null,
    val build: Int = 0,
    val bursts: Int = 0,
    /** Défis surprise réussis. */
    val defis: Int = 0,
    /** Détail de la séance du jour (null : pas partagé, ou version qui ne l'envoie pas). */
    val todayExercises: List<SharedExercise>? = null,
    /** Ressenti de la séance du jour : 0 dur, 1 bien, 2 en forme. */
    val todayFeeling: Int? = null,
    /** Jour Boss : [BOSS_WON], [BOSS_LOST] ou [BOSS_ONGOING] ; null un jour normal. */
    @SerialName("tb") val todayBoss: String? = null,
    /** Défis surprise proposés aujourd'hui (une fois proposés seulement). */
    @SerialName("td") val todayDefis: List<SharedDefi>? = null,
) {
    /** Points du concours : un Boss vaut plus qu'un exploit, qui vaut plus qu'un Burst, qui vaut plus qu'une séance. */
    val score: Int get() = bossDefeated * BOSS_POINTS + exploits * EXPLOIT_POINTS + bursts * BURST_POINTS + defis * Challenges.POINTS + sessionsDone

    fun isToday(today: LocalDate) = date == today.toString()

    /** Même contenu, horodatage mis à part : sert à ne pas republier pour rien. */
    fun sameAs(other: FriendCard?) = other != null && copy(updatedAt = 0) == other.copy(updatedAt = 0)

    companion object {
        const val BOSS_POINTS = 5
        const val BOSS_WON = "w"
        const val BOSS_LOST = "l"
        const val BOSS_ONGOING = "o"
        const val EXPLOIT_POINTS = 3
        /** En plus du point de la séance. */
        const val BURST_POINTS = 2
        const val PSEUDO_MAX = 20
    }
}

/** Un exercice de la séance du jour, tel que les potes le voient. */
@Serializable
data class SharedExercise(
    val name: String,
    /** « 3 × 15s ». */
    val planned: String? = null,
    /** « 15s · 15s · 43s », « fait », null si rien. */
    val done: String? = null,
    val finished: Boolean = false,
    val exploit: Boolean = false,
    /** Fait à la place d'un exercice de la routine. */
    val replaced: Boolean = false,
    /** Version faite : [VERSION_EASY] ou [VERSION_ECCENTRIC] ; null : normale. Noms courts, la carte doit rester petite. */
    @SerialName("v") val version: String? = null,
    /** Palier (à partir de 1) et nombre de paliers. */
    @SerialName("lv") val level: Int? = null,
    @SerialName("lc") val levelCount: Int? = null,
    /** Ressenti de l'exo (index de [StoredProgram.FEELINGS]). */
    @SerialName("fe") val feeling: Int? = null,
    /** Dose du jour réduite (« Crevé »), en % de la dose pleine. Anciennes versions : mise en route et fatigue. */
    @SerialName("pc") val dosePercent: Int? = null,
    /** Fait avec des poids. */
    @SerialName("w") val weighted: Boolean? = null,
    /** Jour Boss : l'objectif de la série à fond. */
    @SerialName("tg") val target: Int? = null,
) {
    /** Sans les détails en plus : ce qui saute d'abord quand la carte est trop grosse. */
    fun bare() = copy(version = null, level = null, levelCount = null, feeling = null, dosePercent = null, weighted = null, target = null)

    companion object {
        const val VERSION_EASY = "f"
        const val VERSION_ECCENTRIC = "x"
        const val VERSION_TIRED = "c"
    }
}

/** Un défi surprise du jour, tel que les potes le voient : « 30 squats classiques » et son issue. */
@Serializable
data class SharedDefi(
    @SerialName("l") val label: String,
    /** [WON], [LOST], [REFUSED], ou null : encore en jeu. */
    @SerialName("r") val result: String? = null,
) {
    companion object {
        const val WON = "w"
        const val LOST = "l"
        const val REFUSED = "r"
        /** Pas plus de défis par jour sur une carte. */
        const val MAX = 2

        fun of(c: Challenge, now: Long) = SharedDefi(
            label = c.label.take(60),
            result = when (c.status(now)) {
                Challenge.Status.WON -> WON
                Challenge.Status.LOST, Challenge.Status.EXPIRED -> LOST
                Challenge.Status.REFUSED -> REFUSED
                else -> null
            },
        )
    }
}

/** Un nom tel qu'envoyé aux potes : entier jusqu'à [max] caractères, sinon coupé au dernier mot avec « … ». */
fun shareName(name: String, max: Int = 60): String {
    if (name.length <= max) return name
    val cut = name.take(max - 1)
    val space = cut.lastIndexOf(' ')
    return (if (space > max / 2) cut.take(space) else cut).trimEnd(' ', '(', ',', '·') + "…"
}

object Social {
    /** De quoi rester bien sous les 4 Ko d'un message ntfy. */
    const val MAX_SHARED = 15
    private const val MAX_CARD_BYTES = 3500

    /** Compact : une carte publiée doit rester petite (ntfy limite un message à 4 Ko). */
    private val compact = Json {
        ignoreUnknownKeys = true
        @OptIn(ExperimentalSerializationApi::class)
        explicitNulls = false
    }

    fun encode(card: FriendCard): String = compact.encodeToString(FriendCard.serializer(), card)

    fun encodeAll(cards: List<FriendCard>): String = compact.encodeToString(ListSerializer(FriendCard.serializer()), cards)

    fun decodeAll(text: String): List<FriendCard> = try {
        compact.decodeFromString(ListSerializer(FriendCard.serializer()), text)
    } catch (e: SerializationException) {
        emptyList()
    } catch (e: IllegalArgumentException) {
        emptyList()
    }

    fun card(
        id: String,
        pseudo: String,
        programs: List<StoredProgram>,
        today: LocalDate,
        now: Long,
        build: Int,
        challengesWon: Int = 0,
        /** Partager le détail de la séance du jour (exos, prévu, fait, ressenti). */
        details: Boolean = true,
        /** Défis surprise connus (ceux d'aujourd'hui partent sur la carte). */
        challenges: List<Challenge> = emptyList(),
    ): FriendCard {
        var done = 0
        var total = 0
        var title: String? = null
        var feeling: Int? = null
        var boss: String? = null
        val shared = ArrayList<SharedExercise>()
        val stats = programs.map { p ->
            val schedule = ProgramSchedule(p, today)
            schedule.sessionOn(today)?.let { s ->
                done += schedule.requiredDone(s)
                total += s.requiredCount
                if (title == null) title = s.day.title ?: p.plan.name
                if (feeling == null) feeling = p.feelingOf(s.index)
                if (s.isBoss && boss == null) boss = when {
                    schedule.isComplete(s) -> FriendCard.BOSS_WON
                    s.required.all { schedule.exerciseState(s, it) != ExerciseState.TODO } -> FriendCard.BOSS_LOST
                    else -> FriendCard.BOSS_ONGOING
                }
                if (details) runCatching { shared += sharedExercises(schedule, s) }.onFailure(Safety::report)
            }
            Stats.of(schedule)
        }
        val all = Stats.merge(stats, today)
        val last = all.exploits.maxByOrNull { it.date }
        return FriendCard(
            id = id,
            pseudo = pseudo.trim().take(FriendCard.PSEUDO_MAX),
            date = today.toString(),
            updatedAt = now,
            todayDone = done,
            todayTotal = total,
            todayTitle = title,
            streak = all.currentStreak,
            sessionsDone = all.sessionsDone,
            bossDefeated = all.bossDefeated,
            bursts = all.burstsDone,
            defis = challengesWon,
            exploits = all.exploits.size,
            lastExploit = last?.label(),
            build = build,
            todayExercises = shared.take(MAX_SHARED).takeIf { details },
            todayFeeling = feeling.takeIf { details },
            todayBoss = boss,
            todayDefis = challenges.filter { it.date == today.toString() && !(it.acceptedAt == null && it.open(now)) }
                .takeLast(SharedDefi.MAX).map { SharedDefi.of(it, now) }.ifEmpty { null },
        ).fitted()
    }

    /** Les exercices de la séance (sans échauffement ni étirements), dans l'ordre affiché. */
    private fun sharedExercises(schedule: ProgramSchedule, s: Session): List<SharedExercise> = s.order.mapNotNull { e ->
        val pr = s.prescriptions[e]
        if (pr.exercise.optional) return@mapNotNull null
        val log = schedule.program.logOf(s.index, e)
        val finished = schedule.exerciseState(s, e) == ExerciseState.DONE
        fun fmt(v: Int) = if (pr.maxIsSeconds) Measures.formatSeconds(v) else "$v"
        val values = log?.perSet?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { if (it == 0) "–" else fmt(it) }
        val doneLabel = values ?: log?.max?.let { "max ${fmt(it)}" } ?: if (finished) "fait" else null
        val planned = pr.exploitPlanned
        val max = log?.max
        SharedExercise(
            name = shareName(pr.exercise.name),
            planned = pr.summary().ifEmpty { null }?.take(40),
            done = doneLabel?.take(60),
            finished = finished,
            exploit = max != null && planned != null && Exploit.isExploit(max, planned, pr.maxIsSeconds),
            replaced = pr.replaces != null,
            version = when {
                pr.eccentric -> SharedExercise.VERSION_ECCENTRIC
                pr.tired -> SharedExercise.VERSION_TIRED
                pr.isEasy -> SharedExercise.VERSION_EASY
                else -> null
            },
            level = pr.palier?.plus(1)?.takeIf { pr.levelCount > 1 },
            levelCount = pr.levelCount.takeIf { pr.palier != null && it > 1 },
            feeling = schedule.program.exerciseFeelingOf(s.index, e),
            dosePercent = pr.exercise.tired?.takeIf { pr.tired }?.let { 100 - it },
            weighted = true.takeIf { pr.weighted == true },
            target = pr.target.takeIf { pr.isBoss && pr.allOut },
        )
    }

    /** Une carte trop grosse pour ntfy perd son détail, exercice par exercice en partant de la fin. */
    private fun FriendCard.fitted(): FriendCard {
        var card = this
        // Trop gros : les détails en plus sautent d'abord, puis les exos un par un en partant de la fin.
        if (encode(card).toByteArray().size > MAX_CARD_BYTES) card = card.copy(todayExercises = card.todayExercises?.map { it.bare() }, todayDefis = null)
        while (encode(card).toByteArray().size > MAX_CARD_BYTES && !card.todayExercises.isNullOrEmpty()) {
            card = card.copy(todayExercises = card.todayExercises!!.dropLast(1))
        }
        return card
    }

    /**
     * Lit la réponse de ntfy (`GET /{canal}/json?poll=1`) : un objet JSON par ligne, dont `message`
     * contient une carte. Les lignes illisibles ou étrangères sont ignorées.
     */
    fun parsePoll(body: String): List<FriendCard> = body.lineSequence().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        try {
            val event = compact.parseToJsonElement(line) as? JsonObject ?: return@mapNotNull null
            if (event["event"]?.jsonPrimitive?.contentOrNull != "message") return@mapNotNull null
            val message = event["message"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            compact.decodeFromString(FriendCard.serializer(), message)
                .takeIf { it.id.isNotBlank() && it.pseudo.isNotBlank() }
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }.toList()

    /** Garde la carte la plus récente de chacun (anciennes cartes en cache comprises). */
    fun merge(cache: List<FriendCard>, fresh: List<FriendCard>): List<FriendCard> =
        (cache + fresh).groupBy { it.id }.map { (_, cards) -> cards.maxBy { it.updatedAt } }
            .sortedByDescending { it.updatedAt }
            .take(MAX_FRIENDS)

    /**
     * Les cartes à montrer. Chaque réinstallation crée un nouvel identifiant : un même pseudo ne garde que sa carte
     * la plus récente, et le mien n'apparaît pas en double de moi-même. Une carte qui n'a pas bougé depuis
     * [FORGET_AFTER_MS] est oubliée.
     */
    fun visible(cards: List<FriendCard>, myPseudo: String?, now: Long): List<FriendCard> {
        fun key(pseudo: String) = pseudo.trim().lowercase()
        val mine = myPseudo?.let(::key)
        return cards.filter { now - it.updatedAt <= FORGET_AFTER_MS && key(it.pseudo) != mine }
            .groupBy { key(it.pseudo) }.map { (_, same) -> same.maxBy { it.updatedAt } }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * Qui porte déjà ce pseudo, en dehors de moi ([myId]) : la carte la plus récente, null si personne.
     * Seulement les cartes connues (12 h du canal, 14 jours de cache) : c'est une vérification approchée.
     */
    fun pseudoOwner(cards: List<FriendCard>, pseudo: String, myId: String, now: Long): FriendCard? {
        val wanted = pseudo.trim().lowercase()
        if (wanted.isEmpty()) return null
        return cards.filter { it.id != myId && it.pseudo.trim().lowercase() == wanted && now - it.updatedAt <= FORGET_AFTER_MS }
            .maxByOrNull { it.updatedAt }
    }

    /** Classement : score décroissant, puis avancée du jour. */
    fun ranking(cards: List<FriendCard>, today: LocalDate): List<FriendCard> = cards.sortedWith(
        compareByDescending<FriendCard> { it.score }
            .thenByDescending { if (it.isToday(today) && it.todayTotal > 0) it.todayDone.toFloat() / it.todayTotal else -1f },
    )

    const val MAX_FRIENDS = 50
    const val FORGET_AFTER_MS = 14L * 24 * 3600 * 1000
}
