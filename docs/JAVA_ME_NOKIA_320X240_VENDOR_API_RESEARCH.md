# Java ME / J2ME Cross-Vendor API Research for 320×240 Nokia Targets

Source of truth: defensive API research, not implementation evidence. No system, build, or gameplay was changed.

## 1. Ringkasan Eksekutif

Tidak exist `universal API` across Nokia Java ME devices merely because they share the same screen resolution. The portable core should rely only on:

- MIDP 2.0 (`javax.microedition.lcdui`, `javax.microedition.midlet`, `javax.microedition.rms`, optional `javax.microedition.io`)
- CLDC 1.1 or, where unavoidable, fallback-compatible code for CLDC 1.0 devices
- `Canvas`, `Graphics`, `Image`, `Display`, and standard `Command`/events
- Optional `javax.microedition.media` and `javax.microedition.media.control` only behind feature detection
- Nokia UI API or other vendor extensions only as optional adapters

Nokia UI classes such as `FullCanvas`, `DirectGraphics`, `DirectUtils`, `DeviceControl`, and `Sound` can provide enhancements, but they are proprietary, version-dependent, and must not enter the core portable runtime.

The safest abstration strategy is a capability-detection layer using guarded class loading and fallbacks, not direct unconditional references to proprietary Nokiaclasses.

## 2. Definisi Perangkat 320×240

A screen resolution is a hardware property, not an API guarantee. Devices with the same physical resolution may differ in:

- CLDC/MIDP generation
- heap size and max JAR size
- color depth
- fullscreen behavior
- pointer-event availability
- audio format support
- vendor API version
- firmware in the same model family

Nokia Series 40 architecture differs by generation and feature pack. Series 40 3rd Edition introduced QVGA 240×320 support. This does not imply that all S40 devices implement the same API set.

## 3. Inventaris Perangkat Relevan

Status below is evidence-labeled. “Confirmed” means a specific source or device entry states it; “Reported” means secondary community documentation; “Unknown” means no reliable evidence was found.

| Perangkat / Platform | Resolusi / orientasi | CLDC | MIDP | Status |
|---|---:|---|---|---|
| Nokia 6300 (Series 40 3rd Edition Feature Pack 2) | 240×320 QVGA, portrait physical display | CLDC 1.1 (platform evidence) | MIDP 2.0 | Reported as S40 3rd FP2 device using CLDC 1.1 and MIDP 2.0 from platform-era sources; proprietary API availability on this exact model not separately confirmed |
| Nokia 5300 (Series 40 3rd Edition Feature Pack 2) | 240×320 QVGA | CLDC 1.1 (platform evidence) | MIDP 2.0 | Same platform-family claim; per-model heap, API versions and pointer behavior must be field-tested |
| Nokia 7373 (Series 40 3rd Edition Feature Pack 2) | 240×320 QVGA | CLDC 1.1 (platform evidence) | MIDP 2.0 | Same as above |
| Nokia 5310 XpressMusic (likely later Series 40 5th Edition target, not directly verified in this research) | 240×320-class QVGA | Unknown | Unknown | Series/firmware-specific; not listed as confirmed here |

Important distinction: a 320×240 logical game frame is not the physical display dimensions. On QVGA feature phones that support portrait 240×320, the logical frame must be adapted (rotate/scale accordingly) without changing the internal coordinate model. Both orientation handling and device-specific scaling require runtime testing.

Common platform-level observations:
- Series 40 3rd Edition introduced QVGA (240×320) support on some member models.
- Feature packs within an edition can change multimedia and graphics behavior.
- Heap and JAR size differences, key behavior, and pointer behavior are not proven by platform name alone.

## 4. API Inti Java ME

### Wajib untuk Runtime Portable

- `javax.microedition.lcdui.Canvas` / `Graphics` / `Image` / `Display` / `Displayable`
- `javax.microedition.midlet.MIDlet`
- `javax.microedition.rms.RecordStore` for persistence only if save data is approved
- optional `javax.microedition.io.Connector` for connectivity-dependent features

### Penting – CLDC 1.0 vs CLDC 1.1

- CLDC 1.1 adds floating-point support and selected `java.lang`/`java.util` capabilities.
- HoloJava should avoid reliance on floating point and on optional CLDC 1.1 APIs in the core path.
- A game based on integer coordinates and indexed integer attributes is more portable across both configurations.

### Penting – MIDP 1.0 vs MIDP 2.0

