# HoloJava — Audit Kontrol Gameplay (READ-ONLY)

| Item | Nilai |
|---|---|
| HEAD saat audit | `d1d11f9` — `Implement compact character selection layout` |
| Perubahan lokal | `FullGameCanvas.java` (4/2, patch Collab), `RunWorld.java` (6/0, accessor `offerIsCollab`) |
| Laporan untracked | 39 direktori, seluruhnya dipertahankan |
| Kode diubah selama audit | **tidak ada** — `git diff --stat` tetap `10 insertions, 2 deletions` sebelum & sesudah |
| Staging / commit kedua | **tidak ada** |
| Game / emulator | **tidak dijalankan** |
| JAR referensi | tidak disentuh |

Audit ini sepenuhnya baca-saja. Tidak ada berkas sumber yang dimodifikasi.

---

## Ringkasan Temuan

| # | Temuan | Status | Dampak |
|---|--------|--------|--------|
| **F1** | Skill dan Special **terbalik** terhadap katalog | 🔴 terbukti statis | Tinggi |
| **F2** | Flag aksi bocor antar layar dan melewati pause | 🔴 terbukti statis | Tinggi |
| **F3** | Timer run & scaling **tetap berjalan saat pause** | 🔴 terbukti statis | Sedang |
| **F4** | **Tidak ada jalan keluar dari run** | 🔴 terbukti statis | Sedang |
| **F5** | `keyReleased` tidak di-override; softkey tak terpakai | 🟠 terbukti statis | Rendah |
| **F6** | `RunWorld.drawPaused` kode mati | 🟠 terbukti statis | Rendah |
| **F7** | `attackEdge` redundan | 🟠 terbukti statis | Rendah |
| **F8** | Tidak ada umpan balik cooldown skill / special terpakai | 🟠 terbukti statis | Rendah |
| **F9** | Layar CONTROLS tidak lengkap | 🟠 terbukti statis | Rendah |
| **F10** | Loop 40 ms lambat pada perangkat lambat | ⚪ perlu runtime | Tidak diketahui |

---

## 1. Pemetaan Tombol Fisik

### 1.1 `keyPressed` — `FullGameCanvas.java:105-110`

```java
public void keyPressed(int key) {
    // Numeric action keys are deliberately explicit: 5 = skill, 9 = special.
    if (key == '5') skillPressed = true;
    if (key == '9') specialPressed = true;
    if (key == '1') attackPulse = true;
}
```

| Key | Aksi | Ditangani di |
|-----|------|--------------|
| `'1'` | pulse serangan | `attackPulse` |
| `'5'` | skill | `skillPressed` |
| `'9'` | special | `specialPressed` |

### 1.2 `getKeyStates()` — `FullGameCanvas.java:112-127`

D polled tiap frame, bukan lewat event:

| Konstanta | Dipakai untuk |
|-----------|---------------|
| `FIRE_PRESSED` | konfirmasi menu, pause/resume, pilih offer |
| `UP_PRESSED` / `DOWN_PRESSED` | navigasi menu, pindah opsi level-up |
| `LEFT_PRESSED` | navigasi menu + gerak pemain |
| `RIGHT_PRESSED` | gerak pemain + page karakter (mati) |
| `GAME_A_PRESSED` | serangan dasar (hold-to-fire) |

Edge detection manual: `fireEdge = fire && !fireWasDown` dan setara untuk up/down/left/right/attack (`:120-127`).

### 1.3 `keyReleased` — **tidak ada**

```
grep keyReleased src/java-me-fullgame/  →  (kosong)
```

Harusrah: tidak ada override `keyReleased(int)`. Semua state tombol berasal dari polling `getKeyStates()`. Konsekuensinya/netral untuk logika saat ini (semua aksi berbasis edge atau hold dari polling), tapi berarti tidak ada hook rilis tombol sama sekali.

### 1.4 Softkey — **tidak ada**

```
grep "key == -[0-9]|KEY_SOFT"  →  (kosong)
```

