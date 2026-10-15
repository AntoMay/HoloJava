# Collab & Super Collab Recipe Audit

## 1. Recipe Sources

| Source | Content | Loaded? |
|--------|---------|---------|
| `ContentCatalog.java:275-290` | hardcoded fallback: 5 recipes | **YES — active** |
| `data/wiki/collabs.csv` | 31 recipes (26 ordinary + 5 super) | **NO — dead** |

`ContentCatalog.java:268` attempts `getResourceAsStream("/data/wiki/collabs.csv")`. The build (`tools/build_fullgame.sh:32`) packages only `META-INF assets me` — **no `data/` directory**. Confirmed: `unzip -l` on the reference JAR returns **0** entries matching `data/`. Therefore `is == null` and the hardcoded fallback at `:284-290` is always used.

---

## 2. Active Collab Recipes (5)

| # | Result | Weapon A | Weapon B | A defined? | B defined? | Result defined? | MaxLevel |
|---|--------|----------|----------|-----------|-----------|-----------------|----------|
| 1 | Absolute Wall | `bounce_ball` | `cutting_board` | ✅ L182 | ✅ L185 | ✅ L190 | 7 |
| 2 | BL Fujoshi | `bl_book` | `psycho_axe` | ✅ L179 | ✅ L180 | ✅ L191 | 7 |
| 3 | Black Plague | `ens_curse` | `owl_dagger` | ✅ L186 | ✅ L187 | ✅ L192 | 7 |
| 4 | Blood Lust | `owl_dagger` | `psycho_axe` | ✅ L187 | ✅ L180 | ✅ L193 | 7 |
| 5 | Legendary Sausage | `bl_book` | `sausage` | ✅ L179 | ✅ L181 | ✅ L194 | 7 |

**Cross-check — recipe fields vs `WeaponDef.prerequisiteA/B`:** all five agree exactly.

| Result | `prerequisiteA` / `B` (weapon def) | `weaponA` / `B` (recipe) | Agree? |
|--------|-----------------------------------|---------------------------|--------|
| `absolute_wall` L190 | `bounce_ball`, `cutting_board` | same | ✅ |
| `bl_fujoshi` L191 | `bl_book`, `psycho_axe` | same | ✅ |
| `black_plague` L192 | `ens_curse`, `owl_dagger` | same | ✅ |
| `blood_lust` L193 | `owl_dagger`, `psycho_axe` | same | ✅ |
| `legendary_sausage` L194 | `bl_book`, `sausage` | same | ✅ |

**All 5 recipes are internally consistent and reachable.**

---

## 3. Collab Runtime Path

| Step | Code | Behavior |
|------|------|----------|
| Gate | `RunWorld.java:1208` | `weaponCount < 6 && mode.allowsCollabs` |
| Iterate recipes | `:1209-1210` | over `ContentCatalog.COLLABS` |
| Level check | `:1211-1213` | both materials at `maxLevel` |
| Offer | `:1214-1216` | `OFFER_WEAPON`, `collab = true` |
| Consume | `:1267-1270` | `removeWeaponById(A)`, `removeWeaponById(B)`, `addWeapon(result, 1)` |

**Result weapons are level 1** (`addWeapon(o.value, 1)`), not max-level — matches HoloCure semantics (collab starts at level 1).

`mode.allowsCollabs` (`ContentCatalog.java:247-249`): Stage Mode ✅, Endless ✅, Time Mode ❌.

---

## 4. Collab Result Effects — Implemented

| Result | Category | Pattern | Damage | RunWorld dispatch |
|--------|----------|---------|--------|-------------------|
| `absolute_wall` | 2 (collab) | `WEAPON_MELEE` / `PATTERN_MELEE_ARC` | 90 | ✅ |
| `bl_fujoshi` | 2 | `WEAPON_MELEE` / `PATTERN_MELEE_ARC` | 85 | ✅ |
| `black_plague` | 2 | `WEAPON_MELEE` / `PATTERN_AOE` | 85 | ✅ |
| `blood_lust` | 2 | `WEAPON_MELEE` / `PATTERN_ORBIT` | 95 | ✅ |
| `legendary_sausage` | 2 | `WEAPON_MELEE` / `PATTERN_ORBIT` | 70 | ✅ |

`ContentCatalog.validate()` (`ContentCatalog.java:413-421`) additionally requires every category-2 weapon to have both prerequisites resolvable **and** a matching `CollabDef`. All five active recipes satisfy both conditions.

