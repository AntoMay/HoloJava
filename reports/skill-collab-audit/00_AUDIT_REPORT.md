# Skill Character & Weapon Collab Audit (READ-ONLY)

| Item | Value |
|------|-------|
| Checkpoint | `65dca7af7a972a2a597d2f70213d4af40ac6215d` |
| Working tree | clean (tracked) |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |

**No code, asset, data, or test was modified. No commit was made.**

---

## A. Skill and Special

### A.1 Activation chain

| Step | Location | Behaviour |
|------|----------|-----------|
| Key input | `FullGameCanvas.java:107` | `if (key == '5') skillPressed = true;` |
| Key input | `FullGameCanvas.java:108` | `if (key == '9') specialPressed = true;` |
| Documented | `FullGameCanvas.java` `drawControls` | `"5: skill   9: special attack"` ✅ |
| Gate | `RunWorld.java:383` | `if (skillPressed && skillAcquired && now >= skillReadyAt) activateSkill(now);` |
| Gate | `RunWorld.java:384` | `if (specialPressed && !specialUsed) activateSpecial(now);` |
| Unlock | `RunWorld.java:1170` | `if (r < 7 && !skillAcquired) return OFFER_SKILL;` — 7/20 chance, first two offer slots |
| Unlock | `RunWorld.java:1263-1264` | `if (o.type == OFFER_SKILL) skillAcquired = true;` |
| Initial state | `RunWorld.java:185` | `skillAcquired = false; skillReadyAt = 0; specialUsed = false;` |

**Skill is gated behind a level-up offer. Special has no gate at all** — `specialUsed` starts `false`, so the special attack is available from the first second of every run with no unlock.

### A.2 Per-character table — names, effects, and parameters

All values read from `RunWorld.java:387-421`. `damageEnemiesInRadius(radius, damage, knockback)`; the third argument is **knockback**, and `damageEnemy` is called with `critical = false` in every branch.

| Idx | Character | Skill (displayed) | Skill effect | Cooldown | Special (in source) | Special effect |
|-----|-----------|-------------------|--------------|----------|---------------------|----------------|
| 0 | Gura `gura` | **Shark Bite** | `damageEnemiesInRadius(70, 6, true)` — r70, dmg 6, knockback 5 | **15 000 ms** (`:388`) | Shark Call | `damageEnemiesInRadius(110, 18, true)` — r110, dmg 18, knockback 5 |
| 1 | Ame `ame` | **Hunter's Instinct** | `slowEnemiesUntil = now + 3500`; each active enemy's `nextAttack = now + 1200` | 15 000 ms | Slow Time | `slowEnemiesUntil = now + 6000` |
| 2 | Calli `calli` | **Undying** | `shield = 20`; `shieldReadyAt = now + 15000` | 15 000 ms | The Reaper | `playerHp = min(playerMaxHp, playerHp + max(8, playerMaxHp/4))`; `lifestealUntil = now + 10000` |
| 3 | Kiara `kiara` | **Second Flame** | `lifestealUntil = now + 8000` | 15 000 ms | Phoenix Fire | `damageEnemiesInRadius(130, 24, true)` — r130, dmg 24, knockback 5 |
| 4 | Ina `ina` | **The Ancient One** | `invulUntil = now + 2500`; `damageEnemiesInRadius(45, 4, false)` — r45, dmg 4, **no knockback** | 15 000 ms | Spirit of the Oni | `shield = 30`; `shieldReadyAt = now + 10000`; `clearEnemyShots()` |

### A.3 Are the effects real gameplay, or cosmetic? — **All are real**

Each assigned state variable was traced to a consumer:

| Variable | Assigned at | Consumed at | Effect |
|----------|-------------|-------------|--------|
| `slowEnemiesUntil` | `:392`, `:410` | `:491` `boolean slow = now < slowEnemiesUntil`; `:618` boss speed × 0.35 | ✅ enemies and bosses slowed |
| `shield` | `:395`, `:417` | `:973-975` `absorbed = min(shield, dealt); shield -= absorbed; dealt -= absorbed;` | ✅ absorbs damage |
| `shieldReadyAt` | `:396`, `:418` | `:961` `if (shieldReadyAt > 0 && now >= shieldReadyAt) { shield = 15; ... }` | ✅ recurring shield refresh |
| `lifestealUntil` | `:398`, `:413` | `:997` `if (now < lifestealUntil) playerHp = min(playerMaxHp, playerHp + 1);` | ✅ heals 1 HP/tick |
| `invulUntil` | `:400` | `:551`, `:674`, `:981` `playerInvulnerable(now)` | ✅ contact damage skipped |
| `playerHp` heal | `:412` | `:974` cap and `:997` clamp | ✅ |
| `clearEnemyShots()` | `:419` | `:419` body sets `shots[i].active = false` for non-player shots | ✅ clears bullets |

