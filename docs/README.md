# Xiaozhi Voice Assistant for Phicomm R1

A project integrating the **xiaozhi** library into the **Phicomm R1** smart speaker, turning it into a fully-fledged AI voice assistant with voice recognition, AI processing, and TTS response capabilities.

## 🎯 Features

* ✅ **Wake Word Detection**: Detects the trigger word "小智" (Xiao Zhi)
* ✅ **Voice Recognition**: Records audio and sends it to the Xiaozhi API
* ✅ **Dual Mode**: Supports Cloud and Self-hosted Xiaozhi servers
* ✅ **Auto Fallback**: Automatically switches from Cloud to Self-hosted when the connection is lost
* ✅ **LED Control**: Displays status via the R1's LED strip
* ✅ **Audio Playback**: Plays TTS responses from Xiaozhi
* ✅ **Auto Start**: Automatically starts upon R1 boot
* ✅ **Background Service**: Runs in the background 24/7

## 📋 Requirements

### Hardware

* Phicomm R1 speaker (Rockchip RK3229)
* USB or WiFi ADB connection
* Root access (optional, for LED control)

### Software

* Android Studio 2.3.3+
* Android SDK API 22 (Android 5.1)
* JDK 1.7/1.8
* ADB (Android Debug Bridge)

### Xiaozhi API

* Xiaozhi Cloud account ([https://xiaozhi.me](https://xiaozhi.me)) OR
* Self-hosted Xiaozhi server

## 🚀 Quick Installation

### Method 1: Using the automated script (Recommended)

**Linux/Mac:**

```bash
cd scripts
chmod +x install.sh
./install.sh [R1_IP_ADDRESS]

```

**Windows:**

```cmd
cd scripts
install.bat [R1_IP_ADDRESS]

```

### Method 2: Manual Installation

See detailed instructions in the [`INSTALLATION_GUIDE.md`](./INSTALLATION_GUIDE.md) file.

## 📁 Project Structure

```
.
├── R1XiaozhiApp/                    # Android project
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/phicomm/r1/xiaozhi/
│   │   │   │   ├── config/
│   │   │   │   │   └── XiaozhiConfig.java         # Configuration
│   │   │   │   ├── service/
│   │   │   │   │   ├── VoiceRecognitionService.java    # Audio recording
│   │   │   │   │   ├── XiaozhiConnectionService.java   # API connection
│   │   │   │   │   ├── AudioPlaybackService.java       # Audio playback
│   │   │   │   │   └── LEDControlService.java          # LED
│   │   │   │   ├── receiver/
│   │   │   │   │   └── BootReceiver.java          # Auto-start
│   │   │   │   └── ui/
│   │   │   │       └── MainActivity.java          # User Interface
│   │   │   ├── res/                               # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
├── scripts/
│   ├── install.sh                   # Installation script (Linux/Mac)
│   └── install.bat                  # Installation script (Windows)
├── scripts/
│   ├── INSTALLATION_GUIDE.md        # Detailed instructions
└── README.md                        # This file

```

## ⚙️ Configuration

### Xiaozhi Connection Mode

The app supports 2 connection modes:

1. **Cloud Mode** (default)
* URL: `wss://xiaozhi.me/websocket`
* Register at: [https://xiaozhi.me](https://xiaozhi.me)


2. **Self-hosted Mode**
* URL: `ws://YOUR_SERVER:8080/websocket`
* Server installation: [https://stable-learn.com/en/py-xiaozhi-guide/](https://stable-learn.com/en/py-xiaozhi-guide/)



### Configuration in [`XiaozhiConfig.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/config/XiaozhiConfig.java:1)

```java
public static final String DEFAULT_CLOUD_URL = "wss://xiaozhi.me/websocket";
public static final String DEFAULT_SELF_HOSTED_URL = "ws://192.168.1.100:8080/websocket";
public static final String DEFAULT_WAKE_WORD = "小智";

```

## 🔧 Build from source

```bash
# Clone repository
git clone <repository-url>
cd r1xiaozhi

# Build APK
cd R1XiaozhiApp
./gradlew assembleRelease  # Linux/Mac
gradlew.bat assembleRelease  # Windows

# The APK will be generated at:
# app/build/outputs/apk/release/app-release.apk

```

## 📱 Usage

### Starting services

```bash
# Start all services
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity

# Or start each service individually
adb shell am startservice com.phicomm.r1.xiaozhi/.service.VoiceRecognitionService
adb shell am startservice com.phicomm.r1.xiaozhi/.service.XiaozhiConnectionService

```

### Test wake word

1. Say the wake word: **"小智"** (Xiao Zhi)
2. The LED will turn green (listening)
3. Speak your command
4. The LED will turn white (thinking)
5. The R1 will play the response from Xiaozhi

### Viewing logs

```bash
# Realtime logs
adb logcat | grep -E "(VoiceRecognition|XiaozhiConnection|AudioPlayback|LEDControl)"

# Or filter by tag
adb logcat VoiceRecognition:D XiaozhiConnection:D *:S

```

## 🎨 LED Status Colors

* **Light Blue**: Idle (waiting for wake word)
* **Green (spinning)**: Listening
* **White (pulsing)**: Thinking (processing)
* **Cyan**: Speaking
* **Red (blinking)**: Error

## 🔌 HTTP API

The app has a built-in HTTP server on port `8088`:

```bash
# Check status
curl http://192.168.1.XXX:8088/status

# Start services
curl http://192.168.1.XXX:8088/start

# Stop services
curl http://192.168.1.XXX:8088/stop

# Get config
curl http://192.168.1.XXX:8088/config

```

## 🐛 Troubleshooting

### App cannot connect to Xiaozhi

```bash
# Check network
adb shell ping xiaozhi.me

# Check config
adb shell cat /data/data/com.phicomm.r1.xiaozhi/shared_prefs/xiaozhi_config.xml

```

### Wake word is not working

Wake word detection currently uses a simple energy-based method. To improve this, you can integrate:

* **Porcupine**: [https://github.com/Picovoice/porcupine](https://github.com/Picovoice/porcupine)
* **Snowboy**: [https://github.com/Kitt-AI/snowboy](https://github.com/Kitt-AI/snowboy)

### LED is not working

```bash
# Check root
adb shell su -c "id"

# Test LED manually
adb shell su -c "echo '7fff ff0000' > /sys/class/leds/multi_leds0/led_color"

```

## 📚 References

* **Xiaozhi Documentation**: [https://stable-learn.com/en/py-xiaozhi-guide/](https://stable-learn.com/en/py-xiaozhi-guide/)
* **Xiaozhi Hardware Guide**: [https://docs.freenove.com/projects/fnk0102/en/latest/fnk0102/codes/xiaozhi/](https://docs.freenove.com/projects/fnk0102/en/latest/fnk0102/codes/xiaozhi/)
* **Phicomm R1 Hacking**: [https://github.com/sagan/r1-helper](https://github.com/sagan/r1-helper)
* **R1 Custom Apps**: [https://github.com/sallaixu/R1-APP](https://github.com/sallaixu/R1-APP)
* **Android Background Services**: [https://developer.android.com/develop/background-work/services](https://developer.android.com/develop/background-work/services)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

MIT License - Free to use and modify

## ⚠️ Disclaimer

This project is not officially affiliated with Phicomm or Xiaozhi. Use responsibly and comply with their terms of service.

## 🙏 Credits

* **Xiaozhi Team** - AI engine
* **Phicomm R1 Community** - Hardware hacking guides
* Contributors and testers

---

**Developed with ❤️ for the smart speaker community**

If you find this project helpful, please star ⭐ this repository!
