package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CivicReportDao {
    @Query("SELECT * FROM civic_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<CivicReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: CivicReport): Long

    @Query("DELETE FROM civic_reports WHERE id = :id")
    suspend fun deleteReportById(id: Int)

    @Query("UPDATE civic_reports SET status = :status WHERE id = :id")
    suspend fun updateReportStatus(id: Int, status: String)

    @Query("DELETE FROM civic_reports")
    suspend fun clearAll()

    // Discussion threads / Comments operations
    @Query("SELECT * FROM civic_comments WHERE reportId = :reportId ORDER BY timestamp ASC")
    fun getCommentsForReport(reportId: Int): Flow<List<CivicComment>>

    @Query("SELECT * FROM civic_comments")
    fun getAllComments(): Flow<List<CivicComment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CivicComment): Long

    @Query("DELETE FROM civic_comments WHERE reportId = :reportId")
    suspend fun deleteCommentsForReport(reportId: Int)

    // Saved posts / Bookmarks operations
    @Query("SELECT * FROM saved_reports ORDER BY savedAt DESC")
    fun getAllSavedReports(): Flow<List<SavedReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReport(savedReport: SavedReport): Long

    @Query("DELETE FROM saved_reports WHERE reportId = :reportId")
    suspend fun unsaveReport(reportId: Int)

    // Reels operations
    @Query("SELECT * FROM civic_reels ORDER BY timestamp DESC")
    fun getAllReels(): Flow<List<CivicReel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReel(reel: CivicReel): Long

    @Query("UPDATE civic_reels SET likesCount = :likesCount, hasLiked = :hasLiked WHERE id = :id")
    suspend fun updateReelLike(id: Int, likesCount: Int, hasLiked: Boolean)

    @Query("UPDATE civic_reels SET joinedCount = :joinedCount, hasJoined = :hasJoined WHERE id = :id")
    suspend fun updateReelJoin(id: Int, joinedCount: Int, hasJoined: Boolean)

    @Query("DELETE FROM civic_reels WHERE id = :id")
    suspend fun deleteReelById(id: Int)

    // Notification operations
    @Query("SELECT * FROM civic_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<CivicNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: CivicNotification): Long

    @Query("UPDATE civic_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)

    @Query("DELETE FROM civic_notifications")
    suspend fun clearAllNotifications()
}
