---
name: android-tv-launcher
description: Build, configure and install cleanyourtv's lightweight Apple-TV-style launcher on an Android TV / Google TV over ADB, make it the default Home, set the dock and inputs, and brand it (a name on Home, a boot splash after the Android animation, a built-in wallpaper, UI sounds). Use it whenever someone wants to replace or customize their TV's home screen or launcher, put their own name/logo/splash on the TV, set which apps sit in the dock, fix Home or remote buttons after a launcher change, or asks whether the boot logo can be changed.
---

# Android TV launcher: install, configure, brand

Paths: `<skill>` is the folder that contains this SKILL.md; `<repo>` is the folder two levels above it (the one
holding `templates/`, `tvs/` and `scripts/`). Run commands from `<repo>` or use absolute paths.

The launcher is `launcher/` in [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT): native Java,
~400 KB, pitch-black home, dock, glass focus highlight, Inputs menu, clock screensaver. Configuration lives in
`launcher/app/src/main/res/values/config.xml`; themes (`tools/theme.sh themes/<name>`) overlay a `config.json`
without rebuilding, which is handy for quick A/B tests.

## Build prerequisites

- JDK 17 or newer (`/usr/libexec/java_home -V` on macOS). build-and-install.sh asks for 17; if only a newer one
  is installed, export `JAVA_HOME` yourself (19 and 21 build fine).
- Android SDK with platform 34 (`ANDROID_HOME`, default `~/Library/Android/sdk` or `~/Android/Sdk`).
- First build downloads Gradle 8.9 (a minute or two).

Before building, check the TV accepts APKs at all: the matching `examples/` header says if the firmware
rejects sideloads or third-party HOME apps (some China-market sets). If so, stop after the debloat and say so.

## Configure (config.xml)

- `dock_apps`: the user's four apps, in order. Package names from the snapshot or
  `cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER`.
- `hidden_apps`: tiles nobody should see (the Live TV app, which the Inputs menu covers; vendor tiles like TCL's
  TKey `com.tcl.partnercustomizer`; Settings, which has its own button).
- `live_tv_package` (e.g. `com.tcl.tv`, `com.mediatek.wwtv.tvcenter`), plus `live_tv_action` /
  `source_panel_service` only when the examples say the firmware needs them.
- Everything else is documented inline in config.xml; leave defaults unless the user asks.

## Install and verify

`cd launcher && TV_HOST=<ip> ./build-and-install.sh` builds the release APK, pushes it, installs it, starts it,
compiles it with `-m speed`, then disables the stock launcher and sets Home. It refuses to disable anything it
does not recognise, and on later runs (Home already ours) it disables nothing.

Verify with screenshots (`android-tv-debloat/scripts/shot.sh`): press Home; press Down (the grid); open the
Inputs menu; reboot and confirm Home is still `com.gathin.tvhome`. Then ask the user to press the real remote
buttons: Home, Input/Source, Settings and any app buttons (Netflix, YouTube...). Injected keys are not proof.
If a button stopped working, see `INPUT_KEY_SERVICE`, `HOME_KEY_SERVICE`, `app_keys` and
`settings_key_keycodes` in config.xml and cleanyourtv's README.

## Branding: name, splash, wallpaper, sounds

Use the templates in `<repo>/templates` (read its README): `dreamos` (a finished design) and
`starter` (the stock look with every switch below added but off) and `extreme` (everything costly off, for very
old TVs; see the extreme mode in android-tv-debloat). Build one for this TV with
`<repo>/scripts/apply-template.sh <template> --tv <folder in tvs/> --dock a,b,c,d [--set k=v] --install`
(with `TV_HOST` set): a clean cleanyourtv checkout at the template's base commit, its patch and assets, then
config in layers (template.json, the TV's profile.json, the user's choices). The switches, each also settable
from a theme's config.json:

| Key | What it does |
|---|---|
| `brand_name` | A wordmark above the greeting on Home ("DreamOS"); empty hides it |
| `boot_splash` | brand_name on black for ~2.7 s after the Android boot animation (once per boot, via `Settings.Global.BOOT_COUNT`) and when the screen wakes with Home in front; any key skips it; `SPLASH_HOLD_MS` sets the length |
| `builtin_wallpaper` | `res/drawable-nodpi/home_wallpaper.jpg` behind Home, cross-fading to `home_wallpaper_blur.jpg` when the grid scrolls below the dock |
| `ambient_glow` | The full-screen glow in the focused app's colour (off saves a full-screen layer on weak GPUs) |
| `header_pills` | The Inputs and Wi-Fi pills next to Settings |
| `dock_top_dp` | Pins the dock tiles' top edge at a fixed height, for layouts drawn to coordinates |
| `ui_sounds` | Startup, focus-move and select sounds from `res/raw` (see android-tv-ui-sounds) |
| `design_width_dp` | Lays out on a canvas this many dp wide whatever density the TV reports (960 = Android TV's standard), so a design looks the same on 240-dpi sets |

For branding on the stock look, use `starter` with `--set brand_name=... --set boot_splash=true ...` and
replace its placeholder wallpaper and sounds. For a new visual design, use the android-tv-launcher-design skill.
If the TV has no `profile.json` yet, read its `debloat.txt` header for the Live TV app and the tiles to hide,
pass them with `--set`, and write a profile.json for it afterwards.

What cannot change without root, so do not promise it: the manufacturer's boot logo (bootloader partition)
and Android's boot animation (`/system/media/bootanimation.zip`, read-only and verified). The splash comes
right after them. Also optional: rename the TV for Cast and Bluetooth with
`settings put global device_name "<name>"`, after asking.

Verifying the splash: ADB comes back after the splash has ended, so a screenshot cannot catch it. Reboot and
ask the user to watch and listen, then adjust `SPLASH_HOLD_MS` in 0.5 s steps as they ask.
