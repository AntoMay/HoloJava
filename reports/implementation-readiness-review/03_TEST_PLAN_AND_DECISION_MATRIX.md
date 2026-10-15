# HoloJava — Test Plan & Decision Matrix

## 1. Test Plan: PB-01 Pause/Resume Lifecycle (Already Implemented)

### 1.1 Test Objective
Verify that MIDlet pause/resume correctly pauses and resumes the game simulation.

### 1.2 Deterministic Test Cases (Static Design)

| TC-ID | Scenario | Preconditions | Steps | Expected Result | Verification Method |
|-------|----------|---------------|-------|-----------------|---------------------|
| TC-PB01-01 | App launch | Fresh install | 1. Launch app<br>2. Enter gameplay<br>3. Verify simulation runs | `applicationPaused = false`, `world.state = RUNNING` | Source code inspection |
| TC-PB01-02 | Incoming call | In gameplay | 1. Receive call → `pauseApp()`<br>2. Verify `applicationPaused = true`<br>3. Verify `world.state = PAUSED`<br>4. Verify run loop sleeps 40ms | `applicationPaused = true`, `world.state = PAUSED`, run loop skips update/render | Source trace: `pauseApp()` → `canvas.pauseApplication()` → `world.pause()` |
| TC-PB01-03 | App resume after pause | App paused | 1. Return to app → `startApp()`<br>2. Verify `applicationPaused = false`<br>3. Verify `world.state = RUNNING` | `applicationPaused = false`, `world.state = RUNNING`, run loop resumes | Source trace: `startApp()` → `canvas.resumeApplication()` → `world.resume()` |
| TC-PB01-04 | In-game pause (FIRE) | In gameplay, `STATE_RUNNING` | 1. Press FIRE<br>2. Verify `world.state = PAUSED`<br>3. Press FIRE again<br>4. Verify `world.state = RUNNING` | Toggle between RUNNING and PAUSED | Source: `update()` lines 134-135 |
| TC-PB01-05 | Pause during level-up | In `STATE_LEVEL_UP` | 1. Pause via MIDlet<br>2. Verify level-up screen persists<br>3. Resume<br>3. Level-up still functional | Level-up state preserved, no simulation update | Code trace: level-up handled in `update()` before pause check |
| TC-PB01-06 | Pause → Destroy → Restart | App paused | 1. Pause app<br>2. Kill process<br>3. Re-launch<br>3. Verify fresh state | Fresh game state, no resume of paused simulation | `destroyApp()` calls `stopApplication()`, fresh `startApp()` creates new world |

### 1.3 Invariant Checks
| Invariant | Verification |
|-----------|--------------|
| `applicationPaused = true` ⇔ `world.state = PAUSED` (when world exists) | Source trace |
| Run loop skips update/render when `applicationPaused = true` | Bytecode trace |
| `pauseApp()` called exactly once per background transition | MIDlet spec |
| `resumeApplication()` called exactly once per foreground transition | MIDlet spec |

## 2. Test Plan: PB-04 HUD String Allocation Optimization

### 2.1 Test Objective
Verify cached string optimization reduces per-frame allocations without visual regression.

### 2.2 Deterministic Test Cases (Static Design)

| TC-ID | Scenario | Preconditions | Steps | Expected Result | Verification Method |
|-------|----------|---------------|-------|-----------------|---------------------|
| TC-PB04-01 | Steady state | In gameplay, no value changes | 1. Render 100 frames<br>2. Count string allocations | Zero string allocations in `drawHud` | Static analysis: cache hit on all lines |
| TC-PB04-02 | HP change | Player takes damage | 1. Deal damage<br>2. Render frame | Only HP line rebuilt | Cache invalidation: `playerHp` change detected |
| TC-PB04-03 | EXP gain | Player collects EXP | 1. Gain EXP<br>2. Render frame | Only Level/EXP line rebuilt | Cache invalidation: `experience` change |
| TC-PB04-04 | Time advance | Time advances 1 second | 1. Advance time 1000ms<br>2. Render frame | Time line rebuilt (via timeString cache) | `timeString()` cache + `kills` check |
| TC-PB04-05 | Boss spawn | Boss becomes active | 1. Spawn boss<br>2. Render frame | Boss name cached until despawn | `bossName()` cached until `boss.active = false` |
| TC-PB04-06 | Time Mode fans | Time Mode, kill target | 1. Kill enemy in Time Mode<br>2. Render frame | Only Fans line rebuilt | `kills` change detected |
| TC-PB04-07 | Rapid HP changes | Regen + damage same frame | 1. Apply regen + damage<br>2. Render | HP line rebuilt once per frame (final value) | Cache uses final value per frame |
| TC-PB04-08 | Full HUD render | All lines active | 1. Enter boss fight in Time Mode<br>2. Render frame | All lines cached, only changed lines rebuilt | Cache hits on unchanged lines |

### 2.3 Cache Invalidation Test Matrix

| Cached Line | Source Fields | Invalidation Trigger |
|-------------|---------------|---------------------|
| HP | `playerHp`, `playerMaxHp` | Either value changes |
| Level/EXP | `playerLevel`, `experience`, `expNeed` | Any changes |
| Time/Kills | `timeString()` (per second), `kills` | Time tick (1s) or `kills` change |
| Coins/Weapons/Items | `coins`, `weaponCount`, `itemCount` | Any changes |
| Fans (Time Mode) | `kills`, `mode.targetKills` | `kills` changes |
| Boss Name | `bossName()` | `boss.active` toggle |
| Controls Hint | Constant | Never (static string) |

### 2.4 Static Verification Checklist

