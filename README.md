# Calyx Launcher

Calyx is a local-first Android home-screen launcher with a distinctive glass-inspired visual system, quick app-library navigation, and adaptable home-screen organization. Minimum supported version: Android 8.0 (API 26).

## v0.4.0 — Centers

### Notifications and privacy

- **Calyx Notification Center** opens from the clock-card **ALERTS** chip or by swiping down on the home surface. Notifications are grouped by app in newest-first order; tap a group heading to expand/collapse, tap a notification to open, swipe a clearable card to dismiss, or use its explicit dismiss, snooze, mute, clear-all, and app-provided inline-reply actions.
- **Notification badges** show active counts on home, dock, recent-app and App Library icons. Counts use active notifications, not an app's private unread-message state. Badges and the panel require optional Android Notification Access.
- **Mute in Calyx** filters selected apps from the Calyx panel and badge counts only; it does not silence or cancel notifications in Android.
- **Privacy Shield** hides notification message bodies inside Calyx. **Notification history** is off by default; when enabled it keeps at most 50 entries on-device, can be cleared in the panel, and is excluded from backups.
- The optional **daily digest** schedules a count-only reminder for currently active, lower-priority notifications. It uses an inexact daily alarm and requires Notification Access; Android 13+ also requires notification-posting permission. Declining that permission leaves the in-app panel available.

### Control Center, Permission Center, and Peek

- The clock-card **CTRL** chip opens the Calyx **Control Center**: configurable, reorderable and resizable tiles for flashlight, Do Not Disturb, rotation lock, Wi‑Fi/Bluetooth settings, timer, alarm, camera, calculator, quick note, QR scanner and accessibility magnifier settings; plus media/ringer volume, brightness, timeout and available notification media actions with album art when supplied.
- Android remains authoritative: Wi‑Fi opens Android's system panel rather than attempting a prohibited direct toggle on Android 10+; rotation, brightness and timeout require the user to grant **Modify system settings**; Do Not Disturb requires **Notification Policy Access**; flashlight asks for camera permission only when tapped. Missing companion apps are reported rather than faked.
- **Permission Center** explains each optional grant and opens the matching Android page. Calyx never silently grants sensitive access or changes system state at startup.
- Swipe inward from either screen edge or tap the clock-card **PEEK** chip for the **Peek Panel**, with up to eight user-selected app favorites and quick links to notifications, controls and local notes. Both entry points can be turned off in Settings.
- Optional local DND quiet hours use inexact daily alarms. The schedule restores the previous interruption filter when it ends unless the current filter has been changed manually; disable it from **Settings → Centers and privacy**.
- Quick notes, active-notification snapshots, counts and opt-in history are device-local. Quick notes and notification content are not included in portable launcher backups.

## v0.3.0 — feature build

### Home screen and clock

- Redesigned **live clock and date panel** with 12/24-hour time, localized date, and a UTC-offset badge.
- Three one-tap style-family presets: **Calyx Glass**, **Calyx Flow**, and **Calyx Dynamic**. The preset coordinates icon shape, folder presentation, drawer treatment, and motion; each can be fine-tuned afterward. Dynamic uses the system wallpaper's primary color where Android exposes it (API 27+), with a safe fallback.
- Multiple home pages, configurable 3–8-column grid, adjustable icons, and selectable page transitions.
- Long-press an app and choose **Move icon (drag)** to reorder it or move it between the home and dock.
- Create a folder from an app's home-screen menu by selecting another home app; rename, open, change its presentation, or dissolve it later. Folder styles include a grid mosaic, card tiles, and a layered icon preview.
- Configurable **4–7 item dock**; icons compact at higher capacities. Dragging a home tile onto the dock moves it there.

### App Library

- Search plus one-tap **All / Games / Social / Media / Tools** category chips. Categories are inferred from the Android game classification and app names; some apps may be grouped approximately.
- A recent-app strip plus switchable alphabetical / **frequently used first** ordering. Recent/frequency history stays on-device.
- Selectable full-screen, reachable-sheet, frosted, and inset-card drawer presentations; adjustable 3–6-column app grid.
- Hide an app from Calyx from its drawer menu; restore it through **Settings → Hidden apps**. Hiding does not uninstall the app.
- App info, uninstall, and supported launcher shortcuts remain available for individual apps.

