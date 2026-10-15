# Progress Report — Evidence-Based Gameplay Implementation

**Date:** 2026-10-15
**Scope:** Initial inventory through the first three implementation groups.
**Reference artifact:** `/home/antomay/Downloads/HoloJavaFullGame.jar`
SHA-256 `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — verified this session
against the recorded reference hash. Not modified.

---

## 1. Groups completed

| Group | Commit | Subject |
|---|---|---|
| S2.3 | `0c4ff4d` | Close the RMS-path coverage gap; unblocks the coin persistence commit |
| S3 | `e7c3db4` | Persist collected coin pickups to the permanent balance |
| Parity | `504143b` | Verify port parity; prove gameplay events are processed once |

Each was committed only after its change group passed the full suite, a negative control, and the
project build verifier.

---

## 2. Initial state and what was found

**Git at start:** branch `main`, HEAD `3a8681c`, two tracked modifications
(`SaveStore.java`, `SaveStoreLegacyMigrationTest.java`) and three untracked source files left over
from the coin-persistence work, plus **133 untracked audit reports**. Nothing was discarded.

That leftover work had been returned **BLOCK S2** by the preceding final review, with two HIGH
findings: the S2.2 absence fix had zero test coverage (proved by a suite-wide negative control), and
the regression test written for it did not test its own name. It could not be committed as it stood,
so it was the first thing to finish.

**Coin chain had no production caller.** `creditCoins`, `spendCoins`, and `coinBalance` were defined,
tested, and documented, but nothing in the game ever called them. The feature the whole S2 chain was
built for did not happen in game.

**Gameplay logic had zero headless test coverage.** Every existing test covered storage or the coin
model. Nothing touched movement, combat, enemies, bosses, EXP, or level-up.

---

## 3. Evidence used

### Reference JAR

Used for: member parity, integer-constant drift analysis, and confirming the exact shape of
`collectPickup`, `damageEnemy`, `damagePlayer`, `updateShots`, and `generateOffers`.

No decompiler is available in this environment; `javap -p` and `javap -c` were used, so every claim
below is from bytecode, not from inference.

### Prior audit reports

`reports/coin-persistence-audit/00_COIN_AND_STAT_SHOP_DESIGN.md` supplied the authorised scope for
S3 and identified Q2/Q3 as deferred decisions. `reports/implementation-readiness-review/`,
`reports/compact-hud-implementation/`, and `reports/visual-redesign-audit/` explain the intentional
HUD divergences. The 133 existing reports were read, not rewritten.

### Wiki

**Not consulted this session.** No change in these three groups required a HoloCure data value.
Every implemented behaviour was derived from the reference bytecode or from an already-approved
design document. No mechanic, damage number, drop rate, or character was invented, and no claim here
depends on the wiki. It should be consulted before any content or balance work.

---

## 4. Results

**Port parity.** Zero fields missing. Every constant difference across 109 shared methods traced to
either the deliberate viewport decoupling or the compact-HUD redesign — all resolve to identical
values at the default 320×240 world. **No gameplay value has drifted from the reference.**

**Coverage.** 857 checks across six suites, all passing, from 812 before this session.

| Suite | Checks |
|---|---|
| `CoinBalanceTest` | 408 |
| `SaveStoreLegacyMigrationTest` | 245 |
| `SaveStoreCoinPersistenceTest` | 159 |
| `SaveStoreRmsPathTest` | 20 |
| `RunWorldCoinWiringTest` | 11 |
| `RunWorldEventOnceTest` | 14 |

**Negative controls** — 11 distinct production mutations, each detected by exactly the test written
for it:

| Mutation | Detected |
|---|---|
| Absence classification removed from the RMS read | 5 checks |
| Coin credit removed from `collectPickup` | 8 checks |
| `!p.active` double-collect guard removed | 1 check |
| Player invulnerability window removed | 2 checks |
| Pool loop stops skipping dead enemies | 1 check |
| Per-shot `hitMask` guard removed | 1 check |
| Run-state check removed from `damagePlayer` | 1 check |
| Piercing shots consumed on contact | 1 check |

**Build:** `./tools/build_fullgame.sh` exit 0; `verify_fullgame_build.py` → `PASS: JAR=836555 bytes,
assets=189, wiki rows verified`. `git diff --check` exit 0.

---

## 5. Intentional divergences from the reference

1. **Permanent coin balance.** The reference persists no coins at all. This build does, for
   **collected pickups only**. The run-local counter feeding the HUD and results screen is unchanged,
   so nothing a player sees during a run behaves differently.
2. **Scrolling world.** Shot culling, player clamping, and boss spawn now scale with world
   dimensions instead of fixed 320×240 literals. Identical at default; correct for wide stages.
3. **Compact HUD** and **pause menu**, both pre-existing and documented in their own reports.

## 6. Deliberately NOT changed

The held-offer shift in `generateOffers` moves array references, so after a hold the same `Offer`
object can appear in two slots. Bytecode comparison shows the reference does exactly the same thing
— `offers[heldOffer]` → local, shift `offers[i] = offers[i-1]`, then `offers[0] = local`. This is a
quirk of the reference design, not a porting defect, so it was left alone rather than "fixed"
without evidence.

---

## 7. Open decision required — Q2 / Q3

**This is the one item that cannot be settled from the sources, and it needs a human decision.**

The permanent balance is credited only when a coin **pickup** is collected. Two other coin grants
stay run-local:

- **Victory** — `coins += stage.coinClear + defeatCount / 4` (`RunWorld.java:769`)
- **Level-up** — `coins += 100` (`RunWorld.java:1371`)

**Consequence, visible to the player:** a player can finish a run, see "Coins +40" on the results
screen, and find the permanent balance unchanged. Whether the balance should have moved is exactly
what is undecided.

**Why it cannot be decided from evidence.** The reference persists no coins, so the reference
cannot say whether its direct grants were ever intended to be permanent. The reference is silent on
the question, and there is no wiki statement about a permanent coin balance at all — the feature
does not exist upstream. Any choice here is a design decision, not a porting correction.

**Minimum decision needed — one of:**

- **A.** Persist the two direct grants too, so the results screen and the saved balance always agree.
- **B.** Persist neither, and show the permanent balance separately from the run total so the
  results screen stops implying the two are the same number.
- **C.** Persist the level-up grant but not the victory grant, or the reverse.

I recommend **A**: it is the smallest change, it removes the inconsistency entirely, and it matches
the existing design rule already written down in the coin design ("only what is *collected* becomes
permanent" was written when the other two grants were not yet decided, not because they were meant
to be excluded).

Work not blocked by this decision continues in the next group.

---

## 8. Other open findings, carried forward

| Finding | Status |
|---|---|
| MEDIUM-1 — `setCompleted` / `setHardcore` / `setSelectedCharacter` mutate memory before `save()` and ignore its result; a refused write is later persisted by an unrelated `creditCoins`. No data loss. | Open, documented |
| MEDIUM-2 — `FakeStorage.failClose` models a close failure as "committed nothing"; the real adapter models it as "`setRecord` succeeded, close failed". The fake is the more optimistic. | Open, documented |
| LOW-1 — `SaveStoreCoinPersistenceTest:577` comment claims a `RecordStoreException`; the fake throws `StorageFailure`. | Open |
| LOW-2 — class Javadoc says the coin test "never touches `javax.microedition.rms`", now false. | Open |
| LOW-3 — `SaveStoreLegacyMigrationTest.testNonLegacyRecordLengths` is tautological; computes `min(len,56)` and asserts it equals `min(len,56)`. | Open |
| LOW-4 — `CoinBalance.cap()` has no production caller. | Open |

---

## 9. Still requires an emulator or a handset

- Entity spawning, AI, magnet collection, rendering, and boss encounter flow — `AssetLibrary`
  cannot be constructed off-device, so none of it is headlessly reachable.
- Whether real firmware raises `RecordStoreNotFoundException` from `openRecordStore(name, false)`.
- Whether `getRecord` on a closed store raises `RecordStoreNotFoundException` (treated as absent) or
  `RecordStoreNotOpenException`. The pinned stub declares the latter; the stub is authoritative for
  compilation, not device behaviour.
- Whether `setRecord` without an explicit commit is durable at `closeRecordStore()`.
- Whether a 56-byte record round-trips intact, and real RMS size limits.
- On-device heap cost, including the extra array per coin transaction.

No emulator or device run was performed at any point in this session. Every execution claim above is
a headless JVM run against pinned stubs or a fake storage.

---

## 10. Next queue

1. **Q2/Q3** — awaiting the decision in section 7.
2. Extend headless coverage to EXP → level-up → offer selection and boss encounter completion,
   using the same allocate-and-drive technique and the negative-control discipline established here.
3. Resolve the carried-forward MEDIUM/LOW findings.
4. Consult the HoloCure wiki before any content, balance, or drop-table work.