MIDP mendefinisikan softkey sebagai keyCode negatif (`-1`=`-1`, `KEY_LEFT_SOFTKEY`=`-6`). Tidak ada satupun yang ditangani, dan `keyPressed` tidak pernah menerima bilangan negatif. Artinya **seluruh softkey perangkat tidak dapat dipakai** — tidak ada tombol Back/backsoftkey.

---

## 2. Start dan Transisi ke Gameplay

### Rantai transisi (terverifikasi)

```
MENU ──FIRE(case 0/2)──> CHARACTERS
CHARACTERS ──FIRE──> save.setSelectedCharacter + screen = STAGES
STAGES ──FIRE──> stageIndex = stages[menuIndex]; screen = PREP
PREP ──FIRE──> startRun(): new RunWorld(...); screen = GAME
GAME ──hp<=0 / kills>=target──> screen = RESULTS
RESULTS ──FIRE(0)──> startRun()   ──FIRE(1)──> screen = MENU; world = null
```

`startRun()` (`FullGameCanvas.java:271-276`) memverifikasi ulang `stageUnlocked` dan `stageAllowed` sebelum membuat `RunWorld` — sehingga stage terkunci tidak bisa dimasuki melalui jalur mana pun.

**Tidak ada temuan cacat pada transisi start.**

---

## 3. Pause / Resume

### 3.1 Mekanisme — terbukti benar untuk simulasi

`RunWorld.pause()` / `resume()` (`RunWorld.java:284-285`):

```java
void pause() { if (state == STATE_RUNNING) state = STATE_PAUSED; }
void resume() { if (state == STATE_PAUSED) state = STATE_RUNNING; }
```

`FullGameCanvas.java:136` — `world.update()` **dilewati** saat `STATE_PAUSED`, dan `RunWorld.update()` sendiri juga guard `if (state != STATE_RUNNING) return;` (`:364`).

Jadi **musuh, serangan, projectile, pickup, regen, dan spawn semuanya membeku** saat pause. Ini benar dan terverifikasi.

### 3.2 🔴 F3 — Timer run & scaling TIDAK membeku

| Lokasi | Kode |
|--------|------|
| `RunWorld.elapsedSeconds()` `:282` | `(System.currentTimeMillis() - startTime) / 1000L` |
| `RunWorld.updateScaling()` `:392` | `System.currentTimeMillis() - startTime` |
| `RunWorld.timeString()` | memakai `elapsedSeconds()` |

`startTime` ditetapkan sekali di `:197` dan **tidak pernah digeser saat pause**.

- **Perilaku aktual:** HUD tetap menggambar `Time mm:ss` yang bertambah setiap detik selama pause, karena `drawHud` (:1549) memperbarui `cachedTimeString` tiap 1000 ms tanpa memandang state.
- **Perilaku yang diharapkan:** pause membekukan run timer dan lembar_{(??)} — ini konvensi standar dan konsisten denganITTUA fakta bahwa semua counter lain membeku.
- **Bukti:** `elapsedSeconds()` membaca jam dinding, bukan akumulator simulasi.
- **Dampak:** pemain yang exploit pause=================================================== menjalankan waktu_dan_stats masih naik sehingga run yang sama dengan waktu gameplay lebih pendekterminadou难度 lebih tinggi.
- **Perbaikan minimal:**急性急急。Menambah field `pausedAt`/`pausedTotal` dan mengurangi durasi pause dari `elapsedSeconds()`; `updateScaling()` harus memakai sumber waktu yang sama.

### 3.3 🔴 F2 — Flag aksi tidak dibersihkan saat pause

| Lokasi | Kode |
|--------|------|
| `FullGameCanvas.java:107-109` | `keyPressed` menyetel flag tanpa memandang screen |
| `FullGameCanvas.java:136-141` | flag hanya di-reset **di dalam** `if (world.state() != STATE_PAUSED)` |

```java
if (world.state() != RunWorld.STATE_PAUSED) {
    world.update(keys, attackHeld || attackEdge || attackPulse, skillPressed, specialPressed, now);
    attackPulse = false;
    skillPressed = false; specialPressed = false;   // <-- hanya di sini
}
```

