package org.kylchik.gitworktreecheckout.worktree

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.tasks.context.WorkingContextProvider
import git4idea.repo.GitRepositoryManager
import org.jdom.Element
import java.io.File
import java.io.FileFilter
import kotlin.io.path.Path

class GitWorktreePathFixer: WorkingContextProvider() {
    override fun getId(): String = "GitWorktreePathFixer"
    override fun getDescription(): String = "Change restored workspace to match opened project"

    private val NOTIFICATION = NotificationGroupManager.getInstance()
        .getNotificationGroup("GitWorktreePathFixer Notification Group")

    override fun saveContext(project: Project, toElement: Element) {}

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
            NOTIFICATION
                .createNotification(message, NotificationType.INFORMATION)
                .addAction(ChangePathAction(currentProjectPath, allProjectsExceptCurrent))
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
    private val currentProjectPath: String,
    private val allProjectsExceptCurrent: List<String>
) : NotificationAction("Fix path") {
    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        val project = e.project ?: return
        val fileEditorManager = FileEditorManager.getInstance(project)
        val openedFiles = fileEditorManager.openFiles
        val selectedFile = fileEditorManager.selectedEditor?.file
        var fileThatShouldBeInFocus: VirtualFile? = null

        openedFiles.forEach { openedFile ->
            fileEditorManager.closeFile(openedFile)

            val wrongPath = allProjectsExceptCurrent.firstOrNull { worktree -> openedFile.path.startsWith(worktree) }
            val fileWithCorrectPath = if (wrongPath != null) {
                val correctPath = Path(openedFile.path.replace(wrongPath, currentProjectPath))
                VfsUtil.findFile(correctPath, true) ?: openedFile
            } else {
                openedFile
            }

            if (selectedFile == openedFile) fileThatShouldBeInFocus = fileWithCorrectPath
            fileEditorManager.openFile(fileWithCorrectPath, false)
        }

        if (fileThatShouldBeInFocus != null) {
            fileEditorManager.openFile(fileThatShouldBeInFocus!!, false, true)
        }
    }
}