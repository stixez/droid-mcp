package io.droidmcp.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64

/** Light, dark, or whatever the phone uses. */
enum class ThemeMode { System, Light, Dark }

/** A computer with its own token, revocable on its own. */
data class PairedComputer(val name: String, val token: String, val createdAt: Long)

/** Everything the user can configure, as one immutable snapshot. */
data class AppSettings(
    /** Bearer token clients send. Generated once and kept, so pairings survive restarts. */
    val token: String? = null,
    val port: Int = DEFAULT_PORT,
    val readOnly: Boolean = false,
    val tlsEnabled: Boolean = false,
    /** Shell tools refuse sensitive settings and permission grants when true. */
    val strictShellPolicy: Boolean = true,
    /** Opt-in for write_file, move_file, delete_file and create_directory. */
    val allowFileWrites: Boolean = false,
    /** Ask on the phone before tools that send, delete or change things. */
    val confirmRiskyCalls: Boolean = true,
    /** Tools the user switched off; hidden from tools/list and refused by tools/call. */
    val disabledTools: Set<String> = emptySet(),
    val setupCompleted: Boolean = false,
    /** Root was granted once; a restarted process re-opens the root shell on its own. */
    val rootGranted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    /** The user said "Not now" to the screen-control nudge on Home. */
    val essentialsHintDismissed: Boolean = false,
    /** Start the server when the phone finishes booting. */
    val startOnBoot: Boolean = false,
    /** Ask GitHub once a day whether a newer version is out. */
    val checkForUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    /** Newest version GitHub reported, without the leading "v". */
    val latestVersion: String? = null,
    /** The user tapped "Later" on the banner for this version. */
    val dismissedUpdate: String? = null,
    /** Only serve on the Wi-Fi networks in [trustedNetworks]; pause everywhere else. */
    val trustedWifiOnly: Boolean = false,
    /** Wi-Fi names (SSIDs) the server may run on. */
    val trustedNetworks: Set<String> = emptySet(),
    /** Tools that run without asking even when approvals are on. */
    val alwaysAllowedTools: Set<String> = emptySet(),
    /** Computers paired with their own tokens. */
    val pairedComputers: List<PairedComputer> = emptyList(),
) {
    /** The port the server binds: the fixed HTTPS port when HTTPS is on, else [port]. */
    val activePort: Int get() = if (tlsEnabled) TLS_PORT else port

    companion object {
        const val DEFAULT_PORT = 8080
        const val TLS_PORT = 8443
    }
}

