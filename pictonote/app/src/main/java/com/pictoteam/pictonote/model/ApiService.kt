package com.pictoteam.pictonote.api

import com.pictoteam.pictonote.model.GenerateContentRequest // Import request model
import com.pictoteam.pictonote.model.GenerateContentResponse // Import response model
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") modelName: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}