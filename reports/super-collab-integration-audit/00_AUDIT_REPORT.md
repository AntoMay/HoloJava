# Super Collab Integration Readiness Audit — READ-ONLY

| Item | Value |
|------|-------|
| Checkpoint | `c7764a8` (`Remove unsupported Uber Sheep item`) |
| Working tree | clean (tracked) |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |

**No file was edited, added, removed, reformatted, or moved. No staging, no commit. No game/MIDlet/emulator run. Nothing downloaded or installed. No build run — every finding below is a static source read, so a build would add nothing. The reference JAR was only hashed, never opened for writing.**

Primary sources: `RunWorld.java` (cited as `RunWorld`), `FullGameCanvas.java` (`Canvas`), `ContentCatalog.java` (`Catalog`).

---

## 1. Integration Readiness Summary

**The agreed design is largely compatible with existing structure — with four concrete gaps.**

| Design requirement | Ready? | Evidence |
|--------------------|--------|----------|
| Weapon cap 6 | ✅ | `weaponSlots[6]` `:47`, guard `:1111` |
| Support Item cap 6 | ✅ | `itemSlots[6]` `:50`, guard `:1120` |
| Option count unchanged | ✅ | `offerCount = rerolls > 0 ? 6 : 5` `:1154`; replacement happens in-place per slot |
| Collab replaces one normal option | ✅ **already true today** | `:1212-1222` fills an `OFFER_WEAPON` slot with a collab |
| Super Collab replaces one normal option | ⚠️ same slot mechanism, needs recipe data | see §5 |
| No Hammer / Anvil mechanism | ✅ nothing to remove | `grep -rni "golden\|anvil\|hammer" src/` → 0 matches |
| Orange accent (ordinary Collab) | ❌ **not possible today** | see §2.3 |
| Golden accent (Super Collab) | ❌ **not possible today** | see §2.3 |

**Verdict: slot mechanics and the "replace one option" model are already in place. The colour-accent requirement is blocked by a missing accessor, and Super Collab is blocked by missing data.**

---

## 2. Level-Up Option Formation

### 2.1 Option count and storage

| Concern | Location | Behaviour |
|---------|----------|-----------|
| Storage | `RunWorld:55` | `private final Offer[] offers = new Offer[6];` |
| Count | `RunWorld:111`, set `:1154` | `offerCount = rerolls > 0 ? 6 : 5` |
| Offer struct | `RunWorld` inner `class Offer` | `int type; int value; int existing; boolean newItem; boolean collab;` |
| Reset | `RunWorld:1152-1153` | `clear()` all offers, reset `offerUsed[]` |

Slot layout is meaningful:

| Slots | Meaning |
|-------|---------|
| **0–3** | real options (Skill / Weapon / Item / Stat / Collab) |
| **4** | `OFFER_REROLL` or `-1` — `RunWorld:1166-1167` |
| **5** | `OFFER_HOLD` or `-1` — `RunWorld:1168` |

`selectOffer()` `RunWorld:291-...` branches on `index >= 4` for the meta options.

### 2.2 Generation flow

```
generateOffers()                                  RunWorld:1151-1169
  ├─ clear offers[0..5]                           :1152
  ├─ offerUsed[] = false                          :1153
  ├─ offerCount = rerolls > 0 ? 6 : 5             :1154
  ├─ for slot in 0..3:                            :1155
  │    type = chooseOfferType(slot)               :1156 / :1171-1181
  │      slot < 2 : SKILL(7/20) | WEAPON(7/20) | ITEM(5/20) | STAT
  │      slot >= 2 : WEAPON 50% | ITEM 50%
  │    if !fillOffer(slot,type)                   :1157
  │       fallback: fillOffer(slot, WEAPON) else fillOffer(slot, ITEM)   :1158
  ├─ held offer shifted to front                  :1161-1165
  ├─ offers[4] = REROLL or -1                     :1166-1167
  └─ offers[5] = HOLD or -1                       :1168
```

`fillOffer(slot, type)` `RunWorld:1183-1260`:

