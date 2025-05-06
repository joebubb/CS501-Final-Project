package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.format.TextStyle
import java.util.Locale

// Utility to save YearMonth state across recompositions
val YearMonthSaver = Saver<YearMonth, List<Int>>(save = { listOf(it.year, it.monthValue) }, restore = { YearMonth.of(it[0], it[1]) })

// Utility to save Month state across recompositions
val MonthSaver = Saver<Month, Int>(save = { it.value }, restore = { Month.of(it) })

/**
 * ArchiveScreen shows a calendar of entries and a list of journal memories.
 */
@Composable
fun ArchiveScreen(navController: NavHostController) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation

    var selectedYearMonth by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.now()) }
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var showMonthYearPickerDialog by rememberSaveable { mutableStateOf(false) }

    // Adjust padding based on screen width
    val contentPadding = if (screenWidthDp >= 600) 24.dp else 16.dp
    val modifier = Modifier.fillMaxSize().padding(contentPadding)

    // Reset selectedDay when month changes
    LaunchedEffect(selectedYearMonth) {
        selectedDay = null
        Log.d("ArchiveScreen", "Month changed: $selectedYearMonth")
    }

    // Show dialog for month/year selection
    if (showMonthYearPickerDialog) {
        MonthYearPickerDialog(
            initialYearMonth = selectedYearMonth,
            onDismissRequest = { showMonthYearPickerDialog = false },
            onYearMonthSelected = {
                selectedYearMonth = it
                showMonthYearPickerDialog = false
            }
        )
    }

    // Layout: landscape large vs portrait/small
    if (screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ArchiveHeader(selectedYearMonth) { showMonthYearPickerDialog = true }
                CalendarGrid(context, selectedYearMonth, GridCells.Adaptive(60.dp)) { day -> selectedDay = day }
            }
            Column(Modifier.weight(1f).padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MemoriesCard(modifier = Modifier.fillMaxSize(), context, selectedYearMonth.year, selectedYearMonth.monthValue, selectedDay, navController)
            }
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ArchiveHeader(selectedYearMonth) { showMonthYearPickerDialog = true }
            val columns = if (screenWidthDp >= 600) GridCells.Adaptive(70.dp) else GridCells.Fixed(7)
            CalendarGrid(context, selectedYearMonth, columns) { day -> selectedDay = day }
            MemoriesCard(Modifier.fillMaxWidth().weight(1f), context, selectedYearMonth.year, selectedYearMonth.monthValue, selectedDay, navController)
        }
    }
}

/**
 * Header showing current month and year, opens picker on click.
 */
@Composable
fun ArchiveHeader(selectedYearMonth: YearMonth, onHeaderClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onHeaderClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(
            text = selectedYearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ", " + selectedYearMonth.year,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Dialog for selecting month and year.
 */
@Composable
fun MonthYearPickerDialog(initialYearMonth: YearMonth, onDismissRequest: () -> Unit, onYearMonthSelected: (YearMonth) -> Unit) {
    var dialogYear by rememberSaveable { mutableStateOf(initialYearMonth.year) }
    var dialogMonth by rememberSaveable(stateSaver = MonthSaver) { mutableStateOf(initialYearMonth.month) }
    val months = Month.values()

    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Month and Year", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ dialogYear-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(dialogYear.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton({ dialogYear++ }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 200.dp)) {
                    items(months) { month ->
                        Button(
                            onClick = { dialogMonth = month },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (month == dialogMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (month == dialogMonth) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(month.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onYearMonthSelected(YearMonth.of(dialogYear, dialogMonth)) }) { Text("OK") }
                }
            }
        }
    }
}

/**
 * Displays calendar grid and highlights days with entries.
 */
