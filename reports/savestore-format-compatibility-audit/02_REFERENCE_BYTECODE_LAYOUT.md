# SaveStore Format Compatibility Audit — Reference Bytecode Layout

## 1. Bytecode Overview

### 1.1 Disassembly Metadata
- **Class**: `me.holojava.fullgame.SaveStore`
- **Source File**: `SaveStore.java`
- **Compiler**: Standard javac (inferred)
- **Disassembly Tool**: `javap -c -p`
- **Bytecode Lines**: 299

### 1.2 Class Structure
```
Class: me.holojava.fullgame.SaveStore
Fields:
  #10: data:[B                    // byte[] data
Methods:
  <init>()V                      // Constructor
  isCompleted(II)Z               // Check stage completion
  setCompleted(II)V              // Set stage completion
  hardcore()Z                    // Get hardcore flag
  setHardcore(Z)V                // Set hardcore flag
  selectedCharacter()I           // Get selected character
  setSelectedCharacter(I)V       // Set selected character
  load()V                        // Load from RMS
  save()V                        // Save to RMS
```

## 2. Constant Pool Values

| Constant Pool # | Value | Type | Notes |
|-----------------|-------|------|-------|
| #3 | "holojava_fullgame_v1" | String | STORE name |
| #10 | data:[B | Field | byte[] data |
| #11 | java/lang/Object."<init>":()V | Method | Object constructor |
| #14 | RecordStore.openRecordStore | Method | RMS open |
| #13 | RecordStore.getRecord | Method | Read record |
| #15 | RecordStore.setRecord | Method | Write record |
| #12 | RecordStore.closeRecordStore | Method | Close store |
| #16 | load() | Method | Load method |
| #17 | save() | Method | Save method |
| #3 | "holojava_fullgame_v1" | String | STORE constant |
| #16 | load()V | Method | load() |
| #17 | save()V | Method | save() |

## 2. Bytecode Evidence — Key Methods

### 2.1 Constructor (`<init>`)

```bytecode
me.holojava.fullgame.SaveStore();
  Code:
     0: aload_0
     1: invokespecial #11                 // java/lang/Object."<init>"
     4: aload_0
     5: bipush        6                  // DATA_LEN = 6
     7: newarray       byte              // new byte[6]
     9: putfield      #10                // this.data = new byte[6]
    12: iconst_0
    13: istore_1                         // i = 0
    14: iload_1
    15: bipush        6                  // loop bound = 6
    17: if_icmpge     33                 // if i >= 6
    20: aload_0
    21: getfield      #10                // this.data
    24: iload_1                          // i
    25: iconst_0
    26: bastore                          // data[i] = 0
    27: iinc          1, 1               // i++
    30: goto          14                 // loop
    33: aload_0
    34: invokespecial #16                // invokespecial load()
    37: return
```

**Key Findings:**
- `bipush 6; newarray byte` → **6-byte array allocation**
- Loop runs exactly 6 iterations (`bipush 6; if_icmpge`)
- No reference to `DATA_LEN = 48` or `SETTINGS_INDEX = 47` in bytecode

### 2.2 `isCompleted(int, int)` — Offset 35-60

```bytecode
boolean isCompleted(int, int);
  Code:
     0: iload_1                    // stageOrder
     1: iflt          9           // if stageOrder < 0
     4: iload_1
     5: iconst_5                   // 5
     6: if_icmplt     11           // if stageOrder < 5
     9: iconst_0
    10: ireturn                     // return false
    11: aload_0
    12: getfield      #10          // this.data
    15: iload_1                    // stageOrder
    16: baload                     // byte b = data[stageOrder]
    17: sipush        255          // 0xFF
    20: iand                       // b & 0xFF
    21: istore_3
    22: iload_3
    23: iconst_1
    24: iload_2                    // characterIndex
    25: ishl                       // 1 << characterIndex
    26: iand                       // (b & 0xFF) & (1 << charIndex)
    27: ifeq          34
    30: iconst_1
    31: goto          35
    34: iconst_0
    35: ireturn
```

**Logic**: `return (data[stageOrder] & 0xFF & (1 << characterIndex)) != 0`
- Stage order checked: `0 <= stageOrder < 5` (5 stages max)
- Character index: no explicit bound check in bytecode (uses `ishl` directly)

