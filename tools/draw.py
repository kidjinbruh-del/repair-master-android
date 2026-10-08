"""
Генератор иллюстраций для приложения «Мастер по ремонту».

Рисует векторные схемы/компоненты в SVG, рендерит в WebP через Chromium
(Playwright) в масштабе 2x, складывает в app/src/main/assets/img/.
Полностью офлайн, без внешних картинок и библиотек кроме playwright+pillow.
"""

import os
import pathlib
import sys

from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "img"
RAW = pathlib.Path(os.environ.get("TEMP", ".")) / "repairmaster_raw"

W, H = 900, 560
BG = "#12161F"
FRAME = "#2A3242"
TEXT = "#E6EBF5"
MUTED = "#98A2B6"
AMBER = "#FFB020"
CYAN = "#22D3EE"
GREEN = "#22D3A5"
RED = "#FF5C74"
VIOLET = "#A78BFA"
BODY = "#E4C88F"
BODY_DK = "#C9A96B"
SILVER = "#C9D1DE"


def esc(s: str) -> str:
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def doc(body: str, w: int = W, h: int = H, title: str = "", sub: str = "") -> str:
    """Заголовок приложение рисует сам, поэтому в картинку он не выводится."""
    t = ""
    s = ""
    return f"""<!DOCTYPE html><html><head><meta charset="utf-8"><style>
html,body{{margin:0;padding:0;background:{BG};}}
.wrap{{width:{w}px;height:{h}px;background:{BG};position:relative;
      font-family:'Segoe UI',Arial,sans-serif;color:{TEXT};}}
.t{{position:absolute;top:16px;left:22px;font-size:23px;font-weight:700;color:{TEXT};}}
.s{{position:absolute;top:46px;left:22px;font-size:14px;color:{MUTED};}}
.lbl{{font-size:15px;fill:{TEXT};}}
.lblm{{font-size:13px;fill:{MUTED};}}
.lbls{{font-size:12px;fill:{MUTED};}}
.num{{font-size:15px;fill:{BG};font-weight:700;}}
</style></head><body><div class="wrap">{t}{s}{body}</div></body></html>"""


def svg(inner: str, w: int = W, h: int = H) -> str:
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
            f'style="position:absolute;top:0;left:0">{inner}</svg>')


def sub(text: str) -> str:
    return f'<div class="s">{esc(text)}</div>'


def line(x1, y1, x2, y2, color=FRAME, w=2, dash=None, cap="round"):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" '
            f'stroke-width="{w}" stroke-linecap="{cap}"{d}/>')


def path(d, color=FRAME, w=2, fill="none", dash=None):
    da = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<path d="{d}" stroke="{color}" stroke-width="{w}" fill="{fill}" '
            f'stroke-linecap="round" stroke-linejoin="round"{da}/>')


def rect(x, y, w, h, fill="none", stroke=FRAME, sw=2, rx=0, dash=None):
    d = f' stroke-dasharray="{dash}"' if dash else ""
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="{sw}" rx="{rx}"{d}/>')


def circle(cx, cy, r, fill="none", stroke=FRAME, sw=2):
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}" stroke="{stroke}" '
            f'stroke-width="{sw}"/>')


def text(x, y, s, size=15, fill=TEXT, anchor="start", weight="400", cls="lbl"):
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" '
            f'text-anchor="{anchor}" font-weight="{weight}" class="{cls}">'
            f'{esc(s)}</text>')


# ---------------------------------------------------------------- primitives

def resistor(x, y, w=150, h=44, bands=None, vertical=False, label=None):
    """Резистор: тело + цветовые полосы."""
    bands = bands if bands is not None else ["#8a6d3b", "#1f1f1f", "#d43a3a", "#c8a02a"]
    if vertical:
        parts = [rect(x - h / 2, y - w / 2, h, w, fill=BODY, stroke=BODY_DK, rx=6)]
        bw = 7
        for i, c in enumerate(bands):
            by = y - w / 2 + 18 + i * 22
            parts.append(rect(x - h / 2 - 1, by, h + 2, bw, fill=c, stroke="none"))
        parts.append(line(x - 90, y, x - h / 2, y, FRAME, 3))
        parts.append(line(x + h / 2, y, x + 90, y, FRAME, 3))
    else:
        parts = [rect(x, y, w, h, fill=BODY, stroke=BODY_DK, rx=9)]
        bw = 8
        xs = [x + 22 + i * 22 for i in range(len(bands))]
        for bx, c in zip(xs, bands):
            parts.append(rect(bx, y - 1, bw, h + 2, fill=c, stroke="none"))
        parts.append(line(x - 45, y + h / 2, x, y + h / 2, FRAME, 3))
        parts.append(line(x + w, y + h / 2, x + w + 45, y + h / 2, FRAME, 3))
    if label:
        parts.append(text(x, y - 16, label, 14, AMBER, "middle", "600"))
    return "".join(parts)


