package com.pictoteam.pictonote.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,

)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?,
    val usageMetadata: UsageMetadata?,
    val modelVersion: String?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?,
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
    val startIndex: Int?,
    val endIndex: Int?,
    val uri: String?
)

@JsonClass(generateAdapter = true)
data class UsageMetadata(
    val promptTokenCount: Int?,
    val candidatesTokenCount: Int?,
    val totalTokenCount: Int?,
    val promptTokensDetails: List<TokenDetails>?,
    val candidatesTokensDetails: List<TokenDetails>?
)

@JsonClass(generateAdapter = true)
data class TokenDetails(
    val modality: String?,
    val tokenCount: Int?
)


@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>?,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String?
)