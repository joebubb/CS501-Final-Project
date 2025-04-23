package com.pictoteam.pictonote.api

import com.pictoteam.pictonote.model.GenerateContentRequest // Import request model
import com.pictoteam.pictonote.model.GenerateContentResponse // Import response model
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Defines the API endpoints for the Google Generative Language API (Gemini).
 */
interface GeminiApiService {

    /**
     * Generates content based on the provided request body using a specified model.
     *
     * Corresponds to:
     * POST /v1beta/models/{model}:generateContent?key=YOUR_API_KEY
     *
     * @param modelName The name of the model (e.g., "gemini-1.5-flash-latest").
     * @param apiKey Your API key.
     * @param request The request body containing the prompt.
     * @return A [GenerateContentResponse] containing the generated content.
     */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") modelName: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}