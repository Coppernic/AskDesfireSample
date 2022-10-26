package fr.coppernic.tools.askdemo.data.repository

import fr.coppernic.tools.askdemo.data.datasource.BadgeDataSource
import fr.coppernic.tools.askdemo.domain.model.AskBadge
import fr.coppernic.tools.askdemo.domain.repository.BadgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile

class BadgeRepositoryImpl(private val datasource: BadgeDataSource): BadgeRepository {
    private var isAborted: Boolean = false

    override suspend fun startScan(): Flow<Result<AskBadge>> {
        isAborted = false
        return datasource.getData()
            .takeWhile {
                it.getOrNull() != null || !isAborted
            }
    }

    override fun stopScan() {
        isAborted = true
    }

}