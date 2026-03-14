#!/usr/bin/env python3
"""Convert amigo_2048.svg to Android Vector Drawable."""

import xml.etree.ElementTree as ET
import re

SVG_IN  = "mobile/shared/assets/svg/amigo_2048.svg"
AVD_OUT = "mobile/android/src/main/res/drawable/ic_amigo_profile.xml"

def mat_mul(a, b):
    a0,b0,c0,d0,e0,f0 = a
    a1,b1,c1,d1,e1,f1 = b
    return (
        a0*a1 + c0*b1, b0*a1 + d0*b1,
        a0*c1 + c0*d1, b0*c1 + d0*d1,
        a0*e1 + c0*f1 + e0, b0*e1 + d0*f1 + f0,
    )

def parse_transform(t):
    t = t.strip()
    m = re.match(r'translate\(\s*([^,\s)]+)[,\s]+([^)]+)\)', t)
    if m:
        return (1,0,0,1,float(m.group(1)),float(m.group(2)))
    m = re.match(r'matrix\(([^)]+)\)', t)
    if m:
        v = [float(x) for x in re.split(r'[,\s]+', m.group(1).strip())]
        return tuple(v)
    m = re.match(r'scale\((-?[\d.]+),(-?[\d.]+)\)', t)
    if m:
        return (float(m.group(1)),0,0,float(m.group(2)),0,0)
    m = re.match(r'scale\((-?[\d.]+)\)', t)
    if m:
        s = float(m.group(1))
        return (s,0,0,s,0,0)
    return (1,0,0,1,0,0)

def tx(mat, x, y):
    a,b,c,d,e,f = mat
    return a*x + c*y + e, b*x + d*y + f

def scale_only(mat, dx, dy):
    """Apply only the scale/rotation part (no translation) — for relative coords."""
    a,b,c,d,e,f = mat
    return a*dx + c*dy, b*dx + d*dy

def fmt(v):
    s = f"{v:.4f}"
    # trim trailing zeros but keep at least one decimal
    s = s.rstrip('0').rstrip('.')
    return s

def tokenize(d):
    return re.findall(
        r'[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?',
        d
    )

def transform_path(d, mat):
    tokens = tokenize(d)
    out = []
    i = 0
    cmd = 'M'
    cur_x, cur_y = 0.0, 0.0  # track current point for H/V

    while i < len(tokens):
        tok = tokens[i]
        if tok.isalpha():
            cmd = tok
            i += 1
            # Don't emit command yet — we'll emit uppercase version below
            continue

        def n(j): return float(tokens[i + j])

        if cmd == 'M':
            x, y = n(0), n(1)
            nx, ny = tx(mat, x, y)
            cur_x, cur_y = nx, ny
            out.append(f"M {fmt(nx)},{fmt(ny)}")
            i += 2; cmd = 'L'
        elif cmd == 'm':
            dx, dy = n(0), n(1)
            # First 'm' in a path is absolute (SVG spec); subsequent are relative
            if not out:
                nx, ny = tx(mat, dx, dy)
                cur_x, cur_y = nx, ny
                out.append(f"M {fmt(nx)},{fmt(ny)}")
            else:
                ndx, ndy = scale_only(mat, dx, dy)
                cur_x += ndx; cur_y += ndy
                out.append(f"M {fmt(cur_x)},{fmt(cur_y)}")
            i += 2; cmd = 'l'
        elif cmd == 'L':
            x, y = n(0), n(1)
            nx, ny = tx(mat, x, y)
            cur_x, cur_y = nx, ny
            out.append(f"L {fmt(nx)},{fmt(ny)}")
            i += 2
        elif cmd == 'l':
            dx, dy = n(0), n(1)
            ndx, ndy = scale_only(mat, dx, dy)
            cur_x += ndx; cur_y += ndy
            out.append(f"L {fmt(cur_x)},{fmt(cur_y)}")
            i += 2
        elif cmd == 'H':
            x = n(0)
            nx, ny = tx(mat, x, cur_y)
            cur_x = nx
            out.append(f"L {fmt(nx)},{fmt(ny)}")
            i += 1
        elif cmd == 'h':
            dx = n(0)
            ndx, _ = scale_only(mat, dx, 0)
            cur_x += ndx
            out.append(f"L {fmt(cur_x)},{fmt(cur_y)}")
            i += 1
        elif cmd == 'V':
            y = n(0)
            nx, ny = tx(mat, cur_x, y)
            cur_y = ny
            out.append(f"L {fmt(nx)},{fmt(ny)}")
            i += 1
        elif cmd == 'v':
            dy = n(0)
            _, ndy = scale_only(mat, 0, dy)
            cur_y += ndy
            out.append(f"L {fmt(cur_x)},{fmt(cur_y)}")
            i += 1
        elif cmd == 'C':
            pts = [(n(j), n(j+1)) for j in range(0,6,2)]
            tpts = [tx(mat, p[0], p[1]) for p in pts]
            cur_x, cur_y = tpts[2]
            out.append("C " + " ".join(f"{fmt(p[0])},{fmt(p[1])}" for p in tpts))
            i += 6
        elif cmd == 'c':
            pts = [(n(j), n(j+1)) for j in range(0,6,2)]
            tpts = [scale_only(mat, p[0], p[1]) for p in pts]
            abs_pts = [(cur_x + tpts[k][0], cur_y + tpts[k][1]) for k in range(3)]
            cur_x, cur_y = abs_pts[2]
            out.append("C " + " ".join(f"{fmt(p[0])},{fmt(p[1])}" for p in abs_pts))
            i += 6
        elif cmd == 'S':
            pts = [(n(j), n(j+1)) for j in range(0,4,2)]
            tpts = [tx(mat, p[0], p[1]) for p in pts]
            cur_x, cur_y = tpts[1]
            out.append("S " + " ".join(f"{fmt(p[0])},{fmt(p[1])}" for p in tpts))
            i += 4
        elif cmd == 's':
            pts = [(n(j), n(j+1)) for j in range(0,4,2)]
            tpts = [scale_only(mat, p[0], p[1]) for p in pts]
            abs_pts = [(cur_x + tpts[k][0], cur_y + tpts[k][1]) for k in range(2)]
            cur_x, cur_y = abs_pts[1]
            out.append("S " + " ".join(f"{fmt(p[0])},{fmt(p[1])}" for p in abs_pts))
            i += 4
        elif cmd == 'A':
            rx,ry,xr,la,sw,x,y = n(0),n(1),n(2),n(3),n(4),n(5),n(6)
            a_m,_,_,d_m,_,_ = mat
            nrx = abs(a_m)*rx; nry = abs(d_m)*ry
            nx, ny = tx(mat, x, y)
            cur_x, cur_y = nx, ny
            out.append(f"A {fmt(nrx)},{fmt(nry)},{fmt(xr)},{int(la)},{int(sw)},{fmt(nx)},{fmt(ny)}")
            i += 7
        elif cmd == 'a':
            rx,ry,xr,la,sw,dx,dy = n(0),n(1),n(2),n(3),n(4),n(5),n(6)
            a_m,_,_,d_m,_,_ = mat
            nrx = abs(a_m)*rx; nry = abs(d_m)*ry
            ndx, ndy = scale_only(mat, dx, dy)
            cur_x += ndx; cur_y += ndy
            out.append(f"A {fmt(nrx)},{fmt(nry)},{fmt(xr)},{int(la)},{int(sw)},{fmt(cur_x)},{fmt(cur_y)}")
            i += 7
        elif cmd in ('Z','z'):
            out.append("Z")
            i += 1
        else:
            i += 1

    return " ".join(out)