**No skill or special is text-only or visual-only.** All five produce measurable gameplay changes.

### A.4 Branch isolation — no fall-through

`RunWorld.java:389-402` (skill) and `:407-420` (special) use `if / else if / else` on `characterIndex` with **no fall-through**: every branch either returns its own effect or is skipped entirely.

`characterIndex` is bounded to `[0, CHARACTERS.length)` — `FullGameCanvas.java:59` clamps on load, and `:201` assigns only from the filtered character menu. The trailing `else` therefore only ever serves index 4 (Ina), and with `CHARACTERS.length == 5` it is unreachable for any other index.

**Conclusion: effects do not leak into another character's branch.**

### A.5 Name binding is positional, not verified by name

| Source | Status |
|--------|--------|
| `ContentCatalog.java` `CharacterDef.skill` / `.special` | names present, e.g. `"Shark Bite"`, `"Shark Call"` |
| `character.skill` read | **exactly once** — `RunWorld.java:270`, the level-up offer label |
| `character.special` read | **never** — zero references outside `ContentCatalog.java` |
| `RunWorld` effect selection | by `characterIndex`, **not by name** |

The inline comments in `RunWorld.java` (`// Shark Bite`, etc.) match the catalog exactly, but they are comments. **Nothing enforces that the effect selected for index *n* corresponds to the name stored at index *n*.** They agree today because catalog order and asset-group order were both fixed to `gura, ame, calli, kiara, ina` in A1.

### A.6 Inconsistencies found

| # | Finding | Severity | Evidence |
|---|---------|----------|----------|
| S1 | **Special name is never shown to the player.** `character.special` has no reader anywhere in `src/` | MEDIUM | grep `.special` outside `ContentCatalog.java` → none |
| S2 | **Special has no unlock gate.** Wiki documents a shop upgrade; here `specialUsed` starts `false` (`:185`) so key `9` works immediately | MEDIUM | `:185`, `:384` |
| S3 | `RunWorld.java:361` — `if (now < slowEnemiesUntil) { /* player remains unaffected by enemy slow */ }` has an **empty body**. Dead statement | LOW | `:361` |
| S4 | `skillAcquired()` / `specialUsed()` accessors (`:239-240`) have **no callers** — dead code | LOW | grep across `src/` → no callers |
| S5 | Skill cooldown is a flat 15 s for all five characters; no per-character variance is encoded | INFO | `:388` outside the branch chain |

### A.7 CSV and bytecode comparison

| Character | Runtime name | `characters.csv` `special_attack` | JAR bytecode | Match |
|-----------|--------------|-----------------------------------|--------------|-------|
| Gura | Shark Call | `Shark Call` | `ldc` string present | ✅ |
| Ame | Slow Time | `Slow Time` | present | ✅ |
| Calli | The Reaper | `The Reaper` | present | ✅ |
| Kiara | Phoenix Fire | `Phoenix Fire` | present | ✅ |
| **Ina** | **Spirit of the Oni** | **`Tako Spin`** | `ldc #117 // String Spirit of the Oni` | ❌ **CSV differs** |

`characters.csv` has **no skill column at all** — the five skill names exist only in the catalog (originally recovered from JAR bytecode) and in `RunWorld` comments.

**Unverifiable:** no runtime execution was permitted, so effect *balance* (whether 6 damage is appropriate, whether 15 s is the right cooldown) cannot be assessed — only that the code does what it says.

---

## B. Weapon Collab

### B.1 Active recipe set

`ContentCatalog.java:284-291` — the static initialiser tries `/data/wiki/collabs.csv` first, falls back to 5 hardcoded recipes. The resource is **not packaged** (`unzip -l` on the built JAR shows **0** `data/` entries), so the hardcoded 5 are always what runs.

| Id | Result weapon | Material A | Material B | CSV A / B | Match |
|----|---------------|-----------|-----------|-----------|-------|
| `absolute_wall` | Absolute Wall | `bounce_ball` | `cutting_board` | Bounce Ball / Cutting Board | ✅ |
| `bl_fujoshi` | BL Fujoshi | `bl_book` | `psycho_axe` | BL Book / Psycho Axe | ✅ |
| `black_plague` | Black Plague | `ens_curce`→`ens_curse` | `owl_dagger` | EN's Curse / Owl Dagger | ✅ |
| `blood_lust` | Blood Lust | `owl_dagger` | `psycho_axe` | Owl Dagger / Psycho Axe | ✅ |
| `legendary_sausage` | Legendary Sausage | `bl_book` | `sausage` | BL Book / Sausage | ✅ |

