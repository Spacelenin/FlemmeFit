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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
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
import com.spacelenin.flemmefit.model.BURST_STEPS
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.ExerciseState
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Prescription
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session
import java.time.LocalDate

/** Rouge « Boss », indépendant du thème pour rester reconnaissable. */
val BossColor = Color(0xFFE05252)
val DeloadColor = Color(0xFF8FE0A5)
val BurstColor = Color(0xFFFFC94D)

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
                            schedule.isInProgram(date) -> "Repos 😴"
                            date.isBefore(schedule.startDate) -> "Pas encore commencé"
                            else -> "Programme terminé 🎉"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        when {
                            schedule.isInProgram(date) -> "Rien de prévu ce jour-là. Profites-en."
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
    if (session == null) return
    session.prescriptions.forEachIndexed { i, prescription ->
        item(key = "ex-${session.index}-$i") {
            ExerciseRow(
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
            )
        }
    }
    item(key = "day-footer") {
        Text(
            if (schedule.isLocked(session)) "Séance à venir : elle se fera le jour même."
            else if (session.isBoss) "Terminer un exercice Boss demande ton max. Tout le Boss fait = Boss vaincu."
            else "Touche la vignette pour marquer comme fait, l'exercice pour son détail. Appui long : saisir tes séries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionHeader(schedule: ProgramSchedule, session: Session, color: Color, actions: DayActions) {
    val done = schedule.doneCount(session)
    val total = session.exerciseCount
    val ratio = if (total == 0) 0f else done.toFloat() / total
    val postponed = schedule.program.postponeCount(session.index)
    val accent = if (session.isBoss) BossColor else color

    if (session.isBurst) {
        Banner("🚀 Burst", "Séance renforcée : +$BURST_STEPS étapes de progression, ou une série de plus. Une fois par semaine.", BurstColor)
        Spacer(Modifier.height(12.dp))
    } else if (session.isBoss) {
        Banner("💀 Jour Boss", "Chiffres de la semaine prochaine, et dernière série à fond. Bats ton dernier max !", BossColor)
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
    Text(
        "${session.date.longLabel().uppercase()} · ${session.position(schedule.program.plan.repeat).uppercase()}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(session.title(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val allDone = done == total
        val locked = schedule.isLocked(session)
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
    perSet?.let { values -> return values.joinToString(" · ") { if (it == 0) "–" else if (p.maxIsSeconds) Measures.formatSeconds(it) else "$it" } }
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
    locked: Boolean,
    prescription: Prescription,
    state: ExerciseState,
    log: ExerciseLog?,
    color: Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDetail: () -> Unit,
) {
    val exercise = prescription.exercise
    val done = state == ExerciseState.DONE
    val partial = state == ExerciseState.PARTIAL
    var menu by remember { mutableStateOf(false) }

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
                    else -> prescription.summary().ifEmpty { null }
                },
                prescription.palier?.let { "niveau ${it + 1}/${prescription.levelCount}" },
                prescription.beforeDeload?.let { "récup (au lieu de $it)" },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (prescription.allOut) {
                val target = prescription.target?.let(prescription::maxLabel)
                Text(
                    when {
                        prescription.isTest -> "📏 Record à battre" + (target?.let { " : $it" } ?: "")
                        prescription.isBoss -> "Dernière série à fond" + (target?.let { " · objectif $it" } ?: "")
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
                DropdownMenuItem(
                    text = { Text("Voir le détail") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { menu = false; onDetail() },
                )
            }
        }
    }
}
