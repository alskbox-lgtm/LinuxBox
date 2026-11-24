package com.linuxbox.android

import android.app.Application
import com.linuxbox.android.core.DistroManager
import com.linuxbox.android.core.LinuxCommandRunner
import com.linuxbox.android.desktop.DesktopController
import com.linuxbox.android.terminal.SessionManager

class LinuxBoxApp : Application() {

    lateinit var distroManager: DistroManager
        private set

    lateinit var commandRunner: LinuxCommandRunner
        private set

    lateinit var sessionManager: SessionManager
        private set

    lateinit var desktopController: DesktopController
        private set

    override fun onCreate() {
        super.onCreate()
        distroManager = DistroManager(this)
        commandRunner = LinuxCommandRunner(distroManager)
        sessionManager = SessionManager(distroManager)
        desktopController = DesktopController(distroManager, commandRunner)
    }

    override fun onTerminate() {
        desktopController.stopDesktop()
        sessionManager.closeAll()
        super.onTerminate()
    }
}
