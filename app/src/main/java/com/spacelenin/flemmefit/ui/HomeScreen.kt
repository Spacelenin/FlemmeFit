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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.spacelenin.flemmefit.data.ProgramRepository
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.ui.theme.programColor
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme
import com.spacelenin.flemmefit.ui.theme.FitPalette
import com.spacelenin.flemmefit.update.AppUpdater
import java.time.LocalDate
import java.time.YearMonth

class ImportActions(
    val fromFile: () -> Unit,
    val fromPaste: () -> Unit,
    /** Programme embarqué (fichier de examples/). */
    val example: (asset: String) -> Unit,
    /** Restaurer une sauvegarde FlemmeFit. */
    val restore: () -> Unit,
)

/** Ce que l'accueil peut demander au reste de l'appli. */
class HomeActions(
    /** Bilan, tests et stats (l'onglet Stats). */
    val open: (id: String) -> Unit,
    /** Ouvre la page d'un jour (sa séance). */
    val openDay: (id: String, date: LocalDate) -> Unit,
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
    repository: ProgramRepository,
    /** Programme affiché (null : le premier). */
    selectedId: String?,
    onSelectProgram: (String) -> Unit,
    /** Jour à montrer (notification, import), consommé une fois montré. */
    focus: LocalDate?,
    onFocusConsumed: () -> Unit,
    actions: HomeActions,
    importActions: ImportActions,
    updater: AppUpdater,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(colors = fitBarColors(), 
                title = { Text("FlemmeFit", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    FitIconButton(onClick = { menuOpen = true }, icon = Icons.Default.Add, description = "Ajouter un programme", color = LocalFitRoom.current)
                    FitIconButton(onClick = actions.settings, icon = Icons.Default.Settings, description = "Réglages")
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (programs.isEmpty()) EmptyHome(importActions)
            else HomeContent(programs, repository, selectedId, onSelectProgram, focus, onFocusConsumed, actions, Modifier)
        }
    }

    if (menuOpen) ImportSheet(importActions, onDismiss = { menuOpen = false })
}

/** Ajouter un programme : d'où vient-il ? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(importActions: ImportActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor()) {
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
            SheetRow("🏠", "Routine maison", "Niveaux selon tes tests") { onDismiss(); importActions.example(ROUTINE_ASSET) }
            SheetRow("💪", "Programme d'exemple", "Un full body pour essayer") { onDismiss(); importActions.example(EXAMPLE_ASSET) }
            SheetRow("♻️", "Restaurer une sauvegarde", "Tout reprendre depuis un fichier de sauvegarde") { onDismiss(); importActions.restore() }
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
private fun HomeContent(
    programs: List<StoredProgram>,
    repository: ProgramRepository,
    selectedId: String?,
    onSelectProgram: (String) -> Unit,
    focus: LocalDate?,
    onFocusConsumed: () -> Unit,
    actions: HomeActions,
    modifier: Modifier,
) {
    val today = rememberToday()
    val program = programs.find { it.id == selectedId } ?: programs.first()
    val schedules = remember(programs, today) { programs.associate { it.id to ProgramSchedule(it, today) } }
    val schedule = schedules.getValue(program.id)
    val color = programColor(program.colorIndex)
    val others = programs.filter { it.id != program.id }.map { schedules.getValue(it.id) to programColor(it.colorIndex) }

    val initial = remember(program.id) { initialDate(schedule, today) }
    var monthIndex by rememberSaveable(program.id) { mutableStateOf(initial.year * 12 + initial.monthValue - 1) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    var editTests by remember { mutableStateOf(false) }
    // Le calendrier se place sur le mois demandé (début d'un programme importé, notification).
    LaunchedEffect(focus) {
        focus?.let {
            monthIndex = it.year * 12 + it.monthValue - 1
            onFocusConsumed()
        }
    }
    val openDay: (LocalDate) -> Unit = { actions.openDay(program.id, it) }
    // Les boutons de la carte du jour (mode séance, Burst, décalage) ouvrent leurs fenêtres ici.
    val dayActions = rememberDayActions(program, schedule, repository, color, onSelect = {})

    val switcher: @Composable () -> Unit = {
        if (programs.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                programs.forEach { p ->
                    FitChip(
                        selected = p.id == program.id,
                        onClick = { onSelectProgram(p.id) },
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
            dayActions = dayActions,
            onShow = openDay,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    val suggestion = remember(schedule) { runCatching { schedule.profileSuggestion() }.getOrNull() }
    val unlocked = remember(schedule) { runCatching { schedule.pendingChecks() }.getOrDefault(emptyList()) }
    var checksLater by rememberSaveable(program.id) { mutableStateOf(false) }
    val notices: @Composable () -> Unit = {
        if (suggestion != null) {
            val current = program.profileOn(today)
            val accept = { repository.setProfile(program.id, suggestion, today) }
            val later = { repository.dismissProfileHint(program.id, today) }
            NoticeBubble(
                FitPalette.Orange,
                Modifier.padding(horizontal = 16.dp),
                plain = { ProfileSuggestionCard(current, suggestion, color, accept, later, Modifier.padding(horizontal = 16.dp)) },
            ) {
                ProfileSuggestionContent(current, suggestion, accept, later)
            }
        }
        unlocked.firstOrNull()?.takeIf { !checksLater }?.let { v ->
            val familyName = program.plan.family(v.family)?.name ?: v.family
            val confirm = { repository.confirmVariant(program.id, v.id, today) }
            val later = { checksLater = true }
            NoticeBubble(
                FitPalette.Orange,
                Modifier.padding(horizontal = 16.dp),
                plain = { VariantCheckCard(v, familyName, color, confirm, later, Modifier.padding(horizontal = 16.dp)) },
            ) {
                VariantCheckContent(v, familyName, confirm, later)
            }
        }
        if (program.missingTests.isNotEmpty()) {
            val text = program.missingTests.joinToString { program.testName(it, today) } +
                " : en attendant, tu es au niveau le plus facile. Touche pour saisir tes résultats."
            NoticeBubble(
                color,
                Modifier.padding(horizontal = 16.dp).clickable { editTests = true },
                plain = {
                    Card(
                        onClick = { editTests = true },
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("📏 Tests de niveau à faire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
            ) {
                Text("📏 Tests de niveau à faire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val next = schedule.progress().nextSession
        if (next != null && next.isBoss && next.date == today.plusDays(1)) {
            NoticeBubble(
                BossColor,
                Modifier.padding(horizontal = 16.dp),
                plain = {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1515)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text("💀 Boss demain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${program.plan.name} · ${next.title()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0BDBD))
                        }
                    }
                },
            ) {
                Text("💀 Boss demain !", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = BossColor)
                Text("${program.plan.name} · ${next.title()}", style = MaterialTheme.typography.bodySmall)
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
                onSelect = openDay,
                others = others,
            )
            Text(
                (if (LocalFitTheme.current) "tampon : faite · rond orange : commencée · ✕ : manquée · point : prévue (rouge Boss, vert récup)"
                else "● plein : faite · ○ cercle : prévue · rouge : manquée / Boss · vert : récup") +
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
            // Fold déplié : la carte du jour à gauche, le mois à droite.
            Row(Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().scrollIfNeeded().padding(vertical = 8.dp),
                ) {
                    switcher()
                    todayCard()
                    notices()
                }
                Column(Modifier.weight(1f).fillMaxHeight().scrollIfNeeded()) { calendar() }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().scrollIfNeeded().padding(vertical = 8.dp),
            ) {
                switcher()
                todayCard()
                notices()
                calendar()
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (editTests) {
        TestsDialog(
            program = program,
            current = remember(schedule) { schedule.currentTestResults() },
            onSave = { results ->
                repository.setTests(program.id, results)
                editTests = false
            },
            onChangeOption = { test, option, result -> repository.changeTestOption(program.id, test, option, result) },
            onDismiss = { editTests = false },
        )
    }
}

/** Aujourd'hui si on est dans le programme, sinon son début ou sa fin. */
private fun initialDate(schedule: ProgramSchedule, today: LocalDate): LocalDate = when {
    today.isBefore(schedule.startDate) -> schedule.startDate
    else -> schedule.endDate?.takeIf { today.isAfter(it) } ?: today
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
    dayActions: DayActions,
    /** Ouvre la page d'un jour. */
    onShow: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = schedule.sessionOn(today)
    val progress = schedule.progress()
    val finished = schedule.endDate?.let { today.isAfter(it) } == true
    val notStarted = today.isBefore(schedule.startDate)
    val accent = when {
        session?.isBurst == true -> BurstColor
        session?.isBoss == true -> BossColor
        session?.isDeload == true -> DeloadColor
        else -> color
    }

    val fit = LocalFitTheme.current
    val badge = when {
        finished -> "Terminé"
        notStarted -> "Bientôt"
        session?.isBurst == true -> "🚀 Burst"
        session?.isBoss == true -> "💀 Boss"
        session?.isDeload == true -> "🌿 Récup"
        session != null -> "Aujourd'hui"
        else -> "Repos"
    }
    val programLine = program.plan.name + (session?.let { " · ${it.position(program.plan.repeat)}" } ?: "")
    val next = progress.nextSession
    val (title, sub) = when {
        finished -> "Programme terminé 🎉" to "${progress.doneSessions}/${progress.totalSessions} séances faites"
        notStarted -> "Ça commence le ${schedule.startDate.shortLabel()}" to (next?.let { "Première séance : ${it.title()}" } ?: "")
        session != null -> session.title() to "${schedule.requiredDone(session)}/${session.requiredCount} exos faits"
        next != null -> "Repos aujourd'hui" to "Prochaine : ${next.date.shortLabel()} · ${next.title()}"
        else -> "Repos aujourd'hui" to "Plus de séance à venir"
    }
    val ratio = if (session == null || session.requiredCount == 0) 0f else schedule.requiredDone(session).toFloat() / session.requiredCount

    // Le contenu sous l'en-tête, commun aux deux thèmes.
    val content: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            if (session != null && !finished) {
                if (fit) {
                    FitRing(ratio, accent)
                } else {
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
        }

        // Boutons compacts sur le thème fit, de la couleur du contexte.
        val main = ButtonDefaults.buttonColors(containerColor = if (fit) accent else MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(if (fit) 8.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                finished -> {
                    PillButton(onClick = { actions.open(program.id) }, colors = main, small = true) { Text("Voir le bilan") }
                    SoftButton(onClick = { actions.restart(program) }, small = true) { Text("Recommencer") }
                }
                session != null -> {
                    PillButton(onClick = { dayActions.onGuided(session) }, colors = main, small = true) {
                        Text(if (schedule.doneCount(session) == 0) "C'est parti" else "Continuer")
                    }
                    if (schedule.canBurst(session)) {
                        SoftButton(onClick = { dayActions.onBurst(session, true) }, small = true) { Text("🚀 Burst") }
                    }
                    if (schedule.canChill(session)) {
                        SoftButton(onClick = { dayActions.onChill(session, true) }, small = true) { Text("🛋 Chill") }
                    }
                    if (!schedule.isComplete(session)) {
                        SoftButton(onClick = { dayActions.onPostpone(session) }, small = true) { Text("Décaler") }
                    }
                }
                else -> if (next != null) SoftButton(onClick = { onShow(next.date) }, small = true) { Text("Voir la prochaine séance") }
            }
        }

        others.forEach { (p, s) ->
            val o = s.sessionOn(today)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(programColor(p.colorIndex)))
                Spacer(Modifier.width(6.dp))
                Text(
                    p.plan.name + " : " + (o?.let { "${it.title()} (${s.requiredDone(it)}/${it.requiredCount})" } ?: "repos aujourd'hui"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (fit) {
        // Panneau à bandeau : titre dans une bande rayée de la couleur du contexte, corps ligné.
        val base = accent.forWhiteText()
        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    translate(top = 4.dp.toPx()) { drawOutline(outline, base.deeper()) }
                }
                .border(1.dp, base.deeper(), shape)
                .padding(1.dp)
                .border(3.dp, Color.White, shape)
                .clip(shape)
                .background(Color.White),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(base.pale(0.3f), base)))
                    .fitStripes(Color.White, alpha = 0.18f)
                    .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Text(badge, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    programLine,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CompositionLocalProvider(LocalContentColor provides Color.White) { actions.menu(program) }
            }
            Column(Modifier.fillMaxWidth().linedPaper().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F0F0F),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 10.dp, vertical = 3.dp),
                )
                Text(
                    programLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions.menu(program)
            }
            content()
        }
    }
}

