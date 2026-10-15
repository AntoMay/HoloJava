# Character Data Validation

## 1. Method

Three independent sources were compared for every field:

| Source | How read | Role |
|--------|----------|------|
| Reference JAR bytecode | `javap -c -p ContentCatalog.class` (static initializer) | **primary** — the shipped implementation |
| `data/wiki/characters.csv` | direct read, cols 1–9 | **independent cross-check** |
| Local assets | `ls assets/fullgame/characters/*` | asset-index verification |

No value was chosen by guesswork. Where JAR and CSV disagreed, the discrepancy is recorded explicitly (§5).

---

## 2. Per-Character Validation

### 2.1 Gawr Gura — index 0

| Field | Value | JAR bytecode | CSV row | Match |
|-------|-------|--------------|---------|-------|
| id | `gura` | `ldc #171` | `gawr_gura` (short form is JAR-native) | ✅ intent |
| name | Gawr Gura | `ldc #48` | `"Gawr Gura"` | ✅ |
| hp | 65 | `bipush 65` | `"65"` | ✅ |
| atkPercent | 110 | `bipush 110` | `"110"` | ✅ |
| spdPercent | 140 | `sipush 140` | `"140"` | ✅ |
| critPercent | 5 | `iconst_5` | `"5"` | ✅ |
| size | 1 | `iconst_1` | `"1"` | ✅ |
| mainWeapon | `trident_thrust` | `ldc #230` | `"Trident Thrust"` (display) | ✅ id resolves |
| special | Shark Call | `ldc #109` | `"Shark Call"` | ✅ |
| skill | Shark Bite | `ldc #108` | *(no column)* | ✅ JAR-only |
| playable | `true` | `iconst_1` | — | ✅ |
| assetGroup | `Gura` | `ldc #56` | — | ✅ |

**Weapon resolution**: `trident_thrust` defined at `ContentCatalog.java:185`, `characterOwner = "gura"` ✅
**Assets**: `characters/Gura/idle/spr_Gura_Idle_0..2.png` (3), `characters/Gura/run/spr_Gura_Run_0..5.png` (6) ✅
**Result: VALID**

### 2.2 Amelia Watson — index 1

| Field | Value | JAR bytecode | CSV row | Match |
|-------|-------|--------------|---------|-------|
| id | `ame` | `ldc #137` | `amelia_watson` | ✅ |
| name | Amelia Watson | `ldc #28` | `"Amelia Watson"` | ✅ |
| hp | 75 | `bipush 75` | `"75"` | ✅ |
| atkPercent | 130 | `sipush 130` | `"130"` | ✅ |
| spdPercent | 135 | `sipush 135` | `"135"` | ✅ |
| critPercent | 10 | `bipush 10` | `"10"` | ✅ |
| size | 3 | `iconst_3` | `"3"` | ✅ |
| mainWeapon | `pistol_shot` | `ldc #202` | `"Pistol Shot"` | ✅ |
| special | Slow Time | `ldc #114` | `"Slow Time"` | ✅ |
| skill | Hunter's Instinct | `ldc #70` | *(none)* | ✅ JAR-only |
| playable / group | `true` / `Ame` | `iconst_1` / `ldc #27` | — | ✅ |

**Weapon**: `ContentCatalog.java:186`, owner `ame` ✅
**Assets**: idle 3, run 6 ✅ — **Result: VALID**

### 2.3 Mori Calliope — index 2

| Field | Value | JAR bytecode | CSV row | Match |
|-------|-------|--------------|---------|-------|
| id | `calli` | `ldc #148` | `mori_calliope` | ✅ |
| name | Mori Calliope | `ldc #85` | `"Mori Calliope"` | ✅ |
| hp | 70 | `bipush 70` | `"70"` | ✅ |
| atkPercent | 115 | `bipush 115` | `"115"` | ✅ |
| spdPercent | 130 | `sipush 130` | `"130"` | ✅ |
| critPercent | 10 | `bipush 10` | `"10"` | ✅ |
| size | 4 | `iconst_4` | `"4"` | ✅ |
| mainWeapon | `scythe_swing` | `ldc #207` | `"Scythe Swing"` | ✅ |
| special | The Reaper | `ldc #126` | `"The Reaper"` | ✅ |
| skill | Undying | `ldc #133` | *(none)* | ✅ JAR-only |
| playable / group | `true` / `Calli` | `iconst_1` / `ldc #35` | — | ✅ |

**Weapon**: `ContentCatalog.java:187`, owner `calli` ✅
**Assets**: idle 4, run 6 ✅ — **Result: VALID**

### 2.4 Takanashi Kiara — index 3

