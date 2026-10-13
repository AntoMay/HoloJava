# HoloJava Known Gaps - Viewport and Resolution

## Viewport and Resolution
- 320x240 landscape designated as logical design resolution
- Viewport implementation uses integer allocation-free layout with letterboxing
- World expands on wide/tall screens; narrow screens use centered crop of minimum 320x240 world
- Portraits lazy-loaded via `AssetLibrary.characterPortrait()` to reduce startup memory
- Runtime pools bounded: ENEMY_POOL=48, SHOT_POOL=128, MELEE_POOL=24, PICKUP_POOL=96
- No per-frame object allocations; all pools initialized once during `reset()`

