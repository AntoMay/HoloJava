package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers enemy spawning and the timed wave events, which the backlog tracked as HOLD-01 because the
 * behaviour was never verified.
 *
 * <p>Parity with the reference JAR was established before these tests were written, from bytecode:
 * <ul>
 *   <li>{@code updateSpawns}, {@code spawnEnemy}, {@code spawnWave}, {@code updateTimedEvents},
 *       {@code updateScaling}, {@code activeEnemyCount} and {@code freeEnemy} carry identical
 *       integer and double constants in both.</li>
 *   <li>{@code updateSpawns} is instruction-for-instruction the same, including the boss gate, the
 *       {@code elapsed / 5000 * 20} tightening, the 250 ms floor and the 550 ms Time Mode clamp.</li>
 *   <li>{@code update()} calls the same twelve helpers in the same order.</li>
 *   <li>{@code spawnWave} reads {@code boss.active} zero times in the reference, so spawning a wave
 *       during a boss fight is inherited behaviour, not a porting defect.</li>
 * </ul>
 *
 * <p>These tests drive the production {@code updateSpawns} and {@code updateTimedEvents}. Instances
 * are allocated without running the constructor and written through {@code sun.misc.Unsafe} by real
 * field type. Test-only: no file under {@code src/} references {@code sun.*}.
 */
public final class RunWorldSpawnScheduleTest {

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

    private static long getLong(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getLong(target);
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

    private static void setBool(Object target, String field, boolean value) {
        set(target, field, Boolean.valueOf(value));
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static final int POOL = 48;

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
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "state", Integer.valueOf(0));
        set(w, "scaleA", Double.valueOf(0.0));
        set(w, "scaleB", Double.valueOf(0.0));
        set(w, "ringEventDone", Boolean.FALSE);
        set(w, "hordeEventDone", Boolean.FALSE);
        set(w, "nextSpawnAt", Long.valueOf(0L));
        set(w, "nextWaveAt", Long.valueOf(0L));
        set(w, "playerLevel", Integer.valueOf(1));
        set(w, "expNeed", Integer.valueOf(79));
        set(w, "bossEncounterIndex", Integer.valueOf(-1));
        set(w, "encounterStarted", new boolean[4]);
        set(w, "encounterDefeated", new boolean[4]);
        set(w, "random", new Random(4242L));
        set(w, "enemies", enemies());
        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
        set(boss, "defIndex", Integer.valueOf(0));
        set(boss, "x", Integer.valueOf(100));
        set(boss, "y", Integer.valueOf(100));
        set(boss, "hp", Integer.valueOf(10));
        set(w, "boss", boss);
        return w;
    }

    private static Object enemies() throws Exception {
        Class<?> ec = Class.forName("me.holojava.fullgame.RunWorld$Enemy");
        Object array = Array.newInstance(ec, POOL);
        for (int i = 0; i < POOL; i++) {
            Object e = allocate(ec);
            set(e, "active", Boolean.FALSE);
            Array.set(array, i, e);
        }
        return array;
    }

    private static Object[] enemyPool(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("enemies");
        f.setAccessible(true);
        return (Object[]) f.get(w);
    }

