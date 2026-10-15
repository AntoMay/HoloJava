# Audit: World Background Berbasis Full Background + Camera Scrolling

**Status:** audit-only. Tidak ada kode Java, aset, konfigurasi, build script, build
artifact, atau laporan lama yang diubah/di-commit. Tidak ada staging/commit/push.
**Tanggal:** 2026-10-15
**Ruang lingkup:** sistem koordinat world, kamera, viewport, dan rendering background.
**Laporan ini terpisah dari `00_BACKGROUND_TILE_AUDIT.md` (tidak ditimpa).**

---

## 1. Status Repository

| Item | Nilai |
|---|---|
| Branch | `main` |
| HEAD | `3a9d950 Fix fantasy stage background tile` |
| Tracked tree | **bersih** (`git status --porcelain -uno` kosong) |
| Staged / unstaged diff | kosong keduanya |
| Stash | kosong |

**Commit Fantasy terverifikasi masih ada:**
```
3a9d950 Fix fantasy stage background tile
b1239e5 Initial HoloJava project baseline
```
`assets/fullgame/stages/fantasy.png` = blob `8a2defb…`, SHA-256
`e49d489f9983b1f215f3a22d51e9b6fbf07d91840fab575c3d7fc5376e2f8bee` — identik dengan
hasil build sebelumnya. **Tidak disentuh oleh audit ini.**

Untracked: seluruhnya di bawah `reports/` (41 direktori, termasuk
`reports/background-tile-audit/`). Tidak ada file yang dihapus, dipindah, atau dibersihkan.

---

## 2. Audit Sistem Kamera dan World

### 2.1 Positionsummary

> **Tidak ada kamera.** Tidak ada `cameraX`/`cameraY`/`camX`/`camY` di seluruh
> `src/java-me-fullgame/`. Semua simulasi sudah berada di koordinat world yang benar,
> tetapi **world selalu berukuran sama dengan layar**, sehingga tidak ada yang perlu
> digeser.

### 2.2 Bukti struktural

#### (a) `Viewport` hanya Recentering, bukan Kamera

`src/java-me-fullgame/me/holojava/fullgame/Viewport.java:26-33`
```java
void update(int width, int height) {
    screenW = width > 0 ? width : MIN_W;
    screenH = height > 0 ? height : MIN_H;
    worldW = screenW < MIN_W ? MIN_W : screenW;   // baris 29
    worldH = screenH < MIN_H ? MIN_H : screenH;   // baris 30
    offsetX = (screenW - worldW) / 2;            // baris 31
    offsetY = (screenH - worldH) / 2;            // baris 32
}
```

`offsetX`/`offsetY` **hanya** nilai `>= 0` (0) atau negatif kecil untuk crop居中.
Hitungannya deterministik dan independen dari posisi pemain:

| Layar | `worldW x worldH` | `offsetX, offsetY` | Makna |
|---|---|---|---|
| 320×240 | 320×240 | (0, 0) | world = layar, offset selalu 0 |
| 480×240 | 480×240 | (0, 0) | world = layar, offset selalu 0 |
| 352×240 | 352×240 | (0, 0) | world = layar, offset selalu 0 |
| 240×320 | 320×320 | **(−40, 0)** | world > layar → crop terpusat |
| 176×208 | 320×240 | **(−72, −16)** | crop terpusat |
| 128×128 | 320×240 | **(−96, −56)** | crop terpusat |

**Konsekuensi:** untuk setiap layar ≥ 320×240, `offsetX == offsetY == 0` **selalu**.
Jadi `offsetX/offY` yang diteruskan ke `world.draw()` bukan kamera — hanya
letterbox/crop. Tidak ada satu pun pembacaan posisi pemain di `Viewport`.

Ada method `screenX()`/`screenY()` (`Viewport.java:35-36`) = `offset + world`, tapi
**tidak pernah dipanggil** dari mana pun.

#### (b) Ukuran World dimiliki Layar, bukan Stage — ini blocker utama

`src/java-me-fullgame/me/holojava/fullgame/RunWorld.java:251-260`
```java
void setViewport(Viewport viewport) {
    if (viewport == null) return;
    worldWidth  = viewport.worldW;    // baris 253  <-- menimpa ukuran world
    worldHeight = viewport.worldH;    // baris 254
    if (!viewportInitialized) {
        px = (worldWidth  - 24) / 2;  // baris 256
        py = (worldHeight - 24) / 2;  // baris 257
        viewportInitialized = true;
    }
}
```

`setViewport()` dipanggil **dua kali per frame**:
- `FullGameCanvas.java:165` — di `update()` (lagi tick input)
- `FullGameCanvas.java:366` — di `render()` (lagi paint)

