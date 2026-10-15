# Gameplay Effects Audit — READ-ONLY

| Item | Value |
|------|-------|
| Checkpoint | `65dca7a` (`Migrate legacy save progression layout`) |
| Tracked files at start | clean |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |

**No file was changed. No game, MIDlet, or emulator was run. Nothing was downloaded or committed.**

Primary source: `src/java-me-fullgame/me/holojava/fullgame/RunWorld.java` (cited as `RunWorld`), `ContentCatalog.java` (cited as `Catalog`), `FullGameCanvas.java` (cited as `Canvas`).

---

## 1. Summary

The combat and progression loop is coherent: every drop has a verified source, every item effect that is declared-and-granted is consumed, and the crit/haste/knockback/slow/lifesteal/invulnerability paths all terminate correctly.

**Five issues found.** Two are statically proven to affect play (`BUG POTENSIAL`): EXP overflow silently discarded at the 200 merge cap, and a permanently-dead damage-reduction field. The rest are dead metadata with no gameplay effect.

No crash, infinite loop, or double-damage path was found.

---

## 2. Mechanics Table

### 2.1 Character skills and specials

Triggers: key `5` → skill (`Canvas:107`), key `9` → special (`Canvas:108`). Skill requires `skillAcquired`; special has no gate (`RunWorld:383-384`, init `:185`).

| Character | Skill | Effect applied | Special | Effect applied | Status |
|-----------|-------|----------------|---------|----------------|--------|
| Gura | Shark Bite | `damageEnemiesInRadius(70, 6, true)` `:390` | Shark Call | `damageEnemiesInRadius(110, 18, true)` `:408` | `VALID` |
| Ame | Hunter's Instinct | `slowEnemiesUntil = now+3500`; enemy `nextAttack = now+1200` `:392-393` | Slow Time | `slowEnemiesUntil = now+6000` `:410` | `VALID` |
| Calli | Undying | `shield = 20` `:395` | The Reaper | heal `max(8, maxHp/4)` + `lifestealUntil = now+10000` `:412-413` | `VALID` |
| Kiara | Second Flame | `lifestealUntil = now+8000` `:398` | Phoenix Fire | `damageEnemiesInRadius(130, 24, true)` `:415` | `VALID` |
| Ina | The Ancient One | `invulUntil = now+2500` + `damageEnemiesInRadius(45, 4, false)` `:400-401` | Spirit of the Oni | `shield = 30` + `clearEnemyShots()` `:417-419` | `VALID` |

Every assigned state variable was traced to a consumer:

| Variable | Set | **Consumed** | Real effect |
|----------|-----|--------------|-------------|
| `slowEnemiesUntil` | `:392`, `:410` | `:498` `speed = e.speed * (slow ? 0.35 : 1.0)`; `:618` boss speed ×0.35 | yes |
| `shield` | `:395`, `:417`, `:961`, `:971` | `:973-975` `absorbed = min(shield, dealt); shield -= absorbed` | yes |
| `shieldReadyAt` | `:396`, `:418` | `:961` recurring refresh to 15 | yes |
| `lifestealUntil` | `:398`, `:413` | `:997` `+1 HP` **per enemy kill** | yes |
| `invulUntil` | `:400`, `:978` | `:981` `playerInvulnerable(now)`; guards `:551`, `:674` | yes |
| `clearEnemyShots()` | `:419` | `:1058-1060` deactivates non-player shots | yes |

All ten effects are genuine gameplay, not labels.

### 2.2 HP recovery

| Source | Location | Amount | Cap behaviour |
|--------|----------|--------|---------------|
| Run start | `:211` | `playerHp = playerMaxHp` | full |
| Pickup type 2 (HP drop) | `:939` | `min(maxHp, hp + max(5, maxHp/5))` | clamped to max |
| OFFER_FOOD | `:1286` | `min(maxHp, hp + max(1, maxHp/5))` | clamped |
| Lifesteal | `:997` | `+1` per enemy kill | clamped |
| Calli special | `:412` | `min(maxHp, hp + max(8, maxHp/4))` | clamped |
| Max-HP increase | `:1293` | `baseMaxHp += max(1, charHp/10)` — **does not heal** | HP ratio drops |
| Clamp after recompute | `:1143` | `if (playerHp > playerMaxHp) playerHp = playerMaxHp` | clamp |

**Level-up does not heal** — `collectPickup` `:924-931` only increments `playerLevel` and switches state. No HP write.

### 2.3 Drops and pickups

Pool: `PICKUP_POOL = 96` (`:32`, `:46`). Pickups expire after `30 000 ms` (`:906`). Magnet type 3 auto-collects within `distSq < 900`.

| Type | Meaning | Source | Condition |
|------|---------|--------|-----------|
| 0 | EXP | `:685` boss, `:992` enemy | on every boss/enemy death |
| 1 | Coin | `:686` boss ×5 = 10 each | boss death; also `:993` `rand(coinChance)==0` |
| 2 | HP | `:994` | `rand(200) == 0` |
| 3 | Magnet | `:996` | `rand(3000) == 0` |
| 4 | Weapon level-up | `:995` | `rand(1301) == 0` |

