# HoloJava Optimization Summary - 320x240 Landscape and Memory

## Optimization Logical Resolution: 320x240 Landscape

The Viewport class (`Viewport.java`) already designates 320x240 as the minimum/logical design resolution:

- `static final int MIN_W = 320;`
- `static final int MIN_H = 240;`
- `void update(int width, int height)` expands the world to at least 320x240 and centers it via offsets
- `contains(int worldX, int worldY)` clips rendering to the valid viewport
- `screenX()/screenY()` convert world coordinates to canvas coordinates

**Scaling policy:**
- 320x240 (4:3 aspect ratio) is the logical design resolution
- Screens with matching aspect ratio use full world dimensions
- Wide screens (e.g., 16:9) expand the world horizontally
- Tall screens (e.g., portrait) expand the world vertically
- Narrow screens use a centered crop, maintaining the 320x240 world proportions via offsets
- Letterboxing is preferred over sprite distortion

**Input mapping consistency:**
- Touch/key input uses world coordinates
- Canvas dimensions are converted via `screenX()/screenY()` for consistent gameplay
- The same world-to-screen conversion applies regardless of display size

## Memory Optimization

### Assets
- **Portraits**: Lazy-loaded via `AssetLibrary.characterPortrait(int index)` - only loaded when needed (character selection screen), not at startup
- **Weapon/projectile images**: Loaded at startup (core gameplay assets, small individual files)
- **Enemy/boss animation frames**: Loaded on demand via `AssetLibrary.prepareStage(int stageIndex)` - only the enemies/bosses for the current stage
- **Tile images**: Loaded lazily via `AssetLibrary.tile(String key)` - only the tiles for the current stage
- **No all-47-portraits-at-startup**: The `CharacterAssets.portrait` field is `null` initially and loaded on first access

### Runtime Objects
- **Bounded object pools** (initialized once during `RunWorld.reset()`):
  - ENEMY_POOL = 48
  - SHOT_POOL = 128
  - MELEE_POOL = 24
  - PICKUP_POOL = 96
- **No per-frame object creation** in rendering or combat hot paths
- **All pools are fixed-size arrays**; exhaustion is handled predictably
- **No unbounded collections** introduced

### Images and Effects
- **Prefer existing appropriately sized assets** - no new image assets created for this task
- **Reduce only genuinely unnecessary buffering or effects** - none identified that degrade gameplay
- **Pre-existing optimization**: `RunWorld` states "No allocation occurs in entity loops"

## Diagnosed Startup Crash

The saved prototype JAR (`Downloads/HoloJavaFullGame.jar`, 312,521 bytes) was **missing** two class files present in the current build:
- `me/holojava/fullgame/Viewport.class`
- `me/holojava/fullgame/WikiCatalogData.class`

These were added during this session's collab catalog work. The current JAR (508,855 bytes) includes them. The prototype also lacked 47 portrait assets, but these are optional fallback art.

**Confirmed**: The current JAR passes all verification checks (`tools/verify_fullgame_build.py`: ALL CHECKS PASSED). No J2ME Loader launch was performed as part of this task.

## Tests Executed

- `tools/verify_fullgame_build.py`: PASS (JAR=508861 bytes, assets=166, wiki rows verified)
- Viewport calculations verified for: 240x320, 320x240, 480x800, 800x480, 1080x2400, square, and extra-wide dimensions
- No regressions introduced

## Remaining Crash Hypotheses (unresolved)

If J2ME Loader crashes remain unexplained, possible causes include:
- Prototype was built before collab catalog regeneration (missing `Viewport.class`, `WikiCatalogData.class`)
- Missing 47 portrait assets (optional fallback art, may cause display issues but unlikely to crash)
- Other initialization issues unrelated to resolution/memory

**J2ME Loader execution was not performed** as part of this task - no emulator/device testing was done.

## Files Modified

- `src/java-me-fullgame/me/holojava/fullgame/Viewport.java`: Updated javadoc to explicitly document 320x240 as logical design resolution and scaling policy
- `docs/rebuild/KNOWN_GAPS_VIEWPORT.md`: New file documenting viewport and resolution gaps

## Verified Artifacts

- `dist/java-me-fullgame/HoloJavaFullGame.jar` (508,861 bytes)
- `dist/java-me-fullgame/HoloJavaFullGame.jad` (275 bytes)
- `docs/rebuild/OPTIMIZATION_SUMMARY.md`
- `docs/rebuild/KNOWN_GAPS_VIEWPORT.md`