Artinya `worldWidth`/`worldHeight` = ukuran layar, **bukan properti stage**.
Tidak ada konsep "map" sama sekali. Inilah yang harus diputuskan lebih dulu sebelum
kamera bisa ada.

#### (c) Posisi pemain: world coordinate murni

`RunWorld.java:100-101` — `private int px = 130; private int py = 95;`
`RunWorld.java:400-427` — `updatePlayer()`:

```java
double speed = 120.0 * character.spdPercent / 100.0;     // baris 401
...
px += (int) ((dx / len) * speed * 0.04);                  // baris 415
py += (int) ((dy / len) * speed * 0.04);                  // baris 416
...
if (px < 0) px = 0;                                       // baris 421
if (py < 0) py = 0;                                       // baris 422
if (px > worldWidth  - 24) px = worldWidth  - 24;         // baris 423
if (py > worldHeight - 24) py = worldHeight - 24;         // baris 424
```

Pemain 24×24 px, **`px`/`py` adalah koordinat world absolut**. Clamp langsung ke
batas world. Tidak ada referensi kamera di mana pun dalam simulasi.

#### (d) Translasi world → layar

Hanya di `RunWorld.draw()` → `drawBackground/drawPlayer/drawHud`, semuanya memakai
`offX + <worldX>`:

- `RunWorld.java:1477` `drawBackground(g, offX, offY);`
- `RunWorld.java:1484, 1487, 1492, 1494` pickups — `int x = offX + p.x;`
- `RunWorld.java:1502` shots — `int x = offX + s.x;`
- `RunWorld.java:1511, 1514` enemies — `int x = offX + e.x;`
- `RunWorld.java:1521` boss — `int x = offX + boss.x;`
- `RunWorld.java:1552, 1558` player — `int x = offX + px;`

`offX/offY` berasal dari `FullGameCanvas.java:367`:
```java
world.draw(g, viewport.offsetX, viewport.offsetY, System.currentTimeMillis());
```

Jadi: **seluruh transformasi dunia→layar terpusat pada penambahan `offX/offY` di
`draw()`.** Mengganti `offX` dengan `-cameraX` adalah titik integrasi kamera yang
paling natural dan tidak menyentuh simulasi.

#### (e) Perhatikan: ada DUA offset berbeda di `FullGameCanvas`

`FullGameCanvas.java:357-367`
```java
viewport.update(w, h);
int ox = (w - Viewport.MIN_W) / 2;                      // baris 358  offset独立
int oy = (h - Viewport.MIN_H) / 2;                      // baris 359
if (ox < 0) ox = 0; if (oy < 0) oy = 0;                // baris 359 clamp
...
world.draw(g, viewport.offsetX, viewport.offsetY, ...); // baris 367  offset berbeda
if (world.state() == STATE_LEVEL_UP) drawLevelUp(g, ox, oy);  // baris 368
if (world.state() == STATE_PAUSED)   drawPause(g, ox, oy);    // baris 369
```

`ox/oy` (letterbox, clamp ≥ 0) dipakai untuk **overlay menu**; `viewport.offsetX/offsetY`
(crop, bisa negatif) dipakai untuk **world**. Keduanya tidak boleh dicampur saat
kamera ditambahkan — `ox/oy` tetap untuk HUD/overlay layar, `offset` untuk world.

#### (f) HUD ikut transform world saat ini — harus dilepas

`RunWorld.draw()` (`1476-1529`) memanggil `drawHud(g, offX, offY, now)` — baris 1524/1528.
`drawHud` memakainya untuk **semua** elemen HUD:

- `RunWorld.java:1573` `g.drawString(cachedHpString, offX + 4, offY + 4, …)`
- `RunWorld.java:1580` `g.fillRect(offX + HP_BAR_X, offY + HP_BAR_Y, …)`
- `RunWorld.java:1597` `drawHudSlot(g, offX + HP_BAR_X + i*SLOT_W, offY + WEAPON_SLOT_Y, owned)`
- `RunWorld.java:1636` `int statusX = offX + worldWidth - STATUS_MARGIN;`
- `RunWorld.java:1658` `int bossBarX = offX + (worldWidth - BOSS_BAR_W) / 2;`
- `RunWorld.java:1669, 1674` teks status & kontrol hint

**Jika `offX` diganti `-cameraX`, seluruh HUD ikut bergeser** — itu regresi visual
yang pasti terjadi dan harus ditangani di dalam scope kamera.

#### (g) Simulasi & collision sudah murni world — tidak perlu disentuh

