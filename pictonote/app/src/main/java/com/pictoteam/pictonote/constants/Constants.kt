package com.pictoteam.pictonote.constants

import java.time.format.DateTimeFormatter

// Filesystem
const val JOURNAL_DIR = "journal_entries"
const val JOURNAL_IMAGE_DIR = "journal_images"
const val IMAGE_URI_MARKER = "IMAGE_URI::"

// Date/Time Formatting
val filenameDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val filenameDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")
val archiveDisplayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
val editDisplayDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

// Navigation
const val ARG_ENTRY_FILE_PATH = "entryFilePath"
const val ROUTE_HOME = "home"
const val ROUTE_ARCHIVE = "archive"
const val ROUTE_JOURNAL = "journal"
const val ROUTE_SETTINGS = "settings"

const val ROUTE_JOURNAL_WITH_OPTIONAL_ARG = "$ROUTE_JOURNAL?$ARG_ENTRY_FILE_PATH={$ARG_ENTRY_FILE_PATH}"