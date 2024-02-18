package git4idea.test

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.testFramework.replaceService
import com.intellij.util.io.write
import com.intellij.util.messages.Topic
import git4idea.commands.Git
import junit.framework.TestCase
import org.kylchik.gitworktreecheckout.worktree.GitWorktreePathFixer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class GitPlatformTest : HeavyPlatformTestCase() {
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        val vcsHelper = MockVcsHelper(myProject)
        project.replaceService(AbstractVcsHelper::class.java, vcsHelper, testRootDisposable)

        val git = TestGitImpl()
        ApplicationManager.getApplication().replaceService(Git::class.java, git, testRootDisposable)
    }

    protected fun createWorktree(branchName: String): Project {
        git(project, "worktree add -b $branchName ./$branchName")
        // NB: update is required. When we create a new worktree, this triggers `VCS_CONFIGURATION_CHANGED` event
        // and `VcsRepositoryManager` starts to update. This captures the `WRITE_LOCK` that is never going to be released.
        // When later we will call `registerRepo` method, it also tries to capture the same lock and deadlock appears.
        StandardFileSystems.local().refresh(true)

        val worktreeProjectPath = Paths.get(project.basePath!!).resolve(branchName)
        val projectB = ProjectManagerEx.getInstanceEx().openProject(
            worktreeProjectPath,
            OpenProjectTaskBuilder().projectName(project.name).build()
        )!!
        registerRepo(projectB, worktreeProjectPath)

        return projectB
    }

    protected fun createFile(dir: String, fileName: String, content: String = ""): VirtualFile {
        return createFile(Paths.get(dir), fileName, content)
    }

    protected fun createFile(dir: Path, fileName: String, content: String = ""): VirtualFile {
        val file = createTempFile(dir, fileName, content)
        val stream = FileOutputStream(file)
        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
        return getVirtualFile(file)
    }

    private fun createTempFile(dir: Path, fileName: String, text: String?): File {
        val file = dir.resolve(fileName)
        if (text == null) {
            Files.createDirectories(dir)
            Files.createFile(file)
        } else {
            file.write(text)
        }
        return file.toFile()
    }

    protected fun FileEditorManager.openFiles(vararg filePath: Path): List<FileEditor> {
        return filePath.flatMap { openFile(it) }
    }

    protected fun FileEditorManager.openFile(filePath: Path): List<FileEditor> {
        val txtFileInB = VfsUtil.findFileByIoFile(filePath.toFile(), true)!!
        val descriptor = OpenFileDescriptor(project, txtFileInB)
        return this.openFileEditor(descriptor, true)
    }

    protected fun FileEditorManager.closeAllOpenedFiles() {
        openFiles.forEach { closeFile(it) }
    }

    protected fun subscribeToNotifications(
        projectRef: Project,
        container: MutableList<Notification>,
        topic: Topic<Notifications> = Notifications.TOPIC
    ) {
        val connection = projectRef.messageBus.connect(testRootDisposable)
        connection.subscribe(topic, object : Notifications {
            override fun notify(notification: Notification) {
                container += notification
            }
        })
    }

    protected fun fixPathFor(projectRef: Project) {
        var notifications = mutableListOf<Notification>()
        subscribeToNotifications(projectRef, notifications)

        GitWorktreePathFixer().process(projectRef)

        notifications = notifications
            .filter { !it.groupId.contains("Externally added files can be added to") }
            .toMutableList()
        TestCase.assertEquals(1, notifications.size)
        notifications.first().callAction(projectRef)
    }

    private fun Notification.callAction(projectRef: Project) {
        val changePathAction = this.actions.first()
        // TODO use `TestActionEvent.createTestEvent` instead of `AnActionEvent.createFromAnAction`
        val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, projectRef).build()
        val event = AnActionEvent.createFromAnAction(changePathAction, null, "", dataContext)
        (changePathAction as? NotificationAction)?.actionPerformed(event, this)
    }
}
