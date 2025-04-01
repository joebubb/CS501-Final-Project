package com.pictoteam.pictonote.model



import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.pictoteam.pictonote.BuildConfig

import kotlinx.coroutines.launch
import retrofit2.HttpException

class GeminiViewModel : ViewModel() {
    private val geminiApiKey = BuildConfig.GEMINI_API_KEY
    private val modelName = "gemini-1.5-flash-latest"

    private val _apiResult = MutableLiveData<String>()
    val apiResult: LiveData<String> = _apiResult

    private val _journalPromptSuggestion = MutableLiveData<String>()
    val journalPromptSuggestion: LiveData<String> = _journalPromptSuggestion

    init {
        Log.d("GeminiTestVM", "ViewModel initialized.")
    }


    fun makeSimpleApiCall() {
        _apiResult.value = "Calling API..."

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
                    _apiResult.postValue(generatedText)
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

    fun suggestJournalPrompt() {
        _journalPromptSuggestion.value = "Generating prompt suggestion..."

        viewModelScope.launch {
            Log.d("GeminiViewModel", "[JournalPrompt] Coroutine launched. Preparing API call...")
            val promptForGemini = "Suggest a thoughtful and inspiring journal prompt suitable for self-reflection."
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = promptForGemini))))
            )
            Log.d("GeminiViewModel", "[JournalPrompt] Request Body Created: $request")

            try {
                Log.d("GeminiViewModel", "[JournalPrompt] Calling apiService.generateContent...")
                val response = GeminiApiClient.apiService.generateContent(modelName, geminiApiKey, request)
                Log.d("GeminiViewModel", "[JournalPrompt] API call successful.")

                val generatedPrompt = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!generatedPrompt.isNullOrBlank()) {
                    Log.i("GeminiViewModel", "[JournalPrompt] Generated Prompt Suggestion: $generatedPrompt")
                    _journalPromptSuggestion.postValue(generatedPrompt.trim())
                } else {
                    Log.w("GeminiViewModel", "[JournalPrompt] Received response, but prompt text was null or empty.")
                    Log.d("GeminiViewModel", "[JournalPrompt] Full Response: $response")
                    _journalPromptSuggestion.postValue("Could not generate a prompt suggestion.")
                }

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
                Log.e("GeminiViewModel", "[JournalPrompt] HTTP Error: ${e.code()} - ${e.message()}. Body: $errorBody", e)
                _journalPromptSuggestion.postValue("Error fetching prompt: HTTP ${e.code()}.")

            } catch (e: Exception) {
                Log.e("GeminiViewModel", "[JournalPrompt] Generic Error: ${e.message}", e)
                _journalPromptSuggestion.postValue("Error fetching prompt: ${e.message}")
            }
        }
    }
}