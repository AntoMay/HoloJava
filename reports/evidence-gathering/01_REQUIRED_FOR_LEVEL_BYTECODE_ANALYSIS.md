# HoloJava Evidence Gathering — requiredForLevel() Bytecode Analysis

## 1. Source Code vs Bytecode Comparison

### 1.1 Source Code (RunWorld.java:1270-1277)

```java
private int requiredForLevel(int level) {
    // The Level Up page explicitly states 79 EXP for level 1 -> 2.
    if (level <= 1) return 79;
    double current = MathUtil.power(4.0 * level, 2.1);
    double next = MathUtil.power(4.0 * (level + 1), 2.1);
    int value = MathUtil.roundEven(next) - MathUtil.roundEven(current);
    return value < 1 ? 1 : value;
}
```

### 1.2 Bytecode (RunWorld.class, lines 7171-7230)

```bytecode
private int requiredForLevel(int);
  Code:
     0: iload_1              // load level parameter
     1: iconst_1            // push 1
     2: if_icmpgt     8     // if level > 1, goto offset 8
     5: bipush        79    // push 79
     7: ireturn             // return 79
     8: ldc2_w        #215  // double 4.0
    11: iload_1             // load level
    12: i2d                 // int to double
    13: dmul                // 4.0 * level
    14: ldc2_w        #211  // double 2.1
    17: invokestatic  #526  // MathUtil.power(DD)D
    20: dstore_2            // store current
    21: ldc2_w        #215  // double 4.0
    24: iload_1             // load level
    25: iconst_1            // push 1
    26: iadd                // level + 1
    27: i2d                 // int to double
    28: dmul                // 4.0 * (level + 1)
    29: ldc2_w        #211  // double 2.1
    32: invokestatic  #526  // MathUtil.power(DD)D
    35: dstore        4     // store next
    37: dload         4     // load next
    39: invokestatic  #527  // MathUtil.roundEven(D)I
    42: dload_2             // load current
    43: invokestatic  #527  // MathUtil.roundEven(D)I
    46: isub                // subtract
    47: istore        6     // store value
    49: iload         6     // load value
    51: iconst_1            // push 1
    52: if_icmpge     59    // if value >= 1, goto 59
    55: iconst_1            // push 1
    56: goto          61    // goto return
    59: iload         6     // load value
    61: ireturn             // return value
```

### 1.3 Verification: MATCH

| Aspect | Source | Bytecode | Status |
|--------|--------|----------|--------|
| Level <= 1 returns 79 | `if (level <= 1) return 79;` | `if_icmpgt 8; bipush 79; ireturn` | CONFIRMED_PARITY |
| Floating-point for level > 1 | `MathUtil.power(4.0 * level, 2.1)` | `ldc2_w 4.0; i2d; dmul; ldc2_w 2.1; invokestatic power` | CONFIRMED_PARITY |
| roundEven on both values | `MathUtil.roundEven(next) - MathUtil.roundEven(current)` | Two `invokestatic roundEven` + `isub` | CONFIRMED_PARITY |
| Minimum 1 guard | `value < 1 ? 1 : value` | `if_icmpge 59; iconst_1; goto 61` | CONFIRMED_PARITY |

**CONCLUSION**: CONFIRMED_PARITY — bytecode matches source exactly for requiredForLevel.

---

## 2. MathUtil.power() and roundEven() Bytecode Verification

### 2.1 roundEven (MathUtil.class lines 24-56)

