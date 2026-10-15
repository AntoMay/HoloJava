# Supporting Item Catalog Audit

## 1. Item Inventory

All 11 items are declared in `ContentCatalog.java:199-211`. No item is defined anywhere else.

| # | Id | Name | Cat | Effect const | Value | MaxLv | Sprite | Declared |
|---|-----|------|-----|--------------|-------|-------|--------|----------|
| 1 | `face_mask` | Face Mask | 1 | `EFFECT_DAMAGE_PCT` | 30 | 1 | `bullet` | L200 |
| 2 | `knightly_milk` | Knightly Milk | 1 | `EFFECT_PICKUP_PCT` | 30 | 1 | `bullet` | L201 |
| 3 | `energy_drink` | Energy Drink | 1 | `EFFECT_SPEED_PCT` | 40 | 1 | `bullet` | L202 |
| 4 | `just_bandage` | Just Bandage | 0 | `EFFECT_MAX_HP` | 10 | 3 | `bullet` | L203 |
| 5 | `study_glasses` | Study Glasses | 0 | `EFFECT_EXP_PCT` | 30 | 1 | `bullet` | L204 |
| 6 | `sake` | Sake | 0 | `EFFECT_CRIT_PCT` | 5 | 3 | `bullet` | L205 |
| 7 | `gorillas_paw` | Gorilla's Paw | 0 | `EFFECT_DAMAGE_PCT` | 20 | 3 | `bullet` | L206 |
| 8 | `piki_piki` | Piki Piki Piman | 0 | `EFFECT_MAX_HP` | 15 | 3 | `bullet` | L207 |
| 9 | `limiter` | Limiter | 0 | `EFFECT_PICKUP_PCT` | 100 | 1 | `bullet` | L208 |
| 10 | `headphones` | Headphones | 2 | `EFFECT_NEGATE` | 15 | 1 | `bullet` | L209 |
| 11 | `uber_sheep` | Uber Sheep | 0 | `EFFECT_FOOD_DROP` | 10 | 5 | `bullet` | L210 |

---

## 2. Defined Effect vs Actually-Applied Effect

Stat application occurs in `RunWorld.recomputeStats()` (`RunWorld.java:1123-1144`), scaled by `d.value * itemLevels[i]` (`:1131`).

| # | Item | Defined | Applied | Where | Status |
|---|------|---------|---------|-------|--------|
| 1 | `face_mask` | DAMAGE_PCT | `atkPercentBonus += v` | `:1132` | **IMPLEMENTED** |
| 2 | `knightly_milk` | PICKUP_PCT | `pickupPercentBonus += v` | `:1136` | **IMPLEMENTED** |
| 3 | `energy_drink` | SPEED_PCT | `speedPercentBonus += v` | `:1134` | **IMPLEMENTED** |
| 4 | `just_bandage` | MAX_HP | `maxHp += v` | `:1135` | **IMPLEMENTED** |
| 5 | `study_glasses` | EXP_PCT | `expPercentBonus += v` | `:1138` | **IMPLEMENTED** |
| 6 | `sake` | CRIT_PCT | `critPercentBonus += v` | `:1137` | **IMPLEMENTED** |
| 7 | `gorillas_paw` | DAMAGE_PCT | `atkPercentBonus += v` | `:1132` | **IMPLEMENTED** |
| 8 | `piki_piki` | MAX_HP | `maxHp += v` | `:1135` | **IMPLEMENTED** |
| 9 | `limiter` | PICKUP_PCT | `pickupPercentBonus += v` | `:1136` | **IMPLEMENTED** |
| 10 | `headphones` | NEGATE | `rand(100) < d.value` → `negated = true` | `:969` | **IMPLEMENTED** (out-of-band) |
| 11 | `uber_sheep` | FOOD_DROP | **none** | — | **DATA_ONLY** |

**Summary: 10 of 11 implemented. 1 dead (`uber_sheep`).**

`EFFECT_SHIELD` (`ContentCatalog.java:43`) is declared but **no item uses it** and no code applies it — confirmed by grep across `src/java-me-fullgame`.

### 2.1 Consumer verification

Each bonus target must actually be consumed downstream. Verified in `RunWorld.java`:

| Bonus variable | Consumed at | Verified |
|-----------------|-------------|----------|
| `atkPercentBonus` | damage calc | ✅ |
| `speedPercentBonus` | movement | ✅ |
| `critPercentBonus` | shot crit roll | ✅ |
| `pickupPercentBonus` | coin/EXP collect radius | ✅ |
| `expPercentBonus` | EXP gain | ✅ |
| `maxHp` → `playerMaxHp` | `:1142-1143` | ✅ |
| `regen` (frozen meat concept) | `:1139` + `:1144` timer | ✅ but no item currently sets `EFFECT_REGEN` |

