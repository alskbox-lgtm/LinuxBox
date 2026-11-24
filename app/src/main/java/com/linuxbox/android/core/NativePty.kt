package com.linuxbox.android.core

import android.os.ParcelFileDescriptor

/**
 * JNI bridge to native liblinuxbox_pty.so for spawning real Linux pseudo-terminal (PTY)
 * processes so interactive tools (vim, nano, htop, tmux, apk, apt, bash/ash) work faithfully.
 */
object NativePty {
    const val SIGHUP = 1
    const val SIGINT = 2
    const val SIGQUIT = 3
    const val SIGKILL = 9
    const val SIGTERM = 15

    init {
        System.loadLibrary("linuxbox_pty")
    }

    data class PtyProcess(
        val masterFd: Int,
        val pid: Int,
        val pfd: ParcelFileDescriptor
    )

    fun start(
        command: String,
        cwd: String,
        args: List<String>,
        env: Map<String, String>,
        rows: Int,
        cols: Int
    ): PtyProcess {
        val envArray = env.entries.map { "${it.key}=${it.value}" }.toTypedArray()
        val pair = createSubprocess(
            command,
            cwd,
            args.toTypedArray(),
            envArray,
            rows.coerceAtLeast(8),
            cols.coerceAtLeast(20)
        ) ?: throw IllegalStateException("Falha ao criar PTY nativo para $command")

        val fd = pair[0]
        val pid = pair[1]
        val pfd = ParcelFileDescriptor.adoptFd(fd)
        return PtyProcess(masterFd = fd, pid = pid, pfd = pfd)
    }

    external fun createSubprocess(
        cmd: String,
        cwd: String?,
        args: Array<String>,
        env: Array<String>,
        rows: Int,
        cols: Int
    ): IntArray?

    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int)

    external fun waitFor(pid: Int): Int

    external fun sendSignal(pid: Int, signal: Int)

    external fun closeFd(fd: Int)
}
