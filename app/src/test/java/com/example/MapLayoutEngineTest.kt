package com.example

import com.example.ui.map.MapLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayoutEngineTest {

    @Test
    fun `seed positions are finite and spread out`() {
        val p0 = MapLayoutEngine.seedPosition(0)
        val p1 = MapLayoutEngine.seedPosition(1)
        val p10 = MapLayoutEngine.seedPosition(10)
        for (p in listOf(p0, p1, p10)) {
            assertTrue(p.first.isFinite())
            assertTrue(p.second.isFinite())
        }
        // Later indices sit farther from the origin (spiral grows outward).
        val r1 = p1.first * p1.first + p1.second * p1.second
        val r10 = p10.first * p10.first + p10.second * p10.second
        assertTrue(r10 > r1)
    }

    @Test
    fun `relax keeps positions finite and does not diverge to NaN`() {
        val positions = mapOf(
            1L to Pair(0f, 0f),
            2L to Pair(0f, 0f), // deliberately coincident to test the anti-NaN nudge
            3L to Pair(50f, 50f),
            4L to Pair(-50f, -50f)
        )
        val edges = listOf(1L to 2L, 2L to 3L, 3L to 4L)
        val relaxed = MapLayoutEngine.relax(positions, edges, iterations = 80)

        assertEquals(positions.size, relaxed.size)
        for ((_, p) in relaxed) {
            assertTrue("x must be finite", p.first.isFinite())
            assertTrue("y must be finite", p.second.isFinite())
        }
        // The two coincident nodes must be pushed apart.
        val a = relaxed.getValue(1L)
        val b = relaxed.getValue(2L)
        val dist = Math.hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble())
        assertTrue("coincident nodes should separate", dist > 1.0)
    }

    @Test
    fun `relax is a no-op for fewer than two nodes`() {
        val single = mapOf(1L to Pair(5f, 5f))
        assertEquals(single, MapLayoutEngine.relax(single, emptyList()))
    }

    @Test
    fun `bounding box covers all nodes plus card size`() {
        val positions = mapOf(
            1L to Pair(0f, 0f),
            2L to Pair(100f, 200f)
        )
        val bounds = MapLayoutEngine.boundingBox(positions)
        assertNotNull(bounds)
        bounds!!
        assertEquals(0f, bounds.minX, 0.001f)
        assertEquals(0f, bounds.minY, 0.001f)
        assertEquals(100f + MapLayoutEngine.NODE_WIDTH, bounds.maxX, 0.001f)
        assertEquals(200f + MapLayoutEngine.NODE_HEIGHT, bounds.maxY, 0.001f)
    }

    @Test
    fun `bounding box is null when empty`() {
        assertEquals(null, MapLayoutEngine.boundingBox(emptyMap()))
    }
}
