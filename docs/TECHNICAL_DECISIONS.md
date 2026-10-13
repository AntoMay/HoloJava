# Technical Decisions

Source of truth: architecture decisions and their rationale.

| ID | Decision | Status | Rationale |
|---|---|---|---|
| TD-001 | Use Java 21 LTS as eventual target runtime | Drafted | Modern LTS Java baseline |
| TD-002 | Use Gradle with wrapper | Drafted | Reproducible standard build workflow |
| TD-003 | Use LibGDX instead of Java2D | Drafted | Viewport, asset, audio, and input support |
| TD-004 | Fixed internal resolution 320×240 | Accepted by roadmap | Explicit project requirement |
| TD-005 | Keep simulation logic independent of renderer types | Accepted by roadmap | Testability and maintainability |
| TD-006 | Extracted assets remain local-only pending license review | Accepted by roadmap | Legal caution |
| TD-007 | Mechanisms require evidence labels | Accepted by roadmap | Prevent speculative reverse engineering |
| TD-008 | Avoid custom framework until concrete need | Accepted by roadmap | Reduce complexity |
| TD-009 | Placeholder mechanics marked temporary | Accepted by roadmap | Prevent false reconstruction claims |
| TD-010 | Original/extraction outputs treated as read-only | Accepted by roadmap | Preserve evidence integrity |

## Pivot and Asset-Resolution Decisions

| ID | Decision | Rationale |
|---|---|---|
| TD-011 | Recoverable YYC algorithm is no longer an implementation gate | Original source is not available |
| TD-012 | Game systems are independently designed | Enables implementation without full reverse engineering |
| TD-013 | Placeholder and candidate assets remain local references | Avoid asset duplication and unlicensed redistribution |
| TD-014 | Gameplay parameters are configurable | Simplifies balancing without changing engine code |
| TD-015 | First playable slice is a minimal vertical loop | Controls scope and validates architecture |
| TD-016 | Asset keys resolve through configured relative roots | Avoids hard-coded home-directory paths |
| TD-017 | Asset metadata, loading, playback, and rendering remain separate | Permits deterministic tests and easier resource replacement |
| TD-018 | Variable-sized unverified frames are not normalized during load | Prevents silent source mutation |

## Platform and Compatibility Decisions

Target platform has changed from the earlier provisional desktop plan to Java ME/J2ME-compatible output. The previous Java 21/libGDX assumptions are compatibility constraints, not active implementation direction.

| ID | Decision | Status | Rationale |
|---|---|---|---|
| TD-019 | Target a Java ME/J2ME-compatible runtime, not Linux or desktop Java game frameworks | Proposed / pending toolchain verification | Required by the 320×240 feature-phone and J2ME Loader compatibility goal |
| TD-020 | Provisional compatibility baseline is CLDC 1.1 plus MIDP 2.0 | Provisional | Best common baseline for J2ME Loader and many older phones; still must be verified against target emulators and devices |
| TD-021 | Keep Java SE JDK 21 only as a build-host compiler candidate, not the runtime | Provisional | JDK 21 is available as a Debian package candidate but cannot directly express the J2ME runtime or preverification |
| TD-022 | Linux is the development and build environment only; JAR/JAD is the portable runtime artifact | Accepted for planning | Avoids committing to a desktop runtime |
| TD-023 | Original and extracted assets remain untouched during compatibility/toolchain work | Accepted by roadmap | Preserves evidence and avoids unlicensed integration |
| TD-024 | Use 320×240 as the logical coordinate system, with scaling for physical screens | Accepted | Single portable game coordinate system |
| TD-025 | JAR and JAD manifest requirements must be verified against the selected toolchain | Unverified | MIDlet-Jar-URL, MIDlet-Name, microedition-configuration, microedition-profile, and preverification all depend on toolchain details |

## Superseded Assumptions

- `TD-003`: libGDX desktop support is no longer the target runtime architecture.
- `TD-001`: Java 21 is not the target runtime; it is only a build-host candidate unless later revised with evidence.
- All broader JDK-class libraries, desktop game APIs, and Linux windowing are excluded from runtime design.


## Simplified Direction Decisions (2026)

| ID | Decision | Status | Rationale |
|---|---|---|---|
| TD-026 | Primary runtime target is J2ME Loader on Android | Provisional working | Simplifies build and iteration |
| TD-027 | Nokia compatibility is a secondary target only | Provisional working | Avoids per-model compatibility research from blocking planning |
| TD-028 | Assume MIDP 2.0/LCDUI baseline APIs are portable to both targets, including `Canvas`, `Graphics`, `Image`, `Display`, and `Displayable`, unless conflicting evidence emerges | Asumsi kerja | Reduces API surface while retaining fallback caveats |
| TD-029 | Internal logical resolution remains 320×240 with aspect-preserving scaling | Accepted | Single coordinate model |
| TD-030 | Buttons and key state are the primary input; pointer and audio are optional | Accepted | Four-way key mapping is more universal on tested device families |
| TD-031 | No `com.nokia.mid.ui`, `DeviceControl`, `Sound`, or `DirectGraphics` in the scaffold or initial prototype | Accepted planning constraint | Avoid vendor lock-in until a genuine requirement exists |
| TD-032 | Broad phase-game and per-device API research is paused except for implementation blockers | Accepted planning constraint | Prevents scope creep |
| TD-033 | Use only one shortest verifiable toolchain path: OpenJDK 21 build host, ProGuard 6.2.2 preverification, plus one approved external CLDC 1.1/MIDP 2.0 API jar set under version pin and license controls | Provisional | Reduces toolchain search to a single auditable path |


## Change Procedure

Any change to an accepted decision must record:

- Decision ID.
- Old value.
- New value.
- Reason.
- Impacted files or phases.
- Date and approval.
EOF