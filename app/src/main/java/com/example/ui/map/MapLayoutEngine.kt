package com.example.ui.map

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure layout math for the Visual Map. Kept free of Android/Compose types so it
 * can be unit-tested in isolation.
 *
 * Positions are the top-left corner of each node card in map-space, keyed by note
 * id. New nodes are seeded on a Fermat (golden-angle) spiral; the whole graph can
 * then be relaxed with a lightweight force-directed pass so that linked notes pull
 * together and unrelated ones spread apart.
 */
object MapLayoutEngine {

    /** Node card dimensions in map-space, mirrored by the composable. */
    const val NODE_WIDTH = 120f
    const val NODE_HEIGHT = 80f

    private const val GOLDEN_ANGLE = 2.39996f
    private const val SPIRAL_SPACING = 120f

    /**
     * Seed position for the node at [index] on a Fermat spiral, so freshly added
     * nodes fan out evenly instead of stacking at the origin.
     */
    fun seedPosition(index: Int): Pair<Float, Float> {
        val theta = index * GOLDEN_ANGLE
        val r = SPIRAL_SPACING * sqrt(index.toFloat())
        return Pair(r * cos(theta), r * sin(theta))
    }

    /**
     * Force-directed relaxation: every pair of nodes repels, and every [edges]
     * connection acts as a spring pulling its endpoints together. Runs a fixed
     * number of [iterations] and returns the settled positions. Nodes absent from
     * [positions] are ignored; edges referencing missing nodes are skipped.
     */
    fun relax(
        positions: Map<Long, Pair<Float, Float>>,
        edges: List<Pair<Long, Long>>,
        iterations: Int = 60,
        repulsion: Float = 90_000f,
        springLength: Float = 200f,
        springStrength: Float = 0.02f,
        maxStep: Float = 40f
    ): Map<Long, Pair<Float, Float>> {
        if (positions.size < 2) return positions

        val ids = positions.keys.toList()
        val pos = positions.mapValues { floatArrayOf(it.value.first, it.value.second) }.toMutableMap()
        val validEdges = edges.filter { pos.containsKey(it.first) && pos.containsKey(it.second) }

        repeat(iterations) {
            val disp = ids.associateWith { floatArrayOf(0f, 0f) }

            // Pairwise repulsion.
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val a = pos.getValue(ids[i])
                    val b = pos.getValue(ids[j])
                    var dx = a[0] - b[0]
                    var dy = a[1] - b[1]
                    var distSq = dx * dx + dy * dy
                    if (distSq < 0.01f) {
                        // Coincident nodes: nudge apart deterministically to avoid NaN.
                        dx = (i - j).toFloat()
                        dy = (i + j).toFloat().coerceAtLeast(1f)
                        distSq = dx * dx + dy * dy
                    }
                    val force = repulsion / distSq
                    val dist = sqrt(distSq)
                    val fx = dx / dist * force
                    val fy = dy / dist * force
                    disp.getValue(ids[i])[0] += fx
                    disp.getValue(ids[i])[1] += fy
                    disp.getValue(ids[j])[0] -= fx
                    disp.getValue(ids[j])[1] -= fy
                }
            }

            // Spring attraction along edges.
            for ((from, to) in validEdges) {
                val a = pos.getValue(from)
                val b = pos.getValue(to)
                val dx = b[0] - a[0]
                val dy = b[1] - a[1]
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val force = (dist - springLength) * springStrength
                val fx = dx / dist * force
                val fy = dy / dist * force
                disp.getValue(from)[0] += fx
                disp.getValue(from)[1] += fy
                disp.getValue(to)[0] -= fx
                disp.getValue(to)[1] -= fy
            }

            // Apply capped displacement.
            for (id in ids) {
                val d = disp.getValue(id)
                val step = sqrt(d[0] * d[0] + d[1] * d[1])
                if (step > 0.0001f) {
                    val capped = step.coerceAtMost(maxStep)
                    val p = pos.getValue(id)
                    p[0] += d[0] / step * capped
                    p[1] += d[1] / step * capped
                }
            }
        }

        return ids.associateWith { pos.getValue(it).let { p -> Pair(p[0], p[1]) } }
    }

    /** Axis-aligned bounds of the node cards, or null if there are no nodes. */
    fun boundingBox(positions: Map<Long, Pair<Float, Float>>): Bounds? {
        if (positions.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((_, p) in positions) {
            minX = minOf(minX, p.first)
            minY = minOf(minY, p.second)
            maxX = maxOf(maxX, p.first + NODE_WIDTH)
            maxY = maxOf(maxY, p.second + NODE_HEIGHT)
        }
        return Bounds(minX, minY, maxX, maxY)
    }

    data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val width: Float get() = maxX - minX
        val height: Float get() = maxY - minY
        val centerX: Float get() = (minX + maxX) / 2f
        val centerY: Float get() = (minY + maxY) / 2f
    }
}
