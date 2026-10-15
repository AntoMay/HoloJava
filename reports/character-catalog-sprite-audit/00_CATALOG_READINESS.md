# HoloJava — Character Catalog Sprite & Skill Audit (Tahap 3D)

## Evidence Base
Local assets tree, `unzip -l` on reference JAR, `javap -c -p` on extracted `ContentCatalog.class` / `SaveStore.class`, `ContentCatalog.java`, `AssetLibrary.java`, `data/wiki/*.csv`, `docs/rebuild/`.

| Check | Result |
|-------|--------|
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |

---

## 1. Asset Inventory (FACT)

### 1.1 Animated Sprites — 5 of 47

| Asset Group | Path | Idle frames | Run frames | Portrait |
|-------------|------|-------------|------------|----------|
| `Gura` | `assets/fullgame/characters/Gura/` | 3 (`spr_Gura_Idle_0..2`) | 6 (`spr_Gura_Run_0..5`) | `portrait.png` |
| `Ame` | `assets/fullgame/characters/Ame/` | 3 (`_idle_0..2`) | 6 (`_run_0..5`) | `portrait.png` |
| `Calli` | `assets/fullgame/characters/Calli/` | 4 (`_idle_0..3`) | 6 (`_run_0..5`) | `portrait.png` |
| `Kiara` | `assets/fullgame/characters/Kiara/` | 4 (`_idle_0..3`) | 6 (`_run_0..5`) | `portrait.png` |
| `Ina` | `assets/fullgame/characters/Ina/` | 4 (`_idle_0..3`) | 6 (`_run_0..5`) | `portrait.png` |

Reference JAR contains the same 5 groups (69 entries under `characters/`) — **identical to the repo working tree**. No extra sprites in the JAR.

### 1.2 Portraits — 47 files, NOT in JAR

`assets/fullgame/portraits/` holds 47 PNGs named by **CSV id** (`gawr_gura.png`, `amelia_watson.png`, …).
Reference JAR contains **0** entries under `assets/fullgame/portraits/`.

### 1.3 Asset Pipeline Is Hardcoded for Exactly 5

`AssetLibrary.java:160-167` — `groupIndex()` maps only `Gura→0, Ame→1, Calli→2, Kiara→3, Ina→4`, returns `-1` otherwise.
`AssetLibrary.java:143` — fallback is `index % 5`.
`AssetLibrary.java:151-157` — `characterPortrait()` resolves `/assets/fullgame/portraits/<CHARACTERS[index].id>.png`.

**Consequence**: with 5-character ids (`gura`), the portrait path becomes `portraits/gura.png`, but the file on disk is `gawr_gura.png` → **path mismatch, portrait load fails** (silently; `image()` is failure-tolerant).

---

## 2. Reference JAR Character Roster (Authoritative, from bytecode)

`javap -c -p ContentCatalog.class` static initializer: **`anewarray` of size 5** — the JAR shipped a 5-character catalog, matching the 5 sprite groups exactly.

| Idx | Id | Name | HP | ATK | SPD | Crit | Size | Main Weapon | Special Attack | Skill | Playable | AssetGroup |
|-----|-----|------|----|-----|-----|------|------|-------------|----------------|-------|----------|------------|
| 0 | `gura` | Gawr Gura | 65 | 110 | 140 | 5 | 1 | `trident_thrust` | **Shark Call** | **Shark Bite** | `true` | `Gura` |
| 1 | `ame` | Amelia Watson | 75 | 130 | 135 | 10 | 3 | `pistol_shot` | **Slow Time** | **Hunter's Instinct** | `true` | `Ame` |
| 2 | `calli` | Mori Calliope | 70 | 115 | 130 | 10 | 4 | `scythe_swing` | **The Reaper** | **Undying** | `true` | `Calli` |
| 3 | `kiara` | Takanashi Kiara | 90 | 100 | 140 | 5 | 3 | `phoenix_sword` | **Phoenix Fire** | **Second Flame** | `true` | `Kiara` |
| 4 | `ina` | Ninomae Inanis | 75 | 90 | 150 | 1 | 1 | `summon_tentacle` | **Spirit of the Oni** | **The Ancient One** | `true` | `Ina` |

**Cross-checks (all consistent):**
- Weapon ids match `BASE_WEAPONS` entries with `characterOwner` set (`ContentCatalog.java:173-177`).
- Stats match `characters.csv` rows for these five (e.g. Gura 65/110/140/5/1; Ame 75/130/135/10/3).
- Special-attack names match CSV `special_attack` column (Gura "Shark Call", Ame "Slow Time").
- `AssetLibrary.groupIndex()` index order matches this exact order.

---

## 3. Current Source vs JAR — Factory Signature

| Aspect | Reference JAR | Current source | Note |
|--------|---------------|----------------|------|
| Catalog size | 5 (hardcoded array) | 0 (`buildCharacters()` stub) | **Root cause of the blocker** |
| `character()` arity | 12 args | 13 args (+ `fallbackArt`) | Source added `fallbackArt` / `gameplayApproximation` |
| `skill` field | present (1 string) | present (`CharacterDef.skill`) | Same |
| Collab skill system | — | not in `CharacterDef` | See §5 |

