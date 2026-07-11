package com.example.ui.map

import com.example.data.Link
import com.example.data.Note
import kotlin.math.ln
import kotlin.math.max

/**
 * Pure positioning math for the Radial Knowledge Map. No Android/Compose types —
 * fully unit-testable on the JVM.
 *
 * Model:
 *  - The current center note sits at radius 0.
 *  - Every other note's radius encodes connection strength to the center:
 *    shortest-path hops through the links graph, log-scaled. Notes with no path
 *    to the center sit on the outer edge.
 *  - Angle encodes the note's creation-shelf: each shelf owns a wedge whose
 *    sweep is log-scaled by its note count, with a minimum sweep so tiny
 *    shelves stay comfortably tappable.
 *
 * Determinism: same input → identical output. Placement inside a wedge uses a
 * golden-ratio sequence keyed by note id (no RNG), so positions are stable
 * across sessions and animate smoothly when wedges resize.
 */
object RadialLayoutEngine {

    /** Glyph shape channel — independent from color. Extensible: add a case
     *  here and a branch in the screen's drawGlyph(); nothing else changes. */
    enum class NodeShape { CIRCLE, DIAMOND, SQUARE }

    data class Wedge(
        val shelf: String,
        val startDeg: Float,
        val sweepDeg: Float,
        val noteCount: Int
    ) {
        val midDeg: Float get() = startDeg + sweepDeg / 2f
        fun containsAngle(angleDeg: Float): Boolean {
            val a = ((angleDeg % 360f) + 360f) % 360f
            val s = ((startDeg % 360f) + 360f) % 360f
            val end = s + sweepDeg
            return if (end <= 360f) a >= s && a < end else a >= s || a < (end - 360f)
        }
    }

    data class RadialNode(
        val noteId: Long,
        /** 0 = center … 1 = outer edge (unreachable band). */
        val radiusNorm: Float,
        val angleDeg: Float,
        val creationShelf: String,
        /** Extra tag-shelves shown as ring segments (creation shelf excluded), capped. */
        val segmentShelves: List<String>,
        /** How many tag-shelves were cut by the segment cap ("+N more"). */
        val overflowSegments: Int,
        val shape: NodeShape,
        val isCenter: Boolean,
        /** Reserved secondary channel (e.g. recency); 1f = neutral, unused in v1. */
        val sizeFactor: Float = 1f
    )

    data class RadialLayout(
        val centerNoteId: Long?,
        val wedges: List<Wedge>,
        val nodes: List<RadialNode>
    ) {
        companion object { val EMPTY = RadialLayout(null, emptyList(), emptyList()) }
    }

    /** Minimum wedge sweep so even a 1-note shelf is comfortably tappable. */
    const val MIN_WEDGE_SWEEP_DEG = 24f
    /** Innermost ring for directly-linked notes — keeps them clear of the center glyph. */
    const val MIN_RADIUS_NORM = 0.18f
    /** Reachable notes stay inside this; the outer band is reserved for unreachable ones. */
    const val MAX_REACHABLE_RADIUS_NORM = 0.85f
    /** Visible ring-segment cap; beyond this the UI shows "+N". */
    const val MAX_SEGMENTS = 4
    /** Radial stagger between same-band neighbors so they never fully overlap. */
    private const val COLLISION_STEP = 0.022f
    private const val COLLISION_WRAP = 5
    private const val GOLDEN_RATIO_FRAC = 0.6180339887

