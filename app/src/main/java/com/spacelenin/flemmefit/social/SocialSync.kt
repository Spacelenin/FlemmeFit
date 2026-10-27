package com.spacelenin.flemmefit.social

import android.content.Context
import android.util.Log
import com.spacelenin.flemmefit.BuildConfig
import com.spacelenin.flemmefit.data.ProgramRepository
import com.spacelenin.flemmefit.model.FriendCard
import com.spacelenin.flemmefit.model.Reminders
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

    private val _friends = MutableStateFlow(
        Social.visible(Social.decodeAll(prefs.getString(KEY_CACHE, null) ?: "[]"), _pseudo.value, System.currentTimeMillis()),
    )
    /** Cartes reçues (la mienne exclue), dernière version connue de chacun. */
    val friends: StateFlow<List<FriendCard>> = _friends.asStateFlow()

    /** Appelé avec les pseudos des potes qui viennent de battre un Boss. */
    @Volatile
    var onBossNews: ((List<String>) -> Unit)? = null

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
        _friends.value = Social.visible(_friends.value, clean, System.currentTimeMillis())
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

    /**
     * Pseudo déjà porté par quelqu'un d'autre ? Lit le canal (délai court) et les cartes gardées.
     * Null si personne, ou si rien n'a pu être vérifié. À appeler hors du fil principal.
     */
    fun pseudoOwner(value: String): FriendCard? {
        val fresh = try {
            Social.parsePoll(httpGet("$BASE/${encoded()}/json?poll=1&since=12h", PSEUDO_TIMEOUT_MS))
        } catch (e: Exception) {
            Log.w(TAG, "Canal injoignable pour vérifier le pseudo", e)
            emptyList()
        }
        val known = Social.decodeAll(prefs.getString(KEY_CACHE, null) ?: "[]")
        return Social.pseudoOwner(known + fresh, value, myId, System.currentTimeMillis())
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
                pull()
                Status.Idle
            } catch (e: Exception) {
                Log.w(TAG, "Canal injoignable", e)
                Status.Failed("Canal injoignable (${e.message.orEmpty()}). Les dernières cartes connues restent affichées.")
            }
        }
    }

    /** Lit le canal et met à jour les cartes connues. [timeoutMs] : délai réseau, plus court pour un rappel. */
    private fun pull(timeoutMs: Int? = null) {
        val fresh = Social.parsePoll(httpGet("$BASE/${encoded()}/json?poll=1&since=12h", timeoutMs))
            .filter { it.id != myId }
        val merged = Social.visible(Social.merge(_friends.value, fresh), _pseudo.value, System.currentTimeMillis())
        prefs.edit().putString(KEY_CACHE, Social.encodeAll(merged)).apply()
        _friends.value = merged
        announceBosses(merged)
    }

    /** Compare les Boss vaincus de chacun à ce qui était connu, et prévient s'il y en a de nouveaux. */
    @Synchronized
    private fun announceBosses(cards: List<FriendCard>) {
        if (_pseudo.value == null) return
        val seen = prefs.getStringSet(KEY_SEEN_BOSSES, emptySet()).orEmpty().associate {
            it.substringAfter(':') to (it.substringBefore(':').toIntOrNull() ?: 0)
        }
        val news = Reminders.newBosses(cards, seen)
        val updated = Reminders.seenBosses(cards, seen)
        prefs.edit().putStringSet(KEY_SEEN_BOSSES, updated.map { (pseudo, n) -> "$n:$pseudo" }.toSet()).apply()
        if (news.isNotEmpty()) onBossNews?.invoke(news)
    }

    /** Vérification de fond (alarme toutes les 3 h) : lit le canal pour annoncer les Boss battus. */
    fun checkInBackground() {
        if (_pseudo.value == null) return
        try {
            pull(BACKGROUND_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Canal injoignable pour la vérification", e)
        }
    }

    /**
     * Les potes, pour un rappel : lecture rapide du canal, sinon les dernières cartes connues.
     * Rien si on ne joue pas (pas de pseudo).
     */
    fun friendsForReminder(): List<FriendCard> {
        if (_pseudo.value == null) return emptyList()
        try {
            pull(REMINDER_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Canal injoignable pour le rappel", e)
        }
        return _friends.value
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

    private fun httpGet(url: String, timeoutMs: Int? = null): String = connect(url, timeoutMs).run {
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

    private fun connect(url: String, timeoutMs: Int? = null) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeoutMs ?: 15_000
        readTimeout = timeoutMs ?: 20_000
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
        private const val KEY_SEEN_BOSSES = "seenBosses"
        private const val PUBLISH_DEBOUNCE_MS = 20_000L
        private const val REPUBLISH_AFTER_MS = 6 * 60 * 60 * 1000L
        /** Un rappel tient dans les quelques secondes qu'Android laisse à une alarme. */
        private const val REMINDER_TIMEOUT_MS = 3_000
        private const val BACKGROUND_TIMEOUT_MS = 6_000
        private const val PSEUDO_TIMEOUT_MS = 5_000
    }
}
