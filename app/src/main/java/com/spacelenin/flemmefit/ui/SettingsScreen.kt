package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.data.AppSettings
import com.spacelenin.flemmefit.data.BackupManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.spacelenin.flemmefit.model.Counter
import com.spacelenin.flemmefit.model.Measures
import com.spacelenin.flemmefit.model.Prescription
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.reminders.ReminderScheduler
import com.spacelenin.flemmefit.social.SocialSync
import com.spacelenin.flemmefit.ui.theme.AppTheme
import com.spacelenin.flemmefit.update.AppUpdater

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    backup: BackupManager,
    reminders: ReminderScheduler,
    updater: AppUpdater,
    social: SocialSync,
    programs: List<StoredProgram>,
    onSocial: () -> Unit,
    onTutorial: () -> Unit,
    onBack: () -> Unit,
) {
    val theme by settings.theme.collectAsStateWithLifecycle()
    val restBonus by settings.restBonus.collectAsStateWithLifecycle()
    val countersUnlocked by settings.countersUnlocked.collectAsStateWithLifecycle()
    val reminderSettings by reminders.settings.collectAsStateWithLifecycle()
    val pseudo by social.pseudo.collectAsStateWithLifecycle()
    var showReminders by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Section("APPARENCE")
                Text(
                    "Thème : ${theme.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    AppTheme.entries.forEach { t -> ThemeSwatch(t, selected = t == theme, onClick = { settings.setTheme(t) }) }
                }

                Section("SÉANCE")
                Text("Repos en plus", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                Text(
                    "Pour tous les exercices, jamais moins que prévu ni plus du double." +
                        if (restBonus > 0) " Ex. 1min30 → ${Measures.formatSeconds(Prescription.withRestBonus(90, restBonus))}." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    listOf(0, 25, 50, 75, 100).forEach { percent ->
                        FilterChip(
                            selected = restBonus == percent,
                            onClick = { settings.setRestBonus(percent) },
                            label = { Text(if (percent == 0) "Prévu" else "+$percent %") },
                        )
                    }
                }

                Section("RAPPELS")
                SettingRow(
                    title = "Rappels",
                    subtitle = if (reminderSettings.enabled) {
                        "Tous les jours d'entraînement à ${reminderSettings.dailyMinutes.asClock()}" +
                            if (reminderSettings.eveningEnabled) ", relance à ${reminderSettings.eveningMinutes.asClock()}" else ""
                    } else {
                        "Désactivés"
                    },
                    onClick = { showReminders = true },
                )

                Section("SAUVEGARDE")
                BackupSection(backup, settings) { title, subtitle, onClick -> SettingRow(title, subtitle, onClick) }

                Section("MISES À JOUR")
                UpdateBanner(updater)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Version installée", style = MaterialTheme.typography.bodyLarge)
                        Text("Build ${updater.currentVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { updater.check() }) { Text("Vérifier") }
                }

                Section("LES POTES")
                SettingRow(title = "Pseudo et canal", subtitle = pseudo ?: "Pas encore choisi", onClick = onSocial)

                Section("AIDE")
                SettingRow(title = "Comment marche l'appli", subtitle = "Le petit tour en six écrans", onClick = onTutorial)

                // Juste au-dessus du champ secret : ouvrir les compteurs les montre là où on regarde.
                if (countersUnlocked) {
                    Section("COMPTEURS 🔓")
                    val counters = remember(programs) { countersOf(programs, java.time.LocalDate.now()) }
                    if (counters.isEmpty()) {
                        Text(
                            "Rien à compter pour l'instant : chaque exercice fait viendra s'ajouter ici.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    counters.forEach { CounterRow(it) }
                }

                // Un champ sans rien autour. Le bon mot ouvre (ou referme) les compteurs.
                var word by remember { mutableStateOf("") }
                val context = LocalContext.current
                OutlinedTextField(
                    value = word,
                    onValueChange = { text ->
                        // On agit quand le mot apparaît, sans vider le champ : le clavier renverrait le mot
                        // en cours de frappe, et ça refermerait aussitôt ce qui vient de s'ouvrir.
                        val before = Counter.isSecretWord(word)
                        word = text.take(20)
                        if (!before && Counter.isSecretWord(word)) {
                            val on = !countersUnlocked
                            settings.setCountersByHand(on)
                            Toast.makeText(context, if (on) "🔓" else "🔒", Toast.LENGTH_SHORT).show()
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally).width(160.dp).padding(top = 32.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (showReminders) RemindersDialog(reminders, programs, onDismiss = { showReminders = false })
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeSwatch(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(theme.accent)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = theme.label
                this.selected = selected
            },
    ) {
        if (selected) Icon(Icons.Default.Check, null, tint = theme.onAccent)
    }
}
