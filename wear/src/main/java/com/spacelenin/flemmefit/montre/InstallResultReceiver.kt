package com.spacelenin.flemmefit.montre

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Réponse de l'installeur du système : succès, échec, ou confirmation demandée à l'utilisateur. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Updater.note("Le système demande une confirmation…")
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Updater.note("Écran de confirmation impossible : ${e.javaClass.simpleName} ${e.message.orEmpty()}")
                    }
                } else {
                    Updater.note("Pas d'écran de confirmation fourni.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.note("✅ Installé ! Relance l'app pour voir le nouveau numéro.")
            else -> Updater.note("❌ Échec (statut $status) : $message")
        }
    }
}
