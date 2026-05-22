# Guide to Integrating Xiaozhi into Phicomm R1

## Overview

This project integrates the **xiaozhi** library into the **Phicomm R1** speaker, transforming it into a smart AI voice assistant with the following capabilities:

* ✅ Voice recognition and wake word detection
* ✅ Connection to the Xiaozhi API (Cloud or Self-hosted)
* ✅ TTS (Text-to-Speech) audio playback from Xiaozhi
* ✅ LED control for status indication
* ✅ Automatic startup on boot
* ✅ 24/7 background operation

## Requirements

### Hardware

* **Phicomm R1** speaker (with Rockchip RK3229 chipset)
* ADB connection via USB or WiFi
* Root access (for LED control)

### Software

* **Android Studio** 2.3.3 or newer
* **Android SDK** API Level 22 (Android 5.1)
* **JDK** 1.7 or 1.8
* **ADB** (Android Debug Bridge)
* **Gradle** (included with Android Studio)

### Xiaozhi API

* Xiaozhi Cloud account ([https://xiaozhi.me](https://xiaozhi.me)) OR
* Self-hosted Xiaozhi server (see docs at [https://stable-learn.com/en/py-xiaozhi-guide/](https://stable-learn.com/en/py-xiaozhi-guide/))

## Step 1: Prepare the Development Environment

### 1.1. Install Android Studio

```bash
# Download Android Studio from:
# https://developer.android.com/studio

# After installation, open Android Studio and install these components:
# - Android SDK Platform 22
# - Android SDK Build-Tools 22.0.1
# - Android SDK Platform-Tools

```

### 1.2. Set Up ADB

```bash
# Check if ADB is installed
adb version

# If not, add it to your PATH:
# Windows: Add C:\Users\[YourUser]\AppData\Local\Android\Sdk\platform-tools to PATH
# Linux/Mac: export PATH=$PATH:~/Android/Sdk/platform-tools

```

### 1.3. Connect to the R1 via ADB

```bash
# Enable Developer Mode on the R1 (if settings are accessible)
# Or connect via USB and enable ADB

# Check connection
adb devices

# If connecting via WiFi
adb connect 192.168.1.XXX:5555  # Replace XXX with the R1's IP address

```

## Step 2: Build the Application

### 2.1. Clone or Open the Project

```bash
# Open Android Studio
# File > Open > Select the R1XiaozhiApp folder

```

### 2.2. Sync Gradle

```bash
# Android Studio will automatically sync
# Or run manually:
cd R1XiaozhiApp
./gradlew clean build  # Linux/Mac
gradlew.bat clean build  # Windows

```

### 2.3. Build the APK

```bash
# Build the debug APK (for testing)
./gradlew assembleDebug

# Build the release APK (for production)
./gradlew assembleRelease

# The APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk

```

## Step 3: Configure Xiaozhi Cloud

### 3.1. Register a Xiaozhi Account

1. **Visit** [https://xiaozhi.me/](https://xiaozhi.me/)
2. **Register** for a new account
3. **Log in** and select **Console**

### 3.2. Create an Agent (AI Assistant)

1. In the Console, click **"Create Agent"** in the top right corner
2. Name the Agent (e.g., "R1 Assistant")
3. Configure the Agent:

```text
Dialogue Language: Vietnamese
Voice Role: Female voice (or male voice as preferred)
Role Introduction:
  I am {{assistant_name}}, a smart virtual assistant on the Phicomm R1 speaker.
  I have a pleasant voice, prefer using concise sentences, and am always ready to help you.

```

4. **Save the configuration**

### 3.3. Add the R1 Device to the Agent

1. Within the newly created Agent, click **"Manage Devices"** (or "Add Device" if there are no devices yet)
2. The system will display a form requesting a **6-digit pairing code**
3. **KEEP this page open** - we will retrieve the code from the R1 in the next step

### 3.4. Retrieve the Pairing Code from the R1

After installing the app on the R1 (Step 4), the app will automatically generate and display a **6-digit code**:

**Method 1: Check via ADB log**

```bash
# View the log to find the pairing code
adb logcat | grep "Pairing Code"

# The output will look like:
# XiaozhiConnection: Pairing Code: 123456

```

**Method 2: Check via HTTP API**

```bash
# Access via a browser or curl
curl http://192.168.1.XXX:8088/pairing

# Or open a web browser: http://192.168.1.XXX:8088/pairing

```

**Method 3: View on screen (if the R1 has HDMI output)**

* The app will display the pairing code prominently on the screen

### 3.5. Complete the Pairing

1. Copy the **6-digit code** from the R1
2. Return to the Xiaozhi Console page
3. Enter the code into the "Add Device" form
4. Click **"Add"** or **"Pair Device"**
5. ✅ **Success!** Your R1 is now connected to Xiaozhi Cloud

---

## 🔐 Pairing Code Mechanism (Important!)

### Device ID and Pairing Code

The app uses a **Device ID based on the MAC address** to generate the pairing code (following the xiaozhi-esp32 standard):

**Process:**

1. **Retrieve the WiFi MAC Address** of the R1 (e.g., `AA:BB:CC:DD:EE:FF`)
2. **Create the Device ID**: Remove the `:` colons → `AABBCCDDEEFF`
3. **Generate the Pairing Code**: Take the last 6 characters → `DDEEFF`

**Advantages:**

* ✅ The code is **static** and does not change upon reboot
* ✅ Compatible with the **Xiaozhi Cloud protocol**
* ✅ Easy to debug and verify
* ✅ Every device has a unique code

**Fallback Options:**

* If the MAC address cannot be retrieved → it uses the **Android ID**
* If both fail → it uses a **timestamp** (last resort)

### Viewing the Device ID and Pairing Code

```bash
# Via ADB logcat
adb logcat | grep "PairingCode"
# Output:
# PairingCode: Device ID: AABBCCDDEEFF
# PairingCode: Pairing Code: DDEEFF

# Via HTTP API
curl http://192.168.1.XXX:8088/pairing

# Via Web UI
http://192.168.1.XXX:8088/

```

### Debugging Pairing Issues

**Verify Device ID format:**

```bash
# The Device ID must be 12 hex characters (0-9, A-F)
# The Pairing Code must be 6 hex characters
# Valid example:
#   Device ID: AABBCC123456
#   Pairing Code: 123456

```

**Check MAC address:**

```bash
# Check the MAC address of the R1
adb shell ip addr show wlan0 | grep "link/ether"
# Output: link/ether aa:bb:cc:dd:ee:ff
# → Device ID will be: AABBCCDDEEFF
# → Pairing Code will be: DDEEFF

```

**If the MAC is faked (Android 6+):**

* Android 6+ may return a fake MAC address: `02:00:00:00:00:00`
* The app will automatically fallback to the Android ID
* Alternatively, reset the pairing to use a timestamp-based ID

---

### 3.6. If you encounter the "Device already added" error

This occurs when the pairing code has already been used previously. **Solutions:**

**Method 1: Reset via App (if the R1 has a screen)**

* Open the app on the R1
* Click the **"Reset Pairing"** button
* A new code will appear immediately

**Method 2: Reset via Web UI**

```bash
# Access via a browser or curl
curl http://192.168.1.XXX:8088/reset-pairing

# The output will contain a new code:
# {"status":"success","new_pairing_code":"654321",...}

```

**Method 3: Reset via ADB**

```bash
# Clear SharedPreferences
adb shell rm /data/data/com.phicomm.r1.xiaozhi/shared_prefs/xiaozhi_pairing.xml

# Restart the app
adb shell am force-stop com.phicomm.r1.xiaozhi
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity

# View the new code
adb logcat | findstr /i "PAIRING CODE"

```

After resetting, use the **new code** to add the device to the Xiaozhi Console.

### 3.6. Choose a Connection Mode

You have 2 options:

**Option A: Use Xiaozhi Cloud (Recommended - already set up above)**

* URL: `wss://xiaozhi.me/websocket`
* Device is registered and paired
* No need to host a server yourself

**Option B: Self-hosted Xiaozhi Server (Advanced)**

* Install the server following the guide: [https://stable-learn.com/en/py-xiaozhi-guide/](https://stable-learn.com/en/py-xiaozhi-guide/)
* URL: `ws://YOUR_SERVER_IP:8080/websocket`
* Requires server hosting knowledge

### 3.7. Edit the Default Configuration (Optional)

Open the file [`XiaozhiConfig.java`](https://www.google.com/search?q=R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/config/XiaozhiConfig.java:25) and modify:

```java
public static final String DEFAULT_CLOUD_URL = "wss://xiaozhi.me/websocket";       // matches code
public static final String DEFAULT_SELF_HOSTED_URL = "ws://192.168.1.100:8080/websocket"; // matches code
public static final String DEFAULT_WAKE_WORD = "小智"; // Or "Xiao Zhi"

```

## Step 4: Install on the R1

### 4.1. Disable R1 System Apps (Important!)

```bash
# Disable original Phicomm apps to prevent conflicts
adb shell pm hide com.phicomm.speaker.player
adb shell pm hide com.phicomm.speaker.device
adb shell pm hide com.phicomm.speaker.airskill
adb shell pm hide com.phicomm.speaker.exceptionreporter

# Verify that they were disabled successfully
adb shell pm list packages -d | grep phicomm

```

### 4.2. Install the APK

```bash
# Copy the APK to the R1
adb push app/build/outputs/apk/release/app-release.apk /data/local/tmp/

# Install
adb shell pm install -t -r /data/local/tmp/app-release.apk

# Or install directly (if using a newer ADB version)
adb install -r app/build/outputs/apk/release/app-release.apk

```

### 4.3. Grant Permissions

```bash
# Grant audio recording and other permissions
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.RECORD_AUDIO
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.READ_EXTERNAL_STORAGE

```

### 4.4. Root the R1 to Control the LED (Optional but recommended)

```bash
# Check for root access
adb shell su -c "id"

# If not rooted, refer to:
# https://github.com/sagan/r1-helper
# https://www.computersolutions.cn/blog/2019/08/hacking-a-phicomm-r1-speaker/

# Grant 'su' access to the app
adb shell su -c "pm grant com.phicomm.r1.xiaozhi android.permission.ACCESS_SUPERUSER"

```

## Step 5: Startup and Configuration

### 5.1. First-time App Launch

```bash
# Start MainActivity
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity

# Or start the service directly
adb shell am startservice com.phicomm.r1.xiaozhi/.service.VoiceRecognitionService
adb shell am startservice com.phicomm.r1.xiaozhi/.service.XiaozhiConnectionService

```

### 5.2. Configure via SharedPreferences

```bash
# Set to Cloud mode
adb shell "echo 'use_cloud=true' > /data/data/com.phicomm.r1.xiaozhi/shared_prefs/xiaozhi_config.xml"

# Or edit the XML file directly:
adb pull /data/data/com.phicomm.r1.xiaozhi/shared_prefs/xiaozhi_config.xml
# Edit the local file
adb push xiaozhi_config.xml /data/data/com.phicomm.r1.xiaozhi/shared_prefs/

```

### 5.3. Configuration Parameters

Configurable parameters include:

* `use_cloud`: `true` or `false`
* `cloud_url`: Xiaozhi Cloud URL
* `self_hosted_url`: Self-hosted server URL
* `api_key`: API key (if needed)
* `wake_word`: Wake word (default: "小智")
* `auto_start`: Auto-start on boot (`true`/`false`)
* `led_enabled`: Toggle LED on/off (`true`/`false`)
* `http_server_port`: Port for the HTTP server (default: 8088)

## Step 6: Testing

### 6.1. Check Logs

```bash
# View logs in realtime
adb logcat | grep -E "(VoiceRecognition|XiaozhiConnection|AudioPlayback|LEDControl)"

# Or filter by tag
adb logcat VoiceRecognition:D XiaozhiConnection:D AudioPlayback:D LEDControl:D *:S

```

### 6.2. Test the Wake Word

```bash
# Speak the wake word into the R1's mic
# Default: "小智" (Xiao Zhi)

# Check the log to see:
# VoiceRecognition: Wake word detected!
# LEDControl: State: LISTENING

```

### 6.3. Test Audio Playback

```bash
# Send a test command
adb shell am startservice \
  -n com.phicomm.r1.xiaozhi/.service.AudioPlaybackService \
  -a com.phicomm.r1.xiaozhi.PLAY_URL \
  --es audio_url "https://example.com/test.mp3"

```

## Step 7: Auto-start on Boot

### 7.1. Enable Auto-start

The app includes a [`BootReceiver`](https://www.google.com/search?q=R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/receiver/BootReceiver.java:1) to automatically launch it.

```bash
# Check if the BootReceiver is enabled
adb shell pm list packages -e | grep xiaozhi

# Test the boot receiver
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED

```

### 7.2. Disable Battery Optimization

```bash
# Prevent the app from being killed
adb shell dumpsys deviceidle whitelist +com.phicomm.r1.xiaozhi

```

## Troubleshooting

### Error: "Cannot connect to Xiaozhi"

**Solution:**

1. Check the R1's network connection
2. Ping the xiaozhi server: `adb shell ping xiaozhi.me`
3. Check the URL in the config
4. Try switching to self-hosted mode

### Error: "No RECORD_AUDIO permission"

**Solution:**

```bash
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.RECORD_AUDIO

```

### Error: "LED control not working"

**Solution:**

1. Check your root access
2. Test the LED manually:

```bash
adb shell su -c "echo '7fff ff0000' > /sys/class/leds/multi_leds0/led_color"

```

### The App Crashes on Boot

**Solution:**

```bash
# View the crash log
adb logcat | grep AndroidRuntime

# Temporarily disable auto-start
adb shell pm disable com.phicomm.r1.xiaozhi/.receiver.BootReceiver

```

### The Wake Word Does Not Work

**Solution:**

1. Currently, simple energy-based detection is used.
2. To improve it, integrate a specialized library such as:
* **Porcupine** ([https://github.com/Picovoice/porcupine](https://github.com/Picovoice/porcupine))
* **Snowboy** ([https://github.com/Kitt-AI/snowboy](https://github.com/Kitt-AI/snowboy))



## Advanced: HTTP API Server

The app features a built-in HTTP server for remote control:

```bash
# Access via a browser or curl
curl http://192.168.1.XXX:8088/status
curl http://192.168.1.XXX:8088/start
curl http://192.168.1.XXX:8088/stop
curl http://192.168.1.XXX:8088/config

```

## Uninstall

```bash
# Remove the app
adb uninstall com.phicomm.r1.xiaozhi

# Re-enable system apps
adb shell pm unhide com.phicomm.speaker.player
adb shell pm unhide com.phicomm.speaker.device
adb shell pm unhide com.phicomm.speaker.airskill

```

## References

* **Xiaozhi Docs**: [https://stable-learn.com/en/py-xiaozhi-guide/](https://stable-learn.com/en/py-xiaozhi-guide/)
* **Xiaozhi Hardware Guide**: [https://docs.freenove.com/projects/fnk0102/en/latest/fnk0102/codes/xiaozhi/](https://docs.freenove.com/projects/fnk0102/en/latest/fnk0102/codes/xiaozhi/)
* **R1 Hacking**: [https://github.com/sagan/r1-helper](https://github.com/sagan/r1-helper)
* **R1 Custom ROM**: [https://github.com/sallaixu/R1-APP](https://github.com/sallaixu/R1-APP)

## Support

If you encounter any issues, please:

1. Check the logs: `adb logcat | grep Xiaozhi`
2. Review the configuration steps
3. Test each service individually

## License

MIT License - Free to use and modify

---

**Good luck! 🎉**
