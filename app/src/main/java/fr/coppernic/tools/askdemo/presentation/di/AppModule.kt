package fr.coppernic.tools.askdemo.presentation.di

import android.content.Context
import fr.coppernic.tools.askdemo.data.datasource.BadgeDataSource
import fr.coppernic.tools.askdemo.data.repository.BadgeRepositoryImpl
import fr.coppernic.tools.askdemo.domain.repository.BadgeRepository

class AppModule(override val applicationContext: Context): AppComponent {

    //region Repositories

    override val badgeRepository: BadgeRepository by lazy {
        BadgeRepositoryImpl(badgeDataSource)
    }

    //endregion

    //region Data sources

    private val badgeDataSource: BadgeDataSource by lazy {
        BadgeDataSource()
    }

    //endregion

}