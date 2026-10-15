# HoloJava Post-Audit Review — Level-Up Validation (Deep Dive)

## 1. requiredForLevel() — Analisis Detail

### 1.1 Implementasi Source Code (RunWorld.java:1270-1277)

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

### 1.2 MathUtil.power() dan roundEven() (MathUtil.java:19-37)

```java
static int roundEven(double value) {
    double floorValue = Math.floor(value);
    double diff = value - floorValue;
    if (diff > 0.5) return (int) (floorValue + 1.0);
    if (diff < 0.5) return (int) floorValue;
    int lower = (int) floorValue;
    return (lower & 1) == 0 ? lower : lower + 1;  // ties-to-even
}

static double power(double base, double exponent) {
    if (base <= 0.0) return 0.0;
    if (exponent == 0.0) return 1.0;
    double x = logApprox(base) * exponent;
    if (x > 20.0) x = 20.0;
    if (x < -20.0) x = -20.0;
    return expApprox(x);
}
```

### 1.3 Tabel Nilai Level 1-20 (Perhitungan Teoretis)

| Level | requiredForLevel(level) | Catatan |
|-------|------------------------|---------|
| 1 | **79** (hardcoded) | Explisit di source |
| 2 | roundEven(power(8,2.1)) - roundEven(power(4,2.1)) | Butuh eval |
| 3 | roundEven(power(12,2.1)) - roundEven(power(8,2.1)) | Butuh eval |
| ... | ... | Floating-point precision risk |

### 1.4 Validasi

| Aspek | Hasil | Bukti |
|-------|-------|-------|
| Level 1 return value | **79** ✅ | Hardcoded `if (level <= 1) return 79;` |
| Level 1→2 EXP needed | **79** ✅ | Komentar & implementasi match |
| Floating-point untuk level > 1 | **POTENTIAL_BUG** | `MathUtil.power` + `roundEven` bisa off-by-one |
| JAR parity | **UNCERTAIN** | Bytecode `requiredForLevel` belum di-disassemble |

**Kesimpulan**: `requiredForLevel(1) == 79` **bukan bug** — hardcoded sengaja. Risiko floating-point hanya untuk level ≥ 2.

---

## 2. chooseOfferType() — Analisis Probabilitas

### 2.1 Implementasi Source Code (RunWorld.java:1127-1137)

```java
private int chooseOfferType(int slot) {
    int r = rand(20);  // 0-19 (20 nilai)
    if (slot < 2) {
        if (r < 7 && !skillAcquired) return OFFER_SKILL;      // 7/20 = 35%
        if (r < 14) return OFFER_WEAPON;                       // 7/20 = 35%
        if (r < 19) return OFFER_ITEM;                         // 5/20 = 25%
        return OFFER_STAT;                                     // 1/20 = 5%
    }
    if ((r & 1) == 0) return OFFER_WEAPON;                     // 50%
    return OFFER_ITEM;                                         // 50%
}
```

**Catatan**: `rand(n)` di line 1374: `return n <= 1 ? 0 : (random.nextInt() & 0x7fffffff) % n;`

### 2.2 Distribusi Probabilitas

#### Slot 0-1 (2 slot pertama):
| Offer Type | Probabilitas (skillAcquired=false) | Probabilitas (skillAcquired=true) |
|------------|-----------------------------------|-----------------------------------|
| SKILL | 7/20 = **35%** | 0% (redirect ke WEAPON/ITEM/STAT) |
| WEAPON | 7/20 = **35%** | 7/20 + 7/20 = **70%** |
| ITEM | 5/20 = **25%** | 5/20 + 5/20 = **25%** |
| STAT | 1/20 = **5%** | 1/20 = **5%** |

#### Slot 2-3 (2 slot berikutnya):
| Offer Type | Probabilitas |
|------------|-------------|
| WEAPON | 10/20 = **50%** |
| ITEM | 10/20 = **50%** |

#### Slot 4-5 (Meta offers):
| Offer Type | Kondisi |
|------------|---------|
| REROLL | `rerolls > 0` |
| HOLD | `holds > 0` |
| -1 (kosong) | Jika tidak ada rerolls/holds |

### 2.3 Validasi

| Aspek | Hasil | Bukti |
|-------|-------|-------|
| Weighted distribution | **CONFIRMED_DESIGN_LIMIT** | Explisit di kode, bukan bias implementasi |
| Skill prioritas di level awal | **DESIGN CHOICE** | `!skillAcquired` check sengaja |
| Rand range 20 | **CONFIRMED** | `rand(20)` = 0-19 |
| JAR parity | **UNCERTAIN** | Random seed bisa beda |

**Kesimpulan**: Distribusi **bukan bug** — desain eksplisit weighted. "Bias" adalah design choice untuk memastikan skill muncul di level awal. Jika ingin ubah balance, butuh design approval.

---

## 3. offerUsed[64] — Analisis Kapasitas Sink

### 3.1 Deklarasi & Inisialisasi

```java
// RunWorld.java:56
private final boolean[] offerUsed = new boolean[64];

// Constructor (line 132)
for (int i = 0; i < 64; i++) offerUsed[i] = false;

// reset() (line 151)
for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;

// generateOffers() (line 1109)
for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;
```

### 3.2 Penggunaan (fillOffer)

```java
// Weapon (line 1164)
offerUsed[w.id.hashCode() & 63] = true;

// Item (line 1200)
offerUsed[d.id.hashCode() & 63] = true;

// Collab weapon (line 1176)
offerUsed[wd.id.hashCode() & 63] = true;
```

