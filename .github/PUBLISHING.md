# Publishing Thor Ergo 1.0

The publication workflow is configured in this fork. It runs only for the
approved `Thor-Ergo-1.0-Publish.zip` archive on `main`, or when manually started.
It never modifies the upstream repository.

## One-time upload

1. Open the repository's **Actions** tab. If GitHub asks for permission to enable
   workflows in this fork, enable them.
2. From the repository root choose **Add file → Upload files**.
3. Upload **Thor-Ergo-1.0-Publish.zip** without extracting or renaming it.
4. Commit directly to **main**.
5. Open **Actions → Publish Thor Ergo 1.0**. If it did not start automatically,
   choose **Run workflow**, select `main`, and start it.

No Git installation, command-line login, personal access token or SSH password is
needed for publication. The GitHub job uses its own scoped `GITHUB_TOKEN`.

## What the job does

It checks the pinned SHA-256 before extracting anything; verifies all files,
the complete installation ZIP and its embedded SSH payload; runs isolated
application and host-installer tests; replaces the fork's working tree with the
Thor Ergo source, documentation and rendered menu previews while retaining its
Git history; and publishes **Thor Ergo 1.0** under tag **v1.0**.

Release assets are first uploaded to a draft and downloaded again for checksum
verification. Only then is the release made public. A public release is never
silently overwritten. There are no scheduled jobs or recurring maintenance tasks.

The temporary upload archive is removed from the current source tree after
import. It remains in Git history so that an interrupted publication can be
retried without asking for another upload.

A green workflow result confirms publication. Download `Thor-Ergo-1.0.zip` from
Releases → Assets, not the publication ZIP or GitHub's Source code archives.
Do not archive the repository until the workflow has completed and the download
has been checked.
