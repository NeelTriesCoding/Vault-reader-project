package com.example.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Note
import com.example.ui.VaultViewModel
import com.example.ui.map.RadialLayoutEngine.NodeShape
import com.example.ui.map.RadialLayoutEngine.RadialLayout
import com.example.ui.map.RadialLayoutEngine.RadialNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Radial Knowledge Map. The current center note sits in the middle; radius
 * encodes connection strength (graph distance) to it, angle encodes the note's
 * creation-shelf wedge. See [RadialLayoutEngine] for the positioning math.
 *
 * Rendering: a single Canvas under one camera transform (pan + zoom). All
 * geometry lives in "map space" (px, origin at map center); the camera maps it
 * to the screen, so hit-testing is one inverse transform.
 *
 * Motion model (all physical, nothing snaps):
 *  - Layout changes (recenter, wedge redistribution) glide in polar space
 *    behind a single eased progress animation.
 *  - Pan carries momentum on release (spline decay) and rubber-bands at the
 *    edge of the sensible area; zoom resists past its limits and springs back.
 *  - Wedge taps ripple a soft two-cycle pulse on every matching note.
 */

// --- Tunables -----------------------------------------------------------------

private val NODE_RADIUS = 13.dp
private val CENTER_NODE_RADIUS = 19.dp
private val SEGMENT_RING_GAP = 3.dp
private val SEGMENT_RING_WIDTH = 3.dp
private const val LABEL_ZOOM_THRESHOLD = 1.3f
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 3.5f
private const val ZOOM_OVERSHOOT = 1.25f          // rubber-band range past the limits
private const val MAP_EDGE_PADDING_FRACTION = 0.88f
private const val LAYOUT_ANIM_MS = 420            // recenter / redistribution glide
private const val PULSE_MS = 1400                 // two soft cycles
private val MIN_TOUCH_TARGET = 44.dp              // per-spec minimum hit size

// --- Small value types -----------------------------------------------------------

private data class Polar(val radiusNorm: Float, val angleDeg: Float)

private fun lerpPolar(start: Polar, end: Polar, t: Float): Polar {
    // Shortest-path angle interpolation so nodes never swing the long way round.
    val delta = ((end.angleDeg - start.angleDeg + 540f) % 360f) - 180f
    return Polar(
        radiusNorm = start.radiusNorm + (end.radiusNorm - start.radiusNorm) * t,
        angleDeg = start.angleDeg + delta * t
    )
}

private fun Polar.toOffset(mapRadius: Float): Offset {
    val r = radiusNorm * mapRadius
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset((r * cos(rad)).toFloat(), (r * sin(rad)).toFloat())
}

