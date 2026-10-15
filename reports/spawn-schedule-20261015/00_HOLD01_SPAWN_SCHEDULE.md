# HOLD-01 — Enemy Spawning and Wave Progression

**Date:** 2026-10-15
**Change group:** Enemy spawn schedule, capacity, and timed waves
**Outcome:** Parity confirmed, verified, committed locally. **No production code changed.**

`reports/comprehensive-audit/07_REMEDIATION_BACKLOG.md` held this as `HOLD-01`: *"`nextSpawnAt` /
`nextWaveAt` full behavior — Butuh verifikasi mendalam di `RunWorld.update()`"*. That verification is
now done.

---

## 1. Evidence: bytecode

### `nextWaveAt` is dead in the reference too

| | declaration | reset write | `updateTimedEvents` write | **read** |
|---|---|---|---|---|
| Reference | 155 | offset 404 | offset 67 | **none** |
| Ours | `RunWorld.java:146` | `:234` | `:519` | **none** |

The reference declares `nextWaveAt` and writes it twice, and never reads it. Our source is identical.
The backlog's worry that wave progression might have been dropped in the port is **disproved**: the
wave events are driven by `elapsed` thresholds, not by `nextWaveAt`, in both.

### The spawn chain is constant-identical

Comparing integer *and* double constants per method between the two disassemblies:

```
MATCH  updateSpawns        MATCH  updateScaling
MATCH  spawnEnemy          MATCH  updateTimedEvents
MATCH  spawnWave           MATCH  activeEnemyCount
MATCH  freeEnemy
```

The only differences in the whole enemy family are the two already-verified viewport decouplings
(`clampEnemy` 320/240 → world fields, `spawnBoss` 140 → `worldWidth / 2 - 20`), both identical at the
default 320×240 world.

### `updateSpawns` is instruction-for-instruction identical

Gate order, `elapsed / 5000L` cast to int, `* 20` tightening, the 250 floor, the Time Mode
`Math.min(interval, 550)`, `spawnEnemy`, and `nextSpawnAt = now + interval` — every opcode matches.

### `update()` calls the same twelve helpers in the same order

`updateScaling, updatePlayer, updateTimedEvents, updateSpawns, updateBossDirector, updateEnemies,
updateBoss, updateWeapons, updateShots, updateMelee, updatePickups, updateRegen` — identical in both.

### `spawnWave` reads `boss.active` zero times in the reference

So spawning a timed wave during a boss fight is inherited behaviour, not a porting defect. Recorded,
not changed.

---

## 2. Behaviour established

**Interval.** `spawnIntervalMs − (elapsed / 5000) × 20`, floored at 250 ms, and clamped to at most
550 ms in Time Mode. Spawning therefore *tightens* as a run progresses: stage1 starts at 1300 ms and
reaches the 250 ms floor after roughly 80 s.

**Gates, in order.** A live boss stops spawning outright; then `now >= nextSpawnAt`; then the stage
cap.

**Enemy mix.** `rand(100)` split 55 / 30 / 15 across `enemyA` / `enemyB` / `enemyC`.

**Waves.** Ring at 180 s spawning 16 enemies in a ring; horde at 900 s spawning 18 at the edges. Both
are one-shot, guarded by `ringEventDone` / `hordeEventDone`.

**The cap freezes the schedule.** The `maxEnemies` check returns *before* `nextSpawnAt` is advanced,
so while a stage sits at its cap the gate does not move. Time passing during a full stage produces no
backlog: freeing one slot resumes the schedule from the frozen gate, one interval ahead.

**Pause.** The schedule is wall-clock. `update()` returns early unless the run is running, so a pause
neither rewinds nor accumulates; resuming long past an overdue gate yields exactly one spawn.

**Scrolling.** A wider world changes only *where* enemies appear via `placeAtEdge`. Schedule and
counts are identical at 320 and 640.

---

## 3. Tests

`tools/tests/me/holojava/fullgame/RunWorldSpawnScheduleTest.java` — 60 checks, driving the
production `updateSpawns`, `updateTimedEvents`, and `spawnEnemy`.

| # | Test |
|---|---|
| 1 | nothing spawns before the first gate (`startTime + 1000`) |
| 2 | the interval equals `spawnIntervalMs` at t = 0 |
| 3 | the interval tightens with run time and reaches the 250 floor |
| 4 | every stage variant follows its own rate and declares a positive cap |
| 5 | a live boss stops spawning and does not advance the gate |
| 6 | the stage `maxEnemies` cap is never exceeded |
| 7 | a full cap freezes the gate; freeing a slot resumes from it |
| 8 | Time Mode clamps the interval to 550 |
| 9 | ring and horde waves fire exactly once, 16 and 18 enemies |
| 10 | a pause does not rewind the clock, and produces no burst on resume |
| 11 | scrolling changes placement, not the schedule |
| 12 | the enemy mix is 55 / 30 / 15 |

### Negative controls

| Control | Mutation | Result |
|---|---|---|
| N1 | spawn gate no longer honoured | FAIL, 2 |
| N2 | tightening term removed from the formula | FAIL, 5 |
| N3 | 250 ms floor removed | FAIL, 3 |
| N4 | `maxEnemies` cap removed | FAIL, 5 |
| N5 | boss gate removed from `updateSpawns` | FAIL, 2 |
| N6 | Time Mode 550 clamp removed | FAIL, 1 |
| N7 | ring wave one-shot guard removed | FAIL, 1 |
| N8 | ring wave size 16 → 24 | FAIL, 2 |
| N9 | horde wave one-shot guard removed | FAIL, 1 |
| N10 | enemy mix thresholds 55/85 → 33/66 | FAIL, 2 |

N10 initially passed and exposed a blind test. Three of my own expectations were also wrong while
writing this, and each was a real fact rather than a fudge:

- I compared an absolute gate timestamp against an interval when asserting monotonicity, and had the
  comparison direction backwards — spawning *tightens*, so the later interval is the smaller one.
- I expected the gate to keep advancing while a stage was at its cap. It does not: the cap check
  returns first, so the gate freezes. The test now asserts the real behaviour.
- I expected a freed slot to raise the count above the cap. Freeing a slot lowers it first; the test
  now checks the slot is refilled.
- The first mix measurement read 69.4 % / 30.6 % / 0 % because stage1 declares `enemyC` as `"shrimp"`,
  identical to its `enemyA`, making the buckets indistinguishable. The mix is now measured on
  Fantasy Island, the only stage naming three distinct enemies.

---

## 4. Limitations

- Enemy AI, movement, and contact damage are not covered; they need spawned assets, and
  `AssetLibrary` cannot be constructed off-device.
- `placeAtEdge` placement is asserted only indirectly (test 11 compares schedules, not positions).
- **No wiki data was used.** Every value asserted here comes from the reference bytecode or from the
  HoloJava catalog. HoloCure's published spawn rates were deliberately not consulted, because they
  describe a different game and the reference JAR is the authority for this port.
- No emulator or handset run was performed.

---

## 5. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldSpawnScheduleTest.java   (new, 60 checks)
```

No production source changed; the JAR is byte-identical in size to the previous commit.

Full suite: **1041 checks, 0 failures** across eleven suites. Build exit 0, verifier
`PASS: JAR=836627 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.