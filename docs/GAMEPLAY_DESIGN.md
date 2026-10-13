# Gameplay Design

Source of truth: HoloJava's independently designed gameplay, separate from verified original HoloCure behavior.

## Game Summary

HoloJava is a 2D survival roguelite vertical-slice game. The player controls one character in a fixed logical 320×240 arena, avoids or attacks enemies, collects XP, levels up, and survives an increasingly difficult encounter until defeat ends the run.

All mechanics in this document are original design decisions unless explicitly marked as verified original behavior in `GAME_MECHANICS.md`.

## Required State Model

Core states:

- `BOOT`
- `MENU`
- `RUNNING`
- `LEVEL_UP`
- `PAUSED`
- `GAME_OVER`

State changes must be deterministic and testable without rendering.

## Entity Model

### Player

Fields:

- Position.
- Velocity.
- Facing or last movement vector.
- Health.
- XP.
- Level.
- Active weapon state.
- Invulnerability timer.
- Upgrade selections.

Independent assumptions:

- Initial health and movement speed are temporary constants.
- Attack behavior may start automatic on the first vertical slice.
- Player position is clamped to arena bounds.

### Enemy

Fields:

- Position.
- Velocity.
- Health.
- Move speed.
- Contact damage.
- Target reference.
- Drop behavior.

Independent assumptions:

- Enemies move toward the player or a simple orbit target.
- Enemy health is small enough to keep first-slice encounters readable.

### Projectile or Attack

Fields:

- Position.
- Velocity.
- Damage.
- Lifetime.
- Hit mask.
- Source owner.

Independent assumptions:

- First slice may use one basic projectile or auto-trigger attack.

### XP Gem

Fields:

- Position.
- Value.
- Collection radius.
- Lifetime.
- Magnetic behavior.

## Systems

### Input System

Responsibilities:

- Read keyboard state.
- Produce a movement vector.
- Support menu choices.

### Movement System

Responsibilities:

- Apply configured movement speed.
- Update entity positions.
- Clamp positions to arena bounds.

### Spawn System

Responsibilities:

- Track elapsed run time.
- Spawn enemies based on configured intervals.
- Increase spawn rate over time.

### AI System

First version:

- Simple steering toward the player or a target position.
- Configurable acceleration and maximum speed.
- No pathfinding.

### Combat System

Responsibilities:

- Apply attack hit rules.
- Apply damage.
- Remove defeated entities.
- Trigger damage feedback.

### Progression System

Responsibilities:

- Track XP.
- Compute level thresholds.
- Enter level-up selection.
- Apply selected upgrade.
- Record run progress.

### Difficulty System

Responsibilities:

- Increase enemy spawn rate.
- Increase enemy count caps.
- Increase enemy base attributes modestly.

### State and Game Flow

Required flow:

1. Start run.
2. Spawn starting enemies.
3. Update player, enemies, attacks, collisions, and progression.
4. Choose upgrade at level-up thresholds.
5. Continue until health reaches zero.
6. Enter game-over state.

## Vertical Slice Acceptance Criteria

A build is considered to have passed the first gameplay vertical slice only when:

1. The game opens and displays a logical 320×240 playfield.
2. The player can move in multiple directions using the keyboard.
3. At least one enemy spawns repeatedly.
4. Enemies move toward the player.
5. A basic attack can damage and defeat enemies.
6. Defeated enemies produce an XP item.
7. The player can collect XP.
8. XP collection raises a visible level.
9. A level-up presents and accepts a small upgrade choice.
10. Difficulty changes over time.
11. Player death transitions to game over.
12. Game states can be entered and exited deterministically.
13. At least core movement, XP, and health logic are covered by tests.

## Configuration Plan

Gameplay values should be configuration-driven rather than scattered constants. Proposed initial values:

- Player base speed.
- Player health.
- Enemy speed.
- Enemy health.
- Enemy contact damage.
- Spawn interval.
- Spawn cap.
- XP per gem.
- Level XP threshold.
- Attack cooldown.
- Difficulty interval and multiplier.

All initial values must be marked as temporary where original behavior is unknown. No value may be presented as extracted from HoloCure unless labeled verified.

## Testable Behaviors

The following must be designed so they can be tested without the full renderer:

- Movement vector.
- Collision bounds.
- Damage reduction.
- XP gain and leveling.
- Upgrade selection state.
- Spawn timer accumulation.
- Game-over transition.

## Deferred Work

Deferred from vertical slice:

- Character roster.
- Complex weapon evolution.
- Advanced enemy AI.
- Room loading.
- Local save data.
- Online features.
- Full art polish.
- Full original asset integration.