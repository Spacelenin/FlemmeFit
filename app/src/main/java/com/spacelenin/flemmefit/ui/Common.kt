package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spacelenin.flemmefit.model.SideBalance
import com.spacelenin.flemmefit.model.Stats
import com.spacelenin.flemmefit.model.TestProgress
import com.spacelenin.flemmefit.model.VariantProgress
import java.time.LocalDate

/** Largeur à partir de laquelle on affiche deux colonnes (écran intérieur des Fold, tablettes, paysage). */
val TwoPaneMinWidth = 600.dp

/** Stats sur une colonne, ou deux (vue d'ensemble | exercices) sur écran large. */
@Composable
fun StatsPanes(
    stats: Stats,
    color: Color,
    showProgram: Boolean,
    modifier: Modifier = Modifier,
    tests: List<TestProgress> = emptyList(),
    variants: List<VariantProgress> = emptyList(),
    balances: List<SideBalance> = emptyList(),
    /** Éléments en tête de la vue d'ensemble. */
    header: LazyListScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                    header()
                    statsTestItems(tests, color)
                    statsVariantItems(variants, color)
                    statsBalanceItems(balances, color)
                    statsOverviewItems(stats, color, showProgram)
                }
                VerticalDivider()
                LazyColumn(Modifier.weight(1f).fillMaxHeight()) { statsExerciseItems(stats, color) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                header()
                statsTestItems(tests, color)
                statsVariantItems(variants, color)
                statsBalanceItems(balances, color)
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

/**
 * Plein écran par-dessus l'appli, hors des barres système. Les marges sont lues à la fois sur l'écran principal et
 * dans la fenêtre du Dialog, la plus grande l'emporte : certains téléphones (barre à 3 boutons) n'en donnent
 * aucune au Dialog, et le bas de l'écran passait sous les boutons.
 */
@Composable
fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val outer = WindowInsets.safeDrawing.asPaddingValues()
    val root = LocalView.current.let { view ->
        ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    }
    val density = LocalDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = dismissOnClickOutside, decorFitsSystemWindows = false),
    ) {
        val inner = WindowInsets.safeDrawing.asPaddingValues()
        val dir = LocalLayoutDirection.current
        fun px(v: Int?) = with(density) { (v ?: 0).toDp() }
        val padding = PaddingValues(
            start = maxOf(outer.calculateStartPadding(dir), inner.calculateStartPadding(dir), px(root?.left)),
            top = maxOf(outer.calculateTopPadding(), inner.calculateTopPadding(), px(root?.top)),
            end = maxOf(outer.calculateEndPadding(dir), inner.calculateEndPadding(dir), px(root?.right)),
            bottom = maxOf(outer.calculateBottomPadding(), inner.calculateBottomPadding(), px(root?.bottom)),
        )
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.padding(padding), content = content)
        }
    }
}

/** Titre de section des listes (stats, exos). */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}
