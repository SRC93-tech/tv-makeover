#!/usr/bin/env python3
"""Set values in the launcher's res/values/config.xml, keeping its comments.

Layers are applied in order, later ones win:
  --json FILE[:KEY]   a JSON object of config keys (KEY picks a nested object, e.g. template.json:config)
  --dock a,b,c,d      dock_apps, in order
  --set key=value     any key; true/false for bools, numbers for integers, comma lists for arrays

Usage: python3 -I launcher_config.py <config.xml> [--json f[:k]]... [--dock list] [--set k=v]...
Only keys that already exist in config.xml are accepted, so a typo fails instead of being ignored.
"""
import json
import re
import sys
from xml.sax.saxutils import escape


def element(xml, key):
    m = re.search(r'(<(string-array|integer-array|string|bool|integer) name="%s"[^>]*>)(.*?)(</\2>)' % re.escape(key), xml, re.S)
    if not m:
        sys.exit(f'config.xml has no key {key!r}')
    return m


def render(kind, value, indent):
    if kind in ('string-array', 'integer-array'):
        items = value if isinstance(value, list) else [v for v in str(value).split(',') if v.strip()]
        if not items:
            return '\n' + indent
        return ''.join(f'\n{indent}    <item>{escape(str(v).strip())}</item>' for v in items) + '\n' + indent
    if kind == 'bool':
        if isinstance(value, str):
            value = value.strip().lower() == 'true'
        return 'true' if value else 'false'
    if kind == 'integer':
        return str(int(value))
    return escape(str(value))


def apply(xml, key, value):
    m = element(xml, key)
    indent = re.search(r'([ \t]*)$', xml[:m.start()]).group(1)
    return xml[:m.start()] + m.group(1) + render(m.group(2), value, indent) + m.group(4) + xml[m.end():]


def main():
    args = sys.argv[1:]
    if not args or args[0].startswith('-'):
        sys.exit(__doc__)
    path, args = args[0], args[1:]
    xml = open(path, encoding='utf-8').read()
    changed = []
    i = 0
    while i < len(args):
        flag, val = args[i], args[i + 1] if i + 1 < len(args) else None
        if val is None:
            sys.exit(f'{flag} needs a value')
        if flag == '--json':
            file, _, key = val.partition(':')
            data = json.load(open(file, encoding='utf-8'))
            for part in filter(None, key.split('.')):
                data = data.get(part, {})
            pairs = list(data.items())
        elif flag == '--dock':
            pairs = [('dock_apps', val)]
        elif flag == '--set':
            k, _, v = val.partition('=')
            pairs = [(k.strip(), v)]
        else:
            sys.exit(f'unknown option {flag}')
        for k, v in pairs:
            xml = apply(xml, k, v)
            changed.append(k)
        i += 2
    open(path, 'w', encoding='utf-8').write(xml)
    print(f'{path}: set {", ".join(dict.fromkeys(changed)) or "nothing"}')


if __name__ == '__main__':
    main()
