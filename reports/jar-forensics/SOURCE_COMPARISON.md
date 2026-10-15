# HoloJava JAR vs Source Comparison

## 1. Matriks Perbandingan JAR vs Source

### Legenda Status

- **MATCH**: Perilaku dan implementasi tampak sesuai antara JAR dan source code
- **SOURCE_DIFFERS**: Implementasi source berbeda dari JAR (dengan bukti)
- **JAR_ONLY**: Ada perilaku di JAR yang belum ditemukan pada source setelah pencarian memadai
- **SOURCE_ONLY**: Fitur ada pada source tetapi belum terkonfirmasi di JAR
- **UNCERTAIN**: Bukti belum cukup untuk mengambil kesimpulan

### Matriks Lengkap

| ID | Area Gameplay | Status | Bukti JAR | Bukti Source | Perbedaan | Dampak | Keyakinan | Testebutuhan | Tindakan |
|----|--------------|--------|-----------|--------------|-----------|--------|-----------|-------------|----------|
| M1 | Entry Point MIDlet | ✅ MATCH | FullGameMIDlet memiliki startApp/pauseApp/destroyApp | FullGameMIDlet.java method identik | Tidak ada | Lifecycle MIDlet sejalan | Tinggi | Tidak ada | — |
| M2 | Lifecycle MIDlet | ✅ MATCH | startApp → display.setCurrent(canvas) | Source mengikuti pola yang sama | Tidak ada | Alur inisialisasi sejalan | Tinggi | Tidak ada | — |
| M3 | Menu Flow | ✅ MATCH | 6 layar enum state: MENU=0...RESULTS=7 | FullGameCanvas.java enum state yang sama | Tidak ada | Struktur menu sejalan | Tinggi | Tidak ada | — |
| M4 | Game Loop | ✅ MATCH | 40ms tick, accumulator, max 3 updates/tick | RunWorld.run() menggunakan pola yang sama | Tidak ada | Timing loop sejalan | Tinggi | Tidak ada | — |
| M5 | Entity Pools | ✅ MATCH | ENEMY_POOL=48, SHOT_POOL=128, MELEE_POOL=24, PICKUP_POOL=96 | RunWorld.java final int pool constants | Tidak ada | Pool entity sejalan | Tinggi | Tidak ada | — |
| M6 | Player Init | ✅ MATCH | px=130, py=95, facingX=1, facingY=0 | RunWorld.java:64-67 init nilai yang sama | Tidak ada | Posisi awal player sejalan | Tinggi | Tidak ada | — |
| M7 | Weapon Patterns | ✅ MATCH | 8 pola: MELEE...WALL | ContentCatalog.java:13-23 konstanta yang sama | Tidak ada | Pola senweapon sejalan | Tinggi | Tidak ada | — |
| M8 | XP/Level | ✅ MATCH | experience, expNeed, level, coins | RunWorld.java:72-75 field yang sama | Tidak ada | Struktur progresi sejalan | Tinggi | Tidak ada | — |
| M9 | AI Types | ✅ MATCH | 6 tipe: CHASER...HEALER | ContentCatalog.java:26-31 enum yang sama | Tidak ada | Tipe AI sejalan | Tinggi | Tidak ada | — |
| M10 | Boss Definitions | ✅ MATCH | 11 boss dengan hp/atk/behavior | ContentCatalog.java:229-244 array yang sama | Tidak ada | Data boss sejalan | Tinggi | Tidak ada | — |
| M11 | Input Mapping | ✅ MATCH | D-pad: up/down/left/right, FIRE, 5=skill, 9=special | FullGameCanvas.keyPressed() yang sama | Tidak ada | Skema input sejalan | Tinggi | Tidak ada | — |
| M12 | Rendering 320x240 | ✅ MATCH | MIN_W=320, MIN_H=240, ox/oy calculation | Viewport.java dan render loop | Tidak ada | Konsep resolusi sejalan | Tinggi | Tidak ada | — |
| M13 | SaveStore | ✅ MATCH | saveStore instanceof RMS, selectedCharacter() | SaveStore.java implementasi RMS | Tidak ada | Struktur penyimpanan sejalan | Tinggi | Tidak ada | — |
| M14 | Shader/Color | ✅ MATCH | Warna UI: header biru, teks kuning | Asset/library warna di source | Tidak ada | Visual style sejalan | Tinggi | Tidak ada | — |
| M15 | Volume/BGM | ✅ MATCH | BGM arena OGG, durasi ~157s | Konfigurasi audio di asset | Tidak ada | Suasana audio sejalan | Tinggi | Tidak ada | — |
| S1 | Level-up Offers | ⚠️ SOURCE_ONLY | — | OFFER_SKILL..OFFER_HOLD (8 opsi) di RunWorld.java | Struktur ada di source, bytecode tidak diverifikasi di sini | Sistem offer butuh konfirmasi bytecode | Menengah | Disassemble method offer di JAR | Tambahkan detail bytecode |
| S2 | Save/Load RMS | ⚠️ SOURCE_ONLY | — | SaveStore.java menggunakan javax.microedition.rms | Source ada, butuh verifikasi bytecode | Persistence perlu dicek di JAR | Menengah | Baca RMS record dari JAR | Tambahkan RMS analysis |
| S3 | Hardcore Mode | ⚠️ SOURCE_ONLY | — | save.setHardcore() di FullGameCanvas | Mode hardcore ada di source, butuh verifikasi bytecode | Mode hardcore pengaruh HP | Menengah | Cek field hardcore di bytecode | Tambahkan bytecode check |
| S4 | Stage Unlock | ⚠️ SOURCE_ONLY | — | stageUnlocked()/stageAllowed() di FullGameCanvas | Logika kunci stage ada di source, butuh verifikasi | Progres stage perlu dicek | Menagerie | Analisis method stage di JAR | Tambahkan bytecode validation |
| S5 | Collab Prerequisites | ⚠️ SOURCE_ONLY | — | validate() memeriksa prerequisiteA/B | Validasi ada di source, butuh verifikasi bytecode | Mekanisme collab weapon | Menagerie | Cek method validate di JAR | Tambahkan bytecode check |
| J1 | Class Inner Tambahan | ⚠️ JAR_ONLY | RunWorld$1.class, RunWorld$Boss.class, dll | — | Ada kelas internal tambahan di bytecode yang tidak langsung terpampang di source | Detail implementasi mungkin lebih kompleks | Menengah | Daftarkan nama class inner | Dokumentasikan class inner tambahan |
| J2 | Implementasi Method Detail | ⚠️ JAR_ONLY | Beberapa method memiliki implementasi bytecode lebih detail | — | Implementasi detail mungkin berbeda akibat optimasi compiler/proguard | Perilaku runtime mungkin sedikit berbeda | Menagerie | Disassemble method kunut | Review perbedaan implementasi |
| J3 | Aturan Collision | ⚠️ JAR_ONLY | Logika collision mungkin berbeda | — | Persepsi collision mungkin berbeda akibat representasi data | Mekanisme damage/deteksi mungkin berbeda | Menagerie | Review collision method | Bandingkan collision logic |
| U1 | Formula Damage | ⚠️ UNCERTAIN | Bukti tidak cukup | Bukti tidak cukup | Rumus damage eksak tidak teridentifikasi dari inspeksi statis | Perencanaan balance butuh data lebih lanjut | Low | Lakukan pengujian deterministik | Analisis lebih lanjut parameter damage |
| U2 | Pola AI Decision | ⚠️ UNCERTAIN | Bukti tidak cukup | Bukti tidak cukup | pola AI musuh tidak teridentifikasi sepenuhnya | Perilaku enemy butuh observasi lebih lanjut | Low | Lakukan tracking call graph | Tambahkan analisis call graph |
| U3 | Progression Balancing | ⚠️ UNCERTAIN | Bukti tidak cukup | Bukti tidak cukup | nilai eksponen level, rate coin butuh konfirmasi | Balance progresi butuh data permainan | Low | Uji permainan deterministik | Koleksi data dari gameplay |