---

## 3. Acquisition Path

Items are obtained exclusively through level-up offers:

| Step | Code | Behavior |
|------|------|----------|
| Offer type | `RunWorld.java:22` | `OFFER_ITEM = 2` |
| Offer a new item | `:1243-1250` | random from `ITEMS`, skip owned |
| Offer a level-up | `:1231-1240` | 50% chance if slot already held |
| Apply new | `:1114-1119` | `addItem(itemIndex, level)` |
| Apply level-up | `:1277-1278` | `itemLevels[o.existing] = min(maxLevel, +1)` |
| Label | `:266-268` | `d.name + " NEW"/" Lv"+n` |

**Slots**: 6 max (`weaponCount < 6` / `itemCount < 6` guards at `:1220`, `:1242`).

**No external acquisition exists**: no shop, no drop table, no crate system. Items cannot be obtained other than random level-up.

---

## 4. Asset Dependencies

| Asset path | Status | Used for |
|-----------|--------|----------|
| `assets/fullgame/projectiles/bullet.png` | ✅ present | shot rendering (`AssetLibrary.java:83`) |

**Finding**: `ItemDef.sprite` (`ContentCatalog.java:210` etc.) is **stored but never used for item rendering**. All 11 items declare `"bullet"`. Grep confirms `def.sprite` is read only in `RunWorld.java:741` (shot spawn) and `:1410-1411` (shot size), never for the item pickup or level-up icon. The level-up screen uses a single generic `assets.pickupIcon` (`AssetLibrary.java:91`).

---

## 5. Wiki Cross-Check

Source: <https://holocure.wiki.gg/wiki/Item> (referenced from Character page nav)

| HoloJava item | Wiki status | Note |
|---------------|-------------|------|
| Gorilla's Paw | real item, used by Super Collab `holy_fire` / `jingisukan` | ✅ present in `collabs.csv` |
| Uber Sheep | real item, used by Super Collab `jingisukan` | ✅ present in `collabs.csv` |
| Sake | real item, used by Super Collab `snow_queen` | ✅ present in `collabs.csv` |
| Knightly Milk | real item | not referenced by any local recipe |
| Headphones | real item | — |
| Limiter | real item | — |
| Study Glasses | real item | — |
| Energy Drink | real item | — |
| Face Mask | **not a standard HoloCure item** | ⚠️ name resembles "Face Mask" from other titles |
| Just Bandage | **not verified as a HoloCure item** | ⚠️ |
| Piki Piki Piman | **not verified as a HoloCure item** | ⚠️ |

Items 9–11 (Face Mask, Just Bandage, Piki Piki Piman) could not be matched to wiki entries within this audit. Marked **BELUM TERVERIFIKASI** — not asserted as fabricated, but unconfirmed.

---

## 6. Findings

| # | Finding | Severity | Evidence |
|---|---------|----------|----------|
| I1 | `uber_sheep` effect never applied | MEDIUM | no `EFFECT_FOOD_DROP` consumer |
| I2 | `ItemDef.sprite` is dead metadata | LOW | `RunWorld.java:741,1454` only |
| I3 | `EFFECT_REGEN` handled but unreachable | LOW | no item declares it |
| I4 | `EFFECT_SHIELD` declared, unused | LOW | `ContentCatalog.java:43` |
| I5 | 3 items unverified against wiki | LOW | §5 |
| I6 | No shop/drop acquisition path | INFO | level-up only |

---

## 7. Status Per Item

| Item | Status |
|------|--------|
| `face_mask` | `IMPLEMENTED` (provenance BELUM TERVERIFIKASI) |
| `knightly_milk` | `IMPLEMENTED` |
| `energy_drink` | `IMPLEMENTED` |
| `just_bandage` | `IMPLEMENTED` (provenance BELUM TERVERIFIKASI) |
| `study_glasses` | `IMPLEMENTED` |
| `sake` | `IMPLEMENTED` |
| `gorillas_paw` | `IMPLEMENTED` |
| `piki_piki` | `IMPLEMENTED` (provenance BELUM TERVERIFIKASI) |
| `limiter` | `IMPLEMENTED` |
| `headphones` | `IMPLEMENTED` |
| `uber_sheep` | `DATA_ONLY` |

**No items were created, modified, or removed.**
