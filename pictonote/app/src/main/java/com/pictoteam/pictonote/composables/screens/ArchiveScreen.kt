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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.pictoteam.pictonote.constants.ARG_ENTRY_FILE_PATH
import com.pictoteam.pictonote.constants.JOURNAL_DIR
import com.pictoteam.pictonote.constants.ROUTE_JOURNAL
import com.pictoteam.pictonote.constants.archiveDisplayTimeFormatter
import com.pictoteam.pictonote.constants.filenameDateFormatter
import com.pictoteam.pictonote.constants.filenameDateTimeFormatter
import com.pictoteam.pictonote.model.IMAGE_URI_MARKER
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
    val contentModifier = topLevelModifier.padding(vertical = screenPadding)

    LaunchedEffect(selectedMonth) { selectedDay = null }

    when {
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(modifier = contentModifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(2f), Arrangement.spacedBy(16.dp)) { // Left Column
                    Text("Calendar - $currentYear", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                    MonthSelectorRow(currentYear, selectedMonth) { newMonth -> selectedMonth = newMonth }
                    CalendarGrid(context, currentYear, selectedMonth, GridCells.Adaptive(minSize = 60.dp), Modifier.fillMaxWidth().padding(vertical = 8.dp)) { day -> selectedDay = day }
                }
                Column(Modifier.weight(1f).padding(top = (MaterialTheme.typography.headlineMedium.fontSize.value.dp + 16.dp)), horizontalAlignment = Alignment.CenterHorizontally) { // Right Column
                    MemoriesCard(Modifier.fillMaxSize(), context, currentYear, selectedMonth, selectedDay, navController)
                }
            }
        }
        else -> { // Default Portrait/Phone
            val gridColumns = if (screenWidthDp >= 600) GridCells.Adaptive(minSize = 70.dp) else GridCells.Fixed(7)
            Column(modifier = contentModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Calendar - $currentYear", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                MonthSelectorRow(currentYear, selectedMonth) { newMonth -> selectedMonth = newMonth }
                CalendarGrid(context, currentYear, selectedMonth, gridColumns, Modifier.fillMaxWidth().padding(vertical = 8.dp)) { day -> selectedDay = day }
                MemoriesCard(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), context, currentYear, selectedMonth, selectedDay, navController)
            }
        }
    }
}

@Composable
fun MonthSelectorRow(currentYear: Int, selectedMonth: Int, onMonthSelected: (Int) -> Unit) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
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

