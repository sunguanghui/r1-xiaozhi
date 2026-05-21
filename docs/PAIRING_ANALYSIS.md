# Analysis of the Xiaozhi Pairing Code System

## 🔍 Research from xiaozhi-esp32

After analyzing the source code at https://github.com/78/xiaozhi-esp32, the following was found:

### ❌ **Current problem:**
- Our code generates a **random 6-digit number**
- Xiaozhi Cloud **rejects** this random code with an "Invalid code" error

### ✅ **Correct approach:**
According to xiaozhi-esp32, the pairing code must be derived from:

```cpp
// From xiaozhi-esp32/src/main.cpp
String device_id = WiFi.macAddress(); // Get MAC address
device_id.replace(":", "");           // Remove colons

// Pairing code = last 6 characters of device_id
String pairing_code = device_id.substring(device_id.length() - 6);
```

**Example:**
- MAC Address: `AA:BB:CC:DD:EE:FF`
- Device ID: `AABBCCDDEEFF`
- **Pairing Code: `DDEEFF`** (last 6 characters)

### 🎯 **Solution:**

#### **Option 1: Use MAC Address (Same as ESP32)**
```java
// Get the R1's Wi-Fi MAC address
WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
WifiInfo wifiInfo = wifiManager.getConnectionInfo();
String macAddress = wifiInfo.getMacAddress(); // AA:BB:CC:DD:EE:FF
String deviceId = macAddress.replace(":", "").toUpperCase(); // AABBCCDDEEFF
String pairingCode = deviceId.substring(deviceId.length() - 6); // DDEEFF
```

#### **Option 2: Use Android Device ID**
```java
// Get Android device ID
String androidId = Settings.Secure.getString(
    getContentResolver(), 
    Settings.Secure.ANDROID_ID
); // Example: "9774d56d682e549c"

// Take the last 6 characters
String pairingCode = androidId.substring(androidId.length() - 6).toUpperCase();
```

### 📋 **Protocol Handshake:**

According to xiaozhi-esp32, the handshake message must follow this format:

```json
{
  "header": {
    "name": "Handshake",
    "message_id": "unique-id",
    "namespace": "System.Device"
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",  // Full device ID
    "pairing_code": "DDEEFF",      // Last 6 characters
    "model": "Phicomm R1",
    "firmware_version": "1.0.0"
  }
}
```

### 🔧 **Implementation Plan:**

1. **Update PairingCodeGenerator:**
   - Get Wi-Fi MAC or Android ID
   - Generate code from device ID (not random)
   - Store device_id to send in the handshake

2. **Update XiaozhiConnectionService:**
   - Send handshake according to Xiaozhi protocol format
   - Include device_id and pairing_code

3. **Testing:**
   - Verify pairing code matches the pattern
   - Test with Xiaozhi Console

### 📚 **References:**

- xiaozhi-esp32 source: https://github.com/78/xiaozhi-esp32
- Xiaozhi Protocol: https://stable-learn.com/en/py-xiaozhi-guide/
- ESP32 implementation: https://github.com/78/xiaozhi-esp32/blob/main/src/main.cpp

---

**Next:** Implement MAC-based pairing code generation
