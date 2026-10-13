# HoloJava Gameplay Roadmap

Phased prioritization based on source code audit from baseline commit `b1239e546068de51c3265f78a492dc7343d92aea`.

## Phase 1: Baseline, Build Reproducibility, and Testing
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P1.1 | Inisialisasi Git lokal dan baseline commit | Commit `Initial HoloJava project baseline` di `b1239e5...` | -- | Tinggi | Repo `.git` ada di root, `git status` bersih, hash commit terverifikasi | `git status --short --branch`, periksa hash commit | verified |
| P1.2 | `.gitignore` tanpa pola `*.jar` lebar | `.gitignore` berisi `build/`, `dist/`, `__pycache__/`, dll tanpa `*.jar` | -- | Tinggi | `.gitignore` tidak mengabaikan `deps/java-me/*.jar` | Review `.gitignore` isi, `git status` setelah staging JAR | verified |
| P1.3 | Reproducibility build dengan dependency lokal | `deps/java-me/cldcapi11-2.0.4.jar` dan `midpapi20-2.0.4.jar` tercatat | `deps/java-me/` | Tinggi | JAR dependency ada di repo, build dapat di-ulang dari baseline | Verifikasi file ada di indeks git | verified |
| P1.4 | Identifikasi file sensitif/hilang dalam staging | Tidak ada file sensitif ditemukan di commit | Semua file di `git diff --cached --stat` | Menengah | Tidak ada file rahasia yang ter-expose di commit | Review setiap file sebelum `git add` | verified |
| P1.5 | Verifikasi struktur `.gitignore` vs aturan task | Aturan `.gitignore` sesuai dengan 9 aturan task | Task spec Section Tahap 2 | Menengah | Setiap aturan tercantum dan alasan diberikan | Cross-check dengan spec task | verified |

## Phase 2: Stabilitas Core Gameplay: State, Collision, Damage, Entity Lifecycle
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P2.1 | Alur state game: STATE_RUNNING ↔ PAUSED ↔ LEVEL_UP ↔ GAME_OVER ↔ VICTORY | `RunWorld.java:14-19` static final state constants | RunWorld state machine | Tinggi | Semua 5 state bisa di-transisi dan di-render dengan benar | Toggle state via tombol FIRE di layar GAME, verifikasi teks hasil | suspected |
| P2.2 | Pool entity dan alokasi memori (ENEMY_POOL=48, SHOT_POOL=128, MELEE_POOL=24, PICKUP_POOL=96) | `RunWorld.java:29-32` final int pool constants | RunWorld enemy/shot/melee/pupuk arrays | Tinggi | Pool dideklarasikan dengan ukuran tetap, tidak terjadi allocation dalam loop entitas | Inspeksi kode: pool dideklarasikan sebagai `private final X[]` di class RunWorld | suspected |
| P2.3 | Gerakan player (px=130, py=95, D-pad input) | `FullGameCanvas.java:105-110` keyPressed, `RunWorld.java:64-67` px/py init | Input states from GameCanvas | Tinggi | Posisi player bisa diubah melalui input D-pad, batas layar 320×240 | D-pad arah di FullGameCanvas, verifikasi px berubah | verified |
| P2.4 | Spawn, AI, collision, damage, entity cleanup | `RunWorld.java` enemy AI behavior, collision detection | Entity pools, weapon patterns | Menengah | Enemy spawn, AI movement, damage application, dan cleanup terverifikasi | Simulasi tanpa emulator: inspect loop entity di RunWorld.update() | suspected |
| P2.5 | Senjata dan pola serangan (melee, projectile, orbit, boomerang, AOE, homing, piercing, wall) | `ContentCatalog.java:13-23` weapon pattern constants, `BASE_WEAPONS` dan `WEAPONS` arrays | ContentCatalog weapon definitions | Menengah | Semua 8 pola senweapon terdaftar dan bisa diakses melalui `ContentCatalog.weapon(int)` | Pemerikkan `ContentCatalog.weaponIndex()` dan `weapon(String)` | verified |
| P2.6 | XP, level-up, upgrade, dan item drop | `RunWorld.java:72-75` experience/expNeed/coins, `ContentCatalog.java:37` item definitions | ContentCatalog ITEMS, RunWorld experience logic | Menengah | Sistem XP/level-up terverifikasi, upgrade selection di PREP screen berfungsi | Lihat `RunWorld.state == STATE_LEVEL_UP` dan `FullGameCanvas.drawLevelUp()` | suspected |