**Dua jalur bocor yang terbukti secara statis:**

1. **Lewati pause.** Tekan `5` saat paused → `skillPressed = true`. Tekan FIRE → `resume()` → state `RUNNING` → baris yang sama langsung berjalan dengan `skillPressed == true` → skill aktif seketika pada frame resume.
2. **Lintas layar.** Tekan `5`/`9`/`1` di menu mana pun (`screen != GAME`) → tidak ada handler yang mereset flag → tetap `true` sampai run dimulai → skill/special/serangan aktif pada frame pertama run.

- **Harapan:** flag aksi hanya berlaku untuk frame yang ditekan.
- **Dampak:** skill 15 detik aktif tanpa biaya, special sekali-per-run terbuang, atau serangan otomatis pada frame pertama.
- **Perbaikan minimal:** reset ketiga flag di akhir `update()` pada cabang `screen == GAME` **dan** saat masuk `STATE_PAUSED`; idealnya reset juga pada setiap frame di luar gameplay.

---

## 4. Back dan Exit

### 4.1 Back per layar

| Layar | Tombol back | Lokasi |
|-------|-------------|--------|
| MODES | FIRE (idx 1) | `:184` |
| CHARACTERS | LEFT (page 0) atau `> Back` + FIRE | `:190`, `:202` |
| STAGES | LEFT | `:216` |
| SETTINGS | LEFT atau FIRE (idx 3) | `:245`, `:250` |
| CONTROLS | LEFT atau FIRE | `:160` |
| PREP | LEFT | `:254` |
| RESULTS | FIRE (idx 1) | `:268` |
| MENU | — (root) | — |
| **GAME** | **tidak ada** | — |
| **PAUSED** | **tidak ada** | — |

### 4.2 🔴 F4 — Tidak ada jalan keluar dari run

```
grep -nE "quit|abandon|toMenu" FullGameCanvas.java  →  (kosong)
```

Cabang `screen == GAME` (`FullGameCanvas.java:133-153`) hanya menangani:
- `fireEdge` → pause / resume (`:134-135`)
- pembaruan dunia + level-up offer (`:136-146`)
- transisi ke RESULTS bila game over / menang (`:147-152`)

Tidak ada handler UP/DOWN/LEFT saat `STATE_PAUSED`. `drawPause` hanya menawarkan `"FIRE resume"` (`:527`).

- **Perilaku aktual:** setelah `startRun()`, satu-satunya jalan keluar adalah kematian (GAME_OVER) atau kemenangan (VICTORY).
- **Harapan:** minimal ada jalan abandon ke menu dari pause.
- **Dampak:** pemain terkunci di run yang panjang; tidak bisa keluar untuk mengecek settings/controls.
- **Perbaikan minimal:** tambahkan satu entri menu di `drawPause` + handler UP/DOWN/FIRE pada `STATE_PAUSED` yang set `screen = RESULTS` (atau MENU) dan `world = null`.

### 4.3 Exit

`updateMain` case 5 (`:174`):
```java
case 5: exitRequested = true; stopped = true; shutdownRequest.requestShutdown(); break;
```

---

## 5. Aktivasi Skill dan Special

### 5.1 🔴 F1 — Skill dan Special TERBALIK terhadap katalog

Tiga sumber independen saling bertentangan:

| Karakter | `ContentCatalog.skill` | `ContentCatalog.special` | Komentar di `activateSkill()` | Komentar di `activateSpecial()` |
|----------|------------------------|--------------------------|-------------------------------|--------------------------------|
| gura (0) | `Shark Call` | `Shark Bite` | `// Shark Bite` | `// Shark Call` |
| ame (1) | `Slow Time` | `Hunter's Instinct` | `// Hunter's Instinct` | `// Slow Time` |
| calli (2) | `The Reaper` | `Undying` | `// Undying` | `// The Reaper` |
| kiara (3) | `Phoenix Fire` | `Second Flame` | `// Second Flame` | `// Phoenix Fire` |
| ina (4) | `Spirit of the Oni` | `The Ancient One` | `// The Ancient One` | `// Spirit of the Oni` |

