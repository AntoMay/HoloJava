# Collab & Super Collab Prerequisite Audit — READ-ONLY

| Item | Value |
|------|-------|
| Checkpoint | `e82a188` (`Fix experience pickup overflow`) |
| Working tree | `ContentCatalog.java` modified — **Task A** (removal of `uber_sheep`) |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Reference version compared | HoloCure **Update 0.7.1731644049** (2024-11-15), <https://holocure.wiki.gg/wiki/Super_Collab> |

**No Collab or Super Collab implementation was changed. No refactor was performed.**

---

## 1. Ordinary Collab

### 1.1 Recipe source and what is actually loaded at runtime

`ContentCatalog.java:277-303` — a static initialiser tries `/data/wiki/collabs.csv`, then falls back to a hardcoded array.

The CSV resource is **not packaged**: `build_fullgame.sh:11` copies only `assets/fullgame`, `:32` zips only `META-INF assets me`, and `unzip -l` on the built JAR shows **0** `data/` entries. `getResourceAsStream` therefore always returns `null`.

**Recipes actually loaded at runtime: 5** (`ContentCatalog.java:295-303`).

| # | Result | `weaponA` | `weaponB` | WeaponDef | maxLevel |
|---|--------|-----------|-----------|-----------|----------|
| 1 | Absolute Wall | `bounce_ball` | `cutting_board` | `:202` | 7 |
| 2 | BL Fujoshi | `bl_book` | `psycho_axe` | `:203` | 7 |
| 3 | Black Plague | `ens_curse` | `owl_dagger` | `:204` | 7 |
| 4 | Blood Lust | `owl_dagger` | `psycho_axe` | `:205` | 7 |
| 5 | Legendary Sausage | `bl_book` | `sausage` | `:206` | 7 |

All 20 weapons declare `maxLevel = 7` (verified across `ContentCatalog.java:195-206`).

### 1.2 Level requirement — enforced

`RunWorld.java:1208-1218`:

```java
if (weaponCount < 6 && mode.allowsCollabs) {
    for (int c = 0; c < ContentCatalog.COLLABS.length; c++) {
        CollabDef cd = ContentCatalog.COLLABS[c];
        int a = weaponLevelById(cd.weaponA); int b = weaponLevelById(cd.weaponB);
        WeaponDef wd = ContentCatalog.weapon(cd.id);
        if (a == wd.maxLevel && b == wd.maxLevel && !offerUsed[wd.id.hashCode() & 63]) {
```

`weaponLevelById` returns `0` when the weapon is not held (`RunWorld.java` helper), so an absent material can never satisfy `a == 7`. **Both materials must be held at level 7.** This matches the reference rule.

### 1.3 How components are obtained — no Anvil exists

`grep -rni "anvil\|golden" src/` → **no matches anywhere**.

The reference game allows Anvils to level weapons quickly outside level-ups. HoloJava has **no Anvil**; the only leveling routes are:

- level-up offer `OFFER_WEAPON` for an already-held weapon (`RunWorld.java:1200-1206`)
- ground drop type `4` at `rand(1301) == 0` (`:995`)

Both are random. There is no deterministic "spend a resource to level a weapon" path.

### 1.4 Components are consumed — yes

`RunWorld.java:1267-1270`:

```java
if (o.collab) {
    removeWeaponById(def.prerequisiteA);
    removeWeaponById(def.prerequisiteB);
    addWeapon(o.value, 1);
}
```

Both materials are removed and the result is added at **level 1**. `removeWeaponById` shifts the slot array down and decrements `weaponCount`. This matches the reference rule that collabs consume both inputs.

### 1.5 Can the character's starting weapon be a component? — Yes

`RunWorld.java:205` `addWeapon(ContentCatalog.weaponIndex(character.mainWeapon), 1);` puts the character's main weapon into slot 0 at **level 1**.

It is therefore a normal member of `weaponSlots` and can be levelled by offers or the type-4 drop. If it ever reached level 7 and matched a recipe, `removeWeaponById` would consume it.

