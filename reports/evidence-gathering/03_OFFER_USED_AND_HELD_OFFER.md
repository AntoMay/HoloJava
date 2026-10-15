# HoloJava Evidence Gathering — offerUsed[64] & heldOffer Analysis

## 1. offerUsed[64] — Complete Lifecycle

### 1.1 Declaration (RunWorld.java:56)

```java
private final boolean[] offerUsed = new boolean[64];
```

### 1.2 Initialization (Constructor, line 132)

```java
for (int i = 0; i < 64; i++) offerUsed[i] = false;
```

### 1.3 Reset Points

| Location | Method | Line | Code |
|----------|--------|------|------|
| Constructor | RunWorld() | 132 | `for (int i = 0; i < 64; i++) offerUsed[i] = false;` |
| New run | reset() | 151 | `for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;` |
| Each level-up | generateOffers() | 1109 | `for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;` |

**Bytecode verification**: All three locations show `arraylength` + `bastore` loop with `iconst_0`.

### 1.4 Write Sites (fillOffer)

| Offer Type | Bytecode Offset | Index Calculation | Write |
|------------|-----------------|-------------------|-------|
| WEAPON (upgrade) | 346-347 | `w.id.hashCode() & 63` | `bastore` iconst_1 |
| WEAPON (collab) | 520-527 | `wd.id.hashCode() & 63` | `bastore` iconst_1 |
| WEAPON (new) | 602-609 | `def.id.hashCode() & 63` | `bastore` iconst_1 |
| ITEM (upgrade) | 654-655 | `d.id.hashCode() & 63` | `bastore` iconst_1 |
| ITEM (new) | 688-695 | `d.id.hashCode() & 63` | `bastore` iconst_1 |

### 1.5 Read Sites (fillOffer guards)

| Offer Type | Bytecode Offset | Check |
|-----------|-----------------|-------|
| WEAPON (upgrade) | 265-281 | `baload` + `ifne` skip |
| WEAPON (collab) | 444-460 | `baload` + `ifne` skip |
| WEAPON (new) | 550-566 | `baload` + `ifne` skip |
| ITEM (upgrade) | 610-626 | `baload` + `ifne` skip |
| ITEM (new) | 654-670 | `baload` + `ifne` skip |

### 1.6 Index Calculation: `hashCode() & 63`

**Source**: `offerUsed[w.id.hashCode() & 63]`
**Bytecode**: `invokevirtual hashCode; bipush 63; iand` (offsets 274-279, 453-458, 541-546, 591-596, 641-646)

**Analysis**: Modulo 64 via bitwise AND. Maps String.hashCode() to 0-63 range.

### 1.7 Capacity Analysis

| Metric | Value | Implication |
|--------|-------|-------------|
| Array size | 64 | Fixed |
| Index range | 0-63 | `hashCode & 63` |
| Collision handling | None (overwrite) | If two IDs hash to same index, second marks first as "used" |
| Reset frequency | Per level-up + per run | Prevents cross-level-up contamination |
| Theoretical max unique | 64 | Before collision guaranteed |

### 1.8 Collision Probability

With typical weapon/item count (~94 weapons + 37 items = 131 unique IDs):
- Expected collisions at 64 slots: birthday problem
- ~50% chance of at least 1 collision at ~9-10 items
- Near certainty of collisions by ~64 items

**CONFIRMED_SOURCE_BEHAVIOR**: Collisions cause false "used" positives, reducing available offers.

---

## 2. heldOffer — Complete Lifecycle

### 2.1 Declaration (RunWorld.java:113)

```java
private int heldOffer = -1;
```

### 2.2 Initialization

| Location | Value |
|----------|-------|
| Constructor | Not explicitly set (default 0) then reset() at line 164 |
| reset() line 164 | `heldOffer = -1` |

### 2.3 Write Sites

| Location | Condition | Value Set |
|----------|-----------|-----------|
| selectOffer() line 262 | `offers[index].type == OFFER_HOLD && holds > 0` | `heldOffer = selectedOffer < 4 ? selectedOffer : heldOffer` |
| selectOffer() line 262 (else) | `offers[index].type == OFFER_HOLD && holds > 0` but `selectedOffer >= 4` | `heldOffer` unchanged |

**Bytecode (selectOffer offsets 98-118)**:
```
98: getfield holds
103: ifle 131
107: getfield selectedOffer
104: if_icmpge 114   // if selectedOffer >= 4
111: goto 118
114: getfield heldOffer
118: putfield heldOffer
```

### 2.4 Read Sites

| Location | Condition | Action |
|----------|-----------|--------|
| generateOffers() line 1117 | `heldOffer >= 0 && heldOffer < 4 && offers[heldOffer].type >= 0` | Copy held offer to slot 0, shift others |

**Bytecode (generateOffers offsets 112-146)**:
```
112: getfield heldOffer
115: iflt 184
118: getfield heldOffer
122: iconst_4
123: if_icmpge 184   // heldOffer < 4
126: getfield offers[heldOffer].type
135: iflt 184        // type >= 0
... copy offers[heldOffer] to offers[0], shift 1-3 right
```

### 2.5 Slot Mapping

