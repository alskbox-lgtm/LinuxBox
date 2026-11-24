package com.linuxbox.android.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.coroutines.coroutineContext

data class CommandExecutionResult(
    val exitCode: Int,
    val output: String
)

/**
 * Executes non-interactive Linux commands inside the active PRoot distro
 * with real-time line-by-line output streaming (used by the Linux App Store,
 * live package search, and Desktop GUI launcher).
 */
class LinuxCommandRunner(private val distroManager: DistroManager) {

    suspend fun runShellCommand(
        shellCommand: String,
        distro: DistroDefinition = distroManager.getActiveDistro(),
        onLine: ((String) -> Unit)? = null
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val shell = distroManager.resolveLoginShell(distro)
        val spec = distroManager.buildProotSpec(
            distro = distro,
            guestCommand = listOf(shell, "-c", shellCommand)
        )

        val pb = ProcessBuilder(spec.args)
        pb.directory(File(spec.workingDir))
        pb.redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(spec.env)

        val process = pb.start()
        val outputBuilder = StringBuilder()

        try {
            BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    if (!coroutineContext.isActive) {
                        process.destroyForcibly()
                        break
                    }
                    outputBuilder.appendLine(line)
                    onLine?.invoke(line)
                    line = reader.readLine()
                }
            }
            val code = process.waitFor()
            CommandExecutionResult(exitCode = code, output = outputBuilder.toString())
        } catch (t: Throwable) {
            process.destroyForcibly()
            throw t
        }
    }

    fun startBackgroundCommand(
        shellCommand: String,
        distro: DistroDefinition = distroManager.getActiveDistro()
    ): Process {
        val shell = distroManager.resolveLoginShell(distro)
        val spec = distroManager.buildProotSpec(
            distro = distro,
            guestCommand = listOf(shell, "-c", shellCommand)
        )
        val pb = ProcessBuilder(spec.args)
        pb.directory(File(spec.workingDir))
        pb.redirectErrorStream(true)
        pb.environment().clear()
        pb.environment().putAll(spec.env)
        return pb.start()
    }
}