**Caveat found**: `RunWorld.java:206`
```java
if (mode.standardLoadout) weaponLevels[0] = ContentCatalog.weapon(0).maxLevel;
```
uses `weapon(0)` — the **global** weapon at index 0 (`trident_thrust`), not `character.mainWeapon`. For a non-Gura character this would set the wrong weapon's level.

Currently **unreachable**: `standardLoadout` is `true` only for Time Mode (`ContentCatalog.java:249`), and Time Mode sets `allowsCollabs = false` (`:249`), so line 206 can never influence a collab. Flagged as latent, not fixed.

### 1.6 Limits and extra conditions

| Condition | Applied? | Evidence |
|-----------|----------|----------|
| Weapon slot cap | ✅ `weaponCount < 6` | `:1208` |
| Mode gate | ✅ `mode.allowsCollabs` — Stage ✅, Endless ✅, Time ❌ | `:249`, `:1208` |
| One collab per run | ❌ **not implemented** — no `collabCount` / `collabsUsed` field | grep → none |
| Player-level gate | ❌ none | — |

A run may produce **multiple** collabs, provided the player re-acquires both materials at level 7 each time. This is a divergence from the reference only for Super Collabs; ordinary Collabs have no per-run cap in the reference either.

---

## 2. Super Collab

### 2.1 Concept presence in code

| Element | In HoloJava? | Evidence |
|---------|--------------|----------|
| Super Collab concept | **Metadata only** | `CollabDef.type` field (`ContentCatalog.java:172`); parser accepts `"super_collab"` (`WikiCatalogData.java:140`) |
| Golden Hammer | ❌ **absent** | `grep -rni "golden" src/` → none |
| Golden Anvil | ❌ **absent** | same |
| Anvil (any) | ❌ **absent** | same |
| Category-3 weapon | ❌ **absent** | `BASE_WEAPONS` uses categories 0, 1, 2 only |
| Super Item concept | ❌ **absent** | no `isSuper` / super-item field |
| Runtime offer branch | ❌ **absent** | `RunWorld.java:1267-1270` has only the `o.collab` branch |

**`CollabDef.type` is never read by any runtime logic.** `RunWorld` uses only `cd.weaponA`, `cd.weaponB`, and `cd.id`. `WikiCatalogData.isValidCollab` reads `def.type`, but that method has **no callers** (verified by grep) — it is dead code.

### 2.2 Which super-collab recipes exist in data

`data/wiki/collabs.csv` contains 5 rows with `type = "super_collab"` — **never loaded** (CSV not packaged):

| Recipe | Collab required | Companion item | Item exists in HoloJava? |
|--------|-----------------|----------------|---------------------------|
| Holy Fire | Dragon Fire | Gorilla's Paw | item ✅ / weapon ❌ |
| Idol Live | Idol Concert | Idol Costume | ❌ / ❌ |
| Jingisukan | Elite Cooking | Uber Sheep | item ❌ (removed in Task A) / weapon ❌ |
| Snow Queen | Snow Flower Sake | Sake | item ✅ / weapon ❌ |
| True Infinite BL Works | BL Fujoshi | Hope Soda | ❌ / ✅ (BL Fujoshi is an active collab) |

Of the four required collab weapons, **none** exists in `BASE_WEAPONS`. So even if the recipe list were loaded, no super collab could ever be assembled.

### 2.3 Prerequisites the runtime actually checks

**None.** There is no super-collab code path, therefore no checks for any of:

| Reference prerequisite | Implemented? |
|-------------------------|--------------|
| Companion item at max level | ❌ |
| Player level ≥ 50 | ❌ |
| ≥ 3 Collabs held | ❌ |
| Golden Hammer held | ❌ |
| Golden Anvil / crafting station | ❌ |
| One super collab per run | ❌ |
| Companion item consumed on craft | ❌ |

### 2.4 Code that is absent vs metadata-only vs no consumer

| Class | Category | Detail |
|-------|----------|--------|
| `CollabDef.type` | **Metadata only** | Stored, never read at runtime |
| `WikiCatalogData.buildCollabDefs` super-collab branch | **Metadata only** | Parses `super_collab` rows, but the CSV is never packaged |
| `WikiCatalogData.isValidCollab` | **No consumer** | Zero callers |
| `EFFECT_FOOD_DROP` | **No consumer** | Retained constant; the only item using it was removed in Task A |
| Golden Hammer / Golden Anvil / Anvil / Super Item | **Absent** | No code, no data |