@Composable
fun CalendarGrid(context: Context, currentYear: Int, selectedMonth: Int, columns: GridCells, modifier: Modifier = Modifier, onDateSelected: (Int) -> Unit) {
    val daysWithEntries by remember(context, currentYear, selectedMonth) {
        mutableStateOf(getDaysWithEntriesForMonth(context, currentYear, selectedMonth))
    }
    LazyVerticalGrid(columns, modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp)) {
        val daysInMonth = YearMonth.of(currentYear, selectedMonth).lengthOfMonth()
        items(daysInMonth) { dayIndex ->
            val dayOfMonth = dayIndex + 1
            val hasEntry = daysWithEntries.contains(dayOfMonth)
            Card(
                modifier = Modifier.aspectRatio(1f),
                onClick = { if (hasEntry) onDateSelected(dayOfMonth) },
                enabled = hasEntry,
                colors = CardDefaults.cardColors(containerColor = if (hasEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("$dayOfMonth", style = MaterialTheme.typography.bodyMedium, color = if (hasEntry) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun MemoriesCard(modifier: Modifier = Modifier, context: Context, selectedYear: Int, selectedMonth: Int, selectedDay: Int?, navController: NavHostController) {
    var journalEntries by remember(context, selectedYear, selectedMonth, selectedDay) { mutableStateOf<List<JournalEntryData>>(emptyList()) }
    var currentEntryIndex by remember(selectedYear, selectedMonth, selectedDay) { mutableStateOf(0) }

    LaunchedEffect(selectedYear, selectedMonth, selectedDay) {
        journalEntries = selectedDay?.let { day -> withContext(Dispatchers.IO) { readJournalEntriesForDate(context, selectedYear, selectedMonth, day) } } ?: emptyList()
        currentEntryIndex = 0
    }

    val currentEntry = journalEntries.getOrNull(currentEntryIndex)

    Card(modifier = modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
            when {
                selectedDay == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Select a highlighted date...", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center) }
                journalEntries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No entries found for this date.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                currentEntry != null -> {
                    // Header with Timestamp and Navigation
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(currentEntry.fileTimestamp?.format(archiveDisplayTimeFormatter) ?: "Entry ${currentEntryIndex + 1}", style = MaterialTheme.typography.titleMedium)
                        if (journalEntries.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton({ currentEntryIndex = (currentEntryIndex - 1 + journalEntries.size) % journalEntries.size }, enabled = journalEntries.size > 1) { Icon(Icons.Default.ArrowBack, "Previous") }
                                Text("${currentEntryIndex + 1}/${journalEntries.size}", Modifier.padding(horizontal = 8.dp))
                                IconButton({ currentEntryIndex = (currentEntryIndex + 1) % journalEntries.size }, enabled = journalEntries.size > 1) { Icon(Icons.Default.ArrowForward, "Next") }
                            }
                        }
                    }
                    // Image
                    val imageFile: File? = remember(currentEntry.imageRelativePath) { currentEntry.imageRelativePath?.let { File(context.filesDir, it) } }
                    if (imageFile?.exists() == true) {
                        Image(rememberAsyncImagePainter(imageFile), "Journal image", Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(bottom = 12.dp))
                    }
                    // Text
                    if (currentEntry.textContent.isNotBlank()) {
                        Text(currentEntry.textContent, style = MaterialTheme.typography.bodyLarge)
                    } else if (imageFile?.exists() == true) {
                        Text("Image captured, no text added.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Empty entry.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.weight(1f)) // Push button to bottom
                    // Edit Button Navigates
                    Button(
                        onClick = {
                            try {
                                val encodedPath = Uri.encode(currentEntry.filePath)
                                // Navigate using the constant route pattern
                                navController.navigate("$ROUTE_JOURNAL?$ARG_ENTRY_FILE_PATH=$encodedPath")
                            } catch (e: Exception) {
                                Log.e("MemoriesCard", "Error encoding/navigating: ${e.message}")
                                Toast.makeText(context, "Error opening editor", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Edit, "Edit Entry", Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Edit Entry")
                    }
                }
            }
        }
    }
}


fun getDaysWithEntriesForMonth(context: Context, year: Int, month: Int): Set<Int> {
    val days = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) return emptySet()
    val monthPrefix = String.format("%d-%02d", year, month)
    val filePrefix = "journal_$monthPrefix"
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")) {
            try {
                val dateString = file.name.substringAfter("journal_").substringBefore("_")
                val date = LocalDate.parse(dateString, filenameDateFormatter)
                if (date.year == year && date.monthValue == month) {
                    days.add(date.dayOfMonth)
                }
            } catch (e: Exception) {
                // Log.e("GetDaysWithEntries", "Error parsing filename: ${file.name}", e) // Already logged inside function if needed
            }
        }
    }
    return days
}

fun readJournalEntriesForDate(context: Context, year: Int, month: Int, day: Int): List<JournalEntryData> {
    val entries = mutableListOf<JournalEntryData>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) return emptyList()
    val dateString = String.format("%d-%02d-%02d", year, month, day)
    val filePrefix = "journal_$dateString"
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")) {
            try {
                val (imagePath, textContent) = readJournalEntryFromFileInternal(file) // Use internal helper
                val fileTimestamp = try {
                    val timestampStr = file.name.substringAfter("journal_").substringBefore(".txt")
                    LocalDateTime.parse(timestampStr, filenameDateTimeFormatter)
                } catch (e: Exception) { null }

                entries.add(JournalEntryData(file.absolutePath, fileTimestamp, imagePath, textContent ?: ""))
            } catch (e: Exception) {
                Log.e("ReadEntries", "Error reading file ${file.absolutePath}", e)
            }
        }
    }
    entries.sortBy { it.fileTimestamp } // Oldest first
    return entries
}

// Internal helper to read a single file
private fun readJournalEntryFromFileInternal(entryFile: File): Pair<String?, String?> {
    var imagePath: String? = null
    var textContent: String? = null
    try {
        val lines = entryFile.readLines()
        val imageLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }
        imagePath = imageLine?.substringAfter(IMAGE_URI_MARKER)?.trim()
        textContent = lines.drop(if (imageLine != null) 1 else 0).joinToString("\n")
    } catch (e: Exception) {
        Log.e("ReadFileInternal", "Error reading ${entryFile.path}", e)
        // Return nulls or rethrow depending on desired error handling
    }
    return Pair(imagePath, textContent)
}

// updateJournalEntryText - Keep if needed elsewhere
/*
fun updateJournalEntryText(context: Context, filePath: String, newText: String): Boolean {

}
*/