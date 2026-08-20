package com.kaynanamtv.data.remote.xtream

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.kaynanamtv.data.remote.dto.XtreamLiveStreamRow
import com.kaynanamtv.data.remote.dto.XtreamSeriesItem
import com.kaynanamtv.data.remote.dto.XtreamStream

object XtreamStreamJsonReader {

    fun readLiveStreamRow(reader: JsonReader): XtreamLiveStreamRow? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        reader.beginObject()
        var num = 0
        var name = ""
        var streamId = 0L
        var streamIcon: String? = null
        var epgChannelId: String? = null
        var categoryId: String? = null
        var categoryName: String? = null
        var categoryIds: List<String>? = null
        var tvArchive = 0
        var tvArchiveDuration: Int? = null
        var containerExtension: String? = null
        var isAdult: Boolean? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "num" -> num = readIntLenient(reader)
                "name" -> name = readStringLenient(reader)
                "stream_id" -> streamId = readLongLenient(reader)
                "stream_icon" -> streamIcon = readNullableStringLenient(reader)
                "epg_channel_id" -> epgChannelId = readNullableStringLenient(reader)
                "category_id" -> categoryId = readNullableStringLenient(reader)
                "category_name" -> categoryName = readNullableStringLenient(reader)
                "category_ids" -> categoryIds = readNullableStringListLenient(reader)
                "tv_archive" -> tvArchive = readIntLenient(reader)
                "tv_archive_duration" -> tvArchiveDuration = readNullableIntLenient(reader)
                "container_extension" -> containerExtension = readNullableStringLenient(reader)
                "is_adult" -> isAdult = readNullableBooleanLenient(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return XtreamLiveStreamRow(
            num = num,
            name = name,
            streamId = streamId,
            streamIcon = streamIcon,
            epgChannelId = epgChannelId,
            categoryId = categoryId,
            categoryName = categoryName,
            categoryIds = categoryIds,
            tvArchive = tvArchive,
            tvArchiveDuration = tvArchiveDuration,
            containerExtension = containerExtension,
            isAdult = isAdult
        )
    }

    fun readStream(reader: JsonReader): XtreamStream? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        reader.beginObject()
        var num = 0
        var name = ""
        var streamType = ""
        var streamId = 0L
        var streamIcon: String? = null
        var coverBig: String? = null
        var epgChannelId: String? = null
        var added: String? = null
        var categoryId: String? = null
        var categoryName: String? = null
        var categoryIds: List<String>? = null
        var customSid: String? = null
        var tvArchive = 0
        var directSource: String? = null
        var tvArchiveDuration: Int? = null
        var containerExtension: String? = null
        var rating: String? = null
        var rating5based: String? = null
        var tmdb: String? = null
        var trailer: String? = null
        var youtubeTrailer: String? = null
        var isAdult: Boolean? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "num" -> num = readIntLenient(reader)
                "name" -> name = readStringLenient(reader)
                "stream_type" -> streamType = readStringLenient(reader)
                "stream_id" -> streamId = readLongLenient(reader)
                "stream_icon" -> streamIcon = readNullableStringLenient(reader)
                "cover_big" -> coverBig = readNullableStringLenient(reader)
                "epg_channel_id" -> epgChannelId = readNullableStringLenient(reader)
                "added" -> added = readNullableStringLenient(reader)
                "category_id" -> categoryId = readNullableStringLenient(reader)
                "category_name" -> categoryName = readNullableStringLenient(reader)
                "category_ids" -> categoryIds = readNullableStringListLenient(reader)
                "custom_sid" -> customSid = readNullableStringLenient(reader)
                "tv_archive" -> tvArchive = readIntLenient(reader)
                "direct_source" -> directSource = readNullableStringLenient(reader)
                "tv_archive_duration" -> tvArchiveDuration = readNullableIntLenient(reader)
                "container_extension" -> containerExtension = readNullableStringLenient(reader)
                "rating" -> rating = readNullableStringLenient(reader)
                "rating_5based" -> rating5based = readNullableStringLenient(reader)
                "tmdb" -> tmdb = readNullableStringLenient(reader)
                "trailer" -> trailer = readNullableStringLenient(reader)
                "youtube_trailer" -> youtubeTrailer = readNullableStringLenient(reader)
                "is_adult" -> isAdult = readNullableBooleanLenient(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return XtreamStream(
            num = num,
            name = name,
            streamType = streamType,
            streamId = streamId,
            streamIcon = streamIcon,
            coverBig = coverBig,
            epgChannelId = epgChannelId,
            added = added,
            categoryId = categoryId,
            categoryName = categoryName,
            categoryIds = categoryIds,
            customSid = customSid,
            tvArchive = tvArchive,
            directSource = directSource,
            tvArchiveDuration = tvArchiveDuration,
            containerExtension = containerExtension,
            rating = rating,
            rating5based = rating5based,
            tmdb = tmdb,
            trailer = trailer,
            youtubeTrailer = youtubeTrailer,
            isAdult = isAdult
        )
    }

