# Activation Flow Implementation - Completed

## Problems Resolved

### Previous Issues
The Android code did **NOT** receive the 6-digit code from the server because:
1. Self-generating the challenge instead of receiving it from the server
2. Missing the OTA Config step to retrieve activation data
3. Flow did not match py-xiaozhi

### New Solution
Implemented the correct flow following py-xiaozhi:
1. **GET OTA Config** → Receive challenge + code from server
2. **Display code** to user
3. **POST Activation** with server challenge
4. **Poll** until user enters the code on the website

---

## Files Created/Updated

### 1. ✅ OTAConfigManager.java (NEW)
**Path**: `R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/activation/OTAConfigManager.java`

**Purpose**:
- Fetch OTA configuration from server
- Parse activation data (challenge + code)
- Parse WebSocket config
- Based on `py-xiaozhi/src/core/ota.py`

**Key Methods**:
```java
public void fetchOTAConfig(OTACallback callback)
private OTAResponse performOTARequest()
private JSONObject buildPayload(String deviceId)
private OTAResponse parseOTAResponse(String jsonString)
```

**Response Structure**:
```java
OTAResponse {
    WebSocketConfig websocket;
    ActivationData activation {
        String challenge;  // From server!
        String code;       // 6-digit code from server!
        String url;
        int timeout;
    }
}
```

### 2. ✅ DeviceActivator.java (UPDATED)
**Path**: `R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/activation/DeviceActivator.java`

**Key Changes**:

#### Before (INCORRECT):
```java
// Self-generating challenge - WRONG!
String challenge = "xiaozhi-activation-" + System.currentTimeMillis();
String hmac = fingerprint.generateHmac(challenge);
```

#### After (CORRECT):
```java
// STEP 1: Fetch OTA config
otaManager.fetchOTAConfig(callback);

// STEP 2: Parse activation data from server
serverChallenge = response.activation.challenge;  // From server!
verificationCode = response.activation.code;      // From server!

// STEP 3: Display code to user
notifyVerificationCode(verificationCode);

// STEP 4: Poll with server challenge
String hmac = fingerprint.generateHmac(serverChallenge);
```

**New Fields**:
```java
private String serverChallenge;    // Challenge from server
private String verificationCode;   // 6-digit code from server
private final OTAConfigManager otaManager;
```

**New Methods**:
```java
private void handleOTAResponse(OTAResponse response)
private void performActivationPolling()
```

### 3. ✅ MainActivity.java (ALREADY PRESENT)
**Path**: `R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java`

**UI Components** (already present):
- `activationCodeText` - Displays activation code
- `activationProgressText` - Displays progress
- `cancelActivationButton` - Cancels activation

**Methods**:
- `showActivationCode(String code)` - Displays server code
- `updateActivationProgress(int, int)` - Updates progress
- `copyActivationCode()` - Copies code to clipboard

---

## Complete Flow