def capacitor_polar(cx, cy, label="", desc=""):
    """Электролит: цилиндр + полоса полярности."""
    p = [
        rect(cx - 34, cy - 62, 68, 124, fill="#1C2436", stroke=FRAME, rx=10),
        rect(cx - 34, cy - 62, 30, 124, fill="#2A3550", stroke="none", rx=10),
        line(cx - 34, cy - 20, cx + 34, cy - 20, RED, 5),
        text(cx - 34, cy - 44, "−", 26, RED, "middle", "700"),
        line(cx - 44, cy + 62, cx - 10, cy + 62, FRAME, 3),
        line(cx + 10, cy + 62, cx + 44, cy + 62, FRAME, 3),
    ]
    if label:
        p.append(text(cx, cy + 6, label, 17, TEXT, "middle", "700"))
    if desc:
        p.append(text(cx, cy + 26, desc, 12, MUTED, "middle"))
    return "".join(p)


def capacitor_ceramic(cx, cy, label="", desc=""):
    p = [
        rect(cx - 30, cy - 56, 60, 112, fill="#D8C39A", stroke=BODY_DK, rx=8),
        line(cx - 44, cy, cx - 30, cy, FRAME, 3),
        line(cx + 30, cy, cx + 44, cy, FRAME, 3),
        rect(cx - 30, cy - 12, 60, 24, fill="#B9A176", stroke="none", rx=4),
    ]
    if label:
        p.append(text(cx, cy - 24, label, 16, TEXT, "middle", "700"))
    if desc:
        p.append(text(cx, cy + 40, desc, 12, MUTED, "middle"))
    return "".join(p)


def diode(cx, cy, horizontal=True, label="1N4007", desc="выпрямительный"):
    p = []
    if horizontal:
        p += [
            path(f"M {cx-60} {cy} L {cx-18} {cy}", FRAME, 3),
            path(f"M {cx+18} {cy} L {cx+60} {cy}", FRAME, 3),
            path(f"M {cx-18} {cy-40} L {cx-18} {cy+40} L {cx+18} {cy} Z",
                 AMBER, 3, "#2A3550"),
            path(f"M {cx+18} {cy-40} L {cx+18} {cy+40}", AMBER, 4),
        ]
    else:
        p += [
            path(f"M {cx} {cy-60} L {cx} {cy-18}", FRAME, 3),
            path(f"M {cx} {cy+18} L {cx} {cy+60}", FRAME, 3),
            path(f"M {cx-40} {cy-18} L {cx+40} {cy-18} L {cx} {cy+18} Z",
                 AMBER, 3, "#2A3550"),
            path(f"M {cx-40} {cy+18} L {cx+40} {cy+18}", AMBER, 4),
        ]
    p.append(text(cx, cy - 56 if horizontal else cy - 74, label, 15, TEXT,
                  "middle", "700"))
    p.append(text(cx, cy + 78 if horizontal else cy + 88, desc, 12, MUTED, "middle"))
    return "".join(p)


def transistor_to92(cx, cy, label="BC547"):
    p = [
        rect(cx - 34, cy - 46, 68, 92, fill="#1B2233", stroke=FRAME, rx=18),
        rect(cx - 26, cy - 38, 52, 76, fill="#232C42", stroke="none", rx=14),
        line(cx - 34, cy + 20, cx - 34, cy + 78, FRAME, 4),
        line(cx, cy + 46, cx, cy + 78, FRAME, 4),
        line(cx + 34, cy + 20, cx + 34, cy + 78, FRAME, 4),
        rect(cx - 46, cy + 78, 24, 12, fill=SILVER, stroke=FRAME, rx=3),
        rect(cx - 12, cy + 78, 24, 12, fill=SILVER, stroke=FRAME, rx=3),
        rect(cx + 22, cy + 78, 24, 12, fill=SILVER, stroke=FRAME, rx=3),
        text(cx - 52, cy + 26, "E", 14, MUTED),
        text(cx + 40, cy + 26, "C", 14, MUTED),
        text(cx - 6, cy + 40, "B", 14, MUTED),
        text(cx, cy - 60, label, 15, TEXT, "middle", "700"),
    ]
    return "".join(p)


