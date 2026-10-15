# Audit F — Coin Permanen & Rencana Character Stat Shop

**Status:** READ-ONLY audit. Tidak ada kode, aset, save, konfigurasi, atau build artifact yang
diubah. Tidak ada commit, tidak ada push.
**Tanggal:** 2026-10-15
**Baseline:** `3a8681cd7e7fba40110d69f7b62e3476ca103259` — *Implement Grassy Plains background*,
tracked tree bersih.
**Catatan:** laporan ini **baru** dan tidak mengubah/menimpa laporan yang sudah ada.

---

## 0. Ringkasan Eksekutif

Temuan terpenting:

1. **Sistem drop coin sudah ada lengkap.** Mob dan boss **sudah** menjatuhkan coin sebagai
   pickup (type 1). Aturan desain "mob dapat menjatuhkan coin selain EXP" **sudah terpenuhi**.
2. Yang **belum ada** hanyalah persistensi. `coins` adalah `int` lokal-run di `RunWorld`,
   **tidak pernah** menyentuh `SaveStore`.
3. **K安全和 anti-duplikasi sudah terjamin secara struktural** oleh `p.active = false` yang
   dijalankan lebih dulu di `collectPickup`.
4. Save record **48 byte**, dan `load()` memakai `min(loaded.length, DATA_LEN)` —
  Artinya **memperpanjang `DATA_LEN` kompatibel ke belakang** tanpa mereset data lama.
5. **EXP sudah terpisah** dari coin (field `experience`/`expNeed`, rumus
   `requiredForLevel()`), tidak pernah dipersistensi. Persyaratan #5 sudah terpenuhi.
6. Record sudah **mencadangkan 47 byte karakter** although runtime hanya punya 5 karakter —
   ruang untuk Stat Shop sudah tersedia.
7. **Tidak ada** sistem upgrade permanen sama sekali (`grep` nol hasil).
   `applyStat()` hanya menambah bonus **lokal-run** yang di-reset tiap run.

---

## 1. Struktur SaveStore Saat Ini

`src/java-me-fullgame/me/holojava/fullgame/SaveStore.java`

| Aspek | Nilai | Lokasi |
|---|---|---|
| Store name | `"holojava_fullgame_v1"` | `:10` |
| Record ID | `1` | `:11` |
| Panjang data | `DATA_LEN = 48` byte | `:12` |
| Indeks settings | `SETTINGS_INDEX = 47` | `:13` |
| Legacy | `LEGACY_LEN = 6` byte, 5 stage, 5 karakter | `:14-16` |
| Alokasi byte | `data[0..46]` = **47 byte karakter**, `data[47]` = settings | implisit |

### 1.1 Isi byte karakter (`data[0..46]`)

Satu byte per karakter; **bit `stageOrder` (0..7)** = stage selesai.

```java
boolean isCompleted(int characterIndex, int stageOrder) {      // :26
    if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length
        || stageOrder < 0 || stageOrder > 7) return false;
    int v = data[characterIndex] & 0xff;
    return (v & (1 << stageOrder)) != 0;
}
```

**Perhatikan batas `characterIndex >= CHARACTERS.length`.** Runtime `CHARACTERS.length = 5`
(diuji), padahal save menyediakan 47 byte dan `DOCUMENTED_CHARACTER_COUNT = 47`. Byte 5..46
saat ini **selalu nol dan tidak bisa ditulis** selama roster 5 karakter. Ini ruang yang sudah
dipakai untuk_character stat upgrades nanti.

### 1.2 Byte settings (`data[47]`) — **ada overlap bit**

```java
boolean hardcore()            { return (data[47] & 1)   != 0; }   // :39  bit 0
int     selectedCharacter()   { return data[47] & 0x3f;        }   // :47  bit 0..5
void setSelectedCharacter(int v) {
    int settings = data[47] & 0xc0;                              // :50  hanya bit 6-7 dipertahankan
    data[47] = (byte)(settings | (v & 0x3f));                    // :51  bit 0 dibangun dari index karakter
}
```

Terbukti secara eksekusi (headless probe):

