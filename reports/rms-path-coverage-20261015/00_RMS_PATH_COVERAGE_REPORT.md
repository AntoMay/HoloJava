# S2.3 — Close the RMS-Path Coverage Gap (Unblocks the Coin Persistence Commit)

**Date:** 2026-10-15
**Change group:** Test-seam completion for the S2.2 absence-classification fix
**Outcome:** Verified, committed locally.

---

## 1. Why this group came first

The S2 coin-persistence work was left uncommitted because the S2.1 final review returned
**BLOCK S2** with two HIGH findings:

- **HIGH-1** — `SaveStore.RmsRecordStorage.read()` and `SaveStore.isAbsence()` were never executed
  by any test. Proven by negative control: reverting the absence fix left all 812 checks green.
- **HIGH-2** — `testMissingStoreAllowsNewSave` did not test what its name claimed. The
  `FakeStorage` replaces the RMS adapter outright, so it can never express "store missing"; the test
  was a duplicate of `testAbsentRecordStillInitialises`.

Per the working policy that unverified changes may not be committed, this group had to be finished
before anything could be built on top of S2.

---

## 2. Root cause

The test seam was one level too high. `FakeStorage implements SaveStore.RecordStorage`, which
*replaces* `RmsRecordStorage`. Everything above the adapter was well covered; the adapter itself —
the exact code that regressed — was structurally unreachable.

---

## 3. Change made

Minimal and confined to the seam. Gameplay, rendering, HUD, record format, and the store name are
untouched.

### `src/java-me-fullgame/me/holojava/fullgame/SaveStore.java`

1. New `RecordStoreOpener` interface — the single substitutable step, `open(name, createIfMissing)`.
2. New private `RealRecordStoreOpener` — the production implementation, a direct call to
   `RecordStore.openRecordStore`.
3. `RmsRecordStorage` gains a package-private constructor taking an opener. The no-arg constructor
   keeps `new RealRecordStoreOpener()`, so production behaviour is identical.
4. `RmsRecordStorage` changed from `private` to package-private so the adapter is reachable from the
   test in the same package.

The `read()` and `write()` bodies are otherwise **unchanged** — the exception classification from
S2.2 is preserved verbatim.

### `tools/tests/me/holojava/fullgame/SaveStoreRmsPathTest.java` (new, 20 checks)

| # | Test | What it proves |
|---|---|---|
| 1 | `isAbsence against real RMS exceptions` | The shipped predicate matches real `RecordStoreNotFoundException` / `InvalidRecordIDException`, and does **not** match `RecordStoreException`, `RecordStoreNotOpenException`, `RecordStoreFullException`. Also asserts the two absence types remain distinct siblings. |
| 2 | `read maps both absence types to null` | **The S2.2 regression.** An absent store and an absent record both read as `null`, not as a failure. |
| 3 | `other failures stay failures` | Each other RMS failure still raises `StorageFailure`. |
| 4 | `write reports failure when opening fails` | A write that never reached the store is never reported as committed. |
| 5 | `read opens the established store without creating it` | The adapter still opens store `holojava_fullgame_v1` with `createIfMissing == false` — the RMS contract was not quietly altered. |
| 6 | `new install through the real adapter` | End to end through the real adapter: no store → `LOAD_ABSENT` (first save allowed); any other error → `LOAD_FAILED` with writes refused. |

### How real RMS exceptions are obtained

The pinned MIDP stub throws `Error("API Stub has been used")` from every constructor, so no RMS
exception can be created with `new`. The test allocates genuine
`javax.microedition.rms.*` instances **without running a constructor**, via
`sun.misc.Unsafe.allocateInstance`, reached purely reflectively (`Class.forName`,
`getDeclaredField`, `Method.invoke`) so the file still compiles under `--release 8`.

These are real RMS objects, so production's `instanceof` matching is exercised for real rather than
simulated with a stand-in hierarchy.

**This is test-only.** No game source file references `sun.*`. Verified after the build: `grep -rl
'sun/misc' build/java-me-fullgame/classes` → none. Test sources live in `tools/tests/` and are never
compiled into the JAR.

---

## 4. Verification actually performed

### Positive control — all suites, pinned stubs, `--release 8`

| Suite | Checks | Failures | Result |
|---|---|---|---|
| `CoinBalanceTest` | 408 | 0 | PASS |
| `SaveStoreLegacyMigrationTest` | 245 | 0 | PASS |
| `SaveStoreCoinPersistenceTest` | 159 | 0 | PASS |
| `SaveStoreRmsPathTest` | 20 | 0 | PASS |

Game compile exit 0. Test compile exit 0. `git diff --check` exit 0.

### Negative control — the decisive check

The absence classification was deleted from `RmsRecordStorage.read()` in a **scratch copy outside
the repository**, then the same suite was run:

| Suite | Checks | Failures | Result |
|---|---|---|---|
| `CoinBalanceTest` | 408 | 0 | PASS |
| `SaveStoreLegacyMigrationTest` | 245 | 0 | PASS |
| `SaveStoreCoinPersistenceTest` | 159 | 0 | PASS |
| `SaveStoreRmsPathTest` | 20 | **5** | **FAIL** |

This is the result that was missing in the S2.1 review. The regression is now detected, and only
the test written for it detects it.

### Build and packaging

- `./tools/build_fullgame.sh` → exit 0
- `python3 tools/verify_fullgame_build.py` → `PASS: JAR=836512 bytes, assets=189, wiki rows verified`
- Output SHA-256: `b9a2773d802d21902949b46fec99618c72aecf4676a9ecb32952be43e3cb1b8a`
- Reference JAR re-verified unchanged: `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb`

---

## 5. Files changed

```
 M src/java-me-fullgame/me/holojava/fullgame/SaveStore.java            (+52 / -8)
 M tools/tests/me/holojava/fullgame/SaveStoreLegacyMigrationTest.java  (+22 / -13)
?? src/java-me-fullgame/me/holojava/fullgame/CoinBalance.java
?? tools/tests/me/holojava/fullgame/CoinBalanceTest.java
?? tools/tests/me/holojava/fullgame/SaveStoreCoinPersistenceTest.java
?? tools/tests/me/holojava/fullgame/SaveStoreRmsPathTest.java
```

---

## 6. Intentional differences from the reference JAR

The reference JAR has no coin persistence and no storage seam. Differences are additive and
localised:

- `CoinBalance`, `Viewport`, `WikiCatalogData` — new types with no reference counterpart.
- `SaveStore$RecordStorage`, `SaveStore$RecordStoreOpener`, `SaveStore$RmsRecordStorage`,
  `SaveStore$RealRecordStoreOpener`, `SaveStore$StorageFailure`, `SaveStore$1` — nested types of the
  seam. Record store name, record id, and byte layout for bytes 0–47 are unchanged.

---

## 7. Residual uncertainty and risk

**Unverified off-device — these still need an emulator or a handset:**

1. Whether real firmware raises `RecordStoreNotFoundException` from
   `openRecordStore(name, false)` when no store exists. The pinned stub declares it; the test drives
   the adapter's *handling* of that exception, not a device's decision to raise it.
2. Whether `getRecord` on a closed store raises `RecordStoreNotFoundException` (which `isAbsence`
   would treat as absent) or `RecordStoreNotOpenException`. The pinned stub declares the latter.
   The stub is authoritative for compilation, not for device behaviour.
3. Whether `setRecord` without an explicit commit operation is durable at `closeRecordStore()`. The
   code's durability claim rests entirely on that, and is documented as such in the source.
4. Whether a 56-byte record round-trips intact on device, and the real RMS record size limits.

**Carried-over findings from the final review that this group does not address** (documented, not
fixed, as they are outside the seam):

- **MEDIUM-1** — `setCompleted` / `setHardcore` / `setSelectedCharacter` mutate `data` before
  `save()` and ignore its result. A mutation whose write was refused is later persisted by an
  unrelated `creditCoins`. No data loss; undocumented coupling.
- **MEDIUM-2** — `FakeStorage.failClose` models a close failure as "committed nothing", while
  `RmsRecordStorage.write()` models it as "`setRecord` succeeded, close failed", where the bytes have
  most likely landed. The fake is the more optimistic of the two.
- **LOW-1** — `SaveStoreCoinPersistenceTest:577` comment claims a `RecordStoreException`; the fake
  throws `StorageFailure`.
- **LOW-2** — the class Javadoc of `SaveStoreCoinPersistenceTest` says it "never touches
  `javax.microedition.rms`", which is no longer true, and its "does not prove" section does not
  mention that the adapter was untested. Now partly answered by the new file.
- **LOW-3** — `SaveStoreLegacyMigrationTest.testNonLegacyRecordLengths` is tautological: it computes
  `min(len, 56)` locally and asserts equality with `min(len, 56)`, never calling `SaveStore.load()`.
- **LOW-4** — `CoinBalance.cap()` has no production caller.

---

## 8. Next queued groups

1. Wire `creditCoins` into the run coin pickup (`RunWorld.collectPickup`, type 1) — the behaviour
   the whole S2 chain was built for, currently with **no production caller**.
2. Decide the MEDIUM-1 mutator rollback question before the coin wiring makes the coupling more
   visible.
3. Re-examine `SaveStoreLegacyMigrationTest.testNonLegacyRecordLengths` (LOW-3): either drive it
   through `load()` or restate it as documentation.
4. Gameplay gap inventory against the reference JAR, using the negative-control discipline
   established here: every claim of coverage must survive a deliberate break.