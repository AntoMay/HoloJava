# Test Plan

Source of truth: test strategy, cases, and validation evidence.

The original table in this file (T-001..T-016, every row "Planned", dated
2026-10-13) described a vertical slice that was never built that way, while the
real tests grew up elsewhere. It has been retired: tests are code now, under
`tools/tests/me/holojava/fullgame/`, and this file records how they are
organised and what they must guarantee.

See also `docs/GAMEPLAY_DESIGN.md` for the behavior those tests express.

## Testing Layers

1. **Catalog and data validation.** `ContentCatalog.validate()` runs at MIDlet
   startup (`FullGameMIDlet`), and suites pin the data itself:
   `BossStatsWikiTest` (every boss row against the wiki figures),
   `WikiCatalogDataTest` and `CollabCsvPackagedTest` (the packaged CSVs),
   `CharacterShopTest` and `CoinBalanceTest` (economy tables).
2. **Behavior suites.** One system per suite, driven through reflection on the
   real classes — no test doubles of our own code. The 28 `RunWorld*` suites
   cover scheduling, combat, pickups, offers, skills, HUD wiring, audio, and
   lifecycle; the three `SaveStore*` suites cover persistence including refused
   writes and legacy saves; `UiStateMachineTest` covers menu flow.
3. **Asset contract.** `RunWorldBossArtTest` pins catalog entries to the actual
   PNG dimensions on disk, so art cannot drift away from its hitbox unnoticed.
4. **Build gates.** Not suites — they run inside every build and test
   invocation. After each production compile, `tools/verify_cldc_classes.sh`
   rewrites `StringBuilder` and checks every `java.*`/`javax.*` reference
   against the pinned CLDC/MIDP stubs (`tools/check_cldc_compliance.py`); the
   test runner compiles a deliberately violating fixture every run to prove the
   gate still rejects; ProGuard is the second gate over the shipped JAR.

## Running

```sh
sh tools/run_fullgame_tests.sh                 # every suite
sh tools/run_fullgame_tests.sh SuiteName ...   # selected suites
```

At the time of writing: 41 suites, ~4,300 checks. Every commit is expected to
leave them green together with `sh tools/build_all.sh`.

## Rules

- Suites must be deterministic and device-free: no sleeps (rewind the clock
  instead), no network, no writes outside the run's own scratch area.
- A pin test states what it pins and why in its javadoc, so a future change
  must edit the pin deliberately rather than let the value drift silently.
- A fixed defect ships with a regression test that fails before the fix.

## Historical

The retired T-001..T-016 table survives in git history. Its categories —
fixed timestep, coordinate transforms, collision, damage, XP thresholds, spawn
timers, loader failure reporting, non-blocking audio — are each covered by one
of the current suites above.