/** Settings source the features depend on; [SettingsRepository] is the DataStore-backed one. */
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun snapshot(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)

    /** The persisted token, generating and saving one on first use. */
    suspend fun ensureToken(): String

    /** Replace the token; every client has to re-pair. */
    suspend fun rotateToken(): String
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) : SettingsStore {

    private val store = context.applicationContext.dataStore

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    override suspend fun snapshot(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    override suspend fun ensureToken(): String {
        var token: String? = null
        store.edit { prefs ->
            token = prefs[Keys.TOKEN] ?: newToken().also { prefs[Keys.TOKEN] = it }
        }
        return checkNotNull(token)
    }

    override suspend fun rotateToken(): String {
        val fresh = newToken()
        store.edit { it[Keys.TOKEN] = fresh }
        return fresh
    }

    private fun Preferences.toSettings() = AppSettings(
        token = this[Keys.TOKEN],
        port = this[Keys.PORT] ?: AppSettings.DEFAULT_PORT,
        readOnly = this[Keys.READ_ONLY] ?: false,
        tlsEnabled = this[Keys.TLS] ?: false,
        strictShellPolicy = this[Keys.STRICT_SHELL] ?: true,
        allowFileWrites = this[Keys.FILE_WRITES] ?: false,
        confirmRiskyCalls = this[Keys.CONFIRM_RISKY] ?: true,
        disabledTools = this[Keys.DISABLED_TOOLS] ?: emptySet(),
        setupCompleted = this[Keys.SETUP_DONE] ?: false,
        rootGranted = this[Keys.ROOT_GRANTED] ?: false,
        themeMode = this[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
        essentialsHintDismissed = this[Keys.ESSENTIALS_HINT_DISMISSED] ?: false,
        startOnBoot = this[Keys.START_ON_BOOT] ?: false,
        checkForUpdates = this[Keys.CHECK_UPDATES] ?: true,
        lastUpdateCheck = this[Keys.LAST_UPDATE_CHECK] ?: 0L,
        latestVersion = this[Keys.LATEST_VERSION],
        dismissedUpdate = this[Keys.DISMISSED_UPDATE],
        trustedWifiOnly = this[Keys.TRUSTED_WIFI_ONLY] ?: false,
        trustedNetworks = this[Keys.TRUSTED_NETWORKS] ?: emptySet(),
        alwaysAllowedTools = this[Keys.ALWAYS_ALLOWED] ?: emptySet(),
        pairedComputers = decodeComputers(this[Keys.PAIRED_COMPUTERS] ?: emptySet()),
    )

    private fun MutablePreferences.write(s: AppSettings) {
        if (s.token != null) this[Keys.TOKEN] = s.token else remove(Keys.TOKEN)
        this[Keys.PORT] = s.port
        this[Keys.READ_ONLY] = s.readOnly
        this[Keys.TLS] = s.tlsEnabled
        this[Keys.STRICT_SHELL] = s.strictShellPolicy
        this[Keys.FILE_WRITES] = s.allowFileWrites
        this[Keys.CONFIRM_RISKY] = s.confirmRiskyCalls
        this[Keys.DISABLED_TOOLS] = s.disabledTools
        this[Keys.SETUP_DONE] = s.setupCompleted
        this[Keys.ROOT_GRANTED] = s.rootGranted
        this[Keys.THEME] = s.themeMode.name
        this[Keys.ESSENTIALS_HINT_DISMISSED] = s.essentialsHintDismissed
        this[Keys.START_ON_BOOT] = s.startOnBoot
        this[Keys.CHECK_UPDATES] = s.checkForUpdates
        this[Keys.LAST_UPDATE_CHECK] = s.lastUpdateCheck
        if (s.latestVersion != null) this[Keys.LATEST_VERSION] = s.latestVersion else remove(Keys.LATEST_VERSION)
        if (s.dismissedUpdate != null) this[Keys.DISMISSED_UPDATE] = s.dismissedUpdate else remove(Keys.DISMISSED_UPDATE)
        this[Keys.TRUSTED_WIFI_ONLY] = s.trustedWifiOnly
        this[Keys.TRUSTED_NETWORKS] = s.trustedNetworks
        this[Keys.ALWAYS_ALLOWED] = s.alwaysAllowedTools
        this[Keys.PAIRED_COMPUTERS] = encodeComputers(s.pairedComputers)
    }

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val PORT = intPreferencesKey("port")
        val READ_ONLY = booleanPreferencesKey("read_only")
        val TLS = booleanPreferencesKey("tls")
        val STRICT_SHELL = booleanPreferencesKey("strict_shell")
        val FILE_WRITES = booleanPreferencesKey("file_writes")
        val CONFIRM_RISKY = booleanPreferencesKey("confirm_risky")
        val DISABLED_TOOLS = stringSetPreferencesKey("disabled_tools")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val ROOT_GRANTED = booleanPreferencesKey("root_granted")
        val THEME = stringPreferencesKey("theme")
        val ESSENTIALS_HINT_DISMISSED = booleanPreferencesKey("essentials_hint_dismissed")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val CHECK_UPDATES = booleanPreferencesKey("check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val LATEST_VERSION = stringPreferencesKey("latest_version")
        val DISMISSED_UPDATE = stringPreferencesKey("dismissed_update")
        val TRUSTED_WIFI_ONLY = booleanPreferencesKey("trusted_wifi_only")
        val TRUSTED_NETWORKS = stringSetPreferencesKey("trusted_networks")
        val ALWAYS_ALLOWED = stringSetPreferencesKey("always_allowed_tools")
        val PAIRED_COMPUTERS = stringSetPreferencesKey("paired_computers")
    }

    companion object {
        private val random = SecureRandom()
        private const val FIELD_SEPARATOR = '\u001f'

        /** One entry per computer: name, token and time joined by a unit separator nobody types. */
        internal fun encodeComputers(list: List<PairedComputer>): Set<String> =
            list.map { listOf(it.name, it.token, it.createdAt.toString()).joinToString(FIELD_SEPARATOR.toString()) }.toSet()

        internal fun decodeComputers(raw: Set<String>): List<PairedComputer> =
            raw.mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size != 3) return@mapNotNull null
                PairedComputer(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
            }.sortedBy { it.createdAt }

        /** 32 random bytes, URL-safe base64: 43 chars, well above the server's 16-char floor. */
        fun newToken(): String {
            val bytes = ByteArray(32).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
