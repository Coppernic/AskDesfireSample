package fr.coppernic.tools.askdemo.presentation

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LifecycleObserver
import fr.coppernic.tools.askdemo.presentation.di.AppComponent
import fr.coppernic.tools.askdemo.presentation.di.AppModule


class App : Application(), LifecycleObserver {

    companion object {
        lateinit var components: AppComponent
    }

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        components = AppModule(this)
    }
}