# Guide to Setting Up GitHub Repository & CI/CD

## Table of Contents
1. [Create GitHub Repository](#1-create-github-repository)
2. [Upload code to GitHub](#2-upload-code-to-github)
3. [Configure GitHub Actions](#3-configure-github-actions)
4. [Create automatic releases](#4-create-automatic-releases)
5. [Use CI/CD](#5-use-cicd)

---

## 1. Create GitHub Repository

### Step 1.1: Create a new repository

1. Go to https://github.com/new
2. Fill in the information:
   - **Repository name**: `r1-xiaozhi` (or any name you prefer)
   - **Description**: `Xiaozhi Voice Assistant for Phicomm R1 - AI-powered smart speaker`
   - **Visibility**: 
     - ✅ **Public** (recommended - to allow community contributions)
     - ⚪ **Private** (if you want to keep it private)
3. Do **NOT** select:
   - ❌ Add a README file
   - ❌ Add .gitignore
   - ❌ Choose a license
4. Click **"Create repository"**

### Step 1.2: Save the repository URL

You will see a URL like:
```
https://github.com/YOUR_USERNAME/r1-xiaozhi.git
```

---

## 2. Upload code to GitHub

### Step 2.1: Initialize Git (if not already done)

Open a terminal/command prompt in the project directory (`f:/PHICOMM_R1/xiaozhi/r1xiaozhi`):

```bash
# Initialize git repository
git init

# Add all files
git add .

# Commit
git commit -m "Initial commit: Xiaozhi Voice Assistant for Phicomm R1"
```

### Step 2.2: Connect to GitHub

```bash
# Add remote repository (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/r1-xiaozhi.git

# Verify remote
git remote -v
```

### Step 2.3: Push code

```bash
# Push to GitHub
git branch -M main
git push -u origin main
```

**Note**: If prompted for authentication, use:
- **Username**: Your GitHub username
- **Password**: Personal Access Token (PAT)
  - Create a PAT at: https://github.com/settings/tokens
  - Select scopes: `repo`, `workflow`

---

## 3. Configure GitHub Actions

GitHub Actions is already pre-configured! You have 2 workflows:

### Workflow 1: Build APK (`.github/workflows/build.yml`)

**Runs automatically when:**
- ✅ Code is pushed to the `main`, `master`, or `develop` branch
- ✅ A Pull Request is created
- ✅ A tag `v*` is created (e.g., `v1.0.0`)
- ✅ Manual trigger (via the GitHub interface)

**Outputs:**
- Debug APK (retention: 30 days)
- Release APK (retention: 90 days)
- SHA256 checksums

### Workflow 2: Code Quality (`.github/workflows/test.yml`)

**Runs automatically when:**
- ✅ Code is pushed to the `main`, `master`, `develop` branch
- ✅ A Pull Request is created

**Checks:**
- Lint checks
- Unit tests
- Code analysis

### Step 3.1: Verify workflows

1. Go to: `https://github.com/YOUR_USERNAME/r1-xiaozhi/actions`
2. You will see workflows running
3. Click on a workflow to view progress

### Step 3.2: Download APK from Actions

After a successful build:

1. Go to the **Actions** tab
2. Click on the latest workflow run
3. Scroll down to the **Artifacts** section
4. Download:
   - `R1Xiaozhi-Debug-APK`
   - `R1Xiaozhi-Release-APK`
   - `Checksums`

---

## 4. Create automatic releases

### Step 4.1: Create a tag to trigger a release

```bash
# Create and push a version tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### Step 4.2: GitHub will automatically:

1. ✅ Build Release APK
2. ✅ Generate checksums
3. ✅ Create GitHub Release
4. ✅ Upload APK and checksums as release assets
5. ✅ Generate release notes

### Step 4.3: View the Release

Go to: `https://github.com/YOUR_USERNAME/r1-xiaozhi/releases`

The release will have:
- 📦 Release APK file
- 🔐 SHA256 checksums
- 📝 Release notes with changelog
- 📥 Download instructions

---

## 5. Use CI/CD

### 5.1. Basic workflow

**When developing:**
```bash
# 1. Make changes
git add .
git commit -m "feat: add new feature"

# 2. Push to trigger CI
git push origin main

# 3. GitHub Actions will automatically:
#    - Build APK
#    - Run tests
#    - Upload artifacts
```

**When releasing:**
```bash
# 1. Update version in build.gradle
# Open R1XiaozhiApp/app/build.gradle
# Increment versionCode and versionName

# 2. Commit changes
git add R1XiaozhiApp/app/build.gradle
git commit -m "chore: bump version to 1.0.1"
git push

# 3. Create tag
git tag -a v1.0.1 -m "Release version 1.0.1"
git push origin v1.0.1

# 4. GitHub will automatically create a Release with the APK
```

### 5.2. Manual trigger build

To trigger a build manually:

1. Go to the **Actions** tab
2. Select the **"Build Android APK"** workflow
3. Click the **"Run workflow"** button
4. Select the branch
5. Click **"Run workflow"**

### 5.3. Badges for README

Add build status badges to the README:

```markdown
[![Build](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/build.yml)
[![Tests](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/test.yml/badge.svg)](https://github.com/YOUR_USERNAME/r1-xiaozhi/actions/workflows/test.yml)
```

---

## 6. Advanced: Signing APK (Optional)

To sign the release APK with your keystore:

### Step 6.1: Create keystore

```bash
keytool -genkey -v -keystore r1xiaozhi.keystore -alias r1xiaozhi -keyalg RSA -keysize 2048 -validity 10000
```

### Step 6.2: Add secrets to GitHub

1. Go to **Settings** → **Secrets and variables** → **Actions**
2. Click **"New repository secret"**
3. Add:
   - `KEYSTORE_FILE`: Base64 of the keystore file
   - `KEYSTORE_PASSWORD`: Keystore password
   - `KEY_ALIAS`: Key alias
   - `KEY_PASSWORD`: Key password

```bash
# Convert keystore to base64
base64 r1xiaozhi.keystore > keystore.txt
# Copy the content of keystore.txt and paste it into KEYSTORE_FILE
```

### Step 6.3: Update build.yml

Uncomment the signing section in `.github/workflows/build.yml`

---

## 7. Troubleshooting

### Build failed: "Permission denied"

```bash
# Make gradlew executable
git update-index --chmod=+x R1XiaozhiApp/gradlew
git commit -m "fix: make gradlew executable"
git push
```

### Build failed: "SDK not found"

GitHub Actions sets up the SDK automatically. If the error persists:
- Check the JDK version in the workflow (must be JDK 8)
- Check the Android SDK version in build.gradle

### Artifacts cannot be downloaded

- Check retention days (default 30 days for debug, 90 days for release)
- Artifacts are automatically deleted after the retention period

### Release is not created automatically

- Verify tag format: must be `v*` (e.g., `v1.0.0`)
- Check permissions: `GITHUB_TOKEN` must have write access

---

## 8. Best Practices

### Versioning

Use Semantic Versioning:
- `v1.0.0` - Major release
- `v1.1.0` - Minor update (new features)
- `v1.1.1` - Patch (bug fixes)

### Branch Strategy

```
main/master     ← Production-ready code
  ↑
develop         ← Development branch
  ↑
feature/*       ← Feature branches
```

### Commit Messages

```bash
feat: Add new feature
fix: Fix bug
docs: Update documentation
chore: Update dependencies
refactor: Refactor code
test: Add tests
```

---

## 9. Monitoring

### Check build status

```bash
# Via GitHub CLI
gh run list

# View specific run
gh run view RUN_ID
```

### Download artifacts via CLI

```bash
# List artifacts
gh run download RUN_ID --list

# Download specific artifact
gh run download RUN_ID -n R1Xiaozhi-Release-APK
```

---

## Done!

Your repository now has:
- ✅ Automated builds on every push
- ✅ Automatic releases on tags
- ✅ Code quality checks
- ✅ APK artifacts available for download
- ✅ Professional CI/CD pipeline

**Next steps:**
1. Share repository with the community
2. Add contributors
3. Accept Pull Requests
4. Keep building awesome features!

---

**Repository URL example:**
```
https://github.com/YOUR_USERNAME/r1-xiaozhi
```

**Download latest release:**
```
https://github.com/YOUR_USERNAME/r1-xiaozhi/releases/latest
```
