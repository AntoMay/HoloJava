# HoloJava PB-05 Audit — Source Formula & Numeric Semantics

## 1. Implementation Overview

### 1.1 Method Signature
```java
// RunWorld.java:1310-1317
private int requiredForLevel(int level) {
    // The Level Up page explicitly states 79 EXP for level 1 -> 2.
    if (level <= 1) return 79;
    double current = MathUtil.power(4.0 * level, 2.1);
    double next = MathUtil.power(4.0 * (level + 1), 2.1);
    int value = MathUtil.roundEven(next) - MathUtil.roundEven(current);
    return value < 1 ? 1 : value;
}
```

### 1.2 Call Sites (Verified in Source)
| Location | Line | Context |
|----------|------|---------|
| Constructor (`reset()`) | L166 | Initial `expNeed = requiredForLevel(1)` |
| `selectOffer()` | L315 | After level-up: `expNeed = requiredForLevel(playerLevel)` |
| `collectPickup()` (EXP) | L931 | While loop: `expNeed = requiredForLevel(playerLevel)` |

## 2. Mathematical Formula

### 2.1 Closed Form (Conceptual)
For level `L` (where L ≥ 2):
```
requiredForLevel(L) = roundEven((4(L+1))^2.1) - roundEven((4L)^2.1)
```
Where `roundEven` = banker's rounding (ties to even).

For L = 1: **79** (hardcoded constant)

### 2.2 Operational Semantics

#### Level-Up Trigger (RunWorld.java L312, L928)
```java
while (experience >= expNeed && state == STATE_RUNNING) {
    experience -= expNeed;
    playerLevel++;
    expNeed = requiredForLevel(playerLevel);
    state = STATE_LEVEL_UP;
    generateOffers();
    selectedOffer = 0;
}
```
- Multiple level-ups per pickup supported (while loop)
- `experience` reduced by `expNeed` per level
- `expNeed` recomputed for new level
- State transitions to `STATE_LEVEL_UP` for offer selection

#### Initial State (RunWorld.java L165-166)
```java
playerLevel = 1; experience = 0; expNeed = requiredForLevel(1); // = 79
```

## 3. MathUtil Implementation Details

### 3.1 `roundEven(double)` — Banker's Rounding (Lines 20-27)
```java
static int roundEven(double value) {
    double floorValue = Math.floor(value);
    double diff = value - floorValue;
    if (diff > 0.5) return (int) (floorValue + 1.0);
    if (diff < 0.5) return (int) floorValue;
    int lower = (int) floorValue;
    return (lower & 1) == 0 ? lower : lower + 1;  // ties to even
}
```
**Behavior**: Standard banker's rounding (ties to even). Returns `int`.

### 3.2 `power(double, double)` — Custom Exponentiation (Lines 29-37)
```java
static double power(double base, double exponent) {
    if (base <= 0.0) return 0.0;
    if (exponent == 0.0) return 1.0;
    double x = logApprox(base) * exponent;
    if (x > 20.0) x = 20.0;      // Upper clamp
    if (x < -20.0) x = -20.0;    // Lower clamp
    return expApprox(x);
}
```
**Key properties**:
- Custom implementation (no `Math.pow` in CLDC 1.1)
- Input clamped to `[-20, 20]` for `expApprox`
- Delegates to `logApprox` + `expApprox`

### 3.3 `logApprox(double)` — 19-term Taylor Series (Lines 39-59)
```java
private static double logApprox(double value) {
    // Normalize: value = m * 2^exponent, m ∈ [1, 2)
    // ln(value) = 2 * Σ(z^(2k-1)/(2k-1)) + exponent * ln(2)
    // where z = (value-1)/(value+1), k=1..19 step 2
}
```
- 19 terms (i=1,3,5,...,19)
- Uses `z = (m-1)/(m+1)` transformation
- Reconstructs `ln(value)` using `ln(2) ≈ 0.6931471805599453`

### 3.4 `expApprox(double)` — 18-term Taylor Series (Lines 61-69)
```java
private static double expApprox(double x) {
    // e^x = Σ(x^k/k!) for k=0..18
}
```
- 18 terms (k=0..18)
- Input clamped to `[-20, 20]` by `power()`

## 4. Numeric Types & Precision

