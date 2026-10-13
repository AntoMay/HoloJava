# Phase 1 Investigation Backlog

Source of truth for ranked reverse-engineering investigation work. This is not implementation permission.

## Prioritization Policy

Investigate minimum gameplay loop before broader systems:

1. Player movement.
2. Enemy spawning and movement.
3. Collision and damage.
4. Weapon attack behavior.
5. Enemy defeat and experience drops.
6. Experience collection and level-up.
7. Upgrade selection.
8. Survival, stage progression, and game-over conditions.

## Ranked Candidates

### P1 — Player Core

Candidate objects:

- `obj_Player`
- `obj_PlayerManager`
- `obj_PlayerManagerDungeon`
- `obj_PlayerPlatformer`
- `obj_playerNightLight`
- `input_controller_object`
- `Obj_Debug_FallText_*`

Evidence:

- Exact object names in `HoloCure_Unpacked/index/Objects_Detail.txt`.
- Native symbols in `HoloCure_Unpacked/binary_logic/gml_objects.txt`, including `gml_Object_obj_Player_Create_0`, `Step`, `Draw`, `Other`, `Collision`, and `Alarm` entries.
- Strings containing player/object names in the native executable.

Confidence: `Inferred`.

Missing information:

- Movement vector, speed, acceleration, collision masks, invulnerability timing, health and currency logic.

Next method:

- Native string and symbol inspection around player objects.
- Observational play capture under Wine if reliable.
- Room instance context from `Rooms_Detail.txt`.

Dependencies:

- Input handling.
- Collision geometry.
- Spawn and stage loop.

### P2 — Attack and Weapon Execution

Candidate objects:

- `obj_Attack`
- `obj_AttackController`
- `obj_cautionattack`
- `obj_FightPitAttack`
- `obj_itemLightBeam`
- `obj_MinigameNPC` is contextually relevant only for mini-games and should not be assumed combat-related.

Evidence:

- Exact object names in `Objects_Detail.txt`.
- `gml_Object_obj_Attack_*` and `gml_Object_obj_AttackController_*` symbols.
- Symbol classification category `weapon_projectile` has 9354 entries.

Confidence: `Inferred`.

Missing information:

- Cooldowns, trajectories, targeting, hitboxes, damage application, knockback, crit rules, scaling.

Next method:

- Inspect native symbols containing attack-controller event names.
- Cross-reference sprite and texture groups with attack objects.
- Capture projectile behavior in gameplay observation.

Dependencies:

- Player movement.
- Enemy collision and damage.

### P3 — Enemy Base and Spawning

Candidate objects:

- `obj_BaseEnemy`
- `obj_Enemy`
- `obj_EnemyDead`
- `obj_EnemySpawn`
- `obj_EnemySpawner`
- `obj_MobManager`

Evidence:

- Exact object names in `Objects_Detail.txt`.
- `gml_Object_obj_Enemy*` and `gml_Object_obj_EnemySpawner*` native symbols.

Confidence: `Inferred`.

Missing information:

- Spawn intervals, wave thresholds, enemy stat formulas, target behavior, death rewards, debuff rules.

Next method:

- Native symbol names and room instance context.
- Observe spawn patterns and timers under reference game.

Dependencies:

- Stage loop.
- Combat.

### P4 — Experience, Level-Up, and Upgrade Selection

Candidate objects and systems:

- `obj_PickUpable`
- `obj_ItemCrate`
- `obj_FandomManager`
- `obj_TitleChar_Suisei` is character selection context, not progression directly.
- Global script names containing `AddFandomEXP`, `CalculateScore`, `DoAchievement`.

Evidence:

- Object names in `Objects_Detail.txt`.
- Native global script names in `gml_globalscripts.txt`.
- String and symbol context.

Confidence: `Inferred`.

Missing information:

- XP thresholds.
- Drop rules.
- Upgrade pool selection.
- Randomness and reroll mechanics.
- Upgrade display order.

