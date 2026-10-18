#!/usr/bin/env python3
"""Turns the three Hutan Sawit character images into game-ready sprite frames.

Sources (checked into the repo so the build is reproducible):

  assets-source/hutan_sawit/wowo.jpeg      the podium guardian -> bosses/WoWo (4x 64x64)
  assets-source/hutan_sawit/fufufafa.jpeg  the purple flame head -> bosses/FufuFafa (4 frames)
  assets-source/hutan_sawit/kucing19.jpeg  the missing-poster guy -> enemies/Kucing19 (3 frames)

Each source is a large pixel-art render. MIDP 2.0 cannot scale images at runtime, so
the pipeline is: downscale to a small working resolution, flood the flat background
(the two-tone checkerboard on the boss images, plain white on the enemy image) to
transparency from the borders only, tight-crop to the silhouette, box-filter down to
the display size, and bake the animation as bob offsets across the frames the loader
expects (bosses 4, enemies 3). WoWo keeps the 64x64 canvas and the spr_WoWo_ naming
it has always had, so the catalog and RunWorldBossArtTest stay as they are.

Run: tools/prepare_hutan_sawit_cast.py
"""

import sys
from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "assets-source" / "hutan_sawit"
BOSSES = ROOT / "assets" / "fullgame" / "bosses"
ENEMIES = ROOT / "assets" / "fullgame" / "enemies"

WORK_LONG_SIDE = 384          # working resolution for the background flood
BOB = ((0, -1, 0, 1), (0, -2, 0, 2), (0, -1, 0))


def flood_remove_background(im: Image.Image, bright_min: int, sat_max: int) -> Image.Image:
    """Clears flat background pixels connected to the border, keeping interior light areas."""
    im = im.convert("RGBA")
    w, h = im.size
    px = list(im.getdata())
    bg_like = [
        (max(p[:3]) - min(p[:3]) <= sat_max) and (sum(p[:3]) / 3 >= bright_min)
        for p in px
    ]
    seen = bytearray(w * h)
    queue = deque()

    def seed(x, y):
        i = y * w + x
        if bg_like[i] and not seen[i]:
            seen[i] = 1
            queue.append(i)

    for x in range(w):
        seed(x, 0)
        seed(x, h - 1)
    for y in range(h):
        seed(0, y)
        seed(w - 1, y)

    while queue:
        i = queue.popleft()
        x, y = i % w, i // w
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h:
                j = ny * w + nx
                if bg_like[j] and not seen[j]:
                    seen[j] = 1
                    queue.append(j)

    out = []
    for i, p in enumerate(px):
        out.append((0, 0, 0, 0) if seen[i] else p)
    result = Image.new("RGBA", (w, h))
    result.putdata(out)
    return result


def prepare(path: Path, bright_min: int, sat_max: int) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    scale = WORK_LONG_SIDE / max(im.size)
    if scale < 1:
        im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.LANCZOS)
    cleared = flood_remove_background(im, bright_min, sat_max)
    bbox = cleared.getbbox()
    return cleared.crop(bbox) if bbox else cleared


def fit_height(im: Image.Image, target_h: int, max_w: int) -> Image.Image:
    scale = min(target_h / im.height, max_w / im.width)
    return im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.BOX)


def write_frames(directory: Path, prefix: str, frames) -> tuple:
    directory.mkdir(parents=True, exist_ok=True)
    for index, frame in enumerate(frames):
        frame.save(directory / ("%s%d.png" % (prefix, index)))
    return frames[0].size


def stack_frames(base: Image.Image, pad_top: int, pad_bottom: int, offsets) -> list:
    """Bakes bob offsets into fixed-size frames; the catalog hitbox is the frame size."""
    w = base.width
    h = base.height + pad_top + pad_bottom
    frames = []
    for dy in offsets:
        canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        canvas.alpha_composite(base, (0, max(pad_top, pad_top + dy)))
        frames.append(canvas)
    return frames


def main() -> int:
    for name in ("wowo.jpeg", "fufufafa.jpeg", "kucing19.jpeg"):
        if not (SRC / name).is_file():
            print("missing source image: %s" % (SRC / name), file=sys.stderr)
            return 1

    # ---- WoWo: keeps its historical 64x64 canvas and spr_WoWo_ naming.
    wowo = fit_height(prepare(SRC / "wowo.jpeg", 165, 28), 60, 56)
    canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    canvas.alpha_composite(wowo, ((64 - wowo.width) // 2, 64 - wowo.height))
    size = write_frames(BOSSES / "WoWo", "spr_WoWo_", stack_frames(canvas, 0, 0, BOB[0]))
    print("bosses/WoWo  %dx%d x4" % size)

    # ---- FufuFafa: a floating head, so the frames bob harder and the canvas pads for it.
    # Its checkerboard is the dark variant (grays ~113/~173), hence the lower brightness gate.
    fff = fit_height(prepare(SRC / "fufufafa.jpeg", 98, 30), 52, 64)
    size = write_frames(BOSSES / "FufuFafa", "spr_FufuFafa_", stack_frames(fff, 3, 1, BOB[1]))
    print("bosses/FufuFafa  %dx%d x4 (set collW/collH to this)" % size)

    # ---- 19 Kucing: a walking fan, gentle sway, 3 frames like every other enemy family.
    kucing = fit_height(prepare(SRC / "kucing19.jpeg", 226, 14), 30, 24)
    size = write_frames(ENEMIES / "Kucing19", "spr_Kucing19_", stack_frames(kucing, 1, 1, BOB[2]))
    print("enemies/Kucing19  %dx%d x3" % size)
    return 0


if __name__ == "__main__":
    sys.exit(main())
