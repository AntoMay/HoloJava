# Collab Weapon Effects Audit (READ-ONLY)

| Item | Value |
|------|-------|
| Reference checkpoint | `65dca7af7a972a2a597d2f70213d4af40ac6215d` |
| Tracked files at start | clean |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Prior report | `reports/skill-collab-audit/00_AUDIT_REPORT.md` |

**No code, CSV, asset, test, or configuration was modified. No commit was made.**

---

## 1. Summary Table — Five Collab Weapons

Field order per `ContentCatalog.weapon(...)` factory, `ContentCatalog.java:321-323`:
`id, name, category, type, pattern, cooldownMs, damage, count, speed, range, area, knockback, weight, maxLevel, owner, sprite, preA, preB`

| Id | Name | type | pattern | cd | dmg | count | speed | range | area | knock | maxLvl | sprite |
|----|------|------|---------|----|----|-------|-------|-------|------|--------|-------|--------|
| `absolute_wall` | Absolute Wall | MELEE | **WALL** | 1600 | 6 | 1 | **0** | 46 | 38 | 8 | 7 | axe |
| `bl_fujoshi` | BL Fujoshi | MELEE | MELEE_ARC | 1300 | 6 | 1 | 0 | 42 | 30 | 7 | 7 | bullet |
| `black_plague` | Black Plague | MULTISHOT | AOE | 1500 | 5 | 2 | 4 | 115 | 24 | 3 | 7 | bomb |
| `blood_lust` | Blood Lust | MELEE | MELEE_ARC | 1000 | 7 | **2** | 0 | 48 | 30 | 8 | 7 | scythe |
| `legendary_sausage` | Legendary Sausage | MELEE | ORBIT | 900 | 5 | 2 | 5 | 40 | 14 | 4 | 7 | bullet |

Source: `ContentCatalog.java:202-206`.

---

## 2. Trigger → Effect Flow

```
generateOffers()  RunWorld.java:1208-1218
  └─ requires: weaponCount < 6 && mode.allowsCollabs
     && weaponLevelById(preA) == result.maxLevel (7)
     && weaponLevelById(preB) == result.maxLevel (7)
        └─ offer OFFER_WEAPON, collab = true

selectOffer()     RunWorld.java:1267-1270
  └─ removeWeaponById(preA); removeWeaponById(preB); addWeapon(result, 1)

updateWeapons()   RunWorld.java:697-709
  └─ per slot: if now < weaponReadyAt → skip
     autonomous = (pattern == ORBIT)
     if (!attackPressed && !autonomous) → skip        ← all but Legendary Sausage need FIRE
     fireWeapon(slot, def, now)
     cooldown = max(80, cooldownMs * 100 / max(50, haste))
     haste = 100 + hastePercent + (level-1)*5

fireWeapon()      RunWorld.java:711-771
  damage = def.damage + max(0, level-1)
  damage = damage * (100 + atkPercent + atkBonus) / 100
  crit = rand(100) < clamp(critPercent + critBonus, 0, 75);  if crit damage *= 2
  count = def.count + (level >= 5 ? 1 : 0)
  ├─ pattern == MELEE_ARC  → createMelee(); return      ← bl_fujoshi, blood_lust
  └─ else → per-shot projectile setup                   ← absolute_wall, black_plague, legendary_sausage

createMelee()     RunWorld.java:778-793   (160 ms lifetime, hitbox = area × area)
updateMelee()     RunWorld.java:795-812
updateShots()     RunWorld.java:817-879   (movement, expiry, collision)
explode()         RunWorld.java:881-893   (AOE radial damage, knockback 4)
```

---

## 3. Per-Weapon Effect Verification

### 3.1 Absolute Wall — `absolute_wall`, `PATTERN_WALL`, `speed = 0`