def ic_dip(x, y, w=150, h=90, pins=7, label="NE555", notch=True):
    p = [rect(x, y, w, h, fill="#141A26", stroke=FRAME, rx=6)]
    for i in range(pins):
        px = x + 12 + i * (w - 24) / (pins - 1)
        p.append(rect(px - 6, y - 14, 12, 16, fill=SILVER, stroke=FRAME, rx=2))
        p.append(rect(px - 6, y + h - 2, 12, 16, fill=SILVER, stroke=FRAME, rx=2))
    if notch:
        p.append(path(f"M {x+w/2-12} {y+6} A 12 12 0 0 0 {x+w/2+12} {y+6}", FRAME, 2))
    p.append(text(x + w / 2, y + h / 2 + 5, label, 17, TEXT, "middle", "700"))
    return "".join(p)


def wire_run(x1, y1, x2, y2, color="#C36A2D", w=10, dash=None):
    return path(f"M {x1} {y1} L {x2} {y2}", color, w, "none", dash)


def multimeter(cx, cy):
    w, h = 300, 330
    x, y = cx - w / 2, cy - h / 2
    p = [
        rect(x, y, w, h, fill="#1B2233", stroke=FRAME, sw=3, rx=22),
        rect(x + 20, y + 18, w - 40, 96, fill="#0B1220", stroke=FRAME, rx=10),
        text(cx - 100, y + 56, "12.48", 30, CYAN, "middle", "700"),
        text(cx + 96, y + 56, "V", 20, MUTED),
        text(cx - 100, y + 88, "DC", 13, MUTED, "middle"),
        circle(cx, y + 190, 74, fill="#141A26", stroke=FRAME, sw=3),
        rect(cx - 6, y + 190 - 74, 12, 24, fill=AMBER, stroke="none", rx=4),
        text(cx, y + 190 + 6, "Ω", 26, MUTED, "middle", "700"),
        text(cx - 42, y + 196, "V⎓", 14, MUTED, "middle"),
        text(cx + 46, y + 196, "A", 14, MUTED, "middle"),
        circle(x + 66, y + h - 40, 20, fill="#0B1220", stroke=RED, sw=3),
        circle(x + 110, y + h - 40, 20, fill="#0B1220", stroke=FRAME, sw=3),
        text(x + 66, y + h - 35, "10A", 10, RED, "middle"),
        text(x + 110, y + h - 35, "COM", 9, MUTED, "middle"),
    ]
    return "".join(p)


def iron_body(cx, cy, scale=1.0):
    p = [
        path(f"M {cx-150} {cy-10} L {cx+60} {cy-10} L {cx+120} {cy} L {cx+60} {cy+10} Z",
             SILVER, 3, "#39424F"),
        path(f"M {cx+120} {cy} L {cx+200} {cy}", AMBER, 4),
        path(f"M {cx-150} {cy-10} L {cx-150} {cy+10}", "#5A6472", 4),
        rect(cx - 240, cy - 26, 92, 52, fill="#1F2A3D", stroke=FRAME, rx=12),
        rect(cx - 250, cy - 34, 34, 68, fill="#2A3A55", stroke=FRAME, rx=10),
    ]
    return "".join(p)


def probe(cx, cy, color=RED, down=True):
    d = 1 if down else -1
    return "".join([
        path(f"M {cx-7} {cy} L {cx+7} {cy}", color, 4),
        path(f"M {cx-7} {cy} L {cx-7} {cy + 70 * d}", FRAME, 6),
        path(f"M {cx+7} {cy} L {cx+7} {cy + 70 * d}", FRAME, 6),
        path(f"M {cx-7} {cy + 70 * d} L {cx+7} {cy + 70 * d}", "#8A94A6", 5),
    ])