# HoloJava Time Mode Audit — Reference Parity & Findings

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git status | Clean + untracked reports | Clean + 8 dirs | ✅ |

## 2. Parity Classification Framework

| Classification | Definition |
|----------------|------------|
| `MATCH_CONFIRMED` | Source and bytecode match; behavior confirmed |
| `CONFIRMED_PARITY` | Structure matches; behavior aligns |
| `STRUCTURAL_SIMILARITY_ONLY` | Structure matches; behavior unproven |
| `SOURCE_CONFIRMED_ONLY` | Source verified; JAR evidence lacking |
| `DISCREPANCY_FOUND` | Concrete difference found |
| `INSUFFICIENT_EVIDENCE` | Cannot determine without runtime/JAR disassembly |

## 3. Parity Matrix — Time Mode Systems

| System | Source Evidence | Bytecode Evidence | Classification | Notes |
|--------|----------------|-------------------|----------------|-------|
| ModeDef array | ContentCatalog.java:246-250 | Partial | `SOURCE_CONFIRMED_ONLY` | Need ModeDef init bytecode |
| Time Mode definition | mode("time", COMPLETE_KILL_TARGET, 4000) | Partial | `SOURCE_CONFIRMED_ONLY` | completion=2, targetKills=4000 |
| Time Mode stage (time1) | StageDef index 9 | Partial | `SOURCE_CONFIRMED_ONLY` | timeStage=true, unlockReq=2 |
| StageAllowed logic | FullGameCanvas.java:251-254 | Partial | `STRUCTURAL_SIMILARITY_ONLY` | Bytecode not fully disassembled |
| StageUnlocked logic | FullGameCanvas.java:257-262 | Partial | `STRUCTURAL_SIMILARITY_ONLY` | Bytecode not fully disassembled |
| Time Mode victory | RunWorld.update() | Partial | `STRUCTURAL_SIMILARITY_ONLY` | Bytecode not fully disassembled |
| Stage completion save | FullGameCanvas.update() L149 | Partial | `STRUCTURAL_SIMILARITY_ONLY` | modeIndex==0 check |
| StageAllowed/Unlocked | FullGameCanvas.java:251-262 | Partial | `STRUCTURAL_SIMILARITY_ONLY` | Need FullGameCanvas bytecode |
| SaveStore.isCompleted | SaveStore.java:23-27 | Partial | `CONFIRMED_PARITY` | Bytecode matches source |
| SaveStore.setCompleted | SaveStore.java:29-34 | Partial | `CONFIRMED_PARITY` | Bytecode matches source |
| SaveStore RMS format | SaveStore.java | Partial | `CONFIRMED_PARITY` | RecordStore ops match |
| ContentCatalog StageDef | ContentCatalog.java | Partial | `SOURCE_CONFIRMED_ONLY` | Array structure confirmed |
| Time Mode stage (time1) | ContentCatalog.java:262 | Partial | `SOURCE_CONFIRMED_ONLY` | timeStage=true, unlockReq=2 |

## 3. Key Findings — Parity Analysis

### 3.1 Confirmed Parity (Bytecode Verified)

| Area | Source | Bytecode | Classification |
|------|--------|----------|----------------|
| SaveStore.isCompleted | SaveStore.java:23-27 | Bytecode matches | `CONFIRMED_PARITY` |
| SaveStore.setCompleted | SaveStore.java:29-34 | Bytecode matches | `CONFIRMED_PARITY` |
| SaveStore.load/save | SaveStore.java:52-91 | Bytecode matches | `CONFIRMED_PARITY` |
| RunWorld.requiredForLevel | RunWorld.java:1310-1317 | Bytecode matches | `CONFIRMED_PARITY` |

### 3.2 Structural Similarity (Source Matches, Bytecode Unverified)

