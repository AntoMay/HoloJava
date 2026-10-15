# Diff and Regression Review

## 1. Complete Diff

```diff
diff --git a/src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java b/src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java
index c88f26b..dc19b17 100644
--- a/src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java
+++ b/src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java
@@ -165,7 +165,19 @@ final class ContentCatalog {
         String source;
     }
 
-    static final CharacterDef[] CHARACTERS = WikiCatalogData.buildCharacters();
+    // Playable roster: only characters with verified local animation assets are exposed.
+    // Order is load-bearing: AssetLibrary maps asset groups Gura, Ame, Calli, Kiara, Ina to
+    // character indices 0-4 (AssetLibrary.groupIndex), and each entry's mainWeapon must
+    // resolve against BASE_WEAPONS for validate() to pass.
+    static final CharacterDef[] BASE_CHARACTERS = {
+        character("gura", "Gawr Gura", 65, 110, 140, 5, 1, "trident_thrust", "Shark Call", "Shark Bite", true, "Gura"),
+        character("ame", "Amelia Watson", 75, 130, 135, 10, 3, "pistol_shot", "Slow Time", "Hunter's Instinct", true, "Ame"),
+        character("calli", "Mori Calliope", 70, 115, 130, 10, 4, "scythe_swing", "The Reaper", "Undying", true, "Calli"),
+        character("kiara", "Takanashi Kiara", 90, 100, 140, 5, 3, "phoenix_sword", "Phoenix Fire", "Second Flame", true, "Kiara"),
+        character("ina", "Ninomae Inanis", 75, 90, 150, 1, 1, "summon_tentacle", "Spirit of the Oni", "The Ancient One", true, "Ina")
+    };
+
+    static final CharacterDef[] CHARACTERS = WikiCatalogData.buildCharacters(BASE_CHARACTERS);
 
     // Additional documented roster is tracked in data/wiki/characters.csv. Entries without
     // verified local animation assets are defined but not presented as playable.
diff --git a/src/java-me-fullgame/me/holojava/fullgame/WikiCatalogData.java b/src/java-me-fullgame/me/holojava/fullgame/WikiCatalogData.java
index 6765c7e..94fd8bd 100644
--- a/src/java-me-fullgame/me/holojava/fullgame/WikiCatalogData.java
+++ b/src/java-me-fullgame/me/holojava/fullgame/WikiCatalogData.java
@@ -160,10 +160,15 @@ public class WikiCatalogData {
 
 
     /**
-     * Stub method: returns empty array. Full implementation would parse characters.csv.
+     * Returns the playable character roster defined in {@code ContentCatalog.BASE_CHARACTERS}.
+     * Entries are limited to characters with verified local animation assets and a main weapon
+     * that resolves against {@code ContentCatalog.BASE_WEAPONS}; see {@code ContentCatalog.validate()}.
+     *
+     * <p>Does not read {@code /data/wiki/characters.csv}. That resource is not packaged into the
+     * build output, and its rows use display-name weapons that would fail validation.
      */
-    static final CharacterDef[] buildCharacters() {
-        return new CharacterDef[0];
+    static final CharacterDef[] buildCharacters(CharacterDef[] baseCharacters) {
+        return baseCharacters;
     }
```

---

## 2. Diff Statistics

| File | Insertions | Deletions | Net |
|------|-----------|-----------|-----|
| `ContentCatalog.java` | 14 | 1 | +13 |
| `WikiCatalogData.java` | 11 | 3 | +8 |
| **Total** | **21** | **4** | **+17** |

`git diff --check`: clean.

---

## 3. Scope Conformance

| Instruction | Status | Evidence |
|-------------|--------|----------|
| Only necessary code changed | ✅ | 2 files, both catalog data + its factory |
| `SaveStore` unchanged | ✅ | absent from diff |
| Stage unlock unchanged | ✅ | absent from diff |
| `FullGameCanvas` unchanged | ✅ | absent from diff |
| Skill / Special / Item / Collab / Hard-Time untouched | ✅ | absent from diff |
| No CSV or asset modified | ✅ | `git status` shows only the 2 source files |
| No 6th character | ✅ | exactly 5 entries |
| No 47-row CSV import | ✅ | no CSV read at runtime; documented why |
| No invented data | ✅ | every field bytecode-proven |
| No game/emulator | ✅ | build + verifier only |
| No commit | ✅ | HEAD still `666e338` |
| No history change | ✅ | `git rev-parse` unchanged |
| Out-of-scope data preserved | ✅ | all prior reports, CSVs, assets intact |

