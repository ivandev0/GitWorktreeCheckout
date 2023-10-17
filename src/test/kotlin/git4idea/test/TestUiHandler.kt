package git4idea.test

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitCommit
import git4idea.GitNotificationIdsHolder
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitSmartOperationDialog
import git4idea.repo.GitRepository

open class TestUiHandler(private val project: Project) : GitBranchUiHandler {
    override fun getProgressIndicator() = EmptyProgressIndicator()

    override fun showSmartOperationDialog(project: Project,
                                          changes: List<Change>,
                                          paths: Collection<String>,
                                          operation: String,
                                          forceButton: String?): GitSmartOperationDialog.Choice =
        GitSmartOperationDialog.Choice.SMART

    override fun showBranchIsNotFullyMergedDialog(project: Project,
                                                  history: Map<GitRepository, List<GitCommit>>,
                                                  baseBranches: Map<GitRepository, String>,
                                                  removedBranch: String): Boolean {
        throw UnsupportedOperationException()
    }

    override fun notifyError(title: String, message: String) {
        VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.BRANCH_OPERATION_ERROR, title, message)
    }

    override fun notifyErrorWithRollbackProposal(title: String, message: String, rollbackProposal: String): Boolean {
        throw UnsupportedOperationException("$title\n$message\n$rollbackProposal")
    }

    override fun showUnmergedFilesNotification(operationName: String, repositories: Collection<GitRepository>) {
        throw UnsupportedOperationException("$operationName\n$repositories")
    }

    override fun showUnmergedFilesMessageWithRollback(operationName: String, rollbackProposal: String): Boolean {
        throw UnsupportedOperationException("$operationName\n$rollbackProposal")
    }

    override fun showUntrackedFilesNotification(operationName: String, root: VirtualFile, relativePaths: Collection<String>) {
        throw UnsupportedOperationException("$operationName $root\n$relativePaths")
    }

    override fun showUntrackedFilesDialogWithRollback(operationName: String,
                                                      rollbackProposal: String,
                                                      root: VirtualFile,
                                                      relativePaths: Collection<String>): Boolean {
        throw UnsupportedOperationException("$operationName\n$rollbackProposal\n$root\n$relativePaths")
    }

    override fun confirmRemoteBranchDeletion(branchNames: List<String>,
                                             trackingBranches: Collection<String>,
                                             repositories: Collection<GitRepository>): GitBranchUiHandler.DeleteRemoteBranchDecision {
        throw UnsupportedOperationException("$branchNames\n$trackingBranches\n$repositories")
    }
}
