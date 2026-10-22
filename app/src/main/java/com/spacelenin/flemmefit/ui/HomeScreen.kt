package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.Session
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.ui.theme.programColor
import com.spacelenin.flemmefit.update.AppUpdater
import java.time.LocalDate
import java.time.YearMonth

class ImportActions(
    val fromFile: () -> Unit,
    val fromPaste: () -> Unit,
    /** Programme embarqué (fichier de examples/). */
    val example: (asset: String) -> Unit,
)

/** Ce que l'accueil peut demander au reste de l'appli. */
class HomeActions(
    /** Ouvre un programme, sur un jour donné ou sur un onglet (0 calendrier, 1 stats). */
    val open: (id: String, date: LocalDate?, tab: Int) -> Unit,
    val postpone: (id: String, dayIndex: Int) -> Unit,
    /** Passe la séance du jour en Burst. */
    val burst: (id: String, dayIndex: Int) -> Unit,
    /** Repart de zéro avec la même routine, à partir d'aujourd'hui. */
    val restart: (StoredProgram) -> Unit,
    val settings: () -> Unit,
    /** Bouton ⋮ d'un programme (date, tests, mise à jour, suppression). */
    val menu: @Composable (StoredProgram) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    programs: List<StoredProgram>,
    actions: HomeActions,
    importActions: ImportActions,
    updater: AppUpdater,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlemmeFit", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Add, "Ajouter un programme") }
                    IconButton(onClick = actions.settings) { Icon(Icons.Default.Settings, "Réglages") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (programs.isEmpty()) EmptyHome(importActions)
            else HomeContent(programs, actions, Modifier)
            // Par-dessus l'écran, en bas : l'accueil ne bouge pas quand une mise à jour est annoncée.
            UpdateBanner(updater, floating = true, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (menuOpen) ImportSheet(importActions, onDismiss = { menuOpen = false })
}

/** Ajouter un programme : d'où vient-il ? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(importActions: ImportActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Ajouter un programme",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                "Un fichier qui porte le nom d'un programme existant propose de le mettre à jour, suivi gardé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            SheetRow("📄", "Depuis un fichier", "Un .json reçu ou téléchargé") { onDismiss(); importActions.fromFile() }
            SheetRow("📋", "Coller du texte", "Le JSON copié depuis un message") { onDismiss(); importActions.fromPaste() }
            SheetRow("🏠", "Routine maison", "Paliers selon tes tests de départ") { onDismiss(); importActions.example(ROUTINE_ASSET) }
            SheetRow("💪", "Programme d'exemple", "Un full body pour essayer") { onDismiss(); importActions.example(EXAMPLE_ASSET) }
        }
    }
}

@Composable
private fun SheetRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeContent(programs: List<StoredProgram>, actions: HomeActions, modifier: Modifier) {
    val today = rememberToday()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val program = programs.find { it.id == selectedId } ?: programs.first()
    val schedules = remember(programs, today) { programs.associate { it.id to ProgramSchedule(it, today) } }
    val schedule = schedules.getValue(program.id)
    val color = programColor(program.colorIndex)
    val others = programs.filter { it.id != program.id }.map { schedules.getValue(it.id) to programColor(it.colorIndex) }

    var monthIndex by rememberSaveable { mutableStateOf(today.year * 12 + today.monthValue - 1) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    var postponing by remember { mutableStateOf<Session?>(null) }

    val switcher: @Composable () -> Unit = {
        if (programs.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                programs.forEach { p ->
                    FilterChip(
                        selected = p.id == program.id,
                        onClick = { selectedId = p.id },
                        label = { Text(p.plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(programColor(p.colorIndex))) },
                    )
                }
            }
        }
    }
    val todayCard: @Composable () -> Unit = {
        TodayCard(
            program = program,
            schedule = schedule,
            color = color,
            today = today,
            others = programs.filter { it.id != program.id }.map { it to schedules.getValue(it.id) },
            actions = actions,
            onPostpone = { postponing = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    val bossCard: @Composable () -> Unit = {
        val next = schedule.progress().nextSession
        if (next != null && next.isBoss && next.date == today.plusDays(1)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1515)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("💀 Boss demain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("${program.plan.name} · ${next.title()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0BDBD))
                }
            }
        }
    }
    val calendar: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MonthCalendar(
                schedule = schedule,
                color = color,
                month = month,
                onMonthChange = { monthIndex = it.year * 12 + it.monthValue - 1 },
                selected = today,
                today = today,
                onSelect = { date -> actions.open(program.id, date, 0) },
                others = others,
            )
            Text(
                "● plein : faite · ○ cercle : prévue · rouge : manquée / Boss · vert : décharge" +
                    if (others.isNotEmpty()) " · point : autre programme" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            // Fold déplié : la séance du jour à gauche, le mois à droite.
            Row(Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                ) {
                    switcher()
                    todayCard()
                    bossCard()
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { calendar() }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            ) {
                switcher()
                todayCard()
                calendar()
                bossCard()
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    postponing?.let { s ->
        AlertDialog(
            onDismissRequest = { postponing = null },
            title = { Text("Repousser la séance ?") },
            text = {
                Text(
                    "La séance du ${s.date.shortLabel()} passe au ${s.date.plusDays(1).shortLabel()}. " +
                        "Tout le reste du programme est décalé d'un jour.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.postpone(program.id, s.index)
                    postponing = null
                }) { Text("Repousser") }
            },
            dismissButton = { TextButton(onClick = { postponing = null }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(
    program: StoredProgram,
    schedule: ProgramSchedule,
    color: Color,
    today: LocalDate,
    others: List<Pair<StoredProgram, ProgramSchedule>>,
    actions: HomeActions,
    onPostpone: (Session) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = schedule.sessionOn(today)
    val progress = schedule.progress()
    val finished = schedule.endDate?.let { today.isAfter(it) } == true
    val notStarted = today.isBefore(schedule.startDate)
    var askBurst by remember { mutableStateOf(false) }
    val accent = when {
        session?.isBurst == true -> BurstColor
        session?.isBoss == true -> BossColor
        session?.isDeload == true -> DeloadColor
        else -> color
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val badge = when {
                    finished -> "Terminé"
                    notStarted -> "Bientôt"
                    session?.isBurst == true -> "🚀 Burst"
                    session?.isBoss == true -> "💀 Boss"
                    session?.isDeload == true -> "🌿 Décharge"
                    session != null -> "Aujourd'hui"
                    else -> "Repos"
                }
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F0F0F),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Text(
                    program.plan.name + (session?.let { " · ${it.position(program.plan.repeat)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions.menu(program)
            }

            val next = progress.nextSession
            val (title, sub) = when {
                finished -> "Programme terminé 🎉" to "${progress.doneSessions}/${progress.totalSessions} séances faites"
                notStarted -> "Ça commence le ${schedule.startDate.shortLabel()}" to (next?.let { "Première séance : ${it.title()}" } ?: "")
                session != null -> session.title() to "${schedule.doneCount(session)}/${session.exerciseCount} exos faits"
                next != null -> "Repos aujourd'hui" to "Prochaine : ${next.date.shortLabel()} · ${next.title()}"
                else -> "Repos aujourd'hui" to "Plus de séance à venir"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (session != null && !finished) {
                    val ratio = if (session.exerciseCount == 0) 0f else schedule.doneCount(session).toFloat() / session.exerciseCount
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                        CircularProgressIndicator(
                            progress = { ratio },
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    finished -> {
                        Button(onClick = { actions.open(program.id, null, 1) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) {
                            Text("Voir le bilan")
                        }
                        OutlinedButton(onClick = { actions.restart(program) }) { Text("Recommencer") }
                    }
                    session != null -> {
                        Button(onClick = { actions.open(program.id, today, 0) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) {
                            Text(if (schedule.doneCount(session) == 0) "C'est parti" else "Continuer")
                        }
                        if (schedule.canBurst(session)) {
                            OutlinedButton(onClick = { askBurst = true }) { Text("🚀 Burst") }
                        }
                        if (schedule.doneCount(session) < session.exerciseCount) {
                            OutlinedButton(onClick = { onPostpone(session) }) { Text("Repousser") }
                        }
                    }
                    else -> OutlinedButton(onClick = { actions.open(program.id, next?.date, 0) }) { Text("Voir le programme") }
                }
            }

            others.forEach { (p, s) ->
                val o = s.sessionOn(today)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(programColor(p.colorIndex)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        p.plan.name + " : " + (o?.let { "${it.title()} (${s.doneCount(it)}/${it.exerciseCount})" } ?: "repos aujourd'hui"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (askBurst && session != null) {
        BurstDialog(
            onConfirm = {
                askBurst = false
                actions.burst(program.id, session.index)
            },
            onDismiss = { askBurst = false },
        )
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
            Text("Aucun programme", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "Importe un entraînement au format FlemmeFit (JSON) pour le suivre dans le calendrier.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = importActions.fromFile, modifier = Modifier.fillMaxWidth()) { Text("Importer un fichier") }
            OutlinedButton(onClick = importActions.fromPaste, modifier = Modifier.fillMaxWidth()) { Text("Coller du texte") }
            OutlinedButton(onClick = { importActions.example(ROUTINE_ASSET) }, modifier = Modifier.fillMaxWidth()) { Text("Routine maison (paliers)") }
            OutlinedButton(onClick = { importActions.example(EXAMPLE_ASSET) }, modifier = Modifier.fillMaxWidth()) { Text("Programme d'exemple") }
        }
    }
}
