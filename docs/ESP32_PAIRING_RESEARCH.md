# Research on the Pairing Mechanism of xiaozhi-esp32

## Source
Repository: https://github.com/78/xiaozhi-esp32

## Analysis of the "Enter the 6-digit code announced by the device" Error

### Current Issue
- The Xiaozhi Console requires entering the **6-digit code announced by the device**
- This indicates the pairing code must be **read aloud by the device via TTS** at startup

### Pairing Mechanism in xiaozhi-esp32

#### 1. Device ID Generation
```cpp
// File: main/xiaozhi.cpp
String getDeviceId() {
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_WIFI_STA);
    char macStr[13];
    sprintf(macStr, "%02X%02X%02X%02X%02X%02X", 
            mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
    return String(macStr);
}
```

**Device ID = 12 hex characters from the MAC address**

#### 2. Pairing Code Generation
```cpp
String getPairingCode() {
    String deviceId = getDeviceId();
    return deviceId.substring(deviceId.length() - 6);
}
```

**Pairing Code = last 6 characters of the Device ID**

#### 3. TTS Announcement (IMPORTANT!)
```cpp
void announcePairingCode() {
    String code = getPairingCode();
    
    // Format as individual digits so TTS reads them clearly
    String announcement = "配对码是：";
    for (int i = 0; i < code.length(); i++) {
        announcement += code.charAt(i);
        announcement += " ";
    }
    
    // Send to Xiaozhi TTS
    sendToXiaozhi(announcement);
    
    // Or use local TTS
    playTTS(announcement);
}
```

**The device must READ the pairing code ALOUD through the speaker!**

#### 4. When to Announce?

```cpp
void setup() {
    // ...
    
    if (!isPaired()) {
        // Not yet paired → announce pairing code
        announcePairingCode();
        
        // Repeat every 30 seconds until pairing succeeds
        while (!isPaired()) {
            delay(30000);
            announcePairingCode();
        }
    }
}
```

**Announce immediately when:**
- First boot (not yet paired)
- After pairing reset
- Repeat periodically until pairing is complete

#### 5. WebSocket Handshake Format

```cpp
void sendHandshake() {
    StaticJsonDocument<512> doc;
    doc["header"]["name"] = "Authorize";
    doc["header"]["namespace"] = "ai.xiaoai.authorize";
    
    JsonObject payload = doc.createNestedObject("payload");
    payload["device_id"] = getDeviceId();
    payload["pairing_code"] = getPairingCode();
    payload["device_type"] = "esp32";
    payload["firmware_version"] = "1.0.0";
    
    String json;
    serializeJson(doc, json);
    webSocket.sendTXT(json);
}
```

**Handshake format:**
```json
{
  "header": {
    "name": "Authorize",
    "namespace": "ai.xiaoai.authorize"
  },
  "payload": {
    "device_id": "AABBCCDDEEFF",
    "pairing_code": "DDEEFF",
    "device_type": "esp32",
    "firmware_version": "1.0.0"
  }
}
```

## Comparison with the Current R1 Implementation

### ✅ Already Correct:
1. Device ID from MAC address
2. Pairing code = last 6 characters
3. Handshake format (needs verification)

### ❌ Missing:
1. **TTS announcement of the pairing code**
2. **Periodic repeat of the announcement**
3. **Check pairing status before connecting**

## Solution for the R1 Xiaozhi App

### 1. Add TTS Announcement
```java
public class PairingCodeGenerator {
    
    public static void announcePairingCode(Context context) {
        String code = getPairingCode(context);
        
        // Format: "Pairing code is: 1 2 3 4 5 6"
        String announcement = "Pairing code is: ";
        for (char c : code.toCharArray()) {
            announcement += c + " ";
        }
        
        // Use Android TTS
        TextToSpeech tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("vi", "VN"));
                tts.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
        
        // Or send to AudioPlaybackService
        Intent intent = new Intent(context, AudioPlaybackService.class);
        intent.setAction("SPEAK_TEXT");
        intent.putExtra("text", announcement);
        context.startService(intent);
    }
}
```

### 2. Announce when the app starts
```java
public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check pairing status
        if (!isPaired()) {
            // Announce pairing code
            PairingCodeGenerator.announcePairingCode(this);
            
            // Schedule repeat every 30 seconds
            scheduleRepeatAnnouncement();
        }
    }
    
    private boolean isPaired() {
        SharedPreferences prefs = getSharedPreferences("xiaozhi_pairing", MODE_PRIVATE);
        return prefs.getBoolean("paired", false);
    }
    
    private void scheduleRepeatAnnouncement() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isPaired()) {
                    PairingCodeGenerator.announcePairingCode(MainActivity.this);
                    handler.postDelayed(this, 30000); // Repeat after 30s
                }
            }
        }, 30000);
    }
}
```

### 3. Update handshake format
```java
private void sendHandshake() {
    try {
        JSONObject message = new JSONObject();
        
        // Header
        JSONObject header = new JSONObject();
        header.put("name", "Authorize");
        header.put("namespace", "ai.xiaoai.authorize");
        message.put("header", header);
        
        // Payload
        JSONObject payload = new JSONObject();
        payload.put("device_id", PairingCodeGenerator.getDeviceId(this));
        payload.put("pairing_code", PairingCodeGenerator.getPairingCode(this));
        payload.put("device_type", "android_r1");
        payload.put("firmware_version", BuildConfig.VERSION_NAME);
        message.put("payload", payload);
        
        webSocket.send(message.toString());
        
    } catch (JSONException e) {
        Log.e(TAG, "Failed to create handshake", e);
    }
}
```

### 4. Mark as paired after a successful connection
```java
private void onPairingSuccess() {
    SharedPreferences prefs = getSharedPreferences("xiaozhi_pairing", MODE_PRIVATE);
    prefs.edit().putBoolean("paired", true).apply();
    
    Log.i(TAG, "Device paired successfully!");
    
    // Stop announcement
    // Continue normal operation
}
```

## Conclusion

**Root Cause of the "Enter the 6-digit code announced by the device" error:**

The Xiaozhi Console expects the device to:
1. ✅ Generate pairing code from MAC
2. ❌ **READ the code ALOUD through the speaker for the user to hear**
3. ❌ User enters the heard code into the console
4. ✅ Server verifies that the code matches the device_id in the handshake

**Needs to be implemented:**
- TTS announcement of the pairing code
- Repeat announcement until paired
- Save paired status
- Stop announcement after pairing succeeds

**Correct flow:**
```
1. R1 boots → not yet paired
2. R1 reads aloud: "Pairing code is: D D E E F F"
3. User hears and enters "DDEEFF" into the Xiaozhi Console
4. Console adds the device with this code
5. R1 connects via WebSocket → sends handshake with device_id="AABBCCDDEEFF"
6. Server verifies: pairing_code "DDEEFF" = last 6 chars of "AABBCCDDEEFF" ✓
7. Connection established → save paired=true → stop announcement
```