@Composable
fun RadialMapScreen(viewModel: VaultViewModel) {
    val layout by viewModel.radialLayout.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val shelfPulse by viewModel.shelfPulse.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()

    // Camera state lives in the ViewModel so it survives tab switches.
    val zoom by viewModel.mapScale.collectAsStateWithLifecycle()
    val panX by viewModel.mapOffsetX.collectAsStateWithLifecycle()
    val panY by viewModel.mapOffsetY.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val sortedShelves = remember(layout) { layout.wedges.map { it.shelf } }
    val notesById = remember(notes) { notes.associateBy { it.id } }

    // --- Layout glide: previous polar positions ease into the new ones -------------
    val layoutProgress = remember { Animatable(1f) }
    var startPositions by remember { mutableStateOf<Map<Long, Polar>>(emptyMap()) }
    var targetPositions by remember { mutableStateOf<Map<Long, Polar>>(emptyMap()) }

    LaunchedEffect(layout) {
        val newTargets = layout.nodes.associate {
            it.noteId to Polar(it.radiusNorm, it.angleDeg)
        }
        if (targetPositions.isEmpty()) {
            // First composition: no glide, just settle.
            startPositions = newTargets
            targetPositions = newTargets
            layoutProgress.snapTo(1f)
        } else if (newTargets != targetPositions) {
            // Capture where nodes are RIGHT NOW (possibly mid-glide) as the start.
            val t = layoutProgress.value
            startPositions = newTargets.mapValues { (id, target) ->
                val prevStart = startPositions[id]
                val prevTarget = targetPositions[id]
                when {
                    prevStart != null && prevTarget != null -> lerpPolar(prevStart, prevTarget, t)
                    prevTarget != null -> prevTarget
                    else -> target // brand-new note: appears in place (no fly-in)
                }
            }
            targetPositions = newTargets
            layoutProgress.snapTo(0f)
            layoutProgress.animateTo(1f, tween(LAYOUT_ANIM_MS, easing = FastOutSlowInEasing))
        }
    }

    fun currentPolar(node: RadialNode): Polar {
        val target = targetPositions[node.noteId] ?: Polar(node.radiusNorm, node.angleDeg)
        val start = startPositions[node.noteId] ?: target
        return lerpPolar(start, target, layoutProgress.value)
    }

    // --- Shelf pulse ------------------------------------------------------------------
    val pulseProgress = remember { Animatable(1f) } // 1 = idle
    var pulsingShelf by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(shelfPulse) {
        val (shelf, _) = shelfPulse ?: return@LaunchedEffect
        pulsingShelf = shelf
        pulseProgress.snapTo(0f)
        pulseProgress.animateTo(1f, tween(PULSE_MS))
        pulsingShelf = null
    }
    // Notes matching the pulsed shelf — by creation shelf OR any current tag,
    // regardless of which wedge they physically sit in.
    val pulsedNoteIds = remember(pulsingShelf, notes) {
        val shelf = pulsingShelf ?: return@remember emptySet<Long>()
        notes.filter { n ->
            n.creationShelf == shelf ||
                n.tags.any { it.trim().lowercase().replace(" ", "_") == shelf }
        }.mapTo(HashSet()) { it.id }
    }

    // --- Long-press card ----------------------------------------------------------------
    var cardNoteId by remember { mutableStateOf<Long?>(null) }

    // --- Camera helpers -----------------------------------------------------------------
    val flingJobs = remember { mutableStateOf<List<Job>>(emptyList()) }
    fun cancelFlings() {
        flingJobs.value.forEach { it.cancel() }
        flingJobs.value = emptyList()
    }

    fun screenToMap(screen: Offset): Offset {
        val cx = viewport.width / 2f
        val cy = viewport.height / 2f
        return Offset((screen.x - cx - panX) / zoom, (screen.y - cy - panY) / zoom)
    }

    fun mapToScreen(map: Offset): Offset {
        val cx = viewport.width / 2f
        val cy = viewport.height / 2f
        return Offset(cx + panX + map.x * zoom, cy + panY + map.y * zoom)
    }

    fun mapRadiusPx(): Float =
        min(viewport.width, viewport.height) / 2f * MAP_EDGE_PADDING_FRACTION

    /** Furthest the camera may rest from home, so the map can't be lost off-screen. */
    fun panLimit(): Float = mapRadiusPx() * zoom + min(viewport.width, viewport.height) / 4f

    fun settleCamera() {
        // Spring zoom and pan back inside their bounds after rubber-banding.
        val targetZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val limit = panLimit()
        val dist = hypot(panX, panY)
        val (targetX, targetY) = if (dist > limit && dist > 0f) {
            Pair(panX / dist * limit, panY / dist * limit)
        } else Pair(panX, panY)

        if (targetZoom != zoom || targetX != panX || targetY != panY) {
            val springSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
            flingJobs.value = flingJobs.value + listOf(
                scope.launch {
                    Animatable(viewModel.mapScale.value).animateTo(targetZoom, springSpec) {
                        viewModel.mapScale.value = value
                    }
                },
                scope.launch {
                    Animatable(viewModel.mapOffsetX.value).animateTo(targetX, springSpec) {
                        viewModel.mapOffsetX.value = value
                    }
                },
                scope.launch {
                    Animatable(viewModel.mapOffsetY.value).animateTo(targetY, springSpec) {
                        viewModel.mapOffsetY.value = value
                    }
                }
            )
        }
    }

    fun animateCameraHome() {
        cancelFlings()
        val springSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
        flingJobs.value = listOf(
            scope.launch {
                Animatable(viewModel.mapScale.value).animateTo(1f, springSpec) {
                    viewModel.mapScale.value = value
                }
            },
            scope.launch {
                Animatable(viewModel.mapOffsetX.value).animateTo(0f, springSpec) {
                    viewModel.mapOffsetX.value = value
                }
            },
            scope.launch {
                Animatable(viewModel.mapOffsetY.value).animateTo(0f, springSpec) {
                    viewModel.mapOffsetY.value = value
                }
            }
        )
    }

    // --- Hit-testing ------------------------------------------------------------------------
    fun nodeAt(screen: Offset): RadialNode? {
        if (viewport == IntSize.Zero) return null
        val mapPoint = screenToMap(screen)
        val mapRadius = mapRadiusPx()
        // 44dp minimum touch target in SCREEN space → divide by zoom for map space.
        val minTargetMap = with(density) { (MIN_TOUCH_TARGET.toPx() / 2f) } / zoom
        var best: RadialNode? = null
        var bestDist = Float.MAX_VALUE
        for (node in layout.nodes) {
            val pos = currentPolar(node).toOffset(mapRadius)
            val glyphMap = with(density) {
                (if (node.isCenter) CENTER_NODE_RADIUS else NODE_RADIUS).toPx()
            }
            val threshold = maxOf(glyphMap, minTargetMap)
            val d = (mapPoint - pos).getDistance()
            if (d <= threshold && d < bestDist) {
                best = node
                bestDist = d
            }
        }
        return best
    }

    fun wedgeAt(screen: Offset): RadialLayoutEngine.Wedge? {
        if (viewport == IntSize.Zero) return null
        val mapPoint = screenToMap(screen)
        val r = mapPoint.getDistance()
        if (r > mapRadiusPx()) return null
        val angle = Math.toDegrees(atan2(mapPoint.y.toDouble(), mapPoint.x.toDouble())).toFloat()
        return layout.wedges.firstOrNull { it.containsAngle(angle) }
    }

    val canvasColor = if (dark) MapColors.canvasDark else MapColors.canvasLight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(canvasColor)
            .onSizeChanged { viewport = it }
            .testTag("radial_map")
    ) {
        if (layout.nodes.isEmpty()) {
            EmptyMapState(dark = dark)
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Taps and long-presses. A pan/pinch consumes its events past
                    // touch slop, which cancels these automatically.
                    .pointerInput(layout, viewport) {
                        detectTapGestures(
                            onTap = { screen ->
                                val node = nodeAt(screen)
                                when {
                                    node != null -> {
                                        cardNoteId = null
                                        if (!node.isCenter) viewModel.recenterOn(node.noteId)
                                    }
                                    cardNoteId != null -> cardNoteId = null // tap-away dismiss
                                    else -> wedgeAt(screen)?.let { viewModel.pulseShelf(it.shelf) }
                                }
                            },
                            onLongPress = { screen ->
                                nodeAt(screen)?.let { cardNoteId = it.noteId }
                            }
                        )
                    }
                    // Physical pan/zoom: velocity-tracked, momentum on release,
                    // rubber-band past the limits with a spring settle.
                    .pointerInput(viewport) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            cancelFlings()
                            val velocityTracker = VelocityTracker()
                            var pastSlop = false
                            var slopAccum = Offset.Zero

                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break
                                val zoomChange = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()

                                if (!pastSlop) {
                                    slopAccum += pan
                                    if (slopAccum.getDistance() > touchSlop || zoomChange != 1f) {
                                        pastSlop = true
                                    }
                                }
                                if (pastSlop) {
                                    if (zoomChange != 1f && centroid.isSpecified) {
                                        // Zoom about the pinch centroid, with
                                        // rubber-band resistance past the limits.
                                        val old = viewModel.mapScale.value
                                        var next = old * zoomChange
                                        if (next > MAX_ZOOM) {
                                            next = MAX_ZOOM +
                                                (next - MAX_ZOOM) / (1f + (next - MAX_ZOOM))
                                            next = next.coerceAtMost(MAX_ZOOM * ZOOM_OVERSHOOT)
                                        } else if (next < MIN_ZOOM) {
                                            next = next.coerceAtLeast(MIN_ZOOM / ZOOM_OVERSHOOT)
                                        }
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val mapPX = (centroid.x - cx - viewModel.mapOffsetX.value) / old
                                        val mapPY = (centroid.y - cy - viewModel.mapOffsetY.value) / old
                                        viewModel.mapScale.value = next
                                        viewModel.mapOffsetX.value = centroid.x - cx - mapPX * next
                                        viewModel.mapOffsetY.value = centroid.y - cy - mapPY * next
                                    }
                                    if (pan != Offset.Zero) {
                                        // Damped drag once beyond the pan limit.
                                        val limit = panLimit()
                                        val dist = hypot(
                                            viewModel.mapOffsetX.value,
                                            viewModel.mapOffsetY.value
                                        )
                                        val damp = if (dist > limit) 0.35f else 1f
                                        viewModel.mapOffsetX.value += pan.x * damp
                                        viewModel.mapOffsetY.value += pan.y * damp
                                    }
                                    if (centroid.isSpecified) {
                                        velocityTracker.addPosition(
                                            event.changes.first().uptimeMillis, centroid
                                        )
                                    }
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            }

                            if (pastSlop) {
                                // Momentum, then settle any rubber-banded overshoot.
                                val velocity = velocityTracker.calculateVelocity()
                                val decay = splineBasedDecay<Float>(this)
                                val limit = panLimit()
                                val jobs = listOf(
                                    scope.launch {
                                        AnimationState(viewModel.mapOffsetX.value, velocity.x)
                                            .animateDecay(decay) {
                                                viewModel.mapOffsetX.value =
                                                    value.coerceIn(-limit, limit)
                                                if (abs(value) > limit) cancelAnimation()
                                            }
                                    },
                                    scope.launch {
                                        AnimationState(viewModel.mapOffsetY.value, velocity.y)
                                            .animateDecay(decay) {
                                                viewModel.mapOffsetY.value =
                                                    value.coerceIn(-limit, limit)
                                                if (abs(value) > limit) cancelAnimation()
                                            }
                                    }
                                )
                                // The watcher is tracked too, so a new gesture
                                // cancels the pending settle along with the fling.
                                val watcher = scope.launch {
                                    jobs.forEach { it.join() }
                                    settleCamera()
                                }
                                flingJobs.value = jobs + watcher
                            }
                        }
                    }
            ) {
                val mapRadius = mapRadiusPx()
                withTransform({
                    translate(left = size.width / 2f + panX, top = size.height / 2f + panY)
                    scale(scaleX = zoom, scaleY = zoom, pivot = Offset.Zero)
                }) {
                    drawWedges(layout.wedges, sortedShelves, mapRadius, dark)
                    // Center note draws last (on top).
                    for (node in layout.nodes.sortedBy { if (it.isCenter) 1 else 0 }) {
                        val polar = currentPolar(node)
                        drawNode(
                            node = node,
                            position = polar.toOffset(mapRadius),
                            sortedShelves = sortedShelves,
                            dark = dark,
                            zoom = zoom,
                            title = notesById[node.noteId]?.title?.ifBlank { "Untitled" }
                                ?: "Untitled",
                            textMeasurer = textMeasurer,
                            density = density.density,
                            pulse = if (node.noteId in pulsedNoteIds) pulseProgress.value else null
                        )
                    }
                }
            }
        }

        // Long-press card: appears near its note, never screen-centered.
        cardNoteId?.let { id ->
            val note = notesById[id]
            val node = layout.nodes.firstOrNull { it.noteId == id }
            if (note != null && node != null && viewport != IntSize.Zero) {
                NotePeekCard(
                    note = note,
                    nodeScreenPos = mapToScreen(currentPolar(node).toOffset(mapRadiusPx())),
                    viewport = viewport,
                    sortedShelves = sortedShelves,
                    dark = dark,
                    onDismiss = { cardNoteId = null }
                )
            }
        }

        // Persistent, unobtrusive "take me home".
        IconButton(
            onClick = {
                cardNoteId = null
                animateCameraHome()
                viewModel.resetRadialMap()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .testTag("radial_map_reset")
        ) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = "Reset map view",
                tint = if (dark) Color(0xFFB9B9C0) else Color(0xFF5A5A60)
            )
        }
    }
}