**Proven finding.** `PATTERN_WALL` is declared at `ContentCatalog.java:24` and assigned to two weapons (`cutting_board:197`, `absolute_wall:202`), but it is **never branched on anywhere in `RunWorld.java`**. The complete set of patterns the runtime handles: `ORBIT` (702, 738), `MELEE_ARC` (718), `HOMING` (752, 826), `BOOMERANG` (838), `AOE` (847, 859, 871, 894), `PIERCING` (860, 868), `PROJECTILE` (1051, enemy shots only).

`absolute_wall` therefore falls through to the generic projectile path. At `RunWorld.java:769-770`:

```java
s.dx = (int) (dx / len * def.speed);   // def.speed == 0  →  s.dx = 0
s.dy = (int) (dy / len * def.speed);   //                  →  s.dy = 0
```

and `:771` sets `s.x = ox; s.y = oy;` — the spawn point, i.e. the player position (`:748-749`).

Result: a **stationary** hitbox at the player's position. It moves never (`:837` `s.x += s.dx` with `dx = 0`), lives `max(8, 46/3) = 15` frames (`:734`), and expires at `:846` — no `explode()`, because the pattern is not `AOE`.

**What actually happens:** up to 15 frames of collision damage at the player's own position, once per 1600 ms cooldown. **No wall is ever created.**

`cutting_board` (a prerequisite, and itself `PATTERN_WALL`) has `speed = 5`, so it does travel — as an ordinary projectile. The missing pattern affects it only in that it gets no special behaviour.

### 3.2 BL Fujoshi — `bl_fujoshi`, `PATTERN_MELEE_ARC`

| Field | Used? | Where |
|-------|-------|-------|
| `damage = 6` | ✅ | `:713-716` |
| `cooldownMs = 1300` | ✅ | `:706` |
| `area = 30` | ✅ | `:783` → `updateMelee` hitbox `m.area × m.area` |
| `knockback = 7` | ✅ | `:783` → `damageEnemy(...)` |
| `range = 42` | ❌ **dead** | written `:783`, never read |
| `count = 1` | n/a | `fireWeapon` returns at `:718` before `count` is used |
| `type = MELEE` | ❌ **dead** | never read |
| `weight = 0` | ❌ dead | never read |
| `sprite = bullet` | ❌ dead for melee | `createMelee` sets no sprite |

Effect: a 30×30 square hitbox centred on the player for 160 ms, `hitMask` preventing repeat hits on the same enemy, boss hit limited by `bossHit`.

### 3.3 Blood Lust — `blood_lust`, `PATTERN_MELEE_ARC`

Structurally identical to BL Fujoshi.

| Field | Used? |
|-------|-------|
| `damage = 7` | ✅ |
| `cooldownMs = 1000` | ✅ |
| `area = 30` | ✅ |
| `knockback = 8` | ✅ |
| `range = 48` | ❌ **dead** |
| **`count = 2`** | ❌ **dead** — `fireWeapon:718` returns before `count` is read |

Because both use `area = 30`, **the two melee collabs have an identical hitbox** despite different `range` values (42 vs 48). Their real differences are damage (6 vs 7), cooldown (1300 vs 1000), knockback (7 vs 8).

### 3.4 Black Plague — `black_plague`, `PATTERN_AOE`

| Field | Used? | Where |
|-------|-------|-------|
| `damage = 5` | ✅ | `:713-716` |
| `cooldownMs = 1500` | ✅ | `:706` |
| `count = 2` | ✅ | `:722` — 2 projectiles, spread `(i - 0.5) * 0.16` rad (`:761`) |
| `speed = 4` | ✅ | `:769-770` |
| `area = 24` | ✅ | `:733` → `explode(s.x, s.y, s.area, …)` |
| `range = 115` | ✅ | `:734` → `life = max(8, 115/3) = 38` frames |
| `knockback = 3` | ✅ | `:732` → direct hit; `explode` uses its own hardcoded **4** |

Collision flow on first enemy contact (`:856-871`): `:859` calls `explode(..., damage = 0, ...)` — which is a no-op because `explode` guards `if (damage > 0)` (`:882`) — then sets `remove = true`, then `:871` performs the real `explode(..., s.damage, s.critical)`.

