# Field notes: quirks seen on real TVs

Read the section that matches what you are seeing. These complement cleanyourtv's `docs/TROUBLESHOOTING.md`.

## Connection

- **Authorisation**: the first "Allow" without "Always allow" is valid for that connection only. The next
  `tv.py` call blocks in `connect()` (the client sent its public key and waits) and times out after 120 s.
  Ask the user to accept with "Always allow" and retry; after that, reboots reconnect without a prompt.
- **Network ADB after reboot**: on TCL Android TV, 5555 is open again ~25 s after a reboot, before
  `sys.boot_completed`. Poll `getprop sys.boot_completed` before measuring anything.
- **Router band isolation**: an ISP router broadcast "...5GHz" and "...2.4GHz" SSIDs; devices on
  one could not reach devices on the other ("Host is down"). The TV (2.4 GHz-only radio) could still join the
  "5GHz" SSID because the router also broadcast that name on 2.4 GHz. The frequency in `dumpsys wifi` is the
  truth, not the SSID's name.
- **A command you thought was cancelled may have run**: if a long shell command was interrupted, check the
  state (settings values, `uptime`) before assuming nothing happened.

## Diagnosis

- `top`'s load average is meaningless on Realtek/MediaTek TVs (kernel threads in D state). Use `%idle`.
- `sitatvservice` (Realtek, root) uses ~14% of a core permanently; it is firmware, leave it.
- Screenshots: Netflix, HDMI, Live TV and some update screens come back black or 0 bytes. Not a fault.
- `screencap -p` to stdout is corrupted on MediaTek firmware; write to /sdcard and pull (scripts/shot.sh does).
- `sys.boot.reason` reads `watchdog` for every reboot on TCL Realtek firmware, including `adb reboot`.
- `dumpsys gfxinfo`'s GPU histogram puts unmeasured frames in a 4950 ms bucket; use framestats (see the
  design skill's `frames.py`).

## TCL BeyondTV, Android 11, Realtek RTD2851 (Hitachi CDH-LE504KSMART20, client HIT-LA-RT2851-S1)

- 2 GB RAM + 600 MB zRAM, 4x Cortex-A55 at 1.1 GHz, Mali-470 (OpenGL ES 2.0), UI 1920x1080 at density 320
  (2x), panel 4K.
- `com.google.android.katniss:interactor` used 30-33% of a core at idle, even right after boot. Disabling
  voice (katniss, `com.tcl.assistant`, `com.tcl.micmanager`, `com.google.android.tv.assistant`,
  `com.tcl.smartalexa`) took idle CPU from ~84% to ~95% free.
- `com.tcl.guard` (TGuard) is `*PERS*` uid 1000: `pm disable-user` succeeds but the memory manager still runs.
  It also carries a PackageInstallerService: disable it after the launcher is installed.
- `com.tcl.partnercustomizer` provides the TKey page and the stock launcher's partner rows: keep it, hide its
  tile in the launcher (`hidden_apps`).
- No `com.tcl.sourcemananger` on this build; inputs are `com.tcl.tvinput` (tuner + passthrough HDMI 1-3, AV).
  The launcher's Inputs menu works through TvInputManager. AV reports "connected" with nothing plugged in.
- The Wi-Fi network saved during setup belongs to setupwraith: never disable it here.
- `STREAM_SYSTEM` volume is 0 on the built-in speakers while `STREAM_MUSIC` follows the TV volume: UI sounds
  must use the media stream to be heard.
- Debloat alone did not make apps faster; the launcher swap, voice off and animation scales did most of it.
  Free memory after boot went from ~530-600 MB to ~640-690 MB.

## Apps

- Star+ (merged into Disney+ in Latin America) and the old HBO Max app (`com.hbo.hbonow`, replaced by Max,
  `com.wbd.stream`) are dead weight on older sets; offer to disable them.
- Netflix (`com.netflix.ninja`) keeps ~150-230 MB resident in the background after it has been opened once.
