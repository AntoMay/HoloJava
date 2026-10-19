#!/usr/bin/env python3
"""
Generate separate transparent PNG files for each Choose Idol layout element.
Only background is solid; all UI elements have transparent backgrounds.
"""
from PIL import Image, ImageDraw, ImageFont
import os

OUTPUT_DIR = "/home/antomay/Projects/HoloJava/docs/layout_parts"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Colors
BG_BLUE = (117, 213, 247, 255)        # #75D5F7
ACCENT_BLUE = (66, 191, 239, 255)     # #42BFEF
INK_DARK = (36, 108, 145, 255)        # #246C91
TRANSPARENT = (0, 0, 0, 0)

W, H = 320, 240

def load_fonts():
    try:
        return {
            'title': ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16),
            'name': ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 10),
            'stat': ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10),
            'small': ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 8),
            'tiny': ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 7),
        }
    except:
        return {k: ImageFont.load_default() for k in ['title','name','stat','small','tiny']}

fonts = load_fonts()

def save_transparent(name, img):
    path = os.path.join(OUTPUT_DIR, name)
    img.save(path)
    print(f"  Saved: {name}")

# ============================================================
# 1. BACKGROUND (solid) - 320x240
# ============================================================
bg = Image.new('RGBA', (320, 240), (117, 213, 247, 255))
save_transparent("01_background.png", bg)

# ============================================================
# 2. TITLE "CHOOSE YOUR IDOL" - transparent BG
# ============================================================
title_img = Image.new('RGBA', (320, 40), TRANSPARENT)
draw = ImageDraw.Draw(title_img)
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
text = "CHOOSE YOUR IDOL"
tw = draw.textlength("CHOOSE YOUR IDOL", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16))
tx = (320 - tw) // 2
draw.text((tx, 12), "CHOOSE YOUR IDOL", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16), fill=(36, 108, 145, 255))
save_transparent("02_title.png", title_img)

# ============================================================
# 3. CHARACTER BOXES (5 separate boxes) - 48x56 each
# ============================================================
for i, name in enumerate(["Gura", "Ame", "Calli", "Kiara", "Ina"]):
    box = Image.new('RGBA', (48, 56), TRANSPARENT)
    draw = ImageDraw.Draw(box)
    # Border
    draw.rectangle([0, 0, 47, 55], outline=(66, 191, 239, 255), width=2)
    # Portrait placeholder (centered)
    draw.rectangle([6, 4, 41, 51], outline=(66, 191, 239, 200), width=1)
    # Name label at bottom
    fn = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 7)
    tw = draw.textlength(name, font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 7))
    draw.text(((48 - draw.textlength(name, font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 7))) // 2, 44), name, font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 7), fill=(36, 108, 145, 180))
    save_transparent(f"03_box_{name}.png", box)

# ============================================================
# 4. CHARACTER PORTRAITS (5 separate) - transparent BG
# ============================================================
# These are placeholders - in real game, load from assets
portrait_names = ["Gura", "Ame", "Calli", "Kiara", "Ina"]
for name in portrait_names:
    img = Image.new('RGBA', (36, 48), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 35, 47], outline=(66, 191, 239, 200), width=1)
    draw.text((2, 20), "PORTRAIT", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 6), fill=(66, 191, 239, 200))
    save_transparent(f"04_portrait_{name}.png", img)

# ============================================================
# 5. DETAIL PANEL BOX (296x126) - transparent BG, border only
# ============================================================
panel = Image.new('RGBA', (296, 126), TRANSPARENT)
draw = ImageDraw.Draw(panel)
# Double border
draw.rectangle([0, 0, 295, 125], outline=(66, 191, 239, 255), width=2)
draw.rectangle([1, 1, 294, 124], outline=(66, 191, 239, 150), width=1)
save_transparent("05_panel_box.png", panel)

# ============================================================
# 5. CHARACTER NAME TEXT (transparent BG)
# ============================================================
name_img = Image.new('RGBA', (280, 14), TRANSPARENT)
draw = ImageDraw.Draw(name_img)
draw.text((0, 0), "Gawr Gura", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 10), fill=(36, 108, 145, 255))
save_transparent("05_character_name.png", name_img)

# ============================================================
# 6. IDLE SPRITE (40x48) - transparent BG
# ============================================================
sprite = Image.new('RGBA', (40, 48), TRANSPARENT)
draw = ImageDraw.Draw(sprite)
draw.rectangle([0, 0, 39, 47], outline=(66, 191, 239, 200), width=1)
draw.text((2, 20), "SPRITE", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 6), fill=(66, 191, 239, 200))
save_transparent("06_idle_sprite.png", sprite)

# ============================================================
# 7. STATUS TEXT LINES (each line separate)
# ============================================================
stats_lines = [
    "HP: 65",
    "ATK: 110%",
    "SPD: 140%",
    "CRT: 5%",
    "Size: 1",
    "",
    "Weapon: Trident Thrust",
    "Special: Shark Call",
    "Skill: Shark Bite",
]

for i, line in enumerate(stats_lines):
    if line == "":
        img = Image.new('RGBA', (250, 14), TRANSPARENT)
    else:
        # Estimate width
        draw = ImageDraw.Draw(Image.new('RGBA', (1,1)))
        tw = draw.textlength(line, font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10))
        img = Image.new('RGBA', (int(tw)+4, 14), TRANSPARENT)
        draw = ImageDraw.Draw(img)
        draw.text((2, 0), line, font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10), fill=(36, 108, 145, 255))
    save_transparent(f"07_stat_{i:02d}.png", img)

# ============================================================
# 6. SCROLL INDICATORS
# ============================================================
scroll_img = Image.new('RGBA', (30, 14), TRANSPARENT)
draw = ImageDraw.Draw(scroll_img)
draw.text((0, 0), "▲ ▼", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10), fill=(66, 191, 239, 200))
save_transparent("08_scroll_indicator.png", scroll_img)

# ============================================================
# 7. GRID LAYOUT GUIDE (for reference)
# ============================================================
guide = Image.new('RGBA', (320, 240), (0,0,0,0))
draw = ImageDraw.Draw(guide)
# Grid positions
for i in range(5):
    bx = 24 + i * 56
    draw.rectangle([bx, 42, bx+48, 98], outline=(255, 0, 0, 100), width=1)
# Panel area
draw.rectangle([12, 114, 307, 239], outline=(0, 255, 0, 100), width=1)
# Stats area
draw.rectangle([70, 122, 296, 232], outline=(0, 0, 255, 100), width=1)
save_transparent("99_layout_guide.png", guide)

print(f"\nAll parts saved to {OUTPUT_DIR}/")
print("Files created:")
for f in sorted(os.listdir(OUTPUT_DIR)):
    print(f"  {f}")