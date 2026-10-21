#!/usr/bin/env python3
"""Convert a simple SVG icon (Figma/Lucide exports) into an Android VectorDrawable.

Usage: python3 -I svg_to_vector.py icon.svg out.xml [--density 2] [--color '#FFFFFFFF']
--density: the TV's density / 160 (320 dpi -> 2). A 38px icon on a 1080p design becomes 19dp.
--color: replaces currentColor and white strokes/fills, so a selector tint (android:drawableTint) can recolour it.

Handles <path>, <circle>, <rect> (with rx), <line>, <polyline>, <polygon> with fill, stroke, stroke-width,
stroke-linecap/linejoin, fill-rule and opacity. Group transforms are not applied: export flattened from Figma.
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET

NAMED = {'white': '#FFFFFF', 'black': '#000000', 'none': None}


def argb(value, opacity, fallback):
    if value is None or value == 'none':
        return None
    if value == 'currentColor':
        value = fallback
    value = NAMED.get(value, value)
    m = re.fullmatch(r'#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})', value or '')
    if not m:
        sys.exit(f'unsupported colour {value!r}')
    h = m.group(1)
    if len(h) == 3:
        h = ''.join(c * 2 for c in h)
    if len(h) == 6:
        h = f'{round(255 * opacity):02X}' + h
    return '#' + h.upper()


def path_of(el):
    tag = el.tag.split('}')[-1]
    f = lambda k, d=0.0: float(el.get(k, d))
    if tag == 'path':
        return el.get('d')
    if tag == 'circle':
        cx, cy, r = f('cx'), f('cy'), f('r')
        return f'M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0Z'
    if tag == 'rect':
        x, y, w, h = f('x'), f('y'), f('width'), f('height')
        rx = min(f('rx', el.get('ry', 0)), w / 2)
        if rx <= 0:
            return f'M{x},{y}h{w}v{h}h{-w}Z'
        return (f'M{x + rx},{y}h{w - 2 * rx}a{rx},{rx} 0 0 1 {rx},{rx}v{h - 2 * rx}a{rx},{rx} 0 0 1 {-rx},{rx}'
                f'h{-(w - 2 * rx)}a{rx},{rx} 0 0 1 {-rx},{-rx}v{-(h - 2 * rx)}a{rx},{rx} 0 0 1 {rx},{-rx}Z')
    if tag == 'line':
        return f"M{f('x1')},{f('y1')}L{f('x2')},{f('y2')}"
    if tag in ('polyline', 'polygon'):
        pts = el.get('points', '').replace(',', ' ').split()
        pairs = [f'{pts[i]},{pts[i + 1]}' for i in range(0, len(pts) - 1, 2)]
        return 'M' + 'L'.join(pairs) + ('Z' if tag == 'polygon' else '')
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('svg')
    ap.add_argument('out')
    ap.add_argument('--density', type=float, default=2.0)
    ap.add_argument('--color', default='#FFFFFFFF')
    a = ap.parse_args()
    root = ET.parse(a.svg).getroot()
    vb = [float(v) for v in re.split(r'[\s,]+', root.get('viewBox', '').strip()) if v] or None
    w = float(re.sub(r'px$', '', root.get('width', str(vb[2] if vb else 24))))
    h = float(re.sub(r'px$', '', root.get('height', str(vb[3] if vb else 24))))
    vw, vh = (vb[2], vb[3]) if vb else (w, h)
    paths = []

    def walk(el, inherited):
        style = dict(inherited)
        for k in ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-rule', 'opacity'):
            if el.get(k) is not None:
                style[k] = el.get(k)
        if el.get('transform'):
            print(f'warning: transform on <{el.tag.split("}")[-1]}> ignored; flatten it in Figma', file=sys.stderr)
        d = path_of(el)
        if d:
            op = float(style.get('opacity', 1))
            attrs = [f'android:pathData="{d}"']
            fill = argb(style.get('fill', '#000000'), op, a.color)
            stroke = argb(style.get('stroke'), op, a.color)
            if fill:
                attrs.append(f'android:fillColor="{fill}"')
                if style.get('fill-rule') == 'evenodd':
                    attrs.append('android:fillType="evenOdd"')
            if stroke:
                attrs.append(f'android:strokeColor="{stroke}"')
                attrs.append(f'android:strokeWidth="{style.get("stroke-width", "1")}"')
                for k, ak in (('stroke-linecap', 'strokeLineCap'), ('stroke-linejoin', 'strokeLineJoin')):
                    if style.get(k):
                        attrs.append(f'android:{ak}="{style[k]}"')
            paths.append('    <path\n        ' + '\n        '.join(attrs) + ' />')
        for child in el:
            walk(child, style)

    walk(root, {})
    if not paths:
        sys.exit('no drawable shapes found')
    dp = lambda px: f'{px / a.density:g}dp'
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           f'    android:width="{dp(w)}" android:height="{dp(h)}"\n'
           f'    android:viewportWidth="{vw:g}" android:viewportHeight="{vh:g}">\n'
           + '\n'.join(paths) + '\n</vector>\n')
    open(a.out, 'w').write(xml)
    print(f'{a.out}: {len(paths)} path(s), {dp(w)} x {dp(h)}')


if __name__ == '__main__':
    main()
