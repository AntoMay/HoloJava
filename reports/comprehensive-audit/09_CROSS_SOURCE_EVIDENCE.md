# HoloJava Comprehensive Audit - Cross-Source Evidence Matrix (REVIEW VERSION)

## 1. Hierarki Otoritas Bukti

Hierarki berikut digunakan sesuai pertanyaan yang sedang diuji:

1. **Perilaku dan kontrak yang dimaksudkan untuk HoloJava**: spesifikasi HoloJava yang disetujui, dokumentasi proyek, dan keputusan eksplisit pengguna.
2. **Perilaku reference HoloJava**: bukti dari `HoloJavaFullGame.jar`, dibandingkan dengan source code HoloJava.
3. **Implementasi HoloCure**: bukti dari source atau artefak lokal yang berhasil diidentifikasi.
4. **Penjelasan mekanisme HoloCure**: wiki dan dokumentasi teknis yang relevan.
5. **Hipotesis auditor**: interpretasi yang belum didukung bukti langsung.

Hierarki ini bukan berarti sumber tingkat bawah tidak berguna. Artinya, sumber-sumber tersebut tidak boleh digunakan untuk mengesampingkan bukti yang lebih relevan terhadap pertanyaan yang sedang diuji.

## 2. Matriks Bukti Lintas-Sumber

Untuk setiap topik yang relevan, catat:

- ID temuan terkait.
- Subsystem HoloJava.
- File dan baris HoloJava.
- Bukti reference JAR.
- Path dan file dari `HoloCure_Unpacked`, jika relevan.
- Path dan file dari `HoloCure`, jika relevan.
- URL wiki atau dokumentasi.
- Versi atau konteks setiap sumber.
- Apakah sumber langsung, tidak langsung, atau hanya berupa hipotesis.
- Apakah bukti konsisten atau bertentangan.
- Kesimpulan yang didukung bukti.
- Tingkat keyakinan dan pertanyaan yang belum terjawab.

### Contoh Format:

| ID | Subsystem HoloJava | File & Baris | Bukti JAR | HoloCure_Unpacked | HoloCure | Wiki | Versi | Konsistensi | Keyakinan |
|----|-------------------|-------------|-----------|-----------------|----------|------|-------|-------------|-----------|
| AUD-001 | Lifecycle MIDlet | `FullGameMIDlet.java:10-15` | ✅ MATCH | — | — | Wiki: HoloCure lifecycle | 1.0.0 | ✅ Konsisten | CONFIRMED |
| AUD-002 | Choose Offer Type | `RunWorld.java:1127-1136` | ⚠️ PARTIAL | — | — | Wiki: skill mechanics | Versi 1.0 | ⚠️ Pending | STRONG_EVIDENCE |

## 3. Perluasan `01_SOURCE_INVENTORY.md`

Perluas `01_SOURCE_INVENTORY.md` untuk mencatat ketiga sumber tambahan secara terpisah:

- **HoloJava source code**: file di `/home/antomay/Projects/HoloJava/src/`
- **Reference JAR**: `/home/antomay/Downloads/HoloJavaFullGame.jar`
- **HoloCure_Unpacked**: status "belum diverifikasi path" / "tidak ditemukan" / "diinspeksi parial"
- **HoloCure**: status serupa

Setiap sub-bagian dalam `01_SOURCE_INVENTORY.md` harus memiliki bagian tambahan:

```
Sumber HoloJava: [status]
Reference JAR: [status dengan bukti]
HoloCure_Unpacked: [status dan path]
HoloCure: [status dan path]
Wiki: [URL + topik + konsistensi]
```

## 4. Perluasan `04_JAR_PARITY_MATRIX.md`

Perluas `04_JAR_PARITY_MATRIX.md` hanya untuk parity antara HoloJava dan reference JAR; bukti HoloCure harus ditempatkan dalam matriks lintas-sumber agar tidak mencampur dua jenis pembandingan.

