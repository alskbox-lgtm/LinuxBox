package com.linuxbox.android.core

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class DistroInstallState(
    val distro: DistroDefinition,
    val isInstalled: Boolean,
    val isActive: Boolean,
    val diskUsageBytes: Long,
    val supportedOnDevice: Boolean
)

data class SetupProgress(
    val stage: String,
    val detail: String,
    val fraction: Float? // null = indeterminate
)

class DistroManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("linuxbox_prefs", Context.MODE_PRIVATE)
    val distrosBaseDir: File = File(context.filesDir, "distros").apply { mkdirs() }
    val prootTmpDir: File = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
    val nativeLibDir: File = File(context.applicationInfo.nativeLibraryDir)
    val deviceAbi: String = DistroCatalog.detectPreferredAbi()

    var activeDistroId: String
        get() = prefs.getString("active_distro", DistroCatalog.alpine.id) ?: DistroCatalog.alpine.id
        set(value) {
            prefs.edit().putString("active_distro", value).apply()
        }

    var mountAndroidStorage: Boolean
        get() = prefs.getBoolean("mount_android_storage", true)
        set(value) {
            prefs.edit().putBoolean("mount_android_storage", value).apply()
        }

    var disableSeccomp: Boolean
        get() = prefs.getBoolean("disable_seccomp", false)
        set(value) {
            prefs.edit().putBoolean("disable_seccomp", value).apply()
        }

    fun distroDir(distroId: String): File = File(distrosBaseDir, distroId)

    fun rootfsDir(distroId: String): File = File(distroDir(distroId), "rootfs")

    fun sysdataDir(distroId: String): File = File(distroDir(distroId), "sysdata")

    private fun readyMarker(distroId: String): File = File(distroDir(distroId), ".linuxbox_ready")

    fun isInstalled(distroId: String): Boolean {
        val rootfs = rootfsDir(distroId)
        return readyMarker(distroId).exists() &&
            (File(rootfs, "bin/sh").exists() || File(rootfs, "usr/bin/sh").exists())
    }

    fun getActiveDistro(): DistroDefinition {
        val id = activeDistroId
        if (isInstalled(id)) return DistroCatalog.byId(id)
        val firstInstalled = DistroCatalog.all.firstOrNull { isInstalled(it.id) }
        return firstInstalled ?: DistroCatalog.alpine
    }

    fun getAndroidStorageDir(): File? {
        return try {
            val dir = Environment.getExternalStorageDirectory()
            if (dir != null && dir.exists()) dir else null
        } catch (_: Throwable) {
            null
        }
    }

    fun resolveLoginShell(distro: DistroDefinition): String {
        val rootfs = rootfsDir(distro.id)
        val candidates = listOf(
            distro.defaultShell,
            "/bin/bash",
            "/usr/bin/bash",
            "/bin/ash",
            "/bin/sh",
            "/usr/bin/sh"
        )
        for (candidate in candidates) {
            val rel = candidate.trimStart('/')
            if (File(rootfs, rel).exists()) {
                return candidate
            }
        }
        return "/bin/sh"
    }

    fun buildProotSpec(
        distro: DistroDefinition = getActiveDistro(),
        guestCommand: List<String>,
        guestWorkDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap()
    ): ProotSpec {
        migrateLegacyDisplayConfig(rootfsDir(distro.id))
        return ProotCommandBuilder.build(
            nativeLibDir = nativeLibDir,
            rootfsDir = rootfsDir(distro.id),
            sysdataDir = sysdataDir(distro.id),
            prootTmpDir = prootTmpDir,
            guestCommand = guestCommand,
            guestWorkDir = guestWorkDir,
            mountAndroidStorage = mountAndroidStorage,
            androidStorageDir = getAndroidStorageDir(),
            disableSeccomp = disableSeccomp,
            extraEnv = extraEnv
        )
    }

    private fun migrateLegacyDisplayConfig(rootfs: File) {
        val files = listOf(
            File(rootfs, "etc/profile.d/linuxbox.sh"),
            File(rootfs, "root/.profile"),
            File(rootfs, "root/.bashrc")
        )
        for (f in files) {
            if (!f.exists()) continue
            try {
                val text = f.readText()
                var updated = text
                if (updated.contains("export DISPLAY=:1")) {
                    updated = updated.replace("export DISPLAY=:1", "export DISPLAY=\"\${DISPLAY:-127.0.0.1:10}\"")
                }
                if (!updated.contains("ELECTRON_DISABLE_SANDBOX")) {
                    updated += "\nexport ELECTRON_DISABLE_SANDBOX=1\nexport QTWEBENGINE_DISABLE_SANDBOX=1\nexport MOZ_FAKE_NO_SANDBOX=1\n"
                }
                if (updated != text) {
                    f.writeText(updated)
                }
            } catch (_: Throwable) {
                // ignore
            }
        }

        // Auto-wrap Chromium/Electron binaries in /usr/local/bin with --no-sandbox --disable-dev-shm-usage
        val localBin = File(rootfs, "usr/local/bin")
        val chromiumApps = listOf(
            "chromium",
            "chromium-browser",
            "google-chrome",
            "google-chrome-stable",
            "brave-browser",
            "code",
            "codium",
            "discord",
            "obsidian"
        )
        for (appName in chromiumApps) {
            val realBin = File(rootfs, "usr/bin/$appName")
            val wrapper = File(localBin, appName)
            if (realBin.exists() && !wrapper.exists()) {
                try {
                    localBin.mkdirs()
                    wrapper.writeText(
                        "#!/bin/sh\nexec /usr/bin/$appName --no-sandbox --disable-dev-shm-usage \"\$@\"\n"
                    )
                    try {
                        android.system.Os.chmod(wrapper.absolutePath, 0x1ED) // 0755
                    } catch (_: Throwable) {
                        wrapper.setExecutable(true, false)
                    }
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }

    suspend fun listDistroStates(): List<DistroInstallState> = withContext(Dispatchers.IO) {
        val activeId = activeDistroId
        DistroCatalog.all.map { def ->
            val installed = isInstalled(def.id)
            DistroInstallState(
                distro = def,
                isInstalled = installed,
                isActive = installed && def.id == activeId,
                diskUsageBytes = if (installed) computeDirectorySize(rootfsDir(def.id)) else 0L,
                supportedOnDevice = def.supportsAbi(deviceAbi)
            )
        }
    }

    suspend fun ensureDefaultDistroInstalled(
        onProgress: (SetupProgress) -> Unit
    ): DistroDefinition {
        val alpine = DistroCatalog.alpine
        if (!isInstalled(alpine.id)) {
            installDistro(alpine, onProgress)
            activeDistroId = alpine.id
        }
        return getActiveDistro()
    }

    suspend fun installDistro(
        distro: DistroDefinition,
        onProgress: (SetupProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val targetDistroDir = distroDir(distro.id)
        val targetRootfs = rootfsDir(distro.id)
        val marker = readyMarker(distro.id)

        if (marker.exists()) marker.delete()
        if (targetRootfs.exists()) {
            onProgress(SetupProgress("Limpando instalação anterior", distro.name, null))
            deleteRecursivelySafe(targetRootfs)
        }
        targetRootfs.mkdirs()

        val bundledAsset = distro.bundledAssetByAbi[deviceAbi]
        if (bundledAsset != null) {
            onProgress(
                SetupProgress(
                    stage = "Extraindo ${distro.name} (offline)",
                    detail = "Preparando sistema Linux embutido ($deviceAbi)...",
                    fraction = 0.1f
                )
            )
            context.assets.open(bundledAsset).use { assetStream ->
                RootfsExtractor.extract(
                    inputStream = assetStream,
                    destinationDir = targetRootfs,
                    compression = distro.compression,
                    stripComponents = distro.stripComponents
                ) { prog ->
                    val mb = prog.bytesWritten / (1024f * 1024f)
                    onProgress(
                        SetupProgress(
                            stage = "Extraindo ${distro.name}",
                            detail = "${prog.entriesExtracted} arquivos • %.1f MB • %s".format(mb, prog.currentPath),
                            fraction = (0.1f + (prog.entriesExtracted / 1200f).coerceAtMost(0.8f))
                        )
                    )
                }
            }
        } else {
            val urlString = distro.downloadUrlByAbi[deviceAbi]
                ?: throw IllegalStateException("${distro.name} não possui imagem para arquitetura $deviceAbi")

            val tempArchive = File(context.cacheDir, "${distro.id}-rootfs.tmp")
            if (tempArchive.exists()) tempArchive.delete()

            try {
                downloadFileWithProgress(urlString, tempArchive) { downloaded, total ->
                    val dlMb = downloaded / (1024f * 1024f)
                    val detail = if (total > 0) {
                        val totalMb = total / (1024f * 1024f)
                        "Baixando %.1f MB / %.1f MB".format(dlMb, totalMb)
                    } else {
                        "Baixando %.1f MB...".format(dlMb)
                    }
                    val frac = if (total > 0) (downloaded.toFloat() / total.toFloat()) * 0.55f else null
                    onProgress(SetupProgress("Baixando ${distro.name} ${distro.version}", detail, frac))
                }

                onProgress(
                    SetupProgress(
                        stage = "Extraindo ${distro.name}",
                        detail = "Descompactando sistema de arquivos Linux...",
                        fraction = 0.6f
                    )
                )

                FileInputStream(tempArchive).use { fis ->
                    RootfsExtractor.extract(
                        inputStream = fis,
                        destinationDir = targetRootfs,
                        compression = distro.compression,
                        stripComponents = distro.stripComponents
                    ) { prog ->
                        val mb = prog.bytesWritten / (1024f * 1024f)
                        onProgress(
                            SetupProgress(
                                stage = "Extraindo ${distro.name}",
                                detail = "${prog.entriesExtracted} arquivos • %.1f MB".format(mb),
                                fraction = 0.6f + (prog.entriesExtracted / 9000f).coerceAtMost(0.35f)
                            )
                        )
                    }
                }
            } finally {
                tempArchive.delete()
            }
        }

        onProgress(SetupProgress("Configurando ambiente Linux", "DNS, repositórios e perfil de usuário...", 0.96f))
        configureRootfs(distro, targetRootfs)
        FakeProcBuilder.ensureFakeProcFiles(sysdataDir(distro.id))
        marker.writeText("installed=${System.currentTimeMillis()}\nabi=$deviceAbi\nversion=${distro.version}\n")

        onProgress(SetupProgress("Pronto!", "${distro.name} configurado e pronto para uso.", 1f))
    }

    suspend fun removeDistro(distroId: String) = withContext(Dispatchers.IO) {
        deleteRecursivelySafe(distroDir(distroId))
        if (activeDistroId == distroId) {
            val fallback = DistroCatalog.all.firstOrNull { isInstalled(it.id) }?.id
                ?: DistroCatalog.alpine.id
            activeDistroId = fallback
        }
    }

    private fun configureRootfs(distro: DistroDefinition, rootfs: File) {
        val etcDir = File(rootfs, "etc").apply { mkdirs() }
        val rootHome = File(rootfs, "root").apply { mkdirs() }
        val tmpDir = File(rootfs, "tmp").apply { mkdirs() }
        File(rootfs, "mnt/android").mkdirs()
        File(rootfs, "dev/shm").mkdirs()

        try {
            android.system.Os.chmod(tmpDir.absolutePath, 0x1FF) // 0777
        } catch (_: Throwable) {
            tmpDir.setReadable(true, false)
            tmpDir.setWritable(true, false)
            tmpDir.setExecutable(true, false)
        }

        // Reliable DNS servers (Cloudflare, Google, Quad9)
        File(etcDir, "resolv.conf").writeText(
            """
            nameserver 1.1.1.1
            nameserver 8.8.8.8
            nameserver 9.9.9.9
            """.trimIndent() + "\n"
        )

        File(etcDir, "hosts").writeText(
            """
            127.0.0.1 localhost linuxbox
            ::1       localhost ip6-localhost ip6-loopback
            """.trimIndent() + "\n"
        )

        File(etcDir, "hostname").writeText("linuxbox\n")

        // Distro-specific package manager configuration for Android PRoot compatibility
        when (distro.packageManager) {
            PackageManagerType.APK -> {
                val apkDir = File(etcDir, "apk").apply { mkdirs() }
                File(apkDir, "repositories").writeText(
                    """
                    https://dl-cdn.alpinelinux.org/alpine/v3.21/main
                    https://dl-cdn.alpinelinux.org/alpine/v3.21/community
                    """.trimIndent() + "\n"
                )
            }

            PackageManagerType.APT -> {
                val aptConfDir = File(etcDir, "apt/apt.conf.d").apply { mkdirs() }
                // Critical under PRoot: disables _apt user sandbox which fails setgroups() without real root
                File(aptConfDir, "99linuxbox-proot").writeText(
                    """
                    APT::Sandbox::User "root";
                    Acquire::Retries "3";
                    Dpkg::Options { "--force-confdef"; "--force-confold"; }
                    """.trimIndent() + "\n"
                )
            }

            PackageManagerType.PACMAN -> {
                val pacmanConf = File(etcDir, "pacman.conf")
                if (pacmanConf.exists()) {
                    val content = pacmanConf.readText()
                    if (!content.contains("DisableSandbox")) {
                        pacmanConf.writeText(
                            content.replace("[options]", "[options]\nDisableSandbox")
                        )
                    }
                }
            }

            PackageManagerType.DNF -> Unit
        }

        // Clean interactive shell profile with helpful Portuguese welcome & aliases
        val pkgHint = when (distro.packageManager) {
            PackageManagerType.APK -> "apk add python3 nodejs git nano"
            PackageManagerType.APT -> "apt update && apt install python3 nodejs git nano"
            PackageManagerType.PACMAN -> "pacman -Sy python nodejs git nano"
            PackageManagerType.DNF -> "dnf install python3 nodejs git nano"
        }

        val profileScript = """
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export HOME=/root
            export USER=root
            export TERM=xterm-256color
            export COLORTERM=truecolor
            export LANG=C.UTF-8
            export DISPLAY="${'$'}{DISPLAY:-127.0.0.1:10}"
            export PS1='\[\e[1;32m\]root@linuxbox\[\e[0m\]:\[\e[1;36m\]\w\[\e[0m\]# '
            alias ll='ls -la --color=auto'
            alias la='ls -A --color=auto'
            alias cls='clear'
            ajuda() {
              printf '\033[1;32m=== LinuxBox (%s %s) ===\033[0m\n' '${distro.name}' '${distro.version}'
              printf '  Instalar programas: \033[1;36m%s\033[0m\n' '$pkgHint'
              printf '  Pasta do Android:   \033[1;36m/mnt/android\033[0m\n'
              printf '  Dica: use a aba \033[1;33mProgramas\033[0m para instalar apps com 1 toque!\n\n'
            }
            if [ -z "${'$'}LINUXBOX_WELCOMED" ]; then
              export LINUXBOX_WELCOMED=1
              printf '\033[1;32m● LinuxBox ativo:\033[0m %s %s (%s)\n' '${distro.name}' '${distro.version}' "$(uname -m 2>/dev/null || echo linux)"
              printf '\033[2mDigite \033[0;36majuda\033[2m para ver comandos ou use a aba Programas para instalar pacotes.\033[0m\n\n'
            fi
        """.trimIndent() + "\n"

        val profileD = File(etcDir, "profile.d").apply { mkdirs() }
        File(profileD, "linuxbox.sh").writeText(profileScript)
        File(rootHome, ".profile").writeText(profileScript)
        File(rootHome, ".bashrc").writeText(profileScript)
    }

    private fun downloadFileWithProgress(
        urlString: String,
        outputFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        var currentUrl = urlString
        var redirects = 0
        var conn: HttpURLConnection

        while (true) {
            conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 45_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "LinuxBox-Android/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 8) {
                val location = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirecionamento sem destino (HTTP $code)")
                currentUrl = URL(URL(currentUrl), location).toString()
                conn.disconnect()
                redirects++
            } else {
                break
            }
        }

        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Erro ao baixar imagem Linux (HTTP ${conn.responseCode})")
            }
            val totalBytes = conn.contentLengthLong
            var downloaded = 0L
            val buffer = ByteArray(64 * 1024)
            var lastNotify = 0L

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 150) {
                            lastNotify = now
                            onProgress(downloaded, totalBytes)
                        }
                    }
                }
            }
            onProgress(downloaded, totalBytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun computeDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                try {
                    val isSymlink = java.nio.file.Files.isSymbolicLink(child.toPath())
                    if (isSymlink) continue
                    if (child.isDirectory) {
                        stack.add(child)
                    } else {
                        sum += child.length()
                    }
                } catch (_: Throwable) {
                    // ignore unreadable entries
                }
            }
        }
        return sum
    }

    private fun deleteRecursivelySafe(root: File) {
        if (!root.exists()) return
        val stack = ArrayDeque<File>()
        val postOrder = mutableListOf<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            postOrder.add(cur)
            try {
                if (!java.nio.file.Files.isSymbolicLink(cur.toPath()) && cur.isDirectory) {
                    cur.setWritable(true)
                    cur.listFiles()?.forEach { stack.add(it) }
                }
            } catch (_: Throwable) {
                // ignore
            }
        }
        for (file in postOrder.asReversed()) {
            try {
                java.nio.file.Files.deleteIfExists(file.toPath())
            } catch (_: Throwable) {
                file.delete()
            }
        }
    }
}