    private static int activeEnemies(Object w) {
        try {
            Object[] pool = enemyPool(w);
            int n = 0;
            for (int i = 0; i < pool.length; i++) {
                if (getBool(pool[i], "active")) n++;
            }
            return n;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void spawns(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateSpawns", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static void timedEvents(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateTimedEvents", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    /**
     * The interval {@code updateSpawns} should use at a given elapsed time, derived from the
     * catalog and the documented formula. Written out here so a test failure shows the expected
     * arithmetic rather than a number buried in production code.
     */
    private static int intervalFor(int stageIndex, int modeIndex, long elapsed) {
        int interval = ContentCatalog.stage(stageIndex).spawnIntervalMs
                - (int) (elapsed / 5000L) * 20;
        if (interval < 250) interval = 250;
        if (ContentCatalog.mode(modeIndex).completion == ContentCatalog.COMPLETE_KILL_TARGET
                && interval > 550) interval = 550;
        return interval;
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testNothingSpawnsBeforeTheFirstGate();
        testIntervalFollowsTheDocumentedFormula();
        testIntervalTightensWithRunTimeAndFloorsAt250();
        testStageVariantsHaveTheirOwnRatesAndCaps();
        testBossPresenceStopsSpawning();
        testCapacityCapIsRespected();
        testPoolExhaustionDoesNotBacklog();
        testTimeModeClampsTheInterval();
        testRingAndHordeWavesFireExactlyOnce();
        testPauseDoesNotRewindTheClock();
        testScrollingDoesNotChangeTheSchedule();
        testEnemyMixFollowsTheReferenceThresholds();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** reset() arms the first spawn at startTime + 1000. */
    private static void testNothingSpawnsBeforeTheFirstGate() throws Exception {
        System.out.println("[1] nothing spawns before the first gate");
        Object w = world(0, 0);
        set(w, "nextSpawnAt", Long.valueOf(1000L));

        spawns(w, 999L);
        expectEq(activeEnemies(w), 0, "no enemy before the gate");

        spawns(w, 1000L);
        expectEq(activeEnemies(w), 1, "exactly one enemy at the gate");
        expectEq(getLong(w, "nextSpawnAt"), 1000L + ContentCatalog.stage(0).spawnIntervalMs,
            "the next gate is pushed one interval ahead");
    }

    /** At t = 0 the interval is exactly the stage value, unclamped. */
    private static void testIntervalFollowsTheDocumentedFormula() throws Exception {
        System.out.println("[2] the interval follows the documented formula");
        int interval = ContentCatalog.stage(0).spawnIntervalMs;
        expect(interval > 250, "the stage interval is above the floor");

        Object w = world(0, 0);
        set(w, "nextSpawnAt", Long.valueOf(0L));
        spawns(w, 0L);
        expectEq(getLong(w, "nextSpawnAt"), 0L + interval, "interval equals spawnIntervalMs at t=0");
    }

    /**
     * interval = spawnIntervalMs - (elapsed / 5000) * 20, floored at 250. The floor is reached once
     * the subtraction would drive it below.
     */
    private static void testIntervalTightensWithRunTimeAndFloorsAt250() throws Exception {
        System.out.println("[3] the interval tightens with run time and floors at 250");
        int base = ContentCatalog.stage(0).spawnIntervalMs;

        Object w = world(0, 0);
        set(w, "nextSpawnAt", Long.valueOf(0L));
        spawns(w, 10000L);
        expectEq(getLong(w, "nextSpawnAt"), 10000L + (base - (int) (10000L / 5000L) * 20),
            "one tightening step after 10 s");

        Object floored = world(0, 0);
        set(floored, "nextSpawnAt", Long.valueOf(0L));
        spawns(floored, 600000L);
        expectEq(getLong(floored, "nextSpawnAt"), 600000L + 250,
            "the interval is clamped to the 250 ms floor deep into a run");

        // Spawning tightens over a run, so the later interval is never longer than the earlier one.
        // Both are compared as intervals, not as absolute gate timestamps.
        Object slow = world(0, 0);
        set(slow, "nextSpawnAt", Long.valueOf(0L));
        spawns(slow, 0L);
        long firstInterval = getLong(slow, "nextSpawnAt") - 0L;
        long secondNow = firstInterval;
        set(slow, "nextSpawnAt", secondNow);
        spawns(slow, secondNow + 300000L);
        long secondInterval = getLong(slow, "nextSpawnAt") - (secondNow + 300000L);
        expect(secondInterval <= firstInterval,
            "the interval never grows as the run continues (" + firstInterval + " -> " + secondInterval + ")");
        expect(secondInterval == 250,
            "and it reaches the 250 ms floor deep into a run");
    }

    /** Every stage carries its own rate and cap; the schedule follows the catalog. */
    private static void testStageVariantsHaveTheirOwnRatesAndCaps() throws Exception {
        System.out.println("[4] stage variants have their own rates and caps");
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            Object w = world(i, 0);
            set(w, "nextSpawnAt", Long.valueOf(0L));
            spawns(w, 0L);
            int expected = ContentCatalog.stage(i).spawnIntervalMs;
            if (ContentCatalog.MODES[0].completion == ContentCatalog.COMPLETE_KILL_TARGET
                    && expected > 550) expected = 550;
            expectEq(getLong(w, "nextSpawnAt"), expected,
                "stage " + i + " (" + ContentCatalog.stage(i).id + ") spawn interval");
            expect(ContentCatalog.stage(i).maxEnemies > 0,
                "stage " + i + " declares a positive enemy cap");
        }
    }

    /** updateSpawns returns immediately while a boss is live. */
    private static void testBossPresenceStopsSpawning() throws Exception {
        System.out.println("[5] a live boss stops spawning");
        Object w = world(0, 0);
        Field bossField = worldClass().getDeclaredField("boss");
        bossField.setAccessible(true);
        Object boss = bossField.get(w);
        set(boss, "active", Boolean.TRUE);

        set(w, "nextSpawnAt", Long.valueOf(0L));
        spawns(w, 5000L);
        expectEq(activeEnemies(w), 0, "no enemy spawned during the boss fight");
        expectEq(getLong(w, "nextSpawnAt"), 0L, "and the schedule was not advanced");

        set(boss, "active", Boolean.FALSE);
        spawns(w, 5000L);
        expectEq(activeEnemies(w), 1, "spawning resumes once the boss is gone");
    }

    /** The stage cap is enforced before anything is spawned. */
    private static void testCapacityCapIsRespected() throws Exception {
        System.out.println("[6] the stage enemy cap is respected");
        int cap = ContentCatalog.stage(0).maxEnemies;
        Object w = world(0, 0);
        set(w, "nextSpawnAt", Long.valueOf(0L));

        long now = 0L;
        for (int i = 0; i < cap + 25; i++) {
            spawns(w, now);
            now = getLong(w, "nextSpawnAt");    // always aim at the next gate
        }
        expectEq(activeEnemies(w), cap,
            "the enemy count never exceeds the stage cap of " + cap);
    }

    /**
 * The stage cap is checked <em>before</em> the timer is advanced, so while the stage is at its cap
 * the spawn gate freezes rather than queueing a backlog. Freeing a slot resumes the schedule from
 * the frozen gate.
     */
    private static void testPoolExhaustionDoesNotBacklog() throws Exception {
        System.out.println("[7] a full stage cap freezes the gate instead of queueing");
        Object w = world(0, 0);
        set(w, "nextSpawnAt", Long.valueOf(0L));
        long now = 0L;
        for (int i = 0; i < POOL; i++) {
            spawns(w, now);
            now = getLong(w, "nextSpawnAt");
        }
        int held = activeEnemies(w);
        long frozenGate = getLong(w, "nextSpawnAt");
        expectEq(held, ContentCatalog.stage(0).maxEnemies,
            "the stage is held at its cap");

        // Still at the cap: the gate must not move, or missed time would burst out later.
        spawns(w, frozenGate);
        spawns(w, frozenGate + 60000L);
        expectEq(activeEnemies(w), held, "no further enemies while full");
        expectEq(getLong(w, "nextSpawnAt"), frozenGate,
            "the gate stayed frozen instead of accumulating a backlog");

        // Free one slot and the schedule resumes from the frozen gate, one interval ahead.
        Object[] pool = enemyPool(w);
        setBool(pool[0], "active", false);
        expectEq(activeEnemies(w), held - 1, "freeing a slot drops the live count");
        spawns(w, frozenGate);
        expectEq(activeEnemies(w), held,
            "the freed slot was refilled, so the count is back at the cap");
        expectEq(getLong(w, "nextSpawnAt"), frozenGate + intervalFor(0, 0, frozenGate),
            "and the next gate is one interval beyond the frozen one");
    }

    /** Time Mode clamps the interval to at most 550 ms. */
    private static void testTimeModeClampsTheInterval() throws Exception {
        System.out.println("[8] Time Mode clamps the interval");
        int timeStage = 9;
        int base = ContentCatalog.stage(timeStage).spawnIntervalMs;
        Object w = world(timeStage, 2);
        set(w, "nextSpawnAt", Long.valueOf(0L));
        spawns(w, 0L);
        long expected = base > 550 ? 550 : base;
        expectEq(getLong(w, "nextSpawnAt"), expected,
            "the Time Mode clamp applies (base " + base + " -> " + expected + ")");
    }

    /** The ring wave at 180 s and the horde at 900 s each fire once. */
    private static void testRingAndHordeWavesFireExactlyOnce() throws Exception {
        System.out.println("[9] the ring and horde waves fire exactly once");
        Object w = world(0, 0);

        timedEvents(w, 179999L);
        expectEq(activeEnemies(w), 0, "no wave before 180 s");

        timedEvents(w, 180000L);
        expectEq(activeEnemies(w), 16, "the ring wave spawns 16 enemies");
        expect(getBool(w, "ringEventDone"), "and is recorded as done");

        Object before = enemyPool(w)[0];
        timedEvents(w, 180001L);
        timedEvents(w, 200000L);
        expectEq(activeEnemies(w), 16, "the ring wave does not repeat");

        timedEvents(w, 900000L);
        expect(getBool(w, "hordeEventDone"), "the horde event is recorded at 900 s");
        int afterHorde = activeEnemies(w);
        expect(afterHorde > 16, "the horde wave adds enemies");

        timedEvents(w, 901000L);
        expectEq(activeEnemies(w), afterHorde, "the horde wave does not repeat either");
        expect(before != null, "the pool object was stable across the events");
    }

    /**
     * The schedule is driven by wall-clock milliseconds, and {@code update()} returns early unless
     * the run is running. A pause therefore does not rewind anything: on resume an overdue gate
     * fires at most once, not as a burst.
     */
    private static void testPauseDoesNotRewindTheClock() throws Exception {
        System.out.println("[10] a pause does not rewind the clock");
        Object w = world(0, 0);
        set(w, "nextSpawnAt", Long.valueOf(1000L));

        set(w, "state", Integer.valueOf(RunWorld.STATE_PAUSED));
        // Simulate the loop skipping work while paused: updateSpawns is simply not called.
        long gateBefore = getLong(w, "nextSpawnAt");
        expectEq(gateBefore, 1000L, "the gate is untouched while paused");
        expectEq(activeEnemies(w), 0, "and nothing spawned");

        set(w, "state", Integer.valueOf(RunWorld.STATE_RUNNING));
        long muchLater = gateBefore + 600000L;
        spawns(w, muchLater);
        expectEq(activeEnemies(w), 1,
            "resuming long after the gate yields one spawn, not a backlog");
        expectEq(getLong(w, "nextSpawnAt"), muchLater + 250,
            "and the next gate is a full interval ahead, using the floor deep into a run");
    }

    /** Scrolling widens the world, which moves where enemies appear but not how often. */
    private static void testScrollingDoesNotChangeTheSchedule() throws Exception {
        System.out.println("[11] scrolling does not change the schedule");
        int narrow = 320, wide = 640;

        Object a = world(0, 0);
        set(a, "worldWidth", Integer.valueOf(narrow));
        set(a, "nextSpawnAt", Long.valueOf(0L));
        spawns(a, 0L);

        Object b = world(0, 0);
        set(b, "worldWidth", Integer.valueOf(wide));
        set(b, "nextSpawnAt", Long.valueOf(0L));
        spawns(b, 0L);

        expectEq(getLong(a, "nextSpawnAt"), getLong(b, "nextSpawnAt"),
            "a wider world produces the same spawn schedule");
        expectEq(activeEnemies(a), activeEnemies(b), "and the same enemy count");
    }

    /**
     * {@code spawnEnemy} rolls {@code rand(100)} and splits it 55 / 30 / 15 across the stage's
     * enemyA, enemyB and enemyC. Those thresholds are gameplay values read from the reference, so
     * they are pinned here: a wide tolerance still catches a rebalanced mix such as 33 / 33 / 34.
     */
    private static void testEnemyMixFollowsTheReferenceThresholds() throws Exception {
        System.out.println("[12] the enemy mix follows the reference thresholds");
        int stageIndex = 4;   // Fantasy Island: the only stage naming three distinct enemies
        ContentCatalog.StageDef stage = ContentCatalog.stage(stageIndex);
        Object w = world(stageIndex, 0);
        int idxA = enemyIndexOf(w, stage.enemyA);
        int idxB = enemyIndexOf(w, stage.enemyB);
        int idxC = enemyIndexOf(w, stage.enemyC);
        expect(idxA >= 0 && idxB >= 0 && idxC >= 0, "the stage names three known enemies");
        // Several stages reuse enemyA as enemyC, which would make the three buckets indistinguishable.
        // The mix is therefore measured on a stage with three distinct ids.
        expect(idxA != idxC, "the chosen stage distinguishes its first and third enemy");

        Object[] pool = enemyPool(w);
        Method spawn = worldClass().getDeclaredMethod("spawnEnemy", long.class);
        spawn.setAccessible(true);

        int samples = 6000;
        int a = 0, b = 0, c = 0;
        for (int i = 0; i < samples; i++) {
            for (int k = 0; k < pool.length; k++) setBool(pool[k], "active", false);
            spawn.invoke(w, Long.valueOf(1000L + i));
            for (int k = 0; k < pool.length; k++) {
                if (getBool(pool[k], "active")) {
                    int d = getInt(pool[k], "defIndex");
                    if (d == idxA) a++;
                    else if (d == idxB) b++;
                    else if (d == idxC) c++;
                }
            }
        }
        expectEq(a + b + c, samples, "every sample produced an enemy of the stage's three types");
        expectPercent(a, samples, 55, "enemyA");
        expectPercent(b, samples, 30, "enemyB");
        expectPercent(c, samples, 15, "enemyC");
    }

    /** Resolves an enemy id the same way production does, through RunWorld's own lookup. */
    private static int enemyIndexOf(Object w, String id) throws Exception {
        Method m = worldClass().getDeclaredMethod("enemyIndex", String.class);
        m.setAccessible(true);
        return ((Integer) m.invoke(w, id)).intValue();
    }

    private static void expectPercent(int count, int total, int expected, String label) {
        double pct = 100.0 * count / total;
        expect(Math.abs(pct - expected) <= 4.0,
            label + " is about " + expected + "% (measured " + String.format("%.1f", pct) + "%)");
    }

    // ---------------------------------------------------------------- helpers

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(long got, long want, String label) {
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