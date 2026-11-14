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
import kotlinx.coroutines.launch
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
                HOME -> HomeWithList(today, update, updating, onStart, onDemo, onUpdate)
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
    // Comme sur le téléphone : seuls les exos obligatoires comptent (pas l'échauffement ni les étirements).
    val required = today.exercises.filterNot { it.optional }.ifEmpty { today.exercises }
    val done = required.count { it.done }
    val total = required.size
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
        is Updater.State.Receiving -> Kicker(
            "Réception de la maj : " + (updating.percent?.let { "$it %" } ?: "…") +
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
    // Toucher un pote : sa mini-fiche.
    var open by remember { mutableStateOf<Pair<Int, WatchFriend>?>(null) }
    val shown = open
    if (shown != null) {
        FriendSheet(shown.first, shown.second) { open = null }
        return
    }
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
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Fiche de ${f.name}") { open = i to f }
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

/** Mini-fiche d'un pote : son jour, sa série, ses Boss et exploits, son dernier exploit. */
@Composable
private fun FriendSheet(rank: Int, f: WatchFriend, onBack: () -> Unit) {
    val pal = LocalPalette.current
    Dial(1f, Orange) {
        Band((if (rank == 0) "👑 " else "${rank + 1}e · ") + f.name, ctx = Orange)
        Centered(top = 44.dp) {
            f.today?.let { Kicker(it, pal.ink) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("🔥" to "${f.streak}", "💀" to "${f.boss}", "🏆" to "${f.exploits}", "pts" to "${f.points}").forEach { (k, v) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(k, style = text(11.sp, FontWeight.SemiBold), color = pal.soft)
                        Text(v, style = text(14.sp, FontWeight.Black), color = pal.ink)
                    }
                }
            }
            f.lastExploit?.let { Meta("Dernier exploit : $it") }
            FitButton("Retour", onBack, plain = true)
        }
    }
}

/*
 * L'accueil en vertical : ↓ ouvre la liste des exos du jour, ↓ encore ramène à l'accueil (boucle) ;
 * ↑ depuis la liste ramène aussi à l'accueil, ↑ sur l'accueil ne fait rien.
 */
@Composable
private fun HomeWithList(
    today: WatchToday?,
    update: Updater.Found?,
    updating: Updater.State,
    onStart: () -> Unit,
    onDemo: () -> Unit,
    onUpdate: () -> Unit,
) {
    var showList by remember { mutableStateOf(false) }
    val canList = today?.isSessionDay == true
    if (showList && canList && today != null) {
        SessionList(today) { showList = false }
        return
    }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(canList) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (canList && total > threshold) showList = true },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) { HomePage(today, update, updating, onStart, onDemo, onUpdate) }
}

/** Les exos du jour, à cocher ou décocher comme sur la page du jour du téléphone. */
@Composable
private fun SessionList(today: WatchToday, onClose: () -> Unit) {
    val pal = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val threshold = with(density) { 70.dp.toPx() }
    // Cochés depuis la montre, en attendant que le téléphone renvoie la séance à jour.
    val local = remember(today) { mutableStateMapOf<Int, Boolean>() }
    // Arrivé au bout de la liste : un balayage de plus ramène à l'accueil.
    val edges = remember {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (consumed.y != 0f) pull = 0f
                    pull += available.y
                    if (kotlin.math.abs(pull) > threshold) {
                        pull = 0f
                        onClose()
                    }
                }
                return Offset.Zero
            }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dial(today.exercises.filterNot { it.optional }.let { r -> if (r.isEmpty()) 0f else r.count { local[it.i] ?: it.done }.toFloat() / r.size }) {
        LazyColumn(
            state = list,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 36.dp, bottom = 40.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(edges)
                .onRotaryScrollEvent { e ->
                    scope.launch { list.scrollBy(e.verticalScrollPixels) }
                    true
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            item { Kicker(today.title ?: "Séance du jour") }
            items(today.exercises, key = { it.i }) { ex ->
                val done = local[ex.i] ?: ex.done
                // Une série à fond se fait en séance (il faut le max) : on peut la décocher, pas la cocher ici.
                val canToggle = done || !ex.allOut
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.chip)
                        .then(
                            if (canToggle) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Checkbox, onClickLabel = ex.name) {
                                local[ex.i] = !done
                                Store.check(context, today, ex, !done)
                            } else Modifier,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(if (done) Green.c else Color(0xFFD9E1E6)),
                    ) { if (done) Text("✓", style = text(12.sp, FontWeight.Black), color = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, style = text(13.sp, FontWeight.ExtraBold).copy(textAlign = TextAlign.Start), color = if (ex.optional) pal.soft else pal.ink, maxLines = 2)
                        Text(
                            listOfNotNull(
                                if (ex.timed) formatSeconds(ex.planned) else if (ex.plain) "${ex.sets} série(s)" else "${ex.sets} × ${ex.planned}",
                                "facultatif".takeIf { ex.optional },
                                "à fond, en séance".takeIf { ex.allOut && !done },
                            ).joinToString(" · "),
                            style = text(11.sp, FontWeight.SemiBold).copy(textAlign = TextAlign.Start),
                            color = pal.soft,
                        )
                    }
                }
            }
        }
    }
}
