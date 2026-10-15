# Legacy Save Migration — Implementation Report (Tahap 3)

## 1. Baseline

| Item | Value |
|------|-------|
| Checkpoint | `6acdd6abaa5b2cf6830c7a3e2790f7ff6cc2aced` |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Audit reference | `reports/legacy-save-compat-audit/00_LEGACY_SAVE_COMPAT_REPORT.md` |
| **Commit made** | **No** — changes left in the working tree for review |

---

## 2. Byte/Bit Mapping — Before and After

### 2.1 Layout confirmation (from bytecode, not variable names)

**Reference JAR** `SaveStore.isCompleted` — parameter order is reversed relative to the current source:

```
  0: iload_1                     ← FIRST parameter = stageOrder
  5: iconst_5; if_icmplt 11      ← stageOrder must be 0..4
 15: iload_1                     ← ARRAY INDEX = stageOrder
 16: baload                      ← data[stageOrder]
 24: iload_2                     ← SECOND parameter = characterIndex
 25: ishl                        ← (1 << characterIndex)
```

**Current source** `SaveStore.java:23-27`:

```java
int v = data[characterIndex] & 0xff;
return (v & (1 << stageOrder)) != 0;
```

| | Array index | Bit position | Stage bound | Record size |
|---|---|---|---|---|
| Legacy (JAR) | `stageOrder` | `characterIndex` | 0–4 | 6 |
| This build | `characterIndex` | `stageOrder` | 0–7 | 48 |

### 2.2 Migration rule

```
legacy[s] bit c  == 1   →   current[c] bit s == 1
```

### 2.3 Worked example

| Legacy byte | Binary | Legacy meaning | Destination | Source meaning |
|-------------|--------|----------------|-------------|----------------|
| `L[0]` | `0b00000001` | Char 0, Stage 0 | `data[0]` bit 0 | Char 0, Stage 0 ✅ |
| `L[1]` | `0b00000100` | Char 2, Stage 1 | `data[2]` bit 1 | Char 2, Stage 1 ✅ |
| `L[3]` | `0b00010000` | Char 4, Stage 3 | `data[4]` bit 3 | Char 4, Stage 3 ✅ |
| `L[5]` | `0b00000001` | hardcore on | `data[47]` | hardcore on ✅ |

Before this change all three completion bytes were copied to the wrong character index.

### 2.4 Settings mapping — confirmed, not assumed

| | Legacy (JAR) | This build |
|---|---|---|
| Settings index | `iconst_5` → `data[5]` | `SETTINGS_INDEX = 47` |
| `hardcore()` | `data[5] & 1` | `data[47] & 1` |
| `selectedCharacter()` | `data[5] & 15` (0x0F, bits 0-3) | `data[47] & 0x3F` (bits 0-5) |
| `setSelectedCharacter` | `(data[5] & 240) \| (v & 15)` | `(data[47] & 0xC0) \| (v & 0x3F)` |

Hardcore sits in **bit 0 in both**. The legacy writer only ever set the character index in bits 0-3, so bits 4-5 of any JAR-written record are 0 and this build's 6-bit field is a **superset**.

**Therefore `data[47] = legacy[5]` verbatim is correct.** No bit is normalised, masked, or discarded — the value is copied unchanged, including the unused bits 6-7, which the source's own `setSelectedCharacter` preserves via `& 0xC0`.

---

## 3. Files Changed

| File | Change | Lines |
|------|--------|-------|
| `src/java-me-fullgame/me/holojava/fullgame/SaveStore.java` | 3 constants, legacy branch, new pure helper | +42 / −2 |
| `tools/tests/me/holojava/fullgame/SaveStoreLegacyMigrationTest.java` | **new** — unit test harness | 176 |

Tracked diff: **1 file, 42 insertions, 2 deletions**. The test is untracked and lives outside `src/`, so the game build never compiles or packages it (verified: 0 `Test` entries in the JAR; `tools/build_fullgame.sh:12` compiles only `src/java-me-fullgame`).

### 3.1 Code change detail

```java
// load(), legacy branch
- for (int i = 0; i < 5; i++) data[i] = loaded[i];
- data[SETTINGS_INDEX] = loaded[5];
+ byte[] migrated = transposeLegacyRecord(loaded);
+ for (int i = 0; i < DATA_LEN; i++) data[i] = migrated[i];
```

```java
// new pure static helper — no RMS, no instance state
static byte[] transposeLegacyRecord(byte[] legacy) {
    if (legacy == null || legacy.length != LEGACY_LEN)
        throw new IllegalArgumentException(...);
    byte[] current = new byte[DATA_LEN];
    for (int stage = 0; stage < LEGACY_STAGES; stage++) {
        int stageMask = legacy[stage] & 0xff;
        for (int character = 0; character < LEGACY_CHARS; character++)
            if ((stageMask & (1 << character)) != 0) current[character] |= (1 << stage);
    }
    current[SETTINGS_INDEX] = legacy[LEGACY_SETTINGS_INDEX];
    return current;
}
```

`LEGACY_STAGES = 5`, `LEGACY_CHARS = 5`, `LEGACY_SETTINGS_INDEX = 5` were added as named constants so the mapping is explicit rather than magic numbers.

---

## 4. Test Results

Compiled against the real build output plus the MIDP/CLDC stubs; executed on a desktop JVM. **No MIDlet, emulator, or device.** The test never constructs `SaveStore` and never touches `javax.microedition.rms`.

```
[1] basic transposition
[2] full 5x5 matrix
[3] bitmask edges
[4] settings byte
[5] idempotency and length separation
[6/7] non-legacy record lengths
checks=229 failures=0
RESULT: PASS
```