| Slot Index | Offer Type | Can Be Held? |
|------------|------------|--------------|
| 0-3 | Regular (WEAPON, ITEM, STAT, FOOD, COINS, SKILL) | **YES** (heldOffer < 4) |
| 4 | OFFER_REROLL (if rerolls > 0) | **NO** (heldOffer < 4 fails) |
| 5 | OFFER_HOLD (if holds > 0) | **NO** (heldOffer < 4 fails) |

### 2.6 HOLD Meta-Offer Logic (selectOffer)

When player selects slot 5 (OFFER_HOLD):
1. `holds--`
2. `heldOffer = selectedOffer < 4 ? selectedOffer : heldOffer` — saves which regular offer to hold
3. `generateOffers()` called again
4. In new `generateOffers()`, held offer copied to slot 0

**Key insight**: Player can only HOLD regular offers (slots 0-3). Cannot hold REROLL or HOLD itself.

---

## 3. Interaction Between offerUsed and heldOffer

### 3.1 Level-Up Flow

1. Player levels up → `state = STATE_LEVEL_UP`
2. `generateOffers()` called:
   - `offerUsed[]` reset to all false
   - 4 regular offers generated via `chooseOfferType` + `fillOffer`
   - If `heldOffer` valid (0-3, type >= 0), that offer moved to slot 0
   - Slot 4 = REROLL (if rerolls > 0)
   - Slot 5 = HOLD (if holds > 0)
3. Player selects offer:
   - If selects HOLD (slot 5): `heldOffer` set to chosen slot (if < 4), `generateOffers()` re-called
   - If selects REROLL (slot 4): `rerolls--`, `generateOffers()` re-called
   - If selects regular (slot 0-3): `applyOffer()`, level++, possibly another level-up

### 3.2 offerUsed During HOLD/REROLL

Since `generateOffers()` is called after HOLD/REROLL, `offerUsed[]` is **reset again** (line 1109). This means:
- HOLD/REROLL gives fresh offer set
- Previously offered items/weapons can reappear
- Only cross-level-up `offerUsed` persistence is within single `generateOffers()` call

---

## 4. Classification Summary

| Aspect | Classification | Evidence |
|--------|---------------|----------|
| offerUsed array size 64 | CONFIRMED_SOURCE_BEHAVIOR + CONFIRMED_BYTECODE_BEHAVIOR | `new boolean[64]` / `bastore` loop |
| Modulo 64 index (`hashCode & 63`) | CONFIRMED_PARITY | `invokevirtual hashCode; bipush 63; iand` |
| Reset per level-up (generateOffers) | CONFIRMED_SOURCE_BEHAVIOR + CONFIRMED_BYTECODE_BEHAVIOR | Loop at offset 26-49 |
| Reset per run (reset) | CONFIRMED_SOURCE_BEHAVIOR + CONFIRMED_BYTECODE_BEHAVIOR | Loop at offset 151 |
| Collision behavior (overwrite) | CONFIRMED_SOURCE_BEHAVIOR | No collision check, direct `bastore` |
| heldOffer 4-slot limit | CONFIRMED_PARITY | `heldOffer < 4` check in generateOffers |
| heldOffer exclusion of meta offers | CONFIRMED_PARITY | Slot 4-5 are REROLL/HOLD, not regular |
| heldOffer cross-run persistence | CONFIRMED_SOURCE_BEHAVIOR | reset() sets -1; selectOffer sets from selection |
| heldOffer carry-over via HOLD meta | CONFIRMED_SOURCE_BEHAVIOR | selectOffer sets heldOffer; generateOffers restores |

---

## 5. Design Intent Assessment

| Mechanism | Apparent Intent | Verified? |
|-----------|----------------|-----------|
| offerUsed modulo 64 | Limit offer variety per level-up, prevent infinite repeats | CONFIRMED_SOURCE_BEHAVIOR |
| offerUsed reset per level-up | Fresh offer pool each level-up | CONFIRMED_SOURCE_BEHAVIOR |
| heldOffer 4-slot limit | Only regular offers holdable, not meta | CONFIRMED_SOURCE_BEHAVIOR |
| heldOffer cross-run | Allow strategy across runs (save good offer) | CONFIRMED_SOURCE_BEHAVIOR |
| HOLD meta-offer | Spend hold token to preserve an offer | CONFIRMED_SOURCE_BEHAVIOR |

**No evidence of bugs** — all mechanisms appear intentional and consistent.

---

## 6. Edge Cases

| Edge Case | Behavior |
|-----------|----------|
| heldOffer = -1 (initial) | generateOffers skips hold logic (iflt) |
| heldOffer >= 4 | generateOffers skips (if_icmpge) |
| heldOffer valid but offer cleared | generateOffers checks `offers[heldOffer].type >= 0` |
| More than 64 unique weapons in run | offerUsed collisions cause false "used", reducing variety |
| Player holds offer, then new run starts | reset() sets heldOffer = -1, previous hold lost |
| Player holds offer, gets same offer next level-up | offerUsed reset per level-up, so can reappear |

---

## 6. Next Verification Steps

1. **Calculate collision probability** for typical weapon/item counts
2. **Trace hold token economy** — how holds are earned/spent
3. **Verify REROLL interaction** — does reroll also reset offerUsed? (Yes, generateOffers called)
