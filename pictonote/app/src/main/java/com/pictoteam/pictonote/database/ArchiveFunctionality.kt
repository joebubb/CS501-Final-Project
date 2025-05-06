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

        // Extract image path from content if not provided directly
        val finalImagePath = imagePath ?: extractImagePathFromContent(content)

        // If there's an image, upload it to Firebase Storage
        if (!finalImagePath.isNullOrEmpty()) {
            try {
                val storage = FirebaseStorage.getInstance()
                val storageRef = storage.reference

                // Extract filename from path
                val imageFileName = finalImagePath.substringAfterLast("/")

                // Create a reference with user ID and date for organization
                val imageRef = storageRef.child("users/$userId/journal_images/$dateString/$imageFileName")

                // Find the image file
                val imageFile = File(context.filesDir, finalImagePath)
                if (imageFile.exists()) {
                    // Upload file directly without compression for better quality
                    val uploadTask = imageRef.putFile(imageFile.toUri()).await()

                    // Get the public download URL
                    imageUrl = imageRef.downloadUrl.await().toString()
                    Log.i("SaveRemoteJournalEntry", "Image uploaded: $imageUrl")
                } else {
                    Log.w("SaveRemoteJournalEntry", "Image file not found: $finalImagePath")
                }
            } catch (e: Exception) {
                Log.e("SaveRemoteJournalEntry", "Error uploading image: ${e.message}", e)
                // Continue even if image upload fails
            }
        }

        // Create entry data including image URL if available
        val journalEntry = hashMapOf(
            "date" to dateString,
            "content" to content,
            "imagePath" to finalImagePath,
            "imageUrl" to imageUrl,
            "lastModified" to System.currentTimeMillis()
        )

        // Save entry to Firestore
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
}

