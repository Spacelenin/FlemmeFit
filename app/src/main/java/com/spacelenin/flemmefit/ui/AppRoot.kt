package com.spacelenin.flemmefit.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.data.AppSettings
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.ParseResult
import com.spacelenin.flemmefit.model.PlanParser
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
    incoming: ImportSource?,
    onIncomingConsumed: () -> Unit,
    openRequest: String?,
    onOpenRequestConsumed: () -> Unit,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // Programme tout juste importé dont il faut demander les tests de départ.
    var askTestsFor by rememberSaveable { mutableStateOf<String?>(null) }
    // Jour et onglet demandés en ouvrant un programme depuis l'accueil.
    var openEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var openTab by rememberSaveable { mutableStateOf(0) }
    // Onglet du bas : 0 accueil, 1 stats, 2 potes.
    var tab by rememberSaveable { mutableStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var restarting by remember { mutableStateOf<StoredProgram?>(null) }
    fun open(id: String, date: LocalDate? = null, programTab: Int = 0) {
        openEpoch = date?.toEpochDay()
        openTab = programTab
        showSettings = false
        openId = id
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
            if (state.programs.any { it.id == id }) open(id)
            onOpenRequestConsumed()
        }
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

    val importActions = ImportActions(
        // Beaucoup de gestionnaires de fichiers ne typent pas bien le .json : on accepte aussi le texte et tout le reste.
        fromFile = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*")) },
        fromPaste = { pasting = true },
        example = { asset ->
            scope.launch {
                pendingText = withContext(Dispatchers.IO) {
                    context.assets.open(asset).bufferedReader().use { it.readText() }
                }
            }
        },
    )

    val program = state.programs.find { it.id == openId }
    when {
        program != null -> {
            BackHandler { openId = null }
            key(program.id) {
                ProgramScreen(
                    program = program,
                    repository = repository,
                    onBack = { openId = null },
                    askTests = askTestsFor == program.id,
                    onTestsAsked = { askTestsFor = null },
                    openOn = openEpoch?.let(LocalDate::ofEpochDay),
                    initialTab = openTab,
                )
            }
        }
        showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(
                settings = settings,
                reminders = reminders,
                updater = updater,
                social = social,
                programs = state.programs,
                onSocial = { showSettings = false; tab = 2 },
                onBack = { showSettings = false },
            )
        }
        else -> {
            if (tab != 0) BackHandler { tab = 0 }
            MainTabs(tab = tab, onTab = { tab = it }) {
                when (tab) {
                    1 -> GlobalStatsScreen(programs = state.programs)
                    2 -> SocialScreen(social = social, repository = repository)
                    else -> HomeScreen(
                        programs = state.programs,
                        actions = HomeActions(
                            open = ::open,
                            postpone = repository::postpone,
                            restart = { restarting = it },
                            settings = { showSettings = true },
                        ),
                        importActions = importActions,
                        updater = updater,
                    )
                }
            }
        }
    }

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
                    open(repository.addProgram(old.plan, LocalDate.now(), results))
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
                open(id)
            },
            onConfirm = { start ->
                clearImport()
                val id = repository.addProgram(result.plan, start)
                if (result.plan.tests.isNotEmpty()) askTestsFor = id
                open(id)
            },
        )
        null -> {}
    }
}

private suspend fun readSource(context: Context, source: ImportSource): String = when (source) {
    is ImportSource.Text -> source.text
    is ImportSource.File -> withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(source.uri) ?: error("fichier introuvable")
        stream.bufferedReader().use { it.readText() }
    }
}
