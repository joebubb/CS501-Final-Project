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
import androidx.compose.runtime.livedata.observeAsState // Import observeAsState
import androidx.compose.runtime.saveable.rememberSaveable // Added import
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.pictoteam.pictonote.constants.ARG_ENTRY_FILE_PATH
import com.pictoteam.pictonote.constants.IMAGE_URI_MARKER
import com.pictoteam.pictonote.constants.JOURNAL_IMAGE_DIR
import com.pictoteam.pictonote.constants.ROUTE_JOURNAL
import com.pictoteam.pictonote.constants.filenameDateTimeFormatter
import com.pictoteam.pictonote.constants.viewEntryDisplayDateTimeFormatter
import com.pictoteam.pictonote.model.GeminiViewModel // Import GeminiViewModel
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
    encodedEntryFilePath: String?,
    geminiViewModel: GeminiViewModel = viewModel() // Inject GeminiViewModel
) {
    val context = LocalContext.current
    var entryData by remember { mutableStateOf<JournalEntryData?>(null) } // Data reloaded via LaunchedEffect
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var errorLoading by rememberSaveable { mutableStateOf<String?>(null) }
    var decodedFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    // Observe reflection state from GeminiViewModel
    val reflectionResult by geminiViewModel.journalReflection.observeAsState("")
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)

    LaunchedEffect(encodedEntryFilePath) {
        geminiViewModel.clearReflectionState()

        if (encodedEntryFilePath == null) {
            errorLoading = "Entry path missing."
            isLoading = false
            Log.e("ViewEntryScreen", "Encoded entry file path is null.")
            return@LaunchedEffect
        }

        // If there's a new encodedEntryFilePath, reset loading states
        // If it's the same path (e.g. rotation), rememberSaveable handles retaining state for isLoading/errorLoading
        // However, we always want to try loading if the path is new or state was not saved.
        val previousDecodedPath = decodedFilePath // Get current value before trying to decode new one
        var newDecodedPath: String? = null

        try {
            newDecodedPath = Uri.decode(encodedEntryFilePath)
            // Only reset loading flags if the path actually changed or if it's the initial load
            if (newDecodedPath != previousDecodedPath || entryData == null) {
                isLoading = true
                errorLoading = null
                decodedFilePath = newDecodedPath // Store the successfully decoded path
                Log.d("ViewEntryScreen", "Decoded file path: $newDecodedPath")

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
                // Path is the same, and data was likely already loaded and screen state (isLoading/errorLoading) restored by rememberSaveable
                Log.d("ViewEntryScreen", "Path $newDecodedPath is same as previous $previousDecodedPath, or data already present. Relying on saved state for isLoading/errorLoading.")
            }
        } catch (e: Exception) {
            Log.e("ViewEntryScreen", "Error decoding file path '$encodedEntryFilePath': ${e.message}", e)
            errorLoading = "Invalid entry identifier."
            isLoading = false
            decodedFilePath = null // Ensure decodedFilePath is reset on error
            entryData = null // Clear any stale data
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            geminiViewModel.clearReflectionState()
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
                    if (entryData != null && decodedFilePath != null) {
                        IconButton(onClick = {
                            try {
                                // Re-encode the successfully decoded and stored file path
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Entry not found or could not be loaded.")
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryContentView(context: Context, entryData: JournalEntryData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val imageFile: File? = remember(entryData.imageRelativePath) {
            entryData.imageRelativePath?.let { File(context.filesDir, it) }
        }

        if (imageFile?.exists() == true) {
            Image(
                painter = rememberAsyncImagePainter(model = imageFile),
                contentDescription = "Journal image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )
        } else if (entryData.imageRelativePath != null) {
            Text(
                "Image file missing (${entryData.imageRelativePath})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (entryData.textContent.isNotBlank()) {
            Text(
                text = entryData.textContent,
                style = MaterialTheme.typography.bodyLarge
            )
        } else if (imageFile?.exists() != true) {
            Text(
                "This entry is empty.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
            if (isLoadingReflection) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}


private suspend fun loadJournalEntryData(context: Context, filePath: String): JournalEntryData = withContext(Dispatchers.IO) {
    val entryFile = File(filePath)
    if (!entryFile.exists() || !entryFile.isFile) {
        throw IOException("Entry file not found or is not a file: $filePath")
    }

    var imageRelativePath: String? = null
    var textContent = ""
    var fileTimestamp: LocalDateTime? = null

    try {
        try {
            val timestampStr = entryFile.name.substringAfter("journal_").substringBefore(".txt")
            fileTimestamp = LocalDateTime.parse(timestampStr, filenameDateTimeFormatter)
        } catch (e: Exception) {
            Log.w("LoadEntryData", "Could not parse timestamp from filename: ${entryFile.name}", e)
        }

        val lines = entryFile.readLines()
        val imageLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }

        if (imageLine != null) {
            val path = imageLine.substringAfter(IMAGE_URI_MARKER).trim()
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
        throw IOException("Failed to read entry content from $filePath", e)
    }
}