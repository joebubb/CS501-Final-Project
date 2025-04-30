package com.pictoteam.pictonote.model

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pictoteam.pictonote.JOURNAL_DIR // Import JOURNAL_DIR
import com.pictoteam.pictonote.filenameDateFormatter // Import formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate

// Marker to identify image URI within the saved text file
const val IMAGE_URI_MARKER = "IMAGE_URI::"
const val JOURNAL_IMAGE_DIR = "journal_images" // Subdirectory for images

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val _capturedImageUri = MutableStateFlow<Uri?>(null)
    val capturedImageUri: StateFlow<Uri?> = _capturedImageUri

    // Use Compose state for text to simplify JournalScreen
    val journalText = mutableStateOf("")

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    // We might still need GeminiViewModel for AI features later
    // val geminiViewModel: GeminiViewModel by inject() // Or pass via constructor if needed

    fun onImageCaptured(uri: Uri) {
        _capturedImageUri.value = uri
        Log.d("JournalViewModel", "Image captured, URI set: $uri")
        // Maybe load today's text if an image is newly captured?
        // loadTodaysText() // Optional: Decide if you want this behavior
    }

    fun clearJournalState() {
        _capturedImageUri.value = null
        journalText.value = ""
        _isSaving.value = false
        Log.d("JournalViewModel", "Journal state cleared.")
    }

    // --- Saving Logic ---
    fun saveJournalEntry(context: Context) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val textToSave = journalText.value
                val imageUriToSave = _capturedImageUri.value
                var finalImagePath: String? = null // Path relative to app's filesDir

                // 1. Handle Image: Copy to permanent storage if it exists
                if (imageUriToSave != null) {
                    finalImagePath = copyImageToInternalStorage(context, imageUriToSave)
                    if (finalImagePath == null) {
                        Log.e("JournalViewModel", "Failed to copy image to internal storage.")
                        // Decide how to handle: save without image, show error?
                        // For now, we'll proceed without the image path in the text file.
                    }
                }

                // 2. Prepare Text Content
                val contentBuilder = StringBuilder()
                if (finalImagePath != null) {
                    contentBuilder.append(IMAGE_URI_MARKER).append(finalImagePath).append("\n")
                }
                contentBuilder.append(textToSave)
                val finalContent = contentBuilder.toString()

                // 3. Save Text File
                saveTextToFile(context, finalContent)

                Log.i("JournalViewModel", "Journal Entry Saved Successfully.")
                // Optionally clear state after saving on the main thread
                // withContext(Dispatchers.Main) { clearJournalState() }

            } catch (e: Exception) {
                Log.e("JournalViewModel", "Error saving journal entry", e)
                // Handle error (e.g., show a Snackbar)
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun copyImageToInternalStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val imageDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        // Use date in filename for uniqueness, similar to text entry
        val todayDateString = LocalDate.now().format(filenameDateFormatter)
        val destinationFile = File(imageDir, "IMG_$todayDateString.jpg")

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // Return the path relative to filesDir for storage in the text file
            return@withContext "${JOURNAL_IMAGE_DIR}/${destinationFile.name}"
        } catch (e: IOException) {
            Log.e("JournalViewModel", "Failed to copy image from $sourceUri to $destinationFile", e)
            return@withContext null
        }
    }

    private suspend fun saveTextToFile(context: Context, content: String) = withContext(Dispatchers.IO) {
        val journalDir = File(context.filesDir, JOURNAL_DIR)
        if (!journalDir.exists()) {
            journalDir.mkdirs()
        }
        val todayDateString = LocalDate.now().format(filenameDateFormatter)
        val filename = "journal_$todayDateString.txt"
        val file = File(journalDir, filename)

        try {
            file.writeText(content)
            Log.i("JournalViewModel", "Text content saved to ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e("JournalViewModel", "Error saving text content to file", e)
            throw e // Re-throw to be caught by the calling function
        }
    }

    // --- Loading Logic (Optional - for resuming today's entry) ---
    fun loadTodaysEntry(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val (imagePath, text) = readTodaysJournalEntryInternal(context)
            withContext(Dispatchers.Main) {
                if (imagePath != null) {
                    // Convert stored relative path back to a File URI
                    val imageFile = File(context.filesDir, imagePath)
                    if (imageFile.exists()) {
                        _capturedImageUri.value = Uri.fromFile(imageFile)
                        Log.d("JournalViewModel", "Loaded image for today: ${_capturedImageUri.value}")
                    } else {
                        Log.w("JournalViewModel", "Image file not found at path: $imagePath")
                        _capturedImageUri.value = null // Reset if file missing
                    }
                } else {
                    _capturedImageUri.value = null
                }
                journalText.value = text ?: ""
                Log.d("JournalViewModel", "Loaded text for today: ${journalText.value.length} chars")
            }
        }
    }

    // Internal reading function returning both parts
    private suspend fun readTodaysJournalEntryInternal(context: Context): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, JOURNAL_DIR)
        val todayDateString = LocalDate.now().format(filenameDateFormatter)
        val filename = "journal_$todayDateString.txt"
        val entryFile = File(directory, filename)

        var imagePath: String? = null
        var textContent: String? = null

        try {
            if (entryFile.exists() && entryFile.isFile) {
                val fullContent = entryFile.readText()
                if (fullContent.startsWith(IMAGE_URI_MARKER)) {
                    val lines = fullContent.lines()
                    imagePath = lines.firstOrNull()?.substringAfter(IMAGE_URI_MARKER)
                    textContent = lines.drop(1).joinToString("\n")
                } else {
                    textContent = fullContent
                }
                Log.d("ReadTodayInternal", "Found entry: ImagePath=$imagePath, TextLength=${textContent?.length}")
            } else {
                Log.d("ReadTodayInternal", "No entry file found for today: $filename")
            }
        } catch (e: Exception) {
            Log.e("ReadTodayInternal", "Error reading today's journal entry: ${entryFile.absolutePath}", e)
        }
        return@withContext Pair(imagePath, textContent)
    }
}