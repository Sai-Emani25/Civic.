package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "civic_reels")
data class CivicReel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val postedBy: String = "Me",
    val likesCount: Int = 0,
    val hasLiked: Boolean = false,
    val joinedCount: Int = 0,
    val hasJoined: Boolean = false,
    val mobilizeDate: String, // e.g., "Saturday, Jun 20 @ 8:00 AM"
    val maxParticipants: Int = 20,
    val videoTemplateKey: String = "waste", // "waste", "pothole", "water", "tree"
    val timestamp: Long = System.currentTimeMillis()
)
