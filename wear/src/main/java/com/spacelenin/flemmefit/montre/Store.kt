package com.spacelenin.flemmefit.montre

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.util.UUID

/** Démarre le magasin dès le lancement du processus (app, service, réception Bluetooth). */
class MontreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}

/** Étapes d'une séance sur la montre. */
object Phase {
    const val EXERCISE = "exercise"
    const val INPUT = "input"
    const val REST = "rest"
    const val NEXT = "next"
    const val FEELING = "feeling"
    const val RECAP = "recap"
}

/**
 * Une séance en cours. Tout est daté (heures de départ et de fin) : couper l'app ou éteindre l'écran
 * ne fait rien perdre, les minuteurs se recalculent à partir de l'heure.
 */
@Serializable
data class Run(
    val date: String,
    val session: Int,
    val programId: String = "",
    val demo: Boolean = false,
    val startedAt: Long,
    val startBattery: Int? = null,
    /** Exercices à faire, dans l'ordre (positions dans [WatchToday.exercises]). */
    val queue: List<Int>,
    /** Position courante dans [queue]. */
    val pos: Int = 0,
    /** Série en cours (à partir de 0). */
    val set: Int = 0,
    /** Valeurs des séries déjà faites de l'exo en cours. */
    val values: List<Int> = emptyList(),
    /** Valeur réglée (− / +, lunette) pour la série en cours, pas encore validée. */
    val draft: Int? = null,
    /** Ressenti donné à l'exo précédent sur l'écran « Exo suivant ». */
    val lastFeeling: Int? = null,
    val phase: String = Phase.EXERCISE,
    /** Début de la série en cours (chrono des exos en durée). */
    val phaseStartedAt: Long = startedAt,
    val restEndsAt: Long = 0,
    /** Le repos a déjà fait vibrer à 10 s, puis à 0. */
    val warned: Boolean = false,
    val rang: Boolean = false,
    val done: Int = 0,
    val skipped: Int = 0,
    val feeling: Int? = null,
    val endedAt: Long? = null,
    val endBattery: Int? = null,
) {
    val minutes get() = (((endedAt ?: System.currentTimeMillis()) - startedAt) / 60_000).toInt()
    val batteryUsed get() = if (startBattery != null && endBattery != null) (startBattery - endBattery).coerceAtLeast(0) else null
}

object Store {
    private const val PREFS = "montre"
    private const val KEY_TODAY = "today"
    private const val KEY_RUN = "run"
    private const val KEY_PENDING = "pending"
    private const val KEY_DEMO = "demo"

    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _today = MutableStateFlow<WatchToday?>(null)
    /** La séance du jour envoyée par le téléphone. */
    val today: StateFlow<WatchToday?> = _today

    private val _demo = MutableStateFlow(false)
    /** Démo choisie faute de données du téléphone. */
    val demo: StateFlow<Boolean> = _demo

    private val _run = MutableStateFlow<Run?>(null)
    val run: StateFlow<Run?> = _run

    private val _pending = MutableStateFlow<List<WatchLog>>(emptyList())
    /** Ce qui attend le retour du téléphone à portée. */
    val pending: StateFlow<List<WatchLog>> = _pending

    private val _battery = MutableStateFlow(100)
    val battery: StateFlow<Int> = _battery

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _today.value = prefs.getString(KEY_TODAY, null)?.let { runCatching { WatchJson.decodeFromString(WatchToday.serializer(), it) }.getOrNull() }
        _run.value = prefs.getString(KEY_RUN, null)?.let { runCatching { WatchJson.decodeFromString(Run.serializer(), it) }.getOrNull() }
        _pending.value = prefs.getString(KEY_PENDING, null)
            ?.let { runCatching { WatchJson.decodeFromString(ListSerializer(WatchLog.serializer()), it) }.getOrNull() }.orEmpty()
        _demo.value = prefs.getBoolean(KEY_DEMO, false)
        // Batterie relue chaque minute : fond nuit sous 15 %, récap de fin de séance.
        scope.launch {
            var published = -100
            while (true) {
                _battery.value = readBattery()
                if (kotlin.math.abs(_battery.value - published) >= 5) {
                    published = _battery.value
                    PhoneLink.publishStatus(app)
                }
                delay(60_000)
            }
        }
    }

    fun readBattery(): Int {
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (v in 0..100) return v
        val sticky = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0) level * 100 / scale else 100
    }

    fun todayDate(): String = LocalDate.now().toString()

    /** Ce qu'on montre : la séance du téléphone si elle est du jour, sinon la démo si choisie. */
    fun shown(today: WatchToday?, demo: Boolean): WatchToday? = when {
        today != null && today.date == todayDate() -> today
        demo -> demoToday(todayDate())
        else -> null
    }

    fun setToday(json: String) {
        val parsed = runCatching { WatchJson.decodeFromString(WatchToday.serializer(), json) }.getOrNull() ?: return
        _today.value = parsed
        prefs.edit().putString(KEY_TODAY, json).apply()
        // De vraies données arrivent : la démo s'arrête.
        if (_demo.value) setDemo(false)
    }

    fun setDemo(on: Boolean) {
        _demo.value = on
        prefs.edit().putBoolean(KEY_DEMO, on).apply()
    }

    fun setRun(run: Run?) {
        _run.value = run
        prefs.edit().apply {
            if (run == null) remove(KEY_RUN) else putString(KEY_RUN, WatchJson.encodeToString(Run.serializer(), run))
        }.apply()
    }

    fun updateRun(change: (Run) -> Run) {
        _run.value?.let { setRun(change(it)) }
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** Case cochée ou décochée depuis la liste des exos : envoyée au téléphone (rien en démo). */
    fun check(context: Context, today: WatchToday, ex: WatchExercise, done: Boolean) {
        val current = _today.value
        if (current != null && current.date == today.date && current.session == today.session) {
            val updated = current.copy(exercises = current.exercises.map { if (it.i == ex.i) it.copy(done = done) else it })
            _today.value = updated
            prefs.edit().putString(KEY_TODAY, WatchJson.encodeToString(WatchToday.serializer(), updated)).apply()
        } else {
            return
        }
        send(WatchLog(id = newId(), date = today.date, session = today.session, programId = today.programId, exercise = ex.i, check = done, at = System.currentTimeMillis()))
    }

    /** Ajoute un envoi à la file et tente de l'envoyer tout de suite (Bluetooth seulement). */
    fun send(log: WatchLog) {
        setPending(_pending.value + log)
        PhoneLink.flush(app)
    }

    fun setPending(list: List<WatchLog>) {
        _pending.value = list
        prefs.edit().putString(KEY_PENDING, WatchJson.encodeToString(ListSerializer(WatchLog.serializer()), list)).apply()
    }

    fun version(): Int = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode.toInt()

    /** L'état de la montre, pour l'encart du téléphone. */
    fun status() = WatchStatus(
        version = version(),
        battery = _battery.value,
        pending = _pending.value.size,
        inSession = _run.value?.let { it.phase != Phase.RECAP } == true,
        todayDate = _today.value?.date,
        at = System.currentTimeMillis(),
    )
}
