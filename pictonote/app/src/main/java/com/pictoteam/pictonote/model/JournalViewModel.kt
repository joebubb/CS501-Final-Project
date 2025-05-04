package com.pictoteam.pictonote.model

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast // Import Toast
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

    fun onImageCaptured(uri: Uri) {
        _capturedImageUri.value = uri
        Log.d("JournalViewModel", "Image captured, URI set: $uri")
    }

    // Renamed for clarity, this resets the UI state for a new entry
    fun clearJournalState() {
        _capturedImageUri.value = null
        journalText.value = ""
        _isSaving.value = false // Ensure saving is reset if called manually
        Log.d("JournalViewModel", "Journal state cleared for new entry.")
    }

    // --- Saving Logic ---
    fun saveJournalEntry(context: Context) {
        // Prevent multiple save operations
        if (_isSaving.value) {
            Log.w("JournalViewModel", "Save already in progress, ignoring duplicate request.")
            return
        }
        _isSaving.value = true // Set saving state immediately on main thread

        viewModelScope.launch { // Launch coroutine for background work
            var success = false // Flag to track if saving succeeded
            try {
                // --- Perform IO operations on Dispatchers.IO ---
                val finalContent = withContext(Dispatchers.IO) {
                    val textToSave = journalText.value // Capture state values
                    val imageUriToSave = _capturedImageUri.value
                    var finalImagePath: String? = null // Path relative to app's filesDir

                    // 1. Handle Image: Copy to permanent storage if it exists
                    if (imageUriToSave != null) {
                        finalImagePath = copyImageToInternalStorage(context, imageUriToSave)
                        if (finalImagePath == null) {
                            Log.e("JournalViewModel", "Failed to copy image to internal storage.")
                            // Decide how to handle: save without image, show error? Proceeding without image path.
                        } else {
                            Log.d("JournalViewModel", "Image copied to internal storage: $finalImagePath")
                        }
                    }

                    // 2. Prepare Text Content
                    val contentBuilder = StringBuilder()
                    if (finalImagePath != null) {
                        // IMPORTANT: Ensure newline after marker if text follows
                        contentBuilder.append(IMAGE_URI_MARKER).append(finalImagePath).append("\n")
                    }
                    contentBuilder.append(textToSave)
                    val content = contentBuilder.toString()

                    // 3. Save Text File
                    saveTextToFile(context, content)
                    content // Return the saved content (or just indicate success)
                }
                // --- IO Operations Complete ---

                // If saveTextToFile didn't throw an exception, we consider it a success
                success = true
                Log.i("JournalViewModel", "Journal Entry Saved Successfully (IO part finished).")

            } catch (e: Exception) {
                Log.e("JournalViewModel", "Error during saving journal entry", e)
                // Optionally show an error Toast on the main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving entry: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // --- Switch back to Main thread for UI updates (Toast, state reset) ---
                withContext(Dispatchers.Main) {
                    if (success) {
                        // Show success message
                        Toast.makeText(context, "Journal entry saved!", Toast.LENGTH_SHORT).show()
                        // Reset the state for a new entry
                        clearJournalState() // This will trigger UI recomposition
                        Log.d("JournalViewModel", "Save successful: State cleared and user notified.")
                    } else {
                        // Only reset the saving flag if saving failed, state remains for retry/edit
                        _isSaving.value = false
                        Log.w("JournalViewModel", "Save failed: Saving flag reset, state kept.")
                    }
                    // Note: _isSaving is set to false within clearJournalState() on success path.
                }
            }
        }
    }

    // --- Private Helper Functions (Remain unchanged) ---

    private suspend fun copyImageToInternalStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val imageDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        val todayDateString = LocalDate.now().format(filenameDateFormatter)
        // Add timestamp for uniqueness in case of multiple saves same day (though UI resets now)
        val destinationFileName = "IMG_${todayDateString}_${System.currentTimeMillis()}.jpg"
        val destinationFile = File(imageDir, destinationFileName)

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // Return the relative path
            return@withContext "${JOURNAL_IMAGE_DIR}/${destinationFile.name}"
        } catch (e: IOException) {
            Log.e("JournalViewModel", "Failed to copy image from $sourceUri to $destinationFile", e)
            return@withContext null
        } catch (e: SecurityException) {
            Log.e("JournalViewModel", "Security exception reading image URI $sourceUri", e)
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
            Log.i("JournalViewModel", "Text content saved/overwritten to ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e("JournalViewModel", "Error saving text content to file ${file.absolutePath}", e)
            throw e // Re-throw to be caught by the calling function's catch block
        }
    }

    fun loadTodaysEntry(context: Context) {
        // Clear previous state before loading
        clearJournalState()
        Log.d("JournalViewModel", "Loading today's entry...")
        viewModelScope.launch { // Use viewModelScope
            val (imagePath, text) = readTodaysJournalEntryInternal(context) // Perform read on IO dispatcher
            // Update state on Main thread
            withContext(Dispatchers.Main) {
                if (imagePath != null) {
                    val imageFile = File(context.filesDir, imagePath)
                    if (imageFile.exists()) {
                        _capturedImageUri.value = Uri.fromFile(imageFile)
                        Log.d("JournalViewModel", "Loaded image for today: ${_capturedImageUri.value}")
                    } else {
                        Log.w("JournalViewModel", "Image file not found at path: $imagePath")
                        _capturedImageUri.value = null
                    }
                } else {
                    _capturedImageUri.value = null
                }
                journalText.value = text ?: ""
                Log.d("JournalViewModel", "Loaded text for today: ${journalText.value.length} chars. Image URI: ${_capturedImageUri.value}")
            }
        }
    }

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
                val firstLine = fullContent.lines().firstOrNull()
                if (firstLine != null && firstLine.startsWith(IMAGE_URI_MARKER)) {
                    imagePath = firstLine.substringAfter(IMAGE_URI_MARKER).trim()
                    textContent = fullContent.lines().drop(1).joinToString("\n")
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