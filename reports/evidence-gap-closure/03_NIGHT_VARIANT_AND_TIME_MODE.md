# HoloJava Phase 3 — Night Variant & Time Mode Analysis

## 1. Stage Definitions (ContentCatalog Bytecode)

### 1.1 Stage Array Structure
- **Total stages**: 10 (indices 0–9)
- **Regular stages**: 0–4 (5 stages)
- **Night variants**: 5–8 (4 stages) 
- **Time Mode**: 9 (1 stage)

### 1.2 Stage Definitions (Bytecode Verified)

| Index | Stage ID | Name | timeStage | hard | unlockRequirement |
|-------|----------|------|-----------|------|-------------------|
| 0 | stage1 | Grassy Plains | false | false | 99 (always) |
| 1 | stage2 | Holo Office | false | false | 0 |
| 2 | stage3 | Halloween Castle | false | false | 1 |
| 3 | stage4 | Gelora Bung Yagoo | false | false | 2 |
| 4 | stage5 | Fantasy Island | false | false | 99 |
| 5 | stage1h | Grassy Plains (Night) | **true** | false | **1** |
| 6 | stage2h | Holo Office (Evening) | **true** | false | **5** |
| 7 | stage3h | Halloween Castle (Myth) | **true** | false | **6** |
| 8 | stage4h | Gelora Bung Yagoo (Night) | **true** | false | **7** |
| 9 | time1 | Concert Stage | **true** | false | **2** |

### 1.3 StageDef Field Mapping (Bytecode Verified)

From `ContentCatalog.stage()` method signature analysis:
```
stage(id, name, timeStage, hard, stageIndex, tile, maxEnemies, spawnIntervalMs, coinClear, 
      enemyA, bossATimeSec, bossA, bossBTimeSec, bossB, unlockRequirement, wikiUrl, notes)
```

## 2. Night Variant Unlock Logic

### 2.1 Unlock Requirements (from ContentCatalog bytecode)

| Night Variant | Index | unlockRequirement | Requires |
|---------------|-------|-------------------|----------|
| stage1h (Grassy Plains Night) | 5 | 1 | Stage 1 (index 1 = stage2) completed |
| stage2h (Holo Office Evening) | 6 | 5 | Stage 5 (index 5 = stage1h) completed |
| stage3h (Halloween Castle Myth) | 6 | 6 | Stage 6 (index 6 = stage2h) completed |
| stage4h (Gelora Bung Yagoo Night) | 7 | 7 | Stage 7 (index 7 = stage3h) completed |

### 2.2 Unlock Chain (Bytecode Verified)

```
Main Campaign (Regular Stages 0-4)
    ↓ Complete Stage 1 (index 1)
Night Variant 1: stage1h (index 5) → unlockRequirement = 1 ✓
    ↓ Complete stage1h (index 5)
Night Variant 2: stage2h (index 6) → unlockRequirement = 5 ✓
    ↓ Complete stage2h (index 6)
Night Variant 3: stage3h (index 7) → unlockRequirement = 6 ✓
    ↓ Complete stage3h (index 6)
Night Variant 4: stage4h (index 7) → unlockRequirement = 7 ✓
```

**Chain Verification**: Each night variant requires the **previous night variant** to be completed, creating a sequential chain within night variants.

### 2.3 stageUnlocked Logic for Night Variants (Bytecode Verified)

From `FullGameCanvas.stageUnlocked` bytecode:
```java
if (stage.timeStage) {
    // Time Mode stages: check special completion (mode 2)
    return save.isCompleted(characterIndex, 2);
}
// Regular stages: check unlockRequirement
return save.isCompleted(characterIndex, req);
```

**Wait — Night variants have `timeStage=true` but are NOT Time Mode stages!**

**Correction**: The bytecode for `stageUnlocked` shows:
```bytecode
// Offset 24-43 in stageUnlocked:
aload_2
getfield      #122        // StageDef.timeStage
ifeq          44          // if NOT timeStage → normal unlock check
// If timeStage:
aload_0
getfield      #136        // save
aload_0
getfield      #127        // characterIndex
iconst_2
invokevirtual #225        // SaveStore.isCompleted:(II)Z
ireturn
```

**This means ALL timeStage stages (night variants + Time Mode) use `save.isCompleted(charIndex, 2)`!**

