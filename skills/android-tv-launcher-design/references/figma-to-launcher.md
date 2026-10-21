# From a 1920x1080 frame to the launcher's layout

Worked example: the DreamOS frame (density 2, so dp = px / 2). Every number below is in
`templates/dreamos/launcher.patch`; change them for a new design.

## Contents
1. Screen padding and header
2. Top-right button
3. Dock and grid geometry
4. Glass panels
5. Wallpaper and baked blur
6. Fonts
7. Performance reference

## 1. Screen padding and header

`activity_main.xml`: the vertical root LinearLayout holds the header RelativeLayout and the grid.

| Design | Value | Launcher |
|---|---|---|
| Brand text left | 150px | root `paddingStart` 75dp |
| Button right edge | 1771.5px | root `paddingEnd` = (1920 - 1771.5) / 2 = 74dp |
| Button top | 36px | root `paddingTop` 18dp |
| Brand text top | 43px | brand `marginTop` = (43 - 36) / 2 = 3.5dp |
| Brand 48px Inter Tight Medium | | `textSize` 24sp, `@font/inter_tight_medium`, `includeFontPadding=false` |
| Clock 36px, top 101px | brand box ends at 43 + 1.21 x 48 = 101 | `textSize` 18sp directly under the brand, no margin |

Hide what the design does not show (greeting, date, divider) with `visibility="gone"` rather than deleting
views the code references. Both header blocks align to the parent top (`layout_alignParentTop`).

## 2. Top-right button

84.46 x 82px, radius 35px, glass fill, 38px Lucide icon centred:
`GlassButton` style, 42 x 41dp, `paddingStart` (42 - 19) / 2 = 11.5dp, `gravity=center_vertical`,
`drawableStart` the icon, `drawableTint=@drawable/pill_text` (white, dark when focused). Background set in
code: a `StateListDrawable` with a white rounded shape for `state_focused` and a `GlassDrawable` otherwise.
Hidden pills (Inputs, Wi-Fi) are controlled from code (`header_pills`), because the Inputs pill's visibility is
recomputed whenever inputs change.

## 3. Dock and grid geometry

Design: four 349 x 165px tiles, radius 25px, lefts at 190, 587, 984, 1381px (gap 48px), top 848px.
The grid (a RecyclerView with one tile per cell, cards centred in their cells) needs:

- tile = 349 x 165px -> `AppAdapter.DESIGN_W_DP` 174.5, `DESIGN_H_DP` 82.5; `item_app.xml` card and name width
  174.5dp, name `marginTop` = card height + 7 = 89.5dp; `cornerRadius` 12.5dp; `tile_bg` radius 12.5dp.
- cell = tile + gap = 397px; first cell starts at 190 - 48 / 2 = 166px, last ends at 1754px.
  Grid `paddingStart` = 166 / 2 - 75 = 8dp, `paddingEnd` = (1920 - 1754) / 2 - 74 = 9dp, so inner width / 4 = 198.5dp.
- `tileWidthPx` caps the tile so the 1.10 focus zoom fits its cell: `fit = (cell - margin) / 1.10` must be at
  least the tile. With a 24dp gap the stock 12dp margin shrinks the tile to 169.5dp; 6dp keeps 174.5dp.
- vertical: `dock_top_dp` = 848 / 2 = 424. The code sets the grid's top padding so the first card lands there:
  `want = dock_top_dp * density - gridTopInWindow - (6dp decoration + 8dp item padding)`.

## 4. Glass panels

Figma: `bg-[rgba(217,217,217,0.71)] blur-[2px] border-2 border-white rounded-[35px]`.

- Fill: alpha 0.71 x 255 = 181 = 0xB5 -> `0xB5D9D9D9`. Stroke: 2px = 1dp, drawn inside (inset by half the width).
- Layer blur 2px -> Skia `BlurMaskFilter` radius (sigma - 0.5) / 0.57735 = 2.6px on both fill and stroke.
  Render once per size into an ARGB bitmap with a 3 x radius margin and draw that bitmap (`GlassDrawable`).
  The blur spills past the bounds, so the parents need `clipChildren=false`.
- Dock band = union of the dock cards plus the design's insets: left 40px, right 41.5px, top 28px, bottom
  29px -> 20, 20.75, 14, 14.5dp (`DockDecoration`).
- Check corners and edges with `px.py`: a 1px offset or a centred instead of inside stroke shows up there.

## 5. Wallpaper and baked blur

- `home_wallpaper.jpg` set as the root's background (`builtin_wallpaper`); a theme wallpaper, if pushed, draws
  over it. Decoded size 1920 x 1080 x 4 = 8.3 MB each.
- Scrolled state (blurred photo, brand and clock faded out): an `ImageView` above the background with the
  baked blur (`home_wallpaper_blur.jpg`), alpha 0. On `onScrolled`, `computeVerticalScrollOffset() > 0` fades it
  in over 300 ms with the header out, and when fully in the root background is set to null so only one
  full-screen picture is drawn per frame. Back on the dock: restore the background, fade the blur out, then
  `GONE`. Re-check the state in `onResume` (a rebuild can reset the scroll without a scroll event).
- The Figma "Bokeh blur" shader (radius 160, density 6) was baked from Figma's own export of the node.
  Without Figma, `ffmpeg -i in.jpg -vf "gblur=sigma=40" out.jpg` is a reasonable stand-in; say it is one.

## 6. Fonts

Replace `res/font/outfit*.xml` with one XML per weight pointing at the new TTF
(`<font android:font="@font/inter_tight" android:fontWeight="500" android:fontVariationSettings="'wght' 500"/>`),
then rename all `@font/outfit` and `R.font.outfit` references across `res/` and `java/` (about 50). Add the
font's OFL licence file next to the launcher. The raw `@font/<family>` references draw the default instance
(`fvar.py`), so check it is a sensible weight for menus.

## 7. Performance reference (Mali-470 MP, 1080p UI, Android 11)

| Build | GPU p50 / p90 | Frames over 16.7ms | Notes |
|---|---|---|---|
| Stock cleanyourtv (black) | 11 / 18 ms (histogram) | - | dock navigation |
| DreamOS, dock navigation | 13 / 18 ms (histogram) | - | photo + glass, glow off |
| Grid navigation, black background (A/B) | 14.9 / 18.9 ms (framestats) | 24% | same build, `builtin_wallpaper` off |
| Grid navigation, DreamOS | 16.0 / 26.1 ms (framestats) | 36% | photo/blur on |

CPU stayed at 5-9 ms throughout: on these TVs the GPU's fill rate is the limit. Cheap: one opaque full-screen
bitmap, small translated/scaled views, cached bitmaps. Expensive: several translucent full-screen layers,
animated full-screen alpha, live blur, video backgrounds.
