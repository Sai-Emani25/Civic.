package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.api.*
import com.example.data.CivicReport
import com.example.data.CivicRepository
import com.example.data.CivicComment
import com.example.data.SavedReport
import com.example.data.CivicNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CivicViewModel(private val repository: CivicRepository) : ViewModel() {

    // List of reports observing room database reactively
    val reports: StateFlow<List<CivicReport>> = repository.allReports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedReports: StateFlow<List<SavedReport>> = repository.allSavedReports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val reels: StateFlow<List<com.example.data.CivicReel>> = repository.allReels
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val notifications: StateFlow<List<CivicNotification>> = repository.allNotifications
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allComments: StateFlow<List<CivicComment>> = repository.allComments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userSocialCredits = MutableStateFlow(3200) // Default starting points to test shopping, custom designs & titles immediately!
    val lifetimeSocialCredits = MutableStateFlow(4400) // Lifetime total earned (including 1200 for initial PlgEcoKit)
    val activeUserTitle = MutableStateFlow("Platinum Ward Ambassador")
    val activeProfileDesign = MutableStateFlow("Default") // "Default", "Emerald Glow", "Retro Cyber Neon", "Sunset Gold Frame"
    val purchasedItems = MutableStateFlow<Set<String>>(setOf("PlgEcoKit")) // Pre-populate with one item to demonstrate inventory

    fun earnCredits(amount: Int) {
        userSocialCredits.value += amount
        lifetimeSocialCredits.value += amount
    }

    fun spendCredits(amount: Int, itemKey: String): Boolean {
        if (userSocialCredits.value >= amount) {
            userSocialCredits.value -= amount
            purchasedItems.value = purchasedItems.value + itemKey
            return true
        }
        return false
    }

    fun activateTitle(title: String) {
        activeUserTitle.value = title
    }

    fun activateProfileDesign(design: String) {
        activeProfileDesign.value = design
    }

    init {
        prepopulateSampleCommunityFeed()
        prepopulateSampleReels()
    }

    private fun prepopulateSampleCommunityFeed() {
        viewModelScope.launch {
            try {
                val currentReports = repository.allReports.first()
                if (currentReports.isEmpty()) {
                    val samples = listOf(
                        CivicReport(
                            imageUri = "",
                            isSampleImage = true,
                            sampleImageKey = "trash",
                            latitude = 12.9725,
                            longitude = 77.5960,
                            issueCategory = "Waste Management",
                            severity = "High",
                            description = "Trash bin overflowing right next to the public park, attracting stray dogs and flies.",
                            routingDepartment = "BBMP Solid Waste Division",
                            publicHazardFlag = false,
                            status = "In Progress",
                            postedBy = "@urban_explorer"
                        ),
                        CivicReport(
                            imageUri = "",
                            isSampleImage = true,
                            sampleImageKey = "pothole",
                            latitude = 12.9695,
                            longitude = 77.5930,
                            issueCategory = "Road Infrastructure",
                            severity = "Critical",
                            description = "Massive pothole in the left lane here. Already saw a scooter swerve and almost fall today.",
                            routingDepartment = "BBMP Road Infrastructure Division",
                            publicHazardFlag = true,
                            status = "Reported",
                            postedBy = "@pothole_patrol"
                        ),
                        CivicReport(
                            imageUri = "",
                            isSampleImage = true,
                            sampleImageKey = "water",
                            latitude = 12.9740,
                            longitude = 77.5925,
                            issueCategory = "Water Leak",
                            severity = "Medium",
                            description = "Clean drinking water leaking from a broken pipe and flooding the footpath.",
                            routingDepartment = "BWSSB Water Supply Maintenance",
                            publicHazardFlag = false,
                            status = "Acknowledged",
                            postedBy = "@water_sentinel"
                        ),
                        CivicReport(
                            imageUri = "",
                            isSampleImage = true,
                            sampleImageKey = "tree",
                            latitude = 12.9700,
                            longitude = 77.5980,
                            issueCategory = "Fallen Tree",
                            severity = "Critical",
                            description = "Large branch fell during yesterday's rainstorm, blocking half of the roadway.",
                            routingDepartment = "BBMP Forest and Parks Cell",
                            publicHazardFlag = true,
                            status = "Resolved",
                            postedBy = "@green_citizen"
                        )
                    )

                    withContext(Dispatchers.IO) {
                        for (sample in samples) {
                            val reportId = repository.insertReport(sample)
                            val sampleComments = when (sample.postedBy) {
                                "@urban_explorer" -> listOf(
                                    CivicComment(reportId = reportId.toInt(), username = "@helper_bee", commentText = "Saw this too! The smell is getting unbearable with the afternoon heat."),
                                    CivicComment(reportId = reportId.toInt(), username = "@clean_streets", commentText = "I called the local supervisor, they said a truck will pick it up tomorrow morning.")
                                )
                                "@pothole_patrol" -> listOf(
                                    CivicComment(reportId = reportId.toInt(), username = "@rider_001", commentText = "Thank you for posting this! Almost crashed my scooter here last night."),
                                    CivicComment(reportId = reportId.toInt(), username = "@city_infra", commentText = "Routed and BBMP has marked this for asphalt batching.")
                                )
                                "@water_sentinel" -> listOf(
                                    CivicComment(reportId = reportId.toInt(), username = "@save_water", commentText = "So much water wasted... hope BWSSB plugs this pipe immediately!"),
                                    CivicComment(reportId = reportId.toInt(), username = "@resident_bbr", commentText = "Sent a direct tweet to the ward engineer.")
                                )
                                "@green_citizen" -> listOf(
                                    CivicComment(reportId = reportId.toInt(), username = "@city_tree", commentText = "The sweepers cut up and piled the branches on the side! Road is clear now.")
                                )
                                else -> emptyList()
                            }
                            for (comm in sampleComments) {
                                repository.insertComment(comm)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CivicViewModel", "Failed to prepopulate data", e)
            }
        }
    }

    private fun prepopulateSampleReels() {
        viewModelScope.launch {
            try {
                val currentReels = repository.allReels.first()
                if (currentReels.isEmpty()) {
                    val sampleReels = listOf(
                        com.example.data.CivicReel(
                            description = "🗑️ Heavy litter & plastic wastes choking Vasanth Nagar pathway! Organizing a plogging cleanup with local ward group this Saturday. Join us to clean this mess up! Bags will be provided.",
                            latitude = 12.9725,
                            longitude = 77.5946,
                            postedBy = "@green_alliance",
                            likesCount = 28,
                            hasLiked = false,
                            joinedCount = 8,
                            hasJoined = false,
                            mobilizeDate = "Saturday, Jun 20 @ 7:30 AM",
                            maxParticipants = 15,
                            videoTemplateKey = "waste"
                        ),
                        com.example.data.CivicReel(
                            description = "🕳️ Hazardous open potholes along Shanthala Nagar main access road. Planning a rapid gravel fill and cone-marking run this Sunday morning. Need 6 helpers with spades!",
                            latitude = 12.9740,
                            longitude = 77.5946,
                            postedBy = "@pothole_warriors",
                            likesCount = 45,
                            hasLiked = false,
                            joinedCount = 12,
                            hasJoined = false,
                            mobilizeDate = "Sunday, Jun 21 @ 9:00 AM",
                            maxParticipants = 20,
                            videoTemplateKey = "pothole"
                        ),
                        com.example.data.CivicReel(
                            description = "🌳 fallen tree branches blocking pedestrian access path on 4th Cross. Let's gather to chop down and shift branches to community composting unit. Fast 1-hr session!",
                            latitude = 12.9700,
                            longitude = 77.5946,
                            postedBy = "@ward77_helpers",
                            likesCount = 19,
                            hasLiked = false,
                            joinedCount = 5,
                            hasJoined = false,
                            mobilizeDate = "Saturday, Jun 20 @ 4:00 PM",
                            maxParticipants = 8,
                            videoTemplateKey = "tree"
                        )
                    )
                    withContext(Dispatchers.IO) {
                        for (reel in sampleReels) {
                            repository.insertReel(reel)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CivicViewModel", "Failed to prepopulate reels", e)
            }
        }
    }

    val isAnalyzing = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    // Current inputs
    val latitude = MutableStateFlow("12.9716")
    val longitude = MutableStateFlow("77.5946")
    val selectedPresetKey = MutableStateFlow<String?>("trash") // default to waste / trash preset
    val selectedCustomImageUri = MutableStateFlow<Uri?>(null)

    // UI state indicator for API Key warning (if using local simulator)
    val isDemoMode: StateFlow<Boolean> = MutableStateFlow(
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY"
        } catch (e: Throwable) {
            true
        }
    ).stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun selectPreset(key: String) {
        selectedPresetKey.value = key
        selectedCustomImageUri.value = null
    }

    fun selectCustomImage(uri: Uri) {
        selectedCustomImageUri.value = uri
        selectedPresetKey.value = null
    }

    fun deleteReport(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReport(id)
        }
    }

    fun updateReportStatus(id: Int, status: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = reports.value.find { it.id == id }
            repository.updateStatus(id, status)
            if (status.lowercase() == "resolved") {
                earnCredits(300) // +300 Social Credits for Resolving/Cleaning an issue!
            }
            if (report != null && report.postedBy == "Me") {
                repository.insertNotification(
                    CivicNotification(
                        title = "Report Status Updated",
                        message = "Your reported issue '${report.issueCategory}' is now '$status'.",
                        type = "status_update",
                        reportId = id
                    )
                )
            }
        }
    }

    fun clearAllReports() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    // High fidelity simulator when API Key is missing or user requests simulation
    private fun runLocalSimulatedRouting(presetKey: String?, lat: Double, lon: Double): CivicRoutingResult {
        val isBlr = Math.abs(lat - 12.9716) < 0.5 && Math.abs(lon - 77.5946) < 0.5
        val isNy = Math.abs(lat - 40.7128) < 0.5 && Math.abs(lon - -74.0060) < 0.5
        val isLdn = Math.abs(lat - 51.5074) < 0.5 && Math.abs(lon - -0.1278) < 0.5

        return when (presetKey) {
            "trash" -> {
                val dept = when {
                    isBlr -> "BBMP Solid Waste Division"
                    isNy -> "NYC Dept of Sanitation (DSNY)"
                    isLdn -> "London Borough Waste Management"
                    else -> "Municipal Public Sanitation Dept"
                }
                CivicRoutingResult(
                    issueCategory = "Waste Management",
                    severity = "High",
                    description = "An overflowing commercial dumpster blocking pedestrian path. Spilling organic municipal garbage attracting stray animals.",
                    routingDepartment = dept,
                    publicHazardFlag = false
                )
            }
            "pothole" -> {
                val dept = when {
                    isBlr -> "BBMP Road Infrastructure Division"
                    isNy -> "NYC Dept of Transportation (DOT)"
                    isLdn -> "Transport for London (TfL) Street Care"
                    else -> "Municipal PWD & Highways Dept"
                }
                CivicRoutingResult(
                    issueCategory = "Road Infrastructure",
                    severity = "Critical",
                    description = "Extremely deep jagged pothole in middle of lane with exposed structural gravel. Risking tire blowouts or cyclist falls.",
                    routingDepartment = dept,
                    publicHazardFlag = true
                )
            }
            "water" -> {
                val dept = when {
                    isBlr -> "BWSSB Water Supply Maintenance"
                    isNy -> "NYC Dept of Environmental Protection"
                    isLdn -> "Thames Water Utility"
                    else -> "District Water & Sewage Authority"
                }
                CivicRoutingResult(
                    issueCategory = "Water Leak",
                    severity = "Medium",
                    description = "A sub-surface water utility pipe fracture venting high-pressure clean water directly onto active street level, flooding gutters.",
                    routingDepartment = dept,
                    publicHazardFlag = false
                )
            }
            "tree" -> {
                val dept = when {
                    isBlr -> "BBMP Forest and Parks Cell"
                    isNy -> "NYC Parks & Recreation Dept"
                    isLdn -> "City Forestry Services / Council"
                    else -> "Parks & Emergency Public Works"
                }
                CivicRoutingResult(
                    issueCategory = "Fallen Tree",
                    severity = "Critical",
                    description = "Heavy oak limb completely snapped under shear wind force, blockading active lanes with auxiliary impact on power overhead cables.",
                    routingDepartment = dept,
                    publicHazardFlag = true
                )
            }
            else -> {
                // If custom image loaded, generate an elegant default based on location
                val dept = when {
                    isBlr -> "BBMP Civic Command Center"
                    else -> "Municipal Authority Center"
                }
                CivicRoutingResult(
                    issueCategory = "Environmental Maintenance",
                    severity = "Medium",
                    description = "A civic sustainability issue reported near coordinates ($lat, $lon). Requires rapid local assessment and resolution.",
                    routingDepartment = dept,
                    publicHazardFlag = false
                )
            }
        }
    }

    fun submitReport(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            var latVal = latitude.value.toDoubleOrNull() ?: 12.9716
            var lonVal = longitude.value.toDoubleOrNull() ?: 77.5946

            if (!latVal.isFinite()) {
                latVal = 12.9716
            }
            if (!lonVal.isFinite()) {
                lonVal = 77.5946
            }

            val pKey = selectedPresetKey.value
            val customUri = selectedCustomImageUri.value

            if (pKey == null && customUri == null) {
                errorMessage.value = "Please select or upload an image first."
                return@launch
            }

            isAnalyzing.value = true
            errorMessage.value = null

            try {
                val result: CivicRoutingResult = if (isDemoMode.value) {
                    // Simulating a network delay to present premium loading animations
                    withContext(Dispatchers.IO) {
                        Thread.sleep(1800)
                    }
                    runLocalSimulatedRouting(pKey, latVal, lonVal)
                } else {
                    // Call real Gemini API
                    withContext(Dispatchers.IO) {
                        val apiKey = (BuildConfig.GEMINI_API_KEY as? String) ?: ""
                        
                        // Load bitmap safely with resolution checks and downsampling to prevent OOM
                        val bitmap = when {
                            pKey != null -> {
                                val resId = when (pKey) {
                                    "trash" -> R.drawable.civic_preset_waste_1779646341257
                                    "pothole" -> R.drawable.civic_preset_pothole_1779646358062
                                    "water" -> R.drawable.civic_preset_water_1779646376881
                                    "tree" -> R.drawable.civic_preset_tree_1779646395038
                                    else -> R.drawable.civic_preset_waste_1779646341257
                                }
                                BitmapFactory.decodeResource(context.resources, resId)
                            }
                            customUri != null -> {
                                var sampleSize = 1
                                val options = BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }
                                context.contentResolver.openInputStream(customUri)?.use {
                                    BitmapFactory.decodeStream(it, null, options)
                                }
                                val width = options.outWidth
                                val height = options.outHeight
                                val reqMax = 1024
                                if (width > reqMax || height > reqMax) {
                                    val halfWidth = width / 2
                                    val halfHeight = height / 2
                                    while ((halfWidth / sampleSize) >= reqMax || (halfHeight / sampleSize) >= reqMax) {
                                        sampleSize *= 2
                                    }
                                }
                                val decodeOptions = BitmapFactory.Options().apply {
                                    inSampleSize = sampleSize
                                }
                                context.contentResolver.openInputStream(customUri)?.use {
                                    BitmapFactory.decodeStream(it, null, decodeOptions)
                                }
                            }
                            else -> throw Exception("Bitmap loading failed")
                        }

                        if (bitmap == null) {
                            throw Exception("Could not serialize selected image.")
                        }

                        val base64Image = bitmap.toBase64()

                        val prompt = """
                            You are the AI routing engine for a civic sustainability app. 
                            Users will upload an image of a local issue along with their coordinates. 
                            Your job is to analyze the image, classify the problem, assess its severity, and determine the correct local authority for resolution.

                            The reported issue is located at Coordinates: Latitude = $latVal, Longitude = $lonVal.
                            
                            Please target the correct municipal service organization based on these coordinates. 
                            If near Bengaluru (approx 12.97° N, 77.59° E), route to:
                            - BBMP for general municipal problems, waste accumulation, road potholes, and fallen trees.
                            - BESCOM for damaged electrical wires, streetlights, power infrastructure.
                            - BWSSB for water system leaks, sewer line breaks, open manholes.
                            
                            If located near New York, USA, route to the appropriate NYC agency (e.g. NYC DOT, NYPD, DSNY, DEP).
                            If elsewhere, specify a standard regional equivalent (such as Public Works Department, Waste Clean Division, Water Utility Commission, etc.).

                            Respond ONLY in valid JSON format with the exact keys:
                            - "issue_category": (e.g., "Waste Management", "Road Infrastructure", "Water Leak", "Fallen Tree", "Electrical Hazard")
                            - "severity": (Low, Medium, High, Critical)
                            - "description": A concise 2-sentence summary of the visible issue in the image.
                            - "routing_department": The targeted municipal agency.
                            - "public_hazard_flag": Boolean (true/false) if it poses an immediate physical safety danger.
                        """.trimIndent()

                        val request = GenerateContentRequest(
                            contents = listOf(
                                Content(
                                    parts = listOf(
                                        Part(text = prompt),
                                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                                    )
                                )
                            ),
                            generationConfig = GenerationConfig(
                                responseMimeType = "application/json",
                                temperature = 0.1f
                            )
                        )

                        val response = RetrofitClient.service.generateContent(apiKey, request)
                        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            ?: throw Exception("Empty response received from artificial intelligence engine.")

                        // Dynamic substring parser to isolate the JSON envelope perfectly
                        val startIdx = textResponse.indexOf("{")
                        val endIdx = textResponse.lastIndexOf("}")
                        if (startIdx == -1 || endIdx == -1) {
                            throw Exception("Failed to structure AI output. Raw response: $textResponse")
                        }
                        val cleanJson = textResponse.substring(startIdx, endIdx + 1)
                        val jsonObject = JSONObject(cleanJson)

                        CivicRoutingResult(
                            issueCategory = jsonObject.optString("issue_category", "Custom Civic Issue"),
                            severity = jsonObject.optString("severity", "Medium"),
                            description = jsonObject.optString("description", "A local sustainability concern was classified near reported coordinates."),
                            routingDepartment = jsonObject.optString("routing_department", "Municipal Public Works Department"),
                            publicHazardFlag = jsonObject.optBoolean("public_hazard_flag", false)
                        )
                    }
                }

                // Insert into Database
                val newReport = CivicReport(
                    imageUri = customUri?.toString() ?: "",
                    isSampleImage = pKey != null,
                    sampleImageKey = pKey ?: "",
                    latitude = latVal,
                    longitude = lonVal,
                    issueCategory = result.issueCategory,
                    severity = result.severity,
                    description = result.description,
                    routingDepartment = result.routingDepartment,
                    publicHazardFlag = result.publicHazardFlag,
                    status = "Reported"
                )

                withContext(Dispatchers.IO) {
                    repository.insertReport(newReport)
                }
                earnCredits(50) // +50 Social Credits for reporting!

                // Reset state
                selectedCustomImageUri.value = null
                selectedPresetKey.value = "trash"

                onSuccess()

            } catch (e: Exception) {
                Log.e("CivicViewModel", "Routing failed", e)
                errorMessage.value = "Analysis failed: ${e.localizedMessage ?: "Unknown Error"}"
            } finally {
                isAnalyzing.value = false
            }
        }
    }

    fun saveReport(report: CivicReport) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = SavedReport(
                reportId = report.id,
                originalIssueCategory = report.issueCategory,
                originalDescription = report.description,
                originalPostedBy = report.postedBy,
                originalSampleImageKey = report.sampleImageKey,
                originalImageUri = report.imageUri,
                isSampleImage = report.isSampleImage,
                savedAt = System.currentTimeMillis()
            )
            repository.saveReport(saved)
        }
    }

    fun unsaveReport(reportId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.unsaveReport(reportId)
        }
    }

    fun addComment(
        reportId: Int,
        username: String,
        commentText: String,
        statusProofImage: String = "",
        isStatusProof: Boolean = false,
        onFinished: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (commentText.isNotBlank()) {
                val comment = CivicComment(
                    reportId = reportId,
                    username = username.ifBlank { "@anonymous" },
                    commentText = commentText,
                    timestamp = System.currentTimeMillis(),
                    statusProofImage = statusProofImage,
                    isStatusProof = isStatusProof
                )
                repository.insertComment(comment)
                
                // Award Credits
                if (isStatusProof) {
                    earnCredits(200) // +200 credits for uploading cleanup/resolve proof photo & message!
                } else {
                    earnCredits(20) // +20 credits for general community discussion engagement!
                }

                // In-app notification setup: alert user if someone replies to their discussion thread!
                val report = reports.value.find { it.id == reportId }
                val normalisedUser = username.trim().lowercase()
                val isMeComment = normalisedUser == "@me" || normalisedUser == "me"
                if (report != null && report.postedBy == "Me" && !isMeComment) {
                    repository.insertNotification(
                        CivicNotification(
                            title = "New Thread Reply",
                            message = "${comment.username} replied: \"${comment.commentText}\"",
                            type = "comment_reply",
                            reportId = reportId
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    onFinished()
                }
            }
        }
    }

    fun insertNotification(title: String, message: String, type: String, reportId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertNotification(
                CivicNotification(
                    title = title,
                    message = message,
                    type = type,
                    reportId = reportId
                )
            )
        }
    }

    fun markNotificationAsRead(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markNotificationAsRead(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllNotifications()
        }
    }

    fun getCommentsForReport(reportId: Int): Flow<List<CivicComment>> {
        return repository.getCommentsForReport(reportId)
    }

    // Reel operations
    fun toggleReelLike(id: Int, currentLikes: Int, hasLiked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextLike = !hasLiked
            val nextCount = if (nextLike) currentLikes + 1 else currentLikes - 1
            repository.updateReelLike(id, nextCount.coerceAtLeast(0), nextLike)
        }
    }

    fun toggleReelJoin(id: Int, currentJoinedCount: Int, hasJoined: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val nextJoin = !hasJoined
            val nextCount = if (nextJoin) currentJoinedCount + 1 else currentJoinedCount - 1
            repository.updateReelJoin(id, nextCount.coerceAtLeast(0), nextJoin)
            if (nextJoin) {
                earnCredits(100) // Join Mobilization Cleanups yields +100 Social Credits
            }
        }
    }

    fun submitReel(description: String, latitude: Double, longitude: Double, mobilizeDate: String, maxParticipants: Int, videoTemplateKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val reel = com.example.data.CivicReel(
                description = description,
                latitude = latitude,
                longitude = longitude,
                postedBy = "@me_active",
                likesCount = 0,
                hasLiked = false,
                joinedCount = 1, // Created is automatically joined
                hasJoined = true,
                mobilizeDate = mobilizeDate,
                maxParticipants = maxParticipants,
                videoTemplateKey = videoTemplateKey
            )
            repository.insertReel(reel)
        }
    }

    fun deleteReel(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReel(id)
        }
    }
}

class CivicViewModelFactory(private val repository: CivicRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CivicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CivicViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
