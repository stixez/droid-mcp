package io.droidmcp.app.data.server

import android.content.Context
import android.location.LocationManager
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.droidmcp.app.confirm.ConfirmationCenter
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.permissions.PermissionSource
import io.droidmcp.app.data.settings.AppSettings
import io.droidmcp.app.data.settings.PairedComputer
import io.droidmcp.app.data.settings.SettingsRepository
import io.droidmcp.app.data.settings.SettingsStore
import io.droidmcp.app.data.tools.ToolCatalog
import io.droidmcp.app.service.McpServerService
import io.droidmcp.calendar.CalendarTools
import io.droidmcp.contacts.ContactsTools
import io.droidmcp.core.AuditSink
import io.droidmcp.core.DroidMcp
import io.droidmcp.core.ToolCallConfirmer
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.transport.TokenStore
import io.droidmcp.files.FilesTools
import io.droidmcp.media.MediaTools
import io.droidmcp.server.DroidMcpServerService
import io.droidmcp.tls.SelfSignedCert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.BindException

/** What the server is doing right now. */
sealed interface ServerStatus {
    data object Stopped : ServerStatus
    data object Starting : ServerStatus

    /** [port], [tls] and [token] are what the live server uses, which may differ from saved settings. */
    data class Running(
        val port: Int,
        val tls: Boolean,
        val token: String,
        val toolCount: Int,
        val startedAt: Long,
    ) : ServerStatus

    /** The service is up but not listening, because the phone isn't on a trusted Wi-Fi. */
    data class Paused(val reason: String) : ServerStatus

    data class Failed(val reason: String) : ServerStatus
}

/** The settings that are baked into a server when it's built; changing them needs a restart. */
private data class BakedSettings(
    val port: Int,
    val readOnly: Boolean,
    val tls: Boolean,
    val strictShellPolicy: Boolean,
    val allowFileWrites: Boolean,
    val confirmRiskyCalls: Boolean,
    val token: String?,
) {
    // With HTTPS the server always binds the HTTPS port, so the HTTP port doesn't matter then.
    constructor(s: AppSettings) : this(
        s.activePort,
        s.readOnly, s.tlsEnabled, s.strictShellPolicy, s.allowFileWrites, s.confirmRiskyCalls, s.token,
    )
}

/** What the features need from the server; [ServerController] is the real one. */
interface ServerControl {
    val status: StateFlow<ServerStatus>

    /** True while running with settings that only take effect after a restart. */
    val restartNeeded: StateFlow<Boolean>

    /** SHA-256 of the TLS certificate while TLS is on, for clients that pin it. */
    val tlsFingerprint: StateFlow<String?>

    /**
     * Trusted Wi-Fi paused the server while the app was closed, only because Android hid the
     * Wi-Fi name from a background app. "All the time" location would have avoided it.
     */
    val pausedWhileClosed: StateFlow<Boolean>

    fun dismissPausedWhileClosed()

    fun start()
    fun stop()

    /** Restart a running server so saved settings apply; starts a stopped one. */
    fun restart()

    /** Restart only if running (e.g. after the token changed); a stopped server stays stopped. */
    fun restartIfRunning()

    /** Switch tools on or off. Persisted, and applied to a running server at once. */
    fun setToolsEnabled(names: Set<String>, enabled: Boolean)
}

/** Computers with their own tokens. */
interface PairingControl {
    /** Create a token for a new computer named [name]; replaces one with the same name. */
    suspend fun addComputer(name: String): PairedComputer

    /** The computer's token stops working at once. */
    suspend fun revokeComputer(name: String)
}

/**
 * Owns the one [DroidMcp] instance for the whole app. It lives in the app graph rather than in a
 * ViewModel, so the server outlives every screen, and the foreground service can rebuild it on
 * its own when Android restarts the process.
 *
 * Flow: [start] launches [McpServerService]; the service registers itself ([onServiceCreated]),
 * calls [createServer] on a background thread and reports back through [onServiceStarted] /
 * [onServiceFailed] / [onServiceStopped]. Every callback names its service instance, and only the
 * newest one counts, so a slow build from a service that was already stopped can't publish itself.
 *
 * With "trusted Wi-Fi only" on, the controller pauses the listener (the service stays, so it can
 * resume without a background service start, which Android forbids) whenever the phone leaves
 * the trusted networks, and resumes when it's back.
 */