All five `CollabDef` recipes match both the CSV **and** the corresponding `WeaponDef.prerequisiteA/prerequisiteB` fields — a three-way agreement.

### B.2 Trigger conditions

`RunWorld.java:1208-1218`:

```java
if (weaponCount < 6 && mode.allowsCollabs) {
    for (int c = 0; c < ContentCatalog.COLLABS.length; c++) {
        CollabDef cd = ContentCatalog.COLLABS[c];
        int a = weaponLevelById(cd.weaponA); int b = weaponLevelById(cd.weaponB);
        WeaponDef wd = ContentCatalog.weapon(cd.id);
        if (a == wd.maxLevel && b == wd.maxLevel && !offerUsed[wd.id.hashCode() & 63]) { ... }
```

| Requirement | Value | Evidence |
|-------------|-------|----------|
| Material level | **both at `maxLevel` = 7** | all materials and results declare `maxLevel 7` |
| Slot space | `weaponCount < 6` | `:1208` |
| Mode gate | `mode.allowsCollabs` | Stage Mode ✅, Endless ✅, Time Mode ❌ |
| Once per offer cycle | `offerUsed[...]` | `:1213` |

All 7 distinct materials (`bounce_ball`, `cutting_board`, `bl_book`, `psycho_axe`, `ens_curse`, `owl_dagger`, `sausage`) are **category 0**, so they are randomly offered at `:1220-1228`. Reachable in principle.

### B.3 Consumption semantics

`RunWorld.java:1267-1270`:

```java
if (o.collab) {
    removeWeaponById(def.prerequisiteA);
    removeWeaponById(def.prerequisiteB);
    addWeapon(o.value, 1);
}
```

**Both** materials are removed and the result is added at **level 1** — consistent with wiki semantics and internally consistent across all five recipes.

`removeWeaponById` (`:1105`-adjacent) shifts slots down and decrements `weaponCount` correctly.

**Repeatability**: a collab cannot re-trigger without re-acquiring both materials at level 7. Once consumed, `weaponLevelById` returns 0 for the missing id, so the condition `a == 7` fails. **No unbounded repeat trigger exists.**

### B.4 Result weapon effects — all implemented

| Result | Pattern | Damage | Dispatched in `RunWorld` |
|--------|---------|--------|--------------------------|
| `absolute_wall` | `PATTERN_WALL` | 6 | `:760`-series wall pattern |
| `bl_fujoshi` | `PATTERN_MELEE_ARC` | 6 | `:718` |
| `black_plague` | `PATTERN_AOE` | 5 | `:847`, `:871` explode |
| `blood_lust` | `PATTERN_MELEE_ARC` | 7 | `:718` |
| `legendary_sausage` | `PATTERN_ORBIT` | 5 | `:702`, `:738` autonomous |

All five patterns are dispatched in `RunWorld.java`. No collab is definition-only.

### B.5 CSV recipe grouping

| Group | Count | Recipes |
|-------|-------|---------|
| **Supported and matching** | **5** | `absolute_wall`, `bl_fujoshi`, `black_plague`, `blood_lust`, `legendary_sausage` |
| **Supported but differs from CSV** | **0** | — |
| **Not supported by runtime** | **26** | `bone_bros`, `breathe_in_type_asacoco`, `broken_dreams`, `crescent_bardiche`, `curse_ball`, `dragon_fire`, `eldritch_horror`, `elite_cooking`, `flattening_board`, `frozen_sea`, `im_die_thank_you_forever`, `idol_concert`, `light_beam`, `lightning_wiener`, `micomet`, `mikorone`, `rap_dog`, `ring_of_fitness`, `snow_flower_sake`, `stream_of_tears`, + 6 more |
| **Super Collab (type `super_collab`)** | **5** | `holy_fire`, `idol_live`, `jingisukan`, `snow_queen`, `true_infinite_bl_works` |
| **Cannot be determined from available evidence** | **0** | — |

