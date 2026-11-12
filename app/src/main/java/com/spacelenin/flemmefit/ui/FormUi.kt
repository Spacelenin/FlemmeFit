package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Les formes du jour proposées : en forme, fatigué, malade (en % de la dose). */
val FormChoices = listOf(100, 70, 55)

private fun formEmoji(percent: Int) = when {
    percent >= 100 -> "💪"
    percent > 55 -> "😮‍💨"
    else -> "🤒"
}

/** « 😮‍💨 Fatigué · 70 % », « 🤒 Malade · 55 % ». */
fun formLabel(percent: Int): String = when {
    percent >= 100 -> "💪 En forme"
    percent > 55 -> "${formEmoji(percent)} Fatigué · $percent %"
    else -> "${formEmoji(percent)} Malade · $percent %"
}

/** Ce que fait la forme du jour, en une phrase. */
fun formExplanation(percent: Int, bossMoved: Boolean): String =
    "Séries à $percent %. Ça compte comme une séance faite, ton réglage ne bouge pas." +
        if (bossMoved) " Le Boss du jour est reporté de quelques jours." else ""

/** Le choix de la forme du jour : trois pastilles, avant de commencer la séance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormRow(current: Int?, enabled: Boolean, onPick: (Int?) -> Unit, modifier: Modifier = Modifier, center: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text("Forme du jour", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormChoices.forEach { p ->
                val selected = (current ?: 100) == p
                FitChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onPick(if (p >= 100) null else p) },
                    label = { Text("${formEmoji(p)} $p %") },
                )
            }
        }
    }
}
