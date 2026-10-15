# HoloJava — Alternative A: Minimal Fix Design

## Scope
Fix only the explicit progression bug (`stage5` unlock) and clamp SaveStore to 5 stages. No migration, no versioning.

---

## Changes Required

### 1. `ContentCatalog.java` — Fix Fantasy Island unlock
```java
// Line 257: Change unlockRequirement from 99 to 4
stage("stage5", "Fantasy Island", false, false, 4, "fantasy", 4, 
      "nousagi_a", "nousagi_b", "nousagi_c", 850, 36, 15000, 
      4,  // was 99
      "nanora", 600, "pekodam", 1200, ...)
```

### 2. `SaveStore.java` — Clamp stageOrder to 0-4
```java
// Line 24: isCompleted
if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length 
    || stageOrder < 0 || stageOrder > 4) return false;  // was 7

// Line 30: setCompleted
if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length 
    || stageOrder < 0 || stageOrder > 4) return;  // was 7
```

### 3. `FullGameCanvas.java` — Hide Hard/Time from Stage Select
```java
// Line 251-255: stageAllowed — already filters by mode, no change needed for Stage Mode
// Line 307: drawModes — change items to hide Endless/Time (UI only)
String[] items = { "Stage Mode", "Back" };  // was 4 items
```

---

## Behavior After Fix

| Stage | order | unlockRequirement | Accessible? |
|-------|-------|-------------------|-------------|
| stage1 | 0 | -1 | ✅ Always |
| stage2 | 1 | 0 | ✅ After stage1 |
| stage3 | 2 | 1 | ✅ After stage2 |
| stage4 | 3 | 2 | ✅ After stage3 |
| stage5 | 4 | **4** (fixed) | ✅ After stage4 |

- Hard stages (order 5-8) and Time (order 9) **rejected by `stageOrder > 4` clamp**
- Mode Select still shows Endless/Time but they're non-functional (no save path)

---

## Legacy Save Compatibility

| Scenario | Result |
|----------|--------|
| Fresh install | ✅ Works |
| Legacy 6-byte save | ❌ **Corrupts** — transpose bug unchanged |
| Current 48-byte save | ⚠️ Partial — stages 5-7 lost (now clamped) |

**No migration implemented** — legacy users lose/corrupt progress.

---

## Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| Legacy save corruption | **HIGH** | Not addressed |
| Silent data loss on save failure | **HIGH** | Not addressed |
| Future stage addition requires re-fix | **MEDIUM** | Hardcoded clamp |
| Hard/Time code rot | **LOW** | Unused but compiled |

---

## Testing Required

| Test Case | Expected |
|-----------|----------|
| Fresh install → Stage 1 only unlocked | Pass |
| Complete Stage 1 → Stage 2 unlocked | Pass |
| Complete Stage 4 → Stage 5 unlocked | Pass |
| Complete Stage 5 → No further stages | Pass |
| Restart game → Progress persisted | Pass |
| Load legacy 6-byte save | **Fail (corrupt)** |
| Save failure (RMS full) | Silent loss (unchanged) |

---

## Verdict

**Not recommended** as standalone solution. Only fixes the explicit `stage5` bug but leaves:
- Fundamental legacy format incompatibility
- Silent save failures
- No extensibility for future stages

Use only if migration effort is absolutely prohibited and legacy saves are acceptable loss.