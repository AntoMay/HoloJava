package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Drives the real {@link RunWorld#collectPickup} coin branch to prove a collected coin reaches
 * permanent storage exactly once.
 *
 * <p><b>Why this file is built the way it is.</b> {@code RunWorld} cannot be constructed on a
 * desktop JVM: its constructor calls {@code AssetLibrary.prepareStage}, which reaches
 * {@code Image.createImage}, and the pinned MIDP stub throws {@code Error("API Stub has been
 * used")} — an {@code Error}, which the library's {@code catch (Exception)} does not catch. That
 * was confirmed directly, not assumed.
 *
 * <p>So the instance is allocated <em>without</em> running the constructor and the fields the coin
 * branch actually touches are set reflectively. The method under test is the genuine production
 * bytecode, not a re-implementation: for a type-1 pickup it reads only {@code p.active},
 * {@code p.type}, {@code p.value}, {@code coins} and {@code save}, all of which are set here.
 *
 * <p><b>What this does not prove.</b> Nothing about the magnet, collision, or pickup-spawning code
 * that reaches this method, nor the rendered result. Those need an emulator or a handset.
 */
public final class RunWorldCoinWiringTest {

    private static int checks = 0;
    private static int failures = 0;

    /** Allocates an object of the given class without invoking any constructor. */
    private static Object allocate(Class<?> type) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
            return allocateInstance.invoke(unsafe, type);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot allocate " + type.getName() + " without a"
                    + " constructor; sun.misc.Unsafe is unavailable", t);
        }
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

    private static int getInt(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static Object newPickup(int type, int value) throws Exception {
        Object p = allocate(pickupClass());
        set(p, "type", Integer.valueOf(type));
        set(p, "value", Integer.valueOf(value));
        set(p, "active", Boolean.TRUE);
        return p;
    }

    private static Class<?> pickupClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Pickup");
    }

    private static Class<?> runWorldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    /** A RunWorld bound to a given store, with only the coin-branch fields initialised. */
    private static Object worldWith(SaveStore save) throws Exception {
        Object world = allocate(runWorldClass());
        set(world, "save", save);
        set(world, "coins", Integer.valueOf(0));
        return world;
    }

    private static void collect(Object world, Object pickup, long now) throws Exception {
        Method m = runWorldClass().getDeclaredMethod("collectPickup", pickupClass(),
                long.class);
        m.setAccessible(true);
        m.invoke(world, pickup, Long.valueOf(now));
    }

    public static void main(String[] args) throws Exception {
        testCollectedCoinReachesPermanentBalance();
        testRunCounterAndPermanentBalanceBothAdvance();
        testInactivePickupIsSkippedByTheUpdateLoop();
        testRefusedWriteStillCountsForTheRun();
        testNullStoreDoesNotBreakTheRun();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---- in-memory store, so the test observes committed bytes --------------------

    private static final class Recording implements SaveStore.RecordStorage {
        byte[] record;
        boolean failWrites;
        int writes;

        Recording() {
            record = new byte[56];
        }

        public byte[] read(int recordId) {
            return record.clone();
        }

        public boolean write(int recordId, byte[] payload) {
            writes++;
            if (failWrites) return false;
            record = payload.clone();
            return true;
        }
    }

    // ---- 1. The pickup is committed --------------------------------------------

    private static void testCollectedCoinReachesPermanentBalance() throws Exception {
        System.out.println("[1] a collected coin reaches the permanent balance");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object world = worldWith(save);

        Object pickup = newPickup(1, 10);
        collect(world, pickup, 1000L);

        expectEq(save.coinBalance(), 10L, "one 10-coin pickup is credited exactly once");
        expectEq(SaveStore.readCoin(store.record, 48), 10L, "and the balance is genuinely persisted");
        expectEq(store.writes, 1, "exactly one record write was issued");
    }

    // ---- 2. Run counter and permanent balance are separate ------------------------

    /**
     * The run counter feeds the HUD and the results screen; the stored balance survives the run.
     * They must both advance, and only the stored one may persist.
     */
    private static void testRunCounterAndPermanentBalanceBothAdvance() throws Exception {
        System.out.println("[2] run counter and permanent balance both advance");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object world = worldWith(save);

        collect(world, newPickup(1, 10), 1000L);
        collect(world, newPickup(1, 7), 1100L);

        expectEq(getInt(world, "coins"), 17, "the run counter sums both pickups");
        expectEq(save.coinBalance(), 17L, "the permanent balance sums both pickups");
    }

    // ---- 3. One pickup cannot be committed twice ---------------------------------

    /**
     * The guard is in the update loop, not in {@code collectPickup}: it skips inactive pickups.
     * This drives the real loop with an already-collected pickup still in the pool, which is the
     * situation that would double-count if the guard were missing.
     */
    private static void testInactivePickupIsSkippedByTheUpdateLoop() throws Exception {
        System.out.println("[3] an already-collected pickup is skipped");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object world = worldWith(save);

        Object pickup = newPickup(1, 10);
        collect(world, pickup, 1000L);
        expectEq(save.coinBalance(), 10L, "collected once");

        // Put the spent pickup back in a pool and run the real update loop over it.
        // Arrays cannot come from allocateInstance, so build the pool the ordinary way.
        Object pool = java.lang.reflect.Array.newInstance(pickupClass(), 1);
        java.lang.reflect.Array.set(pool, 0, pickup);
        set(world, "pickups", pool);
        set(world, "pickupPercentBonus", Integer.valueOf(0));
        set(world, "px", Integer.valueOf(pickupX(pickup)));
        set(world, "py", Integer.valueOf(pickupY(pickup)));

        updatePickups(world, 1100L);
        expectEq(save.coinBalance(), 10L, "the spent pickup did not credit a second time");
    }

    private static int pickupX(Object pickup) throws Exception {
        Field f = pickupClass().getDeclaredField("x");
        f.setAccessible(true);
        return f.getInt(pickup);
    }

    private static int pickupY(Object pickup) throws Exception {
        Field f = pickupClass().getDeclaredField("y");
        f.setAccessible(true);
        return f.getInt(pickup);
    }

    private static void updatePickups(Object world, long now) throws Exception {
        Method m = runWorldClass().getDeclaredMethod("updatePickups", long.class);
        m.setAccessible(true);
        m.invoke(world, Long.valueOf(now));
    }

    // ---- 4. A refused write must not break the run -------------------------------

    /**
     * Persistence is optional; the run is not. The run counter counts the coin either way, and
     * under the coin-write debounce the in-memory balance keeps it too: a store that refuses
     * costs nothing but the disk copy, which stays where it was until a later write lands.
     */
    private static void testRefusedWriteStillCountsForTheRun() throws Exception {
        System.out.println("[4] a refused write still counts for the run");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object world = worldWith(save);

        collect(world, newPickup(1, 10), 1000L);
        store.failWrites = true;
        collect(world, newPickup(1, 10), 1100L);

        expectEq(getInt(world, "coins"), 20, "the run counter advanced regardless");
        expectEq(save.coinBalance(), 20L,
            "the balance kept both coins while storage refused");
        expectEq(SaveStore.readCoin(store.record, 48), 10L, "the bytes on disk still say 10");

        expect(!save.flush(), "flush reports the refusal");
        expectEq(SaveStore.readCoin(store.record, 48), 10L,
            "and a refused flush touches nothing");

        store.failWrites = false;
        expect(save.flush(), "the next flush lands the pending coin");
        expectEq(SaveStore.readCoin(store.record, 48), 20L,
            "and the record ends up holding both coins");
    }

    // ---- 5. A run without persistence must not crash -----------------------------

    private static void testNullStoreDoesNotBreakTheRun() throws Exception {
        System.out.println("[5] a run without a store still collects");
        Object world = worldWith(null);
        collect(world, newPickup(1, 10), 1000L);
        expectEq(getInt(world, "coins"), 10, "coins are still counted with no store attached");
    }

    // ---- helpers ----------------------------------------------------------------

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