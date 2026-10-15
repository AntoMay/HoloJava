# Boss Encounter Director and Stage-Completion Bookkeeping

**Date:** 2026-10-15
**Change group:** Boss encounter flow — the remaining unverified part of the win path
**Outcome:** Verified, committed locally. **No production code changed.**

---

## 1. Why this subsystem

Stage completion depends on the encounter bookkeeping being right. `defeatBoss` decides victory by
reading `encounterDefeated[]`, and `updateBossDirector` decides which encounter may appear. If either
is wrong, a player wins a stage they did not finish, or cannot finish one they did.

Neither had any test coverage.

---

## 2. What the director does

```java
private void updateBossDirector(long now) {
    if (stage.timeStage || stage.bossA == null || stage.bossA.length() == 0) return;
    int elapsedSec = (int) ((now - startTime) / 1000L);
    if (boss.active) return;
    if (bossEncounterIndex < 0) {
        if (!encounterStarted[0] && elapsedSec >= stage.bossATimeSec) { spawnBoss(0); return; }
        if (!encounterStarted[1] && elapsedSec >= stage.bossBTimeSec) { spawnBoss(1); return; }
        if (stage.hard && !encounterStarted[2] && elapsedSec >= 1800) { spawnBoss(2); return; }
    }
    if (mode.completion == ContentCatalog.COMPLETE_ON_DEATH && elapsedSec >= 1200
            && nextEndlessEncounter < 6) {
        spawnBoss(nextEndlessEncounter % 2);
        nextEndlessEncounter++;
    }
}
```

Relevant earlier finding, re-confirmed here: `update()` opens with `if (state != STATE_RUNNING)
return;`, so the director cannot run after a run has ended.

---

## 3. Tests added

`tools/tests/me/holojava/fullgame/RunWorldBossEncounterTest.java` — 34 checks, driving the
production `updateBossDirector`, `spawnBoss`, and `defeatBoss`.

| # | Test | Proves |
|---|---|---|
| 1 | an encounter waits for its time gate | No boss one second early; the boss appears at the gate as encounter 0; a live boss blocks the next spawn |
| 2 | an encounter is never started twice | After a defeat the next gate can fire, but a non-hard stage stops after its two encounters |
| 3 | a defeat records the encounter | `encounterDefeated[0]` set, `bossEncounterIndex` back to −1, boss deactivated |
| 4 | victory needs the final encounter | Beating only boss A does **not** win a normal stage; beating boss B does |
| 5 | a hard stage needs its third encounter | Two encounters are not enough on stage1h; the third appears at the 1800 s gate and wins it |
| 6 | a time stage runs no director | No boss and no encounter record on time1 |
| 7 | endless mode repeats encounters up to the cap | The endless counter advances only when no boss is live, and never passes 6 |

---

## 4. Verification

Full suite: **981 checks, 0 failures** across ten suites. Build exit 0, verifier
`PASS: JAR=836627 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.

### Negative controls

| Control | Mutation | Result |
|---|---|---|
| B1 | boss spawns without waiting for its time gate | FAIL, 1 check |
| B2 | `encounterStarted` no longer blocks a repeat | FAIL, 7 checks |
| B3 | a live boss no longer blocks a second spawn | FAIL, 1 check |
| B4 | `defeatBoss` stops recording the encounter | FAIL, 5 checks |
| B5 | victory no longer requires the final encounter | FAIL, 3 checks |
| B6 | hard stages use slot 1 instead of slot 2 | FAIL, 1 check |
| B7 | time stages no longer skip the director | **PASS — not detected, see below** |

**B3 initially passed too.** The live-boss guard is the *only* thing the director's
`if (boss.active) return;` decides in Endless Mode — the timed branches are all skipped once an
encounter is live — and the first version of test 7 probed at 660 s, below the 1200 s endless
threshold, so the endless branch could not fire either way. Moved past the threshold; B3 now fails.

**B7 honestly explained.** `time1` declares `bossA = ""`, so the `stage.timeStage` clause is
redundant *for the current catalog*: the very next clause, `stage.bossA.length() == 0`, already
returns. Removing `stage.timeStage` therefore changes nothing today. No test can detect it unless a
time stage is ever given a boss. The clause is correct defensive code and was left alone.

---

## 5. Findings

No defect found in the encounter subsystem. Two properties worth recording:

- **The clear reward cannot be paid twice.** `defeatBoss` now has an idempotence guard (added with
  decision C), and every entry path is gated on `boss.active`.
- **The `stage.timeStage` clause is currently redundant**, as described under B7.

---

## 6. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldBossEncounterTest.java   (new, 34 checks)
```

No production source changed; the JAR is byte-identical in size to the completion/unlock commit.

---

## 7. Still unverified

Boss attack patterns, contact damage, animations, and anything involving spawned assets or
rendering — `AssetLibrary` cannot be constructed off-device. All device-level RMS questions carry
forward. No emulator or handset run was performed.