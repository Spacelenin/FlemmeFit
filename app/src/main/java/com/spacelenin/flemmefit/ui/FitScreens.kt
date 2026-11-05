package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacelenin.flemmefit.model.Badge
import com.spacelenin.flemmefit.model.FriendCard
import com.spacelenin.flemmefit.ui.theme.FitPalette
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme
import java.util.Locale

private val Ink = Color(0xFF3D3F42)

/** Puce de choix : la puce habituelle, ou une pastille nacrée (pleine de la couleur de la salle une fois choisie). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    if (!LocalFitTheme.current) {
        FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon)
        return
    }
    val room = LocalFitRoom.current
    val shape = CircleShape
    val fill = if (selected) {
        Brush.verticalGradient(listOf(lerp(room, Color.White, 0.35f), room))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EEF2)))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(fill, shape)
            .drawBehind {
                // Reflet sur la moitié haute.
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) 0.45f else 0.9f), Color.White.copy(alpha = 0f)), endY = size.height * 0.5f),
                    topLeft = Offset(size.height * 0.3f, 1.5.dp.toPx()),
                    size = Size((size.width - size.height * 0.6f).coerceAtLeast(0f), size.height * 0.42f),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            .border(2.5.dp, if (!enabled) FitPalette.Line else if (selected) lerp(room, Color.Black, 0.2f) else lerp(room, Color.White, 0.3f), shape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        leadingIcon?.invoke()
        androidx.compose.material3.ProvideTextStyle(
            MaterialTheme.typography.labelLarge.copy(
                color = if (selected) Color.White else FitPalette.Text.copy(alpha = if (enabled) 1f else 0.4f),
                fontWeight = FontWeight.Bold,
            ),
        ) { label() }
    }
}

/** Barre d'avancement : la barre fine habituelle, ou une jauge épaisse, arrondie et rayée. */
@Composable
fun FitProgress(progress: () -> Float, color: Color, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    if (!LocalFitTheme.current) {
        LinearProgressIndicator(progress = progress, color = color, modifier = modifier)
        return
    }
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Color.White)
            .background(Brush.verticalGradient(listOf(Color(0xFFE3E8EC), Color.White)))
            .border(2.dp, lerp(color, Color.White, 0.35f), shape)
            .drawBehind {
                val p = progress().coerceIn(0f, 1f)
                if (p <= 0f) return@drawBehind
                val inset = 2.dp.toPx()
                val w = (size.width - inset * 2) * p
                val h = size.height - inset * 2
                drawRoundRect(
                    Brush.verticalGradient(listOf(lerp(color, Color.White, 0.4f), color, lerp(color, Color.Black, 0.12f))),
                    topLeft = Offset(inset, inset), size = Size(w, h), cornerRadius = CornerRadius(h / 2),
                )
                // Rayures fines et reflet sur la partie remplie.
                var y = inset + 1.5.dp.toPx()
                while (y < inset + h) {
                    drawLine(Color.White.copy(alpha = 0.16f), Offset(inset, y), Offset(inset + w, y), strokeWidth = 1f)
                    y += 3.dp.toPx()
                }
                drawRoundRect(
                    Color.White.copy(alpha = 0.35f), topLeft = Offset(inset + h / 3, inset + 1f),
                    size = Size((w - h * 0.66f).coerceAtLeast(0f), h * 0.35f), cornerRadius = CornerRadius(h / 4),
                )
            },
    )
}

// --- Badges : des médailles ---

private val MedalColors = listOf(
    Color(0xFFC0773A), // bronze
    Color(0xFF9AA5AF), // argent
    Color(0xFFE0B000), // or
    Color(0xFF2BB3D9), // bleu
    Color(0xFF8A5CD6), // violet
    Color(0xFFE0262B), // rouge
)

private fun Int.grouped() = String.format(Locale.FRENCH, "%,d", this)

