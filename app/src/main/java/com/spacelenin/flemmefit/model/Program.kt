package com.spacelenin.flemmefit.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** État persistant de l'app. [schemaVersion] permettra de migrer le stockage si besoin. */
@Serializable
data class AppState(
    val schemaVersion: Int = 1,
    val programs: List<StoredProgram> = emptyList(),
)

/**
 * Ce qui a réellement été fait sur un exercice, quand ça diffère du prévu, et/ou le max
 * noté sur une série « à fond ». Valeurs par série.
 */
@Serializable
data class ExerciseLog(
    val sets: Int? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    /** Max de la série à fond : répétitions, ou secondes pour un exercice en durée. */
    val max: Int? = null,
    /** Détail série par série (répétitions, ou secondes pour un exercice en durée) ; 0 = série pas faite. */
    val perSet: List<Int>? = null,
) {
    val isEmpty get() = sets == null && reps == null && seconds == null && max == null && perSet == null

    companion object {
        /** Saisie série par série : les séries faites, et la dernière comme max si elle était à fond. */
        fun ofSets(values: List<Int>, allOut: Boolean) = ExerciseLog(
            sets = values.count { it > 0 },
            max = values.lastOrNull()?.takeIf { allOut && it > 0 },
            perSet = values,
        )
    }
}

/** Passage d'un test à un autre exercice au choix, à partir de [date], avec le max refait ce jour-là. */
@Serializable
data class TestChange(val test: String, val option: String, val date: String, val result: Int? = null)

/**
 * Un programme importé et son suivi.
 *
 * Les jours du programme sont numérotés à partir de 0, répétitions comprises (« index de jour »).
 * Chaque entrée de [postpones] insère un jour de décalage juste avant ce jour,
 * ce qui repousse ce jour et tout le reste du programme.
 */
