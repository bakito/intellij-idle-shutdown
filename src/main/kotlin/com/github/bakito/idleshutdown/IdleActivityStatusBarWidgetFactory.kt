package com.github.bakito.idleshutdown

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class IdleActivityStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId() = IdleActivityStatusBarWidget.ID

    override fun getDisplayName() = "Idle Activity"

    override fun isAvailable(project: Project): Boolean {
        val settings = service<IdleShutdownSettings>()
        val idleEnabled = settings.isTimeoutFilePresent() || settings.state.enabled
        return idleEnabled && settings.state.showLastActivityInStatusbar
    }

    override fun createWidget(project: Project): StatusBarWidget =
        IdleActivityStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        (widget as? IdleActivityStatusBarWidget)?.dispose()
    }

    override fun canBeEnabledOn(statusBar: com.intellij.openapi.wm.StatusBar) = false
}
