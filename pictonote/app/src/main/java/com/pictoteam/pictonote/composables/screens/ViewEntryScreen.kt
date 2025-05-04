// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/ViewEntryScreen.kt
package com.pictoteam.pictonote.composables.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Use auto-mirrored icon
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.pictoteam.pictonote.constants.ARG_ENTRY_FILE_PATH
import com.pictoteam.pictonote.constants.IMAGE_URI_MARKER
import com.pictoteam.pictonote.constants.JOURNAL_IMAGE_DIR // Need this for resolving image path
import com.pictoteam.pictonote.constants.ROUTE_JOURNAL
import com.pictoteam.pictonote.constants.filenameDateTimeFormatter
import com.pictoteam.pictonote.constants.viewEntryDisplayDateTimeFormatter
import com.pictoteam.pictonote.model.JournalEntryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewEntryScreen(
    navController: NavHostController,
    encodedEntryFilePath: String? // Receive encoded path
) {
    val context = LocalContext.current
    var entryData by remember { mutableStateOf<JournalEntryData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorLoading by remember { mutableStateOf<String?>(null) }
    var decodedFilePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(encodedEntryFilePath) {
        if (encodedEntryFilePath == null) {
            errorLoading = "Entry path missing."
            isLoading = false
            Log.e("ViewEntryScreen", "Encoded entry file path is null.")
            return@LaunchedEffect
        }

        try {
            // Decode the file path received from navigation
            val decodedPath = Uri.decode(encodedEntryFilePath)
            decodedFilePath = decodedPath // Store decoded path for later use (Edit button)
            Log.d("ViewEntryScreen", "Decoded file path: $decodedPath")

            isLoading = true
            errorLoading = null
            entryData = try {
                loadJournalEntryData(context, decodedPath)
            } catch (e: IOException) {
                Log.e("ViewEntryScreen", "IOException loading entry: ${e.message}", e)
                errorLoading = "Could not load entry details. File might be corrupted or missing."
                null
            } catch (e: Exception) {
                Log.e("ViewEntryScreen", "Error loading entry: ${e.message}", e)
                errorLoading = "An unexpected error occurred while loading the entry."
                null
            } finally {
                isLoading = false
            }
        } catch (e: Exception) {
            // Error during decoding
            Log.e("ViewEntryScreen", "Error decoding file path '$encodedEntryFilePath': ${e.message}", e)
            errorLoading = "Invalid entry identifier."
            isLoading = false
            decodedFilePath = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryData?.fileTimestamp?.format(viewEntryDisplayDateTimeFormatter) ?: "View Entry") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show Edit button only if entry loaded successfully and we have a valid path
                    if (entryData != null && decodedFilePath != null) {
                        IconButton(onClick = {
                            try {
                                // Re-encode the path for safe navigation
                                val reEncodedPath = Uri.encode(decodedFilePath)
                                // Navigate to JournalScreen for editing
                                navController.navigate("$ROUTE_JOURNAL?$ARG_ENTRY_FILE_PATH=$reEncodedPath")
                            } catch (e: Exception) {
                                Log.e("ViewEntryScreen", "Error encoding/navigating to edit: ${e.message}")
                                Toast.makeText(context, "Error opening editor", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Entry")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp), // Add specific screen padding
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                errorLoading != null -> Text(errorLoading!!, color = MaterialTheme.colorScheme.error)
                entryData != null -> EntryContentView(context, entryData!!)
                else -> Text("Entry not found or could not be loaded.") // Fallback
            }
        }
    }
}

@Composable
private fun EntryContentView(context: Context, entryData: JournalEntryData) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image Display (if available)
        val imageFile: File? = remember(entryData.imageRelativePath) {
            entryData.imageRelativePath?.let { File(context.filesDir, it) }
        }

        if (imageFile?.exists() == true) {
            Image(
                painter = rememberAsyncImagePainter(model = imageFile),
                contentDescription = "Journal image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f) // Maintain aspect ratio
                    .padding(bottom = 8.dp) // Add some space below image
            )
        } else if (entryData.imageRelativePath != null) {
            // Indicate if image was expected but not found
            Text(
                "Image file missing (${entryData.imageRelativePath})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Text Content Display
        if (entryData.textContent.isNotBlank()) {
            Text(
                text = entryData.textContent,
                style = MaterialTheme.typography.bodyLarge
            )
        } else if (imageFile?.exists() != true) {
            // Handle case where entry has neither image nor text
            Text(
                "This entry is empty.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // If only image exists, no extra text needed here as the image is shown above.
    }
}


// Helper function to load data for a specific entry file
// Adapted from JournalViewModel and ArchiveScreen
private suspend fun loadJournalEntryData(context: Context, filePath: String): JournalEntryData = withContext(Dispatchers.IO) {
    val entryFile = File(filePath)
    if (!entryFile.exists() || !entryFile.isFile) {
        throw IOException("Entry file not found or is not a file: $filePath")
    }

    var imageRelativePath: String? = null
    var textContent = ""
    var fileTimestamp: LocalDateTime? = null

    try {
        // Attempt to parse timestamp from filename
        try {
            val timestampStr = entryFile.name.substringAfter("journal_").substringBefore(".txt")
            fileTimestamp = LocalDateTime.parse(timestampStr, filenameDateTimeFormatter)
        } catch (e: Exception) {
            Log.w("LoadEntryData", "Could not parse timestamp from filename: ${entryFile.name}", e)
            // Continue without timestamp if parsing fails
        }

        // Read file content
        val lines = entryFile.readLines()
        val imageLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }

        if (imageLine != null) {
            val path = imageLine.substringAfter(IMAGE_URI_MARKER).trim()
            // Basic validation: check if it looks like a path within our image dir
            if (path.startsWith(JOURNAL_IMAGE_DIR)) {
                imageRelativePath = path
            } else {
                Log.w("LoadEntryData", "Image path in file seems invalid: $path")
            }
        }

        textContent = lines.drop(if (imageLine != null) 1 else 0).joinToString("\n")

        Log.d("LoadEntryData", "Loaded entry $filePath. Image: $imageRelativePath, Text length: ${textContent.length}")
        JournalEntryData(filePath, fileTimestamp, imageRelativePath, textContent)

    } catch (e: Exception) {
        Log.e("LoadEntryData", "Error reading or parsing file content: $filePath", e)
        throw IOException("Failed to read entry content from $filePath", e) // Rethrow as IOException or custom exception
    }
}