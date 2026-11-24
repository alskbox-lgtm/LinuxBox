package com.linuxbox.android.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxbox.android.terminal.TerminalColors
import com.linuxbox.android.terminal.TerminalEmulator
import com.linuxbox.android.terminal.TerminalSession
import com.linuxbox.android.ui.theme.LinuxBoxColors
import kotlinx.coroutines.delay

private const val IME_SENTINEL = "\u200B"

@Composable
fun TerminalCanvas(
    session: TerminalSession,
    fontSizeSp: Float,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onConsumeModifiers: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val revision by session.revision.collectAsState()

    var scrollOffset by remember(session.id) { mutableIntStateOf(0) }
    var dragAccumulator by remember(session.id) { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cursorBlinkOn by remember { mutableStateOf(true) }

    LaunchedEffect(session.id) {
        while (true) {
            delay(550)
            cursorBlinkOn = !cursorBlinkOn
        }
    }

    val textSizePx = with(density) { fontSizeSp.sp.toPx() }
    val normalPaint = remember(textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = textSizePx
            isSubpixelText = true
        }
    }
    val boldPaint = remember(textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = textSizePx
            isSubpixelText = true
        }
    }
    val italicPaint = remember(textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
            textSize = textSizePx
            isSubpixelText = true
        }
    }
    val bgPaint = remember {
        Paint().apply { style = Paint.Style.FILL }
    }
    val linePaint = remember {
        Paint().apply { strokeWidth = 2f }
    }

    val metrics = remember(textSizePx) { normalPaint.fontMetrics }
    val cellWidth = remember(textSizePx) { normalPaint.measureText("W").coerceAtLeast(8f) }
    val cellHeight = remember(textSizePx) {
        (metrics.descent - metrics.ascent + metrics.leading + 2f).coerceAtLeast(14f)
    }
    val baselineOffset = remember(textSizePx) { -metrics.ascent + 1f }
    val padPx = with(density) { 6.dp.toPx() }

    // Resize PTY automatically whenever viewport or font size changes
    LaunchedEffect(canvasSize, cellWidth, cellHeight, session.id) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val cols = ((canvasSize.width - padPx * 2f) / cellWidth).toInt().coerceIn(20, 240)
            val rows = ((canvasSize.height - padPx * 2f) / cellHeight).toInt().coerceIn(6, 120)
            session.resize(rows, cols)
        }
    }

    val snapshot = remember(revision, scrollOffset, session.id) {
        session.emulator.captureSnapshot(scrollOffset)
    }

    // Clamp scroll offset if alt-screen opened
    LaunchedEffect(snapshot.maxScrollOffset) {
        if (scrollOffset > snapshot.maxScrollOffset) {
            scrollOffset = snapshot.maxScrollOffset
        }
    }

    var imeValue by remember {
        mutableStateOf(TextFieldValue(IME_SENTINEL, TextRange(IME_SENTINEL.length)))
    }

    fun dispatchTypedText(text: String) {
        if (text.isEmpty()) return
        scrollOffset = 0
        var consumed = false
        for (ch in text) {
            when {
                ch == '\n' -> session.sendText("\r")
                ctrlArmed -> {
                    session.sendControlChar(ch)
                    consumed = true
                }
                altArmed -> {
                    session.sendText("\u001B$ch")
                    consumed = true
                }
                else -> session.sendText(ch.toString())
            }
        }
        if (consumed) onConsumeModifiers()
    }

    Box(
        modifier = modifier
            .background(Color(TerminalColors.DEFAULT_BG))
            .onSizeChanged { canvasSize = it }
            .pointerInput(session.id) {
                detectTapGestures(
                    onTap = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                )
            }
            .pointerInput(session.id, cellHeight) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        val lineStep = cellHeight.coerceAtLeast(12f)
                        val deltaLines = (dragAccumulator / lineStep).toInt()
                        if (deltaLines != 0) {
                            dragAccumulator -= deltaLines * lineStep
                            val maxOff = session.emulator.captureSnapshot(0).maxScrollOffset
                            scrollOffset = (scrollOffset + deltaLines).coerceIn(0, maxOff)
                        }
                    }
                )
            }
    ) {
        // Hidden IME + hardware keyboard input bridge
        BasicTextField(
            value = imeValue,
            onValueChange = { next ->
                val raw = next.text
                when {
                    raw.isEmpty() -> {
                        scrollOffset = 0
                        session.sendRawBytes(byteArrayOf(0x7F)) // Backspace / DEL
                    }
                    raw != IME_SENTINEL -> {
                        val inserted = raw.replace(IME_SENTINEL, "")
                        if (inserted.isNotEmpty()) {
                            dispatchTypedText(inserted)
                        }
                    }
                }
                imeValue = TextFieldValue(IME_SENTINEL, TextRange(IME_SENTINEL.length))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    scrollOffset = 0
                    session.sendText("\r")
                }
            ),
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            scrollOffset = 0
                            session.sendText("\r")
                            true
                        }
                        Key.Backspace -> {
                            scrollOffset = 0
                            session.sendRawBytes(byteArrayOf(0x7F))
                            true
                        }
                        Key.Tab -> {
                            scrollOffset = 0
                            session.sendText("\t")
                            true
                        }
                        Key.Escape -> {
                            session.sendText("\u001B")
                            true
                        }
                        Key.DirectionUp -> {
                            session.sendArrow('A')
                            true
                        }
                        Key.DirectionDown -> {
                            session.sendArrow('B')
                            true
                        }
                        Key.DirectionRight -> {
                            session.sendArrow('C')
                            true
                        }
                        Key.DirectionLeft -> {
                            session.sendArrow('D')
                            true
                        }
                        else -> {
                            val code = keyEvent.nativeKeyEvent.unicodeChar
                            if (keyEvent.isCtrlPressed && code in 1..127) {
                                session.sendControlChar(code.toChar())
                                true
                            } else if (keyEvent.isAltPressed && code in 32..126) {
                                session.sendText("\u001B${code.toChar()}")
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
        )

        // High-FPS native canvas terminal renderer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.drawColor(TerminalColors.DEFAULT_BG)

            val visRows = snapshot.visibleRows
            val rowCount = visRows.size
            val colCount = snapshot.cols

            for (r in 0 until rowCount) {
                val row = visRows[r]
                val topY = padPx + r * cellHeight
                val bottomY = topY + cellHeight
                val baseY = topY + baselineOffset

                val maxC = minOf(colCount, row.chars.size)
                for (c in 0 until maxC) {
                    val leftX = padPx + c * cellWidth
                    val style = row.style[c]
                    val inverse = (style and TerminalEmulator.STYLE_INVERSE) != 0

                    var fgColor = if (inverse) row.bg[c] else row.fg[c]
                    val bgColor = if (inverse) row.fg[c] else row.bg[c]

                    if (bgColor != TerminalColors.DEFAULT_BG) {
                        bgPaint.color = bgColor
                        canvas.drawRect(leftX, topY, leftX + cellWidth + 0.6f, bottomY, bgPaint)
                    }

                    val ch = row.chars[c]
                    if (ch != ' ' && ch != '\u0000') {
                        if ((style and TerminalEmulator.STYLE_DIM) != 0) {
                            fgColor = (fgColor and 0x00FFFFFF) or (0x99 shl 24)
                        }
                        val paint = when {
                            (style and TerminalEmulator.STYLE_BOLD) != 0 -> boldPaint
                            (style and TerminalEmulator.STYLE_ITALIC) != 0 -> italicPaint
                            else -> normalPaint
                        }
                        paint.color = fgColor
                        canvas.drawText(row.chars, c, 1, leftX, baseY, paint)

                        if ((style and TerminalEmulator.STYLE_UNDERLINE) != 0) {
                            linePaint.color = fgColor
                            canvas.drawLine(leftX, bottomY - 2f, leftX + cellWidth, bottomY - 2f, linePaint)
                        }
                        if ((style and TerminalEmulator.STYLE_STRIKE) != 0) {
                            linePaint.color = fgColor
                            val midY = topY + cellHeight * 0.55f
                            canvas.drawLine(leftX, midY, leftX + cellWidth, midY, linePaint)
                        }
                    }
                }
            }

            // Crisp emerald cursor block
            if (snapshot.cursorVisible && cursorBlinkOn &&
                snapshot.cursorRow in 0 until rowCount &&
                snapshot.cursorCol in 0 until colCount
            ) {
                val cx = padPx + snapshot.cursorCol * cellWidth
                val cy = padPx + snapshot.cursorRow * cellHeight
                bgPaint.color = (TerminalColors.CURSOR_COLOR and 0x00FFFFFF) or (0xCC shl 24)
                canvas.drawRect(cx, cy, cx + cellWidth, cy + cellHeight, bgPaint)
            }
        }

        // Scrollback indicator pill when scrolled up
        if (scrollOffset > 0) {
            Surface(
                color = LinuxBoxColors.SurfaceElevated.copy(alpha = 0.94f),
                shape = RoundedCornerShape(20.dp),
                onClick = { scrollOffset = 0 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = "↑ $scrollOffset linhas • Voltar ao fim",
                    color = LinuxBoxColors.Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
