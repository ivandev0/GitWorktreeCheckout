# Git Worktree Checkout plugin

<!-- Plugin description -->

Plugin for IntelliJ IDEA that helps to restore the workspace after switching to a worktree branch.

### How it works

Whenever a user switches branches, IntelliJ IDEA automatically restores the workspace associated
with a given branch. This plugin checks if the restored workspace contains files that point outside the current
working directory. This is exactly the case when working with worktrees in IntelliJ IDEA. If such files
were found, the plugin will show a notification and will suggest to fix files' paths.

![Notification Window](https://raw.githubusercontent.com/ivandev0/GitWorktreeCheckout/master/pic/notification_window.jpeg)

### Main features

1. Very straightforward to use. Notification will appear only when some files have wrong paths.
2. Plugin restores files in the same layout they were loaded. Including any complex editor splitters.
3. All inner file states are preserved.

<!-- Plugin description end -->