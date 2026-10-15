# HoloCure_Unpacked — Audit Aset HUD (READ-ONLY)

| Item | Nilai |
|---|---|
| Direktori sumber | `/home/antomay/Projects/HoloCure_Unpacked` |
| Manifest baseline (sebelum) | `280a9d1c9cb9c213e7ff38d95825d0d659e1d0c7f65c46902793f4e7212338a0` |
| Manifest final (sesudah) | `280a9d1c9cb9c213e7ff38d95825d0d659e1d0c7f65c46902793f4e7212338a0` — **IDENTIK** |
| Isi | 34.422 file, 463.584.164 byte, 33.494 PNG |
| HoloJava HEAD | `c7764a8` — tidak berubah |
| JAR referensi | `20b31de6…3a6dfb` — tidak berubah |
| Staging / commit | tidak ada |

**Direktori sumber tidak diubah, dipindahkan, dihapus, maupun ditulis.** Semua contact sheet dibuat di `/tmp/opencode/holocure_audit/` lalu disalin ke direktori laporan HoloJava. Tidak ada game/emulator yang dijalankan, tidak ada unduhan atau instalasi.

---

## 1. Konteks Sumber

`README.txt` mengidentifikasi sumber sebagai:

- **HoloCure - Save the Fans!**, GameMaker Studio 2 (2024.6), build **YYC** (GML terkompiler — body script tidak tersedia)
- Steam **AppID 2420510**
- Ekstraksi: **UndertaleModTool CLI v0.9.2.0** dari `HoloCure.zip`

> **Penting (YYC):** karena logika terkompiler, `index/Scripts.txt` hanya memuat **nama** script (`InitSkillData`, `SpecialApply`, …). Body dan referensi sprite di dalamnya **tidak dapat dipulihkan**. Kesimpulan tentangITHubskripsi runtime harus berbasis nama aset + inspeksi visual, bukan pembacaan kode.

---

## 2. Ringkasan Temuan

| # | Temuan | Status |
|---|--------|--------|
| **F1** | **147 sprite `*Icon` khusus** tersedia, termasuk ikon per-weapon | ✅ Ditemukan |
| **F2** | **Ikon skill & Special per-karakter TIDAK ADA** di HoloCure | ⚠️ Konfirmasi negatif |
| **F3** | `spr_optionSkillIcon` / `spr_optionSpecial` = **penanda tipe generik**, bukan ikon skill | ✅ Terverifikasi visual |
| **F4** | Badge level `Lv1`–`Lv5` tersedia (**bukan** Lv1–Lv7) | ✅ Ditemukan |
| **F5** | Frame slot & bar HP tersedia | ✅ Ditemukan |
| **F6** | **211 dari 213** PNG HoloJava sudah byte-identik dengan HoloCure | ✅ Bukti provenance |
| **F7** | **Tidak ada berkas lisensi** di direktori sumber | ⚠️ Perlu pemeriksaan terpisah |

---

## 3. F1 — Ikon Weapon (menjawab fokus #3)

HoloJava saat ini memetakan 20 weapon ke **8** sprite projectile generik (6 weapon memakai `bullet` 7×7). HoloCure memiliki **ikon asli per-weapon**.

### 3.1 Pemetaan HoloJava → HoloCure

