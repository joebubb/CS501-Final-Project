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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState // For observing LiveData values from ViewModel
import androidx.compose.runtime.saveable.rememberSaveable // For preserving state across configuration changes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // For accessing ViewModels in Composables
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.pictoteam.pictonote.constants.ARG_ENTRY_FILE_PATH
import com.pictoteam.pictonote.constants.IMAGE_URI_MARKER
import com.pictoteam.pictonote.constants.JOURNAL_IMAGE_DIR
import com.pictoteam.pictonote.constants.ROUTE_JOURNAL
import com.pictoteam.pictonote.constants.filenameDateTimeFormatter
import com.pictoteam.pictonote.constants.viewEntryDisplayDateTimeFormatter
import com.pictoteam.pictonote.model.GeminiViewModel // ViewModel for AI reflection functionality
import com.pictoteam.pictonote.model.JournalEntryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/**
 * Screen for viewing a journal entry in read-only mode
 * Displays both the entry content and an AI-generated reflection on the content
 * Also provides navigation to edit the entry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewEntryScreen(
    navController: NavHostController,
    encodedEntryFilePath: String?, // URL-encoded path to the journal entry file
    geminiViewModel: GeminiViewModel = viewModel() // For AI-powered reflection generation
) {
    val context = LocalContext.current
    var entryData by remember { mutableStateOf<JournalEntryData?>(null) } // Holds the loaded entry data
    var isLoading by rememberSaveable { mutableStateOf(true) } // Loading state preserved across configuration changes
    var errorLoading by rememberSaveable { mutableStateOf<String?>(null) } // Error message if loading fails
    var decodedFilePath by rememberSaveable { mutableStateOf<String?>(null) } // Stores the decoded file path

    // Observe AI reflection states from GeminiViewModel
    val reflectionResult by geminiViewModel.journalReflection.observeAsState("")
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)

    // Load entry data when screen is first composed or when the path changes
    LaunchedEffect(encodedEntryFilePath) {
        geminiViewModel.clearReflectionState() // Reset any previous reflection

        if (encodedEntryFilePath == null) {
            errorLoading = "Entry path missing."
            isLoading = false
            Log.e("ViewEntryScreen", "Encoded entry file path is null.")
            return@LaunchedEffect
        }

        // Track path changes to avoid unnecessary reloading
        // This is important for orientation changes where the path stays the same
        val previousDecodedPath = decodedFilePath
        var newDecodedPath: String? = null

        try {
            newDecodedPath = Uri.decode(encodedEntryFilePath)
            // Only reload if path changed or initial load
            if (newDecodedPath != previousDecodedPath || entryData == null) {
                isLoading = true
                errorLoading = null
                decodedFilePath = newDecodedPath
                Log.d("ViewEntryScreen", "Decoded file path: $newDecodedPath")

                // Load entry data from file system
                entryData = try {
                    loadJournalEntryData(context, newDecodedPath)
                } catch (e: IOException) {
                    Log.e("ViewEntryScreen", "IOException loading entry: ${e.message}", e)
                    errorLoading = "Could not load entry details."
                    null
                } catch (e: Exception) {
                    Log.e("ViewEntryScreen", "Error loading entry: ${e.message}", e)
                    errorLoading = "An unexpected error occurred."
                    null
                } finally {
                    isLoading = false
                }
            } else {
                // Path is the same, rely on saved state
                Log.d("ViewEntryScreen", "Path $newDecodedPath is same as previous $previousDecodedPath, or data already present. Relying on saved state for isLoading/errorLoading.")
            }
        } catch (e: Exception) {
            Log.e("ViewEntryScreen", "Error decoding file path '$encodedEntryFilePath': ${e.message}", e)
            errorLoading = "Invalid entry identifier."
            isLoading = false
            decodedFilePath = null // Reset path on error
            entryData = null // Clear any stale data
        }
    }

    // Clean up reflection state when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            geminiViewModel.clearReflectionState()
        }
    }

    // Main screen layout with top app bar
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
                    // Edit button only appears if entry is successfully loaded
                    if (entryData != null && decodedFilePath != null) {
                        IconButton(onClick = {
                            try {
                                // Re-encode the path for navigation parameter
                                val reEncodedPath = Uri.encode(decodedFilePath)
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
        // Content area with scrolling
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Show different views based on loading state
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorLoading != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorLoading!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                entryData != null -> {
                    // Entry content followed by AI reflection section
                    EntryContentView(context, entryData!!)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "AI Reflection",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ReflectionViewCard(
                        reflectionResult = reflectionResult,
                        isLoadingReflection = isLoadingReflection
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            geminiViewModel.reflectOnJournalEntry(entryData!!.textContent)
                        },
                        enabled = !isLoadingReflection && entryData!!.textContent.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Reflect")
                    }
                }
                else -> {
                    // Fallback for unexpected state (no error but no data)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Entry not found or could not be loaded.")
                    }
                }
            }
        }
    }
}

/**
 * Displays the journal entry content (image and text)
 * Shows appropriate placeholders for missing content
 */