// --- Long-press peek card ------------------------------------------------------------

@Composable
private fun NotePeekCard(
    note: Note,
    nodeScreenPos: Offset,
    viewport: IntSize,
    sortedShelves: List<String>,
    dark: Boolean,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val cardWidth = with(density) { 240.dp.toPx() }
    val margin = with(density) { 12.dp.toPx() }
    val gap = with(density) { 28.dp.toPx() }

    // Place beside the node, biased toward screen center, clamped to the viewport.
    val x = (if (nodeScreenPos.x < viewport.width / 2f) nodeScreenPos.x + gap
    else nodeScreenPos.x - gap - cardWidth)
        .coerceIn(margin, (viewport.width - cardWidth - margin).coerceAtLeast(margin))
    val estHeight = with(density) { 150.dp.toPx() }
    val y = (nodeScreenPos.y - estHeight / 2f)
        .coerceIn(margin, (viewport.height - estHeight - margin).coerceAtLeast(margin))

    val summaryText = remember(note) { peekSummary(note) }
    val shelves = remember(note) {
        (listOf(note.creationShelf) + note.tags.map { it.trim().lowercase().replace(" ", "_") })
            .filter { it.isNotEmpty() }
            .distinct()
            .take(5)
    }

    ElevatedCard(
        modifier = Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .widthIn(max = 240.dp)
            .testTag("note_peek_card")
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(top = 6.dp)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                shelves.forEach { shelf ->
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MapColors.shelfColor(shelf, sortedShelves, dark))
                    )
                }
                Text(
                    text = shelves.joinToString("  ") { it },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** ~20-word card text: precomputed shortSummary, else truncate what exists. */
internal fun peekSummary(note: Note): String {
    note.shortSummary?.takeIf { it.isNotBlank() }?.let { return it }
    val source = note.summary?.takeIf { it.isNotBlank() } ?: note.content
    val words = source.trim().split(Regex("\\s+"))
    return if (words.size <= 20) words.joinToString(" ")
    else words.take(20).joinToString(" ") + "…"
}

// --- Empty / first-run state -----------------------------------------------------------

@Composable
private fun EmptyMapState(dark: Boolean) {
    val breathe by rememberInfiniteTransition(label = "empty_breathe").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "empty_breathe_alpha"
    )
    val dotColor = if (dark) Color(0xFF8E8E98) else Color(0xFFB5B0A6)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(dotColor.copy(alpha = breathe), radius = 10.dp.toPx(), center = center)
            drawCircle(
                color = dotColor.copy(alpha = breathe * 0.35f),
                radius = 22.dp.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        Text(
            text = "Your notes will appear here as you add them",
            style = MaterialTheme.typography.bodyMedium,
            color = dotColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 96.dp)
        )
    }
}

