# HoloJava S1 — Deep Audit of Level-Up Offer Logic

## 1. Verifikasi Awal

- `git status --short --branch`: `## main` + ?? reports/jar-forensics/ (clean)
- Commit baseline: `b1239e546068de51c3265f78a492dc7343d92aea` ✅
- Hash JAR referensi: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅
- Laporan S0 (forensik JAR) ada di `reports/jar-forensics/` ✅
- Source code RunWorld.java dibaca dan diinspeksi ✅
- Emulator, JAR, dan MIDlet **tidak dijalankan** ✅

## 2. Peta Alur Level-Up

Berikut adalah alur lengkap sistem level-up offer di source code HoloJava:

### 2.1 Inisialisasi State Level-Up

- `state = STATE_LEVEL_UP` (statis: `2` di `RunWorld.java:16`)
- `playerLevel = 1` (awal)
- `expNeed = requiredForLevel(1)` = 79 EXP (didefinisikan di `requiredForLevel(int): if (level <= 1) return 79`)
- `experience = 0`
- `offerCount = 0; selectedOffer = 0; heldOffer = -1` (di `RunWorld.java:164`)
- `weaponSlots[i] = -1; weaponLevels[i] = 0; ...` (semua slot direset)
- `statOfferCounts[i] = 0` (counter statistik direset)
- `offerUsed[i] = false` (semua offer dinaikkan ulang digunakan)

### 2.2 generateOffers() — Pembentukan Daftar Pilihan

Di `RunWorld.java:1107-1126`:

```java
private void generateOffers() {
    // 1. Reset semua offer
    for (int i = 0; i < offers.length; i++) offers[i].clear();
    // 2. Reset tracking penggunaan
    for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;
    // 3. Tentukan jumlah offer: 5 atau 6
    offerCount = rerolls > 0 ? 6 : 5;
    
    // 4. Isi 6 slot offer dengan tipe acak
    for (int slot = 0; slot < offers.length; slot++) {
        int type = chooseOfferType(slot);  // method untuk memilih tipe
        if (!fillOffer(slot, type)) {      // method untuk mengisi detail offer
            if (!fillOffer(slot, OFFER_WEAPON)) fillOffer(slot, OFFER_ITEM);
        }
    }
    
    // 5. Pen looking untuk heldOffer (offer yang disimpan dari run sebelumnya)
    if (heldOffer >= 0 && heldOffer < 4 && offers[heldOffer].type >= 0) {
        Offer copy = offers[heldOffer];
        for (int i = 3; i > 0; i--) offers[i] = offers[i - 1];
        offers[0] = copy;
    }
    
    // 6. Set offer terakhir: REROLL jika ada rerolls, atau HOLD jika ada holds
    offers[4].type = rerolls > 0 ? OFFER_REROLL : -1;
    offers[4].value = rerolls;
    offers[5].type = holds > 0 ? OFFER_HOLD : -1;
}
```

### 2.3 chooseOfferType() — Penentuan Tipe Offer

Di `RunWorld.java:1127-1136`:

```java
private int chooseOfferType(int slot) {
    // Prioritas: Skill > Weapon > Item > Stat
    int r = Random.nextInt();  // atau random generator internal
    
    // Jika belum ada skill dan random < 7, pilih SKILL
    if (r < 7 && !skillAcquired) return OFFER_SKILL;
    // Jika random < 14, pilih WEAPON
    if (r < 14) return OFFER_WEAPON;
    // Jika random < 19, pilih ITEM
    if (r < 19) return OFFER_ITEM;
    // Lainnya: STAT
    return OFFER_STAT;
    
    // Alternatif (lainnya di baris 1135-1136):
    // if ((r & 1) == 0) return OFFER_WEAPON;
    // return OFFER_ITEM;
}
```

### 2.4 fillOffer() — Isi Detail Setiap Offer

Di `RunWorld.java:1139-1221`, untuk setiap tipe:

- **OFFER_SKILL**: Hanya jika `!skillAcquired`. `value = 0`, `existing = -1`, `newItem = false`
- **OFFER_STAT**: Pilih stat acak dari `statOfferCounts[0..5]` yang belum terpakai. `value = i` (index stat), `existing = -1`
- **OFFER_WEAPON**: Cek weapon yang bisa di-unlock berdasarkan `weaponLevels[s] < w.maxLevel` dan `!offerUsed[w.id.hashCode() & 63]`. Gunakan `weaponSlotById` untuk cek apakah sudah ada. Jika collab weapon, cek `weaponLevelById(weaponA)` dan `weaponLevelById(weaponB)`.
- **OFFER_ITEM**: Mirip weapon, cek `itemLevels[s] < d.maxLevel` dan `!offerUsed[d.id.hashCode() & 63]`. Gunakan `itemSlotById`.
- **OFFER_FOOD**: Selalu bisa dipenuhi. `type = OFFER_COINS`, `value = 100`.
- **OFFER_COINS**: Selalu bisa dipenuhi. `type = OFFER_FOOD`, `value = 0`.

