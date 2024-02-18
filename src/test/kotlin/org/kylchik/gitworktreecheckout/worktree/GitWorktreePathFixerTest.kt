package org.kylchik.gitworktreecheckout.worktree

import com.intellij.notification.Notification
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vcs.Executor
import com.intellij.project.stateStore
import git4idea.test.GitPlatformTest
import git4idea.test.addCommit
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

    override fun tearDown() {
        FileEditorManager.getInstance(project).closeAllOpenedFiles()
        super.tearDown()
    }

    fun `test path is fixed for single file from worktree`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create a file
        createFile(mainProjectPath, "file.txt")

        // 4. Commit a file
        addCommit(project, "add new file `file.txt`")

        // 5. Create new worktree "B" with new branch "worktree-project"
        val worktreeProjectName = "worktree-project"
        val projectB = createWorktree(worktreeProjectName)
        val worktreeProjectPath = Paths.get(projectB.basePath!!)

        // 6. Open a file previously created in project "A"
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(worktreeProjectPath.resolve("file.txt"))
        TestCase.assertEquals(1, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.first().path.endsWith("$worktreeProjectName${File.separator}file.txt"))

        // 7. Fix paths
        fixPathFor(project)

        // 8. Check the result
        TestCase.assertTrue(fileEditorManager.openFiles.first().path.endsWith("file.txt"))
        TestCase.assertFalse(fileEditorManager.openFiles.first().path.endsWith("$worktreeProjectName${File.separator}file.txt"))

        // Tear down for `projectB`
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
    }

    fun `test path is fixed for multiple files from worktree`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create files
        createFile(mainProjectPath, "file.txt")
        createFile(mainProjectPath.resolve("subDir"), "fileInSubDir.txt")
        createFile(mainProjectPath.resolve("subDir"), "secondFileInSubDir.txt")
        createFile(mainProjectPath.resolve("subDir").resolve("subSubDir"), "fileInSubSubDir.txt")

        // 4. Commit a file
        addCommit(project, "add new files")

        // 5. Create new worktree "B" with new branch "worktree-project"
        val worktreeProjectName = "worktree-project"
        val projectB = createWorktree(worktreeProjectName)
        val worktreeProjectPath = Paths.get(projectB.basePath!!)

        // 6. Open files that were previously created in project "A"
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles(
            worktreeProjectPath.resolve("file.txt"),
            worktreeProjectPath.resolve("subDir").resolve("fileInSubDir.txt"),
            worktreeProjectPath.resolve("subDir").resolve("secondFileInSubDir.txt"),
            worktreeProjectPath.resolve("subDir").resolve("subSubDir").resolve("fileInSubSubDir.txt")
        )
        TestCase.assertEquals(4, fileEditorManager.openFiles.size)

        // 7. Fix paths
        fixPathFor(project)

        // 8. Check the result
        TestCase.assertEquals(4, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.none { it.path.contains(worktreeProjectName) })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path.endsWith("file.txt") })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path.endsWith("subDir${File.separator}fileInSubDir.txt") })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path.endsWith("subDir${File.separator}secondFileInSubDir.txt") })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path.endsWith("subDir${File.separator}subSubDir${File.separator}fileInSubSubDir.txt") })

        // Tear down for `projectB`
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
    }

    fun `test path is fixed for single file from main repo`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create a file
        createFile(mainProjectPath, "file.txt")

        // 4. Commit a file
        addCommit(project, "add new file `file.txt`")

        // 5. Create new worktree "B" with new branch "worktree-project"
        val worktreeProjectName = "worktree-project"
        val projectB = createWorktree(worktreeProjectName)

        // 6. Open a file previously created in project "A" as it was opened in "A"
        val fileEditorManager = FileEditorManager.getInstance(projectB)
        fileEditorManager.openFile(mainProjectPath.resolve("file.txt"))
        TestCase.assertEquals(1, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.first().path == "$mainProjectPath${File.separator}file.txt")

        // 7. Fix paths
        fixPathFor(projectB)

        // 8. Check the result
        TestCase.assertTrue(fileEditorManager.openFiles.first().path.endsWith("$worktreeProjectName${File.separator}file.txt"))
        TestCase.assertFalse(fileEditorManager.openFiles.first().path == "$mainProjectPath${File.separator}file.txt")

        // Tear down for `projectB`
        fileEditorManager.closeAllOpenedFiles()
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
    }

    fun `test files outside project`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create two files: one in the main project and another outside.
        createFile(mainProjectPath, "file.txt")
        val fileOutsideProject = createFile(mainProjectPath.parent, "outside.txt")
        val fileOutsideProjectPath = fileOutsideProject.toNioPath()

        // 4. Commit a file
        addCommit(project, "add new files")

        // 5. Create new worktree "B" with new branch "worktree-project"
        val worktreeProjectName = "worktree-project"
        val projectB = createWorktree(worktreeProjectName)
        val worktreeProjectPath = Paths.get(projectB.basePath!!)

        // 6. Open a file previously created in project "A"
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles(
            worktreeProjectPath.resolve("file.txt"),
            fileOutsideProjectPath,
        )
        TestCase.assertEquals(2, fileEditorManager.openFiles.size)

        // 7. Fix paths
        fixPathFor(project)

        // 8. Check the result
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path.endsWith("file.txt") })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == fileOutsideProjectPath.toString() })
        TestCase.assertTrue(fileEditorManager.openFiles.none { it.path.contains(worktreeProjectName) })

        // Tear down for `projectB`
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
        fileOutsideProjectPath.toFile().delete()
    }

    fun `test no notification for the same project`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create a file
        val file = createFile(mainProjectPath, "file.txt")

        // 4. Commit a file
        addCommit(project, "add new file `file.txt`")

        // 5. Open a file previously created in project "A"
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(file.toNioPath())
        TestCase.assertEquals(1, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.first().path == file.path)

        // 6. Checkout back to "master"
        git(project, "checkout master")

        // 7. Fix paths
        val notifications = mutableListOf<Notification>()
        subscribeToNotifications(project, notifications)
        GitWorktreePathFixer().process(project)
        TestCase.assertEquals(0, notifications.size)

        // 8. Check the result
        TestCase.assertTrue(fileEditorManager.openFiles.first().path == file.path)
    }

    fun `test single file with multiple worktrees`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create a file in `project`
        createFile(mainProjectPath, "file.txt")
        addCommit(project, "add new file `file.txt`")

        // 4. Create new worktree "B" with new branch "worktree-project"
        val worktreeProjectBName = "worktree-project"
        val projectB = createWorktree(worktreeProjectBName)
        val worktreeProjectBPath = Paths.get(projectB.basePath!!)

        // 5. Create new worktree "C" with new branch "worktree-project2"
        val worktreeProjectCName = "worktree-project2"
        val projectC = createWorktree(worktreeProjectCName)
        val worktreeProjectCPath = Paths.get(projectC.basePath!!)

        // 7. Open a file in project "B" previously created in project "A"
        val fileEditorManagerB = FileEditorManager.getInstance(projectB)
        fileEditorManagerB.openFile(mainProjectPath.resolve("file.txt"))
        TestCase.assertEquals(1, fileEditorManagerB.openFiles.size)
        TestCase.assertTrue(fileEditorManagerB.openFiles.any { it.path == "$mainProjectPath${File.separator}file.txt" })

        // 8. Fix paths
        fixPathFor(projectB)

        // 9. Check the result
        TestCase.assertTrue(fileEditorManagerB.openFiles.any { it.path == "$worktreeProjectBPath${File.separator}file.txt" })

        // 7.1. Open a file in project "C" previously created in project "A"
        val fileEditorManagerC = FileEditorManager.getInstance(projectC)
        fileEditorManagerC.openFile(worktreeProjectBPath.resolve("file.txt"))
        TestCase.assertEquals(1, fileEditorManagerC.openFiles.size)
        TestCase.assertTrue(fileEditorManagerC.openFiles.any { it.path == "$worktreeProjectBPath${File.separator}file.txt" })

        // 8.2. Fix paths
        fixPathFor(projectC)

        // 9.2. Check the result
        TestCase.assertTrue(fileEditorManagerC.openFiles.any { it.path == "$worktreeProjectCPath${File.separator}file.txt" })

        // Tear down
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectC)
    }

    fun `test path is fixed for third worktree`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create a file in `project`
        createFile(mainProjectPath, "file.txt")
        addCommit(project, "add new file `file.txt`")

        // 4. Create new worktree "B" with new branch "worktree-project"
        val worktreeProjectBName = "worktree-project"
        val projectB = createWorktree(worktreeProjectBName)
        val worktreeProjectBPath = Paths.get(projectB.basePath!!)

        // 5. Create a file in `projectB`
        createFile(worktreeProjectBPath, "file2.txt")
        addCommit(projectB, "add new file `file2.txt`")

        // 6. Switch branches to make new project `C` from project `B`
        Executor.cd(worktreeProjectBPath)
        git(projectB, "checkout -b tmp")
        Executor.cd(mainProjectPath)
        git(project, "checkout $worktreeProjectBName")

        // 7. Create new worktree "C" with new branch "worktree-project2"
        val worktreeProjectCName = "worktree-project2"
        val projectC = createWorktree(worktreeProjectCName)
        val worktreeProjectCPath = Paths.get(projectC.basePath!!)

        // 8. Open a file previously created in projects "A" and "B"
        val fileEditorManager = FileEditorManager.getInstance(projectC)
        fileEditorManager.openFile(mainProjectPath.resolve("file.txt"))
        fileEditorManager.openFile(worktreeProjectBPath.resolve("file2.txt"))
        TestCase.assertEquals(2, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$mainProjectPath${File.separator}file.txt" })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$worktreeProjectBPath${File.separator}file2.txt" })

        // 9. Fix paths
        fixPathFor(projectC)

        // 10. Check the result
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$worktreeProjectCPath${File.separator}file.txt" })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$worktreeProjectCPath${File.separator}file2.txt" })

        // Tear down
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectC)
    }

    fun `test path is fixed for main project with two worktrees`() {
        // 1. Create project "A"
        createRepository(project, projectNioRoot, makeInitialCommit = true)

        // 2. Create branch "dev"
        git(project, "checkout -b dev")

        // 3. Create new worktree "B" with new branch "worktree-project"
        val worktreeProjectBName = "worktree-project"
        val projectB = createWorktree(worktreeProjectBName)
        val worktreeProjectBPath = Paths.get(projectB.basePath!!)

        // 4. Create a file in project "B"
        createFile(worktreeProjectBPath, "file.txt")
        addCommit(projectB, "add new file `file.txt`")

        // 5. Switch branches to make new project `C` from project `B`
        Executor.cd(worktreeProjectBPath)
        git(projectB, "checkout -b tmp")
        Executor.cd(mainProjectPath)
        git(project, "checkout $worktreeProjectBName")

        // 6. Create new worktree "C" with new branch "worktree-project2"
        val worktreeProjectCName = "worktree-project2"
        val projectC = createWorktree(worktreeProjectCName)
        val worktreeProjectCPath = Paths.get(projectC.basePath!!)

        // 7. Create a file in project "C"
        createFile(worktreeProjectCPath, "file2.txt")
        addCommit(projectC, "add new file `file2.txt`")

        // 8. Switch branches to load previously created files
        Executor.cd(worktreeProjectCPath)
        git(projectB, "checkout -b tmp2")
        Executor.cd(mainProjectPath)
        git(project, "checkout $worktreeProjectCName")

        // 9. Open a file previously created in projects "B" and "C"
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(worktreeProjectBPath.resolve("file.txt"))
        fileEditorManager.openFile(worktreeProjectCPath.resolve("file2.txt"))
        TestCase.assertEquals(2, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$worktreeProjectBPath${File.separator}file.txt" })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$worktreeProjectCPath${File.separator}file2.txt" })

        // 10. Fix paths
        fixPathFor(project)

        // 11. Check the result
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$mainProjectPath${File.separator}file.txt" })
        TestCase.assertTrue(fileEditorManager.openFiles.any { it.path == "$mainProjectPath${File.separator}file2.txt" })

        // Tear down
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectC)
    }

    fun test0() {
        // worktrees are outside the main project directory
    }
}