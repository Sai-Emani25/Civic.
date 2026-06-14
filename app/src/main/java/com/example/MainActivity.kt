package com.example

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.CivicDatabase
import com.example.data.CivicReport
import com.example.data.CivicRepository
import com.example.data.CivicComment
import com.example.data.SavedReport
import com.example.data.CivicNotification
import com.example.ui.CivicViewModel
import com.example.ui.CivicViewModelFactory
import com.example.ui.theme.MyApplicationTheme

fun getWardName(lat: Double, lon: Double): String {
    return if (lat == 12.9725) {
        "Vasanth Nagar (Ward 93)"
    } else if (lat == 12.9695) {
        "Shivajinagar (Ward 92)"
    } else if (lat == 12.9740) {
        "Shanthala Nagar (Ward 111)"
    } else if (lat == 12.9700) {
        "Sampangiram Nagar (Ward 77)"
    } else {
        val dLat = lat - 12.9716
        val dLon = lon - 77.5946
        when {
            dLat >= 0 && dLon >= 0 -> "Vasanth Nagar (Ward 93)"
            dLat >= 0 && dLon < 0 -> "Shanthala Nagar (Ward 111)"
            dLat < 0 && dLon >= 0 -> "Sampangiram Nagar (Ward 77)"
            else -> "Shivajinagar (Ward 92)"
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create Database and Repository instances
        val database = CivicDatabase.getDatabase(applicationContext)
        val repository = CivicRepository(database.civicReportDao())

        setContent {
            MyApplicationTheme {
                val viewModel: CivicViewModel = viewModel(
                    factory = CivicViewModelFactory(repository)
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    CivicApp(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun CivicApp(
    viewModel: CivicViewModel,
    modifier: Modifier = Modifier
) {
    // Privacy & Telemetry states
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val savedReports by viewModel.savedReports.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val allComments by viewModel.allComments.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(CivicTab.FEED) }
    var activeThreadId by remember { mutableStateOf<Int?>(null) }
    var activeReportDetails by remember { mutableStateOf<CivicReport?>(null) }
    var isIntakeExpanded by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = activeThreadId != null || activeReportDetails != null) {
        if (activeThreadId != null) {
            activeThreadId = null
        } else if (activeReportDetails != null) {
            activeReportDetails = null
        }
    }

    // Privacy & Telemetry states
    var incognitoEnabled by remember { mutableStateOf(false) }
    var precisionLocation by remember { mutableStateOf(true) }
    var alertNotifications by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    Box(modifier = modifier.fillMaxSize()) {
        if (showNotificationsDialog) {
            NotificationDialog(
                notifications = notifications,
                onDismiss = { showNotificationsDialog = false },
                onMarkAsRead = { id -> viewModel.markNotificationAsRead(id) },
                onClearAll = { viewModel.clearAllNotifications() },
                onSelectReport = { reportId ->
                    activeThreadId = reportId
                }
            )
        }

        if (activeThreadId != null) {
            // Full Overlay sliding discussion threads view
            DiscussionThreadView(
                reportId = activeThreadId!!,
                reports = reports,
                viewModel = viewModel,
                onBack = { activeThreadId = null }
            )
        } else {
            Scaffold(
                bottomBar = {
                    if (!isTablet) {
                        NavigationBar(
                            modifier = Modifier.navigationBarsPadding(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            CivicTab.values().forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (isTablet) {
                        // Side Navigation Rail for tablets & foldables as per dynamic design instructions!
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            CivicTab.values().forEach { tab ->
                                NavigationRailItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    // Master tabs selection rendering
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentTab) {
                            CivicTab.FEED -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        AppTitleBar(
                                            isDemoMode = isDemoMode,
                                            notifications = notifications,
                                            onBellClick = { showNotificationsDialog = true }
                                        )
                                    }

                                    item {
                                        ImpactDashboard()
                                    }

                                    item {
                                        // Expandable Intake card
                                        if (!isIntakeExpanded) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { isIntakeExpanded = true }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "New Report Trigger",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Report Local Civic Hazard (AI Core)",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        } else {
                                            Column {
                                                IntakeCard(
                                                    viewModel = viewModel,
                                                    isAnalyzing = isAnalyzing,
                                                    errorMsg = errorMsg,
                                                    isDemoMode = isDemoMode
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                TextButton(
                                                    onClick = { isIntakeExpanded = false },
                                                    modifier = Modifier.align(Alignment.End)
                                                ) {
                                                    Text("Collapse intake form", color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        ReportsHeader(
                                            reportsCount = reports.size,
                                            onClearAll = { viewModel.clearAllReports() }
                                        )
                                    }

                                    if (reports.isEmpty()) {
                                        item {
                                            EmptyState()
                                        }
                                    } else {
                                        items(reports, key = { it.id }) { report ->
                                            val isSaved = savedReports.any { it.reportId == report.id }
                                            val reportComments = allComments.filter { it.reportId == report.id }
                                            ReportItemCard(
                                                report = report,
                                                comments = reportComments,
                                                isSaved = isSaved,
                                                onSaveToggle = {
                                                    if (isSaved) viewModel.unsaveReport(report.id) else viewModel.saveReport(report)
                                                },
                                                onCommentClick = { activeThreadId = report.id },
                                                onClick = { activeReportDetails = report },
                                                onDelete = { viewModel.deleteReport(report.id) },
                                                onStatusChange = { id, status -> viewModel.updateReportStatus(id, status) }
                                            )
                                        }
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                            CivicTab.REELS -> {
                                CivicReelsScreen(
                                    viewModel = viewModel,
                                    onNavigateToMap = { lat, lon ->
                                        currentTab = CivicTab.MAP
                                    },
                                    onSelectThread = { activeThreadId = it }
                                )
                            }
                            CivicTab.MAP -> {
                                ExploreMapScreen(
                                    reports = reports,
                                    viewModel = viewModel,
                                    savedReports = savedReports,
                                    onSelectThread = { activeThreadId = it }
                                )
                            }
                            CivicTab.SAVED -> {
                                SavedReportsScreen(
                                    savedReports = savedReports,
                                    reports = reports,
                                    viewModel = viewModel,
                                    onSelectThread = { activeThreadId = it }
                                )
                            }
                            CivicTab.MY_POSTS -> {
                                val myReports = reports.filter { it.postedBy == "Me" }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "My Reported Issues",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Manage and trace status on your reported tickets",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (myReports.isEmpty()) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.Campaign,
                                                    contentDescription = "Empty my reports",
                                                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "No Submissions Yet",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Open the Feed tab and trigger the Intake flow to post an issue.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(myReports, key = { it.id }) { report ->
                                                val isSaved = savedReports.any { it.reportId == report.id }
                                                val reportComments = allComments.filter { it.reportId == report.id }
                                                ReportItemCard(
                                                    report = report,
                                                    comments = reportComments,
                                                    isSaved = isSaved,
                                                    onSaveToggle = {
                                                        if (isSaved) viewModel.unsaveReport(report.id) else viewModel.saveReport(report)
                                                    },
                                                    onCommentClick = { activeThreadId = report.id },
                                                    onClick = { activeReportDetails = report },
                                                    onDelete = { viewModel.deleteReport(report.id) },
                                                    onStatusChange = { id, status -> viewModel.updateReportStatus(id, status) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            CivicTab.SETTINGS -> {
                                ProfileSettingsScreen(
                                    reports = reports,
                                    savedReports = savedReports,
                                    viewModel = viewModel,
                                    incognitoEnabled = incognitoEnabled,
                                    onIncognitoChange = { incognitoEnabled = it },
                                    precisionLocation = precisionLocation,
                                    onPrecisionChange = { precisionLocation = it },
                                    alertNotifications = alertNotifications,
                                    onAlertChange = { alertNotifications = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Details dialog
        activeReportDetails?.let { report ->
            val isSaved = savedReports.any { it.reportId == report.id }
            ReportDetailsDialog(
                report = report,
                comments = allComments.filter { it.reportId == report.id },
                onDismiss = { activeReportDetails = null },
                onDelete = {
                    viewModel.deleteReport(report.id)
                    activeReportDetails = null
                },
                onStatusChange = { status ->
                    viewModel.updateReportStatus(report.id, status)
                    activeReportDetails = report.copy(status = status)
                },
                onPostComment = { user, text ->
                    viewModel.addComment(report.id, user, text, isStatusProof = false)
                }
            )
        }
    }
}

@Composable
fun ImpactDashboard() {
    var selectedKpiExplanator by remember { mutableStateOf<String?>(null) }
    var kpiExplainTitle by remember { mutableStateOf("") }
    var kpiExplainDesc by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "PROJECTED SUSTAINABILITY & COMMUNITY IMPACT",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Response Time
            KpiMiniCard(
                value = "-40%",
                label = "Response Delay",
                icon = Icons.Default.Campaign,
                bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    kpiExplainTitle = "Slashing Response Times by 40%"
                    kpiExplainDesc = "Through Google Gemini AI's instant triage, citizen hazard images are categorized and structured in real-time. This bypasses bureaucratic lag, boosting city maintenance response times by up to 40%."
                    selectedKpiExplanator = "response"
                }
            )

            // Card 2: Carbon Reduction
            KpiMiniCard(
                value = "-30%",
                label = "Fleet CO2",
                icon = Icons.Default.Analytics,
                bgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    kpiExplainTitle = "30% Fleet Carbon Reduction"
                    kpiExplainDesc = "By automatically grouping tickets geospatially and prioritizing critical risks, municipal work crews can schedule optimized repair runs. This prevents redundant fleet driving, reducing transportation CO2 by 30%."
                    selectedKpiExplanator = "co2"
                }
            )

            // Card 3: Karma Participation
            KpiMiniCard(
                value = "750+",
                label = "Target Karma",
                icon = Icons.Default.Star,
                bgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = {
                    kpiExplainTitle = "Active Community Engagement"
                    kpiExplainDesc = "Citizens receive 50 Karma points for reporting local issues, and another 10 points when saving threads or contributing posts. Active neighborhoods foster collaboration and drive accountability."
                    selectedKpiExplanator = "karma"
                }
            )

            // Card 4: Water Conservation
            KpiMiniCard(
                value = "Saved!",
                label = "Water & Resource",
                icon = Icons.Default.Info,
                bgColor = Color(0xFFE3F2FD),
                textColor = Color(0xFF1565C0),
                onClick = {
                    kpiExplainTitle = "Conserving Vital Resources"
                    kpiExplainDesc = "Unattended utility leaks waste millions of gallons of clean drinking water. Instant automated alerts route water pipe issues to maintenance cells immediately to preserve critical natural resources."
                    selectedKpiExplanator = "water"
                }
            )
        }
    }

    if (selectedKpiExplanator != null) {
        AlertDialog(
            onDismissRequest = { selectedKpiExplanator = null },
            confirmButton = {
                TextButton(onClick = { selectedKpiExplanator = null }) {
                    Text("Got it")
                }
            },
            title = {
                Text(
                    text = kpiExplainTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = kpiExplainDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
}

@Composable
fun KpiMiniCard(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = textColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor.copy(alpha = 0.8f)
            )
            Text(
                text = "Tap to explain",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun AppTitleBar(
    isDemoMode: Boolean,
    notifications: List<CivicNotification> = emptyList(),
    onBellClick: () -> Unit = {}
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1.5f)
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "CivicResolve AI Logo",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "CIVICRESOLVE AI",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Notifications bell with badge
            val unreadCount = notifications.count { !it.isRead }
            IconButton(
                onClick = onBellClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = Color(0xFFC62828),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications bell",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // High-fidelity active status badge
            Surface(
                color = if (isDemoMode) Color(0xFFFFECEF) else Color(0xFFE8F5E9),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isDemoMode) Color(0xFFFFB74D).copy(alpha = 0.5f) else Color(0xFF81C784).copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isDemoMode) Color(0xFFB3261E) else Color(0xFF2E7D32))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDemoMode) "Simulation Mode" else "Gemini Live API",
                        color = if (isDemoMode) Color(0xFF1D1B20) else Color(0xFF2E7D32),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
        }
        Text(
            text = "Autonomous municipal classification and emergency threat routing service.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun IntakeCard(
    viewModel: CivicViewModel,
    isAnalyzing: Boolean,
    errorMsg: String?,
    isDemoMode: Boolean
) {
    val selectedPreset by viewModel.selectedPresetKey.collectAsStateWithLifecycle()
    val customUri by viewModel.selectedCustomImageUri.collectAsStateWithLifecycle()
    val latField by viewModel.latitude.collectAsStateWithLifecycle()
    val lonField by viewModel.longitude.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Launchers for picking images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectCustomImage(uri)
        }
    }

    val fallbackPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.selectCustomImage(uri)
        }
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group 1: Select Issue Image
            Text(
                text = "1. Classify Civic Issue Image",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Horizontal selection scroll for preloaded graphics
            Text(
                text = "Select high fidelity preset issue to test classification:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetItemCard(
                    title = "Trash Pile",
                    key = "trash",
                    resId = R.drawable.civic_preset_waste_1779646341257,
                    isSelected = selectedPreset == "trash",
                    testTag = "preset_trash_chip",
                    onClick = { viewModel.selectPreset("trash") }
                )
                PresetItemCard(
                    title = "Major Pothole",
                    key = "pothole",
                    resId = R.drawable.civic_preset_pothole_1779646358062,
                    isSelected = selectedPreset == "pothole",
                    testTag = "preset_pothole_chip",
                    onClick = { viewModel.selectPreset("pothole") }
                )
                PresetItemCard(
                    title = "Pipe Leak",
                    key = "water",
                    resId = R.drawable.civic_preset_water_1779646376881,
                    isSelected = selectedPreset == "water",
                    testTag = "preset_water_chip",
                    onClick = { viewModel.selectPreset("water") }
                )
                PresetItemCard(
                    title = "Fallen Tree",
                    key = "tree",
                    resId = R.drawable.civic_preset_tree_1779646395038,
                    isSelected = selectedPreset == "tree",
                    testTag = "preset_tree_chip",
                    onClick = { viewModel.selectPreset("tree") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Or upload custom option
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        } catch (e: Exception) {
                            try {
                                fallbackPickerLauncher.launch("image/*")
                            } catch (ex: Exception) {
                                android.widget.Toast.makeText(
                                    context,
                                    "No photo picker or file explorer application found on this device.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    .background(
                        color = if (customUri != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (customUri != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .testTag("custom_image_picker")
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "Add custom photo icon",
                    tint = if (customUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (customUri != null) "Custom Image Selected" else "Or Upload Local Custom Image",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (customUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (customUri != null) "Tap here to change image" else "Select any photo from your device's gallery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (customUri != null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Confirm selection",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Group 2: Coordinates Location Selector
            Text(
                text = "2. Set GPS Coordinates & Vicinity",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Target different jurisdictions to evaluate dynamic municipal routing maps:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Jurisdiction presetted location buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetCityChip(
                    name = "Bengaluru, IN",
                    lat = 12.9716,
                    lon = 77.5946,
                    currentLat = latField,
                    currentLon = lonField,
                    onSelect = { lat, lon ->
                        viewModel.latitude.value = lat.toString()
                        viewModel.longitude.value = lon.toString()
                    },
                    modifier = Modifier.weight(1.3f)
                )
                PresetCityChip(
                    name = "New York, US",
                    lat = 40.7128,
                    lon = -74.0060,
                    currentLat = latField,
                    currentLon = lonField,
                    onSelect = { lat, lon ->
                        viewModel.latitude.value = lat.toString()
                        viewModel.longitude.value = lon.toString()
                    },
                    modifier = Modifier.weight(1.3f)
                )
                PresetCityChip(
                    name = "London, UK",
                    lat = 51.5074,
                    lon = -0.1278,
                    currentLat = latField,
                    currentLon = lonField,
                    onSelect = { lat, lon ->
                        viewModel.latitude.value = lat.toString()
                        viewModel.longitude.value = lon.toString()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Latitude and Longitude Input Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = latField,
                    onValueChange = { viewModel.latitude.value = it },
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lat_input_field")
                )

                OutlinedTextField(
                    value = lonField,
                    onValueChange = { viewModel.longitude.value = it },
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lon_input_field")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isDemoMode) {
                // High fidelity informative banner of local simulator mode which explains API Key usage
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFCC80)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Alert icon",
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Prototyping Simulator Active",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "To enable live multi-modal Gemini AI parsing, add your GEMINI_API_KEY inside the Secrets panel in AI Studio sidebar.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5D4037),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // Error display
            errorMsg?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Routing Action Button
            Button(
                onClick = {
                    viewModel.submitReport(context) {
                        // Success block
                    }
                },
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDemoMode) Color(0xFF1B9E65) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_report_button")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "Analyzing Civic Image...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Analyze icon Action"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDemoMode) "Analyze & Route Issue" else "Run Gemini AI Router",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetItemCard(
    title: String,
    key: String,
    resId: Int,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1.0f)
    val cardStrokeColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp)
                )
                .clip(RoundedCornerShape(14.dp))
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = "$title preset",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected logo",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.sp
            ),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PresetCityChip(
    name: String,
    lat: Double,
    lon: Double,
    currentLat: String,
    currentLon: String,
    onSelect: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = currentLat.toDoubleOrNull() == lat && currentLon.toDoubleOrNull() == lon
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clickable { onSelect(lat, lon) }
            .minimumInteractiveComponentSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ReportsHeader(
    reportsCount: Int,
    onClearAll: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Dispatch History Ticket Logs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = reportsCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (reportsCount > 0) {
            TextButton(
                onClick = onClearAll,
                modifier = Modifier.testTag("reset_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear all database elements logo",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear logs", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ReportsList(
    reports: List<CivicReport>,
    savedReports: List<SavedReport>,
    viewModel: CivicViewModel,
    onCardClick: (CivicReport) -> Unit,
    onDeleteClick: (Int) -> Unit,
    onStatusChange: (Int, String) -> Unit,
    onSelectThread: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reports.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize()
        ) {
            EmptyState()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(reports, key = { it.id }) { report ->
                val isSaved = savedReports.any { it.reportId == report.id }
                ReportItemCard(
                    report = report,
                    isSaved = isSaved,
                    onSaveToggle = {
                        if (isSaved) {
                            viewModel.unsaveReport(report.id)
                        } else {
                            viewModel.saveReport(report)
                        }
                    },
                    onCommentClick = { onSelectThread(report.id) },
                    onClick = { onCardClick(report) },
                    onDelete = { onDeleteClick(report.id) },
                    onStatusChange = onStatusChange
                )
            }
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.DashboardCustomize,
            contentDescription = "Empty list icon layout",
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No Civic Reports Logged Yet",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Select one of the quick preset civic issues above and run the AI Router process.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
        )
    }
}

@Composable
fun ReportItemCard(
    report: CivicReport,
    comments: List<CivicComment> = emptyList(),
    isSaved: Boolean,
    onSaveToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (Int, String) -> Unit
) {
    // Dynamic styling based on severity level
    val severityPair = when (report.severity.lowercase()) {
        "low", "minor" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        "medium", "moderate" -> Color(0xFFFFFDE7) to Color(0xFFF57F17)
        "high" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        "critical" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = if (report.publicHazardFlag) 1.5.dp else 0.dp,
                    color = if (report.publicHazardFlag) Color(0xFFFF5722) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Part - Preview Image
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (report.isSampleImage) {
                        val imgRes = when (report.sampleImageKey) {
                            "trash" -> R.drawable.civic_preset_waste_1779646341257
                            "pothole" -> R.drawable.civic_preset_pothole_1779646358062
                            "water" -> R.drawable.civic_preset_water_1779646376881
                            "tree" -> R.drawable.civic_preset_tree_1779646395038
                            else -> R.drawable.civic_preset_waste_1779646341257
                        }
                        Image(
                            painter = painterResource(id = imgRes),
                            contentDescription = "Preset preview thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (report.imageUri.isNotEmpty()) {
                        AsyncImage(
                            model = report.imageUri,
                            contentDescription = "Custom preview thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Preview fallbacks",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                // Middle Part - Info details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = report.issueCategory,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Severity Tag
                        Surface(
                            color = severityPair.first,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(18.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = report.severity,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = severityPair.second
                                )
                            }
                        }
                    }

                    // Direct Targeted routing authority
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = "District organization icon",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = report.routingDepartment,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Coordinates & Date span
                    Text(
                        text = "Coord: ${String.format("%.4f", report.latitude)}, ${String.format("%.4f", report.longitude)} • ${
                            DateUtils.getRelativeTimeSpanString(
                                report.timestamp,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            )
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Ward Location Icon",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = getWardName(report.latitude, report.longitude),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                // Right Part: Quick actions drop / delete icon
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete report ticket",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Public threat flashing danger banner
            if (report.publicHazardFlag) {
                Surface(
                    color = Color(0xFFFFE0B2),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Hazard Alert mark",
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IMMEDIATE PHYSICAL HAZARD TO PUBLIC DETECTED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE65100)
                            )
                        )
                    }
                }
            }

            // Instagram-style Interaction Row (Like, Comment, Bookmarks, and posted by badge!)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    var isLiked by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { isLiked = !isLiked },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like item",
                            tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onCommentClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Thread discussion",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "by ${report.postedBy}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                IconButton(
                    onClick = onSaveToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save bookmark item",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Dynamic Progress Status bar tracker
            val hasVideoOrPhoto = comments.any { it.isStatusProof || it.statusProofImage.trim().isNotEmpty() }
            val hasComments = comments.isNotEmpty()

            val dStatusText: String
            val dStatusBg: Color
            val dStatusTextCol: Color

            if (report.status.lowercase() == "resolved") {
                dStatusText = "Resolved ✅"
                dStatusBg = Color(0xFFE8F5E9)
                dStatusTextCol = Color(0xFF2E7D32)
            } else if (hasVideoOrPhoto) {
                dStatusText = "Under Review 🔍"
                dStatusBg = Color(0xFFF3E5F5) // light purple
                dStatusTextCol = Color(0xFF7B1FA2) // deep purple
            } else if (hasComments) {
                dStatusText = "Discussion in Progress"
                dStatusBg = Color(0xFFFFFDE7) // yellow
                dStatusTextCol = Color(0xFFF57F17) // dark gold/yellow
            } else {
                dStatusText = "Open"
                dStatusBg = Color(0xFFE8F5E9) // green
                dStatusTextCol = Color(0xFF2E7D32) // deep green
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ticket Status: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = dStatusBg,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = dStatusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = dStatusTextCol,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Interactive increment status
                if (report.status.lowercase() != "resolved") {
                    if (report.postedBy == "Me") {
                        TextButton(
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            onClick = {
                                val nextStatus = when (report.status.lowercase()) {
                                    "reported" -> "Acknowledged"
                                    "acknowledged" -> "In Progress"
                                    "in progress" -> "Resolved"
                                    else -> "Resolved"
                                }
                                onStatusChange(report.id, nextStatus)
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Advance Status",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardDoubleArrowRight,
                                    contentDescription = "Advance logo",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked badge",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "By ${report.postedBy}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success icon resolved status",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Closed Ticket",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportDetailsDialog(
    report: CivicReport,
    comments: List<CivicComment> = emptyList(),
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit,
    onPostComment: (String, String) -> Unit = { _, _ -> }
) {
    var newCommentText by remember { mutableStateOf("") }
    var newUsernameField by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Image Analysis Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.77f)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFE2E8F0))
                        .border(1.5.dp, Color.White, RoundedCornerShape(24.dp))
                ) {
                    if (report.isSampleImage) {
                        val imgRes = when (report.sampleImageKey) {
                            "trash" -> R.drawable.civic_preset_waste_1779646341257
                            "pothole" -> R.drawable.civic_preset_pothole_1779646358062
                            "water" -> R.drawable.civic_preset_water_1779646376881
                            "tree" -> R.drawable.civic_preset_tree_1779646395038
                            else -> R.drawable.civic_preset_waste_1779646341257
                        }
                        Image(
                            painter = painterResource(id = imgRes),
                            contentDescription = "Analysis Preview Source",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (report.imageUri.isNotEmpty()) {
                        AsyncImage(
                            model = report.imageUri,
                            contentDescription = "Analysis Preview Source",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top Left overlay status badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "AI VISION ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                fontSize = 9.sp
                            ),
                            color = Color.Black
                        )
                    }

                    // Floating close icon top right
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close description overlay",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Green/Blue pulse dots bottom right
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF3B82F6), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF93C5FD), CircleShape)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Classification Summary Grid (Category & Severity row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Category Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "CATEGORY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = report.issueCategory,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        // Severity Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(24.dp))
                                .border(
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "SEVERITY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PriorityHigh,
                                        contentDescription = "Alert Symbol",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = report.severity.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Description Block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "AI DESCRIPTION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Text(
                                text = report.description,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Routing Engine Details
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ROUTING DEPARTMENT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            fontSize = 10.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = report.routingDepartment,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "External routing route indicator",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Hazard Box
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "HAZARD FLAG: ${report.publicHazardFlag.toString().uppercase()}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = (-0.2).sp,
                                            fontSize = 9.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }

                            // Thin Divider
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                            )

                            // Location & REF info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "location pin indicator",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${String.format("%.4f", report.latitude)}, ${String.format("%.4f", report.longitude)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "REF: 88A-#${report.id}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (report.status.equals("resolved", ignoreCase = true)) {
                        // Let's find any proof-of-cleaning from the comments
                        val proofComment = comments.find { it.isStatusProof && it.statusProofImage.isNotEmpty() }
                        val proofImageKey = proofComment?.statusProofImage ?: when (report.sampleImageKey) {
                            "trash" -> "clean_bins"
                            "pothole" -> "clean_road"
                            "water" -> "clean_pipe"
                            "tree" -> "clean_tree"
                            else -> "clean_bins"
                        }

                        // Pictures section
                        Text(
                            text = "BEFORE & AFTER CLEANUP VISUALS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.1.sp,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFF2E7D32).copy(alpha = 0.9f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Before Image Card
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .background(Color(0xFFE2E8F0))
                                    ) {
                                        if (report.isSampleImage) {
                                            val imgRes = when (report.sampleImageKey) {
                                                "trash" -> R.drawable.civic_preset_waste_1779646341257
                                                "pothole" -> R.drawable.civic_preset_pothole_1779646358062
                                                "water" -> R.drawable.civic_preset_water_1779646376881
                                                "tree" -> R.drawable.civic_preset_tree_1779646395038
                                                else -> R.drawable.civic_preset_waste_1779646341257
                                            }
                                            Image(
                                                painter = painterResource(id = imgRes),
                                                contentDescription = "Original report before image",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (report.imageUri.isNotEmpty()) {
                                            AsyncImage(
                                                model = report.imageUri,
                                                contentDescription = "Original report before image",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Campaign,
                                                    contentDescription = "No before image",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        
                                        Surface(
                                            color = Color(0xFFC62828),
                                            shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "BEFORE",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    Text(
                                        text = "Original Issue Report Area",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // After Image Card
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.4f))
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .background(Color(0xFFC8E6C9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = when (proofImageKey) {
                                                    "clean_bins" -> "🗑️🧹"
                                                    "clean_road" -> "🛣️🛠️"
                                                    "clean_pipe" -> "💧🔧"
                                                    "clean_tree" -> "🌱🌳"
                                                    else -> "📸✅"
                                                },
                                                fontSize = 32.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = when (proofImageKey) {
                                                    "clean_bins" -> "BINS RESTORED"
                                                    "clean_road" -> "ROAD REPAIRED"
                                                    "clean_pipe" -> "LEAK SEALED"
                                                    "clean_tree" -> "PARK RESTORED"
                                                    else -> "CLEANUP COMPLETE"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 8.sp,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            )
                                        }

                                        Surface(
                                            color = Color(0xFF2E7D32),
                                            shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 8.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "AFTER",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    Text(
                                        text = proofComment?.username?.let { "Verified by $it" } ?: "Community Handled ✓",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(8.dp),
                                        color = Color(0xFF2E7D32),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Conversation section
                    Text(
                        text = "CONVERSATION & DISCUSSION HISTORY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.1.sp,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    if (comments.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No conversation logs. Speak up and start the chat below!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            comments.forEach { comment ->
                                val isProof = comment.isStatusProof
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isProof) Color(0xFFE8F5E9).copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    border = if (isProof) BorderStroke(1.dp, Color(0xFFA5D6A7).copy(alpha = 0.5f)) else null,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = comment.username,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isProof) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary
                                                )
                                                if (isProof) {
                                                    Surface(
                                                        color = Color(0xFF2E7D32),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "CLEANUP EVIDENCE 🌱",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 7.sp),
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            Text(
                                                text = DateUtils.getRelativeTimeSpanString(
                                                    comment.timestamp,
                                                    System.currentTimeMillis(),
                                                    DateUtils.MINUTE_IN_MILLIS
                                                ).toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = comment.commentText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Interactive Comment Composer directly inside the Popup dialog!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "TALK & DISCUSSION BOARD",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newUsernameField,
                                    onValueChange = { newUsernameField = it },
                                    placeholder = { Text("Name (e.g. @me)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newCommentText,
                                    onValueChange = { newCommentText = it },
                                    placeholder = { Text("Write your message here...") },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = {
                                        if (newCommentText.isNotBlank()) {
                                            val author = if (newUsernameField.isBlank()) "@me" else if (newUsernameField.startsWith("@")) newUsernameField else "@$newUsernameField"
                                            onPostComment(author, newCommentText)
                                            newCommentText = "" // Clear input
                                        }
                                    },
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Send 💬", style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Action buttons (Dismiss & Confirm Route / Status progress)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (report.status.lowercase() != "resolved") {
                            if (report.postedBy == "Me") {
                                Button(
                                    onClick = {
                                        val nextStatus = when (report.status.lowercase()) {
                                            "reported" -> "Acknowledged"
                                            "acknowledged" -> "In Progress"
                                            "in progress" -> "Resolved"
                                            else -> "Resolved"
                                        }
                                        onStatusChange(nextStatus)
                                    },
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(52.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Confirm Route logo icon inline",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = when (report.status.lowercase()) {
                                                "reported" -> "Confirm Route"
                                                "acknowledged" -> "Begin Dispatch"
                                                else -> "Complete Resolve"
                                            },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(52.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🔒 Only ${report.postedBy} can change status",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailDocRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Detail logo indicator",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

enum class CivicTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FEED("Feed", Icons.Default.Home),
    REELS("Reels", Icons.Default.PlayCircle),
    MAP("Map", Icons.Default.Explore),
    SAVED("Saved", Icons.Default.Bookmark),
    MY_POSTS("My Posts", Icons.Default.AccountCircle),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun ExploreMapScreen(
    reports: List<CivicReport>,
    viewModel: CivicViewModel,
    savedReports: List<SavedReport>,
    onSelectThread: (Int) -> Unit
) {
    var selectedPinReport by remember { mutableStateOf<CivicReport?>(null) }
    val centerLat = 12.9716
    val centerLon = 77.5946

    var selectedCategory by remember { mutableStateOf("All Categories") }
    var selectedWard by remember { mutableStateOf("All Wards") }
    var showOnlyCleaningPoints by remember { mutableStateOf(false) }
    var mapSearchQuery by remember { mutableStateOf("") }

    var zoomScale by remember { mutableStateOf(6000f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    // Pulsing user location GPS circle animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseSize"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val filteredReports = remember(reports, selectedCategory, selectedWard, showOnlyCleaningPoints, mapSearchQuery) {
        reports.filter { report ->
            val matchCategory = selectedCategory == "All Categories" || report.issueCategory.lowercase() == selectedCategory.lowercase()
            val reportWard = getWardName(report.latitude, report.longitude)
            val matchWard = selectedWard == "All Wards" || reportWard == selectedWard
            val matchCleaning = !showOnlyCleaningPoints || report.status.lowercase() == "resolved"
            val matchSearch = mapSearchQuery.isBlank() || 
                    report.description.contains(mapSearchQuery, ignoreCase = true) ||
                    report.issueCategory.contains(mapSearchQuery, ignoreCase = true) ||
                    getWardName(report.latitude, report.longitude).contains(mapSearchQuery, ignoreCase = true)
            matchCategory && matchWard && matchCleaning && matchSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "Community Civic Map",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Interactive GPS rendering of reported hazards in your neighborhood",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Search & GPS status metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = mapSearchQuery,
                onValueChange = { mapSearchQuery = it },
                placeholder = { Text("Search streets, categories, or keywords...", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (mapSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { mapSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f).height(46.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            )

            // Real simulated GPS indicator block
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF7DD3FC))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("📡", fontSize = 12.sp)
                    Column {
                        Text("GPS: FIXED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Black), color = Color(0xFF0369A1))
                        Text("Accuracy: 8m", style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp), color = Color(0xFF0284C7))
                    }
                }
            }
        }

        // Category filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val categories = listOf("All Categories", "Waste Management", "Road Infrastructure", "Water Leak", "Fallen Tree")
            categories.forEach { cat ->
                val isSelected = cat == selectedCategory
                Card(
                    modifier = Modifier.clickable { selectedCategory = cat },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Ward filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val wards = listOf("All Wards", "Vasanth Nagar (Ward 93)", "Shivajinagar (Ward 92)", "Shanthala Nagar (Ward 111)", "Sampangiram Nagar (Ward 77)")
            wards.forEach { ward ->
                val isSelected = ward == selectedWard
                Card(
                    modifier = Modifier.clickable { selectedWard = ward },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = ward,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Points of Cleaning Toggle Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.clickable { showOnlyCleaningPoints = !showOnlyCleaningPoints },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (showOnlyCleaningPoints) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, if (showOnlyCleaningPoints) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (showOnlyCleaningPoints) "🧹 Cleaned Points Only" else "📌 Show All Points",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (showOnlyCleaningPoints) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showOnlyCleaningPoints) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active Filter Check",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Text(
                text = "Show restored locations where mess is cleared",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // MAP CONTAINER WITH GORGEOUS VECTOR ROADWAYS, GREENERY, WATER, AND PULSING GPS BEACON
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF1F5F9)) // nice light map slate canvas background
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val cXPx = wPx / 2f + panX
            val cYPx = hPx / 2f + panY

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                if (changes.isNotEmpty()) {
                                    val change = changes[0]
                                    if (change.pressed) {
                                        val dragAmountX = change.position.x - change.previousPosition.x
                                        val dragAmountY = change.position.y - change.previousPosition.y
                                        panX += dragAmountX
                                        panY += dragAmountY
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Draw land features: Parks
                // Vasanth Nagar Park (Top Right): dLon = 0.003, dLat = 0.002
                val vParkX = cXPx + (0.003f * zoomScale)
                val vParkY = cYPx - (0.002f * zoomScale)
                val vParkW = 140f * (zoomScale / 6000f)
                val vParkH = 100f * (zoomScale / 6000f)
                drawRect(
                    color = Color(0xFFDCFCE7), // soft land emerald park green
                    topLeft = androidx.compose.ui.geometry.Offset(vParkX - vParkW / 2, vParkY - vParkH / 2),
                    size = androidx.compose.ui.geometry.Size(vParkW, vParkH)
                )

                // Shivajinagar Recreation Ground (Bottom Left): dLon = -0.002, dLat = -0.003
                val sGroundX = cXPx + (-0.002f * zoomScale)
                val sGroundY = cYPx - (-0.003f * zoomScale)
                val sGroundW = 160f * (zoomScale / 6000f)
                val sGroundH = 110f * (zoomScale / 6000f)
                drawRect(
                    color = Color(0xFFE8F5E9), 
                    topLeft = androidx.compose.ui.geometry.Offset(sGroundX - sGroundW / 2, sGroundY - sGroundH / 2),
                    size = androidx.compose.ui.geometry.Size(sGroundW, sGroundH)
                )

                // Draw water reservoirs: Sankey Pool (Top Left): dLon = -0.003, dLat = 0.003
                val poolX = cXPx + (-0.003f * zoomScale)
                val poolY = cYPx - (0.003f * zoomScale)
                val poolRadius = 80f * (zoomScale / 6000f)
                drawCircle(
                    color = Color(0xFFBFDBFE), // pool blue
                    radius = poolRadius,
                    center = androidx.compose.ui.geometry.Offset(poolX, poolY)
                )

                // Draw background street grids
                val gridGap = 100f * (zoomScale / 6000f)
                var tempX = panX % gridGap
                while (tempX < size.width) {
                    if (tempX > 0) {
                        drawLine(
                            color = Color(0xFFEAEFF5),
                            start = androidx.compose.ui.geometry.Offset(tempX, 0f),
                            end = androidx.compose.ui.geometry.Offset(tempX, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    tempX += gridGap
                }
                var tempY = panY % gridGap
                while (tempY < size.height) {
                    if (tempY > 0) {
                        drawLine(
                            color = Color(0xFFEAEFF5),
                            start = androidx.compose.ui.geometry.Offset(0f, tempY),
                            end = androidx.compose.ui.geometry.Offset(size.width, tempY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    tempY += gridGap
                }

                // DRAW ROADS
                val mainRoadW = 28f * (zoomScale / 6000f)
                val subRoadW = 20f * (zoomScale / 6000f)

                // Cunningham Road (Main horizontal intersecting centered at lat=12.9716, dLat=0)
                drawLine(
                    color = Color(0xFFCBD5E1), // solid grey road bed
                    start = androidx.compose.ui.geometry.Offset(0f, cYPx),
                    end = androidx.compose.ui.geometry.Offset(size.width, cYPx),
                    strokeWidth = mainRoadW
                )
                // Cunningham Road white center dashes
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(0f, cYPx),
                    end = androidx.compose.ui.geometry.Offset(size.width, cYPx),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )

                // Millers Road (Main vertical intersecting centered at lon=77.5946, dLon=0)
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = androidx.compose.ui.geometry.Offset(cXPx, 0f),
                    end = androidx.compose.ui.geometry.Offset(cXPx, size.height),
                    strokeWidth = mainRoadW
                )
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(cXPx, 0f),
                    end = androidx.compose.ui.geometry.Offset(cXPx, size.height),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )

                // Queen's Road (Horizontal below Cunningham Road: dLat = -0.0022)
                val queensY = cYPx - (-0.0022f * zoomScale)
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = androidx.compose.ui.geometry.Offset(0f, queensY),
                    end = androidx.compose.ui.geometry.Offset(size.width, queensY),
                    strokeWidth = subRoadW
                )

                // Netaji Road (Vertical right of Millers Road: dLon = 0.0028)
                val netajiX = cXPx + (0.0028f * zoomScale)
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = androidx.compose.ui.geometry.Offset(netajiX, 0f),
                    end = androidx.compose.ui.geometry.Offset(netajiX, size.height),
                    strokeWidth = subRoadW
                )
            }

            // OVERLAY: LANDMARK LABELS
            // Label: Vasanth Nagar Garden
            val vParkXDp = with(density) { (cXPx + (0.003f * zoomScale)).toDp() }
            val vParkYDp = with(density) { (cYPx - (0.002f * zoomScale)).toDp() }
            Box(
                modifier = Modifier.offset(x = vParkXDp - 40.dp, y = vParkYDp - 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌳", fontSize = 12.sp)
                    Text(
                        text = "Vasanth Garden",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color(0xFF166534))
                    )
                }
            }

            // Label: Sankey Pool
            val poolXDp = with(density) { (cXPx + (-0.003f * zoomScale)).toDp() }
            val poolYDp = with(density) { (cYPx - (0.003f * zoomScale)).toDp() }
            Box(
                modifier = Modifier.offset(x = poolXDp - 30.dp, y = poolYDp - 14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💧", fontSize = 12.sp)
                    Text(
                        text = "Sankey Reservoir",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E40AF))
                    )
                }
            }

            // Label: Cunningham Rd Text label (Drawn over the road)
            Box(
                modifier = Modifier.offset(x = with(density) { (cXPx).toDp() } - 100.dp, y = with(density) { (cYPx).toDp() } - 18.dp)
            ) {
                Text(
                    text = "🛣️ CUNNINGHAM ROAD",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                    color = Color(0xFF64748B)
                )
            }

            // Label: Millers Rd Text label (Drawn over the road)
            Box(
                modifier = Modifier.offset(x = with(density) { (cXPx).toDp() } + 14.dp, y = with(density) { (cYPx).toDp() } + 70.dp)
            ) {
                Text(
                    text = "🛣️ MILLERS ROAD",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                    color = Color(0xFF64748B)
                )
            }

            // OVERLAY: PULSING GPS "YOU ARE HERE" INTERACTIVE BEACON AT THE INTERSECTION
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (cXPx).toDp() } - 19.dp,
                        y = with(density) { (cYPx).toDp() } - 19.dp
                    )
                    .size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(pulseSize.dp)
                        .background(Color(0xFF3B82F6).copy(alpha = pulseAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color.White, CircleShape)
                        .border(3.dp, Color(0xFF3B82F6), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFF3B82F6), CircleShape)
                )
            }

            // "You are here" Badge
            Box(
                modifier = Modifier.offset(
                    x = with(density) { (cXPx).toDp() } + 14.dp,
                    y = with(density) { (cYPx).toDp() } - 8.dp
                )
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(modifier = Modifier.size(5.dp).background(Color(0xFF3B82F6), CircleShape))
                        Text(
                            text = "YOU ARE HERE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            // OVERLAY: CIVIC ISSUE PIN MARKERS
            filteredReports.forEach { report ->
                if (report.latitude.isFinite() && report.longitude.isFinite()) {
                    val dLat = (report.latitude - centerLat).toFloat()
                    val dLon = (report.longitude - centerLon).toFloat()

                    // Match coordinates to the virtual vector map perfectly!
                    val mapXPx = cXPx + (dLon * zoomScale)
                    val mapYPx = cYPx - (dLat * zoomScale)

                    val mapXDp = with(density) { mapXPx.toDp() } - 19.dp
                    val mapYDp = with(density) { mapYPx.toDp() } - 19.dp

                    val isResolved = report.status.lowercase() == "resolved"
                    val emoji = if (isResolved) {
                        "🧹" // Restored
                    } else {
                        when (report.issueCategory.lowercase()) {
                            "waste management" -> "🗑️"
                            "road infrastructure" -> "🕳️"
                            "water leak" -> "💧"
                            "fallen tree" -> "🌳"
                            else -> "🚨"
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = mapXDp, y = mapYDp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isResolved) Color(0xFFE8F5E9).copy(alpha = 0.95f)
                                else Color.White.copy(alpha = 0.95f)
                            )
                            .border(
                                1.5.dp,
                                if (selectedPinReport?.id == report.id) MaterialTheme.colorScheme.primary
                                else if (isResolved) Color(0xFF2E7D32)
                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .clickable { selectedPinReport = report },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = emoji, fontSize = 16.sp)
                            Icon(
                                imageVector = if (isResolved) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                                contentDescription = "Pin indicator",
                                tint = if (selectedPinReport?.id == report.id) {
                                    MaterialTheme.colorScheme.primary
                                } else if (isResolved) {
                                    Color(0xFF2E7D32)
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }
            }

            // OVERLAY: FLOATING ZOOM CONTROLS, COMPASS RESET, AND GPS HOME ON THE TOP RIGHT
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compass reset to defaults
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "🧭",
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clickable {
                                    panX = 0f
                                    panY = 0f
                                    zoomScale = 6000f
                                    selectedPinReport = null
                                }
                        )
                    }
                }

                // Zoom Out FAB
                FloatingActionButton(
                    onClick = { zoomScale = (zoomScale - 1000f).coerceAtLeast(3000f) },
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(34.dp),
                    elevation = FloatingActionButtonDefaults.elevation(3.dp)
                ) {
                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // Zoom In FAB
                FloatingActionButton(
                    onClick = { zoomScale = (zoomScale + 1000f).coerceAtMost(15000f) },
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(34.dp),
                    elevation = FloatingActionButtonDefaults.elevation(3.dp)
                ) {
                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // GPS Recenter Button
                FloatingActionButton(
                    onClick = {
                        panX = 0f
                        panY = 0f
                        zoomScale = 6000f
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                    elevation = FloatingActionButtonDefaults.elevation(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Recenter",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        selectedPinReport?.let { report ->
            val isSaved = savedReports.any { it.reportId == report.id }

            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = report.issueCategory,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val isResolved = report.status.lowercase() == "resolved"
                            Surface(
                                color = if (isResolved) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isResolved) "🟢 Cleaned & Restored" else report.status,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isResolved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (report.status.lowercase() == "resolved") {
                            Text(
                                text = "🌱 Point of Cleaning! Active citizens restored this locality.",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = report.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            if (isSaved) {
                                viewModel.unsaveReport(report.id)
                            } else {
                                viewModel.saveReport(report)
                            }
                        }) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Save pin",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = { onSelectThread(report.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = "Chat logo",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thread", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedReportsScreen(
    savedReports: List<SavedReport>,
    reports: List<CivicReport>,
    viewModel: CivicViewModel,
    onSelectThread: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "Saved Bookmarks",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Kept posts and reported tracking history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (savedReports.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = "Empty bookmark icon",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Saved Posts",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Bookmark issues from the feed or map to track them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(savedReports, key = { it.reportId }) { saved ->
                    val originalReport = reports.find { it.id == saved.reportId }

                    if (originalReport == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.civic_logo_1779646240088),
                                        contentDescription = "Default civic app icon resource",
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "⚠️ ISSUE POST REMOVED",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${saved.originalIssueCategory} post has been resolved or removed by original author.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }

                                IconButton(onClick = { viewModel.unsaveReport(saved.reportId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Unsave deleted post",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = originalReport.issueCategory,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    IconButton(
                                        onClick = { viewModel.unsaveReport(saved.reportId) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Unsave post",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = originalReport.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isCompleted = originalReport.status.lowercase() == "resolved"
                                    Surface(
                                        color = if (isCompleted) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        border = if (isCompleted) BorderStroke(1.dp, Color(0xFF2E7D32)) else null
                                    ) {
                                        Text(
                                            text = if (isCompleted) "Status: COMPLETED" else "Status: ${originalReport.status}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal),
                                            color = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { onSelectThread(saved.reportId) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = "Thread icon",
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open Thread", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationDialog(
    notifications: List<CivicNotification>,
    onDismiss: () -> Unit,
    onMarkAsRead: (Int) -> Unit,
    onClearAll: () -> Unit,
    onSelectReport: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "In-App Notifications",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = "Empty notifications",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Notifications Yet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "We will notify you when your report's status is updated, or when someone replies to your discussion threads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        val isUnread = !notif.isRead
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isUnread) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMarkAsRead(notif.id)
                                    onSelectReport(notif.reportId)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            color = when (notif.type) {
                                                "status_update" -> Color(0xFFE8F5E9)
                                                else -> Color(0xFFE3F2FD)
                                            },
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (notif.type) {
                                            "status_update" -> Icons.Default.Campaign
                                            else -> Icons.Default.ChatBubbleOutline
                                        },
                                        contentDescription = "Notification type logo",
                                        tint = when (notif.type) {
                                            "status_update" -> Color(0xFF2E7D32)
                                            else -> Color(0xFF1565C0)
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = notif.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = DateUtils.getRelativeTimeSpanString(
                                            notif.timestamp,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS
                                        ).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                
                                if (isUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

data class LevelProgress(
    val levelNumber: Int,
    val levelName: String,
    val currentLevelCredits: Int,
    val nextLevelTarget: Int,
    val progress: Float
)

fun calculateLevelProgress(lifetimeCredits: Int): LevelProgress {
    return when {
        lifetimeCredits < 1000 -> {
            LevelProgress(1, "Eco Seedling 🌱", lifetimeCredits, 1000, lifetimeCredits.toFloat() / 1000f)
        }
        lifetimeCredits < 2500 -> {
            val current = lifetimeCredits - 1000
            val target = 1500
            LevelProgress(2, "Green Sprouts 🌿", current, target, current.toFloat() / target.toFloat())
        }
        lifetimeCredits < 5000 -> {
            val current = lifetimeCredits - 2500
            val target = 2500
            LevelProgress(3, "Civic Guardian 🛡️", current, target, current.toFloat() / target.toFloat())
        }
        lifetimeCredits < 10000 -> {
            val current = lifetimeCredits - 5000
            val target = 5000
            LevelProgress(4, "Eco Warrior 👑", current, target, current.toFloat() / target.toFloat())
        }
        else -> {
            LevelProgress(5, "Earth Ambassador 🌎", lifetimeCredits - 10000, 10000, 1f)
        }
    }
}

@Composable
fun ProfileSettingsScreen(
    reports: List<CivicReport>,
    savedReports: List<SavedReport>,
    viewModel: CivicViewModel,
    incognitoEnabled: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    precisionLocation: Boolean,
    onPrecisionChange: (Boolean) -> Unit,
    alertNotifications: Boolean,
    onAlertChange: (Boolean) -> Unit
) {
    val reportsCopiedCount = reports.filter { it.postedBy == "Me" }.size
    val context = androidx.compose.ui.platform.LocalContext.current

    // Reactive credits states
    val credits by viewModel.userSocialCredits.collectAsStateWithLifecycle()
    val lifetimeCredits by viewModel.lifetimeSocialCredits.collectAsStateWithLifecycle()
    val activeTitle by viewModel.activeUserTitle.collectAsStateWithLifecycle()
    val activeDesign by viewModel.activeProfileDesign.collectAsStateWithLifecycle()
    val purchased by viewModel.purchasedItems.collectAsStateWithLifecycle()

    var selectedShopCategory by remember { mutableStateOf("Profile Perks") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = when (activeDesign) {
                                "Emerald Glow" -> listOf(Color(0xFF2E7D32), Color(0xFF00FF87), Color(0xFF4CAF50))
                                "Retro Cyber Neon" -> listOf(Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFF8A2BE2))
                                "Sunset Gold Frame" -> listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFF4500))
                                else -> listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            }
                        )
                    )
                    .padding(if (activeDesign != "Default") 5.dp else 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.civic_logo_1779646240088),
                        contentDescription = "User profile avatar image reference",
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = reportsCopiedCount.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text("My Posts", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = savedReports.size.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text("Saved", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$credits 🪙",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    )
                    Text("Eco Credits", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "@civic_hero_me",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Surface(
                    color = when (activeTitle) {
                        "Eco Warrior" -> Color(0xFF2E7D32).copy(alpha = 0.12f)
                        "De-Litter Legend" -> Color(0xFFE65100).copy(alpha = 0.12f)
                        "Civic Champion" -> Color(0xFF1976D2).copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(
                        1.dp,
                        when (activeTitle) {
                            "Eco Warrior" -> Color(0xFF2E7D32)
                            "De-Litter Legend" -> Color(0xFFE65100)
                            "Civic Champion" -> Color(0xFF1976D2)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(
                        text = activeTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold, 
                            fontSize = 8.sp,
                            color = when (activeTitle) {
                                "Eco Warrior" -> Color(0xFF2E7D32)
                                "De-Litter Legend" -> Color(0xFFE65100)
                                "Civic Champion" -> Color(0xFF1976D2)
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "Platinum Ward Ambassador • Active Bangalore",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Level Progress Card
        val levelInfo = calculateLevelProgress(lifetimeCredits)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CIVIC STANDING & LEVEL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = levelInfo.levelName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF2E7D32)
                        )
                    }
                    
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "LVL ${levelInfo.levelNumber}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lifetime Earnings: ${lifetimeCredits} 🪙",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (levelInfo.levelNumber < 5) {
                            Text(
                                text = "Next Level in ${levelInfo.nextLevelTarget - levelInfo.currentLevelCredits} pts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                text = "Max Level Reached!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { levelInfo.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = Color(0xFF2E7D32),
                        trackColor = Color(0xFFE8F5E9)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${levelInfo.currentLevelCredits} / ${levelInfo.nextLevelTarget} 🪙",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Redemptions Shop Layout
        Text(
            text = "🌱 Social Credit Shop & Civic Perks",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF2E7D32)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Profile Perks", "Physical Goods", "My Inventory").forEach { tab ->
                val isSelected = selectedShopCategory == tab
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedShopCategory = tab },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF2E7D32) else Color.Transparent)
                ) {
                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        if (selectedShopCategory == "Profile Perks") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Special Badges & Titles",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val titlesOptions = listOf(
                    Triple("Eco Warrior", 300, "For community leaders organizing waste clear drives"),
                    Triple("De-Litter Legend", 600, "Awarded to verified local plogging leaders"),
                    Triple("Civic Champion", 1000, "Supreme title for elite ward coordinator")
                )

                titlesOptions.forEach { (titleItem, cost, desc) ->
                    val hasBought = purchased.contains(titleItem)
                    val isActive = activeTitle == titleItem

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(titleItem, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isActive) {
                                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                    Text("Equipped ✓", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                                }
                            } else if (hasBought) {
                                Button(
                                    onClick = { viewModel.activateTitle(titleItem) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Equip", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val ok = viewModel.spendCredits(cost, titleItem)
                                        if (ok) {
                                            android.widget.Toast.makeText(context, "$titleItem unlocked! Equip it now.", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Insufficient Eco Credits! Needs $cost.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("$cost 🪙", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Aesthetic Glow Frames",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val framesOptions = listOf(
                    Triple("Emerald Glow", 500, "Leafy green halo gradient on your avatar"),
                    Triple("Retro Cyber Neon", 1200, "Interactive synthwave neon border"),
                    Triple("Sunset Gold Frame", 1800, "Premium golden shimmer ring frame")
                )

                framesOptions.forEach { (frameItem, cost, desc) ->
                    val hasBought = purchased.contains(frameItem)
                    val isActive = activeDesign == frameItem

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(frameItem, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isActive) {
                                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                    Text("Equipped ✓", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                                }
                            } else if (hasBought) {
                                Button(
                                    onClick = { viewModel.activateProfileDesign(frameItem) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Equip", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val ok = viewModel.spendCredits(cost, frameItem)
                                        if (ok) {
                                            android.widget.Toast.makeText(context, "$frameItem border unlocked! Elevate your profile looks.", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Insufficient Eco Credits! Needs $cost.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("$cost 🪙", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        } else if (selectedShopCategory == "Physical Goods") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sustainable Goods & Tools",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val realEquipment = listOf(
                    Triple("PlgEcoKit", "Plogging Pro Cleanup Starter Kit 🧹", 1200),
                    Triple("SteelBottle", "Double-walled Steel Water Bottle 🥤", 1500),
                    Triple("SolarPower", "Civic Charge 20K Solar Powerbank 🔋", 5000),
                    Triple("InductionCook", "Smart energy-efficient induction Cooker 🍳", 8000),
                    Triple("NoteProPhone", "Note Pro Sustainable Smartphone 📱", 15000)
                )

                realEquipment.forEach { (itemKey, labelName, itemCost) ->
                    val hasClaimed = purchased.contains(itemKey)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(labelName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text(
                                    text = if (hasClaimed) "Coupon: EXP-REDEEM-${itemKey.uppercase()}-2026" else "Will generate instant offline store coupon",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasClaimed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (hasClaimed) {
                                Button(
                                    onClick = {
                                        val pinSecret = (1000..9999).random()
                                        android.widget.Toast.makeText(context, "Exclusive Claim ID: ECO-$itemKey-$pinSecret. Show at civic centers to collect!", android.widget.Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Claim Coupon", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val ok = viewModel.spendCredits(itemCost, itemKey)
                                        if (ok) {
                                            android.widget.Toast.makeText(context, "Successfully redeemed! Click 'Claim Coupon' to get verification code.", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Need $itemCost Credits! Help resolve issues & post cleanup proofs.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("$itemCost 🪙", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Inventory Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Locker & Unlocked Redemptions 🎒",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val inventoryItems = mutableListOf<Triple<String, String, String>>() // Key, Label/Name, Type
                
                // Add bought titles
                if (purchased.contains("Eco Warrior")) inventoryItems.add(Triple("Eco Warrior", "Eco Warrior Title 🎖️", "Title"))
                if (purchased.contains("De-Litter Legend")) inventoryItems.add(Triple("De-Litter Legend", "De-Litter Legend Title 🎖️", "Title"))
                if (purchased.contains("Civic Champion")) inventoryItems.add(Triple("Civic Champion", "Civic Champion Title 🎖️", "Title"))
                
                // Add bought frames
                if (purchased.contains("Emerald Glow")) inventoryItems.add(Triple("Emerald Glow", "Emerald Glow Profile Frame ✨", "Frame"))
                if (purchased.contains("Retro Cyber Neon")) inventoryItems.add(Triple("Retro Cyber Neon", "Retro Cyber Neon Profile Frame ✨", "Frame"))
                if (purchased.contains("Sunset Gold Frame")) inventoryItems.add(Triple("Sunset Gold Frame", "Sunset Gold Profile Frame ✨", "Frame"))
                
                // Add bought physical goods
                if (purchased.contains("PlgEcoKit")) inventoryItems.add(Triple("PlgEcoKit", "Plogging Pro Cleanup Starter Kit 🧹", "Physical"))
                if (purchased.contains("SteelBottle")) inventoryItems.add(Triple("SteelBottle", "Double-walled Steel Water Bottle 🥤", "Physical"))
                if (purchased.contains("SolarPower")) inventoryItems.add(Triple("SolarPower", "Civic Charge 20K Solar Powerbank 🔋", "Physical"))
                if (purchased.contains("InductionCook")) inventoryItems.add(Triple("InductionCook", "Smart energy-efficient induction Cooker 🍳", "Physical"))
                if (purchased.contains("NoteProPhone")) inventoryItems.add(Triple("NoteProPhone", "Note Pro Sustainable Smartphone 📱", "Physical"))

                if (inventoryItems.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "No items in inventory",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Organize cleanups, post reports, and engage in civic discussions to earn Eco Credits, then unlock exclusive perks from the shop!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    inventoryItems.forEach { (itemKey, labelName, itemType) ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = when (itemType) {
                                                "Title" -> Color(0xFFE3F2FD)
                                                "Frame" -> Color(0xFFF3E5F5)
                                                else -> Color(0xFFE8F5E9)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = itemType.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 8.sp,
                                                    color = when (itemType) {
                                                        "Title" -> Color(0xFF1565C0)
                                                        "Frame" -> Color(0xFF7B1FA2)
                                                        else -> Color(0xFF2E7D32)
                                                    }
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = labelName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (itemType) {
                                            "Title" -> "Active Title: ${if (activeTitle == itemKey) "EQUIPPED" else "INACTIVE"}"
                                            "Frame" -> "Active Frame: ${if (activeDesign == itemKey) "EQUIPPED" else "INACTIVE"}"
                                            else -> "Coupon: EXP-REDEEM-${itemKey.uppercase()}-2026"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                when (itemType) {
                                    "Title" -> {
                                        val isActive = activeTitle == itemKey
                                        if (isActive) {
                                            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    text = "Equipped ✓",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.activateTitle(itemKey) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Equip", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    "Frame" -> {
                                        val isActive = activeDesign == itemKey
                                        if (isActive) {
                                            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    text = "Equipped ✓",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color(0xFF2E7D32),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.activateProfileDesign(itemKey) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Equip", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    "Physical" -> {
                                        Button(
                                            onClick = {
                                                val pinSecret = (1000..9999).random()
                                                android.widget.Toast.makeText(context, "Exclusive Claim ID: ECO-$itemKey-$pinSecret. Show at civic centers to collect!", android.widget.Toast.LENGTH_LONG).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Claim Coupon", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Text(
            text = "Privacy & Telemetry Controls",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Incognito Reporting Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Submit issues anonymously to city councils", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = incognitoEnabled, onCheckedChange = onIncognitoChange)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pin-Point GPS Accuracy", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Enable finer 5-meter coordinates routing telemetry", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = precisionLocation, onCheckedChange = onPrecisionChange)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Urgent Physical Hazard Alerts", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Notify when critical hazards are verified in your ward", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = alertNotifications, onCheckedChange = onAlertChange)
                }
            }
        }

        Text(
            text = "Ward System Settings",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.clearAllReports() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Flush Cached Neighborhood Feed", color = MaterialTheme.colorScheme.onErrorContainer)
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "V2.2.0-Alpha • CivicResolve Engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Powered by Google Gemini AI Routing Solutions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun DiscussionThreadView(
    reportId: Int,
    reports: List<CivicReport>,
    viewModel: CivicViewModel,
    onBack: () -> Unit
) {
    val reels by viewModel.reels.collectAsStateWithLifecycle()
    val isReel = reportId < 0
    val reel = if (isReel) reels.find { it.id == -reportId } else null
    val report = if (!isReel) reports.find { it.id == reportId } else null

    val commentsFlow = remember(reportId) { viewModel.getCommentsForReport(reportId) }
    val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var commentText by remember { mutableStateOf("") }
    var usernameField by remember { mutableStateOf("") }
    
    // Status Proof state management
    var isStatusProof by remember { mutableStateOf(false) }
    var selectedProofImage by remember { mutableStateOf("clean_bins") }

    if (report == null && reel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Details not found or removed.", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (isReel) "Group Discussion Board" else "Discussion & Threads",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (isReel) "Plogging Drive • ${getWardName(reel!!.latitude, reel.longitude)}" else "${report!!.issueCategory} • ${report.routingDepartment}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isReel) "ORGANIZED BY ${reel!!.postedBy.uppercase()}" else "POSTED BY ${report!!.postedBy.uppercase()}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isReel) reel!!.description else report!!.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isReel && report!!.status.lowercase() == "resolved") {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "✓ RESOLVED",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (comments.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Empty comments logo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No comments in this thread yet",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Be the first to share localized updates on this issue!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(comments) { comment ->
                        val isProof = comment.isStatusProof
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isProof) Color(0xFFE8F5E9).copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (isProof) BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = comment.username,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isProof) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary
                                        )
                                        if (isProof) {
                                            Surface(
                                                color = Color(0xFF2E7D32),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "CLEANUP PROOF 🌱",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 8.sp),
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = DateUtils.getRelativeTimeSpanString(
                                            comment.timestamp,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS
                                        ).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = comment.commentText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (isProof && comment.statusProofImage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFE8F5E9)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (comment.statusProofImage) {
                                                    "clean_bins" -> "🗑️🧹"
                                                    "clean_road" -> "🛣️🛠️"
                                                    "clean_pipe" -> "💧🔧"
                                                    "clean_tree" -> "🌱🌳"
                                                    else -> "📸✅"
                                                },
                                                fontSize = 20.sp
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when (comment.statusProofImage) {
                                                    "clean_bins" -> "BINS & SIDEWALK VERIFIED SPOTLESS"
                                                    "clean_road" -> "POTHOLE / ROAD REPAIR EVIDENCE"
                                                    "clean_pipe" -> "WATER SOURCING PIPELINE CLEARED"
                                                    "clean_tree" -> "GREEN SPACE RESTORATION PROOF"
                                                    else -> "CITIZEN CLEANUP PHOTO PROOF"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = Color(0xFF2E7D32)
                                            )
                                            Text(
                                                text = "A community leader verified the hazard area as 'Restored' & marked this a Point of Cleaning.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                
                // Proof selector toggle check
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(
                            checked = isStatusProof,
                            onCheckedChange = { isStatusProof = it }
                        )
                        Text(
                            text = "Submit Cleanup & Resolve Proof (+200 Credits 🪙)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isStatusProof) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Horizontal selector representation
                if (isStatusProof) {
                    Text(
                        text = "Choose Proof Scene Asset to attach:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val scenes = listOf(
                            Pair("clean_bins", "🗑️ Bins Clean"),
                            Pair("clean_road", "🛣️ Road Clear"),
                            Pair("clean_pipe", "💧 Pipe Sealed"),
                            Pair("clean_tree", "🌱 Green Restored")
                        )
                        scenes.forEach { (key, display) ->
                            val isSel = selectedProofImage == key
                            Card(
                                modifier = Modifier.clickable { selectedProofImage = key },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSel) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF2E7D32) else Color.Transparent)
                            ) {
                                Text(
                                    text = display,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSel) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = usernameField,
                        onValueChange = { usernameField = it },
                        placeholder = { Text("Your name (e.g. @me)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(50.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(if (isStatusProof) "Verify cleanup details..." else "Add helpful comments...") },
                        modifier = Modifier.weight(1f).height(50.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                val name = if (usernameField.isBlank()) "@me" else if (usernameField.startsWith("@")) usernameField else "@$usernameField"
                                viewModel.addComment(
                                    reportId = reportId,
                                    username = name,
                                    commentText = commentText,
                                    statusProofImage = if (isStatusProof) selectedProofImage else "",
                                    isStatusProof = isStatusProof,
                                    onFinished = {
                                        commentText = ""
                                        isStatusProof = false // Reset checkbox
                                    }
                                )
                                // If the issue wasn't resolved, optionally help advance status or keep as is!
                                if (!isReel && isStatusProof && report != null && report.status.lowercase() != "resolved") {
                                    viewModel.updateReportStatus(reportId, "Resolved")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStatusProof) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text(if (isStatusProof) "Post Proof" else "Post")
                    }
                }
            }
        }
    }
}

@Composable
fun CivicReelsScreen(
    viewModel: CivicViewModel,
    onNavigateToMap: (Double, Double) -> Unit,
    onSelectThread: (Int) -> Unit
) {
    val reels by viewModel.reels.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showRecordDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0C)) // Deep dark immersive cinematic feel
    ) {
        if (reels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "No reels icon",
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    )
                    Text(
                        text = "No Environmental Reels Yet",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Be the first to record a brief video, highlight a local mess, and recruit volunteer ploggers or clean-up squads to improve the area!",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { showRecordDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Record First Reel")
                    }
                }
            }
        } else {
            // Immersive Reels layout
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    // Header banner explaining the section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(10.dp)
                                                                        .background(Color.Red, CircleShape)
                                                                )
                                                                Text(
                                                                    text = "LIVE ENVIRONMENTAL ACTION DRIVES",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                                                    color = Color.Red
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = "Citizen Plogging & Cleanups",
                                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "Ploggers, activists, and neighbor groups: watch the quick loop reports and claim cleanup sessions directly!",
                                                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                                                            )
                                                        }
                                                    }
                                                }

                items(reels, key = { it.id }) { reel ->
                    ReelItemCard(
                        reel = reel,
                        onLikeChange = { viewModel.toggleReelLike(reel.id, reel.likesCount, reel.hasLiked) },
                        onJoinChange = { viewModel.toggleReelJoin(reel.id, reel.joinedCount, reel.hasJoined) },
                        onNavigateToMap = onNavigateToMap,
                        onDelete = { viewModel.deleteReel(reel.id) },
                        onSelectThread = onSelectThread
                    )
                }
            }

            // Floating action button inside the reels list for high visibility
            FloatingActionButton(
                onClick = { showRecordDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("record_reel_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = "Record Reel")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Reel", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        if (showRecordDialog) {
            RecordReelDialog(
                onDismiss = { showRecordDialog = false },
                onSubmit = { desc, lat, lon, date, limit, vibe ->
                    viewModel.submitReel(desc, lat, lon, date, limit, vibe)
                    showRecordDialog = false
                    android.widget.Toast.makeText(context, "Reel created! Group mobilization is active.", android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

@Composable
fun ReelItemCard(
    reel: com.example.data.CivicReel,
    onLikeChange: () -> Unit,
    onJoinChange: () -> Unit,
    onNavigateToMap: (Double, Double) -> Unit,
    onDelete: () -> Unit,
    onSelectThread: (Int) -> Unit
) {
    val context = LocalContext.current
    var isLikedAnim by remember { mutableStateOf(false) }

    // Map video templates to beautiful drawings or pictures
    val coverResId = when (reel.videoTemplateKey) {
        "waste", "trash" -> R.drawable.civic_preset_waste_1779646341257
        "pothole" -> R.drawable.civic_preset_pothole_1779646358062
        "water" -> R.drawable.civic_preset_water_1779646376881
        "tree" -> R.drawable.civic_preset_tree_1779646395038
        else -> R.drawable.civic_preset_waste_1779646341257
    }

    // Playback video progress indicator
    val infiniteTransition = rememberInfiniteTransition(label = "reel_playback_trans")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reel_playback"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            .padding(12.dp)
            .testTag("reel_card_${reel.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background visual frame (our lovely graphic illustration)
            Image(
                painter = painterResource(id = coverResId),
                contentDescription = "Background frame",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .align(Alignment.Center)
            )

            // Dark overlay for tiktok cinema style contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f,
                            endY = 1500f
                        )
                    )
            )

            // Playback animation scanner effect: horizontal laser scanning slightly down
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (480 * sweepProgress).dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF00FF87).copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )

            // Top Status Bar: ACTIVE PROJECT or Delete
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00FF87).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF00FF87), CircleShape)
                        )
                        Text(
                            text = "ACTIVE PROJECT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                            color = Color(0xFF00FF87)
                        )
                    }
                }

                if (reel.postedBy == "Me" || reel.postedBy == "@me_active") {
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete my Reel",
                            tint = Color.Red
                        )
                    }
                }
            }

            // Right side overlay floating action buttons (Likes, Mobilizations, Share)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp, bottom = 48.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Like Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            isLikedAnim = true
                            onLikeChange()
                        }
                    ) {
                        Icon(
                            imageVector = if (reel.hasLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (reel.hasLiked) Color(0xFFFF2D55) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = reel.likesCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    )
                }

                // Mobilizers Joined Count Column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onJoinChange
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Mobilized Ploggers",
                            tint = if (reel.hasJoined) Color(0xFF34C759) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "${reel.joinedCount}/${reel.maxParticipants}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        textAlign = TextAlign.Center
                    )
                }

                // Group Chat Column
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            if (reel.hasJoined) {
                                onSelectThread(-reel.id)
                            } else {
                                android.widget.Toast.makeText(context, "Join the group first to access the chat!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat",
                            tint = if (reel.hasJoined) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Talk",
                        color = if (reel.hasJoined) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    )
                }

                // Share Button (copy link notification)
                IconButton(
                    onClick = {
                        android.widget.Toast.makeText(
                            context,
                            "Mobilization link copied to clipboard! Share details in your local plogging chat.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom info overlay (description, location tag, date, join CTA button)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // User & Title details
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = reel.postedBy,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = reel.description,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Location tag row
                Row(
                    modifier = Modifier
                        .clickable { onNavigateToMap(reel.latitude, reel.longitude) }
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "pinned coordinate",
                        tint = Color(0xFF2F80ED),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = getWardName(reel.latitude, reel.longitude),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = "open details",
                        tint = Color.LightGray,
                        modifier = Modifier.size(10.dp)
                    )
                }

                // Collaborative cleanup scheduling / plogging activity details card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF242424).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🗓 TARGET CLEANUP DATE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = Color.Gray, fontSize = 8.sp, letterSpacing = 0.5.sp)
                            )
                            Text(
                                text = reel.mobilizeDate,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            Text(
                                text = "${reel.maxParticipants - reel.joinedCount} open spots left",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, color = if (reel.joinedCount >= reel.maxParticipants) Color.Red else Color.Green, fontSize = 9.sp)
                            )
                        }

                        // Register / Join button
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = onJoinChange,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (reel.hasJoined) Color(0xFF34C759) else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (reel.hasJoined) Icons.Default.Check else Icons.Default.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (reel.hasJoined) "Joined!" else "Join Group",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                )
                            }

                            if (reel.hasJoined) {
                                Button(
                                    onClick = { onSelectThread(-reel.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2196F3)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Chat with group",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Talk 💬",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Loop track timeline bar at the very bottom
            LinearProgressIndicator(
                progress = { sweepProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = Color(0xFF00FF87),
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
fun RecordReelDialog(
    onDismiss: () -> Unit,
    onSubmit: (description: String, latitude: Double, longitude: Double, mobilizeDate: String, maxVolunteers: Int, templateKey: String) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var mobilizeDate by remember { mutableStateOf("Saturday, Jun 20 @ 8:00 AM") }
    var maxVolunteers by remember { mutableStateOf(15f) }
    var selectedVibe by remember { mutableStateOf("waste") } // "waste", "pothole", "water", "tree"

    // Ward Selection: We present 4 local wards
    val wardsList = listOf(
        Triple("Vasanth Nagar (Ward 93)", 12.9725, 77.5946),
        Triple("Shivajinagar (Ward 92)", 12.9695, 77.5946),
        Triple("Shanthala Nagar (Ward 111)", 12.9740, 77.5946),
        Triple("Sampangiram Nagar (Ward 77)", 12.9700, 77.5946)
    )
    var selectedWardIndex by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "🎥 CAPTURE & MOBILIZE REEL",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Broadcast a short loop video of a local mess/hazard to environmental groups & plogger networks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // Topic picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "VIBE / TOPIC OVERLAY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val vibes = listOf(
                            "waste" to "🗑️ Waste",
                            "pothole" to "🕳️ Pothole",
                            "water" to "💧 Pipemess",
                            "tree" to "🌳 Branch"
                        )
                        vibes.forEach { (vkey, label) ->
                            val isSel = selectedVibe == vkey
                            Card(
                                onClick = { selectedVibe = vkey },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp))
                                }
                            }
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Video Description & Call-to-Action") },
                    placeholder = { Text("e.g. Garbage bins blocked! Let's plog this route clean this Saturday.") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 3
                )

                // Ward Geolocation Spinner / Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TARGET GEOLOCATION WARD",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        wardsList.forEachIndexed { idx, ward ->
                            val isSel = selectedWardIndex == idx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedWardIndex = idx }
                                    .background(
                                        if (isSel) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (isSel) MaterialTheme.colorScheme.secondary else Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = ward.first,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "selected",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Mobilization Date text field
                OutlinedTextField(
                    value = mobilizeDate,
                    onValueChange = { mobilizeDate = it },
                    label = { Text("Group Cleanup Date & Time") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                    singleLine = true
                )

                // Volunteers max limit slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MAX TEAM SIZE (ACTIVISTS)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "${maxVolunteers.toInt()} people",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = maxVolunteers,
                        onValueChange = { maxVolunteers = it },
                        valueRange = 5f..40f,
                        steps = 6
                    )
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (desc.isNotBlank()) {
                                val selectedWard = wardsList[selectedWardIndex]
                                onSubmit(desc, selectedWard.second, selectedWard.third, mobilizeDate, maxVolunteers.toInt(), selectedVibe)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Capture & Stream")
                    }
                }
            }
        }
    }
}
