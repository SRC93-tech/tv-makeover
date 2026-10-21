---
name: android-tv-launcher-design
description: Implement a custom TV home-screen design (from a Figma frame, a screenshot or a written brief) in cleanyourtv's Android TV launcher to the pixel - fonts, wallpaper, glass panels, baked blur and Figma shader effects, icons, focus states - then verify it against the design with pixel comparisons and measure the GPU cost on the real TV. Use it whenever someone shares a Figma link or mockup for their TV home screen, asks to change the launcher's font, colours, wallpaper, blur or layout, asks whether an effect like blur will run on their TV, or wants an "exactly like this" TV UI.
---

# Android TV launcher design

Paths: `<skill>` is the folder that contains this SKILL.md; `<repo>` is the folder two levels above it (the one
holding `templates/`, `tvs/` and `scripts/`). Run commands from `<repo>` or use absolute paths.

Turn a design into the launcher (`launcher/` in cleanyourtv) and prove it matches. Tools in
`<skill>/scripts`: `px.py` and `bbox.py` (pixel and text/icon placement checks, no Pillow needed),
`measure.sh` + `frames.py` (frame times on the TV), `svg_to_vector.py` (icons), `fvar.py` (variable font
weights), `crop_to_frame.sh` (background placement). The formulas, with a worked example, are in
`references/figma-to-launcher.md`; read it before writing layout code.

Start from a template in `<repo>/templates`: `dreamos` is a finished design (glass dock and button,
font swap, header, pinned dock position, built-in wallpaper with a blurred variant, no-bounce focus), `starter`
the stock look with the same switches. Build it into a working copy with
`<repo>/scripts/apply-template.sh <template>`, edit there, and save the result as a new template
(steps in templates/README.md). Changing DreamOS's numbers is much faster than rebuilding those pieces.

## 1. Read the design

- Figma: load the figma-design-to-code skill if it is available, then `create_session`,
  `get_design_context` on each frame (home, scrolled grid, focus states if drawn), `get_screenshot` with
  `maxDimension: 1920` for a full-size reference PNG, and `download_assets` for images and SVGs. The frame
  should be 1920x1080, the TV's UI size (`wm size`; a 4K panel still draws its UI at 1080p). If the
  connection drops mid-call, just retry.
- A mockup or brief: agree on a 1920x1080 frame with the user first (or draft one in Figma with the
  figma-generate-design skill) so there is something to verify against.
- List what the design does not define, decide sensibly and tell the user: focus state of each element, the
  scrolled grid, empty dock slots, a button without an icon. Ask only where taste decides (what a bare button does).

## 2. Units

Design px on a 1920x1080 frame map to dp by `dp = px / (density / 160)` (`wm density`; 320 dpi -> divide by 2).
Text px map to sp the same way. Keep `design_width_dp` at 960 so the layout lands the same on TVs that
report another density (verified by forcing `wm density 240`, then `wm density reset`). Figma's `leading-[normal]` matches a TextView with `includeFontPadding=false`,
so a text box's top in Figma is the TextView's top.

## 3. Assets

- **Background**: `download_assets` on the image node; its `export` comes back already clipped to the frame
  (verify a few pixels against your own crop). Otherwise `crop_to_frame.sh` from the node's size and offset.
  1920x1080 JPEG, quality ~88, under ~500 KB, in `res/drawable-nodpi/` so Android never rescales it.
- **Icons**: `svg_to_vector.py icon.svg res/drawable/ic_x.xml --density 2`, white, tinted by the button's
  colour selector so it can invert on focus.
- **Fonts**: download the variable TTF from Google Fonts' GitHub (`ofl/<family>/<Family>[wght].ttf`) with
  its `OFL.txt`; run `fvar.py` to see the default weight (raw `@font/x` uses it: Outfit's is 100, Inter
  Tight's 400). Mirror the launcher's `font-family` XMLs (one per weight with `fontVariationSettings`;
  setting variations per TextView costs ~10 ms per view on a TV CPU) and rename every `@font/` reference.

## 4. Effects the TV cannot draw live

Android 11 TVs have no RenderEffect blur (Android 12+), Mali-400/450/470-class GPUs have no headroom for
multi-pass full-screen shaders, and Figma's shader effects (`<ShaderEffect>`, WebGPU) have no Android
runtime. Bake them instead, and explain that this is the exact design output, not an approximation:

- Blur or shader over a static image: export Figma's render of that node and ship it as an image; switch
  between states with a short cross-fade, then stop drawing the hidden image.
- Layer blur on a panel (`blur-[2px]` on glass): render the panel once into a bitmap with a
  `BlurMaskFilter` (`GlassDrawable` in the patch) and draw the bitmap each frame.
- Backdrop blur over moving content: not feasible; propose a translucent fill instead and show both.

## 5. Motion

TVs feel slow when things overshoot. Use critically damped springs for the focus highlight
(`SpringForce.DAMPING_RATIO_NO_BOUNCY`, stiffness ~600) and decelerate interpolators for the tile zoom; no
`OvershootInterpolator`. Animate translation/scale of small views; avoid animating alpha on full-screen layers
(each frame becomes a full-screen blend), or set `forceHasOverlappingRendering(false)` on single-bitmap views.

## 6. Verify against the design

Build, install (`build-and-install.sh`), press Home, wait for the splash and animations, take
`android-tv-debloat/scripts/shot.sh`. Then:

- `px.py design.png tv.png x,y ...` on flat background, panel fills and edges, buttons: expect +/-2 per
  channel on flat areas; edges of blurred shapes within ~12.
- `bbox.py x0,y0,x1,y1 250 design.png tv.png` around each text and icon: expect the same box to +/-1 px
  (digits of a clock differ, compare left/top).
- Fix what differs (usually a padding rounding or an inside-vs-centred stroke), re-check, and report a table.

## 7. Measure the cost

`measure.sh <label> <dir>` injects the same key runs and prints CPU and GPU frame-time percentiles from
framestats. Compare against the previous build, or A/B a single element by pushing a theme `config.json` that
switches it off (`tools/theme.sh <dir>`, then `tools/theme.sh --reset`). Report median and p90 GPU time and the
share of frames over 16.7 ms; on a Mali-470 a full-screen photo added ~1 ms median and ~7 ms at p90 while
scrolling the grid. If the budget is blown, offer cheaper options (smaller or darker image, fewer
translucent layers, no glow) rather than silently dropping parts of the design.