---

## 5. Latent NullPointerException (not currently triggered)

If `collabs.csv` were ever packaged, `COLLABS` would hold **31** recipes, but `BASE_WEAPONS` defines only **5** collab weapons. At `RunWorld.java:1212-1213`:

```java
ContentCatalog.WeaponDef wd = ContentCatalog.weapon(cd.id);   // returns null for 26 recipes
if (a == wd.maxLevel && ...)                                   // NPE
```

`ContentCatalog.weapon(String)` (`:382-385`) returns `null` on miss. **26 recipes would NPE.** Currently masked because the CSV is not packaged.

---

## 6. Super Collab Status

### 6.1 In source

| Element | Status | Evidence |
|---------|--------|----------|
| `WeaponDef.category` doc mentions super collab | declared | `ContentCatalog.java:68` |
| `CollabDef.type` field | exists, stores `"collab"` or `"super_collab"` | `:162`; `WikiCatalogData.java:70` |
| Category-3 weapon definition | **NONE** | `BASE_WEAPONS` uses categories 0, 1, 2 only |
| Super-collab recipe in active list | **NONE** | fallback list has 5 `type="collab"` only |
| Super-collab offer logic | **NONE** | `RunWorld.java:1267` has only the `o.collab` branch |
| Item requirement check | **NONE** | no code pairs a collab with an item |

### 6.2 In `collabs.csv` (not loaded)

| Recipe | Collab required | Item required |
|--------|-----------------|---------------|
| Holy Fire | Dragon Fire | Gorilla's Paw |
| Idol Live | Idol Concert | Idol Costume |
| Jingisukan | Elite Cooking | Uber Sheep |
| Snow Queen | Snow Flower Sake | Sake |
| True Infinite BL Works | BL Fujoshi | Hope Soda |

**None of the prerequisite collab weapons** (`dragon_fire`, `idol_concert`, `elite_cooking`, `snow_flower_sake`) exist in `BASE_WEAPONS`. Two prerequisite items (`uber_sheep`, `sake`) exist; `idol_costume` and `hope_soda` do not.

**Verdict: Super Collab is `UNKNOWN` / not implemented.** Nothing in the active catalog or runtime can produce a super collab.

---

## 7. Wiki Cross-Check

Source: <https://holocure.wiki.gg/wiki/Collab> and `/wiki/Super_Collab` (linked from Character page nav)

| Wiki concept | HoloJava |
|--------------|----------|
| Collab = two max-level weapons combine | ✅ `RunWorld.java:1213` |
| Result starts at level 1 | ✅ `:1270` |
| Super Collab = collab + item | ❌ absent |
| Disallowed in Time Mode | ✅ `ContentCatalog.java:249` |

---

## 8. Findings

| # | Finding | Severity | Evidence |
|---|---------|----------|----------|
| C1 | Super Collab entirely absent | MEDIUM | no category-3 weapon in `BASE_WEAPONS` |
| C2 | 26 CSV recipes would NPE if `data/` were packaged | HIGH (latent) | `RunWorld.java:1212` |
| C3 | Result weapon starts at level 1, not max | INFO | `:1270` — matches wiki |
| C4 | `CollabDef.type` stored but never branched on | LOW | no reader of `.type` |

---

## 9. Status Per Recipe

| Recipe | Materials valid | Reachable | Result effect | Status |
|--------|----------------|-----------|---------------|--------|
| Absolute Wall | ✅ | ✅ | ✅ | `IMPLEMENTED` |
| BL Fujoshi | ✅ | ✅ | ✅ | `IMPLEMENTED` |
| Black Plague | ✅ | ✅ | ✅ | `IMPLEMENTED` |
| Blood Lust | ✅ | ✅ | ✅ | `IMPLEMENTED` |
| Legendary Sausage | ✅ | ✅ | ✅ | `IMPLEMENTED` |
| Holy Fire | ❌ | ❌ | ❌ | `UNKNOWN` |
| Idol Live | ❌ | ❌ | ❌ | `UNKNOWN` |
| Jingisukan | ❌ | ❌ | ❌ | `UNKNOWN` |
| Snow Queen | ❌ | ❌ | ❌ | `UNKNOWN` |
| True Infinite BL Works | partial (`bl_fujoshi` ✅, `hope_soda` ❌) | ❌ | ❌ | `UNKNOWN` |

**No recipe was created, modified, or removed.**
