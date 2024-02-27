package org.kylchik.gitworktreecheckout.worktree

import org.jdom.Element

abstract class ElementVisitor {
    fun visit(element: Element) {
        val splitterElement = element.getChild("splitter")
        val first = splitterElement?.getChild("split-first")
        val second = splitterElement?.getChild("split-second")

        if (first == null || second == null) {
            element.getChild("leaf")?.getChildren("file")?.let { files ->
                files.forEach { file -> visitFile(file) }
            }
        }
        else {
            visit(first)
            visit(second)
        }
    }

    protected abstract fun visitFile(file: Element)
}