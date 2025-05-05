// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/HomeScreen.kt
package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState // Add scroll state import
import androidx.compose.foundation.verticalScroll // Add vertical scroll import
import androidx.compose.material.icons.Icons // Import Icons
import androidx.compose.material.icons.filled.Refresh // Import Refresh icon
import androidx.compose.material3.*
import androidx.compose.runtime.* // Keep existing runtime imports
import androidx.compose.runtime.saveable.rememberSaveable // Import if needed for complex state saving
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign // Import TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import com.pictoteam.pictonote.constants.* // Import constants
import com.pictoteam.pictonote.model.GeminiViewModel // Import GeminiViewModel
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.launch // Import launch coroutine builder
import kotlinx.coroutines.withContext // Import withContext
import java.io.File
import java.time.LocalDate

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

    // State for triggering refresh
    var refreshTrigger by remember { mutableStateOf(0) } // Use remember, not rememberSaveable unless needed

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

    when {
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Column(modifier = contentModifier) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp)) // Space below title
                Row(Modifier.fillMaxSize().padding(vertical = 0.dp), Arrangement.spacedBy(24.dp), Alignment.Top) {
                    // Left Column
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { // Make left column scrollable
                        StreakCard(modifier = commonCardModifier.fillMaxWidth(), screenWidthDp = screenWidthDp)
                        WeeklySummaryCard( // Add summary card here
                            modifier = commonCardModifier.fillMaxWidth(),
                            geminiViewModel = geminiViewModel,
                            onRefresh = {
                                coroutineScope.launch { // Use coroutine scope for manual refresh
                                    Log.d("HomeScreen", "Manual refresh triggered")
                                    refreshTrigger++ // Increment trigger to re-run LaunchedEffect
                                }
                            }
                        )
                    }
                    // Right Column
                    Column(Modifier.weight(1f)) {
                        RemindersCard(modifier = commonCardModifier.fillMaxWidth())
                        // Can add more cards here if needed
                    }
                }
            }
        }
        else -> { // Portrait or smaller screens
            // Make the whole column scrollable
            Column(
                contentModifier.verticalScroll(rememberScrollState()), // Apply verticalScroll here
                verticalArrangement = Arrangement.spacedBy(16.dp), // Reduced spacing slightly
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp)) // Smaller space

                val cardWidthModifier = Modifier.fillMaxWidth(if (screenWidthDp < 600) 0.95f else 1f) // Slightly wider on small screens

                StreakCard(commonCardModifier.then(cardWidthModifier), screenWidthDp)
                RemindersCard(commonCardModifier.then(cardWidthModifier))
                WeeklySummaryCard( // Add summary card here
                    modifier = commonCardModifier.then(cardWidthModifier),
                    geminiViewModel = geminiViewModel,
                    onRefresh = {
                        coroutineScope.launch { // Use coroutine scope for manual refresh
                            Log.d("HomeScreen", "Manual refresh triggered")
                            refreshTrigger++ // Increment trigger to re-run LaunchedEffect
                        }
                    }
                )
                Spacer(Modifier.height(16.dp)) // Add padding at the bottom inside scrollable area
            }
        }
    }
}

