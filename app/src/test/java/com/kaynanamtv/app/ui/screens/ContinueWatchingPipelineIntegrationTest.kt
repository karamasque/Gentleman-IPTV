package com.kaynanamtv.app.ui.screens

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Movie
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.PlaybackWatchedStatus
import com.kaynanamtv.domain.model.Series
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import com.kaynanamtv.domain.usecase.ContinueWatchingResult
import com.kaynanamtv.domain.usecase.ContinueWatchingScope
import com.kaynanamtv.domain.usecase.GetContinueWatching
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContinueWatchingPipelineIntegrationTest {

    private val playbackHistoryRepository: PlaybackHistoryRepository = mock()
    private val getContinueWatching = GetContinueWatching(playbackHistoryRepository)

    @Test
    fun `unified continue watching pipeline guarantees consistency across Home Movies and Series`() = runTest {
        val providerId = 1L

        val doctorStrange = PlaybackHistory(
            contentId = 101L,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Doctor Strange",
            streamUrl = "http://srv/movie/501.mp4",
            resumePositionMs = 50_000L,
            totalDurationMs = 120_000L,
            lastWatchedAt = 8000L
        )
        val dracula = PlaybackHistory(
            contentId = 102L,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Dracula (2025)",
            streamUrl = "http://srv/movie/502.mp4",
            resumePositionMs = 40_000L,
            totalDurationMs = 100_000L,
            lastWatchedAt = 7000L
        )
        // Two Room rows for GTA VI with the same remote streamId 777
        val gtaRow1 = PlaybackHistory(
            contentId = 103L,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Grand Theft Auto VI",
            streamUrl = "http://srv/movie/777.mp4",
            resumePositionMs = 30_000L,
            totalDurationMs = 90_000L,
            lastWatchedAt = 6000L
        )
        val gtaRow2 = PlaybackHistory(
            contentId = 104L,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Grand Theft Auto VI",
            streamUrl = "http://srv/movie/777.mp4",
            resumePositionMs = 30_000L,
            totalDurationMs = 90_000L,
            lastWatchedAt = 5500L
        )
        val untouchable = PlaybackHistory(
            contentId = 105L,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Untouchable (2011)",
            streamUrl = "http://srv/movie/505.mp4",
            resumePositionMs = 60_000L,
            totalDurationMs = 120_000L,
            lastWatchedAt = 4000L
        )
        val tuzluKahve = PlaybackHistory(
            contentId = 106L,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Tuzlu Kahve",
            streamUrl = "http://srv/movie/506.mp4",
            resumePositionMs = 25_000L,
            totalDurationMs = 60_000L,
            lastWatchedAt = 3000L
        )

        val ep1 = PlaybackHistory(
            contentId = 201L,
            seriesId = 50L,
            seasonNumber = 1,
            episodeNumber = 1,
            contentType = ContentType.SERIES_EPISODE,
            providerId = providerId,
            title = "Episode 1",
            streamUrl = "http://srv/series/801.mp4",
            resumePositionMs = 15_000L,
            totalDurationMs = 45_000L,
            lastWatchedAt = 2000L
        )
        val ep2 = PlaybackHistory(
            contentId = 202L,
            seriesId = 50L,
            seasonNumber = 1,
            episodeNumber = 2,
            contentType = ContentType.SERIES_EPISODE,
            providerId = providerId,
            title = "Episode 2",
            streamUrl = "http://srv/series/802.mp4",
            resumePositionMs = 20_000L,
            totalDurationMs = 45_000L,
            lastWatchedAt = 1000L
        )

        // Completed movie (should be excluded)
        val completedMovie = PlaybackHistory(
            contentId = 107L,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Completed Movie",
            streamUrl = "http://srv/movie/507.mp4",
            resumePositionMs = 98_000L,
            totalDurationMs = 100_000L,
            lastWatchedAt = 9000L,
            watchedStatus = PlaybackWatchedStatus.COMPLETED_AUTO
        )

        val seededList = listOf(doctorStrange, dracula, gtaRow1, gtaRow2, untouchable, tuzluKahve, ep1, ep2, completedMovie)

        whenever(playbackHistoryRepository.getContinueWatchingCandidatesByProvider(eq(providerId), any()))
            .thenReturn(flowOf(seededList))
        whenever(playbackHistoryRepository.getContinueWatchingCandidatesByProviders(eq(setOf(providerId)), any()))
            .thenReturn(flowOf(seededList))

        // 1. Home scope (ALL_VOD)
        val homeResult = getContinueWatching(providerId = providerId, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.ALL_VOD).first()
        assertThat(homeResult).isInstanceOf(ContinueWatchingResult.Items::class.java)
        val homeItems = (homeResult as ContinueWatchingResult.Items).items

        // 2. Movies scope (MOVIES)
        val moviesResult = getContinueWatching(providerId = providerId, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.MOVIES).first()
        assertThat(moviesResult).isInstanceOf(ContinueWatchingResult.Items::class.java)
        val movieItems = (moviesResult as ContinueWatchingResult.Items).items

        // 3. Series scope (SERIES)
        val seriesResult = getContinueWatching(providerId = providerId, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.SERIES).first()
        assertThat(seriesResult).isInstanceOf(ContinueWatchingResult.Items::class.java)
        val seriesItems = (seriesResult as ContinueWatchingResult.Items).items

        // Assert 1 & 5: GTA exists exactly once in both Home and Movies
        val gtaHomeCount = homeItems.count { it.title == "Grand Theft Auto VI" }
        val gtaMoviesCount = movieItems.count { it.title == "Grand Theft Auto VI" }
        assertThat(gtaHomeCount).isEqualTo(1)
        assertThat(gtaMoviesCount).isEqualTo(1)

        // Assert 2: Movies screen displays all 5 eligible movies
        assertThat(movieItems.map { it.title }).containsExactly(
            "Doctor Strange",
            "Dracula (2025)",
            "Grand Theft Auto VI",
            "Untouchable (2011)",
            "Tuzlu Kahve"
        ).inOrder()

        // Assert 3: Home displays exact same movie identities as Movies Continue Watching
        val homeMovieTitles = homeItems.filter { it.contentType == ContentType.MOVIE }.map { it.title }
        assertThat(homeMovieTitles).containsExactlyElementsIn(movieItems.map { it.title }).inOrder()

        // Assert 4: Series screen displays both episodes
        assertThat(seriesItems.map { it.title }).containsExactly("Episode 1", "Episode 2").inOrder()

        // Assert 5: Home displays exact same episode identities as Series Continue Watching
        val homeEpisodeTitles = homeItems.filter { it.contentType == ContentType.SERIES_EPISODE }.map { it.title }
        assertThat(homeEpisodeTitles).containsExactlyElementsIn(seriesItems.map { it.title }).inOrder()

        // Assert 7: Completed content is absent
        assertThat(homeItems.any { it.title == "Completed Movie" }).isFalse()
        assertThat(movieItems.any { it.title == "Completed Movie" }).isFalse()
    }

    @Test
    fun `25 plus items remain available in Continue Watching without truncation`() = runTest {
        val providerId = 1L
        val largeList = (1..30).map { i ->
            PlaybackHistory(
                contentId = 1000L + i,
                contentType = ContentType.MOVIE,
                providerId = providerId,
                title = "Movie $i",
                streamUrl = "http://srv/movie/$i.mp4",
                resumePositionMs = 10_000L,
                totalDurationMs = 60_000L,
                lastWatchedAt = 10000L - i
            )
        }

        whenever(playbackHistoryRepository.getContinueWatchingCandidatesByProvider(eq(providerId), any()))
            .thenReturn(flowOf(largeList))

        val result = getContinueWatching(providerId = providerId, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.ALL_VOD).first()
        val items = (result as ContinueWatchingResult.Items).items

        assertThat(items).hasSize(30)
        assertThat(items.first().title).isEqualTo("Movie 1")
        assertThat(items.last().title).isEqualTo("Movie 30")
    }
}
