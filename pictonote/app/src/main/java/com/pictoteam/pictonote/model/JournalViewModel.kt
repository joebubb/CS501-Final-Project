package com.pictoteam.pictonote.model

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pictoteam.pictonote.appFirestore // Ensure this import works with your project structure
import com.pictoteam.pictonote.constants.*
import com.pictoteam.pictonote.database.extractImagePathFromContent
import com.pictoteam.pictonote.database.saveEntryToRemote
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStoreManager = SettingsDataStoreManager(application)

    private val _capturedImageUri = MutableStateFlow<Uri?>(null)
    val capturedImageUri: StateFlow<Uri?> = _capturedImageUri.asStateFlow()

    private val _journalText = MutableStateFlow("")
    val journalText: StateFlow<String> = _journalText.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editingFilePath = MutableStateFlow<String?>(null)
    val isEditing: StateFlow<Boolean> = _editingFilePath.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Accessor for the raw value of _editingFilePath
    val editingFilePathValue: String?
        get() = _editingFilePath.value

    fun updateJournalText(newText: String) {
        _journalText.value = newText
    }

    fun onImageCaptured(uri: Uri) {
        // Only set image URI if we are NOT currently editing an existing entry's image.
        // If editing, image handling is managed by loadEntryForEditing.
        // This prevents an image taken while "editing text" from overwriting the existing image state.
        if (_editingFilePath.value == null) {
            _capturedImageUri.value = uri
            Log.d("JournalVM", "New image captured and set: $uri")
        } else {
            Log.w("JournalVM", "Image capture ignored while editing an existing entry. Current image URI: ${_capturedImageUri.value}")
            // Consider if you want to allow replacing an image during edit mode explicitly.
            // If so, you'd need a different mechanism.
            // For now, this logic prioritizes the loaded image during edit.
        }
    }

    fun clearJournalState() {
        Log.d("JournalVM", "Clearing journal state. Current editing path: ${_editingFilePath.value}")
        _capturedImageUri.value = null
        _journalText.value = ""
        _isSaving.value = false
        _editingFilePath.value = null // This marks that we are no longer editing a specific file
        Log.d("JournalVM", "Journal state cleared.")
    }

    fun isNewEntryState(): Boolean {
        val isNew = _capturedImageUri.value == null &&
                _journalText.value.isEmpty() &&
                _editingFilePath.value == null &&
                !_isSaving.value
        Log.d("JournalVM", "isNewEntryState check: $isNew (URI: ${_capturedImageUri.value}, TextEmpty: ${_journalText.value.isEmpty()}, EditPath: ${_editingFilePath.value}, Saving: ${_isSaving.value})")
        return isNew
    }


    fun loadEntryForEditing(context: Context, filePath: String) {
        Log.d("JournalVM", "Attempting to load entry for editing: $filePath")
        // Clear previous state before loading new, to avoid conflicts
        // but keep _editingFilePath to mark we are in edit mode for *this* file
        _capturedImageUri.value = null
        _journalText.value = ""
        _isSaving.value = false // Ensure saving flag is reset
        _editingFilePath.value = filePath // Set the current editing path *first*

        viewModelScope.launch {
            try {
                val (imgRelPath, loadedText) = readJournalEntryFromFileInternal(context, filePath)
                withContext(Dispatchers.Main) { // Ensure UI updates are on the main thread
                    if (imgRelPath != null) {
                        val imgFile = File(context.filesDir, imgRelPath)
                        if (imgFile.exists()) {
                            _capturedImageUri.value = Uri.fromFile(imgFile)
                            Log.d("JournalVM", "Image loaded for editing: ${_capturedImageUri.value}")
                        } else {
                            _capturedImageUri.value = null
                            Log.w("JournalVM", "Edit Load: Image file missing at path: $imgRelPath")
                            Toast.makeText(context, "Warning: Image file for this entry is missing.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        _capturedImageUri.value = null
                        Log.d("JournalVM", "No image path found in entry for editing.")
                    }
                    _journalText.value = loadedText ?: ""
                    Log.i("JournalVM", "Entry successfully loaded for editing. Path: $filePath, Image URI: ${_capturedImageUri.value}, Text Length: ${journalText.value.length}")
                }
            } catch (e: Exception) {
                Log.e("JournalVM", "Error loading entry for editing: $filePath", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading entry details.", Toast.LENGTH_LONG).show()
                    // If loading fails, we should clear the editing state to avoid confusion
                    clearJournalState() // This will set _editingFilePath.value to null
                }
            }
        }
    }


    fun saveJournalEntry(context: Context, onComplete: (success: Boolean, wasEditing: Boolean) -> Unit) {
        if (_isSaving.value) {
            Log.w("JournalVM", "Save attempt ignored, already saving.")
            onComplete(false, _editingFilePath.value != null)
            return
        }

        val currentText = _journalText.value
        val currentImgUri = _capturedImageUri.value
        val currentEditPath = _editingFilePath.value // This is the path of the file being edited, if any

        Log.d("JournalVM", "Attempting to save. Editing: ${currentEditPath != null}, Image: $currentImgUri, Text Blank: ${currentText.isBlank()}")

        if (currentImgUri == null && currentText.isBlank()) {
            // For a new entry, if both are blank, there's nothing to save.
            // If editing, and user clears everything, we might allow saving an empty entry (overwriting).
            if (currentEditPath == null) {
                Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
                Log.d("JournalVM", "Save cancelled: Nothing to save (new entry).")
                onComplete(false, false)
                return
            }
            // Allow saving/overwriting an existing entry with blank content
            Log.d("JournalVM", "Proceeding to save/overwrite existing entry with blank content: $currentEditPath")
        }

        _isSaving.value = true
        val wasEditing = currentEditPath != null

        viewModelScope.launch {
            var localSaveOk = false
            var entryIdForSync: String? = null
            var finalContentForSync: String? = null

            try {
                val contentBuilder = StringBuilder()
                var finalImageRelativePath: String? = null

                if (wasEditing) { // Overwriting an existing entry
                    entryIdForSync = File(currentEditPath!!).name.substringAfter("journal_").removeSuffix(".txt")
                    Log.d("JournalVM", "Updating existing entry: $currentEditPath (ID: $entryIdForSync)")

                    // Check if the image URI is still the one from the file system (not a temp cache URI)
                    // And if it matches the original image path (if one existed)
                    // The _capturedImageUri during edit mode should point to the persistent internal file.
                    if (currentImgUri != null && currentImgUri.scheme == "file") {
                        // The URI points to a file. We need its relative path for the marker.
                        // This assumes currentImgUri is the correct, persistent URI.
                        val imgFile = File(currentImgUri.path!!) // Should not be null if scheme is file
                        if(imgFile.startsWith(context.filesDir)) {
                            finalImageRelativePath = imgFile.relativeTo(context.filesDir).path
                            Log.d("JournalVM", "Using existing/updated image for edit: $finalImageRelativePath")
                        } else {
                            Log.w("JournalVM", "Image URI for edit is not in app's filesDir: $currentImgUri. Image will not be saved in marker.")
                        }
                    } else if (currentImgUri != null) {
                        Log.w("JournalVM", "Image URI for edit has unexpected scheme: ${currentImgUri.scheme}. Image will not be saved in marker.")
                    }


                    if (finalImageRelativePath != null) {
                        contentBuilder.appendLine("$IMAGE_URI_MARKER$finalImageRelativePath")
                    }
                    contentBuilder.append(currentText)
                    finalContentForSync = contentBuilder.toString()

                    overwriteTextFileInternal(context, currentEditPath, finalContentForSync)
                    localSaveOk = true
                    Log.i("JournalVM", "Entry updated locally: $currentEditPath")

                } else { // Saving a new entry
                    if (currentImgUri != null) {
                        // This URI is from the camera (likely a cache file) or gallery
                        finalImageRelativePath = copyImageToInternalStorageInternal(context, currentImgUri)
                        if (finalImageRelativePath != null) {
                            contentBuilder.appendLine("$IMAGE_URI_MARKER$finalImageRelativePath")
                            Log.d("JournalVM", "New image copied to internal storage: $finalImageRelativePath")
                        } else {
                            Log.e("JournalVM", "Failed to copy new image to internal storage. Image will not be part of the entry.")
                            // Potentially notify user, but save text part anyway
                            Toast.makeText(context, "Error saving image, text saved.", Toast.LENGTH_LONG).show()
                        }
                    }
                    contentBuilder.append(currentText)
                    finalContentForSync = contentBuilder.toString()

                    val savedFileNameWithExt = saveTextToNewFileInternal(context, finalContentForSync)
                    entryIdForSync = savedFileNameWithExt.substringAfter("journal_").removeSuffix(".txt")
                    localSaveOk = true
                    Log.i("JournalVM", "New entry saved locally: $savedFileNameWithExt (ID: $entryIdForSync)")
                }

                if (localSaveOk && entryIdForSync != null && finalContentForSync != null) {
                    val appSettings = settingsDataStoreManager.appSettingsFlow.first()
                    if (appSettings.autoSyncEnabled) {
                        Log.d("JournalVM", "Auto-sync enabled. Attempting to sync entry: $entryIdForSync")
                        val remoteSaveSuccess = saveEntryToRemote(context, entryIdForSync, finalContentForSync)
                        if (remoteSaveSuccess) {
                            Log.i("JournalVM", "Auto-sync: Entry $entryIdForSync successfully saved to remote.")
                        } else {
                            Log.w("JournalVM", "Auto-sync: Entry $entryIdForSync failed to save to remote. It remains saved locally.")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saved locally. Auto-sync to cloud failed.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Log.d("JournalVM", "Auto-sync disabled. Entry $entryIdForSync saved locally only.")
                    }
                }

            } catch (e: Exception) {
                Log.e("JournalVM", "Error during save/update operation", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error saving entry", Toast.LENGTH_LONG).show() }
                localSaveOk = false
            } finally {
                withContext(Dispatchers.Main) {
                    if (localSaveOk) {
                        Toast.makeText(context, "Entry ${if (wasEditing) "updated" else "saved"}!", Toast.LENGTH_SHORT).show()
                        clearJournalState() // Clear state after successful save (new or edit)
                    }
                    _isSaving.value = false
                    onComplete(localSaveOk, wasEditing)
                    Log.d("JournalVM", "Save operation complete. Success: $localSaveOk, Was Editing: $wasEditing")
                }
            }
        }
    }

    private suspend fun readJournalEntryFromFileInternal(context: Context, filePath: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val entryFile = File(filePath)
        if (!entryFile.exists()) {
            Log.e("JournalVM", "File not found for reading: $filePath")
            throw IOException("File not found: $filePath")
        }
        val lines = entryFile.readLines()
        val imagePath = extractImagePathFromContent(lines.joinToString("\n"))
        val text = lines.filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n")
        Log.d("JournalVM", "Read from $filePath. Image: $imagePath, TextLen: ${text.length}")
        return@withContext Pair(imagePath, text)
    }

    private suspend fun copyImageToInternalStorageInternal(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val imagesDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val timestamp = LocalDateTime.now().format(filenameDateTimeFormatter) // Ensure unique enough name
        val destinationFile = File(imagesDir, "IMG_${timestamp}_${uri.lastPathSegment ?: "image"}.jpg")

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            val relativePath = "$JOURNAL_IMAGE_DIR/${destinationFile.name}"
            Log.d("JournalVM", "Image copied to internal storage: $relativePath from URI: $uri")
            return@withContext relativePath
        } catch (e: Exception) {
            Log.e("JournalVM", "Failed to copy image from $uri to internal storage", e)
            destinationFile.delete() // Clean up if copy failed
            return@withContext null
        }
    }

    private suspend fun saveTextToNewFileInternal(context: Context, content: String): String = withContext(Dispatchers.IO) {
        val journalDir = File(context.filesDir, JOURNAL_DIR)
        if (!journalDir.exists()) {
            journalDir.mkdirs()
        }
        val timestamp = LocalDateTime.now().format(filenameDateTimeFormatter)
        val file = File(journalDir, "journal_$timestamp.txt")
        file.writeText(content)
        Log.d("JournalVM", "New text file saved: ${file.name} in ${journalDir.absolutePath}")
        return@withContext file.name // Return only the filename with extension
    }

    private suspend fun overwriteTextFileInternal(context: Context, fullPath: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(fullPath)
        if (!file.exists()) {
            Log.w("JournalVM", "Attempting to overwrite non-existent file: $fullPath. Creating it.")
            // Ensure parent directory exists (should normally be the case)
            file.parentFile?.mkdirs()
        }
        file.writeText(content)
        Log.d("JournalVM", "Text file overwritten: $fullPath")
    }
}