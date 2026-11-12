# Agent Instructions

This repository (`SRC93-tech/tv-makeover`) maintains our customized Android TV launcher (`com.gathin.tvhome`), TV-specific optimization profiles, wallpapers, and ADB deployment scripts. Every system modification must remain reversible without root.

## Project Layout

- `build/dreamos/launcher/`: Custom Android launcher source code (`MainActivity`, `AppAdapter`, `Weather`, `SkyView`, `ClockDream`, layouts, drawables, and JVM unit tests).
- `build/dreamos/tools/`: Zero-binary Python ADB-over-TCP tooling (`tv.py`, `b64push.py`, `theme.ps1`, `restore.ps1`).
- `build/vu-fast-alpine/`: Low-RAM runtime theme (`config.json` with `reduced_motion: true`) and Alpine wallpaper pack.
- `install-apk.ps1`: Build/push/install/AOT-compile deployment script for Android TVs over Wi-Fi ADB.
- `tvs/`: Device profiles and verified safe debloat lists (notably `tvs/vu-hamamatsucho/` and `tvs/skyworth-globe/`).
- `skills/`, `scripts/`, `templates/`: Modular playbooks and template utilities.

## Working Rules

- Keep changes reversible (`pm disable-user`, reversible `settings put`), apply in small verified batches, and capture screenshots + `logcat` checks after UI or launcher changes.
- Never run concurrent ADB commands against the same TV host (`tv.py` / `b64push.py` sequentially only).
- Never commit secrets, keystores, `adbkey*`, local backups (`cleanyourtv/backup/`), build outputs, or unnecessary private device data (`*.jpg`/`*.png` outside explicitly intended wallpaper assets).
- Always run relevant unit tests (`GridNavigationTest`, `SkyTest`, `OrderTest`, `DockSlotsTest`) and `assembleRelease` before deploying launcher builds.
- Remind the owner to turn off USB/Network debugging when finished if leaving debugging enabled is unnecessary.
