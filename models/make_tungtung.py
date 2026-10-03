#!/usr/bin/env python3
"""Writes tung_tung_tung_sahur.obj/.mtl/.png: a wooden log with a face, stick arms and legs and a baseball bat.
The model looks along -Z (Blender's OBJ default); DIHClient turns it to face forward."""
import math, os, random
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
NAME = 'tung_tung_tung_sahur'
V, VT, F = [], [], {}   # F: material -> list of faces (tuples of (v, vt))

def vert(p, uv=(0, 0)):
    V.append(p); VT.append(uv)
    return len(V)

def sub(a, b): return (a[0]-b[0], a[1]-b[1], a[2]-b[2])
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def dot(a, b): return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
def norm(a):
    l = math.sqrt(dot(a, a)) or 1
    return (a[0]/l, a[1]/l, a[2]/l)

def tri(mat, a, b, c, ref):
    """Adds a triangle of vertex ids, flipped if needed so that it faces away from ref (a point inside the shape)."""
    pa, pb, pc = V[a-1], V[b-1], V[c-1]
    n = cross(sub(pb, pa), sub(pc, pa))
    cen = ((pa[0]+pb[0]+pc[0])/3, (pa[1]+pb[1]+pc[1])/3, (pa[2]+pb[2]+pc[2])/3)
    if dot(n, sub(cen, ref)) < 0:
        b, c = c, b
    F.setdefault(mat, []).append((a, b, c))

def lathe(mat, rings, rx, rz, seg=40, cx=0.0, cz=0.0):
    """rings: [(y, scale)], scale 0 is a pole. Front (-Z) is u = 0.5, u grows towards the viewer's right."""
    ids = []
    ymin, ymax = rings[0][0], rings[-1][0]
    for y, s in rings:
        row = []
        for j in range(seg + 1):
            a = -math.pi + 2*math.pi*j/seg
            p = (cx - rx*s*math.sin(a), y, cz - rz*s*math.cos(a))
            row.append(vert(p, (j/seg, 1 - (y - ymin)/(ymax - ymin))))
        ids.append(row)
    for i in range(len(rings) - 1):
        for j in range(seg):
            a, b, c, d = ids[i][j], ids[i][j+1], ids[i+1][j+1], ids[i+1][j]
            ref = (cx, (rings[i][0] + rings[i+1][0])/2, cz)
            if rings[i][1] > 0: tri(mat, a, b, c, ref)
            if rings[i+1][1] > 0: tri(mat, a, c, d, ref)
    # flat bottom
    ref = (cx, (ymin + ymax)/2, cz)
    if rings[0][1] > 0:
        mid = vert((cx, ymin, cz), (0.5, 1))
        for j in range(seg):
            tri(mat, mid, ids[0][j], ids[0][j+1], ref)

def sphere(mat, c, rx, ry, rz, seg=16, rings=10):
    rr = [(c[1] - ry*math.cos(math.pi*i/rings), math.sin(math.pi*i/rings)) for i in range(rings + 1)]
    ids = []
    for y, s in rr:
        row = []
        for j in range(seg + 1):
            a = -math.pi + 2*math.pi*j/seg
            row.append(vert((c[0] - rx*s*math.sin(a), y, c[2] - rz*s*math.cos(a)), (j/seg, 0.5)))
        ids.append(row)
    for i in range(rings):
        for j in range(seg):
            a, b, cc, d = ids[i][j], ids[i][j+1], ids[i+1][j+1], ids[i+1][j]
            if i > 0: tri(mat, a, b, cc, c)
            if i < rings - 1: tri(mat, a, cc, d, c)

