package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pictoteam.pictonote.constants.*
import com.pictoteam.pictonote.model.GeminiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Saves LocalDate state across recompositions
val LocalDateSaver = Saver<LocalDate, List<Int>>(save = { listOf(it.year, it.monthValue, it.dayOfMonth) }, restore = { LocalDate.of(it[0], it[1], it[2]) })

// Converts LocalDate to and from String for Set saving
private fun localDateToString(date: LocalDate): String = "${date.year}:${date.monthValue}:${date.dayOfMonth}"
private fun stringToLocalDate(str: String): LocalDate {
    val parts = str.split(':').map(String::toInt)
    return LocalDate.of(parts[0], parts[1], parts[2])
}

// Saves Set<LocalDate> state across recompositions
val SetLocalDateSaver = Saver<Set<LocalDate>, List<String>>(save = { it.map(::localDateToString) }, restore = { it.map(::stringToLocalDate).toSet() })

// Reads journal text content from file, excluding image URI markers
private fun readTextInternal(file: File): String? = runCatching {
    file.readLines().filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n").trim()
}.onFailure {
    Log.e("HomeScreen", "Error reading file ${file.path}", it)
}.getOrNull()

// Aggregates journal entries text within date range
suspend fun getJournalTextForRange(context: Context, start: LocalDate, end: LocalDate): String = withContext(Dispatchers.IO) {
    val builder = StringBuilder()
    val dir = File(context.filesDir, JOURNAL_DIR)
    if (!dir.isDirectory) return@withContext ""

    var date = start
    while (!date.isAfter(end)) {
        val prefix = "journal_${date.format(filenameDateFormatter)}"
        dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".txt") }?.sortedBy { it.name }?.forEach { f ->
            readTextInternal(f)?.let { text ->
                if (builder.isNotEmpty()) builder.append("\n\n")
                val timestamp = f.name.substringAfter("journal_").substringBefore(".txt")
                builder.append("--- Entry from $timestamp ---\n").append(text)
            }
        }
        date = date.plusDays(1)
    }
    Log.d("HomeScreen", "Fetched text length ${builder.length}")
    builder.toString()
}

// Identifies dates with entries over the past week
suspend fun getDatesWithEntriesPastWeek(context: Context): Set<LocalDate> = withContext(Dispatchers.IO) {
    val today = LocalDate.now()
    val entries = mutableSetOf<LocalDate>()
    val dir = File(context.filesDir, JOURNAL_DIR)
    if (!dir.isDirectory) return@withContext emptySet()

    repeat(7) { offset ->
        val date = today.minusDays(offset.toLong())
        val prefix = "journal_${date.format(filenameDateFormatter)}"
        if (dir.listFiles { f -> f.name.startsWith(prefix) }?.any() == true) entries.add(date)
    }
    Log.d("HomeScreen", "Past week entries: $entries")
    entries
}

// Computes current consecutive-day streak from set of entry dates
fun calculateStreak(dates: Set<LocalDate>): Int {
    val today = LocalDate.now()
    val anchor = when {
        dates.contains(today) -> today
        dates.contains(today.minusDays(1)) -> today.minusDays(1)
        else -> return 0
    }
    var count = 1
    var check = anchor.minusDays(1)
    while (dates.contains(check)) {
        count++
        check = check.minusDays(1)
    }
    Log.d("HomeScreen", "Calculated streak: $count")
    return count
}

@Composable
fun HomeScreen(viewModel: GeminiViewModel = viewModel()) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isLandscapeLarge = config.screenWidthDp >= 840 && config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val padding = if (config.screenWidthDp >= 600) 24.dp else 16.dp
    val scope = rememberCoroutineScope()

    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var dates by rememberSaveable(stateSaver = SetLocalDateSaver) { mutableStateOf(emptySet<LocalDate>()) }

    LaunchedEffect(refreshKey) {
        dates = getDatesWithEntriesPastWeek(context)
        val end = LocalDate.now()
        val start = end.minusDays(6)
        val text = getJournalTextForRange(context, start, end)
        viewModel.generateWeeklySummary(text)
    }

    val streak by remember(dates) { derivedStateOf { calculateStreak(dates) } }
    val summary by viewModel.weeklySummary.collectAsStateWithLifecycle()
    val loading by viewModel.isWeeklySummaryLoading.collectAsStateWithLifecycle()

    // Top-level layout adapts for landscape/portrait
    if (isLandscapeLarge) {
        Row(Modifier.fillMaxSize().padding(horizontal = padding, vertical = padding), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                StreakCard(dates, streak, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                WeeklySummaryCard(summary, loading, onRefresh = { scope.launch { refreshKey++ } }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            RemindersCard(Modifier.weight(1f).padding(vertical = 8.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = padding, vertical = padding), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StreakCard(dates, streak, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            RemindersCard(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            WeeklySummaryCard(summary, loading, onRefresh = { scope.launch { refreshKey++ } }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
    }
}

@Composable
fun StreakCard(dates: Set<LocalDate>, streak: Int, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val week = List(7) { i -> today.minusDays((6 - i).toLong()) }

    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Streak", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val text = if (streak > 0) "$streak Day${if (streak != 1) "s" else ""}" else "0 Days"
            val color = if (streak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
                if (streak > 0) Text("🔥", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                week.forEach { date ->
                    val initial = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).first()
                    val isToday = date == today
                    val hasEntry = dates.contains(date)
                    val (bg, fg, border) = when {
                        isToday && hasEntry -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.primary)
                        isToday && !hasEntry -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.secondary)
                        hasEntry -> Triple(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), MaterialTheme.colorScheme.onPrimaryContainer, Color.Transparent)
                        else -> Triple(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), Color.Transparent)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).border(if (isToday) 2.dp else 1.dp, border, CircleShape).background(bg).padding(4.dp), contentAlignment = Alignment.Center) {
                        Text(initial.toString(), fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, color = fg)
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
            Button(onClick = { /* setup notifications */ }) { Text("Set Up Push Notifications") }
        }
    }
}

@Composable
fun WeeklySummaryCard(summary: String?, loading: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Weekly AI Summary", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Filled.Refresh, null) }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator() else Text(summary ?: "No summary available", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
            }
        }
    }
}
