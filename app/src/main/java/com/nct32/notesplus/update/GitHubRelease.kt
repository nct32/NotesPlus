package com.nct32.notesplus.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single asset attached to a [GitHubRelease] (e.g. an APK download).
 *
 * Mirrors the `assets` entries of the GitHub Releases API
 * (https://docs.github.com/en/rest/releases/releases).
 */
@Serializable
data class GitHubAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browser_download_url: String = "",
    @SerialName("size") val size: Long = 0,
)

/**
 * A GitHub release, as returned by the GitHub Releases API.
 *
 * Only the fields the update flow needs are modeled; unknown JSON keys are
 * ignored by the parser.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tag_name: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("published_at") val published_at: String? = null,
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
)
