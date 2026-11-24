package com.linuxbox.android.terminal

import kotlin.math.max
import kotlin.math.min

/**
 * Full-featured VT100 / ANSI / xterm-256color terminal emulator state machine.
 * Supports normal + alternate screen buffers, scrollback history, scroll regions (DECSTBM),
 * 16/256/24-bit RGB SGR attributes, UTF-8 streaming, and DSR/DA replies so interactive Linux
 * applications (vim, nano, htop, tmux, apk, apt, python3) render faithfully.
 */
class TerminalEmulator(
    initialRows: Int = 24,
    initialCols: Int = 80,
    private val maxScrollback: Int = 2000,
    private val onReplyToPty: ((ByteArray) -> Unit)? = null,
    private val onTitleChanged: ((String) -> Unit)? = null
) {
    companion object {
        const val STYLE_BOLD: Int = 1 shl 0
        const val STYLE_DIM: Int = 1 shl 1
        const val STYLE_ITALIC: Int = 1 shl 2
        const val STYLE_UNDERLINE: Int = 1 shl 3
        const val STYLE_INVERSE: Int = 1 shl 4
        const val STYLE_STRIKE: Int = 1 shl 5
    }

    class Row(cols: Int) {
        var chars = CharArray(cols) { ' ' }
        var fg = IntArray(cols) { TerminalColors.DEFAULT_FG }
        var bg = IntArray(cols) { TerminalColors.DEFAULT_BG }
        var style = IntArray(cols)

        fun clear(
            fromCol: Int = 0,
            toColExclusive: Int = chars.size,
            fillBg: Int = TerminalColors.DEFAULT_BG
        ) {
            val start = fromCol.coerceIn(0, chars.size)
            val end = toColExclusive.coerceIn(start, chars.size)
            for (c in start until end) {
                chars[c] = ' '
                fg[c] = TerminalColors.DEFAULT_FG
                bg[c] = fillBg
                style[c] = 0
            }
        }

        fun copy(): Row {
            val r = Row(chars.size)
            chars.copyInto(r.chars)
            fg.copyInto(r.fg)
            bg.copyInto(r.bg)
            style.copyInto(r.style)
            return r
        }

        fun resize(newCols: Int) {
            if (newCols == chars.size) return
            val oldLen = chars.size
            val copyLen = min(oldLen, newCols)
            chars = CharArray(newCols) { i -> if (i < copyLen) chars[i] else ' ' }
            fg = IntArray(newCols) { i -> if (i < copyLen) fg[i] else TerminalColors.DEFAULT_FG }
            bg = IntArray(newCols) { i -> if (i < copyLen) bg[i] else TerminalColors.DEFAULT_BG }
            style = IntArray(newCols) { i -> if (i < copyLen) style[i] else 0 }
        }
    }

    data class Snapshot(
        val rows: Int,
        val cols: Int,
        val visibleRows: List<Row>,
        val cursorRow: Int,
        val cursorCol: Int,
        val cursorVisible: Boolean,
        val scrollOffset: Int,
        val maxScrollOffset: Int,
        val isAlternateScreen: Boolean,
        val applicationCursorKeys: Boolean
    )

    private enum class ParserState {
        GROUND,
        ESCAPE,
        ESCAPE_INTERMEDIATE,
        CSI_ENTRY,
        OSC_STRING,
        OSC_ESC,
        DCS_IGNORE
    }

    private val lock = Any()

    var rows: Int = initialRows.coerceAtLeast(4)
        private set
    var cols: Int = initialCols.coerceAtLeast(10)
        private set

    private var mainScreen: Array<Row> = Array(rows) { Row(cols) }
    private var altScreen: Array<Row> = Array(rows) { Row(cols) }
    private var useAltScreen: Boolean = false
    private val scrollback = ArrayDeque<Row>(256)

    private var cursorRow: Int = 0
    private var cursorCol: Int = 0
    private var pendingWrap: Boolean = false

    private var savedCursorRow: Int = 0
    private var savedCursorCol: Int = 0
    private var savedFg: Int = TerminalColors.DEFAULT_FG
    private var savedBg: Int = TerminalColors.DEFAULT_BG
    private var savedStyle: Int = 0

    // Saved normal-screen cursor when entering alternate screen
    private var mainSavedRow: Int = 0
    private var mainSavedCol: Int = 0

    private var scrollTop: Int = 0
    private var scrollBottom: Int = rows - 1

    private var currentFg: Int = TerminalColors.DEFAULT_FG
    private var currentBg: Int = TerminalColors.DEFAULT_BG
    private var currentStyle: Int = 0

    private var cursorVisible: Boolean = true
    var applicationCursorKeys: Boolean = false
        private set
    var bracketedPasteMode: Boolean = false
        private set

    private var parserState = ParserState.GROUND
    private val csiBuffer = StringBuilder(64)
    private val oscBuffer = StringBuilder(128)

    // Incremental UTF-8 decoder state
    private var utf8Remaining = 0
    private var utf8CodePoint = 0

    private fun activeScreen(): Array<Row> = if (useAltScreen) altScreen else mainScreen

    fun processBytes(data: ByteArray, length: Int = data.size) {
        synchronized(lock) {
            for (i in 0 until length) {
                val b = data[i].toInt() and 0xFF
                if (utf8Remaining > 0) {
                    if ((b and 0xC0) == 0x80) {
                        utf8CodePoint = (utf8CodePoint shl 6) or (b and 0x3F)
                        utf8Remaining--
                        if (utf8Remaining == 0) {
                            processCodePoint(utf8CodePoint)
                        }
                        continue
                    } else {
                        // Malformed sequence: emit replacement and reprocess current byte
                        utf8Remaining = 0
                        processCodePoint(0xFFFD)
                    }
                }

                when {
                    b < 0x80 -> processCodePoint(b)
                    (b and 0xE0) == 0xC0 -> {
                        utf8CodePoint = b and 0x1F
                        utf8Remaining = 1
                    }
                    (b and 0xF0) == 0xE0 -> {
                        utf8CodePoint = b and 0x0F
                        utf8Remaining = 2
                    }
                    (b and 0xF8) == 0xF0 -> {
                        utf8CodePoint = b and 0x07
                        utf8Remaining = 3
                    }
                    else -> processCodePoint(0xFFFD)
                }
            }
        }
    }

    fun processText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        processBytes(bytes, bytes.size)
    }

    fun resize(newRows: Int, newCols: Int) {
        val r = newRows.coerceAtLeast(4)
        val c = newCols.coerceAtLeast(10)
        synchronized(lock) {
            if (r == rows && c == cols) return

            mainScreen = resizeGrid(mainScreen, r, c, pushToScrollback = true)
            altScreen = resizeGrid(altScreen, r, c, pushToScrollback = false)
            rows = r
            cols = c
            scrollTop = 0
            scrollBottom = rows - 1
            cursorRow = cursorRow.coerceIn(0, rows - 1)
            cursorCol = cursorCol.coerceIn(0, cols - 1)
            pendingWrap = false
        }
    }

    private fun resizeGrid(
        oldGrid: Array<Row>,
        newRows: Int,
        newCols: Int,
        pushToScrollback: Boolean
    ): Array<Row> {
        val oldRows = oldGrid.size
        oldGrid.forEach { it.resize(newCols) }
        if (newRows == oldRows) return oldGrid

        return if (newRows < oldRows) {
            val excess = (cursorRow + 1 - newRows).coerceAtLeast(0).coerceAtMost(oldRows - newRows)
            if (pushToScrollback && excess > 0) {
                for (i in 0 until excess) {
                    pushScrollback(oldGrid[i].copy())
                }
                cursorRow = (cursorRow - excess).coerceAtLeast(0)
            }
            Array(newRows) { idx -> oldGrid[excess + idx] }
        } else {
            Array(newRows) { idx ->
                if (idx < oldRows) oldGrid[idx] else Row(newCols)
            }
        }
    }

    fun captureSnapshot(requestedScrollOffset: Int = 0): Snapshot {
        synchronized(lock) {
            val screen = activeScreen()
            val maxOffset = if (useAltScreen) 0 else scrollback.size
            val offset = requestedScrollOffset.coerceIn(0, maxOffset)

            val visible = ArrayList<Row>(rows)
            if (offset == 0) {
                for (r in 0 until rows) {
                    visible.add(screen[r].copy())
                }
            } else {
                val sbSize = scrollback.size
                val startIndex = sbSize - offset
                for (r in 0 until rows) {
                    val combinedIdx = startIndex + r
                    val rowObj = if (combinedIdx < sbSize) {
                        scrollback[combinedIdx]
                    } else {
                        screen[combinedIdx - sbSize]
                    }
                    visible.add(rowObj.copy())
                }
            }

            return Snapshot(
                rows = rows,
                cols = cols,
                visibleRows = visible,
                cursorRow = cursorRow + offset,
                cursorCol = cursorCol,
                cursorVisible = cursorVisible && offset == 0,
                scrollOffset = offset,
                maxScrollOffset = maxOffset,
                isAlternateScreen = useAltScreen,
                applicationCursorKeys = applicationCursorKeys
            )
        }
    }

    fun getScreenPlainText(): String {
        synchronized(lock) {
            val screen = activeScreen()
            return buildString {
                for (r in 0 until rows) {
                    append(String(screen[r].chars).trimEnd())
                    if (r < rows - 1) append('\n')
                }
            }
        }
    }

    private fun processCodePoint(cp: Int) {
        when (parserState) {
            ParserState.GROUND -> handleGround(cp)
            ParserState.ESCAPE -> handleEscape(cp)
            ParserState.ESCAPE_INTERMEDIATE -> {
                if (cp in 0x30..0x7E) {
                    parserState = ParserState.GROUND
                }
            }
            ParserState.CSI_ENTRY -> handleCsi(cp)
            ParserState.OSC_STRING -> handleOsc(cp)
            ParserState.OSC_ESC -> {
                if (cp == '\\'.code) {
                    dispatchOsc()
                }
                parserState = ParserState.GROUND
            }
            ParserState.DCS_IGNORE -> {
                if (cp == 0x1B || cp == 0x9C) {
                    parserState = ParserState.GROUND
                }
            }
        }
    }

    private fun handleGround(cp: Int) {
        when (cp) {
            0x00, 0x07 -> Unit // NUL, BEL
            0x08 -> { // BS
                pendingWrap = false
                if (cursorCol > 0) cursorCol--
            }
            0x09 -> { // HT (tab stop every 8 columns)
                pendingWrap = false
                val nextTab = ((cursorCol / 8) + 1) * 8
                cursorCol = min(nextTab, cols - 1)
            }
            0x0A, 0x0B, 0x0C -> { // LF, VT, FF
                pendingWrap = false
                lineFeed()
            }
            0x0D -> { // CR
                pendingWrap = false
                cursorCol = 0
            }
            0x0E, 0x0F -> Unit // SO / SI charset shift
            0x1B -> { // ESC
                parserState = ParserState.ESCAPE
            }
            else -> {
                if (cp >= 0x20) {
                    putChar(cp)
                }
            }
        }
    }

    private fun handleEscape(cp: Int) {
        when (cp) {
            '['.code -> {
                csiBuffer.setLength(0)
                parserState = ParserState.CSI_ENTRY
            }
            ']'.code -> {
                oscBuffer.setLength(0)
                parserState = ParserState.OSC_STRING
            }
            'P'.code, '^'.code, '_'.code -> {
                parserState = ParserState.DCS_IGNORE
            }
            '('.code, ')'.code, '*'.code, '+'.code, '#'.code -> {
                parserState = ParserState.ESCAPE_INTERMEDIATE
            }
            '7'.code -> {
                saveCursor()
                parserState = ParserState.GROUND
            }
            '8'.code -> {
                restoreCursor()
                parserState = ParserState.GROUND
            }
            'D'.code -> { // IND
                lineFeed()
                parserState = ParserState.GROUND
            }
            'E'.code -> { // NEL
                cursorCol = 0
                lineFeed()
                parserState = ParserState.GROUND
            }
            'M'.code -> { // RI (Reverse Index)
                reverseIndex()
                parserState = ParserState.GROUND
            }
            'c'.code -> { // RIS (Full Reset)
                fullReset()
                parserState = ParserState.GROUND
            }
            '='.code, '>'.code -> { // Application/Normal keypad
                parserState = ParserState.GROUND
            }
            else -> {
                parserState = ParserState.GROUND
            }
        }
    }

    private fun handleCsi(cp: Int) {
        if (cp == 0x1B) {
            parserState = ParserState.ESCAPE
            return
        }
        if (cp in 0x40..0x7E) {
            dispatchCsi(cp.toChar(), csiBuffer.toString())
            parserState = ParserState.GROUND
        } else if (cp in 0x20..0x3F) {
            if (csiBuffer.length < 128) {
                csiBuffer.append(cp.toChar())
            }
        } else {
            // Execute embedded C0 controls inside CSI
            if (cp == 0x08 || cp == 0x0A || cp == 0x0D) {
                handleGround(cp)
            }
        }
    }

    private fun handleOsc(cp: Int) {
        when (cp) {
            0x07 -> { // BEL terminates OSC
                dispatchOsc()
                parserState = ParserState.GROUND
            }
            0x1B -> {
                parserState = ParserState.OSC_ESC
            }
            else -> {
                if (oscBuffer.length < 256 && cp >= 0x20) {
                    oscBuffer.append(cp.toChar())
                }
            }
        }
    }

    private fun dispatchOsc() {
        val raw = oscBuffer.toString()
        val sep = raw.indexOf(';')
        if (sep > 0) {
            val code = raw.substring(0, sep)
            val payload = raw.substring(sep + 1)
            if (code == "0" || code == "2") {
                onTitleChanged?.invoke(payload)
            }
        }
    }

    private fun dispatchCsi(finalChar: Char, rawParams: String) {
        val isPrivate = rawParams.startsWith("?")
        val isGreater = rawParams.startsWith(">")
        val cleaned = rawParams.trimStart('?', '>', '!', ' ')
        val params = if (cleaned.isEmpty()) {
            emptyList()
        } else {
            cleaned.split(';').map { it.toIntOrNull() ?: 0 }
        }

        fun p(index: Int, defaultVal: Int): Int {
            val v = params.getOrNull(index) ?: 0
            return if (v == 0) defaultVal else v
        }

        if (isPrivate) {
            handlePrivateMode(finalChar, params)
            return
        }
        if (isGreater) {
            if (finalChar == 'c') {
                onReplyToPty?.invoke("\u001B[>0;276;0c".toByteArray(Charsets.US_ASCII))
            }
            return
        }

        when (finalChar) {
            'A' -> { // CUU
                pendingWrap = false
                cursorRow = max(scrollTop, cursorRow - p(0, 1))
            }
            'B', 'e' -> { // CUD
                pendingWrap = false
                cursorRow = min(scrollBottom, cursorRow + p(0, 1))
            }
            'C', 'a' -> { // CUF
                pendingWrap = false
                cursorCol = min(cols - 1, cursorCol + p(0, 1))
            }
            'D' -> { // CUB
                pendingWrap = false
                cursorCol = max(0, cursorCol - p(0, 1))
            }
            'E' -> { // CNL
                pendingWrap = false
                cursorRow = min(scrollBottom, cursorRow + p(0, 1))
                cursorCol = 0
            }
            'F' -> { // CPL
                pendingWrap = false
                cursorRow = max(scrollTop, cursorRow - p(0, 1))
                cursorCol = 0
            }
            'G', '`' -> { // CHA
                pendingWrap = false
                cursorCol = (p(0, 1) - 1).coerceIn(0, cols - 1)
            }
            'H', 'f' -> { // CUP / HVP
                pendingWrap = false
                cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (p(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'd' -> { // VPA
                pendingWrap = false
                cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1)
            }
            'J' -> { // ED
                val mode = params.getOrNull(0) ?: 0
                eraseDisplay(mode)
            }
            'K' -> { // EL
                val mode = params.getOrNull(0) ?: 0
                eraseLine(mode)
            }
            'X' -> { // ECH
                val count = p(0, 1)
                activeScreen()[cursorRow].clear(cursorCol, min(cols, cursorCol + count), currentBg)
            }
            'L' -> { // IL (Insert Lines)
                insertLines(p(0, 1))
            }
            'M' -> { // DL (Delete Lines)
                deleteLines(p(0, 1))
            }
            '@' -> { // ICH (Insert Characters)
                insertChars(p(0, 1))
            }
            'P' -> { // DCH (Delete Characters)
                deleteChars(p(0, 1))
            }
            'S' -> { // SU (Scroll Up)
                scrollUpRegion(p(0, 1))
            }
            'T' -> { // SD (Scroll Down)
                scrollDownRegion(p(0, 1))
            }
            'm' -> { // SGR
                applySgr(if (params.isEmpty()) listOf(0) else params)
            }
            'r' -> { // DECSTBM
                val top = (p(0, 1) - 1).coerceIn(0, rows - 1)
                val bottom = (p(1, rows) - 1).coerceIn(0, rows - 1)
                if (top < bottom) {
                    scrollTop = top
                    scrollBottom = bottom
                } else {
                    scrollTop = 0
                    scrollBottom = rows - 1
                }
                cursorRow = 0
                cursorCol = 0
                pendingWrap = false
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
            'n' -> { // DSR
                val code = params.getOrNull(0) ?: 0
                if (code == 5) {
                    onReplyToPty?.invoke("\u001B[0n".toByteArray(Charsets.US_ASCII))
                } else if (code == 6) {
                    val reply = "\u001B[${cursorRow + 1};${cursorCol + 1}R"
                    onReplyToPty?.invoke(reply.toByteArray(Charsets.US_ASCII))
                }
            }
            'c' -> { // Primary DA
                onReplyToPty?.invoke("\u001B[?1;2c".toByteArray(Charsets.US_ASCII))
            }
        }
    }

    private fun handlePrivateMode(finalChar: Char, params: List<Int>) {
        val enable = when (finalChar) {
            'h' -> true
            'l' -> false
            else -> return
        }
        for (mode in params) {
            when (mode) {
                1 -> applicationCursorKeys = enable
                25 -> cursorVisible = enable
                47, 1047 -> switchAltScreen(enable, saveRestoreCursor = false)
                1049 -> switchAltScreen(enable, saveRestoreCursor = true)
                2004 -> bracketedPasteMode = enable
            }
        }
    }

    private fun switchAltScreen(enable: Boolean, saveRestoreCursor: Boolean) {
        if (enable && !useAltScreen) {
            if (saveRestoreCursor) {
                mainSavedRow = cursorRow
                mainSavedCol = cursorCol
            }
            useAltScreen = true
            altScreen.forEach { it.clear() }
            scrollTop = 0
            scrollBottom = rows - 1
            cursorRow = 0
            cursorCol = 0
            pendingWrap = false
        } else if (!enable && useAltScreen) {
            useAltScreen = false
            scrollTop = 0
            scrollBottom = rows - 1
            if (saveRestoreCursor) {
                cursorRow = mainSavedRow.coerceIn(0, rows - 1)
                cursorCol = mainSavedCol.coerceIn(0, cols - 1)
            }
            pendingWrap = false
        }
    }

    private fun applySgr(params: List<Int>) {
        var i = 0
        while (i < params.size) {
            val code = params[i]
            when (code) {
                0 -> {
                    currentFg = TerminalColors.DEFAULT_FG
                    currentBg = TerminalColors.DEFAULT_BG
                    currentStyle = 0
                }
                1 -> currentStyle = currentStyle or STYLE_BOLD
                2 -> currentStyle = currentStyle or STYLE_DIM
                3 -> currentStyle = currentStyle or STYLE_ITALIC
                4 -> currentStyle = currentStyle or STYLE_UNDERLINE
                7 -> currentStyle = currentStyle or STYLE_INVERSE
                9 -> currentStyle = currentStyle or STYLE_STRIKE
                22 -> currentStyle = currentStyle and (STYLE_BOLD or STYLE_DIM).inv()
                23 -> currentStyle = currentStyle and STYLE_ITALIC.inv()
                24 -> currentStyle = currentStyle and STYLE_UNDERLINE.inv()
                27 -> currentStyle = currentStyle and STYLE_INVERSE.inv()
                29 -> currentStyle = currentStyle and STYLE_STRIKE.inv()
                in 30..37 -> currentFg = TerminalColors.color256(code - 30)
                38 -> {
                    val mode = params.getOrNull(i + 1)
                    if (mode == 5 && i + 2 < params.size) {
                        currentFg = TerminalColors.color256(params[i + 2])
                        i += 2
                    } else if (mode == 2 && i + 4 < params.size) {
                        currentFg = TerminalColors.rgb(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
                39 -> currentFg = TerminalColors.DEFAULT_FG
                in 40..47 -> currentBg = TerminalColors.color256(code - 40)
                48 -> {
                    val mode = params.getOrNull(i + 1)
                    if (mode == 5 && i + 2 < params.size) {
                        currentBg = TerminalColors.color256(params[i + 2])
                        i += 2
                    } else if (mode == 2 && i + 4 < params.size) {
                        currentBg = TerminalColors.rgb(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
                49 -> currentBg = TerminalColors.DEFAULT_BG
                in 90..97 -> currentFg = TerminalColors.color256(code - 90 + 8)
                in 100..107 -> currentBg = TerminalColors.color256(code - 100 + 8)
            }
            i++
        }
    }

    private fun putChar(cp: Int) {
        if (pendingWrap) {
            pendingWrap = false
            cursorCol = 0
            lineFeed()
        }
        val ch = if (cp <= 0xFFFF) cp.toChar() else '□'
        val row = activeScreen()[cursorRow]
        val col = cursorCol.coerceIn(0, cols - 1)
        row.chars[col] = ch
        row.fg[col] = currentFg
        row.bg[col] = currentBg
        row.style[col] = currentStyle

        if (cursorCol >= cols - 1) {
            pendingWrap = true
        } else {
            cursorCol++
        }
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollUpRegion(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) {
            scrollDownRegion(1)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun scrollUpRegion(lines: Int) {
        val screen = activeScreen()
        val count = lines.coerceIn(1, scrollBottom - scrollTop + 1)
        for (step in 0 until count) {
            if (!useAltScreen && scrollTop == 0 && scrollBottom == rows - 1) {
                pushScrollback(screen[0].copy())
            }
            for (r in scrollTop until scrollBottom) {
                screen[r] = screen[r + 1]
            }
            screen[scrollBottom] = Row(cols).apply { clear(fillBg = currentBg) }
        }
    }

    private fun scrollDownRegion(lines: Int) {
        val screen = activeScreen()
        val count = lines.coerceIn(1, scrollBottom - scrollTop + 1)
        for (step in 0 until count) {
            for (r in scrollBottom downTo scrollTop + 1) {
                screen[r] = screen[r - 1]
            }
            screen[scrollTop] = Row(cols).apply { clear(fillBg = currentBg) }
        }
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val screen = activeScreen()
        val n = count.coerceIn(1, scrollBottom - cursorRow + 1)
        for (r in scrollBottom downTo cursorRow + n) {
            screen[r] = screen[r - n]
        }
        for (r in cursorRow until cursorRow + n) {
            screen[r] = Row(cols).apply { clear(fillBg = currentBg) }
        }
        cursorCol = 0
        pendingWrap = false
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val screen = activeScreen()
        val n = count.coerceIn(1, scrollBottom - cursorRow + 1)
        for (r in cursorRow..scrollBottom - n) {
            screen[r] = screen[r + n]
        }
        for (r in scrollBottom - n + 1..scrollBottom) {
            screen[r] = Row(cols).apply { clear(fillBg = currentBg) }
        }
        cursorCol = 0
        pendingWrap = false
    }

    private fun insertChars(count: Int) {
        val row = activeScreen()[cursorRow]
        val n = count.coerceIn(1, cols - cursorCol)
        for (c in cols - 1 downTo cursorCol + n) {
            row.chars[c] = row.chars[c - n]
            row.fg[c] = row.fg[c - n]
            row.bg[c] = row.bg[c - n]
            row.style[c] = row.style[c - n]
        }
        row.clear(cursorCol, cursorCol + n, currentBg)
    }

    private fun deleteChars(count: Int) {
        val row = activeScreen()[cursorRow]
        val n = count.coerceIn(1, cols - cursorCol)
        for (c in cursorCol until cols - n) {
            row.chars[c] = row.chars[c + n]
            row.fg[c] = row.fg[c + n]
            row.bg[c] = row.bg[c + n]
            row.style[c] = row.style[c + n]
        }
        row.clear(cols - n, cols, currentBg)
    }

    private fun eraseDisplay(mode: Int) {
        val screen = activeScreen()
        when (mode) {
            0 -> {
                screen[cursorRow].clear(cursorCol, cols, currentBg)
                for (r in cursorRow + 1 until rows) screen[r].clear(fillBg = currentBg)
            }
            1 -> {
                for (r in 0 until cursorRow) screen[r].clear(fillBg = currentBg)
                screen[cursorRow].clear(0, cursorCol + 1, currentBg)
            }
            2 -> {
                for (r in 0 until rows) screen[r].clear(fillBg = currentBg)
            }
            3 -> {
                scrollback.clear()
                for (r in 0 until rows) screen[r].clear(fillBg = currentBg)
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val row = activeScreen()[cursorRow]
        when (mode) {
            0 -> row.clear(cursorCol, cols, currentBg)
            1 -> row.clear(0, cursorCol + 1, currentBg)
            2 -> row.clear(0, cols, currentBg)
        }
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedFg = currentFg
        savedBg = currentBg
        savedStyle = currentStyle
    }

    private fun restoreCursor() {
        cursorRow = savedCursorRow.coerceIn(0, rows - 1)
        cursorCol = savedCursorCol.coerceIn(0, cols - 1)
        currentFg = savedFg
        currentBg = savedBg
        currentStyle = savedStyle
        pendingWrap = false
    }

    private fun fullReset() {
        useAltScreen = false
        mainScreen.forEach { it.clear() }
        altScreen.forEach { it.clear() }
        scrollback.clear()
        cursorRow = 0
        cursorCol = 0
        pendingWrap = false
        scrollTop = 0
        scrollBottom = rows - 1
        currentFg = TerminalColors.DEFAULT_FG
        currentBg = TerminalColors.DEFAULT_BG
        currentStyle = 0
        cursorVisible = true
        applicationCursorKeys = false
        bracketedPasteMode = false
    }

    private fun pushScrollback(row: Row) {
        if (maxScrollback <= 0) return
        if (scrollback.size >= maxScrollback) {
            scrollback.removeFirst()
        }
        scrollback.addLast(row)
    }
}
