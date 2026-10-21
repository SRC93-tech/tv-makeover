# TV profiles

One folder per tested TV or box. 123 of them come from cleanyourtv's `examples/` (MIT, (c) 2026 Gathin; their
original README is `UPSTREAM-README.md`), refreshed with `scripts/sync-tv-profiles.sh`. Browse `INDEX.md`, or find
the closest match to a TV with `scripts/find-tv-profile.sh <brand model chip android ...>`.

Each folder has a `debloat.txt`: what is disabled, what must never be disabled, and the firmware's quirks in its
header. Folders this repo adds also have:

- `profile.json`: model, platform, hardware, display, stock launcher, and a `launcher` object (`live_tv_package`,
  `hidden_apps`, any config.xml key) that `scripts/apply-template.sh --tv <folder>` applies.
- `notes.md`: what was measured and learned on that unit.

Adding your TV: copy the closest folder to `tvs/<brand>-<model>/`, adjust the list on your TV in batches (the
android-tv-debloat skill), write `profile.json`, and consider sending the list to cleanyourtv too.
