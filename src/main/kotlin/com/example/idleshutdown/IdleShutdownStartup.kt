package com.example.idleshutdown

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class IdleShutdownStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        service<IdleWatchdog>().start() // idempotent; runs once per IDE session
    }
}
