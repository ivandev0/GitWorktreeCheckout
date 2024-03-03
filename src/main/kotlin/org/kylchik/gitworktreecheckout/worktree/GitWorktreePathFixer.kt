package org.kylchik.gitworktreecheckout.worktree

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorSplitterState
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.tasks.context.WorkingContextProvider
import git4idea.repo.GitRepositoryManager
import org.jdom.Element
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File
import java.io.FileFilter

private fun getProdFileEditorManager(project: Project): FileEditorManagerImpl? {
    return FileEditorManager.getInstance(project) as? FileEditorManagerImpl
}

class GitWorktreePathFixer: WorkingContextProvider() {
    override fun getId(): String = "GitWorktreePathFixer"
    override fun getDescription(): String = "Change restored workspace to match opened project"

    private val NOTIFICATION = NotificationGroupManager.getInstance()
        .getNotificationGroup("Notification from `Git Worktree Checkout` plugin")

    override fun saveContext(project: Project, toElement: Element) {
        val fileEditorManager = getProdFileEditorManager(project)
        fileEditorManager?.mainSplitters?.writeExternal(toElement)
    }

    override fun loadContext(project: Project, fromElement: Element) {
        val gitRepositoryManager = GitRepositoryManager.getInstance(project)
        val currentProjectPath = gitRepositoryManager.repositories[0].root.path + File.separator
        val allProjects = gitRepositoryManager.allProjectsOfGivenGit() ?: return
        val allProjectsExceptCurrent = allProjects.filter { it != currentProjectPath }
        if (allProjectsExceptCurrent.isEmpty()) return

        val fileEditorManager = FileEditorManager.getInstance(project)
        val openedFiles = fileEditorManager.openFiles

        val filesWithWrongPath = openedFiles.filter {
            openedFile -> allProjectsExceptCurrent.any { worktree -> openedFile.path.startsWith(worktree) }
        }

        if (filesWithWrongPath.isNotEmpty()) {
            val message = "There are files with wrong path that were opened in different worktree"
            val projectsPaths = buildList {
                add(currentProjectPath)
                addAll(allProjectsExceptCurrent.sortedDescending())
            }
            NOTIFICATION
                .createNotification(message, NotificationType.INFORMATION)
                .addAction(ChangePathAction(fromElement, projectsPaths))
                .notify(project)
        }
    }

    private fun GitRepositoryManager.allProjectsOfGivenGit(): List<String>? {
        val gitWorktreeDir = this.repositories.first().repositoryFiles.worktreesDirFile
        val mainRepositoryPath = gitWorktreeDir.parentFile.parentFile.absolutePath + File.separator
        val locationOfGitDirInWorktrees = gitWorktreeDir.listFiles()?.mapNotNull { worktreeGit ->
            worktreeGit.listFiles(FileFilter { it.path.endsWith("gitdir") })
                ?.single()
                ?.readLines()
                ?.first()
        } ?: return null
        val worktreesPaths = locationOfGitDirInWorktrees.map { it.removeSuffix(".git") }
        return listOf(mainRepositoryPath) + worktreesPaths
    }
}

private class ChangePathAction(
    private val element: Element,
    private val projectsPaths: List<String>
) : NotificationAction("Fix path") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val project = e.project ?: return
        val fileEditorManager = getProdFileEditorManager(project) ?: return

        runWithModalProgressBlocking(project, "Fixing editors after branch switch") {
            reportRawProgress {
                fixPathsInElement(element, projectsPaths)

                @Suppress("UnstableApiUsage")
                fileEditorManager.mainSplitters.restoreEditors(EditorSplitterState(element), editorHadFocus())
            }
        }

        notification.expire()
    }

    // Copied from `OpenEditorsContextProvider`
    private fun editorHadFocus(): Boolean {
        var c = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        while (c != null) {
            if (c is Window) return false
            if (c is EditorsSplitters) return true
            c = c.parent
        }
        return true // if editor was focused, but was closed by `clearContext` call, we'll get here
    }
}

private fun fixPath(projectsPaths: List<String>, oldPath: String): String {
    val currentProjectPath = projectsPaths.first()
    val allProjectsExceptCurrent = projectsPaths.drop(1)

    val wrongPath = allProjectsExceptCurrent.firstOrNull { worktree -> oldPath.startsWith("file://$worktree") }
    if (wrongPath != null) {
        return oldPath.replace(wrongPath, currentProjectPath)
    }
    return oldPath
}

fun fixPathsInElement(element: Element, projectsPaths: List<String>) {
    object : ElementVisitor() {
        override fun visitFile(file: Element) {
            val entry = file.getChild("entry")
            val oldPath = entry.getAttributeValue("file")
            val newPath = fixPath(projectsPaths, oldPath)
            entry.setAttribute("file", newPath)
        }
    }.visit(element)
}
