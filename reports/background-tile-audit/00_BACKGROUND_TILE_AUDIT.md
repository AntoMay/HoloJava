# Audit Sistem Background Map HoloJava

**Status:** read-only audit. Tidak ada source, aset, script, konfigurasi, atau laporan
yang diedit, dipindahkan, ditimpa, atau dihapus. Tidak ada staging/commit/push.
**Tanggal:** 2026-10-15
**Ruang lingkup:** sumber gambar background, proses cropping sprite tile, rendering background.

---

## 1. Status Repository (Tahap 1 butir 1)

| Item | Nilai |
|---|---|
| Branch | `main` (HEAD `7ec4f68` "Fix splash screen color palette") |
| Tracked tree | **bersih** — `git status --porcelain -uno` mengembalikan output kosong |
| Untracked | 130 entri, seluruhnya di bawah `reports/` (laporan audit sebelumnya + 4 PNG di `reports/holocure-asset-audit/`) |
| Stash | kosong (`git stash list` kosong) |

Commit terakhir: `7ec4f68`, `8614016`, `a311b03`, `b1a813f`, `7bf8691`.
Seluruh file `assets/fullgame/stages/*.png` masuk lewat commit baseline tunggal
`b1239e5 Initial HoloJava project baseline`.

---

## 2. Direktori Source Unpacked dan Aset Background

### 2.1 Lokasi source unpacked

`/home/antomay/Projects/HoloCure_Unpacked` (read-only). 33.494 file PNG terindeks.
`README.txt` mencatat sumbernya: HoloCure - Save the Fans!, GameMaker Studio 2
2024.6, build **YYC** (native), 1280x720.

### 2.2 Resource "Background" GameMaker — hanya 7, semuanya tileset

`index/Backgrounds.txt`:

```
Count: 7
Tile_Tower_SectionA
Tile_Tower_SectionB_Objects
Tile_Tower_SectionB
Tile_Tower_SectionC
Tile_Tower_SectionD
TileSet1
tls_dungeon
```

`index/summary.txt` mengonfirmasi `Backgrounds: 7`. **Tidak ada satu pun resource
Background berukuran "full map"** — ketujuhanya adalah atlas tileset yang wajib
di-slicing:

| Tileset | Dimensi |
|---|---|
| `tilesets/TileSet1.png` | 288x288 |
| `tilesets/Tile_Tower_SectionA.png` | 700x700 |
| `tilesets/Tile_Tower_SectionB.png` | 700x700 |
| `tilesets/Tile_Tower_SectionB_Objects.png` | 420x420 |
| `tilesets/Tile_Tower_SectionC.png` | 440x440 |
| `tilesets/Tile_Tower_SectionD.png` | 340x340 |
| `tilesets/tls_dungeon.png` | 204x272 |

### 2.3 Aset background lantai yang sesungguhnya (full map) di `unpacked`

Aset ini **ada** di source unpacked, di `texture_groups/bg/Sprites/` (416 file):

| Aset | Dimensi | Peran |
|---|---|---|
| `BG_newgrass_0.png` | 1280x1280 | lantai Grassy Plains (rumput penuh, seamless) |
| `BG_newgrass_night_0.png` | 1280x1280 | lantai Grassy Plains malam |
| `bg_holoOfficeFloor_0.png` | 512x512 | lantai Holo Office, pola checker, period 128 |
| `bg_holoOfficeFloor_Sunset_0.png` | 512x512 | lantai Holo Office sore, period 128 |
| `BG_castle_night_0.png` | 640x360 | dinding/atap castle (transparan di tengah) |
| `BG_FantasyIsland_0.png` | 975x1280 | **full map Fantasy Island** (rumput + strip pasir kiri & kanan) |
| `bg_concertstage_0.png` | 1238x1096 | panggung konser (disc) |
| `BG_IDArena_0.png` | 2404x1452 | ID Arena |
| `BG_CastleWall3_0.png` | 256x256 | tile dinding castle |

Artinya: **source yang benar untuk tile ada di `unpacked`**, tetapi HoloJava tidak
memakainya untuk tile — ia memakai sprite `BG_*` berukuran kecil.

---

## 3. Aset Stage HoloJava Saat Ini dan Provenansnya

Direktori: `assets/fullgame/stages/` (8 file, identik byte-per-byte dengan reference JAR,
lihat §6).

