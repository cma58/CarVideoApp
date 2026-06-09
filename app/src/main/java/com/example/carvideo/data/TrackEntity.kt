package com.example.carvideo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.carvideo.extractor.SearchResultItem

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val isLiked: Boolean = false,
    val lastPlayedTimestamp: Long = 0L,
    val lastPlayedHour: Int = -1,
    val playCount: Int = 0
) {
    fun toSearchResultItem() = SearchResultItem(
        title = title,
        url = url,
        uploader = uploader,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl
    )
}

fun SearchResultItem.toEntity(isLiked: Boolean = false, lastPlayed: Long = 0L, lastHour: Int = -1) = TrackEntity(
    url = url,
    title = title,
    uploader = uploader,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    isLiked = isLiked,
    lastPlayedTimestamp = lastPlayed,
    lastPlayedHour = lastHour
)
