package io.droidmcp.app.data.tools

import android.app.Activity
import android.content.Context
import io.droidmcp.accessibility.AccessibilityTools
import io.droidmcp.alarms.AlarmsTools
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.settings.AppSettings
import io.droidmcp.apps.AppsTools
import io.droidmcp.audio.AudioTools
import io.droidmcp.biometric.BiometricTools
import io.droidmcp.bluetooth.BluetoothTools
import io.droidmcp.calendar.CalendarTools
import io.droidmcp.calllog.CallLogTools
import io.droidmcp.camera.CameraTools
import io.droidmcp.clipboard.ClipboardTools
import io.droidmcp.contacts.ContactsTools
import io.droidmcp.core.McpTool
import io.droidmcp.device.DeviceTools
import io.droidmcp.dnd.DndTools
import io.droidmcp.downloads.DownloadsTools
import io.droidmcp.files.FilesTools
import io.droidmcp.flashlight.FlashlightTools
import io.droidmcp.health.HealthTools
import io.droidmcp.ime.ImeTools
import io.droidmcp.intent.IntentTools
import io.droidmcp.keyguard.KeyguardTools
import io.droidmcp.location.LocationTools
import io.droidmcp.media.MediaTools
import io.droidmcp.mlkit.MlKitTools
import io.droidmcp.network.NetworkTools
import io.droidmcp.nfc.NfcTools
import io.droidmcp.notifications.NotificationsTools
import io.droidmcp.notificationsreply.NotificationsReplyTools
import io.droidmcp.notificationwatch.NotificationWatchTools
import io.droidmcp.overlay.OverlayTools
import io.droidmcp.playback.PlaybackTools
import io.droidmcp.print.PrintTools
import io.droidmcp.qr.QrTools
import io.droidmcp.ringtone.RingtoneTools
import io.droidmcp.root.RootTools
import io.droidmcp.screen.ScreenTools
import io.droidmcp.screenshot.ScreenshotTools
import io.droidmcp.sensors.SensorTools
import io.droidmcp.settings.SettingsTools
import io.droidmcp.shell.ShellPolicy
import io.droidmcp.shizuku.ShizukuTools
import io.droidmcp.sms.SmsTools
import io.droidmcp.telephony.TelephonyTools
import io.droidmcp.tts.TtsTools
import io.droidmcp.usb.UsbTools
import io.droidmcp.vibration.VibrationTools
import io.droidmcp.wallpaper.WallpaperTools
import io.droidmcp.web.WebTools
import io.droidmcp.wifi.WifiTools

/** Sections of the Tools screen. */
enum class ToolSection(val title: String) {
    PersonalData("Your data"),
    Phone("Phone and system"),
    Control("Apps and screen"),
    Notifications("Notifications and media"),
    Vision("Camera, vision and web"),
    Advanced("Advanced"),
}

/** Inputs a group needs to build its tools. */
class BuildInputs(
    val context: Context,
    val settings: AppSettings,
    val currentActivity: () -> Activity?,
)

/**
 * One droid-mcp module as the user sees it: a named group of tools, gated by at most one
 * [capability][Capabilities] (plus the module's own install-time check).
 */
class ToolGroup(
    val id: String,
    val title: String,
    val section: ToolSection,
    /** Capability the user must grant before these tools are offered; null = always offered. */
    val capabilityId: String? = null,
    /** Alternative capabilities: any one of them is enough (Shizuku or root). */
    val anyOfCapabilities: List<String> = emptyList(),
    /** The module's own install-time permission check (normal permissions). */
    val moduleCheck: (Context) -> Boolean = { true },
    val build: (BuildInputs) -> List<McpTool>,
) {
    fun isAvailable(context: Context, granted: Set<String>): Boolean {
        if (capabilityId != null && Capabilities.exists(capabilityId) && capabilityId !in granted) return false
        if (anyOfCapabilities.isNotEmpty() && anyOfCapabilities.none { it in granted }) return false
        return moduleCheck(context)
    }

    /** The capability to send the user to when this group isn't available. */
    val requiredCapability: String? get() = capabilityId?.takeIf(Capabilities::exists) ?: anyOfCapabilities.firstOrNull()
}

/** A group resolved against the current permissions and settings. */
data class ResolvedGroup(
    val group: ToolGroup,
    val available: Boolean,
    val tools: List<McpTool>,
)

