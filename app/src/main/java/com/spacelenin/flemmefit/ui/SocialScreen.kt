package com.spacelenin.flemmefit.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.Exploit
import com.spacelenin.flemmefit.model.FriendCard
import com.spacelenin.flemmefit.model.Social
import com.spacelenin.flemmefit.social.SocialSync

private val Medals = listOf("🥇", "🥈", "🥉")

private fun ago(updatedAt: Long, now: Long): String {
    val minutes = (now - updatedAt) / 60_000
    return when {
        minutes < 2 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 48 * 60 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (24 * 60)} j"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(social: SocialSync, repository: ProgramRepository, onBack: (() -> Unit)? = null) {
    val pseudo by social.pseudo.collectAsStateWithLifecycle()
    val friends by social.friends.collectAsStateWithLifecycle()
    val status by social.status.collectAsStateWithLifecycle()
    val state by repository.state.collectAsStateWithLifecycle()
    val today = rememberToday()
    var settings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pseudo) { if (pseudo != null) social.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Les potes") },
                navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } } },
                actions = {
                    if (pseudo != null) {
                        IconButton(onClick = social::refresh) { Icon(Icons.Default.Refresh, "Actualiser") }
                        TextButton(onClick = { settings = true }) { Text("Réglages") }
                    }
                },
            )
        },
    ) { padding ->
        if (pseudo == null) {
            PseudoSetup(onDone = social::setPseudo, modifier = Modifier.padding(padding))
        } else {
            val me = remember(state, today, pseudo) { social.myCard(today) }
            val ranking = Social.ranking(friends + listOfNotNull(me), today)
            val now = System.currentTimeMillis()

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "Points : Boss 💀 = ${FriendCard.BOSS_POINTS}, exploit 🏆 = ${FriendCard.EXPLOIT_POINTS}, Burst 🚀 = +${FriendCard.BURST_POINTS}, séance = 1. " +
                                "Exploit = un max au moins ${Exploit.FACTOR}× plus grand que la série prévue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when (val s = status) {
                            SocialSync.Status.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                            is SocialSync.Status.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            SocialSync.Status.Idle -> if (friends.isEmpty()) {
                                Text(
                                    "Personne d'autre pour l'instant. Tes potes apparaîtront dès qu'ils auront choisi un pseudo.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(ranking, key = { _, c -> c.id }) { i, card ->
                    FriendRow(
                        rank = i,
                        last = i == ranking.lastIndex && ranking.size > 2,
                        card = card,
                        isMe = card.id == social.myId,
                        today = today,
                        now = now,
                    )
                }
            }
        }
    }

    if (settings) {
        SocialSettingsDialog(
            pseudo = pseudo.orEmpty(),
            topic = social.topic.collectAsStateWithLifecycle().value,
            onSave = { p, t ->
                social.setPseudo(p)
                social.setTopic(t)
                settings = false
            },
            onDismiss = { settings = false },
        )
    }
}

@Composable
private fun FriendRow(rank: Int, last: Boolean, card: FriendCard, isMe: Boolean, today: java.time.LocalDate, now: Long) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Text(
                        card.pseudo.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.pseudo + if (isMe) " (toi)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        Medals.getOrNull(rank) ?: if (last) "🐌 dernier" else "${rank + 1}e",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${card.score} pts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
            val todayText = when {
                !card.isToday(today) -> "Pas de nouvelles aujourd'hui 👀"
                card.todayTotal == 0 -> "Repos aujourd'hui 😴"
                card.todayDone == card.todayTotal -> "Séance du jour faite ✅ (${card.todayTitle ?: "séance"})"
                else -> "Aujourd'hui : ${card.todayDone}/${card.todayTotal} exos (${card.todayTitle ?: "séance"})"
            }
            Text(todayText, style = MaterialTheme.typography.bodyMedium)
            if (card.isToday(today) && card.todayTotal > 0) {
                LinearProgressIndicator(progress = { card.todayDone.toFloat() / card.todayTotal }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                "🔥 ${card.streak} · 💀 ${card.bossDefeated} · 🏆 ${card.exploits} · 🚀 ${card.bursts} · ${card.sessionsDone} séances",
                style = MaterialTheme.typography.bodyMedium,
            )
            card.lastExploit?.let { Text("Dernier exploit : $it", style = MaterialTheme.typography.bodySmall) }
            if (last && !isMe) Text("Lanterne rouge. On ne juge pas. Enfin si, un peu.", style = MaterialTheme.typography.bodySmall)
            if (!isMe) {
                Text(
                    "Mis à jour ${ago(card.updatedAt, now)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PseudoSetup(onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choisis ton pseudo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tes potes verront ce pseudo, ton avancée du jour, ta série, tes Boss vaincus et tes exploits. " +
                "Rien d'autre ne quitte le téléphone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(FriendCard.PSEUDO_MAX) },
            label = { Text("Pseudo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("C'est parti") }
    }
}

@Composable
private fun SocialSettingsDialog(pseudo: String, topic: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var p by rememberSaveable { mutableStateOf(pseudo) }
    var t by rememberSaveable { mutableStateOf(topic) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages des potes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = p, onValueChange = { p = it.take(FriendCard.PSEUDO_MAX) }, label = { Text("Pseudo") }, singleLine = true)
                OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Canal") }, singleLine = true)
                Text(
                    "Tous ceux qui ont le même canal se voient. Le canal par défaut est commun à toutes les apps FlemmeFit ; " +
                        "changez-le tous ensemble pour un groupe à part (6 caractères minimum).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(p, t) }, enabled = p.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Au démarrage, tant qu'aucun pseudo n'est choisi : apparaître chez les potes sans passer par l'onglet. */
@Composable
fun PseudoPromptDialog(onSave: (String) -> Unit, onLater: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Ton pseudo pour les potes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tes potes verront ce pseudo, ta séance du jour, tes 🔥 d'affilée, tes Boss vaincus et tes exploits. " +
                        "Rien d'autre ne quitte le téléphone. Modifiable dans Réglages.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(FriendCard.PSEUDO_MAX) },
                    label = { Text("Pseudo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("C'est parti") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Plus tard") } },
    )
}
