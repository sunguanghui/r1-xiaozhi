# Xiaozhi Voice Assistant for Phicomm R1

Integrates the **Xiaozhi** AI voice assistant into the **Phicomm R1** speaker, turning it into a smart speaker.

## ✨ Features

- 🎙️ **Wake Word Detection** - Activated by "小智" (Xiao Zhi)
- ☁️ **Xiaozhi Cloud Integration** - Connects to the Xiaozhi AI Cloud
- 🏠 **Self-hosted Support** - Supports running a local server
- 🔄 **Auto Fallback** - Automatically switches to backup when connection is lost
- 💡 **LED Status Indicator** - Displays status via LED
- 🔊 **Audio Playback** - Plays TTS audio from Xiaozhi
- 🚀 **Auto Start on Boot** - Automatically starts on device power-on
- 🌐 **HTTP Control Panel** - Web interface for management and viewing pairing code

## 📱 Pairing with Xiaozhi Cloud

### Step 1: Install APK on R1
```bash
adb install -r R1Xiaozhi-v1.0.0-release.apk
```

### Step 2: Get the Pairing Code

**Method 1: View via ADB**
```bash
adb logcat | grep "Pairing Code"
# Output: XiaozhiConnection: XIAOZHI PAIRING CODE: 123456
```

**Method 2: Access Web UI**
```
http://[R1_IP_ADDRESS]:8088
```

You will see the **6-digit code** displayed prominently.

### Step 3: Add Device to Xiaozhi Console

1. Go to https://xiaozhi.me/console/agents
2. Create a new Agent (or select an existing one)
3. Click **"Add Device"**
4. Enter the **6-digit code** from R1
5. ✅ Done!

### Step 4: Configure Agent

```
Dialogue Language: Vietnamese
Voice Role: Female (or male)
Role Introduction:
  I am {{assistant_name}}, a smart virtual assistant on the Phicomm R1 speaker.
  I have a pleasant voice, prefer concise sentences, and am always ready to help you.
```

## 🚀 Quick Start

### Download APK
```bash
# Download from GitHub Releases
wget https://github.com/xuan2261/r1-xiaozhi/releases/latest/download/R1Xiaozhi-v1.0.0-release.apk
```

### Install
```bash
# Connect R1 via ADB
adb connect 192.168.1.XXX:5555

# Install APK
adb install -r R1Xiaozhi-v1.0.0-release.apk

# Grant permissions
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.RECORD_AUDIO
adb shell pm grant com.phicomm.r1.xiaozhi android.permission.WRITE_EXTERNAL_STORAGE
```

### Start
```bash
# Start services
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

### View Pairing Code
```bash
# Via ADB
adb logcat | grep "PAIRING CODE"

# Or open in browser
# http://192.168.1.XXX:8088
```

## 📖 Detailed Guide

See [`INSTALLATION_GUIDE.md`](../docs/INSTALLATION_GUIDE.md) for a complete guide covering:
- Setting up the development environment
- Building from source code
- Advanced configuration
- Troubleshooting
- Root and LED control

## 🏗️ Build from Source

```bash
# Clone repository
git clone https://github.com/xuan2261/r1-xiaozhi.git
cd r1-xiaozhi/R1XiaozhiApp

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease

# APK output
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk
```

## 🌐 HTTP API Endpoints

After installing the app, you can access these endpoints:

- `http://[R1_IP]:8088/` - Home page with pairing code
- `http://[R1_IP]:8088/pairing` - JSON pairing info
- `http://[R1_IP]:8088/status` - Device status
- `http://[R1_IP]:8088/config` - Configuration
- `http://[R1_IP]:8088/start` - Start services
- `http://[R1_IP]:8088/stop` - Stop services

## 🔧 Tech Stack

- **Android API 22** (Lollipop 5.1) - Target for Phicomm R1
- **Rockchip RK3229** - R1's chipset (ARMv7)
- **Java 7** - Backward compatibility
- **OkHttp 3.12.13** - WebSocket communication
- **NanoHTTPD** - Embedded HTTP server
- **Gson** - JSON parsing

## 📦 Dependencies

```gradle
// Network
compile 'com.squareup.okhttp3:okhttp:3.12.13'
compile 'com.google.code.gson:gson:2.8.5'

// HTTP Server
compile 'org.nanohttpd:nanohttpd:2.3.1'
compile 'org.nanohttpd:nanohttpd-websocket:2.3.1'

// Logging
compile 'com.jakewharton.timber:timber:4.5.1'
```

## 📂 Project Structure

```
R1XiaozhiApp/
├── app/
│   └── src/main/
│       ├── java/com/phicomm/r1/xiaozhi/
│       │   ├── config/          # Configuration
│       │   ├── service/         # Background services
│       │   ├── ui/              # Activities
│       │   ├── receiver/        # Broadcast receivers
│       │   └── util/            # Utilities
│       ├── res/                 # Resources
│       └── AndroidManifest.xml
├── build.gradle
└── README.md
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

MIT License - Free to use and modify

## 🙏 Credits

- **Xiaozhi AI** - https://xiaozhi.me
- **Phicomm R1** - Hardware platform
- **Community** - Testing and feedback

## 📞 Support

- **Documentation**: [INSTALLATION_GUIDE.md](../docs/INSTALLATION_GUIDE.md)
- **Issues**: https://github.com/xuan2261/r1-xiaozhi/issues
- **Xiaozhi Docs**: https://stable-learn.com/en/py-xiaozhi-guide/

---

**Made with ❤️ for Phicomm R1 Community**
