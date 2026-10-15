# HoloJava Evidence Gathering — chooseOfferType() Control Flow & Probability Analysis

## 1. Source Code vs Bytecode Comparison

### 1.1 Source Code (RunWorld.java:1127-1137)

```java
private int chooseOfferType(int slot) {
    int r = rand(20);
    if (slot < 2) {
        if (r < 7 && !skillAcquired) return OFFER_SKILL;
        if (r < 14) return OFFER_WEAPON;
        if (r < 19) return OFFER_ITEM;
        return OFFER_STAT;
    }
    if ((r & 1) == 0) return OFFER_WEAPON;
    return OFFER_ITEM;
}
```

### 1.2 Bytecode (RunWorld.class, lines 6270-6323)

```bytecode
private int chooseOfferType(int);
  Code:
     0: aload_0
     1: bipush        20
     3: invokespecial #581                // Method rand:(I)I
     6: istore_2        // r = rand(20)
     7: iload_1         // load slot
     8: iconst_2
     9: if_icmpge     45    // if slot >= 2, goto 45
    12: iload_2         // r
    13: bipush        7
    15: if_icmpge     27    // if r >= 7, goto 27
    18: aload_0
    19: getfield      #388  // skillAcquired
    22: ifne          27    // if skillAcquired, goto 27
    25: iconst_0
    26: ireturn         // return OFFER_SKILL (0)
    27: iload_2         // r
    28: bipush        14
    30: if_icmpge     35    // if r >= 14, goto 35
    33: iconst_1
    34: ireturn         // return OFFER_WEAPON (1)
    35: iload_2         // r
    36: bipush        19
    38: if_icmpge     43    // if r >= 19, goto 43
    41: iconst_2
    42: ireturn         // return OFFER_ITEM (2)
    43: iconst_3
    44: ireturn         // return OFFER_STAT (3)
    45: iload_2         // r
    46: iconst_1
    47: iand            // r & 1
    48: ifne          53    // if odd, goto 53
    51: iconst_1
    52: ireturn         // return OFFER_WEAPON (1)
    53: iconst_2
    54: ireturn         // return OFFER_ITEM (2)
```

### 1.3 Verification: MATCH

| Aspect | Source | Bytecode | Status |
|--------|--------|----------|--------|
| rand(20) call | `rand(20)` | `bipush 20; invokespecial rand` | CONFIRMED_PARITY |
| slot < 2 branch | `if (slot < 2)` | `iload_1; iconst_2; if_icmpge 45` | CONFIRMED_PARITY |
| r < 7 && !skillAcquired | `if (r < 7 && !skillAcquired)` | `iload_2; bipush 7; if_icmpge 27; getfield skillAcquired; ifne 27` | CONFIRMED_PARITY |
| r < 14 -> WEAPON | `if (r < 14)` | `iload_2; bipush 14; if_icmpge 35; iconst_1` | CONFIRMED_PARITY |
| r < 19 -> ITEM | `if (r < 19)` | `iload_2; bipush 19; if_icmpge 43; iconst_2` | CONFIRMED_PARITY |
| else -> STAT | `return OFFER_STAT` | `iconst_3; ireturn` | CONFIRMED_PARITY |
| slot >= 2: even -> WEAPON | `(r & 1) == 0` | `iload_2; iconst_1; iand; ifne 53; iconst_1` | CONFIRMED_PARITY |
| slot >= 2: odd -> ITEM | `return OFFER_ITEM` | `iconst_2; ireturn` | CONFIRMED_PARITY |

**CONCLUSION**: CONFIRMED_PARITY — bytecode matches source exactly.

---

## 2. rand() Implementation

### 2.1 Source (RunWorld.java:1374)

```java
private int rand(int n) { return n <= 1 ? 0 : (random.nextInt() & 0x7fffffff) % n; }
```

### 2.2 Bytecode (RunWorld.class, lines 7705-7720)

```bytecode
private int rand(int);
  Code:
     0: iload_1
     1: iconst_1
     2: if_icmpgt     9
     5: iconst_0
     6: goto          21
     9: aload_0
    10: getfield      #378                // Field random:Ljava/util/Random;
    13: invokevirtual #505                // Method java/util/Random.nextInt:()I
    16: ldc           #16                 // int 2147483647
    18: iand
    19: iload_1
    20: irem
    21: ireturn
```

**Behavior**: Returns 0 for n <= 1. For n > 1: `(random.nextInt() & 0x7fffffff) % n`. Uses `java.util.Random` with seed from constructor: `new Random(0x4A4F4C00L + characterIndex * 977L + stageIndex * 131L)`.

**CONFIRMED_PARITY**.

---

## 3. Probability Analysis (Initial Selection)

### 3.1 Slot 0 and 1 (slot < 2)

RNG range: `rand(20)` returns 0-19 (20 values).

| Condition | RNG Values | Count | Probability | Offer Type |
|-----------|------------|-------|-------------|------------|
| `r < 7 && !skillAcquired` | 0,1,2,3,4,5,6 | 7 | 7/20 = **35%** | OFFER_SKILL (0) |
| `r < 14` (7-13) | 7,8,9,10,11,12,13 | 7 | 7/20 = **35%** | OFFER_WEAPON (1) |
| `r < 19` (14-18) | 14,15,16,17,18 | 5 | 5/20 = **25%** | OFFER_ITEM (2) |
| `r >= 19` (19) | 19 | 1 | 1/20 = **5%** | OFFER_STAT (3) |

