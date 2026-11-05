package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.Variant

/** Variante débloquée qui demande une confirmation ([Variant.check]) avant le premier passage. */
@Composable
fun VariantCheckCard(variant: Variant, familyName: String, color: Color, onConfirm: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VariantCheckContent(variant, familyName, onConfirm, onLater)
        }
    }
}

/** Le texte et les boutons (dans une carte, ou une bulle sur le thème fit). */
@Composable
fun VariantCheckContent(variant: Variant, familyName: String, onConfirm: () -> Unit, onLater: () -> Unit) {
    Text("🔓 Débloqué : ${variant.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Tu as tous les prérequis. Avant de commencer, un dernier test :", style = MaterialTheme.typography.bodyMedium)
    variant.check?.let { Text("« $it »", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
    variant.warning?.let { Text("⚠️ $it", style = MaterialTheme.typography.bodySmall) }
    Text(
        "Si c'est bon, il arrive dès la prochaine séance de ${familyName.lowercase()}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(onClick = onConfirm) { Text("C'est bon, j'y arrive") }
        TextButton(onClick = onLater) { Text("Pas encore") }
    }
}
