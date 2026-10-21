#!/usr/bin/env python3
"""Bake the Main Menu (title screen) art for HoloJava's 320x240 screen.

The reference HoloCure title screen (rm_Title, a 640x360 GameMaker room) lays the
whole roster out in four staggered rows with the HoloCure logo dropped into the gap
in the top row, over a bright rainbow "burst" backdrop with a hot spotlight glow
behind the logo and a dark bar carrying the white menu icons.

Every piece of art is pre-scaled here because CLDC 1.1 / MIDP 2.0 cannot scale or
rotate images at runtime. Output goes to assets/fullgame/ui/:

  title_bg.png          320x240  rainbow burst backdrop + baked dark icon bar
  menu_logo.png         ~118x50  HoloCure wordmark (from spr_HoloCureTitle_full)
  menu_logo_shine.png   same     logo with a diagonal shine sweep (shine frame)
  title_char_<i>.png    per-char one roster sprite, pre-scaled, in display order

The roster order and the room coordinates below are taken straight from the
reference room dump (HoloCure_Unpacked/index/Rooms_Detail.txt, "rm_Title"), whose
origin is bottom-centre per character. The script also prints the screen-space
table (left x, top y) that FullGameCanvas bakes in, so the Java layout and this
script can never drift apart.

Run:  python3 tools/prepare_title_menu_assets.py
"""

import os

from PIL import Image, ImageDraw, ImageFilter

# --- paths -----------------------------------------------------------------
UNPACKED = "/home/antomay/Projects/HoloCure_Unpacked/sprites"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "assets", "fullgame", "ui")

SCREEN_W, SCREEN_H = 320, 240

# Roster in display order: left->right along each row, top row first, with the
# logo sitting in the top-row gap (index 4..4 boundary -> after Marine, before Coco).
# Each entry: (folder-name under spr_Title_, room_x, room_y).
# Coordinates are the reference room's instance origins (bottom-centre).
ROSTER = [
    # --- row 1 (top): Pekora Noel Flare Marine  [LOGO]  Coco Kanata Towa Watame Luna
    ("Pekora", 58, 165), ("Noel", 98, 166), ("Flare", 141, 174), ("Marine", 178, 170),
    ("Coco", 436, 159), ("Kanata", 486, 165), ("Towa", 520, 160), ("Watame", 557, 158),
    ("Luna", 593, 156),
    # --- row 2: Kaela Kobo Zeta Anya Reine Ollie Iofi Moona Risu Aki Haato Shion Ayame
    ("Kaela", 54, 206), ("Kobo", 101, 205), ("Zeta", 144, 211), ("Anya", 195, 201),
    ("Reine", 240, 218), ("Ollie", 297, 219), ("Iofi", 346, 221), ("Moona", 388, 220),
    ("Risu", 428, 213), ("Aki", 469, 214), ("Haato", 514, 207), ("Shion", 555, 203),
    ("Ayame", 592, 196),
    # --- row 3: Korone Okayu Mio Fubuki AZKi Roboco Sora Suisei Miko Matsuri Mel Choco Subaru
    ("Korone", 76, 237), ("Okayu", 119, 238), ("Mio", 162, 245), ("Fubuki", 196, 249),
    ("Azki", 237, 255), ("Roboco", 279, 259), ("Sora", 317, 261), ("Suisei", 364, 257),
    ("Miko", 404, 253), ("Matsuri", 455, 244), ("Mel", 492, 248), ("Choco", 541, 249),
    ("Subaru", 583, 242),
    # --- row 4 (front): Kronii Sana IRyS Fauna Mumei Baelz Ame Gura Ina Kiara Calli Aqua
    ("Kronii", 78, 289), ("Sana", 122, 293), ("Irys", 161, 299), ("Fauna", 202, 302),
    ("Mumei", 246, 306), ("bae", 298, 301), ("Ame", 346, 305), ("Gura", 392, 301),
    ("Ina", 435, 298), ("Kiara", 486, 296), ("Calli", 528, 295), ("Aqua", 570, 293),
]

# Sprites carry their native size; the room is 640x360 and we render 320x240, so
# sprite art is halved (0.5) to match the reference's on-screen scale. Positions
# use the same 0.5 horizontally, and a slightly larger vertical factor so the four
# rows spread down to the icon bar (the room is 16:9, the screen is 4:3).
SPRITE_SCALE = 0.5
POS_X_SCALE = 0.5
POS_Y_SCALE = 0.588

# A few hand nudges (in screen px) applied after the room mapping, for members whose
# reference spot reads slightly off once halved. Index -> dx (negative moves left).
# Gura sits a touch right of her neighbours, so nudge her left a little.
MANUAL_DX = {42: -8}

LOGO_W = 118        # displayed logo width
LOGO_Y = 22         # logo top (kept in sync with TITLE_LOGO_Y in FullGameCanvas)