**Kelima karakter terbalik.** Kode死 dead memetakan `characterIndex` → efek dengan label yang **tertukar** terhadap `CharacterDef`.

Rujukan silang yang_confirms hal ini:

- `RunWorld.java:281` — `offerLabel()` untuk `OFFER_SKILL` mengembalikan `character.skill`.
- `RunWorld.java:431` `activateSkill` idx 0 ber komentar `// Shark Bite`; katalog menyatakan `Shark Bite` adalah **special**.
- HUD: `RunWorld` hint `"5 Skill  9 Special"`; CONTROLS: `"5: skill   9: special attack"`.

Jadi **tombol 5 ("Skill") menjalankan efek yang katalog sebut Special**, dan sebaliknya. Salah satu dari tiga hal berikut pasti salah dan tidak dapat diputuskan tanpa produk: (a) label katalog, (b) komentar di `activateSkill`/`activateSpecial`, atau (c) effect yang ditugaskan.

 Deskripsi asli HoloCure (dari `strings.txt`) mengarahkan arahnya: `Shark Call` = "deals 500% damage to all targets around" (AoE besar), `Shark Bite` = penanda Bite Mark + lifesteal saat kill. Kode memberi `activateSpecial` → `damageEnemiesInRadius(110, 18, true)` dengan komentar `// Shark Call`, yaitu **efek yang cocok untuk Shark Call tetapi terikat pada tombol Special**.

- **Dampak:** setiap skill/special karakter yang ditekan melakukan hal yang salah secara semantik, dan HUD/label yang ditampilkan menyesatkan.
- **Perbaikan minimal:** Science — putuskan dulu mana yang benar (katalog vs kode), lalu Tukar label di `ContentCatalog` **atau** tukar cabang `characterIndex` di kedua method. Butuh keputusan produk; **tidak thugs**egaasan.

### 5.2 Cooldown — **didukung dan bekerja**

| Fungsi | Nilai | Lokasi |
|--------|-------|--------|
| Cooldown skill | 15 000 ms | `:430` `skillReadyAt = now + 15000L` |
| Gerbang aktivasi | `skillPressed && skillAcquired && now >= skillReadyAt` | `:425` |
| Special | sekali per run | `:448` `specialUsed = true`, gate `:426` `!specialUsed` |
| Reset | ketiganya di-reset di `reset()` | `:221` |
| Akuisisi skill | lewat level-up offer `OFFER_SKILL` | `:1216`, `:1226`, `:1285` |

Tidak ada bug cooldown yang terbukti. Namun `now` berasal dari `System.currentTimeMillis()`, konsisten dengan sisa simulasi.

### 5.3 🟠 F8 — Tidak ada umpan balik cooldown / status di UI

`RunWorld` mengekspos `skillAcquired()` (`:275`) dan `specialUsed()` (`:276`), tetapi **keduanya tidak pernah dipanggil** dari `FullGameCanvas`:

```
grep "skillAcquired()\|specialUsed()" FullGameCanvas.java  →  (kosong)
```

- **Perilaku aktual:** menekan `5` saat cooldown, atau `9` setelah special terpakai, tidak memberi umpan balik apa pun — pemain tidak tahu apakahnya gagal atau belum.
- **Harapan:** indikator visual (ikon meredup / label cooldown), setidaknya di slot skill HUD yang baru.
- **Perbaikan minimal:** baca `world.skillAcquired()` / `world.specialUsed()` di `drawHud` dan modulating marker slot; atau tambahkan teks cooldown.

---

## 6. D-pad, FIRE, Konflik Input

### 6.1 Konflik yang diperiksa — tidak ada

