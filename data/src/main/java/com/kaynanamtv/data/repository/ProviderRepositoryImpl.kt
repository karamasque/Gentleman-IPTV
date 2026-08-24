package com.kaynanamtv.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.*
import com.kaynanamtv.data.local.entity.ProviderEntity
import com.kaynanamtv.data.manager.recording.RecordingAlarmScheduler
import com.kaynanamtv.data.manager.reminder.ProgramReminderAlarmScheduler
import com.kaynanamtv.data.mapper.*
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.remote.http.buildGenericProviderRequestProfile
import com.kaynanamtv.data.remote.jellyfin.JellyfinProvider
import com.kaynanamtv.data.remote.stalker.StalkerApiService
import com.kaynanamtv.data.remote.stalker.StalkerPlaybackMode
import com.kaynanamtv.data.remote.stalker.StalkerProvider
import com.kaynanamtv.data.remote.xtream.XtreamApiService
import com.kaynanamtv.data.remote.xtream.XtreamProvider
import com.kaynanamtv.data.security.CredentialCrypto
import com.kaynanamtv.data.security.CredentialDecryptionException
import com.kaynanamtv.data.sync.SyncManager
import com.kaynanamtv.data.sync.hasUsableLiveCatalogForActivation
import com.kaynanamtv.data.util.ProviderInputSanitizer
import com.kaynanamtv.data.util.UrlSecurityPolicy
import com.kaynanamtv.domain.manager.ProviderCredentials
import com.kaynanamtv.domain.model.*
import com.kaynanamtv.domain.provider.IptvProvider
import com.kaynanamtv.domain.repository.LiveStreamProgramRequest
import com.kaynanamtv.domain.repository.ProviderDeleteProgress
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.repository.SyncMetadataRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.awaitClose
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kaynanamtv.data.sync.PermanentImageCache
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerDao: ProviderDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val programDao: ProgramDao,
    private val recordingRunDao: RecordingRunDao,
    private val programReminderDao: ProgramReminderDao,
    private val stalkerApiService: StalkerApiService,
    private val xtreamApiService: XtreamApiService,
    private val credentialCrypto: CredentialCrypto,
    private val accountE2eeCrypto: com.kaynanamtv.data.security.AccountE2eeCrypto,
    private val preferencesRepository: PreferencesRepository,
    private val syncManager: SyncManager,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val transactionRunner: DatabaseTransactionRunner,
    private val recordingAlarmScheduler: RecordingAlarmScheduler,
    private val programReminderAlarmScheduler: ProgramReminderAlarmScheduler,
    private val jellyfinProvider: JellyfinProvider,
    private val database: com.kaynanamtv.data.local.KaynanamTVDatabase? = null
) : ProviderRepository {
    private var firestoreListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    private companion object {
        const val XTREAM_GUIDE_BATCH_CONCURRENCY = 4
        const val BACKGROUND_EPG_START_DELAY_MS = 15_000L
        // Row-equivalent weights for non-row delete steps so the progress bar still moves
        // meaningfully on providers with tiny (or empty) catalogs.
        const val ALARM_STEP_WEIGHT = 5
        const val PROVIDER_ROW_STEP_WEIGHT = 200
        const val FINALIZE_STEP_WEIGHT = 200
        val logger: Logger = Logger.getLogger(ProviderRepositoryImpl::class.java.name)
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingDeletedProviderIds = java.util.Collections.synchronizedSet(HashSet<Long>())

    private val currentAccountUidFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        trySend(FirebaseAuth.getInstance().currentUser?.uid)
        awaitClose {
            FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }

    init {
        try {
            val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                val uid = user?.uid
                Log.i("ProviderRepository", "[AUTH_STATE_CHANGED] user=${user?.email} uid=$uid")
                firestoreListenerRegistration?.remove()
                firestoreListenerRegistration = null
                
                if (user != null) {
                    val currentUid = user.uid
                    Log.i("ProviderRepository", "[CURRENT_UID] $currentUid")
                    startFirestoreSnapshotListener(currentUid)
                }
            }
            FirebaseAuth.getInstance().addAuthStateListener(authListener)
        } catch (e: Exception) {
            Log.e("ProviderRepository", "Failed to register AuthStateListener", e)
        }
    }

    private fun startFirestoreSnapshotListener(currentUid: String) {
        firestoreListenerRegistration?.remove()
        val firestore = FirebaseFirestore.getInstance()
        Log.i("ProviderRepository", "[PROVIDER_CLOUD_LISTENER_START] Starting Firestore snapshot listener for uid=$currentUid")
        firestoreListenerRegistration = firestore.collection("users").document(currentUid)
            .collection("providers").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ProviderRepository", "[FIRESTORE_SNAPSHOT_ERROR] Firestore listener error: ${error.message}", error)
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        repositoryScope.launch {
                            kotlinx.coroutines.delay(2000)
                            val currentUser = FirebaseAuth.getInstance().currentUser
                            if (currentUser?.uid == currentUid) {
                                currentUser.getIdToken(true).addOnSuccessListener {
                                    Log.i("ProviderRepository", "[AUTH_TOKEN_REFRESHED] Retrying Firestore snapshot listener for uid=$currentUid")
                                    startFirestoreSnapshotListener(currentUid)
                                }
                            }
                        }
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val remoteDocCount = snapshot.documents.size
                    Log.i("ProviderRepository", "[FIRESTORE_SNAPSHOT_RECEIVED] docCount=$remoteDocCount")
                    Log.i("ProviderRepository", "[REMOTE_DOC_COUNT] $remoteDocCount")
                    repositoryScope.launch {
                        try {
                            val persistentDeletedIds = preferencesRepository.getDeletedProviderIdsSync()
                            val tombstonedIds = persistentDeletedIds + pendingDeletedProviderIds
                            val remoteList = snapshot.documents.mapNotNull { it.data }
                            val localEntities = providerDao.getAllForAccountSync(currentUid)
                            
                            // 1. Sync from Local to Firestore (Upload missing local providers owned by this user)
                            val remoteIds = remoteList.mapNotNull { 
                                (it["id"] as? Long ?: (it["id"] as? String)?.toLongOrNull())?.toString() 
                            }
                            val unassignedEntities = providerDao.getAllForAccountSync(null)
                            (localEntities + unassignedEntities).distinctBy { it.id }.forEach { entity ->
                                if (entity.id.toString() !in remoteIds && entity.id !in tombstonedIds) {
                                    val cleartextPassword = try {
                                        credentialCrypto.decryptIfNeeded(entity.password)
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    val provider = entity.toPublicDomain().copy(password = cleartextPassword, accountUid = currentUid)
                                    syncProviderToFirestore(provider)
                                }
                            }
                            
                            // 2. Sync from Firestore to Local (Download missing remote providers)
                            val localIds = localEntities.map { it.id.toString() }

                            remoteList.forEach { providerData ->
                                val idStr = (providerData["id"] as? Long ?: (providerData["id"] as? String)?.toLongOrNull())?.toString() ?: return@forEach
                                val idLong = idStr.toLongOrNull() ?: return@forEach
                                Log.i("ProviderRepository", "[REMOTE_PROVIDER_ID] idLong=$idLong localExists=${idStr in localIds} tombstoned=${idLong in tombstonedIds}")
                                if (idStr !in localIds && idLong !in tombstonedIds) {
                                    val typeStr = providerData["type"] as? String ?: return@forEach
                                    val type = try { ProviderType.valueOf(typeStr) } catch (e: Exception) { 
                                        Log.e("ProviderRepository", "Unknown provider type: $typeStr", e)
                                        return@forEach 
                                    }
                                    val rawRemotePassword = providerData["password"] as? String ?: ""
                                    Log.i("ProviderRepository", "[E2EE_DECRYPT_START] id=$idLong isEncrypted=${rawRemotePassword.startsWith("enc:v2:")}")
                                    val cleartextPassword = try {
                                        val decrypted = accountE2eeCrypto.decryptForAccount(rawRemotePassword, currentUid)
                                        Log.i("ProviderRepository", "[E2EE_DECRYPT_SUCCESS] id=$idLong")
                                        decrypted
                                    } catch (e: Exception) {
                                        Log.e("ProviderRepository", "[E2EE_DECRYPT_FAIL] id=$idLong error=${e.message}", e)
                                        rawRemotePassword
                                    }
                                    val provider = Provider(
                                        id = idLong,
                                        accountUid = currentUid,
                                        name = providerData["name"] as? String ?: "",
                                        type = type,
                                        serverUrl = providerData["serverUrl"] as? String ?: "",
                                        username = providerData["username"] as? String ?: "",
                                        password = cleartextPassword,
                                        m3uUrl = providerData["m3uUrl"] as? String ?: "",
                                        epgUrl = providerData["epgUrl"] as? String ?: "",
                                        httpUserAgent = providerData["httpUserAgent"] as? String ?: "",
                                        httpHeaders = providerData["httpHeaders"] as? String ?: "",
                                        stalkerMacAddress = providerData["stalkerMacAddress"] as? String ?: "",
                                        stalkerDeviceProfile = providerData["stalkerDeviceProfile"] as? String ?: "",
                                        stalkerDeviceTimezone = providerData["stalkerDeviceTimezone"] as? String ?: "",
                                        stalkerDeviceLocale = providerData["stalkerDeviceLocale"] as? String ?: "",
                                        stalkerSerialNumber = providerData["stalkerSerialNumber"] as? String ?: "",
                                        stalkerDeviceId = providerData["stalkerDeviceId"] as? String ?: "",
                                        stalkerDeviceId2 = providerData["stalkerDeviceId2"] as? String ?: "",
                                        stalkerSignature = providerData["stalkerSignature"] as? String ?: "",
                                        stalkerAdvancedOptionsJson = providerData["stalkerAdvancedOptionsJson"] as? String ?: "",
                                        stalkerAuthMode = StalkerAuthMode.valueOf(providerData["stalkerAuthMode"] as? String ?: "AUTO"),
                                        stalkerPortalProfile = StalkerPortalProfile.valueOf(providerData["stalkerPortalProfile"] as? String ?: "MAG_BASIC"),
                                        stalkerPortalFingerprint = StalkerPortalFingerprint.valueOf(providerData["stalkerPortalFingerprint"] as? String ?: "BASIC_MAC"),
                                        stalkerMagPreset = StalkerMagPreset.valueOf(providerData["stalkerMagPreset"] as? String ?: "GENERIC_SAFE"),
                                        stalkerLastBootstrapRecipe = StalkerBootstrapRecipe.valueOf(providerData["stalkerLastBootstrapRecipe"] as? String ?: "GENERIC_SAFE"),
                                        stalkerEndpointPreference = StalkerEndpointPreference.valueOf(providerData["stalkerEndpointPreference"] as? String ?: "AUTO"),
                                        stalkerCookieMode = StalkerCookieMode.valueOf(providerData["stalkerCookieMode"] as? String ?: "NONE"),
                                        stalkerPlaybackBackendHint = StalkerPlaybackBackendHint.valueOf(providerData["stalkerPlaybackBackendHint"] as? String ?: "AUTO"),
                                        stalkerLastPlaybackMode = providerData["stalkerLastPlaybackMode"] as? String,
                                        stalkerCredentialsRequired = providerData["stalkerCredentialsRequired"] as? Boolean ?: false,
                                        stalkerMacRequired = providerData["stalkerMacRequired"] as? Boolean ?: true,
                                        stalkerUsesTemporaryLinks = providerData["stalkerUsesTemporaryLinks"] as? Boolean ?: false,
                                        stalkerModuleRestricted = providerData["stalkerModuleRestricted"] as? Boolean ?: false,
                                        stalkerStrictFingerprintRequired = providerData["stalkerStrictFingerprintRequired"] as? Boolean ?: false,
                                        stalkerRecipeFallbackUsed = providerData["stalkerRecipeFallbackUsed"] as? Boolean ?: false,
                                        stalkerRecipeRediscoveryAttempts = (providerData["stalkerRecipeRediscoveryAttempts"] as? Long)?.toInt() ?: 0,
                                        isActive = providerData["isActive"] as? Boolean ?: true,
                                        maxConnections = (providerData["maxConnections"] as? Long)?.toInt() ?: 1,
                                        expirationDate = providerData["expirationDate"] as? Long,
                                        apiVersion = providerData["apiVersion"] as? String,
                                        allowedOutputFormats = providerData["allowedOutputFormats"] as? List<String> ?: emptyList(),
                                        epgSyncMode = ProviderEpgSyncMode.valueOf(providerData["epgSyncMode"] as? String ?: "UPFRONT"),
                                        guideSourcePolicy = GuideSourcePolicy.valueOf(providerData["guideSourcePolicy"] as? String ?: "AUTO"),
                                        channelLogoSourcePolicy = ChannelLogoSourcePolicy.valueOf(providerData["channelLogoSourcePolicy"] as? String ?: "SUPPLIER_PREFERRED"),
                                        xtreamFastSyncEnabled = providerData["xtreamFastSyncEnabled"] as? Boolean ?: true,
                                        xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.valueOf(providerData["xtreamLiveSyncMode"] as? String ?: "AUTO"),
                                        m3uVodClassificationEnabled = providerData["m3uVodClassificationEnabled"] as? Boolean ?: false,
                                        status = ProviderStatus.valueOf(providerData["status"] as? String ?: "UNKNOWN"),
                                        lastSyncedAt = providerData["lastSyncedAt"] as? Long ?: 0L,
                                        createdAt = providerData["createdAt"] as? Long ?: System.currentTimeMillis()
                                    )
                                    Log.i("ProviderRepository", "[ROOM_UPSERT_START] id=$idLong accountUid=$currentUid name=${provider.name}")
                                    try {
                                        providerDao.insert(provider.toSecureEntity())
                                        Log.i("ProviderRepository", "[ROOM_UPSERT_SUCCESS] id=$idLong")
                                    } catch (e: Exception) {
                                        Log.e("ProviderRepository", "[ROOM_UPSERT_FAIL] id=$idLong error=${e.message}", e)
                                    }
                                    repositoryScope.launch {
                                        syncManager.sync(provider.id, force = false)
                                    }
                                }
                            }
                            val roomCount = providerDao.getAllForAccountSync(currentUid).size
                            Log.i("ProviderRepository", "[ROOM_PROVIDER_COUNT_FOR_UID] uid=$currentUid count=$roomCount")
                        } catch (e: Exception) {
                            Log.e("ProviderRepository", "Error processing Firestore snapshot", e)
                        }
                    }
                }
            }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getProviders(): Flow<List<Provider>> =
        currentAccountUidFlow.flatMapLatest { uid ->
            providerDao.getAllForAccount(uid).map { entities ->
                val providers = entities.map { it.toPublicDomain() }
                Log.i("ProviderRepository", "[UI_PROVIDER_COUNT_FOR_UID] uid=$uid count=${providers.size}")
                providers
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getActiveProvider(): Flow<Provider?> =
        currentAccountUidFlow.flatMapLatest { uid ->
            providerDao.getActiveForAccount(uid).map { it?.toPublicDomain() }
        }

    override suspend fun getProvider(id: Long): Provider? =
        providerDao.getById(id)?.toPublicDomain()

    override suspend fun addProvider(provider: Provider): Result<Long> = try {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val boundProvider = if (provider.accountUid == null && currentUid != null) {
            provider.copy(accountUid = currentUid)
        } else {
            provider
        }
        val id = insertProvider(boundProvider)
        syncProviderIdToFirestore(id)
        repositoryScope.launch {
            syncManager.sync(id, force = true)
        }
        Result.success(id)
    } catch (e: Exception) {
        Result.error("Failed to add provider: ${e.message}", e)
    }

    override suspend fun updateProvider(provider: Provider): Result<Unit> = try {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val boundProvider = if (provider.accountUid == null && currentUid != null) {
            provider.copy(accountUid = currentUid)
        } else {
            provider
        }
        providerDao.update(boundProvider.toSecureEntity())
        syncProviderIdToFirestore(boundProvider.id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to update provider: ${e.message}", e)
    }

    override suspend fun getAllProviderCredentials(): List<ProviderCredentials> {
        return providerDao.getAllSync()
            .map { entity ->
                ProviderCredentials(
                    serverUrl = entity.serverUrl,
                    username = entity.username,
                    password = try {
                        credentialCrypto.decryptIfNeeded(entity.password)
                    } catch (e: Throwable) {
                        ""
                    },
                )
            }
            .filter { it.username.isNotBlank() && it.password.isNotBlank() }
    }

    override suspend fun getProviderCleartextPassword(id: Long): String? {
        val entity = providerDao.getById(id) ?: return null
        return try {
            credentialCrypto.decryptIfNeeded(entity.password)
        } catch (e: Throwable) {
            null
        }
    }


    override suspend fun updateProviderPassword(
        serverUrl: String,
        username: String,
        cleartextPassword: String,
    ): Boolean {
        val entity = providerDao.getAllSync().firstOrNull {
            it.serverUrl == serverUrl && it.username == username
        } ?: return false
        val encrypted = credentialCrypto.encryptIfNeeded(cleartextPassword)
        providerDao.update(entity.copy(password = encrypted))
        syncProviderIdToFirestore(entity.id)
        return true
    }

    override suspend fun deleteProvider(
        id: Long,
        onProgress: ((ProviderDeleteProgress) -> Unit)?
    ): Result<Unit> {
        pendingDeletedProviderIds.add(id)
        runCatching { preferencesRepository.recordDeletedProviderId(id) }
        return try {
            // Run Firestore delete in a background coroutine so it doesn't block the SQLite deletion if network is slow/offline
            repositoryScope.launch(Dispatchers.IO) {
                runCatching { deleteProviderFromFirestore(id) }
                    .onFailure { logger.warning("Firestore provider delete failed for $id: ${it.message}") }
            }
            val recordingRunIds = recordingRunDao.getIdsByProvider(id)
            val reminderIds = programReminderDao.getIdsByProvider(id)

            // Query image URLs for permanent cache cleanup before deleting from database
            val channelUrls = channelDao.getByProviderSync(id).mapNotNull { it.logoUrl }.filter { it.isNotBlank() }
            val movieUrls = movieDao.getByProviderSync(id).mapNotNull { it.posterUrl }.filter { it.isNotBlank() }
            val seriesUrls = seriesDao.getByProviderSync(id).mapNotNull { it.posterUrl }.filter { it.isNotBlank() }
            val allUrls = (channelUrls + movieUrls + seriesUrls).distinct()

            onProgress?.invoke(ProviderDeleteProgress(message = "Sağlayıcı kaldırılıyor...", fraction = 0.5f))

            transactionRunner.inTransaction {
                programDao.deleteByProvider(id)
                channelDao.deleteByProvider(id)
                movieDao.deleteByProvider(id)
                seriesDao.deleteByProvider(id)
                providerDao.delete(id)
            }

            recordingRunIds.forEach { runId ->
                runPostDeleteCleanup("recording alarm $runId") {
                    recordingAlarmScheduler.cancel(runId)
                }
            }
            reminderIds.forEach { reminderId ->
                runPostDeleteCleanup("reminder alarm $reminderId") {
                    programReminderAlarmScheduler.cancel(reminderId)
                }
            }
            runPostDeleteCleanup("provider sync cleanup $id") {
                syncManager.onProviderDeleted(id)
            }
            runPostDeleteCleanup("traffic coordinator reset $id") {
                com.kaynanamtv.data.remote.stalker.StalkerTrafficCoordinator.resetForProvider(id)
            }

            // Immediately ensure active provider fallback is selected so UI & player never refer to deleted provider
            runPostDeleteCleanup("active source cleanup $id") {
                val lastActiveId = preferencesRepository.lastActiveProviderId.first()
                if (lastActiveId == id) {
                    val remainingProviders = providerDao.getAllSync()
                    if (remainingProviders.isNotEmpty()) {
                        val nextProvider = remainingProviders.first()
                        providerDao.setActive(nextProvider.id)
                        preferencesRepository.setLastActiveProviderId(nextProvider.id)
                        preferencesRepository.setActiveLiveSource(com.kaynanamtv.domain.model.ActiveLiveSource.ProviderSource(nextProvider.id))
                    } else {
                        preferencesRepository.setLastActiveProviderId(0L)
                        preferencesRepository.setActiveLiveSource(null)
                    }
                }
            }

            onProgress?.invoke(ProviderDeleteProgress(message = "Sağlayıcı silindi.", fraction = 1.0f))

            // Offload heavy disk I/O (image files, vacuum) to background
            repositoryScope.launch(Dispatchers.IO) {
                runPostDeleteCleanup("provider image cache cleanup $id") {
                    PermanentImageCache.deleteCachedFiles(context, allUrls)
                }
                runPostDeleteCleanup("vacuum and wal checkpoint $id") {
                    database?.openHelper?.writableDatabase?.let { sqliteDb ->
                        sqliteDb.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                        sqliteDb.execSQL("VACUUM")
                    }
                }
                kotlinx.coroutines.delay(3000)
                pendingDeletedProviderIds.remove(id)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            pendingDeletedProviderIds.remove(id)
            Result.error("Failed to delete provider: ${e.message}", e)
        }
    }

    private inline fun runPostDeleteCleanup(step: String, block: () -> Unit) {
        runCatching(block).onFailure { throwable ->
            logger.warning("Provider delete committed but post-delete cleanup failed for $step: ${throwable.message}")
        }
    }

    override suspend fun setActiveProvider(id: Long): Result<Unit> {
        return try {
            val provider = providerDao.getById(id)
                ?: return Result.error("Provider not found")
            if (!hasUsableLiveCatalogForActivation(id, provider.type, channelDao, categoryDao, syncMetadataRepository)) {
                syncManager.scheduleProviderSyncResume(id)
                return Result.error("Provider is saved but no content has been committed yet. Sync will resume in background.")
            }
            providerDao.setActive(id)
            preferencesRepository.setLastActiveProviderId(id)
            preferencesRepository.setActiveLiveSource(com.kaynanamtv.domain.model.ActiveLiveSource.ProviderSource(id))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to set active provider: ${e.message}", e)
        }
    }

    override suspend fun loginXtream(
        serverUrl: String,
        username: String,
        password: String,
        name: String,
        httpUserAgent: String,
        httpHeaders: String,
        xtreamFastSyncEnabled: Boolean,
        epgSyncMode: ProviderEpgSyncMode,
        xtreamLiveSyncMode: com.kaynanamtv.domain.model.ProviderXtreamLiveSyncMode,
        guideSourcePolicy: GuideSourcePolicy,
        channelLogoSourcePolicy: ChannelLogoSourcePolicy,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ): Result<Provider> {
        val normalizedServerUrl = ProviderInputSanitizer.cleanXtreamServerUrl(serverUrl)
        val normalizedUsername = ProviderInputSanitizer.normalizeUsername(username)
        val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)
        val resolvedServerUrl = ProviderInputSanitizer.resolveUrlProtocol(normalizedServerUrl)

        ProviderInputSanitizer.validateUrl(resolvedServerUrl)?.let { message ->
            return Result.error(message)
        }
        UrlSecurityPolicy.validateXtreamServerUrl(resolvedServerUrl)?.let { message ->
            return Result.error(message)
        }
        val tAuthStart = System.currentTimeMillis()
        onProgress?.invoke("1/4 • Sunucu doğrulanıyor…")
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val existingProvider = if (id != null) {
            // Edit path: check that the new normalized identity does not collide with a
            // different provider before we commit the update.
            val collision = providerDao.getByUrlAndUserForAccount(resolvedServerUrl, normalizedUsername, accountUid = currentUid)
            if (collision != null && collision.id != id) {
                return Result.error("A provider with this server URL and username already exists.")
            }
            providerDao.getById(id)
        } else {
            providerDao.getByUrlAndUserForAccount(resolvedServerUrl, normalizedUsername, accountUid = currentUid)
        }
        val effectivePassword = try {
            password.takeIf { it.isNotBlank() }
                ?: existingProvider?.password?.let(credentialCrypto::decryptIfNeeded)
                ?: ""
        } catch (e: CredentialDecryptionException) {
            return Result.error(e.message ?: CredentialDecryptionException.MESSAGE, e)
        }
        val provider = createXtreamProvider(
            providerId = 0,
            serverUrl = resolvedServerUrl,
            username = normalizedUsername,
            password = effectivePassword,
            httpUserAgent = httpUserAgent,
            httpHeaders = httpHeaders
        )
        return when (val authResult = provider.authenticate()) {
            is Result.Success -> {
                val authDuration = System.currentTimeMillis() - tAuthStart
                Log.i("ONBOARD_TRACE", "[ONBOARD_TRACE] AUTH_DONE elapsed=${authDuration}ms")
                Log.i("FAST_ONBOARDING", "[AUTH] completed in ${authDuration}ms")
                val providerData = if (existingProvider != null) {
                    onProgress?.invoke("1/4 • Sağlayıcı güncelleniyor…")
                    val updated = authResult.data.copy(
                        id = existingProvider.id,
                        accountUid = existingProvider.accountUid ?: currentUid,
                        name = normalizedName.ifBlank { existingProvider.name },
                        serverUrl = resolvedServerUrl,
                        username = normalizedUsername,
                        password = effectivePassword,
                        httpUserAgent = httpUserAgent,
                        httpHeaders = httpHeaders,
                        epgSyncMode = epgSyncMode,
                        xtreamLiveSyncMode = xtreamLiveSyncMode,
                        guideSourcePolicy = guideSourcePolicy,
                        channelLogoSourcePolicy = channelLogoSourcePolicy,
                        xtreamFastSyncEnabled = xtreamFastSyncEnabled,
                        isActive = false,
                        status = ProviderStatus.PARTIAL,
                        lastSyncedAt = 0,
                        createdAt = existingProvider.createdAt
                    )
                    providerDao.update(updated.toSecureEntity())
                    syncProviderIdToFirestore(updated.id)
                    Log.i("ONBOARD_TRACE", "[ONBOARD_TRACE] PROVIDER_UPDATED id=${updated.id}")
                    updated.copy(password = "")
                } else {
                    val newData = authResult.data.copy(
                        accountUid = currentUid,
                        name = normalizedName.ifBlank { authResult.data.name },
                        httpUserAgent = httpUserAgent,
                        httpHeaders = httpHeaders,
                        epgSyncMode = epgSyncMode,
                        xtreamLiveSyncMode = xtreamLiveSyncMode,
                        guideSourcePolicy = guideSourcePolicy,
                        channelLogoSourcePolicy = channelLogoSourcePolicy,
                        xtreamFastSyncEnabled = xtreamFastSyncEnabled,
                        isActive = false,
                        status = ProviderStatus.PARTIAL
                    )
                    val newId = insertProvider(newData)
                    syncProviderIdToFirestore(newId)
                    Log.i("ONBOARD_TRACE", "[ONBOARD_TRACE] PROVIDER_CREATED id=$newId")
                    newData.copy(id = newId).copy(password = "")
                }

                val tSyncStart = System.currentTimeMillis()
                val syncRes = syncManager.sync(
                    providerData.id,
                    force = true,
                    onProgress = onProgress,
                    trackInitialLiveOnboarding = true
                )
                val totalSyncDuration = System.currentTimeMillis() - tSyncStart
                Log.i("FAST_ONBOARDING", "[TOTAL_TTFC] auth=${authDuration}ms sync=${totalSyncDuration}ms total=${authDuration + totalSyncDuration}ms")

                handleInitialOnboardingSync(
                    providerData = providerData,
                    syncResult = syncRes,
                    onProgress = onProgress,
                    syncFailurePrefix = "Provider login succeeded, but initial sync failed. The provider was saved and can be retried from Settings"
                )
            }
            is Result.Error -> Result.error(authResult.message, authResult.exception)
            is Result.Loading -> Result.error("Unexpected loading state")
        }
    }

    override suspend fun validateM3u(
        url: String,
        name: String,
        epgUrl: String,
        httpUserAgent: String,
        httpHeaders: String,
        epgSyncMode: ProviderEpgSyncMode,
        m3uVodClassificationEnabled: Boolean,
        guideSourcePolicy: GuideSourcePolicy,
        channelLogoSourcePolicy: ChannelLogoSourcePolicy,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ): Result<Provider> = try {
        val normalizedUrl = ProviderInputSanitizer.resolveUrlProtocol(
            ProviderInputSanitizer.normalizeUrl(url)
        )
        val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)

        ProviderInputSanitizer.validateUrl(normalizedUrl)?.let { message ->
            return Result.error(message)
        }
        UrlSecurityPolicy.validatePlaylistSourceUrl(normalizedUrl)?.let { message ->
            return Result.error(message)
        }
        onProgress?.invoke("Validating playlist URL...")
        val providerName = normalizedName.ifBlank {
            normalizedUrl.substringAfterLast("/").substringBefore("?").ifBlank { "M3U Playlist" }
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val existingProvider = if (id != null) {
            // Edit path: check that the new normalized URL does not collide with a different
            // provider before we commit the update.
            val collision = providerDao.getByUrlAndUserForAccount(normalizedUrl, "", accountUid = currentUid)
            if (collision != null && collision.id != id) {
                return Result.error("A playlist provider with this URL already exists.")
            }
            providerDao.getById(id)
        } else {
            providerDao.getByUrlAndUserForAccount(normalizedUrl, "", accountUid = currentUid)
        }

        val providerData = if (existingProvider != null) {
            val updated = existingProvider.copy(
                accountUid = existingProvider.accountUid ?: currentUid,
                name = if (normalizedName.isNotBlank()) normalizedName else existingProvider.name,
                serverUrl = normalizedUrl,
                m3uUrl = normalizedUrl,
                epgUrl = epgUrl.trim(),
                httpUserAgent = httpUserAgent,
                httpHeaders = httpHeaders,
                epgSyncMode = epgSyncMode,
                m3uVodClassificationEnabled = m3uVodClassificationEnabled,
                guideSourcePolicy = guideSourcePolicy,
                channelLogoSourcePolicy = channelLogoSourcePolicy,
                isActive = false,
                status = ProviderStatus.PARTIAL,
                lastSyncedAt = 0
            )
            providerDao.update(updated)
            syncProviderIdToFirestore(updated.id)
            updated.toPublicDomain()
        } else {
            val provider = Provider(
                accountUid = currentUid,
                name = providerName,
                type = ProviderType.M3U,
                serverUrl = normalizedUrl,
                m3uUrl = normalizedUrl,
                epgUrl = epgUrl.trim(),
                httpUserAgent = httpUserAgent,
                httpHeaders = httpHeaders,
                epgSyncMode = epgSyncMode,
                m3uVodClassificationEnabled = m3uVodClassificationEnabled,
                guideSourcePolicy = guideSourcePolicy,
                channelLogoSourcePolicy = channelLogoSourcePolicy,
                isActive = false,
                status = ProviderStatus.PARTIAL
            )
            val newId = insertProvider(provider)
            syncProviderIdToFirestore(newId)
            provider.copy(id = newId).copy(password = "")
        }

        handleInitialOnboardingSync(
            providerData = providerData,
            syncResult = syncManager.sync(providerData.id, force = true, onProgress = onProgress),
            onProgress = onProgress,
            syncFailurePrefix = "Playlist saved, but initial sync failed. The provider was saved and can be retried from Settings"
        )
    } catch (e: Exception) {
        Result.error("Failed to add M3U provider: ${e.message}", e)
    }

    override suspend fun loginJellyfin(
        serverUrl: String,
        username: String,
        password: String,
        name: String,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ): Result<Provider> {
        return try {
            val normalizedServerUrl = ProviderInputSanitizer.resolveUrlProtocol(
                ProviderInputSanitizer.normalizeUrl(serverUrl)
            )
            val normalizedUsername = ProviderInputSanitizer.normalizeUsername(username)
            val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)
            ProviderInputSanitizer.validateUrl(normalizedServerUrl)?.let { return Result.error(it) }
            val providerName = normalizedName.ifBlank {
                normalizedServerUrl.substringAfter("//").substringBefore("/").ifBlank { "Jellyfin" }
            }
            onProgress?.invoke("Signing in to Jellyfin...")
            val authResult = when (val res = jellyfinProvider.authenticate(
                serverUrl = normalizedServerUrl, username = normalizedUsername, password = password
            )) {
                is Result.Success -> res.data
                is Result.Error -> return Result.error(res.message, res.exception)
                is Result.Loading -> return Result.error("Unexpected loading state")
            }
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val existingProvider = if (id != null) providerDao.getById(id)?.toDomain() else null
            val providerData = if (existingProvider != null) {
                onProgress?.invoke("Updating existing provider...")
                val updated = existingProvider.copy(
                    accountUid = existingProvider.accountUid ?: currentUid,
                    name = providerName, serverUrl = normalizedServerUrl, username = normalizedUsername,
                    password = authResult, m3uUrl = "", epgUrl = "", httpUserAgent = "", httpHeaders = "",
                    isActive = false, status = ProviderStatus.PARTIAL, lastSyncedAt = 0
                )
                providerDao.update(updated.toSecureEntity())
                syncProviderIdToFirestore(updated.id)
                updated.copy(password = "")
            } else {
                val provider = Provider(accountUid = currentUid, name = providerName, type = ProviderType.JELLYFIN,
                    serverUrl = normalizedServerUrl, username = normalizedUsername, password = authResult,
                    isActive = false, status = ProviderStatus.PARTIAL)
                 val newId = insertProvider(provider)
                 syncProviderIdToFirestore(newId)
                 provider.copy(id = newId).copy(password = "")
            }
            handleInitialOnboardingSync(
                providerData = providerData,
                syncResult = syncManager.sync(providerData.id, force = false, onProgress = onProgress),
                onProgress = onProgress,
                syncFailurePrefix = "Jellyfin provider saved, but initial sync failed. The provider was saved and can be retried from Settings"
            )
        } catch (e: Exception) {
            Result.error("Failed to add Jellyfin provider: ${e.message}", e)
        }
    }

    override suspend fun loginJellyfinQuickConnect(
        serverUrl: String, name: String, onCode: ((String) -> Unit)?, onProgress: ((String) -> Unit)?, id: Long?
    ): Result<Provider> {
        return try {
            val normalizedServerUrl = ProviderInputSanitizer.resolveUrlProtocol(
                ProviderInputSanitizer.normalizeUrl(serverUrl)
            )
            val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)
            ProviderInputSanitizer.validateUrl(normalizedServerUrl)?.let { return Result.error(it) }
            val providerName = normalizedName.ifBlank {
                normalizedServerUrl.substringAfter("//").substringBefore("/").ifBlank { "Jellyfin" }
            }
            val existingProvider = if (id != null) providerDao.getById(id)?.toDomain() else null
            onProgress?.invoke("Requesting Quick Connect code...")
            val quickConnect = when (val quickConnectResult = jellyfinProvider.authenticateQuickConnect(
                serverUrl = normalizedServerUrl, onCode = onCode, onProgress = onProgress
            )) {
                is Result.Success -> quickConnectResult.data
                is Result.Error -> return Result.error(quickConnectResult.message, quickConnectResult.exception)
                is Result.Loading -> return Result.error("Unexpected loading state")
            }
            val providerData = saveJellyfinProvider(providerName = providerName,
                serverUrl = normalizedServerUrl, username = quickConnect.userName.ifBlank { providerName },
                password = quickConnect.accessToken, existingProvider = existingProvider)
            handleInitialOnboardingSync(
                providerData = providerData,
                syncResult = syncManager.sync(providerData.id, force = false, onProgress = onProgress),
                onProgress = onProgress,
                syncFailurePrefix = "Jellyfin provider saved, but initial sync failed. The provider was saved and can be retried from Settings"
            )
        } catch (e: Exception) {
            Result.error("Failed to add Jellyfin provider: ${e.message}", e)
        }
    }

    private suspend fun saveJellyfinProvider(
        providerName: String, serverUrl: String, username: String, password: String, existingProvider: Provider?
    ): Provider {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        return if (existingProvider != null) {
            val updated = existingProvider.copy(
                accountUid = existingProvider.accountUid ?: currentUid,
                name = providerName.ifBlank { existingProvider.name }, type = ProviderType.JELLYFIN,
                serverUrl = serverUrl, username = username, password = password,
                m3uUrl = "", epgUrl = "", httpUserAgent = "", httpHeaders = "",
                isActive = false, status = ProviderStatus.PARTIAL, lastSyncedAt = 0
            )
            providerDao.update(updated.toSecureEntity())
            syncProviderIdToFirestore(updated.id)
            updated.copy(password = "")
        } else {
            val provider = Provider(accountUid = currentUid, name = providerName, type = ProviderType.JELLYFIN,
                serverUrl = serverUrl, username = username, password = password,
                isActive = false, status = ProviderStatus.PARTIAL)
            val newId = insertProvider(provider)
            syncProviderIdToFirestore(newId)
            provider.copy(id = newId).copy(password = "")
        }
    }


    override suspend fun loginStalker(
        portalUrl: String,
        macAddress: String,
        name: String,
        authMode: StalkerAuthMode,
        username: String,
        password: String,
        httpUserAgent: String,
        httpHeaders: String,
        deviceProfile: String,
        timezone: String,
        locale: String,
        serialNumber: String,
        deviceId: String,
        deviceId2: String,
        signature: String,
        stalkerAdvancedOptionsJson: String,
        epgSyncMode: ProviderEpgSyncMode,
        guideSourcePolicy: GuideSourcePolicy,
        channelLogoSourcePolicy: ChannelLogoSourcePolicy,
        onProgress: ((String) -> Unit)?,
        id: Long?
    ): Result<Provider> {
        val normalizedPortalUrl = ProviderInputSanitizer.normalizeUrl(portalUrl)
        val normalizedMacAddress = ProviderInputSanitizer.normalizeMacAddress(macAddress)
        val normalizedName = ProviderInputSanitizer.normalizeProviderName(name)
        val normalizedUsername = ProviderInputSanitizer.normalizeUsername(username)
        val resolvedPortalUrl = ProviderInputSanitizer.resolveUrlProtocol(normalizedPortalUrl)
        val normalizedDeviceProfile = ProviderInputSanitizer.normalizeDeviceProfile(deviceProfile)
        val normalizedTimezone = ProviderInputSanitizer.normalizeTimezone(timezone)
        val normalizedLocale = ProviderInputSanitizer.normalizeLocale(locale)
        val normalizedSerialNumber = ProviderInputSanitizer.normalizeStalkerSerial(serialNumber)
        val normalizedDeviceId = ProviderInputSanitizer.normalizeStalkerDeviceId(deviceId)
        val normalizedDeviceId2 = ProviderInputSanitizer.normalizeStalkerDeviceId(deviceId2)
        val normalizedSignature = ProviderInputSanitizer.normalizeStalkerSignature(signature)
        val normalizedAdvancedOptionsJson = stalkerAdvancedOptionsJson.trim()

        ProviderInputSanitizer.validateUrl(resolvedPortalUrl)?.let { message ->
            return Result.error(message)
        }
        UrlSecurityPolicy.validateStalkerPortalUrl(resolvedPortalUrl)?.let { message ->
            return Result.error(message)
        }
        if (normalizedMacAddress.isNotBlank()) {
            ProviderInputSanitizer.validateMacAddress(normalizedMacAddress)?.let { message ->
                return Result.error(message)
            }
        }

        onProgress?.invoke("Authenticating...")
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val existingProvider = if (id != null) {
            // Edit path: check that the new normalized identity does not collide with a
            // different provider before we commit the update.
            val collision = providerDao.getByUrlAndUserForAccount(resolvedPortalUrl, normalizedUsername, normalizedMacAddress, accountUid = currentUid)
            if (collision != null && collision.id != id) {
                return Result.error("A Stalker provider with this portal URL and identity already exists.")
            }
            providerDao.getById(id)
        } else {
            providerDao.getByUrlAndUserForAccount(resolvedPortalUrl, normalizedUsername, normalizedMacAddress, accountUid = currentUid)
        }
        val effectivePassword = try {
            password.takeIf { it.isNotBlank() }
                ?: existingProvider?.password?.let(credentialCrypto::decryptIfNeeded)
                ?: ""
        } catch (e: CredentialDecryptionException) {
            return Result.error(e.message ?: CredentialDecryptionException.MESSAGE, e)
        }

        val provider = createStalkerProvider(
            providerId = 0L,
            portalUrl = resolvedPortalUrl,
            macAddress = normalizedMacAddress,
            authMode = authMode,
            username = normalizedUsername,
            password = effectivePassword,
            httpUserAgent = httpUserAgent,
            httpHeaders = httpHeaders,
            deviceProfile = normalizedDeviceProfile,
            timezone = normalizedTimezone,
            locale = normalizedLocale,
            serialNumber = normalizedSerialNumber,
            deviceId = normalizedDeviceId,
            deviceId2 = normalizedDeviceId2,
            signature = normalizedSignature,
            stalkerAdvancedOptionsJson = normalizedAdvancedOptionsJson
        )

        return when (val authResult = provider.authenticate()) {
            is Result.Success -> {
                val providerData = if (existingProvider != null) {
                    onProgress?.invoke("Updating existing provider...")
                    val updated = authResult.data.copy(
                        id = existingProvider.id,
                        accountUid = existingProvider.accountUid ?: currentUid,
                        name = normalizedName.ifBlank { existingProvider.name },
                        serverUrl = resolvedPortalUrl,
                        username = normalizedUsername,
                        password = effectivePassword,
                        httpUserAgent = httpUserAgent,
                        httpHeaders = httpHeaders,
                        stalkerMacAddress = normalizedMacAddress,
                        stalkerDeviceProfile = normalizedDeviceProfile,
                        stalkerDeviceTimezone = normalizedTimezone,
                        stalkerDeviceLocale = normalizedLocale,
                        stalkerSerialNumber = normalizedSerialNumber,
                        stalkerDeviceId = normalizedDeviceId,
                        stalkerDeviceId2 = normalizedDeviceId2,
                        stalkerSignature = normalizedSignature,
                        stalkerAdvancedOptionsJson = normalizedAdvancedOptionsJson,
                        epgUrl = existingProvider.epgUrl,
                        epgSyncMode = epgSyncMode,
                        guideSourcePolicy = guideSourcePolicy,
                        channelLogoSourcePolicy = channelLogoSourcePolicy,
                        xtreamFastSyncEnabled = false,
                        m3uVodClassificationEnabled = false,
                        isActive = false,
                        status = ProviderStatus.PARTIAL,
                        lastSyncedAt = 0L,
                        createdAt = existingProvider.createdAt
                    )
                    providerDao.update(updated.toSecureEntity())
                    syncProviderIdToFirestore(updated.id)
                    updated.copy(password = "")
                } else {
                    val newData = authResult.data.copy(
                        accountUid = currentUid,
                        name = normalizedName.ifBlank { authResult.data.name },
                        serverUrl = resolvedPortalUrl,
                        username = normalizedUsername,
                        password = effectivePassword,
                        httpUserAgent = httpUserAgent,
                        httpHeaders = httpHeaders,
                        stalkerMacAddress = normalizedMacAddress,
                        stalkerDeviceProfile = normalizedDeviceProfile,
                        stalkerDeviceTimezone = normalizedTimezone,
                        stalkerDeviceLocale = normalizedLocale,
                        stalkerSerialNumber = normalizedSerialNumber,
                        stalkerDeviceId = normalizedDeviceId,
                        stalkerDeviceId2 = normalizedDeviceId2,
                        stalkerSignature = normalizedSignature,
                        stalkerAdvancedOptionsJson = normalizedAdvancedOptionsJson,
                        epgSyncMode = epgSyncMode,
                        guideSourcePolicy = guideSourcePolicy,
                        channelLogoSourcePolicy = channelLogoSourcePolicy,
                        xtreamFastSyncEnabled = false,
                        m3uVodClassificationEnabled = false,
                        isActive = false,
                        status = ProviderStatus.PARTIAL
                    )
                    val newId = insertProvider(newData)
                    syncProviderIdToFirestore(newId)
                    newData.copy(id = newId).copy(password = "")
                }

                handleInitialOnboardingSync(
                    providerData = providerData,
                    syncResult = syncManager.sync(providerData.id, force = false, onProgress = onProgress),
                    onProgress = onProgress,
                    syncFailurePrefix = "Provider login succeeded, but initial sync failed. The provider was saved and can be retried from Settings"
                )
            }
            is Result.Error -> Result.error(authResult.message, authResult.exception)
            is Result.Loading -> Result.error("Unexpected loading state")
        }
    }

    private suspend fun handleInitialOnboardingSync(
        providerData: Provider,
        syncResult: Result<Unit>,
        onProgress: ((String) -> Unit)? = null,
        syncFailurePrefix: String
    ): Result<Provider> = when (syncResult) {
        is Result.Success -> {
            val finalStatus = if (syncManager.currentSyncState(providerData.id) is SyncState.Partial) {
                ProviderStatus.PARTIAL
            } else {
                ProviderStatus.ACTIVE
            }
                if (!hasUsableLiveCatalogForActivation(
                    providerData.id,
                    providerData.type,
                    channelDao,
                    categoryDao,
                    syncMetadataRepository
                )) {
                updateProviderSyncStatus(
                    providerData.id,
                    ProviderStatus.PARTIAL,
                    lastSyncedAt = System.currentTimeMillis(),
                    isActive = false
                )
                syncManager.scheduleProviderSyncResume(providerData.id)
                val message = "$syncFailurePrefix: Sync did not finish with any committed content."
                Result.error(
                    message,
                    ProviderSavedWithSyncErrorException(
                        provider = providerData.copy(status = ProviderStatus.PARTIAL, isActive = false),
                        message = message
                    )
                )
            } else {
                onProgress?.invoke("4/4 • IPTV aktif ediliyor…")
                providerDao.setActive(providerData.id)
                preferencesRepository.setLastActiveProviderId(providerData.id)
                preferencesRepository.setActiveLiveSource(com.kaynanamtv.domain.model.ActiveLiveSource.ProviderSource(providerData.id))
                updateProviderSyncStatus(
                    providerData.id,
                    finalStatus,
                    lastSyncedAt = System.currentTimeMillis()
                )
                maybeScheduleBackgroundEpgSync(providerData.id)
                Result.success(providerData.copy(status = finalStatus, isActive = true))
            }
        }
        is Result.Error -> {
            updateProviderSyncStatus(providerData.id, ProviderStatus.PARTIAL, isActive = false)
            syncManager.scheduleProviderSyncResume(providerData.id)
            val message = "$syncFailurePrefix: ${syncResult.message}"
            Result.error(
                message,
                ProviderSavedWithSyncErrorException(
                    provider = providerData.copy(status = ProviderStatus.PARTIAL, isActive = false),
                    message = message,
                    cause = syncResult.exception
                )
            )
        }
        is Result.Loading -> Result.error("Unexpected loading state")
    }

    /**
     * Delegates to [SyncManager] — the single source of truth for the full sync pipeline.
     */
    override suspend fun refreshProviderData(
        providerId: Long,
        force: Boolean,
        movieFastSyncOverride: Boolean?,
        epgSyncModeOverride: ProviderEpgSyncMode?,
        onProgress: ((String) -> Unit)?
    ): Result<Unit> {
        return when (
            val syncResult = syncManager.sync(
                providerId,
                force = force,
                movieFastSyncOverride = movieFastSyncOverride,
                epgSyncModeOverride = epgSyncModeOverride,
                onProgress = onProgress
            )
        ) {
            is Result.Success -> {
                val finalStatus = if (syncManager.currentSyncState(providerId) is SyncState.Partial) {
                    ProviderStatus.PARTIAL
                } else {
                    ProviderStatus.ACTIVE
                }
                val provider = providerDao.getById(providerId)
                if (provider != null && !hasUsableLiveCatalogForActivation(
                        providerId,
                        provider.type,
                        channelDao,
                        categoryDao,
                        syncMetadataRepository
                    )) {
                    updateProviderSyncStatus(
                        providerId,
                        ProviderStatus.PARTIAL,
                        lastSyncedAt = System.currentTimeMillis(),
                        isActive = false
                    )
                    syncManager.scheduleProviderSyncResume(providerId)
                } else {
                    updateProviderSyncStatus(providerId, finalStatus, System.currentTimeMillis())
                    maybeScheduleBackgroundEpgSync(providerId)
                }
                syncResult
            }
            is Result.Error -> {
                updateProviderSyncStatus(providerId, ProviderStatus.ERROR)
                syncResult
            }
            is Result.Loading -> Result.error("Unexpected loading state")
        }
    }

    override suspend fun getProgramsForLiveStream(
        providerId: Long,
        streamId: Long,
        epgChannelId: String?,
        limit: Int
    ): Result<List<Program>> {
        val providerEntity = providerDao.getById(providerId)
            ?: return Result.error("Provider $providerId not found")
        if (!allowsOnDemandGuide(providerEntity.toPublicDomain())) {
            return Result.error("On-demand guide lookup is disabled for this provider.")
        }
        return when (providerEntity.type) {
            ProviderType.XTREAM_CODES -> when (val providerContextResult = createXtreamLiveProgramProviderContext(providerId)) {
                is Result.Success -> {
                    val result = fetchXtreamProgramsForLiveStream(
                        providerId = providerId,
                        streamId = streamId,
                        epgChannelId = epgChannelId,
                        limit = limit,
                        xtreamProvider = providerContextResult.data
                    )
                    if (result is Result.Success && result.data.isNotEmpty()) {
                        cacheProgramsForChannel(providerId, result.data)
                        refreshCachedEpgMetadata(providerId)
                    }
                    result
                }
                is Result.Error -> Result.error(providerContextResult.message, providerContextResult.exception)
                is Result.Loading -> Result.error("Unexpected loading state")
            }
            ProviderType.STALKER_PORTAL -> {
                val stalkerProvider = createStalkerProviderFromEntity(providerEntity)
                val channelKey = epgChannelId?.takeIf { it.isNotBlank() } ?: streamId.toString()
                when (val result = stalkerProvider.getShortEpg(channelKey, limit)) {
                    is Result.Success -> {
                        if (result.data.isNotEmpty()) {
                            cacheProgramsForChannel(providerId, result.data)
                            refreshCachedEpgMetadata(providerId)
                        }
                        result
                    }
                    is Result.Error -> Result.error(result.message, result.exception)
                    is Result.Loading -> Result.error("Unexpected loading state")
                }
            }
            ProviderType.M3U,
            ProviderType.JELLYFIN -> Result.error("On-demand guide lookup is unavailable for this provider.")
        }
    }

    override suspend fun getProgramsForLiveStreams(
        providerId: Long,
        requests: List<LiveStreamProgramRequest>,
        limit: Int
    ): Map<LiveStreamProgramRequest, Result<List<Program>>> {
        val normalizedRequests = requests
            .filter { it.streamId > 0L }
            .distinct()
        if (normalizedRequests.isEmpty()) {
            return emptyMap()
        }

        val providerEntity = providerDao.getById(providerId)
            ?: return normalizedRequests.associateWith { Result.error("Provider $providerId not found") }
        if (!allowsOnDemandGuide(providerEntity.toPublicDomain())) {
            return normalizedRequests.associateWith {
                Result.error("On-demand guide lookup is disabled for this provider.")
            }
        }

        return when (providerEntity.type) {
            ProviderType.XTREAM_CODES -> when (val providerContextResult = createXtreamLiveProgramProviderContext(providerId)) {
                is Result.Success -> coroutineScope {
                    val requestDispatcher = Dispatchers.IO.limitedParallelism(XTREAM_GUIDE_BATCH_CONCURRENCY)
                    normalizedRequests
                        .map { request ->
                            async(requestDispatcher) {
                                request to fetchXtreamProgramsForLiveStream(
                                    providerId = providerId,
                                    streamId = request.streamId,
                                    epgChannelId = request.epgChannelId,
                                    limit = limit,
                                    xtreamProvider = providerContextResult.data
                                )
                            }
                        }
                        .awaitAll()
                        .also { results ->
                            val cachedPrograms = results
                                .mapNotNull { (_, result) -> (result as? Result.Success)?.data }
                                .flatten()
                            if (cachedPrograms.isNotEmpty()) {
                                cacheProgramsForChannels(providerId, cachedPrograms)
                                refreshCachedEpgMetadata(providerId)
                            }
                        }
                        .toMap()
                }
                is Result.Error -> normalizedRequests.associateWith {
                    Result.error(providerContextResult.message, providerContextResult.exception)
                }
                is Result.Loading -> normalizedRequests.associateWith {
                    Result.error("Unexpected loading state")
                }
            }
            ProviderType.STALKER_PORTAL -> {
                val stalkerProvider = createStalkerProviderFromEntity(providerEntity)
                val results = normalizedRequests.associateWith { request ->
                    stalkerProvider.getShortEpg(
                        request.epgChannelId?.takeIf { it.isNotBlank() } ?: request.streamId.toString(),
                        limit
                    )
                }
                val cachedPrograms = results.values
                    .mapNotNull { (it as? Result.Success)?.data }
                    .flatten()
                if (cachedPrograms.isNotEmpty()) {
                    cacheProgramsForChannels(providerId, cachedPrograms)
                    refreshCachedEpgMetadata(providerId)
                }
                results
            }
            ProviderType.M3U,
            ProviderType.JELLYFIN -> normalizedRequests.associateWith {
                Result.error("On-demand guide lookup is unavailable for this provider.")
            }
        }
    }

    override suspend fun buildCatchUpUrl(providerId: Long, streamId: Long, start: Long, end: Long): String? {
        return buildCatchUpUrls(providerId, streamId, start, end).firstOrNull()
    }

    override suspend fun buildCatchUpUrls(providerId: Long, streamId: Long, start: Long, end: Long): List<String> {
        val providerEntity = providerDao.getById(providerId) ?: return emptyList()
        val provider = providerEntity.toPublicDomain()
        val providerPassword = credentialCrypto.decryptIfNeeded(providerEntity.password)
        val channel = channelDao.getById(streamId)
        val resolvedStreamId = channel?.streamId?.takeIf { it > 0 } ?: streamId
        return when (provider.type) {
            ProviderType.XTREAM_CODES -> createXtreamProvider(
                providerId = providerId,
                serverUrl = provider.serverUrl,
                username = provider.username,
                password = providerPassword,
                allowedOutputFormats = provider.allowedOutputFormats,
                httpUserAgent = provider.httpUserAgent,
                httpHeaders = provider.httpHeaders
            ).buildCatchUpUrls(resolvedStreamId, start, end)
            ProviderType.M3U -> {
                val source = channel?.catchUpSource ?: return emptyList()
                buildM3uCatchUpUrls(source, start, end)
            }
            ProviderType.STALKER_PORTAL -> createStalkerProviderFromEntity(providerEntity).buildCatchUpUrls(
                streamId = resolvedStreamId,
                start = start,
                end = end,
                sourceStreamUrl = channel?.streamUrl,
                sourceCatchUpSource = channel?.catchUpSource
            )
            ProviderType.JELLYFIN -> emptyList()
        }
    }

    suspend fun createXtreamProvider(
        providerId: Long,
        serverUrl: String,
        username: String,
        password: String,
        allowedOutputFormats: List<String> = emptyList(),
        httpUserAgent: String = "",
        httpHeaders: String = ""
    ): IptvProvider {
        val enableBase64TextCompatibility = preferencesRepository.xtreamBase64TextCompatibility.first()
        return XtreamProvider(
            providerId = providerId,
            api = xtreamApiService,
            serverUrl = serverUrl,
            username = username,
            password = password,
            allowedOutputFormats = allowedOutputFormats,
            enableBase64TextCompatibility = enableBase64TextCompatibility,
            requestProfile = buildGenericProviderRequestProfile(
                ownerTag = "provider:$providerId/xtream",
                httpUserAgent = httpUserAgent,
                httpHeaders = httpHeaders
            )
        )
    }

    private fun createStalkerProvider(
        providerId: Long,
        portalUrl: String,
        macAddress: String,
        authMode: StalkerAuthMode,
        username: String,
        password: String,
        httpUserAgent: String = "",
        httpHeaders: String = "",
        portalFingerprintHint: StalkerPortalFingerprint = StalkerPortalFingerprint.BASIC_MAC,
        magPresetHint: StalkerMagPreset = StalkerMagPreset.GENERIC_SAFE,
        bootstrapRecipeHint: StalkerBootstrapRecipe = StalkerBootstrapRecipe.GENERIC_SAFE,
        endpointPreferenceHint: StalkerEndpointPreference = StalkerEndpointPreference.AUTO,
        cookieModeHint: StalkerCookieMode = StalkerCookieMode.NONE,
        playbackBackendHint: StalkerPlaybackBackendHint = StalkerPlaybackBackendHint.AUTO,
        portalProfileHint: StalkerPortalProfile = StalkerPortalProfile.MAG_BASIC,
        preferredPlaybackMode: StalkerPlaybackMode? = null,
        deviceProfile: String,
        timezone: String,
        locale: String,
        serialNumber: String = "",
        deviceId: String = "",
        deviceId2: String = "",
        signature: String = "",
        stalkerAdvancedOptionsJson: String = ""
    ): StalkerProvider {
        return StalkerProvider(
            providerId = providerId,
            api = stalkerApiService,
            portalUrl = portalUrl,
            macAddress = macAddress,
            authMode = authMode,
            username = username,
            password = password,
            httpUserAgent = httpUserAgent,
            httpHeaders = httpHeaders,
            portalFingerprintHint = portalFingerprintHint,
            magPresetHint = magPresetHint,
            bootstrapRecipeHint = bootstrapRecipeHint,
            endpointPreferenceHint = endpointPreferenceHint,
            cookieModeHint = cookieModeHint,
            playbackBackendHint = playbackBackendHint,
            portalProfileHint = portalProfileHint,
            preferredPlaybackMode = preferredPlaybackMode,
            deviceProfile = deviceProfile,
            timezone = timezone,
            locale = locale,
            serialNumber = serialNumber,
            deviceId = deviceId,
            deviceId2 = deviceId2,
            signature = signature,
            stalkerAdvancedOptionsJson = stalkerAdvancedOptionsJson
        )
    }

    private fun createStalkerProviderFromEntity(entity: ProviderEntity): StalkerProvider {
        return createStalkerProvider(
            providerId = entity.id,
            portalUrl = entity.serverUrl,
            macAddress = entity.stalkerMacAddress,
            authMode = entity.stalkerAuthMode,
            username = entity.username,
            password = try {
                credentialCrypto.decryptIfNeeded(entity.password)
            } catch (_: Throwable) {
                ""
            },
            httpUserAgent = entity.httpUserAgent,
            httpHeaders = entity.httpHeaders,
            portalFingerprintHint = entity.stalkerPortalFingerprint,
            magPresetHint = entity.stalkerMagPreset,
            bootstrapRecipeHint = entity.stalkerLastBootstrapRecipe,
            endpointPreferenceHint = entity.stalkerEndpointPreference,
            cookieModeHint = entity.stalkerCookieMode,
            playbackBackendHint = entity.stalkerPlaybackBackendHint,
            portalProfileHint = entity.stalkerPortalProfile,
            preferredPlaybackMode = entity.stalkerLastPlaybackMode
                ?.let { runCatching { StalkerPlaybackMode.valueOf(it) }.getOrNull() },
            deviceProfile = entity.stalkerDeviceProfile,
            timezone = entity.stalkerDeviceTimezone,
            locale = entity.stalkerDeviceLocale,
            serialNumber = entity.stalkerSerialNumber,
            deviceId = entity.stalkerDeviceId,
            deviceId2 = entity.stalkerDeviceId2,
            signature = entity.stalkerSignature,
            stalkerAdvancedOptionsJson = entity.stalkerAdvancedOptionsJson
        )
    }

    private fun ProviderEntity.toPublicDomain(): Provider {
        return toDomain().copy(password = "")
    }

    private suspend fun createXtreamLiveProgramProviderContext(providerId: Long): Result<XtreamProvider> {
        if (providerId <= 0L) {
            return Result.error("Live stream context is unavailable.")
        }

        val providerEntity = providerDao.getById(providerId)
            ?: return Result.error("Provider $providerId not found")
        val provider = providerEntity.toPublicDomain()
        if (provider.type != ProviderType.XTREAM_CODES) {
            return Result.error("On-demand guide lookup is available only for Xtream providers.")
        }

        val providerPassword = try {
            credentialCrypto.decryptIfNeeded(providerEntity.password)
        } catch (e: CredentialDecryptionException) {
            return Result.error(e.message ?: CredentialDecryptionException.MESSAGE, e)
        }

        return Result.success(
            createXtreamProvider(
                providerId = providerId,
                serverUrl = provider.serverUrl,
                username = provider.username,
                password = providerPassword,
                allowedOutputFormats = provider.allowedOutputFormats,
                httpUserAgent = provider.httpUserAgent,
                httpHeaders = provider.httpHeaders
            ) as XtreamProvider
        )
    }

    private suspend fun fetchXtreamProgramsForLiveStream(
        providerId: Long,
        streamId: Long,
        epgChannelId: String?,
        limit: Int,
        xtreamProvider: XtreamProvider
    ): Result<List<Program>> {
        if (providerId <= 0L || streamId <= 0L) {
            return Result.error("Live stream context is unavailable.")
        }

        val shortProgramsResult = xtreamProvider.getShortEpg(
            channelId = streamId.toString(),
            limit = limit.coerceAtLeast(1)
        )
        val shortPrograms = (shortProgramsResult as? Result.Success)?.data
            ?.sortedBy { it.startTime }
            .orEmpty()
        if (shortPrograms.isNotEmpty()) {
            return Result.success(
                normalizeXtreamPrograms(
                    providerId = providerId,
                    channelId = epgChannelId ?: streamId.toString(),
                    programs = shortPrograms
                )
            )
        }

        return when (val fullProgramsResult = xtreamProvider.getEpg(streamId.toString())) {
            is Result.Success -> {
                val normalizedPrograms = normalizeXtreamPrograms(
                    providerId = providerId,
                    channelId = epgChannelId ?: streamId.toString(),
                    programs = fullProgramsResult.data.sortedBy { it.startTime }
                )
                Result.success(normalizedPrograms)
            }
            is Result.Error -> {
                val shortError = shortProgramsResult as? Result.Error
                val combinedMessage = listOfNotNull(
                    shortError?.message?.takeIf { it.isNotBlank() },
                    fullProgramsResult.message.takeIf { it.isNotBlank() }
                )
                    .distinct()
                    .joinToString(separator = " / ")
                    .ifBlank { "Failed to load on-demand guide" }
                Result.error(combinedMessage, fullProgramsResult.exception ?: shortError?.exception)
            }
            is Result.Loading -> Result.error("Unexpected loading state")
        }
    }

    private fun Provider.toSecureEntity(): ProviderEntity {
        val encryptedPassword = credentialCrypto.encryptIfNeeded(password)
        return copy(password = encryptedPassword).toEntity()
    }

    private suspend fun updateProviderSyncStatus(
        providerId: Long,
        status: ProviderStatus,
        lastSyncedAt: Long? = null,
        isActive: Boolean? = null
    ) {
        val current = providerDao.getById(providerId) ?: return
        val updated = current.copy(
            status = status,
            lastSyncedAt = lastSyncedAt ?: current.lastSyncedAt,
            isActive = isActive ?: current.isActive
        )
        providerDao.update(updated)
        syncProviderIdToFirestore(providerId)
    }

    private suspend fun maybeScheduleBackgroundEpgSync(providerId: Long) {
        val provider = providerDao.getById(providerId) ?: return
        if (provider.epgSyncMode != ProviderEpgSyncMode.BACKGROUND) {
            return
        }
        // The previous implementation launched a coroutine that slept for 15s and then
        // scheduled the worker. That kept a coroutine alive (and held onto its captures)
        // even when the user immediately backed out of the screen. WorkManager's own
        // initialDelay is the right place for that wait — it's persisted, cancellable,
        // and doesn't pin any process state.
        syncManager.scheduleBackgroundEpgSync(providerId)
    }

    private fun normalizeXtreamPrograms(
        providerId: Long,
        channelId: String,
        programs: List<Program>
    ): List<Program> {
        return programs.map { program ->
            program.copy(
                providerId = providerId,
                channelId = channelId
            )
        }
    }

    private suspend fun cacheProgramsForChannel(providerId: Long, programs: List<Program>) {
        val channelId = programs.firstOrNull()?.channelId ?: return
        transactionRunner.inTransaction {
            programDao.deleteForChannel(providerId, channelId)
            programDao.insertAll(programs.map { it.toEntity().copy(providerId = providerId) })
        }
    }

    private suspend fun cacheProgramsForChannels(providerId: Long, programs: List<Program>) {
        if (programs.isEmpty()) return
        val programsByChannel = programs.groupBy { it.channelId }
        transactionRunner.inTransaction {
            programsByChannel.forEach { (channelId, channelPrograms) ->
                programDao.deleteForChannel(providerId, channelId)
                programDao.insertAll(channelPrograms.map { it.toEntity().copy(providerId = providerId) })
            }
        }
    }

    private suspend fun refreshCachedEpgMetadata(providerId: Long) {
        val now = System.currentTimeMillis()
        val metadata = (syncMetadataRepository.getMetadata(providerId) ?: SyncMetadata(providerId)).copy(
            lastEpgSync = now,
            lastEpgSuccess = now,
            epgCount = programDao.countByProvider(providerId)
        )
        syncMetadataRepository.updateMetadata(metadata)
    }

    private fun allowsOnDemandGuide(provider: Provider): Boolean = when (provider.guideSourcePolicy) {
        GuideSourcePolicy.AUTO,
        GuideSourcePolicy.PROVIDER_ONLY -> true
        GuideSourcePolicy.EXTERNAL_ONLY,
        GuideSourcePolicy.DISABLED -> provider.type != ProviderType.XTREAM_CODES && provider.type != ProviderType.STALKER_PORTAL
    }

    private fun syncProviderIdToFirestore(providerId: Long) {
        repositoryScope.launch {
            runCatching {
                val entity = providerDao.getById(providerId) ?: run {
                    Log.w("ProviderRepository", "[CLOUD_UPLOAD_FAIL] id=$providerId not found in Room")
                    return@launch
                }
                val cleartextPassword = try {
                    credentialCrypto.decryptIfNeeded(entity.password)
                } catch (e: Exception) {
                    ""
                }
                val provider = entity.toPublicDomain().copy(password = cleartextPassword)
                syncProviderToFirestore(provider)
            }.onFailure { throwable ->
                Log.e("ProviderRepository", "[CLOUD_UPLOAD_FAIL] id=$providerId error=${throwable.message}", throwable)
            }
        }
    }

    private suspend fun syncProviderToFirestore(provider: Provider) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.i("ProviderRepository", "[CLOUD_UPLOAD_SKIP] No authenticated Firebase user for provider id=${provider.id}")
            return
        }
        val targetUid = user.uid
        if (provider.accountUid != null && provider.accountUid != targetUid) {
            Log.w("ProviderRepository", "[CLOUD_UPLOAD_BLOCKED] Provider ${provider.id} owned by ${provider.accountUid}, but current user is $targetUid")
            return
        }
        // If provider in Room had null accountUid, bind it to current user
        if (provider.accountUid == null) {
            val entity = providerDao.getById(provider.id)
            if (entity != null && entity.accountUid == null) {
                providerDao.update(entity.copy(accountUid = targetUid))
            }
        }
        val firestore = FirebaseFirestore.getInstance()
        Log.i("ProviderRepository", "[CLOUD_UPLOAD_START] id=${provider.id} uid=$targetUid")
        Log.i("ProviderRepository", "[CLOUD_UPLOAD_PATH] users/$targetUid/providers/${provider.id}")
        try {
            val cleartextPassword = try {
                credentialCrypto.decryptIfNeeded(provider.password)
            } catch (e: Exception) {
                provider.password
            }
            val accountEncryptedPassword = try {
                accountE2eeCrypto.encryptForAccount(cleartextPassword, targetUid)
            } catch (e: Exception) {
                cleartextPassword
            }
            val data = hashMapOf(
                "id" to provider.id,
                "name" to provider.name,
                "type" to provider.type.name,
                "serverUrl" to provider.serverUrl,
                "username" to provider.username,
                "password" to accountEncryptedPassword,
                "m3uUrl" to provider.m3uUrl,
                "epgUrl" to provider.epgUrl,
                "httpUserAgent" to provider.httpUserAgent,
                "httpHeaders" to provider.httpHeaders,
                "stalkerMacAddress" to provider.stalkerMacAddress,
                "stalkerDeviceProfile" to provider.stalkerDeviceProfile,
                "stalkerDeviceTimezone" to provider.stalkerDeviceTimezone,
                "stalkerDeviceLocale" to provider.stalkerDeviceLocale,
                "stalkerSerialNumber" to provider.stalkerSerialNumber,
                "stalkerDeviceId" to provider.stalkerDeviceId,
                "stalkerDeviceId2" to provider.stalkerDeviceId2,
                "stalkerSignature" to provider.stalkerSignature,
                "stalkerAdvancedOptionsJson" to provider.stalkerAdvancedOptionsJson,
                "stalkerAuthMode" to provider.stalkerAuthMode.name,
                "stalkerPortalProfile" to provider.stalkerPortalProfile.name,
                "stalkerPortalFingerprint" to provider.stalkerPortalFingerprint.name,
                "stalkerMagPreset" to provider.stalkerMagPreset.name,
                "stalkerLastBootstrapRecipe" to provider.stalkerLastBootstrapRecipe.name,
                "stalkerEndpointPreference" to provider.stalkerEndpointPreference.name,
                "stalkerCookieMode" to provider.stalkerCookieMode.name,
                "stalkerPlaybackBackendHint" to provider.stalkerPlaybackBackendHint.name,
                "stalkerLastPlaybackMode" to provider.stalkerLastPlaybackMode,
                "stalkerCredentialsRequired" to provider.stalkerCredentialsRequired,
                "stalkerMacRequired" to provider.stalkerMacRequired,
                "stalkerUsesTemporaryLinks" to provider.stalkerUsesTemporaryLinks,
                "stalkerModuleRestricted" to provider.stalkerModuleRestricted,
                "stalkerStrictFingerprintRequired" to provider.stalkerStrictFingerprintRequired,
                "stalkerRecipeFallbackUsed" to provider.stalkerRecipeFallbackUsed,
                "stalkerRecipeRediscoveryAttempts" to provider.stalkerRecipeRediscoveryAttempts,
                "isActive" to provider.isActive,
                "maxConnections" to provider.maxConnections,
                "expirationDate" to provider.expirationDate,
                "apiVersion" to provider.apiVersion,
                "allowedOutputFormats" to provider.allowedOutputFormats,
                "epgSyncMode" to provider.epgSyncMode.name,
                "guideSourcePolicy" to provider.guideSourcePolicy.name,
                "channelLogoSourcePolicy" to provider.channelLogoSourcePolicy.name,
                "xtreamFastSyncEnabled" to provider.xtreamFastSyncEnabled,
                "xtreamLiveSyncMode" to provider.xtreamLiveSyncMode.name,
                "m3uVodClassificationEnabled" to provider.m3uVodClassificationEnabled,
                "status" to provider.status.name,
                "lastSyncedAt" to provider.lastSyncedAt,
                "createdAt" to provider.createdAt
            )
            firestore.collection("users").document(targetUid)
                .collection("providers").document(provider.id.toString())
                .set(data).await()
            Log.i("ProviderRepository", "[CLOUD_UPLOAD_SUCCESS] id=${provider.id} path=users/$targetUid/providers/${provider.id}")
        } catch (e: Exception) {
            Log.e("ProviderRepository", "[CLOUD_UPLOAD_FAIL] id=${provider.id} error=${e.message}", e)
            throw e
        }
    }

    private suspend fun deleteProviderFromFirestore(providerId: Long) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val entity = providerDao.getById(providerId)
        if (entity != null && entity.accountUid != null && entity.accountUid != user.uid) {
            Log.w("ProviderRepository", "Blocked cloud delete: Provider $providerId owned by ${entity.accountUid}, not ${user.uid}")
            return
        }
        val firestore = FirebaseFirestore.getInstance()
        try {
            firestore.collection("users").document(user.uid)
                .collection("providers").document(providerId.toString())
                .delete().await()
            Log.d("ProviderRepository", "Deleted provider $providerId from Firestore")
        } catch (e: Exception) {
            Log.e("ProviderRepository", "Failed to delete provider from Firestore", e)
        }
    }

    private fun generateDeterministicId(
        type: ProviderType,
        serverUrl: String,
        username: String,
        m3uUrl: String,
        stalkerMacAddress: String,
        accountUid: String? = null
    ): Long {
        val accountPart = if (!accountUid.isNullOrBlank()) "|$accountUid" else ""
        val key = when (type) {
            ProviderType.XTREAM_CODES -> "XTREAM|${serverUrl.trim().lowercase()}|${username.trim().lowercase()}$accountPart"
            ProviderType.M3U -> "M3U|${m3uUrl.trim().lowercase()}$accountPart"
            ProviderType.STALKER_PORTAL -> "STALKER|${serverUrl.trim().lowercase()}|${stalkerMacAddress.trim().lowercase()}$accountPart"
            ProviderType.JELLYFIN -> "JELLYFIN|${serverUrl.trim().lowercase()}|${username.trim().lowercase()}$accountPart"
        }
        return kotlin.math.abs(fnv1a64(key))
    }

    private fun generateDeterministicId(provider: Provider): Long {
        return generateDeterministicId(
            type = provider.type,
            serverUrl = provider.serverUrl,
            username = provider.username,
            m3uUrl = provider.m3uUrl,
            stalkerMacAddress = provider.stalkerMacAddress,
            accountUid = provider.accountUid
        )
    }

    private fun fnv1a64(key: String): Long {
        var hash = -0x7a36a4a58b72cd0bL
        for (i in 0 until key.length) {
            hash = hash xor key[i].code.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }

    private suspend fun insertProvider(provider: Provider): Long {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val boundProvider = if (provider.accountUid == null && currentUid != null) {
            provider.copy(accountUid = currentUid)
        } else {
            provider
        }
        val deterministicId = generateDeterministicId(boundProvider)
        val providerWithId = boundProvider.copy(id = deterministicId)
        providerDao.insert(providerWithId.toSecureEntity())
        Log.i("ProviderRepository", "[ROOM_UPSERT_SUCCESS] id=$deterministicId accountUid=${boundProvider.accountUid}")
        return deterministicId
    }
}

