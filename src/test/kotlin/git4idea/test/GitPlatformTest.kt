package git4idea.test

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.io.write
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class GitPlatformTest : HeavyPlatformTestCase() {
    protected fun createFile(dir: String, fileName: String, content: String): VirtualFile {
        return createFile(Paths.get(dir), fileName, content)
    }

    protected fun createFile(dir: Path, fileName: String, content: String): VirtualFile {
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
}
