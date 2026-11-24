package com.linuxbox.android

import com.linuxbox.android.terminal.TerminalColors
import com.linuxbox.android.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {

    @Test
    fun basicTextAndNewlinesRenderCorrectly() {
        val term = TerminalEmulator(initialRows = 10, initialCols = 40)
        term.processText("LinuxBox\r\nLinha 2")
        val snap = term.captureSnapshot()
        assertTrue(String(snap.visibleRows[0].chars).startsWith("LinuxBox"))
        assertTrue(String(snap.visibleRows[1].chars).startsWith("Linha 2"))
        assertEquals(1, snap.cursorRow)
        assertEquals(7, snap.cursorCol)
    }

    @Test
    fun utf8PortugueseSplitAcrossChunksDecodesCleanly() {
        val term = TerminalEmulator(initialRows = 5, initialCols = 40)
        val phrase = "Instalação concluída: ação, coração!"
        val bytes = phrase.toByteArray(Charsets.UTF_8)
        // Feed 1 byte at a time to stress incremental UTF-8 decoding
        for (b in bytes) {
            term.processBytes(byteArrayOf(b), 1)
        }
        val line = String(term.captureSnapshot().visibleRows[0].chars).trimEnd()
        assertEquals(phrase, line)
    }

    @Test
    fun ansiSgr256AndTrueColorAreApplied() {
        val term = TerminalEmulator(initialRows = 5, initialCols = 40)
        term.processText("\u001B[1;38;2;10;20;30;48;5;196mA\u001B[0mB")
        val row = term.captureSnapshot().visibleRows[0]
        assertEquals('A', row.chars[0])
        assertEquals(TerminalColors.rgb(10, 20, 30), row.fg[0])
        assertEquals(TerminalColors.color256(196), row.bg[0])
        assertTrue((row.style[0] and TerminalEmulator.STYLE_BOLD) != 0)

        assertEquals('B', row.chars[1])
        assertEquals(TerminalColors.DEFAULT_FG, row.fg[1])
        assertEquals(TerminalColors.DEFAULT_BG, row.bg[1])
        assertEquals(0, row.style[1])
    }

    @Test
    fun alternateScreenRestoresNormalBufferAndCursor() {
        val term = TerminalEmulator(initialRows = 6, initialCols = 30)
        term.processText("shell prompt$")
        // Enter alternate screen (vim/nano/htop)
        term.processText("\u001B[?1049h\u001B[2J\u001B[HInside VIM")
        val altSnap = term.captureSnapshot()
        assertTrue(altSnap.isAlternateScreen)
        assertTrue(String(altSnap.visibleRows[0].chars).startsWith("Inside VIM"))

        // Leave alternate screen
        term.processText("\u001B[?1049l")
        val mainSnap = term.captureSnapshot()
        assertFalse(mainSnap.isAlternateScreen)
        assertTrue(String(mainSnap.visibleRows[0].chars).startsWith("shell prompt$"))
        assertEquals(0, mainSnap.cursorRow)
        assertEquals(13, mainSnap.cursorCol)
    }

    @Test
    fun dsrCursorPositionQuerySendsExpectedReply() {
        var reply = ""
        val term = TerminalEmulator(
            initialRows = 10,
            initialCols = 40,
            onReplyToPty = { bytes -> reply = String(bytes, Charsets.US_ASCII) }
        )
        term.processText("\u001B[4;9H\u001B[6n")
        assertEquals("\u001B[4;9R", reply)
    }

    @Test
    fun scrollbackPreservesOverflowLines() {
        val term = TerminalEmulator(initialRows = 4, initialCols = 20, maxScrollback = 50)
        for (i in 1..8) {
            term.processText("Line $i\r\n")
        }
        val latest = term.captureSnapshot(0)
        assertTrue(latest.maxScrollOffset >= 4)
        val oldest = term.captureSnapshot(latest.maxScrollOffset)
        assertTrue(String(oldest.visibleRows[0].chars).startsWith("Line 1"))
    }
}