internal fun buildM3uCatchUpUrls(source: String, start: Long, end: Long): List<String> {
    val trimmedSource = source.trim()
    if (trimmedSource.isBlank()) return emptyList()

    val durationSeconds = (end - start).coerceAtLeast(0L)
    val durationMinutes = (durationSeconds / 60L).coerceAtLeast(1L)
    val startDate = java.time.Instant.ofEpochSecond(start).atZone(java.time.ZoneOffset.UTC)
    val endDate = java.time.Instant.ofEpochSecond(end).atZone(java.time.ZoneOffset.UTC)
    val compactStart = startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
    val compactEnd = endDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))

    val replacements = linkedMapOf(
        "{start}" to start.toString(),
        "{end}" to end.toString(),
        "{duration}" to durationSeconds.toString(),
        "{duration_seconds}" to durationSeconds.toString(),
        "{duration_minutes}" to durationMinutes.toString(),
        "{utc}" to start.toString(),
        "{utcend}" to end.toString(),
        "{lutc}" to end.toString(),
        "{timestamp}" to start.toString(),
        "{Y}" to startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy")),
        "{m}" to startDate.format(java.time.format.DateTimeFormatter.ofPattern("MM")),
        "{d}" to startDate.format(java.time.format.DateTimeFormatter.ofPattern("dd")),
        "{H}" to startDate.format(java.time.format.DateTimeFormatter.ofPattern("HH")),
        "{M}" to startDate.format(java.time.format.DateTimeFormatter.ofPattern("mm")),
        "{S}" to startDate.format(java.time.format.DateTimeFormatter.ofPattern("ss")),
        "{Ymd}" to startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")),
        "{YmdHis}" to compactStart,
        "{utc:yyyyMMddHHmmss}" to compactStart,
        "{utcend:yyyyMMddHHmmss}" to compactEnd
    )

    val expanded = replacements.entries.fold(trimmedSource) { current, (placeholder, value) ->
        current.replace(placeholder, value)
    }

    return listOf(expanded).distinct()
}
