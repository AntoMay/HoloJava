#!/usr/bin/env python3
"""Prepares the HoloCure-style title screen assets for the 320x240 canvas.

Sources come from the unpacked HoloCure data (Kay Yu's game), same tree the other
extraction tools use. MIDP 2.0 cannot scale images at runtime, so every output is
already at its display size:

  ui/title_bg.png      320x240  stage backdrop (hud_new_title_BG, center-cropped)
  ui/menu_cast.png     320x116  transparent crowd of every playable talent's idle
                                sprite, exactly once each (47 in the source build;
                                the seven Holo House critters stay out), laid out in
                                four staggered rows like the game's title roster
  ui/menu_icon_0..7    halved hud_title_icons (the eight bar entries we use,
                                frame 7 the red X for the Keluar entry)
  ui/menu_label.png    116x20   the gold-bordered label box (menu_first frame 0),
                                wide enough for the longest entry name
  ui/menu_text_0..7    116x20   each entry name, pre-rendered white and centred,
                                one notch smaller than MIDP's smallest font (see
                                label_texts)

Run: tools/prepare_title_menu_assets.py [--source /path/to/HoloCure_Unpacked]
"""

import argparse
import random
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = Path.home() / "Projects" / "HoloCure_Unpacked"
UI = ROOT / "assets" / "fullgame" / "ui"

# Display geometry on the 320x240 canvas, mirrored by FullGameCanvas.drawMainMenu.
SCREEN_W, SCREEN_H = 320, 240
CAST_W, CAST_H = 320, 116
ICON_COUNT = 8
LABEL_W, LABEL_H = 116, 20
# MAIN_ITEMS in FullGameCanvas, in bar order. The names are baked as images (label_texts)
# because MIDP exposes only small/medium/large and the label already sat at small: a fixed
# vocabulary is what makes pre-rendering the whole set practical.
ENTRY_NAMES = [
    "Papan Peringkat",
    "Pencapaian",
    "Toko",
    "Main",
    "Holo House",
    "Pengaturan",
    "Credits",
    "Keluar",
]
# DejaVu Sans Bold at 10px renders "Papan Peringkat" ~93px wide against the ~107px the
# 12px logical SansSerif bold used before, i.e. the "slightly smaller" label request.
LABEL_FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
LABEL_TEXT_PX = 10


def title_background(source: Path) -> Image.Image:
    """hud_new_title_BG (660x360, 16:9) center-cropped to 4:3 and scaled to 320x240."""
    bg = Image.open(source / "textures" / "Sprites" / "hud_new_title_BG_0.png").convert("RGB")
    w, h = bg.size
    keep = int(h * 4 / 3)                       # 480 of 660 columns at native height
    left = (w - keep) // 2
    bg = bg.crop((left, 0, left + keep, h))
    return bg.resize((SCREEN_W, SCREEN_H), Image.LANCZOS)


def base_idle_dirs(source: Path):
    """Base-costume idle sprite dirs: spr_<Name>_idle, without the O1+ outfit variants.

    Case-insensitive on purpose: the only base dir spelled with a capital I is
    Gura's ``spr_Gura_Idle``, and the strict match silently dropped her from the
    roster -- 46 talents instead of the game's 47.
    """
    pattern = re.compile(r"^spr_[A-Za-z0-9]+_idle$", re.IGNORECASE)
    sprites = source / "sprites"
    return sorted(d for d in sprites.iterdir() if d.is_dir() and pattern.match(d.name))


