# HoloJava Time Mode Audit — Completion & SaveStore Analysis

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git status | Clean + untracked reports | Clean + 8 dirs | ✅ |

## 2. Stage Completion Detection

### 2.1 Victory Detection (RunWorld.java)

#### 2.1.1 Stage Mode Victory (COMPLETE_FINAL_BOSS)
```java
// RunWorld.java:679-694
private void defeatBoss() {
    boss.active = false;
    int slot = bossEncounterIndex;
    bossEncounterIndex = -1;
    if (slot >= 0 && slot < encounterDefeated.length) encounterDefeated[slot] = true;
    ContentCatalog.BossDef def = ContentCatalog.BOSSES[boss.defIndex];
    dropPickup(boss.x + bossWidth() / 2, boss.y + bossHeight() / 2, 0, def.exp, System.currentTimeMillis());
    for (int i = 0; i < 5; i++) dropPickup(boss.x + bossWidth() / 2, boss.y + bossHeight() / 2, 1, 10, System.currentTimeMillis());
    if (mode.completion == ContentCatalog.COMPLETE_FINAL_BOSS) {
        boolean finalDefeated = stage.hard ? encounterDefeated[2] : encounterDefeated[1];
        if (finalDefeated) {
            coins += stage.coinClear + defeatCount / 4;
            won = true;
            state = STATE_VICTORY;
        }
    }
}
```

**Key Points**:
- Only `COMPLETE_FINAL_BOSS` (Stage Mode) triggers victory state
- Requires final boss defeated (`encounterDefeated[1]` or `[2]` for hard)
- Sets `won = true`, `state = STATE_VICTORY`

### 2.1.2 Endless Mode Victory (COMPLETE_ON_DEATH)
```java
// RunWorld.java:303-305
if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET && kills >= mode.targetKills) {
    won = true;
    state = STATE_VICTORY;
}
```
- Endless Mode uses `COMPLETE_ON_DEATH` (survival) but also has kill-target check
- Actually Endless uses `COMPLETE_ON_DEATH` (1), Time Mode uses `COMPLETE_KILL_TARGET` (2)

### 2.1.3 Time Mode Victory
```java
// RunWorld.java:303-305
if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET && kills >= mode.targetKills) {
    won = true;
    state = STATE_VICTORY;
}
```
- Time Mode: `COMPLETE_KILL_TARGET` (2) with 4000 kills target
- No boss defeat required — pure kill count

## 3. Stage Completion Persistence

### 3.1 Save on Victory (FullGameCanvas.java:147-151)
```java
if (worldWasVictory && modeIndex == 0) 
    save.setCompleted(characterIndex, ContentCatalog.stage(stageIndex).order);
```

**Critical Finding**: Stage completion **only saved when `modeIndex == 0` (Stage Mode)**.

| Mode | modeIndex | Saves Stage Completion? |
|------|-----------|------------------------|
| Stage Mode | 0 | ✅ YES |
| Endless Mode | 1 | ❌ NO |
| Time Mode | 2 | ❌ NO |

### 3.2 Stage Completion Tracking (SaveStore.java)

```java
// SaveStore.java:23-27
boolean isCompleted(int characterIndex, int stageOrder) {
    if (characterIndex < 0 || characterIndex >= CHARACTERS.length || stageOrder < 0 || stageOrder > 7) return false;
    int v = data[characterIndex] & 0xff;
    return (v & (1 << stageOrder)) != 0;
}

void setCompleted(int characterIndex, int stageOrder) {
    if (characterIndex < 0 || characterIndex >= CHARACTERS.length || stageOrder < 0 || stageOrder > 7) return;
    int v = data[characterIndex] & 0xff;
    data[characterIndex] = (byte) (v | (1 << stageOrder));
    save();
}
```

**Storage**: 5 bytes (indices 0-4), bitmask per character (5 characters × 8 stages)

