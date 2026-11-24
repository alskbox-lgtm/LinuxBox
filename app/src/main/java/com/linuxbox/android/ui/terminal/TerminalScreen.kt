package com.linuxbox.android.ui.terminal

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxbox.android.terminal.TerminalMode
import com.linuxbox.android.ui.LinuxBoxViewModel
import com.linuxbox.android.ui.MainTab
import com.linuxbox.android.ui.theme.LinuxBoxColors

@Composable
fun TerminalScreen(
    viewModel: LinuxBoxViewModel,
    modifier: Modifier = Modifier
) {
    val allSessions by viewModel.sessionManager.sessions.collectAsState()
    val selectedMode by viewModel.sessionManager.selectedMode.collectAsState()
    val activeId by viewModel.sessionManager.activeSessionId.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    val activeDistro by viewModel.activeDistro.collectAsState()
    val isDesktopRunning by viewModel.desktopController.isServerRunning.collectAsState()

    val modeSessions = allSessions.filter { it.mode == selectedMode }
    val activeSession = modeSessions.firstOrNull { it.id == activeId } ?: modeSessions.lastOrNull()

    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LinuxBoxColors.Background)
    ) {
        // Terminal Bifurcation Switcher: Separate CLI vs Graphical UI (X11)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LinuxBoxColors.Surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalBranchButton(
                label = "Terminal Separado",
                count = allSessions.count { it.mode == TerminalMode.STANDALONE },
                selected = selectedMode == TerminalMode.STANDALONE,
                accentColor = LinuxBoxColors.Primary,
                icon = Icons.Default.Terminal,
                onClick = { viewModel.selectTerminalMode(TerminalMode.STANDALONE) },
                modifier = Modifier.weight(1f)
            )
            TerminalBranchButton(
                label = "Terminal UI Gráfica",
                count = allSessions.count { it.mode == TerminalMode.DESKTOP_UI },
                selected = selectedMode == TerminalMode.DESKTOP_UI,
                accentColor = LinuxBoxColors.Secondary,
                icon = Icons.Default.Computer,
                onClick = { viewModel.selectTerminalMode(TerminalMode.DESKTOP_UI) },
                modifier = Modifier.weight(1f)
            )
        }

        // Optional status strip when on the Graphical UI branch
        if (selectedMode == TerminalMode.DESKTOP_UI) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LinuxBoxColors.Secondary.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isDesktopRunning) {
                        "Conectado ao Desktop X11 (DISPLAY=127.0.0.1:10)"
                    } else {
                        "Modo UI Gráfica • Desktop X11 parado"
                    },
                    color = LinuxBoxColors.Secondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        if (!isDesktopRunning) {
                            viewModel.startDesktop("1024x600")
                        }
                        viewModel.selectTab(MainTab.DESKTOP)
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, LinuxBoxColors.Secondary.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = if (isDesktopRunning) "Ver Desktop" else "Iniciar Desktop",
                        color = LinuxBoxColors.Secondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        HorizontalDivider(color = LinuxBoxColors.Border, thickness = 1.dp)

        // Session tab bar for the selected branch + quick terminal controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LinuxBoxColors.Surface)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activeAccent = if (selectedMode == TerminalMode.DESKTOP_UI) {
                    LinuxBoxColors.Secondary
                } else {
                    LinuxBoxColors.Primary
                }

                modeSessions.forEachIndexed { idx, session ->
                    val isSelected = session.id == activeSession?.id
                    val isRunning by session.isRunning.collectAsState()
                    val tabTitle by session.title.collectAsState()

                    Surface(
                        color = if (isSelected) LinuxBoxColors.SurfaceElevated else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) activeAccent else LinuxBoxColors.Border
                        ),
                        modifier = Modifier.clickable {
                            viewModel.sessionManager.selectSession(session.id)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) activeAccent else LinuxBoxColors.Danger)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${idx + 1}: ${tabTitle.take(15)}",
                                color = if (isSelected) LinuxBoxColors.TextPrimary else LinuxBoxColors.TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                            if (modeSessions.size > 1) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar aba",
                                    tint = LinuxBoxColors.TextSecondary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.closeTerminalSession(session.id) }
                                )
                            }
                        }
                    }
                }

                // New session tab in current branch
                Surface(
                    color = activeAccent.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { viewModel.createNewTerminalSession(mode = selectedMode) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nova sessão",
                        tint = activeAccent,
                        modifier = Modifier.padding(5.dp).size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = { viewModel.adjustFontSize(-1.5f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ZoomOut, "Diminuir fonte", tint = LinuxBoxColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { viewModel.adjustFontSize(1.5f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ZoomIn, "Aumentar fonte", tint = LinuxBoxColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    activeSession?.let {
                        clipboard.setText(AnnotatedString(it.emulator.getScreenPlainText()))
                        Toast.makeText(context, "Tela do terminal copiada", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, "Copiar tela", tint = LinuxBoxColors.TextSecondary, modifier = Modifier.size(17.dp))
            }
            IconButton(
                onClick = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Keyboard, "Abrir teclado", tint = LinuxBoxColors.Primary, modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = LinuxBoxColors.Border, thickness = 1.dp)

        if (activeSession == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Nenhuma sessão em ${selectedMode.label} (${activeDistro.name})",
                        color = LinuxBoxColors.TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.createNewTerminalSession(mode = selectedMode) }) {
                        Text("Abrir ${selectedMode.label}")
                    }
                }
            }
        } else {
            TerminalCanvas(
                session = activeSession,
                fontSizeSp = fontSizeSp,
                ctrlArmed = ctrlArmed,
                altArmed = altArmed,
                onConsumeModifiers = {
                    ctrlArmed = false
                    altArmed = false
                },
                focusRequester = focusRequester,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            HorizontalDivider(color = LinuxBoxColors.Border, thickness = 1.dp)

            ExtraKeysBar(
                ctrlArmed = ctrlArmed,
                altArmed = altArmed,
                onToggleCtrl = { ctrlArmed = !ctrlArmed },
                onToggleAlt = { altArmed = !altArmed },
                onSendSequence = { seq -> activeSession.sendText(seq) },
                onArrow = { dir -> activeSession.sendArrow(dir) },
                onInterrupt = { activeSession.sendInterrupt() },
                onPaste = {
                    val text = clipboard.getText()?.text
                    if (!text.isNullOrEmpty()) {
                        activeSession.sendText(text)
                    }
                }
            )
        }
    }
}

@Composable
private fun TerminalBranchButton(
    label: String,
    count: Int,
    selected: Boolean,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) accentColor.copy(alpha = 0.18f) else LinuxBoxColors.Background,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, if (selected) accentColor else LinuxBoxColors.Border),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentColor else LinuxBoxColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (count > 0) "$label ($count)" else label,
                color = if (selected) accentColor else LinuxBoxColors.TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
