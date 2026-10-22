package com.spacelenin.flemmefit

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spacelenin.flemmefit.ui.AppRoot
import com.spacelenin.flemmefit.ui.ImportSource
import com.spacelenin.flemmefit.ui.theme.FlemmeFitTheme

class MainActivity : ComponentActivity() {

    /** Programme reçu via « Ouvrir avec » ou « Partager ». */
    private var incoming by mutableStateOf<ImportSource?>(null)

    /** Programme à ouvrir, demandé par une notification. */
    private var openRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // L'appli est toujours en sombre : icônes claires dans les barres système.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            incoming = intent.toImportSource()
            openRequest = intent.getStringExtra(EXTRA_PROGRAM_ID)
        }

        val app = application as FlemmeFitApp
        setContent {
            val theme by app.settings.theme.collectAsStateWithLifecycle()
            FlemmeFitTheme(theme) {
                AppRoot(
                    repository = app.repository,
                    updater = app.updater,
                    reminders = app.reminders,
                    social = app.social,
                    settings = app.settings,
                    backup = app.backup,
                    incoming = incoming,
                    onIncomingConsumed = { incoming = null },
                    openRequest = openRequest,
                    onOpenRequestConsumed = { openRequest = null },
                )
            }
        }
    }

    /** À chaque retour au premier plan (lancement compris) : y a-t-il une nouvelle version ? */
    override fun onStart() {
        super.onStart()
        val updater = (application as FlemmeFitApp).updater
        updater.loadChangelogIfUpdated()
        updater.autoCheck()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toImportSource()?.let { incoming = it }
        intent.getStringExtra(EXTRA_PROGRAM_ID)?.let { openRequest = it }
    }

    private fun Intent.toImportSource(): ImportSource? = when (action) {
        Intent.ACTION_VIEW -> data?.let { ImportSource.File(it) }
        Intent.ACTION_SEND -> streamUri()?.let { ImportSource.File(it) }
            ?: getStringExtra(Intent.EXTRA_TEXT)?.let { ImportSource.Text(it) }
        else -> null
    }

    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    companion object {
        const val EXTRA_PROGRAM_ID = "com.spacelenin.flemmefit.PROGRAM_ID"
    }
}
