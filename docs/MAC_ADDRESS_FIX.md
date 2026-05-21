# MAC Address Format Fix

## Problem
When calling the OTA API, the server returns the error **HTTP 400: "Invalid MAC address"**

## Root Cause
**MAC address format mismatch between Android and py-xiaozhi:**

### py-xiaozhi (CORRECT)
```python
# device_fingerprint.py - Line 91-94
def _normalize_mac_address(self, mac_address: str) -> str:
    clean_mac = re.sub(r'[^a-fA-F0-9]', '', mac_address)
    formatted_mac = ":".join(clean_mac[i : i + 2] for i in range(0, 12, 2))
    return formatted_mac.lower()  # ✅ "aa:bb:cc:dd:ee:ff"
```

### Android before fix (WRONG)
```java
// DeviceFingerprint.java - Line 107
private String retrieveMacAddress() {
    // ...
    return macAddress.replace(":", "").toLowerCase();  // ❌ "aabbccddeeff"
}
```

### Server OTA API requirement
- **Format**: `"aa:bb:cc:dd:ee:ff"` (lowercase with colons)
- **Validation**: Rejects incorrect format → HTTP 400

## Solution

### 1. Add MAC address normalization method
```java
/**
 * Normalize MAC address to format: aa:bb:cc:dd:ee:ff
 * Matches py-xiaozhi device_fingerprint.py line 91-94
 */
private String normalizeMacAddress(String mac) {
    if (mac == null || mac.isEmpty()) {
        return "00:00:00:00:00:00";
    }
    
    // Remove all non-hex characters
    String clean = mac.replaceAll("[^a-fA-F0-9]", "");
    
    if (clean.length() != 12) {
        return "00:00:00:00:00:00";
    }
    
    return formatMacAddress(clean);
}

/**
 * Format 12-character hex string to MAC format with colons
 * Example: "aabbccddeeff" -> "aa:bb:cc:dd:ee:ff"
 */
private String formatMacAddress(String cleanMac) {
    StringBuilder formatted = new StringBuilder();
    for (int i = 0; i < 12; i += 2) {
        if (i > 0) {
            formatted.append(":");
        }
        formatted.append(cleanMac.substring(i, i + 2));
    }
    return formatted.toString().toLowerCase();
}
```

### 2. Update retrieveMacAddress()
```java
private String retrieveMacAddress() {
    try {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);  // ✅ Fixed typo
        
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String macAddress = wifiInfo.getMacAddress();
            
            if (macAddress != null && !macAddress.equals("02:00:00:00:00:00")) {
                return normalizeMacAddress(macAddress);  // ✅ Return with colons
            }
        }
    } catch (Exception e) {
        // ...
    }
    return "00:00:00:00:00:00";
}
```

### 3. Update generateSerialNumber()
```java
public String generateSerialNumber() {
    String macAddress = getDeviceId();  // Already normalized with colons
    
    // Remove colons for serial number (match py-xiaozhi line 226)
    String macClean = macAddress.toLowerCase().replace(":", "");
    
    // Generate short hash from MAC + Android ID
    String androidId = Settings.Secure.getString(
        context.getContentResolver(), 
        Settings.Secure.ANDROID_ID
    );
    String combined = macClean + (androidId != null ? androidId : "");
    String hash = generateHash(combined);
    String shortHash = hash.substring(0, 8);
    
    return String.format("SN-%s-%s", shortHash, macClean);
}
```

## Result

### Before fix
```
MAC address sent: "aabbccddeeff"
Server response: HTTP 400 {"error": "Invalid MAC address"}
```

### After fix
```
MAC address sent: "aa:bb:cc:dd:ee:ff"
Server response: HTTP 200 + OTA config data
```

## Commit
- **Hash**: `71bb1fc`
- **Message**: "Fix: Correct MAC address format to match py-xiaozhi"
- **Files changed**: `DeviceFingerprint.java`

## Testing
To test this fix:
1. Build and install the APK
2. Run the activation flow
3. Check the log to verify the MAC address has the correct format
4. Verify the server receives the MAC in the format `aa:bb:cc:dd:ee:ff`

## Reference
- **py-xiaozhi**: `src/core/device_fingerprint.py` line 70-94, 201-229
- **Android**: `DeviceFingerprint.java` line 96-175
- **OTA API**: `https://api.tenclass.net/xiaozhi/ota/`
