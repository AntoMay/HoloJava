#!/usr/bin/env python3
"""
Generate the Choose Your Idol layout part sheet from the game's own constants.

The layout constants are parsed out of FullGameCanvas.java rather than restated
here, so this generator cannot silently drift away from the code it documents.
If a constant is missing or ambiguous the script fails loudly instead of drawing a
stale picture.

Output goes to docs/layout_parts/ and is overwritten on every run.
"""
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CANVAS = os.path.join(REPO, "src", "java-me-fullgame", "me", "holojava", "fullgame",
                      "FullGameCanvas.java")
OUT_DIR = os.path.join(REPO, "docs", "layout_parts")

W, H = 320, 240
BG = (117, 213, 247, 255)
ACCENT = (66, 191, 239, 255)
INK = (36, 108, 145, 255)
CLEAR = (0, 0, 0, 0)

# Character order, matching ContentCatalog.CHARACTERS.
CHARACTERS = ["Gura", "Ame", "Calli", "Kiara", "Ina"]

FONT_DIR = "/usr/share/fonts/truetype/dejavu"


def font(size, bold=False):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:
        return ImageFont.truetype(os.path.join(FONT_DIR, name), size)
    except OSError:
        return ImageFont.load_default()


def read_constants():
    """Pull the IDOL_* layout constants straight out of the game source."""
    with open(CANVAS) as fh:
        src = fh.read()

    ints = {}
    for name, value in re.findall(
            r"private\s+static\s+final\s+int\s+(IDOL_\w+)\s*=\s*(-?\d+)\s*;", src):
        ints[name] = int(value)

    arr = re.search(
        r"private\s+static\s+final\s+int\[\]\s+IDOL_SPRITE_DX\s*=\s*\{([^}]*)\}", src)
    if arr:
        ints["IDOL_SPRITE_DX"] = [int(v) for v in re.findall(r"-?\d+", arr.group(1))]

    required = ["IDOL_BOX_W", "IDOL_BOX_H", "IDOL_GRID_X", "IDOL_GRID_Y", "IDOL_GAP",
                "IDOL_PANEL_X", "IDOL_PANEL_Y", "IDOL_PANEL_W", "IDOL_PANEL_H",
                "IDOL_NAME_ROW_H", "IDOL_BODY_PAD_BOTTOM", "IDOL_LINE_H",
                "IDOL_TEXT_X", "IDOL_SPRITE_X", "IDOL_FEET_Y", "IDOL_SPRITE_DX"]
    missing = [k for k in required if k not in ints]
    if missing:
        sys.exit("could not read layout constants from FullGameCanvas.java: %s"
                 % ", ".join(missing))
    return ints


def status_lines(c):
    """Mirrors buildIdolDetailLines in FullGameCanvas. Values are illustrative."""
    return ["HP: %d" % c["hp"], "ATK: %d%%" % c["atk"], "SPD: %d%%" % c["spd"],
            "CRT: %d%%" % c["crt"], "Size: %d" % c["size"], "",
            "Weapon: %s" % c["weapon"], "Special: %s" % c["special"],
            "Skill: %s" % c["skill"], "", "Asset: %s" % c["group"], "(original art)"]


# Illustrative values mirroring ContentCatalog, one entry per playable character.
CATALOG = [
    dict(name="Gawr Gura", hp=65, atk=110, spd=140, crt=5, size=1,
         weapon="Trident Thrust", special="Shark Call", skill="Shark Bite", group="Gura"),
    dict(name="Amelia Watson", hp=75, atk=130, spd=135, crt=10, size=3,
         weapon="Tactical Gun", special="Slow Time", skill="Hunter's Instinct", group="Ame"),
    dict(name="Mori Calliope", hp=70, atk=115, spd=130, crt=10, size=4,
         weapon="Scythe Swing", special="The Reaper", skill="Undying", group="Calli"),
    dict(name="Takanashi Kiara", hp=90, atk=100, spd=140, crt=5, size=3,
         weapon="Phoenix Sword", special="Phoenix Fire", skill="Kagura", group="Kiara"),
    dict(name="Ninomae Ina'nis", hp=75, atk=90, spd=150, crt=1, size=1,
         weapon="Elf Scepter", special="Spirit of the Oni", skill="Constellation",
         group="Ina"),
]


written_parts = set()


def save(name, img):
    img.save(os.path.join(OUT_DIR, name))
    written_parts.add(name)
    print("  %s" % name)


