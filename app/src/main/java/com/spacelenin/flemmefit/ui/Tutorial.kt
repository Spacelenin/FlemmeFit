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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.BURST_STEPS

private class TutorialPage(val icon: String, val title: String, val lines: List<String>)

private val Pages = listOf(
    TutorialPage(
        "🏠", "L'accueil",
        listOf(
            "En haut : la séance du jour. « C'est parti » l'ouvre, « Repousser » la décale d'un jour (tout le reste suit).",
            "En dessous : le mois. Touche un jour pour voir sa séance.",
            "Plusieurs programmes ? Un sélecteur apparaît en haut, les autres sont des points de couleur sur le calendrier.",
            "Le ⋮ de la carte : date de début, tests, couleur, mise à jour de la routine, suppression.",
        ),
    ),
    TutorialPage(
        "✅", "Valider une séance",
        listOf(
            "Touche la vignette d'un exercice pour le valider, touche son nom pour le détail (chiffres, consigne, paliers).",
            "Appui long ou ⋮ › « Noter ce que j'ai fait » : tu règles chaque série. En dessous du prévu, l'exercice est partiel et la progression attend.",
            "Les séances à venir ne se valident pas : on valide ce qui est fait, le jour même.",
        ),
    ),
    TutorialPage(
        "📈", "La progression",
        listOf(
            "Chaque séance validée fait monter les chiffres (reps, durée ou charge, selon la routine).",
            "Dernière série « à fond » : note ton max. Bien au-dessus du prévu, ça accélère ; en dessous, ça ralentit.",
            "Toutes les 4 semaines : décharge 🌿, le volume est divisé par deux pour récupérer.",
        ),
    ),
    TutorialPage(
        "📏", "Tests et paliers",
        listOf(
            "Certaines routines démarrent d'un test : ton résultat choisit ton palier (les chiffres de départ).",
            "Les tests sont refaits en semaine de décharge et te font changer de palier.",
            "Tu peux corriger tes résultats via ⋮ › « Tests de départ ».",
        ),
    ),
    TutorialPage(
        "💀", "Boss et Burst",
        listOf(
            "Boss : une séance surprise toutes les 3-4 semaines, dévoilée la veille. Chiffres de la semaine suivante et dernière série à fond.",
            "Burst 🚀 : une fois par semaine, tu peux renforcer la séance du jour (+$BURST_STEPS crans, ou une série de plus). Annulable tant que rien n'est validé.",
        ),
    ),
    TutorialPage(
        "👥", "Le reste",
        listOf(
            "Potes : classement entre amis avec un pseudo (séances, Boss vaincus, exploits).",
            "Réglages (roue dentée) : thème, rappels, mises à jour, et ce tuto dans Aide.",
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
        text = { Text("Six écrans pour comprendre comment valider, progresser, les paliers, les Boss et le Burst. Toujours dispo dans Réglages › Aide.") },
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
        title = { Text(p.title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                p.lines.forEach { line ->
                    Row {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(10.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
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
