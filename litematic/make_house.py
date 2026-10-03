#!/usr/bin/env python3
"""Erzeugt haus.litematic (Litematica-Schematic, Version 6, Minecraft 1.21.11 = Datenversion 4671). Nur Standardbibliothek."""
import gzip, math, struct, sys, time
from pathlib import Path

DATA_VERSION = 4671
SX, SY, SZ = 9, 8, 9          # Größe (x, y, z)

# ---------- NBT-Schreiber ----------
def _s(v):
    b = v.encode("utf-8")
    return struct.pack(">H", len(b)) + b

class Tag:
    def __init__(self, tid, payload): self.tid, self.payload = tid, payload

def Int(v): return Tag(3, struct.pack(">i", v))
def Long(v): return Tag(4, struct.pack(">q", v))
def Str(v): return Tag(8, _s(v))
def LongArray(vs): return Tag(12, struct.pack(">i", len(vs)) + b"".join(struct.pack(">q", v) for v in vs))
def List(tid, items):  # items: bereits kodierte Payloads
    return Tag(9, struct.pack(">bi", tid, len(items)) + b"".join(items))
def Compound(d):
    out = b""
    for k, t in d.items():
        out += struct.pack(">b", t.tid) + _s(k) + t.payload
    return Tag(10, out + b"\x00")

# ---------- Haus ----------
def build():
    blocks = {}   # (x,y,z) -> (name, props-tuple)
    def put(x, y, z, name, **props): blocks[(x, y, z)] = (name, tuple(sorted(props.items())))
    ring = [(x, z) for x in range(1, 8) for z in range(1, 8) if x in (1, 7) or z in (1, 7)]
    for x in range(1, 8):
        for z in range(1, 8):
            put(x, 0, z, "minecraft:oak_planks")                      # Boden
    for x, z in ring:
        put(x, 1, z, "minecraft:cobblestone")                         # Sockel
        for y in (2, 3, 4):
            put(x, y, z, "minecraft:birch_planks")                    # Wände
    for x, z in [(1, 1), (1, 7), (7, 1), (7, 7)]:
        for y in (1, 2, 3, 4):
            put(x, y, z, "minecraft:oak_log", axis="y")               # Eckpfosten
    for x, z in [(2, 7), (6, 7), (2, 1), (4, 1), (6, 1), (1, 4), (7, 4)]:
        for y in (2, 3):
            put(x, y, z, "minecraft:glass")                           # Fenster
    put(4, 1, 7, "minecraft:oak_door", facing="south", half="lower", hinge="left", open="false", powered="false")
    put(4, 2, 7, "minecraft:oak_door", facing="south", half="upper", hinge="left", open="false", powered="false")
    # Satteldach: Stufen laufen in x-Richtung von 0 bis 8
    for k in range(4):
        for x in range(0, 9):
            put(x, 4 + k, k, "minecraft:spruce_stairs", facing="south", half="bottom", shape="straight", waterlogged="false")
            put(x, 4 + k, 8 - k, "minecraft:spruce_stairs", facing="north", half="bottom", shape="straight", waterlogged="false")
    for x in range(0, 9):
        put(x, 7, 4, "minecraft:spruce_planks")                       # First
    for x in (1, 7):                                                  # Giebel
        for z in range(2, 7): put(x, 5, z, "minecraft:birch_planks")
        for z in range(3, 6): put(x, 6, z, "minecraft:birch_planks")
        put(x, 7, 4, "minecraft:birch_planks")
    # Einrichtung
    put(2, 1, 2, "minecraft:crafting_table")
    put(3, 1, 2, "minecraft:furnace", facing="south", lit="false")
    put(6, 1, 3, "minecraft:red_bed", facing="north", part="foot", occupied="false")
    put(6, 1, 2, "minecraft:red_bed", facing="north", part="head", occupied="false")
    put(4, 3, 2, "minecraft:wall_torch", facing="south")
    put(3, 3, 6, "minecraft:wall_torch", facing="north")
    put(5, 3, 6, "minecraft:wall_torch", facing="north")
    return blocks

def pack(indices, bits):
    n = len(indices)
    longs = [0] * ((n * bits + 63) // 64)
    mask = (1 << bits) - 1
    for i, v in enumerate(indices):
        start = i * bits
        a, off = start >> 6, start & 63
        longs[a] |= (v & mask) << off
        if off + bits > 64:
            longs[a + 1] |= (v & mask) >> (64 - off)
    return [x - (1 << 64) if x >= 1 << 63 else x & ((1 << 64) - 1) for x in (l & ((1 << 64) - 1) for l in longs)]

def main(out):
    blocks = build()
    palette = [("minecraft:air", ())]
    index = {palette[0]: 0}
    states = []
    for y in range(SY):
        for z in range(SZ):
            for x in range(SX):
                st = blocks.get((x, y, z), palette[0])
                if st not in index:
                    index[st] = len(palette)
                    palette.append(st)
                states.append(index[st])
    bits = max(2, math.ceil(math.log2(len(palette))))
    pal = []
    for name, props in palette:
        d = {"Name": Str(name)}
        if props:
            d["Properties"] = Compound({k: Str(v) for k, v in props})
        pal.append(Compound(d).payload)
    size = Compound({"x": Int(SX), "y": Int(SY), "z": Int(SZ)})
    now = int(time.time() * 1000)
    root = Compound({
        "Version": Int(6),
        "MinecraftDataVersion": Int(DATA_VERSION),
        "Metadata": Compound({
            "Name": Str("Kleines Haus"), "Author": Str("Claude"), "Description": Str("Kleines Haus mit Satteldach, Tür, Fenstern und Einrichtung"),
            "RegionCount": Int(1), "TimeCreated": Long(now), "TimeModified": Long(now),
            "TotalBlocks": Int(sum(1 for s in states if s)), "TotalVolume": Int(SX * SY * SZ), "EnclosingSize": size,
        }),
        "Regions": Compound({"Haus": Compound({
            "Position": Compound({"x": Int(0), "y": Int(0), "z": Int(0)}),
            "Size": size,
            "BlockStatePalette": List(10, pal),
            "BlockStates": LongArray(pack(states, bits)),
            "TileEntities": List(10, []), "Entities": List(10, []),
            "PendingBlockTicks": List(10, []), "PendingFluidTicks": List(10, []),
        })}),
    })
    # Wurzel: Compound mit leerem Namen
    data = struct.pack(">b", 10) + _s("") + root.payload
    Path(out).write_bytes(gzip.compress(data, 9, mtime=0))
    print(f"{out}: {len(palette)} Paletteneinträge, {bits} Bit, {sum(1 for s in states if s)} Blöcke von {len(states)}")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "haus.litematic")