| Aksi | Byte | `hardcore()` | `selectedCharacter()` |
|---|---|---|---|
| `setHardcore(true)` lalu `setSelected(3)` | `0x03` | **1** (bertahan) | 3 |
| `setHardcore(true)` lalu `setSelected(2)` | `0x02` | **0** (hilang) | 2 |

> **Bug lama yang perlu diketahui:** `setSelectedCharacter()` hanya mempertahankan bit 6-7,
> sehingga **bit 0 dibangun ulang dari index karakter**. Memilih karakter berindex **genap**
> akan **menghapus flag hardcore**. Pre-existing, bukan原因は pekerjaan coin — tapi akan
> berinteraksi kalau byte settings nanti dipakai lagi untuk field baru.

### 1.3 Baca (`load()`) dan tulis (`save()`)

```java
private void load() {                                            // :55
    store = RecordStore.openRecordStore(STORE, false);
    byte[] loaded = store.getRecord(RECORD_ID);
    if (loaded != null) {
        if (loaded.length == LEGACY_LEN) {                       // 6 byte -> transpos
            byte[] migrated = transposeLegacyRecord(loaded);
            for (int i = 0; i < DATA_LEN; i++) data[i] = migrated[i];
        } else {
            int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;   // :68
            for (int i = 0; i < count; i++) data[i] = loaded[i];               // :69
        }
    }
    ... catch (RecordStoreException ignored) { }                 // :72/:73  DIABAIKAN
}

private void save() {                                            // :117
    store = RecordStore.openRecordStore(STORE, true);
    try { store.setRecord(RECORD_ID, data, 0, data.length); }
    catch (RecordStoreException ignored) { }                     // :122     DIABAIKAN
    // tidak ada commit(); stub ini menulis langsung              // :123
}
```

Konsekuensi penting:
- **Kegagalan penyimpanan diam-diam.** Tidak adaUmpan balik ke pemanggil.
- Setiap `save()` = **open + write + close** (3 operasi RMS).
- Tidak ada versioning; kompatibilitas ditentukan **panjang record**.

---

## 2. Alur Coin: Drop → Pickup → Saldo → Penyimpanan

### 2.1 Sumber coin yang **sudah ada**

| Sumber | Lokasi | Nilai | Bentuk |
|---|---|---|---|
| Mob | `RunWorld.java:1072` | `rand(def.coinChance) == 0` → **10 coin** | pickup type 1 |
| Boss | `RunWorld.java:765` | `for (i < 5) dropPickup(..., 1, 10, ...)` = **50 coin** | 5 pickup type 1 |
| Victory | `RunWorld.java:767` | `coins += stage.coinClear + defeatCount / 4` | **pemberian langsung, tanpa pickup** |
| Level-up `OFFER_COINS` | `RunWorld.java:1371` | `coins += 100` | **pemberian langsung, tanpa pickup** |

`coinChance` per mob (dari `ContentCatalog.java`, urutan argumen
`enemy(id,name,behavior,hp,atk,speed,cooldown,exp,coinChance,w,h,sprite,documented)`):

| Mob | coinChance | exp | | Mob | coinChance | exp |
|---|---|---|---|---|---|---|
| shrimp | 90 | 7 | | moonafic | 80 | 12 |
| dark_shrimp | 75 | 9 | | moonafic_vip | 60 | 16 |
| sukonbu | 80 | 9 | | nousagi_a | 85 | 12 |
| kuro_sukonbu | 65 | 11 | | nousagi_b | 75 | 15 |
| matsurisu | 85 | 10 | | nousagi_c | 65 | 18 |
| matsurisu3 | 65 | 14 | | nousagi_d | 60 | 20 |
| | | | | otaku | 60 | 20 |

Artinya **1 dari 60–90 kill mob** menghasilkan coin. Pada stage dengan `maxEnemies` 20–40 dan
respawn ~1,2 s, itu kira-kira **satu pickup coin per ~90 detik** per run — cukup rendah
untuk menulis RMS pada tiap pickup tanpa beban berarti.

`stage.coinClear` per stage: stage1=20, stage2=24, stage3=28, stage4=32, stage5=36,
stage1h=26, stage2h=30, stage3h=34, stage4h=38, time1=40.

