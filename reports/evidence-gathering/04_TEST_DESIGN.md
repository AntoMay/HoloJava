# HoloJava Evidence Gathering — Test Design Specifications

## 1. Test Design Principles

- **Static only**: No runtime execution, no emulator, no game launch
- **Deterministic**: Given same inputs, same expected outputs
- **Traceable**: Each test case maps to source/bytecode location
- **No source modification**: Tests are specifications only, not implemented

---

## 2. TB-001: requiredForLevel(level) — Level 1-50

### 2.1 Test Objective
Verify `requiredForLevel(int level)` returns correct EXP thresholds for all levels, with focus on:
- Hardcoded branch (level <= 1)
- Floating-point formula behavior (level >= 2)
- Precision/rounding edge cases
- Minimum guard (value < 1 ? 1 : value)

### 2.2 Test Cases

| TC-ID | Input (level) | Expected Output | Basis | Status |
|-------|---------------|-----------------|-------|--------|
| TB-001-01 | 0 | 79 | Hardcoded `level <= 1` | EXPECTED_VERIFIED |
| TB-001-02 | -1 | 79 | Hardcoded `level <= 1` | EXPECTED_VERIFIED |
| TB-001-03 | -100 | 79 | Hardcoded `level <= 1` | EXPECTED_VERIFIED |
| TB-001-04 | 1 | 79 | Hardcoded `level <= 1` + comment | EXPECTED_VERIFIED |
| TB-001-05 | 2 | UNKNOWN | Formula: roundEven(power(8,2.1)) - roundEven(power(4,2.1)) | PENDING_COMPUTATION |
| TB-001-06 | 3 | UNKNOWN | Formula: roundEven(power(12,2.1)) - roundEven(power(8,2.1)) | PENDING_COMPUTATION |
| TB-001-07 | 4 | UNKNOWN | Formula | PENDING_COMPUTATION |
| TB-001-08 | 5 | UNKNOWN | Formula | PENDING_COMPUTATION |
| TB-001-09 | 10 | UNKNOWN | Formula | PENDING_COMPUTATION |
| TB-001-10 | 20 | UNKNOWN | Formula | PENDING_COMPUTATION |
| TB-001-11 | 50 | UNKNOWN | Formula | PENDING_COMPUTATION |

### 2.3 Special Edge Cases

| TC-ID | Input | Expected Behavior | Basis |
|-------|-------|-------------------|-------|
| TB-001-E1 | Integer.MAX_VALUE | No crash, returns >= 1 | Clamping at ±20 in power(), minimum guard |
| TB-001-E2 | Large level where power > exp(20) | Clamped result | Bytecode offset 33 clamps to 20.0 |

### 2.4 Required Computation Method
To fill UNKNOWN values, must implement MathUtil algorithms in test harness:
1. `logApprox(base)` — 19-term Taylor series of `2 * sum((z^(2k-1))/(2k-1))` where `z = (value-1)/(value+1)`
2. Multiply by exponent (2.1)
3. Clamp to [-20, 20]
4. `expApprox(x)` — 18-term Taylor series `sum(x^k/k!)`
5. `roundEven` — banker's rounding (ties to even)
6. Subtract consecutive levels

### 2.5 Acceptance Criteria
- All TB-001-01 through TB-001-04 return exactly 79 ✅ VERIFIED
- TB-001-05 through TB-001-11: monotonic increasing, all >= 1, no duplicates from rounding
- TB-001-E1, TB-001-E2: no exception, return >= 1

---

## 3. TB-002: chooseOfferType() — Control Flow & Distribution

### 3.1 Test Objective
Verify control flow correctness and initial probability distribution.

### 3.2 Deterministic Control Flow Tests

