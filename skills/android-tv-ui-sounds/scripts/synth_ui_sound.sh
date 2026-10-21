#!/usr/bin/env bash
# Synthesize a UI sound with ffmpeg alone (free, offline, no account): a starting point when no AI sound
# generator is available. Each preset is a few sine partials with an envelope; tweak the numbers to taste.
# Commas inside an expression are escaped (\,) because ffmpeg filter graphs split on commas.
# Usage: synth_ui_sound.sh move|select|startup <out.wav>   (then run prepare_sound.sh on it)
set -euo pipefail
KIND="${1:?move|select|startup}"; OUT="${2:?out.wav}"
case "$KIND" in
  # A soft, short wooden tick: the quietest of the three, since it plays on every focus move.
  move) EXPR='0.55*sin(2*PI*1320*t)*exp(-t*55)+0.25*sin(2*PI*2640*t)*exp(-t*90)+0.3*sin(2*PI*220*t)*exp(-t*40)'; D=0.14 ;;
  # Two quick rising notes (A5 then E6): a confirmation, a little brighter than the move tick.
  select) EXPR='0.5*sin(2*PI*880*t)*exp(-t*20)*lt(t\,0.11)+0.5*sin(2*PI*1318.5*(t-0.08))*exp(-(t-0.08)*11)*gte(t\,0.08)'; D=0.5 ;;
  # A warm major-seventh chord that swells and fades: the boot splash.
  startup) EXPR='(sin(2*PI*261.63*t)+0.8*sin(2*PI*329.63*t)+0.7*sin(2*PI*392*t)+0.45*sin(2*PI*493.88*t)+0.3*sin(2*PI*523.25*t)+0.15*sin(2*PI*1046.5*t)*exp(-t*2))*(1-exp(-t*4))*exp(-t*1.1)/3.4'; D=2.8 ;;
  *) echo "unknown preset $KIND" >&2; exit 1 ;;
esac
FILTER="aecho=0.8:0.5:60|120:0.25|0.12"
[ "$KIND" = move ] && FILTER="anull"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "aevalsrc=$EXPR:s=48000:d=$D" -af "$FILTER" -ac 2 "$OUT"
echo "$OUT ($KIND, ${D}s)"
