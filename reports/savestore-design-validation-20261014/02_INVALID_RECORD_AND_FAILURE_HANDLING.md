# SaveStore Invalid Record & Failure Handling Analysis

## 1. Baseline Verification

| Item | Value |
|------|-------|
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Git HEAD | `666e338` ✅ |
| Source file | `src/java-me-fullgame/me/holojava/fullgame/SaveStore.java` |

---

## 2. Current Source Load Path Analysis

### 2.1 Load Method (Source Lines 52-75)

```java
private void load() {
    RecordStore store = null;
    try {
        store = RecordStore.openRecordStore(STORE, false);
        try {
            byte[] loaded = store.getRecord(RECORD_ID);
            if (loaded != null) {
                if (loaded.length == LEGACY_LEN) {  // LEGACY_LEN = 6
                    for (int i = 0; i < 5; i++) data[i] = loaded[i];
                    data[SETTINGS_INDEX] = loaded[5];  // SETTINGS_INDEX = 47
                } else {
                    int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;
                    for (int i = 0; i < count; i++) data[i] = loaded[i];
                }
            }
        } catch (RecordStoreException ignored) { }
    } catch (RecordStoreException ignored) {
        // Defaults remain valid when RMS is unavailable.
    } finally {
        if (store != null) {
            try { store.closeRecordStore(); } catch (RecordStoreException ignored) { }
        }
    }
}
```

### 2.2 Behavior by Input Record Length

| Record Length | Path Taken | Result |
|---------------|------------|--------|
| `null` | `loaded != null` false | Defaults (all zeros) — **SAFE** |
| `0` (empty) | `else` branch, `count = 0` | No copy, all zeros — **SAFE but loses settings** |
| `1-5` | `else` branch, `count = length` | Partial copy (indices 0..length-1), rest zeros — **CORRUPTS** |
| `6` (legacy) | `if (loaded.length == 6)` | Copies 0-4, settings to 47 — **SEMANTICALLY WRONG (transposition)** |
| `7-47` | `else` branch, `count = length` | Partial copy, `data[47]` stays 0 — **LOSES SETTINGS** |
| `48` | `else` branch, `count = 48` | Full copy — **CORRECT for self-format** |
| `>48` | `else` branch, `count = 48` | Truncates to 48 bytes — **SAFE truncation** |

---

## 3. Failure Mode Analysis

### 3.1 Critical Failure: 6-Byte Legacy Load (Semantic Corruption)

**Current behavior:** Copies bytes 0-4 as character bitmasks (should be stage bitmasks), copies byte 5 to settings.

**Result:** All stage completion data transposed incorrectly. Settings preserved.

**Severity:** **HIGH** — Silent data corruption, progress scrambled.

### 3.2 Critical Failure: 7-47 Byte Records (Settings Loss)

**Current behavior:** Copies available bytes, but `data[47]` (settings) remains 0 because `count < 48`.

**Result:** Hardcore mode lost, selected character reset to 0.

**Severity:** **HIGH** — Settings reset on load.

### 3.3 Critical Failure: 1-5 Byte Records (Partial Corruption)

**Current behavior:** Copies 1-5 bytes to `data[0..length-1]`.

**Result:** Partial stage/character data, settings lost, rest zeros.

**Severity:** **MEDIUM** — Unlikely in practice but possible with corruption.

### 3.4 RMS Exception Handling

**Current behavior:** All `RecordStoreException` caught and ignored.

```java
try {
    store = RecordStore.openRecordStore(STORE, false);
    try {
        byte[] loaded = store.getRecord(RECORD_ID);
        // ... process loaded ...
    } catch (RecordStoreException ignored) { }
} catch (RecordStoreException ignored) { }
```

**Issues:**
- `openRecordStore` fails → defaults used (OK)
- `getRecord` fails → defaults used (OK)
- `setRecord` in `save()` fails → silently ignored (**DATA LOSS**)

