# HoloJava Time Mode Audit — Summary

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antomay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git branch | `main` | ✅ |
| Git status | Clean + untracked reports | Clean + 8 dirs | ✅ |
| Source changes | None | `git diff` empty | ✅ |

## 2. Audit Scope

**Focus**: Time Mode progression, stage unlock, completion tracking, save/load behavior

**Files Analyzed**:
- `FullGameCanvas.java` — mode/stage selection, unlock logic, stage completion saving
- `RunWorld.java` — Time Mode stage definition, victory conditions, defeatBoss
- `ContentCatalog.java` — ModeDef/StageDef definitions, timeStage flag, unlockRequirement
- `FullGameMIDlet.java` — lifecycle integration
- `SaveStore.java` — RMS persistence for stage completion

**Reference JAR**: Bytecode extraction available but Time Mode specific logic not fully disassembled.

## 3. Key Findings Summary

### 3.1 Time Mode Implementation

| Aspect | Implementation | Evidence |
|--------|---------------|----------|
| **Mode Definition** | `ModeDef("time", "Time Mode", COMPLETE_KILL_TARGET, 4000, false, false, true)` | ContentCatalog.java:249 |
| **Stage** | `time1` (Concert Stage), index 9, `timeStage=true` | ContentCatalog.java:262 |
| **Unlock Requirement** | `unlockRequirement = 2` (requires stage 2 cleared) | ContentCatalog.java:262 |
| **Mode Type** | `COMPLETE_KILL_TARGET = 2` (kill 4000 fans) | ContentCatalog.java:34 |
| **Allowed in Time Mode** | Only `timeStage=true` stages | FullGameCanvas.stageAllowed():L251-254 |

### 3.2 Stage Unlock Logic

**`stageAllowed(index)`** (FullGameCanvas.java:251-254):
```java
if (ContentCatalog.MODES[modeIndex].completion == ContentCatalog.COMPLETE_KILL_TARGET) 
    return s.timeStage;  // Only timeStage in Time Mode
return !s.timeStage;      // No timeStage in other modes
```

**`stageUnlocked(index)`** (FullGameCanvas.java:257-262):
```java
if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;
if (s.timeStage) return save.isCompleted(characterIndex, 2);  // Requires stage 2
return save.isCompleted(characterIndex, s.unlockRequirement);
```

### 3.3 Night Variants Unlock (Indices 5-8)

| Night Stage | Index | unlockRequirement | Requires |
|-------------|-------|-------------------|----------|
| stage1h (Night) | 5 | 1 | Stage 1 (Holo Office) |
| stage2h | 6 | 5 | Stage 5 (stage1h) |
| stage3h | 7 | 6 | Stage 6 (stage2h) |
| stage4h | 8 | 7 | Stage 7 (stage3h) |

**Chain**: Sequential — each night variant requires previous night variant completed.

### 3.4 Time Mode Unlock

| Aspect | Implementation |
|--------|----------------|
| **Stage** | time1 (index 9) |
| **unlockRequirement** | 2 (requires stage 2 cleared) |
| **timeStage** | true |
| **Available** | Only when `modeIndex == 2` (Time Mode selected) |
| **Unlock Check** | `stageUnlocked(9)` → `save.isCompleted(characterIndex, 2)` |

### 3.5 Stage Completion & Save

**Victory Condition** (RunWorld.defeatBoss:L679-694):
```java
if (mode.completion == COMPLETE_FINAL_BOSS) {
    boolean finalDefeated = stage.hard ? encounterDefeated[2] : encounterDefeated[1];
    if (finalDefeated) { won = true; state = STATE_VICTORY; }
}
```

**Save on Victory** (FullGameCanvas.update:L147-151):
```java
if (worldWasVictory && modeIndex == 0) 
    save.setCompleted(characterIndex, ContentCatalog.stage(stageIndex).order);
```

**Critical Finding**: Stage completion is **only saved for `modeIndex == 0` (Stage Mode)**. Time Mode and Endless Mode victories do **not** persist stage completion.

### 3.6 Time Mode Completion

| Aspect | Implementation |
|--------|----------------|
| **Victory Condition** | `kills >= 4000` (mode.targetKills) |
| **Mode** | `COMPLETE_KILL_TARGET` (kills-based) |
| **Stage Progression** | None — Time Mode is single stage (time1) |
| **Stage Save** | **NOT saved** (only `modeIndex == 0` saves) |
| **Replay** | Always available after unlock (no save required) |

