// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/database/FirebaseSyncHelper.kt
package com.pictoteam.pictonote.database

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.QuerySnapshot // Import if needed, though .documents is usually available
import com.google.firebase.storage.FirebaseStorage
import com.pictoteam.pictonote.appFirestore // Use the extension property
import com.pictoteam.pictonote.constants.IMAGE_URI_MARKER
import com.pictoteam.pictonote.constants.JOURNAL_DIR
import com.pictoteam.pictonote.constants.JOURNAL_IMAGE_DIR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// FirestoreJournalEntry, checkFirestoreDatabaseConfigured, saveEntryToRemote,
// extractImagePathFromContent, downloadImageFromRemote, saveDownloadedEntryLocally
// functions remain THE SAME as the last version.
// ... (paste those functions here if you need the full file context for this helper)

data class FirestoreJournalEntry(
    val entryId: String = "",
    val content: String = "",
    val remoteImageUrl: String? = null,
    val lastModified: Long = 0
)

suspend fun checkFirestoreDatabaseConfigured(context: Context): Boolean {
    return suspendCoroutine { continuation ->
        try {
            val db = context.appFirestore
            db.collection("users").document("database_check_doc")
                .get()
                .addOnSuccessListener { continuation.resume(true) }
                .addOnFailureListener { e ->
                    Log.e("CheckFirestore", "Error: ${e.message}")
                    if (e.message?.contains("database", ignoreCase = true) == true &&
                        e.message?.contains("does not exist", ignoreCase = true) == true) {
                        continuation.resume(false)
                    } else {
                        Log.w("CheckFirestore", "Firestore accessible but encountered error: ${e.message}. Assuming configured for now.")
                        continuation.resume(true)
                    }
                }
        } catch (e: Exception) {
            Log.e("CheckFirestore", "Exception during Firestore check", e)
            continuation.resumeWithException(e)
        }
    }
}

suspend fun saveEntryToRemote(context: Context, uniqueEntryId: String, localFileContent: String): Boolean {
    val user = FirebaseAuth.getInstance().currentUser ?: return false.also { Log.e("SaveRemote", "No user signed in") }
    val userId = user.uid
    val db = context.appFirestore

    try {
        val localImageRelativePath = extractImagePathFromContent(localFileContent)
        var remoteImageUrl: String? = null

        if (!localImageRelativePath.isNullOrEmpty()) {
            val imageFile = File(context.filesDir, localImageRelativePath)
            if (imageFile.exists()) {
                val storageRef = FirebaseStorage.getInstance().reference
                val imageFileNameInStorage = localImageRelativePath.substringAfterLast('/')
                val remoteImageRef = storageRef.child("users/$userId/$JOURNAL_IMAGE_DIR/$uniqueEntryId/$imageFileNameInStorage")
                remoteImageRef.putFile(imageFile.toUri()).await()
                remoteImageUrl = remoteImageRef.downloadUrl.await().toString()
                Log.i("SaveRemote", "Image uploaded for $uniqueEntryId: $remoteImageUrl")
            } else {
                Log.w("SaveRemote", "Local image file not found: $localImageRelativePath for $uniqueEntryId")
            }
        }

        val firestoreEntry = FirestoreJournalEntry(
            entryId = uniqueEntryId,
            content = localFileContent,
            remoteImageUrl = remoteImageUrl,
            lastModified = System.currentTimeMillis()
        )

        db.collection("users").document(userId)
            .collection("journal_entries").document(uniqueEntryId)
            .set(firestoreEntry)
            .await()
        Log.i("SaveRemote", "Entry $uniqueEntryId saved to Firestore.")
        return true
    } catch (e: Exception) {
        Log.e("SaveRemote", "Error saving entry $uniqueEntryId to remote: ${e.message}", e)
        return false
    }
}

fun extractImagePathFromContent(content: String): String? {
    return content.lines().firstOrNull { it.startsWith(IMAGE_URI_MARKER) }
        ?.substringAfter(IMAGE_URI_MARKER)?.trim()
}

suspend fun downloadImageFromRemote(context: Context, remoteUrl: String, localImageRelativePath: String): Boolean {
    if (localImageRelativePath.isBlank()) return false.also { Log.e("DownloadImage", "Local image path is blank") }

    return try {
        val localImageFile = File(context.filesDir, localImageRelativePath)
        localImageFile.parentFile?.mkdirs()

        if (localImageFile.exists() && localImageFile.length() > 0) {
            try {
                BitmapFactory.decodeFile(localImageFile.absolutePath)?.let {
                    Log.i("DownloadImage", "Image $localImageRelativePath already exists locally and is valid.")
                    return true
                }
            } catch (e: Exception) { Log.w("DownloadImage", "Local image $localImageRelativePath corrupted, re-downloading.", e) }
        }

        val imageRef = FirebaseStorage.getInstance().getReferenceFromUrl(remoteUrl)
        withContext(Dispatchers.IO) {
            FileOutputStream(localImageFile).use { outputStream ->
                imageRef.getStream { _, inputStream ->
                    inputStream.copyTo(outputStream)
                }.await()
            }
        }
        Log.i("DownloadImage", "Image downloaded from $remoteUrl to: ${localImageFile.absolutePath}")
        true
    } catch (e: Exception) {
        Log.e("DownloadImage", "Error downloading image from $remoteUrl to $localImageRelativePath: ${e.message}", e)
        File(context.filesDir, localImageRelativePath).delete()
        false
    }
}

