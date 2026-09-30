# Tool Reference

Reference for all 147 tools. They are defined in 45 modules. The 17 tools in `shell-core` are exposed through either `shizuku` or `root`. `core`, `notification-listener`, `overlay`, `audit`, `tls` and `server-service` expose no LLM tools.

Parameters are optional unless marked (required).

On Android 10+, tools that start an activity return an error when the host is in the background and lacks the overlay permission (`SYSTEM_ALERT_WINDOW`). These tools are `launch_app`, `create_alarm`, `create_timer`, `toggle_wifi`, `set_brightness` (when it opens the grant screen), `send_intent`, `share_content` and `open_deep_link`.

---

## Device

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_device_info` | Model, manufacturer, OS/SDK version, screen size | -- |
| `get_battery_info` | Battery level, charging status, charging source | -- |
| `get_connectivity` | Whether the active network is up and uses WiFi, cellular or Bluetooth transport | -- |
| `get_storage_info` | Total, available and used storage in bytes | -- |

`get_connectivity` needs `ACCESS_NETWORK_STATE`.

## Calendar

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_calendar` | Events in a date range, including recurring occurrences and multi-day events | `start_date` (required, YYYY-MM-DD), `end_date` (default `start_date`), `limit` (1-100, default 10) |
| `create_event` | Create a calendar event | `title` (required), `start` (required, YYYY-MM-DD HH:mm), `end` (required, YYYY-MM-DD HH:mm), `location`, `description`, `calendar_id` (default: primary writable calendar) |
| `search_events` | Search events by keyword in title or description | `query` (required), `limit` (1-100, default 10) |

Needs `READ_CALENDAR`. `create_event` is registered only when `WRITE_CALENDAR` is also granted. `search_events` returns a recurring event once, not once per occurrence.

## Contacts

| Tool | Description | Parameters |
|------|-------------|------------|
| `search_contacts` | Search contacts by display name (substring) | `query` (required), `limit` (1-100, default 10) |
| `read_contact` | Full details for one contact | `contact_id` (required) |
| `list_contacts` | Paginated contact list | `limit` (1-100, default 50), `offset` (default 0) |

Needs `READ_CONTACTS`.

## SMS

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_messages` | Read SMS, filtered by box, number or date | `box` (inbox/sent, default inbox), `address`, `since` (YYYY-MM-DD), `limit` (1-100, default 10) |
| `send_message` | Send an SMS. Waits up to 15 s for the carrier and reports `status` as `sent`, `failed` or `timeout`. | `to` (required, phone number), `body` (required) |
| `search_messages` | Search message bodies by keyword | `query` (required), `limit` (1-100, default 10) |

Needs `READ_SMS` and `SEND_SMS`. `send_message` rejects malformed phone numbers.

## Files

| Tool | Description | Parameters |
|------|-------------|------------|
| `browse_files` | List a directory (name, size, modified date, is-directory) | `path` (default `/sdcard`), `limit` (1-100, default 10) |
| `read_file` | Read a text file. Binary files return an error. | `path` (required), `max_lines` (1-1000, default 100) |
| `search_files` | Recursive, case-insensitive filename search (depth 5, time-bounded) | `query` (required), `path` (default `/sdcard`), `limit` (1-100, default 10) |

Paths are sandboxed to external storage. Anything else is rejected. On Android 11+, other apps' non-media files are not visible. Needs `READ_EXTERNAL_STORAGE` on API 32 and below, and nothing on API 33+.

## Notifications

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_active_notifications` | The host app's own active notifications | `limit` (1-100, default 10) |

Returns only notifications the host app posted. To read other apps' notifications, use [Notifications (Reply)](#notifications-reply) or [Notification Watch](#notification-watch).

