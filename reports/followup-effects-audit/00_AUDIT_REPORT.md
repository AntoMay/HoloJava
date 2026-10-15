# Follow-up Effects Audit — `defensePercent` and `uber_sheep` (READ-ONLY)

| Item | Value |
|------|-------|
| Checkpoint | `65dca7a` (`Migrate legacy save progression layout`) |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Working tree | `RunWorld.java` modified (EXP overflow fix) — **preserved, untouched** |

**No file was changed by this audit. No build was run — none was needed, since every question is answerable by reading source and the shipped bytecode.**

**EXP overflow fix status: INTACT.** Verified present in the working tree before and after this audit at `RunWorld.java:1095` (`p.value + value <= 200`) with no `Math.min(200, …)` remaining in `dropPickup`. This audit did not modify, revert, or overwrite it.

Line numbers below reflect the working tree (the EXP fix shifted lines after ~1086 upward by +4).

---

## 1. `defensePercent`

### 1.1 Complete lifecycle — 3 occurrences, 0 writers

| # | Line | Kind | Code |
|---|------|------|------|
| 1 | `RunWorld.java:93` | declaration | `private int defensePercent;` |
| 2 | `RunWorld.java:972` | **read** | `int dealt = Math.max(1, amount * (100 - defensePercent) / 100);` |
| 3 | `RunWorld.java:1129` | reset | `… expPercentBonus = 0; defensePercent = 0;` |

`grep -rn "defensePercent" src/` returns exactly these three. There is **no** `+=`, no `= <positive>`, and no external assignment anywhere in the tree.

### 1.2 Can anything raise it? — No

| Candidate path | Evidence | Result |
|----------------|----------|--------|
| Item effect | `recomputeStats` `:1131-1141` matches 8 effect constants: `DAMAGE_PCT`, `HASTE_PCT`, `SPEED_PCT`, `MAX_HP`, `PICKUP_PCT`, `CRIT_PCT`, `EXP_PCT`, `REGEN`. None assigns `defensePercent` | ❌ |
| Level-up stat | `applyStat` `:1296-1302` handles stat 0–5: maxHp, atk, speed, crit, pickup, haste. No defense branch | ❌ |
| Skill / Special | `activateSkill` `:387-403`, `activateSpecial` `:405-421` — none touch it | ❌ |
| Character definition | `CharacterDef` has no defense field (`ContentCatalog.java:48-63`) | ❌ |
| **Constant existence** | `EFFECT_*` constants are `0..10` (`ContentCatalog.java:36-46`): `DAMAGE_PCT, HASTE_PCT, SPEED_PCT, MAX_HP, PICKUP_PCT, CRIT_PCT, REGEN, SHIELD, NEGATE, FOOD_DROP, EXP_PCT`. **There is no `EFFECT_DEFENSE` constant at all** | ❌ |

Java default-initialises `int` fields to `0`, so the field is `0` from construction and is reset to `0` on every stat recompute.

### 1.3 Is it player-visible? — No

`RunWorld.java:972` evaluates to `Math.max(1, amount * 100 / 100)` = `Math.max(1, amount)`.

`amount` at every call site is ≥ 1:

| Call site | Source of `amount` | Minimum |
|-----------|---------------------|---------|
| `:535`, `:554` | `e.atk` | 4 |
| `:660`, `:675` | `boss.atk` | 16 |
| `:875` | `s.damage` (enemy shot, from `e.atk` / `boss.atk`) | 4 |
| boss shot `:632`, `:664` | `boss.atk` | 16 |

Catalog minimums: enemy `atk` 4, boss `atk` 16.

Because `amount ≥ 1`, `Math.max(1, amount)` is identical to `amount`. The line is **functionally transparent** — a player cannot observe any difference.

### 1.4 Verdict

| | |
|---|---|
| **Status** | `METADATA SAJA` — inactive leftover field |
| **Player-visible bug?** | **No** |
| **Risk** | **None** — provably a no-op at every call site |
| **Nature** | Dead scaffolding. The consuming line at `:972` is itself inert |

---

## 2. `uber_sheep` / `EFFECT_FOOD_DROP`

### 2.1 Definition

`ContentCatalog.java:222`:
```java
item("uber_sheep", "Uber Sheep", 0, EFFECT_FOOD_DROP, 10, 5, 4, "bullet")
```

Field order per `:333-334`: `id, name, category, effect, value, maxLevel, weight, sprite`.

| Field | Value |
|-------|-------|
| category | `0` (healing) |
| effect | `EFFECT_FOOD_DROP` (= `9`, `ContentCatalog.java:45`) |
| value | `10` |
| maxLevel | `5` |
| weight | `4` |
| sprite | `bullet` |

