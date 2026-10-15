# HoloJava Comprehensive Audit — Architecture and State

## 1. Sistem Arsitektur Umum

### 1.1 Layers / Layers Arsitektur

Arsitektur HoloJava terstruktur dalam beberapa lapisan yang terbagi jelas:

| Lapisan | Tanggung Jawab | File Utama |
|---------|---------------|------------|
| **A. Entry Point & Lifecycle** | MIDlet lifecycle, inisialisasi | `FullGameMIDlet.java` |
| **B. UI & Rendering** | Canvas, viewport, render loop, input | `FullGameCanvas.java`, `Viewport.java` |
| **C. Game Logic & State Machine** | State transisi, game loop, update | `RunWorld.java` |
| **D. Data Catalog** | Semua definisi data statis | `ContentCatalog.java` |
| **E. Aset & Resource** | Pemuat gambar, animasi, konfig | `AssetLibrary.java` |
| **F. Penyimpanan** | RMS data, progres karakter | `SaveStore.java` |
| **G. Utilitas** | Kalkulasi matematis | `MathUtil.java` |

### 1.2 Alur Data

1. **Inisialisasi**: `FullGameMIDlet.startApp()` → `FullGameCanvas` → `RunWorld` → `ContentCatalog` → `AssetLibrary`
2. **Loop Utama**: `RunWorld.run()` → `update()` → `render()` → `sleep(16)`
3. **Input → Gameplay**: `FullGameCanvas.keyPressed()` → `RunWorld.update(keys, ...)` → pergerakan/spawn/collision/damage
4. **Combat**: `RunWorld` → damage calculation → entity pool modification → XP/item drop
5. **Progression**: level-up trigger → `generateOffers()` → player memilih → `applyOffer()` → state update
6. **Save/Load**: `SaveStore` RMS read/write saat transisi state tertentu
7. **Viewport**: `Viewport.update()` → `FullGameCanvas.render()` → offset 320×240 → `Graphics.drawString/drawImage`

### 1.2 State Machine

State utama yang ditawalkannya:

| State | Nilai | Transisi Dari | Transisi Ke |
|-------|-------|--------------|-------------|
| STATE_RUNNING | 0 | startApp/RESULTS→GAME | PAUSED, LEVEL_UP, GAME_OVER, VICTORY |
| STATE_PAUSED | 1 | STATE_RUNNING (FIRE pause) | STATE_RUNNING (FIRE resume) |
| STATE_LEVEL_UP | 2 | GAME (experience sufficient) | STATE_RUNNING (seleksi selesai), atau stay |
| STATE_GAME_OVER | 3 | STATE_RUNNING (player HP ≤ 0) | RESULTS |
| STATE_VICTORY | 4 | STATE_RUNNING (boss defeated) | RESULTS |

#### Sub-state dalam Level-Up:
- Player melihat 5-6 opsi offer
- `moveOffer(delta)` menggeser `selectedOffer`
- `applyOffer(index)` menerapkan pilihan
- Kembali ke `STATE_RUNNING` setelah `FIRE` di layar GAME

### 1.3 Dependensi Antar-Subsystem

| Dependensi | Diterima Oleh | Memberikan |
|------------|--------------|-----------|
| `ContentCatalog` | Semua subsystem gameplay | Definisi karakter, weapon, item, enemy, boss, stage, mode |
| `AssetLibrary` | `FullGameCanvas.render()` | Pemuat gambar/sprite |
| `RunWorld` | `FullGameCanvas.update()` | Simulasi game loop, entity management |
| `SaveStore` | `FullGameMIDlet.startApp()` | Penyimpanan progres, status hardcore |
| `Viewport` | `FullGameCanvas.render()` | Manajemen koordinat 320×240 |
| `Input (FullGameCanvas)` | `RunWorld.update()` | Status kunci D-pad/FIRE/5/9 |

## 2. Variabel State Global (RunWorld)

Variabel global di `RunWorld` yang menyimpan kondisi selama satu run:

```java
// Player state
private int px = 130, py = 95;        // Posisi player
private int facingX = 1, facingY = 0; // Arah menghadap
private int playerHp, playerMaxHp;
private int playerLevel = 1;
private int experience = 0;
private int expNeed;                    // Dihitung oleh requiredForLevel()
private int coins;
private int kills;
private long invulUntil;              // Invulnerability timer

// Entity pools
private static final int ENEMY_POOL = 48;
private static final int SHOT_POOL = 128;
private static final int MELEE_POOL = 24;
private static final int PICKUP_POOL = 96;
private final Enemy[] enemies = new Enemy[ENEMY_POOL];
private final Shot[] shots = new Shot[SHOT_POOL];
private final Melee[] melee = new Melee[MELEE_POOL];
private final Pickup[] pickups = new Pickup[PICKUP_POOL];

// Sistem offer level-up
private int offerCount;        // 5 atau 6
private int selectedOffer;     // Index offer yang dipilih
private int heldOffer = -1;    // Offer yang ditahan dari run sebelumnya
private int rerolls;           // Sisa rerolls
private int holds;             // Sisa holds
private boolean offerUsed[] = new boolean[64]; // Tracking penggunaan unik

// Statistik
private int[] statOfferCounts = new int[6]; // Counter setiap tipe stat offer
private WeaponDef[] weaponSlots = new int[6];
private int[] weaponLevels = new int[6];
private int[] itemSlots = new int[6];
private int[] itemLevels = new int[6];

// Event tracking
private long slowEnemiesUntil;
private long lifestealUntil;
private long regenTickAt;
private long shieldReadyAt;
private int shield;
private int weaponCount;
private int itemCount;
private boolean skillAcquired;
private long skillReadyAt;
private boolean specialUsed;
```

## 3. Alur Update Loop

### 2.1 Game Loop (`RunWorld.run()`)

```java
// Setiap tick 40ms:
1. accumulator += delta
2. updates = 0
3. while (accumulator >= 40L && updates < 3) {
       update(now);
       accumulator -= 40L;
       updates++;
   }
4. render();
5. flushGraphics();
6. sleep(16);
```

### 2.2 Method `update(long now)`

Memproses input, update game state:

- Baca `getKeyStates()` → bitmask tombol
- Edge detection (falling edge: tombol baru ditekan)
- Jika `validationError`, early return
- `viewport.update(getWidth(), getHeight())`
- Jika `world != null`: `world.update(keys, attackHeld, skill, special, now)`
- `attackPulse = false; skillPressed = false; specialPressed = false`
- Jika `STATE_LEVEL_UP`: navigasi offer UI
- Jika `STATE_GAME_OVER`/`STATE_VICTORY`: manage results state

### 2.3 Method `render()`

Meng-render frame:

- `getGraphics()`, `fillRect(0, 0, width, height)` (background hitam)
- `viewport.update(width, height)`
- `ox = (width - MIN_W) / 2`, `oy = (height - MIN_H) / 2` (MIN_W=320, MIN_H=240)
- Jika `validationError`: tampilkan error text
- Jika `screen == GAME` & `world != null`:
  - `world.draw(g, offsetX, offsetY, System.currentTimeMillis())`
  - Draw Level Up jika STATE_LEVEL_UP
  - Draw Pause jika STATE_PAUSED
  - Draw Game Over / Victory jika STATE_GAME_OVER / STATE_VICTORY
- Jika `screen == RESULTS`: drawResults
- Jika lain: drawMenuScreen

## 3. Dependensi Siklus dan Tumpang Tindih

Saya memeriksa adanya dependensi siklus (circular dependency):

- `FullGameMIDlet` → `FullGameCanvas` → `RunWorld` → `ContentCatalog` → (kembali ke) `FullGameMIDlet`
- **Analisis**: Ini adalah dependensi yang seharusnya (layered architecture), bukan circular dependency yang bermasalah. Urutan inisialisasi:
  `MIDlet → Canvas → World → Katalog → Aset`
  Semuanya terindeks dengan benar dan tidak ada loop tak terhingga pada inisialisasi.

## 3. State Stale dan Reachable States

### 3.1 State yang Dapat Direach (Reachable)

Dari analisis jalur kode, state-state berikut dapat ter achievement:

