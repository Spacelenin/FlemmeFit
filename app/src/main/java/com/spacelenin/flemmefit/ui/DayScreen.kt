package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.ui.theme.programColor
import java.time.LocalDate

/** La page d'un jour, ouverte depuis le calendrier de l'accueil : sa séance, ses exercices, ses actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    program: StoredProgram,
    repository: ProgramRepository,
    date: LocalDate,
    /** Passer à un autre jour (flèches, ou séance décalée). */
    onDate: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val today = rememberToday()
    val schedule = remember(program, today) { ProgramSchedule(program, today) }
    val color = programColor(program.colorIndex)
    val actions = rememberDayActions(program, schedule, repository, color, onSelect = onDate)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program.plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour au calendrier") } },
                actions = {
                    IconButton(onClick = { onDate(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Jour précédent") }
                    IconButton(onClick = { onDate(date.plusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Jour suivant") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                dayDetailItems(schedule, date, color, actions)
            }
        }
    }
}
