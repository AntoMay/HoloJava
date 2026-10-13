# Reverse Engineering Map

Source of truth: evidence mapping for original game resources, symbols, and strings.

## Audit Source Paths

- Original game: `/home/antomay/Projects/HoloCure`
- Extraction output: `/home/antomay/Projects/HoloCure_Unpacked`
- Gameplay symbol export: `/home/antomay/Projects/HoloCure_Unpacked/binary_logic`
- Native string export: `/home/antomay/Projects/HoloCure_Unpacked/binary_strings`

## Key Source Artifacts

| Artifact | Status | Use |
|---|---|---|
| `HoloCure_Unpacked/index/summary.txt` | Present | Engine and resource counts |
| `HoloCure_Unpacked/index/Objects_Detail.txt` | Present | 524 object definitions and event counts |
| `HoloCure_Unpacked/index/Rooms_Detail.txt` | Present | 29 rooms and instance coordinates |
| `HoloCure_Unpacked/index/Variables_Detail.txt` | Present | Variable/global init notes |
| `HoloCure_Unpacked/binary_logic/gml_symbols.txt` | Present | 29874 native GameMaker symbols |
| `HoloCure_Unpacked/binary_logic/gml_scripts.txt` | Present | 27501 script symbols |
| `HoloCure_Unpacked/binary_logic/gml_globalscripts.txt` | Present | 710 global init symbols |
| `HoloCure_Unpacked/binary_logic/gml_objects.txt` | Present | 14297 object/event symbols |
| `HoloCure_Unpacked/binary_logic/gml_rooms.txt` | Present | 70 room symbols |
| `HoloCure_Unpacked/binary_strings/HoloCure.exe.strings.txt` | Present | Native executable strings |
| `HoloCure_Unpacked/strings/strings.txt` | Present | 81613 text strings |

## Current Mapping State

- Asset manifest generated: 34,422 physical files, 463,584,164 bytes, 8,124 unique SHA-256 hashes.
- Symbol classification generated: 72,452 symbols.
- Candidate gameplay objects identified from exact object names: `obj_Player`, `obj_PlayerManager`, `obj_Attack`, `obj_AttackController`, `obj_BaseEnemy`, `obj_Enemy`, `obj_EnemySpawn`, `obj_EnemySpawner`, `obj_PickUpable`, `obj_ItemCrate`, `obj_GameManager`, `obj_InputManager`, `obj_TextController`, `obj_StageManager`.
- Rooms identified: 29 rooms including `rm_Title`, `rm_CharSelect`, `rm_Tutorial`, `Room1`, `rm_Dungeon`, `rm_ConcertStage`, `rm_Castle`, `rm_IDArena`, `rm_HoloOffice`, and `rm_UsadaCasino`.
- Detailed exports available: `Objects_Detail.txt` (524 objects), `Rooms_Detail.txt` (29 rooms), `Variables_Detail.txt` (variable count 0, 710 null-code global init entries).
- Room cross-reference now reports 29 GameMaker rooms, 13 native room code-base matches after suffix normalization, and 0 direct exact native room symbols before that normalization.
- No gameplay mechanic is validated yet.
- Object names, room instance names, and event counts are available for inspection.
- Global init scripts exist in metadata but their code entries are null, consistent with a YYC build.
- Native symbol strings can expose object/event/script names, but are not source code.

## Pivot Note

Exhaustive reverse engineering is now optional rather than a blocking implementation gate. The extraction evidence remains useful for asset identification and reference, but HoloJava gameplay is independently designed.

## Next Mapping Work

1. Inventory all extracted directories and file extensions.
2. Classify global scripts into likely subsystems.
3. Cross-reference object names with room instances.
4. Identify candidate player, enemy, weapon, manager, UI, and boss objects.
5. Mark every candidate as evidence-backed or unresolved.
6. Update `docs/GAME_MECHANICS.md` only after validation.

## Evidence Rule

No mechanic may be marked `Verified` or `Tested` solely because it has a resource name.
EOF