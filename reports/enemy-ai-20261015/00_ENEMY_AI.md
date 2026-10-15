# Enemy AI, Movement, Contact Damage, Knockback, Despawn

**Date:** 2026-10-15
**Change group:** `updateEnemies` and the helpers it dispatches to
**Outcome:** Parity confirmed. **No production code changed.**

---

## 1. Parity

Seven of the nine AI-family methods carry identical integer and double constants against the
reference bytecode:

```
MATCH  updateEnemies (full dispatch chain)   MATCH  moveToward
MATCH  applyEnemyKnockback                   MATCH  applyKnockback
MATCH  healNearbyEnemies                     MATCH  nowAttackDelay
MATCH  spawnEnemy
```

The two differences are the known `320`/`240` → world-dimension decouplings in `clampEnemy` and
`placeAtEdge`. **No defect found.**

---

## 2. Behaviour verified

| Area | Result |
|---|---|
| Chaser | Steps one normalised step per tick toward the player on both axes; reverses when the player is on the other side |
| Shooter | Kites: backs away inside 60 px, closes beyond 100 px, holds position in between, in both mirror directions |
| Contact damage | Requires `invulUntil <= now < e.nextAttack`; lands, reschedules the enemy, and repeats are stopped by the player's window |
| Invulnerability | A protected player takes no contact damage, and the window is neither refreshed nor cleared |
| Knockback | Displaces on top of the enemy's own step while the window is open, then stops contributing |
| Clamping | Both corners: an enemy knocked far past the left/top is clamped to `-w`/`-h`, and past the right/bottom to `worldWidth`/`worldHeight` |
| Spawn placement | Lands on an edge and is always at least 80 px from the player, **including when the player hugs an edge** where a naive pick would land on top of them |
| Bomber | Deactivates on contact, releasing its slot, after dealing damage |
| Healer | Restores nearby enemies without exceeding their maximum |
| Charger | Locks a direction, dashes along it, and the window is time-bounded |
| Scrolling | Enemy movement is world-space; a scrolled camera does not change the step |
| knockResist | Survives inside the ten second window, then decays to zero |

### Two behaviours worth writing down

**Contact damage reuses the ranged-attack timestamp.** The branch is
`if (now < invulUntil || now >= e.nextAttack) { /* nothing */ } else if (overlap(...))`. Because
`spawnEnemy` sets `nextAttack = now + cooldownMs`, a freshly spawned enemy *can* touch, and stops
being able to once that timestamp passes. This is inherited, not invented, and is now pinned.

**Invulnerability is guarded twice.** The contact branch checks `now < invulUntil`, and
`damagePlayer` independently returns early when `playerInvulnerable(now)`. Confirmed identical in
the reference — its `updateEnemies` reads `invulUntil` at offset 768 and `damagePlayer` calls
`playerInvulnerable` at offset 9. The outer check is therefore redundant with the inner one.

---

## 3. Negative controls — ten of eleven detected

| Control | Mutation | Result |
|---|---|---|
| E1 | chaser movement removed | FAIL, 4 |
| E2 | shooter retreat band removed | FAIL, 1 |
| E3 | contact collision check removed | FAIL, 1 |
| E4 | outer invulnerability check removed | **PASS — behaviour-equivalent, see below** |
| E5 | knockback application removed | FAIL, 3 |
| E6 | enemy right/top clamp removed | FAIL, 2 |
| E7 | spawn minimum distance removed | FAIL, 12 |
| E8 | bomber no longer despawns | FAIL, 1 |
| E9 | healer no longer heals | FAIL, 1 |
| E10 | charger never commits to a dash | FAIL, 1 |
| E11 | knockResist decay removed | FAIL, 1 |

**E4 is honestly reported, not counted as coverage.** Removing the outer invulnerability check
changes nothing observable, because `damagePlayer` refuses the hit on its own. That is a genuine
redundancy present identically in the reference, not a blind test, so the control is recorded as
behaviour-equivalent rather than quietly ignored or "fixed".

**Five controls initially passed and exposed blind tests**, all corrected:

- **E2** — the shooter test only placed the enemy to the right of the player, where the *approach*
  branch moves it the same way. Added the mirrored case, where only the retreat line can act.
- **E3** — the contact test only used an overlapping enemy, so dropping the overlap test changed
  nothing. Added a distant enemy that must never deal contact damage.
- **E6** — the clamp test only pushed left, and the left clamp lives on a different source line
  that the mutation did not touch. Added the opposite corner for the right and top bounds.
- **E7** — with the player centred, all four edges are already more than 80 px away, so the retry
  loop was never load-bearing. Added a player hugging the top-left corner, where it is.
- **E11** — knockResist decay was not covered at all. Added a dedicated test.

Three of my own expectations were also wrong and were corrected against the code rather than
fudged: the shooter's back-away sign, the contact gate requiring a *future* `nextAttack`, and a
scroll test that had pinned `viewWidth` to the world width, disabling travel entirely.

---

## 4. Limitations

- Enemy type variety is exercised through one fixture per behaviour; the catalog holds 13 enemies
  across 5 behaviours, and per-enemy `collW`/`collH`/`speed` differences are not enumerated.
- Boss AI shares `moveToward`, `applyKnockback`, `clampEnemy` and `nowAttackDelay` but is covered
  separately in `RunWorldBossEncounterTest` only for the encounter director, not its combat.
- Animation cycling (`anim % 3`) is exercised incidentally but not asserted.
- Enemy shooting is covered by `fireEnemyShot`'s presence in the constant comparison, not by
  asserting the projectile that a shooter produces.
- No wiki data was used; `holocure.wiki.gg` remains blocked from this environment.
- No emulator or handset run was performed.

---

## 5. Files changed

```
?? tools/tests/me/holojava/fullgame/RunWorldEnemyAiTest.java   (new, 176 checks)
```

No production source changed; the JAR is byte-identical in size to the previous commit.

Full suite: **1471 checks, 0 failures** across sixteen suites. Build exit 0, verifier
`PASS: JAR=836627 bytes, assets=189, wiki rows verified`, `git diff --check` exit 0.

---

## 6. Open decisions (carried forward, unchanged)

1. **`uber_sheep`** — reference catalog item missing here; inert in both, and adding it would dilute
   ten working items. Recommendation: drop it.
2. **Weapon attack gate** — reference auto-fires; this build requires the attack input.
   Recommendation: keep the gate.