package com.pictoteam.pictonote.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.pictoteam.pictonote.PictoNoteApplication
import com.pictoteam.pictonote.constants.JOURNAL_DIR
import com.pictoteam.pictonote.constants.JOURNAL_IMAGE_DIR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// Data class for journal entry
data class JournalEntry(
    val dateString: String = "",
    val content: String = "",
    val imagePath: String? = null,
    val lastModified: Long = 0
)

// Function to check if Firestore database is properly configured
suspend fun checkFirestoreDatabase(): Boolean {
    return suspendCoroutine { continuation ->
        try {
            // Use the Firestore instance from the Application class
            val db = PictoNoteApplication.firestore

            // Try a simple operation to check if database is accessible
            db.collection("database_check").document("test")
                .get()
                .addOnSuccessListener {
                    continuation.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e("CheckFirestore", "Error checking Firestore database: ${e.message}")
                    if (e.message?.contains("database") == true &&
                        e.message?.contains("does not exist") == true) {
                        continuation.resume(false)
                    } else {
                        // For other errors, like network issues, we'll consider database might exist
                        continuation.resume(true)
                    }
                }
        } catch (e: Exception) {
            Log.e("CheckFirestore", "Error checking Firestore database", e)
            continuation.resumeWithException(e)
        }
    }
}

// Save a journal entry to Firebase Firestore and Storage (if image exists)
suspend fun saveRemoteJournalEntry(context: Context, dateString: String, content: String, imagePath: String?): Boolean {
    return try {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("SaveRemoteJournalEntry", "No user is signed in")
            return false
        }

        val userId = user.uid
        val db = PictoNoteApplication.firestore
        var imageUrl: String? = null

        // If there's an image, upload it to Firebase Storage
        if (!imagePath.isNullOrEmpty()) {
            try {
                val storage = FirebaseStorage.getInstance()
                val storageRef = storage.reference

                // Extract filename from path or create a unique one
                val imageFileName = imagePath.substringAfterLast("/")
                val imageRef = storageRef.child("users/$userId/journal_images/$imageFileName")

                val imageFile = File(context.filesDir, imagePath)
                if (imageFile.exists()) {
                    // Compress image before uploading to save storage and bandwidth
                    val bytes = withContext(Dispatchers.IO) {
                        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        val stream = ByteArrayOutputStream()

                        // Compress with quality 85% - good balance between quality and size
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        stream.toByteArray()
                    }

                    // Upload image to Firebase Storage
                    val uploadTask = imageRef.putBytes(bytes).await()
                    imageUrl = imageRef.downloadUrl.await().toString()
                    Log.i("SaveRemoteJournalEntry", "Image uploaded: $imageUrl")
                } else {
                    Log.w("SaveRemoteJournalEntry", "Image file not found: $imagePath")
                }
            } catch (e: Exception) {
                Log.e("SaveRemoteJournalEntry", "Error uploading image: ${e.message}", e)
                // Continue even if image upload fails - we'll still save the text content
            }
        }

        // Create entry data including image URL if available
        val journalEntry = hashMapOf(
            "date" to dateString,
            "content" to content,
            "imagePath" to imagePath,
            "imageUrl" to imageUrl,
            "lastModified" to System.currentTimeMillis()
        )

        // Save entry to Firestore
        try {
            db.collection("users")
                .document(userId)
                .collection("journal_entries")
                .document(dateString)
                .set(journalEntry)
                .await()

            Log.i("SaveRemoteJournalEntry", "Entry for $dateString saved to Firestore")
            return true
        } catch (e: Exception) {
            Log.e("SaveRemoteJournalEntry", "Error saving to Firestore: ${e.message}")
            return false
        }
    } catch (e: Exception) {
        Log.e("SaveRemoteJournalEntry", "Error saving to Firestore: ${e.message}")
        return false
    }
}

