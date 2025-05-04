package com.pictoteam.pictonote

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// TODO: Add future archive-related helper functions here.

/*
fun saveLocalJournalEntry(context: Context, entry: String) {
    if (entry.isBlank()) {
        Log.w("SaveJournalEntry", "Attempted to save an empty entry. Overwriting if exists.")
    }

    try {
        val directory = File(context.filesDir, JOURNAL_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // create filename using the current date ONLY
        val todayDateString = LocalDate.now().format(filenameDateFormatter) // Uses the old formatter
        val filename = "journal_$todayDateString.txt"
        val file = File(directory, filename)

        file.writeText(entry)

        Log.i("SaveJournalEntry", "Journal Entry Saved/Updated: ${file.absolutePath}")

    } catch (e: IOException) {
        Log.e("SaveJournalEntry", "Error saving journal entry to file", e)
        // Handle the error appropriately
    } catch (e: Exception) {
        Log.e("SaveJournalEntry", "An unexpected error occurred during saving", e)
    }
}
*/