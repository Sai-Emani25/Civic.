package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "civic_notifications")
data class CivicNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val type: String, // "status_update" or "comment_reply"
    val reportId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
