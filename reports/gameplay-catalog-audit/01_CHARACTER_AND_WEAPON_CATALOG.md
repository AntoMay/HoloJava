# Character & Basic-Attack Catalog Audit

## 1. Catalog Activation Status

| Check | Result | Evidence |
|-------|--------|----------|
| `ContentCatalog.CHARACTERS` declaration | `= WikiCatalogData.buildCharacters()` | `ContentCatalog.java:168` |
| `buildCharacters()` body | `return new CharacterDef[0];` | `WikiCatalogData.java:165-167` |
| Active entries at runtime | **0** | derived |
| Reference JAR entries | **5** | `javap` `iconst_5; anewarray CharacterDef` |

**No character definition is active in the current source.** The five-character roster exists only in reference JAR bytecode and in the asset tree.

---

## 2. Five Characters — Definition, Assets, Weapons

Roster order below is authoritative: it matches the JAR `anewarray` order, `AssetLibrary.java:67-69` group order, and `BASE_WEAPONS` `characterOwner` order.

### 2.1 Gawr Gura

| Field | Value | Evidence |
|-------|-------|----------|
| Catalog id | `gura` | JAR `ldc #170` |
| Display name | Gawr Gura | JAR `ldc #47` |
| HP / ATK / SPD / Crit / Size | 65 / 110 / 140 / 5 / 1 | JAR `bipush 65,110` `sipush 140` `iconst_5,1` |
| Basic attack id | `trident_thrust` | JAR `ldc #230`; defined `ContentCatalog.java:173` |
| Attack pattern | `PATTERN_MELEE_ARC` | `ContentCatalog.java:173` |
| Idle frames | 3 (`spr_Gura_Idle_0..2.png`) | `assets/fullgame/characters/Gura/idle/` |
| Run frames | 6 (`spr_Gura_Run_0..5.png`) | `assets/fullgame/characters/Gura/run/` |
| Weapon sprite | `projectiles/trident.png` | `AssetLibrary.java:69,78` |
| Asset group | `Gura` → index 0 | `AssetLibrary.java:161` |
| **Skill name** | Shark Bite | JAR `ldc #107` |
| **Special name** | Shark Call | JAR `ldc #108`; CSV col 4 |
| Portrait | `portraits/gawr_gura.png` — **mismatched id** | asset path vs `AssetLibrary.java:155` |

### 2.2 Amelia Watson

| Field | Value | Evidence |
|-------|-------|----------|
| Catalog id | `ame` | JAR `ldc #34` |
| HP / ATK / SPD / Crit / Size | 75 / 130 / 135 / 10 / 3 | JAR |
| Basic attack id | `pistol_shot` | JAR `ldc #202`; `ContentCatalog.java:174` |
| Attack pattern | `WEAPON_MULTISHOT` + `PATTERN_PROJECTILE` | `ContentCatalog.java:174` |
| Idle / Run | 3 / 6 | `assets/fullgame/characters/Ame/` |
| Weapon sprite | `projectiles/ame_bullet.png` | `AssetLibrary.java:69,78` |
| Asset group | `Ame` → index 1 | `AssetLibrary.java:162` |
| **Skill name** | Hunter's Instinct | JAR `ldc #69` |
| **Special name** | Slow Time | JAR `ldc #113`; CSV agrees |
| Portrait | `portraits/amelia_watson.png` — mismatched | as above |

### 2.3 Mori Calliope

| Field | Value | Evidence |
|-------|-------|----------|
| Catalog id | `calli` | JAR `ldc #147` |
| HP / ATK / SPD / Crit / Size | 70 / 115 / 130 / 10 / 4 | JAR |
| Basic attack id | `scythe_swing` | JAR `ldc #207`; `ContentCatalog.java:175` |
| Attack pattern | `WEAPON_MELEE` + `PATTERN_MELEE_ARC` | `ContentCatalog.java:175` |
| Idle / Run | 4 / 6 | `assets/fullgame/characters/Calli/` |
| Asset group | `Calli` → index 2 | `AssetLibrary.java:163` |
| **Skill name** | Undying | JAR `ldc #132` |
| **Special name** | The Reaper | JAR `ldc #125`; CSV agrees |
| Portrait | `portraits/mori_calliope.png` — mismatched | as above |

### 2.4 Takanashi Kiara

| Field | Value | Evidence |
|-------|-------|----------|
| Catalog id | `kiara` | JAR `ldc #179` |
| HP / ATK / SPD / Crit / Size | 90 / 100 / 140 / 5 / 3 | JAR |
| Basic attack id | `phoenix_sword` | JAR `ldc #200`; `ContentCatalog.java:176` |
| Attack pattern | `WEAPON_MELEE` + `PATTERN_MELEE_ARC` | `ContentCatalog.java:176` |
| Idle / Run | 4 / 6 | `assets/fullgame/characters/Kiara/` |
| Asset group | `Kiara` → index 3 | `AssetLibrary.java:164` |
| **Skill name** | Second Flame | JAR `ldc #106` |
| **Special name** | Phoenix Fire | JAR `ldc #98`; CSV agrees |
| Portrait | `portraits/takanashi_kiara.png` — mismatched | as above |

