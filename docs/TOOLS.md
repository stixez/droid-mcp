# Tool Reference

Complete reference for all 145 tools. They live in 50 of droid-mcp's 53 modules — 48 user-callable plus 2 support (`notification-listener`, `shell-core`); the 3 opt-in hardening modules (`audit`, `tls`, `server-service`) expose no tools.

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
| `set_volume` | Set volume level | `stream` (media/ring/alarm/notification), `level` (required) |
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

## Notifications (Reply)

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_repliable_notifications` | Active notifications that expose a RemoteInput action (WhatsApp, Signal, Messenger, Slack, SMS, Gmail, etc.) | `limit` |
| `reply_to_notification` | Send a reply via the notification's RemoteInput PendingIntent | `key` (required), `text` (required) |
| `dismiss_notification` | Cancel a notification by key | `key` (required) |
| `invoke_notification_action` | Trigger a non-reply action (Mark as read, Snooze, Archive, etc.) on an active notification | `key` (required), exactly one of `action_label` or `action_index` |

Requires notification listener access (same as Playback). To enable the active-notification cache, `dismiss_notification`, and `invoke_notification_action`, the host app's listener service must extend `McpNotificationListenerServiceBase` from the `droid-mcp-notification-listener` module. `reply_to_notification` returns success when the underlying `PendingIntent.send` fires — it does NOT confirm the receiving app delivered the message.

## Notification Watch

| Tool | Description | Parameters |
|------|-------------|------------|
| `watch_notifications` | Register a filter against the live notification stream; returns `watch_id`. Filter semantics: case-insensitive substring on sender / keyword, AND-combine, fire-once-per-key with optional `fire_on_update`, no replay. | `package_name?`, `sender_pattern?`, `keyword?`, `ttl_seconds`, `fire_on_update` |
| `unwatch_notifications` | Remove a watch by id. Idempotent — unknown id returns success with `removed = false`. | `watch_id` (required) |
| `list_notification_watches` | List currently-active watches with TTL countdown. Expired watches are swept before the list is returned. | -- |

Push subscription complement to Notifications (Reply)'s pull/snapshot tools. Shares the same listener service. Hosts can subscribe directly to `NotificationListenerBus.events: SharedFlow<NotificationEvent>` from the `droid-mcp-notification-listener` module to react to notifications without using the LLM-tool surface at all.

`NotificationEvent` fields: `key`, `packageName`, `title`, `text`, `bigText`, `subText`, `tickerText`, `category` (e.g. `CATEGORY_MESSAGE`), `channelId`, `groupKey`, `isOngoing`, `isClearable`, `legacyPriority` (pre-O), `channelImportance` (post-O, dominant; `-1` if unresolvable), `postedAt` (system receive time), `when` (app-set timestamp), `hasReplyAction`, `actionLabels`.

## Accessibility

| Tool | Description | Parameters |
|------|-------------|------------|
| `query_screen` | Dump the active window's UI tree as a flat list of nodes. **Nodes are ranked** clickable > has-text > scrollable > rest; when truncated, the highest-ranked nodes are kept. | `max_nodes` (1-2000, default 500) |
| `find_node` | Search the UI tree by any combination of text, view-id, class, package | `text`, `view_id`, `class_name`, `package_name`, `limit` |
| `wait_for_text` | Block (with timeout) until a condition is met. Result shape: `{ status: "matched" \| "timeout", elapsed_ms, ... }` — timeout is NOT an error. | `condition` (`text` default, or `window_change`), `text` (required when condition=text), `timeout_ms`, `poll_ms` |
| `click_node` | Perform ACTION_CLICK on a node matching the selector | selector params, `index` |
| `long_click_node` | Perform ACTION_LONG_CLICK on a node | selector params, `index` |
| `set_node_text` | Replace an editable node's text via ACTION_SET_TEXT. Returns `node_not_editable` if target is read-only. | selector params, `text` (required) |
| `scroll_node` | Scroll a scrollable node forward or backward | selector params, `direction` |
| `gesture` | Dispatch a touch gesture path via dispatchGesture | `points` (required, array of [x, y]), `duration_ms` |
| `global_action` | System-wide action: back, home, recents, notifications, quick_settings, power_dialog, lock_screen, screenshot. `AccessibilityTools.idempotentGlobalActions` exposes the safe-to-retry subset. | `action` (required) |
| `get_active_window_info` | Foreground package + root class + window id | -- |
| `take_screenshot_via_a11y` | Capture the screen via AccessibilityService.takeScreenshot — no MediaProjection prompt | `format` (png/jpeg), `quality` |
| `tap` | Single-tap at screen coords via `dispatchGesture`. | `x` (required), `y` (required) |
| `long_press` | Long-press at screen coords for `duration_ms` (default 800). | `x` (required), `y` (required), `duration_ms` |
| `find_and_tap` | One-call `find_node` + `click_node`. | `match` (required), `match_kind` (`text` default, `desc`, `id`, `class`), `case_insensitive` |
| `scroll_to_find` | Swipe in `direction` until `match` appears or `max_scrolls` exhausts. **Reading-direction semantics:** `down` reveals content below (internally swipes up). | `match` (required), `direction` (`down` default, `up`, `left`, `right`), `max_scrolls` |

Requires an enabled AccessibilityService (Settings > Accessibility > Installed apps). The host app's service must extend `DroidMcpAccessibilityService` from the `droid-mcp-accessibility` module. `take_screenshot_via_a11y` requires Android 11 (API 30) or newer; call `AccessibilityTools.supportedTools(context)` to filter API-gated tools at runtime.

## IME

| Tool | Description | Parameters |
|------|-------------|------------|
| `is_ime_active` | Check whether the droid-mcp keyboard is the active IME and an editor is bound | -- |
| `type_text` | Commit text at the cursor via InputConnection.commitText | `text` (required) |
| `commit_keystroke` | Send a named key event: enter, backspace, del, tab, escape, up, down, left, right, home, end, page_up, page_down | `key` (required) |
| `delete_text` | Delete `before` and `after` characters around the cursor | `before`, `after` |
| `set_selection` | Move the cursor or select a range | `start` (required), `end` (required) |
| `get_text_around_cursor` | Read text before and after the cursor | `before`, `after` |
| `switch_to_previous_ime` | Switch back to the user's previous keyboard | -- |

Requires the droid-mcp keyboard enabled in Settings > System > Languages & input AND selected via the IME picker. The host app's service must extend `DroidMcpInputMethodService` from the `droid-mcp-ime` module. Pair with `accessibility.global_action` (action = `notifications` or via a vendor-specific keypath) to flip to the keyboard programmatically.

## Overlay

No LLM tools — programmatic API only. The `droid-mcp-overlay` module exposes `OverlayController` for floating-window primitives:

```kotlin
val overlay = OverlayController(context)
if (!overlay.isPermissionGranted()) {
    startActivity(overlay.permissionIntent())  // → ACTION_MANAGE_OVERLAY_PERMISSION
}
overlay.show(OverlayConfig(
    label = "Ask",
    onClick = { /* open chat */ },
    onLongPress = { /* quick voice */ },
    onDragEnd = { x, y -> /* persist position */ },
))
overlay.hide()
```

Requires `SYSTEM_ALERT_WINDOW` (Settings > Apps > Special access > Display over other apps). Uses `TYPE_APPLICATION_OVERLAY` on API 26+, falls back to `TYPE_PHONE` on older versions.

## Shizuku (Tier 4 — shell-UID admin)

`droid-mcp-shell-core` defines the tools below; `droid-mcp-shizuku` wires them against Shizuku's binder. The same surface is reused by `:droid-mcp-root` with a root-UID backend (see [Root](#root-tier-5--same-surface-broader-privilege) below) — host apps register one or the other, not both.

### Package manager

| Tool | Description | Parameters |
|------|-------------|------------|
| `install_apk` | Silent `pm install` from a local file path. | `path` (required), `replace` (default true → `-r`) |
| `uninstall_app` | Silent `pm uninstall <pkg>`. | `package_name` (required), `keep_data` (default false → `-k`) |
| `clear_app_data` | Wipe app data + cache via `pm clear`. | `package_name` (required) |
| `force_stop_app` | Terminate app processes via `am force-stop`. | `package_name` (required) |
| `disable_app` | Hide an app via `pm disable-user --user 0`. Reversible. | `package_name` (required) |
| `enable_app` | Re-enable a disabled app via `pm enable`. Idempotent. | `package_name` (required) |

### Permissions

| Tool | Description | Parameters |
|------|-------------|------------|
| `grant_permission` | `pm grant <pkg> <perm>` — bypass the runtime-permission dialog. Idempotent. | `package_name` (required), `permission` (required) |
| `revoke_permission` | `pm revoke <pkg> <perm>`. Idempotent. | `package_name` (required), `permission` (required) |
| `list_app_permissions` | Parse `dumpsys package <pkg>` for granted / requested permissions. | `package_name` (required) |

### Settings

| Tool | Description | Parameters |
|------|-------------|------------|
| `put_secure_setting` | `settings put secure <key> <value>`. Idempotent. | `key` (required), `value` (required) |
| `put_global_setting` | `settings put global <key> <value>`. Idempotent. | `key` (required), `value` (required) |
| `put_system_setting` | `settings put system <key> <value>`. Idempotent. | `key` (required), `value` (required) |

### Dumpsys / state

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_top_window` | Parse `dumpsys window` for foreground package + activity. Cheaper than `accessibility.get_active_window_info` when the accessibility service isn't enabled. | -- |

