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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
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
import com.example.ui.map.MapLayoutEngine

@Composable
fun VisualMapTabScreen(
    viewModel: VaultViewModel,
    notes: List<Note>,
    links: List<Link>,
    books: List<Book>
) {
    val scale by viewModel.mapScale.collectAsStateWithLifecycle()
    val offsetX by viewModel.mapOffsetX.collectAsStateWithLifecycle()
    val offsetY by viewModel.mapOffsetY.collectAsStateWithLifecycle()
    val positions by viewModel.nodePositions.collectAsStateWithLifecycle()

    val mapTagFilter by viewModel.mapTagFilter.collectAsStateWithLifecycle()
    val mapShelfFilter by viewModel.mapShelfFilter.collectAsStateWithLifecycle()

    var showAddLinkDialog by remember { mutableStateOf(false) }
    var selectedPreviewNote by remember { mutableStateOf<Note?>(null) }
    var fullNoteDetailsToView by remember { mutableStateOf<Note?>(null) }
    var selectedLink by remember { mutableStateOf<Link?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // Synchronize Node positions for the map
    LaunchedEffect(notes) {
        viewModel.updateMapPositions()
    }

    // Map filter matching (memoized so it isn't recomputed on every drag frame).
    val filteredNotes = remember(notes, books, mapTagFilter, mapShelfFilter) {
        notes.filter { note ->
            val matchesTag = mapTagFilter == null || note.tags.contains(mapTagFilter)
            val matchesShelf = mapShelfFilter == null || run {
                val matchingBooks = books.filter { it.shelves.contains(mapShelfFilter) }
                matchingBooks.any { note.source?.startsWith(it.title) == true }
            }
            matchesTag && matchesShelf
        }
    }

    // Available tags & shelves for visual map filtering (memoized).
    val allTags = remember(notes) { notes.flatMap { it.tags }.toSet().toList() }
    val allShelves = remember(books) { books.flatMap { it.shelves }.toSet().toList() }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notes Knowledge Map",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.autoArrangeMap() },
                            modifier = Modifier.testTag("map_auto_arrange_button")
                        ) {
                            Icon(Icons.Default.AutoGraph, contentDescription = "Auto-arrange")
                        }
                        IconButton(
                            onClick = {
                                fitMapToView(viewModel, viewportSize, density.density, positions, filteredNotes)
                            },
                            modifier = Modifier.testTag("map_fit_button")
                        ) {
                            Icon(Icons.Default.FitScreen, contentDescription = "Fit to screen")
                        }
                        IconButton(
                            onClick = { viewModel.resetMapView() },
                            modifier = Modifier.testTag("map_reset_button")
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset view")
                        }
                        Button(
                            onClick = { showAddLinkDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Link, "Add Link", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Link", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tag Filter Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = mapTagFilter == null,
                            onClick = { viewModel.mapTagFilter.value = null },
                            label = { Text("All Tags") }
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = mapTagFilter == tag,
                            onClick = { viewModel.mapTagFilter.value = tag },
                            label = { Text("#$tag") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Shelf Filter Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = mapShelfFilter == null,
                            onClick = { viewModel.mapShelfFilter.value = null },
                            label = { Text("All Shelves") }
                        )
                    }
                    items(allShelves) { shelf ->
                        FilterChip(
                            selected = mapShelfFilter == shelf,
                            onClick = { viewModel.mapShelfFilter.value = shelf },
                            label = { Text("Shelf: $shelf") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121216)) // Premium dark slate background
        ) {
            // Interactive Zoom / Pan Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            viewModel.mapScale.value = (scale * zoom).coerceIn(0.3f, 2.5f)
                            viewModel.mapOffsetX.value = offsetX + pan.x
                            viewModel.mapOffsetY.value = offsetY + pan.y
                        }
                    }
            ) {
                // Nodes Container (Transformed based on zoom & pan)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        // Tap on an edge (away from any node) to inspect / delete the link.
                        .pointerInput(links, filteredNotes, positions) {
                            detectTapGestures { tap ->
                                findLinkNearTap(
                                    tapPx = tap,
                                    density = density.density,
                                    links = links,
                                    filteredNotes = filteredNotes,
                                    positions = positions
                                )?.let { selectedLink = it }
                            }
                        }
                ) {
                    // 1. Connection lines (Edges). Positions are in dp, so convert to
                    //    px here to stay aligned with the dp-offset node cards.
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        links.forEach { link ->
                            val posA = positions[link.noteIdA]
                            val posB = positions[link.noteIdB]
                            val hasA = filteredNotes.any { it.id == link.noteIdA }
                            val hasB = filteredNotes.any { it.id == link.noteIdB }

                            if (posA != null && posB != null && hasA && hasB) {
                                // Center the connection lines on the 120dp x 80dp cards.
                                val start = Offset((posA.first + 60f).dp.toPx(), (posA.second + 40f).dp.toPx())
                                val end = Offset((posB.first + 60f).dp.toPx(), (posB.second + 40f).dp.toPx())
                                val highlighted = selectedLink?.id == link.id
                                drawLine(
                                    color = edgeColor(link.relationship),
                                    start = start,
                                    end = end,
                                    strokeWidth = if (highlighted) 7f else 3f
                                )
                            }
                        }
                    }

                    // 2. Nodes rendering (Compose elements)
                    filteredNotes.forEach { note ->
                        val pos = positions[note.id] ?: Pair(0f, 0f)
                        val isAi = note.origin == "ai"
                        val isAtomic = note.origin == "atomic"

                        // Dark rich container colors that stand out perfectly on the dark canvas
                        val bgColor = when {
                            isAtomic -> Color(0xFF2A1B3D) // Deep rich dark purple
                            isAi -> Color(0xFF2E2413) // Deep dark amber/bronze
                            else -> Color(0xFF142233) // Deep rich navy/steel blue
                        }

                        // Bright vibrant borders to delineate cards
                        val borderColor = when {
                            isAtomic -> Color(0xFFBB86FC) // Neon lavender
                            isAi -> Color(0xFFFFC107) // Vibrant gold/amber
                            else -> Color(0xFF2196F3) // Electric blue
                        }

                        val titleTextColor = Color.White
                        val tagTextColor = when {
                            isAtomic -> Color(0xFFE1BEE7) // Light violet
                            isAi -> Color(0xFFFFECB3) // Light amber
                            else -> Color(0xFFBBDEFB) // Light sky blue
                        }

                        Box(
                            modifier = Modifier
                                .offset(x = pos.first.dp, y = pos.second.dp)
                                .size(120.dp, 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(
                                    width = if (selectedPreviewNote?.id == note.id) 3.dp else 1.5.dp,
                                    color = if (selectedPreviewNote?.id == note.id) Color.White else borderColor,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .pointerInput(note.id) {
                                    detectDragGestures(
                                        onDragEnd = { viewModel.commitNodePosition(note.id) }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        // dragAmount is in px; positions are in dp — convert so
                                        // the node tracks the finger 1:1 at any screen density.
                                        viewModel.moveNode(
                                            note.id,
                                            dragAmount.x / density.density,
                                            dragAmount.y / density.density
                                        )
                                    }
                                }
                                .clickable {
                                    if (selectedPreviewNote?.id == note.id) {
                                        fullNoteDetailsToView = note
                                    } else {
                                        selectedPreviewNote = note
                                    }
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = note.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = titleTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = FontFamily.Serif
                                )
                                if (note.tags.isNotEmpty()) {
                                    Text(
                                        text = "#${note.tags.first()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tagTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Legend Overlay (Top Left) - Styled beautifully to match dark theme
            Surface(
                color = Color(0xDD1A1A22), // Semi-transparent ultra-dark overlay
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Legend",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF2196F3)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("User Note", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0E0E0))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFC107)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Note", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0E0E0))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFBB86FC)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Atomic Concept", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0E0E0))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0x33FFFFFF))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Links (tap to edit)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    EdgeLegendRow(EDGE_SUPPORTS, "Supports")
                    Spacer(modifier = Modifier.height(4.dp))
                    EdgeLegendRow(EDGE_CONTRADICTS, "Contradicts")
                    Spacer(modifier = Modifier.height(4.dp))
                    EdgeLegendRow(EDGE_INSPIRED, "Inspired / relates")
                    Spacer(modifier = Modifier.height(4.dp))
                    EdgeLegendRow(EDGE_OTHER, "Other")
                }
            }

            // Preview Sheet (Bottom overlay when node is tapped)
            selectedPreviewNote?.let { note ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = note.title.ifBlank { "Untitled Note" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                            IconButton(onClick = { selectedPreviewNote = null }) {
                                Icon(Icons.Default.Close, "Dismiss preview")
                            }
                        }
                        Text(
                            text = note.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tap node again to view fully.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Button(
                                onClick = { fullNoteDetailsToView = note }
                            ) {
                                Text("Open Note")
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Inspect / delete a link (opened by tapping an edge)
    selectedLink?.let { link ->
        val noteA = notes.find { it.id == link.noteIdA }
        val noteB = notes.find { it.id == link.noteIdB }
        Dialog(onDismissRequest = { selectedLink = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(edgeColor(link.relationship))
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${noteA?.title?.ifBlank { "Untitled" } ?: "?"}  →  ${noteB?.title?.ifBlank { "Untitled" } ?: "?"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Relationship", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Text(link.relationship, style = MaterialTheme.typography.bodyMedium)
                    if (link.reasoning.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Reasoning", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Text(link.reasoning, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { selectedLink = null }) { Text("Close") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.deleteLink(link.id)
                                selectedLink = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("delete_link_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    // Dialogue: Add Custom Note Link Connection
    if (showAddLinkDialog) {
        var selectedANoteId by remember { mutableStateOf<Long?>(null) }
        var selectedBNoteId by remember { mutableStateOf<Long?>(null) }
        var relationship by remember { mutableStateOf("supports") } // supports, contradicts, inspired_by, etc.
        var reasoning by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddLinkDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Connect Notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Source Note A:", style = MaterialTheme.typography.labelMedium)
                    LazyRow {
                        items(notes) { note ->
                            FilterChip(
                                selected = selectedANoteId == note.id,
                                onClick = { selectedANoteId = note.id },
                                label = { Text(note.title.ifBlank { "Untitled" }) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Target Note B:", style = MaterialTheme.typography.labelMedium)
                    LazyRow {
                        items(notes) { note ->
                            FilterChip(
                                selected = selectedBNoteId == note.id,
                                onClick = { selectedBNoteId = note.id },
                                label = { Text(note.title.ifBlank { "Untitled" }) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = relationship,
                        onValueChange = { relationship = it },
                        label = { Text("Relationship (e.g. supports, refutes, quotes)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reasoning,
                        onValueChange = { reasoning = it },
                        label = { Text("Reasoning (How are they connected?)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showAddLinkDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedANoteId != null && selectedBNoteId != null) {
                                    viewModel.insertLink(
                                        selectedANoteId!!,
                                        selectedBNoteId!!,
                                        relationship,
                                        reasoning
                                    )
                                    showAddLinkDialog = false
                                }
                            },
                            enabled = selectedANoteId != null && selectedBNoteId != null
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }

    // Reuse Note Detail Dialog when clicked from Map
    fullNoteDetailsToView?.let { note ->
        Dialog(onDismissRequest = { fullNoteDetailsToView = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
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
                        IconButton(onClick = { fullNoteDetailsToView = null }) {
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
// VISUAL MAP HELPERS
// ==========================================

// Edge colors bucketed by relationship keyword.
private val EDGE_SUPPORTS = Color(0xFF66BB6A)    // green
private val EDGE_CONTRADICTS = Color(0xFFEF5350)  // red
private val EDGE_INSPIRED = Color(0xFF42A5F5)     // blue
private val EDGE_OTHER = Color(0xFF90A4AE)        // steel grey

/** Maps a link's free-text relationship onto a legend color bucket. */
fun edgeColor(relationship: String): Color {
    val r = relationship.lowercase()
    return when {
        r.contains("support") || r.contains("agree") || r.contains("shared") -> EDGE_SUPPORTS
        r.contains("contradict") || r.contains("refute") || r.contains("oppose") ||
            r.contains("diverg") || r.contains("against") -> EDGE_CONTRADICTS
        r.contains("inspir") || r.contains("relate") || r.contains("quote") ||
            r.contains("ascent") || r.contains("comparison") -> EDGE_INSPIRED
        else -> EDGE_OTHER
    }
}

@Composable
private fun EdgeLegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0E0E0))
    }
}

/**
 * Finds the visible link whose edge passes nearest to [tapPx] (in the map layer's
 * local pixel space), within a finger-sized threshold. Node positions are in dp,
 * so they're scaled by [density] to compare against the pixel tap point.
 */
private fun findLinkNearTap(
    tapPx: Offset,
    density: Float,
    links: List<Link>,
    filteredNotes: List<Note>,
    positions: Map<Long, Pair<Float, Float>>
): Link? {
    val threshold = 22f * density
    val visibleIds = filteredNotes.mapTo(HashSet()) { it.id }
    var best: Link? = null
    var bestDist = threshold
    for (link in links) {
        if (link.noteIdA !in visibleIds || link.noteIdB !in visibleIds) continue
        val a = positions[link.noteIdA] ?: continue
        val b = positions[link.noteIdB] ?: continue
        val ax = (a.first + 60f) * density
        val ay = (a.second + 40f) * density
        val bx = (b.first + 60f) * density
        val by = (b.second + 40f) * density
        val d = distanceToSegment(tapPx.x, tapPx.y, ax, ay, bx, by)
        if (d < bestDist) {
            bestDist = d
            best = link
        }
    }
    return best
}

/** Shortest distance from point (px,py) to the segment (ax,ay)-(bx,by). */
private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lenSq = dx * dx + dy * dy
    if (lenSq < 0.0001f) return kotlin.math.hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
    val projX = ax + t * dx
    val projY = ay + t * dy
    return kotlin.math.hypot(px - projX, py - projY)
}

/**
 * Centers and scales the map so all visible nodes fit inside [viewportSize].
 * The pan/zoom layer uses a center transform origin, so translation is derived
 * as scale * (viewportCenter - graphCenter) in pixels. No-op if nothing is laid out.
 */
private fun fitMapToView(
    viewModel: VaultViewModel,
    viewportSize: IntSize,
    density: Float,
    positions: Map<Long, Pair<Float, Float>>,
    filteredNotes: List<Note>
) {
    if (viewportSize.width == 0 || viewportSize.height == 0) return
    val visibleIds = filteredNotes.mapTo(HashSet()) { it.id }
    val visible = positions.filterKeys { it in visibleIds }
    val bounds = MapLayoutEngine.boundingBox(visible) ?: return

    val vw = viewportSize.width.toFloat()
    val vh = viewportSize.height.toFloat()
    // bounds are in dp; convert to px for comparison against the viewport.
    val graphW = (bounds.width * density).coerceAtLeast(1f)
    val graphH = (bounds.height * density).coerceAtLeast(1f)

    val scale = (0.9f * minOf(vw / graphW, vh / graphH)).coerceIn(0.3f, 2.5f)
    val graphCenterX = bounds.centerX * density
    val graphCenterY = bounds.centerY * density

    viewModel.mapScale.value = scale
    viewModel.mapOffsetX.value = scale * (vw / 2f - graphCenterX)
    viewModel.mapOffsetY.value = scale * (vh / 2f - graphCenterY)
}
