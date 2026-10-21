package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Options de ${program.plan.name}", tint = tint) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Changer la date de début") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { open = false; dialog = DIALOG_START },
            )
            if (program.plan.tests.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Tests de départ") },
                    leadingIcon = { Icon(Icons.Default.Star, null) },
                    onClick = { open = false; dialog = DIALOG_TESTS },
                )
            }
            DropdownMenuItem(
                text = { Text("Couleur du programme") },
                leadingIcon = { Dot(programColor(program.colorIndex), size = 18) },
                onClick = { open = false; dialog = DIALOG_COLOR },
            )
            DropdownMenuItem(
                text = { Text("Mettre à jour la routine") },
                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                onClick = { open = false; onUpdateRoutine() },
            )
            DropdownMenuItem(
                text = { Text("Supprimer le programme", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; dialog = DIALOG_DELETE },
            )
        }
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
            val today = rememberToday()
            TestsDialog(
                program = program,
                current = remember(program, today) { ProgramSchedule(program, today).currentTestResults() },
                onSave = { results ->
                    repository.setTests(program.id, results)
                    dialog = null
                    onTestsAsked()
                },
                onDismiss = {
                    dialog = null
                    onTestsAsked()
                },
            )
        }
        DIALOG_COLOR -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Couleur de « ${program.plan.name} »") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0 until ProgramColorCount).forEach { i ->
                        val selected = i == Math.floorMod(program.colorIndex, ProgramColorCount)
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(programColor(i))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable {
                                    repository.setColor(program.id, i)
                                    dialog = null
                                }
                                .semantics { contentDescription = if (i == 0) "Couleur du thème" else "Couleur ${i + 1}" },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Fermer") } },
        )
        DIALOG_DELETE -> AlertDialog(
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
private const val DIALOG_COLOR = "color"