| HoloJava id | HoloJava name | Sprite HoloCure | Ukuran | Opaque% | Keyakinan |
|---|---|---|---|---|---|
| `bl_book` | BL Book | `spr_BLBookIcon` | 15×20 | 99.7 | **Tinggi** |
| `psycho_axe` | Psycho Axe | `spr_PsychoAxeIcon` | 22×18 | 41.4 | **Tinggi** |
| `sausage` | Sausage | `spr_Sausage` | 24×21 | 51.2 | **Tinggi** |
| `holo_bomb` | Holo Bomb | `spr_HoloBombIcon` | 21×20 | 87.6 | **Tinggi** |
| `glowstick` | Glowstick | `spr_GlowStickIcon` | 20×20 | 31.0 | **Tinggi** |
| `bounce_ball` | Bounce Ball | `spr_BounceBallIcon` | 20×20 | 77.0 | **Tinggi** |
| `cutting_board` | Cutting Board | `spr_CuttingBoardIcon` | 21×20 | 63.1 | **Tinggi** |
| `ens_curse` | EN's Curse | `spr_CurseballIcon` | 23×21 | 67.9 | **Sedang** — nama berbeda (`Curseball`), nama display HoloCure `EN's Curse` |
| `owl_dagger` | Owl Dagger | `spr_OwlDaggerIcon` | 23×20 | 38.9 | **Tinggi** |
| `fan_beam` | Fan Beam | `spr_LightBeamIcon` | 25×21 | 43.4 | **Rendah** — kandidat; belum terbukti |
| `absolute_wall` | Absolute Wall | `spr_AbsoluteWallIcon` | 23×22 | 56.5 | **Tinggi** |
| `black_plague` | Black Plague | `spr_BlackPlagueIcon` | 23×22 | 80.2 | **Tinggi** |
| `blood_lust` | Blood Lust | `spr_BloodThirstIcon` | 25×20 | 60.2 | **Sedang** — nama HoloCure `Blood Thirst` |
| `legendary_sausage` | Legendary Sausage | `spr_LegendarySausageIcon` | 22×22 | 59.5 | **Tinggi** |
| `bl_fujoshi` | BL Fujoshi | — | — | — | ❌ **Tidak ditemukan** |

**Bukti keyakinan "Tinggi"**: nama sprite HoloCure cocok token-per-token dengan nama display HoloJava, **dan** kedua nama ada di `strings/strings.txt` HoloCure (semua 14 nama weapon HoloJava terverifikasi ada sebagai string HoloCure). Bukti tambahan: tiap ikon having pasangan sprite animasi dengan nama sama (`spr_BLBook`/`spr_BLBookIcon`, `spr_PsychoAxe`/`spr_PsychoAxeIcon`, …).

### 3.2 Main weapon 5 karakter HoloJava

| HoloJava id | Karakter | Sprite HoloCure | Ukuran | Opaque% | Keyakinan |
|---|---|---|---|---|---|
| `trident_thrust` | Gura | `spr_GuraWeapon` | 22×20 | 47.0 | **Tinggi** |
| `pistol_shot` | Ame | `spr_Ameweapon` | 18×20 | 50.8 | **Tinggi** (perhatikan kapitalisasi `Ameweapon`) |
| `scythe_swing` | Calli | `spr_CalliWeapon` | 24×20 | 44.2 | **Tinggi** |
| `phoenix_sword` | Kiara | `spr_KiaraWeapon` | 22×20 | 47.5 | **Tinggi** |
| `summon_tentacle` | Ina | `spr_InaWeapon` | 24×20 | 59.2 | **Tinggi** |

Sprite *maxed* (level maksimum) juga tersedia: `spr_GuraMaxedWeapon` 25×25, `spr_AmeMaxedWeapon` 24×25, `spr_CalliMaxedWeapon` 25×23, `spr_KiaraMaxedWeapon` 25×25, `spr_InaMaxedWeapon` 25×25. Bukti keyakinan **Tinggi** (pola `<Karakter>Weapon` konsisten untuk kelima karakter).

> **Koreksi pembacaan**: pada contact sheet awal, `spr_GuraWeapon` sempat terbaca sebagai sel kosong. Analisis alpha membuktikan **tidak kosong** (22×20, 47.0% piksel tak transparan, bbox `(0,0,22,20)`).

---

## 4. F2 — Skill & Special (menjawab fokus #1 & #2)

### 4.1 Hasil: TIDAK ADA ikon per-karakter

`AssetLibrary` HoloJava tidak punya aset skill, dan **HoloCure juga tidak**. Yang ada hanyalah **penanda tipe generik**:

| Sprite | Ukuran | Isi (terverifikasi visual) |
|---|---|---|
| `spr_optionSkillIcon` | 34×34 | Kotak persegi outline putih + **bintang emas** di kanan-bawah |
| `spr_optionSpecial` | 35×35 | Kotak persegi outline putih + **kantong emas** di kanan-bawah |
| `spr_optionWeaponIcon` | 35×35 | Kotak sama + **X biru** |
| `spr_optionCollabWeaponIcon` | 35×35 | Kotak sama + **X oranye** |
| `spr_optionSuperCollabWeaponIcon` | 35×35 | Kotak sama + **X merah** |
| `spr_optionItemIcon` | 34×35 | Kotak sama + **X hijau** |
| `spr_optionStatIcon` | 35×35 | Kotak sama + **X kuning** |
| `spr_optionUnlockIcon` | 33×35 | Kotak sama + **X oranye** |

Kedelua sprite ini **identik secara struktural** — hanya berbeda penanda sudut. Keduanya adalah **jenis penanda opsi**, bukan representasi skill.

### 4.2 Bukti negatif

1. Pencarian `spr_*` untuk `skill`/`special` → 63 hasil; **semuanya** adalah animasi efek karakter (`spr_GuraSharkSpecial`, `spr_Calli_specialfx`, `spr_AZKiSpecialMarker`, …) atau penanda generik di atas. **Tidak ada** sprite bernama skill/karakter yang berupa ikon statis.
2. `index/GameObjects.txt` + `Objects_Detail.txt` → hanya **satu** objek yang relevan: `obj_GeoSpecialMarker` → `spr_AZKiSpecialMarker`. Tidak ada objek ikon skill.
3. `strings/strings.txt` → deskripsi skill/special (`Shark Call`, `Shark Bite`, …) adalah **teks murni multi-bahasa**, tanpa referensi sprite.

### 4.3 Implikasi untuk HoloJava

Hambaan H1 dari audit sebelumnya (**ikon skill tidak tersedia**) **tidak dapat diselesaikan dari HoloCure** — bukan karena aset HoloJava kurang lengkap, tetapi karena HoloCure sendiri tidak mengilustrasikan skill. Sel skill di HoloJava akan tetap perlu representasi non-ikon (atau aset baru).

`spr_UberSheepIcon` (23×18) **ada** di HoloCure — item yang telah dihapus dari HoloJava pada commit `c7764a8`. Relevant sebagai catatan provenance saja; **tidak** usulan untuk dikembalikan.

---

## 5. F4 — Badge Level (menjawab fokus #4)

| Sprite | Ukuran | Frame | Isi |
|---|---|---|---|
| `hud_weapon_case_levels` | 18×7 | 5 | Pill `Lv 1` … `Lv 5` (Lv5 emas = maksimal) |
| `hud_item_case_levels` | 18×7 | 5 | idem |
| `hud_mainweapon_case_levels` | 20×7 | 5 | idem |

Semua RGBA 8-bit, non-interlaced, ~97% piksel tak transparan (pill padat dengan teks).

> **Keterbatasan penting:** HoloCure hanya menyediakan **Lv1–Lv5**, sedangkan HoloJava proved `maxLevel == 7` untuk **20/20 weapon**. Badge ini **tidak menutupi** level 6–7. Butuh 2 frame tambahan atau cap teks.

---

## 6. F5 — Frame Slot, Bar, dan Elemen HUD Lain

| Sprite | Ukuran | Fungsi terverifikasi | Relevan untuk HoloJava |
|---|---|---|---|
| `hud_caseSelect` | 34×34 | **Frame kotak outline sian** | 🟢 Frame slot — warna sian亮 |
| `hud_caseSelect_armory` | 34×34 | Frame kotak sian (sama) | 🟢 Alternatif frame slot |
| `hud_optionIconCase` | 32×32 | **Frame kotak outline putih/abu** | 🟢 Frame slot netral — paling cocok |
| `spr_optionWeaponIcon` | 35×35 | Frame + penanda tipe | 🟡 Frame + badge tipe |
| `hud_HUD_HP_green` | 129×6 | **Bar HP hijau** (2 frame) | 🟢 Fill HP normal |
| `hud_HUD_HP_purple` | 129×6 | **Bar HP magenta/ungu** | 🟢 **Fill HP rendah** —印证 perubahan warna ambang |
| `hud_expbar_bg` | 640×26 | Bar biru solid — latar EXP | 🟡 Terlalu lebar (640px) untuk 320 logical |
| `hud_expbar_frame` | 640×13 | Garis gelap tipis | 🟡 idem |
| `hud_HPicon` | 16×14 | **Hati merah** | 🟢 Ikon HP |
| `hud_atkicon` | 16×16 | Pedang bersilang | 🟢 Ikon ATK |
| `hud_spdicon` | 16×16 | Sayap/sepatu biru | 🟢 Ikon SPD |
| `hud_criticon` | 16×16 | Bentuk emas | 🟢 Ikon CRIT |
| `hud_critdamicon` | 16×16 | X pedang emas | 🟢 Ikon CRIT DMG |
| `hud_cooldownicon` | 16×16 | Lingkaran biru ber-slash | 🟢 Indikator cooldown/tidak tersedia |
| `hud_level` | 146×32 | Teks `LV` + panah putih | 🟡 Too wide |
| `hud_new_HUD` | 148×60 | **Transparan kosong** (bbox penuh, 0% terlihat) | ❌ Tidak berguna |
| `hud_portcase` / `hud_portcase_full` | 47×37 / 47×49 | Banner sudut putih | 🟡 Dekoratif, bukan frame slot |

### 6.1 `hud_optionIcons` — BUKAN ikon skill

22 frame (14×12 – 28×16, plus 4 frame 1×1 kosong). Inspeksi visual: **pasangan ikon status** biru/putih — bar, gelombang, speaker, controller, `A→B`, kotak `ITEM`/`COST`, `COST`. Frame 0/1 = bar, 2/3 = gelombang, 4/5 = speaker, 8–11 = controller, 12/13 = `A→B`, 14/15 = `ITEM`, 16/17 = dash. **Tidak terkait skill.**

### 6.2 Bukan kandidat (sudah disaring)

| Sprite | Alasan ditolak |
|---|---|
| `furn_*Icon` (≈700-an) | Dekorasi furnitur/interior — **bukan** HUD |
| `spr_Wall*Icon`, `spr_Floor_*Icon` | Material dinding/lantai |
| `spr_Crops_*Icon` | Tanaman pertanian |
| `spr_CookingIcon`, `spr_FarmingIcon`, `spr_FishingIcon` | Aktivitas sampingan |
| `spr_*Portrait`, `spr_*Port` | Portrait karakter — sudah ada di HoloJava |
| `spr_*Special`, `spr_*Weapon` (animasi) | Animasi efek gameplay, bukan ikon statis |
| `spr_ach_skill` | Ternyata **tanda larangan** (lingkaran merah ber-slash), bukan ikon skill |

---

## 7. F6 — Provenance / Asal-Usul

### 7.1 Bukti hash

Dihash seluruh 33.494 PNG HoloCure dan 213 PNG HoloJava:

```
HoloJava png files        : 213
byte-identical matches    : 211  (99.1%)
```

Contoh: `bosses/AchanBoss/spr_AchanBoss_0.png` ≡ `sprites/spr_AchanBoss/spr_AchanBoss_0.png`.

**Kesimpulan:** aset HoloJava **sudah diekstrak dari HoloCure**. Status provenance untuk ikon baru **tidak berubah** dari status aset yang sudah ada.

Dua file HoloJava **tidak** punya pasangan HoloCure:
- `assets/fullgame/stages/office.png`
- `assets/fullgame/stages/fantasy.png`

Keduanya adalah tile stage — kemungkinan dibuat khusus HoloJava.

### 7.2 ⚠️ Pemeriksaan Lisensi Terpisah Diperlukan

| Temuan | Detail |
|---|---|
| **Tidak ada berkas lisensi** | `find` untuk `*licen*`, `*copying*`, `*credit*` → nihil. Hanya `README.txt` (catatan ekstraksi). |
| **Asal** | HoloCure.zip, AppID 2420510 — game fan-made |
| **Aset** | HoloCure memuat aset turunan dari IP Hololive; tiap aset имеет kemungkinan pemilik hak cipta berbeda |
| **Dampak** | **Menjadi transact** — 211/213 aset HoloJava yang sudah ada adalah byte-identik. Menambah ikon baru **tidak menambahkan risiko baru**, hanya memperbesar himpunan aset turunan yang sama. |

**Rekomendasi:** tinjau lisensi HoloCure + tiap aset Hololive **sebelum**ariantaset baru dik Redistribusikan. Status ini sudah berlaku bagi HoloJava hari ini.

---

## 8. Ringkasan Kandidat untuk HoloJava

### Prioritas tinggi — langsung menyelesaikan masalah HoloJava

| Kebutuhan HoloJava | Aset HoloCure | Dampak |
|---|---|---|
| Ikon weapon membedakan 20 weapon | 14 ikon `*Icon` + 5 `*<Karakter>Weapon` | Menghapus duplikasi `bullet` 7×7 |
| Badge level | `hud_weapon_case_levels` 18×7 | ⚠️ hanya Lv1–5 |
| Frame slot | `hud_optionIconCase` 32×32 / `hud_caseSelect` 34×34 | Mengganti kotak polos |
| Warna HP rendah | `hud_HUD_HP_purple` 129×6 | confiscated bukti visual |

### Tidak tersedia

| Kebutuhan | Status |
|---|---|
| **Ikon skill per-karakter** | ❌ Tidak ada di HoloCure |
| **Ikon Special per-karakter** | ❌ Tidak ada di HoloCure |
| Badge Lv6–7 | ❌ HoloCure hanya Lv1–5 |

---

## 9. Batasan Audit

| # | Batasan |
|---|---|
| L1 | Build YYC → **body script tidak tersedia**. Pemetaan runtime (mis. sprite mana yang dipakai skill) **tidak dapat dibuktikan** dari data; kesimpulan berbasis nama aset + inspeksi visual. |
| L2 | `spr_*Weapon` yang dipilih HoloCure untuk main weapon saat runtime tidak dapat dikonfirmasi 100% tanpa GML; keyakinan "Tinggi" berasal dari konsistensi pola ke-5 karakter. |
| L3 | `spr_LightBeamIcon` untuk `fan_beam` hanya **kandidat** — belum terbukti. |
| L4 | Tidak ada game/emulator yang dijalankan; **tidak ada pengujian visual pada perangkat**. |
| L5 | Hanya `sprites/` yang ditelusuri mendalam. `textures/`, `textures_grouped/`, `masks/`, `binary_strings/` belum diaudit untuk fokus ini. |

---

## 10. Lampiran — Contact Sheet

Semua sheet dibuat dari bacaan read-only di `/tmp/opencode/holocure_audit/`, lalu disalin ke direktori ini.

| Berkas | Isi |
|---|---|
| `contact_hud_candidates.png` | 16 kandidat HUD inti (skill/special marker, case, bar, ikon status) |
| `contact_hud_frames.png` | 18 frame HUD (caseSelect, optionIconCase, HP green/purple, expbar, portcase) |
| `contact_levels_weapons.png` | Badge Lv1–Lv5 + 11 sprite weapon karakter |
| `contact_weapon_icons.png` | **33 kandidat ikon weapon** — sheet utama untuk fokus #3 |
| `contact_optionIcons.png` | 22 frame `hud_optionIcons` (terbukti bukan ikon skill) |

Semua sheet memakai **latar checkerboard** agar transparansi terlihat, dan sprite **tidak diperbesar** sehingga proporsi asli terjaga.

---

## 11. Status Akhir

| Item | Nilai |
|---|---|
| `HoloCure_Unpacked` | **tidak berubah** — manifest SHA-256 identik |
| Source HoloJava | **tidak berubah** — hanya 2 file `M` dari tugas sebelumnya |
| Build / verifier | **tidak dijalankan** (tidak ada perubahan kode) |
| JAR referensi | `20b31de6…3a6dfb` — tidak berubah |
| Staging / commit | tidak ada |
| Berkas baru | 5 contact sheet + laporan ini, di `reports/holocure-asset-audit/` |

**Tidak ada aset yang diimplementasikan.** Menunggu tinjauan sebelum tindakan apa pun.
