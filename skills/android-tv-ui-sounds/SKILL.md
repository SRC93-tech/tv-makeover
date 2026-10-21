---
name: android-tv-ui-sounds
description: Create, prepare and wire interface sounds into an Android TV launcher - a startup sound with the boot splash, a tick when focus moves between icons, a confirmation on OK - including prompts for AI sound generators, an offline ffmpeg synthesizer, trimming/fades/loudness/OGG encoding, and playing them at the TV's volume. Use it whenever someone wants their TV home screen to make sounds, has sound files (mp3/wav) for startup, navigation or selection, wants to generate UI sounds, or says a TV UI sound is too loud, too late or not heard.
---

# Android TV UI sounds

Paths: `<skill>` is the folder that contains this SKILL.md; `<repo>` is the folder two levels above it (the one
holding `templates/`, `tvs/` and `scripts/`). Run commands from `<repo>` or use absolute paths.

Three roles, from loudest to quietest. The quietest is the one heard most often:

| Role | When it plays | Length | Level the user liked (peak / mean) |
|---|---|---|---|
| startup | with the boot splash | 2-3 s, resolves and ends | -2 dBFS / -17 dB |
| select | OK on an icon or button | 0.3-0.7 s | -10 dBFS / -24 dB |
| move | each focus move with the arrows | 0.1-0.4 s | -18 dBFS / -34 dB |

Scripts in `<skill>/scripts` (need ffmpeg): `prepare_sound.sh` and `synth_ui_sound.sh`.

## 1. Get the sounds

- The user brings files (often from an AI sound-effect generator). Map each file to its role exactly as they
  say. If two files are byte-identical or the move sound is much louder than select, point it out once and
  keep their choice.
- They want to generate them: give them prompts to paste into a generator (ElevenLabs Sound Effects or
  similar), short and concrete, e.g.:
  - startup: "A beautiful, minimal startup chime for a TV operating system, warm soft pad with a gentle bell,
    rising then resolving, about 2.5 seconds, clean ending, no voice"
  - move: "A soft, warm and elegant UI tick for moving between items, very short, muted, no reverb tail"
  - select: "A beautifully subtle confirmation sound for choosing an item, two gentle rising notes, half a
    second, no reverb tail"
  Ask for 3-4 variations each and let them pick by ear. If an audio-generation tool is connected in this
  session, offer it, but check its cost and ask before spending credits.
- Offline and free: `synth_ui_sound.sh move|select|startup out.wav` makes a starting point from sine
  partials; adjust the expression numbers to taste.

## 2. Prepare each file

`prepare_sound.sh in.mp3 res/raw/sound_<role>.ogg [PEAK_DB]` prints duration, peak/mean and silences, trims
leading silence (a late start feels like lag on every key press) and trailing silence (keeping 30 ms of
decay), adds a 20 ms fade so the cut never clicks, and encodes OGG Vorbis. Pass a peak (e.g. `-18` for move)
only when the user wants it louder or quieter. Expect 5-50 KB per file.

## 3. Wire them into the launcher

Both templates in `<repo>/templates` contain the integration (`UiSounds.java`, the `ui_sounds`
switch in Config and config.xml, and three hooks in MainActivity); `references/UiSounds.java` is a standalone
copy. Put the prepared files in the template's `assets/res/raw/` and rebuild with apply-template.sh. The decisions behind it:

- SoundPool, files in `res/raw/sound_startup.ogg`, `sound_move.ogg`, `sound_select.ogg`, decoded once at start.
- Media stream (`USAGE_MEDIA`), not sonification: TVs often keep the system stream at 0 on the built-in
  speakers (`dumpsys audio`, `STREAM_SYSTEM ... (speaker): 0`), and the sounds should follow the TV volume.
- move: a global focus-change listener on the root plays it only within 300 ms of a D-pad key, so focus moved
  by code (returning from an app, a rebuild) stays silent.
- select: OK key down, first press only (no repeats), with something focused.
- startup: from the splash; if the sample is still loading at boot, it plays when ready, but only within 1.5 s.
- Release the SoundPool in `onDestroy`, not `onPause`, so the select sound finishes while the app opens.

## 4. Verify

Build and install, then `logcat -d | grep -E "noteStartAudio.*com.gathin.tvhome"` after a key press shows
the launcher started audio. That log is not reliable at boot, and nothing in a log proves it sounds right:
ask the user to move around, press OK and power-cycle the TV, and adjust by ear. Soundbars on HDMI ARC can
add latency or clip the first milliseconds of very short sounds; a few ms of extra lead-in can help there.