## Phase 3: Progression: XP, Level-Up, Upgrade, dan Reset antar-Run
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P3.1 | Simpan progres character (save/load via RMS) | `FullGameMIDlet.java:16-17` `new SaveStore()`, `SaveStore.selectedCharacter()` | SaveStore, MIDlet lifecycle | Menengah | Karakter bisa dipilih di layar CHARACTERS, data persist antar-run | Test: pilih character, keluar dan masuk kembali, verifikasi persistence | suspected |
| P3.2 | Level-up offer selection (stat offers, weapon offers, item offers) | `RunWorld.java:20-27` OFFER_SKILL..OFFER_HOLD, `offerUsed[64]`, `statOfferCounts[6]` | RunWorld offer system, FullGameCanvas.drawLevelUp() | Menengah | Player bisa memilih offer di layar level-up, offer tidak bisa di-select dua kali | Interaksi layar PREP → GAME → LEVEL_UP, verifikasi opsi offer | suspected |
| P3.3 | Reset run (hardcore mode, save state) | `FullGameCanvas.java:226` `save.setHardcore()`, `SaveStore` | SaveStore, hardcore flag | Low | Mode hardcore mengubah HP Endless, data reset antar-run sesuai setting | Verifikasi di SETTINGS menu: ON/OFF Hardcore, test run ulang | unverified |
| P3.4 | Progression balancing (exp need per level, coin drop rate) | `RunWorld.java:72-74` experience, expNeed, coins | Game design balance | Low | Nilai ekspansi level-up merasional untuk sesi play jangka pendek | Review angka di kode, perbandingan dengan desain gameplay | unverified |

## Phase 4: AI Musuh, Boss, dan Balancing
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P4.1 | 6 tipe AI musuh: CHASER, SHOOTER, CHARGER, BOMBER, HEALER | `ContentCatalog.java:26-31` final int AI constants | ContentCatalog ENEMIES array | Tinggi | Semuai 6 AI terdaftar dan bisa diakses melalui `ENEMIES[index]` | Pemerikkan `enemy.behavior` di setiap enemy def | verified |
| P4.2 | 10 tipe boss dengan HP/ATK/jadwal | `ContentCatalog.java:229-244` BOSSES array | ContentCatalog boss definitions | Menengah | 11 boss terdaftar dengan hp, atk, behavior, cooldown | Pemerikkan isi `ContentCatalog.BOSSES[i]` | verified |
| P4.3 | Mekanik damage dan lifesteal/slow/regen timer | `RunWorld.java:77-80` invulUntil, slowEnemiesUntil, lifestealUntil, regenTickAt | RunWorld state timers | Menengah | Timer decrement dan efektif damage calculation terverifikasi | Inspeksi logika timer di update loop | suspected |
| P4.4 | Enemy shot/mekle collision dengan player | `RunWorld.java` collision detection logic | Entity pools, player hitbox | Menengah | Damage diterima ketika enemy shot/melee menyentuh player | Review kode collision di RunWorld, tanpa emulator | suspected |
| P4.5 | Boss schedule dan enemy pool limiting | `RunWorld.java:43-46` enemy[]/shot[]/melee[]/pickup[] arrays | Entity pools, boss AI cooldown | Low | Enemy pool terbatas, boss spawn menurut cooldown | Review spawning logika di RunWorld, hitung enemy count per tick | unverified |

## Phase 5: Kelengkapan Karakter, Senjata, Item, dan Konten
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P5.1 | 47 karakter playable terdaftar di ContentCatalog.CHARACTERS | `ContentCatalog.java:168` `static final CharacterDef[] CHARACTERS` | ContentCatalog | Tinggi | Semua 47 karakter ada di array, `character(int index)` bekerja | Pemerikkan panjang array dan index batas | verified |
| P5.2 | 94 weapon terdaftar (BASE_WEAPONS + WikiCatalogData.buildWeapons) | `ContentCatalog.java:172-197` BASE_WEAPONS + WEAPONS | Weapon definitions, prerequisites | Tinggi | Semua 94 weapon terdaftar, prerequisite validation bekerja | Cross-check `weapon(String id)` dan `weaponIndex(String)` | verified |
| P5.3 | 37 item effect terdefinisi (healing, stat, utility, super) | `ContentCatalog.java:199-211` ITEMS array | ContentCatalog item definitions | Menengah | Semu 37 item memiliki kategori, effect, value | Pemerikkan `item(int index)` dan `item(String id)` | verified |
| P5.4 | Konten stage 1-5 + variants + time mode | `ContentCatalog.java:252-263` STAGES array | Stage definitions, enemy/boss references | Tinggi | 16 stage terdefinisi (11 utama + 5 hard variant + time mode) | Pemerikkan `stage(int index)` dan `stage(String id)` untuk setiap index | verified |
| P5.5 | Kolaborasi (COLLABS) dibaca dari data/wiki/collabs.csv | `ContentCatalog.java:267-291` static block CSV load | data/wiki/collabs.csv | Low | CSV dibaca di inisialisasi statis, fallback available | Verifikasi file ada dan terbaca tanpa error | suspected |

