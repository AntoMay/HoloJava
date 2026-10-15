# Decision C — Stage-Clear Reward Persistence

**Date:** 2026-10-15
**Change group:** Persist the victory reward; keep the level-up bonus run-local
**Outcome:** Verified, committed locally. Includes one latent bug found and fixed by the new tests.

---

## 1. Decision as implemented

| # | Clause | Implementation |
|---|---|---|
| 1 | Victory reward `coinClear + defeatCount / 4` is credited to the permanent balance | `RunWorld.defeatBoss()` calls `save.creditCoins(clearReward)` |
| 2 | Level-up `+100` stays run-local, never persisted | `applyOffer`'s `OFFER_COINS` branch is unchanged |
| 3 | Pickups credited only after a successful storage transaction | Already true: `creditCoins` adopts the candidate record only after `storage.write` returns true |
| 4 | Uncollected coins are never converted | No conversion path exists; only `collectPickup` and the clear reward credit the balance |
| 5 | HUD keeps showing run coins | The `coins` field still feeds the HUD; both it and the stored balance advance |

The amount is **not** hardcoded. The reward stays `stage.coinClear + defeatCount / 4`, read from the
stage definition, so per-stage clear values from the catalog keep working unchanged.

---

## 2. A latent bug found and fixed

The new test called `defeatBoss()` three times and observed the reward paid **three times**
(9006 credits for a 3002 reward).

**Cause.** `defeatBoss()` had no re-entry guard. It sets `boss.active = false` but does not check
it, and the reward branch depends on `encounterDefeated[]`, which stays true. The reference JAR has
exactly the same shape — its bytecode starts `boss.active = false` with no test — but there the
consequence was only an inflated run-local counter.

**Fix.** `if (!boss.active) return;` at the top of `defeatBoss()`.

**Why this is safe and not a behaviour change.** Every current path into `defeatBoss()` already
checks `boss.active`:

| Caller | Guard |
|---|---|
| `updateBoss` bomber self-destruct (`RunWorld.java:747`) | `updateBoss` opens with `if (!boss.active) return;` |
| `damageBoss` from melee (`RunWorld.java:896`) | `if (boss.active && !m.bossHit && overlap(...))` |
| `damageBoss` from shots (`RunWorld.java:953`) | `if (!remove && boss.active && !s.bossHit && overlap(...))` |
| `damageBoss` from radius damage (`RunWorld.java:1132`) | `if (boss.active) { ... }` |

So the guard is unreachable in play today and changes no existing outcome. It exists because the
reward is now a **permanent** grant: without it, any future caller that forgets the `boss.active`
check would duplicate coins across every future run, not merely inflate one run's total.

This is the mission's rule — one event must not process a reward more than once — applied to the one
reward that is now durable.

---

## 3. Tests added

`tools/tests/me/holojava/fullgame/RunWorldClearRewardTest.java` — 16 checks, one section per
decision clause.

| # | Test | Proves |
|---|---|---|
| 1 | the clear reward reaches the permanent balance | Balance, run counter, and the bytes on disk all receive `coinClear + defeatCount/4`; the run is marked won |
| 2 | the clear reward is granted only once | Three `defeatBoss()` calls pay once and issue no further record writes |
| 3 | the level-up `+100` stays run-local | Run counter `+100`, permanent balance unchanged; only the clear reward is persisted |
| 4 | uncollected coins are never persisted | A run ending with 250 run coins persists only the clear reward |
| 5 | a refused clear-reward write leaves the balance unchanged | Run counter advances and the run is won, but the stored balance and disk bytes stay at 0 |
| 6 | the run counter still tracks pickups and rewards | Run total is pickups plus clear reward, which is what the HUD shows |

**Construction.** `RunWorld` cannot be built headlessly — its constructor reaches
`Image.createImage`, whose MIDP stub throws an `Error` that `AssetLibrary`'s `catch (Exception)`
does not catch. Instances are allocated without running the constructor; only the fields the
exercised methods touch are set; pool arrays are fully materialised because reflectively created
arrays are null-filled. The methods under test are the genuine production bytecode. Test-only: no
file under `src/` references `sun.*`.

---

## 4. Verification

Full suite, pinned stubs, `--release 8`:

| Suite | Checks | Failures | Result |
|---|---|---|---|
| `CoinBalanceTest` | 408 | 0 | PASS |
| `SaveStoreLegacyMigrationTest` | 245 | 0 | PASS |
| `SaveStoreCoinPersistenceTest` | 159 | 0 | PASS |
| `SaveStoreRmsPathTest` | 20 | 0 | PASS |
| `RunWorldCoinWiringTest` | 11 | 0 | PASS |
| `RunWorldEventOnceTest` | 14 | 0 | PASS |
| `RunWorldClearRewardTest` | 16 | 0 | PASS |
| **Total** | **873** | **0** | **PASS** |

### Negative controls — each decision clause proven by breaking it

| Control | Mutation | Result |
|---|---|---|
| C1 | victory reward no longer persisted | FAIL, 6 checks |
| C2 | `defeatBoss` idempotence guard removed | FAIL, 2 checks |
| C3 | level-up `+100` wrongly made permanent (i.e. drifting to option A) | FAIL, 2 checks |
| C4 | reward changed to `coinClear` only, dropping `defeatCount / 4` | FAIL, 9 checks |

C3 matters most: it is exactly the forbidden drift toward option A, and the suite rejects it.

### Build

- `./tools/build_fullgame.sh` → exit 0
- `python3 tools/verify_fullgame_build.py` → `PASS: JAR=836584 bytes, assets=189, wiki rows verified`
- `git diff --check` → exit 0

---

## 5. Files changed

```
 M src/java-me-fullgame/me/holojava/fullgame/RunWorld.java   (+14 / -1)
?? tools/tests/me/holojava/fullgame/RunWorldClearRewardTest.java  (new, 16 checks)
```

The production change is 14 lines: the reward is split into a named local, credited to the store,
and a four-line idempotence guard with its rationale. No damage, timing, probability, or catalog
value was touched.

---

## 6. Intentional divergence from the reference

1. **The clear reward is persisted.** The reference persists no coins at all; there the reward only
   advanced a run-local counter.
2. **`defeatBoss` gained a re-entry guard.** The reference has none. Unreachable in play today;
   present because the reward is now durable.

---

## 7. Residual risk

- Everything device-level still unverified: firmware RMS behaviour, `setRecord` durability at
  close, 56-byte record round-trip, on-device heap cost, and the real boss encounter flow. No
  emulator or handset run was performed.
- A stage cleared with a refused write shows the reward in the run total and results screen while
  the permanent balance does not move. This is the documented best-effort persistence contract, and
  matches the pickup path; it is recorded here so it is a known consequence rather than a surprise.

---

## 8. Next queued group

Continue the gameplay subsystem queue: extend headless coverage to EXP → level-up → offer selection
and boss encounter completion, using the same allocate-and-drive technique with negative controls
for every claim.