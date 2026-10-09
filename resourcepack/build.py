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
    return png(16, 16, stone_px((52, 52, 58), (96, 96, 104), 20261003))


def stone_px(dark, light, seed):
    rng = random.Random(seed)
    grid = [[rng.random() for _ in range(16)] for _ in range(16)]
    px = []
    for y in range(16):
        for x in range(16):
            soft = (grid[y][x] + grid[y][(x + 1) % 16] + grid[y][(x + 2) % 16] + grid[(y + 1) % 16][x]) / 4
            band = 0.5 + 0.5 * math.sin((y + 0.7 * math.sin(x * 0.9)) * math.pi / 2.6)
            v = clamp01(0.45 * soft + 0.30 * band + 0.25 * rng.random())
            if rng.random() < 0.05:
                v *= 0.55  # kleine dunkle Risse
            c = lerp(dark, light, v)
            px.append((c[0], c[1], min(255, c[2] + 3), 255))
    return px


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


FLAT = {
    "gravel": (131, 127, 126), "sand": (219, 207, 163), "red_sand": (190, 102, 33), "dirt": (134, 96, 67),
    "coarse_dirt": (119, 85, 59), "rooted_dirt": (144, 103, 76), "andesite": (136, 136, 136), "diorite": (188, 188, 188),
    "granite": (149, 103, 85), "tuff": (108, 109, 102), "clay": (160, 166, 179), "netherrack": (97, 38, 38),
    "soul_sand": (81, 62, 50), "soul_soil": (75, 57, 46), "mud": (60, 57, 60),
}


def flat(color):
    return png(16, 16, [(color[0], color[1], color[2], 255)] * 256)


def crosshair():
    px = [(0, 0, 0, 0)] * (15 * 15)
    for i in range(15):
        if abs(i - 7) >= 3 and abs(i - 7) <= 6:
            px[7 * 15 + i] = (255, 255, 255, 255)
            px[i * 15 + 7] = (255, 255, 255, 255)
    px[7 * 15 + 7] = (255, 255, 255, 255)
    return png(15, 15, px)


def display(entries):
    return {k: {"rotation": r, "translation": t, "scale": [sc, sc, sc]} for k, (r, t, sc) in entries.items()}


def shield_models():
    base = {"gui_light": "front", "textures": {"particle": "block/dark_oak_planks"}}
    normal = dict(base, display=display({
        "thirdperson_righthand": ([0, 90, 0], [10, 6, -4], 1), "thirdperson_lefthand": ([0, 90, 0], [10, 6, 12], 1),
        "firstperson_righthand": ([0, 180, 5], [-10, -4, -10], 1.25), "firstperson_lefthand": ([0, 180, 5], [10, -6, -10], 1.25),
        "gui": ([15, -25, -5], [2, 3, 0], 0.65), "fixed": ([0, 180, 0], [-4.5, 4.5, -5], 0.55),
        "on_shelf": ([0, 0, 0], [11, 18.5, 8.7], 1.4), "ground": ([0, 0, 0], [2, 4, 2], 0.25)}))
    blocking = dict(base, display=display({
        "thirdperson_righthand": ([45, 155, 0], [-3.49, 11, -2], 1), "thirdperson_lefthand": ([45, 155, 0], [11.51, 7, 2.5], 1),
        "firstperson_righthand": ([0, 180, -5], [-15, -1, -11], 1.25), "firstperson_lefthand": ([0, 180, -5], [5, -1, -11], 1.25),
        "gui": ([15, -25, -5], [2, 3, 0], 0.65)}))
    return normal, blocking


def small_hand_models():
    generated = {"parent": "builtin/generated", "gui_light": "front", "display": display({
        "ground": ([0, 0, 0], [0, 2, 0], 0.5), "head": ([0, 180, 0], [0, 13, 7], 1),
        "thirdperson_righthand": ([0, 0, 0], [0, 3, 1], 0.55),
        "firstperson_righthand": ([0, -90, 25], [1.13, 2.2, 1.13], 0.48), "fixed": ([0, 180, 0], [0, 0, 0], 1)})}
    handheld = {"parent": "item/generated", "display": display({
        "thirdperson_righthand": ([0, -90, 55], [0, 4.0, 0.5], 0.85), "thirdperson_lefthand": ([0, 90, -55], [0, 4.0, 0.5], 0.85),
        "firstperson_righthand": ([0, -90, 25], [1.13, 2.2, 1.13], 0.48), "firstperson_lefthand": ([0, 90, -25], [1.13, 2.2, 1.13], 0.48)})}
    block = {"gui_light": "side", "display": display({
        "gui": ([30, 225, 0], [0, 0, 0], 0.625), "ground": ([0, 0, 0], [0, 3, 0], 0.25), "fixed": ([0, 0, 0], [0, 0, 0], 0.5),
        "on_shelf": ([0, 180, 0], [0, 0, 0], 1), "thirdperson_righthand": ([75, 45, 0], [0, 2.5, 0], 0.375),
        "firstperson_righthand": ([0, 45, 0], [0, -1, 0], 0.3), "firstperson_lefthand": ([0, 225, 0], [0, -1, 0], 0.3)})}
    return generated, handheld, block