## Call Log

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_call_log` | Recent calls | `limit` (1-100, default 10), `offset` (default 0), `type` (all/incoming/outgoing/missed, default all) |
| `search_call_log` | Search by number or contact name (substring) | `query` (required), `limit` (1-100, default 10) |

Needs `READ_CALL_LOG`.

## Media

| Tool | Description | Parameters |
|------|-------------|------------|
| `search_media` | Search photos and videos by filename or date taken | `query`, `start_date` (YYYY-MM-DD), `end_date` (YYYY-MM-DD, inclusive), `media_type` (images/videos/all, default all), `limit` (1-100, default 10), `offset` (default 0) |
| `get_media_metadata` | Metadata for one MediaStore item | `media_id` (required), `media_type` (image/video, default image) |
| `list_albums` | Albums (MediaStore buckets) with item counts | `limit` (1-100, default 10), `media_type` (images/videos/all, default images) |

Needs `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` on API 33+, and `READ_EXTERNAL_STORAGE` below that.

## Location

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_current_location` | Freshest last-known cached fix across enabled providers. Does not request a new fix. | `accuracy` (fine/coarse, default coarse; sets provider preference) |
| `get_location_address` | Reverse-geocode coordinates to an address | `latitude` (required), `longitude` (required) |

`get_current_location` needs `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`, and returns an error when no cached fix exists. `get_location_address` uses the platform `Geocoder`, which needs network access but no location permission.

## Health

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_step_count` | Steps since last reboot (step counter sensor) | -- |
| `get_activity_info` | Available motion sensors and their specs | -- |

`get_step_count` needs `ACTIVITY_RECOGNITION` on Android 10+. It reads the sensor, not Health Connect, and the count resets on reboot.

## Clipboard

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_clipboard` | Read the current clipboard | -- |
| `write_clipboard` | Write text to the clipboard | `text` (required), `label` (default `droid-mcp`) |

On Android 10+, `read_clipboard` works only while the host app is in the foreground or is the default keyboard. Otherwise it returns an error.

## Apps

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_installed_apps` | List installed apps | `include_system` (default false), `limit` (1-100, default 50) |
| `get_app_info` | Details for one app | `package_name` (required) |
| `launch_app` | Launch an app | `package_name` (required) |

## Alarms

| Tool | Description | Parameters |
|------|-------------|------------|
| `create_alarm` | Set an alarm in the clock app | `hour` (required, 0-23), `minute` (required, 0-59), `message`, `days` (comma-separated, e.g. `mon,wed`; empty = one-time) |
| `create_timer` | Start a countdown timer | `seconds` (required, > 0), `message` |
| `create_reminder` | Calendar event with an alert | `title` (required), `datetime` (required, YYYY-MM-DD HH:mm), `minutes_before` (≥ 0, default 10) |

Alarm and timer use `SET_ALARM`. `create_reminder` is registered only when `READ_CALENDAR` and `WRITE_CALENDAR` are granted. Reading existing alarms is not supported.

## Settings

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_settings` | Brightness, volume, WiFi, Bluetooth, airplane mode, auto-rotate | -- |
| `set_brightness` | Set screen brightness (switches to manual mode) | `level` (required, 0-255) |
| `set_volume` | Set a stream's volume | `stream` (media/ring/alarm/notification, default media), `level` (required, 0 to stream max) |
| `toggle_wifi` | Turn WiFi on or off | `enabled` (required) |

`get_settings` and `set_volume` are always registered. `set_brightness` is registered only when `WRITE_SETTINGS` is granted, and `toggle_wifi` only with `CHANGE_WIFI_STATE`. On Android 10+, `toggle_wifi` opens the system WiFi panel instead of toggling.

## Bluetooth

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_bluetooth_status` | Adapter state and details | -- |
| `list_paired_devices` | Bonded devices | -- |

Needs `BLUETOOTH_CONNECT` on Android 12+, and `BLUETOOTH` below that.

## WiFi

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_wifi_info` | Current connection: SSID, BSSID, IP, link speed, RSSI, frequency | -- |
| `list_saved_networks` | Saved networks | -- |

Needs `ACCESS_WIFI_STATE` and `ACCESS_FINE_LOCATION`. Without location permission, SSID and BSSID are hidden. `list_saved_networks` always returns an empty list on Android 10+.

## Downloads

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_downloads` | Files in the Downloads folder | `limit` (1-100, default 10), `sort_by` (date/name/size, default date) |
| `search_downloads` | Search Downloads by filename (case-insensitive) | `query` (required), `limit` (1-100, default 10) |

Needs `READ_EXTERNAL_STORAGE` on API 32 and below. On Android 11+, other apps' non-media files are hidden.

## Screen

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_screen_state` | Screen on/off, rotation, brightness, lock state | -- |
| `get_display_info` | Resolution, density, refresh rate, HDR capability | -- |

