package com.spacelenin.flemmefit.model

import java.time.LocalDate

/** Un résultat de test, avec l'exercice au choix fait ce jour-là (crunch, sit-up…) s'il y en a. */
data class TestPoint(val date: LocalDate, val value: Int, val option: String? = null)

/** Où en est un exercice à niveaux rattaché au test, et ce qu'il faut au test pour passer au suivant. */
data class LevelGoal(
    val exercise: String,
    /** Niveau actuel, 0-based. */
    val level: Int,
    val levelCount: Int,
    /** Résultat de test qui ouvre le niveau suivant, null au dernier niveau. */
    val nextFrom: Int?,
    /** Ce que demande le niveau suivant (« 4 × 9 »). */
    val nextLabel: String?,
)

/** Historique d'un test (résultat de départ, puis chaque test refait) et prochains paliers. */
data class TestProgress(
    val test: PlanTest,
    val points: List<TestPoint>,
    /** Prochaine séance où le test est refait, s'il y en a une de prévue. */
    val nextDate: LocalDate?,
    val goals: List<LevelGoal>,
    /** Nom affiché : celui de l'exercice au choix en cours. */
    val name: String = test.name,
) {
    /** Les résultats depuis le dernier changement d'exercice : départ et record ne comparent que ceux-là. */
    val segment: List<TestPoint> get() = points.takeLastWhile { it.option == points.last().option }
    val current: Int? get() = points.lastOrNull()?.value
    val first: Int? get() = segment.firstOrNull()?.value
    val best: Int? get() = segment.maxOfOrNull { it.value }
    /** Premier résultat avec l'exercice en cours, s'il en a changé. */
    val switchedAt: TestPoint? get() = segment.firstOrNull()?.takeIf { segment.size < points.size }

    /** Index des points où l'exercice change (un repère sur la courbe). */
    val switches: List<Int> get() = points.indices.filter { it > 0 && points[it].option != points[it - 1].option }

    /** Ce qu'il manque au prochain test pour monter d'un niveau (sur l'exercice le plus proche), null si rien à gagner. */
    val toNextLevel: Int? get() = goals.mapNotNull { it.nextFrom }.minOrNull()?.let { it - (current ?: 0) }?.takeIf { it > 0 }

    fun format(value: Int): String = if (test.isSeconds) Measures.formatSeconds(value) else "$value"
}

/** Jours regardés en avance pour trouver le prochain test (un cycle de récup et un peu plus). */
private const val LOOK_AHEAD_DAYS = 8 * DAYS_PER_WEEK

/** Les tests du programme : courbe des résultats, prochain test, niveaux à portée. */
fun ProgramSchedule.testProgress(): List<TestProgress> {
    val plan = program.plan
    if (plan.tests.isEmpty()) return emptyList()
    val lastIndex = lastIndexOnOrBefore(today)
    val past = if (lastIndex >= 0) sessionsThrough(lastIndex) else emptyList()
    val current = currentTestResults()
    val exercises = plan.weeks.flatMap { w -> w.days.flatMap { it.exercises } }

    return plan.tests.map { test ->
        val points = buildList {
            fun point(date: LocalDate, value: Int) = TestPoint(date, value, program.optionOn(test.id, date))
            program.tests[test.id]?.let { add(point(startDate, it)) }
            for (s in past) {
                s.prescriptions.forEachIndexed { e, p ->
                    if (p.isTest && p.exercise.test == test.id) program.logOf(s.index, e)?.max?.let { add(point(s.date, it)) }
                }
            }
            program.testChanges.filter { it.test == test.id && it.result != null }.forEach { add(point(LocalDate.parse(it.date), it.result!!)) }
        }.sortedBy { it.date }
        val nextDate = ((lastIndex + 1)..(lastIndex + LOOK_AHEAD_DAYS)).firstNotNullOfOrNull { i ->
            sessionAt(i)?.takeIf { s ->
                s.prescriptions.withIndex().any { (e, p) -> p.isTest && p.exercise.test == test.id && program.logOf(s.index, e)?.max == null }
            }?.date
        }
        val result = current[test.id]
        val goals = exercises
            .filter { it.test == test.id && it.levels.isNotEmpty() && !it.retest }
            .distinctBy { it.key }
            .map { ex ->
                val level = ex.levelIndexFor(result)
                val next = ex.levels.getOrNull(level + 1)
                LevelGoal(
                    exercise = program.exerciseName(ex.name, today),
                    level = level,
                    levelCount = ex.levels.size,
                    nextFrom = next?.from,
                    nextLabel = next?.let { ex.atLevel(level + 1) }?.let { at ->
                        listOfNotNull(at.sets?.let { "$it ×" }, at.reps ?: at.duration).joinToString(" ").ifEmpty { null }
                    },
                )
            }
        TestProgress(test, points, nextDate, goals, name = program.testName(test, today))
    }
}
