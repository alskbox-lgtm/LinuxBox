package com.linuxbox.android.core

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

enum class ArchiveCompression {
    GZIP,
    XZ,
    NONE
}

data class ExtractionProgress(
    val entriesExtracted: Int,
    val bytesWritten: Long,
    val currentPath: String
)

/**
 * Extracts Linux rootfs archives (.tar.gz / .tar.xz / .tar) cleanly inside Android's sandbox:
 * - Preserves POSIX permissions (executable bits on /bin, /usr/bin, /sbin, /lib)
 * - Preserves symlinks (essential for BusyBox/Alpine/Debian dynamic linkers)
 * - Safely converts hard links into symlinks or copies (avoids Android F2FS/SELinux hardlink issues)
 * - Skips block/char device nodes (PRoot bind-mounts host /dev)
 * - Protects against path traversal outside destination rootfs
 */
object RootfsExtractor {

    fun extract(
        inputStream: InputStream,
        destinationDir: File,
        compression: ArchiveCompression,
        stripComponents: Int = 0,
        onProgress: ((ExtractionProgress) -> Unit)? = null
    ) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val rootCanonical = destinationDir.canonicalPath
        val buffered = BufferedInputStream(inputStream, 64 * 1024)
        val decompressed: InputStream = when (compression) {
            ArchiveCompression.GZIP -> GzipCompressorInputStream(buffered)
            ArchiveCompression.XZ -> XZCompressorInputStream(buffered)
            ArchiveCompression.NONE -> buffered
        }

        val deferredHardLinks = mutableListOf<Pair<File, String>>()
        val deferredDirModes = mutableListOf<Pair<File, Int>>()
        var entryCount = 0
        var totalBytes = 0L
        val buffer = ByteArray(64 * 1024)

        TarArchiveInputStream(decompressed).use { tar ->
            var entry: TarArchiveEntry? = tar.nextEntry
            while (entry != null) {
                val stripped = stripPathComponents(entry.name, stripComponents)
                if (stripped != null) {
                    val outFile = File(destinationDir, stripped)
                    // Path traversal check without following existing symlinks
                    val normalizedTarget = outFile.toPath().normalize().toAbsolutePath().toString()
                    if (!normalizedTarget.startsWith(rootCanonical)) {
                        entry = tar.nextEntry
                        continue
                    }

                    entryCount++
                    if (entryCount % 40 == 0 || entryCount <= 5) {
                        onProgress?.invoke(ExtractionProgress(entryCount, totalBytes, stripped))
                    }

                    when {
                        entry.isDirectory -> {
                            outFile.mkdirs()
                            val mode = normalizeMode(entry.mode, isDir = true)
                            deferredDirModes.add(outFile to mode)
                        }

                        entry.isSymbolicLink -> {
                            outFile.parentFile?.mkdirs()
                            deleteIfExists(outFile)
                            createSymlinkSafe(entry.linkName, outFile)
                        }

                        entry.isLink -> {
                            // Defer hardlink resolution until target file has been extracted
                            val strippedLink = stripPathComponents(entry.linkName, stripComponents)
                            if (strippedLink != null) {
                                deferredHardLinks.add(outFile to strippedLink)
                            }
                        }

                        entry.isFile -> {
                            outFile.parentFile?.mkdirs()
                            deleteIfExists(outFile)
                            FileOutputStream(outFile).use { fos ->
                                while (true) {
                                    val read = tar.read(buffer)
                                    if (read <= 0) break
                                    fos.write(buffer, 0, read)
                                    totalBytes += read
                                }
                            }
                            applyModeSafe(outFile, normalizeMode(entry.mode, isDir = false))
                        }

                        // Character devices, block devices, FIFOs are skipped safely
                        else -> Unit
                    }
                }
                entry = tar.nextEntry
            }
        }

        // Resolve hard links safely: prefer native hardlink, fallback to relative symlink or copy
        for ((linkFile, targetRelPath) in deferredHardLinks) {
            val targetFile = File(destinationDir, targetRelPath)
            if (!targetFile.exists()) continue
            linkFile.parentFile?.mkdirs()
            deleteIfExists(linkFile)
            val created = tryHardLink(targetFile, linkFile) ||
                tryRelativeSymlink(targetFile, linkFile)
            if (!created) {
                targetFile.copyTo(linkFile, overwrite = true)
                copyPermissionsSafe(targetFile, linkFile)
            }
        }

        // Apply directory permissions in reverse depth order so child writes succeed first
        for ((dir, mode) in deferredDirModes.asReversed()) {
            applyModeSafe(dir, mode)
        }

        onProgress?.invoke(ExtractionProgress(entryCount, totalBytes, "Concluído"))
    }

    internal fun stripPathComponents(rawName: String, stripComponents: Int): String? {
        val cleaned = rawName
            .removePrefix("./")
            .trimStart('/')
            .trimEnd('/')
        if (cleaned.isEmpty() || cleaned == ".") return null
        val parts = cleaned.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        if (parts.size <= stripComponents) return null
        return parts.drop(stripComponents).joinToString("/")
    }

    private fun normalizeMode(rawMode: Int, isDir: Boolean): Int {
        // Strip setuid/setgid/sticky bits and ensure owner has rwx on dirs or rw on files
        val perm = rawMode and 0x1FF // 0777
        return if (isDir) {
            perm or 0x1C0 // ensure owner rwx (0700) so app can always manage rootfs
        } else {
            perm or 0x180 // ensure owner rw (0600)
        }
    }

    private fun deleteIfExists(file: File) {
        try {
            Files.deleteIfExists(file.toPath())
        } catch (_: Throwable) {
            file.delete()
        }
    }

    private fun createSymlinkSafe(target: String, linkFile: File) {
        try {
            android.system.Os.symlink(target, linkFile.absolutePath)
            return
        } catch (_: Throwable) {
            // Fallback for JVM unit test environment
        }
        Files.createSymbolicLink(linkFile.toPath(), Paths.get(target))
    }

    private fun tryHardLink(existingTarget: File, newLink: File): Boolean {
        return try {
            android.system.Os.link(existingTarget.absolutePath, newLink.absolutePath)
            true
        } catch (_: Throwable) {
            try {
                Files.createLink(newLink.toPath(), existingTarget.toPath())
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun tryRelativeSymlink(targetFile: File, linkFile: File): Boolean {
        return try {
            val parentPath = linkFile.parentFile?.toPath() ?: return false
            val rel = parentPath.relativize(targetFile.toPath()).toString()
            createSymlinkSafe(rel, linkFile)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun applyModeSafe(file: File, mode: Int) {
        try {
            android.system.Os.chmod(file.absolutePath, mode)
            return
        } catch (_: Throwable) {
            // Fallback for JVM unit tests
        }
        val ownerExec = (mode and 0x40) != 0
        val anyExec = (mode and 0x49) != 0
        file.setReadable(true, false)
        file.setWritable(true, true)
        if (ownerExec || anyExec) {
            file.setExecutable(true, false)
        }
    }

    private fun copyPermissionsSafe(source: File, dest: File) {
        try {
            val stat = android.system.Os.stat(source.absolutePath)
            android.system.Os.chmod(dest.absolutePath, stat.st_mode and 0x1FF)
        } catch (_: Throwable) {
            if (source.canExecute()) {
                dest.setExecutable(true, false)
            }
        }
    }
}