    fun layout(notes: List<Note>, links: List<Link>, centerNoteId: Long?): RadialLayout {
        if (notes.isEmpty()) return RadialLayout.EMPTY

        // Resolve center: explicit choice, else most recent note. (createdAt never
        // changes on edit, so "recent" means most recently *created*.)
        val center = notes.firstOrNull { it.id == centerNoteId }
            ?: notes.maxByOrNull { it.createdAt }!!

        // --- Graph: undirected adjacency + degree ------------------------------
        val noteIds = notes.mapTo(HashSet()) { it.id }
        val adjacency = HashMap<Long, MutableSet<Long>>()
        for (link in links) {
            if (link.noteIdA !in noteIds || link.noteIdB !in noteIds) continue
            adjacency.getOrPut(link.noteIdA) { mutableSetOf() }.add(link.noteIdB)
            adjacency.getOrPut(link.noteIdB) { mutableSetOf() }.add(link.noteIdA)
        }
        val degree = { id: Long -> adjacency[id]?.size ?: 0 }

        // --- BFS hop distance from center --------------------------------------
        val hops = HashMap<Long, Int>()
        hops[center.id] = 0
        var frontier = listOf(center.id)
        var depth = 0
        while (frontier.isNotEmpty()) {
            depth++
            val next = mutableListOf<Long>()
            for (id in frontier) {
                for (neighbor in adjacency[id].orEmpty()) {
                    if (neighbor !in hops) {
                        hops[neighbor] = depth
                        next.add(neighbor)
                    }
                }
            }
            frontier = next
        }
        val maxHops = hops.values.max() // ≥ 0; 0 when nothing is linked to center

        // --- Wedges -------------------------------------------------------------
        val wedges = allocateWedges(notes)
        val wedgeByShelf = wedges.associateBy { it.shelf }

        // --- Radius per note ----------------------------------------------------
        // log-scaled hops normalized into [MIN, MAX_REACHABLE]; unreachable → 1.0.
        fun baseRadius(noteId: Long): Float {
            val h = hops[noteId] ?: return 1f
            if (h == 0) return 0f
            if (maxHops <= 1) return MIN_RADIUS_NORM
            val t = ln(1.0 + h) / ln(1.0 + maxHops)
            return MIN_RADIUS_NORM + ((MAX_REACHABLE_RADIUS_NORM - MIN_RADIUS_NORM) * t).toFloat()
        }

        // Collision stagger: within each (hop-band, shelf) group, order by link
        // count (stronger connections drawn very slightly closer) then id, and
        // step radii apart so same-band neighbors stay independently tappable.
        val staggerRank = HashMap<Long, Int>()
        notes.groupBy { Pair(hops[it.id], it.creationShelf.ifBlank { "unsorted" }) }
            .values.forEach { group ->
                group.sortedWith(compareByDescending<Note> { degree(it.id) }.thenBy { it.id })
                    .forEachIndexed { i, note -> staggerRank[note.id] = i % COLLISION_WRAP }
            }

        // --- Assemble nodes ------------------------------------------------------
        val nodes = notes.map { note ->
            val shelf = note.creationShelf.ifBlank { "unsorted" }
            val wedge = wedgeByShelf.getValue(shelf)
            val isCenter = note.id == center.id
            val rank = staggerRank[note.id] ?: 0

            val radius = when {
                isCenter -> 0f
                hops[note.id] == null -> // unreachable: outer edge, staggered inward
                    (1f - rank * COLLISION_STEP).coerceAtLeast(MAX_REACHABLE_RADIUS_NORM + 0.02f)
                else -> // reachable: staggered outward within the band
                    (baseRadius(note.id) + rank * COLLISION_STEP)
                        .coerceAtMost(MAX_REACHABLE_RADIUS_NORM)
            }

            // Golden-ratio fraction keyed by id → stable angle inside the wedge.
            val frac = (((note.id * GOLDEN_RATIO_FRAC) % 1.0 + 1.0) % 1.0).toFloat()
            val pad = minOf(4f, wedge.sweepDeg * 0.10f)
            val usable = max(wedge.sweepDeg - 2f * pad, 0.1f)
            val angle = if (isCenter) wedge.midDeg else wedge.startDeg + pad + frac * usable

            val extraShelves = note.tags
                .map { it.trim().lowercase().replace(" ", "_") }
                .filter { it.isNotEmpty() && it != shelf }
                .distinct()

            RadialNode(
                noteId = note.id,
                radiusNorm = radius,
                angleDeg = ((angle % 360f) + 360f) % 360f,
                creationShelf = shelf,
                segmentShelves = extraShelves.take(MAX_SEGMENTS),
                overflowSegments = max(0, extraShelves.size - MAX_SEGMENTS),
                shape = shapeFor(note.origin),
                isCenter = isCenter
            )
        }

        return RadialLayout(center.id, wedges, nodes)
    }

    /**
     * Wedge sweeps ∝ log2(1 + noteCount), normalized to 360°, with
     * [MIN_WEDGE_SWEEP_DEG] enforced iteratively. If there are so many shelves
     * that minimums alone exceed 360°, falls back to an equal split. A single
     * shelf gets the full circle. Shelves are sorted alphabetically so wedge
     * order (and color assignment) is stable.
     */
    fun allocateWedges(notes: List<Note>): List<Wedge> {
        val counts = notes
            .groupBy { it.creationShelf.ifBlank { "unsorted" } }
            .mapValues { it.value.size }
            .toSortedMap()
        if (counts.isEmpty()) return emptyList()
        if (counts.size == 1) {
            val (shelf, count) = counts.entries.first()
            return listOf(Wedge(shelf, 0f, 360f, count))
        }

        val shelves = counts.keys.toList()
        // Too many shelves for the minimum? Equal split, ignore the minimum.
        if (shelves.size * MIN_WEDGE_SWEEP_DEG >= 360f) {
            val sweep = 360f / shelves.size
            var start = 0f
            return shelves.map { s ->
                Wedge(s, start, sweep, counts.getValue(s)).also { start += sweep }
            }
        }

        // Proportional log sweeps, then iteratively pin any wedge below the
        // minimum and redistribute the remainder among the rest.
        val raw = shelves.associateWith { ln(1.0 + counts.getValue(it)) }
        val pinned = mutableSetOf<String>()
        var sweeps: Map<String, Float>
        while (true) {
            val freeShelves = shelves.filterNot { it in pinned }
            val freeSpace = 360f - pinned.size * MIN_WEDGE_SWEEP_DEG
            val freeTotal = freeShelves.sumOf { raw.getValue(it) }
            sweeps = shelves.associateWith { s ->
                if (s in pinned) MIN_WEDGE_SWEEP_DEG
                else (freeSpace * (raw.getValue(s) / freeTotal)).toFloat()
            }
            val newlyPinned = freeShelves.filter { sweeps.getValue(it) < MIN_WEDGE_SWEEP_DEG }
            if (newlyPinned.isEmpty()) break
            pinned.addAll(newlyPinned)
        }

        var start = 0f
        return shelves.map { s ->
            val sweep = sweeps.getValue(s)
            Wedge(s, start, sweep, counts.getValue(s)).also { start += sweep }
        }
    }

    fun shapeFor(origin: String): NodeShape = when (origin) {
        "ai" -> NodeShape.DIAMOND
        "atomic" -> NodeShape.SQUARE
        else -> NodeShape.CIRCLE // "user" and anything unknown
    }
}
