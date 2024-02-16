package git4idea.test

import com.intellij.ide.DataManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.io.write
import com.intellij.util.messages.Topic
import junit.framework.TestCase
import org.kylchik.gitworktreecheckout.worktree.GitWorktreePathFixer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class GitPlatformTest : HeavyPlatformTestCase() {
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
        container: MutableList<Notification>,
        topic: Topic<Notifications> = Notifications.TOPIC
    ) {
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(topic, object : Notifications {
            override fun notify(notification: Notification) {
                container += notification
            }
        })
    }

    protected fun fixPath() {
        val notifications = mutableListOf<Notification>()
        subscribeToNotifications(notifications)

        GitWorktreePathFixer().process(project)

        TestCase.assertEquals(1, notifications.size)
        notifications.first().callAction()
    }

    private fun Notification.callAction() {
        val changePathAction = this.actions.first()
        // TODO use `TestActionEvent.createTestEvent` instead of `AnActionEvent.createFromAnAction`
        val event = AnActionEvent.createFromAnAction(changePathAction, null, "", DataManager.getInstance().dataContext)
        (changePathAction as? NotificationAction)?.actionPerformed(event, this)
    }
}
