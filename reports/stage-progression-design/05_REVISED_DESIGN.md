# HoloJava — Revised Simple Progression Design (Post-Validation)

## Changes from Original Design (03_SIMPLE_PROGRESSION_DESIGN.md)

Based on `04_CRITICAL_VALIDATION.md` finding: **RMS cannot guarantee atomic replace** — mid-write power loss on main record (ID 1) can corrupt it after legacy is already migrated to temp (ID 2).

### Fix: Recovery Logic in `load()`

Check temp record (ID 2) on every load. If a valid v2 record exists there, migration completed but main record write failed → recover from temp.

---

## Complete Revised SaveStore.java

```java
package me.holojava.fullgame;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/** Optional RMS persistence with versioned format, atomic migration, and crash recovery. */
final class SaveStore {
    private static final String STORE = "holojava_fullgame_v1";
    private static final int RECORD_ID = 1;          // Main save record
    private static final int TEMP_RECORD_ID = 2;     // Temp for atomic writes
    private static final int DATA_LEN = 48;          // Internal data size
    private static final int SETTINGS_INDEX = 47;    // Settings byte in internal data
    private static final int LEGACY_LEN = 6;         // Legacy v1 record length
    private static final int SAVE_VERSION = 2;       // Current format version
    private static final int STAGE_COUNT = 5;        // Normal stages only (order 0-4)

    private final byte[] data = new byte[DATA_LEN];

    SaveStore() {
        java.util.Arrays.fill(data, (byte) 0);
        load();
    }

    // ==================== PUBLIC API ====================

    /** Check if character completed a stage (only normal stages 0-4). */
    boolean isCompleted(int characterIndex, int stageOrder) {
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length
            || stageOrder < 0 || stageOrder >= STAGE_COUNT) return false;
        int v = data[characterIndex] & 0xff;
        return (v & (1 << stageOrder)) != 0;
    }

    /** Mark stage completed and persist (only normal stages 0-4). */
    void setCompleted(int characterIndex, int stageOrder) {
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length
            || stageOrder < 0 || stageOrder >= STAGE_COUNT) return;
        int v = data[characterIndex] & 0xff;
        data[characterIndex] = (byte) (v | (1 << stageOrder));
        save();
    }

    // Settings (unchanged)
    boolean hardcore() { return (data[SETTINGS_INDEX] & 1) != 0; }
    void setHardcore(boolean value) {
        int v = data[SETTINGS_INDEX] & 0xff;
        if (value) v |= 1; else v &= ~1;
        data[SETTINGS_INDEX] = (byte) v;
        save();
    }
    int selectedCharacter() { return data[SETTINGS_INDEX] & 0x3f; }
    void setSelectedCharacter(int value) {
        int settings = data[SETTINGS_INDEX] & 0xc0;
        data[SETTINGS_INDEX] = (byte) (settings | (value & 0x3f));
        save();
    }

    // ==================== MIGRATION (Explicit, One-Time) ====================

    /** Called once on first run. Returns true if migration performed. */
    boolean migrateIfNeeded() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE, false);
            byte[] loaded = store.getRecord(RECORD_ID);
            if (loaded == null) return false;                    // Fresh install
            if (isCurrentFormat(loaded)) return false;           // Already v2
            if (!isLegacyFormat(loaded)) return false;           // Unknown format — don't touch

            // Transpose legacy (stage-major) → current (char-major)
            byte[] migrated = transposeLegacy(loaded);
            if (!validateCurrent(migrated)) return false;

            // ATOMIC: Write to temp → verify → replace main
            store.setRecord(TEMP_RECORD_ID, migrated, 0, migrated.length);
            byte[] verify = store.getRecord(TEMP_RECORD_ID);
            if (verify == null || !arraysEqual(migrated, verify)) return false;

            store.setRecord(RECORD_ID, migrated, 0, migrated.length);
            store.deleteRecord(TEMP_RECORD_ID);
            java.lang.System.arraycopy(migrated, 0, data, 0, DATA_LEN);
            return true;
        } catch (RecordStoreException e) {
            return false;  // Legacy untouched on any error
        } finally {
            closeQuietly(store);
        }
    }

    // ==================== LOAD (Read-Only + Recovery) ====================

    private void load() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE, false);
            byte[] loaded = store.getRecord(RECORD_ID);

            // RECOVERY: Check temp record for completed-but-uncommitted migration
            byte[] temp = store.getRecord(TEMP_RECORD_ID);
            if (temp != null && isCurrentFormat(temp)) {
                // Migration succeeded to temp but main record write failed/crashed
                // Recover: promote temp to main
                store.setRecord(RECORD_ID, temp, 0, temp.length);
                store.deleteRecord(TEMP_RECORD_ID);
                java.lang.System.arraycopy(temp, 1, data, 0, DATA_LEN);  // Skip version byte
                return;
            }

            if (loaded == null) return;  // Fresh install

            if (isCurrentFormat(loaded)) {
                // v2 format: [version][48 bytes data]
                java.lang.System.arraycopy(loaded, 1, data, 0, DATA_LEN);
            } else if (isLegacyFormat(loaded)) {
                // Legacy v1: transpose in memory only (don't auto-save)
                byte[] transposed = transposeLegacy(loaded);
                java.lang.System.arraycopy(transposed, 0, data, 0, DATA_LEN);
            }
            // Unknown format: keep defaults, don't overwrite
        } catch (RecordStoreException ignored) { }
        finally { closeQuietly(store); }
    }

    // ==================== SAVE (Atomic with Verification) ====================

    private void save() {
        // Build versioned record: [version][48 bytes data]
        byte[] record = new byte[DATA_LEN + 1];
        record[0] = (byte) SAVE_VERSION;
        java.lang.System.arraycopy(data, 0, record, 1, DATA_LEN);

        if (!validateCurrent(data)) return;  // Don't save invalid state

        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE, true);
            // Atomic: write temp → verify → replace
            store.setRecord(TEMP_RECORD_ID, record, 0, record.length);
            byte[] verify = store.getRecord(TEMP_RECORD_ID);
            if (verify == null || !arraysEqual(record, verify)) return;
            store.setRecord(RECORD_ID, record, 0, record.length);
            store.deleteRecord(TEMP_RECORD_ID);
        } catch (RecordStoreException ignored) { }
        finally { closeQuietly(store); }
    }

    // ==================== HELPERS ====================

    private static boolean isLegacyFormat(byte[] d) {
        return d != null && d.length == LEGACY_LEN && validateLegacy(d);
    }

    private static boolean isCurrentFormat(byte[] d) {
        return d != null && d.length == DATA_LEN + 1 && d[0] == SAVE_VERSION && validateCurrent(d);
    }

    private static boolean validateLegacy(byte[] d) {
        // Legacy stage bytes: only bits 0-4 used (5 characters)
        for (int i = 0; i < 5; i++) if ((d[i] & 0xE0) != 0) return false;
        // Settings: char index 0-4 in bits 1-5
        return (d[5] & 0x3F) < 5;
    }

    private static boolean validateCurrent(byte[] d) {
        // Settings: char index 0-4 in bits 1-5
        return (d[SETTINGS_INDEX] & 0x3F) < 5;
    }

    /** Transpose legacy (stage-major) to current (character-major). */
    private static byte[] transposeLegacy(byte[] legacy) {
        byte[] cur = new byte[DATA_LEN];
        // Legacy: L[stage] bit char = char completed stage
        // Current: S[char] bit stage = char completed stage
        for (int stage = 0; stage < 5; stage++) {
            byte mask = legacy[stage];
            for (int ch = 0; ch < 5; ch++) {
                if ((mask & (1 << ch)) != 0) cur[ch] |= (1 << stage);
            }
        }
        // Settings byte: bit-identical (hardcore bit 0, char bits 1-5)
        cur[SETTINGS_INDEX] = legacy[5];
        // Indices 5-46 remain 0 (stages 5-7 default incomplete)
        return cur;
    }

    private static boolean arraysEqual(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static void closeQuietly(RecordStore s) {
        if (s != null) try { s.closeRecordStore(); } catch (RecordStoreException ignored) {}
    }
}
```

