"""Prisma Archers art: launcher icons (all densities + adaptive), Play Store icon and feature graphic.

python3 tools/make_art.py <project_root>
Everything is drawn here (bullseye with an arrow; dusk duel for the store graphic), supersampled.
"""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = sys.argv[1]
RES = os.path.join(ROOT, "app/src/main/res")
STORE = os.path.join(ROOT, "store")
os.makedirs(STORE, exist_ok=True)

FONT = "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf"


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def vgrad(w, h, stops):
    """Vertical gradient through [(pos, rgb), ...]."""
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        for i in range(len(stops) - 1):
            (p0, c0), (p1, c1) = stops[i], stops[i + 1]
            if p0 <= t <= p1:
                col.putpixel((0, y), lerp(c0, c1, (t - p0) / max(1e-6, p1 - p0)))
                break
    return col.resize((w, h))


def glow(img, cx, cy, r, color, alpha):
    if r < 2:
        return
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (int(255 * alpha),))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(r * 0.45)))


def sky(w, h):
    """Dusk: deep violet on top, warm orange at the horizon."""
    bg = vgrad(w, h, [(0, (20, 10, 48)), (0.45, (76, 22, 84)), (0.8, (214, 76, 70)), (1, (249, 146, 60))]).convert("RGBA")
    glow(bg, w * 0.5, h * 0.92, w * 0.55, (253, 224, 138), 0.45)
    glow(bg, w * 0.15, h * 0.12, w * 0.45, (124, 58, 237), 0.45)
    return bg


def mountains(img, horizon, color, amp, freq, seed):
    w, h = img.size
    d = ImageDraw.Draw(img)
    pts = [(0, h)]
    for x in range(0, w + 8, 8):
        u = x / w
        y = horizon - amp * (0.55 * abs(math.sin(u * freq + seed)) + 0.3 * abs(math.sin(u * freq * 2.3 + seed * 2)) + 0.15 * abs(math.sin(u * freq * 5.1 + seed)))
        pts.append((x, y))
    pts.append((w, h))
    d.polygon(pts, fill=color)




def target(img, cx, cy, r, tilt=0.0):
    """Bullseye with a wooden rim and a soft shadow."""
    glow(img, cx + r * 0.08, cy + r * 0.12, r * 1.25, (0, 0, 0), 0.45)
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r * 1.08, cy - r * 1.08, cx + r * 1.08, cy + r * 1.08], fill=(120, 72, 36, 255))
    cols = [(248, 250, 252), (239, 68, 68), (248, 250, 252), (239, 68, 68), (250, 204, 21)]
    for i, c in enumerate(cols):
        rr = r * (1 - i * 0.19)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=c + (255,))
    # glossy highlight
    hl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([cx - r * 0.75, cy - r * 0.9, cx + r * 0.2, cy - r * 0.25], fill=(255, 255, 255, 70))
    img.alpha_composite(hl.filter(ImageFilter.GaussianBlur(r * 0.08)))


def arrow(img, tip, angle, length, width, fletch=(244, 114, 182)):
    """Arrow whose tip is buried at [tip]; angle in radians (screen, 0 = pointing right)."""
    d = ImageDraw.Draw(img)
    dx, dy = math.cos(angle), math.sin(angle)
    px, py = -dy, dx
    tail = (tip[0] - dx * length, tip[1] - dy * length)
    d.line([tail, tip], fill=(139, 90, 43, 255), width=int(width))
    d.line([(tail[0] + px * width * 0.15, tail[1] + py * width * 0.15), (tip[0] + px * width * 0.15, tip[1] + py * width * 0.15)], fill=(190, 130, 70, 255), width=max(1, int(width * 0.3)))
    for s in (-1, 1):
        f = [
            (tail[0] + dx * width * 0.4, tail[1] + dy * width * 0.4),
            (tail[0] + dx * width * 4.2, tail[1] + dy * width * 4.2),
            (tail[0] + dx * width * 1.6 + px * s * width * 2.2, tail[1] + dy * width * 1.6 + py * s * width * 2.2),
            (tail[0] - dx * width * 1.0 + px * s * width * 2.2, tail[1] - dy * width * 1.0 + py * s * width * 2.2),
        ]
        d.polygon(f, fill=fletch + (255,))


