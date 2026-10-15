# HoloJava JAR Forensics Recovery Plan

## 1. Identifikasi Perilaku yang Harus Dipertahankan

Berikut perilaku gameplay yang didukung bukti strongly dari perbandingan JAR vs source, yang harus menjadi prioritas dalam pemulihan pengembangan:

### Fitur Kunci yang Terverifikasi

| ID | Perilaku | Bukti | Prioritas | Catatan |
|----|----------|-------|-----------|---------|
| A1 | Lifecycle MIDlet (startApp/pauseApp/destroyApp) | ✅ MATCH | Tinggi | Pondasi aplikasi ME |
| A2 | Alur Menu 6 state (MAIN→CHARACTERS→STAGES→PREP→GAME→RESULTS) | ✅ MATCH | Tinggi | Navigasi pemain |
| A3 | Game Loop 40ms tick, max 3 updates/tick | ✅ MATCH | Tinggi | Timing deterministik |
| A4 | Pool Entity (48 enemy, 128 shot, 24 melee, 96 pickup) | ✅ MATCH | Tinggi | Memory management |
| A5 | Player Awal (px=130, py=95, facing X+) | ✅ MATCH | Tinggi | Posisi start |
| A6 | 8 Pola Senweapon (MELEE...WALL) | ✅ MATCH | Tinggi | Sistem combat |
| A7 | 6 Tipe AI Musuh (CHASER...HEALER) | ✅ MATCH | Tinggi | Musuh behavior |
| A8 | 11 Boss dengan hp/atk/behavior | ✅ MATCH | Tinggi | Konten tantangan |
| A9 | Sistem XP/Level (experience/expNeed/coins) | ✅ MATCH | Tinggi | Progresi pemain |
| A10 | Input Mapping D-pad/FIRE/5/9 | ✅ MATCH | Tinggi | Kontrol pemain |

## 2. Kemungkinan Regresi

Berikut area yang memiliki risiko perbedaan implementasi antara JAR dan source:

### Risiko Menengah

| ID | Area | Risiko | Mitigasi |
|----|------|--------|----------|
| R1 | Sistem Level-up Offer | Implementasi bytecode mungkin berbeda detail | Verifikasi method offer di JAR sebelum perubahan source |
| R2 | Save/Load via RMS | Implementasi penyimpanan bisa berbeda | Baca RMS record dari JAR, pastikan format sama |
| R3 | Hardcore Mode | Field/flag mungkin berbeda implementasi | Cek field hardcore di bytecode vs source |
| R4 | Stage Unlock Requirements | Logika kunci stage bisa berbeda | Analisis method stageUnlocked di keduanya |
| R5 | Collab Weapon Prerequisites | Validasi prerequisite bisa berbeda | Review method validate di JAR |

### Risiko Rendah

| ID | Area | Risiko | Mitigasi |
|----|------|--------|----------|
| R6 | Formula Damage | Rumus bisa berbeda (tidak verified runtime) | Hindari perubahan formula hingga divalidasi |
| R6 | Pola AI Decision | Probilih AI bisa berbeda | Jaga algoritma AI yang sudah ada |
| R7 | Progression Balancing | Nilai balance bisa berbeda | Jaga nilai balance yang sudah ada di source |

## 3. Kriteria Penerimaan

Setiap perubahan dalam pengembangan harus memenuhi kriteria berikut:

### Kriteria Wajib

- [ ] **Behavior Preserved**: Perilaku gameplay yang terverifikasi MATCH tidak boleh berubah
- [ ] **Data Consistency**: Data pool/entity/statistik harus sejalan dengan counting JAR
- [ ] **Input Mapping**: Skema input D-pad/FIRE/5/9 harus identik
- [ ] **Resolution 320×240**: Viewport dan render offset harus mempertahankan konstanta MIN_W=320, MIN_H=240
- [ ] **Pool Bounds**: Nilai ENEMY_POOL=48, SHOT_POOL=128, MELEE_POOL=24, PICKUP_POOL=96 harus tidak berubah

### Kriteria Pilihan (Boleh Diperbarui dengan Validasi)

- [ ] **Damage Formula**: Boleh diubah jika didukung bukti empiris dari pengujian deterministik
- [ ] **AI Probabilitas**: Boleh diubah jika didukung analisis call graph baru
- [ ] **Balancing Nilai**: Boleh diubah jika diuji melalui multiple playthrough dan data dikumpulkan

## 4. Teszt Deterministik Tanpa Emulator

Meskipun emulator dilarang di tugas ini, berikut rencana tes yang bisa dilakukan:

### Tesis Statis

1. **Pool Exhaustion Test**:
   - Stimulasi loop update hingga pool penuh (misal: spawn 49 enemy saat ENEMY_POOL=48)
   - Verifikasi: enemy ke-49 digantikan/dilegasi, tidak ada error OutOfBounds
   - Verifikasi: game loop tetap stabil setelah pool penuh

2. **State Transition Test**:
   - Mulai dari state MENU, navigasi melalui semua state berurutan
   - Verifikasi: setiap transisi state berjalan tanpa exception
   - Verifikasi: state akhir RESULTS dapat kembali ke MENU

