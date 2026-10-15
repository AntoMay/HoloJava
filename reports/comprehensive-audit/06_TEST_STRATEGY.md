# HoloJava Comprehensive Audit - Test Strategy (REVIEW VERSION)

## 1. Pengujian Deterministik Tanpa Runtime Game

Meskipun emulator & JAR dilarang dijalankan, berikut strategi pengujian yang dapat dirancang berdasarkan analisis statis:

### 1.1 Invariant Utama yang Dapat Diuji Statis

| ID | Invariant | Verifikasi Statis | Kesulitan |
|----|-----------|------------------|-----------|
| T1 | Pool tidak melebihi kapasitas | `enemies.length <= ENEMY_POOL (48)` selama loop update | Tinggi |
| T2 | `offerCount` 5 atau 6 | `offerCount = rerolls > 0 ? 6 : 5` | Tinggi |
| T3 | `selectedOffer` round-robin | `selectedOffer = (selectedOffer + delta + offerCount) % offerCount` | Tinggi |
| T4 | `offerUsed[64]` tidak melebihi 63 | `offerUsed[w.id.hashCode() & 63]` | Tinggi |
| T5 | `playerLevel` naik 1 per level-up | `playerLevel++` setelah `applyOffer()` | Tinggi |
| T6 | `expNeed` dihitung ulang | `expNeed = requiredForLevel(playerLevel)` | Tinggi |

### 1.2 Code-Based Test Cases (Desain saja, tidak diimplementasikan)

#### Teskes T1: Pool Exhaustion
- **Prosedur**: Simulasi loop update hingga enemies.length mendekati 48
- **Expected**: enemies ke-49 digantikan/dilegasi, tidak ada IndexOutOfBoundsException
- **Deterministik**: Ya, berdasarkan kondisi `if (i < poolSize)` di setiap iterasi

#### Teskes T2: Level-Up Offer Count
- **Prosedur**: Trigger level-up, count offer yang muncul
- **Expected**: 5 offer (tanpa rerolls) atau 6 (dengan rerolls)
- **Deterministik**: Ya, berdasarkan `rerolls > 0 ? 6 : 5`

#### Teskes T3: Offer Navigation
- **Prosedur**: Dari level-up, panggil `moveOffer(1)` 6x, verify `selectedOffer` wrap-around
- **Expected**: `selectedOffer` kembali ke index 0 setelah 6 langkah
- **Deterministik**: Ya, berdasarkan formula modulo

#### Teskes T4: Offer Used Tracking
- **Prosedur**: Trigger level-up berulang hingga 64 weapon unik terpakai
- **Expected**: `offerUsed[hash & 63]` true untuk 64 weapon berbeda; weapon ke-65 tidak muncul
- **Deterministik**: Ya, berdasarkan tracking hashCode modulo

#### Teskes T5: Level-Up Progression
- **Prosedur**: Dari level 1, dapatkan 79 EXP, trigger level-up, verify level = 2
- **Expected**: `playerLevel` naik 1, `expNeed = requiredForLevel(2)`
- **Deterministik**: Ya, berdasarkan `requiredForLevel(1)` == 79 dan `playerLevel++`

#### Teskes T6: Offer Type Probabilities
- **Prosedur**: Jalankan level-up 100 kali, catat tipe tiap offer
- **Expected**: Distribusi mengikuti probabilitas `chooseOfferType()` (skill > weapon > item > stat)
- **Deterministik**: Ya (bias probabilistik), butuh sample size besar

### 1.2 Test Harness Desain (tanpa Modify Source)

Jika harness test diizinkan (tanpa mengubah source produksi):
1. **Test Harness Eksternal**: Buat file test terpisah di `/tmp/` yang import class HoloJava (jika izin diberikan)
2. **Mock Object**: Desain mock untuk `AssetLibrary`, `SaveStore` untuk unit test
3. **Input Simulator**: Desain simulator kunci D-pad/FIRE/5/9 event stream
4. **Output Verifier**: Desain verifier untuk mencetak state kritis setelah operasi

**Catatan**: Test harness ini butuh persetujuan dan tidak boleh memodifikasi file produksi `/home/antomay/Projects/HoloJava/src/`.

## 2. Coverage Gaps

Subsystem yang belum diliputi unit test (tanpa executor):
- `requiredForLevel()` floating-point precision
- `chooseOfferType()` probabilistik distro
- `fillOffer()` setiap cabang (weapon/item/stat/food/coins)
- `applyOffer()` setiap tipe (weapon/item/stat/food/coins/skill)
- Logika `heldOffer` carry-over antar-run
- `statOfferCounts` overflow boundary

## 2. Prioritas Desain Pengujian

| Prioritas | Area | Waktu Estimasi | Dependensi |
|-----------|------|----------------|------------|
| Tinggi | T1–T5 (invariant utama) | 1–2 hari | Hanya analisis statis |
| Menengah | T6 (distro probabilistik) | 2–3 hari | Sample size >100 level-up |
| Rendah | Semua edge case (overflow, border case) | 3–5 hari | Verifikasi tambahan |

## 3. Kriteria Berhasi Pengujian

- [ ] Setiap invariant T1–T5 terverifikasi dengan sample size minimal 100 iterasi
- [ ] Tidak ada `ArrayIndexOutOfBoundsException` dalam simulasi pool
- [ ] `offerCount` selalu 5 atau 6
- [ ] `selectedOffer` navigasi round-robin bekerja
- [ ] `offerUsed` tracking bekerja sesuai desain
- [ ] `playerLevel` naik konsisten sesuai `requiredForLevel()`

## 3. Batasan

- Pengujian hanya berupa desain/analisis statis
- Tidak ada executor game (emulator/JAR)
- Test harness harus mendapat persetujuan jika mengimpor class produksi
- Tidak ada modifikasi file sourceproduk