def load_trimmed(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


# The seven Holo House critter ids (bug-catch / fishing mascots). They match the
# base-idle shape above but they are not hololive talents, and the original's
# title crowd is the talent roster itself -- the wiki describes the 0.5+
# title screen as "all of the hololive members featured in a group" -- so these
# never stand in it.
CRITTER_IDS = frozenset({
    "spr_Beetle_idle", "spr_Bunny_idle", "spr_Cat_idle", "spr_Dog_idle",
    "spr_Goriela_idle", "spr_Monkey_idle", "spr_Penguin_idle",
})


def character_collage(source: Path) -> Image.Image:
    """Four staggered rows holding every talent exactly once, over transparency.

    The crowd is the roster: the seven Holo House critters are left out and no
    talent repeats to fill space. The old version wrapped around the directory
    list once it ran past ~53 entries, so half the strip was duplicates of a
    roster that was itself missing Gura; instead each row now derives its
    spacing from its own sprites and spans the strip however they pack.

    Deterministic: the same seed always rebuilds the same crowd, so the asset only
    changes when the script or the source art does.
    """
    rng = random.Random(20261018)
    dirs = [d for d in base_idle_dirs(source) if d.name not in CRITTER_IDS]
    if not dirs:
        raise SystemExit("no talent idle sprite dirs found under %s/sprites" % source)
    rng.shuffle(dirs)

    canvas = Image.new("RGBA", (CAST_W, CAST_H), (0, 0, 0, 0))
    # Row tops and heights: back rows smaller and higher, front rows bigger.
    rows = [(2, 24), (28, 26), (56, 28), (84, 30)]
    # Split the roster across the rows in proportion to row height (largest
    # remainder), so the taller front rows hold slightly fewer, wider chibis.
    total_height = float(sum(height for _, height in rows))
    shares = [len(dirs) * height / total_height for _, height in rows]
    counts = [int(share) for share in shares]
    for i in sorted(range(len(rows)),
                    key=lambda i: shares[i] - counts[i],
                    reverse=True)[:len(dirs) - sum(counts)]:
        counts[i] += 1

    cursor = 0
    for row_index, ((top, height), count) in enumerate(zip(rows, counts)):
        frames = []
        for d in dirs[cursor:cursor + count]:
            cursor += 1
            frame = load_trimmed(d / (d.name + "_0.png"))
            if frame.height < 4 or frame.width < 4:
                continue
            scale = height / frame.height
            width = max(2, int(frame.width * scale))
            frames.append(frame.resize((width, height), Image.BOX))
        if not frames:
            continue
        start = -rng.randint(0, 10) - row_index * 7   # stagger the row starts per row
        # Gap tuned for a tight, overlapping crowd: aim for ~2 px overlap
        # instead of exact-width fill, so characters visually pack together.
        natural_gap = ((CAST_W - start - sum(f.width for f in frames)) / (len(frames) - 1)
                       if len(frames) > 1 else 0)
        gap = min(natural_gap, -2.0)  # clamp to at most -2 px (overlap)
        x = float(start)
        for frame in frames:
            canvas.alpha_composite(frame, (int(x), top + rng.randint(0, 2)))
            x += frame.width + gap
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


def label_texts() -> None:
    """Bakes every ENTRY_NAMES string, white on transparency, centred in the label box.

    The canvas draws these on top of menu_label.png at the same coordinates, so the box
    and its text slide in together. FullGameCanvas falls back to a drawn SIZE_SMALL
    string when an asset is missing, which keeps a partial build readable.
    """
    font = ImageFont.truetype(LABEL_FONT, LABEL_TEXT_PX)
    for index, name in enumerate(ENTRY_NAMES):
        canvas = Image.new("RGBA", (LABEL_W, LABEL_H), (0, 0, 0, 0))
        draw = ImageDraw.Draw(canvas)
        # +1px drops the optical centre onto the box's interior mid-line.
        draw.text((LABEL_W / 2, LABEL_H / 2 + 1), name, font=font,
                  fill=(255, 255, 255, 255), anchor="mm")
        canvas.save(UI / ("menu_text_%d.png" % index))


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
    label_texts()

    for path in sorted(UI.glob("menu_*.png")) + [UI / "title_bg.png"]:
        print("%s %s" % (path.relative_to(ROOT), Image.open(path).size))
    return 0


if __name__ == "__main__":
    sys.exit(main())