| Key | File | Dimensi | Bytes | Provenansi terverifikasi | Peran sebenarnya |
|---|---|---|---|---|---|
| `grass` | `grass.png` | 32x22 | 963 | **byte-identik** `textures_grouped/Sprites/BG_grass_panel/BG_grass_panel_0.png` | panel rumput **bertepi atas** (bukan tile lantai) |
| `office` | `office.png` | 128x128 | 593 | **downscale piksel-persis** dari `bg_holoOfficeFloor_0.png` (Pearson 1.0000, MAD 0.00) | tile lantai asli, sudah benar |
| `castle` | `castle.png` | 128x128 | 6362 | **byte-identik** `BG_castlefloor_0.png` | tile lantai asli, sudah benar |
| `carpet` | `carpet.png` | 111x128 | 593 | **byte-identik** `BG_carpet_0.png` | runner/red carpet (seam-free H & V) |
| `yagoo` | `yagoo.png` | 34x97 | 1222 | **byte-identik** `BG_yagoohead_0.png` | **prop/bust**, bukan tile lantai |
| `fantasy` | `fantasy.png` | 128x128 | 28657 | **downscale** `BG_FantasyIsland_0.png` (Pearson 0.9356) — **bukan crop** | **full map scene** |
| `concert` | `concert.png` | 56x49 | 1561 | **byte-identik** `bg_crowd_0.png` | **prop** (lampu/panggung) |
| `beach` | `beach.png` | 54x320 | 1301 | **byte-identik** `BG_beachshore_0.png` (frame 0 dari 21 frame animasi) | strip pantai vertikal; **tidak dipakai stage mana pun** |

Bukti tambahan:

- 6 dari 8 file **byte-identik** dengan sprite di `unpacked` (dibuktikan dengan md5 pada
  seluruh 33.494 PNG).
- `office.png` dan `fantasy.png` **tidak ada** di `unpacked` — keduanya hasil turunan lokal.
  `docs/rebuild/ASSET_MAPPING.md:72,76,80` dan `docs/rebuild/ASSET_INVENTORY.md:136`
  mengonfirmasi: `office`/`fantasy` adalah "local normalization"/"local crop/resize fallback".
- Uji template-match **nol kecocokan**: tidak ada satu pun dari 8 aset HoloJava yang
  merupakan potongan piksel-persis dari aset background besar di `unpacked`.

---

## 4. Pipeline Aset → Layar

### 4.1 Penemuan dan pemuatan

`src/java-me-fullgame/me/holojava/fullgame/AssetLibrary.java`

- Baris 200-231 — `Image tile(String key)`: dispatch if-chain 8 key
  (`grass`, `office`, `castle`, `carpet`, `yagoo`, `fantasy`, `concert`, dan fallback `beach`),
  masing-masing memanggil `image("/assets/fullgame/stages/<key>.png")` lalu cache di field
  `tileGrass`..`tileBeach` (baris 36-43).
- Baris 282-289 — `private Image image(String path)`:
  ```java
  try { return Image.createImage(path); }
  catch (Exception exception) { failures++; ...; return null; }
  ```
  Pemuatan **lambat**, sekali per stage,dikembalikan `null` bila gagal (lalu fallback
  ke `fillRect` solid).

### 4.2 Mekanisme slicing — TIDAK ADA

`grep` atas `getSubimage|createScaledImage|MutableImage|drawRegion|getRegion|setClip`
di `src/java-me-fullgame/` → **0 hasil**. Yang dipakai hanya
`Image.createImage(String)` (AssetLibrary.java:283) dan
`g.drawImage(img, x, y, anchor)` (RunWorld.java:1484 dst.).

**Tidak ada source rectangle, tidak ada cropping, tidak ada tile size, tidak ada atlas
coordinate, tidak ada slicing.** `Image` disimpan apa adanya sebagai gambar utuh.

### 4.3 Konsumsi di `RunWorld.java`

`src/java-me-fullgame/me/holojava/fullgame/RunWorld.java:1476-1477`
```java
void draw(Graphics g, int offX, int offY, long now) {
    drawBackground(g, offX, offY);
```

