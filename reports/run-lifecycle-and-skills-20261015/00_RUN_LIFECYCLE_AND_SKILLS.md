# Run Lifecycle and Skill Acquisition-to-Effect

**Date:** 2026-10-15
**Change groups:** (1) player/run lifecycle end to end; (2) skill and special acquisition-to-effect
**Outcome:** Parity confirmed. **No production code changed.**

---

## Why this suite could not be written before

The pinned MIDP stub JARs make every `javax.microedition.lcdui.Image` member throw
`java.lang.Error("API Stub has been used")`. `AssetLibrary.image()` catches `Exception`, not `Error`,
so no `AssetLibrary` can be built headlessly — and with no `AssetLibrary`, no real `RunWorld` can be
constructed. Every earlier suite therefore reconstructed a world field by field, which cannot prove
anything about the constructor or about cross-run isolation, since a fresh object is zeroed by the
runtime regardless of what `reset()` does.

`tools/tests/javax/microedition/lcdui/Image.java` is a test-only stand-in that hands back a
placeholder instead of throwing. It is compiled into a separate output directory that goes **first**
on the classpath and is never packaged: `tools/build_fullgame.sh` compiles only
`src/java-me-fullgame`, and the built JAR contains no test class. Verified: the JAR is byte-identical
in size (836702) and holds no test artefact.

With it, the suite builds a genuine `AssetLibrary` (97 image requests, 0 asset failures), a genuine
`SaveStore` over an in-memory record store, and `new RunWorld(...)` itself.

`tools/run_fullgame_tests.sh` was added so the classpath ordering is reproducible instead of an
ad-hoc command. There was no test runner before it.

---

## Part 1 — End-to-end lifecycle

### 1. Start