## Text-to-Speech

| Tool | Description | Parameters |
|------|-------------|------------|
| `speak_text` | Speak text aloud | `text` (required), `language` (BCP-47, default `en`), `pitch` (0.5-2.0, default 1.0), `speed` (0.5-2.0, default 1.0) |
| `get_tts_info` | Default TTS engine and its languages | -- |

## Web

| Tool | Description | Parameters |
|------|-------------|------------|
| `web_search` | Search DuckDuckGo. Returns title, URL and snippet. | `query` (required), `limit` (1-50, default 5) |
| `fetch_webpage` | Fetch an http(s) URL and return its readable text | `url` (required), `max_length` (≥ 1, default 2000) |

By default, both tools block loopback, private, link-local and similar addresses, including on redirects. `WebTools.all(context, allowPrivateNetwork = true)` turns this off. `fetch_webpage` reads at most 5 MB of the response body.

## Flashlight

| Tool | Description | Parameters |
|------|-------------|------------|
| `toggle_flashlight` | Turn the torch on or off | `enabled` (required) |
| `set_flashlight_brightness` | Set torch strength (Android 13+) | `level` (required, 0-255; 0 = off) |

Both tools need a camera with a flash. `set_flashlight_brightness` maps `level` onto the device's strength range. On devices without variable strength, it just turns the torch on.

## Network

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_data_usage` | Mobile data received and sent (bytes) | `days` (1-90, default 30) |
| `get_cellular_signal` | Signal strength (ASU, dBm, level) | -- |
| `is_vpn_active` | Whether the active network uses a VPN | -- |

All three tools need `ACCESS_NETWORK_STATE`. `get_data_usage` needs Usage access (`PACKAGE_USAGE_STATS`) to apply the `days` window. Without it, the tool falls back to since-boot `TrafficStats` totals and adds a `note`. `is_vpn_active` usually reports the VPN package as `"unknown"`.

## Telephony

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_phone_number` | Line-1 phone number, if the SIM provides it | -- |
| `get_sim_info` | SIM serial, carrier, country, subscription ID, physical slot index | -- |
| `get_network_operator` | Operator name, ID, MCC, MNC | -- |
| `get_call_state` | Call state (idle/ringing/active) | -- |

The module declares `READ_PHONE_STATE` and `READ_PHONE_NUMBERS`, but `get_network_operator` needs neither. `get_call_state` needs `READ_PHONE_STATE`. The other tools return null for fields they cannot read, and `-1` for unknown slot or subscription IDs. The SIM serial is usually null on Android 10+.

## Vibration

| Tool | Description | Parameters |
|------|-------------|------------|
| `vibrate` | Vibrate for a duration | `duration_ms` (required, 1-10000), `amplitude` (1-255, default: system) |
| `vibrate_pattern` | Vibrate a pattern of alternating OFF/ON durations | `timings` (required, array of ms; each ≤ 10000, total ≤ 30000; out of range is rejected), `repeat` (index, default -1 = no repeat) |
| `cancel_vibration` | Stop vibration started by this app | -- |

Needs `VIBRATE`. A repeating pattern runs until `cancel_vibration` is called, or for at most 60 s.

## Biometric

| Tool | Description | Parameters |
|------|-------------|------------|
| `check_biometric_availability` | Availability of strong biometric, weak biometric and device credential authenticators | -- |
| `get_biometric_enrollments` | Present and enrolled modalities (fingerprint, face) | -- |

These tools read `BiometricManager` and need no runtime permission.

