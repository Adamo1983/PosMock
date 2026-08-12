package it.primesoftware.posmock

import android.app.Application
import it.primesoftware.posmock.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PosMockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PosMockApplication)
            modules(appModules)
        }
    }
}
