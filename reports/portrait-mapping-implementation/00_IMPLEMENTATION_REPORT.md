# A2 — Portrait Mapping Fix: Implementation Report

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Git HEAD | `666e338` | `666e338` | ✅ MATCH |
| Reference JAR SHA-256 (before) | `20b31de6…3a6dfb` | identical | ✅ MATCH |
| A1 changes present | 2 files, +21 / −4 | identical after A2 | ✅ PRESERVED |

A1 diff was re-measured after the A2 edit and is byte-for-byte unchanged (`ContentCatalog.java` +14/−1, `WikiCatalogData.java` +11/−3).

---

## 2. Defect Confirmed Narrowly

`AssetLibrary.java:155` (pre-fix) resolved portraits from the **catalog id**:

```java
current.portrait = image("/assets/fullgame/portraits/" + ContentCatalog.CHARACTERS[index].id + ".png");
```

Catalog ids are short (`gura`, `ame`, `calli`, `kiara`, `ina` — restored by A1 from the reference JAR). Portrait files are named after the **wiki id**.

| Catalog id (requested) | File on disk | Result |
|------------------------|--------------|--------|
| `gura.png` | absent | ❌ |
| `ame.png` | absent | ❌ |
| `calli.png` | absent | ❌ |
| `kiara.png` | absent | ❌ |
| `ina.png` | absent | ❌ |

| Wiki id (actual file) | Size |
|-----------------------|------|
| `gawr_gura.png` | 3354 B |
| `amelia_watson.png` | 4367 B |
| `mori_calliope.png` | 3424 B |
| `takanashi_kiara.png` | 3931 B |
| `ninomae_inanis.png` | 3702 B |

All five long-form files exist in `assets/fullgame/portraits/` (47 portraits total).

**Symptom**: `image()` (`AssetLibrary.java:238-245`) catches the load failure, increments `failures`, records `firstFailure`, and returns `null`. Portrait silently absent in character select — no crash.

---

## 3. Packaging Check (important)

A prior audit noted the reference JAR contained **zero** `portraits/` entries. That was true of the *reference* JAR only — it predates the portraits being added to the working tree.

The **current** build does package them: `tools/build_fullgame.sh:11` copies `assets/fullgame`, and `:32` zips `META-INF assets me`. Verified in the freshly built JAR:

```
assets/fullgame/portraits/gawr_gura.png
assets/fullgame/portraits/amelia_watson.png
assets/fullgame/portraits/mori_calliope.png
assets/fullgame/portraits/takanashi_kiara.png
assets/fullgame/portraits/ninomae_inanis.png
```

**Conclusion**: the filename spelling was the sole defect. No packaging change was needed.

---

## 4. Fix Applied

Added a private static resolver next to the existing `groupIndex(String)` helper, matching that method's established if-chain idiom:

```java
private static String portraitFile(String catalogId) {
    if ("gura".equals(catalogId)) return "gawr_gura";
    if ("ame".equals(catalogId)) return "amelia_watson";
    if ("calli".equals(catalogId)) return "mori_calliope";
    if ("kiara".equals(catalogId)) return "takanashi_kiara";
    if ("ina".equals(catalogId)) return "ninomae_inanis";
    return catalogId;
}
```

`characterPortrait(int)` now routes through it.

### Design choices

| Choice | Rationale |
|--------|-----------|
| Localized to `AssetLibrary.java` | smallest safe surface; no catalog schema change |
| Keyed by **catalog id**, not array index | immune to catalog reordering; no index-parallel array to keep in sync |
| Falls back to `catalogId` | unknown ids behave exactly as before — no new failure mode |
| No asset renamed | instruction forbids it; 47-file set untouched |
| `static` helper, no state | matches `groupIndex`; no per-instance cost |

### Alternatives rejected

| Option | Why rejected |
|--------|--------------|
| Rename 5 PNGs to catalog ids | forbidden by instructions; would break the other 42 portraits' naming convention |
| Add `portraitId` field to `CharacterDef` | touches A1 files and all 5 factory call sites — broader than needed |
| Index-parallel `String[] portraitFiles` | requires manual sync with catalog order; fails loudly on future additions |

---

## 5. File Change

| File | Change | Lines |
|------|--------|-------|
| `src/java-me-fullgame/me/holojava/fullgame/AssetLibrary.java` | `characterPortrait` routes through new `portraitFile()` helper | +17 / −1 |

Exact locations: `AssetLibrary.java:151-158` (modified method), `:160-172` (new helper).

No other file modified. `ContentCatalog.java` and `WikiCatalogData.java` untouched by A2.

---

## 6. Test Results

| Check | Result |
|-------|--------|
| `./tools/build_fullgame.sh` | ✅ **PASS** — JAR 510261 bytes (was 510055; +206 from new constant-pool strings) |
| `python3 tools/verify_fullgame_build.py` | ✅ **PASS** — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ clean |
| Mapping strings present in built JAR | ✅ all 5 pairs compiled |
| Each mapped target packaged | ✅ all 5 found in JAR |

Bytecode confirmation from the built JAR:

```
String gura              → String gawr_gura
String ame               → String amelia_watson
String calli             → String mori_calliope
String kiara             → String takanashi_kiara
String ina               → String ninomae_inanis
```

3 build warnings (`source`/`target 8 obsolete`) are pre-existing from `tools/build_fullgame.sh:12`, unrelated to this change.

---

## 7. Boundary Compliance

| Boundary | Status |
|----------|--------|
| A1 changes preserved exactly | ✅ re-measured: +21/−4 unchanged |
| Character ordering preserved | ✅ mapping is id-keyed; no index dependency |
| No assets renamed / moved / added | ✅ `git status` shows no change under `assets/` |
| No broad catalog rewrite | ✅ |
| A3 / A4 not implemented | ✅ |
| Character selection, stage, modes, skills, items, collabs, saves untouched | ✅ only `AssetLibrary.characterPortrait` + new helper |
| No game / MIDlet / emulator | ✅ |
| No download / install | ✅ |
| No commit / reset / stash / history rewrite | ✅ |
| Reference JAR unchanged | ✅ SHA-256 identical |

---

## 8. Remaining Limitations

| # | Limitation | Note |
|---|-----------|------|
| L1 | **No runtime confirmation.** Portraits were verified to exist and to be packaged, and the mapping was verified in bytecode — but no image was rendered, since launching the game was prohibited. Visual confirmation requires a device run. |
| L2 | 42 unmapped portraits | The other 42 wiki-id portraits have no catalog entry, so the fallback path is unreachable today. Harmless now; a future catalog expansion must extend `portraitFile()`. |
| L3 | Other characters still blocked | A3 (Hard/Time visibility) and A4 (`stage5` unlock `99 → 3`) remain open and were deliberately not touched. |
| L4 | Ina `special` discrepancy | JAR `Spirit of the Oni` vs CSV `Tako Spin` — carried from A1, deferred to B2. |

---

## 9. Verdict

**A2 COMPLETE.** Portrait lookup now resolves to the correct packaged assets for all five verified characters, via a localized, order-independent, fail-safe mapping. Build and verifier are green; no A1 work was disturbed.

**Awaiting approval before A3 or A4.**
