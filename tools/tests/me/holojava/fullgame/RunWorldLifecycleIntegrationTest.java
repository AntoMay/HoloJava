package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Drives a real {@link RunWorld} from construction to run end, and from one run into the next.
 *
 * <p>Unlike the other suites, this one constructs the genuine objects — a real {@code AssetLibrary},
 * a real {@code SaveStore} over an in-memory record store, and {@code new RunWorld(...)} itself. That
 * is possible because {@code javax.microedition.lcdui.Image} is shadowed on the test classpath by a
 * test-only stand-in; the pinned MIDP stub otherwise throws {@code Error} from
 * {@code Image.createImage}, which {@code AssetLibrary.image()} does not catch because it catches
 * {@code Exception}. The shadow is compiled into a separate output directory and never packaged, so
 * production source and the shipped JAR are unaffected.
 *
 * <p>The chain under test is the whole one, not a stage in isolation: start a run, walk it with real
 * EXP pickups until it levels up, take a real offer, and observe the resulting effect through a real
 * combat tick. Cross-run isolation is checked the same way, by dirtying one world as thoroughly as
 * possible and then constructing a second one from the same assets and the same save.
 *
 * <p>Field reads go through {@link java.lang.reflect} and, for private primitives, {@code
 * sun.misc.Unsafe}, matching the other suites. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldLifecycleIntegrationTest {

    private static int checks = 0;
    private static int failures = 0;

    // ---------------------------------------------------------------- infrastructure

    private static Object unsafe;

    static {
        try {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = f.get(null);
        } catch (Exception e) {
            throw new IllegalStateException("sun.misc.Unsafe unavailable", e);
        }
    }

    private static Object allocate(Class<?> type) {
        try {
            return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        } catch (Exception e) {
            throw new IllegalStateException("cannot allocate " + type.getName(), e);
        }
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            long off = (Long) unsafe.getClass()
                    .getMethod("objectFieldOffset", Field.class).invoke(unsafe, f);
            Class<?> t = f.getType();
            if (t == int.class) {
                unsafe.getClass().getMethod("putInt", Object.class, long.class, int.class)
                        .invoke(unsafe, target, off, value);
            } else if (t == long.class) {
                unsafe.getClass().getMethod("putLong", Object.class, long.class, long.class)
                        .invoke(unsafe, target, off, value);
            } else if (t == boolean.class) {
                unsafe.getClass().getMethod("putBoolean", Object.class, long.class, boolean.class)
                        .invoke(unsafe, target, off, value);
            } else if (t == double.class) {
                unsafe.getClass().getMethod("putDouble", Object.class, long.class, double.class)
                        .invoke(unsafe, target, off, value);
            } else {
                unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
                        .invoke(unsafe, target, off, value);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }

    private static Object get(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static int getInt(Object t, String f) { return ((Number) get(t, f)).intValue(); }
    private static long getLong(Object t, String f) { return ((Number) get(t, f)).longValue(); }
    private static boolean getBool(Object t, String f) { return ((Boolean) get(t, f)).booleanValue(); }

    private static Object[] pool(Object w, String name) {
        return (Object[]) get(w, name);
    }

    private static Object call(Object target, String name, Class<?>[] sig, Object[] args) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException("cannot call " + name, e);
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** An in-memory record store that counts writes, so persistence side effects are observable. */
    private static final class MemoryStore implements SaveStore.RecordStorage {
        byte[] record;
        int writes;
        int[] written = new int[64];

        public byte[] read(int recordId) { return record; }

        public boolean write(int recordId, byte[] payload) {
            writes++;
            record = payload.clone();
            if (recordId >= 0 && recordId < written.length) written[recordId]++;
            return true;
        }
    }

    private static AssetLibrary assets;

    private static MemoryStore store;
    private static SaveStore save;

    /** Builds a genuine world through the real constructor, with auto-spawn disabled. */
    private static RunWorld start(int characterIndex, int modeIndex, int stageIndex) {
        RunWorld w = new RunWorld(assets, save, characterIndex, modeIndex, stageIndex);
        // Keep the world deterministic: the suite places entities explicitly rather than letting
        // the spawn timer fill the arena behind our assertions.
        set(w, "nextSpawnAt", Long.valueOf(Long.MAX_VALUE));
        return w;
    }

    private static void resetFixture() {
        store = new MemoryStore();
        save = new SaveStore(store);
    }

    // ---------------------------------------------------------------- driving the world

    private static void tick(RunWorld w, long now) {
        w.update(0, false, false, now);
    }

    private static void tick(RunWorld w, long now, boolean special) {
        w.update(0, false, special, now);
    }

    /** Places an EXP pickup exactly on the player and ticks once, driving the real pickup path. */
    private static void grantExp(RunWorld w, int value, long now) {
        int px = getInt(w, "px");
        int py = getInt(w, "py");
        call(w, "dropPickup", new Class<?>[] { int.class, int.class, int.class, int.class, long.class },
                new Object[] { Integer.valueOf(px + 18), Integer.valueOf(py + 18), Integer.valueOf(0),
                        Integer.valueOf(value), Long.valueOf(now) });
        tick(w, now);
    }

    /**
     * Buys the Special Attack upgrade on the fixture save — the only ability a run still gates.
     * The skill needs no grant at all anymore: Skill Pasif left the shop, so every run starts
     * holding it.
     */
    private static void grantSpecialAttack() {
        save.setUpgradeLevel(ContentCatalog.upgradeIndex("special_attack"), 1);
    }

    /** Takes choices until the world is running again, so later ticks are actually processed. */
    private static void drainLevelUps(RunWorld w) {
        for (int i = 0; i < 12 && w.state() == RunWorld.STATE_LEVEL_UP; i++) w.selectOffer();
    }

    /** Spawns one enemy through the real spawner and parks it at a chosen offset from the player. */
    private static Object spawnEnemyAt(RunWorld w, long now, int dx, int dy, int hp) throws Exception {
        int before = activeCount(w, "enemies");
        call(w, "spawnEnemy", new Class<?>[] { long.class }, new Object[] { Long.valueOf(now) });
        Object[] pool = pool(w, "enemies");
        if (activeCount(w, "enemies") == before) return null;
        for (Object e : pool) {
            if (!getBool(e, "active")) continue;
            set(e, "x", Integer.valueOf(getInt(w, "px") + 12 + dx));
            set(e, "y", Integer.valueOf(getInt(w, "py") + 12 + dy));
            set(e, "hp", Integer.valueOf(hp));
            // nextAttack is a "not ready until" stamp, so an already-elapsed one is what makes
            // the enemy free to deal contact damage on this tick. The enemy still walks toward
            // the player on its own.
            set(e, "nextAttack", Long.valueOf(0L));
            return e;
        }
        return null;
    }

    private static int activeCount(Object w, String name) {
        int n = 0;
        for (Object e : pool(w, name)) if (getBool(e, "active")) n++;
        return n;
    }

    private static Object boss(RunWorld w) { return get(w, "boss"); }

    private static void clearEnemies(RunWorld w) {
        for (Object e : pool(w, "enemies")) set(e, "active", Boolean.FALSE);
    }

    /** Pool position of an element, so a real method taking a pool index can be driven. */
    private static int indexOf(Object w, String name, Object element) {
        Object[] p = pool(w, name);
        for (int i = 0; i < p.length; i++) if (p[i] == element) return i;
        return -1;
    }

    /** Calls the real step() helper so the integer floor can be measured directly. */
    private static int stepOf(Object w, double speed) throws Exception {
        return ((Number) call(w, "step", new Class<?>[] { double.class },
                new Object[] { Double.valueOf(speed) })).intValue();
    }

    private static Class<?> enemyClass() throws Exception {
        for (Class<?> c : Class.forName("me.holojava.fullgame.RunWorld").getDeclaredClasses()) {
            if (c.getSimpleName().equals("Enemy")) return c;
        }
        throw new IllegalStateException("no nested Enemy type");
    }

    private static Class<?> offerClass() throws Exception {
        for (Class<?> c : Class.forName("me.holojava.fullgame.RunWorld").getDeclaredClasses()) {
            if (c.getSimpleName().equals("Offer")) return c;
        }
        throw new IllegalStateException("no nested Offer type");
    }

    // ---------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        assets = new AssetLibrary();

        testStartInitialisesTheWholeRun();
        testExpPickupTriggersLevelUpAndOffers();
        testSkillPasifGrantsTheSkillAndItFiresByItself();
        testSkillEffectMatrix();
        testSpecialLocked();
        testEffectsAreActuallyConsumedByCombat();
        testCooldownsAndLevelCapsHold();
        testDeathEndsTheRunOnce();
        testVictoryCreditsTheRewardExactlyOnce();
        testResultsScreenIsEnteredOnlyOnce();
        testANewRunInheritsNothing();
        testResetClearsADirtiedRun();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---------------------------------------------------------------- 1. run start

    /** A freshly constructed run must come up fully initialised and idle. */
    private static void testStartInitialisesTheWholeRun() throws Exception {
        System.out.println("[1] a new run initialises everything");
        resetFixture();
        RunWorld w = start(0, 0, 0);
        ContentCatalog.CharacterDef ch = ContentCatalog.CHARACTERS[0];

        expectEq(w.state(), RunWorld.STATE_RUNNING, "the run starts running");
        expectEq(w.playerLevel(), 1, "at level 1");
        expectEq(w.experience(), 0, "with no experience banked");
        expectEq(w.expNeed(), 79, "and 79 EXP to the next level");
        expectEq(w.kills(), 0, "no kills");
        expectEq(w.coins(), 0, "no coins");
        expectEq(getInt(w, "defeatCount"), 0, "no defeats counted");

        expectEq(getInt(w, "baseMaxHp"), ch.hp, "base max HP comes from the character");
        expectEq(w.playerMaxHp(), ch.hp, "and becomes the live maximum");
        expectEq(w.playerHp(), w.playerMaxHp(), "with the player starting at full health");

        expectEq(w.weaponCount(), 1, "the character's starting weapon is equipped");
        expectEq(ContentCatalog.WEAPONS[getIntArray(w, "weaponSlots")[0]].id, ch.mainWeapon,
                "and it is the one the character declares");
        expect(getLongArray(w, "weaponReadyAt")[0] > 0L, "with its first shot on a short cooldown");
        expectEq(w.itemCount(), 0, "no items");

        expect(w.skillAcquired(), "the skill is always held: Skill Pasif left the shop");
        expectEq(w.specialCharge(), 0, "the special meter starts empty");
        expectEq(getLong(w, "skillReadyAt"), 0L, "no skill cooldown running yet");
        expectEq(getInt(w, "shield"), 0, "no shield");
        expectEq(getLong(w, "shieldReadyAt"), 0L, "no shield recharge armed");
        expectEq(getLong(w, "lifestealUntil"), 0L, "no lifesteal window");
        expectEq(getLong(w, "slowEnemiesUntil"), 0L, "no slow window");
        expectEq(getLong(w, "invulUntil"), 0L, "no invulnerability window");

        expectEq(activeCount(w, "enemies"), 0, "no enemies");
        expectEq(activeCount(w, "shots"), 0, "no projectiles in flight");
        expectEq(activeCount(w, "melee"), 0, "no melee arcs");
        expectEq(activeCount(w, "pickups"), 0, "no pickups on the ground");
        expect(!getBool(boss(w), "active"), "no boss");
        expectEq(getInt(w, "bossEncounterIndex"), -1, "and no encounter scheduled");

        expect(!getBool(w, "ringEventDone"), "the timed ring event is pending");
        expect(!getBool(w, "hordeEventDone"), "the timed horde event is pending");
        expect(getLong(w, "startTime") > 0L, "the run timer is set");
        expect(w.offerCount() >= 4, "initial offers are generated (" + w.offerCount() + ")");
    }

    // ---------------------------------------------------------------- 2. exp -> level up

    /** A real EXP pickup must bank experience, level up, and open the offer screen. */
    private static void testExpPickupTriggersLevelUpAndOffers() throws Exception {
        System.out.println("[2] an EXP pickup banks experience and levels up");
        resetFixture();
        RunWorld w = start(0, 0, 0);
        long now = System.currentTimeMillis();

        grantExp(w, 100, now);
        expectEq(w.state(), RunWorld.STATE_LEVEL_UP, "picking up enough EXP opens the level-up screen");
        expectEq(w.playerLevel(), 2, "and the level advanced to 2");
        expectEq(w.experience(), 100 - 79, "the spent EXP was deducted");
        expect(w.expNeed() > 0, "the next requirement is set");

        // Further ticks must not process the level-up again: update() ignores a non-running state.
        int levelBefore = w.playerLevel();
        int expBefore = w.experience();
        tick(w, now + 5000L);
        expectEq(w.playerLevel(), levelBefore, "a frozen level-up screen does not level again");
        expectEq(w.experience(), expBefore, "and does not consume more experience");

        w.selectOffer();
        expectEq(w.state(), RunWorld.STATE_RUNNING, "taking an offer resumes the run");
    }

    // ---------------------------------------------------------------- 3. skill chain end to end

    /**
     * The whole chain in one run: a run built with no purchase at all behind it, starting at t=0
     * already holding the skill, and then a combat tick in which the skill visibly damages an
     * enemy — with no level-up, no offer and no key anywhere in the path.
     */
    private static void testSkillPasifGrantsTheSkillAndItFiresByItself() throws Exception {
        System.out.println("[3] the run starts with the skill, which then fires by itself");
        resetFixture();
        grantSpecialAttack();
        RunWorld w = start(0, 0, 0);
        long now = System.currentTimeMillis() + 1000L;

        expect(w.skillAcquired(), "the run starts with the skill already held");
        expectEq(w.state(), RunWorld.STATE_RUNNING, "the run is running");

        Object enemy = spawnEnemyAt(w, now, 10, 0, 500);
        expect(enemy != null, "an enemy was spawned for the skill to hit");
        int hpBefore = getInt(enemy, "hp");
        tick(w, now + 100L);
        expect(getInt(enemy, "hp") < hpBefore,
                "Shark Bite reduced the nearby enemy from " + hpBefore + " to " + getInt(enemy, "hp"));
        expect(getLong(w, "skillReadyAt") > 0L, "and put the skill on cooldown");
    }

    // ---------------------------------------------------------------- 4. skill matrix

    /**
     * Every character's skill at its fixed base power: the skill is free and identical for
     * all, but the state each skill writes and the place that effect is later read differ. Each
     * row is asserted on its own run.
     */
    private static void testSkillEffectMatrix() throws Exception {
        System.out.println("[4] skill effect matrix, one run per character");
        for (int ci = 0; ci < ContentCatalog.CHARACTERS.length; ci++) {
            resetFixture();
            grantSpecialAttack();
            RunWorld w = start(ci, 0, 0);
            long now = System.currentTimeMillis() + 1000L;

            // Skills fire at their fixed base power now — there are no stacks left to scale them,
            // so this matrix pins the base values outright.
            expect(w.skillAcquired(), ContentCatalog.CHARACTERS[ci].name
                    + ": the run starts holding the skill, as every run does");

            long before = now;
            // 30 puts the enemy at px+42: outside the player's 24-wide box, so contact damage
            // cannot fire and blur the effect, but inside the narrowest skill radius of 45.
            // Note damageEnemiesInRadius measures from px/py, not the player's centre.
            Object enemy = spawnEnemyAt(w, now, 30, 0, 500);
            int hpBefore = enemy == null ? -1 : getInt(enemy, "hp");
            tick(w, now + 100L);
            expectEq(getLong(w, "skillReadyAt"), before + 100L + 15000L,
                    ContentCatalog.CHARACTERS[ci].name + ": skill goes on a 15 second cooldown");

            switch (ci) {
                case 0: {   // Shark Bite (Gawr Gura): radius 70, 60 damage, knockback
                    expect(enemy != null && getInt(enemy, "hp") < hpBefore,
                            "Gawr Gura: Shark Bite damaged an enemy inside radius 70");
                    break;
                }
                case 1: {   // Hunter's Instinct (Amelia Watson): slows enemies and delays their next attack
                    expectEq(getLong(w, "slowEnemiesUntil"), before + 100L + 3500L,
                            "Amelia Watson: Hunter's Instinct opened a 3.5 second slow");
                    expect(enemy != null && getLong(enemy, "nextAttack") == before + 100L + 1200L,
                            "Amelia Watson: and pushed the enemy's next attack 1.2 seconds out");
                    break;
                }
                case 2: {   // Undying (Mori Calliope): grants a shield and arms its recharge
                    expectEq(getInt(w, "shield"), 20, "Mori Calliope: Undying granted 20 shield");
                    expectEq(getLong(w, "shieldReadyAt"), before + 100L + 15000L,
                            "Mori Calliope: and armed the recharge 15 seconds out");
                    break;
                }
                case 3: {   // Second Flame (Takanashi Kiara): opens a lifesteal window
                    expectEq(getLong(w, "lifestealUntil"), before + 100L + 8000L,
                            "Takanashi Kiara: Second Flame opened an 8 second lifesteal window");
                    break;
                }
                default: {  // The Ancient One (Ninomae Inanis): brief invulnerability plus a small radius hit
                    expectEq(getLong(w, "invulUntil"), before + 100L + 2500L,
                            "Ninomae Inanis: The Ancient One granted 2.5 seconds of invulnerability");
                    expect(enemy != null && getInt(enemy, "hp") < hpBefore,
                            "Ninomae Inanis: and damaged an enemy inside radius 45");
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 5. special matrix

    /** The special attack is now locked in the shop. Verify it stays locked and doesn't fire. */
    private static void testSpecialLocked() throws Exception {
        System.out.println("[5] special attack is locked and does not fire");
        for (int ci = 0; ci < ContentCatalog.CHARACTERS.length; ci++) {
            resetFixture();
            // Even with grantSpecialAttack, the upgrade is locked in the catalog so it stays locked
            grantSpecialAttack();
            RunWorld w = start(ci, 0, 0);
            long now = System.currentTimeMillis() + 1000L;
            // Fill the meter to max
            set(w, "invulUntil", Long.valueOf(0L));
            set(w, "specialCharge", Integer.valueOf(RunWorld.SPECIAL_CHARGE_MAX));

            // The special should remain locked regardless of grantSpecialAttack call
            // Verify specialLocked remains true (the upgrade is locked in the catalog)
            expect(getBool(w, "specialLocked"), ContentCatalog.CHARACTERS[ci].name + ": specialLocked remains true");

            // Try to fire the special - it should not fire because it's locked
            tick(w, now + 100L, true);

            // State should remain IDLE, charge should remain full (not drained)
            expectEq(getInt(w, "specialState"), RunWorld.SPECIAL_STATE_IDLE,
                    ContentCatalog.CHARACTERS[ci].name + ": specialState remains IDLE when locked");
            expectEq(getInt(w, "specialCharge"), RunWorld.SPECIAL_CHARGE_MAX,
                    ContentCatalog.CHARACTERS[ci].name + ": charge remains full when locked");

            // Enemy should not be damaged by special (since it didn't fire)
            Object enemy = spawnEnemyAt(w, now, 90, 0, 900);
            if (enemy != null) {
                int hpBefore = getInt(enemy, "hp");
                tick(w, now + 100L, true);
                expectEq(getInt(enemy, "hp"), hpBefore,
                        ContentCatalog.CHARACTERS[ci].name + ": enemy not damaged by locked special");
            }
        }
    }  // end for (ci)

    // ---------------------------------------------------------------- 6. effects consumed by combat

    /**
     * Setting an effect field is not the same as using it. Each of these drives real combat code and
     * observes the consequence.
     */
    private static void testEffectsAreActuallyConsumedByCombat() throws Exception {
        System.out.println("[6] acquired effects are consumed by real combat code");

        // Lifesteal heals on the kill that granted it.
        resetFixture();
        RunWorld w = start(3, 0, 0);
        long now = System.currentTimeMillis() + 1000L;
        set(w, "lifestealUntil", Long.valueOf(now + 10000L));
        set(w, "playerHp", Integer.valueOf(10));
        Object enemy = spawnEnemyAt(w, now, 100, 0, 1);
        // The real damage function is where lifesteal is consumed, so driving it directly is what
        // isolates the wiring: the weapon gate never opens in this suite.
        int enemyIndex = indexOf(w, "enemies", enemy);
        call(w, "damageEnemy", new Class<?>[] { int.class, enemyClass(), int.class, boolean.class,
                    int.class, int.class, int.class },
                new Object[] { Integer.valueOf(enemyIndex), enemy, Integer.valueOf(1),
                        Boolean.FALSE, Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0) });
        expect(!getBool(enemy, "active"), "the enemy died");
        expectEq(w.playerHp(), 11, "and lifesteal healed the player by 1");

        // A shield absorbs damage before it reaches health.
        resetFixture();
        w = start(2, 0, 0);
        now = System.currentTimeMillis() + 1000L;
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "shield", Integer.valueOf(10));
        set(w, "playerHp", Integer.valueOf(50));
        int before = w.playerHp();
        int defense = getInt(w, "defensePercent");
        int incoming = 6;
        int expected = Math.max(1, incoming * (100 - defense) / 100);
        call(w, "damagePlayer", new Class<?>[] { int.class, long.class },
                new Object[] { Integer.valueOf(incoming), Long.valueOf(now) });
        expectEq(w.playerHp(), before, "health was untouched while the shield covered the hit");
        // The shield absorbs what actually reaches it, which is the hit after this character's
        // own reduction -- the shield is not a way around Defense Up or the character's stat.
        expectEq(getInt(w, "shield"), 10 - expected,
                "the shield paid the " + expected + " points that survived a " + defense
                        + "% reduction, not the raw " + incoming);

        // Invulnerability makes damage a no-op outright.
        set(w, "invulUntil", Long.valueOf(now + 5000L));
        set(w, "shield", Integer.valueOf(0));
        before = w.playerHp();
        call(w, "damagePlayer", new Class<?>[] { int.class, long.class },
                new Object[] { Integer.valueOf(20), Long.valueOf(now) });
        expectEq(w.playerHp(), before, "damage during invulnerability is ignored entirely");

        // The slow window is read by the enemy movement code. Two parallel worlds that differ only
        // in that window, so the comparison cannot be skewed by the enemy settling first. The
        // character matters: every run now holds its skill from the first tick, and character 1's
        // Hunter's Instinct would slow the "normal" world's enemies too, so this pair uses a
        // character whose skill (Second Flame's lifesteal window) touches no movement at all.
        resetFixture();
        RunWorld slowed = start(3, 0, 0);
        RunWorld normal = start(3, 0, 0);
        now = System.currentTimeMillis() + 1000L;
        set(slowed, "slowEnemiesUntil", Long.valueOf(now + 600000L));
        Object a = spawnEnemyAt(slowed, now, 200, 0, 500);
        Object b = spawnEnemyAt(normal, now, 200, 0, 500);
        if (a != null && b != null) {
            // step() floors at 1 pixel, and every catalogued enemy is slow enough that both the
            // slowed and the unslowed step floor to 1, which hides the effect entirely. The
            // reference's step is byte-identical, so that masking is inherited. Raising both
            // speeds above the floor is what makes the multiplier observable at all.
            set(a, "speed", Double.valueOf(100.0));
            set(b, "speed", Double.valueOf(100.0));
            int slowTravel = 0;
            int normalTravel = 0;
            int xa0 = getInt(a, "x");
            int xb0 = getInt(b, "x");
            for (int i = 1; i <= 10; i++) {
                tick(slowed, now + i * 100L);
                tick(normal, now + i * 100L);
            }
            slowTravel = Math.abs(getInt(a, "x") - xa0);
            normalTravel = Math.abs(getInt(b, "x") - xb0);
            expect(slowTravel < normalTravel && normalTravel > 0,
                    "the slowed enemy covered less ground than the identical unslowed one ("
                            + slowTravel + " vs " + normalTravel + " px)");
        }

        // And record the masking itself, so the limitation is pinned rather than hidden.
        int stepAtCatalogSpeed = stepOf(slowed, 1.0);
        int stepAtCatalogSpeedSlowed = stepOf(slowed, 0.35);
        expectEq(stepAtCatalogSpeed, 1, "an unslowed catalog-speed enemy steps one pixel");
        expectEq(stepAtCatalogSpeedSlowed, 1,
                "and a slowed one also floors to one, which is why the effect is invisible at "
                        + "catalog speeds");
    }

    // ---------------------------------------------------------------- 7. cooldowns and caps

    private static void testCooldownsAndLevelCapsHold() throws Exception {
        System.out.println("[7] cooldowns and level caps");
        resetFixture();
        grantSpecialAttack();
        RunWorld w = start(0, 0, 0);
        long now = System.currentTimeMillis() + 1000L;

        // Another tick inside the cooldown does nothing at all.
        Object enemy = spawnEnemyAt(w, now, 8, 0, 900);
        long firstCast = now + 100L;
        tick(w, firstCast);
        long readyAfterFirst = getLong(w, "skillReadyAt");
        int hpAfterFirst = getInt(enemy, "hp");
        tick(w, firstCast + 1000L);
        expectEq(getInt(enemy, "hp"), hpAfterFirst, "a tick inside the cooldown does nothing");
        expectEq(getLong(w, "skillReadyAt"), readyAfterFirst, "and does not extend the cooldown");

        // After the cooldown lapses the skill fires again.
        tick(w, readyAfterFirst);
        expect(getInt(enemy, "hp") < hpAfterFirst, "the skill fires again once the cooldown lapses");
        expectEq(getLong(w, "skillReadyAt"), readyAfterFirst + 15000L, "and rearms a fresh 15 seconds");

        // Shield recharge: the armed timer refills the shield and re-arms. The character matters:
        // every run now holds its skill from the first tick, and character 2's Undying would set
        // shield to 20 on this very tick and overwrite the refill, so this block uses a character
        // whose skill (Shark Bite's damage pulse) never touches the shield.
        resetFixture();
        w = start(0, 0, 0);
        now = System.currentTimeMillis() + 1000L;
        set(w, "shield", Integer.valueOf(0));
        set(w, "shieldReadyAt", Long.valueOf(now + 5000L));
        tick(w, now + 6000L);
        expectEq(getInt(w, "shield"), 15, "the shield recharged to 15 when its timer elapsed");
        expectEq(getLong(w, "shieldReadyAt"), now + 6000L + 15000L, "and re-armed 15 seconds later");

        // Weapon level caps: repeated upgrades stop at the weapon's maximum.
        resetFixture();
        w = start(0, 0, 0);
        int maxLevel = ContentCatalog.WEAPONS[getIntArray(w, "weaponSlots")[0]].maxLevel;
        for (int i = 0; i < maxLevel + 5 && w.weaponCount() > 0; i++) {
            int[] slots = getIntArray(w, "weaponSlots");
            int existing = -1;
            for (int s = 0; s < w.weaponCount(); s++) if (slots[s] >= 0) { existing = s; break; }
            if (existing < 0) break;
            set(w, "invulUntil", Long.valueOf(0L));
            // Drive the real upgrade path directly: build an upgrade offer for the held weapon.
            Object offer = Array.get(pool(w, "offers"), 0);
            set(offer, "type", Integer.valueOf(1));            // OFFER_WEAPON
            set(offer, "value", Integer.valueOf(slots[existing]));
            set(offer, "existing", Integer.valueOf(existing));
            set(offer, "collab", Boolean.FALSE);
            set(w, "state", Integer.valueOf(RunWorld.STATE_LEVEL_UP));
            w.selectOffer();
        }
        int[] lv = getIntArray(w, "weaponLevels");
        int cap = maxLevel;
        for (int s = 0; s < w.weaponCount(); s++) {
            if (lv[s] > cap) cap = lv[s];
        }
        expect(cap <= maxLevel, "the starting weapon never exceeded its maximum level of " + maxLevel);
    }

    private static int[] getIntArray(Object target, String field) {
        return (int[]) get(target, field);
    }

    private static long[] getLongArray(Object target, String field) {
        return (long[]) get(target, field);
    }

    // ---------------------------------------------------------------- 8. death

    /** Death must end the run, and the end must not be processed a second time. */
    private static void testDeathEndsTheRunOnce() throws Exception {
        System.out.println("[8] death ends the run exactly once");
        resetFixture();
        RunWorld w = start(0, 0, 0);
        long now = System.currentTimeMillis() + 1000L;

        set(w, "playerHp", Integer.valueOf(2));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "defensePercent", Integer.valueOf(0));
        Object enemy = spawnEnemyAt(w, now, 0, 0, 500);
        set(enemy, "atk", Integer.valueOf(50));

        tick(w, now);
        expectEq(w.state(), RunWorld.STATE_GAME_OVER, "the run ends in game over");
        expectEq(w.playerHp(), 0, "with health clamped at zero, never negative");

        int killsBefore = w.kills();
        long t = now + 1000L;
        for (int i = 0; i < 5; i++) tick(w, t += 2000L);
        expectEq(w.state(), RunWorld.STATE_GAME_OVER, "a finished run stays finished");
        expectEq(w.playerHp(), 0, "and health stays at zero");
        expectEq(w.kills(), killsBefore, "and nothing is scored after death");
    }

    // ---------------------------------------------------------------- 9. victory reward

    /** The clear reward is a persistent transaction and must be committed exactly once. */
    private static void testVictoryCreditsTheRewardExactlyOnce() throws Exception {
        System.out.println("[9] victory credits the clear reward exactly once");
        resetFixture();
        RunWorld w = start(0, 0, 0);
        long now = System.currentTimeMillis() + 1000L;

        // defeatBoss reads encounterDefeated[1] as the final encounter on every
        // stage (the unsourced hard 30:00 duplicate was removed), so the
        // encounter that ends stage mode depends on the stage.
        expect(!ContentCatalog.STAGES[0].hard, "the first stage is not a hard stage");
        int finalEncounter = ContentCatalog.STAGES[0].hard ? 2 : 1;
        call(w, "spawnBoss", new Class<?>[] { int.class },
                new Object[] { Integer.valueOf(finalEncounter) });
        Object b = boss(w);
        expect(getBool(b, "active"), "the final boss is on the field");

        set(w, "defeatCount", Integer.valueOf(8));
        int expectedReward = ContentCatalog.STAGES[0].coinClear + 8 / 4;
        int coinsBefore = w.coins();
        int writesBefore = store.writes;

        set(b, "hp", Integer.valueOf(1));
        call(w, "damageBoss", new Class<?>[] { int.class, boolean.class, int.class },
                new Object[] { Integer.valueOf(999), Boolean.FALSE, Integer.valueOf(0) });

        expectEq(w.state(), RunWorld.STATE_VICTORY, "defeating it wins the run");
        expect(w.won(), "and flags the victory");
        expectEq(w.coins(), coinsBefore + expectedReward,
                "the run counter gained coinClear plus a quarter of the defeats");
        expectEq(store.writes - writesBefore, 1, "exactly one persistent write was committed");
        expectEq((int) save.coinBalance(), expectedReward, "and the stored balance holds the reward");

        // The guard: a finished run cannot be finished again.
        long t = now;
        for (int i = 0; i < 10; i++) {
            call(w, "damageBoss", new Class<?>[] { int.class, boolean.class, int.class },
                    new Object[] { Integer.valueOf(999), Boolean.FALSE, Integer.valueOf(0) });
            tick(w, t += 1000L);
        }
        expectEq(store.writes - writesBefore, 1,
                "repeated hits and ticks after the win commit no further writes");
        expectEq((int) save.coinBalance(), expectedReward, "and the stored balance is unchanged");
    }

    // ---------------------------------------------------------------- 10. results once

    /** The canvas must enter the results screen once; leaving GAME is what prevents a second call. */
    private static void testResultsScreenIsEnteredOnlyOnce() throws Exception {
        System.out.println("[10] the results screen is entered once per finished run");
        resetFixture();
        Object canvas = allocate(Class.forName("me.holojava.fullgame.FullGameCanvas"));
        set(canvas, "assets", assets);
        set(canvas, "save", save);
        set(canvas, "screen", Integer.valueOf(7));          // GAME (was 6, now 7 due to HELP=5)
        set(canvas, "characterIndex", Integer.valueOf(0));
        set(canvas, "modeIndex", Integer.valueOf(0));
        set(canvas, "stageIndex", Integer.valueOf(0));

        RunWorld w = start(0, 0, 0);
        set(w, "state", Integer.valueOf(RunWorld.STATE_VICTORY));
        set(w, "won", Boolean.TRUE);
        Field worldField = Class.forName("me.holojava.fullgame.FullGameCanvas").getDeclaredField("world");
        worldField.setAccessible(true);
        worldField.set(canvas, w);

        int writesBefore = store.writes;
        Method m = Class.forName("me.holojava.fullgame.FullGameCanvas")
                .getDeclaredMethod("handleRunEnd");
        m.setAccessible(true);
        m.invoke(canvas);

        expectEq(getInt(canvas, "screen"), 8, "the canvas moved to the results screen (was 7, now 8)");
        expect(getBool(canvas, "worldWasVictory"), "and recorded it as a victory");
        expectEq(getInt(canvas, "resultIndex"), 0, "with the first entry highlighted");
        int writesAfterFirst = store.writes;
        expect(writesAfterFirst > writesBefore, "the stage completion was persisted");

        // A second update tick in GAME would re-enter; but the canvas has left GAME, so the
        // production guard is the screen change itself. Assert the world is now behind RESULTS.
        Method updateResults = Class.forName("me.holojava.fullgame.FullGameCanvas")
                .getDeclaredMethod("updateResults", boolean.class, boolean.class, boolean.class,
                        boolean.class);
        updateResults.setAccessible(true);
        updateResults.invoke(canvas, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);
        expectEq(getInt(canvas, "screen"), 0, "choosing the second entry returns to the main menu");
        expect(worldField.get(canvas) == null, "and drops the world reference");

        // Restarting from results builds a genuinely new world.
        set(canvas, "screen", Integer.valueOf(8));
        set(canvas, "resultIndex", Integer.valueOf(0));
        updateResults.invoke(canvas, Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
        expectEq(getInt(canvas, "screen"), 7, "restarting returns to gameplay (GAME=7)");
        Object restarted = worldField.get(canvas);
        expect(restarted != null, "a new world was constructed");
        if (restarted != null) {
            RunWorld rw = (RunWorld) restarted;
            expectEq(rw.state(), RunWorld.STATE_RUNNING, "the new run is running");
            expectEq(rw.playerLevel(), 1, "at level 1");
            expect(rw.skillAcquired(), "with the skill held, as it is in every run");
            expectEq(getLong(rw, "skillReadyAt"), 0L, "and no skill cooldown carried over");
        }
    }

    // ---------------------------------------------------------------- 11. cross-run isolation

    /**
     * The real leak test: dirty one run as hard as the public API allows, then build a second run
     * from the same assets and the same save and check nothing carried over.
     */
    private static void testANewRunInheritsNothing() throws Exception {
        System.out.println("[11] a new run inherits nothing from the previous one");
        resetFixture();
        grantSpecialAttack();

        // ---- run one: fight with the passive skill, die, earn
        RunWorld first = start(3, 1, 0);
        long now = System.currentTimeMillis() + 1000L;

        Object enemy = spawnEnemyAt(first, now, 6, 0, 900);
        tick(first, now + 100L);                   // lifesteal window opens
        tick(first, now + 200L, true);             // special fires

        // Put enemies, projectiles, melee arcs and pickups on the field.
        spawnEnemyAt(first, now + 300L, 40, 0, 300);
        call(first, "dropPickup",
                new Class<?>[] { int.class, int.class, int.class, int.class, long.class },
                new Object[] { Integer.valueOf(50), Integer.valueOf(50), Integer.valueOf(0),
                        Integer.valueOf(30), Long.valueOf(now + 300L) });
        grantExp(first, 400, now + 400L);
        drainLevelUps(first);

        // Earn coins through the real pickup path.
        call(first, "dropPickup",
                new Class<?>[] { int.class, int.class, int.class, int.class, long.class },
                new Object[] { Integer.valueOf(getInt(first, "px") + 18),
                        Integer.valueOf(getInt(first, "py") + 18), Integer.valueOf(1),
                        Integer.valueOf(25), Long.valueOf(now + 500L) });
        tick(first, now + 500L);

        set(first, "playerHp", Integer.valueOf(1));
        set(first, "invulUntil", Long.valueOf(0L));
        Object killer = spawnEnemyAt(first, now + 600L, 0, 0, 500);
        set(killer, "atk", Integer.valueOf(99));
        tick(first, now + 600L);

        expectEq(first.state(), RunWorld.STATE_GAME_OVER, "the first run was played to a death");
        expect(first.coins() > 0 || save.coinBalance() > 0L, "coins were earned during the run");
        long storedBefore = save.coinBalance();

        // ---- run two: same assets, same save
        RunWorld second = start(3, 1, 0);

        expectEq(second.state(), RunWorld.STATE_RUNNING, "the second run starts running");
        expect(second.skillAcquired(),
            "the skill is held again: it is permanent, like the coin balance");
        expectEq(second.specialCharge(), 0, "the special meter is empty again");
        expectEq(getLong(second, "skillReadyAt"), 0L, "no skill cooldown carried over");
        expectEq(getInt(second, "shield"), 0, "no shield carried over");
        expectEq(getLong(second, "shieldReadyAt"), 0L, "no shield recharge carried over");
        expectEq(getLong(second, "lifestealUntil"), 0L, "no lifesteal window carried over");
        expectEq(getLong(second, "slowEnemiesUntil"), 0L, "no slow window carried over");
        expectEq(getLong(second, "invulUntil"), 0L, "no invulnerability carried over");

        expectEq(second.playerLevel(), 1, "the level reset");
        expectEq(second.experience(), 0, "experience reset");
        expectEq(second.kills(), 0, "kills reset");
        expectEq(getInt(second, "defeatCount"), 0, "defeats reset");
        expectEq(second.coins(), 0, "the run-local coin counter reset");

        expectEq(second.weaponCount(), 1, "the inventory is the character's single starting weapon");
        expectEq(second.itemCount(), 0, "no items carried over");

        expectEq(activeCount(second, "enemies"), 0, "no enemies carried over");
        expectEq(activeCount(second, "shots"), 0, "no projectiles carried over");
        expectEq(activeCount(second, "melee"), 0, "no melee arcs carried over");
        expectEq(activeCount(second, "pickups"), 0, "no pickups carried over");
        expect(!getBool(boss(second), "active"), "no boss carried over");
        expectEq(getInt(second, "bossEncounterIndex"), -1, "no encounter carried over");
        expect(!getBool(second, "ringEventDone"), "the ring event is pending again");
        expect(!getBool(second, "hordeEventDone"), "the horde event is pending again");
        expect(get(second, "heldOffer") == null, "the stashed offer was cleared");

        // The one thing that is supposed to survive: the persistent balance.
        expectEq((int) save.coinBalance(), (int) storedBefore, "the permanent coin balance did survive the new run");

        // And the fresh world really is playable rather than just zeroed.
        long t = System.currentTimeMillis() + 1000L;
        grantExp(second, 100, t);
        expectEq(second.state(), RunWorld.STATE_LEVEL_UP, "the new run can still level up");
    }

    // ---------------------------------------------------------------- 12. reset

    /**
     * {@code reset} is private and is called only from the constructor, so a fresh-instance test
     * cannot see it: a new object is already zeroed by the runtime. This drives it directly on a
     * deliberately dirtied world, which is what makes "a reset line was removed" detectable rather
     * than silently unobservable.
     */
    private static void testResetClearsADirtiedRun() throws Exception {
        System.out.println("[12] reset clears every run-scoped field of a dirtied world");
        resetFixture();
        grantSpecialAttack();
        RunWorld w = start(2, 1, 0);
        long now = System.currentTimeMillis() + 1000L;
        tick(w, now + 100L);                           // skill: shield + recharge
        tick(w, now + 200L, true);                     // special

        // Dirty every category the reset is supposed to clear.
        spawnEnemyAt(w, now + 300L, 30, 0, 300);
        spawnEnemyAt(w, now + 300L, -30, 20, 300);
        call(w, "dropPickup", new Class<?>[] { int.class, int.class, int.class, int.class, long.class },
                new Object[] { Integer.valueOf(40), Integer.valueOf(40), Integer.valueOf(0),
                        Integer.valueOf(50), Long.valueOf(now + 300L) });
        call(w, "spawnBoss", new Class<?>[] { int.class }, new Object[] { Integer.valueOf(0) });
        grantExp(w, 400, now + 400L);
        drainLevelUps(w);
        set(w, "shield", Integer.valueOf(9));
        set(w, "slowEnemiesUntil", Long.valueOf(now + 99999L));
        set(w, "lifestealUntil", Long.valueOf(now + 99999L));
        set(w, "invulUntil", Long.valueOf(now + 99999L));
        set(w, "defeatCount", Integer.valueOf(17));
        set(w, "kills", Integer.valueOf(23));
        set(w, "coins", Integer.valueOf(99));
        set(w, "heldOffer", allocate(offerClass()));
        // The timed events must actually have fired first, or "reset re-armed them" would pass
        // against a world where they were never set in the first place.
        set(w, "ringEventDone", Boolean.TRUE);
        set(w, "hordeEventDone", Boolean.TRUE);
        set(w, "regenTickAt", Long.valueOf(now + 99999L));

        expect(getBool(w, "ringEventDone") && getBool(w, "hordeEventDone"),
                "both timed events were consumed before the reset");

        call(w, "reset", new Class<?>[] { SaveStore.class }, new Object[] { save });

        expect(w.skillAcquired(), "reset granted the skill, which every run now starts with");
        expectEq(w.specialCharge(), 0, "reset emptied the special meter");
        expectEq(getLong(w, "skillReadyAt"), 0L, "reset cleared the skill cooldown");
        expectEq(getInt(w, "shield"), 0, "reset cleared the shield");
        expectEq(getLong(w, "shieldReadyAt"), 0L, "reset disarmed the shield recharge");
        expectEq(getLong(w, "lifestealUntil"), 0L, "reset closed the lifesteal window");
        expectEq(getLong(w, "slowEnemiesUntil"), 0L, "reset closed the slow window");
        expectEq(getLong(w, "invulUntil"), 0L, "reset cleared invulnerability");
        expectEq(w.playerLevel(), 1, "reset returned to level 1");
        expectEq(w.experience(), 0, "reset emptied the experience bank");
        expectEq(w.kills(), 0, "reset zeroed the kills");
        expectEq(getInt(w, "defeatCount"), 0, "reset zeroed the defeat count");
        expectEq(w.coins(), 0, "reset zeroed the run-local coins");
        expectEq(w.weaponCount(), 1, "reset rebuilt the starting loadout");
        expectEq(w.itemCount(), 0, "reset cleared the items");
        expectEq(activeCount(w, "enemies"), 0, "reset deactivated every enemy");
        expectEq(activeCount(w, "shots"), 0, "reset deactivated every projectile");
        expectEq(activeCount(w, "melee"), 0, "reset deactivated every melee arc");
        expectEq(activeCount(w, "pickups"), 0, "reset removed every pickup");
        expect(!getBool(boss(w), "active"), "reset deactivated the boss");
        expectEq(getInt(w, "bossEncounterIndex"), -1, "reset cleared the encounter");
        expect(!getBool(w, "ringEventDone"), "reset re-armed the ring event");
        expect(!getBool(w, "hordeEventDone"), "reset re-armed the horde event");
        expect(get(w, "heldOffer") == null, "reset cleared the stashed offer");
        expectEq(w.state(), RunWorld.STATE_RUNNING, "reset put the run back into the running state");
        expectEq(w.playerHp(), w.playerMaxHp(), "and restored full health");
    }

    // ---------------------------------------------------------------- helpers

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(long got, long want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(String got, String want, String label) {
        expect(want.equals(got), label + ": expected \"" + want + "\" got \"" + got + "\"");
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}
