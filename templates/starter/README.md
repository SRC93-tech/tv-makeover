# Starter

cleanyourtv's stock launcher plus the switches the other templates use, all off: `brand_name`, `boot_splash`,
`builtin_wallpaper` (with its blurred twin while the grid scrolls), `ui_sounds`, `ambient_glow`, `header_pills`,
`dock_top_dp` and `design_width_dp`. Turn them on with `--set`, e.g.

```bash
scripts/apply-template.sh starter --tv <folder> --set brand_name=MyTV --set boot_splash=true --set ui_sounds=true
```

The wallpaper (a dark gradient) and the sounds (synthesized with `synth_ui_sound.sh`) are placeholders made for
this repo; replace the files in `assets/res/` with your own. Copy this folder to start a new template.