### Widgets

- Optional built-in **clock/date**, **battery**, and **weather** cards in a horizontally scrolling strip.
- **Add Android widget** opens the system picker for widgets supplied by installed apps. Long-press a hosted widget in the strip to remove it.
- Weather uses a city entered by the user—**Calyx does not request or read device location**. The city is sent to Open-Meteo for geocoding and current conditions; tap the card to retry/refresh. The card identifies Open-Meteo as its data source.

### Backup, privacy, and compatibility

- Export/import `.calyxbackup` files for preferences, home/dock placement, folders, hidden-app selection, and built-in-card selection.
- Android widget IDs and recent/frequent-use history are excluded from the portable backup. Add hosted Android widgets again after restoring; usage history remains local to that installation.
- Launcher preferences are stored locally. Weather is the only v0.3 network feature; no runtime location permission, analytics, or background polling is added.
- v0.3 is a portrait-first phone layout; broad landscape/tablet adaptation is a later roadmap phase. Android's widget providers still determine their own rendering behavior.

> **Weather service licensing:** Open-Meteo's public endpoint is for non-commercial use. Their current pricing page describes a commercial-use licence and customer endpoint for commercial distribution. Check the terms before shipping Calyx commercially and provide the required attribution. See [Open-Meteo pricing/licensing](https://open-meteo.com/en/pricing), [forecast API](https://open-meteo.com/en/docs), and [geocoding API](https://open-meteo.com/en/docs/geocoding-api).

## Gestures and actions

| Gesture/action | Result |
| --- | --- |
| Swipe up | Open App Library |
| Swipe down on home | Open the Calyx Notification Center; Android's own shade remains available via the device's system-edge gesture |
| Swipe inward from either screen edge on home | Open the optional Peek Panel |
| Swipe down at top of App Library | Close the drawer |
| Double tap | Open App Library with search focused |
| Long press empty home area | Open Calyx Settings |
| Long press a home app | Open app actions; choose **Move icon (drag)** to begin a drag |
| Long press a home app → **Create folder** | Choose another home app and name the folder |
| Long press an App Library app | Add to home/dock, hide in Calyx, open app info, uninstall, or use supported shortcuts |
| Tap a folder | Open its app grid; tap a child app to launch it |
| Long press a weather card | Change its city; tap it to refresh |

## Build locally

Requires JDK 17, Gradle 8.9+, and Android SDK 34.

```sh
gradle assembleDebug
gradle testDebugUnitTest
gradle lintDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and uploads a debug APK artifact on pushes and manual runs.

## Signed releases

Release signing keys are **never committed**. Before creating a `v*` tag, configure these GitHub Actions repository secrets:

- `CALYX_KEYSTORE_BASE64` — base64-encoded Android release keystore
- `CALYX_KEYSTORE_PASSWORD`
- `CALYX_KEY_ALIAS`
- `CALYX_KEY_PASSWORD`

Pushing a version tag such as `v0.4.0` builds a signed release APK and attaches it to a GitHub Release. Keep a secure offline copy of the keystore; updates signed with a different key cannot update existing installs.

## Android notes

- Minimum OS is Android 8.0 (API 26). Wallpaper-derived Dynamic colors are available from API 27; Android 12+ supports real backdrop blur.
- App shortcuts appear only when an app publishes them and Android exposes them to Calyx.
- Android widget layouts are controlled by their provider; Calyx hosts them in a horizontally scrollable card strip.
- Calyx's Notification Center is a launcher-only surface, not a replacement for Android's system notification shade. Notification Access is optional and can be revoked in Android settings.
- DND, digest, camera/flashlight, and system-write access are opt-in. Android may delay inexact schedules, and device manufacturers may restrict or vary system panels.

## Project structure

```text
app/src/main/java/com/calyx/launcher/  Kotlin launcher implementation
  CentersUi.kt                         Notification, Control, Permission and Peek panels
  NotificationCenterService.kt         Optional notification listener and local history
  CenterReceivers.kt                    Opt-in inexact digest and DND schedules
app/src/main/res/                       Android layouts and resources
.github/workflows/                      Debug CI and signed-release workflows
calyx-source.zip                        Maintained source bundle for the phone-based upload workflow
```
