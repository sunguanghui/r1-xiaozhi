# 🔧 Fix UTF-8 Encoding Crash - JNI Error

## 🔴 Newly Discovered Issue

**After fixing NetworkChangeReceiver**, the app still crashes with a UTF-8 error:

```
F/art: JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8: illegal start byte 0xf0
F/art: at com.phicomm.r1.xiaozhi.ui.MainActivity.onCreate(MainActivity.java:140)
```

## 📋 Root Cause Analysis

### 1. Modified UTF-8 vs Standard UTF-8

**Android JNI** uses **Modified UTF-8** (CESU-8), which differs from standard UTF-8:
- **Standard UTF-8**: Emoji and 4-byte characters are valid
- **Modified UTF-8**: Only supports 1-3 byte characters
- Byte `0xf0` = start of a 4-byte emoji → **INVALID** in JNI

### 2. Characters That Cause Crashes

**In our code:**

#### Layout XML - activity_main.xml
```xml
<!-- ❌ HAS EMOJI -->
<Button android:text="📋 Sao Chép Mã" />

<!-- ✅ FIXED -->
<Button android:text="Sao Chep Ma" />
```

#### MainActivity.java
```java
// ❌ HAS EMOJI & SPECIAL CHARS
updateStatus("✓ Đã ghép nối thành công!");
updateStatus("✗ Ghép nối thất bại");
updateStatus("⚠ Chưa ghép nối");

// ✅ FIXED - ASCII only
updateStatus("[OK] Da ghep noi thanh cong!");
updateStatus("[FAIL] Ghep noi that bai");
updateStatus("[!] Chua ghep noi");
```

## ✅ Applied Solution

### 1. Replace Emoji in Layout

**File:** [`activity_main.xml`](R1XiaozhiApp/app/src/main/res/layout/activity_main.xml:35)

```diff
- android:text="📋 Sao Chép Mã"
+ android:text="Sao Chep Ma"
```

### 2. Replace Emoji/Special Chars in Java

**File:** [`MainActivity.java`](R1XiaozhiApp/app/src/main/java/com/phicomm/r1/xiaozhi/ui/MainActivity.java)

| Line | Old (Emoji) | New (ASCII) |
|------|-----------|-------------|
| 61 | `Da ket noi - Dang xac thuc...` | `Da ket noi - Dang xac thuc...` |
| 72 | `Mat ket noi` | `Mat ket noi` |
| 83 | `✓ Da ghep noi thanh cong!` | `[OK] Da ghep noi thanh cong!` |
| 87 | `Da ghep noi` | `Da Ghep Noi` |
| 98 | `✗ Ghep noi that bai` | `[FAIL] Ghep noi that bai` |
| 117 | `Loi:` | `Loi:` |
| 193 | `✓ Da ghep noi - San sang` | `[OK] Da ghep noi - San sang` |
| 194 | `✓ Da Ghep Noi` | `[OK] Da Ghep Noi` |
| 204 | `⚠ Chua ghep noi` | `[!] Chua ghep noi` |
| 229 | `✓ Da sao chep` | `[OK] Da sao chep` |
| 240 | `chua san sang` | `chua san sang` |
| 244 | `Dang ket noi...` | `Dang ket noi...` |
| 265 | `Da reset - Vui long` | `Da reset - Vui long` |

### 3. Remove Diacritics (Vietnamese Marks)

**Reason:** Vietnamese diacritical marks can also cause issues with JNI on API 22
- `á, à, ả, ã, ạ` → `a`
- `đ` → `d`
- `ư, ơ` → `u, o`

**Applied to:** All text in Java code

## 🔍 Full Codebase Check

### Files Checked

```bash
# Search non-ASCII characters
grep -r '[^\x00-\x7F]' R1XiaozhiApp/app/src/main/java/ > utf8_chars.txt
```

**Result:** 93 matches - All handled in MainActivity and layout

### Safe Files (Comments Only)

The following files contain UTF-8 only in **comments** → Safe:
- `XiaozhiApplication.java` - Vietnamese comments
- `PairingCodeGenerator.java` - Comments
- `ErrorCodes.java` - Messages in strings (not passed through JNI)
- `XiaozhiConnectionService.java` - Comments
- `LEDControlService.java` - Comments
- `VoiceRecognitionService.java` - Comments

## 📊 Impact Assessment

### ✅ Fixed
- MainActivity UI text → ASCII only
- Button labels → ASCII only
- Status messages → ASCII only
- Toast messages → ASCII only

