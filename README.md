# Calyx Launcher

Calyx is a local-first Android home-screen launcher with a distinctive glass-inspired visual system, quick app-library navigation, and adaptable home-screen organization. Minimum supported version: Android 8.0 (API 26).

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
| Swipe down on home | Request the Android notification panel (system/device dependent) |
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
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and uploads a debug APK artifact on pushes and manual runs.

## Signed releases

Release signing keys are **never committed**. Before creating a `v*` tag, configure these GitHub Actions repository secrets:

- `CALYX_KEYSTORE_BASE64` — base64-encoded Android release keystore
- `CALYX_KEYSTORE_PASSWORD`
- `CALYX_KEY_ALIAS`
- `CALYX_KEY_PASSWORD`

Pushing a version tag such as `v0.3.0` builds a signed release APK and attaches it to a GitHub Release. Keep a secure offline copy of the keystore; updates signed with a different key cannot update existing installs.

## Android notes

- Minimum OS is Android 8.0 (API 26). Wallpaper-derived Dynamic colors are available from API 27; Android 12+ supports real backdrop blur.
- App shortcuts appear only when an app publishes them and Android exposes them to Calyx.
- Android widget layouts are controlled by their provider; Calyx hosts them in a horizontally scrollable card strip.
- The notification panel is invoked through Android system APIs and is not replaced by Calyx.

## Project structure

```text
app/src/main/java/com/calyx/launcher/  Kotlin launcher implementation
app/src/main/res/                       Android layouts and resources
.github/workflows/                      Debug CI and signed-release workflows
calyx-source.zip                        Maintained source bundle for the phone-based upload workflow
```
