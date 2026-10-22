package com.spacelenin.flemmefit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** Une version publiée dans les Releases GitHub (tag `build-N`, N = versionCode). */
data class ReleaseInfo(
    val versionCode: Int,
    val name: String,
    val notes: String?,
    val apkUrl: String,
    val apkSize: Long,
    /** Empreinte SHA-256 de l'APK en hexadécimal, si GitHub la fournit. */
    val sha256: String?,
)

object Releases {

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GithubAsset> = emptyList(),
    )

    @Serializable
    private data class GithubAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
        val digest: String? = null,
    )

    /** Lignes techniques des messages de commit (signatures, liens de session) : sans intérêt dans l'appli. */
    private val noise = Regex("""^(Co-Authored-By:|Claude-Session:|🤖|https://claude\.ai/)""", RegexOption.IGNORE_CASE)

    fun cleanNotes(body: String): String? =
        body.lines().filterNot { noise.containsMatchIn(it.trim()) }.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n").trim().takeIf { it.isNotEmpty() }

    private val tagPattern = Regex("""^build-(\d+)$""")

    /** Lit la réponse de `GET /repos/{owner}/{repo}/releases/latest`. null si elle ne contient pas d'APK utilisable. */
    fun parseLatest(json: String): ReleaseInfo? {
        val release = try {
            PlanParser.json.decodeFromString(GithubRelease.serializer(), json)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (release.draft || release.prerelease) return null
        val code = tagPattern.matchEntire(release.tagName)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return ReleaseInfo(
            versionCode = code,
            name = release.name ?: release.tagName,
            notes = release.body?.let(::cleanNotes),
            apkUrl = apk.url,
            apkSize = apk.size,
            sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
        )
    }
}
