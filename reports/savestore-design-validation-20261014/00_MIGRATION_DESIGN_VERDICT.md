# SaveStore Migration Design Validation — Verdict (Recovery Session)

## 1. Baseline Verification (Post-Restart)

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antomay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git status | Clean + untracked reports | Clean + 13 dirs | ✅ |
| Source changes | None | `git diff` empty | ✅ |

## 2. Executive Verdict

**VERDICT: MIGRATION DESIGN NOT PROVEN SAFE — FUNDAMENTAL SEMANTIC INCOMPATIBILITY DISCOVERED**

The previous validation report (`savestore-migration-design-validation/00_MIGRATION_DESIGN_VERDICT.md`) correctly identified critical flaws but **missed the root cause**: the legacy 6-byte format and the current 48-byte source format use **fundamentally different data layouts** (transposed dimensions). This makes simple byte-copy migration **mathematically impossible** without data transformation.

### Summary of Findings

| Validation Area | Status | Verdict |
|-----------------|--------|---------|
| Byte mapping correctness | ❌ **FAIL** | Legacy and source use transposed layouts (stages×chars vs chars×stages) |
| Legacy record handling | ❌ **FAIL** | Proposed migration copies bytes without transposition → corrupts data |
| Format 48-byte semantics | ⚠️ **PARTIAL** | Indices 0-4 = character bitmasks (8 stages each); 5-46 = reserved; 47 = settings |
| Migration safety | ❌ **UNSAFE** | Proposed code calls `save()` before validation; no rollback |
| Idempotency | ❌ **UNPROVEN** | No idempotency guard |
| Rollback safety | ❌ **UNPROVEN** | No rollback mechanism |

**OVERALL VERDICT: `DESIGN_NOT_PROVEN_SAFE` — DO NOT IMPLEMENT**

---

## 3. Critical New Finding: Transposed Data Layouts

### 3.1 Legacy Format (Reference JAR Bytecode — 6 bytes)

```
Byte Offset | Content                          | Interpretation
------------|----------------------------------|----------------
0           | Stage 0 bitmask                  | Bit n = Character n completed stage 0
1           | Stage 1 bitmask                  | Bit n = Character n completed stage 1
2           | Stage 2 bitmask                  | Bit n = Character n completed stage 2
3           | Stage 3 bitmask                  | Bit n = Character n completed stage 3
4           | Stage 4 bitmask                  | Bit n = Character n completed stage 4
5           | Settings (hardcore + character)  | Bit 0 = hardcore, Bits 1-5 = char index
```

**Access pattern (bytecode):** `data[stageOrder]` — stage is array index, character is bit position.

### 3.2 Current Source Format (48 bytes)

```
Byte Offset | Content                          | Interpretation
------------|----------------------------------|----------------
0           | Character 0 stage bitmask        | Bit m = Stage m completed by char 0
1           | Character 1 stage bitmask        | Bit m = Stage m completed by char 1
2           | Character 2 stage bitmask        | Bit m = Stage m completed by char 2
3           | Character 3 stage bitmask        | Bit m = Stage m completed by char 3
4           | Character 4 stage bitmask        | Bit m = Stage m completed by char 4
5-46        | Reserved / future expansion        | 42 bytes, all zero
47          | Settings (hardcore + character)  | Bit 0 = hardcore, Bits 1-5 = char index
```

**Access pattern (source):** `data[characterIndex]` — character is array index, stage is bit position.

### 3.3 The Transposition

| Dimension | Legacy (Bytecode) | Current Source |
|-----------|-------------------|----------------|
| Array index | Stage (0-4) | Character (0-4) |
| Bit position | Character (0-4) | Stage (0-7) |
| Stages tracked | 5 (0-4) | 8 (0-7) |
| Characters tracked | 5 (bits 0-4) | 5 (indices 0-4) |

**This is a matrix transpose:** Legacy = `stage[5][char_bits]`, Source = `character[5][stage_bits]`.

### 3.4 Why Simple Byte Copy Fails

The proposed migration in `03_COMPATIBILITY_RISK_MATRIX.md`:

```java
for (int i = 0; i < 5; i++) data[i] = loaded[i];  // Copies stage bitmasks to character bitmasks!
data[47] = loaded[5];  // Settings byte — this part IS compatible
```

**Result:** Legacy "Stage 0 completions for all chars" becomes "Character 0 completions for all stages". **Data is corrupted**, not migrated.

Example: If legacy has Character 2 completed Stage 1 → `loaded[1]` has bit 2 set.
After migration: `data[1]` (Character 1) has bit 2 set → "Character 1 completed Stage 2" — **wrong character, wrong stage**.

---

## 4. Source Code Bugs Identified (Independent of Migration)

### 4.1 Stage Bound Check vs Allocation Mismatch

