# HoloJava Time Mode Critical Review — Night Variant Field Mapping

## 1. Baseline Verification

| Item | Value |
|------|-------|
| Project Root | `/home/antomay/Projects/HoloJava` |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` |
| Git HEAD | `666e338` |
| Source File | `src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java` |
| Reference Class | `me.holojava.fullgame.ContentCatalog` |

---

## 2. StageDef Field Mapping

### 2.1 StageDef Source Definition (ContentCatalog.java:136-157)

```java
static final class StageDef {
    String id;
    String name;
    boolean hard;
    boolean timeStage;      // ← 4th constructor parameter
    int order;
    String tile;
    int palette;
    String enemyA;
    String enemyB;
    String enemyC;
    int spawnIntervalMs;
    int maxEnemies;
    int coinClear;
    int unlockRequirement;  // -1 default, 99 = never
    String bossA;
    int bossATimeSec;
    String bossB;
    int bossBTimeSec;
    String documentedSource;
    String coverage;
}
```

### 2.2 StageDef Constructor Parameter Order

```java
static StageDef stage(String id, String name, boolean hard, boolean time, int order,
        String tile, int palette, String enemyA, String enemyB, String enemyC,
        int spawnIntervalMs, int maxEnemies, int coins, int unlockRequirement,
        String bossA, int bossATimeSec, String bossB, int bossBTimeSec,
        String source, String coverage)
```

**Parameter Order**:
1. `id` (String)
2. `name` (String)
3. `hard` (boolean) — 3rd param
3. `timeStage` (boolean) — **4th parameter** ← CRITICAL
4. `order` (int)
5. `tile` (String)
6. `palette` (int)
6. `enemyA` (String)
6. `enemyB` (String)
6. `enemyC` (String)
7. `spawnIntervalMs` (int)
7. `maxEnemies` (int)
7. `coinClear` (int)
7. `unlockRequirement` (int)
7. `bossA` (String)
7. `bossATimeSec` (int)
7. `bossB` (String)
7. `bossBTimeSec` (int)
7. `source` (String)
7. `coverage` (String)

---

## 2. Source Code Stage Definitions

| Stage Call | hard (3rd) | timeStage (4th) | order | unlockReq |
|------------|------------|-----------------|-------|-----------|
| `stage("stage1", "Grassy Plains", false, false, 0, ...)` | false | **false** | 0 | -1 |
| `stage("stage2", "Holo Office", false, false, 1, ...)` | false | **false** | 1 | 0 |
| `stage("stage3", "Halloween Castle", false, false, 2, ...)` | false | **false** | 2 | 1 |
| `stage("stage4", "Gelora Bung Yagoo", false, false, 3, ...)` | false | **false** | 3 | 2 |
| `stage("stage5", "Fantasy Island", false, false, 4, ...)` | false | **false** | 4 | 99 |
| `stage("stage1h", "Grassy Plains (Night)", true, **false**, 5, ...)` | true | **false** | 5 | 1 |
| `stage("stage2h", "Holo Office (Evening)", true, **false**, 6, ...) | true | **false** | 6 | 5 |
| `stage("stage3h", "Halloween Castle (Myth)", true, **false**, 7, ...) | true | **false** | 6 | 6 |
| `stage("stage4h", "Gelora Bung Yagoo (Night)", true, **false**, 8, ...) | true | **false** | 7 | 7 |
| `stage("time1", "Concert Stage", false, **true**, 9, ...)` | false | **true** | 9 | 2 |

---

## 3. Bytecode Evidence

### 2.1 ContentCatalog StageDef Bytecode Field Mapping

| Field | Bytecode Field # | putfield Offset |
|-------|------------------|-----------------|
| `timeStage` | `#351` | offset 30 in `stage()` method |
| `unlockRequirement` | `#352` | putfield #352 at offset 100 |

### 2.2 StageDef.stage() Bytecode Parameter Mapping

The `stage()` method signature (from bytecode):
```
stage:(Ljava/lang/String;Ljava/lang/String;ZZILjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)Lme/holojava/fullgame/ContentCatalog$StageDef;
```

Parameter order on stack (17 params):
1. id (String)
2. name (String)
3. hard (Z) ← 3rd
4. timeStage (Z) ← **4th** ← **CRITICAL**
5. order (I)
6. tile (String)
6. palette (I)
6. enemyA (String)
6. enemyB (String)
6. enemyC (String)
7. spawnIntervalMs (I)
7. maxEnemies (I)
7. coinClear (I)
7. unlockRequirement (I) ← putfield #352
7. bossA (String)
7. bossATimeSec (I)
7. bossB (String)
7. bossBTimeSec (I)
7. source (String)
7. coverage (String)

