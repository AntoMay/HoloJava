# HUD Ringkas HoloJava — Laporan Implementasi

| Item | Nilai |
|---|---|
| HEAD | `c7764a8` — tidak berubah |
| Build | ✅ PASS — JAR 511903 bytes |
| Verifier | ✅ PASS — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ bersih |
| JAR referensi sebelum | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` |
| JAR referensi sesudah | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — **identik** |
| Staging / commit | tidak ada |
| Game / emulator | **tidak dijalankan** |

---

## 1. Berkas Berubah

### `src/java-me-fullgame/me/holojava/fullgame/RunWorld.java` — satu-satunya berkas

Semua perubahan confined ke `drawHud()` + blok konstanta HUD. Tidak ada berkas lain yang disentuh.

**Alasan setiap perubahan:**

| Perubahan | Alasan |
|---|---|
| `HP_BAR_W` 120 → **66** | Spesifikasi: 20–22% dari 320 px (64–70 px). 66 px = **20.6%** |
| Tambah konstanta slot ringkas | Dua baris slot 6 sel, lebar = lebar HP bar |
| `SLOT_W = HP_BAR_W / SLOT_COLS` | **Invarian terjamin compile-time**: lebar baris selalu sama dengan HP bar |
| Ganti strip 26 px lama → 2 baris 11 px | Layout ringkas yang disepakati |
| Pindahkan 3 baris teks ke kanan-atas | Baris Lv/EXP, Time/Kill, Coins kini jadi isi blok Status run |
| Format `"Lv"+n` → `"Lv "+n`, `" W"+n` → `" W "+n` | Sesuai spesifikasi baris status |
| Baris Fans: y 4 → y **64**,=right-aligned | Block Status run memakai pojok kanan atas y 4–61; Fans akan tabrakan |
| Hapus badge level | `Font.SIZE_SMALL` (~12 px) **tidak muat** di sel 11 px — lihat §5 |

### `FullGameCanvas.java` — **tidak disentuh**

`git diff --numstat` tetap `4 2` — persis patch aksen Collab oranye yang sudah ada sebelumnya.

---

## 2. Koordinat & Ukuran Final

Semua koordinat logical world pixel (`offX +` / `offY +`).

### A. HP bar

| Properti | Nilai |
|---|---|
| Posisi | `+4, +18` |
| Ukuran | **66 × 10** |
| Lebar dalam | 62 × 6 (inset 2) |
| Persentase | **20.6%** dari 320 px (spesifikasi 20–22%) |
| Teks HP | `+4, +4` — dipertahankan |
| Fill | `clamp(playerHp / playerMaxHp, 0, 1)`, guard `maxHp > 0` |
| Warna normal | hijau `(40,190,60)` |
| Warna rendah | merah `(220,40,40)` pada `rasio ≤ 25%` |
| Latar | `(20,20,20)` border + `(90,30,30)` dalam |

Nilai konstanta **dibuktikan dari bytecode** (`javap -constants`): `HP_BAR_W = 66`, `SLOT_W = 11`, `SLOT_COLS = 6`.

### B. Baris weapon slot

| Properti | Nilai |
|---|---|
| Posisi | `+4, +30` |
| Jumlah sel | **6** |
| Ukuran sel | **11 × 11** |
| Pitch | 11 px (sel bersebelahan) |
| Total | `6 × 11 = 66 px` — **sama dengan HP bar** |
| Rentang | x `4..69`, y `30..40` |
| Interior sel | 9 × 9 (inset 1) |

Sumber data runtime: `weaponCount`, `weaponSlots[i]`, `weaponLevels[i]`, `ContentCatalog.WEAPONS[wi]`, `assets.projectile(wd.sprite)`.

### C. Baris skill slot

| Properti | Nilai |
|---|---|
| Posisi | `+4, +42` |
| Jumlah sel | **6** |
| Ukuran sel | **11 × 11** |
| Total | `6 × 11 = 66 px` — **sama dengan HP bar & bar weapon** |
| Rentang | x `4..69`, y `42..52` |
| Sumber data | `skillAcquired` (boolean) + `character.skill` |

Sel `0` terisi bila `skillAcquired`; sel `1..5` **selalu kosong** (lihat §5.2).

---

## 3. Blok Status Run

| Baris | Y | Anchor | Sumber runtime |
|---|---|---|---|
| `Status run` | `+4` | `TOP\|RIGHT` | konstanta |
| `Lv <n>  EXP <a>/<b>` | `+19` | `TOP\|RIGHT` | `playerLevel`, `experience`, `expNeed` |
| `Time <mm:ss>  Kill <n>` | `+34` | `TOP\|RIGHT` | `timeString()`, `kills` |
| `Coins <n>  W <n>  I <n>` | `+49` | `TOP\|RIGHT` | `coins`, `weaponCount`, `itemCount` |

- **X anchor**: `offX + worldWidth - 4` →.right-aligned terhadap tepi world, **otomatis mengikuti `worldWidth`** di layar lebih lebar.
- **Pitch baris** 15 px — konvensi proyek yang sudah ada.
- **Tanpa estimasi karakter**: menggunakan `Graphics.RIGHT` (konstanta MIDP, terbukti `RIGHT = 8`, `TOP = 16` via `javap -constants` pada stub)._DEVICE_ yang mengukur lebar teks, bukan perkiraan.
- Font: `FACE_SYSTEM / STYLE_PLAIN / SIZE_SMALL` — sama dengan HUD lama.
- Warna: `(0,0,0)` — sama dengan HUD lama.
- Semua string di-cache dengan guard yang sudah ada (`cachedLevelExpString`, `cachedTimeString`, `cachedTimeKillString`, `cachedCoinsString`); **tidak ada alokasi per frame baru**.

---

## 4. Audit Overlap

### Batas worst-case (7 px/char, font device-dependent)

| Lebar world | Status run x | HUD kiri x | Clearance | Tabrakan |
|---|---|---|---|---|
| 320 | `155..316` | `4..69` | **86 px** | ❌ tidak |
| 360 | `195..356` | `4..69` | 126 px | ❌ tidak |
| 480 | `315..476` | `4..69` | 246 px | ❌ tidak |

### Tabrakan vertikal

| Elemen | Y | Status block y 4..61 | Hasil |
|---|---|---|---|
| Baris Fans | 64..76 | ✅ 3 px jarak | aman |
| Boss bar | 92..110 | ✅ 31 px jarak | aman |
| Nama boss | 96..108 | ✅ | aman |
| Petunjuk kontrol | 226..238 | ✅ | aman |

### Elemen yang dipindah & alasannya

**Baris Fans** semula `offX + worldWidth - 90, offY + 4` (kiri-anchored, y 4) — **akan menimpa judul `Status run`**. Dipindah ke `offY + 64`, right-anchored. Ini perubahan HUD saja; `mode.completion == COMPLETE_KILL_TARGET` tidak disentuh.

### Verifikasi bahwa baris lama dihapus

Tiga `drawString` kiri-anchored pada y 35/50/65 **dihapus**; blok pemicu cache-nya **dipertahankan** agar string tetap segar. Tidak ada teks yang digambar dua kali.

---

## 5. Konflik yang Ditemukan (Tidak Diperbaiki dengan Mengarang Data)

### 5.1 ⚠️ Konflik ukuran — ikon & level tidak muat di sel 11 px

Spesifikasi menuntut HP bar 64–70 px **dan** 6 slot selebar itu → **11 px per sel**, interior **9 × 9**.

- **MIDP 2.0 tidak dapat menskalakan gambar.** `Graphics.drawImage` hanya menggambar ukuran asli. `drawRegion` bisa memotong, tetapi potongan tengah 9×9 dari sprite 22×20 tidak dapat diidentifikasi — itu akan menyesatkan, bukan solusi.
- Aset sprite yang tersedia: **7×7 sampai 24×24**.

| Sprite | Ukuran |wu Hasil di sel 9×9 |
|---|---|---|
| `bullet` | 7×7 | ✅ digambar |
| `ame_bullet` | 10×8 | ❌ chip kategori |
| `bomb` | 21×20 | ❌ chip kategori |
| `trident` | 22×20 | ❌ chip kategori |
| `phoenix` | 22×20 | ❌ chip kategori |
| `scythe` | 24×20 | ❌ chip kategori |
| `tentacle` | 24×20 | ❌ chip kategori |
| `axe` | 22×24 | ❌ chip kategori |

**Konsekuensi**: hanya `bullet` (7×7) yang benar-benar tampil sebagai ikon — itu 6 dari 20 weapon. **Semua 5 main weapon dan 5 collab weapon** jatuh ke fallback.

**Fallback yang dipilih**: chip 9×9 berwarna sesuai `WeaponDef.category` nyata — basic `(110,140,190)`, main `(210,170,60)`, collab `(200,120,60)`. Warna diturunkan dari data runtime, **bukan ikon karangan**.

**Badge level dihapus**: `SIZE_SMALL` (~12 px tinggi) secara fisik tidak muat di sel 11 px. `weaponLevels[i]` **tetap dibaca** runtime tetapi tidak dapat dirender pada ukuran ini.

> **Rekomendasi (di luar cakupan tugas, tidak diimplementasikan)**: bila ikon & level level perlu dipertahankan, baris slot harus ~120 px (seperti implementasi sebelumnya), yaitu 6 sel 20 px. Itu keluar dari rentang 20–22% yang diminta.

### 5.2 ⚠️ Data skill hanya mendukung satu skill

`ContentCatalog.CharacterDef` memiliki `String skill` — **satu per karakter**, bukan koleksi. Kepemilikan dilacak oleh `RunWorld.skillAcquired` (boolean).

Artinya: **5 dari 6 sel skill secara struktural selalu kosong.** Ini bukan bug — ini batas model data. Tidak ada data kepemilikan baru yang dibuat; tidak ada ikon skill yang dikarang.

Audit aset HoloCure (`reports/holocure-asset-audit/`) mengonfirmasi **HoloCure juga tidak memiliki ikon skill per-karakter** — hanya penanda tipe generik `spr_optionSkillIcon`.

### 5.3 Karakter `·` tidak digunakan

Spesifikasi menulis pemisah `·` (U+00B7). Implementasi memakai **dua spasi ASCII**, mengikuti konvensi proyek yang sudah ada (`"Lv"+n+"  EXP "`).

Alasan:
1. **Seluruh source HoloJava 100% ASCII** (diverifikasi: 0 baris non-ASCII di 9 berkas).
2. `tools/build_fullgame.sh` menjalankan `javac --release 8` **tanpa flag `-encoding`** → bergantung default platform.
3. Font sistem MIDP belum tentu memuat glif U+00B7 → berisiko kotak kosong.

Struktur baris tetap persis seperti dispesifikasikan; hanya glif pemisah yang ASCII.

### 5.4 `weaponLevels` tidak dirender

Sudah tercakup §5.1. Array tetap jadi sumber data; hanya tidak dapat divisualisasikan.

---

## 6. Hasil Verifikasi

| Pemeriksaan | Hasil |
|---|---|
| `./tools/build_fullgame.sh` | ✅ PASS — 511903 bytes |
| `python3 tools/verify_fullgame_build.py` | ✅ PASS — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ bersih |
| 3 warning build | ⚠️ **sudah ada sebelumnya** — `javac -source 8/-target 8 obsolete`, bukan dari perubahan ini |
| Konstanta basi | ✅ nol — `SLOT_X/SLOT_Y/SLOT_SIZE/SLOT_PITCH/SLOT_DIGITS/SLOT_SKILL_FILL` semuanya hilang |
| Konstanta tak terpakai | ✅ nol — 18/18 konstanta baru direferensikan |
| Invarian lebar baris | ✅ `SLOT_COLS × SLOT_W = 6 × 11 = 66 = HP_BAR_W` (dibuktikan via `javap -constants`) |
| Guard pembagian nol HP | ✅ `maxHpRatio > 0.0` |
| Fill ter-clamp | ✅ `MathUtil.clamp(..., 0.0, 1.0)` |
| Warna HP rendah | ✅ `<= 25%` → merah, dipertahankan |
| Boss bar terpusat | ✅ `offX + (worldWidth - BOSS_BAR_W) / 2` utuh |
| Tidak ada mutasi gameplay | ✅ nol — tidak ada perubahan pada HP/XP/leveling/weapon/skill/save/inventory |

---

## 7. Konfirmasi Integritas

**Patch Collab oranye — UTUH:**

```
RunWorld.java:327       boolean offerIsCollab(int index) {
FullGameCanvas.java:424 if (world.offerIsCollab(i)) g.setColor(selected ? 255 : 230, selected ? 190 : 138, selected ? 90 : 40);
```

Tidak ada reset, stash, atau overwrite terhadap perubahan lokal yang sudah ada.

**Git:**

```
 M src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java   (4/2 — patch oranye, utuh)
 M src/java-me-fullgame/me/holojava/fullgame/RunWorld.java         (129/14 — kumulatif 3 tugas)
staged : EMPTY
HEAD   : c7764a8
```

**Tidak ada game, MIDlet, emulator, maupun FreeJ2ME-Plus yang dijalankan. Tidak ada unduhan atau instalasi. Tidak ada commit atau staging.**

Tidak ada pengujian visual pada perangkat — build PASS membuktikan kompilasi & integritas aset, **bukan** tampilan.
