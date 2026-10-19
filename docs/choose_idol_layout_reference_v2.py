#!/usr/bin/env python3
"""
Generate Choose Idol layout reference PNG for documentation.
320x240 layout per ASCII spec.
Background: solid (not transparent)
UI elements: drawn with visible styling for reference
"""
from PIL import Image, ImageDraw, ImageFont
import os

OUTPUT_PATH = "/home/antomay/Projects/HoloJava/docs/choose_idol_layout_reference.png"

# Colors (RGBA)
BG_BLUE = (117, 213, 247, 255)        # #75D5F7 - background blue
ACCENT_BLUE = (66, 191, 239, 255)     # #42BFEF - accent/border
INK_DARK = (36, 108, 145, 255)        # #246C91 - text dark
WHITE = (255, 255, 255, 255)

W, H = 320, 240

def create_layout_image():
    img = Image.new('RGBA', (W, H), BG_BLUE)
    draw = ImageDraw.Draw(img)

    # Fonts
    try:
        font_large = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
        font_medium = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 12)
        font_normal = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10)
        font_small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 8)
    except:
        font_large = ImageFont.load_default()
        font_normal = ImageFont.load_default()
        font_small = ImageFont.load_default()

    # ============================================================
    # BACKGROUND: Solid blue (Y=0-240)
    # ============================================================
    # Already set by Image.new()

    # ============================================================
    # HEADER BAND (Y=0-40): Solid blue
    # ============================================================
    # Already set by Image.new()

    # TITLE: "CHOOSE YOUR IDOL" centered in blue band (Y=0-40)
    title_text = "CHOOSE YOUR IDOL"
    title_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
    title_w = draw.textlength("CHOOSE YOUR IDOL", font=title_font)
    title_x = (320 - title_w) // 2
    title_y = 12  # centered in 40px band
    draw.text((title_x, 12), "CHOOSE YOUR IDOL", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16), fill=(36, 108, 145, 255))

    # ============================================================
    # GRID: 5 CHARACTER BOXES (Y=42-98)
    # ============================================================
    box_w, box_h = 48, 56
    gap = 8
    start_x = 24  # centered
    start_y = 42

    for i in range(5):
        bx = 24 + i * (48 + 8)
        by = 42
        # Box border (light blue)
        draw.rectangle([24 + i*56, 42, 24 + i*56 + 48, 98], outline=(66, 191, 239, 255), width=2)
        # Portrait placeholder
        px = 24 + i*56 + 6
        py = 46
        draw.rectangle([px, py, px+36, py+48], outline=(66, 191, 239, 200), width=1)
        # Character label (small)
        names = ["Gura", "Ame", "Calli", "Kiara", "Ina"]
        label_w = draw.textlength(names[i], font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 7))
        lx = 24 + i*56 + (48 - label_w) // 2
        draw.text((lx, 100), names[i], font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 7), fill=(36, 108, 145, 180))

    # ============================================================
    # DIVIDER (removed - no divider line)
    # ============================================================

    # ============================================================
    # DETAIL PANEL (Y=114-240)
    # ============================================================
    panel_x, panel_y = 12, 114
    panel_w, panel_h = 296, 126

    # Panel double border
    draw.rectangle([12, 114, 307, 239], outline=(66, 191, 239, 255), width=2)
    draw.rectangle([13, 115, 306, 238], outline=(66, 191, 239, 150), width=1)

    # Character name at top-left
    draw.text((20, 122), "Gawr Gura", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 10), fill=(36, 108, 145, 255))

    # Sprite placeholder (left side, vertically centered)
    draw.rectangle([20, 154-24, 60, 154+24], outline=(66, 191, 239, 200), width=1)
    draw.text((26, 150), "SPRITE", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 7), fill=(66, 191, 239, 200))

    # Stats on the right (fixed position, NOT affected by sprite)
    stats_x = 70
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
        y = 130 + i * 14
        draw.text((70, y), line, font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10), fill=(36, 108, 145, 255))

    # Scroll indicators
    draw.text((280, 116), "▲ ▼", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10), fill=(66, 191, 239, 200))

    # Layout guides (optional - thin lines showing structure)
    # Vertical center line
    # draw.line([160, 0, 160, 240], fill=(255, 255, 255, 50), width=1)
    # Horizontal guides
    # draw.line([0, 40, 320, 40], fill=(255, 255, 255, 50), width=1)
    # draw.line([0, 108, 320, 108], fill=(255, 255, 255, 50), width=1)
    # draw.line([0, 114, 320, 114], fill=(255, 255, 255, 50), width=1)

    return img

def main():
    img = create_layout_image()
    img.save("/home/antomay/Projects/HoloJava/docs/choose_idol_layout_reference.png")
    print(f"Saved to /home/antomay/Projects/HoloJava/docs/choose_idol_layout_reference.png")
    print(f"Size: {img.size}, Mode: {img.mode}")

if __name__ == "__main__":
    main()