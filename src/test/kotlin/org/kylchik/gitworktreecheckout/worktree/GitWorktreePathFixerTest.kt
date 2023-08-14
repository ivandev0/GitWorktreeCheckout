package org.kylchik.gitworktreecheckout.worktree

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.tasks.context.WorkingContextManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import git4idea.GitUtil
import git4idea.branch.GitBranchWorker
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryImpl
import git4idea.test.*
import git4idea.test.createRepository
import git4idea.test.git
import junit.framework.TestCase
import org.jetbrains.jetCheck.Generator
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Function
import kotlin.io.path.Path

class GitWorktreePathFixerTest: GitPlatformTest() {
    private val projectRoot: VirtualFile
        get() = getOrCreateProjectBaseDir()

    private val projectPath: String
        get() = FileUtil.toSystemIndependentName(projectNioRoot.toString())

    private val projectNioRoot: Path
        get() = project.stateStore.getProjectBasePath()


    fun test() {
//        GitWorkTreeBaseTest
//        GitPlatformTest
        println(projectDirOrFile)
//        git(project, "worktree add $projectPath")
//        gitAsBytes
//        assertHasNotification()
    }

    private fun createNewProject(): Project? {
//        doCreateAndOpenProject()

        val temp = createTempDirectory()
        val tempDir = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp))

        return ProjectManagerEx.getInstanceEx().openProject(
            Path.of(tempDir!!.path),
            createTestOpenProjectOptions()
        )
    }

    fun test0() {
        WorkingContextManager.getInstance(project).enableUntil(testRootDisposable)

        // create project A
        println(project.basePath)
        val repo = createRepository(project, projectNioRoot, makeInitialCommit = true)
        // create branch A1
        println(git(project, "branch"))
        println(git(project, "branch dev"))
        println(git(project, "branch"))
        // create new worktree (B) with new branch A2
        git(project, "worktree add -b worktree_branch ./worktree-project")
        println(git(project, "branch"))

        // open project B
        val path = Path(project.basePath + "/worktree-project")
        println("project B $path")
        val projectB = ProjectManagerEx.getInstanceEx().openProject(path, OpenProjectTaskBuilder().projectName(project.name).build())!!
        println("project B off path: ${projectB.basePath}")
        Executor.cd(projectB.basePath!!)
        println(git(projectB, "branch"))
        println(FileEditorManager.getInstance(projectB).openFiles.size)

        WorkingContextManager.getInstance(projectB).enableUntil(testRootDisposable)

        // create and open a file
        val txtFile = createTempVirtualFile("file.txt", null, "", StandardCharsets.UTF_8)
        val descriptor = OpenFileDescriptor(projectB, txtFile)
        val editor = FileEditorManager.getInstance(projectB).openFileEditor(descriptor, true)
        println(FileEditorManager.getInstance(projectB).openFiles.size)

        // check branch changed
        var toBranch = ""
        var fromBranch = ""
        class Listener: BranchChangeListener {
            override fun branchHasChanged(branchName: String) {
                toBranch = branchName
            }

            override fun branchWillChange(branchName: String) {
                fromBranch = branchName
            }
        }

        projectB.messageBus.connect(testRootDisposable).subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, Listener())

        // checkout other branch in B
        val rootDir = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(projectB.basePath!!))!!
        val gitGir = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(Paths.get(project.basePath!! + "/.git/worktrees/worktree-project"))!!

        val repoB = GitRepositoryImpl::class.java.getDeclaredMethod(
            "createInstance",
            VirtualFile::class.java, VirtualFile::class.java, Project::class.java, Disposable::class.java,
        ).let {
            it.isAccessible = true
            it.invoke(null, rootDir, gitGir, projectB, testRootDisposable) as GitRepository
        }

//        val projectBVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(projectB.basePath!!))!!
//        val repoB = GitUtil.getRepositoryManager(projectB).getRepositoryForRoot(projectBVirtualFile)
        val workerB = GitBranchWorker(projectB, TestGitImpl(), TestUiHandler(projectB))
        workerB.checkout("dev", false, listOf(repoB))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        TestCase.assertEquals("dev", toBranch)
        TestCase.assertEquals("worktree_branch", fromBranch)
        println(repoB.git("branch"))

        // close project B
        ProjectManager.getInstance().closeAndDispose(projectB)
        println("closed B")
        toBranch = ""
        fromBranch = ""

        // return to A and checkout branch A2
        Executor.cd(project.basePath!!)
        project.messageBus.connect(testRootDisposable).subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, Listener())

        println(repo.git("branch"))

        val worker = GitBranchWorker(project, TestGitImpl(), TestUiHandler(project))
        worker.checkout("worktree_branch", false, listOf(repo))

        println(git(project, "branch"))

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        TestCase.assertEquals("worktree_branch", toBranch)
        TestCase.assertEquals("master", fromBranch)
        println(FileEditorManager.getInstance(project).openFiles.size)

        // check that there is one file with wrong path
        // TODO somehow reopen editors. OpenEditorsContextProvider doesn't work because of test file manager
        //  probably the easiest way is to create my own provider
    }

    private fun closeAllOpenedFiles() {
        val editor = FileEditorManager.getInstance(project)
        editor.openFiles.forEach { editor.closeFile(it) }
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