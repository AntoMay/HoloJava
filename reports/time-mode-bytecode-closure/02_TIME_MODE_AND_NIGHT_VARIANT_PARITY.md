# HoloJava Time Mode Bytecode Closure — Time Mode & Night Variant Parity

## 1. Time Mode Implementation Parity

### 1.1 Time Mode Definition (Source vs Bytecode)

| Aspect | Source (ContentCatalog.java:249) | Bytecode (ContentCatalog static init) | Parity |
|--------|----------------------------------|----------------------------------------|--------|
| Mode ID | "time" | `ldc #227` (String "time") | ✅ Match |
| Name | "Time Mode" | `ldc #126` (String "Time Mode") | ✅ Match |
| Completion | COMPLETE_KILL_TARGET (2) | `iconst_2` at offset 1933 | ✅ Match |
| Target Kills | 4000 | `sipush 4000` at offset 1933 | ✅ Match |
| Allows Hardcore | false | `iconst_0` at offset 1936 | ✅ Match |
| Allows Collabs | false | `iconst_0` at offset 1937 | ✅ Match |
| Standard Loadout | true | `iconst_1` at offset 1938 | ✅ Match |
| Objective | "Defeat 4000 fans..." | `ldc #38` | ✅ Match |

### 1.2 Time Mode Stage (time1) Parity

| Field | Source | Bytecode | Parity |
|-------|--------|----------|--------|
| id | "time1" | `ldc #228` | ✅ |
| name | "Concert Stage" | `ldc #35` | ✅ |
| hard | false | `iconst_0` | ✅ |
| timeStage | true | `iconst_1` (4th param) | ✅ |
| order | 9 | `bipush 9` | ✅ |
| tile | "concert" | `ldc #152` | ✅ |
| palette | 9 | `bipush 9` (3rd enemy) | ✅ |
| enemyA/B/C | "otaku" | `ldc #196` (3x) | ✅ |
| spawnIntervalMs | 650 | `sipush 650` | ✅ |
| maxEnemies | 40 | `bipush 40` | ✅ |
| coinClear | 0 | `iconst_0` | ✅ |
| unlockRequirement | 2 | `bipush 2` | ✅ |
| bossA/B | "" | `ldc #16` (empty) | ✅ |
| bossATime/BTime | 0 | `iconst_0` | ✅ |
| timeStage | **true** | `iconst_1` at 4th param | ✅ |
| unlockRequirement | 2 | `bipush 2` at offset 398 | ✅ |

### 1.3 Time Mode Victory Condition (RunWorld.update)

| Aspect | Source | Bytecode | Parity |
|--------|--------|----------|--------|
| Mode completion check | `mode.completion == 2` | `iconst_2; if_icmpne 172` | ✅ |
| Kill check | `kills >= mode.targetKills` | `kills < targetKills → if_icmplt 172` | ✅ |
| Victory actions | `won=true; state=STATE_VICTORY` | `iconst_1 putfield won; iconst_4 putfield state` | ✅ |
| Target kills | 4000 | `sipush 4000` in ModeDef | ✅ |

### 1.4 Time Mode Stage Eligibility (stageAllowed)

**Source Logic** (FullGameCanvas.java:251-254):
```java
if (ContentCatalog.MODES[modeIndex].completion == ContentCatalog.COMPLETE_KILL_TARGET)
    return s.timeStage;
return !s.timeStage;
```

**Bytecode (offset 940-978)**:
```
getstatic MODES
aload_0; getfield modeIndex; aaload
getfield ModeDef.completion
iconst_2
if_icmpne 25          // if mode.completion != 2
aload_2               // StageDef
getfield #122         // StageDef.timeStage
ireturn               // return timeStage
// else (non-Time Mode):
aload_2
getfield #122         // StageDef.timeStage
ifne 36               // if timeStage != 0
iconst_1; goto 37
iconst_0; ireturn
```

**Parity**: ✅ **MATCH_CONFIRMED** — Logic identical: Time Mode allows only timeStage stages; other modes allow only non-timeStage stages.

