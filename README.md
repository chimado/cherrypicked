# Cherrypicked

<img src="docs/design/logo/2-focus-ring-banner.png" alt="Cherrypicked" width="320">

A small, fast launcher for Android TV / Google TV, for people who use a handful of apps.
No ads, no recommendations, no accounts, no network access, no dependencies beyond the Android SDK.

![Home screen](docs/design/tv-home-favorite.png)

## What it does

- **Home:** your favourites front and centre, everything else one row down.
- **Main app:** optionally opens your main app when the TV boots or wakes.
- **Buttons:** remap remote buttons (single, double, long press) to apps, inputs or actions.
- **Quick panel:** hold Home for the sleep timer and HDMI inputs, over whatever is playing.
- **Appearance:** preset patterns and colours, drawn in code (no wallpaper memory cost).
- **Backup:** export and import the whole setup as one JSON file.

Requires Android 12 (API 31) or newer.

## Build

Needs JDK 17+ and the Android SDK (platform 36). `ANDROID_HOME` defaults to `~/Android/Sdk`.

```bash
./build.sh
```

The APK lands in `dist/cherrypicked.apk`. It is signed with your local **debug key**, which is fine for
sideloading onto your own TV but means builds from different machines can't update each other.

Run the checks (unit tests, lint, release build) with:

```bash
scripts/ci-local.sh
```

## Install

```bash
adb connect <tv-ip>:5555
adb install -r dist/cherrypicked.apk
```

Then, to make the Home button open Cherrypicked, enable its accessibility service
(Settings → Accessibility → Cherrypicked), or over adb:

```bash
adb shell settings put secure enabled_accessibility_services io.github.chimado.cherrypicked/io.github.chimado.cherrypicked.a11y.CpAccessibilityService
```

Note that this command **replaces** the list of enabled accessibility services; read the current value
first (`adb shell settings get secure enabled_accessibility_services`) if you use others.

The accessibility service is used only to see remote key presses (Home, remapped buttons) and which app is
in front. Nothing leaves the device: the app does not request the `INTERNET` permission.

## Layout

- `app/` — the app; plain Kotlin and platform Views
- `docs/design/` — design notes, measurements and screenshots from the reference TV
- `tools/measure.sh` — start-up, memory and frame-time measurements over adb

## License

[GPL-3.0](LICENSE). The bundled Inter font is under the
[SIL Open Font License 1.1](app/src/main/assets/Inter-OFL.txt).
