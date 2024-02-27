package org.kylchik.gitworktreecheckout.worktree

import org.jdom.Element
import java.nio.file.Path

fun Element.getAllFilesPath(dropFilePrefix: Boolean = true): List<String> {
    return buildList {
        object : ElementVisitor() {
            override fun visitFile(file: Element) {
                val entry = file.getChild("entry")
                val path = entry.getAttributeValue("file")
                add(if (dropFilePrefix) path.removePrefix("file://") else path)
            }
        }.visit(this@getAllFilesPath)
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