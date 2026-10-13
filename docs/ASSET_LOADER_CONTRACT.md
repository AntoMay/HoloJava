# Asset Loader Contract

Source of truth: rules for resolving and handling verified local HoloCure assets in a future Java implementation.

## Resolution Rules

1. Every asset lookup begins with a stable logical key, for example `pickup.xp`.
2. The loader receives the local asset root through exactly one of:
   - an explicit runtime configuration value,
   - the `HOLOJAVA_ASSET_ROOT` environment variable, or
   - the default project-relative path from `config/assets.json`.
3. Relative paths from `config/assets.json` are appended to the configured root.
4. Paths are not hard-coded to a user home directory.
5. Missing roots, missing keys, or missing files must produce an error containing the key and the attempted relative path.
6. Single-file image assets require `path`.
7. Frame-sequence assets require `frame_pattern` and a complete `indices` list (or an equivalent explicit manifest).
8. Relative file names may be discovered only if they are listed by configuration, not by broad directory scans during gameplay.

## Runtime Responsibilities

The proposed runtime has four separate responsibilities:

1. Metadata definition: `config/assets.json` and loader contracts.
2. Resource loading: reading and decoding a file into a runtime representation.
3. Animation playback: selecting frame indices over time.
4. Rendering: displaying a decoded image.

These responsibilities must remain separate. Metadata must not know about Java objects, and rendering must not decode files.

## Resource Ownership

- Each scene owns the resources that it loads.
- A scene starts by loading only required asset keys.
- A scene releases scoped resources when it exits, even on error.
- No asset is copied into the HoloJava project unless an explicit later task approves it.
- Audio handles and image buffers have separate close/dispose paths and do not share lifetime assumptions.

## Frame Sequence Handling

1. All frame entries must be validated before playback starts.
2. A missing frame aborts loading for that asset.
3. Exporter filename order is only file-order evidence, not proof of animation semantics.
4. Animation timing and semantics require later validation before use in gameplay.
5. A multi-frame key must store frame count, indices, and dimensions when verified.

## Variable-Size Frame Handling

- `effect.explosion` currently exports to non-uniform dimensions and must not be silently resized, cropped, or padded during load.
- Rendering code must either:
  - preserve each source rectangle at its original pixel dimensions, or
  - defer integration until its anchor, origin, and intended scale are validated.
- If normalization is ever added, it must be a documented preprocessing step separate from loading.

## Audio Handling

- Audio file keys store format, sample rate, channels, and duration metadata when verified.
- Audio loading must identify format before playback.
- Missing or unreadable audio must degrade with a clear error or a documented silent fallback.
- Audio playback must not block simulation.

## Testable Contract Rules

1. `assets.json` parses successfully.
2. Every non-unresolved `path` and frame index exists relative to a configured test root.
3. Unknown keys fail with key-specific errors.
4. Missing files fail with relative-path-specific errors.
5. Unresolved keys are returned as explicit loader failures or placeholders.
6. Variable-sized frame arrays preserve measured dimensions.
7. Multi-frame keys are not accepted if they only include a count and no frame list or pattern.

## Local Asset Root Setup

Preferred configuration order:

1. Explicit application configuration.
2. `HOLOJAVA_ASSET_ROOT`.
3. Project-relative default.

The root value should be normalized to an absolute path at runtime but must not be embedded permanently in reusable JSON.

## Variable-Sized and Uncertain Assets

The first-slice candidate `effect.explosion` must retain its mixed frame metadata. It is allowed for cataloging but not for use in uniform-size assumptions. Any integration code that assumes uniform dimensions is invalid until this asset is replaced or annotated with anchors.