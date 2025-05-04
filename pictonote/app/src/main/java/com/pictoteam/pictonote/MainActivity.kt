package com.pictoteam.pictonote

import android.os.Bundle
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
import com.pictoteam.pictonote.constants.ARG_ENTRY_FILE_PATH
import com.pictoteam.pictonote.constants.ROUTE_ARCHIVE
import com.pictoteam.pictonote.constants.ROUTE_HOME
import com.pictoteam.pictonote.constants.ROUTE_JOURNAL
import com.pictoteam.pictonote.constants.ROUTE_JOURNAL_WITH_OPTIONAL_ARG
import com.pictoteam.pictonote.constants.ROUTE_SETTINGS
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
            selected = currentRoute == ROUTE_ARCHIVE,
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
                if (currentRoute?.startsWith(ROUTE_JOURNAL) != true) {
                    navController.navigate(ROUTE_JOURNAL) { // Navigate to plain route for new
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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
    if (navController.currentDestination?.route != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
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
            route = ROUTE_JOURNAL_WITH_OPTIONAL_ARG,
            arguments = listOf(navArgument(ARG_ENTRY_FILE_PATH) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val entryFilePath = backStackEntry.arguments?.getString(ARG_ENTRY_FILE_PATH)
            JournalScreen(
                navController = navController,
                entryFilePathToEdit = entryFilePath
            )
        }
        composable(ROUTE_SETTINGS) { SettingsScreen() }
    }
}