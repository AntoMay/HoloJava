# HoloJava — PB-04 HUD String Allocation Review

## 1. Executive Summary

**Status**: `READY_FOR_EXPLICIT_APPROVAL` — Optimization confirmed, ready for approval

**Issue**: `RunWorld.drawHud()` performs per-frame string concatenation using `+` operator, creating temporary String objects every render frame (~60 FPS).

## 2. Current Implementation Analysis

### 2.1 Source Code (RunWorld.java, Lines 1476-1495)

```java
private void drawHud(Graphics g, int offX, int offY, long now) {
    Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    g.setFont(small);
    g.setColor(0, 0, 0);
    
    // Line 1: HP
    g.drawString("HP " + playerHp + "/" + playerMaxHp, offX + 4, offY + 4, Graphics.TOP | Graphics.LEFT);
    
    // Line 2: Level & EXP
    g.drawString("Lv" + playerLevel + "  EXP " + experience + "/" + expNeed, offX + 4, offY + 19, Graphics.TOP | Graphics.LEFT);
    
    // Line 3: Time & Kills
    g.drawString("Time " + timeString() + "  Kill " + kills, offX + 4, offY + 34, Graphics.TOP | Graphics.LEFT);
    
    // Line 4: Coins, Weapons, Items
    g.drawString("Coins " + coins + "  W" + weaponCount + " I" + itemCount, offX + 4, offY + 49, Graphics.TOP | Graphics.LEFT);
    
    // Conditional: Fans (Time Mode)
    if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET) {
        g.drawString("Fans " + kills + "/" + mode.targetKills, offX + worldWidth - 90, offY + 4, Graphics.TOP | Graphics.LEFT);
    }
    
    // Conditional: Boss HP bar
    if (boss.active) {
        // ... draw rects ...
        g.drawString(bossName(), offX + worldWidth / 2, offY + 80, Graphics.TOP | Graphics.HCENTER);
    }
    
    // Line 5: Controls hint
    g.setColor(200, 200, 200);
    g.drawString("5 Skill  9 Special", offX + worldWidth - 105, offY + worldHeight - 14, Graphics.TOP | Graphics.LEFT);
}
```

### 2.2 String Concatenation Analysis

| Line | Expression | Concatenations | Allocations/Frame | Changes |
|------|------------|----------------|-------------------|---------|
| 1 | `"HP " + playerHp + "/" + playerMaxHp` | 3 | 3-4 | Every frame |
| 2 | `"Lv" + playerLevel + "  EXP " + experience + "/" + expNeed` | 4 | 4-5 | Every frame |
| 3 | `"Time " + timeString() + "  Kill " + kills` | 3 | 3-4 | Every frame |
| 4 | `"Coins " + coins + "  W" + weaponCount + " I" + itemCount` | 4 | 4-5 | Every frame |
| 5 (cond) | `"Fans " + kills + "/" + mode.targetKills` | 2 | 2-3 | Time Mode only |
| 6 (cond) | `bossName()` + position | 1 | 1 | Boss active only |
| 7 | `"5 Skill  9 Special"` | 0 | 0 | Constant |

**Total per frame (gameplay)**: ~14-18 String allocations
**Total per frame (Time Mode + Boss)**: ~18-22 String allocations

### 2.3 Value Change Frequency

| Value | Change Frequency | Cacheable? |
|-------|------------------|------------|
| `playerHp` | On damage/heal | Yes |
| `playerMaxHp` | On max HP change | Yes |
| `playerLevel` | On level up | Yes |
| `experience` | On EXP gain | Yes |
| `expNeed` | On level up | Yes |
| `timeString()` | Every second | Yes (1s cache) |
| `kills` | On kill | Yes |
| `coins` | On coin pickup | Yes |
| `weaponCount` | On weapon acquire | Yes |
| `itemCount` | On item acquire | Yes |
| `mode.targetKills` | Never (constant) | Yes |
| `bossName()` | On boss spawn | Yes |
| `boss.hp`/`boss.maxHp` | Every frame (during boss) | No |

## 3. Bytecode Verification

### 3.1 drawHud Bytecode (RunWorld.class)

Key observations from bytecode:
- Each `drawString` call preceded by multiple string concatenation operations
- Java compiler translates `+` to `StringBuilder.append()` calls
- Each frame creates new `StringBuilder` instances
- `timeString()` called every frame (creates new String)