### 2.5 Ninomae Inanis

| Field | Value | Evidence |
|-------|-------|----------|
| Catalog id | `ina` | JAR `ldc #177` |
| HP / ATK / SPD / Crit / Size | 75 / 90 / 150 / 1 / 1 | JAR |
| Basic attack id | `summon_tentacle` | JAR `ldc #225`; `ContentCatalog.java:177` |
| Attack pattern | `WEAPON_MELEE` + `PATTERN_MELEE_ARC` | `ContentCatalog.java:177` |
| Idle / Run | 4 / 6 | `assets/fullgame/characters/Ina/` |
| Asset group | `Ina` → index 4 | `AssetLibrary.java:165` |
| **Skill name** | The Ancient One | JAR `ldc #124` |
| **Special name** | Spirit of the Oni | JAR `ldc #116`; CSV agrees |
| Portrait | `portraits/ninomae_inanis.png` — mismatched | as above |

---

## 3. Weapon ID Resolution

All five basic attacks resolve against `BASE_WEAPONS` (`ContentCatalog.java:173-177`):

| Id | Defined | `characterOwner` | `maxLevel` | Pattern |
|----|---------|------------------|-----------|---------|
| `trident_thrust` | ✅ L173 | `gura` | 7 | MELEE_ARC |
| `pistol_shot` | ✅ L174 | `ame` | 7 | PROJECTILE |
| `scythe_swing` | ✅ L175 | `calli` | 7 | MELEE_ARC |
| `phoenix_sword` | ✅ L176 | `kiara` | 7 | MELEE_ARC |
| `summon_tentacle` | ✅ L177 | `ina` | 7 | MELEE_ARC |

**Weapon damage is genuinely implemented.** `RunWorld.java:712-713` scales `def.damage + max(0, level-1)`; all eight `PATTERN_*` constants are dispatched in `RunWorld.java:702-871`.

---

## 4. Skill & Special Attack — Implementation Reality

### 4.1 What the source does

| Step | Code | Behavior |
|------|------|----------|
| Offer generated | `RunWorld.java:1180-1181` | `OFFER_SKILL` value 0 |
| Offer labelled | `RunWorld.java:270` | `return character.skill;` (name only) |
| Offer badge | `RunWorld.java:282` | `"SKILL"` |
| Offer selected | `RunWorld.java:1263-1264` | `skillAcquired = true;` — **nothing else** |

### 4.2 What is absent

| Element | Status | Evidence |
|---------|--------|----------|
| `character.special` referenced in gameplay | **ZERO references** | grep `.special` in `RunWorld.java` / `FullGameCanvas.java` → none |
| Skill stat modifiers | none | no code reads `skill` beyond label |
| Skill upgrade levels | none | wiki says 3 upgrades per skill; source has a single boolean |
| Special attack activation | none | no charge, cooldown, or trigger |

**Conclusion**: skills and specials are `DATA_ONLY`. Names are catalog metadata; they produce no gameplay effect.

---

## 5. Wiki Cross-Check (Ver 0.7)

Source: <https://holocure.wiki.gg/wiki/Character> (page revision id 22063)

| Claim | Wiki | HoloJava | Match |
|-------|------|----------|-------|
| Playable roster size | 47 | 0 active / 5 JAR | ❌ |
| Myth default-unlocked set | 5 (Amelia, Gura, Inanis, Kiara, Calliope) | same 5 | ✅ |
| Skills per character | **3** | 1 (`CharacterDef.skill`) | ❌ |
| Skill upgrade cap | 3 | none | ❌ |
| Special requires shop upgrade | yes | no shop system | ❌ |
| Main weapon levels | 7 | 7 (`maxLevel`) | ✅ |
| Character names / specials | matches CSV + JAR | — | ✅ |

Wiki version: **0.7.1731644049 (2024-11-15)** — matches `ContentCatalog.java:11` `WIKI_VERSION`.

---

## 6. Validation Status

`ContentCatalog.validate()` (`ContentCatalog.java:407-412`) checks:
1. `weapon(c.mainWeapon) != null`
2. `c.playable == true`

Both **would pass** for the five JAR-verified entries. The function is currently vacuous because `CHARACTERS.length == 0`.

Note: `CharacterDef` has a 13-arg factory (`ContentCatalog.java:298-300`) with a `fallbackArt` flag that the JAR's 12-arg call did not use. Setting `fallbackArt=true` also sets `gameplayApproximation` (`:305`).

---

## 7. Problems Found

| # | Problem | Severity | Location |
|---|---------|----------|----------|
| P1 | Catalog empty → all consumers break | **BLOCKER** | `WikiCatalogData.java:165-167` |
| P2 | Portrait files named by wiki id, code resolves catalog id | HIGH | `AssetLibrary.java:155` |
| P3 | Skill/Special are inert labels | MEDIUM | `RunWorld.java:1263-1264` |
| P4 | `character.special` stored but never read | MEDIUM | `CharacterDef` field |
| P5 | Only 1 of 3 wiki skills modelled | LOW | `CharacterDef.skill` (single String) |

---

**Status: COMPLETE. No files modified.**
