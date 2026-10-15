# HoloJava — Audit Awal Redesign Visual (READ-ONLY)

| Item | Nilai |
|---|---|
| HEAD saat audit | `4ec51a6` — `Hide redundant control hints` |
| Baseline disebut tugas | `9ce633b` — **satu commit di belakang** (parent dari HEAD) |
| Working tree | CLEAN — `git diff` EMPTY, `git diff --cached` EMPTY |
| Kode/aset/manifest/build diubah | **tidak ada** |
| `HoloCure_Unpacked` | manifest `280a9d1c…38a0` — **identik, tidak diubah** |
| JAR referensi | `20b31de6…3a6dfb` — tidak diubah |
| Direktori `reports/` | 40 direktori lama utuh + 1 direktori audit baru (pratinjau) |

Audit sepenuhnya baca-saja. Tidak ada aset disalin, dikonversi, atau diubah.

---

## 1. Ringkasan Temuan

| # | Temuan | Dampak |
|---|--------|--------|
| **F1** | **MANIFEST tidak punya atribut `MIDlet-Icon`** | Ikon MIDlet = default sistem |
| **F2** | **Tidak ada aset logo sama sekali** di HoloJava | Splash/logo mustahil tanpa aset baru |
| **F3** | **CLDC 1.1 / MIDP 2.0 tidak punya API scaling** | Aset besar wajib di-crop atau di-scale offline |
| **F4** | **Tidak ada Displayable splash** — canvas langsung `setCurrent` | Splash harus jadi state in-canvas |
| **F5** | **Frame A-chan hanya dimuat saat `prepareStage()`** (saat run mulai) & tanpa accessor publik | A-chan tak tersedia di Main Menu |
| **F6** | **Verifier mengunci jumlah aset = 166** | Menambah aset **akan menggagalkan build** |
| **F7** | A-chan HoloJava **byte-identik** dengan HoloCure | Preseden provenance sudah ada |

---

## 2. Konfigurasi MIDlet

### 2.1 Manifest — `tools/build_fullgame.sh:34-42`

```
Manifest-Version: 1.0
MIDlet-Name: HoloJava Full Game
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava Full Game, , me.holojava.fullgame.FullGameMIDlet
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
```

### 2.2 Application descriptor — `build_fullgame.sh:57-67`

`dist/java-me-fullgame/HoloJavaFullGame.jad` memuat `MIDlet-Name`, `MIDlet-Version`, `MIDlet-Vendor`, `MIDlet-1`, `MIDlet-Jar-URL`, `MIDlet-Jar-Size`, config & profile. **Tidak ada `MIDlet-Icon` di JAD maupun manifest.**

### 2.3 🔴 F1 — `MIDlet-Icon` tidak ada

Atribut `MIDlet-Icon` adalah atribut opsional standar MIDP 2.0 yang menunjuk URL aset relatif terhadap root JAR. Build script meng-zip `META-INF assets me` (`:32`), sehingga aset berada di `/assets/fullgame/...` → jalurnya `assets/fullgame/<file>.png`.

**Penambahan diperlukan**: 1 baris `MIDlet-Icon: assets/fullgame/<file>.png` di heredoc MANIFEST (`build_fullgame.sh:34-42`).

> MIDP 2.0 **tidak menetapkan ukuran ikon**. Device umumnya meminta 24×24 – 48×48. Tidak ada aset HoloJava pada kelas ukuran itu (lihat §4).

---

## 3. Alur Startup & Input

### 3.1 Rantai startup

```
FullGameMIDlet.startApp()                    FullGameMIDlet.java:14
 ├─ if canvas == null:
 │   ├─ save = new SaveStore()                        :16
 │   ├─ validation = ContentCatalog.validate()         :17
 │   ├─ assets = new AssetLibrary()                   :18
 │   └─ canvas = new FullGameCanvas(...)              :22
 ├─ display.setCurrent(canvas)                        :25   ← canvas langsung jadi Displayable
 ├─ new Thread(canvas).start()                        :26-29
 ├─ canvas.resumeApplication()                        :30
 └─ if canvas.exitRequested() notifyDestroyed()       :31-33
```

