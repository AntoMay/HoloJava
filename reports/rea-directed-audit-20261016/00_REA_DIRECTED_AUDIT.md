# REA Directed Audit — Pre-Tahap B–H (2026-10-16)

Status: REA **tidak dijalankan** (terhalang environment). Audit statis artefak referensi dilakukan sebagai pengganti. **Tidak ada perubahan kode gameplay** pada tugas ini; baseline tidak diubah.

## 1. Audit REA: versi, dependensi, entry point, kemampuan

Lokasi: `/home/antomay/Projects/tools/rea/`
Versi: `rea-agents 6.5.0` (`package.json:2-5`).

- Requirement runtime (`package.json:44-46`): `node ^22.19.0 || ^24.11.0 || >=26.0.0`, `npm@11.16.0`. File `.nvmrc`: `24.18.0`.
- Lingkungan aktual (terverifikasi via `node --version; npm --version`):
  - `node v20.19.2` → **tidak memenuhi** syarat minimum (22.19).
  - `npm 9.2.0` → di bawah `npm@11.16.0` yang dipin.
- Entry point: `scripts/rea.mjs` (`package.json:40-43`, bin `rea`/`rea-agents`). Script mencek `dist/cli-runtime/cli.js` / `dist/mcp/main.js` sebelum import.
- Dependensi/artefak build:
  - `dist/` **tidak ada** (`ls` gagal).
  - `node_modules/` **tidak ada** (`ls` gagal).
  - Artinya runtime terkompilasi belum dibangun; menjalankan REA membutuhkan `npm ci && npm run build:cached`, yaitu menginstal paket + build — **dilarang tanpa izin eksplisit** (ketentuan tugas #2), sehingga tidak dilakukan.
- Perintah yang benar-benar dijalankan (read-only, tanpa instalasi):
  - `node /home/antomay/Projects/tools/rea/scripts/rea.mjs --help` → output: `REA's compiled runtime is missing. Run npm ci && npm run build:cached ...` Exit 0 (pesan ke stderr, exit code dari skrip tetap 0 pada pemanggilan ini).
  - `grep -ri gamemaker|data.win|gml|undertalemod|utmt docs src` → hanya satu hit tidak relevan (`src/application/WebRuntimeService.ts:75`, tentang `window.requested_ms`, bukan GameMaker). Tidak ada provider/dokumen GameMaker.
  - Inventaris CLI (`src/cliCommandNames.ts`): perintah `analyze/inspect/decompile/xrefs/trace`, `inspect-managed-artifact` (.NET), `analyze-javascript-application`, Android/APK, firmware, WASM, EVM, SQLite, browser, dsb. **Tidak ada perintah `data.win` / GameMaker / GML / YYC.**
  - Tabel kemampuan (`README.md:164-194`): native (butuh Hopper/Ghidra/IDA), ELF layout (butuh pwntools), Go/Rust/WASM/EVM/SQLite/JS/.NET/APK/firmware/pcap, dsb. Tidak ada baris GameMaker.
- Provider native di host ini: `ps` (procps-ng 4.0.4) ada; `java`, `adb` ada; `ghidra/hopper/ida` **tidak ditemukan di PATH**. Jadi kalaupun REA bisa dibangun, analisis native dalam (decompile HoloCure.exe YYC) tetap butuh provider yang tidak tersedia + Node yang tidak memenuhi syarat.

Kesimpulan audit: REA **tidak dapat dijalankan dengan aman** di lingkungan saat ini. Klaim sebaliknya tidak dibuat.

## 2. Mengapa REA tidak relevan langsung untuk `data.win` YYC

Referensi proyek (`HoloCure_Unpacked/README.txt:10-12`): GameMaker Studio 2 v2024.6, build **YYC → logika game terkompilasi native, tidak ada source GML di `data.win`**. Yang tersedia: strings (~81.613 baris), binary strings dari exe, sprite/teksur/audio/font/mask/shader, daftar resource, objek, room.

REA 6.5.0 tidak mengklaim kemampuan membongkar `data.win` YYC menjadi GML. Jalur terdekat yang didukung REA hanyalah inspeksi `HoloCure.exe` sebagai biner native (strings/simbol/pseudocode via provider eksternal), bukan rekonstruksi semantik GameMaker. Maka: (a) membangun/menjalankan REA tidak akan memulihkan formula damage/cooldown/probabilitas/stat; (b) audit ini tidak mengarang source GML, formula, cooldown, probabilitas, atau nilai stat.

## 3. Audit statis pengganti (read-only) — lima karakter, senjata, skill, special, combat, item, lifecycle

Metode: `grep`/`ls` read-only ke `/home/antomay/Projects/HoloCure_Unpacked` + baca `src/java-me-fullgame/.../ContentCatalog.java`, `RunWorld.java`, `AssetLibrary.java`, `docs/GAME_MECHANICS.md`. Tidak ada file referensi yang ditulis/diubah.

### 3.1 Lima karakter (Myth)

Temuan langsung (direct):
- `ContentCatalog.java:184-190` mendefinisikan tepat 5 `BASE_CHARACTERS` playable: `gura` (Gawr Gura), `ame` (Amelia Watson), `calli` (Mori Calliope), `kiara` (Takanashi Kiara), `ina` (Ninomae Inanis). Komentar `181-183`: urutan load-bearing untuk `AssetLibrary.groupIndex` dan `mainWeapon` harus resolve ke `BASE_WEAPONS`.
- Sprite referensi terverifikasi ada untuk kelimanya, contoh: `spr_Ame_idle/bullet/bubba/Clock*`, `spr_Calli_idle/run/death/specialfx*/Slash1/Perk*`, dan pola penamaan `spr_<Nama>_O{1..4}_idle/run` (teramati untuk Ame, Calli; Gura/Kiara/Ina mengikuti konvensi direktori sprite yang sama, total 3.362 direktori sprite).
- String referensi: 311 baris cocok pola `gura|amelia|calli|kiara|inanis` di `strings/strings.txt`; contoh langsung: `gura`, `calli`, `kiara`, `GuraTridentThrust`, `CalliSlash1`, `KiaraSlash`, `GuraSpecial`, `CalliSpecial`, `KiaraPhoenix`, `GuraSharkSpecial`, `guraClear/Gachikoi` dkk.
- Objek referensi: `obj_TitleChar_Ame/Ayame/Calli/Gura/Ina/Kiara/Watame`, `obj_Player`, `obj_PlayerManager`, `obj_Enemy*` ada di `index/GameObjects.txt`.

Inferensi (ditandai, bukan bukti internal):
- Nama string seperti `GuraSharkSpecial` menunjukkan *label* fitur, bukan bukti cara kerja internal (durasi, radius, damage). Sesuai ketentuan #5, tidak diperlakukan sebagai source.

Belum diketahui (unknown):
- Stat asli (HP/ATK/SPD/CRIT), hitbox, kecepatan, dan efek skill/special asli. Nilai di `ContentCatalog.java:185-189` (mis. Gura 65/110/140/5) adalah **desain independen HoloJava**, bukan hasil ekstraksi — tidak ada klaim parity (`GAME_MECHANICS.md:15-17` menyatakan belum ada mekanik tervalidasi).

### 3.2 Senjata (lima senjata utama + pola)

Temuan langsung:
- `ContentCatalog.java:196-201`: `trident_thrust` (gura), `pistol_shot` (ame), `scythe_swing` (calli), `phoenix_sword` (kiara), `summon_tentacle` (ina), masing-masing dengan pola `PATTERN_MELEE_ARC` kecuali pistol (`PATTERN_PROJECTILE`), plus angka cooldown/damage/count/speed/range/area/knockback/weight/maxLevel yang semuanya konstanta desain HoloJava.
- Pola engine yang dikodekan: 8 konstanta `PATTERN_MELEE_ARC..PATTERN_WALL` (`13-24`) dan 3 tipe `WEAPON_MELEE/MULTISHOT/RANGED`.
- String referensi mengandung `weapon`, `Weapon`, `weaponType`, `baseDamage`, `weaponBonus`, `weaponSizeMultiplier`, `collabList`, `SuperCollab`, `unlockedWeapons/disabledWeapons` — bukti kosakata sistem senjata/kolab ada di build asli, tanpa nilai.
- Simbol: `symbol_classification.csv` ~67.718 baris; `gml_Script_*` = 27.501 simbol menurut `Roadmap.md:93`. Nama simbol adalah label; bukan source.

Inferensi / unknown:
- Tidak ada bukti formula damage, scaling level, cooldown aktual, atau resep kolab asli yang terbukti dari artefak yang dibaca. Resep kolab di `ContentCatalog.java:214-218` + CSV `data/wiki/collabs.csv` adalah dokumentasi/wiki + desain, bukan dekompilasi. Tidak ada perubahan yang didukung bukti untuk Tahap B–H dari sisi ini.

### 3.3 Skill, special, combat, item

Temuan langsung (HoloJava, desain independen):
- `RunWorld.java:154-156,319-320`: `skillAcquired`, `skillReadyAt`, `specialUsed`; trigger di `469-470` (`skillPressed && skillAcquired && now >= skillReadyAt → activateSkill`; `specialPressed && !specialUsed → activateSpecial`, sekali per run).
- `activateSkill (473-...)` / `activateSpecial (491-...)` memanggil `damageEnemiesInRadius(...)` dengan radius/damage literal (mis. 70/6, 45/4, 110/18, 130/24 — lihat `476-501`). Ini konstanta HoloJava, bukan formula asli.
- Combat: `damagePlayer (1073-)`, `damageEnemy (1092-)`, `damageBoss (1109-)`, `explode (984-)`, pola tembak `fireWeapons` dengan `cooldown = max(80, def.cooldownMs*100/max(50,haste))` (`804-805`), `damage = def.damage + max(0,level-1)`, skala `(100+atkPercent+bonus)/100`, krit `*2` (`811-814`, komentar menyebut aproksimasi).
- Item: `ContentCatalog.java:223-234` 10 `ITEMS` (face_mask, knightly_milk, energy_drink, ...) dengan efek `EFFECT_* (36-46)`. `DOCUMENTED_ITEM_COUNT=37` (`10`) vs 10 yang diimplementasi — selisih adalah cakupan desain, bukan temuan REA.
- String asli ada `skillDamage`, `isSkill`, `specialMeter`, `RemoveWeapon/RemoveSkill`, `fandomEXP/AddFandomEXP/manageEXP` — bukti kosakata, bukan angka.

Unknown: efek skill/special per karakter yang asli, tabel senjata per level, drop rate, dan interaksi kolab. Tidak dikarang.

### 3.4 Lifecycle run

Temuan langsung:
- `RunWorld.java:21-25`: `STATE_RUNNING/PAUSED/LEVEL_UP/GAME_OVER/VICTORY`; transisi di `328-329` (pause/resume), `396-402`/`1031-1035` (exp ≥ need → LEVEL_UP), `426/431/791` (GAME_OVER/VICTORY).
- `startRun`-setara di `212-235`: reset state, level=1, exp=0, `expNeed=requiredForLevel(1)`, `skillAcquired=false`, `specialUsed=false`.
- `GAME_MECHANICS.md:19-30`: semua mekanik (movement, attack, evolution, collision, damage, AI, XP, spawn, boss, save) berstatus **Unknown** — konsisten dengan tidak adanya klaim parity.
- Lifecycle MIDlet (`FullGameMIDlet.java`, `SaveStore.java` RMS) adalah implementasi HoloJava; tidak ada bukti REA yang menghubungkannya ke perilaku save asli (string asli hanya mengandung kata generik `save`; tidak dibuktikan format/kompatibilitasnya).

## 4. Keputusan untuk Tahap B–H

- **Tidak ada perubahan gameplay yang didukung bukti REA pada tugas ini**, karena REA tidak berjalan dan audit statis tidak memulihkan formula/logika internal apa pun.
- Pemetaan yang dipakai: "Tahap B–H" dibaca sebagai rangkaian bertahap setelah baseline (sejalan dengan Phase 2–8 di `Roadmap.md` dan Phase 1–8 di `docs/roadmap/GAMEPLAY_ROADMAP.md`). Tugas ini hanya menghasilkan laporan; **tidak memulai Tahap B–H, tidak menyentuh `src/`, `config/`, `data/`, `docs/`, atau laporan lama**. Verifikasi: tidak ada `edit`/`write` selain file laporan baru ini.
- Jika Tahap B–H merujuk pada definisi tahapan yang berbeda (dokumen lain/ekspektasi penugasan), maka bagian ini perlu koreksi penugasan sebelum implementasi — jangan menebak cakupannya.

## 5. Perintah/workflow yang benar-benar dijalankan, hasil, keterbatasan

Benar-benar dijalankan (semua read-only; tanpa `npm ci/install`, tanpa update Node, tanpa unduh provider):
1. `read` direktori/file: `tools/rea/`, `package.json`, `.nvmrc`, `README.md`, `src/cliCommandNames.ts`, `HoloJava/`, `reports/`, `HoloCure/`, `HoloCure_Unpacked/`, `Roadmap.md`, `GAMEPLAY_DESIGN.md`, `GAME_MECHANICS.md`, `ContentCatalog.java`, `GAMEPLAY_ROADMAP.md`, `PROJECT_STATUS.md`.
2. `node --version; npm --version` → `v20.19.2` / `9.2.0` (gagal syarat `^22.19||^24.11||>=26`).
3. `ls dist/`, `ls node_modules/` (REA) → keduanya tidak ada.
4. `node scripts/rea.mjs --help` → pesan runtime hilang (butuh `npm ci && npm run build:cached`); tidak dilanjutkan karena butuh izin.
5. `grep` GameMaker/data.win/GML di `docs/`+`src/` (REA) → tidak ada dukungan GameMaker.
6. `which ghidra hopper ida` → tidak ada; `ps/java/adb` ada.
7. `grep` strings/objek/sprite untuk 5 karakter, senjata, skill/special/combat/item/lifecycle + baca `RunWorld.java`/`AssetLibrary.java`.

Hasil: inventaris kemampuan REA + bukti kosakata/struktur (nama string/simbol/objek/aset) + penegasan bahwa angka HoloJava adalah desain independen.

Keterbatasan:
- Tanpa Node 22+/dependensi/build, REA tidak dapat dijalankan — sesuai larangan instalasi, tidak ada jalan aman untuk menjalankannya di sesi ini.
- Tanpa provider Hopper/Ghidra/IDA, analisis native dalam tidak tersedia kalaupun REA dibangun.
- REA tidak memiliki jalur GameMaker `data.win`→GML; ekspektasi "menganalisis mekanik" via REA untuk target YYC harus diturunkan: maksimal yang bisa dilakukan REA adalah inspeksi `HoloCure.exe` native + artefak statis yang sudah ada.
- Nama simbol/string bukan bukti implementasi; tidak ada formula yang dibuktikan.

Laporan disimpan di direktori baru: `reports/rea-directed-audit-20261016/00_REA_DIRECTED_AUDIT.md`. Laporan lama tidak ditimpa/dihapus.
