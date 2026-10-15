# HoloJava JAR Forensics Bytecode Map

## 1. Entry Point dan Lifecycle MIDlet

### FullGameMIDlet (me.holojava.fullgame.FullGameMIDlet)

- **Method yang Ditemukan** (via `javap -c`):
  - `startApp()` — Memulai aplikasi, membuat FullGameCanvas, validasi ContentCatalog, memulai gameThread
  - `pauseApp()` — Memausa aplikasi, memanggil canvas.pauseApplication()
  - `destroyApp(boolean)` — Menghentikan aplikasi, memanggil canvas.stopApplication()
  - `requestShutdown()` — Antarmuka dari FullGameCanvas.ShutdownRequest, memanggil notifyDestroyed()

- **Alur Lifecycle**:
  1. `startApp()` dipanggil MIDlet container
  2. Jika `canvas == null`, instantiate `SaveStore`, jalankan `ContentCatalog.validate()`
  3. Buat `AssetLibrary`, jika `validation == null` dan `assets.failures() > 0`, set validation error
  4. Buat `FullGameCanvas(assets, save, validation, this)`
  5. `display.setCurrent(canvas)` — Set canvas sebagai display current
  6. Mulai `gameThread = new Thread(canvas, "holojava-fullgame")`
  7. `canvas.resumeApplication()`
  8. Cek `canvas.exitRequested()`, jika ya, `notifyDestroyed()`

- **Class Hierarchy**: `FullGameMIDlet extends MIDlet implements FullGameCanvas.ShutdownRequest`

## 2. State Machine dan UI

### FullGameCanvas States (enum dalam bytecode)

- `MENU = 0`
- `MODES = 1`
- `CHARACTERS = 2`
- `STAGES = 3`
- `SETTINGS = 4`
- `PREP = 5`
- `GAME = 6`
- `RESULTS = 7`
- `CONTROLS = 8`

### Alur State Transitions

- `MENU` → CHARACTERS (tombol Play)
- `MENU` → MODES (tombol Game Mode)
- `MENU` → CHARACTERS (based on characterIndex)
- `MENU` → STAGES (tombol Stage Select)
- `MENU` → SETTINGS (tombol Settings)
- `MENU` → EXIT (tombol Exit/5 + select)

 Dari MODES → CHARACTERS
 Dari CHARACTERS → STAGES (pilih character)
 Dari STAGES → PREP (mulai run)
 Dari PREP → GAME (startRun)
 Dari GAME → RESULTS (game over / victory)
 Dari RESULTS → MENU (kembali / replay)

### Viewport

- **Konstanta**: `MIN_W = 320`, `MIN_H = 240` (resolusi logis)
- **Render Offset**: `ox = (w - MIN_W) / 2`, `oy = (h - MIN_H) / 2`
- **Viewport Update**: `viewport.update(w, h)` di setiap frame

## 3. Game Loop dan Input

### RunWorld.run() Structure

- **Tick Duration**: 40ms per update cycle
- **Accumulator Pattern**:
  - `accumulator += delta` (delta dari tick sebelumnya)
  - `updates = 0`
  - `while (accumulator >= 40L && updates < 3)`
  - `update(now); accumulator -= 40L; updates++`
- **Max Updates per Tick**: 3
- **Sleep antar-Tick**: `sleep(16)` (sekitar 60 FPS target)

### keyPressed(int key)

- `key == '5'` → `skillPressed = true`
- `key == '9'` → `specialPressed = true`
- `key == '1'` → `attackPulse = true`

### Update Method

- Baca `getKeyStates()` untuk status input
- Parse bitmask: `FIRE_PRESSED`, `UP_PRESSED`, `DOWN_PRESSED`, `LEFT_PRESSED`, `RIGHT_PRESSED`, `GAME_A_PRESSED`
- Edge detection: `fire && !fireWasDown`, dll.
- Update `fireWasDown`, `upWasDown`, dan seterusnya
- Jika `validationError != null`, return early
- `viewport.update(getWidth(), getHeight())`
- Jika `world != null`, `world.setViewport(viewport)` dan `world.update(keys, ...)`

- Jika `screen == GAME`:
  - Tombol FIRE untuk pause/resume
  - `world.update(keys, attackHeld || attackEdge || attackPulse, skillPressed, specialPressed, now)`
  - `attackPulse = false; skillPressed = false; specialPressed = false`
  - Jika `STATE_LEVEL_UP`: navigasi offer dengan up/down, select dengan fire
  - Jika `STATE_GAME_OVER` atau `STATE_VICTORY`: set `worldWasVictory`, pindah ke `RESULTS`

### Render Method

- `getGraphics()`, `fillRect(0, 0, w, h)` (background hitam)
- `viewport.update(w, h)`
- `ox = (w - MIN_W) / 2`, `oy = (h - MIN_H) / 2`
- Jika `validationError`, tampilkan error text
- Jika `screen == GAME` dan `world != null`:
  - `world.draw(g, viewport.offsetX, viewport.offsetY, System.currentTimeMillis())`
  - Draw Level Up jika STATE_LEVEL_UP
  - Draw Pause jika STATE_PAUSED
  - Draw Game Over / Victory jika STATE_GAME_OVER / STATE_VICTORY
- Jika `screen == RESULTS` → drawResults
- Jika lain → drawMenuScreen

## 4. Combat dan Weapon Patterns

### Weapon Categories (ContentCatalog)

- `WEAPON_MELEE = 0`
- `WEAPON_MULTISHOT = 1`
- `WEAPON_RANGED = 2`

### Weapon Patterns (ContentCatalog)