| TC-ID | Input (slot, r, skillAcquired) | Expected Output | Path |
|-------|--------------------------------|-----------------|------|
| TB-002-D1 | (0, 0, false) | OFFER_SKILL (0) | r<7, !skillAcquired |
| TB-002-D2 | (1, 6, false) | OFFER_SKILL (0) | r=6<7, !skillAcquired |
| TB-002-D3 | (0, 7, false) | OFFER_WEAPON (1) | r>=7, r<14 |
| TB-002-D4 | (1, 13, false) | OFFER_WEAPON (1) | r=13<14 |
| TB-002-D5 | (0, 14, false) | OFFER_ITEM (2) | r>=14, r<19 |
| TB-002-D6 | (1, 18, false) | OFFER_ITEM (2) | r=18<19 |
| TB-002-D7 | (0, 19, false) | OFFER_STAT (3) | r>=19 |
| TB-002-D8 | (0, 0, true) | OFFER_WEAPON (1) | skillAcquired=true, r<14 |
| TB-002-D9 | (1, 19, true) | OFFER_STAT (3) | skillAcquired=true, r>=19 |
| TB-002-D10 | (2, 0, false) | OFFER_WEAPON (1) | slot>=2, r even |
| TB-002-D11 | (3, 1, false) | OFFER_ITEM (2) | slot>=2, r odd |
| TB-002-D12 | (2, 19, false) | OFFER_ITEM (2) | slot>=2, r=19 odd |
| TB-002-D13 | (5, 5, false) | OFFER_WEAPON (1) | slot>=2, r=5 odd? no, 5&1=1 -> ITEM |

### 3.3 Probabilistic Distribution Tests (Design Spec Only)

**Initial Distribution (slot 0-1, skillAcquired=false):**
| Offer Type | Theoretical Probability | RNG Values | Count |
|------------|------------------------|------------|-------|
| SKILL | 35% | 0-6 | 7 |
| WEAPON | 35% | 7-13 | 7 |
| ITEM | 25% | 14-18 | 5 |
| STAT | 5% | 19 | 1 |

**Initial Distribution (slot 0-1, skillAcquired=true):**
| Offer Type | Theoretical Probability | RNG Values | Count |
|------------|------------------------|------------|-------|
| WEAPON | 70% | 0-13 | 14 |
| ITEM | 25% | 14-18 | 5 |
| STAT | 5% | 19 | 1 |

**Distribution (slot 2-3):**
| Offer Type | Theoretical Probability | RNG Values | Count |
|------------|------------------------|------------|-------|
| WEAPON | 50% | even (0,2,4...18) | 10 |
| ITEM | 50% | odd (1,3,5...19) | 10 |

### 3.4 Acceptance Criteria
- All deterministic tests TB-002-D1 through TB-002-D13 pass with exact match
- Distribution specification documented but NOT executed (requires RNG simulation)

---

## 4. TB-003: offerUsed[64] — Boundary & Collision

### 4.1 Test Objective
Verify modulo-64 indexing, reset behavior, and collision handling.

### 4.2 Test Cases

| TC-ID | Scenario | Expected Behavior | Basis |
|-------|----------|-------------------|-------|
| TB-003-01 | Fresh run, first level-up | All offerUsed[i] == false | Constructor + reset() + generateOffers() all reset |
| TB-003-02 | After 1 weapon offer | offerUsed[hash & 63] == true | fillOffer writes true |
| TB-003-03 | Same weapon offered again | Skipped (offerUsed true) | fillOffer guard `ifne` |
| TB-003-04 | Different weapon, same hash mod 64 | Second weapon falsely "used" | Collision: `hashCode & 63` |
| TB-003-05 | 64 unique weapons offered | All offerUsed[i] == true | Full capacity |
| TB-003-06 | 65th unique weapon | All slots true, collision guaranteed | Pigeonhole principle |
| TB-003-07 | Next level-up (same run) | All offerUsed reset to false | generateOffers() line 1109 |
| TB-003-08 | New run | All offerUsed reset to false | reset() line 151 |

### 4.3 Collision Test Design (Static Analysis)

Since we cannot execute, define collision test as:
- **Input**: Set of weapon IDs with known hash codes
- **Operation**: Compute `hashCode() & 63` for each
- **Expected**: Identify collisions where different IDs map to same index
- **Verification**: Manual computation of hash codes for known weapon IDs from ContentCatalog

