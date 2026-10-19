# CHOOSE YOUR IDOL — Layout Specification

Canonical reference for the **Choose Your Idol** screen at **320×240**.

This document supersedes every earlier layout description. The values below are the
ones actually compiled into the game, not a proposal. When this document and
`FullGameCanvas.java` disagree, the code is the truth and this document is the bug.

- **Canonical code:** `src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java`
- **Layout constants:** the `IDOL_*` block near the top of that file
- **Draw code:** `drawChooseIdol(Graphics, int, int)`
- **Input code:** `updateChooseIdol(boolean, boolean, boolean, boolean, boolean)`
- **Canvas size:** 320×240 landscape

---

## 1. The `IDOL_*` constants

These are the single source of truth. Nothing in the layout is written inline.

| Constant | Value | Meaning |
| --- | ---: | --- |
| `IDOL_COLS` | 5 | Characters in the grid |
| `IDOL_ROWS` | 1 | Grid rows |
| `IDOL_BOX_W` | 48 | Selection box width |
| `IDOL_BOX_H` | 56 | Selection box height |
| `IDOL_GRID_X` | 24 | Grid left edge — `(320 − (5×48 + 4×8)) / 2` |
| `IDOL_GRID_Y` | 49 | Grid top edge |
| `IDOL_GAP` | 8 | Gap between boxes |
| `IDOL_PANEL_X` | 12 | Detail panel left edge |
| `IDOL_PANEL_Y` | 114 | Detail panel top edge |
| `IDOL_PANEL_W` | 296 | `320 − 2×12`, so the right margin matches the left |
| `IDOL_PANEL_H` | 114 | `240 − 114 − 12`, so the bottom margin matches the sides |
| `IDOL_NAME_ROW_H` | 18 | Height of the full-width character-name row |
| `IDOL_BODY_PAD_BOTTOM` | 6 | Clearance between the last status row and the border |
| `IDOL_LINE_H` | 12 | Status line pitch |
| `IDOL_TEXT_X` | 124 | Status column offset from the panel edge (`12 + 124 = 136` absolute) |
| `IDOL_SPRITE_X` | 28 | Sprite left edge from the panel edge (`12 + 28 = 40` absolute) |
| `IDOL_FEET_Y` | 76 | Ground line, measured down from the panel top (`114 + 76 = 190` absolute) |
| `IDOL_SPRITE_DX` | `{0,0,−12,0,0}` | Per-character horizontal correction |
| `IDOL_ANIM_FPS` | 8 | Idle animation rate |

---

## 2. Vertical budget

The screen is divided so the outer margins are equal on all four sides.

```
y=0    ┌──────────────────────────────────────┐
       │  Title band                         │
y=8    │  "CHOOSE YOUR IDOL"  (SIZE_LARGE)   │
       │                                      │
y=40   ├──────────────────────────────────────┤
       │                                      │  9px
y=49   │  Grid: 5 boxes, 48×56 each          │
       │                                      │
y=105  │                                      │
       │                                      │  9px
y=114  ├──────────────────────────────────────┤
       │  Detail panel, 296×114              │
       │    y=114  name row      (18px)      │
       │    y=132  body                     │
       │                                      │
y=228  │                                      │
       └──────────────────────────────────────┘
y=240    12px margin below the panel
```

`IDOL_GRID_Y = 49` gives a 9px gap under the title band (ends at y=40) and a 9px
gap above the panel (starts at y=114). The grid is deliberately symmetric.

`IDOL_PANEL_H = 114` was reduced from 126 so the panel's bottom margin is 12px —
the same value as its left and right margins. That equality is the intent, not an
accident.

---

## 3. The selection grid

Five boxes in one row, portrait only. No names under the boxes, no Back button.

| Box | Left edge | Right edge |
| ---: | ---: | ---: |
| Gura (index 0) | 24 | 71 |
| Ame (index 1) | 80 | 127 |
| Calli (index 2) | 136 | 183 |
| Kiara (index 3) | 192 | 239 |
| Ina (index 4) | 248 | 295 |

Box *i* starts at `24 + i × 56`.

- **Unselected:** 1px border at `(bx, by)` sized `47×55`.
- **Selected:** the same 1px stroke drawn one pixel outside, at `(bx−2, by−2)`
  sized `51×59`. The selection reads as an outer halo, not a thicker line.
