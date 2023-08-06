package org.kylchik.gitworktreecheckout.worktree

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.tasks.context.WorkingContextProvider
import git4idea.repo.GitRepositoryManager
import org.jdom.Element
import java.io.File
import java.io.FileFilter
import kotlin.io.path.Path

class MyContextProvider: WorkingContextProvider() {
    override fun getId(): String = "test"
    override fun getDescription(): String = ""

    private val NOTIFICATION = NotificationGroupManager.getInstance()
        .getNotificationGroup("MyContextProvider Notification Group")

    override fun saveContext(project: Project, toElement: Element) {
//        println("Saved")
    }

    override fun loadContext(project: Project, fromElement: Element) {
        val gitRepositoryManager = GitRepositoryManager.getInstance(project)
        val currentProjectPath = gitRepositoryManager.repositories[0].root.path + File.separator
        val gitWorktreeDir = gitRepositoryManager.repositories.first().repositoryFiles.worktreesDirFile
        val mainRepositoryPath = gitWorktreeDir.parentFile.parentFile.absolutePath + File.separator
        val locationOfGitDirInWorktrees = gitWorktreeDir.listFiles()?.mapNotNull { worktreeGit ->
            worktreeGit.listFiles(FileFilter { it.path.endsWith("gitdir") })
                ?.single()
                ?.readLines()
                ?.first()
        } ?: return
        val worktreesPaths = locationOfGitDirInWorktrees.map { it.removeSuffix(".git") }
        val allProjects = listOf(mainRepositoryPath) + worktreesPaths
        val allProjectsExceptCurrent = allProjects.filter { it != currentProjectPath }

        val fileEditorManager = FileEditorManager.getInstance(project)
        val openedFiles = fileEditorManager.openFiles

        val filesWithWrongPath = openedFiles.filter {
            openedFile -> allProjectsExceptCurrent.any { worktree -> openedFile.path.startsWith(worktree) }
        }
        if (filesWithWrongPath.isNotEmpty()) {
            val notification = NOTIFICATION.createNotification("Some wrong files were detected", NotificationType.INFORMATION)
            notification.addAction(object : NotificationAction("Change path") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    openedFiles.forEach { openedFile ->
                        fileEditorManager.closeFile(openedFile)

                        val wrongPath = allProjectsExceptCurrent.firstOrNull { worktree -> openedFile.path.startsWith(worktree) }
                        val fileWithCorrectPath = if (wrongPath != null) {
                            val correctPath = Path(openedFile.path.replace(wrongPath, currentProjectPath))
                            VfsUtil.findFile(correctPath, true) ?: openedFile
                        } else {
                            openedFile
                        }

                        fileEditorManager.openFile(fileWithCorrectPath, false)
                    }
                }
            }).notify(project)
        }
    }
}