def archer(img, x, ground, h, aim=-0.45, color=(59, 130, 246), skin=(252, 217, 182), bow=(146, 64, 14), facing=1):
    """Stick-figure archer drawing a bow (pose like the game)."""
    d = ImageDraw.Draw(img)
    u = h / 1.9
    f = facing
    def P(dx, dy):
        return (x + dx * u * f, ground - dy * u)
    lw = int(u * 0.13)
    dark = lerp(color, (0, 0, 0), 0.35)
    d.line([P(0, 0.9), P(-0.1, 0.45), P(-0.17, 0)], fill=dark + (255,), width=lw, joint="curve")
    d.line([P(0.02, 1.24), P(0, 0.9)], fill=dark + (255,), width=int(u * 0.27))
    d.line([P(0.03, 1.52), P(0.02, 1.24)], fill=color + (255,), width=int(u * 0.29))
    d.line([P(0, 0.9), P(0.14, 0.45), P(0.2, 0)], fill=color + (255,), width=lw, joint="curve")
    ca, sa = math.cos(-aim), math.sin(-aim)
    hand = P(0.03 + ca * 0.58, 1.47 + sa * 0.58)
    pull = P(0.03 + ca * 0.1, 1.47 + sa * 0.1)
    d.line([P(0.03, 1.5), P(-0.15, 1.45), pull], fill=dark + (255,), width=int(lw * 0.9), joint="curve")
    head = P(0.05, 1.76)
    hr = u * 0.19
    d.ellipse([head[0] - hr, head[1] - hr, head[0] + hr, head[1] + hr], fill=skin + (255,))
    d.pieslice([head[0] - hr * 1.05, head[1] - hr * 1.1, head[0] + hr * 1.05, head[1] + hr * 0.6], 180, 360, fill=lerp(color, (0, 0, 0), 0.15) + (255,))
    ex = head[0] + hr * 0.5 * f
    d.ellipse([ex - hr * 0.14, head[1] - hr * 0.2, ex + hr * 0.14, head[1] + hr * 0.08], fill=(31, 41, 55, 255))
    # bow: arc through the hand, perpendicular to the aim
    adx, ady = ca * f, -sa
    pxv, pyv = -ady, adx
    half = u * 0.62
    back = u * 0.2
    t1 = (hand[0] + pxv * half - adx * back, hand[1] + pyv * half - ady * back)
    t2 = (hand[0] - pxv * half - adx * back, hand[1] - pyv * half - ady * back)
    ctrl = (hand[0] + adx * u * 0.34, hand[1] + ady * u * 0.34)
    pts = []
    for i in range(21):
        t = i / 20
        pts.append(((1 - t) ** 2 * t1[0] + 2 * (1 - t) * t * ctrl[0] + t * t * t2[0], (1 - t) ** 2 * t1[1] + 2 * (1 - t) * t * ctrl[1] + t * t * t2[1]))
    d.line([t1, pull, t2], fill=(241, 245, 249, 255), width=max(1, int(u * 0.025)))
    d.line(pts, fill=bow + (255,), width=int(u * 0.075), joint="curve")
    d.line([P(0.03, 1.5), hand], fill=color + (255,), width=int(lw * 0.9))
    d.ellipse([hand[0] - u * 0.07, hand[1] - u * 0.07, hand[0] + u * 0.07, hand[1] + u * 0.07], fill=skin + (255,))
    return pull, (adx, ady)


