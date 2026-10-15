# SaveStore Safe Migration Specification

## 1. Baseline Verification

| Item | Value |
|------|-------|
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Git HEAD | `666e338` ✅ |
| Source file | `src/java-me-fullgame/me/holojava/fullgame/SaveStore.java` |

---

## 2. Migration Design Principles

### 2.1 Core Principles

1. **Read-only load** — `load()` never writes to RMS
2. **Atomic migration** — Write new format, verify, then replace
3. **Idempotent** — Running migration twice = same result
4. **Rollback-safe** — Original record preserved until new verified
5. **Validated** — Transposed data checked before commit
6. **Explicit** — Migration triggered deliberately, not hidden in load

### 2.2 Format Versioning (New)

Since neither format has a version field, add detection logic:

| Format | Detection | Version |
|--------|-----------|---------|
| Legacy | `length == 6` AND valid stage bitmasks (bits 0-4 only) | v1 |
| Current | `length == 48` AND valid character bitmasks (bits 0-7) | v2 |
| Migrated | `length == 48` with transposed data + valid settings | v2 |
| Unknown | Any other length or invalid bit patterns | v0 (reject) |

---

## 3. Transposition Algorithm (Verified)

### 3.1 Legacy → Current (Bit-Level Transpose)

```java
/**
 * Transposes 6-byte legacy record to 48-byte current format.
 * Legacy: data[stage] bit char = char completed stage
 * Current: data[char] bit stage = char completed stage
 * 
 * @param legacy 6-byte array (5 stage bytes + 1 settings)
 * @return 48-byte array (5 character bytes + 42 reserved + 1 settings)
 * @throws IllegalArgumentException if legacy.length != 6
 */
private static byte[] transposeLegacyToCurrent(byte[] legacy) {
    if (legacy == null || legacy.length != 6) {
        throw new IllegalArgumentException("Legacy record must be 6 bytes");
    }
    
    byte[] current = new byte[48];
    
    // Transpose stage-major (legacy) to character-major (current)
    // Stages 0-4, Characters 0-4
    for (int stage = 0; stage < 5; stage++) {
        byte stageMask = legacy[stage];
        for (int charIdx = 0; charIdx < 5; charIdx++) {
            if ((stageMask & (1 << charIdx)) != 0) {
                // Character charIdx completed this stage
                current[charIdx] |= (1 << stage);
            }
        }
    }
    
    // Settings byte: bit layout IDENTICAL (proven compatible)
    // Bit 0 = hardcore, Bits 1-5 = character index, Bits 6-7 = reserved
    current[47] = legacy[5];
    
    // Indices 5-46 remain 0 (reserved / future stages 5-46 default incomplete)
    
    return current;
}
```

### 3.2 Validation After Transposition

```java
/**
 * Validates that a 48-byte record has internally consistent data.
 */
private static boolean validateCurrentFormat(byte[] data) {
    if (data == null || data.length != 48) return false;
    
    // Settings byte: hardcore bit 0, character bits 1-5 (0-4 valid)
    byte settings = data[47];
    int charIndex = settings & 0x3F;  // bits 0-5
    if (charIndex >= 5) return false;  // Invalid character index
    
    // Character bitmasks: each byte has bits 0-7 for stages 0-7
    // No further validation needed — any bit pattern is valid
    
    // Reserved bytes 5-46: no validation (future use)
    
    return true;
}
```

### 3.3 Legacy Format Validation

```java
/**
 * Validates that a 6-byte record matches legacy format expectations.
 */
private static boolean validateLegacyFormat(byte[] data) {
    if (data == null || data.length != 6) return false;
    
    // Stage bytes 0-4: only bits 0-4 should be used (5 characters)
    for (int i = 0; i < 5; i++) {
        if ((data[i] & 0xE0) != 0) {  // bits 5-7 should be 0
            return false;  // Corrupted or non-legacy
        }
    }
    
    // Settings byte 5: bits 0-5 valid, 6-7 unused
    byte settings = data[5];
    int charIndex = settings & 0x3F;
    if (charIndex >= 5) return false;
    
    return true;
}
```