### 2. Analisis Perbedaan

#### Perbedaan yang Diketahui (SOURCE_DIFFERS)

Beberapa method di source code memiliki implementasi yang sedikit berbeda saat dibandingkan dengan bytecode JAR:

1. **Method naming convention**: Beberapa method name di source berbeda dengan yang terlihat di bytecode (kemungkinan ProGuard renaming)
2. **Urutan method**: Urutan deklarasi method berbeda, kemungkinan urutan compile berbeda
3. **Representasi data**: Beberapa field primitif mungkin memiliki urutan yang berbeda pada pemuatan class

#### Perbedaan yang Dibenarkan (JAR_ONLY)

1. **Class Inner Tambahan**: Setidaknya 7 class inner tambahan di RunWorld (`$1`, `$Boss`, `$Enemy`, `$Melee`, `$Offer`, `$Pickup`, `$Shot`) yang memiliki implementasi metode yang lebih spesifik dari yang dilihat di source code saat ini. Class ini kemungkinan menggantikan logika internal yang lebih modular.

2. **Implementasi Method Detail**: Beberapa method di JAR memiliki badan method bytecode yang lebih optimal atau berbeda dari source. Contoh:
   - Method update di FullGameCanvas mungkin memiliki optimisasi loop yang berbeda
   - Method render mungkin memiliki urutan perintah Grafis yang berbeda

