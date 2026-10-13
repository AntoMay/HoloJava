# HoloJava Known Gaps

## Character Roster
- 38 of 47 documented characters lack verified local animation snapshots in this build
- Character stats, starting weapons, and skills need full validation against wiki data
- 47 documented characters in `data/wiki/characters.csv`; only 5 have verified art in this build

## Weapon Catalog
- 84 of 94 documented weapons beyond the implemented 20 runtime definitions
- Weapon categories (melee, multishot, ranged), patterns (arc, projectile, orbit, boomerang, AOE, homing, piercing, wall), and behaviors need full definition
- 5 main weapons + 10 basic weapons + 5 collab prerequisites currently implemented

## Item Catalog
- 43 of 54 item rows including super items not yet defined
- 11 data-defined items currently in the runtime catalog
- Item effects (healing, stats, utility, super) need full specification

## Collab/Super-Collab Recipes
- 25 of 30 documented collab/super-collab recipes beyond the 30 now-generated from CSV
- Super collab item prerequisites (e.g., Hope Soda, Sake, Gorilla's Paw) need runtime handling
- Golden Hammer drop chance and level requirements (50+, 3 collabs) not yet implemented

## Gameplay Systems
- Full per-stage timed-event tables and mini-boss rotation not yet data-driven
- Anvil enhancement success/cost UI, enchantments, stamps, and G. Rank
- Holozon boxes, super items, food preparation, Holo House meta-progression
- Save data for full meta-progression (only stage completion and Hardcore setting currently persisted)
- Level-up offer probability, weighting, ban rules, and duplicate offer behavior

## Audio and Visual
- No reliable local MIDP playback evidence; audio support platform-dependent
- Full-resolution original stage parallax maps not achievable within Java ME heap constraints
- Original YYC gameplay source and exact native formulas unavailable

## API and Compliance
- CLDC 1.1 / MIDP 2.0 constraints may limit certain gameplay features
- No Java SE-only APIs, streams, lambdas, or reflection used in runtime code
- Bounded pools and allocation-conscious simulation implemented
AUDEOF
cat /home/$(whoami)/Projects/HoloJava/docs/rebuild/KNOWN_GAPS.md | head -5
