package com.example.carvideo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY lastPlayedTimestamp DESC")
    fun getLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 100")
    fun getHistory(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE url = :url LIMIT 1")
    suspend fun getTrack(url: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE url = :url")
    suspend fun updateLikeStatus(url: String, isLiked: Boolean)

    @Query("UPDATE tracks SET lastPlayedTimestamp = :timestamp, lastPlayedHour = :hour, playCount = playCount + 1 WHERE url = :url")
    suspend fun recordPlay(url: String, timestamp: Long, hour: Int)

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR uploader LIKE '%' || :query || '%'")
    suspend fun searchLocal(query: String): List<TrackEntity>
}