**The 26 unsupported recipes are not a defect.** Their materials (Plug Type Asacoco, CEO's Tears, Spider Cooking, Idol Song, Wamy Water, X-Potato, Fan Beam, Glowstick, Holo Bomb, Elite Lava Bucket…) have no `WeaponDef` in `BASE_WEAPONS` at all, so they could not be produced even if the recipe list were loaded.

**The 5 super collabs are likewise not a defect** — there is no category-3 weapon, no item-pairing logic, and no super-collab offer branch anywhere in `RunWorld`.

### B.6 Null / index risk

`ContentCatalog.weapon(String id)` returns **`null`** on miss (`ContentCatalog.java` weapon lookup), and `RunWorld.java:1212-1213` dereferences `wd.maxLevel` without a null check.

| Scenario | Today | If `data/` were ever packaged |
|----------|-------|--------------------------------|
| Active 5 collabs | ✅ safe — all 5 ids resolve | unchanged |
| 26 CSV collabs | unreachable | ❌ **`NullPointerException`** on `wd.maxLevel` |
| 5 super collabs | unreachable | ❌ **NPE** |

`weaponIndex` returns `-1` on miss, but at `:1214` it is called with an id already proven present by the `wd` lookup, so `-1` cannot occur.

`ContentCatalog.validate()` already enforces both invariants (`invalid_prerequisite`, `missing_collab_definition`), so a null-deref would be caught at validation time — **provided `validate()` is actually called**. That call site was not traced in this audit.

---

## C. Findings by Risk

| Risk | Finding | Evidence |
|------|---------|----------|
| **HIGH (latent)** | Null dereference on `wd.maxLevel` if the CSV recipe list is ever packaged — 31 recipes, 5 defined weapons | `RunWorld.java:1212-1213`; `ContentCatalog` `weapon(String)` returns `null` |
| **MEDIUM** | Special attack name never displayed | `character.special` has no reader |
| **MEDIUM** | Special attack has no unlock gate, unlike the wiki's shop-upgrade requirement | `RunWorld.java:185`, `:384` |
| **LOW** | Empty `if` body at `RunWorld.java:361` | dead statement |
| **LOW** | Dead accessors `skillAcquired()` / `specialUsed()` | no callers |
| **LOW** | Name↔effect coupling is positional only; nothing enforces it | `RunWorld.java:389-402` vs `ContentCatalog` |
| **INFO** | CSV `special_attack` for Ina (`Tako Spin`) disagrees with runtime (`Spirit of the Oni`) | `data/wiki/characters.csv` vs `ContentCatalog.java` |
| **INFO** | 26 collabs + 5 super collabs unsupported | by design; materials/weapons absent |

---

## D. Smallest Fixes for Proven Problems

| # | Problem | Smallest fix | Risk |
|---|---------|--------------|------|
| F1 | NPE if CSV collabs are ever loaded | Add a `wd == null` guard at `RunWorld.java:1212` (`if (wd == null) continue;`) — one line | LOW |
| F2 | Special name never shown | Draw `ContentCatalog.MODES`-independent special name in the prep/HUD screen, or drop the unused field. **Decision needed** — no obvious existing string slot was proven | MEDIUM |
| F3 | Special has no gate | Add a `specialUnlocked` flag mirroring `skillAcquired`. **Behaviour change — needs product approval** | MEDIUM |
| F4 | Empty `if` at `:361` | Delete the statement or move the comment above it | LOW |
| F5 | Dead accessors `:239-240` | Remove, or wire into a HUD | LOW |
| F6 | Ina CSV `special_attack` mismatch | Documentation-only reconciliation; runtime value traces to the reference JAR | NONE |

**F1 is the only defect with a concrete crash path**, and it is currently unreachable. F3 changes player-visible behaviour and should not be done without approval.

---

## E. Unverified Items

| # | Item |
|---|--------|
| U1 | Effect balance — no runtime execution permitted, so only the code's literal behaviour is described |
| U2 | Whether `ContentCatalog.validate()` is invoked at startup; not traced in this audit |
| U3 | Whether any real installation holds a 6-byte legacy save now migrated by `65dca7a` |
| U4 | Whether the CSV was ever intended as the active recipe source, or was always documentation |
| U5 | Real HoloCure skill/Special values — only the local CSV and the reference JAR were consulted; no live wiki comparison was performed in this audit |

---

## Conclusion

**Skills and Specials are fully implemented, not cosmetic.** All ten effects trace to a real consumer, each is isolated to its own `characterIndex` branch, and every parameter is provable from source. Three low-severity code-hygiene issues and two medium product questions were found.

**All five active collab recipes are correct and reachable**, with three-way agreement between `CollabDef`, `WeaponDef.prerequisiteA/B`, and `collabs.csv`. Materials are consumed and replaced consistently, and no unbounded repeat trigger exists. The one real hazard is a latent null dereference that is currently unreachable.

**No fix was implemented. Awaiting review.**