// --- Drawing ----------------------------------------------------------------------------

private fun DrawScope.drawWedges(
    wedges: List<RadialLayoutEngine.Wedge>,
    sortedShelves: List<String>,
    mapRadius: Float,
    dark: Boolean
) {
    if (wedges.isEmpty()) return
    val fillAlpha = if (dark) MapColors.wedgeFillAlphaDark else MapColors.wedgeFillAlphaLight
    val hairline = if (dark) MapColors.hairlineDark else MapColors.hairlineLight
    val arcRect = Rect(-mapRadius, -mapRadius, mapRadius, mapRadius)

    for (wedge in wedges) {
        drawArc(
            color = MapColors.shelfColor(wedge.shelf, sortedShelves, dark).copy(alpha = fillAlpha),
            startAngle = wedge.startDeg,
            sweepAngle = wedge.sweepDeg,
            useCenter = true,
            topLeft = arcRect.topLeft,
            size = Size(arcRect.width, arcRect.height)
        )
    }
    if (wedges.size > 1) {
        for (wedge in wedges) {
            val rad = Math.toRadians(wedge.startDeg.toDouble())
            drawLine(
                color = hairline,
                start = Offset.Zero,
                end = Offset((mapRadius * cos(rad)).toFloat(), (mapRadius * sin(rad)).toFloat()),
                strokeWidth = 1f
            )
        }
    }
    drawCircle(color = hairline, radius = mapRadius, center = Offset.Zero, style = Stroke(width = 1f))
}

