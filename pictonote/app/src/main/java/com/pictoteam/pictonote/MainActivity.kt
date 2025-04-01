package com.pictoteam.pictonote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pictoteam.pictonote.model.GeminiViewModel
import com.pictoteam.pictonote.ui.theme.PictoNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PictoNoteTheme {
                GeminiApiScreen()
            }
        }
    }
}

@Composable
fun GeminiApiScreen(
    viewModel: GeminiViewModel = viewModel()
) {
    val journalPromptState by viewModel.journalPromptSuggestion.observeAsState("No prompt suggested yet.")

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Journal Prompt Suggestion:")
            Spacer(modifier = Modifier.height(8.dp))
            when (journalPromptState) {
                "Generating prompt suggestion..." -> {
                    CircularProgressIndicator()
                }
                "No prompt suggested yet." -> {
                    Text("Click the button below to get a suggestion.")
                }
                else -> {
                    Text(text = journalPromptState)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {
                viewModel.suggestJournalPrompt()
            }) {
                Text("Suggest Journal Prompt")
            }
        }
    }
}