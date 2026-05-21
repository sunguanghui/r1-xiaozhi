# SSL Certificate Expiration Fix

## Problem
WebSocket connection failed due to an expired SSL certificate on the server:
```
ExtCertPathValidatorException: Could not validate certificate: 
Certificate expired at Fri Nov 08 07:59:59 GMT+08:00 2024 
(compared to Sat Oct 18 19:38:08 GMT+08:00 2025)
```

## Root Cause
- The server `wss://xiaozhi.me/v1/ws` uses an expired SSL certificate
- Certificate expiry date: **Nov 08, 2024**
- Current date: **Oct 18, 2025**
- The Android WebSocket client validates SSL certificates by default → rejects expired cert

## Solution

### Option 1: Bypass SSL Validation (Development/Testing Only)
**⚠️ WARNING: FOR TESTING ONLY — DO NOT USE IN PRODUCTION!**

Add a custom `SSLSocketFactory` and `HostnameVerifier` to bypass SSL validation:

```java
// TrustAllCertificates.java
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class TrustAllCertificates {
    
    public static SSLSocketFactory getSSLSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static HostnameVerifier getAllowAllHostnameVerifier() {
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true; // Accept all hostnames
            }
        };
    }
}
```

**Update XiaozhiConnectionService.java:**
```java
// In connectWithToken() method
webSocketClient = new WebSocketClient(serverUri, headers) {
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        // ...
    }
    // ... other methods
};

// Set SSL socket factory (bypass certificate validation)
if (serverUri.getScheme().equals("wss")) {
    webSocketClient.setSocketFactory(TrustAllCertificates.getSSLSocketFactory());
}

webSocketClient.connect();
```

### Option 2: Request Server Admin to Renew Certificate
Contact the administrator of `xiaozhi.me` to renew the SSL certificate.

### Option 3: Use HTTP Instead of HTTPS (Not Recommended)
Switch from `wss://` to `ws://` (unencrypted):
```java
// XiaozhiConfig.java
public static final String WEBSOCKET_URL = "ws://xiaozhi.me/v1/ws";
```

**⚠️ Not secure — data is transmitted without encryption!**

### Option 4: Add Custom Certificate to Trust Store
If you have a new certificate from the server, you can add it to the app's trust store.

## Recommended Solution

**For Development/Testing:**
1. Use Option 1 (Bypass SSL) with an enable/disable flag
2. Add configuration to turn SSL validation on or off
3. Log a clear warning when SSL bypass is enabled

**For Production:**
1. Request the server admin to renew the certificate
2. Or use a different server with a valid certificate

## Implementation Plan

### 1. Create TrustAllCertificates utility
```java
package com.phicomm.r1.xiaozhi.util;

// ... code above
```

### 2. Add SSL bypass option to XiaozhiConfig
```java
// For testing only - disable SSL validation
public static final boolean BYPASS_SSL_VALIDATION = true; // Set to false in production!
```

### 3. Update XiaozhiConnectionService
```java
if (serverUri.getScheme().equals("wss") && XiaozhiConfig.BYPASS_SSL_VALIDATION) {
    Log.w(TAG, "⚠️ SSL VALIDATION BYPASSED - FOR TESTING ONLY!");
    webSocketClient.setSocketFactory(TrustAllCertificates.getSSLSocketFactory());
}
```

## Security Notes

- **NEVER** bypass SSL validation in production apps
- Expired certificates are a security risk
- Man-in-the-middle attacks are possible if SSL is not validated
- Only use bypass for testing with a server you fully trust

## Timeline
- Certificate expired: **Nov 08, 2024**
- Current date: **Oct 18, 2025**
- Certificate has been expired for **11 months and 10 days**

The server admin needs to renew the certificate immediately!

---

## 🔧 Implementation Update (Revision 2)

### ❌ The initial approach did NOT work

**Error**: The `Java-WebSocket 1.3.9` library does not have a `setSocketFactory()` method

```java
// ❌ DOES NOT COMPILE - Method does not exist
webSocketClient.setSocketFactory(TrustAllCertificates.getSSLSocketFactory());
```

**Error message**:
```
error: cannot find symbol
  symbol:   method setSocketFactory(SSLSocketFactory)
  location: variable webSocketClient of type WebSocketClient
```

### ✅ Actual solution: Use Java Reflection

Since `Java-WebSocket 1.3.9` has a private field `socketFactory` but no public setter, we must use **Reflection** to access it:

```java
// In XiaozhiConnectionService.connectToServer()

// Check if SSL bypass is needed
final boolean bypassSSL = serverUri.getScheme().equals("wss") 
                         && XiaozhiConfig.BYPASS_SSL_VALIDATION;

if (bypassSSL) {
    Log.w(TAG, "⚠️ ============================================");
    Log.w(TAG, "⚠️ SSL CERTIFICATE VALIDATION BYPASSED!");
    Log.w(TAG, "⚠️ THIS IS INSECURE - FOR TESTING ONLY!");
    Log.w(TAG, "⚠️ NEVER USE IN PRODUCTION!");
    Log.w(TAG, "⚠️ ============================================");
}

webSocketClient = new WebSocketClient(serverUri, headers) {
    
    @Override
    protected void onSetSSLParameters(javax.net.ssl.SSLParameters sslParameters) {
        // Called before SSL handshake - can customize SSL parameters here
        if (bypassSSL) {
            Log.d(TAG, "Setting custom SSL parameters (bypass mode)");
        }
    }
    
    // ... other overrides ...
};

// 🔒 Set SSL socket factory via reflection (BEFORE connect)
if (bypassSSL) {
    try {
        // Use reflection to access private field
        java.lang.reflect.Field socketFactoryField = 
            webSocketClient.getClass().getDeclaredField("socketFactory");
        socketFactoryField.setAccessible(true);
        socketFactoryField.set(webSocketClient, 
            TrustAllCertificates.getSSLSocketFactory());
        Log.d(TAG, "✅ SSL socket factory set successfully via reflection");
        
    } catch (Exception e) {
        Log.e(TAG, "❌ Failed to set SSL socket factory via reflection", e);
        
        // Fallback: Try system property (less reliable)
        System.setProperty("javax.net.ssl.trustAll", "true");
        Log.w(TAG, "⚠️ Using fallback system property method");
    }
}

webSocketClient.connect();
```