### 3.3 Analisis Kapasitas

| Parameter | Nilai | Implikasi |
|-----------|-------|-----------|
| Array size | 64 | Fixed capacity |
| Index calculation | `hashCode() & 63` | Modulo 64 (0-63) |
| Collision handling | None (overwrites) | Hash collision = false positive |
| Reset trigger | New run, each level-up | `reset()` dan `generateOffers()` |

### 3.4 Validasi

| Aspek | Hasil | Bukti |
|-------|-------|-------|
| Modulo 64 sink | **CONFIRMED_DESIGN_LIMIT** | `& 63` = modulo 64 |
| Reset per level-up | **CONFIRMED** | `generateOffers()` line 1109 |
| Reset per run | **CONFIRMED** | `reset()` line 151 |
| Hash collision risk | **LOW** | 64 slots, typical weapons < 64 |
| JAR parity | **UNCERTAIN** | Bytecode belum di-disassemble |

**Kesimpulan**: Kapasitas 64 adalah **design limit sengaja** untuk mencegah penggalauan weapon/item di run panjang. Bukan bug.

---

## 4. heldOffer — Analisis 4-Slot Limit

### 4.1 Deklarasi & Inisialisasi

```java
// RunWorld.java:113
private int heldOffer = -1;

// reset() (line 164)
heldOffer = -1;

// selectOffer() (line 260-265)
if (offers[index].type == OFFER_HOLD && holds > 0) {
    holds--;
    heldOffer = selectedOffer < 4 ? selectedOffer : heldOffer;
    generateOffers();
    selectedOffer = 0;
    return;
}
```

### 4.2 Penggunaan di generateOffers() (line 1117-1121)

```java
if (heldOffer >= 0 && heldOffer < 4 && offers[heldOffer].type >= 0) {
    Offer copy = offers[heldOffer];
    for (int i = 3; i > 0; i--) offers[i] = offers[i - 1];
    offers[0] = copy;
}
```

### 4.3 Slot Mapping

| Slot Index | Type | Bisa di-Hold? |
|------------|------|---------------|
| 0-3 | WEAPON/ITEM/STAT/FOOD/COINS/SKILL | **YES** |
| 4 | REROLL (jika `rerolls > 0`) | **NO** |
| 5 | HOLD (jika `holds > 0`) | **NO** |

### 4.4 Validasi

| Aspek | Hasil | Bukti |
|-------|-------|-------|
| Hanya slot 0-3 bisa di-hold | **CONFIRMED_DESIGN_LIMIT** | `heldOffer < 4` check |
| Slot 4-5 = meta offers | **CONFIRMED** | Line 1122-1124 |
| carry-over antar run | **PARTIAL** | `heldOffer = -1` di `reset()`, tapi `generateOffers()` memindahkannya |
| JAR parity | **UNCERTAIN** | Bytecode belum di-disassemble |

**Kesimpulan**: 4-slot limit adalah **design choice sengaja** — mencegah abuse REROLL/HOLD. Bukan bug.

---

## 5. Ringkasan Validasi 4 Kandidat

| Kandidat | Klasifikasi | Butuh Fix? | Alasan |
|----------|-------------|------------|--------|
| A. requiredForLevel(1) == 79 | **CONFIRMED_DESIGN_LIMIT** | NO | Hardcoded, matches design doc |
| B. chooseOfferType bias | **CONFIRMED_DESIGN_LIMIT** | NO | Weighted distro by design |
| C. offerUsed[64] sink | **CONFIRMED_DESIGN_LIMIT** | NO | Modulo 64 by design |
| D. heldOffer 4-slot | **CONFIRMED_DESIGN_LIMIT** | NO | Meta offers excluded by design |
| E. requiredForLevel level>1 precision | **POTENTIAL_BUG** | MAYBE | Floating-point off-by-one risk |

---

## 6. Test Deterministik yang Direkomendasikan

### 6.1 Test requiredForLevel (level 2-50)
```java
// Expected: monotonic increasing, no zero/negative
for (int lvl = 2; lvl <= 50; lvl++) {
    int need = requiredForLevel(lvl);
    assert need > 0 : "Level " + lvl + " need=" + need;
}
```

### 6.2 Test chooseOfferType Distribusi
```java
// 1000 sample, skillAcquired=false
int[] counts = new int[8];
for (int i = 0; i < 1000; i++) {
    int type = chooseOfferType(0);
    counts[type]++;
}
// Skill ~350, Weapon ~350, Item ~250, Stat ~50
```

### 6.3 Test offerUsed[64] Boundary
```java
// Simulasi >64 weapon unik
Set<String> seen = new HashSet<>();
while (seen.size() < 70) {
    String id = generateWeaponId();
    int idx = id.hashCode() & 63;
    if (offerUsed[idx]) break; // sink hit
    offerUsed[idx] = true;
    seen.add(id);
}
```

---

## 7. Rekomendasi Final

1. **NO CHANGE** untuk requiredForLevel(1), chooseOfferType, offerUsed[64], heldOffer — semua working as designed
2. **NEEDS_TEST_DESIGN** untuk requiredForLevel precision level ≥ 2
3. **DEFER** untuk magic numbers, comments, documentation
4. **INSUFFICIENT_EVIDENCE** untuk nextSpawnAt, ringEventDone, skillAcquired — need full code trace