**No double damage.** The zero-damage explosion only marks the blast. Net: one radial hit of `s.damage` within radius 24, knockback 4.

### 3.5 Legendary Sausage — `legendary_sausage`, `PATTERN_ORBIT`

| Field | Used? | Where |
|-------|-------|-------|
| `damage = 5` | ✅ | `:713-716` |
| `cooldownMs = 900` | ✅ | `:706` |
| `count = 2` | ✅ | `:722`, angles `(2π·i/2) + now/900` (`:743`) |
| `area` | ❌ **dead** | ORBIT never calls `explode` |
| `range = 40` | ❌ **dead** — overridden | `:734` sets `life = max(8, 13) = 13`, but `:746` overwrites with `life = 100 + level*8` |
| `speed = 5` | ❌ **dead** | attached shots never integrate `dx/dy` (`:821-824` recomputes position from `orbitAngle`) |

Effect: two blades orbiting the player at radius 30 (`:823-824`), `hitMask` preventing repeat hits on the same enemy, boss limited by `bossHit`. Autofiring — `updateWeapons:702-703` exempts `ORBIT` from the `attackPressed` requirement.

---

## 4. Double-Fire / Cooldown Verification

| Check | Result |
|-------|--------|
| One fire per trigger | ✅ `updateWeapons:698-708` loops slots once; `weaponReadyAt[s]` set after each fire |
| Cooldown honoured | ✅ `:700` `if (wid < 0 \|\| now < weaponReadyAt[s]) continue;` |
| Haste scaling | ✅ `:705-706` `max(80, cooldownMs * 100 / max(50, haste))`, floor 80 ms |
| Melee double-hit | ✅ `hitMask` (`:808`) and `bossHit` (`:809`) |
| Shot double-hit | ✅ `hitMask` (`:855`), `bossHit` (`:867`) |
| AOE double damage | ✅ zero-damage explode at `:859` is a no-op; real explode once at `:871` |
| Level scaling | ✅ `damage + (level-1)` at `:713`; `count + 1` at level ≥ 5 (`:717`) |

**No unintended damage multiplication found.**

---

## 5. Prerequisite Consistency

| Result | `prerequisiteA` / `B` | `CollabDef` (runtime) | `collabs.csv` | 3-way match |
|--------|----------------------|-----------------------|---------------|-------------|
| `absolute_wall` | `bounce_ball`, `cutting_board` | same | Bounce Ball / Cutting Board | ✅ |
| `bl_fujoshi` | `bl_book`, `psycho_axe` | same | BL Book / Psycho Axe | ✅ |
| `black_plague` | `ens_curse`, `owl_dagger` | same | EN's Curse / Owl Dagger | ✅ |
| `blood_lust` | `owl_dagger`, `psycho_axe` | same | Owl Dagger / Psycho Axe | ✅ |
| `legendary_sausage` | `bl_book`, `sausage` | same | BL Book / Sausage | ✅ |

All seven distinct materials exist as category-0 weapons (`ContentCatalog.java:195-203`) and are randomly offered (`RunWorld.java:1220-1228`), so every recipe is reachable.

---

## 6. Proven Findings

