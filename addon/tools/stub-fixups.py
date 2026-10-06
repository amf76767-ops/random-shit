#!/usr/bin/env python3
"""Small corrections to the stubs GenStubs.py makes, for code that is compiled without the real Minecraft jar.
usage: stub-fixups.py <gen/net/minecraft>"""
import pathlib
import sys

root = pathlib.Path(sys.argv[1])

FIXES = {
    'class_2596.java': [('java.lang.Class getClass();', '')],
    'class_11719.java': [('public static class class_11721  {', 'public static class class_11721 extends net.minecraft.class_11719 {')],
    'class_6880.java': [(
        'public static class class_6883  {',
        'public static class class_6883 implements net.minecraft.class_6880 { '
        'public boolean method_40225(net.minecraft.class_5321 a0) { return false; }',
    )],
}

for name, pairs in FIXES.items():
    f = root / name
    if f.exists():
        text = f.read_text()
        for old, new in pairs:
            text = text.replace(old, new)
        f.write_text(text)
