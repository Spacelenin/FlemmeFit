package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.Badge
import java.util.Locale

private fun Int.grouped() = String.format(Locale.FRENCH, "%,d", this)

/** Badges de gros paliers : la médaille atteinte et le prochain palier. */
fun LazyListScope.statsBadgeItems(badges: List<Badge>, color: Color) {
    if (badges.isEmpty()) return
    item(key = "badges") {
        Column {
            Text(
                "Badges 🏅",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    badges.forEach { BadgeRow(it, color) }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BadgeRow(b: Badge, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(b.medal, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                b.reached?.let { "${it.grouped()} ${b.unit}" } ?: b.family,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            val next = b.next
            if (next != null) {
                Text(
                    "${b.value.grouped()} / ${next.grouped()} pour le prochain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { (b.value.toFloat() / next).coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            } else {
                Text("Tout en haut. Respect.", style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}
