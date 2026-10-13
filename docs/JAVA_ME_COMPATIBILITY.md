# Java ME / J2ME Compatibility

Source of truth for Java ME/J2ME runtime assumptions and compatibility limitations.

## Compatible Target Architecture

HoloJava targets Java ME-compatible MIDlets, not a Linux desktop Java game. The provisional baseline candidate is:

- **Configuration:** CLDC 1.1
- **Profile:** MIDP 2.0
- **Host platform:** 320×240 logical resolution
- **Primary emulator:** J2ME Loader on Android
- **Secondary target:** older Nokia and other Java ME feature phones that support the same profile and required capabilities

This baseline is provisional. It is supported by compatibility knowledge but not by an actual HoloJava build in this repository.

## Compatibility Tiers

| Tier | Target | Status | Notes |
|---|---|---|---|
| Tier 1 | J2ME Loader on Android | Candidate primary runtime | Strong support for MIDP games in general, but settings and key mappings differ by device and game profile. |
| Tier 2 | Nokia feature phones with CLDC 1.1 and MIDP 2.0 | Candidate secondary target | Highly version- and device-specific; software listings, key layout, heap size, audio and image support vary. |
| Tier 3 | Other CLDC/MIDP feature phones | Exploratory | Must be considered individually by profile, heap, screen dimensions, and vendor UI APIs. |

No claim of universal Nokia compatibility is made. Device models differ in screen size, keypad layout, Java ME profile, heap limits, media support, vendor APIs, and runtime behavior.

## Evidence Labels

- `Confirmed by documentation`: stated in reliable reference material.
- `Inferred but unverified`: derived from general Java ME knowledge, not demonstrated for HoloJava.
- `Runtime-tested by us`: not applicable yet.
- `Unresolved`: no safe determination.

HoloJava has no Java ME build result yet.

## Platform Baseline

| Capability | Baseline | Status |
|---|---|---|
| Configuration | CLDC 1.1 | Provisional |
| Profile | MIDP 2.0 | Provisional |
| Game API | `javax.microedition.lcdui.game` | Candidate |
| Media API | `javax.microedition.media` | Candidate; device-dependent |
| Persistent storage | RMS | Candidate; size/device-dependent |
| Graphics | 16-bit `Graphics`, `Image` | Candidate |
| Input | `Canvas` key codes and game actions | Candidate |
| Networking | not required for vertical slice | Candidate only |

## Logical Rendering Rules

The internal game coordinate system is exactly 320×240. Physical screen dimensions are not assumed to match.

Rendering must:

- Keep the logical coordinate system at 320×240.
- Render integer coordinates within that logical frame.
- Query physical `Canvas` width and height at runtime.
- Preserve aspect ratio when mapping the logical frame to the physical display.
- Use letterboxing or pillarboxing where the physical screen is not 4:3.
- Map pointer/touch coordinates back from physical pixels to logical coordinates.
- Handle physical landscape and portrait displays without changing logical game coordinates.
- Avoid silently cropping gameplay-critical content.
- Avoid stretching the logical frame arbitrarily to fill the display unless separately approved.

On a smaller display, the entire 320×240 frame should be scaled down proportionally or letterboxed. Gameplay-critical areas must not be clipped simply because a smaller display exists.

## Input Rules

- Use `Canvas.keyPressed(int keyCode)`, `keyPressed`/`keyRepeated`, and game actions where available.
- Standard numeric keys are `Canvas.KEY_NUM0` through `KEY_NUM9`, `KEY_STAR`, and `KEY_POUND`.
- Directional game actions are `Canvas.LEFT`, `RIGHT`, `UP`, `DOWN`, and `FIRE`.
- Some devices map directional or action keys differently; `getKeyName()` and `getGameAction()` should be used for diagnostics but should not be assumed correct on every device.
- Pointer events are optional and are not required for the first vertical slice.
- Any touch input would be used only for diagnostics or later authorized scope.

## Image Format Support (MIDP Evidence)

`Confirmed by documentation`:
- MIDP requires PNG support for `Image.createImage`.
- PNG critical chunks are required.
- MIDP devices must use conformant PNG handling.

`Device- and vendor-dependent`:
- JPEG is not required by MIDP, and some MIDP implementations may support it.
- Alpha/transparency behavior can vary, especially with translucent PNGs.
- Image decoding small images is usually acceptable, but larger mixed-size frame sets may consume memory quickly. Candidate images such as `effect.explosion` retain non-uniform dimensions and must not be treated as a uniform sprite array.
- No asset is copied into the project in this phase.

## Audio Support (MIDP Evidence)

`Confirmed by documentation`:
- MIDP 2.0 provides the Mobile Media API (`javax.microedition.media`, `javax.microedition.media.control`).

`Device- and vendor-dependent`:
- Tone generation may be more reliable than sampled formats on older devices.
- WAV, MIDI, MP3 and other formats depend on device support and installed software.
- Sampled audio can consume heap quickly; durations should be treated conservatively.
- The candidate audio files from `config/assets.json` have verified format and duration metadata only. That metadata does not prove runtime support on any phone or emulator.

## Memory and Performance Constraints

General Java ME devices are constrained by:

- Limited heap size and max JAR size.
- Strict startup and rendering latency requirements.
- Pointer Input limitations and nonblocking requirements.
- Slow class and image loading.
- Per-device screen dimensions.

HoloJava design must therefore:

- Avoid large frame allocations.
- Avoid assuming entire extracted asset sets can be loaded.
- Load only candidate keys.
- Keep simulation logic simple.
- Track allocations and disposal in future implementation planning.
- Keep rendering diagnostics outside of gameplay logic.

## J2ME Loader Notes

J2ME Loader is treated as the primary emulator runtime for testing. It supports many MIDP games, scaling, virtual input, and settings. Its compatibility should not be assumed equal to OEM behavior. J2ME Loader settings, key mappings, and audio behavior may differ from Nokia or other target feature phones.

A smoke test should use:
1. A small preverified JAR/JAD.
2. A simple diagnostic canvas.
3. A 320×240 logical coordinate test pattern.
4. Artifact-size and launch checks.

No gameplay may be implemented as part of that smoke test.

## Nokia Compatibility Notes

Second-tier support is exploratory. Sources and tools differ by Nokia software platform. Some platforms advertise MIDP 2.0 while still having vendor-specific resource sizes, orientation, audio, and key behavior. The Nokia UI API is a vendor extension and should be treated as optional. No Nokia hardware has been tested in this phase because no runtime scaffold has been created.

## Unresolved Compatibility Items

- Which exact Java ME APIs a minimum MIDlet can rely on.
- Actual profile limits on the chosen J2ME Loader version.
- Whether audio formats used by candidate assets play on a typical target.
- Maximum sprite and frame dimensions that fit within safe heap limits.
- Actual physical-to-logical scaling quality on target displays.
- Any Nokia-specific limitations.
- Whether ProGuard or another preverification route will successfully process intended bytecode.