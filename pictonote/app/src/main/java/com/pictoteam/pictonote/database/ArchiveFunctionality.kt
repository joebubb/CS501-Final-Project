package com.pictoteam.pictonote.database

import android.content.Context
import android.util.Log
import com.pictoteam.pictonote.constants.JOURNAL_DIR
import com.pictoteam.pictonote.constants.filenameDateFormatter
import java.io.File
import java.io.IOException
import java.time.LocalDate

// Saves today's journal entry to local storage
// Overwrites any existing entry for today
fun saveLocalJournalEntry(context: Context, entry: String) {
    if (entry.isBlank()) {
        Log.w("SaveJournalEntry", "Saving empty entry - might be clearing previous content")
    }

    try {
        val directory = File(context.filesDir, JOURNAL_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Format the filename with today's date
        val todayDateString = LocalDate.now().format(filenameDateFormatter)
        val filename = "journal_$todayDateString.txt"
        val file = File(directory, filename)

        file.writeText(entry)

        Log.i("SaveJournalEntry", "Journal Entry Saved/Updated: ${file.absolutePath}")

    } catch (e: IOException) {
        Log.e("SaveJournalEntry", "Error saving journal entry to file", e)
        // We should probably show a toast or something to the user here
    } catch (e: Exception) {
        Log.e("SaveJournalEntry", "An unexpected error occurred during saving", e)
    }
}

