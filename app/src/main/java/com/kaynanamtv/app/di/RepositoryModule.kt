package com.kaynanamtv.app.di

import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.RoomDatabaseTransactionRunner
import com.kaynanamtv.data.manager.DownloadManagerImpl
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.security.AndroidKeystoreCredentialCrypto
import com.kaynanamtv.data.security.CredentialCrypto
import com.kaynanamtv.data.sync.ProviderSyncStateReaderImpl
import com.kaynanamtv.data.validation.ProviderSetupInputValidatorImpl
import com.kaynanamtv.domain.manager.ParentalPinVerifier
import com.kaynanamtv.domain.manager.ProviderSetupInputValidator
import com.kaynanamtv.domain.manager.ProviderSyncStateReader
import com.kaynanamtv.data.repository.*
import com.kaynanamtv.domain.manager.ParentalControlSessionStore
import com.kaynanamtv.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

import com.kaynanamtv.data.repository.AuthRepositoryImpl
import com.kaynanamtv.domain.repository.AuthRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindCommunityChatRepository(impl: CommunityChatRepositoryImpl): CommunityChatRepository

    @Binds @Singleton
    abstract fun bindProviderRepository(impl: ProviderRepositoryImpl): ProviderRepository

    @Binds @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds @Singleton
    abstract fun bindCombinedM3uRepository(impl: CombinedM3uRepositoryImpl): CombinedM3uRepository

    @Binds @Singleton
    abstract fun bindMovieRepository(impl: MovieRepositoryImpl): MovieRepository

    @Binds @Singleton
    abstract fun bindSeriesRepository(impl: SeriesRepositoryImpl): SeriesRepository

    @Binds @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds @Singleton
    abstract fun bindEpgRepository(impl: EpgRepositoryImpl): EpgRepository

    @Binds @Singleton
    abstract fun bindEpgSourceRepository(impl: EpgSourceRepositoryImpl): EpgSourceRepository

    @Binds @Singleton
    abstract fun bindFavoriteRepository(impl: FavoriteRepositoryImpl): FavoriteRepository

    @Binds @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds @Singleton
    abstract fun bindPlaybackHistoryRepository(impl: PlaybackHistoryRepositoryImpl): PlaybackHistoryRepository

    @Binds @Singleton
    abstract fun bindExternalRatingsRepository(impl: ExternalRatingsRepositoryImpl): ExternalRatingsRepository

    @Binds @Singleton
    abstract fun bindSyncMetadataRepository(impl: SyncMetadataRepositoryImpl): SyncMetadataRepository

    @Binds @Singleton
    abstract fun bindPlaybackCompatibilityRepository(impl: PlaybackCompatibilityRepositoryImpl): PlaybackCompatibilityRepository

    @Binds @Singleton
    abstract fun bindDatabaseTransactionRunner(impl: RoomDatabaseTransactionRunner): DatabaseTransactionRunner

    @Binds @Singleton
    abstract fun bindBackupManager(impl: com.kaynanamtv.data.manager.BackupManagerImpl): com.kaynanamtv.domain.manager.BackupManager

    @Binds @Singleton
    abstract fun bindDriveBackupSyncManager(impl: com.kaynanamtv.data.manager.GoogleDriveBackupSyncManager): com.kaynanamtv.domain.manager.DriveBackupSyncManager

    @Binds @Singleton
    abstract fun bindRecordingManager(impl: com.kaynanamtv.data.manager.RecordingManagerImpl): com.kaynanamtv.domain.manager.RecordingManager

    @Binds @Singleton
    abstract fun bindDownloadManager(impl: DownloadManagerImpl): DownloadManager

    @Binds @Singleton
    abstract fun bindProgramReminderManager(impl: com.kaynanamtv.data.manager.ProgramReminderManagerImpl): com.kaynanamtv.domain.manager.ProgramReminderManager

    @Binds @Singleton
    abstract fun bindParentalControlSessionStore(impl: PreferencesRepository): ParentalControlSessionStore

    @Binds @Singleton
    abstract fun bindParentalPinVerifier(impl: PreferencesRepository): ParentalPinVerifier

    @Binds @Singleton
    abstract fun bindProviderSetupInputValidator(impl: ProviderSetupInputValidatorImpl): ProviderSetupInputValidator

    @Binds @Singleton
    abstract fun bindProviderSyncStateReader(impl: ProviderSyncStateReaderImpl): ProviderSyncStateReader

    @Binds @Singleton
    abstract fun bindCredentialCrypto(impl: AndroidKeystoreCredentialCrypto): CredentialCrypto

    @Binds @Singleton
    abstract fun bindMediaPrefetcher(impl: com.kaynanamtv.app.manager.CoilMediaPrefetcher): com.kaynanamtv.domain.manager.MediaPrefetcher

    @Binds @Singleton
    abstract fun bindRemoteConfigRepository(impl: RemoteConfigRepositoryImpl): RemoteConfigRepository

    @Binds @Singleton
    abstract fun bindPlaybackContentionManager(impl: com.kaynanamtv.data.manager.DefaultPlaybackContentionManager): com.kaynanamtv.domain.manager.PlaybackContentionManager

    companion object {
        @Provides
        @Singleton
        fun provideRepositoryCoroutineScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }

        @Provides
        @Singleton
        fun provideM3uParser(): com.kaynanamtv.data.parser.M3uParser {
            return com.kaynanamtv.data.parser.M3uParser()
        }
    }
}
