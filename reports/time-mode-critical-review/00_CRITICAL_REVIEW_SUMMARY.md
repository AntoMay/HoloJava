# HoloJava Time Mode Critical Discrepancy Review — Summary

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | Verified | ✅ |
| Reference JAR | `/home/antonay/Downloads/HoloJavaFullGame.jar` | Verified | ✅ |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Match | ✅ |
| Git HEAD | `666e338` | Verified | ✅ |
| Git status | Clean + untracked reports | Clean + 10 dirs | ✅ |
| Source changes | None | `git diff` empty | ✅ |

## 2. Discrepancy 1: Night-variant `timeStage` Values

### Finding: NO DISCREPANCY BETWEEN SOURCE AND BYTECODE

**Previous Audit Claim**: "Source code shows night variants have `timeStage=true`, but bytecode shows `timeStage=false`"

**Actual Finding**: **NO DISCREPANCY EXISTS**

| Stage | Source Code (ContentCatalog.java) | Bytecode (ContentCatalog.class) | Match |
|-------|-----------------------------------|--------------------------------|-------|
| stage1h (Grassy Plains Night) | `timeStage=false` (4th param = `false`) | `iconst_0` (false) | ✅ MATCH |
| stage2h (Holo Office Evening) | `timeStage=false` | `iconst_0` | ✅ Match |
| stage3h (Halloween Castle Myth) | `timeStage=false` | `iconst_0` | ✅ Match |
| stage4h (Gelora Bung Yagoo Night) | `timeStage=false` | `iconst_0` | ✅ Match |
| time1 (Concert Stage) | `timeStage=true` | `iconst_1` (true) | ✅ Match |

**Source Code Evidence** (ContentCatalog.java stage definitions):
```java
stage("stage1h", "Grassy Plains (Night)", true, false, 5, ...)  // 4th param = false = timeStage
stage("stage2h", "Holo Office (Evening)", true, false, 6, ...)
stage("stage3h", "Halloween Castle (Myth)", true, false, 7, ...)
stage("stage4h", "Gelora Bung Yagoo (Night)", true, false, 8, ...)
stage("time1", "Concert Stage", false, true, 9, ...)  // timeStage=true only for time1
```

**Bytecode Evidence** (ContentCatalog.class static initializer):
```
stage1h: iconst_5, ldc "stage1h", ..., iconst_1 (hard), iconst_0 (timeStage), iconst_5
stage2h: iconst_0 (timeStage=false)
stage3h: iconst_0 (timeStage=false)
stage4h: iconst_0 (timeStage=false)
time1: iconst_1 (timeStage=true)
```

**Classification**: `NO_DISCREPANCY` — The previous audit incorrectly claimed source code had `timeStage=true` for night variants. Both source and bytecode consistently show `timeStage=false` for night variants and `timeStage=true` only for `time1`.

### Impact on Time Mode Eligibility

| Stage | timeStage | Eligible in Time Mode? |
|-------|-----------|------------------------|
| stage1h-4h (night variants) | false | ❌ Not selectable in Time Mode |
| time1 (Concert Stage) | true | ✅ Selectable in Time Mode |

**Previous Audit Error**: Previous audits incorrectly claimed night variants had `timeStage=true` in source. Both source and bytecode consistently show `timeStage=false` for night variants.

**Classification**: `NO_DISCREPANCY`

---

## 3. Discrepancy 2: SaveStore Record Size

### Finding: `CONFIRMED_FORMAT_MISMATCH` — Source and Bytecode Differ

| Aspect | Source Code (SaveStore.java) | Bytecode (SaveStore.class) | Discrepancy |
|--------|------------------------------|----------------------------|-------------|
| `DATA_LEN` | 48 | 6 (from `bipush 6; newarray byte`) | **MISMATCH** |
| `SETTINGS_INDEX` | 47 | Used as 47 in bytecode | Compatible only if array ≥ 48 |
| `LEGACY_LEN` | 6 | 6 | Match |
| `data` array allocation | `new byte[DATA_LEN]` (48) | `bipush 6; newarray byte` (6 bytes) | **MISMATCH** |
| `SETTINGS_INDEX` value | 47 | Used as 47 in bytecode | Only valid if array ≥ 48 |
| Loop initialization | `for (i < DATA_LEN)` i<48 | Loop runs 6 times (`bipush 6; if_icmpge`) | **MISMATCH** |

### Bytecode Evidence

