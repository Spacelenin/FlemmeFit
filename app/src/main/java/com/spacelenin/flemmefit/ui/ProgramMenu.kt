package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.ui.theme.ProgramColorCount
import com.spacelenin.flemmefit.ui.theme.programColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.spacelenin.flemmefit.model.PlanExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import java.time.LocalDate

/**
 * Bouton ⋮ d'un programme et tout ce qu'il ouvre : date de début, tests, mise à jour de la routine, suppression.
 * Le même sur l'accueil et sur l'écran du programme.
 */
@Composable
fun ProgramMenuButton(
    program: StoredProgram,
    repository: ProgramRepository,
    /** Réimporter la routine depuis un fichier (l'import propose alors la mise à jour). */
    onUpdateRoutine: () -> Unit,
    onDeleted: () -> Unit = {},
    onStartChanged: (LocalDate) -> Unit = {},
    /** Ouvre directement les tests (juste après l'import). */
    askTests: Boolean = false,
    onTestsAsked: () -> Unit = {},
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    var open by remember { mutableStateOf(false) }
    var dialog by rememberSaveable(program.id) { mutableStateOf(if (askTests) DIALOG_TESTS else null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = rememberToday()
    // Export : la routine telle quelle, ou en modèle avec tous les réglages écrits.
    var exportTemplate by rememberSaveable { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { picked ->
        if (picked != null) {
            val text = if (exportTemplate) PlanExport.template(program.plan) else PlanExport.routine(program.plan)
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(picked)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
                }
                Toast.makeText(context, if (!ok) "Export impossible" else if (exportTemplate) "Modèle exporté : modifie-le puis « Mettre à jour la routine »" else "Routine exportée", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun fileName(suffix: String) = program.plan.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "routine" } + suffix + "-$today.json"

    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Options de ${program.plan.name}", tint = tint) }
    if (open) {
        ProgramSheet(
            program = program,
            onDismiss = { open = false },
            onStart = { open = false; dialog = DIALOG_START },
            onTests = { open = false; dialog = DIALOG_TESTS },
            onColor = { repository.setColor(program.id, it) },
            onUpdate = { open = false; onUpdateRoutine() },
            onExport = { open = false; dialog = DIALOG_EXPORT },
            onDelete = { open = false; dialog = DIALOG_DELETE },
        )
    }

    when (dialog) {
        DIALOG_START -> DateSelectDialog(
            initial = program.start,
            onSelect = { date ->
                repository.changeStartDate(program.id, date)
                onStartChanged(date)
            },
            onDismiss = { dialog = null },
        )
        DIALOG_TESTS -> {
            TestsDialog(
                program = program,
                current = remember(program, today) { ProgramSchedule(program, today).currentTestResults() },
                onSave = { results ->
                    repository.setTests(program.id, results)
                    dialog = null
                    onTestsAsked()
                },
                onChangeOption = { test, option, result -> repository.changeTestOption(program.id, test, option, result) },
                onDismiss = {
                    dialog = null
                    onTestsAsked()
                },
            )
        }
        DIALOG_EXPORT -> PanelDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Exporter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("La routine telle quelle, ou un modèle avec tous les réglages écrits, à modifier puis à réimporter.", style = MaterialTheme.typography.bodyMedium)
                    SheetRow("⇪", "La routine (JSON)") {
                        dialog = null
                        exportTemplate = false
                        export.launch(fileName(""))
                    }
                    SheetRow("✎", "Un modèle à modifier") {
                        dialog = null
                        exportTemplate = true
                        export.launch(fileName("-modele"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } },
        )
        DIALOG_DELETE -> PanelDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Supprimer « ${program.plan.name} » ?") },
            text = { Text("Le programme et toute sa progression seront effacés.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    onDeleted()
                    repository.deleteProgram(program.id)
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Annuler") } },
        )
    }
}

private const val DIALOG_START = "start"
private const val DIALOG_TESTS = "tests"
private const val DIALOG_DELETE = "delete"
private const val DIALOG_EXPORT = "export"
