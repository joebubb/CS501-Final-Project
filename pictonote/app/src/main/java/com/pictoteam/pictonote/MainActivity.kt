package com.pictoteam.pictonote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pictoteam.pictonote.model.GeminiViewModel
import com.pictoteam.pictonote.ui.theme.PictoNoteTheme
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PictoNoteTheme {
                PictoNote()
            }
        }
    }
}

@Composable
fun PictoNote() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavigationGraph(navController)
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    // TODO: Implement logic to highlight the selected item based on the current route
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val selectedRoute = currentDestination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountBox, contentDescription = "Archive") },
            label = { Text("Archive") },
            selected = selectedRoute == "archive",
            onClick = {
                if (navController.currentDestination?.route != "archive") {
                    navController.navigate("archive") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = selectedRoute == "home",
            onClick = {
                if (navController.currentDestination?.route != "home") {
                    navController.navigate("home") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Journal") },
            label = { Text("Journal") },
            selected = selectedRoute == "journal",
            onClick = {
                if (navController.currentDestination?.route != "journal") {
                    navController.navigate("journal") {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
    }
}

@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("archive") { ArchiveScreen() }
        composable("journal") { JournalScreen() }
    }
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Home Page", style = MaterialTheme.typography.headlineMedium)

        // Streak Card
        Card(modifier = Modifier.fillMaxWidth(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Streak", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                    // TODO: Implement logic to fetch streak data and highlight completed days
                    val completedDays = setOf(1, 2, 4)

                    daysOfWeek.forEachIndexed { index, day ->
                        val isCompleted = completedDays.contains(index)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    1.dp,
                                    if (isCompleted) MaterialTheme.colorScheme.primary else Color.Gray, // Example border
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day,
                                fontSize = 16.sp,
                                color = if (isCompleted) MaterialTheme.colorScheme.primary else LocalContentColor.current // Example color
                            )
                        }
                    }
                }
            }
        }

        // Reminders Card
        Card(modifier = Modifier.fillMaxWidth(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reminders", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { /* TODO: Implement push notification setup logic */ }) {
                    Text("Set up Push Notifications")
                }
                // TODO: Add display for existing reminders if any
            }
        }
    }
}

@Composable
fun ArchiveScreen() {
    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) } // Default to current month
    val currentYear = YearMonth.now().year // Use current year

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Calendar - $currentYear",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // Month Selector Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(12) { monthIndex ->
                val monthValue = monthIndex + 1
                val monthName = YearMonth.of(currentYear, monthValue)
                    .month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                Button(
                    onClick = { selectedMonth = monthValue },
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

        // Calendar Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val daysInMonth = YearMonth.of(currentYear, selectedMonth).lengthOfMonth()
            // TODO: Fetch which days have entries for the selected month/year
            val daysWithEntries = setOf(5, 10, 22)

            items(daysInMonth) { dayIndex ->
                val dayOfMonth = dayIndex + 1
                val hasEntry = daysWithEntries.contains(dayOfMonth)
                Card(
                    modifier = Modifier
                        .aspectRatio(1f),
                    onClick = { /* TODO: Navigate to view entry for dayOfMonth */ },
                    enabled = hasEntry,
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "$dayOfMonth",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasEntry) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Memories Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // TODO: Replace with actual memory display logic
                Text("Memories Area", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
fun JournalScreen(geminiViewModel: GeminiViewModel = viewModel()) {
    var text by remember { mutableStateOf("") }
    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("Click 'Prompt' for a suggestion.")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)

    val reflectionResult by geminiViewModel.journalReflection.observeAsState("") // Default to empty string
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Journal Entry Section
        Text("Journal Entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) // Use larger title
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (text.isEmpty()) {
                        Text("What's on your mind?", color = MaterialTheme.colorScheme.outline)
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Suggestion Card
        Text("Suggestion:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = promptSuggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isLoadingPrompt) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reflection Card
        Text("Reflection:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isLoadingReflection -> "Generating reflection..."
                        reflectionResult.isBlank() && !text.isBlank() -> "Click 'Reflection' for insights on your entry." // Prompt only if text exists
                        reflectionResult.isBlank() && text.isBlank() -> "Write an entry first." // Prompt if text is empty
                        else -> reflectionResult
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isLoadingReflection) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    geminiViewModel.suggestJournalPrompt()
                },
                enabled = !isLoadingPrompt && !isLoadingReflection
            ) {
                Text("Prompt")
            }

            // Reflection Button
            Button(
                onClick = {
                    geminiViewModel.reflectOnJournalEntry(text)
                },
                enabled = !isLoadingReflection && !isLoadingPrompt && text.isNotBlank()
            ) {
                Text("Reflection")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                saveJournalEntry(text)
                text = ""
                // TODO: Optionally clear the reflection in the ViewModel
                // geminiViewModel.clearReflection()
            },
            modifier = Modifier.align(Alignment.End),
            // Enabled only if text is not empty and no API calls are happening
            enabled = text.isNotBlank() && !isLoadingPrompt && !isLoadingReflection
        ) {
            Text("Finish Entry")
        }
    }
}