package fr.coppernic.tools.askdemo.presentation.di

import android.content.Context
import fr.coppernic.tools.askdemo.data.repository.BadgeRepositoryImpl
import fr.coppernic.tools.askdemo.domain.repository.BadgeRepository

interface AppComponent {

    val applicationContext: Context
    val badgeRepository: BadgeRepository

}