### 3.2 🔴 F4 — Tidak ada splash screen

`display.setCurrent(canvas)` di `:25` berjalan **langsung**; tidak ada `SplashCanvas`/`Canvas` terpisah, tidak ada state splash.

State awal Canvas (`FullGameCanvas.java:29`):
```java
private int screen = MENU;
```
Konstanta layar (`:12-19`): `MENU=0, MODES=1, CHARACTERS=2, STAGES=3, SETTINGS=4, PREP=5, GAME=6, RESULTS=7, CONTROLS=8`.

Dispatch render (`drawMenuScreen`, `:~313-321`):
```java
if (screen == MENU) drawMainMenu(...);
else if (screen == MODES) drawModes(...);
... else if (screen == PREP) drawPrep(...);
```

### 3.3 🔴 Prompt `PRESS 5 TO START` vs mapping aktual

`keyPressed` (`FullGameCanvas.java:105-110`):
```java
if (key == '5') skillPressed = true;
if (key == '9') specialPressed = true;
if (key == '1') attackPulse = true;
```

`'5'` **memang sah** sebagai pemicu splash — tapi saat ini hanya dikonsumsi di cabang `screen == GAME` (`:137-141`).

> ⚠️ **Perhatian — interacting dengan temuan audit F2** (`reports/gameplay-input-audit/00_AUDIT_REPORT.md`): `skillPressed` **tidak pernah di-reset di luar cabang GAME**. Jika splash memakai flag yang sama, menekan `5` di splash **akan bocor ke frame gameplay pertama** (skill aktif gratis + cooldown 15 detik terpakai). Splash **wajib** me-reset flag secara eksplisit saat transitioning.

---

## 4. Inventaris Aset

### 4.1 HoloJava — logo & ikon

| Permintaan | Status |
|---|---|
| Logo HoloCure | ❌ **TIDAK ADA** — `find assets -iname "*logo*" -o -iname "*title*" -o -iname "*splash*"` → nihil |
| `assets/fullgame/ui/` | hanya `pickup_icon.png` (16×16, RGBA, 40.6% opaque) |
| `assets/fullgame/items/` | **kosong** |
| Ikon kelas MIDlet (24–48 px) | ❌ tidak ada; aset terkecil = `bullet.png` 7×7, `spr_EXP_*` 12×13 |

Aset 43×38 opaque 100% yang ada semuanya **portrait karakter** (`gawr_gura`, `amelia_watson`, … 46 file) — bukan logo.

### 4.2 HoloJava — A-chan

**Satu-satunya A-chan**: `assets/fullgame/bosses/AchanBoss/spr_AchanBoss_0..3.png`

| Frame | Ukuran | Format | Opaque |
|-------|--------|--------|--------|
| 0 | 24×29 | RGBA | 63.4% |
| 1 | 23×29 | RGBA | 65.7% |
| 2 | 25×29 | RGBA | 63.3% |
| 3 | 24×30 | RGBA | 63.1% |

🔴 **Hanya ~24×30 px** — terlalu kecil untuk dekorasi Main Menu, dan **ukuran antar-frame berbeda** (23–25 lebar, 29–30 tinggi) sehingga `drawImage` pada koordinat sama akan menyebabkan **jitter**.

🔴 **F5 — tidak dapat diakses di Main Menu**: frame boss hanya dimuat oleh `AssetLibrary.prepareStage()` (`AssetLibrary.java:95-104` → `loadBoss()` `:120-124`), dipanggil saat run dimulai. `prepareStage` juga **meng-nolkan** `bosses[i].frames` (`:96`). Tidak ada accessor publik boss — hanya `prepareStage`, `characterPortrait`, `tile`, `projectile`, `characterWeapon`.

### 4.3 🔴 F7 — Provenance A-chan

