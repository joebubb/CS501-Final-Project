// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/HomeScreen.kt
package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState // Add scroll state import
import androidx.compose.foundation.shape.CircleShape // Import CircleShape
import androidx.compose.foundation.verticalScroll // Add vertical scroll import
import androidx.compose.material.icons.Icons // Import Icons
import androidx.compose.material.icons.filled.Refresh // Import Refresh icon
import androidx.compose.material3.*
import androidx.compose.runtime.* // Keep existing runtime imports
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // Import clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight // Import FontWeight
import androidx.compose.ui.text.style.TextAlign // Import TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Import sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import com.pictoteam.pictonote.constants.* // Import constants
import com.pictoteam.pictonote.model.GeminiViewModel // Import GeminiViewModel
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.launch // Import launch coroutine builder
import kotlinx.coroutines.withContext // Import withContext
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek // Add this import
import java.time.format.TextStyle // Add this import
import java.util.Locale // Add this import


// --- Helper Functions for Streak ---

// Internal helper copied/adapted from ArchiveScreen to read single file content (for summary)
private fun readJournalEntryTextFromFileInternal(entryFile: File): String? {
    var textContent: String? = null
    if (!entryFile.exists() || !entryFile.canRead()) {
        Log.e("ReadFileInternalText", "File does not exist or cannot be read: ${entryFile.path}") // Unique tag
        return null
    }
    try {
        val lines = entryFile.readLines()
        // Join all lines *except* the image marker line
        textContent = lines.filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n").trim()
    } catch (e: Exception) {
        Log.e("ReadFileInternalText", "Error reading file content: ${entryFile.path}", e) // Unique tag
        return null // Return null on error
    }
    return textContent
}

// Function to get text for a date range (for summary)
suspend fun getJournalTextForDateRange(context: Context, startDate: LocalDate, endDate: LocalDate): String = withContext(Dispatchers.IO) {
    val allText = StringBuilder()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) {
        Log.w("GetTextForRange", "Journal directory not found: ${directory.absolutePath}")
        return@withContext ""
    }

    var currentDate = startDate
    while (!currentDate.isAfter(endDate)) {
        val dateString = currentDate.format(filenameDateFormatter)
        val filePrefix = "journal_$dateString"

        directory.listFiles { file ->
            file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")
        }?.sortedBy { it.name }?.forEach { file ->
            val entryText = readJournalEntryTextFromFileInternal(file)
            if (!entryText.isNullOrBlank()) {
                if (allText.isNotEmpty()) {
                    allText.append("\n\n")
                }
                // Extract timestamp correctly, handling both formats
                val namePart = file.name.substringAfter("journal_")
                val timestampStr = if (namePart.contains("_")) {
                    namePart.substringBefore(".txt") // Includes date and time
                } else {
                    namePart.removeSuffix(".txt") // Just the date
                }
                allText.append("--- Entry from $timestampStr ---")
                allText.append("\n")
                allText.append(entryText)
            }
        }
        currentDate = currentDate.plusDays(1)
    }
    Log.d("GetTextForRange", "Fetched total text length: ${allText.length} for range $startDate to $endDate")
    return@withContext allText.toString()
}


// New function to get the exact dates with entries in the past week
suspend fun getDatesWithEntriesForPastWeek(context: Context): Set<LocalDate> = withContext(Dispatchers.IO) {
    val today = LocalDate.now()
    val datesWithEntries = mutableSetOf<LocalDate>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists()) return@withContext emptySet()

    // Check the last 7 days, including today
    for (i in 0..6) {
        val date = today.minusDays(i.toLong())
        val dateString = date.format(filenameDateFormatter)
        val filePrefix = "journal_$dateString"
        // Check if *any* file exists for that specific date (could have multiple entries per day)
        val hasEntryForDay = directory.listFiles { file ->
            file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")
        }?.any() ?: false // Check if the list is not null and not empty

        if (hasEntryForDay) {
            datesWithEntries.add(date)
        }
    }
    Log.d("StreakLogic", "Dates with entries in past week: $datesWithEntries")
    return@withContext datesWithEntries
}

// New function to calculate the current consecutive streak (Snapchat-style)
fun calculateCurrentStreak(datesWithEntries: Set<LocalDate>): Int {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    // Determine the anchor day for the streak calculation
    val anchorDay = when {
        datesWithEntries.contains(today) -> today       // If entry today, anchor is today
        datesWithEntries.contains(yesterday) -> yesterday // If no entry today but entry yesterday, anchor is yesterday
        else -> null                                      // Otherwise, no anchor, streak is 0
    }

    // If no entry today or yesterday, the streak is broken
    if (anchorDay == null) {
        Log.d("StreakLogic", "No entry today or yesterday. Streak is 0.")
        return 0
    }

    // Start counting from the anchor day
    var currentStreak = 1 // Start with 1 for the anchor day
    var previousDayToCheck = anchorDay.minusDays(1)

    // Count backwards consecutively
    while (datesWithEntries.contains(previousDayToCheck)) {
        currentStreak++
        previousDayToCheck = previousDayToCheck.minusDays(1)
    }

    Log.d("StreakLogic", "Calculated streak: $currentStreak (Based on last entry: $anchorDay)")
    return currentStreak
}


// --- HomeScreen Composable ---

