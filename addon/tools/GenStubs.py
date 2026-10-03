#!/usr/bin/env python3
"""Erzeugt Java-Platzhalter für alle Minecraft-Klassen, die die Original-JAR benutzt (Name, Art, Signatur aus ihrem Bytecode).
Damit lassen sich Module neu übersetzen, ohne Minecraft zu haben. Was nicht aus dem Bytecode hervorgeht (Vererbung, Enums,
Generics), steht in stubs-hints.txt.
usage: GenStubs.py base.jar hints.txt outdir"""
import re, subprocess, sys, zipfile
from collections import defaultdict
from pathlib import Path

jar, hints, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
names = [n[:-6].replace('/', '.') for n in zipfile.ZipFile(jar).namelist() if n.endswith('.class') and n.startswith('dev/')]
txt = subprocess.run(['javap', '-c', '-p', '-cp', jar] + names, capture_output=True, text=True).stdout
ref = re.compile(r'(invoke\w+|getstatic|putstatic|getfield|putfield)\s+#\d+(?:,\s*\d+)?\s+// (Method|InterfaceMethod|Field) ((?:net/minecraft|com/mojang)/[^.]+)\.(\S+?):(\S+)')
members = defaultdict(lambda: {'m': {}, 'f': {}})
interfaces = set()
for op, kind, owner, name, desc in ref.findall(txt):
    name = name.strip('"')
    static = op in ('invokestatic', 'getstatic', 'putstatic')
    if kind == 'InterfaceMethod':
        interfaces.add(owner)
    if kind == 'Field':
        members[owner]['f'][name] = (desc, static)
    else:
        key = (name, desc)
        old = members[owner]['m'].get(key)
        members[owner]['m'][key] = static or bool(old)
# Hinweise
extends, enums, ifaces, generic, extra, skip = {}, set(), set(interfaces), {}, defaultdict(list), set()
fieldhints, methodhints = [], []
for line in Path(hints).read_text().splitlines():
    line = line.split('#')[0].strip()
    if not line:
        continue
    p = line.split()
    if p[0] == 'extends': extends[p[1]] = p[2]
    elif p[0] == 'enum': enums.add(p[1])
    elif p[0] == 'interface': ifaces.add(p[1])
    elif p[0] == 'implements': extra[p[1]].append(p[2])
    elif p[0] == 'returns': generic[(p[1], p[2])] = ' '.join(p[3:])   # returns owner method Type
    elif p[0] == 'skip': skip.add(p[1])
    elif p[0] == 'field': fieldhints.append((p[1], p[2], p[3], len(p) > 4))
    elif p[0] == 'method': methodhints.append((p[1], p[2], p[3], len(p) > 4))

def jtype(d, i=0):
    arr = 0
    while d[i] == '[':
        arr += 1; i += 1
    c = d[i]
    prim = {'Z': 'boolean', 'B': 'byte', 'C': 'char', 'S': 'short', 'I': 'int', 'J': 'long', 'F': 'float', 'D': 'double', 'V': 'void'}
    if c in prim:
        t, i = prim[c], i + 1
    else:
        j = d.index(';', i)
        n = d[i + 1:j]
        i = j + 1
        t = n.replace('/', '.')
        if n.startswith('net/minecraft/') or n.startswith('com/mojang/'):
            t = n.replace('/', '.').replace('$', '.')
    return t + '[]' * arr, i

def params(desc):
    i, res = 1, []
    while desc[i] != ')':
        t, i = jtype(desc, i)
        res.append(t)
    return res, jtype(desc, i + 1)[0]

def top(owner):
    return owner.split('/')[-1].split('$')[0]

def sig(owner, name, desc, static):
    ps, r = params(desc)
    r = generic.get((owner.split('/')[-1], name), r)
    args = ', '.join(f'{t} a{k}' for k, t in enumerate(ps))
    return ps, r, args

for o, n, d, st in fieldhints:
    members['net/minecraft/' + o]['f'][n] = (d, st)
for o, n, d, st in methodhints:
    members['net/minecraft/' + o]['m'][(n, d)] = st