Next method:

- Native symbols for fandom/exp and level-up handlers.
- Observation of XP thresholds and upgrade offers.

Dependencies:

- Enemy defeat and drops.
- UI and input.

### P5 — Collision and Damage Application

Candidate evidence:

- Native symbols: `gml_Object_obj_damageText_*`, `gml_Object_obj_Player_*`, `gml_Object_obj_BaseEnemy_*`, `gml_Object_obj_Attack_*`.
- Index count: `collision_damage` category has 357 symbol matches.
- Object and event counts in `Objects_Detail.txt`.

Confidence: `Inferred`.

Missing information:

- Collision boxes, shape precision, damage order, invulnerability, status effect timing.

Next method:

- Exact native object/event inspection.
- Defensive testing in Java only after collision inputs are understood.

Dependencies:

- Player and enemy state.
- Asset metadata for masks.

### P6 — Stage, Spawn System, Boss, and Game-Over

Candidate objects:

- `obj_StageManager`
- `obj_EnemySpawner`
- `obj_DungeonBattleManager`
- `obj_DungeonMapController`
- `obj_TitleChar_*` for character selection flow.
- Rooms such as `Room1`, `rm_Tutorial`, `rm_ConcertStage`, `rm_Castle`, `rm_Dungeon`, `rm_IDArena`.

Evidence:

- Exact object and room names.
- Room instance coordinates in `Rooms_Detail.txt`.
- Native room symbols in `binary_logic/gml_rooms.txt`.

Confidence: `Inferred`.

Missing information:

- Stage duration, boss triggers, spawn curves, win/loss conditions, restart behavior.

Next method:

- Room instance inventory and camera behavior.
- Observation of stage timers and boss transitions.

Dependencies:

- Core combat loop.

### P7 — Managers, UI, Text, and Game State

Candidate objects:

- `obj_GameManager`
- `obj_InputManager`
- `obj_TextController`
- `obj_DialogueController`
- `obj_DebugManager`
- `obj_vfxGUI`
- `obj_damageText`

Evidence:

- Exact object names.
- Global symbols containing UI/menu/text/input patterns.

Confidence: `Inferred`.

Missing information:

- State transition table, pause rules, input mapping, localization behavior.

Next method:

- Symbol and room observation.
- Save data investigation in a later phase.

Dependencies:

- Player and stage loop.

### P8 — Audio, Visual Effects, Fonts, and Shaders

Candidate assets:

- 380 audio resources.
- 23 fonts and glyph metadata.
- 48 shaders.
- `obj_SoundController`.
- `obj_glare_controller`.

Confidence: `Inferred`.

Missing information:

- Sound trigger conditions, mixing, ducking, shader conversion needs.

Next method:

- Asset metadata and audio file inspection.
- Later rendering parity checks.

### P9 — Meta Progression, Save Data, Settings, and Network

Candidate evidence:

- Objects: `obj_HoloHouseManager`, `obj_PlantManager`, `obj_UnlockRoom`, `Obj_FirebaseAuth*`, `Obj_FirebaseLoginManager`.
- Global script symbols: `DoAchievement`, `CheckValidName`, `Firebase...`.
- `save_meta` category: 2706 symbol matches.

Confidence: `Inferred`.

Missing information:

- Save schema, persistence timing, unlock formulas, online ranking, account workflow.

Next method:

- Native string inspection and later optional observational analysis.
- Security/legal review before any network implementation.

## Unknowns Requiring Evidence

- Exact damage and health formulas.
- Level and XP thresholds.
- Enemy spawn schedules.
- Weapon upgrade trees.
- Collision precision.
- Save format.
- Game-over and restart logic.

## Recommended Immediate Next Investigation Task

Inspect `gml_Object_obj_Player_*` and `gml_Object_obj_Attack*` symbols, cross-reference them with `Objects_Detail.txt`, then produce a behavior hypothesis list marked explicitly as `Inferred`.
EOF
echo "backlog written"