package com.spacelenin.flemmefit.montre

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Juste après une mise à jour : la nouvelle version est annoncée au téléphone, qui ferme sa fenêtre de mise à jour. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Store.init(context)
        PhoneLink.refresh(context)
    }
}
