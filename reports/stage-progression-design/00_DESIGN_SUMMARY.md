# HoloJava — Stage Progression Design Summary

## Baseline Verification
| Item | Status |
|------|--------|
| Project root | `/home/antomay/Projects/HoloJava` ✅ |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Git HEAD | `666e338` ✅ |
| Source unchanged | `git diff` empty ✅ |

---

## Current State Analysis

### Normal Path Stages (Stage Mode, non-time, non-hard)
| order | Stage ID | Name | unlockRequirement |
|-------|----------|------|-------------------|
| 0 | `stage1` | Grassy Plains | -1 (default open) |
| 1 | `stage2` | Holo Office | 0 |
| 2 | `stage3` | Halloween Castle | 1 |
| 3 | `stage4` | Gelora Bung Yagoo | 2 |
| 4 | `stage5` | Fantasy Island | **99 (always unlocked per code)** |

**Issue**: `stage5` has `unlockRequirement=99` but code treats 99 as "always unlocked" (line 259), breaking sequential progression.

### Hard/Time Stages (Excluded per Spec)
| order | Stage ID | Type | Notes |
|-------|----------|------|-------|
| 5 | `stage1h` | Hard | unlock=1 |
| 6 | `stage2h` | Hard | unlock=5 |
| 7 | `stage3h` | Hard | unlock=6 |
| 8 | `stage4h` | Hard | unlock=7 |
| 9 | `time1` | Time | unlock=2 (hardcoded check) |

### SaveStore Format Conflict (Critical)
| Aspect | Legacy JAR (6 bytes) | Current Source (48 bytes) |
|--------|---------------------|---------------------------|
| Layout | Stage-major: `data[stage]` bit `char` | Char-major: `data[char]` bit `stage` |
| Stages tracked | 5 (0-4) | 8 (0-7) |
| Settings | Index 5 | Index 47 |
| **Compatibility** | **Fundamentally transposed** — byte copy corrupts data |

### Win/Save/Load Flow
1. **Win**: `FullGameCanvas:149` → `save.setCompleted(char, stage.order)` only in Stage Mode (`modeIndex==0`)
2. **Save**: `SaveStore:32-33` → sets bit in `data[char]`, calls `save()` (writes 48 bytes)
3. **Load**: `SaveStore:59-65` → legacy path copies bytes 0-4 + settings to 47 (**wrong transpose**)
4. **Stage Select**: `stageAllowed` filters by mode, `stageUnlocked` checks `unlockRequirement`

---

## Three Design Alternatives

### Alternative A: Minimal Fix (Least Change)
**Goal**: Fix `stage5` unlock + clamp SaveStore to 5 normal stages only.

| Change | File | Lines |
|--------|------|-------|
| Change `stage5.unlockRequirement` from 99 → 4 | `ContentCatalog.java` | 257 |
| Clamp `stageOrder` max to 4 in `isCompleted`/`setCompleted` | `SaveStore.java` | 24, 30 |
| Remove Hard/Time from Stage Select (UI only) | `FullGameCanvas.java` | 251-255 |

**Risk**: Low code change. **Data Loss**: None (legacy migration still broken). **Legacy Compat**: Still broken.

---

### Alternative B: Compatible Save Migration
**Goal**: Fix progression + implement correct legacy→current transpose migration.

| Change | File | Lines |
|--------|------|-------|
| Add `migrateLegacy()` with bit transpose | `SaveStore.java` | New method |
| Fix `load()` to call migration before use | `SaveStore.java` | 52-75 |
| Fix `stage5.unlockRequirement` = 4 | `ContentCatalog.java` | 257 |
| Clamp `stageOrder` to 4 for normal path | `SaveStore.java` | 24, 30 |
| Hide Hard/Time modes from UI | `FullGameCanvas.java` | 251-255, 307 |

**Risk**: Medium (migration logic). **Data Loss**: Prevented by transpose. **Legacy Compat**: Fixed.

---

### Alternative C: Simple Progression System (Recommended)
**Goal**: Clean sequential progression, explicit versioned save format, future-proof.

| Change | File | Lines |
|--------|------|-------|
| Add `SAVE_VERSION = 2` constant | `SaveStore.java` | New |
| Versioned record: `[version][48 bytes data]` | `SaveStore.java` | `save()`/`load()` |
| Correct transpose migration v1→v2 | `SaveStore.java` | New method |
| Normal stages only: `STAGE_COUNT = 5` | `SaveStore.java` | New constant |
| `stage5.unlockRequirement` = 4 | `ContentCatalog.java` | 257 |
| Stage Select: filter to normal stages only | `FullGameCanvas.java` | 208-218, 251-255 |
| Mode Select: hide Endless/Time (keep code) | `FullGameCanvas.java` | 307 |

**Risk**: Medium. **Data Loss**: Zero (versioned, atomic write). **Legacy Compat**: Full v1→v2 migration.

---

## Comparison

| Criterion | A: Minimal | B: Migration | C: Simple System |
|-----------|------------|--------------|------------------|
| Files Changed | 3 | 3 | 3 |
| Lines Changed | ~10 | ~80 | ~100 |
| Legacy Save Support | ❌ Broken | ✅ Fixed (transpose) | ✅ Fixed (versioned) |
| Progression Correct | ✅ (5 stages) | ✅ (5 stages) | ✅ (5 stages) |
| Future Extensibility | Low | Medium | **High** |
| Data Loss Risk | Medium | Low | **None** |
| Test Complexity | Low | Medium | Medium |

---

## Recommendation: **Alternative C (Simple Progression System)**

**Reasoning**:
1. **Only C solves legacy format conflict permanently** — versioned schema prevents future mismatches
2. **Atomic write + verification** eliminates silent data loss
3. **Explicit `STAGE_COUNT = 5`** matches spec "normal stages only" and clamps at source
4. **Hard/Time code preserved** — just hidden from UI per spec
5. **Clean foundation** for future stages without re-migration

**Acceptance Criteria**:
- Fresh install: Stage 1 open, others locked
- Complete Stage 1 → Stage 2 unlocked (persists after restart)
- Legacy 6-byte save: loads correctly, progress transposed
- No Hard/Time modes visible in Stage/Mode select
- `stage5` (Fantasy Island) unlocks after Stage 4

---

## Reports Created
1. `00_DESIGN_SUMMARY.md` — This document
2. `01_MINIMAL_FIX_DESIGN.md` — Alternative A detail
3. `02_MIGRATION_DESIGN.md` — Alternative B detail
4. `03_SIMPLE_PROGRESSION_DESIGN.md` — Alternative C detail (recommended)

**Status**: READY FOR REVIEW. No code changes made.