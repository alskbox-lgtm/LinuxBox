package com.linuxbox.android.core

import java.io.File

/**
 * Generates safe static /proc entries for PRoot bind-mounting.
 * Android 8+ SELinux blocks untrusted apps from reading several /proc files
 * (/proc/stat, /proc/loadavg, /proc/uptime, /proc/version, /proc/vmstat), which causes
 * standard Linux tools like htop, top, uptime, ps, and fastfetch to fail unless overridden.
 */
object FakeProcBuilder {

    data class FakeProcFiles(
        val loadavg: File,
        val stat: File,
        val uptime: File,
        val version: File,
        val vmstat: File
    )

    fun ensureFakeProcFiles(sysdataDir: File, cpuCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)): FakeProcFiles {
        if (!sysdataDir.exists()) {
            sysdataDir.mkdirs()
        }

        val loadavg = File(sysdataDir, "loadavg").apply {
            writeText("0.18 0.14 0.10 1/248 1024\n")
        }

        val statContent = buildString {
            appendLine("cpu  12450 210 5320 482190 610 0 185 0 0 0")
            for (i in 0 until cpuCount) {
                appendLine("cpu$i ${12450 / cpuCount} ${210 / cpuCount} ${5320 / cpuCount} ${482190 / cpuCount} ${610 / cpuCount} 0 ${185 / cpuCount} 0 0 0")
            }
            appendLine("intr 248192 0 0 0 0 0 0 0")
            appendLine("ctxt 982144")
            appendLine("btime ${System.currentTimeMillis() / 1000L - 3600L}")
            appendLine("processes 4820")
            appendLine("procs_running 1")
            appendLine("procs_blocked 0")
            appendLine("softirq 98210 0 32100 120 4200 0 0 1200 28400 0 32190")
        }
        val stat = File(sysdataDir, "stat").apply {
            writeText(statContent)
        }

        val uptime = File(sysdataDir, "uptime").apply {
            writeText("3600.42 14210.18\n")
        }

        val arch = System.getProperty("os.arch") ?: "aarch64"
        val version = File(sysdataDir, "version").apply {
            writeText("Linux version 6.6.0-linuxbox (android@$arch) (clang 18) #1 SMP PREEMPT\n")
        }

        val vmstat = File(sysdataDir, "vmstat").apply {
            writeText(
                """
                nr_free_pages 128000
                nr_zone_inactive_anon 24500
                nr_zone_active_anon 62100
                nr_zone_inactive_file 41200
                nr_zone_active_file 38900
                pgpgin 102400
                pgpgout 84200
                pswpin 0
                pswpout 0
                pgfault 412800
                pgmajfault 120
                """.trimIndent() + "\n"
            )
        }

        return FakeProcFiles(
            loadavg = loadavg,
            stat = stat,
            uptime = uptime,
            version = version,
            vmstat = vmstat
        )
    }
}
