package com.pictoteam.pictonote.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.pictoteam.pictonote.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class GeminiViewModel : ViewModel() {
    // api configuration
    private val geminiApiKey = BuildConfig.GEMINI_API_KEY
    private val modelName = "gemini-1.5-flash" // best blend of intelligence and conciseness

    // Journal Prompt
    private val _journalPromptSuggestion = MutableLiveData<String>("Click 'Prompt' for a suggestion.") // Initial non-null value
    val journalPromptSuggestion: LiveData<String> = _journalPromptSuggestion

    private val _isPromptLoading = MutableLiveData<Boolean>(false)
    val isPromptLoading: LiveData<Boolean> = _isPromptLoading

    // Journal Reflection
    private val _journalReflection = MutableLiveData<String>("") // Initial non-null value
    val journalReflection: LiveData<String> = _journalReflection

    private val _isReflectionLoading = MutableLiveData<Boolean>(false)
    val isReflectionLoading: LiveData<Boolean> = _isReflectionLoading

    // weekly summary
    private val _weeklySummary = MutableStateFlow<String?>("Generate summary...") // Can be null if error occurs
    val weeklySummary: StateFlow<String?> = _weeklySummary.asStateFlow()

    private val _isWeeklySummaryLoading = MutableStateFlow<Boolean>(false)
    val isWeeklySummaryLoading: StateFlow<Boolean> = _isWeeklySummaryLoading.asStateFlow()

    init {
        Log.d("GeminiViewModel", "ViewModel initialized.")
    }

    private suspend fun generateContentFromPrompt(promptText: String, callerTag: String): String? {
        Log.d("GeminiViewModel", "$callerTag Coroutine launched. Preparing API call...")
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = promptText))))
        )
        Log.d("GeminiViewModel", "$callerTag Request Body Created: $request")

        return try {
            Log.d("GeminiViewModel", "$callerTag Calling apiService.generateContent...")
            val response = GeminiApiClient.apiService.generateContent(modelName, geminiApiKey, request)
            Log.d("GeminiViewModel", "$callerTag API call successful.")

            val generatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!generatedText.isNullOrBlank()) {
                Log.i("GeminiViewModel", "$callerTag Generated Content: $generatedText")
                generatedText.trim() // Return valid text
            } else {
                Log.w("GeminiViewModel", "$callerTag Received response, but generated text was null or empty.")
                Log.d("GeminiViewModel", "$callerTag Full Response: $response")
                null
            }

        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Log.e("GeminiViewModel", "$callerTag HTTP Error: ${e.code()} - ${e.message()}. Body: $errorBody", e)
            null
        } catch (e: Exception) {
            Log.e("GeminiViewModel", "$callerTag Generic Error: ${e.message}", e)
            null
        }
    }

    fun suggestJournalPrompt() {
        if (_isPromptLoading.value == true) return

        _isPromptLoading.value = true
        // Post the loading message immediately, ensuring it's non-null
        _journalPromptSuggestion.postValue("Generating prompt suggestion...")
        val callerTag = "[JournalPrompt]"

        viewModelScope.launch {
            val promptForGemini = "Suggest a thoughtful and inspiring journal prompt suitable for self-reflection."
            val generatedPrompt = generateContentFromPrompt(promptForGemini, callerTag)

            // Use the result if not null, otherwise post a specific error message
            val messageToPost = generatedPrompt ?: "Error fetching prompt suggestion."
            _journalPromptSuggestion.postValue(messageToPost)

            _isPromptLoading.postValue(false)
        }
    }

    fun reflectOnJournalEntry(journalEntry: String) {
        if (journalEntry.isBlank()) {
            // Ensure non-null post
            _journalReflection.postValue("Please write something before asking for a reflection.")
            return
        }
        if (_isReflectionLoading.value == true) return

        _isReflectionLoading.value = true
        // Post the loading message immediately, ensuring it's non-null
        _journalReflection.postValue("Generating reflection...")
        val callerTag = "[JournalReflection]"

        viewModelScope.launch {
            val promptForGemini = """
            Please reflect on the following journal entry. Provide some thoughtful insights, questions to consider, or a brief summary of the potential themes or emotions expressed. Keep the reflection concise.

            Journal Entry:
            ---
            $journalEntry
            ---

            Reflection:
            """.trimIndent()

            val generatedReflection = generateContentFromPrompt(promptForGemini, callerTag)

            // Use the result if not null, otherwise post a specific error message. Both paths post non-null.
            val messageToPost = generatedReflection ?: "Error generating reflection for this entry."
            _journalReflection.postValue(messageToPost)

            _isReflectionLoading.postValue(false)
        }
    }

    fun generateWeeklySummary(allEntriesText: String) {
        if (_isWeeklySummaryLoading.value) return // Don't start if already loading
        if (allEntriesText.isBlank()) {
            _weeklySummary.value = "No entries found in the last 7 days to summarize."
            return
        }

        _isWeeklySummaryLoading.value = true
        _weeklySummary.value = "Generating summary..." // Indicate loading
        val callerTag = "[WeeklySummary]"

        viewModelScope.launch {
            // Prompt Engineering: Instruct Gemini clearly
            val promptForGemini = """
            You are an insightful assistant. Below is a collection of journal entries from the past 7 days.
            Please read through them and provide a concise summary (around 3-5 sentences) highlighting the main themes, activities, or emotions present.
            Focus on providing a reflective overview rather than just listing events. If there are conflicting emotions or themes, briefly mention that complexity. 
            If there is not enough to make a meaningful summary, just summarize what you can in 1-2 sentences and say that there was not much journaling this week. 
            
            Journal Entries Text:
            ---
            $allEntriesText
            ---

            Summary:
            """.trimIndent()

            val generatedSummary = generateContentFromPrompt(promptForGemini, callerTag)

            // Update StateFlow on the main thread (implicitly handled by StateFlow)
            _weeklySummary.value = generatedSummary ?: "Could not generate summary. Please try again later." // Provide user-friendly error message
            _isWeeklySummaryLoading.value = false
        }
    }
    private val _apiResult = MutableLiveData<String>("")
    val apiResult: LiveData<String> = _apiResult
}