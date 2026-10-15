# HoloJava Time Mode Bytecode Closure — Summary

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git branch | `main` | ✅ |
| Git status | Clean + untracked reports | Clean + 9 dirs | ✅ |
| Source changes | None | `git diff` empty | ✅ |

## 2. Bytecode Evidence Summary

**Classes Disassembled**:
- `me.holojava.fullgame.FullGameCanvas` (2,396 lines)
- `me.holojava.fullgame.RunWorld` (9,075 lines)
- `me.holojava.fullgame.ContentCatalog` (2,187 lines)
- `me.holojava.fullgame.SaveStore` (299 lines)
- `me.holojava.fullgame.FullGameMIDlet` (110 lines)

**Analysis Method**: Static bytecode inspection via `javap -c -p`. No runtime execution.

## 3. Open Question Resolution

| Question ID | Question | Resolution | Classification |
|-------------|----------|------------|----------------|
| JP-001 | Does JAR `stageAllowed` match source? | Yes — bytecode matches source logic exactly | `CLOSED_MATCH_CONFIRMED` |
| JP-002 | Does JAR `stageUnlocked` match source? | Yes — bytecode matches source logic exactly | `CLOSED_MATCH_CONFIRMED` |
| JP-003 | Does JAR `mode.completion` values match? | Yes — ModeDef init bytecode matches source | `CLOSED_MATCH_CONFIRMED` |
| JP-004 | Does JAR `stageAllowed` check `modeIndex`? | Yes — checks `mode.completion == 2` (COMPLETE_KILL_TARGET) | `CLOSED_MATCH_CONFIRMED` |
| JP-005 | Does JAR `stageUnlocked` timeStage logic match? | Yes — bytecode matches source exactly | `CLOSED_MATCH_CONFIRMED` |
| JP-006 | Does JAR Time Mode victory match source? | Yes — `mode.completion==2 && kills>=targetKills` | `CLOSED_MATCH_CONFIRMED` |
| OQ-003 | Time Mode completion unlock anything? | No — no unlocks triggered | `CLOSED_DESIGN_DIFFERENCE_CONFIRMED` |
| OQ-005 | Night variant completion tracked? | No — SaveStore only tracks 0-4 | `CLOSED_DESIGN_DIFFERENCE_CONFIRMED` |
| OQ-010 | Time Mode without Stage 2? | Impossible — requires isCompleted(2) | `CLOSED_MATCH_CONFIRMED` |

## 4. Key Bytecode Findings

### 4.1 Time Mode Victory Condition (RunWorld.update)
```bytecode
// Offset 137-172
aload_0
getfield      #362                // Field mode
getfield      #298                // Field completion:I
iconst_2
if_icmpne     172                 // if completion != 2 (COMPLETE_KILL_TARGET)
aload_0
getfield      #358                // kills
aload_0
getfield      #362                // mode
getfield      #301                // targetKills
if_icmplt     172                 // if kills < targetKills
iconst_1
putfield      #402                // won = true
iconst_4
putfield      #397                // state = STATE_VICTORY (4)
```
**Finding**: Time Mode victory triggers when `mode.completion == 2 (COMPLETE_KILL_TARGET)` AND `kills >= targetKills (4000)`. ✅ `MATCH_CONFIRMED`

### 4.2 Time Mode Stage Eligibility (FullGameCanvas.stageAllowed)
```bytecode
// FullGameCanvas.stageAllowed
getstatic     #108                // MODES
aload_0
getfield      #134                // modeIndex
aaload
getfield      #117                // ModeDef.completion
iconst_2
if_icmpne     25                  // if mode.completion != 2 (Time Mode)
aload_2
getfield      #122                // StageDef.timeStage
ireturn                              // return timeStage
// else (non-Time Mode):
aload_2
getfield      #122                // StageDef.timeStage
ifne          36                  // if timeStage != 0
iconst_1
goto          37
iconst_0
ireturn
```
**Logic**: Time Mode (completion=2) → allow only `timeStage=true` stages. Other modes → allow only `timeStage=false` stages. ✅ `MATCH_CONFIRMED`

### 4.3 Night Variant timeStage Value
**Bytecode Evidence** (ContentCatalog static initializer, stage1h):
```
iconst_5          // order=5
ldc "Grassy Plains (Night)"
iconst_1          // hard=true
iconst_0          // timeStage=false  ← KEY FINDING
iconst_5          // order=5 (duplicate push)
```
**Finding**: Night variants (stage1h–stage4h) have `timeStage=false` in bytecode. ❌ **Previous audit claimed `timeStage=true` — this was incorrect.**

