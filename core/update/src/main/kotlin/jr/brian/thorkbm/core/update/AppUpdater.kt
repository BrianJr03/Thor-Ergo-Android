package jr.brian.thorkbm.core.update

import kotlinx.coroutines.flow.StateFlow

enum class CheckStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

/** What the update sheet shows. */
sealed interface UpdateStep {
    data object Notes : UpdateStep

    /** Android has not yet allowed ThorKBM to install apps. */
    data object NeedsPermission : UpdateStep

    /** [total] is 0 when the server did not say how big the APK is. */
    data class Downloading(val downloaded: Long, val total: Long) : UpdateStep

    data object Ready : UpdateStep

    /** [message] is the technical reason, when there is one. */
    data class Failed(val message: String?) : UpdateStep
}

data class UpdateState(
    val installedVersion: String = "",
    val check: CheckStatus = CheckStatus.IDLE,
    val release: Release? = null,
    /** `null` while the sheet is closed. */
    val step: UpdateStep? = null,
)

enum class UpdateAction {
    /** Asks GitHub for the latest release and opens the sheet if it is newer. */
    CHECK,

    /** Reopens the sheet for a release found earlier. */
    SHOW,

    /** Downloads the APK, then opens the installer. Also retries after a failure or the permission step. */
    DOWNLOAD,

    INSTALL,

    /** Opens the system page where the user allows ThorKBM to install apps. */
    ALLOW_INSTALLS,

    OPEN_RELEASE_PAGE,

    /** Closes the sheet; ignored while downloading. */
    DISMISS,
}

/**
 * Self-update from GitHub Releases. One instance per process, so the panel and the app's own
 * screens show the same sheet and the same download.
 */
interface AppUpdater {
    val state: StateFlow<UpdateState>

    /**
     * Called each time the app is opened. Checks when "Check for updates on start" is on, at most
     * every half hour: the panel keeps the process alive, so "once per process" could mean weeks.
     */
    fun checkOnStart()

    fun perform(action: UpdateAction)
}
