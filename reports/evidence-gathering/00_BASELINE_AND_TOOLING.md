# HoloJava Evidence Gathering — Baseline & Tooling

## 1. Baseline Verification (T+0)

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Project root | `/home/antomay/Projects/HoloJava` | `/home/antomay/Projects/HoloJava` | ✅ MATCH |
| Reference JAR | `/home/antomay/Downloads/HoloJavaFullGame.jar` | Exists, accessible | ✅ MATCH |
| JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | Identical | ✅ MATCH |
| Git branch | `main` | `main` | ✅ MATCH |
| Git HEAD | `bdc415e` | `bdc415e` | ✅ MATCH |
| Git status | Clean + untracked reports | `?? reports/comprehensive-audit/`, `?? reports/jar-forensics/`, `?? reports/post-audit-review/` | ✅ MATCH |
| Working tree | No tracked changes | `git diff` = empty | ✅ MATCH |

## 2. Reports Present

### Post-Audit Review (6 files)
- `reports/post-audit-review/00_REVIEW_SUMMARY.md` (4896 bytes)
- `reports/post-audit-review/01_FINDING_VALIDATION.md` (5835 bytes)
- `reports/post-audit-review/02_LEVEL_UP_VALIDATION.md` (9010 bytes)
- `reports/post-audit-review/03_PARITY_REVIEW.md` (6123 bytes)
- `reports/post-audit-review/04_BACKLOG_REVIEW.md` (6504 bytes)
- `reports/post-audit-review/05_REVIEW_COVERAGE.md` (7842 bytes)

### JAR Forensics (5 files)
- `reports/jar-forensics/ARTIFACT_REPORT.md` (5658 bytes)
- `reports/jar-forensics/BYTECODE_MAP.md` (8790 bytes)
- `reports/jar-forensics/RECOVERY_PLAN.md` (8498 bytes)
- `reports/jar-forensics/S1_LEVEL_UP_DEEP_AUDIT.md` (19038 bytes)
- `reports/jar-forensics/SOURCE_COMPARISON.md` (9984 bytes)

### Comprehensive Audit (9 files)
- All 9 files present in `reports/comprehensive-audit/`

## 3. Tooling Available

| Tool | Version/Path | Purpose |
|------|--------------|---------|
| `javap` | `/usr/bin/javap` (OpenJDK) | Bytecode disassembly `-c -p` |
| `java` | `/usr/bin/java` (OpenJDK) | Runtime (NOT USED per constraints) |
| `javac` | `/usr/bin/javac` (OpenJDK) | Compiler (NOT USED per constraints) |
| `unzip` | `/usr/bin/unzip` | JAR extraction |
| `grep`/`sed`/`awk` | Standard Linux | Text processing |

## 4. Bytecode Extraction Results

| Class | Source | Bytecode Lines | Status |
|-------|--------|----------------|--------|
| `me.holojava.fullgame.RunWorld` | 1557 lines | 9075 lines | ✅ Extracted |
| `me.holojava.fullgame.MathUtil` | 70 lines | 215 lines | ✅ Extracted |
| `me.holojava.fullgame.FullGameCanvas` | ~459 lines | 2396 lines | ✅ Extracted |

Extraction method: `unzip -o JAR "classpath" && javap -c -p classfile`

## 5. Analysis Scope Confirmed

Per `reports/post-audit-review/02_LEVEL_UP_VALIDATION.md` and `04_BACKLOG_REVIEW.md`, Phase 1 focuses on:

1. **`requiredForLevel(level)`** — bytecode vs source, precision for level ≥ 2
2. **`chooseOfferType()`** — control flow, RNG range, probabilities
3. **`offerUsed[64]`** — modulo 64 sink, collision, reset behavior
4. **`heldOffer` 4-slot** — index bounds, HOLD/REROLL exclusion, carry-over logic

All other areas marked `INSUFFICIENT_EVIDENCE` or `DESIGN_INTENT_UNVERIFIED` in review are deferred.

## 6. Methodology

- **Source of truth**: Bytecode from reference JAR (extracted with `javap -c -p`)
- **Comparison baseline**: Source code in `/home/antomay/Projects/HoloJava/src/java-me-fullgame/me/holojava/fullgame/`
- **No runtime execution**: All analysis static
- **No modifications**: Source, JAR, Git, build outputs untouched

## 7. Evidence Gaps Identified (Pre-Analysis)

| Gap | Impact | Plan |
|-----|--------|------|
| `requiredForLevel` exact values for level 2-50 | Cannot compute floating-point exactly without runtime | Mathematical analysis of `MathUtil.power` + `roundEven` |
| `chooseOfferType` effective distribution with fallbacks | Source shows weighted, but `fillOffer` fallbacks change effective distro | Control flow analysis of `fillOffer` |
| `offerUsed` hash collision behavior | Source uses `hashCode() & 63`, but collision handling unclear | Bytecode inspection of all access sites |
| `heldOffer` cross-run persistence | `reset()` sets -1, but `generateOffers()` restores | Trace `heldOffer` lifecycle across run boundaries |
| JAR parity for all 4 areas | Partial bytecode match confirmed, full behavioral parity unknown | Document per-method bytecode match status |

---
