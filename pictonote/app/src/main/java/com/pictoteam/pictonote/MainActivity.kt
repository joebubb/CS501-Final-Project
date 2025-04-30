package com.pictoteam.pictonote

import android.Manifest // Ensure Manifest import is present
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log // Import Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme // Import for dark theme check
import androidx.compose.foundation.layout.* // Includes WindowInsets, padding, etc.
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Import all filled icons used
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Import Color for transparent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat // Import WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.rememberAsyncImagePainter // Make sure Coil import is correct (might be coil3.compose)
import com.google.accompanist.systemuicontroller.rememberSystemUiController // Import Accompanist
import com.pictoteam.pictonote.composables.SettingsScreen // Ensure SettingsScreen is imported if used directly
import com.pictoteam.pictonote.model.GeminiViewModel
import com.pictoteam.pictonote.model.IMAGE_URI_MARKER
import com.pictoteam.pictonote.model.JournalViewModel
import com.pictoteam.pictonote.model.SettingsViewModel
import com.pictoteam.pictonote.ui.theme.PictoNoteTheme
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import kotlin.math.roundToInt


const val JOURNAL_DIR = "journal_entries"
val filenameDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // *** Enable Edge-to-Edge display ***
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()

            PictoNoteTheme(
                darkTheme = settings.isDarkMode,
                baseFontSize = settings.baseFontSize
            ) {
                // Apply edge-to-edge adjustments globally here
                ApplyEdgeToEdge()
                // Render the main app structure
                PictoNoteApp()
            }
        }
    }
}

// Helper composable to configure system bars for edge-to-edge
@Composable
private fun ApplyEdgeToEdge() {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme() // Determine icon color based on system theme

    // Apply system bar settings using DisposableEffect
    DisposableEffect(systemUiController, useDarkIcons) {
        systemUiController.setStatusBarColor(
            color = Color.Transparent, // Make status bar transparent
            darkIcons = useDarkIcons   // Set status bar icon colors
        )

        systemUiController.setNavigationBarColor(
            color = Color.Transparent, // Make navigation bar transparent
            darkIcons = useDarkIcons   // Set navigation bar icon colors
        )

        onDispose { } // No specific cleanup needed in this case
    }
}

@Composable
fun PictoNoteApp() {
    val navController = rememberNavController()
    Scaffold(
        // Bottom Navigation Bar remains consistent
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding -> // innerPadding contains safe areas from system bars
        // This allows content like the camera preview to go under the status bar.
        val bottomPadding = innerPadding.calculateBottomPadding()

        Box(modifier = Modifier.padding(bottom = bottomPadding)) {
            // The NavigationGraph content will now respect only the bottom padding.
            // Individual screens need to handle status bar padding if they shouldn't overlap.
            NavigationGraph(navController = navController)
        }
    }
}

// Bottom navigation bar - No changes needed
@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar { // Material 3 NavigationBar
        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountBox, contentDescription = "Archive") },
            label = { Text("Archive") },
            selected = currentRoute == "archive",
            onClick = { navigateTo(navController, "archive") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = currentRoute == "home",
            onClick = { navigateTo(navController, "home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Journal") },
            label = { Text("Journal") },
            selected = currentRoute == "journal",
            onClick = { navigateTo(navController, "journal") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentRoute == "settings",
            onClick = { navigateTo(navController, "settings") }
        )
    }
}

// Navigation helper - No changes needed
private fun navigateTo(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

// Navigation graph - No changes needed
@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("archive") { ArchiveScreen() }
        composable("journal") { JournalScreen() }
        composable("settings") { SettingsScreen() } // Make sure SettingsScreen composable exists
    }
}