@Serializable
data class StoredProgram(
    val id: String,
    val plan: Plan,
    /** Date ISO (yyyy-MM-dd) du jour 1 de la semaine 1. */
    val startDate: String,
    val colorIndex: Int = 0,
    val importedAt: Long = 0,
    val postpones: List<Int> = emptyList(),
    /** Exercices validés, clés produites par [doneKey]. */
    val done: Set<String> = emptySet(),
    /** Saisies (réel fait, max), mêmes clés que [done]. */
    val logs: Map<String, ExerciseLog> = emptyMap(),
    /** Résultats des tests de départ ([Plan.tests]), par id de test. */
    val tests: Map<String, Int> = emptyMap(),
    /** Jours (index) passés en Burst : séance renforcée, une par semaine au plus. */
    val bursts: List<Int> = emptyList(),
    /** Jours (index) passés en Chill : séance allégée, une par semaine au plus. */
    val chills: List<Int> = emptyList(),
    /** Choix « avec / sans poids » dit pour un exercice d'un jour, mêmes clés que [done]. */
    val weights: Map<String, Boolean> = emptyMap(),
    /** « J'ai fait autre chose » : activités en plus, par date ISO. Affichées, jamais comptées. */
    val extras: Map<String, List<String>> = emptyMap(),
    /** Ressenti de fin de séance, par index de jour : 0 dur, 1 bien, 2 en forme. */
    val feelings: Map<String, Int> = emptyMap(),
    /** Exercices ajoutés par l'utilisateur : variantes perso, ou « Autre ». */
    val custom: List<CustomExercise> = emptyList(),
    /** Jours variante refusés (« Pas aujourd'hui ») : la variante est replacée plus tard dans le cycle. */
    val variantSkips: Set<Int> = emptySet(),
    /** Choix d'exercice des tests à options (crunch ou sit-up), dans l'ordre où ils ont été faits. */
    val testChanges: List<TestChange> = emptyList(),
    /**
     * Exercices arrivés avec une nouvelle version de la routine sur des séances déjà terminées, mêmes clés
     * que [done] : facultatifs sur ces séances, pour qu'elles restent validées.
     */
    val excused: Set<String> = emptySet(),
    /** Ressenti de chaque exercice fait, mêmes clés que [done] : voir [Adaptation.FEELING_HARD] et suivants. */
    val exerciseFeelings: Map<String, Int> = emptyMap(),
) {
    val start: LocalDate get() = LocalDate.parse(startDate)

    fun isDone(dayIndex: Int, exerciseIndex: Int) = doneKey(dayIndex, exerciseIndex) in done

    fun logOf(dayIndex: Int, exerciseIndex: Int): ExerciseLog? = logs[doneKey(dayIndex, exerciseIndex)]

    /** Cocher / décocher. Décocher efface aussi la saisie. */
    fun toggle(dayIndex: Int, exerciseIndex: Int): StoredProgram {
        val key = doneKey(dayIndex, exerciseIndex)
        return if (key in done) copy(done = done - key, logs = logs - key) else copy(done = done + key)
    }

    fun setSessionDone(dayIndex: Int, exerciseCount: Int, value: Boolean): StoredProgram {
        val keys = (0 until exerciseCount).map { doneKey(dayIndex, it) }
        return if (value) copy(done = done + keys) else copy(done = done - keys.toSet(), logs = logs - keys.toSet())
    }

    /** Enregistre une saisie. [complete] = le prévu est atteint, l'exercice est validé ; sinon il est partiel. */
    fun saveLog(dayIndex: Int, exerciseIndex: Int, log: ExerciseLog, complete: Boolean): StoredProgram {
        val key = doneKey(dayIndex, exerciseIndex)
        val newLogs = if (log.isEmpty) logs - key else logs + (key to log)
        return copy(done = if (complete) done + key else done - key, logs = newLogs)
    }

    fun extrasOn(date: LocalDate): List<String> = extras[date.toString()].orEmpty()

    fun withExtra(date: LocalDate, text: String): StoredProgram {
        val clean = text.trim().take(MAX_EXTRA_LENGTH).ifEmpty { return this }
        return copy(extras = extras + (date.toString() to extrasOn(date) + clean))
    }

    fun withoutExtra(date: LocalDate, index: Int): StoredProgram {
        val left = extrasOn(date).filterIndexed { i, _ -> i != index }
        return copy(extras = if (left.isEmpty()) extras - date.toString() else extras + (date.toString() to left))
    }

    fun feelingOf(dayIndex: Int): Int? = feelings[dayIndex.toString()]

    fun exerciseFeelingOf(dayIndex: Int, exerciseIndex: Int): Int? = exerciseFeelings[doneKey(dayIndex, exerciseIndex)]

    fun withExerciseFeeling(dayIndex: Int, exerciseIndex: Int, feeling: Int?): StoredProgram {
        val key = doneKey(dayIndex, exerciseIndex)
        return copy(exerciseFeelings = if (feeling == null) exerciseFeelings - key else exerciseFeelings + (key to feeling.coerceIn(0, 2)))
    }

    fun withFeeling(dayIndex: Int, feeling: Int) = copy(feelings = feelings + (dayIndex.toString() to feeling.coerceIn(0, 2)))

    fun weightsChoice(dayIndex: Int, exerciseIndex: Int): Boolean? = weights[doneKey(dayIndex, exerciseIndex)]

    fun withWeights(dayIndex: Int, exerciseIndex: Int, on: Boolean) = copy(weights = weights + (doneKey(dayIndex, exerciseIndex) to on))

    fun skipVariant(dayIndex: Int) = copy(variantSkips = variantSkips + dayIndex)

    fun addCustom(exercise: CustomExercise) = copy(custom = custom + exercise)

    fun updateCustom(id: String, transform: (CustomExercise) -> CustomExercise) =
        copy(custom = custom.map { if (it.id == id) transform(it) else it })

    fun burst(dayIndex: Int) = if (dayIndex in bursts) this else copy(bursts = (bursts + dayIndex).sorted())

    fun undoBurst(dayIndex: Int) = copy(bursts = bursts - dayIndex)

    fun chill(dayIndex: Int) = if (dayIndex in chills) this else copy(chills = (chills + dayIndex).sorted())

    fun undoChill(dayIndex: Int) = copy(chills = chills - dayIndex)

    fun postpone(dayIndex: Int) = copy(postpones = (postpones + dayIndex).sorted())

    fun undoPostpone(dayIndex: Int): StoredProgram {
        val i = postpones.indexOf(dayIndex)
        return if (i < 0) this else copy(postpones = postpones.toMutableList().apply { removeAt(i) })
    }

    fun postponeCount(dayIndex: Int) = postpones.count { it == dayIndex }

    fun withStartDate(date: LocalDate) = copy(startDate = date.toString())

    /**
     * Remplace la routine en gardant le suivi : chaque case cochée ou saisie suit son exercice
     * (même clé, même jour du calendrier), même si l'ordre des exercices a changé.
     * Ce qui ne correspond plus à rien dans la nouvelle routine est perdu (voir [planUpdateLoss]).
     */
    fun withPlan(newPlan: Plan): StoredProgram {
        val moved = remap(newPlan)
        return copy(
            plan = newPlan,
            done = done.mapNotNull { moved[it] }.toSet(),
            logs = logs.mapNotNull { (k, v) -> moved[k]?.let { it to v } }.toMap(),
            weights = weights.mapNotNull { (k, v) -> moved[k]?.let { it to v } }.toMap(),
            exerciseFeelings = exerciseFeelings.mapNotNull { (k, v) -> moved[k]?.let { it to v } }.toMap(),
            tests = tests.filterKeys { id -> newPlan.tests.any { it.id == id } },
            excused = excused.mapNotNull { moved[it] }.toSet() + newOnFinishedDays(newPlan),
        )
    }

    /** Exercices obligatoires de [newPlan] sans équivalent dans les séances où tout l'obligatoire était fait. */
    private fun newOnFinishedDays(newPlan: Plan): Set<String> =
        done.mapNotNull { it.substringBefore(':').toIntOrNull() }.distinct().flatMap { dayIndex ->
            val old = plan.dayAt(dayIndex)?.exercises ?: return@flatMap emptyList()
            val new = newPlan.dayAt(dayIndex)?.exercises ?: return@flatMap emptyList()
            val finished = old.indices.all { old[it].optional || doneKey(dayIndex, it) in done || doneKey(dayIndex, it) in excused }
            if (!finished) return@flatMap emptyList()
            new.indices.filter { !new[it].optional && old.none { o -> o.key == new[it].key } }
                .map { doneKey(dayIndex, it) }
        }.toSet()

    /** Nombre de cases cochées ou saisies qui ne trouveraient plus leur exercice dans [newPlan]. */
    fun planUpdateLoss(newPlan: Plan): Int {
        val moved = remap(newPlan)
        return (done + logs.keys).count { it !in moved }
    }

    /** Ancienne clé de suivi → nouvelle clé, pour les exercices retrouvés dans [newPlan]. */
    private fun remap(newPlan: Plan): Map<String, String> {
        val result = HashMap<String, String>()
        (done + logs.keys + weights.keys + excused + exerciseFeelings.keys).mapNotNull { key ->
            val day = key.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            val ex = key.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            Triple(key, day, ex)
        }.groupBy { it.second }.forEach { (dayIndex, entries) ->
            val oldExercises = plan.dayAt(dayIndex)?.exercises ?: return@forEach
            val newExercises = newPlan.dayAt(dayIndex)?.exercises ?: return@forEach
            // Variante et exercice perso du jour viennent après ceux de la routine : ils suivent leur rang.
            entries.filter { it.third >= oldExercises.size }.forEach {
                result[it.first] = doneKey(dayIndex, newExercises.size + it.third - oldExercises.size)
            }
            // Un même exercice peut apparaître deux fois dans une séance : on les apparie dans l'ordre.
            val used = HashSet<Int>()
            for (e in oldExercises.indices) {
                val key = oldExercises[e].key
                val target = newExercises.indices.firstOrNull { it !in used && newExercises[it].key == key } ?: continue
                used += target
                entries.firstOrNull { it.third == e }?.let { result[it.first] = doneKey(dayIndex, target) }
            }
        }
        return result
    }

    /** Le choix en cours pour [testId] à [date], null tant que rien n'est dit. */
    fun optionOn(testId: String, date: LocalDate): String? =
        testChanges.lastOrNull { it.test == testId && !date.isBefore(LocalDate.parse(it.date)) }?.option

    /** Nom d'exercice tel qu'affiché à [date] : `{id du test}` remplacé par le choix en cours (« crunch »). */
    fun exerciseName(name: String, date: LocalDate): String =
        plan.tests.filter { it.options.isNotEmpty() }.fold(name) { acc, t -> acc.replace("{${t.id}}", t.optionLabel(optionOn(t.id, date))) }

    /** Nom du test tel qu'affiché à [date] : celui du choix en cours, sinon celui de la routine. */
    fun testName(test: PlanTest, date: LocalDate): String = test.option(optionOn(test.id, date))?.name ?: test.name

    /**
     * Change l'exercice d'un test. Le premier choix vaut depuis le début (c'est ce qui a été fait) ; les suivants
     * à partir de [today], avec le max refait tout de suite s'il y en a un : il devient la référence du test.
     */
    fun changeTestOption(testId: String, option: String, today: LocalDate, result: Int? = null): StoredProgram {
        val current = optionOn(testId, today)
        if (current == option) return this
        val first = testChanges.none { it.test == testId }
        val change = if (first) TestChange(testId, option, startDate)
        else TestChange(testId, option, today.toString(), result?.takeIf { it > 0 })
        return copy(testChanges = testChanges.filterNot { !first && it.test == testId && it.date == change.date } + change)
    }

    fun withTests(results: Map<String, Int>) = copy(tests = results.filterKeys { id -> plan.tests.any { it.id == id } })

    /** Tests de départ pas encore renseignés. */
    val missingTests: List<PlanTest> get() = plan.tests.filter { it.id !in tests }

    /** Plus grand index de jour ayant une trace (case cochée ou saisie). */
    fun lastTrackedIndex(): Int = (done.asSequence() + logs.keys.asSequence())
        .mapNotNull { it.substringBefore(':').toIntOrNull() }
        .maxOrNull() ?: -1

    companion object {
        fun doneKey(dayIndex: Int, exerciseIndex: Int) = "$dayIndex:$exerciseIndex"
        const val MAX_EXTRA_LENGTH = 80
        /** Ressentis, du plus dur au plus facile. */
        val FEELINGS = listOf("😫", "🙂", "💪")
    }
}
