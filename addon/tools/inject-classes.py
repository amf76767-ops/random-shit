#!/usr/bin/env python3
"""Copies a jar and swaps in freshly compiled classes (the old versions of the same top-level classes are dropped).
usage: inject-classes.py <in.jar> <out.jar> <classes dir> <version>"""
import json
import os
import sys
import zipfile

src, out, classes, version = sys.argv[1:5]
new = {}
for root, _, files in os.walk(classes):
    for f in files:
        full = os.path.join(root, f)
        new[os.path.relpath(full, classes).replace(os.sep, '/')] = full
tops = {name[:-6].split('$')[0] for name in new if name.endswith('.class')}

with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        name = info.filename
        if name.endswith('.class') and name[:-6].split('$')[0] in tops:
            continue
        data = zin.read(name)
        if name == 'fabric.mod.json':
            meta = json.loads(data)
            meta['version'] = version
            data = json.dumps(meta, indent=2).encode()
        zout.writestr(info, data)
    for name, full in sorted(new.items()):
        zout.write(full, name)
print('wrote', out, '-', len(new), 'classes replaced')