`src/java-me-fullgame/me/holojava/fullgame/RunWorld.java:1531-1543`
```java
private void drawBackground(Graphics g, int offX, int offY) {
    int[][] colors = { { 22, 48, 30 }, { 42, 44, 54 }, { 46, 28, 34 }, { 58, 42, 30 },
            { 28, 52, 62 }, { 16, 24, 40 }, { 54, 34, 40 }, { 42, 20, 30 },
            { 18, 18, 34 }, { 36, 20, 52 } };
    int c = stage.palette % colors.length;
    g.setColor(colors[c][0], colors[c][1], colors[c][2]);
    g.fillRect(offX, offY, worldWidth, worldHeight);
    Image tile = assets.tile(stage.tile);
    if (tile != null) {
        int tw = tile.getWidth(); int th = tile.getHeight();
        for (int y = 0; y < worldHeight; y += th) for (int x = 0; x < worldWidth; x += tw) g.drawImage(tile, offX + x, offY + y, Graphics.TOP | Graphics.LEFT);
    }
}
```

**Titik krusial: `tw`/`th` diambil dari `tile.getWidth()`/`getHeight()`.** Kalau tidak ada
source rectangle, maka **ukuran gambar itu sendiri menjadi ukuran tile** — setiap
gambar utuh dipakai sebagai satu tile dan diulang di kedua sumbu.

### 4.4 Konsumsi di `FullGameCanvas.java`

`src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java:366-367`
```java
world.setViewport(viewport);
world.draw(g, viewport.offsetX, viewport.offsetY, System.currentTimeMillis());
```

`Viewport.java:26-33` — `worldW = max(screenW, 320)`, `worldH = max(screenH, 240)`,
`offsetX/offsetY`为中心. Tidak ada masalah viewport terpisah yang ditemukan;
`drawBackground` menutup `worldWidth x worldHeight` penuh.

### 4.5 Pemetaan stage → tile

`ContentCatalog.java:263-274`, field `StageDef.tile` (baris 142):

| Stage | Nama | key tile |
|---|---|---|
| `stage1` | Grassy Plains | `grass` |
| `stage2` | Holo Office | `office` |
| `stage3` | Halloween Castle | `castle` |
| `stage4` | Gelora Bung Yagoo | `carpet` |
| `stage5` | Fantasy Island | `fantasy` |
| `stage1h` | Grassy Plains (Night) | `grass` |
| `stage2h` | Holo Office (Evening) | `office` |
| `stage3h` | Halloween Castle (Myth) | `castle` |
| `stage4h` | Gelora Bung Yagoo (Night) | `yagoo` |
| `time1` | Concert Stage | `concert` |

`beach` **tidak terpakai** (sesuai `ASSET_MAPPING.md:78`).

---

## 5. Bukti: Full Background Digunakan Sebagai Tile

### 5.1 Simulasi literal `drawBackground()` pada world 320x240

Rendering disimulasikan persis mengikuti loop di RunWorld.java:1541
(`drawImage(tile, offX+x, offY+y)` untuk `x += tw`, `y += th`). Hasil:

| key | ukuran | jumlah salinan | hasil visual |
|---|---|---|---|
| `grass` | 32x22 | 10 x 11 = **110** | kisi 110 kotak, tiap kotak bergaris cyan/putih — **rusak** |
| `office` | 128x128 | 3 x 2 = 6 | checkerboard biru-abu, **OK** |
| `castle` | 128x128 | 3 x 2 = 6 | paving batu, **OK** |
| `carpet` | 111x128 | 3 x 2 = 6 | karpet merah + border emas berulang, **dapat diterima** |
| `yagoo` | 34x97 | 10 x 3 = **30** | barisan bust yagoo sebagai lantai — **rusak** |
| `fantasy` | 128x128 | 3 x 2 = 6 | **seluruh scene Fantasy Island tampil 6 kali** — **rusak** |
| `concert` | 56x49 | 6 x 5 = **30** | grid lampu panggung sebagai lantai — **rusak** |
| `beach` | 54x320 | 6 x 1 = 6 | garis pantai menjadi **vertikal** (terbalik orientasi), dan 320px > 240px sehingga terpotong — **rusak** |

### 5.2 Bukti kuantitatif kontinuitas sambungan

Metrik: `wrap` (selisih kolom terakhir vs kolom pertama) dibanding `internal`
(rata-rata selisih antar kolom сосед). Rasio kecil = tile benar-benar seamless.

