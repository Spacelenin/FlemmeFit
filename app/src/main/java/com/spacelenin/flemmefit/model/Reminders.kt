package com.spacelenin.flemmefit.model

import java.time.LocalDate
import kotlin.random.Random

/** Une notification à afficher pour un programme. */
data class ReminderMessage(val programId: String, val title: String, val text: String)

/**
 * Contenu des rappels, ton « terriblement taquin ». Le tirage dépend du jour et du programme :
 * une même notification ne change pas de phrase si elle est recalculée.
 */
object Reminders {

    private val daily = listOf(
        "Debout la loque ! {titre} : {exos} exos, pas de négociation.",
        "Le canapé a porté plainte : tu l'uses trop. {exos} exos au programme.",
        "Tes muscles ont lancé un avis de recherche. Dernière localisation connue : {titre}.",
        "{exos} petits exos. Petits. Comme ta motivation, mais on y croit (un peu).",
        "FlemmeFit porte un peu trop bien son nom si tu sautes {titre} aujourd'hui.",
        "Rappel amical (et légèrement menaçant) : {titre}, c'est aujourd'hui.",
        "Même ton chargeur de téléphone fait plus d'efforts que toi. {exos} exos, allez.",
        "{titre} t'attend. Il a l'air déçu, mais il t'attend.",
        "Ton futur toi a appelé : il veut que tu fasses ces {exos} exos. Il avait l'air essoufflé.",
        "Les excuses du jour sont épuisées. Reste plus que {titre}.",
    )

    private val bossToday = listOf(
        "💀 Le Boss est là. Il a vu tes stats et il rigole encore.",
        "💀 Jour Boss. Prouve que t'es pas juste une loque en jogging.",
        "💀 Le Boss t'attend. Il paraît que tu as peur. Il paraît.",
        "💀 Aujourd'hui, Boss. Bats ton max ou assume ta honte en silence.",
    )

    private val bossTomorrow = listOf(
        "💀 Demain c'est Boss. Ce soir : dodo, pas de série jusqu'à 3 h.",
        "💀 Le Boss arrive demain. Lui, il s'échauffe déjà.",
        "💀 Demain, Boss. Mange bien, dors bien, prie un peu.",
    )

    private val evening = listOf(
        "Il reste {reste} exos. Le canapé mène largement au score.",
        "Encore {reste} exos et tu pourras frimer. Ou pas, vu le rythme.",
        "{reste} exos restants. Ils vont pas se faire tout seuls, j'ai vérifié.",
        "Petit rappel : {reste} exos. La journée finit bientôt, ta flemme non.",
        "{reste} exos t'attendent. Ils commencent à croire que tu les évites.",
    )

    private fun pick(list: List<String>, programId: String, today: LocalDate, salt: Int) =
        list[Random(today.toEpochDay() * 31 + programId.hashCode() * 7 + salt).nextInt(list.size)]

    private fun String.fill(vararg values: Pair<String, Any>) =
        values.fold(this) { acc, (k, v) -> acc.replace("{$k}", v.toString()) }

    /**
     * « Bob et Al ont déjà fini leur séance. » : les potes qui ont tout fait aujourd'hui, null si personne.
     * Envoyé en notification à part, à côté d'un rappel de séance pas finie.
     */
    fun friendsDone(friends: List<FriendCard>, today: LocalDate): String? {
        val names = friends.filter { it.isToday(today) && it.todayTotal > 0 && it.todayDone >= it.todayTotal }
            .sortedByDescending { it.updatedAt }.map { it.pseudo.trim() }
        return when (names.size) {
            0 -> null
            1 -> "${names[0]} a déjà fini sa séance."
            2 -> "${names[0]} et ${names[1]} ont déjà fini leur séance."
            3 -> "${names[0]}, ${names[1]} et ${names[2]} ont déjà fini leur séance."
            else -> "${names[0]}, ${names[1]} et ${names.size - 2} autres ont déjà fini leur séance."
        }
    }

    /** La notification des potes, à part du rappel pour que tout le texte s'affiche. */
    fun friendsMessage(line: String) = ReminderMessage(FRIENDS_ID, "👥 Les potes", line)

    const val FRIENDS_ID = "potes"

    /**
     * Potes qui ont battu un Boss depuis la dernière fois : [seen] garde le nombre de Boss vaincus déjà connu
     * de chaque pseudo. Un pseudo jamais vu est noté sans rien annoncer.
     */
    fun newBosses(cards: List<FriendCard>, seen: Map<String, Int>): List<String> =
        cards.filter { c -> seen[c.pseudo.trim().lowercase()]?.let { c.bossDefeated > it } == true }
            .sortedByDescending { it.updatedAt }
            .map { it.pseudo.trim() }

    /** Ce que [seen] devient après avoir vu [cards]. */
    fun seenBosses(cards: List<FriendCard>, seen: Map<String, Int>): Map<String, Int> =
        seen + cards.associate { it.pseudo.trim().lowercase() to maxOf(it.bossDefeated, seen[it.pseudo.trim().lowercase()] ?: 0) }

    /** « 💀 Bob a battu son Boss. » */
    fun bossMessage(names: List<String>): ReminderMessage? {
        val text = when (names.size) {
            0 -> return null
            1 -> "${names[0]} a battu son Boss. À toi de pas être le maillon faible."
            2 -> "${names[0]} et ${names[1]} ont battu leur Boss. Ça commence à faire beaucoup."
            else -> "${names[0]}, ${names[1]} et ${names.size - 2} autre${if (names.size > 3) "s" else ""} ont battu leur Boss."
        }
        return ReminderMessage(BOSS_NEWS_ID, "💀 Boss vaincu", text)
    }

    const val BOSS_NEWS_ID = "potes-boss"

    /** Rappel du jour : séance prévue et pas finie, sinon annonce du Boss du lendemain. */
    fun daily(programs: List<StoredProgram>, today: LocalDate): List<ReminderMessage> = programs.mapNotNull { p ->
        val schedule = ProgramSchedule(p, today)
        val session = schedule.sessionOn(today)
        val name = p.plan.name
        when {
            session != null && !schedule.isComplete(session) -> {
                val titre = session.day.title ?: "la séance"
                val text = if (session.isBoss) pick(bossToday, p.id, today, 1)
                else pick(daily, p.id, today, 0).fill("titre" to titre, "exos" to session.requiredCount - schedule.requiredDone(session))
                ReminderMessage(p.id, if (session.isBoss) "💀 Boss : $titre" else "💪 $name : $titre", text)
            }
            schedule.sessionOn(today.plusDays(1))?.isBoss == true ->
                ReminderMessage(p.id, "$name : Boss demain", pick(bossTomorrow, p.id, today, 2))
            else -> null
        }
    }

    /** Relance du soir : séance du jour pas finie. */
    fun evening(programs: List<StoredProgram>, today: LocalDate): List<ReminderMessage> = programs.mapNotNull { p ->
        val schedule = ProgramSchedule(p, today)
        val session = schedule.sessionOn(today) ?: return@mapNotNull null
        val remaining = session.requiredCount - schedule.requiredDone(session)
        if (remaining <= 0) return@mapNotNull null
        val streak = schedule.progress().streak
        // Un Boss déjà sauvé (aux trois quarts) ne fait plus sauter la série.
        val text = pick(evening, p.id, today, 3).fill("reste" to remaining) +
            if (streak > 0 && !schedule.isSaved(session)) " Tes $streak 🔥 d'affilée sautent à minuit." else ""
        ReminderMessage(p.id, "⏰ ${p.plan.name} : ${session.day.title ?: "séance"} pas finie", text)
    }
}
