package com.spacelenin.flemmefit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.reminders.ReminderScheduler

private val TimePresets = listOf(7 * 60 + 30, 12 * 60, 18 * 60, 20 * 60)

fun Int.asClock() = "%02d:%02d".format(this / 60, this % 60)

@Composable
fun RemindersDialog(reminders: ReminderScheduler, programs: List<StoredProgram>, onDismiss: () -> Unit) {
    val settings by reminders.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) } // "daily" ou "evening"
    var denied by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) reminders.update(settings.copy(enabled = true))
    }

    fun setEnabled(on: Boolean) {
        if (on && !reminders.canNotify() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            reminders.update(settings.copy(enabled = on))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rappels") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow("Me rappeler de bosser", settings.enabled, ::setEnabled)
                if (denied) {
                    Text(
                        "Notifications refusées : autorise-les dans les réglages Android de FlemmeFit.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (settings.enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rappel du jour", Modifier.weight(1f))
                        OutlinedButton(onClick = { editing = "daily" }) { Text(settings.dailyMinutes.asClock()) }
                    }
                    SwitchRow("Relance du soir si pas fini", settings.eveningEnabled) {
                        reminders.update(settings.copy(eveningEnabled = it))
                    }
                    if (settings.eveningEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Heure de la relance", Modifier.weight(1f))
                            OutlinedButton(onClick = { editing = "evening" }) { Text(settings.eveningMinutes.asClock()) }
                        }
                    }
                    if (programs.size > 1) {
                        HorizontalDivider()
                        Text("Programmes concernés", style = MaterialTheme.typography.labelLarge)
                        programs.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = p.id !in settings.mutedPrograms,
                                    onCheckedChange = { on ->
                                        val muted = if (on) settings.mutedPrograms - p.id else settings.mutedPrograms + p.id
                                        reminders.update(settings.copy(mutedPrograms = muted))
                                    },
                                )
                                Text(p.plan.name)
                            }
                        }
                    }
                    HorizontalDivider()
                    OutlinedButton(onClick = reminders::test, modifier = Modifier.fillMaxWidth()) { Text("Envoyer une notification de test") }
                    Text(
                        "Pas de rappel les jours de repos. Avec un pseudo dans Potes, le rappel dit aussi qui a déjà fini sa séance. Si rien n'arrive sur un Samsung : Réglages › Applications › " +
                            "FlemmeFit › Batterie › « Non restreinte ».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )

    editing?.let { which ->
        val current = if (which == "daily") settings.dailyMinutes else settings.eveningMinutes
        TimeSelectDialog(
            initialMinutes = current,
            onSelect = { m ->
                reminders.update(if (which == "daily") settings.copy(dailyMinutes = m) else settings.copy(eveningMinutes = m))
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimeSelectDialog(initialMinutes: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePicker(state = state)
                // Raccourcis : un appui suffit.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    TimePresets.forEach { m ->
                        SuggestionChip(
                            onClick = {
                                onSelect(m)
                                onDismiss()
                            },
                            label = { Text(m.asClock()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