def ellipse_to_path(cx, cy, rx, ry):
    return (f"M {cx-rx},{cy} "
            f"A {rx},{ry},0,0,1,{cx+rx},{cy} "
            f"A {rx},{ry},0,0,1,{cx-rx},{cy} Z")

def get_style(elem):
    style = elem.get('style','')
    props = {}
    for item in style.split(';'):
        if ':' in item:
            k,v = item.split(':',1)
            props[k.strip()] = v.strip()
    fill = props.get('fill', elem.get('fill','#000000'))
    if fill in ('none','None',''):
        return None, 0
    opacity = float(props.get('opacity','1'))
    fill_opacity = float(props.get('fill-opacity','1'))
    return fill, opacity * fill_opacity

def get_mat(elem):
    t = elem.get('transform','')
    return parse_transform(t) if t else (1,0,0,1,0,0)

def walk(elem, parent_mat, results):
    tag = elem.tag.split('}')[-1] if '}' in elem.tag else elem.tag
    mat = mat_mul(parent_mat, get_mat(elem))

    if tag == 'g':
        for child in elem:
            walk(child, mat, results)
        return

    fill, alpha = get_style(elem)
    if fill is None or alpha < 0.01:
        return

    path_d = None
    if tag == 'path':
        path_d = elem.get('d','')
    elif tag == 'ellipse':
        cx,cy = float(elem.get('cx',0)), float(elem.get('cy',0))
        rx,ry = float(elem.get('rx',0)), float(elem.get('ry',0))
        path_d = ellipse_to_path(cx, cy, rx, ry)
    elif tag == 'rect':
        x,y = float(elem.get('x',0)), float(elem.get('y',0))
        w,h = float(elem.get('width',0)), float(elem.get('height',0))
        path_d = f"M {x},{y} H {x+w} V {y+h} H {x} Z"

    if path_d:
        transformed = transform_path(path_d, mat)
        color = fill.lstrip('#').upper()
        if len(color) == 3:
            color = ''.join(c*2 for c in color)
        alpha_hex = format(min(255, int(round(alpha * 255))), '02X')
        results.append((f"#{alpha_hex}{color}", transformed))

def convert():
    tree = ET.parse(SVG_IN)
    root = tree.getroot()
    vb = root.get('viewBox','0 0 64 64').split()
    vw, vh = float(vb[2]), float(vb[3])

    results = []
    for child in root:
        walk(child, (1,0,0,1,0,0), results)

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="64dp"',
        '    android:height="64dp"',
        f'    android:viewportWidth="{vw}"',
        f'    android:viewportHeight="{vh}">',
    ]
    for color, d in results:
        lines += [
            '    <path',
            f'        android:fillColor="{color}"',
            f'        android:pathData="{d}" />',
        ]
    lines.append('</vector>')

    with open(AVD_OUT, 'w') as f:
        f.write('\n'.join(lines) + '\n')
    print(f"Written {AVD_OUT} ({len(results)} paths)")

convert()