### 2.3 Allocation Estimate

| Metric | Value |
|--------|-------|
| Frames/second | ~60 |
| String allocations/frame | 14-22 |
| Allocations/second | 840-1,320 |
| Bytes/frame (est.) | 500-800 bytes |
| GC pressure | Moderate on 1-2 MB heap |

## 4. Optimization Options Analysis

### Option A: Status Quo (No Change)
- **Pros**: No code changes, zero risk
- **Cons**: Continued GC pressure, potential frame hitches on low-end devices
- **Verdict**: Acceptable for current scope, but optimization recommended

### Option B: Local StringBuffer per Frame
```java
private void drawHud(Graphics g, int offX, int offY, long now) {
    StringBuffer sb = new StringBuffer();
    // ... append to sb, sb.toString() for drawString
}
```
- **Pros**: Single StringBuffer per frame instead of multiple StringBuilders
- **Cons**: Still creates 1 StringBuffer + 4-6 Strings per frame
- **Improvement**: ~50% fewer temporary objects

### Option C: Cached HUD Strings (Recommended)
```java
private String cachedHpString;
private int lastPlayerHp = -1, lastPlayerMaxHp = -1;
// ... similar for other fields

private void drawHud(Graphics g, int offX, int offY, long now) {
    if (playerHp != lastPlayerHp || playerMaxHp != lastPlayerMaxHp) {
        cachedHpString = "HP " + playerHp + "/" + playerMaxHp;
        lastPlayerHp = playerHp; lastPlayerMaxHp = playerMaxHp;
    }
    g.drawString(cachedHpString, ...);
    // ... similar for other lines
}
```
- **Pros**: Near-zero allocations when values unchanged; only rebuilds on value change
- **Cons**: More code, needs cache invalidation logic
- **Improvement**: ~95%+ reduction in allocations (only rebuilds on value change)

### Option D: StringBuffer + Invalidate on Change (Hybrid)
```java
private StringBuffer hudBuffer = new StringBuffer();
private long lastHudUpdate = 0;
private int lastPlayerHp = -1, lastPlayerMaxHp = -1, ...;

private void drawHud(Graphics g, int offX, int offY, long now) {
    boolean dirty = (playerHp != lastPlayerHp || ...);
    if (dirty || hudBuffer.length() == 0) {
        hudBuffer.setLength(0);
        // rebuild all lines into buffer
        // update last* fields
    }
    // draw from buffer substrings or toString()
}
```
- **Pros**: Single buffer reuse, explicit dirty tracking
- **Cons**: Complex substring extraction for drawString

## 3. CLDC 1.1 Compatibility

| Feature | CLDC 1.1 Support | Notes |
|---------|------------------|-------|
| `StringBuffer` | ✅ Yes | `java.lang.StringBuffer` |
| `StringBuilder` | ❌ No | Added in CLDC 1.1? No, J2SE 5.0. Use `StringBuffer` |
| `String` concat | ✅ Yes | Compiler handles |
| `StringBuffer.setLength(0)` | ✅ Yes | Clear buffer |
| `StringBuffer.append()` | ✅ Yes | Multiple overloads |

**Note**: `StringBuilder` is NOT available in CLDC 1.1. Must use `StringBuffer`.

## 4. Option Comparison

| Criteria | Option B (StringBuffer/frame) | Option C (Cached Strings) | Option D (Hybrid) |
|----------|-------------------------------|---------------------------|-------------------|
| Allocations/frame (steady) | ~4-6 Strings | ~0-2 (only changed) | ~0-2 |
| Code complexity | Low | Medium | Medium-High |
| Cache invalidation | N/A | Manual per field | Manual + flag |
| CLDC 1.1 compatible | ✅ | ✅ | ✅ |
| Maintainability | High | Medium | Medium |
| GC pressure reduction | ~50% | ~95%+ | ~95%+ |
| Risk of stale display | None | Low (if invalidation correct) | Low |

## 4. Recommended Approach: Option C (Cached Strings)

### 4.1 Implementation Sketch

