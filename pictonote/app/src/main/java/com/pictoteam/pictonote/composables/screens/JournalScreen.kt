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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
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

val promptTypes = listOf("Default", "Reflective", "Creative", "Goal-Oriented", "Gratitude")
private const val MAX_JOURNAL_LENGTH = 10_000
const val IMAGE_AUTO_MINIMIZE_DELAY_MS = 5000L

// Helper class for rememberPrevious
private class PreviousHolder<T>(var value: T?)

// Helper Composable to remember the previous value of a non-null nullable type
@Composable
private fun <T> rememberPrevious(current: T): T? {
    val holder = remember { PreviousHolder<T>(null) }
    val previous = holder.value
    SideEffect {
        holder.value = current
    }
    return previous
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    navController: NavHostController,
    entryFilePathToEdit: String?, // This comes from navigation, stable across rotations for the same screen instance
    journalViewModel: JournalViewModel = viewModel(),
    geminiViewModel: GeminiViewModel = viewModel(),
    setCameraPreviewActive: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ViewModel states are automatically saved by the ViewModel's lifecycle
    val capturedImageUri by journalViewModel.capturedImageUri.collectAsStateWithLifecycle()
    val journalText by journalViewModel.journalText.collectAsStateWithLifecycle()
    val isSaving by journalViewModel.isSaving.collectAsStateWithLifecycle()
    val isEditingStateFlow = journalViewModel.isEditing // This is a StateFlow
    val isEditingValue by isEditingStateFlow.collectAsStateWithLifecycle() // Collect it as state

    // Gemini ViewModel states (LiveData observed, also retained by ViewModel)
    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("Click 'Prompt' for suggestion")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)

    // UI-specific states that need to be saved across configuration changes
    var isImageMinimized by rememberSaveable(isEditingValue) { mutableStateOf(false) }
    var isInitialImageDisplay by rememberSaveable { mutableStateOf(true) }
    var isPromptDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedPromptType by rememberSaveable { mutableStateOf(promptTypes[0]) }


    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    var hasCamPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamPermission = granted
        if (!granted) { Toast.makeText(context, "Camera permission is required to add photos.", Toast.LENGTH_LONG).show() }
    }

    val isCameraPreviewActuallyShowing = !isEditingValue && capturedImageUri == null && hasCamPermission
    LaunchedEffect(isCameraPreviewActuallyShowing) {
        setCameraPreviewActive(isCameraPreviewActuallyShowing)
    }
    DisposableEffect(Unit) {
        onDispose {
            setCameraPreviewActive(false)
        }
    }

    suspend fun <T> ListenableFuture<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addListener({
                try { continuation.resume(get()) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }, ContextCompat.getMainExecutor(context))
            continuation.invokeOnCancellation { cancel(true) }
        }
    }

    val previousEntryFilePathToEdit = rememberPrevious(current = entryFilePathToEdit)

    LaunchedEffect(entryFilePathToEdit) { // Keyed by entryFilePathToEdit
        Log.d("JournalScreen", "Effect triggered. Path: $entryFilePathToEdit, PrevPath: $previousEntryFilePathToEdit, isEditingValue: $isEditingValue")

        if (entryFilePathToEdit != null) {
            // Editing an existing entry
            // Load only if the path has actually changed OR if the ViewModel isn't already in editing mode for this path
            if (entryFilePathToEdit != previousEntryFilePathToEdit || journalViewModel.editingFilePathValue != entryFilePathToEdit) {
                Log.d("JournalScreen", "Loading entry for edit: $entryFilePathToEdit")
                isInitialImageDisplay = true // Reset for new loaded entry
                isImageMinimized = false    // Expand image for new loaded entry
                try {
                    val decodedPath = Uri.decode(entryFilePathToEdit)
                    journalViewModel.loadEntryForEditing(context, decodedPath)
                } catch (e: Exception) {
                    Log.e("JournalScreen", "Error decoding/loading path for editing: $entryFilePathToEdit", e)
                    Toast.makeText(context, "Error loading entry for editing.", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            } else {
                Log.d("JournalScreen", "Path $entryFilePathToEdit same as previous or VM already editing this path. ViewModel state preserved.")
            }
        } else {
            // New entry (entryFilePathToEdit is null)
            if (previousEntryFilePathToEdit != null) {
                // Navigated from an editing screen to a new entry screen
                Log.d("JournalScreen", "Navigated from edit (path: $previousEntryFilePathToEdit) to new entry. Clearing ViewModel.")
                journalViewModel.clearJournalState()
                isInitialImageDisplay = true // Reset for new entry screen
                isImageMinimized = false
            } else {
                // This is a new entry screen (could be initial load or rotation on new entry screen)
                // The ViewModel's state (text, image URI) should be preserved by the ViewModel itself across rotations.
                // We do NOT want to call clearJournalState() here if the user has already typed something.
                Log.d("JournalScreen", "New entry screen. ViewModel state (text, image) should be preserved across rotations.")
                // isInitialImageDisplay and isImageMinimized will be restored by rememberSaveable
            }
        }
    }


    LaunchedEffect(capturedImageUri, isEditingValue) {
        if (capturedImageUri != null && isInitialImageDisplay && !isEditingValue) {
            Log.d("JournalScreen", "Newly captured image detected, ensuring expanded state."); isImageMinimized = false; isInitialImageDisplay = false
        } else if (capturedImageUri == null) {
            // If image is cleared (e.g. cancel on new entry), reset initial display flag
            isInitialImageDisplay = true
        }
    }

    LaunchedEffect(Unit) { if (!hasCamPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(lifecycleOwner) { try { cameraProvider = cameraProviderFuture.await(); Log.d("JournalScreen", "CameraProvider obtained.") } catch (e: Exception) { Log.e("JournalScreen", "Failed to get CameraProvider.", e) } }

    LaunchedEffect(cameraProvider, hasCamPermission, lifecycleOwner) {
        if (hasCamPermission && cameraProvider != null) {
            try {
                cameraProvider?.unbindAll()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture); Log.d("JournalScreen", "CameraX use cases bound.")
            } catch (exc: Exception) { Log.e("JournalScreen", "CameraX use case binding failed", exc) }
        } else {
            Log.d("JournalScreen", "Skipping CameraX binding (Permission: $hasCamPermission, Provider: ${cameraProvider != null})")
        }
    }

    LaunchedEffect(isImageMinimized, capturedImageUri, isEditingValue) {
        if (!isImageMinimized && capturedImageUri != null && !isEditingValue) {
            Log.d("JournalScreen", "Auto-minimize timer started (delay: ${IMAGE_AUTO_MINIMIZE_DELAY_MS}ms).")
            delay(IMAGE_AUTO_MINIMIZE_DELAY_MS)
            if (isActive && !isImageMinimized && capturedImageUri != null && !isEditingValue) {
                Log.d("JournalScreen", "Auto-minimize timer finished, minimizing image.")
                isImageMinimized = true
            } else {
                Log.d("JournalScreen", "Auto-minimize timer cancelled or conditions changed.")
            }
        } else {
            Log.d("JournalScreen", "Auto-minimize timer conditions not met.")
        }
    }

    when {
        isCameraPreviewActuallyShowing -> {
            Box(Modifier.fillMaxSize()) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                Button(
                    onClick = {
                        if (cameraProvider != null && !isSaving) {
                            takePhoto(context, imageCapture, journalViewModel::onImageCaptured)
                        } else {
                            Log.w("JournalScreen", "Photo button clicked but provider null or saving.")
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).navigationBarsPadding().size(72.dp),
                    shape = CircleShape,
                    enabled = cameraProvider != null && !isSaving
                ) { Icon(Icons.Filled.PhotoCamera,
                    contentDescription = "Take Photo", modifier = Modifier.size(36.dp)) }
            }
        }

        capturedImageUri != null || isEditingValue -> {
            val screenPadding = if (LocalConfiguration.current.screenWidthDp >= 600) 24.dp else 16.dp
            Column(
                Modifier.fillMaxSize()
                    .padding(horizontal = screenPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
                    .imePadding()
            ) {
                if ((capturedImageUri == null && !isEditingValue) || (capturedImageUri == null && isEditingValue)) {
                    Spacer(Modifier.height(16.dp))
                } else if (capturedImageUri != null) {
                    Spacer(Modifier.height(8.dp))
                }

                if (capturedImageUri != null) {
                    AnimatedVisibility(visible = !isImageMinimized) {
                        Image(
                            painter = rememberAsyncImagePainter(model = capturedImageUri),
                            contentDescription = if (isEditingValue) "Journal image (non-editable)" else "Captured journal image",
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(bottom = 16.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .then(if (!isEditingValue) Modifier.clickable { isImageMinimized = true } else Modifier)
                                .then(if (isEditingValue) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.medium) else Modifier)
                        )
                    }
                    AnimatedVisibility(visible = isImageMinimized) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { isImageMinimized = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Image, "Show full image", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Image attached (tap to view)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else if (isEditingValue) {
                    Text("Editing text-only entry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally))
                }

                Text("Journal Entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                BasicTextField(
                    value = journalText,
                    onValueChange = { journalViewModel.updateJournalText(it) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (journalText.isEmpty()) Text("Add your thoughts...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                            innerTextField()
                        }
                    }
                )
                Text("${journalText.length} / $MAX_JOURNAL_LENGTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))


                Text("AI Assistance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                CollapsibleSuggestionCard(promptSuggestion, isLoadingPrompt)
                Spacer(Modifier.height(16.dp))

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
                    ) { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Prompt") }
                }
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val wasCurrentlyEditing = isEditingValue
                            journalViewModel.clearJournalState()
                            isImageMinimized = false
                            isInitialImageDisplay = true
                            if (wasCurrentlyEditing) navController.popBackStack()
                        },
                        enabled = !isSaving
                    ) { Icon(Icons.Default.Cancel, "Cancel", Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Cancel") }

                    Button(
                        onClick = {
                            journalViewModel.saveJournalEntry(context) { success, wasEditingMode ->
                                if (success) {
                                    isImageMinimized = false
                                    isInitialImageDisplay = true
                                    if (wasEditingMode) navController.popBackStack()
                                }
                            }
                        },
                        enabled = !isSaving && (isEditingValue || capturedImageUri != null || journalText.isNotBlank())
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(24.dp), MaterialTheme.colorScheme.onPrimary, 2.dp)
                        else { val buttonText = if (isEditingValue) "Update Entry" else "Save Entry"; Icon(Icons.Default.Check, buttonText, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text(buttonText) }
                    }
                }
            }
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val textToShow = when { !hasCamPermission -> "Camera permission needed to capture photos."; cameraProvider == null -> "Initializing Camera..."; else -> "Loading..." }
                    Text(textToShow, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(16.dp))
                    if (!hasCamPermission) Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                    else if (cameraProvider == null) CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun CollapsibleSuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var isOverflowing by remember(promptSuggestion) { mutableStateOf(false) }

    val showToggleButton = (isOverflowing || isExpanded) && !isLoadingPrompt

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
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
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    onTextLayout = { textLayoutResult: TextLayoutResult ->
                        isOverflowing = textLayoutResult.didOverflowHeight || textLayoutResult.lineCount > 1
                    }
                )

                if (isLoadingPrompt) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = 8.dp))
                } else if (showToggleButton) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse prompt" else "Expand prompt",
                        modifier = Modifier.size(24.dp).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

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