# Time-Driven Scaling and Collab Weapons

**Date:** 2026-10-15
**Change groups:** (1) `updateScaling` and every consumer; (2) collab weapons
**Outcome:** Parity confirmed for both. **No production code changed.**

---

# Part 1 — Scaling

## Verified formula

```
scaleA = Math.max(0.0, minuteTimer - 23) + 37.0 * hourTimer
scaleB = (elapsed >= 1800000 ? (elapsed - 1800000) / 60000.0 : 0.0) + 60.0 * hourTimer
minuteTimer = elapsed / 60000      hourTimer = elapsed / 3600000
```

**Byte-for-byte identical to the reference**, confirmed by disassembling its `updateScaling`:
`currentTimeMillis() - startTime`, divisors `60000` and `3600000`, `Math.max(0.0, minuteTimer - 23)`,
`37.0 * hourTimer`, and the `1800000` comparison with `60.0 * hourTimer`.

Thresholds verified by measurement:

| Elapsed | scaleA | scaleB |
|---|---|---|
| 10 min | 0 | 0 |
| 22.5 min | 0 | 0 |
| 24 min | 1 | 0 |
| 30 min | 7 | 0 |
| 35 min | 12 | 5 |
| 60 min | 74 | 30 |
| 2 hours | 171 | 210 |

**A point I initially got wrong and the test corrected:** `minuteTimer` counts *total* minutes, not
minutes past 23. At 60 minutes that is 60 − 23 = 37, plus the hour term's 37, giving 74 — not 37.

## Verified schedule

Identical to the reference's `update`: `now - startTime > 120000` **and** `lastMinuteScaled != now / 60000`.
Measured behaviour: nothing scales in the first two minutes; a run already stamped for the current
wall-clock minute does not rescale; an unstamped long run does, and records the minute.

## Recomputed, not accumulated

`scaleA` and `scaleB` are assigned from the clock, never added to. Twenty-five consecutive calls
leave both unchanged, so a repeated call cannot inflate them.

## Consumers, each measured

| Stat | Formula | Clamp |
|---|---|---|
| Enemy HP | `(base + base·0.05·scaleA) ^ (1 + scaleB/50)`, round-even | `[1, 100000]` |
| Enemy ATK | `(base + 2·scaleA) ^ (1 + scaleB/25)`, round-even | `[1, 1000]` |
| Enemy speed | `(base + 0.12·scaleA) ^ (1 + scaleB/25)` | `[0.1, 3.0]` |
| Boss HP | `(def.hp + def.hp·0.05·scaleA) ^ (1 + scaleB/50)`, **truncating cast** | `≤ 20000` |
| Boss ATK | `(def.atk + 2·scaleA) ^ (1 + scaleB/25)`, **truncating cast** | none |
| Boss speed | **`0.55 + min(0.5, scaleA·0.005)`** — not the enemy formula | `≤ 1.05` |
| Boss shot count | `5 + (int) min(6.0, scaleA / 8.0)` | `≤ 11` |

The boss row is the one worth flagging: it uses a **different rounding mode and an entirely different
speed formula** from enemies. That looks like an inconsistency but is confirmed in the reference, so
it is documented rather than changed.

A newly spawned enemy picks up the current scaling, so late spawns are tougher than early ones —
verified by spawning at minute 0 and at four hours.

## Negative controls — twelve of twelve detected

| Control | Mutation | Result |
|---|---|---|
| S1 | `scaleA` made cumulative | FAIL, 1 |
| S2 | threshold 23 → 10 | FAIL, 5 |
| S3 | `scaleB` hour coefficient 60 → 6 | FAIL, 1 |
| S4 | `scaleA` hour coefficient 37 → 3 | FAIL, 2 |
| S5 | enemy HP clamp removed | FAIL, 1 |
| S6 | enemy ATK `scaleA` term removed | FAIL, 1 |
| S7 | enemy speed clamp removed | FAIL, 1 |
| S8 | boss uses the enemy speed formula | FAIL, 2 |
| S9 | boss HP cap 20000 removed | FAIL, 1 |
| S10 | two-minute gate removed | FAIL, 1 |
| S11 | per-minute dedupe removed | FAIL, 1 |
| S12 | spawn no longer scales enemy HP | FAIL, 2 |

