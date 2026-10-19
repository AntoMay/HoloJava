#!/usr/bin/env python3
"""
Generate Choose Idol layout reference PNG for documentation.
320x240 layout per ASCII spec.
Background: solid (not transparent)
All other elements: transparent background
"""
from PIL import Image, ImageDraw, ImageFont
import os

# Output path
OUTPUT_PATH = "/home/antomay/Projects/HoloJava/docs/choose_idol_layout_reference.png"

# Colors (RGBA)
BG_BLUE = (117, 213, 247, 255)        # #75D5F7 - background blue
PANEL_LIGHT = (168, 231, 252, 255)    # #A8E7FC - light panel
ACCENT_BLUE = (66, 191, 239, 255)     # #42BFEF - accent/border
INK_DARK = (36, 108, 145, 255)        # #246C91 - text dark
TRANSPARENT = (0, 0, 0, 0)            # fully transparent
WHITE = (255, 255, 255, 255)

# Canvas size
W, H = 320, 240

def create_layout_image():
    # Create base image with solid background
    img = Image.new('RGBA', (W, H), BG_BLUE)
    draw = ImageDraw.Draw(img)

    # Try to load a font
    try:
        font_large = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
        font_small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10)
        font_tiny = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 8)
    except:
        font_large = ImageFont.load_default()
        font_small = ImageFont.load_default()
        font_tiny = ImageFont.load_default()

    # ============================================================
    # LAYER 1: BLUE BACKGROUND BAND (Y=0-40)
    # ============================================================
    draw.rectangle([0, 0, 320, 40], fill=BG_BLUE)

    # ============================================================
    # LAYER 2: TITLE "CHOOSE YOUR IDOL" - centered in blue band
    # ============================================================
    title_text = "CHOOSE YOUR IDOL"
    # Use a temporary image for text to get transparency
    title_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
    title_w = draw.textlength(title_text, font=title_font)
    title_x = (320 - title_w) // 2
    title_y = 12  # centered vertically in 40px band (40-16)/2 = 12
    draw.text((title_x, title_y), title_text, font=title_font, fill=INK_DARK)

    # ============================================================
    # LAYER 3: GRID OF 5 CHARACTER BOXES (Y=42-98)
    # ============================================================
    box_w, box_h = 48, 56
    gap = 8
    start_x = 24  # centered: (320 - (5*48 + 4*8)) / 2 = 24
    start_y = 42  # Y=42

    for i in range(5):
        bx = start_x + i * (48 + 8)
        by = 42
        # Draw light blue border box (transparent fill)
        # Box border
        draw.rectangle([bx, by, bx + 48, by + 56], outline=ACCENT_BLUE, width=2)
        # Placeholder for character portrait (transparent)
        # Draw a placeholder rectangle for portrait area
        portrait_x = bx + (48 - 36) // 2
        portrait_y = by + (56 - 48) // 2
        draw.rectangle([portrait_x, portrait_y, portrait_x + 36, portrait_y + 48],
                       outline=ACCENT_BLUE, width=1, fill=None)

    # ============================================================
    # LAYER 4: DETAIL PANEL (Y=114-240)
    # ============================================================
    panel_x, panel_y = 12, 114
    panel_w, panel_h = 296, 126

    # Panel double border
    draw.rectangle([12, 114, 307, 239], outline=ACCENT_BLUE, width=2)
    draw.rectangle([13, 115, 306, 238], outline=ACCENT_BLUE, width=1)

    # Character name at top-left
    draw.text((20, 122), "Gawr Gura", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 10), fill=INK_DARK)

    # Sprite placeholder (left side, vertically centered in text area)
    # Text area: Y=132 to Y=232 (py+8 to py+ph-8 = 114+8 to 114+126-8)
    text_area_top = 132
    text_area_bottom = 232
    text_area_center = text_area_top + (text_area_bottom - text_area_top) // 2

    sprite_w, sprite_h = 40, 48  # typical sprite size
    sprite_x = 20  # px + 8
    sprite_y = 132 + (100 - 48) // 2  # centered in 132-232 area

    # Sprite placeholder (transparent with border)
    draw.rectangle([20, 154 - 24, 60, 154 + 24], outline=ACCENT_BLUE, width=1)

    # Character name at left, above sprite area
    draw.text((20, 122), "Gawr Gura", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 10), fill=INK_DARK)

    # Stats on the right of sprite (fixed position)
    stats_x = 20 + 40 + 8  # 68px from panel left
    stats_y_start = 130
    line_h = 14

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
        draw.text((70, y), line, font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10), fill=INK_DARK)

    # Scroll indicators
    draw.text((280, 116), "▲ ▼", font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 10), fill=ACCENT_BLUE)

    return img

def main():
    img = create_layout_image()
    img.save(OUTPUT_PATH)
    print(f"Saved to {OUTPUT_PATH}")
    print(f"Size: {img.size}")
    print(f"Mode: {img.mode}")

if __name__ == "__main__":
    main()