@Composable
private fun EntryContentView(context: Context, entryData: JournalEntryData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Resolve image file path if present in entry data
        val imageFile: File? = remember(entryData.imageRelativePath) {
            entryData.imageRelativePath?.let { File(context.filesDir, it) }
        }

        // Image display with error handling
        if (imageFile?.exists() == true) {
            Image(
                painter = rememberAsyncImagePainter(model = imageFile),
                contentDescription = "Journal image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )
        } else if (entryData.imageRelativePath != null) {
            // Show error if image path exists but file is missing
            Text(
                "Image file missing (${entryData.imageRelativePath})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Text content display with placeholder for empty entries
        if (entryData.textContent.isNotBlank()) {
            Text(
                text = entryData.textContent,
                style = MaterialTheme.typography.bodyLarge
            )
        } else if (imageFile?.exists() != true) {
            // Only show "empty" message if there's no image or text
            Text(
                "This entry is empty.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Card displaying AI-generated reflection on the journal entry
 * Shows loading indicator, placeholder, or actual reflection
 */
@Composable
fun ReflectionViewCard(reflectionResult: String, isLoadingReflection: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Choose appropriate text based on state
            val textToShow = when {
                isLoadingReflection -> "Generating reflection..."
                reflectionResult.isBlank() -> "Click 'Reflect' for AI insights on this entry."
                else -> reflectionResult
            }
            Text(
                text = textToShow,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            )
            // Show loading indicator while generating reflection
            if (isLoadingReflection) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * Loads a journal entry file from the file system and parses its contents
 * Extracts image path, text content, and timestamp information
 * Returns structured JournalEntryData or throws exceptions for IO errors
 */
private suspend fun loadJournalEntryData(context: Context, filePath: String): JournalEntryData = withContext(Dispatchers.IO) {
    val entryFile = File(filePath)
    if (!entryFile.exists() || !entryFile.isFile) {
        throw IOException("Entry file not found or is not a file: $filePath")
    }

    var imageRelativePath: String? = null
    var textContent = ""
    var fileTimestamp: LocalDateTime? = null

    try {
        // Extract timestamp from filename using the defined formatter
        try {
            val timestampStr = entryFile.name.substringAfter("journal_").substringBefore(".txt")
            fileTimestamp = LocalDateTime.parse(timestampStr, filenameDateTimeFormatter)
        } catch (e: Exception) {
            Log.w("LoadEntryData", "Could not parse timestamp from filename: ${entryFile.name}", e)
        }

        // Read file contents line by line
        val lines = entryFile.readLines()
        // Check for image marker in first line
        val imageLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }

        // Extract image path if present
        if (imageLine != null) {
            val path = imageLine.substringAfter(IMAGE_URI_MARKER).trim()
            if (path.startsWith(JOURNAL_IMAGE_DIR)) {
                imageRelativePath = path
            } else {
                Log.w("LoadEntryData", "Image path in file seems invalid: $path")
            }
        }

        // Text content is everything after the image marker line (if present)
        textContent = lines.drop(if (imageLine != null) 1 else 0).joinToString("\n")

        Log.d("LoadEntryData", "Loaded entry $filePath. Image: $imageRelativePath, Text length: ${textContent.length}")
        JournalEntryData(filePath, fileTimestamp, imageRelativePath, textContent)

    } catch (e: Exception) {
        Log.e("LoadEntryData", "Error reading or parsing file content: $filePath", e)
        throw IOException("Failed to read entry content from $filePath", e)
    }
}