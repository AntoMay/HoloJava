package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Proves the stage-clear reward follows design decision C, and that it is granted exactly once.
 *
 * <p>Decision C, clause by clause:
 * <ol>
 *   <li>The victory reward {@code coinClear + defeatCount / 4} is credited to the permanent
 *       balance.</li>
 *   <li>The level-up {@code +100} stays run-local and is never persisted.</li>
 *   <li>A collected pickup lands in the balance at once while its write is debounced; a
 *       refusing store costs it nothing but the disk copy.</li>
 *   <li>Coins never collected are never converted into a permanent balance.</li>
 *   <li>The HUD keeps showing run coins, so the run counter must still advance.</li>
 * </ol>
 *
 * <p><b>Construction.</b> {@code RunWorld} cannot be built on a desktop JVM: its constructor
 * reaches {@code Image.createImage}, whose MIDP stub throws an {@code Error} that the library's
 * {@code catch (Exception)} does not catch (confirmed, not assumed). Instances are allocated
 * without running the constructor and only the fields the exercised methods touch are set. The
 * methods under test are the genuine production bytecode.
 *
 * <p><b>Not proven.</b> Anything requiring spawned assets, rendering, or the real boss encounter
 * flow. Those need an emulator or a handset.
 */
public final class RunWorldClearRewardTest {

    private static int checks = 0;
    private static int failures = 0;

    // ---------------------------------------------------------------- allocation

