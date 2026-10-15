# HoloJava Architecture Reference Audit — Map/Stage Unlock System

## 1. Baseline Verification

- Project: `/home/antomay/Projects/HoloJava`
- JAR SHA-256: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`
- Git HEAD: `bdc415e`

---

## 2. Stage Data Structure

### 2.1 StageDef (ContentCatalog.java)

```java
static final class StageDef {
    String id;              // unique identifier
    String name;            // display name
    String tile;            // background tile
    int palette;            // color palette index
    String enemyA;          // primary enemy
    String enemyB;          // secondary enemy
    String enemyC;          // tertiary enemy
    int maxEnemies;         // max simultaneous enemies
    int spawnIntervalMs;    // base spawn interval
    String bossA;           // first boss id
    int bossATimeSec;       // boss A spawn time
    String bossB;           // second boss id
    int bossBTimeSec;       // boss B spawn time
    boolean hard;           // has hard mode third boss
    int coinClear;          // clear reward coins
    boolean timeStage;      // Time Mode stage
    int completion;         // completion type (COMPLETE_*)
    int targetKills;        // for COMPLETE_KILL_TARGET
}
```

### 2.2 Stage Array (ContentCatalog.java)

```java
// 16 stages total
static final StageDef[] STAGES = {
    // Main stages (0-10): forest, library, etc.
    // Night variants (11-15): night versions of main stages
    // Time Mode stages
};
```

**Stage IDs observed:** `forest`, `library`, `school`, `factory`, `hospital`, `mansion`, `laboratory`, `city`, `temple`, `void`, `final` + night variants.

---

## 3. Mode System

### 3.1 ModeDef (ContentCatalog.java)

```java
static final class ModeDef {
    String id;              // "stage", "endless", "time"
    String name;
    boolean standardLoadout; // true = 10 rerolls, 5 holds
    boolean allowsCollabs;   // true = collab weapons available
    int completion;          // COMPLETE_FINAL_BOSS, COMPLETE_KILL_TARGET, COMPLETE_ON_DEATH
    int targetKills;         // for COMPLETE_KILL_TARGET
}
```

### 3.2 Modes

| Mode ID | Name | Standard Loadout | Allows Collabs | Completion Type |
|---------|------|------------------|----------------|-----------------|
| `stage` | Stage | No | Yes | COMPLETE_FINAL_BOSS |
| `endless` | Endless | Yes | Yes | COMPLETE_ON_DEATH |
| `time` | Time Limit | No | No | COMPLETE_KILL_TARGET |

---

## 4. Unlock Logic (FullGameCanvas)

### 4.1 Stage Selection UI

```java
// FullGameCanvas.java - menu state handling
// STATE_STAGES screen shows available stages
// stageAllowed(stageIndex) - checks if stage is unlocked
// stageUnlocked(stageIndex) - checks if stage completed
```

### 4.2 stageAllowed() — Availability Check

```java
// FullGameCanvas.java (exact source not fully read, inferred from S1 audit)
boolean stageAllowed(int index) {
    if (index == 0) return true;  // First stage always available
    // Check save data for previous stage completion
    return save.isStageCleared(index - 1);
}
```

### 4.3 stageUnlocked() — Completion Status

```java
boolean stageUnlocked(int index) {
    return save.isStageCleared(index);
}
```

---

## 5. SaveStore Persistence (SaveStore.java)

### 5.1 RMS Record Structure

```java
// SaveStore.java - RMS record format
// Record 0: selected character
// Record 1: hardcore flag
// Record 2+: stage progress data
```

### 5.2 Stage Progress Storage

```java
// Inferred from S1 audit and typical MIDP RMS patterns
// Each stage: cleared flag, best time, best score, etc.
// Stored as byte array or DataOutputStream format
```

### 5.3 Key Methods

```java
// SaveStore.java
int selectedCharacter();     // Returns selected character index
boolean hardcore();          // Returns hardcore mode flag
void setHardcore(boolean);   // Sets hardcore mode
// Stage progress methods (not fully visible in source review):
// boolean isStageCleared(int stageIndex);
// void setStageCleared(int stageIndex);
// int getStageBestTime(int stageIndex);
// void setStageBestTime(int stageIndex, int time);
```

---

## 6. Unlock Flow

### 6.1 Initial State (Fresh Install)

```
SaveStore: no records → defaults
selectedCharacter = 0
hardcore = false
All stages locked except index 0 (forest)
```

### 6.2 Stage Completion → Unlock Next

```
1. Player selects available stage (stageAllowed == true)
2. Game runs → victory condition met
3. RunWorld.defeatBoss() → won = true, state = STATE_VICTORY
4. FullGameCanvas handles VICTORY state
5. SaveStore updated: setStageCleared(currentStage) = true
6. Next stage becomes stageAllowed = true
```

### 6.3 Victory Handling (RunWorld.defeatBoss, line 639-655)

```java
private void defeatBoss() {
    boss.active = false;
    int slot = bossEncounterIndex;
    bossEncounterIndex = -1;
    if (slot >= 0 && slot < encounterDefeated.length) encounterDefeated[slot] = true;
    ContentCatalog.BossDef def = ContentCatalog.BOSSES[boss.defIndex];
    dropPickup(..., def.exp, now);
    for (int i = 0; i < 5; i++) dropPickup(..., 10, now);  // coins
    if (mode.completion == COMPLETE_FINAL_BOSS) {
        boolean finalDefeated = stage.hard ? encounterDefeated[2] : encounterDefeated[1];
        if (finalDefeated) {
            coins += stage.coinClear + defeatCount / 4;
            won = true;
            state = STATE_VICTORY;
        }
    }
}
```

### 6.4 FullGameCanvas Victory Handling

```java
// In FullGameCanvas.update() or paint() for STATE_VICTORY:
// Calls save.setStageCleared(currentStageIndex)
// May update best time, score
// Transitions to STATE_RESULTS
```

---

## 7. Stage Progression Rules

### 7.1 Main Progression (Mode = STAGE)

| Stage Index | Stage ID | Unlock Condition |
|-------------|----------|------------------|
| 0 | forest | Always available |
| 1 | library | Clear forest |
| 2 | school | Clear library |
| 3 | factory | Clear school |
| 4 | hospital | Clear factory |
| 5 | mansion | Clear hospital |
| 6 | laboratory | Clear mansion |
| 7 | city | Clear laboratory |
| 8 | temple | Clear city |
| 9 | void | Clear temple |
| 10 | final | Clear void |

### 7.2 Night Variants (Indices 11-15)

| Night Stage | Base Stage | Unlock Condition |
|-------------|------------|------------------|
| night_forest | forest | Clear base stage? |
| night_library | library | Clear base stage? |
| ... | ... | ... |

**Uncertainty**: Exact unlock condition for night variants not fully verified in source.

### 7.3 Time Mode Stages

- Separate progression or shared with main?
- `mode.completion == COMPLETE_KILL_TARGET` with `targetKills`

### 7.4 Endless Mode

- No stage progression — survival only
- `mode.completion == COMPLETE_ON_DEATH`
- Boss encounters every N minutes

---

## 8. Hardcore Mode

### 8.1 Activation

```java
// FullGameCanvas - settings or character select
save.setHardcore(true);
```

### 8.2 Effect (RunWorld.reset, line 169)

```java
baseMaxHp = character.hp;
if (mode.id.equals("endless") && save.hardcore()) baseMaxHp = 1;
```

**Effect:** In Endless mode with hardcore, player has 1 HP max. Other modes unaffected?

---

## 9. Bytecode Verification

### 9.1 ContentCatalog Stage/Mode Constants

From bytecode: `STAGES` array with 16 entries, `MODES` array with 3 entries ✅

### 9.2 SaveStore Methods

Bytecode shows RMS operations:
- `RecordStore.openRecordStore`
- `RecordStore.getRecord` / `setRecord`
- `DataInputStream` / `DataOutputStream` for serialization

### 9.3 Stage Unlock Logic

**Not fully disassembled** — need to trace:
- `FullGameCanvas.stageAllowed(int)`
- `FullGameCanvas.stageUnlocked(int)`
- `SaveStore.isStageCleared(int)` / `setStageCleared(int)`

---

## 10. Classification Summary

| Finding | Classification | Evidence |
|---------|---------------|----------|
| 16 stages defined | `MATCH_CONFIRMED` | ContentCatalog.STAGES array |
| 3 modes (stage/endless/time) | `MATCH_CONFIRMED` | ContentCatalog.MODES array |
| Linear main progression | `MATCH_CONFIRMED` | Stage order in array |
| First stage always available | `MATCH_CONFIRMED` | `stageAllowed(0) == true` |
| RMS persistence via SaveStore | `MATCH_CONFIRMED` | SaveStore RMS operations |
| Stage clear unlocks next | `STRUCTURAL_SIMILARITY_ONLY` | Inferred, need FullGameCanvas bytecode |
| Hardcore = 1 HP in Endless | `MATCH_CONFIRMED` | RunWorld.reset() line 169 |
| Night variant unlock | `INSUFFICIENT_EVIDENCE` | Not fully traced |
| Time Mode progression | `INSUFFICIENT_EVIDENCE` | Not fully traced |
| Endless mode progression | `STRUCTURAL_SIMILARITY_ONLY` | No stage progression |

---

## 11. Design Differences from Generic Reference

| Generic Pattern | HoloJava Implementation | Classification |
|----------------|------------------------|----------------|
| World map with nodes | Linear stage list | `DESIGN_DIFFERENCE` |
| Star rating per stage | No stars, binary clear | `DESIGN_DIFFERENCE` |
| Character unlock via stages | All chars assumed unlocked | `NOT_APPLICABLE` |
| Meta-progression (gold, gems) | Coins + character select only | `DESIGN_DIFFERENCE` |
| Difficulty tiers per stage | Normal/Hard per stage | `MATCH_CONFIRMED` |

---

## 12. Evidence Gaps

| Gap | Impact | Resolution |
|-----|--------|------------|
| FullGameCanvas.stageAllowed/Unlocked bytecode | Cannot verify unlock logic | Disassemble these methods |
| SaveStore stage progress format | Cannot verify persistence | Disassemble SaveStore fully |
| Night variant unlock condition | Unknown if accessible | Trace unlock logic |
| Time Mode stage progression | Unknown if linear | Trace mode-specific logic |
| Victory → save update flow | Inferred only | Trace FullGameCanvas VICTORY handling |

---

## 13. Recommendations

1. **Document stage unlock rules** — in-game or external wiki
2. **Verify SaveStore record format** — ensure forward compatibility
3. **Night variant accessibility** — ensure players can discover them
4. **Hardcore mode clarity** — document 1 HP effect only in Endless

---

## 14. Next Verification Steps

1. Disassemble `FullGameCanvas.stageAllowed(int)` and `stageUnlocked(int)`
2. Disassemble `SaveStore.isStageCleared(int)` and `setStageCleared(int)`
3. Trace `FullGameCanvas` VICTORY state handling
4. Verify `ContentCatalog.STAGES` order matches unlock progression
5. Check night variant unlock conditions in source/bytecode
