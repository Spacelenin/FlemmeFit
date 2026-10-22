package com.spacelenin.flemmefit.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.data.AppSettings
import com.spacelenin.flemmefit.data.BackupManager
import com.spacelenin.flemmefit.model.Backup
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

private val whenFormat = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH)

private fun formatEpoch(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(whenFormat)

private fun formatCreated(text: String) = try {
    LocalDateTime.parse(text).format(whenFormat)
} catch (_: Exception) {
    text
}

/** Relance l'appli de zéro : les réglages restaurés sont relus au démarrage. */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    exitProcess(0)
}

private val openTypes = arrayOf("application/json", "text/*", "application/octet-stream", "*/*")

/**
 * Restaurer une sauvegarde : choisir le fichier, voir ce qu'il contient, confirmer, et l'appli redémarre.
 * Renvoie l'action qui ouvre le sélecteur.
 */
@Composable
fun rememberRestore(backup: BackupManager): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Backup?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val read = try {
                    backup.read(uri)
                } catch (_: Exception) {
                    null
                }
                if (read == null) error = "Ce fichier n'est pas une sauvegarde FlemmeFit." else pending = read
            }
        }
    }

    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { if (!restoring) pending = null },
            title = { Text("Restaurer cette sauvegarde ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sauvegarde du ${formatCreated(b.createdAt)}.")
                    Text(
                        if (b.state.programs.isEmpty()) "Aucun programme."
                        else b.state.programs.joinToString("\n") { "• ${it.plan.name} (depuis le ${LocalDate.parse(it.startDate).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH))})" } +
                            "\n${b.doneExercises} exercices faits au total.",
                    )
                    Text(
                        "Tout ce qu'il y a dans l'appli maintenant sera remplacé (programmes, suivi, réglages, pseudo), puis l'appli redémarre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = {
                        restoring = true
                        scope.launch {
                            try {
                                backup.restore(b)
                                restartApp(context)
                            } catch (e: Exception) {
                                restoring = false
                                pending = null
                                error = "Restauration impossible : ${e.message.orEmpty()}"
                            }
                        }
                    },
                ) { Text(if (restoring) "Restauration…" else "Restaurer") }
            },
            dismissButton = { TextButton(enabled = !restoring, onClick = { pending = null }) { Text("Annuler") } },
        )
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Restauration") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
    return { picker.launch(openTypes) }
}

/** Section « Sauvegarde » des réglages. */
@Composable
fun BackupSection(backup: BackupManager, settings: AppSettings, row: @Composable (title: String, subtitle: String, onClick: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri by settings.backupUri.collectAsStateWithLifecycle()
    val last by settings.backupLast.collectAsStateWithLifecycle()
    val failure by settings.backupError.collectAsStateWithLifecycle()
    var manage by remember { mutableStateOf(false) }

    val choose = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { picked ->
        picked?.let(backup::chooseFile)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { picked ->
        if (picked != null) {
            scope.launch {
                val ok = try {
                    backup.write(picked)
                    true
                } catch (_: Exception) {
                    false
                }
                Toast.makeText(context, if (ok) "Sauvegarde exportée" else "Export impossible", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val restore = rememberRestore(backup)

    row(
        "Sauvegarde automatique",
        when {
            uri == null -> "Désactivée. Choisis un fichier (sur le téléphone ou Google Drive) pour l'activer."
            failure != null -> "⚠ Dernier essai raté : $failure"
            last > 0 -> "Active · dernière le ${formatEpoch(last)}"
            else -> "Active"
        },
    ) { if (uri == null) choose.launch(Backup.FILE_NAME) else manage = true }
    row("Exporter maintenant", "Une copie datée, à garder où tu veux") { export.launch("flemmefit-sauvegarde-${LocalDate.now()}.json") }
    row("Restaurer une sauvegarde", "Reprendre programmes, suivi et réglages depuis un fichier", restore)

    if (manage) {
        AlertDialog(
            onDismissRequest = { manage = false },
            title = { Text("Sauvegarde automatique") },
            text = {
                Text(
                    "Le fichier est réécrit tout seul quelques secondes après chaque changement (exercice fait, saisie, réglage du programme)." +
                        if (last > 0) "\n\nDernière sauvegarde : ${formatEpoch(last)}." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manage = false
                    scope.launch {
                        val ok = backup.autoSave()
                        Toast.makeText(context, if (ok) "Sauvegardé" else "Sauvegarde impossible", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Sauvegarder") }
            },
            dismissButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = { manage = false; choose.launch(Backup.FILE_NAME) }) { Text("Changer de fichier") }
                    TextButton(onClick = { manage = false; backup.stopAuto() }) { Text("Arrêter", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}
