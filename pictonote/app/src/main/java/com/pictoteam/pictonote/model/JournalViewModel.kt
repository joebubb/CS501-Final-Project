package com.pictoteam.pictonote.model

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
// Removed compose runtime imports, handle text state differently
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pictoteam.pictonote.constants.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime

const val IMAGE_URI_MARKER = "IMAGE_URI::"

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val _capturedImageUri = MutableStateFlow<Uri?>(null)
    val capturedImageUri: StateFlow<Uri?> = _capturedImageUri.asStateFlow() // Expose as StateFlow

    // Use StateFlow for text as well for consistency and Compose collection
    private val _journalText = MutableStateFlow("")
    val journalText: StateFlow<String> = _journalText.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editingFilePath = MutableStateFlow<String?>(null)
    val isEditing: StateFlow<Boolean> = _editingFilePath.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false) // Derived state


    // Call this from BasicTextField's onValueChange
    fun updateJournalText(newText: String) {
        _journalText.value = newText
    }

    fun onImageCaptured(uri: Uri) {
        if (_editingFilePath.value == null) { // Only allow image capture for new entries
            _capturedImageUri.value = uri
            Log.d("JournalViewModel", "Image captured for new entry: $uri")
        } else {
            Log.w("JournalViewModel", "Image capture ignored while editing.")
        }
    }

    fun clearJournalState() {
        _capturedImageUri.value = null
        _journalText.value = ""
        _isSaving.value = false
        _editingFilePath.value = null
        Log.d("JournalViewModel", "State cleared.")
    }

    // Helper to check if ViewModel is in new entry state
    fun isNewEntryState(): Boolean {
        return _capturedImageUri.value == null && _journalText.value.isEmpty() && _editingFilePath.value == null && !_isSaving.value
    }


    fun loadEntryForEditing(context: Context, filePath: String) {
        Log.d("JournalViewModel", "Loading entry for editing: $filePath")
        clearJournalState() // Ensure clean state before loading
        _editingFilePath.value = filePath

        viewModelScope.launch {
            try {
                val (imageRelPath, loadedText) = readJournalEntryFromFile(context, filePath)

                withContext(Dispatchers.Main) {
                    if (imageRelPath != null) {
                        val imageFile = File(context.filesDir, imageRelPath)
                        if (imageFile.exists()) {
                            _capturedImageUri.value = Uri.fromFile(imageFile)
                        } else {
                            Log.w("JournalViewModel", "Edit Load: Image file missing: $imageRelPath")
                            _capturedImageUri.value = null
                            Toast.makeText(context, "Warning: Image file missing", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        _capturedImageUri.value = null
                    }
                    _journalText.value = loadedText ?: "" // Update text StateFlow
                    Log.d("JournalViewModel", "Entry loaded for editing. Image: ${_capturedImageUri.value}, TextLen: ${_journalText.value.length}")
                }
            } catch (e: Exception) {
                Log.e("JournalViewModel", "Error loading entry: $filePath", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading entry", Toast.LENGTH_LONG).show()
                    _editingFilePath.value = null
                }
            }
        }
    }

    fun saveJournalEntry(context: Context, onComplete: (success: Boolean, wasEditing: Boolean) -> Unit) {
        if (_isSaving.value) return

        val currentText = _journalText.value // Capture current value
        val currentImageUri = _capturedImageUri.value
        val currentEditingPath = _editingFilePath.value

        if (currentImageUri == null && currentText.isBlank()) {
            Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
            onComplete(false, currentEditingPath != null)
            return
        }

        _isSaving.value = true
        val wasEditing = currentEditingPath != null

        viewModelScope.launch {
            var success = false
            var savedIdentifier: String? = null

            try {
                savedIdentifier = withContext(Dispatchers.IO) {
                    val contentBuilder = StringBuilder()
                    if (wasEditing) {
                        // Edit Mode
                        val (existingImageRelPath, _) = readJournalEntryFromFile(context, currentEditingPath!!)
                        if (existingImageRelPath != null) {
                            contentBuilder.appendLine("$IMAGE_URI_MARKER$existingImageRelPath")
                        }
                        contentBuilder.append(currentText)
                        overwriteTextFile(context, currentEditingPath, contentBuilder.toString())
                        currentEditingPath // Return path
                    } else {
                        // New Entry Mode
                        var imageRelPath: String? = null
                        if (currentImageUri != null) {
                            imageRelPath = copyImageToInternalStorage(context, currentImageUri)
                            if (imageRelPath != null) {
                                contentBuilder.appendLine("$IMAGE_URI_MARKER$imageRelPath")
                            } else {
                                Log.e("JournalViewModel", "Save New: Failed image copy")
                            }
                        }
                        contentBuilder.append(currentText)
                        saveTextToNewFile(context, contentBuilder.toString()) // Return filename
                    }
                }
                success = true
                Log.i("JournalViewModel", "Entry ${if(wasEditing) "updated" else "saved"}: $savedIdentifier")

            } catch (e: Exception) {
                Log.e("JournalViewModel", "Save/Update error", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error saving entry", Toast.LENGTH_LONG).show() }
            } finally {
                withContext(Dispatchers.Main) {
                    val finalSuccess = success
                    if (finalSuccess) {
                        Toast.makeText(context, "Entry ${if (wasEditing) "updated" else "saved"}!", Toast.LENGTH_SHORT).show()
                        clearJournalState() // Clear state fully only on success
                    } else {
                        _isSaving.value = false
                    }
                    onComplete(finalSuccess, wasEditing)
                }
            }
        }
    }

    private suspend fun readJournalEntryFromFile(context: Context, filePath: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val entryFile = File(filePath)
        if (!entryFile.exists() || !entryFile.isFile) throw IOException("Entry file not found: $filePath")

        return@withContext try {
            val lines = entryFile.readLines()
            val imageLine = lines.firstOrNull { it.startsWith(IMAGE_URI_MARKER) }
            val imagePath = imageLine?.substringAfter(IMAGE_URI_MARKER)?.trim()
            val textContent = lines.drop(if (imageLine != null) 1 else 0).joinToString("\n")
            Pair(imagePath, textContent)
        } catch (e: Exception) {
            Log.e("ReadFromFile", "Error reading: $filePath", e)
            throw e
        }
    }

    private suspend fun copyImageToInternalStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val imageDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            Log.e("JournalViewModel", "Failed image dir creation")
            return@withContext null
        }
        val timestamp = LocalDateTime.now().format(filenameDateTimeFormatter)
        val destFile = File(imageDir, "IMG_$timestamp.jpg")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext "${JOURNAL_IMAGE_DIR}/${destFile.name}" // Relative path
        } catch (e: Exception) {
            Log.e("JournalViewModel", "Image copy failed", e)
            destFile.delete() // Attempt cleanup
            return@withContext null
        }
    }

    private suspend fun saveTextToNewFile(context: Context, content: String): String = withContext(Dispatchers.IO) {
        val journalDir = File(context.filesDir, JOURNAL_DIR)
        if (!journalDir.exists() && !journalDir.mkdirs()) throw IOException("Failed journal dir creation")
        val timestamp = LocalDateTime.now().format(filenameDateTimeFormatter)
        val file = File(journalDir, "journal_$timestamp.txt")
        try {
            file.writeText(content)
            return@withContext file.name
        } catch (e: Exception) { throw e }
    }

    private suspend fun overwriteTextFile(context: Context, filePath: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) throw IOException("Overwrite target not found: $filePath")
        try {
            file.writeText(content)
        } catch (e: Exception) { throw e }
    }
}