### 2.2 Effect consumers — zero

`grep -rn "EFFECT_FOOD_DROP" src/` returns exactly **two** occurrences:

| Line | Kind |
|------|------|
| `ContentCatalog.java:45` | constant declaration |
| `ContentCatalog.java:222` | the `uber_sheep` definition itself |

**No runtime code reads it.** `recomputeStats` (`:1131-1141`) never matches it, and no other method inspects `d.effect == EFFECT_FOOD_DROP`.

### 2.3 Can it be obtained? — Yes

| Question | Evidence | Answer |
|----------|----------|--------|
| Is it in `ITEMS`? | `ContentCatalog.java:199-223` | ✅ yes, 1 of 11 |
| Offered on level-up? | `RunWorld.java:1248-1253` iterates the **full** `ContentCatalog.ITEMS` pool randomly, no chance gate | ✅ yes |
| Held in a slot? | `addItem` `:1105-1111`, gated only by `itemCount < 6` | ✅ up to 6 slots |
| Level-ups possible? | `:1279-1281` `itemLevels[o.existing] = min(maxLevel, +1)` → up to level 5 | ✅ |
| Dropped by enemies/bosses? | `dropPickup` call sites use types `0` (EXP), `1` (coin), `2` (HP), `3` (magnet), `4` (weapon level). `collectPickup` `:923-947` handles only those five. **No pickup type yields an item** | ❌ items never drop on the ground |

So `uber_sheep` reaches the player purely through level-up offers.

### 2.4 What happens once held

`recomputeStats` `:1132` computes `int v = d.value * itemLevels[i];` = `10 × level`, then tests the effect against 8 constants. `EFFECT_FOOD_DROP` matches none, so `v` is **computed and discarded**.

Every other `ItemDef` field is equally inert for this item: `sprite` is never used for item rendering, `weight` and `category` have no consumer in `RunWorld`.

### 2.5 Verdict

| | |
|---|---|
| **Status** | **Registered, obtainable, and completely inert** |
| **Classification** | *Active-but-nonfunctional* — distinct from "listed but inactive" |
| **Player-visible bug?** | **Yes** — the player can be offered "Uber Sheep", take it, level it to 5, and observe zero effect. Unlike `defensePercent`, this one reaches the player. |
| **Risk** | **Low–Medium** — no crash or data loss; the harm is an empty offer slot and a misleading level-up screen entry |

---

## 3. Comparison of the Two Findings

| | `defensePercent` | `uber_sheep` / `EFFECT_FOOD_DROP` |
|---|---|---|
| Reaches the player? | No — field always 0, consuming line is a no-op | **Yes** — offered and holdable |
| Visible effect of absence | None | Wasted level-up offer slot |
| Can be triggered by normal play? | N/A | Yes, any run |
| Crash / data loss risk | None | None |
| Root cause | No `EFFECT_DEFENSE` constant was ever defined | Constant defined, consumer never written |

---

## 4. Recommendations (minimal)

| # | Finding | Recommendation | Risk of doing it |
|---|---------|----------------|------------------|
| **1** | `EFFECT_FOOD_DROP` has no consumer | Implement the drop-food behaviour, **or** remove `uber_sheep` from `ITEMS` (`ContentCatalog.java:222`). Removing is one line and stops offering a blank item. | LOW — removing deletes an item players may already hold; `recomputeStats` ignores it either way, so no state migration is needed |
| **2** | `defensePercent` is inert | No player-facing action needed. Either delete the field plus its two uses (`:93`, `:972`, `:1129`), or leave it as an unfinished hook. **No urgency.** | NONE — currently provably a no-op |

Neither recommendation requires touching drop probabilities, item values, the pickup pool, or any other mechanic.

---

## 5. Explicitly Not Done

- `defensePercent` was **not** wired to anything and **not** removed.
- `uber_sheep` was **not** removed and `EFFECT_FOOD_DROP` was **not** implemented.
- The EXP overflow fix in `RunWorld.java:1095` was **not** modified, reverted, or reformatted.
- No build, verifier, game, MIDlet, or emulator was run — unnecessary for a read-only source audit.
- No commit was made.

---

## 6. Final Status

| Item | Value |
|------|-------|
| HEAD | `65dca7a` — unchanged |
| Tracked modifications | `RunWorld.java` only — the pre-existing EXP fix, byte-identical to before this audit |
| Commit | none |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/followup-effects-audit/00_AUDIT_REPORT.md` |
