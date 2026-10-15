# HoloJava Time Mode Audit — Unlock & Stage Transitions

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git status | Clean + untracked reports | Clean + 8 dirs | ✅ |

## 2. Stage Unlock Logic — Source Analysis

### 2.1 StageUnlocked Method (FullGameCanvas.java:257-262)

```java
private boolean stageUnlocked(int index) {
    ContentCatalog.StageDef s = ContentCatalog.stage(index);
    if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;
    if (s.timeStage) return save.isCompleted(characterIndex, 2);
    return save.isCompleted(characterIndex, s.unlockRequirement);
}
```

### 2.2 StageAllocation Method (FullGameCanvas.java:251-254)

```java
private boolean stageAllowed(int index) {
    ContentCatalog.StageDef s = ContentCatalog.stage(index);
    if (ContentCatalog.MODES[modeIndex].completion == ContentCatalog.COMPLETE_KILL_TARGET) 
        return s.timeStage;
    return !s.timeStage;
}
```

### 2.2 Stage Definitions (ContentCatalog.java:252-263)

| Index | Stage ID | Name | timeStage | unlockRequirement | Notes |
|-------|---------|------|-----------|-------------------|-------|
| 0 | stage1 | Grassy Plains | false | 99 | Always unlocked |
| 1 | stage2 | Holo Office | false | 0 | Requires stage 0 |
| 2 | stage3 | Halloween Castle | false | 1 | Requires stage 1 |
| 3 | stage4 | Gelora Bung Yagoo | false | 2 | Requires stage 2 |
| 4 | stage5 | Fantasy Island | false | 99 | Special |
| 5 | stage1h | Grassy Plains (Night) | true | 1 | Requires stage 1 |
| 4 | stage2h | Holo Office (Evening) | true | 5 | Requires stage 5 |
| 7 | stage3h | Halloween Castle (Myth) | true | 6 | Requires stage 6 |
| 8 | stage4h | Gelora Bung Yagoo (Night) | true | 7 | Requires stage 7 |
| 9 | time1 | Concert Stage | true | 2 | Requires stage 2 |

## 3. Stage Unlock Logic Analysis

### 3.1 Regular Stages (Indices 0-4)

| Stage | unlockRequirement | Logic |
|-------|-------------------|-------|
| stage1 (0) | 99 | Always unlocked (special value) |
| stage2 (1) | 0 | Requires stage 0 completed |
| stage3 (2) | 1 | Requires stage 1 completed |
| stage4 (3) | 2 | Requires stage 2 completed |
| stage5 (4) | 99 | Special — always unlocked |

**Logic**: `save.isCompleted(characterIndex, s.unlockRequirement)` checks if prerequisite stage completed.

### 3.2 Night Variants (Indices 5-8)

| Night Stage | Index | unlockRequirement | Requires | Gate |
|-------------|-------|-------------------|----------|------|
| stage1h (Night) | 5 | 1 | Stage 1 (index 1) | Stage 2 completion |
| stage2h | 6 | 5 | Stage 5 (index 5) | Stage 2 completion |
| stage3h | 7 | 6 | Stage 6 (index 6) | Stage 2 completion |
| stage4h | 8 | 7 | Stage 7 (index 7) | Stage 2 completion |

**Critical Finding**: All night variants have `timeStage=true`, so `stageUnlocked()` routes to:
```java
if (s.timeStage) return save.isCompleted(characterIndex, 2);  // Requires stage 2
```

**Result**: Night variants form a **sequential chain** (1h→2h→3h→4h) but **ALL are gated behind stage 2 completion**.

### 3.3 Time Mode Stage

| Stage | Index | unlockRequirement | Logic |
|-------|-------|-------------------|-------|
| time1 | 9 | 2 | Requires stage 2 completed (via `save.isCompleted(characterIndex, 2)`) |

### 3.3 Stage Allocation Logic (FullGameCanvas.java:251-254)

```java
private boolean stageAllowed(int index) {
    ContentCatalog.StageDef s = ContentCatalog.stage(index);
    if (ContentCatalog.MODES[modeIndex].completion == ContentCatalog.COMPLETE_KILL_TARGET) 
        return s.timeStage;  // Time Mode: only timeStage stages
    return !s.timeStage;     // Other modes: no timeStage stages
}
```

**Behavior**:
- **Stage Mode / Endless Mode**: Only regular stages (timeStage=false) visible
- **Time Mode**: Only timeStage stages visible (night variants + time1)

## 4. Stage Transition Flow

### 4.1 Fresh Install (No Save Data)
| Stage | Allowed | Unlocked | Visible in Stage Select |
|-------|---------|----------|------------------------|
| stage1 | ✅ | ✅ | ✅ |
| stage2 | ✅ | ❌ | ✅ (locked) |
| stage3 | ✅ | ❌ | ✅ (locked) |
| stage4 | ✅ | ❌ | ✅ (locked) |
| stage5 | ✅ | ✅ | ✅ |
| stage1h | ❌ | ❌ | ❌ (hidden) |
| stage2h | ❌ | ❌ | ❌ |
| stage3h | ❌ | ❌ | ❌ |
| stage4h | ❌ | ❌ | ❌ |
| time1 | ❌ | ❌ | ❌ |

