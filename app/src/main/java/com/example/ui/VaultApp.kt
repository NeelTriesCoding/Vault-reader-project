package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.EbookReaderScreen
import com.example.ui.screens.LibraryTabScreen
import com.example.ui.screens.NotesTabScreen
import com.example.ui.screens.SettingsTabScreen
import com.example.ui.screens.VisualMapTabScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultApp(viewModel: VaultViewModel) {
    var currentTab by remember { mutableStateOf("library") }

    // Observe core state
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val links by viewModel.links.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val currentBook by viewModel.currentBook.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (currentBook == null) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = currentTab == "library",
                        onClick = { currentTab = "library" },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "notes",
                        onClick = { currentTab = "notes" },
                        icon = { Icon(Icons.Default.EditNote, contentDescription = "Notes") },
                        label = { Text("Notes") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "map",
                        onClick = { currentTab = "map" },
                        icon = { Icon(Icons.Default.Hub, contentDescription = "Map") },
                        label = { Text("Visual Map") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "settings",
                        onClick = { currentTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (currentBook == null) innerPadding else PaddingValues(0.dp))
        ) {
            if (currentBook != null) {
                EbookReaderScreen(viewModel = viewModel)
            } else {
                when (currentTab) {
                    "library" -> LibraryTabScreen(viewModel, books, shelves)
                    "notes" -> NotesTabScreen(viewModel, notes)
                    "map" -> VisualMapTabScreen(viewModel, notes, links, books)
                    "settings" -> SettingsTabScreen(viewModel)
                }
            }
        }
    }
}
