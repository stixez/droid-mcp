package io.droidmcp.app.data.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.droidmcp.accessibility.AccessibilityTools
import io.droidmcp.bluetooth.BluetoothTools
import io.droidmcp.calendar.CalendarTools
import io.droidmcp.calllog.CallLogTools
import io.droidmcp.camera.CameraTools
import io.droidmcp.contacts.ContactsTools
import io.droidmcp.core.support.UsageAccess
import io.droidmcp.downloads.DownloadsTools
import io.droidmcp.files.FilesTools
import io.droidmcp.health.HealthTools
import io.droidmcp.location.LocationTools
import io.droidmcp.wifi.WifiTools
import io.droidmcp.media.MediaTools
import io.droidmcp.overlay.OverlayController
import io.droidmcp.playback.PlaybackTools
import io.droidmcp.root.RootTools
import io.droidmcp.screenshot.MediaProjectionHolder
import io.droidmcp.shizuku.ShizukuTools
import io.droidmcp.sms.SmsTools
import io.droidmcp.telephony.TelephonyTools

/** Every capability the app can ask for, in Setup order. */
object Capabilities {

    const val NOTIFICATIONS = "notifications"
    const val SCREEN_CONTROL = "screen_control"
    const val BACKGROUND = "background"
    const val CALENDAR = "calendar"
    const val CONTACTS = "contacts"
    const val SMS = "sms"
    const val CALL_LOG = "call_log"
    const val PHOTOS = "photos"
    const val FILES = "files"
    const val LOCATION = "location"
    const val WIFI = "wifi"
    const val CAMERA = "camera"
    const val PHONE = "phone"
    const val ACTIVITY = "activity"
    const val BLUETOOTH = "bluetooth"
    const val NOTIFICATION_ACCESS = "notification_access"
    const val OPEN_APPS = "open_apps"
    const val USAGE_ACCESS = "usage_access"
    const val DND = "dnd"
    const val MODIFY_SETTINGS = "modify_settings"
    const val SCREEN_CAPTURE = "screen_capture"
    const val KEYBOARD = "keyboard"
    const val SHIZUKU = "shizuku"
    const val ROOT = "root"

    /**
     * "Allow all the time" location, so trusted Wi-Fi can read the network name from the
     * background. Not in [all]: Setup never asks for it, only the paused-server card offers it.
     */
    val BACKGROUND_LOCATION = Capability(
        id = "background_location",
        title = "Location all the time",
        description = "Lets trusted Wi-Fi check the network name while the app is closed.",
        section = CapabilitySection.Advanced,
        grant = GrantMethod.Runtime(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else emptyList(),
        ),
        isGranted = { context ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        },
    )

