package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.spacelenin.flemmefit.model.DoseInfo
import com.spacelenin.flemmefit.model.Prescription
import com.spacelenin.flemmefit.model.Session
import com.spacelenin.flemmefit.model.withDose
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme

private val RowHeight = 84.dp

/** « 3 × 12 », sans le repos ni le reste. */
private fun Prescription.doseLabel(): String = summary().substringBefore(" · ").ifEmpty { "—" }

/** Pourquoi la dose est réduite, en une ligne : « 🌱 mise en route · 💪 triceps déjà travaillés ». */
fun DoseInfo.reasons(): String = listOfNotNull(
    "🌱 mise en route".takeIf { ramp < 0.995 },
    tired.takeIf { fatigue < 0.995 && it.isNotEmpty() }?.let { "💪 ${it.joinToString(", ")} déjà travaillés" },
).joinToString(" · ")

/** La dose réduite d'un exo : petite ligne, ou étiquette pâle de la couleur de la salle sur le thème fit. */
@Composable
fun DoseTag(dose: DoseInfo, modifier: Modifier = Modifier) {
    val text = "${dose.reasons()} · ${dose.percent} %"
    if (!LocalFitTheme.current) {
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = DeloadColor, modifier = modifier)
        return
    }
    val room = LocalFitRoom.current
    val shape = RoundedCornerShape(10.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = lerp(room, Color.Black, 0.3f),
        modifier = modifier
            .padding(top = 2.dp)
            .clip(shape)
            .background(lerp(room, Color.White, 0.85f))
            .border(1.5.dp, lerp(room, Color.White, 0.4f), shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * « Réorganiser » : les exos de la séance à glisser par leur poignée. La fatigue de chacun se recalcule à chaque
 * déplacement, avec la dose qui en sort. L'échauffement et les étirements restent à leur place, la variante suit son
 * exercice de base.
 */
@Composable
fun ReorderDialog(
    session: Session,
    isToday: Boolean,
    /** Les clés dans le nouvel ordre (vide : celui de la routine), gardé pour la suite ou non. */
    onApply: (keys: List<String>, keep: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val n = session.day.exercises.size
    val items = remember(session.index) {
        mutableStateListOf<Int>().apply { addAll(session.order.filter { it < n && !session.day.exercises[it].optional }) }
    }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
    fun keys() = items.map { session.day.exercises[it].key }
    val order = session.orderFor(keys())
    val doses = session.dosesFor(order)
    val fit = LocalFitTheme.current
    val room = LocalFitRoom.current

    fun move(e: Int, by: Int) {
        val pos = items.indexOf(e)
        val to = pos + by
        if (pos < 0 || to !in items.indices) return
        items.removeAt(pos)
        items.add(to, e)
    }

    PanelDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réorganiser") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Fais glisser ≡ pour changer l'ordre. Un exo qui passe après d'autres sur les mêmes muscles est réduit d'autant. " +
                        "L'échauffement et les étirements restent à leur place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.isDeload || session.isBoss) {
                    Text(
                        if (session.isBoss) "Jour Boss : rien n'est réduit, seul l'ordre change." else "Semaine allégée : rien n'est réduit, seul l'ordre change.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column {
                    items.forEachIndexed { pos, e ->
                        key(e) {
                            val isDragged = dragged == e
                            val full = session.full.getOrNull(e) ?: return@key
                            val dose = doses[e]
                            val variant = session.variantIndex?.takeIf { session.variantBaseIndex == e && it != e }?.let { session.prescriptions.getOrNull(it) }
                            val shape = RoundedCornerShape(if (fit) 22.dp else 14.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragged) offset else 0f
                                        val s = if (isDragged) 1.03f else 1f
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .fillMaxWidth()
                                    .height(RowHeight)
                                    .padding(vertical = 3.dp)
                                    .then(if (isDragged) Modifier.shadow(8.dp, shape) else Modifier)
                                    .clip(shape)
                                    .then(
                                        if (fit) Modifier.background(Brush.horizontalGradient(listOf(lerp(room, Color.White, 0.82f), Color.White))).border(2.dp, lerp(room, Color.White, if (isDragged) 0f else 0.4f), shape)
                                        else Modifier.background(if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh),
                                    )
                                    .semantics(mergeDescendants = true) {
                                        customActions = listOf(
                                            CustomAccessibilityAction("Monter") { move(e, -1); true },
                                            CustomAccessibilityAction("Descendre") { move(e, 1); true },
                                        )
                                    },
                            ) {
                                if (fit) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(start = 8.dp).size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(2.dp, room, RoundedCornerShape(8.dp)),
                                    ) { Text("${pos + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = lerp(room, Color.Black, 0.2f)) }
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                                    Text(full.exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val shown = dose?.let { full.withDose(it) } ?: full
                                    Text(
                                        if (dose?.reduced == true) "${full.doseLabel()} → ${shown.doseLabel()} · ${dose.percent} %" else "${full.doseLabel()} · dose pleine",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (dose?.reduced == true) FontWeight.Bold else FontWeight.Normal,
                                        color = if (dose?.reduced == true) DeloadColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val why = listOfNotNull(dose?.takeIf { it.reduced }?.reasons(), variant?.let { "🎲 puis ${it.exercise.name}" }).joinToString(" · ")
                                    if (why.isNotEmpty()) {
                                        Text(why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                // La poignée : c'est elle qu'on fait glisser (l'appui long sur l'exo garde son rôle ailleurs).
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .width(48.dp)
                                        .fillMaxHeight()
                                        .pointerInput(e) {
                                            detectDragGestures(
                                                onDragStart = { dragged = e; offset = 0f },
                                                onDragEnd = { dragged = null; offset = 0f },
                                                onDragCancel = { dragged = null; offset = 0f },
                                            ) { change, amount ->
                                                change.consume()
                                                offset += amount.y
                                                val at = items.indexOf(e)
                                                if (offset > rowPx / 2 && at < items.lastIndex) {
                                                    move(e, 1)
                                                    offset -= rowPx
                                                } else if (offset < -rowPx / 2 && at > 0) {
                                                    move(e, -1)
                                                    offset += rowPx
                                                }
                                            }
                                        }
                                        .semantics { contentDescription = "Déplacer ${full.exercise.name}" },
                                ) {
                                    Text("≡", style = MaterialTheme.typography.headlineSmall.copy(shadow = null), color = if (fit) lerp(room, Color.Black, 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    items.clear()
                    items.addAll(session.orderFor(null).filter { it < n && !session.day.exercises[it].optional })
                }) { Text("Ordre de la routine") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onApply(keys(), false) }) { Text(if (isToday) "Juste aujourd'hui" else "Juste ce jour") }
                TextButton(onClick = { onApply(keys(), true) }) { Text(if (isToday) "À partir de maintenant" else "À partir de ce jour") }
            }
        },
    )
}
