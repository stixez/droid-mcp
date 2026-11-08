package io.droidmcp.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import io.droidmcp.app.data.permissions.Capabilities

/** Icons for capabilities and tool groups, kept out of the data layer. */
object AppIcons {

    fun capability(id: String): ImageVector = when (id) {
        Capabilities.NOTIFICATIONS -> Icons.Outlined.Notifications
        Capabilities.SCREEN_CONTROL -> Icons.Outlined.TouchApp
        Capabilities.BACKGROUND -> Icons.Outlined.BatteryChargingFull
        Capabilities.CALENDAR -> Icons.Outlined.CalendarMonth
        Capabilities.CONTACTS -> Icons.Outlined.Contacts
        Capabilities.SMS -> Icons.Outlined.Sms
        Capabilities.CALL_LOG -> Icons.Outlined.Call
        Capabilities.PHOTOS -> Icons.Outlined.PhotoLibrary
        Capabilities.FILES -> Icons.Outlined.Folder
        Capabilities.LOCATION -> Icons.Outlined.LocationOn
        Capabilities.WIFI -> Icons.Outlined.Wifi
        Capabilities.CAMERA -> Icons.Outlined.PhotoCamera
        Capabilities.PHONE -> Icons.Outlined.SimCard
        Capabilities.ACTIVITY -> Icons.AutoMirrored.Outlined.DirectionsWalk
        Capabilities.BLUETOOTH -> Icons.Outlined.Bluetooth
        Capabilities.NOTIFICATION_ACCESS -> Icons.Outlined.NotificationsActive
        Capabilities.OPEN_APPS -> Icons.Outlined.Layers
        Capabilities.USAGE_ACCESS -> Icons.Outlined.QueryStats
        Capabilities.DND -> Icons.Outlined.DoNotDisturbOn
        Capabilities.MODIFY_SETTINGS -> Icons.Outlined.Tune
        Capabilities.SCREEN_CAPTURE -> Icons.Outlined.Screenshot
        Capabilities.KEYBOARD -> Icons.Outlined.Keyboard
        Capabilities.SHIZUKU -> Icons.Outlined.Terminal
        Capabilities.ROOT -> Icons.Outlined.AdminPanelSettings
        else -> Icons.Outlined.Widgets
    }

    fun toolGroup(id: String): ImageVector = when (id) {
        "calendar" -> Icons.Outlined.CalendarMonth
        "contacts" -> Icons.Outlined.Contacts
        "sms" -> Icons.Outlined.Sms
        "calllog" -> Icons.Outlined.Call
        "media" -> Icons.Outlined.PhotoLibrary
        "files" -> Icons.Outlined.Folder
        "downloads" -> Icons.Outlined.Download
        "location" -> Icons.Outlined.LocationOn
        "health" -> Icons.AutoMirrored.Outlined.DirectionsWalk
        "device" -> Icons.Outlined.PhoneAndroid
        "screen" -> Icons.Outlined.BrightnessMedium
        "settings" -> Icons.Outlined.Settings
        "network" -> Icons.Outlined.SignalCellularAlt
        "wifi" -> Icons.Outlined.Wifi
        "bluetooth" -> Icons.Outlined.Bluetooth
        "telephony" -> Icons.Outlined.SimCard
        "audio" -> Icons.Outlined.Headphones
        "sensors" -> Icons.Outlined.Sensors
        "biometric" -> Icons.Outlined.Fingerprint
        "keyguard" -> Icons.Outlined.Lock
        "flashlight" -> Icons.Outlined.FlashlightOn
        "vibration" -> Icons.Outlined.Vibration
        "dnd" -> Icons.Outlined.DoNotDisturbOn
        "wallpaper" -> Icons.Outlined.Wallpaper
        "ringtone" -> Icons.Outlined.NotificationsNone
        "usb" -> Icons.Outlined.Usb
        "nfc" -> Icons.Outlined.Nfc
        "apps" -> Icons.Outlined.Apps
        "intent" -> Icons.Outlined.Link
        "alarms" -> Icons.Outlined.Alarm
        "clipboard" -> Icons.Outlined.ContentPaste
        "accessibility" -> Icons.Outlined.TouchApp
        "ime" -> Icons.Outlined.Keyboard
        "screenshot" -> Icons.Outlined.Screenshot
        "print" -> Icons.Outlined.Print
        "guide" -> Icons.Outlined.AdsClick
        "notifications" -> Icons.Outlined.Notifications
        "notifications-reply" -> Icons.Outlined.NotificationsActive
        "notification-watch" -> Icons.Outlined.Visibility
        "playback" -> Icons.Outlined.MusicNote
        "camera" -> Icons.Outlined.PhotoCamera
        "qr" -> Icons.Outlined.QrCodeScanner
        "mlkit" -> Icons.Outlined.DocumentScanner
        "web" -> Icons.Outlined.Language
        "tts" -> Icons.AutoMirrored.Outlined.VolumeUp
        "shell" -> Icons.Outlined.Terminal
        else -> Icons.Outlined.Widgets
    }
}