```
┌──────────────────────────────────────────────────────────┐
│ 1. User Clicks "Connect"                                 │
│    → MainActivity.connectToXiaozhi()                     │
│    → XiaozhiConnectionService.connect()                  │
│    → DeviceActivator.startActivation()                   │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 2. Fetch OTA Config                                      │
│    → OTAConfigManager.fetchOTAConfig()                   │
│    → GET https://api.tenclass.net/xiaozhi/ota/version   │
│    → Headers: Device-Id, Client-Id, Activation-Version  │
│    → Body: {application: {...}, board: {...}}           │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 3. Server Response                                       │
│    {                                                      │
│      "websocket": {...},                                 │
│      "activation": {                                     │
│        "challenge": "abc123xyz...",  ← FROM SERVER!     │
│        "code": "123456",             ← FROM SERVER!     │
│        "url": "https://xiaozhi.me/activate",            │
│        "timeout": 300                                    │
│      }                                                    │
│    }                                                      │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 4. Parse & Display Code                                  │
│    → DeviceActivator.handleOTAResponse()                 │
│    → serverChallenge = response.activation.challenge     │
│    → verificationCode = response.activation.code         │
│    → notifyVerificationCode(verificationCode)            │
│    → MainActivity.showActivationCode("123456")           │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 5. User Enters Code on Website                           │
│    → Visit: https://xiaozhi.me/activate                 │
│    → Enter code: 123456                                  │
│    → Server validates and marks device activated          │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│ 6. Android Polls Activation API                          │
│    → DeviceActivator.performActivationPolling()          │
│    → LOOP: POST /activate with server challenge         │
│    → Body: {                                             │
│        "sn": "SN-...",                                   │
│        "mac": "aabbccddee",                              │
│        "challenge": "abc123xyz",  ← FROM SERVER!        │
│        "signature": hmac(challenge),                     │
│        "client_id": "..."                                │
│      }                                                    │
└──────────────────────────────────────────────────────────┘
                        ↓
            ┌───────────┴───────────┐
            │                       │
            ▼                       ▼
  ┌──────────────────┐    ┌──────────────────┐
  │ HTTP 202         │    │ HTTP 200         │
  │ Waiting...       │    │ Success!         │
  │ → Sleep 5s       │    │ → Get token      │
  │ → Retry          │    │ → Save token     │
  └──────────────────┘    │ → Mark activated │
            ↑             │ → Connect WS     │
            │             └──────────────────┘
            │                       │
            └───────────────────────┘
                  (Max 60 retries)
```

---

## Before/After Comparison

| Aspect | Before (INCORRECT) | After (CORRECT) |
|--------|--------------|--------------|
| **OTA Config** | ❌ Not present | ✅ OTAConfigManager |
| **Challenge** | ❌ Self-generated `xiaozhi-activation-{timestamp}` | ✅ Received from server |
| **Verification Code** | ❌ Not displayed | ✅ Displayed from server response |
| **Flow** | ❌ POST immediately without challenge | ✅ GET OTA → Parse → POST with server challenge |
| **HMAC** | ❌ HMAC of self-generated challenge | ✅ HMAC of server challenge |
| **Token Storage** | ❌ Not saved | ✅ Save access_token |

---

## Code Comparison

### Challenge Generation

#### Before ❌:
```java
// DeviceActivator.java - Line 167
String challenge = "xiaozhi-activation-" + System.currentTimeMillis();
String hmac = fingerprint.generateHmac(challenge);
// POST /activate with self-generated challenge → Server rejects!
```

#### After ✅:
```java
// Step 1: Get challenge from server
otaManager.fetchOTAConfig(new OTACallback() {
    @Override
    public void onSuccess(OTAResponse response) {
        if (response.activation != null) {
            // Challenge from server!
            serverChallenge = response.activation.challenge;
            verificationCode = response.activation.code;

            // Display code to user
            notifyVerificationCode(verificationCode);

            // Polling with server challenge
            performActivationPolling();
        }
    }
});

// Step 2: Use server challenge
String hmac = fingerprint.generateHmac(serverChallenge);
// POST /activate with server challenge → Server accepts!
```

---

## API Endpoints

### 1. OTA Config API
```
GET https://api.tenclass.net/xiaozhi/ota/version?device_id={mac}&client_id={id}

Headers:
- Content-Type: application/json
- Device-Id: {mac_address}
- Client-Id: {uuid}
- Activation-Version: 1.0.0
- User-Agent: android/xiaozhi-android-1.0.0
- Accept-Language: zh-CN

Body:
{
  "application": {
    "version": "1.0.0",
    "elf_sha256": "{hmac_key}"
  },
  "board": {
    "type": "android",
    "name": "xiaozhi-android",
    "ip": "192.168.1.100",
    "mac": "{mac_address}"
  }
}

Response (Device NOT activated):
{
  "websocket": {
    "url": "wss://xiaozhi.me/v1/ws",
    "token": null,
    "protocol": "v1"
  },
  "activation": {
    "challenge": "server-generated-challenge-abc123",
    "code": "123456",
    "url": "https://xiaozhi.me/activate",
    "timeout": 300
  }
}

Response (Device activated):
{
  "websocket": {
    "url": "wss://xiaozhi.me/v1/ws",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "protocol": "v1"
  }
  // NO "activation" field
}
```

