# Gameplay Parity Audit and Single-Event Invariant Tests

**Date:** 2026-10-15
**Change group:** Gameplay verification against the reference JAR + event-once invariant tests
**Outcome:** Verified, committed locally.

---

## 1. Why this group

The mission requires gameplay to be demonstrably correct, not merely compiling, and explicitly
lists the rule: *"Pastikan satu event tidak memproses damage, death, reward, atau pickup berkali-kali."*

Two facts from the inventory drove the plan:

1. **The coin chain had no production caller** — addressed and committed separately (S3).
2. **Gameplay logic had zero headless test coverage.** Every existing test covered storage or the
   coin model. No test touched movement, combat, enemies, bosses, EXP, or level-up.

Before adding tests it was necessary to know whether the port was actually faithful, otherwise new
tests would enshrine bugs.

---

## 2. Structural parity against the reference JAR

Reference: `/home/antomay/Downloads/HoloJavaFullGame.jar`, SHA-256
`20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`, re-verified this session.
Compared with `javap -p` / `javap -c`; no decompiler is available in this environment.

### Members

| Class | Only in reference | Only in ours |
|---|---|---|
| `RunWorld` | `drawPaused`, plus `update`/`updateWeapons`/`drawBackground` with fewer parameters | `drawHudSlot`, `cameraX`, `setViewport`, `worldWidth`, `worldHeight`, `offerIsCollab`, and the same three methods with an extra parameter |
| `FullGameCanvas` | three `update*` methods with fewer parameters | the same three with more parameters, plus pause, splash, ink and page-background helpers |
| `ContentCatalog` | none | one `character(...)` overload |

**Every field present in the reference exists in our source — zero missing.**

### The two apparently missing members, resolved

- **`drawPaused` is not missing behaviour.** It moved to `FullGameCanvas.drawPause`
  (`FullGameCanvas.java:708-711`) during the deliberate HUD decoupling, because HUD drawing now
  receives screen offsets instead of world coordinates. The reference's version draws only the
  string `PAUSED`; ours draws `PAUSED` plus a Resume / Return to Menu list. Ours is a strict
  superset, matching the pause design in
  `reports/implementation-readiness-review/01_PAUSE_RESUME_DESIGN.md`.
- **Signature differences are all the viewport change.** `update`, `updateWeapons`,
  `drawBackground`, `updateCharacters`, `updateModes`, `updateResults` each gained one parameter
  for the scroll offset introduced by commits `4cbe8d4` and `82a3d46`.

### Integer constant drift

All integer constants were extracted per method from both disassemblies and compared as
multisets. 109 methods are shared; **11 differ**. Each was traced to its bytecode:

| Method | Reference | Ours | Resolution |
|---|---|---|---|
| `clampEnemy`, `drawBackground`, `placeAtEdge`, `updateBoss` | `320`, `240` | none — read from fields | World dimensions moved into fields. Default is `320x240` (`RunWorld.java:11-12`, `Viewport.MIN_W/MIN_H`), so identical at default. |
| `updateShots` | `350`, `270` | `worldWidth + 30`, `worldHeight + 30` | 320+30 = 350, 240+30 = 270. Identical. |
| `updatePlayer` | `296`, `216` | `worldWidth - 24`, `worldHeight - 24` | 320−24 = 296, 240−24 = 216. Identical. |
| `spawnBoss` | `140` | `worldWidth / 2 - 20` | 320/2 − 20 = 140. Identical. |
| `dropPickup` | `200` twice | `200` once | The reference merges when `p.value + value <= 200` and then clamps with `Math.min(..., 200)`. The clamp is unreachable given the guard, so behaviour is identical. |
| `drawHud`, `drawLevelUp` | — | — | Deliberate compact-HUD redesign; see `reports/compact-hud-implementation/` and `reports/visual-redesign-audit/`. |

**Conclusion: no damage, cooldown, probability, HP, EXP, duration, or price value has drifted.**

One consequence worth recording: for scrolling stages the world is wider than 320, so the boss
spawn point, shot culling bounds, and player clamp now scale with the world instead of being fixed.
That is the correct adaptation of the deliberate scrolling feature, not a regression.

---

## 3. Tests added

`tools/tests/me/holojava/fullgame/RunWorldEventOnceTest.java` — 14 checks, covering the
mission's single-event rule.

Two methods carry the risk and both rely on the **call site** to prevent repeats rather than
refusing one internally:

- `damagePlayer` — guarded only by `state != STATE_RUNNING || playerInvulnerable(now)`, and it sets
  `invulUntil = now + 800` as a side effect of landing a hit.
- `damageEnemy` — awards `kills` and `defeatCount` with **no internal idempotence at all**. A second
  lethal call would pay the reward twice. Only the `!enemy.active` and `hitMask` checks in
  `updateShots` prevent that.

