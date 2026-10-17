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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.SessionStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val WeekDays = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
fun MonthCalendar(
    schedule: ProgramSchedule,
    color: Color,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mois précédent")
            }
            Text(
                month.atDay(1).monthLabel(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WeekDays.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }

        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = offset + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayOfMonth = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (dayOfMonth in 1..month.lengthOfMonth()) {
                            DayCell(
                                date = month.atDay(dayOfMonth),
                                schedule = schedule,
                                color = color,
                                selected = selected,
                                today = today,
                                onSelect = onSelect,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    schedule: ProgramSchedule,
    color: Color,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val session = schedule.sessionOn(date)
    val status = session?.let { schedule.status(it) }
    val scheme = MaterialTheme.colorScheme
    val (background, content) = when (status) {
        SessionStatus.DONE -> color to Color.White
        SessionStatus.PARTIAL -> color.copy(alpha = 0.35f) to scheme.onSurface
        SessionStatus.MISSED -> scheme.errorContainer to scheme.onErrorContainer
        SessionStatus.PENDING, null -> Color.Transparent to
            if (schedule.isInProgram(date)) scheme.onSurface else scheme.onSurface.copy(alpha = 0.4f)
    }
    val label = buildString {
        append(date.longLabel())
        when (status) {
            SessionStatus.DONE -> append(", séance faite")
            SessionStatus.PARTIAL -> append(", séance commencée")
            SessionStatus.MISSED -> append(", séance manquée")
            SessionStatus.PENDING -> append(", séance prévue")
            null -> {}
        }
        if (session?.isBoss == true) append(", jour Boss")
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .then(if (date == selected) Modifier.border(2.dp, scheme.onSurface, CircleShape) else Modifier)
            .background(background)
            .then(if (status == SessionStatus.PENDING) Modifier.border(1.5.dp, color, CircleShape) else Modifier)
            .clickable { onSelect(date) }
            .semantics { contentDescription = label },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                date.dayOfMonth.toString(),
                color = content,
                fontWeight = if (date == today) FontWeight.ExtraBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (session?.isBoss == true) {
                Text("💀", style = MaterialTheme.typography.labelSmall)
            } else if (date == today) {
                Spacer(Modifier.size(4.dp).clip(CircleShape).background(content))
            }
        }
    }
}
