package com.linuxbox.android.ui.desktop

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linuxbox.android.desktop.RfbClient
import com.linuxbox.android.ui.LinuxBoxViewModel
import com.linuxbox.android.ui.theme.LinuxBoxColors
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DesktopScreen(
    viewModel: LinuxBoxViewModel,
    modifier: Modifier = Modifier
) {
    val controller = viewModel.desktopController
    val isInstalled by controller.isDesktopInstalled.collectAsState()
    val isRunning by controller.isServerRunning.collectAsState()
    val statusLog by controller.statusLog.collectAsState()
    val desktopLogs by controller.desktopLogs.collectAsState()
    val rfbState by controller.rfbClient.state.collectAsState()
    val frameRev by controller.rfbClient.frameRevision.collectAsState()
    val activeDistro by viewModel.activeDistro.collectAsState()

    val resolutions = listOf("800x480", "1024x600", "1280x720")
    var selectedRes by remember { mutableStateOf("1024x600") }
    var holdLeftButton by remember { mutableStateOf(false) }
    var showKeyInput by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var textToSend by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LinuxBoxColors.Background)
    ) {
        // Control Header
        Surface(
            color = LinuxBoxColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Desktop Linux Gráfico (X11 + VNC)",
                            color = LinuxBoxColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusLog,
                            color = if (statusLog.contains("Erro", ignoreCase = true) ||
                                statusLog.contains("não respondeu", ignoreCase = true)
                            ) LinuxBoxColors.Warning else LinuxBoxColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 3
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    if (isInstalled) {
                        if (isRunning) {
                            Button(
                                onClick = { viewModel.stopDesktop() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Danger),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Parar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startDesktop(selectedRes) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF041309), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Iniciar", color = Color(0xFF041309), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isInstalled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showLogsDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            border = BorderStroke(
                                1.dp,
                                if (desktopLogs.isNotEmpty()) LinuxBoxColors.Secondary else LinuxBoxColors.Border
                            )
                        ) {
                            Icon(Icons.Default.Article, null, tint = LinuxBoxColors.Secondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ver Logs (${desktopLogs.size})", color = LinuxBoxColors.Secondary, fontSize = 11.5.sp)
                        }

                        if (!isRunning) {
                            resolutions.forEach { res ->
                                FilterChip(
                                    selected = selectedRes == res,
                                    onClick = { selectedRes = res },
                                    label = { Text(res, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace) }
                                )
                            }
                        } else {
                            FilterChip(
                                selected = holdLeftButton,
                                onClick = { holdLeftButton = !holdLeftButton },
                                leadingIcon = { Icon(Icons.Default.Mouse, null, modifier = Modifier.size(15.dp)) },
                                label = { Text(if (holdLeftButton) "Segurando Clique" else "Arrastar Janela", fontSize = 11.5.sp) }
                            )
                            FilterChip(
                                selected = showKeyInput,
                                onClick = { showKeyInput = !showKeyInput },
                                leadingIcon = { Icon(Icons.Default.Keyboard, null, modifier = Modifier.size(15.dp)) },
                                label = { Text("Teclado", fontSize = 11.5.sp) }
                            )
                            OutlinedButton(
                                onClick = { controller.launchGuiApp("xterm -bg '#0B0F14' -fg '#E6EDF3'") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("+ XTerm", fontSize = 11.5.sp)
                            }
                            OutlinedButton(
                                onClick = { controller.launchGuiApp("mousepad") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Mousepad", fontSize = 11.5.sp)
                            }
                            OutlinedButton(
                                onClick = { controller.launchGuiApp("thunar") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Arquivos", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // Optional quick text input bar for sending keystrokes to X11 windows
        if (isRunning && showKeyInput) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LinuxBoxColors.SurfaceElevated)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textToSend,
                    onValueChange = { textToSend = it },
                    placeholder = { Text("Digite texto para enviar ao Desktop...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (textToSend.isNotEmpty()) {
                            controller.rfbClient.sendUnicodeText(textToSend)
                            textToSend = ""
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enviar", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { controller.rfbClient.sendUnicodeText("\n") },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("Enter", fontSize = 12.sp)
                }
            }
        }

        // Main desktop viewport or inline diagnostic log view
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (!isInstalled) {
                Surface(
                    color = LinuxBoxColors.Surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, LinuxBoxColors.Border),
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Computer,
                            contentDescription = null,
                            tint = LinuxBoxColors.Secondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Ambiente Gráfico Linux Completo",
                            color = LinuxBoxColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Instale o servidor X11 (Xvfb), o gerenciador de janelas Fluxbox e o servidor VNC local dentro do ${activeDistro.name}. Depois você poderá abrir janelas e apps gráficos Linux diretamente nesta tela, sem app externo e sem computador!",
                            color = LinuxBoxColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { viewModel.installDesktopStack() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null, tint = Color(0xFF041309))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Instalar Desktop Gráfico (~25 MB)",
                                color = Color(0xFF041309),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                when (val state = rfbState) {
                    is RfbClient.ConnectionState.Disconnected -> {
                        if (desktopLogs.isNotEmpty() && !isRunning) {
                            // Show full scrollable diagnostic log directly in the large viewport area
                            InlineDesktopLogPanel(
                                logs = desktopLogs,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(
                                    Icons.Default.Computer,
                                    null,
                                    tint = LinuxBoxColors.Primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Desktop Linux pronto para iniciar",
                                    color = LinuxBoxColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Toque em Iniciar acima para abrir o Fluxbox X11 em $selectedRes com visualizador VNC integrado.",
                                    color = LinuxBoxColors.TextSecondary,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }

                    is RfbClient.ConnectionState.Connecting -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = LinuxBoxColors.Primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Conectando ao Desktop X11 local...", color = LinuxBoxColors.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    is RfbClient.ConnectionState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Text("Conexão VNC: ${state.message}", color = LinuxBoxColors.Warning, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.startDesktop(selectedRes) }) {
                                Text("Reiniciar Desktop")
                            }
                            Spacer(Modifier.height(10.dp))
                            if (desktopLogs.isNotEmpty()) {
                                InlineDesktopLogPanel(
                                    logs = desktopLogs,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }

                    is RfbClient.ConnectionState.Connected -> {
                        VncCanvasView(
                            rfbClient = controller.rfbClient,
                            frameRevision = frameRev,
                            holdLeftButton = holdLeftButton,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showLogsDialog) {
        DesktopLogsDialog(
            logs = desktopLogs,
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
private fun InlineDesktopLogPanel(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Surface(
        color = LinuxBoxColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LinuxBoxColors.Border),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Log Completo do Servidor X11 / VNC",
                    color = LinuxBoxColors.Secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                        Toast.makeText(context, "Log copiado!", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copiar", fontSize = 11.5.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                color = LinuxBoxColors.Background,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, LinuxBoxColors.Border),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                ) {
                    items(logs) { line ->
                        Text(
                            text = line,
                            color = if (line.contains("erro", ignoreCase = true) ||
                                line.contains("fail", ignoreCase = true) ||
                                line.contains("fatal", ignoreCase = true)
                            ) Color(0xFFFCA5A5) else LinuxBoxColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopLogsDialog(
    logs: List<String>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = LinuxBoxColors.Surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, LinuxBoxColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                InlineDesktopLogPanel(
                    logs = if (logs.isEmpty()) listOf("Nenhum log registrado ainda. Toque em Iniciar primeiro.") else logs,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}

@Composable
private fun VncCanvasView(
    rfbClient: RfbClient,
    frameRevision: Long,
    holdLeftButton: Boolean,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val bitmap = remember(frameRevision) { rfbClient.copyCurrentBitmap() }
    val (fbW, fbH) = rfbClient.framebufferSize()

    var cursorX by remember(fbW, fbH) {
        mutableFloatStateOf(if (fbW > 0) fbW / 2f else 0f)
    }
    var cursorY by remember(fbW, fbH) {
        mutableFloatStateOf(if (fbH > 0) fbH / 2f else 0f)
    }

    fun currentScale(): Float {
        if (viewSize.width <= 0 || viewSize.height <= 0 || fbW <= 0 || fbH <= 0) return 1f
        return min(viewSize.width.toFloat() / fbW, viewSize.height.toFloat() / fbH)
    }

    Canvas(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
            .pointerInput(fbW, fbH) {
                detectTapGestures(
                    onTap = {
                        if (fbW <= 0 || fbH <= 0) return@detectTapGestures
                        val cx = cursorX.roundToInt().coerceIn(0, fbW - 1)
                        val cy = cursorY.roundToInt().coerceIn(0, fbH - 1)
                        rfbClient.sendPointerEvent(cx, cy, 1) // 1 clique = botão esquerdo
                        rfbClient.sendPointerEvent(cx, cy, 0)
                    },
                    onDoubleTap = {
                        if (fbW <= 0 || fbH <= 0) return@detectTapGestures
                        val cx = cursorX.roundToInt().coerceIn(0, fbW - 1)
                        val cy = cursorY.roundToInt().coerceIn(0, fbH - 1)
                        rfbClient.sendPointerEvent(cx, cy, 4) // 2 cliques = botão direito
                        rfbClient.sendPointerEvent(cx, cy, 0)
                    },
                    onLongPress = {
                        if (fbW <= 0 || fbH <= 0) return@detectTapGestures
                        val cx = cursorX.roundToInt().coerceIn(0, fbW - 1)
                        val cy = cursorY.roundToInt().coerceIn(0, fbH - 1)
                        // Segurar = duplo clique esquerdo (para abrir ícones/pastas)
                        rfbClient.sendPointerEvent(cx, cy, 1)
                        rfbClient.sendPointerEvent(cx, cy, 0)
                        rfbClient.sendPointerEvent(cx, cy, 1)
                        rfbClient.sendPointerEvent(cx, cy, 0)
                    }
                )
            }
            .pointerInput(fbW, fbH, holdLeftButton) {
                detectDragGestures(
                    onDragStart = {
                        if (fbW <= 0 || fbH <= 0) return@detectDragGestures
                        val cx = cursorX.roundToInt().coerceIn(0, fbW - 1)
                        val cy = cursorY.roundToInt().coerceIn(0, fbH - 1)
                        rfbClient.sendPointerEvent(cx, cy, if (holdLeftButton) 1 else 0)
                    },
                    onDragEnd = {
                        if (fbW <= 0 || fbH <= 0) return@detectDragGestures
                        val cx = cursorX.roundToInt().coerceIn(0, fbW - 1)
                        val cy = cursorY.roundToInt().coerceIn(0, fbH - 1)
                        rfbClient.sendPointerEvent(cx, cy, 0)
                    },
                    onDragCancel = {
                        if (fbW <= 0 || fbH <= 0) return@detectDragGestures
                        val cx = cursorX.roundToInt().coerceIn(0, fbW - 1)
                        val cy = cursorY.roundToInt().coerceIn(0, fbH - 1)
                        rfbClient.sendPointerEvent(cx, cy, 0)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (fbW <= 0 || fbH <= 0) return@detectDragGestures
                        val scale = currentScale().coerceAtLeast(0.1f)
                        val sensitivity = 1.25f
                        cursorX = (cursorX + (dragAmount.x / scale) * sensitivity)
                            .coerceIn(0f, (fbW - 1).toFloat())
                        cursorY = (cursorY + (dragAmount.y / scale) * sensitivity)
                            .coerceIn(0f, (fbH - 1).toFloat())
                        val cx = cursorX.roundToInt().coerceIn(0, fbW - 1)
                        val cy = cursorY.roundToInt().coerceIn(0, fbH - 1)
                        rfbClient.sendPointerEvent(cx, cy, if (holdLeftButton) 1 else 0)
                    }
                )
            }
    ) {
        if (bitmap != null && fbW > 0 && fbH > 0) {
            val scale = min(size.width / fbW, size.height / fbH)
            val dstW = (fbW * scale).roundToInt()
            val dstH = (fbH * scale).roundToInt()
            val offX = ((size.width - dstW) / 2f).roundToInt()
            val offY = ((size.height - dstH) / 2f).roundToInt()
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(offX, offY),
                dstSize = IntSize(dstW, dstH)
            )

            // Draw crisp virtual mouse cursor overlay at exact pointer position
            val px = offX + cursorX * scale
            val py = offY + cursorY * scale
            val pointerPath = Path().apply {
                moveTo(px, py)
                lineTo(px, py + 24f)
                lineTo(px + 6.5f, py + 18.5f)
                lineTo(px + 12f, py + 28f)
                lineTo(px + 15.5f, py + 26f)
                lineTo(px + 10f, py + 16.5f)
                lineTo(px + 17.5f, py + 16.5f)
                close()
            }
            drawPath(path = pointerPath, color = Color.White)
            drawPath(path = pointerPath, color = Color.Black, style = Stroke(width = 2.2f))
        }
    }
}
