package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.BOSS_STEPS
import com.spacelenin.flemmefit.model.BURST_STEPS
import com.spacelenin.flemmefit.model.FriendCard

private class TutorialPage(val icon: String, val title: String, val lines: List<String>)

private val Pages = listOf(
    TutorialPage(
        "🏠", "L'accueil",
        listOf(
            "En haut : la carte du jour. « C'est parti » lance le mode séance, « Décaler » la reporte d'un jour (tout le reste suit).",
            "En dessous : le mois. Touche un jour pour ouvrir sa page : la séance, les exercices et ses actions. Les flèches passent au jour d'avant ou d'après.",
            "Jours sans séance = jours off 😴 : ils ne cassent pas ta série. « J'ai fait autre chose » (marche, vélo…) les note sur le calendrier ✚, sans rien compter.",
            "Widget : appui long sur l'écran d'accueil du téléphone › Widgets › FlemmeFit, pour voir ta séance et ta série sans ouvrir l'appli.",
            "Le ⋮ de la carte : date de début, tests, couleur, mise à jour de la routine, suppression.",
        ),
    ),
    TutorialPage(
        "✅", "Faire sa séance",
        listOf(
            "« C'est parti » ou « Démarrer la séance » : le mode séance te guide exercice par exercice, série par série, avec le repos décompté. Tout s'enregistre tout seul.",
            "Touche la vignette d'un exercice pour le marquer comme fait, touche son nom pour le détail (chiffres, consigne, niveaux).",
            "Appui long ou ⋮ › « Saisir mes séries » : tu règles chaque série. En dessous du prévu, l'exercice est incomplet et la progression attend.",
            "Dans le détail ou le ⋮ : ⏱ chrono (séries et repos enchaînés) et minuteur de repos. Écran verrouillé ? Une notification décompte et sonne à la fin.",
            "Échauffement et étirements sont facultatifs : les passer ne compte pas contre toi.",
            "Séance finie : dis comment c'était (😫 🙂 💪), ou passe.",
            "Besoin de souffler plus ? Réglages › Séance › Repos en plus : jusqu'au double du repos prévu, pour tous les exercices.",
            "Les séances à venir ne se marquent pas : on note ce qui est fait, le jour même.",
            "🏋️ Boxe et Beat Saber : dis le jour même si tu fais avec ou sans poids. Avec, la progression monte un peu moins vite, jusqu'au même plafond.",
        ),
    ),
    TutorialPage(
        "📈", "La progression",
        listOf(
            "Chaque séance terminée fait monter les chiffres (reps, durée ou charge, selon la routine).",
            "Chaque exo se règle sur toi : note ce que tu fais vraiment (plus que prévu sur toutes les séries, ou en dessous), le temps en plus après 0 sur les exos en durée, et dis si c'était dur ou facile. Pile le prévu : le rythme est bon.",
            "Ça bouge vite les premières semaines, puis ça se cale. Le réglage de chaque exo est dans l'onglet Exos.",
            "Toutes les 4 semaines : semaine récup 🌿, le volume est divisé par deux pour récupérer.",
        ),
    ),
    TutorialPage(
        "📏", "Tests et niveaux",
        listOf(
            "Certaines routines démarrent d'un test : ton résultat choisit ton niveau (les chiffres de départ).",
            "Les tests sont refaits en semaine récup : ils te font changer de niveau et vérifient que le réglage ne va pas trop vite.",
            "Tu peux corriger tes résultats via ⋮ › « Tests de niveau ». Abdos : choisis-y crunch ou sit-up, et change quand tu veux (ton max refait recale la suite).",
            "Onglet Stats : la courbe de chaque test, ton record, la date du prochain test et ce qu'il te manque pour passer au niveau suivant.",
        ),
    ),
    TutorialPage(
        "🎲", "Variantes et exos perso",
        listOf(
            "Certains jours tirés au sort, une variante plus dure (diamant, pike, une main, cossack…) remplace une partie des séries de son exo de base (aux jambes, tout l'exercice : les squats classiques servent surtout de test).",
            "Tu es prévenu en haut de la séance et au moment des séries. Pas chaud ? « Pas aujourd'hui » la replace plus tard dans le cycle.",
            "Sa dernière série au max recale ton taux (1 pompe pike ≈ 10 pompes…) : la dose suit ton vrai niveau. Courbes dans Stats.",
            "Onglet Exos : ajoute tes propres exos. Dans une famille, ils deviennent des variantes ; dans « Autre », l'appli les place 2 fois par semaine.",
        ),
    ),
    TutorialPage(
        "💀", "Boss, Burst et Chill",
        listOf(
            "Boss : une séance surprise toutes les 3-4 semaines, dévoilée la veille. Plus dure qu'un Burst (+$BOSS_STEPS étapes) et dernière série à fond. Tout fait = Boss vaincu ; aux trois quarts, ta série 🔥 tient, sans points.",
            "Burst 🚀 : une fois par semaine, tu peux renforcer la séance du jour (+$BURST_STEPS étapes, ou une série de plus). +${FriendCard.BURST_POINTS} points chez les potes. Annulable tant que rien n'est fait.",
            "Chill 🛋 : une fois par semaine aussi, pour les jours mous : séries divisées par deux, la progression attend. Elle compte quand même pour ta série.",
        ),
    ),
    TutorialPage(
        "👥", "Le reste",
        listOf(
            "Stats : le bilan, les badges 🏅 (10, 50, 100, 500, 1 000, 10 000 pompes, séances, Boss…), les courbes de tests et tous les chiffres.",
            "Exos : tes exos perso et les variantes de la routine, avec ton taux pour chacune.",
            "Potes : classement entre amis avec un pseudo (séances, Boss vaincus, exploits, Bursts). Tes rappels te disent qui a déjà fini, et une notif prévient quand un pote bat son Boss.",
            "Réglages (roue dentée) : thème, rappels, sauvegarde (un fichier réécrit tout seul, si tu en choisis un), mises à jour, et ce tuto dans Aide.",
            "Routine modifiée ? Réimporte le fichier : l'appli propose de mettre à jour le programme en gardant ton suivi.",
        ),
    ),
)

