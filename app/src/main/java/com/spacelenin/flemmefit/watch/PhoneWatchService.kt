package com.spacelenin.flemmefit.watch

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.spacelenin.flemmefit.FlemmeFitApp

/** Reçoit ce qui vient de la montre, même app fermée : séries faites, état de la montre, demande de la séance du jour. */
class PhoneWatchService : WearableListenerService() {
    private val link get() = (application as FlemmeFitApp).watch

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            val json = DataMapItem.fromDataItem(item).dataMap.getString(WatchPaths.KEY_JSON) ?: return@forEach
            when {
                item.uri.path == WatchPaths.STATUS -> link.onStatus(json)
                item.uri.path?.startsWith(WatchPaths.LOG) == true -> link.onLog(json, item.uri)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WatchPaths.SYNC) link.publish()
    }
}
