# UI State Machine — Transitions, Input, Latches

**Date:** 2026-10-15
**Change group:** `FullGameCanvas` screen transitions and per-screen input
**Outcome:** Parity confirmed. One minimal extraction made to remove test-theatre.
**Production change: 14 lines, behaviour-identical.**

---

## 1. Harness note: no seam was needed except one

Every per-screen handler already takes its input as explicit booleans — `updateMain(up, down, fire)`,
`updateCharacters(..., left, right)`, `updateStages(...)`, and so on — so the tests drive the genuine
production methods directly. Only `update(long)` itself is unreachable, because it calls the MIDP
`getKeyStates()` first; its dispatch is a plain `if / else if` chain over `screen`.

**One exception had to be fixed.** The CONTROLS branch was written inline
(`else if (screen == CONTROLS) { if (leftEdge || rightEdge || fireEdge) screen = SETTINGS; }`), and the
first version of its test *mirrored that logic inside the test*. That asserted the test, not the
product — and the negative control that removed the branch passed, proving it. The branch was moved
verbatim into `handleControlsInput(boolean back, boolean confirm)`, exactly as `handleRunEnd()` was
earlier, and the test now calls production code. Same call site, same statements, no behaviour change.

## 2. Parity

Eight of the fourteen UI handlers match the reference on constants: `selectableStages`, `stageAllowed`,
`updatePause`, `updatePrep`, `updateResults`, `updateSplash`, `updateStages`, `wrap`.

Every remaining difference is **ours-only, with no reference counterpart**:

| Method | Added constant | Why |
|---|---|---|
| `stageUnlocked` | `99` | The Fantasy Island unlock fix, from reference evidence |
| `updateCharacters` | `8` | `CHARACTER_PAGE_SIZE` — paged character select |
| `updateMain`, `updateModes`, `updateSettings` | `6`, `8` | Screen and page constants |
| `startRun` | `6` | The method itself is an extraction |

No reference-only constants, and no reference method is missing.

---

## 3. Transitions covered

| From | Input | Result |
|---|---|---|
| SPLASH | confirm | MENU, index 0, latches cleared; no input leaves it up |
| MENU | entries 0–4 | CHARACTERS / MODES / CHARACTERS / STAGES / SETTINGS |
| MENU | entry 5 | exit requested, loop stopped, MIDlet asked to shut down |
| MODES | back | MENU with the cursor on the mode entry |
| MODES | confirm on the available mode | CHARACTERS, `modeIndex` recorded |
| CHARACTERS | confirm a character | index set, **persisted via `setSelectedCharacter`**, → STAGES |
| CHARACTERS | confirm the trailing entry | back to MENU |
| CHARACTERS | left on page 0 | MENU; right pages only when another page exists |
| STAGES | confirm a locked stage | **refused** — no transition, `stageIndex` untouched |
| STAGES | confirm an unlocked stage | PREP with the stage recorded |
| STAGES | back | CHARACTERS with the cursor restored to the chosen character |
| SETTINGS | entry 0 | toggles hardcore and persists it |
| SETTINGS | entry 2 | CONTROLS |
| CONTROLS | left, right, or fire | SETTINGS; nothing pressed stays put |
| PREP | back | STAGES with the cursor on the stage being prepared |
| RESULTS | back | MENU, world dropped |
| RESULTS | second entry | MENU, world dropped |
| `startRun` on a locked stage | — | falls back to STAGES and creates no run |

**Index bounds** are asserted by walking past both ends: the main menu stays in `[0,6)`, the mode list
in `[0,2)`, the character list in `[0, CHARACTERS+1)`, and the pause list in `[0,2)`.

**Latches.** `clearInputLatches` drops the skill, special, and attack-pulse flags and rebases every
held-key flag to "not held", so a key held through a transition cannot leak into the next session.

**Irrelevant input.** Confirming without a direction on the menu does not move the cursor; simultaneous
up and down resolve to a single step.

---

## 4. Negative controls — thirteen of thirteen detected

| Control | Mutation | Result |
|---|---|---|
| U1 | splash never advances | FAIL, 1 |
| U2 | menu index wrapping removed | FAIL, 36 |
| U3 | exit entry no longer stops the loop | FAIL, 3 |
| U4 | `startRun` unlock gate removed | FAIL (aborts constructing a run) |
| U5 | stage screen no longer refuses locked stages | FAIL, 2 |
| U6 | character choice no longer persisted | FAIL, 1 |
| U7 | character index wrapping removed | FAIL, 6 |
| U8 | latches no longer cleared | FAIL, 3 |
| U9 | results back no longer returns to the menu | FAIL, 1 |
| U10 | stage back no longer restores the character cursor | FAIL, 1 |
| U11 | prep back no longer restores the stage cursor | FAIL, 1 |
| U12 | controls page no longer returns to settings | FAIL, 3 |
| U13 | pause index wrapping removed | FAIL, 2 |

Two controls initially did not detect anything useful:

- **U12 exposed test-theatre**, described in section 1. This was the important one: a test that
  mirrors production logic will pass whatever production does. It was fixed by extracting the branch
  and calling it.
- **U13 exposed an uncovered path.** The pause cursor's wrapping was never asserted, so a removed wrap
  was invisible. A dedicated test was added.

One of my own fixture errors was also corrected: the results-screen cases relied on `menuIndex` while
`updateResults` reads `resultIndex`, so "the second entry" was silently exercising restart instead.
The index is now set explicitly per case.

---

## 5. Harness limitations — not verified

These are real gaps in the harness, not claims of coverage:

- **`startRun`'s success path cannot run headlessly.** It constructs a `RunWorld`, which reaches
  `AssetLibrary.prepareStage` and the MIDP `Image.createImage` stub. Only its *refusal* path (locked
  stage) is covered; the successful restart from Results and from Prep needs a device.
- **`update(long)` itself is not executed**, since it calls `getKeyStates()`. The per-screen handlers
  it dispatches to are covered directly, but the dispatch chain and its edge-computation
  (`upEdge = up && !upWasDown`) are verified by reading, not by running.
- **Key repeat** is a platform behaviour. Edge detection is implemented; that a held key produces one
  edge per press is not exercised here.
- **Pause during gameplay** — `updatePause` calling `world.resume()` or abandoning the run — needs a
  live `RunWorld`, so only its index handling is covered.
- **No rendering or layout is verified.** `paint()`/`render()` need a MIDP `Graphics`; nothing about
  how any of this looks has been checked.
- No wiki data was used; `holocure.wiki.gg` remains blocked from this environment.
- No emulator or handset run was performed.

---

## 6. Files changed

```
 M src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java   (+14 / -1)
?? tools/tests/me/holojava/fullgame/UiStateMachineTest.java         (new, 112 checks)
```

The production change is the CONTROLS extraction only. Full suite: **1583 checks, 0 failures** across
seventeen suites. Build exit 0, verifier `PASS: JAR=836702 bytes, assets=189, wiki rows verified`,
`git diff --check` exit 0.

---

## 7. Open decisions (unchanged)

1. **`uber_sheep`** — inert reference item missing here; adding it would dilute ten working items.
2. **Weapon attack gate** — reference auto-fires; this build requires the attack input.