| Type | Lines | Notes |
|------|-------|-------|
| `OFFER_SKILL` | `:1184-1187` | only if `!skillAcquired` |
| `OFFER_STAT` | `:1188-1196` | weighted pick `{2,3,4,3,4,2}` |
| `OFFER_WEAPON` | `:1198-1234` | 50% upgrade existing → **collab** `:1212-1222` → new category-0 |
| `OFFER_ITEM` | `:1235-1253` | 50% upgrade existing → new item |
| fallbacks | `:1255-1256` | `OFFER_COINS` if weapon fails, `OFFER_FOOD` if item fails |

### 2.3 Can one option be replaced without changing the count? — **Yes**

The collab branch `RunWorld:1212-1222` writes into the **same `offers[slot]`** that a normal weapon would have used:

```java
offers[slot].type = OFFER_WEAPON; offers[slot].value = ContentCatalog.weaponIndex(wd.id);
offers[slot].existing = -1; offers[slot].newItem = true; offers[slot].collab = true;
```

`fillOffer` returns `true`, so `generateOffers` does not backfill that slot. **`offerCount` is never touched by a fill.** Replacing one option with a Super Collab is therefore structurally identical and preserves the total.

**Distribution impact**: a Super Collab would occupy a slot that might otherwise have been a weapon upgrade or a new weapon. This is the intended "replace" semantics, but it does mean collab-eligible runs lose one normal weapon offer. Recorded as a design consequence, not a defect.

### 2.4 Rendering — colour accents are currently blocked

`Canvas.drawLevelUp` renders a **flat text list**. There is no card, border, icon, or per-option background.

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

`drawText` calls `g.drawString` without setting a colour, so **the `g.setColor` immediately before it is the effective text colour** — this is a clean, isolated hook.

**Blocking gap**: `Offer.collab` is set at `RunWorld:1219` but **no accessor exposes it**. The canvas can only call:

| Accessor | Line |
|----------|------|
| `offerCount()` | `:251` |
| `selectedOffer()` | `:252` |
| `offerLabel(int)` | `:257` |
| `offerType(int)` | `:277` |

`offerType` returns `"WEAPON"` for a collab, so **an ordinary Collab is currently visually indistinguishable from a normal weapon**.

An orange/golden accent therefore requires **one new accessor** (e.g. `int offerKind(int)` returning 0/1/2). No layout change is needed.

---

## 3. Weapon Slot Validation

### 3.1 Capacity

| Check | Location | Result |
|-------|----------|--------|
| Array size | `RunWorld:47` | `weaponSlots[6]` |
| Runtime cap | `RunWorld:1111` | `if (weaponCount < 6)` inside `addWeapon` |

**Every** weapon insertion goes through the guarded helper — verified by enumerating call sites:

| Call site | Context |
|-----------|---------|
| `:205` | character starting weapon |
| `:1274` | collab result |
| `:1278` | new weapon (normal) |
| `:1111-1114` | the guard + increment |

There is **no direct `weaponSlots[...] =` write outside `addWeapon`**. Capacity is enforced centrally.

### 3.2 Collab consumption

`RunWorld:1267-1270`:

```java
removeWeaponById(def.prerequisiteA);
removeWeaponById(def.prerequisiteB);
addWeapon(o.value, 1);
```

`removeWeaponById` (`:1341-1349`) shifts slots down, decrements `weaponCount`, and clears the vacated tail. Net change: **−1 weapon**.

**Overflow safety**: the offer is gated by `weaponCount < 6` at `:1212`. After removing 2 there are at least 3 free slots, so `addWeapon` at `:1274` can never fail. **Materials cannot be lost to a failed insert.**

### 3.3 Behaviour when inventory is full

`addWeapon` silently returns when `weaponCount == 6` (`if (weaponCount < 6)` has no `else`). For the collab path this is unreachable given the `:1212` gate. For `:1278` (new weapon) it is reachable but harmless — the offer is simply voided.

---

## 4. Support Item Slot Validation

### 4.1 Capacity is a runtime check, not just an array size

