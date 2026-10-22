package com.spacelenin.flemmefit.social

import android.content.Context
import android.util.Log
import com.spacelenin.flemmefit.BuildConfig
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.FriendCard
import com.spacelenin.flemmefit.model.Social
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.UUID

/**
 * Page « Les potes » sans serveur à nous : chaque app publie sa carte dans un canal ntfy.sh
 * (relais public gratuit, sans compte) et lit celles des autres. ntfy ne garde les messages que 12 h :
 * chaque app conserve donc la dernière carte connue de chacun.
 */
@OptIn(FlowPreview::class)
class SocialSync(context: Context, private val repository: ProgramRepository) {

    sealed interface Status {
        data object Idle : Status
        data object Loading : Status
        data class Failed(val message: String) : Status
    }

    private val prefs = context.getSharedPreferences("social", Context.MODE_PRIVATE)
    // Filet de sécurité : une erreur imprévue en arrière-plan est journalisée au lieu de faire planter l'appli.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Erreur en arrière-plan", e) })
    private val lock = Mutex()

    /** Identifiant aléatoire de cette installation : c'est lui, pas le pseudo, qui distingue les joueurs. */
    val myId: String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_ID, it).apply()
    }

    private val _pseudo = MutableStateFlow(prefs.getString(KEY_PSEUDO, null))
    val pseudo: StateFlow<String?> = _pseudo.asStateFlow()

    private val _topic = MutableStateFlow(prefs.getString(KEY_TOPIC, null) ?: DEFAULT_TOPIC)
    val topic: StateFlow<String> = _topic.asStateFlow()

    private val _friends = MutableStateFlow(Social.decodeAll(prefs.getString(KEY_CACHE, null) ?: "[]"))
    /** Cartes reçues (la mienne exclue), dernière version connue de chacun. */
    val friends: StateFlow<List<FriendCard>> = _friends.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    init {
        // Republie quand la progression change ; le délai regroupe les cases cochées à la suite.
        scope.launch {
            repository.state.drop(1).debounce(PUBLISH_DEBOUNCE_MS).collect { publish(force = false) }
        }
    }

    fun myCard(today: LocalDate = LocalDate.now()): FriendCard? = _pseudo.value?.let {
        Social.card(myId, it, repository.state.value.programs, today, System.currentTimeMillis(), BuildConfig.VERSION_CODE)
    }

    fun setPseudo(value: String) {
        val clean = value.trim().take(FriendCard.PSEUDO_MAX).ifEmpty { return }
        prefs.edit().putString(KEY_PSEUDO, clean).apply()
        _pseudo.value = clean
        scope.launch { publish(force = true) }
    }

    fun setTopic(value: String) {
        val clean = value.trim().lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
        if (clean.length < 6 || clean == _topic.value) return
        prefs.edit().putString(KEY_TOPIC, clean).remove(KEY_CACHE).remove(KEY_LAST).apply()
        _topic.value = clean
        _friends.value = emptyList()
        refresh()
    }

    /** Au lancement : republie seulement si la carte a changé ou date de plus de 6 h. */
    fun publishIfNeeded() {
        scope.launch { publish(force = false) }
    }

    /** Publie ma carte si besoin, puis lit le canal. */
    fun refresh() {
        scope.launch {
            _status.value = Status.Loading
            _status.value = try {
                publish(force = false)
                val fresh = Social.parsePoll(httpGet("$BASE/${encoded()}/json?poll=1&since=12h"))
                    .filter { it.id != myId }
                val merged = Social.merge(_friends.value, fresh)
                prefs.edit().putString(KEY_CACHE, Social.encodeAll(merged)).apply()
                _friends.value = merged
                Status.Idle
            } catch (e: Exception) {
                Log.w(TAG, "Canal injoignable", e)
                Status.Failed("Canal injoignable (${e.message.orEmpty()}). Les dernières cartes connues restent affichées.")
            }
        }
    }

    /**
     * N'envoie que si la carte a changé, ou si la dernière publication a plus de 6 h
     * (pour rester dans les 12 h gardées par ntfy). Économise le quota quotidien du relais.
     */
    private suspend fun publish(force: Boolean) = lock.withLock {
        val card = myCard() ?: return@withLock
        val last = prefs.getString(KEY_LAST, null)?.let { Social.decodeAll("[$it]").firstOrNull() }
        val lastAt = prefs.getLong(KEY_LAST_AT, 0)
        val stale = System.currentTimeMillis() - lastAt > REPUBLISH_AFTER_MS
        if (!force && card.sameAs(last) && !stale) return@withLock
        try {
            val body = Social.encode(card)
            httpPost("$BASE/${encoded()}", body)
            prefs.edit().putString(KEY_LAST, body).putLong(KEY_LAST_AT, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Publication impossible", e)
        }
    }

    private fun encoded() = URLEncoder.encode(_topic.value, "UTF-8")

    private fun httpGet(url: String): String = connect(url).run {
        try {
            if (responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP $responseCode")
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }

    private fun httpPost(url: String, body: String) = connect(url).run {
        try {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            outputStream.use { it.write(body.toByteArray()) }
            if (responseCode !in 200..299) throw IOException("HTTP $responseCode")
        } finally {
            disconnect()
        }
    }

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "FlemmeFit/${BuildConfig.VERSION_NAME}")
    }

    companion object {
        private const val TAG = "SocialSync"
        private const val BASE = "https://ntfy.sh"
        /** Canal commun par défaut : tous ceux qui l'utilisent se voient. */
        const val DEFAULT_TOPIC = "flemmefit-potes-q7m2xk9v"
        private const val KEY_ID = "id"
        private const val KEY_PSEUDO = "pseudo"
        private const val KEY_TOPIC = "topic"
        private const val KEY_CACHE = "cache"
        private const val KEY_LAST = "lastPublished"
        private const val KEY_LAST_AT = "lastPublishedAt"
        private const val PUBLISH_DEBOUNCE_MS = 20_000L
        private const val REPUBLISH_AFTER_MS = 6 * 60 * 60 * 1000L
    }
}
