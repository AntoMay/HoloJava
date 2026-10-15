# HoloJava — Alternative B: Compatible Save Migration Design

## Scope
Fix progression + implement correct legacy→current transpose migration. No versioning, single format.

---

## Changes Required

### 1. `SaveStore.java` — Add Migration Logic

```java
// New constants
private static final int STAGE_COUNT = 5;  // Normal stages only

// New method: transpose legacy (stage-major) to current (char-major)
private static byte[] transposeLegacy(byte[] legacy) {
    byte[] current = new byte[DATA_LEN];  // 48 bytes
    // Legacy: data[stage] bit char = completed
    // Current: data[char] bit stage = completed
    for (int stage = 0; stage < 5; stage++) {
        byte mask = legacy[stage];
        for (int ch = 0; ch < 5; ch++) {
            if ((mask & (1 << ch)) != 0) {
                current[ch] |= (1 << stage);
            }
        }
    }
    // Settings byte compatible (bit 0=hardcore, 1-5=char)
    current[SETTINGS_INDEX] = legacy[5];
    return current;
}

// Modified load() with migration
private void load() {
    RecordStore store = null;
    try {
        store = RecordStore.openRecordStore(STORE, false);
        try {
            byte[] loaded = store.getRecord(RECORD_ID);
            if (loaded != null) {
                if (loaded.length == LEGACY_LEN) {
                    // MIGRATE: transpose + write new format
                    byte[] migrated = transposeLegacy(loaded);
                    System.arraycopy(migrated, 0, data, 0, DATA_LEN);
                    save();  // Overwrite with 48-byte format
                } else if (loaded.length >= DATA_LEN) {
                    System.arraycopy(loaded, 0, data, 0, DATA_LEN);
                } else {
                    // Partial: copy what we have
                    System.arraycopy(loaded, 0, data, 0, loaded.length);
                }
            }
        } catch (RecordStoreException ignored) { }
    } catch (RecordStoreException ignored) { }
    finally { closeQuietly(store); }
}

// Clamp stageOrder to STAGE_COUNT-1 (4)
boolean isCompleted(int characterIndex, int stageOrder) {
    if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length 
        || stageOrder < 0 || stageOrder >= STAGE_COUNT) return false;
    // ...
}

void setCompleted(int characterIndex, int stageOrder) {
    if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length 
        || stageOrder < 0 || stageOrder >= STAGE_COUNT) return;
    // ...
}
```

### 2. `ContentCatalog.java` — Fix Fantasy Island
```java
// Line 257: unlockRequirement 99 → 4
```

### 3. `FullGameCanvas.java` — UI Filtering
```java
// drawModes: hide Endless/Time
String[] items = { "Stage Mode", "Back" };

// updateStages: filter to normal stages only (STAGES array indices 0-4)
private void updateStages(...) {
    // Only show stages with !s.hard && !s.timeStage
}
```

---

## Migration Behavior

| Input Record | Action |
|--------------|--------|
| `null` | Fresh — defaults |
| 6 bytes (legacy) | **Transpose → write 48-byte → use** |
| 48 bytes (current) | Use directly |
| 7-47 bytes | Partial copy (settings lost) |
| >48 bytes | Truncate to 48 |

---

## Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Migration transpose bug | **MEDIUM** | Unit test with known vectors |
| `save()` fails mid-migration | **HIGH** | Legacy overwritten — **no rollback** |
| Partial legacy (1-5 bytes) | **MEDIUM** | Treated as current → settings lost |
| No versioning — future changes | **MEDIUM** | Will need re-migration |

---

## Critical Flaw: No Rollback

```java
// Current migration in load():
byte[] migrated = transposeLegacy(loaded);
System.arraycopy(migrated, 0, data, 0, DATA_LEN);
save();  // If this fails: legacy record GONE, replaced with partial
```

**If RMS write fails after transpose but before complete**: Original 6-byte save lost forever.

---

## Testing Required

| Test Case | Expected |
|-----------|----------|
| Fresh install | Pass |
| Sequential Stage 1-5 unlock | Pass |
| Restart persistence | Pass |
| Legacy 6-byte (known pattern) → correct transpose | Pass |
| Legacy 6-byte → migration write fails | **Data loss** |
| Current 48-byte → load correct | Pass |
| Save failure during gameplay | Silent loss (unchanged) |

---

## Verdict

**Better than A** — fixes legacy compatibility for the happy path.  
**Not recommended** — lacks atomic migration, no rollback, no versioning for future changes.  
Use only if versioned format (Alternative C) is deemed over-engineering.