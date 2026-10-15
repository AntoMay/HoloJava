# Stage 1–5 Scope Audit

## 1. Declared Stages

All 10 stages are declared in `ContentCatalog.java:252-263` (`STAGES` array). `StageDef` is defined at `:136-157`.

| Array idx | Id | Name | `order` | `hard` | `timeStage` | `unlockRequirement` | In Stage 1–5 scope? |
|-----------|-----|------|---------|--------|-------------|---------------------|---------------------|
| 0 | `stage1` | Grassy Plains | 0 | false | false | -1 | ✅ |
| 1 | `stage2` | Holo Office | 1 | false | false | 0 | ✅ |
| 2 | `stage3` | Halloween Castle | 2 | false | false | 1 | ✅ |
| 3 | `stage4` | Gelora Bung Yagoo | 3 | false | false | 2 | ✅ |
| 4 | `stage5` | Fantasy Island | 4 | false | false | **99** | ✅ (with defect) |
| 5 | `stage1h` | Grassy Plains (Night) | 5 | **true** | false | 1 | ❌ extra |
| 6 | `stage2h` | Holo Office (Evening) | 6 | **true** | false | 5 | ❌ extra |
| 7 | `stage3h` | Halloween Castle (Myth) | 7 | **true** | false | 6 | ❌ extra |
| 8 | `stage4h` | Gelora Bung Yagoo (Night) | 8 | **true** | false | 7 | ❌ extra |
| 9 | `time1` | Concert Stage | 9 | false | **true** | 2 | ❌ extra |

**Stage 1–5 = array indices 0–4, all `hard=false`, `timeStage=false`.** This is the intended scope.

---

## 2. Unlock Semantics — Verified

`FullGameCanvas.stageUnlocked()` (`FullGameCanvas.java:257-262`):

```java
if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;  // always open
if (s.timeStage) return save.isCompleted(characterIndex, 2);             // hardcoded 2
return save.isCompleted(characterIndex, s.unlockRequirement);            // by order
```

`unlockRequirement` is a **stage order**, not an array index — `FullGameCanvas.java:149` persists completion using `ContentCatalog.stage(stageIndex).order`.

| Stage | Requirement | Resolves to | Correct for sequential? |
|-------|-------------|-------------|------------------------|
| `stage1` | -1 | always open | ✅ |
| `stage2` | 0 | stage order 0 = stage1 | ✅ |
| `stage3` | 1 | stage order 1 = stage2 | ✅ |
| `stage4` | 2 | stage order 2 = stage3 | ✅ |
| `stage5` | **99** | always open | ❌ **defect** |

**Defect S1**: `stage5` should require order `3` (stage4). Currently it is permanently unlocked. Correcting to `3` produces a strict chain with no cycle: `-1 → 0 → 1 → 2 → 3`.

**Note on the `99` comment**: `StageDef.unlockRequirement` is documented as `// stage order required, -1 default, 99 = never.` (`ContentCatalog.java:150`), but `:259` treats `99` as *always open*. Comment and code disagree.

---

## 3. Extra Modes Reachable in UI

| Screen | Code | Current behavior | Stage 1–5 scope impact |
|--------|------|------------------|------------------------|
| Mode select | `FullGameCanvas.java:307` | 4 entries: Stage Mode, Endless Mode, Time Mode, Back | Endless + Time reachable |
| Stage select | `FullGameCanvas.java:209` | `count = ContentCatalog.STAGES.length + 1` = **11** | all 10 stages listed |
| Mode filter | `FullGameCanvas.java:251-255` | Stage/Endless → non-timeStage; Time → timeStage | Hard stages listed under Stage Mode too |

**Finding S2**: the stage list is not filtered by `hard`. `stage1h`–`stage4h` (orders 5–8) appear in the Stage Mode list, and `time1` appears in the Time Mode list.

**Finding S3**: `SaveStore.setCompleted` rejects `stageOrder > 7` (`SaveStore.java:30`), so clearing orders 8 and 9 is a **silent no-op** — the player sees a victory screen with no persisted progress.

**Finding S4**: `stageAllowed` (`FullGameCanvas.java:253`) depends on `modeIndex`, which persists across navigation; selecting Stage Mode while `modeIndex == 1` (Endless) is only prevented by the mode screen itself.

---

## 4. Per-Stage Dependencies (Stage 1–5)

