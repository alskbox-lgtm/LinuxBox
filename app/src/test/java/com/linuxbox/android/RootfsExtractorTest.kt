package com.linuxbox.android

import com.linuxbox.android.core.ArchiveCompression
import com.linuxbox.android.core.RootfsExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

class RootfsExtractorTest {

    @Test
    fun stripPathComponentsRejectsTraversalAndStripsPrefix() {
        assertEquals("bin/sh", RootfsExtractor.stripPathComponents("./bin/sh", 0))
        assertEquals("bin/sh", RootfsExtractor.stripPathComponents("debian-arm64/bin/sh", 1))
        assertNull(RootfsExtractor.stripPathComponents("debian-arm64/", 1))
        assertNull(RootfsExtractor.stripPathComponents("../../etc/passwd", 0))
    }

    @Test
    fun extractsRealBundledAlpineRootfsWithSymlinksAndExecutableBits() {
        val bundled = File("src/main/assets/rootfs/alpine-x86_64.tgz")
        assertTrue("Bundled Alpine rootfs archive must exist", bundled.exists())

        val tempDir = Files.createTempDirectory("linuxbox-alpine-test").toFile()
        try {
            FileInputStream(bundled).use { fis ->
                RootfsExtractor.extract(
                    inputStream = fis,
                    destinationDir = tempDir,
                    compression = ArchiveCompression.GZIP,
                    stripComponents = 0
                )
            }

            val busybox = File(tempDir, "bin/busybox")
            val shLink = File(tempDir, "bin/sh")
            val alpineRelease = File(tempDir, "etc/alpine-release")
            val apkBin = File(tempDir, "sbin/apk")

            assertTrue("bin/busybox should exist", busybox.exists())
            assertTrue("bin/busybox should be executable", busybox.canExecute())
            assertTrue("bin/sh symlink should exist", Files.isSymbolicLink(shLink.toPath()))
            assertTrue("sbin/apk should exist", apkBin.exists())
            assertTrue("sbin/apk should be executable", apkBin.canExecute())
            assertTrue("etc/alpine-release should exist", alpineRelease.exists())
            assertTrue(
                "etc/alpine-release should be 3.21.x",
                alpineRelease.readText().trim().startsWith("3.21")
            )
            assertFalse("No stray parent traversal files", File(tempDir.parentFile, "etc/alpine-release").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
