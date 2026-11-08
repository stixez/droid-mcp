package io.droidmcp.app

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import io.droidmcp.app.confirm.ApprovalPolicy
import io.droidmcp.app.confirm.ConfirmationCenter
import io.droidmcp.app.data.update.UpdateChecker
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.permissions.PermissionRepository
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.NetworkAddress
import io.droidmcp.app.data.server.ServerController
import io.droidmcp.app.data.settings.SettingsRepository
import io.droidmcp.app.data.tools.ToolCatalog
import io.droidmcp.app.service.McpNotificationListenerService
import io.droidmcp.audit.RoomAuditSink
import io.droidmcp.overlay.GuideOverlay
import io.droidmcp.playback.NotificationListenerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.lang.ref.WeakReference

/**
 * The app's object graph, built once in [DroidMcpApp]. Plain constructor injection: every
 * dependency is visible here, and tests build the pieces with fakes instead.
 */
class AppGraph(app: Application) {

    init {
        // Before anything checks permissions: notification-access checks read this holder.
        NotificationListenerHolder.set(ComponentName(app, McpNotificationListenerService::class.java))
    }

    /** Lives as long as the process; owns the server and the settings cache. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val foreground = ForegroundTracker()

    val settings = SettingsRepository(app)
    val permissions = PermissionRepository(app)
    val network = NetworkAddress(app)

    /**
     * One network callback for everyone (server, notification, screens) instead of one each. Also
     * re-read when Wi-Fi details access changes: the Wi-Fi name becomes readable without any network
     * event, and a server paused for "can't read the Wi-Fi name" must notice. And re-read when the
     * app comes to the front: Android hides the name from background apps, and the network callback
     * doesn't fire again just because it became readable.
     */
    val networkUpdates: Flow<LocalNetwork> = merge(
        network.observe(),
        permissions.granted
            .map { Capabilities.WIFI in it }
            .distinctUntilChanged()
            .drop(1)
            .map { network.current() },
        foreground.isForeground
            .drop(1)
            .filter { it }
            .map { network.current() },
    )
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(appScope, SharingStarted.WhileSubscribed(SHARE_STOP_MS), replay = 1)

    /**
     * Every HTTP tools/call, kept for 7 days. Backs the Activity screen. Built on first use, not in
     * Application.onCreate: right after boot a busy phone can take long enough setting up the
     * database that Android gives up on starting the process (seen as an ANR on Android 15).
     */
    val audit: RoomAuditSink by lazy { RoomAuditSink(app) }

    val approvals = ApprovalPolicy(settings, appScope)

    val confirmations = ConfirmationCenter(app, isAppInForeground = { foreground.isForeground.value }, policy = approvals)

    val updates = UpdateChecker(settings, BuildConfig.VERSION_NAME)

    val catalog = ToolCatalog(app, currentActivity = { foreground.currentActivity() })

    val server = ServerController(
        context = app,
        settings = settings,
        catalog = catalog,
        permissions = permissions,
        auditSink = { audit },
        confirmer = confirmations,
        pendingApproval = confirmations::hasPending,
        scope = appScope,
        network = networkUpdates,
        currentNetwork = network::current,
    )

    init {
        // Shizuku's binder arrives asynchronously after process start, and can die with the
        // Shizuku app. Re-check so the shell tools appear or disappear on their own.
        Shizuku.addBinderReceivedListenerSticky { permissions.refresh() }
        Shizuku.addBinderDeadListener { permissions.refresh() }

        // An approval shown as an in-app dialog follows the user out of the app as a notification,
        // and the notification goes away again when they come back to the dialog.
        appScope.launch {
            foreground.isForeground.drop(1).collect { visible ->
                if (visible) confirmations.onAppForegrounded() else confirmations.onAppBackgrounded()
            }
        }
        // A guide on screen could cover an approval's details; take it down when one appears.
        appScope.launch {
            confirmations.pending
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { waiting -> if (waiting) GuideOverlay.shared(app).hide() }
        }
    }
}

private const val SHARE_STOP_MS = 5_000L

/** Whether an activity of ours is on screen, and which one (printing needs an Activity). */
class ForegroundTracker {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    @Volatile
    private var resumed: WeakReference<Activity>? = null

    fun onStart() {
        _isForeground.value = true
    }

    fun onStop() {
        _isForeground.value = false
    }

    fun onResume(activity: Activity) {
        resumed = WeakReference(activity)
    }

    fun onPause(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    fun currentActivity(): Activity? = resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}

class DroidMcpApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** The graph from any context: `context.appGraph.server`. */
val Context.appGraph: AppGraph get() = (applicationContext as DroidMcpApp).graph
