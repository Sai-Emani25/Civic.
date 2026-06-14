package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "civic_comments")
data class CivicComment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reportId: Int,
    val username: String,
    val commentText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val statusProofImage: String = "", // URI or preset key for picture proving cleaned/taken care of
    val isStatusProof: Boolean = false // Marker if this comment is a cleanup status proof
)
