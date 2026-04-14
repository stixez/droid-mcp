# Tool Reference

Complete reference for all 91 tools across 40 modules.

---

## Device

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_device_info` | Model, manufacturer, OS version, screen dimensions | -- |
| `get_battery_info` | Battery level, charging status, power source | -- |
| `get_connectivity` | WiFi, cellular, Bluetooth connection status | -- |
| `get_storage_info` | Total, available, and used storage | -- |

## Calendar

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_calendar` | Read events for a date range | `start_date` (required), `end_date`, `limit` |
| `create_event` | Create a calendar event | `title` (required), `start` (required), `end` (required), `location`, `description`, `calendar_id` |
| `search_events` | Search events by keyword | `query` (required), `limit` |

## Contacts

| Tool | Description | Parameters |
|------|-------------|------------|
| `search_contacts` | Find contacts by name | `query` (required), `limit` |
| `read_contact` | Full details for a contact | `contact_id` (required) |
| `list_contacts` | Paginated contact list | `limit`, `offset` |

## SMS

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_messages` | Read SMS with filters | `box`, `address`, `since`, `limit` |
| `send_message` | Send an SMS (validated) | `to` (required), `body` (required) |
| `search_messages` | Search messages by keyword | `query` (required), `limit` |

## Files

| Tool | Description | Parameters |
|------|-------------|------------|
| `browse_files` | List directory contents | `path`, `limit` |
| `read_file` | Read text file content | `path` (required), `max_lines` |
| `search_files` | Recursive filename search | `query` (required), `path`, `limit` |

File access is sandboxed to external storage directories. Paths outside the allowed roots are rejected.

## Notifications

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_active_notifications` | Read active status bar notifications | `limit` |

Returns notifications posted by the host app by default. Full cross-app access requires configuring a `NotificationListenerService`.

## Call Log

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_call_log` | Recent call history | `limit`, `type` (all/incoming/outgoing/missed) |
| `search_call_log` | Search by number or name | `query` (required), `limit` |

## Media

| Tool | Description | Parameters |
|------|-------------|------------|
| `search_media` | Search photos and videos | `query`, `start_date`, `end_date`, `media_type`, `limit`, `offset` |
| `get_media_metadata` | Metadata for a media file | `media_id` (required) |
| `list_albums` | Photo/video albums with counts | `limit` |

## Location

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_current_location` | Device GPS coordinates | `accuracy` (fine/coarse) |
| `get_location_address` | Reverse geocode to address | `latitude` (required), `longitude` (required) |

## Health

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_step_count` | Steps since device reboot | -- |
| `get_activity_info` | Available motion sensors | -- |

Step data is sensor-based (not Health Connect). Values reset on device reboot.

## Clipboard

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_clipboard` | Read current clipboard content | -- |
| `write_clipboard` | Write text to clipboard | `text` (required), `label` |

## Apps

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_installed_apps` | List installed applications | `include_system`, `limit` |
| `get_app_info` | Details for a specific app | `package_name` (required) |
| `launch_app` | Launch an application | `package_name` (required) |

## Alarms

| Tool | Description | Parameters |
|------|-------------|------------|
| `create_alarm` | Set an alarm | `hour` (required), `minute` (required), `message`, `days` |
| `create_timer` | Start a countdown timer | `seconds` (required), `message` |
| `create_reminder` | Create a calendar reminder | `title` (required), `datetime` (required), `minutes_before` |

## Settings

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_settings` | Read brightness, volume, WiFi, Bluetooth status | -- |
| `set_brightness` | Set screen brightness | `level` (required, 0-255) |
| `set_volume` | Set volume level | `stream` (media/ring/alarm), `level` (required) |
| `toggle_wifi` | Toggle WiFi on/off | `enabled` (required) |

On API 29+, `toggle_wifi` opens the system WiFi settings panel instead of toggling directly.

## Bluetooth

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_bluetooth_status` | Adapter status and name | -- |
| `list_paired_devices` | Bonded Bluetooth devices | -- |

## WiFi

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_wifi_info` | Current WiFi connection details | -- |
| `list_saved_networks` | Saved WiFi networks | -- |

SSID access requires location permission on API 26+. `list_saved_networks` returns empty on API 29+ due to platform restrictions.