Real constructor. Verified initial and idle: running state, level 1, 0 EXP, **79** EXP required
(the Level Up page's stated figure), 0 kills/coins/defeats, `baseMaxHp` from the character and
`playerHp == playerMaxHp`, exactly the character's declared starting weapon on a short first
cooldown, no items, no skill, special unspent, every effect field at zero, no enemy, projectile,
melee arc or pickup, boss inactive with no encounter scheduled, both timed events pending, timer set,
offers generated.

### 2. EXP → level-up → offer → choice

A real EXP pickup dropped at the player and collected by `updatePickups`: experience is banked, the
level advances to 2, the cost is deducted, the next requirement is set, and the state becomes
`STATE_LEVEL_UP`. Further ticks change nothing, because `update()` ignores a non-running state.

One behaviour worth recording: leftover experience re-opens the level-up screen immediately after a
choice, so a world that has just gained a skill is *still* in `STATE_LEVEL_UP`. Anything that then
tries to drive combat is silently ignored. The suite drains the screen explicitly.

### 3. Effect

Test [3] runs the whole chain in one world: level-ups until the generator offers a skill, take it,
then spawn an enemy inside radius and tick. The label the offer carried is the character's own skill
name, the skill is granted, and the nearby enemy actually loses health.

### 4. State changes

Cooldowns: skill 15 s, and a press inside it does nothing and does not extend it; after it lapses the
skill fires again and rearms a fresh 15 s. Shield recharge: the armed timer refills to 15 and re-arms
15 s later. Level caps: repeatedly upgrading the starting weapon never exceeds its `maxLevel`.

### 5. Run end

**Death** — enemy contact damage (real overlap path) drives HP to 0, the run ends in
`STATE_GAME_OVER`, health clamps at 0 rather than going negative, and five further ticks score
nothing.

**Victory** — the real `damageBoss` → `defeatBoss` path. The final encounter for a non-hard stage is
encounter 1, which is what `defeatBoss` reads. The run-local counter gains
`stage.coinClear + defeatCount/4`, **exactly one** persistent write is committed, and the stored
balance holds the reward. Ten further hits and ticks after the win commit no further write — the
`if (!boss.active) return;` guard in `defeatBoss` is what holds, and it is load-bearing precisely
because the reward is now permanent. Control L9 removes it and the suite catches it.

**Results once** — the canvas enters the results screen, records the victory, and persists stage
completion. The guard against re-entry is the screen change itself: `update()` only calls
`handleRunEnd()` while `screen == GAME`. Choosing the second entry returns to the menu and drops the
world reference; choosing the first builds a genuinely new world.

### 6. Next run

Run one is dirtied as hard as the public API allows — skill acquired and fired, special spent,
enemies, projectiles, pickups, a boss, levels, coins. Run two is constructed from the same assets and
the same save. Nothing carried over: skill, special, all five effect fields and their cooldowns,
level, experience, kills, defeats, run-local coins, inventory, every pool, boss, encounter, both timed
events, the held offer slot. The new world is then shown to still work — it levels up normally.

The one thing that **is** meant to survive, the permanent coin balance, is asserted to survive.

### `reset()` needed its own test

`reset` is private and called only from the constructor, so no fresh-instance test can observe it.
Deleting a line from `reset()` would have been completely undetectable. Test [12] therefore dirties
a world and then drives `reset` directly, which is what makes the reset controls meaningful.

---

## Part 2 — Skill and special matrix

Acquisition is the same for every skill: a level-up offer of type `SKILL`, available only while
`skillAcquired` is false, applied by `selectOffer` → `applyOffer`. The special is not acquired at
all; it is gated only on being unused.

| Character | Skill | State the skill writes | Where that state is consumed | Evidence |
|---|---|---|---|---|
| Gawr Gura | Shark Bite | nothing beyond `skillReadyAt` | `damageEnemiesInRadius(70, 6, knockback)` | [4] enemy HP falls |
| Amelia Watson | Hunter's Instinct | `slowEnemiesUntil = now+3500`; every active enemy's `nextAttack = now+1200` | `updateEnemies` speed and attack timing | [4] both fields asserted |
| Mori Calliope | Undying | `shield = 20`, `shieldReadyAt = now+15000` | `damagePlayer` absorption | [4] both fields; [6] absorption measured |
| Takanashi Kiara | Second Flame | `lifestealUntil = now+8000` | `damageEnemy`, on the kill | [4] field; [6] HP rises on a kill |
| Ninomae Inanis | The Ancient One | `invulUntil = now+2500` | `damagePlayer` gate, boss contact | [4] field; [6] damage ignored |

| Character | Special | Effect | Evidence |
|---|---|---|---|
| Gawr Gura | Shark Call | radius 110, 18 damage | [5] enemy HP falls |
| Amelia Watson | Slow Time | `slowEnemiesUntil = now+6000` | [5] |
| Mori Calliope | The Reaper | heal `max(8, maxHp/4)` + `lifestealUntil = now+10000` | [5] exact heal amount |
| Takanashi Kiara | Phoenix Fire | radius 130, 24 damage | [5] |
| Ninomae Inanis | Spirit of the Oni | `shield = 30`, `shieldReadyAt = now+10000`, clears enemy shots | [5] all three |

Every character: cooldown `now + 15000`; `specialUsed` latches so a second press does nothing.

### Three measurement details the tests had to get right

- **`damageEnemiesInRadius` measures from `px`/`py`, not the player's centre.** An offset of *d* places
  the enemy *d+12* away. An early version placed the enemy 112 from the origin and Shark Call
  (radius 110) correctly missed.
- **Contact damage fires while `now < nextAttack`,** not the other way round. Stamping `nextAttack`
  to the current time silently *disables* it. This initially made the death test fail.
- **The Reaper's heal is capped at `playerMaxHp`,** so a full-health player heals 0 and proves
  nothing. The test wounds the character first and asserts it was below maximum.

### Passives: the item table matches the reference exactly

The reference's item table was decoded from bytecode (each `dup … invokestatic item:` run gives the
id, name and the four ints) and compared with ours:

| Item | Reference | Ours |
|---|---|---|
| face_mask | DAMAGE_PCT 30 | same |
| knightly_milk | PICKUP_PCT 30 | same |
| energy_drink | SPEED_PCT 40 | same |
| just_bandage | MAX_HP 10 | same |
| study_glasses | EXP_PCT 10 | same |
| sake | CRIT_PCT 5 | same |
| gorillas_paw | DAMAGE_PCT 20 | same |
| piki_piki | MAX_HP 15 | same |
| limiter | PICKUP_PCT 100 | same |
| headphones | NEGATE 15 | same |
| uber_sheep | FOOD_DROP 10 | removed in `c7764a8` |

So **no item in the reference uses `EFFECT_HASTE_PCT`, `EFFECT_REGEN` or `EFFECT_SHIELD` either.**
Regen and haste have working code paths in this port but are unreachable from the item table. That is
**inherited reference behaviour, identical on both sides**, not a porting defect — the same class of
finding as `uber_sheep`. The item effects themselves were verified earlier in `RunWorldItemTest`.