    fun readSeriesItem(reader: JsonReader): XtreamSeriesItem? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        reader.beginObject()
        var seriesId = 0L
        var name = ""
        var cover: String? = null
        var coverBig: String? = null
        var movieImage: String? = null
        var plot: String? = null
        var description: String? = null
        var cast: String? = null
        var director: String? = null
        var genre: String? = null
        var releaseDate: String? = null
        var releaseDateAlt: String? = null
        var lastModified: String? = null
        var rating: String? = null
        var rating5based: String? = null
        var backdropPath: List<String>? = null
        var youtubeTrailer: String? = null
        var trailer: String? = null
        var tmdb: String? = null
        var tmdbId: String? = null
        var episodeRunTime: String? = null
        var categoryId: String? = null
        var categoryName: String? = null
        var isAdult: Boolean? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "series_id" -> seriesId = readLongLenient(reader)
                "name" -> name = readStringLenient(reader)
                "cover" -> cover = readNullableStringLenient(reader)
                "cover_big" -> coverBig = readNullableStringLenient(reader)
                "movie_image" -> movieImage = readNullableStringLenient(reader)
                "plot" -> plot = readNullableStringLenient(reader)
                "description" -> description = readNullableStringLenient(reader)
                "cast" -> cast = readNullableStringLenient(reader)
                "director" -> director = readNullableStringLenient(reader)
                "genre" -> genre = readNullableStringLenient(reader)
                "releaseDate" -> releaseDate = readNullableStringLenient(reader)
                "releasedate", "release_date" -> releaseDateAlt = readNullableStringLenient(reader)
                "last_modified" -> lastModified = readNullableStringLenient(reader)
                "rating" -> rating = readNullableStringLenient(reader)
                "rating_5based" -> rating5based = readNullableStringLenient(reader)
                "backdrop_path" -> backdropPath = readNullableStringListLenient(reader)
                "youtube_trailer" -> youtubeTrailer = readNullableStringLenient(reader)
                "trailer" -> trailer = readNullableStringLenient(reader)
                "tmdb" -> tmdb = readNullableStringLenient(reader)
                "tmdb_id" -> tmdbId = readNullableStringLenient(reader)
                "episode_run_time" -> episodeRunTime = readNullableStringLenient(reader)
                "category_id" -> categoryId = readNullableStringLenient(reader)
                "category_name" -> categoryName = readNullableStringLenient(reader)
                "is_adult" -> isAdult = readNullableBooleanLenient(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return XtreamSeriesItem(
            seriesId = seriesId,
            name = name,
            cover = cover,
            coverBig = coverBig,
            movieImage = movieImage,
            plot = plot,
            description = description,
            cast = cast,
            director = director,
            genre = genre,
            releaseDate = releaseDate,
            releaseDateAlt = releaseDateAlt,
            lastModified = lastModified,
            rating = rating,
            rating5based = rating5based,
            backdropPath = backdropPath,
            youtubeTrailer = youtubeTrailer,
            trailer = trailer,
            tmdb = tmdb,
            tmdbId = tmdbId,
            episodeRunTime = episodeRunTime,
            categoryId = categoryId,
            categoryName = categoryName,
            isAdult = isAdult
        )
    }

    private fun readStringLenient(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun readNullableStringLenient(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun readLongLenient(reader: JsonReader): Long {
        return when (reader.peek()) {
            JsonToken.NUMBER -> {
                try {
                    reader.nextLong()
                } catch (e: NumberFormatException) {
                    reader.nextDouble().toLong()
                }
            }
            JsonToken.STRING -> {
                val str = reader.nextString().trim()
                str.toLongOrNull() ?: str.toDoubleOrNull()?.toLong() ?: 0L
            }
            JsonToken.NULL -> {
                reader.nextNull()
                0L
            }
            else -> {
                reader.skipValue()
                0L
            }
        }
    }

    private fun readIntLenient(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonToken.NUMBER -> {
                try {
                    reader.nextInt()
                } catch (e: NumberFormatException) {
                    reader.nextDouble().toInt()
                }
            }
            JsonToken.STRING -> {
                val str = reader.nextString().trim()
                str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt() ?: 0
            }
            JsonToken.NULL -> {
                reader.nextNull()
                0
            }
            else -> {
                reader.skipValue()
                0
            }
        }
    }

    private fun readNullableIntLenient(reader: JsonReader): Int? {
        return when (reader.peek()) {
            JsonToken.NUMBER -> {
                try {
                    reader.nextInt()
                } catch (e: NumberFormatException) {
                    reader.nextDouble().toInt()
                }
            }
            JsonToken.STRING -> {
                val str = reader.nextString().trim()
                str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt()
            }
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun readNullableBooleanLenient(reader: JsonReader): Boolean? {
        return when (reader.peek()) {
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER -> {
                val num = reader.nextInt()
                num != 0
            }
            JsonToken.STRING -> {
                val str = reader.nextString().trim().lowercase()
                str == "1" || str == "true" || str == "yes"
            }
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun readNullableStringListLenient(reader: JsonReader): List<String>? {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                val list = mutableListOf<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    readNullableStringLenient(reader)?.let(list::add)
                }
                reader.endArray()
                list
            }
            JsonToken.STRING -> {
                val str = reader.nextString()
                if (str.isNotBlank()) listOf(str) else null
            }
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }
}
