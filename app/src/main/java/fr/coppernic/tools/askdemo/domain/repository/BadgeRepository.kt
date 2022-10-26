package fr.coppernic.tools.askdemo.domain.repository

import fr.coppernic.tools.askdemo.domain.model.AskBadge
import kotlinx.coroutines.flow.Flow

interface BadgeRepository {

    suspend fun startScan(): Flow<Result<AskBadge>>
    fun stopScan()

}