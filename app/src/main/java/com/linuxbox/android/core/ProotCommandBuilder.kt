package com.linuxbox.android.core

import java.io.File

data class ProotSpec(
    val executable: String,
    val args: List<String>,
    val env: Map<String, String>,
    val workingDir: String
)

/**
 * Constructs rock-solid PRoot invocations for running unmodified Linux distributions
 * completely standalone inside Android's sandbox without root access.
 */
object ProotCommandBuilder {

    fun build(
        nativeLibDir: File,
        rootfsDir: File,
        sysdataDir: File,
        prootTmpDir: File,
        guestCommand: List<String>,
        guestWorkDir: String = "/root",
        mountAndroidStorage: Boolean = true,
        androidStorageDir: File? = null,
        disableSeccomp: Boolean = false,
        extraEnv: Map<String, String> = emptyMap()
    ): ProotSpec {
        if (!prootTmpDir.exists()) {
            prootTmpDir.mkdirs()
        }

        val prootBin = File(nativeLibDir, "libproot.so")
        val loaderBin = File(nativeLibDir, "libproot-loader.so")
        val loader32Bin = File(nativeLibDir, "libproot-loader32.so")
        val fakeProc = FakeProcBuilder.ensureFakeProcFiles(sysdataDir)

        val args = mutableListOf<String>()
        args.add(prootBin.absolutePath)
        args.add("--link2symlink")
        args.add("-0")
        args.add("-r")
        args.add(rootfsDir.absolutePath)
        args.add("-w")
        args.add(guestWorkDir)

        // Core Linux virtual filesystems
        args.add("-b"); args.add("/dev")
        args.add("-b"); args.add("/proc")
        args.add("-b"); args.add("/sys")
        args.add("-b"); args.add("/dev/urandom:/dev/random")
        args.add("-b"); args.add("/proc/self/fd:/dev/fd")
        args.add("-b"); args.add("/proc/self/fd/0:/dev/stdin")
        args.add("-b"); args.add("/proc/self/fd/1:/dev/stdout")
        args.add("-b"); args.add("/proc/self/fd/2:/dev/stderr")

        // Bind writable shared-memory directory (critical for apt, python multiprocessing, X11/VNC)
        val shmDir = File(prootTmpDir, "shm").apply { mkdirs() }
        args.add("-b"); args.add("${shmDir.absolutePath}:/dev/shm")

        // Bind safe fake /proc entries so top/htop/uptime/free work under Android SELinux
        args.add("-b"); args.add("${fakeProc.loadavg.absolutePath}:/proc/loadavg")
        args.add("-b"); args.add("${fakeProc.stat.absolutePath}:/proc/stat")
        args.add("-b"); args.add("${fakeProc.uptime.absolutePath}:/proc/uptime")
        args.add("-b"); args.add("${fakeProc.version.absolutePath}:/proc/version")
        args.add("-b"); args.add("${fakeProc.vmstat.absolutePath}:/proc/vmstat")

        // Optional shared Android storage access inside Linux at /mnt/android
        if (mountAndroidStorage && androidStorageDir != null && androidStorageDir.exists()) {
            val mountPoint = File(rootfsDir, "mnt/android")
            if (!mountPoint.exists()) mountPoint.mkdirs()
            args.add("-b"); args.add("${androidStorageDir.absolutePath}:/mnt/android")
        }

        // Launch guest command with clean Linux environment via /usr/bin/env
        args.add("/usr/bin/env")
        args.add("-i")
        args.add("HOME=/root")
        args.add("USER=root")
        args.add("LOGNAME=root")
        args.add("TERM=xterm-256color")
        args.add("COLORTERM=truecolor")
        args.add("LANG=C.UTF-8")
        args.add("LC_ALL=C.UTF-8")
        args.add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        args.add("TMPDIR=/tmp")
        args.add("DISPLAY=127.0.0.1:10")
        args.add("MOZ_FAKE_NO_SANDBOX=1")
        args.add("DEBIAN_FRONTEND=noninteractive")
        extraEnv.forEach { (k, v) ->
            args.add("$k=$v")
        }
        args.addAll(guestCommand)

        val env = linkedMapOf(
            "PROOT_TMP_DIR" to prootTmpDir.absolutePath,
            "PROOT_LOADER" to loaderBin.absolutePath,
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "TMPDIR" to prootTmpDir.absolutePath
        )
        if (loader32Bin.exists()) {
            env["PROOT_LOADER_32"] = loader32Bin.absolutePath
        }
        if (disableSeccomp) {
            env["PROOT_NO_SECCOMP"] = "1"
        }

        return ProotSpec(
            executable = prootBin.absolutePath,
            args = args,
            env = env,
            workingDir = rootfsDir.absolutePath
        )
    }
}