/** Every tool module the app ships, grouped for people. */
class ToolCatalog(private val context: Context, private val currentActivity: () -> Activity?) {

    val groups: List<ToolGroup> = listOf(
        // Your data
        ToolGroup("calendar", "Calendar", ToolSection.PersonalData, Capabilities.CALENDAR) { CalendarTools.all(it.context) },
        ToolGroup("contacts", "Contacts", ToolSection.PersonalData, Capabilities.CONTACTS) { ContactsTools.all(it.context) },
        ToolGroup("sms", "Messages", ToolSection.PersonalData, Capabilities.SMS) { SmsTools.all(it.context) },
        ToolGroup("calllog", "Call history", ToolSection.PersonalData, Capabilities.CALL_LOG) { CallLogTools.all(it.context) },
        ToolGroup("media", "Photos and videos", ToolSection.PersonalData, Capabilities.PHOTOS, moduleCheck = MediaTools::hasPermissions) {
            MediaTools.all(it.context)
        },
        ToolGroup("files", "Files", ToolSection.PersonalData, Capabilities.FILES, moduleCheck = FilesTools::hasPermissions) {
            FilesTools.all(it.context, includeWriteTools = it.settings.allowFileWrites)
        },
        ToolGroup("downloads", "Downloads", ToolSection.PersonalData, Capabilities.FILES, moduleCheck = DownloadsTools::hasPermissions) {
            DownloadsTools.all(it.context)
        },
        ToolGroup("location", "Location", ToolSection.PersonalData, Capabilities.LOCATION) { LocationTools.all(it.context) },
        ToolGroup("health", "Activity", ToolSection.PersonalData, Capabilities.ACTIVITY) { HealthTools.all(it.context) },

        // Phone and system
        ToolGroup("device", "Device", ToolSection.Phone, moduleCheck = DeviceTools::hasPermissions) { DeviceTools.all(it.context) },
        ToolGroup("screen", "Display", ToolSection.Phone) { ScreenTools.all(it.context) },
        ToolGroup("settings", "Settings", ToolSection.Phone) { SettingsTools.all(it.context) },
        ToolGroup("network", "Network", ToolSection.Phone) { NetworkTools.all(it.context) },
        ToolGroup("wifi", "Wi-Fi", ToolSection.Phone, Capabilities.WIFI) { WifiTools.all(it.context) },
        ToolGroup("bluetooth", "Bluetooth", ToolSection.Phone, Capabilities.BLUETOOTH) { BluetoothTools.all(it.context) },
        ToolGroup("telephony", "Phone and SIM", ToolSection.Phone, Capabilities.PHONE) { TelephonyTools.all(it.context) },
        ToolGroup("audio", "Audio devices", ToolSection.Phone) { AudioTools.all(it.context) },
        ToolGroup("sensors", "Sensors", ToolSection.Phone) { SensorTools.all(it.context) },
        ToolGroup("biometric", "Biometrics", ToolSection.Phone) { BiometricTools.all(it.context) },
        ToolGroup("keyguard", "Lock screen", ToolSection.Phone) { KeyguardTools.all(it.context) },
        ToolGroup("flashlight", "Flashlight", ToolSection.Phone) { FlashlightTools.all(it.context) },
        ToolGroup("vibration", "Vibration", ToolSection.Phone, moduleCheck = VibrationTools::hasPermissions) { VibrationTools.all(it.context) },
        ToolGroup("dnd", "Do Not Disturb", ToolSection.Phone, Capabilities.DND) { DndTools.all(it.context) },
        ToolGroup("wallpaper", "Wallpaper", ToolSection.Phone, moduleCheck = WallpaperTools::hasPermissions) { WallpaperTools.all(it.context) },
        ToolGroup("ringtone", "Ringtones", ToolSection.Phone) { RingtoneTools.all(it.context) },
        ToolGroup("usb", "USB", ToolSection.Phone) { UsbTools.all(it.context) },
        ToolGroup("nfc", "NFC", ToolSection.Phone, moduleCheck = NfcTools::hasPermissions) { NfcTools.all(it.context) },

        // Apps and screen
        ToolGroup("apps", "Apps", ToolSection.Control) { AppsTools.all(it.context) },
        ToolGroup("intent", "Links and sharing", ToolSection.Control) { IntentTools.all(it.context) },
        ToolGroup("alarms", "Alarms and timers", ToolSection.Control, moduleCheck = AlarmsTools::hasPermissions) { AlarmsTools.all(it.context) },
        ToolGroup("clipboard", "Clipboard", ToolSection.Control) { ClipboardTools.all(it.context) },
        ToolGroup("accessibility", "Screen control", ToolSection.Control, Capabilities.SCREEN_CONTROL) { AccessibilityTools.all(it.context) },
        ToolGroup("ime", "Keyboard", ToolSection.Control, Capabilities.KEYBOARD) { ImeTools.all(it.context) },
        ToolGroup("screenshot", "Screenshots", ToolSection.Control, Capabilities.SCREEN_CAPTURE) { ScreenshotTools.all(it.context) },
        ToolGroup("print", "Printing", ToolSection.Control) { PrintTools.all(it.context) { currentActivity() } },
        // Draws on top of other apps, so it needs the same permission as opening apps.
        ToolGroup("guide", "Point and wait", ToolSection.Control, Capabilities.OPEN_APPS) { OverlayTools.all(it.context) },

        // Notifications and media
        ToolGroup("notifications", "Own notifications", ToolSection.Notifications) { NotificationsTools.all(it.context) },
        ToolGroup("notifications-reply", "All notifications", ToolSection.Notifications, Capabilities.NOTIFICATION_ACCESS) {
            NotificationsReplyTools.all(it.context)
        },
        ToolGroup("notification-watch", "Notification watching", ToolSection.Notifications, Capabilities.NOTIFICATION_ACCESS) {
            NotificationWatchTools.all(it.context)
        },
        ToolGroup("playback", "Media playback", ToolSection.Notifications, Capabilities.NOTIFICATION_ACCESS) { PlaybackTools.all(it.context) },

        // Camera, vision and web
        ToolGroup("camera", "Camera", ToolSection.Vision, Capabilities.CAMERA) { CameraTools.all(it.context) },
        ToolGroup("qr", "QR and barcodes", ToolSection.Vision) { QrTools.all(it.context) },
        ToolGroup("mlkit", "Text and image recognition", ToolSection.Vision) { MlKitTools.all(it.context) },
        ToolGroup("web", "Web", ToolSection.Vision) { WebTools.all(it.context) },
        ToolGroup("tts", "Speech", ToolSection.Vision) { TtsTools.all(it.context) },

        // Advanced: Shizuku and root expose the same tool names, so exactly one set is built.
        ToolGroup(
            "shell", "Shell (Shizuku or root)", ToolSection.Advanced,
            anyOfCapabilities = listOf(Capabilities.SHIZUKU, Capabilities.ROOT),
        ) { inputs ->
            val policy = if (inputs.settings.strictShellPolicy) ShellPolicy.RECOMMENDED else ShellPolicy.PERMISSIVE
            if (RootTools.isRootAvailable()) RootTools.all(inputs.context, policy) else ShizukuTools.all(inputs.context, policy)
        },
    )