def ensure_out():
    os.makedirs(OUT, exist_ok=True)


def load_title_sprite(name):
    path = os.path.join(UNPACKED, "spr_Title_%s" % name, "spr_Title_%s_0.png" % name)
    return Image.open(path).convert("RGBA")


def bake_background():
    """Plain star-stage backdrop taken straight from the reference game (hud_new_title_BG),
    scaled to fill 320x240 and centre-cropped, so both the Splash and the Main Menu sit on the
    calm original stage instead of the procedural rainbow burst. No icon bar is baked in: the
    stage floor is already dark enough (mean luma ~41) to carry the white bar icons."""
    src = Image.open(os.path.join(UNPACKED, "hud_new_title_BG",
                                  "hud_new_title_BG_0.png")).convert("RGB")
    scale = max(SCREEN_W / float(src.size[0]), SCREEN_H / float(src.size[1]))
    nw, nh = int(round(src.size[0] * scale)), int(round(src.size[1] * scale))
    im = src.resize((nw, nh), Image.LANCZOS)
    left = (nw - SCREEN_W) // 2
    top = (nh - SCREEN_H) // 2
    base = im.crop((left, top, left + SCREEN_W, top + SCREEN_H)).convert("RGB")

    base.save(os.path.join(OUT, "title_bg.png"))


def bake_logo():
    """Scale the reference HoloCure wordmark down; also bake a shine frame."""
    src = Image.open(os.path.join(UNPACKED, "spr_HoloCureTitle_full",
                                  "spr_HoloCureTitle_full_0.png")).convert("RGBA")
    ratio = LOGO_W / src.size[0]
    logo = src.resize((LOGO_W, max(1, int(src.size[1] * ratio))), Image.LANCZOS)
    logo.save(os.path.join(OUT, "menu_logo.png"))

    # shine frame: a soft bright diagonal band swept across the logo, clipped to
    # the logo's own alpha so nothing spills into the background.
    shine = logo.copy()
    band = Image.new("L", logo.size, 0)
    db = ImageDraw.Draw(band)
    lw, lh = logo.size
    db.polygon([(int(lw * 0.30), 0), (int(lw * 0.46), 0),
                (int(lw * 0.30), lh), (int(lw * 0.14), lh)], fill=150)
    band = band.filter(ImageFilter.GaussianBlur(3))
    la = logo.split()[3]
    from PIL import ImageChops
    band = ImageChops.multiply(band, la)
    white = Image.new("RGBA", logo.size, (255, 255, 255, 255))
    shine = Image.composite(white, shine, band)
    shine.save(os.path.join(OUT, "menu_logo_shine.png"))
    return logo.size


def bake_chars():
    """Scale each roster sprite to its on-screen size, in display order."""
    sizes = []
    for i, (name, _rx, _ry) in enumerate(ROSTER):
        im = load_title_sprite(name)
        w = max(1, int(round(im.size[0] * SPRITE_SCALE)))
        h = max(1, int(round(im.size[1] * SPRITE_SCALE)))
        im = im.resize((w, h), Image.LANCZOS)
        im.save(os.path.join(OUT, "title_char_%d.png" % i))
        sizes.append((w, h))
    return sizes


def compute_positions(sizes):
    """Map each character's room origin (bottom-centre) to a screen top-left."""
    pos = []
    for i, ((name, rx, ry), (w, h)) in enumerate(zip(ROSTER, sizes)):
        left = int(round(rx * POS_X_SCALE - w / 2.0)) + MANUAL_DX.get(i, 0)
        feet = int(round(ry * POS_Y_SCALE))
        top = feet - h
        pos.append((left, top))
    return pos


def print_java_table(pos, sizes):
    print("    // Screen-space base positions (top-left) + sizes, baked by")
    print("    // tools/prepare_title_menu_assets.py. Index order == title_char_*.png order.")
    print("    private static final int[] CAST_X = {")
    row = "        "
    for i, (lx, _ty) in enumerate(pos):
        row += "%3d, " % lx
        if (i + 1) % 12 == 0:
            print(row.rstrip())
            row = "        "
    if row.strip():
        print(row.rstrip())
    print("    };")
    print("    private static final int[] CAST_Y = {")
    row = "        "
    for i, (_lx, ty) in enumerate(pos):
        row += "%3d, " % ty
        if (i + 1) % 12 == 0:
            print(row.rstrip())
            row = "        "
    if row.strip():
        print(row.rstrip())
    print("    };")


def main():
    ensure_out()
    bake_background()
    logo_size = bake_logo()
    sizes = bake_chars()
    pos = compute_positions(sizes)
    print("logo=%dx%d  chars=%d" % (logo_size[0], logo_size[1], len(sizes)))
    print_java_table(pos, sizes)


if __name__ == "__main__":
    main()