---

## Part 3 — Negative controls, eighteen of eighteen

| Control | Mutation | Result |
|---|---|---|
| L1 | skill offer never grants the skill | FAIL, 24 |
| L2 | skill press no longer reaches `activateSkill` | FAIL, 2 |
| L3 | skill cooldown never armed | FAIL, 8 |
| L4 | lifesteal window never consumed on a kill | FAIL, 2 |
| L5 | shield no longer absorbs damage | FAIL, 2 |
| L6 | invulnerability gate removed from `damagePlayer` | FAIL, 1 |
| L7 | special never marked as spent (fires twice) | FAIL, 7 |
| L8 | Reaper heals twice the stated amount | FAIL, 1 |
| L9 | clear-reward idempotence guard removed | FAIL, 2 |
| L10 | offer generator never offers a skill | FAIL, 25 |
| L11 | reset no longer clears skill state | FAIL, 3 |
| L12 | reset no longer clears run-local counters | FAIL, 3 |
| L13 | reset no longer deactivates live entities | FAIL, 3 |
| L14 | reset no longer clears boss/encounter state | FAIL, 2 |
| L15 | reset no longer re-arms timed events | FAIL, 2 |
| L16 | reset no longer clears the held offer | FAIL, 1 |
| L17 | EXP no longer triggers the level-up screen | FAIL, 27 |
| L18 | death no longer ends the run | FAIL, 3 |

L1/L10 cover acquisition severed at two depths — at the generator and at the apply step. L7/L8 cover
an effect installed twice. L11–L16 cover reset lines removed.

**L15 initially passed** and exposed a blind assertion: test [12] checked that reset re-armed the two
timed events, but had never set them first, so the check passed against a world where they were
already false. The test now consumes both events before resetting and asserts they were consumed,
which is what gives the control something to bite on.

---

## Classification

**Porting defect:** none found.

**Parity confirmed:** the whole lifecycle — construction, level-up, offers, skills, specials,
cooldowns, death, victory reward, results entry, restart and cross-run isolation.

**Inherited reference behaviour, deliberately preserved:**
- `step()` is `(int)(x * 0.04)` floored at 1 — byte-identical in the reference. At every catalogued
  enemy speed both the slowed and unslowed step floor to 1, so the slow multiplier is **numerically
  masked for movement**. The suite proves the flag is consumed by raising both speeds above the floor,
  and separately pins the masking itself by calling `step()` directly, so the limitation is recorded
  rather than hidden.
- The player is deliberately immune to the enemy slow: `if (now < slowEnemiesUntil) { /* ... */ }` is
  an empty branch.
- `EFFECT_REGEN`, `EFFECT_HASTE_PCT` and `EFFECT_SHIELD` are implemented or declared but no item
  reaches them, in the reference as well as here.

**Data or features inert in the reference:** `uber_sheep` (unchanged decision), and the three effect
constants above.

**Harness limitations:**
- The `Image` stand-in returns a 1×1 placeholder. Rendering is never exercised — `getGraphics()` is
  reachable only from the canvas paint path — so nothing here is evidence about drawing.
- `updateScaling` reads the system clock, so elapsed values are produced by moving `startTime`; that
  constraint is unchanged from the scaling suite.
- The weapon attack gate means no weapon fires in this suite, so the lifesteal test drives
  `damageEnemy` directly — the production function where lifesteal is consumed. That isolates the
  wiring precisely but is not itself proof that a weapon can kill.
- Auto-spawn is disabled by parking `nextSpawnAt`, so every entity in the suite is placed explicitly.
- No emulator or handset run was performed.

---

## Verification

- **1841 checks, 0 failures** across 20 suites (176 new in `RunWorldLifecycleIntegrationTest`).
- Build exit 0. JAR **836702 bytes**, unchanged.
- Verifier `PASS: JAR=836702 bytes, assets=189, wiki rows verified`, exit 0.
- `git diff --check` exit 0, staged and unstaged.
- No tracked production source changed. The JAR contains no test class or test-only class.

## Files added

```
tools/run_fullgame_tests.sh                                     (new runner)
tools/tests/javax/microedition/lcdui/Image.java                 (test-only stand-in)
tools/tests/me/holojava/fullgame/RunWorldLifecycleIntegrationTest.java
```
