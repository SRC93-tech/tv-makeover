#!/usr/bin/env bash
# Build a launcher from a template for a given TV: a clean cleanyourtv checkout at the template's base commit,
# the template's patch and assets, then config in three layers (template.json, the TV profile, your choices).
#
# Usage: scripts/apply-template.sh <template> [options]
#   --tv <profile>          a folder in tvs/ whose profile.json has a "launcher" object (live TV app, hidden apps...)
#   --dock a,b,c,d          the dock apps, in order
#   --set key=value         any config.xml key; repeatable (e.g. --set brand_name=MiTele)
#   --work <dir>            the working copy to build in (default: build/<template>); it is reset on every run
#   --cleanyourtv <src>     where to clone cleanyourtv from (default: GitHub; a local clone is faster)
#   --install               build and install on the TV at $TV_HOST (otherwise build only)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEMPLATE="${1:?usage: apply-template.sh <template> [--tv profile] [--dock a,b,c,d] [--set k=v]... [--install]}"; shift
T="$ROOT/templates/$TEMPLATE"
[ -f "$T/template.json" ] || { echo "no template at $T (see templates/)" >&2; exit 1; }
WORK="$ROOT/build/$TEMPLATE"; SRC="https://github.com/Gathin23/cleanyourtv"; TV=""; INSTALL=0; CFG=()
while [ $# -gt 0 ]; do
  case "$1" in
    --tv) TV="$2"; shift 2 ;;
    --dock) CFG+=(--dock "$2"); shift 2 ;;
    --set) CFG+=(--set "$2"); shift 2 ;;
    --work) WORK="$2"; shift 2 ;;
    --cleanyourtv) SRC="$2"; shift 2 ;;
    --install) INSTALL=1; shift ;;
    *) echo "unknown option $1" >&2; exit 1 ;;
  esac
done
read -r COMMIT PATCH ASSETS <<< "$(python3 -I -c 'import json,sys,os
t = json.load(open(sys.argv[1])); d = os.path.dirname(sys.argv[1])
print(t["base"]["commit"], os.path.normpath(os.path.join(d, t.get("patch", "launcher.patch"))),
      os.path.normpath(os.path.join(d, t.get("assets", "assets/res"))))' "$T/template.json")"

# 1. A clean checkout at the base commit. Git-ignored files (build output, tools/venv, local.properties) survive.
if [ ! -d "$WORK/.git" ]; then
  git clone --quiet "$SRC" "$WORK"
fi
git -C "$WORK" fetch --quiet origin 2>/dev/null || true
git -C "$WORK" checkout --quiet --force "$COMMIT"
git -C "$WORK" reset --quiet --hard "$COMMIT"
git -C "$WORK" clean --quiet -fd -- launcher
echo "cleanyourtv $COMMIT in $WORK"

# 2. The template: code patch, then binary assets (fonts, wallpapers, sounds) and licence files. A template.json can
#    point "patch" and "assets" at another template's files.
git -C "$WORK" apply "$PATCH"
cp -R "$ASSETS/." "$WORK/launcher/app/src/main/res/"
find "$(dirname "$ASSETS")" -maxdepth 1 -name '*.txt' -exec cp {} "$WORK/launcher/" \;
echo "template $TEMPLATE applied"

# 3. Config layers: template, TV profile, your choices.
LAYERS=(--json "$T/template.json:config")
if [ -n "$TV" ]; then
  P="$ROOT/tvs/$TV/profile.json"
  if [ -f "$P" ]; then LAYERS+=(--json "$P:launcher")
  else echo "note: tvs/$TV has no profile.json; set live_tv_package / hidden_apps with --set (read tvs/$TV/debloat.txt)"; fi
fi
python3 -I "$ROOT/scripts/launcher_config.py" "$WORK/launcher/app/src/main/res/values/config.xml" "${LAYERS[@]}" ${CFG[@]+"${CFG[@]}"}

# 4. Build (and install).
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"; [ -n "$JAVA_HOME" ] && export JAVA_HOME
fi
if [ ! -x "$WORK/tools/venv/bin/python" ] && [ ! -x "$WORK/tools/venv/Scripts/python.exe" ]; then
  python3 -m venv "$WORK/tools/venv" && "$WORK/tools/venv/bin/pip" install -q -r "$WORK/tools/requirements.txt"
fi
cd "$WORK/launcher"
if [ "$INSTALL" = 1 ]; then
  : "${TV_HOST:?set TV_HOST to the TV IP to install}"
  ./build-and-install.sh
else
  [ -f local.properties ] || echo "sdk.dir=${ANDROID_HOME:-$HOME/Library/Android/sdk}" > local.properties
  ./gradlew assembleRelease -q --console=plain
  echo "built $WORK/launcher/app/build/outputs/apk/release/app-release.apk"
fi