| # | Requirement | Coverage |
|---|-------------|----------|
| 1 | Basic transposition | Every bit reaches the correct destination index |
| 2 | Full matrix | All 25 valid `(character, stage)` pairs, each in isolation, plus the all-bits-set case |
| 3 | Bitmask edges | Empty record; `0xe1` (bits 0,5,6,7); a lone bit-5; reserved bytes 5-46 never written |
| 4 | Settings | 8 byte values incl. `0x00`/`0x01`/`0x0f`/`0xff`; hardcore bit preserved; no bleed into character bytes |
| 5 | Idempotency | Input array unmodified; repeated migration deterministic; 48-byte and 7-byte inputs rejected by the legacy path |
| 6 | No RMS write in `load` | Verified statically — see §5 |
| 7 | Record lengths | 0, 1, 7, 47, 48, 49, 200 |
| 8 | RMS failure | Contract unchanged — see §6 |

### 4.1 Bit policy outside the supported range (documents audit P3)

Legacy stage bytes may legally carry bits 5-7, because the JAR's `setCompleted` bounded `characterIndex` at `<= 7`. Characters 5-7 **do not exist** in this 5-character roster and have no destination byte.

**Policy: bits 5-7 are dropped; bits 0-4 are fully preserved.** This is the only representable choice — not a silent discard. Tests `[3]` assert it explicitly.

---

## 5. Static Verification — No Write During Load

| Check | Method | Result |
|-------|--------|--------|
| `load()` source contains `setRecord` | grep | **0** ✅ |
| Compiled `load()` invokes `RecordStore.setRecord` | `javap` | **0** ✅ |
| Legacy branch dispatches on length 6 | `javap` | `bipush 6; if_icmpne 61` ✅ |
| Legacy branch calls the helper | `javap` | `invokestatic transposeLegacyRecord:([B)[B` ✅ |
| `save()` still writes the full payload | `javap` | `arraylength; setRecord(I[BII)V` ✅ |
| `DATA_LEN` still 48 | `javap` | `bipush 48` ×2 ✅ |

The 6-byte record therefore stays on disk until the next real gameplay save — no temp record, no read-back verification, and no reliance on non-atomic replace.

---

## 6. Behaviour of Non-6-Byte Records — Unchanged

| Record length | Branch | Behaviour |
|---------------|--------|-----------|
| `null` | `if (loaded != null)` | defaults |
| 0-5 | `else` | partial copy, `count = min(len, 48)` |
| **6** | legacy branch | **transposed** (changed) |
| 7-47 | `else` | partial copy, `data[47]` stays 0 → settings lost (unchanged) |
| 48 | `else` | full copy (unchanged) |
| >48 | `else` | truncated to 48 (unchanged) |
| RMS open/get throws | `catch (RecordStoreException ignored)` | defaults kept — **contract untouched** |

No scope was silently widened.

---

## 7. Build Verification

| Check | Result |
|-------|--------|
| `./tools/build_fullgame.sh` | ✅ **PASS** — JAR 510758 bytes |
| `python3 tools/verify_fullgame_build.py` | ✅ **PASS** — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ clean |
| Unit test | ✅ **PASS** — 229 checks, 0 failures |
| Test excluded from game JAR | ✅ 0 `Test` entries |
| CLDC 1.1 / MIDP 2.0 compatible | ✅ `new byte[]`, `IllegalArgumentException`, enhanced-for-free loops — all CLDC 1.1 |
| Reference JAR SHA-256 | ✅ unchanged |
| External dependencies added | ✅ none |

---

## 8. Remaining Unverified Risk

| # | Risk |
|---|--------|
| U1 | Whether any real installation actually holds a 6-byte record. No save data available |
| U2 | On-device RMS behaviour — record size limits, `setRecord` atomicity, failure timing. Needs a device or emulator |
| U3 | Whether the JAR ever produced bits 5-7 in practice. The JAR canvas bounded `characterIndex` to 5, so probably not — but not provable statically. Policy is documented in §4.1 either way |
| U4 | **A 6-byte length alone proves nothing about validity.** This migration only re-interprets a known layout; it does not validate. A corrupted 6-byte record will be transposed deterministically rather than detected. There is no reliable discriminator — see audit §6 |
| U5 | The first write after migration replaces the 6-byte record with 48 bytes. Correct if the mapping is right (tested); lossy if a future edit breaks it |
| U6 | Legacy bits 5-7, if ever present, are dropped with no user-visible signal |

---

## 9. Scope Compliance

| Constraint | Status |
|-----------|--------|
| Migration only when length is exactly 6 | ✅ guarded by `LEGACY_LEN`; helper rejects anything else |
| Save format unchanged: 48 bytes, same record ID and store name | ✅ no version byte added |
| No RMS write during `load()` | ✅ statically verified |
| `save()` still writes the 48-byte layout | ✅ unchanged |
| 48-byte records read as before | ✅ `else` branch untouched |
| 7-47 and >48 byte behaviour unchanged | ✅ §6 |
| Unlock progression, roster, modes, gameplay untouched | ✅ `SaveStore.java` is the only tracked file changed |
| No commit | ✅ |
| No game / MIDlet / emulator | ✅ desktop JVM unit test only |

---

## 10. Conclusion

Option C is implemented as an in-memory transpose during `load()`. A 6-byte legacy record now maps each completion fact to the correct `(character, stage)` pair instead of transposing it incorrectly, and settings move verbatim to index 47 on proven bit-compatible grounds.

The 6-byte record is never written during load, so a failed or unplayed migration leaves it intact and re-runs identically next launch.

**Uncommitted and awaiting review.**
