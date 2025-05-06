// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/HomeScreen.kt
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
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale


private fun readJournalEntryTextFromFileInternal(entryFile: File): String? {
    var textContent: String? = null
    if (!entryFile.exists() || !entryFile.canRead()) {
        Log.e("ReadFileInternalText", "File does not exist or cannot be read: ${entryFile.path}")
        return null
    }
    try {
        val lines = entryFile.readLines()
        textContent = lines.filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n").trim()
    } catch (e: Exception) {
        Log.e("ReadFileInternalText", "Error reading file content: ${entryFile.path}", e)
        return null
    }
    return textContent
}

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
                val namePart = file.name.substringAfter("journal_")
                val timestampStr = if (namePart.contains("_")) {
                    namePart.substringBefore(".txt")
                } else {
                    namePart.removeSuffix(".txt")
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


suspend fun getDatesWithEntriesForPastWeek(context: Context): Set<LocalDate> = withContext(Dispatchers.IO) {
    val today = LocalDate.now()
    val datesWithEntries = mutableSetOf<LocalDate>()
    val directory = File(context.filesDir, JOURNAL_DIR)
    if (!directory.exists()) return@withContext emptySet()

    for (i in 0..6) {
        val date = today.minusDays(i.toLong())
        val dateString = date.format(filenameDateFormatter)
        val filePrefix = "journal_$dateString"
        val hasEntryForDay = directory.listFiles { file ->
            file.isFile && file.name.startsWith(filePrefix) && file.name.endsWith(".txt")
        }?.any() ?: false

        if (hasEntryForDay) {
            datesWithEntries.add(date)
        }
    }
    Log.d("StreakLogic", "Dates with entries in past week: $datesWithEntries")
    return@withContext datesWithEntries
}

fun calculateCurrentStreak(datesWithEntries: Set<LocalDate>): Int {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val anchorDay = when {
        datesWithEntries.contains(today) -> today
        datesWithEntries.contains(yesterday) -> yesterday
        else -> null
    }

    if (anchorDay == null) {
        Log.d("StreakLogic", "No entry today or yesterday. Streak is 0.")
        return 0
    }

    var currentStreak = 1
    var previousDayToCheck = anchorDay.minusDays(1)

    while (datesWithEntries.contains(previousDayToCheck)) {
        currentStreak++
        previousDayToCheck = previousDayToCheck.minusDays(1)
    }

    Log.d("StreakLogic", "Calculated streak: $currentStreak (Based on last entry: $anchorDay)")
    return currentStreak
}


@Composable
fun HomeScreen(geminiViewModel: GeminiViewModel = viewModel()) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation
    val commonCardModifier = Modifier.padding(vertical = 8.dp)

    val screenContentPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

    val contentAreaModifier = Modifier
        .fillMaxSize()
        .padding(horizontal = screenContentPadding)

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = refreshTrigger) {
        Log.d("HomeScreen", "LaunchedEffect running for weekly summary (Trigger: $refreshTrigger)")
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(6)
        val fetchedText = getJournalTextForDateRange(context, startDate, endDate)
        geminiViewModel.generateWeeklySummary(fetchedText)
    }

    val datesWithEntries by produceState<Set<LocalDate>>(initialValue = emptySet(), context, refreshTrigger) {
        value = getDatesWithEntriesForPastWeek(context)
    }
    val currentStreak by remember(datesWithEntries) {
        derivedStateOf { calculateCurrentStreak(datesWithEntries) }
    }


    when {
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Column(modifier = contentAreaModifier.padding(vertical = screenContentPadding)) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxSize().padding(vertical = 0.dp), Arrangement.spacedBy(24.dp), Alignment.Top) {
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
                    Column(Modifier.weight(1f)) {
                        RemindersCard(modifier = commonCardModifier.fillMaxWidth())
                    }
                }
            }
        }
        else -> {
            Column(
                modifier = contentAreaModifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = screenContentPadding),
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

@Composable
fun StreakCard(
    modifier: Modifier = Modifier,
    datesWithEntries: Set<LocalDate>,
    currentStreak: Int
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

            val streakNumberText = if (currentStreak > 0) {
                "$currentStreak Day${if (currentStreak != 1) "s" else ""}"
            } else {
                "0 Days"
            }
            val streakColor = if (currentStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = streakNumberText,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = streakColor
                )
                if(currentStreak > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("🔥", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pastWeekDates.forEach { date ->
                    val dayInitial = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).first()
                    val isToday = date.isEqual(today)
                    val hasEntry = datesWithEntries.contains(date)

                    val (backgroundColor, textColor, borderColor) = when {
                        isToday && hasEntry -> Triple(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.onPrimary,
                            MaterialTheme.colorScheme.primary
                        )
                        isToday && !hasEntry -> Triple(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.secondary
                        )
                        !isToday && hasEntry -> Triple(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            Color.Transparent
                        )
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
                                width = if (isToday) 2.dp else 1.dp,
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

@Composable
fun WeeklySummaryCard(
    modifier: Modifier = Modifier,
    geminiViewModel: GeminiViewModel,
    onRefresh: () -> Unit
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