- MIDP 2.0 adds improved `Canvas`, `Image`, font support, and game APIs.
- Build for MIDP 2.0 only if the target has it. Do not assume all QVGA Nokia units have MIDP 2.0.
- `javax.microedition.lcdui.game` is standard MIDP, but still optional if a device’s behavior differs.

### API Grup: Digunakan

| API Group | Package | Prioritas | Catatan |
|---|---|---|---|
| MIDlet lifecycle | `javax.microedition.midlet` | Wajib | Tanpa `startApp`/`pauseApp`/`destroyApp`, MIDlet tidak hidup |
| LCDUI | `javax.microedition.lcdui` | Wajib | Baseline gambar dan input |
| RMS | `javax.microedition.rms` | Optional | Gunakan hanya jika persistence masuk scope |
| Connectivity | `javax.microedition.io` | Bukan inti | Tidak diperlukan untuk vertikal slice awal |
| Networking not required | Not needed | Not in scope for core | Avoids device-specific permission behavior |

## 5. JSR dan API Opsional

Dipisahkan menurut prioritas game, bukan dianggap jaminan:

### A. Diperlukan / hampir pasti (`Core portable candidate`)

- MIDP 2.0 (`JSR 118`) — standard game UI
- CLDC 1.1 (`JSR 139`) — configuration baseline; pada CLDC 1.0 gunakan bilangan bulat dan jangan mengandalkan fitur tambahan

### B. Berguna Tetapi Opsional

- Mobile Media API (`JSR 135`) — audio, video, tone. Berguna untuk audio, namun implementasi perangkat berbeda; fallback silence wajib
- JTWI (`JSR 185`) — harmonisasi JSR baseline; tidak otomatis menambah fitur hardware, tetapi kontrak baseline penting
- Java Location (`JSR 179`) — hanya jika lokasi menjadi fitur; tidak perlu untuk vertical slice

### C. Tidak Diperlukan untuk Vertical Slice

- JSR 75 FileConnection/PIM
- JSR 82 Bluetooth
- JSR 120/205 Wireless Messaging API
- JSR 184 3D Graphics
- JSR 226 SVG
- JSR 234 Advanced Multimedia Supplements
- JSR 177 SATSA
- JSR 179 Location
- JSR 248 MSA

### D. Potensial Tidak Cocok untuk Kompatibilitas Luas

- JSR 234, M3G, dan high-level vector/3D API bergantung platform dan implementasi, serta dapat meningkatkan memory usage dan complexity.
- Bluetooth, SMS, file access memerlukan permission/device support dan tidak menjadi inti game.

## 6. Nokia UI API dan Ekstensi Proprietary

Berdasarkan dokumentasi mirror, package `com.nokia.mid.ui` meliputi:

- `DirectGraphics` — ekstensi gambar: polygon, triangle, pemutaran gambar, alpha
- `DirectUtils` — utility untuk DirectGraphics
- `FullCanvas` — kanvas full screen; langsung digantikan oleh `Canvas.setFullScreenMode(boolean)` pada MIDP 2.0
- `DirectUtils` placement in same package

Sumber mirror menunjukkan klasifikasi `FullCanvas` sebagai deprecated as of Nokia UI API 1.1 pada perangkat MIDP 2.0+ dan digantikan oleh `setFullScreenMode`.

Juga terkait:

- `com.nokia.mid.sound.Sound` mungkin ada di varian API Nokia; exact class path dan capability dilaporkan terpisah dari device implementasi
- `DeviceControl` mungkin digunakan untuk backlight atau kontrol perangkat

### Risiko Penggunaan `FullCanvas` Secara Langsung

- Jika kelas tidak tersedia pada perangkat, MIDlet dapat gagal load jika referensi statis atau field mencantumkan tipenya.
- Bahkan jika kelas tersedia pada SDK/emulator, `FullCanvas` mungkin tidak ternormalisir pada firmware seluruh model.
- `Canvas.setFullScreenMode(true)` adalah opsi standar MIDP 2.0 yang seharusnya digunakan dulu.

### Risiko Penggunaan `DirectGraphics`

- `DirectGraphics` dapat memberi akselerasi rotasi/alpha/pixel access, tetapi media vendor-specific.
- Jika kelas tidak tersedia, MIDlet dapat gagal load kalau code compiled dengan reference langsung.
- Fallbacknya: gambar standard `Graphics.drawImage`, penskalaan manual, alpha support terbatas (device-dependent).
- Do not place DirectGraphics into the core portable layer.

