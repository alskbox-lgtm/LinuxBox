package com.linuxbox.android.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.linuxbox.android.BuildConfig
import com.linuxbox.android.core.AppUpdateInfo
import com.linuxbox.android.core.SetupProgress
import com.linuxbox.android.ui.desktop.DesktopScreen
import com.linuxbox.android.ui.distros.SystemsScreen
import com.linuxbox.android.ui.store.ProgramsScreen
import com.linuxbox.android.ui.terminal.TerminalScreen
import com.linuxbox.android.ui.theme.LinuxBoxColors

@Composable
fun LinuxBoxRoot(viewModel: LinuxBoxViewModel) {
    val isBootstrapping by viewModel.isBootstrapping.collectAsState()
    val bootstrapProgress by viewModel.bootstrapProgress.collectAsState()
    val bootstrapError by viewModel.bootstrapError.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val consoleTask by viewModel.consoleTask.collectAsState()
    val preparingInstallerName by viewModel.preparingInstallerName.collectAsState()
    val pendingInstaller by viewModel.pendingInstallerPrompt.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()

    if (isBootstrapping || bootstrapError != null) {
        BootstrapScreen(
            progress = bootstrapProgress,
            error = bootstrapError,
            onRetry = { viewModel.bootstrap() }
        )
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(LinuxBoxColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = LinuxBoxColors.Background,
        bottomBar = {
            Column {
                HorizontalDivider(color = LinuxBoxColors.Border, thickness = 1.dp)
                NavigationBar(
                    containerColor = LinuxBoxColors.Surface,
                    tonalElevation = 0.dp
                ) {
                    MainTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            MainTab.TERMINAL -> Icons.Default.Terminal
                            MainTab.PROGRAMS -> Icons.Default.Apps
                            MainTab.DESKTOP -> Icons.Default.Computer
                            MainTab.SYSTEMS -> Icons.Default.Dns
                        }
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = LinuxBoxColors.Primary,
                                selectedTextColor = LinuxBoxColors.Primary,
                                indicatorColor = LinuxBoxColors.PrimaryDim.copy(alpha = 0.45f),
                                unselectedIconColor = LinuxBoxColors.TextSecondary,
                                unselectedTextColor = LinuxBoxColors.TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.TERMINAL -> TerminalScreen(viewModel = viewModel)
                MainTab.PROGRAMS -> ProgramsScreen(viewModel = viewModel)
                MainTab.DESKTOP -> DesktopScreen(viewModel = viewModel)
                MainTab.SYSTEMS -> SystemsScreen(viewModel = viewModel)
            }
        }
    }

    consoleTask?.let { task ->
        ConsoleTaskDialog(
            task = task,
            onDismiss = { viewModel.dismissConsoleTask() },
            onLaunch = { cmd ->
                viewModel.dismissConsoleTask()
                viewModel.runCommandInTerminal(cmd)
            }
        )
    }

    preparingInstallerName?.let { fileName ->
        Dialog(onDismissRequest = {}) {
            Surface(
                color = LinuxBoxColors.Surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, LinuxBoxColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = LinuxBoxColors.Secondary
                    )
                    Column {
                        Text(
                            text = "Preparando instalador...",
                            color = LinuxBoxColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = fileName,
                            color = LinuxBoxColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    pendingInstaller?.let { prompt ->
        InstallerModeDialog(
            prompt = prompt,
            onRunInGui = { viewModel.executePendingInstallerInGui() },
            onRunInTerminal = { viewModel.executePendingInstallerInTerminal() },
            onDismiss = { viewModel.dismissPendingInstaller() }
        )
    }

    if (pendingInstaller == null && preparingInstallerName == null) {
        availableUpdate?.let { update ->
            AppUpdateDialog(
                update = update,
                downloadProgress = updateDownloadProgress,
                onDownloadAndInstall = { viewModel.downloadAndInstallUpdate() },
                onOpenGitHub = { viewModel.openUrlInBrowser(update.releaseHtmlUrl) },
                onDismiss = { viewModel.dismissUpdateDialog() }
            )
        }
    }
}

@Composable
private fun BootstrapScreen(
    progress: SetupProgress,
    error: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LinuxBoxColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = LinuxBoxColors.Surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, LinuxBoxColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = LinuxBoxColors.Primary,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "LinuxBox",
                    color = LinuxBoxColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sistema Linux autônomo no Android • Sem PC e sem Root",
                    color = LinuxBoxColors.TextSecondary,
                    fontSize = 12.5.sp
                )

                Spacer(Modifier.height(22.dp))

                if (error != null) {
                    Text(
                        text = error,
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary)
                    ) {
                        Text("Tentar Novamente", color = Color(0xFF041309), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = progress.stage,
                        color = LinuxBoxColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = progress.detail,
                        color = LinuxBoxColors.TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(14.dp))
                    if (progress.fraction != null) {
                        LinearProgressIndicator(
                            progress = { progress.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = LinuxBoxColors.Primary,
                            trackColor = LinuxBoxColors.Border
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = LinuxBoxColors.Primary,
                            trackColor = LinuxBoxColors.Border
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTaskDialog(
    task: ConsoleTaskState,
    onDismiss: () -> Unit,
    onLaunch: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(task.lines.size) {
        if (task.lines.isNotEmpty()) {
            listState.animateScrollToItem(task.lines.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = { if (!task.isRunning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = LinuxBoxColors.Surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, LinuxBoxColors.Border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when {
                        task.isRunning -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = LinuxBoxColors.Primary
                        )
                        task.exitCode == 0 -> Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = LinuxBoxColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                        else -> Icon(
                            Icons.Default.ErrorOutline,
                            null,
                            tint = LinuxBoxColors.Danger,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            color = LinuxBoxColors.TextPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                task.isRunning -> "Executando no ambiente Linux..."
                                task.exitCode == 0 -> "Concluído com sucesso!"
                                else -> "Finalizado com erro (código ${task.exitCode})"
                            },
                            color = when {
                                task.isRunning -> LinuxBoxColors.Secondary
                                task.exitCode == 0 -> LinuxBoxColors.Primary
                                else -> LinuxBoxColors.Danger
                            },
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = LinuxBoxColors.Background,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, LinuxBoxColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            items(task.lines) { line ->
                                Text(
                                    text = line,
                                    color = when {
                                        line.startsWith("$ ") -> LinuxBoxColors.Primary
                                        line.contains("ERROR", ignoreCase = true) ||
                                            line.contains("ERRO", ignoreCase = true) -> Color(0xFFFCA5A5)
                                        else -> LinuxBoxColors.TextPrimary
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(task.lines.joinToString("\n")))
                            Toast.makeText(context, "Log copiado!", Toast.LENGTH_SHORT).show()
                        },
                        border = BorderStroke(1.dp, LinuxBoxColors.Secondary.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            null,
                            tint = LinuxBoxColors.Secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Copiar Log", color = LinuxBoxColors.Secondary, fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.End) {
                        if (task.isRunning) {
                            OutlinedButton(
                                onClick = onDismiss,
                                border = BorderStroke(1.dp, LinuxBoxColors.Danger)
                            ) {
                                Text("Cancelar", color = LinuxBoxColors.Danger)
                            }
                        } else {
                            OutlinedButton(onClick = onDismiss) {
                                Text("Fechar")
                            }
                            if (task.exitCode == 0 && !task.launchAfterCommand.isNullOrBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onLaunch(task.launchAfterCommand) },
                                    colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF041309), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Abrir no Terminal", color = Color(0xFF041309), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallerModeDialog(
    prompt: PendingInstallerPrompt,
    onRunInGui: () -> Unit,
    onRunInTerminal: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = LinuxBoxColors.Surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, LinuxBoxColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Como executar o instalador?",
                    color = LinuxBoxColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = prompt.fileName,
                    color = LinuxBoxColors.Secondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp
                )
                Text(
                    text = "Sistema ativo: ${prompt.distro.name} ${prompt.distro.version}",
                    color = LinuxBoxColors.TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onRunInGui,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Computer, null, tint = Color(0xFF041520), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Rodar pela UI Gráfica (Desktop X11)",
                        color = Color(0xFF041520),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onRunInTerminal,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Terminal, null, tint = Color(0xFF041309), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Rodar apenas no Terminal (CLI)",
                        color = Color(0xFF041309),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateDialog(
    update: AppUpdateInfo,
    downloadProgress: Float?,
    onDownloadAndInstall: () -> Unit,
    onOpenGitHub: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDownloading = downloadProgress != null
    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
        Surface(
            color = LinuxBoxColors.Surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, LinuxBoxColors.Primary.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        null,
                        tint = LinuxBoxColors.Primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Atualização Disponível!",
                            color = LinuxBoxColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} → ${update.tagName}",
                            color = LinuxBoxColors.Primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                if (update.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = LinuxBoxColors.Background,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LinuxBoxColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            items(update.releaseNotes.lines()) { line ->
                                Text(
                                    text = line,
                                    color = LinuxBoxColors.TextSecondary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(Modifier.height(14.dp))
                    val pct = ((downloadProgress ?: 0f) * 100).toInt()
                    Text(
                        text = "Baixando APK do GitHub ($pct%)...",
                        color = LinuxBoxColors.Secondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (downloadProgress ?: 0f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = LinuxBoxColors.Primary,
                        trackColor = LinuxBoxColors.Border
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDownloadAndInstall,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Baixar e Instalar ${update.tagName}",
                            color = Color(0xFF041309),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = onOpenGitHub) {
                            Text("Ver no GitHub", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Text("Depois", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
