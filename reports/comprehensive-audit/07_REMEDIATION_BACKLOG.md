# HoloJava Comprehensive Audit - Remediation Backlog (REVIEW VERSION)

## 1. Prioritas Perbaikan

Berikut temuan dikelompokkan berdasarkan severity dan dependensi:

### P0 - Critical correctness/data integrity

| ID | Area | Masalah | Risiko | Urgensia |
|----|------|---------|--------|----------|
| P0-001 | `requiredForLevel(1)` | Jika bukan 79 EXP, progresi level 1 awal salah | Progresi pemain sesat dari level 1 | **CRITICAL** |
| P0-002 | `offerUsed[64]` sink | Jika >64 weapon unik, weapon berhentinya muncul | Progresi weapon "terkunci" di run panjang | **HIGH** |

### P1 - High: bug terkonfirmasi yang mengganggu alur utama

| ID | Area | Masalah | Risiko | Urgensia |
|----|------|---------|--------|----------|
| P1-003 | `chooseOfferType()` bias | Skill muncul terlalu sering di level pertama (~7/32768 per slot) | Progresi skill terasa tidak seimbang | **HIGH** |
| P1-004 | `heldOffer` batasan 4 slot | Hanya 4 dari 6 slot bisa dipahin dari run sebelumnya | Strategi "simpan offer terbaik" terbatas | **HIGH** |
| P1-005 | `offerCount` minimum | Minimal 5 offer, tapi `fillOffer()` punya fallback yang bisa gagal | `offerCount == 0` kemungkinan jika bug | **HIGH** |

### P2 - Medium: inkonsistensi, risiko lebih kecil

| ID | Area | Masalah | Risiko | Urgensia |
|----|------|---------|--------|----------|
| P2-006 | `heldOffer` reset antar-run | `generateOffers()` memindahkan jika `heldOffer >= 0 && < 4`, tapi asal `heldOffer` apa? | Progres carry-over antar-run tidak konsisten | **MEDIUM** |
| P2-007 | `statOfferCounts` overflow | Jika level > 1000, counter int overflow kemungkinan | Nilai statistik tidak valid | **MEDIUM** |
| P2-008 | `weaponLevels` batasan | `Math.min(current.maxLevel, ...)` tapi `current` variable butuh verifikasi di setiap call site | Weapon naik level melebihı maxLevel (jika bug) | **MEDIUM** |
| P2-009 | `itemLevels` batasan | Sama seperti weapon | Item naik level melebihı maxLevel (jika bug) | **MEDIUM** |
| P2-010 | `nextSpawnAt` / `nextWaveAt` | Variabel ada di baris 96-97 tapi butuh verifikasi di `update()` | Fitur wave/spawn tidak diverifikasi | **MEDIUM** |
| P2-011 | `ringEventDone` / `hordeEventDone` | Variabel baris 98-99 tapi butuh verifikasi menyeluruh | Event special tidak diverifikasi | **MEDIUM** |
| P2-012 | `skillAcquired`, `skillReadyAt`, `specialUsed` | Tri variabel baris 105-107 tapi butuh verifikasi menyeluruh | Kondisi skill/tidak pasti | **MEDIUM** |
| P2-013 | `nextSpawnAt` / `nextWaveAt` | Tracking spawn wave | **LOW** | Dokumentasi |
| P2-014 | Magic numbers | Berbagai angka tetes di kode (79, 40, 24, 48, 128, 96) | Mempermudah pemahaman kode | **LOW** |
| P2-015 | Komentar yang kurang | Beberapa method kurang memiliki komentar yang jelas | Mempermudah pemeliharaan | **LOW** |

### P3 - Low: maintainability, dokumentasi, peluang peningkatan

| ID | Area | Masalah | Risiko | Urgensia |
|----|------|---------|--------|----------|
| P3-001 | Magic numbers | Berbagai angka tetes di kode | Mempermudah pemahaman kode | **LOW** |
| P3-002 | Komentar yang kurang | Beberapa method kurang memiliki komentar yang jelas | Mempermudah pemeliharaan | **LOW** |
| P3-003 | Duplikasi formula | Mungkin ada formula duplikat di kode berbeda | Penjaga konsistensi | **LOW** |

### HOLD: Bukti Belum Cukup

| ID | Area | Alasan | Langkah Selanjutnya |
|----|------|--------|---------------------|
| HOLD-01 | `nextSpawnAt` / `nextWaveAt` full behavior | Butuh verifikasi mendalam di `RunWorld.update()` | Lakukan analisis lintas method |
| HOLD-02 | `skillAcquired` penuh | Butuh verifikasi seluruh penggunaan | Lakukan analisis call graph |
| HOLD-03 | `statOfferCounts` overflow boundary | Butuh sample level >1000 | Uji dengan level tinggi |

## 2. Urutan Pekerjaan yang Disarankan