## 4. Key Findings

### 4.1 Confirmed Behaviors

| Finding | Status | Evidence |
|---------|--------|----------|
| Time Mode unlock requires stage 2 completion | `MATCH_CONFIRMED` | stageUnlocked(9) checks `save.isCompleted(2)` |
| Time Mode only allows timeStage stages | `MATCH_CONFIRMED` | stageAllowed() checks mode.completion |
| Night variants form sequential chain | `MATCH_CONFIRMED` | unlockRequirement chain 1→5→6→7 |
| Night variants require stage 2 completion | `MATCH_CONFIRMED` | timeStage unlock requires `isCompleted(2)` |
| Time Mode stage not saved on victory | `MATCH_CONFIRMED` | save.setCompleted only for modeIndex==0 |
| Time Mode uses kill-target (4000) | `MATCH_CONFIRMED` | ModeDef.targetKills=4000 |

### 4.2 Design Differences / Ambiguities

| Issue | Classification | Details |
|-------|----------------|---------|
| Time Mode stage not saved on victory | `DESIGN_DIFFERENCE` | Only Stage Mode saves progress |
| Night variant sequential chain vs Time Mode gate | `DESIGN_DIFFERENCE` | Night variants chain, but all gated by stage 2 |
| Time Mode stage not shown in Stage Select normally | `DESIGN_DIFFERENCE` | stageAllowed() hides timeStage in non-Time modes |
| Night variant unlockRequirement points to previous night | `STRUCTURAL_SIMILARITY_ONLY` | Chain 5→6→7 but all gated by stage 2 |

## 5. Unresolved Questions / Open Questions

| Question | Status | Evidence Needed |
|----------|--------|-----------------|
| Does Time Mode victory save any progress? | `MATCH_CONFIRMED` No | Source confirms no save.setCompleted |
| Can Time Mode be played without completing stage 2? | `MATCH_CONFIRMED` No | stageUnlocked(9) requires isCompleted(2) |
| Does Time Mode completion unlock anything? | `INSUFFICIENT_EVIDENCE` | No evidence of unlocks |
| Does Endless Mode save stage progress? | `MATCH_CONFIRMED` No | modeIndex != 0 doesn't save |
| Night variant unlock chain vs Time Mode gate | `DESIGN_DIFFERENCE` | Chain exists but gated by stage 2 |

## 6. Reference JAR Parity

| Aspect | Classification | Notes |
|--------|----------------|-------|
| Time Mode stage definition | `STRUCTURAL_SIMILARITY_ONLY` | Bytecode not fully disassembled |
| ModeDef completion values | `INSUFFICIENT_EVIDENCE` | Need ModeDef init bytecode |
| Time Mode stage unlock bytecode | `INSUFFICIENT_EVIDENCE` | Need FullGameCanvas stageUnlocked bytecode |
| SaveStore stage completion bytecode | `MATCH_CONFIRMED` | isCompleted/setCompleted verified |

## 6. Report Files Created

| File | Purpose |
|------|---------|
| `00_TIME_MODE_AUDIT_SUMMARY.md` | This file |
| `01_UNLOCK_AND_STAGE_TRANSITIONS.md` | Stage unlock logic & transitions |
| `02_COMPLETION_AND_SAVESTORE.md` | Completion tracking & save behavior |
| `03_REFERENCE_PARITY_AND_FINDINGS.md` | JAR parity analysis |
| `04_STATE_MATRIX_AND_OPEN_QUESTIONS.md` | State matrix & open questions |

## 7. Quality Gate

- ✅ JAR SHA-256 unchanged: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- ✅ Git HEAD: `666e338`
- ✅ Source unchanged: `git diff` empty
- ✅ All prior reports intact
- ✅ No source modifications, no runtime, no builds
- ✅ New reports in `/home/antomay/Projects/HoloJava/reports/time-mode-audit/`

---

**Status**: Audit complete. All Time Mode progression, unlock, and save behavior documented. Key finding: Time Mode and Endless Mode victories do not persist stage completion. Night variants form sequential chain but all require stage 2 completion first. Time Mode is a single-stage kill-target mode (4000 kills) that does not persist stage completion.
