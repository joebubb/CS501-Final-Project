// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/MainActivity.kt
package com.pictoteam.pictonote

import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext // Added for auto-sync context
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.firebase.auth.FirebaseAuth
import com.pictoteam.pictonote.composables.screens.ArchiveScreen
import com.pictoteam.pictonote.composables.screens.HomeScreen
import com.pictoteam.pictonote.composables.screens.JournalScreen
import com.pictoteam.pictonote.composables.screens.SettingsScreen
import com.pictoteam.pictonote.composables.screens.ViewEntryScreen
import com.pictoteam.pictonote.constants.*
import com.pictoteam.pictonote.database.checkFirestoreDatabaseConfigured
import com.pictoteam.pictonote.database.fetchAndSyncRemoteEntriesToLocal
import com.pictoteam.pictonote.model.SettingsViewModel
import com.pictoteam.pictonote.ui.theme.PictoNoteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()
            PictoNoteTheme(darkTheme = settings.isDarkMode, baseFontSize = settings.baseFontSize) {
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
    var isCameraPreviewActive by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val smallestScreenWidthDp = configuration.smallestScreenWidthDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPhone = smallestScreenWidthDp < 600

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute, isCameraPreviewActive) {
        if (currentRoute?.startsWith(ROUTE_JOURNAL) == false && isCameraPreviewActive) {
            Log.d("PictoNoteApp", "Route changed from Journal or camera no longer active, resetting isCameraPreviewActive")
            isCameraPreviewActive = false
        }
    }

    val onJournalScreenAndCameraActive = currentRoute?.startsWith(ROUTE_JOURNAL) == true && isCameraPreviewActive
    val showBottomBar = !(isPhone && isLandscape || !isPhone && isLandscape && onJournalScreenAndCameraActive)


    val firebaseAuth = FirebaseAuth.getInstance()
    var initialSyncTriggered by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(firebaseAuth.currentUser, initialSyncTriggered) {
        val user = firebaseAuth.currentUser
        if (user != null && !initialSyncTriggered) {
            Log.d("PictoNoteApp", "User signed in. Checking DB and triggering initial remote entries fetch.")
            initialSyncTriggered = true
            coroutineScope.launch {
                delay(2000)
                if (checkFirestoreDatabaseConfigured(context)) {
                    fetchAndSyncRemoteEntriesToLocal(
                        context = context,
                        onComplete = { successCount, failureCount ->
                            Log.i("PictoNoteApp", "Initial fetch complete. Synced: $successCount, Failed: $failureCount")
                        }
                    )
                } else {
                    Log.w("PictoNoteApp", "Initial fetch skipped: Firestore DB not configured.")
                }
            }
        } else if (user == null) {
            initialSyncTriggered = false
        }
    }

    Scaffold(
        bottomBar = { if (showBottomBar) BottomNavigationBar(navController) }
    ) { innerPadding ->
        val actualTopPadding = if (isCameraPreviewActive) 0.dp else innerPadding.calculateTopPadding()
        val layoutDirection = LocalLayoutDirection.current
        val contentAreaPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection),
            top = actualTopPadding,
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding()
        )
        Box(modifier = Modifier.fillMaxSize().padding(contentAreaPadding)) {
            NavigationGraph(navController = navController,
                setCameraPreviewActive = { isActive -> if (isCameraPreviewActive != isActive) isCameraPreviewActive = isActive }
            )
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
                if (currentRoute?.startsWith(ROUTE_JOURNAL) != true) {
                    navController.navigate(ROUTE_JOURNAL) {
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

private fun navigateTo(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    setCameraPreviewActive: (Boolean) -> Unit
) {
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
            val encodedEntryFilePath = backStackEntry.arguments?.getString(ARG_ENTRY_FILE_PATH)
            JournalScreen(
                navController = navController,
                entryFilePathToEdit = encodedEntryFilePath,
                setCameraPreviewActive = setCameraPreviewActive,
                journalViewModel = viewModel(),
                geminiViewModel = viewModel()
            )
        }
        composable(
            route = ROUTE_VIEW_ENTRY_WITH_ARG,
            arguments = listOf(navArgument(ARG_ENTRY_FILE_PATH) {
                type = NavType.StringType
                nullable = false
            })
        ) { backStackEntry ->
            val encodedEntryFilePath = backStackEntry.arguments?.getString(ARG_ENTRY_FILE_PATH)
            if (encodedEntryFilePath != null) {
                ViewEntryScreen(
                    navController = navController,
                    encodedEntryFilePath = encodedEntryFilePath
                )
            } else {
                Log.e("NavigationGraph", "Error: entryFilePath was null for $ROUTE_VIEW_ENTRY_WITH_ARG.")
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
        composable(ROUTE_SETTINGS) { SettingsScreen() }
    }
}