package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.ui.theme.programColor
import com.spacelenin.flemmefit.reminders.ReminderScheduler
import com.spacelenin.flemmefit.update.AppUpdater
import java.time.LocalDate

class ImportActions(
    val fromFile: () -> Unit,
    val fromPaste: () -> Unit,
    val example: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    programs: List<StoredProgram>,
    onOpen: (String) -> Unit,
    onStats: () -> Unit,
    importActions: ImportActions,
    updater: AppUpdater,
    reminders: ReminderScheduler,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlemmeFit") },
                actions = {
                    if (programs.isNotEmpty()) {
                        TextButton(onClick = onStats) { Text("📊 Stats") }
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "Plus d'options") }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rappels") },
                                onClick = { moreOpen = false; showReminders = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Vérifier les mises à jour") },
                                onClick = { moreOpen = false; updater.check() },
                            )
                            DropdownMenuItem(
                                text = { Text("Version : build ${updater.currentVersion}") },
                                onClick = {},
                                enabled = false,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (programs.isNotEmpty()) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { menuOpen = true },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Importer") },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Depuis un fichier") }, onClick = { menuOpen = false; importActions.fromFile() })
                        DropdownMenuItem(text = { Text("Coller du texte") }, onClick = { menuOpen = false; importActions.fromPaste() })
                        DropdownMenuItem(text = { Text("Programme d'exemple") }, onClick = { menuOpen = false; importActions.example() })
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            UpdateBanner(updater)
            HomeContent(programs, onOpen, importActions, Modifier.weight(1f))
        }
    }

    if (showReminders) RemindersDialog(reminders, programs, onDismiss = { showReminders = false })
}

@Composable
private fun HomeContent(
    programs: List<StoredProgram>,
    onOpen: (String) -> Unit,
    importActions: ImportActions,
    modifier: Modifier,
) {
    if (programs.isEmpty()) {
        EmptyHome(importActions, modifier)
    } else {
        val today = rememberToday()
        // Une colonne sur téléphone / Fold plié, plusieurs sur l'écran déplié.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            items(programs, key = { it.id }) { program ->
                ProgramCard(program, today, onClick = { onOpen(program.id) })
            }
        }
    }
}

@Composable
private fun ProgramCard(program: StoredProgram, today: LocalDate, onClick: () -> Unit) {
    val schedule = remember(program, today) { ProgramSchedule(program, today) }
    val progress = schedule.progress()
    val color = programColor(program.colorIndex)

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(16.dp).weight(1f)) {
                Text(program.plan.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                program.plan.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { progress.ratio }, color = color, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text("${progress.doneExercises}/${progress.totalExercises} exos", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                val next = progress.nextSession
                Text(
                    when {
                        next == null && progress.doneSessions == progress.totalSessions -> "Programme terminé 🎉"
                        next == null -> "Plus de séance à venir"
                        next.isBoss && next.date == today -> "Aujourd'hui : Boss 💀 ${next.title()}"
                        next.isBoss -> "Demain : Boss 💀 ${next.title()}"
                        next.date == today -> "Aujourd'hui : ${next.title()}"
                        else -> "Prochaine : ${next.date.shortLabel()} — ${next.title()}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${progress.doneSessions}/${progress.totalSessions} séances · série ${progress.streak} 🔥",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(importActions: ImportActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Text("Aucun programme", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Importe un entraînement au format FlemmeFit (JSON) pour le suivre dans le calendrier.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = importActions.fromFile, modifier = Modifier.fillMaxWidth()) { Text("Importer un fichier") }
            OutlinedButton(onClick = importActions.fromPaste, modifier = Modifier.fillMaxWidth()) { Text("Coller du texte") }
            OutlinedButton(onClick = importActions.example, modifier = Modifier.fillMaxWidth()) { Text("Essayer le programme d'exemple") }
        }
    }
}
