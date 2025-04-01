package com.pictoteam.pictonote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- Request Data Classes ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

// --- Response Data Classes (Simplified Example) ---
// Based on typical Gemini API responses. You might need to adjust based on the full response structure.

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?,
    @Json(name = "promptFeedback") // Example of handling different JSON names
    val promptFeedback: PromptFeedback?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentResponsePart?,
    val finishReason: String?,
    val index: Int?,
    val safetyRatings: List<SafetyRating>?
)

@JsonClass(generateAdapter = true)
data class ContentResponsePart( // Renamed to avoid conflict with request 'Content'
    val parts: List<Part>?, // Can reuse the 'Part' class here if the structure is the same
    val role: String?
)

@JsonClass(generateAdapter = true)
data class SafetyRating(
    val category: String?,
    val probability: String?
)

@JsonClass(generateAdapter = true)
data class PromptFeedback(
    val safetyRatings: List<SafetyRating>?
)