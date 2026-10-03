#!/usr/bin/env python3
"""Baut das Resourcepack DIH-Visuals-1.21.11.zip. Nur Python-Standardbibliothek, alle Texturen entstehen im Code (keine fremden Dateien)."""
import json, math, random, struct, sys, zipfile, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT.parent / "dist" / "DIH-Visuals-1.21.11.zip"
FORMAT = 75  # Resourcepack-Version von Minecraft 1.21.11


def png(w, h, px):
    """px: Liste von (r,g,b,a), Zeile für Zeile."""
    raw = b"".join(b"\x00" + bytes(v for p in px[y * w:(y + 1) * w] for v in p) for y in range(h))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def clamp01(v):
    return max(0.0, min(1.0, v))


def stone():
    """Dunkler, geschichteter Stein im Stil von Tiefenschiefer. 16x16."""
    rng = random.Random(20261003)
    dark, light = (52, 52, 58), (96, 96, 104)
    grid = [[rng.random() for _ in range(16)] for _ in range(16)]
    px = []
    for y in range(16):
        for x in range(16):
            # weiche Lagen (waagerecht gestreckt) plus Pixelrauschen
            soft = (grid[y][x] + grid[y][(x + 1) % 16] + grid[y][(x + 2) % 16] + grid[(y + 1) % 16][x]) / 4
            band = 0.5 + 0.5 * math.sin((y + 0.7 * math.sin(x * 0.9)) * math.pi / 2.6)
            v = clamp01(0.45 * soft + 0.30 * band + 0.25 * rng.random())
            if rng.random() < 0.05:
                v *= 0.55  # kleine dunkle Risse
            c = lerp(dark, light, v)
            px.append((c[0], c[1], min(255, c[2] + 3), 255))
    return png(16, 16, px)


def glint():
    """Schillernder Verzauberungsglanz (Item und Rüstung). 128x128, schwarz = kein Glanz."""
    w = 128
    px = []
    for y in range(w):
        for x in range(w):
            u = (x + y) / w                      # diagonal, kachelt bei 64
            band = 0.5 + 0.5 * math.sin(2 * math.pi * (2 * u))
            band = band ** 3
            hue = 0.5 + 0.5 * math.sin(2 * math.pi * (u + (x - y) / (2.0 * w)))
            col = lerp((70, 220, 255), (190, 90, 255), hue)
            px.append((round(col[0] * band), round(col[1] * band), round(col[2] * band), 255))
    return png(w, w, px)


def sun():
    """Weiche, runde Sonne mit Lichthof (wird additiv gezeichnet). 32x32."""
    px = []
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 15.5) / 16.0
            core = clamp01((0.36 - r) / 0.06)
            halo = clamp01(math.exp(-(r - 0.30) * 5.5)) * clamp01((1.0 - r) / 0.35) if r > 0.30 else 1.0
            a = max(core, halo * 0.75)
            c = lerp((255, 190, 100), (255, 248, 215), core)
            px.append((c[0], c[1], c[2], round(255 * clamp01(a))))
    return png(32, 32, px)


def water(size, frames, flowing):
    """Animierter, durchscheinender Wasserstreifen (Frames untereinander, nahtlos). Alpha ca. 0.45."""
    rng = random.Random(77 if flowing else 78)
    waves = [(rng.choice([1, 2, 3]), rng.choice([1, 2, 3]), rng.random() * 6.28) for _ in range(5)]
    deep, shallow = (40, 92, 215), (105, 170, 250)
    px = []
    for f in range(frames):
        for y in range(size):
            for x in range(size):
                s = 0.0
                for kx, ky, ph in waves:
                    if flowing:
                        s += math.sin(2 * math.pi * (kx * x / size + ky * (y + f * size / frames) / size) + ph)
                    else:
                        s += math.sin(2 * math.pi * (kx * x / size + ky * y / size + f / frames) + ph)
                v = clamp01(0.5 + s / 10)
                c = lerp(deep, shallow, v)
                px.append((c[0], c[1], c[2], round(105 + 40 * v)))
    return png(size, size * frames, px)


def files():
    mc = "assets/minecraft/"
    yield "pack.mcmeta", json.dumps({"pack": {"pack_format": FORMAT, "min_format": FORMAT, "max_format": FORMAT,
                                              "description": "DIH Visuals: Tiefenschiefer-Stone, klares Wasser, durchsichtige Wolken, Himmelverlauf, Glanz, kleines Totem"}}, indent=2).encode()
    yield mc + "textures/block/stone.png", stone()
    yield mc + "textures/misc/enchanted_glint_item.png", glint()
    yield mc + "textures/misc/enchanted_glint_armor.png", glint()
    yield mc + "textures/block/water_still.png", water(16, 32, False)
    yield mc + "textures/block/water_still.png.mcmeta", json.dumps({"animation": {"frametime": 2}}).encode()
    yield mc + "textures/block/water_flow.png", water(32, 32, True)
    yield mc + "textures/block/water_flow.png.mcmeta", json.dumps({"animation": {}}).encode()
    yield mc + "textures/environment/celestial/sun.png", sun()
    yield mc + "textures/environment/rain.png", png(32, 32, [(0, 0, 0, 0)] * (32 * 32))  # Regen unsichtbar (Geräusch bleibt)
    glint_meta = json.dumps({"texture": {"blur": True}}).encode()
    yield mc + "textures/misc/enchanted_glint_item.png.mcmeta", glint_meta
    yield mc + "textures/misc/enchanted_glint_armor.png.mcmeta", glint_meta
    for path in sorted((ROOT / "static").rglob("*")):
        if path.is_file():
            yield path.relative_to(ROOT / "static").as_posix(), path.read_bytes()
    # Totem kleiner: gilt für Item-Rahmen und die Pop-Animation, falls sie den Kontext "fixed" nutzt
    yield mc + "models/item/totem_of_undying.json", json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "minecraft:item/totem_of_undying"},
        "display": {"fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]}}}, indent=2).encode()


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in files():
            zi = zipfile.ZipInfo(name, (2026, 10, 3, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, data)
            if len(sys.argv) > 1 and name.endswith(".png"):
                p = Path(sys.argv[1]) / Path(name).name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(data)
    print("geschrieben:", OUT)


if __name__ == "__main__":
    main()