### Runtime Detection Approach

- Gunakan reflection / `Class.forName` dalam blok terisolasi, lalu invoke metode secara reflection.
- Hindari import kelas vendor secara langsung di class yang pasti dimuat.
- Fallback ke standar API jika method/class unavailable.
- Design required features around functionality, not class presence.

## 7. API Vendor Lain

### Sony Ericsson / Sony Mobile

Dokumentasi Sony Ericsson membahas platform JAva ME yang mendukung `JSR 82`, `JSR 135`, `JSR 185`, dan extensions proprietary seperti optional APIs per platform. Banyak Sony Ericsson JP-2 dan yang lebih modern mendukung MIDP 2.0/Sony's Java platform, namun `com.sonyericsson.*` extensions tidak portable ke Nokia.

Rekomendasi:
- Treat these APIs as platform-specific only.
- Do not make any HoloJava core feature require `SonyEricsson` extensions.
- If SE extensions can be proven on a Nokia target, mark them as `Reported`, not `Confirmed`.

### Samsung dan Motorola

Bukti yang diterima hanya seputar Java ME broadly. Samsung/Motorola proprietary packages tentu tidak portable to Nokia. Such APIs should only be evaluated under a specific target device evidence model.

### Sumber Komunitas

- Device database and archived J2ME wiki function as secondary evidence.
- Community claims should be marked `Reported` or `Unknown`.
- An absence of documentation for a device API is not proof that it works or that it is unavailable.

## 8. Matriks Kompatibilitas

### A. API Inti

| Kombinasi | Nokia S40 QVGA (FP2-era) | J2ME Loader Android | Status |
|---|---|---|---|
| MIDP 2.0 required | Specified/Reported | Likely Supported depending J2ME Loader version | `Specified` on hardware; `Reported` for emulator |
| CLDC 1.1 baseline | Series 40 3rd likely CLDC 1.1 | J2ME Loader behavior emulator/version dependent | `Reported` / `Device-specific` |
| Faster integer-only logic | Recommended | Recommended | `Recommended`, not evidence-based |
| Standard `Graphics.drawImage` | Expected | Expected | `Expected`, not tested by us |
| Standard `Canvas` input | Expected | Expected with configurable keys | `Expected` |
| `Display.setFullScreenMode` | MIDP 2.0 feature | Emulator dependent | `Specified` at MIDP 2.0 spec level |

### B. Nokia UI API

| Kelas / Fitur | Season / Device Pattern | Vendor / Built-in | Portability | Status |
|---|---|---|---|---|
| `com.nokia.mid.ui.FullCanvas` | Nokia UI API 1.0-class devices | Proprietary extension | Replaced by MIDP 2.0 `setFullScreenMode` | `Deprecated`; availability variant confirmed only by class mirror, not exact device list |
| `DirectGraphics` | Nokia UI API 1.1 | Proprietary extension | Fallback to `Graphics` | `Reported/Unknown`; risky if imported directly |
| `DirectUtils` | Associated with Nokia UI API | Proprietary extension | Standard Utility subset only | `Reported` |
| `DeviceControl` (`backlight`, etc.) | Nokia short-key activation | Proprietary, device behavior variant | Fallback no-op | `Unknown` |
| Nokia `Sound` | Nokia ROM/player dependency | Proprietary | Fallback `ToneControl` or MMAPI | `Unknown` |

### C. JSR dan Multimedia

| API | Prioritas HoloJava | Device Pattern | Status |
|---|---|---|---|
| JSR 135 MMAPI | Audio only, optional | Support varies significantly; often audio playback is an edge case | `Optional`; `Specified` standard, implementation/device dependent |
| JSR 118 MIDP 2.0 | High | Standard baseline if target explicitly supports | `Specified` standard |
| JSR 185 JTWI | JSR harmonization | Broadly recognized baseline | `Specified` |
| JSR 184 / 226 / 234 | Not needed | Highly device- and platform-variant; memory intensive | `Not in scope for core` |
| JSR 82 / 120 / 205 | Not needed | Only if explicitly desired | `Optional` only |

### D. Vendor APIs Lain

| Kategori | Status |
|---|---|
| Sony Ericsson proprietary packages | Enabled only on SE Java platforms; not portable to Nokia devices |
| Samsung/Motorola proprietary packages | Not portable; require separate evidence model |
| Community API availability claims | `Reported` at best, never `Confirmed` without device-specific evidence |

## 9. Rekomendasi Arsitektur HoloJava

