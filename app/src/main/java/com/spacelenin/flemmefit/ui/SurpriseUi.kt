package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * L'écran surprise : une fausse offre payante, aux couleurs de l'appli. Les « champs » ne sont que des cases dessinées,
 * on ne peut rien y écrire ; le moindre appui ferme tout, sans un mot, et rien n'est gardé ni envoyé.
 */
@Composable
fun SurpriseDialog(onClose: () -> Unit) {
    val anywhere = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
    PanelDialog(
        onDismissRequest = onClose,
        title = { Text("FlemmeFit Premium 💎") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = anywhere) {
                Text("Ta période d'essai gratuite est terminée.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Pour garder tes programmes, ton suivi et tes potes, passe à Premium : 4,99 € par mois, sans engagement.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FakeField("Numéro de carte", "1234 5678 9012 3456", onClose)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FakeField("Expiration", "MM/AA", onClose, Modifier.weight(1f))
                    FakeField("CVC", "123", onClose, Modifier.weight(1f))
                }
                FakeField("Titulaire", "Prénom Nom", onClose)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Plus tard") } },
        confirmButton = { PillButton(onClick = onClose) { Text("Payer 4,99 €") } },
    )
}

/** Une case qui ressemble à un champ : rien ne s'y tape, l'appui ferme l'écran. */
@Composable
private fun FakeField(label: String, placeholder: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(48.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp),
        ) {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}
