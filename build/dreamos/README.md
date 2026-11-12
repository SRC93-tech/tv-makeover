# Custom Android TV Launcher (`com.gathin.tvhome`)

This Android module builds our customized leanback Home replacement and `ClockDream` screensaver APK targeting Android TV 8.0+ (`minSdk 26`, `targetSdk 34`).

## Features in This Build

- **Single Horizontal Bar + Trailing "All apps" Tile** (`single_row_bar`): Keeps all pinned and installed apps on one scrollable horizontal row at the bottom with a matching frosted **All apps** card at the end.
- **Smooth Reorder UX**: Reorder via **Settings → Reorder apps** or long-press **OK** on any app tile (**Move / Rearrange**). Uses `DefaultItemAnimator` neighbor sliding, carried tile lift (`-8dp`), neighbor dimming, horizontal scroll pinning, and pure layout-bound `GlassView` tracking.
- **Header & Personalization**: Custom TV name (`brand_name`), greeting, 12h AM/PM clock + date, live Open-Meteo weather with condition icons (`Weather.java`), **Inputs** switcher, **Wi-Fi** status pill (`header_pills`), and single-tap Settings menu.
- **Low-GPU Motion Profile**: Setting `"reduced_motion": true`, `"ambient_glow": false`, and `"advanced_effects": false` in runtime `config.json` keeps GPU cost minimal on `925 MB` RAM / Mali-class TVs.

## Build & Test

From `build/dreamos/launcher`:

```powershell
# Run JVM unit tests
.\gradlew.bat testDebugUnitTest --tests com.gathin.tvhome.GridNavigationTest --tests com.gathin.tvhome.SkyTest --tests com.gathin.tvhome.OrderTest --tests com.gathin.tvhome.DockSlotsTest

# Build R8-minified release APK
.\gradlew.bat assembleRelease
```

The output APK is written to `app/build/outputs/apk/release/app-release.apk`. To deploy directly to a TV on your LAN, run `..\..\..\install-apk.ps1 -TvHost <TV_IP>` from this folder or repo root.

## Attribution & License

Derived from [cleanyourtv](https://github.com/Gathin23/cleanyourtv) under the MIT License ([`../LICENSE`](../LICENSE)).
