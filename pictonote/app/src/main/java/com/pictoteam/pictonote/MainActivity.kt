package com.pictoteam.pictonote

import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pictoteam.pictonote.composables.SettingsScreen
import com.pictoteam.pictonote.model.GeminiViewModel
import com.pictoteam.pictonote.ui.theme.PictoNoteTheme
import com.pictoteam.pictonote.model.SettingsViewModel
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// main activity: entry point of the app
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // get app settings from the view model
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()

            // apply theme and launch main ui layout
            PictoNoteTheme(
                darkTheme = settings.isDarkMode,
                baseFontSize = settings.baseFontSize
            ) {
                PictoNote()
            }
        }
    }
}

// main ui layout that handles overall scaffold and navigation
@Composable
fun PictoNote() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        // apply scaffold padding to the content
        Box(modifier = Modifier.padding(paddingValues)) {
            NavigationGraph(navController = navController)
        }
    }
}

// bottom navigation bar to switch between screens
@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
            label = { Text("Archive") },
            selected = currentRoute == "archive",
            onClick = { navigateTo(navController, "archive") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") },
            selected = currentRoute == "home",
            onClick = { navigateTo(navController, "home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
            label = { Text("Journal") },
            selected = currentRoute == "journal",
            onClick = { navigateTo(navController, "journal") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = currentRoute == "settings",
            onClick = { navigateTo(navController, "settings") }
        )
    }
}

// helper to navigate between screens
private fun navigateTo(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

// navigation graph defining routes and corresponding screens
@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("archive") { ArchiveScreen() }
        composable("journal") { JournalScreen() }
        composable("settings") { SettingsScreen() }
    }
}

// home screen ui layout: adapts to screen size and orientation
@Composable
fun HomeScreen() {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation

    val commonCardModifier = Modifier.padding(vertical = 8.dp)
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp
    val landscapeHorizontalPadding = if (orientation == Configuration.ORIENTATION_LANDSCAPE) screenPadding + 8.dp else screenPadding

    when {
        // tablet landscape layout: two columns
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = landscapeHorizontalPadding, vertical = screenPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    // show streak card with full width
                    StreakCard(modifier = commonCardModifier.fillMaxWidth(), screenWidthDp = screenWidthDp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    // extra space for alignment in tablet layout
                    Spacer(modifier = Modifier.height(16.dp))
                    RemindersCard(modifier = commonCardModifier.fillMaxWidth())
                }
            }
        }
        // tablet portrait layout: column layout
        screenWidthDp >= 600 -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenPadding, vertical = screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(20.dp))
                StreakCard(modifier = commonCardModifier.fillMaxWidth(), screenWidthDp = screenWidthDp)
                Spacer(modifier = Modifier.height(16.dp))
                RemindersCard(modifier = commonCardModifier.fillMaxWidth())
            }
        }
        // phone layout: column layout with constrained width
        else -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(screenPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Home Page", style = MaterialTheme.typography.headlineMedium)
                StreakCard(modifier = commonCardModifier.fillMaxWidth(0.9f), screenWidthDp = screenWidthDp)
                RemindersCard(modifier = commonCardModifier.fillMaxWidth(0.9f))
            }
        }
    }
}

