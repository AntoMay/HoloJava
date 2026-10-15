# S3 — Persist Collected Coin Pickups

**Date:** 2026-10-15
**Change group:** Wire permanent coin balance into the run coin pickup
**Outcome:** Verified, committed locally.

---

## 1. Problem

S2 built and verified a permanent coin balance with transaction semantics, but a repository-wide
search found **no production caller**:

```
$ grep -rn 'creditCoins\|spendCoins\|coinBalance' src/ --include=*.java | grep -v SaveStore.java
(no results)
```

The whole chain — `CoinBalance`, the 56-byte record, the transaction guards, the RMS absence
handling — existed but nothing ever credited a coin. The gameplay feature the design describes did
not actually happen in game.

---

## 2. Evidence and the divergence it creates

### Reference JAR behaviour (verified by disassembly)

`javap -c` on `me.holojava.fullgame.RunWorld.collectPickup` in
`/home/antomay/Downloads/HoloJavaFullGame.jar` (SHA-256
`20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`, re-verified this session):

```
132: aload_1
133: getfield  Pickup.type
136: iconst_1
137: if_icmpne 156
140: aload_0
141: dup
142: getfield  coins
145: aload_1
146: getfield  Pickup.value
149: iadd          <- coins += p.value, then nothing else
```

The reference **does not persist coins**. `RunWorld` in the reference has no `SaveStore` field at
all; the store is only passed to `reset(SaveStore)` and used for `hardcore()`. Confirmed via
`javap -p` — the only members mentioning `SaveStore` are the constructor parameter and `reset`.

**This change is therefore an intentional divergence from the reference**, and is scoped
accordingly.

### Authorisation

`reports/coin-persistence-audit/00_COIN_AND_STAT_SHOP_DESIGN.md:396-399` specifies stage S3
verbatim:

> ### S3 — RunWorld: persistensi saat pickup coin
> - `collectPickup()` type 1: `save.addCoins(p.value)` **ditambahkan setelah** `coins += p.value`.

The design's risk register (`:221`) explicitly defers the two non-pickup grants to a later stage:

> R5 | **Reward tanpa pickup** | `:767` dan `:1371` | Jika tidak dipersistensi, pemain melihat
> "Coins +40" di Results tapi saldo permanen tidak naik.

Those are open questions Q2 and Q3, assigned to S4. **They are deliberately not touched here.**
`coins += stage.coinClear + defeatCount / 4` (victory) and `coins += 100` (level-up) remain
run-local, exactly as the reference has them.

---

## 3. Change made

`src/java-me-fullgame/me/holojava/fullgame/RunWorld.java`, +13 lines, no deletions:

1. `private final SaveStore save;` field, documented, null-tolerant.
2. `this.save = save;` in the constructor.
3. `if (save != null) save.creditCoins(p.value);` in the type-1 branch of `collectPickup`,
   immediately after the unchanged `coins += p.value;`.

**Not changed:** run counter arithmetic, drop chances and amounts, magnet radius, pickup lifetime,
HUD, results screen, EXP handling, or any other pickup type.

---

## 4. Double-counting — verified, not assumed

The design (`:206-210`) requires proof that one pickup cannot pay twice. Confirmed on two levels:

**Source level.** `collectPickup` sets `p.active = false` on its very first line, before touching
any value. The only caller, `updatePickups`, begins each iteration with `if (!p.active) continue;`
and collects through an `if / else if`, so it is unreachable twice in one frame.

**Behaviour level.** Test 3 below drives the real `updatePickups` loop with an already-collected
pickup still sitting in the pool, and asserts the balance does not move. Removing the
`if (!p.active) continue;` guard makes that test fail — see the negative controls.

---

## 5. Test added

`tools/tests/me/holojava/fullgame/RunWorldCoinWiringTest.java` — 11 checks.

**Construction problem and how it is handled.** `RunWorld` cannot be built on a desktop JVM. Its
constructor calls `AssetLibrary.prepareStage`, which reaches `Image.createImage`; the pinned MIDP
stub throws `Error("API Stub has been used")`, and because that is an `Error` the library's
`catch (Exception)` does not catch it. Confirmed directly rather than assumed:

```
AssetLibrary FAILED: java.lang.Error: API Stub has been used
```

