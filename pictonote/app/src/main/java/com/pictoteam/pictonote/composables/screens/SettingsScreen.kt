package com.pictoteam.pictonote.composables.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll // Standard vertical scroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable // Added import
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

// Helper extension function
fun android.content.Context.findActivity(): android.app.Activity {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("Activity not found from context $this. Ensure this Composable is hosted in an Activity.")
}

@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel = viewModel()) {
    val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()
    val notificationFrequencies = listOf("Daily", "Weekly", "Bi-Weekly", "Monthly", "Never")

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val scope = rememberCoroutineScope()
    var isSyncing by rememberSaveable { mutableStateOf(false) }
    var syncStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var currentSyncPhase by rememberSaveable { mutableStateOf("") }
    var showDbSetupDialog by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        if (!checkFirestoreDatabaseConfigured(context)) {
            // Only show dialog if not already shown due to saved state
            if (!showDbSetupDialog) showDbSetupDialog = true
        }
    }

    if (showDbSetupDialog) {
        AlertDialog(
            onDismissRequest = { showDbSetupDialog = false },
            title = { Text("Database Setup Required") },
            text = { Text("Your Firebase Firestore database is not set up or accessible. Please ensure it's created (in Native mode) and security rules allow access.") },
            confirmButton = { TextButton(onClick = { showDbSetupDialog = false }) { Text("OK") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        SettingItem(title = "Dark Mode", description = "Enable dark theme for the app") {
            Switch(checked = settings.isDarkMode, onCheckedChange = { settingsViewModel.updateDarkMode(it) })
        }
        Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(8.dp))

        FontSizeSetting(
            currentSizeSp = settings.baseFontSize,
            onSizeChange = { newSize -> settingsViewModel.updateBaseFontSize(newSize) }
        )
        Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(8.dp))

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

        SettingItem(title = "Push Notifications", description = "Receive reminders and updates") {
            Switch(checked = settings.notificationsEnabled, onCheckedChange = { settingsViewModel.updateNotificationsEnabled(it) })
        }
        if (settings.notificationsEnabled) {
            NotificationFrequencySetting(
                frequencies = notificationFrequencies,
                selectedFrequency = settings.notificationFrequency,
                onFrequencySelected = { settingsViewModel.updateNotificationFrequency(it) }
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enable push notifications to set frequency.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp)); Divider(); Spacer(Modifier.height(16.dp))

        Text("Cloud Sync", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Manually backup & sync all journal entries and images.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (!isSyncing) {
                        isSyncing = true
                        currentSyncPhase = ""
                        syncStatusMessage = "Preparing manual sync..."
                        scope.launch {
                            if (!checkFirestoreDatabaseConfigured(context)) {
                                showDbSetupDialog = true
                                isSyncing = false
                                syncStatusMessage = "Error: Database not configured."
                                return@launch
                            }
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
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = "Manual Sync",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Sync")
                }
            }

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

@Composable
fun NotificationFrequencySetting(
    frequencies: List<String>,
    selectedFrequency: String,
    onFrequencySelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Notification Frequency", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column {
            frequencies.forEach { frequency ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (frequency == selectedFrequency),
                            onClick = { onFrequencySelected(frequency) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (frequency == selectedFrequency),
                        onClick = null
                    )
                    Text(
                        text = frequency,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}