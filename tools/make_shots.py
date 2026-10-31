"""Promotional phone screenshots (1080x1920) for the Play Store, drawn with the same art as make_art.py.

python tools/make_shots.py <project_root>
Writes store/screenshots/<locale>/01.png ... for pt-BR and en-US (other languages show the pt-BR set).
"""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = sys.argv[1]
OUT = os.path.join(ROOT, "store", "screenshots")

# Reuse the drawing helpers of make_art.py (everything before its main code).
src = open(os.path.join(ROOT, "tools", "make_art.py"), encoding="utf-8").read()
src = src[: src.index("big = icon_scene")]
A = {"__name__": "make_art"}
exec(compile(src, "make_art.py", "exec"), A)
sky, glow, mountains, target, arrow, archer, lerp = (A[k] for k in ("sky", "glow", "mountains", "target", "arrow", "archer", "lerp"))

W, H = 1080, 1920
FONTS = [r"C:\Windows\Fonts\seguibl.ttf", r"C:\Windows\Fonts\arialbd.ttf",
         "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]
FONT = next(f for f in FONTS if os.path.exists(f))

CAPTIONS = {
    "pt-BR": [("PUXE E SOLTE", "mire com o dedo e acerte!"), ("NA CABEÇA!", "vitória em câmera lenta"),
              ("DUELO CHEFE", "a cada 10 fases"), ("VENTO E ALTURA", "cada tiro é um desafio"),
              ("FLECHAS ESPECIAIS", "fogo, gelo, bomba e tripla"), ("13 ARQUEIROS", "9 arcos para colecionar")],
    "en-US": [("PULL & RELEASE", "aim with your finger and hit!"), ("HEADSHOT!", "slow-motion victory"),
              ("BOSS DUEL", "every 10 levels"), ("WIND & HEIGHT", "every shot is a challenge"),
              ("SPECIAL ARROWS", "fire, ice, bomb and triple"), ("13 ARCHERS", "9 bows to collect")],
}
COLORS = [(59, 130, 246), (239, 68, 68), (34, 197, 94), (168, 85, 247), (250, 204, 21), (236, 72, 153),
          (20, 184, 166), (249, 115, 22), (100, 116, 139), (14, 165, 233), (132, 204, 22), (244, 63, 94)]


def scenery():
    img = sky(W, H)
    mountains(img, H * 0.86, (76, 28, 70, 255), H * 0.1, 6, 1.0)
    mountains(img, H * 0.95, (36, 14, 40, 255), H * 0.06, 4, 2.2)
    return img


def tower(img, x, top, w):
    d = ImageDraw.Draw(img)
    d.rectangle([x - w / 2, top, x + w / 2, H], fill=(68, 64, 60, 255))
    for k in range(int((H - top) / 60)):
        y = top + 30 + k * 60
        d.line([(x - w / 2, y), (x + w / 2, y)], fill=(48, 45, 42, 255), width=4)
    d.rectangle([x - w / 2 - 8, top - 10, x + w / 2 + 8, top + 18], fill=(34, 197, 94, 255))


def dots(img, p0, p1, lift, n=22):
    d = ImageDraw.Draw(img)
    for i in range(1, n):
        t = i / n
        x = p0[0] + (p1[0] - p0[0]) * t
        y = p0[1] + (p1[1] - p0[1]) * t - lift * 4 * t * (1 - t)
        r = 6 + 5 * t
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, int(110 + 120 * t)))