| Check | Location | Result |
|-------|----------|--------|
| Array size | `RunWorld:50-51` | `itemSlots[6]`, `itemLevels[6]` |
| Runtime cap | `RunWorld:1120` | `if (itemCount < 6)` inside `addItem` |
| Offer gate | `RunWorld:1248` | `if (itemCount < 6)` |

Call sites of `addItem`: only `:1283` (new item offer). All go through the guarded helper.

### 4.2 Finding an item by id

`itemSlotById(String id)` `RunWorld:1333-1336` — returns the slot index or `-1`. **Reusable as-is.**

### 4.3 Finding an item's level — **helper missing**

**There is no `itemLevelById` helper.** `grep -c "itemLevelById"` → **0**.

The weapon side has `weaponLevelById` (`RunWorld`, returns `0` when absent). A Super Collab prerequisite check ("companion item at max level") needs the item equivalent. Reading `itemLevels[itemSlotById(id)]` inline works but does not match the existing convention and would not handle the `-1` case safely on its own.

### 4.4 Max level is per-item — not uniform

Verified from `Catalog:211-221` (after `uber_sheep` removal in `c7764a8`):

| Item | maxLevel |
|------|----------|
| Face Mask | **1** |
| Knightly Milk | 3 |
| Energy Drink | 3 |
| Just Bandage | 3 |
| Study Glasses | **5** |
| Sake | 3 |
| Gorilla's Paw | 3 |
| Piki Piki Piman | 3 |
| Limiter | 3 |
| Headphones | **5** |

The cap must be read from `ItemDef.maxLevel`, never hardcoded. `RunWorld:1241` already does this correctly for item upgrades: `itemLevels[s] < d.maxLevel`.

### 4.5 Behaviour when full or missing

- Full: `addItem` silently returns (`:1120`).
- Missing: `itemSlotById` returns `-1`; `weaponLevelById` has the same convention (`s < 0 ? 0 : …`), so an item equivalent should return `0` rather than a raw `-1` to stay consistent and avoid negative indexing.

---

## 5. Super Collab Readiness — Gap Analysis

### 5.1 Gaps blocking implementation

| # | Gap | Evidence | Severity |
|---|-----|----------|----------|
| **G1** | **No Super Collab recipe data.** `grep -rni "super" src/` finds only inert `CollabDef.type` metadata. `BASE_WEAPONS` has no category-3 weapon. | `Catalog` | **BLOCKER** |
| **G2** | **No `itemLevelById` helper** | §4.3 | High |
| **G3** | **No accessor exposing `Offer.collab`** — colour accents impossible | §2.4 | High |
| **G4** | **No run-scoped "super collab used" flag** | no such field in `RunWorld` | Medium |
| **G5** | **No item-removal helper** — only `removeWeaponById` exists | `RunWorld` | Medium |
| **G6** | **No item-effect transfer concept** — the design does not state whether the companion item's effect persists | — | Decision |

### 5.2 Requirements that are already satisfied

| Requirement | Evidence |
|-------------|----------|
| Replace one normal option | `:1212-1222` pattern; `offerCount` never touched |
| Keep total option count | `:1154`; fill returns `true` so no backfill |
| Consume only after successful craft | consumption happens in `selectOffer`, not at offer time — `:1267-1270` |
| Result at level 1 | `addWeapon(o.value, 1)` `:1274` |
| Result weapon must exist | `weaponIndex(wd.id)` `:1218`; **guard `wd == null` required** (latent NPE identified in `reports/skill-collab-audit`) |
| Run-scoped state + reset | `reset()` `:160-188` already clears every comparable field (`offerCount=0`, `heldOffer=-1`, …) |
| No Hammer/Anvil to remove | `grep` → 0 matches |
| Weapon capacity | `addWeapon` guard `:1111` |
| Item capacity | `addItem` guard `:1120` |

### 5.3 Preventing double consumption and repeat craft

For ordinary collabs this is already safe: materials are removed, so `weaponLevelById` returns `0` and the recipe cannot re-trigger without re-acquiring both.

For Super Collab the same holds **provided** the companion item is also removed. If the item were *kept*, a recipe could re-trigger whenever the collab weapon is re-acquired — **this is the main repeat-craft hazard and depends on G5/decision G6**.