---

## 3. Bytecode Evidence for Each Stage

### 3.1 Night Variant Definitions (Bytecode Offsets)

| Stage | ldc Name | hard (3rd) | timeStage (4th) | order | unlockReq |
|-------|----------|------------|-----------------|-------|-----------|
| stage1h | `ldc #214 "stage1h"` | `iconst_1` (true) | **iconst_0** (false) | 5 (iconst_5) | 1 (iconst_1) |
| stage2h | `ldc #216 "stage2h"` | `iconst_1` | **iconst_0** | 6 (bipush 6) | 5 (bipush 5) |
| stage3h | `ldc #218 "stage3h"` | `iconst_1` | **iconst_0** | 7 (bipush 7) | 6 (bipush 6) |
| stage4h | `ldc #220 "stage4h"` | `iconst_1` | **iconst_0** | 8 (bipush 8) | 7 (bipush 7) |
| time1 | `ldc #228 "time1"` | `iconst_0` | **iconst_1** | 9 (bipush 9) | 2 (bipush 2) |

### 3.2 Bytecode Evidence for timeStage (4th parameter)

| Stage | Bytecode Sequence (around 4th param) | timeStage Value |
|-------|--------------------------------------|-----------------|
| stage1h | `iconst_1` (hard), **`iconst_0`**, `iconst_5` | **false** |
| stage2h | `iconst_1`, **`iconst_0`**, `bipush 6` | **false** |
| stage3h | `iconst_1`, **`iconst_0`**, `bipush 7` | **false** |
| stage4h | `iconst_1`, **`iconst_0`**, `bipush 8` | **false** |
| time1 | `iconst_0`, **`iconst_1`**, `bipush 9` | **true** |

**Key Finding**: All 4 night variants have `iconst_0` (false) at the 4th parameter position = `timeStage=false`.

---

## 4. Time Mode Eligibility Logic

### 3.1 stageAllowed() Bytecode (FullGameCanvas.class offset 940-978)

```bytecode
// if (mode.completion == COMPLETE_KILL_TARGET) return s.timeStage;
// else return !s.timeStage;
getstatic MODES
aload_0; getfield modeIndex; aaload
getfield ModeDef.completion
iconst_2                // COMPLETE_KILL_TARGET = 2
if_icmpne 25            // if mode.completion != 2
aload_2; getfield #122; ireturn  // return timeStage
// else:
aload_2; getfield #122; ifne 36; iconst_1; goto 37; iconst_0; ireturn
```

**Logic**:
- Time Mode (completion=2): returns `timeStage` (only timeStage=true allowed)
- Other modes: returns `!timeStage` (only non-timeStage allowed)

### 3.2 Night Variant Eligibility in Time Mode

| Stage | timeStage | Time Mode Eligible? |
|-------|-----------|---------------------|
| stage1h-4h (night) | **false** | ❌ Not selectable |
| time1 (Concert Stage) | true | ✅ Selectable |

**Finding**: Night variants have `timeStage=false` in both source and bytecode. They are **NOT playable in Time Mode** (which only allows `timeStage=true` stages).

---

## 4. Previous Audit Error Analysis

### 4.1 Previous Audit Claim vs. Reality

| Previous Audit Claim | Actual Source Code | Bytecode | Discrepancy |
|----------------------|-------------------|----------|-------------|
| "Source shows night variants have timeStage=true" | **FALSE** — source shows `false` | `iconst_0` (false) | Previous audit misread source |
| "Bytecode shows timeStage=false" | Correct | `iconst_0` | **NO DISCREPANCY** |
| "Night variants playable in Time Mode" | No — timeStage=false | timeStage=false → not selectable | Previous audit was wrong |

### 4.2 Root Cause of Previous Audit Error

The previous audit likely misread the `stage()` method signature, confusing the 3rd parameter (`hard`) with the 4th parameter (`timeStage`). 

For night variants:
- 3rd param (hard) = `true` (`iconst_1`)
- 4th param (timeStage) = `false` (`iconst_0`)

The audit likely confused the 3rd `iconst_1` (hard=true) with the 4th parameter (timeStage).

---

## 5. Final Classification

| Discrepancy | Classification | Evidence |
|-------------|----------------|----------|
| Night-variant `timeStage` source vs bytecode | **NO_DISCREPANCY** | Both source and bytecode show `timeStage=false` for all 4 night variants |
| Night variant playability in Time Mode | `NO_DISCREPANCY` | Both source and bytecode show `timeStage=false` → not selectable in Time Mode |

**Final Classification**: `NO_DISCREPANCY` — No discrepancy exists between source and bytecode for night-variant `timeStage` values.
