package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.Counter
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.StoredProgram
import java.time.LocalDate
import java.util.Locale

/** Compteurs de tous les programmes. */
fun countersOf(programs: List<StoredProgram>, today: LocalDate): List<Counter> =
    Counter.merge(programs.map { Stats.of(ProgramSchedule(it, today)).counters })

/** « 2 345 pompes », « 10h05 de boxe ». */
fun Counter.valueLabel(): String =
    if (isTime) formatTotalSeconds(seconds) else String.format(Locale.FRENCH, "%,d", reps)

private fun Counter.milestoneLabel(): String =
    if (isTime) "${formatTotalSeconds(seconds)} de ${name.lowercase()}" else "${valueLabel()} ${name.lowercase()}"

/** Une ligne de compteur dans les Réglages. */
@Composable
fun CounterRow(counter: Counter) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(counter.name, style = MaterialTheme.typography.bodyLarge)
            val details = listOfNotNull(
                counter.equivalence()?.let { "≈ $it" },
                counter.weightedSeconds.takeIf { it > 0 }?.let { "dont ${formatTotalSeconds(it)} avec poids" },
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(counter.valueLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** Le jour où un compteur passe le cap : les compteurs se dévoilent. */
@Composable
fun CountersUnlockedDialog(milestone: Counter, onSee: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔓 Compteurs débloqués") },
        text = {
            Text(
                "${milestone.milestoneLabel().replaceFirstChar { it.titlecase(Locale.FRENCH) }} au total, rien que ça.\n\n" +
                    "Tous tes totaux (pompes, abdos, temps de boxe, de gainage, de Beat Saber…) et ce qu'ils représentent " +
                    "t'attendent dans Réglages › Compteurs.",
            )
        },
        confirmButton = { TextButton(onClick = onSee) { Text("Voir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Plus tard") } },
    )
}