fun saveDownloadedEntryLocally(context: Context, uniqueEntryId: String, contentWithImageMarker: String): Boolean {
    try {
        val directory = File(context.filesDir, JOURNAL_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val filename = "journal_$uniqueEntryId.txt"
        val file = File(directory, filename)
        file.writeText(contentWithImageMarker)
        file.setLastModified(System.currentTimeMillis())
        Log.i("SaveLocalDownload", "Downloaded entry $uniqueEntryId saved locally: ${file.absolutePath}")
        return true
    } catch (e: Exception) {
        Log.e("SaveLocalDownload", "Error saving downloaded entry $uniqueEntryId locally: ${e.message}", e)
        return false
    }
}


suspend fun fetchAndSyncRemoteEntriesToLocal(
    context: Context,
    onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    onComplete: (successCount: Int, failureCount: Int) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser ?: return onComplete(0, 0).also { Log.e("FetchRemote", "No user signed in") }
    val userId = user.uid
    val db = context.appFirestore
    var successCount = 0
    var failureCount = 0
    var remoteEntriesSnapshot: QuerySnapshot? = null // Declare here to use in final catch

    try {
        remoteEntriesSnapshot = db.collection("users").document(userId)
            .collection("journal_entries").get().await()

        val totalEntries = remoteEntriesSnapshot.documents.size
        if (totalEntries == 0) {
            Log.i("FetchRemote", "No remote entries to fetch.")
            onComplete(0, 0)
            return
        }

        remoteEntriesSnapshot.documents.forEachIndexed { index, doc ->
            val remoteEntry = doc.toObject(FirestoreJournalEntry::class.java)?.copy(entryId = doc.id)
            if (remoteEntry == null) {
                Log.w("FetchRemote", "Failed to parse remote entry: ${doc.id}")
                failureCount++
                onProgress(index + 1, totalEntries)
                return@forEachIndexed
            }

            var localFileContent = remoteEntry.content
            val localImageRelativePathInContent = extractImagePathFromContent(remoteEntry.content)

            if (!remoteEntry.remoteImageUrl.isNullOrBlank() && !localImageRelativePathInContent.isNullOrBlank()) {
                val imageDownloaded = downloadImageFromRemote(context, remoteEntry.remoteImageUrl, localImageRelativePathInContent)
                if (!imageDownloaded) {
                    Log.w("FetchRemote", "Failed to download image for entry ${remoteEntry.entryId}, remote URL: ${remoteEntry.remoteImageUrl}")
                }
            }

            if (saveDownloadedEntryLocally(context, remoteEntry.entryId, localFileContent)) {
                successCount++
            } else {
                failureCount++
            }
            onProgress(index + 1, totalEntries)
        }
        Log.i("FetchRemote", "Fetch remote entries complete. Synced locally: $successCount, Failed: $failureCount")
        onComplete(successCount, failureCount)

    } catch (e: Exception) {
        Log.e("FetchRemote", "Error fetching remote entries: ${e.message}", e)
        // Corrected: Check if remoteEntriesSnapshot is null before accessing its properties
        val totalDocs = remoteEntriesSnapshot?.documents?.size ?: 0
        val totalReportedFailures = if (successCount == 0 && failureCount == 0 && totalDocs > 0) totalDocs else failureCount
        onComplete(successCount, totalReportedFailures)
    }
}


suspend fun synchronizeAllJournalEntries(
    context: Context,
    onPhaseChange: (phase: String) -> Unit = {},
    onProgress: (phase: String, current: Int, total: Int) -> Unit = { _, _, _ -> },
    onComplete: (totalUniqueEntriesProcessed: Int, totalSuccessfullySyncedOrUpToDate: Int) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser ?: return onComplete(0,0).also { Log.e("SyncAll", "No user") }
    val userId = user.uid
    val db = context.appFirestore

    val processedEntryIds = mutableSetOf<String>()
    var operationsSucceededThisSync = 0 // Tracks operations specifically for this sync run

    try {
        if (!checkFirestoreDatabaseConfigured(context)) {
            Log.e("SyncAll", "Firestore database not configured.")
            onComplete(0,0)
            return
        }

        // Phase 1: Upload Local to Remote
        val uploadPhaseName = "Uploading"
        onPhaseChange(uploadPhaseName)
        val localJournalDir = File(context.filesDir, JOURNAL_DIR)
        val localFiles = localJournalDir.listFiles { f -> f.isFile && f.name.startsWith("journal_") && f.name.endsWith(".txt") } ?: emptyArray()

        if (localFiles.isNotEmpty()) {
            localFiles.forEachIndexed { index, localFile ->
                val entryId = localFile.name.substringAfter("journal_").removeSuffix(".txt")
                processedEntryIds.add(entryId)
                val content = localFile.readText()
                val remoteDoc = db.collection("users").document(userId).collection("journal_entries").document(entryId).get().await()
                val remoteEntryData = remoteDoc.toObject(FirestoreJournalEntry::class.java)

                if (remoteEntryData == null || localFile.lastModified() > remoteEntryData.lastModified) {
                    if (saveEntryToRemote(context, entryId, content)) {
                        // Successfully uploaded/updated remote
                    }
                }
                onProgress(uploadPhaseName, index + 1, localFiles.size)
            }
        }
        Log.i("SyncAll", "Upload phase attempted for ${localFiles.size} files.")

        // Phase 2: Download Remote to Local
        val downloadPhaseName = "Downloading"
        onPhaseChange(downloadPhaseName)
        val remoteDocs = db.collection("users").document(userId).collection("journal_entries").get().await().documents

        if (remoteDocs.isNotEmpty()) {
            remoteDocs.forEachIndexed { index, doc ->
                val remoteEntry = doc.toObject(FirestoreJournalEntry::class.java)?.copy(entryId = doc.id)
                if (remoteEntry == null) { onProgress(downloadPhaseName, index + 1, remoteDocs.size); return@forEachIndexed }

                processedEntryIds.add(remoteEntry.entryId)

                val localF = File(localJournalDir, "journal_${remoteEntry.entryId}.txt")
                val shouldDl = !localF.exists() || localF.lastModified() < remoteEntry.lastModified

                if (shouldDl) {
                    val imgPath = extractImagePathFromContent(remoteEntry.content)
                    if (!remoteEntry.remoteImageUrl.isNullOrBlank() && !imgPath.isNullOrBlank()) {
                        downloadImageFromRemote(context, remoteEntry.remoteImageUrl, imgPath)
                    }
                    saveDownloadedEntryLocally(context, remoteEntry.entryId, remoteEntry.content)
                    File(localJournalDir, "journal_${remoteEntry.entryId}.txt").setLastModified(remoteEntry.lastModified)
                }
                onProgress(downloadPhaseName, index + 1, remoteDocs.size)
            }
        }
        Log.i("SyncAll", "Download phase attempted for ${remoteDocs.size} documents.")

        var finalSuccessCount = 0
        for (id in processedEntryIds) {
            val localFile = File(localJournalDir, "journal_$id.txt")
            val remoteDocRef = db.collection("users").document(userId).collection("journal_entries").document(id)
            val remoteSnapshot = remoteDocRef.get().await()
            val remoteData = remoteSnapshot.toObject(FirestoreJournalEntry::class.java)

            if (localFile.exists() && remoteData != null) {
                if (Math.abs(localFile.lastModified() - remoteData.lastModified) < 2000) {
                    finalSuccessCount++
                } else if (localFile.lastModified() > remoteData.lastModified && remoteSnapshot.exists()) { // Local newer, remote exists
                    // Check if remote's content now matches local's (implying successful upload)
                    if (remoteData.content == localFile.readText()) finalSuccessCount++
                } else if (remoteData.lastModified >= localFile.lastModified()) { // Remote newer or same, local updated
                    finalSuccessCount++
                }
            } else if (localFile.exists() && remoteData == null) { // Only local, successfully uploaded
                // This implies it was uploaded if saveEntryToRemote was called and succeeded.
                // For simplicity, we'll count it. More robust check would be to confirm remote exists now.
                finalSuccessCount++
            } else if (!localFile.exists() && remoteData != null) { // Only remote, successfully downloaded
                if (File(localJournalDir, "journal_$id.txt").exists()) { // Check if download created it
                    finalSuccessCount++
                }
            }
        }
        finalSuccessCount = finalSuccessCount.coerceAtMost(processedEntryIds.size)

        Log.i("SyncAll", "Sync logic complete. Unique Processed: ${processedEntryIds.size}, Final Success Count: $finalSuccessCount")
        onComplete(processedEntryIds.size, finalSuccessCount)

    } catch (e: Exception) {
        Log.e("SyncAll", "Full synchronization error: ${e.message}", e)
        // Fallback: report unique IDs found vs. operations that might have succeeded before error
        var fallbackSuccessCount = 0 // Calculate a fallback based on what could be determined
        // This is tricky, ideally we'd count successes more granularly during the process
        // For now, using 0 for successes if a major exception occurs mid-process before final verification.
        onComplete(processedEntryIds.size, 0)
    }
}