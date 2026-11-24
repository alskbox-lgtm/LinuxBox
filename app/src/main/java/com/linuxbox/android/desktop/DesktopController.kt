package com.linuxbox.android.desktop

import com.linuxbox.android.core.DistroManager
import com.linuxbox.android.core.LinuxCommandRunner
import com.linuxbox.android.core.PackageCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Manages the optional standalone Linux GUI Desktop environment (Xvfb + Fluxbox + x11vnc)
 * running completely inside PRoot and displayed via the embedded RfbClient.
 */
class DesktopController(
    private val distroManager: DistroManager,
    private val commandRunner: LinuxCommandRunner
) {
    val rfbClient = RfbClient()

    private val _isDesktopInstalled = MutableStateFlow(false)
    val isDesktopInstalled: StateFlow<Boolean> = _isDesktopInstalled.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _statusLog = MutableStateFlow("Ambiente gráfico parado.")
    val statusLog: StateFlow<String> = _statusLog.asStateFlow()

    private val _desktopLogs = MutableStateFlow<List<String>>(emptyList())
    val desktopLogs: StateFlow<List<String>> = _desktopLogs.asStateFlow()

    private var serverProcess: Process? = null
    private val logBuffer: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private fun appendLog(line: String) {
        synchronized(logBuffer) {
            logBuffer.add(line)
            if (logBuffer.size > 300) {
                logBuffer.removeAt(0)
            }
            _desktopLogs.value = logBuffer.toList()
        }
    }

    private fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _desktopLogs.value = emptyList()
        }
    }

    suspend fun refreshInstalledState() {
        val result = commandRunner.runShellCommand(
            "command -v Xvfb >/dev/null 2>&1 && command -v x11vnc >/dev/null 2>&1 && command -v fluxbox >/dev/null 2>&1 && echo OK || echo NO"
        )
        _isDesktopInstalled.value = result.output.trim().endsWith("OK")
    }

    suspend fun installDesktopStack(onLine: (String) -> Unit): Boolean {
        val distro = distroManager.getActiveDistro()
        val desktopProgram = PackageCatalog.programs.first { it.id == "desktop_fluxbox" }
        val pkgs = desktopProgram.packagesFor(distro.packageManager)
        val cmd = PackageCatalog.buildInstallCommand(distro.packageManager, pkgs)
        val res = commandRunner.runShellCommand(cmd, distro = distro, onLine = onLine)
        refreshInstalledState()
        return res.exitCode == 0 && _isDesktopInstalled.value
    }

    suspend fun startDesktop(resolution: String = "1024x600") = withContext(Dispatchers.IO) {
        stopDesktop()
        clearLogs()
        val distro = distroManager.getActiveDistro()
        val rootfs = distroManager.rootfsDir(distro.id)

        appendLog("[LinuxBox] Distro ativa: ${distro.name} ${distro.version}")
        appendLog("[LinuxBox] Iniciando Xvfb ($resolution), Fluxbox e x11vnc na porta 5901...")
        _statusLog.value = "Iniciando servidor Xvfb ($resolution), Fluxbox e VNC..."

        val launchScript = """
            echo "[script] Limpando locks antigos..."
            rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 /tmp/xvfb.log /tmp/fluxbox.log /tmp/x11vnc.log 2>/dev/null || true
            mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix
            echo "[script] Iniciando Xvfb :1 -screen 0 ${resolution}x24..."
            Xvfb :1 -screen 0 ${resolution}x24 -ac -nolisten tcp -noreset >/tmp/xvfb.log 2>&1 &
            XVFB_PID=${'$'}!
            i=0
            while [ ! -S /tmp/.X11-unix/X1 ] && [ "${'$'}i" -lt 25 ]; do
                if ! kill -0 "${'$'}XVFB_PID" 2>/dev/null; then
                    echo "[script] ERRO: Xvfb encerrou prematuramente!"
                    cat /tmp/xvfb.log 2>/dev/null || true
                    exit 1
                fi
                sleep 0.2
                i=${'$'}((i + 1))
            done
            if [ ! -S /tmp/.X11-unix/X1 ]; then
                echo "[script] ERRO: Socket /tmp/.X11-unix/X1 não apareceu após 5s."
                cat /tmp/xvfb.log 2>/dev/null || true
                exit 1
            fi
            echo "[script] Xvfb pronto! Iniciando Fluxbox..."
            export DISPLAY=:1
            xsetroot -solid '#101820' 2>/dev/null || true
            fluxbox >/tmp/fluxbox.log 2>&1 &
            sleep 0.5
            xterm -geometry 80x24+32+32 -fa 'DejaVu Sans Mono' -fs 11 -bg '#0B0F14' -fg '#E6EDF3' -title 'LinuxBox Desktop' >/dev/null 2>&1 &
            echo "[script] Iniciando x11vnc em 127.0.0.1:5901..."
            exec x11vnc -display :1 -nopw -listen 127.0.0.1 -rfbport 5901 -forever -shared -noxdamage -noshm -noxrecord 2>&1
        """.trimIndent()

        try {
            val proc = commandRunner.startBackgroundCommand(launchScript, distro)
            serverProcess = proc
            _isServerRunning.value = true

            // Drain stdout/stderr into desktopLogs so pipe never blocks and user can read full diagnostics
            thread(name = "desktop-log-drain", isDaemon = true) {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            if (line.isNotBlank()) {
                                appendLog(line)
                            }
                            line = reader.readLine()
                        }
                    }
                } catch (_: Throwable) {
                    // process closed
                }
            }

            // Wait up to 15 seconds (25 * 600ms) for 127.0.0.1:5901 to accept connections
            var connected = false
            for (attempt in 1..25) {
                delay(600)
                if (!proc.isAlive) {
                    val code = proc.exitValue()
                    appendLog("[LinuxBox] Processo encerrou com código $code")
                    collectRootfsLogs(rootfs)
                    val lastErr = logBuffer.lastOrNull { it.isNotBlank() } ?: "código $code"
                    _statusLog.value = "Erro ao iniciar X11/VNC ($lastErr) — toque em 'Ver Logs'"
                    _isServerRunning.value = false
                    return@withContext
                }
                if (canConnectLocalPort(5901)) {
                    connected = true
                    break
                }
                _statusLog.value = "Aguardando servidor VNC local ($attempt/25)..."
            }

            if (connected) {
                appendLog("[LinuxBox] Porta 127.0.0.1:5901 aberta! Conectando cliente RFB...")
                _statusLog.value = "Desktop Linux rodando em 127.0.0.1:5901 ($resolution)"
                rfbClient.connect("127.0.0.1", 5901)
            } else {
                appendLog("[LinuxBox] Timeout após 15s: porta 5901 não abriu.")
                collectRootfsLogs(rootfs)
                stopDesktop(keepStatus = true)
                _statusLog.value = "Porta VNC 5901 não respondeu — toque em 'Ver Logs' para detalhes."
            }
        } catch (t: Throwable) {
            appendLog("[LinuxBox] Exceção: ${t.message}")
            _statusLog.value = "Erro ao iniciar Desktop: ${t.message}"
            _isServerRunning.value = false
        }
    }

    private fun collectRootfsLogs(rootfs: File) {
        val logFiles = listOf("tmp/xvfb.log", "tmp/fluxbox.log", "tmp/x11vnc.log")
        for (rel in logFiles) {
            val f = File(rootfs, rel)
            if (f.exists() && f.length() > 0) {
                appendLog("--- /$rel ---")
                try {
                    f.readLines().takeLast(25).forEach { appendLog(it) }
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }

    fun launchGuiApp(command: String) {
        try {
            commandRunner.startBackgroundCommand("DISPLAY=:1 nohup $command >/dev/null 2>&1 &")
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun stopDesktop(keepStatus: Boolean = false) {
        rfbClient.disconnect()
        try {
            serverProcess?.destroyForcibly()
        } catch (_: Throwable) {
            // ignore
        }
        serverProcess = null
        _isServerRunning.value = false
        if (!keepStatus) {
            _statusLog.value = "Ambiente gráfico parado."
        }
    }

    private fun canConnectLocalPort(port: Int): Boolean {
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress("127.0.0.1", port), 400)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }
}
