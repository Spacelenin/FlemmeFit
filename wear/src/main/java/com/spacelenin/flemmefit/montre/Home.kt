package com.spacelenin.flemmefit.montre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/*
 * Accueil au centre, en boucle : … Réglages ⇄ Semaine ⇄ ACCUEIL ⇄ Potes ⇄ Stats ⇄ Réglages …
 * Deux balayages au plus pour revenir à l'accueil, dans un sens ou dans l'autre.
 */
private const val PAGES = 5
private const val HOME = 0
private const val FRIENDS = 1
private const val STATS = 2
private const val SETTINGS = 3
private const val WEEK = 4
private const val LAPS = 2000

private fun pageName(page: Int) = when (page) {
    HOME -> "Accueil"
    FRIENDS -> "Potes"
    STATS -> "Stats"
    SETTINGS -> "Réglages"
    else -> "Semaine"
}

@Composable
fun HomePager(
    today: WatchToday?,
    update: Updater.Found?,
    updating: Updater.State,
    onStart: () -> Unit,
    onDemo: () -> Unit,
    onUpdate: () -> Unit,
    onCheck: () -> Unit,
) {
    val state = rememberPagerState(initialPage = LAPS * PAGES / 2) { LAPS * PAGES }
    // Toutes les pages restent construites en mémoire (2 de chaque côté = les 5) : le balayage ne fait que les afficher.
    HorizontalPager(state = state, beyondViewportPageCount = 2, modifier = Modifier.fillMaxSize()) { index ->
        val page = Math.floorMod(index, PAGES)
        Box(Modifier.fillMaxSize()) {
            when (page) {
                HOME -> HomePage(today, update, updating, onStart, onDemo, onUpdate)
                FRIENDS -> FriendsPage(today)
                STATS -> StatsPage(today)
                SETTINGS -> SettingsPage(update, updating, onUpdate, onCheck)
                else -> WeekPage(today)
            }
            if (page == HOME) PagerDots()
        }
    }
}

@Composable
private fun BoxScope.EdgeLabel(name: String, left: Boolean) {
    Text(
        if (left) "$name ›" else "‹ $name",
        style = text(9.sp, FontWeight.Black).copy(letterSpacing = 0.5.sp),
        color = LocalPalette.current.soft,
        modifier = Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 14.dp)
            .vertical(clockwise = !left),
    )
}

@Composable
private fun BoxScope.PagerDots() {
    val ctx = LocalCtx.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFC9D4DB)))
        Box(Modifier.size(width = 14.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(ctx.c))
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFC9D4DB)))
    }
}

@Composable
private fun Centered(top: Dp = 0.dp, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = top),
    ) { content() }
}

@Composable
private fun HomePage(today: WatchToday?, update: Updater.Found?, updating: Updater.State, onStart: () -> Unit, onDemo: () -> Unit, onUpdate: () -> Unit) {
    if (today == null) {
        Dial(0f) {
            Band("FlemmeFit")
            Centered(top = 40.dp) {
                Meta("En attente du téléphone.\nOuvre FlemmeFit sur ton téléphone, à portée en Bluetooth.")
                UpdateLine(update, updating, onUpdate)
            }
        }
        return
    }
    // Montre trop ancienne pour ce téléphone : rien ne se lance avant la mise à jour.
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }
    if (version < today.minWatch) {
        Dial(0f, Red) {
            Band("Mise à jour requise", ctx = Red)
            Centered(top = 44.dp) {
                Meta("Ton téléphone a besoin de la version ${today.minWatch} de l'app montre (tu as la $version).")
                Meta("Téléphone : Réglages › Montre › Mettre à jour la montre.")
                UpdateLine(update, updating, onUpdate)
            }
        }
        return
    }
    val done = today.exercises.count { it.done }
    val total = today.exercises.size
    val progress = if (total == 0) 0f else done.toFloat() / total
    Dial(progress) {
        val band = when (today.mode) {
            WatchToday.MODE_BOSS -> "💀 Boss"
            WatchToday.MODE_BURST -> "🚀 Burst"
            WatchToday.MODE_DELOAD -> "🌿 Récup"
            WatchToday.MODE_CHILL -> "🛋 Chill"
            else -> if (today.isSessionDay) "Aujourd'hui" else "Repos"
        }
        Band(band, today.streak.takeIf { it > 0 }?.let { "🔥 $it" })
        Centered(top = 44.dp) {
            if (today.isSessionDay) {
                today.position?.let { Kicker(it) }
                Title(today.title.orEmpty())
                Meta(
                    if (done == 0) listOfNotNull("$total exos", today.minutes?.let { "~$it min" }).joinToString(" · ")
                    else "$done/$total exos",
                )
                when {
                    done >= total -> Kicker("✅ Séance faite")
                    else -> FitButton(if (done == 0) (if (today.mode == WatchToday.MODE_BOSS) "Affronter" else "C'est parti") else "Continuer", onStart, big = true)
                }
            } else {
                Title("Repos aujourd'hui", 17.sp)
                today.next?.let { Meta(it) }
                if (today.bossTomorrow) Kicker("💀 Boss demain", Red.c)
            }
            UpdateLine(update, updating, onUpdate)
        }
    }
}