// Home screen - Added status bar padding
@Composable
fun HomeScreen() {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation

    val commonCardModifier = Modifier.padding(vertical = 8.dp)
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

    // *** Apply statusBarsPadding() to the top-level layout for this screen ***
    val topLevelModifier = Modifier
        .fillMaxSize()
        .statusBarsPadding() // Ensure content is below the status bar
        .padding(horizontal = screenPadding) // Apply horizontal padding

    // Apply vertical padding after status bar padding
    val contentModifier = topLevelModifier.padding(vertical = screenPadding)

    when {
        // tablet landscape layout
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Column(modifier = contentModifier) { // Use modifier with all padding
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 0.dp), // Row might not need extra vertical padding
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Spacer(modifier = Modifier.height(16.dp)) // Adjust spacing as needed
                        StreakCard(modifier = commonCardModifier.fillMaxWidth(), screenWidthDp = screenWidthDp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Spacer(modifier = Modifier.height(16.dp)) // Adjust spacing as needed
                        RemindersCard(modifier = commonCardModifier.fillMaxWidth())
                    }
                }
            }
        }
        // tablet portrait / phone layout
        else -> {
            Column(
                modifier = contentModifier, // Use modifier with all padding
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                StreakCard(modifier = commonCardModifier.fillMaxWidth(if (screenWidthDp < 600) 0.9f else 1f), screenWidthDp = screenWidthDp)
                RemindersCard(modifier = commonCardModifier.fillMaxWidth(if (screenWidthDp < 600) 0.9f else 1f))
            }
        }
    }
}


// Streak Card - No functional changes needed for edge-to-edge
@Composable
fun StreakCard(
    modifier: Modifier = Modifier,
    screenWidthDp: Int
) {
    val context = LocalContext.current

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Streak",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
            val isTablet = screenWidthDp >= 600
            val boxSize = if (isTablet) 56.dp else 36.dp
            val spacing = if (isTablet) 12.dp else 4.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                val completedDaysIndices = remember { getDaysWithEntriesForPastWeek(context) }

                daysOfWeek.forEachIndexed { index, day ->
                    val isCompleted = completedDaysIndices.contains(index)
                    Box(
                        modifier = Modifier
                            .size(boxSize)
                            .border(
                                1.dp,
                                if (isCompleted) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day,
                            color = if (isCompleted) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            style = if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// Helper function - No changes needed
fun getDaysWithEntriesForPastWeek(context: Context): Set<Int> {
    val today = LocalDate.now()
    val pastWeekIndices = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)

    if (!directory.exists()) return emptySet()

    for (i in 0..6) {
        val date = today.minusDays(i.toLong())
        val dateString = date.format(filenameDateFormatter)
        val filename = "journal_$dateString.txt"
        if (File(directory, filename).exists()) {
            val dayOfWeekIndex = date.dayOfWeek.value % 7
            pastWeekIndices.add(dayOfWeekIndex)
        }
    }
    Log.d("StreakCard","Past week indices with entries: $pastWeekIndices")
    return pastWeekIndices
}

// Reminders Card - No functional changes needed for edge-to-edge
@Composable
fun RemindersCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Reminders", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { /* TODO: Notification setup */ }) {
                Text("Set Up Push Notifications")
            }
        }
    }
}

// Helper function - No changes needed
fun getDaysWithEntriesForMonth(context: Context, year: Int, month: Int): Set<Int> {
    val days = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) return emptySet()

    val monthPrefix = String.format("%d-%02d", year, month)
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith("journal_$monthPrefix") && file.name.endsWith(".txt")) {
            try {
                val dateString = file.name.substringAfter("journal_").substringBefore(".txt")
                val date = LocalDate.parse(dateString, filenameDateFormatter)
                if (date.year == year && date.monthValue == month) {
                    days.add(date.dayOfMonth)
                }
            } catch (e: Exception) {
                Log.e("GetDaysWithEntries", "Error parsing filename: ${file.name}", e)
            }
        }
    }
    Log.d("GetDaysWithEntries", "Found entries for days in $monthPrefix: $days")
    return days
}

// Helper function - No changes needed (reading logic remains the same)
fun readJournalEntryForDate(context: Context, year: Int, month: Int, day: Int): Pair<String?, String?> {
    val directory = File(context.filesDir, JOURNAL_DIR)
    val dateString = String.format("%d-%02d-%02d", year, month, day)
    val filename = "journal_$dateString.txt"
    val entryFile = File(directory, filename)

    var imagePath: String? = null
    var textContent: String? = null

    try {
        if (entryFile.exists() && entryFile.isFile) {
            val fullContent = entryFile.readText()
            val firstLine = fullContent.lines().firstOrNull()
            if (firstLine != null && firstLine.startsWith(IMAGE_URI_MARKER)) {
                imagePath = firstLine.substringAfter(IMAGE_URI_MARKER).trim()
                textContent = fullContent.lines().drop(1).joinToString("\n")
                Log.d("ReadJournalEntry", "Found entry with image: $imagePath")
            } else {
                textContent = fullContent
                Log.d("ReadJournalEntry", "Found entry without image.")
            }
        } else {
            Log.w("ReadJournalEntry", "No entry file found for $dateString")
            return Pair(null, null)
        }
    } catch (e: FileNotFoundException) {
        Log.e("ReadJournalEntry", "File not found: ${entryFile.absolutePath}", e)
        return Pair(null, null)
    }
    catch (e: IOException) {
        Log.e("ReadJournalEntry", "IOException reading entry: ${entryFile.absolutePath}", e)
        return Pair(null, null)
    }
    catch (e: Exception) {
        Log.e("ReadJournalEntry", "Unexpected error reading entry file: ${entryFile.absolutePath}", e)
        return Pair(null, null)
    }
    return Pair(imagePath, textContent)
}