// Function to extract image path from content that uses IMAGE_URI:: format
fun extractImagePathFromContent(content: String): String? {
    val regex = """IMAGE_URI::([^\s]+)""".toRegex()
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

        // Handle different URL formats
        val imageRef = try {
            // Try direct reference from URL
            storage.getReferenceFromUrl(remoteUrl)
        } catch (e: IllegalArgumentException) {
            // If URL format is not valid for getReferenceFromUrl, try to extract path
            Log.w("DownloadImage", "Could not get direct reference, trying to parse URL: ${e.message}")

            // Parse URL to get path components
            val uri = Uri.parse(remoteUrl)
            val path = uri.path?.removePrefix("/") ?: throw IOException("Invalid URL format")

            // Create reference from path components
            storage.reference.child(path)
        }

        // Create the images directory if it doesn't exist
        val imagesDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        // Create a local file to save the image
        val localFile = File(context.filesDir, localImagePath)

        // Create parent directories if they don't exist
        localFile.parentFile?.mkdirs()

        // Check if image already exists and is valid
        if (localFile.exists() && localFile.length() > 0) {
            try {
                // Validate existing file by trying to decode it
                val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                if (bitmap != null) {
                    Log.i("DownloadImage", "Image already exists locally and is valid: ${localFile.absolutePath}")
                    return true
                }
            } catch (e: Exception) {
                Log.w("DownloadImage", "Existing local image is corrupted, redownloading")
                // Continue with download if validation fails
            }
        }

        // Download with progress tracking for large files
        val ONE_MEGABYTE: Long = 1024 * 1024

        withContext(Dispatchers.IO) {
            // For small files: simple download
            try {
                // First try with file metadata to check size
                val metadata = imageRef.metadata.await()

                if (metadata.sizeBytes <= ONE_MEGABYTE) {
                    // Small file - download directly
                    val bytes = imageRef.getBytes(ONE_MEGABYTE).await()
                    FileOutputStream(localFile).use { it.write(bytes) }
                } else {
                    // Large file - use stream download
                    val outputStream = FileOutputStream(localFile)
                    imageRef.getStream { taskSnapshot, inputStream ->
                        val buffer = ByteArray(16384) // 16KB buffer
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.close()
                    }.await()
                }

                Log.i("DownloadImage", "Image downloaded to: ${localFile.absolutePath}")
                true
            } catch (e: Exception) {
                // Handle exception and clean up failed download
                Log.e("DownloadImage", "Error during file download: ${e.message}")
                if (localFile.exists()) {
                    localFile.delete()
                }
                throw e
            }
        }

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

// Enhanced version of synchronizeJournalEntries that properly handles images
suspend fun synchronizeJournalEntries(
    context: Context,
    onProgress: (phase: String, current: Int, total: Int, syncedEntries: Int, failedEntries: Int) -> Unit = { _, _, _, _, _ -> },
    onComplete: (uploadSuccess: Boolean, uploadSynced: Int, uploadFailed: Int,
                 downloadSuccess: Boolean, downloadSynced: Int, downloadFailed: Int) -> Unit
) {
    try {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("SynchronizeJournalEntries", "No user is signed in")
            onComplete(false, 0, 0, false, 0, 0)
            return
        }

        val userId = user.uid
        val db = PictoNoteApplication.firestore

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
                    // Extract image path using the correct format
                    val imagePath = extractImagePathFromContent(entry.content)

                    // If this entry has an image, sync it first
                    var imageUrl: String? = null
                    if (!imagePath.isNullOrEmpty()) {
                        // Synchronize the image with Firebase Storage
                        imageUrl = synchronizeImage(context, imagePath, userId)
                    }

                    // Now create a journal entry document for Firestore
                    val journalEntry = hashMapOf(
                        "date" to entry.dateString,
                        "content" to entry.content,
                        "imagePath" to imagePath,
                        "imageUrl" to imageUrl,
                        "lastModified" to System.currentTimeMillis()
                    )

                    // Save to Firestore
                    db.collection("users")
                        .document(userId)
                        .collection("journal_entries")
                        .document(entry.dateString)
                        .set(journalEntry)
                        .await()

                    uploadSyncedEntries.incrementAndGet()

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

        // Get remote entries from Firestore
        val remoteEntries = try {
            db.collection("users")
                .document(userId)
                .collection("journal_entries")
                .get()
                .await()
        } catch (e: Exception) {
            Log.e("SynchronizeJournalEntries", "Error fetching remote entries: ${e.message}")
            onComplete(uploadSuccess, uploadSynced, uploadFailed, false, 0, 0)
            return
        }

        val totalRemoteEntries = remoteEntries.documents.size
        if (totalRemoteEntries == 0) {
            Log.i("SynchronizeJournalEntries", "No remote entries found")
            onComplete(uploadSuccess, uploadSynced, uploadFailed, true, 0, 0)
            return
        }

        // Create image directory if it doesn't exist
        val imagesDir = File(context.filesDir, JOURNAL_IMAGE_DIR)
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        val downloadSyncedEntries = AtomicInteger(0)
        val downloadFailedEntries = AtomicInteger(0)

        // Process each remote entry
        for ((index, document) in remoteEntries.documents.withIndex()) {
            try {
                val dateString = document.id
                val content = document.getString("content") ?: ""
                val imageUrl = document.getString("imageUrl")
                val remotePath = document.getString("imagePath")

                // If this entry has an image URL, download it
                var updatedContent = content
                if (!imageUrl.isNullOrEmpty() && !remotePath.isNullOrEmpty()) {
                    // Generate local filename from remote path
                    val filename = remotePath.substringAfterLast("/")
                    val localImagePath = "$JOURNAL_IMAGE_DIR/$filename"

                    // Download the image
                    val downloadSuccess = downloadImageFromStorage(
                        context,
                        imageUrl,
                        localImagePath
                    )

                    if (downloadSuccess) {
                        // If the download was successful, make sure content has the correct IMAGE_URI format
                        val existingImageRegex = """IMAGE_URI::([^\s]+)""".toRegex()
                        updatedContent = if (existingImageRegex.containsMatchIn(content)) {
                            // Replace existing path
                            content.replace(existingImageRegex, "IMAGE_URI::$localImagePath")
                        } else {
                            // Add IMAGE_URI tag at the beginning of content
                            "IMAGE_URI::$localImagePath $content"
                        }
                    }
                }

                // Save the entry locally
                val saveSuccess = saveLocalJournalEntry(
                    context,
                    dateString,
                    updatedContent
                )

                if (saveSuccess) {
                    downloadSyncedEntries.incrementAndGet()
                } else {
                    downloadFailedEntries.incrementAndGet()
                }

                // Update progress
                onProgress("Downloading", index + 1, totalRemoteEntries,
                    downloadSyncedEntries.get(), downloadFailedEntries.get())

            } catch (e: Exception) {
                Log.e("SynchronizeJournalEntries", "Error downloading entry: ${e.message}")
                downloadFailedEntries.incrementAndGet()
                onProgress("Downloading", index + 1, totalRemoteEntries,
                    downloadSyncedEntries.get(), downloadFailedEntries.get())
            }
        }

        downloadSuccess = downloadFailedEntries.get() == 0
        downloadSynced = downloadSyncedEntries.get()
        downloadFailed = downloadFailedEntries.get()

        // Final completion callback with both phases results
        onComplete(
            uploadSuccess, uploadSynced, uploadFailed,
            downloadSuccess, downloadSynced, downloadFailed
        )

    } catch (e: Exception) {
        Log.e("SynchronizeJournalEntries", "Synchronization failed: ${e.message}")
        onComplete(false, 0, 0, false, 0, 0)
    }
}

