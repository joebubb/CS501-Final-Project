package com.pictoteam.pictonote.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,

)

/* --- Optional Sub-Models for Request (Uncomment and define if used) ---
@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float?,
    val topK: Int?,
    val topP: Float?,
    val maxOutputTokens: Int?,
    val stopSequences: List<String>?
)

@JsonClass(generateAdapter = true)
data class SafetySetting(
    val category: String, // e.g., HARM_CATEGORY_HARASSMENT
    val threshold: String // e.g., BLOCK_MEDIUM_AND_ABOVE
)

@JsonClass(generateAdapter = true)
data class Blob(
    val mimeType: String,
    val data: String // Base64 encoded data
)
*/


// =====================================================================================
// == Response Models (Data received FROM the API)
// =====================================================================================

/**
 * Represents the overall response from the generateContent endpoint.
 */
@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    /** A list of generated content candidates. */
    val candidates: List<Candidate>?,

    /** Metadata about the tokens used in the request and response. */
    val usageMetadata: UsageMetadata?,

    /** The specific model version used for generation. */
    val modelVersion: String?
    // val promptFeedback: PromptFeedback? // Can sometimes appear based on safety settings
)

/**
 * Represents a single generated content candidate.
 */
@JsonClass(generateAdapter = true)
data class Candidate(
    /** The generated content itself. */
    val content: Content?, // Reuses the Content data class (defined below)

    /** The reason generation stopped (e.g., "STOP", "MAX_TOKENS", "SAFETY"). */
    val finishReason: String?,

    /** Metadata about citations found in the generated text. */
    val citationMetadata: CitationMetadata?,

    /** Average log probability of the generated tokens (advanced use). */
    val avgLogprobs: Double?
    // val safetyRatings: List<SafetyRating>? // Can appear based on safety settings
    // val index: Int? // Index of the candidate
)

/**
 * Contains metadata about citations.
 */
@JsonClass(generateAdapter = true)
data class CitationMetadata(
    /** List of citation sources. */
    val citationSources: List<CitationSource>?
)

/**
 * Represents a single source for a citation.
 */
@JsonClass(generateAdapter = true)
data class CitationSource(
    /** Start index (inclusive) in the generated text where the citation applies. */
    val startIndex: Int?,
    /** End index (exclusive) in the generated text where the citation applies. */
    val endIndex: Int?,
    /** URI of the cited source. */
    val uri: String? // Nullable as it wasn't present in all examples
    // val license: String?
)

/**
 * Contains information about token usage.
 */
@JsonClass(generateAdapter = true)
data class UsageMetadata(
    /** Number of tokens in the prompt. */
    val promptTokenCount: Int?,
    /** Number of tokens in the generated candidates. */
    val candidatesTokenCount: Int?,
    /** Total number of tokens. */
    val totalTokenCount: Int?,
    /** Detailed token counts for the prompt. */
    val promptTokensDetails: List<TokenDetails>?,
    /** Detailed token counts for the candidates. */
    val candidatesTokensDetails: List<TokenDetails>?
)

/**
 * Details about token counts for a specific modality.
 */
@JsonClass(generateAdapter = true)
data class TokenDetails(
    /** The modality (e.g., "TEXT"). */
    val modality: String?,
    /** The token count for this modality. */
    val tokenCount: Int?
)

/* --- Optional Sub-Models for Response (Uncomment and define if needed/present) ---
@JsonClass(generateAdapter = true)
data class PromptFeedback(
    val blockReason: String?,
    val safetyRatings: List<SafetyRating>?
)

@JsonClass(generateAdapter = true)
data class SafetyRating(
    val category: String, // e.g., HARM_CATEGORY_DANGEROUS_CONTENT
    val probability: String // e.g., NEGLIGIBLE, LOW, MEDIUM, HIGH
)
*/


// =====================================================================================
// == Shared Models (Used in both Request & Response)
// =====================================================================================

/**
 * Represents a single block of content, usually corresponding to a turn
 * in a conversation or a single prompt input. Used in both request and response.
 */
@JsonClass(generateAdapter = true)
data class Content(
    /** The parts that make up this content block (e.g., text, image data). */
    val parts: List<Part>?, // List can be null or empty

    /** Optional: The role of the author (e.g., "user", "model"). Present in response. */
    val role: String? = null
)

/**
 * Represents a piece of data within a Content block (e.g., a text snippet).
 * Used in both request and response.
 */
@JsonClass(generateAdapter = true)
data class Part(
    /** The text content. */
    val text: String? // Text can be null or empty
    // Add other part types if needed, e.g.:
    // val inlineData: Blob? // Needs Blob class defined (e.g., in Request section)
)