# Asset Loader Test Plan

Source of truth: planned Java-side asset-loader test outline. These tests are not yet executable because no Java project exists. They are planning artifacts only.

This plan follows `docs/ASSET_LOADER_CONTRACT.md`, `config/assets.json`, `docs/ASSET_USAGE.md`, and `Roadmap.md`.

## Root Configuration and Resolution Rules

Root resolution precedence:

1. An explicit application configuration override.
2. The environment variable `HOLOJAVA_ASSET_ROOT`, only if it is non-empty.
3. The configured relative default from `config/assets.json`, typically relative to the project root.

Invalid explicit override must fail fast. The loader must not silently ignore an invalid explicit override and fall back to a default root.

Paths are always resolved against the selected asset root and reported relatively where possible.

## Test Case Catalog

### T-AR-001 Default root accepts a valid extraction root

- Category: root configuration
- State: planned
- Setup: Set no explicit override and no `HOLOJAVA_ASSET_ROOT`; run from the project directory.
- Expected result: loader selects the configured project-relative default.
- Failure diagnostic: `AssetRootNotConfigured` or `AssetRootNotFound` with the configured default path.

### T-AR-002 Environment root override takes precedence

- Category: root configuration
- State: planned
- Setup: Set `HOLOJAVA_ASSET_ROOT` to a valid temporary fixture directory. Set no explicit override.
- Expected result: loader uses the environment root.
- Failure diagnostic: an environment-root-aware loader that still reads the default root.

### T-AR-003 Explicit configuration overrides environment

- Category: root configuration
- State: planned
- Setup: Provide an explicit valid root and an environment variable pointing to a different valid root.
- Expected result: explicit configuration root wins.
- Failure diagnostic: use of environment root when an explicit root is supplied.

### T-AR-004 Absolute root path is supported

- Category: root configuration
- State: planned
- Setup: Use an absolute temporary root.
- Expected result: root is used unchanged.
- Failure diagnostic: path reinterpreted as project-relative or path normalization error.

### T-AR-005 Relative root path is supported

- Category: root configuration
- State: planned
- Setup: Use a project-relative temporary root.
- Expected result: root is resolved relative to the documented project root.
- Failure diagnostic: missing files despite valid root, or inconsistent separator handling.

### T-AR-006 Invalid explicit override fails fast

- Category: root configuration
- State: planned
- Setup: Provide a non-existent explicit root.
- Expected result: load fails during root validation; default root is not used.
- Failure diagnostic: `AssetRootNotFound(root=...)`.

### T-AR-007 Environment override with spaces is preserved

- Category: root configuration
- State: planned
- Setup: Set `HOLOJAVA_ASSET_ROOT` to a valid path containing a space.
- Expected result: path is preserved literally.
- Failure diagnostic: truncated or altered root path.

### T-AR-008 Root exists but asset is missing

- Category: root and asset resolution
- State: planned
- Setup: Use an existing empty or partial extraction root.
- Expected result: root is accepted; subsequent requests for required assets fail with key and relative path.
- Failure diagnostic: generic root failure that hides the missing asset.

## Asset-Key Resolution

### T-AL-001 Resolve every currently defined key

- Category: key and config
- State: planned
- Setup: Configure a valid extraction root.
- Expected result: every key currently listed as `verified: true` passes path existence and declared-metadata checks.
- Failure diagnostic: specific key and relative path.

### T-AL-002 Unknown key

- Category: key and config
- State: planned
- Setup: Request an unlisted key.
- Expected result: immediate loader error naming the unknown key.
- Failure diagnostic: obscure null pointers or silent empty results.

### T-AL-003 Known but unresolved key

- Category: key and config
- State: planned
- Setup: Request `effect.hit`.
- Expected result: loader reports the key as unresolved and does not treat it as a successfully loaded original asset.
- Failure diagnostic: missing path error without unresolved classification, or false success.

### T-AL-004 Missing single-file asset

- Category: key and path
- State: planned
- Setup: Temporarily use a fixture root where a single-file asset is missing.
- Expected result: error naming the key and relative path.
- Failure diagnostic: successful load of a surrounding or unrelated file.

### T-AL-005 Missing frame in sequence

