# R1 Xiaozhi - ESP32 Pairing Implementation

## 📖 Overview

Android app integrating the Xiaozhi voice assistant for the Phicomm R1 speaker, implemented according to the [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32) standard.

### Key Features
- ✅ **Local code generation** - No API server required
- ✅ **WebSocket + Authorize handshake** - Simple protocol
- ✅ **Callback-driven** - No polling
- ✅ **Offline-capable** - Code generation works even without a network connection
- ✅ **Android 5.1+ compatible** - Supports Phicomm R1 (API 22)

## 🚀 Quick Start

### 1. Build APK
```bash
cd R1XiaozhiApp
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Install on R1
```bash
# Connect R1 via ADB
adb connect <R1_IP>:5555

# Install
adb install -r app-debug.apk

# Launch
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

### 3. Pairing with Xiaozhi Cloud

**Step 1**: Open the app, you will see a pairing code (6 characters)
```
Pairing code: DD EE FF
```

**Step 2**: Go to https://console.xiaozhi.ai

**Step 3**: Enter the code `DDEEFF` in the console

**Step 4**: Press "Connect" in the app

**Step 5**: The app will automatically confirm and display "✓ Paired successfully!"

Done! You can now use voice commands.

## 🔧 Architecture

### Pairing Flow
```
┌─────────────┐
│   User      │
│ Opens App   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────┐
│ PairingCodeGenerator        │
│ - Get MAC address           │
│ - deviceId = MAC without :  │
│ - code = last 6 chars       │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Display Code: DD EE FF      │
└─────────────────────────────┘
       │
       │ User enters code
       │ in console.xiaozhi.ai
       │
       ▼
┌─────────────────────────────┐
│ User clicks "Connect"       │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ XiaozhiConnectionService    │
│ wss://xiaozhi.me/v1/ws      │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Send Authorize Handshake    │
│ {                           │
│   "header": {               │
│     "name": "Authorize",    │
│     "namespace": "..."      │
│   },                        │
│   "payload": {              │
│     "device_id": "...",     │
│     "pairing_code": "...",  │
│     "device_type": "..."    │
│   }                         │
│ }                           │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Receive Response            │
│ {"payload": {"code": "0"}}  │
└──────┬──────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Mark as Paired              │
│ Show "Success!"             │
└─────────────────────────────┘
```

### Components

#### 1. PairingCodeGenerator
```java
// Get device ID (MAC address)
String deviceId = PairingCodeGenerator.getDeviceId(context);
// -> "AABBCCDDEEFF"

// Get pairing code (last 6 chars)
String code = PairingCodeGenerator.getPairingCode(context);
// -> "DDEEFF"

// Check/update pairing status
boolean paired = PairingCodeGenerator.isPaired(context);
PairingCodeGenerator.markAsPaired(context);
PairingCodeGenerator.resetPairing(context);
```

#### 2. XiaozhiConnectionService
```java
// Setup listener
service.setConnectionListener(new ConnectionListener() {
    @Override
    public void onPairingSuccess() {
        // Handle success
    }
    
    @Override
    public void onPairingFailed(String error) {
        // Handle error
    }
});

// Connect (auto sends Authorize handshake)
service.connect();

// Send text after paired
service.sendTextMessage("今天天气怎么样");
```

#### 3. HTTPServerService
REST API server running on port 8080:

```bash
# Get pairing code
curl http://localhost:8080/pairing-code
# Response:
# {
#   "device_id": "AABBCCDDEEFF",
#   "pairing_code": "DDEEFF",
#   "paired": false
# }

# Get status
curl http://localhost:8080/status
# Response:
# {
#   "paired": true,
#   "device_id": "AABBCCDDEEFF",
#   "status": "paired"
# }

# Reset pairing
curl -X POST http://localhost:8080/reset
# Response:
# {
#   "success": true,
#   "message": "Pairing reset successfully"
# }
```

## 🔍 Debugging

### Enable logs
```bash
adb logcat | grep -E "PairingCode|XiaozhiConnection|MainActivity"
```

