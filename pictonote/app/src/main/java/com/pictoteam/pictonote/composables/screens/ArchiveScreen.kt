// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/ArchiveScreen.kt
package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Keep existing icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.pictoteam.pictonote.constants.* // Import all constants
import com.pictoteam.pictonote.model.IMAGE_URI_MARKER // Ensure this is imported if needed by readJournalEntryFromFileInternal
import com.pictoteam.pictonote.model.JournalEntryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun ArchiveScreen(navController: NavHostController) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation
    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    val currentYear = YearMonth.now().year
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp
    val topLevelModifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = screenPadding)
    val contentModifier = topLevelModifier.padding(vertical = screenPadding) // Use vertical padding from screenPadding

    LaunchedEffect(selectedMonth) { selectedDay = null } // Reset day when month changes

    when {
        // Large Screen Landscape Layout
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(modifier = contentModifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Left Column: Calendar
                Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Calendar - $currentYear", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                    MonthSelectorRow(currentYear, selectedMonth) { newMonth -> selectedMonth = newMonth }
                    CalendarGrid(
                        context = context,
                        currentYear = currentYear,
                        selectedMonth = selectedMonth,
                        columns = GridCells.Adaptive(minSize = 60.dp), // Smaller cells for more days
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) { day -> selectedDay = day }
                }
                // Right Column: Memories Card
                Column(
                    modifier = Modifier.weight(1f).padding(top = (MaterialTheme.typography.headlineMedium.fontSize.value.dp + 16.dp)), // Align below title roughly
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MemoriesCard(
                        modifier = Modifier.fillMaxSize(), // Take full height of this column
                        context = context,
                        selectedYear = currentYear,
                        selectedMonth = selectedMonth,
                        selectedDay = selectedDay,
                        navController = navController
                    )
                }
            }
        }
        // Default Portrait / Smaller Screen Layout
        else -> {
            val gridColumns = if (screenWidthDp >= 600) GridCells.Adaptive(minSize = 70.dp) else GridCells.Fixed(7)
            Column(modifier = contentModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Calendar - $currentYear", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                MonthSelectorRow(currentYear, selectedMonth) { newMonth -> selectedMonth = newMonth }
                CalendarGrid(
                    context = context,
                    currentYear = currentYear,
                    selectedMonth = selectedMonth,
                    columns = gridColumns,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { day -> selectedDay = day }
                // Memories card takes remaining space
                MemoriesCard(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), // Use weight
                    context = context,
                    selectedYear = currentYear,
                    selectedMonth = selectedMonth,
                    selectedDay = selectedDay,
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun MonthSelectorRow(currentYear: Int, selectedMonth: Int, onMonthSelected: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp) // Padding for scrollable content
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
            ) {
                Text(monthName)
            }
        }
    }
}

