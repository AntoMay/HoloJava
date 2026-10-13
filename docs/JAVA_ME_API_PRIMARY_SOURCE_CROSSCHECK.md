# Cross-Check of Primary Sources: Java ME / J2ME APIs for HoloJava

This document audits the prior research report:

- `docs/JAVA_ME_NOKIA_320X240_VENDOR_API_RESEARCH.md`

and compares it with primary and near-primary Java ME and Nokia sources, including the currently approved HoloJava documentation:

- `docs/JAVA_ME_COMPATIBILITY.md`
- `docs/TOOLCHAIN_READINESS.md`
- `docs/TECHNICAL_DECISIONS.md`
- `docs/PROJECT_STATUS.md`
- `Roadmap.md`

No installation, source code, build, JAR/JAD creation, asset modification, configuration change, or gameplay work was performed.

## 1. Source Authority and Labels

The following status labels are used:

- `Confirmed`: a primary source directly supports the claim.
- `Partially confirmed`: part of the claim is supported, but the broader conclusion remains unsupported.
- `Unverified`: no suitable source was found, or the claim exceeds available evidence.
- `Contradicted`: a reliable source directly conflicts with the claim.

Source classes:

- `Primary official source`: Oracle/Sun MIDP 2.0 documentation or JCP specification pages.
- `Preserved vendor mirror`: a preserved copy of Nokia Java documentation that may derive from official Nokia documentation, but is not served by official Nokia infrastructure.
- `Platform or device database`: credible but non-primary reference, useful only as supporting evidence.
- `Secondary source`: blog, wiki, forum, or community database; cannot prove implementation.

Primary and core sources used:

- Oracle MIDP 2.0 `Canvas` API:  
  `https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/lcdui/Canvas.html`
- Oracle Java for Mobile Devices documentation:  
  `https://docs.oracle.com/javame/mobile/mobile.html`
- JCP JSR 118, Mobile Information Device Profile 2.0:  
  `https://jcp.org/en/jsr/detail?id=118`
- JCP JSR 139, Connected Limited Device Configuration 1.1:  
  `https://jcp.org/en/jsr/detail?id=139`
- JCP JSR 135 detail, Mobile Media API:  
  `https://jcp.org/en/jsr/detail?id=135`
- Oracle MMAPI technical article:  
  `https://www.oracle.com/technical-resources/articles/javame/mobile-media-api.html`
- Archived Nokia UI API 1.1 package and `DeviceControl` documentation:  
  `https://nikita36078.github.io/J2ME_Docs/docs/Nokia_UI_API_1_1/com/nokia/mid/ui/package-summary.html`  
  `https://nikita36078.github.io/J2ME_Docs/docs/Nokia_UI_API_1_1/com/nokia/mid/ui/DeviceControl.html`

## 2. Core Java ME API Cross-Check

| Claim | Primary source | Verified content | Status | HoloJava consequence |
|---|---|---|---|---|
| `Canvas`, `Graphics`, `Image`, `Display`, and `Displayable` are the correct core game UI types | Oracle MIDP 2.0 `Canvas` and JCP JSR 118 | The `Canvas` class is documented for low-level event handling and graphics calls; this is the MIDP 2.0 way to implement a game | Confirmed at MIDP 2.0 level | Core can rely on these types only after the real target is known to be MIDP 2.0 |
| Portable movement/game controls should use game actions instead of raw key codes | Official MIDP 2.0 Canvas docs | "`Portable applications should use game actions instead of key codes.`" | Confirmed | Continue normalizing `getGameAction()`; raw key codes must remain fallback only |
| Pointer input may not exist | Official MIDP 2.0 Canvas APIs | `hasPointerEvents()` and `hasPointerMotionEvents()` explicitly check device capabilities | Confirmed | Pointer must stay optional and never become the only control path |
| Fullscreen mode is a MIDP 2.0 feature that may still leave UI indicators visible | Official MIDP 2.0 `setFullScreenMode(boolean)` / `sizeChanged(int, int)` | `setFullScreenMode` is documented with `Since: MIDP 2.0`; a change from normal to fullscreen may trigger `sizeChanged` | Confirmed | Rendering must measure `getWidth()` and `getHeight()` rather than assume 320×240 physical output |
| Fullscreen might not free all input keys | Official MIDP 2.0 Canvas document | In normal mode, key-code events may overlap commands; implementation details depend on device mode | Confirmed | Key mapping and command placement must be tested before game tuning |
| OpenGL/3D should not be in the core | JSR 118 scope and project decisions | JSR 118 is centered on LCDUI capabilities and common mobile devices, not reliance on later high-level graphics JSRs | Confirmed as scope alignment | Keep optional JSRs outside the core unless separately justified |
| CLDC 1.1 remains broadly suitable but CLDC 1.0 compatibility is separate | JCP JSR 139 | CLDC 1.1 is backward-compatible with CLDC 1.0 and was created to improve VM compliance with tight footprint targets | Confirmed at specification level | Core logic must use integer operations; do not assume floating point universally |
| Persistent storage is optional for the first vertical slice | Official MIDP 2.0 and prior docs, JCP JSR 118 | `RecordStore` is not listed as a mandatory gameplay dependency | Confirmed | Keep persistence outside the core vertical slice until approved |

