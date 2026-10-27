# Release Signing Setup

This document describes how to set up secure Android release signing for the Helper app CI/CD pipeline.

## Current Status

- **Signing configuration**: Added to `app/build.gradle.kts` - reads from environment variables
- **Environment variables required**: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
- **Release builds**: Will fail if signing configuration is missing (no silent unsigned releases)
- **Debug builds**: Continue to work without release signing secrets
- **CI workflows**: 
  - `ci.yml` - runs tests and debug builds on push/PR to main (no signing secrets needed)
  - `release.yml` - runs on version tags `v*`, requires production environment approval, builds signed release APK

## 1. Keystore Management

### Check if you already have a keystore

Before creating a new keystore, check if one already exists. Look in common locations outside the repo:
- `~/keystores/`
- `~/android/`
- Any backup locations you may have used previously

**Do NOT create a new keystore if you already have one and know its credentials.** Using a different keystore will prevent updates to the Play Store if the app was previously published.

### Generate a new keystore (only if approved)

If you need to create a new keystore and have explicit approval, use this command (run locally, outside the repo):

```bash
keytool -genkey -v \
  -keystore ~/keystores/helper-release.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias helper-key
```

**Important notes:**
- Store the keystore outside the Git repository (e.g., `~/keystores/`)
- Set restrictive permissions: `chmod 600 ~/keystores/helper-release.jks`
- Use strong, unique passwords
- Record the alias name you choose
- The keystore will be valid for ~27 years (10000 days) - ensure you can access it for the app's lifetime
- **Never commit the keystore to Git**

### Backup the keystore securely

Your keystore is critical. If lost, you cannot update your app on the Play Store.

Recommended backup strategies:
1. **Encrypted backup**: Copy to an encrypted drive or use encrypted cloud storage (e.g., encrypted zip with strong password)
2. **Secure password manager**: Some password managers can store files securely
3. **Physical backup**: Store on an encrypted USB drive in a secure location
4. **Multiple locations**: Keep copies in at least 2 different secure locations

**Never store the keystore in:**
- Git repository
- Unencrypted cloud storage
- Public locations
- Email or messaging apps

## 2. GitHub Secrets Setup

Add the following secrets to your GitHub repository (Settings → Secrets and variables → Actions):

| Secret Name | Value | Notes |
|-------------|-------|-------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore file | Encode the entire .jks file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password | Password for the keystore |
| `ANDROID_KEY_ALIAS` | Key alias | The alias used when creating the key |
| `ANDROID_KEY_PASSWORD` | Key password | Password for the specific key (often same as keystore password) |

### Encode the keystore to Base64

Run this command locally (it will not print the full encoded data to avoid cluttering output):

```bash
base64 -w 0 ~/keystores/helper-release.jks | pbcopy  # macOS
# or
base64 -w 0 ~/keystores/helper-release.jks | xclip -selection clipboard  # Linux
```

On some systems without `-w 0`, use `base64 ~/keystores/helper-release.jks | tr -d '\n'`. Copy the output and paste it as the value for `ANDROID_KEYSTORE_BASE64` secret in GitHub.

## 3. GitHub Environment Setup

Configure the `production` environment with required reviewers:

1. Go to repository Settings → Environments
2. Create or select the `production` environment
3. Enable "Required reviewers" and add approvers who must approve before release builds run
4. Optionally configure deployment branches to restrict to tags

The release workflow references `environment: production`, so it will wait for approval before building and publishing.

## 4. Local Testing

### Test a signed release build locally

```bash
export ANDROID_KEYSTORE_PATH=~/keystores/helper-release.jks
export ANDROID_KEYSTORE_PASSWORD="your_keystore_password"
export ANDROID_KEY_ALIAS="your_key_alias"
export ANDROID_KEY_PASSWORD="your_key_password"

./gradlew assembleRelease --console=plain
```

The APK will be at `app/build/outputs/apk/release/app-release.apk`.

### Verify the APK signature

```bash
# Using apksigner (from Android SDK build-tools)
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk

# Check package info
aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -E "package: name=|versionCode=|versionName="
```

## 5. Triggering a Release

1. Ensure all changes are committed and pushed to `main`
2. Create and push a version tag:
```bash
git tag v1.2.0
git push origin v1.2.0
```
3. The release workflow will trigger, wait for approval in the `production` environment, build the signed APK, verify it, and create a GitHub Release
4. The signed APK will be attached to the release

**Note**: Tags are not created automatically by the workflow - you must create them manually after explicit approval to release.

## 6. Security Notes

- Keystore is stored outside Git and excluded via `.gitignore`
- In CI, keystore is decoded to `/tmp/` with `chmod 600` and deleted after the job completes (even on failure)
- Secrets are passed via environment variables, never as command-line arguments
- The release job has `contents: write` permission (least privilege - only the release job)
- CI builds on PRs/pushes don't require signing secrets
- Release builds will fail explicitly if signing config is missing
- No secrets are logged or exposed in workflow output

## Troubleshooting

- **"Release signing requires..." error**: Make sure all four environment variables are set
- **Signature verification fails**: Check that the keystore and passwords are correct
- **Workflow waiting for approval**: Go to Actions tab and approve the deployment in the production environment
- **APK not attached to release**: Check workflow logs for errors in the publish step