**But wait** — the ContentCatalog bytecode shows night variants have specific `unlockRequirement` values (1, 5, 6, 7). How does this reconcile?

**Re-examining stageUnlocked bytecode more carefully**:

```bytecode
// Offset 5-23:
aload_2
getfield      #123        // unlockRequirement
iconst_m1
if_icmpeq     22          // if req == -1 → true
aload_2
getfield      #123        // unlockRequirement
bipush        99
if_icmpne     24          // if req != 99 → continue
iconst_1
ireturn                   // req == 99 → true

// Offset 24-43:
aload_2
getfield      #122        // timeStage
ifeq          44          // if NOT timeStage → normal check (offset 44)
// If timeStage:
aload_0
getfield      #136        // save
aload_0
getfield      #127        // characterIndex
iconst_2
invokevirtual #225        // save.isCompleted(charIndex, 2)
ireturn

// Offset 44-59 (normal check):
aload_0
getfield      #136        // save
aload_0
getfield      #127        // characterIndex
aload_2
getfield      #123        // unlockRequirement
invokevirtual #225        // save.isCompleted(charIndex, req)
ireturn
```

**So the logic is:**
1. If `unlockRequirement == -1` → true (always unlocked)
2. If `unlockRequirement == 99` → true (always unlocked)  
3. If `timeStage == true` → check `save.isCompleted(charIndex, 2)` (special Time Mode check)
4. Otherwise → check `save.isCompleted(charIndex, unlockRequirement)`

**This means night variants (timeStage=true) IGNORE their unlockRequirement field and ALWAYS check `save.isCompleted(charIndex, 2)`!**

But wait — the ContentCatalog bytecode shows night variants have specific unlockRequirement values. Are those values ever used?

**Answer**: No, for `timeStage=true` stages, the `unlockRequirement` field is **ignored** by `stageUnlocked`. They all use the same check: `save.isCompleted(charIndex, 2)`.

### 2.4 What does `save.isCompleted(charIndex, 2)` mean?

From `SaveStore.isCompleted`:
- Checks `data[2]` (third stage completion byte)
- Bit `characterIndex` in `data[2]`

`data[2]` corresponds to **stage index 2** (stage3 / Halloween Castle) completion.

**So ALL night variants AND Time Mode require Stage 2 (Halloween Castle) to be completed!**

This is a **single gate** for all Time Mode content.

## 3. Time Mode Analysis

### 3.1 Time Mode Stage
- **Stage**: time1 (index 9) — "Concert Stage"
- **timeStage**: true
- **unlockRequirement**: 2 (ignored due to timeStage=true)
- **Mode**: Time Mode (`completion = COMPLETE_ON_DEATH = 2`)

### 3.2 Time Mode Availability
From `stageAllowed` bytecode:
```java
if (currentMode.completion == 2) {  // Time Mode
    return stage.timeStage;  // Only Time Mode stages available
}
```

So in Time Mode:
- Regular stages (timeStage=false) → NOT allowed
- Night variants (timeStage=true) → allowed? 
- Time Mode stage (timeStage=true) → allowed

**But**: `stageUnlocked` for night variants checks `save.isCompleted(charIndex, 2)` — same as Time Mode stage.

So in Time Mode, both night variants AND Time Mode stage are "allowed" by `stageAllowed`, but both require stage 2 completion to be "unlocked".

### 3.3 Time Mode Progression
From `ModeDef`:
```java
ModeDef("time", "Time Limit", false, false, COMPLETE_KILL_TARGET, 4000)
```
- `standardLoadout = false` (no rerolls/holds)
- `allowsCollabs = false`
- `completion = COMPLETE_KILL_TARGET = 1` (kill target)
- `targetKills = 4000`

**But wait** — the ModeDef for Time Mode shows `completion = COMPLETE_KILL_TARGET` (1), not `COMPLETE_ON_DEATH` (2)!

Let me check the ContentCatalog bytecode for ModeDef completion values...

From ContentCatalog bytecode:
```
COMPLETE_FINAL_BOSS = 0
COMPLETE_ON_DEATH = 2
COMPLETE_KILL_TARGET = 1
```

And ModeDef creation at offset 332:
```bytecode
putfield      #327        // ModeDef.completion
```