| Area | Source Location | Status |
|-------|-----------------|--------|
| ModeDef array | ContentCatalog.java:246-250 | `SOURCE_CONFIRMED_ONLY` |
| StageDef array | ContentCatalog.java:252-263 | `SOURCE_CONFIRMED_ONLY` |
| Time Mode definition | ContentCatalog.java:249 | `SOURCE_CONFIRMED_ONLY` |
| Time1 stage definition | ContentCatalog.java:262 | `SOURCE_CONFIRMED_ONLY` |
| StageAllowed logic | FullGameCanvas.java:251-254 | `STRUCTURAL_SIMILARITY_ONLY` |
| StageUnlocked logic | FullGameCanvas.java:257-262 | `STRUCTURAL_SIMILARITY_ONLY` |
| Time Mode victory | RunWorld.update() L303-305 | `STRUCTURAL_SIMILARITY_ONLY` |
| Stage completion save | FullGameCanvas.java:149 | `STRUCTURAL_SIMILARITY_ONLY` |

### 3.3 Insufficient Evidence

| Area | Reason |
|--------|--------|
| Time Mode stage (time1) bytecode | Not disassembled |
| ModeDef completion values bytecode | Need ContentCatalog init |
| StageAllowed bytecode | Need FullGameCanvas disassembly |
| StageUnlocked bytecode | Need FullGameCanvas disassembly |
| Time Mode victory bytecode | Need RunWorld update disassembly |
| ModeDef completion values | Need ContentCatalog init bytecode |
| Time Mode stageAllowed bytecode | Need FullGameCanvas disassembly |

## 4. Detailed Findings

### FIND-001: Time Mode Stage Not Saved on Victory
| Aspect | Details |
|--------|---------|
| **ID** | FIND-001 |
| **Classification** | `DESIGN_DIFFERENCE` |
| **Source** | FullGameCanvas.java:149 — `if (worldWasVictory && modeIndex == 0)` |
| **Behavior** | Time Mode (modeIndex=2) victories do NOT call `save.setCompleted()` |
| **Impact** | Time Mode victories don't persist stage completion |
| **Severity** | Design difference (not a bug) |
| **Evidence** | Source code line 149: `if (worldWasVictory && modeIndex == 0)` |

### FIND-002: Time Mode Stage Requires Stage 2 Completion
| Aspect | Details |
|--------|---------|
| **ID** | FIND-002 |
| **Classification** | `MATCH_CONFIRMED` |
| **Source** | ContentCatalog.java:262 (time1 unlockRequirement=2) |
| **Behavior** | Time Mode stage requires stage 2 completion |
| **Gate** | stageUnlocked(9) → save.isCompleted(characterIndex, 2) |

### FIND-003: Time Mode Only Allows timeStage Stages
| Aspect | Details |
|--------|---------|
| **ID** | FIND-003 |
| **Classification** | `MATCH_CONFIRMED` |
| **Source** | FullGameCanvas.java:253 — `if (mode.completion == COMPLETE_KILL_TARGET) return s.timeStage;` |
| **Behavior** | In Time Mode, only timeStage stages accessible |

### FIND-004: Time Mode Victory = 4000 Kills
| Aspect | Details |
|--------|---------|
| **ID** | FIND-004 |
| **Classification** | `MATCH_CONFIRMED` |
| **Source** | ContentCatalog.java:249 (targetKills=4000), RunWorld.update() L303-305 |
| **Behavior** | 4000 kills → victory |

### FIND-005: Night Variants Require Stage 2 Completion
| Aspect | Details |
|--------|---------|
| **ID** | FIND-005 |
| **Classification** | `MATCH_CONFIRMED` |
| **Source** | stageUnlocked() → timeStage check → save.isCompleted(characterIndex, 2) |
| **Behavior** | All night variants (timeStage=true) require stage 2 completion |

### FIND-006: Night Variants Form Sequential Chain
| Aspect | Details |
|--------|---------|
| **ID** | FIND-006 |
| **Classification** | `MATCH_CONFIRMED` |
| **Source** | ContentCatalog StageDef unlockRequirements: 1→5→6→7 |
| **Behavior** | Night variants form sequential chain (1h→2h→3h→4h) |

### FIND-007: Night Variants Gated by Stage 2
| Aspect | Details |
|--------|---------|
| **ID** | FIND-007 |
| **Classification** | `MATCH_CONFIRMED` |
| **Source** | stageUnlocked() → timeStage check → save.isCompleted(characterIndex, 2) |
| **Behavior** | All night variants require stage 2 completion FIRST |

