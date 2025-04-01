package com.pictoteam.pictonote.model



import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.pictoteam.pictonote.BuildConfig

import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainViewModel : ViewModel() {
    private val geminiApiKey = BuildConfig.GEMINI_API_KEY
    private val modelName = "gemini-1.5-flash-latest"

    private val _apiResult = MutableLiveData<String>()
    val apiResult: LiveData<String> = _apiResult

    init {
        Log.d("GeminiTestVM", "ViewModel initialized.")
        // Optionally call immediately when ViewModel is created
        // makeSimpleApiCall()
    }


    fun makeSimpleApiCall() {
        _apiResult.value = "Calling API..." // Update UI state

        viewModelScope.launch {
            Log.d("GeminiTestVM", "Coroutine launched. Preparing API call...")
            val prompt = "who was the first president of the usa"
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt))))
            )
            Log.d("GeminiTestVM", "Request Body Created: $request")

            try {
                Log.d("GeminiTestVM", "Calling apiService.generateContent...")
                val response = GeminiApiClient.apiService.generateContent(modelName, geminiApiKey, request)
                Log.d("GeminiTestVM", "API call successful.")

                val generatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (generatedText != null) {
                    Log.i("GeminiTestVM", "Generated Text: $generatedText")
                    _apiResult.postValue(generatedText) // Update LiveData for UI
                } else {
                    Log.w("GeminiTestVM", "Received response, but text content was null or empty.")
                    Log.d("GeminiTestVM", "Full Response: $response")
                    _apiResult.postValue("Received response, but no text found.")
                }

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
                Log.e("GeminiTestVM", "HTTP Error: ${e.code()} - ${e.message()}. Body: $errorBody", e)
                _apiResult.postValue("HTTP Error: ${e.code()}. Check logs.")

            } catch (e: Exception) {
                Log.e("GeminiTestVM", "Generic Error: ${e.message}", e)
                _apiResult.postValue("Error: ${e.message}")
            }
        }
    }
}