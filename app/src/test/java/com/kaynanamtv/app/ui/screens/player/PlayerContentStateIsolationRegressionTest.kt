package com.kaynanamtv.app.ui.screens.player

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Episode
import com.kaynanamtv.domain.model.Series
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlayerContentStateIsolationRegressionTest {

    @Test
    fun `shouldResolveChannelPlaybackContext returns true ONLY for LIVE content`() {
        assertThat(shouldResolveChannelPlaybackContext("LIVE", 101L)).isTrue()
        assertThat(shouldResolveChannelPlaybackContext("MOVIE", 101L)).isFalse()
        assertThat(shouldResolveChannelPlaybackContext("SERIES_EPISODE", 101L)).isFalse()
        assertThat(shouldResolveChannelPlaybackContext("SERIES", 101L)).isFalse()
        assertThat(shouldResolveChannelPlaybackContext("LIVE", -1L)).isFalse()
        assertThat(shouldResolveChannelPlaybackContext("LIVE", 0L)).isFalse()
    }

    @Test
    fun `shouldUseStoredLiveStreamInfo matches only exact or blank requested URLs`() {
        assertThat(shouldUseStoredLiveStreamInfo("", "https://stream.ts")).isTrue()
        assertThat(shouldUseStoredLiveStreamInfo("   ", "https://stream.ts")).isTrue()
        assertThat(shouldUseStoredLiveStreamInfo("https://stream.ts", "https://stream.ts")).isTrue()
        assertThat(shouldUseStoredLiveStreamInfo("https://stream.ts?token=1", "https://stream.ts")).isFalse()
    }

    @Test
    fun `buildLivePlaybackRecordCandidate returns null for non-LIVE content`() {
        val movieRecord = buildLivePlaybackRecordCandidate(
            currentProviderId = 1L,
            currentContentType = ContentType.MOVIE,
            currentContentId = 55L,
            currentTitle = "Sample Movie",
            currentResolvedPlaybackUrl = "https://example.com/movie.mp4",
            currentStreamUrl = "https://example.com/movie.mp4",
            channel = null
        )
        assertThat(movieRecord).isNull()

        val seriesRecord = buildLivePlaybackRecordCandidate(
            currentProviderId = 1L,
            currentContentType = ContentType.SERIES_EPISODE,
            currentContentId = 77L,
            currentTitle = "S01E01",
            currentResolvedPlaybackUrl = "https://example.com/ep.mp4",
            currentStreamUrl = "https://example.com/ep.mp4",
            channel = null
        )
        assertThat(seriesRecord).isNull()
    }

    @Test
    fun `matchesActivePlaybackSession rejects stale requestVersion from previous session`() {
        val activeSessionVersion = 5L
        val staleSessionVersion = 4L
        val streamUrl = "https://example.com/live.m3u8"

        val isStaleActive = matchesActivePlaybackSession(
            requestVersion = staleSessionVersion,
            activeRequestVersion = activeSessionVersion,
            expectedLogicalUrl = streamUrl,
            currentResolvedPlaybackUrl = streamUrl,
            currentStreamUrl = streamUrl
        )
        assertThat(isStaleActive).isFalse()

        val isCurrentActive = matchesActivePlaybackSession(
            requestVersion = activeSessionVersion,
            activeRequestVersion = activeSessionVersion,
            expectedLogicalUrl = streamUrl,
            currentResolvedPlaybackUrl = streamUrl,
            currentStreamUrl = streamUrl
        )
        assertThat(isCurrentActive).isTrue()
    }

    @Test
    fun `buildSeriesEpisodeResolution resolves artwork and title for SERIES_EPISODE only`() {
        val episode = Episode(
            id = 101L,
            title = "Pilot",
            episodeNumber = 1,
            seasonNumber = 1,
            streamUrl = "https://example.com/ep1.mkv",
            seriesId = 10L,
            providerId = 1L,
            coverUrl = "https://example.com/ep1.jpg"
        )
        val series = Series(
            id = 10L,
            name = "Breaking Code",
            providerId = 1L,
            seasons = emptyList()
        )

        val seriesEpisodeResolution = buildSeriesEpisodeResolution(
            series = series,
            episodeId = 101L,
            seasonNumber = 1,
            episodeNumber = 1,
            currentContentType = ContentType.SERIES_EPISODE,
            currentArtworkUrl = null
        )

        assertThat(seriesEpisodeResolution.resolvedSeasonNumber).isEqualTo(1)
        assertThat(seriesEpisodeResolution.resolvedEpisodeNumber).isEqualTo(1)

        val movieResolution = buildSeriesEpisodeResolution(
            series = series,
            episodeId = 101L,
            seasonNumber = 1,
            episodeNumber = 1,
            currentContentType = ContentType.MOVIE,
            currentArtworkUrl = "https://example.com/movie_poster.jpg"
        )
        assertThat(movieResolution.resolvedArtworkUrl).isEqualTo("https://example.com/movie_poster.jpg")
    }

    @Test
    fun `resolveSeriesEpisodeIdentity handles explicit and fallback metadata cleanly`() = runTest {
        val explicit = resolveSeriesEpisodeIdentity(
            providerId = 1L,
            internalChannelId = 200L,
            seriesId = 50L,
            seasonNumber = 2,
            episodeNumber = 3,
            lookupEpisode = { null }
        )
        assertThat(explicit).isEqualTo(
            ResolvedSeriesEpisodeIdentity(seriesId = 50L, seasonNumber = 2, episodeNumber = 3)
        )

        val fallback = resolveSeriesEpisodeIdentity(
            providerId = 1L,
            internalChannelId = 200L,
            seriesId = null,
            seasonNumber = null,
            episodeNumber = null,
            lookupEpisode = {
                Episode(
                    id = 200L,
                    title = "E4",
                    episodeNumber = 4,
                    seasonNumber = 1,
                    streamUrl = "https://example.com/e4.mkv",
                    seriesId = 50L,
                    providerId = 1L
                )
            }
        )
        assertThat(fallback).isEqualTo(
            ResolvedSeriesEpisodeIdentity(seriesId = 50L, seasonNumber = 1, episodeNumber = 4)
        )
    }
}
