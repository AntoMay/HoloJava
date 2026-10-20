# Project Status

Source of truth: current phase, blockers, progress, and immediate next action.  
Current roadmap version: `0.2`.

## State on 2026-10-20

- Phases 0-2: complete (the 2026-10-13 record below is retained as history).
- Phases 3-5: complete — the rebuild ships menus, the five reference stages with their four Hard
  variants, time attack, the original stage6, five idols, weapons/collabs/items, shop, save and
  per-stage BGM. `Roadmap.md`'s phase table was caught up on this date.
- Phase 6: retired into the recurring audit cycle (`reports/`); coverage of record is
  `reports/fullgame_asset_manifest.csv` plus the test suites. `SYSTEM_CATALOG.md` and
  `CONTENT_COVERAGE.md` moved to `docs/archive/` on this date.
- Phase 7: active — 41 suites / ~4,300 checks green on every commit, plus the CLDC bytecode gate
  after every compile.
- Phase 8: not started; the asset-redistribution review remains the release gate.

**Done 2026-10-19** (detail in the git log): character ATK was being applied as a bonus and
doubled every character's damage — fixed and the original damage values restored; weapon
hitboxes now follow the sprite; Choose Your Idol was rebuilt; `dist/` split into DevBuild
(rich starting coins) and Public (zero).

**Done 2026-10-20 (audit items 1-9):** collab catalog parsed with CLDC-only APIs; the Hold
offer stashed nothing and cost a charge anyway; Growth and Skill Up wired into the run's
damage; one-shot patch scripts removed; the CLDC bytecode gate now proves emitted bytecode on
every build; music retries and coin writes throttled; boss HP/ATK/EXP replaced with the wiki's
per-stage figures (provenance in `docs/GAME_MECHANICS.md`, pinned by `BossStatsWikiTest`; the
boss-HP ceiling note under Boss Combat Fixes below was updated with it); `README.md` added;
the stale status documents refreshed.

**Done 2026-10-20 (early-game balance):** weapon damage had been authored at a tenth of the
enemy-HP scale — a level-1 main needed ten swings to kill a 30 HP Shrimp, so a zero-coin run's
opening minutes were unwinnable, where the original one-shots its opening fans. The damage column
was retuned to that scale: mains land the one-shot floor on the weakest minute-0 fan at each
idol's ATK% (Trident 30 x 1.10, Pistol 24 x 1.30, Tentacle 34 x 0.90), the pool and collab rows
followed at the same factor, and the skill/special bursts moved with them (Shark Bite/The Ancient
One 60/40, Shark Call/Phoenix Fire 180/240). Cooldowns, counts, reach, radii, enemy HP and every
wiki-sourced boss figure are untouched; rationale in the comment over `BASE_WEAPONS`.
`RunWorldWeaponHitboxTest`'s fixture gained the pickup pool, because a covered enemy now dies and
a kill drops EXP through the production path.

**Known follow-ups from the same audit (not yet done):** per-frame allocation in the render
path, `AssetLibrary.prepareStage` leaking `framesM`, no integrity check over the save record,
duplicated helpers and dead classes, the legacy `src/java-me-{first-slice,phase3,diagnostic}`
modules, and a ProGuard keep-rule naming a nonexistent `MIDLET_VERSION`. (The debug
enemy-outline overlay from that list is now behind `DEBUG_ENEMY_OUTLINE = false`, done in the
git log.)

**Immediate next action:** work that follow-up list, then runtime validation on a real J2ME
target — no suite covers device behavior. Phase 8 licensing review stays the release gate.

## State on 2026-10-13 (superseded by the block above)

- Phase 0: complete.
- Phase 1: complete for the existing asset and symbol evidence inventory.
- Phase 2: complete — architecture, compatibility, and diagnostic toolchain validation verified.
- Phase 3: clean full-game rebuild implementation milestone; the runtime is data-driven and packaged, but full HoloCure parity and broad runtime validation remain deferred.
- Phase 3: rebuilt full-game runtime implemented (`dist/DevBuild/mobile/HoloJavaFullGame.jar`); compile, ProGuard preverification, and packaging validated; J2ME Loader runtime gameplay test pending.
- Phase 4: not started.
- Phase 5: not started.
- Phase 6: not started.
- Phase 7: not started.
- Phase 8: not started.

## Original Content Added (2026-10-17)

One original stage was added on top of the reference content. Nothing below is claimed to be
recovered from the original game; every value is this project's own design decision.

**Arena Hutan Sawit** (`stage6`, catalog index and order 10)

- Enemy: **Biji Sawit**, `AI_ORBIT_CHARGER`, 35 HP / 3 ATK / 0.48 speed / 800 ms cooldown / 10 EXP,
  1-in-80 coin drop, 18x16 hitbox. It approaches at a sub-pixel ~0.72 px/tick, circles the player
  for 2-3 random laps held at a 55 px ring (direction and lap count re-rolled per approach), then
  locks a direction and dashes at 5 px/tick for 650 ms — the frond crown on the sprite is the
  visual read for that wind-up. Originally an `AI_CHARGER` at 4 ATK / 0.55 speed that dashed on
  sight at 7 px/tick; nerfed on both axes and given the circling wind-up on request, so a swarm
  reads as a milling crowd rather than a wall of simultaneous charges. The circling rates are
  driven by a fractional-pixel accumulator because `step()` floors every catalog speed to 1 px.
