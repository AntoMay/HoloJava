# SaveStore Byte Mapping Evidence

## 1. Baseline Verification

| Item | Value |
|------|-------|
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Git HEAD | `666e338` ✅ |
| Source file | `src/java-me-fullgame/me/holojava/fullgame/SaveStore.java` |

---

## 2. Legacy Format (Reference JAR Bytecode) — 6 Bytes

### 2.1 Bytecode Evidence

**Constructor allocation:**
```
bipush 6; newarray byte  →  new byte[6]
```

**Loop initialization (6 iterations):**
```
bipush 6; if_icmpge  →  for (i=0; i<6; i++) data[i]=0
```

**`isCompleted(int characterIndex, int stageOrder)`:**
```
iload_1 (stageOrder)
iconst_5; if_icmpge → if (stageOrder >= 5) return false
baload → byte b = data[stageOrder]  // ARRAY INDEX = stageOrder
(1 << characterIndex) → bit position = characterIndex
```

**`setCompleted(int characterIndex, int stageOrder)`:**
```
stageOrder < 5 check
characterIndex <= 7 check (allows 8 characters!)
baload → data[stageOrder]
bastore → data[stageOrder] = newValue
```

**`hardcore()` / `setHardcore()` / `selectedCharacter()` / `setSelectedCharacter()`:**
```
iconst_5; baload → data[5]  // SETTINGS_INDEX = 5 in bytecode
```

**Legacy load path (record.length == 6):**
```
Copies i=0..4: data[i] = loaded[i]  (5 bytes)
Then: data[5] = loaded[5]  (settings at index 5)
```

**Save path:**
```
setRecord(1, data, 0, data.length)  →  writes 6 bytes
```

### 2.2 Legacy Record Layout (Verified from Bytecode)

| Offset | Size | Field | Bit Layout |
|--------|------|-------|------------|
| 0 | 1 | Stage 0 completions | Bit n = Character n completed stage 0 (n=0..4) |
| 1 | 1 | Stage 1 completions | Bit n = Character n completed stage 1 |
| 2 | 1 | Stage 2 completions | Bit n = Character n completed stage 2 |
| 3 | 1 | Stage 3 completions | Bit n = Character n completed stage 3 |
| 4 | 1 | Stage 4 completions | Bit n = Character n completed stage 4 |
| 5 | 1 | Settings | Bit 0 = hardcore; Bits 1-5 = character index (0-4); Bits 6-7 = unused |

**Total: 6 bytes**

### 2.3 Legacy Access Patterns (Bytecode)

| Operation | Array Index | Bit Position |
|-----------|-------------|--------------|
| `isCompleted(char, stage)` | `stage` | `char` |
| `setCompleted(char, stage)` | `stage` | `char` |
| `hardcore()` | 5 | 0 |
| `selectedCharacter()` | 5 | 1-5 |

---

## 3. Current Source Format — 48 Bytes

### 3.1 Source Code Evidence

```java
private static final int DATA_LEN = 48;
private static final int SETTINGS_INDEX = 47;
private static final int LEGACY_LEN = 6;
private final byte[] data = new byte[DATA_LEN];  // 48 bytes
```

**Constructor:**
```java
for (int i = 0; i < DATA_LEN; i++) data[i] = 0;  // 48 iterations
```

**`isCompleted(int characterIndex, int stageOrder)`:**
```java
if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length || stageOrder < 0 || stageOrder > 7) return false;
int v = data[characterIndex] & 0xff;  // ARRAY INDEX = characterIndex
return (v & (1 << stageOrder)) != 0;  // BIT POSITION = stageOrder
```

**`setCompleted(int characterIndex, int stageOrder)`:**
```java
if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length || stageOrder < 0 || stageOrder > 7) return;
int v = data[characterIndex] & 0xff;
data[characterIndex] = (byte) (v | (1 << stageOrder));
save();
```

**Settings access (all use `SETTINGS_INDEX = 47`):**
```java
boolean hardcore() { return (data[47] & 1) != 0; }
void setHardcore(boolean value) { ... data[47] = ...; save(); }
int selectedCharacter() { return data[47] & 0x3f; }
void setSelectedCharacter(int value) { ... data[47] = ...; save(); }
```