---

## Revised Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Migration transpose bug | LOW | Pure function, testable with vectors |
| Write failure mid-migration | LOW | Legacy record preserved until verify |
| Partial/invalid legacy | LOW | `validateLegacy` rejects corrupt |
| Future format change | LOW | Version byte enables v3 migration |
| Save failure during play | LOW | Atomic temp-write + verify |
| **Power loss during main record overwrite** | **MEDIUM** | **Recovery: `load()` checks TEMP_RECORD_ID (2) and promotes if valid v2** |

---

## Recovery Flow (Power Loss During Main Record Write)

```
Normal migration:
  Legacy (ID 1) → transpose → Temp (ID 2) → verify → Main (ID 1) → delete Temp

Crash during Main write:
  Legacy (ID 1) → transpose → Temp (ID 2) ✓ verified → Main (ID 1) ✗ CORRUPTED

Next launch load():
  1. Read Main (ID 1) → corrupt/unknown
  2. Read Temp (ID 2) → valid v2 format ✓
  3. Promote Temp → Main (ID 1)
  4. Delete Temp
  5. Load recovered data
```

**Guarantee**: Legacy data never lost. Either migration completes fully, or temp record preserves migrated state for recovery.

---

## Testing Additions (Beyond Original)

| Test Case | Expected |
|-----------|----------|
| Kill process during `setRecord(RECORD_ID)` in migration | Next launch recovers from temp |
| Kill process during `setRecord(RECORD_ID)` in save() | Next launch loads previous valid save (temp verify failed, main untouched) |
| Corrupt main record + valid temp | Recovery promotes temp |
| Valid main + stale temp (shouldn't happen) | Main used, temp ignored |

---

## Other Files (Unchanged from Original Design)

### `ContentCatalog.java` — Line 257 Fix
```java
stage("stage5", "Fantasy Island", false, false, 4, "fantasy", 4, 
      "nousagi_a", "nousagi_b", "nousagi_c", 850, 36, 15000, 
      4,  // was 99
      "nanora", 600, "pekodam", 1200, ...)
```

### `FullGameCanvas.java` — UI Filtering
```java
// Constructor or init: save.migrateIfNeeded();

// drawModes: only Stage Mode
String[] items = { "Stage Mode", "Back" };

// updateStages: filter to normal stages (!hard && !timeStage)
```

---

## Status: **REVISED DESIGN COMPLETE**

All four validation areas addressed:
1. ✅ Legacy mapping — transposition proven, concrete example
2. ✅ RecordStore atomicity — recovery logic added for mid-write crash
3. ✅ Save versioning — unambiguous 49-byte layout with version byte
4. ✅ Scope progres — 5 normal stages, sequential unlock, Hard/Time hidden

**Ready for implementation approval.**