- Boss: **WoWo**, `AI_SHOOTER`, 110000 HP / 44 ATK / 1000 ms cooldown / 3000 EXP (retuned
  2026-10-20 when Pekodam gained its wiki 100000/35 — it was 9000/300 — so the two-axis step-up
  still holds; the EXP is the original 300/400 pair scaled into the wiki's boss-EXP range).
  It holds distance
  and fires a radial seed burst that widens with elapsed time (5 shots, up to 11 late in a run),
  so the fight is about reading and stepping out of rings rather than tracking one bullet.
  Deliberately above Pekodam, the final boss of the stage this one unlocks behind, on both axes.
- Stage tuning: 1400 ms base spawn interval, 18 concurrent enemies, 12000 clear coins.
- Unlock: originally `unlockRequirement` 4 (clear Fantasy Island first), which is where it
  belongs in the progression. It was then opened to `99`, the always-available marker Fantasy Island
  already uses, on request so the stage could be reached without a 20-minute gate. Both values are
  single literals on the `stage6` line in `ContentCatalog.STAGES`, and the gated form is recorded in
  a comment there.
- Tile: `palm_forest` (128x128, seamless), generated alongside the sprites.

**Save layout change.** The completion map was a single byte per character, which capped stage
orders at 7. `stage4h` already sat at order 8 and was silently unrecordable, and Arena Hutan Sawit
at order 10 would have been too. `SaveStore` now reads two bytes per character, both inside the
0..46 region that was already reserved for completion: byte `i` holds orders 0-7 exactly as
before, byte `5 + i` holds orders 8-15. Existing saves decode unchanged because their high bytes
are zero. See `tools/tests/.../ArenaHutanSawitTest` for the round-trip and layout checks.

**Art provenance.** The tile, the three Biji Sawit frames and the four WoWo frames are original
pixel art generated by `tools/generate_palm_forest_sprites.py`, not extracted HoloCure files, so
they carry no redistribution question from the reference game's assets.

**Not verified.** WoWo itself has never been seen at runtime: it is scheduled for the 10:00
encounter, which no test session has reached. The Biji Sawit enemies, the palm forest tile and the
stage itself were observed running in the desktop emulator. The combat numbers above remain
unvalidated placeholders, not balanced values.

## Current Full-Game Rebuild Snapshot

- New source set: `src/java-me-fullgame`.
- New build artifacts: `build/java-me-fullgame/` and `dist/DevBuild/`.
- Implemented data-driven runtime: menus, Stage/Endless/Time modes, five verified playable Myth characters, eleven stage definitions (the five reference stages, their four Hard variants, time attack, and the original stage6), enemy director, boss schedule, weapons, items, skills, specials, EXP, level-up, collab prerequisites, pause, results, and optional RMS persistence.
- Evidence-based coverage: `docs/rebuild/COVERAGE.md`.
- Asset mapping: `docs/rebuild/ASSET_MAPPING.md` and `reports/fullgame_asset_manifest.csv`.
- The prior diagnostic, first-slice, and Phase 3 prototype artifacts remain preserved as regression evidence.
- J2ME Loader runtime success for the rebuilt JAR is not yet verified.

### Main-menu and pages pass

Follow-on from the Choose Your Idol layout, the surrounding pages were brought in line with it.

**Page headings, not bar labels.** The upper-case style belongs to the pages you open *from* the
main menu, matching CHOOSE YOUR IDOL: PAPAN PERINGKAT, PENCAPAIAN, TOKO, MAIN, HOLO HOUSE,
PENGATURAN, CREDITS, KELUAR appear as the title band of those pages. The gold label box at the
bottom right of the title screen keeps its sentence case ("Papan Peringkat", "Pencapaian", …) —
upper-casing that changed the look of the title screen itself, which is not what was wanted.

Worth knowing if a name changes again: the bar label is baked into `menu_text_*.png`, so it is not
a string edit. `ENTRY_NAMES` in `tools/prepare_title_menu_assets.py` holds the same list and has
to be re-run, or the label box draws the old text while the fallback string says the new one.

**Help text removed.** The `Kembali: FIRE / L / R` footer is gone from every page. Nothing else
needed to say it: every screen already backs out on FIRE or L/R, and the Help page lists the
bindings properly.

**Pengaturan.** Audio / Help / Back. Controls was folded into Help rather than deleted outright,
so the bindings are still documented in one place; `CONTROLS` is no longer a screen and the
constants after it were renumbered.

**Hardcore stays in the shop.** What Settings lost was its own row and the note pointing at
Toko; the `Lainnya` row itself, `BUY_TOGGLE`, and the Endless Mode read all remain.

**Page headings were not actually centred.** `title()` drew at a fixed `x + 90` and `y + 18`,
which only lined up by accident for headings of one particular length -- short ones sat visibly
left of centre, long ones ran off the right. It now centres from the measured width and shares a
`TITLE_TOP` constant with the CHOOSE YOUR IDOL heading, and `drawChooseIdol` draws its heading
through that same helper, so the reference screen and the pages after it cannot drift apart. The
`titleCentered()` variant that had patched over this for TOKO only is gone.

**Credits** is now a scrolling list of centred rows. The text is longer than one screen, so it is
data (`buildCreditRows()`) rather than a hand-stepped sequence of draw calls: adding a credit is a
one-line edit that cannot break the layout. Up/Down scroll, Down stops at `creditsMaxScroll()` so
the last credit is the last reachable row, opening the page resets the offset, and a marker bar
under the list shows how much is left while there is any. `UiStateMachineTest` [7e] checks the
window bounds, that the list is genuinely taller than the window, and that the clamp holds at
both ends.

**`TERKUNCI`** is centred and proportional: the word stands on its own in the locked colour with
no frame around it and no `[ ]` brackets -- a box and brackets made it read like a button when it
is a status -- the copy follows underneath, and a short accent rule closes the block in place of
the old footer line.

**Shop confirmation asks Ya / Tidak.** The popup itself was never removed -- commit `2c55fca`
introduced it and nothing since has touched it -- but its yes/no was *implicit*: FIRE bought, any
d-pad press cancelled. The popup therefore never asked a readable question, and "look again, then
confirm" was the same gesture as the first press. It now shows an explicit `Ya` / `Tidak` pair and
highlights whichever is selected; a d-pad press toggles the answer (never the list behind it) and
FIRE commits the highlighted one. Ya is highlighted when the popup opens, so one FIRE still
confirms and the old reflex still works, and the gesture that used to cancel now lands on `Tidak`
and still cannot spend. `confirmShopAction()` is split out of `buyUpgrade()` so answering executes
in one press rather than re-arming and waiting for a second. `[15b]` drives the Tidak path through
the real input handler.

**Coin readout lowered.** It sat at `y + 20`, level with the heading (which reaches about row 24),
so a long balance ran into the title. It is now at `y + 28` (`COIN_TOP`), which clears the heading
and still lands inside the 40px title band.

**Contact damage was gated on the inverse condition.** `nextAttack` is a "not ready until" stamp,
and every other attack path — bomber, shooter, charger, orbit charger, boss — gates on
`now >= e.nextAttack`. The contact branch had it the other way round, so a chaser could only bite
while its cooldown was *pending*: it hurt you for the first second of its life and was harmless
forever after. Stage 1 is built entirely from chasers (shrimp, dark shrimp) with no bomber, shooter
or orbit charger to fall back on, so once an enemy survived its first second the stage had no
working damage source and the HP readout never moved. Sawit and Fantasy Island felt normal because
their mixes lean on paths that were never affected. It was never character-specific: one 24×24
hitbox for all five characters, now `PLAYER_HIT_W`/`PLAYER_HIT_H`. Two tests had pinned the
inverted behaviour as intended — `RunWorldEnemyAiTest` [3] said outright that a fresh spawn's
future `nextAttack` "is what arms contact damage" — and are rewritten.

**Damage reduction.** The original has a "Defense Up" upgrade — it appears verbatim in the unpacked
string table between "Regeneration" and "Special Attack" — but **no per-character defence stat**:
searching the unpacked symbol table for "defense"/"defence" turns up only `AyameDefenseField`, which
is an ability, and every "reduce damage taken" string belongs to an item or a form. Reduction in
the original comes from what the player picks up, not from who they are.

An earlier pass gave the characters 4–10% each. Those were invented balance numbers presented as a
stat, which contradicts how this project treats evidence, and they also made the larger characters
better on two axes at once. They are back to **0 for all five**. `CharacterDef.defensePercent` is
kept as a field so the shop's Defense Up has an explicit base to stack on rather than an implicit
one, and so the wiring stays testable.

The defence *system* is unchanged and is what the tests cover: Defense Up stacks on the character's
base instead of replacing it, `recomputeStats()` rebuilds from that base so a repeated call neither
compounds nor strips it, and the shop's own contribution is clamped to the 15% the row advertises
rather than to the total, so the two numbers keep separate owners.

**Shop wiring audit.** All 34 rows reach the run: 11 through an `EFFECT_*` that `recomputeStats()`
folds in, and the rest through flags the run actually reads (`specialLocked`, `rerolls`, `holds`,
`marketingLevel`, `weaponLimitLevel`, `itemLimitLevel`, `collabBan`, plus the Hardcore rule and the
Refund All action). No row is a purchase that costs coins and changes nothing.
`RunWorldDefenseTest` holds that as a test rather than as a one-off check, so a future row added
without wiring fails instead of shipping.

### Build outputs

`tools/build_all.sh` empties `dist/` and rebuilds everything. The layout is by target
first, then by flavour:

```text
dist/
  DevBuild/            large coin capital, so the shop can be driven without grinding
    mobile/HoloJavaFullGame.jar    preverified (ProGuard), sideload on a phone
    desktop/HoloJavaFullGame.jar  plain javac output, runs in FreeJ2ME-Plus
  Public/              zero coins, so players earn what they spend
    mobile/HoloJavaFullGame.jar
    desktop/HoloJavaFullGame.jar
```

Each directory also carries the matching `.jad` descriptor. Both flavours name the file
`HoloJavaFullGame.jar` because the directory already says which target it is.

The mobile artifact is ProGuard-preverified because a real MIDP device expects it. The
desktop artifact is plain `javac` output because ProGuard 6.2.2 emits a `StackMapTable`
that a desktop JVM rejects outright — see the header of `tools/build_fullgame_desktop.sh`.

The two builds differ in exactly one thing, the compiled-in value of
`ShopStartingCapital.VALUE`. It has to be compiled in rather than passed as a system
property, because a real MIDP device cannot be given `-D` flags and FreeJ2ME's own
`restartApp()` puts them after `-jar` where the JVM ignores them. `tools/build_public.sh`
therefore compiles from a copy of the sources with that one file rewritten; nothing under
`src/` is modified, so the development and test builds keep the large capital.

Note that the dated audit material under `reports/` and `docs/rebuild/` still refers to
the previous `dist/java-me-fullgame/` path. Those are records of what was true at the
time and were deliberately left alone.

## Level-Up Offer Mix Fix (2026-10-17)

Reported as "damage is too harsh because there is no character upgrade system". The upgrade system
existed; its offer mix was broken.

`chooseOfferType` rolled `int r = rand(20)` and then tested `r < 20`, which is true for every value
`rand(20)` can return. Both leading slots were therefore pinned to `OFFER_SKILL` on every level-up
while the skill was unacquired, meaning they offered the same skill twice and never reached the
branches below. Slots 2 and 3 were weapon/item only. Character stat upgrades were therefore
structurally near-unreachable: a player collected weapons but had no route to Max HP or ATK, which
is exactly the survivability curve that makes an early stage feel unfair.

The fix guarantees the one-time skill on slot 0 and weights the rest weapon 50%, item 25%, stat
25%. Slot 0 no longer duplicates the skill. `RunWorldOfferMixTest` pins all of it, including the
regression that matters: with the skill taken, every slot must be able to roll a stat, and one
level-up may never offer the skill twice.

Observed in the desktop emulator after the fix: max HP moved 70 -> 77 across a level-up, which is
the `Max HP +10%` stat landing.

## Arena Hutan Sawit Map Art (2026-10-17)

The stage background is now the reference scene rather than a repeating floor tile.

The reference is one composed 3:2 picture, so it cannot tile: repeating any single crop of it would
destroy the composition. `tools/generate_palm_forest_map.py` therefore crops the 1536x1024 source
to 4:3 (cropping, not scaling, so no trunk or flame is bent), resizes to the 320x240 playfield, and
writes both the whole arena and a 4x4 grid of 80x60 parts under
`assets/fullgame/stages/palm_forest_parts/`.

`StageDef` gained `mapCols`/`mapRows`. When both are above zero, `AssetLibrary.prepareStage` loads
that grid and `RunWorld.drawBackground` draws each part once at its grid position instead of
repeating one tile; parts outside the camera are skipped. Stages that do not set the fields keep the
original single-tile path untouched, so no other stage changes. Parts are released on stage switch
by the same rule that releases enemy and boss sprites.

Verified by comparing the reconstructed in-game canvas against the source: 93% of pixels within a
tolerance of 60, mean absolute difference 13.9 per pixel, and the residue is the player, the Biji
Sawit minions, the HP bar and the HUD rather than background.

## RMS Write Path Defect (2026-10-17)

Found while bringing the shop up: nothing the game saved had ever been stored.

`SaveStore.RmsRecordStorage.write` called `store.setRecord(...)` on its own. Under the MIDP
specification `setRecord` only replaces a record whose id the store has already issued and throws
`InvalidRecordIDException` otherwise; `createRecord` is what establishes an id. On a fresh install
every write therefore failed. Because `SaveStore` only adopts a balance after its write reports
success, the visible symptom was a coin balance permanently stuck at zero, and stage unlocks, the
selected character and shop passives were silently lost the same way.

The fix falls back to `addRecord` when `setRecord` reports the id is unknown. `addRecord` is core
MIDP 1.0, so it works on runtimes with no `createRecord`, and on a fresh store it is issued the
first free id, which is the id the adapter already reads back.

Verified on FreeJ2ME-Plus by reading the emulator's own RMS file: the 56-byte record now exists,
carries the balance, and carries per-character passive levels.

**Not unit tested, and why.** `RecordStore` in the pinned stub has a private constructor, so a
device-like double cannot be subclassed, and `Unsafe.allocateInstance` on the class itself yields an
instance whose every method throws. The commit path therefore cannot be exercised offline; it is
covered by the fake-storage coin tests plus the emulator run above. `SaveStoreRmsPathTest` records
this limitation.

## Shop and Stackable Skills (2026-10-17)

**Skill stacking.** The skill was a one-shot boolean, so a second skill offer could never appear and
the slot was wasted once taken. It is now a stack count up to `SKILL_MAX_STACKS` (5). Burst skills
scale damage and radius, duration skills scale their window, and Undying's shield scales its value.
`skillAcquired` stays the authoritative "has a skill" flag; `skillPower()` floors at 1 so a world
whose flag was set without a stack count behaves as before. Pinned by `RunWorldSkillStackingTest`.

**Shop.** A Shop row sits between the roster and Back on the character screen and buys permanent
passives for the selected character. Passives are read once at the start of a run and applied
underneath the run's items, so a purchase is never diluted by which items happened to drop.

The eight passives were audited against `RunWorld` rather than invented; each drives a field the run
already computes, or fills one that existed but was never set:

| Passive | Effect | Notes |
|---|---|---|
| Vitality | Max HP +10%/level | shares `baseMaxHp` with the level-up HP stat |
| Might | ATK +8%/level | applied to outgoing damage |
| Guard | Damage taken -6%/level | fills `defensePercent`, which `damagePlayer` read but nothing set: damage reduction was dead code |
| Swift | Move speed +10%/level |  |
| Precision | Crit +4%/level |  |
| Insight | EXP gain +12%/level |  |
| Fortune | Coin drop rate +15%/level | shortens the enemy drop interval, capped so it can never become a guaranteed drop; no item covers coin rate at all |
| Tempo | Haste +6%/level |  |

Every passive runs 0..10 levels (`PASSIVE_MAX_LEVEL`), for every stat in the shop. Levels are
per character rather than global: a purchase strengthens the selected character only, and the
other roster entries keep their own vectors. A full 10/10 clear-out of all eight rows costs
5,795,500, which the starting capital covers with change.

**Storage.** Passive levels are nibbles in bytes 10..29, inside the region that was already reserved
for the completion map and never occupied, so `DATA_LEN` stays 56 and no existing save has to
migrate. Nibbles rather than tighter 3-bit fields because a 3-bit field straddles byte boundaries,
and a single-byte accessor would silently lose the bits that spilled. Levels above
`PASSIVE_MAX_LEVEL` are refused by the storage layer itself, so the whole pipeline agrees on the
0..10 range. Verified against the emulator's own RMS bytes: the five characters carry five distinct
level vectors with no overlap.

**Starting capital.** `FullGameCanvas.SHOP_STARTING_CAPITAL` (9999999) is applied as a top-up from
`FullGameMIDlet.ensureStartingCapital()`. It is deliberately kept out of `SaveStore`, which owns the
real balance semantics and whose tests pin a fresh install to zero. Deleting the constant and that
one method restores a zero balance.

## Boss Combat Fixes (2026-10-17)

Three boss-side defects were reported (HP, hitbox, and player attacks not going toward the boss)
and fixed together, since all three share the same root: the boss was second-class in code written
around regular enemies.

**1. HP no longer pinned at 20000.** `spawnBossById` computed boss HP with the enemy scaling formula
but then clamped it with `min(20000, ...)`. Since any catalog boss above roughly 1000 HP reaches that
cap by the 10:00 gate, every boss in the game had exactly 20000 HP — Halloween Bae's verified 72000
was crushed to 20000, and Fubuzilla's 15000 was inflated to match. The clamp is now
`scaledBossHp()`, the same power formula under a boss-specific ceiling that has been 400000 since
2026-10-20 (it first shared `scaledEnemyHp`'s 100000, but the wiki figures — Goriela's 180000,
WoWo's 110000 — outrank that cap and would have been clamped flat; enemies keep 100000). At the
normal 10:00/20:00 gates the scaling factors are still zero, so documented bosses surface their
catalog values; the ceiling only shapes long Endless repeats. Pinned by
`RunWorldBossEncounterTest.testBossHpIsNotPinnedByACap` (a 72000 boss survives spawn above 20000)
and `RunWorldScalingTest[6]` (clamped to 400000, exact formula below the cap).

**2. Hitbox follows the sprite.** Every boss shared a fixed 42x42 box regardless of art, while
actual frame sizes run from 22x33 (Nodoka) to 128x69 (Spiderchama/EldrichHaachama) and 99x116
(Pekodam). A large boss's body sat mostly outside its own hitbox, so shots that visibly connected
missed and contact damage landed off-sprite. `BossDef` now carries `collW`/`collH` matched to each
boss's art directory frame size; `bossWidth()`/`bossHeight()` read them (42x42 remains the fallback).
`RunWorldBossArtTest` pins every pair against the actual PNG IHDR dimensions, so resizing art
without updating the catalog fails loudly.

**3. Attacks can be aimed at the boss.** Targeting used `nearestEnemy()`, which only scans the
enemy pool — the boss was never a target. During a boss fight, aimed and homing weapons tracked the
adds around it and, with no adds left, flew off in the player's facing direction instead of at the
boss. `nearestTarget()` now compares the boss's center against the nearest add and returns the new
`TARGET_BOSS` sentinel (`-2`, with `-1` still meaning none); `fireWeapon` resolves the initial aim
to the boss box, and `updateShots` steers `PATTERN_HOMING` shots at that same box so a homing shot
converges on the body the hit test uses. A closer add still outranks the boss. Pinned by
`RunWorldBossEncounterTest.testAimedShotsTrackTheBoss`.

## Magnet and Pickup Fixes (2026-10-17)

Reported as "the pickup system works only when I touch the object; the magnet does not work".
Two defects, both confirmed in `updatePickups`.

**1. The pull phase never existed.** The attraction radius and the collection radius were the same
22 px circle, so a pickup was collected on the very tick it entered the aura and the pull code
never showed: pickups behaved exactly as if they had to be walked into, which is the reported
symptom. The two radii are separate now — `MAGNET_BASE_RADIUS` (56 px, scaled by
`pickupPercentBonus` as before) pulls at `MAGNET_PULL_SPEED` (3 px/tick), `PICKUP_TOUCH_RADIUS`
(16 px) collects. A pulled pickup re-measures after each step, so one that arrives this tick is
collected this tick rather than one tick late. The base radius also had to grow: at 22 px between
centers, with a 24 px player and a 12 px pickup, the old aura ended where contact began, so even a
working pull would have been invisible.

**2. The EXP Magnet pickup did nothing.** Pickup type 3 (dropped 1/3000, documented as "all EXP
and HoloCoins in large area vacuumed") was drawn in from anywhere and then silently discarded:
`collectPickup` had no branch for it. Collecting one now opens a vacuum window
(`VACUUM_DURATION_MS`, 3000 ms) in which every pickup on the field is pulled in at the fast 8
px/tick and collects on the touch radius as it arrives. The window is time-boxed and cleared by
`reset()`, so it cannot leak between runs.

Pinned by `RunWorldPickupMagnetTest` (23 checks): the aura pulls without collecting, the aura has
an edge, the touch radius collects, a pulled pickup converges over several ticks rather than
vanishing on entry or oscillating, the bonus widens the aura but not the touch radius, the magnet
pickup is pulled from anywhere, it arms a vacuum, the vacuum pulls and collects far pickups, the
vacuum expires, and a new run clears it.

## Collab Ingredient Rotation and Skill Row (2026-10-17)

Reported as a wave of five collab questions: collab obtainable (confirmed), but after crafting BL
Fujoshi the level-up roll still offered BL Book and its partner Psycho Axe; re-confirm the weapon
slot decrease; the function of the six boxes under the weapon row is doubtful; and combining a
skill with a weapon is unconfirmed.

**1. Consumed collab components kept being offered (defect, fixed).** `applyOffer` consumes the two
components, but nothing filtered them from the fresh-pick roll afterwards. With the full wiki CSV
(30 recipes), every basic weapon is a component of at least one recipe — BL Book feeds three (BL
Fujoshi, Frozen Sea, Legendary Sausage) and Psycho Axe feeds three (BL Fujoshi, Blood Lust,
Crescent Bardiche) — so both reappeared on the very next level-ups. The reference rule is absolute
(HoloCure wiki, Collab): "Weapons that have been used in a collab cannot appear again in the
rotation when leveling up or other means". `feedsOwnedCollab` now fires as soon as ANY owned collab
lists the weapon: the sibling recipes become unobtainable for the run, exactly as in the reference.
An owned collab is separately skipped in the roll (`hasCollab`), so it can never be re-offered
either.

**2. Weapon slot decrease after a collab — confirmed correct.** `applyOffer` removes
`prerequisiteA` and `prerequisiteB` and adds the collab: two weapons become one, so the weapon row
loses one lit box and a slot frees up — matching the reference ("This will open up a weapon
inventory slot"). Pinned by `RunWorldCollabTest` [7].

**3. The six skill boxes under the weapon row had five dead cells (defect, fixed).** Only cell 0
ever lit (`skillAcquired`), so the row had no visible function. The row now reports what the stacks
bought: `skillSlotOwned` lights cell 0 for the skill itself (usable even on a run whose flag was set
without stacks, since `skillPower()` floors at 1), cells 1..4 at stacks 2 up to
`SKILL_MAX_STACKS` (5), and the last cell as the MAX badge that completes the row at the cap. Note
the second row is the skill row, not items — item slots have no boxes in the HUD; the inventory
only shows as `I <count>` in the status line.

**4. Skill combined with weapon — no such mechanic exists (clarified).** Collabs combine TWO
WEAPONS: when both components reach max level, the collab appears as a level-up offer, consumes
both and adds one collab weapon. Skills are separate: activated on their own key at a 15 s
cooldown, with stacks raising `skillPower()` (damage and radius, or duration for Hunter's Instinct
and Second Flame, shield for Undying). The five super collab rows in the CSV (collab + item, e.g.
True Infinite BL Works = BL Fujoshi + Hope Soda) can never trigger: their `weapon_b` is an item id,
which `weaponLevelById` resolves to 0, and this build has no super collab system.

Pinned by `RunWorldCollabTest` (56 checks: [9] consumed components are filtered even while sibling
recipes still list them, [10] nothing is filtered before any collab is owned, [11] the filter spans
only the consumed components of the owned collab) and `RunWorldSkillStackingTest` (47 checks, [9]
the skill row cells report the stack level). Full suite: 32 suites, 3030 checks, all passing. (The
skill row and the stacking test were superseded by the Global Shop wave below.)

## Global Shop, Passive Skill and Character Browser (2026-10-17)

A restructuring wave with three asks: move the shop out of the character menu into the main menu
and make it global; replace the stackable level-up skill with a one-time "Skill Pasif" purchase
that auto-casts; and turn the character screen into a plain browser whose click only selects and
shows the status panel.

**1. The shop is global and lives on the main menu.** The main menu grew from six entries to seven
(Play, Game Mode, Characters, Stage Select, Shop, Settings, Exit; `MAIN_SHOP = 4`). The passives
are now one shared vector applied to every character, sold 0..5 instead of the old per-character
0..10 (`PASSIVE_MAX_LEVEL = 5`; a full shop costs 2,201 coins). The character menu lost its Shop
row entirely, and the shop's row highlight now follows `menuIndex` — previously the drawn highlight
read a `shopIndex` field that was never synced, so it was stuck on row 0.

**2. Skill Pasif: the skill as a permanent 0/1 upgrade.** The shop's `PASSIVES.length + 2`-th row
sells the skill for a fixed `SKILL_PASSIVE_COST` (1,000 coins). Once bought, every character starts
every run already holding their skill (`reset()` reads `save.skillPassiveOwned()`), and
`updatePlayer` casts it automatically from the first tick and then every `SKILL_COOLDOWN_MS`
(15,000 ms), at its fixed base power — no key, no slot, no HUD row. `OFFER_SKILL` was deleted
whole: the level-up roll can no longer produce the skill in any slot, slot 0 rolls like every other
slot, and stat upgrades keep their 25% share per slot. The old stack system (`skillStacks`,
`skillPower`, `skillSlotOwned`, `SKILL_MAX_STACKS`) and the `KEY_SKILL`/'8' binding plus its
`skillPressed` latch were removed; `update` and `updatePlayer` lost their skill parameters.
`RunWorldSkillPassiveTest` replaces `RunWorldSkillStackingTest` and pins the removal itself: the
stack field and accessors must not exist, effects are the fixed base, and the cadence is exactly
15 s. The purchase survives a run deliberately — like the coin balance it is meta-permanent
(`RunWorldLifecycleIntegrationTest` [11]).

**3. Character screen is a browser, not a launcher.** The roster's single trailing row is now
Back. Clicking a character selects it, persists it, pins its status panel on the right and stays on
the screen; Play on the main menu jumps straight to Stage Select with whatever was picked, and the
stage-select footer names the character ("Playing: ..."). Game Mode also continues to Stage Select
directly instead of detouring through the roster.

**4. Save layout v2 with an automatic fold.** Passive bytes moved from the per-character nibble
field (bytes 10..29, 5 characters x 8 passives x 0..10) to the global vector in bytes 10..3,
byte 14 holds the Skill Pasif flag, bytes 15..45 stay reserved and byte 46 carries
`LAYOUT_VERSION = 2`. `migrateShopLayout()` runs inside `load()` whenever the stamp is absent: it
folds each stat to the highest level any old character had bought, clamped to 0..5, clears bytes
14..29 (including the old half-used skill stack) and stamps the version, leaving completion,
settings and coins untouched. The record stays 56 bytes and the store keeps its historical name.
`CharacterShopTest` was rewritten for the global layout (342 checks, including the fold from a
hand-built old record, the version stamp, and the Skill Pasif transaction) and `UiStateMachineTest`
for the new menu map. Full suite: 32 suites, 2849 checks, all passing.

## New Main Menu, Three-Tab Toko and Upgrade Wiring (2026-10-17)

A wave with three asks: rebuild the main menu around the seven requested entries, build the Toko
as a real three-tab shop priced from the HoloCure wiki, and wire every upgrade the save can
already express — locking the rest as visible data.

**1. Main menu, seven entries, old palette.** The menu keeps its palette and style but now reads,
top to bottom: Papan Peringkat, Pencapaian, Toko, Main, Holo House, Pengaturan, Credits
(`MAIN_LEADERBOARD..MAIN_CREDITS`, `MAIN_ITEM_COUNT = 7`, title HOLOJAVA). The first, second and
fourth entries are shut for now: they open a shared placeholder screen (`LOCKED`, screen 13) that
carries its own title. Credits got a real page (screen 14). Row pitch dropped to 18 px so the
seventh entry fits above the hint line. Exit left the menu entirely — it is now row 4 of
Pengaturan, which grew to five rows (Hardcore, Audio, Controls, Exit, Back); both Back paths
(settings row and Left) restore the cursor to Pengaturan.

**2. Play flow: Main to Mode to Karakter to Stage.** Main opens Game Mode, picking a mode opens
Karakter, clicking a character persists it and continues to Stage Select, and every back step
walks one level up (Stage to Karakter to Mode to Menu), restoring the parent's cursor. The
character screen is therefore a step again rather than a browser, and the stage screen backs to
Karakter instead of sideways. Pause/results return to Main.

**3. Toko: submenu, three tabs, wiki prices.** Toko opens a submenu (Beli Upgrade / Armory /
Keluar). Beli Upgrade is a three-tab page (Ability, Stat, Lainnya) over
`ContentCatalog.UPGRADES[34]` — 12 Ability, 14 Stat, 8 Lainnya — every row drawn with its wiki
price and cap, a detail panel below, and a trailing Kembali row; L/R switches tabs. Fifteen rows
have no gameplay yet (Growth, Eliminate, Stamps, Kustomisasi, Support, Temuan Material, Surat
Fans, Enchantments, Fandom, Special CDR, Skill Up, Food Drops Up, Enhancement Rate Up, Supers Ban,
G Rank Off) and are marked `locked`: shown with prices, refused on purchase. Prices are the wiki
tables verbatim (Special Attack 500, Reroll 2,500..25,000, Max HP Up 200..36,000, Money Gain Up
400..60,000, the limit rows free, …). Armory lists the weapon/item catalogue read-only with a
Kustomisasi-locked footer; Fire does nothing there. The Skill Pasif row is gone entirely: every
character's skill is free and always active.

**4. Save layout v3, refunds.** The eight-passive vector became 34 nibbles in bytes 10..26
(17 bytes), byte 46 stamps `LAYOUT_VERSION = 3`, record stays 56 bytes. `migrateShopLayout()`
now folds both older layouts — the v2 global eight-nibble vector and the v1 per-character vector
(bytes 10..29) — onto their target rows with the new caps, then clears bytes 10..45 (the Skill
Pasif flag byte included). `setUpgradeLevel` refuses out-of-range, over-cap, locked and
non-levelled rows; `refundValue()`/`refundAll()` credit every owned level back in one write.
`FullGameCanvas.SHOP_STARTING_CAPITAL` (9,999,999) is credited at boot as temporary test capital.

**5. What the shop can now actually do.** Special Attack gates `activateSpecial`; Reroll and
Tahan are bought charge-per-level (the free 10/5 bonuses are gone, so a fresh run shows four
plain offers); Hardcore is the settings flag; Collab Ban blocks the collab branch of `fillOffer`;
Weapon/Item Limit lower the loadout caps to `6 − level`; Marketing adds one extra fan per spawn
interval, still bound by `stage.maxEnemies`; the eight old passives carry their new names and
caps; Pick Up Range feeds `pickupPercentBonus`; Regeneration heals `1 + level` on one 5 s tick
(the regen item keeps its +2 on the same tick); Money Gain Up scales coin VALUE (+20%/level,
capped +200%) instead of the drop roll. Locked rows change nothing anywhere. `recomputeStats`
loops all 34 rows.

**6. Tests.** `CharacterShopTest` was rewritten for the 34-row catalog, the v3 layout, both
migrations, the locked/toggle/action refusals, the two-press Refund All flow, the canvas purchase
path and the stat wiring (980 checks). `UiStateMachineTest` covers the seven-entry menu, the new
flow, the Toko submenu, the placeholder pages and the Exit row; `RunWorldLifecycleIntegrationTest`
grants Special Attack instead of Skill Pasif (its skill and slow fixtures moved to characters
whose skill no longer masks the effect under always-on skills). Full suite: 32 suites,
3535 checks, all passing. Build: device and desktop JARs compiled after the wave.

## Runtime Findings (2026-10-17)

The desktop-emulator runtime was exercised for the first time. Three separate
blockers were found and resolved; only the third was in this project's own code
path. None of them relate to image sizes, and none were introduced by the new
stage.

**1. FreeJ2ME-Plus restarts forever on an encoding mismatch.**
`Mobile.restartApp()` builds its relaunch command with `-Dfile.encoding=` placed
*after* `-jar`, where the JVM treats it as an application argument rather than a
system property. The child therefore starts with the wrong encoding again, the
check fails again, and the emulator loops while the LCD panel stays blank.
Workaround: pass `-Dfile.encoding=ISO_8859_1` *before* `-jar`. This is an
upstream defect in `/home/antomay/Projects/tools/freej2me-plus`.

**2. The preverified JAR cannot be loaded by a desktop JVM.**
ProGuard 6.2.2 emits an `extended full_frame` (frame_type 255) with
`number_of_locals = 0` in at least one method. The class file specification
requires a full_frame to carry at least one local, so the host JVM rejects
`FullGameMIDlet.class` before any MIDlet code runs:

    java.lang.ClassFormatError: StackMapTable format error: bad type array size
        in method 'void me.holojava.fullgame.FullGameMIDlet.startApp()'

Class version 52 does not require a StackMapTable, and the type-inference
verifier accepts this code, so removing the attribute would be a valid repair.
`tools/build_fullgame_desktop.sh` avoids the problem instead by packaging the
plain `javac` output, which the existing desktop test suite already proves loads.
The preverified JAR from `tools/build_fullgame.sh` is still produced for real
MIDP targets; whether a real device's verifier tolerates the malformed frames is
**unverified** and should be treated as a risk until it is tested.

**3. Not a defect: assets are fine.** All new PNGs are 8-bit RGBA at the same
dimensions as assets already known to load, and the baseline build at HEAD
without this stage renders the same blank screen. A control run of the minimal
diagnostic MIDlet failed identically, which is what isolated the cause to the
emulator and to ProGuard rather than to game code.

Verified working: the game launches, menus render, movement, enemy spawning,
contact damage, EXP and level-up all work in FreeJ2ME-Plus on the desktop
emulator build. Audio is unavailable in this environment (`Tone Player: Can not
open line`) and remains untested.

## HoloCure Title Menu, FufuFafa and 19 Kucing (2026-10-18)

A presentation wave plus one roster wave: the main menu and splash were rebuilt after HoloCure's
title screen, the Hutan Sawit got a redrawn boss art, a second boss and a cat enemy, and A-chan
(the old splash leftover) was dropped. Entry order, palette and every wave-6 menu behaviour were
kept.

**1. HoloCure-style main menu.** The seven entries moved from a vertical list into a horizontal
icon bar — podium, trophy, bag, play, house, gear, list — on a 202 px baseline from x = 40, each
slot its icon width plus a 23 px gap (`MAIN_ITEMS` order unchanged: Papan Peringkat, Pencapaian,
Toko, Main, Holo House, Pengaturan, Credits). The selected icon lifts 3 px off the bar and bobs on
a 180 ms clock with a white underline beneath it; a gold label box (116x20 at x = 200, y = 206)
slides the entry name in at 25 ms per step, bottom right above the `SETUJU: 5/FIRE` hint
(y = 228). Behind it: the full `title_bg` collage, the `menu_cast` roster strip at y = 64, the
176x74 logo centred at x = 72 bobbing on a 600 ms clock with `HOLOJAVA` under it, and
`version 0.1` top right. Up/Down and L/R sweep the bar through one shared dispatch
(`updateMain(upEdge || leftEdge, downEdge || rightEdge, fireEdge)`), FIRE opens the entry exactly
as in wave 6, and Exit stays inside Pengaturan. All motion reads `clockMs`, recorded once per
frame from `update(long now)`, so headless tests freeze every effect at rest. The label box is
116 px wide because a screenshot review caught "Papan Peringkat" (~107 px) overflowing an earlier
70 px box.

**2. Splash.** The splash shares the title look: `title_bg`, the 300x126 logo at (10, 42) with a
small bob, `HOLOJAVA` centred at y = 172, and `PRESS 5 TO START` blinking on the half second.
TITLE_DEEP (10, 22, 49) fills in when the backdrop asset is missing. A-chan is gone:
`achanFrame()` and the twenty `achan_blink_*.png` frames were deleted, and the title fallback
became TITLE_DEEP.

**3. All title art pre-scaled.** MIDP cannot scale images at runtime, so
`tools/prepare_title_menu_assets.py` bakes every title asset at its final size: `title_bg`,
`menu_cast`, `menu_label` (116x20) and the seven `menu_icon_*` sprites.

**4. Arena Hutan Sawit roster.** Boss art WoWo was redrawn (64x64, 4 frames), a second boss
FufuFafa joined the stage (46x56, 4 frames, collision box 46x56, AI_SHOOTER, HP 12000 — retuned
2026-10-20 to 125000 with the wiki boss figures, ATK 48,
spawning 1200 s into the run, 600 s after WoWo), and the new enemy 19 Kucing patrols the palm
forest (14x32, 3 frames, hitbox 12x28, AI_CHASER, HP 40). `tools/prepare_hutan_sawit_cast.py` now
tight-crops every sprite through `getbbox()`; FufuFafa needed its own threshold (98, 30) because
its checkerboard backdrop sits at 113/173 rather than the lighter default, and its bob table is
3 frames. `tools/generate_palm_forest_sprites.py` no longer emits WoWo — the WoWo/leaf-plate/bark/
eye-glow code was removed and only BijiSawit regenerates from it (identical bytes).

**5. Tests and manifest.** `RunWorldBossArtTest` pins FufuFafa alongside WoWo; `ArenaHutanSawitTest`
pins the new enemy slot, both boss slots, the spawn schedule and the new art on disk. Full suite:
32 suites, 3576 checks, all passing. The derived-asset manifest now holds 179 rows (−20 A-chan,
+10 title UI) and `tools/verify_fullgame_build.py` checks 179.

**6. Emulator verification.** The desktop emulator must be launched with width-then-height LCD
arguments (`0 320 240 3`); the help text in `tools/build_fullgame_desktop.sh` was corrected.
Screenshots confirmed the splash, bar navigation, the Game Mode and Character Select pages, the
back steps between them, and a live Hutan Sawit run. Injecting single keystrokes through XTEST
proved flaky (occasional missed or duplicated edges against FreeJ2ME's held-key polling) — a
test-harness artifact, not a menu defect; the state machine itself is covered by the suites above.

## Menu Polish: Keluar Entry, Shop Confirmation and Layout Fixes (2026-10-18)

Small-fix wave requested right after the HoloCure menu landed:

**1. Main menu cleanup.** The `HOLOJAVA` wordmark under the logo and the `SETUJU: 5/FIRE` hint
are gone (the splash keeps its own `HOLOJAVA` / `PRESS 5 TO START`). The gold label box now draws
pre-rendered `menu_text_0..7` images — DejaVu Sans Bold at 10 px, baked by
`tools/prepare_title_menu_assets.py` — one notch below MIDP's smallest font, with the drawn
SIZE_SMALL string kept as the partial-build fallback. The bar gained its eighth entry, Keluar
(red-X frame `menu_icon_7`), which sets the same shutdown flags as the Pengaturan Exit row, and
`TITLE_BAR_X` moved 40 → 27 so the eight icons keep the old centred span (265 px).

**2. Pengaturan.** The page is now Audio / Help / Back. Two rows left it: **Controls**, whose
bindings were folded into Help so the two pages cannot disagree about what a key does, and the
note that read "Hardcore kini ada di Toko (tab Lainnya)". Only the note went — the Hardcore row
itself stays in Toko → Beli Upgrade → Lainnya and still toggles the bit `RunWorld` reads for
Endless Mode.

**3. Toko layout and flow.** The TOKO heading is centred and the coin balance moved to the
top-right of the title band on every Toko-family page (`drawCoin`). Beli Upgrade's three tabs
are now a list submenu: Toko → Beli Upgrade opens the new `SHOPTABS` screen (PILIH TAB:
Ability / Stat / Lainnya / Kembali); inside the purchase page L/R and the trailing Kembali row
both step back to it with the cursor restored on the tab just used, and the tab strip became a
read-only breadcrumb. Purchases ask first: the first FIRE on a levelled row arms `buyConfirm`
("Beli N Koin? FIRE: ya. Geser: batal."), the second buys; any movement cancels, maxed and
locked rows never arm, and Refund All keeps its own two-press flow.

**4. Tests and assets.** UiStateMachineTest [3]/[4]/[11]/[18]/[21] re-pin the eight-entry menu,
the four-row settings and the tab submenu; CharacterShopTest gained `upgradeBuy` (the two-press
path) and test [15] for the confirmation semantics. Full suite: 32 suites, 3602 checks, all
passing; the derived-asset manifest holds 188 rows (+1 icon, +8 baked labels) and
`tools/verify_fullgame_build.py` checks 188. Every change was screenshot-verified on a fresh
desktop-emulator instance — the XTEST edge flakiness seen before reappeared only on a stale
window and vanished after a restart.

## Shop Popup Confirmations and Five-Row List (2026-10-18)

Follow-up polish requested right after the Menu Polish wave:

**1. Tab strip removed.** The Beli Upgrade purchase page no longer draws the read-only
`[Ability] Stat Lainnya` breadcrumb at the top — tabs are chosen on the PILIH TAB submenu, so
the strip only repeated information the flow had already established. Rows now start at `y+56`
with a 26 px pitch (was `y+66` / 20 px), giving the list more air under the title band.

**2. Five rows per page, scrolling.** `SHOP_PAGE_SIZE` went 6 → 5, so the purchase list shows
five rows at a time and `keepShopRowVisible` scrolls the rest into view; the Armory catalogue
shares the same constant and stays in step. The detail panel (separator at `y+184`, row text
`y+190`/`y+206`, key hint `y+222`) is unchanged, and the hint is now always
`FIRE: beli  L/R / akhir: kembali ke tab` — it no longer swaps itself for the question.

**3. Confirmations are modal popups.** `buyConfirm` and `refundConfirm` no longer replace the
key-hint line; they draw a centred 248×96 `drawShopPopup` box that covers the list: the
question as heading ("Beli \<nama\>?" / "Refund All?"), the effect lines (level-up with
`-N Koin` or `gratis`, and the balance after: `Koin tersisa: …`, or `+N Koin (sisa …)` for a
refund), and a bold `FIRE: ya    Geser: batal` footer. While a popup is open it owns the input
(`updateUpgrades`): FIRE executes the armed action on the highlighted row, and any d-pad press
only closes the popup — no cursor move, no navigation — so cancelling with geser never steps
back to the tab submenu by accident. Locked and maxed rows still never arm a popup.

**4. Tests.** CharacterShopTest [11] and [15] re-pin the modal semantics: a d-pad press closes
the pending popup without leaving the purchase page or moving the cursor (new `upgradesBack`
helper drives L/R), and only the consecutive second FIRE spends. Full suite: 32 suites, 3607
checks, all passing; builds unchanged in shape (phone 1364256 B, desktop 1363210 B) and
`tools/verify_fullgame_build.py` passes with 188 assets.

**5. Runtime verification and one emulator finding.** Screenshot-verified on the desktop
emulator: the five-row list, the scrolled view, the popup rendering (name, level-up, cost,
balance-after, footer) and the geser-cancels-in-place behaviour. The purchase's second-press
execution is covered by the reflection-driven tests above; the emulator session itself hit an
input-injection failure that is on the emulator side, not in the game: freej2me-plus
`MobilePlatform.updateKeyState` releases the key bit with XOR instead of AND-NOT
(`keyState ^= mask`), so a release event that arrives without a matching tracked press flips a
zero bit to one and wedges that game-action bit permanently (seen as "FIRE is dead while the
d-pad still works"), and under Wayland the compositor intermittently steals X input focus from
the Xwayland window (keys silently go nowhere). Both were root-caused by reading the emulator
source and a live jstack (game loop and AWT event thread healthy, focus object inspected
directly); the game's own input state machine is unaffected and fully test-covered.

## Popup Idle-Tick Fix and Right-Key Repeat Fix (2026-10-18)

Two bugs reported right after the popup wave, both confirmed by reading the input path:

**1. The popup closed itself ~40 ms after opening.** `update()` calls `updateUpgrades(...)` on
every 40 ms tick, but the modal branch treated "no FIRE edge" as "d-pad pressed" and cleared
`buyConfirm`/`refundConfirm` — so an idle tick (no key at all) dismissed the popup before the
player could answer, and every later FIRE just re-armed it: purchases with a price could never
be completed, and the abandoned geser press leaked into normal back-navigation. The branch now
reacts to real edges only: FIRE executes the question, an actual d-pad press
(`up || down || left || right`) closes it in place, and an idle tick keeps it open.
CharacterShopTest [11] and [15] gained an `upgradesIdleTicks` helper that replays the real
loop (N all-false calls between arm and confirm) — the coverage gap that let this ship.

**2. Right scrolled every tick instead of every press.** `update()` never assigned
`rightWasDown = right` with the other edge latches (only `clearInputLatches` did), so
`rightEdge = right && !rightWasDown` fired on *every* tick a key press was held (~25/s) —
that is the "gulir ke kanan terlalu cepat" report, and it also made a single held geser
produce a second edge that navigated away after closing the popup. One line fixes it; with
the fix a 600 ms hold advances exactly one entry.

Verified end-to-end on the desktop emulator: the popup stayed open across a 3-second idle,
geser closed it in place on BELI UPGRADE (cursor unmoved, no tab-submenu jump), the two-press
purchase completed (Reroll Lv0→Lv1, 9999999→9997499), and a 600 ms Right hold moved the main
menu exactly one icon. Full suite: 32 suites, 3609 checks; phone JAR 1364288 B, desktop JAR
1363232 B, `verify_fullgame_build.py` passes.

## Gameplay HUD Rebuild and Level-Up Popup (2026-10-18)

A gameplay-UI wave requested as one list; all items are in and verified on the emulator.

**Top-left block.** The HP text and HP bar are untouched ("pertahankan ui Bar HP"). The
key-hint line ("5/FIRE/1 Attack 8 Skill 9 Special 0 Pause") is gone — its cache field went
with it. Under the HP bar sits the new **special bar** (66x6): full violet #AA7CFF while the
bought special is still held, a dark #3A2C5C track once it is locked or spent. Violet is the
recommendation because it reads as an "ultimate meter" by convention and never collides with
the HP pair (green/red) or the EXP gold. The weapon tray moved down to sit under it, and a
second identical six-cell tray was added below: the **item/skill row**, showing the passive
skills (items) collected this run — items fill `itemSlots` in pickup order, so the same
prefix rule as weapons marks the owned cells.

**Top-centre.** The run timer moved here, bold and centred like the original game.

**Top-right run card**, in the requested order: "Lv n", then a gold **EXP bar** (#FFCE4A —
the genre convention, the only warm bar on the HUD, black-bordered for any sky), then the
"Status run" label, "Coins n  Kill n", and the wave line "W n | stage". The wave counter is
`waveNumber()`, incremented in `spawnWave` and floored at 1. The Time-mode Fans progress line
stays, parked below the wave line.

**Level-up popup.** `FullGameCanvas.drawLevelUp` is now a popup, not a black page: a 50%
dithered dim (MIDP has no alpha, so a 2x2 checkerboard of near-black reads as the requested
"background agak transparan") over the frozen run, with a panel in the menu trio — #A8E7FC
panel, #246C91 ink border/text, #42BFEF accent selection bar (ink on accent keeps the 4.27:1
contrast the menus already chose). Collab offers keep a deeper gold when idle. The offer list
lives in a five-row window that scrolls inside the box (`levelUpScroll`, reset when the popup
closes), so Reroll/Tahan rows can never stretch the popup; ink triangles mark more rows
above/below.

The pickup-magnet suite's source-reading `read()` helper grew a dynamic buffer: the fixed
16 KB cap clipped the very `reset()` body it pins once this wave pushed the file past it.

Verified end-to-end on the desktop emulator: full run on Grassy Plains with the new HUD
(special bar violet, timer centred, gold EXP bar filling at Lv 2, item row marking Sake the
moment it was taken), the popup stayed open across a long idle, d-pad moved the selection in
place, and FIRE applied the offer and closed it. Full suite: 32 suites, 3609 checks; phone
JAR 1364921 B, desktop JAR 1364083 B, `verify_fullgame_build.py` passes (188 assets).

## Per-Stage BGM as MIDI (2026-10-18)

Every stage now plays its own looping background track during a run. The sources are the
unpacked OGGs in `HoloCure_Unpacked/sounds/bgm` (14 files, ~171 MB total), which no J2ME jar
could ever carry — so each track was transcribed to **MIDI** instead: ten OGGs became
10 files under `assets/fullgame/audio/`, sized by the owner's 2026-10-18 policy — **tens of
KB per track, hard cap 60 KB** (the first pass over-shrunk to 3–14 KB and was re-transcribed).
The transcription used Spotify's **basic-pitch** (already present at
`/home/antomay/Projects/_tools/basic-pitch/.venv311`, Python 3.11), whose CLI accepts the OGGs
directly; each result was post-processed with pretty_midi/mido: notes split by register into
three GM Level-1 programs (below G2 → Electric Bass 33, G2–B4 → Electric Piano 4, C5 up →
Lead square 80) and flattened to MIDI type 0 for maximum phone-synth compatibility.

**Stage-to-track table** (the one place it lives: `BgmAudio.musicFor(stageIndex)`; swapping a
track is a one-line change): stage1 Grassy Plains → suspect, stage2 Holo Office → candygoround,
stage3 Halloween Castle → halloweennight, stage4 Gelora Bung Yagoo → identity, stage5 Fantasy
Island → fantasykiseki, the four hard variants in order → adtruck / stardust / mythortreat /
bokuragakurukara, time1 Concert Stage → concert. **Arena Hutan Sawit (stage6) is deliberately
silent** — it is this project's own stage, has no source track, and was requested without
music.

**The pairing is verified, not guessed** (first pass guessed 6 of 10 wrong). Three sources
agree: the wiki's Soundtrack page (Suspect = Stage 1, Shijoshugi Adtruck = Stage 1 Hard,
Candy-Go-Round = Stage 2, Stardust Song = Stage 2 Hard, Koyoi wa Halloween Night = Stage 3,
Myth or Treat = Stage 3 Hard, id:entity voices = Stage 4, Asuiro ClearSky = Time Stage 1);
the game's own credits (`Song: …` strings, 14 songs ↔ 14 OGG names 1:1 — including
`bokuragakurukara` = "All Aboard! viv:ID CRUISE ~Bokura ga kuru kara~" and `fantasykiseki` =
"Interact Fantasia + Kiseki Knot"); and the room names in `Rooms_Detail.txt`
(rm_GrassPlains_Night, rm_HoloOffice_SunSet, rm_IDArena_Night …). Stage 4 Hard →
bokuragakurukara is the one elimination pairing (every other song is pinned by name or wiki).
Not stage music: SSS is the menu theme (Shiny Smily Story), gopeko is Ochame Kinou (Usada
Casino race), casino is PEKORANDOMBRAIN — those MIDIs were dropped from the jar. Unused
stage-grade pool for later: holoHouse, intro, gameover, gamewin.

**Menu theme (splash → Main Menu).** The pre-game screens — splash, Main Menu and every
sub-menu — now loop their own track, a transcription of `bgm_SSS.ogg` (**Shiny Smily Story**,
the original game's menu BGM, 19 KB as MIDI). Wiring is one hook in `FullGameCanvas.render()`:
every non-GAME screen calls `bgm().playMenu()` except the results page, which keeps its
silence; `resumeApplication()` follows the same rule and re-reads the saved Audio flag first.
The Pengaturan toggle silences it like any other track.

**Implementation.** `me.holojava.fullgame.BgmAudio` loads the resource with
`getResourceAsStream`, wraps it in a `ByteArrayInputStream`, and plays it through MMAPI
(`Manager.createPlayer(in, "audio/midi")`, `setLoopCount(-1)` — the spec `LOOP_CONTINUOUSLY`
value, since the pinned microemu API stub declares `setLoopCount` but omits the constant).
FreeJ2ME-Plus routes `audio/midi` to its MIDIPlayer (sequencer + soft synth); real phones
carry their own GM synth. Hooks: `startRun()` plays the stage track, `pauseApplication()`
stops it, `resumeApplication()` restarts it when a run is still on screen, and `render()`
stops it on every non-GAME screen (results, menus, splash). The in-game pause menu keeps the
music, as in the original. Every MMAPI touch is wrapped in `catch (Throwable)`: a device
without MMAPI, a corrupt stream, or a decode failure degrades to silence, never a crash.
There is no volume UI yet; the track plays at device default.

**Gate and verification.** New suite `BgmStageMusicTest` (33 suites) pins the
contract: every stage except stage6 maps under `assets/fullgame/audio`, each file exists with
an `MThd` header, the menu theme is a real MIDI too, and the budget holds (≤60 KB per track,
≤600 KB for the set — the owner's 2026-10-18 policy) so the jar
cannot quietly grow back toward audio bloat; `verify_fullgame_build.py` now also requires
every `.mid` on disk to be present in the JAR. On the emulator the pipeline was proven with
temporary trace prints removed again before commit: lookup found, 9091 bytes read,
`JavaxPlatformPlayer` created, `start()` reached **state 400 (Player.STARTED)**; a standalone
probe parsed the same file through the JDK sound stack (147.27 s, matching source) and the
machine's default soundbank carries all 129 GM instruments, so the output is audible. Note
for future debugging: FreeJ2ME-Plus holds an HDMI ALSA line warm from boot (its synth pool),
so `/proc/asound` state cannot isolate the game's player — the MMAPI state trace is the
reliable signal. Two quirks worth knowing: alt-tabbing the emulator fires
`pauseApp`/`resumeApplication`, which restarts the track from the top; and the "music" is a
transcription — keyboard/lead timbres over the original arrangement, which is exactly the
trade the size budget demanded.

## HUD, Audio Toggle and Drop Corrections (2026-10-18)

Second feedback pass over the in-game UI and the drop economy:

- **HUD text is white.** Every gameplay HUD string (HP, timer, Lv, Status run, Coins/Kill,
  wave, Fans) draws in white instead of black; bar borders keep their black outlines.
- **EXP bar wears the original blue.** Fill `#5EDDEE` sampled from the unpacked
  `hud_expbar_anim` art, track `#183684` from `hud_expbar_bg` — the source game's own bar
  palette rather than the previous XP-gold recommendation.
- **Special bar: gold, EXP-width.** Fill moved to `#FFCE4A` (the EXP bar's old gold) and
  widened 66 → 76 px so `SPECIAL_BAR_W == EXP_BAR_W`; height and placement unchanged.
- **LEVEL UP popup has no full-screen dim.** `dimBackdrop` was deleted; only the light-blue
  panel floats over the frozen run. The pause screen keeps its full-page treatment.
- **Pengaturan → Audio is live.** The row reads `Audio: ON/OFF`, flips a persisted flag and
  drives `BgmAudio.setEnabled`: OFF stops the track immediately, ON applies on the next
  play, and `startRun()`/`resumeApplication()` re-sync the flag from the save on every
  entry. Storage is SaveStore byte 56 with inverted encoding (0 = ON), so every pre-audio
  save starts with music on; `DATA_LEN` 56 → 57 is the documented growth migration and no
  record is rewritten on load.
- **Food Drops Up is implemented.** The row left the locked set (15 → 14 locked rows):
  `EFFECT_FOOD_DROP` feeds `RunWorld.foodDropBonus` (+4%/level, 5 levels = +20%), which
  shrinks the food roll divisor `200 * 100 / (100 + bonus)` — 1-in-200 at zero levels,
  1-in-166 at the cap. The heal amount itself is unchanged.
- **Ground food has real art.** The type-2 pickup draws `spr_OkayuOnigiri_0.png` (16×14,
  copied verbatim from `HoloCure_Unpacked/sprites/spr_OkayuOnigiri/`), centred over the
  12 px pickup box; the red square stays as the load-failure fallback. Manifest rows
  188 → 189 and `verify_fullgame_build.py`'s expected count follows.

Audit findings behind the rate questions: the level-up stat pool reads no shop row — the
`Max HP +10%` offer frequency is the fixed weight (2 of 18) in `fillOffer`, so buying
"Max HP Up" cannot raise its appearance rate; and the full drop roll table was re-checked
in place: EXP always drops, coins per-enemy 1-in-N (Money Gain Up scales value only), food
1-in-200 (now scaled by Food Drops Up), weapon upgrade 1-in-1301, EXP Magnet 1-in-3001,
boss = big EXP plus five coins.

Gate: 33/33 suites, 3663 checks; both builds PASS (phone JAR 1426414 B manifest-verified,
desktop JAR 1425508 B). Emulator smoke on Grassy Plains: HUD colours pixel-sampled from a
screenshot (EXP track `#183684`, special `#FFCE4A`, white text), the LEVEL UP panel showed
without any dim layer, and the run levelled through several popup cycles with no errors.

## Completed in Existing Project Stage

- Phase 1 reports: asset manifest, symbol classification, cross-reference, phase backlog.
- Detailed room/object exports.
- Candidate gameplay object and asset identification in evidence-only form.

## Completed in Broad-Reconstruction Documentation Milestone

1. Updated `Roadmap.md` for the Phase 0–8 trajectory.
2. Reconciled broad reconstruction as the long-term goal without claiming parity with HoloCure.
3. Created `docs/RECONSTRUCTION_SCOPE.md`.
4. Created `docs/SYSTEM_CATALOG.md` (archived 2026-10-20 at `docs/archive/SYSTEM_CATALOG.md`).
5. Created `docs/CONTENT_COVERAGE.md` (archived 2026-10-20 at `docs/archive/CONTENT_COVERAGE.md`).
6. Reaffirmed independent Java gameplay design and 320×240 internal resolution.
7. Recorded asset licensing and redistribution as a release gate.
8. Preserved existing architecture, asset-loader, and test planning documents.

No Java, Gradle, libGDX, build file, source code, test code, or executable asset was changed or introduced by this milestone.


## Completed in Java ME/J2ME Compatibility and Toolchain Review

1. Inspected the Debian environment and confirmed no Java JDK, JRE, Ant, Gradle, Maven, or preverification tool is installed.
2. Reviewed available package metadata: Debian lists OpenJDK 21 and ProGuard tools, but no Java ME SDK or preverification tool.
3. Documented the compatibility tiers, MIDP/CLDC baseline, display scaling, key coding, image/audio constraints, and toolchain blocker.
4. Retained the 320×240 logical resolution and broad-reconstruction scope.
5. Did not install software, use privileged system changes, create build files, or create gameplay.

## Blockers

1. Candidate assets are unverified for redistribution — still the Phase 8 release gate.
2. No original-game formulas have been recovered (by design since the strategic pivot: YYC decompilation is deferred, not pending).
3. Original room renders were not exported.
4. Physical-device runtime behavior remains untested. The desktop emulator (FreeJ2ME-Plus) now exercises menus, combat, audio and saves in play sessions, but pointer/touch, Nokia and other device APIs, physical-screen scaling, and long-term device stability are not established.
5. Gameplay implementation itself is no longer a gate — it was explicitly approved and shipped. What remains before any public distribution is the asset redistribution review.

## Licensing and Asset Risk

Selected assets are candidate paths only. Their textures, sounds, and other original game files may be protected by copyright or separately licensed. They should remain local unless redistribution permission is documented. Any final public build must pass the licensing review defined for Phase 8.

## Simplified Phase 2 and Toolchain Direction

> Historical (2026-10-13): the three blockers listed below have since been cleared — OpenJDK 21
> installed, CLDC 1.1 / MIDP 2.0 stubs pinned in `deps/java-me/`, ProGuard 6.2.2 confirmed — and
> Phase 3+ followed. The compatibility assumptions (Canvas-only rendering, key-first input, no
> Nokia vendor APIs) still govern the code. Kept as the record of that decision.

Phase 2 remains documentation and readiness only. The active simplified target is J2ME Loader on Android, with Nokia support as a secondary goal.

Core assumptions:
- MIDP 2.0/LCDUI baseline is treated as available on both target classes until conflicting evidence is found.
- Render only through `Canvas`, `Graphics`, `Image`, `Display`, and `Displayable`.
- Internal viewport is 320×240, scaled or letterboxed without altering logical coordinates.
- Input is primarily game-action key mapping; pointer and audio are optional with fallback no-op.
- No Nokia vendor API (`com.nokia.mid.ui`, `DeviceControl`, `Sound`, `DirectGraphics`) until a concrete feature requires it.
- No per-model API research except for blockers that stop compilation or prototyping.

Three real blockers:
1. A Java build HOST is still unavailable (`java`/`javac` are not installed); OpenJDK 21 is the local candidate, with optional rootless Temurin JDK 8 escape hatch.
2. No approved CLDC 1.1/MIDP 2.0 API class library has been downloaded, licensed, checksummed, and version-pinned.
3. ProGuard 6.2.2, or an equivalent direct classfile preverification path, must be semantically confirmed for the actual diagnostic class input.

If those blockers are cleared, the pragmatic next sequence is:
1. Compile a one-screen `MIDlet` with `Canvas`, `Display`, `Image`, and static text.
2. Preverify using the selected single toolchain.
3. Package JAR/JAD and launch only in J2ME Loader as an emulator smoke test.
4. Record discrepancies and preserve fallback routes documented here.



Phase 2 diagnostic validation is complete as of 2026-10-13. See `Phase 2 Closure (2026-10-13)`. The broad reconstruction and 320×240 resolution are unchanged. Phase 3 local prototype work is now explicitly authorized and implemented; compile/preverification/packaging are complete, while runtime J2ME Loader gameplay testing and broader phase closure remain pending.

## Phase 2 Diagnostic Toolchain Result (2026-10-13)

Verified implementation-related facts for the Phase 2 diagnostic only:

- Added a minimal diagnostic MIDlet source at `src/java-me-diagnostic/me/holojava/diagnostic/DiagnosticMIDlet.java`.
- It uses standard MIDP 2.0/LCDUI APIs only (`MIDlet`, `Display`, `Canvas`, `Graphics`); no Nokia/vendor APIs, assets, networking, audio, reflection, or gameplay systems.
- Compiled successfully with JDK 21 and `--release 8` against the locally pinned CLDC 1.1 (2.0.4) and MIDP 2.0 (2.0.4) API stubs.
- Output class major version: 52.
- Preverified successfully with ProGuard 6.2.2 using `-microedition`, `-dontshrink -dontoptimize -dontobfuscate -dontwarn`.
- Packaged into `dist/java-me-diagnostic/HoloJavaToolchainDiagnostic.jar` (3153 bytes).
- Matching descriptor: `dist/java-me-diagnostic/HoloJavaToolchainDiagnostic.jad`; `MIDlet-Jar-Size` exactly matches the final JAR.
- JAR contains only the manifest and two application classes; API stub classes are not included.
- Source archiving check: JAR passes `unzip -t`; class files expose `midi ` lifecycle methods.
- J2ME Loader runtime launch was verified on 2026-10-13: MIDlet launch PASSED, key input PASSED, and Canvas dimensions PASSED; no additional errors were observed.
- Phase 2 diagnostic toolchain validation is now marked complete based on that runtime result.
- Phase 3 local gameplay implementation is started and preverified; see `dist/java-me-phase3/HoloJavaPhase3.jar`.
- Phase 3 gameplay implementation has started as a bounded local prototype; J2ME Loader runtime gameplay validation is still required before claiming runtime success.


## Phase 2 Closure (2026-10-13)

Phase 2 diagnostic toolchain validation satisfied the verified acceptance trail:

1. Java build host: OpenJDK 21.
2. API used for compilation: `org.microemu:cldcapi11:2.0.4` and `org.microemu:midpapi20:2.0.4`.
3. Compiler result: successful compile with JDK 21, class major version 52.
4. Preverification: ProGuard 6.2.2 with `-microedition` passed.
5. Packaging: 3,153-byte JAR and matching JAD verified for descriptor consistency.
6. Runtime: J2ME Loader confirmed MIDlet launch, key input, and Canvas dimensions with no errors.

Verified only:

- J2ME Loader on the tested configuration.
- Standard MIDP 2.0/LCDUI APIs for the diagnostic MIDlet.
- MIDlet lifecycle, package manifest/JAD consistency, basic rendering, key handling, and measured current Canvas behavior.

Not verified by this diagnostic:

- Nokia-device compatibility.
- Audio playback or audio format support.
- Pointer/touch input.
- Physical-device heap, orientation, key-mapping behavior.
- Aspect-preserving scaling across multiple resolutions beyond the tested Canvas dimensions.
- Long-term runtime stability, repeated launch cycles, or repeated installation/uninstallation behavior.
- Asset license approval or redistribution rights.

Remaining before authorized Phase 3 work:

1. Explicit approval to implement gameplay.
2. Independent gameplay design scope and asset strategy.
3. Confirmed asset licensing/redistribution decision where required.
4. Any Nokia or additional runtime targets remain future validation, not current evidence.