class ServerController(
    private val context: Context,
    private val settings: SettingsStore,
    private val catalog: ToolCatalog,
    private val permissions: PermissionSource,
    /** Called on the build thread, so a lazily built sink isn't created on the main thread. */
    private val auditSink: () -> AuditSink,
    private val confirmer: ToolCallConfirmer,
    /** Whether an approval is waiting for the user; see [ConfirmationCenter.UI_DRIVERS]. */
    private val pendingApproval: () -> Boolean = { false },
    private val scope: CoroutineScope,
    private val network: Flow<LocalNetwork>,
    private val currentNetwork: () -> LocalNetwork,
) : ServerControl, PairingControl {

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    override val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _tlsFingerprint = MutableStateFlow<String?>(null)
    override val tlsFingerprint: StateFlow<String?> = _tlsFingerprint.asStateFlow()

    private val _pausedWhileClosed = MutableStateFlow(false)
    override val pausedWhileClosed: StateFlow<Boolean> = _pausedWhileClosed.asStateFlow()

    /** Latest saved settings, for quick synchronous reads. Starts with defaults until DataStore loads. */
    val currentSettings: StateFlow<AppSettings> =
        settings.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** The full settings the running server was built with; null when no server. */
    private val built = MutableStateFlow<AppSettings?>(null)

    override val restartNeeded: StateFlow<Boolean> =
        combine(currentSettings, built, _status) { current, used, status ->
            status is ServerStatus.Running && used != null && BakedSettings(used) != BakedSettings(current)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    @Volatile
    private var server: DroidMcp? = null

    /** The service instance that currently owns the server; older instances are ignored. */
    @Volatile
    private var activeOwner: Any? = null

    /** Port the last build tried to bind, for error messages. */
    @Volatile
    private var attemptedPort: Int = AppSettings.DEFAULT_PORT

    /** Serialises builds, tool syncs and gating so they can't interleave. */
    private val buildLock = Mutex()

    private var restartPending = false

    /** A restart was asked for while starting (e.g. the token changed); do it once running. */
    private var restartAfterStart = false

    /**
     * The service instance the app itself stopped (user, restart, failed resume). Its end isn't a
     * refused start, even while a newer start() already says Starting.
     */
    @Volatile
    private var stoppedOwner: Any? = null

    /** Counts start() calls; compared with [startsAtStop] to see a start that came after a stop. */
    @Volatile
    private var starts = 0

    @Volatile
    private var startsAtStop = 0

    /** Guards [activeOwner] together with the server fields, against a build finishing during a stop. */
    private val ownerLock = Any()

    /** When the last service stopped, to tell a port still held by it from a real conflict. */
    @Volatile
    private var lastStopAt = 0L

    private var bindRetried = false

    /** Which shell backend the registered shell tools use; a switch needs new instances. */
    private var builtShellBackend: String? = null

    /**
     * The service that has reached the foreground, if any. Stopping a service started with
     * startForegroundService before it gets there crashes the app, so such a stop waits.
     */
    private var foregroundOwner: Any? = null
    private var stopWhenForeground = false

    /** The trust verdict the network flow last produced, for a server that starts later. */
    @Volatile
    private var latestVerdict: String? = null
    @Volatile
    private var verdictKnown = false

    init {
        // Grants and revocations while running: add or drop the affected tools in place. The SDK
        // announces the change to connected clients (notifications/tools/list_changed).
        scope.launch {
            permissions.granted.drop(1).collect { granted ->
                rememberRoot(granted)
                syncTools()
            }
        }
        // Trusted Wi-Fi only: pause and resume as the network changes.
        scope.launch {
            combine(network, currentSettings) { net, s -> trustVerdict(net, s) }
                .onEach { latestVerdict = it; verdictKnown = true }
                .distinctUntilChanged()
                .collect { reason -> applyTrust(reason) }
        }
    }

    override fun start() {
        val current = _status.value
        if (current is ServerStatus.Running || current is ServerStatus.Starting || current is ServerStatus.Paused) return
        starts++
        stopWhenForeground = false
        _status.value = ServerStatus.Starting
        try {
            DroidMcpServerService.start(context, McpServerService::class.java)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException when started from the background.
            Log.e(TAG, "Couldn't start the server service", e)
            _status.value = ServerStatus.Failed(BACKGROUND_START_REFUSED)
        }
    }

    override fun stop() {
        restartPending = false
        restartAfterStart = false
        stopService()
    }

    private fun stopService() {
        if (_status.value == ServerStatus.Starting && foregroundOwner == null) {
            // Asked to stop before the service reached the foreground; stop when it does.
            stopWhenForeground = true
            return
        }
        stoppedOwner = activeOwner
        startsAtStop = starts
        DroidMcpServerService.stop(context, McpServerService::class.java)
        if (_status.value !is ServerStatus.Running) _status.value = ServerStatus.Stopped
    }

    override fun restart() {
        val current = _status.value
        if (current !is ServerStatus.Running && current !is ServerStatus.Paused) {
            start()
            return
        }
        restartPending = true
        stopService()
    }

    override fun restartIfRunning() {
        when (_status.value) {
            is ServerStatus.Running, is ServerStatus.Paused -> restart()
            // A build in progress may already hold the old settings (e.g. the old token).
            ServerStatus.Starting -> restartAfterStart = true
            else -> Unit
        }
    }

    override fun setToolsEnabled(names: Set<String>, enabled: Boolean) {
        scope.launch {
            settings.update { s ->
                s.copy(disabledTools = if (enabled) s.disabledTools - names else s.disabledTools + names)
            }
            buildLock.withLock {
                val running = server ?: return@withLock
                running.setDisabledTools(settings.snapshot().disabledTools intersect running.registeredNames())
                refreshToolCount(running)
            }
        }
    }

    override suspend fun addComputer(name: String): PairedComputer {
        val computer = PairedComputer(name.trim(), SettingsRepository.newToken(), System.currentTimeMillis())
        // Under the build lock: a build in progress either sees this computer in its snapshot or
        // has finished, so the live server gets it here. Never neither.
        buildLock.withLock {
            settings.update { s -> s.copy(pairedComputers = s.pairedComputers.filterNot { it.name == computer.name } + computer) }
            server?.restoreClient(computer.toClient())
        }
        return computer
    }

    override suspend fun revokeComputer(name: String) {
        buildLock.withLock {
            settings.update { s -> s.copy(pairedComputers = s.pairedComputers.filterNot { it.name == name }) }
            server?.revokeClient(name)
        }
    }

    // ---- Called by McpServerService -------------------------------------------------------

    fun onServiceCreated(owner: Any) {
        synchronized(ownerLock) { activeOwner = owner }
    }

    /** The service is in the foreground now, so stopping it is safe. */
    fun onServiceForeground(owner: Any) {
        if (owner !== activeOwner) return
        foregroundOwner = owner
        if (stopWhenForeground) {
            stopWhenForeground = false
            stopService()
        }
    }

    /** Build the server from the saved settings. Runs on [owner]'s background thread. */
    fun createServer(owner: Any): DroidMcp = runBlocking(Dispatchers.IO) {
        buildLock.withLock {
            // A stopped service's build must not run: it would bind the port its successor needs.
            if (owner !== activeOwner) throw CancellationException("superseded")
            _status.value = ServerStatus.Starting
            val token = settings.ensureToken()
            val snapshot = settings.snapshot()
            attemptedPort = snapshot.activePort

            // After a process restart nothing has opened a root shell yet, so root reads as "not
            // granted". Re-open it when the user granted root before; Magisk remembers the answer.
            if (snapshot.rootGranted) runCatching { Shell.getShell() }
            permissions.refresh()

            val tools = catalog.availableTools(snapshot, permissions.granted.value)
            val builder = DroidMcp.builder()
                .addTools(tools)
                .enableHttpServer(
                    port = snapshot.port,
                    token = token,
                    readOnly = snapshot.readOnly,
                    context = context,
                )
                .withAuditSink(auditSink())
                .addBuiltInPrompts()
            // Providers check their own permissions and stay empty without them.
            listOf(
                MediaTools.resources(context),
                FilesTools.resources(),
                ContactsTools.resources(context),
                CalendarTools.resources(context),
            ).forEach(builder::addResourceProvider)
            if (snapshot.confirmRiskyCalls) {
                builder.confirmToolCalls(
                    // UI tools that never ask (guide_user) still go through the confirmer while an
                    // approval waits, so it can refuse them then.
                    requiresConfirmation = { tool ->
                        ToolRegistry.CONFIRM_BY_DEFAULT(tool) ||
                            (tool.name in ConfirmationCenter.UI_DRIVERS && pendingApproval())
                    },
                    confirmer = confirmer,
                )
            }
            if (snapshot.tlsEnabled) {
                builder.enableTls(
                    SelfSignedCert.loadOrCreate(File(context.filesDir, TLS_KEYSTORE), httpsPort = AppSettings.TLS_PORT),
                )
            }

            val fresh = builder.build()
            fresh.setDisabledTools(snapshot.disabledTools intersect tools.map { it.name }.toSet())
            snapshot.pairedComputers.forEach { runCatching { fresh.restoreClient(it.toClient()) } }
            // Checked again after the slow part (TLS key, root shell): a stop may have come in. The
            // check and the writes are one step, so onServiceStopped can't clear in between.
            synchronized(ownerLock) {
                if (owner !== activeOwner) throw CancellationException("superseded")
                server = fresh
                built.value = snapshot.copy(token = token)
                _tlsFingerprint.value = fresh.tlsFingerprint
                builtShellBackend = shellBackend()
            }
            fresh
        }
    }

    fun onServiceStarted(owner: Any, started: DroidMcp) {
        if (owner !== activeOwner || started !== server) return
        bindRetried = false
        val s = built.value ?: return
        _status.value = runningStatus(started, s)
        if (restartAfterStart) {
            restartAfterStart = false
            restart()
            return
        }
        // Started (by hand, at boot or by Android) on a network that isn't trusted: pause at once.
        // Use the verdict the network flow last produced, not a fresh read: a pause it didn't see
        // could never be undone by it, as a repeat of that verdict is filtered out.
        val reason = if (verdictKnown) latestVerdict else trustVerdict(currentNetwork(), currentSettings.value)
        reason?.let { scope.launch { pause(it) } }
    }

    fun onServiceFailed(owner: Any, error: Exception) {
        if (owner !== activeOwner) return
        clearServer()
        restartPending = false
        restartAfterStart = false
        // The previous instance may still be releasing the port; one quiet retry covers that.
        val portBusy = error is BindException || error.cause is BindException
        if (portBusy && !bindRetried && System.currentTimeMillis() - lastStopAt < PORT_RELEASE_WINDOW_MS) {
            bindRetried = true
            _status.value = ServerStatus.Stopped
            scope.launch {
                delay(PORT_RETRY_DELAY_MS)
                start()
            }
            return
        }
        bindRetried = false
        _status.value = ServerStatus.Failed(describe(error))
    }

    fun onServiceStopped(owner: Any) {
        synchronized(ownerLock) {
            if (owner !== activeOwner) return
            activeOwner = null
            if (foregroundOwner === owner) foregroundOwner = null
            clearServer()
        }
        lastStopAt = System.currentTimeMillis()
        val stoppedOnPurpose = owner === stoppedOwner
        when {
            _status.value is ServerStatus.Failed -> Unit
            // Stopped, then started again before this instance finished: the new one is on its way.
            stoppedOnPurpose && starts != startsAtStop && _status.value == ServerStatus.Starting -> Unit
            // The service ended before it ever ran and nobody asked it to: Android refused it.
            _status.value == ServerStatus.Starting && !stoppedOnPurpose && !restartPending ->
                _status.value = ServerStatus.Failed(BACKGROUND_START_REFUSED)
            else -> _status.value = ServerStatus.Stopped
        }
        if (restartPending) {
            restartPending = false
            start()
        }
    }

    // ---- Internals -------------------------------------------------------------------------

    private fun clearServer() {
        server = null
        built.value = null
        _tlsFingerprint.value = null
    }

    /** Null when the network is fine to serve on, else why not. */
    private fun trustVerdict(net: LocalNetwork, s: AppSettings): String? {
        if (!s.trustedWifiOnly) return null
        if (!net.onWifi) return "Paused: not on Wi-Fi"
        val ssid = net.ssid ?: return hiddenSsidReason()
        return if (ssid in s.trustedNetworks) null else "Paused: \"$ssid\" isn't a trusted network"
    }

    /** Why the Wi-Fi name can't be read, in words the user can act on. */
    private fun hiddenSsidReason(): String = when {
        Capabilities.WIFI !in permissions.granted.value ->
            "Paused: can't read the Wi-Fi name. Allow Wi-Fi details in droid-mcp."
        context.getSystemService(LocationManager::class.java)?.isLocationEnabled == false ->
            "Paused: can't read the Wi-Fi name while Location is off."
        // Android shows the name only to an app in use; after a restart or a network change in the
        // background that isn't us yet. Opening the app reads it again.
        else -> HIDDEN_IN_BACKGROUND
    }

    private fun paused(reason: String): ServerStatus.Paused {
        if (reason == HIDDEN_IN_BACKGROUND && !Capabilities.BACKGROUND_LOCATION.isGranted(context)) {
            _pausedWhileClosed.value = true
        }
        return ServerStatus.Paused(reason)
    }

    override fun dismissPausedWhileClosed() {
        _pausedWhileClosed.value = false
    }

    private suspend fun applyTrust(reason: String?) {
        val current = _status.value
        when {
            reason != null && current is ServerStatus.Running -> pause(reason)
            reason != null && current is ServerStatus.Paused -> _status.value = paused(reason)
            reason == null && current is ServerStatus.Paused -> resume()
        }
    }

    private suspend fun pause(reason: String) = withContext(Dispatchers.IO) {
        buildLock.withLock {
            val running = server ?: return@withLock
            runCatching { running.stopServer() }
            _status.value = paused(reason)
        }
    }

    private suspend fun resume() = withContext(Dispatchers.IO) {
        buildLock.withLock {
            val paused = server ?: return@withLock
            val s = built.value ?: return@withLock
            try {
                paused.startServer()
                _status.value = runningStatus(paused, s)
            } catch (e: Exception) {
                // End the service too: it still holds the dead server and would swallow the next
                // start (its onStartCommand returns early while it has one), stuck at Starting.
                withContext(Dispatchers.Main) { stopService() }
                _status.value = ServerStatus.Failed(describe(e))
            }
        }
    }

    private fun runningStatus(running: DroidMcp, s: AppSettings) = ServerStatus.Running(
        port = s.activePort,
        tls = s.tlsEnabled,
        token = s.token.orEmpty(),
        toolCount = visibleToolCount(running, s),
        startedAt = System.currentTimeMillis(),
    )

    /** What clients see in tools/list: read-only mode hides every tool that changes something. */
    private fun visibleToolCount(running: DroidMcp, s: AppSettings): Int =
        running.listTools().count { !s.readOnly || it.annotations.readOnlyHint }

    /**
     * Register newly allowed tools and drop ones whose permission went away. Uses the settings
     * the server was built with, so changes that wait for a restart stay waiting.
     */
    private suspend fun syncTools() = withContext(Dispatchers.Default) {
        buildLock.withLock {
            val running = server ?: return@withLock
            val usedSettings = built.value ?: return@withLock
            val disabled = settings.snapshot().disabledTools
            val wanted = catalog.availableTools(usedSettings, permissions.granted.value)
            val wantedNames = wanted.map { it.name }.toSet()
            val current = running.registeredNames()
            val backend = shellBackend()
            // Same tools as before: nothing to tell clients (each change sends list_changed).
            if (wantedNames == current && backend == builtShellBackend) return@withLock
            builtShellBackend = backend
            // Gate first, so a tool the user switched off is never listed, even for a moment.
            running.setDisabledTools(disabled intersect wantedNames)
            (current - wantedNames).forEach(running::unregisterTool)
            // Register every wanted tool, not only new names: instances can change behind the same
            // name (the shell tools switch between Shizuku and root).
            running.registerTools(wanted)
            refreshToolCount(running)
        }
    }

    private fun refreshToolCount(running: DroidMcp) {
        val status = _status.value
        val s = built.value ?: return
        if (status is ServerStatus.Running) _status.value = status.copy(toolCount = visibleToolCount(running, s))
    }

    /** Must match the shell group in [ToolCatalog], which builds the root tools when root is granted. */
    private fun shellBackend(): String? = permissions.granted.value.let {
        when {
            Capabilities.ROOT in it -> Capabilities.ROOT
            Capabilities.SHIZUKU in it -> Capabilities.SHIZUKU
            else -> null
        }
    }

    /** Remember a root grant so a restarted process can re-open the root shell on its own. */
    private suspend fun rememberRoot(granted: Set<String>) {
        if (Capabilities.ROOT in granted && !currentSettings.value.rootGranted) {
            settings.update { it.copy(rootGranted = true) }
        }
    }

    private fun describe(error: Exception): String = when {
        error is BindException || error.cause is BindException ->
            if (attemptedPort == AppSettings.TLS_PORT) {
                "Port ${AppSettings.TLS_PORT} (HTTPS) is already in use by another app. Turn HTTPS off or close that app."
            } else {
                "Port $attemptedPort is already in use. Pick another port in Settings."
            }
        else -> error.message ?: "The server couldn't start."
    }

    private companion object {
        const val TAG = "ServerController"
        const val TLS_KEYSTORE = "droid-mcp-tls.p12"
        const val PORT_RELEASE_WINDOW_MS = 5_000L
        const val PORT_RETRY_DELAY_MS = 800L
        const val BACKGROUND_START_REFUSED = "Android didn't let the server start in the background. Open the app and try again."
        const val HIDDEN_IN_BACKGROUND =
            "Paused: Android hides the Wi-Fi name from apps in the background. Open droid-mcp to check this network."
    }
}

private fun PairedComputer.toClient() = TokenStore.PairedClient(label = name, token = token, createdAt = createdAt)

/**
 * Every tool name in the registry. Gating only ever disables registered names (callers intersect),
 * so enabled + disabled is the whole registry.
 */
private fun DroidMcp.registeredNames(): Set<String> = listTools().map { it.name }.toSet() + disabledTools()