## Sensors

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_accelerometer` | Acceleration x, y, z (m/s²) | `duration_ms` (1-5000) |
| `get_gyroscope` | Rotation x, y, z (rad/s) | `duration_ms` (1-5000) |
| `get_light_level` | Ambient light (lux) | `duration_ms` (1-5000) |
| `get_proximity` | Distance (cm) and `is_near` (< 5 cm) | `duration_ms` (1-5000) |

No permissions needed. Without `duration_ms`, each tool takes a single reading. With it, the tool collects readings for that long. If the sensor is missing, the tool returns an error.

## QR / Barcode

| Tool | Description | Parameters |
|------|-------------|------------|
| `scan_qr_code` | Decode a QR code from an image | `image_uri` (required) |
| `scan_barcode` | Decode an EAN-13, EAN-8, UPC-A, UPC-E, CODE-128 or CODE-39 barcode from an image | `image_uri` (required) |
| `generate_qr_code` | Generate a QR code as base64 PNG | `text` (required), `size` (100-1000, default 300) |

Scanning uses ML Kit and generation uses ZXing. `image_uri` may be a `file://` path on external storage or a `content://` URI. The host app's own content authorities are refused.

## Camera

| Tool | Description | Parameters |
|------|-------------|------------|
| `take_photo` | Take a photo without a preview. Saves a full-resolution JPEG to the gallery. With `return_data`, also returns a downscaled base64 copy. | `return_data` (default false), `max_dimension` (64-4096, default 1280), `format` (jpeg/png, default jpeg), `quality` (1-100, default 85) |
| `capture_video` | Record a video | `duration_sec` (1-60, default 10) |
| `get_camera_capabilities` | List cameras and their capabilities | -- |

Needs `CAMERA`, plus `WRITE_EXTERNAL_STORAGE` on API 28. Photos are saved to `Pictures/droid-mcp` and videos to `Movies/droid-mcp`.

## Audio

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_audio_devices` | Connected audio devices: ID, type, name, whether each is an output | -- |

## NFC

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_nfc_status` | Whether NFC is present and enabled | -- |
| `read_nfc_tag` | NDEF data from the last scanned tag. Reads live if the tag is in range, otherwise returns cached data (`cached = true`). | -- |
| `write_nfc_tag` | Write one NDEF record to the last scanned tag | `type` (required, text/uri), `content` (required) |

The host app must pass discovered tags to `NfcTagCache.update(tag)`. `write_nfc_tag` fails if the tag is read-only or the record exceeds its capacity.

## Intent / Share

| Tool | Description | Parameters |
|------|-------------|------------|
| `send_intent` | Fire an allowlisted intent | `action` (required), `data`, `type`, `package_name`, `extras` (object; string extras) |
| `share_content` | Share text via the share sheet | `text` (required), `subject`, `type` (default `text/plain`) |
| `open_deep_link` | Open a URI with ACTION_VIEW | `uri` (required), `package_name` |

`send_intent` allows only these actions: VIEW, DIAL, SEND, SENDTO, CHOOSER, SEARCH, WEB_SEARCH and EDIT. Data URIs for `send_intent` and `open_deep_link` must use one of these schemes: http, https, geo, tel, mailto, sms, smsto, mms, mmsto or market. Any other scheme returns `uri_scheme_not_allowed`. All three tools return `background_activity_launch_blocked` when a background launch is blocked.

## Playback

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_now_playing` | Currently playing media from active sessions | -- |
| `media_control` | Send a playback command | `command` (required: play/pause/stop/next/previous), `package_name` |

Needs Notification access. The host must register a `NotificationListenerService` and call `NotificationListenerHolder.set(componentName)`.

## Notifications (Reply)

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_repliable_notifications` | Active notifications with a free-form RemoteInput reply action | `limit` (1-100, default 20) |
| `reply_to_notification` | Reply through a notification's RemoteInput action | `key` (required), `text` (required) |
| `dismiss_notification` | Cancel a notification by key | `key` (required) |
| `invoke_notification_action` | Trigger a non-reply action (e.g. Mark as read) | `key` (required), plus one of `action_label` (case-insensitive substring) or `action_index` (0-based) |

Needs Notification access. The host's listener service must extend `McpNotificationListenerServiceBase`, which fills the notification store that all four tools read, and must be registered with `NotificationListenerHolder.set(componentName)`. `reply_to_notification` succeeds once the PendingIntent fires. It does not confirm that the receiving app delivered the message.

## Notification Watch

