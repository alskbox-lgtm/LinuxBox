package com.linuxbox.android.ui.distros

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linuxbox.android.BuildConfig
import com.linuxbox.android.core.DistroInstallState
import com.linuxbox.android.core.SetupProgress
import com.linuxbox.android.ui.LinuxBoxViewModel
import com.linuxbox.android.ui.theme.LinuxBoxColors
import java.io.File

@Composable
fun SystemsScreen(
    viewModel: LinuxBoxViewModel,
    modifier: Modifier = Modifier
) {
    val distroStates by viewModel.distroStates.collectAsState()
    val distroTask by viewModel.distroTaskProgress.collectAsState()
    val distroError by viewModel.distroError.collectAsState()
    val mountStorage by viewModel.mountAndroidStorage.collectAsState()
    val disableSeccomp by viewModel.disableSeccomp.collectAsState()

    val nativeDir = viewModel.distroManager.nativeLibDir
    val prootOk = File(nativeDir, "libproot.so").exists()
    val loaderOk = File(nativeDir, "libproot-loader.so").exists()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LinuxBoxColors.Background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Sistemas Linux (Distros)",
                    color = LinuxBoxColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Instale e alterne entre múltiplas distribuições Linux reais direto no celular, sem root e sem computador.",
                    color = LinuxBoxColors.TextSecondary,
                    fontSize = 12.5.sp
                )
            }
        }

        if (distroError != null) {
            item {
                Surface(
                    color = LinuxBoxColors.Danger.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, LinuxBoxColors.Danger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = distroError ?: "",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.clearDistroError() }) {
                            Text("OK", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        items(distroStates, key = { it.distro.id }) { state ->
            val progressForThis = if (distroTask?.first == state.distro.id) distroTask?.second else null
            DistroCard(
                state = state,
                activeProgress = progressForThis,
                anyTaskRunning = distroTask != null,
                onInstall = { viewModel.installOrReinstallDistro(state.distro) },
                onActivate = { viewModel.activateDistro(state.distro) },
                onRemove = { viewModel.removeDistro(state.distro) }
            )
        }

        // Engine Settings Card
        item {
            Surface(
                color = LinuxBoxColors.Surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LinuxBoxColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Configurações do Motor Linux (PRoot)",
                        color = LinuxBoxColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Compartilhar arquivos do Android (/mnt/android)",
                                color = LinuxBoxColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Acessa o armazenamento do celular dentro do Linux em /mnt/android (novas abas).",
                                color = LinuxBoxColors.TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = mountStorage,
                            onCheckedChange = { viewModel.setMountAndroidStorage(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Modo compatibilidade (Desativar Seccomp)",
                                color = LinuxBoxColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Ative apenas se algum programa travar em kernels Android mais antigos.",
                                color = LinuxBoxColors.TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Switch(
                            checked = disableSeccomp,
                            onCheckedChange = { viewModel.setDisableSeccomp(it) }
                        )
                    }
                }
            }
        }

        // Native Diagnostics Card
        item {
            Surface(
                color = LinuxBoxColors.Surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LinuxBoxColors.Border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Diagnóstico do Ambiente Nativo",
                        color = LinuxBoxColors.TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    DiagLine("Arquitetura (ABI)", viewModel.distroManager.deviceAbi)
                    DiagLine("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    DiagLine("Motor PRoot nativo", if (prootOk) "OK (libproot.so)" else "Ausente")
                    DiagLine("Carregador ELF", if (loaderOk) "OK (libproot-loader.so)" else "Ausente")
                    DiagLine("Kernel host", System.getProperty("os.version") ?: "Linux")
                    DiagLine("Diretório RootFS", viewModel.distroManager.distrosBaseDir.absolutePath)
                }
            }
        }
    }
}

@Composable
private fun DistroCard(
    state: DistroInstallState,
    activeProgress: SetupProgress?,
    anyTaskRunning: Boolean,
    onInstall: () -> Unit,
    onActivate: () -> Unit,
    onRemove: () -> Unit
) {
    val accent = Color(state.distro.accentHex)

    Surface(
        color = LinuxBoxColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (state.isActive) LinuxBoxColors.Primary else LinuxBoxColors.Border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${state.distro.name} ${state.distro.version}",
                            color = LinuxBoxColors.TextPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.distro.isBundled) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = LinuxBoxColors.Secondary.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "OFFLINE",
                                    color = LinuxBoxColors.Secondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${state.distro.packageManager.displayName} • ${
                            if (state.isInstalled) formatBytes(state.diskUsageBytes) else state.distro.downloadSizeLabel
                        }",
                        color = LinuxBoxColors.TextSecondary,
                        fontSize = 11.5.sp
                    )
                }

                if (state.isActive) {
                    Surface(
                        color = LinuxBoxColors.PrimaryDim.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = LinuxBoxColors.Primary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ativo", color = LinuxBoxColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = state.distro.tagline,
                color = LinuxBoxColors.TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            if (activeProgress != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${activeProgress.stage} — ${activeProgress.detail}",
                    color = LinuxBoxColors.Secondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(6.dp))
                if (activeProgress.fraction != null) {
                    LinearProgressIndicator(
                        progress = { activeProgress.fraction.coerceIn(0f, 1f) },
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
            } else {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!state.supportedOnDevice) {
                        Text(
                            "Indisponível para arquitetura deste dispositivo",
                            color = LinuxBoxColors.Warning,
                            fontSize = 11.5.sp
                        )
                    } else if (state.isInstalled) {
                        if (!state.isActive) {
                            Button(
                                onClick = onActivate,
                                enabled = !anyTaskRunning,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Ativar", color = Color(0xFF041309), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = onInstall,
                            enabled = !anyTaskRunning,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resetar", fontSize = 12.sp)
                        }
                        if (state.distro.id != "alpine") {
                            OutlinedButton(
                                onClick = onRemove,
                                enabled = !anyTaskRunning,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, LinuxBoxColors.Danger.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, null, tint = LinuxBoxColors.Danger, modifier = Modifier.size(15.dp))
                            }
                        }
                    } else {
                        Button(
                            onClick = onInstall,
                            enabled = !anyTaskRunning,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LinuxBoxColors.Secondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF041520), modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Baixar e Instalar (${state.distro.downloadSizeLabel})",
                                color = Color(0xFF041520),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = LinuxBoxColors.TextSecondary, fontSize = 12.sp)
        Text(
            value,
            color = LinuxBoxColors.TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb.coerceAtLeast(1.0))
    }
}
