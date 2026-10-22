package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.BURST_STEPS
import com.spacelenin.flemmefit.model.ExerciseLog
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Plan
import com.spacelenin.flemmefit.model.Prescription

/** Valeur d'une série, en reps ou en durée. */
private fun Prescription.setLabel(v: Int) = if (maxIsSeconds) Measures.formatSeconds(v) else v.toString()

/** Détail d'un exercice : chiffres du jour en grand, consigne, échelle des paliers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    /** Séance à venir : on regarde, on ne coche pas. */
    locked: Boolean,
    prescription: Prescription,
    plan: Plan,
    testResults: Map<String, Int>,
    color: Color,
    done: Boolean,
    onLog: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val exercise = prescription.exercise
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            prescription.palier?.let { p ->
                val test = exercise.test?.let(plan::test)
                val result = exercise.test?.let(testResults::get)
                Text(
                    "Niveau ${p + 1} sur ${prescription.levelCount}" +
                        if (test != null && result != null) " · test : ${if (test.isSeconds) Measures.formatSeconds(result) else "$result"}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }
            Text(exercise.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)

            val facts = listOfNotNull(
                prescription.sets?.let { it.toString() to "séries" },
                prescription.repsLabel?.let { it to "reps par série" },
                prescription.durationLabel?.let { it to if (prescription.sets != null) "par série" else "durée" },
                prescription.loadLabel?.let { it to "charge" },
                exercise.rest?.let { it to "de repos" },
            )
            if (facts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    facts.forEach { (value, label) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(12.dp),
                        ) {
                            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            prescription.beforeDeload?.let {
                Text("🌿 Semaine récup : au lieu de $it", style = MaterialTheme.typography.bodyMedium, color = DeloadColor)
            }
            if (prescription.allOut) {
                val target = prescription.target?.let(prescription::setLabel)
                Text(
                    when {
                        prescription.isTest -> "📏 Une seule série, à fond" + (target?.let { ". Record à battre : $it." } ?: ".")
                        prescription.isBoss -> "💀 Dernière série à fond" + (target?.let { ", objectif $it." } ?: ".")
                        else -> "Dernière série : le max possible" + (target?.let { ", au moins $it." } ?: ".")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = BossColor,
                )
            }
            exercise.description?.let {
                Text("Comment le faire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            val current = prescription.palier
            if (current != null && exercise.levels.isNotEmpty()) {
                Text("Où tu en es", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val unit = exercise.test?.let(plan::test)
                exercise.levels.forEachIndexed { i, level ->
                    val next = exercise.levels.getOrNull(i + 1)?.from
                    val fmt = { v: Int -> if (unit?.isSeconds == true) Measures.formatSeconds(v) else v.toString() }
                    val range = if (next != null) "${fmt(level.from)} – ${fmt(next - 1)}" else "${fmt(level.from)} et +"
                    val at = exercise.atLevel(i)
                    val mine = i == current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mine) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(range, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            listOfNotNull(at.sets?.let { "$it ×" }, at.reps ?: at.duration).joinToString(" "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Text(if (mine) "toi" else "", style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.width(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (locked) {
                Text("Séance à venir : elle se fera le jour même.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onLog, modifier = Modifier.weight(1f)) { Text("Saisir mes séries") }
                    Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text(if (done) "Retirer" else "Marquer comme fait") }
                }
            }
        }
    }
}

/**
 * Saisie série par série : on touche une série pour la régler. Si chaque série atteint le prévu l'exercice est validé,
 * sinon il passe en partiel. La dernière série d'un exercice à fond compte comme max.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetsLogSheet(
    prescription: Prescription,
    existing: ExerciseLog?,
    onSave: (ExerciseLog, complete: Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val planned = prescription.perSetPlanned ?: 0
    val count = prescription.sets ?: 1
    val step = if (prescription.maxIsSeconds) 5 else 1
    var values by rememberSaveable {
        mutableStateOf(
            existing?.perSet?.takeIf { it.size == count }
                ?: List(count) { i ->
                    val per = (if (prescription.maxIsSeconds) existing?.seconds else existing?.reps) ?: planned
                    val doneSets = existing?.sets ?: count
                    when {
                        i >= doneSets -> 0
                        i == count - 1 && existing?.max != null -> existing?.max ?: per
                        else -> per
                    }
                },
        )
    }
    var selected by rememberSaveable { mutableStateOf(values.indexOfFirst { it < planned }.takeIf { it >= 0 } ?: (count - 1)) }
    val log = ExerciseLog.ofSets(values, prescription.allOut)
    val complete = prescription.isMetBy(log)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Column {
                Text("Ce que tu as vraiment fait", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${prescription.exercise.name} · prévu ${prescription.summary()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("TOUCHE UNE SÉRIE POUR LA RÉGLER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEachIndexed { i, v ->
                    val isSel = i == selected
                    val short = v in 1 until planned
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .size(width = 56.dp, height = 60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .then(if (isSel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { selected = i }
                            .semantics { contentDescription = "Série ${i + 1} : ${if (v == 0) "pas faite" else prescription.setLabel(v)}" }
                            .padding(top = 8.dp),
                    ) {
                        Text(
                            if (v == 0) "–" else prescription.setLabel(v),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("S${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                fun change(delta: Int) {
                    values = values.toMutableList().also { it[selected] = (it[selected] + delta).coerceIn(0, 9999) }
                }
                FilledTonalIconButton(onClick = { change(-step) }, modifier = Modifier.size(56.dp)) {
                    Text("−", style = MaterialTheme.typography.headlineSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
                    val v = values[selected]
                    Text(if (v == 0) "–" else prescription.setLabel(v), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "série ${selected + 1}" + if (prescription.allOut && selected == count - 1) " · à fond" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { change(step) }, modifier = Modifier.size(56.dp)) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
            val total = values.sum()
            val totalLabel = if (prescription.maxIsSeconds) Measures.formatSeconds(total) else "$total reps"
            val plannedTotal = planned * count
            val plannedLabel = if (prescription.maxIsSeconds) Measures.formatSeconds(plannedTotal) else "$plannedTotal"
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (complete) DeloadColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
            ) {
                Column {
                    Text(
                        if (complete) "Fait ✓ · $totalLabel" else "Incomplet · $totalLabel sur $plannedLabel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (complete) "Chaque série atteint le prévu." else "L'exercice reste incomplet, la progression attend.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existing != null) TextButton(onClick = onClear) { Text("Effacer") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(log, complete) }) { Text("Enregistrer") }
            }
        }
    }
}

/** Initiales d'un exercice pour sa vignette (« Pompes lentes » → « PL »). */
fun exerciseInitials(name: String): String =
    name.split(' ', '-', '\'').filter { it.isNotBlank() && it.first().isLetter() && it.length > 2 }
        .take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { name.take(1).uppercase() }

/** Petite pastille ronde de couleur. */
@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

/** Confirmation avant de passer la séance du jour en Burst. */
@Composable
fun BurstDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🚀 Burst aujourd'hui ?") },
        text = {
            Text(
                "La séance du jour est renforcée : +$BURST_STEPS étapes de progression sur les exercices qui progressent, " +
                    "une série de plus sur les autres. Une fois par semaine. Annulable tant que rien n'est fait.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("C'est parti") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Pas aujourd'hui") } },
    )
}
