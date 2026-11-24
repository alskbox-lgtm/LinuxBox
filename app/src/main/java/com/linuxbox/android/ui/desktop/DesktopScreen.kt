package com.linuxbox.android.ui.desktop

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val rfbState by controller.rfbClient.state.collectAsState()
    val frameRev by controller.rfbClient.frameRevision.collectAsState()
    val activeDistro by viewModel.activeDistro.collectAsState()

    val resolutions = listOf("800x480", "1024x600", "1280x720")
    var selectedRes by remember { mutableStateOf("1024x600") }
    var rightClickMode by remember { mutableStateOf(false) }
    var showKeyInput by remember { mutableStateOf(false) }
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
                            color = LinuxBoxColors.TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }

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
                                selected = rightClickMode,
                                onClick = { rightClickMode = !rightClickMode },
                                leadingIcon = { Icon(Icons.Default.Mouse, null, modifier = Modifier.size(15.dp)) },
                                label = { Text(if (rightClickMode) "Botão Direito" else "Botão Esquerdo", fontSize = 11.5.sp) }
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

        // Main desktop viewport or onboarding card
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

                    is RfbClient.ConnectionState.Connecting -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = LinuxBoxColors.Primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Conectando ao Desktop X11 local...", color = LinuxBoxColors.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    is RfbClient.ConnectionState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("Conexão VNC: ${state.message}", color = LinuxBoxColors.Warning, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.startDesktop(selectedRes) }) {
                                Text("Reiniciar Desktop")
                            }
                        }
                    }

                    is RfbClient.ConnectionState.Connected -> {
                        VncCanvasView(
                            rfbClient = controller.rfbClient,
                            frameRevision = frameRev,
                            rightClickMode = rightClickMode,
                            modifier = Modifier.fillMaxSize()
                        )
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
    rightClickMode: Boolean,
    modifier: Modifier = Modifier
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val bitmap = remember(frameRevision) { rfbClient.copyCurrentBitmap() }
    val (fbW, fbH) = rfbClient.framebufferSize()

    fun mapOffsetToFb(pos: Offset): Pair<Int, Int>? {
        if (viewSize.width <= 0 || viewSize.height <= 0 || fbW <= 0 || fbH <= 0) return null
        val scale = min(viewSize.width.toFloat() / fbW, viewSize.height.toFloat() / fbH)
        val drawW = fbW * scale
        val drawH = fbH * scale
        val offX = (viewSize.width - drawW) / 2f
        val offY = (viewSize.height - drawH) / 2f
        val fx = ((pos.x - offX) / scale).roundToInt().coerceIn(0, fbW - 1)
        val fy = ((pos.y - offY) / scale).roundToInt().coerceIn(0, fbH - 1)
        return fx to fy
    }

    val activeMask = if (rightClickMode) 4 else 1

    Canvas(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
            .pointerInput(fbW, fbH, rightClickMode) {
                detectTapGestures(
                    onTap = { offset ->
                        val (x, y) = mapOffsetToFb(offset) ?: return@detectTapGestures
                        rfbClient.sendPointerEvent(x, y, activeMask)
                        rfbClient.sendPointerEvent(x, y, 0)
                    },
                    onDoubleTap = { offset ->
                        val (x, y) = mapOffsetToFb(offset) ?: return@detectTapGestures
                        rfbClient.sendPointerEvent(x, y, 1)
                        rfbClient.sendPointerEvent(x, y, 0)
                        rfbClient.sendPointerEvent(x, y, 1)
                        rfbClient.sendPointerEvent(x, y, 0)
                    },
                    onLongPress = { offset ->
                        val (x, y) = mapOffsetToFb(offset) ?: return@detectTapGestures
                        rfbClient.sendPointerEvent(x, y, 4) // right-click context menu in Fluxbox
                        rfbClient.sendPointerEvent(x, y, 0)
                    }
                )
            }
            .pointerInput(fbW, fbH) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val (x, y) = mapOffsetToFb(offset) ?: return@detectDragGestures
                        rfbClient.sendPointerEvent(x, y, 1)
                    },
                    onDragEnd = {
                        rfbClient.sendPointerEvent(0, 0, 0)
                    },
                    onDrag = { change, _ ->
                        val (x, y) = mapOffsetToFb(change.position) ?: return@detectDragGestures
                        rfbClient.sendPointerEvent(x, y, 1)
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
        }
    }
}
