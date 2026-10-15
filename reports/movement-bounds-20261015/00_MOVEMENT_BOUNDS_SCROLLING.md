# Player Movement, Arena Bounds, and Horizontal Scrolling

**Date:** 2026-10-15
**Change group:** `updatePlayer`, clamping, and the camera/scroll interaction
**Outcome:** Parity confirmed, verified, committed locally. **No production code changed.**

---

## 1. Evidence: `updatePlayer` against the reference bytecode

| Aspect | Reference bytecode | This build | |
|---|---|---|---|
| Base speed | `120.0 * character.spdPercent / 100.0` | identical | ✓ |
| Speed bonus | `speed *= 1.0 + speedPercentBonus / 100.0` | identical | ✓ |
| Slow immunity | `if (now < slowEnemiesUntil) { }` — player unaffected | identical | ✓ |
| LEFT | `iconst_4` | `GameCanvas.LEFT_PRESSED` = 4 | ✓ |
| RIGHT | `bipush 32` | `GameCanvas.RIGHT_PRESSED` = 32 | ✓ |
| UP | `iconst_2` | `GameCanvas.UP_PRESSED` = 2 | ✓ |
| DOWN | `bipush 64` | `GameCanvas.DOWN_PRESSED` = 64 | ✓ |
| Diagonal | `Math.sqrt(dx² + dy²)`, then `(dx/len) * speed * 0.04` | identical | ✓ |
| Facing | set from `dx`, then `dx == 0 → 0` when moving; same for `dy` | identical | ✓ |
| Lower/left bound | `px < 0 → 0`, `py < 0 → 0` | identical | ✓ |
| Upper bound | `px > 296 → 296`, `py > 216 → 216` | `worldWidth - 24`, `worldHeight - 24` | decomposition |

The only difference is the upper bound. 296 and 216 are `320 - 24` and `240 - 24`; this build writes
the same arithmetic against the world dimensions. **At the reference's own 320×240 world the two
produce identical numbers**, which test 3 confirms by walking to the edge and observing exactly 296
and 216.

**A false alarm worth recording.** The input masks first looked mismatched — the reference reads 32
for right where `RIGHT_PRESSED` is commonly 8. The cause was checking the wrong class: the project
imports `javax.microedition.lcdui.game.GameCanvas`, whose constants are UP 2, DOWN 64, LEFT 4,
RIGHT 32, exactly matching the bytecode. `javax.microedition.lcdui.Canvas` holds key *codes*, not
masks. No defect existed; recording it so it is not re-investigated.

---

## 2. Can Stage 2 walk the character out of the viewport?

**No — and that is a deliberate adaptation, not inherited behaviour.**

The reference has no scrolling at all: its world is fixed at 320×240 and the clamp keeps the player
inside it by construction. Stage 2 sets `scrollW = 2`, so the world becomes `viewWidth * 2` and the
player may legitimately stand at world x up to `worldWidth - 24`, which is past the viewport.

What keeps them visible is the camera:

```java
private int cameraX() {
    int travel = worldWidth - viewWidth;
    if (travel <= 0) return 0;
    int target = playerCenterX() - viewWidth / 2;
    if (target < 0) return 0;
    if (target > travel) return travel;
    return target;
}
```

Clamped to `[0, worldWidth - viewWidth]`. Test 5 sweeps **every reachable position** on Stage 2 at
320×240 and confirms none projects outside the viewport; test 6 confirms the camera never leaves its
legal range and rests at 0 and `worldWidth - viewWidth` at the two ends.

Clamping the player to the *viewport* instead of the world would have been the other defensible
choice, but it would leave two thirds of the scrolling stage unreachable, so it was not adopted.

---

## 3. Scrolling is rendering-only

`cameraX()` is a pure function of the player position — no state, no accumulation. Test 7 confirms
that idle frames leave the player untouched and the camera unchanged. Draw code derives
`worldOffX = offX - cameraX` once and adds it to entity coordinates for rendering only; collision,
attacks, and the HUD all read world coordinates directly.

Stage 1 does not scroll, its world equals the viewport, and test 3 confirms the bounds land exactly
on the reference's 296 and 216.

---

## 4. Tests

`tools/tests/me/holojava/fullgame/RunWorldMovementBoundsTest.java` — 35 checks, driving the real
`updatePlayer`, `setViewport`, and `cameraX`.

| # | Test |
|---|---|
| 1 | the input masks match the reference |
| 2 | straight and diagonal movement follow the reference formula |
| 3 | a non-scrolling stage clamps exactly like the reference (296 / 216 / 0) |
| 4 | the player cannot leave the world bounds, across four stage and screen combinations |
| 5 | a scrolling stage keeps the player on screen at every reachable position |
| 6 | the camera never reveals past the world, and never moves on a flat stage |
| 7 | scrolling does not disturb gameplay coordinates |

### Negative controls — all nine detected

| Control | Mutation | Result |
|---|---|---|
| M1 | right clamp dropped | FAIL, 11 |
| M2 | right clamp uses viewport width instead of world width | FAIL, 5 |
| M3 | left clamp dropped | FAIL, 5 |
| M4 | bottom clamp dropped | FAIL, 2 |
| M5 | camera no longer clamped at the world end | FAIL, 2 |
| M6 | camera no longer clamped at the world start | FAIL, 2 |
| M7 | camera stops following the player | FAIL, 4 |
| M8 | diagonal normalisation dropped (corner speed-up) | FAIL, 2 |
| M9 | scrolling world width ignored | FAIL, 3 |

One of my own assertions was wrong initially: the on-screen sweep ran to `worldWidth`, which is not a
reachable position because `updatePlayer` clamps to `worldWidth - 24` every frame. The sweep now
covers the reachable domain, and test 4 proves that clamp by walking there for real rather than
asserting it.

---

## 5. Limitations

- Rendering offsets are verified structurally (the camera is pure and only `draw` consumes
  `worldOffX`), not by executing `draw`, which needs a MIDP `Graphics`.
- Touch/key mapping from screen coordinates into world coordinates is not covered.
- Larger viewports are only sampled at 320×240 and 640×240; a screen smaller than 320×240 uses the
  centred-crop path in `Viewport` and is not exercised here.
- No emulator or handset run was performed.

---

## 6. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldMovementBoundsTest.java   (new, 35 checks)
```

No production source changed; the JAR is byte-identical in size to the previous commit.

Full suite: **1108 checks, 0 failures** across thirteen suites. Build exit 0, verifier
`PASS: JAR=836627 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.