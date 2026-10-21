#!/usr/bin/env bash
# Screenshot the TV into a local PNG and print which activity is in front.
# screencap's stdout is corrupted by vendor log lines on some firmware (MediaTek), so it goes through /sdcard.
# A 0-byte or black file over Netflix, HDMI or Live TV is the TV's secure video layer, not a broken TV.
# Usage: TV_HOST=<tv ip> CYT=<cleanyourtv clone> shot.sh <out.png>
set -euo pipefail
OUT="${1:?usage: shot.sh <out.png>}"
: "${TV_HOST:?set TV_HOST to the TV IP}"
CYT="${CYT:-$PWD}"
export TV_HOST
tv() { "$CYT/tools/venv/bin/python" -I "$CYT/tools/tv.py" "$@"; }
tv 'screencap -p /sdcard/shot.png' >/dev/null
tv --pull /sdcard/shot.png "$OUT" >/dev/null
echo "$(wc -c < "$OUT" | tr -d ' ') bytes  $OUT"
tv 'dumpsys activity activities | grep -m1 -E "mResumedActivity|topResumedActivity"' 2>/dev/null | grep -v 'Broken pipe' || true