### 2.2 Penempatan pickup

```java
private void dropPickup(int x, int y, int type, int value, long now) {   // :1165
    if (type == 0) { /* merging hanya untuk EXP, cap 200 */ }             // :1169-1178
    for (...) { p.active = true; p.type = type; p.value = value; p.x = x - 6; p.y = y - 6; p.born = now; return; }
}
```
**Coin (type 1) tidak pernah di-merge** — tiap coin adalah pickup tersendiri.

### 2.3 Pengumpulan

```java
private void updatePickups(long now) {                       // :970
    int radius = 22 + (22 * pickupPercentBonus) / 100;
    for (int i = 0; i < pickups.length; i++) {
        Pickup p = pickups[i];
        if (!p.active) continue;
        if (now - p.born > 30000L) { p.active = false; continue; }   // :976  kadaluarsa 30 detik
        ... magnet ...
        if (p.type == 3 && distSq < 900) collectPickup(p, now);
        else if (distSq <= radius * radius) collectPickup(p, now);
    }
}

private void collectPickup(Pickup p, long now) {              // :1000
    p.active = false;                                          // :1001  DITANDA SEBELUM APA PUN
    if (p.type == 0)      { ... experience ... }
    else if (p.type == 1) { coins += p.value; }                // :1016  <-- satu-satunya handler coin
    else if (p.type == 2) { ... heal ... }
    else if (p.type == 4) { ... weapon level ... }
}
```

**`coins` saat ini:** `private int coins;` (`RunWorld.java:117`), di-reset ke 0 di `:210`,
dibaca accessor `int coins()` (`:309`), ditampilkan di HUD (`:1680`) dan layar Results
(`FullGameCanvas.java:721`). **Tidak pernah** diteruskan ke `SaveStore`.

### 2.4 Pemisahan EXP — sudah terpenuhi

| | EXP | Coin |
|---|---|---|
| Field | `experience`, `expNeed`, `playerLevel` | `coins` |
| Kenaikan | `experience += gained` (`collectPickup`, type 0) | `coins += p.value` |
| Progres | `requiredForLevel(level)` → level-up | tidak ada |
| Dipersistensi | **tidak** | **tidak** |

Keduanya lokal-run dan **sudah terpisah**. Persyaratan #5 sudah terpenuhi tanpa perubahan.

---

## 3. Risiko Kehilangan / Duplikasi Coin

### 3.1 Anti-duplikasi — **sudah aman secara struktural**

- `collectPickup()` menaruh `p.active = false` pada **baris pertama**, sebelum touch nilai apa pun.
- `updatePickups()` melakukan **satu lintasan** per frame; setiap indeks diproses sekali.
- Guard `if (!p.active) continue` di atas mencegah pickup yang sudah dikoleksi diproses lagi.

→ **Tidak ada jalur kode yang bisa memberi coin dua kali untuk satu pickup**, baik dalam satu
frame maupun lintas frame.

### 3.2 Risiko yang **tersisa**

| # | Risiko | Bukti | Dampak |
|---|---|---|---|
| R1 | **Kegagalan `save()` diam-diam** | `SaveStore.java:122` `catch (RecordStoreException ignored) { }` | Saldo bertambah di memori, hilang saat restart. Pemain melihat coin masuk HUD lalu kehilangan semuanya tanpa pesan. |
| R2 | **Pickup tidak terkoleksi hilang** | `:976` `now - p.born > 30000L` | Coin yang jatuh tapi tidak diambil dalam 30 detik hilang. Ini perilaku yang sudah ada. |
| R3 | **Pickup gagal ter-spawn** | `dropPickup()` `:1179-1184` — loop mencari slot `!p.active`; kalau `PICKUP_POOL` (96) penuh, drop **diam-diam dibuang** (`return` tanpa出口) | Coin hilang tanpa jejak saat pool penuh. |
| R4 | **Run berakhir sebelum pickup dikoleksi** | Semua pickup di-reset di `:222` | Sesuai aturan desain #4 (hanya yang *terkoleksi* yang permanen) — perlu dikonfirmasi. |
| R5 | **Reward tanpa pickup** | `:767` dan `:1371` | Jika tidak ikut dipersistensi, pemain melihat "Coins +40" di Results tapi saldo permanen tidak naik. |
| R6 | **Write RMS per pickup** | `save()` = open+write+close | ~1 per 90 detik ⇒ negligible. Akan berbeda bila Stat Shop menulis setiap frame (tidak akan). |
| R7 | **Presisi floating** | Tidak berlaku — semua integer. | — |

