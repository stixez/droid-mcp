package io.droidmcp.settings

import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetSettingsTool(private val context: Context) : McpTool {

    override val name = "get_settings"
    override val description = "Read current device settings including brightness, volume, WiFi, Bluetooth, airplane mode, and auto-rotate"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val contentResolver = context.contentResolver

        val brightness = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) { -1 }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volumeRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val volumeMedia = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiEnabled = wifiManager.isWifiEnabled

        val bluetoothEnabled = try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.isEnabled ?: false
        } catch (e: Exception) { false }

        val airplaneMode = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

        val autoRotate = try {
            Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION) != 0
        } catch (e: Exception) { false }

        return ToolResult.success(mapOf(
            "screen_brightness" to brightness,
            "volume_ring" to volumeRing,
            "volume_media" to volumeMedia,
            "volume_alarm" to volumeAlarm,
            "wifi_enabled" to wifiEnabled,
            "bluetooth_enabled" to bluetoothEnabled,
            "airplane_mode" to airplaneMode,
            "auto_rotate" to autoRotate,
        ))
    }
}