### A. Core Portable Layer

Gunakan tanpa proprietary imports:

- MIDP 2.0 LCDUI
- Standard MIDP event callbacks (`CommandListener`, `KeyListener` equivalents through `Canvas`)
- Ganbar 2D manual
- Integer coordinate transform
- App object state model
- Resource-key based image/audio indirection
- Capability probes

### B. Optional Capabilities

- MMAPI sound where available.
- Fullscreen mode if `setFullScreenMode` behaves correctly.
- Immediate-mode after refresh optimization only if standard semantics remain stable.
- Pointer only if target key layout is known; avoid input assumption.

### C. Vendor Adapters

- `NokiaCanvasExtension` adapter should be isolate, e.g.:
  - `NokiaUIAvailability` interface
  - `DirectGraphics` bridge, optional
  - `DeviceControl` adapter no-op fallback
  - `NokiaSound` adapter, optional
- Adapter should never be a base class imported by core.
- Load the adapter only after guarded detection.

### D. Fallback Behavior

- No `DirectGraphics`: use `Graphics.drawImage`, standard trig fixed point, and clipped polygons.
- No `Sound`: silence or MIDP `ToneControl` only if present.
- No `FullCanvas`: standard `Canvas.setFullScreenMode(true)`, with safe fallback to non-fullscreen if unsupported.
- No touchscreen: key event only.
- No pointer: keyboard exclusivity.
- No pointer handling if the device does not report reliable hit coordinates.

### E. Runtime Detection

- Maintain wrapper facade, e.g. `CapabilityProbe`:
  - `isMIDP2 = true by compilation baseline` but load capability at runtime anyway
  - `supportsDirectGraphics()`
  - `supportsPointer()`
  - `supportsMMAPI()`,
  - `supportsMemory()`
- Do not parse `System.getProperty` alone as definitive evidence.
- Reflective `Class.forName` in an isolated helper may be more reliable than direct reference.
- Keep exception handling tight.

### F. Display / Input Layer for 320×240

Design rules:
- Canonical logical frame: `320x240`.
- On physical 240×320 devices, let renderer continue to operate in logical coordinates and map each logical coordinate with defined orientation policy.
- Available orientations may differ per device; do not hardcode one landscape mapping as universal.
- Scale nearest-neighbor only if approved and tested; otherwise add letterbox or pillarbox.
- Critical gameplay must remain visible after scaling.
- Input keys must be normalized through a `KeyMapper`.
- Do not process key code names unsafely; always return to a known key enum.

### G. Audio Layer

Audio design:
- Candidate audio remains keys, not filenames.
- If MMAPI is supported, support PCM/WAV or tone.
- If not, silence or standard `ToneControl`.
- Audio playback must never block simulation.
- Audio candidate metadata is not runtime evidence.

### H. Memory Strategy

Conservative rules:
- Use small frame counts.
- Prefer sharing immutable images.
- Do not decode all assets at once.
- Require asset disposed at scene exits.
- Avoid caching everything in memory on low-memory devices.
- Use fixed-size arrays where possible.
- Prefer file-ordering-based rendering manifest and avoid holding mixed-size explosion frames in memory without bounds.

## 10. Rekomendasi Profil Target

### Profil A — Baseline Java ME

- Requirements:
  - MIDP 2.0
  - CLDC 1.0 or 1.1
  - standard LCDUI
- Features:
  - a complete game loop
  - keyboard input
  - sprite and frame-based rendering
  - scaling
  - optional audio via `ToneControl` or `Sound` adapter
- Limitations:
  - no direct graphics acceleration
  - no proprietary touch API
  - performance highly device-dependent
- Testing:
  - key mapping, fullscreen, image decode, 320×240 scaling

### Profil B — Nokia QVGA with Verified Additional Features

- Additional optional support only after runtime observation:
  - fullscreen mode
  - direct graphics or improved draw performance
  - MMAPI audio playback
- Limited model-by-model; features must be tested per firmware.
- Prioritize known QVGA S40 devices with confirmed heap/JAR behavior.

### Profil C — J2ME Loader on Android

- Highest flexibility and lowest uniformity constraints.
- Settings are configurable from Android emulator UI.
- Audio/image support and performance differ from physical phones.
- Run as primary compatibility smoke test, not as proof of Nokia compatibility.

## 11. Sumber Online yang Digunakan