// Archive screen - Added status bar padding
@Composable
fun ArchiveScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation

    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    val currentYear = YearMonth.now().year
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

    // *** Apply statusBarsPadding() to the top-level layout for this screen ***
    val topLevelModifier = Modifier
        .fillMaxSize()
        .statusBarsPadding() // Ensure content is below the status bar
        .padding(horizontal = screenPadding) // Apply horizontal padding

    // Apply vertical padding after status bar padding
    val contentModifier = topLevelModifier.padding(vertical = screenPadding)

    LaunchedEffect(selectedMonth) {
        selectedDay = null // Reset day when month changes
    }

    when {
        // tablet landscape layout
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(
                modifier = contentModifier, // Use modifier with all padding
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Column (Calendar)
                Column(modifier = Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Calendar - $currentYear",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    MonthSelectorRow(currentYear, selectedMonth) { newMonth -> selectedMonth = newMonth }
                    CalendarGrid(
                        context = context,
                        currentYear = currentYear,
                        selectedMonth = selectedMonth,
                        columns = GridCells.Adaptive(minSize = 60.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        onDateSelected = { day -> selectedDay = day }
                    )
                }
                // Right Column (Memories) - Adjust top padding to align visually
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = (MaterialTheme.typography.headlineMedium.fontSize.value.dp + 16.dp)), // Approximate alignment
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MemoriesCard(
                        modifier = Modifier.fillMaxSize(),
                        context = context,
                        selectedYear = currentYear,
                        selectedMonth = selectedMonth,
                        selectedDay = selectedDay
                    )
                }
            }
        }
        // default column layout
        else -> {
            val gridColumns = if (screenWidthDp >= 600) GridCells.Adaptive(minSize = 70.dp) else GridCells.Fixed(7)
            Column(
                modifier = contentModifier, // Use modifier with all padding
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Calendar - $currentYear",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MonthSelectorRow(currentYear, selectedMonth) { newMonth -> selectedMonth = newMonth }
                CalendarGrid(
                    context = context,
                    currentYear = currentYear,
                    selectedMonth = selectedMonth,
                    columns = gridColumns,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    onDateSelected = { day -> selectedDay = day }
                )
                MemoriesCard(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                    context = context,
                    selectedYear = currentYear,
                    selectedMonth = selectedMonth,
                    selectedDay = selectedDay
                )
            }
        }
    }
}

// Month Selector Row - No changes needed
@Composable
fun MonthSelectorRow(
    currentYear: Int,
    selectedMonth: Int,
    onMonthSelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(12) { monthIndex ->
            val monthValue = monthIndex + 1
            val month = YearMonth.of(currentYear, monthValue).month
            val monthName = month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Button(
                onClick = { onMonthSelected(monthValue) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMonth == monthValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedMonth == monthValue) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text(monthName) }
        }
    }
}