- **Portrait:** centred in the box on both axes.

Character order is fixed and has been unchanged since the grid was first built:
**Gura, Ame, Calli, Kiara, Ina**.

---

## 4. The detail panel

A single 1px border, drawn at `(px, py)` sized `295×113`.

The border is one pixel. It was a double border before and was deliberately
thinned so the panel and the selection boxes read as the same kind of frame.

### 4.1 Name row

The character's name gets a full-width row of its own:

- Drawn at `(px + 8, py + 6)` — that is **(20, 120)** absolute.
- `STYLE_BOLD`, `SIZE_SMALL`.
- The row is `IDOL_NAME_ROW_H = 18` tall, so the status column below it starts
  clear instead of overprinting the name.

The name is drawn exactly once. An earlier version drew it twice and left a
ghost on top of itself.

### 4.2 Body

Everything below the name row:

```
bodyTop = py + IDOL_NAME_ROW_H   = 132
bodyH   = IDOL_PANEL_H - IDOL_NAME_ROW_H - IDOL_BODY_PAD_BOTTOM = 114 - 18 - 6 = 90
```

### 4.3 Status column

- **Left edge:** `px + IDOL_TEXT_X` = **(136)** absolute.
- **Width:** `pw − IDOL_TEXT_X − padSide` = `296 − 124 − 8` = **164**.
- **Clip:** `(textX, bodyTop, textW, bodyH)`, i.e. `(136, 132, 164, 90)`.

The 136 is not arbitrary. It was measured off the rendered title glyphs:

```
S (of CHOOSE)  ends at x = 134.7
E (of CHOOSE)  starts at x = 137.0
                       midpoint = 135.85  →  136
```

So the status block hangs directly under the heading, at the point where the
word CHOOSE breaks. The column's x is a fixed constant and does **not** depend on
sprite width, so animating the sprite can never shift the text.

### 4.4 The status list

Built by `buildIdolDetailLines(CharacterDef)`, twelve rows:

| # | Row | Source |
| ---: | --- | --- |
| 0 | `HP: …` | `c.hp` |
| 1 | `ATK: …%` | `c.atkPercent` |
| 2 | `SPD: …%` | `c.spdPercent` |
| 3 | `CRT: …%` | `c.critPercent` |
| 4 | `Size: …` | `c.size` |
| 5 | *(blank)* | separator |
| 6 | `Weapon: …` | weapon name for `c.mainWeapon` |
| 7 | `Special: …` | `c.special` |
| 8 | `Skill: …` | `c.skill` |
| 9 | *(blank)* | separator |
| 10 | `Asset: …` | `c.assetGroup` |
| 11 | `(original art)` / `(fallback art)` | `c.fallbackArt` |

**Visible rows:** `(IDOL_PANEL_H − IDOL_NAME_ROW_H − IDOL_BODY_PAD_BOTTOM) / IDOL_LINE_H`
= `90 / 12` = **7**.

The 12px pitch is what lets seven rows fit under the name without shrinking the
panel. At the old 14px pitch only six rows showed, which pushed `Weapon` and
everything after it off screen and made the gap under `Size` look like two blank
lines.

**Scroll** (`idolDetailScroll`), Up/Down:

```
maxScroll = max(0, listLength − visibleRows)
```

`Down` stops at `maxScroll`, so the last row on screen is always
`(original art)` and the list can never be scrolled into blank space below itself.
`Up` stops at 0. Changing character with Left/Right resets the scroll to 0.

Both bounds are computed by helpers (`idolVisibleLines()`, `idolMaxScroll()`) that
the draw code also uses, so the input layer and the renderer cannot disagree about
where the list ends.

### 4.5 Over-long rows

The column is pinned to 136 and cannot grow without breaking the title alignment,
which leaves 164px. The longest status value in the current cast is Ina's
`Special: Spirit of the Oni` at roughly 183px.

Rather than let the clip cut a word in half, `ellipsize(Font, String, int)` trims
any row that does not fit and appends `...`.

---

## 5. The idle sprite

### 5.1 Ground line

