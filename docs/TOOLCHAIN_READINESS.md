# Toolchain Readiness

Source of truth: current Java ME build-environment findings and required setup approval.

## Environment Inspected

- OS: Debian GNU/Linux 13 (trixie), kernel `7.1.13+deb13-amd64`.
- Architecture: `amd64` / x86-64.
- Disk: approximately 5.3GB free under `/home` and 12GB free on the root filesystem.
- Installed Java runtime/compiler: none.
- Installed Ant: none.
- Installed Gradle: none.
- Installed Maven: none.
- Installed preverification tool: none.
- Installed Java ME SDK: none.
- Installed packaging helper for MIDlet packaging: none (`zip` and `unzip` are available).

No system-wide changes were made during this review.

## Available Local Tools

- `zip` and `unzip`
- `ar`
- Python and Pillow for diagnostic asset metadata only
- Android SDK tree present, but no confirmed Java ME runtime or preverification tool

These tools are cohesion-preserving diagnostics only. They cannot produce a MIDlet.

## Candidate Package Evidence

- Debian candidate: `openjdk-21` (JDK candidate, no installation).
- Debian candidate: `proguard-cli` (preverification/obfuscation helper, no installation).
- No candidate Java ME SDK or MIDP development kit was found by package-name search.
- Assets, icon sources, and extractor tools remain outside the Java ME build path.

## Build State Before Diagnostic (Historical)

This section records the state before the user-approved diagnostic build. Its historical statements are retained for readiness evidence; see `Diagnostic Validation Result (2026-10-13)` for the verified result.

A valid Java ME MIDlet requires more than compilation:

1. A Java compile step targeting a supported bytecode level.
2. CLDC 1.1 and MIDP 2.0 API classes.
3. Preverification metadata for stack maps and type verification.
4. JAR resource layout plus a JAD descriptor.
5. Emulator or device testing.

No currently installed component provides all of these. Therefore no `MIDlet`, `Canvas`, manifest, JAR, or JAD was created in this phase.

## Proposed Approval Path

If approved, perform the next small rootless or approved-software action:

1. Use OpenJDK 21 as the build host only.
2. Create a local, project-scoped root under `/home/antomay/Projects` (or `$HOME/.local`) rather than a system-wide installation. Alternatively report the exact system package request if root access is necessary.
3. Acquire a trusted CLDC/MIDP API jar or SDK artifact, pinned by version and checksum before use.
4. Add a preverification route (ProGuard or the SDK `preverify` tool), pinned by checksum and version.
5. Generate a trivial MIDlet scaffold and package it without gameplay.
6. Run it only as a smoke test in J2ME Loader or another compatible emulator.
7. Record the exact versions and hashes only after they are actually selected and verified.

Do not grant blanket authority to download or install software without reviewing the proposed artifact list.

## Minimal Scaffold Definition (Only if Toolchain Is Verified)

The later permitted scaffold is limited to:

- A `MIDlet` subclass with `startApp`, `pauseApp`, and `destroyApp`.
- A minimal `Canvas` showing a diagnostic 320×240 coordinate pattern.
- Simple key diagnostics, not gameplay.
- Basic JAR/JAD packaging.
- A runtime resize/scaling audit at the configured logical resolution.
- A smoke-test procedure in J2ME Loader.

Not permitted:

- Player movement or a playable character.
- Enemies, weapons, HP, XP, upgrade choices, or spawn systems.
- A gameplay loop.
- Asset conversion, bulk copy, or production asset loader implementation.
- Any desktop runtime target.


## Candidate Toolchain Findings

### Approach A: OpenJDK + external CLDC/MIDP APIs + ProGuard preverification

Recommended as the best automatable approach on Debian.

- **OpenJDK 21**, candidate `21.0.12.1+1-1~deb13u1` (Debian trixie packaging); license permits local build use. JDK 21 can compile Java; Java ME API classes must be external and would require their own license and pin.
- **ProGuard CLI 6.2.2-3**, candidate Debian package; includes preverification support targeting Java Micro Edition.
- **Manual requirement:** CLDC 1.1 and MIDP 2.0 classes/jAR artifact. This repository does not contain one, and no system-wide source for a trustworthy copy was identified.
- **License risk:** Typical CLDC/MIDP API classes come from Oracle or device OEM SDK archives under non-open terms. They must not be copied or redistributed casually.
- **Maintenance:** OpenJDK and ProGuard are maintained; the CLDC/MIDP artifact is legacy and unverified.
- **Compatibility note:** Java source must be restricted to CLDC 1.1-level syntax and runtime behavior even though the compiler is modern.