`rand(n)` = `(random.nextInt() & 0x7fffffff) % n` (`:1042`), so each condition is exactly `1/n`.

EXP values: enemies `7–20` (`Catalog:225-240`), bosses `40–180` (`Catalog:241-257`).

### 2.4 Items

11 items (`Catalog:199-211`). All are granted via OFFER_ITEM (`:1238`, `:1250`) — random from the full pool, never chance-gated.

| Effect const | Items | Applied at | Status |
|--------------|--------|-----------|--------|
| `EFFECT_DAMAGE_PCT` | face_mask, gorillas_paw | `:1132` `atkPercentBonus += v` | `VALID` |
| `EFFECT_PICKUP_PCT` | knightly_milk, limiter | `:1136` | `VALID` |
| `EFFECT_SPEED_PCT` | energy_drink | `:1134` | `VALID` |
| `EFFECT_MAX_HP` | just_bandage, piki_piki | `:1135` | `VALID` |
| `EFFECT_EXP_PCT` | study_glasses | `:1138` | `VALID` |
| `EFFECT_CRIT_PCT` | sake | `:1137` | `VALID` |
| `EFFECT_NEGATE` | headphones | `:969` `rand(100) < d.value` → negates hit, grants `shield = max(shield,1)` | `VALID` |
| `EFFECT_FOOD_DROP` | uber_sheep | **never read** | `METADATA SAJA` |
| `EFFECT_REGEN` | *no item* | `:959` `regen && …` — flag never set | `METADATA SAJA` |
| `EFFECT_SHIELD` | *no item* | **never read** | `METADATA SAJA` |

`ItemDef.sprite` is stored but never used for pickup rendering (`RunWorld` reads `def.sprite` only for shots).

### 2.5 Combat and status

| Mechanic | Location | Behaviour | Status |
|----------|----------|-----------|--------|
| Critical | `:715-716` | `rand(100) < clamp(crit + bonus, 0, 75)` → `damage *= 2` | `VALID` |
| Haste | `:705-706` | `max(80, cooldownMs * 100 / max(50, haste))`; floor 80 ms | `VALID` — affects **weapon cooldown only**, not movement |
| Knockback | `applyKnockback` | `strength * resist * 0.7`, applied for 240 ms | `VALID` |
| Enemy slow | `:498`, `:618` | enemy/boss speed ×0.35 | `VALID` |
| Invulnerability | `:978`, `:981` | 800 ms after any hit | `VALID`, terminates |
| Lifesteal | `:997` | +1 HP per kill during window | `VALID`, window-bounded |
| Enemy shots | `:1045-1056` | `critical = false`, `knockback = 0`, `life = 120`, sprite `bullet` | `VALID` |
| Shield refresh | `:961` | once `shieldReadyAt` passes, shield → 15, next in 15 s | `VALID`, self-limiting |
| Damage reduction | `:972` | `amount * (100 - defensePercent) / 100` | `BUG POTENSIAL` — see §3.2 |

---

## 3. Proven Findings

### 3.1 EXP overflow silently discarded at the merge cap — `BUG POTENSIAL`

`RunWorld.java:1086-1097`:

```java
if (type == 0) {
    for (int i = 0; i < pickups.length; i++) {
        Pickup p = pickups[i];
        if (p.active && p.type == 0 && Math.abs(p.x - x) < 8 && Math.abs(p.y - y) < 8 && p.value < 200) {
            p.value = Math.min(200, p.value + value);
            return;                      // ← returns regardless of overflow
        }
    }
}
```

**Code path**: enemy A dies → `:992` drops EXP `v1` → pickup `P` created. Enemy B dies within 8 px while `P` is still active → merge branch taken → `P.value = min(200, v1+v2)` → `return`.

If `v1 + v2 > 200`, the excess is **dropped with no replacement pickup**. Example: `P.value = 190`, enemy EXP 18 → `P.value = 200`, **8 EXP lost**.

**Reachability**: enemies spawn in waves (`stage.spawnIntervalMs` 650–1300, `maxEnemies` 20–40) and drops persist 30 s. Values are 7–20 (enemy) and 40–180 (boss), so reaching the 200 cap requires roughly 11–25 co-located merges, or a boss drop merging into an existing pickup. Both are plausible in a dense wave but were not observed — **no runtime was executed**.

Note `:923` `if (p.value >= 200) { /* documented red tier retained */ }` shows 200 is a deliberate tier boundary, so the cap itself is intentional; only the **overflow** is unrecoverable.

### 3.2 `defensePercent` is structurally always zero — `BUG POTENSIAL`

`RunWorld.java:93` declares it, `:972` consumes it, `:1125` resets it to 0.

**Code path**: `damagePlayer` (`:964`) → `int dealt = Math.max(1, amount * (100 - defensePercent) / 100);`

grep shows exactly three occurrences — declaration, this read, and the reset. **Nothing ever increments it.** No `applyStat` branch (`:1292-1299`) sets it; no item effect maps to it. The damage-reduction step is therefore always `amount * 100 / 100`, i.e. a no-op, while still charging a `Math.max(1, …)` floor.

