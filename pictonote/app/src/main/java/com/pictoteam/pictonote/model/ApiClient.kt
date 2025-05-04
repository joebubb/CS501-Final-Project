package com.pictoteam.pictonote.model

import com.pictoteam.pictonote.api.GeminiApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory


object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    // Moshi instance
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // Enable Kotlin support
        .build()

    // Retrofit instance
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }
}