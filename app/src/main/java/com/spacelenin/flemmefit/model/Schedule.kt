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
) {
    /** Exercices de la routine, puis variante et exercice perso du jour. */
    val exerciseCount get() = prescriptions.size
    /** Exercices qui comptent pour finir la séance : tout sauf les facultatifs (échauffement, étirements). */
    val required: List<Int> get() = prescriptions.indices.filterNot { prescriptions[it].exercise.optional }.ifEmpty { prescriptions.indices.toList() }
    val requiredCount get() = required.size
    /** Semaine du programme depuis le début (0-based), répétitions comprises. */
    val programWeek get() = index / DAYS_PER_WEEK
}

private const val VARIANT_SALT = 0x5EED1L
private const val OTHER_SALT = 0x5EED2L

/** Crans de progression ajoutés par un Burst. */
const val BURST_STEPS = 2
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

    // --- Jours Boss : une semaine sur 3 à 4, tirée au sort de façon reproductible. ---

    private val bossRandom = Random(seed)
    private val bossWeeks = ArrayList<Int>()
    private val bossWeekSet = HashSet<Int>()

    private fun planDaysOfWeek(programWeek: Int): List<PlanDay> = plan.weeks[programWeek % plan.weeks.size].days

    private fun isBossWeek(programWeek: Int): Boolean {
        if (!plan.boss) return false
        while (bossWeeks.lastOrNull().let { it == null || it < programWeek }) {
            val prev = bossWeeks.lastOrNull()
            var next = if (prev == null) 2 + bossRandom.nextInt(2) else prev + 3 + bossRandom.nextInt(2)
            while (isDeloadWeek(next) || planDaysOfWeek(next).isEmpty()) next++
            if (totalDays != null && next * DAYS_PER_WEEK >= totalDays) next = Int.MAX_VALUE
            bossWeeks += next
            bossWeekSet += next
        }
        return programWeek in bossWeekSet
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
    private val lastMax = HashMap<String, Int>()
    private val testResults = HashMap(program.tests)
    // Max refaits en changeant d'exercice de test : appliqués à la première séance qui suit.
    private val appliedChanges = HashSet<Int>()
    private val optionTests = plan.tests.filter { it.options.isNotEmpty() }
    private val levelOf = HashMap<String, Int>()
    private val levelSince = HashMap<String, Int>()
    // Dernier choix « avec / sans poids » dit pour chaque exercice : proposé par défaut ensuite.
    private val lastWeights = HashMap<String, Boolean>()

    // --- Variantes et exercices perso ---

    private val variantCycleWeeks = if (deloadEvery > 0) deloadEvery else VARIANT_CYCLE_WEEKS
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
        fun wanted(v: Variant) = Variants.quota(v, plan.family(v.family)?.test?.let(testResults::get))
        val candidates = variants.mapNotNull { pick ->
            val (v, custom) = pick
            if (custom != null && !custom.activeOn(date)) return@mapNotNull null
            if (baseIndex(day, v) == null) return@mapNotNull null
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
            val left = otherLeft.getOrPut(o.id) { OTHER_PER_WEEK }
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
        // Le Boss ne se dévoile que la veille : avant, la séance est affichée normalement.
        val boss = !deload && isBossWeek(week) && bossIndexOfWeek(week) == i && !date.isAfter(today.plusDays(1))
        val weeksBefore = if (deloadEvery > 0) week - week / deloadEvery else week
        val burst = !boss && !deload && i in program.bursts
        val chill = !boss && !deload && !burst && i in program.chills

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
                val applied = Adaptation.guard(target, total, test) - baseLevel
                if (applied != state.applied) adapt[ex.key] = state.copy(value = applied.toDouble(), applied = applied)
            }
            ex.test?.let { testSeen[ex.key] = testCount[it] ?: 0 }
            val steps = steps()
            val extra = when {
                p == null -> 0
                boss -> BOSS_STEPS
                burst -> BURST_STEPS
                else -> 0
            }
            val applied = adapt[ex.key]?.applied ?: 0
            val base = palier?.let { named(ex.atLevel(it), date) } ?: ex
            if (!deload && !chill && p != null) adaptViews[ex.key] = AdaptBase(base, steps, applied, weighted)
            val level = (steps + applied).coerceAtLeast(0) + extra
            return Prescription.of(
                base, level, deload || chill, boss = boss, lastMax = lastMax[ex.key], palier = palier, burst = burst,
                weighted = weighted,
            )
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
            val block = Variants.block(
                v, family, base.exercise, family.test?.let(testResults::get), ratios[v.id] ?: v.ratio, v.ratio,
                custom = custom != null, testSeconds = plan.test(family.test)?.isSeconds == true,
            )
            variantBase = b
            if (family.replace) {
                // La variante prend la place de l'exercice de base, au même rang.
                prescriptions[b] = block
                variantIndex = b
            } else {
                prescriptions[b] = base.copy(
                    sets = base.sets?.let(Variants::baseSets),
                    allOut = base.isBoss,
                    target = base.target.takeIf { base.isBoss },
                    replacedBy = v.name,
                )
                variantIndex = prescriptions.size
                prescriptions += block
            }
        }
        if (shown && otherPick != null) {
            otherIndex = prescriptions.size
            prescriptions += prescribe(otherIndex, otherPick.asExercise())
        }
        val order = (0 until n).toMutableList()
        variantIndex?.takeIf { it != variantBase }?.let { order.add(order.indexOf(variantBase!!) + 1, it) }
        // L'exercice perso passe avant le dernier de la séance (les étirements).
        otherIndex?.let { if (n >= 2) order.add(order.indexOf(n - 1), it) else order.add(it) }
        resolved[i] = Session(
            i, week / plan.weeks.size + 1, cycleWeek + 1, day, planWeek.label, date, boss, deload, prescriptions, burst,
            order = order, variantIndex = variantIndex, variantBaseIndex = variantBase, otherIndex = otherIndex, isChill = chill,
        )

        prescriptions.forEachIndexed { e, pr ->
            val key = pr.key
            val max = program.logOf(i, e)?.max
            if (e == variantIndex) {
                val v = variantPick!!.first
                val test = plan.family(v.family)?.test?.let(testResults::get)
                if (max != null && max > 0) {
                    variantPoints.getOrPut(v.id) { ArrayList() } += TestPoint(date, max)
                    if (pr.variant?.full == true && test != null && test > 0) ratios[v.id] = Variants.learn(ratios[v.id] ?: v.ratio, test, max)
                }
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
            val done = program.isDone(i, e)
            if (done && !deload && !chill) validated[key] = (validated[key] ?: 0) + 1
            if (!deload && !chill) learn(pr, i, e, done, final = done || date.isBefore(today))
            if (max == null) return@forEachIndexed
            lastMax[key] = max
        }
    }

    /** `{id du test}` dans les noms : le choix d'exercice en cours pour ce test (« crunch »). */
    private fun named(ex: Exercise, date: LocalDate): Exercise {
        if (optionTests.isEmpty() || '{' !in ex.name) return ex
        return ex.copy(name = program.exerciseName(ex.name, date))
    }

    /** Variantes (de la routine et perso) : leurs max notés et leur taux. */
    fun variantProgress(): List<VariantProgress> {
        ensure(lastIndexOnOrBefore(today))
        return variants.filter { (_, c) -> c == null || !c.deleted }.map { (v, c) ->
            val test = plan.test(plan.family(v.family)?.test)
            VariantProgress(
                v, plan.family(v.family)?.name ?: v.family, variantPoints[v.id].orEmpty().toList(), ratios[v.id] ?: v.ratio, v.ratio, c != null,
                testName = test?.name, testSeconds = test?.isSeconds == true,
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

    /** Ce que la séance dit de l'exercice part dans son réglage. */
    private fun learn(pr: Prescription, i: Int, e: Int, done: Boolean, final: Boolean) {
        val p = pr.exercise.progression ?: return
        val factor = if (pr.weighted == true) pr.exercise.weights?.factor ?: WeightsOption.DEFAULT_FACTOR else 1.0
        val step = (if (pr.maxIsSeconds) Measures.seconds(p.duration)?.toDouble() else p.reps?.toDouble())?.times(factor)
        val signal = Adaptation.signal(
            pr, program.logOf(i, e), done, final, step,
            exerciseFeeling = program.exerciseFeelingOf(i, e), sessionFeeling = program.feelingOf(i),
        ) ?: return
        val view = adaptViews[pr.key]
        // Jamais sous le départ du palier, ni au-delà du plafond de l'exercice (le réglage n'y servirait à rien).
        val minimum = -(view?.steps ?: 0)
        val maximum = view?.let { v ->
            val key = v.exercise to v.weighted
            if (key !in ceilings) ceilings[key] = Adaptation.ceiling(v.exercise, v.weighted)
            ceilings[key]?.let { it - v.steps }
        } ?: Int.MAX_VALUE
        adapt[pr.key] = Adaptation.update(adapt[pr.key] ?: AdaptState(), signal, minimum, maximum)
    }

    /** Le réglage de chaque exercice à ce jour, comparé à sa courbe de base. */
    fun adaptations(): List<ExerciseAdaptation> {
        ensure(lastIndexOnOrBefore(today))
        return adaptViews.mapNotNull { (key, v) ->
            val state = adapt[key] ?: return@mapNotNull null
            val level = (v.steps + state.applied).coerceAtLeast(0)
            val adapted = Prescription.of(v.exercise, level, false, false, null, weighted = v.weighted)
            val plain = Prescription.of(v.exercise, v.steps, false, false, null, weighted = v.weighted)
            ExerciseAdaptation(
                v.exercise.name, adapted.perSetPlanned, plain.perSetPlanned, adapted.maxIsSeconds,
                adapted.sets, plain.sets, state.applied, state.samples, state.stable,
            )
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
