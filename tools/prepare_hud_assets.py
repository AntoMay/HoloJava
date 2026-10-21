#!/usr/bin/env python3
"""Extended HUD asset preparation - extracts all useful UI assets from unpacked data.

Run: tools/prepare_hud_assets.py [--source /path/to/HoloCure_Unpacked]
"""

import argparse
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SOURCE = Path.home() / "Projects" / "HoloCure_Unpacked"
UI = ROOT / "assets" / "fullgame" / "ui"
HUD = UI / "hud"

def load_trimmed(path: Path) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    bbox = im.getbbox()
    return im.crop(bbox) if bbox else im

def save_scaled(im: Image.Image, target_w: int, out_path: Path):
    """Scale image proportionally to target width."""
    scale = target_w / im.width
    target_h = max(1, int(im.height * scale))
    im.resize((target_w, target_h), Image.LANCZOS).save(out_path)

def extract_expbar_anim(source: Path):
    """Extract 90-frame EXP bar animation, scaled to 76px wide (our EXP_BAR_W)."""
    print("Extracting EXP bar animation (90 frames)...")
    anim_dir = HUD / "expbar_anim"
    anim_dir.mkdir(parents=True, exist_ok=True)
    
    for i in range(90):
        src = source / "textures" / "Sprites" / f"hud_expbar_anim_{i}.png"
        if not src.exists():
            print(f"  WARNING: missing frame {i}")
            continue
        im = Image.open(src).convert("RGBA")
        # Source is 648x26, scale to 76px wide (our EXP_BAR_W)
        target_w = 76
        scale = target_w / im.width
        target_h = max(1, int(im.height * scale))
        im.resize((target_w, target_h), Image.LANCZOS).save(anim_dir / f"expbar_anim_{i:02d}.png")
    print(f"  Saved {len(list(anim_dir.glob('*.png')))} frames to {anim_dir}")

def extract_expbar_static(source: Path):
    """Extract static EXP bar components."""
    print("Extracting static EXP bar components...")
    
    # Background
    bg = Image.open(source / "textures" / "Sprites" / "hud_expbar_bg_0.png").convert("RGBA")
    save_scaled(bg, 76, HUD / "expbar_bg.png")
    
    # Frame
    frame = Image.open(source / "textures" / "Sprites" / "hud_expbar_frame_0.png").convert("RGBA")
    save_scaled(frame, 76, HUD / "expbar_frame.png")
    
    # Shine (if exists)
    shine_src = source / "textures" / "Sprites" / "hud_expbar_shine_0.png"
    if shine_src.exists():
        shine = Image.open(shine_src).convert("RGBA")
        save_scaled(shine, 76, HUD / "expbar_shine.png")
    
    print("  Saved expbar_bg.png, expbar_frame.png")

def extract_sp_bar(source: Path):
    """Extract SP bar components."""
    print("Extracting SP bar components...")
    
    # Background
    bg = Image.open(source / "textures" / "Sprites" / "ui_SP_Bar_bg_0.png").convert("RGBA")
    # Source 69x11, our SPECIAL_BAR_W = 66 (HP_BAR_W)
    save_scaled(bg, 66, HUD / "spbar_bg.png")
    
    # Fill (normal)
    fill = Image.open(source / "textures" / "Sprites" / "ui_SP_Bar_fill_0.png").convert("RGBA")
    save_scaled(fill, 66, HUD / "spbar_fill.png")
    
    # Fill rainbow
    fill_rainbow = Image.open(source / "textures" / "Sprites" / "ui_SP_Bar_fill_rainbow_0.png").convert("RGBA")
    save_scaled(fill_rainbow, 66, HUD / "spbar_fill_rainbow.png")
    
    # Frame
    frame = Image.open(source / "textures" / "Sprites" / "ui_SP_Bar_frame_0.png").convert("RGBA")
    save_scaled(frame, 66, HUD / "spbar_frame.png")
    
    print("  Saved spbar_bg.png, spbar_fill.png, spbar_fill_rainbow.png, spbar_frame.png")

def extract_digit_fonts(source: Path):
    """Extract digit fonts (white, yellow, pink)."""
    print("Extracting digit fonts...")
    
    for color in ["white", "yellow", "pink"]:
        color_dir = HUD / f"digits_{color}"
        color_dir.mkdir(parents=True, exist_ok=True)
        
        for i in range(10):
            src = source / "textures" / "Sprites" / f"ui_digit_{color}_{i}.png"
            if src.exists():
                im = Image.open(src).convert("RGBA")
                # Scale to reasonable size (our font rendering uses ~7px height)
                # Source is 5x7, keep as-is or slightly larger
                im.save(color_dir / f"digit_{i}.png")
        print(f"  Saved {color} digits (10)")

