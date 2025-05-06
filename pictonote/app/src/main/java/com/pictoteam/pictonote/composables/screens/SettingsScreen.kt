package com.pictoteam.pictonote.composables.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var currentSyncPhase by remember { mutableStateOf("") }
    var showDbSetupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!checkFirestoreDatabaseConfigured(context)) {
            showDbSetupDialog = true
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
            .verticalScroll(rememberScrollState())
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
            "Backup & sync journal entries and images.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        syncStatusMessage?.let {
            val isError = it.contains("Error", ignoreCase = true) || it.contains("Failed", ignoreCase = true)
            val isSuccess = it.contains("Complete", ignoreCase = true) && !isError
            val textColor = when {
                isError -> MaterialTheme.colorScheme.error
                isSuccess -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant // Neutral for in-progress
            }
            Text(
                it, style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (!isSyncing) {
                        isSyncing = true; currentSyncPhase = ""; syncStatusMessage = "Preparing sync..."
                        scope.launch {
                            if (!checkFirestoreDatabaseConfigured(context)) {
                                showDbSetupDialog = true; isSyncing = false; syncStatusMessage = "Error: Database not configured."; return@launch
                            }
                            synchronizeAllJournalEntries(context,
                                onPhaseChange = { phase -> currentSyncPhase = phase; syncStatusMessage = "$phase..." },
                                onProgress = { phase, current, total -> syncStatusMessage = "$phase ($current/$total)" },
                                onComplete = { totalUniqueEntries, totalSuccessfullySynced ->
                                    isSyncing = false; currentSyncPhase = ""
                                    val failures = totalUniqueEntries - totalSuccessfullySynced
                                    syncStatusMessage = if (totalUniqueEntries > 0) {
                                        "Sync Complete: $totalSuccessfullySynced/$totalUniqueEntries files handled."
                                    } else {
                                        "Sync Complete: No files needed syncing."
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
                if (isSyncing) {
                    CircularProgressIndicator(Modifier.size(20.dp), MaterialTheme.colorScheme.onPrimary, 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("Syncing...")
                } else {
                    Icon(Icons.Default.CloudSync, "Sync"); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Sync")
                }
            }

            Button(
                onClick = { settingsViewModel.logoutUser(activity) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, "Logout"); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Log Out")
            }
        }
        Spacer(Modifier.weight(1f)) // Pushes the button row up if content is short.
        // Remove or adjust if you want buttons higher.
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