package com.example.ui.screens

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
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.VaultViewModel

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
