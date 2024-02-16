package org.kylchik.gitworktreecheckout.worktree

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.project.stateStore
import com.intellij.testFramework.OpenProjectTaskBuilder
import git4idea.test.*
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
        addCommit("add new file `file.txt`")

        // 5. Create new worktree "B" with new branch "worktree_branch"
        val worktreeProjectName = "worktree-project"
        git(project, "worktree add -b worktree_branch ./$worktreeProjectName")
        StandardFileSystems.local().refresh(true)

        // 7. Open a file previously created in project "A"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)
        val projectB = ProjectManagerEx.getInstanceEx().openProject(
            worktreeProjectPath,
            OpenProjectTaskBuilder().projectName(project.name).build()
        )!!
        registerRepo(projectB, worktreeProjectPath)

        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(worktreeProjectPath.resolve("file.txt"))
        TestCase.assertEquals(1, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.first().path.endsWith("$worktreeProjectName${File.separator}file.txt"))

        // 8. Fix paths
        fixPathFor(project)

        // 9. Check the result
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
        addCommit("add new files")

        // 5. Create new worktree "B" with new branch "worktree_branch"
        val worktreeProjectName = "worktree-project"
        git(project, "worktree add -b worktree_branch ./$worktreeProjectName")
        StandardFileSystems.local().refresh(true)

        // 7. Open files that were previously created in project "A"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)
        val projectB = ProjectManagerEx.getInstanceEx().openProject(
            worktreeProjectPath,
            OpenProjectTaskBuilder().projectName(project.name).build()
        )!!
        registerRepo(projectB, worktreeProjectPath)

        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles(
            worktreeProjectPath.resolve("file.txt"),
            worktreeProjectPath.resolve("subDir").resolve("fileInSubDir.txt"),
            worktreeProjectPath.resolve("subDir").resolve("secondFileInSubDir.txt"),
            worktreeProjectPath.resolve("subDir").resolve("subSubDir").resolve("fileInSubSubDir.txt")
        )
        TestCase.assertEquals(4, fileEditorManager.openFiles.size)

        // 8. Fix paths
        fixPathFor(project)

        // 9. Check the result
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
        addCommit("add new file `file.txt`")

        // 5. Create new worktree "B" with new branch "worktree_branch"
        val worktreeProjectName = "worktree-project"
        git(project, "worktree add -b worktree_branch ./$worktreeProjectName")
        // NB: update is required. When we create a new worktree, this triggers `VCS_CONFIGURATION_CHANGED` event
        // and `VcsRepositoryManager` starts to update. This captures the `WRITE_LOCK` that is never going to be released.
        // When later we will call `registerRepo` method, it also tries to capture the same lock and deadlock appears.
        StandardFileSystems.local().refresh(true)

        // 7. Open a file previously created in project "A" as it was opened in "A"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)
        val projectB = ProjectManagerEx.getInstanceEx().openProject(
            worktreeProjectPath,
            OpenProjectTaskBuilder().projectName(project.name).build()
        )!!
        registerRepo(projectB, worktreeProjectPath)

        val fileEditorManager = FileEditorManager.getInstance(projectB)
        fileEditorManager.openFile(mainProjectPath.resolve("file.txt"))
        TestCase.assertEquals(1, fileEditorManager.openFiles.size)
        TestCase.assertTrue(fileEditorManager.openFiles.first().path == "$mainProjectPath${File.separator}file.txt")

        // 8. Fix paths
        fixPathFor(projectB)

        // 9. Check the result
        TestCase.assertTrue(fileEditorManager.openFiles.first().path.endsWith("$worktreeProjectName${File.separator}file.txt"))
        TestCase.assertFalse(fileEditorManager.openFiles.first().path == "$mainProjectPath${File.separator}file.txt")

        // Tear down for `projectB`
        fileEditorManager.closeAllOpenedFiles()
        ProjectManagerEx.getInstanceEx().closeAndDispose(projectB)
    }

    fun test2() {
        // the same as test1, but we have some files not from any project
        // check that these files have the same path as before action click
    }

    fun test3() {
        // create project A
        // create new branch A1
        // create an open some files in branch A1
        // create new branch A2 and checkout
        // checkout back to A1
        // notification must not appear
    }

    fun test4() {
        // multiple worktrees
    }

    fun test5() {
        // multiple worktrees
        // switch to worktree 1, but don't click action button on notification
        // open some files
        // switch to worktree 2, but don't click action button on notification
        // open some files
        // switch back to main
    }
}