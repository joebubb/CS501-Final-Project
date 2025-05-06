package com.pictoteam.pictonote.constants

import java.time.format.DateTimeFormatter

// Storage directory configuration for journal entries and their associated images
const val JOURNAL_DIR = "journal_entries"
const val JOURNAL_IMAGE_DIR = "journal_images"
const val IMAGE_URI_MARKER = "IMAGE_URI::" // Special marker to identify image references in entry content

// Date and time formatters for various application functions
val filenameDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val filenameDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
val archiveDisplayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
val editDisplayDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
val viewEntryDisplayDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a") // Used for full date/time display on entry view screen

// Navigation constants
const val ARG_ENTRY_FILE_PATH = "entryFilePath" // Navigation argument for passing entry paths between screens
const val ROUTE_HOME = "home" // Main screen route
const val ROUTE_ARCHIVE = "archive" // Journal archive/history screen
const val ROUTE_JOURNAL = "journal" // Editor screen for creating/editing entries
const val ROUTE_SETTINGS = "settings" // Application settings screen
const val ROUTE_VIEW_ENTRY = "view_entry" // Read-only entry viewer screen

// Navigation route patterns with parameter definitions
const val ROUTE_JOURNAL_WITH_OPTIONAL_ARG = "$ROUTE_JOURNAL?$ARG_ENTRY_FILE_PATH={$ARG_ENTRY_FILE_PATH}" // Optional path for editing existing entries
const val ROUTE_VIEW_ENTRY_WITH_ARG = "$ROUTE_VIEW_ENTRY/{$ARG_ENTRY_FILE_PATH}" // Required path parameter for viewing specific entries