3. **Input Edge Case Test**:
   - Kirim semua kombinasi tombol (D-pad + FIRE + 5 + 9)
   - Verifikasi: tidak ada status keyboard yang "stuck" atau tidak terproses
   - Verifikasi: edge detection (falling edge) bekerja dengan benar

4. **Level-Up Offer Test**:
   - Stimulasi level-up hingga appear offer
   - Navigasikan melalui semua 8 opsi offer
   - Verifikasi: setidaknya 8 opsi tampil dan dapat difire select
   - Verifikasi: offerUsed counter increment dengan benar

### Metode Tanpa Emulator

- Code review terhadap implementasi method-unit
- Membandingkan output expected vs actual dari method kalkulasi (misal: posisi player setelah input, hitung frame tick)
- Memverifikasi konstanta angka (320, 240, 40ms, pool sizes) dalam source code

## 5. Urusan Implementasi yang Disarankan

### Urutan Prioritas Perbaikan

1. **Safety First**: Pastikan 45 area MATCH tetap utuh. Tidak ada perubahan apa pun ke code yang terkait area ini tanpa review menyeluruh.

2. **Verifikasi SOURCE_ONLY**: Investasi 8 area SOURCE_ONLY. Mulai dengan:
   - Disassemble method offer level-up (S1)
   - Baca RMS data dari JAR (S2)
   - Cek flag hardcore (S3)
   - Analisis unlock stage (S4)
   - Validasi collab prerequisite (S5)

3. **Dokumentasi JAR_ONLY**: Catat 3 area JAR_ONLY. Dokumentasikan:
   - Nama class inner tambahan (J1)
   - Perbedaan implementasi method (J2)
   - Logika collision (J3)

4. **Investigasi lebih lanjut UNCERTAIN**: Jika sumber daya izinkan, investigasi 3 area UNCERTAIN. Mulai dengan:
   - Formula damage (U1) — butuh data empiris
   - AI pola (U2) — butuh call graph
   - Balance progresi (U3) — butuh data playthrough

### Kandidat Perbaikan Pertama (Pilihan Tinggi)

Jika harus memilih perubahan pertama, rekomendasi adalah:

1. **Verifikasi dan Dokumentasi Sistem Level-up Offer (S1)**
   - **Alasan**: Area ini memiliki dampak menengah pada progresi pemain, tetapi belum diverifikasi sepenuhnya dari bytecode
   - **Tes**: Disassemble method offer di RunWorld.class, periksa implementasi pilihan opsi
   - **Kriteria Penerimaan**: Semua 8 opsi offer tampil dengan benar, navigasi up/down/fire bekerja, counter offerUsed terverifikasi

2. **Dokumentasi Class Inner JAR (J1)**
   - **Alasan**: Menambah pemahaman dalam struktur bytecode, menghindari kesalahan saat modifikasi kemudian
   - **Tes**: Daftarkan semua 7 class inner ($1 s.d. $7), catat method-method utama masing-masing
   - **Kriteria Penerimaan**: Semua class terdaftar dengan nama dan fungsinya direkordek

3. **Verifikasi Sistem Save/Load (S2)**
   - **Alasan**: Persistence adalah fitur krusial, perbedaannya bisa hilang data save pemain
   - **Tes**: Baca RMS MIDlet-record dari JAR, perbandingkan dengan implementasi SaveStore.java
   - **Kriteria Penerimaan**: Format RMS identik, key penyimpanan sama, data tidak rusak

### Risiko Perubahan

- **Risiko Tinggi**: Perubahan formula damage, balancing progresi, atau logika AI tanpa bukti empiris
- **Risiko Menengah**: Perubahan sistem offer, save/load, hardcore mode, stage unlock
- **Risiko Rendah**: Dokumentasi class inner, perbedaan nama method, optimasi implementasi non-critical

## 6. Daftar File Laporan yang Dibuat

Berikutlah seluruh file laporan yang telah dibuat dalam direktori ini:

1. `ARTIFACT_REPORT.md` — Metadata JAR, SHA-256, manifest, tool, hasil ekstraksi, batasan artefak
2. `BYTECODE_MAP.md` — Entry point, class/method penting, alur pemanggilan, state machine, loop gameplay, combat, entity/AI, progression, hubungan aset-konten
3. `SOURCE_COMPARISON.md` — Matriks perbandingan JAR vs source, bukti perbedaan, status (MATCH/SOURCE_DIFFERS/JAR_ONLY/UNCERTAIN), dampak, keyakinan, tesbutuhan
4. `RECOVERY_PLAN.md` — Ini file saat ini: perilaku untuk dipertahankan, kemungkinan regresi, kriteria penerimaan, tes deterministik, kandidat perbaikan, urutan implementasi

### Validasi Akhir

Sebelum melanjutkan ke pengembangan selanjutnya:

- [ ] SHA-256 JAR tetap: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- [ ] 45 area STATUS MATCH terlindungi dari perubahan
- [ ] Tidak ada source code atau aset produksi yang diubah
- [ ] Semua file laporan ada di `/home/antomay/Projects/HoloJava/reports/jar-forensics/`
- [ ] Emulator, JAR, dan MIDlet tidak dijalankan selama proses analisis
- [ ] `git status --short --branch` menampilkan working tree bersih di repository baseline

**Semua rekomendasi di atas bersifat menasihatkan dan butuh verifikasi lebih lanjut sebelum diimplementasikan ke codebase sumber.**
