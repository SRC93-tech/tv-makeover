#!/usr/bin/env bash
# Extreme performance, system side, for very old or weak TVs. Reversible: `apply` first records every value it
# changes in a state file, `undo` puts those values back. Pair it with the `extreme` launcher template.
#
#   - Window, transition and animator scales to 0 (no system animations at all).
#   - The apps you name lose permission to run in the background (appops RUN_ANY_IN_BACKGROUND ignore) and go to
#     the "rare" standby bucket, so they stop holding RAM and waking up between uses. They still work normally
#     when opened. Name heavy streaming apps the household does not need running in the background; never music
#     players, casting (com.google.android.apps.mediashell), the launcher, keyboards or TV/input services.
#   - Trims app caches (pm trim-caches): frees storage on nearly full TVs; caches refill as apps are used.
#
# Usage: TV_HOST=<tv ip> CYT=<cleanyourtv clone> scripts/extreme-performance.sh apply [pkg,pkg,...] [--dry-run]
#        TV_HOST=<tv ip> CYT=<cleanyourtv clone> scripts/extreme-performance.sh undo [--dry-run]
#        TV_HOST=<tv ip> CYT=<cleanyourtv clone> scripts/extreme-performance.sh status [pkg,pkg,...]
# State file: $STATE (default ./extreme-state-<TV_HOST>.txt); keep it until you have undone.
set -euo pipefail
ACTION="${1:?usage: extreme-performance.sh apply|undo|status [pkg,pkg,...] [--dry-run]}"; shift
PKGS=""; DRY=0
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; *) PKGS="$a" ;; esac; done
: "${TV_HOST:?set TV_HOST to the TV IP}"
CYT="${CYT:-$PWD}"
STATE="${STATE:-$PWD/extreme-state-$TV_HOST.txt}"
export TV_HOST
tv() { "$CYT/tools/venv/bin/python" -I "$CYT/tools/tv.py" "$@"; }
run() {  # run <shell command on the TV>, or print it with --dry-run
  if [ "$DRY" = 1 ]; then echo "would run: $1"; else tv "$1" >/dev/null; echo "ran: $1"; fi
}
SCALES="window_animation_scale transition_animation_scale animator_duration_scale"

read_state() {  # prints "scale <name> <value>" and "app <pkg> <appops mode> <bucket>" lines for the current TV
  local cmds="" p
  for s in $SCALES; do cmds+="echo scale $s \$(settings get global $s); "; done
  for p in ${PKGS//,/ }; do
    # No explicit mode ("No operations.") means the default, allow.
    cmds+="m=\$(cmd appops get $p RUN_ANY_IN_BACKGROUND | sed -n 's/^RUN_ANY_IN_BACKGROUND: \([a-z]*\).*/\1/p' | head -1); "
    cmds+="echo app $p \${m:-allow} \$(am get-standby-bucket $p); "
  done
  tv "$cmds" | tr -d '\r'
}

case "$ACTION" in
  status)
    read_state | sed 's/^/  /' ;;
  apply)
    if [ -f "$STATE" ] && [ "$DRY" = 0 ]; then echo "$STATE exists: undo first (or move it away)" >&2; exit 1; fi
    if [ "$DRY" = 1 ]; then read_state | sed 's/^/  current: /'; else read_state > "$STATE"; echo "recorded $(wc -l < "$STATE" | tr -d ' ') values in $STATE"; fi
    for s in $SCALES; do run "settings put global $s 0"; done
    for p in ${PKGS//,/ }; do
      run "cmd appops set $p RUN_ANY_IN_BACKGROUND ignore"
      run "am set-standby-bucket $p rare"
    done
    run "pm trim-caches 999G" ;;
  undo)
    [ -f "$STATE" ] || { echo "no state file at $STATE: nothing recorded to restore" >&2; exit 1; }
    while read -r kind name a b; do
      case "$kind" in
        scale) if [ -z "$a" ] || [ "$a" = null ]; then run "settings delete global $name"; else run "settings put global $name $a"; fi ;;
        app)
          run "cmd appops set $name RUN_ANY_IN_BACKGROUND ${a:-allow}"
          case "$b" in 10) bk=active ;; 20) bk=working_set ;; 30) bk=frequent ;; 40) bk=rare ;; *) bk=active ;; esac
          run "am set-standby-bucket $name $bk" ;;
      esac
    done < "$STATE"
    [ "$DRY" = 1 ] || { mv "$STATE" "$STATE.undone"; echo "restored; the record is kept as $STATE.undone"; } ;;
  *) echo "unknown action $ACTION (apply, undo, status)" >&2; exit 1 ;;
esac
