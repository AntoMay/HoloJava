# HoloJava — Tahap 3A: Validasi Kritis Desain Save Migration

## Baseline
| Item | Status |
|------|--------|
| Reports reviewed | `reports/stage-progression-design/03_SIMPLE_PROGRESSION_DESIGN.md`, `reports/savestore-design-validation-20261014/01_BYTE_MAPPING_EVIDENCE.md` |
| Source unchanged | ✅ `git diff` empty |
| JAR SHA-256 | ✅ `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` |

---

## 1. Legacy Mapping: Byte/Bit Transposition Table

### 1.1 Legacy Format (Reference JAR Bytecode — 6 bytes, **stage-major**)

| Byte Offset | Field | Bit Layout | Meaning |
|-------------|-------|------------|---------|
| 0 | Stage 0 | bit 0-4 = char 0-4 | Character n completed Stage 0 |
| 1 | Stage 1 | bit 0-4 = char 0-4 | Character n completed Stage 1 |
| 2 | Stage 2 | bit 0-4 = char 0-4 | Character n completed Stage 2 |
| 3 | Stage 3 | bit 0-4 = char 0-4 | Character n completed Stage 3 |
| 4 | Stage 4 | bit 0-4 = char 0-4 | Character n completed Stage 4 |
| 5 | Settings | bit 0 = hardcore, bit 1-5 = char index (0-4) | Game settings |

**Characters supported**: 5 (bits 0-4 only). Bytecode `setCompleted` allows `characterIndex <= 7` but only 5 bits exist — bits 5-7 always zero.

### 1.2 Target Format (Source Design — 48 bytes, **character-major**)

| Byte Offset | Field | Bit Layout | Meaning |
|-------------|-------|------------|---------|
| 0 | Character 0 | bit 0-4 = stage 0-4, bit 5-7 = stage 5-7 | Char 0 completed Stage m |
| 1 | Character 1 | bit 0-4 = stage 0-4, bit 5-7 = stage 5-7 | Char 1 completed Stage m |
| 2 | Character 2 | bit 0-4 = stage 0-4, bit 5-7 = stage 5-7 | Char 2 completed Stage m |
| 3 | Character 3 | bit 0-4 = stage 0-4, bit 5-7 = stage 5-7 | Char 3 completed Stage m |
| 4 | Character 4 | bit 0-4 = stage 0-4, bit 5-7 = stage 5-7 | Char 4 completed Stage m |
| 5-46 | Reserved | — | Zero |
| 47 | Settings | bit 0 = hardcore, bit 1-5 = char index (0-4) | Game settings |

**Characters supported**: 5 (indices 0-4). Source bounds check uses `ContentCatalog.CHARACTERS.length` (stub returns 0, CSV has 47).

### 1.3 Transposition Mapping (Proven Required)

| Legacy → Target | Formula | Verified |
|-----------------|---------|----------|
| `L[stage]` bit `char` → `S[char]` bit `stage` | `S[char][stage] = L[stage][char]` | ✅ Mathematical proof |
| `L[5]` (settings) → `S[47]` (settings) | Direct byte copy | ✅ Bit-identical |
| Stages 5-7 in legacy | N/A → `S[char]` bit 5-7 = 0 | ✅ Default incomplete |

### 1.4 Concrete Example (Proof Mapping Not Reversed)

**Legacy save**: Character 2 completed Stage 1; Character 4 completed Stage 3.
- `loaded[1]` (Stage 1) = `0b00000100` (bit 2 = 1)
- `loaded[3]` (Stage 3) = `0b00010000` (bit 4 = 1)
- `loaded[5]` = `0b00000001` (hardcore on, char 0)

**After CORRECT transpose**:
- `data[2]` (Char 2) = `0b00000010` (bit 1 = 1) → "Char 2 completed Stage 1" ✅
- `data[4]` (Char 4) = `0b00001000` (bit 3 = 1) → "Char 4 completed Stage 3" ✅
- `data[47]` = `0b00000001` → hardcore on, char 0 ✅

**After WRONG byte-copy** (current source bug):
- `data[1]` (Char 1) = `0b00000100` → "Char 1 completed Stage 2" ❌
- `data[3]` (Char 3) = `0b00010000` → "Char 3 completed Stage 4" ❌

### 1.5 Status: **PASS** — Mapping fully specified, transposition proven, concrete verification included.

---

## 2. RecordStore Atomicity: CLDC/MIDP API Analysis