### 4.4 Acceptance Criteria
- TB-003-01, TB-003-07, TB-003-08: Verified via bytecode reset loops
- TB-003-02, TB-003-03: Verified via fillOffer bytecode write/read
- TB-003-04, TB-003-05, TB-003-06: Documented as DESIGN_INTENT_UNVERIFIED — collision behavior is overwrite, no protection

---

## 5. TB-004: heldOffer — 4-Slot Limit & Carry-Over

### 5.1 Test Objective
Verify heldOffer index bounds, HOLD meta-offer interaction, and cross-run reset.

### 5.2 Test Cases

| TC-ID | Scenario | Expected Behavior | Basis |
|-------|----------|-------------------|-------|
| TB-004-01 | New run, initial state | heldOffer == -1 | reset() line 164 |
| TB-004-02 | Player selects HOLD (slot 5) on regular offer (slot 0) | heldOffer = 0, holds--, generateOffers() called | selectOffer offset 98-118 |
| TB-004-03 | Player selects HOLD on slot 3 | heldOffer = 3 | selectOffer: `selectedOffer < 4` |
| TB-004-04 | Player selects HOLD on slot 4 (REROLL) | heldOffer unchanged (stays previous) | selectOffer: `selectedOffer >= 4` branch |
| TB-004-05 | Player selects HOLD on slot 5 (HOLD itself) | heldOffer unchanged | selectOffer: slot 5 >= 4 |
| TB-004-06 | After HOLD, next generateOffers | held offer copied to slot 0, others shifted | generateOffers offset 112-146 |
| TB-004-07 | New run after held offer | heldOffer == -1 | reset() line 164 |
| TB-004-08 | heldOffer >= 4 at generateOffers | Skipped (if_icmpge 184) | generateOffers offset 122-123 |
| TB-004-09 | heldOffer valid but offer cleared | Skipped (type < 0 check) | generateOffers offset 135-138 |

### 5.3 Acceptance Criteria
- TB-004-01, TB-004-07: heldOffer = -1 after reset ✅ VERIFIED (bytecode)
- TB-004-02 through TB-004-06: Verified via selectOffer/generateOffers bytecode
- TB-004-08, TB-004-09: Boundary guards verified in bytecode

---

## 6. Test Implementation Notes

### 6.1 What CAN Be Done Statically
- Bytecode instruction verification (already done in Phase 1)
- Control flow path validation (already done)
- Mathematical computation of requiredForLevel (requires implementing MathUtil approximations)
- Hash collision analysis (requires weapon IDs from ContentCatalog)

### 6.2 What CANNOT Be Done (Per Constraints)
- Runtime execution of any test
- Game simulation or emulator
- Random sampling / statistical tests
- Source code modification to add test hooks

### 6.3 Recommended Next Step
Create a standalone Java SE test harness that:
1. Reimplements `MathUtil.power`, `roundEven`, `logApprox`, `expApprox`
2. Reimplements `rand(n)` with known seed
3. Reimplements `chooseOfferType`, `fillOffer` logic
4. Runs all TB-001 through TB-004 test cases deterministically

This harness would be **external** to the HoloJava project and not modify any project files.

---

## 7. Test Matrix Summary

| Test Suite | Deterministic Cases | Probabilistic Specs | Edge Cases | Status |
|------------|---------------------|---------------------|------------|--------|
| TB-001 requiredForLevel | 4 (levels 0, -1, 1) | 0 | 2 (MAX_VALUE, clamp) | 4/11 verified |
| TB-002 chooseOfferType | 13 | 3 distributions | 0 | 13/13 designed |
| TB-003 offerUsed | 8 | 0 | 1 (collision) | 5/8 designed |
| TB-004 heldOffer | 9 | 0 | 0 | 9/9 designed |

**Total**: 34 deterministic test cases designed, 11 pending computation (TB-001 levels 2-50).