### Standby

| Tool | Description | Parameters |
|------|-------------|------------|
| `set_app_standby_bucket` | `am set-standby-bucket` — `active` / `working_set` / `frequent` / `rare` / `restricted`. Idempotent. | `package_name` (required), `bucket` (required) |
| `make_app_inactive` | `am set-inactive <pkg> true` for Doze testing. Idempotent. | `package_name` (required) |

### Screencap

| Tool | Description | Parameters |
|------|-------------|------------|
| `capture_screen_quiet` | `screencap -p` via the shell backend — no MediaProjection consent prompt, no status-bar indicator. Returns base64 PNG. | `display` (default 0) |

### Escape hatch

| Tool | Description | Parameters |
|------|-------------|------------|
| `run_shell` | Run an arbitrary command via the backend. **Default-deny**: the host must register allowed prefixes via `ShellAllowlist.set(...)` before this tool succeeds. Prefer the argv form (`args` array) for anything with whitespace/quotes; the string form whitespace-splits naively. Stdout truncated at `max_stdout_bytes` (1024-65536, default 8192). | `command` (required), `args` (array, optional), `max_stdout_bytes` |

Requires Shizuku activated + the host app granted permission. See [docs/SHIZUKU.md](../docs/SHIZUKU.md). Deep `dumpsys` tools (`batterystats`, `procstats`, full notification dumpsys) are deferred to a future release because their raw output overwhelms an LLM's token budget without bespoke parsing.