**Load method:**
```java
if (loaded.length == LEGACY_LEN) {  // 6
    for (int i = 0; i < 5; i++) data[i] = loaded[i];
    data[SETTINGS_INDEX] = loaded[5];  // data[47] = loaded[5]
} else {
    int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;
    for (int i = 0; i < count; i++) data[i] = loaded[i];
}
```

**Save method:**
```java
store.setRecord(RECORD_ID, data, 0, data.length);  // writes 48 bytes
```

### 3.2 Source Record Layout (Verified from Source)

| Offset | Size | Field | Bit Layout |
|--------|------|-------|------------|
| 0 | 1 | Character 0 stages | Bit m = Stage m completed by char 0 (m=0..7) |
| 1 | 1 | Character 1 stages | Bit m = Stage m completed by char 1 |
| 2 | 1 | Character 2 stages | Bit m = Stage m completed by char 2 |
| 3 | 1 | Character 3 stages | Bit m = Stage m completed by char 3 |
| 4 | 1 | Character 4 stages | Bit m = Stage m completed by char 4 |
| 5-46 | 42 | Reserved / future | All zero (uninitialized) |
| 47 | 1 | Settings | Bit 0 = hardcore; Bits 1-5 = character index (0-4); Bits 6-7 = reserved |

**Total: 48 bytes**

### 3.3 Source Access Patterns

| Operation | Array Index | Bit Position |
|-----------|-------------|--------------|
| `isCompleted(char, stage)` | `char` | `stage` |
| `setCompleted(char, stage)` | `char` | `stage` |
| `hardcore()` | 47 | 0 |
| `selectedCharacter()` | 47 | 1-5 |

---

## 4. Transposition Proof

### 4.1 Layout Comparison

```
LEGACY (6 bytes, stage-major):           SOURCE (48 bytes, character-major):
┌─────────────────────────────┐          ┌─────────────────────────────┐
│ data[0] = Stage 0 bits      │          │ data[0] = Char 0 bits       │
│   bit 0 = Char 0 done S0    │          │   bit 0 = Stage 0 done      │
│   bit 1 = Char 1 done S0    │          │   bit 1 = Stage 1 done      │
│   bit 2 = Char 2 done S0    │          │   bit 2 = Stage 2 done      │
│   bit 3 = Char 3 done S0    │          │   bit 3 = Stage 3 done      │
│   bit 4 = Char 4 done S0    │          │   bit 4 = Stage 4 done      │
│   bit 5-7 = unused          │          │   bit 5-7 = Stages 5-7      │
├─────────────────────────────┤          ├─────────────────────────────┤
│ data[1] = Stage 1 bits      │          │ data[1] = Char 1 bits       │
│   bit 0 = Char 0 done S1    │          │   bit 0 = Stage 0 done      │
│   bit 1 = Char 1 done S1    │          │   ...                       │
│   ...                       │          │                             │
├─────────────────────────────┤          ├─────────────────────────────┤
│ data[4] = Stage 4 bits      │          │ data[4] = Char 4 bits       │
├─────────────────────────────┤          ├─────────────────────────────┤
│ data[5] = Settings          │          │ data[5-46] = Reserved (0)   │
└─────────────────────────────┘          │ data[47] = Settings         │
                                         └─────────────────────────────┘
```

### 4.2 Mathematical Transposition

Legacy: `L[stage][char_bit] = 1` iff character `char` completed stage `stage`
Source: `S[char][stage_bit] = 1` iff character `char` completed stage `stage`

**Relationship:** `S[char][stage] = L[stage][char]` for stage ∈ [0,4], char ∈ [0,4]

For stages 5-7: `L` has no data → `S[char][5..7] = 0` (incomplete)

### 4.3 Settings Byte Compatibility

| Aspect | Legacy (byte 5) | Source (byte 47) | Compatible? |
|--------|-----------------|------------------|-------------|
| Hardcore bit | Bit 0 | Bit 0 | ✅ |
| Character bits | Bits 1-5 (5 bits) | Bits 1-5 (5 bits) | ✅ |
| Unused bits | 6-7 | 6-7 (reserved) | ✅ |
| **Byte value** | **Identical meaning** | **Identical meaning** | ✅ **COMPATIBLE** |

**Conclusion:** Settings byte can be copied directly: `data[47] = loaded[5]` is semantically correct.

