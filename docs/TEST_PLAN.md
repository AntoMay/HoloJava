# Test Plan

Source of truth: test strategy, cases, and validation evidence.

See also `docs/GAMEPLAY_DESIGN.md` for required vertical-slice behavior.

## Testing Layers

1. Static/file validation.
2. Unit tests.
3. Component smoke tests.
4. Integration smoke tests.
5. Behavioral comparison tests against HoloCure reference observations.

## Planned Tests

| ID | Layer | Case | Status |
|---|---|---|---|
| T-001 | Static | Internal dimensions equal 320×240 | Planned |
| T-002 | Unit | Fixed timestep accumulates consistently | Planned |
| T-003 | Unit | Coordinate transform correctness | Planned |
| T-004 | Unit | Collision detection and resolution | Planned |
| T-005 | Unit | Damage application and death state | Planned |
| T-006 | Unit | Object lifetime and cleanup | Planned |
| T-007 | Unit | XP thresholds and level transitions | Planned |
| T-008 | Unit | Spawn timer accumulation | Planned |
| T-009 | Component | Asset loader reports missing assets | Planned |
| T-010 | Component | Audio triggers do not block simulation | Planned |
| T-011 | Integration | Application starts from packaged jar | Planned |
| T-012 | Behavioral | Movement observed against independent design criteria | Planned |

## Asset Test Notes

- Candidate asset metadata from `ASSET_USAGE.md` must be verified before field mapping to rendering components.
- Missing assets must fail gracefully.

## Current Testing Reality

No build, no Java toolchain, and no gameplay are present yet. Test execution cannot begin until Phase 3.