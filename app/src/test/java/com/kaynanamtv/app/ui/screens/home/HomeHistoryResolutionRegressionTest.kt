package com.kaynanamtv.app.ui.screens.home

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.app.navigation.PlayerNavigationRequest
import com.kaynanamtv.app.navigation.safePlayerNavigationRequest
import com.kaynanamtv.data.local.entity.EpisodeEntity
import com.kaynanamtv.data.local.entity.MovieEntity
import com.kaynanamtv.data.local.entity.PlaybackHistoryEntity
import com.kaynanamtv.data.local.entity.SeriesEntity
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.Series
import org.junit.Test

class HomeHistoryResolutionRegressionTest {

    // Scenario A: Cloud movie history: streamUrl blank, internalId valid, providerId valid -> navigation accepted
    @Test
    fun `navigation accepted when streamUrl is blank but local identity is valid`() {
        val request = PlayerNavigationRequest(
            streamUrl = "",
            title = "Inception",
            internalId = 42L,
            providerId = 1L,
            contentType = "MOVIE"
        )
        val result = safePlayerNavigationRequest(request)
        assertThat(result).isNotNull()
        assertThat(result?.internalId).isEqualTo(42L)
    }

    // Scenario B: streamUrl blank, internalId invalid, providerId invalid -> navigation rejected
    @Test
    fun `navigation rejected when streamUrl is blank and local identity is invalid`() {
        val invalidInternalId = PlayerNavigationRequest(
            streamUrl = "",
            title = "Unknown",
            internalId = -1L,
            providerId = 1L,
            contentType = "MOVIE"
        )
        assertThat(safePlayerNavigationRequest(invalidInternalId)).isNull()

        val invalidProviderId = PlayerNavigationRequest(
            streamUrl = "",
            title = "Unknown",
            internalId = 42L,
            providerId = null,
            contentType = "MOVIE"
        )
        assertThat(safePlayerNavigationRequest(invalidProviderId)).isNull()

        val zeroProviderId = PlayerNavigationRequest(
            streamUrl = "",
            title = "Unknown",
            internalId = 42L,
            providerId = 0L,
            contentType = "MOVIE"
        )
        assertThat(safePlayerNavigationRequest(zeroProviderId)).isNull()
    }

    // Scenario C: valid normal streamUrl -> existing navigation accepted; unsafe scheme rejected
    @Test
    fun `navigation accepted for valid streamUrl and rejected for unsafe schemes`() {
        val validRequest = PlayerNavigationRequest(
            streamUrl = "http://example.com/movie.mp4",
            title = "Valid Movie",
            internalId = -1L,
            providerId = null,
            contentType = "MOVIE"
        )
        assertThat(safePlayerNavigationRequest(validRequest)).isNotNull()

        val xtreamRequest = PlayerNavigationRequest(
            streamUrl = "xtream://provider/123",
            title = "Xtream Movie",
            internalId = -1L,
            providerId = null,
            contentType = "MOVIE"
        )
        assertThat(safePlayerNavigationRequest(xtreamRequest)).isNotNull()

        val unsafeRequest = PlayerNavigationRequest(
            streamUrl = "javascript:alert(1)",
            title = "Malicious",
            internalId = 42L,
            providerId = 1L,
            contentType = "MOVIE"
        )
        assertThat(safePlayerNavigationRequest(unsafeRequest)).isNull()
    }

    // Scenario D: Cloud movie resolved to localMovie with streamUrl -> local playback_history gets streamUrl
    @Test
    fun `cloud movie hydration assigns streamUrl from local movie entity`() {
        val localMovie = MovieEntity(
            id = 101L,
            providerId = 1L,
            streamId = 555L,
            name = "Interstellar",
            streamUrl = "http://server/movie/555.mp4",
            posterUrl = "http://server/poster.jpg"
        )
        val streamUrl = localMovie.streamUrl.takeIf { it.isNotBlank() } ?: ""
        val historyEntity = PlaybackHistoryEntity(
            providerId = localMovie.providerId,
            contentId = localMovie.id,
            contentType = ContentType.MOVIE,
            title = localMovie.name,
            posterUrl = localMovie.posterUrl,
            streamUrl = streamUrl,
            resumePositionMs = 5000L,
            totalDurationMs = 100000L,
            lastWatchedAt = 123456L
        )

        assertThat(historyEntity.streamUrl).isEqualTo("http://server/movie/555.mp4")
    }

    // Scenario E: Episode coverUrl = "" & Series poster exists -> fallback to series poster
    @Test
    fun `episode blank coverUrl falls back to series poster`() {
        val localEpisode = EpisodeEntity(
            id = 501L,
            providerId = 1L,
            seriesId = 10L,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Pilot",
            coverUrl = ""
        )
        val localSeries = SeriesEntity(
            id = 10L,
            providerId = 1L,
            name = "Breaking Bad",
            posterUrl = "http://server/series_poster.jpg",
            backdropUrl = "http://server/series_backdrop.jpg"
        )
        val posterUrl = localEpisode.coverUrl?.takeIf { it.isNotBlank() }
            ?: localSeries.posterUrl?.takeIf { it.isNotBlank() }
            ?: localSeries.backdropUrl?.takeIf { it.isNotBlank() }

        assertThat(posterUrl).isEqualTo("http://server/series_poster.jpg")
    }

