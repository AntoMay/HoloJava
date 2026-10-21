#!/usr/bin/env python3
"""Prepares the HoloCure-style title screen assets for the 320x240 canvas.

Sources come from the unpacked HoloCure data (Kay Yu's game), same tree the other
extraction tools use. MIDP 2.0 cannot scale images at runtime, so every output is
already at its display size:

  ui/title_bg.png        320x240  stage backdrop (hud_new_title_BG, center-cropped)
  ui/menu_cast.png       320x116  transparent crowd of every playable talent's TITLE
                                  sprite (spr_Title_*), exactly once each (47 talents),
                                  densely packed in a natural arch on the stage floor
  ui/menu_logo.png       176x34   title logo (hud_titlebar_0, scaled to 176px wide)
  ui/sparkle_0.png       9x11     sparkle frame 0 (hudfx_sparkle_0, halved)
  ui/sparkle_1.png       7x9      sparkle frame 1 (hudfx_sparkle_1, halved)
  ui/menu_icon_0..7      halved hud_title_icons (the eight bar entries we use,
                                  frame 7 the red X for the Keluar entry)
  ui/menu_label.png      116x20   the gold-bordered label box (menu_first frame 0),
                                  wide enough for the longest entry name
  ui/menu_text_0..7      116x20   each entry name, pre-rendered white and centred,
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

# Graduated members (Update 0.7+) who get a sparkle on the title screen.
# Source: HoloCure wiki - "The title screen for Update 0.7 identifies former talents
# that have left hololive via a sparkle that appears on them."
GRADUATED_IDS = frozenset({
    "spr_Title_Coco_0.png",      # Kiryu Coco
    "spr_Title_Sana_0.png",      # Tsukumo Sana
    "spr_Title_Mel_0.png",       # Yozora Mel
    "spr_Title_Aqua_0.png",      # Minato Aqua
    "spr_Title_Ame_0.png",       # Amelia Watson
    "spr_Title_Fauna_0.png",     # Ceres Fauna
    "spr_Title_Shion_0.png",     # Murasaki Shion
    "spr_Title_Mumei_0.png",     # Nanashi Mumei
    "spr_Title_Gura_0.png",      # Gawr Gura
    "spr_Title_Kanata_0.png",    # Amane Kanata
})


def title_background(source: Path) -> Image.Image:
    """hud_new_title_BG (660x360, 16:9) center-cropped to 4:3 and scaled to 320x240.

    The crop keeps the starry sky and the stage floor. The stage floor in the
    source is at the bottom ~60px; after center-crop+resize it sits at y≈180-200
    in the 320x240 output. The crowd (menu_cast) is drawn at y=64 in the canvas,
    which places the characters' feet on the stage floor when the cast image
    bottom aligns with the backdrop's floor.
    """
    bg = Image.open(source / "textures" / "Sprites" / "hud_new_title_BG_0.png").convert("RGB")
    w, h = bg.size
    keep = int(h * 4 / 3)                       # 480 of 660 columns at native height
    left = (w - keep) // 2
    bg = bg.crop((left, 0, left + keep, h))
    return bg.resize((SCREEN_W, SCREEN_H), Image.LANCZOS)


def load_trimmed(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im


def title_sprite_paths(source: Path):
    """Return paths to the 47 spr_Title_*_0.png files, one per talent.

    These are the pre-posed title screen character sprites from the source game,
    exactly 47 matching the playable roster. They are larger and properly posed
    for the title screen crowd (unlike the gameplay idle sprites).
    """
    sprites_dir = source / "textures" / "Sprites"
    paths = []
    for p in sorted(sprites_dir.glob("spr_Title_*_0.png")):
        # Skip the full title screen composite and any non-talent sprites
        name = p.stem  # e.g. "spr_Title_Gura_0"
        if name == "spr_HoloCureTitle_full_0":
            continue
        paths.append(p)
    if len(paths) != 47:
        print("WARNING: found %d spr_Title_* sprites (expected 47)" % len(paths), file=sys.stderr)
    return paths


def character_collage(source: Path) -> Image.Image:
    """Title screen crowd matching the official HoloCure wiki layout exactly.

    Layout (4 rows, logo centered in row 1):
    Row 1 (10 chars + logo center): Pekora, Noel, Flare, Marine, [LOGO], Coco, Kanata, Towa, Watame, Luna
    Row 2 (13 chars): Kaela, Kobo, Zeta, Anya, Reine, Ollie, Iofi, Moona, Risu, Aki, Haato, Shion, Ayame
    Row 3 (13 chars): Korone, Okayu, Mio, Fubuki, AZKi, Roboco, Sora, Suisei, Miko, Matsuri, Mel, Choco, Subaru
    Row 4 (12 chars): Kronii, Sana, IRyS, Fauna, Mumei, Bae, Ame, Gura, Ina, Kiara, Calli, Aqua

    Total: 47 characters + logo in row 1 center.
    Matches official HoloCure wiki title screen layout exactly.
    """
    # Exact character order per wiki reference - 4 rows
    row1_left = ["spr_Title_Pekora_0", "spr_Title_Noel_0", "spr_Title_Flare_0", "spr_Title_Marine_0"]
    row1_right = ["spr_Title_Coco_0", "spr_Title_Kanata_0", "spr_Title_Towa_0", "spr_Title_Watame_0", "spr_Title_Luna_0"]
    row2 = ["spr_Title_Kaela_0", "spr_Title_Kobo_0", "spr_Title_Zeta_0", "spr_Title_Anya_0",
            "spr_Title_Reine_0", "spr_Title_Ollie_0", "spr_Title_Iofi_0", "spr_Title_Moona_0",
            "spr_Title_Risu_0", "spr_Title_Aki_0", "spr_Title_Haato_0", "spr_Title_Shion_0", "spr_Title_Ayame_0"]
    row3 = ["spr_Title_Korone_0", "spr_Title_Okayu_0", "spr_Title_Mio_0", "spr_Title_Fubuki_0",
            "spr_Title_Azki_0", "spr_Title_Roboco_0", "spr_Title_Sora_0", "spr_Title_Suisei_0",
            "spr_Title_Miko_0", "spr_Title_Matsuri_0", "spr_Title_Mel_0", "spr_Title_Choco_0", "spr_Title_Subaru_0"]
    row4 = ["spr_Title_Kronii_0", "spr_Title_Sana_0", "spr_Title_Irys_0", "spr_Title_Fauna_0",
            "spr_Title_Mumei_0", "spr_Title_bae_0", "spr_Title_Ame_0", "spr_Title_Gura_0",
            "spr_Title_Ina_0", "spr_Title_Kiara_0", "spr_Title_Calli_0", "spr_Title_Aqua_0"]

    # Load all sprites into a dict for lookup
    sprites_dir = source / "textures" / "Sprites"
    sprite_dict = {}
    for p in sorted(sprites_dir.glob("spr_Title_*_0.png")):
        name = p.stem
        if name == "spr_HoloCureTitle_full_0":
            continue
        frame = load_trimmed(p)
        if frame.width >= 4 and frame.height >= 4:
            sprite_dict[name] = frame

    canvas = Image.new("RGBA", (CAST_W, CAST_H), (0, 0, 0, 0))

    # Row configuration: 4 rows, back to front
    # Row 0 (back): smallest, highest; Row 3 (front): largest, lowest
    row_heights = [24, 28, 32, 36]  # increasing height per row
    row_tops = [10, 34, 62, 86]     # vertical positions
    # Ensure last row bottom = CAST_H (116)
    row_tops[3] = CAST_H - 36

    # Logo sprite (from spr_gacha_holologo_0.png - fully opaque)
    logo_path = Path("/home/antomay/Projects/HoloCure_Unpacked/textures/Sprites/spr_gacha_holologo_0.png")
    logo_img = load_trimmed(Path(logo_path))
    logo_scale = 40 / logo_img.height  # logo height ~40px in grid
    logo_img = logo_img.resize((int(logo_img.width * 40 / logo_img.height), 40), Image.LANCZOS)

    rng = random.Random(20261018)

    all_rows = [
        ["spr_Title_Pekora_0", "spr_Title_Noel_0", "spr_Title_Flare_0", "spr_Title_Marine_0"],
        ["spr_Title_Kaela_0", "spr_Title_Kobo_0", "spr_Title_Zeta_0", "spr_Title_Anya_0",
         "spr_Title_Reine_0", "spr_Title_Ollie_0", "spr_Title_Iofi_0", "spr_Title_Moona_0",
         "spr_Title_Risu_0", "spr_Title_Aki_0", "spr_Title_Haato_0", "spr_Title_Shion_0", "spr_Title_Ayame_0"],
        ["spr_Title_Korone_0", "spr_Title_Okayu_0", "spr_Title_Mio_0", "spr_Title_Fubuki_0",
         "spr_Title_Azki_0", "spr_Title_Roboco_0", "spr_Title_Sora_0", "spr_Title_Suisei_0",
         "spr_Title_Miko_0", "spr_Title_Matsuri_0", "spr_Title_Mel_0", "spr_Title_Choco_0", "spr_Title_Subaru_0"],
        ["spr_Title_Kronii_0", "spr_Title_Sana_0", "spr_Title_Irys_0", "spr_Title_Fauna_0",
         "spr_Title_Mumei_0", "spr_Title_bae_0", "spr_Title_Ame_0", "spr_Title_Gura_0",
         "spr_Title_Ina_0", "spr_Title_Kiara_0", "spr_Title_Calli_0", "spr_Title_Aqua_0"]
    ]
    row1_right = ["spr_Title_Coco_0", "spr_Title_Kanata_0", "spr_Title_Towa_0", "spr_Title_Watame_0", "spr_Title_Luna_0"]

    for row_idx, row_names in enumerate(all_rows):
        if row_idx == 0:
            # Row 1: left part, logo, right part
            names = ["spr_Title_Pekora_0", "spr_Title_Noel_0", "spr_Title_Flare_0", "spr_Title_Marine_0"] + ["LOGO"] + row1_right
        elif row_idx == 1:
            names = ["spr_Title_Kaela_0", "spr_Title_Kobo_0", "spr_Title_Zeta_0", "spr_Title_Anya_0",
                     "spr_Title_Reine_0", "spr_Title_Ollie_0", "spr_Title_Iofi_0", "spr_Title_Moona_0",
                     "spr_Title_Risu_0", "spr_Title_Aki_0", "spr_Title_Haato_0", "spr_Title_Shion_0", "spr_Title_Ayame_0"]
        elif row_idx == 2:
            names = ["spr_Title_Korone_0", "spr_Title_Okayu_0", "spr_Title_Mio_0", "spr_Title_Fubuki_0",
                     "spr_Title_Azki_0", "spr_Title_Roboco_0", "spr_Title_Sora_0", "spr_Title_Suisei_0",
                     "spr_Title_Miko_0", "spr_Title_Matsuri_0", "spr_Title_Mel_0", "spr_Title_Choco_0", "spr_Title_Subaru_0"]
        else:
            names = ["spr_Title_Kronii_0", "spr_Title_Sana_0", "spr_Title_Irys_0", "spr_Title_Fauna_0",
                     "spr_Title_Mumei_0", "spr_Title_bae_0", "spr_Title_Ame_0", "spr_Title_Gura_0",
                     "spr_Title_Ina_0", "spr_Title_Kiara_0", "spr_Title_Calli_0", "spr_Title_Aqua_0"]

        # Load and resize frames for this row
        height = row_heights[row_idx]
        row_frames = []
        for name in names:
            if name == "LOGO":
                row_frames.append(("LOGO", logo_img))
                continue
            frame = sprite_dict.get(name)
            if not frame:
                continue
            scale = height / frame.height
            width = max(2, int(frame.width * scale))
            row_frames.append((name, frame.resize((width, height), Image.BOX)))

        if not row_frames:
            continue

        top = row_tops[row_idx]
        overlap = -6 if row_idx < 2 else -8  # tighter overlap in front rows

        # Calculate total width and center
        total_w = sum(f[1].width for f in row_frames) + overlap * (len(row_frames) - 1)
        start = (CAST_W - total_w) // 2

        x = float(start)
        for name, frame in row_frames:
            px = int(x)
            py = top + rng.randint(0, 1)
            canvas.alpha_composite(frame, (px, py))
            x += frame.width + overlap

    return canvas

def menu_logo(source: Path) -> None:
    """Title logo extracted from the reference title screen composite.
    
    Extracted from spr_HoloCureTitle_full_0.png (rows 0-39, centered 240px wide).
    Scaled to 320px wide maintaining aspect ratio = 320x53.
    Fully opaque (extracted from composite where logo is fully visible).
    """
    # Extract logo from reference composite
    ref = Image.open(source / "textures" / "Sprites" / "spr_HoloCureTitle_full_0.png")
    scaled = ref.resize((320, 240), Image.LANCZOS)
    # Logo is at top, centered, ~240px wide x 40px tall in 320x240 scaled
    logo = scaled.crop((40, 0, 280, 40))  # x=40-280 (240 wide), y=0-40 (40 tall)
    # Scale to full canvas width (320px) maintaining aspect ratio
    target_w = 320
    scale = target_w / logo.width
    target_h = max(1, int(logo.height * scale))
    logo.resize((target_w, target_h), Image.LANCZOS).save(UI / "menu_logo.png")


def sparkle_frames(source: Path) -> None:
    """Sparkle animation frames (2 frames, halved for display size)."""
    for i in range(2):
        sparkle = Image.open(
            source / "textures" / "Sprites" / ("hudfx_sparkle_%d.png" % i)
        ).convert("RGBA")
        half = (max(1, sparkle.width // 2), max(1, sparkle.height // 2))
        sparkle.resize(half, Image.BOX).save(UI / ("sparkle_%d.png" % i))


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
    menu_logo(args.source)
    sparkle_frames(args.source)
    bar_icons(args.source)
    label_box(args.source)
    label_texts()

    for path in sorted(UI.glob("menu_*.png")) + [UI / "title_bg.png", UI / "menu_logo.png", UI / "sparkle_0.png", UI / "sparkle_1.png"]:
        print("%s %s" % (path.relative_to(ROOT), Image.open(path).size))
    return 0


if __name__ == "__main__":
    sys.exit(main())