/** Faut-il un petit tour ? Proposé une fois, au premier programme. */
@Composable
fun TutorialOfferDialog(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text("Un petit tour de l'appli ?") },
        text = { Text("Sept écrans pour comprendre l'accueil, le mode séance, la progression, les niveaux, les variantes, les Boss, le Burst et le Chill. Toujours dispo dans Réglages › Aide.") },
        confirmButton = { TextButton(onClick = onYes) { Text("Oui, montre-moi") } },
        dismissButton = { TextButton(onClick = onNo) { Text("Non merci") } },
    )
}

@Composable
fun TutorialDialog(onClose: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val p = Pages[page]
    val last = page == Pages.lastIndex
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Text(p.icon, style = MaterialTheme.typography.displaySmall) },
        title = { Text(p.title, fontWeight = FontWeight.ExtraBold, maxLines = 1) },
        text = {
            Column {
                // Toutes les pages sont posées l'une sur l'autre, seule la page en cours est visible :
                // le cadre prend la hauteur de la plus longue et les boutons ne bougent pas d'une page à l'autre.
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Pages.forEachIndexed { i, pg ->
                        val shown = i == page
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.alpha(if (shown) 1f else 0f).then(if (shown) Modifier else Modifier.clearAndSetSemantics {}),
                        ) {
                            pg.lines.forEach { line ->
                                Row {
                                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    Spacer(Modifier.width(10.dp))
                                    Text(line, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Page ${page + 1} sur ${Pages.size}" },
                ) {
                    Pages.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == page) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (last) onClose() else page++ }) { Text(if (last) "C'est compris" else "Suivant") }
        },
        dismissButton = {
            if (page > 0) TextButton(onClick = { page-- }) { Text("Précédent") }
            else TextButton(onClick = onClose) { Text("Passer") }
        },
    )
}