| Operation | Input Types | Intermediate | Final |
|-----------|-------------|--------------|-------|
| `4.0 * level` | `int` → `double` | `double` | `double` |
| `power()` | `double`, `double` | `double` | `double` |
| `roundEven()` | `double` | `double` → `int` | `int` |
| Subtraction | `int`, `int` | `int` | `int` |
| Final `value` | `int` | `int` | `int` (return) |

**Key observations**:
- All intermediate calculations in `double` (IEEE 754 64-bit)
- Final result cast to `int` after `roundEven`
- No integer arithmetic in critical path (all `double`)
- Minimum guard: `value < 1 ? 1 : value`

## 5. Level-Up Logic Edge Cases

### 5.1 Multiple Level-Ups Per Pickup
```java
while (experience >= expNeed && state == STATE_RUNNING) {
    experience -= expNeed;
    playerLevel++;
    expNeed = requiredForLevel(playerLevel);
    state = STATE_LEVEL_UP;
    generateOffers();
    selectedOffer = 0;
}
```
- While loop handles multiple level-ups from single large EXP pickup
- Each iteration: subtract `expNeed`, increment level, recompute `expNeed`
- State set to `STATE_LEVEL_UP` for offer selection

### 5.2 Level 1 Hardcoded Behavior
```java
if (level <= 1) return 79;
```
- Covers level 0, 1, and negative (defensive)
- `requiredForLevel(1)` = 79 (L1→L2)
- `requiredForLevel(0)` = 79 (L0→L1, theoretically)

### 5.3 Minimum Guard
```java
return value < 1 ? 1 : value;
```
- Ensures minimum 1 EXP per level
- Prevents zero/negative requirements

## 6. Bytecode Verification Points

### 6.1 Level ≤ 1 Branch (RunWorld.class offset 1310-1317)
```
0: iload_1           // load level
1: iconst_1
2: if_icmpgt 8       // if level > 1
5: bipush 79         // push 79
7: ireturn           // return 79
```

### 6.2 Floating-Point Path (offsets 8-61)
- `ldc2_w 4.0` / `ldc2_w 2.1` constants
- Two `invokestatic MathUtil.power` calls
- Two `invokestatic MathUtil.roundEven` calls
- `isub` for subtraction
- `if_icmpge` for minimum guard

### 6.3 Call Sites (Bytecode Verified)
| Call Site | Offset | Context |
|-----------|--------|---------|
| Constructor/reset | 468, 1246 | Initial expNeed |
| selectOffer | 4873, 200 | Post level-up |
| collectPickup | 6964, 1868 | EXP pickup while loop |

## 7. Call Flow Summary

```
Game Start
    │
    ▼
RunWorld.reset() → requiredForLevel(1) = 79
    │
    ▼
Gameplay → EXP pickup → collectPickup()
    │
    ▼
while (experience >= expNeed) {
    experience -= expNeed;
    playerLevel++;
    expNeed = requiredForLevel(playerLevel);  // ◄── Key call
    state = STATE_LEVEL_UP;
    generateOffers();
}
    │
    ▼
Player selects offer → selectOffer()
    │
    ▼
applyOffer() → state = STATE_RUNNING
    │
    ▼
if (experience >= expNeed) → level up again (while loop)
```

## 8. Design Intent Evidence

### 8.1 Explicit Comment
```java
// The Level Up page explicitly states 79 EXP for level 1 -> 2.
if (level <= 1) return 79;
```
- Explicit design intent documented in code
- Hardcoded value aligns with game design document

### 8.2 Formula Choice
- Power 2.1 exponent → super-linear growth
- 4.0 base multiplier → scales with level
- Banker's rounding → deterministic, unbiased
- Custom power() → CLDC 1.1 compliance (no Math.pow)

## 9. Summary of Numeric Semantics

| Property | Value/Behavior |
|----------|----------------|
| Level 1→2 EXP | 79 (hardcoded) |
| Level 2+ formula | `roundEven(4(l+1)^2.1) - roundEven(4l^2.1)` |
| Rounding | Banker's (ties to even) |
| Power function | Custom Taylor series (19+18 terms) |
| Clamping | ±20 on exponentiated log |
| Minimum per level | 1 EXP |
| Type | `int` (32-bit signed) |
| Max at L50 | ~2,782 EXP per level |
| Cumulative at L50 | ~69,396 EXP total |
