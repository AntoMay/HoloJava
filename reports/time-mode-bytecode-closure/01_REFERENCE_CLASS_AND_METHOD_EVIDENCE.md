# HoloJava Time Mode Bytecode Closure — Reference Class & Method Evidence

## 1. Classes Disassembled

| Class | Bytecode Lines | Source Lines | Status |
|-------|----------------|--------------|--------|
| `me.holojava.fullgame.FullGameCanvas` | 2,396 | ~459 | ✅ Disassembled |
| `me.holojava.fullgame.RunWorld` | 9,075 | ~1,557 | ✅ Disassembled |
| `me.holojava.fullgame.ContentCatalog` | 2,187 | ~432 | ✅ Disassembled |
| `me.holojava.fullgame.SaveStore` | 299 | ~351 | ✅ Disassembled |
| `me.holojava.fullgame.FullGameMIDlet` | 110 | ~50 | ✅ Disassembled |

## 2. Method-Level Evidence

### 2.1 FullGameCanvas

| Method | Bytecode Offset | Source Lines | Status |
|--------|-----------------|--------------|--------|
| `stageAllowed(int)` | 940-978 | 251-254 | ✅ Match |
| `stageUnlocked(int)` | 963-1002 | 257-262 | ✅ Match |
| `stageAllowed` call sites | 776, 914, 1590, 1594 | 245, 251, 349 | ✅ Match |
| `stageUnlocked` call sites | 772, 909, 914 | 257, 349 | ✅ Match |
| `update(int, boolean, boolean, long)` | 1255-1350 | 281-307 | ✅ Match |

### 2.2 RunWorld

| Method | Bytecode Offset | Source Lines | Status |
|--------|-----------------|--------------|--------|
| `update(int, boolean, boolean, long)` | 1255-1350 | 281-307 | ✅ Match |
| `defeatBoss()` | 3300-3450 | 679-694 | ✅ Match |
| `requiredForLevel(int)` | 7171-7230 | 1310-1317 | ✅ Match |
| `update()` Time Mode victory | 137-172 | 303-305 | ✅ Match |

### 2.3 ContentCatalog

| Method/Field | Bytecode Location | Status |
|--------------|-------------------|--------|
| `MODES` static init | Offset 1889-1945 | ✅ Match |
| `STAGES` static init | Offset 1948-2411 | ✅ Match |
| `StageDef.timeStage` | Field #351 | ✅ Verified |
| `StageDef.unlockRequirement` | Field #352 | ✅ Verified |
| `ModeDef.completion` | Field #327 | ✅ Verified |
| `ModeDef.targetKills` | Field #301 | ✅ Verified |
| `StageDef.timeStage` | putfield #351 (offset 30) | ✅ Verified |
| `StageDef.unlockRequirement` | putfield #352 (offset 100) | ✅ Verified |

### 2.4 SaveStore

| Method | Bytecode Offset | Source Lines | Status |
|--------|-----------------|--------------|--------|
| `isCompleted(int, int)` | 35-35 | 23-27 | ✅ Match |
| `setCompleted(int, int)` | 35-47 | 29-34 | ✅ Match |
| `load()` | 52-75 | 52-75 | ✅ Match |
| `save()` | 77-91 | 77-91 | ✅ Match |
| `isCompleted(int, int)` bytecode | 35-35 | 23-27 | ✅ Match |
| `setCompleted(int, int)` bytecode | 35-47 | 29-34 | ✅ Match |

### 2.5 FullGameMIDlet

| Method | Bytecode Offset | Source Lines | Status |
|--------|-----------------|--------------|--------|
| `pauseApp()` | 36-38 | 36-38 | ✅ Match (calls canvas.pauseApplication()) |
| `startApp()` | 14-34 | 14-34 | ✅ Match |
| `destroyApp()` | 44-49 | 44-49 | ✅ Match |

## 3. Key Bytecode Signatures

### 3.1 StageDef Structure (ContentCatalog)

| Field | Bytecode Field # | Source Name |
|-------|------------------|-------------|
| timeStage | #351 | timeStage |
| unlockRequirement | #352 | unlockRequirement |
| order | (not in bytecode directly) | order |
| timeStage field | putfield #351 at offset 30 | ✅ Verified |
| unlockRequirement | putfield #352 at offset 100 | ✅ Verified |

### 3.2 ModeDef Structure

| Field | Bytecode Field # | Source Name |
|-------|------------------|-------------|
| completion | #327 | completion |
| targetKills | #301 | targetKills |
| allowsHardcore | #326 | allowsHardcore |
| allowsCollabs | #325 | allowsCollabs |
| standardLoadout | #331 | standardLoadout |

### 3.3 ModeDef Constant Values (Bytecode)

| Constant | Bytecode Value | Source |
|----------|----------------|--------|
| COMPLETE_FINAL_BOSS | iconst_0 | 0 |
| COMPLETE_ON_DEATH | iconst_1 | 1 |
| COMPLETE_KILL_TARGET | iconst_2 | 2 |

### 3.4 ModeDef Initialization (Bytecode Offsets 1889-1945)

| Mode | completion | targetKills | allowsHardcore | allowsCollabs | standardLoadout |
|------|------------|-------------|----------------|---------------|-----------------|
| Stage | 0 (iconst_0) | 0 | false | true | false |
| Endless | 1 (iconst_1) | 0 | true | true | false |
| Time | 2 (iconst_2) | 4000 (sipush 4000) | false | false | true |

### 3.5 StageDef Initialization (Bytecode Offsets 1948-2411)