private fun DrawScope.drawNode(
    node: RadialNode,
    position: Offset,
    sortedShelves: List<String>,
    dark: Boolean,
    zoom: Float,
    title: String,
    textMeasurer: TextMeasurer,
    density: Float,
    pulse: Float? // 0..1 while this note is pulsing, null when idle
) {
    val glyphRadius =
        (if (node.isCenter) CENTER_NODE_RADIUS else NODE_RADIUS).toPx() * node.sizeFactor
    val fill = MapColors.shelfColor(node.creationShelf, sortedShelves, dark)
    val outline = if (dark) Color(0x66FFFFFF) else Color(0x59000000)

    if (node.isCenter) {
        drawCircle(
            color = fill.copy(alpha = if (dark) 0.28f else 0.18f),
            radius = glyphRadius * 1.9f,
            center = position
        )
    }

    // Soft "look here" pulse: two expanding, fading ripples — calm, not a flash.
    if (pulse != null && pulse < 1f) {
        repeat(2) { ripple ->
            val local = ((pulse * 2f) - ripple * 0.5f).coerceIn(0f, 1f)
            if (local > 0f && local < 1f) {
                drawCircle(
                    color = fill.copy(alpha = (1f - local) * 0.45f),
                    radius = glyphRadius * (1.2f + local * 1.1f),
                    center = position,
                    style = Stroke(width = (3f - 2f * local) * density)
                )
            }
        }
    }

    drawGlyph(node.shape, position, glyphRadius, fill, outline)

    if (node.segmentShelves.isNotEmpty()) {
        val ringRadius = glyphRadius + SEGMENT_RING_GAP.toPx() + SEGMENT_RING_WIDTH.toPx() / 2f
        val ringRect = Rect(
            position.x - ringRadius, position.y - ringRadius,
            position.x + ringRadius, position.y + ringRadius
        )
        val count = node.segmentShelves.size
        val gapDeg = 8f
        val sweep = (360f / count) - gapDeg
        node.segmentShelves.forEachIndexed { i, shelf ->
            drawArc(
                color = MapColors.shelfColor(shelf, sortedShelves, dark),
                startAngle = -90f + i * (360f / count) + gapDeg / 2f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = ringRect.topLeft,
                size = Size(ringRect.width, ringRect.height),
                style = Stroke(width = SEGMENT_RING_WIDTH.toPx())
            )
        }
    }

    // Zoom-gated labels: hidden rather than shrunk into illegibility. The center
    // note's label always shows — it's the map's "you are here".
    if (zoom >= LABEL_ZOOM_THRESHOLD || node.isCenter) {
        val measured = textMeasurer.measure(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = if (node.isCenter) 12.sp else 10.sp,
                fontWeight = if (node.isCenter) FontWeight.SemiBold else FontWeight.Normal,
                color = if (dark) Color(0xFFE8E8EC) else Color(0xFF2C2C30),
                textAlign = TextAlign.Center
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = (96f * density).toInt())
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                position.x - measured.size.width / 2f,
                position.y + glyphRadius + SEGMENT_RING_GAP.toPx() +
                    SEGMENT_RING_WIDTH.toPx() + 4f * density
            )
        )
        if (node.overflowSegments > 0 && zoom >= LABEL_ZOOM_THRESHOLD) {
            val plus = textMeasurer.measure(
                text = "+${node.overflowSegments}",
                style = TextStyle(
                    fontSize = 8.sp,
                    color = if (dark) Color(0xFFA9A9B2) else Color(0xFF6E6E74)
                )
            )
            drawText(
                textLayoutResult = plus,
                topLeft = Offset(
                    position.x + glyphRadius + SEGMENT_RING_GAP.toPx() +
                        SEGMENT_RING_WIDTH.toPx() + 2f * density,
                    position.y - glyphRadius - plus.size.height
                )
            )
        }
    }
}