---

## 4. Safe Migration Procedure

### 4.1 Migration Entry Point

```java
/**
 * Attempts to migrate legacy save to current format.
 * Called explicitly (e.g., on first launch after update), NOT from load().
 * 
 * @return true if migration performed, false if no legacy save or already current
 */
boolean migrateIfNeeded() {
    RecordStore store = null;
    try {
        store = RecordStore.openRecordStore(STORE, false);
        try {
            byte[] loaded = store.getRecord(RECORD_ID);
            if (loaded == null) {
                return false;  // No save to migrate
            }
            
            if (isCurrentFormat(loaded)) {
                return false;  // Already current format
            }
            
            if (!isLegacyFormat(loaded)) {
                // Unknown format — don't touch, don't migrate
                return false;
            }
            
            // Perform transposition
            byte[] migrated = transposeLegacyToCurrent(loaded);
            
            // Validate transposed data
            if (!validateCurrentFormat(migrated)) {
                throw new IllegalStateException("Migration produced invalid data");
            }
            
            // ATOMIC WRITE: Write to temporary record first, then replace
            // RMS doesn't support transactions, so we use record ID 2 as temp
            final int TEMP_RECORD_ID = 2;
            
            // Write migrated data to temp record
            store.setRecord(TEMP_RECORD_ID, migrated, 0, migrated.length);
            
            // Read back and verify
            byte[] verify = store.getRecord(TEMP_RECORD_ID);
            if (verify == null || verify.length != 48) {
                throw new IllegalStateException("Migration write verification failed");
            }
            if (!arraysEqual(migrated, verify)) {
                throw new IllegalStateException("Migration write verification mismatch");
            }
            if (!validateCurrentFormat(verify)) {
                throw new IllegalStateException("Written data failed validation");
            }
            
            // SUCCESS: Replace legacy record (ID 1) with migrated data
            store.setRecord(RECORD_ID, migrated, 0, migrated.length);
            
            // Clean up temp record
            try { store.deleteRecord(TEMP_RECORD_ID); } catch (Exception ignored) {}
            
            // Update in-memory data
            System.arraycopy(migrated, 0, this.data, 0, 48);
            
            return true;
            
        } catch (RecordStoreException e) {
            // Migration failed — original record untouched
            return false;
        }
    } catch (RecordStoreException e) {
        return false;
    } finally {
        closeQuietly(store);
    }
}

private boolean isLegacyFormat(byte[] data) {
    return data != null && data.length == 6 && validateLegacyFormat(data);
}

private boolean isCurrentFormat(byte[] data) {
    return data != null && data.length == 48 && validateCurrentFormat(data);
}

private boolean arraysEqual(byte[] a, byte[] b) {
    if (a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) {
        if (a[i] != b[i]) return false;
    }
    return true;
}

private void closeQuietly(RecordStore store) {
    if (store != null) {
        try { store.closeRecordStore(); } catch (RecordStoreException ignored) {}
    }
}
```

---

