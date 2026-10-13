# HoloJava wiki content data (`data/wiki/`)

Evidence-based content data extracted from the **HoloCure Wiki on wiki.gg** for later Java
catalog integration. Every row in every CSV carries a `source_url` pointing at the page the
values were read from, and a `coverage_note` recording what is complete, what was deliberately
omitted, and what the wiki leaves undocumented.

## Files

| File | Rows (excl. header) | Contents |
| --- | --- | --- |
| `characters.csv` | 47 | Playable characters: base HP/ATK/SPD/CRT/SIZE, main weapon, special attack |
| `weapons.csv` | 94 | Every weapon: basic (17), main (47), collab (25), super collab (5) |
| `items.csv` | 54 | Base items (37) plus their documented super variants (17) |
| `stages.csv` | 10 | Stage 1-5, Stage 1H-4H, Time Stage 1 |
| `bosses.csv` | 15 | Main bosses per stage/variant, with scheduled spawn times in seconds |
| `modes.csv` | 4 | Stage Mode, Endless Mode, Hardcore, Time Mode |
| `collabs.csv` | 30 | Collab recipes (25) and Super Collab recipes (5) |
| `README.md` | – | This file |

## How the data was produced

1. **Read-only HTTP fetches** against `holocure.wiki.gg` only. No game files, no other
   repositories, no downloads, no images copied, no Java written.
2. Where possible the wiki's own **MediaWiki API and Cargo tables** were used, because those are
   the machine-readable form the wiki itself renders its tables from:
   - `https://holocure.wiki.gg/api.php?action=cargotables` — discovered the 10 Cargo tables
     (`Characters`, `Weapons`, `Items`, `Stages`, `Skills`, `Specials`, `Stamps`, `Patches`,
     `PatchSections`, `TypeIndex`).
   - `...&action=cargoquery&tables=Characters&fields=name,mainWeapon,special,hp,atk,spd,crt,size`
     — the source of every numeric value in `characters.csv`.
   - `...&tables=Weapons&fields=name,weaponType,weaponCat,weight,user,unlockedBy,collabs,components,description`
     — the source of every value in `weapons.csv` and the `weapon_a`/`weapon_b` columns of
     `collabs.csv`.
   - `...&tables=Stages` and `https://holocure.wiki.gg/wiki/Special:CargoTables/Items` — the
     source of `stages.csv` and `items.csv`.
3. Prose pages were fetched as raw wikitext (`/wiki/<Page>?action=raw`) for `Stage`, `Enemy`,
   `Collab`, `Super Collab`, `Weapon`, `Item`, `Drop`, `EXP`, `Level Up`, `Time Stage 1`,
   `Character`, and `Character/stats`.
4. Fields were **copied, not inferred**. Only these mechanical transformations were applied:
   - `Character/stats` renders ATK/SPD as `"1.20x"` → stored as `120` in
     `atk_multiplier_percent` / `spd_multiplier_percent` (the `x` suffix stripped, value
     converted to a percentage number). Crit was already `"10%"` → `10`.
   - `Enemy` boss table times `"10:00"` / `"20:00"` → `600` / `1200` in
     `scheduled_time_seconds`.
   - `id` slugs are derived from the documented display names (lowercase, spaces/`&`/`'`/
     `!` replaced with `_`).
   - `description` keeps only the **level 1 / base** text. The wiki stores per-level upgrade
     text in the same field separated by `;\n`; those later levels were dropped to keep the CSV
     usable. This is called out in every affected row.
   - Inline wiki markup (`<span class="gtgreen">…</span>` colour spans, `[[File:…]]`,
     `[[link|text]]`) was stripped down to plain text.

## Known limitations

- **Game version coverage.** The wiki currently documents **Update 0.7**
  (version 0.7.1731644049, 2024-11-15) as the latest content. Anything added after that is not
  represented here.
- **No numeric combat stats.** `characters.csv` carries base stats only. It does **not** include
  G. Rank duplicate bonuses (+1% HP/ATK/SPD per duplicate, capped at 20% at G. Rank 21), Shop
  Upgrade bonuses, skills, or Special Attack damage. The wiki itself notes that the character
  select screen shows pre-bonus values.
- **`description` is level-1 only** for weapons and items, as described above. Full per-level
  stacks live in the wiki's Cargo tables (`damageStack`, `healStack`, `attackTimeStack`,
  `hitLimitStack`, …) and on `Weapon/stats`; they were not copied.
