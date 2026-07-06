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
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.data.parsing.SentenceSplitter
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

    // Keep the screen awake while reading; clear the flag when leaving the reader.
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Swipe paging, two-way synced with the ViewModel's page index.
    val pagerState = rememberPagerState(initialPage = currentPageIndex) { pageCount }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentPageIndex) viewModel.goToPage(pagerState.currentPage)
    }
    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex != pagerState.currentPage &&
            currentPageIndex in 0 until pageCount
        ) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    // Dialogs / Sheet States
    var selectedSentenceForMenu by remember { mutableStateOf<String?>(null) }
    var selectedSentenceIndex by remember { mutableStateOf(-1) }
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIdx ->
                if (isPdf) {
                    val bitmap = pdfBitmaps[pageIdx]
                    if (bitmap != null) {
                        // Double-tap to zoom; pinch/pan once zoomed. transformable is
                        // disabled at 1x so the pager keeps handling horizontal swipes.
                        var pdfScale by remember(pageIdx) { mutableStateOf(1f) }
                        var pdfOffset by remember(pageIdx) { mutableStateOf(Offset.Zero) }
                        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                            pdfScale = (pdfScale * zoomChange).coerceIn(1f, 5f)
                            pdfOffset = if (pdfScale > 1f) pdfOffset + panChange else Offset.Zero
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .transformable(transformState, enabled = pdfScale > 1f)
                                .pointerInput(pageIdx) {
                                    detectTapGestures(onDoubleTap = {
                                        if (pdfScale > 1f) {
                                            pdfScale = 1f; pdfOffset = Offset.Zero
                                        } else {
                                            pdfScale = 2.5f
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "PDF Page ${pageIdx + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = pdfScale
                                        scaleY = pdfScale
                                        translationX = pdfOffset.x
                                        translationY = pdfOffset.y
                                    }
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    // Text-based e-reader (EPUB/TXT/MOBI)
                    val pageText = if (pageIdx in pages.indices) pages[pageIdx] else ""

                    val pageHighlights = remember(highlights, pageIdx) {
                        highlights.filter { it.bookId == activeBook.id && it.pageIndex == pageIdx }
                    }

                    val processedSentences = remember(pageText) { SentenceSplitter.split(pageText) }

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
                            processedSentences.forEachIndexed { sIndex, sentence ->
                                // Prefer the robust per-index match; fall back to text
                                // for legacy highlights saved before sentenceIndex existed.
                                val activeHighlight = pageHighlights.find { it.sentenceIndex == sIndex }
                                    ?: pageHighlights.find {
                                        it.sentenceIndex == -1 &&
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
                                            selectedSentenceIndex = sIndex
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
    }

    // Modal Dialog: Sentence Highlight / Note Action
    if (showSentenceMenu && selectedSentenceForMenu != null) {
        val sentence = selectedSentenceForMenu!!
        val sentenceIndex = selectedSentenceIndex
        val activeHighlight = highlights.find {
            it.bookId == activeBook.id && it.pageIndex == currentPageIndex &&
                    (it.sentenceIndex == sentenceIndex ||
                        (it.sentenceIndex == -1 && it.sentenceText.trim().equals(sentence.trim(), ignoreCase = true)))
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
                                        viewModel.addHighlight(activeBook.id, currentPageIndex, sentenceIndex, sentence, name)
                                        showSentenceMenu = false
                                    }
                            )
                        }
                    }

                    if (activeHighlight != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                val idx = if (activeHighlight.sentenceIndex != -1) activeHighlight.sentenceIndex else sentenceIndex
                                viewModel.removeHighlight(activeBook.id, currentPageIndex, idx)
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
