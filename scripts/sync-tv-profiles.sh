#!/usr/bin/env bash
# Pull the latest TV lists from cleanyourtv's examples/ into tvs/ (new models, updated lists), keep this repo's
# own files (profile.json, notes.md) and rebuild tvs/INDEX.md. Review the diff before committing.
# Usage: scripts/sync-tv-profiles.sh [cleanyourtv clone or URL]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-https://github.com/Gathin23/cleanyourtv}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
git clone --quiet --depth 1 "$SRC" "$TMP/cyt" 2>/dev/null || git clone --quiet "$SRC" "$TMP/cyt"
rsync -a --exclude README.md "$TMP/cyt/examples/" "$ROOT/tvs/"
cp "$TMP/cyt/examples/README.md" "$ROOT/tvs/UPSTREAM-README.md"
python3 -I "$ROOT/scripts/build_tv_index.py"
echo "synced from $SRC ($(git -C "$TMP/cyt" log --oneline -1))"