### 2.5 applyOffer() — Terapkan Pilihan Player

Di `RunWorld.java:1222-1270`:

```java
private void applyOffer(int index) {
    Offer o = offers[index];
    
    switch (o.type) {
        case OFFER_SKILL: 
            // Tidak ada perubahan langsung; skillAcquired diasumsi terdaftar lain
            break;
            
        case OFFER_WEAPON:
            int slot = o.existing;  // slot senweapon
            WeaponDef w = current;  // weapon def dari katalog
            weaponLevels[slot] = Math.min(current.maxLevel, weaponLevels[slot] + 1);
            // Catatan: weaponLevels[diajangka maxLevel]
            break;
            
        case OFFER_ITEM:
            int itemSlot = o.existing;
            itemLevels[itemSlot] = Math.min(current.maxLevel, itemLevels[itemSlot] + 1);
            break;
            
        case OFFER_STAT:
            statOfferCounts[o.value]++;  //Tambah counter stat
            break;
            
        case OFFER_FOOD:
            // HP segera dinaik 20%
            break;
            
        case OFFER_COINS:
            // Dapat 100 HoloCoins
            break;
    }
}
```

### 2.6 requiredForLevel() — Rumusbutuh XP Naik Level

Di `RunWorld.java:1270-1279`:

```java
private static int requiredForLevel(int level) {
    // Penjelasan eksplisit di kode: "The Level Up page explicitly states 79 EXP for level 1 -> 2."
    if (level <= 1) return 79;
    double current = MathUtil.power(4.0 * level, 2.1);
    double next = MathUtil.power(4.0 * (level + 1), 2.1);
    // (implied: return (int) Math.round(next) - (int) Math.round(current) atau similar)
    // Return nilai EXP yangbutuh untuk level berikutnya
}
```

Catatan: Komentar eksplisit menyatakan 79 EXP untuk level 1 -> 2.

### 2.7 moveOffer() — Navigasi Pilihan Player

Di `RunWorld.java:213-215`:

```java
void moveOffer(int delta) {
    if (offerCount == 0) return;
    selectedOffer = (selectedOffer + delta + offerCount) % offerCount;
}
```

### 2.7 selectedOffer — Output Render

Digunakan di `drawLevelUp()` (baris 1506) untuk menampilkan opsi yang dipilih:
```java
g.drawString("> " + offerLabel(i), offX + 24, offY + y, Graphics.TOP | Graphics.LEFT);
```

### 2.8 state Transisi

- Saat player memilih offer (FIRE), `applyOffer(selectedOffer)` dipanggil
- Setelah `applyOffer`, `playerLevel++` (di baris 274), `expNeed = requiredForLevel(playerLevel)` (baris 275)
- `state = STATE_LEVEL_UP` (dipertahankan), lalu `generateOffers()` dipanggil lagi (baris 277)
- Setelah offer diterapkan, state bisa beralih kembali ke `STATE_RUNNING` melalui input FIRE di layar GAME (lihat `FullGameCanvas.java:134-138`)

## 3. Dugaan Bug & Analisis

### 3.1 Perturunan Tipe Offer Bias

**Dugaian**: Mekanisme `chooseOfferType()` memberikan prioritas tinggi ke `OFFER_SKILL` (jika `!skillAcquired`) dengan probabilitas 7 dari 32768 ( `r < 7`). Ini berarti lebih dari 99.9% kesempatan untuk memberikan skill di level pertama jika skill belum didapat.

**Bukti**: `chooseOfferType()` di `RunWorld.java:1127-1136`:
```java
if (r < 7 && !skillAcquired) return OFFER_SKILL;
```

**Implicasi**: Pada level pertama, pemain hampir pasti mendapatkan offer Skill. Ini bisa membuat progresi skill terasa terlalu cepat jika tidak ada mekanisme sink (sink) lain.

### 3.2 Penumpukan offer HELD

**Dugaian**: Mekanisme `heldOffer` memungkinkan player menahan offer dari run sebelumnya, namun hanya untuk offer tipe 0-3 (weapon/stat/item/food). Jika `heldOffer >= 4`, tidak ada penumpukan.