```java
// Source lines 23-24, 29-30
stageOrder > 7 allowed (8 stages: 0-7)
but only data[0-4] allocated for character bitmasks
```

If `stageOrder` = 5, 6, or 7: `data[characterIndex]` accesses indices 0-4 (OK), but bit position 5-7 in those bytes. **This works for bits 5-7** — each character byte has 8 bits, so stages 0-7 fit in one byte per character. **This part is actually consistent.**

### 4.2 Legacy Load Path — Settings Index

```java
// Source lines 59-61
if (loaded.length == LEGACY_LEN) {  // 6
    for (int i = 0; i < 5; i++) data[i] = loaded[i];  // Copies 5 bytes
    data[SETTINGS_INDEX] = loaded[5];  // SETTINGS_INDEX = 47
}
```

**This does NOT crash** — `data` is 48 bytes, index 47 is valid. The previous audit incorrectly claimed `ArrayIndexOutOfBoundsException` because it confused the bytecode's 6-byte array with the source's 48-byte array.

**However:** The byte copy is semantically wrong (transposition issue above).

### 4.3 Modern Load Path — Truncation Risk

```java
// Source lines 62-64
} else {
    int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;
    for (int i = 0; i < count; i++) data[i] = loaded[i];
}
```

If a 48-byte record is loaded, all 48 bytes copied — OK.
If a 6-byte record is loaded (not caught by legacy check), only 6 bytes copied — `data[47]` remains 0 (settings lost).

---

## 5. Reference JAR Bugs Identified

### 5.1 Stage Bound Check Inconsistency

Bytecode `isCompleted`: `stageOrder < 5` (5 stages)
Bytecode `setCompleted`: `stageOrder < 5` AND `characterIndex <= 7` (8 characters!)

But legacy format only has 5 stage bytes (indices 0-4) and 5 character bits (0-4). Allowing `characterIndex <= 7` reads/writes bits 5-7 which are unused. **Inconsistent bounds.**

### 5.2 No Forward Compatibility

JAR reads 48-byte record → truncates to 6 bytes → loses stages 5-7 (which don't exist in legacy anyway) and settings byte at index 47.

---

## 6. Intended Behavior Analysis

### 6.1 What the Source Intends

- 5 characters × 8 stages = 40 stage-completion bits
- Packed as 5 bytes (one per character, 8 bits each)
- 42 bytes reserved for future use
- 1 byte settings at index 47

### 6.2 What the Legacy Format Provided

- 5 stages × 5 characters = 25 stage-completion bits
- Packed as 5 bytes (one per stage, 5 bits each)
- 1 byte settings at index 5

### 6.3 Compatibility Gap

| Feature | Legacy | Source | Compatible? |
|---------|--------|--------|-------------|
| Characters | 5 | 5 | ✅ |
| Stages | 5 | 8 | ⚠️ Source expects more |
| Layout | Stage-major | Character-major | ❌ **Transposed** |
| Settings location | Index 5 | Index 47 | ⚠️ Different offset, same bits |
| Settings bits | Bit 0=hardcore, 1-5=char | Bit 0=hardcore, 1-5=char | ✅ Same layout |

---

## 7. Reports Created in This Session

| File | Purpose |
|------|---------|
| `00_MIGRATION_DESIGN_VERDICT.md` | This verdict |
| `01_BYTE_MAPPING_EVIDENCE.md` | Byte-for-byte mapping with evidence |
| `02_INVALID_RECORD_AND_FAILURE_HANDLING.md` | Invalid record handling & failure modes |
| `03_SAFE_MIGRATION_SPEC.md` | Safe migration spec with transposition logic |

---

## 8. Final Integrity Gate

- ✅ Git HEAD unchanged: `666e338`
- ✅ JAR SHA-256 unchanged: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- ✅ Source unchanged: `git diff` empty
- ✅ No builds, no runtime, no source modifications
- ✅ Prior reports intact in `reports/savestore-format-compatibility-audit/` and `reports/savestore-migration-design-validation/`
- ✅ New reports in unique directory: `reports/savestore-design-validation-20261014/`

---

## 9. Conclusion

**The migration design is NOT proven safe.** The fundamental transposition between legacy (stage-major) and source (character-major) layouts means:

1. **No simple byte-copy migration exists** — requires bit-level transposition
2. **Previous audit reports missed this** — they assumed compatible layouts
3. **Any migration must transform data**, not copy bytes
4. **Source has internal consistency** for its own layout (5 chars × 8 stages in 5 bytes)
5. **Legacy format cannot represent stages 5-7** — migration must default them to incomplete

**Recommendation:** Do not implement migration until transposition logic is designed, verified, and tested. Consider whether the 48-byte format is the right design or if a versioned schema would be better.

**Decision Gate:** Migration design requires explicit approval AFTER transposition logic is proven correct.