package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_reports")
data class SavedReport(
    @PrimaryKey val reportId: Int,
    val originalIssueCategory: String,
    val originalDescription: String,
    val originalPostedBy: String,
    val originalSampleImageKey: String,
    val originalImageUri: String,
    val isSampleImage: Boolean,
    val savedAt: Long = System.currentTimeMillis()
)