// streak card: shows weekly progress
@Composable
fun StreakCard(
    modifier: Modifier = Modifier,
    screenWidthDp: Int
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Streak",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
            val isTablet = screenWidthDp >= 600
            val boxSize = if (isTablet) 56.dp else 36.dp
            val spacing = if (isTablet) 12.dp else 4.dp

            // display days of week with completion status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                val completedDays = setOf(1, 2, 4)
                daysOfWeek.forEach { day ->
                    val index = daysOfWeek.indexOf(day)
                    val isCompleted = completedDays.contains(index)
                    Box(
                        modifier = Modifier
                            .size(boxSize)
                            .border(
                                1.dp,
                                if (isCompleted) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day,
                            color = if (isCompleted) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            style = if (isTablet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// reminders card: shows notification setup
@Composable
fun RemindersCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Reminders", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            // simple button for push notifications setup
            Button(onClick = { }) { Text("Set Up Push Notifications") }
        }
    }
}

// archive screen layout: calendar and memories
@Composable
fun ArchiveScreen() {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation
    var selectedMonth by remember { mutableStateOf(YearMonth.now().monthValue) }
    val currentYear = YearMonth.now().year
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

    when {
        // tablet landscape: calendar and memories side by side
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenPadding + 8.dp, vertical = screenPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Calendar - $currentYear",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    // month selector row to switch between months
                    MonthSelectorRow(currentYear, selectedMonth) { selectedMonth = it }
                    // display calendar grid using adaptive columns
                    CalendarGrid(
                        currentYear,
                        selectedMonth,
                        columns = GridCells.Adaptive(minSize = 60.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // show memories card below calendar title
                    MemoriesCard(
                        modifier = Modifier.fillMaxSize()
                            .padding(top = (MaterialTheme.typography.headlineMedium.fontSize.value.dp + 16.dp))
                    )
                }
            }
        }
        // default column layout for tablet portrait and phone
        else -> {
            val gridColumns = if (screenWidthDp >= 600) GridCells.Adaptive(minSize = 70.dp) else GridCells.Fixed(7)
            Column(
                modifier = Modifier.fillMaxSize().padding(screenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Calendar - $currentYear",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                MonthSelectorRow(currentYear, selectedMonth) { selectedMonth = it }
                CalendarGrid(
                    currentYear,
                    selectedMonth,
                    columns = gridColumns,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                MemoriesCard(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)
                )
            }
        }
    }
}

// month selector row: shows buttons for each month
@Composable
fun MonthSelectorRow(
    currentYear: Int,
    selectedMonth: Int,
    onMonthSelected: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(12) { monthIndex ->
            val monthValue = monthIndex + 1
            val monthName = YearMonth.of(currentYear, monthValue).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
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

// calendar grid: displays days in the selected month
@Composable
fun CalendarGrid(
    currentYear: Int,
    selectedMonth: Int,
    columns: GridCells,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        val daysInMonth = YearMonth.of(currentYear, selectedMonth).lengthOfMonth()
        val daysWithEntries = setOf(5, 10, 22)
        items(daysInMonth) { dayIndex ->
            val dayOfMonth = dayIndex + 1
            val hasEntry = daysWithEntries.contains(dayOfMonth)
            Card(
                modifier = Modifier.aspectRatio(1f),
                onClick = { },
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
}

// memories card: placeholder for memories content
@Composable
fun MemoriesCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Memories area", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

// journal screen layout: input area and ai suggestions
@Composable
fun JournalScreen(geminiViewModel: GeminiViewModel = viewModel()) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val orientation = configuration.orientation
    var text by remember { mutableStateOf("") }
    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("click 'prompt' for a suggestion.")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)
    val reflectionResult by geminiViewModel.journalReflection.observeAsState("")
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)
    val screenPadding = if (screenWidthDp >= 600) 24.dp else 16.dp

    // journal entry input: lets the user type text
    @Composable
    fun JournalEntryInput(modifier: Modifier = Modifier, showTitle: Boolean = true) {
        Column(modifier = modifier) {
            if (showTitle) {
                Text("Journal Entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .defaultMinSize(minHeight = 150.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (text.isEmpty()) {
                            Text("What's on your mind?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                        }
                        innerTextField()
                    }
                }
            )
        }
    }

    // ai assistance: shows prompt and reflection suggestions
    @Composable
    fun AiAssistanceAndControls(modifier: Modifier = Modifier) {
        Column(modifier = modifier) {
            Text("Suggestion:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            SuggestionCard(promptSuggestion, isLoadingPrompt)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Reflection:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            ReflectionCard(text, reflectionResult, isLoadingReflection)
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { geminiViewModel.suggestJournalPrompt() },
                    enabled = !isLoadingPrompt && !isLoadingReflection
                ) { Text("Prompt") }
                Button(
                    onClick = { geminiViewModel.reflectOnJournalEntry(text) },
                    enabled = !isLoadingReflection && !isLoadingPrompt && text.isNotBlank()
                ) { Text("Reflection") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { saveJournalEntry(text); text = "" },
                modifier = Modifier.align(Alignment.End),
                enabled = text.isNotBlank() && !isLoadingPrompt && !isLoadingReflection
            ) { Text("Finish Entry") }
        }
    }

    when {
        // tablet landscape: two-pane layout with input and ai controls side by side
        screenWidthDp >= 840 && orientation == Configuration.ORIENTATION_LANDSCAPE -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenPadding + 8.dp, vertical = screenPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                JournalEntryInput(
                    modifier = Modifier.weight(1.8f).fillMaxHeight(),
                    showTitle = true
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Spacer(modifier = Modifier.height(MaterialTheme.typography.titleLarge.fontSize.value.dp + 8.dp))
                    AiAssistanceAndControls()
                }
            }
        }
        // default layout: column with input above ai controls
        else -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(screenPadding)
            ) {
                JournalEntryInput(modifier = Modifier.weight(1f), showTitle = true)
                Spacer(modifier = Modifier.height(16.dp))
                AiAssistanceAndControls()
            }
        }
    }
}

// suggestion card: shows prompt suggestion text
@Composable
fun SuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = promptSuggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (isLoadingPrompt) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

// reflection card: shows reflection result or loading text
@Composable
fun ReflectionCard(entryText: String, reflectionResult: String, isLoadingReflection: Boolean) {
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
                    reflectionResult.isBlank() && entryText.isNotBlank() -> "Click 'reflection' for insights."
                    reflectionResult.isBlank() && entryText.isBlank() -> "Write an entry first."
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
}