def panel(img, box, radius, fill, outline, width):
    """Translucent rounded card, composited (drawing RGBA directly would overwrite alpha)."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)
    img.alpha_composite(layer)


def text_fit(d, txt, max_w, size):
    while size > 30:
        f = ImageFont.truetype(FONT, size)
        if d.textlength(txt, font=f) <= max_w:
            return f
        size -= 4
    return ImageFont.truetype(FONT, size)


def caption(img, title, sub):
    t = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    td = ImageDraw.Draw(t)
    f1 = text_fit(td, title, W - 120, 128)
    tw = td.textlength(title, font=f1)
    td.text(((W - tw) / 2, 110), title, font=f1, fill=(255, 255, 255, 255))
    g = Image.new("RGBA", (W, H), (249, 115, 22, 0))
    g.putalpha(t.split()[3].filter(ImageFilter.GaussianBlur(22)).point(lambda a: int(a * 0.8)))
    img.alpha_composite(g)
    gp = Image.new("RGB", (W, 1))
    for x in range(W):
        gp.putpixel((x, 0), lerp((253, 224, 71), (249, 115, 22), x / W))
    img.alpha_composite(Image.composite(gp.resize((W, H)).convert("RGBA"), Image.new("RGBA", (W, H), (0, 0, 0, 0)), t.split()[3]))
    d = ImageDraw.Draw(img)
    f2 = text_fit(d, sub, W - 140, 62)
    sw = d.textlength(sub, font=f2)
    d.text(((W - sw) / 2, 285), sub, font=f2, fill=(255, 255, 255, 235))


def duel(img, ph, eh, enemy_color=(239, 68, 68), size=230, lift=360, hit=True):
    """Player tower on the left, enemy on the right, dotted shot between them."""
    tower(img, 200, ph, 240)
    tower(img, 880, eh, 240)
    pull, _ = archer(img, 200, ph, size, aim=-0.5)
    archer(img, 880, eh, size, aim=-0.3, color=enemy_color, facing=-1)
    head = (880 - size / 1.9 * 0.05, eh - size / 1.9 * 1.76)
    dots(img, (pull[0] + 40, pull[1] - 20), head, lift)
    if hit:
        arrow(img, head, math.radians(25), size * 0.75, size * 0.05)
    return head


def shot_aim():
    img = scenery()
    tower(img, 200, H * 0.66, 240)
    tower(img, 880, H * 0.56, 240)
    pull, _ = archer(img, 200, H * 0.66, 260, aim=-0.6)
    archer(img, 880, H * 0.56, 230, aim=-0.3, color=(239, 68, 68), facing=-1)
    dots(img, (pull[0] + 40, pull[1] - 20), (850, H * 0.45), 380)
    d = ImageDraw.Draw(img)
    # drag hint: finger pulling back
    fx, fy = 330, H * 0.82
    d.line([(fx + 160, fy - 90), (fx, fy)], fill=(255, 255, 255, 200), width=10)
    glow(img, fx, fy, 70, (255, 255, 255), 0.35)
    d.ellipse([fx - 38, fy - 38, fx + 38, fy + 38], fill=(255, 255, 255, 240))
    return img


def shot_headshot():
    img = scenery()
    glow(img, W / 2, H * 0.5, W * 0.6, (253, 224, 71), 0.25)
    head = duel(img, H * 0.7, H * 0.62, size=260)
    glow(img, head[0], head[1], 120, (255, 255, 255), 0.5)
    d = ImageDraw.Draw(img)
    for i in range(14):
        a = math.radians(i * 360 / 14)
        d.line([(head[0] + math.cos(a) * 70, head[1] + math.sin(a) * 70),
                (head[0] + math.cos(a) * 130, head[1] + math.sin(a) * 130)], fill=(255, 245, 200, 230), width=6)
    f = ImageFont.truetype(FONT, 96)
    d.text((W / 2 - d.textlength("x3", font=f) / 2 - 300, H * 0.42), "0.3x", font=ImageFont.truetype(FONT, 70), fill=(255, 255, 255, 200))
    return img


def shot_boss():
    img = sky(W, H)
    glow(img, W * 0.75, H * 0.55, W * 0.6, (239, 68, 68), 0.35)
    mountains(img, H * 0.95, (70, 22, 40, 255), H * 0.08, 6, 0.7)
    tower(img, 200, H * 0.74, 240)
    tower(img, 860, H * 0.5, 280)
    archer(img, 200, H * 0.74, 230, aim=-0.7)
    archer(img, 860, H * 0.5, 330, aim=-0.4, color=(30, 41, 59), facing=-1)
    # crown over the boss
    d = ImageDraw.Draw(img)
    cx, cy = 860 - 330 / 1.9 * 0.05, H * 0.5 - 330 / 1.9 * 1.76 - 95
    glow(img, cx, cy, 120, (250, 204, 21), 0.5)
    d.polygon([(cx - 80, cy + 40), (cx - 90, cy - 45), (cx - 40, cy - 5), (cx, cy - 70), (cx + 40, cy - 5),
               (cx + 90, cy - 45), (cx + 80, cy + 40)], fill=(250, 204, 21, 255))
    f = ImageFont.truetype(FONT, 96)
    d.text((W * 0.2 - d.textlength("x3", font=f) / 2, H * 0.27), "x3", font=f, fill=(255, 237, 160, 255))
    # boss health bar
    panel(img, [W * 0.55, H * 0.25, W * 0.95, H * 0.25 + 34], 17, (0, 0, 0, 120), None, 0)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([W * 0.55 + 6, H * 0.25 + 6, W * 0.88, H * 0.25 + 28], radius=12, fill=(239, 68, 68, 255))
    return img


def shot_wind():
    img = scenery()
    tower(img, 190, H * 0.82, 220)
    tower(img, 890, H * 0.42, 240)
    pull, _ = archer(img, 190, H * 0.82, 210, aim=-0.9)
    archer(img, 890, H * 0.42, 210, aim=0.2, color=(168, 85, 247), facing=-1)
    dots(img, (pull[0] + 30, pull[1] - 30), (870, H * 0.42 - 190), 420)
    d = ImageDraw.Draw(img)
    # wind arrows
    for k, y in enumerate((H * 0.24, H * 0.3, H * 0.36)):
        x0 = 120 + k * 60
        d.line([(x0, y), (x0 + 260, y)], fill=(255, 255, 255, 255), width=8)
        d.polygon([(x0 + 290, y), (x0 + 250, y - 22), (x0 + 250, y + 22)], fill=(255, 255, 255, 255))
    f = ImageFont.truetype(FONT, 64)
    d.text((560, H * 0.335), "4 m/s", font=f, fill=(255, 255, 255, 230))
    return img


def special_arrow(img, cx, cy, kind):
    glow_c = {"fire": (249, 115, 22), "ice": (125, 211, 252), "bomb": (100, 116, 139), "triple": (250, 204, 21)}[kind]
    glow(img, cx, cy, 150, glow_c, 0.5)
    d = ImageDraw.Draw(img)
    panel(img, [cx - 190, cy - 140, cx + 190, cy + 140], 40, (255, 255, 255, 30), (255, 255, 255, 90), 3)
    d = ImageDraw.Draw(img)
    if kind == "triple":
        for k in (-1, 0, 1):
            arrow(img, (cx + 120, cy + k * 55 - 10), math.radians(-12 + k * 10), 240, 12, fletch=glow_c)
    else:
        arrow(img, (cx + 120, cy - 30), math.radians(-20), 260, 14, fletch=glow_c)
    if kind == "fire":
        for i in range(6):
            glow(img, cx + 120 - i * 18, cy - 30 - i * 4, 40 - i * 4, (253, 186, 116), 0.5)
    if kind == "ice":
        for i in range(6):
            a = math.radians(i * 60)
            d.line([(cx + 120, cy - 30), (cx + 120 + math.cos(a) * 45, cy - 30 + math.sin(a) * 45)], fill=(224, 242, 254, 255), width=6)
    if kind == "bomb":
        d.ellipse([cx + 85, cy - 75, cx + 165, cy + 5], fill=(30, 41, 59, 255))
        glow(img, cx + 160, cy - 80, 26, (250, 204, 21), 0.8)


def shot_arrows():
    img = scenery()
    special_arrow(img, 290, H * 0.38, "fire")
    special_arrow(img, 790, H * 0.38, "ice")
    special_arrow(img, 290, H * 0.62, "bomb")
    special_arrow(img, 790, H * 0.62, "triple")
    return img


def shot_archers():
    img = scenery()
    d = ImageDraw.Draw(img)
    cols, cw, ch = 3, 300, 330
    x0, y0 = (W - cols * cw) / 2, H * 0.22
    for i, c in enumerate(COLORS):
        cx, cy = x0 + (i % cols + 0.5) * cw, y0 + (i // cols + 0.5) * ch
        panel(img, [cx - 130, cy - 150, cx + 130, cy + 150], 34, (255, 255, 255, 28),
              (250, 204, 21, 255) if i == 0 else (255, 255, 255, 70), 4 if i == 0 else 2)
        archer(img, cx - 20, cy + 120, 190, aim=-0.3, color=c)
        d = ImageDraw.Draw(img)
    return img


SHOTS = [shot_aim, shot_headshot, shot_boss, shot_wind, shot_arrows, shot_archers]
bases = [f() for f in SHOTS]
for loc, caps in CAPTIONS.items():
    os.makedirs(os.path.join(OUT, loc), exist_ok=True)
    for i, (base, (t, s)) in enumerate(zip(bases, caps)):
        img = base.copy()
        caption(img, t, s)
        img.convert("RGB").save(os.path.join(OUT, loc, f"{i + 1:02d}.png"), optimize=True)
print("done")