    private static Object allocate(Class<?> type) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
            return allocateInstance.invoke(theUnsafe.get(null), type);
        } catch (Throwable t) {
            throw new IllegalStateException("cannot allocate " + type.getName(), t);
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

    private static boolean getBool(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getBoolean(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Class<?> pickupClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Pickup");
    }

    // ---------------------------------------------------------------- fixture

    /** An in-memory store that also counts writes, so refusals can be observed. */
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

    private static Object filled(Class<?> component, int count) throws Exception {
        Object array = Array.newInstance(component, count);
        for (int i = 0; i < count; i++) {
            Object element = allocate(component);
            try {
                Field active = component.getDeclaredField("active");
                active.setAccessible(true);
                active.setBoolean(element, false);
            } catch (NoSuchFieldException ignored) {
                // A pool type without an active flag needs no defaulting.
            }
            Array.set(array, i, element);
        }
        return array;
    }

    /**
     * A world on a stage whose completion rule is the final boss, with the boss encounter flagged
     * as the final one defeated, so {@code defeatBoss()} reaches the reward branch.
     */
    private static Object clearedRun(SaveStore save, int stageIndex) throws Exception {
        Object w = allocate(worldClass());
        set(w, "save", save);
        set(w, "random", new Random(999L));
        setInt(w, "worldWidth", 320);
        setInt(w, "worldHeight", 240);
        setInt(w, "stageIndex", stageIndex);
        setInt(w, "coins", 0);
        setInt(w, "kills", 0);
        setInt(w, "defeatCount", 8);
        set(w, "stage", ContentCatalog.STAGES[stageIndex]);
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "won", Boolean.FALSE);
        setInt(w, "state", 0);
        setInt(w, "bossEncounterIndex", 1);
        set(w, "pickups", filled(pickupClass(), 96));
        set(w, "enemies", filled(Class.forName("me.holojava.fullgame.RunWorld$Enemy"), 48));
        set(w, "shots", filled(Class.forName("me.holojava.fullgame.RunWorld$Shot"), 128));

        Object encounters = Array.newInstance(boolean.class, 4);
        Array.setBoolean(encounters, 1, true);   // encounter 1 defeated = the non-hard final boss
        set(w, "encounterDefeated", encounters);
        Object started = Array.newInstance(boolean.class, 4);
        set(w, "encounterStarted", started);

        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.TRUE);
        setInt(boss, "defIndex", 0);
        setInt(boss, "x", 100);
        setInt(boss, "y", 100);
        setInt(boss, "hp", 10);
        setInt(boss, "maxHp", 10);
        setInt(boss, "anim", 0);
        set(boss, "hitMask", Long.valueOf(0L));
        set(boss, "chargeUntil", Long.valueOf(0L));
        set(w, "boss", boss);
        return w;
    }

    private static void setInt(Object target, String field, int value) {
        set(target, field, Integer.valueOf(value));
    }

    private static void defeatBoss(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("defeatBoss");
        m.setAccessible(true);
        m.invoke(w);
    }

    private static void collectPickup(Object w, int type, int value, long now) throws Exception {
        Object p = allocate(pickupClass());
        setInt(p, "type", type);
        setInt(p, "value", value);
        setInt(p, "x", 10);
        setInt(p, "y", 10);
        set(p, "active", Boolean.TRUE);
        Method m = worldClass().getDeclaredMethod("collectPickup", pickupClass(), long.class);
        m.setAccessible(true);
        m.invoke(w, p, Long.valueOf(now));
    }

    private static int coinClear(int stageIndex) {
        return ContentCatalog.STAGES[stageIndex].coinClear;
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testClearRewardReachesPermanentBalance();
        testClearRewardIsGrantedOnlyOnce();
        testLevelUpHundredStaysRunLocal();
        testUncollectedCoinsAreNeverPersisted();
        testRefusedClearWriteStillPaysTheReward();
        testRunCounterStillDrivesTheHud();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /**
     * Clause 1. The reward is {@code coinClear + defeatCount / 4}; with defeatCount 8 that is
     * {@code coinClear + 2}.
     */
    private static void testClearRewardReachesPermanentBalance() throws Exception {
        System.out.println("[1] the clear reward reaches the permanent balance");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object w = clearedRun(save, 0);

        int expected = coinClear(0) + 8 / 4;
        defeatBoss(w);

        expectEq(save.coinBalance(), expected,
            "the permanent balance received coinClear + defeatCount/4");
        expectEq(getInt(w, "coins"), expected, "the run counter received the same amount");
        expect(getBool(w, "won"), "the run is marked won");
        expectEq(SaveStore.readCoin(store.record, 48), expected, "the reward is genuinely on disk");
    }

    /**
     * The reward is a permanent coin grant, so a double payment would duplicate coins forever.
     * {@code defeatBoss} is reachable from the bomber self-destruct, from {@code damageBoss}, and
     * directly; this calls it twice to prove the second call is inert.
     */
    private static void testClearRewardIsGrantedOnlyOnce() throws Exception {
        System.out.println("[2] the clear reward is granted only once");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object w = clearedRun(save, 0);

        int expected = coinClear(0) + 2;
        defeatBoss(w);
        int writesAfterFirst = store.writes;

        defeatBoss(w);
        defeatBoss(w);

        expectEq(save.coinBalance(), expected, "repeated defeatBoss calls do not pay again");
        expectEq(store.writes, writesAfterFirst, "and issue no further record writes");
    }

    /** Clause 2. The level-up bonus is a run reward and must never be persisted. */
    private static void testLevelUpHundredStaysRunLocal() throws Exception {
        System.out.println("[3] the level-up +100 stays run-local");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object w = clearedRun(save, 0);

        // The OFFER_COINS branch of applyOffer is what grants the 100.
        applyOfferCoins(w, 100);
        expectEq(getInt(w, "coins"), 100, "the run counter received the level-up bonus");
        expectEq(save.coinBalance(), 0L, "the permanent balance did not");

        defeatBoss(w);
        expectEq(save.coinBalance(), coinClear(0) + 2L,
            "only the clear reward was persisted, on top of no level-up bonus");
    }

    /**
     * Applies the level-up coin offer directly. {@code applyOffer} reads several fields, so the
     * offer is built to isolate the coin branch.
     */
    private static void applyOfferCoins(Object w, int amount) throws Exception {
        Object offer = allocate(Class.forName("me.holojava.fullgame.RunWorld$Offer"));
        setInt(offer, "type", 5);            // OFFER_COINS
        setInt(offer, "value", amount);
        setInt(offer, "existing", -1);
        Object offers = filled(offer.getClass(), 6);
        Array.set(offers, 0, offer);
        set(w, "offers", offers);
        setInt(w, "weaponCount", 0);
        setInt(w, "itemCount", 0);
        Method m = worldClass().getDeclaredMethod("applyOffer", int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(0));
    }

    /** Clause 4. Only what is collected is ever converted; the run total alone converts nothing. */
    private static void testUncollectedCoinsAreNeverPersisted() throws Exception {
        System.out.println("[4] uncollected coins are never persisted");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object w = clearedRun(save, 0);

        // A run that ends without any pickup being collected.
        setInt(w, "coins", 250);
        defeatBoss(w);

        expectEq(save.coinBalance(), coinClear(0) + 2L,
            "only the clear reward persisted; the 250 run coins were not converted");
    }

    /**
     * Clause 3 applied to the reward: persistence is optional, the grant is not. A store that
     * refuses every write must cost the run neither its counter nor its balance — only the bytes
     * on disk stay where they were, until a later write lands them.
     */
    private static void testRefusedClearWriteStillPaysTheReward() throws Exception {
        System.out.println("[5] a refused clear-reward write still pays the reward");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object w = clearedRun(save, 0);

        store.failWrites = true;
        defeatBoss(w);

        expectEq(getInt(w, "coins"), coinClear(0) + 2, "the run counter still advanced");
        expectEq(save.coinBalance(), coinClear(0) + 2L,
            "and the permanent balance received the reward despite the refusal");
        expectEq(SaveStore.readCoin(store.record, 48), 0L, "the bytes on disk still say zero");
        expect(!save.flush(), "the flush reports the refusal too");
        expectEq(SaveStore.readCoin(store.record, 48), 0L, "and still leaves disk untouched");

        // The refusal is temporary: the next write over a healthy store lands everything.
        store.failWrites = false;
        expect(save.flush(), "the reward lands once storage recovers");
        expectEq(SaveStore.readCoin(store.record, 48), coinClear(0) + 2L,
            "the recovered record carries the full reward");
        expect(getBool(w, "won"), "the run still counts as won");
    }

    /** Clause 5. The HUD reads the run counter, so it must keep tracking pickups and rewards. */
    private static void testRunCounterStillDrivesTheHud() throws Exception {
        System.out.println("[6] the run counter still tracks pickups and rewards");
        Recording store = new Recording();
        SaveStore save = new SaveStore(store);
        Object w = clearedRun(save, 0);

        collectPickup(w, 1, 10, 1000L);
        collectPickup(w, 1, 10, 1100L);
        defeatBoss(w);

        expectEq(getInt(w, "coins"), 20 + coinClear(0) + 2,
            "the run total is pickups plus the clear reward, which is what the HUD shows");
        expectEq(save.coinBalance(), 20L + coinClear(0) + 2L,
            "and the permanent balance agrees because everything here was earned");
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