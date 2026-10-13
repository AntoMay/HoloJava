# System Catalog

Source of truth: identifiable HoloCure systems, evidence references, implementation status, and design assumptions.

Status meanings:

- `Identified`: discovered through extraction, symbols, names, resources, or documentation.
- `Planned`: included in HoloJava design but not implemented.
- `Implemented`: Java implementation exists.
- `Tested`: implementation has reproducible test evidence.
- `Deferred`: intentionally postponed with a recorded reason.
- `Unresolved`: insufficient evidence or no safe design decision.
- `Not in scope`: explicitly excluded.

No system in this catalog is `Implemented` or `Tested` because no gameplay implementation, Java project, or tests exist yet.

## System Inventory

| System ID | System | Evidence Source | Current Status | Dependencies | Notes and Assumptions |
|---|---|---|---|---|---|
| `SYS-01` | Source data inventory | `index/summary.txt`, `tools/build_asset_manifest.py` | Identified | none | Counts for sounds, sprites, objects, rooms, and shaders are verified. |
| `SYS-02` | Asset extraction and manifest | `reports/asset_manifest.csv`, `index/Objects_Detail.txt`, `index/Rooms_Detail.txt` | Identified | `SYS-01` | Path and metadata inventories exist; asset use remains pending. |
| `SYS-03` | Native symbol classification | `binary_logic/gml_symbols.txt`, `binary_logic/gml_scripts.txt`, `binary_logic/gml_objects.txt`, `binary_logic/gml_globalscripts.txt` | Identified | `SYS-02` | Symbols identify likely procedures and objects, but not implementation. |
| `SYS-04` | Object and room metadata | `Objects_Detail.txt`, `Rooms_Detail.txt` | Identified | `SYS-03` | Object sprites, parent relationships, event counts, room dimensions, and instances exist. |
| `SYS-05` | Asset selection and keys | `config/assets.json`, `docs/ASSET_USAGE.md` | Planned | `SYS-02` | Candidate proxy assets are selected and verified; replacements may be needed. |
| `SYS-06` | Asset loading and resolution | `docs/ASSET_LOADER_CONTRACT.md`, `docs/ASSET_LOADER_TEST_PLAN.md` | Planned | `SYS-05` | Contract is documented but no Java loader exists. |
| `SYS-07` | Fixed-resolution rendering | `Roadmap.md`, `docs/GAMEPLAY_DESIGN.md` | Planned | `SYS-06` | 320×240 internal resolution is an approved design requirement. |
| `SYS-08` | Player input | `gml_Object_obj_...` symbol names, object inventory | Unresolved | none | Object names indicate input exists, but controls and behavior are not verified. |
| `SYS-09` | Player locomotion | `Objects_Detail.txt` candidate object names, symbol names | Unresolved | `SYS-08` | Movement semantics and speed are not known from evidence. |
| `SYS-10` | Enemy management | Object names such as `obj_BaseEnemy`, `obj_Enemy`, `obj_EnemySpawn`, `obj_EnemySpawner`, `obj_MobManager` | Unresolved | `SYS-01` | Object names indicate enemies and spawning, but behavior and balancing are not verified. |
| `SYS-11` | Attack and weapon execution | Object and global script names related to attack, projectile, bullet, weapon | Unresolved | `SYS-03` | Naming evidence does not establish formulas, timing, targeting, or hitboxes. |
| `SYS-12` | Collision and damage | Mask exports, object detail, symbol names | Unresolved | `SYS-09`, `SYS-10`, `SYS-11` | Collision boxes, damage order, invulnerability, and effects are unverified. |
| `SYS-13` | XP and level-up | Object names `obj_PickUpable`, `obj_ItemCrate`, global script names around fancypickups and level-up | Unresolved | `SYS-10`, `SYS-12` | XP thresholds, collection behavior, and upgrade flow are not known. |
| `SYS-14` | Upgrade selection | Global script names and UI evidence | Unresolved | `SYS-13` | Selection pool and placement logic are not verified. |
| `SYS-15` | Spawn and stage progression | `Rooms_Detail.txt`, object names, global script names | Unresolved | `SYS-10`, `SYS-12` | Spawn timing, wave composition, boss triggers, and route logic are not verified. |
| `SYS-16` | Animation and frame playback | `ASSET_USAGE.md`, `objects` and `Rooms_Detail` | Unresolved | `SYS-05` | Frame ordering, anchors, and playback timing are unverified. |
| `SYS-17` | Audio playback | `sounds`, object and script names, shaders | Identified | `SYS-05` | Assets exist; playback requirements are planned and unimplemented. |
| `SYS-18` | UI and text | Object names `obj_TextController`, `obj_DialogueController`, fonts, indexes | Unresolved | `SYS-02` | Layout and text flow behavior are not verified. |
| `SYS-19` | Main loop and state management | `GAMEPLAY_DESIGN.md`, room/object inventory | Planned | `SYS-06` | Java design is proposed but not implemented. |
| `SYS-20` | Saves and settings | Object and script names | Unresolved | `SYS-19` | Save schema and expected fields are not known. |
| `SYS-21` | Build and packaging | Toolchain status | Planned | none | Java, Gradle, and libGDX are not installed. |
| `SYS-22` | Asset redistribution compliance | `ASSET_USAGE.md`, all media assets | Unresolved | `SYS-05` | Licenses for local assets have not been reviewed. |

## System Catalog Rules

1. Status changes from `Identified` to `Implemented` only when Java code exists.
2. Status changes from `Implemented` to `Tested` only with documented test evidence.
3. Mark an item `Deferred` only with a reason and approval.
4. Mark an item `Unresolved` only when evidence or a safe design decision is missing.
5. No implementation may depend on a symbol name as proof of behavior.
6. Unverified systems should remain under HoloJava's own design rules rather than assumptions from HoloCure.