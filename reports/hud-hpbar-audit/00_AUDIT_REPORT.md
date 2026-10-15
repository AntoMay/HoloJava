# HUD / HP Bar Audit — READ-ONLY

| Item | Value |
|------|-------|
| Checkpoint | `c7764a8` (`Remove unsupported Uber Sheep item`) |
| Working tree | `FullGameCanvas.java` + `RunWorld.java` modified — the uncommitted orange-accent UI patch. **Preserved untouched by this audit.** |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |

**No file was changed, staged, or committed. No game, MIDlet, emulator, or FreeJ2ME-Plus was run. Nothing was downloaded or installed. The reference JAR was hashed only.**

Sources: `RunWorld.java` (`RunWorld`), `FullGameCanvas.java` (`Canvas`), `Viewport.java` (`Viewport`).

---

## 1. Headline Finding

**There is no player HP bar.** Player HP is rendered as **text only** — `"HP X/Y"`.

The only graphical HP bar in the game is the **boss** bar. An exhaustive `fillRect` scan of `RunWorld` confirms it:

| Line | Element | Is an HP bar? |
|------|---------|---------------|
| `:1449` | pickup sprite fallback (12×13) | no |
| `:1452` | coin sprite fallback (10×10) | no |
| `:1454,1456` | EXP pickup sprite | no |
| `:1458` | food pickup sprite | no |
| `:1467` | shot sprite fallback (7×7) | no |
| `:1476` | enemy sprite fallback | no |
| `:1486` | boss sprite fallback | no |
| `:1501` | world background | no |
| `:1517` | player sprite fallback (24×24) | no |
| **`:1580-1581`** | **boss HP bar (250×18 background, 244×12 fill)** | **YES — boss only** |
| `:1596` | level-up overlay | no |
| `:1614` | pause overlay | no |

This reframes the task: any player HP bar would be a **new element**, not a modification.

---

## 2. HP Rendering Path

### 2.1 Call chain

```
Canvas.render()                        FullGameCanvas:277
  └─ world.draw(g, viewport.offsetX, viewport.offsetY, now)   :306
       └─ RunWorld.draw(g, offX, offY, now)                  :1440
            ├─ drawBackground(g, offX, offY)                  :1441 → :1495
            ├─ … pickups / shots / enemies / boss sprite …
            ├─ drawPlayer(g, offX, offY, now)                 :1491 → :1509
            └─ drawHud(g, offX, offY, now)                    :1492 → :1526
```

`drawHud` is called on **every** frame (`:1492`), and unconditionally — there is no branch on state.

### 2.2 Data source — authoritative gameplay state

| HUD text | Source fields | Declared |
|----------|---------------|----------|
| `HP X/Y` | `playerHp`, `playerMaxHp` | `RunWorld:94`, `:96` area |
| `LvX EXP X/Y` | `playerLevel`, `experience`, `expNeed` | `:71-73` |
| `Time … Kill …` | `kills`, `timeString()` | `:74-75` |
| `Coins … W… I…` | `coins`, `weaponCount`, `itemCount` | `:74`, `:83-84` |
| `Fans …` | `kills`, `mode.targetKills` | `:1572` |

**FACT**: every value is read directly from the live simulation field inside `drawHud`. There is no intermediate model, no snapshot, and no separate UI copy of HP. The HUD reads the same `playerHp` that `damagePlayer` and all healing paths mutate.

### 2.3 HP bar calculation — none exists

There is no length computation for player HP because there is no player bar. The only length formula in the HUD is the **boss** bar:

`RunWorld:1581`
```java
g.setColor(220, 30, 30);
g.fillRect(offX + 38, offY + 79,
           (int) (244.0 * MathUtil.clamp((double) boss.hp / (double) boss.maxHp, 0.0, 1.0)), 12);
```

| Property | Value |
|----------|-------|
| Base width | `244.0` pixels |
| Ratio | `boss.hp / boss.maxHp` |
| Clamp | `MathUtil.clamp(..., 0.0, 1.0)` — safe against 0 or negative HP |
| Floor | truncation `(int)` → a non-zero ratio always yields ≥ 1 px until HP is 0 |

`boss.maxHp` is assigned at `:596` as `(int) Math.min(20000.0, …)` so it is ≥ 1, making the division safe.

---

## 3. HUD Elements Around the HP Line

All coordinates are `offX + …`, `offY + …`. Font is `FACE_SYSTEM / STYLE_PLAIN / SIZE_SMALL`, set once at `:1527-1528`.

| # | Element | X | Y | Size | Colour | Line |
|---|---------|---|---|------|--------|------|
| 1 | **HP `X/Y`** (text) | `+4` | `+4` | text | `(0,0,0)` set `:1529` | `:1537` |
| 2 | **`LvX  EXP X/Y`** | `+4` | `+19` | text | `(0,0,0)` | `:1546` |
| 3 | **`Time …  Kill …`** | `+4` | `+34` | text | `(0,0,0)` | `:1558` |
| 4 | **`Coins …  W… I…`** | `+4` | `+49` | text | `(0,0,0)` | `:1567` |
| 5 | **`Fans …`** (Time Mode only) | `worldWidth - 90` | `+4` | text | `(0,0,0)` | `:1575` |
| 6 | **Boss bar background** | `+35` | `+76` | `250 × 18` | `(40,0,0)` | `:1580` |
| 7 | **Boss bar fill** | `+38` | `+79` | `≤244 × 12` | `(220,30,30)` | `:1581` |
| 8 | **Boss name** | `worldWidth/2`, `HCENTER` | `+80` | text | `(255,255,255)` | `:1587` |
| 9 | **Controls hint** | `worldWidth - 105` | `worldHeight - 14` | text | `(200,200,200)` | `:1592` |