    val all: List<Capability> = listOfNotNull(
        Capability(
            id = NOTIFICATIONS,
            title = "Notifications",
            description = "Shows when the server is on, and asks you before a client sends, deletes or changes something.",
            section = CapabilitySection.Essentials,
            grant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                GrantMethod.Runtime(listOf(Manifest.permission.POST_NOTIFICATIONS))
            } else {
                GrantMethod.SystemSettings(::appNotificationSettings)
            },
            isGranted = { NotificationManagerCompat.from(it).areNotificationsEnabled() },
        ),
        Capability(
            id = SCREEN_CONTROL,
            title = "Screen control",
            description = "Lets clients read what is on screen and tap, type and scroll for you.",
            section = CapabilitySection.Essentials,
            grant = GrantMethod.SystemSettings { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            isGranted = { AccessibilityTools.permissionStatus(it).granted },
        ),
        Capability(
            id = BACKGROUND,
            title = "Keep running in the background",
            description = "Stops battery saving from closing the server while the screen is off. Some phones do this a lot.",
            section = CapabilitySection.Essentials,
            grant = GrantMethod.SystemSettings { ctx ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${ctx.packageName}".toUri())
            },
            isGranted = { ctx ->
                ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            },
        ),

        // Read and write together: the module lists cover only the read tools, and the write tools
        // register only once the write permission is granted (same permission group, one dialog).
        runtime(
            CALENDAR,
            "Calendar",
            "Read your events and add, move or delete them.",
            (CalendarTools.requiredPermissions() + Manifest.permission.WRITE_CALENDAR).distinct(),
        ),
        runtime(
            CONTACTS,
            "Contacts",
            "Look up people and add or edit contacts.",
            (ContactsTools.requiredPermissions() + Manifest.permission.WRITE_CONTACTS).distinct(),
        ),
        runtime(SMS, "Messages", "Read and search texts, and send new ones.", SmsTools.requiredPermissions()),
        runtime(CALL_LOG, "Call history", "See recent and missed calls.", CallLogTools.requiredPermissions()),
        runtime(PHOTOS, "Photos and videos", "Find photos and videos and read their details.", MediaTools.requiredPermissions()),
        // Android 13+ needs no permission for files and downloads, so this row only exists before it.
        runtime(
            FILES,
            "Files and downloads",
            "Browse and read documents and downloads in shared storage.",
            (FilesTools.requiredPermissions() + DownloadsTools.requiredPermissions()).distinct(),
        ),
        runtime(LOCATION, "Location", "Tell clients where the phone is.", LocationTools.requiredPermissions())
            // Approximate location alone is enough for the location tools.
            ?.copy(isGranted = LocationTools::hasPermissions),
        // Android shows the Wi-Fi name only to apps with precise location (and Location switched on).
        runtime(
            WIFI,
            "Wi-Fi details",
            "Read the Wi-Fi name and saved networks. Android asks for precise location for this.",
            WifiTools.requiredPermissions(),
        ),
        runtime(CAMERA, "Camera", "Take photos and short videos on request.", CameraTools.requiredPermissions()),
        runtime(PHONE, "Phone and SIM", "Read the phone number, SIM and carrier details.", TelephonyTools.requiredPermissions()),
        runtime(ACTIVITY, "Physical activity", "Read step counts and whether you are walking or still.", HealthTools.requiredPermissions()),
        runtime(BLUETOOTH, "Nearby devices", "List paired Bluetooth devices.", BluetoothTools.requiredPermissions()),

        Capability(
            id = NOTIFICATION_ACCESS,
            title = "Notification access",
            description = "Read notifications from every app, reply to them, and control music.",
            section = CapabilitySection.PhoneControl,
            grant = GrantMethod.SystemSettings { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
            isGranted = { PlaybackTools.permissionStatus(it).granted },
        ),
        Capability(
            id = OPEN_APPS,
            title = "Open apps",
            description = "Display over other apps, so clients can open apps while droid-mcp is in the background.",
            section = CapabilitySection.PhoneControl,
            grant = GrantMethod.SystemSettings { OverlayController(it).permissionIntent() },
            isGranted = { Settings.canDrawOverlays(it) },
        ),
        Capability(
            id = USAGE_ACCESS,
            title = "Usage access",
            description = "Screen time per app and mobile data usage.",
            section = CapabilitySection.PhoneControl,
            grant = GrantMethod.SystemSettings { Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
            isGranted = { UsageAccess.isGranted(it) },
        ),
        Capability(
            id = DND,
            title = "Do Not Disturb",
            description = "Turn Do Not Disturb on and off.",
            section = CapabilitySection.PhoneControl,
            grant = GrantMethod.SystemSettings { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
            isGranted = { it.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true },
        ),
        Capability(
            id = MODIFY_SETTINGS,
            title = "Modify system settings",
            description = "Change brightness and the ringtone.",
            section = CapabilitySection.PhoneControl,
            grant = GrantMethod.SystemSettings { ctx ->
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${ctx.packageName}".toUri())
            },
            isGranted = { Settings.System.canWrite(it) },
        ),
        Capability(
            id = SCREEN_CAPTURE,
            title = "Screenshots",
            description = "Capture the screen as an image. Android asks again each time the app restarts.",
            section = CapabilitySection.PhoneControl,
            grant = GrantMethod.ScreenCapture,
            isGranted = { MediaProjectionHolder.projection != null },
            sessionOnly = true,
        ),

        Capability(
            id = KEYBOARD,
            title = "droid-mcp keyboard",
            description = "Type into any text field. Turn it on, then pick it as your keyboard when a client needs to type.",
            section = CapabilitySection.Advanced,
            grant = GrantMethod.SystemSettings { Intent(Settings.ACTION_INPUT_METHOD_SETTINGS) },
            isGranted = ::isOurKeyboardEnabled,
        ),
        Capability(
            id = SHIZUKU,
            title = "Shizuku",
            description = "Install apps, grant permissions and run allowlisted shell commands, without root.",
            section = CapabilitySection.Advanced,
            grant = GrantMethod.Shizuku,
            isGranted = { ShizukuTools.isShizukuReady() },
        ),
        Capability(
            id = ROOT,
            title = "Root",
            description = "The same shell tools as Shizuku, through Magisk or KernelSU.",
            section = CapabilitySection.Advanced,
            grant = GrantMethod.Root,
            isGranted = { RootTools.permissionStatus(it).granted },
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Capability = byId.getValue(id)

    /** Whether [id] is asked for on this device (some rows only exist on older Android). */
    fun exists(id: String): Boolean = id in byId

    /** A runtime-permission row, or null when this Android version needs no permission for it. */
    private fun runtime(id: String, title: String, description: String, permissions: List<String>) =
        if (permissions.isEmpty()) null else Capability(
        id = id,
        title = title,
        description = description,
        section = CapabilitySection.PersonalData,
        grant = GrantMethod.Runtime(permissions),
        isGranted = { ctx ->
            permissions.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        },
    )

    private fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun isOurKeyboardEnabled(context: Context): Boolean =
        context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.any { it.packageName == context.packageName } == true
}
