package com.spacelenin.flemmefit.montre

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Liaison avec l'app du téléphone, en Bluetooth uniquement : on n'envoie qu'à un téléphone « à portée »
 * (relié directement, pas par le cloud). Sinon tout attend dans la file et part au retour du téléphone.
 */
object PhoneLink {
    private const val TAG = "PhoneLink"
    const val PATH_TODAY = "/flemmefit/today"
    const val PATH_LOG = "/flemmefit/log/"
    const val PATH_SYNC = "/flemmefit/sync"
    const val PATH_STATUS = "/flemmefit/watch"
    const val PATH_APK = "/flemmefit/apk"
    const val KEY_JSON = "json"

    private val lock = Mutex()

    /** Le téléphone à portée en Bluetooth, s'il y en a un. */
    private suspend fun nearbyPhone(context: Context): String? =
        Wearable.getNodeClient(context).connectedNodes.await().firstOrNull { it.isNearby }?.id

    /** Envoie la file d'attente si le téléphone est à portée. */
    fun flush(context: Context) {
        val app = context.applicationContext
        Store.scope.launch {
            lock.withLock {
                try {
                    if (Store.pending.value.isEmpty()) return@withLock
                    if (nearbyPhone(app) == null) return@withLock
                    val client = Wearable.getDataClient(app)
                    for (log in Store.pending.value) {
                        val request = PutDataMapRequest.create(PATH_LOG + log.id).apply {
                            dataMap.putString(KEY_JSON, WatchJson.encodeToString(WatchLog.serializer(), log))
                        }.asPutDataRequest().setUrgent()
                        client.putDataItem(request).await()
                        Store.setPending(Store.pending.value.filterNot { it.id == log.id })
                    }
                    publishStatus(app)
                } catch (e: Exception) {
                    Log.w(TAG, "Envoi au téléphone impossible", e)
                }
            }
        }
    }

    /** Publie l'état de la montre (version, batterie, envois en attente) pour l'encart du téléphone. */
    fun publishStatus(context: Context) {
        val app = context.applicationContext
        Store.scope.launch {
            try {
                if (nearbyPhone(app) == null) return@launch
                val request = PutDataMapRequest.create(PATH_STATUS).apply {
                    dataMap.putString(KEY_JSON, WatchJson.encodeToString(WatchStatus.serializer(), Store.status()))
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(app).putDataItem(request).await()
            } catch (e: Exception) {
                Log.w(TAG, "État non publié", e)
            }
        }
    }

    /** Relit la dernière séance publiée et demande au téléphone de la renvoyer à jour. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        Store.scope.launch {
            try {
                val items = Wearable.getDataClient(app).dataItems.await()
                try {
                    items.forEach { item ->
                        if (item.uri.path == PATH_TODAY) {
                            DataMapItem.fromDataItem(item).dataMap.getString(KEY_JSON)?.let(Store::setToday)
                        }
                    }
                } finally {
                    items.release()
                }
                nearbyPhone(app)?.let { Wearable.getMessageClient(app).sendMessage(it, PATH_SYNC, ByteArray(0)).await() }
                publishStatus(app)
            } catch (e: Exception) {
                Log.w(TAG, "Téléphone injoignable", e)
            }
            flush(app)
        }
    }

    fun onData(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH_TODAY) {
                DataMapItem.fromDataItem(event.dataItem).dataMap.getString(KEY_JSON)?.let(Store::setToday)
            }
        }
    }
}

/**
 * Reçoit la séance du jour même app fermée (et en profite pour renvoyer la file : le téléphone est là),
 * et la mise à jour de l'app envoyée par le téléphone en Bluetooth.
 */
class PhoneListenerService : WearableListenerService() {
    override fun onChannelOpened(channel: com.google.android.gms.wearable.ChannelClient.Channel) {
        if (channel.path != PhoneLink.PATH_APK) return
        Store.init(this)
        val app = applicationContext
        Store.scope.launch {
            val client = Wearable.getChannelClient(app)
            try {
                Updater.received()
                val file = java.io.File(app.cacheDir, "from-phone.apk")
                client.getInputStream(channel).await().use { input -> file.outputStream().use { input.copyTo(it) } }
                Updater.installReceived(app, file)
            } catch (e: Exception) {
                Updater.note("❌ Maj du téléphone : ${e.message ?: e.javaClass.simpleName}")
            } finally {
                runCatching { client.close(channel).await() }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Store.init(this)
        PhoneLink.onData(dataEvents)
        PhoneLink.flush(this)
    }
}