- **`weight` is genuinely blank in several places**, not lost in extraction:
  - All 30 collab/super-collab weapons — they are crafted at a Golden Anvil, never offered by
    Level Up, and the wiki's Cargo `weight` field is empty.
  - Items `Corporation Pin`, `Promise Tiara`, `Raven Feather` — empty in the source table.
  These are marked `UNKNOWN`/not-applicable in `coverage_note`.
- **Super items have no wiki pages.** Each super item exists only as a `descriptionSuper` field
  and an icon file (`Super Sake Icon.png`, …) on its base item's page. The `name` and `id` of
  the 17 `type=super` rows were derived from that documented file/prose naming, and their
  `max_level` mirrors the base item's max level because the wiki states super items count as
  max-level items. Every such row flags this.
- **`Hi-Spec Pistol` is dual-typed.** The wiki's `weaponType` for it is literally
  `multishot;ranged`; the `type` column therefore holds both values instead of a single one.
- **Boss member groups were not split.** `Halloween Myth` (5 sprites) and `Area 15` (3 sprites)
  are documented as groups whose members are only named in image file names, so each is one row.
- **Stage 5 Hard does not exist on the wiki**, yet the Enemy page reserves a `TBD` cell for a
  Stage 5 20:00 Hard boss. That gap is preserved as an explicit `TBD` row in `bosses.csv`
  rather than being filled in.
- **`Stage 4 (Hard)` and `Stage 5` unlock conditions are `UNKNOWN`.** The wiki Stage page lists
  `N/A` in those cells.
- **Mini-bosses are not enumerated.** The Enemy page states mini-bosses spawn every 2 minutes
  and main bosses every 10 minutes, but does not list mini-boss names or times per stage, so
  they have no rows in `bosses.csv`.
- **`background_candidates` holds filenames, not assets.** Only the Stage Infobox `image1`
  value as documented on the wiki (e.g. `GrassyPlains.png`). No image was downloaded, copied,
  or embedded.
- **No standalone `Stage Mode` / `Time Mode` / `Endless Mode` pages exist**; those rows in
  `modes.csv` are sourced from the `Stage` article's "Game modes" section plus
  `Time Stage 1 - Concert Stage`.
- **Enemy composition, spawn tables, damage/scaling formulas, Drop/EXP/Level Up detail** were
  read and used to sanity-check the CSVs but are not separate files, because no output schema
  was requested for them. Key facts already folded into `coverage_note`:
  enemy drops are EXP 1/1, Food 1/200, HoloCoins 1/90, Anvil 1/1301, EXP Magnet 1/3000; level 1
  to 2 requires 79 EXP with `reqNextLevel = round((4(L+1))^2.1) - round((4L)^2.1)`; StatUp
  weights are Max HP 2, ATK 3, SPD 4, Crit 3, Pick Up 4, Haste 2; all main weapons have weight 3.
- **CSV dialect.** UTF-8, LF line endings, every field double-quoted, commas and quotes inside
  fields escaped by doubling. No embedded newlines.

## Pages consulted

- https://holocure.wiki.gg/wiki/Character  (redirects from Characters)
- https://holocure.wiki.gg/wiki/Character/stats
- https://holocure.wiki.gg/wiki/Weapon
- https://holocure.wiki.gg/wiki/Item
- https://holocure.wiki.gg/wiki/Enemy
- https://holocure.wiki.gg/wiki/Stage
- https://holocure.wiki.gg/wiki/Stage_1_-_Grassy_Plains … `Stage_5_-_Fantasy_Island`,
  `Stage_1_(Hard)_-_Grassy_Plains_(Night)` … `Stage_4_(Hard)_-_Gelora_Bung_Yagoo_(Night)`
- https://holocure.wiki.gg/wiki/Time_Stage_1_-_Concert_Stage
- https://holocure.wiki.gg/wiki/Collab
- https://holocure.wiki.gg/wiki/Super_Collab
- https://holocure.wiki.gg/wiki/Drop
- https://holocure.wiki.gg/wiki/EXP
- https://holocure.wiki.gg/wiki/Level_Up
- https://holocure.wiki.gg/wiki/Special:CargoTables/{Weapons,Items,Stages}
- https://holocure.wiki.gg/api.php (cargotables, cargoquery)

## Intended use

These files are **input data** for the HoloJava catalog. They are not code and are not compiled.
When wiring them into Java, treat `id` as the join key, keep `source_url` for traceability, and
respect the blank fields — a blank means "not documented upstream", not "zero" and not "none".