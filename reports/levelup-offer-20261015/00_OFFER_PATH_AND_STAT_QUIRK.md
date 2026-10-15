# Level-Up Offer Path — Single-Event Invariants and a Reference-Faithful Quirk

**Date:** 2026-10-15
**Change group:** EXP → level-up → offer selection verification
**Outcome:** Verified, committed locally. **No production code was changed** — the audit found the
port faithful and one apparent bug proved to be inherited from the reference.

---

## 1. A serious-looking bug that turned out to be faithful

While wiring tests for the offer path, the following was observed:

```java
// RunWorld.applyOffer, OFFER_STAT branch
statOfferCounts[o.value]++;
applyStat(o.value);        // e.g. atkPercentBonus += 8
recomputeStats();          // atkPercentBonus = 0; ...
```

`recomputeStats()` zeroes exactly the fields `applyStat()` increments. Measured, driving the real
production `applyOffer`:

| Stat offer | Effect observed | Offer counted? |
|---|---|---|
| 0 — Max HP | `baseMaxHp` 100 → 106, `playerMaxHp` → 106 | yes |
| 1 — Attack | `atkPercentBonus` stays 0 | yes |
| 4 — Pick Up | `pickupPercentBonus` stays 0 | yes |
| 3 — Crit | `critPercentBonus` stays 0 | yes |

So seven of the eight stat offers are consumed and counted but produce no effect. That reads like a
severe gameplay bug.

### It is not a porting defect. The reference does the same thing.

Reference JAR bytecode:

```
private void applyOffer(int);
  ...
  247: invokespecial  #536   // Method applyStat:(I)V
  251: invokespecial  #582   // Method recomputeStats:()V

private void recomputeStats();
    0: putfield  atkPercentBonus = 0
    7: putfield  hastePercent = 0
   12: putfield  speedPercentBonus = 0
   17: putfield  pickupPercentBonus = 0
   22: putfield  critPercentBonus = 0
   27: putfield  expPercentBonus = 0
   32: putfield  defensePercent = 0
```

Same call order, same seven fields zeroed. Stat 0 survives only because `recomputeStats` reads
`baseMaxHp` as its starting value instead of resetting it.

### Decision: not changed

Fixing this would be inventing a divergence from the reference, which the working rules forbid in
the absence of evidence that the port is wrong. The evidence says the port is right.

The behaviour is now **pinned by test 3**, and negative control **O3** removes the wiping so the
suite fails. Anyone who later "fixes" the stat offers will be stopped by a test that explains the
reference evidence, rather than silently diverging.

If the project does want to fix an upstream quirk, that is a deliberate design decision and should
be recorded as such — it is not a bug fix.

---

## 2. Tests added

`tools/tests/me/holojava/fullgame/RunWorldOfferOnceTest.java` — 25 checks.

| # | Test | Proves |
|---|---|---|
| 1 | `applyOffer` ignores the control slots | Slots 4 and 5 are reroll and hold and grant nothing; out-of-range indexes are refused. |
| 2 | the max-HP stat offer takes effect | Stat 0 raises both `baseMaxHp` and `playerMaxHp`, and is counted once. |
| 3 | percentage stat offers match the reference | Attack, pickup, and crit bonuses stay 0 while the offer is still counted — with the reference evidence in the Javadoc. |
| 4 | an offer is applied only once per level-up | Three `selectOffer()` presses apply one offer; state returns to running. |
| 5 | `selectOffer` is inert outside the level-up state | Refused while running, while paused, and when `offerCount == 0`. |
| 6 | an EXP pickup grants exactly one level | A 500-EXP pickup against a 79 threshold yields level 2, not 7; the run waits on an offer. |
| 7 | a reroll consumes without applying | A reroll spends one charge, applies nothing, and leaves the player choosing. |

**Construction.** `RunWorld` cannot be built headlessly (see the S2.3 report). Fields are written
through `sun.misc.Unsafe` **by their real type**, because several — including the final `offers`
array — cannot be set by plain reflection on modern JDKs. Reaching `Unsafe` purely reflectively keeps
the file compiling under `--release 8`. Pool arrays are fully materialised.

---

## 3. Verification

Full suite, pinned stubs, `--release 8`:

| Suite | Checks | Failures | Result |
|---|---|---|---|
| `CoinBalanceTest` | 408 | 0 | PASS |
| `SaveStoreLegacyMigrationTest` | 245 | 0 | PASS |
| `SaveStoreCoinPersistenceTest` | 159 | 0 | PASS |
| `SaveStoreRmsPathTest` | 20 | 0 | PASS |
| `RunWorldCoinWiringTest` | 11 | 0 | PASS |
| `RunWorldEventOnceTest` | 14 | 0 | PASS |
| `RunWorldClearRewardTest` | 16 | 0 | PASS |
| `RunWorldOfferOnceTest` | 25 | 0 | PASS |
| **Total** | **898** | **0** | **PASS** |

### Negative controls

| Control | Mutation | Result |
|---|---|---|
| O1 | `selectOffer` state gate removed | FAIL, 4 checks |
| O2 | `applyOffer` index bound check widened past the control slots | FAIL, 3 checks |
| O3 | `recomputeStats` no longer wipes stat bonuses | FAIL, 1 check |
| O4 | EXP level-up loop drops the running-state guard | FAIL, 1 check |
| O5 | `selectOffer` no longer returns to `STATE_RUNNING` | FAIL, 2 checks |

### Build

`./tools/build_fullgame.sh` exit 0; `verify_fullgame_build.py` → `PASS: JAR=836584 bytes, assets=189,
wiki rows verified`; `git diff --check` exit 0.

---

## 4. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldOfferOnceTest.java   (new, 25 checks)
```

No production source changed. The JAR size is unchanged from the decision-C commit, confirming it.

---

## 5. Findings recorded, not acted on

- **Stat offers 1–7 are inert** — verified as reference-faithful. Documented above and pinned by a
  test.
- **Held-offer aliasing** — `generateOffers` shifts array references, so after a hold the same
  `Offer` object can occupy two slots. The reference bytecode does the identical
  `astore` / shift / `aastore` sequence. Left alone.

---

## 6. Still unverified

Device-level RMS behaviour, `setRecord` durability, 56-byte round-trip, on-device heap cost, and
anything involving spawned assets, rendering, or the real pickup magnet. No emulator or handset run
was performed.

---

## 7. Next queued group

Boss encounter completion and stage progression transitions: encounter ordering, the
`encounterStarted` / `encounterDefeated` bookkeeping, win and lose transitions, and that a stage
cannot be completed twice — using the same technique and negative-control discipline.