### 3.1 Spacing and overlap analysis

- **Left column** (elements 1-4): rows at y `+4`, `+19`, `+34`, `+49` — a uniform **15 px pitch**. `Font.SIZE_SMALL` in MIDP is ~11-12 px tall, so rows do not overlap.
- **Left column vs boss bar**: text ends around y `+49 + 12 = +61`; the boss bar begins at `+76`. **15 px of clearance** — no overlap.
- **Fans (5) vs left column**: Fans sits at y `+4` on the **right**; the HP line is at y `+4` on the **left**. At 320 logical width, `Fans` starts at x `230`. The HP text `"HP 65/65"` is ~9 chars ≈ 54 px, ending near x 58. **No overlap at 320 px.**
- **Controls hint (9)** is bottom-right at `worldHeight - 14`; nothing else occupies that row.

### 3.2 Elements that could exceed the screen

| Element | Risk |
|---------|------|
| Left column texts | Low — longest is `"Time 00:00  Kill 0"` ≈ 20 chars ≈ 120 px at `SIZE_SMALL`, well inside 320 |
| Controls hint | `"5 Skill  9 Special"` ≈ 18 chars, anchored at `worldWidth - 105`; requires ~105 px of world width — satisfied since `worldWidth ≥ 320` |
| Boss bar | Fixed 250 px — see §5.1 |

---

## 4. HUD Consistency

### 4.1 Coordinate system

**Mixed, but coherent by role:**

| Group | Anchoring | Behaviour |
|-------|-----------|-----------|
| Left column (1-4) | absolute `offX + 4` | left-anchored, stays at world left edge |
| Fans (5) | `offX + worldWidth - 90` | **right-anchored to world edge** |
| Controls hint (9) | `offX + worldWidth - 105`, `offY + worldHeight - 14` | **right/bottom-anchored to world** |
| Boss bar (6,7) | **absolute** `offX + 35` / `+38` | fixed offset, **does not track world width** |
| Boss name (8) | `offX + worldWidth/2`, `HCENTER` | **centred on world** |

So four of five groups use world-relative anchoring that scales with the viewport; **the boss bar is the sole exception.**

### 4.2 Viewport interaction

`Viewport.update()` (`Viewport.java:26-33`):

```java
worldW = screenW < MIN_W ? MIN_W : screenW;   // world GROWS on wide screens
worldH = screenH < MIN_H ? MIN_H : screenH;
offsetX = (screenW - worldW) / 2;
offsetY = (screenH - worldH) / 2;
```

- On a **wide** screen the world **expands horizontally** (no stretching).
- On a **narrow** screen a **centred crop** of the 320×240 world is used, with a negative-ish `offsetX`.
- `offX`/`offY` reach `RunWorld.draw` as `viewport.offsetX/offsetY` (`FullGameCanvas:306`).

`RunWorld.worldWidth/worldHeight` are initialised to 320/240 (`RunWorld:11-12`) and updated from the viewport at `:217-218`.

**Menu screens differ**: `FullGameCanvas:297` computes `ox = (w - Viewport.MIN_W) / 2` and uses fixed `Viewport.MIN_W` (`:315`, `:419`, `:440`). Menus are always a fixed 320×240 panel centred on screen; the **gameplay HUD uses an expanding world**. Two different layouts coexist by design.

---

## 5. Proven Findings

### 5.1 Boss bar desyncs from the boss name on wide screens — MEDIUM

The boss bar is pinned at `offX + 35` (`:1580`), width 250 → spans x `35..285`.
The boss name is centred at `offX + worldWidth / 2` (`:1587`).

At 320 logical width these agree exactly (`(320-250)/2 = 35` ✓). On wider worlds they diverge:

| World width | Bar X | Bar span | Name centre | Error |
|-------------|-------|----------|-------------|-------|
| 320 | 35 | 35..285 | 160 | **0 px** ✅ |
| 360 | 35 | 35..285 | 180 | **20 px left** |
| 480 | 35 | 35..285 | 240 | **80 px left** |

This is a **proven inconsistency**, visible on any device wider than 4:3. It is not speculative — it follows directly from `:1580` vs `:1587`.

### 5.2 No player HP bar exists — not a bug, a gap

The task premise assumes one. The evidence shows HP is text-only. This is **not a defect** — it is a missing feature. It must be reported as such rather than "fixed".

### 5.3 Player HP has no at-a-glance bar; text is the only signal — LOW

