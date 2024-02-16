package org.kylchik.gitworktreecheckout.worktree

import com.intellij.ide.DataManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.stateStore
import git4idea.test.*
import git4idea.test.createRepository
import git4idea.test.git
import junit.framework.TestCase
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class GitWorktreePathFixerTest: GitPlatformTest() {
    private val mainProjectPath: Path
        get() = Paths.get(project.basePath!!)

    private val projectNioRoot: Path
        get() = project.stateStore.getProjectBasePath()

    fun test0() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create a file
        createFile(mainProjectPath, "file.txt", "")

        // 4. Commit a file
        addCommit("add new file `file.txt`")

        // 5. Create new worktree "B" with new branch "worktree_branch"
        git(project, "worktree add -b worktree_branch ./worktree-project")

        // 7. Open a file that was previously created in project "A"
        val fileEditorManager = FileEditorManager.getInstance(project)

        val worktreeProjectPath = mainProjectPath.resolve("worktree-project")
        val txtFileInB = VfsUtil.findFileByIoFile(worktreeProjectPath.resolve("file.txt").toFile(), true)!!
        val descriptor = OpenFileDescriptor(project, txtFileInB)
        fileEditorManager.openFileEditor(descriptor, true)
        TestCase.assertEquals(1, fileEditorManager.openFiles.size)

        // 8. Fix paths
        val notifications = mutableListOf<Notification>()
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications += notification
            }
        })

        TestCase.assertTrue(fileEditorManager.openFiles.first().path.endsWith("worktree-project${File.separator}file.txt"))
        GitWorktreePathFixer().process(project)

        // 9. Check the result
        TestCase.assertEquals(1, notifications.size)
        val notification = notifications.first()
        val changePathAction = notification.actions.first()
        // TODO use `TestActionEvent.createTestEvent` instead of `AnActionEvent.createFromAnAction`
        val event = AnActionEvent.createFromAnAction(changePathAction, null, "", DataManager.getInstance().dataContext)
        (changePathAction as? NotificationAction)?.actionPerformed(event, notification)

        TestCase.assertTrue(fileEditorManager.openFiles.first().path.endsWith("file.txt"))
        TestCase.assertFalse(fileEditorManager.openFiles.first().path.endsWith("worktree-project${File.separator}file.txt"))
    }

    fun test1() {
        // create project A
        // create new branch A1
        // create an open some files in branch A1
        // create new branch A2 and checkout
        // create project B
        // checkout to branch A1
        // check that notification appear
        // click action button
        // check that path to files changed
    }

    fun test2() {
        // the same as test1, but we have some files not from any project
        // check that these files have the same path as before action click
    }

    fun test3() {
        // the same as test1
        // in project B, checkout to branch B2
        // in project A, checkout back to A1
        // check that notification appear
        // click action button
        // check that path to files changed
    }

    fun test4() {
        // create project A
        // create new branch A1
        // create an open some files in branch A1
        // create new branch A2 and checkout
        // checkout back to A1
        // notification must not appear
    }
}