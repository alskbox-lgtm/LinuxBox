package com.linuxbox.android.terminal

import com.linuxbox.android.core.DistroDefinition
import com.linuxbox.android.core.DistroManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

class SessionManager(private val distroManager: DistroManager) {

    private val counter = AtomicInteger(0)

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    fun activeSession(): TerminalSession? {
        val id = _activeSessionId.value
        return _sessions.value.firstOrNull { it.id == id } ?: _sessions.value.firstOrNull()
    }

    fun createSession(
        distro: DistroDefinition = distroManager.getActiveDistro(),
        initialCommand: String? = null,
        rows: Int = 28,
        cols: Int = 80
    ): TerminalSession {
        val index = counter.incrementAndGet()
        val session = TerminalSession(
            id = "term-$index",
            distro = distro,
            distroManager = distroManager,
            initialCommand = initialCommand,
            initialRows = rows,
            initialCols = cols
        )
        _sessions.value = _sessions.value + session
        _activeSessionId.value = session.id
        return session
    }

    fun selectSession(id: String) {
        if (_sessions.value.any { it.id == id }) {
            _activeSessionId.value = id
        }
    }

    fun closeSession(id: String) {
        val target = _sessions.value.firstOrNull { it.id == id } ?: return
        target.close()
        val remaining = _sessions.value.filterNot { it.id == id }
        _sessions.value = remaining
        if (_activeSessionId.value == id) {
            _activeSessionId.value = remaining.lastOrNull()?.id
        }
    }

    fun runInTerminal(command: String) {
        val current = activeSession()
        if (current != null && current.isRunning.value) {
            current.sendText("$command\r")
        } else {
            createSession(initialCommand = command)
        }
    }

    fun closeAll() {
        _sessions.value.forEach { it.close() }
        _sessions.value = emptyList()
        _activeSessionId.value = null
    }
}
