package com.example.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Shelf → color assignment for the Radial Knowledge Map.
 *
 * Base palette is Okabe–Ito: designed to stay distinguishable under the common
 * forms of color-vision deficiency (never relies on a red-vs-green distinction
 * alone). Light and dark themes get separately tuned variants so contrast holds
 * on both canvases.
 *
 * Assignment is deterministic: shelves are indexed by their position in the
 * alphabetically-sorted shelf list (the same order used for wedge layout).
 * Past 8 shelves the palette cycles with a lightness shift per cycle.
 */
object MapColors {

    // Okabe–Ito, tuned for a light canvas (yellow and grey darkened for contrast).
    private val LIGHT = listOf(
        Color(0xFFE69F00), // orange
        Color(0xFF0072B2), // blue
        Color(0xFF009E73), // bluish green
        Color(0xFFCC79A7), // reddish purple
        Color(0xFFD55E00), // vermillion
        Color(0xFF56B4E9), // sky blue
        Color(0xFFB8A000), // gold (yellow darkened for white background)
        Color(0xFF6E6E6E)  // grey (darkened for white background)
    )

    // Same hue family, lightened ~25% toward white so they carry on a dark canvas.
    private val DARK = LIGHT.map { lerp(it, Color.White, 0.25f) }

    /** Canvas + chrome colors that aren't shelf-dependent. */
    val canvasLight = Color(0xFFFDFBF7) // warm paper white
    val canvasDark = Color(0xFF121216)  // deep slate
    val hairlineLight = Color(0x1F000000)
    val hairlineDark = Color(0x2EFFFFFF)
    val wedgeFillAlphaLight = 0.06f
    val wedgeFillAlphaDark = 0.10f

    /**
     * Deterministic shelf color. [sortedShelves] must be the alphabetically
     * sorted list of all creation-shelves currently on the map (matching
     * RadialLayoutEngine's wedge order). Unknown shelves fall back to a stable
     * hash so they still get a consistent color.
     */
    fun shelfColor(shelf: String, sortedShelves: List<String>, dark: Boolean): Color {
        val palette = if (dark) DARK else LIGHT
        val position = sortedShelves.indexOf(shelf)
        val index = if (position >= 0) position else (shelf.hashCode().let { it xor (it shr 16) }).mod(palette.size)
        val base = palette[index % palette.size]
        val cycle = if (position >= 0) position / palette.size else 0
        if (cycle == 0) return base
        // Cycle 2+ shifts lightness so repeats stay tellable-apart at small sizes.
        val shift = (cycle * 0.18f).coerceAtMost(0.45f)
        return if (dark) lerp(base, Color.Black, shift) else lerp(base, Color.White, shift)
    }
}