This is a *configured-but-unreachable* mechanic, not an active bug.

### 3.3 `EFFECT_REGEN` unreachable — `METADATA SAJA`

`:959` `if (regen && playerHp < playerMaxHp) playerHp = min(maxHp, hp + 2);` is gated on `regen`, set only at `:1139` for `EFFECT_REGEN`. No item declares that constant (`Catalog:199-211` uses DAMAGE, PICKUP, SPEED, MAX_HP, EXP, CRIT, NEGATE, FOOD_DROP). Dead branch.

### 3.4 `EFFECT_SHIELD` and `EFFECT_FOOD_DROP` declared, never applied — `METADATA SAJA`

`EFFECT_SHIELD` (`Catalog:43`) has zero readers in `RunWorld`. `EFFECT_FOOD_DROP` (`Catalog:45`) is set by `uber_sheep` (`:210`) but has zero readers — so that item is granted in runs and does nothing.

### 3.5 Dead offer types and empty branch — `METADATA SAJA`

| Item | Location | Finding |
|------|----------|---------|
| `OFFER_REROLL` (6), `OFFER_HOLD` (7) | `:26-27` | Handled at `:274`, `:286`, `:294-300` but **never generated** — `offers[slot].type = OFFER_*` only ever emits SKILL, STAT, WEAPON, ITEM, COINS, FOOD |
| `RunWorld:361` | `if (now < slowEnemiesUntil) { /* comment */ }` | Empty body; documents that the slow affects enemies, not the player |
| `rerolls` | `:186` | `mode.standardLoadout ? 10 : 0` — never decremented, no reroll offer exists |

---

## 4. Verified Good — No Defect Found

| Check | Result |
|-------|--------|
| Double damage (melee) | `hitMask` `:808` + `bossHit` `:809` |
| Double damage (shot) | `hitMask` `:855` + `bossHit` `:867` |
| AOE double explode | `:859` uses `damage = 0` and `explode` guards `if (damage > 0)` `:882`; real explode once at `:871` |
| Effect never ends | `invulUntil`, `slowEnemiesUntil`, `lifestealUntil`, `shieldReadyAt` all absolute timestamps compared against `now` |
| Cooldown broken | `weaponReadyAt[s]` set after every fire `:707`, checked `:700` |
| Level-up loop | `while (experience >= expNeed && state == STATE_RUNNING)` `:927` — bounded because `expNeed` grows via `requiredForLevel` `:1310` |
| Pickup pool overflow | pool full → `dropPickup` returns silently `:1099-1101`; not proven reachable (§5) |
| HP above max | clamped `:1143` |
| Crate/index errors | `weaponIndex` returns −1 → guarded `:1106`; `freeEnemy`/`freeShot` return −1 → guarded |

---

## 5. Cannot Be Determined Statically

| # | Item |
|---|--------|
| R1 | Whether the §3.1 EXP overflow actually occurs in play. Requires a runtime or a simulation of wave kill density. |
| R2 | Whether `PICKUP_POOL = 96` can be exhausted. Requires kill-rate measurement; overflow would silently discard drops. |
| R3 | Whether `defensePercent` and `EFFECT_REGEN` were intended to be reachable, or are abandoned scaffolding. No comment or doc states either. |
| R4 | Balance of any value. No specification exists locally to judge against; `collabs.csv`/`characters.csv` carry no effect data. |
| R5 | Whether `OFFER_REROLL`/`OFFER_HOLD` were meant to be wired up. `rerolls = 10` at `:186` suggests intent, but no generator exists. |

---

## 6. Recommendations (max 5, ordered by impact and certainty)

| # | Recommendation | Impact | Certainty |
|---|----------------|--------|-----------|
| **1** | **Stop discarding EXP overflow.** In `dropPickup`, only `return` when the merge fits; otherwise fall through and spawn a new pickup. One-line condition change. | Medium — silent progression loss in dense waves | High that the code loses data; Medium that it happens in practice |
| **2** | **Decide `defensePercent`'s fate.** Either wire it to a stat/item or delete `:93`, `:972`, `:1125`. Currently a no-op that still applies a `max(1, …)` floor. | Low — no gameplay change either way | High (dead by static proof) |
| **3** | **Remove or implement `uber_sheep` / `EFFECT_FOOD_DROP`.** An item players can hold that does nothing is worse than not offering it. | Low–Medium — player expectation | High |
| **4** | **Remove the empty `if` at `RunWorld:361`** and hoist its comment above the line. | Cosmetic | High |
| **5** | **Remove dead `EFFECT_REGEN`, `EFFECT_SHIELD`, `OFFER_REROLL`, `OFFER_HOLD`,** or document them as planned. They currently imply mechanics that do not exist. | Cosmetic / clarity | High |

No recommendation is made about balance values; there is no local specification to compare against, and §5 R4 is explicit about that.

---

## 7. Final Status

| Item | Value |
|------|-------|
| HEAD | `65dca7a` — unchanged |
| Tracked files modified | **none** |
| Commit | none |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/gameplay-effects-audit/00_AUDIT_REPORT.md` |