| key | wrapX | intX | rasio | wrapY | intY | rasio |
|---|---|---|---|---|---|---|
| `fantasy.png` | **77.41** | 6.07 | **12.75** | 6.73 | 3.92 | 1.72 |
| `beach.png` | **148.02** | 8.59 | **17.23** | 9.92 | 1.29 | 7.67 |
| `grass.png` | **107.05** | 19.61 | 5.46 | 110.39 | 26.94 | 4.10 |
| `yagoo.png` | 15.56 | 22.22 | 0.70 | 82.75 | 15.38 | 5.38 |
| `concert.png` | 27.13 | 8.95 | 3.03 | 39.75 | 8.50 | 4.68 |
| `office.png` | 21.78 | 1.31 | 16.62 | 21.78 | 1.30 | 16.77 |
| `castle.png` | 0.43 | 2.40 | 0.18 | 0.48 | 1.99 | 0.24 |
| `carpet.png` | 0.00 | 1.81 | 0.00 | 0.05 | 0.02 | 2.95 |

`office.png` rasio tinggi hanya artefak metric karena isinya checkerboard (langkah
warna dalam = 1,31 sehingga rasio meaningless); pemeriksaan periodenya independen
menunjukkan `bg_holoOfficeFloor_0.png` 512x512 memiliki **periode horizontal dan
vertikal = 128 piksel**, dan `office.png` 128x128 adalah tepat satu periode utuh.

### 5.3 Bukti terkuat: `fantasy.png` adalah full background, bukan tile

- **Bukan crop.** Template-match pada `BG_FantasyIsland_0.png` (975x1280) dengan
  pencarian offset exhaustif: **tidak ada** offset yang menghasilkan kecocokan piksel persis.
- **Bukan sprite terpisah.** md5 seluruh 33.494 PNG: tidak ada yang cocok.
- **Turunan (downscale).** Pearson 0.9356 / MAD 8.10 terhadap downscale 128x128 dari
  `BG_FantasyIsland_0.png`; komposisinya identik — strip pasir kiri, rumput di tengah,
  strip pasir kanan, tambalan bunga berwarna di kanan-atas, batu abu-abu di kiri-bawah.
- **Bukan tile.** Edge kiri ≠ edge kanan (rasio 12.75), jadi tidak mungkin diulang
  horizontal.

Kesimpulan: `fantasy.png` adalah **gambar full background yang diperkecil**, lalu
dipakai sebagai tile yang diulang 3x2. Ini persis cacat yang menjadi target audit.

### 5.4 sourced rect tidak pernah ada — jadi "pengulangan keliru" terjadi karena aset, bukan karena kode

Tidak ada source rectangle di mana pun. Mengganti aset dengan potongan yang benar
otomatis memperbaiki pengulangan, karena `drawBackground` sudah benar secara mekanis
(memakai `getWidth()`/`getHeight()` sebagai langkah).

---

## 6. Perbandingan dengan Implementasi Referensi

### 6.1 Reference JAR bytecode — identik secara struktural

`/home/antomay/Downloads/HoloJavaFullGame.jar` (312.521 bytes) tersedia lokal.
Disassembly `RunWorld.drawBackground` tersimpan di
`reports/evidence-gathering/RunWorld_bytecode.txt:8210-8461`:

```
262: sipush 320
265: sipush 240
268: invokevirtual Graphics.fillRect:(IIII)V
279: getfield  ContentCatalog$StageDef.tile:Ljava/lang/String;
282: invokevirtual AssetLibrary.tile:(Ljava/lang/String;)Ljavax/microedition/lcdui/Image;
294: invokevirtual Image.getWidth:()I        -> istore 7
301: invokevirtual Image.getHeight:()I       -> istore 8
311: sipush 240   (y < 240, step = th)
322: sipush 320   (x < 320, step = tw)
339: bipush 20
341: invokevirtual Graphics.drawImage:(Ljavax/microedition/lcdui/Image;III)V
```

Artinya reference JAR juga **menjadikannya gambar utuh sebagai tile**, tanpa source rect.
HoloJava ACTIONS code-nya dengan benar; HoloJava hanya **menggeneralisasi** `320/240`
menjadi `worldWidth/worldHeight` (sengaja, sesuai javadoc `Viewport.java:8-11`).