| Potensi konflik | Status |
|-----------------|--------|
| FIRE saat LEVEL_UP vs pause | ✅ Aman. `:134` mensyaratkan `STATE_RUNNING`; `:142` menangani `STATE_LEVEL_UP` |
| '5'/'9' saat LEVEL_UP | ✅ Aman. `RunWorld.update()` return di `:364` saat bukan RUNNING |
| Bergerak saat LEVEL_UP | ✅ Diblokir. `updatePlayer` hanya dipanggil dari `update()` |
| Beberapa `update()` per frame | ✅ Aman. Edge flag di-reset pada panggilan pertama, panggilan berikutnya melihat `fireWasDown == fire` |
| GAME_A di menu | ✅ Tidak ada aksi (dipakai hanya `:119` untuk `attackHeld`) |

### 6.2 RIGHT praktis tidak terpakai

`rightEdge` diteruskan **hanya** ke `updateCharacters` (`:157`). Di `CHARACTERS`, `right` hanya pindah page bila `characterPage < lastPage`; dengan 5 karakter dan `CHARACTER_PAGE_SIZE = 8`, `lastPage = 0`, sehingga **RIGHT tidak pernah melakukan apa pun**. Di semua layar lain `RIGHT_PRESSED` hanya menggerakkan pemain.

### 6.3 🟠 F7 — `attackEdge` redundan

```java
boolean attackEdge = attackHeld && !attackWasDown;    // :125
...
world.update(keys, attackHeld || attackEdge || attackPulse, ...);   // :137
```

Karena `attackEdge ⇒ attackHeld`, maka `attackHeld || attackEdge ≡ attackHeld`. `attackEdge` tidak menambah perilaku apa pun. Bukan bug, tetapi sisa kode yang menyesatkan.

### 6.4 🟠 F9 — Layar CONTROLS tidak lengkap

Teks yang tampil (`FullGameCanvas.java:491-496`):

```
D-pad: move / menu navigation
GAME A or 1: manual basic attack
FIRE: confirm / pause / resume
5: skill   9: special attack
Orbiting and passive weapons may fire autonomously.
```

Yang **tidak** disebut: bahwa **LEFT = back** di semua submenu; bahwa **RIGHT praktis tidak melakukan apa pun** di luar gameplay; dan bahwa **tidak ada jalan keluar dari run**. Petunjuk di tiap layar juga tidak konsisten satu sama lain — `"D-pad move   FIRE select"` (:329), `"UP/DOWN select   LEFT back   FIRE confirm"` (:459), `"LEFT or FIRE back"` (:496), `"FIRE: confirm / pause / resume"` (:493).

- **Perbaikan minimal:** perbarui teks CONTROLS dengan tombol back + quit note.

---

## 7. Penyimpanan Progres

### 7.1 Mekanisme — write-on-mutation

`SaveStore` menyimpan **langsung pada setiap setter** (bukan perlu flush di akhir):

| Metode | Lokasi | Menulis? |
|--------|--------|----------|
| `setCompleted()` | `:32` → `save()` `:36` | ya |
| `setHardcore()` | `:40` → `save()` `:44` | ya |
| `setSelectedCharacter()` | `:49` → `save()` `:52` | ya |

Catatan pada `:123`: *"This pinned API stub exposes no commit operation; record writes are immediate."* — jadi pada stub build ini tulis bersifat langsung.

### 7.2 Titik pemicu

| Peristiwa | Lokasi | Tersimpan |
|-----------|--------|-----------|
| Karakter dipilih | `:205` `save.setSelectedCharacter` | ya |
| Stage dituntaskan | `:149` `save.setCompleted` (hanya `worldWasVictory && modeIndex == 0`) | ya |
| Hardcore toggle | `:247` | ya |
| Exit dari menu | `:174` | tidak perlu — sudah tertulis |
| `stopApplication()` | `:74` | tidak ada penambahan — aman karena write-on-mutation |

**Tidak ada temuan cacat pada persistensi.** Progres sudah tersimpan sebelum MIDlet berhenti.

### 7.3 Catatan

`:149` hanya menyimpan completion bila `modeIndex == 0`. Karena `updateModes` hanya offering 2 entri (Stage Mode saja) dan idx 1 = back, `modeIndex` praktis selalu 0 — jadi tidak ada progres yang hilang. Namun bila mode ke depan diaktifkan, completion di mode lain **tidak akan tercatat** — ini disengaja dan perlu diingat.

