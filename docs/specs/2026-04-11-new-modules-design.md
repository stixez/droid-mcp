# New MCP Modules Design

**Date:** 2026-04-11
**Status:** Approved
**Modules:** 9 new tool modules for droid-mcp SDK

## Overview

Adding 9 new modules to expose additional Android capabilities via MCP. Modules are split into two batches: simple read-only APIs (Batch 1) and hardware/media APIs (Batch 2).

All modules follow the existing droid-mcp pattern:
- Independent modules depending only on `:droid-mcp-core`
- Provider object with `all(context)`, `requiredPermissions()`, `hasPermissions()`
- Each tool implements `McpTool` interface
- No fully qualified names inline

---

## Batch 1: Quick Wins

### droid-mcp-telephony

**Purpose:** Expose phone/SIM/network identity information.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `get_phone_number` | — | phone_number (String?), is_available (Boolean) |
| `get_sim_info` | — | sim_serial, carrier_name, country_iso, slot_index |
| `get_network_operator` | — | operator_name, operator_id, mcc, mnc |
| `get_call_state` | — | state (idle/ringing/active), phone_number |

**Permissions:**
- `READ_PHONE_STATE`
- `READ_SMS` (for phone number on some devices)

**Implementation:**
- Uses `TelephonyManager`
- Handle `null` returns (dual-SIM, restricted devices)
- Version-check `subscriptionId` for API 31+

---

### droid-mcp-vibration

**Purpose:** Provide haptic feedback capabilities.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `vibrate` | duration_ms (Int, 1-10000), amplitude (Int?) | success (Boolean) |
| `vibrate_pattern` | timings (List<Int>), repeat (Int?) | success (Boolean) |

**Permissions:**
- `VIBRATE`

**Implementation:**
- Uses `Vibrator` system service
- `amplitude` uses `Vibrator.createPredefined()` on API 26+
- Pattern: ON/OFF milliseconds in `timings` array
- `repeat: -1` for no repeat, `0` to restart from beginning

---

### droid-mcp-flashlight

**Purpose:** Control device flash/torch.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `toggle_flashlight` | — | is_on (Boolean) |
| `set_flashlight_brightness` | level (Int 0-255) | current_level (Int) |

**Permissions:**
- `FLASHLIGHT` (API 34+)
- `CAMERA` (implicit on older APIs)

**Implementation:**
- Uses `CameraManager.setTorchMode()`
- Brightness via `captureRequestTemplate` on API 33+
- Handle multiple cameras (use first available flash unit)

---

### droid-mcp-biometric

**Purpose:** Query biometric hardware and enrollment status (read-only).

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `check_biometric_availability` | — | can_authenticate (Boolean), hardware_type |
| `get_biometric_enrollments` | — | enrolled_count, has_fingerprint, has_face |

**Permissions:**
- None (read-only queries)

**Implementation:**
- Uses `BiometricManager` for API 30+
- Falls back to `FingerprintManager` for API 28-29
- Returns hardware type: none, fingerprint, face, iris

---

### droid-mcp-network

**Purpose:** Monitor network usage, signal strength, and VPN status.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `get_data_usage` | days (Int, default 30) | bytes_tx, bytes_rx, packet_tx, packet_rx |
| `get_cellular_signal` | — | signal_asu, signal_dbm, level (none/poor/good/excellent) |
| `is_vpn_active` | — | is_active, vpn_package_name |

**Permissions:**
- `PACKAGE_USAGE_STATS` (data usage)
- `ACCESS_NETWORK_STATE`

**Implementation:**
- Data usage: `NetworkStatsManager.querySummaryForDevice()`
- Signal: `TelephonyManager.getCellSignalStrengths()`
- VPN: `ConnectivityManager.getNetworkCapabilities().TRANSPORT_VPN`

---

## Batch 2: Complex

### droid-mcp-sensors

**Purpose:** Read device sensors for orientation, motion, and environment detection.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `get_accelerometer` | duration_ms (Int?) | x, y, z, accuracy, timestamp, readings[] |
| `get_gyroscope` | duration_ms (Int?) | x, y, z, accuracy, timestamp, readings[] |
| `get_light_level` | duration_ms (Int?) | lux, accuracy, timestamp, readings[] |
| `get_proximity` | duration_ms (Int?) | distance_cm, is_near, accuracy, timestamp, readings[] |