```java
// Add fields to RunWorld
private String cachedHpString = "";
private String cachedLevelExpString = "";
private String cachedTimeKillString = "";
private String cachedCoinsString = "";
private String cachedFansString = "";
private String cachedBossName = "";
private String cachedControlsHint = "5 Skill  9 Special";

private int lastPlayerHp = -1, lastPlayerMaxHp = -1;
private int lastPlayerLevel = -1, lastExperience = -1, lastExpNeed = -1;
private long lastTimeStringUpdate = 0;
private String cachedTimeString = "";
private int lastKills = -1;
private int lastCoins = -1, lastWeaponCount = -1, lastItemCount = -1;
private int lastFansKills = -1;
private String lastBossName = null;

// In drawHud:
private void drawHud(Graphics g, int offX, int offY, long now) {
    // HP
    if (playerHp != lastPlayerHp || playerMaxHp != lastPlayerMaxHp) {
        cachedHpString = "HP " + playerHp + "/" + playerMaxHp;
        lastPlayerHp = playerHp; lastPlayerMaxHp = playerMaxHp;
    }
    g.drawString(cachedHpString, offX + 4, offY + 4, Graphics.TOP | Graphics.LEFT);
    
    // Level & EXP
    if (playerLevel != lastPlayerLevel || experience != lastExperience || expNeed != lastExpNeed) {
        cachedLevelExpString = "Lv" + playerLevel + "  EXP " + experience + "/" + expNeed;
        lastPlayerLevel = playerLevel; lastExperience = experience; lastExpNeed = expNeed;
    }
    g.drawString(cachedLevelExpString, ...);
    
    // Time & Kills (timeString caches per second)
    if (now - lastTimeStringUpdate >= 1000) {
        cachedTimeString = timeString();
        lastTimeStringUpdate = now;
    }
    if (kills != lastKills || cachedTimeKillString == null) {
        cachedTimeKillString = "Time " + cachedTimeString + "  Kill " + kills;
        lastKills = kills;
    }
    g.drawString(cachedTimeKillString, ...);
    
    // ... similar for other lines
}
```

### 3.2 Expected Allocation Reduction

| Scenario | Current | Optimized | Reduction |
|----------|---------|-----------|-----------|
| Steady state (no value change) | 14-22/frame | 0-2/frame | 95%+ |
| Active combat (HP/EXP changing) | 14-22/frame | 4-8/frame | 60-70% |
| Boss fight + Time Mode | 22/frame | 6-10/frame | 65%+ |

## 4. CLDC 1.1 Compatibility Confirmed

- `StringBuffer` ✅ Available
- String concatenation ✅ Works
- No `StringBuilder` needed (not in CLDC 1.1)
- All String methods used are CLDC 1.1 compatible

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Stale cached values | Low | Visual glitch | Explicit invalidation on every value change |
| Cache miss logic bug | Low | Extra allocation | Code review + deterministic tests |
| Increased code size | Certain | +100-150 lines | Acceptable for hot path |
| Thread safety | N/A (single-threaded render) | N/A | Single render thread |

## 6. Verification Plan

| Test Case | Expected Behavior |
|-----------|-------------------|
| Steady state (no gameplay changes) | Zero string allocations in drawHud |
| Player takes damage | Only HP line rebuilt |
| Player gains EXP | Only Level/EXP line rebuilt |
| Time advances 1 second | Time string rebuilds (via timeString cache) |
| Boss spawns | Boss name cached until despawn |
| Time Mode | Fans line cached until kills change |
| Rapid HP changes (regen + damage) | HP line rebuilds each change |

## 5. Readiness Decision

**Status**: `READY_FOR_EXPLICIT_APPROVAL`

**Recommended Approach**: Option C (Cached Strings) — maximum allocation reduction, deterministic behavior, CLDC 1.1 compatible.

**Scope**: ~150 lines added to RunWorld (fields + cache logic in drawHud)
**Risk**: Low (single-threaded render, explicit invalidation)
**Rollback**: Trivial (revert to string concat)

## 6. Verification Artifacts

- RunWorld.java `drawHud()` (lines 1476-1495)
- RunWorld.drawHud() bytecode (string concat operations visible)
- Phase 3 report: `05_BUG_CANDIDATE_RECONCILIATION.md` (PB-04 section)

## 6. Recommendation

**Approve Option C (Cached Strings)** for implementation. Provides maximum allocation reduction with deterministic behavior and minimal risk.