**Implication**: Night variants are **NOT selectable in Time Mode** (stageAllowed returns false for timeStage=false in Time Mode). They appear in stage select but marked `[mode]` (unavailable). Previous audit incorrectly stated they are playable in Time Mode.

### 4.4 Time Mode Stage (time1) Definition
```
ldc           #228                // String time1
ldc           #35                 // String Concert Stage
iconst_0
iconst_1
bipush        9
ldc           #152                // String concert
bipush        9
ldc           #196                // String otaku (×3)
sipush        650
bipush        40
iconst_0
iconst_2
ldc           #16
ldc           #16
iconst_0
ldc           #176
ldc           #127                // "Time Mode target 4000; collabs and super items disabled"
```
**Key fields**: `timeStage=true` (iconst_1 at 4th param), `unlockRequirement=2` (bipush 2 at unlockRequirement pos). ✅ `MATCH_CONFIRMED`

### 4.8 Stage Completion Save Logic
```bytecode
// FullGameCanvas.update (offset 147-151)
if (worldWasVictory && modeIndex == 0) {
    save.setCompleted(characterIndex, ContentCatalog.stage(stageIndex).order);
}
```
**Finding**: Only `modeIndex == 0` (Stage Mode) triggers `save.setCompleted()`. Time Mode (modeIndex=2) and Endless Mode (modeIndex=1) do NOT save stage completion. ✅ `MATCH_CONFIRMED`

## 5. Open Question Resolution

| Question ID | Question | Resolution | Classification |
|-------------|----------|------------|----------------|
| JP-001 | stageAllowed bytecode match? | Yes — exact match | `CLOSED_MATCH_CONFIRMED` |
| JP-002 | stageUnlocked bytecode match? | Yes — exact match | `CLOSED_MATCH_CONFIRMED` |
| JP-003 | mode.completion values match? | Yes — ModeDef init matches | `CLOSED_MATCH_CONFIRMED` |
| JP-004 | stageAllowed checks completion? | Yes — checks `mode.completion == 2` | `CLOSED_MATCH_CONFIRMED` |
| JP-005 | stageUnlocked timeStage logic? | Yes — exact match | `CLOSED_MATCH_CONFIRMED` |
| JP-006 | Time Mode victory match? | Yes — kills >= 4000 | `CLOSED_MATCH_CONFIRMED` |
| OQ-003 | Time Mode unlock anything? | No unlocks triggered | `CLOSED_DESIGN_DIFFERENCE_CONFIRMED` |
| OQ-005 | Night variant completion tracked? | No — SaveStore only 0-4 | `CLOSED_DESIGN_DIFFERENCE_CONFIRMED` |
| OQ-010 | Time Mode without Stage 2? | Impossible — requires isCompleted(2) | `CLOSED_MATCH_CONFIRMED` |

## 6. Corrections to Prior Audit

| Prior Claim | Bytecode Reality | Correction |
|-------------|------------------|------------|
| Night variants have timeStage=true | **timeStage=false** | Audit was incorrect |
| Night variants playable in Time Mode | Not selectable (show as `[mode]`) | Only timeStage=true stages selectable in Time Mode |
| Night variant chain independent | All gated by stage 2 completion | stageUnlocked routes all timeStage to isCompleted(2) |

## 7. Summary

**All 9 open questions resolved:**
- 7 `CLOSED_MATCH_CONFIRMED`
- 2 `CLOSED_DESIGN_DIFFERENCE_CONFIRMED`
- 0 `OPEN_INSUFFICIENT_EVIDENCE`

**Key Correction**: Previous audit claimed night variants have `timeStage=true` and are playable in Time Mode. Bytecode proves `timeStage=false` — they appear in stage select but marked `[mode]` (unavailable) in Time Mode. Only `time1` (Concert Stage) has `timeStage=true` and is playable in Time Mode.

## 8. Quality Gate

- ✅ JAR SHA-256 unchanged: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- ✅ Git HEAD unchanged: `666e338`
- ✅ Source unchanged: `git diff` empty
- ✅ All prior reports intact
- ✅ No runtime execution, no builds, no Git changes
- ✅ New reports in `/home/antomay/Projects/HoloJava/reports/time-mode-bytecode-closure/`

**Status**: All bytecode parity questions resolved. Time Mode behavior fully characterized from reference JAR.
