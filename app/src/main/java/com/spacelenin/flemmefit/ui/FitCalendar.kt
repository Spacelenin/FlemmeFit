package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.SessionStatus
import com.spacelenin.flemmefit.ui.theme.FitPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val FitWeekDays = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

private fun dayColor(column: Int, base: Color): Color = when (column) {
    5 -> FitPalette.Saturday
    6 -> FitPalette.Sunday
    else -> base
}

/** Le calendrier du thème fit : un panneau à bandeau coloré, une grille, un tampon sur chaque séance faite. */
@Composable
fun FitMonthCalendar(
    schedule: ProgramSchedule,
    color: Color,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    others: List<Pair<ProgramSchedule, Color>> = emptyList(),
) {
    val frame = RoundedCornerShape(18.dp)
    val grid = color.copy(alpha = 0.45f)
    Column(
        modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .padding(4.dp)
            .clip(frame)
            .background(Color.White)
            .border(3.dp, color, frame),
    ) {
        // Bandeau : − Mois +.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(color).padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            RoundStep("−", "Mois précédent", color) { onMonthChange(month.minusMonths(1)) }
            Text(
                month.atDay(1).monthLabel(),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = Color.White,
                    shadow = Shadow(Color(0x55000000), Offset(0f, 2f), 3f),
                ),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            RoundStep("+", "Mois suivant", color) { onMonthChange(month.plusMonths(1)) }
        }
        Row(Modifier.fillMaxWidth()) {
            FitWeekDays.forEachIndexed { c, d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelLarge,
                    color = dayColor(c, FitPalette.Text),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).border(0.5.dp, grid).padding(vertical = 4.dp),
                )
            }
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val rows = (offset + month.lengthOfMonth() + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayOfMonth = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(0.95f).border(0.5.dp, grid)) {
                        if (dayOfMonth in 1..month.lengthOfMonth()) {
                            FitDayCell(month.atDay(dayOfMonth), c, schedule, color, selected, today, onSelect, others)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundStep(symbol: String, label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(symbol, color = color, style = MaterialTheme.typography.titleLarge.copy(shadow = null, color = color), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun FitDayCell(
    date: LocalDate,
    column: Int,
    schedule: ProgramSchedule,
    color: Color,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    others: List<Pair<ProgramSchedule, Color>>,
) {
    val otherColors = others.filter { (s, _) -> s.sessionOn(date) != null }.map { it.second }
    val hasExtra = schedule.program.extrasOn(date).isNotEmpty()
    val session = schedule.sessionOn(date)
    val status = session?.let { schedule.status(it) }
    val inProgram = schedule.isInProgram(date)
    val label = dayCellLabel(date, status, session?.isBoss == true, hasExtra)
    val background = when {
        status == SessionStatus.MISSED -> Color(0xFFFDE8E8)
        date == today -> FitPalette.Highlight
        else -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(background)
            .then(if (date == selected) Modifier.border(2.5.dp, color) else Modifier)
            .clickable { onSelect(date) }
            .semantics { contentDescription = label },
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (date == today) FontWeight.Black else FontWeight.Bold,
            color = dayColor(column, FitPalette.Text).copy(alpha = if (inProgram) 1f else 0.45f),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 1.dp),
        )
        Box(Modifier.align(Alignment.Center).padding(top = 8.dp)) {
            when (status) {
                SessionStatus.DONE -> FitStamp(FitPalette.Orange, size = 24.dp)
                SessionStatus.SAVED -> FitStamp(FitPalette.Orange, size = 24.dp, faded = true)
                SessionStatus.PARTIAL -> Spacer(Modifier.size(16.dp).border(2.5.dp, FitPalette.Orange, CircleShape))
                SessionStatus.MISSED -> Text("✕", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                SessionStatus.PENDING -> Spacer(
                    Modifier.size(9.dp).clip(CircleShape).background(
                        when {
                            session?.isBoss == true -> BossColor
                            session?.isDeload == true -> DeloadColor
                            else -> color
                        },
                    ),
                )
                null -> {}
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
        ) {
            if (session?.isBoss == true) Text("💀", style = MaterialTheme.typography.labelSmall)
            if (hasExtra) Text("✚", style = MaterialTheme.typography.labelSmall, color = DeloadColor)
            otherColors.forEach { Spacer(Modifier.size(5.dp).clip(CircleShape).background(it)) }
        }
    }
}