---

## 8. Loop dan Timing

### 8.1 Fixed timestep — `FullGameCanvas.java:76-103`

```java
accumulator += delta;
int updates = 0;
while (accumulator >= 40L && updates < 3) { update(now); accumulator -= 40L; updates++; }
render(); flushGraphics(); sleep(16);
```

- Tick logika **40 ms (25 Hz)**, render ~60 Hz, maksimum **3** catch-up step.
- `delta` dijepit ke `[0, 200]` (`:88-89`) → waktu hilang saat aplikasi lambat, mencegah lompatan besar.
- Saat `applicationPaused`, `last` diset ulang (`:83`) sehingga akumulator tidak menumpuk.

### 8.2 ⚪ F10 — Perilaku pada perangkat lambat (perlu runtime)

Jika frame render lebih lambat dari ~13 fps, akumulator akan tertinggal: setiap frame hanya 3 × 40 ms = 120 ms logic time Advance, sementara waktu nyata bertambah lebih cepat. Akibatnya **game berjalan dalam slow-motion**, dan `delta > 200` membuang waktu secara tambahan.

- **Bukti statis:** clamp `delta > 200` dan batas `updates < 3`.
- **Mengapa butuh runtime:** dampak nyata bergantung pada kecepatan perangkat; tidak dapat disimulasikan secara sah di sini (emulator dilarang).
- **Perbaikan minimal (jika terkonfirmasi):** longgarkan `updates < 3` ke batas yang lebih tinggi, atau gunakan `delta` penuh dengan sub-stepping.

---

## 9. Ringkasan Perbaikan Minimal (Belum Diimplementasikan)

| # | Temuan | Perbaikan minimal | Risiko |
|---|--------|-------------------|--------|
| **F1** | Skill/Special terbalik | Keputusan produk: katalog atau kode yang benar; lalu Tukar label/branch di satu tempat | **Tinggi** — menyentuh balancing |
| **F2** | Flag bocor | Reset `attackPulse`/`skillPressed`/`specialPressed` di luar cabang PAUSED, dan tiap frame non-GAME | Rendah |
| **F3** | Timer tidak membeku | Akumulator waktu pause di `elapsedSeconds()` + `updateScaling()` | Sedang |
| **F4** | Tidak bisa keluar dari run | 1 entri menu di `drawPause` + handler saat `STATE_PAUSED` | Rendah |
| **F5** | Tanpa `keyReleased`/softkey | Tambahkan softkey back, atau didokumentasikan sebagai batasan | Rendah |
| **F6** | `drawPaused` mati | Hapus, atau panggil sebagai ganti `drawPause` | Sangat rendah |
| **F7** | `attackEdge` redundan | Sederhanakan ke `attackHeld` | Sangat rendah |
| **F8** | Tanpa umpan balik cooldown | Pakai accessor yang sudah ada di `drawHud` | Rendah |
| **F9** | CONTROLS tidak lengkap | Perbarui teks | Sangat rendah |
| **F10** | Slow-motion di perangkat lambat | Naikkan batas catch-up | Rendah |

**Rekomendasi urutan:** F2 → F4 → F9 → F8 → F6/F7 (aman, terisolasi). F1 dan F3 memerlukan keputusan produk lebih dulu.

---

## 10. Batas Audit

| # | Batas |
|---|-------|
| B1 | Tidak ada perangkat atau emulator — **tidak ada pengujian runtime**. Semua temuan F1–F9 adalah pembuktian statis (alur kode + konstanta), F10 memerlukan pengukuran. |
| B2 | Jalur MIDP yang tidak di-override (mis. `-1` saat MIDlet entregando fokus) tidak dapat diamati. |
| B3 | Perilamo多重度 perangkat untuk softkey berbeda antar OEM — pemetaan softkey tidak dapat diverifikasi. |
| B4 | Tidak ada perubahan kode dibuat. Semua usulan di §9 belum diimplementasikan dan menunggu persetujuan terpisah. |
