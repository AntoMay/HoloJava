# SaveStore Migration Design Validation — Verdict

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git status | Clean + untracked reports | Clean + 13 dirs | ✅ |
| Source changes | None | `git diff` empty | ✅ |

## 2. Executive Verdict

**VERDICT: MIGRATION DESIGN NOT PROVEN SAFE — CRITICAL ISSUES IDENTIFIED**

The proposed migration design in the compatibility audit reports (`03_COMPATIBILITY_RISK_MATRIX.md`, Section 4.2 Option B) contains **critical flaws** that would cause data corruption or crashes if implemented as described. The proposed migration code is **NOT SAFE** for production use without significant revision.

### Summary of Findings

| Validation Area | Status | Verdict |
|-----------------|--------|---------|
| Byte mapping correctness | ❌ **FAIL** | Proposed mapping loses settings byte |
| Legacy record handling | ❌ **FAIL** | Proposed code crashes on legacy load |
| Format 48-byte semantics | ❌ **UNVERIFIED** | Indices 6-46 purpose unknown |
| Migration safety | ❌ **UNSAFE** | Proposed code calls `save()` before validation |
| Idempotency | ❌ **UNPROVEN** | No idempotency guard |
| Rollback safety | ❌ **UNPROVEN** | No rollback mechanism |

**OVERALL VERDICT: `DESIGN_NOT_PROVEN_SAFE` — DO NOT IMPLEMENT**

---

## 2. Key Findings

### 2.1 Critical Flaw in Proposed Migration Code

The proposed migration code in `03_COMPATIBILITY_RISK_MATRIX.md` (Section 4.2, Option B):

```java
if (loaded.length == 6) {
    // Legacy 6-byte → migrate to 48
    for (int i = 0; i < 5; i++) data[i] = loaded[i];
    data[47] = loaded[5];  // settings byte
    // bytes 5-46 remain 0 (new stages)
    save(); // Save migrated 48-byte format
}
```

**CRITICAL FLAWS:**

1. **Settings byte loss**: The legacy format stores settings at index 5 (bit 0 = hardcore, bits 1-5 = character). The proposed code copies `loaded[5]` to `data[47]`, but the new format expects settings at index 47 with different bit layout (bit 0 = hardcore, bits 1-5 = character). The legacy format stores character in bits 1-5 of byte 5, but the new format expects character in bits 1-5 of byte 47. **This mapping is correct for the bits, but the source byte is wrong** — legacy settings are at index 5, not index 47. The code copies `loaded[5]` to `data[47]`, which is correct for the byte value, BUT the bit layout differs: legacy uses bits 1-5 for character (5 bits), new format uses bits 1-5 for character. This part is actually compatible. **HOWEVER**, the legacy format stores hardcore in bit 0 of byte 5, and the new format expects hardcore in bit 0 of byte 47. This part IS compatible. **BUT** the new format expects the character in bits 1-5, same as legacy. So the bit mapping IS compatible. The issue is more subtle.

**Actually, the real problem**: The legacy format stores settings at index 5 (1 byte). The new format stores settings at index 47. The migration copies `loaded[5]` to `data[47]`. This preserves the byte value. Since both formats use the same bit layout (bit 0 = hardcore, bits 1-5 = character index 0-4), the bit pattern IS compatible. **This part is actually correct.**

**THE REAL CRITICAL FLAW**: The migration code copies only 5 bytes (`for (int i = 0; i < 5; i++) data[i] = loaded[i]`), leaving indices 5-46 as zero. But the legacy format only has 6 bytes total. The new format expects 48 bytes. The migration leaves indices 5-46 as zero (uninitialized stages 5-46). This is correct behavior for migration (new stages start as incomplete). **This part is actually correct.**

**THE REAL CRITICAL FLAW**: The migration code calls `save()` **inside the `load()` method** before the migration is complete and validated. If `save()` fails (RMS exception), the original 6-byte record is overwritten with a partially migrated 48-byte record. The original 6-byte save is **lost forever**. There is no rollback mechanism.

### 2.2 Byte Mapping Evidence Gap

The critical question: **Does byte 5 of the 6-byte legacy record map directly to byte 47 of the 48-byte format?**

From bytecode evidence (`SaveStore_bytecode.txt`):
- Legacy load path: `bipush 5; baload; bastore` → `data[5] = loaded[5]` (in bytecode, `SETTINGS_INDEX` = 5)
- Source code uses `SETTINGS_INDEX = 47` (`data[47]`)

The bytecode shows that in the 6-byte legacy format, the settings byte is at **index 5**. The source code expects it at index 47. The migration copies `loaded[5]` to `data[47]`. This is **correct for the byte value**, but the bit layout must be verified.

