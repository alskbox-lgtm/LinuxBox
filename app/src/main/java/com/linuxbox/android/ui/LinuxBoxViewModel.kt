package com.linuxbox.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linuxbox.android.LinuxBoxApp
import com.linuxbox.android.core.DistroDefinition
import com.linuxbox.android.core.DistroInstallState
import com.linuxbox.android.core.LinuxProgram
import com.linuxbox.android.core.PackageCatalog
import com.linuxbox.android.core.ProgramCategory
import com.linuxbox.android.core.RepoSearchResult
import com.linuxbox.android.core.SetupProgress
import com.linuxbox.android.service.LinuxSessionService
import com.linuxbox.android.terminal.TerminalSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // Engine settings
    private val _mountAndroidStorage = MutableStateFlow(distroManager.mountAndroidStorage)
    val mountAndroidStorage: StateFlow<Boolean> = _mountAndroidStorage.asStateFlow()

    private val _disableSeccomp = MutableStateFlow(distroManager.disableSeccomp)
    val disableSeccomp: StateFlow<Boolean> = _disableSeccomp.asStateFlow()

    private var activeConsoleJob: Job? = null

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
                    sessionManager.createSession(distro = distro)
                }
                startForegroundServiceSafe("${distro.name} ${distro.version} ativo")
                _isBootstrapping.value = false
                refreshInstalledPackages()
                desktopController.refreshInstalledState()
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

    fun createNewTerminalSession(initialCommand: String? = null): TerminalSession {
        val distro = _activeDistro.value
        val session = sessionManager.createSession(distro = distro, initialCommand = initialCommand)
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

    fun runCommandInTerminal(command: String) {
        _selectedTab.value = MainTab.TERMINAL
        sessionManager.runInTerminal(command)
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