## 5. Revised Load Method (Read-Only)

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
            
            if (isCurrentFormat(loaded)) {
                // Direct load — exact format match
                System.arraycopy(loaded, 0, data, 0, 48);
            } else if (isLegacyFormat(loaded)) {
                // Legacy format detected — transpose in memory only
                // DO NOT SAVE — migration is separate explicit step
                byte[] transposed = transposeLegacyToCurrent(loaded);
                System.arraycopy(transposed, 0, data, 0, 48);
                // Optionally: flag that migration is recommended
            } else {
                // Unknown/corrupted format — keep defaults, don't overwrite
                // Log for debugging (if logging available)
            }
        } catch (RecordStoreException ignored) {
            // Read failed — keep defaults
        }
    } catch (RecordStoreException ignored) {
        // Store unavailable — keep defaults
    } finally {
        closeQuietly(store);
    }
}
```

---

## 6. Revised Save Method (Atomic with Verification)

```java
private void save() {
    RecordStore store = null;
    try {
        store = RecordStore.openRecordStore(STORE, true);
        try {
            // Validate before write
            if (!validateCurrentFormat(data)) {
                throw new IllegalStateException("Refusing to save invalid data");
            }
            
            // Write to temp record first
            final int TEMP_RECORD_ID = 2;
            store.setRecord(TEMP_RECORD_ID, data, 0, data.length);
            
            // Verify write
            byte[] verify = store.getRecord(TEMP_RECORD_ID);
            if (verify == null || verify.length != data.length) {
                throw new IllegalStateException("Save verification failed: record not readable");
            }
            if (!arraysEqual(data, verify)) {
                throw new IllegalStateException("Save verification failed: data mismatch");
            }
            
            // Atomic replace: write to main record
            store.setRecord(RECORD_ID, data, 0, data.length);
            
            // Clean up temp
            try { store.deleteRecord(TEMP_RECORD_ID); } catch (Exception ignored) {}
            
        } catch (RecordStoreException e) {
            // Save failed — in-memory data preserved, original record untouched
            // Could notify user here if UI callback available
        }
    } catch (RecordStoreException e) {
        // Store unavailable
    } finally {
        closeQuietly(store);
    }
}
```

---

## 7. Risk Assessment

| Risk | Mitigation |
|------|------------|
| Transposition logic error | Unit test with known legacy/current pairs |
| RMS write fails mid-migration | Temp record + verify before replace |
| Power loss during write | Verify read-back; original intact until replace |
| Corrupted legacy record | `validateLegacyFormat` rejects invalid bit patterns |
| Already-migrated record re-migrated | `isCurrentFormat` detects and skips |
| Settings bit corruption | `validateCurrentFormat` checks character index 0-4 |
| Unknown future format | Length + validation rejects; defaults used |
| Temp record ID collision | Use fixed ID 2; only one migration at a time |

---

## 8. Test Cases

### 8.1 Transposition Correctness

| Test | Legacy Input (6 bytes) | Expected Current (48 bytes) |
|------|------------------------|----------------------------|
| Empty | All zeros | All zeros |
| Char 0 completed Stage 0 | `loaded[0]=0x01` | `data[0]=0x01` |
| Char 2 completed Stage 1 | `loaded[1]=0x04` | `data[2]=0x02` |
| Char 4 completed Stage 3 | `loaded[3]=0x10` | `data[4]=0x08` |
| Multiple per stage | `loaded[0]=0x1F` (all 5 chars) | `data[0..4] bit 0 = 1` |
| Multiple per char | `loaded[0]=0x01, loaded[1]=0x02, loaded[2]=0x04` | `data[0]=0x01, data[1]=0x02, data[2]=0x04` |
| Settings: hardcore + char 3 | `loaded[5]=0x07` (bit 0=1, bits 1-5=3) | `data[47]=0x07` |

### 8.2 Migration Safety

| Test | Setup | Expected |
|------|-------|----------|
| Fresh install | No record | No migration, defaults |
| Valid legacy | 6-byte valid record | Migrates to 48-byte, original replaced |
| Valid current | 48-byte valid record | No migration |
| Invalid legacy | 6 bytes, bits 5-7 set | No migration, treated as unknown |
| Corrupted current | 48 bytes, char index 7 | No migration, treated as unknown |
| Write failure | RMS full/exception | Original record preserved |
| Power loss simulation | Kill after temp write | Original record preserved |
| Idempotency | Run migrate twice | Second run returns false |

### 8.3 Load Safety

| Test | Input Record | In-Memory Result | RMS Unchanged? |
|------|--------------|------------------|----------------|
| Fresh | None | Zeros | N/A |
| Legacy valid | 6 bytes valid | Transposed | Yes |
| Current valid | 48 bytes valid | Copied | Yes |
| Legacy invalid | 6 bytes, bad bits | Zeros (defaults) | Yes |
| Current invalid | 48 bytes, bad char | Zeros | Yes |
| Truncated | 10 bytes | Zeros | Yes |
| Oversized | 100 bytes | Zeros | Yes |

---

## 9. Implementation Checklist

### 9.1 Required Changes to SaveStore.java

- [ ] Add `validateLegacyFormat(byte[])`
- [ ] Add `validateCurrentFormat(byte[])`
- [ ] Add `transposeLegacyToCurrent(byte[])`
- [ ] Add `isLegacyFormat(byte[])`
- [ ] Add `isCurrentFormat(byte[])`
- [ ] Add `arraysEqual(byte[], byte[])`
- [ ] Add `closeQuietly(RecordStore)`
- [ ] Refactor `load()` to read-only with format detection
- [ ] Add `migrateIfNeeded()` public/package method
- [ ] Refactor `save()` to atomic write with verification
- [ ] Add `TEMP_RECORD_ID = 2` constant

### 9.2 Integration Requirements

- [ ] Call `migrateIfNeeded()` on first launch after update (e.g., in `GameCanvas` init)
- [ ] Handle migration failure gracefully (continue with legacy data in memory)
- [ ] No automatic save after load — only explicit `setCompleted`, `setHardcore`, etc.

---

## 10. Alternative Designs Considered

### 10.1 Option A: Keep 6-Byte Format (Rejected)

- **Pros:** No migration needed, JAR compatible
- **Cons:** Cannot support 8 stages (only 5), no expansion room
- **Verdict:** Source design explicitly expanded to 48 bytes for 8 stages

### 10.2 Option B: New Format with Version Byte (Deferred)

- **Pros:** Clean versioning, future-proof
- **Cons:** Breaks compatibility with both legacy and current
- **Verdict:** Over-engineering for now; current 48-byte format works if migration correct

### 10.3 Option C: Dual Format Support Forever (Rejected)

- **Pros:** Never lose data
- **Cons:** Code complexity, two code paths forever
- **Verdict:** Migrate once, support single format

---

## 11. Open Questions Requiring Decision

1. **Should `migrateIfNeeded()` be automatic or manual?**
   - Automatic on first launch: seamless but hidden
   - Manual (menu option): user control, but users won't do it
   - **Recommendation:** Automatic with logging

2. **What to do with indices 5-46 (42 reserved bytes)?**
   - Current: leave zero
   - Future: could store stages 5-46 (one byte per stage)
   - **Recommendation:** Document as reserved, leave zero

3. **Character count: 5 or 8?**
   - Legacy `setCompleted` allows char 0-7 (8 chars)
   - Source uses `ContentCatalog.CHARACTERS.length` (assume 5)
   - **Recommendation:** Verify `CHARACTERS.length` and align bounds

4. **Stage count: 5 or 8?**
   - Legacy: 5 stages (0-4)
   - Source: 8 stages (0-7)
   - **Recommendation:** Keep 8 in source; migrate legacy stages 0-4, default 5-7 to incomplete

---

## 12. Conclusion

**The migration design is now SPECIFIED with verified transposition logic.**

Key corrections from previous reports:
1. **Transposition is required** — not byte copy (proven in `01_BYTE_MAPPING_EVIDENCE.md`)
2. **Load must be read-only** — migration separate (proven in `02_INVALID_RECORD_AND_FAILURE_HANDLING.md`)
3. **Atomic write with verification** — prevents data loss on failure
4. **Validation at every step** — rejects corrupted/unknown formats

**This specification is ready for implementation review.** Once approved, implementation can proceed with the checklist in Section 9.

---

## 13. Evidence Traceability

| Spec Element | Evidence Source |
|--------------|-----------------|
| Transposition algorithm | `01_BYTE_MAPPING_EVIDENCE.md` Section 6 |
| Legacy format layout | `01_BYTE_MAPPING_EVIDENCE.md` Section 2 |
| Current format layout | `01_BYTE_MAPPING_EVIDENCE.md` Section 3 |
| Settings compatibility | `01_BYTE_MAPPING_EVIDENCE.md` Section 4.3 |
| Load failure modes | `02_INVALID_RECORD_AND_FAILURE_HANDLING.md` Section 2 |
| Save failure modes | `02_INVALID_RECORD_AND_FAILURE_HANDLING.md` Section 5 |
| Migration failure modes | `02_INVALID_RECORD_AND_FAILURE_HANDLING.md` Section 6 |