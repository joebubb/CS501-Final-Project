package com.pictoteam.pictonote.database

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.pictoteam.pictonote.constants.JOURNAL_DIR
import com.pictoteam.pictonote.constants.filenameDateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

// function to save/overwrite the journal entry for the current date
fun saveLocalJournalEntry(context: Context, entry: String) {
    if (entry.isBlank()) {
        Log.w("SaveJournalEntry", "Attempted to save an empty entry. Overwriting if exists.")
    }

    try {
        val directory = File(context.filesDir, JOURNAL_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // create filename using the current date ONLY
        val todayDateString = LocalDate.now().format(filenameDateFormatter)
        val filename = "journal_$todayDateString.txt"
        val file = File(directory, filename)

        file.writeText(entry)

        Log.i("SaveJournalEntry", "Journal Entry Saved/Updated: ${file.absolutePath}")

    } catch (e: IOException) {
        Log.e("SaveJournalEntry", "Error saving journal entry to file", e)
    } catch (e: Exception) {
        Log.e("SaveJournalEntry", "An unexpected error occurred during saving", e)
    }
}

// Save a journal entry to Firebase Firestore
suspend fun saveRemoteJournalEntry(dateString: String, content: String): Boolean {
    return try {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("SaveRemoteJournalEntry", "No user is signed in")
            return false
        }

        val userId = user.uid
        val db = FirebaseFirestore.getInstance()
        val journalEntry = hashMapOf(
            "date" to dateString,
            "content" to content,
            "lastModified" to System.currentTimeMillis()
        )

        withContext(Dispatchers.IO) {
            db.collection("users")
                .document(userId)
                .collection("journal_entries")
                .document(dateString)
                .set(journalEntry)
                .await()
        }

        Log.i("SaveRemoteJournalEntry", "Entry for $dateString saved to Firestore")
        true
    } catch (e: Exception) {
        Log.e("SaveRemoteJournalEntry", "Error saving to Firestore: ${e.message}")
        false
    }
}

// For backwards compatibility with old function signature
fun saveRemoteJournalEntry() {
    Log.w("ArchiveFunctionality", "Using deprecated saveRemoteJournalEntry() - please update to the suspend version with parameters")
}

// Synchronize all local journal entries with Firestore
suspend fun synchronizeJournalEntries(
    context: Context,
    onProgress: (current: Int, total: Int, syncedEntries: Int, failedEntries: Int) -> Unit = { _, _, _, _ -> },
    onComplete: (success: Boolean, syncedEntries: Int, failedEntries: Int) -> Unit
) {
    try {
        val directory = File(context.filesDir, JOURNAL_DIR)
        if (!directory.exists() || !directory.isDirectory) {
            Log.w("SynchronizeJournalEntries", "Journal directory doesn't exist")
            onComplete(true, 0, 0)
            return
        }

        // Filter to get only valid journal entry files
        val journalFiles = directory.listFiles { file ->
            file.isFile && file.name.startsWith("journal_") && file.name.endsWith(".txt")
        } ?: emptyArray()

        if (journalFiles.isEmpty()) {
            Log.i("SynchronizeJournalEntries", "No journal entries to synchronize")
            onComplete(true, 0, 0)
            return
        }

        val syncedEntries = AtomicInteger(0)
        val failedEntries = AtomicInteger(0)
        val totalEntries = journalFiles.size

        for ((index, file) in journalFiles.withIndex()) {
            try {
                // Extract date from filename (journal_YYYY-MM-DD.txt -> YYYY-MM-DD)
                val dateString = file.name.substringAfter("journal_").substringBefore(".txt")
                val content = file.readText()

                val success = saveRemoteJournalEntry(dateString, content)
                if (success) {
                    syncedEntries.incrementAndGet()
                } else {
                    failedEntries.incrementAndGet()
                }

                // Update progress
                onProgress(index + 1, totalEntries, syncedEntries.get(), failedEntries.get())

            } catch (e: Exception) {
                Log.e("SynchronizeJournalEntries", "Error processing file ${file.name}: ${e.message}")
                failedEntries.incrementAndGet()
                onProgress(index + 1, totalEntries, syncedEntries.get(), failedEntries.get())
            }
        }

        val allSuccess = failedEntries.get() == 0
        Log.i("SynchronizeJournalEntries", "Synchronization completed: ${syncedEntries.get()} synced, ${failedEntries.get()} failed")
        onComplete(allSuccess, syncedEntries.get(), failedEntries.get())

    } catch (e: Exception) {
        Log.e("SynchronizeJournalEntries", "Synchronization failed: ${e.message}")
        onComplete(false, 0, 0)
    }
}

// For backwards compatibility with old function
fun Synchronize() {
    Log.w("ArchiveFunctionality", "Using deprecated Synchronize() function - please update to synchronizeJournalEntries")
}

// UI component for sync functionality
@Composable
fun SyncCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var syncStatus by remember { mutableStateOf("") }
    var syncDetails by remember { mutableStateOf("") }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "Cloud Synchronization",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Sync your journal entries with the cloud to access them on all your devices.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (syncStatus.isNotEmpty()) {
                Text(
                    syncStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (syncDetails.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        syncDetails,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (!isSyncing) {
                        isSyncing = true
                        syncStatus = "Syncing..."
                        syncDetails = ""

                        scope.launch {
                            synchronizeJournalEntries(
                                context = context,
                                onProgress = { current, total, synced, failed ->
                                    progress = current.toFloat() / total.toFloat()
                                    syncDetails = "Progress: $current/$total"
                                },
                                onComplete = { success, synced, failed ->
                                    isSyncing = false
                                    if (success) {
                                        syncStatus = "Sync completed successfully"
                                        syncDetails = "$synced entries synchronized"
                                    } else {
                                        syncStatus = "Sync completed with issues"
                                        syncDetails = "$synced entries synchronized, $failed failed"
                                    }
                                }
                            )
                        }
                    }
                },
                enabled = !isSyncing
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sync to Cloud")
                    if (isSyncing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}