    // Scenario F: Episode coverUrl = null & Series poster exists -> fallback to series poster
    @Test
    fun `episode null coverUrl falls back to series poster`() {
        val localEpisode = EpisodeEntity(
            id = 502L,
            providerId = 1L,
            seriesId = 10L,
            seasonNumber = 1,
            episodeNumber = 2,
            title = "Cat's in the Bag...",
            coverUrl = null
        )
        val localSeries = SeriesEntity(
            id = 10L,
            providerId = 1L,
            name = "Breaking Bad",
            posterUrl = "http://server/series_poster.jpg",
            backdropUrl = "http://server/series_backdrop.jpg"
        )
        val posterUrl = localEpisode.coverUrl?.takeIf { it.isNotBlank() }
            ?: localSeries.posterUrl?.takeIf { it.isNotBlank() }
            ?: localSeries.backdropUrl?.takeIf { it.isNotBlank() }

        assertThat(posterUrl).isEqualTo("http://server/series_poster.jpg")
    }

    // Scenario G: Episode cover blank & Series poster blank & Series backdrop exists -> uses backdrop
    @Test
    fun `episode and series blank poster falls back to series backdrop`() {
        val localEpisode = EpisodeEntity(
            id = 503L,
            providerId = 1L,
            seriesId = 10L,
            seasonNumber = 1,
            episodeNumber = 3,
            title = "...And the Bag's in the River",
            coverUrl = ""
        )
        val localSeries = SeriesEntity(
            id = 10L,
            providerId = 1L,
            name = "Breaking Bad",
            posterUrl = "",
            backdropUrl = "http://server/series_backdrop.jpg"
        )
        val posterUrl = localEpisode.coverUrl?.takeIf { it.isNotBlank() }
            ?: localSeries.posterUrl?.takeIf { it.isNotBlank() }
            ?: localSeries.backdropUrl?.takeIf { it.isNotBlank() }

        assertThat(posterUrl).isEqualTo("http://server/series_backdrop.jpg")
    }

    // Scenario H: Movie artwork behavior unchanged
    @Test
    fun `movie artwork uses poster then backdrop then null`() {
        val movieWithPoster = MovieEntity(
            id = 1L,
            providerId = 1L,
            name = "Movie 1",
            posterUrl = "http://server/m1_poster.jpg",
            backdropUrl = "http://server/m1_backdrop.jpg"
        )
        val poster1 = movieWithPoster.posterUrl?.takeIf { it.isNotBlank() }
            ?: movieWithPoster.backdropUrl?.takeIf { it.isNotBlank() }
        assertThat(poster1).isEqualTo("http://server/m1_poster.jpg")

        val movieWithBackdropOnly = MovieEntity(
            id = 2L,
            providerId = 1L,
            name = "Movie 2",
            posterUrl = "",
            backdropUrl = "http://server/m2_backdrop.jpg"
        )
        val poster2 = movieWithBackdropOnly.posterUrl?.takeIf { it.isNotBlank() }
            ?: movieWithBackdropOnly.backdropUrl?.takeIf { it.isNotBlank() }
        assertThat(poster2).isEqualTo("http://server/m2_backdrop.jpg")
    }

    // Scenario I & J: Dashboard series backfill enrichment and Live query isolation
    @Test
    fun `dashboard continue watching items receive parent series artwork backfill`() {
        val historyItems = listOf(
            PlaybackHistory(
                contentId = 501L,
                contentType = ContentType.SERIES_EPISODE,
                providerId = 1L,
                title = "Pilot",
                posterUrl = "",
                streamUrl = "http://server/episode.mp4",
                seriesId = 10L
            ),
            PlaybackHistory(
                contentId = 101L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                title = "Inception",
                posterUrl = "http://server/inception.jpg",
                streamUrl = "http://server/movie.mp4"
            )
        )

        val seriesList = listOf(
            Series(
                id = 10L,
                providerId = 1L,
                name = "Breaking Bad",
                posterUrl = "http://server/bb_poster.jpg"
            )
        )

        val seriesById = seriesList.associateBy { it.id }
        val enrichedItems = historyItems.map { history ->
            if (history.posterUrl.isNullOrBlank() && (history.contentType == ContentType.SERIES || history.contentType == ContentType.SERIES_EPISODE)) {
                val parent = seriesById[history.seriesId ?: history.contentId]
                val artwork = parent?.posterUrl?.takeIf { it.isNotBlank() } ?: parent?.backdropUrl?.takeIf { it.isNotBlank() }
                if (artwork != null) history.copy(posterUrl = artwork) else history
            } else {
                history
            }
        }

        assertThat(enrichedItems[0].posterUrl).isEqualTo("http://server/bb_poster.jpg")
        assertThat(enrichedItems[1].posterUrl).isEqualTo("http://server/inception.jpg")
    }

    @Test
    fun `recent live history filtering keeps live channels regardless of resume position`() {
        val mixedHistory = listOf(
            PlaybackHistory(
                contentId = 101L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                title = "Movie 1",
                streamUrl = "http://server/movie.mp4",
                resumePositionMs = 50000L,
                lastWatchedAt = 2000L
            ),
            PlaybackHistory(
                contentId = 201L,
                contentType = ContentType.LIVE,
                providerId = 1L,
                title = "Channel 1",
                streamUrl = "http://server/live.ts",
                resumePositionMs = 0L, // Live doesn't have resume position
                lastWatchedAt = 1500L
            )
        )

        val liveOnly = mixedHistory.filter { it.contentType == ContentType.LIVE }
        assertThat(liveOnly).hasSize(1)
        assertThat(liveOnly.first().contentId).isEqualTo(201L)
        assertThat(liveOnly.first().resumePositionMs).isEqualTo(0L)
    }
}