def tube(mat, p0, p1, r0, r1, seg=10, caps=True):
    ax = norm(sub(p1, p0))
    helper = (1, 0, 0) if abs(ax[0]) < 0.9 else (0, 1, 0)
    u = norm(cross(ax, helper)); w = cross(ax, u)
    rings = []
    for k, (p, r) in enumerate(((p0, r0), (p1, r1))):
        ring = []
        for j in range(seg):
            c, sn = math.cos(2*math.pi*j/seg), math.sin(2*math.pi*j/seg)
            ring.append(vert((p[0] + r*(u[0]*c + w[0]*sn), p[1] + r*(u[1]*c + w[1]*sn), p[2] + r*(u[2]*c + w[2]*sn)), (j/seg, k)))
        rings.append(ring)
    mid = ((p0[0]+p1[0])/2, (p0[1]+p1[1])/2, (p0[2]+p1[2])/2)
    for j in range(seg):
        k = (j + 1) % seg
        tri(mat, rings[0][j], rings[0][k], rings[1][k], mid)
        tri(mat, rings[0][j], rings[1][k], rings[1][j], mid)
    if caps:
        for p, ring in ((p0, rings[0]), (p1, rings[1])):
            c = vert(p)
            for j in range(seg):
                tri(mat, c, ring[j], ring[(j + 1) % seg], mid)

# ---- the figure (units: metres, feet on y=0, looking along -Z)
RX, RZ = 0.27, 0.23
BODY = [(0.72, 0.78), (0.74, 0.93), (0.80, 1.0), (1.62, 1.0), (1.70, 0.95), (1.75, 0.80), (1.78, 0.55), (1.80, 0.25), (1.805, 0.0)]
lathe('wood', BODY, RX, RZ)
# face parts
for sx in (-1, 1):
    sphere('white', (sx*0.105, 1.58, -0.205), 0.085, 0.1, 0.085)
    sphere('black', (sx*0.105, 1.575, -0.275), 0.04, 0.045, 0.03)
sphere('woodDark', (0, 1.46, -0.235), 0.03, 0.075, 0.05)
# arms: shoulder, elbow, hand
for sx in (-1, 1):
    sh, el, ha = (sx*0.29, 1.36, -0.02), (sx*0.38, 1.12, -0.06), (sx*0.34, 0.86, -0.18)
    tube('woodDark', sh, el, 0.032, 0.028)
    tube('woodDark', el, ha, 0.028, 0.026)
    sphere('woodDark', sh, 0.04, 0.04, 0.04, 8, 6)
    sphere('woodDark', el, 0.034, 0.034, 0.034, 8, 6)
    sphere('woodDark', ha, 0.05, 0.05, 0.05, 8, 6)
# the bat leans on the figure's left hand (+X) and reaches forward and down
hand = (0.34, 0.86, -0.18)
tube('bat', (hand[0] - 0.02, hand[1] + 0.2, hand[2] + 0.06), (0.52, 0.07, -0.78), 0.028, 0.068, 14)
sphere('bat', (hand[0] - 0.02, hand[1] + 0.2, hand[2] + 0.06), 0.034, 0.034, 0.034, 8, 6)
sphere('bat', (0.52, 0.07, -0.78), 0.068, 0.068, 0.068, 12, 8)
# legs and feet
for sx in (-1, 1):
    hip, knee, ank = (sx*0.1, 0.75, 0.0), (sx*0.11, 0.4, 0.01), (sx*0.115, 0.07, 0.0)
    tube('woodDark', hip, knee, 0.034, 0.03)
    tube('woodDark', knee, ank, 0.03, 0.028)
    sphere('woodDark', knee, 0.034, 0.034, 0.034, 8, 6)
    sphere('woodDark', (sx*0.115, 0.035, -0.06), 0.055, 0.04, 0.115, 10, 6)

# ---- texture of the log: wood grain, brows, smile, cheeks (u = angle around the log, v = height)
W, H = 1024, 1024
random.seed(7)
img = Image.new('RGB', (W, H), (196, 132, 62))
px = img.load()
for x in range(W):
    g = 0.5 + 0.5*math.sin(x*0.19 + 2*math.sin(x*0.031)) * 0.6 + random.random()*0.15
    for y in range(H):
        wob = math.sin(y*0.012 + x*0.05)*0.04
        k = 0.82 + 0.24*(g + wob)
        px[x, y] = (int(196*k), int(132*k), int(62*k))