// Calendar Grid - No changes needed
@Composable
fun CalendarGrid(
    context: Context,
    currentYear: Int,
    selectedMonth: Int,
    columns: GridCells,
    modifier: Modifier = Modifier,
    onDateSelected: (Int) -> Unit
) {
    val daysWithEntries by remember(currentYear, selectedMonth) {
        mutableStateOf(getDaysWithEntriesForMonth(context, currentYear, selectedMonth))
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        val daysInMonth = YearMonth.of(currentYear, selectedMonth).lengthOfMonth()
        items(daysInMonth) { dayIndex ->
            val dayOfMonth = dayIndex + 1
            val hasEntry = daysWithEntries.contains(dayOfMonth)
            Card(
                modifier = Modifier.aspectRatio(1f),
                onClick = { if (hasEntry) onDateSelected(dayOfMonth) },
                enabled = hasEntry,
                colors = CardDefaults.cardColors(
                    containerColor = if (hasEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "$dayOfMonth",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasEntry) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Memories Card - No changes needed
@Composable
fun MemoriesCard(
    modifier: Modifier = Modifier,
    context: Context,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int?
) {
    val (imagePath, journalText) = remember(selectedYear, selectedMonth, selectedDay) {
        selectedDay?.let { readJournalEntryForDate(context, selectedYear, selectedMonth, it) } ?: Pair(null, null)
    }
    val imageFile: File? = remember(imagePath) {
        imagePath?.takeIf { it.isNotBlank() }?.let { File(context.filesDir, it) }
    }

    Card(modifier = modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            if (selectedDay == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a highlighted date to view the memory.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                }
            } else if (imageFile != null && imageFile.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(imageFile),
                    contentDescription = "Journal image for $selectedMonth/$selectedDay/$selectedYear",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(bottom = 12.dp)
                )
                if (!journalText.isNullOrBlank()) {
                    Text(text = journalText, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("Image captured on this date.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (!journalText.isNullOrBlank()) {
                Text(text = journalText, style = MaterialTheme.typography.bodyLarge)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No content found for this date.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


// --- JournalScreen with Edge-to-Edge Camera and Debugging Logs ---
@Composable
fun JournalScreen(
    journalViewModel: JournalViewModel = viewModel(),
    geminiViewModel: GeminiViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State from ViewModel
    val capturedImageUri by journalViewModel.capturedImageUri.collectAsStateWithLifecycle()
    var journalText by journalViewModel.journalText
    val isSaving by journalViewModel.isSaving.collectAsStateWithLifecycle()

    // AI State
    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("click 'prompt' for a suggestion.")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)
    val reflectionResult by geminiViewModel.journalReflection.observeAsState("")
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)

    // CameraX related state
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }}
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // Permission handling
    var hasCamPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
            if (!granted) Log.w("JournalScreen", "Camera permission denied.")
            else Log.d("JournalScreen", "Camera permission granted.") // Log grant
        }
    )

    // Request permission & load entry on launch
    LaunchedEffect(key1 = Unit) {
        Log.d("JournalScreen", "Initial LaunchedEffect: Requesting permission and loading entry.")
        launcher.launch(Manifest.permission.CAMERA)
        journalViewModel.loadTodaysEntry(context)
    }

    // Effect to get CameraProvider instance ONCE when the composable enters
    LaunchedEffect(key1 = lifecycleOwner) { // Use lifecycleOwner to run once per lifecycle
        Log.d("JournalScreen", "LaunchedEffect to get CameraProvider instance...")
        try {
            val provider = cameraProviderFuture.get() // Attempt to get it synchronously first (might work if already initialized)
            cameraProvider = provider
            Log.d("JournalScreen", "CameraProvider obtained synchronously in LaunchedEffect.")
        } catch (e: Exception) {
            // If sync fails, rely on the listener
            Log.d("JournalScreen", "CameraProvider not ready synchronously, relying on listener.")
            if (cameraProvider == null) { // Add listener only if still null
                cameraProviderFuture.addListener({
                    cameraProvider = try { cameraProviderFuture.get() } catch (e: Exception) { null }
                    if (cameraProvider != null) {
                        Log.d("JournalScreen", "CameraProvider obtained via listener.")
                    } else {
                        Log.e("JournalScreen", "Failed to get CameraProvider from listener.", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }


    // Bind CameraX use cases WHEN provider and permission are ready
    LaunchedEffect(cameraProvider, hasCamPermission) {
        Log.d("JournalScreen", "Binding Effect Check: HasPerm=$hasCamPermission, Provider set=${cameraProvider != null}")
        if (hasCamPermission && cameraProvider != null) {
            try {
                // It's crucial to unbind before binding again
                Log.d("JournalScreen", "Unbinding all previous use cases...")
                cameraProvider?.unbindAll()

                Log.d("JournalScreen", "Binding new use cases...")
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) },
                    imageCapture
                )
                Log.d("JournalScreen", "CameraX successfully bound to lifecycle.")
            } catch (exc: Exception) {
                Log.e("JournalScreen", "Use case binding failed", exc)
            }
        }
    }

    // *** ADD LOGGING HERE TO SEE STATE DURING COMPOSITION ***
    Log.d("JournalScreen", "Composing UI. HasPerm: $hasCamPermission, Provider: $cameraProvider, IsSaving: $isSaving, Button Enabled: ${cameraProvider != null && !isSaving}")

    // Determine screen content
    if (capturedImageUri == null && hasCamPermission) {
        // --- Camera Preview View ---
        Box(modifier = Modifier.fillMaxSize()) { // Fills edge-to-edge
            // Using AndroidView ensures PreviewView lifecycle integration
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Capture Button
            Button(
                onClick = {
                    // Log state again right before attempting the action
                    Log.d("JournalScreen", "Capture button clicked. Provider ready: ${cameraProvider != null}, Not Saving: ${!isSaving}")
                    if (cameraProvider != null && !isSaving) {
                        takePhoto(context, imageCapture, journalViewModel::onImageCaptured)
                    } else {
                        Log.w("JournalScreen", "Button click ignored. Provider: $cameraProvider, Saving: $isSaving")
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding() // Pad for system nav bar
                    .size(72.dp),
                shape = CircleShape,
                // The crucial state check for enabling the button
                enabled = cameraProvider != null && !isSaving
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Take Photo", modifier = Modifier.size(36.dp))
            }
        }
    } else if (capturedImageUri != null) {
        // --- Entry Editing View ---
        // (Rest of the code for the editing view remains the same)
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = screenPadding) // Horizontal padding
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.statusBarsPadding()) // Push content below status bar

            Image(
                painter = rememberAsyncImagePainter(capturedImageUri),
                contentDescription = "Captured journal image",
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(bottom = 16.dp)
            )
            Text("Journal Entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            BasicTextField(
                value = journalText,
                onValueChange = { journalText = it },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    if (journalText.isEmpty()) Text("Add your thoughts...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    innerTextField()
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            // --- AI Assistance ---
            Text("AI Assistance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            SuggestionCard(promptSuggestion, isLoadingPrompt)
            Spacer(modifier = Modifier.height(16.dp))
            ReflectionCard(journalText, reflectionResult, isLoadingReflection)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { geminiViewModel.suggestJournalPrompt() }, enabled = !isLoadingPrompt && !isLoadingReflection) { Text("Prompt") }
                Button(onClick = { geminiViewModel.reflectOnJournalEntry(journalText) }, enabled = !isLoadingReflection && !isLoadingPrompt && journalText.isNotBlank()) { Text("Reflect") }
            }
            Spacer(modifier = Modifier.height(32.dp))
            // --- Save/Discard ---
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { journalViewModel.clearJournalState() }, enabled = !isSaving) {
                    Icon(Icons.Default.Clear, contentDescription = "Discard", modifier = Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Discard")
                }
                Button(onClick = { journalViewModel.saveJournalEntry(context) }, enabled = !isSaving) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Check, contentDescription = "Save Entry", modifier = Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Save Entry") }
                }
            }
        }

    } else {
        // --- Fallback View (Permission Denied or Loading) ---
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (!hasCamPermission) "Camera permission is needed..." else "Initializing Camera...", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                if (!hasCamPermission) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                } else if (cameraProvider == null) { // Show loading indicator only if permission granted but provider not ready
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    // Log that we are waiting
                    Log.d("JournalScreen", "Fallback view: Waiting for cameraProvider.")
                }
            }
        }
    }
}

// Helper function to take photo - No changes needed here (already has logging)
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageSaved: (Uri) -> Unit
) {
    val photoFile = File(context.externalCacheDir ?: context.cacheDir, "JPEG_${System.currentTimeMillis()}.jpg")
    Log.d("TakePhoto", "Saving picture to: ${photoFile.absolutePath}")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("JournalScreen", "Photo capture failed: ${exc.message}", exc)
                // Consider showing a Toast or Snackbar to the user here
            }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                Log.d("JournalScreen", "Photo capture succeeded: $savedUri")
                onImageSaved(savedUri)
            }
        }
    )
}

// Suggestion Card - No changes needed
@Composable
fun SuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = promptSuggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
            if (isLoadingPrompt) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

// Reflection Card - No changes needed
@Composable
fun ReflectionCard(entryText: String, reflectionResult: String, isLoadingReflection: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = when {
                    isLoadingReflection -> "Generating reflection..."
                    reflectionResult.isBlank() && entryText.isNotBlank() -> "Click 'Reflect' for insights."
                    reflectionResult.isBlank() && entryText.isBlank() -> "Write an entry first."
                    else -> reflectionResult
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
            )
            if (isLoadingReflection) CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}