Semua AI/collision sudah world-murni, **tanpa kamera**, jadi Introducing kamera
tidak memerlukan perubahan pada logika mana pun:

| Fungsi | Lokasi | Nature |
|---|---|---|
| `playerCenterX/Y()` | `RunWorld.java:1458-1459` | `px+12`, `py+12` |
| `overlap(a…)` | `RunWorld.java:1467-1469` | AABB murni |
| `clampEnemy()` | `RunWorld.java:1471-1474` | clamp ke `worldWidth/worldHeight` |
| AI chase | `RunWorld.java:526-527, 541-553, 604-607` | `e.x - playerCenterX()` |
| Boss chase/charge | `RunWorld.java:657-658, 683-699` | `boss.x` vs `playerCenterX()` |
| Spawn di tepi | `RunWorld.java:517-525` | pakai `worldWidth/worldHeight` |
| Spawn radial | `RunWorld.java:1120-1121` | `playerCenter + cos/sin` |
| Pickup magnet | `RunWorld.java:949-959` | jarak ke `playerCenter` |
| Contact damage | `RunWorld.java:595, 716` | `overlap(px,py,…)` |
| Shot bounds | `RunWorld.java:888` | `worldWidth + 30` |
| Clamp pemain | `RunWorld.java:421-424` | batas world |

**Implikasi:** memperbesar `worldWidth/worldHeight` otomatis (i) memberi ruang gerak
pemain lebih luas, (ii) membuat musuh spawn lebih jauh dari tepi, (iii)/stretch
spawnWave — tanpa satu pun edit AI.

#### (h)Apakah kamera benar-benar mengikuti pemain?

**Tidak.** Tidak ada basis kode yang mengolerasi `px/py` ke `offsetX/offsetY`.
Satu-satunya pembacaan `px/py` di luar simulasi adalah `drawPlayer` (`:1549`) dan
`drawHud` indirectly via `playerCenterX()`. Kamera harus dibangun dari nol.

#### (i) Dead code & accessor tak terpakai (catatan, di luar scope)

- `RunWorld.drawLevelUp()` (`:1693-1707`) adalah `private` dan **tidak pernah
  dipanggil** — `FullGameCanvas.drawLevelUp()` (`:692`) yang dipakai. Dead code.
- `RunWorld.worldWidth()` / `worldHeight()` (`:262-263`) tidak punya pemanggil.
- `Viewport.screenX/screenY/describe()` tidak punya pemanggil.
- `RunWorld.step()` (`:1462`) dipakai AI, bukan kamera.

---

## 3. Audit Seluruh Background Stage

### 3.1 Aset HoloJava saat ini

| key | File | Dimensi | File B | Provenansi | Klasifikasi |
|---|---|---|---|---|---|
| `grass` | `stages/grass.png` | 32×22 | 963 | byte-identik `BG_grass_panel_0` | panel bertepi, bukan full bg |
| `office` | `stages/office.png` | 128×128 | 593 | downscale persis `bg_holoOfficeFloor_0` | **tile lantai** |
| `castle` | `stages/castle.png` | 128×128 | 6362 | byte-identik `BG_castlefloor_0` | tile lantai |
| `carpet` | `stages/carpet.png` | 111×128 | 593 | byte-identik `BG_carpet_0` | runner/pola |
| `yagoo` | `stages/yagoo.png` | 34×97 | 1222 | byte-identik `BG_yagoohead_0` | **prop** |
| `fantasy` | `stages/fantasy.png` | **64×64** | 2049 | crop(160,592) `BG_FantasyIsland_0` | tile rumput (commit `3a9d950`) |
| `concert` | `stages/concert.png` | 56×49 | 1561 | byte-identik `bg_crowd_0` | **prop** |
| `beach` | `stages/beach.png` | 54×320 | 1301 | byte-identik `BG_beachshore_0` | strip; **tidak dipakai** |

Tidak ada satu pun yang merupakan **full background**. Semua consumed via tile-repeat.

### 3.2 Kandidat full background di `unpacked`

Semua dari `HoloCure_Unpacked/texture_groups/bg/Sprites/`:

| Aset | Dimensi | Opaque | File B | Stage cocok |
|---|---|---|---|---|
| `BG_newgrass_0.png` | 1280×1280 | 1.000 | 631.042 | stage1 |
| `BG_newgrass_night_0.png` | 1280×1280 | 1.000 | 476.191 | **stage1h (night)** ✅ |
| `bg_holoOfficeFloor_0.png` | 512×512 | 1.000 | 2.341 | stage2 |
| `bg_holoOfficeFloor_Sunset_0.png` | 512×512 | 1.000 | 2.343 | **stage2h (sunset)** ✅ |
| `bg_officeWall_0.png` | 512×76 | 0.646 | 624 | dinding office (pelengkap) |
| `bg_officeWall_Sunset_0.png` | 512×76 | 0.646 | 619 | dinding office sunset |
| `BG_FantasyIsland_0.png` | 975×1280 | 0.985 | 544.722 | stage5 |
| `BG_CastleWall1_0.png` | 128×257 | 1.000 | 1.523 | dinding castle (strip, bukan lantai) |
| `BG_CastleWall2_0.png` | 128×263 | 0.982 | 865 | dinding castle 2 |
| `BG_castlefloor_0.png` | 128×128 | 1.000 | 6.362 | tile lantai castle |
| `bg_concertstage_0.png` | 1238×1096 | **0.769** | 297.928 | time1 (parsial) |
| `BG_IDArena_0.png` | 2404×1452 | 0.693 | 517.495 | (tidak dipakai stage mana pun) |
| `BG_castle_night_0.png` | 640×360 | **0.000** | 30.442 | **TIDAK DAPAT DIPAKAI** (transparan penuh) |

**Stage tanpa full background yang layak:**

- **stage4 / stage4h (Gelora Bung Yagoo)** — tidak ada. Hanya ada
  `BG_carpet_0` (111×128) dan `BG_yagoohead_0` / `_night_` / `_sunset_` (34×97, prop).
  Varian `_night`/`_sunset` hanya ada untuk **prop**, bukan untuk background.
- **stage3h (Castle Myth)** — tidak ada varian terpisah; `BG_CastleWall1_0` dipakai
  bersama, dan `BG_castle_night_0` transparan penuh sehingga tidak berguna.

### 3.3 Kesesuaian tiap background terhadap world background

| Aset | Ukuran vs viewport 320×240 | Perlu penskalaan? | Perlu clipping? | Perlu fill? |
|---|---|---|---|---|
| `bg_holoOfficeFloor_*` 512×512 | **lebih besar** di kedua sumbu | Ya (1:1 kalau world 512×512; 2:1 ke 256×256) | Ya, viewport lebih kecil | Tidak (opaque 100%) |
| `BG_newgrass_*` 1280×1280 | jauh lebih besar | Ya, wajib (2,0×–2,67× ke 480–640) | Ya | Tidak |
| `BG_FantasyIsland_0` 975×1280 | lebih besar, tapi **potret** | Ya + mungkin letterbox/crop rasio | Ya | Tidak |
| `BG_CastleWall1_0` 128×257 | **lebih kecil di X** | Tidak | Tidak | **Ya di sumbu X** |
| `bg_concertstage_0` 1238×1096 | lebih besar | Ya | Ya | **Ya** (23% transparan → perlu warna latar) |

**Semua penskalaan harus terjadi saat authoring** (pre-bake di PNG), bukan saat runtime —
lihat §4.3.

---

## 4. Evaluasi Strategi Camera Scrolling

### 4.1 Model yang dievaluasi

1. Background berada dalam koordinat world (0,0)–(worldW,worldH).
2. Kamera menentukan area world yang terlihat: `(camX, camY)` = kiri-atas viewport.
3. Posisi layar = `worldXY − camXY + letterboxOffset`.
4. Kamera dibatasi: `0 ≤ camX ≤ max(0, worldW − viewW)`, sama untuk Y.
5. **Jika world ≤ viewport** → `cam = 0` dan background di-center-kan (bukan
   disejajarkan kiri-atas), supaya tidak ada area kosong.

### 4.2 Penilaian tiap klausul terhadap kode sekarang

| Klausul | Kondisi sekarang | Kebutuhan perubahan |
|---|---|---|
| Background dalam koordinat world | ❌ tidak ada; `drawBackground` menerima gambar penuh dan mengulang | ganti ke blit berbasis kamera |
| Kamera menentukan area terlihat | ❌ tidak ada | **tambah `cameraX/cameraY` + clamp** |
| Posisi layar konsisten | ✅ sudah (`offX + worldX` di semua `draw`) | ubah `offX` → `letterboxX − cameraX` |
| Kamera dibatasi world & viewport | ❌ tidak ada | clamp baru |
| World < viewport → center, tanpa area kosong | ⚠️ belum ada; hanya Relevan setelah world jadi milik stage | **perlu kebijakan baru** |
| H & V scrolling sesuai desain | ⚠️ tidak ada; stage tidak punya metadata orientasi | perlu keputusan per stage |

### 4.3 Batasan API MIDP 2.0 — diverifikasi dari stub yang di-pin

`javap` terhadap `deps/java-me/midpapi20-2.0.4.jar`:

