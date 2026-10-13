# Reconstruction Scope

Source of truth: the reconstruction boundary for HoloJava.

## Objective

HoloJava will reconstruct as much of HoloCure's identifiable systems, content, and presentation as practical, using available local extracted assets where appropriate. HoloCure is the asset and presentation reference. Gameplay implementation in HoloJava is independently designed.

HoloJava does not aim to reproduce the original GameMaker YYC implementation internally, and it does not claim behavioral parity unless a mechanic is validated and documented as validated.

## Scope Model

HoloJava separates scope into the following layers:

- **Presentation**: sprites, backgrounds, fonts, shaders, sounds, UI layouts, and visual effects.
- **Systems**: identifiable gameplay and runtime systems inferred from extracted names, symbols, rooms, objects, resources, and observable behavior.
- **Content**: playable stages, characters, enemies, weapons, items, upgrades, pickups, text content, and media.
- **Infrastructure**: asset loader, renderer, input, configuration, save/settings, and build packaging.

## Included Areas

The following areas are in scope unless explicitly deferred in `docs/SYSTEM_CATALOG.md`:

- Desktop 2D survival run behavior based on the official HoloJava design.
- 320×240 internal resolution rendering.
- Player locomotion, attack presentation, and collision handling in the Java implementation.
- Enemy spawning, movement, contact damage, and defeat handling in the Java implementation.
- XP collection, leveling, upgrade selection, and difficulty progression in the Java implementation.
- Stage and session game flow in the Java implementation.
- Audio and visual presentation using suitable local assets or approved placeholders.
- UI, text, score, and HUD concepts adapted into the Java implementation.
- Configuration-driven balancing.
- Save/settings behavior only after its scope and format are explicitly approved.

## Explicit Exclusions

The following are not automatic scope commitments:

- Exact duplication of the GameMaker runtime.
- Recovery of every GameMaker bytecode or underlying YYC C++ function.
- Behavioral parity with undiscovered mechanics.
- Unvalidated damage, spawn, XP, AI, or upgrade algorithms.
- Networking or leaderboards without security and permission review.
- Mobile-specific input or platform behavior.
- Full extraction-wide asset integration without prioritization.
- Redistribution of protected original assets without permission.

## Evidence Policy

- Presence in the extraction is not proof of implementation in HoloJava.
- A system listed in `SYSTEM_CATALOG.md` is not production code unless it has status `Implemented`.
- A system is not validated unless it has status `Tested`.
- Original HoloCure behavior may only be recorded in `GAME_MECHANICS.md` when evidence exists.
- Anything unverified should be marked `Deferred` or `Unresolved`, not described as reconstructed.

## Distribution and Public Build

- Asset redistribution is a separate approval topic from gameplay implementation.
- A build that includes original assets requires explicit permission review.
- Placeholder assets are the safe baseline for any public prototype until permissions are documented.

## Scope Change Control

Any scope change must update:

- This file, if the boundary or inclusion changes.
- `docs/SYSTEM_CATALOG.md` if system statuses are affected.
- `docs/CONTENT_COVERAGE.md` if coverage targets or selected assets change.
- `Roadmap.md` if the milestones or timeline are affected.
- `docs/PROJECT_STATUS.md` if immediate work changes.

## Source of Truth

This file is the primary source of truth for scope boundaries. It supersedes broad interpretation of the HoloCure asset set as an implementation directive.