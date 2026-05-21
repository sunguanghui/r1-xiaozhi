# Testing Guide for the Xiaozhi App on Phicomm R1

## Project Overview

**Repository**: https://github.com/xuan2261/r1-xiaozhi  
**Latest Commit**: `7e81b7d` - UX improvements with copy button  
**Build Status**: ⏳ Building on GitHub Actions

### Key Commits

1. **`9abb9cd`** - ESP32-based pairing with error handling
2. **`4c52fcf`** - findViewById type cast fix (API 22)
3. **`7e81b7d`** - UX improvements (Copy button + instructions)

## Preparation

### 1. Download APK

After GitHub Actions finishes building, download from:
```
https://github.com/xuan2261/r1-xiaozhi/actions
→ Select the latest workflow run
→ Download the "app-debug" artifact
→ Unzip to get app-debug.apk
```

### 2. Install on R1

**Via ADB**:
```bash
# Connect R1 via USB or WiFi
adb devices

# Install APK
adb install -r app-debug.apk

# Launch app
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

**Via USB**:
1. Copy APK to a USB drive
2. Plug the USB drive into the R1
3. Use the file manager to install the APK

### 3. Permissions

The app requires the following permissions (auto-granted in code):
- `INTERNET` - WebSocket connection
- `ACCESS_WIFI_STATE` - Get MAC address
- `ACCESS_NETWORK_STATE` - Check connectivity
- `RECORD_AUDIO` - Voice recognition
- `WRITE_EXTERNAL_STORAGE` - Save audio cache

## Test Cases

### Test 1: Pairing Flow (Happy Path) ✅

**Objective**: Verify that pairing succeeds with the correct flow

**Steps**:
1. Launch the app for the first time
2. Observe the pairing code displayed (e.g., "DD EE FF")
3. Click "📋 Copy Code"
4. Verify the toast "✓ Copied code: DDEEFF"
5. Open a browser → `https://console.xiaozhi.ai`
6. Log in → Select an agent → "Add Device"
7. Paste the code "DDEEFF" → Submit
8. Wait for "Device added successfully"
9. Return to the app → Click "Connect"
10. Wait for the status "✓ Paired successfully!"

**Expected**:
- ✅ Code displayed in correct format (6 uppercase characters)
- ✅ Copy works
- ✅ Console accepts the code
- ✅ WebSocket connects successfully
- ✅ Authorize handshake success (code=0)
- ✅ UI updated: "Paired"
- ✅ Instructions hidden
- ✅ Connect button disabled

**Logs to check**:
```bash
adb logcat | grep -E "MainActivity|PairingCode|XiaozhiConnection"
```

Expected:
```
=== PAIRING CODE DEBUG ===
Device ID: AABBCCDDEEFF
Pairing Code: DDEEFF
=========================
WebSocket connected
Sending Authorize handshake: {...}
Pairing SUCCESS!
Device marked as paired
```

### Test 2: Wrong Flow (Connect First) ❌

**Objective**: Verify error handling when the user connects before adding the code

**Steps**:
1. Launch the app
2. Observe the pairing code
3. Do **NOT** add it to the console
4. Click "Connect" immediately
5. Observe the status

**Expected**:
- ❌ Server rejects with an error code
- ✅ App displays: "✗ Pairing failed: Invalid authentication code"
- ✅ Retry logic kicks in (if error is retryable)
- ✅ Connect button re-enabled after failure

**Logs**:
```
Pairing FAILED: code=xxx (Invalid pairing code)
```

### Test 3: Reset Pairing 🔄

**Objective**: Verify that reset works correctly

**Steps**:
1. After successfully pairing (Test 1)
2. Click "Reset Pairing"
3. Observe UI changes

**Expected**:
- ✅ WebSocket disconnects
- ✅ Pairing status reset
- ✅ Pairing code displayed again
- ✅ Instructions visible again
- ✅ Copy button visible again
- ✅ Connect button enabled
- ✅ Toast: "Reset done - please pair again"

### Test 4: Network Issues 🌐

**Objective**: Verify that retry logic works

**Steps**:
1. Disable WiFi on R1
2. Click "Connect"
3. Observe behavior

**Expected**:
- ❌ Connection fails
- ✅ Retry #1 after 2s
- ✅ Retry #2 after 4s
- ✅ Retry #3 after 8s
- ❌ Give up after 3 retries
- ✅ Error message: "Server not responding"

**Logs**:
```
WebSocket error: ...
Scheduling reconnect #1 in 2000ms
Retrying connection...
Max retries reached. Giving up.
```

### Test 5: Auto-Reconnect 🔌

**Objective**: Verify auto-reconnect when paired

**Steps**:
1. After pairing (Test 1)
2. Disable WiFi for 10s
3. Enable WiFi
4. Observe behavior

**Expected**:
- ⚠ Status: "Connection lost"
- 🔄 Auto retry kicks in
- ✅ Reconnects successfully
- ✅ Status: "Connected - Authenticating..."
- ✅ Auto send Authorize handshake
- ✅ Status: "✓ Paired successfully!"

### Test 6: Device ID Consistency 🔑

**Objective**: Verify device ID is stable across reboots

