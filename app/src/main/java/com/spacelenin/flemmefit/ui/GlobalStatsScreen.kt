package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.StoredProgram

/** Stats tous programmes confondus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalStatsScreen(programs: List<StoredProgram>, onBack: () -> Unit) {
    val today = rememberToday()
    val stats = remember(programs, today) { Stats.merge(programs.map { Stats.of(ProgramSchedule(it, today)) }, today) }
    val color = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats globales") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f).fillMaxHeight()) { statsOverviewItems(stats, color, showProgram = true) }
                    VerticalDivider()
                    LazyColumn(Modifier.weight(1f).fillMaxHeight()) { statsExerciseItems(stats) }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    statsOverviewItems(stats, color, showProgram = true)
                    statsExerciseItems(stats)
                }
            }
        }
    }
}