---

## 4. Regression Surface

### 4.1 Static-initialization order

`CHARACTERS` now depends on `BASE_CHARACTERS`, which is declared immediately before it. Both are `static final` in the same class; the factory `character(...)` only allocates objects and touches no other static field.

**Verified safe**: the build compiled and preverified without a `NoSuchFieldError`-class problem, and `BASE_WEAPONS` is referenced by `validate()` rather than by `BASE_CHARACTERS` construction.

### 4.2 Consumers now reachable

| Consumer | Behavior change |
|----------|-----------------|
| `AssetLibrary:18` | array grows 0 → 5; preload loop at `:66-79` now executes |
| `AssetLibrary:137-149` | `characters[index]` resolves instead of failing |
| `AssetLibrary:151-158` | portrait lookup now attempted (still mismatched — §5) |
| `RunWorld:145,208` | character stats readable |
| `RunWorld:270` | skill label displays |
| `SaveStore:24,30` | `CHARACTERS.length` bound 0 → 5 |
| `FullGameCanvas:187,194,200,315,322,335` | character menu populated |

### 4.3 Behavior changes that follow necessarily

| # | Change | Why | Risk |
|---|--------|-----|------|
| R1 | `SaveStore.isCompleted`/`setCompleted` now accept indices 0–4 | bound derives from `CHARACTERS.length` | none — previously all writes were no-ops |
| R2 | `validate()` now actually runs 5 iterations | loop was vacuous at length 0 | none — all checks pass |
| R3 | Character menu shows 5 entries | catalog restored | none |
| R4 | `save.setSelectedCharacter` can now persist a real index | `0x3f` mask, index 0–4 valid | none |
| R5 | A run can now start (previously would have thrown at `RunWorld:145`) | catalog restored | none — strictly an improvement |

No existing correct behavior was altered. Every change is a **first-time activation** of previously dead code paths.

---

## 5. Deferred Findings — Deliberately NOT Fixed

| # | Finding | Severity | Priority |
|---|---------|----------|----------|
| **F1** | Portrait path mismatch — `AssetLibrary:155` resolves `portraits/<catalog_id>.png` (`gura.png`) but files are named by wiki id (`gawr_gura.png`) | HIGH | **A2** |
| F2 | `stage5.unlockRequirement = 99` breaks the sequential chain (should be `3`) | HIGH | A4 |
| F3 | Hard/Time stages (orders 5–8) and modes visible in UI | MEDIUM | A3 |
| F4 | `SaveStore.setCompleted` rejects `stageOrder > 7`; orders 8–9 silently dropped | MEDIUM | A4 |
| F5 | Skill and Special have no gameplay effect (`RunWorld:1263-1264`) | MEDIUM | B1/B2 |
| F6 | Ina `special` = "Spirit of the Oni" (JAR) vs "Tako Spin" (CSV) | LOW | B2 |
| F7 | `uber_sheep` / `EFFECT_FOOD_DROP` never applied | MEDIUM | C1 |
| F8 | 26 CSV collab recipes would NPE if `data/` were packaged | MEDIUM (latent) | C2 |
| F9 | `ItemDef.sprite` dead metadata | LOW | C3 |

**F1 was explicitly excluded** by the A1 instruction set and remains open as Priority A2. The catalog works without it — only the character-select portrait image is missing.

---

## 6. Residual Risk

| Risk | Level | Note |
|------|-------|------|
| Compilation | **NONE** | build + verifier green |
| Index misalignment | **NONE** | order matches `groupIndex()` and preload arrays |
| Validator failure | **NONE** | all 5 pass both checks |
| Runtime-only issues | **UNKNOWN** | no emulator run per instructions; A2–A4 and manual testing still required |
| Legacy save compatibility | **OUT OF SCOPE** | unaffected by A1; `SaveStore` untouched |

---

## 7. Verdict

**Diff is minimal, in-scope, and fully evidenced.**

The change restores data that was already proven correct by the reference JAR, using the file's own existing `BASE_*` pattern. No logic was rewritten, no behavior was redefined, and no deferred item was touched.

**A1 is complete and ready for review.**
