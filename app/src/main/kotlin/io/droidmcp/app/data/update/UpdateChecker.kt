package io.droidmcp.app.data.update

import android.util.Log
import io.droidmcp.app.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app isn't on Google Play, so nothing tells users about new versions. Once a day (when the
 * app opens) this asks GitHub for the latest release; Home shows a banner when it's newer.
 * Only the public releases API is contacted, with no identifiers beyond what any request carries.
 */
class UpdateChecker(
    private val settings: SettingsStore,
    private val currentVersion: String,
    private val fetchLatestTag: suspend () -> String? = ::fetchFromGitHub,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Check if enabled and the last check is over a day old. Never throws. */
    suspend fun checkIfDue() {
        try {
            val s = settings.snapshot()
            if (!s.checkForUpdates || now() - s.lastUpdateCheck < DAY_MS) return
            checkNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A settings read or write failed (disk full, damaged file): skip this time.
            Log.w(TAG, "Update check skipped", e)
        }
    }

    /**
     * Ask GitHub now. Returns the newer version, or null when up to date or unreachable. A failed
     * attempt is remembered too, so the next one waits an hour instead of every app open.
     */
    suspend fun checkNow(): String? {
        val tag = runCatching { fetchLatestTag() }.getOrNull()
        if (tag == null) {
            settings.update { it.copy(lastUpdateCheck = now() - DAY_MS + RETRY_AFTER_FAILURE_MS) }
            return null
        }
        val latest = tag.removePrefix("v")
        settings.update { it.copy(lastUpdateCheck = now(), latestVersion = latest) }
        return latest.takeIf { isNewer(it, currentVersion) }
    }

    companion object {
        const val RELEASES_URL = "https://github.com/stixez/droid-mcp/releases/latest"
        private const val API_URL = "https://api.github.com/repos/stixez/droid-mcp/releases/latest"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val RETRY_AFTER_FAILURE_MS = 60 * 60 * 1000L
        private const val TAG = "UpdateChecker"

        /** `1.2.10` > `1.2.9`; a `-debug` style suffix on [current] is ignored. Pure, unit-tested. */
        fun isNewer(latest: String, current: String): Boolean {
            val a = parts(latest) ?: return false
            val b = parts(current.substringBefore('-')) ?: return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(version: String): List<Int>? =
            version.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }

        private suspend fun fetchFromGitHub(): String? = withContext(Dispatchers.IO) {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "droid-mcp-app")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.content
            } finally {
                connection.disconnect()
            }
        }
    }
}