**Permissions:**
- None (standard sensor APIs)

**Implementation:**
- Uses `SensorManager` with `SensorEventListener`
- Single reading by default (`duration_ms` null)
- Batch readings: collect samples for `duration_ms`, return array
- Clamp `duration_ms` to 1-5000ms
- Handle sensors not present on device

---

### droid-mcp-qr

**Purpose:** Scan and generate QR codes and barcodes.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `scan_qr_code` | — | raw_value, format, timeout (Boolean) |
| `scan_barcode` | — | raw_value, format, timeout (Boolean) |
| `generate_qr_code` | text (String), size (Int) | qr_image (base64 PNG) |

**Permissions:**
- `CAMERA` (for scanning)

**Dependencies:**
- `com.google.mlkit:barcode-scanning:17.3.0`

**Implementation:**
- Scanning: Requires camera preview UI in sample app
- Module provides `scan_qr_code(context, imageUri)` for image-based scanning
- Generate: Uses `zxing` QRCodeWriter or similar lightweight library
- Returns base64-encoded PNG for `generate_qr_code`

---

### droid-mcp-camera

**Purpose:** Capture photos and videos via device camera.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `take_photo` | return_data (Boolean), output_uri (String?) | file_path, image_data (base64?), width, height |
| `capture_video` | duration_sec (Int?), return_data (Boolean) | file_path, video_data (base64?), duration_ms |
| `get_camera_capabilities` | — | cameras[], max_resolution, flash_available, fps_range |

**Permissions:**
- `CAMERA`
- `WRITE_EXTERNAL_STORAGE` (API 28-32)

**Dependencies:**
- CameraX core, camera2, lifecycle, view:
  - `androidx.camera:camera-camera2:1.4.1`
  - `androidx.camera:camera-lifecycle:1.4.1`
  - `androidx.camera:camera-video:1.4.1`

**Implementation:**
- Use CameraX `ImageCapture` and `VideoCapture`
- Default: save to `Environment.DIRECTORY_PICTURES/DCIM`
- `return_data=true`: encode output as base64
- File size limit for base64: warn if >10MB

---

### droid-mcp-audio

**Purpose:** Enumerate connected audio devices.

**Tools:**
| Tool | Params | Returns |
|------|--------|---------|
| `get_audio_devices` | — | devices: [{type, name, id, is_output}] |

**Permissions:**
- None

**Implementation:**
- Uses `AudioManager.getDevices()`
- Returns device type: speaker, headphone, bluetooth, usb, hdmi, etc.
- Distinguishes output vs input devices
- Useful for troubleshooting audio routing issues

---

## File Changes Required

### Gradle Configuration
- `settings.gradle.kts`: Add 9 new module includes
- `gradle/libs.versions.toml`: Add ML Kit and CameraX versions
- `droid-mcp-all/build.gradle.kts`: Add `api()` deps for all 9 modules

### New Files (per module)
- `droid-mcp-{name}/build.gradle.kts`
- `droid-mcp-{name}/src/main/AndroidManifest.xml`
- `droid-mcp-{name}/src/main/kotlin/io/droidmcp/{name}/{Name}Tools.kt`
- `droid-mcp-{name}/src/main/kotlin/io/droidmcp/{name}/*Tool.kt` files

### Sample App Updates
- Update ViewModel to register new tools
- Add permission requests for new modules
- QR scanning: add camera preview UI
- Camera capture: add preview UI

### Documentation
- Update README with new modules
- Update CLAUDE.md quick reference

---

## Modules NOT Implemented

| Dropped | Reason |
|---------|--------|
| `droid-mcp-telephony-ne` (MMS) | Carrier Services dependency, fragmented API, RCS replaces MMS |
| `droid-mcp-audio-recording` | Limited utility without transcription, adds complexity |

---

## Success Criteria

1. All 9 modules build successfully with `./gradlew assembleDebug`
2. Each module's tools execute without crash on device/emulator
3. Permissions properly declared per module
4. Sample app demonstrates all new tools
5. README and CLAUDE.md updated

---

## Implementation Order

**Batch 1 (quick wins):**
1. telephony
2. vibration
3. flashlight
4. biometric
5. network

**Batch 2 (complex):**
6. sensors
7. qr
8. camera
9. audio
