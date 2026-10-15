# SaveStore Format Compatibility Audit — Compatibility Risk Matrix

## 1. Compatibility Scenarios

### 1.1 Scenario Matrix

| Scenario | Current Source (48-byte) | Reference JAR (6-byte) | Result |
|----------|--------------------------|------------------------|--------|
| Fresh install (no save) | Creates 48-byte record | Creates 6-byte record | ✅ Both work |
| Load legacy 6-byte record | **CRASH** (`ArrayIndexOutOfBoundsException`) | Works natively | **INCOMPATIBLE** |
| Load 48-byte record (from current source) | Works | Truncates to 6 bytes | **DATA LOSS** |
| Save after loading legacy | Writes 48 bytes | Writes 6 bytes | **FORMAT DIVERGENCE** |

### 1.2 Compatibility Matrix

| Source Version | Record Format | Load Result | Save Result | Risk |
|----------------|---------------|-------------|-------------|------|
| Current (48B) | 6-byte (legacy) | **CRASH** (`data[47]` on 6-byte array) | Writes 48B | **CRITICAL** |
| Current (48B) | 48-byte (self) | OK | 48 bytes | ✅ OK |
| Ref JAR (6B) | 6-byte (legacy) | OK | 6 bytes | ✅ OK |
| Current (48B) | 48-byte (self) | OK | 48 bytes | OK |
| Ref JAR (6B) | 48-byte | Truncates to 6B | 6 bytes | Data loss |

## 2. Risk Matrix

| Risk ID | Scenario | Severity | Likelihood | Impact | Detection |
|---------|----------|----------|------------|--------|-----------|
| RISK-001 | Load 6-byte record with 48-byte source | **CRITICAL** | High (existing users) | Crash on startup (`ArrayIndexOutOfBoundsException` at `data[47]`) | Startup crash |
| RISK-002 | Save 48-byte record, read with 6-byte impl | High | Medium | Silent data loss (truncation to 6 bytes) | Silent corruption |
| RISK-003 | Settings corruption | High | Medium | Hardcore/character reset | Corrupted save |
| RISK-004 | Stage progress loss | High | Medium | Stages 5-7 lost | Lost progress |
| RISK-005 | Settings corruption | High | Medium | Hardcore/character reset | Corrupted save |
| RISK-006 | First run (no save) | None | — | Fresh 48-byte record | None |

### Risk Severity Definitions
- **CRITICAL**: Application crash, data unrecoverable
- **HIGH**: Silent data corruption, progress loss
- **MEDIUM**: Recoverable with user action
- **LOW**: Cosmetic or negligible

## 3. Detailed Failure Scenarios

### 3.1 Scenario: Existing User Updates to Current Source

**Precondition**: User has existing 6-byte save from reference JAR
**Action**: User updates to current source (48-byte) and launches game
**Execution Path**:
1. `SaveStore()` constructor allocates `new byte[48]`
2. `load()` called, reads 6-byte record from RMS
3. `loaded.length == LEGACY_LEN (6)` → legacy path taken
4. Loop: `for (i=0; i<5; i++) data[i] = loaded[i]` — OK
5. `data[SETTINGS_INDEX] = loaded[5]` → **`data[47] = loaded[5]`**
6. **CRASH**: `ArrayIndexOutOfBoundsException` (array length 6, index 47)

**Impact**: **Immediate crash on startup** for all existing users upgrading.

### 3.2 Scenario: Existing 48-byte Save Read by Reference JAR

**Precondition**: User has 48-byte save from current source
**Action**: User runs reference JAR (6-byte impl)
**Execution**:
1. JAR loads 48-byte record
2. `loaded.length (48) != LEGACY_LEN (6)` → modern path
3. Copies `min(48, 6) = 6` bytes to `data[0..5]`
4. **Data loss**: Stages 5-7 lost, settings byte at index 47 not read
4. Game runs with truncated data

**Impact**: Silent data loss — stages 5-7 progress lost, settings reset.

### 3.3 Scenario: Fresh Install with Current Source

**Precondition**: No existing save
**Result**: Creates new 48-byte record, works correctly
**Risk**: None

