package com.spacelenin.flemmefit.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/** Une séance placée dans le calendrier, avec ce qui y est demandé. */
data class Session(
    /** Index de jour dans le programme (répétitions comprises). */
    val index: Int,
    /** 1-based. */
    val cycle: Int,
    /** 1-based, dans le cycle. */
    val week: Int,
    val day: PlanDay,
    val weekLabel: String?,
    val date: LocalDate,
    val isBoss: Boolean,
    val isDeload: Boolean,
    val prescriptions: List<Prescription>,
    /** Séance renforcée à la demande (Burst). */
    val isBurst: Boolean = false,
    /** Ordre d'affichage (index dans [prescriptions]) : la variante suit son exercice de base. */
    val order: List<Int> = prescriptions.indices.toList(),
    /** Jour variante : le bloc variante, et l'exercice de base dont il remplace la série max. */
    val variantIndex: Int? = null,
    val variantBaseIndex: Int? = null,
    /** Exercice perso « Autre » du jour. */
    val otherIndex: Int? = null,
    /** Séance allégée à la demande (Chill). */
    val isChill: Boolean = false,
    /** Les mêmes exercices à dose pleine (avant « Crevé » et la forme du jour). */
    val full: List<Prescription> = prescriptions,
    /** Forme du jour : 70 (fatigué) ou 55 (malade), en % de la dose ; null : en forme. */
    val form: Int? = null,
    /** Le Boss de ce jour a été reporté (forme du jour choisie). */
    val bossMoved: Boolean = false,
) {
    /** L'ordre que donneraient ces clés d'exercices de la routine (voir [SessionOrder.arrange]). */
    fun orderFor(keys: List<String>?): List<Int> = SessionOrder.arrange(day.exercises, keys, variantIndex, variantBaseIndex, otherIndex)

    /** Exercices de la routine, puis variante et exercice perso du jour. */
    val exerciseCount get() = prescriptions.size
    /** Exercices qui comptent pour finir la séance : tout sauf les facultatifs (échauffement, étirements). */
    val required: List<Int> get() = prescriptions.indices.filterNot { prescriptions[it].exercise.optional }.ifEmpty { prescriptions.indices.toList() }
    val requiredCount get() = required.size
    /** Semaine du programme depuis le début (0-based), répétitions comprises. */
    val programWeek get() = index / DAYS_PER_WEEK
}

private const val VARIANT_SALT = 0x5EED1L
/** Suggestion de rythme : sur cette période, avec au moins tant de séances et de retours d'exercices. */
private const val SUGGESTION_DAYS = 21L
private const val SUGGESTION_MIN_SESSIONS = 5
private const val SUGGESTION_MIN_SIGNALS = 12
private const val OTHER_SALT = 0x5EED2L

/** Crans de progression ajoutés par un Burst. */
const val BURST_STEPS = 2
/**
 * Passage à un Boss par bloc de semaines de travail : les Boss tombés avant cette date (faits, ou annoncés la veille)
 * restent ceux de l'ancienne règle.
 */
val BOSS_BLOCKS_FROM: java.time.LocalDate = java.time.LocalDate.of(2026, 11, 16)

/** Crans de progression d'un Boss : plus dur qu'un Burst. */
const val BOSS_STEPS = 3
/** Part d'un Boss à faire pour sauver la journée (sans points ni 🔥 en plus). */
const val BOSS_SAVE_SHARE = 0.75

/** [SAVED] : Boss fait aux trois quarts, la série 🔥 tient mais n'avance pas. */
enum class SessionStatus { DONE, SAVED, PARTIAL, MISSED, PENDING }

enum class ExerciseState { DONE, PARTIAL, TODO }

data class Progress(
    val doneExercises: Int,
    val totalExercises: Int,
    val doneSessions: Int,
    /** Programme illimité : séances prévues jusqu'à aujourd'hui. */
    val totalSessions: Int,
    /** Séances terminées d'affilée, en remontant depuis aujourd'hui. */
    val streak: Int,
    val missedSessions: Int,
    val nextSession: Session?,
) {
    val ratio: Float get() = if (totalExercises == 0) 0f else doneExercises.toFloat() / totalExercises
}

/**
 * Projection d'un [StoredProgram] sur le calendrier.
 *
 * Les séances sont résolues à la demande et dans l'ordre (la progression d'une séance dépend des
 * précédentes), ce qui permet les programmes illimités. Un objet par état du programme et par jour :
 * il n'est pas partagé entre threads.
 */
class ProgramSchedule(val program: StoredProgram, val today: LocalDate = LocalDate.now()) {
    private val plan = program.plan
    private val postpones = program.postpones.sorted()
    private val deloadEvery = plan.effectiveDeloadEvery
    private val seed = program.id.hashCode().toLong()

    val cycleLength = plan.weeks.size * DAYS_PER_WEEK
    val isForever = plan.isForever
    /** null si illimité. */
    val totalDays: Int? = if (isForever) null else cycleLength * plan.repeat
    val startDate: LocalDate = program.start
    val endDate: LocalDate? = totalDays?.let { dateOf(it - 1) }

    private fun shift(dayIndex: Int): Int {
        var n = 0
        for (p in postpones) if (p <= dayIndex) n++ else break
        return n
    }

    fun dateOf(dayIndex: Int): LocalDate = startDate.plusDays((dayIndex + shift(dayIndex)).toLong())

    private fun inRange(dayIndex: Int) = dayIndex >= 0 && (totalDays == null || dayIndex < totalDays)

    /** Index du jour de programme tombant à [date], ou null (avant le début, après la fin, ou jour de décalage). */
    fun indexOn(date: LocalDate): Int? {
        val offset = ChronoUnit.DAYS.between(startDate, date).toInt()
        if (offset < 0) return null
        for (i in maxOf(0, offset - postpones.size)..offset) {
            if (i + shift(i) == offset) return i.takeIf(::inRange)
        }
        return null
    }

    /** Plus grand index de jour dont la date est ≤ [date] (-1 si avant le début). */
    fun lastIndexOnOrBefore(date: LocalDate): Int {
        var i = ChronoUnit.DAYS.between(startDate, date).toInt()
        if (i < 0) return -1
        while (i >= 0 && dateOf(i).isAfter(date)) i--
        return totalDays?.let { minOf(i, it - 1) } ?: i
    }