| Sumber | Jenis | Klaim yang didukung |
|---|---|---|
| Wikipedia — Series 40 | Semi-official encyclopedic reference | S40 3rd Edition introduced QVGA 240×320; feature pack behavior and generation differences |
| Phonemore / device family entries (`Series 40 3rd edition Feature Pack 1`) | Device/platform database | feature pack API listing, screen resolution, platform classes |
| Nokia 6300 Wikipedia/Nokia wiki | Specific device documentation | Nokia 6300 runs Series 40 3rd Edition FP2 and has QVGA 240×320 display |
| Nikita36078 GitHub J2ME Docs mirror for Nokia UI API 1.1 | Community host mirror of official docs | `com.nokia.mid.ui` classes, `FullCanvas`, `DirectGraphics`, `DirectUtils`; deprecation note |
| `wupp.cc` all classes Nokia UI API 1.1 | Community/Java ME class index | six core Nokia UI API classes |
| J2ME Device wiki / GSMArena device profiles | Community | Establish general device variation; not sufficient alone for API support |
| Sony Ericsson JavaPlatform Wikipedia | Vendor platform doc | JP platform versions imply different package/API behavior across models |
| Oracle Java ME technical resources | Oracle documentation | MMAPI as optional JSR 135 |
| Java ME course/university resource (Pitt/Slide) | Educational reference | Nokia device API trajectory across generations |
| GSMArena review database | Device metadata | model-specific resolution and feature metadata |

## 12. Fakta yang Terverifikasi Versus Asumsi

### Terverifikasi dari sumber

- Nokia 6300 runs Series 40 3rd FP2 and has QVGA display.
- S40 3rd Edition introduced QVGA 240×320 displays.
- Nokia UI API exists under `com.nokia.mid.ui`.
- `FullCanvas` was deprecated in favor of `Canvas.setFullScreenMode` in later Nokia UI API 1.1 + MIDP 2.0.
- JSR 135 is optional and device support varies.
- Sony Ericsson Java platforms are versioned and feature differences exist.
- CLDC 1.1 and MIDP 2.0 are distinct specifications; variations in device support are normal.

### Asumsi/Belum Diverifikasi

- Whether a specific S40 device includes CLDC 1.1 rather than CLDC 1.0.
- Whether a model truly implements Nokia UI API 1.1 vs 1.0.
- Whether pointer or fullscreen works as expected.
- Whether MMAPI audio formats are playable.
- Whether a Nokia device supports `DirectGraphics` or `DeviceControl`.
- Whether a physical device can load stack and multi-frame sprite without exceeding heap.
- Whether all S40 QVGA family devices share the same Java API.

## 13. Pertanyaan yang Belum Terjawab

1. API jar CLDC 1.1 and MIDP 2.0 resmi yang digunakan untuk compile — checksum and license not yet determined.
2. Exact Nokia API versions per device/model.
3. API availability on target ROM/firmware level.
4. Memory limits per supported model.
5. Audio format, especially MIDI versus WAV, across target devices.
6. Pointer/input mappings and virtual baseline.
7. Whether J2ME Loader emulation assures compatibility with target physical devices.
8. Whether internal 320×240 logical coordinate system should use portrait or landscape output.

## 14. Langkah Investigasi Lanjutan Tanpa Instalasi

1. Collect exact model/firmware/API lists directly from GSM Arena and Nokia archive documentation.
2. Compare device SDK reference documentation for API availability.
3.Build a no-implementation capability probe checklist later, once toolchain is verified.
4. Obtain and verify official `CLDC/MIDP` source/API under approved licenses.
5. Test J2ME Loader after an approved scaffold exists.

## 15. Perubahan Dokumentasi yang Disarankan

- `docs/JAVA_ME_COMPATIBILITY.md`: update cross-vendor caveats and three profiles.
- `docs/TOOLCHAIN_READINESS.md`: retain focus on API license; add cross-vendor compatibility rationale.
- `docs/RECONSTRUCTION_SCOPE.md`: no scope change is required; only technology support list should add “vendor API optional” note.
- `docs/TECHNICAL_DECISIONS.md`: optionally add decisions for “portability-first” and “vendor optional.”
- `docs/PROJECT_STATUS.md`: record investigation in progress if this research doc is committed.
- `Roadmap.md`: no phase changes are warranted at this time.

## 16. Catatan Dokumen Audit

Dokumen ini merupakan laporan penelitian, bukan implementasi atau bukti runtime. Isi matrix yang berstatus `Confirmed` hanya diterapkan pada klaim yang diakui oleh sumber, bukan karena resolusi layar atau nama platform.