The test therefore allocates a `RunWorld` **without running its constructor** and sets, by
reflection, only the fields the type-1 branch touches: `save`, `coins`, and on the pickup `type`,
`value`, `active`. The method under test is the genuine production bytecode. For a type-1 pickup
`collectPickup` reads nothing else, which is what makes this sound.

This is test-only tooling; nothing in `src/` references `sun.*` or unsafe allocation.

| # | Test | Proves |
|---|---|---|
| 1 | `a collected coin reaches the permanent balance` | One 10-coin pickup credits 10, the bytes are persisted, exactly one write is issued. |
| 2 | `run counter and permanent balance both advance` | Two pickups sum to 17 in both the run counter and the stored balance. |
| 3 | `an already-collected pickup is skipped` | Drives the real `updatePickups` over a spent pickup; no second credit. |
| 4 | `a refused write still counts for the run` | On a refused write the run counter still advances and the stored balance does not drift from the bytes. |
| 5 | `a run without a store still collects` | A null `SaveStore` does not throw; coins are still counted. |

---

## 6. Verification actually performed

Full suite, pinned stubs, `--release 8`:

| Suite | Checks | Failures | Result |
|---|---|---|---|
| `CoinBalanceTest` | 408 | 0 | PASS |
| `SaveStoreLegacyMigrationTest` | 245 | 0 | PASS |
| `SaveStoreCoinPersistenceTest` | 159 | 0 | PASS |
| `SaveStoreRmsPathTest` | 20 | 0 | PASS |
| `RunWorldCoinWiringTest` | 11 | 0 | PASS |
| **Total** | **843** | **0** | **PASS** |

### Negative controls — both deliberately break production code

| Control | Mutation | `SaveStoreCoinPersistenceTest` | `RunWorldCoinWiringTest` |
|---|---|---|---|
| NC-A | `save.creditCoins(p.value)` removed (S3 reverted) | PASS | **FAIL, 8 checks** |
| NC-B | `if (!p.active) continue;` guard removed | PASS | **FAIL, 1 check** |

Both mutations were applied to scratch copies outside the repository. Each is detected only by the
new test, which is the property that makes these tests worth having.

### Build

- `./tools/build_fullgame.sh` → exit 0
- `python3 tools/verify_fullgame_build.py` → `PASS: JAR=836555 bytes, assets=189, wiki rows verified`
- `git diff --check` → exit 0

---

## 7. Files changed

```
 M src/java-me-fullgame/me/holojava/fullgame/RunWorld.java        (+13 / -0)
?? tools/tests/me/holojava/fullgame/RunWorldCoinWiringTest.java    (new, 11 checks)
```

---

## 8. Intentional divergence from the reference

The reference persists no coin balance. This build does, for **collected pickups only**. The
divergence is:

- deliberate — authorised by the S3 stage of the coin design;
- scoped — the two direct grants (victory `coinClear`, level-up `+100`) are deliberately excluded
  pending Q2/Q3 in S4;
- additive — the run-local counter the HUD and results screen read is completely unchanged, so
  nothing the player sees in a run behaves differently.

---

## 9. Residual uncertainty

**Needs an emulator or a handset:**

1. That a dropped coin pickup is spawned, magnetised, and collected in a real run — the pickup
   spawn, magnet, and rendering paths are not exercised by any headless test.
2. RMS write cost and durability at the real pickup rate. The design estimates roughly one coin
   pickup per 90 s of run time (`reports/coin-persistence-audit/00_COIN_AND_STAT_SHOP_DESIGN.md:145-146`),
   which should be negligible, but that is an estimate.
3. All device-level RMS questions carried over from the S2.3 report, still unresolved off-device.

**Deliberately out of scope, tracked as open:**

- **Q2 / Q5 (R5)** — victory `coinClear` and level-up `+100` remain run-local, so a player can see
  "Coins +40" on the results screen while the permanent balance does not move. This is a real
  player-visible inconsistency and is the next thing to decide.

---

## 10. Next queued groups

1. **Q2/Q3 decision (S4)** — decide whether the victory and level-up grants join the permanent
   balance, so the results screen and the saved balance stop disagreeing. Needs a design call; the
   bytecode and reference behaviour are already established.
2. Gameplay gap inventory against the reference JAR — movement, combat, enemies, bosses, EXP,
   level-up, drops, stage progression, win/lose — using the negative-control discipline
   established in S2.3 and this group: any coverage claim must survive a deliberate break.
3. Carry over the open MEDIUM/LOW findings listed in the S2.3 report.