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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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

@Composable
fun JournalScreen(
    navController: NavHostController,
    entryFilePathToEdit: String?,
    journalViewModel: JournalViewModel = viewModel(),
    geminiViewModel: GeminiViewModel = viewModel()
) {
    val context = LocalContext.current // Context is available here
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    suspend fun <T> ListenableFuture<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addListener({
                try { continuation.resume(get()) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }, ContextCompat.getMainExecutor(context))

            continuation.invokeOnCancellation { cancel(true) }
        }
    }

    val capturedImageUri by journalViewModel.capturedImageUri.collectAsStateWithLifecycle()
    val journalText by journalViewModel.journalText.collectAsStateWithLifecycle()
    val isSaving by journalViewModel.isSaving.collectAsStateWithLifecycle()
    val isEditing by journalViewModel.isEditing.collectAsStateWithLifecycle()

    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("Click 'Prompt' for suggestion")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)
    val reflectionResult by geminiViewModel.journalReflection.observeAsState("")
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    var hasCamPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCamPermission = granted }


    // Effect to load entry or clear state
    LaunchedEffect(entryFilePathToEdit, journalViewModel) {
        if (entryFilePathToEdit != null) {
            Log.d("JournalScreen", "Effect: Loading entry: $entryFilePathToEdit")
            try {
                val decodedPath = Uri.decode(entryFilePathToEdit)
                journalViewModel.loadEntryForEditing(context, decodedPath)
            } catch (e: Exception) {
                Log.e("JournalScreen", "Error decoding file path: $entryFilePathToEdit", e)
                Toast.makeText(context, "Error loading entry.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        } else {
            if (!journalViewModel.isNewEntryState()) {
                Log.d("JournalScreen", "Effect: Clearing state for new entry.")
                journalViewModel.clearJournalState()
            }
        }
    }

    // Request permission
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // Get CameraProvider instance
    LaunchedEffect(lifecycleOwner) {
        try {
            cameraProvider = cameraProviderFuture.await()
            Log.d("JournalScreen", "CameraProvider obtained.")
        } catch (e: Exception) { Log.e("JournalScreen", "Failed to get CameraProvider.", e) }
    }

    // Bind CameraX use cases
    LaunchedEffect(cameraProvider, hasCamPermission, lifecycleOwner) {
        if (hasCamPermission && cameraProvider != null) {
            try {
                cameraProvider?.unbindAll()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                Log.d("JournalScreen", "CameraX bound.")
            } catch (exc: Exception) { Log.e("JournalScreen", "Use case binding failed", exc) }
        }
    }

    if (!isEditing && capturedImageUri == null && hasCamPermission) {
        // Camera Preview
        Box(Modifier.fillMaxSize()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Button(
                onClick = { if (cameraProvider != null && !isSaving) takePhoto(context, imageCapture, journalViewModel::onImageCaptured) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).navigationBarsPadding().size(72.dp),
                shape = CircleShape,
                enabled = cameraProvider != null && !isSaving
            ) { Icon(Icons.Filled.AddCircle, "Take Photo", Modifier.size(36.dp)) }
        }
    } else if (capturedImageUri != null || isEditing) {
        // Entry Edit/View
        val screenPadding = if (LocalConfiguration.current.screenWidthDp >= 600) 24.dp else 16.dp
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = screenPadding).verticalScroll(rememberScrollState())) {
            // Image Display (if available)
            if (capturedImageUri != null) {
                Image(
                    rememberAsyncImagePainter(capturedImageUri),
                    if (isEditing) "Journal image (non-editable)" else "Captured journal image",
                    Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(bottom = 16.dp)
                        .then(if (isEditing) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else Modifier)
                )
            } else if (isEditing) {
                Text("Editing text-only entry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally))
            }

            // Text Field
            Text("Journal Entry", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            BasicTextField(
                value = journalText,
                onValueChange = { journalViewModel.updateJournalText(it) },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { innerTextField -> Box { if (journalText.isEmpty()) Text("Add your thoughts...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline); innerTextField() } }
            )
            Spacer(Modifier.height(24.dp))

            // AI Assistance
            Text("AI Assistance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            SuggestionCard(promptSuggestion, isLoadingPrompt)
            Spacer(Modifier.height(16.dp))
            ReflectionCard(journalText, reflectionResult, isLoadingReflection)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Button({ geminiViewModel.suggestJournalPrompt() }, enabled = !isLoadingPrompt && !isLoadingReflection) { Text("Prompt") }
                Button({ geminiViewModel.reflectOnJournalEntry(journalText) }, enabled = !isLoadingReflection && !isLoadingPrompt && journalText.isNotBlank()) { Text("Reflect") }
            }
            Spacer(Modifier.height(32.dp))

            // Action Buttons
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        if (isEditing) navController.popBackStack() // Go back if editing
                        journalViewModel.clearJournalState() // Always clear state
                    },
                    enabled = !isSaving
                ) { Icon(Icons.Default.Clear, "Cancel", Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Cancel") }

                Button(
                    onClick = {
                        journalViewModel.saveJournalEntry(context) { success, wasEditingMode ->
                            if (success && wasEditingMode) {
                                navController.popBackStack() // Go back only if edit succeeded
                            }
                        }
                    },
                    enabled = !isSaving && (capturedImageUri != null || journalText.isNotBlank())
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(24.dp), MaterialTheme.colorScheme.onPrimary, 2.dp)
                    else { Icon(Icons.Default.Check, "Save Entry", Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Save Entry") }
                }
            }
        }
    } else {
        // Fallback View (Permission Denied or Loading)
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (!hasCamPermission) "Camera permission needed..." else "Initializing Camera...", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                if (!hasCamPermission) {
                    Spacer(Modifier.height(16.dp))
                    Button({ launcher.launch(Manifest.permission.CAMERA) }) { Text("Grant Permission") }
                } else if (cameraProvider == null) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().defaultMinSize(minHeight = 48.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(promptSuggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, false).padding(end = 8.dp))
            if (isLoadingPrompt) CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

@Composable
fun ReflectionCard(entryText: String, reflectionResult: String, isLoadingReflection: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().defaultMinSize(minHeight = 48.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                when {
                    isLoadingReflection -> "Generating reflection..."
                    reflectionResult.isBlank() && entryText.isNotBlank() -> "Click 'Reflect' for insights."
                    reflectionResult.isBlank() && entryText.isBlank() -> "Write an entry first."
                    else -> reflectionResult
                },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, false).padding(end = 8.dp)
            )
            if (isLoadingReflection) CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

private fun takePhoto(context: Context, imageCapture: ImageCapture, onImageSaved: (Uri) -> Unit) {
    val cacheDir = context.cacheDir ?: run { Log.e("TakePhoto", "Cache dir null"); return }
    if (!cacheDir.exists()) cacheDir.mkdirs()
    val photoFile = File(cacheDir, "JPEG_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) { Log.e("JournalScreen", "Photo capture failed: ${exc.message}", exc); Toast.makeText(context, "Photo Error", Toast.LENGTH_SHORT).show() }
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                Log.d("JournalScreen", "Photo capture succeeded: $savedUri")
                onImageSaved(savedUri)
            }
        }
    )
}