# Tool Reference

Every tool droid-mcp ships: 151 tools in 45 modules, one section per module. Each section starts with what the module needs (permissions, special access, host setup), followed by a table of its tools.

The generated contract [`droid-mcp-all/api/tool-contract.txt`](../droid-mcp-all/api/tool-contract.txt) lists every tool's parameter types, ranges, enums and annotations. If this page and the contract disagree, the contract is right. Tools annotated `readOnly` there are the only ones listed and callable when the server runs with `readOnly = true`.

**Reading the Parameters column.** Parameters are optional unless marked (required). Ranges are inclusive: `limit` (1-100, default 10). Enum values are separated by slashes: `box` (`inbox`/`sent`). A dash means the tool takes no parameters.

**Image results.** `take_photo` (with `return_data`), `take_screenshot_via_a11y`, `capture_screen_quiet` and `generate_qr_code` return their image as an MCP `image` content block. Over MCP, the base64 key (`image_data`, `image_base64` or `qr_image`) is left out of the JSON text so the image is sent only once. In-process callers still find the base64 under that key in `ToolResult.data`.

> [!IMPORTANT]
> On Android 10+, a tool that starts an activity returns an error when the host app is in the background and lacks "Display over other apps" (`SYSTEM_ALERT_WINDOW`). This applies to `launch_app`, `create_alarm`, `create_timer`, `toggle_wifi`, `set_brightness` (when it opens the grant screen), `send_intent`, `share_content` and `open_deep_link`.

The `shell-core` tools (17) are registered through either the `shizuku` or the `root` module. `core`, `notification-listener`, `overlay`, `audit`, `tls` and `server-service` expose no LLM tools.

## Contents

