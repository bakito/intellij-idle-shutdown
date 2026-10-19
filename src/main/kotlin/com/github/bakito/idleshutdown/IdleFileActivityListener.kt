package com.github.bakito.idleshutdown

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/** Treats opening, closing and switching editor tabs as user activity. */
class IdleFileActivityListener : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) = activity()

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) = activity()

    override fun selectionChanged(event: FileEditorManagerEvent) = activity()

    private fun activity() = service<IdleWatchdog>().onUserActivity()
}