### 2.1 API Used in Design
```java
RecordStore.openRecordStore(String name, boolean createIfNecessary)
RecordStore.getRecord(int recordId)              // Returns byte[]
RecordStore.setRecord(int recordId, byte[] data, int offset, int numBytes)
RecordStore.deleteRecord(int recordId)
RecordStore.closeRecordStore()
```
All from `javax.microedition.rms` — standard MIDP 2.0 / CLDC 1.1.

### 2.2 Atomicity Claims vs Reality

| Design Claim | API Reality | Verdict |
|--------------|-------------|---------|
| **Temp record write** (`setRecord(TEMP_ID, ...)`) | Supported — separate record ID | ✅ Valid |
| **Read-back verification** (`getRecord(TEMP_ID)`) | Supported — returns `byte[]` | ✅ Valid |
| **Replace main record** (`setRecord(RECORD_ID, ...)`) | Supported — overwrites existing | ✅ Valid |
| **Delete temp record** (`deleteRecord(TEMP_ID)`) | Supported | ✅ Valid |
| **Legacy untouched until verify** | `setRecord(RECORD_ID)` only called AFTER verify | ✅ Valid |
| **Zero data loss on power failure** | **NOT GUARANTEED** — RMS has no transaction support | ⚠️ **NEEDS REVISION** |

### 2.3 Failure Scenarios (Honest Assessment)

| Failure Point | Legacy Record | New Record | Result |
|---------------|---------------|------------|--------|
| Power loss before `setRecord(TEMP_ID)` | ✅ Intact | ❌ None | Safe |
| Power loss during `setRecord(TEMP_ID)` | ✅ Intact | ⚠️ Corrupt/partial | Safe (legacy kept) |
| Power loss after TEMP verify, before `setRecord(RECORD_ID)` | ✅ Intact | ✅ Verified in TEMP | **Recoverable** — migration can retry |
| Power loss during `setRecord(RECORD_ID)` | ⚠️ **Overwriting** | ⚠️ Writing | **DATA LOSS POSSIBLE** — main record corrupted mid-write |
| `setRecord(RECORD_ID)` throws exception | ✅ Intact (not yet called) | ✅ In TEMP | Safe |
| `deleteRecord(TEMP_ID)` fails | ✅ Updated | ✅ In TEMP (leaked) | Safe (harmless leak) |

### 2.4 Critical Gap: Mid-Write Corruption on Main Record

**RMS `setRecord` overwrites in-place**. No atomic replace. If power fails during the final `setRecord(RECORD_ID, ...)`, the main record is corrupted. Legacy is already gone (we decided to replace it).

**Mitigation not in design**: Write to NEW record ID (e.g., 3), verify, then delete old (1) and rename not possible — RMS has no rename. Must overwrite ID 1.

### 2.5 Status: **NEEDS REVISION** — Design claims "zero data loss" but RMS cannot guarantee atomic replace. Must document: legacy preserved until main record write **starts**; mid-write corruption on main record possible but legacy already migrated to temp. Recovery requires re-migration logic on next launch.

---

## 3. Save Versioning: Unambiguous Record Layout

### 3.1 Proposed Layout (Design v2)

| Offset | Size | Field | Notes |
|--------|------|-------|-------|
| 0 | 1 | Version byte | `2` = current format |
| 1-48 | 48 | Data payload | `DATA_LEN = 48` bytes |
| **Total** | **49** | | |

**Legacy v1**: 6 bytes, no version byte. Detected by `length == 6`.

### 3.2 Detection Logic (from design)

```java
isLegacyFormat(byte[] d): d != null && d.length == 6 && validateLegacy(d)
isCurrentFormat(byte[] d): d != null && d.length == 49 && d[0] == 2 && validateCurrent(d)
```

### 3.3 Ambiguity Analysis

| Record | Length | Version Byte | Detected As | Safe? |
|--------|--------|--------------|-------------|-------|
| Legacy | 6 | N/A | v1 (legacy) | ✅ |
| Current | 49 | `2` | v2 (current) | ✅ |
| Corrupt legacy | 6 | N/A | Unknown (fails `validateLegacy`) | ✅ Rejected |
| Corrupt current | 49 | `2` | Unknown (fails `validateCurrent`) | ✅ Rejected |
| Unknown v3+ | 49 | `3` | Unknown (version != 2) | ✅ Rejected |
| Truncated current | 7-48 | N/A | Unknown (wrong length) | ✅ Rejected |

### 3.4 Settings/Progression Non-Collision

- **Settings** at `data[47]` (internal) → `record[48]` (on disk) — no overlap with version byte at `record[0]`
- **Progression** at `data[0..4]` → `record[1..5]` — no overlap
- **Reserved** at `data[5..46]` → `record[6..47]` — no overlap

