package com.example.data

import kotlinx.coroutines.flow.Flow

class CivicRepository(private val civicReportDao: CivicReportDao) {
    val allReports: Flow<List<CivicReport>> = civicReportDao.getAllReports()
    val allSavedReports: Flow<List<SavedReport>> = civicReportDao.getAllSavedReports()

    suspend fun insertReport(report: CivicReport): Long {
        return civicReportDao.insertReport(report)
    }

    suspend fun deleteReport(id: Int) {
        civicReportDao.deleteReportById(id)
        // Cascade delete comments for this report when report is deleted
        civicReportDao.deleteCommentsForReport(id)
    }

    suspend fun updateStatus(id: Int, status: String) {
        civicReportDao.updateReportStatus(id, status)
    }

    suspend fun clearAll() {
        civicReportDao.clearAll()
    }

    val allComments: Flow<List<CivicComment>> = civicReportDao.getAllComments()

    fun getCommentsForReport(reportId: Int): Flow<List<CivicComment>> {
        return civicReportDao.getCommentsForReport(reportId)
    }

    suspend fun insertComment(comment: CivicComment): Long {
        return civicReportDao.insertComment(comment)
    }

    suspend fun deleteCommentsForReport(reportId: Int) {
        civicReportDao.deleteCommentsForReport(reportId)
    }

    suspend fun saveReport(savedReport: SavedReport): Long {
        return civicReportDao.saveReport(savedReport)
    }

    suspend fun unsaveReport(reportId: Int) {
        civicReportDao.unsaveReport(reportId)
    }

    val allReels: Flow<List<CivicReel>> = civicReportDao.getAllReels()

    suspend fun insertReel(reel: CivicReel): Long {
        return civicReportDao.insertReel(reel)
    }

    suspend fun updateReelLike(id: Int, likesCount: Int, hasLiked: Boolean) {
        civicReportDao.updateReelLike(id, likesCount, hasLiked)
    }

    suspend fun updateReelJoin(id: Int, joinedCount: Int, hasJoined: Boolean) {
        civicReportDao.updateReelJoin(id, joinedCount, hasJoined)
    }

    suspend fun deleteReel(id: Int) {
        civicReportDao.deleteReelById(id)
    }

    val allNotifications: Flow<List<CivicNotification>> = civicReportDao.getAllNotifications()

    suspend fun insertNotification(notification: CivicNotification): Long {
        return civicReportDao.insertNotification(notification)
    }

    suspend fun markNotificationAsRead(id: Int) {
        civicReportDao.markNotificationAsRead(id)
    }

    suspend fun clearAllNotifications() {
        civicReportDao.clearAllNotifications()
    }
}
