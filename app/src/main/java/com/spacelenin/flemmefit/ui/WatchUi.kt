package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.watch.WatchLink
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Hour = DateTimeFormatter.ofPattern("HH:mm")

/** « 14:02 » aujourd'hui, « 13/11 14:02 » sinon. */
private fun moment(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return if (t.toLocalDate() == LocalDate.now()) t.format(Hour) else t.format(DateTimeFormatter.ofPattern("d/MM HH:mm"))
}

/**
 * Encart « Ta montre » des réglages : il n'apparaît qu'une fois une montre vue. Connexion, batterie, version (et sa
 * mise à jour envoyée en Bluetooth), synchro de la séance du jour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchSection(link: WatchLink) {
    val status by link.status.collectAsStateWithLifecycle()
    val connected by link.connected.collectAsStateWithLifecycle()
    val sent by link.lastSent.collectAsStateWithLifecycle()
    val received by link.lastReceived.collectAsStateWithLifecycle()
    val latest by link.latest.collectAsStateWithLifecycle()
    val push by link.push.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { link.refresh() }
    val s = status ?: return

    Card(
        colors = CardDefaults.cardColors(containerColor = fitCardColor(MaterialTheme.colorScheme.surfaceVariant)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).fitBorder(LocalFitRoom.current),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⌚ Ta montre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val (label, color) = if (connected) "Connectée" to Color(0xFF3C8E17) else "Hors de portée" to Color(0xFF7D8790)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(color).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Info("Batterie", (s.battery?.let { "$it %" } ?: "?") + " · vue à ${moment(s.at)}")
            Info(
                "Version",
                "${s.version}" + when {
                    latest != null && latest!! > s.version -> " · la ${latest} est disponible"
                    latest != null -> " · à jour"
                    else -> ""
                },
            )
            Info(
                "Synchro",
                listOfNotNull(
                    sent.takeIf { it > 0 }?.let { "séance envoyée à ${moment(it)}" },
                    received.takeIf { it > 0 }?.let { "dernier retour à ${moment(it)}" },
                ).joinToString(" · ").ifEmpty { "pas encore de séance envoyée" },
            )
            if (s.pending > 0) Info("En attente", "${s.pending} envoi(s) sur la montre, ils partiront quand elle sera à portée")
            if (s.inSession) Info("En ce moment", "séance en cours sur la montre")

            when (val p = push) {
                is WatchLink.Push.Running -> Info("Mise à jour", "version ${p.version} : ${p.step}…")
                is WatchLink.Push.Done -> Info("Mise à jour", "version ${p.version} envoyée, la montre l'installe")
                is WatchLink.Push.Failed -> Info("Mise à jour", "❌ ${p.message}")
                WatchLink.Push.Idle -> Unit
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (latest != null && latest!! > s.version && push !is WatchLink.Push.Running) {
                    PillButton(onClick = { link.pushUpdate() }, small = true) { Text("Mettre à jour la montre") }
                }
                SoftButton(onClick = { link.publish() }, small = true) { Text("Renvoyer la séance") }
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
