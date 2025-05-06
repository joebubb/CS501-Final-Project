package com.pictoteam.pictonote.model

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pictoteam.pictonote.appFirestore
import com.pictoteam.pictonote.constants.*
import com.pictoteam.pictonote.database.extractImagePathFromContent
import com.pictoteam.pictonote.database.saveEntryToRemote
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager // Added
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStoreManager = SettingsDataStoreManager(application) // Instance of DataStoreManager

    private val _capturedImageUri = MutableStateFlow<Uri?>(null)
    val capturedImageUri: StateFlow<Uri?> = _capturedImageUri.asStateFlow()

    private val _journalText = MutableStateFlow("")
    val journalText: StateFlow<String> = _journalText.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _editingFilePath = MutableStateFlow<String?>(null)
    val isEditing: StateFlow<Boolean> = _editingFilePath.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun updateJournalText(newText: String) { _journalText.value = newText }

    fun onImageCaptured(uri: Uri) {
        if (_editingFilePath.value == null) _capturedImageUri.value = uri
        else Log.w("JournalVM", "Img capture ignored while editing.")
    }

    fun clearJournalState() {
        _capturedImageUri.value = null; _journalText.value = "";
        _isSaving.value = false; _editingFilePath.value = null
        Log.d("JournalVM", "State cleared.")
    }

    fun isNewEntryState(): Boolean = _capturedImageUri.value == null && _journalText.value.isEmpty() && _editingFilePath.value == null && !_isSaving.value

    fun loadEntryForEditing(context: Context, filePath: String) {
        Log.d("JournalVM", "Loading for edit: $filePath"); clearJournalState(); _editingFilePath.value = filePath
        viewModelScope.launch {
            try {
                val (imgRelPath, loadedText) = readJournalEntryFromFileInternal(context, filePath)
                withContext(Dispatchers.Main) {
                    if (imgRelPath != null) {
                        val imgFile = File(context.filesDir, imgRelPath)
                        _capturedImageUri.value = if (imgFile.exists()) Uri.fromFile(imgFile) else null.also {
                            Log.w("JournalVM", "Edit Load: Img missing: $imgRelPath")
                            Toast.makeText(context, "Warn: Img missing", Toast.LENGTH_SHORT).show()
                        }
                    } else _capturedImageUri.value = null
                    _journalText.value = loadedText ?: ""
                    Log.d("JournalVM", "Entry loaded. Img: ${_capturedImageUri.value}, TextLen: ${journalText.value.length}")
                }
            } catch (e: Exception) {
                Log.e("JournalVM", "Error loading $filePath", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error loading entry", Toast.LENGTH_LONG).show(); _editingFilePath.value = null }
            }
        }
    }

    fun saveJournalEntry(context: Context, onComplete: (success: Boolean, wasEditing: Boolean) -> Unit) {
        if (_isSaving.value) return
        val currentText = _journalText.value; val currentImgUri = _capturedImageUri.value; val currentEditPath = _editingFilePath.value
        if (currentImgUri == null && currentText.isBlank() && currentEditPath == null) {
            Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show(); onComplete(false, false); return
        }
        _isSaving.value = true; val wasEditing = currentEditPath != null
        viewModelScope.launch {
            var localSaveOk = false; var entryId: String? = null; var finalContent: String? = null
            try {
                val contentBuilder = StringBuilder()
                if (wasEditing) {
                    entryId = File(currentEditPath!!).name.substringAfter("journal_").removeSuffix(".txt")
                    val (existingImgRelPath, _) = readJournalEntryFromFileInternal(context, currentEditPath)
                    if (existingImgRelPath != null) {
                        val currentImgFileUri = currentImgUri?.let { if (it.scheme == "file") it else null }
                        val existingImgFileUri = Uri.fromFile(File(context.filesDir, existingImgRelPath))
                        if (currentImgUri != null && currentImgFileUri == existingImgFileUri) {
                            contentBuilder.appendLine("$IMAGE_URI_MARKER$existingImgRelPath")
                        }
                    }
                    contentBuilder.append(currentText)
                    finalContent = contentBuilder.toString()
                    overwriteTextFileInternal(context, currentEditPath, finalContent)
                    localSaveOk = true; Log.i("JournalVM", "Updated locally: $currentEditPath")
                } else {
                    var imgRelPath: String? = null
                    if (currentImgUri != null) {
                        imgRelPath = copyImageToInternalStorageInternal(context, currentImgUri)
                        if (imgRelPath != null) contentBuilder.appendLine("$IMAGE_URI_MARKER$imgRelPath")
                        else Log.e("JournalVM", "New entry img copy fail")
                    }
                    contentBuilder.append(currentText)
                    finalContent = contentBuilder.toString()
                    val savedFName = saveTextToNewFileInternal(context, finalContent)
                    entryId = savedFName.substringAfter("journal_").removeSuffix(".txt")
                    localSaveOk = true; Log.i("JournalVM", "New entry saved locally: $savedFName")
                }

                if (localSaveOk && entryId != null && finalContent != null) {
                    val appSettings = settingsDataStoreManager.appSettingsFlow.first()
                    if (appSettings.autoSyncEnabled) {
                        Log.d("JournalVM", "Auto-sync enabled. Attempting to sync entry: $entryId")
                        val remoteSaveSuccess = saveEntryToRemote(context, entryId, finalContent)
                        if (remoteSaveSuccess) {
                            Log.i("JournalVM", "Auto-sync: Entry $entryId also saved to remote.")
                        } else {
                            Log.w("JournalVM", "Auto-sync: Entry $entryId failed to save to remote. Saved locally.")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Saved locally. Auto-sync to cloud failed.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Log.d("JournalVM", "Auto-sync disabled. Entry $entryId saved locally only.")
                    }
                }
            } catch (e: Exception) {
                Log.e("JournalVM", "Save/Update error", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error saving", Toast.LENGTH_LONG).show() }
                localSaveOk = false
            } finally {
                withContext(Dispatchers.Main) {
                    if (localSaveOk) { Toast.makeText(context, "Entry ${if(wasEditing) "updated" else "saved"}!", Toast.LENGTH_SHORT).show(); clearJournalState() }
                    _isSaving.value = false; onComplete(localSaveOk, wasEditing)
                }
            }
        }
    }

    private suspend fun readJournalEntryFromFileInternal(context: Context, filePath: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val entryFile = File(filePath); if (!entryFile.exists()) throw IOException("File not found $filePath")
        val lines = entryFile.readLines()
        val imagePath = extractImagePathFromContent(lines.joinToString("\n"))
        val text = lines.filterNot { it.startsWith(IMAGE_URI_MARKER) }.joinToString("\n")
        return@withContext Pair(imagePath, text)
    }
    private suspend fun copyImageToInternalStorageInternal(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val imgDir = File(context.filesDir, JOURNAL_IMAGE_DIR); if (!imgDir.exists()) imgDir.mkdirs()
        val ts = LocalDateTime.now().format(filenameDateTimeFormatter)
        val dest = File(imgDir, "IMG_$ts.jpg")
        try { context.contentResolver.openInputStream(uri)?.use {i->FileOutputStream(dest).use{o->i.copyTo(o)}}; return@withContext "$JOURNAL_IMAGE_DIR/${dest.name}"
        } catch(e:Exception){Log.e("JournalVM","Img copy fail internal",e); dest.delete(); return@withContext null}
    }
    private suspend fun saveTextToNewFileInternal(context: Context, content: String): String = withContext(Dispatchers.IO) {
        val jDir = File(context.filesDir, JOURNAL_DIR); if(!jDir.exists()) jDir.mkdirs()
        val ts = LocalDateTime.now().format(filenameDateTimeFormatter)
        val file = File(jDir, "journal_$ts.txt")
        file.writeText(content); return@withContext file.name
    }
    private suspend fun overwriteTextFileInternal(context: Context, path: String, content: String) = withContext(Dispatchers.IO) {
        File(path).writeText(content)
    }
}