def extract_hp_icons(source: Path):
    """Extract HP bar icons."""
    print("Extracting HP icons...")
    
    # Green HP bar
    green = Image.open(source / "textures" / "Sprites" / "hud_HUD_HP_green_0.png").convert("RGBA")
    save_scaled(green, 66, HUD / "hp_green.png")
    
    green1 = Image.open(source / "textures" / "Sprites" / "hud_HUD_HP_green_1.png").convert("RGBA")
    save_scaled(green1, 66, HUD / "hp_green_1.png")
    
    # Purple HP bar (low HP)
    purple = Image.open(source / "textures" / "Sprites" / "hud_HUD_HP_purple_0.png").convert("RGBA")
    save_scaled(purple, 66, HUD / "hp_purple.png")
    
    # HP icon
    hp_icon = Image.open(source / "textures" / "Sprites" / "hud_HPicon_0.png").convert("RGBA")
    hp_icon.save(HUD / "hp_icon.png")
    
    print("  Saved hp_green.png, hp_green_1.png, hp_purple.png, hp_icon.png")

def extract_weapon_case_levels(source: Path):
    """Extract weapon case level icons (0-4)."""
    print("Extracting weapon case level icons...")
    
    for i in range(5):
        src = source / "textures" / "Sprites" / f"hud_weapon_case_levels_{i}.png"
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(HUD / f"weapon_case_level_{i}.png")
    
    # Main weapon case levels
    for i in range(5):
        src = source / "textures" / "Sprites" / f"hud_mainweapon_case_levels_{i}.png"
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(HUD / f"mainweapon_case_level_{i}.png")
    
    print("  Saved weapon/mainweapon case level icons (0-4)")

def extract_button_icons(source: Path):
    """Extract keybind and controller button icons."""
    print("Extracting button icons...")
    
    icon_dir = HUD / "button_icons"
    icon_dir.mkdir(parents=True, exist_ok=True)
    
    # Keybind icons (0-15)
    for i in range(16):
        src = source / "textures" / "Sprites" / f"hud_keybindIcons_{i}.png"
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(icon_dir / f"keybind_{i}.png")
    
    # Controller button icons (0-9)
    for i in range(10):
        src = source / "textures" / "Sprites" / f"hud_controllerButtonIcons_{i}.png"
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(icon_dir / f"controller_{i}.png")
    
    print("  Saved button icons")

def extract_misc_ui(source: Path):
    """Extract other useful UI elements."""
    print("Extracting misc UI...")
    
    # Menu backgrounds
    for name in ["menu_charselecBG_0.png", "menu_first_0.png", "hud_optionsmenu_0.png", "hud_pausemenu_0.png"]:
        src = source / "textures" / "Sprites" / name
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(HUD / name)
    
    # Shop UI
    for i in range(3):
        src = source / "textures" / "Sprites" / f"hud_shopCategories_{i}.png"
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(HUD / f"shop_category_{i}.png")
    
    for i in range(4):
        src = source / "textures" / "Sprites" / f"hud_shopButtons_{i}.png"
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(HUD / f"shop_button_{i}.png")
    
    # Upgrade window
    for name in ["ui_menu_upgrade_window_0.png", "ui_menu_upgrade_window_selected_0.png", "ui_menu_upgrade_window_selected_1.png"]:
        src = source / "textures" / "Sprites" / name
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(HUD / name)
    
    # Dialogue boxes
    for name in ["hud_dialogueBox_0.png", "ui_dialogueBox_0.png"]:
        src = source / "textures" / "Sprites" / name
        if src.exists():
            im = Image.open(src).convert("RGBA")
            im.save(HUD / name)
    
    print("  Saved misc UI elements")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    if not args.source.is_dir():
        print("source tree missing: %s" % args.source, file=sys.stderr)
        return 1

    HUD.mkdir(parents=True, exist_ok=True)
    
    extract_expbar_anim(args.source)
    extract_expbar_static(args.source)
    extract_sp_bar(args.source)
    extract_digit_fonts(args.source)
    extract_hp_icons(args.source)
    extract_weapon_case_levels(args.source)
    extract_button_icons(args.source)
    extract_misc_ui(args.source)
    
    # List all extracted
    print("\nExtracted assets:")
    for path in sorted(HUD.rglob("*.png")):
        im = Image.open(path)
        print(f"  {path.relative_to(ROOT)} {im.size} {im.mode}")
    
    return 0

if __name__ == "__main__":
    sys.exit(main())