def quiet_sounds():
    rain = ["ambient/weather/rain%d" % i for i in range(1, 9)]
    thunder = ["ambient/weather/thunder%d" % i for i in range(1, 4)]
    return {
        "ambient.cave": {"replace": True, "sounds": []},
        "weather.rain": {"replace": True, "sounds": [{"name": n, "volume": 0.25} for n in rain], "subtitle": "subtitles.weather.rain"},
        "weather.rain.above": {"replace": True, "sounds": [{"name": n, "volume": 0.2} for n in rain[:4]], "subtitle": "subtitles.weather.rain"},
        "entity.lightning_bolt.thunder": {"replace": True, "sounds": [{"name": n, "volume": 0.3} for n in thunder],
                                          "subtitle": "subtitles.entity.lightning_bolt.thunder"},
    }


def files():
    mc = "assets/minecraft/"
    yield "pack.mcmeta", json.dumps({"pack": {"pack_format": FORMAT, "min_format": FORMAT, "max_format": FORMAT,
                                              "description": "DIH Visuals: deepslate-style stone, clear water, see-through clouds, starry night sky without moon, sky gradient, colour glint, small totem"}}, indent=2).encode()
    yield mc + "textures/block/stone.png", stone()
    yield mc + "textures/block/deepslate.png", stone()
    yield mc + "textures/block/deepslate_top.png", stone()
    yield mc + "textures/misc/enchanted_glint_item.png", glint()
    yield mc + "textures/misc/enchanted_glint_armor.png", glint()
    yield mc + "textures/block/water_still.png", water(16, 32, False)
    yield mc + "textures/block/water_still.png.mcmeta", json.dumps({"animation": {"frametime": 2}}).encode()
    yield mc + "textures/block/water_flow.png", water(32, 32, True)
    yield mc + "textures/block/water_flow.png.mcmeta", json.dumps({"animation": {}}).encode()
    yield mc + "textures/environment/celestial/sun.png", sun()
    for phase in ("full_moon", "waning_gibbous", "third_quarter", "waning_crescent", "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"):
        yield mc + "textures/environment/celestial/moon/" + phase + ".png", png(32, 32, [(0, 0, 0, 0)] * (32 * 32))
    yield mc + "textures/environment/rain.png", png(32, 32, [(0, 0, 0, 0)] * (32 * 32))  # Regen unsichtbar (Geräusch bleibt)
    glint_meta = json.dumps({"texture": {"blur": True}}).encode()
    yield mc + "textures/misc/enchanted_glint_item.png.mcmeta", glint_meta
    yield mc + "textures/misc/enchanted_glint_armor.png.mcmeta", glint_meta
    for path in sorted((ROOT / "static").rglob("*")):
        if path.is_file():
            yield path.relative_to(ROOT / "static").as_posix(), path.read_bytes()
    for name, color in FLAT.items():
        yield mc + "textures/block/" + name + ".png", flat(color)
    normal, blocking = shield_models()
    yield mc + "models/item/shield.json", json.dumps(normal, indent=2).encode()
    yield mc + "models/item/shield_blocking.json", json.dumps(blocking, indent=2).encode()
    generated, handheld, block = small_hand_models()
    yield mc + "models/item/generated.json", json.dumps(generated, indent=2).encode()
    yield mc + "models/item/handheld.json", json.dumps(handheld, indent=2).encode()
    yield mc + "models/block/block.json", json.dumps(block, indent=2).encode()
    yield mc + "textures/gui/sprites/hud/crosshair.png", crosshair()
    yield mc + "sounds.json", json.dumps(quiet_sounds(), indent=2).encode()
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
