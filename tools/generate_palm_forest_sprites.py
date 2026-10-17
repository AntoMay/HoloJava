#!/usr/bin/env python3
"""Generates the Arena Hutan Sawit sprite set.

Original pixel art, drawn from the project's own reference sheets rather than
from extracted HoloCure files, so the new stage ships art that is clean to
redistribute inside this repository.

Outputs
    assets/fullgame/enemies/BijiSawit/spr_BijiSawit_<n>.png   n = 0..2
    assets/fullgame/bosses/WoWo/spr_WoWo_<n>.png               n = 0..3

Sizes are pinned to what the catalog declares: the enemy collision box is
18x16 (Biji Sawit) and the boss box is 42x42 (WoWo), and the loader counts
3 enemy frames and 4 boss frames.
"""

import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Palette taken from the reference sheets: scorched orange-red oil-palm fruit,
# layered greens for the fronds, warm tan skin, brown husk and bark.
FROND_DARK = (26, 62, 30, 255)
FROND_MID = (46, 100, 38, 255)
FROND_LIT = (86, 148, 52, 255)
FROND_PALE = (140, 186, 72, 255)
FRUIT_DARK = (150, 38, 18, 255)
FRUIT_MID = (204, 62, 26, 255)
FRUIT_LIT = (240, 108, 38, 255)
FRUIT_GLOW = (252, 172, 62, 255)
SKIN_DARK = (188, 138, 96, 255)
SKIN_MID = (226, 176, 130, 255)
SKIN_LIT = (246, 212, 172, 255)
HUSK_DARK = (72, 48, 30, 255)
HUSK_MID = (110, 76, 44, 255)
HUSK_LIT = (150, 108, 66, 255)
BARK_DARK = (58, 38, 24, 255)
BARK_MID = (92, 62, 38, 255)
BARK_LIT = (128, 90, 56, 255)
SHADOW = (26, 18, 12, 110)
EYE = (28, 22, 18, 255)
EYE_GLOW = (255, 214, 96, 255)
CLEAR = (0, 0, 0, 0)


def canvas(w, h):
    return Image.new("RGBA", (w, h), CLEAR)


def put(img, x, y, color):
    """Writes one pixel, ignoring out-of-range coordinates silently."""
    w, h = img.size
    if 0 <= x < w and 0 <= y < h and color[3] != 0:
        img.putpixel((x, y), color)


def ellipse(img, cx, cy, rx, ry, color):
    for y in range(int(cy - ry), int(cy + ry) + 2):
        for x in range(int(cx - rx), int(cx + rx) + 2):
            dx = (x - cx) / float(rx if rx else 1)
            dy = (y - cy) / float(ry if ry else 1)
            if dx * dx + dy * dy <= 1.0:
                put(img, x, y, color)


def ellipse_ring(img, cx, cy, rx, ry, color, thickness=1.0):
    """Shaded disc: lit toward the upper left, dark toward the lower right."""
    for y in range(int(cy - ry), int(cy + ry) + 2):
        for x in range(int(cx - rx), int(cx + rx) + 2):
            dx = (x - cx) / float(rx if rx else 1)
            dy = (y - cy) / float(ry if ry else 1)
            d = dx * dx + dy * dy
            if d > 1.0:
                continue
            lit = (-dx - dy) / 2.0
            edge = (d ** 0.5)
            if lit > 0.34 - edge * thickness:
                c = color[3]
                base = color[:3]
                lit_c = tuple(min(255, int(v + (255 - v) * 0.30)) for v in base) + (c,)
                put(img, x, y, lit_c)
            elif lit < -0.30 + edge * thickness:
                base = color[:3]
                dark_c = tuple(int(v * 0.68) for v in base) + (color[3],)
                put(img, x, y, dark_c)
            else:
                put(img, x, y, color)


def frond(img, x0, y0, length, angle, mirror=False, bright=False, thick=2, leaflets=True):
    """Draws one palm frond as a tapered spine with angled leaflets."""
    mid = FROND_MID if bright else FROND_DARK
    lit = FROND_PALE if bright else FROND_LIT
    dx, dy = math.cos(angle), math.sin(angle)
    steps = max(2, int(length))
    for i in range(steps):
        t = i / float(steps)
        x = x0 + dx * i
        y = y0 + dy * i
        half = max(1, int(round(thick * (1.0 - t * 0.55))))
        for j in range(-half, half + 1):
            put(img, int(round(x - dy * j)), int(round(y + dx * j)), lit if j > 0 else mid)
    if not leaflets:
        return
    # Leaflets fan out along the spine, sweeping toward the frond tip.
    for i in range(2, steps, 2):
        t = i / float(steps)
        bx = x0 + dx * i
        by = y0 + dy * i
        sweep = 1.00 * (1.0 - t) + 0.28
        ll = 3 + int(5 * (1.0 - abs(t - 0.35)))
        for sign in (-1, 1):
            la = angle + sign * sweep
            ldx, ldy = math.cos(la), math.sin(la)
            for k in range(ll):
                c = FROND_MID if bright else FROND_DARK
                if k >= ll - 2:
                    c = lit if bright else FROND_MID
                put(img, int(round(bx + ldx * k)), int(round(by + ldy * k)), c)