Every idle sprite in the asset set has its feet on the **last row of its own
canvas** (verified: bottom opaque row == canvas height − 1 for all 18 frames).
Aligning the canvas bottoms therefore aligns the feet exactly.

```
drawY = py + IDOL_FEET_Y − (spriteHeight − 1)
      = 114 + 76 − (h − 1)
```

Every character's feet land on **row 190** of the screen.

This replaced vertical centring, which was wrong twice over: the five sheets range
from 27px to 47px tall so everyone stood at a different height, and the frames
within a single loop differ by a pixel or two, so each character shuffled in place
on every animation tick.

### 5.2 Horizontal

```
drawX = px + IDOL_SPRITE_X + IDOL_SPRITE_DX[character]
```

`IDOL_SPRITE_X = 28` puts the sprite's left edge at **x=40**.

Calli is the only character with a correction. Her canvas is the widest of the
five (40–42px) because her scythe is drawn out to one side, so with a shared left
edge she stands noticeably further right than everyone else. `IDOL_SPRITE_DX`
gives her `−12`; the other four are `0` because their art is already centred on
its own canvas.

### 5.3 Animation

- 8 FPS, cycling `% 4` over the character's idle frames.
- **Only the sprite moves.** The status text, the name, the panel and the boxes
  are all redrawn at identical coordinates every frame, so nothing else shifts
  while the sprite animates.

---

## 6. Input

`updateChooseIdol(up, down, fire, left, right)`:

| Key | Action |
| --- | --- |
| Left | Previous character; at index 0, returns to the main menu |
| Right | Next character, wrapping |
| Up | Scroll the status list up, stops at 0 |
| Down | Scroll the status list down, stops at `maxScroll` |
| FIRE | Confirm — proceeds to Choose Mode |

Left and Right reset `idolDetailScroll` to 0 so a newly selected character always
opens its panel at the top.

---

## 7. Change history

The screen went through several layout passes. This is the final one; earlier
values are listed only to explain why the current ones look the way they do.

| Commit | Change |
| --- | --- |
| `1330bb7` | Grid lowered for proportional gaps; removed duplicate name draw |
| `2aa38ab` | Name given its own row; added scroll-clamp test |
| `5fe5ada` | Title raised; panel border thinned to 1px; status column widened |
| `076b54c` | Status column pushed further right; panel given a bottom gap |
| `9d18da5` | `IDOL_PANEL_H` 126 → 114 for a real 12px bottom margin |
| `a73c8a4` | Column to `px+80`; 12px line pitch; scroll clamped at the last row; fixed a double-added canvas origin and a clip that never widened back |
| `4258d17` | Column anchored to the title's E\|Y gap at x=146; `ellipsize()` added |
| `e3865a4` | Column to the middle of the E at x=140 |
| `0b3066d` | Column to the S\|E midpoint at x=136 |
| `1de89fc` | Sprites bottom-aligned to one ground line, Calli's feet as the datum |
| `5d78507` | Ground line raised to row 190; Calli pulled 12px left |

---

## 8. Bugs found and fixed along the way

Recorded because each one is invisible at 320×240 and would have bitten on real
hardware.

1. **Double-added canvas origin.** The sprite and the text clip added `x`/`y` to
   `px`/`py`, which already contained them. At exactly 320×240 the origin is 0, so
   this looked fine in the emulator and would have drawn everything off-position on
   any other screen size.

2. **Clip never restored.** MIDP's `clipRect` intersects rather than replaces, so
   the "reset" at the end of the draw never actually reset anything. Now widened
   explicitly, with a comment saying why.

3. **Name drawn twice.** Left a ghost of the character name on top of itself.

4. **Unbounded scroll.** `Down` incremented forever, scrolling into empty space.

5. **Stale `ph` comment.** Read `126` when the constant had been 114 for several
   commits.

---

## 9. Regenerating the layout parts

`docs/layout_parts/` is generated, not hand-drawn:

```sh
python3 docs/generate_layout_parts.py
```

The generator reads the layout constants out of `FullGameCanvas.java` rather than
hard-coding them, so it cannot drift from the game. See
[`CHOOSE_IDOL_LAYOUT_PARTS.md`](CHOOSE_IDOL_LAYOUT_PARTS.md) for what each part file
contains.