**Severity:** **HIGH** for save failures — user thinks progress saved but it isn't.

---

## 4. Reference JAR (Bytecode) Load Path Analysis

### 4.1 Bytecode Load Logic

From bytecode evidence (`02_REFERENCE_BYTECODE_LAYOUT.md`):

**Legacy detection:**
```bytecode
aload_2 (loaded)
arraylength
bipush 6
if_icmpge 31  → if loaded.length >= 6, go to modern path
```

**Legacy path (length == 6):**
- Loop `i < 5`: `data[i] = loaded[i]` (copies 5 stage bytes)
- `data[5] = loaded[5]` (settings at index 5)

**Modern path (length > 6):**
- Copies `min(loaded.length, 6)` bytes to `data[0..5]`
- Only first 6 bytes used; rest ignored

### 4.2 Behavior by Input Record Length

| Record Length | Path | Result |
|---------------|------|--------|
| `null` | Exception caught | Defaults (zeros) — **SAFE** |
| `0-5` | Modern path (len < 6 not legacy) | Partial copy to 6-byte array — **PARTIAL** |
| `6` | Legacy path | Correct native load — **CORRECT** |
| `7-47` | Modern path | First 6 bytes copied — **TRUNCATES** |
| `48` | Modern path | First 6 bytes copied — **TRUNCATES (loses stages 5-7, settings)** |
| `>48` | Modern path | First 6 bytes copied — **TRUNCATES** |

---

## 5. Save Method Failure Analysis

### 5.1 Current Source Save (Lines 77-91)

```java
private void save() {
    RecordStore store = null;
    try {
        store = RecordStore.openRecordStore(STORE, true);  // createIfNecessary = true
        try { store.setRecord(RECORD_ID, data, 0, data.length); }
        catch (RecordStoreException ignored) { }
    } catch (RecordStoreException ignored) { }
    finally { /* close */ }
}
```

**Issues:**
1. **No atomic write** — `setRecord` overwrites in place. If power fails mid-write, record corrupted.
2. **No verification** — No read-back to confirm write succeeded.
3. **Silent failure** — Exception caught and ignored. User unaware save failed.
4. **No backup/rollback** — Original record overwritten before knowing if write succeeds.

### 5.2 Reference JAR Save

Bytecode: `setRecord(1, data, 0, data.length)` — same issues, but writes 6 bytes.

---

## 6. Migration-Specific Failure Scenarios

### 6.1 Proposed Migration (Unsafe Version)

```java
if (loaded.length == 6) {
    for (int i = 0; i < 5; i++) data[i] = loaded[i];
    data[47] = loaded[5];
    save();  // DANGEROUS: overwrites legacy record immediately
}
```

**Failure modes:**
1. `save()` throws → legacy record overwritten with partial 48-byte data → **ORIGINAL LOST**
2. Power loss during `save()` → RMS record corrupted → **DATA LOST**
3. `save()` succeeds but data wrong (transposition bug) → **CORRUPTED DATA SAVED**

### 6.2 Safe Migration Requirements

| Requirement | Current Source | Proposed Migration | Needed |
|-------------|----------------|-------------------|--------|
| Atomic write | ❌ | ❌ | Write to temp, verify, then replace |
| Rollback on failure | ❌ | ❌ | Keep original until new verified |
| Idempotency | ❌ | ❌ | Detect already-migrated records |
| Validation before write | ❌ | ❌ | Verify transposed data correct |
| Settings preservation | ⚠️ (wrong index) | ✅ (byte copy) | Verify bit layout |

---

## 7. Record Format Detection Reliability

### 7.1 Current Detection: Record Length Only

| Format | Length | Detected As |
|--------|--------|-------------|
| Legacy | 6 | Legacy ✅ |
| Current | 48 | Modern ✅ |
| Corrupted legacy | 1-5 | Modern (partial) ❌ |
| Corrupted current | 7-47 | Modern (partial) ❌ |
| Future format | ? | Unknown ❌ |

