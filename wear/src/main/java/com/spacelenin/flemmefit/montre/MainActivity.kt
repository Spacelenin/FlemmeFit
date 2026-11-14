package com.spacelenin.flemmefit.montre

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import kotlinx.coroutines.launch

/** L'app montre : accueil en boucle, ou la séance en cours si elle tourne. */
class MainActivity : ComponentActivity() {
    /** Poignet baissé : écran basse consommation. */
    private var ambient by mutableStateOf(false)

    private val ambientObserver by lazy {
        AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) { ambient = true }
            override fun onExitAmbient() { ambient = false }
            override fun onUpdateAmbient() {}
        })
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var update by mutableStateOf<Updater.Found?>(null)
    private var lastUpdateCheck = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        lifecycle.addObserver(ambientObserver)
        // Une séance d'un autre jour ne reprend pas (sauf son récap).
        Store.run.value?.let { if (it.date != Store.todayDate() && it.phase != Phase.RECAP) Store.setRun(null) }

        setContent {
            val today by Store.today.collectAsState()
            val demo by Store.demo.collectAsState()
            val run by Store.run.collectAsState()
            val battery by Store.battery.collectAsState()
            val updating by Updater.state.collectAsState()
            val scope = rememberCoroutineScope()
            // Gardée d'une image à l'autre : la démo n'est pas recréée à chaque rafraîchissement.
            val demoShown = demo || run?.demo == true
            val shown = remember(today, demoShown) { Store.shown(today, demoShown) }
            val night = battery < NIGHT_BELOW

            // Écran gardé allumé tant qu'une séance tourne, ou qu'une mise à jour se télécharge (luminosité au minimum).
            val busyUpdate = updating is Updater.State.Downloading || updating is Updater.State.Installing || updating is Updater.State.Receiving
            LaunchedEffect(run?.phase, busyUpdate) {
                if ((run != null && run?.phase != Phase.RECAP) || busyUpdate) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply {
                    screenBrightness = if (busyUpdate) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }

            Themed(night, ctxFor(shown?.mode ?: WatchToday.MODE_NORMAL)) {
                val current = run
                if (current != null && shown != null) {
                    SessionScreen(current, shown, ambient)
                } else {
                    HomePager(
                        today = shown,
                        update = update,
                        updating = updating,
                        onStart = {
                            if (shown != null) {
                                askNotificationsOnce()
                                SessionFlow.start(this, shown, demo = shown.session < 0 || demo)
                            }
                        },
                        onDemo = { Store.setDemo(true) },
                        onUpdate = {
                            update?.let { found -> Updater.install(this@MainActivity, found) }
                        },
                        onCheck = { lifecycleScope.launch { update = Updater.checkNow(this@MainActivity) } },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PhoneLink.refresh(this)
        // Les mises à jour : vérifiées au plus toutes les 6 h, jamais en 4G.
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheck > 6 * 3600_000L) {
            lastUpdateCheck = now
            lifecycleScope.launch { update = Updater.check(this@MainActivity) }
        }
    }

    private fun askNotificationsOnce() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
