package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.DAYS_PER_WEEK
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Plan
import com.spacelenin.flemmefit.model.PlanTest
import com.spacelenin.flemmefit.model.StoredProgram
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
fun ImportConfirmDialog(plan: Plan, onDismiss: () -> Unit, onConfirm: (LocalDate, tests: Map<String, Int>) -> Unit) {
    var startEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var results by remember { mutableStateOf(emptyMap<String, String>()) }
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
                if (plan.tests.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Tests de départ", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Fais chaque test une fois, à fond, et note le résultat : il choisit ton palier de départ. " +
                            "Laissé vide : palier le plus facile, à remplir plus tard (menu ⋮ du programme).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    TestResultFields(plan.tests, results) { id, v -> results = results + (id to v) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(start, results.parsedResults()) }) { Text("Importer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (picking) {
        DateSelectDialog(start, onSelect = { startEpoch = it.toEpochDay() }, onDismiss = { picking = false })
    }
}

private fun Map<String, String>.parsedResults(): Map<String, Int> =
    mapNotNull { (id, v) -> v.toIntOrNull()?.let { id to it } }.toMap()

/** Un champ par test, avec sa consigne. Valeurs en texte pour pouvoir rester vides. */
@Composable
private fun TestResultFields(tests: List<PlanTest>, values: Map<String, String>, onChange: (id: String, value: String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tests.forEach { test ->
            OutlinedTextField(
                value = values[test.id].orEmpty(),
                onValueChange = { v -> onChange(test.id, v.filter(Char::isDigit).take(4)) },
                label = { Text(test.name) },
                suffix = { Text(if (test.isSeconds) "s" else "reps") },
                supportingText = if (test.description != null) {
                    { Text(test.description) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Corriger les résultats des tests de départ après l'import. */
@Composable
fun TestsDialog(program: StoredProgram, current: Map<String, Int>, onSave: (Map<String, Int>) -> Unit, onDismiss: () -> Unit) {
    var results by remember { mutableStateOf(program.tests.mapValues { it.value.toString() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tests de départ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Les paliers suivent le dernier résultat : celui-ci, puis ceux des tests refaits en cours de route.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TestResultFields(program.plan.tests, results) { id, v -> results = results + (id to v) }
                val retested = program.plan.tests.filter { t -> current[t.id] != null && current[t.id] != program.tests[t.id] }
                if (retested.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Derniers résultats", style = MaterialTheme.typography.labelLarge)
                    retested.forEach { t ->
                        val v = current.getValue(t.id)
                        Text("${t.name} : ${if (t.isSeconds) Measures.formatSeconds(v) else "$v reps"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(results.parsedResults()) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
