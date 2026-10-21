package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.StoredProgram

/** Stats tous programmes confondus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalStatsScreen(programs: List<StoredProgram>, onBack: (() -> Unit)? = null) {
    val today = rememberToday()
    val stats = remember(programs, today) { Stats.merge(programs.map { Stats.of(ProgramSchedule(it, today)) }, today) }
    val color = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats globales") },
                navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } } },
            )
        },
    ) { padding ->
        StatsPanes(stats, color, showProgram = true, modifier = Modifier.padding(padding))
    }
}