# ---- Gruppieren: Top-Level-Klasse (voller Name) -> {innerer Name: Mitglieder}
def full(n):                       # Hinweis-Namen kurz (class_1) oder voll
    return n if '/' in n else 'net/minecraft/' + n
ifaces = {full(i) for i in ifaces}
enums = {full(e) for e in enums}
extends = {full(k): full(v) for k, v in extends.items()}
extra = {full(k): [full(x) for x in v] for k, v in extra.items()}
generic = {k: v for k, v in generic.items()}

def toplevel(n):
    return n.split('$')[0]

classes = defaultdict(dict)
def ensure(n):
    classes[toplevel(n)].setdefault(n, {'m': {}, 'f': {}})
for owner in members:
    classes[toplevel(owner)][owner] = members[owner]
for n in list(extends) + list(extends.values()) + list(enums) + list(ifaces):
    ensure(n)
for n in re.findall(r'L((?:net/minecraft|com/mojang)/[^;<]+);', txt):
    ensure(n)

def default(t):
    if t in ('int', 'short', 'byte', 'char', 'long', 'float', 'double'): return '0'
    if t == 'boolean': return 'false'
    return 'null'

def dots(n):
    return n.replace('/', '.').replace('$', '.')

def emit(name, mem, nested_in=False):
    is_iface = name in ifaces
    is_enum = name in enums
    cls_name = name.split('$')[-1].split('/')[-1]
    kw = 'interface' if is_iface else ('enum' if is_enum else 'class')
    mods = ('public static ' if nested_in else 'public ') + ('abstract ' if kw == 'class' and extra.get(name) else '')
    heads = []
    if name in extends and not is_iface and not is_enum:
        heads.append('extends ' + dots(extends[name]))
    if extra.get(name):
        heads.append(('extends ' if is_iface else 'implements ') + ', '.join(dots(e) for e in extra[name]))
    L = [f'{mods}{kw} {cls_name} {" ".join(heads)} {{']
    fields = mem['f']
    own = cls_name + ';'
    if is_enum:
        consts = [n for n, (d, st) in fields.items() if st and d.endswith(own)]
        L.append('    ' + (', '.join(consts) if consts else 'DUMMY') + ';')
    for n, (d, st) in fields.items():
        if is_enum and st and d.endswith(own):
            continue
        t = jtype(d)[0]
        if is_iface:
            L.append(f'    {t} {n} = {default(t)};')
        else:
            L.append(f'    public {"static " if st else ""}{t} {n}{" = " + default(t) if st else ""};')
    has_ctor = False
    for (n, d), st in mem['m'].items():
        ps, r = params(d)
        args = ', '.join(f'{t} a{k}' for k, t in enumerate(ps))
        r = generic.get((name.split('/')[-1], n), r)
        if n == '<clinit>' or (is_enum and n in ('values', 'valueOf', 'ordinal', 'name', 'compareTo', 'toString', 'hashCode', 'equals')):
            continue
        if n == '<init>':
            if not is_enum and not is_iface:
                has_ctor = has_ctor or not ps
                L.append(f'    public {cls_name}({args}) {{ }}')
            continue
        if is_iface and not st:
            L.append(f'    {r} {n}({args});')
        else:
            ret = '' if r == 'void' else f' return {default(r)};'
            L.append(f'    public {"static " if st else ""}{r} {n}({args}) {{{ret} }}')
    if kw == 'class' and not has_ctor:
        L.append(f'    public {cls_name}() {{ }}')
    L.append('}')
    return L

out.mkdir(parents=True, exist_ok=True)
for tl, nested in classes.items():
    if tl.split('/')[-1] in skip:
        continue
    pkg = tl.rsplit('/', 1)[0].replace('/', '.')
    main = nested.get(tl, {'m': {}, 'f': {}})
    L = emit(tl, main)
    inner = []
    for name, mem in nested.items():
        if name != tl:
            inner += ['    ' + l for l in emit(name, mem, True)]
    path = out / (tl + '.java')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join([f'package {pkg};', ''] + L[:-1] + inner + ['}']))
print(len(classes), 'Klassen')