| # | Finding | Severity | Evidence |
|---|---------|----------|----------|
| **C1** | **`PATTERN_WALL` is declared and assigned but never implemented.** `absolute_wall` degenerates into a stationary 15-frame hitbox at the player's own position; no wall is created | **MEDIUM** | `ContentCatalog.java:24,202`; `RunWorld.java` has no `PATTERN_WALL` branch; `:769-771` speed 0 |
| **C2** | `Melee.range` written but never read; both melee collabs share an identical `area`-based hitbox despite different `range` | LOW | `RunWorld.java:783` write; `updateMelee` uses `m.area` only |
| **C3** | `blood_lust.count = 2` is dead — `fireWeapon` returns at `:718` before `count` is read | LOW | `RunWorld.java:718` |
| **C4** | `WeaponDef.type` never read anywhere in `src/` | LOW | `WEAPON_MELEE`/`MULTISHOT`/`RANGED` absent from `RunWorld.java` |
| **C5** | `Melee.weaponIndex = def.id.hashCode()` is write-only dead data | LOW | `RunWorld.java:785`; no read of `m.weaponIndex` |
| **C6** | `legendary_sausage.range = 40` is overridden by a hardcoded `life` formula | LOW | `RunWorld.java:734` then `:746` |
| **C7** | AOE knockback differs: direct hit uses `def.knockback` (3), explosion uses hardcoded 4 | INFO | `RunWorld.java:732` vs `:882-885` |

### Correction to the prior audit

`reports/skill-collab-audit/00_AUDIT_REPORT.md` §B.4 listed `absolute_wall` as using a *":760-series wall pattern"*. **That was incorrect** — no wall pattern handling exists at or near that line. The accurate statement is C1 above.

---

## 7. Not Determined

| # | Item |
|---|--------|
| U1 | **Whether C1 is a defect or an accepted approximation.** `absolute_wall` has `speed = 0` and `PATTERN_WALL`; either the pattern was planned and never implemented, or the weapon was deliberately left as a short-range self-centered burst. No comment, doc, or history note was found either way, and no local reference-game data was consulted beyond `data/wiki/collabs.csv`, which records only names and prerequisites — no effect description. |
| U2 | Whether `range` was intended to govern melee reach instead of `area`. Both collabs share `area = 30` while `range` differs, suggesting one of the two was meant to drive reach. |
| U3 | Balance — no runtime execution permitted, so only literal code behaviour is described. |
| U4 | Whether upstream HoloCure defines a wall mechanic; no live wiki comparison was made in this audit. |

---

## 8. Risk and Smallest Fixes

| Finding | Risk | Smallest fix | Change |
|---------|------|--------------|--------|
| **C1** | MEDIUM | Either implement a `PATTERN_WALL` branch, or change `absolute_wall` to a pattern that exists (`PATTERN_WALL` → e.g. `PATTERN_PIERCING`) and give it a non-zero `speed` | 1 catalog line, or a new branch. **Requires a design decision** — U1 is unresolved |
| C2 | LOW | Delete the `m.range = def.range;` assignment | 1 line, cosmetic |
| C3 | LOW | Set `count = 1` to match behaviour | 1 value |
| C4 | LOW | Remove `type` from `WeaponDef` and the three constants | ~5 lines, cosmetic |
| C5 | LOW | Remove the dead assignment | 1 line |
| C6 | LOW | Remove the now-dead `def.range` read at `:734` for ORBIT, or document the override | 1 line |
| C7 | INFO | None — behaviour is intentional-looking | — |

**C1 is the only finding with user-visible gameplay consequence.** It is not a crash and not a data-loss issue: the weapon still functions, it simply does not create a wall. Because U1 is unresolved, this audit does **not** classify it as a bug, and recommends no change without a product decision.

---

## 9. Conclusion

All five collab weapons have complete definitions, all are reachable through the offer/selection path, all resolve their prerequisites consistently with both `CollabDef` and `collabs.csv`, and none multiplies damage or ignores its cooldown.

**One behavioural gap is proven**: `PATTERN_WALL` has no implementation, so `absolute_wall` cannot create a wall. Whether that is a defect or an accepted approximation **cannot be determined from available local evidence** — no comment, doc, or reference data describes the intended behaviour.

Aside from that, the remaining findings are dead metadata fields with no gameplay consequence.

**No bug is proven to require a code change. No changes were made. No commit was made.**

---

## 10. Final Git Status

| Item | Value |
|------|-------|
| HEAD | `65dca7a` — unchanged |
| Tracked file modifications | **none** |
| Commit | none |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/collab-effects-audit/00_AUDIT_REPORT.md` |