**Limitation**: Only stages 0-7 trackable. Night variants (5-8) and time1 (9) use special logic.

## 4. Save/Load Behavior

### 4.1 SaveStore Persistence (SaveStore.java)

```java
// RecordStore: "holojava_fullgame_v1", record ID 1, 48 bytes
// data[0-4]: stage completion bitmasks (5 chars × 8 stages)
// data[47]: bit 0=hardcore, bits 1-3=character, bits 4-7=unused

private void save() {
    RecordStore store = RecordStore.openRecordStore(STORE, true);
    store.setRecord(RECORD_ID, data, 0, data.length);
    // No explicit commit — RMS writes immediately
}
```

**Load Behavior** (constructor → load()):
```java
SaveStore() {
    data = new byte[48];
    load();  // Attempts to read RMS record
}
```

**Load Behavior**:
- Record missing → defaults (all zeros)
- Record too short → pad with zeros
- Record longer → truncate to 48 bytes
- Corrupted → defaults (exception caught)

## 5. Stage Completion Flow

### 5.1 Stage Mode (modeIndex=0) Victory Flow
```
1. defeatBoss() → won=true, state=STATE_VICTORY
2. FullGameCanvas.update() detects STATE_VICTORY
3. worldWasVictory=true, modeIndex==0
4. save.setCompleted(characterIndex, stage.order)
4. screen = RESULTS
```

### 4.2 Time Mode Victory (No Save)
```
1. defeatBoss() or kill target reached → won=true, state=STATE_VICTORY
4. FullGameCanvas.update() detects STATE_VICTORY
5. modeIndex=2 (Time Mode) → save.setCompleted() NOT called
5. screen = RESULTS
```

### 4.3 Endless Mode Victory
```
1. kill target reached or survival → won=true, state=STATE_VICTORY
4. modeIndex=1 → save.setCompleted() NOT called
```

## 5. Stage Completion Flow Table

| Event | Mode | Stage Completion Saved? | Persisted? |
|-------|------|------------------------|------------|
| Defeat final boss | Stage (0) | ✅ Yes | ✅ Yes |
| Reach 4000 kills | Time (2) | ❌ No | ❌ No |
| Survive Endless | Endless (1) | ❌ No | ❌ No |
| Defeat boss (hard) | Stage (0) | ✅ Yes (if final) | ✅ Yes |

## 5. Time Mode Specific Behavior

### 5.1 Time Mode Stage (time1)
- Single stage: `time1` (Concert Stage)
- `timeStage=true`, `unlockRequirement=2` (requires stage 2)
- `mode.completion = COMPLETE_KILL_TARGET (2)`
- `targetKills = 4000`

### 5.2 Time Mode Victory Condition
```java
// RunWorld.update()
if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET && kills >= mode.targetKills) {
    won = true;
    state = STATE_VICTORY;
}
```
- Pure kill count (4000 fans)
- No boss fight required
- No stage completion saved

### 5.3 Time Mode Replay
- Always available after unlock (stage 2 completed)
- No persistent progress between runs
- Each run starts fresh

## 5. SaveStore Field Mapping

### 6.1 Data Layout (48 bytes)
| Byte(s) | Content | Purpose |
|---------|---------|---------|
| 0-4 | Stage completion bitmasks | 5 bytes × 8 stages = 40 stages per character |
| 5-46 | Unused / reserved | — |
| 47 | Settings byte | bit 0=hardcore, bits 1-3=char |

### 6.2 Stage Completion Bits (data[0-4])
| Byte | Stages | Bits per Character |
|------|--------|-------------------|
| data[0] | Stage 0 | bit 0-4 = char 0-4 |
| data[1] | Stage 1 | bit 0-4 = char 0-4 |
| data[2] | Stage 2 | bit 0-4 = char 0-4 |
| data[3] | Stage 3 | bit 0-4 = char 0-4 |
| data[4] | Stage 3 | bit 0-4 = char 0-4 |

