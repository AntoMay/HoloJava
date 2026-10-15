# HoloJava — Per-Character Evidence Table (Tahap 3D)

Every row below is traceable to a local file, an asset path, or disassembly of the reference JAR. Nothing is inferred from memory.

---

## 1. Verified Characters (5) — Status: VALID

### 1.1 `gura` — Gawr Gura

| Field | Value | Evidence |
|-------|-------|----------|
| Id | `gura` | JAR bytecode `ldc #170 // String gura` |
| Name | Gawr Gura | JAR bytecode `ldc #47 // String Gawr Gura` |
| HP / ATK / SPD / Crit / Size | 65 / 110 / 140 / 5 / 1 | JAR bytecode `bipush 65`, `bipush 110`, `sipush 140`, `iconst_5`, `iconst_1` |
| Basic attack | `trident_thrust` (Trident Thrust) | JAR `ldc #230`; defined `ContentCatalog.java:173` |
| Special attack | **Shark Call** | JAR `ldc #108`; CSV `special_attack` agrees |
| Skill | **Shark Bite** | JAR `ldc #107` — **bytecode only, not in any source file** |
| Sprite group | `Gura` | JAR `ldc #55 // String Gura` |
| Sprite files | `spr_Gura_Idle_0..2.png`, `spr_Gura_Run_0..5.png` | `assets/fullgame/characters/Gura/{idle,run}/` |
| Portrait | `portraits/gawr_gura.png` (CSV id) — **does not match** catalog id `gura` | `assets/fullgame/portraits/` |
| Unknown | Skill effect/upgrade behavior; combo skill | No repo data |

### 1.2 `ame` — Amelia Watson

| Field | Value | Evidence |
|-------|-------|----------|
| Id / Name | `ame` / Amelia Watson | JAR `ldc #34`, `ldc #27` |
| Stats | 75 / 130 / 135 / 10 / 3 | JAR `bipush 75`, `sipush 130`, `sipush 135`, `bipush 10`, `iconst_3` |
| Basic attack | `pistol_shot` | JAR `ldc #202`; `ContentCatalog.java:174` |
| Special attack | **Slow Time** | JAR `ldc #113`; CSV agrees |
| Skill | **Hunter's Instinct** | JAR `ldc #69` — bytecode only |
| Sprite group / files | `Ame`; `spr_Ame_idle_0..2.png`, `spr_Ame_run_0..5.png` | `assets/fullgame/characters/Ame/` |
| Portrait mismatch | `portraits/amelia_watson.png` vs catalog id `ame` | confirmed |
| Unknown | Skill effect; combo skill | No repo data |

### 1.3 `calli` — Mori Calliope

| Field | Value | Evidence |
|-------|-------|----------|
| Id / Name | `calli` / Mori Calliope | JAR `ldc #147`, `ldc #84` |
| Stats | 70 / 115 / 130 / 10 / 4 | JAR `bipush 70`, `bipush 115`, `sipush 130`, `bipush 10`, `iconst_4` |
| Basic attack | `scythe_swing` | JAR `ldc #207`; `ContentCatalog.java:175` |
| Special attack | **The Reaper** | JAR `ldc #125`; CSV agrees |
| Skill | **Undying** | JAR `ldc #132` — bytecode only |
| Sprite group / files | `Calli`; `spr_Calli_idle_0..3.png`, `spr_Calli_run_0..5.png` | `assets/fullgame/characters/Calli/` |
| Portrait mismatch | `portraits/mori_calliope.png` vs `calli` | confirmed |
| Unknown | Skill effect; combo skill | No repo data |

### 1.4 `kiara` — Takanashi Kiara

| Field | Value | Evidence |
|-------|-------|----------|
| Id / Name | `kiara` / Takanashi Kiara | JAR `ldc #179`, `ldc #123` |
| Stats | 90 / 100 / 140 / 5 / 3 | JAR `bipush 90`, `bipush 100`, `sipush 140`, `iconst_5`, `iconst_3` |
| Basic attack | `phoenix_sword` | JAR `ldc #200`; `ContentCatalog.java:176` |
| Special attack | **Phoenix Fire** | JAR `ldc #98`; CSV agrees |
| Skill | **Second Flame** | JAR `ldc #106` — bytecode only |
| Sprite group / files | `Kiara`; `spr_Kiara_idle_0..3.png`, `spr_Kiara_run_0..5.png` | `assets/fullgame/characters/Kiara/` |
| Portrait mismatch | `portraits/takanashi_kiara.png` vs `kiara` | confirmed |
| Unknown | Skill effect; combo skill | No repo data |

