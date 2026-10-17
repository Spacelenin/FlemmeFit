package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.DAYS_PER_WEEK
import com.spacelenin.flemmefit.model.Plan
import java.time.LocalDate

@Composable
fun PasteDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coller un programme") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("{ \"format\": \"flemmefit\", \"version\": 1, … }") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text("Lire") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ImportErrorDialog(errors: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import impossible") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                errors.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Le format attendu est décrit dans FORMAT.md du dépôt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun ImportConfirmDialog(plan: Plan, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var startEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val start = LocalDate.ofEpochDay(startEpoch)

    val sessionsPerCycle = plan.weeks.sumOf { it.days.size }
    val end = if (plan.isForever) null else start.plusDays(plan.weeks.size * plan.repeat * DAYS_PER_WEEK - 1L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plan.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                plan.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                val cycle = when {
                    plan.isForever -> ", répétées sans fin"
                    plan.repeat > 1 -> " × ${plan.repeat} répétitions"
                    else -> ""
                }
                Text("${plan.weeks.size} semaine(s)$cycle")
                Text(
                    if (plan.isForever) "$sessionsPerCycle séances par cycle"
                    else "${sessionsPerCycle * plan.repeat} séances au total",
                )
                val extras = listOfNotNull(
                    "jours Boss 💀".takeIf { plan.boss },
                    "décharge 1 semaine sur ${plan.effectiveDeloadEvery}".takeIf { plan.effectiveDeloadEvery > 0 },
                )
                if (extras.isNotEmpty()) Text("Avec " + extras.joinToString(" et "))
                Spacer(Modifier.height(16.dp))
                Text("Date de début (jour 1)", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { picking = true }) { Text(start.longLabel()) }
                Text(
                    end?.let { "Fin prévue : ${it.shortLabel()} (hors reports)" } ?: "Sans date de fin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(start) }) { Text("Importer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (picking) {
        DateSelectDialog(start, onSelect = { startEpoch = it.toEpochDay() }, onDismiss = { picking = false })
    }
}
