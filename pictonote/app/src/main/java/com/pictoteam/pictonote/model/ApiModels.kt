package com.pictoteam.pictonote.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
)


@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    // list of generated content candidates
    val candidates: List<Candidate>?,

    // metadata about tokens used
    val usageMetadata: UsageMetadata?,

    // model version used for generation
    val modelVersion: String?
    // val promptFeedback: PromptFeedback? // appears based on safety settings
)

@JsonClass(generateAdapter = true)
data class Candidate(

    val content: Content?,

    // reason generation stopped
    val finishReason: String?,
    val citationMetadata: CitationMetadata?,
    val avgLogprobs: Double?
)

@JsonClass(generateAdapter = true)
data class CitationMetadata(
    val citationSources: List<CitationSource>?
)

@JsonClass(generateAdapter = true)
data class CitationSource(
    // start index in text where citation applies
    val startIndex: Int?,

    // end index in text where citation applies
    val endIndex: Int?,

    // uri of the cited source
    val uri: String?
    // val license: String?
)

@JsonClass(generateAdapter = true)
data class UsageMetadata(
    // token counts
    val promptTokenCount: Int?,
    val candidatesTokenCount: Int?,
    val totalTokenCount: Int?,

    // detailed token breakdowns
    val promptTokensDetails: List<TokenDetails>?,
    val candidatesTokensDetails: List<TokenDetails>?
)

@JsonClass(generateAdapter = true)
data class TokenDetails(
    // the modality type
    val modality: String?,
    // token count for this modality
    val tokenCount: Int?
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>?,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    // text content
    val text: String?

)