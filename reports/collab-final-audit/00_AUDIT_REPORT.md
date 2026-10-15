# Final Collab Audit & Orange Accent UI Plan — READ-ONLY

| Item | Value |
|------|-------|
| Checkpoint | `c7764a8` (`Remove unsupported Uber Sheep item`) |
| Working tree | clean (tracked) |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |

**No source file was changed, staged, or committed. No game, MIDlet, emulator, or FreeJ2ME-Plus was run. Nothing was downloaded or installed. The reference JAR was hashed only. No Super Collab, Hammer, Anvil, or new weapon category was implemented.**

Sources: `RunWorld.java` (`RunWorld`), `FullGameCanvas.java` (`Canvas`), `ContentCatalog.java` (`Catalog`).

---

## 1. Audit Result Summary

**The ordinary Collab mechanism is correct and its failure paths keep materials safe.**

All seven verification points pass on static reading. The only *proven* defect is a **latent null dereference** at `RunWorld:1217` that is currently unreachable. The UI accent work is blocked by **one missing accessor**, not by layout constraints — the renderer is a flat text list with a clean colour hook.

| Section | Verdict |
|---------|---------|
| A — Collab mechanism | **PASS** (1 latent risk, unreachable) |
| B — Level-up option path | **PASS** |
| C — Orange accent | **Feasible**; smallest change = 1 accessor + 1 conditional colour |

---

## 2. Section A — Collab Mechanism Verification

### 2.1 The five active recipes

Loaded at runtime from the hardcoded fallback `Catalog:295-301` (the CSV is not packaged — `build_fullgame.sh:11,32` zip only `assets`; `unzip -l` shows 0 `data/` entries).

| # | Result | Ingredient A | Ingredient B | Result `WeaponDef` |
|---|--------|--------------|--------------|--------------------|
| 1 | Absolute Wall | `bounce_ball` | `cutting_board` | `Catalog:202` |
| 2 | BL Fujoshi | `bl_book` | `psycho_axe` | `Catalog:203` |
| 3 | Black Plague | `ens_curse` | `owl_dagger` | `Catalog:204` |
| 4 | Blood Lust | `owl_dagger` | `psycho_axe` | `Catalog:205` |
| 5 | Legendary Sausage | `bl_book` | `sausage` | `Catalog:206` |

**FACT**: each `WeaponDef.prerequisiteA/B` matches its `CollabDef` exactly — verified pairwise for all five.

### 2.2 Max-level requirement for both weapons

`RunWorld:1212-1217`:

```java
if (weaponCount < 6 && mode.allowsCollabs) {
    for (int c = 0; c < ContentCatalog.COLLABS.length; c++) {
        CollabDef cd = ContentCatalog.COLLABS[c];
        int a = weaponLevelById(cd.weaponA); int b = weaponLevelById(cd.weaponB);
        WeaponDef wd = ContentCatalog.weapon(cd.id);
        if (a == wd.maxLevel && b == wd.maxLevel && !offerUsed[wd.id.hashCode() & 63]) {
```