/** Grille de médailles : pastille ronde de la couleur du palier (grise sans palier), nom, jauge vers le suivant. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FitMedalGrid(badges: List<Badge>, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        badges.forEach { MedalTile(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun MedalTile(b: Badge, modifier: Modifier) {
    val won = b.tier >= 0
    val color = MedalColors.getOrElse(b.tier) { Color(0xFFC3CAD0) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(max = 160.dp)
            .clip(shape)
            .background(Color.White)
            .linedPaper()
            .border(3.dp, if (won) Ink else FitPalette.Line, shape)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.2f))))
                .border(3.dp, if (won) lerp(color, Color.Black, 0.3f) else Color(0xFFAAB2B9), CircleShape),
        ) {
            Text(if (won) b.medal else "·", fontSize = 24.sp, color = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(b.family, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            b.reached?.let { "${it.grouped()} ${b.unit}" } ?: "pas encore",
            style = MaterialTheme.typography.labelSmall,
            color = FitPalette.Text.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        val next = b.next
        Spacer(Modifier.height(6.dp))
        if (next != null) {
            // Jauge à crans vers le prochain palier.
            val p = (b.value.toFloat() / next).coerceIn(0f, 1f)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().height(8.dp)) {
                repeat(5) { k ->
                    val on = p >= (k + 1) / 5f
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (on) MedalColors.getOrElse(b.tier + 1) { color } else Color(0xFFE3E8EC)),
                    )
                }
            }
            Text("${b.value.grouped()} / ${next.grouped()}", style = MaterialTheme.typography.labelSmall, color = FitPalette.Text.copy(alpha = 0.7f))
        } else {
            Text("Tout en haut", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// --- Potes : le podium ---

private val RankColors = listOf(Color(0xFFD9A400), Color(0xFF8E99A3), Color(0xFFC0773A))

/** « 1er », « 2e »… */
fun rankLabel(rank: Int): Pair<String, String> = "${rank + 1}" to if (rank == 0) "er" else "e"

/**
 * Carte du classement façon podium : le rang en gros (or, argent, bronze, puis gris), le premier sur fond jaune
 * avec une couronne, les points soulignés de bleu. Le dernier exploit passe sous la carte.
 */
@Composable
fun PodiumRow(rank: Int, last: Boolean, card: FriendCard, isMe: Boolean, todayText: String, updated: String?, onClick: () -> Unit) {
    val first = rank == 0 && card.score > 0
    val shape = RoundedCornerShape(18.dp)
    val room = LocalFitRoom.current
    Column {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = if (first) 14.dp else 0.dp)
                    .fillMaxWidth()
                    .then(if (first) Modifier.drawBehind {
                        // Halo jaune autour de la carte du premier.
                        val g = 4.dp.toPx()
                        drawRoundRect(Color(0x66FFE56A), topLeft = Offset(-g, -g), size = Size(size.width + 2 * g, size.height + 2 * g), cornerRadius = CornerRadius(22.dp.toPx()))
                    } else Modifier)
                    .clip(shape)
                    .background(if (first) Color(0xFFFFF1A8) else Color.White)
                    .linedPaper()
                    .border(if (first) 3.5.dp else 3.dp, if (isMe) room else Ink, shape)
                    .clickable(onClick = onClick)
                    .padding(start = 6.dp, end = 14.dp, top = if (first) 16.dp else 10.dp, bottom = if (first) 16.dp else 10.dp),
            ) {
                val (n, suffix) = rankLabel(rank)
                val rankColor = if (last && !first) Color(0xFFB4BCC3) else RankColors.getOrElse(rank) { Color(0xFFB4BCC3) }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Center, modifier = Modifier.width(60.dp)) {
                    Text(n, fontSize = if (first) 36.sp else 30.sp, fontWeight = FontWeight.ExtraBold, color = rankColor)
                    Text(suffix, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = rankColor, modifier = Modifier.padding(top = 4.dp))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isMe) FitPalette.Green else Color(0xFFEEF1F4))
                        .border(2.5.dp, if (isMe) FitPalette.GreenDark else Ink, CircleShape),
                ) {
                    Text(card.pseudo.take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = if (isMe) Color.White else Ink)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.pseudo + if (isMe) " (toi)" else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Ink, maxLines = 1)
                    Text(
                        if (last && card.score == 0) "🐌 Lanterne rouge"
                        else "🔥 ${card.streak} · 🏆 ${card.exploits} · ${card.sessionsDone} séances" + if (card.bossDefeated > 0) " · 💀 ${card.bossDefeated}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = FitPalette.Text.copy(alpha = 0.75f),
                    )
                    Text(todayText, style = MaterialTheme.typography.bodySmall, color = FitPalette.Text.copy(alpha = 0.75f))
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 64.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${card.score}", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("pts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                    }
                    Box(Modifier.width(64.dp).height(2.dp).background(Color(0xFF2BB3D9)))
                }
            }
            if (first) {
                Text("👑", fontSize = 28.sp, modifier = Modifier.offset(x = 2.dp, y = (-2).dp).rotate(-14f))
                Text("✦", fontSize = 14.sp, color = Color.White, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-18).dp, y = 6.dp))
            }
        }
        val detail = listOfNotNull(card.lastExploit?.let { "Dernier exploit : $it" }, updated).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.labelSmall, color = FitPalette.Text.copy(alpha = 0.6f), modifier = Modifier.padding(start = 10.dp, top = 4.dp))
        }
    }
}

