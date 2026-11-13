package com.spacelenin.flemmefit.ui

import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.SharedDefi
import com.spacelenin.flemmefit.model.SharedExercise
import com.spacelenin.flemmefit.model.StoredProgram
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.Exploit
import com.spacelenin.flemmefit.model.AdaptProfile
import androidx.compose.material3.HorizontalDivider
import com.spacelenin.flemmefit.model.FriendCard
import com.spacelenin.flemmefit.model.Social
import com.spacelenin.flemmefit.social.SocialSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * Choisir un pseudo : vérifié auprès du canal avant d'être pris. Déjà porté par quelqu'un d'autre, on prévient ;
 * « C'est moi » le garde quand même (ancien téléphone, réinstallation).
 */
@Composable
fun rememberPseudoPicker(social: SocialSync, onAccepted: () -> Unit = {}): (String) -> Unit {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var taken by remember { mutableStateOf<Pair<String, FriendCard>?>(null) }
    taken?.let { (name, owner) ->
        PanelDialog(
            onDismissRequest = { taken = null },
            title = { Text("Pseudo déjà pris") },
            text = {
                Text(
                    "« ${owner.pseudo.trim()} » est déjà utilisé par quelqu'un d'autre (vu ${ago(owner.updatedAt, System.currentTimeMillis())}). " +
                        "Choisis-en un autre. Si c'est toi sur un ancien téléphone ou avant une réinstallation, tu peux le garder.",
                )
            },
            confirmButton = { TextButton(onClick = { taken = null }) { Text("En choisir un autre") } },
            dismissButton = {
                TextButton(onClick = {
                    social.setPseudo(name)
                    taken = null
                    onAccepted()
                }) { Text("C'est moi") }
            },
        )
    }
    return { name ->
        if (!checking && name.isNotBlank()) {
            scope.launch {
                checking = true
                val owner = withContext(Dispatchers.IO) { social.pseudoOwner(name) }
                checking = false
                if (owner == null) {
                    social.setPseudo(name)
                    onAccepted()
                } else {
                    taken = name to owner
                }
            }
        }
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
    var detailFor by rememberSaveable { mutableStateOf<String?>(null) }
    val shareDetails by social.shareDetails.collectAsStateWithLifecycle()
    val pickPseudo = rememberPseudoPicker(social)

    LaunchedEffect(pseudo) { if (pseudo != null) social.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(colors = fitBarColors(), 
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
            PseudoSetup(onDone = pickPseudo, modifier = Modifier.padding(padding))
        } else {
            val me = remember(state, today, pseudo, shareDetails) { social.myCard(today) }
            val ranking = Social.ranking(friends + listOfNotNull(me), today)
            val now = System.currentTimeMillis()
            ranking.firstOrNull { it.id == detailFor }?.let { card ->
                FriendDayDialog(card, isMe = card.id == social.myId, today = today, onDismiss = { detailFor = null })
            }

            // Une seule colonne, centrée et pas trop large une fois déplié.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        if (com.spacelenin.flemmefit.ui.theme.LocalFitTheme.current) FitBand("Classement", LocalFitRoom.current, Modifier.padding(bottom = 6.dp))
                        Text(
                            "Points : Boss 💀 = ${FriendCard.BOSS_POINTS}, exploit 🏆 = ${FriendCard.EXPLOIT_POINTS}, Burst 🚀 = +${FriendCard.BURST_POINTS}, séance = 1. " +
                                "Exploit = un max bien au-dessus de la série prévue : ×2 au début, jusqu'à ×1,5 pour les grosses séries.",
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
                        onClick = { detailFor = card.id },
                    )
                }
            }
            }
        }
    }

    if (settings) {
        SocialSettingsDialog(
            pseudo = pseudo.orEmpty(),
            topic = social.topic.collectAsStateWithLifecycle().value,
            onSave = { p, t ->
                social.setTopic(t)
                // Même pseudo (à la casse près) : rien à vérifier.
                if (p.trim().lowercase() != pseudo?.trim()?.lowercase()) pickPseudo(p) else social.setPseudo(p)
                settings = false
            },
            onDismiss = { settings = false },
        )
    }
}