### 3.3 Overflow tipe

`int coins` maksimum **2.147.483.647**. CLDC 1.1 menyediakan `long` (8 byte, maksimum
~9,2 kuadrilion) dan codebase sudah memakainya (`startTime`, `invulUntil`, `elapsedSeconds()`).
→ **Rekomendasi: simpan saldo sebagai `long`**, bukan `int`. Tidak ada yang perlu di-cap
pada skala yang masuk akal, dan ini menghilangkan satu kelas bug.

---

## 4. Strategi Kompatibilitas Save Lama

### 4.1 Fakta penentu

`load()` cabang else (`:67-70`):
```java
int count = loaded.length < DATA_LEN ? loaded.length : DATA_LEN;
for (int i = 0; i < count; i++) data[i] = loaded[i];
```
Konstruktor (`:22`) men-nol-kan seluruh `data` **sebelum** `load()`.

Diverifikasi secara aritmetika:

| Panjang record lama | `DATA_LEN` baru = 48 | = 52 | = 56 | = 64 |
|---|---|---|---|---|
| 6 (legacy) | → transpos | → transpos | → transpos | → transpos |
| 47 | salin 47 B, byte 47–47 tetap 0 | salin 47 B, 47–51 tetap 0 | salin 47 B, 47–55 tetap 0 | salin 47 B, 47–63 tetap 0 |
| 48 (saat ini) | salin 48 B | salin 48 B, 48–51 tetap 0 | salin 48 B, 48–55 tetap 0 | salin 48 B, 48–63 tetap 0 |

> **Kesimpulan: memperpanjang `DATA_LEN` kompatibel ke belakang.** Record lama dimuat
> penuh, byte baru tetap nol (saldo awal 0), **tanpa reset dan tanpa kehilangan data**.

### 4.2 Rekomendasi layout

| Opsi | Layout | Catatan |
|---|---|---|
| **A (disarankan)** | `DATA_LEN` 48 → **56**, saldo `long` pada offset 48..55 | Satu field, ruang sisa 0; cukup untuk saldo global |
| B | `DATA_LEN` 48 → **112**: saldo `long` (48–55) + 5 byte/char × 11 stat (56–110) | Kedepan untuk Stat Shop per karakter |
| C | Record terpisah (`RECORD_ID = 2`) | Risiko: `save()` menulis record 1; record 2 butuh jalur tulis sendiri; tanpa transaksi |

**Jangan mengubah nama store ke `..._v2`** — itu akan **membuat record lama tidak pernah
dibaca** dan menganarkannya sebagai progres hilang. Kompatibilitas di sini murni lewat
panjang record.

### 4.3 Serialisasi tanpa API baru

`SaveStore` tidak boleh memakai `DataInput`/`DataOutput`/`ByteBuffer` tanpa memverifikasi
ketersediaannya di stub CLDC 1.1. Pola paling aman dan sudah dipakai proyek: ** encode/decodes
murni di atas `byte[]` dengan aritmetika int**, seperti `transposeLegacyRecord()` sudah
melakukan (`:102-115`). Fungsi statis murni seperti itu **bisa diuji di desktop JVM** tanpa
MIDP — persis pola `tools/tests/.../SaveStoreLegacyMigrationTest.java` (229 checks, PASS
pada audit ini).

---

## 5. Titik Integrasi Character Stat Shop

### 5.1 Struktur karakter saat ini

`ContentCatalog.CharacterDef` — **stat dasar, immutable, shared**:

| Field | Tipe | Contoh (Gura) |
|---|---|---|
| `id`, `name` | String | `gura`, `Gawr Gura` |
| `hp` | int | 65 |
| `atkPercent` | int | 110 |
| `spdPercent` | int | 140 |
| `critPercent` | int | 5 |
| `size`, `mainWeapon`, `special`, `skill`, `playable`, `assetGroup`, `fallbackArt`, `gameplayApproximation` | — | — |

Runtime roster: **5 karakter**, semuanya `playable=true`.
`DOCUMENTED_CHARACTER_COUNT = 47` (targethjämförande), save sudah menyediakan 47 byte.

> **Jangan mengubah `CharacterDef`.** Ia adalah data statis build-time; upgrade permanen
> adalah state pemain dan harus hidup di `SaveStore`.

### 5.2 Yang **tidak boleh** disamakan dengan level-up run

`RunWorld.applyStat()` (`:1376-1381`) menambah **bonus lokal-run**:

| stat | Efek run |
|---|---|
| 0 | `baseMaxHp += hp/10`, `playerMaxHp += hp/10` |
| 1 | `atkPercentBonus += 8` |
| 2 | `speedPercentBonus += 12` |
| 3 | `critPercentBonus += 3` |
| 4 | `pickupPercentBonus += 20` |

Semua field bonus itu **di-nol-kan di `reset()`** (`:1207-1209`) tiap run.

> **Tidak ada sumber yang menyatakan upgrade coin = upgrade level-up.** Level-up adalah
>Hadiah sementara dalam satu run dengan generator offer acak; Stat Shop adalah pembelian
>permanen berbayar. Keduanya **harus dipisah** di field, di penyimpanan, dan di UI. Menggabungkan
>keduanya akan membuat level-up diam-diam mengubah build permanen — bug keras.

### 5.3 Titik integrasi UI

| Lokasi | Isi sekarang | Catatan |
|---|---|---|
| `FullGameCanvas.java:465` | `{"Play","Game Mode","Characters","Stage Select","Settings","Exit"}` | Menu utama, 6 item |
| `FullGameCanvas.java:661` | `{"Hardcore:…","Audio: OFF","Controls","Back"}` | Layar Settings, **sudah menyimpan state** |
| `FullGameCanvas.java:517` | layar Characters | punya paging & preview |

Tiga titik masuk yang masuk akal:
1. **Entri baru di Main Menu** — `"Stat Shop"` sebagai item ke-6, geser `"Exit"` ke-7.
   Paling terisolasi; tidak menyentuh alur stage.
2. **Sub-menu di layar Characters** — tinggi secara konsep (upgrade per karakter), tapi
   layar itu sudah padat (paging + preview + panel).
3. **Baris baru di Settings** —Settings sudah调用 `save.*`, tapi "Settings" bukan tempat
   spends currency secara alami.

**Rekomendasi: opsi 1** — entri Main Menu baru. Paling kecil, tidak mengganggu stage
selection maupun alur run.

### 5.4 Pembelian yang aman

Aturan minimal untuk `buyStat()`:

```
if (level == MAX)              return;   // cegah pembelian ganda / overflow level
long cost = price(level);
if (coinBalance < cost)        return;   // cegah saldo negatif
coinBalance -= cost;           // hanya setelah kedua guard lolos
level += 1;
save();                         // satu penulisan
```

- Saldo `long`, `cost` `long` → `coinBalance - cost` tidak bisa underflow karena dijaga guard.
- `level` di-cap `MAX` sebelum 저장 → tidak pernah melebihi kapasitas byte.
- **Satu** `save()` per pembelian, setelah seluruh state konsisten → tidak ada state setengah
  tertulis.

---

## 6. Pertanyaan Desain (belum ditetapkan — bukan fakta)

Tidak ada satu pun item berikut yang dapat dijawab dari source; semuanya perlu keputusan Anda.

