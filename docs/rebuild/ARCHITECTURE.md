# HoloJava Rebuild Architecture

Target runtime: CLDC 1.1 and MIDP 2.0, with J2ME Loader and possible Nokia device support. Java ME provided means only. No desktop Java APIs, lambdas, streams, reflection, Android classes, or Nokia vendor APIs unless a later milestone justifies a fallback.

## Design Philosophy

- Write a small game, not a generic engine.
- Keep class graph and object allocation low.
- Keep rendering deterministic and low-overhead.
- Prefer simple arrays and object pools.
- Put content definitions in data formats; put gameplay behavior behind small behavior classes.
- Make all game behavior testable off rendering where practical, but keep tests within ordinary Java or build-time assertions.
- Avoid stateful behavior spread across anonymous callbacks.

## Root Package Suggestion

`me.holojava.runtime`
- Application lifecycle and state machine.
- Canvas and presentation loop.

`me.holojava.core`
- Scalar game loop state and entities.

`me.holojava.gameplay`
- Player actions.
- Weapons and items.
- Enemy, projectile, pickup updates.

`me.holojava.render`
- Entity renderers.
- HUD and text routines.

`me.holojava.content`
- Content catalogs and factories that translate definitions into runtime objects.

`me.holojava.assets`
- Lightweight resource lookup and cache.

No single package should become a hidden dumping ground for global variables.

## Run-State Architecture

Represent game states as primitive constants:

- BOOT
- MENU
- RUNNING
- PAUSED
- LEVEL_UP
- GAME_OVER

State changes should be explicit methods receiving a next state. Update and render branches must never advance hidden state during rendering.

## Game Loop

Recommended design:

- Single thread.
- `update(long now)` and `render(Graphics g)`.
- Timer with accumulator and max catch-up of 3 cycles.
- Fixed tick recommended: 40 ms.
- Render interpolation only after measurement proves necessary.

State variables required but simple:
- timing accumulator.
- whether `isRunning` is exact variable.
- time at state transitions.
- fixed random seed optional.

Memory and loop safety:
- Avoid busy retries.
- Sleep minimum on each.tick.
- Stop thread immediately in `destroyApp` when able.
- Do not allocate in loops, except where profiling allows.

## Input

Abstraction:
- `InputSnapshot` with booleans for directions, fire, confirm, cancel, pause.
- Copy key states once per update.
- Numeric safe lookup.
- Key repeat decoupling for menus by polling transition with `wasPressed` copies.

Do not rely on hardware-specific key labels except as documentation.

## Entity Representation

Current prototype monolithic canvas works for proof scale. A clean first playable full game requires at least these conceptual types:

- `RunState`
- `PlayerState`
- `Enemy`
- `Projectile`
- `ExperiencePickup` or generic `DropPickup`
- `EffectInstance` for short-lived visuals
- `SpawnDirector`
- `LevelUpOffer`

Reuse arrays:
- Enemy pool: 32 to 64.
- Projectile pool: 32 to 128 depending content.
- Drop pickups: minimum 128 and possibly more.
- Temporary visuals: limited rings/numbers.
- Avoid dynamic object growth on phone.

Each object should be a local class with primitive fields, not Maps or wrappers. Java ME can accommodate these; consistency matters for performance.

Entity update sequence:
1. Update run timer.
2. Spawn director.
3. Player update.
4. Enemy update and enemy-player collision.
5. Weapon update and projectile movement.
6. Projectile totals against enemy collision.
7. Drops collection.
8. EXP level-up.
9. Status transitions.

## Collision System

- Integer AABB overlap.
- Explicit gameplay collision boxes.
- Render dimensions must not drive gameplay collision.
- Pre-calculated box centers can avoid repeated widths in tight loops.
- For bring/touch scales only while entity data permits.

## Rendering

- Render in one `Graphics` pass each frame.
- Logical area 320x240 with letterbox offset.
- Cache calculation of offset each frame if `getWidth()/Height()` changes.
- Draw sprites with unscaled MIDP draw.
- Null-fallback rectangles.
- Limit string operation in hot loops.
- Rendering optional decorations can be skipped if device performance demands.

## Assets

- Each resource path resolves under the JAR, never absolute home paths.
- All `asset` path maps must be validated at load time, with precise error reporting.
- Static arrays of `Image` frames loaded at scene enter.
- Scene ownership and release is required.
- Don't keep loadable all sprites; load at content boundary.
- Audio remains optional.
- `createImage(String)` from resource stream works, but loading failures should degrade to a placeholder rather than silently omit behavior.

## Content Definitions

Future full game should use immutable encoded data classes or tables for:

- Character definitions.
- Weapon definitions/usability.
- Item definitions.
- Enemy variants.
- Drop and upgrade weights.
- Stage timing patterns.
- Collab and evolution references.

JavaScript objects and builders are impossible. A practical low-cost Java representation is a small class:

```java
class WeaponDef {
  int id;
  int kind; // 0 melee, 1 multishot, 2 ranged.
  int slotWeight;
  int maxLevel;
  int damage;
  int cooldownFrames;
  int projectileCount;
  int projectileSpeed;
  int spriteGroup;
}
```

Even 50 such static classes and arrays fit comfortably midp if sprites are managed. Text localization can reference fields by primitive ID and override only display names as needed.

## Special Effects / Animation

- Frame index as primitive `int`.
- No `AnimationPlayer` framework with listeners/garbageables.
- Animation duration measured in ticks or milliseconds.
- Enemy animation frames should have stable frame counts or stored min.
- First implementation can use frame-by-frame PNGs; only add sheet packs when proven necessary.

## Memory Management

- Total devices might range from 3MB to 4MB.
- Keep total image RAM traceable with a budget.
- Load per stage/scene.
- Reuse arrays and keep primitive `boolean active` counters.
- Release `Image` variables at scene end by letting them go out of scope.
- Avoid `Vector` and `Hashtable` in runtime hot paths where arrays work.

## Rendering and Performance

- 320x240 logic.
- Draw directly to no flip trick: overwrite pixels each tick by clear background.
- Minimize heap work and remove animation state unless needed.
- J2ME loops should use scalar values targeted in specific methods, not object-oriented iterator abstractions.

## Persistence

Early architecture should not implement save state. If progress menu is needed, defer until after a playable vertical slice path and content structures are stable. The wiki includes gacha/shop/growth systems that could be too broad if placed in the initial architecture.

## Testing

Performance baseline is:
- Compile and preverify on official toolchains.
- Manual J2ME Loader runs with timings and crashes.
- Implementation classes should be testable for:
  - Movement boundaries.
  - Spawn count.
  - Timer math.
  - EXP merge/counts.
  - Collision.
  - State transitions.

Native GameMaker internals cannot be extracted, so data extraction remains evidence-based.

## Runtime Error Handling

- Fail gracefully on resource load.
- Always report which asset index failed.
- Never continue from an unresolved required resource without documenting why.
- Keep diagnostic MIDlet proving toolchain separately.
