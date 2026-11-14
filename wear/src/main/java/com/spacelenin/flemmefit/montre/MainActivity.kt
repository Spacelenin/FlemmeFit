package com.spacelenin.flemmefit.montre

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/** Écran de test : version installée, droit d'installer, et bouton de mise à jour avec son journal. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun installedVersion(): Long =
        packageManager.getPackageInfo(packageName, 0).longVersionCode

    @Composable
    private fun TestScreen() {
        val scope = rememberCoroutineScope()
        val log by Updater.log.collectAsState()
        // Relu à chaque action : le droit d'installer peut changer pendant le test.
        val tick = remember { mutableIntStateOf(0) }
        val canInstall = remember(tick.intValue) { packageManager.canRequestPackageInstalls() }

        fun update(force: Boolean) = scope.launch {
            tick.intValue++
            try {
                Updater.note("Recherche…")
                val found = Updater.latest()
                val current = installedVersion()
                when {
                    found == null -> Updater.note("Aucune version montre publiée.")
                    found.version <= current && !force -> Updater.note("À jour (montre-${found.version}).")
                    else -> {
                        Updater.note("Trouvé montre-${found.version}, téléchargement…")
                        Updater.install(this@MainActivity, found)
                    }
                }
            } catch (e: Exception) {
                Updater.note("❌ ${e.javaClass.simpleName} : ${e.message.orEmpty()}")
            }
        }

        ScalingLazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(
                    "FlemmeFit montre",
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF8FD25E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Version ${installedVersion()} · Wear API ${Build.VERSION.SDK_INT}\nDroit d'installer : ${if (canInstall) "oui" else "non"}",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { update(force = false) },
                    label = { Text("Chercher une maj") },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF4F9F27)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { update(force = true) },
                    label = { Text("Forcer (même version)") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = {
                        tick.intValue++
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                        try {
                            startActivity(intent)
                            Updater.note("Réglage « sources inconnues » ouvert.")
                        } catch (e: Exception) {
                            Updater.note("Pas d'écran « sources inconnues » sur cette montre.")
                        }
                    },
                    label = { Text("Autoriser l'installation") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(log.reversed()) { line ->
                Text(line, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Retour du réglage : on relit le droit d'installer.
        setContent { MaterialTheme { TestScreen() } }
    }
}
