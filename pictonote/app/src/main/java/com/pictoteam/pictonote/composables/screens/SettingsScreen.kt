package com.pictoteam.pictonote.composables.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll // Enables scrolling for settings that may not fit screen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable // Preserves state during configuration changes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager
import com.pictoteam.pictonote.model.SettingsViewModel
import com.pictoteam.pictonote.database.checkFirestoreDatabaseConfigured
import com.pictoteam.pictonote.database.synchronizeAllJournalEntries
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Extension function to find the activity from any context
// Useful for operations that require an Activity reference, like logging out
fun android.content.Context.findActivity(): android.app.Activity {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("Activity not found from context $this. Ensure this Composable is hosted in an Activity.")
}

/**
 * Main Settings screen composable that displays all user configurable options
 * Handles theme, font size, notifications, cloud sync and logout functionality
 */
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel = viewModel()) {
    // Collect settings as state with lifecycle awareness to prevent unnecessary recompositions
    val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Coroutine scope for async operations like cloud sync
    val scope = rememberCoroutineScope()
    // Track sync state to update UI appropriately
    var isSyncing by rememberSaveable { mutableStateOf(false) }
    var syncStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSyncPhase by rememberSaveable { mutableStateOf("") }
    var showDbSetupDialog by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Check Firebase configuration on initial composition
    LaunchedEffect(Unit) {
        if (!checkFirestoreDatabaseConfigured(context)) {
            // Only show the dialog if it's not already visible
            if (!showDbSetupDialog) showDbSetupDialog = true
        }
    }

    // Database setup error dialog
    if (showDbSetupDialog) {
        AlertDialog(
            onDismissRequest = { showDbSetupDialog = false },
            title = { Text("Database Setup Required") },
            text = { Text("Your Firebase Firestore database is not set up or accessible. Please ensure it's created (in Native mode) and security rules allow access.") },
            confirmButton = { TextButton(onClick = { showDbSetupDialog = false }) { Text("OK") } }
        )
    }

    // Main settings layout with vertical scrolling
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        // Dark mode toggle
        SettingItem(title = "Dark Mode", description = "Enable dark theme for the app") {
            Switch(checked = settings.isDarkMode, onCheckedChange = { settingsViewModel.updateDarkMode(it) })
        }
        Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(8.dp))

        // Font size adjustment slider
        FontSizeSetting(
            currentSizeSp = settings.baseFontSize,
            onSizeChange = { newSize -> settingsViewModel.updateBaseFontSize(newSize) }
        )
        Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(8.dp))

        // Auto-sync toggle for journal entries
        SettingItem(
            title = "Auto-Sync Entries",
            description = "Automatically sync entries when saved or updated."
        ) {
            Switch(
                checked = settings.autoSyncEnabled,
                onCheckedChange = { settingsViewModel.updateAutoSyncEnabled(it) }
            )
        }
        Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(8.dp))

        Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(16.dp))

        // Manual cloud sync section
        Text("Cloud Sync", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Manually backup & sync all journal entries and images.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        // Display sync status message with appropriate color coding based on message content
        syncStatusMessage?.let { message ->
            val hasFailedText = message.contains("failed", ignoreCase = true) || message.contains("issues", ignoreCase = true)
            val isErrorText = message.contains("Error", ignoreCase = true)
            val isCompleteSuccess = message.contains("handled.", ignoreCase = true) && !hasFailedText && !isErrorText

            val textColor = when {
                isErrorText || (message.startsWith("Manual Sync") && hasFailedText) -> MaterialTheme.colorScheme.error
                isCompleteSuccess -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        // Action buttons for sync and logout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sync button with progress indicator during sync
            Button(
                onClick = {
                    if (!isSyncing) {
                        isSyncing = true
                        currentSyncPhase = ""
                        syncStatusMessage = "Preparing manual sync..."
                        scope.launch {
                            // Verify database configuration before attempting sync
                            if (!checkFirestoreDatabaseConfigured(context)) {
                                showDbSetupDialog = true
                                isSyncing = false
                                syncStatusMessage = "Error: Database not configured."
                                return@launch
                            }
                            // Initiate sync with progress callbacks
                            synchronizeAllJournalEntries(
                                context = context,
                                onPhaseChange = { phase ->
                                    currentSyncPhase = phase
                                    syncStatusMessage = "$phase..."
                                },
                                onProgress = { phaseArgument, current, total ->
                                    if(currentSyncPhase.isEmpty() || currentSyncPhase != phaseArgument) currentSyncPhase = phaseArgument
                                    syncStatusMessage = "$currentSyncPhase ($current/$total)"
                                },
                                onComplete = { totalUniqueEntries, totalSuccessfullySynced ->
                                    isSyncing = false
                                    currentSyncPhase = ""
                                    val failures = totalUniqueEntries - totalSuccessfullySynced
                                    syncStatusMessage = if (totalUniqueEntries > 0) {
                                        "Manual Sync: $totalSuccessfullySynced/$totalUniqueEntries handled."
                                    } else {
                                        "Manual Sync: No files needed syncing."
                                    }
                                    if (failures > 0) {
                                        syncStatusMessage += " ($failures had issues)"
                                    }
                                }
                            )
                        }
                    }
                },
                enabled = !isSyncing,
                modifier = Modifier.weight(1f)
            ) {
                if (isSyncing && currentSyncPhase.isNotEmpty()) {
                    // Show progress indicator while sync is active
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    // Standard sync button appearance
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = "Manual Sync",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Sync")
                }
            }

            // Logout button with error styling
            Button(
                onClick = { settingsViewModel.logoutUser(activity) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Log Out")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Reusable composable for individual settings items
 * Displays a title, optional description, and custom content (usually a toggle)
 */
@Composable
fun SettingItem(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/**
 * Font size adjustment composable with slider
 * Allows users to select their preferred text size within defined min/max bounds
 */
@Composable
fun FontSizeSetting(
    currentSizeSp: Float,
    onSizeChange: (Float) -> Unit
) {
    val minSize = SettingsDataStoreManager.MIN_FONT_SIZE_SP
    val maxSize = SettingsDataStoreManager.MAX_FONT_SIZE_SP
    val steps = (maxSize - minSize).toInt() - 1

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Base Font Size", style = MaterialTheme.typography.titleMedium)
            Text("${currentSizeSp.roundToInt()}.sp", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = currentSizeSp,
            onValueChange = { newValue ->
                onSizeChange(newValue)
            },
            valueRange = minSize..maxSize,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