    /** Every group with its tools and whether it's offered right now. */
    fun resolve(settings: AppSettings, granted: Set<String>): List<ResolvedGroup> {
        val inputs = BuildInputs(context, settings, currentActivity)
        return groups.map { group ->
            ResolvedGroup(
                group = group,
                available = group.isAvailable(context, granted),
                tools = group.build(inputs),
            )
        }
    }

    /** How many tools each capability unlocks, for the Setup screen. */
    fun toolCountsByCapability(settings: AppSettings): Map<String, Int> {
        val inputs = BuildInputs(context, settings, currentActivity)
        val counts = mutableMapOf<String, Int>()
        groups.forEach { group ->
            val ids = listOfNotNull(group.capabilityId?.takeIf(Capabilities::exists)) + group.anyOfCapabilities
            if (ids.isEmpty()) return@forEach
            val size = group.build(inputs).size
            ids.forEach { id -> counts[id] = (counts[id] ?: 0) + size }
        }
        return counts
    }

    /** The tools to register on the server: every available group's tools. */
    fun availableTools(settings: AppSettings, granted: Set<String>): List<McpTool> {
        val inputs = BuildInputs(context, settings, currentActivity)
        return groups.filter { it.isAvailable(context, granted) }.flatMap { it.build(inputs) }
    }
}