img = img.filter(ImageFilter.GaussianBlur(0.8))
d = ImageDraw.Draw(img, 'RGBA')
for i in range(40):  # darker grain streaks
    x = random.randrange(W); h0 = random.randrange(H); l = random.randrange(80, 400)
    d.line([(x, h0), (x + random.randint(-6, 6), h0 + l)], fill=(120, 70, 30, 70), width=random.choice((1, 2, 3)))
# the front is at u = 0.5. The log is 1.08 m tall over 1024 px and about 1.5 m round over 1024 px: x is squeezed by 0.66
def X(dx):  # metres to the right of the centre line (viewer's right) -> pixel
    return 512 + dx/1.5*W
def Y(y):   # world y -> pixel
    return (1 - (y - 0.72)/(1.805 - 0.72))*H
dark = (60, 30, 15, 255)
# brows
for sx in (-1, 1):
    pts = [(X(sx*0.04), Y(1.70)), (X(sx*0.10), Y(1.725)), (X(sx*0.17), Y(1.70))] if sx > 0 else [(X(sx*0.17), Y(1.70)), (X(sx*0.10), Y(1.725)), (X(sx*0.04), Y(1.70))]
    d.line(pts, fill=dark, width=9, joint='curve')
# smile: wide, thin lips with a darker mouth
mouth = [(X(-0.12 + 0.24*t/20), Y(1.33 - 0.07*math.sin(math.pi*t/20))) for t in range(21)]
d.polygon(mouth + [(X(0.12 - 0.24*t/20), Y(1.33 - 0.045*math.sin(math.pi*t/20) + 0.0)) for t in range(21)], fill=(110, 40, 30, 255))
d.line(mouth, fill=dark, width=8, joint='curve')
d.line([(X(-0.12 + 0.24*t/20), Y(1.33 - 0.045*math.sin(math.pi*t/20))) for t in range(21)], fill=dark, width=5)
d.line([(X(-0.12), Y(1.335)), (X(-0.14), Y(1.36))], fill=dark, width=6)
d.line([(X(0.12), Y(1.335)), (X(0.14), Y(1.36))], fill=dark, width=6)
# cheeks and nose shadow
for sx in (-1, 1):
    d.ellipse([X(sx*0.17) - 22, Y(1.43) - 14, X(sx*0.17) + 22, Y(1.43) + 14], fill=(210, 110, 60, 90))
img = img.filter(ImageFilter.GaussianBlur(0.6))
img.save(os.path.join(OUT, NAME + '.png'))

MATS = {
    'wood': ('1.0 1.0 1.0', NAME + '.png'),
    'woodDark': ('0.62 0.40 0.20', None),
    'bat': ('0.80 0.55 0.28', None),
    'white': ('0.97 0.97 0.95', None),
    'black': ('0.04 0.03 0.03', None),
}
with open(os.path.join(OUT, NAME + '.mtl'), 'w') as m:
    for k, (kd, tex) in MATS.items():
        m.write('newmtl %s\nKd %s\n%s\n' % (k, kd, ('map_Kd ' + tex + '\n') if tex else ''))
with open(os.path.join(OUT, NAME + '.obj'), 'w') as o:
    o.write('# Tung Tung Tung Sahur, made for DIHClient CustomModel\nmtllib %s.mtl\n' % NAME)
    for p in V: o.write('v %.4f %.4f %.4f\n' % p)
    for t in VT: o.write('vt %.4f %.4f\n' % (t[0], 1 - t[1]))   # OBJ has v pointing up
    for mat, faces in F.items():
        o.write('usemtl %s\n' % mat)
        for a, b, c in faces: o.write('f %d/%d %d/%d %d/%d\n' % (a, a, b, b, c, c))
print(len(V), 'vertices', sum(len(f) for f in F.values()), 'triangles')
