package com.kaynanamtv.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.kaynanamtv.app.BuildConfig
import com.kaynanamtv.data.local.KaynanamTVDatabase
import com.kaynanamtv.data.local.dao.*
import com.kaynanamtv.data.remote.jellyfin.JellyfinProvider
import com.google.gson.Gson
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DEBUG_SLOW_QUERY_THRESHOLD_MS = 100L

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KaynanamTVDatabase =
        Room.databaseBuilder(
            context,
            KaynanamTVDatabase::class.java,
            "kaynanamtv.db"
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .openHelperFactory(
                if (BuildConfig.DEBUG) {
                    SlowQueryLoggingOpenHelperFactory(
                        delegate = FrameworkSQLiteOpenHelperFactory(),
                        slowQueryThresholdMs = DEBUG_SLOW_QUERY_THRESHOLD_MS
                    )
                } else {
                    FrameworkSQLiteOpenHelperFactory()
                }
            )
            .addMigrations(
                KaynanamTVDatabase.MIGRATION_1_2,
                KaynanamTVDatabase.MIGRATION_2_3,
                KaynanamTVDatabase.MIGRATION_3_4,
                KaynanamTVDatabase.MIGRATION_4_5,
                KaynanamTVDatabase.MIGRATION_5_6,
                KaynanamTVDatabase.MIGRATION_6_7,
                KaynanamTVDatabase.MIGRATION_7_8,
                KaynanamTVDatabase.MIGRATION_8_9,
                KaynanamTVDatabase.MIGRATION_9_10,
                KaynanamTVDatabase.MIGRATION_10_11,
                KaynanamTVDatabase.MIGRATION_11_12,
                KaynanamTVDatabase.MIGRATION_12_13,
                KaynanamTVDatabase.MIGRATION_13_14,
                KaynanamTVDatabase.MIGRATION_14_15,
                KaynanamTVDatabase.MIGRATION_15_16,
                KaynanamTVDatabase.MIGRATION_16_17,
                KaynanamTVDatabase.MIGRATION_17_18,
                KaynanamTVDatabase.MIGRATION_18_19,
                KaynanamTVDatabase.MIGRATION_19_20,
                KaynanamTVDatabase.MIGRATION_20_21,
                KaynanamTVDatabase.MIGRATION_21_22,
                KaynanamTVDatabase.MIGRATION_22_23,
                KaynanamTVDatabase.MIGRATION_23_24,
                KaynanamTVDatabase.MIGRATION_24_25,
                KaynanamTVDatabase.MIGRATION_25_26,
                KaynanamTVDatabase.MIGRATION_26_27,
                KaynanamTVDatabase.MIGRATION_27_28,
                KaynanamTVDatabase.MIGRATION_28_29,
                KaynanamTVDatabase.MIGRATION_29_30,
                KaynanamTVDatabase.MIGRATION_30_31,
                KaynanamTVDatabase.MIGRATION_31_32,
                KaynanamTVDatabase.MIGRATION_32_33,
                KaynanamTVDatabase.MIGRATION_33_34,
                KaynanamTVDatabase.MIGRATION_34_35,
                KaynanamTVDatabase.MIGRATION_35_36,
                KaynanamTVDatabase.MIGRATION_36_37,
                KaynanamTVDatabase.MIGRATION_37_38,
                KaynanamTVDatabase.MIGRATION_38_39,
                KaynanamTVDatabase.MIGRATION_39_40,
                KaynanamTVDatabase.MIGRATION_40_41,
                KaynanamTVDatabase.MIGRATION_41_42,
                KaynanamTVDatabase.MIGRATION_42_43,
                KaynanamTVDatabase.MIGRATION_43_44,
                KaynanamTVDatabase.MIGRATION_44_45,
                KaynanamTVDatabase.MIGRATION_45_46,
                KaynanamTVDatabase.MIGRATION_46_47,
                KaynanamTVDatabase.MIGRATION_47_48,
                KaynanamTVDatabase.MIGRATION_48_49,
                KaynanamTVDatabase.MIGRATION_49_50,
                KaynanamTVDatabase.MIGRATION_50_51,
                KaynanamTVDatabase.MIGRATION_51_52,
                KaynanamTVDatabase.MIGRATION_52_53,
                KaynanamTVDatabase.MIGRATION_53_54,
                KaynanamTVDatabase.MIGRATION_54_55,
                KaynanamTVDatabase.MIGRATION_55_56,
                KaynanamTVDatabase.MIGRATION_56_57,
                KaynanamTVDatabase.MIGRATION_57_58,
                KaynanamTVDatabase.MIGRATION_58_59,
                KaynanamTVDatabase.MIGRATION_59_60,
                KaynanamTVDatabase.MIGRATION_60_61,
                KaynanamTVDatabase.MIGRATION_61_62,
                KaynanamTVDatabase.MIGRATION_62_63,
                KaynanamTVDatabase.MIGRATION_63_64
            )
            .build()

    @Provides @Singleton
    fun provideJellyfinProvider(okHttpClient: OkHttpClient, gson: Gson): JellyfinProvider = JellyfinProvider(okHttpClient, gson)

    @Provides fun provideProviderDao(db: KaynanamTVDatabase): ProviderDao = db.providerDao()
    @Provides fun provideChannelDao(db: KaynanamTVDatabase): ChannelDao = db.channelDao()
    @Provides fun provideChannelPreferenceDao(db: KaynanamTVDatabase): ChannelPreferenceDao = db.channelPreferenceDao()
    @Provides fun provideMovieDao(db: KaynanamTVDatabase): MovieDao = db.movieDao()
    @Provides fun provideSeriesDao(db: KaynanamTVDatabase): SeriesDao = db.seriesDao()
    @Provides fun provideEpisodeDao(db: KaynanamTVDatabase): EpisodeDao = db.episodeDao()
    @Provides fun provideCategoryDao(db: KaynanamTVDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideCatalogSyncDao(db: KaynanamTVDatabase): CatalogSyncDao = db.catalogSyncDao()
    @Provides fun provideProgramDao(db: KaynanamTVDatabase): ProgramDao = db.programDao()
    @Provides fun provideFavoriteDao(db: KaynanamTVDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideVirtualGroupDao(db: KaynanamTVDatabase): VirtualGroupDao = db.virtualGroupDao()
    @Provides fun providePlaybackHistoryDao(db: KaynanamTVDatabase): PlaybackHistoryDao = db.playbackHistoryDao()
    @Provides fun provideTmdbIdentityDao(db: KaynanamTVDatabase): TmdbIdentityDao = db.tmdbIdentityDao()
    @Provides fun provideSearchHistoryDao(db: KaynanamTVDatabase): SearchHistoryDao = db.searchHistoryDao()
    @Provides fun provideSearchDao(db: KaynanamTVDatabase): SearchDao = db.searchDao()
    @Provides fun provideSyncMetadataDao(db: KaynanamTVDatabase): SyncMetadataDao = db.syncMetadataDao()
    @Provides fun provideMovieCategoryHydrationDao(db: KaynanamTVDatabase): MovieCategoryHydrationDao = db.movieCategoryHydrationDao()
    @Provides fun provideSeriesCategoryHydrationDao(db: KaynanamTVDatabase): SeriesCategoryHydrationDao = db.seriesCategoryHydrationDao()
    @Provides fun provideEpgSourceDao(db: KaynanamTVDatabase): EpgSourceDao = db.epgSourceDao()
    @Provides fun provideProviderEpgSourceDao(db: KaynanamTVDatabase): ProviderEpgSourceDao = db.providerEpgSourceDao()
    @Provides fun provideEpgChannelDao(db: KaynanamTVDatabase): EpgChannelDao = db.epgChannelDao()
    @Provides fun provideEpgProgrammeDao(db: KaynanamTVDatabase): EpgProgrammeDao = db.epgProgrammeDao()
    @Provides fun provideChannelEpgMappingDao(db: KaynanamTVDatabase): ChannelEpgMappingDao = db.channelEpgMappingDao()
    @Provides fun provideCombinedM3uProfileDao(db: KaynanamTVDatabase): CombinedM3uProfileDao = db.combinedM3uProfileDao()
    @Provides fun provideCombinedM3uProfileMemberDao(db: KaynanamTVDatabase): CombinedM3uProfileMemberDao = db.combinedM3uProfileMemberDao()
    @Provides fun provideRecordingScheduleDao(db: KaynanamTVDatabase): RecordingScheduleDao = db.recordingScheduleDao()
    @Provides fun provideRecordingRunDao(db: KaynanamTVDatabase): RecordingRunDao = db.recordingRunDao()
    @Provides fun provideProgramReminderDao(db: KaynanamTVDatabase): ProgramReminderDao = db.programReminderDao()
    @Provides fun provideRecordingStorageDao(db: KaynanamTVDatabase): RecordingStorageDao = db.recordingStorageDao()
    @Provides fun providePlaybackCompatibilityDao(db: KaynanamTVDatabase): PlaybackCompatibilityDao = db.playbackCompatibilityDao()
    @Provides fun provideXtreamContentIndexDao(db: KaynanamTVDatabase): XtreamContentIndexDao = db.xtreamContentIndexDao()
    @Provides fun provideXtreamIndexJobDao(db: KaynanamTVDatabase): XtreamIndexJobDao = db.xtreamIndexJobDao()
    @Provides fun provideXtreamLiveOnboardingDao(db: KaynanamTVDatabase): XtreamLiveOnboardingDao = db.xtreamLiveOnboardingDao()
    @Provides fun provideDownloadDao(db: KaynanamTVDatabase): DownloadDao = db.downloadDao()
}
