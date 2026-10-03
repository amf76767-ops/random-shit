#!/usr/bin/env python3
"""Prüft die Shader des Packs mit glslangValidator (apt install glslang-tools).
usage: validate_shaders.py <entpackte Vanilla-Assets, enthält shaders/include>
Ersetzt '#moj_import <minecraft:x.glsl>' durch den Inhalt und lässt jede Datei als GLSL 330 übersetzen."""
import re, subprocess, sys
from pathlib import Path

vanilla = Path(sys.argv[1]) / "assets/minecraft/shaders"
mine = Path(__file__).resolve().parent / "static/assets/minecraft/shaders"
imp = re.compile(r"#moj_import\s*<minecraft:([\w./]+)>")

def expand(src, seen=()):
    def sub(m):
        name = m.group(1)
        if name in seen:
            return ""
        f = (mine / "include" / name)
        f = f if f.exists() else vanilla / "include" / name
        body = expand(f.read_text(), seen + (name,))
        return re.sub(r"^#version.*$", "", body, flags=re.M)
    return imp.sub(sub, src)

bad = 0
for f in sorted(mine.rglob("*.[vf]sh")):
    out = Path("/tmp") / ("check_" + f.name)
    out.write_text(expand(f.read_text()))
    stage = "vert" if f.suffix == ".vsh" else "frag"
    r = subprocess.run(["glslangValidator", "-S", stage, "-d", str(out)], capture_output=True, text=True)
    ok = r.returncode == 0
    print(("ok   " if ok else "FEHLER ") + f.relative_to(mine).as_posix())
    if not ok:
        bad += 1
        print(r.stdout + r.stderr)
sys.exit(1 if bad else 0)