**Problem:** No magic number, version field, or checksum. Length alone is fragile.

### 7.2 Recommended Detection Enhancement

```java
// Add format version byte at known offset
// Or: use checksum of known fields
// Or: validate transposition consistency (e.g., settings byte bit pattern valid)
```

---

## 8. Safe Load Algorithm Design

### 8.1 Principles

1. **Never overwrite original record on load** — load is read-only
2. **Validate before using** — Check bounds, magic, checksum
3. **Graceful degradation** — Unknown format → defaults, don't crash
4. **Explicit migration** — Separate migration step, not hidden in load

### 8.2 Pseudocode for Safe Load

```java
private void load() {
    RecordStore store = null;
    try {
        store = RecordStore.openRecordStore(STORE, false);
        try {
            byte[] loaded = store.getRecord(RECORD_ID);
            if (loaded == null) {
                // Fresh install — defaults already zeroed
                return;
            }
            
            if (isLegacyFormat(loaded)) {
                migrateLegacy(loaded);  // Transpose + validate
            } else if (isCurrentFormat(loaded)) {
                loadCurrentFormat(loaded);
            } else if (isUnknownButRecoverable(loaded)) {
                partialLoad(loaded);
            } else {
                // Unknown/corrupted — keep defaults, log warning
                // DO NOT save defaults over unknown record
            }
        } catch (RecordStoreException e) {
            // RMS error — keep defaults, don't overwrite
        }
    } catch (RecordStoreException e) {
        // Store unavailable — keep defaults
    } finally {
        closeQuietly(store);
    }
}

// Migration is SEPARATE from load — called explicitly or on first run
private void migrateLegacy(byte[] loaded) {
    // 1. Transpose to internal 48-byte format
    // 2. Validate transposed data (check settings bits valid)
    // 3. Write to NEW record (or same ID after verification)
    // 4. Only overwrite after successful write + read-back verify
}
```

---

## 9. Test Cases for Failure Handling

| Test Case | Input | Expected Behavior |
|-----------|-------|-------------------|
| Fresh install | No record | Defaults (zeros), no crash |
| Valid legacy | 6 bytes, valid bits | Transposed load, settings preserved |
| Valid current | 48 bytes | Direct load |
| Truncated legacy | 1-5 bytes | Defaults or partial (documented) |
| Truncated current | 7-47 bytes | Defaults or partial, settings lost |
| Oversized | >48 bytes | Truncate to 48, load |
| Null record | `getRecord` returns null | Defaults |
| RMS unavailable | `openRecordStore` throws | Defaults |
| Save fails | `setRecord` throws | No crash, progress not saved (user warned?) |
| Corrupted settings | Valid length, invalid bits | Clamp/validate, don't crash |
| Already migrated | 48 bytes with valid transpose | Load directly, don't re-migrate |

---

## 10. Evidence Classification

| Finding | Evidence Type | Status |
|---------|---------------|--------|
| Load handles null safely | Source code | ✅ Verified |
| Load corrupts 6-byte legacy | Source code + transposition proof | ✅ Proven |
| Load loses settings for 7-47 bytes | Source code | ✅ Verified |
| Save swallows exceptions | Source code | ✅ Verified |
| Save not atomic | Source code + RMS spec | ✅ Verified |
| No format version/magic | Source + bytecode | ✅ Verified |
| Detection by length only | Source code | ✅ Verified |
| Migration overwrites in-place | Proposed code | ✅ Verified |

---

## 11. Conclusion

**The current load/save implementation has multiple critical failure modes:**

1. **Silent data corruption** on legacy load (transposition bug)
2. **Settings loss** for any record not exactly 6 or 48 bytes
3. **Silent save failures** — user unaware progress not persisted
4. **No atomicity/rollback** — migration or save can lose original data
5. **Fragile format detection** — length-only, no versioning

**Any migration implementation must address all five issues.** The load method should be read-only; migration should be a separate, atomic, verifiable operation.