## Root (Tier 5 — same surface, broader privilege)

`droid-mcp-root` exposes the **same 17 tools** as `shizuku` with identical names, parameters, and output shapes. Difference: backed by libsu's `su` shell instead of Shizuku's binder proxy. Strictly more powerful — capable of `/system` writes, freezing apps via `pm hide`, reading `/data/data/<pkg>`, etc.

Host apps register either `ShizukuTools.all(context)` OR `RootTools.all(context)` — not both (the second `addTools` call overwrites the first since the tool names collide). Hosts that want "root preferred, Shizuku fallback" can detect availability and pick the backend at startup; see [docs/ROOT.md](../docs/ROOT.md) for the dispatch pattern.

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

## ML Kit

| Tool | Description | Parameters |
|------|-------------|------------|
| `recognize_text` | Extract text from an image using ML Kit text recognition | `image_path` (required) |
| `label_image` | Classify the contents of an image | `image_path` (required), `min_confidence` (0.0-1.0, default 0.5) |
| `detect_faces` | Detect faces (bounding boxes, expression probabilities). Does NOT identify. | `image_path` (required) |

All ML Kit tools operate on local image files under external storage (validated via the same sandboxing as the file tools). All inference runs fully on-device — no network calls. Returns include bounding boxes for text lines, confidence scores for labels, and face attribute probabilities (smiling, eyes open) plus Euler angles.
