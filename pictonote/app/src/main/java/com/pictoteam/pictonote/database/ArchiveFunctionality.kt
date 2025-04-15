package com.pictoteam.pictonote.database

import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

fun getJournalEntryForToday(viewModel: FirestoreViewModel) {
    val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val journalEntryDocument = viewModel.getDb().collection("journalEntries").document(currentDate)

    journalEntryDocument.get()
        .addOnSuccessListener { document ->
            if (document.exists()) {
                println("Document data: ${document.data}")
            } else {
                println("No journal entry found for today.")
            }
        }
        .addOnFailureListener { exception ->
            println("Error getting document: $exception")
        }
}

fun fetchJournalEntryForDate(context: Context, date: String, viewModel: FirestoreViewModel) {
    viewModel.getDb().collection("journalEntries")
        .document(date)
        .get()
        .addOnSuccessListener { document ->
            if (!document.exists()) {
                Toast.makeText(context, "No entry for this date.", Toast.LENGTH_SHORT).show()
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Failed to load journal entry.", Toast.LENGTH_SHORT).show()
        }
}

fun saveJournalEntry(
    context: Context,
    text: String,
    viewModel: FirestoreViewModel,
    onSuccess: () -> Unit = {},
    onFailure: (Exception) -> Unit = {}
) {
    if (text.isBlank()) {
        Toast.makeText(context, "Cannot save empty journal entry.", Toast.LENGTH_SHORT).show()
        return
    }

    val entry = hashMapOf(
        "text" to text,
        "timestamp" to System.currentTimeMillis()
    )
    val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    viewModel.getDb().collection("journalEntries")
        .document(currentDate)
        .set(entry)
        .addOnSuccessListener {
            Toast.makeText(context, "Journal entry saved!", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Failed to save entry.", Toast.LENGTH_SHORT).show()
            onFailure(e)
        }
}
