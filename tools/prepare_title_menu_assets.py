#!/usr/bin/env python3
"""Prepares the HoloCure-style title screen assets for the 320x240 canvas.

Sources come from the unpacked HoloCure data (Kay Yu's game), same tree the other
extraction tools use. MIDP 2.0 cannot scale images at runtime, so every output is
already at its display size:

  ui/title_bg.png      320x240  stage backdrop (hud_new_title_BG, center-cropped)
  ui/menu_cast.png     320x116  transparent collage of character idle sprites,
                                laid out in four staggered rows like the game's
                                roster crowd behind the logo
  ui/menu_icon_0..6    halved hud_title_icons (the seven bar entries we use)
  ui/menu_label.png    116x20   the gold-bordered label box (menu_first frame 0),
                                wide enough for the longest entry name

The eighth hud_title_icons frame (the red X) is the reference game's Exit row and
is deliberately not shipped: Exit moved to the Pengaturan page.

Run: tools/prepare_title_menu_assets.py [--source /path/to/HoloCure_Unpacked]
"""

import argparse
import random
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = Path.home() / "Projects" / "HoloCure_Unpacked"
UI = ROOT / "assets" / "fullgame" / "ui"

# Display geometry on the 320x240 canvas, mirrored by FullGameCanvas.drawMainMenu.
SCREEN_W, SCREEN_H = 320, 240
CAST_W, CAST_H = 320, 116
ICON_COUNT = 7
LABEL_W, LABEL_H = 116, 20


def title_background(source: Path) -> Image.Image:
    """hud_new_title_BG (660x360, 16:9) center-cropped to 4:3 and scaled to 320x240."""
    bg = Image.open(source / "textures" / "Sprites" / "hud_new_title_BG_0.png").convert("RGB")
    w, h = bg.size
    keep = int(h * 4 / 3)                       # 480 of 660 columns at native height
    left = (w - keep) // 2
    bg = bg.crop((left, 0, left + keep, h))
    return bg.resize((SCREEN_W, SCREEN_H), Image.LANCZOS)


def base_idle_dirs(source: Path):
    """Base-costume idle sprite dirs: spr_<Name>_idle, without the O1+ outfit variants."""
    pattern = re.compile(r"^spr_[A-Za-z0-9]+_idle$")
    sprites = source / "sprites"
    return sorted(d for d in sprites.iterdir() if d.is_dir() and pattern.match(d.name))


def load_trimmed(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


def character_collage(source: Path) -> Image.Image:
    """Four staggered rows of chibis, front rows slightly larger, over transparency.

    Deterministic: the same seed always rebuilds the same crowd, so the asset only
    changes when the script or the source art does.
    """
    rng = random.Random(20261018)
    dirs = base_idle_dirs(source)
    if not dirs:
        raise SystemExit("no base idle sprite dirs found under %s/sprites" % source)
    rng.shuffle(dirs)

    canvas = Image.new("RGBA", (CAST_W, CAST_H), (0, 0, 0, 0))
    # Row tops and heights: back rows smaller and higher, front rows bigger.
    rows = [(2, 24), (28, 26), (56, 28), (84, 30)]
    cursor = 0
    for row_index, (top, height) in enumerate(rows):
        x = -rng.randint(0, 12) - row_index * 7   # stagger the row starts per row
        while x < CAST_W:
            if cursor >= len(dirs):
                rng.shuffle(dirs)
                cursor = 0
            frame = load_trimmed(dirs[cursor] / (dirs[cursor].name + "_0.png"))
            cursor += 1
            if frame.height < 4 or frame.width < 4:
                continue
            scale = height / frame.height
            width = max(2, int(frame.width * scale))
            frame = frame.resize((width, height), Image.BOX)
            canvas.alpha_composite(frame, (x, top + rng.randint(0, 2)))
            x += max(10, int(width * 0.72))       # dense overlap, like the roster crowd
    return canvas


def bar_icons(source: Path) -> None:
    for index in range(ICON_COUNT):
        icon = Image.open(
            source / "sprites" / "hud_title_icons" / ("hud_title_icons_%d.png" % index)
        ).convert("RGBA")
        half = (max(1, icon.width // 2), max(1, icon.height // 2))
        icon.resize(half, Image.BOX).save(UI / ("menu_icon_%d.png" % index))


def label_box(source: Path) -> None:
    box = Image.open(source / "sprites" / "menu_first" / "menu_first_0.png").convert("RGBA")
    box.resize((LABEL_W, LABEL_H), Image.BOX).save(UI / "menu_label.png")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    if not args.source.is_dir():
        print("source tree missing: %s" % args.source, file=sys.stderr)
        return 1

    UI.mkdir(parents=True, exist_ok=True)
    title_background(args.source).save(UI / "title_bg.png")
    character_collage(args.source).save(UI / "menu_cast.png")
    bar_icons(args.source)
    label_box(args.source)

    for path in sorted(UI.glob("menu_*.png")) + [UI / "title_bg.png"]:
        print("%s %s" % (path.relative_to(ROOT), Image.open(path).size))
    return 0


if __name__ == "__main__":
    sys.exit(main())
