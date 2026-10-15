# HoloJava JAR Forensics Artifact Report

## 1. Metadata JAR

- **Path**: `/home/antomay/Projects/HoloJava/../../../Downloads/HoloJavaFullGame.jar` (reference)
- **Full Absolute Path**: `/home/antomay/Downloads/HoloJavaFullGame.jar`
- **File Size**: 312521 bytes (≈305 KB)
- **SHA-256**: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- **Modification Time**: 2026-10-13 21:34:13 +0700
- **Archive Format**: Valid ZIP/JAR (240 entries)
- **JAD File**: Not found within JAR archive

## 2. Manifest Information

```
Manifest-Version: 1.0
MIDlet-Name: HoloJava Full Game
MIDlet-Version: 1.0.0
MIDlet-Vendor: HoloJava
MIDlet-1: HoloJava Full Game, , me.holojava.fullgame.FullGameMIDlet
MicroEdition-Configuration: CLDC
MicroEdition-Profile: MIDP-2.0
```

## 3. Tool yang Tersedia

- `jar` (JDK tools): Tersedia, digunakan untuk `tf`, `xf`, `javap`
- `unzip`: Tersedia, digunakan untuk melihat isi arsip
- Python 3.x: Tersedia, digunakan untuk parsing zipfile secara programatik
- `javap`: Tersedia untuk disassembly method-bytecode (terbatas)
- **Tool yang TIDAK TERSedia**: ProGuard, JD-GUI, CFR decompiler, Fernflower, atau decompiler Java lain
- **Catatan**: Analisis bertahan pada inspeksi statis, `javap -c`, dan parsing arsip manual. Hasil dekompilasi tidak digunakan sebagai ganti source sebenarnya.

## 4. Hasil Ekstraksi

### Struktur Arsip

- **Total Entries**: 240
- **Class Files (.class)**: 26 file
- **Resource Files ( gambar/asset)**: 214 file
- **Package Structure**:
  - `META-INF/` (1 entry: MANIFEST.MF)
  - `me/holojava/fullgame/` (26 class files + AssetLibrary animation sets)
  - `assets/fullgame/` (214 resource gambar)
  - `assets/phase3/` (tidak ditemukan di JAR ini)
  - `assets/prototype-first-slice/` (tidak ditemukan di JAR ini)

### Daftar Class File

1. `me/holojava/fullgame/FullGameMIDlet.class` — Entry point MIDlet
2. `me/holojava/fullgame/FullGameCanvas.class` — Canvas utama, alur menu/game
3. `me/holojava/fullgame/RunWorld.class` — Simulasi deterministik, logika game loop
4. `me/holojava/fullgame/ContentCatalog.class` — Katalog konten (karakter, weapon, item, enemy, boss, stage)
5. `me/holojava/fullgame/AssetLibrary.class` — Pemuat aset dan resource
6. `me/holojava/fullgame/MathUtil.class` — Utilitas matematis
7. `me/holojava/fullgame/SaveStore.class` — Penyimpanan persistensi (RMS)
8. `me/holojava/fullgame/ContentCatalog$BossDef.class` — Definisi boss dalam
9. `me/holojava/fullgame/ContentCatalog$CharacterDef.class` — Definisi karakter dalam
10. `me/holojava/fullgame/ContentCatalog$EnemyDef.class` — Definisi musuh dalam
11. `me/holojava/fullgame/ContentCatalog$ItemDef.class` — Definisi item dalam
12. `me/holojava/fullgame/ContentCatalog$ModeDef.class` — Definisi mode dalam
13. `me/holojava/fullgame/ContentCatalog$StageDef.class` — Definisi stage dalam
14. `me/holojava/fullgame/ContentCatalog$WeaponDef.class` — Definisi weapon dalam
15. `me/holojava/fullgame/ContentCatalog$CollabDef.class` — Definisi collab dalam
16. `me/holojava/fullgame/FullGameCanvas$ImageHolder.class` — Helper dalam
17. `me/holojava/fullgame/FullGameCanvas$ShutdownRequest.class` — Antarmuka shutdownRequest dalam
18. `me/holojava/fullgame/AssetLibrary$AnimationSet.class` — Animasi set dalam
19. `me/holojava/fullgame/AssetLibrary$CharacterAssets.class` — Asset karakter dalam
20. `me/holojava/fullgame/RunWorld$1.class` — Inisialisasi/run-trigger dalam
21. `me/holojava/fullgame/RunWorld$Boss.class` — Class boss dalam
22. `me/holojava/fullgame/RunWorld$Enemy.class` — Class enemy dalam
23. `me/holojava/fullgame/RunWorld$Melee.class` — Class melee within
24. `me/holojava/fullgame/RunWorld$Offer.class` — Offer class dalam
25. `me/holojava/fullgame/RunWorld$Pickup.class` — Pickup class dalam
26. `me/holojava/fullgame/RunWorld$Shot.class` — Shot class dalam

### Daftar Resource Aset (Contoh)

- `assets/fullgame/characters/Ame/` — Sprite karakter Ame
- `assets/fullgame/characters/Gura/` — Sprite karakter Gura
- `assets/fullgame/characters/Calli/` — Sprite karakter Calli
- `assets/fullgame/characters/Ina/` — Sprite karakter Ina
- `assets/fullgame/characters/kiara/` — Sprite karakter Kiara
- `assets/fullgame/enemies/` — Sprite musuh (Shrimp, Sukonbu, Moonafic, dsb.)
- `assets/fullgame/bosses/` — Sprite boss (Fubuzilla, Pekodam, dsb.)
- `assets/fullgame/projectiles/` — Sprite projectile (bullet, axe, bomb, dsb.)
- `assets/fullgame/weapons/` — Sprite weapon (tidak terpisah terpisah, di dalam projectiles)
- `assets/fullgame/stages/` — Sprite stage (grass, office, castle, concert, dsb.)
- `assets/fullgame/ui/` — UI elements (pickup_icon)

### 5. Batasan Artefak

- JAR adalah **artefak referensi**, bukan source code lengkap
- Bisa sudah lewat proses obfuscation/proguard, menyebabkan simbol tidak terbaca sempurna
- Hanya 26 class `.class` yang ter-ekstraksi, sedangkan 214 resource visual hanya berupa aset visual tanpa logika
- Tidak ada file JAD terpisah dalam arsip
- Tidak boleh digunakan sebagai otomatis sumber kebenaran untuk semua fitur
- Perbedaan antara JAR dan source bisa muncul karena:
  - Optimasi compiler
  - ProGuard/obfuscation
  - Perubahan urutan method
  - Representasi data yang berbeda
- Analisis ini bersifat **statis saja**, tidak menjalankan JAR, MIDlet, atau emulator

## 6. Catatan Penting

- SHA-256 JAR: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- Semua findings bersifat **verified hanya dari inspeksi statis**
- Jangan menganggap JAR lebih baru atau lebih benar daripada source tanpa bukti tambahan
- Untuk detail perbandingan lengkap, lihat SOURCE_COMPARISON.md
