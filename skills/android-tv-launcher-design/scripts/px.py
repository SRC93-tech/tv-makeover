#!/usr/bin/env python3
"""Compare pixels between two PNGs (e.g. a Figma export and a TV screenshot, both 1920x1080).

Usage: python3 -I px.py design.png tv.png X,Y [X,Y ...]
Prints the RGB of each point in both images. Pure Python (no Pillow): 8-bit RGB/RGBA, non-interlaced PNGs.
"""
import struct
import sys
import zlib


def load(path):
    """Return (rows, bytes_per_pixel); rows[y][x * bpp : x * bpp + 3] is the RGB of a pixel."""
    data = open(path, 'rb').read()
    pos, idat = 8, b''
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b'IHDR':
            width, height, depth, ctype = struct.unpack('>IIBB', body[:10])
            if depth != 8 or ctype not in (2, 6):
                sys.exit(f'{path}: only 8-bit RGB/RGBA PNGs are supported')
        elif kind == b'IDAT':
            idat += body
    bpp = 4 if ctype == 6 else 3
    raw, stride, rows, prev = zlib.decompress(idat), width * bpp, [], bytearray(width * bpp)
    for y in range(height):
        kind = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + a) & 255
            elif kind == 2:
                line[i] = (line[i] + b) & 255
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        prev = line
    return rows, bpp


if __name__ == '__main__':
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    images = [load(p) for p in sys.argv[1:3]]
    for point in sys.argv[3:]:
        x, y = map(int, point.split(','))
        print(point.ljust(11), '  '.join(str(tuple(rows[y][x * b:x * b + 3])) for rows, b in images))