| # | Pertanyaan | Mengapa penting |
|---|---|---|
| Q1 | Saldo coin **global** atau **per karakter**? | Menentukan seluruh layout record (opsi A vs B di §4.2) |
| Q2 | Hadiah **`coinClear` (victory)** ikut jadi saldo permanen? | `:767` memberi coin tanpa pickup |
| Q3 | Hadiah **level-up +100** ikut jadi saldo permanen? | `:1371` memberi coin tanpa pickup |
| Q4 | Text HUD `"Coins N"` menampilkan saldo **run** atau **permanen**? | `:1680`;屏 layar Results juga (`:721`) |
| Q5 | Coin **tidak tercollection** (kadaluarsa 30 detik / pool penuh) hilang? | R2 & R3 |
| Q6 | Stats mana yang bisa dibeli, dan harganya? | Tidak ada bukti di source/wiki lokal |
| Q7 | Upgrade permanen **menambah** base stat atau **menggantikan**? | menentukan rumus di `RunWorld` |
| Q8 | Maksimal level per stat? | menentukan lebar byte |
| Q9 | Apakah upgrade berlaku lintas karakter atau terkunci per karakter? |汪汪汪 interacts dengan Q1 |
| Q10 | Apakah stat shop bisa dibuka saat sedang/run? | Restriksi UI |
| Q11 | Behaviour saat `save()` gagal — tampilkan peringatan, atau diamkan seperti sekarang? | R1 |

---

## 7. Urutan Implementasi Terkecil yang Aman

Setiap tahap **independen, dapat diuji, dan dapat dibatalkan**.

### S1 — SaveStore: field + helper murni (tanpa perubahan terlihat)
- `DATA_LEN` 48 → 56; tambah `COIN_INDEX = 48`; `private long coinBalance;`
- Helper statis murni: `encodeCoin/decodeCoin`, `safeAddCoin` (saturating).
- Tests: extend pola `SaveStoreLegacyMigrationTest` — 48-byte lama → saldo 0; 56-byte →
  saldo benar; saldo maksimum `long` tidak overflow; record 6-byte lama tetap transpos.
- **Reversibel:** satu commit. **Perilaku pemain: tidak berubah sama sekali.**

### S2 — SaveStore: API saldo
- `long coinBalance()`, `void addCoins(long)`, `boolean spendCoins(long)`.
- `spendCoins` menolak bila saldo kurang atau sudah ganti.
- Tests: guard saldo kurang, guard double-spend, `addCoins` setelah `spendCoins`,
  kompatibilitas record 48-byte.
- **Reversibel:** satu commit.

### S3 — RunWorld: persistensi saat pickup coin
- `collectPickup()` type 1: `save.addCoins(p.value)` **ditambahkan setelah** `coins += p.value`.
- Test: alur magnet→collect→save; pickup tidak tercollection dua kali (guard `active`);
  pickup yang sudah `active=false` tidak menambah saldo.
- **Perilaku berubah:** coin jadi permanen.ogiiffscreen masih run.

### S4 — Keputusan Q2/Q3
- Terapkan atau tidak pada `defeatBoss()` (`:767`) dan `OFFER_COINS` (`:1371`).
- **Hanya setelah Q2/Q3 dijawab.**

### S5 — SaveStore: level stat permanen
- Terapkan Q1/Q7/Q8/Q9 → pilih layout (A atau B di §4.2).
- Tests: kompatibilitas record lama, batas level, overdraft.

### S6 — UI Stat Shop
- Entri Main Menu baru + layar + `buyStat()` sesuai §5.4.
- Test: guard saldo, guard level max, tampilan setelah pembelian.

---

## 8. Batasan Audit Ini

- **Tidak ada implementasi.** Tidak ada kode/aset/save yang diubah.
- **Tidak ada pengujian runtime** — tidak ada emulator/perangkat. Semua temuan berasal dari
  pembacaan source, probe statis di desktop JVM, dan aritmetika bit.
- `SaveStore` **tidak bisa** dikonstruksi di luar MIDP (`RecordStore.openRecordStore` melempar
  `java.lang.Error: API Stub has been used`), jadi `load()`/`save()` asli tidak pernah dieksekusi
  dalam audit ini. Klaim kompatibilitas §4 berasal dari **aritmetika `min()` yang terverifikasi**,
  bukan dari eksekusi RMS nyata — masih perlu diuji di perangkat.
- Tidak ada commit, tidak ada push. Laporan audit yang ada tidak diubah.