---

## 5. Why Proposed Migration Fails

### 5.1 Proposed Code (from compatibility audit)

```java
for (int i = 0; i < 5; i++) data[i] = loaded[i];  // WRONG: copies stage-major to character-major
data[47] = loaded[5];  // Correct for settings
```

### 5.2 Concrete Corruption Example

**Legacy save state:** Character 2 completed Stage 1, Character 4 completed Stage 3.
- `loaded[1]` (Stage 1) = `0b00000100` (bit 2 set)
- `loaded[3]` (Stage 3) = `0b00010000` (bit 4 set)

**After proposed migration:**
- `data[1]` (Char 1) = `0b00000100` → "Char 1 completed Stage 2" ❌
- `data[3]` (Char 3) = `0b00010000` → "Char 3 completed Stage 4" ❌

**Correct migration should produce:**
- `data[2]` (Char 2) bit 1 set → "Char 2 completed Stage 1" ✅
- `data[4]` (Char 4) bit 3 set → "Char 4 completed Stage 3" ✅

---

## 6. Correct Transposition Algorithm

### 6.1 Pseudocode

```java
// Legacy: loaded[0..4] = stage bitmasks, loaded[5] = settings
// Source: data[0..4] = character bitmasks, data[47] = settings

// Clear all character bitmasks
for (int c = 0; c < 5; c++) data[c] = 0;

// Transpose: for each stage 0..4, each character 0..4
for (int stage = 0; stage < 5; stage++) {
    byte stageMask = loaded[stage];
    for (int charIdx = 0; charIdx < 5; charIdx++) {
        if ((stageMask & (1 << charIdx)) != 0) {
            // Character charIdx completed this stage
            data[charIdx] |= (1 << stage);
        }
    }
}

// Copy settings (compatible bit layout)
data[47] = loaded[5];

// Stages 5-7 remain 0 (incomplete) — correct default for migration
```

### 6.2 Verification of Transposition

| Legacy Input | Source Output | Correct? |
|--------------|---------------|----------|
| `loaded[stage]` bit `char` = 1 | `data[char]` bit `stage` = 1 | ✅ |
| `loaded[5]` (settings) | `data[47]` (settings) | ✅ |
| Stages 5-7 not in legacy | `data[0..4]` bits 5-7 = 0 | ✅ (default incomplete) |

---

## 7. Evidence Classification

| Finding | Evidence Type | Status |
|---------|---------------|--------|
| Legacy uses `data[stage]` | Bytecode `baload` with `stageOrder` | ✅ Verified |
| Source uses `data[character]` | Source `data[characterIndex]` | ✅ Verified |
| Legacy settings at index 5 | Bytecode `iconst_5` for settings | ✅ Verified |
| Source settings at index 47 | Source `SETTINGS_INDEX = 47` | ✅ Verified |
| Settings bit layout identical | Bytecode `0x3F` mask vs source `0x3F` | ✅ Verified |
| Transposition required | Mathematical derivation | ✅ Proven |
| Proposed migration wrong | Counterexample above | ✅ Proven |
| Indices 5-46 purpose | Source: never written, never read | ⚠️ Inferred (reserved) |
| Stages 5-7 in source | Source allows `stageOrder <= 7` | ✅ Verified |
| Stages 5-7 in legacy | Bytecode rejects `stageOrder >= 5` | ✅ Verified |

---

## 8. Open Questions

1. **Indices 5-46 (42 bytes):** Source allocates but never uses. Are they intended for future stages 5-46 (one byte per stage)? Or for something else? No code references them.
2. **Character count bound:** Legacy `setCompleted` allows `characterIndex <= 7` (8 chars) but only 5 bits used. Source uses `ContentCatalog.CHARACTERS.length` (assumed 5). Discrepancy.
3. **Stage count:** Legacy supports 5 stages (0-4). Source supports 8 stages (0-7). Migration must default new stages to incomplete.
4. **Versioning:** No format version in either format. Detection relies on record length only.

---

## 9. Conclusion

**The byte mapping is PROVEN to require transposition, not copy.** Any migration must implement the bit-level transpose algorithm. The settings byte is the only directly compatible field.

**Previous reports (audit and validation) did not identify this transposition**, leading to an incorrect assumption that byte-copy migration would work.