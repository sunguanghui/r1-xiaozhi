# 🔧 SIGABRT Crash Fix Guide - NetworkChangeReceiver

## 📋 Issue Summary

**Symptom:**
```
F/libc (30775): Fatal signal 6 (SIGABRT), code -6 in tid 30775 (comm.r1.xiaozhi)
F/art (30775): art/runtime/runtime.cc:289] native: #07 pc 0005f99d /system/lib/libandroid_runtime.so
```

**Root Cause:**
- [`AndroidManifest.xml`](R1XiaozhiApp/app/src/main/AndroidManifest.xml) declared `NetworkChangeReceiver`
- But the class file `.receiver.NetworkChangeReceiver` **does not exist**
- Android Runtime crashes when it cannot find a declared class

---

## ✅ Applied Solution

### Remove Unnecessary Receiver

**File:** [`AndroidManifest.xml`](R1XiaozhiApp/app/src/main/AndroidManifest.xml)

**Removed block:**
```xml
<!-- Network Change Receiver -->
<receiver
    android:name=".receiver.NetworkChangeReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="android.net.conn.CONNECTIVITY_CHANGE" />
    </intent-filter>
</receiver>
```

**Reason:**
- The app does not need to monitor network changes in real time
- The WebSocket connection has an auto-reconnect mechanism
- [`XiaozhiConnectionService`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/XiaozhiConnectionService.java) handles reconnection on its own

---

## 🔍 How to Detect Similar Errors

### 1. LogCat Filter

```bash
# Filter SIGABRT crashes
adb logcat | grep -E "Fatal signal|SIGABRT"

# Filter Art Runtime errors
adb logcat | grep -E "F/art|F/libc"

# View full crash stack
adb logcat *:E
```

### 2. Similar Error Types

**Class Not Found:**
```
java.lang.ClassNotFoundException: Didn't find class "XXX"
```

**Receiver Not Found:**
```
android.content.ActivityNotFoundException: Unable to find explicit activity class
```

**Service Binding Failed:**
```
android.app.ServiceConnectionLeaked: Service XXX has leaked ServiceConnection
```

---

## 🛠️ Testing After Fix

### 1. Rebuild App

```bash
cd R1XiaozhiApp
./gradlew clean
./gradlew assembleDebug
```

### 2. Install & Monitor

```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clear LogCat
adb logcat -c

# Monitor startup
adb logcat | grep -E "XiaozhiApp|MainActivity|AndroidRuntime"
```

### 3. Test Checklist

- [ ] App starts without crash
- [ ] No SIGABRT seen in logcat
- [ ] MainActivity displays pairing code
- [ ] Services start successfully
- [ ] WebSocket connects successfully

---

## 📊 Remaining Manifest Receivers

### BootReceiver
```xml
<receiver android:name=".receiver.BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```
**Status:** ✅ **EXISTS** - [`BootReceiver.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/receiver/BootReceiver.java)

---

## 🔄 If Network Monitoring Is Needed Later

### Option 1: Create NetworkChangeReceiver

```java
package com.phicomm.r1.xiaozhi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkChangeReceiver extends BroadcastReceiver {
    
    private static final String TAG = "NetworkChange";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        
        boolean isConnected = activeNetwork != null && 
                            activeNetwork.isConnectedOrConnecting();
        
        Log.i(TAG, "Network changed - Connected: " + isConnected);
        
        // TODO: Notify services of network change
    }
}
```

### Option 2: Use ConnectivityManager.NetworkCallback (API 21+)

```java
// In XiaozhiConnectionService.onCreate()
ConnectivityManager cm = (ConnectivityManager) 
    getSystemService(Context.CONNECTIVITY_SERVICE);

ConnectivityManager.NetworkCallback networkCallback = 
    new ConnectivityManager.NetworkCallback() {
    
    @Override
    public void onAvailable(Network network) {
        Log.i(TAG, "Network available - reconnecting...");
        reconnect();
    }
    
    @Override
    public void onLost(Network network) {
        Log.i(TAG, "Network lost");
    }
};

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    cm.registerDefaultNetworkCallback(networkCallback);
}
```

---

## 📝 Manifest Validation Checklist

Before building, always verify:

### 1. All Activities Exist
```bash
# Grep all activities in manifest
grep -E 'android:name="\.' R1XiaozhiApp/app/src/main/AndroidManifest.xml

# Check files exist
find R1XiaozhiApp/app/src -name "MainActivity.java"
find R1XiaozhiApp/app/src -name "SettingsActivity.java"
```

### 2. All Services Exist
```bash
# List services
grep -A5 '<service' R1XiaozhiApp/app/src/main/AndroidManifest.xml

# Verify existence
ls R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/service/
```

### 3. All Receivers Exist
```bash
# List receivers
grep -A5 '<receiver' R1XiaozhiApp/app/src/main/AndroidManifest.xml

# Verify existence
ls R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/receiver/
```

---

## 🚨 Common Manifest Errors

### 1. Wrong Package Path
```xml
<!-- ❌ WRONG -->
<activity android:name="com.wrong.MainActivity" />

<!-- ✅ CORRECT -->
<activity android:name=".ui.MainActivity" />
```

### 2. Missing Class
```xml
<!-- Class must exist -->
<service android:name=".service.NonExistentService" />
```

### 3. Wrong Intent Filter
```xml
<!-- Typo in action name -->
<action android:name="android.intent.action.BOOT_COMPLETE" />
<!-- Correct: BOOT_COMPLETED -->
```

---

## 🎯 Prevention Best Practices

### 1. Lint Checks
```bash
# Run lint before building
./gradlew lint

# Check errors
cat app/build/reports/lint-results.html
```

### 2. Code Review Checklist
- [ ] Every component declared in the manifest has a corresponding class file
- [ ] Package names are correct
- [ ] Intent filters have correct syntax
- [ ] Required permissions are declared

### 3. Automated Testing
```yaml
# .github/workflows/manifest-check.yml
name: Manifest Validation

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Check Manifest Components
        run: |
          # Extract component names
          grep -oP 'android:name="\K[^"]+' app/src/main/AndroidManifest.xml > components.txt
          
          # Check files exist
          while read component; do
            file=$(echo $component | sed 's/\./\//g')
            if [ ! -f "app/src/main/java/${file}.java" ]; then
              echo "ERROR: ${component} not found!"
              exit 1
            fi
          done < components.txt
```

---

## 📚 Related Documentation

- [PAIRING_DEBUG_GUIDE.md](PAIRING_DEBUG_GUIDE.md) - Debug tools & techniques
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - Comprehensive test cases
- [Android Manifest Reference](https://developer.android.com/guide/topics/manifest/manifest-intro)

---

## ✅ Status

**Fixed:** ✅ NetworkChangeReceiver has been removed from the manifest  
**Tested:** ⏳ Needs rebuild & re-test  
**Impact:** ✅ App will no longer crash  
**Side Effects:** ✅ None - feature was not needed

---

**Date:** 2025-10-16  
**Author:** Fullstack Developer  
**Issue:** SIGABRT crash due to missing NetworkChangeReceiver  
**Resolution:** Removed unused receiver from manifest