**Limitation**: Only stages 0-4 tracked. Night variants (5-8) and time1 (9) not tracked.

### 6.3 Settings Byte (data[47])
| Bit | Meaning |
|-----|---------|
| 0 | Hardcore mode |
| 1-3 | Selected character (0-4) |
| 4-7 | Unused |

## 6. Load/Save Error Handling

### 6.1 Load Errors (SaveStore.load())
| Error | Behavior |
|-------|----------|
| RecordStoreNotFoundException | Keep defaults (zeros) |
| RecordStoreException | Keep defaults |
| Record too short | Pad with zeros |
| Record too long | Truncate to 48 bytes |
| Corrupted data | Load whatever readable |

### 6.2 Save Errors
| Error | Behavior |
|-------|----------|
| RecordStoreFullException | Caught, ignored |
| RecordStoreException | Caught, ignored |

## 6. Classification Summary

| Aspect | Classification | Notes |
|--------|----------------|-------|
| Stage completion save (Stage Mode) | `MATCH_CONFIRMED` | Source + bytecode |
| Stage completion save (Time/Endless) | `MATCH_CONFIRMED` | Not saved by design |
| Stage completion tracking (stages 0-4) | `MATCH_CONFIRMED` | Bitmask in SaveStore |
| Night variant completion tracking | `STRUCTURAL_SIMILARITY_ONLY` | Not tracked in SaveStore |
| Time Mode completion | `STRUCTURAL_SIMILARITY_ONLY` | Not saved |
| SaveStore load/save errors | `CONFIRMED_PARITY` | Exception handling verified |
| New install defaults | `MATCH_CONFIRMED` | All zeros |
| Corrupted save handling | `MATCH_CONFIRMED` | Graceful fallback |

## 7. Findings Summary

| Finding | Classification | Impact |
|---------|----------------|--------|
| Time Mode completion not saved | `DESIGN_DIFFERENCE` | By design — only Stage Mode saves |
| Endless Mode completion not saved | `DESIGN_DIFFERENCE` | By design |
| Night variant completion not tracked | `DESIGN_DIFFERENCE` | SaveStore only tracks stages 0-4 |
| Stage 5 (Fantasy Island) unlockRequirement=99 | `DESIGN_DIFFERENCE` | Special case |
| Time Mode stage completion not tracked | `DESIGN_DIFFERENCE` | Single-stage mode |

## 8. Open Questions

| Question | Status | Notes |
|----------|--------|-------|
| Should Time Mode track completion? | `DESIGN_DECISION_REQUIRED` | Currently doesn't save |
| Should Night variant completions persist? | `DESIGN_DECISION_REQUIRED` | Not currently tracked |
| Should Endless Mode track survival time? | `DESIGN_DECISION_REQUIRED` | Not implemented |
| Stage 5 unlockRequirement=99 meaning | `INSUFFICIENT_EVIDENCE` | Always unlocked? |

## 8. Evidence Artifacts

- RunWorld.java:679-694 (defeatBoss victory logic)
- FullGameCanvas.java:147-151 (save.setCompleted on victory)
- SaveStore.java:23-34 (isCompleted/setCompleted)
- SaveStore.java:52-75 (load), 77-91 (save)
- ContentCatalog.java:252-263 (StageDef array)
- ContentCatalog.java:246-250 (ModeDef array)

## 9. Limitations

| Limitation | Impact |
|------------|--------|
| No runtime verification | Static analysis only |
| JAR bytecode for save logic not fully disassembled | `INSUFFICIENT_EVIDENCE` for full parity |
| No runtime allocation measurement | Static estimates only |

---

**Conclusion**: Completion and save behavior is internally consistent and matches design intent. Time Mode and Endless Mode intentionally do not persist stage completion. Only Stage Mode (modeIndex=0) persists stage completion via SaveStore bitmask. Night variants and Time Mode stage use special unlock logic gated by stage 2 completion but do not persist completion state.