Two initially passed and exposed blind tests, both fixed:

- **S6** — ATK still grew through the `scaleB` exponent, so the `scaleA` term was invisible. A case
  was added in the minute 24–30 window where `scaleB` is still zero, isolating `scaleA` alone.
- **S11** — the "already stamped this minute" case used a ten-minute run, where a spurious update
  would still yield `scaleA = 0`. It now uses thirty minutes, where a spurious update is visible.

# Part 2 — Collab weapons

## A fact I had backwards

I read the `mode(...)` argument list and concluded Time Mode was the collab mode. Probing the live
catalog gave the opposite:

```
0 id=stage    collabs=true   1 id=endless collabs=true   2 id=time collabs=false
```

`allowsCollabs` is the second of the three booleans, not the third. **Collab offers appear in Stage
and Endless Mode and not in Time Mode.** The tests now read the catalog rather than my reading of
the parameter order, and the class Javadoc records the correction.

## Verified

| Behaviour | Result |
|---|---|
| `removeWeaponById` | Compacts by shifting left, drops the count by one, and clears the vacated tail slot's id, level, and cooldown |
| Absent or null id | No change at all |
| Levels and cooldowns | Travel with their weapon rather than staying behind |
| Collab offer gate | Requires the mode to allow collabs, a free slot, **both** prerequisites at maximum level, and the collab not yet offered |
| Offer flags | `collab = true`, `existing = -1`, `newItem = true` |
| Applying one | Removes **both** prerequisites and grants the collab at level 1 |
| Repeat offers | The same collab appears at most once across six slots, via `offerUsed` |

## Negative controls — eight of eight detected

| Control | Mutation | Result |
|---|---|---|
| C1 | `removeWeaponById` stops shifting | FAIL |
| C2 | vacated tail slot no longer cleared | FAIL |
| C3 | cooldown no longer shifted | FAIL |
| C4 | prerequisites no longer removed on apply | FAIL, 5 |
| C5 | `allowsCollabs` mode gate removed | FAIL |
| C6 | prerequisites need not be maxed | FAIL |
| C7 | free-slot gate removed | FAIL, 1 |
| C8 | `offerUsed` no longer blocks a repeat | FAIL |

**C7 initially passed** and exposed a blind test: the "full inventory" case had not maxed the
prerequisites, so the offer never reached the slot gate. It now maxes both prerequisites *and* fills
the inventory, and additionally frees a slot in a twin world to prove the gate was what blocked it.

---

## Limitations

- `updateScaling` reads the system clock itself, so elapsed times are produced by moving `startTime`
  into the past. Thresholds are asserted with at least a second of margin, but the values are not
  bit-exact at a boundary.
- Boss shot count scaling is noted above but not separately asserted.
- Collab coverage uses `COLLABS[0]` (`absolute_wall`) as the fixture; the other four collabs are not
  enumerated, though they share one code path.
- `addWeapon` itself is exercised only through the collab swap, not directly.
- No wiki data was used; `holocure.wiki.gg` remains blocked from this environment.
- No emulator or handset run was performed.

## Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldScalingTest.java   (new, 47 checks)
?? tools/tests/me/holojava/fullgame/RunWorldCollabTest.java     (new, 35 checks)
```

No production source changed; the JAR is unchanged at 836702 bytes. Full suite: **1665 checks,
0 failures** across nineteen suites. Build exit 0, verifier
`PASS: JAR=836702 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.

## Status of the queue

Scaling and collab weapons are both closed at parity. The gameplay subsystems named across the audit
list — movement, spawning, boss encounters, combat, weapons, level-up and offers, skills, items,
stage progression, coin persistence, enemy AI, scaling, collabs, and the UI state machine — are now
covered. What remains is the pair of **decisions awaiting a human**: `uber_sheep` and the weapon
attack gate.