package com.spacelenin.flemmefit

import android.content.Context
import android.os.Build
import android.util.Log
import com.spacelenin.flemmefit.model.Safety
import java.io.File
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Rapport du dernier plantage : écrit au moment où l'appli plante, proposé au lancement suivant pour l'envoyer.
 * Seulement l'erreur technique et le téléphone, rien des séances.
 */
object CrashReport {
    private const val FILE = "dernier-plantage.txt"
    private const val ISSUES = "erreurs-rattrapees.txt"
    private const val MAX_ISSUES = 4000
    private val seen: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val writer = Executors.newSingleThreadExecutor()

    fun install(context: Context) {
        val dir = context.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(dir, FILE).writeText(text(error) + issues(dir)) }
            previous?.uncaughtException(thread, error)
        }
        // Erreurs rattrapées (l'appli a continué) : gardées pour un éventuel rapport, les plus récentes au bout. Une
        // même erreur n'est notée qu'une fois par lancement, et jamais depuis l'écran (le calcul se refait souvent).
        Safety.onError = { error ->
            val where = error.stackTrace.firstOrNull()?.toString()
            if (seen.add("${error.javaClass.name}@$where")) {
                Log.w("FlemmeFit", "Erreur rattrapée", error)
                writer.execute {
                    runCatching {
                        val file = File(dir, ISSUES)
                        val all = (if (file.exists()) file.readText() else "") + "\n" + text(error).take(1500)
                        file.writeText(all.takeLast(MAX_ISSUES))
                    }
                }
            }
        }
    }

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun issues(dir: File): String =
        File(dir, ISSUES).takeIf { it.exists() }?.readText()?.let { "\n\nErreurs rattrapées avant :\n$it" } ?: ""

    private fun text(error: Throwable): String =
        "FlemmeFit build ${BuildConfig.VERSION_CODE} · ${LocalDateTime.now().withNano(0)}\n" +
            "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
            error.stackTraceToString().take(8000)
}