All 20 weapons declare `maxLevel = 7`, so both ingredients must be at **level 7**. The comparison is against `wd.maxLevel` (the *result's* cap), not a literal.

### 2.3 Ownership validation before craft

`weaponLevelById` (`RunWorld`, helper):

```java
private int weaponLevelById(String id) {
    int s = weaponSlotById(id);
    return s < 0 ? 0 : weaponLevels[s];
}
```

`weaponSlotById` returns **`-1`** when the weapon is not in `weaponSlots[0..weaponCount-1]`, so `weaponLevelById` returns **`0`**.

**FACT**: `0 != 7`, therefore an unowned ingredient can never satisfy the condition. Ownership is enforced implicitly by the level check — there is no separate `weaponSlotById >= 0` test, and none is needed.

### 2.4 Consumption and result insertion

`RunWorld:1271-1274` inside `applyOffer`:

```java
} else if (o.type == OFFER_WEAPON) {
    ContentCatalog.WeaponDef def = ContentCatalog.weapon(o.value);
    if (o.collab) {
        removeWeaponById(def.prerequisiteA);
        removeWeaponById(def.prerequisiteB);
        addWeapon(o.value, 1);
```

`removeWeaponById` (`:1341-1349`) shifts `weaponSlots`, `weaponLevels`, `weaponReadyAt` down, decrements `weaponCount`, and clears the vacated tail — **including `weaponReadyAt`**, so no stale cooldown is inherited.

Result is added at **level 1**. Net inventory change: **−1 weapon**.

### 2.5 Six-slot capacity and all failure paths

`addWeapon` (`:1109-1115`) is the only weapon-insert path:

```java
private void addWeapon(int weaponIndex, int level) {
    if (weaponIndex < 0) return;
    if (weaponCount < 6) { weaponSlots[weaponCount] = weaponIndex; weaponLevels[weaponCount] = level; weaponCount++; }
}
```

Call sites: `:205` (character start), `:1274` (collab result), `:1278` (normal new weapon). **No direct `weaponSlots[...]` write exists outside `addWeapon`.**

| Failure path | Guard | Materials safe? |
|--------------|-------|----------------|
| Offer never generated | `a == 7 && b == 7` fails at `:1217` | ✅ no mutation |
| Inventory full at offer time | `weaponCount < 6` at `:1212` | ✅ no mutation |
| Collab already offered this cycle | `offerUsed[...]` at `:1217` | ✅ no mutation |
| Weapon id resolves to `-1` | `weaponIndex < 0` at `:1110` | ⚠️ see §2.7 |
| Inventory full at accept time | `weaponCount < 6` at `:1111` | ✅ unreachable — see below |

**Overflow safety (FACT)**: the offer requires `weaponCount < 6`. Accepting removes 2 weapons, leaving `weaponCount ≤ 5 - 2 = 3`, so at least 3 free slots exist. `addWeapon` at `:1274` **cannot fail**. Materials can never be lost to a failed insert.

**No re-validation at accept time** — `applyOffer` re-reads `def.prerequisiteA/B` and removes by id. `removeWeaponById` returns immediately if `slot < 0`, so even a hypothetical lost ingredient degrades silently rather than corrupting the array. This cannot occur in practice: `selectOffer` gates on `state == STATE_LEVEL_UP` (`:291`), during which the player cannot fire (`attackPressed` is consumed by `moveOffer`/`selectOffer` only), so no weapon can be removed between offer and acceptance.

### 2.6 Mode and weapon-count restrictions

| Restriction | Location | Effect |
|-------------|----------|--------|
| `mode.allowsCollabs` | `RunWorld:1212` | Stage Mode ✅, Endless ✅, Time Mode ❌ (`Catalog:247-249`) |
| `weaponCount < 6` | `RunWorld:1212` | same gate as new-weapon offers (`:1224`) |
| Per-run collab cap | — | ❌ none exists (no counter field) |

Ordinary collabs have no per-run limit in the reference game either, so this is **not a divergence**.

### 2.7 Proven latent defect — null dereference

`RunWorld:1216-1217`:

```java
ContentCatalog.WeaponDef wd = ContentCatalog.weapon(cd.id);   // returns null on miss
if (a == wd.maxLevel && ...)                                   // NPE if wd == null
```

`Catalog.weapon(String)` returns **`null`** when the id is absent.

**Currently unreachable**: all five active recipes have matching `WeaponDef`s (verified §2.1), and the CSV that would introduce 26 id-less recipes is never loaded.

**Risk**: latent. Would become live only if `data/` were packaged into the build. Fix is one line.

---

## 3. Section B — Level-Up Option Path

### 3.1 How a Collab option is formed and stored

Inside `fillOffer(slot, OFFER_WEAPON)`, `RunWorld:1218-1221`:

```java
offers[slot].type   = OFFER_WEAPON;
offers[slot].value  = ContentCatalog.weaponIndex(wd.id);
offers[slot].existing = -1;
offers[slot].newItem = true;
offers[slot].collab  = true;      // ← identity marker
offerUsed[wd.id.hashCode() & 63] = true; return true;
```

The `Offer` struct is a private static inner class holding `int type, value, existing; boolean newItem, collab;` with a `clear()` that resets `collab = false`.

**FACT**: the collab is written into the **same `offers[slot]`** a normal weapon would occupy, and `fillOffer` returns `true`.

### 3.2 How Collab identity is distinguished

| Location | Behaviour |
|----------|-----------|
| `RunWorld:1272` `if (o.collab)` | branches to the consume-two path — **internal use works** |
| `RunWorld:277-288` `offerType(int)` | returns `"WEAPON"` for a collab — **identity lost to the UI** |
| `RunWorld:257-275` `offerLabel(int)` | renders the result weapon name — no collab marker |

**FACT**: `Offer.collab` is stored and used internally but **no public accessor exposes it**. `FullGameCanvas` can only call `offerCount()`, `selectedOffer()`, `offerLabel(int)`, `offerType(int)`.

### 3.3 Selection and processing

`selectOffer()` (`:290-...`) → `index >= 4` handles meta options; otherwise `applyOffer(index)` (`:1265`) then `state = STATE_RUNNING`.

`applyOffer` guards `if (index < 0 || index >= 4) return;` — so only slots 0-3 are real offers.

### 3.4 Does a Collab add an option? — **No**

`generateOffers` (`:1151-1169`):
- `:1154` sets `offerCount` **once**, before filling
- `:1155-1160` loops slots 0-3; a successful `fillOffer` prevents backfill at `:1158`
- `:1161-1168` set slots 4 and 5 to meta options

**FACT**: a collab occupies a slot that would otherwise hold a normal weapon, and returns `true` so no replacement fill occurs. `offerCount` is never modified by a fill. **Total option count is unchanged.**

### 3.5 Interaction with reroll / hold / other options

| Mechanism | Location | Interaction with collab |
|-----------|----------|-------------------------|
| **Reroll** | `:295-298`, `:1166` | `reroll` regenerates all offers via `generateOffers()`. A collab disappears; new ones may appear. `offerUsed[]` is cleared at `:1153`, so recipes are re-evaluated. |
| **Hold** | `:299-303`, `:1161-1165`, `:1168` | A held slot is copied and shifted to index 0 (`:1162-1164`). The shift is a **whole-`Offer` copy**, so `collab` travels with it correctly. |
| **Meta slots** | `:1166-1168`, `:294-306` | Unaffected — `offers[4]`/`offers[5]` are overwritten with REROLL/HOLD regardless. |
| **Skill** | `:1184-1187` | Separate branch; a collab never displaces a Skill offer beyond the ordinary slot competition. |

**No defect found** in any interaction.

---

## 4. Section C — Minimal Orange Accent Plan

### 4.1 Renderer inspection

`Canvas.drawLevelUp` renders a **flat text list** — no card, border, icon, or per-option background:

```java
for (int i = 0; i < world.offerCount(); i++) {
    int yy = y + 46 + i * 24;
    g.setColor(i == world.selectedOffer() ? 255 : 190,
               i == world.selectedOffer() ? 230 : 190,
               i == world.selectedOffer() ?  60 : 190);
    drawText(g, x + 24, yy, (i == world.selectedOffer() ? "> " : "  ") + world.offerLabel(i), ...);
    g.setColor(130, 130, 130);
    drawText(g, x + 240, yy, world.offerType(i), ...);
}
```

`drawText` calls `g.drawString` **without** setting a colour, so the `g.setColor` immediately preceding it is the effective text colour. **This is a clean, isolated hook — no layout change is needed.**

### 4.2 Proposed change — descriptive only (NOT applied)

#### Change 1 — `RunWorld.java`, add one accessor

| | |
|---|---|
| **Location** | alongside `offerType(int)` (`:277-288`) |
| **Purpose** | expose collab identity safely to the canvas |
| **Shape** | a bounds-checked `int`/`boolean` accessor returning whether `offers[index].collab` is set, mirroring the existing `index < 0 \|\| index >= offerCount` guard used by `offerLabel`/`offerType` |
| **Before** | no such method; `Offer.collab` is private to `RunWorld` |
| **After** | one public/package accessor; **no behaviour change**, no new state |
| **Why safe** | read-only; mirrors the guard already used twice in the same class |

Returning an `int` kind (0 = normal, 1 = collab) instead of a `boolean` leaves room for a future Super Collab accent without another signature change — but that is optional and `boolean` is the smaller change today.

#### Change 2 — `FullGameCanvas.java`, `drawLevelUp`

| | |
|---|---|
| **Location** | the `g.setColor(...)` inside the offer loop |
| **Before** | selected `(255,230,60)` yellow; unselected `(190,190,190)` grey |
| **After** | if the row is a collab, use an orange tone — e.g. unselected `(230,140,40)`; when selected, keep a brighter/lighter variant (e.g. `(255,190,90)`) so the `> ` prefix and highlight remain legible |
| **Non-collab rows** | **byte-for-byte unchanged** |
| **Layout / option count** | **unchanged** — no new element, no reordering, `offerCount` untouched |

The grey type word at `x + 240` (`:325`) is left alone unless an accent there is wanted; keeping it unchanged is the smaller change.

### 4.3 Requirements coverage

| Requirement | How it is met |
|-------------|---------------|
| Collab gets an orange accent | Conditional colour on the collab row |
| Normal Weapon / Skill keep current look | Only the collab branch differs |
| Selected status stays readable | Selected row keeps the `> ` prefix and a brighter tone |
| No layout or count change | Colour-only; no geometry touched |
| Safe explicit accessor | Bounds-checked method mirroring `offerLabel`/`offerType` |

### 4.4 Visual risks

| # | Risk | Likelihood | Note |
|---|------|-----------|------|
| V1 | Orange may be hard to read at `SIZE_SMALL` on black | Medium | MIDP system fonts have limited anti-aliasing; a lighter tone is safer |
| V2 | Selected-collab tone may read as "yellowish" like the normal highlight | Medium | Decide precedence — propose a distinctly warmer/brighter orange |
| V3 | Two adjacent collab rows could look identical | Low | Colour alone conveys no ordering; the `> ` prefix still marks selection |
| V4 | Colour-blind users may not distinguish | Low | The text label still shows the weapon name; colour is redundant |

**No visual verification is possible without a device** — see §6.

---

## 5. Regression Risks and Suggested Checks

| # | Risk | Severity | Suggested check |
|---|------|----------|-----------------|
| K1 | Latent NPE at `:1217` if `data/` is ever packaged | Medium | Add `if (wd == null) continue;` — independent, 1 line |
| K2 | Accessor bounds bug exposing the wrong offer | Medium | Verify `index < 0 \|\| index >= offerCount` mirrors `offerType` |
| K3 | Accent applied to a non-collab row | Low | Confirm the condition reads the accessor, not `type == OFFER_WEAPON` |
| K4 | Selected-row readability regression | Low | Manual device check |
| K5 | `offerUsed` collisions (64-slot hash) suppressing offers | Low | Pre-existing; unaffected by a read-only accessor |
| K6 | Any gameplay change creeping in | Low | Confirm the diff is accessor + one colour conditional |

**Suggested check order**: K1 first (independent of UI), then K2–K3 statically, then K4 on a device.

---

## 6. Not Determined

| # | Item |
|---|------|
| U1 | Actual rendered appearance — no device or emulator was run, so colour legibility is unverified |
| U2 | Whether the `x + 240` type word should also be accented |
| U3 | Whether `boolean` or tri-state `int` accessor is preferred — `int` leaves room for a future Super Collab accent |
| U4 | Exact orange values — the figures above are proposals, not design decisions |
| U5 | Whether a held collab should keep its accent after the shift at `:1162-1164` — the whole `Offer` is copied, so it should, but this is unverified at runtime |
| U6 | Whether the `offerType` string should become `"COLLAB"` — a text change, deliberately excluded as larger than colour-only |

---

## 7. Final Status

| Item | Value |
|------|-------|
| HEAD | `c7764a8` — unchanged |
| Tracked source files changed | **none** |
| Staging / commit | none |
| Super Collab / Hammer / Anvil / new category implemented | **none** |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/collab-final-audit/00_AUDIT_REPORT.md` |
