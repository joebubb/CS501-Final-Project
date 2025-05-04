package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pictoteam.pictonote.constants.JOURNAL_DIR
import com.pictoteam.pictonote.constants.filenameDateFormatter
import java.io.File
import java.time.LocalDate

@Composable
fun HomeScreen() {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation
    val commonCardModifier = Modifier.padding(vertical = 8.dp)
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp
    val topLevelModifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = screenPadding)
    val contentModifier = topLevelModifier.padding(vertical = screenPadding)

    when {
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Column(modifier = contentModifier) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.fillMaxSize().padding(vertical = 0.dp), Arrangement.spacedBy(24.dp), Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Spacer(Modifier.height(16.dp))
                        StreakCard(modifier = commonCardModifier.fillMaxWidth(), screenWidthDp = screenWidthDp)
                    }
                    Column(Modifier.weight(1f)) {
                        Spacer(Modifier.height(16.dp))
                        RemindersCard(modifier = commonCardModifier.fillMaxWidth())
                    }
                }
            }
        }
        else -> {
            Column(contentModifier, Arrangement.spacedBy(20.dp), Alignment.CenterHorizontally) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                StreakCard(commonCardModifier.fillMaxWidth(if (screenWidthDp < 600) 0.9f else 1f), screenWidthDp)
                RemindersCard(commonCardModifier.fillMaxWidth(if (screenWidthDp < 600) 0.9f else 1f))
            }
        }
    }
}

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
            val dayOfWeekIndex = date.dayOfWeek.value % 7
            pastWeekIndices.add(dayOfWeekIndex)
        }
    }
    Log.d("StreakCard", "Past week indices with entries: $pastWeekIndices")
    return pastWeekIndices
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