package org.kylchik.gitworktreecheckout.worktree

import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase
import org.jdom.Element
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class GitWorktreePathFixerTest: HeavyPlatformTestCase() {
    private val mainProjectPath: Path
        get() = Paths.get(project.basePath!!)

    private lateinit var element: Element

    override fun setUp() {
        element = Element("editor")
        super.setUp()
    }

    fun `test path is fixed for single file from worktree`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectName = "worktree-project"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)

        // Open files
        val fileName = "file.txt"
        leaf.addFile(worktreeProjectPath.resolve(fileName))

        // Fix
        fixPathsInElement(element, listOf(mainProjectPath.toString(), worktreeProjectPath.toString()))

        // Check the result
        val openedFiles = element.getAllFilesPath()
        TestCase.assertEquals(1, openedFiles.size)
        TestCase.assertEquals(mainProjectPath.resolve(fileName).toString(), openedFiles.first())
    }

    fun `test path is fixed for multiple files from worktree`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectName = "worktree-project"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)

        // Open files
        leaf.addFile(worktreeProjectPath.resolve("file.txt"))
        leaf.addFile(worktreeProjectPath.resolve("subDir").resolve("fileInSubDir.txt"))
        leaf.addFile(worktreeProjectPath.resolve("subDir").resolve("secondFileInSubDir.txt"))
        leaf.addFile(worktreeProjectPath.resolve("subDir").resolve("subSubDir").resolve("fileInSubSubDir.txt"))

        // Fix
        fixPathsInElement(element, listOf(mainProjectPath.toString(), worktreeProjectPath.toString()))

        // Check the result
        val openedFiles = element.getAllFilesPath()

        TestCase.assertEquals(4, openedFiles.size)
        TestCase.assertTrue(openedFiles.none { it.contains(worktreeProjectName) })
        TestCase.assertTrue(openedFiles.any { it.endsWith("file.txt") })
        TestCase.assertTrue(openedFiles.any { it.endsWith("subDir${File.separator}fileInSubDir.txt") })
        TestCase.assertTrue(openedFiles.any { it.endsWith("subDir${File.separator}secondFileInSubDir.txt") })
        TestCase.assertTrue(openedFiles.any { it.endsWith("subDir${File.separator}subSubDir${File.separator}fileInSubSubDir.txt") })
    }

    fun `test path is fixed for single file from main repo`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectName = "worktree-project"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)

        // Open files
        val fileName = "file.txt"
        leaf.addFile(mainProjectPath.resolve(fileName))

        // Fix
        fixPathsInElement(element, listOf(worktreeProjectPath.toString(), mainProjectPath.toString()))

        // Check the result
        val openedFiles = element.getAllFilesPath()

        TestCase.assertEquals(1, openedFiles.size)
        TestCase.assertEquals(worktreeProjectPath.resolve(fileName).toString(), openedFiles.first())
    }

    fun `test files outside project`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectName = "worktree-project"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)

        // Open files
        val fileInsideName = "file.txt"
        val fileOutsideName = "fileOutside.txt"

        leaf.addFile(worktreeProjectPath.resolve(fileInsideName))
        val fileOutsideProjectPath = mainProjectPath.parent.resolve(fileOutsideName)
        leaf.addFile(fileOutsideProjectPath)

        // Fix
        fixPathsInElement(element, listOf(mainProjectPath.toString(), worktreeProjectPath.toString()))

        // Check the result
        val openedFiles = element.getAllFilesPath()
        TestCase.assertEquals(2, openedFiles.size)

        TestCase.assertTrue(openedFiles.any { it.endsWith(fileInsideName) })
        TestCase.assertTrue(openedFiles.any { it == fileOutsideProjectPath.toString() })
        TestCase.assertTrue(openedFiles.none { it.contains(worktreeProjectName) })
    }

    fun `test no changes for the same project`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectName = "worktree-project"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)

        // Open files
        val fileName = "file.txt"
        leaf.addFile(mainProjectPath.resolve(fileName))

        // Fix
        fixPathsInElement(element, listOf(mainProjectPath.toString(), worktreeProjectPath.toString()))

        // Check the result
        val openedFiles = element.getAllFilesPath()
        TestCase.assertEquals(1, openedFiles.size)
        TestCase.assertEquals(mainProjectPath.resolve(fileName).toString(), openedFiles.first())
    }

    fun `test single file with multiple worktrees`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectBName = "worktree-project"
        val worktreeProjectBPath = mainProjectPath.resolve(worktreeProjectBName)
        val worktreeProjectCName = "worktree-project2"
        val worktreeProjectCPath = mainProjectPath.resolve(worktreeProjectCName)

        // Open files
        val fileName = "file.txt"
        leaf.addFile(mainProjectPath.resolve(fileName))

        // Fix
        val projectsWithMainB = listOf(worktreeProjectBPath.toString(), mainProjectPath.toString(), worktreeProjectCPath.toString())
        fixPathsInElement(element, projectsWithMainB)

        // Check the result
        val openedFilesInB = element.getAllFilesPath()
        TestCase.assertEquals(1, openedFilesInB.size)
        TestCase.assertEquals(worktreeProjectBPath.resolve(fileName).toString(), openedFilesInB.first())

        // Reset
        element = Element("editor")
        val newLeaf = Element("leaf")
        element.addContent(newLeaf)

        // Open files
        newLeaf.addFile(mainProjectPath.resolve(fileName))

        // Fix
        val projectsWithMainC = listOf(worktreeProjectCPath.toString(), mainProjectPath.toString(), worktreeProjectBPath.toString())
        fixPathsInElement(element, projectsWithMainC)

        // Check the result
        val openedFilesInC = element.getAllFilesPath()
        TestCase.assertEquals(1, openedFilesInC.size)
        TestCase.assertEquals(worktreeProjectCPath.resolve(fileName).toString(), openedFilesInC.first())
    }

    fun `test path is fixed for third worktree`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectBName = "worktree-project"
        val worktreeProjectBPath = mainProjectPath.resolve(worktreeProjectBName)
        val worktreeProjectCName = "worktree-project2"
        val worktreeProjectCPath = mainProjectPath.resolve(worktreeProjectCName)

        // Open files
        val file1Name = "file1.txt"
        val file2Name = "file2.txt"
        leaf.addFile(mainProjectPath.resolve(file1Name))
        leaf.addFile(worktreeProjectBPath.resolve(file2Name))

        // Fix
        // Note: last two projects are manually "sorted"
        val projectsWithMainC = listOf(worktreeProjectCPath.toString(), worktreeProjectBPath.toString(), mainProjectPath.toString())
        fixPathsInElement(element, projectsWithMainC)

        // Check the result
        val openedFiles = element.getAllFilesPath()
        TestCase.assertEquals(2, openedFiles.size)
        TestCase.assertTrue(openedFiles.any { it == worktreeProjectCPath.resolve(file1Name).toString() })
        TestCase.assertTrue(openedFiles.any { it == worktreeProjectCPath.resolve(file2Name).toString() })
    }

    fun `test path is fixed for main project with two worktrees`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectBName = "worktree-project"
        val worktreeProjectBPath = mainProjectPath.resolve(worktreeProjectBName)
        val worktreeProjectCName = "worktree-project2"
        val worktreeProjectCPath = mainProjectPath.resolve(worktreeProjectCName)

        // Open files
        val file1Name = "file1.txt"
        val file2Name = "file2.txt"
        leaf.addFile(worktreeProjectBPath.resolve(file1Name))
        leaf.addFile(worktreeProjectCPath.resolve(file2Name))

        // Fix
        // Note: last two projects are manually "sorted"
        val projectsWithMainC = listOf(mainProjectPath.toString(), worktreeProjectCPath.toString(), worktreeProjectBPath.toString())
        fixPathsInElement(element, projectsWithMainC)

        // Check the result
        val openedFiles = element.getAllFilesPath()
        TestCase.assertEquals(2, openedFiles.size)
        TestCase.assertTrue(openedFiles.any { it == mainProjectPath.resolve(file1Name).toString() })
        TestCase.assertTrue(openedFiles.any { it == mainProjectPath.resolve(file2Name).toString() })
    }

    fun `test worktrees that are outside the main project directory`() {
        val leaf = Element("leaf")
        element.addContent(leaf)

        val worktreeProjectName = "worktree-project"
        val worktreeProjectPath = mainProjectPath.parent.resolve(worktreeProjectName)

        // Open files
        val fileName = "file.txt"
        leaf.addFile(worktreeProjectPath.resolve(fileName))

        // Fix
        fixPathsInElement(element, listOf(mainProjectPath.toString(), worktreeProjectPath.toString()))

        // Check the result
        val openedFiles = element.getAllFilesPath()
        TestCase.assertEquals(1, openedFiles.size)
        TestCase.assertEquals(mainProjectPath.resolve(fileName).toString(), openedFiles.first())
    }

    fun `test splitters`() {
        val splitter = Element("splitter")
        element.addContent(splitter)

        val firstLeaf = Element("leaf")
        splitter.addContent(Element("split-first").apply { addContent(firstLeaf) })
        val secondLeaf = Element("leaf")
        splitter.addContent(Element("split-second").apply { addContent(secondLeaf) })

        val worktreeProjectName = "worktree-project"
        val worktreeProjectPath = mainProjectPath.resolve(worktreeProjectName)

        // Open files
        val fileFirstName = "first.txt"
        firstLeaf.addFile(worktreeProjectPath.resolve(fileFirstName))
        val fileSecondName = "second.txt"
        secondLeaf.addFile(worktreeProjectPath.resolve(fileSecondName))

        // Fix
        fixPathsInElement(element, listOf(mainProjectPath.toString(), worktreeProjectPath.toString()))

        // Check the result
        val openedFiles = element.getAllFilesPath()
        TestCase.assertEquals(2, openedFiles.size)
        TestCase.assertTrue(openedFiles.any { it == mainProjectPath.resolve(fileFirstName).toString() })
        TestCase.assertTrue(openedFiles.any { it == mainProjectPath.resolve(fileSecondName).toString() })
    }
}