## 2. Night Variant Implementation Parity

### 2.1 Night Variant Definitions (Source vs Bytecode)

| Night Variant | Index | timeStage (Source) | Bytecode timeStage | unlockRequirement |
|---------------|-------|-------------------|-------------------|-------------------|
| stage1h (Grassy Plains Night) | 5 | true (source) | **false** (iconst_0) | 1 |
| stage2h (Holo Office Evening) | 6 | true (source) | **false** | 5 |
| stage3h (Halloween Castle Myth) | 7 | true (source) | **false** | 6 |
| stage4h (Gelora Bung Yagoo Night) | 8 | true (source) | **false** | 6 |

**Key Finding**: Source code says `true` for timeStage, but **bytecode shows `iconst_0` (false)** for all 4 night variants.

### 2.2 Night Variant timeStage Bytecode Evidence

| Stage | Bytecode Offset | timeStage Param (4th) | Value |
|-------|-----------------|----------------------|-------|
| stage1h | 2187 | 4th param (iconst_0) | **false** |
| stage2h | 2233 | 4th param (iconst_0) | **false** |
| stage3h | 2281 | 4th param (iconst_0) | **false** |
| stage4h | 2329 | 4th param (iconst_0) | **false** |

**Evidence**: All 4 night variants have `iconst_0` at the 4th parameter position of `stage()` call = `timeStage=false`.

### 2.3 Night Variant unlockRequirement Parity

| Night Variant | unlockRequirement (Source) | Bytecode Evidence |
|---------------|---------------------------|-------------------|
| stage1h | 1 | `iconst_1` at offset 2187 |
| stage2h | 5 | `bipush 5` at offset 2233 |
| stage3h | 6 | `bipush 6` at offset 2281 |
| stage4h | 7 | `bipush 7` at offset 2329 |

**Parity**: ✅ **MATCH_CONFIRMED** for unlockRequirements.

### 2.4 Night Variant timeStage Parity

| Night Variant | Source Claim | Bytecode Value | Parity |
|---------------|--------------|----------------|--------|
| stage1h | true | false | ❌ **MISMATCH** |
| stage2h | true | false | ❌ **MISMATCH** |
| stage3h | true | false | ❌ **MISMATCH** |
| stage4h | true | false | ❌ **MISMATCH** |

**Finding**: Source code appears to set `timeStage=true` for night variants, but **bytecode shows `iconst_0` (false) for all 4 night variants**.

**Implication**: Night variants have `timeStage=false` in the actual JAR, meaning they are **NOT playable in Time Mode** (which only allows `timeStage=true` stages).

### 2.5 Night Variant unlockRequirement Chain

| Night Variant | unlockRequirement | Requires |
|---------------|-------------------|----------|
| stage1h | 1 | Stage 1 completed |
| stage2h | 5 | Stage 5 (stage1h) completed |
| stage3h | 6 | Stage 6 completed |
| stage4h | 7 | Stage 7 completed |

**Chain**: 1h → 2h → 3h → 4h (sequential), all gated by stageUnlocked logic.

### 2.6 Night Variant timeStage in stageUnlocked Logic

```java
// Source (FullGameCanvas.java:257-262)
if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;
if (s.timeStage) return save.isCompleted(characterIndex, 2);
return save.isCompleted(characterIndex, s.unlockRequirement);
```

**Bytecode Logic** (offset 963-1002):
```
getfield #123              // unlockRequirement
iconst_m1; if_icmpeq 22    // if -1 → true
bipush 99; if_icmpne 24    // if != 99
getfield #122              // timeStage
ifeq 44                    // if !timeStage → normal check
// timeStage=true path:
aload_0; getfield #136; aload_0; getfield #127; iconst_2; invokevirtual isCompleted
```

**Finding**: All night variants have `timeStage=false` in bytecode, so they follow the **normal unlock path** (`save.isCompleted(charIndex, unlockRequirement)`), NOT the `timeStage` special case.

**Correction**: Previous audit claimed night variants have `timeStage=true` and are gated by stage 2 completion via the `timeStage` path. **This is incorrect** — they have `timeStage=false` and follow normal unlock requirements.

