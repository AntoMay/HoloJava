# Character & Map System — HoloJava FullGame

> **Tanggal:** 2026-10-18  
> **Tujuan:** Merombak menu utama agar mirip HoloCure asli, menambah sistem map prosedural, dan BGM untuk map tanpa musik.

---

## 1. Arsitektur Menu Baru (Choose Your Idol)

### Flow Navigasi
```
MAIN MENU
    │
    ├── CONTINUE (lanjut run tersimpan)
    ├── CHOOSE YOUR IDOL  ← BARU: grid 5 karakter
    │       │
    │       ├── [Gura] [Ame] [Calli] [Kiara] [Ina]  ← 5 kotak, navigasi L/R/U/D
    │       │
    │       └── Panel Detail (kanan/bawah kotak terpilih):
    │           ├─ Nama Karakter + Tab: [STATUS] [SKILL]
    │           ├─ Sprite Animasi (idle/run loop)
    │           ├─ Statistik: HP, ATK, SPD, CRT, Size
    │           ├─ Weapon Utama + Special
    │           └─ Deskripsi Skill (saat tab SKILL aktif)
    │
    ├── CHOOSE MODE  ← BARU: hanya Stage Mode (Endless/Time nanti)
    │       └── Stage Select
    │
    ├── SHOP (Toko)
    ├── ARMORY
    ├── SETTINGS
    └── CREDITS
```

### Konstanta Layar Baru
```java
// FullGameCanvas.java
private static final int CHOOSE_IDOL = 14;   // Choose Your Idol screen
private static final int CHOOSE_MODE = 15;   // Choose Mode screen
// CHARACTERS (2) → diganti/dihapus, STAGES (11) → dipakai dari CHOOSE_MODE
```

### Struktur Data Karakter (ContentCatalog.CharacterDef)
Sudah ada field yang dibutuhkan:
- `id`, `name` — identitas & nama tampil
- `hp`, `atkPercent`, `spdPercent`, `critPercent`, `size` — statistik
- `mainWeapon`, `special`, `skill` — loadout
- `assetGroup` — folder sprite (Gura, Ame, Calli, Kiara, Ina)
- `playable` — filter roster

**Tambahan untuk menu baru** (bisa di-extend nanti):
```java
// Opsional: tambahkan ke CharacterDef bila perlu
String skillDesc;        // deskripsi skill untuk panel
int spriteCols, spriteRows; // grid animasi idle/run
```

---

## 2. Menambah Karakter Baru (Panduan)

### Sumber Data
1. **Sprite** — `/home/antomay/Projects/HoloCure_Unpacked/sprites/spr_{id}_idle.png`, `spr_{id}_run.png`, `spr_{id}_Portrait.png`
2. **Stat & Skill** — Wiki HoloCure (holocure.fandom.com) + `data/wiki/characters.csv` (jika ada)
3. **Animasi** — AssetLibrary memuat `idle` (4 frame) dan `run` (4 frame) per outfit (O1-O4)

### Langkah Tambah Karakter

#### A. Siapkan Asset Sprite
```bash
# 1. Ekstrak dari unpack (sudah ada di sprites/)
# 2. Pastikan naming konsisten:
#    spr_{id}_idle.png, spr_{id}_run.png
#    spr_{id}_O1_idle.png, spr_{id}_O1_run.png, ... O4
#    spr_{id}_Portrait.png
# 3. Copy ke assets/fullgame/sprites/{id}/ (atau pakai AssetLibrary grup existing)
```

#### B. Definisikan di ContentCatalog.java
```java
// Tambah ke BASE_CHARACTERS array (urutan = index roster)
character("id_baru", "Nama Tampil", HP, ATK%, SPD%, CRT%, SIZE,
          "main_weapon_id", "Nama Special", "Nama Skill",
          true, "AssetGroup", false, false),
```
- `id_baru` = lowercase, sama dengan folder sprite & weapon owner
- `AssetGroup` = nama folder di texture_groups (mis. "Gura", "Ame")

#### C. Tambah Weapon Utama (jika belum ada)
```java
// Di BASE_WEAPONS array
weapon("weapon_id", "Weapon Name", WEAPON_MAIN, TYPE, PATTERN,
       cooldown, dmg, count, speed, range, area, knockback, weight, maxLv,
       "id_baru", "sprite_name", "", ""),
```

#### D. Update AssetLibrary.java
```java
// Di loadCharacters(): grup sudah otomatis dari assetGroup
// Pastikan texture group {id} ada di texture_groups/ atau fallback ke default
```