- Category: key and path
- State: planned
- Setup: Remove one frame from `pickup.xp` in a fixture directory.
- Expected result: request fails during sequence validation before gameplay use.
- Failure diagnostic: error naming key, frame pattern, missing index, and relative path.

### T-AL-006 Invalid catalog format

- Category: key and config
- State: planned
- Setup: Malform the asset JSON or omit a required field.
- Expected result: structured configuration error.
- Failure diagnostic: ambiguous JSON failure or late runtime exception.

### T-AL-007 Duplicate logical keys

- Category: key and config
- State: planned
- Setup: Construct a fixture catalog that would contain duplicate keys if supported by the format.
- Expected result: loader rejects ambiguity or defines deterministic behavior explicitly.
- Failure diagnostic: first or last entry silently selected.

### T-AL-008 Unsupported entry format

- Category: key and config
- State: planned
- Setup: Insert an entry with neither a `path` nor a valid `frame_pattern`.
- Expected result: configuration validation error.
- Failure diagnostic: attempt to instantiate a non-image or unusable asset.

## Path Safety and Portability

### T-PS-001 Resolve relative paths against root

- Category: path safety
- State: planned
- Setup: Request a valid key under a fixture root.
- Expected result: full effective path is inside the selected root.
- Failure diagnostic: resolver emits a path not under root, or key resolution ignores root.

### T-PS-002 Reject traversal

- Category: path safety
- State: planned
- Setup: Catalog entry or fixture attempts `../` traversal.
- Expected result: loader rejects the entry or resolved target before read.
- Failure diagnostic: file read outside root despite validation error.

### T-PS-003 Platform separator handling

- Category: path safety
- State: planned
- Setup: Run analogous path tests on the target platform.
- Expected result: relative sections are interpreted using platform-native semantics.
- Failure diagnostic: separator or escaping error.

### T-PS-004 No source mutation

- Category: path safety
- State: planned
- Setup: Load assets under a fixture root; record checksums before and after.
- Expected result: no original files are moved, copied, resized, cropped, padded, or normalized.
- Failure diagnostic: changed fixture file or destructive operation during loading.

## Frame Sequences and Image Metadata

### T-IF-001 Complete contiguous sequence

- Category: frames
- State: planned
- Setup: Use a fixture or selected asset with complete frames.
- Expected result: all declared indices resolve.
- Failure diagnostic: partial frame list while reporting success.

### T-IF-002 Missing frame

- Category: frames
- State: planned
- Setup: Omit one index from a temporary root while the catalog still declares it.
- Expected result: sequence load fails with missing index and path.
- Diagnostic: frame count initially appears valid but later playback references a missing path.

### T-IF-003 Non-contiguous declared indices

- Category: frames
- State: planned
- Setup: Define a valid sequence with non-consecutive indices, if catalog design permits.
- Expected result: all and only declared indices are loaded in manifest order.
- Failure diagnostic: implicit count-based fallback that omits declared indices.

### T-IF-004 Deterministic file-order handling

- Category: frames
- State: planned
- Setup: Use known frame files.
- Expected result: repeated runs yield the same frame list.
- Failure diagnostic: filesystem-order dependence or unsorted loading.

### T-IF-005 Single image versus frame sequence

- Category: frames
- State: planned
- Setup: Compare one single-frame entry and one multi-frame entry.
- Expected result: metadata and loader APIs distinguish the two without ambiguity.
- Failure diagnostic: both treated identically and frame access fails.

### T-IF-006 Mixed dimensions are preserved

- Category: frames
- State: planned
- Setup: Use `effect.explosion`, whose observed exported frames are 24×24, larger sizes, and 1×1.
- Expected result: each frame retains its measured dimensions.
- Failure diagnostic: use of the first frame's size for all frames or explicit resizing.

### T-IF-007 No semantic ordering inferred from filenames alone

- Category: frames
- State: documentation
- Setup: Review all test specifications.
- Expected result: every frame sequence test explicitly labels animation meaning and anchors as unverified.
- Failure diagnostic: catalog or test assumes exported filename order proves intended animation.

## Resource Lifecycle

### T-RL-001 Successful load and resolve

- Category: resource lifecycle
- State: planned
- Setup: Load a valid single-image key.
- Expected result: resource handle is usable and represents the selected file.
- Failure diagnostic: path-only success without decoded handle.