@Composable
fun HomeScreen(geminiViewModel: GeminiViewModel = viewModel()) { // Inject ViewModel
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation
    val commonCardModifier = Modifier.padding(vertical = 8.dp)
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp
    val topLevelModifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = screenPadding)
    val contentModifier = topLevelModifier.padding(vertical = screenPadding)

    // State for triggering refresh of summary AND streak
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Coroutine scope for manual refresh
    val coroutineScope = rememberCoroutineScope()

    // Effect to load summary on initial composition or when refreshTrigger changes
    LaunchedEffect(key1 = refreshTrigger) { // Re-run when refreshTrigger changes
        Log.d("HomeScreen", "LaunchedEffect running for weekly summary (Trigger: $refreshTrigger)")
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(6) // Get entries from last 7 days including today
        val fetchedText = getJournalTextForDateRange(context, startDate, endDate)
        geminiViewModel.generateWeeklySummary(fetchedText)
    }

    // State for streak data, recalculated when refreshTrigger changes
    val datesWithEntries by produceState<Set<LocalDate>>(initialValue = emptySet(), context, refreshTrigger) {
        value = getDatesWithEntriesForPastWeek(context)
    }
    val currentStreak by remember(datesWithEntries) {
        // Use the updated calculation logic here
        derivedStateOf { calculateCurrentStreak(datesWithEntries) }
    }


    when {
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Column(modifier = contentModifier) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxSize().padding(vertical = 0.dp), Arrangement.spacedBy(24.dp), Alignment.Top) {
                    // Left Column
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        StreakCard(
                            modifier = commonCardModifier.fillMaxWidth(),
                            datesWithEntries = datesWithEntries,
                            currentStreak = currentStreak
                        )
                        WeeklySummaryCard(
                            modifier = commonCardModifier.fillMaxWidth(),
                            geminiViewModel = geminiViewModel,
                            onRefresh = {
                                coroutineScope.launch {
                                    Log.d("HomeScreen", "Manual refresh triggered")
                                    refreshTrigger++
                                }
                            }
                        )
                    }
                    // Right Column
                    Column(Modifier.weight(1f)) {
                        RemindersCard(modifier = commonCardModifier.fillMaxWidth())
                    }
                }
            }
        }
        else -> { // Portrait or smaller screens
            Column(
                contentModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))

                val cardWidthModifier = Modifier.fillMaxWidth(if (screenWidthDp < 600) 0.95f else 1f)

                StreakCard(
                    modifier = commonCardModifier.then(cardWidthModifier),
                    datesWithEntries = datesWithEntries,
                    currentStreak = currentStreak
                )
                RemindersCard(commonCardModifier.then(cardWidthModifier))
                WeeklySummaryCard(
                    modifier = commonCardModifier.then(cardWidthModifier),
                    geminiViewModel = geminiViewModel,
                    onRefresh = {
                        coroutineScope.launch {
                            Log.d("HomeScreen", "Manual refresh triggered")
                            refreshTrigger++
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// --- Streak Card Composable (Refactored with new streak display logic) ---
@Composable
fun StreakCard(
    modifier: Modifier = Modifier,
    datesWithEntries: Set<LocalDate>, // Receive pre-calculated data
    currentStreak: Int // Receive pre-calculated data (now using the new logic)
) {
    val today = LocalDate.now()
    val pastWeekDates = remember(today) {
        List(7) { i -> today.minusDays((6 - i).toLong()) }
    }

    Card(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Streak", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))

            // --- Display the streak number (or 0 if no streak) ---
            val streakNumberText = if (currentStreak > 0) {
                "$currentStreak Day${if (currentStreak != 1) "s" else ""}"
            } else {
                "0 Days" // Simple display for no streak
            }
            // Color is primary if streak > 0, otherwise muted
            val streakColor = if (currentStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = streakNumberText,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = streakColor
                )
                // Show fire emoji only if there's an active streak > 0
                if(currentStreak > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("🔥", style = MaterialTheme.typography.titleLarge)
                }
            }
            // --- End streak number display ---

            Spacer(Modifier.height(16.dp))

            // --- Row for the day indicators ---
            // This part visually represents whether an entry exists for each specific day
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pastWeekDates.forEach { date ->
                    val dayInitial = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).first()
                    val isToday = date.isEqual(today)
                    // Check if an entry *actually* exists for this specific date
                    val hasEntry = datesWithEntries.contains(date)

                    // Determine colors based on whether the entry for *that specific day* was made
                    val (backgroundColor, textColor, borderColor) = when {
                        // Today with an entry: Filled and highlighted
                        isToday && hasEntry -> Triple(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimary,
                            MaterialTheme.colorScheme.primary
                        )
                        // Today *without* an entry: Distinctly outlined/colored to show it's missing
                        isToday && !hasEntry -> Triple(
                            MaterialTheme.colorScheme.surfaceVariant, // Use a distinct background
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.secondary // Use a distinct border
                        )
                        // Past day with an entry: Filled but muted
                        !isToday && hasEntry -> Triple(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            Color.Transparent
                        )
                        // Past day without an entry: Muted empty state
                        else -> Triple(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    }

                    val boxSize = 40.dp

                    Box(
                        modifier = Modifier
                            .size(boxSize)
                            .clip(CircleShape)
                            .border(
                                width = if (isToday) 2.dp else 1.dp, // Thicker border for today
                                color = if (isToday) borderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                            .background(backgroundColor)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$dayInitial",
                            color = textColor,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            // --- End day indicators ---
        }
    }
}


// --- Reminders Card (No Changes) ---
@Composable
fun RemindersCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("Reminders", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { /* TODO: Notification setup */ }) {
                Text("Set Up Push Notifications")
            }
        }
    }
}

// --- Weekly Summary Card (No Changes) ---
@Composable
fun WeeklySummaryCard(
    modifier: Modifier = Modifier,
    geminiViewModel: GeminiViewModel,
    onRefresh: () -> Unit // Callback to trigger refresh
) {
    val summary by geminiViewModel.weeklySummary.collectAsStateWithLifecycle()
    val isLoading by geminiViewModel.isWeeklySummaryLoading.collectAsStateWithLifecycle()

    Card(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Weekly AI Summary",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Summary"
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = summary ?: "Summary not available.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}