## Phase 6: UI/UX pada 320×240
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P6.1 | Menu navigation D-pad/FIRE di layar 320×240 | `FullGameCanvas.java:298-358` drawMainMenu, drawModes, drawCharacters, dll | Renderer Graphics, Font.getFont() | Tinggi | Menu bisa dinavigasi lengkap di 320×240 | Visual review: setiap layar menu ditampilkan dalam konteks resolusi | verified |
| P6.2 | Karakter portrait dan weapon name di karakter select | `FullGameCanvas.java:313-342` drawCharacters | AssetLibrary.characterPortrait(), ContentCatalog | Menengah | Portrait dan stats HP/ATK/SPD/CRIT terlihat | Verifikasi assets di-layout character select screen | suspected |
| P6.3 | Stage select menampilkan kondisi allowed/unlocked/locked | `FullGameCanvas.java:345-359` drawStages | ContentCatalog stage def, SaveStore stage progress | Menengah | Stage list menampilkan status (mode, locked, unlocked) | Test di layar STAGES, verifikasi warna dan prefix/suffix | suspected |
| P6.4 | Results screen menampilkan stats karakter/stad/tim | `FullGameCanvas.java:409-420` drawResults | RunWorld kills, level, coins, elapsedSeconds | Menengah | VICTORY/GAME OVER menampilkan nama karakter, stage, kills, level, time | Test alur replay dari GAME OVER ke RESULTS | unverified |
| P6.5 | Settings: Hardcore toggle, Audio disabled catatan | `FullGameCanvas.java:361-368` drawSettings | SaveStore hardcore flag | Low | Settings menampilkan status Hardcore ON/OFF, catatan Audio | Verifikasi di SETTINGS menu | unverified |

## Phase 7: Kompatibilitas Java ME dan Diagnosis Emulator
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P7.1 | CLDC 1.1 / MIDP 2.0 API compatibility | `deps/java-me/cldcapi11-2.0.4.jar`, `midpapi20-2.0.4.jar` | Library dependency Java ME | Tinggi | Semua import javax.microedition.* digunakan tanpa error compile | Inspeksi import di source file, pastikan tidak ada API melebihi MIDP 2.0 | verified |
| P7.2 | ClassFormatError dari class definitions terdahulu | Laporan diagnosis runtime sebelumnya | tools/verify_fullgame_build.py | Tinggi | Tidak ada ClassFormatError di build yang baru | Jalankan verify_fullgame_build.py terhadap build output | suspected |
| P7.3 | Asset loading validation (ContentCatalog.validate()) | `FullGameMIDlet.java:17-19` `ContentCatalog.validate()` | ContentCatalog validate() method | Tinggi | Validasi menampilkan error atau null | Start MIDlet, lihat validation error di layar | suspected |
| P7.4 | Asset gap coverage monitoring | `CONTENT_COVERAGE.md` dan `RECONSTRUCTION_SCOPE.md` | Dokumen coverage | Menengah | Dokumentasi kesenjangan konten terupdate | Review laporan coverage vs asset yang sebenarnya di repo | unverified |
| P7.5 | Diagnosis ulang build setelah perubahan | `tools/verify_fullgame_build.py` | Tools build verification | Menengah | Hasil verifikasi konsisten setelah ditambah file baru | Jalankan ulang tool verifikasi setelah staging file baru | unverified |

## Phase 8: Optimasi Performa dan Memori
| ID | Deskripsi & Dampak | Bukti / Lokasi Kode | Dependensi | Prioritas | Kriteria Penerimaan | Metode Pengujian yang Aman | Status |
|----|---------------------|----------------------|------------|-----------|----------------------|----------------------------|--------|
| P8.1 | Bounded iteration di game loop (max 3 updates per 40ms tick) | `FullGameCanvas.java:76-103` run() method, accumulator, updates < 3 | Game loop timing | Tinggi | Loop tidak melampaui 3 update per tick, timing konsisten | Waktu eksekusi di atas, verifikasi sleep(16) dan accumulator | verified |
| P8.2 | Pool entity menghindari garbage collection | `RunWorld.java:43-56` final array declarations | RunWorld entity pools | Tinggi | Tidak ada alokasi baru di dalam loop update | Inspeksi: semua entity array dideklarasikan `final` di konstruktor | verified |
| P8.3 | Waktu render tetap (viewport offset calculation) | `FullGameCanvas.java:264-285` render() method, viewport.update(), ox/oy calculation | Viewport, Graphics | Menengah | Render offset kalkulasi konsisten untuk 320×240 | Verifikasi ox = (w - MIN_W)/2, oy = (h - MIN_H)/2 | verified |
| P8.4 | Load time stage/boss data | `ContentCatalog.stage()` dan `ContentCatalog.boss()` lookup | ContentCatalog stage/boss def | Low | Waktu tautan data stage/boss konsisten | Waktu eksekusi rutin pemuatan, verifikasi tidak ada allocation selama runtime | unverified |
