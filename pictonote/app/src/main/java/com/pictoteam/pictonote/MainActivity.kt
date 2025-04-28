package com.pictoteam.pictonote

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pictoteam.pictonote.composables.SettingsScreen
import com.pictoteam.pictonote.model.GeminiViewModel
import com.pictoteam.pictonote.ui.theme.PictoNoteTheme
import com.pictoteam.pictonote.model.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

const val JOURNAL_DIR = "journal_entries"
val filenameDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

// main activity: entry point of the app
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // get app settings from the view model
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()

            // apply theme and launch main ui layout
            PictoNoteTheme(
                darkTheme = settings.isDarkMode,
                baseFontSize = settings.baseFontSize
            ) {
                PictoNoteApp()
            }
        }
    }
}

// main ui layout that handles overall scaffold and navigation
@Composable
fun PictoNoteApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        // apply scaffold padding to the content
        Box(modifier = Modifier.padding(paddingValues)) {
            NavigationGraph(navController = navController)
        }
    }
}

// bottom navigation bar to switch between screens
@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
            label = { Text("Archive") },
            selected = currentRoute == "archive",
            onClick = { navigateTo(navController, "archive") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") },
            selected = currentRoute == "home",
            onClick = { navigateTo(navController, "home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
            label = { Text("Journal") },
            selected = currentRoute == "journal",
            onClick = { navigateTo(navController, "journal") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = currentRoute == "settings",
            onClick = { navigateTo(navController, "settings") }
        )
    }
}

// helper to navigate between screens
private fun navigateTo(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

// navigation graph defining routes and corresponding screens
@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("archive") { ArchiveScreen() }
        composable("journal") { JournalScreen() }
        composable("settings") { SettingsScreen() }
    }
}

// home screen ui layout: adapts to screen size and orientation
@Composable
fun HomeScreen() {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation

    val commonCardModifier = Modifier.padding(vertical = 8.dp)
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp
    val landscapeHorizontalPadding = if (orientation == Configuration.ORIENTATION_LANDSCAPE) screenPadding + 8.dp else screenPadding

    when {
        // tablet landscape layout: two columns
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Column(modifier = Modifier.padding(screenPadding)) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = landscapeHorizontalPadding, vertical = screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        // show streak card with full width
                        StreakCard(modifier = commonCardModifier.fillMaxWidth(), screenWidthDp = screenWidthDp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // extra space for alignment in tablet layout
                        Spacer(modifier = Modifier.height(16.dp))
                        RemindersCard(modifier = commonCardModifier.fillMaxWidth())
                    }
                }
            }

        }
        // tablet portrait layout: column layout
        screenWidthDp >= 600 -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenPadding, vertical = screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(20.dp))
                StreakCard(modifier = commonCardModifier.fillMaxWidth(), screenWidthDp = screenWidthDp)
                Spacer(modifier = Modifier.height(16.dp))
                RemindersCard(modifier = commonCardModifier.fillMaxWidth())
            }
        }
        // phone layout: column layout with constrained width
        else -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(screenPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                StreakCard(modifier = commonCardModifier.fillMaxWidth(0.9f), screenWidthDp = screenWidthDp)
                RemindersCard(modifier = commonCardModifier.fillMaxWidth(0.9f))
            }
        }
    }
}

// streak card: shows weekly progress
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

            // display days of week with completion status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                // Placeholder - Replace with logic checking actual entries for the past week
                val completedDaysIndices = remember { getDaysWithEntriesForPastWeek(context) }

                daysOfWeek.forEachIndexed { index, day ->
                    val isCompleted = completedDaysIndices.contains(index) // Simple check for demo
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

fun getDaysWithEntriesForPastWeek(context: Context): Set<Int> {
    Log.w("StreakCard", "getDaysWithEntriesForPastWeek needs full implementation")
    val today = LocalDate.now()
    val pastWeekIndices = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)

    if (!directory.exists()) return emptySet()

    for (i in 0..6) {
        val date = today.minusDays(i.toLong())
        val dateString = date.format(filenameDateFormatter)
        val filename = "journal_$dateString.txt"
        if (File(directory, filename).exists()) {

            val dayOfWeekIndex = (date.dayOfWeek.value % 7) // Sunday (7) -> 0, Monday (1) -> 1 ... Saturday (6) -> 6
            pastWeekIndices.add(dayOfWeekIndex)
        }
    }
    Log.d("StreakCard","Past week indices with entries: $pastWeekIndices")
    return pastWeekIndices
}


// reminders card: shows notification setup
@Composable
fun RemindersCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Reminders", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            // simple button for push notifications setup
            Button(onClick = { /* TODO: Implement notification setup navigation/logic */ }) {
                Text("Set Up Push Notifications")
            }
        }
    }
}

