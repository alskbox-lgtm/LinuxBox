package com.linuxbox.android.terminal

/**
 * Standard ANSI 16-color, xterm 256-color, and 24-bit RGB palette tuned for
 * LinuxBox's high-contrast obsidian dark terminal aesthetic.
 */
object TerminalColors {
    const val DEFAULT_BG: Int = 0xFF0B0F14.toInt()
    const val DEFAULT_FG: Int = 0xFFE6EDF3.toInt()
    const val CURSOR_COLOR: Int = 0xFF22C55E.toInt()

    private val palette256: IntArray = IntArray(256).apply {
        // 0..7 standard ANSI
        this[0] = 0xFF161B22.toInt() // Black
        this[1] = 0xFFFF6B6B.toInt() // Red
        this[2] = 0xFF22C55E.toInt() // Green
        this[3] = 0xFFF59E0B.toInt() // Yellow
        this[4] = 0xFF3B82F6.toInt() // Blue
        this[5] = 0xFFC084FC.toInt() // Magenta
        this[6] = 0xFF38BDF8.toInt() // Cyan
        this[7] = 0xFFD0D7DE.toInt() // White

        // 8..15 bright ANSI
        this[8] = 0xFF6E7681.toInt()  // Bright Black
        this[9] = 0xFFFF8787.toInt()  // Bright Red
        this[10] = 0xFF4ADE80.toInt() // Bright Green
        this[11] = 0xFFFCD34D.toInt() // Bright Yellow
        this[12] = 0xFF60A5FA.toInt() // Bright Blue
        this[13] = 0xFFD8B4FE.toInt() // Bright Magenta
        this[14] = 0xFF67E8F9.toInt() // Bright Cyan
        this[15] = 0xFFFFFFFF.toInt() // Bright White

        // 16..231: 6x6x6 RGB color cube
        val levels = intArrayOf(0x00, 0x5F, 0x87, 0xAF, 0xD7, 0xFF)
        var idx = 16
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    this[idx++] = (0xFF shl 24) or (levels[r] shl 16) or (levels[g] shl 8) or levels[b]
                }
            }
        }

        // 232..255: 24-step grayscale ramp
        for (i in 0..23) {
            val v = 8 + i * 10
            this[232 + i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    fun color256(index: Int): Int {
        if (index !in 0..255) return DEFAULT_FG
        return palette256[index]
    }

    fun rgb(r: Int, g: Int, b: Int): Int {
        val rc = r.coerceIn(0, 255)
        val gc = g.coerceIn(0, 255)
        val bc = b.coerceIn(0, 255)
        return (0xFF shl 24) or (rc shl 16) or (gc shl 8) or bc
    }
}
