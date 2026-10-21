# Templates

A template is a look for the Home screen that works on any TV in `tvs/`. Each folder has:

| File | What it is |
|---|---|
| `template.json` | Name, base cleanyourtv commit, and the design's config values (applied after the patch) |
| `launcher.patch` | The code and layout changes, against that cleanyourtv commit |
| `assets/res/` | Binary files copied into the launcher's `res/` (fonts, wallpapers, sounds) |
| `preview-*.png` | Screenshots from a real TV (optional) |

Build one for a TV (`--install` also installs it on `TV_HOST`):

```bash
TV_HOST=<tv ip> scripts/apply-template.sh dreamos --tv <folder in tvs/> --dock <pkg1,pkg2,pkg3,pkg4> --install
```

Config is layered: `template.json` "config", then the TV's `profile.json` "launcher" values, then `--dock` and
`--set key=value`. Every key is documented in the launcher's `res/values/config.xml`.

| Template | Look |
|---|---|
| `dreamos` | Photo wallpaper that blurs over the app grid, glass dock and settings button, Inter Tight, DreamOS splash and sounds |
| `starter` | cleanyourtv's stock look with all branding switches added but off; the base for a new design |
| `extreme` | For very old or weak TVs: the stock layout with every animation, effect, wallpaper and sound off (reuses starter's code) |

## Making a new template

1. Copy `starter/` to `templates/<name>/` and set `name`, `title`, `description` in `template.json`.
2. Build it into a working copy (`scripts/apply-template.sh <name>`), change the code in `build/<name>/launcher`
   (the android-tv-launcher-design skill walks through a Figma frame), and replace the assets.
3. Regenerate the patch from the working copy: `cd build/<name> && git add -A launcher && git diff --cached -- launcher
   ':(exclude)*.ttf' ':(exclude)*.jpg' ':(exclude)*.png' ':(exclude)*.ogg' > ../../templates/<name>/launcher.patch`
   (binary files stay in `assets/res/`), then rebuild from scratch to check it applies.
4. Lay out for a 1920x1080 frame and keep `design_width_dp` at 960, so the template looks the same on TVs that
   report another density.