| Field | Value | JAR bytecode | CSV row | Match |
|-------|-------|--------------|---------|-------|
| id | `kiara` | `ldc #179` | `takanashi_kiara` | ✅ |
| name | Takanashi Kiara | `ldc #124` | `"Takanashi Kiara"` | ✅ |
| hp | 90 | `bipush 90` | `"90"` | ✅ |
| atkPercent | 100 | `bipush 100` | `"100"` | ✅ |
| spdPercent | 140 | `sipush 140` | `"140"` | ✅ |
| critPercent | 5 | `iconst_5` | `"5"` | ✅ |
| size | 3 | `iconst_3` | `"3"` | ✅ |
| mainWeapon | `phoenix_sword` | `ldc #200` | `"Phoenix Sword"` | ✅ |
| special | Phoenix Fire | `ldc #99` | `"Phoenix Fire"` | ✅ |
| skill | Second Flame | `ldc #107` | *(none)* | ✅ JAR-only |
| playable / group | `true` / `Kiara` | `iconst_1` / `ldc #73` | — | ✅ |

**Weapon**: `ContentCatalog.java:188`, owner `kiara` ✅
**Assets**: idle 4, run 6 ✅ — **Result: VALID**

### 2.5 Ninomae Inanis — index 4

| Field | Value | JAR bytecode | CSV row | Match |
|-------|-------|--------------|---------|-------|
| id | `ina` | `ldc #177` | `ninomae_inanis` | ✅ |
| name | Ninomae Inanis | `ldc #87` | `"Ninomae Inanis"` | ✅ |
| hp | 75 | `bipush 75` | `"75"` | ✅ |
| atkPercent | 90 | `bipush 90` | `"90"` | ✅ |
| spdPercent | 150 | `sipush 150` | `"150"` | ✅ |
| critPercent | 1 | `iconst_1` | `"1"` | ✅ |
| size | 1 | `iconst_1` | `"1"` | ✅ |
| mainWeapon | `summon_tentacle` | `ldc #225` | `"Summon Tentacle"` | ✅ |
| special | **Spirit of the Oni** | `ldc #117` | ⚠️ `"Tako Spin"` | ❌ **disagree** |
| skill | The Ancient One | `ldc #125` | *(none)* | ✅ JAR-only |
| playable / group | `true` / `Ina` | `iconst_1` / `ldc #71` | — | ✅ |

**Weapon**: `ContentCatalog.java:189`, owner `ina` ✅
**Assets**: idle 4, run 6 ✅ — **Result: VALID** (see §5 for the special-name discrepancy)

---

## 3. Validator Requirements

`ContentCatalog.validate()` (`:407-412`, now shifted) enforces two conditions per character:

| Condition | Gura | Ame | Calli | Kiara | Ina |
|-----------|------|-----|-------|-------|-----|
| `weapon(c.mainWeapon) != null` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `c.playable == true` | ✅ | ✅ | ✅ | ✅ | ✅ |

All ten checks pass. `validate()` would return `null` (no error).

---

## 4. Asset Index Alignment

`AssetLibrary.groupIndex()` (`:160-167`) → `Gura=0, Ame=1, Calli=2, Kiara=3, Ina=4`.

| Index | Catalog `assetGroup` | `groupIndex()` result | Sprite dir | Frame counts |
|-------|---------------------|----------------------|------------|--------------|
| 0 | `Gura` | 0 | `characters/Gura/` | idle 3, run 6 ✅ |
| 1 | `Ame` | 1 | `characters/Ame/` | idle 3, run 6 ✅ |
| 2 | `Calli` | 2 | `characters/Calli/` | idle 4, run 6 ✅ |
| 3 | `Kiara` | 3 | `characters/Kiara/` | idle 4, run 6 ✅ |
| 4 | `Ina` | 4 | `characters/Ina/` | idle 4, run 6 ✅ |

Also matches `AssetLibrary.java:67-69` preload arrays (`groups`, `idleCounts = {3,3,4,4,4}`) and `weaponFiles = {trident, ame_bullet, scythe, phoenix, tentacle}` — each corresponding to the character's `mainWeapon` sprite in `projectiles/`.

---

## 5. Recorded Discrepancies (not resolved in A1)

| # | Item | JAR | CSV | Action |
|---|------|-----|-----|--------|
| D1 | Ina `special` | `Spirit of the Oni` | `Tako Spin` | **JAR value used.** JAR is the shipped reference implementation; CSV is documentation-grade. `special` has zero gameplay effect, so this is cosmetic either way. Needs a decision before Special Attack is implemented (Priority B2). |

D1 is the only field-level disagreement across all five characters. All 45 other stat fields match exactly.

---

## 6. ID Form Note

Catalog ids are short (`gura`, `ame`) while CSV ids are long (`gawr_gura`, `amelia_watson`). This is intentional and matches the JAR and `AssetLibrary.groupIndex()`. It is also the root of the pre-existing portrait path mismatch documented in `03_DIFF_AND_REGRESSION_REVIEW.md` §5 (Priority A2, **not fixed here**).

---

**Status: 5/5 characters VALID.**
