package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.spacelenin.flemmefit.model.ReleaseInfo
import com.spacelenin.flemmefit.update.AppUpdater
import com.spacelenin.flemmefit.update.AppUpdater.State

/** Bandeau de mise à jour en haut de l'accueil ; ne prend aucune place quand il n'y a rien à dire. */
@Composable
fun UpdateBanner(updater: AppUpdater, modifier: Modifier = Modifier, floating: Boolean = false) {
    val state by updater.state.collectAsStateWithLifecycle()
    val dismissed by updater.dismissedVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var askPermission by remember { mutableStateOf<ReleaseInfo?>(null) }

    // « À jour » ou une erreur de vérification s'effacent d'eux-mêmes.
    LaunchedEffect(state) {
        val s = state
        if (s == State.UpToDate || (s is State.Failed && s.release == null)) {
            delay(5_000)
            updater.clearMessage()
        }
    }

    fun install(release: ReleaseInfo) {
        if (updater.canInstall()) updater.install(release) else askPermission = release
    }

    when (val s = state) {
        is State.Available -> if (s.release.versionCode != dismissed) {
            if (floating) {
                // Sur l'accueil : une vraie proposition, par-dessus l'écran.
                AlertDialog(
                    onDismissRequest = { updater.dismiss(s.release) },
                    title = { Text("Nouvelle version disponible") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${s.release.name} (tu as le build ${updater.currentVersion})", style = MaterialTheme.typography.bodyMedium)
                            s.release.notes?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { install(s.release) }) { Text("Installer") } },
                    dismissButton = { TextButton(onClick = { updater.dismiss(s.release) }) { Text("Plus tard") } },
                )
            } else {
                BannerCard(modifier, floating) {
                    Text("Nouvelle version disponible", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${s.release.name} (tu as le build ${updater.currentVersion})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    s.release.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { install(s.release) }) { Text("Installer") }
                        TextButton(onClick = { updater.dismiss(s.release) }) { Text("Plus tard") }
                    }
                }
            }
        }
        is State.Downloading -> BannerCard(modifier, floating) {
            Text("Téléchargement de ${s.release.name}… ${(s.progress * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        }
        State.Installing -> BannerCard(modifier, floating) {
            Text("Installation… confirme dans la fenêtre d'Android.", style = MaterialTheme.typography.bodyMedium)
        }
        State.Checking -> BannerCard(modifier, floating) {
            Text("Recherche de mise à jour…", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        State.UpToDate -> BannerCard(modifier, floating) {
            Text("FlemmeFit est à jour (build ${updater.currentVersion}).", style = MaterialTheme.typography.bodyMedium)
        }
        is State.Failed -> BannerCard(modifier, floating) {
            Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            s.release?.let { r -> TextButton(onClick = { install(r) }) { Text("Réessayer") } }
        }
        State.Idle -> {}
    }

    askPermission?.let { release ->
        AlertDialog(
            onDismissRequest = { askPermission = null },
            title = { Text("Autoriser les mises à jour") },
            text = {
                Text(
                    "Android doit autoriser FlemmeFit à installer des applications (une seule fois). " +
                        "Active « Autoriser cette source », reviens ici et appuie à nouveau sur Installer.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = null
                    context.startActivity(updater.installPermissionIntent())
                }) { Text("Ouvrir les réglages") }
            },
            dismissButton = { TextButton(onClick = { askPermission = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun BannerCard(modifier: Modifier, floating: Boolean, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (floating) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (floating) 8.dp else 0.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}