**Tersedia:**
- `Image.createImage(String)` / `(InputStream)` / `(byte[],int,int)` / `(int,int)`
- `Image.getWidth()`, `Image.getHeight()`, `Image.getRGB(...)`, `Image.createRGBImage(...)`
- `Graphics.translate(int,int)`, `Graphics.getTranslateX/Y()`
- `Graphics.setClip(int,int,int,int)`, `Graphics.clipRect(...)`, `Graphics.getClipX/Y/Width/Height()`
- **`Graphics.drawRegion(Image,int,int,int,int,int,int,int,int)`** ← kandidat utama
- `Graphics.copyArea(...)`

**TIDAK tersedia di stub ini (wajib dibuktikan, bukan diasumsikan):**
- `Image.getScaledInstance` — **tidak ada** (`grep` di stub: NOT FOUND)
- `Image.getSubimage` — **tidak ada** (NOT FOUND)
- `Image.createScaledImage` — **tidak ada** (NOT FOUND)
- `javax.microedition.lcdui.MutableImage` — **kelas tidak ada** (NOT FOUND)

Probe kompilasi (throwaway di `/tmp`, repo tidak disentuh):
- Panggilan **8 int** → **berhasil dikompilasi** terhadap stub.
- Panggilan **7 int** (arity sesuai javadoc MIDP 2.0) → **gagal compile**:
  `required: Image,int,int,int,int,int,int,int,int`

> ⚠️ **Risiko yang harus FLAGged:** stub yang di-pin mendeklarasikan `drawRegion`
> dengan **8 parameter int**, sedangkan javadoc MIDP 2.0 publik menyebut 7. Kode
> yang dikompilasi terhadap stub 8-int akan wondered apakah cocok di perangkat.
> **Wajib divalidasi di emulator/perangkat nyata sebelum Trails** — jangan diasumsikan.

**Implikasi strategis:** karena `getSubimage`/`getScaledInstance` tidak ada,
- **windowing background tanpa memuat gambar penuh ke heap** harus lewat
  `Graphics.drawRegion(srcX, srcY, srcW, srcH, destX, destY, …)` — bukan
  `Image.getSubimage`.
- **semua penskalaan harus pre-bake saat authoring** (di luar repo, menghasilkan PNG
  yang sudah berukuran final). Ini jugaPag-andalan yang bagus: mengunci ukuran heap.

### 4.4 Interaksi dengan rasio layar

| Layar | Viewport efektif | `offsetX/offsetY` sekarang | Efek kamera |
|---|---|---|---|
| 320×240 (baseline) | 320×240 | (0,0) | kamera justru aktif (world 512×512) |
| 480×240 (luas) | 480×240 | (0,0) | kamera X limited 512−480=32 px, Y 272 px |
| 240×320 (tinggi) | 320×320, offset (−40,0) | crop X | kamera dibatasi view 320, bukan 240 |
| 176×208 (kecil) | 320×240, offset (−72,−16) | crop X&Y | kamera dibatasi view 320×240 |

**Titik rawan:** saat layar lebih kecil dari 320×240, `offsetX` negatif membatasi
bagian world yang terlihat. Kamera **harus** dibatasi terhadap
`min(viewW, worldW)` agar tidak pernah menggeser area yang tidak terlihat.

### 4.5 Rute yang tidak boleh diambil (sesuai instruksi)

Tidak ada parallax, tidak ada zoom, tidak multi-layer. Yaitu `drawBackground`
sekali dengan satu `drawRegion` per frame, atau 최대 4 (blit berulang dengan
klip) — bukan lebih.

---

## 5. Prototipe Pertama yang Direkomendasikan

### 5.1 Stage: **Holo Office (`stage2`) + Holo Office (Evening) (`stage2h`)**

**Bukan dipilih karena asetnya termudah.** Alasannya empat, semuanya soal
*validasi kamera dengan risiko regresi terendah*:

1. **Satu-satunya keluarga stage dengan dua full background/day+sunset.**
   `bg_holoOfficeFloor_0.png` (512×512) dan `bg_holoOfficeFloor_Sunset_0.png` (512×512)
   keduanya **100% opaque**. Ini memvalidasi mekanisme varian malam/sore
   **dalam satu percobaan**, sesuatu yang tidak bisa dilakukan stage lain
   (Grass hanya ada satu night; Castle Myth tidak punya varian sama sekali).
2. **512×512 adalah sumber full background terkecil yang tetap lebih besar dari
   viewport 320×240.** Jadi downscale paling ringan (1:1 ke 512×512, atau 2:1 ke
   256×256), dan kegagalan heap — jika terjadi — terjadi di **stage paling murah**.
   Tidak ada stage lain yang bisa gagal lebih murah.