### 6.2 Aset reference JAR identik dengan HoloJava

md5 ke-8 file `assets/fullgame/stages/*.png` dari reference JAR **IDENTIK** dengan
HoloJava. Jadi HoloJava bukan mengubah aset — ia mewarisi set aset reference.

### 6.3 Batas bukti — dinyatakan, tidak dikarang

- HoloCure asli adalah **build YYC**; `README.txt:10-11` menyatakan tidak ada GML source.
  Perilaku tile lantai asli **tidak dapat ditiru dengan keyakinan penuh**.
- `index/Rooms_Detail.txt` menunjukkan lantai stage dibangun oleh instance
  `obj_parallaxBG` (rm_HoloOffice, rm_Castle, rm_CastleMyth, rm_HoloOffice_SunSet),
  bukan oleh resource Background. `obj_parallaxBG` **tidak ada** di
  `Objects_Detail.txt`, jadi geometri tile aslinya tidak dapat dipulihkan dari bukti lokal.
- HoloCure asli 1280x720; HoloJava 320x240 logical. Skala yang "benar" bagi HoloCure
  belum tentu benar bagi HoloJava. Dengan demikian target di §7 adalah
  **konsistensi tile yang benar**, bukan replika pixel-perfect.

---

## 7. Perilaku yang Seharusnya

Dari bukti aset:

1. Sprite tile harus **potongan seamless** dari aset background di `unpacked`, bukan
   gambar utuh dan bukan prop.
2. Potongan harus berasal dari aset yang benar secara semantik:
   - lantai rumput ← `BG_newgrass_0.png` / `BG_newgrass_night_0.png`
   - lantai office ← `bg_holoOfficeFloor_0.png` / `bg_holoOfficeFloor_Sunset_0.png`
   - lantai castle ← `BG_castlefloor_0.png` / `BG_CastleWall3_0.png`
   - carpet ← `BG_carpet_0.png`
   - Fantasy Island ← potongan dari bagian **rumput** `BG_FantasyIsland_0.png`
     (bukan seluruh scene)
3. Variant malam/sore memakai aset `_night` / `_Sunset`, karena stage `stage1h`,
   `stage2h`, `stage3h`, `stage4h` punya palet berbeda (palette 5/6/7/8).

---

## 8. Klasifikasi Setiap Map/Stage

| Stage | key | Aset sekarang | Full background / tile set / kombinasi | Verdict |
|---|---|---|---|---|
| `stage1` Grassy Plains | `grass` | BG_grass_panel 32x22 | panel bertepi, bukan tile | **SALAH** — 110 salinan bergaris tepi |
| `stage2` Holo Office | `office` | downscale bg_holoOfficeFloor | **tile** (1 periode 128) | **BENAR** |
| `stage3` Halloween Castle | `castle` | BG_castlefloor 128x128 | **tile** seamless | **BENAR** |
| `stage4` Gelora Bung Yagoo | `carpet` | BG_carpet 111x128 | tile seam-free (runner) | **DAPAT DITERIMA** |
| `stage5` Fantasy Island | `fantasy` | downscale BG_FantasyIsland | **full background** | **SALAH** — target utama |
| `stage1h` Grassy Plains (Night) | `grass` | sama | panel | **SALAH** |
| `stage2h` Holo Office (Evening) | `office` | varian siang, bukan `_Sunset` | tile, tapi palet salah | **SALAH (minor)** |
| `stage3h` Halloween Castle (Myth) | `castle` | sama | tile | **DAPAT DITERIMA** |
| `stage4h` Gelora Bung Yagoo (Night) | `yagoo` | BG_yagoohead bust | **prop** | **SALAH** — 30 bust sebagai lantai |
| `time1` Concert Stage | `concert` | bg_crowd 56x49 | **prop** | **SALAH** — grid lampu sebagai lantai |

Ringkasan: **2 benar, 2 dapat diterima, 6 salah** (termasuk `beach` yang tidak terpakai).

---

## 9. Strategi Cropping dan Koordinat Sumber

Kandidat crop diverifikasi exhaustif di `texture_groups/bg/Sprites/` dengan metrik
seamless yang sama. Skor 0.0 = sambungan sempurna.