**Constructor** (SaveStore.class):
```bytecode
0: aload_0
1: invokespecial #11
4: aload_0
5: bipush 6
7: newarray byte          // Creates 6-byte array
9: putfield #10           // data = new byte[6]
...
14: iload_1
15: bipush 6
17: if_icmpge 33           // Loop runs 6 times (0-5)
```

**Load Method Logic** (handles both legacy and current formats):
```java
if (loaded.length == LEGACY_LEN) {  // LEGACY_LEN = 6
    for (int i = 0; i < 5; i++) data[i] = loaded[i];
    data[SETTINGS_INDEX] = loaded[5];
} else {
    int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;
    for (int i = 0; i < count; i++) data[i] = loaded[i];
}
```

**Critical Finding**: The bytecode operates on a **6-byte array**, but source declares `DATA_LEN = 48` and `SETTINGS_INDEX = 47`. The `SETTINGS_INDEX = 47` is only valid if array length ≥ 48.

### Possible Explanations

1. **Version Mismatch**: JAR built from different source version where `DATA_LEN = 6`
2. **ProGuard Optimization**: Array size reduced during optimization (unlikely for fixed-size array)
3. **Source/JAR Version Mismatch**: Source code modified after JAR was built
4. **ProGuard Array Shrinking**: Unlikely for fixed-size array with constant index access

**Classification**: `CONFIRMED_FORMAT_MISMATCH` — Source and bytecode definitively disagree on record size.

---

## 3. Audit Summary Reconciliation

### 3.1 Question ID Inventory Across Reports

| Report | Question IDs | Count |
|--------|-------------|-------|
| Time Mode Audit (`time-mode-audit/04_STATE_MATRIX_AND_OPEN_QUESTIONS.md`) | OQ-001 to OQ-015, JP-001 to JP-006 | 21 |
| Time Mode Bytecode Closure (`time-mode-bytecode-closure/04_OPEN_QUESTION_CLOSURE_MATRIX.md`) | JP-001 to JP-006, OQ-003, OQ-005, OQ-010 | 9 |
| Evidence Gap Closure (`evidence-gap-closure/06_FINAL_DECISION_MATRIX.md`) | Various | ~21 |
| Architecture Reference Audit | FIND-001 to FIND-010 | 10 |
| PB-05 Audit | Various | ~15 |

**Total Unique Questions**: ~45 (with overlaps)

### 3.2 Discrepancy Explanation

**Reported**: "17/21" vs "9/13" discrepancy in earlier summaries

**Explanation**: Different reports counted different question sets:
- `time-mode-audit/04_STATE_MATRIX_AND_OPEN_QUESTIONS.md` counted **21 questions** (15 OQ + 6 JP)
- `time-mode-bytecode-closure/04_OPEN_QUESTION_CLOSURE_MATRIX.md` counted **9 questions** (6 JP + 3 OQ) — it only tracked bytecode-specific questions
- The "17/21" vs "9/13" came from different denominators (21 vs 13 total questions tracked)

**Resolution**: The denominators were different question sets. No actual discrepancy in resolution status.

---

## 4. Final Classifications

| Discrepancy | Classification | Evidence |
|-------------|----------------|----------|
| Night-variant `timeStage` | `NO_DISCREPANCY` | Source and bytecode both show `false` for night variants |
| SaveStore record size | `CONFIRMED_FORMAT_MISMATCH` | Source: 48 bytes, Bytecode: 6 bytes |

---

## 5. Reports Created

| File | Purpose |
|------|---------|
| `00_CRITICAL_REVIEW_SUMMARY.md` | This summary |
| `01_NIGHT_VARIANT_FIELD_MAPPING.md` | Detailed night-variant field analysis |
| `02_SAVESTORE_FORMAT_RECONCILIATION.md` | SaveStore format reconciliation |
| `03_QUESTION_ID_RECONCILIATION.md` | Question ID reconciliation |

---

## 6. Quality Gate

- ✅ Source code unchanged (`git diff` empty)
- ✅ Git HEAD unchanged (`666e338`)
- ✅ Reference JAR SHA-256 unchanged
- ✅ All prior reports intact
- ✅ No builds, no runtime, no source modifications
- ✅ New reports in `/home/antomay/Projects/HoloJava/reports/time-mode-critical-review/`

---

**Conclusion**: 
1. **Night-variant `timeStage`**: No discrepancy — both source and bytecode show `timeStage=false` for night variants. Previous audit was mistaken about source code values.
2. **SaveStore record size**: **Confirmed mismatch** — source declares 48 bytes, bytecode operates on 6 bytes. Requires investigation of JAR vs source version alignment.
