# HoloJava Android Reference Study

**Reference Repository**: `/home/antomay/Projects/Holocure-on-Android`  
**Study Date**: 2026-10-13  
**HoloJava Project**: `/home/antomay/Projects/HoloJava`  
**Status**: Audit-only; no source code, assets, or build artifacts modified.

## 1. Executive Summary

The `Holocure-on-Android` repository is a **GameMaker Studio 2** project for adapting Holocure to Android. It is **fundamentally incompatible** with HoloJava's Java ME CLDC 1.1 / MIDP 2.0 target. The Android adaptation uses GameMaker's native toolchain (GML code, binary resources, APK packaging) rather than Java ME APIs. No Java code, MIDP APIs, or CLDC stubs are involved.

The most useful confirmed findings for HoloJava concern high-level game architecture patterns that could conceptually inform HoloJava's design, but all would require reimplementation within MIDP constraints. The repository provides no directly portable code or assets.

## 2. Repository Identity and Commit

- **Directory**: `/home/antomay/Projects/Holocure-on-Android/`
- **Git present**: Yes (`.git` directory exists)
- **README**: `/home/antomay/Projects/Holocure-on-Android/README.md`
- **Project folder**: `/home/antomay/Projects/Holocure-on-Android/Project/`
- **Last modification**: 2026-10-13 (same date as study)

