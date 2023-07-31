package org.kylchik.gitworktreecheckout.worktree

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.tasks.context.WorkingContextProvider
import git4idea.repo.GitRepositoryManager
import org.jdom.Element
import java.io.FileFilter


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
        val currentProjectPath = gitRepositoryManager.repositories[0].root
        val gitWorktreeDir = gitRepositoryManager.repositories.first().repositoryFiles.worktreesDirFile
        val mainRepositoryPath = gitWorktreeDir.parentFile.parentFile
        val locationOfGitDirInWorktrees = gitWorktreeDir.listFiles()?.mapNotNull { worktreeGit ->
            worktreeGit.listFiles(FileFilter { it.path.endsWith("gitdir") })
                ?.single()
                ?.readLines()
                ?.first()
        } ?: return
        val worktreesPaths = locationOfGitDirInWorktrees.map { it.removeSuffix("/.git") }
        val allProjects = listOf(mainRepositoryPath.absolutePath) + worktreesPaths
        val allProjectsExceptCurrent = allProjects.filter { it != currentProjectPath.path }.map { "$it/" }

        val fileEditorManager = FileEditorManager.getInstance(project)
        val openedFiles = fileEditorManager.openFiles

        val filesWithWrongPath = openedFiles.filter {
            openedFile -> allProjectsExceptCurrent.any { worktree -> openedFile.path.startsWith(worktree) }
        }
        if (filesWithWrongPath.isNotEmpty()) {
            val notification = NOTIFICATION.createNotification("Some wrong files were detected", NotificationType.INFORMATION)
            notification.addAction(object : NotificationAction("Change path") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    println("Action clicked")
                }
            }).notify(project)
        }
    }
}