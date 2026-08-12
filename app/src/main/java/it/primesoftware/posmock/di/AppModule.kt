package it.primesoftware.posmock.di

import it.primesoftware.posmock.data.hardware.AndroidBatteryOptimizationManager
import it.primesoftware.posmock.data.local.ConfigStore
import it.primesoftware.posmock.data.local.PosMockPreferences
import it.primesoftware.posmock.data.local.logging.LogRepositoryImpl
import it.primesoftware.posmock.data.local.logging.PublicLogExporter
import it.primesoftware.posmock.data.network.NetworkInfoProvider
import it.primesoftware.posmock.data.server.MockServerController
import it.primesoftware.posmock.data.server.OutcomeProviderImpl
import it.primesoftware.posmock.domain.hardware.BatteryOptimizationManager
import it.primesoftware.posmock.domain.repository.IConfigStore
import it.primesoftware.posmock.domain.repository.ILogExporter
import it.primesoftware.posmock.domain.repository.ILogRepository
import it.primesoftware.posmock.domain.repository.INetworkInfoProvider
import it.primesoftware.posmock.domain.repository.IOutcomeProvider
import it.primesoftware.posmock.domain.repository.IPreferencesRepository
import it.primesoftware.posmock.domain.repository.IServerController
import it.primesoftware.posmock.presentation.activity.MainViewModel
import it.primesoftware.posmock.presentation.screen.config.viewModel.ConfigViewModel
import it.primesoftware.posmock.presentation.screen.monitor.viewModel.MonitorViewModel
import it.primesoftware.posmock.presentation.screen.status.viewModel.StatusViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Tutti i moduli Koin dell'app, come in Ermes.
 *
 * ⚠️ Sono tutti `single`: il server, il log e la configurazione devono
 * sopravvivere alla rotazione e al passaggio in background. Un `factory` qui
 * significherebbe due server che si contendono la stessa porta.
 */
val loggerModule = module {
    single<ILogRepository> { LogRepositoryImpl(androidContext()) }
    single<ILogExporter> { PublicLogExporter(androidContext(), get()) }
}

val localModule = module {
    single<IPreferencesRepository> { PosMockPreferences(androidContext()) }
    single<IConfigStore> { ConfigStore(get()) }
    single<INetworkInfoProvider> { NetworkInfoProvider() }
}

val hardwareModule = module {
    single<BatteryOptimizationManager> { AndroidBatteryOptimizationManager(androidContext(), get()) }
}

val serverModule = module {
    single<IOutcomeProvider> { OutcomeProviderImpl(get()) }
    single<IServerController> { MockServerController(androidContext(), get(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { MainViewModel(get()) }
    viewModel { StatusViewModel(get(), get(), get()) }
    viewModel { ConfigViewModel(get(), get()) }
    viewModel { MonitorViewModel(get(), get()) }
}

val appModules = listOf(loggerModule, localModule, hardwareModule, serverModule, viewModelModule)