### 3.5 Status: **PASS** — Layout unambiguous, version byte at fixed offset 0, legacy detected by length, no field collision. `validateLegacy`/`validateCurrent` add semantic checks.

---

## 4. Scope Progres: 5 Normal Stages, Sequential Unlock, Hard/Time Hidden

### 4.1 Stage Definitions (Source: `ContentCatalog.java:252-263`)

| Array Idx | Stage ID | `order` | `hard` | `timeStage` | `unlockRequirement` | Normal Path? |
|-----------|----------|---------|--------|-------------|---------------------|--------------|
| 0 | `stage1` | 0 | false | false | -1 | ✅ |
| 1 | `stage2` | 1 | false | false | 0 | ✅ |
| 2 | `stage3` | 2 | false | false | 1 | ✅ |
| 3 | `stage4` | 3 | false | false | 2 | ✅ |
| 4 | `stage5` | 4 | false | false | **99** (bug: treated as unlocked) | ✅ |
| 5 | `stage1h` | 5 | true | false | 1 | ❌ |
| 6 | `stage2h` | 6 | true | false | 5 | ❌ |
| 7 | `stage3h` | 7 | true | false | 6 | ❌ |
| 8 | `stage4h` | 8 | true | false | 7 | ❌ |
| 9 | `time1` | 9 | false | true | 2 | ❌ |

### 4.2 Design Enforcement

| Requirement | Implementation | Status |
|-------------|----------------|--------|
| Only 5 normal stages active | `STAGE_COUNT = 5` clamp in `isCompleted`/`setCompleted` | ✅ |
| Stage 1 open initially | `unlockRequirement = -1` → `stageUnlocked` returns true | ✅ |
| Sequential unlock | `unlockRequirement` = previous stage order (0→1→2→3→4) | ✅ (after fixing stage5 to 4) |
| Unlocked stages not selectable | `stageUnlocked` called in `updateStages` before allowing selection | ✅ |
| Hard/Time code preserved | No deletion, only UI filtering in `drawModes`/`updateStages` | ✅ |
| Win triggers save | `FullGameCanvas:149` → `save.setCompleted(char, stage.order)` only in Stage Mode | ✅ |

### 4.3 Stage5 Fix Required

**Current**: `unlockRequirement = 99` → code treats as unlocked (line 259: `== 99` returns true)
**Required**: Change to `4` so it unlocks after Stage 4 (order 3).

### 4.4 Status: **PASS** — Scope correctly defined, 5 normal stages mapped, sequential unlock logic verified, Hard/Time preserved in code. Only `stage5.unlockRequirement` fix needed (1 line).

---

## Summary

| Area | Status | Key Finding |
|------|--------|-------------|
| 1. Legacy Mapping | **PASS** | Transposition proven; concrete example shows byte-copy corrupts data |
| 2. RecordStore Atomicity | **NEEDS REVISION** | RMS cannot guarantee atomic replace; mid-write corruption on main record possible; design must document this limitation |
| 3. Save Versioning | **PASS** | Unambiguous 49-byte layout with version byte at offset 0; legacy detected by length |
| 4. Scope Progres | **PASS** | 5 normal stages (order 0-4), sequential unlock, Hard/Time hidden in UI only |

---

## Required Revision for Design (RecordStore Atomicity)

**Update `03_SIMPLE_PROGRESSION_DESIGN.md` Risk Assessment**:

```markdown
## Revised Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Migration transpose bug | LOW | Pure function, testable with vectors |
| Write failure mid-migration | LOW | Legacy record preserved until verify |
| Partial/invalid legacy | LOW | `validateLegacy` rejects corrupt |
| Future format change | LOW | Version byte enables v3 migration |
| Save failure during play | LOW | Atomic temp-write + verify |
| **Power loss during main record overwrite** | **MEDIUM** | **Documented: legacy already migrated to temp; on next launch, detect v2 in temp (ID 2) and recover** |
```

**Add Recovery Logic to `load()`**:
```java
// In load(), check TEMP_RECORD_ID (2) for completed migration
byte[] temp = store.getRecord(TEMP_RECORD_ID);
if (temp != null && isCurrentFormat(temp)) {
    // Migration completed but main record write failed
    store.setRecord(RECORD_ID, temp, 0, temp.length);
    store.deleteRecord(TEMP_RECORD_ID);
    System.arraycopy(temp, 1, data, 0, DATA_LEN);
    return;
}
```

---

## Verdict

**DESIGN APPROVED WITH ONE REVISION**: Add power-loss recovery for main record overwrite by checking temp record on load. All other areas pass.

**Next Step**: Await approval to implement revised design.