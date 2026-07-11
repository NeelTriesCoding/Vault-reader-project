package com.example.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Book
import com.example.data.Link
import com.example.data.Note
import com.example.data.Highlight
import com.example.ui.map.RadialMapScreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultApp(viewModel: VaultViewModel) {
    var currentTab by remember { mutableStateOf("library") }
    val context = LocalContext.current

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
                    "map" -> RadialMapScreen(viewModel = viewModel)
                    "settings" -> SettingsTabScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabScreen(
    viewModel: VaultViewModel,
    books: List<Book>,
    shelves: Set<String>
) {
    val context = LocalContext.current
    var selectedShelfFilter by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showEditShelvesDialog by remember { mutableStateOf<Book?>(null) }
    var showBatchTagDialog by remember { mutableStateOf<Book?>(null) }

    // File selection launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = getFileName(context, uri) ?: "imported_book"
            // Show shelves multi-choice
            viewModel.importBook(uri, name, emptyList())
            Toast.makeText(context, "Importing '$name'...", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.testTag("import_book_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Book")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Library Shelves",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Shelves Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedShelfFilter == null,
                        onClick = { selectedShelfFilter = null },
                        label = { Text("All Books") }
                    )
                }
                items(shelves.toList()) { shelf ->
                    FilterChip(
                        selected = selectedShelfFilter == shelf,
                        onClick = { selectedShelfFilter = shelf },
                        label = { Text(shelf) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val filteredBooks = if (selectedShelfFilter != null) {
                books.filter { it.shelves.contains(selectedShelfFilter) }
            } else {
                books
            }

            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedShelfFilter != null) "No books in shelf '$selectedShelfFilter'" else "Your library is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import PDF, EPUB, TXT, or MOBI to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredBooks) { book ->
                        Card(
                            onClick = { viewModel.selectBook(book) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("book_card_${book.title.replace(" ", "_").lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // Cover Placeholder Gradient
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Book,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = book.title.take(3).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Shelves display
                                if (book.shelves.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        book.shelves.take(3).forEach { shelf ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = shelf,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { showEditShelvesDialog = book },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Label,
                                            contentDescription = "Edit Shelves",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showBatchTagDialog = book },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Style,
                                            contentDescription = "Batch Tag Notes",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteBook(book) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Book",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Edit Shelves Dialog
    showEditShelvesDialog?.let { book ->
        var newShelfName by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showEditShelvesDialog = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Edit Shelves - ${book.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Current Shelves:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (book.shelves.isEmpty()) {
                        Text("No custom shelves.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            book.shelves.forEach { shelf ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.removeShelfFromBook(book.id, shelf) },
                                    label = { Text(shelf) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newShelfName,
                        onValueChange = { newShelfName = it },
                        label = { Text("Add New Shelf") },
                        modifier = Modifier.fillMaxWidth().testTag("new_shelf_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showEditShelvesDialog = null }) {
                            Text("Close")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newShelfName.isNotBlank()) {
                                    viewModel.addShelfToBook(book.id, newShelfName)
                                    newShelfName = ""
                                }
                            },
                            enabled = newShelfName.isNotBlank(),
                            modifier = Modifier.testTag("add_shelf_button")
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }

    // Dialog: Batch Tag Notes Dialog
    showBatchTagDialog?.let { book ->
        var selectedAction by remember { mutableStateOf("ADD") } // ADD, REMOVE, RENAME
        var targetTag by remember { mutableStateOf("") }
        var replacementTag by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showBatchTagDialog = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Batch Edit Note Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Applies to all notes from: ${book.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Action:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("ADD", "REMOVE", "RENAME").forEach { action ->
                            FilterChip(
                                selected = selectedAction == action,
                                onClick = { selectedAction = action },
                                label = { Text(action) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = targetTag,
                        onValueChange = { targetTag = it },
                        label = { Text(if (selectedAction == "RENAME") "Target Tag to Rename" else "Tag Name") },
                        modifier = Modifier.fillMaxWidth().testTag("batch_tag_target_input")
                    )

                    if (selectedAction == "RENAME") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = replacementTag,
                            onValueChange = { replacementTag = it },
                            label = { Text("New Tag Name") },
                            modifier = Modifier.fillMaxWidth().testTag("batch_tag_replacement_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showBatchTagDialog = null }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (targetTag.isNotBlank()) {
                                    viewModel.batchEditBookTags(
                                        bookTitle = book.title,
                                        action = selectedAction,
                                        targetTag = targetTag,
                                        replacementTag = replacementTag
                                    )
                                    showBatchTagDialog = null
                                }
                            },
                            enabled = targetTag.isNotBlank() && (selectedAction != "RENAME" || replacementTag.isNotBlank()),
                            modifier = Modifier.testTag("batch_edit_tags_confirm_button")
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesTabScreen(
    viewModel: VaultViewModel,
    notes: List<Note>
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf("folder") } // "folder" or "list"
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    var expandedSubfolders by remember { mutableStateOf(setOf<String>()) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var viewingNoteDetail by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes else {
            notes.filter { note ->
                note.title.contains(searchQuery, ignoreCase = true) ||
                        note.content.contains(searchQuery, ignoreCase = true) ||
                        note.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showNoteEditor = true
                },
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Personal Vault",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )

                // View Mode Toggle Button Row
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    IconButton(
                        onClick = { viewMode = "folder" },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == "folder") MaterialTheme.colorScheme.surface else Color.Transparent)
                            .testTag("view_mode_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Folder View",
                            tint = if (viewMode == "folder") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = { viewMode = "list" },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == "list") MaterialTheme.colorScheme.surface else Color.Transparent)
                            .testTag("view_mode_list_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "List View",
                            tint = if (viewMode == "list") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search title, content, or tags...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().testTag("note_search_bar"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching notes found" else "Your vault is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (viewMode == "folder") {
                    val notesByBook = remember(filteredNotes, books) {
                        val groups = mutableMapOf<Book?, MutableList<Note>>()
                        for (note in filteredNotes) {
                            val matchedBook = books.find { b ->
                                note.source?.contains(b.title, ignoreCase = true) == true
                            }
                            groups.getOrPut(matchedBook) { mutableListOf() }.add(note)
                        }
                        groups
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        notesByBook.forEach { (book, bookNotes) ->
                            val folderKey = book?.title ?: "other_unsorted"
                            val isFolderExpanded = expandedFolders.contains(folderKey)

                            item {
                                Card(
                                    onClick = {
                                        expandedFolders = if (isFolderExpanded) {
                                            expandedFolders - folderKey
                                        } else {
                                            expandedFolders + folderKey
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("folder_card_" + folderKey.replace(" ", "_").lowercase()),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = "Folder",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = book?.title ?: "Unsorted Notes",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${bookNotes.size} notes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isFolderExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            if (isFolderExpanded) {
                                if (book != null) {
                                    val overviewKey = folderKey + "_overview"
                                    val aiKey = folderKey + "_ai"
                                    val userKey = folderKey + "_user"

                                    val isOverviewExpanded = expandedSubfolders.contains(overviewKey)
                                    val isAiExpanded = expandedSubfolders.contains(aiKey)
                                    val isUserExpanded = expandedSubfolders.contains(userKey)

                                    val overviewNotes = bookNotes.filter { it.tags.contains("overview") }
                                    val aiNotes = bookNotes.filter { (it.origin == "ai" || it.origin == "atomic") && !it.tags.contains("overview") }
                                    val userNotes = bookNotes.filter { it.origin == "user" && !it.tags.contains("overview") }

                                    // 1. Overview Doc Subfolder
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    expandedSubfolders = if (isOverviewExpanded) {
                                                        expandedSubfolders - overviewKey
                                                    } else {
                                                        expandedSubfolders + overviewKey
                                                    }
                                                }
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = "Overview Doc",
                                                tint = Color(0xFF1976D2),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Overview Docs (" + overviewNotes.size + ")",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (isOverviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Toggle",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    if (isOverviewExpanded) {
                                        if (overviewNotes.isEmpty()) {
                                            item {
                                                Text(
                                                    text = "No overview docs found.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                                )
                                            }
                                        } else {
                                            items(overviewNotes) { note ->
                                                CompactNoteTreeItem(
                                                    note = note,
                                                    onNoteClick = { viewingNoteDetail = note },
                                                    onEditClick = { editingNote = note; showNoteEditor = true },
                                                    onDeleteClick = { viewModel.deleteNote(note) }
                                                )
                                            }
                                        }
                                    }

                                    // 2. AI Notes Subfolder
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    expandedSubfolders = if (isAiExpanded) {
                                                        expandedSubfolders - aiKey
                                                    } else {
                                                        expandedSubfolders + aiKey
                                                    }
                                                }
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "AI Notes",
                                                tint = Color(0xFF7B1FA2),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "AI Notes (" + aiNotes.size + ")",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (isAiExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Toggle",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    if (isAiExpanded) {
                                        if (aiNotes.isEmpty()) {
                                            item {
                                                Text(
                                                    text = "No AI notes found.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                                )
                                            }
                                        } else {
                                            items(aiNotes) { note ->
                                                CompactNoteTreeItem(
                                                    note = note,
                                                    onNoteClick = { viewingNoteDetail = note },
                                                    onEditClick = { editingNote = note; showNoteEditor = true },
                                                    onDeleteClick = { viewModel.deleteNote(note) }
                                                )
                                            }
                                        }
                                    }

                                    // 3. User Notes Subfolder
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    expandedSubfolders = if (isUserExpanded) {
                                                        expandedSubfolders - userKey
                                                    } else {
                                                        expandedSubfolders + userKey
                                                    }
                                                }
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "User Notes",
                                                tint = Color(0xFF00796B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "User Notes (" + userNotes.size + ")",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (isUserExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Toggle",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    if (isUserExpanded) {
                                        if (userNotes.isEmpty()) {
                                            item {
                                                Text(
                                                    text = "No user notes found.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                                )
                                            }
                                        } else {
                                            items(userNotes) { note ->
                                                CompactNoteTreeItem(
                                                    note = note,
                                                    onNoteClick = { viewingNoteDetail = note },
                                                    onEditClick = { editingNote = note; showNoteEditor = true },
                                                    onDeleteClick = { viewModel.deleteNote(note) }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(bookNotes) { note ->
                                        CompactNoteTreeItem(
                                            note = note,
                                            onNoteClick = { viewingNoteDetail = note },
                                            onEditClick = { editingNote = note; showNoteEditor = true },
                                            onDeleteClick = { viewModel.deleteNote(note) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredNotes) { note ->
                            Card(
                                onClick = { viewingNoteDetail = note },
                                modifier = Modifier.fillMaxWidth().testTag("note_item_card_" + note.id),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val isAi = note.origin == "ai"
                                        val isAtomic = note.origin == "atomic"
                                        val badgeBg = when {
                                            isAtomic -> Color(0xFFF3E5F5)
                                            isAi -> Color(0xFFFFECC8)
                                            else -> Color(0xFFE8F1FF)
                                        }
                                        val badgeText = when {
                                            isAtomic -> "Atomic Concept"
                                            isAi -> "AI Enrichment"
                                            else -> "User Note"
                                        }
                                        val badgeTextColor = when {
                                            isAtomic -> Color(0xFF7B1FA2)
                                            isAi -> Color(0xFFC46A00)
                                            else -> Color(0xFF004BA0)
                                        }
                                        Surface(
                                            color = badgeBg,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                color = badgeTextColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row {
                                            IconButton(onClick = {
                                                editingNote = note
                                                showNoteEditor = true
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit note", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { viewModel.deleteNote(note) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete note", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Serif
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = note.content,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    if (note.source != null) {
                                        val matchedBook = books.find { book ->
                                            note.source.contains(book.title, ignoreCase = true)
                                        }
                                        val pageRegex = """Page\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
                                        val matchResult = pageRegex.find(note.source)
                                        val targetPage = matchResult?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: 0

                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (matchedBook != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        viewModel.selectBook(matchedBook)
                                                        viewModel.goToPage(targetPage)
                                                    }
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FolderOpen,
                                                    contentDescription = "Go to file",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = note.source + " (" + matchedBook.filePath + ")",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    textDecoration = TextDecoration.Underline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = note.source,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }

                                    if (note.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            note.tags.take(3).forEach { tag ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        text = "#" + tag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            if (note.tags.size > 3) {
                                                Text(
                                                    text = "+" + (note.tags.size - 3) + " more",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.align(Alignment.CenterVertically)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs: Create/Edit Note and Note Detail
    if (showNoteEditor) {
        var editTitle by remember { mutableStateOf(editingNote?.title ?: "") }
        var editContent by remember { mutableStateOf(editingNote?.content ?: "") }
        var editSource by remember { mutableStateOf(editingNote?.source ?: "") }
        var editTagsText by remember { mutableStateOf(editingNote?.tags?.joinToString(", ") ?: "") }
        val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
        val aiError by viewModel.aiError.collectAsStateWithLifecycle()
        val showAiConfirm by viewModel.showAiConfirmation.collectAsStateWithLifecycle()

        Dialog(onDismissRequest = { if (!isAiLoading) showNoteEditor = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (editingNote == null) "Create New Note" else "Edit Note",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title (Optional - AI will generate if empty)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_note_title")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("Note Content") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("edit_note_content")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editSource,
                        onValueChange = { editSource = it },
                        label = { Text("Source (e.g., Book title, page - Optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_note_source")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editTagsText,
                        onValueChange = { editTagsText = it },
                        label = { Text("Tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_note_tags")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isAiLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini AI analysis & summarization running...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    aiError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { viewModel.clearAiError() }) {
                            Text("Dismiss Error", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showNoteEditor = false }, enabled = !isAiLoading) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editContent.isNotBlank()) {
                                    val parsedTags = editTagsText.split(",")
                                        .map { it.trim().lowercase().replace(" ", "_") }
                                        .filter { it.isNotEmpty() }
                                    viewModel.saveNoteAndEnrich(
                                        id = editingNote?.id ?: 0,
                                        title = editTitle,
                                        content = editContent,
                                        source = editSource.ifBlank { null },
                                        userTags = parsedTags,
                                        triggerAi = true
                                    )
                                    showNoteEditor = false
                                }
                            },
                            enabled = !isAiLoading && editContent.isNotBlank(),
                            modifier = Modifier.testTag("save_note_button")
                        ) {
                            Text("Save & AI Enrich")
                        }
                    }
                }
            }
        }
    }

    viewingNoteDetail?.let { note ->
        Dialog(onDismissRequest = { viewingNoteDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = note.title.ifBlank { "Untitled Note" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewingNoteDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    if (note.source != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Book, "Source", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = note.source, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                        }

                        val matchedBook = books.find { book ->
                            note.source.contains(book.title, ignoreCase = true)
                        }
                        if (matchedBook != null) {
                            val pageRegex = """Page\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
                            val matchResult = pageRegex.find(note.source)
                            val targetPage = matchResult?.groupValues?.get(1)?.toIntOrNull()?.minus(1) ?: 0

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.selectBook(matchedBook)
                                        viewModel.goToPage(targetPage)
                                        viewingNoteDetail = null
                                    }
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Go to file path",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "File: ${matchedBook.filePath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Content",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = note.content, style = MaterialTheme.typography.bodyLarge)

                    note.summary?.let { sum ->
                        if (sum.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                              ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, "AI Summary", tint = Color(0xFFC46A00), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "AI Summary (~10 sentences)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFC46A00)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = sum, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    if (note.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tags",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            note.tags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SETTINGS TAB SCREEN
// ==========================================
@Composable
fun SettingsTabScreen(viewModel: VaultViewModel) {
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val testResult by viewModel.testConnectionResult.collectAsStateWithLifecycle()

    var inputKey by remember { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Vault Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security & Local Storage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your Gemini API key is stored directly in the private storage of this device. It is never transmitted to any third-party server besides Google's official Gemini API endpoint.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gemini API Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        label = { Text("Paste your Gemini API Key") },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (keyVisible) "Hide" else "Show"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveApiKey(inputKey) },
                            modifier = Modifier.weight(1f).testTag("save_api_key_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Locally")
                        }

                        OutlinedButton(
                            onClick = { viewModel.testConnection(inputKey) },
                            modifier = Modifier.weight(1f).testTag("test_connection_button")
                        ) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test API")
                        }
                    }

                    testResult?.let { (success, message) ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = if (success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (success) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (success) "Connection Verified!" else "Connection Failed",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (success) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (success) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                                IconButton(onClick = { viewModel.clearTestConnectionResult() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = if (success) Color(0xFF2E7D32) else Color(0xFFC62828))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPACT NOTE TREE ITEM COMPOSABLE
// ==========================================
@Composable
fun CompactNoteTreeItem(
    note: Note,
    onNoteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val icon = when (note.origin) {
        "ai" -> Icons.Default.AutoAwesome
        "atomic" -> Icons.Default.OfflineBolt
        else -> Icons.Default.Description
    }
    val iconTint = when (note.origin) {
        "ai" -> Color(0xFFFFB300)
        "atomic" -> Color(0xFF8E24AA)
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onNoteClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (note.tags.isNotEmpty()) {
                Text(
                    text = note.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Note",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Note",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ==========================================
// GET FILE NAME HELPER FUNCTION
// ==========================================
fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

// ==========================================
// EBOOK READER SCREEN COMPOSABLE
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbookReaderScreen(viewModel: VaultViewModel) {
    val book by viewModel.currentBook.collectAsStateWithLifecycle()
    val pages by viewModel.readerPages.collectAsStateWithLifecycle()
    val pageCount by viewModel.readerPageCount.collectAsStateWithLifecycle()
    val currentPageIndex by viewModel.currentPageIndex.collectAsStateWithLifecycle()
    val theme by viewModel.readingTheme.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val pdfBitmaps by viewModel.pdfBitmaps.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val activeBook = book ?: return

    val isPdf = activeBook.filePath.substringAfterLast(".").lowercase() == "pdf"

    // Dialogs / Sheet States
    var selectedSentenceForMenu by remember { mutableStateOf<String?>(null) }
    var showSentenceMenu by remember { mutableStateOf(false) }
    var showFontSizeAndThemeDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var quickNoteText by remember { mutableStateOf("") }
    var quickNoteTitle by remember { mutableStateOf("") }

    // Theme values
    val (backgroundColor, textColor) = when (theme) {
        "sepia" -> Pair(Color(0xFFFAF0E6), Color(0xFF3E2723))
        "dark" -> Pair(Color(0xFF121212), Color(0xFFE3E3E3))
        else -> Pair(Color(0xFFFFFFFF), Color(0xFF1C1B1F)) // light
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeBook.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeBook() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFontSizeAndThemeDialog = true }) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Font & Theme")
                    }
                    IconButton(onClick = {
                        quickNoteTitle = "Note on ${activeBook.title}, Page ${currentPageIndex + 1}"
                        quickNoteText = ""
                        showQuickNoteDialog = true
                    }) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Add Page Note")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.prevPage() }, enabled = currentPageIndex > 0) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Page")
                        }

                        Text(
                            text = "Page ${currentPageIndex + 1} of $pageCount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = { viewModel.nextPage() }, enabled = currentPageIndex < pageCount - 1) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Page")
                        }
                    }

                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { viewModel.goToPage(it.toInt()) },
                        valueRange = 0f..(if (pageCount > 1) (pageCount - 1).toFloat() else 1f),
                        steps = if (pageCount > 2) pageCount - 2 else 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(paddingValues)
        ) {
            if (isPdf) {
                val bitmap = pdfBitmaps[currentPageIndex]
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                // Text-based e-reader (EPUB/TXT/MOBI)
                val pageText = if (currentPageIndex in pages.indices) pages[currentPageIndex] else ""
                
                val pageHighlights = remember(highlights, currentPageIndex) {
                    highlights.filter { it.bookId == activeBook.id && it.pageIndex == currentPageIndex }
                }

                val processedSentences = remember(pageText) {
                    val list = mutableListOf<String>()
                    val regex = Regex("(?<=[.!?])\\s+")
                    pageText.split(regex).forEach {
                        if (it.isNotBlank()) list.add(it.trim())
                    }
                    if (list.isEmpty() && pageText.isNotBlank()) {
                        list.add(pageText.trim())
                    }
                    list
                }

                if (processedSentences.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No text on this page.", color = textColor, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        processedSentences.forEach { sentence ->
                            val activeHighlight = pageHighlights.find {
                                it.sentenceText.trim().equals(sentence.trim(), ignoreCase = true)
                            }
                            
                            val highlightBgColor = when (activeHighlight?.colorHex?.lowercase()) {
                                "yellow" -> Color(0xFFFFF59D)
                                "green" -> Color(0xFFA5D6A7)
                                "pink" -> Color(0xFFF48FB1)
                                "blue" -> Color(0xFF90CAF9)
                                else -> Color.Transparent
                            }
                            
                            val textSelectionColor = if (highlightBgColor != Color.Transparent) Color.Black else textColor

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(highlightBgColor, RoundedCornerShape(4.dp))
                                    .clickable {
                                        selectedSentenceForMenu = sentence
                                        showSentenceMenu = true
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = sentence,
                                    fontSize = fontSize.sp,
                                    fontFamily = FontFamily.Serif,
                                    color = textSelectionColor,
                                    lineHeight = (fontSize * 1.4f).sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Sentence Highlight / Note Action
    if (showSentenceMenu && selectedSentenceForMenu != null) {
        val sentence = selectedSentenceForMenu!!
        val activeHighlight = highlights.find {
            it.bookId == activeBook.id && it.pageIndex == currentPageIndex &&
                    it.sentenceText.trim().equals(sentence.trim(), ignoreCase = true)
        }

        Dialog(onDismissRequest = { showSentenceMenu = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sentence Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\"$sentence\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Highlight Color:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            Pair("Yellow", Color(0xFFFFF59D)),
                            Pair("Green", Color(0xFFA5D6A7)),
                            Pair("Pink", Color(0xFFF48FB1)),
                            Pair("Blue", Color(0xFF90CAF9))
                        ).forEach { (name, color) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (activeHighlight?.colorHex?.equals(name, ignoreCase = true) == true) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.addHighlight(activeBook.id, currentPageIndex, sentence, name)
                                        showSentenceMenu = false
                                    }
                            )
                        }
                    }

                    if (activeHighlight != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                viewModel.removeHighlight(activeBook.id, currentPageIndex, sentence)
                                showSentenceMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Remove Highlight", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            quickNoteTitle = "Quote from ${activeBook.title}"
                            quickNoteText = "\"$sentence\""
                            showSentenceMenu = false
                            showQuickNoteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NoteAdd, "Create note")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Note from Quote")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showSentenceMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // Dialog: Font Size & Theme Settings
    if (showFontSizeAndThemeDialog) {
        Dialog(onDismissRequest = { showFontSizeAndThemeDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Reader Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Background Theme:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("light", "sepia", "dark").forEach { themeName ->
                            val btnBgColor = when (themeName) {
                                "sepia" -> Color(0xFFFAF0E6)
                                "dark" -> Color(0xFF121212)
                                else -> Color(0xFFFFFFFF)
                            }
                            val btnTextColor = when (themeName) {
                                "dark" -> Color(0xFFE3E3E3)
                                "sepia" -> Color(0xFF3E2723)
                                else -> Color(0xFF1C1B1F)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(btnBgColor)
                                    .border(
                                        width = if (theme == themeName) 2.dp else 1.dp,
                                        color = if (theme == themeName) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setReadingTheme(themeName) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = themeName.replaceFirstChar { it.uppercase() },
                                    color = btnTextColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Font Size: ${fontSize.toInt()} sp", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { viewModel.setFontSize((fontSize - 2f).coerceAtLeast(12f)) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size")
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = { viewModel.setFontSize(it) },
                            valueRange = 12f..32f,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.setFontSize((fontSize + 2f).coerceAtMost(32f)) }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase Font Size")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showFontSizeAndThemeDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    // Dialog: Create Note
    if (showQuickNoteDialog) {
        Dialog(onDismissRequest = { showQuickNoteDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Create Note",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = quickNoteTitle,
                        onValueChange = { quickNoteTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = quickNoteText,
                        onValueChange = { quickNoteText = it },
                        label = { Text("Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showQuickNoteDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (quickNoteText.isNotBlank()) {
                                    viewModel.saveNoteAndEnrich(
                                        title = quickNoteTitle,
                                        content = quickNoteText,
                                        source = "${activeBook.title}, Page ${currentPageIndex + 1}",
                                        triggerAi = true
                                    )
                                    showQuickNoteDialog = false
                                }
                            },
                            enabled = quickNoteText.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
