# Weapon Execution — Cooldown, Haste, Targeting, Melee, Projectiles

**Date:** 2026-10-15
**Change group:** `updateWeapons`, `fireWeapon`, `createMelee`, projectile flight
**Outcome:** Parity confirmed with one documented divergence. Verified, committed locally.
**No production code changed.**

---

## 1. Parity: 18 of 19 weapon-family methods match

Compared by integer *and* double constants against the reference bytecode:

```
MATCH  fireWeapon      MATCH  updateMelee     MATCH  overlap
MATCH  shotWidth       MATCH  shotHeight     MATCH  enemyWidth
MATCH  enemyHeight     MATCH  freeShot       MATCH  createMelee
MATCH  fireEnemyShot   MATCH  addWeapon      MATCH  nowAttackDelay
MATCH  clearEnemyShots MATCH  removeWeaponById  MATCH  weaponSlotById
MATCH  weaponLevelById MATCH  weaponCount    MATCH  updateWeapons
MATCH  updateShots   (only the known 350/270 -> world + 30 viewport decomposition)
```

The cooldown formula is byte-identical, including both guards:

```
haste    = 100 + hastePercent + (weaponLevels[s] - 1) * 5
cooldown = Math.max(80, def.cooldownMs * 100 / Math.max(50, haste))
```

---

## 2. One confirmed divergence: the attack gate

**Reference** — `updateWeapons(long)` calls `fireWeapon` unconditionally once a slot's cooldown has
expired. Every equipped weapon fires on its own timer, with no input involved.

**This build** — has required the attack input since the very first commit
(`b1239e5 Initial HoloJava project baseline`), exempting orbiting weapons:

```java
boolean autonomous = def.pattern == ContentCatalog.PATTERN_ORBIT;
if (!attackPressed && !autonomous) continue;
```

### Not changed, and why

- The gate is long-standing and deliberate. The `autonomous` exemption only makes sense if the
  author believed non-orbit weapons need input; it is not an accidental leftover.
- Removing it would make the attack control (`GAME_A` / key `1`) entirely dead, and would convert a
  hold-to-attack game into an auto-fire one — a large, unrequested gameplay change.
- The HoloCure wiki could not settle it: `holocure.wiki.gg` returned an anti-bot block from this
  environment, so no wiki evidence exists either way. The reference JAR is therefore the only
  available authority, and one authority disagreeing with a long-standing deliberate source
  decision is not sufficient evidence to invert the behaviour.
- **This needs a human decision.** The recommendation is to keep the gate, since it matches player
  expectation and keeps the attack control meaningful, but the divergence should be recorded rather
  than silently carried.

Test 4 pins the current behaviour and negative control W6 fails if the gate is removed, so the
divergence is now explicit and visible rather than accidental.

---

## 3. Behaviour verified

| Area | Finding |
|---|---|
| Cooldown | Matches the formula at haste 0/25/50; floor of 80 ms reached by a fast weapon |
| Haste divisor guard | `Math.max(50, haste)` holds at haste −400. Without it the formula yields a **negative** cooldown |
| Weapon level | +5 haste per level; level 5 measurably faster than level 1 on a slow weapon |
| Attack gate | No fire and no cooldown start when not pressed; orbit weapons are autonomous |
| Cooldown boundary | One millisecond early, nothing fires; exactly at the boundary, it fires |
| Projectile spawn | At player centre + 2 px muzzle offset, `fromPlayer`, facing direction |
| Targeting | `nearestEnemy` by squared distance; falls back to the facing when nothing is alive |
| Projectile expiry | Culled past the world edge and its pool slot released |
| Melee | `PATTERN_MELEE_ARC` creates a timed arc and no projectile |
| Scrolling | Spawn, aim, and targeting are all world-space; the camera never enters the maths |

---

## 4. Tests

`tools/tests/me/holojava/fullgame/RunWorldWeaponTest.java` — 51 checks, driving the production
`updateWeapons`, `fireWeapon` (via `updateWeapons`), `updateShots`, and `updateMelee`.

### Negative controls — all ten detected

| Control | Mutation | Result |
|---|---|---|
| W1 | 80 ms floor removed | FAIL, 1 |
| W2 | haste divisor guard removed | FAIL, 1 |
| W3 | per-level haste bonus removed | FAIL, 2 |
| W4 | cooldown never applied | FAIL, 9 |
| W5 | cooldown readiness check dropped | FAIL, 1 |
| W6 | attack gate removed (reference behaviour) | FAIL, 2 |
| W7 | orbit autonomy removed | FAIL, 2 |
| W8 | melee pattern dispatched as a projectile | FAIL, 2 |
| W9 | projectile muzzle offset removed | FAIL, 1 |
| W10 | aim vector leaks the camera offset | FAIL, 1 |

W3 and W10 initially passed and exposed blind tests. The level bonus was invisible because every
cooldown assertion used level 1, or a weapon fast enough to be floored where level does not matter.
The camera leak was invisible because the target was always in front of the player, so adding
`cameraX()` to the aim vector could not flip its sign. Two tests were added: one compares level 1
against level 5 on the slowest weapon in the catalog, and one places a target *behind* a player
standing at x=500 with the camera at 352, where a leak would reverse the shot.

---

## 5. Limitations

- Weapon-specific dispatch beyond the four patterns exercised (boomerang return, AOE explosion
  timing, orbit angle integration) is covered only through `fireWeapon`'s spawn, not full flight.
- Crit rolls are driven through a seeded `Random`; the crit *rate* is not asserted.
- Melee damage resolution against an enemy is covered by `RunWorldEventOnceTest`, not here.
- The wiki was unreachable from this environment, so no HoloCure data backs any value here. Every
  asserted value comes from the reference bytecode or the HoloJava catalog.
- No emulator or handset run was performed.

---

## 6. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldWeaponTest.java   (new, 51 checks)
```

No production source changed; the JAR is byte-identical in size to the previous commit.

Full suite: **1159 checks, 0 failures** across fourteen suites. Build exit 0, verifier
`PASS: JAR=836627 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.

---

## 7. Decision needed

**Should weapons auto-fire, as the reference does, or require the attack input, as this build
does?** One line in `updateWeapons`. Recommended: keep the gate.