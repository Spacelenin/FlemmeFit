package com.spacelenin.flemmefit.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.spacelenin.flemmefit.update.AppUpdater
import com.spacelenin.flemmefit.update.AppUpdater.State

/**
 * Mise à jour obligatoire : dès qu'une version plus récente est trouvée, cette fenêtre bloque l'appli
 * jusqu'à l'installation. Refuser ferme l'appli. Sans connexion, rien n'est trouvé et l'appli reste utilisable.
 */
@Composable
fun UpdateGate(updater: AppUpdater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val quit = { (context as? Activity)?.finishAndRemoveTask(); Unit }
    // Ni retour ni toucher à côté : on installe ou on quitte.
    val blocking = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

    val release = when (val s = state) {
        is State.Available -> s.release
        is State.Downloading -> s.release
        is State.Failed -> s.release
        else -> null
    }
    if (release == null && state != State.Installing) return

    AlertDialog(
        onDismissRequest = {},
        properties = blocking,
        title = { Text("Mise à jour obligatoire") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = state) {
                    is State.Available -> {
                        Text("${s.release.name} est disponible (tu as le build ${updater.currentVersion}). Installe-la pour continuer.")
                        s.release.notes?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        }
                        if (!updater.canInstall()) {
                            Text(
                                "Android va d'abord demander d'autoriser FlemmeFit à installer des applis (une seule fois) : " +
                                    "active « Autoriser cette source », puis reviens ici.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is State.Downloading -> {
                        Text("Téléchargement de ${s.release.name}… ${(s.progress * 100).toInt()} %")
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    State.Installing -> Text("Installation… confirme dans la fenêtre d'Android.")
                    is State.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is State.Available, is State.Failed -> TextButton(onClick = {
                    val r = release ?: return@TextButton
                    if (updater.canInstall()) updater.install(r) else context.startActivity(updater.installPermissionIntent())
                }) {
                    Text(if (state is State.Failed) "Réessayer" else "Installer")
                }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = quit) { Text("Quitter") } },
    )
}

/** Retour d'une vérification lancée à la main (Réglages) : à jour, ou erreur. S'efface tout seul. */
@Composable
fun UpdateBanner(updater: AppUpdater, modifier: Modifier = Modifier) {
    val state by updater.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val s = state
        if (s == State.UpToDate || (s is State.Failed && s.release == null)) {
            delay(5_000)
            updater.clearMessage()
        }
    }

    when (val s = state) {
        State.Checking -> BannerCard(modifier) {
            Text("Recherche de mise à jour…", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        State.UpToDate -> BannerCard(modifier) {
            Text("FlemmeFit est à jour (build ${updater.currentVersion}).", style = MaterialTheme.typography.bodyMedium)
        }
        is State.Failed -> if (s.release == null) {
            BannerCard(modifier) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        // Une version trouvée passe par la fenêtre obligatoire.
        else -> {}
    }
}

@Composable
private fun BannerCard(modifier: Modifier, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

/** Juste après une mise à jour : ce qui a changé, une seule fois. */
@Composable
fun ChangelogDialog(updater: AppUpdater) {
    val release by updater.changelog.collectAsStateWithLifecycle()
    val r = release ?: return
    AlertDialog(
        onDismissRequest = updater::markChangelogSeen,
        title = { Text("Quoi de neuf · build ${r.versionCode}") },
        text = {
            Text(
                r.notes.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = updater::markChangelogSeen) { Text("Super") } },
    )
}