### Approach B: Oracle Java ME SDK / Sun WTK via archived RPM or installer

This route supplies MIDP APIs and preverification tools, but Debian readiness is questionable.

- Oracle archives expose JDK 8 compressed archives and source/bin archives such as `midp-2_0-src-linux-i686.zip`, as well as SDKs licensed by the Oracle License Agreement.
- The available SDK evidence points primarily to Windows-oriented MSI/EXE installers (`oracle-jmesdk-3-4-rr-win32-bin.exe`).
- CLDC 1.1 and MIDP 2.0 source archives are x86-centric and old.
- Pro: these classes and tools were the reference MIDlet implementation.
- Con: installation likely requires Oracle tooling, legacy operating constraints, and proprietary licensing; it is not rootless-friendly and is poorly suited to modern reproducible Debian builds.

### Approach C: J2ME Polish or similar Ant-based J2ME framework

Reference frameworks can package and preverify MIDlet suites, but they introduce a large legacy dependency stack.

- J2ME Polish is referenced as an Ant-based J2ME build framework with support for Blackberry and Symbian targets.
- It may be useful as a future build layer but is not required for a minimal preverifiable MIDlet.
- Evidence suggests this ecosystem is long-lived but mostly legacy; its source/version site availability and current compatibility must be verified before adoption.

## Proposed Minimal Build Sequence (No Implementation Yet)

If an API archive and preverifier are approved:

1. Create a `src/me/DiagnosticMIDlet.java` with MIDlet lifecycle only and a Canvas coordinate test.
2. Compile with the selected JDK while including the CLDC 1.1 and MIDP 2.0 classes in `bootclasspath` or classpath.
3. Run ProGuard or the SDK `preverify` step and verify no missing descriptors or stack-map errors.
4. Package with `jar cfm HoloJavaDiagnostic.jar MANIFEST.MF diagnostic classes`.
5. Include a manifest with `MIDlet-Name`, `MIDlet-Vendor`, `MIDlet-Version`, `MIDlet-1`, and `MicroEdition-Configuration`; add an appropriate MIDP profile if JAD is generated.
6. Create JAD only if the emulator/device accepts it.
7. Load and launch the MIDlet in J2ME Loader and verify the 320×240 pattern, scaling, and key diagnostics.
8. Repeat after testing on any available secondary emulator.

## Command Templates (Not Executed)

```sh
# Example identifiers; versions and archives must be approved before use.
# javac ...
# proguard (or preverify) ...
# jar cfm ...
```

No commands from this section have been run. They are design templates only.


## Remaining Limitations Before Phase 3 Readiness

1. Nokia, audio, pointer, multi-resolution, and long-term runtime behavior remain unverified; only the tested J2ME Loader configuration passed.
2. Candidate game assets remain unverified for redistribution.
3. No original-game formulas have been recovered.
4. Phase 3 gameplay implementation requires explicit separate approval.
5. No Git repository status verification was introduced; keep in-place edits documented as before.

The compatibility dossier itself is not a build or runtime test result. No system has been marked `Implemented` or `Tested`.

## Acquired API Stub Artifacts (Phase 2)

The following Maven Central API stub jars were acquired locally on 2026-10-13 into a project-local dependency directory. They are API stubs, not source implementation, runtime, or a completed toolchain test.

