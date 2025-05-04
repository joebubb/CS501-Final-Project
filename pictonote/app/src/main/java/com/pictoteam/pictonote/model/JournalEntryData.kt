package com.pictoteam.pictonote.model

import java.time.LocalDateTime

data class JournalEntryData(
    val filePath: String,
    val fileTimestamp: LocalDateTime?,
    val imageRelativePath: String?,
    var textContent: String
)