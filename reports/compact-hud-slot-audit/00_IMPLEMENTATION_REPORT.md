# Audit Terpadu Sprite Weapon, Skill, dan HUD Ringkas 66 px

| Item | Nilai |
|---|---|
| HEAD | `c7764a8` — tidak berubah |
| Build | ✅ PASS — JAR 511681 bytes |
| Verifier | ✅ PASS — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ bersih |
| JAR referensi sebelum | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` |
| JAR referensi sesudah | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — **identik** |
| `HoloCure_Unpacked` | manifest `280a9d1c…38a0` — **identik, tidak diubah** |
| Staging / commit | tidak ada |
| Game / emulator | **tidak dijalankan** |

---

## 1. Kondisi Slot Sebelum Perbaikan

Kode sebelumnya menggambar sprite projectile bila muat, selain itu chip warna kategori:

```java
Image icon = assets.projectile(wd.sprite);
if (icon != null && icon.getWidth() <= inner && icon.getHeight() <= inner) {
    g.drawImage(icon, ...);                     // <-- hanya bullet 7x7 yang lolos
} else {
    ... chip warna menurut wd.category ...      // <-- 14 weapon lain
}
```

Tiga cacat terbukti:

| # | Cacat | Bukti |
|---|-------|-------|
| **C1** | **Projectile generik digambar seolah ikon weapon.** `bullet` 7×7 dipakai oleh **6 weapon** berbeda (`bl_book`, `sausage`, `glowstick`, `ens_curse`, `bl_fujoshi`, `legendary_sausage`). keenamnya tampil sebagai titik identik — tidak dapat dibedakan dan tampak seperti "ikon weapon spesifik". | Katalog: 6 weapon memakai sprite `bullet` |
| **C2** | **Status skill tidak terbaca.** `SKILL_FILL (46,46,46)` vs `SKILL_FILL_LOCKED (18,18,24)` → kontras WCAG **1.37:1**. | Perhitungan luminansi |
| **C3** | **Chip kategori tidak dapat dibedakan.** `CAT_BASIC (110,140,190)` vs `CAT_COLLAB (200,120,60)` → **1.01:1**; MAIN vs COLLAB **1.54:1**. Ketiganya nyaris tak terbaca satu sama lain. | Perhitungan luminansi |

---

## 2. Bukti Ketidakm—Ikan Ikon

### 2.1 Aset HoloJava (hanya ini yang boleh dipakai)

Interior slot **9×9**. MIDP 2.0 **tidak dapat menskalakan** `Graphics.drawImage`.

| Aset | Ukuran | Muat 9×9? |
|---|---|---|
| `projectiles/bullet.png` | 7×7 | ✅ ya |
| `projectiles/ame_bullet.png` | 10×8 | ❌ |
| `projectiles/bomb.png` | 21×20 | ❌ |
| `projectiles/trident.png` | 22×20 | ❌ |
| `projectiles/phoenix.png` | 22×20 | ❌ |
| `projectiles/scythe.png` | 24×20 | ❌ |
| `projectiles/tentacle.png` | 24×20 | ❌ |
| `projectiles/axe.png` | 22×24 | ❌ |
| `pickups/coin.png` | 15×15 | ❌ |
| `ui/pickup_icon.png` | 16×16 | ❌ |
| 5× `characters/*/portrait.png` | 43–44 × 37–38 | ❌ |

**Hanya `bullet.png` (7×7) yang muat** — dan itu adalah projectile generik milik 6 weapon, bukan ikon weapon.

### 2.2 Aset HoloCure (dilarang impor, dan memang tidak akan muat)

Pemeriksaan ukuran read-only atas 19 sprite yang disebut pada audit sebelumnya:

```
spr_BLBookIcon 15x20 ... spr_LegendarySausageIcon 22x22 ... spr_GuraWeapon 22x20
hud_optionIconCase 32x32   hud_caseSelect 34x34
→ HoloCure icons that fit 9x9: NONE
```

Spiral 5 terkecil di seluruh HoloCure: `spr_CEOTearsIcon` **12×17**, `spr_Crops_RiceIcon` 14×16.

**Kesimpulan ganda:**
1. Lisensi melarang impor — **dihormati, tidak ada aset disalin/diimpor**.
2. Bahkan lisensi teratasi, **tidak ada ikon HoloCure yang bisa tampil** di 9×9 tanpa scaling.

Ini谢谢你 menjawab "Jangan consider sprite projectile sebagai ikon inventory tanpa bukti" secara definitive.

---

## 3. Sprite yang Dipakai Setelah Perbaikan

**Tidak ada sprite yang digambar di dalam slot.**

| Kategori | Jumlah | Treatment |
|---|---|---|
| Ikon weapon terverifikasi | **0** | — |
| Projectile generik | 6 weapon memakai `bullet` | **tidak lagi digambar sebagai ikon** |
| Fallback netral | semua slot terisi | marker 9×9 netral |

### 3.1 Pemetaan audit — keyakinan TIDAK dinaikkan

Audit sebelumnya memetakan ikon HoloCure ke weapon HoloJava. Pemetaan itu **tetap tercatat dengan keyakinan aslinya** dan **tidak digunakan di sini** karena alasan ukuran (2.2).

| Weapon HoloJava | Kandidat HoloCure | Keyakinan audit | Dipakai? |
|---|---|---|---|
| `bl_book` | `spr_BLBookIcon` | Tinggi | ❌ 15×20 |
| `psycho_axe` | `spr_PsychoAxeIcon` | Tinggi | ❌ 22×18 |
| `sausage` | `spr_Sausage` | Tinggi | ❌ 24×21 |
| `holo_bomb` | `spr_HoloBombIcon` | Tinggi | ❌ 21×20 |
| `glowstick` | `spr_GlowStickIcon` | Tinggi | ❌ 20×20 |
| `bounce_ball` | `spr_BounceBallIcon` | Tinggi | ❌ 20×20 |
| `cutting_board` | `spr_CuttingBoardIcon` | Tinggi | ❌ 21×20 |
| `owl_dagger` | `spr_OwlDaggerIcon` | Tinggi | ❌ 23×20 |
| `absolute_wall` | `spr_AbsoluteWallIcon` | Tinggi | ❌ 23×22 |
| `black_plague` | `spr_BlackPlagueIcon` | Tinggi | ❌ 23×22 |
| `legendary_sausage` | `spr_LegendarySausageIcon` | Tinggi | ❌ 22×22 |
| `ens_curse` | `spr_CurseballIcon` | **Sedang** | ❌ 23×21 |
| `blood_lust` | `spr_BloodThirstIcon` | **Sedang** | ❌ 25×20 |
| `fan_beam` | `spr_LightBeamIcon` | **Rendah** | ❌ 25×21 |
| `bl_fujoshi` | — | **tidak ditemukan** | ❌ — |
| 5 main weapon | `spr_<Karakter>Weapon` | Tinggi | ❌ 18×20–24×20 |

> **`ens_curse`, `blood_lust`, `fan_beam` tetap Signaling Sedang/Rendah. `bl_fujoshi` tetap tanpa ikon.** Tidak ada pemeriksaan baru yang menaikkan keyakinan; pemeriksaan ini hanya mengukur ukuran.

### 3.2 Tidak ditemukan / belum terverifikasi

| Aset | Status |
|---|---|
| Ikon individual skill | ❌ tidak ada (audit aset mengonfirmasi `spr_optionSkillIcon` = penanda kategori generik) |
| Ikon individual Special | ❌ tidak ada |
| `hud_weapon_case_levels` | ❌ hanya Lv1–5, **dilarang** dipakai untuk Lv6–7 |

---

## 4. Representasi Slot

Helper tunggal `drawHudSlot(g, sx, sy, owned)` dipakai kedua baris — menjamin konsistensi.

| State | Sel 11×11 | Marker 9×9 | Kontras |
|---|---|---|---|
| Terisi (dimiliki) | `(30,30,30)` | `(175,180,190)` | **8.97:1** vs sel kosong |
| Kosong | `(18,18,24)` | — | — |
| Border (kedua) | `(78,78,90)` | — | 2.28:1 vs sel kosong |

### 4.1 Weapon slot

Sel `i` ditandai **hanya** bila `i < weaponCount` **dan** `weaponSlots[i]` valid:

```java
int wi = weaponSlots[i];
if (wi >= 0 && wi < ContentCatalog.WEAPONS.length && ContentCatalog.WEAPONS[wi] != null) owned = true;
```

Weapon tak dikenal / indeks rusak / slot kosong → **tidak ditandai** (konsisten, tidak mengklaim kepemilikan yang tak terverifikasi).

### 4.2 Skill slot

| State | Perlakuan |
|---|---|
| `skillAcquired == true` | sel `0` ditandai |
| `skillAcquired == false` | keenam sel kosong |
| Sel 1–5 | **selalu kosong** — 5 slot struktural kosong |

Mengapa: `CharacterDef.skill` adalah **satu `String` per karakter**, bukan koleksi; kepemilikan dilacak `skillAcquired`. Tidak ada data untuk 5 skill tambahan.

### 4.3 Badge level

**Tidak dapat ditampilkan.** `Font.SIZE_SMALL` ≈12 px tidak muat di sel 11×11. `hud_weapon_case_levels` (18×7) hanya Lv1–5 dan **tidak boleh** dipakai untuk Lv6–7.

`weaponLevels[]` **tetap dibaca runtime dan tidak diubah** — hanya tidak divisualisasikan. Tidak ada angka level yang dikarang.

---

## 5. Pemeriksaan Overlap, Batas Array, Koordinat

### 5.1 Invarian layout (dibuktikan via `javap -constants`)

| Invarian | Nilai | Status |
|---|---|---|
| Slot per baris | 6 | ✅ |
| Ukuran sel | 11×11 | ✅ |
| Total panjang bar | `6 × 11 = 66` px | ✅ |
| Total bar == HP bar | `66 == 66` | ✅ |
| Interior | 9×9 | ✅ |

### 5.2 Grid

```
weapon  y 30..40 : x  4..14  15..25  26..36  37..47  48..58  59..69
skill   y 42..52 : x  4..14  15..25  26..36  37..47  48..58  59..69
```

- Jarak antar sel: `0,0,0,0,0` → sel berdempetan, **tanpa overlap**
- Self-overlap bar weapon: **tidak ada**
- Self-overlap bar skill: **tidak ada**

### 5.3 Batas layar 320×240

| Elemen | Rentang | Status |
|---|---|---|
| HP bar | x 4..69, y 18..27 | ✅ dalam layar |
| Weapon bar | x 4..69, y 30..40 | ✅ dalam layar |
| Skill bar | x 4..69, y 42..52 | ✅ dalam layar |

### 5.4 Overlap dengan HUD lain (worst-case 7 px/char)

| Lebar world | Status run x | Slot x | Clearance |
|---|---|---|---|
| 320 | 155..316 | 4..69 | **86 px** |
| 360 | 195..356 | 4..69 | 126 px |
| 480 | 315..476 | 4..69 | 246 px |

Collisions terhadap **status run, fans counter, boss bar, dan controls hint**: **tidak ada** di ketiga lebar world.

### 5.5 Batas akses array

| Akses | Penjaga |
|---|---|
| `weaponSlots[i]` | hanya bila `i < weaponCount`; `weaponCount` ≤ 6 dijaga di `:1128/1229/1241` |
| `ContentCatalog.WEAPONS[wi]` | `wi >= 0 && wi < WEAPONS.length` |
| `WEAPONS[wi] != null` | dijaga |
| `weaponLevels[i]` | tidak lagi diakses di jalur render |

Tidak ada akses di luar batas. Tidak ada alokasi objek/string baru per frame (helper: **0** token alokasi).

---

## 6. Berkas Berubah

### `src/java-me-fullgame/me/holojava/fullgame/RunWorld.java` — satu-satunya berkas

| Perubahan | Alasan |
|---|---|
| Hapus cabang `assets.projectile(...)` + `drawImage` dari sel | **C1** — projectile generik 6 weapon tidak boleh tampil sebagai ikon |
| Hapus konstanta `CAT_BASIC/CAT_MAIN/CAT_COLLAB` | **C3** — 3 warna kategori tak dapat dibedakan (terbaik 1.46:1) |
| Hapus `SKILL_FILL`/`SKILL_FILL_LOCKED` | **C2** — digantikan marker netral |
| Tambah `SLOT_MARK/_G/_B = 175/180/190` | Kontras status 1.37:1 → **8.97:1** |
| Tambah helper `drawHudSlot(...)` | Satu jalur render untuk kedua baris — konsistensi terjamin |
| Guard `WEAPONS[wi] != null` diperluas ke penentuan `owned` | Slot weapon tak dikenal → **tidak ditandai** |

Layout, koordinat, jumlah slot, ukuran sel, cache, dan seluruh logika gameplay **tidak berubah**.

### `FullGameCanvas.java` — tidak disentuh

`git diff --numstat` tetap `4 2` — persis patch aksen Collab oranye sebelumnya.

---

## 7. Hasil Verifikasi

| Pemeriksaan | Hasil |
|---|---|
| `./tools/build_fullgame.sh` | ✅ PASS — 511681 bytes |
| `python3 tools/verify_fullgame_build.py` | ✅ PASS — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ bersih |
| 3 warning build | ⚠️ **sudah ada sebelumnya** — `javac -source 8 obsolete` |
| Konstanta basi | ✅ nol |
| Konstanta tak terpakai | ✅ nol (12/12 konstanta slot terpakai) |
| Alokasi per frame baru | ✅ nol |
| Mutasi gameplay/XP/leveling/weapon/skill/save | ✅ **nol** |

---

## 8. Konfirmasi Integritas

**Patch Collab oranye — UTUH:**
```
RunWorld.java:327        boolean offerIsCollab(int index) {
FullGameCanvas.java:424  g.setColor(selected ? 255 : 230, selected ? 190 : 138, selected ? 90 : 40);
```

**Git:**
```
 M FullGameCanvas.java   4/2   (patch oranye, utuh)
 M RunWorld.java       118/14  (kumulatif 4 tugas)
staged : EMPTY      HEAD : c7764a8
```

**Aset & lisensi:**
- Tidak ada aset disalin, diekstrak, diimpor, atau diubah dari `HoloCure_Unpacked`
- Tidak ada aset baru, tidak ada unduhan
- Manifest `HoloCure_Unpacked` **identik** dengan baseline
- Direkomendasikan: pemeriksaan lisensi tetap diperlukan **sebelum distribusi**, karena 211/213 PNG HoloJava sudah byte-identik dengan HoloCure. Status ini **tidak berubah** oleh audit ini.

**Larangan:** tidak ada game, MIDlet, emulator, maupun FreeJ2ME-Plus yang dijalankan; tidak ada pengujian visual; tidak ada staging; tidak ada commit.

---

## 9. Batasan yang Berlaku

| # | Batasan |
|---|---|
| B1 | **Tidak ada slot yang dapat menampilkan identitas weapon.** Konsekuensi tidak langsung dari 11×11 + interior 9×9 + MIDP tanpa scaling. |
| B2 | **Level weapon tidak dapat ditampilkan.** Butuh sel ≥ ~14 px atau glyph specially sized. |
| B3 | **5 dari 6 sel skill struktural kosong.** Batas model data, bukan bug. |
| B4 | **Slot hanya membedakan "terisi" vs "kosong".** Jika identitas atau level dibutuhkan, lebar bar harus naik (~120 px, 6 sel 20 px) — keluar dari spesifikasi 66 px dan **tidak diimplementasikan**. |
| B5 | Kontras dihitung dengan WCAG 2.x relative luminance (luminansi saja, bukan persepsi hue). Nilai aktual perangkat bisa berbeda. |
| B6 | Tidak ada pengujian pada perangkat — build PASS membuktikan kompilasi & integritas aset, bukan tampilan. |
