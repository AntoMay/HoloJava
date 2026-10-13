# HoloJava Rebuild Implementation Roadmap

## Implementation Status (2026-10-13)

The rebuilt runtime now implements the shared core of M0-M4 plus scoped M5/M6 behavior in `src/java-me-fullgame`:

- M0: build layout, resource mapping, catalog validation, and local asset pipeline implemented.
- M1: menus, run-state flow, fixed-tick loop, rendering, pause/resume implemented.
- M2: player, stage enemy pools, scaling, collision, contact damage, and boss encounters implemented.
- M3: weapon patterns, projectiles, drops, EXP, coins, food, and pickup collection implemented.
- M4: weapon/item slots, level-up offers, skills, and five documented collab prerequisites implemented.
- M5: five playable Myth characters with distinct stats, weapons, skills, and specials implemented; other documented characters are data-only.
- M6: stage-specific director, documented 10:00/20:00 boss schedules, hard variants, ring/horde events, and boss AI approximations implemented.
- M7: collab prerequisite logic exists for five recipes; broad weapon evolution, enchants, stamps, and Super systems remain pending.
- M8: static build verification is implemented; device/runtime performance and parity audit remain pending.

See `docs/rebuild/COVERAGE.md` for the evidence-based boundary.

## Milestone Order

M0 - Architecture, build layout, and asset pipeline.
M1 - Core runtime, input, rendering, and run states.
M2 - Player, enemies, spawning, and combat.
M3 - Automatic weapons, projectiles, drops, and pickups.
M4 - EXP, level-up, level offers, and small content data.
M5 - Character skills, special abilities, and richer enemy behavior.
M6 - Stage progression, timed events, bosses, and scaling.
M7 - Weapon evolution, collabs, item catalog, and broad content expansion.
M8 - Performance optimization, device compatibility testing, and parity audit.

Milestone ordering can be adjusted as implementation exposes dependency problems.

## M0 - Architecture, Build Layout, Asset Pipeline

Goal: establish the clean source tree, build scripts, expected JAR/JAD workflow, and resource location contract.

Acceptance criteria:
- Separate audit docs live in `docs/rebuild/`.
- Build commands are documented.
- A new package layout for clean source is chosen.
- The asset path rule is defined.
- No gameplay code is implemented.
- `config/assets.json` or its replacement uses relative paths and is copied only at build time.

Required modules:
- `me.holojava.runtime` (initial lifecycle only)
- Build and asset validation scripts.

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/HoloCure_Wiki

Asset groups:
- Only root assets and folder mapping needed.

Estimated complexity: low.

Risks:
- Overengineering.
- Recreating a framework larger than the game.
- Toolchain drift across developer machines.

Manual test:
- No J2ME gameplay test required; verify clean compilation and packaging.

Exclusions:
- Gameplay content.
- Asset copying from HoloCure beyond described schema.
- Save data.

## M1 - Core Runtime, Input, Rendering, and Run States

Goal: create a runnable logical 320x240 shell with menu, running, paused, level-up placeholder, and game-over state.

Acceptance criteria:
- MIDlet launches in J2ME Loader.
- Logical dimensions and letterboxing work.
- One image displays.
- Input directions produce a movement vector.
- User can enter and leave running, paused, and game-over states.
- Runtime has bounded catch-up.

Required modules:
- `me.holojava.runtime`
- `me.holojava.core.RunState`
- Initial `render` and `render` methods.

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/Character
- https://holocure.wiki.gg/wiki/Enemy (`Enemy` spawning concepts)

Asset groups:
- One player image only.
- Optional background color.

Estimated complexity: low-medium.

Risks:
- Appearance differences due to device resolution.
- GameCanvas key masks differ by device.

Exclusions:
- Full HUD graphics.
- Multiple enemies.
- Audio.

## M2 - Player, Enemies, Combat, and Spawning

Goal: implement simple one-enemy-chases-player combat with bounded pooling and collision.

Acceptance criteria:
- One enemy type spawns and chases.
- Separate player and enemy collision boxes.
- Contact damage cooldown.
- Enemy HP and defeat.
- Spawn cap.
- Player death and Game Over.
- Manual tests pass in J2ME Loader.

Required modules:
- `PlayerState`
- `Enemy`
- `SpawnDirector`
- collision methods.

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/Enemy

Asset groups:
- One player.
- One enemy.
- Optional XP drop fallback.

Estimated complexity: medium.

Risks:
- Nonuniform enemy sprite dimensions.
- Fixed-point timing errors.
- Pool exhaustion and spawn behavior.

Exclusions:
- Skills, special attacks, timed bosses.
- Stage rules.

## M3 - Automatic Weapons, Projectiles, Drops, and Pickups

Goal: implement one weapon with cooldown, projectile pooling, drop spawn, and pickup radius.

Acceptance criteria:
- Weapon cooldown explicit.
- Damage explicit.
- HPEnemy reduces by projectile collision.
- Enemies can drop one or more types of drops.
- Pickups attract based on radius.
- No runtime list allocations.

Required modules:
- `WeaponState`
- `Projectile`
- `DropPickup`
- `PickupCollection`

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/Weapon
- https://holocure.wiki.gg/wiki/Drops

