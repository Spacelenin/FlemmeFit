package com.spacelenin.flemmefit.model

/**
 * Erreurs rattrapées dans les calculs qui ne sont qu'un plus (réglage sur mesure, équilibre gauche / droite) :
 * la séance reste calculée sans eux plutôt que de faire planter l'appli. L'appli les note pour le rapport.
 */
object Safety {
    @Volatile
    var onError: ((Throwable) -> Unit)? = null

    fun report(error: Throwable) {
        runCatching { onError?.invoke(error) }
    }
}
