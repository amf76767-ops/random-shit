#!/usr/bin/env python3
"""Writes the little example robot (glTF binary) that ships with the client: boxes on a node tree with Idle and Walk animations.
Usage: make_example_model.py <out.glb>"""
import json, math, struct, sys, zlib

def png(w, h, px):
    raw = b''.join(b'\x00' + bytes(sum((list(px(x, y)) for x in range(w)), [])) for y in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')

def panel(x, y):
    base = (88, 104, 128, 255)
    if x in (0, 15) or y in (0, 15):
        return (52, 62, 80, 255)
    if y == 8 or x == 8:
        return (70, 84, 106, 255)
    if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        return (200, 210, 225, 255)
    return base

FACES = [  # normal, corners (counter-clockwise seen from outside)
    ((0, 0, 1), [(-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)]),
    ((0, 0, -1), [(1, -1, -1), (-1, -1, -1), (-1, 1, -1), (1, 1, -1)]),
    ((1, 0, 0), [(1, -1, 1), (1, -1, -1), (1, 1, -1), (1, 1, 1)]),
    ((-1, 0, 0), [(-1, -1, -1), (-1, -1, 1), (-1, 1, 1), (-1, 1, -1)]),
    ((0, 1, 0), [(-1, 1, 1), (1, 1, 1), (1, 1, -1), (-1, 1, -1)]),
    ((0, -1, 0), [(-1, -1, -1), (1, -1, -1), (1, -1, 1), (-1, -1, 1)]),
]
UVS = [(0, 1), (1, 1), (1, 0), (0, 0)]

def box(c, s):
    pos, nrm, uv, idx = [], [], [], []
    for n, corners in FACES:
        a = [(c[0] + x * s[0] / 2, c[1] + y * s[1] / 2, c[2] + z * s[2] / 2) for x, y, z in corners]
        u = (a[1][0] - a[0][0], a[1][1] - a[0][1], a[1][2] - a[0][2])
        v = (a[2][0] - a[0][0], a[2][1] - a[0][1], a[2][2] - a[0][2])
        cr = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
        assert cr[0] * n[0] + cr[1] * n[1] + cr[2] * n[2] > 0, 'face winding'
        base = len(pos)
        pos += a
        nrm += [n] * 4
        uv += UVS
        idx += [base, base + 1, base + 2, base, base + 2, base + 3]
    return pos, nrm, uv, idx

class Glb:
    def __init__(self):
        self.bin = bytearray()
        self.views, self.accessors = [], []

    def view(self, data):
        while len(self.bin) % 4:
            self.bin.append(0)
        self.views.append({'buffer': 0, 'byteOffset': len(self.bin), 'byteLength': len(data)})
        self.bin += data
        return len(self.views) - 1

    def acc(self, data, ctype, typ, count, minmax=None):
        a = {'bufferView': self.view(data), 'componentType': ctype, 'type': typ, 'count': count}
        if minmax:
            a['min'], a['max'] = minmax
        self.accessors.append(a)
        return len(self.accessors) - 1

def main(out):
    g = Glb()
    img = g.view(png(16, 16, panel))
    materials = [
        {'name': 'panel', 'pbrMetallicRoughness': {'baseColorTexture': {'index': 0}, 'baseColorFactor': [1, 1, 1, 1]}},
        {'name': 'metal', 'pbrMetallicRoughness': {'baseColorFactor': [0.72, 0.76, 0.82, 1]}},
        {'name': 'visor', 'pbrMetallicRoughness': {'baseColorFactor': [0.1, 0.85, 0.95, 1]}},
        {'name': 'orange', 'pbrMetallicRoughness': {'baseColorFactor': [0.95, 0.5, 0.1, 1]}},
        {'name': 'dark', 'pbrMetallicRoughness': {'baseColorFactor': [0.16, 0.18, 0.22, 1]}},
    ]
    meshes, nodes = [], []

    def mesh(parts):
        prims = []
        for (c, s), m in parts:
            pos, nrm, uv, idx = box(c, s)
            lo = [min(p[i] for p in pos) for i in range(3)]
            hi = [max(p[i] for p in pos) for i in range(3)]
            prims.append({'attributes': {
                'POSITION': g.acc(struct.pack('<%df' % (len(pos) * 3), *[v for p in pos for v in p]), 5126, 'VEC3', len(pos), ([*lo], [*hi])),
                'NORMAL': g.acc(struct.pack('<%df' % (len(nrm) * 3), *[v for p in nrm for v in p]), 5126, 'VEC3', len(nrm)),
                'TEXCOORD_0': g.acc(struct.pack('<%df' % (len(uv) * 2), *[v for p in uv for v in p]), 5126, 'VEC2', len(uv))},
                'indices': g.acc(struct.pack('<%dH' % len(idx), *idx), 5123, 'SCALAR', len(idx)), 'material': m})
        meshes.append({'primitives': prims})
        return len(meshes) - 1

    body = mesh([(((0, 1.1, 0), (0.5, 0.6, 0.28)), 0), (((0, 1.12, 0.145), (0.3, 0.2, 0.02)), 4), (((0, 0.82, 0), (0.46, 0.06, 0.26)), 3)])
    head = mesh([(((0, 0.17, 0), (0.34, 0.34, 0.32)), 1), (((0, 0.19, 0.165), (0.26, 0.08, 0.02)), 2), (((0, 0.4, 0), (0.03, 0.12, 0.03)), 3)])
    arm = mesh([(((0, -0.27, 0), (0.14, 0.6, 0.14)), 3), (((0, -0.6, 0), (0.16, 0.08, 0.16)), 4)])
    leg = mesh([(((0, -0.37, 0), (0.2, 0.74, 0.2)), 4), (((0, -0.77, 0.03), (0.22, 0.06, 0.28)), 1)])
    nodes = [
        {'name': 'Robot', 'children': [1, 2, 3, 4, 5, 6]},
        {'name': 'Body', 'mesh': body},
        {'name': 'Head', 'mesh': head, 'translation': [0, 1.45, 0]},
        {'name': 'ArmL', 'mesh': arm, 'translation': [0.34, 1.35, 0]},
        {'name': 'ArmR', 'mesh': arm, 'translation': [-0.34, 1.35, 0]},
        {'name': 'LegL', 'mesh': leg, 'translation': [0.13, 0.8, 0]},
        {'name': 'LegR', 'mesh': leg, 'translation': [-0.13, 0.8, 0]},
    ]

    def quat(axis, a):
        s, c = math.sin(a / 2), math.cos(a / 2)
        q = [0, 0, 0, c]
        q[axis] = s
        return q

    def anim(name, duration, keys, tracks):
        samplers, channels = [], []
        times = struct.pack('<%df' % len(keys), *keys)
        tacc = g.acc(times, 5126, 'SCALAR', len(keys), ([min(keys)], [max(keys)]))
        for node, axis, angles in tracks:
            vals = [v for a in angles for v in quat(axis, a)]
            samplers.append({'input': tacc, 'output': g.acc(struct.pack('<%df' % len(vals), *vals), 5126, 'VEC4', len(angles)), 'interpolation': 'LINEAR'})
            channels.append({'sampler': len(samplers) - 1, 'target': {'node': node, 'path': 'rotation'}})
        return {'name': name, 'samplers': samplers, 'channels': channels}

    swing = [math.sin(2 * math.pi * k / 4) for k in range(5)]
    walk = anim('Walk', 1.0, [0, .25, .5, .75, 1.0], [
        (5, 0, [0.6 * v for v in swing]), (6, 0, [-0.6 * v for v in swing]),
        (3, 0, [-0.5 * v for v in swing]), (4, 0, [0.5 * v for v in swing]),
        (2, 1, [0.08 * v for v in swing])])
    idle = anim('Idle', 3.0, [0, 1.5, 3.0], [
        (2, 1, [-0.3, 0.3, -0.3]), (3, 0, [0.04, -0.04, 0.04]), (4, 0, [-0.04, 0.04, -0.04])])
    doc = {'asset': {'version': '2.0', 'generator': 'DIHClient example'}, 'scene': 0, 'scenes': [{'nodes': [0]}],
           'nodes': nodes, 'meshes': meshes, 'materials': materials,
           'textures': [{'source': 0}], 'images': [{'bufferView': img, 'mimeType': 'image/png'}],
           'accessors': g.accessors, 'bufferViews': g.views, 'buffers': [{'byteLength': len(g.bin)}],
           'animations': [idle, walk]}
    js = json.dumps(doc, separators=(',', ':')).encode()
    js += b' ' * (-len(js) % 4)
    binary = bytes(g.bin) + b'\0' * (-len(g.bin) % 4)
    total = 12 + 8 + len(js) + 8 + len(binary)
    open(out, 'wb').write(struct.pack('<III', 0x46546C67, 2, total) + struct.pack('<II', len(js), 0x4E4F534A) + js
                          + struct.pack('<II', len(binary), 0x004E4942) + binary)

main(sys.argv[1])
