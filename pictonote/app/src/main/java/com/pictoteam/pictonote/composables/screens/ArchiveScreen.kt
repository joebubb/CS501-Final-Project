// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/ArchiveScreen.kt
package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow // Can be removed if not used elsewhere
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items // For month grid in dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // For dialog year nav
import androidx.compose.material.icons.automirrored.filled.ArrowForward // For dialog year nav
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.pictoteam.pictonote.constants.*
import com.pictoteam.pictonote.constants.IMAGE_URI_MARKER
import com.pictoteam.pictonote.model.JournalEntryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@Composable
fun ArchiveScreen(navController: NavHostController) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation

    // State for the currently selected YearMonth to display on the archive screen
    var selectedYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var showMonthYearPickerDialog by remember { mutableStateOf(false) }

    val screenContentPadding = if (screenWidthDp >= 600) 24.dp else 16.dp
    val contentAreaModifier = Modifier
        .fillMaxSize()
        .padding(screenContentPadding)

    // When selectedYearMonth changes, reset the selectedDay
    LaunchedEffect(selectedYearMonth) {
        selectedDay = null
        Log.d("ArchiveScreen", "YearMonth changed to: $selectedYearMonth, selectedDay reset.")
    }

    if (showMonthYearPickerDialog) {
        MonthYearPickerDialog(
            initialYearMonth = selectedYearMonth,
            onDismissRequest = { showMonthYearPickerDialog = false },
            onYearMonthSelected = { newYearMonth ->
                selectedYearMonth = newYearMonth
                showMonthYearPickerDialog = false
            }
        )
    }

    when {
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(modifier = contentAreaModifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Left Column: Calendar
                Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ArchiveHeader(
                        selectedYearMonth = selectedYearMonth,
                        onHeaderClick = { showMonthYearPickerDialog = true }
                    )
                    CalendarGrid(
                        context = context,
                        yearMonth = selectedYearMonth, // Pass YearMonth
                        columns = GridCells.Adaptive(minSize = 60.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) { day -> selectedDay = day }
                }
                // Right Column: Memories Card
                Column(
                    modifier = Modifier.weight(1f).padding(top = (MaterialTheme.typography.headlineMedium.fontSize.value.dp + 16.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MemoriesCard(
                        modifier = Modifier.fillMaxSize(),
                        context = context,
                        selectedYear = selectedYearMonth.year,
                        selectedMonth = selectedYearMonth.monthValue,
                        selectedDay = selectedDay,
                        navController = navController
                    )
                }
            }
        }
        // Default Portrait / Smaller Screen Layout
        else -> {
            val gridColumns = if (screenWidthDp >= 600) GridCells.Adaptive(minSize = 70.dp) else GridCells.Fixed(7)
            Column(modifier = contentAreaModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ArchiveHeader(
                    selectedYearMonth = selectedYearMonth,
                    onHeaderClick = { showMonthYearPickerDialog = true }
                )
                CalendarGrid(
                    context = context,
                    yearMonth = selectedYearMonth, // Pass YearMonth
                    columns = gridColumns,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { day -> selectedDay = day }
                MemoriesCard(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
                    context = context,
                    selectedYear = selectedYearMonth.year,
                    selectedMonth = selectedYearMonth.monthValue,
                    selectedDay = selectedDay,
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun ArchiveHeader(selectedYearMonth: YearMonth, onHeaderClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClick)
            .padding(vertical = 8.dp), // Add some padding to make clickable area larger
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center // Center the header content
    ) {
        Text(
            text = "${selectedYearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${selectedYearMonth.year}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold // Make it look a bit more like a button
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = "Select Month and Year",
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun MonthYearPickerDialog(
    initialYearMonth: YearMonth,
    onDismissRequest: () -> Unit,
    onYearMonthSelected: (YearMonth) -> Unit
) {
    var currentDialogYear by remember { mutableStateOf(initialYearMonth.year) }
    // Store the initially selected month to pre-select in the dialog's month grid
    var selectedMonthInDialog by remember { mutableStateOf(initialYearMonth.month) }

    val allMonths = Month.values() // Array of all Month enums

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select Month and Year", style = MaterialTheme.typography.titleLarge)

                // Year Selector Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { currentDialogYear-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous Year")
                    }
                    Text(
                        text = currentDialogYear.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { currentDialogYear++ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next Year")
                    }
                }

                // Month Grid (3 columns for months)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 200.dp) // Constrain height if many months
                ) {
                    items(allMonths) { month ->
                        Button(
                            onClick = { selectedMonthInDialog = month },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.aspectRatio(1.5f), // Adjust for button shape
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedMonthInDialog == month) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedMonthInDialog == month) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(month.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onYearMonthSelected(YearMonth.of(currentDialogYear, selectedMonthInDialog))
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}


@Composable
fun CalendarGrid(
    context: Context,
    yearMonth: YearMonth, // Changed to YearMonth
    columns: GridCells,
    modifier: Modifier = Modifier,
    onDateSelected: (Int) -> Unit
) {
    val daysWithEntries by remember(context, yearMonth) { // Key on yearMonth
        mutableStateOf(getDaysWithEntriesForMonth(context, yearMonth.year, yearMonth.monthValue))
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        val daysInMonth = yearMonth.lengthOfMonth()
        items(daysInMonth) { dayIndex ->
            val dayOfMonth = dayIndex + 1
            val hasEntry = daysWithEntries.contains(dayOfMonth)
            Card(
                modifier = Modifier.aspectRatio(1f),
                onClick = { if (hasEntry) onDateSelected(dayOfMonth) },
                enabled = hasEntry,
                colors = CardDefaults.cardColors(
                    containerColor = if (hasEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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

// MemoriesCard, getDaysWithEntriesForMonth, readJournalEntriesForDate, readJournalEntryFromFileInternal
// remain UNCHANGED from your last provided version. Make sure they use selectedYear and selectedMonth.
// ... (Paste the existing MemoriesCard, getDaysWithEntriesForMonth, readJournalEntriesForDate, readJournalEntryFromFileInternal here)
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
    var currentEntryIndex by remember(selectedYear, selectedMonth, selectedDay) { mutableIntStateOf(0) }

    LaunchedEffect(selectedYear, selectedMonth, selectedDay) {
        journalEntries = selectedDay?.let { day ->
            withContext(Dispatchers.IO) {
                readJournalEntriesForDate(context, selectedYear, selectedMonth, day)
            }
        } ?: emptyList()
        currentEntryIndex = 0
        Log.d("MemoriesCard", "Loaded ${journalEntries.size} entries for $selectedYear-$selectedMonth-$selectedDay")
    }

    val currentEntry = journalEntries.getOrNull(currentEntryIndex)

    Card(modifier = modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            when {
                selectedDay == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Select a highlighted date...",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                journalEntries.isEmpty() && selectedDay != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No entries found for this date.",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                currentEntry != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentEntry.fileTimestamp?.format(archiveDisplayTimeFormatter) ?: "Entry ${currentEntryIndex + 1}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (journalEntries.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { currentEntryIndex = (currentEntryIndex - 1 + journalEntries.size) % journalEntries.size },
                                    enabled = journalEntries.size > 1
                                ) { Icon(Icons.Default.ArrowBack, "Previous") } // Corrected Icon
                                Text(
                                    "${currentEntryIndex + 1}/${journalEntries.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = { currentEntryIndex = (currentEntryIndex + 1) % journalEntries.size },
                                    enabled = journalEntries.size > 1
                                ) { Icon(Icons.Default.ArrowForward, "Next") } // Corrected Icon
                            }
                        }
                    }

                    val imageFile: File? = remember(currentEntry.imageRelativePath) {
                        currentEntry.imageRelativePath?.let { File(context.filesDir, it) }
                    }
                    if (imageFile?.exists() == true) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageFile),
                            contentDescription = "Journal image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .padding(bottom = 12.dp)
                        )
                    }

                    if (currentEntry.textContent.isNotBlank()) {
                        Text(
                            text = currentEntry.textContent,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (imageFile?.exists() == true) {
                        Text(
                            "Image captured, no text added.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Empty entry.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.weight(1f))

                    Button(
                        onClick = {
                            try {
                                val encodedPath = Uri.encode(currentEntry.filePath)
                                val route = ROUTE_VIEW_ENTRY_WITH_ARG.replace("{$ARG_ENTRY_FILE_PATH}", encodedPath)
                                Log.d("MemoriesCard", "Navigating to View Entry: $route")
                                navController.navigate(route)
                            } catch (e: Exception) {
                                Log.e("MemoriesCard", "Error encoding/navigating to view: ${e.message}")
                                Toast.makeText(context, "Error opening entry", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Book, contentDescription = "Open Entry", Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Open Entry")
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error displaying entry.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}


fun getDaysWithEntriesForMonth(context: Context, year: Int, month: Int): Set<Int> {
    val days = mutableSetOf<Int>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) {
        Log.w("GetDaysWithEntries", "Journal directory not found: ${directory.absolutePath}")
        return emptySet()
    }

    val monthPrefix = String.format("%d-%02d", year, month) // Corrected to use passed year and month
    val filePrefix = "journal_$monthPrefix"
    Log.d("GetDaysWithEntries", "Scanning for files starting with: $filePrefix in ${directory.absolutePath}")

    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")) {
            try {
                val namePart = file.name.substringAfter("journal_")
                val dateString = if (namePart.contains("_")) {
                    namePart.substringBefore("_")
                } else {
                    namePart.removeSuffix(".txt")
                }
                val date = LocalDate.parse(dateString, filenameDateFormatter)

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

fun readJournalEntriesForDate(context: Context, year: Int, month: Int, day: Int): List<JournalEntryData> {
    val entries = mutableListOf<JournalEntryData>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists() || !directory.isDirectory) {
        Log.w("ReadEntriesForDate", "Journal directory not found: ${directory.absolutePath}")
        return emptyList()
    }

    val dateString = String.format("%d-%02d-%02d", year, month, day)
    val filePrefix = "journal_$dateString"
    Log.d("ReadEntriesForDate", "Reading files starting with: $filePrefix in ${directory.absolutePath}")

    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")) {
            try {
                val (imagePath, textContent) = readJournalEntryFromFileInternal(file)
                val fileTimestamp = try {
                    val namePart = file.name.substringAfter("journal_")
                    if (namePart.contains("_")) {
                        val timestampStr = namePart.substringBefore(".txt")
                        LocalDateTime.parse(timestampStr, filenameDateTimeFormatter)
                    } else {
                        val dateOnlyStr = namePart.removeSuffix(".txt")
                        LocalDate.parse(dateOnlyStr, filenameDateFormatter).atStartOfDay()
                    }
                } catch (e: Exception) {
                    Log.w("ReadEntriesForDate", "Could not parse timestamp from filename: ${file.name}", e)
                    null
                }

                entries.add(
                    JournalEntryData(
                        filePath = file.absolutePath,
                        fileTimestamp = fileTimestamp,
                        imageRelativePath = imagePath,
                        textContent = textContent ?: ""
                    )
                )
            } catch (e: Exception) {
                Log.e("ReadEntriesForDate", "Error processing file ${file.absolutePath}", e)
            }
        }
    }

    entries.sortBy { it.fileTimestamp ?: LocalDateTime.MAX }
    Log.d("ReadEntriesForDate", "Successfully read and sorted ${entries.size} entries for $dateString")
    return entries
}

private fun readJournalEntryFromFileInternal(entryFile: File): Pair<String?, String?> {
    var imagePath: String? = null
    var textContent: String? = null
    if (!entryFile.exists() || !entryFile.canRead()) {
        Log.e("ReadFileInternal", "File does not exist or cannot be read: ${entryFile.path}")
        return Pair(null, null)
    }
    try {
        val lines = entryFile.readLines()
        val imageLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }
        imagePath = imageLine?.substringAfter(IMAGE_URI_MARKER)?.trim()
        textContent = lines.filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n")
        Log.d("ReadFileInternal", "Read ${entryFile.name}. Image path: $imagePath, Text length: ${textContent?.length}")
    } catch (e: Exception) {
        Log.e("ReadFileInternal", "Error reading file content: ${entryFile.path}", e)
        return Pair(null, null)
    }
    return Pair(imagePath, textContent)
}