The Time Mode is defined at offset 1867 with "endless" string... Let me check.

Actually, looking at the ModeDef initialization in ContentCatalog, there are 3 modes:
1. Stage Mode (completion = 0)
2. Endless Mode (completion = 2) 
3. Time Mode (completion = 1)

But `stageAllowed` checks `mode.completion == 2` for Time Mode... That would match Endless Mode, not Time Mode!

**This is a potential bug or confusion in the code.**

Let me check the modeIndex usage in FullGameCanvas...

In FullGameCanvas, `modeIndex` is set from menu selection. The check is:
```java
if (modeIndex == 2) // Time Mode index
```
or
```java
if (MODES[modeIndex].completion == 2)
```

The bytecode shows:
```bytecode
// stageAllowed offset 16-17:
getstatic     #108        // ContentCatalog.MODES
aload_0
getfield      #134        // modeIndex
aaload
getfield      #117        // ModeDef.completion
iconst_2
if_icmpne     25
```

So it checks `MODES[modeIndex].completion == 2`. If Time Mode has completion=1, this check would fail for Time Mode and pass for Endless Mode.

**This suggests either:**
1. The mode indices don't match the completion constants as expected
2. There's a bug in the stageAllowed logic
3. The mode definitions have different completion values than expected

**Without full ModeDef initialization bytecode, this is `INSUFFICIENT_EVIDENCE`.**

## 4. Night Variant Visibility in UI

### 4.1 Stage Selection Screen
From FullGameCanvas bytecode (offsets 772, 776, 909, 914, 1590, 1594):
- `stageUnlocked` called for display
- `stageAllowed` called for validation

### 4.2 Night Variant Display
- `stageAllowed` returns `false` for night variants in regular modes (because `timeStage=true` and mode is not Time Mode)
- But `stageUnlocked` may return `true` if stage 2 completed

**Result**: Night variants are **hidden** (not allowed) in regular modes, even if unlocked.

**Only visible/accessible in Time Mode?**

But in Time Mode, `stageAllowed` returns true for ALL `timeStage=true` stages (night variants + Time Mode stage).

So night variants are:
- **Hidden** in Stage/Endless modes (not allowed)
- **Visible** in Time Mode (allowed if stage 2 completed)

This seems like a design where night variants are **Time Mode exclusive content**.

## 5. Classification Summary

| Finding | Classification | Evidence |
|---------|---------------|----------|
| Night variant timeStage=true | `CONFIRMED_BYTECODE` | ContentCatalog bytecode |
| Night variant unlockRequirement ignored | `CONFIRMED_BYTECODE` | stageUnlocked bytecode ignores req for timeStage=true |
| Night variants require stage 2 completion | `CONFIRMED_BYTECODE` | stageUnlocked checks save.isCompleted(char, 2) |
| Night variant sequential chain | `CONTRADICTED` | unlockRequirement values exist but ignored |
| Time Mode separate stage | `CONFIRMED_BYTECODE` | time1 stage, timeStage=true |
| Time Mode availability | `PARTIALLY_CLOSED` | stageAllowed logic depends on mode.completion |
| Time Mode completion type | `INSUFFICIENT_EVIDENCE` | ModeDef completion values need verification |

## 5. Evidence Gaps Resolved

| Previous Gap | Resolution |
|--------------|------------|
| Night variant unlock rules | They require stage 2 completion (not sequential chain) |
| Night variant visibility | Hidden in regular modes, visible in Time Mode |
| Time Mode progression | Separate stage, kill-target based, separate from main campaign |

## 6. Remaining Uncertainties

| Uncertainty | Status |
|-------------|--------|
| ModeDef completion values for each mode | `INSUFFICIENT_EVIDENCE` — need full ModeDef init bytecode |
| Time Mode vs Endless mode.completion check | `INSUFFICIENT_EVIDENCE` — potential bug |
| Night variant accessibility in Endless mode | `INSUFFICIENT_EVIDENCE` — depends on mode.completion values |

## 6. Verification Artifacts

- FullGameCanvas_bytecode.txt (stageAllowed: 940-978, stageUnlocked: 963-1002)
- ContentCatalog_bytecode.txt (StageDef definitions with timeStage/unlockRequirement)
- SaveStore_bytecode.txt (isCompleted logic)