| Stage | Enemies | Bosses | Enemy assets | Boss assets |
|-------|---------|--------|--------------|-------------|
| `stage1` | `shrimp`, `dark_shrimp`, `shrimp` | `fubuzilla`, `smol_ame` | `Shrimp`, `Shrimp2` ✅ | `fubuzilla`, `AchanBoss` ✅ |
| `stage2` | `sukonbu`, `kuro_sukonbu`, `sukonbu` | `mikodanye`, `achan` | `Sukonbu`, `Sukonbu2` ✅ | `Mikodanye`, `AchanBoss` ✅ |
| `stage3` | `matsurisu`, `matsurisu3`, `matsurisu` | `shubangelion`, `spiderchama` | `Matsurisu`, `Matsurisu3` ✅ | `Shubangelion_walk`, `Pekodam` ✅ |
| `stage4` | `moonafic`, `moonafic_vip`, `moonafic` | `udin`, `area15` | `Moonafic`, `Moonafic2` ✅ | `Udin_walk`, `Pekodam` ✅ |
| `stage5` | `nousagi_a`, `nousagi_b`, `nousagi_c` | `nanora`, `pekodam` | `NousagiA/B/C` ✅ | `Moonafic`, `Pekodam` ✅ |

Enemy/boss assets are resolved **by index**, not by id — `AssetLibrary.java:42-57` maintains parallel `enemyDirs`/`bossDirs`/`enemyPrefixes`/`bossPrefixes`/`enemyCounts`/`bossCounts` arrays. A reordered `ENEMIES`/`BOSSES` array would silently mis-assign art. Stage 1–5 currently align correctly.

Stage tile/palette: `ContentCatalog.java:253-257` → `grass`, `office`, `castle`, `carpet`, `fantasy`. All 5 folders exist under `assets/fullgame/stages/`.

**No Stage 1–5 stage depends on items, weapons, or collabs.** Stages reference only enemies and bosses.

---

## 5. Character / Item / Weapon Coupling

| Dependency | Present? | Evidence |
|-----------|----------|----------|
| Stage → item | ❌ none | `StageDef` has no item field |
| Stage → weapon | ❌ none | `StageDef` has no weapon field |
| Stage → character | ⚠️ indirect | completion is stored per-character (`FullGameCanvas.java:149,261`) |
| Character → basic attack | ✅ yes | `weaponLevels[0]` seeded at `RunWorld.java:206` |
| Character → item slots | ❌ none | items are universal |

---

## 6. Wiki Cross-Check

Source: <https://holocure.wiki.gg/wiki/Stage>

| HoloJava stage | Wiki entity | Notes |
|----------------|-------------|-------|
| Grassy Plains | real stage | first stage, no unlock |
| Holo Office | real stage | — |
| Halloween Castle | real stage | — |
| Gelora Bung Yagoo | real stage | — |
| Fantasy Island | real stage | — |
| `stage1h`–`stage4h` | "Hard"/night variants | HoloJava approximates; asset notes flag fallbacks |
| Concert Stage | Time Mode stage | `timeStage=true`, no bosses |

HoloJava's own `coverage` strings (`ContentCatalog.java:253-257`) declare: *"documented stage/boss timing; combat values approximated"* and *"Nanora asset approximated"*.

---

## 7. Findings

| # | Finding | Severity | Evidence |
|---|---------|----------|----------|
| S1 | `stage5.unlockRequirement = 99` → permanently open, breaks sequential chain | HIGH | `ContentCatalog.java:257` + `FullGameCanvas.java:259` |
| S2 | Stage list not filtered by `hard` — orders 5–8 visible in Stage Mode | MEDIUM | `FullGameCanvas.java:209` |
| S3 | `stageOrder > 7` rejects orders 8–9 silently | MEDIUM | `SaveStore.java:30` |
| S4 | Endless/Time modes reachable although out of scope | MEDIUM | `FullGameCanvas.java:307` |
| S5 | `99` comment says "never" but code means "always open" | LOW | `ContentCatalog.java:150` vs `:259` |
| S6 | Enemy/boss art bound by array index, not id | LOW | `AssetLibrary.java:42-57` |

---

## 8. Scope Verdict

Stage 1–5 (indices 0–4) is a coherent, fully-asset-backed set with a correct sequential chain **except** for `stage5`. Hard variants and Time Mode are additive scope that should be hidden, not deleted.

**No unlock requirement, save format, or progression code was modified.**