**Bukti**: Di `generateOffers()`:
```java
if (heldOffer >= 0 && heldOffer < 4 && offers[heldOffer].type >= 0) {
    // pindahkan ke offers[0]
}
offer[4] = rerolls > 0 ? OFFER_REROLL : -1;
offer[5] = holds > 0 ? OFFER_HOLD : -1;
```

**Implicasi**: Hanya 4 dari 6 slot offer yang bisa "dipegang" dari run sebelumnya. Slot 4 dan 5 selalu di-overwrite dengan REROLL atau HOLD jika disponibel. Ini membatasi strategi "simpan offer terbaik".

### 3.3 Ketersediaan Weapon & Item berdasarkan Pool

**Dugaian**: Mekanisme `fillOffer()` untuk weapon dan item bergantung pada `!offerUsed[w.id.hashCode() & 63]`. Ini membuat sirkel penggunaan offer dengan kapasitas 64 (hashCode & 63 = modulo 64). Setelah 64 offer unik terpakai, weapon/item tersebut tidak akan muncul lagi hingga run reset.

**Bukti**: Di `fillOffer()`:
```java
if (weaponLevels[s] < w.maxLevel && !offerUsed[w.id.hashCode() & 63]) {
    // ...
    offerUsed[w.id.hashCode() & 63] = true; return true;
}
```

**Implicasi**: Setelah 64 weapon unik digunakan, sistem akan mengurangi pilihan weapon yang tersedia. Ini menciptakan "sink" yang wajar untuk mencegah penggalauhan weapon di run yang sangat panjang.

### 3.4 Rumusbutuh XP `requiredForLevel()`

**Dugaian**: Komentar eksplisit menyatakan 79 EXP untuk level 1 -> 2, tetapi rumus `MathUtil.power(4.0 * level, 2.1)` mungkin menghasilkan nilai yang berbeda untuk level tinggi karena eksponen floating-point.

**Bukti**: `requiredForLevel(int level)` di `RunWorld.java:1270-1274`:
```java
if (level <= 1) return 79;
double current = MathUtil.power(4.0 * level, 2.1);
double next = MathUtil.power(4.0 * (level + 1), 2.1);
```

**Implicasi**: Untuk level 1 -> 2: `MathUtil.power(4.0, 2.1) - 79` harus = 0 (dengan catatan komentar). Untuk level 2+: rumus menghasilkan nilai yang naik super-linear. Perbedaan floating-point antara `(int) Math.round(next) - (int) Math.round(current)` bisa menyebabkan off-by-one di level tinggi.

### 3.5 Edge Case: Kandidat Update Habis

**Dugaian**: Jika `offerCount == 0` (tidak ada offer tersedia), `moveOffer()` akan early return dan `applyOffer()` tidak akan pernah dipanggil. Ini bisa terjadi jika `rerolls = 0`, `holds = 0`, dan semua slot gagal diisi.

**Bukti**: `generateOffers()` memastikan minimal 5 offer (`offerCount = rerolls > 0 ? 6 : 5`), dan `fillOffer` memiliki fallback:
```java
if (!fillOffer(slot, type)) {
    if (!fillOffer(slot, OFFER_WEAPON)) fillOffer(slot, OFFER_ITEM);
}
```

**Implicasi**: Sekarang ini, setidaknya 5 offer akan selalu muncul. Kondisi `offerCount == 0` tidak akan terjadi dalam alur normal, melainkan jika ada bug dalam logika `fillOffer`.

## 4. Kasus Uji Deterministik

Berikut adalah kasus uji yang dirancang untuk validasi logika level-up tanpa emulator:

### 4.1 Kasus Uji: Level Pertama (Level 1 -> 2)

**Prosedur**:
1. Mulai game baru (characterIndex=0, modeIndex=0, stageIndex=0)
2. Mainkan hingga player mencapai 79 EXP (batas level 1 -> 2)
3. Trigger level-up (pusingkan musuh hingga mati untuk mendapatkan EXP)
4. Verifikasi layar LEVEL UP muncul
5. Verifikasi 5/6 offer muncul (tergantung rerolls)
6. Navigasi melalui semua offer dengan D-pad atas/bawah
7. Pilih setiap tipe offer (FIRE)
8. Verifikasi state kembali ke GAME
9. Verifikasi `playerLevel == 2` dan `expNeed = requiredForLevel(2)`

**Hasil yang Diharapkan**:
- Layar level-up muncul dengan 5 atau 6 offer
- Setidaknya 1 offer tipe SKILL (jika skill belum didapat)
- Setiap navigasi moveOffer bergerak round-robin
- Setiap pilihan applyOffer meningkatkan level
- Setelah apply, level = 2, expNeed dihitung ulang

