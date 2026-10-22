package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.TestProgress
import java.time.LocalDate

/** Largeur à partir de laquelle on affiche deux colonnes (écran intérieur des Fold, tablettes, paysage). */
val TwoPaneMinWidth = 600.dp

/** Stats sur une colonne, ou deux (vue d'ensemble | exercices) sur écran large. */
@Composable
fun StatsPanes(stats: Stats, color: Color, showProgram: Boolean, modifier: Modifier = Modifier, tests: List<TestProgress> = emptyList()) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                    statsTestItems(tests, color)
                    statsOverviewItems(stats, color, showProgram)
                }
                VerticalDivider()
                LazyColumn(Modifier.weight(1f).fillMaxHeight()) { statsExerciseItems(stats, color) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                statsTestItems(tests, color)
                statsOverviewItems(stats, color, showProgram)
                statsExerciseItems(stats, color)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectDialog(initial: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onSelect(it.pickerMillisToDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    ) { DatePicker(state = state) }
}
