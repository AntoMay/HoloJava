# HOLD-02 and HOLD-03 — Skill, Special, and Stat-Counter Boundaries

**Date:** 2026-10-15
**Change group:** The two remaining items on the remediation backlog's HOLD list
**Outcome:** Parity confirmed, verified, committed locally. **No production code changed.**

Closes:

- **HOLD-02** — *"`skillAcquired` penuh — Butuh verifikasi seluruh penggunaan"*
- **HOLD-03** — *"`statOfferCounts` overflow boundary — Butuh sample level >1000"*

---

## 1. Evidence: parity

Comparing integer and double constants per method between the two disassemblies:

```
MATCH  activateSkill     MATCH  chooseOfferType   MATCH  skillAcquired
MATCH  activateSpecial   MATCH  fillOffer         MATCH  applyOffer
```

Zero differences across the whole skill and offer family, including the **15000 ms** skill cooldown,
the per-character skill effects, and the `r < 7` skill-offer threshold in `chooseOfferType`.

### `statOfferCounts` is write-only in the reference too

| | declaration | constructor | reset | increment |
|---|---|---|---|---|
| Reference | 79 | 236 | 537 / 541 | 7016 (index) |
| Ours | `RunWorld.java:97` | inline initialiser | `:224` | `:1391` |

Both initialise it, zero it on reset, and index it for `++`. **Neither ever reads a counter value**,
so the counter cannot influence gameplay in either build.

### HOLD-03's overflow concern does not apply

- The array is `new int[6]`, and `fillOffer` only ever writes `offers[slot].value = i` for `i` over
  a six-entry weight table, so the index is always in `[0, 5]` and the increment cannot go out of
  bounds.
- The backlog worried about `int` overflow "if level > 1000". A counter of offers-per-stat could
  only overflow after roughly 2.1 billion offers of a single stat in one run, and it is never read
  anyway.

---

## 2. Behaviour established

**Skill.** Gated by `skillPressed && skillAcquired && now >= skillReadyAt` inside `updatePlayer`.
Taking the skill offer sets `skillAcquired`; activating opens a 15000 ms cooldown and applies the
character's effect. A press inside the window changes nothing — neither the cooldown nor the effect.

**Special.** `specialPressed && !specialUsed`. One use per run; a second press is refused by the
gate and the effect is not re-applied.

**Stat offers.** `statOfferCounts[o.value]++` with `o.value` always in `[0, 5]`.

---

## 3. Tests

`tools/tests/me/holojava/fullgame/RunWorldSkillAndStatTest.java` — 32 checks.

| # | Test |
|---|---|
| 1 | the skill is inert until acquired — the key is pressed through `updatePlayer` before and after the offer |
| 2 | the skill opens a fifteen second cooldown — pressed inside the window and after it |
| 3 | the special is available exactly once — pressed twice through the gate |
| 4 | the stat offer index always stays in range — all six indices, plus 400 generated offers |

### Negative controls

| Control | Mutation | Result |
|---|---|---|
| K1 | skill usable without being acquired | FAIL, 2 |
| K2 | skill cooldown shortened to 1 s | FAIL, 2 |
| K3 | special reusable, one-use guard removed | FAIL, 1 |
| K6 | skill cooldown gate dropped (`now >= skillReadyAt`) | FAIL, 2 |
| K4 | `OFFER_SKILL` no longer grants the skill | FAIL, 1 |
| K5 | stat counter indexed one past the array | FAIL (array bounds error) |

**Three of these initially passed and exposed blind tests rather than sound ones.** The first draft
asserted *field state* instead of driving the gate:

- K1 passed because the test only checked that `skillAcquired` was false; it never pressed the key.
- K3 passed for the same reason, using a needlessly indirect assertion.
- K6 passed because the test asserted `t < skillReadyAt` — a statement about the field, which stays
  true whether or not the gate honours it.

All three now drive `updatePlayer`, the real gate. This is the third time in this session that a
negative control caught a test that was quietly checking the wrong thing.

---

## 4. Limitations

- Skill and special *effects* are only asserted as their timestamps; the per-enemy damage and slow
  effects need spawned entities, and `AssetLibrary` cannot be constructed off-device.
- `offers[].newItem`, the hold/reroll interplay, and weapon-level caps remain covered only by
  `RunWorldOfferOnceTest` at the level of single-offer application.
- **No wiki data was used.** Every asserted value comes from the reference bytecode.
- No emulator or handset run was performed.

---

## 5. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldSkillAndStatTest.java   (new, 32 checks)
```

No production source changed; the JAR is byte-identical in size to the previous commit.

Full suite: **1073 checks, 0 failures** across twelve suites. Build exit 0, verifier
`PASS: JAR=836627 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.

---

## 6. Backlog status

All three HOLD items from `reports/comprehensive-audit/07_REMEDIATION_BACKLOG.md` are now closed:

| Item | Resolution |
|---|---|
| HOLD-01 | Spawn schedule verified at parity; `nextWaveAt` dead in both |
| HOLD-02 | Skill/special verified at parity; all gates now covered |
| HOLD-03 | No overflow possible; counter is write-only in both builds |