| # | Test | Proves |
|---|---|---|
| 1 | player damage lands once, then invulnerability holds | First hit lands and opens the window; a hit 100 ms later is ignored; a hit after 5 s lands. |
| 2 | a run that is not running takes no damage | HP untouched outside `STATE_RUNNING`. |
| 3 | an enemy death awards exactly one kill | One lethal call yields exactly one kill, one defeat, and deactivates the enemy. |
| 4 | a dead enemy is skipped by the shot loop | A live shot aimed at an already-dead enemy pays no second kill. |
| 5 | two shots on one enemy pay one kill | 15 HP against two 10-damage shots: both land (HP reaches −5) and exactly one kill is awarded. |
| 6 | a piercing shot hits an enemy only once | The projectile survives contact and two further frames without re-damaging the same enemy. |

### Construction note

`RunWorld` cannot be built on a desktop JVM — its constructor reaches `Image.createImage`, whose MIDP
stub throws an `Error` that `AssetLibrary`'s `catch (Exception)` cannot catch. Confirmed directly:

```
AssetLibrary FAILED: java.lang.Error: API Stub has been used
```

Instances are therefore allocated without running the constructor, and only the fields the
exercised methods touch are set. Pool arrays are built with `java.lang.reflect.Array` and every
slot materialised, because such arrays are null-filled whereas production fills them with
`new Pickup()`. The methods under test are the genuine production bytecode. Test-only tooling: no
file under `src/` references `sun.*`.

---

## 4. Verification

Full suite, pinned stubs, `--release 8`:

| Suite | Checks | Failures | Result |
|---|---|---|---|
| `CoinBalanceTest` | 408 | 0 | PASS |
| `SaveStoreLegacyMigrationTest` | 245 | 0 | PASS |
| `SaveStoreCoinPersistenceTest` | 159 | 0 | PASS |
| `SaveStoreRmsPathTest` | 20 | 0 | PASS |
| `RunWorldCoinWiringTest` | 11 | 0 | PASS |
| `RunWorldEventOnceTest` | 14 | 0 | PASS |
| **Total** | **857** | **0** | **PASS** |

### Negative controls — each mutation detected only by the new test

| Control | Mutation | Result |
|---|---|---|
| NC-C | player invulnerability window removed | FAIL, 2 checks |
| NC-D | pool loop stops skipping dead enemies | FAIL, 1 check |
| NC-E | per-shot `hitMask` guard removed | FAIL, 1 check |
| NC-F | run-state check removed from `damagePlayer` | FAIL, 1 check |
| NC-G | piercing shots consumed on contact | FAIL, 1 check |

Two of these initially **passed** and exposed real gaps in the tests rather than in the game:

- NC-D passed because test 4 originally ran the shot loop with no live shot, so the guard was
  never consulted. Fixed by aiming a real projectile at the corpse.
- NC-G passed because test 6 asserted only the resulting HP, which is the same whether the
  projectile survives or not. Fixed by asserting the projectile is still in flight, making the
  premise of the test explicit.

Both were corrected and re-verified. This is recorded because the first version of these tests
would have been exactly the "green but blind" coverage this project already suffered once.

### Build

- `./tools/build_fullgame.sh` → exit 0
- `python3 tools/verify_fullgame_build.py` → `PASS: JAR=836555 bytes, assets=189, wiki rows verified`
- `git diff --check` → exit 0

---

## 5. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldEventOnceTest.java   (new, 14 checks)
```

No production source was modified in this group. The audit found no defect worth changing.

---

## 6. What is still unverified

**Needs an emulator or a handset:**

- Entity spawning, AI, magnet collection, and rendering — none are reachable headlessly, because
  `AssetLibrary` cannot be constructed off-device.
- Boss encounter flow end to end, including stage clear and results.
- All device-level RMS behaviour carried over from the S2.3 report.

**Known open, by design:**

- **Q2 / Q3** — victory `coinClear` and level-up `+100` remain run-local, so the results screen can
  show coins the permanent balance did not receive. This is the main player-visible inconsistency
  in the build and needs a decision the sources cannot supply.
- MEDIUM-1 and MEDIUM-2 from the S2.3 report: legacy mutators mutate memory before `save()`, and
  the close-failure fake is more optimistic than the real adapter.

---

## 7. Next queued groups

1. **Q2 / Q3 decision** — resolve the results-screen versus saved-balance inconsistency.
2. Extend headless coverage to the next high-risk gameplay paths: EXP → level-up → offer selection,
   and boss encounter completion, using the same allocate-and-drive technique.
3. Carry over the open MEDIUM/LOW findings listed in the S2.3 report.