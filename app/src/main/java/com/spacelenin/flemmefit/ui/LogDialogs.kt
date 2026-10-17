package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.Prescription

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Saisie de ce qui a réellement été fait. Si le prévu est atteint l'exercice est validé,
 * sinon il passe en « partiel » : la journée n'est pas validée et sa progression n'avance pas.
 */
@Composable
fun LogDialog(
    prescription: Prescription,
    existing: ExerciseLog?,
    onSave: (ExerciseLog, complete: Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showReps = !prescription.maxIsSeconds
    val showSeconds = prescription.seconds != null || prescription.maxIsSeconds
    var sets by rememberSaveable { mutableStateOf((existing?.sets ?: prescription.sets)?.toString().orEmpty()) }
    var reps by rememberSaveable { mutableStateOf((existing?.reps ?: prescription.reps)?.toString().orEmpty()) }
    var seconds by rememberSaveable { mutableStateOf((existing?.seconds ?: prescription.seconds)?.toString().orEmpty()) }
    var max by rememberSaveable { mutableStateOf(existing?.max?.toString().orEmpty()) }

    val log = ExerciseLog(
        sets = sets.toIntOrNull(),
        reps = reps.toIntOrNull().takeIf { showReps },
        seconds = seconds.toIntOrNull().takeIf { showSeconds },
        max = max.toIntOrNull(),
    )
    val complete = prescription.isMetBy(log)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prescription.exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prescription.summary().takeIf { it.isNotEmpty() }?.let {
                    Text("Prévu : $it", style = MaterialTheme.typography.bodyMedium)
                }
                NumberField("Séries faites", sets) { sets = it }
                if (showReps) NumberField("Répétitions par série", reps) { reps = it }
                if (showSeconds) NumberField("Durée par série (secondes)", seconds) { seconds = it }
                NumberField(
                    if (prescription.maxIsSeconds) "Max tenu, en secondes (facultatif)" else "Max sur une série à fond (facultatif)",
                    max,
                ) { max = it }
                Text(
                    if (complete) "✅ Le prévu est atteint : l'exercice sera validé."
                    else "◐ En dessous du prévu : l'exercice sera noté partiel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(log, complete) }) { Text("Enregistrer") } },
        dismissButton = {
            if (existing != null) TextButton(onClick = onClear) { Text("Effacer") }
            else TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

/** Valider un exercice de Boss : on note le max de la série à fond pour la suite. */
@Composable
fun BossMaxDialog(prescription: Prescription, onSave: (max: Int?) -> Unit, onDismiss: () -> Unit) {
    var max by rememberSaveable { mutableStateOf(prescription.target?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💀 ${prescription.exercise.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (prescription.maxIsSeconds) "Combien de temps as-tu tenu sur la série à fond ?"
                    else "Combien de répétitions sur la série à fond ?",
                )
                prescription.target?.let { Text("Objectif : $it", style = MaterialTheme.typography.bodySmall) }
                NumberField(if (prescription.maxIsSeconds) "Secondes" else "Répétitions", max) { max = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(max.toIntOrNull()) }, enabled = max.isNotEmpty()) { Text("Valider") } },
        dismissButton = { TextButton(onClick = { onSave(null) }) { Text("Valider sans noter") } },
    )
}
