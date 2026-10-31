package com.spacelenin.flemmefit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.update.AppUpdater

/**
 * Lancement après un plantage : le rapport à envoyer, avant l'appli elle-même (qui pourrait replanter au même
 * endroit). Si une version plus récente existe, elle se propose par-dessus : c'est souvent la correction.
 */
@Composable
fun CrashScreen(report: String, updater: AppUpdater, onContinue: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 560.dp),
        ) {
            Text("😵", style = MaterialTheme.typography.displayMedium)
            Text("FlemmeFit a planté", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                "Désolé ! Tes séances ne sont pas touchées : ne désinstalle surtout pas l'appli. " +
                    "Envoie ce rapport au dév pour qu'il corrige. Il ne contient que l'erreur technique et le modèle du téléphone.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = { share(context, report) }, modifier = Modifier.fillMaxWidth()) { Text("Envoyer le rapport") }
            FilledTonalButton(
                onClick = {
                    copy(context, report)
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (copied) "Rapport copié ✓" else "Copier le rapport") }
            TextButton(onClick = onContinue) { Text("Continuer vers l'appli") }
            Text(
                report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 30,
            )
        }
    }
    // Une mise à jour trouvée (vérifiée à chaque lancement) s'installe d'ici, sans passer par l'appli.
    UpdateGate(updater)
}

private fun share(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "FlemmeFit : rapport de plantage")
        .putExtra(Intent.EXTRA_TEXT, report)
    context.startActivity(Intent.createChooser(send, "Envoyer le rapport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copy(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Rapport FlemmeFit", report))
}
