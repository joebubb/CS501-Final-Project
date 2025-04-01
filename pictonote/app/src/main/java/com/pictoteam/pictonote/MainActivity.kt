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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
    val apiResultState by viewModel.apiResult.observeAsState("Initializing...")
    LaunchedEffect(Unit) {
        viewModel.makeSimpleApiCall()
    }
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Gemini API Test")
            Spacer(modifier = Modifier.height(16.dp))

            Text("API Result:")
            Spacer(modifier = Modifier.height(8.dp))

            when (apiResultState) {
                "Calling API..." -> {
                    CircularProgressIndicator()
                }
                "Initializing..." -> {
                    Text("Waiting to call API...")
                }
                else -> {
                    Text(text = apiResultState)
                }
            }


            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = {
                viewModel.makeSimpleApiCall()
            }) {
                Text("Call API Again")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PictoNoteTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Gemini API Test")
            Spacer(modifier = Modifier.height(16.dp))
            Text("API Result:")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Preview Result Text")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = {  }) {
                Text("Call API Again")
            }
        }
    }
}