`"HP 65/65"` is 9 characters of `SIZE_SMALL` text in black on the background. During dense combat (particles, enemies, boss bar at y+76) the top-left corner is comparatively uncluttered, so readability is plausible — **but this has not been verified on a device**, so it is recorded as an observation, not a confirmed legibility problem.

---

## 6. State Changes and Cache Synchronisation

### 6.1 Caching mechanism

`drawHud` caches six strings and recomputes them only when a comparison value changes:

| Cached string | Cache field | Recompute guard | Line |
|---------------|-------------|-----------------|------|
| `"HP X/Y"` | `cachedHpString` | `cachedHpString == null \|\| playerHp != lastPlayerHp \|\| playerMaxHp != lastPlayerMaxHp` | `:1532` |
| `"LvX EXP X/Y"` | `cachedLevelExpString` | 4-way comparison | `:1540` |
| `"Time…Kill…"` | `cachedTimeKillString` | `kills != lastKills \|\| !cachedTimeString.equals(lastCachedTimeString)` | `:1553` |
| `"Coins…"` | `cachedCoinsString` | 3-way comparison | `:1561` |
| `"Fans…"` | `cachedFansString` | `kills != lastFansKills` | `:1571` |
| boss name | `cachedBossName` | `!bossName().equals(lastBossName)` | `:1583` |
| time string | `cachedTimeString` | `now - lastTimeStringUpdate >= 1000` (1 Hz) | `:1549` |

All guards compare **the authoritative gameplay fields directly**, not a derived copy.

### 6.2 Is staleness possible?

**No — the guards are complete.** The HP cache compares **both** `playerHp` **and** `playerMaxHp`, so it refreshes when HP drops, when a heal raises it, and when `maxHp` changes via `applyStat(0)` (`:1293`) or a `EFFECT_MAX_HP` item (`recomputeStats`). There is no path that mutates HP without changing one of the two compared fields.

### 6.3 Cross-run safety — verified, and initially mis-read

`reset()` (`RunWorld:160-…`) **clears every cache and sentinel** at lines `191-203`:

```java
cachedHpString = null;  cachedLevelExpString = null;
cachedTimeKillString = null;  cachedCoinsString = null;
cachedFansString = null;  cachedBossName = null;
lastPlayerHp = -1; lastPlayerMaxHp = -1;
lastPlayerLevel = -1; lastExperience = -1; lastExpNeed = -1;
lastTimeStringUpdate = 0; cachedTimeString = null; lastCachedTimeString = null;
lastKills = -1;
lastCoins = -1; lastWeaponCount = -1; lastItemCount = -1;
lastFansKills = -1;
lastBossName = null;
```

**FACT**: no HUD state leaks between runs. A first grep over a truncated window appeared to show no cache clearing; re-reading the full method body corrected this. The code is correct.

`cachedControlsHint` is a `final`-style constant initialised once (`:123`) and never changes — correct, it is a literal.

---

## 7. Not Determined

| # | Item |
|---|------|
| U1 | **Actual on-device legibility** — no device or emulator was run, so text size, contrast against busy scenes, and the orange accent's interaction with this HUD are unverified |
| U2 | **Real-world screen aspect ratios.** The divergence in §5.1 is arithmetically proven for world widths > 320, but whether target devices actually exceed 320 px logical width was not determined |
| U3 | Whether a player HP bar is **wanted** at all, or whether text-only is an accepted design — this is a product decision, not a code finding |
| U4 | Behaviour of the HUD when `mode.completion == COMPLETE_KILL_TARGET` (Fans line) is reachable only in Time Mode, which A4a currently disables in the UI |
| U5 | Whether `Font.SIZE_SMALL` metrics match the assumed ~12 px row height on all target devices — row pitch is a hardcoded 15 px |
| U6 | Whether the boss bar should stay fixed-width or scale with the world — design intent not recorded anywhere in the project |

---

## 8. Minimal Proposed Improvements (NOT implemented)

| # | Improvement | File / method | Before → After | Risk |
|---|-------------|---------------|----------------|------|
| **P1** | Centre the boss bar with its name | `RunWorld.drawHud` `:1580-1581` | `offX + 35` → `offX + (worldWidth - 250) / 2`, and fill origin `+38` → `+3` relative to the same base | **LOW** — at 320 px width `(320-250)/2 == 35`, so **no change at the design resolution**; only wider worlds change |
| **P2** | *(Optional)* Add a player HP bar | `RunWorld.drawHud` | absent → a new element | **MEDIUM** — new feature; position, width, and colour are undecided (U3) |
| **P3** | *(Optional)* Extract HUD offsets into named constants | `RunWorld.drawHud` | magic `4, 19, 34, 49` → named constants | **LOW** — pure refactor, no behaviour change |

**P1 is the only change justified by a proven defect**, and it is a no-op at the 320×240 design resolution — it only corrects behaviour on wider screens. P2 is a feature request, not a bug fix, and should be decided before any code is written.

---

## 9. Final Status

| Item | Value |
|------|-------|
| HEAD | `c7764a8` — unchanged |
| Tracked source files changed by this audit | **none** |
| Pre-existing uncommitted UI patch | preserved untouched |
| Staging / commit | none |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/hud-hpbar-audit/00_AUDIT_REPORT.md` |
