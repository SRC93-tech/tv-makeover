#!/usr/bin/env python3
import struct, sys
# Print the variation axes (tag, min, default, max) of a TrueType font's fvar table.
for path in sys.argv[1:]:
    d = open(path, 'rb').read()
    n = struct.unpack('>H', d[4:6])[0]
    for i in range(n):
        tag, _, off, ln = struct.unpack('>4sIII', d[12 + 16 * i:28 + 16 * i])
        if tag == b'fvar':
            ao, _, ac, asz = struct.unpack('>HHHH', d[off + 4:off + 12])
            for a in range(ac):
                t, mn, df, mx = struct.unpack('>4siii', d[off + ao + a * asz:off + ao + a * asz + 16])
                print(path.split('/')[-1], t.decode(), mn / 65536, df / 65536, mx / 65536)