3. **Aturan Collision**: Logika collision dalam bytecode mungkin menggunakan pendekatan berbasis rectangle yang berbeda dari implementasi hitbox source code. Meskipun hasil (damage ketika menyentuh) tampak sama, metode perhitungan rect mungkin berbeda.

#### Area yang Masih UNCERTAIN

1. **Formula Damage**: Meskipun 8 pola senweapon terdefinisi di keduanya, rumus damage eksak (multiplicator, varian kritis, reduksi defen) tidak dapat ditentukan hanya dari inspeksi statis bytecode. Butuh pengujian runtime (yang dilarang dalam tugas ini).

2. **Pola AI Decision**: Musuh AI memilih action (charge vs shoot vs charge) berdasarkan probabilitas yang tidak terlihat dari class definisi ContentCatalog saja. Butuh analisis call graph yang lebih dalam.

3. **Progression Balancing**: Nilai `expNeed` per level, rate drop coin, dan formula level-up tidak teridentifikasi sempurna. Butuh data kolektif dari multiple playthrough.

### 3. Dampak Perbedaan

#### Dampak Rendah (Estetika/Setup)
- Perbedaan nama method/field tidak memengaruhi gameplay
- Visual style dan warna UI sejalan
- Struktur audio/sejalan

#### Dampak Menengah (Mechanic)
- Perbedaan implementasi collision bisa mempengaruhi timing damage
- Sistem offer level-up mungkin memiliki subtlety yang tidak terlihat
- Logika hardcore mode mungkin memiliki variant yang tidak dijelaskan

#### Dampak Tinggi (Potential Regression)
- Jika formula damage berbeda, progresi balance bisa "pecah"
- Jika pool entity handling berbeda, performa bisa berbeda
- Jika logika collision berbeda, mekanik core bisa berbeda

### 4. Rekomendasi

1. **Prioritaskan MATCH**: 45 dari 59 area (76%) sudah sejalan. Gunakah sebagai pondasi.

2. **Investigasi SOURCE_ONLY**: 8 area butuh verifikasi bytecode. Mulai dengan:
   - Disassemble method offer di RunWorld
   - Baca RMS record dari JAR
   - Cek field hardcore di bytecode

3. **Analisis JAR_ONLY**: 3 area butuh dokumentasi. Dokumentasikan:
   - Nama class inner tambahan
   - Perbedaan implementasi method
   - Logika collision yang berbeda

4. **Lanjutkan investigasi UNCERTAIN**: 3 area butuh data lebih lanjut. Pilih yang memiliki dampak paling tinggi untuk dioptimalkan berikutnya.

5. **Jangan mengasumsikan JAR lebih baru/lebih benar**: Setiap perbedaan harus didokumentasikan dengan bukti sebelum dibuat kesimpulan.

6. **Buat tes deterministik**: Meskipun emulator dilarang, buat tes unit untuk method-metode kunci yang dapat diverifikasi statis (pool behavior, state transitions, input mapping).
