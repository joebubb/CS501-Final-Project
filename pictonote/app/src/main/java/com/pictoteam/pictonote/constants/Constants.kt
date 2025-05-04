// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/constants/Constants.kt
package com.pictoteam.pictonote.constants

import java.time.format.DateTimeFormatter

// Filesystem
const val JOURNAL_DIR = "journal_entries"
const val JOURNAL_IMAGE_DIR = "journal_images"
const val IMAGE_URI_MARKER = "IMAGE_URI::" // Ensure this matches JournalViewModel

// Date/Time Formatting
val filenameDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val filenameDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
val archiveDisplayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
val editDisplayDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
val viewEntryDisplayDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a") // Added for View Screen

// Navigation
const val ARG_ENTRY_FILE_PATH = "entryFilePath"
const val ROUTE_HOME = "home"
const val ROUTE_ARCHIVE = "archive"
const val ROUTE_JOURNAL = "journal" // Base route for editing/creating
const val ROUTE_SETTINGS = "settings"
const val ROUTE_VIEW_ENTRY = "view_entry" // New route for viewing

// Route patterns with arguments
const val ROUTE_JOURNAL_WITH_OPTIONAL_ARG = "$ROUTE_JOURNAL?$ARG_ENTRY_FILE_PATH={$ARG_ENTRY_FILE_PATH}"
const val ROUTE_VIEW_ENTRY_WITH_ARG = "$ROUTE_VIEW_ENTRY/{$ARG_ENTRY_FILE_PATH}" // Mandatory argument for viewing