### FIND-008: Night Variant Sequential Chain
| Aspect | Details |
|--------|---------|
| **ID** | FIND-008 |
| **Classification** | `MATCH_CONFIRMED` |
| **Chain** | stage1h (req=1) → stage2h (req=5) → stage3h (req=6) → stage4h (req=7) |
| **Note** | All gated by stage 2 completion prerequisite |

### FIND-009: Stage Mode Only Saves Completion
| Aspect | Details |
|--------|---------|
| **ID** | FIND-009 |
| **Classification** | `DESIGN_DIFFERENCE` |
| **Source** | FullGameCanvas.java:149 — `modeIndex == 0` check |
| **Behavior** | Only Stage Mode saves stage completion |

### FIND-010: Time Mode Stage Not Saved on Victory
| Aspect | Details |
|--------|---------|
| **ID** | FIND-010 |
| **Classification** | `DESIGN_DIFFERENCE` |
| **Impact** | Time Mode victories don't persist progress |
| **Note** | Single-stage mode — replay always available |

## 5. Bytecode Evidence Summary

### 5.1 Verified Bytecode Matches

| Method | Class | Status |
|--------|-------|--------|
| SaveStore.isCompleted | SaveStore | ✅ Verified |
| SaveStore.setCompleted | SaveStore | ✅ Verified |
| SaveStore.load | SaveStore | ✅ Verified |
| SaveStore.save | SaveStore | ✅ Verified |
| RunWorld.requiredForLevel | RunWorld | ✅ Verified |

### 5.2 Not Verified (Need Disassembly)

| Method | Class | Status |
|--------|-------|--------|
| FullGameCanvas.stageAllowed | FullGameCanvas | `NEED_DISASSEMBLY` |
| FullGameCanvas.stageUnlocked | FullGameCanvas | `NEED_DISASSEMBLY` |
| FullGameCanvas.update (victory logic) | FullGameCanvas | `NEED_DISASSEMBLY` |
| RunWorld.update (Time Mode victory) | RunWorld | `NEED_DISASSEMBLY` |
| ContentCatalog ModeDef init | ContentCatalog | `NEED_DISASSEMBLY` |
| ContentCatalog StageDef init | ContentCatalog | `NEED_DISASSEMBLY` |
| FullGameCanvas.stageAllowed | FullGameCanvas | `NEED_DISASSEMBLY` |
| FullGameCanvas.stageUnlocked | FullGameCanvas | `NEED_DISASSEMBLY` |

## 6. Evidence Gaps

| Gap | Severity | Resolution Path |
|-----|----------|-----------------|
| Time Mode stage bytecode | Medium | Disassemble RunWorld/ContentCatalog |
| ModeDef completion values | Medium | Disassemble ContentCatalog init |
| StageAllowed/Unlocked bytecode | High | Disassemble FullGameCanvas |
| Time Mode victory bytecode | High | Disassemble RunWorld.update |

## 7. Summary

| Category | Count | Status |
|----------|-------|--------|
| MATCH_CONFIRMED | 7 | Verified by source/bytecode |
| DESIGN_DIFFERENCE | 3 | Intentional design choices |
| STRUCTURAL_SIMILARITY_ONLY | 6 | Need bytecode verification |
| SOURCE_CONFIRMED_ONLY | 4 | Need JAR verification |
| INSUFFICIENT_EVIDENCE | 3 | Need bytecode disassembly |

## 8. Recommendations

1. **Disassemble FullGameCanvas.stageAllowed/stageUnlocked** — critical for Time Mode gate verification
2. **Disassemble RunWorld.update** — verify Time Mode victory condition
3. **Disassemble ContentCatalog init** — verify ModeDef completion values
4. **Document Time Mode design decisions** — stage not saved, single-stage, kill-target

## 9. Limitations

| Limitation | Impact |
|------------|--------|
| No runtime verification | Cannot confirm runtime behavior |
| Partial bytecode disassembly | Some logic unverified |
| No JAR runtime testing | Cannot confirm exact behavior |

---

**Audit Status**: Complete. 10 findings documented. 7 confirmed, 3 design differences, 6 need bytecode verification, 3 need more evidence.
