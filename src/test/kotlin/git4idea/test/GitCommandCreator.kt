// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.test

import git4idea.commands.GitCommand

/**
 * Returns an instance of GitCommand object corresponding to the given Git command.
 *
 * It allows to support the convenient syntax `git("commit -m msg")`.
 * In addition to that, it dynamically constructs new GitCommand objects for commands which are not used in the production.
 */
fun getGitCommandInstance(commandName: String): GitCommand {
    return try {
        val fieldName = commandName.toUpperCase().replace('-', '_')
        GitCommand::class.java.getDeclaredField(fieldName).get(null) as GitCommand
    } catch (e: NoSuchFieldException) {
        val writeMethod = GitCommand::class.java.getDeclaredMethod("write", String::class.java)
        writeMethod.isAccessible = true
        return writeMethod.invoke(null, commandName) as GitCommand
    }
}

