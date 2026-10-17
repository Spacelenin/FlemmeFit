package com.spacelenin.flemmefit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.spacelenin.flemmefit.model.Exercise
import com.spacelenin.flemmefit.model.Session
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val French = Locale.FRENCH
private val LongDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", French)
private val ShortDate = DateTimeFormatter.ofPattern("EEE d MMM", French)
private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", French)

fun LocalDate.longLabel(): String = format(LongDate).replaceFirstChar { it.titlecase(French) }
fun LocalDate.shortLabel(): String = format(ShortDate)
fun LocalDate.monthLabel(): String = format(MonthTitle).replaceFirstChar { it.titlecase(French) }

/** Le DatePicker Material travaille en millisecondes UTC. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.pickerMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** « 3 × 12 · 60 kg · repos 90s » */
fun Exercise.summary(): String = listOfNotNull(
    when {
        sets != null && reps != null -> "$sets × $reps"
        sets != null && duration != null -> "$sets × $duration"
        sets != null -> "$sets séries"
        reps != null -> "$reps reps"
        else -> null
    },
    duration?.takeIf { sets == null },
    load,
    rest?.let { "repos $it" },
).joinToString(" · ")

fun Session.title(): String = day.title ?: "Séance"

fun Session.position(repeat: Int): String = buildString {
    append(weekLabel ?: "Semaine $week")
    append(" · Jour ${day.day}")
    if (repeat > 1) append(" · Cycle $cycle/$repeat")
}

/** Date du jour, rafraîchie au retour dans l'app (si on passe minuit app ouverte). */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    return today
}
