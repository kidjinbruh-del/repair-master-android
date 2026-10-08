"""
Рендерит иллюстрации из assets.py в app/src/main/assets/img/*.webp.

Запуск:  python tools/gen_images.py [--only name1,name2]
"""

import pathlib
import sys

from PIL import Image
from playwright.sync_api import sync_playwright

import assets as A
import tech_assets as T
from draw import OUT, RAW

SCALE = 2


def main() -> int:
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1].split(",")

    OUT.mkdir(parents=True, exist_ok=True)
    RAW.mkdir(parents=True, exist_ok=True)

    names = [n for n in T.all_assets() if (only is None or n in only)]
    if not names:
        print("nothing to render")
        return 1

    html_by_name = {}
    for name in names:
        try:
            html_by_name[name] = T.get(name)
        except Exception as exc:  # noqa: BLE001
            print(f"  !! {name}: {exc}")

    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(
            viewport={"width": 900, "height": 560},
            device_scale_factor=SCALE,
        )
        for name, html in html_by_name.items():
            page.set_content(html, wait_until="load")
            png = RAW / f"{name}.png"
            page.screenshot(path=str(png))
            img = Image.open(png).convert("RGB")
            webp = OUT / f"{name}.webp"
            img.save(webp, "WEBP", quality=84, method=6)
            kb = webp.stat().st_size / 1024
            print(f"  ok {name:26s} {kb:6.1f} KB")
        browser.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())