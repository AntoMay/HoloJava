# SaveStore Format Compatibility Audit — Summary

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git branch | `main` | ✅ |
| Git status | Clean + untracked reports | Clean + 12 dirs | ✅ |
| Source changes | None | `git diff` empty | ✅ |

## 2. Executive Summary

**Core Finding**: The reference JAR (`HoloJavaFullGame.jar`) contains a SaveStore implementation with **6-byte records**, while the current source code declares `DATA_LEN = 48` bytes. This is a **confirmed format mismatch** (`CONFIRMED_FORMAT_MISMATCH`).

### Key Findings

| Aspect | Current Source | Reference JAR Bytecode | Status |
|--------|----------------|------------------------|--------|
| `DATA_LEN` | 48 bytes | 6 bytes (`bipush 6; newarray byte`) | **MISMATCH** |
| `SETTINGS_INDEX` | 47 | Used as 47 in bytecode | Only valid if array ≥ 48 |
| Record length in RMS | 48 bytes (source) | 6 bytes (bytecode) | **MISMATCH** |
| Stage progress tracking | 8 stages (0-7) per char | 5 stages (0-4) only | Reduced capacity |
| Settings byte location | Index 47 | Stored at byte 5 in legacy | Different layout |

### Root Cause
The reference JAR (`HoloJavaFullGame.jar`) was built from an older source version where `DATA_LEN = 6` (legacy format only). The current source was later updated to `DATA_LEN = 48` to support expanded stage tracking (8 stages × 5 characters = 40 bits, plus settings), but the reference JAR was never rebuilt from this updated source.

### Compatibility Assessment

| Scenario | Current Source (48-byte) Reading JAR (6-byte) | JAR (6-byte) Reading Source (48-byte) |
|----------|-----------------------------------------------|----------------------------------------|
| **Fresh save** | ✅ Works (fresh 48-byte record) | N/A |
| **Legacy 6-byte record** | ⚠️ **CRASH** - `data[47]` access on 6-byte array | ✅ Works (loads 6 bytes, ignores rest) |
| **48-byte record** | ✅ Works | ⚠️ **TRUNCATION** - only 6 bytes read |

### Risk Assessment

| Risk | Severity | Likelihood | Impact |
|------|----------|------------|--------|
| `ArrayIndexOutOfBoundsException` on load | **CRITICAL** | High (if legacy 6-byte record exists) | Crash on startup |
| Silent data loss (truncation) | **HIGH** | Medium (JAR reading 48-byte record) | Lost progress/settings |
| Settings corruption | **HIGH** | Medium | Hardcore/character loss |

## 3. Key Evidence

| Evidence | Source | Bytecode | Status |
|----------|--------|----------|--------|
| `DATA_LEN = 48` | Source line 12 | `bipush 6; newarray byte` | **MISMATCH** |
| `SETTINGS_INDEX = 47` | Source line 13 | `iconst_5` (index 5) used | **MISMATCH** |
| Loop bound `i < DATA_LEN` | `i < 48` | `bipush 6; if_icmpge` | **MISMATCH** |
| `SETTINGS_INDEX = 47` | Source line 13 | `iconst_5` used as index | **INVALID for 6-byte** |

## 4. Reports Created

| File | Purpose |
|------|---------|
| `00_SAVESTORE_COMPATIBILITY_SUMMARY.md` | This summary |
| `01_SOURCE_SCHEMA_AND_HISTORY.md` | Source schema evolution |
| `02_REFERENCE_BYTECODE_LAYOUT.md` | Bytecode evidence |
| `03_COMPATIBILITY_RISK_MATRIX.md` | Risk analysis & recommendations |

## 5. Quality Gate

- ✅ JAR SHA-256 unchanged: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- ✅ Git HEAD unchanged: `666e338`
- ✅ Source unchanged: `git diff` empty
- ✅ All prior reports intact
- ✅ No builds, no runtime, no source modifications
- ✅ Reports in `/home/antomay/Projects/HoloJava/reports/savestore-format-compatibility-audit/`

---

**Conclusion**: The reference JAR uses a legacy 6-byte format incompatible with the current 48-byte source. **Critical incompatibility exists** — legacy 6-byte records will crash the current source on load (`ArrayIndexOutOfBoundsException` at `data[47]`). A migration strategy or JAR rebuild is required before deploying the current source.