## 3. Nokia UI Cross-Check

| Claim | Source | Supporting evidence | Status | Notes |
|---|---|---|---|---|
| The package contains graphics extensions for low-level UI | Preserved Nokia UI 1.1 archive | `com.nokia.mid.ui` is titled graphics-related extensions to LCDUI; `DirectGraphics`, `DirectUtils`, `FullCanvas` are listed | Partially confirmed | The preserved JavaDoc is credible, but the host and preservation chain must be acknowledged as a copied archive |
| `FullCanvas` is deprecated in MIDP 2.0+ devices | Preserved Nokia `FullCanvas` / `Canvas` docs | `FullCanvas` constructor and `addCommand` / `setCommandListener` docs explicitly say deprecated in favor of `Canvas` MIDP 2.0 equivalents | Confirmed in preserved document | Do not use `FullCanvas` as the HoloJava base class; standard `Canvas.setFullScreenMode` is the portable core |
| `DirectGraphics` provides advanced graphics operations | Preserved Nokia `DirectGraphics` docs | Polygon, triangle, rotate/flip image, alpha-related drawing, raw pixel data are described | Partially confirmed | Existence and documented API are clear; runtime capability on each firmware is not documented |
| `DeviceControl` can probe backlight behavior | Preserved Nokia `DeviceControl` docs | `setLights` uses num/level ranges; failures use `IllegalArgumentException` and `IllegalStateException` when unsupported | Confirmed in preserved document | Runtime detection and graceful fallback are mandatory |
| `DeviceControl` Flashing/vibration methods are deprecated in MIDP 2.0+ | Preserved Nokia `DeviceControl` docs | `flashLights` and `startVibra`/`stopVibra` state replacement by `Display.flashBacklight` and `Display.vibrate` | Confirmed in preserved document | Standard MIDP methods should be the first implementation choice |
| `com.nokia.mid.sound` is an optional sound extension widely available across Nokia devices | Prior research inference | A clear item-level primary source for a universal support matrix was not located in this audit | Unverified | Treat as unknown/adapter-only, not baseline |
| Nokia UI API is present on every QVGA Nokia S40 device | Prior research/device lists | No exact per-model evidence was found in the sources reviewed | Unverified | Cancel this universal claim; platform lists are not proof of per-model API implementation |

## 4. Optional Package and Input Behavior

| Claim | Source | What primary docs state | Status | Notes |
|---|---|---|---|---|
| MMAPI is optional rather than required by MIDP | Oracle MMAPI doc and JSR 135 | “optional package”; “no particular transport or format mandatory in the specification” | Confirmed | Audio remains an enhancement with tone/silence fallback |
| MMAPI may exist at different package/API versions | JCP JSR 135 and J2ME common references | Standard optional package; platform security cautions not part of this audit | Confirmed | Profile B audio requires adapter plus validation at runtime |
| Device pointer events are not guaranteed | Official MIDP 2.0 Canvas docs | `hasPointerEvents()` and `hasPointerMotionEvents()` exist for explicitly checking support | Confirmed | Keep pointer input supplementary; controls must be operable from keypad game actions |
| Input key semantics are device-dependent | MIDP 2.0 Canvas prosaic text | Key codes can vary; portable applications should rely primarily on game actions and avoid direct key code dependence | Confirmed | `KeyMapper` should map game actions to HoloJava abstract actions |
| Fullscreen may still include status UI or network activity | Official MIDP 2.0 Canvas docs | Normal mode may show command labels/title/ticker while fullscreen still may be left with network indicator requirement | Confirmed | Logical 320×240 should include margin/safe-zone planning |

## 5. Device Cross-Check