### Urutan 1: P0 Critical Fixes (Wajib Sebelum Segala Hal)
1. **Verifikasi `requiredForLevel(1)` == 79** — Pastikan komentar benar atau sesuaikan implementasi
2. **Verifikasi `offerUsed[64]` sink capacity** — Uji dengan >64 weaponunik berbeda
3. **Verifikasi `chooseOfferType()` bias** — Uji 100 level pertama, catat kemunculan skill

### Urutan 2: P1 High Fixes (Sebelum Fitur Baru)
4. **Verifikasi `heldOffer` mekanisme** — Test run baru, cek nilai Awal `heldOffer`
5. **Verifikasi `heldOffer` kapasitas 4 slot** — Pastikan logika generateOffers()
6. **Verifikasi `offerCount` minimum** — Test case: semua fillOffer gagal

### Urutan 3: P2 Medium Fixes (Sebelum Content Update)
7. **Verifikasi `statOfferCounts` overflow boundary** — Uji level >1000
8. **Verifikasi `weaponLevels`/`itemLevels` batas** — Verifikasi tiap call site `applyOffer()`
9. **Verifikasi `heldOffer` reset antar-run** — Test run baru dari menu

### Urutan 4: P3 Low (Bisa Ditunda)
10. **Dokumentasi `nextSpawnAt`/`nextWaveAt`** — Catatan untuk pengembang depan
11. **Tambahkan komentar magic numbers** — Mempermudah pemeliharaan
12. **Tingkatkan komentar method** — Standar dokumentasi kode

### Urutan 5: HOLD Items (Tidak Segera)
13. **Lakukan investigasi mendalam** untuk HOLD-01, HOLD-02, HOLD-03 — Butuh analisis lebih lanjut sebelum dibuat keputusan.

## 3. Kandidat Perbaikan Terkecil

Jika harus memilih perubahan pertama berdasarkan hasil audit, rekomendasi adalah:

### 7.1 Ubah Komentar `requiredForLevel()`
**Masalah**: Komentar says "79 EXP for level 1 -> 2" tetapi implementasi floating-point mungkin tidak menghasilkan 79 persis.

**Rekomendasi**: Pastikan konsisten:
```java
private static int requiredForLevel(int level) {
    // Pastikan level 1 -> 2 menghasilkan 79 EXP
    if (level <= 1) return 79;
    // Rumus sisa
    int current = (int) (MathUtil.power(4.0 * level, 2.1);
    int next = (int) (MathUtil.power(4.0 * (level + 1), 2.1);
    return next - current;
}
```

**Kriteria Penerimaan**: `requiredForLevel(1)` harus mengembalikan 79, dan `requiredForLevel(2) - requiredForLevel(1)` harus konsisten dengan desain balancing.

### 7.2 Verifikasi Probabilitas `chooseOfferType()`
**Masalah**: Jika balance skill terlalu cepat terlalu sering di level awal.

**Rekomendasi**: Atur ulang batas probabilitas:
```java
// Ubah dari: if (r < 7 && !skillAcquired)
// Ubah menjadi: if (r < 5 && !skillAcquired) // Kurangi probabilitas skill
// atau: if (r < 10 && !skillAcquired && level > 1) // Hanya skill di level 1
```

**Kriteria Penerimaan**: Distribusi skill di level 1 harus < 60% dari total offer (dari sample uji 50 level-up pertama).

### 7.3 Dokumentasi Batas `offerUsed[64]`
**Rekomendasi**: Tambahkan komentar klarifikasi:
```java
// offerUsed digunakan untuk melacak penggunaan unik weapon/item.
// hashCode & 63 = modulo 64 untuk kapasitas sirkel 64 itemunik.
// Setelah 64 weaponunik digunakan, weapon tersebut tidak akan muncul
// hingga run reset (offerUsed dinaikkan ulang).
```

**Kriteria Penerimaan**: Semua developer dapat memahami batas sirkel tanpa membaca logika lengkap hashCode.

## 3. Risiko Regresi per Perbaikan

| Perbaikan | Risiko Regresi | Mitigasi |
|-----------|---------------|----------|
| `requiredForLevel()` | Rendah | Pastikan `requiredForLevel(1)` == 79 tetap terpenuhi |
| `chooseOfferType()` | Rendah | Pastikan distro skill < 60% di level 1 |
| `offerUsed[64]` | Rendah | Pastikan hanya 64 weaponunik yang bisa didapat sebelum run reset |
| `heldOffer` | Rendah | Pastikan logika carry-over antar-run konsisten |
| `statOfferCounts` overflow | Rendah | Pastikan level >1000 tidak digunakan tanpa verifikasi |

## 4. Daftar File Laporan

- `07_REMEDIATION_BACKLOG.md` — Laporan ini

## 5. Quality Gate

Sebelum menerapkan perbaikan:
- [ ] Setiap temuan P0 memiliki bukti dan lokasi kode
- [ ] Setiap perubahan memiliki kriteria penerimaan yang terdefinisi
- [ ] Tidak ada perubahan yang akan memecah `requiredForLevel(1) == 79`
- [ ] Tidak ada perubahan yang akan memecah `offerUsed` tracking
- [ ] Setiap paket diuji dengan analisis statis sebelum dikonfirmasi