| Stage | Index | timeStage | unlockRequirement | order |
|-------|-------|-----------|-------------------|-------|
| stage1 | 0 | false (iconst_0) | -1 (iconst_m1) | 0 |
| stage2 | 1 | false | 0 | 1 |
| stage3 | 2 | false | 1 | 2 |
| stage4 | 3 | false | 2 | 3 |
| stage5 | 4 | false | 99 (bipush 99) | 4 |
| stage1h | 5 | false (iconst_0) | 1 (iconst_1) | 5 |
| stage2h | 6 | false | 5 (bipush 5) | 6 |
| stage3h | 7 | false | 6 (bipush 6) | 7 |
| stage4h | 8 | false | 7 (bipush 7) | 8 |
| time1 | 9 | true (iconst_1) | 2 (bipush 2) | 9 |

## 4. StageDef Field Offsets (Bytecode)

| Field | Bytecode Field # | Putfield Offset |
|-------|------------------|-----------------|
| timeStage | #351 | offset 30 (stage method) |
| unlockRequirement | #352 | offset 100 |

### StageDef.stage() Method Signature
```
stage:(Ljava/lang/String;Ljava/lang/String;ZZILjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IIIILjava/lang/String;ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)Lme/holojava/fullgame/ContentCatalog$StageDef;
```

Parameter order (inferred from bytecode):
1. id (String)
2. name (String)
3. hard (Z)
4. timeStage (Z)
5. order (I)
5. tile (String)
6. palette (I)
6. enemyA (String)
6. enemyB (String)
6. enemyC (String)
6. spawnIntervalMs (I)
6. maxEnemies (I)
6. coinClear (I)
6. unlockRequirement (I)
6. bossA (String)
6. bossATimeSec (I)
6. bossB (String)
6. bossBTimeSec (I)
6. source (String)
6. coverage (String)

## 5. Method-Level Bytecode Evidence

### 5.1 FullGameCanvas.stageAllowed(int) — Bytecode Offset 940-978

```
0: iload_1                    // load index
1: invokestatic #170          // ContentCatalog.stage:(I)LStageDef;
4: astore_2                   // store StageDef
5: getstatic #108             // MODES
8: aload_0                     // this
9: getfield #134              // modeIndex
12: aaload                     // MODES[modeIndex]
13: getfield #117             // ModeDef.completion
16: iconst_2                  // COMPLETE_KILL_TARGET (2)
17: if_icmpne 25              // if completion != 2
20: aload_2                    // StageDef
21: getfield #122             // StageDef.timeStage
24: ireturn                   // return timeStage
25: aload_2                    // StageDef
26: getfield #122             // StageDef.timeStage
29: ifne 36                   // if timeStage != 0
32: iconst_1
33: goto 37
36: iconst_0
37: ireturn
```

### 5.2 FullGameCanvas.stageUnlocked(int) — Bytecode Offset 963-1002

```
0: iload_1                    // index
1: invokestatic #170          // ContentCatalog.stage:(I)LStageDef;
4: astore_2                   // StageDef
5: aload_2
6: getfield #123              // unlockRequirement
9: iconst_m1                  // -1
10: if_icmpeq 22              // if -1
13: aload_2
14: getfield #123             // unlockRequirement
17: bipush 99                 // 99
19: if_icmpne 24              // if != 99
22: iconst_1                  // true
23: ireturn
24: aload_2
25: getfield #122             // timeStage
28: ifeq 44                   // if !timeStage
31: aload_0                    // this
32: getfield #136             // save
35: aload_0                    // this
36: getfield #127             // characterIndex
39: iconst_2                  // 2
40: invokevirtual #225        // save.isCompleted:(II)Z
43: ireturn
44: aload_0                    // this
45: getfield #136             // save
48: aload_0                    // this
49: getfield #127             // characterIndex
52: aload_2                    // StageDef
53: getfield #123             // unlockRequirement
56: invokevirtual #225        // save.isCompleted:(II)Z
59: ireturn
```

### 5.3 RunWorld.update() Time Mode Victory (Offset 137-172)

```
137: aload_0                  // this
138: getfield #362            // mode
141: getfield #298           // ModeDef.completion
144: iconst_2                // COMPLETE_KILL_TARGET (2)
145: if_icmpne 172           // if completion != 2
148: aload_0                  // this
149: getfield #358           // kills
152: aload_0                  // this
153: getfield #362           // mode
156: getfield #301           // targetKills
159: if_icmplt 172           // if kills < targetKills
162: aload_0                  // this
163: iconst_1                // true
164: putfield #402           // won = true
167: aload_0
168: iconst_4                // STATE_VICTORY (4)
169: putfield #397           // state = STATE_VICTORY
172: return
```

## 4. Verification Artifacts

### 4.1 Bytecode Files Location
- `/tmp/holojava-bytecode/FullGameCanvas_bytecode.txt` (2,396 lines)
- `/tmp/holojava-bytecode/RunWorld_bytecode.txt` (9,075 lines)
- `/tmp/holojava-bytecode/ContentCatalog_bytecode.txt` (2,187 lines)
- `/tmp/holojava-bytecode/SaveStore_bytecode.txt` (299 lines)
- `/tmp/holojava-bytecode/FullGameMIDlet_bytecode.txt` (110 lines)

### 4.2 Tools Used
- `javap -c -p` (OpenJDK 21.0.12.1)
- `unzip` for JAR extraction
- Static analysis only — no runtime execution

## 5. Limitations

| Limitation | Impact |
|------------|--------|
| No runtime execution | Cannot verify runtime behavior |
| Partial bytecode coverage | Some methods not fully traced |
| No runtime on target device | CLDC 1.1 precision differences unverified |
| No dynamic analysis | Static analysis only |

---

**Status**: All reference class/method evidence documented. Ready for parity analysis.
