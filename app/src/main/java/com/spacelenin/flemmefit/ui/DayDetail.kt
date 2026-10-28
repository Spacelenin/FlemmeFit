package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.BOSS_STEPS
import com.spacelenin.flemmefit.model.BURST_STEPS
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.ExerciseState
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Prescription
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session
import com.spacelenin.flemmefit.model.Side
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.model.TimerPlans
import java.time.LocalDate

/** Rouge « Boss », indépendant du thème pour rester reconnaissable. */
val BossColor = Color(0xFFE05252)
val DeloadColor = Color(0xFF8FE0A5)
val BurstColor = Color(0xFFFFC94D)
/** Côtés du corps, comme les sabres de Beat Saber : gauche rouge, droite bleue. */
val LeftColor = Color(0xFFE53935)
val RightColor = Color(0xFF2F80FF)

fun sideColor(side: Side): Color = if (side == Side.LEFT) LeftColor else RightColor

fun sideMark(side: Side): String = if (side == Side.LEFT) "🔴" else "🔵"

class DayActions(
    val onToggle: (session: Session, exerciseIndex: Int) -> Unit,
    val onSetAll: (session: Session, done: Boolean) -> Unit,
    val onPostpone: (session: Session) -> Unit,
    val onUndoPostpone: (session: Session) -> Unit,
    /** Saisir ce qui a réellement été fait (appui long). */
    val onEditLog: (session: Session, exerciseIndex: Int) -> Unit,
    /** Cocher un exercice de Boss : demande le max de la série à fond. */
    val onBossCheck: (session: Session, exerciseIndex: Int) -> Unit,
    /** Ouvrir le détail d'un exercice. */
    val onDetail: (session: Session, exerciseIndex: Int) -> Unit,
    /** Passer la séance du jour en Burst (ou l'annuler). */
    val onBurst: (session: Session, on: Boolean) -> Unit,
    /** Passer la séance du jour en Chill (ou l'annuler). */
    val onChill: (session: Session, on: Boolean) -> Unit,
    /** Lancer le mode séance (série par série, repos décompté). */
    val onGuided: (session: Session) -> Unit,
    /** Choix du jour avec / sans poids. */
    val onWeights: (session: Session, exerciseIndex: Int, on: Boolean) -> Unit,
    /** « J'ai fait autre chose » : ajouter une activité en plus à un jour, ou la retirer. */
    val onAddExtra: (date: LocalDate) -> Unit,
    val onRemoveExtra: (date: LocalDate, index: Int) -> Unit,
    /** « Pas aujourd'hui » : la variante du jour est replacée plus tard dans le cycle. */
    val onSkipVariant: (session: Session) -> Unit,
)