---

## 3. Skill Collab

**NOT FOUND.**

A targeted search returned zero matches:

```
grep -rni "skill.collab\|collab.skill" src/ data/ docs/   →   NONE
```

The term "Skill Collab" does not appear in source, in `data/wiki/*.csv`, or in `docs/rebuild/`.

Per the instruction, this is recorded as **not found** and is explicitly **not** treated as equivalent to Super Collab. Skill Collab is a separate concept and HoloJava contains neither.

---

## 4. Comparison with the HoloCure reference

Reference source: <https://holocure.wiki.gg/wiki/Super_Collab>, Update 0.7.1731644049 (2024-11-15).

| Mechanic | HoloCure reference | HoloJava | Divergence |
|----------|--------------------|----------|------------|
| Collab = 2 weapons at max level | yes | ✅ `:1213` | none |
| Both components consumed | yes | ✅ `:1268-1269` | none |
| Result starts at level 1 | yes | ✅ `:1270` | none |
| Anvil levels weapons outside level-ups | yes | ❌ absent | divergence |
| Super Collab = Collab + max-level item | yes | ❌ absent | divergence |
| Golden Hammer required | yes | ❌ absent | divergence |
| Golden Hammer gate: Level ≥50 **or** ≥3 Collabs | yes | ❌ absent | divergence |
| Golden Anvil crafting station | yes | ❌ absent | divergence |
| Only one Super Collab per run | yes | ❌ absent | divergence |
| Companion item consumed | yes | ❌ absent | divergence |
| Super Item may substitute for the item | yes | ❌ absent | divergence |
| 5 super-collab recipes | yes | ❌ CSV only, never loaded | divergence |
| Ordinary collab per-run cap | none | none | none |

**Important:** these reference rules are recorded as context only. Per the instruction, none of them are treated as a HoloJava requirement without code evidence or a design decision. Every "❌" above is reported as *not implemented*, not as *broken*.

---

## 5. Findings

| # | Finding | Status | Risk |
|---|---------|--------|------|
| B1 | No Golden Hammer, Golden Anvil, Anvil, or Super Item concept in code | Not implemented (by scope) | None |
| B2 | No per-run collab counter exists | Not implemented | None |
| B3 | `CollabDef.type` never read at runtime | Metadata only | None |
| B4 | `WikiCatalogData.isValidCollab` has no callers | Dead code | None |
| B5 | All 5 super-collab recipes unreachable — 4 required collab weapons undefined | By design | None |
| B6 | No Anvil; components can only be levelled by random offers/drops | Divergence from reference | Balance only |
| B7 | `RunWorld.java:206` uses `weapon(0)` instead of `character.mainWeapon` | Latent — unreachable while `standardLoadout` is Time-Mode-only | Low |
| B8 | "Skill Collab" not found anywhere in the project | Absent | None |

**No Collab or Super Collab bug was found.** Ordinary Collabs are complete and correct for their implemented scope.

---

## 6. Recommendations (informational — nothing was changed)

| # | Recommendation | Note |
|---|----------------|------|
| 1 | Decide whether Super Collab is in scope at all | No code exists; a decision avoids future ambiguity |
| 2 | Remove or document `CollabDef.type` and `isValidCollab` | Dead metadata currently implies a mechanic that does not exist |
| 3 | Correct `RunWorld.java:206` if `standardLoadout` is ever enabled for a collab-capable mode | Currently unreachable; would give the wrong weapon a max-level start |
| 4 | Leave Anvil out unless parity with the reference is a goal | Requires a new mechanic, not a patch |

**No implementation change is recommended without an explicit design decision.**

---

## 7. Final Status

| Item | Value |
|------|-------|
| HEAD | `e82a188` — unchanged |
| Tracked modifications | `ContentCatalog.java` (Task A only) |
| Collab/Super Collab code changed | **none** |
| Commit | none |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/collab-prerequisite-audit/00_AUDIT_REPORT.md` |
