package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val isTablet = config.screenWidthDp >= 600
    val isLargeTablet = config.screenWidthDp >= 840
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Adjusted padding for different screen sizes
    val horizontalPadding = when {
        isLargeTablet -> 32.dp
        isTablet -> 24.dp
        else -> 16.dp
    }

    val verticalPadding = when {
        isLargeTablet -> 24.dp
        isTablet -> 20.dp
        else -> 16.dp
    }

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

    // Larger spacing for tablets
    val spacingBetweenCards = if (isTablet) 24.dp else 16.dp

    // Different card sizes based on device
    val cardElevation = if (isTablet) 4.dp else 2.dp
    val cardShape = if (isTablet)
        RoundedCornerShape(16.dp)
    else
        MaterialTheme.shapes.medium

    // Card modifiers for better tablet display
    val streakCardModifier = Modifier
        .fillMaxWidth()
        .let { if (isLargeTablet && isLandscape) it.heightIn(min = 260.dp) else it }
        .padding(vertical = 8.dp)

    val summaryCardModifier = Modifier
        .fillMaxWidth()
        .let { if (isLargeTablet && isLandscape) it.heightIn(min = 300.dp) else it }
        .padding(vertical = 8.dp)

    // Top-level layout adapts for landscape/portrait
    if (isLargeTablet && isLandscape) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                shape = cardShape
            ) {
                StreakContent(
                    dates = dates,
                    streak = streak,
                    isTablet = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTablet) 24.dp else 16.dp)
                )
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                shape = cardShape
            ) {
                WeeklySummaryContent(
                    summary = summary,
                    loading = loading,
                    onRefresh = { scope.launch { refreshKey++ } },
                    isTablet = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTablet) 24.dp else 16.dp)
                )
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(spacingBetweenCards),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = streakCardModifier,
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                shape = cardShape
            ) {
                StreakContent(
                    dates = dates,
                    streak = streak,
                    isTablet = isTablet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isTablet) 24.dp else 16.dp)
                )
            }

            Card(
                modifier = summaryCardModifier,
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                shape = cardShape
            ) {
                WeeklySummaryContent(
                    summary = summary,
                    loading = loading,
                    onRefresh = { scope.launch { refreshKey++ } },
                    isTablet = isTablet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isTablet) 24.dp else 16.dp)
                )
            }
        }
    }
}

@Composable
fun StreakContent(dates: Set<LocalDate>, streak: Int, isTablet: Boolean, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val week = List(7) { i -> today.minusDays((6 - i).toLong()) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Streak",
            style = if (isTablet)
                MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)
            else
                MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(if (isTablet) 16.dp else 8.dp))

        val text = if (streak > 0) "$streak Day${if (streak != 1) "s" else ""}" else "0 Days"
        val color = if (streak > 0)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = if (isTablet)
                    MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            if (streak > 0) Text(
                "🔥",
                style = if (isTablet)
                    MaterialTheme.typography.headlineLarge
                else
                    MaterialTheme.typography.titleLarge
            )
        }

        Spacer(Modifier.height(if (isTablet) 32.dp else 16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            week.forEach { date ->
                val initial = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).first()
                val isToday = date == today
                val hasEntry = dates.contains(date)

                val (bg, fg, border) = when {
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
                    hasEntry -> Triple(
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

                // Larger date circles for tablets
                val circleSize = if (isTablet) 56.dp else 40.dp
                val textSize = if (isTablet) 18.sp else 14.sp
                val borderWidth = if (isToday) if (isTablet) 3.dp else 2.dp else 1.dp

                Box(
                    Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .border(borderWidth, border, CircleShape)
                        .background(bg)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initial.toString(),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        fontSize = textSize,
                        color = fg
                    )
                }
            }
        }

        if (isTablet) {
            // Additional guidance text for tablets
            Spacer(Modifier.height(24.dp))
            Text(
                "Track your journal writing consistency",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WeeklySummaryContent(
    summary: String?,
    loading: Boolean,
    onRefresh: () -> Unit,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Weekly AI Summary",
                style = if (isTablet)
                    MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)
                else
                    MaterialTheme.typography.headlineSmall
            )

            // Larger refresh button for tablets
            IconButton(
                onClick = onRefresh,
                enabled = !loading,
                modifier = if (isTablet) Modifier.size(56.dp) else Modifier
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh summary",
                    modifier = if (isTablet) Modifier.size(32.dp) else Modifier
                )
            }
        }

        Spacer(Modifier.height(if (isTablet) 20.dp else 12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (isTablet) 160.dp else 100.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = if (isTablet) Modifier.size(48.dp) else Modifier,
                    strokeWidth = if (isTablet) 4.dp else 2.dp
                )
            } else {
                Text(
                    summary ?: "No summary available",
                    style = if (isTablet)
                        MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp)
                    else
                        MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
            }
        }

        if (isTablet && !loading && summary != null) {
            // Additional button for tablets
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.End).height(48.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Update Summary", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}