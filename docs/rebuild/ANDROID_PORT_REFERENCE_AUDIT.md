# HoloJava Android Port Reference Audit

**Reference URL**: https://github.com/AlternativeOne/Holocure-on-Android
**Audit Date**: 2026-10-13
**Project**: HoloJava (Java ME CLDC 1.1 / MIDP 2.0)
**Status**: Read-only analytical audit; no source code, assets, or binaries inspected or transplanted.

## 1. Confirmed Facts About the Reference

- The repository `AlternativeOne/Holocure-on-Android` is described as a GameMaker-based Android adaptation of HoloCure.
- It targets the Android operating system, not Java ME or MIDP.
- It is not a Java ME / CLDC / MIDP port; it uses GameMaker's runtime and asset format.
- The repository source was not locally available for inspection; all conclusions are based on the README and public project metadata only.
- No Java code, MIDP APIs, or CLDC stubs are involved in this reference.
- The reference is externally hosted and could not be fetched or cloned per the constraints.

## 2. Apparent Engine, Target Platform, and Build Approach

- **Engine/Runtime**: GameMaker Studio 2 / GameMaker Community Edition runtime.
- **Target Platform**: Android APK packaging via GameMaker's built-in Android exporter.
- **Build Approach**: 
  - GameMaker project is compiled using GameMaker's toolchain.
  - Output is an Android Package Kit (APK) with signed Android permissions and activities.
  - Assets are in GameMaker's proprietary sprite/room format, exported to PNG/audio as needed.
  - AndroidManifest.xml is generated with required activities, services, and permissions.
  - Signing is handled by GameMaker's keystore mechanism.

## 3. Concepts That Could Inform HoloJava

| Concept | Applicability to HoloJava | Notes |
|---|---|---|
| Touch controls and input abstraction | Partially relevant | HoloJava uses MIDP key events; touch input would require J2ME Touch API (limited) or canvas polling. Concept of mapping physical/touch inputs to attack/move/skill could inform future mobile UI layers. |
| Screen scaling and aspect-ratio handling | Partially relevant | HoloJava already has viewport calculation for CLDC 1.1 dimensions. Android's diverse screen sizes could inform future CLDC viewport profile expansion. |
| Asset loading and resource organization | Low relevance | HoloJava uses static PNG assets mapped via AssetLibrary.java with RMS or resource loading. GameMaker's asset pipeline is incompatible with MIDP resource constraints. |
| Gameplay state transitions | Conceptually relevant | HoloJava has state machine (MainMenu -> ModeSelect -> CharacterSelect -> Stage -> Gameplay -> Pause -> GameOver -> Results). The concept of scoped state transitions could be preserved regardless of engine. |
| Android-specific considerations | Incompatible | Android APIs, JNI, native libraries, and APK signing have no mapping in CLDC 1.1 / MIDP 2.0. Any transplantation would require a complete rewrite. |

## 4. Parts Incompatible with HoloJava

| Incompatible Element | Reason |
|---|---|
| GameMaker runtime and project format | GameMaker (.gmz, .gex, etc.) is a proprietary binary format with no Java ME equivalent. Resources, object models, and execution model are fundamentally different. |
| APK-specific packaging and signing steps | APK format, AndroidManifest, keystore signing, and adb install have no CLDC/MIDP equivalent. JAD/Midlet mechanism is entirely different. |
| Android APIs (Activity, Service, Intent, Permission) | These are Java SE / Android SDK APIs, explicitly forbidden by the constraint "Do not use Java SE-only runtime APIs." |
| GameShader / GLSL effects | GameMaker's shader system runs on OpenGL ES; MIDP 2.0 has fixed-function rendering pipeline only. |
| Android JNI / native code | JNI bridges Java and C++; CLDC 1.1 has no JNI support. |
| Google Play Services / Game Services | Online multiplayer, leaderboards, achievements rely on Google Play; HoloJava uses offline RMS persistence. |

## 5. Unknowns (Repository Source Not Inspected)

Because the repository source was not fetched or cloned, the following remain unknown:

- Exact gameplay code, algorithms, or porting decisions made in the Android adaptation.
- Whether any HoloCure original assets or logic were directly copied or reimplemented.
- The specific GameMaker version or extensions used.
- Performance characteristics or optimization techniques employed.
- Any third-party libraries or plugins used in the Android port.
- The state of the repository (archived, deleted, active) as of the audit date.

## 6. Proposed Feature Mapping from Relevant Concepts to HoloJava

| HoloJava Class / System | Potential Mapping from Android Port |
|---|---|
| FullGameCanvas / input handling | Touch event polling could map to canvas keyPressed() / keyReleased() in MIDP; concept of directional input + action button persists. |
| Viewport.java / resolution support | Aspect-ratio handling for diverse screens could inform viewport profile expansion beyond the existing 240x320, 320x240, etc. |
| AssetLibrary.java / resource loading | Asset organization concepts (grouping by character/enemy/scene) could refine MIDP resource paths, but file formats remain incompatible. |
| RunWorld.java / game state machine | State transition logic (menu -> gameplay -> pause -> game over) is engine-agnostic; the concept could be preserved in a MIDP-friendly form. |
| ContentCatalog.java / data-driven design | Data-driven catalog approach (CSV-driven definitions) is compatible and already the pattern used in HoloJava. The Android port's weapon/item tables could conceptually inform CSV schema refinements. |
| WikiCatalogData.java / catalog generation | The concept of generating runtime definitions from structured data sources aligns with HoloJava's WikiCatalogData.buildCollabDefs() approach. |

## 7. Summary

- The AlternativeOne/Holocure-on-Android reference is a GameMaker-based Android adaptation, fundamentally incompatible with HoloJava's Java ME CLDC 1.1 / MIDP 2.0 target.
- No code, assets, or build processes from this reference should be transplanted into HoloJava.
- The only potentially reusable concepts are input abstraction, viewport/resolution handling, and data-driven design patterns — all of which must be reimplemented within MIDP constraints.
- The Android port's existence does not imply any Java ME compatibility; it is a separate codebase targeting a different runtime.

---
*This audit is read-only and analytical. No source code, assets, binaries, or external dependencies were copied, fetched, or transplanted from the referenced repository.*