/** Maj disponible : le bouton, puis l'avancement du téléchargement (l'écran reste allumé, luminosité au minimum). */
@Composable
private fun UpdateLine(update: Updater.Found?, updating: Updater.State, onUpdate: () -> Unit) {
    when (updating) {
        is Updater.State.Downloading -> Kicker(
            "Maj ${updating.version} : " + (updating.percent?.let { "$it %" } ?: "connexion…") +
                (updating.kbPerSecond?.let { " · ${if (it >= 1024) "%.1f Mo/s".format(it / 1024f) else "$it Ko/s"}" } ?: ""),
        )
        is Updater.State.Installing -> Kicker(if (updating.version == 0) "Installation de la maj envoyée par le téléphone…" else "Installation de la maj ${updating.version}…")
        is Updater.State.Failed -> {
            Kicker("❌ ${updating.message}", Red.c)
            if (update != null) FitButton("Réessayer", onUpdate, plain = true)
        }
        Updater.State.Idle -> if (update != null) FitButton("Maj ${update.version} dispo", onUpdate, plain = true)
    }
}

@Composable
private fun WeekPage(today: WatchToday?) {
    val week = today?.week
    Dial(week?.let { if (it.total == 0) 0f else it.done.toFloat() / it.total } ?: 0f) {
        Band("Ma semaine", week?.label?.takeIf { it.isNotEmpty() })
        Centered(top = 44.dp) {
            if (week == null) {
                Meta("Pas encore de données du téléphone.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { week.days.forEach { DayDot(it) } }
                Meta("${week.done}/${week.total} séances" + (week.note?.let { " · $it" } ?: ""))
                if (today.bossTomorrow) Kicker("💀 Boss demain", Red.c)
            }
        }
    }
}

@Composable
private fun DayDot(day: WatchDay) {
    val pal = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(day.letter, style = text(10.sp, FontWeight.Black), color = pal.soft)
        val (bg, label, fg) = when (day.state) {
            "done" -> Triple(Green.c, "✓", Color.White)
            "boss" -> Triple(Red.c, "💀", Color.White)
            "missed" -> Triple(Color(0xFFB9C1C8), "✕", Color.White)
            "today" -> Triple(pal.bg, "•", Green.c)
            "rest" -> Triple(Color.Transparent, "·", pal.soft)
            else -> Triple(Color(0xFFD9E1E6), "", Color.White)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (day.state == "today") Green.c else bg)
                .padding(if (day.state == "today") 2.dp else 0.dp)
                .clip(CircleShape)
                .background(bg),
        ) { Text(label, style = text(11.sp, FontWeight.Black), color = fg) }
    }
}

@Composable
private fun FriendsPage(today: WatchToday?) {
    val pal = LocalPalette.current
    Dial(1f, Orange) {
        Band("Les potes", ctx = Orange)
        Centered(top = 44.dp) {
            val friends = today?.friends.orEmpty()
            if (friends.isEmpty()) {
                Meta("Le classement arrive avec le téléphone.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    friends.take(3).forEachIndexed { i, f ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (f.me) Color(0xFFFFF4D6) else pal.chip)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                (if (i == 0) "👑 " else "${i + 1} · ") + f.name,
                                style = text(12.sp).copy(textAlign = TextAlign.Start),
                                color = if (f.me) Color(0xFF33393D) else pal.ink,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${f.points}", style = text(12.sp, FontWeight.Black), color = if (f.me) Color(0xFF33393D) else pal.ink)
                        }
                    }
                }
                today?.friendNews?.let { Meta(it) }
            }
        }
    }
}

@Composable
private fun StatsPage(today: WatchToday?) {
    val pal = LocalPalette.current
    Dial(1f, Sky) {
        Band("Mes stats", ctx = Sky)
        Centered(top = 44.dp) {
            val stats = today?.stats.orEmpty()
            if (stats.isEmpty()) {
                Meta("Tes stats arrivent avec le téléphone.")
            } else {
                stats.take(6).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { s ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                                Text(s.label, style = text(10.sp, FontWeight.SemiBold), color = pal.soft, maxLines = 1)
                                Text(s.value, style = text(13.sp, FontWeight.Black), color = pal.ink, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

/** Réglages : la version de l'app, ses mises à jour, et la démo. */
@Composable
private fun SettingsPage(update: Updater.Found?, updating: Updater.State, onUpdate: () -> Unit, onCheck: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }
    val manual by Updater.manual.collectAsState()
    val demo by Store.demo.collectAsState()
    Dial(0f, Grey) {
        Band("Réglages", ctx = Grey)
        Centered(top = 44.dp) {
            // Version de l'app et mises à jour : chaque état est écrit en clair.
            Meta("Version $version")
            when {
                update != null || updating !is Updater.State.Idle -> UpdateLine(update, updating, onUpdate)
                manual == Updater.Check.Running -> Kicker("Recherche d'une maj…")
                manual == Updater.Check.UpToDate -> {
                    Kicker("✓ Dernière version installée")
                    FitButton("Chercher à nouveau", onCheck, plain = true)
                }
                manual is Updater.Check.Error -> {
                    Kicker("❌ ${(manual as Updater.Check.Error).message}", Red.c)
                    FitButton("Réessayer", onCheck, plain = true)
                }
                else -> FitButton("Chercher une maj", onCheck, plain = true)
            }
            // La démo, pour essayer sans téléphone : rangée ici, l'accueil ne parle que du téléphone.
            if (demo) FitButton("Quitter la démo", { Store.setDemo(false) }, plain = true)
            else FitButton("Essayer la démo", { Store.setDemo(true) }, plain = true)
        }
    }
}
