package com.spacelenin.flemmefit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseTest {

    private fun release(tag: String = "build-7", assets: String = APK, extra: String = "") =
        """{"tag_name":"$tag","name":"FlemmeFit build 7","body":"  Nouveautés  ","draft":false,"prerelease":false,
           "id":1,"author":{"login":"bot"}$extra,"assets":[$assets]}"""

    private val APK = """{"name":"FlemmeFit-7.apk","size":1420519,"digest":"sha256:61EE5E",
        "browser_download_url":"https://github.com/Spacelenin/FlemmeFit/releases/download/build-7/FlemmeFit-7.apk"}"""

    @Test
    fun `parses the latest release`() {
        val info = Releases.parseLatest(release())!!
        assertEquals(7, info.versionCode)
        assertEquals("FlemmeFit build 7", info.name)
        assertEquals("Nouveautés", info.notes)
        assertEquals(1420519, info.apkSize)
        assertEquals("61ee5e", info.sha256)
        assertEquals("https://github.com/Spacelenin/FlemmeFit/releases/download/build-7/FlemmeFit-7.apk", info.apkUrl)
    }

    @Test
    fun `ignores releases without a usable apk or tag`() {
        assertNull(Releases.parseLatest(release(tag = "v1.0")))
        assertNull(Releases.parseLatest(release(assets = """{"name":"notes.txt","browser_download_url":"x"}""")))
        assertNull(Releases.parseLatest(release(extra = ""","prerelease_flag":1""").replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(Releases.parseLatest("""{"message":"Not Found"}"""))
        assertNull(Releases.parseLatest("<html>"))
    }

    @Test
    fun `release notes drop commit signatures`() {
        val body = "Mise à jour obligatoire\n\n- Installer ou quitter\n\nCo-Authored-By: X <x@y>\nClaude-Session: https://claude.ai/code/abc\n\n🤖 Generated with Claude Code\n\nhttps://claude.ai/code/session_1"
        assertEquals("Mise à jour obligatoire\n\n- Installer ou quitter", Releases.cleanNotes(body))
        assertNull(Releases.cleanNotes("Co-Authored-By: X"))
    }
}
