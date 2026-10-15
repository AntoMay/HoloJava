# SaveStore Format Compatibility Audit — Source Schema & History

## 1. Current Source Schema (SaveStore.java)

### 1.1 Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `STORE` | `"holojava_fullgame_v1"` | RMS record store name |
| `RECORD_ID` | `1` | Single record ID |
| `DATA_LEN` | `48` | Record length in bytes |
| `SETTINGS_INDEX` | `47` | Index of settings byte (0-based) |
| `LEGACY_LEN` | `6` | Legacy record length |

### 1.2 Record Layout (Source Design)

```
Byte Offset | Field | Size | Description
------------|-------|------|------------
0-4         | Stage completion bitmasks (5 stages) | 5 bytes
5-46        | Reserved / future expansion | 42 bytes
47          | Settings byte | 1 byte
Total: 48 bytes
```

### 1.3 Bit Layout per Stage Byte

Each of `data[0]` through `data[4]` stores completion for 5 characters (bits 0-4):
- Bit 0: Character 0 completed this stage
- Bit 1: Character 1 completed this stage
- ...
- Bit 4: Character 4 completed this stage

### 1.4 Settings Byte Layout (data[47])

| Bit | Field | Values |
|-----|-------|--------|
| 0   | Hardcore mode | 0=off, 1=on |
| 1-3 | Selected character | 0-4 (5 characters) |
| 4-7 | Reserved | Unused |

### 1.4 Field Access Patterns

| Operation | Source Code | Index Calculation |
|-----------|-------------|-------------------|
| `isCompleted(char, stage)` | `data[char] & (1 << stage)` | `data[char] & (1 << stage)` |
| `setCompleted(char, stage)` | `data[char] \|= (1 << stage)` | `data[char] |= (1 << stage)` |
| `hardcore()` | `(data[47] & 1) != 0` | `data[47] & 1` |
| `setHardcore(v)` | `data[47] \|= 1` / `&= ~1` | bit 0 |
| `selectedCharacter()` | `data[47] & 0x3f` | bits 0-5 |
| `setSelectedCharacter(v)` | `(data[47] & 0xC0) | (v & 0x3F)` | bits 6-7 preserved |

### 1.5 Bounds Checking

| Method | Character Check | Stage Check |
|--------|-----------------|-------------|
| `isCompleted` | `0 <= char < CHARACTERS.length` | `0 <= stage <= 7` |
| `setCompleted` | `0 <= char < CHARACTERS.length` | `0 <= stage <= 7` |

Note: Stage check allows 0-7 (8 stages), but only 5 bytes allocated for stages 0-4.

## 2. Git History Analysis

### 2.1 Commit History

```bash
$ git log --oneline --all -- src/java-me-fullgame/me/holojava/fullgame/SaveStore.java
b1239e5 Initial HoloJava project baseline
```

Only one commit touches SaveStore.java: the initial commit `b1239e5`.

### 2.2 Initial Commit Content (b1239e5)

The initial commit **already contains** `DATA_LEN = 48`, `SETTINGS_INDEX = 47`, `LEGACY_LEN = 6`.

**Conclusion**: The source code has **always** declared `DATA_LEN = 48` in the repository history. There is no commit in the Git history that changed `DATA_LEN` from 6 to 48.

## 3. Bytecode vs Source Discrepancy

### 3.1 Evidence Summary

| Metric | Source Code | Reference JAR Bytecode |
|--------|-------------|------------------------|
| `DATA_LEN` | 48 | 6 (`bipush 6; newarray byte`) |
| Array allocation | `new byte[48]` | `bipush 6; newarray byte` |
| Loop bound | `i < 48` | `bipush 6; if_icmpge` (6 iterations) |
| `SETTINGS_INDEX` | 47 | Used as index 5 in legacy path |

### 3.2 Inference

The reference JAR (`HoloJavaFullGame.jar`) was built from a **different version of the source** where `DATA_LEN = 6`. The current repository's initial commit already has `DATA_LEN = 48`, suggesting:

1. The reference JAR was built from a pre-repository version (pre-git) where `DATA_LEN = 6`
2. The source was later updated to `DATA_LEN = 48` before the initial git commit
3. The reference JAR was never rebuilt from the updated source

## 3. Legacy Format Handling

### Current Source Legacy Handling (Source Lines 59-61)

```java
if (loaded.length == LEGACY_LEN) {  // LEGACY_LEN = 6
    for (int i = 0; i < 5; i++) data[i] = loaded[i];  // copies 5 bytes
    data[SETTINGS_INDEX] = loaded[5];  // SETTINGS_INDEX = 47, but loaded[5] is 6th byte
}
```

**Critical Bug in Source**: When loading a legacy 6-byte record, `data[SETTINGS_INDEX]` with `SETTINGS_INDEX = 47` would throw `ArrayIndexOutOfBoundsException` on a 48-byte array that was initialized from a 6-byte record (only indices 0-5 valid).

### Legacy Record Format (6 bytes)

| Byte | Content |
|------|---------|
| 0 | Stage 0 bitmask (5 chars) |
| 1 | Stage 1 bitmask |
| 2 | Stage 3 bitmask |
| 3 | Stage 3 bitmask |
| 3 | Stage 4 bitmask |
| 5 | Settings byte (hardcore + character) |

## 3. Record Format Evolution Hypothesis

```
Version 1 (JAR):     6 bytes  [5 stage bytes + 1 settings]
       ↓ Source updated
Version 2 (Source):  48 bytes [5 stage bytes + 42 reserved + 1 settings]
       ↓ (JAR not rebuilt)
Reference JAR:       Still 6-byte format
Current Source:      48-byte format
```

## 4. Record Format Specifications

### Current Source Format (48 bytes)

```
Offset | Size | Field
-------|------|-------
0-4    | 5    | Stage completion bitmasks (stages 0-4, 5 chars each)
5-46   | 42   | Reserved / future stages (5-46)
47     | 1    | Settings (hardcore + character)
Total: 48 bytes
```

### Reference JAR Format (6 bytes)

| Offset | Size | Field |
|--------|------|-------|
| 0      | 1    | Stage 0 bitmask |
| 1      | 1    | Stage 1 bitmask |
| 2      | 3    | Stage 2 bitmask |
| 3      | 1    | Stage 3 bitmask |
| 4      | 1    | Stage 4 bitmask |
| 5      | 1    | Settings (hardcore bit 0, char bits 1-5) |

### Legacy Format (6 bytes) - Same as Reference JAR

The reference JAR format **is** the legacy format. The current source treats this as "legacy" but expanded the format without rebuilding the JAR.

## 5. Validation Checks

### Source Internal Consistency Checks

| Check | Result |
|-------|--------|
| `SETTINGS_INDEX < DATA_LEN` | ✅ 47 < 48 |
| `LEGACY_LEN < DATA_LEN` | ✅ 6 < 48 |
| Stage bound check (`stageOrder > 7`) | ⚠️ Allows 0-7 but only 5 bytes allocated |
| `SETTINGS_INDEX < DATA_LEN` | ✅ 47 < 48 |

### Bytecode Internal Consistency

| Check | Result |
|-------|--------|
| Array allocation size matches loop bound | ✅ (both 6) |
| `SETTINGS_INDEX` used as index 5 in legacy path | ⚠️ Valid for 6-byte array |
| `LEGACY_LEN` matches allocation | ✅ (both 6) |

---

## 6. Summary

| Aspect | Current Source | Reference JAR | Status |
|--------|----------------|---------------|--------|
| Record length | 48 bytes | 6 bytes | **MISMATCH** |
| Stage tracking capacity | 8 stages (0-7) | 5 stages (0-4) | Reduced |
| Settings location | Index 47 | Index 5 (legacy) | Different layout |
| Legacy support | Reads 6-byte records | Native format | Compatible read |
| Forward compatibility | Reads 6-byte records | N/A | Partial |
| Backward compatibility | Writes 48-byte records | Cannot read 48-byte | **Broken** |

**Conclusion**: The source and reference JAR represent **two different record format versions**. The source evolved to 48 bytes but the JAR was never rebuilt.
