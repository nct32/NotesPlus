package com.nct32.notesplus.update

import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal client for the GitHub Releases API of the Notes+ repository.
 *
 * The pure functions ([parseReleases], [latestForChannel], [apkAsset]) are
 * unit-testable without network access; only [fetchReleases] touches the
 * network.
 */
object GitHubApiClient {

    const val REPO = "nct32/NotesPlus"

    /** Base URL of the releases endpoint (newest release first). */
    const val API_BASE = "https://api.github.com/repos/$REPO/releases"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(15))
            .build()
    }

    /**
     * Parses a JSON array of GitHub releases.
     *
     * Pure function: safe to unit test without network access. Unknown keys
     * are ignored so the API can evolve without breaking the app.
     */
    fun parseReleases(json: String): List<GitHubRelease> =
        this.json.decodeFromString<List<GitHubRelease>>(json)

    /**
     * Fetches all releases (newest first) for [REPO] from the GitHub API.
     *
     * @throws IOException if the request fails or the API returns an error status.
     */
    suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(API_BASE)
            // GitHub requires a User-Agent header on all API requests.
            .header("User-Agent", "NotesPlus-Android-UpdateChecker")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("GitHub API request failed: ${response.code} $body")
            }
            parseReleases(body)
        }
    }

    /**
     * Picks the newest release for the given [channel].
     *
     * GitHub returns releases newest-first, so the first match wins:
     * [ReleaseChannel.Stable] → first non-prerelease;
     * [ReleaseChannel.Experimental] → first release overall.
     *
     * @return the newest matching release, or `null` if [releases] is empty
     *   (or, for Stable, contains only prereleases).
     */
    fun latestForChannel(releases: List<GitHubRelease>, channel: ReleaseChannel): GitHubRelease? =
        when (channel) {
            ReleaseChannel.Stable -> releases.firstOrNull { !it.prerelease }
            ReleaseChannel.Experimental -> releases.firstOrNull()
        }

    /** @return the first asset of [release] whose name ends with `.apk`, or `null`. */
    fun apkAsset(release: GitHubRelease): GitHubAsset? =
        release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}