    fun isInProgram(date: LocalDate) = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))

    fun isDeloadWeek(programWeek: Int) = deloadEvery > 0 && (programWeek + 1) % deloadEvery == 0

    // --- Jours Boss : un par bloc de semaines de travail (entre deux semaines allégées), semaine et jour au hasard. ---

    private val bossRandom = Random(seed)
    private val bossWeeks = ArrayList<Int>()
    private val bossWeekSet = HashSet<Int>()

    private fun planDaysOfWeek(programWeek: Int): List<PlanDay> = plan.weeks[programWeek % plan.weeks.size].days

    /** Semaines d'un bloc de travail : entre deux semaines allégées (sans allégement : par 3). */
    private val bossBlockSize get() = if (deloadEvery > 0) deloadEvery else 3
    private fun bossBlockOf(programWeek: Int) = programWeek / bossBlockSize

    /** Ancienne règle (un Boss toutes les 3 à 4 semaines) : gardée pour les Boss d'avant [BOSS_BLOCKS_FROM]. */
    private fun oldBossWeekUpTo(programWeek: Int) {
        while (bossWeeks.lastOrNull().let { it == null || it < programWeek }) {
            val prev = bossWeeks.lastOrNull()
            var next = if (prev == null) 2 + bossRandom.nextInt(2) else prev + 3 + bossRandom.nextInt(2)
            while (isDeloadWeek(next) || planDaysOfWeek(next).isEmpty()) next++
            if (totalDays != null && next * DAYS_PER_WEEK >= totalDays) next = Int.MAX_VALUE
            bossWeeks += next
            bossWeekSet += next
        }
    }

    /** Le jour Boss tombe avant le passage à la nouvelle règle : il reste tel quel (déjà fait, ou déjà annoncé). */
    private fun bossBeforeNewRule(programWeek: Int) =
        programWeek != Int.MAX_VALUE && dateOf(bossIndexOfWeek(programWeek)).isBefore(BOSS_BLOCKS_FROM)

    /** La semaine Boss du bloc [block] (nouvelle règle), ou null s'il n'y en a pas. */
    private fun newBossWeekOf(block: Int): Int? {
        val first = block * bossBlockSize
        // Un Boss de l'ancienne règle déjà tombé dans ce bloc (avant le changement) compte pour le bloc.
        oldBossWeekUpTo(first + bossBlockSize - 1)
        if ((first until first + bossBlockSize).any { it in bossWeekSet && bossBeforeNewRule(it) }) return null
        val candidates = (first until first + bossBlockSize).filter { w ->
            !isDeloadWeek(w) && planDaysOfWeek(w).isNotEmpty() && w > 0 &&
                (totalDays == null || w * DAYS_PER_WEEK < totalDays) &&
                !dateOf(bossIndexOfWeek(w)).isBefore(BOSS_BLOCKS_FROM)
        }
        if (candidates.isEmpty()) return null
        return candidates[Random(seed xor (block * 104729L)).nextInt(candidates.size)]
    }

    private fun isBossWeek(programWeek: Int): Boolean {
        if (!plan.boss) return false
        oldBossWeekUpTo(programWeek)
        if (programWeek in bossWeekSet && bossBeforeNewRule(programWeek)) return true
        val block = bossBlockOf(programWeek)
        return newBossWeekOf(block) == programWeek
    }

    /** Le jour [i] est le Boss tiré au sort (avant tout report), qu'il soit dévoilé ou non. */
    fun isNaturalBoss(i: Int): Boolean {
        if (i < 0) return false
        val week = i / DAYS_PER_WEEK
        return !isDeloadWeek(week) && isBossWeek(week) && bossIndexOfWeek(week) == i
    }

    /** Où reporter le Boss du jour [i] : un jour de séance tiré au sort dans les 7 jours qui suivent (-1 : aucun). */
    fun bossMoveTarget(i: Int): Int {
        val candidates = (i + 1..i + DAYS_PER_WEEK).filter { d ->
            inRange(d) && !isDeloadWeek(d / DAYS_PER_WEEK) && !isNaturalBoss(d) &&
                planDaysOfWeek(d / DAYS_PER_WEEK).any { it.day == d % DAYS_PER_WEEK + 1 }
        }
        return if (candidates.isEmpty()) -1 else candidates[Random(seed xor (i * 31337L)).nextInt(candidates.size)]
    }

    private fun bossIndexOfWeek(programWeek: Int): Int {
        val days = planDaysOfWeek(programWeek)
        val pick = Random(seed xor (programWeek * 7919L)).nextInt(days.size)
        return programWeek * DAYS_PER_WEEK + days[pick].day - 1
    }

    // --- Résolution des séances ---

    private val resolved = HashMap<Int, Session>()
    private var builtUpTo = -1
    private val validated = HashMap<String, Int>()
    // Réglage sur mesure de chaque exercice, et le dernier résultat de test qui l'a encadré.
    private val adapt = HashMap<String, AdaptState>()
    private val testSeen = HashMap<String, Int>()
    // Tests refaits, par test : même au même chiffre, un test refait recadre le réglage.
    private val testCount = HashMap<String, Int>()
    // Dernière prescription de chaque exercice hors semaine allégée : de quoi montrer l'écart avec la courbe de base.
    private val adaptViews = LinkedHashMap<String, AdaptBase>()
    private val ceilings = HashMap<Pair<Exercise, Boolean>, Int?>()
    // Exercices faits une série par côté : le réglage de chaque côté, ce que chaque côté fait vraiment (lissé),
    // le suivi de l'équilibre, et son historique.
    private val sideAdapt = HashMap<Pair<String, Side>, AdaptState>()
    private val sideCapacity = HashMap<Pair<String, Side>, Double>()
    private val balance = HashMap<String, BalanceState>()
    private val balanceViews = LinkedHashMap<String, SideBalance>()
    private val balancePoints = HashMap<String, MutableList<TestPoint>>()
    private val lastMax = HashMap<String, Int>()
    // Meilleure série notée de chaque exercice, et meilleur max de chaque variante en vraie version : les prérequis.
    private val bestMax = HashMap<String, Int>()
    // Meilleure série de l'exo avant chaque séance (mêmes clés que [StoredProgram.done]) : de quoi fêter un record.
    private val priorBest = HashMap<String, Int>()
    private val variantBest = HashMap<String, Int>()
    // Variantes à progression fixe : blocs validés depuis le début de l'étape, et vraie version atteinte.
    private val fixedSteps = HashMap<String, Int>()
    private val fixedFull = HashMap<String, Boolean>()
    private val testResults = HashMap(program.tests)
    // Max refaits en changeant d'exercice de test : appliqués à la première séance qui suit.
    private val appliedChanges = HashSet<Int>()
    private val optionTests = plan.tests.filter { it.options.isNotEmpty() }
    private val levelOf = HashMap<String, Int>()
    private val levelSince = HashMap<String, Int>()
    // Dernier choix « avec / sans poids » dit pour chaque exercice : proposé par défaut ensuite.
    private val lastWeights = HashMap<String, Boolean>()
    // Ce que chaque séance finie a dit de chaque exercice (en crans) : de quoi suggérer un autre rythme.
    private val recentSignals = ArrayList<Pair<LocalDate, Double>>()

    // --- Variantes et exercices perso ---

    // --- Réglages du sur-mesure : routine, puis exercice, puis rythme choisi (profil) à la date de la séance ---

    private val variantParams = VariantParams().with(plan.tuning?.variants)
    private val tuningByKey: Map<String, Tuning> = buildMap {
        plan.weeks.forEach { w -> w.days.forEach { d -> d.exercises.forEach { e -> e.tuning?.let { putIfAbsent(e.key, it) } } } }
        plan.variants.forEach { v -> v.tuning?.let { putIfAbsent(v.id.trim().lowercase(), it) } }
    }
    private val baseAdapt = AdaptParams().with(plan.tuning?.adapt)
    private val baseSides = SidesParams().with(plan.tuning?.sides)
    private val adaptCache = HashMap<Pair<String, AdaptProfile>, AdaptParams>()
    private val sidesCache = HashMap<Pair<String, AdaptProfile>, SidesParams>()

    /** Réglage de l'exercice [key] tel que le prévoit la routine (sans le rythme choisi). */
    fun routineParams(key: String): AdaptParams = baseAdapt.with(tuningByKey[key]?.adapt)

    fun adaptParams(key: String, date: LocalDate): AdaptParams {
        val profile = program.profileOn(date)
        return adaptCache.getOrPut(key to profile) { profile.apply(routineParams(key)) }
    }

    fun sidesParams(key: String, date: LocalDate): SidesParams {
        val profile = program.profileOn(date)
        return sidesCache.getOrPut(key to profile) { profile.apply(baseSides.with(tuningByKey[key]?.sides)) }
    }

    private val variantCycleWeeks = if (deloadEvery > 0) deloadEvery else variantParams.cycleWeeks
    private val variants: List<Pair<Variant, CustomExercise?>> = plan.variants.map { it to null } +
        program.custom.filter { !it.isOther && plan.family(it.family) != null }.map { it.asVariant() to it }
    private val others = program.custom.filter { it.isOther }
    private val variantLeft = HashMap<String, Int>()
    private val variantPicked = HashMap<String, Int>()
    // Part des passages voulus qui tient dans les séances de la famille sur le cycle.
    private val familyScale = HashMap<String, Double>()
    // Familles où la variante prend toute la place : passages restants sur le cycle, toutes variantes confondues.
    private val familyLeft = HashMap<String, Int>()
    private var variantCycle = -1
    private val otherLeft = HashMap<String, Int>()
    private var otherWeek = -1
    // Taux perso de chaque variante, recalé à chaque max noté, et ces max.
    private val ratios = HashMap<String, Double>()
    private val variantPoints = HashMap<String, MutableList<TestPoint>>()

    private fun baseIndex(day: PlanDay, v: Variant): Int? {
        val family = plan.family(v.family) ?: return null
        return day.exercises.indexOfFirst { !it.retest && it.key in family.exercises }.takeIf { it >= 0 }
    }

    /** Séances de [from] (inclus) à [end] (exclu), hors semaines allégées, qui remplissent [has]. */
    private fun slotsLeft(from: Int, end: Int, has: (PlanDay) -> Boolean): Int {
        val stop = totalDays?.let { minOf(end, it) } ?: end
        return (from until stop).count { j -> !isDeloadWeek(j / DAYS_PER_WEEK) && plan.dayAt(j)?.let(has) == true }
    }

    /**
     * Tirage d'une séance : chaque candidat a une urgence (passages restants / séances restantes où il peut tomber).
     * Urgence pleine : il tombe forcément ; sinon au hasard, au prorata. Ne dépend que de cette séance et des
     * précédentes : un changement (exo ajouté, mis en pause, séance refusée) ne touche jamais le passé.
     */
    private fun <T> draw(dayIndex: Int, salt: Long, candidates: List<Pair<T, Double>>, picked: (T) -> Int = { 0 }): T? {
        // Plusieurs urgents : celui qui est le moins passé ce cycle d'abord.
        val urgent = candidates.filter { it.second >= 1.0 }
            .minWithOrNull(compareBy<Pair<T, Double>> { picked(it.first) }.thenByDescending { it.second })
        if (urgent != null) return urgent.first
        val r = Random(seed xor (dayIndex * 104729L) xor salt).nextDouble()
        var acc = 0.0
        for ((c, u) in candidates) {
            acc += u
            if (r < acc) return c
        }
        return null
    }

    /** Valeur atteinte pour un prérequis : test, sinon variante, sinon exercice (voir [Variant.requires]). */
    private fun reached(key: String): Int = when {
        plan.test(key) != null -> testResults[key] ?: 0
        plan.variants.any { it.id == key } -> variantBest[key] ?: 0
        else -> bestMax[key.trim().lowercase()] ?: 0
    }

    private fun meets(v: Variant) = v.requires.all { (key, min) -> reached(key) >= min }

    /** Dosage du prochain bloc d'une variante à progression fixe : valeur par série, et vraie version ou non. */
    private fun fixedLevel(v: Variant): Pair<Int, Boolean> {
        val full = fixedFull[v.id] ?: (v.easyUntil == null)
        val per = (v.start ?: 1) + v.step * (fixedSteps[v.id] ?: 0)
        return (v.cap?.let { minOf(per, it) } ?: per) to full
    }

    /** Un bloc validé : un pas de plus, ou passage à la vraie version (en repartant du départ). */
    private fun advanceFixed(v: Variant) {
        val (per, full) = fixedLevel(v)
        if (!full && v.easyUntil != null && per >= v.easyUntil) {
            fixedFull[v.id] = true
            fixedSteps[v.id] = 0
        } else {
            fixedFull[v.id] = full
            fixedSteps[v.id] = (fixedSteps[v.id] ?: 0) + 1
        }
    }

    /** Prérequis de [v] à aujourd'hui. */
    fun requirements(v: Variant): List<VariantRequirement> {
        ensure(lastIndexOnOrBefore(today))
        return requirementsNow(v)
    }

    private fun requirementsNow(v: Variant): List<VariantRequirement> =
        v.requires.map { (key, min) ->
            val test = plan.test(key)
            val variant = plan.variants.firstOrNull { it.id == key }
            val name = test?.name ?: variant?.name ?: exerciseName(key.trim().lowercase()) ?: key
            val seconds = test?.isSeconds ?: variant?.isSeconds ?: (exerciseByKey[key.trim().lowercase()]?.duration != null)
            VariantRequirement(key, name, min, reached(key), seconds)
        }

    /** Meilleure série notée de l'exo [e] avant cette séance, null s'il n'a jamais été noté. */
    fun previousBest(session: Session, e: Int): Int? {
        ensure(session.index)
        return priorBest[StoredProgram.doneKey(session.index, e)]
    }

    /** Variantes débloquées qui attendent une confirmation ([Variant.check]). */
    fun pendingChecks(): List<Variant> {
        ensure(lastIndexOnOrBefore(today))
        return plan.variants.filter { it.check != null && it.id !in program.variantChecks && meets(it) }
    }

    private fun drawVariant(i: Int, day: PlanDay, date: LocalDate): Pair<Variant, CustomExercise?>? {
        val cycle = i / DAYS_PER_WEEK / variantCycleWeeks
        if (cycle != variantCycle) {
            variantCycle = cycle
            variantLeft.clear()
            variantPicked.clear()
            familyScale.clear()
            familyLeft.clear()
        }
        val end = (cycle + 1) * variantCycleWeeks * DAYS_PER_WEEK
        fun wanted(v: Variant) = v.perCycle ?: Variants.quota(v, plan.family(v.family)?.test?.let(testResults::get))
        val candidates = variants.mapNotNull { pick ->
            val (v, custom) = pick
            if (custom != null && !custom.activeOn(date)) return@mapNotNull null
            if (baseIndex(day, v) == null) return@mapNotNull null
            if (!meets(v) || !program.variantConfirmed(v.id, v.check, date)) return@mapNotNull null
            val family = plan.family(v.family)!!
            if (family.replace) {
                val slots = slotsLeft(i, end) { baseIndex(it, v) != null }
                val left = familyLeft.getOrPut(v.family) {
                    val share = Variants.replaceShare(family, variants.map { it.first }.filter { it.family == v.family }, family.test?.let(testResults::get))
                    Variants.replaceCount(share, slots, cycle)
                }
                return@mapNotNull if (left <= 0) null else pick to left.toDouble() / slots.coerceAtLeast(1)
            }
            val left = variantLeft.getOrPut(v.id) {
                // Plus de passages voulus que de séances : chacun réduit d'autant, au moins un.
                val scale = familyScale.getOrPut(v.family) {
                    val demand = variants.filter { (o, c) -> o.family == v.family && (c == null || c.activeOn(date)) }.sumOf { wanted(it.first) }
                    val slots = slotsLeft(i, end) { baseIndex(it, v) != null }
                    if (demand == 0) 1.0 else minOf(1.0, slots.toDouble() / demand)
                }
                (wanted(v) * scale).roundToInt().coerceAtLeast(1)
            }
            if (left <= 0) return@mapNotNull null
            pick to left.toDouble() / slotsLeft(i, end) { baseIndex(it, v) != null }.coerceAtLeast(1)
        }.let { list ->
            // Toute la place : l'urgence est celle de la famille, partagée entre ses variantes (sauf urgence pleine).
            list.map { (pick, u) ->
                val family = plan.family(pick.first.family)!!
                val share = if (family.replace && u < 1.0) list.count { it.first.first.family == pick.first.family } else 1
                pick to u / share
            }
        }
        if (i in program.variantSkips) return null
        return draw(i, VARIANT_SALT, candidates) { variantPicked[it.first.id] ?: 0 }?.also {
            val family = it.first.family
            if (plan.family(family)?.replace == true) familyLeft[family] = familyLeft.getValue(family) - 1
            else variantLeft[it.first.id] = variantLeft.getValue(it.first.id) - 1
            variantPicked[it.first.id] = (variantPicked[it.first.id] ?: 0) + 1
        }
    }

    private fun drawOther(i: Int, date: LocalDate): CustomExercise? {
        val week = i / DAYS_PER_WEEK
        if (week != otherWeek) {
            otherWeek = week
            otherLeft.clear()
        }
        val slots = slotsLeft(i, (week + 1) * DAYS_PER_WEEK) { true }.coerceAtLeast(1)
        val candidates = others.filter { it.activeOn(date) }.mapNotNull { o ->
            val left = otherLeft.getOrPut(o.id) { variantParams.otherPerWeek }
            if (left <= 0) null else o to left.toDouble() / slots
        }
        return draw(i, OTHER_SALT, candidates)?.also { otherLeft[it.id] = otherLeft.getValue(it.id) - 1 }
    }

    private fun ensure(dayIndex: Int) {
        val limit = totalDays?.let { minOf(dayIndex, it - 1) } ?: dayIndex
        while (builtUpTo < limit) build(++builtUpTo)
    }

    private fun build(i: Int) {
        val week = i / DAYS_PER_WEEK
        val cycleWeek = week % plan.weeks.size
        val planWeek = plan.weeks[cycleWeek]
        val day = planWeek.days.firstOrNull { it.day == i % DAYS_PER_WEEK + 1 } ?: return
        val date = dateOf(i)
        program.testChanges.forEachIndexed { k, c ->
            if (c.result != null && k !in appliedChanges && !date.isBefore(LocalDate.parse(c.date))) {
                testResults[c.test] = c.result
                testCount[c.test] = (testCount[c.test] ?: 0) + 1
                appliedChanges += k
            }
        }
        val deload = isDeloadWeek(week)
        // Forme du jour (fatigué, malade) : dose réduite, la séance compte, le réglage ne bouge pas. Un Boss tombé ce
        // jour-là est reporté, une seule fois, sur un jour tiré au sort dans la semaine qui suit.
        val form = program.formOf(i)?.takeIf { !deload }
        val naturalBoss = isNaturalBoss(i)
        val movedHere = program.bossMoves.any { (from, to) -> to == i && from.toIntOrNull()?.let(program::formOf) != null }
        // Le Boss ne se dévoile que la veille : avant, la séance est affichée normalement.
        val boss = !deload && form == null && (naturalBoss || movedHere) && !date.isAfter(today.plusDays(1))
        val weeksBefore = if (deloadEvery > 0) week - week / deloadEvery else week
        val burst = !boss && !deload && form == null && i in program.bursts
        val chill = !boss && !deload && !burst && form == null && i in program.chills
        val pause = chill || form != null

        fun prescribe(e: Int, original: Exercise): Prescription {
            val ex = named(original, date).let { if (StoredProgram.doneKey(i, e) in program.excused) it.copy(optional = true) else it }
            val weighted = ex.weights?.let { w ->
                val said = program.weightsChoice(i, e)
                if (said != null) lastWeights[ex.key] = said
                said ?: lastWeights[ex.key] ?: w.default
            } ?: false
            if (ex.retest) return Prescription.test(ex, plan.test(ex.test), ex.test?.let(testResults::get))
            val test = ex.test?.let(testResults::get)
            val palier = if (ex.levels.isEmpty()) null else ex.levelIndexFor(test)
            val p = ex.progression
            fun steps() = when {
                p == null -> 0
                p.basis == Progression.BASIS_WEEK -> (weeksBefore - (levelSince[ex.key] ?: 0)) / p.every
                else -> (validated[ex.key] ?: 0) / p.every
            }
            if (palier != null && levelOf[ex.key] == null) {
                levelOf[ex.key] = palier
                levelSince[ex.key] = weeksBefore
            } else if (palier != null && test != null && testSeen[ex.key] != (testCount[ex.test] ?: 0)) {
                // Test refait : la courbe de base et le réglage gardent leur volume (recalés sur le nouveau palier),
                // sauf si le réglage va plus loin que ce que le test justifie. Jamais sous le départ du palier.
                val state = adapt[ex.key] ?: AdaptState()
                val before = ex.atLevel(levelOf.getValue(ex.key))
                val target = ex.atLevel(palier)
                fun perSet(level: Int) = Prescription.of(before, level.coerceAtLeast(0), false, false, null).perSetPlanned
                var baseLevel = steps()
                var total = baseLevel + state.applied
                if (palier != levelOf[ex.key]) {
                    baseLevel = Adaptation.match(target, perSet(baseLevel))
                    total = Adaptation.match(target, perSet(total))
                    levelOf[ex.key] = palier
                    levelSince[ex.key] = weeksBefore - baseLevel * (p?.every ?: 1)
                    validated[ex.key] = baseLevel * (p?.every ?: 1)
                }
                val applied = Adaptation.guard(target, total, test, params = adaptParams(ex.key, date)) - baseLevel
                if (applied != state.applied) adapt[ex.key] = state.copy(value = applied.toDouble(), applied = applied)
                // Une série par côté : chaque côté bouge d'autant.
                val delta = applied - state.applied
                Side.entries.forEach { side ->
                    sideAdapt[ex.key to side]?.let { sideAdapt[ex.key to side] = it.copy(value = it.value + delta, applied = it.applied + delta) }
                }
            }
            ex.test?.let { testSeen[ex.key] = testCount[it] ?: 0 }
            val steps = steps()
            // Le Boss ne touche ni l'échauffement ni les étirements (facultatifs), ni les exos à dose fixe (Beat Saber).
            val bossHere = boss && !ex.optional && !ex.steady
            val extra = when {
                p == null -> 0
                bossHere -> BOSS_STEPS
                burst -> BURST_STEPS
                else -> 0
            }
            val applied = adapt[ex.key]?.applied ?: 0
            val base = palier?.let { named(ex.atLevel(it), date) } ?: ex
            if (!deload && !chill && p != null) adaptViews[ex.key] = AdaptBase(base, steps, applied, weighted)
            val level = (steps + applied).coerceAtLeast(0) + extra
            fun at(l: Int) = Prescription.of(
                base, l, deload || chill, boss = bossHere, lastMax = lastMax[ex.key], palier = palier, burst = burst,
                weighted = weighted,
            )
            val limb = ex.perSide ?: return at(level)
            return runCatching { sidedPrescription(limb, ex, steps, applied, extra, deload || chill, ::at) }
                .onFailure(Safety::report).getOrElse { at(level) }
        }
        val prescriptions = day.exercises.mapIndexed(::prescribe).toMutableList()
        val n = prescriptions.size

        // Jour variante et exercice perso : tirés pour chaque séance, dévoilés le jour même.
        val variantPick = if (deload) null else drawVariant(i, day, date)
        val otherPick = if (deload) null else drawOther(i, date)
        val shown = !date.isAfter(today)
        var variantIndex: Int? = null
        var variantBase: Int? = null
        var otherIndex: Int? = null
        if (shown && variantPick != null) {
            val (v, custom) = variantPick
            val b = baseIndex(day, v)!!
            val family = plan.family(v.family)!!
            val base = prescriptions[b]
            val block = if (v.isFixed) {
                val (per, full) = fixedLevel(v)
                Variants.fixedBlock(v, family, base.exercise, per, full, light = chill, params = variantParams)
            } else adaptVariant(
                Variants.block(
                    v, family, base.exercise, family.test?.let(testResults::get), ratios[v.id] ?: v.ratio, v.ratio,
                    custom = custom != null, testSeconds = plan.test(family.test)?.isSeconds == true, params = variantParams,
                ),
                light = chill,
            )
            val worked = block.copy(
                exercise = block.exercise.copy(
                    muscles = v.muscles.ifEmpty { base.exercise.muscles },
                    assists = if (v.muscles.isEmpty()) base.exercise.assists else v.assists,
                ),
            )
            variantBase = b
            if (family.replace) {
                // La variante prend la place de l'exercice de base, au même rang.
                prescriptions[b] = worked
                variantIndex = b
            } else {
                prescriptions[b] = base.copy(
                    sets = base.sets?.let(Variants::baseSets),
                    allOut = base.isBoss,
                    target = base.target.takeIf { base.isBoss },
                    replacedBy = v.name,
                )
                variantIndex = prescriptions.size
                prescriptions += worked
            }
        }
        if (shown && otherPick != null) {
            otherIndex = prescriptions.size
            prescriptions += prescribe(otherIndex, otherPick.asExercise())
        }
        // Exercices remplacés (choix fait depuis la page du jour) : l'exercice choisi, dosé comme celui qu'il suit.
        for (e in 0 until n) {
            if (e == variantIndex || e == variantBase) continue
            val ex = day.exercises[e]
            if (ex.retest) continue
            val id = program.replacementOn(i, e, ex.key, date) ?: continue
            val option = replacementOptions(ex, day).firstOrNull { it.id == id } ?: continue
            runCatching { replacementPrescription(option, ex, date, deload || chill, boss, burst) }
                .onFailure(Safety::report).getOrNull()?.let { prescriptions[e] = it }
        }
        // Version excentrique choisie (jamais un jour Boss ni sur un test) : faite, mais neutre pour le réglage.
        if (!boss) for (e in 0 until n) {
            val pr = prescriptions[e]
            if (pr.exercise.eccentric != null && !pr.isTest && pr.variant == null && program.isEccentric(i, e)) prescriptions[e] = pr.copy(eccentric = true)
        }
        // Ordre choisi (ou celui de la routine) ; la variante suit son exercice de base, l'exo perso passe avant le dernier.
        val order = SessionOrder.arrange(day.exercises, program.orderOn(i, day.day, date), variantIndex, variantBase, otherIndex, n)
        // « Crevé » (Beat Saber) : la dose du jour réduite, jamais un jour Boss ni sur un test.
        val dosed = prescriptions.toMutableList()
        if (form != null) prescriptions.forEachIndexed { e, pr ->
            if (!pr.exercise.optional && !pr.isTest) dosed[e] = pr.cutBy(100 - form).copy(tired = false, formPercent = form)
        } else if (!boss) for (e in 0 until n) {
            val pr = prescriptions[e]
            val cut = pr.exercise.tired ?: continue
            if (!pr.isTest && pr.variant == null && pr.replaces == null && program.isTired(i, e)) dosed[e] = pr.cutBy(cut)
        }
        resolved[i] = Session(
            i, week / plan.weeks.size + 1, cycleWeek + 1, day, planWeek.label, date, boss, deload, dosed, burst,
            order = order, variantIndex = variantIndex, variantBaseIndex = variantBase, otherIndex = otherIndex, isChill = chill,
            full = prescriptions.toList(), form = form, bossMoved = naturalBoss && form != null,
        )

        prescriptions.forEachIndexed { e, pr ->
            val key = pr.key
            val max = program.logOf(i, e)?.max
            if (e == variantIndex) {
                val v = variantPick!!.first
                val test = plan.family(v.family)?.test?.let(testResults::get)
                if (form != null) return@forEachIndexed
                if (max != null && max > 0 && pr.variant?.full == true) variantBest[v.id] = maxOf(variantBest[v.id] ?: 0, max)
                if (v.isFixed && program.isDone(i, e)) advanceFixed(v)
                if (max != null && max > 0) {
                    variantPoints.getOrPut(v.id) { ArrayList() } += TestPoint(date, max)
                    // Le taux se recale sur le max noté.
                    if (pr.variant?.full == true && test != null && test > 0) ratios[v.id] = Variants.learn(ratios[v.id] ?: v.ratio, test, max)
                }
                // Le dosage suit le taux ; ce qui est noté et ressenti règle la variante par-dessus, comme les autres exos.
                val done = program.isDone(i, e)
                if (!pause) runCatching { learn(pr, i, e, done, final = done || date.isBefore(today), extra = 0) }.onFailure(Safety::report)
                return@forEachIndexed
            }
            if (pr.isTest) {
                val test = pr.exercise.test
                if (max != null && test != null) {
                    testResults[test] = max
                    testCount[test] = (testCount[test] ?: 0) + 1
                }
                return@forEachIndexed
            }
            bestMax[key]?.let { priorBest[StoredProgram.doneKey(i, e)] = it }
            // Excentrique fait : la séance compte (validation, série), le reste ne bouge pas.
            // Coché mais rien fait : un oubli comme un autre, le réglage le voit.
            if (pr.eccentric && (program.isDone(i, e) || program.logOf(i, e) != null)) return@forEachIndexed
            // Forme du jour : la séance compte, le réglage, la progression et les max ne bougent pas.
            if (form != null) return@forEachIndexed
            val done = program.isDone(i, e)
            if (done && !deload && !pause) validated[key] = (validated[key] ?: 0) + 1
            // Le réglage n'est qu'un plus : s'il échoue, la séance reste calculée sans lui (jamais de plantage).
            // Boss et Burst gonflent le prévu exprès : le réglage compare à ce qui aurait été prévu sans eux.
            val extra = if (pr.exercise.progression == null) 0 else if (pr.isBoss) BOSS_STEPS else if (burst) BURST_STEPS else 0
            if (!deload && !pause) runCatching { learn(pr, i, e, done, final = done || date.isBefore(today), extra) }.onFailure(Safety::report)
            if (max == null) return@forEachIndexed
            // Objectif du prochain Boss (sans réduction) : le max ramené à la dose pleine.
            lastMax[key] = max
            bestMax[key] = maxOf(bestMax[key] ?: 0, max)
        }
    }

    /** `{id du test}` dans les noms : le choix d'exercice en cours pour ce test (« crunch »). */
    // --- Remplacements ---

    private val exerciseByKey: Map<String, Exercise> = buildMap {
        plan.weeks.forEach { w -> w.days.forEach { d -> d.exercises.forEach { putIfAbsent(it.key, it) } } }
    }
    // Alternatives d'un exercice, écrites sur n'importe lequel de ses jours.
    private val alternativesByKey: Map<String, List<Alternative>> =
        plan.weeks.flatMap { w -> w.days.flatMap { it.exercises } }.groupBy { it.key }
            .mapValues { (_, list) -> list.flatMap { it.alternatives }.distinctBy { it.id.trim() } }

    /** Nom d'un exercice de la routine. */
    fun exerciseName(key: String): String? = exerciseByKey[key]?.name

    /**
     * Ce qui peut remplacer [ex] dans la séance [day] : ses alternatives de la routine, puis les autres exercices de sa
     * famille (sauf ceux déjà au programme ce jour-là).
     */
    fun replacementOptions(ex: Exercise, day: PlanDay): List<ReplacementOption> {
        val inDay = day.exercises.map { it.key }.toSet()
        val alternatives = alternativesByKey[ex.key].orEmpty().map {
            ReplacementOption(ReplacementOption.ALT + it.id.trim(), it.name, it.description, it.follows.trim().lowercase(), it.factor, alternative = true)
        }
        val family = plan.families.filter { ex.key in it.exercises }.flatMap { it.exercises }.distinct()
            .filter { it != ex.key && it !in inDay }
            .mapNotNull { k -> exerciseByKey[k]?.let { ReplacementOption(ReplacementOption.EXERCISE + k, it.name, it.description, k, 1.0, alternative = false) } }
        return alternatives + family
    }

    /** Exercices de la séance qu'on peut remplacer (pas la variante ni son exercice de base, pas un test). */
    fun replaceable(session: Session): List<Int> = session.day.exercises.indices.filter { e ->
        e != session.variantIndex && e != session.variantBaseIndex && !session.day.exercises[e].retest &&
            replacementOptions(session.day.exercises[e], session.day).isNotEmpty()
    }

    /** L'option choisie pour l'exercice [e] de la séance, null s'il est fait tel que prévu. */
    fun currentReplacement(session: Session, e: Int): String? =
        session.day.exercises.getOrNull(e)?.let { program.replacementOn(session.index, e, it.key, session.date) }

    /** L'exercice [key] tel qu'il serait prescrit ce jour-là : son niveau du moment (réglage compris), sans rien apprendre. */
    private fun followed(key: String, date: LocalDate, light: Boolean, boss: Boolean, burst: Boolean): Prescription? {
        val ex = exerciseByKey[key] ?: return null
        val extra = if (ex.progression == null) 0 else if (boss) BOSS_STEPS else if (burst) BURST_STEPS else 0
        val view = adaptViews[key]
        if (view != null) {
            val level = (view.steps + (adapt[key]?.applied ?: view.applied)).coerceAtLeast(0) + extra
            return Prescription.of(view.exercise, level, light, boss = boss, lastMax = lastMax[key], burst = burst, weighted = view.weighted)
        }
        val palier = if (ex.levels.isEmpty()) null else ex.levelIndexFor(ex.test?.let(testResults::get))
        val base = palier?.let { named(ex.atLevel(it), date) } ?: ex
        return Prescription.of(base, extra, light, boss = boss, lastMax = lastMax[key], palier = palier, burst = burst)
    }

    private fun replacementPrescription(option: ReplacementOption, original: Exercise, date: LocalDate, light: Boolean, boss: Boolean, burst: Boolean): Prescription? {
        val f = followed(option.follows, date, light, boss, burst) ?: return null
        val originalName = named(original, date).name
        if (!option.alternative) return f.copy(replaces = originalName)
        fun scale(v: Int?) = v?.let { maxOf(1, Math.round(it * option.factor).toInt()) }
        val reps = scale(f.reps)
        val seconds = scale(f.seconds)
        // Sans progression : l'exercice suit celui qu'il imite, ce qui y est noté ne règle rien.
        val exercise = Exercise(
            name = option.name,
            id = option.id.removePrefix(ReplacementOption.ALT),
            description = option.description,
            sets = f.sets,
            reps = reps?.toString(),
            duration = seconds?.let(Measures::formatSeconds),
            rest = f.exercise.rest,
            amrap = f.allOut,
            // Il remplace l'exercice : mêmes muscles.
            muscles = original.muscles,
            assists = original.assists,
        )
        return f.copy(
            exercise = exercise, reps = reps, seconds = seconds, target = scale(f.target),
            sided = null, variant = null, replacedBy = null, replaces = originalName, palier = null, levelCount = 0,
        )
    }

    private fun named(ex: Exercise, date: LocalDate): Exercise {
        if (optionTests.isEmpty() || ('{' !in ex.name && ex.description?.contains('{') != true)) return ex
        return ex.copy(name = program.exerciseName(ex.name, date), description = ex.description?.let { program.exerciseName(it, date) })
    }

    /** Variantes (de la routine et perso) : leurs max notés et leur taux. */
    fun variantProgress(): List<VariantProgress> {
        ensure(lastIndexOnOrBefore(today))
        return variants.filter { (_, c) -> c == null || !c.deleted }.map { (v, c) ->
            val test = plan.test(plan.family(v.family)?.test)
            VariantProgress(
                v, plan.family(v.family)?.name ?: v.family, variantPoints[v.id].orEmpty().toList(), ratios[v.id] ?: v.ratio, v.ratio, c != null,
                testName = test?.name, testSeconds = test?.isSeconds == true,
                requirements = if (c == null) requirementsNow(v) else emptyList(),
            )
        }
    }

    /**
     * « Pas aujourd'hui » : seulement le jour même, tant que rien n'est noté sur l'exercice de base, la variante
     * ou l'exercice perso (qui changerait de place).
     */
    fun canSkipVariant(session: Session): Boolean {
        if (session.variantIndex == null || session.date != today) return false
        return listOfNotNull(session.variantBaseIndex, session.variantIndex, session.otherIndex)
            .none { program.isDone(session.index, it) || program.logOf(session.index, it) != null }
    }

    /** Dernier résultat connu de chaque test : celui de départ, ou celui du dernier test refait. */
    fun currentTestResults(): Map<String, Int> {
        ensure(lastIndexOnOrBefore(today))
        return testResults.toMap()
    }

    /** Une série par côté : chaque côté à son niveau, le faible en premier avec une série de plus s'il rattrape. */
    private fun sidedPrescription(
        limb: String, ex: Exercise, steps: Int, applied: Int, extra: Int, light: Boolean, at: (Int) -> Prescription,
    ): Prescription {
        val state = balance[ex.key]?.takeIf { !light } ?: BalanceState()
        val weak = state.weak.takeIf { state.mode != BalanceMode.EVEN }
        fun own(side: Side) = (steps + (sideAdapt[ex.key to side]?.applied ?: applied)).coerceAtLeast(0)
        fun strong(side: Side) = state.frozen?.let { minOf(own(side), it) } ?: own(side)
        fun sideLevel(side: Side): Int {
            // Équilibré : les deux côtés font pareil, au niveau moyen.
            if (weak == null) return Math.round((own(Side.LEFT) + own(Side.RIGHT)) / 2.0).toInt() + extra
            return when (side) {
                // Le rattrapage rejoint l'autre côté, il ne le dépasse pas.
                weak -> minOf(own(side) + state.boostLevels, maxOf(own(side), strong(side.other)))
                else -> strong(side)
            } + extra
        }
        val left = at(sideLevel(Side.LEFT))
        val right = at(sideLevel(Side.RIGHT))
        val main = if (weak == Side.RIGHT) right else left
        val n = main.sets ?: 1
        val sided = SidedPlan(
            limb,
            Sides.order(n + if (weak == Side.LEFT) 1 else 0, n + if (weak == Side.RIGHT) 1 else 0, weak ?: Side.LEFT),
            left.perSetPlanned, right.perSetPlanned, if (weak == null) BalanceMode.EVEN else state.mode, weak,
            switchRest = baseSides.with(tuningByKey[ex.key]?.sides).switchRest,
        )
        return main.copy(sets = sided.order.size, sided = sided)
    }

    /**
     * Bloc variante dosé : son réglage sur mesure par-dessus le dosage du taux, en crans d'une rep (ou de 5 s).
     * Le dosage devient la « courbe de base » de la variante, le réglage et les côtés marchent comme pour les autres.
     */
    private fun adaptVariant(block: Prescription, light: Boolean): Prescription {
        val per = block.perSetPlanned ?: return block
        val seconds = block.maxIsSeconds
        val progression = if (seconds) Progression(duration = "5s") else Progression(reps = 1)
        val unit = block.exercise.copy(reps = if (seconds) null else "1", duration = if (seconds) "5s" else null, progression = progression)
        val steps = if (seconds) (per - 5) / 5 else per - 1
        val applied = adapt[block.key]?.applied ?: 0
        adaptViews[block.key] = AdaptBase(unit, steps, applied, false)
        val exercise = block.exercise.copy(progression = progression)
        // Chill : séries divisées par deux, comme le reste de la séance.
        fun at(level: Int): Prescription {
            val p = Prescription.of(unit, level, light, false, null)
            val value = p.perSetPlanned ?: per
            return block.copy(
                exercise = exercise, sets = p.sets ?: block.exercise.sets,
                reps = value.takeIf { !seconds }, seconds = value.takeIf { seconds }, target = value,
            )
        }
        val limb = block.sided?.limb ?: return at(steps + applied)
        return runCatching { sidedPrescription(limb, unit, steps, applied, 0, light, ::at) }.onFailure(Safety::report).getOrElse { block }
    }

    /** Ce que la séance dit de l'exercice part dans son réglage. */
    private fun learn(pr: Prescription, i: Int, e: Int, done: Boolean, final: Boolean, extra: Int) {
        val p = pr.exercise.progression ?: return
        val factor = if (pr.weighted == true) pr.exercise.weights?.factor ?: WeightsOption.DEFAULT_FACTOR else 1.0
        val step = (if (pr.maxIsSeconds) Measures.seconds(p.duration)?.toDouble() else p.reps?.toDouble())?.times(factor)
        val params = adaptParams(pr.key, dateOf(i))
        // Jour Boss : comparé au prévu normal, mais réussir le Boss ne fait monter que d'un cran au plus
        // (on ne tient pas un Boss tous les jours). En dessous du normal, ça baisse comme un autre jour.
        fun signalOf(side: Side?) = Adaptation.signal(
            pr, program.logOf(i, e), done, final, step,
            exerciseFeeling = program.exerciseFeelingOf(i, e), sessionFeeling = program.feelingOf(i), side = side, extra = extra,
            params = params,
        )?.let { if (pr.isBoss) it.coerceAtMost(Adaptation.BOSS_MAX_SIGNAL) else it }
        val view = adaptViews[pr.key]
        // Jamais sous le départ du palier, ni au-delà du plafond de l'exercice (le réglage n'y servirait à rien).
        val minimum = -(view?.steps ?: 0)
        val maximum = view?.let { v ->
            val key = v.exercise to v.weighted
            if (key !in ceilings) ceilings[key] = Adaptation.ceiling(v.exercise, v.weighted)
            ceilings[key]?.let { it - v.steps }
        } ?: Int.MAX_VALUE
        val sided = pr.sided
        if (sided == null) {
            val signal = signalOf(null) ?: return
            adapt[pr.key] = Adaptation.update(adapt[pr.key] ?: AdaptState(), signal, minimum, maximum, params)
            if (final) recentSignals += dateOf(i) to signal
            return
        }
        // Une série par côté : chaque côté se règle sur ses propres séries ; le niveau commun est leur moyenne.
        val common = adapt[pr.key] ?: AdaptState()
        var learned = false
        Side.entries.forEach { side ->
            val signal = signalOf(side) ?: return@forEach
            val k = pr.key to side
            sideAdapt[k] = Adaptation.update(sideAdapt[k] ?: common, signal, minimum, maximum, params)
            if (final) recentSignals += dateOf(i) to signal
            learned = true
        }
        if (!learned) return
        // Un seul côté noté (séance en cours) : l'autre garde le niveau commun.
        val l = sideAdapt[pr.key to Side.LEFT] ?: common
        val r = sideAdapt[pr.key to Side.RIGHT] ?: common
        adapt[pr.key] = AdaptState((l.value + r.value) / 2, Math.round((l.applied + r.applied) / 2.0).toInt(), maxOf(l.samples, r.samples))
        updateBalance(pr, i, e)
    }

    /** L'équilibre après la séance : ce que chaque côté fait vraiment (lissé), l'écart, et le cas qui en sort. */
    private fun updateBalance(pr: Prescription, i: Int, e: Int) {
        val view = adaptViews[pr.key] ?: return
        val sided = pr.sided ?: return
        val log = program.logOf(i, e)
        val done = program.isDone(i, e)
        // Fait de chaque côté : la moyenne de ses séries notées, ou le prévu si c'est simplement coché.
        Side.entries.forEach { side ->
            val values = log?.valuesOf(side)?.filter { it > 0 }.orEmpty()
            val did = if (values.isNotEmpty()) values.average() else if (done) sided.valueOf(side)?.toDouble() else null
            if (did != null) {
                val k = pr.key to side
                sideCapacity[k] = sideCapacity[k]?.let { it * 0.5 + did * 0.5 } ?: did
            }
        }
        val capL = sideCapacity[pr.key to Side.LEFT] ?: return
        val capR = sideCapacity[pr.key to Side.RIGHT] ?: return
        val gap = Sides.gap(Math.round(capL * 10).toInt(), Math.round(capR * 10).toInt()) ?: return
        val weakNow = if (capL <= capR) Side.LEFT else Side.RIGHT
        val before = balance[pr.key] ?: BalanceState()
        fun own(side: Side) = (view.steps + (sideAdapt[pr.key to side]?.applied ?: 0)).coerceAtLeast(0)
        val strongLevel = maxOf(own(Side.LEFT), own(Side.RIGHT))
        val gapLevels = maxOf(1, kotlin.math.abs(own(Side.LEFT) - own(Side.RIGHT)) - before.boostLevels)
        // Le côté faible n'avance son rattrapage que s'il a tenu ce qui lui était demandé, et tant qu'il n'a pas rejoint
        // l'autre côté.
        val caughtUp = before.weak?.let { w -> own(w) + before.boostLevels >= own(w.other) } ?: false
        val weakMet = !caughtUp && before.weak?.let { w ->
            val planned = sided.valueOf(w) ?: return@let false
            val values = log?.valuesOf(w)
            if (values.isNullOrEmpty()) done else values.size >= sided.setsOf(w) && values.all { it >= planned }
        } ?: false
        val perWeek = sessionsPerWeek(pr.key)
        val after = Sides.next(before, gap, weakNow, gapLevels, perWeek, strongLevel, weakMet, sidesParams(pr.key, dateOf(i)))
        // Écart rattrapé : ce que le côté faible a gagné reste à lui.
        if (before.mode != BalanceMode.EVEN && after.mode == BalanceMode.EVEN && before.weak != null && before.boostLevels > 0) {
            val k = pr.key to before.weak
            val st = sideAdapt[k] ?: AdaptState()
            sideAdapt[k] = st.copy(value = st.value + before.boostLevels, applied = st.applied + before.boostLevels)
        }
        balance[pr.key] = after
        // Fin du rattrapage estimée : ce qui reste d'écart, au rythme prévu.
        val eta = after.takeIf { it.mode != BalanceMode.EVEN && it.rate > 0 && perWeek > 0 }?.let {
            dateOf(i).plusDays(Math.round(gapLevels / it.rate / perWeek * 7))
        }
        recordBalance(
            pr.key, view.exercise.name, sided.limb, dateOf(i), Math.round(capL).toInt(), Math.round(capR).toInt(),
            after.mode, after.weak, eta, gap, pr.maxIsSeconds,
        )
    }

    private fun recordBalance(
        key: String, name: String, limb: String, date: LocalDate, left: Int?, right: Int?,
        mode: BalanceMode, weak: Side?, eta: LocalDate?, measured: Double? = null, seconds: Boolean = false,
    ) {
        val gap = measured ?: Sides.gap(left, right) ?: return
        val points = balancePoints.getOrPut(key) { ArrayList() }
        if (points.lastOrNull()?.date == date) points.removeAt(points.lastIndex)
        points += TestPoint(date, Math.round(gap * 100).toInt())
        balanceViews[key] = SideBalance(name, limb, left, right, gap, mode, weak, points.toList(), eta, seconds, key = key)
    }

    /**
     * Séances par semaine où revient l'exercice [key], en moyenne sur les semaines de la routine. Une variante n'est
     * pas dans la routine : ses passages par cycle (voir [Variants.quota]).
     */
    private fun sessionsPerWeek(key: String): Double {
        val variant = variants.firstOrNull { it.first.id.trim().lowercase() == key }?.first
        if (variant != null) {
            val test = plan.family(variant.family)?.test?.let(testResults::get)
            return Variants.quota(variant, test).toDouble() / variantCycleWeeks
        }
        return plan.weeks.sumOf { w -> w.days.count { d -> d.exercises.any { it.key == key } } }.toDouble() / plan.weeks.size.coerceAtLeast(1)
    }

    /**
     * Un autre rythme d'adaptation qui irait mieux, d'après les 3 dernières semaines : presque tout au-dessus du
     * prévu (ou « facile ») → un cran plus vif ; beaucoup en dessous (ou « dur ») → un cran plus doux.
     * Null : rien à proposer (trop tôt, rythme changé récemment, suggestion écartée récemment).
     */
    fun profileSuggestion(): AdaptProfile? {
        ensure(lastIndexOnOrBefore(today))
        val since = today.minusDays(SUGGESTION_DAYS)
        val calm = listOfNotNull(program.profiles.maxOfOrNull { it.date }, program.profileHintDismissed)
            .any { runCatching { LocalDate.parse(it).isAfter(since) }.getOrDefault(false) }
        if (calm) return null
        val recent = recentSignals.filter { !it.first.isBefore(since) && !it.first.isAfter(today) }
        if (recent.size < SUGGESTION_MIN_SIGNALS || recent.map { it.first }.distinct().size < SUGGESTION_MIN_SESSIONS) return null
        val easy = recent.count { it.second >= 0.5 }.toDouble() / recent.size
        val hard = recent.count { it.second <= -0.5 }.toDouble() / recent.size
        val current = program.profileOn(today)
        return when {
            hard >= 0.5 -> when (current) {
                AdaptProfile.BRISK -> AdaptProfile.NORMAL
                AdaptProfile.NORMAL -> AdaptProfile.GENTLE
                AdaptProfile.GENTLE -> null
            }
            easy >= 0.7 -> when (current) {
                AdaptProfile.GENTLE -> AdaptProfile.NORMAL
                AdaptProfile.NORMAL -> AdaptProfile.BRISK
                AdaptProfile.BRISK -> null
            }
            else -> null
        }
    }

    /** L'équilibre gauche / droite des exercices faits une série par côté, à ce jour. */
    fun balances(): List<SideBalance> {
        ensure(lastIndexOnOrBefore(today))
        return balanceViews.values.toList()
    }

    /** Réglage de chaque exercice juste après la séance [index] (ce schedule ne doit rien avoir construit au-delà). */
    private fun snapshotAfter(index: Int): Map<String, Pair<AdaptBase, AdaptState>> {
        ensure(index)
        return adaptViews.mapNotNull { (k, v) -> adapt[k]?.let { k to (v to it) } }.toMap()
    }

    /** Le réglage de chaque exercice à ce jour, comparé à sa courbe de base. */
    fun adaptations(): List<ExerciseAdaptation> {
        ensure(lastIndexOnOrBefore(today))
        return adaptViews.mapNotNull { (key, v) ->
            val state = adapt[key] ?: return@mapNotNull null
            val params = adaptParams(key, today)
            val level = (v.steps + state.applied).coerceAtLeast(0)
            val adapted = Prescription.of(v.exercise, level, false, false, null, weighted = v.weighted)
            val plain = Prescription.of(v.exercise, v.steps, false, false, null, weighted = v.weighted)
            ExerciseAdaptation(
                key = key,
                name = v.exercise.name,
                adapted = adapted.perSetPlanned, base = plain.perSetPlanned, seconds = adapted.maxIsSeconds,
                adaptedSets = adapted.sets, baseSets = plain.sets, crans = state.applied, samples = state.samples, stable = state.isStable(params),
                calibration = params.calibration,
            )
        }
    }

    /**
     * Les exos de la routine qui se règlent sur toi (ni échauffement, ni test, ni dose fixe), dans l'ordre de la
     * semaine, avec leurs jours : de quoi montrer aussi ceux qui ne sont pas encore arrivés.
     */
    fun routineExercises(): List<RoutineExercise> {
        val days = plan.weeks.flatMap { it.days }.sortedBy { it.day }
        val found = LinkedHashMap<String, Exercise>()
        days.forEach { d ->
            d.exercises.forEach { ex ->
                if (!ex.optional && !ex.retest && !ex.steady && (ex.progression != null || ex.levels.isNotEmpty())) found.putIfAbsent(ex.key, ex)
            }
        }
        return found.map { (key, ex) ->
            val on = days.filter { d -> d.exercises.any { it.key == key && !it.retest } }.map { it.day }.distinct()
            RoutineExercise(key, runCatching { named(ex, today).name }.getOrDefault(ex.name), on.map { program.start.dayOfWeek.plus((it - 1).toLong()) })
        }
    }

    /** La fiche de l'exo [key] de la routine, à ce jour (null s'il n'y est pas). */
    fun exerciseInfo(key: String): ExerciseInfo? {
        val base = exerciseByKey[key] ?: return null
        ensure(lastIndexOnOrBefore(today))
        val view = adaptViews[key]
        val test = plan.test(base.test)
        val result = base.test?.let(testResults::get)
        val palier = if (base.levels.isEmpty()) null else base.levelIndexFor(result)
        val shown = named(view?.exercise ?: palier?.let { base.atLevel(it) } ?: base, today)
        val days = plan.weeks.flatMap { w -> w.days.filter { d -> d.exercises.any { it.key == key && !it.retest } }.map { it.day } }.distinct().sorted()
        val weekdays = days.map { program.start.dayOfWeek.plus((it - 1).toLong()) }
        // Tout ce qui se lit à ce jour, avant de regarder les séances à venir (qui font avancer le calcul).
        val family = plan.families.filter { key in it.exercises }.map { it.id }
        val adaptation = adaptations().firstOrNull { it.key == key }
        val balance = balances().firstOrNull { it.key == key }
        val variants = variantProgress().filter { it.variant.family in family }
        val stats = runCatching { Stats.of(this).exercises.firstOrNull { it.key == key } }.getOrNull()
        // Meilleure série de chaque séance faite.
        val history = ArrayList<TestPoint>()
        val eccentricDates = HashSet<LocalDate>()
        for (i in 0..lastIndexOnOrBefore(today)) {
            val s = sessionAt(i) ?: continue
            s.day.exercises.forEachIndexed { e, ex ->
                if (ex.key != key || ex.retest) return@forEachIndexed
                val log = program.logOf(i, e)
                val value = log?.perSet?.maxOrNull()?.takeIf { it > 0 } ?: log?.max
                    ?: if (program.isDone(i, e)) s.prescriptions.getOrNull(e)?.perSetPlanned else null
                if (value != null) history += TestPoint(s.date, value)
                if (value != null && s.prescriptions.getOrNull(e)?.eccentric == true) eccentricDates += s.date
            }
        }
        // Prochaine séance où il est prévu (4 semaines au plus), et sa dose pleine.
        val from = lastIndexOnOrBefore(today).coerceAtLeast(0)
        var next: Pair<LocalDate, Prescription>? = null
        for (i in from until from + 4 * DAYS_PER_WEEK) {
            val s = sessionAt(i) ?: continue
            if (s.date.isBefore(today)) continue
            val e = s.day.exercises.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: continue
            next = s.date to s.full[e]
            break
        }
        return ExerciseInfo(
            key = key,
            name = shown.name,
            description = shown.description,
            muscles = base.muscles,
            assists = base.assists,
            palier = palier,
            levelCount = base.levels.size,
            testName = test?.let { program.testName(it, today) },
            testResult = result,
            nextLevelAt = palier?.let { base.levels.getOrNull(it + 1)?.from },
            testSeconds = test?.isSeconds == true,
            dose = next?.second?.summary()?.substringBefore(" · "),
            progression = base.progression?.describe(),
            adaptation = adaptation,
            weekdays = weekdays,
            nextDate = next?.first,
            stats = stats,
            history = history.takeLast(12),
            eccentricDates = eccentricDates,
            seconds = next?.second?.maxIsSeconds ?: (base.reps == null && base.duration != null),
            balance = balance,
            alternatives = alternativesByKey[key].orEmpty().map { it.name },
            variants = variants,
        )
    }


    companion object {
        /**
         * Exos notés en entier mais laissés incomplets par l'ancienne règle (une série sous le prévu) : validés, sauf
         * jour Boss. Le même programme s'il n'y a rien à changer.
         */
        fun revalidated(program: StoredProgram, today: LocalDate): StoredProgram {
            val schedule = ProgramSchedule(program, today)
            val add = program.logs.filter { (key, log) -> key !in program.done && log.perSet?.let { v -> v.isNotEmpty() && v.all { it > 0 } } == true }
                .keys.filter { key ->
                    val day = key.substringBefore(':').toIntOrNull() ?: return@filter false
                    val e = key.substringAfter(':').toIntOrNull() ?: return@filter false
                    schedule.sessionAt(day)?.prescriptions?.getOrNull(e)?.isMetBy(program.logs.getValue(key)) == true
                }
            return if (add.isEmpty()) program else program.copy(done = program.done + add)
        }

        /**
         * Ce que la séance [index] a changé au réglage de ses exercices : le même programme sans rien de noté ce
         * jour-là, puis avec. La progression normale n'y est pas : seulement ce qui vient de l'adaptation.
         */
        fun adaptationChanges(program: StoredProgram, index: Int, today: LocalDate): List<AdaptChange> {
            val beforeSchedule = ProgramSchedule(program.withoutDay(index), today)
            val afterSchedule = ProgramSchedule(program, today)
            val before = beforeSchedule.snapshotAfter(index)
            val after = afterSchedule.snapshotAfter(index)
            val session = afterSchedule.resolved[index] ?: return emptyList()
            val keys = session.prescriptions.filter { !it.isTest && !it.exercise.optional && it.exercise.progression != null }
                .map { it.key }.distinct()
            return keys.flatMap { key ->
                val (view, now) = after[key] ?: return@flatMap emptyList()
                val was = before[key]?.second ?: AdaptState()
                val changes = ArrayList<AdaptChange>()
                fun label(applied: Int): String {
                    val p = Prescription.of(view.exercise, (view.steps + applied).coerceAtLeast(0), false, false, null, weighted = view.weighted)
                    val value = p.perSetPlanned?.let { if (p.maxIsSeconds) Measures.formatSeconds(it) else "$it" } ?: "?"
                    return listOfNotNull(p.sets?.let { "$it ×" }, value).joinToString(" ") + (view.exercise.perSide?.let { " ${Sides.per(it)}" } ?: "")
                }
                val nowLabel = label(now.applied)
                val wasLabel = label(was.applied)
                if (nowLabel != wasLabel) {
                    changes += AdaptChange(view.exercise.name, if (now.applied > was.applied) AdaptChange.Kind.UP else AdaptChange.Kind.DOWN, nowLabel, wasLabel)
                }
                val b = beforeSchedule.balance[key] ?: BalanceState()
                val a = afterSchedule.balance[key] ?: BalanceState()
                val limb = view.exercise.perSide
                if (limb != null && b.mode == BalanceMode.EVEN && a.mode != BalanceMode.EVEN && a.weak != null) {
                    changes += AdaptChange(
                        view.exercise.name, AdaptChange.Kind.BEHIND, side = Sides.label(limb, a.weak).lowercase(),
                        strongWaits = a.mode == BalanceMode.WEAK_ONLY,
                    )
                } else if (limb != null && b.mode != BalanceMode.EVEN && a.mode == BalanceMode.EVEN) {
                    changes += AdaptChange(view.exercise.name, AdaptChange.Kind.EVEN)
                }
                changes
            }
        }
    }

    fun sessionAt(dayIndex: Int): Session? {
        if (!inRange(dayIndex)) return null
        ensure(dayIndex)
        return resolved[dayIndex]
    }

    fun sessionOn(date: LocalDate): Session? = indexOn(date)?.let(::sessionAt)

    /** Séances des index 0 à [lastIndex] inclus. */
    fun sessionsThrough(lastIndex: Int): List<Session> {
        ensure(lastIndex)
        return (0..minOf(lastIndex, builtUpTo)).mapNotNull { resolved[it] }
    }

    fun allSessionsOrUpTo(date: LocalDate): List<Session> =
        if (totalDays != null) sessionsThrough(totalDays - 1) else sessionsThrough(lastIndexOnOrBefore(date))

    fun exerciseState(session: Session, e: Int): ExerciseState {
        if (program.isDone(session.index, e)) return ExerciseState.DONE
        val log = program.logOf(session.index, e)
        return if (log != null && (log.sets != null || log.reps != null || log.seconds != null)) ExerciseState.PARTIAL
        else ExerciseState.TODO
    }

    fun doneCount(session: Session) = (0 until session.exerciseCount).count { program.isDone(session.index, it) }

    /** Exercices obligatoires faits (les facultatifs ne comptent pas). */
    fun requiredDone(session: Session) = session.required.count { program.isDone(session.index, it) }

    /** Séance finie : tous les exercices obligatoires faits. */
    fun isComplete(session: Session) = requiredDone(session) == session.requiredCount

    /** Boss pas fini mais fait aux trois quarts : la journée est sauvée, sans points ni 🔥 en plus. */
    fun isSaved(session: Session) =
        session.isBoss && !isComplete(session) && requiredDone(session) >= ceil(session.requiredCount * BOSS_SAVE_SHARE)

    fun status(session: Session): SessionStatus {
        val done = doneCount(session)
        val started = done > 0 || (0 until session.exerciseCount).any { exerciseState(session, it) == ExerciseState.PARTIAL }
        return when {
            isComplete(session) -> SessionStatus.DONE
            isSaved(session) -> SessionStatus.SAVED
            started -> SessionStatus.PARTIAL
            session.date.isBefore(today) -> SessionStatus.MISSED
            else -> SessionStatus.PENDING
        }
    }

    /** Une séance à venir ne se coche pas : on ne valide que ce qui est fait. */
    fun isLocked(session: Session) = session.date.isAfter(today)

    /**
     * Burst possible : séance du jour, pas Boss ni décharge, rien encore coché, et pas d'autre Burst
     * la même semaine du programme.
     */
    fun canBurst(session: Session): Boolean =
        session.date == today && !session.isBoss && !session.isDeload && !session.isBurst && !session.isChill &&
            session.prescriptions.any { it.exercise.progression != null || it.sets != null } &&
            untouched(session) &&
            program.bursts.none { it / DAYS_PER_WEEK == session.programWeek }

    /** Annuler un Burst tant que rien n'est coché. */
    fun canUndoBurst(session: Session): Boolean = session.isBurst && untouched(session)

    /** Chill possible : comme le Burst, une fois par semaine du programme, jamais un jour de Boss. */
    fun canChill(session: Session): Boolean =
        session.date == today && !session.isBoss && !session.isDeload && !session.isBurst && !session.isChill &&
            session.prescriptions.any { it.sets != null || it.reps != null || it.seconds != null } &&
            untouched(session) &&
            program.chills.none { it / DAYS_PER_WEEK == session.programWeek }

    fun canUndoChill(session: Session): Boolean = session.isChill && untouched(session)

    /** Forme du jour : le jour même, sur une séance pas encore commencée (ni Chill, ni semaine allégée). */
    fun canChooseForm(session: Session): Boolean =
        session.date == today && !session.isDeload && !session.isChill && !session.isBurst && untouched(session) &&
            session.prescriptions.any { it.sets != null || it.reps != null || it.seconds != null }

    private fun untouched(session: Session) =
        (0 until session.exerciseCount).none { program.isDone(session.index, it) || program.logOf(session.index, it) != null }

    fun nextSession(): Session? {
        var i = maxOf(0, lastIndexOnOrBefore(today.minusDays(1)) + 1)
        val stop = i + cycleLength * 2 + postpones.size + DAYS_PER_WEEK
        while (i < stop && inRange(i)) {
            val s = sessionAt(i)
            if (s != null && !s.date.isBefore(today) && !isComplete(s)) return s
            i++
        }
        return null
    }

    fun progress(): Progress {
        val sessions = allSessionsOrUpTo(today)
        var doneEx = 0
        var totalEx = 0
        var doneSessions = 0
        var missed = 0
        for (s in sessions) {
            doneEx += requiredDone(s)
            totalEx += s.requiredCount
            if (isComplete(s)) doneSessions++
            else if (status(s) == SessionStatus.MISSED) missed++
        }

        var streak = 0
        for (s in sessions.asReversed()) {
            if (s.date.isAfter(today)) continue
            if (isComplete(s)) streak++
            else if (isSaved(s)) continue // Boss sauvé : la série tient sans avancer
            else if (s.date == today) continue // la séance du jour ne casse pas la série
            else break
        }
        return Progress(doneEx, totalEx, doneSessions, sessions.size, streak, missed, nextSession())
    }
}
