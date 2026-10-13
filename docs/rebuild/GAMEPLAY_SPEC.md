# HoloJava Rebuild Gameplay Specification

Sources: HoloCure wiki pages cited inline below. This document records documented mechanics, missing details, required Java ME approximations, and un-researched items. It is not a request to implement all listed content in the first milestone.

## Classification Key

1. Documented and sufficiently specified.
2. Documented but missing implementation details.
3. Inferred approximation required for Java ME.
4. Not yet researched.

## Game States and Run Lifecycle

- Character and stage selected beforehand (documented feature, page list).
- Runtime states implied by wiki and prior project design: menu, running, paused, level-up, game-over.
- Survival run timing is tracked in hours, minutes, and seconds.
- Timed events are triggered by current run time.
- Bosses and minibosses spawn at regular intervals.
- Classification: 2 for exact state machine transitions; 1 for timer-based stage events.

Sources:
- https://holocure.wiki.gg/wiki/Enemy
- https://holocure.wiki.gg/wiki/Stage
- Existing internal design: `docs/GAMEPLAY_DESIGN.md`

## Stats and Characters

- Character selection lists 47 playable characters.
- All characters have a unique starting weapon.
- Each has three skills and one unique Special Attack.
- Base stats include HP, ATK, SPD, CRT, Pickup Radius, and Haste.
- Alternate outfits exist for many characters.
- G.Rank can grant additional stats up to 20% but only after duplicate gacha pulls.
- Skills are acquired through level-up; the wiki says each can be upgraded up to 3 times.
- Special Attacks require a prior shop upgrade before use.

Classification:
- 1 for statutory and character list shape.
- 2 for exact base values, formula modifiers, and interaction precedence.
- 4 for all 47 character-specific behavioral data extraction.

Source:
- https://holocure.wiki.gg/wiki/Character
- https://holocure.wiki.gg/wiki/Character (stat and G.Rank sections)

## Enemy Behavior and Spawning

Documented:

- Enemies move toward the player at designated speed.
- Contact damage uses enemy ATK and occurs when the player collision box overlaps with the enemy.
- Enemies attack again when attack cooldown is over.
- Defeated enemies increase the enemy counter.
- Some enemies have a set maximum lifetime and expire without dropping or counting.
- Spawn pool changes throughout the run, with random weighted selection.
- Spawn rate, spawn cooldown, and enemy limit change over time.
- Timed events forcibly spawn enemies in patterns.
- Mini-bosses spawn every 2 minutes; main bosses every 10 minutes.
- Bosses and some mini-bosses use larger sprites, higher stats, no instant knockout, and variable size/contents.
- Some enemies can heal, debuff, charge, shoot, or explode.

### Exact Scaling Formulas

For a, maximum(0, minuteTimer - 23) + 37 * hourTimer.

For b, minutesPast30 + 60 * hourPast1; b is 0 before 30:00.

ATK = (baseATK + 2a)^(1 + b/25)

SPD = (baseSPD + 0.12a)^(1 + b/25), maximum 300

HP = (baseHP + baseHP * 0.05 * a)^(1 + b/50)

Classification:
- Existing formula: 1 for exact enemy scaling algebra.
- AI details: 2.
- Spawn event tables: 1 if fetched from stage enemy pages; 4 so far unless each stage page is audited further.
- Boss stats formulas for bomber bosses: 1 where provided.
- Lifetime values, spawn weights, and per-stage enemy bases: 4 until extracted.

Sources:
- https://holocure.wiki.gg/wiki/Enemy
- Listed enemy data on stage pages referenced by that page.

## Combat and Damage

Documented:

- Weapons deal damage upon hitting target.
- Character's base and buffs affect damage.
- Critical hits exist as a CRT stat.
- Knockback has speed and duration; cooldown/resistance behavior is exact:

  - Each knockback hit increases resistance by 10%.
  - Maximum 90%.
  - Resets after 10 seconds without knockback hit.
- Effect that ignores resistance exists for some special attacks.
- Bosses can have varying immunity to knockback.

Classification:
- Damage interaction: 2 because wiki says attacker stats affect damage but does not provide one universal closed formula in the audited pages.
- Knockback calculation: 1 for documented values and resistance.
- Collision and hit boxes: 2 for bosses and attack-area dimensions.

Sources:
- https://holocure.wiki.gg/wiki/Enemy#Knockback
- https://holocure.wiki.gg/wiki/Damage (not fully extracted yet)

## Drops and Pickups

Documented drops:

- EXP: every defeated enemy, with amount tied to enemy's EXP stat; boss enemies drop large amounts.
- Food: 1/200 base chance.
- 10 HoloCoins: 1/90 base chance.
- Anvil: 1/1301 base chance.
- EXP Magnet: 1/3000 base chance; all EXP and HoloCoins in large area vacuumed.
- Certain worthless enemies and Silver/Golden YAGOOs have specialized drop behavior.
- EXP merging occurs when many drops of one type is close.
- Summons can pick up EXP for the player if applicable.