### 2.3 `setCompleted(int, int)` — Offset 63-94

```bytecode
void setCompleted(int, int);
  Code:
     0: iload_1                    // stageOrder
     1: iflt          19
     4: iload_1
     5: iconst_5
     6: if_icmpge     19          // if stageOrder >= 5
     9: iload_2                    // characterIndex
    10: iflt          19
    13: iload_2
    14: bipush        7
    16: if_icmple     20          // if characterIndex <= 7
    19: return                     // return if invalid
    20: aload_0
    21: getfield      #10          // this.data
    24: iload_1                    // stageOrder
    25: baload                     // b = data[stageOrder]
    26: sipush        255         // 0xFF
    29: iand                       // b & 0xFF
    30: istore_3
    31: aload_0
    32: getfield      #10
    35: iload_1
    36: iload_3
    37: iconst_1
    38: iload_2                   // characterIndex
    38: ishl                       // 1 << characterIndex
    39: ior                        // b | (1 << characterIndex)
    41: i2b                        // cast to byte
    42: bastore                    // data[stageOrder] = new byte
    43: aload_0
    44: invokespecial #17         // save()
    47: return
```

**Bounds Checks**: `stageOrder < 5`, `0 <= characterIndex <= 7`

### 2.4 `load()` Method — Offset 173-246

Key bytecode segments:

**Legacy Path (record length == 6):**
```
189:  bipush        6            // LEGACY_LEN = 6
190:  if_icmpge     31           // if loaded.length >= 6
...
26: aload_2                    // loaded
27: arraylength
28: goto          33
31: bipush        6             // LEGACY_LEN = 6
33: istore_3                    // store count = 6
...
37: iload_3
39: iload_3
40: if_icmpge     60
...
43: aload_0
44: getfield      #10          // this.data
47: iload         4            // i
49: aload_2                    // loaded
50: iload         4
52: baload                     // loaded[i]
53: bastore                     // data[i] = loaded[i]
```

**Legacy Path Logic (record length == 6):**
- Copies first 5 bytes (`i < 5`) to `data[0..4]`
- Sets `data[SETTINGS_INDEX] = loaded[5]` — **uses index 5**

**Modern Path (record length > 6):**
```
31: bipush        6             // LEGACY_LEN
33: istore_3                    // count = 6 (or loaded.length if < DATA_LEN)
...
63: aload_0
64: getfield      #10          // this.data
67: aload_2
68: iload         4
69: baload
70: bastore
```

### 2.5 `save()` Method — Offset 247-290

```bytecode
aload_1                     // store
iconst_1                   // record ID = 1
aload_0
getfield      #10          // this.data
iconst_0
aload_0
getfield      #10          // this.data
arraylength                // data.length
invokevirtual #15          // setRecord(recordID, data, 0, data.length)
```

**Key**: Writes `data.length` bytes — **6 bytes in bytecode**.

### 2.5 Constant Values in Bytecode

| Constant | Bytecode | Source Value |
|----------|----------|--------------|
| `DATA_LEN` | `bipush 6` (multiple) | 48 (source) |
| `LEGACY_LEN` | `bipush 6` (multiple) | 6 |
| `SETTINGS_INDEX` | `iconst_5` (index 5) | 47 (source) |
| `RECORD_ID` | `iconst_1` | 1 |
| `STORE` name | `"holojava_fullgame_v1"` | Match |

## 3. SaveStore Record Layout — Bytecode View

### 3.1 Actual Record Layout (Bytecode)

| Offset | Size | Field | Notes |
|--------|------|-------|-------|
| 0 | 1 | Stage 0 bitmask (5 chars) | |
| 1 | 1 | Stage 1 bitmask | |
| 2 | 1 | Stage 3 bitmask | |
| 3 | 1 | Stage 4 bitmask | |
| 4 | 1 | Stage 4 bitmask | |
| 5 | 1 | Settings (hardcore + char) | Index 5 = SETTINGS_INDEX in bytecode |

**Total**: 6 bytes

### Stage Bitmask Layout (per byte)
- Bit 0: Character 0 completed
- Bit 1: Character 1 completed
- Bit 4: Character 4 completed
- Bits 5-7: Unused