@Composable
private fun FriendRow(rank: Int, last: Boolean, card: FriendCard, isMe: Boolean, today: java.time.LocalDate, now: Long, onClick: () -> Unit) {
    if (com.spacelenin.flemmefit.ui.theme.LocalFitTheme.current) {
        val todayText = when {
            !card.isToday(today) -> "👀 Pas de nouvelles aujourd'hui"
            card.todayTotal == 0 -> "😴 Repos aujourd'hui"
            card.todayDone == card.todayTotal -> "✅ Séance du jour faite"
            else -> "Aujourd'hui : ${card.todayDone}/${card.todayTotal} exos"
        }
        PodiumRow(rank, last, card, isMe, todayText, updated = if (isMe) null else "mis à jour ${ago(card.updatedAt, now)}", onClick = onClick)
        return
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = fitCardColor(
                if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                highlight = isMe,
            ),
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().fitBorder(LocalFitRoom.current, if (isMe) 3.5.dp else 2.dp),
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
                FitProgress(
                    progress = { card.todayDone.toFloat() / card.todayTotal },
                    color = if (com.spacelenin.flemmefit.ui.theme.LocalFitTheme.current) LocalFitRoom.current else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "🔥 ${card.streak} · 💀 ${card.bossDefeated} · 🏆 ${card.exploits} · 🚀 ${card.bursts}" +
                    (if (card.defis > 0) " · 🎯 ${card.defis}" else "") + " · ${card.sessionsDone} séances",
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
            "Tes potes verront ce pseudo, ton avancée du jour, ta série, tes Boss vaincus et tes exploits, " +
                "et le détail de ta séance du jour (exos, ressenti) : ça se coupe dans les Réglages. Rien d'autre ne quitte le téléphone.",
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
        PillButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("C'est parti") }
    }
}