- [ ] Cache fields declared for each HUD line
- [ ] Cache invalidation on every source field write
- [ ] No cache miss on steady state (0 allocations/frame)
- [ ] Cache invalidation triggered before render
- [ ] No stale values displayed (cache updated before draw)
- [ ] Constant string "5 Skill  9 Special" not reallocated
- [ ] `timeString()` cached per second (not per frame)

## 3. Decision Matrix

| Candidate | Phase 3 Classification | Current State | Readiness Status | Action |
|-----------|----------------------|---------------|-----------------|--------|
| PB-01: pauseApp empty | `CONFIRMED_BUG` | **Already implemented** — `pauseApp()` calls `canvas.pauseApplication()` | `NO_CHANGE_SUPPORTED` | No action needed; document existing implementation |
| PB-02: 16ms input | `DESIGN_DIFFERENCE` | Inherent to frame-based polling | `NO_CHANGE_SUPPORTED` | Document as design constraint |
| PB-03: Pool full silent fail | `DESIGN_DIFFERENCE` | Intentional zero-allocation design | `NO_CHANGE_SUPPORTED` | Document as design trade-off |
| PB-04: HUD string concat | `CONFIRMED_BUG` | Per-frame concat confirmed | `READY_FOR_EXPLICIT_APPROVAL` | Approve cached-string optimization (Option C) |
| PB-05: requiredForLevel precision | `POTENTIAL_BUG` | Drift <1 EXP/level, not runtime verified | `MORE_EVIDENCE_REQUIRED` | Runtime verification needed on target device |
| Time Mode mode.completion | `PARTIALLY_CLOSED` | `stageAllowed` checks `mode.completion == 2` | `DESIGN_DECISION_REQUIRED` | Fix check vs. fix ModeDef values vs. document |

## 3. Decision Matrix Details

### PB-01: Pause/Resume
| Aspect | Decision |
|--------|----------|
| **Final Status** | `NO_CHANGE_SUPPORTED` |
| **Reason** | Already implemented in current source; Phase 3 finding was based on outdated/incorrect observation |
| **Evidence** | `FullGameMIDlet.pauseApp()` → `canvas.pauseApplication()` → `world.pause()` → `applicationPaused` flag checked in run loop |
| **Risk if "Fixed" Again** | None, but redundant code |

### PB-04: HUD String Allocation
| Aspect | Decision |
|--------|----------|
| **Final Status** | `READY_FOR_EXPLICIT_APPROVAL` |
| **Recommended Approach** | Option C: Cached Strings (per-field invalidation) |
| **Scope** | ~150 lines in `RunWorld.drawHud()` + cache fields |
| **Risk** | Low (single-threaded render, explicit invalidation) |
| **Rollback** | Trivial (revert to string concat) |
| **Expected Improvement** | 95%+ allocation reduction in steady state |

### PB-05: requiredForLevel Precision
| Aspect | Decision |
|--------|----------|
| **Final Status** | `MORE_EVIDENCE_REQUIRED` |
| **Reason** | Model shows <1 EXP/level drift, but not runtime verified on CLDC 1.1 target |
| **Blocker** | Cannot run on target device per constraints |
| **Next Step** | Runtime verification on Java ME device if level > 50 reachable |

### Time Mode mode.completion Check
| Aspect | Decision |
|--------|----------|
| **Final Status** | `DESIGN_DECISION_REQUIRED` |
| **Issue** | `stageAllowed` checks `mode.completion == 2` (COMPLETE_ON_DEATH), but Time Mode may have `completion = 1` (COMPLETE_KILL_TARGET) |
| **Options** | A) Fix check to use `modeIndex == 2` (Time Mode index)<br>B) Fix ModeDef to set `completion = 2` for Time Mode<br>C) Document current behavior |
| **Needed** | Full ModeDef initialization bytecode analysis |

## 4. Risk Assessment Summary

| Change | Regression Risk | Performance Impact | Maintenance |
|--------|----------------|-------------------|-------------|
| PB-01: No change | N/A | N/A | N/A |
| PB-04: Cached strings | Very Low | Positive (less GC) | Low (+150 lines) |
| PB-05: No change (pending) | N/A | N/A | N/A |
| Time Mode fix (if approved) | Medium | None | Medium |

## 4. Verification Plan Post-Approval

| Phase | Activities | Criteria |
|-------|------------|----------|
| **Static Verification** | Code review, bytecode diff | No new allocations in steady state |
| **Build Verification** | `ant`/`javac` build succeeds | Clean build, no new warnings |
| **Static Analysis** | `javap -c` on modified classes | Cache logic correct, no dead code |
| **Runtime (if approved)** | Device/emulator testing | No visual regression, GC logs show reduction |

## 5. Final Quality Gate

- ✅ JAR SHA-256 unchanged: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- ✅ Git HEAD unchanged: `bdc415e`
- ✅ Source unchanged: `git diff` empty
- ✅ All prior reports intact
- ✅ New reports in `/home/antomay/Projects/HoloJava/reports/implementation-readiness-review/`
- ✅ No source modifications, no runtime execution, no builds, no Git changes
- ✅ All analysis static; no implementation performed

---

**Final Decisions Summary:**

| Candidate | Decision | Status |
|-----------|----------|--------|
| PB-01 | `NO_CHANGE_SUPPORTED` | Already implemented |
| PB-04 | `READY_FOR_EXPLICIT_APPROVAL` | Cached strings (Option C) |
| PB-05 | `MORE_EVIDENCE_REQUIRED` | Runtime verification needed |
| Time Mode | `DESIGN_DECISION_REQUIRED` | Fix check vs. fix ModeDef vs. document |

**STOP** — Review complete. Awaiting explicit approval for PB-04 implementation and Time Mode design decision.