### 1.5 `ina` — Ninomae Inanis

| Field | Value | Evidence |
|-------|-------|----------|
| Id / Name | `ina` / Ninomae Inanis | JAR `ldc #177`, `ldc #86` |
| Stats | 75 / 90 / 150 / 1 / 1 | JAR `bipush 75`, `bipush 90`, `sipush 150`, `iconst_1`, `iconst_1` |
| Basic attack | `summon_tentacle` | JAR `ldc #225`; `ContentCatalog.java:177` |
| Special attack | **Spirit of the Oni** | JAR `ldc #116`; CSV agrees |
| Skill | **The Ancient One** | JAR `ldc #124` — bytecode only |
| Sprite group / files | `Ina`; `spr_Ina_idle_0..3.png`, `spr_Ina_run_0..5.png` | `assets/fullgame/characters/Ina/` |
| Portrait mismatch | `portraits/ninomae_inanis.png` vs `ina` | confirmed |
| Unknown | Skill effect; combo skill | No repo data |

---

## 2. Unresolved Characters (42) — Status: UNRESOLVED

Full roster (names/ids from `data/wiki/characters.csv`, alphabetically):

`airani_iofifteen`, `akai_haato`, `aki_rosenthal`, `amane_kanata`, `anya_melfissa`, `ayunda_risu`, `azki`, `ceres_fauna`, `hakos_baelz`, `himemori_luna`, `hoshimachi_suisei`, `houshou_marine`, `inugami_korone`, `irys`, `kaela_kovalskia`, `kiryu_coco`, `kobo_kanaeru`, `kureiji_ollie`, `minato_aqua`, `moona_hoshinova`, `murasaki_shion`, `nakiri_ayame`, `nanashi_mumei`, `natsuiro_matsuri`, `nekomata_okayu`, `ookami_mio`, `oozora_subaru`, `ouro_kronii`, `pavolia_reine`, `roboco_san`, `sakura_miko`, `shirakami_fubuki`, `shiranui_flare`, `shirogane_noel`, `tokino_sora`, `tokoyami_towa`, `tsukumo_sana`, `tsunomaki_watame`, `usada_pekora`, `vestia_zeta`, `yozora_mel`

Per-character gaps (identical for all 42):

| Field | Status | Evidence |
|-------|--------|----------|
| Name + id | ✅ available | CSV cols 1-2 |
| Stats (hp/atk/spd/crit/size) | ✅ available | CSV cols 5-9 |
| Special attack name | ✅ available | CSV col 4 |
| **Animated sprite** | ❌ **absent** | no folder under `assets/fullgame/characters/` |
| Portrait | ⚠️ present but unreachable | `portraits/<csv_id>.png`; `AssetLibrary:155` resolves catalog id |
| **Basic attack weapon** | ❌ **unresolvable** | CSV gives display name; `weapon(id)` matches snake_case ids; no mapping table |
| **Skill name** | ❌ **absent** | CSV has no skill column; JAR has skills only for the 5 |
| Skill effect | ❌ absent | no repo data |
| Combo skill | ❌ absent | no repo data |

**Consequence**: each of the 42 fails `ContentCatalog.validate()` at `ContentCatalog.java:410` (`main_weapon_missing`).

---

## 3. Summary

| Metric | Count | Source |
|--------|-------|--------|
| Characters with animated sprites | **5** | assets tree + JAR (identical) |
| Characters with basic attack resolvable to `BASE_WEAPONS` | **5** | JAR + `ContentCatalog.java:173-177` |
| Characters with skill name known | **5** | JAR bytecode only |
| Characters with skill *effect* known | **0** | — |
| Characters with combo skill known | **0** | — |
| CSV documented roster | 47 | `data/wiki/characters.csv` |
| Characters passing `validate()` | **5** | derived |

---

**No sprite, skill, effect, or combination was invented.** Every value traces to a file path, source line, or bytecode offset. Stop — awaiting review.