def icon_scene(S, fg_only=False):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0)) if fg_only else sky(S, S)
    # Adaptive icons crop to the inner ~66%: keep the art smaller there.
    r = S * (0.23 if fg_only else 0.33)
    cx, cy = S * 0.5, S * 0.52
    target(img, cx, cy, r)
    glow(img, cx, cy, r * 0.35, (255, 236, 150), 0.6)
    arrow(img, (cx + r * 0.04, cy - r * 0.02), math.radians(35), r * 1.45, r * 0.09)
    return img


def rounded(img, size, round_mask):
    img = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    if round_mask:
        md.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, size - 1, size - 1], radius=size * 0.22, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


big = icon_scene(2048)
fg = icon_scene(2048, fg_only=True)
for name, f in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    d = os.path.join(RES, f"mipmap-{name}")
    os.makedirs(d, exist_ok=True)
    rounded(big, int(48 * f), False).save(os.path.join(d, "ic_launcher.png"))
    rounded(big, int(48 * f), True).save(os.path.join(d, "ic_launcher_round.png"))
    fg.resize((int(108 * f), int(108 * f)), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"))
    sky(int(108 * f), int(108 * f)).save(os.path.join(d, "ic_launcher_background.png"))
big.convert("RGB").resize((512, 512), Image.LANCZOS).save(os.path.join(STORE, "icon-512.png"))

# Feature graphic 1024x500: dusk, an archer on a rock on the left shooting at a target on the right.
W, H = 2048, 1000
fgx = sky(W, H)
mountains(fgx, H * 0.8, (76, 28, 70, 255), H * 0.2, 7, 1.0)
mountains(fgx, H * 0.93, (36, 14, 40, 255), H * 0.1, 5, 2.2)
d = ImageDraw.Draw(fgx)
d.rectangle([W * 0.66, H * 0.8, W * 0.86, H], fill=(68, 64, 60, 255))
d.rectangle([W * 0.655, H * 0.79, W * 0.865, H * 0.815], fill=(34, 197, 94, 255))
d.rectangle([W * 0.04, H * 0.84, W * 0.2, H], fill=(68, 64, 60, 255))
d.rectangle([W * 0.035, H * 0.83, W * 0.205, H * 0.855], fill=(34, 197, 94, 255))
target(fgx, W * 0.76, H * 0.6, H * 0.17)
# dotted flight path
pull, _ = archer(fgx, W * 0.11, H * 0.835, H * 0.3, aim=-0.55)
x0, y0 = pull[0] + 60, pull[1] - 40
x1, y1 = W * 0.755, H * 0.6
for i in range(1, 26):
    t = i / 26
    x = x0 + (x1 - x0) * t
    y = y0 + (y1 - y0) * t - H * 0.45 * 4 * t * (1 - t)
    rr = 7 + 5 * t
    d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(255, 255, 255, int(120 + 100 * t)))
arrow(fgx, (x1, y1), math.radians(30), H * 0.25, H * 0.022)
t = Image.new("RGBA", (W, H), (0, 0, 0, 0))
td = ImageDraw.Draw(t)
f1 = ImageFont.truetype(FONT, 190)
f2 = ImageFont.truetype(FONT, 104)
td.text((560, 120), "PRISMA", font=f1, fill=(255, 255, 255, 255))
td.text((574, 350), "A R C H E R S", font=f2, fill=(255, 255, 255, 255))
gp = Image.new("RGB", (W, 1))
for x in range(W):
    gp.putpixel((x, 0), lerp((253, 224, 71), (244, 63, 94), min(1, max(0, (x - 560) / (W * 0.45)))))
grad = gp.resize((W, H)).convert("RGBA")
g = Image.new("RGBA", (W, H), (60, 10, 40, 0))
g.putalpha(t.split()[3].filter(ImageFilter.GaussianBlur(26)).point(lambda a: int(a * 0.8)))
fgx.alpha_composite(g)
fgx.alpha_composite(Image.composite(grad, Image.new("RGBA", (W, H), (0, 0, 0, 0)), t.split()[3]))
fgx.convert("RGB").resize((1024, 500), Image.LANCZOS).save(os.path.join(STORE, "feature-graphic-1024x500.png"))
print("done")
