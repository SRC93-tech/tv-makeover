#!/usr/bin/env bash
# Frame times of the launcher under the same remote-key runs, for before/after or A/B comparisons.
# Each run: Home, reset gfxinfo, inject keys 0.45 s apart, dump framestats. Then frames.py summarises all runs.
# For an A/B of one design element, push a theme config.json that turns it off (tools/theme.sh), measure,
# then tools/theme.sh --reset.
# Usage: TV_HOST=<tv ip> CYT=<cleanyourtv clone> [PKG=com.gathin.tvhome] measure.sh <label> <out_dir>
#        SEQS="22 22 21|20 22 19" overrides the key runs (19 up, 20 down, 21 left, 22 right).
set -euo pipefail
LABEL="${1:?usage: measure.sh <label> <out_dir>}"; OUT="${2:?usage: measure.sh <label> <out_dir>}"
: "${TV_HOST:?set TV_HOST to the TV IP}"
CYT="${CYT:-$PWD}"; PKG="${PKG:-com.gathin.tvhome}"
SEQS="${SEQS:-22 22 22 21 21 21|20 22 22 20 21 21|19 19 22 20 22 19}"
export TV_HOST
tv() { "$CYT/tools/venv/bin/python" -I "$CYT/tools/tv.py" "$@"; }
mkdir -p "$OUT"
run=0
IFS='|' read -r -a runs <<< "$SEQS"
for seq in "${runs[@]}"; do
  run=$((run + 1))
  tv "input keyevent KEYCODE_HOME; sleep 2; dumpsys gfxinfo $PKG reset >/dev/null; for k in $seq; do input keyevent \$k; sleep 0.45; done; dumpsys gfxinfo $PKG framestats > /sdcard/fs.txt" >/dev/null
  tv --pull /sdcard/fs.txt "$OUT/fs-$LABEL-$run.txt" >/dev/null
done
tv 'input keyevent KEYCODE_HOME' >/dev/null
echo -n "$LABEL: "
python3 -I "$(dirname "$0")/frames.py" "$OUT"/fs-"$LABEL"-*.txt