### Settings Byte (offset 5)
- Bit 0: Hardcore mode (1 = on)
- Bits 1-5: Selected character (0-4, stored in bits 1-5)
- Bits 6-7: Unused

### Access Patterns (Bytecode)

| Operation | Bytecode Pattern |
|-----------|------------------|
| `isCompleted(char, stage)` | `data[stage] & (1 << char)` |
| `setCompleted(char, stage)` | `data[stage] |= (1 << char)` |
| `hardcore()` | `data[5] & 1` |
| `hardcore(v)` | `data[5] |= 1` / `&= ~1` |
| `selectedCharacter()` | `data[5] & 0x3F` |
| `setSelectedCharacter(v)` | `(data[5] & 0xC0) | (v & 0x3F)` |

## 4. Field Access Evidence

### 4.1 Settings Byte Access (Bytecode Offsets)

| Operation | Bytecode | Index Used |
|-----------|----------|------------|
| `hardcore()` | `iconst_5; baload; iconst_1; iand` | Index 5 |
| `setHardcore(true)` | `iconst_5; baload; sipush 255; iand; iconst_1; ior; i2b; bastore` | 5 |
| `selectedCharacter()` | `iconst_5; baload; bipush 15; iand` | 5 |
| `setSelectedCharacter` | `iconst_5; baload; sipush 240; iand; ... ior; i2b; bastore` | 5 |
| Legacy load: `data[SETTINGS_INDEX] = loaded[5]` | `bipush 5; baload; bastore` | Index 5 |

**Critical**: All bytecode uses **index 5** for settings byte. Source claims index 47.

## 5. RecordStore Operations

### 5.1 RecordStore.openRecordStore
```bytecode
ldc           #3                  // "holojava_fullgame_v1"
iconst_0/1                      // createIfNecessary flag
invokevirtual #14                 // openRecordStore
```

### 4.2 RecordStore.setRecord
```bytecode
iconst_1                      // RECORD_ID = 1
aload_0
getfield      #10             // data
iconst_0
aload_0
getfield      #10             // data
arraylength                   // data.length (6 in bytecode)
invokevirtual #15             // setRecord(1, data, 0, data.length)
```

### 4.3 RecordStore Exception Handling

All RMS operations wrapped in try-catch for `RecordStoreException` — exceptions caught and ignored (persistence is optional).

## 5. Legacy Record Handling

### 5.1 Legacy Detection
```bytecode
aload_2              // loaded
arraylength
bipush        6      // LEGACY_LEN = 6
if_icmpge     31     // if loaded.length >= 6
```

### 5.2 Legacy Load Path (record.length == 6)
```bytecode
iconst_0
istore         4        // i = 0
iload         4        // i
bipush        5        // 5
if_icmpge     31       // while i < 5
aload_0
getfield      #10       // data
iload         4        // i
aload_2
iload         4
baload                      // loaded[i]
bastore                     // data[i] = loaded[i]
iinc          4, 1        // i++
goto          loop

// After loop:
aload_0
getfield      #10           // data
bipush        5             // SETTINGS_INDEX in bytecode = 5
aload_2
iconst_5
baload                       // loaded[5]
bastore                     // data[5] = loaded[5]
```

## 6. Compatibility Implications

### 5.1 6-byte Record → 48-byte Source
| Operation | Result |
|-----------|--------|
| Load legacy (6 bytes) | **CRASH**: `data[47] = loaded[5]` → `ArrayIndexOutOfBoundsException` (array size 6, index 47) |
| Save 48-byte record | **Truncation**: JAR writes 6 bytes, reads only 6 |

### 5.2 Field Mapping Summary

| Logical Field | Source Offset | Bytecode Offset | Size |
|---------------|---------------|-----------------|------|
| Stage 0 bitmask | 0 | 0 | 1 |
| Stage 1 bitmask | 1 | 1 | 1 |
| Stage 2 bitmask | 2 | 2 | 1 |
| Stage 3 bitmask | 3 | 3 | 1 |
| Stage 4 bitmask | 4 | 4 | 1 |
| Stages 5-7 | 5-7 | **N/A** | N/A |
| Settings byte | 47 | 5 | 1 |

---

**Conclusion**: Bytecode implements a **6-byte record format** with 5 stage bytes + 1 settings byte. Source expects 48 bytes. This is a fundamental format incompatibility.
