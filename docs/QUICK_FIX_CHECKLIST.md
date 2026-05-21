# ⚡ Quick Fix Checklist - Common Android Issues

## 🔴 SIGABRT Crash (Fatal signal 6)

**Symptoms:**
```
F/libc: Fatal signal 6 (SIGABRT), code -6 in tid XXX
F/art: art/runtime/runtime.cc:289] native: #07 pc 0005f99d
```

**Quick Fix:**
1. Check logcat for the missing class name
2. Find the component in `AndroidManifest.xml`
3. Delete or create the corresponding class

**See details:** [CRASH_FIX_SIGABRT.md](CRASH_FIX_SIGABRT.md)

---

## 🟠 ClassNotFoundException

**Symptoms:**
```
java.lang.ClassNotFoundException: Didn't find class "com.xxx.YYY"
```

**Quick Fix:**
1. Verify package name in manifest
2. Check that the class file exists
3. Rebuild project: `./gradlew clean build`

---

## 🟡 WebSocket Connection Failed

**Symptoms:**
- Connection timeout
- "Connection refused"
- Immediate disconnect

**Quick Fix:**
1. Check server is running: `curl http://192.168.1.100:8080/health`
2. Verify IP in [`XiaozhiConfig.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/config/XiaozhiConfig.java)
3. Check firewall: `adb shell ping 192.168.1.100`

**Debug commands:**
```bash
# Test WebSocket server
wscat -c ws://192.168.1.100:8080/ws

# Monitor connections
adb logcat | grep -E "WebSocket|Connection"
```

---

## 🟢 Service Not Starting

**Symptoms:**
- Service not running
- `Service XXX has leaked ServiceConnection`

**Quick Fix:**
1. Check that the service is declared in the manifest
2. Verify sufficient permissions
3. Check service lifecycle:
```bash
adb logcat | grep -E "Service|onCreate|onStartCommand"
```

---

## 🔵 Permissions Denied

**Symptoms:**
```
java.lang.SecurityException: Permission denied
```

**Quick Fix:**
1. Add permission to manifest:
```xml
<uses-permission android:name="android.permission.XXX" />
```

2. Request runtime permission (API 23+):
```java
if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
    requestPermissions(new String[]{permission}, REQUEST_CODE);
}
```

3. Verify permissions granted:
```bash
adb shell dumpsys package com.phicomm.r1.xiaozhi | grep permission
```

---

## 🟣 Build Failures

### Gradle Sync Failed
```bash
# Clear cache
./gradlew clean
rm -rf .gradle build

# Re-sync
./gradlew --refresh-dependencies
```

### Compilation Errors
```bash
# Check syntax errors
./gradlew compileDebugJava --stacktrace

# Verify dependencies
./gradlew dependencies
```

### Missing Resources
```bash
# Check R.java generated
ls app/build/generated/source/r/debug/com/phicomm/r1/xiaozhi/R.java

# Rebuild resources
./gradlew clean generateDebugResources
```

---

## 🎯 LogCat Filters Reference

### Crash Detection
```bash
# Fatal errors
adb logcat *:E

# SIGABRT crashes
adb logcat | grep -E "Fatal signal|SIGABRT"

# Java exceptions
adb logcat | grep -E "Exception|Error"
```

### Service Monitoring
```bash
# All services
adb logcat | grep -E "Service|onCreate|onStartCommand|onDestroy"

# Specific service
adb logcat | grep "XiaozhiConnection"
```

### Network Debug
```bash
# WebSocket
adb logcat | grep -E "WebSocket|ws:|Connection"

# HTTP requests
adb logcat | grep -E "OkHttp|Request|Response"
```

### Pairing Debug
```bash
# Pairing flow
adb logcat | grep -E "Pairing|Authorize|DeviceId"

# Code generation
adb logcat | grep "PairingCode"
```

---

## 🛠️ Essential ADB Commands

### Installation
```bash
# Install new APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Uninstall completely
adb uninstall com.phicomm.r1.xiaozhi

# Reinstall (preserve data)
adb install -r -d app-debug.apk
```

### Monitoring
```bash
# Clear logcat
adb logcat -c

# Follow logcat
adb logcat | grep -E "Xiaozhi|MainActivity"

# Save to file
adb logcat > logcat.txt
```

### App Control
```bash
# Start activity
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity

# Stop app
adb shell am force-stop com.phicomm.r1.xiaozhi

# Clear app data
adb shell pm clear com.phicomm.r1.xiaozhi
```

### System Info
```bash
# Device info
adb shell getprop ro.build.version.sdk

# Network info
adb shell ip addr show wlan0

# Battery status
adb shell dumpsys battery
```

---

## 📊 Performance Checks

### Memory Usage
```bash
adb shell dumpsys meminfo com.phicomm.r1.xiaozhi
```

### CPU Usage
```bash
adb shell top | grep xiaozhi
```

### Network Usage
```bash
adb shell dumpsys netstats | grep xiaozhi
```

---

## 🔍 Common Error Codes

| Code | Meaning | Quick Fix |
|------|---------|-----------|
| `1001` | WebSocket connection failed | Check server IP/port |
| `1002` | Authorization timeout | Verify pairing code |
| `1003` | Invalid response | Check server protocol |
| `2001` | Service start failed | Check permissions |
| `2002` | Binding failed | Restart app |
| `3001` | Audio recording error | Check microphone permission |
| `4001` | Network unavailable | Check WiFi connection |

**See details:** [`ErrorCodes.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/util/ErrorCodes.java)

---

## 📚 Documentation Index

| Document | Purpose |
|----------|---------|
| [CRASH_FIX_SIGABRT.md](CRASH_FIX_SIGABRT.md) | Fix SIGABRT crashes |
| [PAIRING_DEBUG_GUIDE.md](PAIRING_DEBUG_GUIDE.md) | Debug pairing issues |
| [TESTING_GUIDE.md](TESTING_GUIDE.md) | Comprehensive test cases |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) | Project overview |
| [HUONG_DAN_CAI_DAT.md](HUONG_DAN_CAI_DAT.md) | Installation guide (Vietnamese) |

---

## 🎯 Quick Rebuild Process

```bash
# 1. Clean
cd R1XiaozhiApp
./gradlew clean

# 2. Build
./gradlew assembleDebug

# 3. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Monitor
adb logcat -c && adb logcat | grep -E "Xiaozhi|Error"

# 5. Test
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

---

**Last Updated:** 2025-10-16  
**Maintained By:** Fullstack Developer Team