| Area | Modules |
|------|---------|
| Personal data | [Calendar](#calendar), [Contacts](#contacts), [SMS](#sms), [Call Log](#call-log), [Files](#files), [Media](#media), [Downloads](#downloads), [Clipboard](#clipboard), [Notifications](#notifications) |
| Device state | [Device](#device), [Screen](#screen), [Keyguard](#keyguard), [Telephony](#telephony), [Network](#network), [WiFi](#wifi), [Bluetooth](#bluetooth), [Audio](#audio), [USB](#usb), [NFC](#nfc), [Biometric](#biometric) |
| Sensors and location | [Location](#location), [Sensors](#sensors), [Health](#health) |
| Device actions | [Apps](#apps), [Alarms](#alarms), [Settings](#settings), [Do Not Disturb](#do-not-disturb), [Flashlight](#flashlight), [Vibration](#vibration), [Wallpaper](#wallpaper), [Ringtone](#ringtone), [Text-to-Speech](#text-to-speech), [Print](#print), [Intent and Share](#intent-and-share) |
| Camera and vision | [Camera](#camera), [Screenshot](#screenshot), [QR and Barcode](#qr-and-barcode), [ML Kit](#ml-kit) |
| Web | [Web](#web) |
| Other apps and UI | [Playback](#playback), [Notification Reply](#notification-reply), [Notification Watch](#notification-watch), [Accessibility](#accessibility), [IME](#ime), [Overlay](#overlay) |
| Power tools (opt-in) | [Shell: Shizuku or root](#shell-shizuku-or-root) |

---

## Calendar

**Needs:** `READ_CALENDAR`. `create_event`, `update_event` and `delete_event` are registered only when `WRITE_CALENDAR` is also granted.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `read_calendar` | Events in a date range, including occurrences of recurring events and multi-day events | `start_date` (required, YYYY-MM-DD)<br>`end_date` (YYYY-MM-DD, default `start_date`)<br>`limit` (1-100, default 10) |
| `search_events` | Events whose title or description contains a keyword. A recurring event is returned once, not per occurrence. | `query` (required)<br>`limit` (1-100, default 10) |
| `create_event` | Create an event | `title` (required)<br>`start` (required, YYYY-MM-DD HH:mm)<br>`end` (required, YYYY-MM-DD HH:mm)<br>`location`<br>`description`<br>`calendar_id` (integer, default: primary writable calendar) |
| `update_event` | Change an event. Only the fields passed are written. | `event_id` (required, ≥ 1)<br>`title`<br>`description`<br>`location`<br>`start` (YYYY-MM-DD HH:mm, or YYYY-MM-DD for all-day)<br>`end` (same format; exclusive for all-day)<br>`all_day` (changing it needs both `start` and `end`) |
| `delete_event` | Delete an event. For a recurring event this deletes the whole series. | `event_id` (required, ≥ 1) |

`update_event` and `delete_event` return an error when the event doesn't exist (so a second delete reports not-found) or its calendar is read-only. When `update_event` gets only one of `start`/`end`, it checks the new value against the event's stored other end. For a recurring event it can change the title, description and location (for every occurrence) but not the times.

## Contacts

**Needs:** `READ_CONTACTS`. `create_contact` is registered only when `WRITE_CONTACTS` is also granted.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `search_contacts` | Contacts whose display name contains the query | `query` (required)<br>`limit` (1-100, default 10) |
| `read_contact` | Full details for one contact | `contact_id` (required, integer) |
| `list_contacts` | Paginated contact list | `limit` (1-100, default 50)<br>`offset` (≥ 0, default 0) |
| `create_contact` | Add a contact to the local device account. Returns `contact_id` (usable with `read_contact`) and `raw_contact_id`. | `name` (required)<br>`phone`<br>`phone_type` (`mobile`/`home`/`work`/`other`, default `mobile`)<br>`email`<br>`email_type` (`home`/`work`/`other`, default `home`) |

## SMS

**Needs:** `READ_SMS` and `SEND_SMS`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `read_messages` | Messages from one box, optionally filtered by number or date | `box` (`inbox`/`sent`, default `inbox`)<br>`address`<br>`since` (YYYY-MM-DD)<br>`limit` (1-100, default 10) |
| `search_messages` | Messages whose body contains a keyword | `query` (required)<br>`limit` (1-100, default 10) |
| `send_message` | Send an SMS and wait up to 15 s for the radio to confirm each part | `to` (required, phone number)<br>`body` (required) |

`send_message` rejects malformed phone numbers. A radio-reported failure is an error. If no confirmation arrives within 15 s, the call still succeeds, with `sent: false` and `status: "timeout"`: the message may still arrive, so don't resend it automatically. A confirmed send returns `sent: true` and `status: "sent"`.

## Call Log

**Needs:** `READ_CALL_LOG`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `read_call_log` | Recent calls, newest first | `limit` (1-100, default 10)<br>`offset` (≥ 0, default 0)<br>`type` (`all`/`incoming`/`outgoing`/`missed`, default `all`) |
| `search_call_log` | Calls whose number or contact name contains the query | `query` (required)<br>`limit` (1-100, default 10) |

## Files

**Needs:** `READ_EXTERNAL_STORAGE` on API 32 and below; nothing on API 33+.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `browse_files` | List a directory: name, size, modified date, whether each entry is a directory | `path` (default `/sdcard`)<br>`limit` (1-100, default 10) |
| `read_file` | Read a text file. Binary files return an error. | `path` (required)<br>`max_lines` (1-1000, default 100) |
| `search_files` | Case-insensitive filename search, recursive to depth 5 and time-bounded | `query` (required)<br>`path` (default `/sdcard`)<br>`limit` (1-100, default 10) |

Paths are sandboxed to external storage; anything outside it is rejected. On Android 11+, other apps' non-media files are not visible.

## Media

**Needs:** `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` on API 33+; `READ_EXTERNAL_STORAGE` below that.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `search_media` | Photos and videos by filename or date taken | `query`<br>`start_date` (YYYY-MM-DD)<br>`end_date` (YYYY-MM-DD, inclusive)<br>`media_type` (`images`/`videos`/`all`, default `all`)<br>`limit` (1-100, default 10)<br>`offset` (≥ 0, default 0) |
| `get_media_metadata` | Metadata for one MediaStore item | `media_id` (required, integer)<br>`media_type` (`image`/`video`, default `image`) |
| `list_albums` | Albums (MediaStore buckets) with item counts | `limit` (1-100, default 10)<br>`media_type` (`images`/`videos`/`all`, default `images`) |

## Downloads

**Needs:** `READ_EXTERNAL_STORAGE` on API 32 and below; nothing on API 33+.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `list_downloads` | Files in the Downloads folder | `limit` (1-100, default 10)<br>`sort_by` (`date`/`name`/`size`, default `date`) |
| `search_downloads` | Downloads whose filename contains the query (case-insensitive) | `query` (required)<br>`limit` (1-100, default 10) |

On Android 11+, other apps' non-media files are hidden.

## Clipboard

**Needs:** nothing. On Android 10+, `read_clipboard` works only while the host app is in the foreground or is the default keyboard; otherwise it returns an error.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `read_clipboard` | Read the current clipboard | - |
| `write_clipboard` | Put text on the clipboard | `text` (required)<br>`label` (default `droid-mcp`) |

## Notifications

**Needs:** nothing.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_active_notifications` | The host app's own active notifications | `limit` (1-100, default 10) |

This returns only notifications the host app posted. To read other apps' notifications, use [Notification Reply](#notification-reply) or [Notification Watch](#notification-watch).

## Device

**Needs:** `ACCESS_NETWORK_STATE` (for `get_connectivity`).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_device_info` | Model, manufacturer, OS and SDK version, screen size | - |
| `get_battery_info` | Battery level, charging status, charging source | - |
| `get_connectivity` | Whether the active network is up, and whether it uses WiFi, cellular or Bluetooth | - |
| `get_storage_info` | Total, available and used storage in bytes | - |

## Screen

**Needs:** nothing.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_screen_state` | Screen on/off, rotation, brightness, lock state | - |
| `get_display_info` | Resolution, density, refresh rate, HDR capability | - |

## Keyguard

**Needs:** nothing.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_lock_state` | Whether the device is locked and the screen is on | - |
| `get_keyguard_info` | Whether a secure lock (PIN, pattern or password) is set, plus lock state | - |

## Telephony

**Needs:** `READ_PHONE_STATE` and `READ_PHONE_NUMBERS` (declared). `get_network_operator` needs neither; `get_call_state` needs `READ_PHONE_STATE`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_phone_number` | Line-1 phone number, if the SIM provides it | - |
| `get_sim_info` | SIM serial, carrier, country, subscription ID, physical slot index | - |
| `get_network_operator` | Operator name, ID, MCC, MNC | - |
| `get_call_state` | Call state: idle, ringing or active | - |

Fields a tool cannot read are null; unknown slot or subscription IDs are `-1`. The SIM serial is usually null on Android 10+.

## Network

**Needs:** `ACCESS_NETWORK_STATE`. `get_data_usage` also uses Usage access (`PACKAGE_USAGE_STATS`, granted in Settings) to apply the `days` window.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_data_usage` | Mobile data received and sent, in bytes | `days` (1-90, default 30) |
| `get_cellular_signal` | Signal strength: ASU, dBm, level | - |
| `is_vpn_active` | Whether the active network uses a VPN | - |

Without Usage access, `get_data_usage` falls back to since-boot `TrafficStats` totals and adds a `note`. `is_vpn_active` usually reports the VPN package as `"unknown"`.

## WiFi

**Needs:** `ACCESS_WIFI_STATE` and `ACCESS_FINE_LOCATION`. Without location permission, SSID and BSSID are hidden.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_wifi_info` | Current connection: SSID, BSSID, IP, link speed, RSSI, frequency | - |
| `list_saved_networks` | Saved networks. Always an empty list on Android 10+. | - |

## Bluetooth

**Needs:** `BLUETOOTH_CONNECT` on Android 12+; `BLUETOOTH` below that.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_bluetooth_status` | Adapter state and details | - |
| `list_paired_devices` | Bonded devices | - |

## Audio

**Needs:** nothing.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_audio_devices` | Connected audio devices: ID, type, name, whether each is an output | - |

## USB

**Needs:** USB host support; no permission. Results include `has_permission`, which says whether the app may open the device.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `list_usb_devices` | Connected USB devices | - |
| `get_usb_device_info` | Descriptor details for one device, including interfaces and endpoints | `device_name` (required, from `list_usb_devices`) |

## NFC

**Needs:** `NFC` (install-time). The host app must pass each discovered tag to `NfcTagCache.update(tag)`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_nfc_status` | Whether NFC is present and enabled | - |
| `read_nfc_tag` | NDEF data from the last scanned tag: read live if the tag is in range, otherwise cached data with `cached = true` | - |
| `write_nfc_tag` | Write one NDEF record to the last scanned tag. Fails if the tag is read-only or the record exceeds its capacity. | `type` (required, `text`/`uri`)<br>`content` (required) |

## Biometric

**Needs:** nothing (reads `BiometricManager`).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `check_biometric_availability` | Availability of strong biometric, weak biometric and device-credential authenticators | - |
| `get_biometric_enrollments` | Present and enrolled modalities (fingerprint, face) | - |

## Location

**Needs:** `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` for `get_current_location`. `get_location_address` needs network access but no location permission.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_current_location` | Freshest cached fix across enabled providers. When nothing is cached (for example on a fresh device), requests a fresh fix from every enabled provider and waits up to 10 s. `source` is `cache` or `fresh`; check `timestamp`, since a cached fix can be old. | `accuracy` (`fine`/`coarse`, default `coarse`; only sets provider preference) |
| `get_location_address` | Reverse-geocode coordinates to an address with the platform `Geocoder` | `latitude` (required, -90 to 90)<br>`longitude` (required, -180 to 180) |

`get_current_location` returns an error when no provider produces a fix within 10 s.

## Sensors

**Needs:** nothing. A missing sensor returns an error.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_accelerometer` | Acceleration x, y, z (m/s²) | `duration_ms` (1-5000) |
| `get_gyroscope` | Rotation x, y, z (rad/s) | `duration_ms` (1-5000) |
| `get_light_level` | Ambient light (lux) | `duration_ms` (1-5000) |
| `get_proximity` | Distance (cm) and `is_near` (under 5 cm) | `duration_ms` (1-5000) |

Without `duration_ms`, each tool takes a single reading; with it, the tool collects readings for that long.

## Health

**Needs:** `ACTIVITY_RECOGNITION` on Android 10+ (for `get_step_count`).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_step_count` | Steps since the last reboot, from the step counter sensor (not Health Connect) | - |
| `get_activity_info` | Available motion sensors and their specs | - |

## Apps

**Needs:** nothing. The module declares a `<queries>` entry for launcher activities, so on Android 11+ apps without a launcher activity are not visible.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `list_installed_apps` | Installed apps | `include_system` (default false)<br>`limit` (1-100, default 50) |
| `get_app_info` | Details for one app | `package_name` (required) |
| `launch_app` | Launch an app | `package_name` (required) |

## Alarms

**Needs:** `SET_ALARM` for alarms and timers. `create_reminder` is registered only when `READ_CALENDAR` and `WRITE_CALENDAR` are granted. `get_next_alarm` needs nothing.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `create_alarm` | Set an alarm in the clock app | `hour` (required, 0-23)<br>`minute` (required, 0-59)<br>`message`<br>`days` (comma-separated, e.g. `mon,wed`; empty means one-time) |
| `create_timer` | Start a countdown timer in the clock app | `seconds` (required, ≥ 1)<br>`message` |
| `create_reminder` | Calendar event with an alert | `title` (required)<br>`datetime` (required, YYYY-MM-DD HH:mm)<br>`minutes_before` (≥ 0, default 10) |
| `get_next_alarm` | Next scheduled alarm clock: `has_alarm`, `trigger_time` (ISO local), `trigger_millis`, `creator_package` | - |

Android has no API to list alarms. `get_next_alarm` reports only the soonest alarm registered with `AlarmManager.setAlarmClock`, normally the clock app's.

## Settings

**Needs:** nothing for `get_settings` and `set_volume`. `set_brightness` is registered only when `WRITE_SETTINGS` is granted, and `toggle_wifi` only with `CHANGE_WIFI_STATE`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_settings` | Brightness, volume, WiFi, Bluetooth, airplane mode, auto-rotate | - |
| `set_brightness` | Set screen brightness (switches to manual mode) | `level` (required, 0-255) |
| `set_volume` | Set one stream's volume | `stream` (`media`/`ring`/`alarm`/`notification`, default `media`)<br>`level` (required, 0 to the stream's max) |
| `toggle_wifi` | Turn WiFi on or off. On Android 10+ this opens the system WiFi panel instead. | `enabled` (required) |

## Do Not Disturb

**Needs:** nothing for `get_dnd_status`. `set_dnd_mode` needs DND access (Settings > Do Not Disturb access).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_dnd_status` | DND status and interruption filter | - |
| `set_dnd_mode` | Set the DND mode and report the resulting `actual_mode` | `mode` (required, `off`/`priority`/`alarms`/`none`) |

On Android 15+, `set_dnd_mode` controls only the app's own DND rule, which the system merges with the user's settings.

## Flashlight

**Needs:** a camera with a flash.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `toggle_flashlight` | Turn the torch on or off | `enabled` (required) |
| `set_flashlight_brightness` | Set torch strength (Android 13+). `level` is mapped onto the device's strength range; without variable strength the torch just turns on. | `level` (required, 0-255; 0 turns it off) |

## Vibration

**Needs:** `VIBRATE`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `vibrate` | Vibrate for a duration | `duration_ms` (required, 1-10000)<br>`amplitude` (1-255, default: system) |
| `vibrate_pattern` | Vibrate a pattern of alternating off/on durations | `timings` (required, array of ms; each ≤ 10000, total ≤ 30000, out-of-range values are rejected)<br>`repeat` (index to repeat from, default -1 = no repeat) |
| `cancel_vibration` | Stop vibration this app started | - |

A repeating pattern runs until `cancel_vibration` is called, or for at most 60 s.

## Wallpaper

**Needs:** `SET_WALLPAPER`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_wallpaper_info` | Wallpaper dimensions and whether a live wallpaper is active | - |
| `set_wallpaper` | Set the wallpaper from an image file. Returns an error when device policy disallows it. | `path` (required, sandboxed to external storage)<br>`target` (`home`/`lock`/`both`, default `both`) |

## Ringtone

**Needs:** nothing for the read tools. `set_ringtone` needs `WRITE_SETTINGS` (Settings > Modify system settings).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `list_ringtones` | Available sounds of a type | `type` (`ringtone`/`notification`/`alarm`, default `ringtone`)<br>`limit` (1-100, default 50) |
| `get_active_ringtone` | Current default sound of a type | `type` (`ringtone`/`notification`/`alarm`, default `ringtone`) |
| `set_ringtone` | Set the default sound of a type | `uri` (required, a `content://` URI or `silent`; other schemes are rejected)<br>`type` (`ringtone`/`notification`/`alarm`, default `ringtone`) |

## Text-to-Speech

**Needs:** nothing.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `speak_text` | Speak text aloud | `text` (required)<br>`language` (BCP-47, default `en`)<br>`pitch` (0.5-2.0, default 1.0)<br>`speed` (0.5-2.0, default 1.0) |
| `get_tts_info` | Default TTS engine and its languages | - |

## Print

**Needs:** an Activity for `print_content`. Pass one with `PrintTools.all(context) { currentActivity }`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `list_printers` | Installed print services and active print jobs | - |
| `print_content` | Open the system print dialog for text or HTML. Success means the dialog opened, not that anything printed; `job_id` may be null. | `content` (required)<br>`job_name` (default `droid-mcp print`)<br>`is_html` (default false; plain text is wrapped in HTML) |

## Intent and Share

**Needs:** nothing. All three tools return `background_activity_launch_blocked` when a background launch is blocked (see the note at the top).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `send_intent` | Fire an allowlisted intent | `action` (required, full action string: `android.intent.action.VIEW`/`DIAL`/`SEND`/`SENDTO`/`CHOOSER`/`SEARCH`/`WEB_SEARCH`/`EDIT`)<br>`data` (URI)<br>`type` (MIME type)<br>`package_name` (explicit target)<br>`extras` (object of string extras) |
| `share_content` | Share text through the share sheet | `text` (required)<br>`subject`<br>`type` (default `text/plain`) |
| `open_deep_link` | Open a URI with `ACTION_VIEW` | `uri` (required)<br>`package_name` |

Only the eight actions above are accepted, as full strings (for example `android.intent.action.DIAL`, not `DIAL`). Data URIs for `send_intent` and `open_deep_link` must use http, https, geo, tel, mailto, sms, smsto, mms, mmsto or market; any other scheme returns `uri_scheme_not_allowed`.

## Camera

**Needs:** `CAMERA`, plus `WRITE_EXTERNAL_STORAGE` on API 28. Photos are saved to `Pictures/droid-mcp` and videos to `Movies/droid-mcp`; `file_path` in the result is the MediaStore content URI.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `take_photo` | Take a photo without a preview, preferring the back camera. Saves a full-resolution JPEG to the gallery. With `return_data`, also returns a downscaled copy as an image content block. | `return_data` (default false)<br>`max_dimension` (64-4096, default 1280)<br>`format` (`jpeg`/`png`, default `jpeg`)<br>`quality` (1-100, default 85; JPEG only) |
| `capture_video` | Record an H.264 MP4 video without audio. The duration is counted from the first recorded frame; the call fails if the camera delivers no frame within 3 s. | `duration_sec` (1-60, default 10) |
| `get_camera_capabilities` | Cameras with facing, flash and maximum resolution | - |

`max_dimension`, `format` and `quality` apply only to the copy returned with `return_data`.

## Screenshot

**Needs:** user consent through MediaProjection. The host calls `MediaProjectionManager.createScreenCaptureIntent()` and passes the projection to `MediaProjectionHolder.set(projection)`. On Android 14+ it must also run a `mediaProjection` foreground service.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `capture_screen` | Screenshot saved to `Pictures/droid-mcp`. Returns the path and content URI, not the image itself. | `format` (`png`/`jpeg`, default `png`)<br>`quality` (1-100, default 90; JPEG only) |

On API 28 without `WRITE_EXTERNAL_STORAGE`, the image goes to the app cache (`saved_to = "cache"`). For a screenshot returned inline, use `take_screenshot_via_a11y` ([Accessibility](#accessibility)) or `capture_screen_quiet` ([Shell](#shell-shizuku-or-root)).

## QR and Barcode

**Needs:** nothing. Scanning uses ML Kit and generation uses ZXing.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `scan_qr_code` | Decode a QR code from an image | `image_uri` (required) |
| `scan_barcode` | Decode a 1D barcode (EAN-13, EAN-8, UPC-A, UPC-E, Code-128, Code-39) from an image. For QR codes use `scan_qr_code`. | `image_uri` (required) |
| `generate_qr_code` | Generate a QR code PNG, returned as an image content block | `text` (required)<br>`size` (100-1000, default 300) |

`image_uri` is a `file://` path on external storage or a `content://` URI. The host app's own content authorities are refused. Neither scan tool opens the camera.

## ML Kit

**Needs:** nothing. The tools use bundled on-device models; `image_path` is sandboxed to external storage.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `recognize_text` | Extract text from an image | `image_path` (required) |
| `label_image` | Label image contents | `image_path` (required)<br>`min_confidence` (0.0-1.0, default 0.5) |
| `detect_faces` | Face bounding boxes, smile and eye-open probabilities, head angles. Does not identify faces. | `image_path` (required) |

## Web

**Needs:** `INTERNET` (install-time).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `web_search` | Search DuckDuckGo; returns title, URL and snippet per result | `query` (required)<br>`limit` (1-50, default 5) |
| `fetch_webpage` | Fetch an http(s) URL and return its readable text. Reads at most 5 MB of the body. | `url` (required)<br>`max_length` (characters, ≥ 1, default 2000) |

By default both tools block loopback, private, link-local and similar addresses. The check runs before any connection is opened: hostnames are checked at DNS resolution and IP literals before the request. Redirects are followed manually (at most 5) so each hop is checked the same way, and a redirect to a non-http(s) URL is refused. `WebTools.all(context, allowPrivateNetwork = true)` turns the block off.

## Playback

> [!NOTE]
> Needs Notification access (Settings > Notification access). The host must register a `NotificationListenerService` and call `NotificationListenerHolder.set(componentName)`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `get_now_playing` | Currently playing media from active sessions | - |
| `media_control` | Send a playback command. Without `package_name`, targets the first active session. | `command` (required, `play`/`pause`/`stop`/`next`/`previous`)<br>`package_name` |

## Notification Reply

> [!NOTE]
> Needs Notification access. The host's listener service must extend `McpNotificationListenerServiceBase` (it fills the notification store all four tools read) and be registered with `NotificationListenerHolder.set(componentName)`.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `list_repliable_notifications` | Active notifications that have a free-form RemoteInput reply action | `limit` (1-100, default 20) |
| `reply_to_notification` | Reply through a notification's RemoteInput action. Success means the PendingIntent fired, not that the app delivered the message. | `key` (required)<br>`text` (required) |
| `dismiss_notification` | Cancel a notification by key | `key` (required) |
| `invoke_notification_action` | Trigger a non-reply action, such as Mark as read | `key` (required)<br>`action_label` (case-insensitive substring)<br>`action_index` (≥ 0)<br>Pass one of `action_label` or `action_index`. |

## Notification Watch

**Needs:** the same listener setup as [Notification Reply](#notification-reply).

| Tool | What it does | Parameters |
|------|--------------|------------|
| `watch_notifications` | Register a filter on newly posted notifications; returns a `watch_id` | At least one of:<br>`package_name` (exact)<br>`sender_pattern` (substring of title)<br>`keyword` (substring of text, big text, sub text or ticker)<br>Optional:<br>`ttl_seconds` (60-86400, default 3600)<br>`fire_on_update` (default false) |
| `unwatch_notifications` | Remove a watch. An unknown ID succeeds with `removed = false`. | `watch_id` (required) |
| `list_notification_watches` | Active watches with remaining TTL | - |
| `poll_notification_watch` | Notifications a watch matched, oldest first (up to 50 buffered per watch) | `watch_id` (required)<br>`clear` (default true) |

Filters combine with AND, and substring matches ignore case. A watch fires once per notification key unless `fire_on_update` is set, and sees only notifications posted after it was registered. At most 50 watches can be active.

Hosts can also collect `NotificationListenerBus.events` (`SharedFlow<NotificationEvent>`, in `droid-mcp-notification-listener`) directly. `NotificationEvent` fields:

- `key`, `packageName`
- `title`, `text`, `bigText`, `subText`, `tickerText`
- `category`, `channelId`, `groupKey`
- `isOngoing`, `isClearable`
- `legacyPriority`, `channelImportance` (`-1` if unknown)
- `postedAt`, `when`
- `hasReplyAction`, `actionLabels`

## Accessibility

> [!NOTE]
> The user must enable the host's accessibility service (Settings > Accessibility), which must extend `DroidMcpAccessibilityService`. Without it, tools return `accessibility_not_enabled` or a not-connected error.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `query_screen` | The active window's UI tree as a flat node list, ranked clickable > has text > scrollable > rest. Truncation keeps the top-ranked nodes. Password text is masked. | `max_nodes` (1-2000, default 500) |
| `find_node` | Nodes matching a selector | selector (see below)<br>`limit` (1-200, default 20) |
| `wait_for_text` | Wait until text appears or the window changes. Returns `status` `matched` or `timeout`; a timeout is not an error. | `condition` (`text`/`window_change`, default `text`)<br>`text` (required when `condition` is `text`)<br>`timeout_ms` (100-60000, default 5000)<br>`poll_ms` (50-2000, default 200) |
| `click_node` | `ACTION_CLICK` on the matched node, or on its nearest clickable ancestor if the node itself refuses (e.g. the row or tab around a label) | selector<br>`index` (≥ 0, default 0) |
| `long_click_node` | `ACTION_LONG_CLICK` on the matched node, or its nearest long-clickable ancestor | selector<br>`index` (≥ 0, default 0) |
| `set_node_text` | Replace an editable node's text (`ACTION_SET_TEXT`). A read-only node returns `node_not_editable`. | `text` (required, the new value)<br>selector, with `match_text` in place of `text`<br>`index` (≥ 0, default 0) |
| `scroll_node` | Scroll the matched node, or its nearest scrollable ancestor (so matching an item label scrolls its list) | selector<br>`index` (≥ 0, default 0)<br>`direction` (`forward`/`backward`, default `forward`) |
| `gesture` | Dispatch a stroke through a list of points | `points` (required, at least 2 `[x, y]` pairs)<br>`duration_ms` (10-3000, default 300) |
| `global_action` | Perform a system action | `action` (required, `back`/`home`/`recents`/`notifications`/`quick_settings`/`power_dialog`/`lock_screen`/`screenshot`) |
| `get_active_window_info` | Foreground package, root class, window ID | - |
| `take_screenshot_via_a11y` | Screenshot without a MediaProjection prompt, returned as an image content block (Android 11+) | `format` (`jpeg`/`png`, default `jpeg`)<br>`quality` (1-100, default 80; JPEG only)<br>`max_dimension` (64-4096, default 1280) |
| `tap` | Tap at screen coordinates | `x` (required, ≥ 0)<br>`y` (required, ≥ 0) |
| `long_press` | Long-press at screen coordinates | `x` (required, ≥ 0)<br>`y` (required, ≥ 0)<br>`duration_ms` (100-5000, default 800) |
| `find_and_tap` | Find a node and click it, or its nearest clickable ancestor. Returns `node_not_found` if nothing matches. | `match` (required)<br>`match_kind` (`text`/`desc`/`id`/`class`, default `text`)<br>`case_insensitive` (default true; substring kinds only) |
| `scroll_to_find` | Scroll until `match` appears. Each step asks the largest container that can move in `direction` to scroll itself (directional scroll action). If that fails, it swipes inside that container, or across the screen when none is found. After each scroll it re-checks for up to 1 s. | `match` (required, case-insensitive substring of text or description)<br>`direction` (`down`/`up`/`left`/`right`, default `down`; `down` reveals content below)<br>`max_scrolls` (1-20, default 5) |

**Selector.** `text` (case-insensitive substring of text or content description), `view_id`, `class_name`, `package_name` (exact matches). At least one is required; they combine with AND. `index` picks among multiple matches. Ancestor fallback climbs at most 8 levels.

`scroll_to_find` returns `found`, `scrolls` and the matched `node`, or `scroll_exhausted` after `max_scrolls` scrolls. `AccessibilityTools.supportedTools(context)` drops `take_screenshot_via_a11y` below Android 11.

## IME

> [!NOTE]
> The host's IME service must extend `DroidMcpInputMethodService`. The user must enable it in keyboard settings and select it in the IME picker.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `is_ime_active` | Whether the droid-mcp keyboard is active with an editor bound. Use it to gate the other tools. | - |
| `type_text` | Commit text at the cursor | `text` (required) |
| `commit_keystroke` | Send a named key | `key` (required, `enter`/`backspace`/`del`/`tab`/`escape`/`up`/`down`/`left`/`right`/`home`/`end`/`page_up`/`page_down`) |
| `delete_text` | Delete characters around the cursor. At least one count must be above 0. | `before` (0-2000, default 0)<br>`after` (0-2000, default 0) |
| `set_selection` | Move the cursor (equal `start` and `end`) or select a range | `start` (required, ≥ 0)<br>`end` (required, ≥ `start`) |
| `get_text_around_cursor` | Text before and after the cursor. Password fields are refused with `password_field`. | `before` (1-2000, default 200)<br>`after` (1-2000, default 200) |
| `switch_to_previous_ime` | Switch back to the previous keyboard | - |

The editing tools (`type_text`, `commit_keystroke`, `delete_text`, `set_selection`, `get_text_around_cursor`) return an error when no text field is focused, with a separate message when the droid-mcp keyboard isn't the active one. Focus a field first, for example with `find_and_tap`.

## Overlay

**Needs:** `SYSTEM_ALERT_WINDOW` (Settings > Display over other apps). No LLM tools: `droid-mcp-overlay` exposes `OverlayController` for a floating button drawn with `TYPE_APPLICATION_OVERLAY`.

```kotlin
val overlay = OverlayController(context)
if (!overlay.isPermissionGranted()) {
    startActivity(overlay.permissionIntent())  // ACTION_MANAGE_OVERLAY_PERMISSION
}
overlay.show(OverlayConfig(
    label = "Ask",
    onClick = { /* open chat */ },
    onLongPress = { /* quick voice */ },
    onDragEnd = { x, y -> /* persist position */ },
))
overlay.hide()
```

## Shell: Shizuku or root

`droid-mcp-shell-core` defines these 17 tools. `droid-mcp-shizuku` (`ShizukuTools.all(context)`) runs them as the shell UID through Shizuku; `droid-mcp-root` (`RootTools.all(context)`) runs them through libsu's `su` with the same names, parameters and output. Setup, backend choice and limits are in [SHELL.md](SHELL.md).

> [!WARNING]
> Register one backend, not both: the registry keeps the last tool registered under each name. Both providers take a `ShellPolicy`. The default, `ShellPolicy.RECOMMENDED`, refuses sensitive setting keys (accessibility services, notification listeners, input methods, ADB, developer options, package verifier) and grants of development permissions such as `WRITE_SECURE_SETTINGS`, returning `denied_by_policy`. `ShellPolicy.PERMISSIVE` denies nothing; use it only with a fully trusted model.

**Needs:** for Shizuku, the Shizuku service running and its permission granted to the host. For root, a rooted device whose superuser manager grants root to the host; `RootTools.requestAccess()` from an Activity triggers the prompt.

| Tool | What it does | Parameters |
|------|--------------|------------|
| `install_apk` | Silent `pm install` | `path` (required, absolute, ends in `.apk`, no `..`)<br>`replace` (default true, adds `-r`) |
| `uninstall_app` | Silent `pm uninstall` | `package_name` (required)<br>`keep_data` (default false; true adds `-k`) |
| `clear_app_data` | `pm clear`: wipe data and cache | `package_name` (required) |
| `force_stop_app` | `am force-stop` | `package_name` (required) |
| `disable_app` | `pm disable-user --user 0`. Reversible and idempotent. | `package_name` (required) |
| `enable_app` | `pm enable`. Idempotent. | `package_name` (required) |
| `grant_permission` | `pm grant` a permission the app declares. Idempotent. | `package_name` (required)<br>`permission` (required) |
| `revoke_permission` | `pm revoke`. Idempotent. Not affected by `ShellPolicy`. | `package_name` (required)<br>`permission` (required) |
| `list_app_permissions` | Requested and granted permissions, parsed from `dumpsys package` | `package_name` (required) |
| `put_secure_setting` | `settings put secure` | `key` (required)<br>`value` (required) |
| `put_global_setting` | `settings put global` | `key` (required)<br>`value` (required) |
| `put_system_setting` | `settings put system` | `key` (required)<br>`value` (required) |
| `get_top_window` | Foreground package and activity from `dumpsys window` | - |
| `set_app_standby_bucket` | `am set-standby-bucket`. Idempotent. | `package_name` (required)<br>`bucket` (required, `active`/`working_set`/`frequent`/`rare`/`restricted`) |
| `make_app_inactive` | `am set-inactive <pkg> true`. Idempotent. | `package_name` (required) |
| `capture_screen_quiet` | `screencap -p` with no consent prompt or indicator, returned as a PNG image content block | `display` (≥ 0, default 0) |
| `run_shell` | Run a command the host allowlisted with `ShellAllowlist.set(...)`. Returns `exit_code`, `stdout`, `stderr` and truncation flags. | `command` (required)<br>`args` (array; argv form, no shell splitting)<br>`max_stdout_bytes` (1024-65536, default 8192; also caps stderr) |

**Errors.** `shell_unavailable` means the backend can't be reached: Shizuku isn't running, or root hasn't been checked yet. `shell_permission_denied` means it is reachable but the host isn't allowed: the Shizuku permission isn't granted, or root was denied. Other codes: `denied_by_policy`, `run_shell_not_enabled` (command not allowlisted), `invalid_args`, `shell_spawn_failed`, and a per-verb failure such as `install_failed` or `settings_put_failed`.

**`run_shell` allowlist.** Entries match argv token by token. Entries that start with an interpreter or exec wrapper (`sh`, `su`, `toybox`, `env`, and similar) throw when set. In string form `command` is split on whitespace, so use `args` for anything with spaces or quotes. Root-only abilities (`pm hide`, `/system` writes, reading `/data/data/<pkg>`) are reachable only through `run_shell`.
