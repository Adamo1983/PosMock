package it.primesoftware.posmock.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Le tre schermate dell'app, nell'ordine in cui si usano: si accende, se serve
 * si cambia qualcosa, si guarda cosa passa.
 *
 * Niente NavHost: con tre destinazioni sempre disponibili il grafo di
 * navigazione aggiungerebbe solo cerimonia, e il `NavigationSuiteScaffold` gia'
 * si adatta da solo a telefono e tablet.
 */
enum class AppDestination(val label: String, val icon: ImageVector) {
    STATUS("Stato", Icons.Filled.PowerSettingsNew),
    CONFIG("Configura", Icons.Filled.Tune),
    MONITOR("Traffico", Icons.Filled.Terminal),
}
