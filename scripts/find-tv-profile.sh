#!/usr/bin/env bash
# Find the TV profiles closest to a TV: scores every tvs/<folder> by how many of the given words appear in its
# folder name and debloat.txt header, and prints the best matches with the lines that matter for the launcher.
# Words that work best: brand, model number, board/chip (rtd2851, mt9221, s905x4), Android version, the
# platform name the TV announces on the network (BeyondTV, PatchWall...).
# Usage: scripts/find-tv-profile.sh <word> [word ...]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ $# -gt 0 ] || { echo "usage: find-tv-profile.sh <brand model chip ...>" >&2; exit 1; }
for dir in "$ROOT"/tvs/*/; do
  f="$dir/debloat.txt"; [ -f "$f" ] || continue
  name=$(basename "$dir")
  hay="$(echo "$name"; head -40 "$f"; cat "$dir/profile.json" 2>/dev/null || true)"
  score=0
  for w in "$@"; do echo "$hay" | grep -qi -- "$w" && score=$((score + 1)); done
  [ "$score" -gt 0 ] && echo "$score $name"
done | sort -k1,1nr -k2 | head -8 | while read -r score name; do
  f="$ROOT/tvs/$name/debloat.txt"
  echo "== $name  ($score/$# words)$([ -f "$ROOT/tvs/$name/profile.json" ] && echo '  [profile.json]')"
  head -2 "$f" | sed 's/^/   /'
  grep -m4 -iE 'stock launcher|live tv( app)?:|launcher/ config|live_tv_package|hidden_apps|DO NOT DISABLE' "$f" | sed 's/^/   /' || true
done