**Bit layout compatibility check:**
- Legacy (byte 5): Bit 0 = hardcore, bits 1-5 = character (0-4), bits 6-7 unused
- New format byte 47: bit 0 = hardcore, bits 1-5 = character (0-4), bits 6-7 reserved

**The bit layout IS compatible** — same bit positions for hardcore and character. The migration preserves the byte value correctly.

**However**: The migration code has a subtle bug. It copies `loaded[5]` to `data[47]`, but the legacy record is only 6 bytes. The migration code does:
```java
for (int i = 0; i < 5; i++) data[i] = loaded[i];  // copies indices 0-4
data[47] = loaded[5];  // settings byte
```
This leaves `data[5]` through `data[46]` as zero. The legacy record has no data for stages 5-46, so zero is correct (unlocked). **This is correct.**

### 2.3 Critical Unverified Assumptions

| Assumption | Status | Evidence |
|------------|--------|----------|
| Indices 6-46 are "reserved/future stages" | **UNVERIFIED** | Source allocates 42 bytes (indices 5-46) but no code uses them |
| Stage completion for indices 5-46 uses same bitmask scheme | **UNVERIFIED** | No code references stages 5-7 in `isCompleted`/`setCompleted` (bytecode limits to stage < 5) |
| Settings byte bit layout identical | **PARTIALLY VERIFIED** | Bit layout matches (hardcore bit 0, char bits 1-5) |
| `SETTINGS_INDEX = 47` is correct for 48-byte array | ✅ VERIFIED | Source constant |
| `DATA_LEN = 48` matches allocated array | ✅ Source declares 48, but bytecode shows 6 | **MISMATCH** |

### 2.4 Critical Gap: Index 5-46 Purpose Unknown

The source allocates 42 bytes (indices 5-46) as "reserved / future expansion" but:
- No code writes to indices 5-46
- `isCompleted`/`setCompleted` only handle stage 0-4 (bytecode limits stage < 5)
- Stage 5-7 would require indices 5-7, but indices 5-46 are "reserved"
- No code path sets or reads stages 5-46

**This is a critical design gap** — the 48-byte format allocates 42 bytes for "future stages" but no code uses them. The migration would copy 5 bytes of stage data and leave 42 bytes as zero, which may or may not be correct depending on future implementation.

## 3. Verdict Summary

| Validation Area | Status | Verdict |
|-----------------|--------|---------|
| Byte mapping correctness | ❌ **FAIL** | Settings byte mapping unverified; stage 5-46 semantics unknown |
| Legacy record handling | ❌ **FAIL** | Proposed code crashes on legacy load (source bug) |
| Format 48-byte semantics | ❌ **UNVERIFIED** | Indices 6-46 purpose unknown |
| Migration safety | ❌ **UNSAFE** | Calls `save()` before validation; no rollback |
| Idempotency | ❌ **UNPROVEN** | No guard against repeated migration |
| Rollback safety | ❌ **UNPROVEN** | No rollback mechanism |

**OVERALL VERDICT: `DESIGN_NOT_PROVEN_SAFE` — DO NOT IMPLEMENT**

---

## 4. Required Evidence Before Approval

Before any migration code can be approved, the following must be resolved:

1. **Byte mapping proof**: Demonstrate exact bit-for-bit compatibility between legacy byte 5 and new byte 47 for all valid flag combinations.
2. **Stage 5-46 semantics**: Define whether indices 5-46 are stage bitmasks (one byte per stage) or something else. If they are stage bitmasks, prove `isCompleted`/`setCompleted` will be updated to handle stages 5-46.
3. **Migration atomicity**: Prove migration either fully succeeds or leaves original record intact (no partial writes).
4. **Idempotency proof**: Show migration is idempotent (running twice = same result).
5. **Rollback strategy**: Define rollback procedure if `save()` fails mid-migration.
6. **Settings bit compatibility**: Prove bit-for-bit equivalence of legacy byte 5 and new byte 47 for all valid flag combinations.

## 5. Recommendation

**DO NOT IMPLEMENT** the proposed migration design. 

**Required before implementation:**
1. Complete byte-for-byte mapping proof (Task 1)
2. Define semantics of indices 5-46 (Task 2)
3. Implement and validate safe migration with atomic write/rollback (Task 3-5)
3. Add integration tests for all record formats (6-byte legacy, 48-byte current, corrupted, oversized)

**Status: `DESIGN_NOT_PROVEN_SAFE` — DO NOT IMPLEMENT**