@Composable
private fun SocialSettingsDialog(pseudo: String, topic: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var p by rememberSaveable { mutableStateOf(pseudo) }
    var t by rememberSaveable { mutableStateOf(topic) }
    PanelDialog(
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
    PanelDialog(
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

/** La séance du jour d'un pote : ses exos (prévu, fait, exploits) et son ressenti, s'il les partage. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendDayDialog(card: FriendCard, isMe: Boolean, today: java.time.LocalDate, onDismiss: () -> Unit) {
    val exercises = card.todayExercises.takeIf { card.isToday(today) }
    val fit = com.spacelenin.flemmefit.ui.theme.LocalFitTheme.current
    PanelDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.pseudo + if (isMe) " (toi)" else "") },
        text = {
            // Toute la largeur : le numéro de build se cale toujours à droite, même sur une carte courte.
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Le récap du pote : sa semaine, son rythme, son meilleur badge, ses compteurs.
                FriendRecap(card)
                // Jour Boss et défis du jour : en tête, même sans le détail des exos.
                if (card.isToday(today)) {
                    card.todayBoss?.let { b ->
                        Text(
                            "💀 Jour Boss · " + when (b) {
                                FriendCard.BOSS_WON -> "vaincu ✅"
                                // Anciennes versions : « raté » envoyé avant la fin de la journée.
                                FriendCard.BOSS_LOST -> "en cours"
                                FriendCard.BOSS_MOVED -> "reporté"
                                else -> "en cours"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = BossColor,
                        )
                    }
                    card.todayForm?.let { f ->
                        Text(formLabel(f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    card.todayDefis?.forEach { d ->
                        Text(
                            "🎯 ${d.label} · " + when (d.result) {
                                SharedDefi.WON -> "✅ réussi"
                                SharedDefi.LOST -> "❌ raté"
                                SharedDefi.REFUSED -> "refusé"
                                else -> "en cours"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                when {
                    !card.isToday(today) -> Text("Pas de nouvelles aujourd'hui 👀")
                    card.todayTotal == 0 -> Text("Repos aujourd'hui 😴")
                    exercises == null -> Text(
                        if (isMe) "Tu ne partages pas le détail de ta séance (Réglages › Les potes)."
                        else "Pas de détail : soit il ne le partage pas, soit son appli n'est pas à jour.",
                    )
                    else -> {
                        Text(
                            (card.todayTitle ?: "Séance du jour") + when (card.todayMode) {
                                FriendCard.MODE_BURST -> "  🚀 Burst"
                                FriendCard.MODE_CHILL -> "  🛋 Chill"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        card.todayMinutes?.let { Text("⏱ ~$it min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        card.todayFeeling?.let { f ->
                            val emoji = StoredProgram.FEELINGS.getOrNull(f)
                            val label = StoredProgram.FEELING_LABELS.getOrNull(f)
                            if (emoji != null && label != null) Text("Ressenti : $emoji $label", style = MaterialTheme.typography.bodyMedium)
                        }
                        exercises.forEach { e ->
                            Column {
                                Text(
                                    (if (e.finished) "✅ " else "⏳ ") + e.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val stars = if (fit) e.level?.let { l -> e.levelCount?.let { levelStars(l - 1, it) } } else null
                                // Chaque info à part : la ligne se replie entre deux infos, jamais au milieu d'une.
                                val extras = listOfNotNull(
                                    "🏆 exploit".takeIf { e.exploit },
                                    "🏅 record".takeIf { e.record == true },
                                    "🔄 remplacé".takeIf { e.replaced },
                                    e.target?.let { "💀 objectif " + if (e.targetSeconds == true) Measures.formatSeconds(it) else "$it" },
                                    when (e.version) {
                                        SharedExercise.VERSION_ECCENTRIC -> "↘ excentrique"
                                        SharedExercise.VERSION_EASY -> "facile"
                                        SharedExercise.VERSION_TIRED -> "🥵 crevé"
                                        else -> null
                                    },
                                    if (!fit) e.level?.let { l -> e.levelCount?.let { "niv. $l/$it" } } else null,
                                    e.feeling?.let { StoredProgram.FEELINGS.getOrNull(it) },
                                    e.dosePercent?.let { "dose $it %" },
                                    "🏋️".takeIf { e.weighted == true },
                                )
                                if (stars != null || extras.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        stars?.let { FitStars(it) }
                                        extras.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                                    }
                                }
                                Text(
                                    listOfNotNull(e.planned?.let { "Prévu $it" }, e.done?.let { "fait : $it" } ?: "pas encore fait".takeIf { !e.finished })
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                // Sa version de l'appli, en tout petit : de quoi voir qui est à jour.
                if (card.build > 0) {
                    Text(
                        "build ${card.build}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** « ●●●○○ » : une pastille par séance prévue de la semaine, pleine si elle est faite. */
fun weekDots(done: Int, planned: Int): String = "●".repeat(done.coerceIn(0, planned)) + "○".repeat((planned - done).coerceAtLeast(0))

/** En tête de la carte complète d'un pote : sa semaine, son rythme, son meilleur badge et ses compteurs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendRecap(card: FriendCard) {
    val items = listOfNotNull(
        card.weekNumber?.let { n ->
            "Semaine $n" + (if (card.weekDeload == true) " · récup" else "") +
                (card.weekPlanned?.takeIf { it > 0 }?.let { " · " + weekDots(card.weekDone ?: 0, it) } ?: "")
        },
        card.rhythm?.let { AdaptProfile.of(it).let { r -> "${r.emoji} ${r.label}" } },
        card.bestBadge?.let { "Meilleur badge : $it" },
    )
    val counters = card.counters.orEmpty()
    if (items.isEmpty() && counters.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
        if (counters.isNotEmpty()) {
            Text("💪 " + counters.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}