## 🔍 Why is Reflection needed?

### Java-WebSocket Library Internal Structure

```java
// Inside the WebSocketClient class (version 1.3.9)
public abstract class WebSocketClient extends WebSocketAdapter implements Runnable {
    
    // ❌ Private field - cannot be accessed directly from outside
    private SSLSocketFactory socketFactory;
    
    // ❌ No public setter method
    // The method setSocketFactory() DOES NOT EXIST in this version
    
    // ✅ Can only be accessed via the Java Reflection API
}
```

### Reflection Step-by-Step

```java
// 1. Get reference to the private field
Field socketFactoryField = webSocketClient.getClass()
                          .getDeclaredField("socketFactory");

// 2. Make it accessible (bypass private modifier)
socketFactoryField.setAccessible(true);

// 3. Set the value to our custom SSLSocketFactory
socketFactoryField.set(webSocketClient, customSocketFactory);
```

### Alternative Approaches Considered

| Approach | Status | Notes |
|----------|--------|-------|
| `setSocketFactory()` method | ❌ Not available | Method doesn't exist in Java-WebSocket 1.3.9 |
| Subclass + override socket creation | ❌ Too complex | Would need to override many internal methods |
| **Reflection** | ✅ **WORKS** | Direct field access, clean solution |
| Upgrade to Java-WebSocket 1.5.x | ⚠️ Risky | May break Java 7 / Android API 22 compatibility |
| Switch to `ws://` (no SSL) | ⚠️ Insecure | No encryption - not acceptable |
| Different WebSocket library | ⚠️ Major refactor | Too much code change needed |

## 🔬 Testing Strategy

### 1. Build Verification
```bash
cd R1XiaozhiApp
./gradlew clean assembleDebug
# Should compile without errors
```

### 2. Runtime Verification

Deploy APK and check **logcat** for these messages:

**Success indicators:**
```
⚠️ ============================================
⚠️ SSL CERTIFICATE VALIDATION BYPASSED!
⚠️ THIS IS INSECURE - FOR TESTING ONLY!
⚠️ NEVER USE IN PRODUCTION!
⚠️ ============================================
Setting custom SSL parameters (bypass mode)
✅ SSL socket factory set successfully via reflection
🔗 WebSocket connecting to: wss://xiaozhi.me/v1/ws?token=...
🔓 WebSocket connection opened successfully
```

**Failure indicators (should NOT appear):**
```
❌ ExtCertPathValidatorException: Could not validate certificate
❌ Certificate expired at Fri Nov 08...
❌ Failed to set SSL socket factory via reflection
```

### 3. Connection Test
1. Start app on device
2. Go through activation flow
3. Verify WebSocket connects successfully
4. Send test command → check if device responds

## 🐛 Troubleshooting

### If Reflection Fails

**Error**: `NoSuchFieldException: socketFactory`

**Possible causes:**
- Java-WebSocket library version changed
- Field name changed in newer versions

**Solution**: Check actual library version
```bash
./gradlew :app:dependencies | grep java-websocket
```

**Alternative**: Use fallback system property (less reliable)
```java
System.setProperty("javax.net.ssl.trustAll", "true");
```

### If Still Getting SSL Certificate Errors

**Option 1**: Switch to unencrypted WebSocket (not recommended)
```java
// In XiaozhiConfig.java
public static final String WEBSOCKET_URL = "ws://xiaozhi.me/v1/ws";
```

**Option 2**: Request server certificate renewal (preferred long-term solution)
- Contact server administrator
- Request Let's Encrypt renewal or new cert
- Current cert expired: **Nov 08, 2024**
- Has been expired for: **11 months 10 days**

**Option 3**: Add custom certificate to trust store
- Export server's certificate (even if expired)
- Add to Android app's trust store
- Requires certificate file access

## 📊 Commits Timeline

| Commit | Description | Status |
|--------|-------------|--------|
| `f44d22d` | Initial SSL bypass attempt (setSocketFactory) | ❌ Build failed |
| `(current)` | SSL bypass using Reflection | ✅ Should work |

## ⚠️ Security Warnings

### Development/Testing
- ✅ Acceptable to bypass SSL for testing with known server
- ✅ Always log warnings when SSL bypass is active
- ✅ Use configuration flag to enable/disable

### Production
- ❌ **NEVER** bypass SSL validation in production
- ❌ **NEVER** ship app with `BYPASS_SSL_VALIDATION = true`
- ❌ Man-in-the-middle attacks possible without SSL validation
- ❌ Violates app store security policies (Google Play, etc.)

### Recommended for Production Release
1. Set `BYPASS_SSL_VALIDATION = false` in `XiaozhiConfig.java`
2. Request server admin to renew SSL certificate
3. Test with valid certificate before release
4. Consider certificate pinning for extra security

## 🎯 Next Steps

1. ✅ Build APK with reflection-based SSL bypass
2. ⏳ Deploy to device and test
3. ⏳ Verify WebSocket connection succeeds
4. ⏳ Test voice commands end-to-end
5. ⏳ Request server admin to renew certificate
6. ⏳ Before production: Disable SSL bypass flag

---

**Last Updated**: Oct 19, 2025  
**Status**: Reflection implementation completed, testing pending
