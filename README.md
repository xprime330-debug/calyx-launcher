# Calyx Launcher

Calyx is an Android home-screen launcher focused on an original, customizable visual identity, smooth everyday interactions, and local-first preferences. It is designed for Android 8.0 (API 26) and newer.

## v0.2.0

- **Home organization:** multiple pages, 3–8-column grid, long-press app actions, reorder by choosing **Move icon (drag)** and dropping onto a home slot or dock.
- **Dock:** up to four apps; drag a home icon to the dock or use the app menu to add/remove apps.
- **Drawer:** app search, app shortcuts where supported by the app and Android version, and an explicit no-results state.
- **Appearance:** Light, Dark and Colorful themes; accent presets; squircle, circle, rounded-square and teardrop icon shapes; adjustable icon size.
- **Motion:** Slide, Fade and Depth page transitions; adjustable drawer animation speed.
- **Drawer surface:** Android 12+ blurs the home content behind the drawer when enabled; older versions use a translucent surface fallback.
- **Backup/restore:** export and import local `.calyxbackup` files for Calyx preferences and home/dock app placement. Backups do not contain app APKs or personal data.
- **Privacy:** launcher settings are stored locally in Android app preferences; no analytics or network requests are added by this update.

## Gestures and actions

| Gesture/action | Result |
| --- | --- |
| Swipe up | Open app drawer |
| Swipe down on home | Request the Android notification panel (device/system dependent) |
| Swipe down at top of drawer | Close drawer |
| Double tap | Open drawer with search focused |
| Long press empty home area | Open Calyx Settings |
| Long press app | App actions; choose **Move icon (drag)** to start a drag, then drop on a home slot or dock |
| Long press app in drawer | Add to home/dock, app info, uninstall, and supported app shortcuts |
| Back/Home | Close drawer or return to first home page |

## Build locally

Requires JDK 17 and Gradle 8.9+.

```sh
gradle assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and uploads a debug APK artifact on pushes and manual runs.

## Signed releases

Release signing keys are **never committed**. Before creating a `v*` tag, configure these GitHub Actions repository secrets:

- `CALYX_KEYSTORE_BASE64` — base64-encoded Android release keystore
- `CALYX_KEYSTORE_PASSWORD`
- `CALYX_KEY_ALIAS`
- `CALYX_KEY_PASSWORD`

Pushing a version tag such as `v0.2.0` builds the signed release APK and attaches it to a GitHub Release. Keep a secure offline backup of the keystore; updates signed with a different key will not update existing installs.

## Android compatibility notes

- Android 12+ supports the drawer's backdrop blur; Android 8–11 receive a translucent fallback.
- App-provided shortcuts appear only for apps that publish launcher shortcuts and when Android exposes them to Calyx.
- The system notification shade is requested through Android and is not replaced by Calyx.

## Project structure

```text
app/src/main/java/com/calyx/launcher/  Kotlin launcher implementation
app/src/main/res/                       Android resources
.github/workflows/                      Debug CI and signed-release workflows
calyx-source.zip                        Source bundle for the phone-based upload workflow
```
