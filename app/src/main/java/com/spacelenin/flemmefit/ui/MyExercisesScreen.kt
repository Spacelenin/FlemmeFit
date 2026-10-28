package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.CustomExercise
import com.spacelenin.flemmefit.model.ExerciseAdaptation
import com.spacelenin.flemmefit.model.Plan
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.SideBalance
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.model.VariantProgress
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Les variantes de la routine et les exos perso. Un exo perso ajouté à une famille rejoint le tirage des jours
 * variante ; dans « Autre », il s'ajoute tout seul à deux séances par semaine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyExercisesScreen(programs: List<StoredProgram>, repository: ProgramRepository) {
    val today = rememberToday()
    // Les exos perso vivent dans le programme qui a des familles (la routine).
    val program = programs.firstOrNull { it.plan.families.isNotEmpty() }
    val schedule = remember(program, today) { program?.let { ProgramSchedule(it, today) } }
    val progress = remember(schedule) { schedule?.variantProgress().orEmpty() }
    val adapted = remember(schedule) { schedule?.adaptations().orEmpty().filter { it.samples > 0 } }
    val balances = remember(schedule) { schedule?.balances().orEmpty() }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mes exos") }) },
        floatingActionButton = {
            if (program != null) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Ajouter un exo") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (program == null) {
                Text(
                    "Ta routine ne définit pas de familles d'exercices : impossible d'y glisser des variantes.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
                return@Box
            }
            val mine = program.custom.filter { !it.deleted }
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                item(key = "intro") {
                    Text(
                        "Certains jours tirés au sort, une variante remplace une partie des séries de son exo de base, " +
                            "dernière série au max. Ce max recale ton taux, et donc la dose des fois suivantes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (adapted.isNotEmpty()) {
                    item(key = "adapt-title") { SectionTitle("Adapté à toi 🎯") }
                    item(key = "adapt-intro") {
                        Text(
                            "Chaque exo se règle sur ce que tu notes en séance (séries faites, temps en plus) et sur ton ressenti. " +
                                "Ça bouge vite les premières séances, puis ça se cale. Les tests de niveau vérifient que ça ne s'emballe pas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(adapted, key = { "adapt-${it.name}" }) { a -> AdaptationRow(a, balances.firstOrNull { it.name == a.name }) }
                }
                item(key = "mine-title") { SectionTitle("Mes exos ⭐") }
                if (mine.isEmpty()) {
                    item(key = "mine-empty") {
                        Text(
                            "Rien pour l'instant. Ajoute un exo : il entre dans le programme dès demain.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(mine, key = { "mine-${it.id}" }) { c ->
                    CustomCard(
                        c,
                        familyName = program.plan.family(c.family)?.name ?: "Autre",
                        progress = progress.firstOrNull { it.variant.id == c.id },
                        onPause = { repository.updateCustom(program.id, c.id) { it.stopped(today) } },
                        onResume = { repository.updateCustom(program.id, c.id) { it.resumed(today) } },
                        onDelete = { deleting = c.id },
                    )
                }
                item(key = "routine-title") { SectionTitle("Variantes de la routine 🎲") }
                items(progress.filter { !it.custom }, key = { "routine-${it.variant.id}" }) { v -> VariantRow(v) }
                item(key = "end") { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (adding && program != null) {
        AddExerciseDialog(
            plan = program.plan,
            onSave = { c ->
                repository.addCustom(program.id, c)
                adding = false
            },
            onDismiss = { adding = false },
            create = { name, family, seconds, ratio, start, description ->
                CustomExercise.create(UUID.randomUUID().toString(), name, family, seconds, ratio, start, description, today)
            },
        )
    }
    deleting?.let { id ->
        val p = program ?: return@let
        val c = p.custom.firstOrNull { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer ${c.name} ?") },
            text = { Text("Il ne sera plus proposé à partir de demain. Ce que tu as déjà fait reste dans tes stats.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.updateCustom(p.id, id) { it.stopped(today).copy(deleted = true) }
                    deleting = null
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

private fun VariantProgress.status(): String = listOfNotNull(
    if (points.isEmpty()) "pas encore fait" else "max ${format(points.last().value)}",
    ratio?.let { "taux : ${ratioLabel(it)}" } ?: "taux mesuré au premier passage",
).joinToString(" · ")

@Composable
private fun AdaptationRow(a: ExerciseAdaptation, balance: SideBalance?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                a.headline,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (a.crans > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(a.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Une série par côté : l'écart gauche / droite et le rattrapage en cours.
        balance?.let { b ->
            Text(
                "↔ " + b.status { it.format(DateTimeFormatter.ofPattern("d/MM")) },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = b.weak?.let(::sideColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VariantRow(v: VariantProgress) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(v.variant.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${v.familyName} · ${v.status()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomCard(
    c: CustomExercise,
    familyName: String,
    progress: VariantProgress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    familyName,
                    if (c.seconds) "en durée" else "en reps",
                    if (c.isActive) null else "en pause",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (c.isOther) "Ajouté à 2 séances par semaine, en fin de séance, avec sa propre progression."
                else progress?.status() ?: "",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.isActive) TextButton(onClick = onPause) { Text("Mettre en pause") }
                else TextButton(onClick = onResume) { Text("Reprendre") }
                TextButton(onClick = onDelete) { Text("Supprimer") }
            }
        }
    }
}

/** Nombre décimal tapé à la française ou non : « 3,5 », « 3.5 ». */
private fun String.toDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseDialog(
    plan: Plan,
    onSave: (CustomExercise) -> Unit,
    onDismiss: () -> Unit,
    create: (name: String, family: String, seconds: Boolean, ratio: Double?, start: Int?, description: String?) -> CustomExercise,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var family by rememberSaveable { mutableStateOf(plan.families.first().id) }
    var seconds by rememberSaveable { mutableStateOf(false) }
    var ratio by rememberSaveable { mutableStateOf("") }
    var unknown by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    val other = family == CustomExercise.OTHER
    val test = plan.test(plan.family(family)?.test)
    val testUnit = test?.let { if (it.isSeconds) "s de ${it.name.lowercase()}" else it.name.lowercase() } ?: "reps de base"
    val per = if (seconds && test?.isSeconds != true) 30 else 1
    val ratioValue = ratio.toDecimal()
    val startValue = start.trim().toIntOrNull()?.takeIf { it > 0 }
    val valid = name.isNotBlank() && if (other) startValue != null else unknown || ratioValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un exo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CustomExercise.MAX_NAME) },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Famille", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (plan.families.map { it.id to it.name } + (CustomExercise.OTHER to "Autre")).forEach { (id, label) ->
                        FilterChip(selected = family == id, onClick = { family = id }, label = { Text(label) })
                    }
                }
                Text("Mesure", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !seconds, onClick = { seconds = false }, label = { Text("Répétitions") })
                    FilterChip(selected = seconds, onClick = { seconds = true }, label = { Text("Durée") })
                }
                if (other) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it.filter(Char::isDigit).take(4) },
                        label = { Text(if (seconds) "Départ par série (secondes)" else "Départ par série (reps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "3 séries, 2 fois par semaine, placé par l'appli. +1 rep ou +5 s toutes les 2 fois, jusqu'au triple.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Difficulté", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (seconds) "$per s ≈ " else "1 rep ≈ ")
                        OutlinedTextField(
                            value = ratio,
                            onValueChange = { ratio = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(5) },
                            enabled = !unknown,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Text(" $testUnit", modifier = Modifier.padding(start = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = unknown, onCheckedChange = { unknown = it })
                        Text("Je sais pas : le premier passage sera juste ton max")
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = { Text("Comment le faire (facultatif)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val r = if (other || unknown) null else ratioValue?.div(per)
                    onSave(create(name, family, seconds, r, if (other) startValue else null, description))
                },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
