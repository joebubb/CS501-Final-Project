package com.pictoteam.pictonote.model

import com.squareup.moshi.JsonClass

// Request model for Gemini API content generation
@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
)

// Main response model from Gemini API
@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?,

    val usageMetadata: UsageMetadata?,

    val modelVersion: String?
)

// Individual response option from Gemini
@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?,

    // Why the generation stopped (e.g., "STOP", "MAX_TOKENS")
    val finishReason: String?,

    val citationMetadata: CitationMetadata?,

    val avgLogprobs: Double?
)

// Metadata for citations in generated content
@JsonClass(generateAdapter = true)
data class CitationMetadata(
    val citationSources: List<CitationSource>?
)

// Individual citation source details
@JsonClass(generateAdapter = true)
data class CitationSource(
    val startIndex: Int?,
    val endIndex: Int?,

    val uri: String?
)

// Token usage statistics for API billing
@JsonClass(generateAdapter = true)
data class UsageMetadata(
    val promptTokenCount: Int?,
    val candidatesTokenCount: Int?,
    val totalTokenCount: Int?,
    val promptTokensDetails: List<TokenDetails>?,
    val candidatesTokensDetails: List<TokenDetails>?
)

// Details of token usage by modality
@JsonClass(generateAdapter = true)
data class TokenDetails(
    val modality: String?,

    val tokenCount: Int?
)

// Content container for messages
@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>?,

    val role: String? = null
)

// Individual content part
@JsonClass(generateAdapter = true)
data class Part(
    val text: String?
)