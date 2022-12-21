package fr.coppernic.tools.askdemo.presentation

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LifecycleObserver
import fr.coppernic.tools.askdemo.BuildConfig
import fr.coppernic.tools.askdemo.presentation.di.AppComponent
import fr.coppernic.tools.askdemo.presentation.di.AppModule
import timber.log.Timber


class App : Application(), LifecycleObserver {

    companion object {
        lateinit var components: AppComponent
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        components = AppModule(this)
    }
}