- `STATE_RUNNING` → utama selama main
- `STATE_PAUSED` → saat pemain menekan FIRE di GAME
- `STATE_LEVEL_UP` → setelah experience cukup
- `STATE_GAME_OVER` → saat player HP habis
- `STATE_VICTORY` → saat boss dikalahkan
- `STATE_LEVEL_UP` (dalam menu) → memilih offer, kembali ke GAME

### 3.2 State yang Tidak Dapat Direach (Unreachable)

- State apa pun yang tidak tertrigger melalui alur `FullGameCanvas` switching
- Contoh: Transisi langsung dari `STATE_RUNNING` ke state lain tanpa melewati handler `fireEdge` di `FullGameCanvas.update()`

### 3.2 State Stale setelah Transisi

Setelah transisi menu → game atau game → menu, beberapa variabel state tidak direset otomatis:

- `world` bisa `null` setelah `screen = RESULTS; world = null;` (FullGameCanvas:241)
- `playerLevel` tetap bertahan sampai di-reset run baru
- `offerUsed[]` mempertahankan tracking selama run aktif

## 3. Konstanta yang Sulit "Diperbesar" (Hardcoded Constants)

Berikut konstanta yang "terhidden" di kode dan harus diedokumentasikan:

| Konstanta | Nilai | Lokasi | Catatan |
|-----------|-------|--------|---------|
| MIN_W | 320 | `Viewport.java:??` | Logical lebar |
| MIN_H | 240 | `Viewport.java:??` | Logical tinggi |
| TICK_MS | 40 | `RunWorld.run()` | Durasi tick game loop |
| MAX_UPDATES | 3 | `RunWorld.run()` | Max update per tick |
| OFFER_COUNT_DEFAULT | 5 | `RunWorld.generateOffers()` | Tanpa rerolls |
| OFFER_COUNT_WITH_REROLL | 6 | `RunWorld.generateOffers()` | Dengan rerolls |
| OFFER_SKILL…HOLD | 0…7 | `RunWorld.java:20-27` | Enum offer |
| ENEMY_POOL…PICKUP_POOL | 48…96 | `RunWorld.java:29-32` | Ukuran pool statis |
| REQUIRED_FOR_LVL_1 | 79 | `RunWorld.requiredForLevel()` | Catatan: "79 EXP for level 1 -> 2" |

## 3. Variabel Global yang Tidak Digunakan (Potensial Dead Code)

Saya mendeteksi beberapa variabel global yang mungkin tidak terpakai sepenuhnya atau hanya digunakan dalam kondisi tertentu:

- `nextSpawnAt`, `nextWaveAt` — Tracking spawn wave, butuh verifikasi apakah digunakan di `RunWorld.update()`
- `ringEventDone`, `hordeEventDone` — Tracking event special, butuh verifikasi
- `skillAcquired`, `skillReadyAt` — Butuh verifikasi di seluruh codebase
- `specialUsed` — Butuh verifikasi
- `weaponCount`, `itemCount` — Counter, butuh verifikasi apakah digunakan untuk iterasi atau hanya inisialisasi

*Catatan*: Variabel ini mungkin digunakan dalam cabang conditional yang belum saya temui dalam scan awal. Semua akan diverifikasi sepenuhnya saat audit lanjut.

## 4. File Laporan yang Sudah Dibuat

- `00_EXECUTIVE_SUMMARY.md` ✅
- `01_SOURCE_INVENTORY.md` ✅
- `02_ARCHITECTURE_AND_STATE.md` ✅ (sedang dibuat)

## 4. Coverage dan Belum Diperiksa

Berikut subsystem yang belum sepenuhnya diperiksa:

- Semua method body dalam `FullGameCanvas.java` (sekitar 459 baris kode, bukan hanya deklarasi)
- Semua method body dalam `RunWorld.java` (sekitar 1557 baris kode)
- Setiap method detail di `ContentCatalog.java` (432 baris)
- Setiap metode di `AssetLibrary.java`
- Setiap metode di `SaveStore.java`
- Setiap metode di `MathUtil.java`
- File `tools/*` — akan divalidasi statis
- File `data/wiki/*` — akan dicatat isi tetapi analisis logika dibatasi

*Inventaris ini akan diperbarui seiring berjalannya audit.*

