package com.spacelenin.flemmefit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.spacelenin.flemmefit.FlemmeFitApp
import com.spacelenin.flemmefit.MainActivity
import com.spacelenin.flemmefit.R
import com.spacelenin.flemmefit.model.ProgramSchedule
import com.spacelenin.flemmefit.model.StoredProgram
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Widget d'écran d'accueil : la séance du jour, où elle en est, et la série 🔥. Le toucher ouvre la page du jour. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    companion object {
        private val dayFormat = DateTimeFormatter.ofPattern("EEEE d", Locale.FRENCH)

        /** Redessine tous les widgets posés (après chaque changement du suivi). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as? FlemmeFitApp ?: return
            val program = app.repository.state.value.programs.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val (label, title, detail) = content(program, LocalDate.now())
            views.setTextViewText(R.id.widget_label, label)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_detail, detail)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { program?.let { putExtra(MainActivity.EXTRA_PROGRAM_ID, it.id) } }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 4300, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun content(program: StoredProgram?, today: LocalDate): Triple<String, String, String> {
            if (program == null) return Triple("FLEMMEFIT", "Pas encore de programme", "Touche pour en ajouter un")
            val schedule = ProgramSchedule(program, today)
            val progress = schedule.progress()
            val streak = "🔥 ${progress.streak} d'affilée"
            val session = schedule.sessionOn(today)
            return when {
                session == null -> {
                    val next = progress.nextSession
                    Triple(
                        "JOUR OFF 😴",
                        next?.let { "Prochaine : ${it.date.format(dayFormat)}" } ?: "Rien de prévu",
                        listOfNotNull(next?.day?.title, streak).joinToString(" · "),
                    )
                }
                else -> {
                    val done = schedule.requiredDone(session)
                    val badge = when {
                        session.isBoss -> "💀 BOSS"
                        session.isBurst -> "🚀 BURST"
                        session.isChill -> "🛋 CHILL"
                        session.isDeload -> "🌿 RÉCUP"
                        schedule.isComplete(session) -> "✓ FAIT"
                        else -> "AUJOURD'HUI"
                    }
                    Triple(badge, session.day.title ?: "Séance", "$done/${session.requiredCount} exos · $streak")
                }
            }
        }
    }
}
