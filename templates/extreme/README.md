# Extreme performance

For TVs that struggle with any home screen: 1 GB of RAM or less, Mali-400/450-class GPUs, old Android 7-9 sets.
It is the starter template's code with every costly feature off: no animations (focus snaps instead of
springing), no glow, no wallpaper (plain black), no splash, no sounds, no module cards, no time-of-day sky.
Nothing is decoded or animated that does not have to be, and the stock layout stays readable and familiar.

```bash
TV_HOST=<tv ip> scripts/apply-template.sh extreme --tv <folder in tvs/> --dock <pkg1,pkg2,pkg3,pkg4> --install
```

Then the system side, reversible: `scripts/extreme-performance.sh` (animation scales off, chosen heavy apps kept
from running in the background, caches trimmed). See its header, and the android-tv-debloat skill.
