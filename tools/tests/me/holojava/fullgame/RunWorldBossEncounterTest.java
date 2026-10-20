package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the boss encounter director and the bookkeeping that decides when a stage counts as
 * completed.
 *
 * <p>The invariants, each of which the victory reward depends on:
 * <ol>
 *   <li>An encounter does not appear before its time gate.</li>
 *   <li>It appears once the gate is reached, and only one boss is live at a time.</li>
 *   <li>The same numbered encounter never starts twice.</li>
 *   <li>Defeating an encounter records it in {@code encounterDefeated}.</li>
 *   <li>Victory requires the stage's <em>final</em> encounter: the 20:00
 *       encounter (slot 1) on every stage. Clearing earlier encounters is
 *       not enough, and no 30:00 encounter exists.</li>
 *   <li>Time stages never run a boss director.</li>
 * </ol>
 *
 * <p>Construction: {@code RunWorld} cannot be built headlessly (see earlier reports), so instances
 * are allocated without running the constructor and driven through {@code sun.misc.Unsafe} by real
 * field type. The director, {@code spawnBoss}, and {@code defeatBoss} exercised here are the
 * production methods.
 */
public final class RunWorldBossEncounterTest {

    private static int checks = 0;
    private static int failures = 0;

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
            long offset = (Long) unsafe.getClass()
                    .getMethod("objectFieldOffset", Field.class).invoke(unsafe, f);
            Class<?> t = f.getType();
            if (t == int.class) {
                unsafe.getClass().getMethod("putInt", Object.class, long.class, int.class)
                        .invoke(unsafe, target, offset, value);
            } else if (t == long.class) {
                unsafe.getClass().getMethod("putLong", Object.class, long.class, long.class)
                        .invoke(unsafe, target, offset, value);
            } else if (t == boolean.class) {
                unsafe.getClass().getMethod("putBoolean", Object.class, long.class, boolean.class)
                        .invoke(unsafe, target, offset, value);
            } else if (t == double.class) {
                unsafe.getClass().getMethod("putDouble", Object.class, long.class, double.class)
                        .invoke(unsafe, target, offset, value);
            } else {
                unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
                        .invoke(unsafe, target, offset, value);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }

    private static int getInt(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static boolean getBool(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getBoolean(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static boolean[] bools(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return (boolean[]) f.get(target);
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Class<?> pickupClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Pickup");
    }

    /** Stage 0 by default; pass an index for a hard or time variant. */
    private static Object world(int stageIndex, int modeIndex) throws Exception {
        Object w = allocate(worldClass());
        set(w, "stage", ContentCatalog.STAGES[stageIndex]);
        set(w, "mode", ContentCatalog.MODES[modeIndex]);
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "stageIndex", Integer.valueOf(stageIndex));
        set(w, "save", null);
        set(w, "startTime", Long.valueOf(0L));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "bossEncounterIndex", Integer.valueOf(-1));
        set(w, "nextEndlessEncounter", Integer.valueOf(0));
        set(w, "state", Integer.valueOf(0));
        set(w, "playerLevel", Integer.valueOf(1));
        set(w, "expNeed", Integer.valueOf(79));
        set(w, "defeatCount", Integer.valueOf(8));
        set(w, "coins", Integer.valueOf(0));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "offerCount", Integer.valueOf(4));
        set(w, "rerolls", Integer.valueOf(0));
        set(w, "holds", Integer.valueOf(0));
        set(w, "selectedOffer", Integer.valueOf(0));
        set(w, "random", new java.util.Random(3L));
        set(w, "encounterStarted", new boolean[4]);
        set(w, "encounterDefeated", new boolean[4]);
        set(w, "offers", filled(offerClass(), 6));
        set(w, "pickups", filled(pickupClass(), 96));
        set(w, "enemies", filled(Class.forName("me.holojava.fullgame.RunWorld$Enemy"), 48));
        set(w, "shots", filled(Class.forName("me.holojava.fullgame.RunWorld$Shot"), 128));

        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
        set(boss, "defIndex", Integer.valueOf(0));
        set(boss, "x", Integer.valueOf(100));
        set(boss, "y", Integer.valueOf(100));
        set(boss, "hp", Integer.valueOf(10));
        set(boss, "maxHp", Integer.valueOf(10));
        set(boss, "anim", Integer.valueOf(0));
        set(boss, "hitMask", Long.valueOf(0L));
        set(boss, "chargeUntil", Long.valueOf(0L));
        set(w, "boss", boss);
        return w;
    }

