package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.AdaptProfile

/** Ce que change le passage de [from] à [to], dit simplement. */
fun profileChangeText(from: AdaptProfile, to: AdaptProfile): String {
    val faster = to.ordinal > from.ordinal
    return "À partir d'aujourd'hui, tes exos s'adapteront " + (if (faster) "plus vite" else "plus doucement") +
        " à ce que tu fais et ressens. Ton niveau actuel ne change pas, ni tes séances passées."
}

/** Choix du rythme d'adaptation d'un programme, avec ce que le changement fera avant de valider. */
@Composable
fun ProfileDialog(current: AdaptProfile, programName: String, onSelect: (AdaptProfile) -> Unit, onDismiss: () -> Unit) {
    var picked by rememberSaveable { mutableStateOf(current.id) }
    val context = LocalContext.current
    val choice = AdaptProfile.of(picked)
    PanelDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rythme d'adaptation") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "À quelle vitesse « $programName » ajuste tes exos à ce que tu fais. Si tu galères, ça baisse toujours aussi vite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AdaptProfile.entries.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().clickable { picked = p.id }.padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = p == choice, onClick = { picked = p.id })
                        Column(Modifier.padding(top = 12.dp)) {
                            Text("${p.emoji} ${p.label}" + if (p == current) " · actuel" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(p.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (choice != current) {
                    Text(profileChangeText(current, choice), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (choice != current) {
                    onSelect(choice)
                    Toast.makeText(context, "${choice.emoji} Rythme ${choice.label} à partir d'aujourd'hui", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }) {
                Text(if (choice != current) "Passer en ${choice.label}" else "Fermer")
            }
        },
        dismissButton = { if (choice != current) TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Proposition d'un autre rythme, d'après les dernières semaines. Rien ne change sans accord. */
@Composable
fun ProfileSuggestionCard(
    current: AdaptProfile,
    suggestion: AdaptProfile,
    color: Color,
    onAccept: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = fitCardColor(color.copy(alpha = 0.14f))),
        modifier = modifier.fillMaxWidth().fitBorder(color, shape = RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ProfileSuggestionContent(current, suggestion, onAccept, onLater)
        }
    }
}

/** Le texte et les boutons de la proposition (dans une carte, ou une bulle sur le thème fit). */
@Composable
fun ProfileSuggestionContent(current: AdaptProfile, suggestion: AdaptProfile, onAccept: () -> Unit, onLater: () -> Unit) {
    val faster = suggestion.ordinal > current.ordinal
    Text(
        if (faster) "${suggestion.emoji} Ça a l'air facile pour toi" else "${suggestion.emoji} Ça a l'air dur en ce moment",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        (if (faster) "Ces 3 dernières semaines, tu fais souvent plus que prévu. "
        else "Ces 3 dernières semaines, beaucoup de séries sont sous le prévu. ") +
            "Passer en ${suggestion.label} ? " + profileChangeText(current, suggestion),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(onClick = onAccept) { Text("Passer en ${suggestion.label}") }
        TextButton(onClick = onLater) { Text("Plus tard") }
    }
}