## Downloads

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_downloads` | Files in Downloads folder | `limit`, `sort_by` (date/name/size) |
| `search_downloads` | Search downloads by filename | `query` (required), `limit` |

## Screen

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_screen_state` | Screen on/off, rotation, locked status | -- |
| `get_display_info` | Resolution, density, refresh rate | -- |

## Text-to-Speech

| Tool | Description | Parameters |
|------|-------------|------------|
| `speak_text` | Speak text aloud | `text` (required), `language`, `pitch`, `speed` |
| `get_tts_info` | Available TTS engines and languages | -- |

## Web

| Tool | Description | Parameters |
|------|-------------|------------|
| `web_search` | Search the web via DuckDuckGo | `query` (required), `limit` |
| `fetch_webpage` | Fetch and extract text from a URL | `url` (required), `max_length` |

Web tools require internet connectivity. Search results include title, URL, and snippet. Webpage fetching strips HTML and returns clean text content.

## Flashlight

| Tool | Description | Parameters |
|------|-------------|------------|
| `toggle_flashlight` | Toggle flashlight on/off | `enabled` (required) |
| `set_flashlight_brightness` | Set flashlight brightness level (Android 13+) | `level` (required, 0-255) |

Flashlight tools require a device with camera flash hardware. `set_flashlight_brightness` is only available on Android 13+ (API 33+) and uses `turnOnTorchWithStrengthLevel()`.

## Network

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_data_usage` | Mobile data usage statistics | `days` (1-90, default 30) |
| `get_cellular_signal` | Cellular signal strength (ASU, dBm, level) | -- |
| `is_vpn_active` | Check if VPN is active and get package name | -- |

`get_data_usage` returns bytes sent/received, packets, and query period. Falls back to cumulative TrafficStats if PACKAGE_USAGE_STATS is not granted. `get_cellular_signal` works on API 28+ using TelephonyManager.signalStrength. `is_vpn_active` detects VPN connections and attempts to identify the VPN app package name.

## Telephony

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_phone_number` | Get the device phone number | -- |
| `get_sim_info` | SIM serial, carrier, country, slot index | -- |
| `get_network_operator` | Network operator name, ID, MCC, MNC | -- |
| `get_call_state` | Current call state (idle/ringing/active) | -- |

Telephony tools use `TelephonyManager` and require `READ_PHONE_STATE`. Phone number availability varies by carrier and device.

## Vibration

| Tool | Description | Parameters |
|------|-------------|------------|
| `vibrate` | Trigger device vibration | `duration_ms` (1-10000), `amplitude` (0-255) |
| `vibrate_pattern` | Vibrate with a pattern | `timings` (list), `repeat` (-1 for none) |

Requires `VIBRATE` permission. Amplitude control available on API 26+.

## Biometric

| Tool | Description | Parameters |
|------|-------------|------------|
| `check_biometric_availability` | Check biometric hardware and capability | -- |
| `get_biometric_enrollments` | Check enrolled biometrics | -- |

Read-only queries using `BiometricManager`. No permissions required.

## Sensors

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_accelerometer` | Read accelerometer (x, y, z in m/s2) | `duration_ms` (1-5000) |
| `get_gyroscope` | Read gyroscope (x, y, z in rad/s) | `duration_ms` (1-5000) |
| `get_light_level` | Ambient light level in lux | `duration_ms` (1-5000) |
| `get_proximity` | Proximity distance (near/far) | `duration_ms` (1-5000) |

No permissions required. Single reading by default; pass `duration_ms` for batch collection. Returns error if sensor hardware is not present.

## QR / Barcode

| Tool | Description | Parameters |
|------|-------------|------------|
| `scan_qr_code` | Scan QR code from image URI | `image_uri` |
| `scan_barcode` | Scan barcode from image URI | `image_uri` |
| `generate_qr_code` | Generate QR code as base64 PNG | `text`, `size` (100-1000) |

Scanning uses ML Kit Barcode Scanning. Supports EAN-13, UPC-A, CODE-128, CODE-39, EAN-8, UPC-E. QR generation uses ZXing.

## Camera

| Tool | Description | Parameters |
|------|-------------|------------|
| `take_photo` | Capture photo via Camera2 API | `return_data` (base64) |
| `capture_video` | Record video | `duration_sec` (1-60) |
| `get_camera_capabilities` | List cameras and capabilities | -- |

Camera tools use Camera2 API for headless capture. Photos saved to Pictures/droid-mcp, videos to Movies/droid-mcp.

## Audio

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_audio_devices` | List connected audio devices | -- |