| Tool | Description | Parameters |
|------|-------------|------------|
| `watch_notifications` | Register a filter on newly posted notifications. Returns a `watch_id`. | at least one of `package_name` (exact), `sender_pattern` (substring of title), `keyword` (substring of text/big text/sub text/ticker); `ttl_seconds` (60-86400, default 3600), `fire_on_update` (default false) |
| `unwatch_notifications` | Remove a watch. An unknown ID returns success with `removed = false`. | `watch_id` (required) |
| `list_notification_watches` | Active watches with remaining TTL | -- |
| `poll_notification_watch` | Notifications a watch matched, oldest first (up to 50 buffered per watch) | `watch_id` (required), `clear` (default true) |

Filters combine with AND, and substring matches ignore case. A watch fires once per notification key unless `fire_on_update` is set. It only sees notifications posted after it was registered. At most 50 watches can be active. The module shares the listener setup with Notifications (Reply).

Hosts can also collect `NotificationListenerBus.events` (`SharedFlow<NotificationEvent>`, in `droid-mcp-notification-listener`) directly. `NotificationEvent` has these fields:

- `key`, `packageName`
- `title`, `text`, `bigText`, `subText`, `tickerText`
- `category`, `channelId`, `groupKey`
- `isOngoing`, `isClearable`
- `legacyPriority`, `channelImportance` (`-1` if unknown)
- `postedAt`, `when`
- `hasReplyAction`, `actionLabels`

## Accessibility

| Tool | Description | Parameters |
|------|-------------|------------|
| `query_screen` | Active window's UI tree as a flat node list, ranked clickable > has-text > scrollable > rest. Truncation keeps the top-ranked nodes. Password text is masked. | `max_nodes` (1-2000, default 500) |
| `find_node` | Find nodes by text, view ID, class or package | `text` (substring of text/description), `view_id`, `class_name`, `package_name` (exact); at least one required; `limit` (1-200, default 20) |
| `wait_for_text` | Wait until text appears or the window changes. Returns `status` `matched` or `timeout` (a timeout is not an error). | `condition` (text/window_change, default text), `text` (required when condition = text), `timeout_ms` (100-60000, default 5000), `poll_ms` (50-2000, default 200) |
| `click_node` | ACTION_CLICK on a matched node | selector, `index` (default 0) |
| `long_click_node` | ACTION_LONG_CLICK on a matched node | selector, `index` (default 0) |
| `set_node_text` | Replace an editable node's text (ACTION_SET_TEXT). Returns `node_not_editable` for a read-only node. | `text` (required, new value); selector with `match_text` in place of `text`; `index` (default 0) |
| `scroll_node` | Scroll a matched node | selector, `index` (default 0), `direction` (forward/backward, default forward) |
| `gesture` | Dispatch a stroke through a list of points | `points` (required, ≥ 2 `[x, y]` pairs), `duration_ms` (10-3000, default 300) |
| `global_action` | System action: back, home, recents, notifications, quick_settings, power_dialog, lock_screen, screenshot | `action` (required) |
| `get_active_window_info` | Foreground package, root class, window ID | -- |
| `take_screenshot_via_a11y` | Screenshot without a MediaProjection prompt, returned as base64 (Android 11+) | `format` (jpeg/png, default jpeg), `quality` (1-100, default 80), `max_dimension` (64-4096, default 1280) |
| `tap` | Tap at screen coordinates | `x` (required), `y` (required) |
| `long_press` | Long-press at screen coordinates | `x` (required), `y` (required), `duration_ms` (100-5000, default 800) |
| `find_and_tap` | Find a node and click it (returns `node_not_found` if absent) | `match` (required), `match_kind` (text/desc/id/class, default text), `case_insensitive` (default true) |
| `scroll_to_find` | Swipe until `match` appears. `down` reveals content below. | `match` (required), `direction` (down/up/left/right, default down), `max_scrolls` (1-20, default 5) |

The selector is `text` (a substring of text or content description), `view_id`, `class_name` or `package_name` (exact matches). At least one is required.

The user must enable the host's accessibility service, which must extend `DroidMcpAccessibilityService`. `AccessibilityTools.supportedTools(context)` drops `take_screenshot_via_a11y` below Android 11.

## IME