def fruit_cluster(img, cx, cy, r, count):
    """Palm-fruit bunch: overlapping discs graded dark to lit, densely packed."""
    import random as _r
    rnd = _r.Random(int(cx) * 31 + int(cy) * 7 + count)
    palette = [FRUIT_DARK, FRUIT_MID, FRUIT_MID, FRUIT_LIT, FRUIT_LIT, FRUIT_GLOW]
    # Pack on jittered rings so the bunch reads as one solid mass, not a ring of dots.
    for i in range(count):
        a = (i / float(count)) * math.pi * 2 + rnd.random() * 0.7
        rad = r * (0.10 + 0.80 * (rnd.random() ** 0.6))
        x = cx + math.cos(a) * rad
        y = cy + math.sin(a) * rad * 0.90
        rr = r * (0.52 + 0.18 * rnd.random())
        ellipse(img, x, y, rr, rr, palette[i % len(palette)])
        # Top-left highlight on each fruit.
        ellipse(img, x - rr * 0.28, y - rr * 0.30, rr * 0.34, rr * 0.30, FRUIT_GLOW)


def leaf_plate(img, cx, cy, w, h, color, dark):
    """Overlapping downward-pointing husk plate: the WoWo torso texture."""
    for y in range(int(cy - h), int(cy + h) + 1):
        t = (y - (cy - h)) / float(2 * h)
        half = w * (1.0 - t) * 0.9
        for x in range(int(cx - half), int(cx + half) + 1):
            edge = 1.0 - abs(x - cx) / float(max(half, 1))
            c = dark if (edge < 0.22 or y > cy + h * 0.72) else color
            put(img, x, y, c)
    # Ridge highlight down the middle of the plate.
    for y in range(int(cy - h * 0.8), int(cy + h * 0.5)):
        put(img, int(cx), y, color)


def ground_shadow(img, cx, cy, rx, ry):
    ellipse(img, cx, cy, rx, ry, SHADOW)


def eyes(img, cx, cy, gap, size=1, glow=False):
    """Two eyes; glow draws the iris highlight used on the boss."""
    for sign in (-1, 1):
        ex = cx + sign * gap
        ellipse(img, ex, cy, size * 0.9, size, EYE)
        if glow:
            put(img, int(round(ex)), int(round(cy - size * 0.4)), EYE_GLOW)


# ---------------------------------------------------------------------------
# Biji Sawit: the palm-seed minion. Small chibi body, frond crown, fruit on the
# shoulders. Three frames: a two-step walk bob plus a slightly wider stance.
# ---------------------------------------------------------------------------

def biji_sawit(frame):
    W, H = 24, 26
    img = canvas(W, H)

    # Walk bob: frame 1 is the raised step, frame 2 passes the low point.
    bob = [0, -1, 0][frame]
    lean = [0, 0, 1][frame]
    cx = 12 + lean

    ground_shadow(img, 12, 24, 7, 2)

    # Feet first, so the body overlaps them.
    left_foot = [7, 6, 8][frame]
    right_foot = [16, 17, 15][frame]
    ellipse(img, left_foot, 22 + bob, 3, 2, HUSK_DARK)
    ellipse(img, right_foot, 22 + bob, 3, 2, HUSK_DARK)

    # Husk body: a short seed shell, narrower than the head so the silhouette
    # reads head-heavy the way the reference minion does.
    ellipse_ring(img, cx, 18 + bob, 6, 5, HUSK_MID)
    for y in range(15 + bob, 22 + bob):
        put(img, cx, y, HUSK_DARK)
    for x in range(cx - 5, cx + 6):
        put(img, x, 14 + bob, HUSK_LIT)

    # Head, sitting proud of the shoulders.
    ellipse_ring(img, cx, 9 + bob, 6, 5, SKIN_MID)

    # Fruit bunches tucked at both temples.
    fruit_cluster(img, cx - 7, 8 + bob, 2, 5)
    fruit_cluster(img, cx + 7, 8 + bob, 2, 5)

    # Frond crown: an upward fan kept inside the canvas so no frond is clipped.
    crown = (cx, 7 + bob)
    for a, bright in [(math.pi * 1.10, False), (math.pi * 1.24, True),
                      (math.pi * 1.38, False), (math.pi * 1.50, True),
                      (math.pi * 1.62, False), (math.pi * 1.76, True),
                      (math.pi * 1.90, False)]:
        frond(img, crown[0], crown[1], 7, a, bright=bright, thick=2)

    # Face.
    eyes(img, cx, 9 + bob, 2, 1)
    # Grin: a run of mouth pixels with two dimples.
    for x in range(cx - 3, cx + 4):
        put(img, x, 12 + bob, SKIN_DARK)
    put(img, cx - 4, 12 + bob, SKIN_DARK)
    put(img, cx + 4, 12 + bob, SKIN_DARK)
    put(img, cx - 2, 13 + bob, SKIN_DARK)
    put(img, cx + 2, 13 + bob, SKIN_DARK)

    return img