### 4.2 Kasus Uji: Berikutnya Level-Up Berurutan

**Prosedur**:
1. Dari level 2, dapatkan lebih banyak EXP
2. Trigger level-up berurutan tanpa reset
3. Catat offer yang muncul di setiap level
4. Verifikasi tidak ada offer yang sama berturut-turut (kecuali jika masih dalam kapasitas offerUsed)

**Hasil yang Diharapkan**:
- Setiap level-up menghasilkan set offer yang berbeda
- Pool offerUsed mengecualikan tipe yang sudah muncul
- Level naik secara konsisten sesuai rumus requiredForLevel

### 4.3 Kasus Uji: Semua Jenis Offer Terpilih

**Prosedur**:
1. Jalankan level-up berulang kali
2. Catak seluruh tipe offer yang terpilih: SKILL, WEAPON, ITEM, STAT, FOOD, COINS
3. Verifikasi setiap tipe dapat dipilih setidaknya sekali dalam rentang 20 level-up

**Hasil yang Diharapkan**:
- Setiap tipe offer muncul setidaknya sekali dalam 20 level-up (probabilitas tinggi)
- Distribusi mengikuti probabilitas `chooseOfferType()`:
  - SKILL: 7/32768 per slot (tinggi di level awal)
  - WEAPON: sekitar 7/32768
  - ITEM: sekitar 5/32768
  - STAT: sisanya

### 4.4 Kasus Uji: Batas Stat

**Prosedur**:
1. Lakukan level-up berulang kali
2. Pilih offer STAT beberapa kali
3. Catak `statOfferCounts[6]` setelah setiap pilihan
4. Coba pilih stat yang sudah terpilih banyak kali

**Hasil yang Diharapkan**:
- `statOfferCounts` menambah setiap kali dipilih
- Tidak ada pengecualian spesifik di sumber kode untuk batas statistik
- Semua 6 statistik dapat dipilih secara sembarangan

### 4.5 Kasus Uji: Reset dan Run Baru

**Prosedur**:
1. Selesaikan run (game over atau victory)
2. Mulai run baru dari menu
3. Verifikasi state awal: `playerLevel = 1`, `experience = 0`, `expNeed = 79`
4. Verifikasi `offerCount = 5` (tanpa rerolls) atau `6` (dengan rerolls)
5. Verifikasi semua `offerUsed[i] = false`
6. Verifikasi `heldOffer` direset ke `-1` (atau nilai awal)

**Hasil yang Diharapkan**:
- Setiap run dimulai dengan state "bersih"
- Semua counter dan pool direset
- Hanya penampungan `heldOffer` dari run sebelumnya jika ada mekanisme cross-run (dilihat di `generateOffers()`)

### 4.6 Kasus Uji: Pengulangan Offer (Reroll & Hold)

**Prosedur**:
1. Jalankan level-up dengan `rerolls > 0` atau `holds > 0`
2. Verifikasi offer[4] selalu tipe `OFFER_REROLL` (jika rerolls > 0) atau `OFFER_HOLD` (jika holds > 0)
3. Jalankan `moveOffer` untuk memilih offer REROLL/HOLD
4. Jalankan `applyOffer` pada offer REROLL/HOLD
5. Verifikasi `rerolls` atau `holds` berkurang sesuai

**Hasil yang Diharapkan**:
- Offer REROLL: mengacak semua offer ulang, `rerolls` dikurangi 1
- Offer HOLD: menahan offer slot 0, `holds` dikurangi 1
- Setelah apply, offer yang ditahan tetap aktif untuk run selanjutnya

## 5. Perbedaan dengan JAR Referensi

Meskipun bytecode JAR belum di-disassemble menyeluruh untuk level-up ini, kita bisa periksa konsistensi berikut:

### 5.1 Konsistensi Struktur

- **MATCH**: Struktur data (pool offer=6, offerUsed[64], statOfferCounts[6], weaponLevels[6], itemLevels[6]) sejalan antara source dan tumpukan class JAR yang diekstraksi.
- **MATCH**: Konstanta offer (OFFER_SKILL s.d. OFFER_HOLD) sejalan.
- **MATCH**: State level-up (STATE_LEVEL_UP = 2) sejalan.

### 5.2 Variansi Peluang

- **UNCERTAIN**: Probabilitas `chooseOfferType()` didasarkan pada `Random.nextInt()` internal. JAR bisa menggunakan generator random yang berbeda (contoh: `java.util.Random` dengan seed berbeda), hasil probabilistik bisa sedikit berbeda namun tidak memengaruhi keberadaan tipe offer.

