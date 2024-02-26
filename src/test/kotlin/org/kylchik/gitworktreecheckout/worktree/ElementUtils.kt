package org.kylchik.gitworktreecheckout.worktree

import org.jdom.Element
import java.nio.file.Path

// TODO write visitor
fun Element.getAllFilesPath(dropFilePrefix: Boolean = true): List<String> {
    return buildList {
        val splitterElement = this@getAllFilesPath.getChild("splitter")
        val first = splitterElement?.getChild("split-first")
        val second = splitterElement?.getChild("split-second")

        if (first == null || second == null) {
            this@getAllFilesPath.getChild("leaf")?.getChildren("file")?.let { files ->
                files.forEach { file ->
                    val entry = file.getChild("entry")
                    val path = entry.getAttributeValue("file")
                    add(if (dropFilePrefix) path.removePrefix("file://") else path)
                }
            }
        }
        else {
            addAll(first.getAllFilesPath())
            addAll(second.getAllFilesPath())
        }
    }
}

fun Element.addFile(path: Path) {
    addFile(path.toString())
}

fun Element.addFile(path: String) {
    val file = Element("file")
    val entry = Element("entry")
    entry.setAttribute("file", "file://$path")
    file.addContent(entry)
    this.addContent(file)
}