### 2. Activation API
```
POST https://api.tenclass.net/xiaozhi/ota/activate

Headers:
- Content-Type: application/json
- Device-Id: {mac_address}
- Client-Id: {uuid}
- Activation-Version: 2

Body:
{
  "Payload": {
    "algorithm": "hmac-sha256",
    "serial_number": "SN-XXXX-aabbccddee",
    "challenge": "{challenge_from_server}",  ← From OTA response!
    "hmac": "{hmac_sha256(challenge)}"
  }
}

Response 202 (Waiting):
{
  "code": "123456",
  "message": "Please enter verification code"
}

Response 200 (Success):
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 86400
}
```

---

## Testing Guide

### 1. Check OTA Config
```bash
# Check OTA response
adb logcat | grep "OTAConfigManager"

# Should see:
# I/OTAConfigManager: Fetching OTA config from: https://...
# I/OTAConfigManager: WebSocket config received: wss://...
# I/OTAConfigManager: Activation data received - Device needs activation
# I/OTAConfigManager: Verification code: 123456
```

### 2. Check Activation Flow
```bash
# Check activation process
adb logcat | grep "DeviceActivator"

# Should see:
# I/DeviceActivator: Starting activation - Fetching OTA config...
# I/DeviceActivator: Activation required - Challenge received from server
# I/DeviceActivator: Verification code: 123456
# I/DeviceActivator: Starting activation polling with server challenge
# I/DeviceActivator: Waiting for user to enter verification code on website...
# ...
# I/DeviceActivator: Activation successful!
```

### 3. Check UI
```bash
# Should see on screen:
# - Activation code: 123456
# - Visit: https://xiaozhi.me/activate
# - Enter code: 123456
# - Copy button visible
# - Progress: Checking... (1/60)
```

---

## Next Steps

### To Test:
1. ✅ Code is complete
2. ✅ Flow matches py-xiaozhi
3. ⏳ Requires server supporting OTA API endpoint
4. ⏳ Test with real server

### To Deploy:
1. Build APK
2. Install on device
3. Test activation flow
4. Verify WebSocket connection after activation

---

## References

### py-xiaozhi Source Files
- `src/core/system_initializer.py` (line 161-209) - OTA config flow
- `src/core/ota.py` (line 120-249) - OTA implementation
- `src/activation/device_activator.py` (line 102-341) - Activation logic
- `src/utils/device_fingerprint.py` - Device identity

### Android Files
- `OTAConfigManager.java` - OTA config fetching (NEW)
- `DeviceActivator.java` - Activation with OTA flow (UPDATED)
- `DeviceFingerprint.java` - Device identity (existing)
- `MainActivity.java` - UI display (existing)

---

## Checklist

- [x] Create OTAConfigManager.java
- [x] Update DeviceActivator.java - add OTA step
- [x] Update DeviceActivator.java - use server challenge
- [x] Verify MainActivity.java has UI ready
- [x] Create document ACTIVATION_FLOW_FIX.md
- [x] Create document ACTIVATION_FLOW_IMPLEMENTATION.md
- [ ] Test with real server
- [ ] Push code to GitHub
- [ ] Update README with activation instructions

---

## Conclusion

**Android code has been updated to fully match the py-xiaozhi activation flow!**

Key improvements:
1. ✅ Added OTA Config step
2. ✅ Receive challenge + code from server
3. ✅ Display code to user
4. ✅ Poll with server challenge (no self-generation)
5. ✅ Save access_token on success

**Ready to test with real server!** 🚀
