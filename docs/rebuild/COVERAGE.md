# Full-Game Coverage Report

Evidence date: 2026-10-13. Wiki source: current wiki.gg tables recorded in `data/wiki/`. Local asset evidence: `reports/fullgame_asset_manifest.csv`.

## Implemented in the rebuilt runtime

| System | Coverage | Evidence |
|---|---|---|
| Main menu | Implemented | `FullGameCanvas` menu state with Play, Modes, Characters, Stages, Settings, Exit |
| Mode catalog | Implemented | Stage, Endless, Time; mode objectives and restrictions encoded in `ContentCatalog` |
| Stage catalog | Implemented as data | 5 regular, 4 hard, 1 Time stage; stage-specific enemy pools, tiles, boss schedules, unlock flags |
| Character selection | Implemented for 5 verified default Myth characters | Gura, Amelia Watson, Mori Calliope, Takanashi Kiara, Ninomae Inanis |
| Character roster reconciliation | Implemented as documentation/data | 47 documented rows in `data/wiki/characters.csv`; 42 lack a verified local playable animation snapshot in this build |
| Run preparation | Implemented | Character/mode/stage confirmation screen |
| Player movement and animation | Implemented | Bounded fixed-tick movement and local idle/run frames |
| Enemy director | Implemented with approximations | Weighted pool selection, edge spawn, cap, scaling, ring/horde events |
| Enemy behaviors | Implemented with approximations | Chaser, shooter, charger, bomber, healer |
| Boss director | Implemented with approximations | Explicit 10:00/20:00 encounter state, boss HP bar, separate boss schedule |
| Weapons | Implemented subset | 5 main weapons + 10 basic weapons + 5 documented collab prerequisites |
| Weapon patterns | Implemented | Melee arc, projectile, orbit, boomerang, AOE, homing, piercing, wall |
| Items | Implemented subset | 11 data-defined items and stat-effect application |
| Skills | Implemented subset | One active skill per playable character, cooldown and level-up acquisition |
| Special attacks | Implemented subset | One special per playable character, one-use activation |
| EXP | Implemented | Documented tier value handling, pickup radius, merging, exact level threshold formula |
| Level-up | Implemented with approximations | Four weighted offers, stat ups, item/weapon/skill offers, reroll/hold metadata options |
| Collabs | Implemented subset | Five documented recipes only when both max-level prerequisites exist |
| Persistence | Implemented optionally | RMS completion bitset and Hardcore setting; gameplay does not depend on it |
| Pause/resume | Implemented | MIDlet lifecycle and FIRE pause/resume |
| Game Over/results/replay | Implemented | Results screen with replay and menu return |
| Audio | Not enabled | No reliable local MIDP playback evidence; settings exposes silent fallback |

## Implemented with explicit approximation

- Enemy base HP/ATK/SPD/EXP values use tuned placeholder values because native GameMaker tables are unavailable.
- Stage background art is represented by local tiles or normalized fallbacks, not full original parallax maps.
- Boss health, damage, rewards, and sprite choices for bosses without verified local art are tuned approximations.
- Special/skill effects are simplified Java ME equivalents of documented character roles.
- Critical multiplier, pickup motion, and some damage/knockback details are platform approximations.
- Hard variants use documented boss names and timings; several boss sprite sets are stand-ins.
- Time Mode uses the documented 4,000-kill objective, but does not reproduce every standardized shop stat or mini-boss rotation.
- Reroll and Hold are represented; Eliminate is not yet exposed in the runtime menu.

## Defined but not yet integrated

- 42 of 47 documented characters.
- 84 of 94 documented weapons beyond the implemented catalog.
- 43 of 54 documented item rows including super items.
- 25 of 30 documented collab/super-collab recipes beyond the five implemented recipes.
- Full per-stage timed-event tables and mini-boss rotation.
- Anvil enhancement success/cost UI, enchantments, stamps, and G. Rank.
- Holozon boxes, super items, food preparation, support items, shop/gacha/Holo House meta-progression.
- Save data for full meta-progression; only stage completion and Hardcore are persisted.

## Unavailable or intentionally unsupported

- Original YYC gameplay source and exact native formulas.
- Complete HoloCure parity claims.
- Guaranteed audio playback across J2ME Loader and Nokia devices.
- Full-resolution original stage parallax maps within the verified Java ME heap.
- Wiki content beyond the current captured Update 0.7 tables.

## Coverage summary

- Playable characters: 5 / 47 documented, with 5 verified local animation sets.
- Weapons: 20 / 94 documented runtime definitions, plus 5 prerequisite-validated collab recipes.
- Items: 11 / 37 base documented items in the runtime catalog.
- Enemy families: 13 local families / many wiki enemy entries; exact wiki denominator is not yet exported.
- Boss encounters: 14 runtime definitions covering the main documented 10:00/20:00 schedule; several use fallback art.
- Stages: 10 / 10 captured stage rows, with stage behavior data-driven.
- Modes: 3 gameplay modes plus the documented Hardcore modifier.

This report intentionally distinguishes implemented behavior from approximations and unavailable content.
