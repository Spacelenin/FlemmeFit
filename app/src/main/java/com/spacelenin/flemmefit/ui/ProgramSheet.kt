package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.StoredProgram
import com.spacelenin.flemmefit.ui.theme.LocalFitTheme
import com.spacelenin.flemmefit.ui.theme.ProgramColorCount
import com.spacelenin.flemmefit.ui.theme.programColor

/** Une ligne du panneau d'un programme : ligne simple, ou bouton blanc bombé sur le thème fit. */
@Composable
fun SheetRow(icon: String, label: String, value: String? = null, onClick: () -> Unit) {
    val fit = LocalFitTheme.current
    val shape = RoundedCornerShape(if (fit) 22.dp else 12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (fit) Modifier.background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEEF1F4)))).border(2.dp, Color(0xFFD3DBE0), shape)
                else Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (fit) 16.dp else 4.dp, vertical = if (fit) 11.dp else 12.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium.copy(shadow = null), modifier = Modifier.width(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (fit) FontWeight.Bold else FontWeight.Normal,
            color = if (fit) Color(0xFF2A332B) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        value?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (fit) Color(0xFF5D6B60) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Le panneau « Mon programme » du bouton ⋮ : réglages (date, couleur), tests de niveau, routine (mise à jour,
 * export), et la suppression à part, tout en bas.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgramSheet(
    program: StoredProgram,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onTests: () -> Unit,
    onColor: (Int) -> Unit,
    onUpdate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val fit = LocalFitTheme.current
    val color = programColor(program.colorIndex)
    val current = Math.floorMod(program.colorIndex, ProgramColorCount)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor(), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .then(if (fit) Modifier.linedPaper() else Modifier)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            if (fit) FitBand(program.plan.name, color)
            else Text(program.plan.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "Depuis le ${program.start.format(DayMonth)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (fit) Modifier.align(Alignment.CenterHorizontally) else Modifier,
            )

            InfoHeader("Réglages", color)
            SheetRow("📅", "Date de début", program.start.format(DayMonth), onStart)
            Text("🎨 Couleur", style = MaterialTheme.typography.bodyLarge, fontWeight = if (fit) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp)) {
                (0 until ProgramColorCount).forEach { i ->
                    val on = i == current
                    Box(
                        Modifier
                            .size(34.dp)
                            .then(if (on) Modifier.border(3.dp, if (fit) color else MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .padding(if (on) 5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(programColor(i))
                            .then(if (fit && !on) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                            .clickable(role = Role.RadioButton) { onColor(i) }
                            .semantics {
                                selected = on
                                contentDescription = if (i == 0) "Couleur du thème" else "Couleur ${i + 1}"
                            },
                    )
                }
            }

            if (program.plan.tests.isNotEmpty()) {
                InfoHeader("Niveau", color)
                StartButton(onClick = onTests, modifier = Modifier.fillMaxWidth().height(52.dp), badge = "★") { Text("Tests de niveau") }
            }

            InfoHeader("Routine", color)
            SheetRow("⟳", "Mettre à jour la routine", onClick = onUpdate)
            SheetRow("⇪", "Exporter", "routine ou modèle", onExport)

            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
                Text("Supprimer le programme", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}
