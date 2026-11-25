package com.linuxbox.android.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linuxbox.android.BuildConfig
import com.linuxbox.android.LinuxBoxApp
import com.linuxbox.android.core.AppUpdateInfo
import com.linuxbox.android.core.DistroDefinition
import com.linuxbox.android.core.DistroInstallState
import com.linuxbox.android.core.GitHubUpdater
import com.linuxbox.android.core.LinuxProgram
import com.linuxbox.android.core.PackageCatalog
import com.linuxbox.android.core.PackageManagerType
import com.linuxbox.android.core.ProgramCategory
import com.linuxbox.android.core.RepoSearchResult
import com.linuxbox.android.core.SetupProgress
import com.linuxbox.android.service.LinuxSessionService
import com.linuxbox.android.terminal.TerminalMode
import com.linuxbox.android.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class MainTab(val label: String) {
    TERMINAL("Terminal"),
    PROGRAMS("Programas"),
    DESKTOP("Desktop"),
    SYSTEMS("Sistemas")
}

data class ConsoleTaskState(
    val title: String,
    val commandPreview: String,
    val isRunning: Boolean,
    val exitCode: Int? = null,
    val lines: List<String> = emptyList(),
    val launchAfterCommand: String? = null
)

data class PendingInstallerPrompt(
    val fileName: String,
    val guestPath: String,
    val distro: DistroDefinition
)

class LinuxBoxViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LinuxBoxApp
    val distroManager = app.distroManager
    val commandRunner = app.commandRunner
    val sessionManager = app.sessionManager
    val desktopController = app.desktopController

    private val _selectedTab = MutableStateFlow(MainTab.TERMINAL)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _isBootstrapping = MutableStateFlow(true)
    val isBootstrapping: StateFlow<Boolean> = _isBootstrapping.asStateFlow()

    private val _bootstrapProgress = MutableStateFlow(
        SetupProgress("Iniciando LinuxBox", "Verificando ambiente Linux local...", null)
    )
    val bootstrapProgress: StateFlow<SetupProgress> = _bootstrapProgress.asStateFlow()

    private val _bootstrapError = MutableStateFlow<String?>(null)
    val bootstrapError: StateFlow<String?> = _bootstrapError.asStateFlow()

    private val _activeDistro = MutableStateFlow(distroManager.getActiveDistro())
    val activeDistro: StateFlow<DistroDefinition> = _activeDistro.asStateFlow()

    private val _distroStates = MutableStateFlow<List<DistroInstallState>>(emptyList())
    val distroStates: StateFlow<List<DistroInstallState>> = _distroStates.asStateFlow()

    private val _distroTaskProgress = MutableStateFlow<Pair<String, SetupProgress>?>(null)
    val distroTaskProgress: StateFlow<Pair<String, SetupProgress>?> = _distroTaskProgress.asStateFlow()

    private val _distroError = MutableStateFlow<String?>(null)
    val distroError: StateFlow<String?> = _distroError.asStateFlow()

    // Terminal UI preferences
    private val _fontSizeSp = MutableStateFlow(13.5f)
    val fontSizeSp: StateFlow<Float> = _fontSizeSp.asStateFlow()

    // App Store / Package Manager state
    private val _selectedCategory = MutableStateFlow(ProgramCategory.ALL)
    val selectedCategory: StateFlow<ProgramCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _installedPackages = MutableStateFlow<Set<String>>(emptySet())
    val installedPackages: StateFlow<Set<String>> = _installedPackages.asStateFlow()

    private val _isRefreshingPackages = MutableStateFlow(false)
    val isRefreshingPackages: StateFlow<Boolean> = _isRefreshingPackages.asStateFlow()

    private val _repoSearchResults = MutableStateFlow<List<RepoSearchResult>>(emptyList())
    val repoSearchResults: StateFlow<List<RepoSearchResult>> = _repoSearchResults.asStateFlow()

    private val _isSearchingRepo = MutableStateFlow(false)
    val isSearchingRepo: StateFlow<Boolean> = _isSearchingRepo.asStateFlow()

    private val _consoleTask = MutableStateFlow<ConsoleTaskState?>(null)
    val consoleTask: StateFlow<ConsoleTaskState?> = _consoleTask.asStateFlow()

    // Installer mode chooser (Terminal CLI vs Graphical UI)
    private val _preparingInstallerName = MutableStateFlow<String?>(null)
    val preparingInstallerName: StateFlow<String?> = _preparingInstallerName.asStateFlow()

    private val _pendingInstallerPrompt = MutableStateFlow<PendingInstallerPrompt?>(null)
    val pendingInstallerPrompt: StateFlow<PendingInstallerPrompt?> = _pendingInstallerPrompt.asStateFlow()

    // Automatic GitHub update checker state
    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateStatusMessage = MutableStateFlow<String?>(null)
    val updateStatusMessage: StateFlow<String?> = _updateStatusMessage.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    // Engine settings
    private val _mountAndroidStorage = MutableStateFlow(distroManager.mountAndroidStorage)
    val mountAndroidStorage: StateFlow<Boolean> = _mountAndroidStorage.asStateFlow()

    private val _disableSeccomp = MutableStateFlow(distroManager.disableSeccomp)
    val disableSeccomp: StateFlow<Boolean> = _disableSeccomp.asStateFlow()

    private var activeConsoleJob: Job? = null
    private var pendingInstallerUri: Uri? = null

    init {
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _isBootstrapping.value = true
            _bootstrapError.value = null
            try {
                val distro = distroManager.ensureDefaultDistroInstalled { progress ->
                    _bootstrapProgress.value = progress
                }
                _activeDistro.value = distro
                _distroStates.value = distroManager.listDistroStates()
                if (sessionManager.sessions.value.isEmpty()) {
                    sessionManager.createSession(distro = distro, mode = TerminalMode.STANDALONE)
                }
                startForegroundServiceSafe("${distro.name} ${distro.version} ativo")
                _isBootstrapping.value = false
                refreshInstalledPackages()
                desktopController.refreshInstalledState()
                checkForAppUpdate(silent = true)
                pendingInstallerUri?.let { uri ->
                    pendingInstallerUri = null
                    handleInstallerUri(uri)
                }
            } catch (t: Throwable) {
                _bootstrapError.value = t.message ?: "Erro inesperado ao inicializar o sistema Linux."
                _isBootstrapping.value = false
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
        if (tab == MainTab.PROGRAMS && _installedPackages.value.isEmpty()) {
            refreshInstalledPackages()
        } else if (tab == MainTab.DESKTOP) {
            viewModelScope.launch { desktopController.refreshInstalledState() }
        } else if (tab == MainTab.SYSTEMS) {
            refreshDistroStates()
        }
    }

    fun adjustFontSize(delta: Float) {
        _fontSizeSp.value = (_fontSizeSp.value + delta).coerceIn(9f, 24f)
    }

    fun selectTerminalMode(mode: TerminalMode) {
        sessionManager.selectMode(mode, autoCreateIfEmpty = true)
    }

    fun createNewTerminalSession(
        mode: TerminalMode = sessionManager.selectedMode.value,
        initialCommand: String? = null
    ): TerminalSession {
        val distro = _activeDistro.value
        val session = sessionManager.createSession(
            distro = distro,
            mode = mode,
            initialCommand = initialCommand
        )
        startForegroundServiceSafe("${distro.name} • ${sessionManager.sessions.value.size} sessão(ões)")
        return session
    }

    fun closeTerminalSession(id: String) {
        sessionManager.closeSession(id)
        if (sessionManager.sessions.value.isEmpty() && !desktopController.isServerRunning.value) {
            try {
                LinuxSessionService.stop(app)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    fun runCommandInTerminal(
        command: String,
        mode: TerminalMode = sessionManager.selectedMode.value
    ) {
        _selectedTab.value = MainTab.TERMINAL
        sessionManager.runInTerminal(command, mode)
        startForegroundServiceSafe("${_activeDistro.value.name} ativo")
    }

    fun setCategory(category: ProgramCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _repoSearchResults.value = emptyList()
        }
    }

    fun refreshInstalledPackages() {
        viewModelScope.launch {
            if (_isRefreshingPackages.value) return@launch
            _isRefreshingPackages.value = true
            try {
                val distro = _activeDistro.value
                val cmd = PackageCatalog.buildListInstalledCommand(distro.packageManager)
                val res = commandRunner.runShellCommand(cmd, distro = distro)
                val pkgs = res.output.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                _installedPackages.value = pkgs
            } catch (_: Throwable) {
                // ignore transient errors
            } finally {
                _isRefreshingPackages.value = false
            }
        }
    }

    fun searchRepositoryPackages() {
        val query = _searchQuery.value.trim()
        if (query.length < 2) return
        viewModelScope.launch {
            _isSearchingRepo.value = true
            try {
                val distro = _activeDistro.value
                val cmd = PackageCatalog.buildSearchCommand(distro.packageManager, query)
                val res = commandRunner.runShellCommand(cmd, distro = distro)
                val parsed = PackageCatalog.parseSearchOutput(
                    pm = distro.packageManager,
                    output = res.output,
                    installedSet = _installedPackages.value
                )
                _repoSearchResults.value = parsed
            } catch (_: Throwable) {
                _repoSearchResults.value = emptyList()
            } finally {
                _isSearchingRepo.value = false
            }
        }
    }

    fun installProgram(program: LinuxProgram) {
        val distro = _activeDistro.value
        val pkgs = program.packagesFor(distro.packageManager)
        val cmd = PackageCatalog.buildInstallCommand(distro.packageManager, pkgs)
        runConsoleTask(
            title = "Instalando ${program.name}",
            command = cmd,
            launchAfter = program.launchCommand
        )
    }

    fun removeProgram(program: LinuxProgram) {
        val distro = _activeDistro.value
        val pkgs = program.packagesFor(distro.packageManager)
        val cmd = PackageCatalog.buildRemoveCommand(distro.packageManager, pkgs)
        runConsoleTask(
            title = "Removendo ${program.name}",
            command = cmd,
            launchAfter = null
        )
    }

    fun installPackageByName(packageName: String) {
        val distro = _activeDistro.value
        val cmd = PackageCatalog.buildInstallCommand(distro.packageManager, listOf(packageName))
        runConsoleTask(
            title = "Instalando pacote '$packageName'",
            command = cmd,
            launchAfter = packageName
        )
    }

    fun removePackageByName(packageName: String) {
        val distro = _activeDistro.value
        val cmd = PackageCatalog.buildRemoveCommand(distro.packageManager, listOf(packageName))
        runConsoleTask(
            title = "Removendo pacote '$packageName'",
            command = cmd,
            launchAfter = null
        )
    }

    fun updateAllPackages() {
        val distro = _activeDistro.value
        val cmd = PackageCatalog.buildUpdateAllCommand(distro.packageManager)
        runConsoleTask(
            title = "Atualizando ${distro.name}",
            command = cmd,
            launchAfter = null
        )
    }

    fun installDesktopStack() {
        val distro = _activeDistro.value
        val desktopProgram = PackageCatalog.programs.first { it.id == "desktop_fluxbox" }
        val pkgs = desktopProgram.packagesFor(distro.packageManager)
        val cmd = PackageCatalog.buildInstallCommand(distro.packageManager, pkgs)
        runConsoleTask(
            title = "Instalando Desktop Gráfico (Fluxbox + X11 + VNC)",
            command = cmd,
            launchAfter = null
        )
    }

    fun startDesktop(resolution: String) {
        viewModelScope.launch {
            startForegroundServiceSafe("Desktop Linux ($resolution)")
            desktopController.startDesktop(resolution)
        }
    }

    fun stopDesktop() {
        desktopController.stopDesktop()
    }

    fun handleInstallerUri(uri: Uri) {
        if (_isBootstrapping.value) {
            pendingInstallerUri = uri
            return
        }
        viewModelScope.launch {
            try {
                val distro = _activeDistro.value
                val rawName = resolveDisplayName(uri) ?: "instalador_linux"
                val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                _preparingInstallerName.value = safeName

                val rootfsTmp = File(distroManager.rootfsDir(distro.id), "tmp").apply { mkdirs() }
                val destFile = File(rootfsTmp, "linuxbox_inst_$safeName")

                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    } ?: throw IllegalStateException("Não foi possível ler o arquivo selecionado.")
                }

                _preparingInstallerName.value = null
                _pendingInstallerPrompt.value = PendingInstallerPrompt(
                    fileName = safeName,
                    guestPath = "/tmp/${destFile.name}",
                    distro = distro
                )
            } catch (t: Throwable) {
                _preparingInstallerName.value = null
                _consoleTask.value = ConsoleTaskState(
                    title = "Erro ao abrir instalador",
                    commandPreview = uri.toString(),
                    isRunning = false,
                    exitCode = -1,
                    lines = listOf("Falha ao abrir arquivo: ${t.message}")
                )
            }
        }
    }

    fun dismissPendingInstaller() {
        _pendingInstallerPrompt.value = null
    }

    fun executePendingInstallerInTerminal() {
        val prompt = _pendingInstallerPrompt.value ?: return
        _pendingInstallerPrompt.value = null
        val hostFile = File(distroManager.rootfsDir(prompt.distro.id), prompt.guestPath.trimStart('/'))
        val cmd = buildInstallerCommand(prompt.fileName, prompt.guestPath, prompt.distro, hostFile)
        _selectedTab.value = MainTab.TERMINAL
        createNewTerminalSession(mode = TerminalMode.STANDALONE, initialCommand = cmd)
    }

    fun executePendingInstallerInGui() {
        val prompt = _pendingInstallerPrompt.value ?: return
        _pendingInstallerPrompt.value = null
        val hostFile = File(distroManager.rootfsDir(prompt.distro.id), prompt.guestPath.trimStart('/'))
        val cmd = buildInstallerCommand(prompt.fileName, prompt.guestPath, prompt.distro, hostFile)
        val lower = prompt.fileName.lowercase()

        viewModelScope.launch {
            _selectedTab.value = MainTab.DESKTOP
            desktopController.refreshInstalledState()
            if (!desktopController.isDesktopInstalled.value) {
                _selectedTab.value = MainTab.TERMINAL
                createNewTerminalSession(
                    mode = TerminalMode.DESKTOP_UI,
                    initialCommand = "echo 'Desktop Gráfico ainda não instalado. Executando no Terminal da UI Gráfica...' && $cmd"
                )
                return@launch
            }
            if (!desktopController.isServerRunning.value) {
                startForegroundServiceSafe("Desktop Linux (1024x600)")
                desktopController.startDesktop("1024x600")
                kotlinx.coroutines.delay(800)
            }
            val guiCommand = if (lower.endsWith(".appimage") || lower.endsWith(".run")) {
                cmd
            } else {
                val escaped = cmd.replace("'", "'\\''")
                "xterm -geometry 88x26+20+20 -bg '#0B0F14' -fg '#E6EDF3' -title 'Instalador: ${prompt.fileName}' -e sh -c 'echo \"=== Instalando ${prompt.fileName} ===\"; $escaped; echo; echo \"Concluido! Pressione Enter para fechar.\"; read _'"
            }
            desktopController.launchGuiApp(guiCommand)
        }
    }

    private fun buildInstallerCommand(
        safeName: String,
        guestPath: String,
        distro: DistroDefinition,
        hostFile: File
    ): String {
        val lower = safeName.lowercase()
        val header = ByteArray(16)
        val headerRead = try {
            hostFile.inputStream().use { it.read(header) }
        } catch (_: Throwable) {
            0
        }
        val headerAscii = if (headerRead > 0) String(header, 0, headerRead, Charsets.ISO_8859_1) else ""
        val isDebMagic = headerAscii.startsWith("!<arch>\n")
        val isRpmMagic = headerRead >= 4 &&
            (header[0].toInt() and 0xFF) == 0xED &&
            (header[1].toInt() and 0xFF) == 0xAB &&
            (header[2].toInt() and 0xFF) == 0xEE &&
            (header[3].toInt() and 0xFF) == 0xDB
        val isScriptMagic = headerAscii.startsWith("#!")
        val isElfMagic = headerRead >= 4 &&
            header[0] == 0x7F.toByte() &&
            header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() &&
            header[3] == 'F'.code.toByte()

        return when {
            lower.endsWith(".deb") || isDebMagic -> {
                when (distro.packageManager) {
                    PackageManagerType.APT ->
                        "chmod 1777 /tmp 2>/dev/null || true; " +
                            "dpkg -i '$guestPath' || " +
                            "(apt-get -o APT::Sandbox::User=root update && apt-get -o APT::Sandbox::User=root install -f -y) || " +
                            "(apt-get -o APT::Sandbox::User=root -o Acquire::AllowInsecureRepositories=true update && apt-get -o APT::Sandbox::User=root install -f -y --allow-unauthenticated)"
                    else ->
                        "echo 'Pacotes .deb são para Ubuntu ou Debian.' && echo 'Vá na aba Sistemas, ative Ubuntu ou Debian e abra o arquivo novamente.' && exit 1"
                }
            }
            lower.endsWith(".rpm") || isRpmMagic -> {
                when (distro.packageManager) {
                    PackageManagerType.DNF -> "dnf install -y '$guestPath'"
                    else -> "if command -v rpm >/dev/null 2>&1; then rpm -ivh '$guestPath'; else echo 'Pacotes .rpm são para Fedora.'; exit 1; fi"
                }
            }
            lower.endsWith(".apk") -> {
                "apk add --allow-untrusted '$guestPath'"
            }
            lower.endsWith(".pkg.tar.zst") || lower.endsWith(".pkg.tar.xz") -> {
                "pacman -U --noconfirm '$guestPath'"
            }
            lower.endsWith(".appimage") || isElfMagic -> {
                "chmod +x '$guestPath' && APPIMAGE_EXTRACT_AND_RUN=1 ELECTRON_DISABLE_SANDBOX=1 '$guestPath' --no-sandbox --disable-dev-shm-usage"
            }
            lower.endsWith(".sh") || lower.endsWith(".run") || isScriptMagic -> {
                "chmod +x '$guestPath' && sh '$guestPath'"
            }
            else -> {
                "chmod +x '$guestPath' && '$guestPath'"
            }
        }
    }

    fun checkForAppUpdate(silent: Boolean = false) {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            if (!silent) {
                _updateStatusMessage.value = "Verificando atualizações no GitHub..."
            }
            val update = GitHubUpdater.checkForUpdate(BuildConfig.VERSION_NAME)
            _availableUpdate.value = update
            _isCheckingUpdate.value = false
            if (update != null) {
                _updateStatusMessage.value = "Nova versão disponível: ${update.tagName}"
            } else if (!silent) {
                _updateStatusMessage.value = "Você já está na versão mais recente (v${BuildConfig.VERSION_NAME})."
            }
        }
    }

    fun dismissUpdateDialog() {
        if (_updateDownloadProgress.value == null) {
            _availableUpdate.value = null
        }
    }

    fun downloadAndInstallUpdate() {
        val update = _availableUpdate.value ?: return
        val url = update.apkDownloadUrl
        if (url.isNullOrBlank()) {
            openUrlInBrowser(update.releaseHtmlUrl)
            return
        }
        viewModelScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !app.packageManager.canRequestPackageInstalls()
                ) {
                    _updateStatusMessage.value = "Permita instalar apps pelo LinuxBox nas configurações e tente novamente."
                    val permIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${app.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    app.startActivity(permIntent)
                    return@launch
                }

                _updateDownloadProgress.value = 0.01f
                _updateStatusMessage.value = "Baixando ${update.tagName} do GitHub..."
                val apkFile = GitHubUpdater.downloadUpdateApk(app, url) { downloaded, total ->
                    _updateDownloadProgress.value = if (total > 0) {
                        (downloaded.toFloat() / total.toFloat()).coerceIn(0.01f, 0.99f)
                    } else {
                        0.5f
                    }
                }
                _updateDownloadProgress.value = null
                _updateStatusMessage.value = "Abrindo instalador do Android..."
                val installIntent = GitHubUpdater.buildInstallApkIntent(app, apkFile)
                app.startActivity(installIntent)
            } catch (t: Throwable) {
                _updateDownloadProgress.value = null
                _updateStatusMessage.value = "Erro ao baixar atualização: ${t.message}"
                openUrlInBrowser(update.releaseHtmlUrl)
            }
        }
    }

    fun openUrlInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun dismissConsoleTask() {
        if (_consoleTask.value?.isRunning == true) {
            activeConsoleJob?.cancel()
        }
        _consoleTask.value = null
    }

    private fun runConsoleTask(
        title: String,
        command: String,
        launchAfter: String?
    ) {
        activeConsoleJob?.cancel()
        val distro = _activeDistro.value
        val logLines = mutableListOf("$ $command")
        _consoleTask.value = ConsoleTaskState(
            title = title,
            commandPreview = command,
            isRunning = true,
            lines = logLines.toList(),
            launchAfterCommand = launchAfter
        )

        activeConsoleJob = viewModelScope.launch {
            try {
                val result = commandRunner.runShellCommand(command, distro = distro) { line ->
                    synchronized(logLines) {
                        logLines.add(line)
                        if (logLines.size > 400) {
                            logLines.removeAt(0)
                        }
                        _consoleTask.value = _consoleTask.value?.copy(lines = logLines.toList())
                    }
                }
                _consoleTask.value = _consoleTask.value?.copy(
                    isRunning = false,
                    exitCode = result.exitCode,
                    lines = logLines.toList()
                )
                refreshInstalledPackages()
                desktopController.refreshInstalledState()
                refreshDistroStates()
                if (_repoSearchResults.value.isNotEmpty()) {
                    searchRepositoryPackages()
                }
            } catch (t: Throwable) {
                logLines.add("ERRO: ${t.message ?: "operação interrompida"}")
                _consoleTask.value = _consoleTask.value?.copy(
                    isRunning = false,
                    exitCode = -1,
                    lines = logLines.toList()
                )
            }
        }
    }

    fun refreshDistroStates() {
        viewModelScope.launch {
            _distroStates.value = distroManager.listDistroStates()
        }
    }

    fun installOrReinstallDistro(distro: DistroDefinition) {
        if (_distroTaskProgress.value != null) return
        viewModelScope.launch {
            _distroError.value = null
            try {
                distroManager.installDistro(distro) { progress ->
                    _distroTaskProgress.value = distro.id to progress
                }
                _distroTaskProgress.value = null
                activateDistro(distro)
            } catch (t: Throwable) {
                _distroTaskProgress.value = null
                _distroError.value = "Falha ao instalar ${distro.name}: ${t.message}"
                refreshDistroStates()
            }
        }
    }

    fun activateDistro(distro: DistroDefinition) {
        if (!distroManager.isInstalled(distro.id)) return
        distroManager.activeDistroId = distro.id
        _activeDistro.value = distro
        _installedPackages.value = emptySet()
        _repoSearchResults.value = emptyList()
        desktopController.stopDesktop()
        sessionManager.createSession(distro = distro)
        refreshDistroStates()
        refreshInstalledPackages()
        viewModelScope.launch { desktopController.refreshInstalledState() }
    }

    fun removeDistro(distro: DistroDefinition) {
        viewModelScope.launch {
            if (_activeDistro.value.id == distro.id) {
                desktopController.stopDesktop()
                sessionManager.closeAll()
            }
            distroManager.removeDistro(distro.id)
            val newActive = distroManager.getActiveDistro()
            _activeDistro.value = newActive
            if (distroManager.isInstalled(newActive.id) && sessionManager.sessions.value.isEmpty()) {
                sessionManager.createSession(distro = newActive)
            }
            refreshDistroStates()
        }
    }

    fun setMountAndroidStorage(enabled: Boolean) {
        distroManager.mountAndroidStorage = enabled
        _mountAndroidStorage.value = enabled
    }

    fun setDisableSeccomp(disabled: Boolean) {
        distroManager.disableSeccomp = disabled
        _disableSeccomp.value = disabled
    }

    fun clearDistroError() {
        _distroError.value = null
    }

    private fun startForegroundServiceSafe(status: String) {
        try {
            LinuxSessionService.start(app, status)
        } catch (_: Throwable) {
            // Android 12+ background start restrictions if app is not foregrounded
        }
    }
}