/** Jauge ronde du thème fit : anneau bombé, fines stries en biais de la couleur du contexte, pourcentage au centre. */
@Composable
private fun FitRing(ratio: Float, color: Color) {
    val base = color.forWhiteText()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 3.dp).size(62.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val c = center
            // Marche sous la jauge, liseré, bord blanc.
            drawCircle(base.deeper(), radius = r, center = c + Offset(0f, 3.dp.toPx()))
            drawCircle(base.deeper(), radius = r)
            drawCircle(Color.White, radius = r - 1.dp.toPx())
            val ring = r - 3.dp.toPx()
            val thick = 9.dp.toPx()
            val mid = ring - thick / 2
            val topLeft = Offset(c.x - mid, c.y - mid)
            val arc = Size(mid * 2, mid * 2)
            drawArc(Color(0xFFE3EAEE), 0f, 360f, false, topLeft, arc, style = Stroke(thick))
            if (ratio > 0f) {
                drawArc(
                    Brush.verticalGradient(listOf(base.pale(0.4f), base, base.deeper())),
                    -90f, 360f * ratio.coerceIn(0f, 1f), false, topLeft, arc, style = Stroke(thick),
                )
            }
            // Stries fines en biais sur l'anneau.
            val band = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(c, ring))
                addOval(Rect(c, ring - thick))
            }
            clipPath(band) {
                val step = 3.5.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(Color.White.copy(alpha = 0.32f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                    x += step
                }
            }
            // Centre bombé.
            val inner = ring - thick
            drawCircle(Color(0xFFC9D4DB), radius = inner)
            drawCircle(Brush.verticalGradient(listOf(Color.White, Color(0xFFE9EFF3)), startY = c.y - inner, endY = c.y + inner), radius = inner - 1.dp.toPx())
        }
        Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = FitPalette.Text)
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
            PillButton(onClick = importActions.fromFile, modifier = Modifier.fillMaxWidth()) { Text("Importer un fichier") }
            OutlinedButton(onClick = importActions.fromPaste, modifier = Modifier.fillMaxWidth()) { Text("Coller du texte") }
            OutlinedButton(onClick = { importActions.example(ROUTINE_ASSET) }, modifier = Modifier.fillMaxWidth()) { Text("Routine maison (niveaux)") }
            OutlinedButton(onClick = { importActions.example(EXAMPLE_ASSET) }, modifier = Modifier.fillMaxWidth()) { Text("Programme d'exemple") }
            TextButton(onClick = importActions.restore) { Text("Nouveau téléphone ? Restaurer une sauvegarde") }
        }
    }
}

/**
 * Défile seulement si le contenu dépasse l'écran : quand tout tient, ni défilement ni étirement au bout
 * (sinon la page bouge pour rien, sur le Fold plié par exemple).
 */
@Composable
private fun Modifier.scrollIfNeeded(): Modifier {
    val state = rememberScrollState()
    return verticalScroll(state, enabled = state.maxValue > 0)
}