def main():
    k = read_constants()
    os.makedirs(OUT_DIR, exist_ok=True)

    body_top = k["IDOL_PANEL_Y"] + k["IDOL_NAME_ROW_H"]
    body_h = k["IDOL_PANEL_H"] - k["IDOL_NAME_ROW_H"] - k["IDOL_BODY_PAD_BOTTOM"]
    visible = body_h // k["IDOL_LINE_H"]
    text_x = k["IDOL_PANEL_X"] + k["IDOL_TEXT_X"]
    text_w = k["IDOL_PANEL_W"] - k["IDOL_TEXT_X"] - 8
    sprite_x = k["IDOL_PANEL_X"] + k["IDOL_SPRITE_X"]
    feet_y = k["IDOL_PANEL_Y"] + k["IDOL_FEET_Y"]

    print("layout read from FullGameCanvas.java")
    print("  visible status rows : %d" % visible)
    print("  status column       : x=%d width=%d" % (text_x, text_w))
    print("  sprite              : x=%d, feet on row %d" % (sprite_x, feet_y))

    # 01 background ---------------------------------------------------------
    save("01_background.png", Image.new("RGBA", (W, H), BG))

    # 02 title --------------------------------------------------------------
    f_title = font(16, bold=True)
    d = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    tw = d.textlength("CHOOSE YOUR IDOL", font=f_title)
    img = Image.new("RGBA", (W, 40), CLEAR)
    ImageDraw.Draw(img).text(((W - tw) // 2, 8), "CHOOSE YOUR IDOL", font=f_title, fill=INK)
    save("02_title.png", img)

    # 03 selection boxes ----------------------------------------------------
    for i, _ in enumerate(CHARACTERS):
        img = Image.new("RGBA", (k["IDOL_BOX_W"], k["IDOL_BOX_H"]), CLEAR)
        dr = ImageDraw.Draw(img)
        dr.rectangle([0, 0, k["IDOL_BOX_W"] - 1, k["IDOL_BOX_H"] - 1],
                     outline=ACCENT, width=1)
        save("03_box_%s.png" % CHARACTERS[i], img)

    # 04 selection frame (the selected box's outer halo) --------------------
    img = Image.new("RGBA", (k["IDOL_BOX_W"] + 4, k["IDOL_BOX_H"] + 4), CLEAR)
    ImageDraw.Draw(img).rectangle(
        [0, 0, k["IDOL_BOX_W"] + 3, k["IDOL_BOX_H"] + 3], outline=ACCENT, width=1)
    save("04_selected_frame.png", img)

    # 05 detail panel border ------------------------------------------------
    img = Image.new("RGBA", (k["IDOL_PANEL_W"], k["IDOL_PANEL_H"]), CLEAR)
    ImageDraw.Draw(img).rectangle([0, 0, k["IDOL_PANEL_W"] - 1, k["IDOL_PANEL_H"] - 1],
                                  outline=ACCENT, width=1)
    save("05_panel_border.png", img)

    # 06 character name row -------------------------------------------------
    img = Image.new("RGBA", (k["IDOL_PANEL_W"] - 16, k["IDOL_NAME_ROW_H"]), CLEAR)
    ImageDraw.Draw(img).text((0, 6), CATALOG[0]["name"], font=font(10, bold=True), fill=INK)
    save("06_name_row.png", img)

    # 07 status rows --------------------------------------------------------
    for i, line in enumerate(status_lines(CATALOG[0])):
        img = Image.new("RGBA", (text_w, k["IDOL_LINE_H"]), CLEAR)
        if line:
            ImageDraw.Draw(img).text((0, 1), line, font=font(10), fill=INK)
        save("07_status_%02d.png" % i, img)

    # 08 scroll window ------------------------------------------------------
    img = Image.new("RGBA", (text_w, body_h), CLEAR)
    ImageDraw.Draw(img).rectangle([0, 0, text_w - 1, body_h - 1],
                                  outline=(66, 191, 239, 120), width=1)
    save("08_status_scroll_window.png", img)

    # 09 sprite ground line -------------------------------------------------
    img = Image.new("RGBA", (W, 6), CLEAR)
    ImageDraw.Draw(img).line([(0, 3), (W, 3)], fill=(255, 0, 0, 160), width=1)
    save("09_ground_line.png", img)

    # 99 layout guide -------------------------------------------------------
    img = Image.new("RGBA", (W, H), BG)
    dr = ImageDraw.Draw(img)
    for i in range(len(CHARACTERS)):
        bx = k["IDOL_GRID_X"] + i * (k["IDOL_BOX_W"] + k["IDOL_GAP"])
        dr.rectangle([bx, k["IDOL_GRID_Y"], bx + k["IDOL_BOX_W"] - 1,
                      k["IDOL_GRID_Y"] + k["IDOL_BOX_H"] - 1],
                     outline=(255, 0, 0, 200), width=1)
    dr.rectangle([k["IDOL_PANEL_X"], k["IDOL_PANEL_Y"],
                  k["IDOL_PANEL_X"] + k["IDOL_PANEL_W"] - 1,
                  k["IDOL_PANEL_Y"] + k["IDOL_PANEL_H"] - 1],
                 outline=(0, 160, 0, 200), width=1)
    dr.rectangle([text_x, body_top, text_x + text_w - 1, body_top + body_h - 1],
                 outline=(0, 0, 255, 200), width=1)
    dr.rectangle([0, body_top, sprite_x - 1, body_top + body_h - 1],
                 outline=(200, 0, 200, 160), width=1)
    dr.line([(0, feet_y), (W, feet_y)], fill=(255, 0, 0, 255), width=1)
    save("99_layout_guide.png", img)

    written = set(written_parts)
    removed = 0
    for name in sorted(os.listdir(OUT_DIR)):
        if name not in written:
            os.remove(os.path.join(OUT_DIR, name))
            print("  removed stale: %s" % name)
            removed += 1

    print("\nwrote %d parts to %s (%d stale removed)"
          % (len(written), OUT_DIR, removed))


if __name__ == "__main__":
    main()