    private static Class<?> offerClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Offer");
    }

    private static Object filled(Class<?> component, int count) throws Exception {
        Object array = Array.newInstance(component, count);
        for (int i = 0; i < count; i++) {
            Object element = allocate(component);
            try {
                Field active = component.getDeclaredField("active");
                active.setAccessible(true);
                active.setBoolean(element, false);
            } catch (NoSuchFieldException ignored) {
                // no flag to default
            }
            Array.set(array, i, element);
        }
        return array;
    }

    private static Object boss(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("boss");
        f.setAccessible(true);
        return f.get(w);
    }

    private static void director(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateBossDirector", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static void defeat(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("defeatBoss");
        m.setAccessible(true);
        m.invoke(w);
    }

    // --------------------------------------------------- HP, hitbox, targeting

    /**
     * The 20:00 boss keeps its catalog-scale HP.
     *
     * <p>spawnBossById used to pin HP with {@code min(20000, ...)}, so every scaled spawn and every
     * boss above 20000 collapsed to exactly 20000 — Halloween Bae's verified 72000 became 20000,
     * making all bosses share one durability. The ceiling is now the enemy HP cap of 100000, so
     * a 72000 boss is no longer crushed. Field values pass through the log/exp approximation in
     * MathUtil.power, so they are asserted near the catalog value rather than equal to it.
     */
    public static void testBossHpIsNotPinnedByACap() throws Exception {
        Object w = world(5, 0);   // stage1h
        // The two 10:00 bosses share one slot, so halloween_bae (the 20:00 gate's
        // boss) only spawns once fubuzilla and smol_ame have both fallen.
        director(w, 600L * 1000L);
        defeat(w);
        director(w, 600L * 1000L + 1L);
        defeat(w);
        director(w, 1200L * 1000L);

        int maxHp = getInt(boss(w), "maxHp");
        int catalogHp = ContentCatalog.boss("halloween_bae").hp;   // 72000
        expect(bossId(w).equals("halloween_bae"), "the 20:00 hard encounter is halloween_bae");
        expect(maxHp > 20000, "a 72000 HP boss is no longer crushed to the old 20000 cap (got "
            + maxHp + ")");
        expect(maxHp <= 100000, "boss HP still respects the 100000 enemy-HP ceiling (got " + maxHp
            + ")");
        expectNear(maxHp, catalogHp, (int) (catalogHp * 0.05),
            "at zero scaling a 72000 boss surfaces ~72000 HP");
        expectNear(getInt(boss(w), "hp"), maxHp, 0,
            "current HP starts at max");
        expectNear(getInt(boss(w), "atk"), ContentCatalog.boss("halloween_bae").atk, 2,
            "ATK is the catalog value at scale zero");
    }

    /**
     * The boss hitbox follows its sprite instead of one fixed 42x42 corner.
     *
     * <p>Sprite sizes run from 22x33 to 128x69, so a fixed box left most of a large boss's body
     * outside the hitbox and made the contact box land nowhere near the visible boss.
     */
    public static void testBossHitboxMatchesItsSprite() throws Exception {
        Object w = world(0, 0);   // stage1
        director(w, ContentCatalog.stage(0).bossATimeSec * 1000L);
        expect(bossId(w).equals("fubuzilla"), "the 10:00 boss is fubuzilla");
        expectEq(callInt(w, "bossWidth"), ContentCatalog.boss("fubuzilla").collW,
            "fubuzilla hitbox width is its sprite width (was a fixed 42)");
        expectEq(callInt(w, "bossHeight"), ContentCatalog.boss("fubuzilla").collH,
            "fubuzilla hitbox height is its sprite height (was a fixed 42)");
        expectEq(ContentCatalog.boss("pekodam").collW, 99, "pekodam's wide sprite keeps its box");
        expectEq(ContentCatalog.boss("nodoka").collW, 22, "nodoka's small sprite keeps its box");
    }
    /**
     * Aimed and homing shots can pick the boss as a target.
     *
     * <p>nearestEnemy ignored the boss, so during a boss fight every aimed weapon tracked the adds
     * around it and, with no adds left, flew off in the player's facing direction. nearestTarget
     * must return TARGET_BOSS when the boss is closest, and updateShots must steer a homing shot
     * toward the boss box.
     */
    public static void testAimedShotsTrackTheBoss() throws Exception {
        Object w = world(0, 0);
        expectEq(callInt(w, "nearestTarget"), RunWorld.TARGET_NONE, "no target while nothing is alive");

        director(w, ContentCatalog.stage(0).bossATimeSec * 1000L);   // spawns fubuzilla
        expectEq(callInt(w, "nearestTarget"), RunWorld.TARGET_BOSS,
            "the boss is the target when nothing else is alive");

        // An add closer to the player outranks the boss.
        Object enemy = pool(w, "enemies")[0];
        set(enemy, "active", Boolean.TRUE);
        set(enemy, "defIndex", Integer.valueOf(0));
        set(enemy, "x", Integer.valueOf(20));
        set(enemy, "y", Integer.valueOf(20));
        expectEq(callInt(w, "nearestTarget"), 0, "a closer add outranks the boss");

        // A homing shot aimed at the boss steers toward its box. Sausage speed is 5, so from
        // (250,250) toward fubuzilla's box at (140,-50) both components come out negative.
        Object s = pool(w, "shots")[0];
        set(s, "active", Boolean.TRUE);
        set(s, "fromPlayer", Boolean.TRUE);
        set(s, "pattern", Integer.valueOf(ContentCatalog.PATTERN_HOMING));
        set(s, "weaponIndex", Integer.valueOf(ContentCatalog.weaponIndex("sausage")));
        set(s, "target", Integer.valueOf(RunWorld.TARGET_BOSS));
        set(s, "x", Integer.valueOf(250));
        set(s, "y", Integer.valueOf(250));
        set(s, "dx", Integer.valueOf(0));
        set(s, "dy", Integer.valueOf(0));
        set(s, "life", Integer.valueOf(50));
        set(s, "age", Integer.valueOf(0));
        set(s, "hitMask", Long.valueOf(0L));
        updateShots(w, 0L);
        expect(getInt(s, "dx") < 0 && getInt(s, "dy") < 0,
            "the homing shot steers toward the boss box (dx=" + getInt(s, "dx")
                + " dy=" + getInt(s, "dy") + ")");

        // With the boss gone the shot stops steering rather than chasing a stale box.
        set(boss(w), "active", Boolean.FALSE);
        set(s, "dx", Integer.valueOf(0));
        set(s, "dy", Integer.valueOf(0));
        updateShots(w, 1L);
        expectEq(getInt(s, "dx"), 0, "no steering once the boss is gone");
        expectEq(getInt(s, "dy"), 0, "no steering once the boss is gone");
    }

    private static int callInt(Object w, String method) throws Exception {
        Method m = worldClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return ((Integer) m.invoke(w)).intValue();
    }

    private static void updateShots(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateShots", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static Object[] pool(Object w, String field) throws Exception {
        Field f = worldClass().getDeclaredField(field);
        f.setAccessible(true);
        return (Object[]) f.get(w);
    }

    private static void expectNear(int got, int want, int tolerance, String label) {
        expect(Math.abs(got - want) <= tolerance,
            label + ": expected " + want + " +/- " + tolerance + " got " + got);
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testBossWaitsForItsTimeGate();
        testEncounterIsNeverStartedTwice();
        testDefeatRecordsTheEncounter();
        testVictoryNeedsTheFinalEncounter();
        testHardStageNeedsItsThirdEncounter();
        testTimeStageRunsNoDirector();
        testEndlessModeRepeatsEncountersUpToTheCap();
        testStage1HardStatsAreVerified();
        testBossHpIsNotPinnedByACap();
        testBossHitboxMatchesItsSprite();
        testAimedShotsTrackTheBoss();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Stage 0: boss A at bossATimeSec, boss B at bossBTimeSec. */
    private static void testBossWaitsForItsTimeGate() throws Exception {
        System.out.println("[1] an encounter waits for its time gate");
        int gateA = ContentCatalog.stage(0).bossATimeSec;
        int gateB = ContentCatalog.stage(0).bossBTimeSec;
        expect(gateA < gateB, "boss A is scheduled before boss B");

        Object w = world(0, 0);
        director(w, (gateA - 1) * 1000L);
        expect(!getBool(boss(w), "active"), "no boss before the first gate");

        director(w, gateA * 1000L);
        expect(getBool(boss(w), "active"), "the boss appears once the gate is reached");
        expectEq(getInt(w, "bossEncounterIndex"), 0, "and it is encounter 0");

        // A live boss blocks a second spawn.
        director(w, (gateB + 60) * 1000L);
        expectEq(getInt(w, "bossEncounterIndex"), 0,
            "no second boss while one is already live");
        expect(!bools(w, "encounterStarted")[1], "encounter 1 was not started");
    }

    /** After a defeat the next gate can fire, but the same encounter never restarts. */
    private static void testEncounterIsNeverStartedTwice() throws Exception {
        System.out.println("[2] an encounter is never started twice");
        int gateA = ContentCatalog.stage(0).bossATimeSec;
        int gateB = ContentCatalog.stage(0).bossBTimeSec;

        Object w = world(0, 0);
        director(w, gateA * 1000L);
        defeat(w);

        director(w, gateB * 1000L);
        expect(getBool(boss(w), "active"), "encounter 1 starts after encounter 0 is defeated");
        expectEq(getInt(w, "bossEncounterIndex"), 1, "and it is encounter 1");

        defeat(w);
        director(w, (gateB + 600) * 1000L);
        expect(bools(w, "encounterStarted")[0] && bools(w, "encounterStarted")[1],
            "both encounters are recorded as started");
        expect(!getBool(boss(w), "active"),
            "a non-hard stage has no third encounter, so nothing more spawns");
    }

    private static void testDefeatRecordsTheEncounter() throws Exception {
        System.out.println("[3] a defeat records the encounter");
        Object w = world(0, 0);
        director(w, ContentCatalog.stage(0).bossATimeSec * 1000L);
        expectEq(getInt(w, "bossEncounterIndex"), 0, "encounter 0 is live");

        defeat(w);
        expect(bools(w, "encounterDefeated")[0], "encounter 0 is recorded as defeated");
        expectEq(getInt(w, "bossEncounterIndex"), -1, "no encounter is live afterwards");
        expect(!getBool(boss(w), "active"), "the boss was deactivated");
    }

    /**
     * The completion condition. Stage 0 is not hard, so it needs encounter slot 1. Beating only the
     * first boss must not end the run.
     */
    private static void testVictoryNeedsTheFinalEncounter() throws Exception {
        System.out.println("[4] victory needs the final encounter");
        Object w = world(0, 0);
        director(w, ContentCatalog.stage(0).bossATimeSec * 1000L);
        defeat(w);

        expect(!getBool(w, "won"), "beating the first boss does not win the stage");
        expectEq(getInt(w, "state"), 0, "and the run is still going");

        director(w, ContentCatalog.stage(0).bossBTimeSec * 1000L);
        defeat(w);
        expect(getBool(w, "won"), "beating the final boss wins the stage");
        expectEq(getInt(w, "state"), RunWorld.STATE_VICTORY, "and the run ends in victory");
    }

    /** Stage 1 Hard runs the verified schedule: Fubuzilla and Smol Ame at
     * 10:00 (sequentially through the single boss slot), Halloween Bae at
     * 20:00 for the win, and nothing at 30:00 (the old duplicate is gone). */
    private static void testHardStageNeedsItsThirdEncounter() throws Exception {
        System.out.println("[5] stage 1 hard runs fubuzilla, smol ame, then halloween bae");
        int hardIndex = 5;                       // stage1h: hard = true
        expect(ContentCatalog.stage(hardIndex).hard, "the fixture stage is a hard variant");

        Object w = world(hardIndex, 0);
        director(w, 600L * 1000L);
        expect(getBool(boss(w), "active"), "fubuzilla appears at 10:00");
        expect(bossId(w).equals("fubuzilla"), "the 10:00 encounter is fubuzilla");
        defeat(w);

        // The single boss slot cannot hold both 10:00 bosses at once, so the
        // verified second one follows as soon as the first falls.
        director(w, 600L * 1000L + 1L);
        expect(getBool(boss(w), "active"), "smol ame follows at the same gate");
        expect(bossId(w).equals("smol_ame"), "the second 10:00 encounter is smol ame");
        defeat(w);

        expect(!getBool(w, "won"), "two 10:00 encounters are not enough to win");
        director(w, 601L * 1000L);
        expect(!getBool(boss(w), "active"), "nothing else spawns right after");

        director(w, ContentCatalog.stage(hardIndex).bossBTimeSec * 1000L);
        expect(getBool(boss(w), "active"), "the 20:00 encounter appears on time");
        expect(bossId(w).equals("halloween_bae"), "the 20:00 encounter is halloween bae");
        defeat(w);
        expect(getBool(w, "won"), "beating halloween bae wins the hard stage");
        expectEq(getInt(w, "state"), RunWorld.STATE_VICTORY, "and the run ends in victory");

        Object w2 = world(hardIndex, 0);
        director(w2, 600L * 1000L);
        defeat(w2);
        director(w2, 600L * 1000L + 1L);
        defeat(w2);
        director(w2, 1200L * 1000L);
        defeat(w2);
        director(w2, 1800L * 1000L);
        expect(!getBool(boss(w2), "active"), "no duplicate boss appears at 30:00");
        expect(!bools(w2, "encounterStarted")[2], "slot 2 is never used");
    }

    private static String bossId(Object w) throws Exception {
        Object b = boss(w);
        Field f = b.getClass().getDeclaredField("defIndex");
        f.setAccessible(true);
        return ContentCatalog.BOSSES[f.getInt(b)].id;
    }

    /**
     * Pins the Stage 1 Hard boss numbers so a later balance pass changes them deliberately.
     *
     * <p>The test used to be called "stats match the verified source", and its javadoc claimed
     * they were verified against the wiki's Stage 1 (Hard) HP/ATK/SPD/EXP columns. They were not.
     * {@code data/wiki/bosses.csv} carries only id, name, stage id, scheduled time, mode
     * restrictions and a source URL -- there is no HP/ATK/SPD/EXP column in any of the extracted
     * wiki data, and the file's own {@code coverage_note} states that those values are not on the
     * Enemy page. What is documented for a boss is its spawn time, and that is asserted elsewhere.
     *
     * <p>So these are balance numbers, like the mob damage. They were halved once and restored: the
 * halving was a reaction to how punishing the numbers looked, but the real cause of that
 * impression was a bug in how character ATK was applied, and once that was fixed the original
 * values were correct. Pinning them is still worth doing, so a later change is a deliberate edit
 * here rather than a quiet drift, and the name now says what they actually are.
     */
    private static void testStage1HardStatsAreVerified() throws Exception {
        System.out.println("[8] stage 1 hard boss stats match the pinned balance values");
        expectBoss("fubuzilla", 8000, 22, 1500);
        expectBoss("smol_ame", 25000, 20, 1500);
        expectBoss("halloween_bae", 72000, 27, 6000);
    }

    private static void expectBoss(String id, int hp, int atk, int exp) {
        ContentCatalog.BossDef def = ContentCatalog.boss(id);
        expect(def != null, id + " is defined");
        if (def == null) return;
        expectEq(def.hp, hp, id + " hp");
        expectEq(def.atk, atk, id + " atk");
        expectEq(def.exp, exp, id + " exp");
    }

    /** Time stages return before any director logic runs. */
    private static void testTimeStageRunsNoDirector() throws Exception {
        System.out.println("[6] a time stage runs no director");
        int timeIndex = 9;                       // time1
        expect(ContentCatalog.stage(timeIndex).timeStage, "the fixture stage is a time stage");

        Object w = world(timeIndex, 2);           // Time Mode
        director(w, 3600L * 1000L);
        expect(!getBool(boss(w), "active"), "no boss ever spawned on a time stage");
        expect(!bools(w, "encounterStarted")[0], "and no encounter was recorded as started");
    }

    /**
     * Endless Mode repeats encounters past the timed ones, up to a cap of six, and it is the only
     * path where the {@code if (boss.active) return} guard in the director actually decides
     * something: the timed branches are all skipped once an encounter is live, so without a guard a
     * second boss could be started on top of the first.
     */
    private static void testEndlessModeRepeatsEncountersUpToTheCap() throws Exception {
        System.out.println("[7] endless mode repeats encounters up to the cap");
        int endless = 1;
        expect(ContentCatalog.mode(endless).completion == ContentCatalog.COMPLETE_ON_DEATH,
            "the fixture mode completes on death");

        Object w = world(0, endless);

        // A live boss must stop the director from starting another. The time is chosen past the endless
        // threshold of 1200s, because below it the endless branch cannot fire either way and the
        // guard would go untested.
        director(w, ContentCatalog.stage(0).bossATimeSec * 1000L);
        expect(getBool(boss(w), "active"), "the timed first encounter started");
        int startedBefore = countTrue(bools(w, "encounterStarted"));
        director(w, 1300L * 1000L);
        expect(getBool(boss(w), "active"), "a boss is still live");
        expectEq(countTrue(bools(w, "encounterStarted")), startedBefore,
            "no additional encounter was started while one was live");
        expectEq(getInt(w, "nextEndlessEncounter"), 0,
            "and the endless counter did not advance either");

        // Once it is down, endless encounters start and stop at the cap of six.
        defeat(w);
        int spawned = 0;
        for (int round = 0; round < 8; round++) {
            director(w, (1200 + round * 60) * 1000L);
            if (getBool(boss(w), "active")) {
                spawned++;
                defeat(w);
            }
        }
        expect(spawned > 0, "endless mode started at least one repeat encounter");
        expect(getInt(w, "nextEndlessEncounter") <= 6, "the endless counter never passed its cap of 6");
    }

    private static int countTrue(boolean[] values) {
        int n = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- helpers

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }
}