| Item | Value |
|---|---|
| Base URL | `https://repo.maven.apache.org/maven2/` |
| Artifact 1 URL | `https://repo.maven.apache.org/maven2/org/microemu/cldcapi11/2.0.4/cldcapi11-2.0.4.jar` |
| Artifact 1 metadata | `https://repo.maven.apache.org/maven2/org/microemu/cldcapi11/2.0.4/cldcapi11-2.0.4.pom` |
| Artifact 1 group/version | `org.microemu:cldcapi11:2.0.4` |
| Artifact 1 size | 44,427 bytes |
| Artifact 1 SHA1 (published and local match) | `acee7100c9d03728cc275f372ee7e7903ad43e4f` |
| Artifact 1 SHA256 (local) | `f86f41bfb4097e4bd9cdfeca528960f6977cbda0f5785342adef9909a6a3baef` |
| Artifact 1 license | LGPL (parent `org.microemu:microemu:2.0.4`, licensed under GNU LGPL) |
| Artifact 2 URL | `https://repo.maven.apache.org/maven2/org/microemu/midpapi20/2.0.4/midpapi20-2.0.4.jar` |
| Artifact 2 metadata | `https://repo.maven.apache.org/maven2/org/microemu/midpapi20/2.0.4/midpapi20-2.0.4.pom` |
| Artifact 2 group/version | `org.microemu:midpapi20:2.0.4` |
| Artifact 2 size | 42,963 bytes |
| Artifact 2 SHA1 (published and local match) | `c183e2bf668548717734cc9c75b9df8950d81ccd` |
| Artifact 2 SHA256 (local) | `b7f6ed5e0344c7000643ecc114e6edff3587e8e77face546af0e12ffd6dbe826` |
| Artifact 2 license | LGPL (same parent) |
| Verification | Both JARs pass `unzip -t`; package lists contain expected CLDC and MIDP 2.0 packages, including Canvas, Display, Graphics, Image, MIDlet, RMS, Player, and MMAPI files. |

Confidence notes:
- The `.sha1` files matched the local JAR contents.
- Maven Central did not provide `.sha256` files for these artifacts, so only local SHA-256 values are recorded.
- These jars are API stubs. They do not prove runtime compatibility with J2ME Loader or Nokia devices, and they do not complete bytecode targeting or preverification.


## Diagnostic Validation Result (2026-10-13)

Phase 2 diagnostic toolchain validation completed and was runtime-verified.

Toolchain path:

- Java build host: OpenJDK 21.
- CLDC 1.1 API stub: `deps/java-me/cldcapi11-2.0.4.jar` (`org.microemu:cldcapi11:2.0.4`).
- MIDP 2.0 API stub: `deps/java-me/midpapi20-2.0.4.jar` (`org.microemu:midpapi20:2.0.4`).
- Preverifier: ProGuard CLI 6.2.2.
- Diagnostic source: `src/java-me-diagnostic/me/holojava/diagnostic/DiagnosticMIDlet.java`.
- Compiler: `javac --release 8`, target class major version 52.
- Preverification: `proguard ... -microedition -dontshrink -dontoptimize -dontobfuscate -dontwarn`, passed.
- Packaging: `zip`-created `dist/java-me-diagnostic/HoloJavaToolchainDiagnostic.jar` (3,153 bytes).
- Descriptor: `dist/java-me-diagnostic/HoloJavaToolchainDiagnostic.jad`, with exact size agreement.
- JAR contents: manifest plus `DiagnosticMIDlet.class` and `DiagnosticCanvas.class` only; API stub classes were not packaged.

J2ME Loader runtime result:

- MIDlet launch: passed.
- Key input: passed.
- Canvas dimensions: passed.
- Additional errors or notes: none.

Verified by this result only:

- J2ME Loader on the tested configuration.
- Standard MIDP 2.0/LCDUI lifecycle and Canvas behavior for the diagnostic MIDlet.
- Compiler output, ProGuard 6.2.2 preverification, JAR/JAD consistency, and descriptor size matching.

Not verified by this result:

- Nokia-device compatibility.
- Audio playback or audio format support.
- Pointer or touch behavior.
- Actual physical-screen scaling beyond the tested Canvas dimensions.
- Heap limits, orientation, key layouts, or vendor-specific runtime behavior on feature phones.
- Long-term runtime stability, repeated cycles, or installation robustness.
- Candidate asset redistribution rights.

The acquisition note above is retained as historical evidence; preverification, packaging, and the J2ME Loader launch described here subsequently resolved the toolchain portion of that earlier toolchain readiness concern.