/**
 * One glyph per note. Shape is an independent channel from color (user =
 * circle, AI = diamond, atomic = square); adding a future shape means one new
 * enum case and one new branch here — nothing else changes.
 */
private fun DrawScope.drawGlyph(
    shape: NodeShape,
    center: Offset,
    radius: Float,
    fill: Color,
    outline: Color
) {
    when (shape) {
        NodeShape.CIRCLE -> {
            drawCircle(color = fill, radius = radius, center = center)
            drawCircle(color = outline, radius = radius, center = center, style = Stroke(width = 1.5f))
        }
        NodeShape.DIAMOND -> {
            val path = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y)
                lineTo(center.x, center.y + radius)
                lineTo(center.x - radius, center.y)
                close()
            }
            drawPath(path, color = fill)
            drawPath(path, color = outline, style = Stroke(width = 1.5f))
        }
        NodeShape.SQUARE -> {
            val side = radius * 1.62f
            val topLeft = Offset(center.x - side / 2f, center.y - side / 2f)
            val corner = androidx.compose.ui.geometry.CornerRadius(side * 0.18f)
            drawRoundRect(color = fill, topLeft = topLeft, size = Size(side, side), cornerRadius = corner)
            drawRoundRect(
                color = outline,
                topLeft = topLeft,
                size = Size(side, side),
                cornerRadius = corner,
                style = Stroke(width = 1.5f)
            )
        }
    }
}
