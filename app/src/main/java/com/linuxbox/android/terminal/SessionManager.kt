package com.linuxbox.android.terminal

import com.linuxbox.android.core.DistroDefinition
import com.linuxbox.android.core.DistroManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages bifurcated terminal sessions:
 * - [TerminalMode.STANDALONE]: Pure isolated CLI sessions
 * - [TerminalMode.DESKTOP_UI]: Sessions connected to the X11 Graphical Desktop (DISPLAY=127.0.0.1:10)
 */
class SessionManager(private val distroManager: DistroManager) {

    private val counter = AtomicInteger(0)

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _selectedMode = MutableStateFlow(TerminalMode.STANDALONE)
    val selectedMode: StateFlow<TerminalMode> = _selectedMode.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val lastActiveByMode = mutableMapOf<TerminalMode, String>()

    fun sessionsForMode(mode: TerminalMode): List<TerminalSession> =
        _sessions.value.filter { it.mode == mode }

    fun activeSession(mode: TerminalMode = _selectedMode.value): TerminalSession? {
        val modeList = sessionsForMode(mode)
        val preferredId = if (mode == _selectedMode.value) _activeSessionId.value else lastActiveByMode[mode]
        return modeList.firstOrNull { it.id == preferredId } ?: modeList.lastOrNull()
    }

    fun selectMode(mode: TerminalMode, autoCreateIfEmpty: Boolean = true) {
        _selectedMode.value = mode
        val existing = activeSession(mode)
        if (existing != null) {
            _activeSessionId.value = existing.id
            lastActiveByMode[mode] = existing.id
        } else if (autoCreateIfEmpty) {
            createSession(mode = mode)
        } else {
            _activeSessionId.value = null
        }
    }

    fun createSession(
        distro: DistroDefinition = distroManager.getActiveDistro(),
        mode: TerminalMode = _selectedMode.value,
        initialCommand: String? = null,
        rows: Int = 28,
        cols: Int = 80
    ): TerminalSession {
        val index = counter.incrementAndGet()
        val session = TerminalSession(
            id = "term-$index",
            distro = distro,
            distroManager = distroManager,
            mode = mode,
            initialCommand = initialCommand,
            initialRows = rows,
            initialCols = cols
        )
        _sessions.value = _sessions.value + session
        _selectedMode.value = mode
        _activeSessionId.value = session.id
        lastActiveByMode[mode] = session.id
        return session
    }

    fun selectSession(id: String) {
        val target = _sessions.value.firstOrNull { it.id == id } ?: return
        _selectedMode.value = target.mode
        _activeSessionId.value = target.id
        lastActiveByMode[target.mode] = target.id
    }

    fun closeSession(id: String) {
        val target = _sessions.value.firstOrNull { it.id == id } ?: return
        target.close()
        val remaining = _sessions.value.filterNot { it.id == id }
        _sessions.value = remaining
        val modeRemaining = remaining.filter { it.mode == target.mode }
        if (lastActiveByMode[target.mode] == id) {
            val next = modeRemaining.lastOrNull()?.id
            if (next != null) lastActiveByMode[target.mode] = next else lastActiveByMode.remove(target.mode)
        }
        if (_activeSessionId.value == id) {
            _activeSessionId.value = modeRemaining.lastOrNull()?.id
        }
    }

    fun runInTerminal(
        command: String,
        mode: TerminalMode = _selectedMode.value
    ) {
        _selectedMode.value = mode
        val current = activeSession(mode)
        if (current != null && current.isRunning.value) {
            _activeSessionId.value = current.id
            lastActiveByMode[mode] = current.id
            current.sendText("$command\r")
        } else {
            createSession(mode = mode, initialCommand = command)
        }
    }

    fun closeAll() {
        _sessions.value.forEach { it.close() }
        _sessions.value = emptyList()
        _activeSessionId.value = null
        lastActiveByMode.clear()
    }
}
