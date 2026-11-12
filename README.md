# tv-makeover

Custom lightweight Android TV launcher and performance-tuning toolkit designed to make entry-level and mid-range Android TVs fast, ad-free, and easy to navigate.

Maintained at [SRC93-tech/tv-makeover](https://github.com/SRC93-tech/tv-makeover). Every TV tweak uses reversible ADB commands (`pm disable-user`, system animation scales, and default Home assignment) without root or flashing.

## Launcher Highlights

- **Single Horizontal App Bar + All Apps Card**: All home apps sit in a single bottom-aligned scrollable strip ending with a frosted **All apps** card that opens the full app library dialog.
- **Smooth App Reordering**: Enter reorder mode from **Settings → Reorder apps** or by long-pressing **OK** on any app tile (**Move / Rearrange**). The carried tile lifts while neighbors smoothly slide and dim, guided by a floating hint pill and `GlassView` focus tracking.
- **Personal Header & Live Weather**:
  - Customizable TV title, time-of-day greeting, and **12-hour AM/PM clock** with inline date.
  - **Open-Meteo live weather** (no API key required) with condition vector icons (sun, moon, partly cloudy, cloudy, fog, rain, snow, thunderstorm) across both Home and the `ClockDream` screensaver, plus optional solar sky tinting.
- **Quick Access Pills & Single-Tap Settings**: Top-right **Inputs** switcher, live **Wi-Fi** status pill (connected SSID with green/red indicator dot), and a single-tap **Settings** menu (*Launcher settings*, *TV system settings*, *Module cards*, *Reorder apps*).
- **Low-RAM Tuned Mode**: Built-in `reduced_motion` support via runtime `config.json` disables expensive full-screen glow/blur layers while keeping smooth focus transitions responsive even on `925 MB` RAM TVs.

## Tested TVs & Real Results

| TV Profile | Platform / OS | Improvements Applied | Measured Impact |
|---|---|---|---|
| **Vu Smart 2K ATV4** (`vu-hamamatsucho`) | MediaTek m5621 · Android TV 9 · `~925 MB` RAM · `720p` | Personalized launcher + `vu-fast-alpine` (`reduced_motion: true`), 22 bloat/voice packages disabled (`katniss`, recommendations, backdrop), `0.5x` animation scales, `eth0` DHCP fix, AOT `speed` compile | **3.5x faster** p95 key response (`95.6 ms → 27.3 ms`), **6.2x faster** navigation (`1,224 ms → 198 ms`), **`0.0%` frame jank**, idle CPU **`52.2% → 2.2%`**, return-to-home **`80 ms` p50** |
| **Skyworth U2** (`skyworth-globe`) | MediaTek MT5891 · Android TV 8.0 · `~1.9 GB` RAM · `1080p` UI | Personalized launcher + full animations, Alpine wallpaper rotation (`15 min`), debloat batches 1–4, `ClockDream` screensaver, `0.5x` scales | Idle `MemFree` **`55 MB → 160+ MB`**, stock launcher/recommendations/backdrop removed from memory |

See [`tvs/vu-hamamatsucho/`](tvs/vu-hamamatsucho/) and [`tvs/skyworth-globe/`](tvs/skyworth-globe/) for package lists and firmware quirks.

## Quick Start (Build & Install over Wi-Fi)

### Prerequisites

- **Computer** on the same Wi-Fi network as the TV with Python 3, JDK 17+ (or Android Studio's JBR), and Android SDK (`platform 34`).
- **TV**: **Settings → Device Preferences → About → Build** (press OK 7 times to unlock Developer options) → **Developer options → USB debugging ON** (and Network/ADB debugging if shown) → accept *"Always allow from this computer"* when prompted.

### Install Prebuilt APK or Build From Source

Use [`install-apk.ps1`](install-apk.ps1) on Windows:

```powershell
# Install the built release APK onto a TV on your LAN
.\install-apk.ps1 -TvHost 192.168.x.x

# Or rebuild from source first, then push, install, AOT-compile, and launch
.\install-apk.ps1 -TvHost 192.168.x.x -Build
```

You can also grab the latest prebuilt APK directly from [GitHub Releases](https://github.com/SRC93-tech/tv-makeover/releases).

### Build & Run Unit Tests Manually

```powershell
cd build\dreamos\launcher
.\gradlew.bat testDebugUnitTest assembleRelease
```

## Customization

- **Static defaults**: Edit [`build/dreamos/launcher/app/src/main/res/values/config.xml`](build/dreamos/launcher/app/src/main/res/values/config.xml) (`brand_name`, `greeting_name`, `single_row_bar`, `weather_city`/`weather_lat`/`weather_lon`, `header_pills`, `dock_apps`, `hidden_apps`, `reduced_motion`).
- **Per-TV runtime overrides & wallpapers (no rebuild required)**: Place a `config.json` and wallpaper images in a theme folder (such as [`build/vu-fast-alpine/`](build/vu-fast-alpine/)) and push with `build/dreamos/tools/theme.ps1` (or `theme.sh`).

## Repository Structure

```text
build/dreamos/launcher/   Android launcher source (com.gathin.tvhome), layouts, weather, and unit tests
build/dreamos/tools/      Python ADB-over-TCP client (tv.py, b64push.py), theme & restore scripts
build/vu-fast-alpine/     Low-RAM runtime theme config and Alpine wallpaper set
tvs/                      Per-model TV hardware profiles, firmware notes, and safe debloat lists
skills/ & scripts/        Agent playbooks and automation helpers
templates/                Launcher UI patch templates (dreamos, starter, extreme)
install-apk.ps1           One-command Windows ADB installer / rebuild-and-deploy script
```

> **Security reminder:** Switch **USB / Network debugging** off on the TV once you finish configuring it so port `5555` is not left open on your local network. Backups, local screenshots, keystores, and ADB keys are excluded via [`.gitignore`](.gitignore).

## License & Attribution

- Licensed under the MIT License ([`LICENSE`](LICENSE)).
- Derived from upstream [tv-makeover](https://github.com/paisanos-io/tv-makeover) and [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT).
- Weather data provided by [Open-Meteo](https://open-meteo.com/); UI icons from [Lucide](https://lucide.dev) (ISC); Inter Tight font under SIL OFL 1.1.