@Composable
fun CalendarGrid(context: Context, yearMonth: YearMonth, columns: GridCells, onDateSelected: (Int) -> Unit) {
    val daysWithEntries = remember(yearMonth) { getDaysWithEntriesForMonth(context, yearMonth.year, yearMonth.monthValue) }
    LazyVerticalGrid(columns = columns, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(4.dp)) {
        val totalDays = yearMonth.lengthOfMonth()
        items(totalDays) { index ->
            val day = index + 1
            val hasEntry = day in daysWithEntries
            Card(
                modifier = Modifier.aspectRatio(1f),
                enabled = hasEntry,
                onClick = { if (hasEntry) onDateSelected(day) },
                colors = CardDefaults.cardColors(
                    containerColor = if (hasEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(day.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Shows journal entries for selected date and allows navigation.
 */
@Composable
fun MemoriesCard(modifier: Modifier,
                 context: Context,
                 selectedYear: Int,
                 selectedMonth: Int,
                 selectedDay: Int?,
                 navController: NavHostController) {
    var entries by remember(context, selectedYear, selectedMonth, selectedDay) { mutableStateOf<List<JournalEntryData>>(emptyList()) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedYear, selectedMonth, selectedDay) {
        entries = selectedDay?.let { withContext(Dispatchers.IO) { readJournalEntriesForDate(context, selectedYear, selectedMonth, it) } } ?: emptyList()
        currentIndex = 0
        Log.d("MemoriesCard", "Loaded ${entries.size} items for date $selectedYear-$selectedMonth-$selectedDay")
    }

    Card(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            when {
                selectedDay == null -> Text("Select a date to view entries", modifier = Modifier.fillMaxSize(), textAlign = TextAlign.Center)
                entries.isEmpty() -> Text("No entries found", modifier = Modifier.fillMaxSize(), textAlign = TextAlign.Center)
                else -> {
                    val entry = entries[currentIndex]
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.fileTimestamp?.format(archiveDisplayTimeFormatter) ?: "Entry", style = MaterialTheme.typography.titleMedium)
                        if (entries.size > 1) {
                            IconButton(onClick = { currentIndex = (currentIndex - 1 + entries.size) % entries.size }) { Icon(Icons.Default.ArrowBack, null) }
                            Text("${currentIndex + 1}/${entries.size}")
                            IconButton(onClick = { currentIndex = (currentIndex + 1) % entries.size }) { Icon(Icons.Default.ArrowForward, null) }
                        }
                    }
                    entry.imageRelativePath?.let { path ->
                        val file = File(context.filesDir, path)
                        if (file.exists()) {
                            Image(painter = rememberAsyncImagePainter(file), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        }
                    }
                    if (entry.textContent.isNotBlank()) {
                        Text(entry.textContent, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        val encoded = Uri.encode(entry.filePath)
                        navController.navigate(ROUTE_VIEW_ENTRY_WITH_ARG.replace("{$ARG_ENTRY_FILE_PATH}", encoded))
                    }, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Default.Book, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Entry")
                    }
                }
            }
        }
    }
}

// Scan files to identify days with journal entries
fun getDaysWithEntriesForMonth(context: Context, year: Int, month: Int): Set<Int> {
    val results = mutableSetOf<Int>()
    val folder = File(context.filesDir, JOURNAL_DIR)
    if (!folder.exists()) return emptySet()
    val prefix = "journal_${year}-${"%02d".format(month)}"
    folder.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(prefix) && file.name.endsWith(".txt")) {
            runCatching {
                val date = LocalDate.parse(file.name.substringAfter("journal_").substringBefore(".txt"), filenameDateFormatter)
                if (date.year == year && date.monthValue == month) results.add(date.dayOfMonth)
            }
        }
    }
    return results
}

// Read all journal entries for a given date and sort by timestamp
fun readJournalEntriesForDate(context: Context, year: Int, month: Int, day: Int): List<JournalEntryData> {
    val entries = mutableListOf<JournalEntryData>()
    val folder = File(context.filesDir, JOURNAL_DIR)
    if (!folder.exists()) return emptyList()
    val dateStr = "${year}-${"%02d".format(month)}-${"%02d".format(day)}"
    val prefix = "journal_$dateStr"
    folder.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(prefix) && file.name.endsWith(".txt")) {
            runCatching {
                val (imagePath, text) = readJournalEntryFromFileInternal(file)
                val timestamp = runCatching {
                    LocalDateTime.parse(file.name.substringAfter("journal_").substringBefore(".txt"), filenameDateTimeFormatter)
                }.getOrNull()
                entries.add(JournalEntryData(file.absolutePath, timestamp, imagePath, text.orEmpty()))
            }
        }
    }
    return entries.sortedBy { it.fileTimestamp }
}

// Parse the content of a journal file into image path and text
private fun readJournalEntryFromFileInternal(file: File): Pair<String?, String?> {
    val lines = file.readLines()
    val imgLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }
    val imagePath = imgLine?.substringAfter(IMAGE_URI_MARKER)?.trim()
    val text = lines.filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n")
    return imagePath to text
}