/** Détail du jour sélectionné, sous forme d'éléments de liste pour s'insérer dans n'importe quelle LazyColumn. */
fun LazyListScope.dayDetailItems(
    schedule: ProgramSchedule,
    date: LocalDate,
    color: Color,
    actions: DayActions,
) {
    val session = schedule.sessionOn(date)
    item(key = "day-header") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                session != null -> SessionHeader(schedule, session, color, actions)
                else -> {
                    Text(date.longLabel().uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            schedule.isInProgram(date) -> "Jour off 😴"
                            date.isBefore(schedule.startDate) -> "Pas encore commencé"
                            else -> "Programme terminé 🎉"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        when {
                            schedule.isInProgram(date) -> "Rien de prévu. Il ne compte pas : ta série 🔥 reste intacte."
                            date.isBefore(schedule.startDate) -> "Le programme commence le ${schedule.startDate.shortLabel()}."
                            else -> "Programme terminé le ${schedule.endDate?.shortLabel()}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (session == null) {
        extraItems(schedule, date, color, actions)
        return
    }
    session.order.forEach { i ->
        val prescription = session.prescriptions[i]
        item(key = "ex-${session.index}-$i") {
            ExerciseRow(
                tag = when {
                    i == session.variantIndex -> prescription.variant!!.let { v ->
                        if (v.whole) "🎲 Variante : à la place de ${v.baseName.lowercase()}"
                        else "🎲 Variante : à la place de ta série max de ${v.baseName.lowercase()}"
                    }
                    i == session.otherIndex -> "⭐ Exo perso"
                    prescription.exercise.optional -> "Facultatif : le passer ne compte pas contre toi"
                    else -> prescription.replacedBy?.let { "🎲 Série max remplacée par : ${it.lowercase()}" }
                },
                locked = schedule.isLocked(session),
                prescription = prescription,
                state = schedule.exerciseState(session, i),
                log = schedule.program.logOf(session.index, i),
                color = if (session.isBoss) BossColor else color,
                onToggle = {
                    val done = schedule.program.isDone(session.index, i)
                    if (prescription.allOut && !done) actions.onBossCheck(session, i) else actions.onToggle(session, i)
                },
                onEdit = { actions.onEditLog(session, i) },
                onDetail = { actions.onDetail(session, i) },
                onWeights = { actions.onWeights(session, i, it) },
            )
        }
    }
    extraItems(schedule, date, color, actions)
    item(key = "day-footer") {
        Text(
            if (schedule.isLocked(session)) "Séance à venir : elle se fera le jour même."
            else if (session.isBoss) "Terminer un exercice Boss demande ton max. Tout le Boss fait = Boss vaincu. Aux trois quarts, ta série 🔥 tient (sans points)."
            else "Touche la vignette pour marquer comme fait, l'exercice pour son détail. Appui long : saisir tes séries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** « J'ai fait autre chose » : ce qui a été fait en plus ce jour-là. Affiché, jamais compté. */
private fun LazyListScope.extraItems(schedule: ProgramSchedule, date: LocalDate, color: Color, actions: DayActions) {
    if (date.isAfter(schedule.today)) return
    val extras = schedule.program.extrasOn(date)
    item(key = "extras") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (extras.isNotEmpty()) {
                Text("EN PLUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                extras.forEachIndexed { i, extra ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✚", color = DeloadColor, modifier = Modifier.padding(end = 10.dp))
                        Text(extra, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { actions.onRemoveExtra(date, i) }) {
                            Icon(Icons.Default.Close, "Retirer $extra", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            AssistChip(
                onClick = { actions.onAddExtra(date) },
                label = { Text("J'ai fait autre chose") },
                leadingIcon = { Text("✚", color = color) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionHeader(schedule: ProgramSchedule, session: Session, color: Color, actions: DayActions) {
    val done = schedule.requiredDone(session)
    val total = session.requiredCount
    val ratio = if (total == 0) 0f else done.toFloat() / total
    val postponed = schedule.program.postponeCount(session.index)
    val accent = if (session.isBoss) BossColor else color

    if (session.isBurst) {
        Banner("🚀 Burst", "Séance renforcée : +$BURST_STEPS étapes de progression, ou une série de plus. Une fois par semaine.", BurstColor)
        Spacer(Modifier.height(12.dp))
    } else if (session.isChill) {
        Banner("🛋 Chill", "Séance allégée : séries divisées par deux, la progression attend. Elle compte pour ta série 🔥.", DeloadColor)
        Spacer(Modifier.height(12.dp))
    } else if (session.isBoss) {
        Banner(
            "💀 Jour Boss",
            "Attention, ça pique : +$BOSS_STEPS étapes de progression (plus dur qu'un Burst) et dernière série à fond. Bats ton dernier max ! " +
                "Tout fait = Boss vaincu. Aux trois quarts, ta série 🔥 tient, mais sans points.",
            BossColor,
        )
        Spacer(Modifier.height(12.dp))
    } else if (session.isDeload) {
        val withTest = session.prescriptions.any { it.isTest }
        Banner(
            "🌿 Semaine récup",
            "Volume divisé par deux pour récupérer." +
                if (withTest) " Commence par le test : il choisit tes niveaux pour la suite." else " La progression reprend la semaine suivante.",
            DeloadColor,
        )
        Spacer(Modifier.height(12.dp))
    }
    session.variantIndex?.let { v ->
        val info = session.prescriptions[v].variant!!
        val variant = session.prescriptions[v].exercise.name
        val base = info.baseName.lowercase()
        Banner(
            "🎲 Variante du jour : $variant",
            if (info.whole) "Aujourd'hui, ${variant.lowercase()} à la place de $base. Sa dernière série au max ajuste la suite."
            else "Un peu moins de $base, puis un bloc de ${variant.lowercase()} à la place de ta série max. Sa dernière série au max ajuste la suite.",
            BurstColor,
        )
        if (schedule.canSkipVariant(session)) {
            AssistChip(onClick = { actions.onSkipVariant(session) }, label = { Text("Pas aujourd'hui") })
        }
        Spacer(Modifier.height(12.dp))
    }
    Text(
        "${session.date.longLabel().uppercase()} · ${session.position(schedule.program.plan.repeat).uppercase()}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(session.title(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    schedule.program.feelingOf(session.index)?.let { f ->
        Text("Ressenti : ${StoredProgram.FEELINGS[f]}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    session.day.notes?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { ratio }, color = accent, modifier = Modifier.weight(1f))
        Spacer(Modifier.size(12.dp))
        Text("${(ratio * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(8.dp))
    val allDone = done == total
    val locked = schedule.isLocked(session)
    if (!locked && !allDone && session.date == schedule.today) {
        Button(onClick = { actions.onGuided(session) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (done == 0) "▶ Démarrer la séance" else "▶ Reprendre la séance", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!locked) {
            AssistChip(
                onClick = { actions.onSetAll(session, !allDone) },
                label = { Text(if (allDone) "Réinitialiser la séance" else "Terminer la séance") },
                leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) },
            )
        }
        if (schedule.canBurst(session)) {
            AssistChip(onClick = { actions.onBurst(session, true) }, label = { Text("🚀 Burst") })
        } else if (schedule.canUndoBurst(session)) {
            AssistChip(onClick = { actions.onBurst(session, false) }, label = { Text("Annuler le Burst") })
        }
        if (schedule.canChill(session)) {
            AssistChip(onClick = { actions.onChill(session, true) }, label = { Text("🛋 Chill") })
        } else if (schedule.canUndoChill(session)) {
            AssistChip(onClick = { actions.onChill(session, false) }, label = { Text("Annuler le Chill") })
        }
        AssistChip(
            onClick = { actions.onPostpone(session) },
            label = { Text("Décaler d'un jour") },
            leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(18.dp)) },
        )
        if (postponed > 0) {
            AssistChip(
                onClick = { actions.onUndoPostpone(session) },
                label = { Text(if (postponed > 1) "Annuler un report ($postponed)" else "Annuler le report") },
                leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun Banner(title: String, text: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.16f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun ExerciseLog.doneLabel(p: Prescription): String {
    fun format(values: List<Int>) = values.joinToString(" · ") { if (it == 0) "–" else if (p.maxIsSeconds) Measures.formatSeconds(it) else "$it" }
    // Une série par côté : chaque côté à part.
    if (sides != null) return Side.entries.mapNotNull { s -> valuesOf(s)?.takeIf { it.isNotEmpty() }?.let { "${sideMark(s)} ${format(it)}" } }.joinToString("   ")
    perSet?.let { values -> return format(values) }
    val sets = sets ?: p.sets
    val per = reps?.toString() ?: seconds?.let(Measures::formatSeconds)
    return when {
        sets != null && per != null -> "$sets × $per"
        per != null -> per
        sets != null -> "$sets séries"
        else -> ""
    }
}

private fun Prescription.maxLabel(value: Int) = if (maxIsSeconds) Measures.formatSeconds(value) else "$value reps"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseRow(
    /** Variante, exo perso, ou série max remplacée. */
    tag: String?,
    locked: Boolean,
    prescription: Prescription,
    state: ExerciseState,
    log: ExerciseLog?,
    color: Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDetail: () -> Unit,
    onWeights: (Boolean) -> Unit,
) {
    val exercise = prescription.exercise
    val done = state == ExerciseState.DONE
    val partial = state == ExerciseState.PARTIAL
    var menu by remember { mutableStateOf(false) }
    val timer = LocalWorkoutTimer.current
    val restBonus = LocalRestBonus.current
    val chrono = remember(prescription, restBonus) { TimerPlans.exercise(prescription, restBonus) }
    val rest = remember(prescription, restBonus) { TimerPlans.rest(prescription, restBonus) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onDetail, onLongClick = if (locked) null else onEdit, onLongClickLabel = "Saisir mes séries")
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // Vignette : la toucher coche l'exercice.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        done -> color
                        partial -> color.copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                )
                .toggleable(value = done, enabled = !locked, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics {
                    contentDescription = when {
                        locked -> "Séance à venir"
                        done -> "Fait, toucher pour retirer"
                        else -> "Toucher pour marquer comme fait"
                    }
                },
        ) {
            if (done) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF0F0F0F))
            } else {
                Text(
                    exerciseInitials(exercise.name),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (partial) MaterialTheme.colorScheme.onSurface else color,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val details = listOfNotNull(
                when {
                    prescription.isTest -> "Une seule série, à fond"
                    else -> prescription.summary(restBonus).ifEmpty { null }
                },
                prescription.palier?.let { "niveau ${it + 1}/${prescription.levelCount}" },
                prescription.beforeDeload?.let { "récup (au lieu de $it)" },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            tag?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = BurstColor) }
            prescription.weighted?.let { on -> WeightsChip(on, enabled = !locked && !done, onChange = onWeights) }
            if (prescription.allOut) {
                val target = prescription.target?.let(prescription::maxLabel)
                Text(
                    when {
                        prescription.isTest -> "📏 Record à battre" + (target?.let { " : $it" } ?: "")
                        prescription.isBoss -> "Dernière série à fond" + (target?.let { " · objectif $it" } ?: "")
                        prescription.sets == 1 -> "Une seule série : ton max" + (target?.let { " (au moins $it)" } ?: "")
                        else -> "Dernière série : le max possible" + (target?.let { " (au moins $it)" } ?: "")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BossColor,
                )
            }
            if (partial && log != null) {
                Text(
                    "Fait : ${log.doneLabel(prescription)} (incomplet)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (done && log?.perSet != null) {
                Text("Fait : ${log.doneLabel(prescription)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            log?.max?.let {
                Text("Max : ${prescription.maxLabel(it)}", style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options de ${exercise.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (done) "Retirer" else "Marquer comme fait") },
                    leadingIcon = { Icon(Icons.Default.Check, null) },
                    enabled = !locked,
                    onClick = { menu = false; onToggle() },
                )
                DropdownMenuItem(
                    text = { Text("Saisir mes séries") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    enabled = !locked,
                    onClick = { menu = false; onEdit() },
                )
                if (!locked && chrono != null) {
                    DropdownMenuItem(
                        text = { Text("Lancer le chrono") },
                        leadingIcon = { Text("⏱") },
                        onClick = { menu = false; timer.start(exercise.name, chrono, onFinished = if (done) null else onToggle) },
                    )
                }
                if (!locked && rest != null) {
                    DropdownMenuItem(
                        text = { Text("Repos ${Measures.formatSeconds(rest.seconds ?: 0)}") },
                        leadingIcon = { Text("⏳") },
                        onClick = { menu = false; timer.start(exercise.name, listOf(rest)) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Voir le détail") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { menu = false; onDetail() },
                )
            }
        }
    }
}
