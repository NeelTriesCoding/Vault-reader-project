package com.example

import com.example.data.Link
import com.example.data.Note
import com.example.ui.map.RadialLayoutEngine
import com.example.ui.map.RadialLayoutEngine.NodeShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialLayoutEngineTest {

    private fun note(
        id: Long,
        shelf: String = "stoicism",
        tags: List<String> = listOf(shelf),
        origin: String = "user",
        createdAt: Long = id // creation order follows id unless overridden
    ) = Note(
        id = id, title = "n$id", content = "c", tags = tags,
        origin = origin, createdAt = createdAt, creationShelf = shelf
    )

    private fun link(a: Long, b: Long) =
        Link(id = a * 1000 + b, noteIdA = a, noteIdB = b, relationship = "r", reasoning = "")

    // --- Radius / BFS -------------------------------------------------------

    @Test
    fun `center note sits at radius zero`() {
        val layout = RadialLayoutEngine.layout(
            listOf(note(1), note(2)), listOf(link(1, 2)), centerNoteId = 1
        )
        assertEquals(0f, layout.nodes.first { it.noteId == 1L }.radiusNorm, 1e-6f)
        assertTrue(layout.nodes.first { it.noteId == 1L }.isCenter)
    }

    @Test
    fun `radius grows with hop distance`() {
        // chain: 1 - 2 - 3 - 4
        val notes = (1L..4L).map { note(it) }
        val links = listOf(link(1, 2), link(2, 3), link(3, 4))
        val layout = RadialLayoutEngine.layout(notes, links, centerNoteId = 1)
        val r = layout.nodes.associate { it.noteId to it.radiusNorm }
        assertTrue(r.getValue(2L) < r.getValue(3L))
        assertTrue(r.getValue(3L) < r.getValue(4L))
        assertTrue(r.getValue(4L) <= RadialLayoutEngine.MAX_REACHABLE_RADIUS_NORM + 1e-6f)
    }

    @Test
    fun `unreachable notes sit on the outer edge beyond all reachable ones`() {
        val notes = listOf(note(1), note(2), note(3)) // 3 has no links
        val links = listOf(link(1, 2))
        val layout = RadialLayoutEngine.layout(notes, links, centerNoteId = 1)
        val reachable = layout.nodes.first { it.noteId == 2L }.radiusNorm
        val orphan = layout.nodes.first { it.noteId == 3L }.radiusNorm
        assertTrue(orphan > reachable)
        assertTrue(orphan > RadialLayoutEngine.MAX_REACHABLE_RADIUS_NORM)
    }

    @Test
    fun `default center is the most recent note`() {
        val notes = listOf(
            note(1, createdAt = 100), note(2, createdAt = 300), note(3, createdAt = 200)
        )
        val layout = RadialLayoutEngine.layout(notes, emptyList(), centerNoteId = null)
        assertEquals(2L, layout.centerNoteId)
    }

    // --- Wedges ---------------------------------------------------------------

    @Test
    fun `wedge sweeps sum to 360 and respect the minimum`() {
        val notes =
            (1L..20L).map { note(it, shelf = "big") } +
                listOf(note(100, shelf = "tiny")) // 1-note shelf
        val wedges = RadialLayoutEngine.allocateWedges(notes)
        assertEquals(360f, wedges.map { it.sweepDeg }.sum(), 0.01f)
        assertTrue(wedges.all { it.sweepDeg >= RadialLayoutEngine.MIN_WEDGE_SWEEP_DEG - 0.01f })
        val tiny = wedges.first { it.shelf == "tiny" }
        val big = wedges.first { it.shelf == "big" }
        assertTrue(big.sweepDeg > tiny.sweepDeg)
    }

    @Test
    fun `single shelf gets the full circle`() {
        val wedges = RadialLayoutEngine.allocateWedges(listOf(note(1), note(2)))
        assertEquals(1, wedges.size)
        assertEquals(360f, wedges[0].sweepDeg, 0.01f)
    }

    @Test
    fun `many shelves fall back to an equal split summing to 360`() {
        val notes = (1L..20L).map { note(it, shelf = "shelf$it") } // 20 * 24 > 360
        val wedges = RadialLayoutEngine.allocateWedges(notes)
        assertEquals(20, wedges.size)
        assertEquals(360f, wedges.map { it.sweepDeg }.sum(), 0.01f)
        assertEquals(wedges[0].sweepDeg, wedges[19].sweepDeg, 0.01f)
    }

    @Test
    fun `untagged notes land in an unsorted wedge`() {
        val notes = listOf(note(1, shelf = ""), note(2, shelf = "stoicism"))
        val layout = RadialLayoutEngine.layout(notes, emptyList(), centerNoteId = 2)
        assertTrue(layout.wedges.any { it.shelf == "unsorted" })
        assertEquals("unsorted", layout.nodes.first { it.noteId == 1L }.creationShelf)
    }

    // --- Placement -------------------------------------------------------------

    @Test
    fun `layout is deterministic`() {
        val notes = (1L..10L).map { note(it, shelf = if (it % 2 == 0L) "a" else "b") }
        val links = listOf(link(1, 2), link(2, 3), link(4, 5))
        val first = RadialLayoutEngine.layout(notes, links, centerNoteId = 1)
        val second = RadialLayoutEngine.layout(notes, links, centerNoteId = 1)
        assertEquals(first, second)
    }

    @Test
    fun `no two non-center nodes share the same position`() {
        // Same shelf, all unlinked (same radius band) — jitter must separate them.
        val notes = (1L..8L).map { note(it, shelf = "same") }
        val layout = RadialLayoutEngine.layout(notes, emptyList(), centerNoteId = 1)
        val positions = layout.nodes.filterNot { it.isCenter }
            .map { Pair(it.radiusNorm, it.angleDeg) }
        assertEquals(positions.size, positions.toSet().size)
    }

    @Test
    fun `node angle stays inside its wedge`() {
        val notes = (1L..12L).map { note(it, shelf = if (it <= 6) "a" else "b") }
        val layout = RadialLayoutEngine.layout(notes, emptyList(), centerNoteId = 1)
        for (node in layout.nodes) {
            val wedge = layout.wedges.first { it.shelf == node.creationShelf }
            assertTrue(
                "note ${node.noteId} angle ${node.angleDeg} outside wedge ${wedge.shelf}",
                wedge.containsAngle(node.angleDeg)
            )
        }
    }

    // --- Encoding ----------------------------------------------------------------

    @Test
    fun `shape follows origin with room for a third`() {
        assertEquals(NodeShape.CIRCLE, RadialLayoutEngine.shapeFor("user"))
        assertEquals(NodeShape.DIAMOND, RadialLayoutEngine.shapeFor("ai"))
        assertEquals(NodeShape.SQUARE, RadialLayoutEngine.shapeFor("atomic"))
        assertEquals(NodeShape.CIRCLE, RadialLayoutEngine.shapeFor("future_unknown"))
    }

    @Test
    fun `ring segments cap at four with overflow count`() {
        val tags = listOf("home", "t1", "t2", "t3", "t4", "t5", "t6")
        val n = note(1, shelf = "home", tags = tags)
        val layout = RadialLayoutEngine.layout(listOf(n), emptyList(), centerNoteId = 1)
        val node = layout.nodes.first()
        assertEquals(RadialLayoutEngine.MAX_SEGMENTS, node.segmentShelves.size)
        assertEquals(2, node.overflowSegments) // 6 extra tags − 4 shown
        assertTrue(node.segmentShelves.none { it == "home" }) // creation shelf excluded
    }
}