---

## 4. Skill & Combo Data Availability

| Data | Location | Coverage | Confidence |
|------|----------|----------|------------|
| Special Attack (all 47) | `data/wiki/characters.csv` col 4 `special_attack` | 47/47 | ✅ Source (wiki-derived) |
| Skill (character skill) | **Only reference JAR bytecode** | 5/5 | ✅ Bytecode-verified |
| 3-upgrade skill levels | `docs/rebuild/GAMEPLAY_SPEC.md:231` | conceptual only | ⚠️ Documented, not implemented |
| Skill effects / exact behavior | — | **0** | ❌ Absent |
| Combo / fused skills | — | **0** | ❌ Absent |

`GAMEPLAY_SPEC.md:238` states the game "cannot ship accurate parity without extracting and validating each skill."

**No skill-effect data exists in the repository for any character.** Only names are known (5 characters, from bytecode).

---

## 5. `validate()` Constraints on Catalog Entries

`ContentCatalog.java:407-412`:
```java
for (int i = 0; i < CHARACTERS.length; i++) {
    CharacterDef c = CHARACTERS[i];
    if (weapon(c.mainWeapon) == null) return "character:" + c.id + ":main_weapon_missing";
    if (!c.playable) return "character:" + c.id + ":incomplete_definition_visible";
}
```

Both conditions pass for the 5 JAR-verified entries. They would **fail** for 42 of 47 CSV characters (display-name weapons have no id — see `07_PREREQUISITE_AUDIT.md` §1 B3).

---

## 6. Per-Character Readiness Table

| # | Character | Id | Sprite | Basic Attack | Skill | Combo | Unknown | Status |
|---|-----------|----|--------|--------------|-------|-------|---------|--------|
| 0 | Gawr Gura | `gura` | ✅ `characters/Gura/` idle 3 + run 6 | ✅ `trident_thrust` | ✅ Shark Bite | ❌ | — | **VALID** |
| 1 | Amelia Watson | `ame` | ✅ `characters/Ame/` idle 3 + run 6 | ✅ `pistol_shot` | ✅ Hunter's Instinct | ❌ | — | **VALID** |
| 2 | Mori Calliope | `calli` | ✅ `characters/Calli/` idle 4 + run 6 | ✅ `scythe_swing` | ✅ Undying | ❌ | — | **VALID** |
| 3 | Takanashi Kiara | `kiara` | ✅ `characters/Kiara/` idle 4 + run 6 | ✅ `phoenix_sword` | ✅ Second Flame | ❌ | — | **VALID** |
| 4 | Ninomae Inanis | `ina` | ✅ `characters/Ina/` idle 4 + run 6 | ✅ `summon_tentacle` | ✅ The Ancient One | ❌ | — | **VALID** |
| 5-46 | 42 others (Airani, Akai, Aki, …) | CSV ids | ⚠️ portrait only; **no animation** | ❌ no id in `BASE_WEAPONS` | ❌ | ❌ | sprite, weapon id, skill, effects | **UNRESOLVED** |

---

## 7. Findings

| # | Finding | Classification |
|---|---------|----------------|
| F1 | Only **5 characters** have animated sprites; repo and JAR agree exactly | FACT (assets + JAR) |
| F2 | Reference JAR shipped a **5-entry** `CHARACTERS` array | FACT (bytecode `anewarray` size 5) |
| F3 | All 5 entries have **special + skill names**, absent from every source file | FACT (bytecode) |
| F4 | Portrait filenames use **CSV ids** (`gawr_gura.png`) but `AssetLibrary` resolves **catalog ids** (`gura.png`) | FACT (source, path mismatch) |
| F5 | `AssetLibrary.groupIndex()` + `index % 5` hardcode a 5-character world | FACT (source) |
| F6 | Reference JAR ships **no** `portraits/` directory | FACT (`unzip -l`) |
| F7 | No skill **effects**, upgrade levels, or combo data for any character | FACT (repo search) |
| F8 | CSV roster of 47 is a **documentation artifact**, not an implementation target | INFERENCE (no sprites, no weapon ids, no skills) |

---

## 8. Recommendation

Build the non-empty catalog from the **5 bytecode-verified entries**, not from the 47-row CSV.

This satisfies every hard constraint simultaneously:
- `CHARACTERS.length == 5` → `SaveStore` bounds become usable
- Every `mainWeapon` resolves in `BASE_WEAPONS` → `validate()` passes
- Every `assetGroup` resolves in `AssetLibrary.groupIndex()` → sprites load
- `playable = true` → `validate()` passes
- Stats and skill names traceable to JAR bytecode, not invention

**Do not** populate 47 entries from CSV — 42 would fail `validate()` and have no animation.

**Remaining defect to fix separately**: portrait path mismatch (F4). Either rename the five portraits to catalog ids, or add an id→CSV-id mapping. Out of scope for this audit.

---

**Status: READY FOR REVIEW.** No code, assets, build, or commit modified.
