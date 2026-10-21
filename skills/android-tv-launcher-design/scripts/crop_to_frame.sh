#!/usr/bin/env bash
# Reproduce a Figma background exactly as placed in its frame: the source image covers a node of NODE_W x NODE_H
# at (LEFT, TOP) inside a FRAME_W x FRAME_H frame (object-cover), and the frame clips it. Usually simpler: download
# the node's `export` with the Figma MCP download_assets tool, which comes back already clipped to the frame.
# Usage: crop_to_frame.sh <source image> <out.jpg> NODE_W NODE_H LEFT TOP [FRAME_W FRAME_H] [JPEG_QUALITY 2-31]
#   e.g. crop_to_frame.sh raw.png wallpaper.jpg 1939 1091 -19 0 1920 1080
set -euo pipefail
IN="$1"; OUT="$2"; NW="$3"; NH="$4"; L="$5"; T="$6"; FW="${7:-1920}"; FH="${8:-1080}"; Q="${9:-3}"
[ "$L" -le 0 ] && [ "$T" -le 0 ] || { echo "LEFT and TOP must be <= 0 (a node that covers the frame)" >&2; exit 1; }
ffmpeg -hide_banner -loglevel error -y -i "$IN" \
  -vf "scale=$NW:$NH:force_original_aspect_ratio=increase:flags=lanczos,crop=$NW:$NH,crop=$FW:$FH:$(( -L )):$(( -T ))" \
  -q:v "$Q" "$OUT"
echo "$OUT $(wc -c < "$OUT" | tr -d ' ') bytes"