```bytecode
static int roundEven(double);
  Code:
     0: dload_0              // value
     1: invokestatic  #14   // Math.floor
     4: dstore_2            // floorValue
     5: dload_0              // value
     6: dload_2              // floorValue
     7: dsub                 // diff = value - floorValue
     8: dstore        4     // diff
    10: dload         4     // diff
    12: ldc2_w        #6    // double 0.5
    15: dcmpl               // diff > 0.5?
    16: ifle          24    // no -> check < 0.5
    19: dload_2             // floorValue
    20: dconst_1
    21: dadd                // floorValue + 1
    22: d2i
    23: ireturn
    24: dload         4     // diff
    26: ldc2_w        #6    // double 0.5
    29: dcmpg               // diff < 0.5?
    30: ifge          36    // no -> tie case
    33: dload_2             // floorValue
    34: d2i
    35: ireturn
    36: dload_2             // floorValue
    37: d2i
    38: istore        6     // lower
    40: iload         6
    42: iconst_1
    43: iand                // lower & 1
    44: ifne          52    // if odd
    47: iload         6
    49: goto          56    // return lower (even)
    52: iload         6
    54: iconst_1
    55: iadd                // return lower + 1
    56: ireturn
```

**Behavior**: Ties-to-even rounding (banker's rounding). Matches source exactly. CONFIRMED_PARITY

### 2.2 power (MathUtil.class lines 58-94)

```bytecode
static double power(double, double);
  Code:
     0: dload_0              // base
     1: dconst_0
     2: dcmpg
     3: ifgt          8     // base > 0?
     6: dconst_0
     7: dreturn             // return 0
     8: dload_2              // exponent
     9: dconst_0
    10: dcmpl
    11: ifne          16    // exponent != 0?
    14: dconst_1
    15: dreturn             // return 1
    16: dload_0              // base
    17: invokestatic  #17   // logApprox
    20: dload_2              // exponent
    21: dmul                 // logApprox(base) * exponent
    22: dstore        4     // x
    24: dload         4
    26: ldc2_w        #12   // double 20.0
    29: dcmpl
    30: ifle          38    // x <= 20?
    33: ldc2_w        #12   // double 20.0
    36: dstore        4     // clamp to 20
    38: dload         4
    40: ldc2_w        #4    // double -20.0
    43: dcmpg
    44: ifge          52    // x >= -20?
    47: ldc2_w        #4    // double -20.0
    50: dstore        4     // clamp to -20
    52: dload         4
    54: invokestatic  #16   // expApprox
    57: dreturn
```

### 2.3 logApprox (lines 96-150)

Series approximation using (value-1)/(value+1) with Taylor expansion up to 19 terms, plus exponent reconstruction.

### 2.4 expApprox (lines 152-182)

Taylor series up to 18 terms: 1 + x + x^2/2! + x^3/3! + ... + x^18/18!

**Clamping**: Input to expApprox clamped to [-20, 20] range.

---

## 3. Theoretical Value Table: Level 1-50

Using the source formula with MathUtil approximations (not standard Math.pow). Values computed from algorithm analysis — NOT runtime verified.

| Level | Source Formula | Expected Result | Notes |
|-------|---------------|-----------------|-------|
| 0 | level <= 1 | 79 | Hardcoded branch |
| 1 | level <= 1 | 79 | Hardcoded branch (comment: 79 EXP for level 1 -> 2) |
| 2 | roundEven(power(8,2.1)) - roundEven(power(4,2.1)) | UNKNOWN | power(8,2.1) = expApprox(2.1 * logApprox(8)) |
| 3 | roundEven(power(12,2.1)) - roundEven(power(8,2.1)) | UNKNOWN | |
| 4 | roundEven(power(16,2.1)) - roundEven(power(12,2.1)) | UNKNOWN | |
| 5 | roundEven(power(20,2.1)) - roundEven(power(16,2.1)) | UNKNOWN | |
| 6 | roundEven(power(24,2.1)) - roundEven(power(20,2.1)) | UNKNOWN | |
| 7 | roundEven(power(28,2.1)) - roundEven(power(24,2.1)) | UNKNOWN | |
| 8 | roundEven(power(32,2.1)) - roundEven(power(28,2.1)) | UNKNOWN | |
| 9 | roundEven(power(36,2.1)) - roundEven(power(32,2.1)) | UNKNOWN | |
| 10 | roundEven(power(40,2.1)) - roundEven(power(36,2.1)) | UNKNOWN | |
| 11-20 | roundEven(power(4(l+1),2.1)) - roundEven(power(4l,2.1)) | UNKNOWN | Progressive increase |
| 21-30 | same | UNKNOWN | Super-linear growth |
| 31-40 | same | UNKNOWN | |
| 41-50 | same | UNKNOWN | |

### 3.1 Computation Method

To compute exact values, one would need to:
1. Execute logApprox(base) series (19-term Taylor)
2. Multiply by 2.1
3. Clamp to [-20, 20]
4. Execute expApprox(x) series (18-term Taylor)
5. Apply roundEven (banker's rounding)
6. Subtract consecutive levels

Cannot be done statically without executing the approximation algorithms. Standard Math.pow would give different results due to:
- logApprox/expApprox approximations vs native implementation
- 20.0 clamping boundary
- Banker's rounding vs standard rounding

### 3.2 Precision Risk Assessment

| Risk | Likelihood | Impact | Evidence |
|------|------------|--------|----------|
| Off-by-one at level boundaries | MEDIUM | Player gets level slightly early/late | roundEven ties-to-even + approximation error |
| Negative/zero expNeed | LOW | Infinite level loop | Guard value < 1 ? 1 : value present |
| Accumulation error at high levels | MEDIUM | Exponential deviation from intended curve | Clamping at +/-20 limits growth |
| Level 1->2 mismatch | NONE | N/A | Hardcoded 79, no floating-point |

---

## 4. Call Sites (Bytecode References)

| Call Site | Offset | Context |
|-----------|--------|---------|
| RunWorld constructor (reset) | 58 / 1246 | Initial expNeed = requiredForLevel(1) |
| selectOffer() after applyOffer | 4873 / 200 | expNeed = requiredForLevel(playerLevel) after level++ |
| collectPickup() (EXP pickup) | 6964 / 1868 | While loop for multiple level-ups in one pickup |

All call sites use invokespecial #584 — correct for private instance method.

---

## 5. Edge Cases Analysis

| Edge Case | Source Behavior | Bytecode Behavior | Verified |
|-----------|-----------------|-------------------|----------|
| level = 0 | level <= 1 -> 79 | iload_1; iconst_1; if_icmpgt -> 79 | CONFIRMED |
| level = -1 | level <= 1 -> 79 | Same as level 0 | CONFIRMED |
| level = 1 | level <= 1 -> 79 | Hardcoded branch | CONFIRMED |
| level = 2 | Floating-point formula | Floating-point formula | CONFIRMED_PARITY |
| level = Integer.MAX_VALUE | Formula (may overflow double) | Same clamping at +/-20 | UNKNOWN |
| power(4*level, 2.1) > exp(20) | Clamped to exp(20) | Clamped at bytecode offset 33 | CONFIRMED |

---

## 6. Classification Summary

| Aspect | Classification | Evidence |
|--------|---------------|----------|
| Level <= 1 behavior | CONFIRMED_SOURCE_BEHAVIOR + CONFIRMED_BYTECODE_BEHAVIOR = CONFIRMED_PARITY | Hardcoded 79 in both |
| Level >= 2 formula | CONFIRMED_SOURCE_BEHAVIOR + CONFIRMED_BYTECODE_BEHAVIOR = CONFIRMED_PARITY | Algorithm match |
| roundEven algorithm | CONFIRMED_PARITY | Banker's rounding match |
| power approximation | CONFIRMED_PARITY | logApprox + expApprox + clamping match |
| Actual numeric values (level 2-50) | INSUFFICIENT_EVIDENCE | Cannot compute approximations statically |
| Off-by-one risk at high levels | POTENTIAL_BUG | Approximation + clamping + rounding chain |
| Level 1->2 = 79 EXP | CONFIRMED_PARITY + CONFIRMED_SOURCE_BEHAVIOR | Hardcoded, not formula |

---

## 7. Next Verification Steps

1. Static computation: Implement logApprox/expApprox/roundEven in a test harness (Java SE) to generate expected table
2. JAR runtime trace: Not permitted in this phase
3. Boundary testing: Level 2, 3, 4, 5, 10, 20, 50 — compare computed vs intended progression curve
4. Design intent: Confirm 79 EXP for level 1->2 is intentional baseline
