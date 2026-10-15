# Items, Effects, Drops, and Level Caps

**Date:** 2026-10-15
**Change group:** `addItem`, `recomputeStats`, every supported `EFFECT_*`, item level caps, slot limits
**Outcome:** Parity confirmed. One catalog parity gap found and documented, deliberately not added.
**No production code changed.**

---

## 1. Effect parity

The reference's `recomputeStats` reads `ItemDef.effect` and compares it against exactly:

```
0 DAMAGE_PCT   1 HASTE_PCT   2 SPEED_PCT   3 MAX_HP
4 PICKUP_PCT   5 CRIT_PCT    6 REGEN      10 EXP_PCT
```

plus a separate `NEGATE` (8) check in `damagePlayer`. **This build handles the identical set.**

Effects **7 (`SHIELD`) and 9 (`FOOD_DROP`) are read nowhere in either build.** They are dead
constants on both sides, not a porting gap.

## 2. Catalog parity gap: `uber_sheep`

Comparing the `ContentCatalog` string constant pools directly:

| | Result |
|---|---|
| In the reference, not here | **`uber_sheep`** |
| Here, not in the reference | `grass_field` (our Stage 1 background, from the background-tile work) |

The reference carries 11 items; this build has 10. The missing entry, read from its bytecode:

```
ldc  "uber_sheep"        // id
ldc  "Uber Sheep"        // name
iconst_0                // sprite index
bipush 9                // effect  = EFFECT_FOOD_DROP
bipush 10               // value
iconst_5 / iconst_4     // extra / maxLevel
ldc  "bullet"           // sprite
```

### Not added, and why

Its only effect is `FOOD_DROP` (9), which **the reference does not implement either**. So
`uber_sheep` is an inert item there: obtainable, consumes an inventory slot, does nothing.

Adding it here would not restore behaviour — it would add an eleventh no-op item and, because
`fillOffer` rolls over `ITEMS`, it would dilute the offer probabilities of all ten working items. A
change with no upside and a real behavioural cost is not one to make automatically.

**This needs a human decision.** Either the item is dropped as an unimplemented upstream stub, or it
is added together with a real `FOOD_DROP` effect — which would itself need a designed value.

---

## 3. Behaviour verified

| Area | Result |
|---|---|
| Attack / speed / pickup / crit / exp items | Each moves its own field by `value × level` and no other |
| Max-HP item | Raises the ceiling by `value × level`; leaves current HP alone while under it |
| Stacking | Two level-1 copies equal one level-2 copy |
| Level cap | `applyOffer` stops at `maxLevel` and adds no second copy |
| Slot limit | Full inventory refuses quietly: no extra slot, no exception |
| Invalid index | `addItem(-1, …)` ignored |
| `recomputeStats` | Rebuilds every bonus from the inventory; five recomputes change nothing; dropping the item zeroes it |
| `NEGATE` | Blocks damage on some seeds and not others, grants a shield charge, and never fires without the item |
| HP clamp | HP above a shrunken ceiling is pulled down |
| Endless mode | `playerMaxHp` pinned to 1 regardless of max-HP items |

Test 1 asserts that **every** catalog item's effect has a live handler — the check that would catch
a lost or newly added item whose effect nothing consumes.

The already-known `applyStat` → `recomputeStats` ordering quirk is inherited from the reference,
already pinned by `RunWorldOfferOnceTest`, and deliberately not restated or changed here. Item
bonuses are unaffected by it because they originate inside `recomputeStats`.

---

## 4. Tests

`tools/tests/me/holojava/fullgame/RunWorldItemTest.java` — 136 checks.

### Negative controls — all thirteen detected

| Control | Mutation | Result |
|---|---|---|
| I1–I6 | each of attack / speed / pickup / crit / exp / max-HP removed | FAIL, 1–3 each |
| I7 | three of the seven recompute resets removed | FAIL, 2 |
| I8 | level scaling removed | FAIL, 2 |
| I9 | slot limit raised to 9 | FAIL, 1 |
| I10 | item level cap ignored | FAIL, 1 |
| I11 | negate check removed from `damagePlayer` | FAIL, 1 |
| I12 | HP no longer clamped to the new maximum | FAIL, 1 |
| I13 | endless-mode max-HP override removed | FAIL, 1 |

Two controls initially passed and exposed blind tests, both fixed:

- **I7** removed three of the seven reset statements, but the rebuild test only watched the crit
  field, whose reset survived. The test now equips one item per percentage effect and recomputes
  five times, so a missing reset doubles a non-zero field instead of being invisible.
- **I9** originally aborted the run with an out-of-bounds write rather than reporting a failure.
  The overfill loop now catches and counts escaping errors, so it reports as a clean failed check.

The first version of the negate test also asserted a guaranteed block, which is wrong: the check is
`rand(100) < d.value`, a real probability. It now sweeps 60 fixed seeds and requires both a block
and a non-block, plus a control sweep proving the same seeds never block without the item.

---

## 5. Limitations

- Item drop tables come from `damageEnemy`'s `rand` rolls, which are covered in
  `RunWorldSpawnScheduleTest` and `RunWorldEventOnceTest` only incidentally; the probability per
  tier is not asserted here.
- Pickup range uses `pickupPercentBonus`, verified only as a field change, not by measuring the
  magnet radius in play.
- `EFFECT_REGEN` and `EFFECT_HASTE_PCT` have engine handlers but no catalog item, so they cannot be
  exercised through the real catalog and are untested.
- No wiki data was used: `holocure.wiki.gg` returned an anti-bot block from this environment, so no
  HoloCure item values back anything here. Every asserted value comes from the reference bytecode
  or the HoloJava catalog.
- No emulator or handset run was performed.

---

## 6. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldItemTest.java   (new, 136 checks)
```

No production source changed; the JAR is byte-identical in size to the previous commit.

Full suite: **1295 checks, 0 failures** across fifteen suites. Build exit 0, verifier
`PASS: JAR=836627 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.

---

## 7. Decisions needed

1. **`uber_sheep`** — drop it as an unimplemented upstream stub, or add it together with a real
   `FOOD_DROP` effect? Recommendation: drop it, since adding it dilutes ten working items for no
   gameplay gain.
2. **Weapon attack gate** (carried forward from the weapon report) — keep hold-to-attack, or match
   the reference's auto-fire?