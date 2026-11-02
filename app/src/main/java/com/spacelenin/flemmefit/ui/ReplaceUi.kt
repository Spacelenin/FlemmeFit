package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session

/**
 * « Changer un exo » : d'abord l'exercice de la séance à changer, puis ce qui peut le remplacer (l'exercice prévu en
 * premier), pour ce jour seulement ou à partir de ce jour.
 */
@Composable
fun ReplaceExerciseDialog(
    schedule: ProgramSchedule,
    session: Session,
    /** Index de l'exercice, sa clé, l'option choisie (null : celui prévu), gardé pour la suite ou non. */
    onApply: (Int, String, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val replaceable = schedule.replaceable(session)
    var picked by rememberSaveable(session.index) { mutableStateOf(replaceable.singleOrNull()) }
    val program = schedule.program
    val e = picked
    if (e == null) {
        PanelDialog(
            onDismissRequest = onDismiss,
            title = { Text("Changer un exo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Lequel veux-tu changer ?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    replaceable.forEach { i ->
                        val now = session.prescriptions[i]
                        Column(Modifier.fillMaxWidth().clickable { picked = i }.padding(vertical = 10.dp)) {
                            Text(now.exercise.name, style = MaterialTheme.typography.titleMedium)
                            now.replaces?.let {
                                Text("à la place de $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        )
        return
    }

    val original = session.day.exercises[e]
    val originalName = program.exerciseName(original.name, session.date)
    val options = schedule.replacementOptions(original, session.day)
    val current = schedule.currentReplacement(session, e).orEmpty()
    var choice by rememberSaveable(session.index, e) { mutableStateOf(current) }
    val noted = program.isDone(session.index, e) || program.logOf(session.index, e) != null
    fun apply(keep: Boolean) = onApply(e, original.key, choice.ifEmpty { null }, keep)
    val isToday = session.date == schedule.today
    val once = if (isToday) "Juste aujourd'hui" else "Juste ce jour"
    val onward = if (isToday) "À partir de maintenant" else "À partir de ce jour"

    PanelDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changer : $originalName") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceRow(selected = choice.isEmpty(), title = "$originalName · prévu", subtitle = null) { choice = "" }
                options.forEach { o ->
                    val followsName = schedule.exerciseName(o.follows)?.let { program.exerciseName(it, session.date) }
                    ChoiceRow(selected = choice == o.id, title = o.name, subtitle = o.dosage(followsName)) { choice = o.id }
                }
                Text(
                    (if (noted) "Ce que tu as déjà noté sur cet exo aujourd'hui sera effacé. " else "") +
                        "« $onward » le garde aux séances suivantes, jusqu'à ce que tu le changes à nouveau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { if (replaceable.size > 1) picked = null else onDismiss() }) {
                Text(if (replaceable.size > 1) "Retour" else "Annuler")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { apply(keep = false) }) { Text(once) }
                TextButton(onClick = { apply(keep = true) }) { Text(onward) }
            }
        },
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