// reads journal entries directory and returns set of days with entries for the given month/year
fun getDaysWithEntriesForMonth(context: Context, year: Int, month: Int): Set<Int> {
    val days = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) {
        return emptySet()
    }

    val monthPrefix = String.format("%d-%02d", year, month) // e.g., "2024-07"

    directory.listFiles()?.forEach { file ->
        // Check filename format: journal_YYYY-MM-DD.txt
        if (file.isFile && file.name.startsWith("journal_$monthPrefix") && file.name.endsWith(".txt")) {
            try {
                // Extract date part: journal_yyyy-MM-dd.txt -> yyyy-MM-dd
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

// reads the content of the journal entry for a specific date
fun readJournalEntryForDate(context: Context, year: Int, month: Int, day: Int): String {
    val directory = File(context.filesDir, JOURNAL_DIR)
    val dateString = String.format("%d-%02d-%02d", year, month, day)
    val filename = "journal_$dateString.txt"
    val entryFile = File(directory, filename)

    return try {
        if (entryFile.exists() && entryFile.isFile) {
            entryFile.readText()
        } else {
            "No entry found for this date." // Or return null if preferred
        }
    } catch (e: FileNotFoundException) {
        Log.e("ReadJournalEntry", "File not found: ${entryFile.absolutePath}", e)
        "Error reading entry: File not found."
    } catch (e: IOException) {
        Log.e("ReadJournalEntry", "IOException reading entry: ${entryFile.absolutePath}", e)
        "Error reading entry: IO Exception."
    } catch (e: Exception) {
        Log.e("ReadJournalEntry", "Unexpected error reading entry: ${entryFile.absolutePath}", e)
        "An unexpected error occurred."
    }
}

// --- New Helper to read TODAY's entry ---
// reads the content of today's journal entry, returns null if not found or error
suspend fun readTodaysJournalEntry(context: Context): String? {
    return withContext(Dispatchers.IO) { // Perform file IO off the main thread
        val directory = File(context.filesDir, JOURNAL_DIR)
        val todayDateString = LocalDate.now().format(filenameDateFormatter)
        val filename = "journal_$todayDateString.txt"
        val entryFile = File(directory, filename)

        try {
            if (entryFile.exists() && entryFile.isFile) {
                Log.d("ReadToday", "Found today's entry: $filename")
                entryFile.readText()
            } else {
                Log.d("ReadToday", "No entry file found for today: $filename")
                null // No entry found for today
            }
        } catch (e: Exception) {
            Log.e("ReadToday", "Error reading today's journal entry: ${entryFile.absolutePath}", e)
            null // Return null on error
        }
    }
}


// archive screen layout: calendar and memories
@Composable
fun ArchiveScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation

    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    val currentYear = YearMonth.now().year // Keep focus on current year for simplicity
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

    LaunchedEffect(selectedMonth) {
        selectedDay = null // Reset day when month changes
    }

    when {
        // tablet landscape: calendar and memories side by side
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenPadding + 8.dp, vertical = screenPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
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
                Column(modifier = Modifier.weight(1f).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    MemoriesCard(
                        modifier = Modifier.fillMaxSize()
                            .padding(top = (MaterialTheme.typography.headlineMedium.fontSize.value.dp + 16.dp)),
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
                modifier = Modifier.fillMaxSize().padding(screenPadding),
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

// month selector row: shows buttons for each month
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
            val monthName = YearMonth.of(currentYear, monthValue).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Button(
                onClick = { onMonthSelected(monthValue) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMonth == monthValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedMonth == monthValue) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(monthName)
            }
        }
    }
}

// calendar grid: displays days in the selected month
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
                    containerColor = if (hasEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
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


// memories card: displays entry for the selected date
@Composable
fun MemoriesCard(
    modifier: Modifier = Modifier,
    context: Context,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int?
) {
    val journalEntry by remember(selectedYear, selectedMonth, selectedDay) {
        derivedStateOf {
            selectedDay?.let { day ->
                readJournalEntryForDate(context, selectedYear, selectedMonth, day)
            }
        }
    }

    Card(modifier = modifier) {
        // Make the content area scrollable if entry is long
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState), // Add scrolling
            contentAlignment = if (journalEntry == null) Alignment.Center else Alignment.TopStart // Align text top-start
        ) {
            if (journalEntry != null) {
                Text(
                    text = journalEntry!!,
                    style = MaterialTheme.typography.bodyLarge
                    // Removed modifier = Modifier.fillMaxSize() to allow scrolling
                )
            } else {
                Text(
                    "Select a highlighted date to view the memory.",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


// journal screen layout: input area and ai suggestions
@Composable
fun JournalScreen(geminiViewModel: GeminiViewModel = viewModel()) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation
    var text by remember { mutableStateOf("") }
    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("click 'prompt' for a suggestion.")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)
    val reflectionResult by geminiViewModel.journalReflection.observeAsState("")
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

    LaunchedEffect(Unit) {
        Log.d("JournalScreen", "LaunchedEffect running to load today's entry")
        val loadedEntry = readTodaysJournalEntry(context)
        if (loadedEntry != null) {
            text = loadedEntry
            Log.d("JournalScreen", "Loaded today's entry.")
        } else {
            Log.d("JournalScreen", "No entry found for today or error reading.")
        }
    }

    // --- Journal Entry Input: BasicTextField should fill available height ---
    @Composable
    fun JournalEntryInput(modifier: Modifier = Modifier, showTitle: Boolean = true) {
        Column(modifier = modifier) { // Takes modifier from parent (which includes weight)
            if (showTitle) {
                Text("Journal Entry", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    // CHANGE: Use fillMaxSize() to occupy the weighted space given to JournalEntryInput
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("What's on your mind?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    }
                    innerTextField()
                }
            )
        }
    }

    // --- AI Assistance: Added internal scrolling ---
    @Composable
    fun AiAssistanceAndControls(modifier: Modifier = Modifier) {
        // This outer Column takes the modifier passed from the parent layout
        Column(modifier = modifier) {
            // Make the content *inside* this section scrollable if needed
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Text("Suggestion:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                SuggestionCard(promptSuggestion, isLoadingPrompt)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Reflection:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                ReflectionCard(text, reflectionResult, isLoadingReflection)
                Spacer(modifier = Modifier.height(24.dp)) // Space before buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { geminiViewModel.suggestJournalPrompt() },
                        enabled = !isLoadingPrompt && !isLoadingReflection
                    ) { Text("Prompt") }
                    Button(
                        onClick = { geminiViewModel.reflectOnJournalEntry(text) },
                        enabled = !isLoadingReflection && !isLoadingPrompt && text.isNotBlank()
                    ) { Text("Reflection") }
                }
                Spacer(modifier = Modifier.height(16.dp)) // Space before Finish Entry
                Button(
                    onClick = { saveLocalJournalEntry(context, text) },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isLoadingPrompt && !isLoadingReflection
                ) { Text("Finish Entry") }
                // Optional: Add a little space at the very bottom inside the scrollable area
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Layout logic based on screen size/orientation
    when {
        // tablet landscape: two-pane layout (Keep as is)
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenPadding + 8.dp, vertical = screenPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Journal input takes more weighted space
                JournalEntryInput(modifier = Modifier.weight(1.8f).fillMaxHeight(), showTitle = true)
                // AI controls take less weighted space
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Add padding/spacer to align top content if needed, like before
                    Spacer(modifier = Modifier.height( MaterialTheme.typography.headlineMedium.fontSize.value.dp + 8.dp))
                    // AI controls fill the height allocated by weight, internal scrolling handles overflow if any
                    AiAssistanceAndControls(modifier = Modifier.fillMaxHeight())
                }
            }
        }

        // --- REVISED: default layout (tablet portrait, phone) ---
        else -> {
            // Use a Column that fills the whole screen (respecting scaffold padding)
            Column(
                modifier = Modifier
                    .fillMaxSize() // Fill available space after Scaffold padding
                    .padding(screenPadding) // Apply overall padding
            ) {
                // Journal Entry takes most of the space, weight(1f) makes it flexible
                JournalEntryInput(
                    modifier = Modifier
                        .weight(1f) // Takes up available vertical space
                        .fillMaxWidth(),
                    showTitle = true
                )
                Spacer(modifier = Modifier.height(16.dp)) // Space between sections
                // AI Controls take the remaining space. Scrolling is handled *inside* it.
                AiAssistanceAndControls(
                    modifier = Modifier.fillMaxWidth() // Takes needed vertical space
                )
            }
        }
    }
}


// suggestion card: shows prompt suggestion text
@Composable
fun SuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = promptSuggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (isLoadingPrompt) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

// reflection card: shows reflection result or loading text
@Composable
fun ReflectionCard(entryText: String, reflectionResult: String, isLoadingReflection: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isLoadingReflection -> "Generating reflection..."
                    reflectionResult.isBlank() && entryText.isNotBlank() -> "Click 'reflection' for insights."
                    reflectionResult.isBlank() && entryText.isBlank() -> "Write an entry first."
                    else -> reflectionResult
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (isLoadingReflection) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}