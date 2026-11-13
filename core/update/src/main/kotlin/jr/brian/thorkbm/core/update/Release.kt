package jr.brian.thorkbm.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** The APK attached to a GitHub release. [sha256] is GitHub's digest, when it reports one. */
data class ApkAsset(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String?,
)

/** The latest GitHub release, as the update sheet shows it. */
data class Release(
    val version: String,
    val isNewer: Boolean,
    /** Markdown: only the sections of the release body newer than the installed version. */
    val notes: String,
    val pageUrl: String,
    /** `null` when the release has no APK; the sheet then offers the release page. */
    val apk: ApkAsset?,
)

/** Reads GitHub's `releases/latest` response and compares versions. Pure, so it is unit tested. */
object Releases {
    const val LATEST_URL = "https://api.github.com/repos/BrianJr03/Thor-Ergo-Android/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, installedVersion: String): Release {
        val release = json.parseToJsonElement(body).jsonObject
        val version = release.string("tag_name").removePrefix("v").trim()
        val apks = (release["assets"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { asset ->
                val url = asset.string("browser_download_url")
                val name = asset.string("name").ifBlank { url.substringAfterLast('/') }
                if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) return@mapNotNull null
                ApkAsset(
                    name = name,
                    url = url,
                    size = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    sha256 = asset.string("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
                )
            }
        return Release(
            version = version,
            isNewer = isNewer(version, installedVersion),
            notes = notesSince(release.string("body"), installedVersion),
            pageUrl = release.string("html_url"),
            // Releases ship one signed APK; never pick a debug build if one is ever attached.
            apk = apks.firstOrNull { "debug" !in it.name.lowercase() } ?: apks.firstOrNull(),
        )
    }

    /**
     * True when [latest] is a strictly greater dotted version than [installed]. A leading "v" and
     * any "-suffix" are ignored, so "2.1-debug" equals "v2.1"; missing parts count as zero.
     */
    fun isNewer(latest: String, installed: String): Boolean {
        val a = parts(latest)
        val b = parts(installed)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * The release body is the whole RELEASE_NOTES.md, one `## <version>` section per release. Keeps
     * the sections newer than [installed], so the sheet shows only what changed since; a body
     * without version sections, or with none newer, is kept whole.
     */
    fun notesSince(body: String, installed: String): String {
        val lines = body.replace("\r\n", "\n").lines()
        val starts = lines.indices.filter { VERSION_HEADING.matches(lines[it]) }
        if (starts.isEmpty()) return body.trim()
        val kept = starts.mapIndexedNotNull { n, start ->
            val version = VERSION_HEADING.matchEntire(lines[start])!!.groupValues[1]
            if (!isNewer(version, installed)) return@mapIndexedNotNull null
            val end = starts.getOrElse(n + 1) { lines.size }
            lines.subList(start, end).joinToString("\n").trim()
        }
        return kept.ifEmpty { return body.trim() }.joinToString("\n\n")
    }

    private val VERSION_HEADING = Regex("""^##\s+v?(\d+(?:\.\d+)*)\b.*$""")

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: return emptyList() }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}