#### E. Rebuild & Test
```bash
tools/run_fullgame_tests.sh
tools/build_fullgame.sh
```
Verifikasi: karakter muncul di Choose Your Idol, sprite animasi jalan, stats & skill tampil.

---

## 3. Sistem Map Prosedural (Seperti Hutan Sawit)

### Generator Existing: `tools/generate_palm_forest_map.py`
- Input: reference image 1536×1024 (3:2)
- Output: 320×240 arena + 4×4 grid parts (80×60 each) di `assets/fullgame/stages/{map}_parts/`
- StageDef pakai `mapCols=4`, `mapRows=4`, `tile="{map}"`

### Template Generator Map Baru
Buat file `tools/generate_{mapname}_map.py` berbasis template:

```python
#!/usr/bin/env python3
"""Generate {MapName} map from reference image."""

import os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_REF = os.path.expanduser("~/Downloads/{reference_image}.png")

MAP_W, MAP_H = 320, 240
GRID_COLS, GRID_ROWS = 4, 4          # atau 5×5, 6×3, dst. sesukanya
PART_W = MAP_W // GRID_COLS
PART_H = MAP_H // GRID_ROWS
VERTICAL_BIAS = 0.42                  # crop bias, sesuaikan komposisi

def crop_to_aspect(src, target_ratio):
    w, h = src.size
    if w / float(h) > target_ratio:
        new_w = int(round(h * target_ratio))
        left = (w - new_w) // 2
        return src.crop((left, 0, left + new_w, h))
    new_h = int(round(w / target_ratio))
    top = int((h - new_h) * VERTICAL_BIAS)
    return src.crop((0, top, w, top + new_h))

def main(argv):
    ref = argv[0] if argv else DEFAULT_REF
    if not os.path.isfile(ref):
        sys.stderr.write(f"reference not found: {ref}\n")
        return 2
    src = Image.open(ref).convert("RGB")
    crop = crop_to_aspect(src, float(MAP_W) / MAP_H)
    arena = crop.resize((MAP_W, MAP_H), Image.LANCZOS)

    stages = os.path.join(ROOT, "assets/fullgame/stages")
    os.makedirs(stages, exist_ok=True)
    arena.save(os.path.join(stages, "{mapname}.png"), optimize=True)

    parts_dir = os.path.join(stages, "{mapname}_parts")
    os.makedirs(parts_dir, exist_ok=True)
    for f in os.listdir(parts_dir):
        if f.endswith(".png"): os.remove(os.path.join(parts_dir, f))

    for row in range(GRID_ROWS):
        for col in range(GRID_COLS):
            box = (col*PART_W, row*PART_H, (col+1)*PART_W, (row+1)*PART_H)
            arena.crop(box).save(os.path.join(parts_dir, f"{mapname}_{row}{col}.png"))

    print(f"Generated {mapname}: {GRID_ROWS}x{GRID_COLS} grid")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
```

### Daftar Map Prosedural (Contoh)
| Map ID | Nama | Grid | Reference Source |
|--------|------|------|------------------|
| `palm_forest` | Arena Hutan Sawit | 4×4 | `~/Downloads/fc28e118-...png` |
| `beach_shore` | Pantai | 4×4 | unpack `BG_beachshore` + custom |
| `holloffice` | Holo Office | 5×4 | unpack `bg_holoOfficeFloor` + walls |
| `halloween_castle` | Halloween Castle | 4×4 | unpack `BG_Castle*` tileset |

### Daftarkan Map di ContentCatalog.java (StageDef)
```java
stage("map_id", "Nama Map", false, false, order,
      "map_id", 0, "enemyA", "enemyB", "enemyC",
      spawnInterval, maxEnemies, coinClear, unlockReq,
      "bossA", bossTime, "bossB", bossTime,
      "source", "coverage",
      0, 0,  // worldW, worldH (0 = viewport)
      GRID_COLS, GRID_ROWS),  // mapCols, mapRows > 0 = composed grid
```

### Enemy & Boss
- Gunakan `EnemyDef` & `BossDef` existing di ContentCatalog
- `spawnIntervalMs`: 800-1500 untuk normal, 600 untuk padat
- `unlockRequirement`: urutan stage (0 = unlocked, 1 = butuh stage 0 selesai, dst.)

---

## 4. BGM untuk Map Tanpa Musik (Hutan Sawit)

### Kebijakan
- **stage6 (Arena Hutan Sawit)** sekarang memiliki BGM kustom: transkripsi dari MP3 viral "M.B.G (Mas Bahlil Ganteng)".
- Stage index 10 (ContentCatalog.STAGES[10]) → `bgm_palm_forest.mid` (24 KB).