3. **Pola checker 128 px membuat error kamera langsung terlihat.** Offset kamera
   salah ≥1 px → pola meloncat di screenshot. Screenshot statis menjadi oracle
   yang kuat tanpa perlu menjalankan gameGROUND.
4. **Tidak menyentuh `stage1`** (default yang dipakai semua smoke test), **tidak
   menyentuh `fantasy`** (yang baru diimpor di `3a9d950`), dan bukan stage
   boss-intensif. Regresi terisolasi dan tidak berinteraksi dengan perubahan
   sebelumnya.

### 5.2 Aset yang dipakai

| Stage | Aset sumber | Dimensi sumber | Opaque |
|---|---|---|---|
| `stage2` | `bg_holoOfficeFloor_0.png` | 512×512 | 1.000 |
| `stage2h` | `bg_holoOfficeFloor_Sunset_0.png` | 512×512 | 1.000 |

Keduanya sudah 100% opaque → **tidak perlu warna fill**, tidak perlu tile fallback,
hanya clipping viewport.

### 5.3 Dimensi world & viewport yang usulan

**World 512×512** — dipilih karena:

| World | Heap RGBA | Roll kamera vs 320×240 |
|---|---|---|
| 480×240 | 450 KB | X 160 px, **Y 0 px** (hanya horizontal) |
| **512×512** | **1.024 KB** | **X 192 px, Y 272 px** (kedua sumbu) |
| 640×480 | 1.200 KB | X 320 px, Y 240 px |

512×512 menghasilkan **scroll di kedua sumbu** dari sumber yang **tidak perlu
di-downscale sama sekali** (512 = 512). Inipermalink memberikan jalur nol-kehilangan
piksel, dan membuat perbandingan dengan implementasi saat ini (yang 128×128)
sangat jelas. Viewport tetap **320×240** — tidak berubah.

### 5.4 Perubahan minimal yang dibutuhkan

Tujuh titik, semuanya di dalam scope background/world/kamera/viewport:

| # | File:fungsi | Perubahan |
|---|---|---|
| 1 | `ContentCatalog.StageDef` | tambah 2 field int `worldW`, `worldH` (default 0 = pakai viewport) |
| 2 | `ContentCatalog.stage(...)` | isi 512/512 untuk `stage2`+`stage2h` |
| 3 | `RunWorld.setViewport()` | **berhenti** menimpa `worldWidth/worldHeight` dari viewport; hanya baca ukuran viewport. Jaga clamp 0..world−24 masih berlaku |
| 4 | `RunWorld` | tambah `cameraX`, `cameraY` + method `updateCamera()` dengan clamp `0..max(0, world−view)` dan aturan center bila world<view |
| 5 | `RunWorld.drawBackground()` | ganti tile-loop dengan satu `drawRegion(bg, camX, camY, viewW, viewH, destX, destY, anchor)` + fill warna di luar area gambar |
| 6 | `RunWorld.draw()` | `drawPlayer/enemies/shots/pickups` memakai `letterbox − camera`; **panggil `drawHud` dengan offset layar, bukan offset kamera** |
| 7 | `AssetLibrary` | tambah key background penuh per stage, cache terpisah dari tile |

Yang **tidak** berubah: `updatePlayer`, seluruh AI, collision, `overlap`,
`playerCenterX/Y`, `ContentCatalog.STAGES`除了 2 field baru, gameplay, UI/menu.

### 5.5 Apakah sistem yang ada bisa dipakai ulang?

**Ya, hampir seluruhnya.** Ini/alasan why risk rendah:

- **Sudah dipakai ulang 100%:** `px/py` world coords, semua AI & collision, clamp
  (`RunWorld.java:421-424`), `playerCenterX/Y`, pola `offX + worldX` di `draw()`.
- **`Viewport` dipakai ulang sebagai sumber ukuran layar** — hanya perlunya offset
  kamera yang baru. `Viewport.update()` tidak perlu berubah sama sekali.
- **HUD tidak perlu diubah** semantik; hanya argumennya (layar vs kamera).
- **Satu-satunya kode yang benar-benar baru:** Kalkulasi `cameraX/cameraY` + clamp.
  Sekitar 10 baris.

### 5.6 Dampak