| key | Aset sumber | Dimensi sumber | Crop (x, y, size) | Skor seamless | File hasil | Bytes file | Heap 32bpp |
|---|---|---|---|---|---|---|---|
| `grass` | `BG_newgrass_0.png` | 1280x1280 | **(208, 0, 64x64)** | **0.0** | 64x64 | kecil | 16 KB |
| `grass` (night) | `BG_newgrass_night_0.png` | 1280x1280 | (208, 0, 64x64) | **0.0** | 64x64 | kecil | 16 KB |
| `office` | `bg_holoOfficeFloor_0.png` | 512x512 | (8, 8, 128x128) | **0.0** | 128x128 | ~1 KB | 64 KB |
| `office` (sunset) | `bg_holoOfficeFloor_Sunset_0.png` | 512x512 | (8, 8, 128x128) | **0.0** | 128x128 | ~1 KB | 64 KB |
| `castle` | `BG_castlefloor_0.png` | 128x128 | (0, 0, 128x128) | 0.18 | **tidak perlu diubah** | 6362 | 64 KB |
| `castle` (myth) | `BG_CastleWall3_0.png` | 256x256 | (0, 0, 32x32) | **0.0** | 32x32 | kecil | 4 KB |
| `carpet` | `BG_carpet_0.png` | 111x128 | (0, 0, 111x128) | 0.00 | **tidak perlu diubah** | 593 | 55 KB |
| `fantasy` | `BG_FantasyIsland_0.png` | 975x1280 | **(160, 592, 64x64)** | 0.787 | 64x64 | ~4 KB | 16 KB |
| `yagoo` | `BG_carpet_0.png` | 111x128 | (0, 0, 111x128) | 0.00 | 111x128 | 593 | 55 KB |
| `concert` | — | — | **belum terkonfirmasi** | — | — | — | — |

Catatan kehati-hatian:

- **`fantasy` (160, 592, 64x64)** adalah bagian tengah-lautan rumput (x 160-224,
  y 592-656), jauh dari strip pasir kiri (x≈0-160) dan kanan (x≈830-975) serta dari
  tambalan bunga berwarna. Skor 0.787 adalah yang terbaik yang ditemukan pada
  kelandaian 8 px.
- **`concert`**: tidak ditemukan aset lantai panggung yang seamless di `unpacked`.
  `bg_camstagelights_0.png` (640x360) **0% opaque** (overlay cahaya, bukan lantai),
  `bg_concertstage_0.png` (1238x1096) 76.9% opaque (disc panggung). **Kandidat tidak
  dapat dipastikan dari bukti lokal** — harus diputuskan terpisah, jangan ditebak.
- **`beach`**: stage tidak memakainya. Sumber `/unpacked` punya 21 frame
  (`BG_beachshore_0..20.png`, lebar bervariasi 49-101 x 320) sehingga strip 54x320
  yang dipakai adalah frame animasi, bukan tile. **Tidak perlu disentuh** sekarang.
- Variant malam/sore **tidak dapat ditangani tanpa perubahan `ContentCatalog`**
  (saat ini `stage1h` memakai key `grass` yang sama dengan `stage1`). Itu di luar
  scope audit background dan harus jadi pekerjaan terpisah.

### 9.1 Verifikasi visual before/after

Simulasi `drawBackground()` 320x240 dengan aset sekarang vs crop yang usulan
menghasilkan: lantai rumput seamless (64x64, 5x4 = 20 drawImage, bukan 110),
Fantasy Island jadi Rumput polos seamless (bukan scene penuh yang diulang 6 kali),
dan memory `fantasy` turun dari 28.657 B file / 64 KB heap menjadi ~4 KB / 16 KB.

---

## 10. Risiko

### 10.1 Memori Java ME

Perkiraan decoded 32bpp aset stage saat ini: **349.544 B (341,4 KB)** pada 8 aset.
Kasus terburuk runtime: hanya 1 stage aktif, jadi hanya 1 aset stage yang hidup.

| Aset | Heap 32bpp sekarang | Setelah |
|---|---|---|
| `fantasy.png` | 65.536 B | 16.384 B (turun 49 KB) |
| `grass.png` | 2.816 B | 16.384 B (naik 13 KB, tapi sprite drawImage turun 110→20) |
| `castle.png` | 65.536 B | tidak berubah |