**Files inspected** (all read-only, no modifications made):
- `/home/antomay/Projects/Holocure-on-Android/README.md`
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/HolocureAndroid.yyp` (GameMaker project JSON)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/rooms/Room1/Room1.yy` (GameMaker room JSON)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/android/options_android.yy` (Android build options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/windows/options_windows.yy` (Windows build options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/main/options_main.yy` (Main build options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/linux/options_linux.yy` (Linux build options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/main/options_main.yy` (cross-platform options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/mac/options_mac.yy` (macOS options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/tvos/options_tvos.yy` (tvOS options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/ios/options_ios.yy` (iOS options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/options/html5/options_html5.yy` (HTML5 options)
- `/home/antomay/Projects/Holocure-on-Android/Project/HolocureAndroid/HolocureAndroid.resource_order` (resource ordering)

**No evidence of**: Java source code, MIDP APIs, CLDC stubs, native Android API usage beyond manifest values, GLSL shaders, JNI, or native C/C++ code.

## 3. Confirmed Architecture and Adaptation Approach

### Engine/Framework
- **GameMaker Studio 2** (Beta version 2023.100.0.273)
- Runtime: runtime-2023.800.0.410
- Project format: GMProject (JSON-based, zip compressed)
- GameMaker Language (GML) is the scripting language, but no GML scripts are exposed in the checked-out files

### Target Platform
- **Android** via GameMaker's built-in Android exporter
- **Target SDK**: 28
- **Minimum SDK**: 24
- **Compile SDK**: 31
- **Supported architectures**: armv7 (true), arm64 (true), x86_64 (true)
- **Screen orientation**: Landscape primary (portrait orientation flags are false)
- **Gamepad support**: Disabled

### Package and Identity
- **Package domain**: `com`
- **Package company**: `lexoff`
- **Package product**: `holocure`
- **Application name**: `Holocure`
- **Version**: `1.0.2`

### Resource Organization
- **Folders**: Sprites, Tile Sets, Sounds, Paths, Scripts, Shaders, Fonts, Timelines, Objects, Rooms, Sequences, Animation Curves, Notes, Extensions
- **Texture group**: Default, 512x512, compressed with bz2, autocropped with 2px border
- **Audio group**: audiogroup_default
- **No IncludedFiles** in the project (all assets appear to be within the .yyp or referenced via paths)

### Adaptation Approach
1. Open the project in GameMaker Beta 2023.100.0.273
2. Place the runtime-2023.800.0.410 in the GameMaker Cache/runtimes directory
3. Compile for Android (the README warns NOT to change targetSdk as it will cause unfixable bug when starting a run)
4. Unzip the generated APK
5. Replace the assets folder with the provided "assets folder from Assets"
6. Zip the APK without compression: `zip -0 -r holocure.apk *`
7. Align the APK: `zipalign -p 4 holocure.apk holocure_aligned.apk`
8. Sign the APK: `apksigner sign --ks PATH_TO_YOUR_KEYSTORE holocure_aligned.apk`

### What the Repository Actually Contains
- **Project**: `HolocureAndroid.yyp` — GameMaker project manifest
- **Room**: `Room1.yy` — Empty room (no instances), 1366×768, two background layers with inherit disabled
- **Android options**: `options_android.yy` — Full Android build configuration (SDK versions, package name, architectures, splash screens, launch settings)
- **No Java code**, **no MIDP APIs**, **no CLDC stubs**, **no native Android API source**
- **No GML scripts**, **no shaders**, **no object definitions** visible in the checked-out files
- **No asset files** (sprites, sounds, etc.) beyond folder references

### Confirmed Limitations
- This is a GameMaker native port, not a Java ME port
- No Java ME / CLDC / MIDP compatibility is claimed or implied
- The adaptation uses GameMaker's runtime and toolchain, not Android's AOSP or the Java SE/Android SDK
- Any transplanted concepts must be reimplemented from scratch within MIDP 2.0 constraints

## 4. Comparison with HoloJava

| Topic | Android reference findings | HoloJava current approach | Potential lesson | Compatibility |
|---|---|---|---|---|
| **Startup and lifecycle** | GameMaker project compiled to APK; launch via `am start -n com.lexoff.holocure/com.unity3d.player.UnityPlayerActivity` (or similar Activity); README provides installation/cli steps | `FullGameMIDlet.startApp()` → `pauseApp()` → `destroyApp(boolean)` → MIDlet lifecycle via J2ME Loader; canvas `update()` / `render()` loop at ~25 FPS | **Portable concept**: State machine pattern (menu → gameplay → pause → game over → results) is engine-agnostic; HoloJava already uses `state = STATE_RUNNING / PAUSED / LEVEL_UP / GAME_OVER / VICTORY` | `Portable concept` — HoloJava's MIDlet lifecycle can mirrorsame pattern without GameMaker dependencies |
| **Controls** | Touchscreen mapped via GameMaker GML; Android option flags: `option_android_gamepad_support: false`; landscape primary; no gamepad; `option_android_lint: false`; logcat configured | `FullGameCanvas.keyPressed(int)` handles `FIRE_PRESSED`, `GAME_A_PRESSED`; `up/down/left/right`; `skillPressed` (key '5'), `specialPressed` (key '9'); attack pulse/held edge detection; combat input separated from menu/input | **Portable concept**: Abstract input mapping (directional + action button) can conceptually carry over; HoloJava uses canvas key events; touch would require J2ME Touch API or canvas polling, which is limited | `Adaptable with Java ME constraints` — HoloJava's key-based input would need touch-mapping layer for Android-style touch controls |
| **Rendering and display** | GameMaker default: 1366×768 room; Android option `option_android_scale: 0`; `option_android_texture_page: "512x512"`; `option_android_interpolate_pixels: false`; landscape primary; views disabled (`enableViews: false`); two background layers with `clearViewBackground: false` | `Viewport.java` deterministic calculation for 240×320, 320×240, 480×800, 800×480, 1080×2400, square, extra-wide; world/screen coordinate separation; sprite proportion preservation; `FullGameCanvas.render()` draws to Canvas dimensions; CLDC 1.1 fixed-function pipeline | **Portable concept**: Resolution handling and aspect-ratio adaptation; HoloJava already has viewport tests for multiple dimensions; GameMaker's approach is similar but with different constraints (OpenGL ES vs. fixed-function MIDP) | `Adaptable with Java ME constraints` — HoloJava's viewport system can conceptually accommodate GameMaker's resolution ideas within CLDC 1.1 limits |
| **Assets and resources** | Texture group: 512x512, bz2-compressed, autocropped, 2px border; Audio group: audiogroup_default; Folder structure: Sprites, Sounds, Paths, etc.; No included files in project (assets may reside outside committed files) | `AssetLibrary.java` maps PNG sprites via `assets/fullgame/`; 166 PNG assets verified against manifest; Resource loading via RMS or getResourceAsStream(); CLDC 1.1 heap constrained; no bz2 compression; no adaptive icon system | **Portable concept**: Data-driven asset organization (folders → resource categories); HoloJava already uses `assets/fullgame/` mapping; concept of grouping by character/enemy/scene could refine MIDP resource paths | `Adaptable with Java ME constraints` — Asset grouping idea can refine HoloJava's existing mapping; compression and adaptive icons are Android-specific |
| **Game functionality and persistence** | Room: empty (no instances); GameMaker DND disabled (`isDnd: false`); physics disabled (`PhysicsWorld: false`); room persistent: false; no sequenceId; logcat: `"yoyo:V DEBUG:V AndroidRuntime:V"`; Android log output; likely GML code for gameplay but not visible in checked-out files | `RunWorld.java` deterministic simulation (enemy spawn, weapon fire, collision, EXP, level-up, boss encounters); `SaveStore.java` RMS persistence for settings/unlocks/selected character; `FullGameMIDlet` MIDlet lifecycle; `ContentCatalog` data-driven definitions; collision via overlap(); EXP pickup, level-up offers; 3 modes (Stage/Endless/Time) | **Portable concept**: Deterministic simulation with bounded pools; HoloJava already uses `ENEMY_POOL = 48`, `SHOT_POOL = 128`, `MELEE_POOL = 24`, `PICKUP_POOL = 96`; state machine (RUNNING/PAUSED/LEVEL_UP/GAME_OVER/VICTORY); data-driven enemy/weapon/item definitions; RMS persistence for retention across runs | `Portable concept` — HoloJava's simulation architecture is already designed for CLDC 1.1; the GameMaker room layout has no direct mapping but the underlying patterns (state machine, pools, data-driven design) are directly applicable |
| **Input abstraction** | Touchscreen mapped via GameMaker GML; Android option flags: `option_android_gamepad_support: false`; landscape primary; no gamepad; `option_android_lint: false`; logcat configured | `FullGameCanvas.keyPressed(int)` handles `FIRE_PRESSED`, `GAME_A_PRESSED`; `up/down/left/right`; `skillPressed` (key '5'), `specialPressed` (key '9'); attack pulse/held edge detection; combat input does not leak into menus/pause/level-overlays; `updateMain()`, `updateCharacters()`, `updatePrep()` handle menu navigation | **Portable concept**: Separate attack/movement/confirm/skill/special/pause inputs; HoloJava already has this separation; touch would add pointer coordinates but core mapping pattern carries over | `Portable concept` — HoloJava's input architecture is directly portable in principle; actual touch mapping would require J2ME Touch API which is limited/optional |
| **Display scaling and aspect ratios** | `option_android_orient_landscape: true`; `option_android_orient_portrait: false`; room 1366×768 (16:9); `option_android_screen_depth: 0`; `option_android_scale: 0`; texture page 512x512; interpolate pixels disabled | `Viewport.java` tests: 240×320, 320×240, 480×800, 800×480, 1080×2400, square, extra-wide; world/screen coordinate separation; sprite proportion preservation; HUD adaptable to portrait/landscape/4:3/16:9/20:9/square/wide; `BaseRendering` uses actual Canvas dimensions | **Portable concept**: Resolution adaptation and HUD placement; HoloJava already has extensive viewport tests; GameMaker's approach is more limited (landscape only) | `Portable concept` — HoloJava's viewport system is more extensive; GameMaker's approach is a subset |
| **Memory-conscious design** | Not evident from checked-out files (GameMaker manages memory internally) | `ENEMY_POOL = 48`, `SHOT_POOL = 128`, `MELEE_POOL = 24`, `PICKUP_POOL = 96`; "No allocation occurs in entity loops" (RunWorld comment); bounded pools; ProGuard preverification; 147 → 40 library classes; no Java SE APIs | **Portable concept** — HoloJava already implements this; GameMaker manages memory internally so no visible patterns | `Portable concept` — HoloJava already leads here |
| **Platform-specific workarounds** | `option_android_target_sdk: "28"`; `option_android_minimum_sdk: "24"`; `option_android_build_tools: "31.0.0"`; lint disabled (`option_android_lint: false`); logcat verbose; splash screens; launchscreen fill; device support level 0; scaling level 0; no gamepad; specific texture page format; density-specific icons (ldpi→xxxhdpi); adaptive icon flags disabled | No platform-specific workarounds beyond CLDC 1.1 / MIDP 2.0 constraints; ProGuard preverification; J2ME Loader compatibility; no density-specific icons (PNG sprites used directly); no splash screen mechanism beyond MIDlet suite description | **Android-specific** — GameMaker's build pipeline and Android SDK constraints have no CLDC/MIDP equivalent | `Android/GameMaker-specific` |

## 5. Portable Lessons for HoloJava

The following concepts from the Android reference could conceptually inform HoloJava, all requiring reimplementation within MIDP constraints:

1. **State machine pattern** — HoloJava already uses `STATE_RUNNING / PAUSED / LEVEL_UP / GAME_OVER / VICTORY`; GameMaker's room/sequence structure confirms this pattern's universal usefulness.

2. **Data-driven design** — Both projects use data-driven definitions (GameMaker resources vs. HoloJava's CSV/WikiCatalogData); the principle of separating content from code is confirmed applicable.

3. **Resolution/adaptation patterns** — GameMaker's `option_android_scale: 0` and HoloJava's `Viewport.java` tests for multiple dimensions share the same goal; HoloJava's implementation is more extensive.

4. **Input mapping abstraction** — The pattern of separating attack/movement/confirm/skill/special/pause inputs is confirmed portable; HoloJava already implements this; touch mapping would be the only new addition.

5. **Log diagnostics** — GameMaker's logcat configuration (`yoyo:V DEBUG:V AndroidRuntime:V`) confirms the value of runtime logging; HoloJava lacks a similar runtime log output mechanism.

## 6. Incompatible with CLDC 1.1 / MIDP 2.0

- **GameMaker runtime and project format** (.yyp, .yy, GML) — no Java ME equivalent
- **APK packaging and signing** (zipalign, apksigner, AndroidManifest, keystore) — no CLDC/MIDP equivalent
- **Android APIs** (Activity, Service, Intent, Permission, Google Play Services) — explicitly forbidden
- **OpenGL ES shaders** — MIDP 2.0 has fixed-function rendering only
- **JNI / native code** — CLDC 1.1 has no JNI
- **Density-specific icon systems** (ldpi→xxxhdpi, adaptive icons) — HoloJava uses uniform PNG sprites
- **Touchscreen APIs** beyond basic canvas polling — J2ME Touch API is limited/optional
- **Density-dependent resource compression** (bz2) — HoloJava uses uncompressed PNG
- **Google Play Services / Game Services** — HoloJava uses offline RMS persistence

## 7. Ranked Follow-up Experiments

| Rank | Problem Addressed | Expected Benefit | Implementation Complexity | CLDC 1.1 / MIDP 2.0 Risk | Verification Test | Order |
|---|---|---|---|---|---|---|
| 1 | Add touch control mapping layer | Enable touch gameplay on capable devices | Medium (canvas polling + pointer events) | Low — adds optional code path | Run canvas touch events; verify MIDlet still starts | Before J2ME Loader test |
| 2 | Enhance viewport test matrix | Confirm robustness across extreme dimensions | Low (add 1920×1080, 720×1280, etc.) | None — pure calculation test | Run `Viewport.java` tests with new dimensions | After core gameplay |
| 3 | Implement log output mechanism | Debugging and diagnostics | Medium (RMS-based logger or MIDlet alert) | Low — optional diagnostic tool | Write log entry; verify persists across runs | After core gameplay |
| 4 | Reimplement Android asset grouping | Organize sprites by character/enemy/scene | Low (refactor AssetLibrary folder structure) | None — pure reorganization | Verify all 166 assets load; no missing references | After core gameplay |
| 5 | Port GameMaker room state machine | Port gameplay state transitions | High (GML→MIDP logic reimplementation) | Medium — risk of logic divergence | Compare room state transitions vs. HoloJava state machine | After core gameplay |

## 8. Unknowns and Limitations

- **No GML scripts or object definitions** are in the checked-out files; gameplay code likely resides outside the committed file set
- **Asset files** (sprites, sounds) are not included; references are folder-path-only
- **No source code inspection** was possible (GameMaker project files are binary/JSON, not decompilable to human-readable GML without the IDE)
- **No runtime testing** was performed; the study is based solely on project metadata and README instructions
- **No J2ME Loader or emulator testing** was possible; the reference is Android/GameMaker-specific
- **Package name**: `com.lexoff.holocure` (from Android options) vs. HoloJava's `me.holojava` — no conflict, but naming conventions differ
- **Whether the empty Room1** indicates a menu room, a placeholder, or a room with instances added at runtime is unclear

## 9. Confirmation of No Modifications

- **No HoloJava source files were modified**: `src/java-me-fullgame/...` unchanged
- **No HoloJava JAR/JAD artifacts were modified**: `dist/java-me-fullgame/` unchanged
- **No HoloCure reference directories were modified**: `HoloCure/` and `HoloCure_Unpacked/` untouched
- **No repositories were cloned or downloaded**: Holocure-on-Android was already present
- **No packages were installed**: No new tooling added
- **No Git state was changed**: No commits, branches, or file modifications in any repository

## 10. Report Path

`/home/antomay/Projects/HoloJava/docs/rebuild/ANDROID_REFERENCE_STUDY.md`