| Previous claim | Current best source class | Status | Consequence for HoloJava |
|---|---|---|---|
| “Series 40 3rd Edition introduced QVGA 240×320” | Platform-generation Wikipedia and device database | Partially confirmed | Good enough for planning; not enough for device support claims |
| “Nokia 6300 runs S40 3rd FP2 and QVGA” | Nokia 6300 Wikipedia and Nokia wiki | Partially confirmed | Supports model-family selection only; does not prove API/runtime behavior |
| “Nokia 5300 / 7373 same platform family” | Series 40 device database and prior report | Partially confirmed / inferred | Same platform family, but per-device heap and firmware varies |
| “All 320×240 S40 targets have Nokia UI extension” | None found | Unverified | Treat all vendor extensions as unknown |
| “MMAPI audio works for `sounds/*.wav` and `*.ogg`” | Device not available + no runtime test; asset metadata only | Unverified | Metadata verification is not runtime evidence |
| “Heap and JAR size safe under a chosen implementation” | No device docs found | Unverified | No fixed claim may be made until build/device-specific evidence exists |
| “Orientation and scaling work transparently on physical hardware” | No target hardware tested | Unverified | Continue designing abstract scaling and orientation policy, then test later |

## 6. Corrections to the Prior Research Report

1. **Core LCDUI conclusion remains valid.**  
   The previous report's recommendation still stands: MIDP 2.0 `Canvas`/`Graphics`/`Image`/`Display` is the safest mandatory core if the target is confirmed to be MIDP 2.0.

2. **Pointer should be treated as conditional, not assumed.**  
   This point was correctly separated; no change required.

3. **Fullscreen recommendation is partly confirmed.**  
   Keep fullscreen as an optional request with `sizeChanged` response, not a guaranteed full-output surface.

4. **`FullCanvas` deprecation is well documented in the preserved doc.**  
   Cross-check confirms the replacement relation. Availability is still another question and must not be assumed universal.

5. **Sound extension is no longer an engineering input claim.**  
   Treat it as unknown and optional-vendor only.

6. **Model-specific API tables must be downgraded.**  
   No current 320×240 device entry in the prior table can claim `Confirmed` for proprietary APIs.

7. **No universal Nokia compatibility claim is acceptable.**  
   Platform family and resolution do not imply identical runtime.

## 7. Architecture Consequence

Cross-check supports the following settled design without implementation:

- Mandatory portable core: MIDP 2.0 `MIDlet`, `Display`, `Canvas`, `Graphics`, `Image`.
- Deterministic logical 320×240 internal frame.
- Key mapping based on game actions first, raw keys second.
- Pointer optional, detected through `hasPointerEvents`.
- Fullscreen optional and size-change aware.
- MMAPI optional and tone/silence fallback.
- Vendor UI API dependencies isolated behind guarded adapters.
- No vendor package directly imported in core compiled logic.
- Pointer-only and audio-only controls must not be required.

This design remains valid and is now better evidenced.

## 8. Does Enough Evidence Exist to Define the API Core?

Yes, but with an important caveat:

The **specification core** is sufficiently evidenced:

- `Canvas` and `Graphics` are core rendering/input constructs.
- `Image` supports the expected standard rendering path.
- Display dimensions and availability differ per implementation.
- Audio, Nokia UI, pointer, and device control are not core.

The **device core** is not yet established:

- target model verification is incomplete,
- capability specifics are undocumented or not present in this audit,
- firmware behavior and heap limits are not validated,
- no implementation-level evidence exists.

Therefore, HoloJava can define a **portable Java ME core API strategy**, but it should not yet designate a concrete Nokia 320×240 device as officially supported.

## 9. Remaining Testing Before a Diagnostic MIDlet

1. Obtain and verify the CLDCC MIDP API jars, including license and checksum.
2. Verify target bytecode level and ProGuard `-microedition` behavior experimentally.
3. Build only after explicit toolchain approval.
4. Run a standard `Canvas` and `Graphics` 320×240 diagnostic on each target emulator.
5. Test:
   - startup
   - logical drawable dimensions
   - key mappings
   - fullscreen
   - pointer flags
   - backlight/vibration fallback behavior
   - audio fallback behavior
6. Test any available secondary emulator/device separately and record exact configurations.
7. Re-run the vendor adapter probe:
   - `Class.forName("com.nokia.mid.ui.DirectGraphics")`
   - `Class.forName("com.nokia.mid.ui.DeviceControl")`
   - guarded `Sound` class detection if applicable.

## 10. Conclusion

The prior Java ME / Nokia API research report is strong enough to support HoloJava's **core portable API design**. It is **not** strong enough to justify implementation-time assumptions about specific Nokia models, vendor extension presence, or system behavior.

HoloJava's recommended API layer is therefore:

**Mandatory core:** MIDP 2.0 `Canvas` / `Graphics` / `Image` / `Display` only, with normalized keys, integer-only rendering, partial-alpha handling through standard image and drawing, and safe fallback when optional capabilities are absent.<|end|><|start|>write to=assistant<|channel|>analysis<|message|>Created file successfully: /home/antomay/Projects/HoloJava/docs/JAVA_ME_API_PRIMARY_SOURCE_CROSSCHECK.md