// Keep StreakCard and RemindersCard as they are
@Composable
fun StreakCard(modifier: Modifier = Modifier, screenWidthDp: Int) {
    val context = LocalContext.current
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Streak", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            val isTablet = screenWidthDp >= 600
            val boxSize = if (isTablet) 56.dp else 36.dp
            val spacing = if (isTablet) 12.dp else 4.dp
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(spacing, Alignment.CenterHorizontally), Alignment.CenterVertically) {
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                // Use remember with context as key
                val completedDaysIndices = remember(context) { getDaysWithEntriesForPastWeek(context) }
                daysOfWeek.forEachIndexed { index, day ->
                    val isCompleted = completedDaysIndices.contains(index)
                    Box(Modifier.size(boxSize).border(1.dp, if (isCompleted) MaterialTheme.colorScheme.primary else Color.Gray, MaterialTheme.shapes.medium).padding(4.dp), Alignment.Center) {
                        Text(day, color = if (isCompleted) MaterialTheme.colorScheme.primary else LocalContentColor.current, style = if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

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

// Keep getDaysWithEntriesForPastWeek as is
fun getDaysWithEntriesForPastWeek(context: Context): Set<Int> {
    val today = LocalDate.now()
    val pastWeekIndices = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists()) return emptySet()

    for (i in 0..6) {
        val date = today.minusDays(i.toLong())
        val dateString = date.format(filenameDateFormatter)
        val filePrefix = "journal_$dateString"
        val hasEntryForDay = directory.listFiles()?.any { file ->
            file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")
        } ?: false
        if (hasEntryForDay) {
            // Correct calculation for Sunday=0, Monday=1... Saturday=6
            // dayOfWeek.value returns 1 (Monday) to 7 (Sunday). We want 0-6 starting Sunday.
            val dayOfWeekIndex = (date.dayOfWeek.value % 7) // Sunday (7 % 7 = 0), Monday (1 % 7 = 1), ..., Saturday (6 % 7 = 6)
            pastWeekIndices.add(dayOfWeekIndex)
        }
    }
    Log.d("StreakCard", "Past week indices with entries: $pastWeekIndices")
    return pastWeekIndices
}


// --- NEW: Weekly Summary Card Composable ---
@Composable
fun WeeklySummaryCard(
    modifier: Modifier = Modifier,
    geminiViewModel: GeminiViewModel,
    onRefresh: () -> Unit // Callback to trigger refresh
) {
    // Collect state from ViewModel
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
                // Refresh Button - Enabled only when not loading
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Summary"
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Content Area: Shows loading or summary text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 100.dp), // Ensure card has some height
                contentAlignment = Alignment.Center // Center loading indicator/text
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = summary ?: "Summary not available.", // Display summary or fallback text
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start, // Align text to the start
                        modifier = Modifier.fillMaxWidth() // Allow text to take full width
                    )
                }
            }
        }
    }
}


// --- Helper function definitions ---

// Internal helper copied/adapted from ArchiveScreen to read single file content
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
        // Log.d("ReadFileInternalText", "Read ${entryFile.name}. Text length: ${textContent?.length}") // Optional logging
    } catch (e: Exception) {
        Log.e("ReadFileInternalText", "Error reading file content: ${entryFile.path}", e) // Unique tag
        return null // Return null on error
    }
    return textContent
}

// New function to get text for a date range
suspend fun getJournalTextForDateRange(context: Context, startDate: LocalDate, endDate: LocalDate): String = withContext(Dispatchers.IO) {
    val allText = StringBuilder()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) {
        Log.w("GetTextForRange", "Journal directory not found: ${directory.absolutePath}")
        return@withContext "" // Return empty if directory doesn't exist
    }

    var currentDate = startDate
    while (!currentDate.isAfter(endDate)) {
        val dateString = currentDate.format(filenameDateFormatter)
        val filePrefix = "journal_$dateString"
        // Log.d("GetTextForRange", "Checking for files starting with: $filePrefix") // Optional logging

        directory.listFiles { file ->
            // Check if it's a file, starts with the specific date prefix, and ends with .txt
            file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")
        }?.sortedBy { it.name }?.forEach { file -> // Sort files for consistency (optional)
            // Log.d("GetTextForRange", "Reading file: ${file.name}") // Optional logging
            val entryText = readJournalEntryTextFromFileInternal(file)
            if (!entryText.isNullOrBlank()) {
                // Add separator/date marker only if there's actual text
                if (allText.isNotEmpty()) { // Add separator before the next entry, but not the very first one
                    allText.append("\n\n")
                }
                allText.append("--- Entry from ${file.name.substringAfter("journal_").substringBefore(".txt")} ---") // Use full timestamp if available
                allText.append("\n")
                allText.append(entryText)
            }
        }
        currentDate = currentDate.plusDays(1)
    }

    Log.d("GetTextForRange", "Fetched total text length: ${allText.length} for range $startDate to $endDate")
    return@withContext allText.toString()
}