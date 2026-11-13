package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ExerciseInfo
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.TestPoint
import com.spacelenin.flemmefit.model.VariantProgress
import com.spacelenin.flemmefit.model.Variants
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme
import java.time.format.TextStyle
import java.util.Locale

/** Titre d'une partie de fiche : petit libellé coloré, ou petit bandeau rayé sur le thème fit. */
@Composable
internal fun InfoHeader(text: String, color: Color) {
    if (!LocalFitTheme.current) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 4.dp))
        return
    }
    val room = LocalFitRoom.current
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(color = Color.White, shadow = null),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(room, lerp(room, Color.White, 0.3f))))
            .fitStripes(Color.White, alpha = 0.16f)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun Line(text: String, strong: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
}

/** Petites barres des dernières séances (meilleure série de chacune). */
@Composable
private fun HistoryBars(points: List<TestPoint>, color: Color, format: (Int) -> String, eccentric: Set<java.time.LocalDate> = emptySet()) {
    if (points.size < 2) return
    val top = points.maxOf { it.value }.coerceAtLeast(1)
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .semantics { contentDescription = points.joinToString { "${it.date.format(DayMonth)} : ${format(it.value)}" + if (it.date in eccentric) " en excentrique" else "" } },
        ) {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (points.size - 1)) / points.size
            points.forEachIndexed { i, p ->
                val h = (p.value.toFloat() / top) * size.height
                drawRoundRect(
                    when {
                        p.date in eccentric -> color.copy(alpha = 0.2f)
                        i == points.lastIndex -> color
                        else -> color.copy(alpha = 0.45f)
                    },
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Text(
            "${points.first().date.format(DayMonth)} → ${points.last().date.format(DayMonth)} · max ${format(top)}" +
                if (points.any { it.date in eccentric }) " · pâle : excentrique ou forme réduite" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor(), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            content = content,
        )
    }
}

/** La fiche d'un exo de la routine : consigne, muscles, niveau, dose, jours, historique, côtés, remplaçants. */
@Composable
fun ExerciseInfoSheet(info: ExerciseInfo, color: Color, onDismiss: () -> Unit) {
    InfoSheet(onDismiss) {
        Text(info.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        info.palier?.let { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                levelStars(p, info.levelCount)?.let { FitStars(it) }
                Text("niveau ${p + 1}/${info.levelCount}", style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
        info.description?.let { InstructionBubble(it) }

        if (info.muscles.isNotEmpty() || info.assists.isNotEmpty()) {
            InfoHeader("Muscles", color)
            Line("💪 " + info.muscles.joinToString(", ") + if (info.assists.isNotEmpty()) " · aide : ${info.assists.joinToString(", ")}" else "")
        }

        if (info.palier != null && info.testName != null) {
            InfoHeader("Niveau", color)
            fun t(v: Int) = if (info.testSeconds) Measures.formatSeconds(v) else "$v"
            Line("📏 ${info.testName} : " + (info.testResult?.let(::t) ?: "pas encore fait"))
            Line(info.nextLevelAt?.let { "Palier suivant à ${t(it)} au test" } ?: "Palier le plus haut atteint 🏆", strong = true)
        }

        InfoHeader("Dose", color)
        info.dose?.let { Line(it + (info.nextDate?.let { d -> " · prochaine le ${d.format(DayMonth)}" } ?: ""), strong = true) }
        info.progression?.let { Line("📈 $it") }
        info.adaptation?.let { a -> Line("🎯 ${a.headline} · ${a.detail}") }

        if (info.weekdays.isNotEmpty()) {
            InfoHeader("Quand", color)
            Line("📅 " + info.weekdays.joinToString(", ") { it.getDisplayName(TextStyle.FULL, Locale.FRENCH) })
        }

        InfoHeader("Historique", color)
        val st = info.stats
        if (st == null || st.times == 0) {
            Line("Pas encore fait.")
        } else {
            val best = (if (info.seconds) st.bestSeconds else st.bestReps)?.let(info::format)
            Line(
                listOfNotNull(
                    "${st.times} fois",
                    if (st.totalReps > 0) "${st.totalReps} reps" else null,
                    if (st.totalSeconds > 0) Measures.formatSeconds(st.totalSeconds) else null,
                    best?.let { "record $it" },
                ).joinToString(" · "),
            )
            st.lastDate?.let { Line("Dernière fois le ${it.format(DayMonth)}" + (st.lastSummary?.let { s -> " : $s" } ?: "")) }
            HistoryBars(info.history, color, info::format, info.eccentricDates)
        }

        info.balance?.let { b ->
            InfoHeader("Gauche / droite", color)
            Line("↔ " + b.status { it.format(DayMonth) })
        }

        if (info.alternatives.isNotEmpty() || info.variants.isNotEmpty()) {
            InfoHeader("À la place", color)
            info.alternatives.forEach { Line("🔄 $it") }
            info.variants.forEach { v ->
                Line("🎲 ${v.variant.name}" + if (v.locked) " · 🔒 " + v.requirements.filter { !it.met }.joinToString(", ") { it.label } else "")
            }
            if (info.alternatives.isNotEmpty()) {
                Text("Pour en changer : « 🔄 Changer un exo » sur la page du jour.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** La fiche d'une variante : consigne, prérequis, dosage, courbe des max. */
@Composable
fun VariantInfoSheet(v: VariantProgress, color: Color, onDismiss: () -> Unit) {
    val variant = v.variant
    InfoSheet(onDismiss) {
        Text(variant.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text("Variante · ${v.familyName}", style = MaterialTheme.typography.labelLarge, color = color)
        variant.description?.let { InstructionBubble(it) }
        variant.easy?.let { Line("Version facile : $it") }
        variant.warning?.let { Line("⚠️ $it") }

        if (v.requirements.isNotEmpty()) {
            InfoHeader("Prérequis", color)
            v.requirements.forEach { Line((if (it.met) "✓ " else "🔒 ") + it.label, strong = !it.met) }
            variant.check?.let { Line("Puis : « $it »") }
        }

        InfoHeader("Dosage", color)
        when {
            variant.isFixed -> Line(
                "📈 ${v.format(variant.start ?: 1)} au départ, +${v.format(variant.step)} à chaque séance réussie" +
                    (variant.cap?.let { ", jusqu'à ${v.format(it)}" } ?: ""),
            )
            v.ratio != null -> Line("⚖️ ${v.ratioLabel(v.ratio)}")
            else -> Line("⚖️ Taux mesuré au premier passage")
        }
        variant.from?.let { Line("Vraie version à partir de $it au test ; avant, la version facile.") }

        InfoHeader("Historique", color)
        if (v.points.isEmpty()) Line("Pas encore fait.")
        else {
            Line("${v.points.size} fois · dernier max ${v.format(v.points.last().value)}" + (v.startRatio?.let { " · taux de départ ${Variants.decimal(it)}" } ?: ""))
            HistoryBars(v.points.takeLast(12), color, v::format)
        }
    }
}
