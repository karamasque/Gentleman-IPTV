package com.kaynanamtv.data.sync

import com.kaynanamtv.data.local.dao.ProviderDao
import com.kaynanamtv.data.local.entity.ProviderEntity
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.ProviderEpgSyncMode
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.ProviderXtreamLiveSyncMode
import com.kaynanamtv.domain.model.SyncMetadata
import com.kaynanamtv.domain.repository.SyncMetadataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

open class FakeProviderDao(
    private val provider: ProviderEntity? = sampleProvider()
) : ProviderDao() {
    override suspend fun getById(id: Long): ProviderEntity? = provider
    override suspend fun getByIds(ids: List<Long>): List<ProviderEntity> =
        listOfNotNull(provider).filter { it.id in ids }
    override suspend fun updateSyncTime(id: Long, timestamp: Long) = Unit
    override fun getAll() = flowOf(listOfNotNull(provider))
    override suspend fun getAllSync(): List<ProviderEntity> = listOfNotNull(provider)
    override fun getActive() = flowOf(provider)
    override fun getByTypeSync(type: ProviderType): List<ProviderEntity> =
        listOfNotNull(provider).filter { it.type == type }
    override suspend fun insertDirect(provider: ProviderEntity) = this.provider?.id ?: 0L
    override suspend fun updateDirect(provider: ProviderEntity) = Unit
    override suspend fun insert(provider: ProviderEntity) = this.provider?.id ?: 0L
    override suspend fun update(provider: ProviderEntity) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun deactivateAll() = Unit
    override suspend fun activate(id: Long) = Unit
    override suspend fun setActive(id: Long) = Unit
    override suspend fun getByUrlAndUser(
        serverUrl: String,
        username: String,
        stalkerMacAddress: String
    ): ProviderEntity? = null
    override suspend fun getByUrlAndUserForAccount(
        serverUrl: String,
        username: String,
        stalkerMacAddress: String,
        accountUid: String?
    ): ProviderEntity? = null
    override fun getAllForAccount(accountUid: String?) = flowOf(listOfNotNull(provider))
    override suspend fun getAllForAccountSync(accountUid: String?): List<ProviderEntity> = listOfNotNull(provider)
    override fun getActiveForAccount(accountUid: String?) = flowOf(provider)
    override suspend fun deactivateAllForAccount(accountUid: String?) = Unit
    override suspend fun updateEpgUrl(id: Long, epgUrl: String) = Unit
    override suspend fun updateM3uUrl(id: Long, m3uUrl: String) = Unit

    companion object {
        fun sampleProvider(type: ProviderType = ProviderType.XTREAM_CODES) = ProviderEntity(
            id = 1L, name = "Test", type = type,
            serverUrl = "https://test.example.com:8080",
            username = "demo", password = "demo",
            epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
            xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.AUTO
        )
    }
}

class FakeSyncMetadataRepository : SyncMetadataRepository {
    private val values = mutableMapOf<Long, SyncMetadata>()

    override fun observeMetadata(providerId: Long): Flow<SyncMetadata?> = flowOf(values[providerId])

    override suspend fun getMetadata(providerId: Long): SyncMetadata? = values[providerId]

    override suspend fun updateMetadata(metadata: SyncMetadata) {
        values[metadata.providerId] = metadata
    }

    override suspend fun clearMetadata(providerId: Long) {
        values.remove(providerId)
    }

    fun reset() {
        values.clear()
    }
}