Returns device type, name, ID, and whether it is an output device. Useful for debugging audio routing.

## NFC

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_nfc_status` | Check if NFC is available and enabled | -- |
| `read_nfc_tag` | Read NDEF data from the last scanned NFC tag | -- |
| `write_nfc_tag` | Write an NDEF record to the scanned tag | `type` (required, "text" or "uri"), `content` (required) |

NFC tools use a tag cache — the host app must forward discovered tags via `NfcTagCache.update(tag)` from its `onNewIntent()`. Read/write operations work on the most recently scanned tag. Writing validates tag capacity and writability before attempting.

## Intent / Share

| Tool | Description | Parameters |
|------|-------------|------------|
| `send_intent` | Fire a safe Android intent | `action` (required), `data`, `type`, `package_name`, `extras` |
| `share_content` | Share text via the Android share sheet | `text` (required), `subject`, `type` |
| `open_deep_link` | Open a URI via ACTION_VIEW | `uri` (required), `package_name` |

`send_intent` restricts actions to a safe allowlist: VIEW, DIAL, SEND, SENDTO, CHOOSER, SEARCH, WEB_SEARCH, EDIT, PICK, GET_CONTENT, CREATE_DOCUMENT, OPEN_DOCUMENT. Dangerous actions (CALL, DELETE, FACTORY_RESET, etc.) are blocked.

## Playback

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_now_playing` | Get currently playing media info | -- |
| `media_control` | Send playback commands | `command` (required: play/pause/stop/next/previous), `package_name` |

Requires notification listener access (Settings > Special access > Notification access). The host app must register a `NotificationListenerService` and call `NotificationListenerHolder.set(componentName)` before using these tools.

## Screenshot

| Tool | Description | Parameters |
|------|-------------|------------|
| `capture_screen` | Capture a screenshot of the current screen | `format` (png/jpeg), `quality` (1-100, jpeg only) |

Requires MediaProjection consent. The host app must obtain a projection token via `MediaProjectionManager.createScreenCaptureIntent()` and pass it to `MediaProjectionHolder.set(projection)`. Screenshots are saved to Pictures/droid-mcp.

## Do Not Disturb

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_dnd_status` | Get current DND status and interruption filter | -- |
| `set_dnd_mode` | Set DND mode | `mode` (required: off/priority/alarms/none) |

`get_dnd_status` works without special permissions. `set_dnd_mode` requires notification policy access (Settings > Special access > Do Not Disturb access) — returns a clear error message if not granted.

## Keyguard

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_lock_state` | Check if device is locked and screen state | -- |
| `get_keyguard_info` | Get security details (PIN/pattern/password configured) | -- |

Read-only queries using `KeyguardManager` and `PowerManager`. No permissions required.

## Wallpaper

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_wallpaper_info` | Get current wallpaper dimensions and live wallpaper status | -- |
| `set_wallpaper` | Set wallpaper from an image file | `path` (required), `target` (home/lock/both) |

`set_wallpaper` validates that the file path is within external storage (same sandboxing as file tools). Checks `isSetWallpaperAllowed` before attempting.

## Ringtone

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_ringtones` | List available ringtones by type | `type` (ringtone/notification/alarm), `limit` (1-100) |
| `get_active_ringtone` | Get the currently set ringtone | `type` (ringtone/notification/alarm) |
| `set_ringtone` | Set the default ringtone | `uri` (required, content:// URI or "silent"), `type` (ringtone/notification/alarm) |

Read operations work without special permissions. `set_ringtone` requires WRITE_SETTINGS (Settings > Special access > Modify system settings). Only `content://` URIs are accepted for safety.

## USB

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_usb_devices` | List all connected USB devices | -- |
| `get_usb_device_info` | Get detailed info for a USB device | `device_name` (required) |

Returns vendor/product IDs, manufacturer, product name, serial number, and per-interface endpoint details (direction, transfer type, max packet size). Requires USB host hardware support.

## Print

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_printers` | List installed print service plugins and active jobs | -- |
| `print_content` | Send content to the system print dialog | `content` (required), `job_name`, `is_html` |

Plain text content is automatically wrapped in HTML. The print dialog opens asynchronously — the tool returns success when the dialog is triggered.