```
frame 0: HoloJava=9c9caabbe9c524b2  HoloCure=9c9caabbe9c524b2  IDENTICAL
frame 1: HoloJava=66ed90c045b9a8ee  HoloCure=66ed90c045b9a8ee  IDENTICAL
frame 2: HoloJava=6dd812d09b476c02  HoloCure=6dd812d09b476c02  IDENTICAL
frame 3: HoloJava=c59c7a342c09d03c  HoloCure=c59c7a342c09d03c  IDENTICAL
```

Keempat frame **byte-identik** dengan `HoloCure_Unpacked/sprites/spr_AchanBoss/`. Konsisten dengan audit sebelumnya (211/213 PNG HoloJava byte-identik HoloCure). **Preseden importing aset HoloCure sudah ada** — namun tetap perlu konfirmasi lisensi Anda (§7).

---

## 5. Aset HoloCure_Unpacked — Kandidat

### 5.1 Logo

| Sprite | Ukuran | Frame | Opaque | Verifikasi visual | Cocok? |
|---|---|---|---|---|---|
| **`spr_HoloCureTitle_full`** | **615×259** | 1 | 48.5% | ✅ **Logo HoloCure asli**: 「ホロキュア!」 + "HoloCure" gradien pink–biru + "-Save the Fans-" + bintang/hati dekoratif | ⚠️ **terlalu lebar** (615 > 320) |
| `spr_gacha_holologo` | 85×98 | 1 | 60.6% | ❌ Segitiga/play arrow cyan — bukan logo | ✗ |
| `spr_fightLogo` | 133×70 | 1 | 39.5% | ❌ Teks "FIGHT!!!" — elemen UI gameplay | ✗ |
| `hud_new_title_BG` | 660×360 | 1 | 100% | Judul/background — terlalu besar, opaque penuh | ✗ |
| `spr_Title_<Char>` × 50 | 34–114 × 100–138 | 1 | — | ❌ Potret splash per-karakter (bukan logo) | ✗ |

**Tidak ada logo HoloCure berukuran ≤320 px di seluruh unpack.** `spr_HoloCureTitle_full` adalah satu-satunya logo asli.

### 5.2 A-chan

| Sprite | Ukuran | Frame | Opaque | Cocok? |
|---|---|---|---|---|
| **`spr_AchanBlink`** | **205×311** | **20** | 72.1% | ✅ **A-chan Rejoice (rambut biru, kacamata)** — animasi kedip **terverifikasi** |
| `spr_AchanShop` | 205×311 | 20 | 72.1% | ✅ Pose toko — juga 20 frame |
| `spr_AchanBoss` | 23–25×29–30 | 4 | ~64% | ⚠️ Chibi boss — **sudah ada di HoloJava** (byte-identik), sangat kecil |
| `spr_AchanDead` | 57×51 | 1 | 67.0% | Statis |

### 5.3 ✅ Animasi kedip A-chan — terbukti

Perbandingan tiap frame terhadap frame 0:

```
frame 0-4   IDENTICAL                      (mata terbuka, 205x311)
frame 5     bbox (0,194)-(205,261)          40 px berubah   205x312
frame 6-18  bbox (0,185)-(205,262)          85 px berubah   205x313
frame 19    bbox (0,185)-(205,261)          44 px berubah   205x312
```

Perubahan terlokalisasi di **y 185–262** = region **mata/kacamata**. 85 piksel berubah konsisten → benar-benar kedip, bukan noise.

⚠️ Tinggi frame bervariasi 311–313 (jitter vertikal 2 px). Lebar konstan 205 → **tidak ada jitter horizontal**.，仍 perlu offset per-frame bila放任 jitter vertikal.

### 5.4 Pratinjau visual

`reports/visual-redesign-audit/logo_candidates.png` — 9 panel: logo native, 2 variasi crop, A-chan native, 2 crop, dan 3 kandidat yang muat native.

---

## 6. 🔴 F3 — Batasan Tekal: Tidak Ada Scaling

