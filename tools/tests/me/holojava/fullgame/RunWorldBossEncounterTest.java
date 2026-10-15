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
 *   <li>Victory requires the stage's <em>final</em> encounter: slot 2 on a hard stage, slot 1
 *       otherwise. Clearing earlier encounters is not enough.</li>
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

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testBossWaitsForItsTimeGate();
        testEncounterIsNeverStartedTwice();
        testDefeatRecordsTheEncounter();
        testVictoryNeedsTheFinalEncounter();
        testHardStageNeedsItsThirdEncounter();
        testTimeStageRunsNoDirector();
        testEndlessModeRepeatsEncountersUpToTheCap();

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

    /** A hard stage needs slot 2, so clearing only the first two must not win it. */
    private static void testHardStageNeedsItsThirdEncounter() throws Exception {
        System.out.println("[5] a hard stage needs its third encounter");
        int hardIndex = 5;                       // stage1h: hard = true
        expect(ContentCatalog.stage(hardIndex).hard, "the fixture stage is a hard variant");

        Object w = world(hardIndex, 0);
        director(w, ContentCatalog.stage(hardIndex).bossATimeSec * 1000L);
        defeat(w);
        director(w, ContentCatalog.stage(hardIndex).bossBTimeSec * 1000L);
        defeat(w);

        expect(!getBool(w, "won"), "two encounters are not enough on a hard stage");
        expect(!bools(w, "encounterDefeated")[2], "the third encounter was never fought");

        director(w, 1800L * 1000L);
        expect(getBool(boss(w), "active"), "the third encounter appears at the hard gate");
        defeat(w);
        expect(getBool(w, "won"), "and beating it wins the hard stage");
        expect(bools(w, "encounterDefeated")[2], "the third encounter is recorded");
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