// Function to synchronize a specific image with Firebase Storage
suspend fun synchronizeImage(
    context: Context,
    localImagePath: String,
    userId: String
): String? {
    return try {
        val imageFile = File(context.filesDir, localImagePath)
        if (!imageFile.exists()) {
            Log.e("SyncImage", "Image file does not exist: $localImagePath")
            return null
        }

        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference

        // Extract filename from path
        val imageFileName = localImagePath.substringAfterLast("/")

        // Create a reference using a consistent path structure
        // Use dateString from filename if available (assuming IMG_YYYY-MM-DD format)
        val datePart = try {
            val dateMatcher = "IMG_(\\d{4}-\\d{2}-\\d{2})".toRegex().find(imageFileName)
            dateMatcher?.groupValues?.get(1) ?: "undated"
        } catch (e: Exception) {
            "undated"
        }

        val imageRef = storageRef.child("users/$userId/journal_images/$datePart/$imageFileName")

        // Upload file
        val uploadTask = imageRef.putFile(imageFile.toUri()).await()

        // Get and return the public download URL
        val downloadUrl = imageRef.downloadUrl.await().toString()
        Log.i("SyncImage", "Image uploaded: $downloadUrl")

        return downloadUrl
    } catch (e: Exception) {
        Log.e("SyncImage", "Error synchronizing image: ${e.message}", e)
        return null
    }
}

// Batch upload multiple images to Firebase Storage
suspend fun batchUploadImagesToStorage(
    context: Context,
    imagePaths: List<String>,
    onProgress: (current: Int, total: Int, successful: Int, failed: Int) -> Unit = { _, _, _, _ -> }
): List<Pair<String, String?>> {
    val user = FirebaseAuth.getInstance().currentUser ?: return emptyList()
    val userId = user.uid
    val storage = FirebaseStorage.getInstance()
    val storageRef = storage.reference

    val results = mutableListOf<Pair<String, String?>>() // Path to URL mapping
    var successCount = 0
    var failCount = 0

    for ((index, imagePath) in imagePaths.withIndex()) {
        try {
            val imageFile = File(context.filesDir, imagePath)
            if (!imageFile.exists()) {
                results.add(Pair(imagePath, null))
                failCount++
                onProgress(index + 1, imagePaths.size, successCount, failCount)
                continue
            }

            // Create a unique ID for the image
            val imageFileName = imagePath.substringAfterLast("/")
            val timestamp = System.currentTimeMillis()
            val imageRef = storageRef.child("users/$userId/journal_images/$timestamp-$imageFileName")

            // Read and optionally compress the image
            val bytes = withContext(Dispatchers.IO) {
                if (imageFile.length() > 2 * 1024 * 1024) { // If larger than 2MB
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    stream.toByteArray()
                } else {
                    // For smaller images, read directly
                    val buffer = ByteArray(imageFile.length().toInt())
                    val inputStream = FileInputStream(imageFile)
                    inputStream.read(buffer)
                    inputStream.close()
                    buffer
                }
            }

            // Upload with resumable upload for larger files
            val uploadTask = if (bytes.size > 1024 * 1024) {
                val stream = bytes.inputStream()
                imageRef.putStream(stream)
            } else {
                imageRef.putBytes(bytes)
            }

            // Wait for upload to complete
            uploadTask.await()

            // Get download URL
            val downloadUrl = imageRef.downloadUrl.await().toString()

            // Add to results
            results.add(Pair(imagePath, downloadUrl))
            successCount++

            Log.i("BatchUpload", "Uploaded image $index: $imagePath -> $downloadUrl")
        } catch (e: Exception) {
            Log.e("BatchUpload", "Failed to upload image $imagePath: ${e.message}")
            results.add(Pair(imagePath, null))
            failCount++
        }

        // Update progress
        onProgress(index + 1, imagePaths.size, successCount, failCount)
    }

    return results
}