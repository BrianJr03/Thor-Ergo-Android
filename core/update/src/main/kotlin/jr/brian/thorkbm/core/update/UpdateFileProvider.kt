package jr.brian.thorkbm.core.update

import androidx.core.content.FileProvider

/**
 * Shares the downloaded APK with the package installer. Its own class, so it never clashes with
 * another FileProvider; the paths are in the manifest meta-data.
 */
internal class UpdateFileProvider : FileProvider()
