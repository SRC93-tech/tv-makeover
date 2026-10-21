#!/usr/bin/env bash
# Prepare a UI sound for the launcher: report its loudness and silences, trim silence at both ends (a sound
# that starts late feels laggy on every key press; a tail of silence only wastes memory), add a 20 ms fade-out so the cut never clicks, optionally
# set its peak, and encode OGG Vorbis (small, decoded once into a SoundPool).
# Usage: prepare_sound.sh <in.(mp3|wav|m4a|...)> <out.ogg> [PEAK_DB]
#   PEAK_DB, e.g. -18 for a focus-move sound: scales the sound so its loudest sample sits there.
set -euo pipefail
IN="$1"; OUT="$2"; PEAK="${3:-}"
command -v ffmpeg >/dev/null || { echo "needs ffmpeg (brew install ffmpeg / apt install ffmpeg)" >&2; exit 1; }
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
level() { ffmpeg -hide_banner -nostats -i "$1" -af volumedetect -f null - 2>&1 | sed -nE 's/.*(max|mean)_volume: (.*) dB/\1 \2/p' | tr '\n' ' '; }
dur() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }

echo "in:  $(printf '%.3f' "$(dur "$IN")") s, $(level "$IN")"
SIL=$(ffmpeg -hide_banner -nostats -i "$IN" -af silencedetect=noise=-50dB:d=0.02 -f null - 2>&1 \
  | grep -oE 'silence_(start|end): [0-9.]+' | tr '\n' ' ')
echo "     silences below -50 dB: ${SIL:-none}"
# Keep from the end of a leading silence (minus 5 ms) to the start of the trailing silence (plus 30 ms of
# decay). A silence counts as trailing when it reaches to within 60 ms of the end: what follows it is padding.
read -r START END <<< "$(echo "$SIL" | awk -v d="$(dur "$IN")" '{
  s = 0; e = d; n = split($0, f, " ")
  for (i = 1; i < n; i += 2) {
    v = f[i + 1] + 0
    if (f[i] == "silence_start:") { last = v; open = 1; if (v < 0.001) lead = 1 }
    else { if (lead == 1) { s = (v > 0.005 ? v - 0.005 : 0); lead = 2 } open = 0; endv = v }
  }
  if (open || endv >= d - 0.06) { if (last > s) e = last + 0.03 }
  if (e > d) e = d
  printf "%.3f %.3f", s, e }')"
ffmpeg -hide_banner -loglevel error -y -ss "$START" -t "$(awk -v s="$START" -v e="$END" 'BEGIN { printf "%.3f", e - s }')" \
  -i "$IN" -ar 48000 "$TMP/trim.wav"
D=$(dur "$TMP/trim.wav")
FADE_AT=$(awk -v d="$D" 'BEGIN { printf "%.3f", (d > 0.04 ? d - 0.02 : 0) }')
GAIN=""
if [ -n "$PEAK" ]; then
  MAX=$(level "$TMP/trim.wav" | sed -nE 's/.*max (-?[0-9.]+).*/\1/p')
  GAIN=",volume=$(awk -v p="$PEAK" -v m="$MAX" 'BEGIN { printf "%.2f", p - m }')dB"
fi
ffmpeg -hide_banner -loglevel error -y -i "$TMP/trim.wav" -af "afade=t=out:st=$FADE_AT:d=0.02$GAIN" \
  -c:a libvorbis -q:a 6 "$OUT"
echo "out: $(printf '%.3f' "$(dur "$OUT")") s, $(level "$OUT") $(wc -c < "$OUT" | tr -d ' ') bytes -> $OUT"