## 4. Compatibility Matrix

| Source Version | Record Format | Load Result | Save Result | Status |
|----------------|---------------|-------------|-------------|--------|
| Current (48B) | 6-byte (legacy) | **CRASH** | 48B | **CRITICAL** |
| Current (48B) | 48-byte (self) | OK | 48B | ✅ OK |
| Ref JAR (6B) | 6-byte (legacy) | OK | 6B | ✅ OK |
| Current (48B) | 48-byte (self) | OK | 48B | ✅ OK |
| Ref JAR (6B) | 48-byte | Truncates to 6B | 6B | Data loss |

## 4. Migration Paths

### 4.1 Option A: Rebuild Reference JAR from Current Source
- **Action**: Build JAR from current source (`DATA_LEN=48`)
- **Pros**: Full compatibility, all features work
- **Cons**: Breaks compatibility with existing 6-byte saves
- **Migration needed**: Yes, for existing 6-byte saves

### 4.2 Option B: Add Migration Logic to Current Source
```java
private void load() {
    byte[] loaded = store.getRecord(RECORD_ID);
    if (loaded != null) {
        if (loaded.length == 6) {
            // Legacy 6-byte → migrate to 48
            for (int i = 0; i < 5; i++) data[i] = loaded[i];
            data[47] = loaded[5];  // settings byte
            // bytes 5-46 remain 0 (new stages)
            save(); // Save migrated 48-byte format
        } else if (loaded.length >= 48) {
            // Modern 48-byte format
            System.arraycopy(loaded, 0, data, 0, 48);
        } else {
            // Partial/corrupted — safe fallback
            int count = Math.min(loaded.length, 48);
            System.arraycopy(loaded, 0, data, 0, count);
        }
    }
}
```

### 4.3 Option C: Dual Format Support
- Detect record length on load
- Migrate legacy → 48 on first load
- Always write 48-byte format

## 5. Migration Risk Assessment

| Migration Strategy | Risk | Effort | User Impact |
|--------------------|------|--------|-------------|
| Rebuild JAR only | **HIGH** (breaks existing saves) | Low | All existing users lose progress |
| Add migration logic | **LOW** | Medium | Seamless for users |
| Dual format support | **LOW** | Medium | Seamless |

## 6. Recommended Action Plan

### Immediate (Before Deploying Current Source)
1. **Implement migration logic in `load()`** — Handle 6-byte → 48 upgrade
2. **Add bounds check** — Guard `SETTINGS_INDEX` access
2. **Test with legacy saves** — Verify migration works
3. **Rebuild JAR** — Deploy with migration logic

### 6.2 Verification Checklist

| Test Case | Expected | Verified |
|-----------|----------|----------|
| Fresh install | Creates 48-byte record | [ ] |
| Load 6-byte legacy | Migrates to 48-byte, no crash | [ ] |
| Load 48-byte | Reads correctly | [ ] |
| Save after load | Writes 48 bytes | [ ] |
| Settings persistence | Hardcore/char saved | [ ] |
| Stage completion | Stages 0-7 tracked | [ ] |

## 7. Final Recommendation

**Immediate Action Required**: Implement migration logic in `SaveStore.load()` before deploying current source to users with existing saves.

**Priority**: CRITICAL — Existing users will crash on startup without migration.

**Effort**: ~2 hours (add migration logic, test, rebuild JAR)

**Risk if Not Fixed**: 100% crash rate for existing users upgrading to new version.

## 6. Evidence Summary

| Evidence | Source | Status |
|----------|--------|--------|
| JAR bytecode: 6-byte array | `javap -c -p` | ✅ Confirmed |
| Source: `DATA_LEN=48` | Source code | ✅ Confirmed |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | ✅ Verified |
| Build verification | `build_fullgame.sh` + `verify_fullgame_build.py` | PASS |

---

**Final Recommendation**: **DO NOT DEPLOY** current source without adding 6-byte → 48-byte migration logic to `SaveStore.load()`. The format mismatch will cause immediate crashes for all existing users.

---

**Audit Status**: Complete. All evidence documented. Ready for decision.
