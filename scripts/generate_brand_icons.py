#!/usr/bin/env python3
"""Gera todos os icones oficiais do Vida Academica a partir de docs/brand/.

Fonte unica:
- docs/brand/app-icon-master.png
- docs/brand/ic_launcher_monochrome.xml
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parent.parent
BRAND_DIR = REPO_ROOT / "docs" / "brand"
MASTER_ICON = BRAND_DIR / "app-icon-master.png"
MONOCHROME_XML = BRAND_DIR / "ic_launcher_monochrome.xml"

ANDROID_RES_DIR = REPO_ROOT / "android-app" / "app" / "src" / "main" / "res"
IOS_APPICON_DIR = (
    REPO_ROOT
    / "ios-app"
    / "CampusUFOP"
    / "Resources"
    / "Assets.xcassets"
    / "AppIcon.appiconset"
)
DOCS_DIR = REPO_ROOT / "docs"

ANDROID_LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Canvas 108dp por densidade para Adaptive Icon foreground
ANDROID_FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

SAFE_ZONE_RATIO = 0.66
LANCZOS = Image.Resampling.LANCZOS


def make_circle_mask(size: int) -> Image.Image:
    """Cria mascara circular com antialiasing via superamostragem 4x."""
    ss = size * 4
    mask = Image.new("L", (ss, ss), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, ss - 1, ss - 1), fill=255)
    return mask.resize((size, size), LANCZOS)


def make_soft_edge_mask(size: int, feather_ratio: float = 0.05) -> Image.Image:
    """Suaviza apenas a margem externa do foreground para fundir com #4A0E1C."""
    margin = max(1, round(size * feather_ratio))
    mask = Image.new("L", (size, size), 255)
    pixels = mask.load()
    for y in range(size):
        dy = min(y, size - 1 - y)
        wy = min(1.0, dy / margin)
        for x in range(size):
            dx = min(x, size - 1 - x)
            wx = min(1.0, dx / margin)
            w = wx * wy
            if w < 1.0:
                pixels[x, y] = int(round(255 * w))
    return mask


def generate_android_icons(master_rgb: Image.Image) -> None:
    for density, size in ANDROID_LEGACY_SIZES.items():
        mipmap_dir = ANDROID_RES_DIR / f"mipmap-{density}"
        mipmap_dir.mkdir(parents=True, exist_ok=True)

        square = master_rgb.resize((size, size), LANCZOS)
        square.save(mipmap_dir / "ic_launcher.png", format="PNG", optimize=True)

        round_img = square.convert("RGBA")
        round_img.putalpha(make_circle_mask(size))
        round_img.save(mipmap_dir / "ic_launcher_round.png", format="PNG", optimize=True)

    for density, canvas_size in ANDROID_FOREGROUND_SIZES.items():
        drawable_dir = ANDROID_RES_DIR / f"drawable-{density}"
        drawable_dir.mkdir(parents=True, exist_ok=True)

        inner_size = round(canvas_size * SAFE_ZONE_RATIO)
        scaled = master_rgb.resize((inner_size, inner_size), LANCZOS).convert("RGBA")
        scaled.putalpha(make_soft_edge_mask(inner_size))

        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        offset = (canvas_size - inner_size) // 2
        canvas.paste(scaled, (offset, offset), scaled)
        canvas.save(
            drawable_dir / "ic_launcher_foreground.png", format="PNG", optimize=True
        )

    monochrome_dest = ANDROID_RES_DIR / "drawable" / "ic_launcher_monochrome.xml"
    monochrome_dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(MONOCHROME_XML, monochrome_dest)


def generate_ios_icon(master_rgb: Image.Image) -> None:
    IOS_APPICON_DIR.mkdir(parents=True, exist_ok=True)
    icon_1024 = (
        master_rgb
        if master_rgb.size == (1024, 1024)
        else master_rgb.resize((1024, 1024), LANCZOS)
    )
    icon_1024.save(IOS_APPICON_DIR / "AppIcon-1024.png", format="PNG", optimize=True)

    contents = {
        "images": [
            {
                "filename": "AppIcon-1024.png",
                "idiom": "universal",
                "platform": "ios",
                "size": "1024x1024",
            }
        ],
        "info": {
            "author": "xcode",
            "version": 1,
        },
    }
    (IOS_APPICON_DIR / "Contents.json").write_text(
        json.dumps(contents, indent=2) + "\n", encoding="utf-8"
    )


def generate_web_icons(master_rgb: Image.Image) -> None:
    apple_touch = master_rgb.resize((180, 180), LANCZOS)
    apple_touch.save(DOCS_DIR / "apple-touch-icon.png", format="PNG", optimize=True)


def main() -> None:
    if not MASTER_ICON.is_file():
        raise FileNotFoundError(f"Arquivo mestre nao encontrado: {MASTER_ICON}")
    if not MONOCHROME_XML.is_file():
        raise FileNotFoundError(f"Arquivo monocromatico nao encontrado: {MONOCHROME_XML}")

    with Image.open(MASTER_ICON) as img:
        master_rgb = img.convert("RGB")
        generate_android_icons(master_rgb)
        generate_ios_icon(master_rgb)
        generate_web_icons(master_rgb)

    print("Icones oficiais gerados com sucesso para Android, iOS e Web.")


if __name__ == "__main__":
    main()
