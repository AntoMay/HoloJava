#!/usr/bin/env python3
"""Builds the Arena Hutan Sawit map art from the project's reference image.

The reference is a single composed 3:2 scene: a burning palm border around an open clearing with
a pond, fallen logs and rocks. It is not a tileable pattern, so it cannot be used as the repeating
stage tile the engine would otherwise draw. This script therefore produces two things:

  assets/fullgame/stages/palm_forest.png
      The whole arena at exactly the 320x240 playfield size, for preview and for any future
      single-image background. Written for reference; the runtime does not load it.

  assets/fullgame/stages/palm_forest_parts/palm_forest_<row><col>.png
      The same arena cut into a 4x4 grid of 80x60 parts, row-major from the top left. The stage
      draws each part once at its grid position instead of repeating one tile, which is what
      reproduces the reference composition on screen.

Cropping, not scaling, is what adapts the 3:2 reference to the 4:3 playfield: squashing it would
bend every trunk and flame. The crop is taken slightly above centre so the burning canopy stays in
frame, since it carries most of the scene's identity.

Usage: tools/generate_palm_forest_map.py [reference-image]
Default reference: ~/Downloads/fc28e118-8ae5-471f-9314-1ba5af43325c.png
"""

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_REFERENCE = os.path.expanduser(
    "~/Downloads/fc28e118-8ae5-471f-9314-1ba5af43325c.png")

MAP_W, MAP_H = 320, 240
GRID_COLS, GRID_ROWS = 4, 4
PART_W = MAP_W // GRID_COLS      # 80
PART_H = MAP_H // GRID_ROWS      # 60
VERTICAL_BIAS = 0.42              # crop this far down the leftover height, 0 = top, 1 = bottom


def crop_to_aspect(src, target_ratio):
    """Crops src to target_ratio (w/h) about a slightly high centre line."""
    w, h = src.size
    if w / float(h) > target_ratio:            # source is wider: trim the sides
        new_w = int(round(h * target_ratio))
        left = (w - new_w) // 2
        return src.crop((left, 0, left + new_w, h))
    new_h = int(round(w / target_ratio))        # source is taller: trim top/bottom
    top = int((h - new_h) * VERTICAL_BIAS)
    return src.crop((0, top, w, top + new_h))


def main(argv):
    reference = argv[0] if argv else DEFAULT_REFERENCE
    if not os.path.isfile(reference):
        sys.stderr.write(
            "reference image not found: %s\n"
            "pass it as the first argument, e.g.\n"
            "  tools/generate_palm_forest_map.py /path/to/reference.png\n" % reference)
        return 2

    src = Image.open(reference).convert("RGB")
    if src.size != (1536, 1024):
        sys.stderr.write("warning: reference is %s, expected (1536, 1024)\n" % (src.size,))
    crop = crop_to_aspect(src, float(MAP_W) / MAP_H)
    arena = crop.resize((MAP_W, MAP_H), Image.LANCZOS)

    stages = os.path.join(ROOT, "assets/fullgame/stages")
    os.makedirs(stages, exist_ok=True)
    arena_path = os.path.join(stages, "palm_forest.png")
    arena.save(arena_path, optimize=True)

    parts_dir = os.path.join(stages, "palm_forest_parts")
    if not os.path.isdir(parts_dir):
        os.makedirs(parts_dir)
    for stale in os.listdir(parts_dir):
        if stale.endswith(".png"):
            os.remove(os.path.join(parts_dir, stale))

    written = []
    for row in range(GRID_ROWS):
        for col in range(GRID_COLS):
            box = (col * PART_W, row * PART_H, (col + 1) * PART_W, (row + 1) * PART_H)
            path = os.path.join(parts_dir, "palm_forest_%d%d.png" % (row, col))
            arena.crop(box).save(path, optimize=True)
            written.append(path)

    rel = lambda p: os.path.relpath(p, ROOT)
    print("source   %s %s" % (rel(reference), src.size))
    print("cropped  %s" % (crop.size,))
    print("arena    %-52s %dx%d  %d bytes" % (rel(arena_path), MAP_W, MAP_H,
                                              os.path.getsize(arena_path)))
    total = sum(os.path.getsize(p) for p in written)
    for path in written:
        print("part     %-52s %dx%d" % (rel(path), PART_W, PART_H))
    print("parts    %d x %d grid of %dx%d, %d bytes total"
          % (GRID_ROWS, GRID_COLS, PART_W, PART_H, total))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))