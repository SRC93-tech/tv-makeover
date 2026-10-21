#!/usr/bin/env bash
# Read-only backup and diagnosis of an Android TV over ADB, through cleanyourtv's tools/tv.py.
# Writes everything a restore or a diagnosis needs into OUT_DIR and prints a short summary.
# Usage: TV_HOST=<tv ip> CYT=<cleanyourtv clone> snapshot.sh <out_dir>
set -euo pipefail
OUT="${1:?usage: snapshot.sh <out_dir>}"
: "${TV_HOST:?set TV_HOST to the TV IP}"
CYT="${CYT:-$PWD}"
PY="$CYT/tools/venv/bin/python"
[ -x "$PY" ] || { echo "No venv at $CYT/tools/venv: python3 -m venv $CYT/tools/venv && $CYT/tools/venv/bin/pip install -r $CYT/tools/requirements.txt" >&2; exit 1; }
mkdir -p "$OUT"
export TV_HOST
tv() { "$PY" -I "$CYT/tools/tv.py" "$@"; }

# One connection, one command per line (tv.py --batch). Nothing here changes the TV.
tv --batch > "$OUT/snapshot.txt" <<'EOF'
getprop
pm list packages -f
pm list packages -d
pm list packages -3
settings list global
settings list secure
settings list system
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER
uptime
cat /proc/meminfo
cat /proc/cpuinfo
df -h /data
top -b -n 2 -d 5 -m 15
dumpsys SurfaceFlinger | grep -m2 -iE 'GLES|EGL implementation'
wm size
wm density
dumpsys tv_input | grep -E 'TvInputInfo\{id=|hdmi_port|type='
dumpsys wifi | grep -o 'cname=[^ ]*'
dumpsys wifi | grep -m1 mWifiInfo
dumpsys audio | grep -E -A6 '^- STREAM_(SYSTEM|MUSIC):' | grep -E 'STREAM|Current'
EOF

S="$OUT/snapshot.txt"
prop() { grep -m1 "^\[$1\]" "$S" | sed -E 's/.*: \[(.*)\]/\1/'; }
section() { awk -v h="\$ $1" 'index($0,h)==1{f=1;next} /^\$ /{f=0} f' "$S"; }
echo "Model:     $(prop ro.product.manufacturer) $(prop ro.product.model) ($(prop ro.product.name)), board $(prop ro.board.platform)"
echo "Android:   $(prop ro.build.version.release) (API $(prop ro.build.version.sdk)), patch $(prop ro.build.version.security_patch)"
echo "Firmware:  $(prop ro.build.fingerprint)"
echo "Client:    $(prop persist.tcl.client_type) $(prop ro.software.version_id)"
echo "Home:      $(section 'cmd package resolve-activity' | tail -1)"
echo "GPU:       $(section 'dumpsys SurfaceFlinger' | grep GLES | sed 's/.*GLES: //')"
echo "Display:   $(section 'wm size' | tail -1), $(section 'wm density' | tail -1)"
grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' "$S" | sed 's/^/Memory:    /'
echo "Storage:   $(section 'df -h' | tail -1)"
W=$(section 'dumpsys wifi | grep -m1')
FREQ=$(echo "$W" | sed -nE 's/.*Frequency: ([0-9]+)MHz.*/\1/p')
BAND=$([ -n "$FREQ" ] && { [ "$FREQ" -lt 3000 ] && echo "2.4 GHz" || echo "5 GHz"; })
echo "Wi-Fi:     \"$(echo "$W" | sed -nE 's/.*mWifiInfo SSID: ([^,]*),.*/\1/p')\" on $BAND (${FREQ} MHz), RSSI $(echo "$W" | sed -nE 's/.*RSSI: ([^,]*),.*/\1/p') dBm, link $(echo "$W" | sed -nE 's/.*, Link speed: ([^,]*), Tx Link.*/\1/p')"
echo "Wi-Fi owners (setupwraith = never disable it): $(section 'dumpsys wifi | grep -o' | sort | uniq -c | tr '\n' ' ')"
echo "Packages:  $(grep -c '^package:' "$S") lines in all lists (see snapshot.txt)"
echo "Top CPU (second sample):"
section 'top -b' | awk '/^Tasks:/{n++} n==2' | sed -n '4,12p'
echo "Saved to $S"