// --- Tests de niveau : un panneau par test ---

/**
 * Saisie d'un test sur le thème fit : panneau blanc à bandeau rayé, le chiffre en gros entre deux boutons ronds,
 * la consigne, et le palier atteint en étoiles.
 */
@Composable
fun FitTestPanel(
    title: String,
    value: String,
    unit: String,
    onValue: (String) -> Unit,
    onStep: (Int) -> Unit,
    description: String?,
    level: String?,
    stars: Int?,
    last: Boolean,
    fieldModifier: Modifier = Modifier,
) {
    val room = LocalFitRoom.current
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).linedPaper().border(3.dp, room, shape)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(lerp(room, Color.White, 0.25f), room)))
                .fitStripes(Color.White, alpha = 0.16f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            RoundStepButton("−", "Moins", room) { onStep(-1) }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 16.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displayMedium.copy(color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, shadow = null),
                    cursorBrush = SolidColor(room),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (last) ImeAction.Done else ImeAction.Next),
                    modifier = fieldModifier.width(110.dp).semantics { contentDescription = title },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (value.isEmpty()) Text("–", style = MaterialTheme.typography.displayMedium.copy(color = FitPalette.Line, shadow = null))
                            inner()
                        }
                    },
                )
                Text(unit, style = MaterialTheme.typography.titleMedium, color = FitPalette.Text.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))
            }
            RoundStepButton("+", "Plus", room) { onStep(1) }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            description?.let { Text(highlightKeywords(it), style = MaterialTheme.typography.bodyMedium, color = FitPalette.Text) }
            if (level != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    stars?.let { FitStars(it) }
                    Text(level, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = lerp(room, Color.Black, 0.2f))
                }
            }
        }
    }
}

@Composable
private fun RoundStepButton(symbol: String, label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EEF2), Color(0xFFD8E0E6))))
            .border(BorderStroke(3.dp, color), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(symbol, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = lerp(color, Color.Black, 0.15f))
    }
}

/** Vignette d'un exo sur le thème fit : un carré brillant de la couleur de la salle (orange une fois fait). */
fun Modifier.fitThumb(room: Color, done: Boolean, partial: Boolean): Modifier {
    val base = when {
        done -> FitPalette.Orange
        partial -> lerp(room, Color.White, 0.35f)
        else -> room
    }
    return background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.3f), base, lerp(base, Color.Black, 0.12f))))
        .drawBehind {
            drawRoundRect(
                Color.White.copy(alpha = 0.35f),
                topLeft = Offset(size.width * 0.12f, 2.dp.toPx()),
                size = Size(size.width * 0.76f, size.height * 0.38f),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )
        }
        .border(2.5.dp, lerp(base, Color.Black, 0.22f), RoundedCornerShape(14.dp))
}
