# Stage Completion and Unlock — Verified Against the Reference

**Date:** 2026-10-15
**Change group:** Stage completion persistence + stage unlock
**Outcome:** Verified, committed locally. One data defect found and corrected from reference
evidence; one minimal extraction to make the completion path testable.

---

## 1. Unlock is requirement-driven, not sequential

`FullGameCanvas.stageUnlocked` consults a per-stage `unlockRequirement`, not "the previous stage":

```java
private boolean stageUnlocked(int index) {
    ContentCatalog.StageDef s = ContentCatalog.stage(index);
    if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;
    if (s.timeStage) return save.isCompleted(characterIndex, 2);
    return save.isCompleted(characterIndex, s.unlockRequirement);
}
```

`-1` and `99` both mean always unlocked. Two consequences that make a sequential assumption wrong:

- **Stage 5, Grassy Plains (Night), requires order 1** — Holo Office — *not* order 4. A player who
  has cleared the two normal early stages but not Fantasy Island can already open it.
- **Stage 4, Fantasy Island, is `99`** and is therefore never locked at all.

---

## 2. Defect found and corrected: Fantasy Island was wrongly locked

Our catalog had `stage5` (Fantasy Island) with `unlockRequirement = 3`, meaning "clear Gelora Bung
Yagoo first". The reference says otherwise.

Read from the reference `ContentCatalog` bytecode, anchored on the `stage5` boss string:

```
2151: sipush  850      <- spawnIntervalMs
2154: bipush  36       <- maxEnemies
2156: sipush  15000    <- coinClear
2159: bipush  99       <- unlockRequirement
2161: ldc "nanora"     <- bossA
```

Cross-checks on the same extraction, all matching our data:

| Stage | Reference | Ours | |
|---|---|---|---|
| stage1 | `shrimp / 1300 / 20 / 3000 / -1` | `-1` | match |
| stage3h | `shubangelion / 850 / 34 / 30000 / 6` | `6` | match |
| stage5 | `nanora / 850 / 36 / 15000 / 99` | `3` | **mismatch** |

`bipush 99` occurs exactly once in the reference `ContentCatalog`, so only Fantasy Island carries
it.

**Impact.** Before the fix, Fantasy Island was locked behind Gelora Bung Yagoo. The reference never
locks it.

**Note on the prior audit.** `reports/evidence-gap-closure/01_STAGE_UNLOCK_BYTECODE.md` agrees on
stage5 = 99, but lists stage1 as `99` where the bytecode says `-1`, and marks the night variants as
`timeStage = true` where our catalog has them `hard = true`. Both values mean "always unlocked", so
its stage1 conclusion was harmless, but the table was not fully reliable and was re-derived here from
the bytecode directly.

**Change:** `unlockRequirement` for stage5 `3 → 99`, with the bytecode evidence recorded in a comment
at the definition.

---

## 3. Minimal extraction to make completion testable

The completion decision lived inline inside `FullGameCanvas.update(long)`. That method calls
`getKeyStates()` first, which is MIDP `Canvas` API and throws
`Error("API Stub has been used")` on a desktop JVM, so the path could not be executed headlessly.

The three statements were moved verbatim into a private method that `update()` now calls at the same
point:

```java
private void handleRunEnd() {
    worldWasVictory = world.state() == RunWorld.STATE_VICTORY;
    if (worldWasVictory && modeIndex == 0) {
        save.setCompleted(characterIndex, ContentCatalog.stage(stageIndex).order);
    }
    screen = RESULTS;
    resultIndex = 0;
}
```

No behaviour change: same statements, same order, same call site. The tests drive **this** method,
which is the body the game loop actually executes when a run ends.

---

## 4. Tests added

`tools/tests/me/holojava/fullgame/StageCompletionUnlockTest.java` — 49 checks.

| # | Test | Proves |
|---|---|---|
| 1 | the unlock table matches the reference | All ten `unlockRequirement` values, and that each stage's `order` still equals its index |
| 2 | a victory records completion | Completion is stored, the run is flagged a victory, the screen moves to results, exactly one record write |
| 3 | a defeat records no completion | Nothing marked complete, **no record write at all**, screen still moves to results |
| 4 | completion unlocks the gated stage | Stage 2 shut before, open after clearing stage 1; a different character stays shut |
| 5 | completion is idempotent | Three replays leave the stored record byte-for-byte identical |
| 6 | progress survives a reload | A fresh session sees the completion, nothing extra was granted, coin field untouched, gated stage open |
| 7 | a legacy 48-byte save still loads and unlocks | Old completion bits load, gates resolve correctly, and a new completion adds to them without losing the old bits |

The methods exercised are the production ones: `handleRunEnd` and `stageUnlocked`. Nothing is
re-implemented in the test.

---

## 5. Verification

| Suite | Checks | Failures | Result |
|---|---|---|---|
| `CoinBalanceTest` | 408 | 0 | PASS |
| `SaveStoreLegacyMigrationTest` | 245 | 0 | PASS |
| `SaveStoreCoinPersistenceTest` | 159 | 0 | PASS |
| `SaveStoreRmsPathTest` | 20 | 0 | PASS |
| `RunWorldCoinWiringTest` | 11 | 0 | PASS |
| `RunWorldEventOnceTest` | 14 | 0 | PASS |
| `RunWorldClearRewardTest` | 16 | 0 | PASS |
| `RunWorldOfferOnceTest` | 25 | 0 | PASS |
| `StageCompletionUnlockTest` | 49 | 0 | PASS |
| **Total** | **947** | **0** | **PASS** |

### Negative controls

| Control | Mutation | Result |
|---|---|---|
| S1 | completion is not saved at all | FAIL, 9 checks |
| S2 | completion saved under stage index instead of order | **PASS — not detected, see below** |
| S3 | completion also recorded on defeat | FAIL, 2 checks |
| S4 | `setCompleted` stops setting the bit | FAIL, 7 checks |
| S5 | unlock table reverted to `stage5 = 3` | FAIL, 2 checks |
| S6 | `99` no longer means always-unlocked | FAIL, 1 check |
| S7 | `stageUnlocked` returns false for `-1` / `99` | FAIL, 2 checks |

**S2 honestly explained.** Recording completion under `stageIndex` rather than
`ContentCatalog.stage(stageIndex).order` is *currently behaviour-equivalent*, because every stage in
the catalog has `order == index`. No test can distinguish the two until the catalog diverges.

Rather than leave that as an invisible assumption, test 1 now asserts `order == index` for every
stage. The day the catalog is reordered, that assertion fails and the index-versus-order choice
becomes a real, visible decision instead of a silent one.

### Build

`./tools/build_fullgame.sh` exit 0; `verify_fullgame_build.py` → `PASS: JAR=836627 bytes, assets=189,
wiki rows verified`; `git diff --check` exit 0.

---

## 6. Files changed

```
 M src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java     (+5 / -1)
 M src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java    (+21 / -3)
?? tools/tests/me/holojava/fullgame/StageCompletionUnlockTest.java   (new, 49 checks)
```

No change to the record format, the record store name, or any boss, coin, offer, or run-reset logic.

---

## 7. Still unverified

- Whether the stage-select screen surfaces the locked state correctly is a rendering concern; the
  gate function itself is covered, the drawing of it is not reachable headlessly.
- All device-level RMS questions carried forward from earlier reports.
- No emulator or handset run was performed.