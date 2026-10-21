# Hitachi CDH-LE504KSMART20 (TCL BeyondTV, Android 11, Realtek RTD2851)

- The voice assistant (`com.google.android.katniss:interactor`) used 30-33% of a core at idle, even right after a
  reboot. Disabling voice took idle CPU from ~84% to ~95% free. This was the biggest single win.
- 2 GB RAM: ~530-600 MB free after boot with the stock home, ~640-690 MB with the cleanyourtv launcher.
- Wi-Fi radio is 2.4 GHz only. The router broadcast a "...5GHz" SSID on both bands and isolated its 2.4 and 5 GHz
  SSIDs from each other: put the TV and the computer on the same SSID.
- The Wi-Fi network saved during setup belongs to `com.google.android.tungsten.setupwraith`: never disable it.
- `com.tcl.guard` is a persistent system process and carries TCL's package installer: disable it only after the
  launcher is installed; its memory manager keeps running anyway.
- `STREAM_SYSTEM` volume is 0 on the speakers; launcher sounds must use the media stream.
- Inputs: `com.tcl.tvinput` (tuner, HDMI 1-3, AV). AV reports "connected" with nothing plugged in.
- Mali-470: the DreamOS photo wallpaper costs ~1 ms (median) to ~7 ms (p90) of GPU time while scrolling the grid.
