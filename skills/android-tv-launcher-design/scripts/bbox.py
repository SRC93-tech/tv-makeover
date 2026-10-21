#!/usr/bin/env python3
"""Bounding box of near-white pixels in a region, in each image: checks text and icon placement to the pixel.

Usage: python3 -I bbox.py X0,Y0,X1,Y1 THRESHOLD image.png [image.png ...]
THRESHOLD 245-250 picks white text or icons; keep the region clear of bright background (clouds, highlights).
"""
import os
import runpy
import sys

if len(sys.argv) < 4:
    sys.exit(__doc__)
load = runpy.run_path(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'px.py'))['load']
x0, y0, x1, y1 = map(int, sys.argv[1].split(','))
t = int(sys.argv[2])
for path in sys.argv[3:]:
    rows, b = load(path)
    xs, ys = [], []
    for y in range(y0, y1):
        r = rows[y]
        for x in range(x0, x1):
            if r[x * b] >= t and r[x * b + 1] >= t and r[x * b + 2] >= t:
                xs.append(x)
                ys.append(y)
    print(os.path.basename(path).ljust(28), (min(xs), min(ys), max(xs), max(ys)) if xs else None)
