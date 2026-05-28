package io.droidmcp.sample

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.pm.PackageManager
import io.droidmcp.overlay.OverlayController
import io.droidmcp.playback.NotificationListenerHolder
import io.droidmcp.root.RootTools
import io.droidmcp.shizuku.ShizukuTools
import rikka.shizuku.Shizuku
import io.droidmcp.sample.ui.MainScreen
import io.droidmcp.sample.ui.theme.DroidMcpTheme
import io.droidmcp.screenshot.MediaProjectionHolder

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.isNotEmpty()) initViewModel()
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
            MediaProjectionHolder.set(projection)
        }
    }

    private var viewModelRef: MainViewModel? = null

    /**
     * Shizuku grant feedback. Registered in onCreate, unregistered in
     * onDestroy. When the user grants permission for our request code, the
     * ViewModel re-initializes so the Tools page reflects the new state
     * without a manual restart.
     */
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            viewModelRef?.initialize()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        // Configure NotificationListenerService component for playback tools
        NotificationListenerHolder.set(
            ComponentName(this, McpNotificationListenerService::class.java)
        )

        setContent {
            DroidMcpTheme {
                val vm: MainViewModel = viewModel()
                viewModelRef = vm
                val state by vm.state.collectAsState()

                // Auto-initialize on first composition
                LaunchedEffect(Unit) {
                    vm.initialize()
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
                    MainScreen(
                        state = state,
                        onRequestPermissions = { requestAllPermissions() },
                        onStartServer = { vm.startServer() },
                        onStopServer = { vm.stopServer() },
                        onCallTool = { name, params -> vm.callTool(name, params) },
                        onClearLogs = { vm.clearLogs() },
                        onRequestSpecialPermission = { type -> requestSpecialPermission(type) },
                        onToggleReadOnly = { value -> vm.setReadOnly(value) },
                        onToggleTls = { value -> vm.setTls(value) },
                        onToggleTool = { name, enabled -> vm.setToolEnabled(name, enabled) },
                        onSetToolsEnabled = { names, enabled -> vm.setToolsEnabled(names, enabled) },
                        onClearAuditLog = { vm.clearAuditLog() },
                        onExportAuditLog = { vm.exportAuditLog { json -> copyAuditToClipboard(json) } },
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestSpecialPermission(type: String) {
        when (type) {
            "notification_listener" -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            "dnd_access" -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            "screen_capture" -> {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
            "write_settings" -> {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            }
            "accessibility" -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            "ime_settings" -> {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            "overlay" -> {
                startActivity(OverlayController(this).permissionIntent())
            }
            "shizuku" -> {
                if (ShizukuTools.isShizukuReady()) {
                    // Already granted — nothing to do.
                } else if (Shizuku.pingBinder()) {
                    // Installed + running, just not granted — request it.
                    ShizukuTools.requestPermission(SHIZUKU_REQUEST_CODE)
                } else {
                    // Not installed or not activated — open Shizuku app / Play.
                    startActivity(ShizukuTools.installOrOpenIntent(this))
                }
            }
            "root" -> {
                // Trigger the libsu su prompt asynchronously. On grant, re-run
                // the ViewModel initializer so Root-backed tools appear in the
                // registry (without an app restart). Callback runs on libsu's
                // worker thread — bounce to main for the ViewModel mutation.
                RootTools.requestAccess { granted ->
                    if (granted) runOnUiThread { viewModelRef?.initialize() }
                }
            }
        }
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 0xCAFE
    }

    private fun initViewModel() {
        viewModelRef?.initialize()
    }

    /** Export the audit JSON to the clipboard — the sample's "do something with it" sink. */
    private fun copyAuditToClipboard(json: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("droid-mcp audit", json))
        Toast.makeText(this, "Audit log copied (${json.length} chars)", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }
}
