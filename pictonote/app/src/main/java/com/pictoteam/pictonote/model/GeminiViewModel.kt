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
    private val modelName = "gemini-1.5-flash" // best blend of intelligence and conciseness

    private val _apiResult = MutableLiveData<String>()
    val apiResult: LiveData<String> = _apiResult

    private val _journalPromptSuggestion = MutableLiveData<String>()
    val journalPromptSuggestion: LiveData<String> = _journalPromptSuggestion

    private val _isPromptLoading = MutableLiveData<Boolean>(false)
    val isPromptLoading: LiveData<Boolean> = _isPromptLoading

    private val _journalReflection = MutableLiveData<String>()
    val journalReflection: LiveData<String> = _journalReflection

    private val _isReflectionLoading = MutableLiveData<Boolean>(false)
    val isReflectionLoading: LiveData<Boolean> = _isReflectionLoading

    init {
        Log.d("GeminiViewModel", "ViewModel initialized.")
        _journalReflection.value = ""
    }

    fun suggestJournalPrompt() {
        if (_isPromptLoading.value == true) return

        _isPromptLoading.value = true
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
            } finally {
                _isPromptLoading.postValue(false)
            }
        }
    }

    fun reflectOnJournalEntry(journalEntry: String) {
        if (journalEntry.isBlank()) {
            _journalReflection.value = "Please write something before asking for a reflection."
            return
        }
        if (_isReflectionLoading.value == true) return

        _isReflectionLoading.value = true
        _journalReflection.value = "Generating reflection..."

        viewModelScope.launch {
            Log.d("GeminiViewModel", "[JournalReflection] Coroutine launched. Preparing API call...")
            // Construct a prompt that includes the user's entry and asks for reflection
            val promptForGemini = """
            Please reflect on the following journal entry. Provide some thoughtful insights, questions to consider, or a brief summary of the potential themes or emotions expressed. Keep the reflection concise.

            Journal Entry:
            ---
            $journalEntry
            ---

            Reflection:
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = promptForGemini))))
            )
            Log.d("GeminiViewModel", "[JournalReflection] Request Body Created: $request")

            try {
                Log.d("GeminiViewModel", "[JournalReflection] Calling apiService.generateContent...")
                val response = GeminiApiClient.apiService.generateContent(modelName, geminiApiKey, request)
                Log.d("GeminiViewModel", "[JournalReflection] API call successful.")

                val generatedReflection = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!generatedReflection.isNullOrBlank()) {
                    Log.i("GeminiViewModel", "[JournalReflection] Generated Reflection: $generatedReflection")
                    _journalReflection.postValue(generatedReflection.trim())
                } else {
                    Log.w("GeminiViewModel", "[JournalReflection] Received response, but reflection text was null or empty.")
                    Log.d("GeminiViewModel", "[JournalReflection] Full Response: $response")
                    _journalReflection.postValue("Could not generate a reflection for this entry.")
                }

            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
                Log.e("GeminiViewModel", "[JournalReflection] HTTP Error: ${e.code()} - ${e.message()}. Body: $errorBody", e)
                _journalReflection.postValue("Error generating reflection: HTTP ${e.code()}.")

            } catch (e: Exception) {
                Log.e("GeminiViewModel", "[JournalReflection] Generic Error: ${e.message}", e)
                _journalReflection.postValue("Error generating reflection: ${e.message}")
            } finally {
                _isReflectionLoading.postValue(false)
            }
        }
    }
}