Classification:
- Overall drop item types and much of chance data: 1.
- Timed unique/YAGOO behavior: stage-dependent and partially documented; 2.
- Vacuum animation/magnet behavior, merging animation, and radius: 2.
- Coin total scaling and farming items: undocumented implementation details.

Source:
- https://holocure.wiki.gg/wiki/Drop
- https://holocure.wiki.gg/wiki/EXP
- https://holocure.wiki.gg/wiki/Enemy#Drops

## EXP and Level-Up

Documented:

- EXP comes from graded visual color tiers:
  - Blue: 1 to 10
  - Green: 11 to 19
  - Yellow: 20 to 49
  - Orange: 50 to 99
  - Purple: 100 to 199
  - Red: 200+
- EXP drops merge.
- Collection requires drops within player pickup radius.
- Certain effects increase pickup range and gain.
- Collect enough EXP and player levels up.
- EXP magnet appears with a very low chance.

Classification:
- 1 for value ranges and magnet concept.
- 2 for exact ratio between enemy stat and EXP, threshold progression, margin levels, offer weighting, and upgrade offers.
- 3 for merging, pickup cadence, and movement behavior.

Source:
- https://holocure.wiki.gg/wiki/EXP
- https://holocure.wiki.gg/wiki/Level_Up

## Weapons

Documented:

- 94 total weapons as of the audited wiki version.
- 17 basic weapons, 47 main weapons, 25 collabs.
- 6 weapon slots, but main weapon always occupies one.
- Weapons have types: Melee, Multishot, Ranged.
- Basic/main weapons max level 7.
- Awakened main weapons gain new sprites and properties at max level.
- Collab upgrades need two max level weapons to create a new special weapon and open a slot.
- Anvils can enhance weapons beyond max level with increasing HoloCoin cost and decreasing 10% success per attempt.
- Upgrades at levels offer choices from random rewards.
- Weapon stats include weight on offering.

Classification:
- 1 for weapon catalog shape, slot count, max level, collab and anvil mechanics.
- 2 for per-weapon cooldown, damage scaling, sprite schema, and implementation details.
- 4 for full data extraction and behavior implementations for every weapon.

Source:
- https://holocure.wiki.gg/wiki/Weapon
- https://holocure.wiki.gg/wiki/Collab

## Items

Documented:

- 6 item slots.
- 37 items and super item variants (as of provided list).
- Item categories: Healing, Stat, Utility.
- Items have max level, weight, description, unlock method.
- holozon boxes have small chance to yield super items.
- Super items usually remove penalties / improve source.
- Items can be upgraded via level-up, anvil, or Holozon box.
- Super Collab exists.
- Item limit increases can unlock more capacity.

Classification:
- 1 for inventory and categorization structure.
- 4 for full item behavior implementation and stat application code.

Source:
- https://holocure.wiki.gg/wiki/Item
- https://holocure.wiki.gg/wiki/Upgrade

## Stage and Timed Events

Documented in the Enemy page and stage links:

- Normal stages and corresponding Hardmode stages.
- Time Stage.
- Enemies vary per stage.
- Boss/miniboss timing.
- Spawn patterns: cluster, horde, ring, wall, stampede, airdrop, fastball, ambush.
- Special enemies: bombers, shooters, chargers, area-effect, harmless, Yagoos.
- Scaling formulas.
- Stage-specific event timing provided on stage Enemy pages.

Classification:
- 1 for broad stage shape and patterns listed.
- 4 for detailed event tables, enemy pool weights, map layouts, and porting requirements.

Source:
- https://holocure.wiki.gg/wiki/Stage
- https://holocure.wiki.gg/wiki/Enemy

## Character Skills and Special Attacks

Each character has:

- A unique main weapon
- 3 skills max 3 upgrades each
- 1 special attack that requires a shop upgrade
- Character-specific stats and outfits
- Fandom system that can pre-acquire skills

Classification:
- 1 for structure.
- 4 for data and exact effects; the game cannot ship accurate parity without extracting and validating each skill.

Source: https://holocure.wiki.gg/wiki/Character

## Movement and Inputs

The wiki documents states, stats, and behavior conceptually:

- Character movement uses SPD.
- Melee weapons follow character position.
- Ranged projectiles are independent.
- Multishot can use projectile gain.
- Drop collection by movement.

The exact joystick/facing rules for Java ME input, aiming when not moving, gamepad versus keyboard, and movement clamp handling are 2/3 across the wiki because controls are part platform dependent.

## HUD, Pause, Game-Over

Documented and inferred from wiki UX:

- EXP meter and HP display are part of described HUD.
- Boss minions and counters.
- Pause and menu behaviors likely expose run stats and equipment.

Classification: 2. The exact HUD layouts and control keys are not fully specified for Java ME.

## Important Implementation Note

The game architecture should separate documented, well-specified mechanics from content with 4 status. Early milestones should only implement well-documented, testable mechanics. Full wiki parity is a later audit task, not an initial build objective.