`javap` pada stub MIDP 2.0 (`deps/java-me/midpapi20-2.0.4.jar`):

```
javax.microedition.lcdui.Graphics:
  public void drawImage(Image, int, int, int);
  public void drawRegion(Image, int, int, int, int, int, int, int, int);
  → hanya drawImage (ukuran asli) dan drawRegion (crop, tanpa resize)
```

`javax.microedition.lcdui.game.Sprite` **ada** di stub dan `FullGameCanvas extends GameCanvas` (`:9`), tetapi:
```
Sprite: setFrame, setFrameSequence, setTransform(TRANS_ROT/MIRROR), paint
→ TIDAK ada API resize
```

**Kesimpulan: tidak ada API scaling di CLDC 1.1 / MIDP 2.0.** Aset lebih besar dari area target harus:

| Opsi | feasibilitas |
|---|---|
| `drawRegion` crop | ✅ tersedia, tapi memotong wordmark logo → **tidak layak** (pratinjau menunjukkan "HoloCur" terpotong) |
| **Pre-scale offline** (resize PNG di luar repo) | ✅ satu-satunya cara mempertahankan proporsi penuh |
| `game.Sprite` | ❌ tidak menskala |

---

## 7. 🔴 F6 — Verifier Mengunci Jumlah Aset

`tools/verify_fullgame_build.py:52-58`:
```python
manifest_rows = list(csv.DictReader(handle))
if len(manifest_rows) != 166:
    fail("expected 166 asset manifest rows, got %d" % len(manifest_rows))
for row in manifest_rows:
    if not (ROOT / row["project_path"]).exists():
        fail("asset manifest path missing: " + row["project_path"])
```

`reports/fullgame_asset_manifest.csv` saat ini berisi **166 baris** dengan kolom:
`project_path, source_path, format, width, height, mode, transparency, role, confidence, evidence`

**Setiap aset baru di `assets/fullgame/` akan membuat build GAGAL** sampai CSV diperbarui **dan** angka 166 dinaikkan di verifier (`:54`).

Selain itu `:39-42` mewajibkan setiap `assets/fullgame/**/*.png` ada di dalam JAR.

⚠️ Ini berarti `reports/fullgame_asset_manifest.csv` adalah **input build**, meski berada di direktori untracked.

---

## 8. Rancangan Implementasi Bertahap

### Fase 1 — Ikon MIDlet (paling kecil)

| Langkah | Perubahan |
|---|---|
| 1a | Pre-scaling aset logo → ikon ~32×32 (butuh persetujuan §9) |
| 1b | Tambah baris `MIDlet-Icon: assets/fullgame/ui/midlet_icon.png` di `build_fullgame.sh:34-42` |
| 1c | Tambah 1 baris CSV + ubah `166` → `167` di `verify_fullgame_build.py:54` |
| 1d | Tidak ada perubahan kode Java |

**Alternatif tanpa aset baru**: pakai `assets/fullgame/ui/pickup_icon.png` (16×16, sudah ada, sudah terdaftar CSV) —零 risiko, tapi bukan logo HoloCure.

### Fase 2 — Splash Screen

Pendekatan **in-canvas** (paling aman):
- Tambah konstanta `SPLASH = 9`
- Ubah `private int screen = MENU;` → `= SPLASH` (`FullGameCanvas.java:29`)
- Tambah cabang render `if (screen == SPLASH) drawSplash(...)` di `drawMenuScreen`
- Tambah cabang input: `else if (screen == SPLASH) updateSplash(...)` di `update()` (`:155-162`)
- **Wajib**: `skillPressed = false` eksplisit saat keluar splash (menutup temuan audit F2)
- Handler: `'5'` → `screen = MENU`

**Kenapa bukan Displayable terpisah**: `setCurrent` dua kali + thread lifecycle menambah risiko; state in-canvas tidak menyentuh `FullGameMIDlet` maupun loop render.

