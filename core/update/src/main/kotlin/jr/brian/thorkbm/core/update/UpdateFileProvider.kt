package jr.brian.thorkbm.core.update

import androidx.core.content.FileProvider

/** Shares the downloaded APK with the package installer. Its own class, so it never clashes with another FileProvider. */
internal class UpdateFileProvider : FileProvider(R.xml.update_paths)
