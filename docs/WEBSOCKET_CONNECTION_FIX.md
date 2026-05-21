# WebSocket Connection Fix

## Problem
After a successful activation, the WebSocket connection fails and cannot connect.

## Analysis

### Current Android Implementation
```java
// XiaozhiConfig.java - Line 12
public static final String WEBSOCKET_URL = "wss://xiaozhi.me/v1/ws";
```

### py-xiaozhi WebSocket URL
Need to verify from the py-xiaozhi config to ensure the URL is correct.

**Most likely**: The WebSocket URL must match the backend server being used for the activation API.

## Possible Causes

### 1. **Incorrect URL**
- Android: `wss://xiaozhi.me/v1/ws`
- Backend may be using a different URL

### 2. **Token authentication issue**
```java
// XiaozhiConnectionService.java - Line 199
headers.put("Authorization", "Bearer " + accessToken);
```

The token may be:
- Expired
- Wrong format
- Not recognized by the server

### 3. **Hello message format**
```java
// XiaozhiConnectionService.java - Line 279-309
{
  "header": {
    "name": "hello",
    "namespace": "ai.xiaoai.common",
    "message_id": "uuid"
  },
  "payload": {
    "device_id": "MAC_ADDRESS",
    "serial_number": "SN-HASH-MAC",
    ...
  }
}
```

The server may expect a different format or additional fields.

### 4. **MAC address format in the hello message**
After fixing the MAC format to include colons, need to verify that:
- `device_id` in the hello message = MAC with colons
- Server expects this format

## Need to Check from py-xiaozhi

### Files to Review
1. **`src/utils/config_manager.py`**
   - WebSocket URL configuration
   - Default server endpoints

2. **`src/core/websocket_client.py`**
   - WebSocket connection logic
   - Header format
   - Authentication method

3. **`src/core/application.py`**
   - Hello message format
   - Connection flow after activation

## Debugging Steps

### 1. Log WebSocket connection
Add detailed logging:
```java
Log.i(TAG, "=== WEBSOCKET CONNECTION ===");
Log.i(TAG, "URL: " + XiaozhiConfig.WEBSOCKET_URL);
Log.i(TAG, "Token: " + accessToken.substring(0, 20) + "...");
Log.i(TAG, "Headers: " + headers.toString());
Log.i(TAG, "============================");
```

### 2. Log WebSocket errors
```java
@Override
public void onError(Exception ex) {
    Log.e(TAG, "=== WEBSOCKET ERROR ===");
    Log.e(TAG, "Error class: " + ex.getClass().getName());
    Log.e(TAG, "Error message: " + ex.getMessage());
    ex.printStackTrace();
    Log.e(TAG, "======================");
}
```

### 3. Log server close reason
```java
@Override
public void onClose(int code, String reason, boolean remote) {
    Log.w(TAG, "=== WEBSOCKET CLOSED ===");
    Log.w(TAG, "Code: " + code);
    Log.w(TAG, "Reason: " + reason);
    Log.w(TAG, "Remote: " + remote);
    Log.w(TAG, "========================");
}
```

## Potential Fixes

### Fix 1: Update WebSocket URL
If py-xiaozhi uses a different URL:
```java
// Change from
public static final String WEBSOCKET_URL = "wss://xiaozhi.me/v1/ws";

// To (example)
public static final String WEBSOCKET_URL = "wss://api.tenclass.net/xiaozhi/ws";
```

### Fix 2: Add token refresh
If the token expires:
```java
// In DeviceActivator.java
public void refreshToken() {
    // Call API to refresh token before connecting WebSocket
}
```

### Fix 3: Update hello message format
If the server expects a different format:
```java
// Match exactly with py-xiaozhi hello message
payload.put("device_id", deviceId);  // MAC with colons
payload.put("serial_number", serialNumber);
payload.put("client_id", XiaozhiConfig.CLIENT_ID);
// Add other fields if needed
```

### Fix 4: Add connection timeout & better error handling
```java
webSocketClient.setConnectionLostTimeout(30); // 30 seconds timeout

// Better retry logic
private void scheduleReconnect(final int errorCode) {
    if (retryCount >= MAX_RETRIES) {
        // Force re-activation if token may be invalid
        if (errorCode == ErrorCodes.AUTHENTICATION_ERROR) {
            deviceActivator.resetActivation();
        }
        return;
    }
    // ... existing retry logic
}
```

## Next Steps

1. ✅ Add detailed logging for debugging
2. ⏳ Compare WebSocket URL with py-xiaozhi
3. ⏳ Verify token format and expiration
4. ⏳ Check hello message format
5. ⏳ Test with real server and capture logs

## Reference Files
- [`XiaozhiConnectionService.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java:194) - WebSocket connection
- [`XiaozhiConfig.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/config/XiaozhiConfig.java:12) - WebSocket URL
- [`DeviceActivator.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/activation/DeviceActivator.java:1) - Token management