**Prompt** `PRESS 5 TO START` — akurat karena `'5'` memang dipetakan di `keyPressed:107`.

### Fase 3 — Main Menu + A-chan

| Aspek | Rencana |
|---|---|
| A-chan | Pre-scale `spr_AchanBlink` 20 frame → ~100×152 (proporsi 205:311 dipertahankan) |
| Akses | Tambah `AssetLibrary` accessor `Image[] achanFrames()` yang memuat 4…20 frame on-demand — **tidak** lewat `prepareStage()` |
| Render | `drawAchanMenu(g, x, y, now)` di dalam `drawMainMenu` |
| Animasi | `frame = (int)((now - t0) / 180L) % N` — mengikuti kadens boss (`RunWorld:715`, 180 ms) |
| Jitter | Offset X/Y per-frame: `(cellW - frame.getWidth())/2` untuk menghindarijgoyang |
| **Dekoratif** | Tidak menambah entri `items[]`, tidak menyentuh `updateMain`/`menuIndex` → **tidak bisa dipilih, tidak memengaruhi navigasi** secara struktural |

### Fase 4 — Tema menu lain (bertahap)

Ganti latar `drawMenuScreen` `(14,20,38)` → biru muda/putih, dan warna `title()`/`list()` — **menunggu review visual** sesuai permintaan Anda.

---

## 9. Kendala & Keputusan yang Memerlukan Persetujuan

| # | Keputusan | Mengapa perlu persetujuan |
|---|-----------|----------------------------|
| **D1** | **Pre-scaling aset HoloCure** (logo 615×259 → ~300×126; A-chan 205×311 → ~100×152) | Melanggar "jangan mengonversi aset" pada tahap audit; mengubah isi aset |
| **D2** | **Lisensi HoloCure** | HoloCure fan-game; aset turunan Hololive. 211/213 aset HoloJava sudah byte-identik HoloCure, tapi apakah Anda ingin menambah dependensi baru? |
| **D3** | **Menambah aset → CSV + verifier** | Wajib ubah `verify_fullgame_build.py:54` (166→N). Ini menyentuh build script |
| **D4** | **A-chan 20 frame ≈ ukuran** | 20 frame pre-scaled menambah ukuran JAR; perlu decide berapa frame dipakai (mis. 6 frame kedip) |
| **D5** | **Ikon: logo HoloCure vs `pickup_icon.png` yang sudah ada** | Pilihan branding |
| **D6** | **Font & warna final** |ODY Anda menyatakan diputuskan setelah review visual |

### RekomendasiUDIO

1. **D5 → ikon dari `pickup_icon.png`** untuk Fase 1 (nol aset baru, nol risiko, instantly bisa)
2. **D1+D2** disetujui dulu → Fase 1b (ikon logo) & Fase 3 (A-chan)
3. **D3** disetujui → update CSV + verifier sebagai bagian dari Fase 1
4. Splash (Fase 2) **tidak butuh aset sama sekali** — bisa dikerjakan lebih dulu dengan warna polos biru muda-putih + teks `HOLOJAVA`, lalu logo ditambahkan setelah Fase 1

---

## 10. Batas Audit

| # | Batas |
|---|-------|
| B1 | Tidak ada perangkat/emulator — **tidak ada pengujian visual**. Semua analisis ukuran/geometri statis |
| B2 | Tidak ada aset disalin/dikonversi/diubah — pratinjau dibuat di `/tmp` lalu disalin sebagai bukti ke `reports/visual-redesign-audit/` |
| B3 | YYC build HoloCure tidak mengizinkan pembacaan kode; pemetaan peran aset (mana yang "logo") ditentukan dari **inspeksi visual**, bukan nama file |
| B4 | Kualitas font & rendering teks final tidak dapat dinilai tanpa perangkat |
| B5 | Baseline yang disebut tugas (`9ce633b`) tertinggal satu commit dari HEAD (`4ec51a6`) — keduanya inspected, working tree bersih di keduanya |
