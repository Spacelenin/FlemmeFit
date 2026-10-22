package com.spacelenin.flemmefit.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.data.AppSettings
import com.spacelenin.flemmefit.data.BackupManager
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.Counter
import com.spacelenin.flemmefit.model.ParseResult
import com.spacelenin.flemmefit.model.PlanParser
import com.spacelenin.flemmefit.model.Reminders
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.reminders.ReminderScheduler
import com.spacelenin.flemmefit.social.SocialSync
import com.spacelenin.flemmefit.update.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface ImportSource {
    data class Text(val text: String) : ImportSource
    data class File(val uri: Uri) : ImportSource
}

const val EXAMPLE_ASSET = "exemple-full-body.json"
const val ROUTINE_ASSET = "routine-maison.json"

@Composable
fun AppRoot(
    repository: ProgramRepository,
    updater: AppUpdater,
    reminders: ReminderScheduler,
    social: SocialSync,
    settings: AppSettings,
    backup: BackupManager,
    incoming: ImportSource?,
    onIncomingConsumed: () -> Unit,
    openRequest: String?,
    onOpenRequestConsumed: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timer = remember { WorkoutTimer() }
    val guard = remember { SessionGuard() }
    val restBonus by settings.restBonus.collectAsStateWithLifecycle()

    // Programme tout juste importé dont il faut demander les tests de départ.
    var askTestsFor by rememberSaveable { mutableStateOf<String?>(null) }
    // Programme affiché sur l'accueil, et jour à y montrer (notification, import).
    var homeProgramId by rememberSaveable { mutableStateOf<String?>(null) }
    var homeFocus by rememberSaveable { mutableStateOf<Long?>(null) }
    // Page d'un jour ouverte depuis le calendrier : programme et date.
    var dayProgramId by rememberSaveable { mutableStateOf<String?>(null) }
    var dayEpoch by rememberSaveable { mutableStateOf(0L) }
    // Onglet du bas : 0 accueil, 1 stats, 2 exos, 3 potes.
    var tab by rememberSaveable { mutableStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var restarting by remember { mutableStateOf<StoredProgram?>(null) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }
    val tutorialOffered by settings.tutorialOffered.collectAsStateWithLifecycle()
    val pseudo by social.pseudo.collectAsStateWithLifecycle()
    // « Plus tard » : on redemande au prochain lancement.
    var pseudoSkipped by rememberSaveable { mutableStateOf(false) }
    /** Bilan, tests et stats : l'onglet Stats. */
    fun openStats() {
        showSettings = false
        dayProgramId = null
        tab = 1
    }

    fun openDay(id: String, date: LocalDate) {
        showSettings = false
        dayEpoch = date.toEpochDay()
        dayProgramId = id
    }

    /** Montre un programme sur l'accueil, au mois du jour indiqué. */
    fun showOnHome(id: String, date: LocalDate = LocalDate.now()) {
        dayProgramId = null
        showSettings = false
        tab = 0
        homeProgramId = id
        homeFocus = date.toEpochDay()
    }
    var pasting by rememberSaveable { mutableStateOf(false) }
    // Texte brut sauvegardé (et non le résultat) pour que l'import en cours survive au pliage / dépliage.
    var pendingText by rememberSaveable { mutableStateOf<String?>(null) }
    var readError by rememberSaveable { mutableStateOf<String?>(null) }
    val parsed = remember(pendingText, readError) {
        readError?.let { ParseResult.Failure(listOf(it)) } ?: pendingText?.let(PlanParser::parse)
    }
    fun clearImport() {
        pendingText = null
        readError = null
    }

    fun load(source: ImportSource) {
        scope.launch {
            try {
                pendingText = readSource(context, source)
            } catch (e: Exception) {
                readError = "Lecture du fichier impossible : ${e.message.orEmpty()}"
            }
        }
    }

    LaunchedEffect(openRequest) {
        openRequest?.let { id ->
            // Notification de rappel : directement la séance du jour.
            if (state.programs.any { it.id == id }) openDay(id, LocalDate.now())
            // Notification des potes : leur onglet.
            else if (id == Reminders.FRIENDS_ID) tab = 3
            onOpenRequestConsumed()
        }
    }

    // La routine intégrée a évolué : les programmes qui la suivent passent à la nouvelle version, suivi gardé.
    LaunchedEffect(Unit) {
        val text = try {
            withContext(Dispatchers.IO) { context.assets.open(ROUTINE_ASSET).bufferedReader().use { it.readText() } }
        } catch (_: Exception) {
            null
        }
        val latest = (text?.let(PlanParser::parse) as? ParseResult.Success)?.plan ?: return@LaunchedEffect
        repository.state.value.programs
            .filter { it.plan.name == latest.name && it.plan.revision < latest.revision }
            .forEach { repository.replacePlan(it.id, latest) }
    }

    LaunchedEffect(incoming) {
        incoming?.let {
            load(it)
            onIncomingConsumed()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { load(ImportSource.File(it)) }
    }

    val restore = rememberRestore(backup)
    val importActions = ImportActions(
        restore = restore,
        // Beaucoup de gestionnaires de fichiers ne typent pas bien le .json : on accepte aussi le texte et tout le reste.
        fromFile = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*")) },
        fromPaste = { pasting = true },
        example = { asset ->
            scope.launch {
                try {
                    pendingText = withContext(Dispatchers.IO) {
                        context.assets.open(asset).bufferedReader().use { it.readText() }
                    }
                } catch (e: Exception) {
                    readError = "Programme d'exemple illisible : ${e.message.orEmpty()}"
                }
            }
        },
    )

    val dayProgram = state.programs.find { it.id == dayProgramId }
    CompositionLocalProvider(LocalWorkoutTimer provides timer, LocalRestBonus provides restBonus, LocalSessionGuard provides guard) {
        Box(Modifier.fillMaxSize()) {
            when {
                dayProgram != null -> {
                    BackHandler { dayProgramId = null }
                    key(dayProgram.id) {
                        DayScreen(
                            program = dayProgram,
                            repository = repository,
                            date = LocalDate.ofEpochDay(dayEpoch),
                            onDate = { dayEpoch = it.toEpochDay() },
                            onBack = { dayProgramId = null },
                        )
                    }
                }
                showSettings -> {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        settings = settings,
                        backup = backup,
                        reminders = reminders,
                        updater = updater,
                        social = social,
                        programs = state.programs,
                        onSocial = { showSettings = false; tab = 3 },
                        onTutorial = { showTutorial = true },
                        onBack = { showSettings = false },
                    )
                }
                else -> {
                    if (tab != 0) BackHandler { tab = 0 }
                    MainTabs(tab = tab, onTab = { tab = it }) {
                        when (tab) {
                            1 -> GlobalStatsScreen(programs = state.programs)
                            2 -> MyExercisesScreen(programs = state.programs, repository = repository)
                            3 -> SocialScreen(social = social, repository = repository)
                            else -> HomeScreen(
                                programs = state.programs,
                                repository = repository,
                                selectedId = homeProgramId,
                                onSelectProgram = { homeProgramId = it },
                                focus = homeFocus?.let(LocalDate::ofEpochDay),
                                onFocusConsumed = { homeFocus = null },
                                actions = HomeActions(
                                    open = { openStats() },
                                    openDay = ::openDay,
                                    restart = { restarting = it },
                                    settings = { showSettings = true },
                                    menu = { p ->
                                        ProgramMenuButton(
                                            p,
                                            repository,
                                            onUpdateRoutine = importActions.fromFile,
                                            onStartChanged = { homeFocus = it.toEpochDay() },
                                            // Juste après l'import : les tests de départ s'ouvrent d'eux-mêmes.
                                            askTests = askTestsFor == p.id,
                                            onTestsAsked = { askTestsFor = null },
                                        )
                                    },
                                ),
                                importActions = importActions,
                                updater = updater,
                            )
                        }
                    }
                }
            }
            // Au-dessus de la barre du bas, sur tous les écrans.
            TimerOverlay(timer, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
        }
    }

    // Compteurs cachés : dévoilés le jour où l'un d'eux passe le cap, une seule fois.
    val countersUnlocked by settings.countersUnlocked.collectAsStateWithLifecycle()
    val counters = remember(state.programs, countersUnlocked) {
        if (countersUnlocked) emptyList() else countersOf(state.programs, LocalDate.now())
    }
    var unlockedBy by remember { mutableStateOf<Counter?>(null) }
    LaunchedEffect(counters) {
        if (!countersUnlocked && !settings.countersLockedByHand && Counter.unlocked(counters)) {
            settings.unlockCounters()
            unlockedBy = Counter.milestone(counters)
        }
    }
    // Pas au milieu d'une séance : le message attend qu'elle soit finie.
    unlockedBy?.takeIf { !guard.active }?.let { milestone ->
        CountersUnlockedDialog(
            milestone = milestone,
            onSee = {
                unlockedBy = null
                dayProgramId = null
                showSettings = true
            },
            onDismiss = { unlockedBy = null },
        )
    }

    val changelog by updater.changelog.collectAsStateWithLifecycle()
    val calm = askTestsFor == null && parsed == null && !pasting && changelog == null
    // Pas encore de pseudo : on le demande dès l'ouverture, pour apparaître chez les potes sans y penser.
    if (pseudo == null && !pseudoSkipped && calm) {
        PseudoPromptDialog(onSave = social::setPseudo, onLater = { pseudoSkipped = true })
    }
    // Premier programme : on propose le tuto une fois, après les éventuels tests de départ et le pseudo.
    if (!tutorialOffered && state.programs.isNotEmpty() && calm && (pseudo != null || pseudoSkipped)) {
        TutorialOfferDialog(
            onYes = {
                settings.markTutorialOffered()
                showTutorial = true
            },
            onNo = settings::markTutorialOffered,
        )
    }
    if (showTutorial) TutorialDialog(onClose = { showTutorial = false })

    restarting?.let { old ->
        AlertDialog(
            onDismissRequest = { restarting = null },
            title = { Text("Recommencer « ${old.plan.name} » ?") },
            text = {
                Text(
                    "Un nouveau programme démarre aujourd'hui avec la même routine et tes derniers résultats de tests. " +
                        "L'ancien reste dans la liste avec son bilan : tu pourras le supprimer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restarting = null
                    val results = ProgramSchedule(old, LocalDate.now()).currentTestResults()
                    showOnHome(repository.addProgram(old.plan, LocalDate.now(), results))
                }) { Text("Recommencer") }
            },
            dismissButton = { TextButton(onClick = { restarting = null }) { Text("Annuler") } },
        )
    }

    if (pasting) {
        PasteDialog(
            onDismiss = { pasting = false },
            onSubmit = {
                pasting = false
                pendingText = it
            },
        )
    }

    when (val result = parsed) {
        is ParseResult.Failure -> ImportErrorDialog(result.errors, onDismiss = ::clearImport)
        is ParseResult.Success -> ImportConfirmDialog(
            plan = result.plan,
            programs = state.programs,
            onDismiss = ::clearImport,
            onUpdate = { id ->
                clearImport()
                repository.replacePlan(id, result.plan)
                if (result.plan.tests.any { t -> state.programs.first { it.id == id }.tests[t.id] == null }) askTestsFor = id
                showOnHome(id)
            },
            onConfirm = { start ->
                clearImport()
                val id = repository.addProgram(result.plan, start)
                if (result.plan.tests.isNotEmpty()) askTestsFor = id
                showOnHome(id, start)
            },
        )
        null -> {}
    }

    ChangelogDialog(updater)
    // En dernier : passe par-dessus tout le reste.
    // Pas en pleine séance : la mise à jour s'affiche dès que le mode séance est fermé et le minuteur arrêté.
    if (!guard.active) UpdateGate(updater)
}

private suspend fun readSource(context: Context, source: ImportSource): String = when (source) {
    is ImportSource.Text -> source.text
    is ImportSource.File -> withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(source.uri) ?: error("fichier introuvable")
        stream.bufferedReader().use { it.readText() }
    }
}
