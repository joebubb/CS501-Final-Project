package com.pictoteam.pictonote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
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
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Home Page", style = MaterialTheme.typography.headlineMedium)

        Box(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Streak", style = MaterialTheme.typography.headlineSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                        daysOfWeek.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Reminders", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { /* Setup push notifications, need firebase to do that so doing it
                     with the database sprint*/ }) {
                        Text("Set up Push Notifications")
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveScreen() {
    var selectedMonth by remember { mutableStateOf(1) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Calendar - 2025", style = MaterialTheme.typography.headlineMedium)
        LazyRow(modifier = Modifier.fillMaxWidth(), userScrollEnabled = true) {
            items(12) { monthIndex ->
                val monthName = YearMonth.of(2025, monthIndex + 1)
                    .month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                Button(onClick = { selectedMonth = monthIndex + 1 }) {
                    Text(monthName)
                }
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.padding(8.dp)) {
            val daysInMonth = YearMonth.of(2025, selectedMonth).lengthOfMonth()
            items(daysInMonth) { day ->
                Card(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("${day + 1}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Memories", style = MaterialTheme.typography.headlineMedium)
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
                Box(modifier = Modifier.padding(16.dp)) {
                    innerTextField()
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { /* Here we have the API prompting function */ }) {
                Text("Prompt")
            }
            Button(onClick = { /*Here we have the API reflection function */ }) {
                Text("Reflection")
            }
        }
        Button(
            onClick = {
                // Implement the logic to save the journal entry in the database when we do that
                //in the next sprint
                saveJournalEntry(text)
                text = ""
            },
        ) {
            Text("Finish Entry")
        }
    }
}
