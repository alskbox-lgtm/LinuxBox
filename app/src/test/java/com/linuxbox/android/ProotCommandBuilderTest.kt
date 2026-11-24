package com.linuxbox.android

import com.linuxbox.android.core.PackageCatalog
import com.linuxbox.android.core.PackageManagerType
import com.linuxbox.android.core.ProotCommandBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProotCommandBuilderTest {

    @Test
    fun buildsValidProotArgumentsAndFakeProcBinds() {
        val base = Files.createTempDirectory("linuxbox-proot-test").toFile()
        try {
            val nativeDir = File(base, "native").apply { mkdirs() }
            File(nativeDir, "libproot.so").writeText("")
            File(nativeDir, "libproot-loader.so").writeText("")
            File(nativeDir, "libproot-loader32.so").writeText("")
            val rootfsDir = File(base, "rootfs").apply { mkdirs() }
            val sysdataDir = File(base, "sysdata")
            val tmpDir = File(base, "tmp")
            val sdcardDir = File(base, "sdcard").apply { mkdirs() }

            val spec = ProotCommandBuilder.build(
                nativeLibDir = nativeDir,
                rootfsDir = rootfsDir,
                sysdataDir = sysdataDir,
                prootTmpDir = tmpDir,
                guestCommand = listOf("/bin/ash", "-l"),
                mountAndroidStorage = true,
                androidStorageDir = sdcardDir,
                disableSeccomp = true
            )

            assertTrue(spec.executable.endsWith("libproot.so"))
            assertTrue(spec.args.contains("--link2symlink"))
            assertTrue(spec.args.contains("-0"))
            assertTrue(spec.args.contains(rootfsDir.absolutePath))
            assertTrue(spec.args.any { it.endsWith(":/proc/stat") })
            assertTrue(spec.args.any { it.endsWith(":/proc/loadavg") })
            assertTrue(spec.args.any { it.endsWith(":/mnt/android") })
            assertTrue(spec.args.contains("/bin/ash"))
            assertEquals("1", spec.env["PROOT_NO_SECCOMP"])
            assertTrue(spec.env["PROOT_LOADER"]!!.endsWith("libproot-loader.so"))
            assertTrue(spec.env["PROOT_LOADER_32"]!!.endsWith("libproot-loader32.so"))
            assertTrue(File(sysdataDir, "stat").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun parsesApkAndAptRepositorySearchResultsCleanly() {
        val apkRaw = """
            python3-3.12.7-r0 - A high-level scripting language
            py3-pip-24.2-r0 - Tool for installing and managing Python packages
        """.trimIndent()
        val apkParsed = PackageCatalog.parseSearchOutput(
            PackageManagerType.APK,
            apkRaw,
            installedSet = setOf("python3")
        )
        assertEquals(2, apkParsed.size)
        assertEquals("python3", apkParsed[0].packageName)
        assertTrue(apkParsed[0].isInstalled)
        assertEquals("py3-pip", apkParsed[1].packageName)
        assertFalse(apkParsed[1].isInstalled)

        val aptRaw = "ripgrep - Recursively searches directories for a regex pattern\n"
        val aptParsed = PackageCatalog.parseSearchOutput(
            PackageManagerType.APT,
            aptRaw,
            installedSet = emptySet()
        )
        assertEquals(1, aptParsed.size)
        assertEquals("ripgrep", aptParsed[0].packageName)
    }

    @Test
    fun githubUpdaterComparesSemanticVersionsCorrectly() {
        assertTrue(com.linuxbox.android.core.GitHubUpdater.isNewerVersion("1.0.5", "1.0.4"))
        assertTrue(com.linuxbox.android.core.GitHubUpdater.isNewerVersion("v1.1.0", "1.0.9"))
        assertTrue(com.linuxbox.android.core.GitHubUpdater.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(com.linuxbox.android.core.GitHubUpdater.isNewerVersion("1.0.5", "1.0.5"))
        assertFalse(com.linuxbox.android.core.GitHubUpdater.isNewerVersion("1.0.4", "1.0.5"))
    }
}
