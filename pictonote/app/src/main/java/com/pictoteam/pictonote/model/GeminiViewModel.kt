// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/model/GeminiViewModel.kt
package com.pictoteam.pictonote.model

// Keep existing imports
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
    // --- Existing Configuration & State ---
    private val geminiApiKey = BuildConfig.GEMINI_API_KEY
    private val modelName = "gemini-1.5-flash"

    private val _journalPromptSuggestion = MutableLiveData<String>("Click 'Prompt' for a suggestion.")
    val journalPromptSuggestion: LiveData<String> = _journalPromptSuggestion

    private val _isPromptLoading = MutableLiveData<Boolean>(false)
    val isPromptLoading: LiveData<Boolean> = _isPromptLoading

    private val _journalReflection = MutableLiveData<String>("") // Initialize as empty
    val journalReflection: LiveData<String> = _journalReflection

    private val _isReflectionLoading = MutableLiveData<Boolean>(false)
    val isReflectionLoading: LiveData<Boolean> = _isReflectionLoading

    private val _weeklySummary = MutableStateFlow<String?>("Generate summary...")
    val weeklySummary: StateFlow<String?> = _weeklySummary.asStateFlow()

    private val _isWeeklySummaryLoading = MutableStateFlow<Boolean>(false)
    val isWeeklySummaryLoading: StateFlow<Boolean> = _isWeeklySummaryLoading.asStateFlow()

    init {
        Log.d("GeminiViewModel", "ViewModel initialized.")
    }

    // --- Private Helper ---
    private suspend fun generateContentFromPrompt(promptText: String, callerTag: String): String? {
        // ... (no changes needed in this function)
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
                generatedText.trim()
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

    // --- Public Functions ---
    fun suggestJournalPrompt(promptType: String) {
        // ... (no changes needed in this function)
        if (_isPromptLoading.value == true) return

        _isPromptLoading.value = true
        _journalPromptSuggestion.postValue("Generating $promptType prompt...")
        val callerTag = "[JournalPrompt-$promptType]"

        viewModelScope.launch {
            val promptForGemini = when (promptType) {
                "Reflective" -> "Suggest a journal prompt focused on self-reflection about recent experiences, emotions, or lessons learned. Make it thoughtful."
                "Creative" -> "Suggest an imaginative or creative writing prompt suitable for a journal entry. It could be a 'what if' scenario, a descriptive task, or a story starter."
                "Goal-Oriented" -> "Suggest a journal prompt focused on setting, reviewing, or reflecting on personal goals, progress, or challenges."
                "Gratitude" -> "Suggest a journal prompt focused on practicing gratitude or appreciating positive aspects of life."
                else -> "Suggest a thoughtful and inspiring general-purpose journal prompt suitable for self-reflection."
            }

            Log.d("GeminiViewModel", "$callerTag Using Gemini prompt: $promptForGemini")
            val generatedPrompt = generateContentFromPrompt(promptForGemini, callerTag)

            val messageToPost = generatedPrompt ?: "Error fetching prompt suggestion."
            _journalPromptSuggestion.postValue(messageToPost)
            _isPromptLoading.postValue(false)
        }
    }

    fun reflectOnJournalEntry(journalEntry: String) {
        if (journalEntry.isBlank()) {
            _journalReflection.postValue("The entry is empty, nothing to reflect on.") // Specific message
            return
        }
        if (_isReflectionLoading.value == true) return

        _isReflectionLoading.value = true
        _journalReflection.postValue("Generating reflection...") // Loading message
        val callerTag = "[JournalReflection]"

        viewModelScope.launch {
            val promptForGemini = """
            Please reflect on the following journal entry. Provide some thoughtful insights, questions to consider, or a brief summary of the potential themes or emotions expressed. Keep the reflection concise (2-4 sentences).

            Journal Entry:
            ---
            $journalEntry
            ---

            Reflection:
            """.trimIndent()

            val generatedReflection = generateContentFromPrompt(promptForGemini, callerTag)
            val messageToPost = generatedReflection ?: "Error generating reflection for this entry."
            _journalReflection.postValue(messageToPost) // Post result or error
            _isReflectionLoading.postValue(false)
        }
    }

    // --- NEW: Function to clear reflection state ---
    fun clearReflectionState() {
        if (_journalReflection.value?.isNotEmpty() == true || _isReflectionLoading.value == true) {
            Log.d("GeminiViewModel", "Clearing reflection state.")
            _journalReflection.postValue("") // Use postValue for thread safety if called from different contexts
            _isReflectionLoading.postValue(false) // Ensure loading is also reset
        }
    }
    // --- End New Function ---

    fun generateWeeklySummary(allEntriesText: String) {
        // ... (no changes needed in this function)
        if (_isWeeklySummaryLoading.value) return
        if (allEntriesText.isBlank()) {
            _weeklySummary.value = "No entries found in the last 7 days to summarize."
            return
        }

        _isWeeklySummaryLoading.value = true
        _weeklySummary.value = "Generating summary..."
        val callerTag = "[WeeklySummary]"

        viewModelScope.launch {
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
            _weeklySummary.value = generatedSummary ?: "Could not generate summary. Please try again later."
            _isWeeklySummaryLoading.value = false
        }
    }

    // Keep if used elsewhere
    private val _apiResult = MutableLiveData<String>("")
    val apiResult: LiveData<String> = _apiResult
}