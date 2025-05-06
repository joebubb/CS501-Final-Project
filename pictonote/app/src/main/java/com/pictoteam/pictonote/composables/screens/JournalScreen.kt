// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/JournalScreen.kt
package com.pictoteam.pictonote.composables.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ExpandLess // Import icons for expand/collapse
import androidx.compose.material.icons.outlined.ExpandMore // Import icons for expand/collapse
import androidx.compose.material.icons.outlined.Image // Import image icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.google.common.util.concurrent.ListenableFuture
import com.pictoteam.pictonote.model.GeminiViewModel
import com.pictoteam.pictonote.model.JournalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File

// Define prompt types here or import from Constants
val promptTypes = listOf("Default", "Reflective", "Creative", "Goal-Oriented", "Gratitude")

// Define a maximum character limit for the journal entry
private const val MAX_JOURNAL_LENGTH = 5000 // Adjust as needed
private const val IMAGE_MINIMIZE_DELAY_MS = 5000L // 5 seconds
private const val PROMPT_EXPAND_THRESHOLD = 500 // Expand prompt by default if under 80 chars

@OptIn(ExperimentalMaterial3Api::class) // Needed for ExposedDropdownMenuBox
@Composable
fun JournalScreen(
    navController: NavHostController,
    entryFilePathToEdit: String?, // This comes from NavArguments, null for new entry
    journalViewModel: JournalViewModel = viewModel(),
    geminiViewModel: GeminiViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // --- State Declarations ---
    val capturedImageUri by journalViewModel.capturedImageUri.collectAsStateWithLifecycle()
    val journalText by journalViewModel.journalText.collectAsStateWithLifecycle()
    val isSaving by journalViewModel.isSaving.collectAsStateWithLifecycle()
    val isEditing by journalViewModel.isEditing.collectAsStateWithLifecycle() // Tracks if editing specific entry

    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("Click 'Prompt' for suggestion")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)

    // --- Local UI State ---
    var isImageMinimized by remember { mutableStateOf(false) }
    LaunchedEffect(capturedImageUri, isEditing) {
        isImageMinimized = false
    }

    // Camera related state
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    // Permission state
    var hasCamPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    // Dropdown Menu State
    var isPromptDropdownExpanded by remember { mutableStateOf(false) }
    var selectedPromptType by remember { mutableStateOf(promptTypes[0]) }


    // --- Helper Functions ---
    suspend fun <T> ListenableFuture<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addListener({
                try { continuation.resume(get()) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }, ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { cancel(true) }
        }
    }

    // --- Effects ---
    // Load/Clear state based on navigation argument
    LaunchedEffect(entryFilePathToEdit, lifecycleOwner) {
        if (entryFilePathToEdit != null) {
            Log.d("JournalScreen", "Effect: Edit mode. Received path (encoded): $entryFilePathToEdit")
            try {
                val decodedPath = Uri.decode(entryFilePathToEdit)
                journalViewModel.loadEntryForEditing(context, decodedPath)
            } catch (e: IllegalArgumentException) {
                Log.e("JournalScreen", "Error decoding file path for editing: $entryFilePathToEdit", e)
                Toast.makeText(context, "Error: Invalid entry identifier.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            } catch (e: Exception) {
                Log.e("JournalScreen", "Error loading entry for editing: $entryFilePathToEdit", e)
                Toast.makeText(context, "Error loading entry for editing.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        } else {
            if (journalViewModel.isEditing.value) {
                Log.w("JournalScreen", "Effect: New entry screen, but ViewModel was in edit mode. Clearing ViewModel.")
                journalViewModel.clearJournalState()
            } else {
                Log.d("JournalScreen", "Effect: New entry screen. Using existing ViewModel state (if any).")
            }
        }
    }

    // Camera Permissions and Binding
    LaunchedEffect(Unit) { if (!hasCamPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(lifecycleOwner) { try { cameraProvider = cameraProviderFuture.await() } catch (e: Exception) { Log.e("JournalScreen", "Cam provider fail", e) } }
    LaunchedEffect(cameraProvider, hasCamPermission, lifecycleOwner) {
        if (hasCamPermission && cameraProvider != null) {
            try {
                cameraProvider?.unbindAll()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) { Log.e("JournalScreen", "Cam bind fail", exc) }
        }
    }

    // Image Minimization Timer Effect
    LaunchedEffect(capturedImageUri, isEditing, isImageMinimized) {
        if (capturedImageUri != null && !isEditing && !isImageMinimized) {
            Log.d("JournalScreen", "Starting image minimize timer.")
            delay(IMAGE_MINIMIZE_DELAY_MS)
            if (isActive && capturedImageUri != null && !isEditing && !isImageMinimized) {
                Log.d("JournalScreen", "Timer finished, minimizing image.")
                isImageMinimized = true
            } else {
                Log.d("JournalScreen", "Timer cancelled or state changed before completion.")
            }
        } else {
            if (capturedImageUri != null) {
                Log.d("JournalScreen", "Image minimize timer not started (isEditing: $isEditing, isMinimized: $isImageMinimized)")
            }
        }
    }


    // --- UI Composition ---
    when {
        // State 1: Camera Preview
        !isEditing && capturedImageUri == null && hasCamPermission -> {
            Box(Modifier.fillMaxSize()) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                Button(
                    onClick = { if (cameraProvider != null && !isSaving) takePhoto(context, imageCapture, journalViewModel::onImageCaptured) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).navigationBarsPadding().size(72.dp),
                    shape = CircleShape,
                    enabled = cameraProvider != null && !isSaving
                ) { Icon(Icons.Filled.PhotoCamera, "Take Photo", modifier = Modifier.size(36.dp)) }
            }
        }

        // State 2: Entry Form
        capturedImageUri != null || isEditing -> {
            val screenPadding = if (LocalConfiguration.current.screenWidthDp >= 600) 24.dp else 16.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = screenPadding)
                    .padding(bottom = 16.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // --- Image Section ---
                if (capturedImageUri != null) {
                    AnimatedVisibility(visible = !isImageMinimized) {
                        Image(
                            painter = rememberAsyncImagePainter(model = capturedImageUri),
                            contentDescription = if (isEditing) "Journal image (non-editable)" else "Captured journal image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .padding(bottom = 16.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .then(
                                    if (!isEditing) Modifier.clickable { isImageMinimized = true } else Modifier
                                )
                                .then(
                                    if (isEditing) Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        MaterialTheme.shapes.medium
                                    ) else Modifier
                                )
                        )
                    }
                    AnimatedVisibility(visible = isImageMinimized) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .clickable { isImageMinimized = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = "Show full image",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Image attached (tap to view)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                } else if (isEditing) {
                    Text("Editing text-only entry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally))
                }
                // --- End Image Section ---


                // --- Journal Text Input ---
                Text("Journal Entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .defaultMinSize(minHeight = 150.dp)
                ) {
                    BasicTextField(
                        value = journalText,
                        onValueChange = { newText ->
                            if (newText.length <= MAX_JOURNAL_LENGTH) {
                                journalViewModel.updateJournalText(newText)
                            } else {
                                Toast.makeText(context, "Character limit ($MAX_JOURNAL_LENGTH) reached", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (journalText.isEmpty()) {
                                    Text("Add your thoughts...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Text(
                    text = "${journalText.length} / $MAX_JOURNAL_LENGTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
                Spacer(Modifier.height(16.dp))
                // --- End Journal Text Input ---


                // --- AI Assistance Section ---
                Text("AI Assistance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                CollapsibleSuggestionCard(promptSuggestion, isLoadingPrompt) // Use new collapsible card
                Spacer(Modifier.height(16.dp))

                // Row for Prompt Type Dropdown and Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = isPromptDropdownExpanded,
                        onExpandedChange = { isPromptDropdownExpanded = !isPromptDropdownExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedPromptType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Prompt Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPromptDropdownExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = isPromptDropdownExpanded,
                            onDismissRequest = { isPromptDropdownExpanded = false }
                        ) {
                            promptTypes.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        selectedPromptType = selectionOption
                                        isPromptDropdownExpanded = false
                                        Log.d("JournalScreen", "Selected prompt type: $selectedPromptType")
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { geminiViewModel.suggestJournalPrompt(selectedPromptType) },
                        enabled = !isLoadingPrompt && !isSaving
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Prompt")
                    }
                }
                // --- End AI Assistance Section ---


                Spacer(Modifier.height(32.dp))

                // --- Cancel / Save/Update Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val wasCurrentlyEditing = isEditing
                            journalViewModel.clearJournalState()
                            if (wasCurrentlyEditing) {
                                navController.popBackStack()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Cancel, "Cancel", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            journalViewModel.saveJournalEntry(context) { success, wasEditingMode ->
                                if (success) {
                                    if (wasEditingMode) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        },
                        enabled = !isSaving && (isEditing || capturedImageUri != null || journalText.isNotBlank())
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            val buttonText = if (isEditing) "Update Entry" else "Save Entry"
                            Icon(Icons.Default.Check, buttonText, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(buttonText)
                        }
                    }
                }
                // --- End Buttons ---
            } // End Main Column
        }

        // State 3: Fallback
        else -> {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val textToShow = when {
                        !hasCamPermission -> "Camera permission needed to capture photos."
                        cameraProvider == null -> "Initializing Camera..."
                        else -> "Loading..."
                    }
                    Text(textToShow, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(16.dp))
                    if (!hasCamPermission) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                    } else if (cameraProvider == null) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

// --- Helper Composables ---

@Composable
fun CollapsibleSuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    // Initialize 'isExpanded' based on prompt length threshold and loading state
    var isExpanded by remember(promptSuggestion, isLoadingPrompt) { // Key includes isLoadingPrompt
        mutableStateOf(promptSuggestion.length < PROMPT_EXPAND_THRESHOLD && !isLoadingPrompt)
    }
    var isOverflowing by remember { mutableStateOf(false) } // Track if text overflows one line when collapsed

    // Determine if the toggle button should be shown based on overflow or expanded state
    // (and not currently loading)
    val showToggleButton = (isOverflowing || isExpanded) && !isLoadingPrompt

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                // Only make clickable if the button is actually shown
                .clickable(enabled = showToggleButton) { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = promptSuggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1, // Expand lines when state is true
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    onTextLayout = { textLayoutResult: TextLayoutResult ->
                        // Check if height overflowed OR if more than one line was needed when collapsed
                        isOverflowing = textLayoutResult.didOverflowHeight || textLayoutResult.lineCount > 1
                    }
                )

                // Conditionally display either the loading indicator or the expand/collapse icon
                if (isLoadingPrompt) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = 8.dp))
                } else if (showToggleButton) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse prompt" else "Expand prompt",
                        modifier = Modifier.size(24.dp).padding(start = 8.dp) // Keep consistent size/padding
                    )
                }
            }
        }
    }
}


// --- Helper Function (takePhoto - no changes) ---
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageSaved: (Uri) -> Unit
) {
    val cacheDir = context.cacheDir ?: run {
        Log.e("TakePhoto", "Cache directory is null.")
        Toast.makeText(context, "Storage Error", Toast.LENGTH_SHORT).show()
        return
    }
    if (!cacheDir.exists()) { cacheDir.mkdirs() }
    val photoFile = File(cacheDir, "PICNOTE_IMG_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("JournalScreen", "Photo capture failed: ${exc.message}", exc)
                Toast.makeText(context, "Photo Capture Error: ${exc.message}", Toast.LENGTH_LONG).show()
            }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                Log.d("JournalScreen", "Photo capture succeeded: $savedUri")
                onImageSaved(savedUri)
            }
        }
    )
}