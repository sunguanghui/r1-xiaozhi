# Xiaozhi Authentication Methods - Comparison of 3 Methods

## 📋 Overview

The Xiaozhi server supports **3 different authentication methods** depending on the client type and setup.

---

## 🔐 Method 1: ESP32 Authorize Handshake

### Source

* Repository: [https://github.com/78/xiaozhi-esp32](https://github.com/78/xiaozhi-esp32)
* File: `main/xiaozhi.c`

### Flow

```text
1. Connect WebSocket: wss://xiaozhi.me/v1/ws
2. Send Authorize handshake
3. Wait for response
4. Ready to use

```

### Message Format

```json
{
  "header": {
    "name": "Authorize",
    "namespace": "ai.xiaoai.authorize",
    "message_id": "uuid"
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "esp32",
    "os_version": "1.0.0",
    "app_version": "1.0.0",
    "brand": "ESP32",
    "model": "WROVER"
  }
}

```

### Pairing Flow

```text
1. User goes to xiaozhi.me/console
2. Add device, enter pairing code: DDEEFF
3. Server saves mapping: code → waiting
4. ESP32 connects and sends handshake
5. Server matches code → Success

```

### ✅ Advantages

* Simple, does not require an API.
* Generates the code locally from the MAC address.
* Suitable for embedded devices.

### ❌ Disadvantages

* The user must enter the code manually.
* The code can expire.
* Lacks session management.

---

## 🔐 Method 2: py-xiaozhi Device Activation

### Source

* Local repository: F:/PHICOMM_R1/xiaozhi/py-xiaozhi
* Files: `device_activator.py`, `device_fingerprint.py`, `websocket_protocol.py`

### Flow

```text
1. Generate serial number from MAC
2. Register device (POST /activate with HMAC challenge)
3. Wait for user to enter verification code
4. Poll until activated
5. Get access token
6. Connect WebSocket with token in headers
7. Send hello message
8. Ready to use

```

### Device Registration

```python
# Generate identity
serial_number = "SN-HASH-MAC"
hmac_key = hash(hardware_info)

# Activate
POST https://api.tenclass.net/xiaozhi/ota/activate
Headers:
  - Activation-Version: 2
  - Device-Id: MAC_ADDRESS
  - Client-Id: UUID
  - Content-Type: application/json

Body:
{
  "Payload": {
    "algorithm": "hmac-sha256",
    "serial_number": "SN-...",
    "challenge": "server_challenge",
    "hmac": "calculated_hmac"
  }
}

```

### WebSocket Connection

```python
# Connect with headers
ws = websockets.connect(
    uri="wss://xiaozhi.me/v1/ws",
    additional_headers={
        "Authorization": "Bearer {access_token}",
        "Protocol-Version": "1",
        "Device-Id": "{mac_address}",
        "Client-Id": "{uuid}"
    }
)

# Send hello (NOT Authorize)
{
  "type": "hello",
  "version": 1,
  "features": {"mcp": true},
  "transport": "websocket",
  "audio_params": {
    "format": "opus",
    "sample_rate": 16000,
    "channels": 1,
    "frame_duration": 20
  }
}

```

### ✅ Advantages

* Secure through the use of HMAC.
* Features session and token management.
* Includes voice announcement for the verification code.
* Built-in retry logic utilizing polling.

### ❌ Disadvantages

* More complex to implement.
* Requires specific API endpoints.
* Requires storage for credentials.

---

## 🔐 Method 3: Self-hosted Simple Auth

### Flow

```text
1. Connect to self-hosted server
2. May not need authentication
3. Or use simple token

```

---

## 🎯 R1 Android Current Implementation

### Currently Using: ESP32 Method (Modified)

```java
// XiaozhiConnectionService.java
POST ws://xiaozhi.me/v1/ws

// Send Authorize
{
  "header": {...},
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "android",      // ✓ Fixed
    "os_version": "5.1.1",          // ✓ Added
    "app_version": "1.0.0",         // ✓ Added
    "brand": "Phicomm",             // ✓ Added
    "model": "R1"                   // ✓ Added
  }
}

```

---

## 🔍 Current Issues

### Symptom

"The 6-digit code does not work" - the pairing code fails to function.

### Possible Causes

#### 1. The Server Requires the py-xiaozhi Method

The `xiaozhi.me` server may have been upgraded and now only accepts:

* The device activation flow.
* The Authorization header with a bearer token.
* It **NO LONGER accepts** the ESP32 Authorize handshake.

#### 2. Missing Mandatory Fields

If the server still supports the ESP32 method, it might be missing:

* The `client_id` in the payload?
* Incorrect headers?

#### 3. Server Configuration

* The server has multiple authentication modes.
* Need to verify the endpoint and version.

---

## ✅ Proposed Solutions

### Option A: Keep the ESP32 Method (Quick Fix)

**If the server still supports the ESP32 method:**

1. ✅ Format fixed (device_type, added fields).
2. ❓ Try adding `client_id` to the payload:

```java
payload.put("client_id", XiaozhiConfig.CLIENT_ID); // "1000013"

```

3. ❓ Check if all necessary headers are present:

```java
// WebSocket headers
headers.put("Protocol-Version", "1");
headers.put("Device-Id", deviceId);
headers.put("Client-Id", clientId);

```

### Option B: Implement the py-xiaozhi Method (Recommended)

**Full implementation mirroring py-xiaozhi:**

#### Step 1: Create DeviceActivator

```java
public class DeviceActivator {
    public ActivationResult activate(Context context) {
        // 1. Generate serial number
        // 2. POST /activate
        // 3. Display verification code
        // 4. Poll until activated
        // 5. Save access token
        // 6. Return token
    }
}

```

#### Step 2: Update XiaozhiConnectionService

```java
public void connect() {
    // 1. Check if activated
    if (!isActivated()) {
        // Trigger activation flow
        startActivation();
        return;
    }
    
    // 2. Get access token
    String token = getAccessToken();
    
    // 3. Connect with headers
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.put("Protocol-Version", "1");
    headers.put("Device-Id", deviceId);
    headers.put("Client-Id", clientId);
    
    webSocket.connect(url, headers);
    
    // 4. Send hello (NOT Authorize)
    sendHello();
}

private void sendHello() {
    JSONObject hello = new JSONObject();
    hello.put("type", "hello");
    hello.put("version", 1);
    hello.put("features", new JSONObject().put("mcp", true));
    hello.put("transport", "websocket");
    // ... audio_params
    
    webSocket.send(hello.toString());
}

```

### Option C: Support Both Methods

```java
public enum AuthMethod {
    ESP32_AUTHORIZE,
    PY_XIAOZHI_ACTIVATION
}

public void connect(AuthMethod method) {
    switch (method) {
        case ESP32_AUTHORIZE:
            connectWithAuthorize();
            break;
        case PY_XIAOZHI_ACTIVATION:
            connectWithActivation();
            break;
    }
}

```

---

## 🧪 Testing Strategy

### 1. Test Current ESP32 Method

```bash
# Check if Authorize still works
adb logcat | grep "Authorize"

```

Expected behavior:

* ✅ Sends Authorize handshake.
* ✅ Receives response with code="0".
* ❌ Receives error "Invalid pairing code" or "Method not supported".

### 2. Test py-xiaozhi Method

Implement the activation flow and test:

```bash
# Should display verification code
adb logcat | grep "verification"

```

### 3. Network Inspection

```bash
# Capture WebSocket traffic
adb shell "tcpdump -i any -s 0 -w /sdcard/websocket.pcap port 443"

```

---

## 📊 Methods Comparison

| Feature | ESP32 | py-xiaozhi | Self-hosted |
| --- | --- | --- | --- |
| Complexity | ⭐ Low | ⭐⭐⭐ High | ⭐⭐ Medium |
| Security | ⭐⭐ Basic | ⭐⭐⭐ HMAC | ⭐ Variable |
| Setup | Manual code entry | Voice announcement | Auto/None |
| Session | No | Yes (token) | Variable |
| Server Support | ✅ Older | ✅ Current | ✅ Custom |
| Best For | IoT devices | Desktop/Mobile | Development |

*(Table Source:)*

---

## 🎯 Recommendation

### Immediate (Quick Test):

1. Add `client_id` to the Authorize payload.
2. Test to see if it works.

### Short-term (If ESP32 does not work):

1. Implement the py-xiaozhi activation flow.
2. Support token-based authentication.
3. Add verification code display functionality.

### Long-term:

1. Support both methods.
2. Automatically detect server requirements.
3. Implement fallback mechanisms between methods.

---

## 📝 Code Examples

### Quick Fix: Add client_id

```java
// XiaozhiConnectionService.java
private void sendAuthorizeHandshake() {
    // ... existing code ...
    
    payload.put("device_id", deviceId);
    payload.put("pairing_code", pairingCode);
    payload.put("device_type", "android");
    payload.put("os_version", Build.VERSION.RELEASE);
    payload.put("app_version", "1.0.0");
    payload.put("brand", "Phicomm");
    payload.put("model", "R1");
    payload.put("client_id", XiaozhiConfig.CLIENT_ID); // ← ADD THIS
    
    // ... rest of code ...
}

```

### Full py-xiaozhi Implementation

See: `PY_XIAOZHI_ACTIVATION_IMPLEMENTATION.md` (to be created)

---

## 🔗 References

* ESP32 Code: [https://github.com/78/xiaozhi-esp32](https://github.com/78/xiaozhi-esp32)
* py-xiaozhi: F:/PHICOMM_R1/xiaozhi/py-xiaozhi
* Current Android: [XiaozhiConnectionService.java](https://www.google.com/search?q=R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java)

---

**Status**: 🔍 Investigating

**Next Action**: Test the quick fix with `client_id`

**Created**: 2025-10-17
