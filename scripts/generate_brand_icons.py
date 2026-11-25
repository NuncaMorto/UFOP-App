#!/usr/bin/env python3
"""Gera todos os icones oficiais do Vida Academica a partir de docs/brand/.

Fonte unica:
- docs/brand/app-icon-master.png
- docs/brand/ic_launcher_monochrome.xml
"""

from __future__ import annotations

import json
import os
import shutil
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

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


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    win_fonts = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts"
    candidates = (
        [
            win_fonts / "segoeuib.ttf",
            win_fonts / "arialbd.ttf",
            Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
        ]
        if bold
        else [
            win_fonts / "segoeui.ttf",
            win_fonts / "arial.ttf",
            Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        ]
    )
    for font_path in candidates:
        if font_path.is_file():
            return ImageFont.truetype(str(font_path), size=size)
    return ImageFont.load_default()


def make_rounded_rect_mask(size: int, radius: int) -> Image.Image:
    ss = 4
    mask = Image.new("L", (size * ss, size * ss), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle(
        (0, 0, size * ss - 1, size * ss - 1), radius=radius * ss, fill=255
    )
    return mask.resize((size, size), LANCZOS)


def generate_social_preview(master_rgb: Image.Image) -> None:
    """Gera docs/brand/social-preview.png (1280x640) para GitHub Social Preview e og:image."""
    width, height = 1280, 640
    canvas = Image.new("RGBA", (width, height), (28, 25, 23, 255))

    # Brilhos sutis nas cores oficiais (bordo e esmeralda)
    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse((-140, -180, 620, 560), fill=(139, 30, 63, 120))
    gdraw.ellipse((860, 260, 1460, 860), fill=(46, 125, 50, 75))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=110))
    canvas = Image.alpha_composite(canvas, glow)

    # Moldura sutil estilo card com alpha composite real
    card_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    cdraw = ImageDraw.Draw(card_layer)
    cdraw.rounded_rectangle(
        (48, 48, width - 48, height - 48),
        radius=36,
        fill=(38, 33, 31, 215),
        outline=(255, 255, 255, 34),
        width=2,
    )
    canvas = Image.alpha_composite(canvas, card_layer)

    # Icone oficial com sombra e cantos arredondados (260x260)
    icon_size = 260
    icon_x, icon_y = 124, (height - icon_size) // 2
    mask = make_rounded_rect_mask(icon_size, radius=60)

    shadow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    shadow_shape = Image.new("RGBA", (icon_size, icon_size), (0, 0, 0, 150))
    shadow.paste(shadow_shape, (icon_x, icon_y + 14), mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=22))
    canvas = Image.alpha_composite(canvas, shadow)

    icon_rgba = master_rgb.resize((icon_size, icon_size), LANCZOS).convert("RGBA")
    icon_rgba.putalpha(mask)
    canvas.paste(icon_rgba, (icon_x, icon_y), icon_rgba)

    # Tipografia a direita
    text_x = icon_x + icon_size + 68

    badge_font = load_font(20, bold=True)
    title_font = load_font(74, bold=True)
    sub_font = load_font(28, bold=False)
    foot_font = load_font(22, bold=True)

    # Pill superior com alpha composite real e centralizacao exata
    badge_text = "OPEN SOURCE  ·  OFFLINE-FIRST"
    pill_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(pill_layer)
    bbox = pdraw.textbbox((0, 0), badge_text, font=badge_font)
    bw = bbox[2] - bbox[0]
    bx, by, pill_w, pill_h = text_x, 166, bw + 40, 40
    pdraw.rounded_rectangle(
        (bx, by, bx + pill_w, by + pill_h),
        radius=20,
        fill=(139, 30, 63, 75),
        outline=(214, 87, 127, 145),
        width=1,
    )
    pdraw.text(
        (bx + pill_w / 2, by + pill_h / 2),
        badge_text,
        font=badge_font,
        fill=(245, 208, 218, 255),
        anchor="mm",
    )
    canvas = Image.alpha_composite(canvas, pill_layer)

    draw = ImageDraw.Draw(canvas)

    # Titulo principal
    draw.text((text_x, 224), "Vida Acadêmica", font=title_font, fill=(245, 245, 244, 255))

    # Subtitulo em 2 linhas
    draw.text(
        (text_x, 326),
        "Horários, faltas, notas, matrizes curriculares",
        font=sub_font,
        fill=(196, 189, 184, 255),
    )
    draw.text(
        (text_x, 366),
        "e mapa do campus 100% offline.",
        font=sub_font,
        fill=(196, 189, 184, 255),
    )

    # Rodape tecnico
    draw.text(
        (text_x, 438),
        "Android  ·  iOS  ·  Rust Core  ·  GNU GPLv3",
        font=foot_font,
        fill=(102, 187, 106, 255),
    )

    canvas.convert("RGB").save(
        BRAND_DIR / "social-preview.png", format="PNG", optimize=True
    )


def generate_web_icons(master_rgb: Image.Image) -> None:
    apple_touch = master_rgb.resize((180, 180), LANCZOS)
    apple_touch.save(DOCS_DIR / "apple-touch-icon.png", format="PNG", optimize=True)
    generate_social_preview(master_rgb)


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
