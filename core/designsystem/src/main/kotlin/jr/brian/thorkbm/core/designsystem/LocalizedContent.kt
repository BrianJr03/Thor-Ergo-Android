package jr.brian.thorkbm.core.designsystem

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import jr.brian.thorkbm.core.model.Language
import java.util.Locale

/**
 * Renders [content] in the app's own interface language, independent of the system locale,
 * so the panel switches language immediately when the setting changes.
 */
@Composable
fun LocalizedContent(language: Language, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localized = remember(base, baseConfiguration, language) {
        val configuration = Configuration(baseConfiguration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
        LocalizedContext(base, base.createConfigurationContext(configuration).resources) to configuration
    }
    CompositionLocalProvider(
        LocalContext provides localized.first,
        LocalConfiguration provides localized.second,
        content = content,
    )
}

/**
 * Keeps the original context (usually the Activity) as its base, so lookups that unwrap
 * the context chain (activity results, permission launchers) still find it; only the
 * resources are swapped for the localised ones.
 */
private class LocalizedContext(base: Context, private val localizedResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}
