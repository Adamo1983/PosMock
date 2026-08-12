package it.primesoftware.posmock.presentation.activity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.primesoftware.posmock.presentation.components.OutcomeDialog
import it.primesoftware.posmock.presentation.navigation.AppDestination
import it.primesoftware.posmock.presentation.screen.config.view.ConfigScreenRoot
import it.primesoftware.posmock.presentation.screen.monitor.view.MonitorScreenRoot
import it.primesoftware.posmock.presentation.screen.status.view.StatusScreenRoot
import org.koin.androidx.compose.koinViewModel

@Composable
fun PosMockApp(viewModel: MainViewModel = koinViewModel()) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.STATUS) }
    val pendingDecision by viewModel.pendingDecision.collectAsStateWithLifecycle()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(destination.icon, contentDescription = destination.label)
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination },
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestination.STATUS -> StatusScreenRoot(
                    modifier = Modifier.padding(innerPadding),
                )

                AppDestination.CONFIG -> ConfigScreenRoot(
                    modifier = Modifier.padding(innerPadding),
                )

                AppDestination.MONITOR -> MonitorScreenRoot(
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }

    // Il dialog sta qui e non dentro una schermata: la richiesta arriva quando
    // arriva, e deve comparire anche se in quel momento si sta guardando il log.
    pendingDecision?.let { decision ->
        OutcomeDialog(
            decision = decision,
            onOutcomeChosen = { outcome -> viewModel.resolve(decision.request.id, outcome) },
        )
    }
}
