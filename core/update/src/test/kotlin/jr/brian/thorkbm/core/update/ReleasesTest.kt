package jr.brian.thorkbm.core.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String, body: String = "", assets: String = "[]") =
        """{"tag_name":"$tag","html_url":"https://github.com/x/releases/$tag","body":${quote(body)},"assets":$assets,"draft":false}"""

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test
    fun `newer versions compare by number, not text`() {
        assertThat(Releases.isNewer("2.10", "2.9")).isTrue()
        assertThat(Releases.isNewer("v2.2", "2.1")).isTrue()
        assertThat(Releases.isNewer("3.0", "2.9.9")).isTrue()
        assertThat(Releases.isNewer("2.1", "2.1")).isFalse()
        assertThat(Releases.isNewer("2.0", "2.1")).isFalse()
    }

    @Test
    fun `suffixes and missing parts are ignored`() {
        assertThat(Releases.isNewer("2.1", "2.1-debug")).isFalse()
        assertThat(Releases.isNewer("2.2", "2.1-debug")).isTrue()
        assertThat(Releases.isNewer("2.1.0", "2.1")).isFalse()
        assertThat(Releases.isNewer("2.1.1", "2.1")).isTrue()
    }

    @Test
    fun `unreadable versions are never newer`() {
        assertThat(Releases.isNewer("", "2.1")).isFalse()
        assertThat(Releases.isNewer("2.2", "")).isFalse()
        assertThat(Releases.isNewer("nightly", "2.1")).isFalse()
    }

    @Test
    fun `parses the release apk with its size and digest`() {
        val assets = """[
            {"name":"ThorKBM-v2.2-debug.apk","browser_download_url":"https://x/debug.apk","size":9},
            {"name":"ThorKBM-v2.2.apk","browser_download_url":"https://x/ThorKBM-v2.2.apk","size":2519952,"digest":"sha256:abc123"},
            {"name":"checksums.txt","browser_download_url":"https://x/checksums.txt","size":1}
        ]"""
        val parsed = Releases.parse(release("v2.2", assets = assets), installedVersion = "2.1")
        assertThat(parsed.version).isEqualTo("2.2")
        assertThat(parsed.isNewer).isTrue()
        assertThat(parsed.pageUrl).isEqualTo("https://github.com/x/releases/v2.2")
        assertThat(parsed.apk).isEqualTo(ApkAsset("ThorKBM-v2.2.apk", "https://x/ThorKBM-v2.2.apk", 2519952, "abc123"))
    }

    @Test
    fun `a release without an apk has none`() {
        val parsed = Releases.parse(release("v2.2"), installedVersion = "2.1")
        assertThat(parsed.apk).isNull()
        assertThat(parsed.isNewer).isTrue()
    }

    @Test
    fun `the installed release is not an update`() {
        assertThat(Releases.parse(release("v2.1"), installedVersion = "2.1").isNewer).isFalse()
    }

    @Test
    fun `notes keep only the sections newer than the installed version`() {
        val body = "# ThorKBM\n\n## 2.3\n\n- Three\n\n## 2.2\n\n- Two\n\n### Fixes\n\n- Fix\n\n## 2.1\n\n- One\n"
        assertThat(Releases.notesSince(body, "2.1")).isEqualTo("## 2.3\n\n- Three\n\n## 2.2\n\n- Two\n\n### Fixes\n\n- Fix")
        assertThat(Releases.notesSince(body, "2.2")).isEqualTo("## 2.3\n\n- Three")
    }

    @Test
    fun `notes without version sections are kept whole`() {
        assertThat(Releases.notesSince("  Bug fixes.\n", "2.1")).isEqualTo("Bug fixes.")
        assertThat(Releases.notesSince("## 2.0\n\n- Old", "2.1")).isEqualTo("## 2.0\n\n- Old")
    }
}
