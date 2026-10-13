# J2ME Loader Manual Test Checklist

Artifact pair:

- `/home/antomay/Projects/HoloJava/dist/java-me-fullgame/HoloJavaFullGame.jar`
- `/home/antomay/Projects/HoloJava/dist/java-me-fullgame/HoloJavaFullGame.jad`

Install both files together. Do not mix them with the preserved diagnostic or slice JADs.

## Navigation

1. Launch `HoloJava Full Game`.
2. Confirm the main menu renders: Play, Game Mode, Characters, Stage Select, Settings, Exit.
3. Use the d-pad and FIRE on every option.
4. Confirm Back returns to the previous screen and Exit shuts down cleanly.

## Character and mode selection

1. Select each of the five Myth characters.
2. Confirm portrait, HP, ATK, SPD, weapon, special, and skill text changes.
3. Confirm each character has a different starting weapon configuration.
4. Select Stage, Endless, and Time modes.
5. Confirm Stage/Endless show normal and Hard stages.
6. Confirm Time mode only permits Concert Stage after Stage 3 completion.

## Stage transitions

1. Start Stage 1 Grassy Plains.
2. Confirm the grass tile and shrimp pool load.
3. Return to menu and start Stage 2, then confirm a different tile and Sukonbu pool.
4. Confirm stage-specific boss schedules do not carry over between runs.
5. Confirm locked stages remain locked until their RMS completion condition is met.

## Gameplay

1. Move with the d-pad in all four directions.
2. Confirm player idle/run animation changes with movement.
3. Confirm the main weapon attacks automatically.
4. Confirm enemies chase, shoot, charge, or use their configured special behavior.
5. Confirm contact damage occurs on cooldown rather than every frame.
6. Confirm invulnerability feedback occurs after a hit.
7. Defeat enemies and collect EXP, coins, food, and rare drops.
8. Confirm the EXP bar, level, coins, kills, weapon count, and item count update.
9. Reach a level-up and confirm the simulation is paused.
10. Navigate offers with UP/DOWN and confirm with FIRE.
11. Confirm weapon, item, stat, and skill offers apply exactly once.
12. Press numeric `5` after acquiring a skill; press numeric `9` for the one-use special.
13. Pause with FIRE and resume with FIRE.
14. Reach a boss schedule and confirm a boss health bar, boss attacks, defeat drops, and encounter state.
15. Confirm Stage Mode victory only after the final scheduled boss is defeated.
16. Confirm Endless Mode continues after scheduled bosses until player defeat.
17. Confirm Time Mode uses the 4,000-kill objective and collab restrictions.
18. Confirm Game Over, Replay, and Return to Menu transitions.

## Failure and performance observations

Record:

- Missing images or fallback rectangles.
- Exceptions or frozen screens.
- Frame rate and heap behavior after several stage changes.
- Whether audio remains silent as designed.
- Whether the physical FIRE/NUMBER keys map as expected.