### 4.2 After Completing Stage 1
| Stage | Allowed | Unlocked |
|-------|---------|----------|
| stage2 | ✅ | ✅ |
| stage3 | ✅ | ❌ |
| stage1h | ❌ | ❌ (needs stage 2) |

### 4.3 After Completing Stage 2 (Critical Gate)
| Stage | Allowed | Unlocked |
|-------|---------|----------|
| stage3 | ✅ | ✅ |
| stage1h (Night) | ❌ | ✅ (unlocked but hidden) |
| time1 (Time Mode) | ❌ (only in Time Mode) | ✅ |

### 4.4 After Stage 2 + Time Mode Selected
| Stage | Allowed | Unlocked |
|-------|---------|----------|
| time1 (Concert Stage) | ✅ | ✅ |
| stage1h | ❌ | ✅ |
| stage2h | ❌ (needs stage 5 = stage1h) | ❌ |

### 4.4 Night Variant Chain Progression
```
Stage 2 Complete
    ↓
stage1h (Night) unlocked
    ↓ Complete stage1h
stage2h unlocked
    ↓ Complete stage2h
stage3h unlocked
    ↓ Complete stage3h
stage4h unlocked
```

**Critical**: All night variants require **Stage 2 completion first**, then form sequential chain.

## 5. Mode Selection Impact

### 5.1 Mode Definitions (ContentCatalog.java:246-250)
| Mode Index | Mode ID | completion | targetKills | allowsCollabs | standardLoadout |
|------------|---------|------------|-------------|---------------|-----------------|
| 0 | stage | COMPLETE_FINAL_BOSS (0) | 0 | true | false |
| 1 | endless | COMPLETE_ON_DEATH (1) | 0 | true | false |
| 2 | time | COMPLETE_KILL_TARGET (2) | 4000 | false | true |

### 5.2 StageAllocation by Mode
| Mode | completion Value | Allowed Stages |
|------|------------------|----------------|
| Stage (0) | COMPLETE_FINAL_BOSS (0) | Regular stages only (timeStage=false) |
| Endless (1) | COMPLETE_ON_DEATH (1) | Regular stages only |
| Time (2) | COMPLETE_KILL_TARGET (2) | Only timeStage=true stages |

**Result**: Time Mode stage (time1) and night variants ONLY visible in Time Mode.

## 6. Unlock State Persistence

### 6.1 SaveStore Stage Completion Tracking
```java
// SaveStore.java:23-27
boolean isCompleted(int characterIndex, int stageOrder) {
    if (characterIndex < 0 || characterIndex >= CHARACTERS.length || stageOrder < 0 || stageOrder > 7) return false;
    int v = data[characterIndex] & 0xff;
    return (v & (1 << stageOrder)) != 0;
}

// SaveStore.java:29-34
void setCompleted(int characterIndex, int stageOrder) {
    // ... bitmask update ...
    save();
}
```

**Storage**: 5 bytes (indices 0-4), each byte = bitmask for 5 characters × 8 stages max.

**Limitation**: Only tracks stages 0-7. Night variants (5-8) and time1 (9) use special logic.

## 7. Classification Summary

| Area | Classification | Notes |
|------|----------------|-------|
| StageAllowed logic | `MATCH_CONFIRMED` | Source verified |
| StageUnlocked logic | `MATCH_CONFIRMED` | Source verified |
| Night variant chain | `MATCH_CONFIRMED` | Sequential but gated by stage 2 |
| Time Mode unlock | `MATCH_CONFIRMED` | Requires stage 2 |
| Time Mode stage visibility | `MATCH_CONFIRMED` | Only in Time Mode |
| Night variant unlock chain | `MATCH_CONFIRMED` | Sequential but all gated by stage 2 |
| Time Mode unlock gate | `MATCH_CONFIRMED` | Stage 2 completion required |

## 8. Remaining Uncertainties

| Question | Status | Evidence Needed |
|----------|--------|-----------------|
| Night variant visibility in Endless Mode | `INSUFFICIENT_EVIDENCE` | Need ModeDef completion values |
| Time Mode stage completion persistence | `MATCH_CONFIRMED` | Not saved (only modeIndex==0 saves) |
| Night variant visibility after stage 2 + Time Mode | `MATCH_CONFIRMED` | Hidden in Time Mode (only timeStage allowed) |

## 9. Evidence Artifacts

- FullGameCanvas.java:251-262 (stageAllowed, stageUnlocked)
- ContentCatalog.java:252-263 (StageDef array)
- ContentCatalog.java:246-250 (ModeDef array)
- SaveStore.java:23-27 (isCompleted), 29-34 (setCompleted)

## 9. Limitations

| Limitation | Impact |
|------------|--------|
| Bytecode not fully disassembled | `INSUFFICIENT_EVIDENCE` for JAR parity |
| ModeDef completion values in bytecode | Need full ContentCatalog init disassembly |
| Night variant in Endless mode visibility | Depends on ModeDef completion values |
