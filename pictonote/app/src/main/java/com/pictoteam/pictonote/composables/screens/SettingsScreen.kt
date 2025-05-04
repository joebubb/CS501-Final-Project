package com.pictoteam.pictonote.composables.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager
import com.pictoteam.pictonote.model.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel = viewModel()) {
    // Collect settings state safely using lifecycle awareness
    val settings by settingsViewModel.appSettings.collectAsStateWithLifecycle()

    // Define available notification frequencies
    val notificationFrequencies = listOf("Daily", "Weekly", "Bi-Weekly", "Monthly", "Never")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // Consistent spacing
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Divider() // Separator

        // Dark Mode Setting
        SettingItem(title = "Dark Mode", description = "Enable dark theme for the app") {
            Switch(
                checked = settings.isDarkMode,
                onCheckedChange = { settingsViewModel.updateDarkMode(it) }
            )
        }

        Divider() // Separator

        // Font Size Setting
        FontSizeSetting(
            currentSizeSp = settings.baseFontSize,
            onSizeChange = { settingsViewModel.updateBaseFontSize(it) }
        )

        Divider() // Separator

        // Notifications Enabled Setting
        SettingItem(title = "Push Notifications", description = "Receive reminders and updates") {
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = { settingsViewModel.updateNotificationsEnabled(it) }
            )
        }

        // This section only shows if notifications are enabled
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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Divider()
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
            .padding(vertical = 8.dp), // Padding around each setting item
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween // Pushes control to the end
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) { // Text takes available space
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
    // Calculate steps for discrete slider movement
    val steps = (maxSize - minSize).toInt() - 1

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Base Font Size", style = MaterialTheme.typography.titleMedium)
            // Display the current size in sp
            Text("${currentSizeSp.roundToInt()}.sp", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = currentSizeSp,
            onValueChange = { newValue ->
                // Update as slider moves
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
        Spacer(Modifier.height(8.dp)) // Space between title and options
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
                        .padding(vertical = 4.dp), // Padding for each radio button row
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (frequency == selectedFrequency),
                        onClick = null
                    )
                    Text(
                        text = frequency,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp) // Space between radio and text
                    )
                }
            }
        }
    }
}