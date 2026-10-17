package com.spacelenin.flemmefit.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.ParseResult
import com.spacelenin.flemmefit.model.PlanParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportSource {
    data class Text(val text: String) : ImportSource
    data class File(val uri: Uri) : ImportSource
}

const val EXAMPLE_ASSET = "exemple-full-body.json"

@Composable
fun AppRoot(repository: ProgramRepository, incoming: ImportSource?, onIncomingConsumed: () -> Unit) {
    val state by repository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var showStats by rememberSaveable { mutableStateOf(false) }
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
        example = {
            scope.launch {
                pendingText = withContext(Dispatchers.IO) {
                    context.assets.open(EXAMPLE_ASSET).bufferedReader().use { it.readText() }
                }
            }
        },
    )

    val program = state.programs.find { it.id == openId }
    when {
        program != null -> {
            BackHandler { openId = null }
            ProgramScreen(program = program, repository = repository, onBack = { openId = null })
        }
        showStats -> {
            BackHandler { showStats = false }
            GlobalStatsScreen(programs = state.programs, onBack = { showStats = false })
        }
        else -> HomeScreen(
            programs = state.programs,
            onOpen = { openId = it },
            onStats = { showStats = true },
            importActions = importActions,
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
            onDismiss = ::clearImport,
            onConfirm = { start ->
                clearImport()
                openId = repository.addProgram(result.plan, start)
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
