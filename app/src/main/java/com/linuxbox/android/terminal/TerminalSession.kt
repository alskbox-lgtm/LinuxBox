package com.linuxbox.android.terminal

import com.linuxbox.android.core.DistroDefinition
import com.linuxbox.android.core.DistroManager
import com.linuxbox.android.core.NativePty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Represents a live interactive Linux terminal session backed by a real JNI PTY
 * executing PRoot inside the chosen Linux distribution.
 */
class TerminalSession(
    val id: String,
    val distro: DistroDefinition,
    private val distroManager: DistroManager,
    initialCommand: String? = null,
    initialRows: Int = 28,
    initialCols: Int = 80
) {
    private val _title = MutableStateFlow("${distro.name}")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private val revisionCounter = AtomicLong(0L)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val writeQueue = LinkedBlockingQueue<ByteArray>()
    private val closed = AtomicBoolean(false)

    @Volatile
    private var ptyProcess: NativePty.PtyProcess? = null

    val emulator: TerminalEmulator = TerminalEmulator(
        initialRows = initialRows,
        initialCols = initialCols,
        onReplyToPty = { bytes -> sendRawBytes(bytes) },
        onTitleChanged = { newTitle ->
            if (newTitle.isNotBlank()) {
                _title.value = newTitle.take(28)
            }
        }
    )

    init {
        startInternal(initialCommand, initialRows, initialCols)
    }

    private fun startInternal(initialCommand: String?, rows: Int, cols: Int) {
        val shell = distroManager.resolveLoginShell(distro)
        val guestCmd = if (initialCommand.isNullOrBlank()) {
            listOf(shell, "-l")
        } else {
            listOf(shell, "-l", "-c", "$initialCommand; exec $shell -l")
        }

        val spec = distroManager.buildProotSpec(
            distro = distro,
            guestCommand = guestCmd
        )

        val proc = try {
            NativePty.start(
                command = spec.executable,
                cwd = spec.workingDir,
                args = spec.args,
                env = spec.env,
                rows = rows,
                cols = cols
            )
        } catch (t: Throwable) {
            emulator.processText(
                "\u001B[1;31mErro ao iniciar sessão Linux:\u001B[0m ${t.message}\r\n"
            )
            bumpRevision()
            _isRunning.value = false
            _exitCode.value = -1
            return
        }

        ptyProcess = proc
        _isRunning.value = true

        val fd = proc.pfd.fileDescriptor

        // Reader thread: PTY master -> TerminalEmulator
        thread(name = "pty-read-$id", isDaemon = true) {
            val buf = ByteArray(8192)
            try {
                FileInputStream(fd).use { input ->
                    while (!closed.get()) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        emulator.processBytes(buf, n)
                        bumpRevision()
                    }
                }
            } catch (_: Throwable) {
                // Expected on PTY close / process exit
            } finally {
                val code = NativePty.waitFor(proc.pid)
                _exitCode.value = code
                _isRunning.value = false
                if (!closed.get()) {
                    emulator.processText(
                        "\r\n\u001B[2m[Sessão encerrada com código $code — toque em + para abrir nova aba]\u001B[0m\r\n"
                    )
                    bumpRevision()
                }
                closeInternal()
            }
        }

        // Writer thread: UI keystrokes -> PTY master
        thread(name = "pty-write-$id", isDaemon = true) {
            try {
                FileOutputStream(fd).use { output ->
                    while (!closed.get()) {
                        val chunk = writeQueue.take()
                        if (chunk.isEmpty() && closed.get()) break
                        output.write(chunk)
                        output.flush()
                    }
                }
            } catch (_: Throwable) {
                // Expected when PTY closes
            }
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty() || closed.get()) return
        sendRawBytes(text.toByteArray(Charsets.UTF_8))
    }

    fun sendRawBytes(bytes: ByteArray) {
        if (bytes.isEmpty() || closed.get()) return
        writeQueue.offer(bytes)
    }

    fun sendControlChar(ch: Char) {
        val upper = ch.uppercaseChar()
        if (upper in '@'..'_' ) {
            val code = (upper.code - '@'.code).toByte()
            sendRawBytes(byteArrayOf(code))
        }
    }

    fun sendArrow(direction: Char) {
        val prefix = if (emulator.applicationCursorKeys) "\u001BO" else "\u001B["
        sendText("$prefix$direction")
    }

    fun resize(rows: Int, cols: Int) {
        val r = rows.coerceAtLeast(4)
        val c = cols.coerceAtLeast(10)
        if (r == emulator.rows && c == emulator.cols) return
        emulator.resize(r, c)
        ptyProcess?.let { proc ->
            if (!closed.get()) {
                NativePty.setPtyWindowSize(proc.masterFd, r, c)
            }
        }
        bumpRevision()
    }

    fun sendInterrupt() {
        sendRawBytes(byteArrayOf(0x03)) // Ctrl+C
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            writeQueue.offer(ByteArray(0))
            ptyProcess?.let { proc ->
                try {
                    NativePty.sendSignal(proc.pid, NativePty.SIGHUP)
                    NativePty.sendSignal(proc.pid, NativePty.SIGTERM)
                } catch (_: Throwable) {
                    // ignore
                }
            }
            closeInternal()
        }
    }

    private fun closeInternal() {
        val proc = ptyProcess
        ptyProcess = null
        if (proc != null) {
            try {
                proc.pfd.close()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun bumpRevision() {
        _revision.value = revisionCounter.incrementAndGet()
    }
}
