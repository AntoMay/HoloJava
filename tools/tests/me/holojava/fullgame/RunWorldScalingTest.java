package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers time-driven scaling: the {@code scaleA}/{@code scaleB} formula, its update schedule, and
 * every consumer — enemy HP, ATK and speed, and the boss variants.
 *
 * <p>Parity with the reference JAR was established first, from bytecode. The reference's
 * {@code updateScaling} is structurally identical: {@code currentTimeMillis() - startTime}, minute
 * and hour divisors {@code 60000} and {@code 3600000}, {@code Math.max(0.0, minuteTimer - 23)} plus
 * {@code 37.0 * hourTimer}, and a {@code scaleB} of {@code (elapsed - 1800000) / 60000.0} past thirty
 * minutes plus {@code 60.0 * hourTimer}. The schedule gate in {@code update} is also identical:
 * {@code now - startTime > 120000} and {@code lastMinuteScaled != now / 60000}.
 *
 * <p>Two things are worth stating plainly, because they look like bugs and are not:
 * <ul>
 *   <li>{@code updateScaling} <b>recomputes</b> {@code scaleA} and {@code scaleB} from the clock
 *       rather than accumulating, so calling it repeatedly cannot inflate the values.</li>
 *   <li>Boss stats use a different shape from enemy stats: a truncating cast rather than
 *       {@code roundEven}, and a bespoke speed formula rather than {@code scaledEnemySpeed}. Both
 *       are confirmed in the reference.</li>
 * </ul>
 *
 * <p>{@code updateScaling} reads the system clock itself, so elapsed times are produced by moving
 * {@code startTime} into the past. Thresholds are asserted with at least a second of margin.
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldScalingTest {

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

    private static double getDouble(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getDouble(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static final long MINUTE = 60000L;
    private static final long HOUR = 3600000L;

    private static Object world(long elapsedMillis) throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "save", null);
        set(w, "random", new Random(3L));
        set(w, "state", Integer.valueOf(0));
        set(w, "startTime", Long.valueOf(System.currentTimeMillis() - elapsedMillis));
        set(w, "lastMinuteScaled", Long.valueOf(-1L));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "viewWidth", Integer.valueOf(320));
        set(w, "viewHeight", Integer.valueOf(240));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "baseMaxHp", Integer.valueOf(100));
        set(w, "scaleA", Double.valueOf(0.0));
        set(w, "scaleB", Double.valueOf(0.0));
        set(w, "nextSpawnAt", Long.valueOf(Long.MAX_VALUE));
        set(w, "nextWaveAt", Long.valueOf(0L));
        set(w, "ringEventDone", Boolean.TRUE);
        set(w, "hordeEventDone", Boolean.TRUE);
        set(w, "bossEncounterIndex", Integer.valueOf(-1));
        set(w, "encounterStarted", new boolean[4]);
        set(w, "encounterDefeated", new boolean[4]);
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "offers", filled("me.holojava.fullgame.RunWorld$Offer", 6));
        set(w, "pickups", filled("me.holojava.fullgame.RunWorld$Pickup", 96));
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 48));
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
        set(w, "melee", filled("me.holojava.fullgame.RunWorld$Melee", 24));
        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
        set(boss, "defIndex", Integer.valueOf(0));
        set(boss, "x", Integer.valueOf(0));
        set(boss, "y", Integer.valueOf(0));
        set(boss, "hp", Integer.valueOf(0));
        set(boss, "maxHp", Integer.valueOf(0));
        set(boss, "hitMask", Long.valueOf(0L));
        set(w, "boss", boss);
        return w;
    }

    private static Object filled(String className, int count) throws Exception {
        Class<?> c = Class.forName(className);
        Object array = Array.newInstance(c, count);
        for (int i = 0; i < count; i++) {
            Object e = allocate(c);
            try {
                Field active = c.getDeclaredField("active");
                active.setAccessible(true);
                active.setBoolean(e, false);
            } catch (NoSuchFieldException ignored) {
                // no flag to default
            }
            Array.set(array, i, e);
        }
        return array;
    }

    private static void scale(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateScaling");
        m.setAccessible(true);
        m.invoke(w);
    }

    private static int scaledHp(Object w, int base) throws Exception {
        Method m = worldClass().getDeclaredMethod("scaledEnemyHp", int.class);
        m.setAccessible(true);
        return ((Integer) m.invoke(w, Integer.valueOf(base))).intValue();
    }

    private static int scaledAtk(Object w, int base) throws Exception {
        Method m = worldClass().getDeclaredMethod("scaledEnemyAtk", int.class);
        m.setAccessible(true);
        return ((Integer) m.invoke(w, Integer.valueOf(base))).intValue();
    }

    private static double scaledSpeed(Object w, double base) throws Exception {
        Method m = worldClass().getDeclaredMethod("scaledEnemySpeed", double.class);
        m.setAccessible(true);
        return ((Double) m.invoke(w, Double.valueOf(base))).doubleValue();
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testScalingIsZeroEarlyInARun();
        testScaleAStartsAtMinuteTwentyFour();
        testScaleBStartsAtMinuteThirty();
        testScalingRecomputesRatherThanAccumulates();
        testEnemyStatsGrowWithScalingAndRespectClamps();
        testBossStatsUseTheirOwnForm();
        testSpawnedEnemiesInheritCurrentScaling();
        testScheduleGateDelaysTheFirstUpdate();
        testNewRunDoesNotInheritScaling();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Before minute 23 the max() clamps scaleA to zero, and scaleB is still zero too. */
    private static void testScalingIsZeroEarlyInARun() throws Exception {
        System.out.println("[1] scaling is zero early in a run");
        Object w = world(10L * MINUTE);
        scale(w);
        expectNear(getDouble(w, "scaleA"), 0.0, 0.001, "scaleA at ten minutes");
        expectNear(getDouble(w, "scaleB"), 0.0, 0.001, "scaleB at ten minutes");

        Object later = world(22L * MINUTE + 30000L);
        scale(later);
        expectNear(getDouble(later, "scaleA"), 0.0, 0.001, "scaleA at twenty-two and a half minutes");
    }

    /** scaleA becomes positive at minute 24 and grows by one per minute thereafter. */
    private static void testScaleAStartsAtMinuteTwentyFour() throws Exception {
        System.out.println("[2] scaleA starts at minute twenty-four");
        Object a = world(24L * MINUTE);
        scale(a);
        expectNear(getDouble(a, "scaleA"), 1.0, 0.02, "scaleA at minute 24");

        Object b = world(30L * MINUTE);
        scale(b);
        expectNear(getDouble(b, "scaleA"), 7.0, 0.02, "scaleA at minute 30");

        // minuteTimer counts TOTAL minutes, not minutes past 23, so at 60 minutes the first term
        // is 60 - 23 = 37 and the hour term adds another 37.
        Object c = world(60L * MINUTE);
        scale(c);
        expectNear(getDouble(c, "scaleA"), 37.0 + 37.0, 0.05,
            "scaleA at minute 60, where the hour term begins");

        Object d = world(2L * HOUR);
        scale(d);
        expectNear(getDouble(d, "scaleA"), 97.0 + 74.0, 0.1,
            "scaleA at two hours: 120 minutes past 23 plus 37 per hour");
    }

    /** scaleB is zero until minute 30, then counts minutes past thirty at full precision. */
    private static void testScaleBStartsAtMinuteThirty() throws Exception {
        System.out.println("[3] scaleB starts at minute thirty");
        Object before = world(29L * MINUTE);
        scale(before);
        expectNear(getDouble(before, "scaleB"), 0.0, 0.001, "scaleB just before minute 30");

        Object at = world(30L * MINUTE);
        scale(at);
        expectNear(getDouble(at, "scaleB"), 0.0, 0.05, "scaleB exactly at minute 30");

        Object after = world(35L * MINUTE);
        scale(after);
        expectNear(getDouble(after, "scaleB"), 5.0, 0.05, "scaleB at minute 35 is five minutes past thirty");

        Object hourly = world(2L * HOUR);
        scale(hourly);
        expectNear(getDouble(hourly, "scaleB"), 90.0 + 120.0, 0.1,
            "scaleB at two hours: ninety minutes past thirty plus 60 per hour");
    }

    /** Recomputing must be idempotent, not cumulative. */
    private static void testScalingRecomputesRatherThanAccumulates() throws Exception {
        System.out.println("[4] scaling recomputes rather than accumulates");
        Object w = world(45L * MINUTE);
        scale(w);
        double firstA = getDouble(w, "scaleA");
        double firstB = getDouble(w, "scaleB");
        for (int i = 0; i < 25; i++) scale(w);
        expectNear(getDouble(w, "scaleA"), firstA, 0.0001, "scaleA is unchanged after 25 recomputes");
        expectNear(getDouble(w, "scaleB"), firstB, 0.0001, "scaleB is unchanged after 25 recomputes");
    }

    /** Enemy HP, ATK and speed all rise with scaling, and none escapes its clamp. */
    private static void testEnemyStatsGrowWithScalingAndRespectClamps() throws Exception {
        System.out.println("[5] enemy stats grow with scaling and respect their clamps");
        Object early = world(0L);
        Object late = world(4L * HOUR);
        scale(early);
        scale(late);

        int hpEarly = scaledHp(early, 100);
        int hpLate = scaledHp(late, 100);
        expect(hpLate > hpEarly, "enemy HP grew with scaling (" + hpEarly + " -> " + hpLate + ")");
        expect(hpLate <= 100000, "enemy HP stayed inside the 100000 cap");
        expect(hpLate >= 1, "and above the floor of 1");

        int atkEarly = scaledAtk(early, 10);
        int atkLate = scaledAtk(late, 10);
        expect(atkLate > atkEarly, "enemy ATK grew (" + atkEarly + " -> " + atkLate + ")");
        expect(atkLate <= 1000, "enemy ATK stayed inside the 1000 cap");

        double spEarly = scaledSpeed(early, 1.0);
        double spLate = scaledSpeed(late, 1.0);
        expect(spLate > spEarly, "enemy speed grew (" + spEarly + " -> " + spLate + ")");
        expect(spLate <= 3.0, "enemy speed stayed inside the 3.0 cap");
        expect(spEarly >= 0.1, "and above the floor of 0.1");

        // A tiny base must not collapse to zero.
        expect(scaledHp(late, 1) >= 1, "a one-point base still yields at least 1 HP");
        expect(scaledAtk(late, 1) >= 1, "a one-point base still yields at least 1 ATK");

        // Isolate the scaleA term: between minute 24 and minute 30 scaleB is still zero, so any
        // growth there can only come from scaleA.
        Object windowA = world(26L * MINUTE);
        Object windowB = world(24L * MINUTE);
        scale(windowA);
        scale(windowB);
        expectNear(getDouble(windowA, "scaleB"), 0.0, 0.001, "scaleB is still zero at minute 26");
        expect(getDouble(windowA, "scaleA") > 0.0, "scaleA has started at minute 26");
        expect(scaledAtk(windowA, 10) > scaledAtk(windowB, 10),
            "ATK rose on scaleA alone while scaleB was still zero");
        expect(scaledSpeed(windowA, 1.0) > scaledSpeed(windowB, 1.0),
            "and so did speed");
    }

    /**
     * Boss stats are computed separately: the same power shape as enemy HP under the same 100000
     * ceiling, but a bespoke speed of 0.55 plus a small scaleA term rather than scaledEnemySpeed.
     *
     * <p>The HP ceiling used to be a bespoke 20000, which pinned every scaled spawn and every
     * catalog boss above 20000 to one durability; it now shares the enemy cap.
     */
    private static void testBossStatsUseTheirOwnForm() throws Exception {
        System.out.println("[6] boss stats use their own formulas");
        Object early = world(0L);
        Object late = world(4L * HOUR);
        scale(early);
        scale(late);
        double scaleA = getDouble(late, "scaleA");

        spawnBoss(early, 0);
        spawnBoss(late, 0);
        Object bossEarly = boss(early);
        Object bossLate = boss(late);

        // Unclamped growth first: at 4h the scaled value blows past any cap, so this only proves
        // the formula rises, not that the field is capped.
        expect(getInt(bossLate, "maxHp") > getInt(bossEarly, "maxHp"),
            "boss HP grew with scaling before the clamp");
        expect(getInt(bossLate, "atk") > getInt(bossEarly, "atk"), "boss ATK grew with scaling");

        double expectedSpeed = 0.55 + Math.min(0.5, scaleA * 0.005);
        expectNear(getDouble(bossLate, "speed"), expectedSpeed, 1e-9,
            "boss speed follows 0.55 + min(0.5, scaleA * 0.005), not the enemy formula");
        expect(getDouble(bossLate, "speed") <= 1.05, "boss speed is capped at 0.55 + 0.5");

        // Clamp the scaling to a small value so the scaled HP lands below the ceiling and the
        // exact formula is observable end to end.
        ContentCatalog.BossDef def = ContentCatalog.BOSSES[0];
        Object mid = world(10L);
        set(mid, "scaleA", Double.valueOf(2.0));
        set(mid, "scaleB", Double.valueOf(0.0));
        spawnBoss(mid, 0);
        double exact = MathUtil.power(def.hp + def.hp * 0.05 * 2.0, 1.0);
        expectEq(getInt(boss(mid), "maxHp"), (int) Math.floor(exact + 0.5),
            "below the ceiling, boss HP is the round-even of the exact scaled value"
                + " (16411.6 rounds to an even 16412)");
        expect(getInt(boss(mid), "maxHp") < 100000, "the scaled value is well under the cap");

        // The cap holds for an enormous run, and it is the enemy ceiling rather than a bespoke one.
        Object huge = world(4L * HOUR);
        scale(huge);
        spawnBoss(huge, 0);
        expectEq(getInt(boss(huge), "maxHp"), 100000,
            "boss HP is clamped to 100000, the same ceiling as enemy HP");
    }

    private static Object boss(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("boss");
        f.setAccessible(true);
        return f.get(w);
    }

    private static void spawnBoss(Object w, int encounter) throws Exception {
        Method m = worldClass().getDeclaredMethod("spawnBoss", int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(encounter));
    }

    /** An enemy spawned after scaling already advanced must carry the scaled statistics. */
    private static void testSpawnedEnemiesInheritCurrentScaling() throws Exception {
        System.out.println("[7] spawned enemies inherit the current scaling");
        ContentCatalog.EnemyDef def = ContentCatalog.ENEMIES[0];
        int baseHp = def.hp;

        Object early = world(0L);
        scale(early);
        spawnEnemy(early, 5000L);
        int hpEarly = firstEnemyHp(early);

        Object late = world(4L * HOUR);
        scale(late);
        spawnEnemy(late, 5000L);
        int hpLate = firstEnemyHp(late);

        expect(hpEarly == scaledHp(early, baseHp), "the early spawn used the early scale");
        expect(hpLate == scaledHp(late, baseHp), "the late spawn used the late scale");
        expect(hpLate > hpEarly, "so a late spawn is tougher than an early one");
    }

    private static void spawnEnemy(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("spawnEnemy", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static int firstEnemyHp(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("enemies");
        f.setAccessible(true);
        Object[] pool = (Object[]) f.get(w);
        for (int i = 0; i < pool.length; i++) {
            Field a = pool[i].getClass().getDeclaredField("active");
            a.setAccessible(true);
            if (a.getBoolean(pool[i])) return getInt(pool[i], "maxHp");
        }
        return -1;
    }

    /**
     * The schedule gate: nothing scales in the first two minutes, and within one wall-clock minute
     * the update is skipped even once the run is long enough.
     */
    private static void testScheduleGateDelaysTheFirstUpdate() throws Exception {
        System.out.println("[8] the schedule gate delays the first update");
        long now = System.currentTimeMillis();

        // Under two minutes: the gate blocks it entirely.
        Object fresh = world(60000L);
        set(fresh, "lastMinuteScaled", Long.valueOf(-1L));
        set(fresh, "scaleA", Double.valueOf(0.0));
        runUpdate(fresh, now);
        expectEq(getLong(fresh, "lastMinuteScaled"), -1L,
            "no scaling ran inside the first two minutes");
        expectNear(getDouble(fresh, "scaleA"), 0.0, 0.001, "scaleA untouched");

        // Past two minutes but already stamped for this minute: still skipped. The run is 30 minutes
        // long so that a spurious extra update would produce a non-zero scaleA and be visible.
        Object stamped = world(30L * MINUTE);
        long thisMinute = now / 60000L;
        set(stamped, "lastMinuteScaled", Long.valueOf(thisMinute));
        set(stamped, "scaleA", Double.valueOf(0.0));
        runUpdate(stamped, now);
        expectNear(getDouble(stamped, "scaleA"), 0.0, 0.001,
            "a run stamped for this minute does not rescale");

        // Past two minutes and unstamped: it runs, and records the current minute.
        Object due = world(10L * MINUTE);
        set(due, "lastMinuteScaled", Long.valueOf(-1L));
        set(due, "scaleA", Double.valueOf(0.0));
        runUpdate(due, now);
        expectEq(getLong(due, "lastMinuteScaled"), thisMinute, "the run stamped the current minute");
        expect(getDouble(due, "scaleA") >= 0.0, "and scaling ran");
    }

    private static void runUpdate(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("update", int.class, boolean.class,
                boolean.class, boolean.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(0), Boolean.FALSE, Boolean.FALSE, Boolean.FALSE,
                Long.valueOf(now));
    }

    /** A brand new run starts from zero scaling and an unstamped schedule. */
    private static void testNewRunDoesNotInheritScaling() throws Exception {
        System.out.println("[9] a new run does not inherit scaling");
        Object old = world(5L * HOUR);
        scale(old);
        expect(getDouble(old, "scaleA") > 0.0, "the long run had scaling applied");

        Object fresh = world(0L);
        expectNear(getDouble(fresh, "scaleA"), 0.0, 0.001, "a new run starts at scaleA 0");
        expectNear(getDouble(fresh, "scaleB"), 0.0, 0.001, "and scaleB 0");
        expectEq(getLong(fresh, "lastMinuteScaled"), -1L, "and an unstamped schedule");

        scale(fresh);
        expectNear(getDouble(fresh, "scaleA"), 0.0, 0.001,
            "scaling a brand new run leaves it at zero");
        expectEq(scaledHp(fresh, 100), scaledHp(world(0L), 100),
            "and its enemies carry unscaled base statistics");
    }

    // ---------------------------------------------------------------- helpers

    private static void expectNear(double got, double want, double tolerance, String label) {
        expect(Math.abs(got - want) <= tolerance,
            label + ": expected " + want + " +/- " + tolerance + " got " + got);
    }

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