package com.spacelenin.flemmefit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private data class Destination(val label: String, val icon: ImageVector)

private val Destinations = listOf(
    Destination("Accueil", Icons.Default.Home),
    Destination("Stats", Icons.AutoMirrored.Filled.List),
    Destination("Exos", Icons.Default.Star),
    Destination("Potes", Icons.Default.Person),
)

/** Barre du bas sur téléphone / Fold plié, rail à gauche sur écran déplié. */
@Composable
fun MainTabs(tab: Int, onTab: (Int) -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Destinations.forEachIndexed { i, d ->
                        NavigationRailItem(
                            selected = tab == i,
                            onClick = { onTab(i) },
                            icon = { Icon(d.icon, null) },
                            label = { Text(d.label) },
                        )
                    }
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // La barre du bas prend déjà la place de la barre de navigation Android.
                Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) { content() }
                NavigationBar {
                    Destinations.forEachIndexed { i, d ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { onTab(i) },
                            icon = { Icon(d.icon, null) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
        }
    }
}