### ⚠️ Still Has UTF-8 (Safe)
- `ErrorCodes.java` messages - OK because they are only used in String, not passed through JNI
- Code comments - OK because they are not compiled into runtime
- `strings.xml` - OK because the Android resource system handles it

### 🎯 Best Practice

**New rules:**
1. **Java code runtime strings**: ASCII only
2. **XML resources**: OK to use UTF-8 (Android handles it)
3. **Comments**: OK to use any encoding
4. **Error messages**: Use string resources, do not hardcode

## 🔨 Build & Test

### Build Command (JAVA_HOME required)

```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_xxx
cd R1XiaozhiApp
gradlew.bat clean assembleDebug

# Linux/Mac
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk
cd R1XiaozhiApp
./gradlew clean assembleDebug
```

### Install & Test

```bash
# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Monitor startup
adb logcat -c
adb logcat | grep -E "Xiaozhi|JNI|UTF-8|SIGABRT"

# Verify: No UTF-8 errors
# Expected: App starts successfully
```

## 📚 Technical Deep Dive

### Modified UTF-8 (CESU-8)

**Differences from Standard UTF-8:**

```
Character: 😀 (U+1F600)
Standard UTF-8:  F0 9F 98 80 (4 bytes)
Modified UTF-8:  INVALID! (JNI crashes)

Character: ✓ (U+2713)
Standard UTF-8:  E2 9C 93 (3 bytes)
Modified UTF-8:  E2 9C 93 (OK, but risky in old API)

Character: A (U+0041)
Standard UTF-8:  41 (1 byte)
Modified UTF-8:  41 (OK)
```

### Why Android API 22 More Strict?

**API Levels:**
- **API 22 (Lollipop 5.1)**: Strict JNI checks, crashes on invalid UTF-8
- **API 23+**: More lenient, better error handling
- **API 26+**: Full UTF-8 support in most cases

**Solution:** Stick to ASCII for API 22 compatibility

## 🎓 Lessons Learned

### 1. Always Test on Target API

```java
// ❌ BAD - Emoji looks nice but crashes
updateStatus("✓ Success!");

// ✅ GOOD - ASCII always works
updateStatus("[OK] Success!");
```

### 2. Use String Resources

```xml
<!-- strings.xml - Android handles encoding -->
<string name="status_success">Connected successfully</string>

<!-- Java - Load from resources -->
statusText.setText(R.string.status_success); // Safe!
```

### 3. Test on Real Device

- The emulator may **NOT** catch this error
- A real device (especially API 22) will crash
- Always test on the target device

## 🚀 Next Steps

### 1. Set JAVA_HOME

```bash
# Find Java installation
java -version

# Windows
setx JAVA_HOME "C:\Program Files\Java\jdk1.8.0_291"

# Add to PATH
setx PATH "%PATH%;%JAVA_HOME%\bin"
```

### 2. Rebuild App

```bash
cd R1XiaozhiApp
gradlew.bat clean
gradlew.bat assembleDebug
```

### 3. Install & Test

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.phicomm.r1.xiaozhi/.ui.MainActivity
```

### 4. Monitor Logs

```bash
# No UTF-8 errors expected
adb logcat | grep -i "utf\|jni\|sigabrt"

# Should see:
# I/XiaozhiApp: Application started
# I/MainActivity: MainActivity created
# (No crashes)
```

## 📋 Checklist

- [x] Fixed emoji in layout XML
- [x] Replaced ✓, ✗, ⚠ with [OK], [FAIL], [!]
- [x] Removed Vietnamese diacritics from runtime strings
- [x] Verified comments are OK (do not affect runtime)
- [x] Documented best practices
- [ ] Set JAVA_HOME to build
- [ ] Test on real device
- [ ] Verify app does not crash

## 📚 Related Documentation

- [CRASH_FIX_SIGABRT.md](CRASH_FIX_SIGABRT.md) - NetworkChangeReceiver fix
- [QUICK_FIX_CHECKLIST.md](QUICK_FIX_CHECKLIST.md) - Common issues
- [Android Modified UTF-8 Spec](https://docs.oracle.com/javase/8/docs/api/java/io/DataInput.html#modified-utf-8)

---

**Date:** 2025-10-16  
**Issue:** JNI UTF-8 encoding error - illegal start byte 0xf0  
**Root Cause:** Emoji and 4-byte UTF-8 chars in Java/XML  
**Solution:** Replace all with ASCII-only text  
**Status:** ✅ Code fixed, rebuild & test required
