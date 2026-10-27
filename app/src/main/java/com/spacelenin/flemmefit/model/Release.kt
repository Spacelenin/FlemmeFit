package com.spacelenin.flemmefit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

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
        return info(release)
    }

    /** Lit la réponse de `GET /repos/{owner}/{repo}/releases` : les versions utilisables, dans l'ordre reçu. */
    fun parseList(json: String): List<ReleaseInfo> {
        val releases = try {
            PlanParser.json.decodeFromString(ListSerializer(GithubRelease.serializer()), json)
        } catch (e: SerializationException) {
            return emptyList()
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
        return releases.mapNotNull(::info)
    }

    /**
     * Notes de toutes les versions après [after] jusqu'à [upTo] incluse, de la plus récente à la plus ancienne :
     * qui saute des versions voit tout ce qu'il a raté. Une seule version : ses notes telles quelles.
     */
    fun combinedNotes(releases: List<ReleaseInfo>, after: Int, upTo: Int): String? {
        val withNotes = releases.filter { it.versionCode in (after + 1)..upTo && it.notes != null }
            .distinctBy { it.versionCode }
            .sortedByDescending { it.versionCode }
        return when (withNotes.size) {
            0 -> null
            1 -> withNotes[0].notes
            else -> withNotes.joinToString("\n\n") { "Build ${it.versionCode}\n${it.notes}" }
        }
    }

    private fun info(release: GithubRelease): ReleaseInfo? {
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