**Aturan**: Kolom "Bukti JAR" hanya mencakup perbandingan HoloJava vs JAR. Semua perbandingan dengan HoloCure ditempatkan di `09_CROSS_SOURCE_EVIDENCE.md`.

## 5. Perluasan `08_COVERAGE_AND_UNVERIFIED.md`

Perbarui `08_COVERAGE_AND_UNVERIFIED.md` dengan:

- Sumber wiki yang berhasil diperiksa: [daftar URL + topik]
- Sumber yang tidak tersedia: [daftar alasannya]
- Direktori lokal yang tidak ditemukan atau tidak dapat diakses: [daftar path]
- File yang tidak diperiksa dan alasannya: [daftar file + alasan]
- Perbedaan versi atau konteks: [daftar perbedaan versi HoloJava, JAR, HoloCure]
- Konflik antarsumber: [daftar konflik dan penyelesaian/alasan ketidakconsistensian]
- Hal yang tidak dapat dibuktikan melalui analisis statis: [daftar]

Jika salah satu direktori tidak tersedia, laporkan kegagalan path secara eksplisit. Jangan mencari proyek pengganti atau menggunakan direktori lain tanpa persetujuan.

Tetap patuhi semua batasan audit-only dari prompt utama. Tidak boleh ada perubahan source code, aset, build output, dependency, Git history, atau implementasi selama tahap ini.

## 6. Contoh Entry Matriks Lintas-Sumber (Untuk Disiapkan)

| ID | Subsystem | File HoloJava | Bukti JAR | HoloCure_Unpacked | HoloCure | Wiki | Versi | Konsistensi | Keterangan |
|----|-----------|-------------|-----------|-----------------|----------|------|-------|-------------|------------|
| AUD-001 | Lifecycle | `FullGameMIDlet.java:10-15` | ✅ MATCH | ✅ Ditemukan di `src/` | ✅ Ditemukan | `https://holocure-wiki.vercel.app/docs/games/` | 1.0.0 | ✅ Konsisten | Logika match persisten |
| AUD-002 | Offer Type | `RunWorld.java:1127-1136` | ⚠️ PARTIAL | ❓ Belum diverifikasi | ❓ Belum ditemukan | `https://holocure-wiki.vercel.app/docs/games/skill/` | Versi 1.0+ | ⚠️ Pending | Butuh verifikasi distro |
| AUD-003 | Damage Formula | `RunWorld.java:1270-1274` | ❓ UNCERTAIN | ❓ Belum diuji | ❓ Belum diuji | `https://holocure-wiki.vercel.app/docs/damage-modifier/` | Tidak diketahui | ❓ Belum tahu | Butuh verifikasi runtime |

## 7. Aturan Cakupan dan Pelaporan

Perluas `01_SOURCE_INVENTORY.md` untuk mencatat ketiga sumber tambahan secara terpisah. Perluas `04_JAR_PARITY_MATRIX.md` hanya untuk parity antara HoloJava dan reference JAR; bukti HoloCure harus ditempatkan dalam matriks lintas-sumber agar tidak mencampur dua jenis pembanding.

Perbarui `08_COVERAGE_AND_UNVERIFIED.md` dengan:

- Sumber wiki yang berhasil diperiksa: [daftar]
- Sumber yang tidak tersedia: [daftar]
- Direktori lokal yang tidak ditemukan atau tidak dapat diakses: [daftar]
- File yang tidak diperiksa dan alasannya: [daftar]
- Perbedaan versi atau konteks: [daftar]
- Konflik antarsumber: [daftar]
- Hal yang tidak dapat dibuktikan melalui analisis statis: [daftar]

Jika salah satu direktori tidak tersedia, laporkan kegagalan path secara eksplisit. Jangan mencari proyek pengganti atau menggunakan direktori lain tanpa persetujuan.

Tetap patuhi semua batasan audit-only dari prompt utama. Tidak boleh ada perubahan source code, aset, build output, dependency, Git history, atau implementasi selama tahap ini.