| Aspek | Dampak |
|---|---|
| **Memori** | `office.png` 128×128 (64 KB) → background 512×512 (**1.024 KB**), +16×. Ini bagian paling berisiko dan harus diukur di perangkat nyata. `docs/rebuild/ASSET_INVENTORY.md:137-139` sudah Fertility peringatan "requires J2ME Loader memory testing" — peringatan ini jadi mengikat. |
| **Performa** | Tile-loop `office` sekarang 3×2=6 `drawImage`/frame → jadi **1 `drawRegion`**/frame. Lebih murah. |
| **Collision** | **Tidak berubah.** Collision tetap world-murni (`overlap`/`playerCenterX`) dan tidak pernah melihat kamera. |
| **JAR** | Menambah ~2–5 KB terkompresi (PNG512 checkerboard sangat baik dikompresi: 2.341 B untuk 512²). Tidak masalah. |
| **Gameplay** | Pemain mendapat area 512×512 bukan 320×240 → **musuh spawn lebih jauh** dan `spawnWave` lebih tersebar. Ini perubahan perilaku yang **wajar** tetapi harus dicatat, bukan diabaikan. |

### 5.7 Risiko jika background lebih kecil / lebih besar dari viewport

| Kasus | Perilaku yang harus ditetapkan | Status |
|---|---|---|
| bg < viewport | center, `cam=0`, tidak ada area kosong | **perlu kebijakan** |
| bg > viewport | `drawRegion` dengan src ter-clamp | **perlu implementasi** |
| bg < world |区域 perlu warna fill / tile | perlu untuk stage lain, **tidak** untuk stage2/2h (512² = world²) |
| 480×240 (world 512×512) | kamera X hanya 32 px — konten akan terlihat hampir diam | risiko UX yang nyata, harus diuji |
| Heap habis | `Image.createImage` melempar → sudah ada fallback `fillRect` (`AssetLibrary.java:283-288`), tetapi fallback itu **tidak memuat background**, hanya warna solid | harus diterima eksplisit, bukan diam-diam |

---

## 6. Rencana Verifikasi

### 6.1 Offline (sebelum packaging)

1. **Build**: `./tools/build_fullgame.sh` — harus exit 0.
2. **Verifier**: `python3 tools/verify_fullgame_build.py` — harus `PASS`.
3. **Integritas aset**: PNG baru masuk JAR; `unzip -p` SHA-256 identik dengan file repo.
4. **Uji geometri kamera (headless)**: tabelkan `camX/camY` untuk world 512×512
   dengan view 320×240, 480×240, 240×320 — verifikasi clamp di 0 dan di max, dan
   aturan center ketika `world<view`.
5. **Uji `drawRegion` di perangkat**: konfirmasi arity 8-int benar-benar berjalan
   (lihat §4.3). **Jika gagal, fallback ke `setClip` + `translate` + `drawImage`.**
6. **Simulasi render offline** per resolusi: gambar `drawRegion` yang diharapkan
   dan bandingkan dengan file — memastikan sumber ter-clamp dengan benar.

### 6.2 Runtime (emulator / perangkat)

7. **Kamera mengikuti pemain ke semua arah**: 8 arah (diagonal termasuk), konfirmasi
   (a) `camX` naik saat pemain ke kanan, (b) `camX` turun dan berhenti di 0 di tepi
   kiri, (c) clamp di `worldW−viewW` di tepi kanan, (d) sama untuk Y.
8. **Batas map**: pemain tidak pernah keluar 512×512; tidak ada strip kosong/
   warna fill yang terlihat di tepi.
9. **Sinkronisasi**: musuh, shot, melee, pickup, boss, HP bar — semua harus bergerak
   bersama kamera, dan tabrakan **tidak boleh** berubah (cek: tabrak musuh pada
   posisi world yang sama dengan sebelum).
10. **HUD tidak bergeser**: saat kamera bergerak, HP bar, slot, status, boss bar
    **harus tetap diam di layar**.
11. **Rasio layar**: 320×240, 480×240, 240×320, dan satu device kecil (mis. 176×208).
12. **Memori**: ukur heap sebelum/sesudah muat background 512×512 pada stage2.
    **Kriteria lulus: tidak ada OutOfMemory pada perangkat target.**
13. **FPS**: ukur di stage2 sebelum vs sesudah; tidak boleh turun.
14. **Fallback**: hapus aset sementara → pastikan tidak crash dan warna solid muncul.

### 6.3 Belum bisa diverifikasi

15. **Pixel-parity dengan HoloCure asli** — build YYC tanpa GML (`README.txt:10-11`),
    `obj_parallaxBG` tidak ada di `Objects_Detail.txt`, dan resolusi asli 1280×720.
    Klaim parity harus dinyatakan eksplisit sebagai estimasi.

---

## 7. Rencana Implementasi Bertahap

