package com.spacelenin.flemmefit.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.spacelenin.flemmefit.ui.theme.FitPalette
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme

/** Couleur de la « salle » du thème fit : chaque onglet a la sienne (murs, plinthe, bandeaux, onglet actif). */
val LocalFitRoom = staticCompositionLocalOf { FitRooms.Home }

object FitRooms {
    val Home = FitPalette.Green
    val Stats = FitPalette.Sky
    val Exos = Color(0xFF19B3A6)
    val Friends = FitPalette.Orange
    val Settings = Color(0xFF7C88D8)

    fun forTab(tab: Int) = when (tab) {
        1 -> Stats
        2 -> Exos
        3 -> Friends
        else -> Home
    }
}

private val White = Color.White
private val TitleShadow = Shadow(Color(0x55000000), Offset(0f, 2f), 3f)

/** Une teinte plus sombre, pour les contours. */
private fun Color.deeper() = lerp(this, Color.Black, 0.18f)

private fun Color.pale(amount: Float) = lerp(this, White, amount)

/** Titre de section : texte normal, ou bandeau coloré arrondi au titre blanc sur le thème fit. */
@Composable
fun FitSectionTitle(text: String, plain: @Composable () -> Unit) {
    if (!LocalFitTheme.current) {
        plain()
        return
    }
    val room = LocalFitRoom.current
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(color = White, shadow = TitleShadow),
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(room, room.pale(0.3f))))
            .border(2.dp, room.deeper(), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * Ligne de liste : telle quelle, ou en pastille pâle de la couleur de la salle sur le thème fit, avec son numéro
 * dans une case à gauche et un tampon une fois faite.
 */
@Composable
fun FitRowFrame(number: Int? = null, done: Boolean = false, content: @Composable () -> Unit) {
    if (!LocalFitTheme.current) {
        content()
        return
    }
    val room = LocalFitRoom.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(room.pale(0.82f), White)))
            .border(2.dp, room.pale(0.4f), shape),
    ) {
        if (number != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(White)
                    .border(2.dp, room, RoundedCornerShape(8.dp)),
            ) {
                Text("$number", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = room.deeper())
            }
        }
        Box(Modifier.weight(1f)) { content() }
        if (done) FitStamp(FitPalette.Orange, Modifier.padding(end = 10.dp), size = 22.dp)
    }
}

/** Barre d'onglets du thème fit : l'onglet actif en pastille pleine, de la couleur de sa salle, texte blanc. */
@Composable
fun FitTabBar(tabs: List<Pair<String, ImageVector>>, tab: Int, onTab: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(White, Color(0xFFECF0F3))))
            .drawBehind { drawLine(FitPalette.Line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2.dp.toPx()) }
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            FitTab(label, icon, selected = i == tab, color = FitRooms.forTab(i), modifier = Modifier.weight(1f).height(58.dp)) { onTab(i) }
        }
    }
}

/** Les mêmes onglets en colonne, à gauche, sur écran déplié. */
@Composable
fun FitTabRail(tabs: List<Pair<String, ImageVector>>, tab: Int, onTab: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(96.dp)
            .background(Brush.horizontalGradient(listOf(White, Color(0xFFECF0F3))))
            .drawBehind { drawLine(FitPalette.Line, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 2.dp.toPx()) }
            .statusBarsPadding()
            .padding(8.dp),
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            FitTab(label, icon, selected = i == tab, color = FitRooms.forTab(i), modifier = Modifier.fillMaxWidth().height(64.dp)) { onTab(i) }
        }
    }
}

@Composable
private fun FitTab(label: String, icon: ImageVector, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(if (selected) Brush.verticalGradient(listOf(color.pale(0.2f), color)) else Brush.verticalGradient(listOf(White, White)))
            .border(2.dp, if (selected) color.deeper() else FitPalette.Line, shape)
            .clickable(role = Role.Tab, onClick = onClick),
    ) {
        val tint = if (selected) White else FitPalette.TextSoft
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(shadow = if (selected) TitleShadow else null),
            fontWeight = FontWeight.Black,
            color = tint,
        )
    }
}

/**
 * Fenêtre : la boîte de dialogue habituelle, ou un panneau à cadre coloré et bandeau de titre sur le thème fit.
 * Mêmes paramètres qu'[AlertDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    if (!LocalFitTheme.current) {
        AlertDialog(
            onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton,
            icon = icon, title = title, text = text, properties = properties,
        )
        return
    }
    val room = LocalFitRoom.current
    val shape = RoundedCornerShape(26.dp)
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .clip(shape)
                .background(White)
                .border(3.dp, room, shape),
        ) {
            if (title != null || icon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(room.pale(0.15f), room)))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides White) {
                        icon?.invoke()
                        ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(color = White, shadow = TitleShadow, fontWeight = FontWeight.Black)) {
                            title?.invoke()
                        }
                    }
                }
            }
            if (text != null) {
                Box(Modifier.weight(1f, fill = false).padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = FitPalette.Text)) { text() }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** Fond des volets du bas : la couleur de la salle, très pâle, sur le thème fit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun sheetColor(): Color = if (LocalFitTheme.current) LocalFitRoom.current.pale(0.9f) else BottomSheetDefaults.ContainerColor

/** Le gros bouton d'action de la séance guidée : sur le thème fit, une pastille verte dégradée au texte blanc. */
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    if (!LocalFitTheme.current) {
        PillButton(onClick = onClick, modifier = modifier, content = content)
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9BE05A), FitPalette.Green, FitPalette.GreenDark)), CircleShape),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = White),
        border = androidx.compose.foundation.BorderStroke(3.dp, Color(0xFF2F7412)),
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = White, shadow = TitleShadow, fontWeight = FontWeight.Black), ) {
            content()
        }
    }
}

/** La consigne d'un exo : texte discret, ou bulle à bandeau vert sur le thème fit. */
@Composable
fun InstructionBubble(text: String, title: String = "Comment faire") {
    if (!LocalFitTheme.current) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(White).border(2.5.dp, FitPalette.Green, shape)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(color = White, shadow = TitleShadow),
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth().background(FitPalette.Green).padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FitPalette.Text, textAlign = TextAlign.Start, modifier = Modifier.padding(14.dp))
    }
}

/** Barre du haut : transparente sur le thème fit, pour que la salle monte jusqu'en haut de l'écran. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fitBarColors(): TopAppBarColors =
    if (LocalFitTheme.current) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = White.copy(alpha = 0.92f))
    else TopAppBarDefaults.topAppBarColors()

/** Contour coloré d'une carte, seulement sur le thème fit. */
@Composable
fun Modifier.fitBorder(color: Color, width: Dp = 3.dp, shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    if (LocalFitTheme.current) border(width, color, shape) else this

/** Fond d'une carte : blanc sur le thème fit (le contour porte la couleur), sinon [plain]. */
@Composable
fun fitCardColor(plain: Color, highlight: Boolean = false): Color =
    if (!LocalFitTheme.current) plain else if (highlight) LocalFitRoom.current.pale(0.86f) else White

/** Bouton flottant : couleur de la salle, texte blanc, sur le thème fit. */
@Composable
fun fabColors(): Pair<Color, Color> =
    if (LocalFitTheme.current) LocalFitRoom.current to White
    else FloatingActionButtonDefaults.containerColor to contentColorFor(FloatingActionButtonDefaults.containerColor)