| Tool | Description | Parameters |
|------|-------------|------------|
| `is_ime_active` | Whether the droid-mcp keyboard is active with an editor bound | -- |
| `type_text` | Commit text at the cursor | `text` (required) |
| `commit_keystroke` | Send a named key: enter, backspace, del, tab, escape, up, down, left, right, home, end, page_up, page_down | `key` (required) |
| `delete_text` | Delete characters around the cursor | `before` (0-2000, default 0), `after` (0-2000, default 0) |
| `set_selection` | Move the cursor or select a range | `start` (required), `end` (required) |
| `get_text_around_cursor` | Text before and after the cursor. Password fields are refused (`password_field`). | `before` (1-2000, default 200), `after` (1-2000, default 200) |
| `switch_to_previous_ime` | Switch back to the previous keyboard | -- |

The host's IME service must extend `DroidMcpInputMethodService`. The user must enable it in keyboard settings and select it in the IME picker.

## Overlay

This module has no LLM tools. `droid-mcp-overlay` exposes `OverlayController` for a floating button:

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

Needs `SYSTEM_ALERT_WINDOW` ("Display over other apps"). Uses `TYPE_APPLICATION_OVERLAY`.

## Shizuku (Tier 4 — shell-UID admin)

`droid-mcp-shell-core` defines the tools below. `droid-mcp-shizuku` runs them through Shizuku, and `droid-mcp-root` runs them through `su` (see [Root](#root-tier-5--same-surface-broader-privilege)). Register one backend, not both.

### Package manager

| Tool | Description | Parameters |
|------|-------------|------------|
| `install_apk` | Silent `pm install` | `path` (required, absolute, ends in `.apk`), `replace` (default true → `-r`) |
| `uninstall_app` | Silent `pm uninstall` | `package_name` (required), `keep_data` (default false → `-k`) |
| `clear_app_data` | `pm clear` (data and cache) | `package_name` (required) |
| `force_stop_app` | `am force-stop` | `package_name` (required) |
| `disable_app` | `pm disable-user --user 0`. Reversible, idempotent. | `package_name` (required) |
| `enable_app` | `pm enable`. Idempotent. | `package_name` (required) |

### Permissions

| Tool | Description | Parameters |
|------|-------------|------------|
| `grant_permission` | `pm grant`. Idempotent. | `package_name` (required), `permission` (required) |
| `revoke_permission` | `pm revoke`. Idempotent. | `package_name` (required), `permission` (required) |
| `list_app_permissions` | Requested and granted permissions from `dumpsys package` | `package_name` (required) |

### Settings

| Tool | Description | Parameters |
|------|-------------|------------|
| `put_secure_setting` | `settings put secure` | `key` (required), `value` (required) |
| `put_global_setting` | `settings put global` | `key` (required), `value` (required) |
| `put_system_setting` | `settings put system` | `key` (required), `value` (required) |

### Dumpsys / state

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_top_window` | Foreground package and activity from `dumpsys window` | -- |

### Standby

| Tool | Description | Parameters |
|------|-------------|------------|
| `set_app_standby_bucket` | `am set-standby-bucket`. Idempotent. | `package_name` (required), `bucket` (required: active/working_set/frequent/rare/restricted) |
| `make_app_inactive` | `am set-inactive <pkg> true`. Idempotent. | `package_name` (required) |

### Screencap

| Tool | Description | Parameters |
|------|-------------|------------|
| `capture_screen_quiet` | `screencap -p` as base64 PNG, with no consent prompt | `display` (default 0) |

### Escape hatch

| Tool | Description | Parameters |
|------|-------------|------------|
| `run_shell` | Run a command. Refused unless the host allowlisted it with `ShellAllowlist.set(...)`. | `command` (required), `args` (array; argv form, no shell splitting), `max_stdout_bytes` (1024-65536, default 8192; applies to stderr too) |

Allowlist entries match argv token by token. Entries that start with an interpreter or exec wrapper (`sh`, `su`, `toybox`, `env`, …) throw. In string form, `command` is split on whitespace, so use `args` for anything with spaces or quotes. `ShizukuTools.all` and `RootTools.all` accept a `ShellPolicy`. The default, `ShellPolicy.RECOMMENDED`, denies sensitive setting keys and permission grants; `PERMISSIVE` denies nothing.

Needs Shizuku running and the host granted its permission. See [SHIZUKU.md](SHIZUKU.md).

## Root (Tier 5 — same surface, broader privilege)

`droid-mcp-root` exposes the same 17 tools, with the same names, parameters and output, through libsu's `su` shell. Each call opens its own `su` session. Root-only abilities (`pm hide`, `/system` writes, reading `/data/data/<pkg>`) are reachable only through `run_shell` with an allowlisted command.

Register `ShizukuTools.all(context)` or `RootTools.all(context)`, not both. The registry keeps the last tool registered under each name. See [ROOT.md](ROOT.md) for picking a backend at startup.

## Screenshot

| Tool | Description | Parameters |
|------|-------------|------------|
| `capture_screen` | Screenshot via MediaProjection, saved to `Pictures/droid-mcp`. Returns path and content URI. | `format` (png/jpeg, default png), `quality` (1-100, default 90; JPEG only) |

The host must get user consent through `MediaProjectionManager.createScreenCaptureIntent()` and pass the projection to `MediaProjectionHolder.set(projection)`. On Android 14+, it must also run a `mediaProjection` foreground service. On API 28 without `WRITE_EXTERNAL_STORAGE`, the image goes to app cache (`saved_to = "cache"`).

## Do Not Disturb

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_dnd_status` | DND status and interruption filter | -- |
| `set_dnd_mode` | Set DND mode. Reports the resulting `actual_mode`. | `mode` (required: off/priority/alarms/none) |

`get_dnd_status` needs no special access. `set_dnd_mode` needs DND access ("Do Not Disturb access" in Settings). On Android 15+, it controls only the app's own DND rule, which the system merges with the user's settings.

## Keyguard

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_lock_state` | Whether the device is locked and the screen is on | -- |
| `get_keyguard_info` | Whether a secure lock (PIN/pattern/password) is set, plus lock state | -- |

No permissions needed.

## Wallpaper

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_wallpaper_info` | Wallpaper dimensions and whether a live wallpaper is active | -- |
| `set_wallpaper` | Set the wallpaper from an image file | `path` (required), `target` (home/lock/both, default both) |

Needs `SET_WALLPAPER`. `path` is sandboxed to external storage. `set_wallpaper` returns an error when device policy disallows setting the wallpaper.

## Ringtone

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_ringtones` | Available sounds of a type | `type` (ringtone/notification/alarm, default ringtone), `limit` (1-100, default 50) |
| `get_active_ringtone` | Current default sound of a type | `type` (ringtone/notification/alarm, default ringtone) |
| `set_ringtone` | Set the default sound of a type | `uri` (required, `content://` URI or `silent`), `type` (ringtone/notification/alarm, default ringtone) |

The read tools need no permissions. `set_ringtone` needs `WRITE_SETTINGS` ("Modify system settings") and rejects non-`content://` URIs.

## USB

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_usb_devices` | Connected USB devices | -- |
| `get_usb_device_info` | Descriptor details for one device, including interfaces and endpoints | `device_name` (required, from `list_usb_devices`) |

Needs USB host support and no permission. Results include `has_permission`, which tells whether the app may open the device.

## Print

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_printers` | Installed print services and active print jobs | -- |
| `print_content` | Open the system print dialog for text or HTML | `content` (required), `job_name` (default `droid-mcp print`), `is_html` (default false; plain text is wrapped in HTML) |

`print_content` needs an Activity. Pass one with `PrintTools.all(context) { currentActivity }`. Success means the dialog opened, not that anything printed. `job_id` may be null.

## ML Kit

| Tool | Description | Parameters |
|------|-------------|------------|
| `recognize_text` | Extract text from an image | `image_path` (required) |
| `label_image` | Label image contents | `image_path` (required), `min_confidence` (0.0-1.0, default 0.5) |
| `detect_faces` | Face bounding boxes, smile/eye-open probabilities and head angles. Does not identify faces. | `image_path` (required) |

`image_path` is sandboxed to external storage. The tools use bundled on-device ML Kit models.