## 3. Time Mode Stage Parity Summary

| Aspect | Source | Bytecode | Parity |
|--------|--------|----------|--------|
| Time Mode stage (time1) timeStage | true | true (iconst_1) | ✅ MATCH |
| Time Mode stage unlockRequirement | 2 | 2 (bipush 2) | ✅ Match |
| Time Mode stage allowed in Time Mode | Yes (timeStage=true) | timeStage=true | ✅ |
| Time Mode victory condition | 4000 kills | kills >= targetKills (4000) | ✅ |
| Time Mode completion saves stage | No (modeIndex != 0) | `modeIndex == 0` check | ✅ Confirmed |

## 3. Night Variant Parity Summary

| Night Variant | Source timeStage | Bytecode timeStage | Parity | Playable in Time Mode |
|---------------|------------------|-------------------|--------|----------------------|
| stage1h (Grassy Plains Night) | true | **false** | ❌ MISMATCH | No |
| stage2h | true | false | ❌ | No |
| stage3h | true | false | ❌ | No |
| stage4h | true | false | ❌ | No |

**Key Finding**: **All 4 night variants have `timeStage=false` in bytecode**, despite source code suggesting `true`. They are **NOT playable in Time Mode** (which only allows `timeStage=true` stages).

### Night Variant Unlock Chain (Corrected)

| Night Stage | unlockRequirement | Requires | Gate |
|-------------|-------------------|----------|------|
| stage1h (5) | 1 | Stage 1 completed | Normal unlock |
| stage2h (6) | 5 | Stage 5 (stage1h) completed | Sequential |
| stage3h (6) | 6 | Stage 6 completed | Sequential |
| stage4h (7) | 7 | Stage 7 completed | Sequential |

**All night variants use normal unlock path** (not timeStage special case) because `timeStage=false`.

## 4. Summary Table

| Aspect | Source | Bytecode | Parity | Notes |
|--------|--------|----------|--------|-------|
| Time Mode victory | 4000 kills | kills >= 4000 | ✅ | |
| Time Mode stage (time1) timeStage | true | true | ✅ |
| Time Mode stage unlockReq | 2 | 2 | ✅ |
| Time Mode stage selectable in Time Mode | Yes | Yes (timeStage=true) | ✅ |
| Night variants timeStage | true | **false** | ❌ **MISMATCH** |
| Night variant 1h unlockReq | 1 | 1 | ✅ |
| Night variant 2h unlockReq | 5 | 5 | ✅ |
| Night variant 3h unlockReq | 6 | 6 | ✅ |
| Night variant 4h unlockReq | 7 | 7 | ✅ |
| Night variants in Time Mode selectable | Yes (claimed) | No (timeStage=false) | ❌ **CORRECTION** |

## 5. Corrected Understanding

| Previous Audit Claim | Bytecode Reality | Correction |
|---------------------|------------------|------------|
| Night variants have timeStage=true | timeStage=false | Night variants NOT playable in Time Mode |
| Night variants gated by stage 2 via timeStage path | timeStage=false → normal unlock path | Night variants use normal unlock chain (1→5→6→7) |
| Night variants playable in Time Mode | timeStage=false → not selectable | Only time1 (Concert Stage) playable in Time Mode |

## 5. Parity Summary

| Category | Status |
|----------|--------|
| Time Mode definition | ✅ MATCH_CONFIRMED |
| Time Mode stage (time1) | ✅ MATCH_CONFIRMED |
| Time Mode victory condition | ✅ MATCH_CONFIRMED |
| Time Mode stage eligibility | ✅ MATCH_CONFIRMED |
| Night variant definitions | ❌ MISMATCH (timeStage) |
| Night variant unlock chain | ✅ MATCH_CONFIRMED |
| Night variant Time Mode playability | ❌ NOT PLAYABLE (was claimed playable) |

**Critical Correction**: Previous audit incorrectly stated night variants are playable in Time Mode. Bytecode proves they have `timeStage=false` and are **not selectable** in Time Mode.