CLDC 1.1 tidak memberi batas heap eksplisit; perangkat lama bisa 256-512 KB.
Penurunan 49 KB pada aset stage terbesar yang bermasalah adalahouer sicherayn
manfaat. Total aset stage tidak naik signifikan.

### 10.2 Performa

`drawBackground` memanggil `drawImage` sebanyak `ceil(320/tw) * ceil(240/th)` kali
**per frame** (60 Hz), tanpa cache buffer:

| key | drawImage/frame sekarang | dengan crop 64x64 |
|---|---|---|
| `grass` | **110** | 20 |
| `yagoo` | 30 | 6 |
| `concert` | 30 | — |
| `castle`/`office`/`fantasy`/`carpet` | 6 | 5 |

`grass` saat ini adalah kasus terburuk: 110 panggilan blit per frame. Asset-only fix
serta-membawa penurunan draw call yang signifikan tanpa menyentuh kode.

### 10.3 Kompatibilitas CLDC 1.1 / MIDP 2.0

- Pendekatan **asset-only** (pre-crop saat authoring) **tidak menambah API sama sekali**.
  Tetap `Image.createImage(String)` + `Graphics.drawImage(Image,int,int,int)`, keduanya
  MIDP 2.0. Tidak ada `MutableImage` (MEEP 2.0) dan tidak ada preemptive.
- Catatan: `javax.microedition.lcdui.Image.getSubimage(int,int,int,int)` **ada** di
  MIDP 2.0, jadi opsi runtime-crop memungkinkan. **Tidak direkomendasikan**: itu
  mempertahankan gambar sumber 512x512-1280x1280 yang ter-decode penuh di heap —
  justru menaikkan pemakaian memori, bukan menurunkan.
- `build_fullgame.sh:11` menyalin `assets/fullgame` apa adanya; `verify_fullgame_build.py`
  memverifikasi setiap PNG masuk ke JAR. Asset baru 64x64 RGBA8 tidak acrescenta
  beban tooling.
- Kompresi: asset saat ini berupa PNG RGBA8 dengan palet sangat terbatas
  (grass 17 warna, castle 7, carpet 4). Asset hasil crop dari aset besar akan punya
  lebih banyak warna dan membengkak di file, tetapi **mengecil di heap** — tradeoff
  yang benar untuk Java ME. Bila file terlalu besar, paletisasi/indexed PNG harus
  diuji pada subset warna.

### 10.4 Risiko gameplay/UI

Nol. Tidak ada perubahan pada `ContentCatalog`, `RunWorld` gameplay, `FullGameCanvas`,
ataupun `Viewport`. hanya gambar latar yang berbeda.

---

## 11. Daftar Pengujian yang Diperlukan

Semua pengujian berikut **belum dijalankan** pada audit ini.

### 11.1 Verifikasi offline (sebelum packaging)

1. **Uji seamless otomatis** untuk setiap aset hasil crop: hitung
   `mean|I[:, w-1] - I[:, 0]|` vs `mean|I[:, i+1] - I[:, i]|` dan padanan vertikal;
   kedua rasio harus kecil (< 1.0).
2. **Uji ketepaan crop**: konfirmasi setiap offset crop menghasilkan 64x64/128x128
   piksel yang seluruhnya opaque (`alpha >= 250`).
3. **Uji regresi aset**: `md5sum assets/fullgame/stages/*.png` dan pastikan hanya
   file yang disetujui yang berubah.
4. **Uji build**: `tools/build_fullgame.sh` (javac + proguard + zip + verifier).
5. **Uji ukuran JAR**: bandingkan `stat -c %s dist/java-me-fullgame/HoloJavaFullGame.jar`
   sebelum/sesudah; harus tidak naik bersih.
6. **Simulasi render offline** 320x240 per stage (seperti lampiran §5.1) untuk
   mengonfirmasi tidak ada garis sambungan.

### 11.2 Verifikasi runtime (wajib, belum dilakukan)

7. **Tampilan tiap stage** di emulator: Grassy Plains, Holo Office, Halloween Castle,
   Gelora Bung Yagoo, Fantasy Island, Concert Stage, serta varian Hard — pastikan
   tidak ada garis sambungan, tidak ada pengulangan objek yang jelas (mis. bust yagoo
   tampak 作为 lantai).