**If skillAcquired == true**: First condition skipped, distribution becomes:
| Condition | RNG Values | Count | Probability | Offer Type |
|-----------|------------|-------|-------------|------------|
| `r < 14` | 0-13 | 14 | 14/20 = **70%** | OFFER_WEAPON (1) |
| `r < 19` | 14-18 | 5 | 5/20 = **25%** | OFFER_ITEM (2) |
| `r >= 19` | 19 | 1 | 1/20 = **5%** | OFFER_STAT (3) |

### 3.2 Slot 2 and 3 (slot >= 2)

RNG range: 0-19. Uses parity of `r`:

| Condition | RNG Values | Count | Probability | Offer Type |
|-----------|------------|-------|-------------|------------|
| `(r & 1) == 0` (even) | 0,2,4,6,8,10,12,14,16,18 | 10 | 10/20 = **50%** | OFFER_WEAPON (1) |
| `(r & 1) == 1` (odd) | 1,3,5,7,9,11,13,15,17,19 | 10 | 10/20 = **50%** | OFFER_ITEM (2) |

---

## 4. Effective Distribution After fillOffer() Fallbacks

### 4.1 generateOffers() Logic (RunWorld.java:1107-1125)

```java
offerCount = rerolls > 0 ? 6 : 5;
for (int slot = 0; slot < 4; slot++) {
    int type = chooseOfferType(slot);
    if (!fillOffer(slot, type)) {
        if (!fillOffer(slot, OFFER_WEAPON)) fillOffer(slot, OFFER_ITEM);
    }
}
```

### 4.2 fillOffer() Fallback Chain

If chosen type cannot be filled:
1. Try OFFER_WEAPON
2. If that fails, try OFFER_ITEM
3. If that fails, fillOffer returns false (slot remains empty)

### 4.3 fillOffer() Success Conditions

| Type | Success Condition |
|------|-------------------|
| OFFER_SKILL | `!skillAcquired` |
| OFFER_STAT | Always (weighted pick from 6 stats) |
| OFFER_WEAPON | Has weapon < maxLevel AND not in offerUsed, OR weaponCount < 6 AND new weapon available AND not in offerUsed |
| OFFER_ITEM | Has item < maxLevel AND not in offerUsed, OR itemCount < 6 AND new item available AND not in offerUsed |
| OFFER_FOOD | Always (fallback for ITEM) |
| OFFER_COINS | Always (fallback for WEAPON) |

### 4.4 Effective Distribution Shift

The **initial** chooseOfferType distribution is NOT the **final** offer distribution because:

1. **SKILL** only available once per run (skillAcquired becomes true after first pick)
2. **WEAPON/ITEM** can fail if all owned are maxed or all available are in offerUsed
3. **Fallbacks** convert failed WEAPON -> COINS, failed ITEM -> FOOD
4. **offerUsed[64]** modulo 64 prevents repeats within a level-up set

**Therefore**: The 35/35/25/5 initial distribution is NOT the effective distribution shown to player.

---

## 5. Call Sites and Usage

### 5.1 Call Site (generateOffers)

Bytecode offset 76 in generateOffers: `invokespecial #541` (chooseOfferType)

Called for slots 0, 1, 2, 3 (4 iterations). Slots 4 and 5 set directly:
- Slot 4: OFFER_REROLL if rerolls > 0, else -1
- Slot 5: OFFER_HOLD if holds > 0, else -1

### 5.2 heldOffer Integration (generateOffers lines 1117-1121)

```java
if (heldOffer >= 0 && heldOffer < 4 && offers[heldOffer].type >= 0) {
    Offer copy = offers[heldOffer];
    for (int i = 3; i > 0; i--) offers[i] = offers[i - 1];
    offers[0] = copy;
}
```

If player held an offer from previous run, it's inserted at position 0, shifting others right. The held offer's original type determines what appears in slot 0.

---

## 6. Classification Summary

| Aspect | Classification | Evidence |
|--------|---------------|----------|
| chooseOfferType algorithm | CONFIRMED_PARITY | Bytecode matches source exactly |
| rand(20) implementation | CONFIRMED_PARITY | `(nextInt() & 0x7fffffff) % 20` |
| Slot 0-1 distribution (skillAcquired=false) | CONFIRMED_SOURCE_BEHAVIOR | 35% Skill, 35% Weapon, 25% Item, 5% Stat |
| Slot 0-1 distribution (skillAcquired=true) | CONFIRMED_SOURCE_BEHAVIOR | 70% Weapon, 25% Item, 5% Stat |
| Slot 2-3 distribution | CONFIRMED_SOURCE_BEHAVIOR | 50% Weapon, 50% Item |
| Effective distribution after fillOffer | POTENTIAL_BUG / DESIGN_INTENT_UNVERIFIED | Fallbacks change actual distribution |
| JAR parity for chooseOfferType | CONFIRMED_PARITY | Bytecode match |

---

## 7. Next Verification Steps

1. **Trace fillOffer for all failure paths** — determine when WEAPON/ITEM/STAT fail
2. **Analyze offerUsed collision impact** — how modulo 64 affects availability
3. **Model effective distribution** — simulate with fillOffer logic (static analysis only)
3. **Verify heldOffer carry-over** — trace how held offer affects slot distribution