// Extract image path from journal text content if available
fun extractImagePathFromContent(content: String): String? {
    // Assuming the image path is stored somewhere in the content
    // This is a simplified example - adapt to your actual format
    val regex = """(?:image\s*path\s*:|image\s*:)\s*(journal_images/[^\s,\n]+)""".toRegex(RegexOption.IGNORE_CASE)
    val matchResult = regex.find(content)
    return matchResult?.groupValues?.getOrNull(1)
}

// Save entry to local storage
fun saveLocalJournalEntry(context: Context, dateString: String, content: String, imagePath: String? = null): Boolean {
    try {
        val directory = File(context.filesDir, JOURNAL_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Create filename using the provided date string
        val filename = "journal_$dateString.txt"
        val file = File(directory, filename)

        file.writeText(content)
        Log.i("SaveLocalJournalEntry", "Journal Entry Saved/Updated: ${file.absolutePath}")
        return true
    } catch (e: IOException) {
        Log.e("SaveLocalJournalEntry", "Error saving journal entry to file", e)
        return false
    } catch (e: Exception) {
        Log.e("SaveLocalJournalEntry", "An unexpected error occurred during saving", e)
        return false
    }
}

// Download an image from Firebase Storage to local storage
suspend fun downloadImageFromStorage(context: Context, remoteUrl: String, localImagePath: String): Boolean {
    return try {
        val storage = FirebaseStorage.getInstance()
        val imageRef = storage.getReferenceFromUrl(remoteUrl)

        // Create the images directory if it doesn't exist
        val imagesDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        // Create a local file to save the image
        val localFile = File(context.filesDir, localImagePath)

        // Download the file
        val task = imageRef.getBytes(Long.MAX_VALUE).await()

        // Save the bytes to local file
        withContext(Dispatchers.IO) {
            val fos = FileOutputStream(localFile)
            fos.write(task)
            fos.close()
        }

        Log.i("DownloadImage", "Image downloaded to: ${localFile.absolutePath}")
        return true
    } catch (e: Exception) {
        Log.e("DownloadImage", "Error downloading image: ${e.message}", e)
        return false
    }
}

// Read local journal entries with their image paths
fun getLocalJournalEntries(context: Context): List<JournalEntry> {
    try {
        val entries = mutableListOf<JournalEntry>()
        val directory = File(context.filesDir, JOURNAL_DIR)

        if (!directory.exists() || !directory.isDirectory) {
            return entries
        }

        val journalFiles = directory.listFiles { file ->
            file.isFile && file.name.startsWith("journal_") && file.name.endsWith(".txt")
        } ?: return entries

        for (file in journalFiles) {
            try {
                // Extract date from filename (journal_YYYY-MM-DD.txt -> YYYY-MM-DD)
                val dateString = file.name.substringAfter("journal_").substringBefore(".txt")
                val content = file.readText()
                val imagePath = extractImagePathFromContent(content)

                entries.add(
                    JournalEntry(
                        dateString = dateString,
                        content = content,
                        imagePath = imagePath,
                        lastModified = file.lastModified()
                    )
                )
            } catch (e: Exception) {
                Log.e("GetLocalJournalEntries", "Error reading file ${file.name}: ${e.message}")
                // Continue with next file
            }
        }

        return entries
    } catch (e: Exception) {
        Log.e("GetLocalJournalEntries", "Error reading journal entries: ${e.message}")
        return emptyList()
    }
}

// Fetch remote journal entries and save locally
suspend fun fetchRemoteJournalEntries(
    context: Context,
    onProgress: (current: Int, total: Int, syncedEntries: Int, failedEntries: Int) -> Unit = { _, _, _, _ -> },
    onComplete: (success: Boolean, syncedEntries: Int, failedEntries: Int) -> Unit
) {
    try {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("FetchRemoteJournalEntries", "No user is signed in")
            onComplete(false, 0, 0)
            return
        }

        val userId = user.uid
        val db = PictoNoteApplication.firestore

        withContext(Dispatchers.IO) {
            val result = try {
                db.collection("users")
                    .document(userId)
                    .collection("journal_entries")
                    .get()
                    .await()
            } catch (e: Exception) {
                Log.e("FetchRemoteJournalEntries", "Error fetching remote entries: ${e.message}")
                onComplete(false, 0, 0)
                return@withContext
            }

            val totalEntries = result.documents.size
            val syncedEntries = AtomicInteger(0)
            val failedEntries = AtomicInteger(0)
            val processedEntries = AtomicInteger(0)

            if (totalEntries == 0) {
                Log.i("FetchRemoteJournalEntries", "No remote entries found")
                onComplete(true, 0, 0)
                return@withContext
            }

            // Create image directory if it doesn't exist
            val imagesDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // Process each document
            result.documents.forEachIndexed { index, document ->
                try {
                    val dateString = document.id
                    val content = document.getString("content") ?: ""
                    val imageUrl = document.getString("imageUrl")

                    // Generate local path for image if we have a URL
                    val localImagePath = if (!imageUrl.isNullOrEmpty()) {
                        // Extract filename from URL or create a new one based on date
                        val filename = try {
                            val uri = Uri.parse(imageUrl)
                            uri.lastPathSegment ?: "image_$dateString.jpg"
                        } catch (e: Exception) {
                            "image_$dateString.jpg"
                        }
                        "$JOURNAL_IMAGE_DIR/$filename"
                    } else {
                        null
                    }

                    // Create updated content that includes image path reference if needed
                    val updatedContent = if (localImagePath != null) {
                        // Add or update image path in content
                        val existingPathRegex = """(?:image\s*path\s*:|image\s*:)\s*(journal_images/[^\s,\n]+)""".toRegex(RegexOption.IGNORE_CASE)
                        if (existingPathRegex.containsMatchIn(content)) {
                            // Replace existing path
                            content.replace(existingPathRegex, "image path: $localImagePath")
                        } else {
                            // Add path info at the end of content
                            "$content\n\nimage path: $localImagePath"
                        }
                    } else {
                        content
                    }

                    // Save text content locally with updated image path reference
                    val saveContentSuccess = saveLocalJournalEntry(
                        context,
                        dateString,
                        updatedContent,
                        localImagePath
                    )

                    // If there's a remote image URL, download it to local storage
                    if (!imageUrl.isNullOrEmpty() && localImagePath != null) {
                        // Use coroutineScope to properly handle image download completion
                        kotlinx.coroutines.coroutineScope {
                            try {
                                val downloadSuccess = downloadImageFromStorage(
                                    context,
                                    imageUrl,
                                    localImagePath
                                )

                                if (!downloadSuccess) {
                                    Log.w("FetchRemoteJournalEntries", "Failed to download image for entry $dateString")
                                } else {

                                }
                            } catch (e: Exception) {
                                Log.e("FetchRemoteJournalEntries", "Error downloading image: ${e.message}")
                                // Continue with entry processing even if image download fails
                            }
                        }
                    }

                    // Consider entry synced if content is saved successfully
                    if (saveContentSuccess) {
                        syncedEntries.incrementAndGet()
                    } else {
                        failedEntries.incrementAndGet()
                    }

                    // Update progress
                    onProgress(index + 1, totalEntries, syncedEntries.get(), failedEntries.get())
                } catch (e: Exception) {
                    Log.e("FetchRemoteJournalEntries", "Error processing entry: ${e.message}")
                    failedEntries.incrementAndGet()
                    onProgress(index + 1, totalEntries, syncedEntries.get(), failedEntries.get())
                } finally {
                    // Increment processed count and check if all entries are done
                    if (processedEntries.incrementAndGet() == totalEntries) {
                        val success = failedEntries.get() == 0
                        Log.i("FetchRemoteJournalEntries", "Download completed: ${syncedEntries.get()} synced, ${failedEntries.get()} failed")
                        onComplete(success, syncedEntries.get(), failedEntries.get())
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FetchRemoteJournalEntries", "Error in fetch process: ${e.message}")
        onComplete(false, 0, 0)
    }
}

// Bidirectional synchronize - both upload and download journal entries with images
suspend fun synchronizeJournalEntries(
    context: Context,
    onProgress: (phase: String, current: Int, total: Int, syncedEntries: Int, failedEntries: Int) -> Unit = { _, _, _, _, _ -> },
    onComplete: (uploadSuccess: Boolean, uploadSynced: Int, uploadFailed: Int,
                 downloadSuccess: Boolean, downloadSynced: Int, downloadFailed: Int) -> Unit
) {
    try {
        // First check if Firestore database is set up properly
        val isDatabaseConfigured = checkFirestoreDatabase()
        if (!isDatabaseConfigured) {
            Log.e("SynchronizeJournalEntries", "Firestore database is not configured")
            onComplete(false, 0, 0, false, 0, 0)
            return
        }

        // PHASE 1: Upload local entries to Firestore
        var uploadSuccess = false
        var uploadSynced = 0
        var uploadFailed = 0

        // Get all local journal entries
        val localEntries = getLocalJournalEntries(context)

        if (localEntries.isNotEmpty()) {
            val uploadSyncedEntries = AtomicInteger(0)
            val uploadFailedEntries = AtomicInteger(0)
            val totalEntries = localEntries.size

            for ((index, entry) in localEntries.withIndex()) {
                try {
                    val success = saveRemoteJournalEntry(
                        context,
                        entry.dateString,
                        entry.content,
                        entry.imagePath
                    )

                    if (success) {
                        uploadSyncedEntries.incrementAndGet()
                    } else {
                        uploadFailedEntries.incrementAndGet()
                    }

                    // Update progress for upload phase
                    onProgress("Uploading", index + 1, totalEntries,
                        uploadSyncedEntries.get(), uploadFailedEntries.get())

                } catch (e: Exception) {
                    Log.e("SynchronizeJournalEntries", "Error uploading entry ${entry.dateString}: ${e.message}")
                    uploadFailedEntries.incrementAndGet()
                    onProgress("Uploading", index + 1, totalEntries,
                        uploadSyncedEntries.get(), uploadFailedEntries.get())
                }
            }

            uploadSuccess = uploadFailedEntries.get() == 0
            uploadSynced = uploadSyncedEntries.get()
            uploadFailed = uploadFailedEntries.get()
            Log.i("SynchronizeJournalEntries", "Upload completed: $uploadSynced synced, $uploadFailed failed")
        } else {
            Log.i("SynchronizeJournalEntries", "No local entries to upload")
            uploadSuccess = true
            uploadSynced = 0
            uploadFailed = 0
        }

        // PHASE 2: Download remote entries
        var downloadSuccess = false
        var downloadSynced = 0
        var downloadFailed = 0

        // Use updated fetchRemoteJournalEntries function
        fetchRemoteJournalEntries(
            context = context,
            onProgress = { current, total, synced, failed ->
                // Update progress for download phase
                onProgress("Downloading", current, total, synced, failed)
            },
            onComplete = { success, synced, failed ->
                downloadSuccess = success
                downloadSynced = synced
                downloadFailed = failed
                Log.i("SynchronizeJournalEntries", "Download completed: $downloadSynced synced, $downloadFailed failed")

                // Final completion callback with both phases results
                onComplete(
                    uploadSuccess, uploadSynced, uploadFailed,
                    downloadSuccess, downloadSynced, downloadFailed
                )
            }
        )

    } catch (e: Exception) {
        Log.e("SynchronizeJournalEntries", "Synchronization failed: ${e.message}")
        onComplete(false, 0, 0, false, 0, 0)
    }
}