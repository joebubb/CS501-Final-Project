// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/MainActivity.kt
package com.pictoteam.pictonote

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.pictoteam.pictonote.composables.screens.ArchiveScreen
import com.pictoteam.pictonote.composables.screens.HomeScreen
import com.pictoteam.pictonote.composables.screens.JournalScreen
import com.pictoteam.pictonote.composables.screens.SettingsScreen
import com.pictoteam.pictonote.composables.screens.ViewEntryScreen // Import the new screen
import com.pictoteam.pictonote.constants.* // Import all constants
import com.pictoteam.pictonote.model.SettingsViewModel
import com.pictoteam.pictonote.ui.theme.PictoNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()
            PictoNoteTheme(
                darkTheme = settings.isDarkMode,
                baseFontSize = settings.baseFontSize
            ) {
                ApplyEdgeToEdge()
                PictoNoteApp()
            }
        }
    }
}

@Composable
private fun ApplyEdgeToEdge() {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = !isSystemInDarkTheme()
    DisposableEffect(systemUiController, useDarkIcons) {
        systemUiController.setStatusBarColor(color = Color.Transparent, darkIcons = useDarkIcons)
        systemUiController.setNavigationBarColor(color = Color.Transparent, darkIcons = useDarkIcons)
        onDispose { }
    }
}

@Composable
fun PictoNoteApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()
        Box(modifier = Modifier.padding(bottom = bottomPadding)) {
            NavigationGraph(navController = navController)
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountBox, contentDescription = "Archive") },
            label = { Text("Archive") },
            // Selected if the route is archive OR if viewing an entry (which originates from archive)
            selected = currentRoute == ROUTE_ARCHIVE || currentRoute?.startsWith(ROUTE_VIEW_ENTRY) == true,
            onClick = { navigateTo(navController, ROUTE_ARCHIVE) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = currentRoute == ROUTE_HOME,
            onClick = { navigateTo(navController, ROUTE_HOME) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Journal") },
            label = { Text("Journal") },
            selected = currentRoute?.startsWith(ROUTE_JOURNAL) ?: false,
            onClick = {
                // Navigate to plain journal route only if not already on a journal route
                if (currentRoute?.startsWith(ROUTE_JOURNAL) != true) {
                    // Navigate to the base journal route for creating a new entry
                    navController.navigate(ROUTE_JOURNAL) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                // If already on Journal screen (editing), clicking again does nothing specific here,
                // but the button remains selected.
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentRoute == ROUTE_SETTINGS,
            onClick = { navigateTo(navController, ROUTE_SETTINGS) }
        )
    }
}

// Navigation helper
private fun navigateTo(navController: NavHostController, route: String) {
    // Prevent navigating to the same route if already there
    // Exception: Allow re-navigating to Archive from ViewEntry, etc. (handled by default nav logic)
    if (navController.currentDestination?.route != route) {
        navController.navigate(route) {
            // Pop up to the start destination of the graph to avoid building up a large stack
            popUpTo(navController.graph.startDestinationId) {
                saveState = true // Save state of screens popped off
            }
            // Avoid multiple copies of the same destination when reselecting the same item
            launchSingleTop = true
            // Restore state when navigating back to previously visited screens
            restoreState = true
        }
    }
}

// Navigation graph
@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) { HomeScreen() }
        composable(ROUTE_ARCHIVE) { ArchiveScreen(navController = navController) }
        composable(
            route = ROUTE_JOURNAL_WITH_OPTIONAL_ARG, // For creating new or editing existing
            arguments = listOf(navArgument(ARG_ENTRY_FILE_PATH) {
                type = NavType.StringType
                nullable = true // Nullable for new entries
                defaultValue = null
            })
        ) { backStackEntry ->
            // Get potentially null, encoded path
            val encodedEntryFilePath = backStackEntry.arguments?.getString(ARG_ENTRY_FILE_PATH)
            JournalScreen(
                navController = navController,
                entryFilePathToEdit = encodedEntryFilePath // Pass encoded path (or null) to JournalScreen
            )
        }
        composable(
            route = ROUTE_VIEW_ENTRY_WITH_ARG, // New route for viewing
            arguments = listOf(navArgument(ARG_ENTRY_FILE_PATH) {
                type = NavType.StringType
                nullable = false // Path is required for viewing
            })
        ) { backStackEntry ->
            // Get the required, encoded path. Handle potential null for safety although NavType says non-nullable.
            val encodedEntryFilePath = backStackEntry.arguments?.getString(ARG_ENTRY_FILE_PATH)
            if (encodedEntryFilePath != null) {
                ViewEntryScreen(
                    navController = navController,
                    encodedEntryFilePath = encodedEntryFilePath // Pass encoded path
                )
            } else {
                // Handle error: Log and navigate back
                Log.e("NavigationGraph", "Error: entryFilePath was null for $ROUTE_VIEW_ENTRY_WITH_ARG.")
                LaunchedEffect(Unit) { // Use LaunchedEffect to navigate safely from composable
                    navController.popBackStack()
                }
            }
        }
        composable(ROUTE_SETTINGS) { SettingsScreen() }
    }
}