# ---------------------------------------------------------------------------
# WoWo: the palm guardian boss. Broad frond mantle, heavy fruit pauldrons, bark
# legs. Four frames: a slow sway of the mantle and shoulders.
# ---------------------------------------------------------------------------

def wowo(frame):
    W, H = 64, 64
    img = canvas(W, H)

    # Sway: the mantle and arms drift, the legs stay planted.
    sway = [0, 1, 0, -1][frame]
    arm_lift = [0, -1, -2, -1][frame]

    ground_shadow(img, 32, 61, 17, 4)

    # Root legs, wide and planted.
    ellipse_ring(img, 21, 51, 9, 10, BARK_MID)
    ellipse_ring(img, 43, 51, 9, 10, BARK_MID)
    for bx in (14, 19, 24, 40, 45, 50):
        ellipse(img, bx, 60, 2, 2, BARK_DARK)

    # Torso: overlapping husk plates, widest at the shoulders, narrowing at the waist.
    ellipse_ring(img, 32, 42, 18, 14, BARK_MID)
    for row, (cy, w, h) in enumerate([(34, 16, 4), (39, 17, 4), (44, 16, 4), (49, 14, 4)]):
        for col, dx in enumerate((-8, 0, 8)):
            leaf_plate(img, 32 + dx + (4 if row % 2 else 0) + sway * 0, cy, w / 2.0, h,
                       BARK_LIT if (row + col) % 2 == 0 else BARK_MID, BARK_DARK)
    # Chest core: a bright plate at the sternum so the torso has a focal point.
    leaf_plate(img, 32, 36, 5, 4, (150, 108, 66, 255), BARK_DARK)

    # Head set into the shoulders.
    ellipse_ring(img, 32, 26 + arm_lift, 11, 10, SKIN_MID)
    # Jaw shading so the head reads separate from the neck.
    for x in range(24, 41):
        put(img, x, 34 + arm_lift, SKIN_DARK)

    # Frond mantle: a wide, dense fan behind and above the head.
    crown = (32, 21 + arm_lift)
    for a, ln, bright, th in [
            (math.pi * 1.00, 28, False, 3), (math.pi * 1.11, 30, True, 3),
            (math.pi * 1.22, 29, False, 3), (math.pi * 1.33, 31, True, 3),
            (math.pi * 1.44, 30, False, 3), (math.pi * 1.56, 32, True, 3),
            (math.pi * 1.67, 30, False, 3), (math.pi * 1.78, 31, True, 3),
            (math.pi * 1.89, 29, False, 3), (math.pi * 2.00, 28, True, 3)]:
        frond(img, crown[0], crown[1], ln, a, bright=bright, thick=th)
    # Side fronds, angled down past the shoulders.
    for sign in (-1, 1):
        angle = 0.32 if sign > 0 else math.pi - 0.32
        frond(img, 32 + sign * 11, 25 + arm_lift, 20, angle, bright=True, thick=3)

    # Fruit pauldrons, one large bunch per shoulder.
    fruit_cluster(img, 15 + sway, 31 + arm_lift, 5, 12)
    fruit_cluster(img, 49 + sway, 31 + arm_lift, 5, 12)

    # Arms: thick bark forearms ending in open claw hands.
    for sign in (-1, 1):
        ax = 32 + sign * (19 + sway)
        ay = 45 + arm_lift
        ellipse_ring(img, ax, ay, 6, 9, HUSK_MID)
        for k in range(3):
            fx = ax + sign * (1 + k * 2)
            fy = ay + 8 + (1 if k == 1 else 0)
            ellipse(img, fx, fy, 2, 3, HUSK_DARK)
            put(img, fx + sign, fy + 1, HUSK_LIT)

    # Face: heavy brows, glowing eyes, a wide confident grin.
    brow_y = 22 + arm_lift
    for sign in (-1, 1):
        for x in range(32 + sign * 4, 32 + sign * 11):
            put(img, x, brow_y - abs(x - (32 + sign * 7)) // 3, HUSK_DARK)
    eyes(img, 32, 27 + arm_lift, 5, 2, glow=True)
    # Grin across the lower face.
    for x in range(25, 40):
        arc = int(3 - abs(x - 32) // 4)
        for y in range(31 + arc, 34 + arc):
            put(img, x, y, SKIN_DARK)
        put(img, x, 34 + arc, SKIN_DARK)
    for x in range(27, 38):
        if x % 2 == 0:
            put(img, x, 33 + int(3 - abs(x - 32) // 4), SKIN_LIT)

    return img


def write(img, path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full, optimize=True)
    return full


def main():
    written = []
    for f in range(3):
        written.append(write(biji_sawit(f),
                             "assets/fullgame/enemies/BijiSawit/spr_BijiSawit_%d.png" % f))
    for f in range(4):
        written.append(write(wowo(f),
                             "assets/fullgame/bosses/WoWo/spr_WoWo_%d.png" % f))

    for path in written:
        img = Image.open(path)
        print("%-56s %dx%d" % (os.path.relpath(path, ROOT), img.size[0], img.size[1]))


if __name__ == "__main__":
    main()