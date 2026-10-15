# HoloJava Post-Audit Review — Finding Validation

## 1. Tabel Validasi Seluruh Temuan

| ID | Temuan Lama | File & Baris | Bukti yang Diperiksa | Klasifikasi Baru | Confidence | Tindakan yang Disarankan |
|----|-------------|--------------|---------------------|------------------|------------|--------------------------|
| AUD-001 | requiredForLevel(1) == 79 (CRITICAL) | RunWorld.java:1270-1277 | Hardcoded `if (level <= 1) return 79;` | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | NO_CHANGE — sudah benar per desain |
| AUD-002 | Floating-point precision requiredForLevel | RunWorld.java:1273-1275 | MathUtil.power + roundEven untuk level > 1 | **POTENTIAL_BUG** | STRONG_EVIDENCE | NEEDS_TEST_DESIGN — test level 2-50 |
| AUD-003 | Bias chooseOfferType() (HIGH) | RunWorld.java:1127-1137 | Weighted distro: Skill 35%, Weapon 35%, Item 25%, Stat 5% | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | NO_CHANGE — desain eksplisit |
| AUD-004 | offerUsed[64] sink capacity (HIGH) | RunWorld.java:56,1164,1109,151 | modulo 64 (hashCode & 63), reset di reset() & generateOffers() | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | NO_CHANGE — desain sengaja |
| AUD-005 | heldOffer 4-slot limit (HIGH) | RunWorld.java:113,1117-1121,262 | heldOffer >= 0 && < 4, slot 4-5 = REROLL/HOLD | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | NO_CHANGE — desain sengaja |
| AUD-006 | offerCount minimum (HIGH) | RunWorld.java:1110,1113-1115 | offerCount = rerolls > 0 ? 6 : 5; fallback fillOffer | **NOT_A_BUG** | CONFIRMED | NO_CHANGE — fallback handle it |
| AUD-007 | heldOffer reset antar-run (MEDIUM) | RunWorld.java:164,1117 | heldOffer = -1 di reset(), carry-over hanya generateOffers() | **NOT_A_BUG** | CONFIRMED | NO_CHANGE — sudah benar |
| AUD-008 | statOfferCounts[6] overflow (MEDIUM) | RunWorld.java:54,1242 | int[] size 6, increment per STAT pick, no cap | **CONFIRMED_DESIGN_LIMIT** | STRONG_EVIDENCE | DEFER — level >1000 tidak realistis |
| AUD-009 | weaponLevels[i] < maxLevel (MEDIUM) | RunWorld.java:1233 | Math.min(current.maxLevel, weaponLevels[slot] + 1) | **NOT_A_BUG** | CONFIRMED | NO_CHANGE — Math.min mencegah |
| AUD-010 | itemLevels[i] < maxLevel (MEDIUM) | RunWorld.java:1238 | Math.min(current.maxLevel, itemLevels[slot] + 1) | **NOT_A_BUG** | CONFIRMED | NO_CHANGE — Math.min mencegah |
| AUD-011 | nextSpawnAt/nextWaveAt (LOW) | RunWorld.java:96-97,396-405 | nextSpawnAt used in updateSpawns(), nextWaveAt updated in updateTimedEvents() | **INSUFFICIENT_EVIDENCE** | UNCONFIRMED | NEED_MORE_EVIDENCE — verify full flow |
| AUD-012 | ringEventDone/hordeEventDone (LOW) | RunWorld.java:98-99,385-392 | Used in updateTimedEvents() for one-time spawnWave | **INSUFFICIENT_EVIDENCE** | UNCONFIRMED | NEED_MORE_EVIDENCE — verify full flow |
| AUD-013 | skillAcquired/skillReadyAt/specialUsed (LOW) | RunWorld.java:105-107,347-380 | skillAcquired set in applyOffer, skillReadyAt in activateSkill, specialUsed in activateSpecial | **INSUFFICIENT_EVIDENCE** | UNCONFIRMED | NEED_MORE_EVIDENCE — verify full flow |
| AUD-014 | Struktur data (INFO) | RunWorld.java:29-32 | 4 pool constants, arrays match | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | INFO only |
| AUD-015 | Konstanta sempurna (INFO) | Viewport.java, RunWorld.java | 320x240, 40ms tick, pool sizes | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | INFO only |
| AUD-016 | Lapisan arsitektur (INFO) | 7 files | 7 layers clearly separated | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | INFO only |
| AUD-017 | Dependensi tidak siklus (INFO) | Init order | MIDlet -> Canvas -> World -> Catalog -> Assets | **CONFIRMED_DESIGN_LIMIT** | CONFIRMED | INFO only |

## 2. Ringkasan Perubahan Klasifikasi

### Downgrade dari CRITICAL/HIGH:
- AUD-001: CRITICAL → CONFIRMED_DESIGN_LIMIT (hardcoded 79, bukan bug)
- AUD-003: HIGH → CONFIRMED_DESIGN_LIMIT (weighted distro, bukan bias)
- AUD-004: HIGH → CONFIRMED_DESIGN_LIMIT (modulo 64 sink, design choice)
- AUD-005: HIGH → CONFIRMED_DESIGN_LIMIT (4-slot hold, design choice)
- AUD-006: HIGH → NOT_A_BUG (fallback ensures 5/6 offers)

### Upgrade/Pindah:
- AUD-002: HIGH → POTENTIAL_BUG (precision issue real untuk level > 1)
- AUD-008: MEDIUM → CONFIRMED_DESIGN_LIMIT (no cap, but level >1000 unrealistic)
- AUD-009, AUD-010: MEDIUM → NOT_A_BUG (Math.min protects)
- AUD-007: MEDIUM → NOT_A_BUG (reset works correctly)
- AUD-011, AUD-012, AUD-013: LOW → INSUFFICIENT_EVIDENCE (need more code review)

## 3. Temuan Duplikat

| Duplicate Group | IDs | Resolution |
|-----------------|-----|------------|
| requiredForLevel issues | AUD-001, AUD-002 | Merge: AUD-001=design limit (level<=1), AUD-002=potential bug (level>1) |
| offerUsed/heldOffer | AUD-004, AUD-005, AUD-007 | Related but distinct: AUD-004=sink, AUD-005=hold limit, AUD-007=reset logic |

## 4. Temuan yang Membutuhkan Investigasi Lebih Lanjut

1. **INSUFFICIENT_EVIDENCE (3)**: nextSpawnAt/nextWaveAt, ringEventDone/hordeEventDone, skillAcquired/skillReadyAt/specialUsed — need full method trace in RunWorld.update()
2. **JAR Parity UNCERTAIN (3)**: Damage formula, AI decision, Progression balancing — need runtime/bytecode analysis
3. **SOURCE_ONLY areas (8)**: Level-up offers, Save/Load RMS, Hardcore mode, Stage unlock, Collab prerequisites — need bytecode verification

## 5. Rekomendasi Prioritas

| Prioritas | Temuan | Alasan |
|-----------|--------|--------|
| **P0** | `requiredForLevel` precision (level > 1) | Floating-point off-by-one risk, real gameplay impact |
| **P1** | `chooseOfferType` distribusi aktual | Design limit, tapi perlu test distribusi real |
| **P1** | `offerUsed[64]` sink boundary | Design limit, tapi perlu test >64 weapon |
| **P2** | INSUFFICIENT_EVIDENCE items | Need full code review before classification |
| **P3** | Magic numbers, comments, documentation | Low risk, maintainability only |