@Composable
fun CalendarGrid(
    context: Context,
    currentYear: Int,
    selectedMonth: Int,
    columns: GridCells,
    modifier: Modifier = Modifier,
    onDateSelected: (Int) -> Unit
) {
    // Remember the days with entries based on context, year, and month
    val daysWithEntries by remember(context, currentYear, selectedMonth) {
        mutableStateOf(getDaysWithEntriesForMonth(context, currentYear, selectedMonth))
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(4.dp) // Inner padding for grid items
    ) {
        val daysInMonth = YearMonth.of(currentYear, selectedMonth).lengthOfMonth()
        items(daysInMonth) { dayIndex ->
            val dayOfMonth = dayIndex + 1
            val hasEntry = daysWithEntries.contains(dayOfMonth)
            Card(
                modifier = Modifier.aspectRatio(1f), // Make cells square
                onClick = { if (hasEntry) onDateSelected(dayOfMonth) },
                enabled = hasEntry, // Disable click if no entry
                colors = CardDefaults.cardColors(
                    containerColor = if (hasEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) // Different look for disabled
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "$dayOfMonth",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasEntry) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun MemoriesCard(
    modifier: Modifier = Modifier,
    context: Context,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int?,
    navController: NavHostController
) {
    var journalEntries by remember(context, selectedYear, selectedMonth, selectedDay) { mutableStateOf<List<JournalEntryData>>(emptyList()) }
    var currentEntryIndex by remember(selectedYear, selectedMonth, selectedDay) { mutableStateOf(0) }

    // Load entries when selection changes
    LaunchedEffect(selectedYear, selectedMonth, selectedDay) {
        journalEntries = selectedDay?.let { day ->
            withContext(Dispatchers.IO) {
                readJournalEntriesForDate(context, selectedYear, selectedMonth, day)
            }
        } ?: emptyList()
        currentEntryIndex = 0 // Reset index when selection changes
        Log.d("MemoriesCard", "Loaded ${journalEntries.size} entries for $selectedYear-$selectedMonth-$selectedDay")
    }

    val currentEntry = journalEntries.getOrNull(currentEntryIndex)

    Card(modifier = modifier.fillMaxSize()) {
        // Column allows scrolling if content overflows
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState) // Make content scrollable
        ) {
            when {
                // State: No date selected
                selectedDay == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select a highlighted date...",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                // State: Date selected, but no entries found (after loading attempt)
                journalEntries.isEmpty() && selectedDay != null -> { // Check selectedDay is not null to differentiate from initial state
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No entries found for this date.",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // State: Entry loaded successfully
                currentEntry != null -> {
                    // Header with Timestamp and Entry Navigation (if multiple)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display timestamp or entry number
                        Text(
                            text = currentEntry.fileTimestamp?.format(archiveDisplayTimeFormatter) ?: "Entry ${currentEntryIndex + 1}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        // Show Prev/Next buttons only if there's more than one entry
                        if (journalEntries.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { currentEntryIndex = (currentEntryIndex - 1 + journalEntries.size) % journalEntries.size },
                                    enabled = journalEntries.size > 1
                                ) { Icon(Icons.Default.ArrowBack, "Previous") }
                                Text(
                                    "${currentEntryIndex + 1}/${journalEntries.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium // Consistent text style
                                )
                                IconButton(
                                    onClick = { currentEntryIndex = (currentEntryIndex + 1) % journalEntries.size },
                                    enabled = journalEntries.size > 1
                                ) { Icon(Icons.Default.ArrowForward, "Next") }
                            }
                        }
                    }

                    // Display Image if available
                    val imageFile: File? = remember(currentEntry.imageRelativePath) {
                        currentEntry.imageRelativePath?.let { File(context.filesDir, it) }
                    }
                    if (imageFile?.exists() == true) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageFile),
                            contentDescription = "Journal image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f) // Maintain aspect ratio
                                .padding(bottom = 12.dp) // Space below image
                        )
                    }

                    // Display Text Preview (limited lines)
                    if (currentEntry.textContent.isNotBlank()) {
                        Text(
                            text = currentEntry.textContent,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 5, // Show only a preview
                            overflow = TextOverflow.Ellipsis // Indicate more text exists
                        )
                    } else if (imageFile?.exists() == true) {
                        // Text shown when only image exists
                        Text(
                            "Image captured, no text added.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Text shown when entry is completely empty
                        Text(
                            "Empty entry.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(16.dp)) // Space before the button
                    Box(Modifier.weight(1f)) // Spacer to push the button to the bottom

                    // Button to Navigate to ViewEntryScreen
                    Button(
                        onClick = {
                            try {
                                // Encode the file path for safe navigation as a route parameter
                                val encodedPath = Uri.encode(currentEntry.filePath)
                                // Construct the route for ViewEntryScreen
                                val route = ROUTE_VIEW_ENTRY_WITH_ARG.replace("{$ARG_ENTRY_FILE_PATH}", encodedPath)
                                Log.d("MemoriesCard", "Navigating to View Entry: $route")
                                navController.navigate(route)
                            } catch (e: Exception) {
                                Log.e("MemoriesCard", "Error encoding/navigating to view: ${e.message}")
                                Toast.makeText(context, "Error opening entry", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.End) // Align button to the right
                    ) {
                        Icon(Icons.Default.Book, contentDescription = "Open Entry", Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Open Entry")
                    }
                }
                // Fallback: Should not happen if logic is correct, but handles unexpected null currentEntry
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error displaying entry.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}


// Helper function to get days with entries for a specific month and year
fun getDaysWithEntriesForMonth(context: Context, year: Int, month: Int): Set<Int> {
    val days = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) {
        Log.w("GetDaysWithEntries", "Journal directory not found: ${directory.absolutePath}")
        return emptySet()
    }

    // Create the prefix for filenames of the target month (e.g., "journal_2024-07")
    val monthPrefix = String.format("%d-%02d", year, month)
    val filePrefix = "journal_$monthPrefix"
    Log.d("GetDaysWithEntries", "Scanning for files starting with: $filePrefix in ${directory.absolutePath}")

    directory.listFiles()?.forEach { file ->
        // Check if it's a file, starts with the correct prefix, and ends with .txt
        if (file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")) {
            try {
                // Extract the date part (e.g., "2024-07-15") from the filename
                val dateString = file.name.substringAfter("journal_").substringBefore("_") // Handle potential timestamp
                val date = LocalDate.parse(dateString, filenameDateFormatter) // Use specific date formatter

                // Double-check year and month match (already partially checked by prefix)
                if (date.year == year && date.monthValue == month) {
                    days.add(date.dayOfMonth)
                } else {
                    Log.w("GetDaysWithEntries", "Filename prefix matched, but date parsed differently: ${file.name} -> $date")
                }
            } catch (e: Exception) {
                Log.e("GetDaysWithEntries", "Error parsing filename to date: ${file.name}", e)
            }
        }
    }
    Log.d("GetDaysWithEntries", "Found entries for days: $days in $year-$month")
    return days
}

// Helper function to read all journal entries for a specific date
fun readJournalEntriesForDate(context: Context, year: Int, month: Int, day: Int): List<JournalEntryData> {
    val entries = mutableListOf<JournalEntryData>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) {
        Log.w("ReadEntriesForDate", "Journal directory not found: ${directory.absolutePath}")
        return emptyList()
    }

    // Create the specific date string (e.g., "2024-07-15")
    val dateString = String.format("%d-%02d-%02d", year, month, day)
    val filePrefix = "journal_$dateString" // Files might have timestamp appended (e.g., journal_2024-07-15_10-30-00-000.txt)
    Log.d("ReadEntriesForDate", "Reading files starting with: $filePrefix in ${directory.absolutePath}")

    directory.listFiles()?.forEach { file ->
        // Check if it's a file, starts with the specific date prefix, and ends with .txt
        if (file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")) {
            try {
                // Use internal helper to read image path and text content
                val (imagePath, textContent) = readJournalEntryFromFileInternal(file)

                // Attempt to parse the full timestamp from the filename
                val fileTimestamp = try {
                    val timestampStr = file.name.substringAfter("journal_").substringBefore(".txt")
                    LocalDateTime.parse(timestampStr, filenameDateTimeFormatter) // Use the detailed formatter
                } catch (e: Exception) {
                    Log.w("ReadEntriesForDate", "Could not parse timestamp from filename: ${file.name}", e)
                    null // Set timestamp to null if parsing fails
                }

                entries.add(
                    JournalEntryData(
                        filePath = file.absolutePath,
                        fileTimestamp = fileTimestamp,
                        imageRelativePath = imagePath,
                        textContent = textContent ?: "" // Use empty string if text is null
                    )
                )
            } catch (e: Exception) {
                Log.e("ReadEntriesForDate", "Error processing file ${file.absolutePath}", e)
                // Optionally skip this file and continue with others
            }
        }
    }

    // Sort entries by timestamp (oldest first), handle null timestamps gracefully (e.g., place them last)
    entries.sortBy { it.fileTimestamp ?: LocalDateTime.MAX }
    Log.d("ReadEntriesForDate", "Successfully read and sorted ${entries.size} entries for $dateString")
    return entries
}

// Internal helper to read image URI and text content from a single journal entry file
private fun readJournalEntryFromFileInternal(entryFile: File): Pair<String?, String?> {
    var imagePath: String? = null
    var textContent: String? = null
    if (!entryFile.exists() || !entryFile.canRead()) {
        Log.e("ReadFileInternal", "File does not exist or cannot be read: ${entryFile.path}")
        return Pair(null, null)
    }
    try {
        val lines = entryFile.readLines()
        // Find the line marking the image URI, if it exists
        val imageLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }
        // Extract the path after the marker, trimming whitespace
        imagePath = imageLine?.substringAfter(IMAGE_URI_MARKER)?.trim()

        // Join all lines *except* the image marker line (if it existed)
        textContent = lines.filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n")

        Log.d("ReadFileInternal", "Read ${entryFile.name}. Image path: $imagePath, Text length: ${textContent?.length}")

    } catch (e: Exception) {
        Log.e("ReadFileInternal", "Error reading file content: ${entryFile.path}", e)
        // Return nulls to indicate failure
        return Pair(null, null)
    }
    return Pair(imagePath, textContent)
}