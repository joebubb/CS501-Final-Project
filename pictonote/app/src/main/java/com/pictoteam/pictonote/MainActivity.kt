package com.pictoteam.pictonote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private val geminiKey = BuildConfig.GEMINI_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PictoNote()
        }
    }
}

@Composable
fun PictoNote() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavigationGraph(navController)
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.AccountBox, contentDescription = "Archive") },
            label = { Text("Archive") },
            selected = false,
            onClick = { navController.navigate("archive") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = false,
            onClick = { navController.navigate("home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Journal") },
            label = { Text("Journal") },
            selected = false,
            onClick = { navController.navigate("journal") }
        )
    }
}

@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("archive") { ArchiveScreen() }
        composable("journal") { JournalScreen() }
    }
}

@Composable
fun HomeScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Home Page", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun ArchiveScreen() {
    var selectedMonth by remember { mutableStateOf(1) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Calendar - 2025", style = MaterialTheme.typography.headlineMedium)
        LazyRow(modifier = Modifier.fillMaxWidth()) {
            items(12) { month ->
                Button(onClick = { selectedMonth = month + 1 }) {
                    Text("Month ${month + 1}")
                }
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.padding(8.dp)) {
            val daysInMonth = YearMonth.of(2025, selectedMonth).lengthOfMonth()
            items(daysInMonth) { day ->
                Card(
                    modifier = Modifier.padding(8.dp)
                ) {


                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .aspectRatio(1f)
                    ) {
                        Text("$selectedMonth/$day", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun JournalScreen() {
    var text by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(8.dp)) {
                    innerTextField()
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { /* Prompt action */ }) {
                Text("Prompt")
            }
            Button(onClick = { /* Reflection action */ }) {
                Text("Reflection")
            }
        }
    }
}