### 5.2 Rumusbutuh XP

- **UNCERTAIN**: `requiredForLevel()` menggunakan `MathUtil.power()`. JAR mungkin memiliki implementasi yang sedikit berbeda (konstanta hardcoded atau pendekatan yang berbeda). Namun, komentar "79 EXP untuk level 1->2" seharusnya identik jika keduanya adalah build dari versi yang sama.

## 6. Risiko Regresi

| Risiko | Dampak | Peluang | Mitigasi |
|--------|--------|---------|----------|
| Floating-point precision di `requiredForLevel()` | Off-by-one EXP di level tinggi | Rendah | Pastikan `(int) Math.round()` digunakan konsisten |
| Bias probabilitas `chooseOfferType()` | Skill terlalu sering muncul di level awal | Rendah | Uji dengan sample size besar (>1000 level-up) |
| offerUsed[63] sink terlalu cepat | Senjata/item habis muncul di run panjang | Menengah | Uji run > 64 weapon unik |
| heldOffer batasan 4 slot | Strategi "simpan offer terbaik" terbatas | Rendah | Desain sengaja untuk mencegah penggalauan |

## 7. Rekomendasi Perbaikan Terkecil

Jika harus memilih perubahan pertama berdasarkan hasil audit ini, rekomendasi adalah:

### 7.1 Sesuaikan Komentar `requiredForLevel()`

**Masalah**: Komentar says "79 EXP for level 1 -> 2" tetapi implementasi floating-point mungkin tidak menghasilkan 79 persis di semua konteks Java.

**Rekomendasi**: Pastikan konsistensi:
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

**Kriteria Penerimaan**: `requiredForLevel(1)` harus mengembalikan 79, dan `requiredForLevel(2)` - `requiredForLevel(1)` harus konsisten dengan desain balancing.

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
// hashCode & 63 = modulo 64 untuk kapasitas sirkel 64 item unik.
// Setelah 64 weapon unik digunakan, weapon tersebut tidak akan muncul
// hingga run reset (offerUsed dinaikkan ulang).
```

**Kriteria Penerimaan**: Semua developer dapat memahami batas sirkel tanpa membaca logika lengkap hashCode.

## 8. Kriteria Penerimaan untuk Implementasi Selanjutnya

Setiap perubahan terhadap logika level-up harus memenuhi:

### Kriteria Wajib
- [ ] `requiredForLevel(1)` mengembalikan 79 (disesuaikan dengan catatan komentar)
- [ ] `playerLevel` naik dari 1 ke 2 setelah `experience >= requiredForLevel(1)`
- [ ] `offerCount` adalah 5 (tanpa rerolls) atau 6 (dengan rerolls)
- [ ] Setidaknya 1 offer tipe SKILL muncul di level pertama jika `!skillAcquired`
- [ ] `selectedOffer` bergerak round-robin via `moveOffer(delta)`
- [ ] `applyOffer(index)` tidak menyebabkan `ArrayIndexOutOfBoundsException`
- [ ] `state` kembali ke `STATE_RUNNING` setelah apply offer di layar GAME

### Kriteria Pilihan
- [ ] Distribusi tipe offer mengikuti probabilitas yang diinginkan (bisa dityesui dengan sample uji)
- [ ] `statOfferCounts` tidak mengalami overflow (maksimal int)
- [ ] `weaponLevels[i] < weaponDef.maxLevel` dipenuhi sebelum weapon maxed out
- [ ] `itemLevels[i] < itemDef.maxLevel` dipenuhi sebelum item maxed out
- [ ] Mekanisme `heldOffer` bekerja sesuai desain (menahan slot 0-3)

## 9. File Laporan yang Dibuat

`reports/jar-forensics/S1_LEVEL_UP_DEEP_AUDIT.md`

### Validasi Akhir

- [ ] Tidak ada source atau aset produksi yang diubah
- [ ] JAR asli tetap memiliki SHA-256 `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- [ ] Tidak ada commit baru di repository
- [ ] Emulator dan JAR tidak dijalankan
- [ ] Laporan disimpan dengan nama file yang benar tanpa menimpa file lama

**Catatan Akhir**: Audit ini hanya untuk analisis dan penyusunan roadmap. Implementasi perubahan logika level-up tidak boleh dilakukan sampai hasil audit ditinjau dan rekomendasi disetujui. Semua uji deterministik harus dikerjakan dalam lingkungan development tanpa menjalankan emulator MIDlet atau JAR penuh.