| Tahap | Cakupan | Keluar |
|---|---|---|
| **T0** | Audit ini | disetujui |
| **T1** | Tambah field `worldW/worldH` ke `StageDef`; ubah `setViewport` agar **tidak** menimpa world; default world = viewport (perilaku saat ini **tidak berubah**) | regresi nol; build hijau |
| **T2** | Tambah `cameraX/cameraY` + clamp + aturan center; pakai di semua entitas; **belum ada** background camera | movement & collision harus identik |
| **T3** | Lepas HUD dari offset kamera | HUD diam; gameplay tak berubah |
| **T4** | Background full image untuk `stage2` saja, 512×512, via `drawRegion` | kamera terlihat; 1 stage uji |
| **T5** | Aktifkan `stage2h` via `_Sunset` | validasi mekanisme varian |
| **T6** | Evaluasi hasil; baru pertimbangkan stage lain | keputusan tahap berikutnya |

T1–T3 **tidak mengubah apa yang terlihat** — hanyaược memindahkan koordinat. Risiko
terpisah dari T4 yang mengubah tampilan. Ini urutan yang dimaksudkan.

---

## 8. Catatan di luar Lingkup

- `RunWorld.drawLevelUp()` (`:1693-1707`) **dead code** (private, tidak dipanggil).
- `RunWorld.worldWidth()/worldHeight()` (`:262-263`), `Viewport.screenX/screenY/describe()`,
  `Viewport.contains()` — **tanpa pemanggil**.
- `drawBackground` mengalokasikan `int[][] colors` (10×3) **setiap frame** —
  anti-pola di CLDC 1.1; seharusnya `static final`. Berada di fungsi yang sama
  dengan proposal, jadi layak diperbaiki bersamaan.
- `docs/rebuild/ASSET_MAPPING.md:87` menyatakan JAR "166 PNG, di bawah 320 KB";
  kenyataan 236 PNG dan 745.281 B. Dokumentasi usang.
- Stage `stage1h`/`stage2h`/`stage3h`/`stage4h` memakai key tile **yang sama** dengan
  stage siang, sehingga varian `_night`/`_Sunset` yang sudah ada tidak pernah dipakai.

---

## 9. Rekomendasi: Satu Perubahan Pertama

**Tahap T1 saja** — dan **tanpa** T2/T3/T4.

> **Ubah ownership ukuran world:** tambahkan dua field `int worldW/worldH` pada
> `ContentCatalog.StageDef`, isi dengan 0 (default) untuk seluruh stage, lalu ubah
> `RunWorld.setViewport()` agar **tidak lagi** menimpa `worldWidth/worldHeight` dari
> viewport — sebagai gantinya memakai nilai stage, dan jatuh ke ukuran viewport
> bila 0.

**Mengapa ini yang pertama:**

1. **Nol perubahan visual.** Dengan default 0 di semua stage, `worldWidth/worldHeight`
   tetap sama dengan viewport persis seperti sekarang. amenable — build harus
   menghasilkan screenshot yang identik.
2. **Membongkar blocker struktural yang nyata.**ITEM berlaku §2.2(b): selama
   `setViewport()` menimpa ukuran world setiap frame, kamera **secara matematis
   mustahil** ada. T1 menghilangkan penghalang itu tanpa membuat kamera pun.
3. **Risiko regresi paling rendah dari semua tahap.** Hanya satu method yang berubah
   Plus dua field data; tidak ada `drawRegion`, tidak ada aset baru, tidak ada
   perubahan memory, tidak ada perubahan pada simulasi.
4. **Membuka jalan yang bisa diverifikasi sendiri.** Setelah T1, `worldW/worldH`
   bisa diisi 512 pada `stage2` **tanpa** kamera —.world jadi 512×512, pemain
   bergerak di area yang lebih luas, dan `drawBackground` masih tile (tampak
  異). Ini membuktikan ownership world sudah benar sebelum kamera masuk, dan
   memberi kita pengukuran heap dunia besar yang dibutuhkan untuk memutuskan
   apakah 512² muat di perangkat.

**Yang sengaja tidak termasuk:** kamera (`cameraX/cameraY`), `drawRegion`, HUD
detachment, aset background baru, dan stage selain `stage2`. Semuanya perlu
pemberitahuan terpisah.

---

*Audit read-only. Tidak ada file tracked yang dimodifikasi. Tidak ada aset yang
dibuat/diubah. Tidak ada staging/commit/push. `fantasy.png` tetap pada blob
`8a2defb…` (commit `3a9d950`). Semua probe toolchain dijalankan di `/tmp/opencode/`
dan tidak menyentuh repo. Analisis visual statis — **belum ada verifikasi runtime**.*