### Expected logs on successful pairing:
```
I/PairingCode: Generated device ID: AABBCCDDEEFF
I/PairingCode: Pairing code: DDEEFF (from device ID: AABBCCDDEEFF)
I/XiaozhiConnection: Connecting to: wss://xiaozhi.me/v1/ws
I/XiaozhiConnection: WebSocket connected
I/XiaozhiConnection: Sending Authorize handshake: {"header":{...},"payload":{...}}
I/XiaozhiConnection: Pairing SUCCESS!
I/PairingCode: Device marked as paired
I/MainActivity: Status: ✓ Paired successfully!
```

### Common issues

**1. Code does not match**
```
E/XiaozhiConnection: Pairing FAILED: code=1001, message=invalid_code
```
→ Check whether the device ID is correct:
```bash
adb logcat | grep "Device ID:"
```

**2. WebSocket connection failed**
```
E/XiaozhiConnection: WebSocket error: Connection refused
```
→ Check network connectivity:
```bash
adb shell ping xiaozhi.me
```

**3. App crash on start**
```
E/AndroidRuntime: FATAL EXCEPTION: main
```
→ Check permissions in logcat:
```bash
adb logcat | grep "Permission denied"
```

## 📦 Project Structure

```
R1XiaozhiApp/
├── app/src/main/
│   ├── java/com/phicomm/r1/xiaozhi/
│   │   ├── config/
│   │   │   └── XiaozhiConfig.java          # Constants
│   │   ├── service/
│   │   │   ├── VoiceRecognitionService.java # Voice input
│   │   │   ├── XiaozhiConnectionService.java # WebSocket + Authorize
│   │   │   ├── AudioPlaybackService.java    # TTS output
│   │   │   ├── LEDControlService.java       # LED effects
│   │   │   └── HTTPServerService.java       # REST API
│   │   ├── ui/
│   │   │   └── MainActivity.java            # Main UI
│   │   └── util/
│   │       └── PairingCodeGenerator.java    # LOCAL code gen
│   ├── res/
│   │   └── layout/
│   │       └── activity_main.xml            # UI layout
│   └── AndroidManifest.xml
├── build.gradle
└── gradle.properties
```

## 🆚 Comparison: Old vs New

### Old (Wrong) Approach
```
User opens app
    ↓
Call API: POST /register {mac_address}
    ↓
Receive: {code: "XXXXXX", token: "..."}
    ↓
Display code
    ↓
Poll API: GET /status?token=... (every 3s)
    ↓
When paired: status=active
    ↓
Connect: wss://xiaozhi.me/v1/ws?token=...
```

**Problems**:
- ❌ Server-side code generation
- ❌ Multiple API calls
- ❌ Inefficient polling
- ❌ Token-based auth does not match the protocol
- ❌ Does not match ESP32 implementation

### New (ESP32) Approach
```
User opens app
    ↓
Generate code LOCAL: deviceId.substring(6)
    ↓
Display code
    ↓
User clicks "Connect"
    ↓
Connect: wss://xiaozhi.me/v1/ws
    ↓
Send Authorize handshake with device_id + pairing_code
    ↓
Receive response: code="0" → Success!
```

**Advantages**:
- ✅ Zero API calls
- ✅ Callback-driven (no polling)
- ✅ Matches ESP32 100%
- ✅ Simpler and faster
- ✅ Works offline

## 📚 References

### Official
- Xiaozhi ESP32: https://github.com/78/xiaozhi-esp32
- Xiaozhi Console: https://console.xiaozhi.ai

### Documentation
- [ESP32_CODE_ANALYSIS.md](./ESP32_CODE_ANALYSIS.md) - Detailed protocol analysis
- [PAIRING_FIX_SUMMARY.md](./PAIRING_FIX_SUMMARY.md) - Before/after comparison
- [ESP32_PAIRING_RESEARCH.md](./ESP32_PAIRING_RESEARCH.md) - Research notes
- [INSTALLATION_GUIDE.md](../docs/INSTALLATION_GUIDE.md) - Detailed installation guide

## 🤝 Contributing

When modifying pairing logic, ALWAYS refer to the ESP32 implementation:
- https://github.com/78/xiaozhi-esp32/blob/master/main/xiaozhi.c

Key functions to review:
- `xiaozhi_device_id_get()` - Device ID generation
- `xiaozhi_authorize()` - Authorize handshake
- `on_xiaozhi_message()` - Message handling

## 📄 License

MIT License - Free to use and modify

## 🙏 Credits

- Xiaozhi Protocol: https://github.com/78
- Phicomm R1 Community
- ESP32 Reference Implementation
