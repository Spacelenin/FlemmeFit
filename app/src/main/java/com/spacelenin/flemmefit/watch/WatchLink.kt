package com.spacelenin.flemmefit.watch

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.spacelenin.flemmefit.FlemmeFitApp
import com.spacelenin.flemmefit.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Liaison avec l'app montre, en Bluetooth uniquement : on n'envoie qu'à une montre « à portée ». Sans montre
 * (la plupart des potes), rien ne se passe : pas de montre, pas d'envoi.
 */
class WatchLink(private val app: FlemmeFitApp) {
    private val prefs = app.getSharedPreferences("watch", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Dernier état publié par la montre (null : jamais vu de montre). */
    private val _status = MutableStateFlow(prefs.getString(KEY_STATUS, null)?.let { runCatching { WatchJson.decodeFromString(WatchStatus.serializer(), it) }.getOrNull() })
    val status: StateFlow<WatchStatus?> = _status.asStateFlow()

    /** Montre reliée en Bluetooth en ce moment. */
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Heure du dernier envoi de la séance du jour, et du dernier retour reçu de la montre. */
    private val _lastSent = MutableStateFlow(prefs.getLong(KEY_SENT, 0))
    val lastSent: StateFlow<Long> = _lastSent.asStateFlow()
    private val _lastReceived = MutableStateFlow(prefs.getLong(KEY_RECEIVED, 0))
    val lastReceived: StateFlow<Long> = _lastReceived.asStateFlow()

    /** Dernière version montre publiée (null : pas encore vérifié). */
    private val _latest = MutableStateFlow<Int?>(null)
    val latest: StateFlow<Int?> = _latest.asStateFlow()

    sealed interface Push {
        data object Idle : Push
        /** [percent] de l'étape en cours, [kbPerSecond] son débit. */
        data class Running(val version: Int, val step: String, val percent: Int? = null, val kbPerSecond: Int? = null) : Push
        data class Done(val version: Int) : Push
        data class Failed(val message: String) : Push
    }

    private val _push = MutableStateFlow<Push>(Push.Idle)
    val push: StateFlow<Push> = _push.asStateFlow()

    private suspend fun nearbyWatch(): String? = try {
        Wearable.getNodeClient(app).connectedNodes.await().firstOrNull { it.isNearby }?.id
    } catch (e: Exception) {
        // Pas de services montre sur ce téléphone : aucune montre possible.
        null
    }.also { _connected.value = it != null }

    /** Envoie la séance du jour à la montre, si elle est là. */
    fun publish() {
        scope.launch {
            lock.withLock {
                try {
                    nearbyWatch() ?: return@withLock
                    val today = LocalDate.now()
                    val payload = WatchBuilder.today(
                        programs = app.repository.state.value.programs,
                        today = today,
                        restBonus = app.settings.restBonus.value,
                        friends = app.social.friends.value,
                        me = app.social.myCard(today),
                        myId = app.social.myId,
                    )
                    val request = PutDataMapRequest.create(WatchPaths.TODAY).apply {
                        dataMap.putString(WatchPaths.KEY_JSON, WatchJson.encodeToString(WatchToday.serializer(), payload))
                    }.asPutDataRequest().setUrgent()
                    Wearable.getDataClient(app).putDataItem(request).await()
                    val now = System.currentTimeMillis()
                    _lastSent.value = now
                    prefs.edit().putLong(KEY_SENT, now).apply()
                } catch (e: Exception) {
                    Log.w(TAG, "Envoi à la montre impossible", e)
                }
            }
        }
    }

    /** Une saisie de la montre : enregistrée comme si elle avait été faite sur le téléphone, puis retirée. */
    fun onLog(json: String, uri: Uri) {
        val log = runCatching { WatchJson.decodeFromString(WatchLog.serializer(), json) }.getOrNull()
        if (log != null) {
            val repo = app.repository
            when (val applied = WatchBuilder.apply(log, repo.state.value.programs, app.settings.restBonus.value)) {
                is WatchBuilder.Applied.Exercise -> repo.saveLog(applied.programId, applied.dayIndex, applied.exerciseIndex, applied.log, applied.complete)
                is WatchBuilder.Applied.Feeling -> repo.setFeeling(applied.programId, applied.dayIndex, applied.feeling)
                is WatchBuilder.Applied.ExerciseFeeling ->
                    repo.setExerciseFeeling(applied.programId, applied.dayIndex, applied.exerciseIndex, applied.feeling)
                is WatchBuilder.Applied.Check -> {
                    // Ne bascule que si l'état diffère : un message reçu deux fois ne décoche pas.
                    val p = repo.state.value.programs.firstOrNull { it.id == applied.programId }
                    if (p != null && p.isDone(applied.dayIndex, applied.exerciseIndex) != applied.done) {
                        repo.toggleExercise(applied.programId, applied.dayIndex, applied.exerciseIndex)
                    }
                }
                null -> Unit
            }
            val now = System.currentTimeMillis()
            _lastReceived.value = now
            prefs.edit().putLong(KEY_RECEIVED, now).apply()
        }
        scope.launch { runCatching { Wearable.getDataClient(app).deleteDataItems(uri).await() } }
    }

    fun onStatus(json: String) {
        val status = runCatching { WatchJson.decodeFromString(WatchStatus.serializer(), json) }.getOrNull() ?: return
        _status.value = status
        _connected.value = true
        prefs.edit().putString(KEY_STATUS, json).apply()
        // Montre plus ancienne que la dernière version : le téléphone lui envoie la mise à jour, une fois par version.
        scope.launch {
            val latest = _latest.value ?: checkLatest()
            if (latest != null && status.version < latest && prefs.getInt(KEY_AUTO_PUSHED, 0) < latest) {
                prefs.edit().putInt(KEY_AUTO_PUSHED, latest).apply()
                pushUpdate()
            }
        }
    }

    /** Relit la dernière séance reçue par la montre et l'état publié : au démarrage et au retour dans les réglages. */
    fun refresh() {
        scope.launch {
            nearbyWatch()
            try {
                val items = Wearable.getDataClient(app).dataItems.await()
                try {
                    items.forEach { item ->
                        val json = DataMapItem.fromDataItem(item).dataMap.getString(WatchPaths.KEY_JSON) ?: return@forEach
                        when {
                            item.uri.path == WatchPaths.STATUS -> onStatus(json)
                            item.uri.path?.startsWith(WatchPaths.LOG) == true -> onLog(json, item.uri)
                        }
                    }
                } finally {
                    items.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Montre injoignable", e)
            }
            checkLatest()
        }
    }

    /** Dernière version montre publiée dans le repo des releases. */
    suspend fun checkLatest(): Int? = withContext(Dispatchers.IO) {
        try {
            val releases = JSONArray(get(LIST_URL))
            var best: Int? = null
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                val v = MONTRE_TAG.matchEntire(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                if (best == null || v > best) best = v
            }
            _latest.value = best
            best
        } catch (e: Exception) {
            null
        }
    }

    /** Télécharge la dernière version montre et l'envoie à la montre en Bluetooth ; la montre l'installe. */
    fun pushUpdate() {
        if (_push.value is Push.Running) return
        scope.launch {
            try {
                val node = nearbyWatch() ?: throw IOException("Montre pas à portée")
                val version = checkLatest() ?: throw IOException("Version montre introuvable")
                _push.value = Push.Running(version, "téléchargement")
                val url = "https://github.com/${AppUpdater.REPOSITORY}/releases/download/montre-$version/FlemmeFit-montre-$version.apk"
                val apk = open(url).let { c ->
                    try {
                        if (c.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${c.responseCode}")
                        val total = c.contentLengthLong
                        val out = java.io.ByteArrayOutputStream()
                        c.inputStream.use { input -> copyWithProgress(input, out, total) { p, k -> _push.value = Push.Running(version, "téléchargement", p, k) } }
                        out.toByteArray()
                    } finally {
                        c.disconnect()
                    }
                }
                // La taille d'abord : la montre affiche sa progression pendant la réception.
                runCatching {
                    Wearable.getMessageClient(app).sendMessage(node, WatchPaths.APK_SIZE, apk.size.toString().toByteArray()).await()
                }
                _push.value = Push.Running(version, "envoi à la montre", 0)
                val channels = Wearable.getChannelClient(app)
                val channel = channels.openChannel(node, WatchPaths.APK).await()
                try {
                    channels.getOutputStream(channel).await().use { output ->
                        copyWithProgress(apk.inputStream(), output, apk.size.toLong()) { p, k -> _push.value = Push.Running(version, "envoi à la montre", p, k) }
                    }
                } finally {
                    runCatching { channels.close(channel).await() }
                }
                _push.value = Push.Done(version)
            } catch (e: Exception) {
                Log.w(TAG, "Mise à jour de la montre impossible", e)
                _push.value = Push.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Copie par blocs en annonçant le pourcentage et le débit (mesuré à peu près chaque seconde). */
    private fun copyWithProgress(input: java.io.InputStream, output: java.io.OutputStream, total: Long, onProgress: (Int?, Int?) -> Unit) {
        val buffer = ByteArray(16 * 1024)
        var done = 0L
        var windowStart = System.currentTimeMillis()
        var windowBytes = 0L
        var speed: Int? = null
        var lastPercent = -1
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            done += n
            windowBytes += n
            val now = System.currentTimeMillis()
            val tick = now - windowStart >= 1000
            if (tick) {
                speed = (windowBytes * 1000 / (now - windowStart) / 1024).toInt()
                windowStart = now
                windowBytes = 0
            }
            val percent = if (total > 0) (done * 100 / total).toInt() else null
            if (percent != lastPercent || tick) {
                lastPercent = percent ?: -1
                onProgress(percent, speed)
            }
        }
        output.flush()
    }

    private fun get(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "FlemmeFit")
        }

    companion object {
        private const val TAG = "WatchLink"
        private const val KEY_STATUS = "status"
        private const val KEY_SENT = "sent"
        private const val KEY_RECEIVED = "received"
        private const val KEY_AUTO_PUSHED = "autoPushed"
        private const val LIST_URL = "https://api.github.com/repos/${AppUpdater.REPOSITORY}/releases?per_page=30"
        private val MONTRE_TAG = Regex("""^montre-(\d+)$""")
    }
}