### Pipeline Transkripsi MP3 → MIDI (Selesai)
Sama dengan pipeline BGM stage (basic-pitch), input MP3:

```bash
# 1. Convert MP3 → WAV (basic-pitch butuh audio PCM)
ffmpeg -i "input.mp3" -ar 44100 -ac 2 temp.wav

# 2. Jalankan basic-pitch dengan threshold padat (sama stage BGM)
basic-pitch /tmp/midi_out temp.wav --onset-threshold 0.3 --frame-threshold 0.3 --minimum-note-length 60

# 3. Post-process (register-split, type-0, dedupe, quantize ≤60KB)
#    Script: /tmp/opencode/post_process_midi.py
#    Output: assets/fullgame/audio/bgm_palm_forest.mid (24 KB)
```

### Daftarkan di BgmAudio.java
```java
// Di musicFor():
case 10: return "/assets/fullgame/audio/bgm_palm_forest.mid"; // Arena Hutan Sawit (index 10)
```

### Update Budget Test
`BgmStageMusicTest.MAX_TOTAL_BYTES` = 614400 (cukup untuk 11 track).

---

## 5. File & Lokasi Penting

| File | Fungsi |
|------|--------|
| `src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java` | Definisi karakter, weapon, stage, upgrade |
| `src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java` | Menu flow, input, rendering |
| `src/java-me-fullgame/me/holojava/fullgame/AssetLibrary.java` | Load sprite/portrait per character |
| `tools/generate_palm_forest_map.py` | Template generator map prosedural |
| `tools/holojava_midi_pipeline.py` | Pipeline transkripsi audio → MIDI |
| `assets/fullgame/stages/{map}_parts/` | Grid parts untuk composed background |
| `assets/fullgame/audio/bgm_{map}.mid` | BGM per stage |
| `data/wiki/characters.csv` | Extended roster (wiki) |
| `docs/CHARACTER_MAP_SYSTEM.md` | **File ini** |

---

## 6. Checklist Implementasi (Untuk PR/Commit)

- [x] **Choose Your Idol screen** (`CHOOSE_IDOL`)
    - [x] Grid 5×1 character boxes
    - [x] Navigasi L/R antar kotak (wrap)
    - [x] Panel detail: nama, tab STATUS/SKILL, sprite animasi, stats, skill desc
    - [x] FIRE → ke CHOOSE_MODE, LEFT di index 0 → MENU
- [x] **Choose Mode screen** (`CHOOSE_MODE`)
    - [x] Hanya "Stage Mode" (Endless/Time nanti)
    - [x] FIRE → ke STAGES, LEFT → CHOOSE_IDOL
- [x] **StageDef mapCols/mapRows** > 0 untuk composed background (stage6: 4×4)
- [x] **Generator map** sudah ada: `tools/generate_palm_forest_map.py`
- [x] **MP3 → MIDI** untuk `bgm_palm_forest.mid` (24 KB)
- [x] **BgmAudio.musicFor(10)** → `bgm_palm_forest.mid`
- [x] **Test suite** 33/33 pass
- [x] **Build phone + desktop** pass verify
- [x] **Dokumentasi** update di file ini

---

## 7. Catatan Teknis (Lengkap)

### AssetLibrary Character Loading
```java
// AssetLibrary.loadCharacters() memuat per assetGroup:
// idle: 4 frame, run: 4 frame, portrait: 1 frame
// Outfit O1-O4: load terpisah jika sprite ada
```

### Animasi Sprite di Menu
- Gunakan `AssetLibrary.characterIdle(index, frame)` untuk idle animation
- Frame rate: ~8 FPS (125 ms/frame) untuk idle
- Loop tak hingga di panel detail

### Performa
- Grid 4×4 parts = 16 image draw per frame (sudah dipakai Hutan Sawit, OK)
- MIDI ≤60 KB/track, total ≤600 KB (sudah di-enforce test)
- JAR size target < 2 MB (sekarang ~1.52 MB)

---

## 8. Riwayat Perubahan (Changelog)

| Tanggal | Commit | Perubahan |
|---------|--------|-----------|
| 2026-10-18 | `7a5776e` | Menu redesign: Choose Your Idol flow (Main → Idol → Mode → Stage) |
| 2026-10-18 | *(commit ini)* | BGM Hutan Sawit: MP3→MIDI transkripsi, update BgmAudio.musicFor(10) |
| 2026-10-18 | *(commit ini)* | Test update: BgmStageMusicTest, UiStateMachineTest untuk flow baru |

---

*Dokumen ini update seiring implementasi. Setiap penambahan karakter/map baru wajib catat di sini.*