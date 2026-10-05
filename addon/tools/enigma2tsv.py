import os, sys
root = sys.argv[1]; out = open(sys.argv[2], 'w')
n = {'c': 0, 'f': 0, 'm': 0}
def parse(path):
    stack = []  # (indent, interFull, namedFull)
    with open(path, encoding='utf-8') as f:
        for raw in f:
            if not raw.strip() or raw.lstrip().startswith('COMMENT'):
                continue
            indent = len(raw) - len(raw.lstrip('\t'))
            t = raw.strip().split(' ')
            kind = t[0]
            while stack and stack[-1][0] >= indent:
                stack.pop()
            if kind == 'CLASS':
                inter = t[1]; named = t[2] if len(t) > 2 and not t[2].startswith('acc:') else None
                if stack:
                    oi, on = stack[-1][1], stack[-1][2]
                    full_i = oi + '$' + inter
                    full_n = on + '$' + (named or inter)
                else:
                    full_i = inter; full_n = named or inter
                stack.append((indent, full_i, full_n))
                out.write('CLASS\t%s\t%s\n' % (full_i, full_n)); n['c'] += 1
            elif kind in ('FIELD', 'METHOD') and stack:
                inter = t[1]
                rest = [x for x in t[2:] if not x.startswith('acc:')]
                if len(rest) == 2:
                    named, desc = rest
                elif len(rest) == 1:
                    continue  # no name, only descriptor
                else:
                    continue
                out.write('%s\t%s\t%s\t%s\t%s\n' % (kind, stack[-1][1], inter, named, desc))
                n['f' if kind == 'FIELD' else 'm'] += 1
for dp, dn, fn in os.walk(root):
    for f in fn:
        if f.endswith('.mapping'):
            parse(os.path.join(dp, f))
print(n)
