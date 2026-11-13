package jr.brian.thorkbm.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jr.brian.thorkbm.core.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks `releases/latest` on GitHub, downloads the release APK into the cache and hands it to the
 * package installer. The installer refuses an APK not signed with the installed app's key, so a
 * tampered download cannot replace ThorKBM; the SHA-256 check catches a corrupted one first.
 */
@Singleton
internal class GitHubAppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : AppUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(UpdateState(installedVersion = installedVersion()))
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var lastStartCheck = Long.MIN_VALUE
    private var check: Job? = null
    private var download: Job? = null

    override fun checkOnStart() {
        val now = SystemClock.elapsedRealtime()
        if (lastStartCheck != Long.MIN_VALUE && now - lastStartCheck < START_CHECK_INTERVAL_MS) return
        lastStartCheck = now
        scope.launch { if (settings.settings.first().autoCheckUpdates) check() }
    }

    override fun perform(action: UpdateAction) {
        when (action) {
            UpdateAction.CHECK -> check()
            UpdateAction.SHOW -> _state.update { if (it.release?.isNewer == true && it.step == null) it.copy(step = UpdateStep.Notes) else it }
            UpdateAction.DOWNLOAD -> download()
            UpdateAction.INSTALL -> install()
            UpdateAction.ALLOW_INSTALLS -> start(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            UpdateAction.OPEN_RELEASE_PAGE -> {
                _state.value.release?.pageUrl?.takeIf { it.isNotBlank() }?.let { start(Intent(Intent.ACTION_VIEW, it.toUri())) }
                dismiss()
            }
            UpdateAction.DISMISS -> dismiss()
        }
    }

    private fun check() {
        if (check?.isActive == true || download?.isActive == true) return
        _state.update { it.copy(check = CheckStatus.CHECKING) }
        check = scope.launch {
            val release = try {
                withContext(Dispatchers.IO) { Releases.parse(fetchLatest(), _state.value.installedVersion) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                null
            }
            _state.update {
                when {
                    release == null -> it.copy(check = CheckStatus.FAILED)
                    release.isNewer -> it.copy(check = CheckStatus.AVAILABLE, release = release, step = it.step ?: UpdateStep.Notes)
                    else -> it.copy(check = CheckStatus.UP_TO_DATE, release = release)
                }
            }
        }
    }

    private fun fetchLatest(): String {
        val connection = open(Releases.LATEST_URL).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("GitHub returned ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download() {
        if (download?.isActive == true) return
        val release = _state.value.release ?: return
        val apk = release.apk ?: return perform(UpdateAction.OPEN_RELEASE_PAGE)
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(step = UpdateStep.NeedsPermission) }
            return
        }
        _state.update { it.copy(step = UpdateStep.Downloading(0, apk.size)) }
        download = scope.launch {
            val step = try {
                withContext(Dispatchers.IO) { fetchApk(apk) }
                UpdateStep.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                UpdateStep.Failed(e.message)
            }
            _state.update { it.copy(step = step) }
            if (step == UpdateStep.Ready) install()
        }
    }

    /** Streams the APK to the cache, replacing any earlier download, and verifies it. */
    private fun fetchApk(apk: ApkAsset) {
        val dir = File(context.cacheDir, CACHE_DIR).apply { deleteRecursively(); mkdirs() }
        val target = File(dir, APK_FILE)
        val partial = File(dir, "$APK_FILE.part")
        // browser_download_url redirects to GitHub's CDN; HttpURLConnection follows it (HTTPS to HTTPS).
        val connection = open(apk.url).apply { setRequestProperty("Accept", "application/octet-stream") }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Server returned ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: apk.size
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var shownPercent = -1L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        // One state change per percent keeps the sheet from recomposing per buffer.
                        val percent = if (total > 0) downloaded * 100 / total else downloaded / (256 * 1024)
                        if (percent != shownPercent) {
                            shownPercent = percent
                            _state.update { it.copy(step = UpdateStep.Downloading(downloaded, total)) }
                        }
                    }
                }
            }
            if (apk.size > 0 && downloaded != apk.size) throw IOException("Download incomplete ($downloaded of ${apk.size} bytes)")
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (apk.sha256 != null && !apk.sha256.equals(sha256, ignoreCase = true)) throw IOException("Download is corrupted (checksum mismatch)")
            if (!partial.renameTo(target)) throw IOException("Could not save the update")
        } finally {
            connection.disconnect()
        }
    }

    private fun install() {
        val file = File(File(context.cacheDir, CACHE_DIR), APK_FILE)
        if (!file.exists()) return download()
        // A failure here must never take the keyboard down with it; the sheet shows it instead.
        val opened = try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            start(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the installer", e)
            false
        }
        if (!opened) _state.update { it.copy(step = UpdateStep.Failed(null)) }
    }

    private fun dismiss() {
        _state.update { if (it.step is UpdateStep.Downloading) it else it.copy(step = null) }
    }

    /** From the panel there is no activity, so everything starts in a new task. */
    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Nothing can open $intent", e)
        false
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "ThorKBM/${_state.value.installedVersion}")
    }

    private fun installedVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private companion object {
        const val TAG = "ThorKBM"
        const val CACHE_DIR = "updates"
        const val APK_FILE = "ThorKBM-update.apk"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val TIMEOUT_MS = 30_000
        const val START_CHECK_INTERVAL_MS = 30 * 60 * 1000L
    }
}
