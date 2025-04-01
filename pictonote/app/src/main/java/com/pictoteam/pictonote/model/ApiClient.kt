package com.pictoteam.pictonote.model

import com.pictoteam.pictonote.api.GeminiApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory


object GeminiApiClient {

    // Base URL for the Gemini API. MUST end with a slash '/'.
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Moshi instance configured for Kotlin data classes.
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // Enable Kotlin support
        .build()

    // Retrofit instance configured with the base URL and Moshi converter.
    // NO custom OkHttpClient needed if you don't need logging, timeouts, etc.
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        // .client(okHttpClient) // <-- REMOVE THIS LINE
        .addConverterFactory(MoshiConverterFactory.create(moshi)) // Use Moshi for JSON
        .build()

    /**
     * Lazily creates and provides the implementation of [GeminiApiService].
     */
    val apiService: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }
}