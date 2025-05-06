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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState // Keep this for prompt suggestion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.SolidColor // Not needed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
// import androidx.compose.ui.text.TextStyle // Not needed
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File

// Define prompt types here or import from Constants
val promptTypes = listOf("Default", "Reflective", "Creative", "Goal-Oriented", "Gratitude")

@OptIn(ExperimentalMaterial3Api::class) // Needed for ExposedDropdownMenuBox
@Composable
fun JournalScreen(
    navController: NavHostController,
    entryFilePathToEdit: String?,
    journalViewModel: JournalViewModel = viewModel(),
    geminiViewModel: GeminiViewModel = viewModel() // Still need for Prompt
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // --- State Declarations ---
    val capturedImageUri by journalViewModel.capturedImageUri.collectAsStateWithLifecycle()
    val journalText by journalViewModel.journalText.collectAsStateWithLifecycle()
    val isSaving by journalViewModel.isSaving.collectAsStateWithLifecycle()
    val isEditing by journalViewModel.isEditing.collectAsStateWithLifecycle()

    // Only observe Prompt related state from GeminiViewModel
    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("Click 'Prompt' for suggestion")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)
    // REMOVED: reflectionResult and isLoadingReflection state observation

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
        // ... (implementation as before)
        return suspendCancellableCoroutine { continuation ->
            addListener({
                try { continuation.resume(get()) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }, ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { cancel(true) }
        }
    }

    // --- Effects ---
    LaunchedEffect(entryFilePathToEdit, journalViewModel) {
        // ... (load/clear logic remains the same) ...
        if (entryFilePathToEdit != null) {
            Log.d("JournalScreen", "Effect: Received potential edit path (encoded): $entryFilePathToEdit")
            try {
                val decodedPath = Uri.decode(entryFilePathToEdit)
                Log.d("JournalScreen", "Effect: Decoded path for loading: $decodedPath")
                journalViewModel.loadEntryForEditing(context, decodedPath)
            } catch (e: IllegalArgumentException) {
                Log.e("JournalScreen", "Error decoding file path for editing: $entryFilePathToEdit", e)
                Toast.makeText(context, "Error: Invalid entry identifier.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            catch (e: Exception) {
                Log.e("JournalScreen", "Error loading entry for editing: $entryFilePathToEdit", e)
                Toast.makeText(context, "Error loading entry for editing.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        } else {
            if (!journalViewModel.isNewEntryState() && !journalViewModel.isEditing.value) {
                Log.d("JournalScreen", "Effect: Clearing state for new entry.")
                journalViewModel.clearJournalState()
            } else {
                Log.d("JournalScreen", "Effect: State likely already correct for new entry or ongoing edit.")
            }
        }
    }

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
                Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = screenPadding)
                    .verticalScroll(rememberScrollState()).padding(bottom = 16.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // Image Display
                // ... (no changes) ...
                if (capturedImageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = capturedImageUri),
                        contentDescription = if (isEditing) "Journal image (non-editable)" else "Captured journal image",
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(bottom = 16.dp)
                            .then(if (isEditing) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else Modifier)
                    )
                } else if (isEditing) {
                    Text("Editing text-only entry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally))
                }


                // Journal Text Input
                // ... (no changes) ...
                Text("Journal Entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                BasicTextField(
                    value = journalText,
                    onValueChange = { journalViewModel.updateJournalText(it) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp)
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
                Spacer(Modifier.height(24.dp))

                // AI Assistance Section
                Text("AI Assistance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                SuggestionCard(promptSuggestion, isLoadingPrompt) // Displays the generated prompt
                Spacer(Modifier.height(16.dp))

                // Row for Prompt Type Dropdown and Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Prompt Type Dropdown
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
                                    }
                                )
                            }
                        }
                    }

                    // Prompt Button - Adjusted enabled logic
                    Button(
                        onClick = { geminiViewModel.suggestJournalPrompt(selectedPromptType) },
                        enabled = !isLoadingPrompt && !isSaving // Remove isLoadingReflection check
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Prompt")
                    }
                }
                // --- End Row ---

                // REMOVED: Spacer, ReflectionCard, Reflect Button

                Spacer(Modifier.height(32.dp)) // Keep spacer before final buttons

                // Cancel / Save/Update Buttons
                // ... (no changes needed in this row) ...
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = {
                            if (isEditing) {
                                navController.popBackStack()
                                journalViewModel.clearJournalState()
                            } else {
                                journalViewModel.clearJournalState()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Cancel, "Cancel", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Cancel")
                    }

                    // Save / Update Button
                    Button(
                        onClick = {
                            journalViewModel.saveJournalEntry(context) { success, wasEditingMode ->
                                if (success) {
                                    if (wasEditingMode) {
                                        navController.popBackStack()
                                    }
                                    // State is cleared in VM on success
                                }
                                // Failure case handled in VM (isSaving becomes false)
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
            }
        }

        // State 3: Fallback
        else -> {
            // ... (no changes needed here) ...
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

// --- Helper Composables (Only SuggestionCard is needed now) ---

@Composable
fun SuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    // ... (no changes needed here) ...
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = promptSuggestion,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
            )
            if (isLoadingPrompt) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

// REMOVED: ReflectionCard composable

// --- Helper Function (takePhoto - no changes) ---
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageSaved: (Uri) -> Unit
) {
    // ... (no changes needed here) ...
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