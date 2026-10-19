# Choose Your Idol — Layout Parts

Companion to [`CHOOSE_IDOL_LAYOUT.md`](CHOOSE_IDOL_LAYOUT.md). That document
specifies the layout; this one explains the generated images in
`docs/layout_parts/`.

## Regenerating

```sh
python3 docs/generate_layout_parts.py
```

The script parses the `IDOL_*` constants out of
`src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java` and derives
everything else from them. It fails with a message if a constant cannot be found,
and it deletes any file in the output directory it did not write, so the part set
can never accumulate stale images from an older layout.

Nothing here is hand-drawn or hand-positioned. The numbers in the guide image are
the compiled ones.

## What each part is

| File | Size | Contents |
| --- | --- | --- |
| `01_background.png` | 320×240 | The solid `#75D5F7` background |
| `02_title.png` | 320×40 | "CHOOSE YOUR IDOL" band, `SIZE_LARGE` bold |
| `03_box_<Name>.png` | 48×56 | One selection box, 1px border, per character |
| `04_selected_frame.png` | 52×60 | The selected box's outer halo, drawn 2px outside |
| `05_panel_border.png` | 296×114 | The detail panel's single 1px border |
| `06_name_row.png` | 280×18 | The character's own name row across the panel top |
| `07_status_NN.png` | 164×12 | One status row, 00–11, at 12px pitch |
| `08_status_scroll_window.png` | 164×90 | The clipped window the status list scrolls inside |
| `09_ground_line.png` | 320×6 | A 1px marker on row 190, the sprites' shared foot line |
| `99_layout_guide.png` | 320×240 | The whole screen with every region outlined |

`07_status_05.png` and `07_status_09.png` are the blank separator rows and are
intentionally empty.

## Reading the guide

`99_layout_guide.png` overlays the layout on the background:

| Colour | Region |
| --- | --- |
| **Red** boxes | The five selection boxes, at `24 + i × 56` |
| **Green** box | The detail panel, `(12, 114)` sized 296×114 |
| **Blue** box | The status column, `(136, 132)` sized 164×90 |
| **Magenta** box | The sprite zone, left of the status column |
| **Red** line | The sprite ground line, row 190 |

## A note on the characters

The selection boxes and the name row carry no portraits or sprites — those are
loaded at runtime from `assets/fullgame/`. The parts therefore show frames and
labels only, so the sheet documents geometry rather than art.

The one thing the sheet cannot show is why `IDOL_SPRITE_DX` exists: Calli's idle
canvas is 40–42px wide because her scythe is drawn out to one side, which is why
she alone carries a `−12` correction. See §5.2 of the layout document.

## Superseded files

The following were removed because they described a layout the game no longer
uses — a double panel border, a 126px panel running to the bottom of the screen, a
stats column at x=70, and a scroll indicator that is no longer drawn:

- `docs/choose_idol_layout_reference.py`
- `docs/choose_idol_layout_reference_v2.py`
- the previous contents of `docs/layout_parts/`

If those scripts are still referenced anywhere, point them at
`docs/generate_layout_parts.py` instead.