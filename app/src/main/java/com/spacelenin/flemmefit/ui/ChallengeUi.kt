package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.challenge.ChallengeManager
import com.spacelenin.flemmefit.model.Challenge
import com.spacelenin.flemmefit.model.Challenges
import kotlinx.coroutines.delay

/**
 * La page du défi surprise : accepter ou refuser, puis le temps qui reste et « Réussi ».
 * S'affiche d'elle-même quand un défi attend une réponse, ou depuis la notification ([requested]).
 */
@Composable
fun ChallengeHost(manager: ChallengeManager, requested: Boolean, onDismissRequest: () -> Unit) {
    val all by manager.all.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    // Fermée à la main : la page attend que le défi change d'état (ou la notification) pour revenir.
    var closed by remember { mutableStateOf<Pair<String, Challenge.Status>?>(null) }
    var seen by remember { mutableStateOf<String?>(null) }
    val challenge = all.lastOrNull()?.takeIf { it.open(now) }
    LaunchedEffect(requested, challenge == null) {
        if (requested) {
            closed = null
            if (challenge == null) onDismissRequest()
        }
    }
    if (challenge == null) return
    val status = challenge.status(now)
    // En cours, la page ne revient d'elle-même que si elle était déjà ouverte : elle ne gêne pas la séance.
    val hidden = closed == challenge.id to status || (status == Challenge.Status.RUNNING && !requested && seen != challenge.id)
    if (hidden) return
    LaunchedEffect(challenge.id) { seen = challenge.id }
    val close = {
        closed = challenge.id to challenge.status(System.currentTimeMillis())
        seen = null
        onDismissRequest()
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (status == Challenge.Status.ASK_RESULT) "🎯 Temps écoulé !" else "🎯 Défi surprise !") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(challenge.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                when (status) {
                    Challenge.Status.OFFERED -> {
                        Text("En 1 h, en autant de fois que tu veux. Réussi : +${Challenges.POINTS} points chez les potes. Refuser ne coûte rien.")
                        Text(
                            "Pour accepter : encore ${clock(((challenge.offeredAt + Challenges.ANSWER_MS - now) / 1000).toInt())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Challenge.Status.RUNNING -> {
                        val left = ((challenge.acceptedAt!! + Challenges.DURATION_MS - now) / 1000).toInt()
                        Text("Il te reste", style = MaterialTheme.typography.bodyMedium)
                        Text(clock(left), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                    }
                    else -> Text("Alors, c'est fait ? Sur l'honneur.")
                }
            }
        },
        confirmButton = {
            when (status) {
                Challenge.Status.OFFERED -> TextButton(onClick = manager::accept) { Text("Accepter") }
                else -> TextButton(onClick = { manager.finish(won = true); close() }) { Text("Réussi ✅") }
            }
        },
        dismissButton = {
            when (status) {
                Challenge.Status.OFFERED -> TextButton(onClick = { manager.refuse(); close() }) { Text("Refuser") }
                Challenge.Status.RUNNING -> TextButton(onClick = close) { Text("Fermer") }
                else -> TextButton(onClick = { manager.finish(won = false); close() }) { Text("Raté") }
            }
        },
    )
}
