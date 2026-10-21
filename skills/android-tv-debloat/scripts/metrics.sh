#!/usr/bin/env bash
# Idle memory and CPU, for a before/after comparison. Run it the same way both times: reboot, wait for
# boot_completed plus WAIT seconds (default 150) with nothing playing, then sample. A TV measured right after
# use (apps cached, swap full) cannot be compared with one measured after a reboot.
# Usage: TV_HOST=<tv ip> CYT=<cleanyourtv clone> [REBOOT=1] [WAIT=150] metrics.sh <label> <out_dir>
set -euo pipefail
LABEL="${1:?usage: metrics.sh <label> <out_dir>}"; OUT="${2:?usage: metrics.sh <label> <out_dir>}"
: "${TV_HOST:?set TV_HOST to the TV IP}"
CYT="${CYT:-$PWD}"
export TV_HOST
tv() { "$CYT/tools/venv/bin/python" -I "$CYT/tools/tv.py" "$@"; }
mkdir -p "$OUT"
if [ "${REBOOT:-0}" = 1 ]; then
  tv reboot >/dev/null 2>&1 || true
  sleep 25
  for _ in $(seq 1 80); do tv 'getprop sys.boot_completed' 2>/dev/null | grep -q 1 && break; sleep 3; done
fi
sleep "${WAIT:-150}"
tv --batch > "$OUT/metrics-$LABEL.txt" <<'EOF'
uptime
grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo
top -b -n 2 -d 5 -m 12
EOF
F="$OUT/metrics-$LABEL.txt"
avail=$(awk '/^MemAvailable:/{print int($2/1024)}' "$F"); swapt=$(awk '/^SwapTotal:/{print $2}' "$F"); swapf=$(awk '/^SwapFree:/{print $2}' "$F")
idle=$(awk '/%idle/{n++; if(n==2){for(i=1;i<=NF;i++) if($i ~ /idle/) print $i}}' "$F")
echo "$LABEL: MemAvailable ${avail} MB, swap used $(( (swapt - swapf) / 1024 )) MB, CPU idle ${idle}"
awk '/^Tasks:/{n++} n==2' "$F" | sed -n '4,9p'