---

## 6. Regression Risks and Edge Cases

| # | Risk | Severity | Mitigation |
|---|------|----------|------------|
| R1 | Null deref on `wd.maxLevel` if a recipe id has no weapon | Medium | Add `if (wd == null) continue;` before `:1217` |
| R2 | Item slot underflow if item removed twice | Medium | Guard `slot < 0` (mirroring `removeWeaponById`) |
| R3 | Super Collab steals a normal weapon offer every eligible level-up | Medium | Accepted by design; confirm intent |
| R4 | Forgetting new state in `reset()` leaks across runs | Medium | Explicit reset test required |
| R5 | `offerUsed[]` is 64 slots indexed by `hashCode() & 63` — a new recipe id can collide and suppress an unrelated offer | Low | Pre-existing; avoid adding ids that collide |
| R6 | Colour hook conflicts with the selected-row yellow (`255,230,60`) | Low | Decide precedence between selected and collab colour |
| R7 | Reading `itemLevels[-1]` if an item helper returns a raw `-1` | Low | Return `0` on miss, like `weaponLevelById` |
| R8 | `offerCount` is 5 when `rerolls == 0`, so slots 4–5 are inactive | Info | Super Collab must only target slots 0–3 |

**Boundary case worth testing**: a run where `weaponCount == 5`. A collab needs `< 6` at `:1212`; after removing 2 and adding 1 the count is 4. Safe. A run at `weaponCount == 4` with a Super Collab consuming 1 weapon + 1 item ends at 4. Also safe.

---

## 7. Recommended Implementation Order (smallest change first)

| Step | Change | Files | Rationale |
|------|--------|-------|-----------|
| **1** | Add `wd == null` guard | `RunWorld` (1 line) | Removes a proven latent NPE; independent of the feature |
| **2** | Add `itemLevelById(String)` mirroring `weaponLevelById` | `RunWorld` (~4 lines) | Required by step 5; harmless alone |
| **3** | Add `removeItemById(String)` mirroring `removeWeaponById` | `RunWorld` (~9 lines) | Required by step 5 |
| **4** | Add `offerKind(int)` accessor exposing `Offer.collab` | `RunWorld` (~4 lines) | Unblocks both accent colours |
| **5** | Add run-scoped `superCollabUsed` field + reset | `RunWorld` (2 lines) | Pure state, no behaviour |
| **6** | Add recipe data + a `fillSuperCollab(slot)` branch modelled on `:1212-1222` | `Catalog` + `RunWorld` | **Only after 1–5 and after G1 content decisions** |
| **7** | Apply colours in `drawLevelUp` | `Canvas` (~6 lines) | Pure presentation; last so it can be reviewed independently |

Steps **1–5 are independent of the feature existing** and are individually safe. Step 6 is the first one that changes gameplay. Step 7 is cosmetic.

---

## 8. Not Determined

| # | Item |
|---|------|
| U1 | Whether the companion item's effect should persist after being consumed. The agreed design is silent. This changes both balance and whether `removeItemById` is even correct. |
| U2 | Whether Super Collab and ordinary Collab may both be offered in the same level-up screen, and in what priority order. |
| U3 | Whether the selected-row highlight (`255,230,60`) or the collab colour wins when a collab row is selected. |
| U4 | How many Super Collabs are permitted per run. The agreed design does not state a limit. |
| U5 | Whether `Offer.collab` should become a tri-state (`boolean` → `int kind`) now that two accents exist. |
| U6 | Runtime behaviour of item consumption on a full inventory — unobservable without a device. |
| U7 | Whether `offerUsed[]` collisions (R5) will be hit by any proposed recipe id. Depends on unreleased content. |

**No build or runtime test was performed**, so all findings are static. Items U1–U4 are design decisions that cannot be derived from the code.

---

## 9. Final Status

| Item | Value |
|------|-------|
| HEAD | `c7764a8` — unchanged |
| Tracked source files changed | **none** |
| Commit / staging | none |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/super-collab-integration-audit/00_AUDIT_REPORT.md` |
