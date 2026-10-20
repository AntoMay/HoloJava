> **Archived 2026-10-20.** Extraction inventory and coverage status from
> 2026-10-13, superseded twice over: the game packages its own asset set (420
> files, inventoried in `reports/fullgame_asset_manifest.csv` and re-checked on
> every build by `tools/verify_fullgame_build.py`), and the extraction
> manifests counted below are regenerable research outputs
> (`tools/build_asset_manifest.py`) that are no longer committed to git. The
> counts remain a valid historical baseline of the *extraction*, not of the
> game's coverage.

# Content Coverage

Source of truth: extracted media inventory and HoloJava coverage status. This document summarizes coverage rather than duplicating the complete manifest.

## Inventory Baseline

Confirmed by `reports/asset_manifest.csv`, `reports/asset_manifest_summary.md`, and related extraction indexes:

- 34,422 regular files.
- 8,124 unique SHA-256 hashes.
- 463,584,164 bytes.
- 33,494 image files with parseable dimensions.
- 380 audio files, including 365 WAV and 15 OGG.
- 3,362 sprite directories.
- 8,121 extracted sprite frame files.
- 8,176 texture group entries, plus 16,302 texture page entries in the physical manifest.
- 839 collision masks.
- 480 shaders in the physical manifest.
- 46 fonts in the physical manifest.
- 7 backgrounds.
- 29 rooms.
- 81,613 localized strings.
- 26 embedded textures.
- 15 embedded images.

The baseline reflects verified files, not gameplay understanding.

## Coverage Status Definitions

- `Identified`: discovered and located in extracted output.
- `Verified`: metadata directly checked, including path, format, dimensions, or duration.
- `Selected`: listed in `config/assets.json` as a candidate.
- `Planned`: referenced in a design document or segment, pending implementation.
- `Implemented`: used by Java code.
- `Tested`: reproduced in a documented test.
- `Deferred`: intentionally postponed, with reason.
- `Unresolved`: ambiguous, undesirable, or not fully verified.
- `Not in scope`: explicitly excluded from first release or broad reconstruction.

## Inventory Summary

| Media Class | Source Count | Current HoloJava Status | Notes |
|---|---:|---|---|
| Audio files | 380 | Selected (5) | Five candidate sound effects/music assets have basic verified metadata. |
| Sprite directories | 3,362 | Selected (10) | Selection covers player, enemy, projectile, XP, effects, UI, and tile candidates. |
| Image files with dimensions | 33,494 | Selected (43 frames across selected assets) | Extracted frame dimensions are verified where used. |
| Texture group entries | 8,176 | Identified | Manifest count; no runtime integration exists. |
| Texture page entries | 16,302 | Identified | Manifest count; no runtime integration exists. |
| Collision masks | 839 | Identified | Count verified only. |
| Shaders | 480 | Identified | Physical manifest count; no translation or cancellation/approval status. |
| Fonts | 46 | Identified | Physical manifest count; candidate font references not selected. |
| Rooms | 29 | Identified | Room dimensions and instances exported. |
| Strings | 81,613 | Identified | Full string inventory exists; selective use is unstarted. |
| Embedded textures | 26 | Identified | No implementation reference yet. |
| Embedded images | 15 | Identified | No implementation reference yet. |

## Selected Candidate Summary

Tracked by `config/assets.json`:

- 15 asset keys.
- 14 keys marked `verified`.
- 1 key marked `unresolved`: `effect.hit`.
- 5 audio assets are selected.
- 1 single-frame player proxy.
- 1 single-frame enemy proxy.
- 12 XP frames.
- 6 pickup-effect frames with uniform 21×21 dimensions.
- 9 explosion frames with non-uniform dimensions.
- 10 indexed digit frames.
- 1 UI header.

## Coverage Reporting Rule

Coverage should be reported as:

`source_count / queried_items / selected / implemented / tested / deferred / unresolved / not_in_scope`

Each coverage claim must include the denominator. Coverage percentages may be computed only after `CONTENT_COVERAGE.md` records the full denominator and method. No arbitrary threshold may declare readiness.

## Asset Lifecycle Notes

- Assets are reference-only during local development.
- Candidate paths may be replaced by placeholders.
- Missing or unresolved assets must follow documented fallback behavior.
- Resource loading does not imply permission to redistribute the source file.
- Exported frame dimensions must be preserved unless a separately reviewed transformation step is documented.