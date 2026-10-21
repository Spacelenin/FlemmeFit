package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spacelenin.flemmefit.model.DAYS_PER_WEEK
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Plan
import com.spacelenin.flemmefit.model.StoredProgram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
/**
 * Confirmation d'import : nouveau programme, ou mise à jour de la routine d'un programme existant
 * (présélectionné s'il porte le même nom) en gardant son suivi.
 */
fun ImportConfirmDialog(
    plan: Plan,
    programs: List<StoredProgram>,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    onUpdate: (programId: String) -> Unit,
) {
    var startEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val start = LocalDate.ofEpochDay(startEpoch)
    // null = nouveau programme.
    var targetId by rememberSaveable {
        mutableStateOf(programs.firstOrNull { it.plan.name.equals(plan.name, ignoreCase = true) }?.id)
    }
    val target = programs.firstOrNull { it.id == targetId }

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
                if (programs.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Ajouter comme…", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow("Nouveau programme", targetId == null) { targetId = null }
                    programs.forEach { p ->
                        ChoiceRow("Mise à jour de « ${p.plan.name} »", targetId == p.id) { targetId = p.id }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (target != null) {
                    val lost = remember(target, plan) { target.planUpdateLoss(plan) }
                    Text(
                        "La routine de « ${target.plan.name} » est remplacée. Tu gardes ta date de début " +
                            "(${target.start.shortLabel()}), tes cases cochées, tes saisies et tes tests de départ.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (lost > 0) {
                        Text(
                            "⚠️ $lost case(s) cochée(s) ou saisie(s) portent sur des exercices retirés de la routine : elles seront perdues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text("Date de début (jour 1)", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { picking = true }) { Text(start.longLabel()) }
                    Text(
                        end?.let { "Fin prévue : ${it.shortLabel()} (hors reports)" } ?: "Sans date de fin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (target == null && plan.tests.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "📏 ${plan.tests.size} tests de départ (${plan.tests.joinToString { it.name }}) : " +
                            "tu noteras tes résultats juste après l'import.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let { onUpdate(it.id) } ?: onConfirm(start) }) {
                Text(if (target != null) "Mettre à jour" else "Importer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (picking) {
        DateSelectDialog(start, onSelect = { startEpoch = it.toEpochDay() }, onDismiss = { picking = false })
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
    }
}

private fun Map<String, String>.parsedResults(): Map<String, Int> =
    mapNotNull { (id, v) -> v.toIntOrNull()?.let { id to it } }.toMap()

/**
 * Saisie des résultats des tests, en plein écran : avec un champ par test, une simple boîte de dialogue
 * passait sous le clavier. Le contenu se décale au-dessus du clavier et « Suivant » passe au champ d'après.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TestsDialog(program: StoredProgram, current: Map<String, Int>, onSave: (Map<String, Int>) -> Unit, onDismiss: () -> Unit) {
    var results by remember { mutableStateOf(program.tests.mapValues { it.value.toString() }) }
    val tests = program.plan.tests
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Tests de départ") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fermer") } },
                    actions = { TextButton(onClick = { onSave(results.parsedResults()) }) { Text("Enregistrer") } },
                )
            },
        ) { padding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    "Fais chaque test une fois, bien échauffé et à fond, puis note le résultat : il choisit ton palier de départ. " +
                        "Laissé vide : palier le plus facile. Les tests refaits en cours de route prennent ensuite le relais.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                tests.forEachIndexed { i, test ->
                    // Le clavier s'ouvre après la prise de focus : on attend qu'il ait réduit l'écran avant de défiler.
                    val bringIntoView = remember { BringIntoViewRequester() }
                    OutlinedTextField(
                        value = results[test.id].orEmpty(),
                        onValueChange = { v -> results = results + (test.id to v.filter(Char::isDigit).take(4)) },
                        label = { Text(test.name) },
                        suffix = { Text(if (test.isSeconds) "s" else "reps") },
                        supportingText = if (test.description != null) {
                            { Text(test.description) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (i == tests.lastIndex) ImeAction.Done else ImeAction.Next,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoView)
                            .onFocusEvent { focus ->
                                if (focus.isFocused) {
                                    scope.launch {
                                        delay(350)
                                        bringIntoView.bringIntoView()
                                    }
                                }
                            },
                    )
                }
                val retested = tests.filter { t -> current[t.id] != null && current[t.id] != program.tests[t.id] }
                if (retested.isNotEmpty()) {
                    Text("Derniers résultats", style = MaterialTheme.typography.labelLarge)
                    retested.forEach { t ->
                        val v = current.getValue(t.id)
                        Text("${t.name} : ${if (t.isSeconds) Measures.formatSeconds(v) else "$v reps"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