8. **Stage dengan layar lebih lebar** (mis. 480x240, 240x320, dan 4:3): pastikan
   `Viewport`.offsetX/offsetY benar dan tile menutupi `worldWidth x worldHeight`
   tanpa celah.
9. **Profil heap** di emulator/handset: ukur `System.freeMemory()`/RSS sebelum dan
   sesudah `AssetLibrary.tile()` untuk stage `fantasy` (yang paling berat) dan
   `castle`.
10. **Frame rate**: ukur FPS pada stage `grass` sebelum/sesudah; target tidak lebih
    buruk dari sebelumnya.
11. **Pemuatan aset gagal**: ensure `AssetLibrary.failures()` tetap 0 dan fallback
    `fillRect` solid tetap bekerja bila aset hilang.

### 11.3 Yang belum bisa diverifikasi

12. **Pixel-parity dengan HoloCure asli** — tidak dapat diverifikasi. Build YYC tidak
    menyediakan GML; `obj_parallaxBG` tidak ada di `Objects_Detail.txt`; dan
    resolusi asli 1280x720 berbeda dari 320x240 logical HoloJava. Setiap klaim
    parity harus dinyatakan eksplisit sebagai estimasi.

---

## 12. Catatan di luar lingkup (ditemukan, tidak diperbaiki)

- `docs/rebuild/ASSET_MAPPING.md:87` menyatakan "JAR berisi 166 PNG dan tetap di bawah
  320 KB terkompresi". Kenyataannya `dist/java-me-fullgame/HoloJavaFullGame.jar` =
  **771.889 B dengan 236 PNG**. Dokumentasi usang.
- `drawBackground` mengallocate array `int[][] colors` (10x3 int) **setiap frame**.
  Alokasi per-frame di CLDC 1.1 adalah anti-pola; seharusnya `static final`.
  Tidakrelated dengancacat tile, tapi berada di fungsi yang sama.
- `stage1h`/`stage2h`/`stage3h`/`stage4h` memakai key tile yang sama dengan stage
  siang masing-masing, sehingga varian malam/sore tidak pernah memakai aset
  `_night`/`_Sunset` yang sudah tersedia di `unpacked`.

---

## 13. Rekomendasi: Satu Perubahan Terkecil

**Ganti satu file aset: `assets/fullgame/stages/fantasy.png`.**

- **Aset sumber:** `/home/antomay/Projects/HoloCure_Unpacked/texture_groups/bg/Sprites/BG_FantasyIsland_0.png` (975x1280)
- **Crop yang dipakai:** `x=160, y=592, w=64, h=64` → simpan sebagai `fantasy.png` RGBA8
- **Skor seamless:** 0.787 (terbaik yang ditemukan pada kelandaian 8 px; bagian
  tengah-lautan rumput, jauh dari strip pasir dan tambalan bunga)

**Alasan memilih ini sebagai perubahan pertama:**

1. Ini **satu-satunya** kasus yang secara bukti memenuhi persis deskripsi cacat:
   satu gambar full background (`BG_FantasyIsland_0`, 975x1280) diperkecil menjadi
   `fantasy.png` lalu diperlakukan sebagai tile yang diulang 3x2 — dengan sambungan
   horizontal 12.75x lebih besar dari langkah internal.
2. **Satu file, nol perubahan kode**, nol perubahan `ContentCatalog`, nol perubahan
   viewport, nol perubahan gameplay/UI.
3. **Berkurang**, tidak bertambah: file 28.657 B → ~4 KB, heap 65.536 B → 16.384 B,
   drawImage/frame 6 → 5.
4. Source dan koordinat crop **terverifikasi exhaustif**, bukan hasil tebakan.

**Yang sengaja TIDAK disertakan** (agar perubahan tetap terkecil dan terverifikasi):
perbaikan `grass`, `yagoo`, `concert`, varian `_night`/`_Sunset`, penambahan API
cropping di `AssetLibrary`, serta `static final` pada array palet. Semua perlu
pemberitahuan terpisah.

---

*Laporan ini read-only. Tidak ada file tracked yang dimodifikasi, tidak ada aset yang
disalin/ditimpa, tidak ada perintah destruktif, tidak ada staging/commit/push.
Seluruh analisis gambar dijalankan di `/tmp/opencode/bgstage/`.*