Asset groups:
- One projectile image.
- One XP pickup.
- optional projectile tracking frames.

Estimated complexity: medium.

Risks:
- Multiple projectile-object interactions under framerate.
- Frame-based timing drift.
- Pickup visual size/glyph.

Exclusions:
- Full 94-weapon catalog.
- Collabs.
- Hit animations beyond placeholders.

## M4 - EXP, Level-Up, and Content Data

Goal: implement basic progression and make it data-defined enough to add one alternative character after launch.

Acceptance criteria:
- EXP bar and color tier semantics.
- Level-up pauses simulation.
- Offer up to four arithmetic-defined upgrades.
- Apply selected upgrade once.
- Run timer increases spawn pace modestly.
- Character and weapon metadata is present in resource or constant table.

Required modules:
- `ExperienceState`
- `LevelUpOffer`
- `ContentIdTables`

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/EXP
- https://holocure.wiki.gg/wiki/Level_Up

Asset groups:
- XP frames.
- Digit frames are optional.
- HUD styling is deferred.

Estimated complexity: medium.
Risks:
- `LeveUp` at repeated levels.
- A bad offer schedule making game unbalanced.
- Memory from frame lists.

Exclusions:
- Skill upgrade tree.
- Anvils and persistent shop.
- Super Items.

## M5 - Characters Skills, Special Abilities, and Enhanced Enemies

Goal: introduce one character selection and one special ability plus richer enemies.

Acceptance criteria:
- Character metadata is distinct.
- Weapon knockback or targeting rule added if selected.
- At least one special ability demonstrated.
- Elite or area-effect enemy behavior enabled.
- Melee/ranged/multishot differ where used.
- Test safe state transitions.

Required modules:
- `CharacterDef`
- `SpecialAttackState`
- `EnemyVariant`
- `Knockback`

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/Character
- https://holocure.wiki.gg/wiki/Enemy#Special_enemies

Asset groups:
- Additional character animations.
- One special effect family.

Estimated complexity: high.

Risks:
- Scope escalation.
- Character animation mismatch.
- Date definitions become unwieldy.

Exclusions:
- All 47 characters.
- 37 items.

## M6 - Stage Progression, Timed Events, Bosses, Scaling

Goal: convert one stage into a concrete encounter using documented timer rules without attempting all stage variations.

Acceptance criteria:
- At least one stage enemy composition is represented.
- Boss/miniboss schedule triggers at 2 and 10 minutes.
- Timer scaling formula documented and applied.
- Timed event patterns like ring/wall can be executed.
- Stage-specific spawn cap policies included.

Required modules:
- `StageDef`
- `TimedEventTable`
- `BossSpawnPolicy`
- `EnemyScaling`

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/Stage
- https://holocure.wiki.gg/wiki/Enemy

Asset groups:
- One stage background.
- Enemy families for that encounter.
- Boss assets.

Estimated complexity: high.

Risks:
- Stage data extraction requires much wiki reading.
- Java ME memory with many sprite types.
- "Full parity" misalignment if only one stage launched.

Exclusions:
- All stages.
- Achievements/meta progression.

## M7 - Weapon Evolution, Collabs, Item Catalog, and Content Expansion

Goal: demonstrate both a collab path and item definitions, then expand content primarily through data.

Acceptance criteria:
- Two basic weapons can combine into one collab in one path.
- Weapon anvil enhancement and cap can be represented even if exact values are placeholders.
- Item slots and at least two item modifiers can be added.
- Enum-based data tables remain coherent.
- At least one rendering update by slot.

Required modules:
- `CollabRecipe`
- `Enhancement`
- `ItemDef`
- `SlotInventory`

Relevant wiki sources:
- https://holocure.wiki.gg/wiki/Collab
- https://holocure.wiki.gg/wiki/Weapon
- https://holocure.wiki.gg/wiki/Item
- https://holocure.wiki.gg/wiki/Upgrade

Asset groups:
- Weapon icons.
- Item graphics.
- Collab sprites.

Estimated complexity: high.

Risks:
- Exponential complexity.
- Character-specific weapon enhancement.
- Critical hit formula remains unmade.

Exclusions:
- 94 weapons.
- 37 items.
- Super items and super collabs.

## M8 - Performance Optimization, Compatibility, and Parity Audit

Goal: measure performance, validate device compatibility, create parity matrix.

Acceptance criteria:
- Bounds and allocations profiled.
- Keep or reduce critical images.
- Nokia/other device gaps documented.
- Existing audit matrix separates implemented, documented-but-untested, deferred.
- Parity claims based on evidence.

Required modules:
- Performance Notes
- Compatibility Notes
- Audit Matrix.

Relevant wiki sources:
- All already cited.

Asset groups:
- Frozen selected asset set.

Estimated complexity: medium-high.

Risks:
- Java ME heap variation.
- Audio support gaps.

Exclusions:
- Claiming parity.
- App store/public redistribution without license review.

## Dependency Summary

- M1 depends on M0.
- M2 depends on M1.
- M3 depends on M2.
- M4 depends on M1 and M3.
- M5 depends on M2 and M4.
- M6 depends on M2, M4, and M5.
- M7 depends on M3 and M5.
- M8 depends on all previous evidence.

Any movement of milestones should preserve testability and manual J2ME Loader verification.