### T-RL-002 Repeated access to same key

- Category: resource lifecycle
- State: planned
- Setup: Request the same resolved key more than once.
- Expected result: contract specifies whether cached or newly loaded resources are returned.
- Failure diagnostic: repeated file loading without owning context, or dual ownership ambiguity.

### T-RL-003 Partial load failure

- Category: resource lifecycle
- State: planned
- Setup: Trigger a frame-sequence failure while some images are already loaded.
- Expected result: no partial runtime resource escapes inconsistent ownership; error identifies failed item.
- Failure diagnostic: half-loaded resource escaping to scene state.

### T-RL-004 Dispose owned resources

- Category: resource lifecycle
- State: planned
- Setup: Load and then dispose a scene's owned resources.
- Expected result: handles are released and unavailable for later use.
- Failure diagnostic: use-after-dispose or resource leak.

### T-RL-005 Do not dispose externally owned resources

- Category: resource lifecycle
- State: planned
- Setup: Configure loader to load using a shared context or borrowed resource.
- Expected result: implementation must know and document whether it owns the loaded resource.
- Failure diagnostic: double disposal or cross-subsystem ownership conflict.

### T-RL-006 Audio resources follow audio ownership

- Category: resource lifecycle
- State: planned
- Setup: Load and dispose audio keys.
- Expected result: audio resources are released separately from image resources.
- Failure diagnostic: audio buffers managed as image textures or no disposal path.

## Synthetic Fixture Tests

These can validate configuration, resolution, path safety, and sequence behavior without the full HoloCure extraction. They should use small temporary directories created by each test and cleaned up afterward.

### T-FX-001 Missing key and missing file

- Category: fixture
- State: executable in principle now, later in code
- Setup: Create a minimal fixture root with one image and a catalog that references it.
- Expected result: missing catalog entry or missing path fails with precise error. No access to full extraction is required.
- Failure diagnostic: test depends on original paths or fixture content is mutated.

### T-FX-002 Incomplete sequence

- Category: fixture
- State: executable in principle now, later in code
- Setup: Create a two-frame sequence with one middle frame absent.
- Expected result: loading fails during frame validation.
- Failure diagnostic: sequence reported complete while an index is missing.

### T-FX-003 Variable-sized fixture

- Category: fixture
- State: executable in principle now, later in code
- Setup: Create two small PNG files with different dimensions.
- Expected result: both dimensions are recorded and preserved.
- Failure diagnostic: implicit resize or dimension override.

### T-FX-004 Path traversal fixture

- Category: fixture
- State: executable in principle now, later in code
- Setup: Attempt to load a catalog entry containing traversal path segments.
- Expected result: rejection before read.
- Failure diagnostic: successful read outside fixture root.

### T-FX-005 Root override fixture

- Category: fixture
- State: executable in principle now, later in code
- Setup: Provide two temporary roots and switch override.
- Expected result: asset requests resolve against the selected root only.
- Failure diagnostic: first root's data remains visible after switching.

## Current Executability Classification

| Group | Executable Now Without Java | Must Wait for Phase 3 Java |
|---|---|---|
| JSON syntax and key validation | Yes, using Python or a text parser | Later duplicated as automated unit tests |
| File existence relative to a real or synthetic root | Yes | Later duplicated as automated unit tests |
| Relative path resolution logic | No | Requires implementation tests |
| Frame loading and dimension preservation | No | Requires image decoder tests |
| Resource lifecycle and disposal | No | Requires runtime tests |
| Audio format duration checks | Yes, using ffprobe | Later duplicated as automated unit tests |

No test in this plan is currently executed against Java implementation because no build system or source tree exists.

## Open Design Decisions

1. The exact Java loader API is not yet specified.
2. The cache/reuse policy for identical keys is not yet decided.
3. The canonical default root from `config/assets.json` needs a future policy equivalent to project-relative and worktree-independent behavior.
4. The semantics of correction frame count versus manifest length remain open.
5. HoloJava must choose whether partial sequence loads are permitted or prohibited; this plan requires failure to preserve consistency.
6. Audio ownership and disposal must be mapped to the selected libGDX backend capabilities during Phase 3.
7. Animation anchors for mixed-size frames must be validated before gameplay rendering.