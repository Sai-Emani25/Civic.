package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "civic_reports")
data class CivicReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imageUri: String,
    val isSampleImage: Boolean = false,
    val sampleImageKey: String = "", // "trash", "road", "water", "tree"
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val issueCategory: String,
    val severity: String,
    val description: String,
    val routingDepartment: String,
    val publicHazardFlag: Boolean,
    val status: String = "Reported", // "Reported", "Acknowledged", "In Progress", "Resolved"
    val postedBy: String = "Me"
)