**Steps**:
1. Note the pairing code on the 1st run (e.g., DDEEFF)
2. Force stop the app: `adb shell am force-stop com.phicomm.r1.xiaozhi`
3. Relaunch the app
4. Note the pairing code on the 2nd run

**Expected**:
- ✅ Code is the same both times (cached in SharedPreferences)
- ✅ Device ID is stable

**Logs**:
```
Device ID: AABBCCDDEEFF  // 1st run
Device ID: AABBCCDDEEFF  // 2nd run - SAME!
```

### Test 7: Voice Recognition Integration 🎤

**Objective**: Verify the voice service works

**Steps**:
1. After pairing
2. Say the wake word (if configured)
3. Or trigger manually for testing

**Expected**:
- ✅ VoiceRecognitionService active
- ✅ Audio capture working
- ✅ Voice data sent via WebSocket
- ✅ Response received from Xiaozhi
- ✅ AudioPlaybackService plays the response

**Note**: This test requires a physical device with a microphone

### Test 8: HTTP Server 🌐

**Objective**: Verify the HTTP server exposes data

**Steps**:
1. App running
2. Get R1 IP: `adb shell ip addr show wlan0`
3. From a PC on the same network: `curl http://[R1_IP]:8088/status`

**Expected**:
```json
{
  "status": "paired",
  "device_id": "AABBCCDDEEFF",
  "pairing_code": "DDEEFF",
  "connected": true
}
```

### Test 9: LED Control 💡

**Objective**: Verify LED service (if R1 has LEDs)

**Expected**:
- 🔴 RED: Not paired
- 🟢 GREEN: Paired successfully
- 🔵 BLUE: Listening
- 🟡 YELLOW: Processing

**Note**: Depends on R1 hardware

### Test 10: Persistence Across Reboot 🔄

**Objective**: Verify the app auto-starts after reboot

**Steps**:
1. Successfully paired
2. Reboot R1: `adb reboot`
3. Wait for boot to complete
4. Check app status

**Expected**:
- ✅ BootReceiver triggers
- ✅ Services auto-start
- ✅ Auto reconnect WebSocket
- ✅ Pairing status preserved

## Debug Tools

### LogCat Filters

**All logs**:
```bash
adb logcat | grep -E "Xiaozhi|Pairing|MainActivity"
```

**Errors only**:
```bash
adb logcat *:E | grep Xiaozhi
```

**Connection events**:
```bash
adb logcat | grep "XiaozhiConnection"
```

**Pairing debug**:
```bash
adb logcat | grep "PAIRING CODE DEBUG"
```

### Clear App Data

Completely reset the app:
```bash
adb shell pm clear com.phicomm.r1.xiaozhi
```

### Check Services Running

```bash
adb shell dumpsys activity services | grep xiaozhi
```

### Network Traffic

Monitor WebSocket:
```bash
adb shell tcpdump -i wlan0 -w /sdcard/capture.pcap
# Analyze with Wireshark
```

## Expected Build Artifacts

After GitHub Actions finishes building, verify:

1. **APK Size**: ~2-3 MB
2. **Min SDK**: 22 (Android 5.1)
3. **Target SDK**: 22
4. **Permissions**: 5 permissions declared
5. **Services**: 5 background services
6. **Activities**: 2 (MainActivity, SettingsActivity)

## Known Issues & Workarounds

### Issue 1: MAC Address 02:00:00:00:00:00

**Symptom**: Fake MAC on some devices

**Workaround**: Fallback to Android ID (already implemented)

**Verify**:
```bash
adb logcat | grep "Fake/invalid MAC detected"
```

### Issue 2: WebSocket Connection Refused

**Symptom**: Cannot connect to xiaozhi.me

**Check**:
```bash
# Test from R1
adb shell ping xiaozhi.me
adb shell curl -I https://xiaozhi.me
```

**Workaround**: Check firewall, proxy settings

### Issue 3: Pairing Code Expired

**Symptom**: Server rejects with "Code expired"

**Root cause**: Code is only valid for 10 minutes

**Workaround**: Generate a new code (reset pairing)

## Performance Benchmarks

**Expected metrics**:
- Cold start: <2s
- Pairing code generation: <100ms
- WebSocket connect: <1s
- Authorize handshake: <500ms
- Total pairing time: <5s

**Monitor with**:
```bash
adb shell dumpsys gfxinfo com.phicomm.r1.xiaozhi
```

## Security Checklist

- [x] No hardcoded credentials
- [x] HTTPS/WSS only
- [x] Permissions justified
- [x] No plaintext storage
- [x] Proper error messages (no info leakage)

## Next Steps

After testing is complete:

1. **Report bugs**: Create GitHub Issues with full logs
2. **Performance tuning**: If bottlenecks are found
3. **Feature requests**: Based on user feedback
4. **Documentation**: Update README with real-world findings

## Support

**Issues**: https://github.com/xuan2261/r1-xiaozhi/issues  
**Docs**: See `README.md`, `INSTALLATION_GUIDE.md`  
**Debug**: See `PAIRING_DEBUG_GUIDE.md`

---

**Happy Testing! 🚀**