- `PATTERN_MELEE_ARC = 0`
- `PATTERN_PROJECTILE = 1`
- `PATTERN_ORBIT = 2`
- `PATTERN_BOOMERANG = 3`
- `PATTERN_AOE = 4`
- `PATTERN_HOMING = 5`
- `PATTERN_PIERCING = 6`
- `PATTERN_WALL = 7`

### Damage dan Cooldown

- Setiap weapon memiliki: `cooldownMs`, `damage`, `speed`, `range`, `area`, `knockback`, `weight`
- Melee weapon: damage langsung bersentuhan
- Projectile: pergerakkan berdasarkan speed dan direction
- Orbit: mengelilingi player
- Boomerang: pulang ke player setelah dilepaskan
- AOE: area of effect damage
- Homing: mengikuti target
- Piercing: melewati beberapa target
- Wall: menempatkan tembok

### Enemy Behavior Types

- `AI_CHASER = 0`: Musuh mengejar player
- `AI_SHOOTER = 1`: Musuh menembak ke arah player
- `AI_CHARGER = 2`: Musuh mendekati cepat, meledak
- `AI_BOMBER = 3`: Musun meledak saat menempuh jarak tertentu
- `AI_HEALER = 4`: Musuh menyembuhkan musuh lain

### Boss Definitions

- 11 boss terdaftar dengan: `id`, `name`, `hp`, `atk`, `behavior`, `cooldownMs`, `exp`, `sprite`
- Behaviour boss mirip enemy tapi dengan HP dan attack yang lebih tinggi
- Jadwal serangan boss di-control melalui `cooldownMs`

## 5. Entity dan AI

### Entity Pools (RunWorld)

- `ENEMY_POOL = 48` — Maksimal 48 musuh bersamaan
- `SHOT_POOL = 128` — Maksimal 128 projectile bersamaan
- `MELEE_POOL = 24` — Maksimal 24 attack melee bersamaan
- `PICKUP_POOL = 96` — Maksimal 96 item XP/utility bersamaan

### Pool Behavior Saat Penuh

- Saat pool penuh, entitas baru akan digantikan/dilegasi
- Tidak ada alokasi memory baru selama loop update (dengan pola pooling)
- Setiap entity dideklarasikan sebagai `private final Enemy[] enemies = new Enemy[ENEMY_POOL]`

### Spawn dan Despawn

- Interval spawn ditetapkan per-stage (`spawnIntervalMs`)
- Max enemies per stage (`maxEnemies`)
- Enemy deceased → didepop dari pool dan dihapus dari daftar aktif
- Enemy yang tidak aktif (di luar layar) → tidak dihapus tapi tidak diproses

### Collision Detection

- Setiap entity memiliki `collW` dan `collW` (collision width/height)
- Damage diterima ketika rect collision antara projectile/melee dengan player
- Invulnerability frame setelah damage (timer `invulUntil`)
- Lifesteal/slow/regen timer terpisah

## 6. Progression

### XP dan Level-Up

- `experience` — Total XP yang terkumpul
- `expNeed` — XP yang dibutuhkan untuk level berikutnya
- `playerLevel` — Level saat ini (awal: 1)
- `coins` — Mata uang accumulative

### Level-Up Offer System

- `OFFER_SKILL = 0` s.d. `OFFER_HOLD = 7` (8 opsi total)
- `offerUsed[64]` — Melacak offer yang sudah digunakan
- `statOfferCounts[6]` — Hitung offer per kategori

### Offer Tipe

- `OFFER_SKILL` — Upgrade skill
- `OFFER_WEAPON` — Upgrade weapon
- `OFFER_ITEM` — Dapat item
- `OFFER_STAT` — Stat upgrade
- `OFFER_FOOD` — Makanan pemulih HP
- `OFFER_COINS` — Dapat koin
- `OFFER_REROLL` — Ulangi opsi
- `OFFER_HOLD` — Tahan opsi

### Reset Antar-Run

- Mode hardcore mengubah HP Endless menjadi 1
- SaveStore menyimpan progres character
- Setiap run dimulai dengan state fresh (world dibuat baru)

## 7. Konten dan Aset

### Katalog yang Diterima dari JAR

- **47 karakter** (dilihat dari ContentCatalog.CHARACTERS.length di source/bytecode)
- **94 weapon** (BASE_WEAPONS + hasil buildWeapons dari WikiCatalogData)
- **37 item** (effect: damage, speed, crit, hp, exp, pickup, shield, food drop)
- **11 boss** (dengan hp/atk/behavior/cooldown)
- **16 stage** (11 utama + 5 varian malam + Time Mode)
- **6 tipe AI** (CHASER, SHOOTER, CHARGER, BOMBER, HEALER + variasi)
- **8 pola senweapon** (MELEE, MULTISHOT, RANGED + 5 pattern lanjutan)

### Hubungan Aset dengan Konten

- Setiap `CharacterDef` memiliki `mainWeapon`, `special`, `skill`
- Setiap `WeaponDef` memiliki `prerequisiteA`, `prerequisiteB` (untuk collab)
- Setiap `StageDef` memiliki `enemyA`, `enemyB`, `enemyC` (3 musuh per stage)
- Setiap `BossDef` memiliki `sprite` dan `assetApproximation` flag
- Asset path dihasilkan dari `AssetLibrary` melalui `AssetLibrary.java`

## 8. Catatan Penting

- Beberapa class dalam bytecode adalah inner class (dianotasi `$`)
- Method-method kunci memiliki implementasi bytecode yang bisa dilihat tapi struktur bisa berbeda dengan source terkompilasi
- Pool entity pattern (`final X[] array`) adalah kunci untuk menghindari garbage collection selama runtime
- Timing game loop 40ms tick bersifat deterministik, asalkan tidak ada variasi input yang tak terduga
- Analisis ini hanyalah peta struktur, tidak menjamin identitas runtime sempurna
