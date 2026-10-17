package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the pickup magnet: the aura that pulls a pickup in, the touch radius that collects it,
 * the pickup bonus that widens the aura, and the EXP Magnet (pickup type 3) vacuum.
 *
 * <p><b>The defect this pins.</b> The aura radius and the collection radius used to be the same
 * 22 px circle, so a pickup was collected on the very tick it entered the aura and the pull never
 * showed: pickups behaved as if they had to be walked into. That is the "the magnet does not
 * work" report. The two radii are separate now — {@link RunWorld#MAGNET_BASE_RADIUS} pulls,
 * {@link RunWorld#PICKUP_TOUCH_RADIUS} collects — so a pickup inside the aura must visibly travel
 * toward the player over several ticks before it is collected.
 *
 * * <p>The EXP Magnet (type 3, dropped 1/3000) also had no branch in {@code collectPickup}: it was
 * drawn in from anywhere and then discarded with no effect. Per the wiki it vacuums all EXP and
 * HoloCoins in a large area, so collecting it must open a vacuum window in which every pickup is
 * pulled from anywhere on the field.
 *
 * <p>Construction follows the other suites: {@code RunWorld} cannot be built headlessly, so it is
 * allocated without its constructor via {@code sun.misc.Unsafe} and the fields these paths read are
 * set reflectively. The methods under test are the production bytecode.
 *
 * <p><b>What this does not prove.</b> Rendering of pickups, the drop chances, EXP merging, and the
 * feel of the numbers at 60 fps on a device — the radii here are pinned as values, not judged.
 */
public final class RunWorldPickupMagnetTest {

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

    public static void main(String[] args) throws Exception {
        testAuraPullsWithoutCollecting();
        testPickupOutsideTheAuraStaysPut();
        testTouchRadiusCollects();
        testPulledPickupConvergesAndCollects();
        testBonusWidensTheAura();
        testMagnetPickupIsPulledFromAnywhere();
        testMagnetPickupOpensAVacuum();
        testVacuumPullsEveryPickupFromAnywhere();
        testVacuumExpires();
        testRunResetClearsTheVacuum();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ------------------------------------------------------------------ tests

    /**
     * The heart of the fix: a coin inside the aura but outside the touch radius moves toward the
     * player and survives the tick. Before the fix it was collected on this tick, because the two
     * radii were the same circle.
     */
    private static void testAuraPullsWithoutCollecting() throws Exception {
        System.out.println("[1] the aura pulls a pickup in without collecting it");
        Object w = world();
        // Player center is (142,107). This coin's center is 34 px away: inside the 56 px aura,
        // outside the 16 px touch radius.
        Object coin = place(w, 1, 10, 170, 101, 1000L);

        updatePickups(w, 1016L);

        expect(getInt(coin, "x") == 167, "the coin moved 3 px toward the player (x="
            + getInt(coin, "x") + ", expected 167)");
        expect(getBool(coin, "active"), "the coin is still on the field after being pulled");
        expectEq(getInt(w, "coins"), 0, "no coin was collected by the pull itself");
    }

    /** The aura has an edge: beyond it a pickup is untouched, or it would be a screen-wide vacuum. */
    private static void testPickupOutsideTheAuraStaysPut() throws Exception {
        System.out.println("[2] a pickup outside the aura stays put");
        Object w = world();
        // Center 64 px from the player: outside the 56 px aura.
        Object coin = place(w, 1, 10, 200, 101, 1000L);

        updatePickups(w, 1016L);

        expectEq(getInt(coin, "x"), 200, "a pickup beyond the aura does not move");
        expect(getBool(coin, "active"), "and is not collected");
    }

    /** Inside the touch radius a pickup is collected, and only then. */
    private static void testTouchRadiusCollects() throws Exception {
        System.out.println("[3] the touch radius collects");
        Object w = world();
        // Center 10 px from the player: inside the 16 px touch radius.
        place(w, 1, 10, 126, 101, 1000L);

        updatePickups(w, 1016L);

        expectEq(getInt(w, "coins"), 10, "a coin inside the touch radius is collected");
        expectEq(activePickups(w), 0, "and leaves the pool");
    }

    /**
     * The pull phase exists and terminates: a coin 34 px out is collected only after several
     * ticks of travel, not on entry to the aura, and not by oscillating around the player.
     */
    private static void testPulledPickupConvergesAndCollects() throws Exception {
        System.out.println("[4] a pulled pickup converges and is collected");
        Object w = world();
        place(w, 1, 10, 170, 101, 1000L);

        int ticks = 0;
        while (activePickups(w) > 0 && ticks < 40) {
            updatePickups(w, 1016L + ticks * 16L);
            ticks++;
        }

        expectEq(getInt(w, "coins"), 10, "the pulled coin is eventually collected");
        expect(ticks >= 2, "it took several ticks of travel, i.e. a visible pull (ticks=" + ticks
            + ")");
    }

    /** The pickup stat and the pickup items widen the aura rather than the touch radius. */
    private static void testBonusWidensTheAura() throws Exception {
        System.out.println("[5] the pickup bonus widens the aura");
        Object bare = world();
        Object boosted = world();
        set(boosted, "pickupPercentBonus", Integer.valueOf(100));   // aura 56 -> 112 px

        // Center 104 px from the player: outside the bare aura, inside the boosted one.
        Object farBare = place(bare, 1, 10, 240, 101, 1000L);
        Object farBoosted = place(boosted, 1, 10, 240, 101, 1000L);

        updatePickups(bare, 1016L);
        updatePickups(boosted, 1016L);

        expectEq(getInt(farBare, "x"), 240, "without the bonus the pickup is outside the aura");
        expect(getInt(farBoosted, "x") < 240, "with +100% the aura reaches it (x="
            + getInt(farBoosted, "x") + ")");
        // The touch radius is unchanged by the bonus.
        expectEq(RunWorld.PICKUP_TOUCH_RADIUS, 16, "the touch radius stays a fixed 16 px");
    }

    /** The EXP Magnet is drawn in from anywhere on the field, not only from the aura. */
    private static void testMagnetPickupIsPulledFromAnywhere() throws Exception {
        System.out.println("[6] the EXP Magnet is pulled from anywhere");
        Object w = world();
        // Center 166 px from the player, far outside even a boosted aura.
        Object magnet = place(w, 3, 0, 302, 101, 1000L);

        updatePickups(w, 1016L);

        expectEq(getInt(magnet, "x"), 294, "the magnet moves at its fast 8 px pull");
        expect(getBool(magnet, "active"), "and is not collected that far out");
    }

    /** Collecting the EXP Magnet opens the documented large-area vacuum. */
    private static void testMagnetPickupOpensAVacuum() throws Exception {
        System.out.println("[7] the EXP Magnet opens a vacuum");
        Object w = world();
        Object magnet = place(w, 3, 0, 302, 101, 1000L);

        collect(w, magnet, 5000L);

        expectEq(getLong(w, "vacuumUntil"), 5000L + RunWorld.VACUUM_DURATION_MS,
            "collecting the magnet arms a vacuum window");
        expectEq(activePickups(w), 0, "the magnet itself leaves the pool");
    }

    /** While the vacuum runs, every pickup is pulled in from anywhere and collected on arrival. */
    private static void testVacuumPullsEveryPickupFromAnywhere() throws Exception {
        System.out.println("[8] the vacuum pulls every pickup from anywhere");
        Object w = world();
        set(w, "vacuumUntil", Long.valueOf(1016L + RunWorld.VACUUM_DURATION_MS));
        // Center 166 px out: untouched by the aura, but inside the vacuum.
        Object coin = place(w, 1, 10, 302, 101, 1000L);

        updatePickups(w, 1016L);
        expect(getInt(coin, "x") < 302, "the vacuum pulls a far coin in (x=" + getInt(coin, "x")
            + ")");

        // Keep ticking within the window; it must arrive and be collected.
        int ticks = 1;
        while (activePickups(w) > 0 && ticks < 40) {
            updatePickups(w, 1016L + ticks * 16L);
            ticks++;
        }
        expectEq(getInt(w, "coins"), 10, "the vacuumed coin is collected on arrival");
    }

    /** The vacuum is a window, not a permanent state: it expires with the clock. */
    private static void testVacuumExpires() throws Exception {
        System.out.println("[9] the vacuum expires");
        Object w = world();
        set(w, "vacuumUntil", Long.valueOf(5000L));
        Object coin = place(w, 1, 10, 302, 101, 1000L);

        updatePickups(w, 9000L);   // past the window

        expectEq(getInt(coin, "x"), 302, "after the window a far coin is left alone");
        expect(getBool(coin, "active"), "and is not collected");
    }

    /**
     * A new run must not inherit the previous run's vacuum.
     *
     * <p>{@code reset()} cannot be driven headlessly here: it dereferences {@code mode},
     * {@code character}, {@code boss} and several final arrays, which the constructor-less
     * fixture leaves null. It also ends in {@code generateOffers()}. So this pins the wiring
     * directly — the same pattern the other suites use for paths that need a real canvas — by
     * asserting the reset body zeroes {@code vacuumUntil} next to clearing the pickup pool.
     */
    private static void testRunResetClearsTheVacuum() throws Exception {
        System.out.println("[10] a new run clears the vacuum");
        String src = read("src/java-me-fullgame/me/holojava/fullgame/RunWorld.java");
        String resetBody = src.substring(src.indexOf("private void reset(SaveStore save)"),
            src.indexOf("private void reset(SaveStore save)") + 2200);
        int pickupClear = resetBody.indexOf("pickups[i].active = false");
        int vacuumClear = resetBody.indexOf("vacuumUntil = 0L");
        expect(pickupClear >= 0, "reset clears the pickup pool");
        expect(vacuumClear >= 0, "reset zeroes the vacuum window");
        expect(vacuumClear > pickupClear,
            "and does so in the same reset pass that clears the pool");
    }

    private static String read(String path) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(path);
        byte[] data = new byte[16384];
        int n = 0;
        int r;
        while ((r = in.read(data, n, data.length - n)) > 0) n += r;
        in.close();
        return new String(data, 0, n, "ISO-8859-1");
    }

    // ---------------------------------------------------------------- helpers

    /** A world with the player at (130,95), a null store, and an empty pickup pool. */
    private static Object world() throws Exception {
        Object w = allocate(worldClass());
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(480));
        set(w, "px", Integer.valueOf(130));
        set(w, "py", Integer.valueOf(95));
        set(w, "coins", Integer.valueOf(0));
        set(w, "pickupPercentBonus", Integer.valueOf(0));
        set(w, "vacuumUntil", Long.valueOf(0L));
        set(w, "save", null);
        set(w, "state", Integer.valueOf(RunWorld.STATE_RUNNING));
        set(w, "playerLevel", Integer.valueOf(1));
        set(w, "experience", Integer.valueOf(0));
        set(w, "expNeed", Integer.valueOf(999999));   // no level-up loop during these tests
        set(w, "playerHp", Integer.valueOf(50));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "pickups", pool(pickupClass(), 8));
        return w;
    }

    /** Places an active pickup in the pool and returns it. */
    private static Object place(Object w, int type, int value, int x, int y, long born)
            throws Exception {
        Object[] pickups = (Object[]) get(w, "pickups");
        for (int i = 0; i < pickups.length; i++) {
            if (getBool(pickups[i], "active")) continue;
            set(pickups[i], "active", Boolean.TRUE);
            set(pickups[i], "type", Integer.valueOf(type));
            set(pickups[i], "value", Integer.valueOf(value));
            set(pickups[i], "x", Integer.valueOf(x));
            set(pickups[i], "y", Integer.valueOf(y));
            set(pickups[i], "born", Long.valueOf(born));
            return pickups[i];
        }
        throw new IllegalStateException("pickup pool is full");
    }

    private static int activePickups(Object w) throws Exception {
        Object[] pickups = (Object[]) get(w, "pickups");
        int n = 0;
        for (int i = 0; i < pickups.length; i++) if (getBool(pickups[i], "active")) n++;
        return n;
    }

    private static void updatePickups(Object w, long now) throws Exception {
        callVoid(w, "updatePickups", new Class<?>[] { Long.TYPE }, new Object[] { Long.valueOf(now) });
    }

    private static void collect(Object w, Object pickup, long now) throws Exception {
        callVoid(w, "collectPickup", new Class<?>[] { pickupClass(), Long.TYPE },
            new Object[] { pickup, Long.valueOf(now) });
    }

    private static void callVoid(Object target, String method, Class<?>[] sig, Object[] args)
            throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, sig);
        m.setAccessible(true);
        m.invoke(target, args);
    }

    private static Object allocate(Class<?> type) {
        try {
            Method m = unsafe.getClass().getMethod("allocateInstance", Class.class);
            return m.invoke(unsafe, type);
        } catch (Exception e) {
            throw new IllegalStateException("cannot allocate " + type.getName(), e);
        }
    }

    private static Object pool(Class<?> component, int count) {
        Object array = Array.newInstance(component, count);
        for (int i = 0; i < count; i++) Array.set(array, i, allocate(component));
        return array;
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Class<?> pickupClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Pickup");
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
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

    private static int getInt(Object target, String field) {
        return ((Integer) get(target, field)).intValue();
    }

    private static long getLong(Object target, String field) {
        return ((Long) get(target, field)).longValue();
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
