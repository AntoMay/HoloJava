package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the level-up offer path: that an offer is applied exactly once per level-up, and that the
 * stat offers behave the way the reference JAR does.
 *
 * <p><b>Construction.</b> {@code RunWorld} cannot be built on a desktop JVM: its constructor
 * reaches {@code Image.createImage}, whose MIDP stub throws an {@code Error} that the library's
 * {@code catch (Exception)} does not catch. Instances are allocated without running the
 * constructor, and fields are written through {@code sun.misc.Unsafe} by their real type, because
 * several — including the final {@code offers} array — cannot be set by plain reflection. The
 * methods under test are the genuine production bytecode.
 *
 * <p><b>Not proven.</b> Anything requiring spawned assets, rendering, or the real pickup magnet.
 */
public final class RunWorldOfferOnceTest {

    private static int checks = 0;
    private static int failures = 0;

    private static final int OFFER_SKILL = 0;
    private static final int OFFER_WEAPON = 1;
    private static final int OFFER_STAT = 3;
    private static final int OFFER_COINS = 5;
    private static final int OFFER_REROLL = 6;
    private static final int STATE_RUNNING = 0;
    private static final int STATE_PAUSED = 1;
    private static final int STATE_LEVEL_UP = 2;

    // ---------------------------------------------------------------- unsafe plumbing

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

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Class<?> offerClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Offer");
    }

    private static Class<?> pickupClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Pickup");
    }

    // ---------------------------------------------------------------- fixture

    private static Object world() throws Exception {
        Object w = allocate(worldClass());
        set(w, "random", new java.util.Random(7L));
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "save", null);
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "state", Integer.valueOf(STATE_RUNNING));
        set(w, "playerLevel", Integer.valueOf(1));
        set(w, "experience", Integer.valueOf(0));
        // A non-zero threshold: with expNeed 0 the post-offer "experience >= expNeed" branch would
        // always fire and immediately level up again, which would mask what test 4 is checking.
        set(w, "expNeed", Integer.valueOf(79));
        set(w, "baseMaxHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "offerCount", Integer.valueOf(0));
        set(w, "selectedOffer", Integer.valueOf(0));
        set(w, "rerolls", Integer.valueOf(3));
        set(w, "holds", Integer.valueOf(1));
        set(w, "heldOffer", Integer.valueOf(-1));
        set(w, "coins", Integer.valueOf(0));
        set(w, "statOfferCounts", new int[16]);
        set(w, "offers", filled(offerClass(), 6));
        set(w, "offerUsed", new boolean[64]);
        set(w, "pickups", filled(pickupClass(), 96));
        set(w, "enemies", filled(Class.forName("me.holojava.fullgame.RunWorld$Enemy"), 48));
        set(w, "shots", filled(Class.forName("me.holojava.fullgame.RunWorld$Shot"), 128));
        return w;
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

    /** Places one offer in a slot of the world's offer array. */
    private static void putOffer(Object w, int slot, int type, int value, int existing)
            throws Exception {
        Object o = allocate(offerClass());
        set(o, "type", Integer.valueOf(type));
        set(o, "value", Integer.valueOf(value));
        set(o, "existing", Integer.valueOf(existing));
        Field f = worldClass().getDeclaredField("offers");
        f.setAccessible(true);
        Object[] offers = (Object[]) f.get(w);
        Array.set(offers, slot, o);
    }

    private static void applyOffer(Object w, int index) throws Exception {
        Method m = worldClass().getDeclaredMethod("applyOffer", int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(index));
    }

    private static void selectOffer(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("selectOffer");
        m.setAccessible(true);
        m.invoke(w);
    }

    private static int[] statCounts(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("statOfferCounts");
        f.setAccessible(true);
        return (int[]) f.get(w);
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testApplyOfferIgnoresTheControlSlots();
        testMaxHpStatOfferTakesEffect();
        testPercentageStatOffersMatchTheReference();
        testOfferIsAppliedOnlyOncePerLevelUp();
        testSelectOfferIsInertOutsideTheLevelUpState();
        testExpPickupGrantsExactlyOneLevel();
        testRerollConsumesWithoutApplying();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Slots 4 and 5 are reroll and hold, never real upgrades. */
    private static void testApplyOfferIgnoresTheControlSlots() throws Exception {
        System.out.println("[1] applyOffer ignores the control slots");
        Object w = world();
        putOffer(w, 4, OFFER_COINS, 0, -1);
        applyOffer(w, 4);
        expectEq(getInt(w, "coins"), 0, "slot 4 granted nothing");

        putOffer(w, 5, OFFER_COINS, 0, -1);
        applyOffer(w, 5);
        expectEq(getInt(w, "coins"), 0, "slot 5 granted nothing");

        applyOffer(w, 99);
        applyOffer(w, -1);
        expectEq(getInt(w, "coins"), 0, "out-of-range indexes granted nothing");
    }

    /**
     * Stat 0 raises max HP, and it survives {@code recomputeStats()} because that method treats
     * {@code baseMaxHp} as the starting value rather than resetting it.
     */
    private static void testMaxHpStatOfferTakesEffect() throws Exception {
        System.out.println("[2] the max-HP stat offer takes effect");
        Object w = world();
        putOffer(w, 0, OFFER_STAT, 0, -1);

        applyOffer(w, 0);
        int gained = getInt(w, "baseMaxHp") - 100;
        expect(gained > 0, "baseMaxHp rose after the offer");
        expectEq(getInt(w, "playerMaxHp"), 100 + gained, "playerMaxHp followed");
        expectEq(statCounts(w)[0], 1, "the offer was counted once");
    }

    /**
     * Percentage stat offers are consumed and counted but produce no effect, because
     * {@code applyStat} increments a field that the {@code recomputeStats()} call immediately after
     * it zeroes again.
     *
     * <p><b>This matches the reference JAR exactly</b> and is deliberately not "fixed". The
     * reference's {@code applyOffer} calls {@code applyStat} at offset 247 followed by
     * {@code recomputeStats} at 251, and its {@code recomputeStats} zeroes {@code atkPercentBonus},
     * {@code hastePercent}, {@code speedPercentBonus}, {@code pickupPercentBonus},
     * {@code critPercentBonus}, {@code expPercentBonus} and {@code defensePercent} — the same seven
     * fields. Any deviation here would be an invented divergence from the reference, not a fix.
     */
    private static void testPercentageStatOffersMatchTheReference() throws Exception {
        System.out.println("[3] percentage stat offers match the reference");
        Object w = world();

        putOffer(w, 0, OFFER_STAT, 1, -1);       // attack
        applyOffer(w, 0);
        expectEq(getInt(w, "atkPercentBonus"), 0,
            "attack bonus is zero, matching the reference's recomputeStats ordering");
        expectEq(statCounts(w)[1], 1, "but the offer was still consumed and counted");

        putOffer(w, 0, OFFER_STAT, 4, -1);       // pickup radius
        applyOffer(w, 0);
        expectEq(getInt(w, "pickupPercentBonus"), 0, "pickup bonus is zero as well");

        putOffer(w, 0, OFFER_STAT, 3, -1);       // crit
        applyOffer(w, 0);
        expectEq(getInt(w, "critPercentBonus"), 0, "crit bonus is zero as well");
    }

    /**
     * The core single-event rule. A stat offer increments a counter and a stat field, so applying
     * it twice would be observable. {@code selectOffer} flips the state to {@code STATE_RUNNING},
     * and every later call is refused by that state check.
     */
    private static void testOfferIsAppliedOnlyOncePerLevelUp() throws Exception {
        System.out.println("[4] an offer is applied only once per level-up");
        Object w = world();
        putOffer(w, 0, OFFER_STAT, 0, -1);
        set(w, "state", Integer.valueOf(STATE_LEVEL_UP));
        set(w, "offerCount", Integer.valueOf(4));
        set(w, "selectedOffer", Integer.valueOf(0));

        selectOffer(w);
        expectEq(statCounts(w)[0], 1, "the offer was applied");
        expectEq(getInt(w, "state"), STATE_RUNNING, "the run resumed");

        selectOffer(w);
        selectOffer(w);
        selectOffer(w);
        expectEq(statCounts(w)[0], 1, "repeat presses did not apply it again");
    }

    /** The state gate must refuse offers in every other state, not just when running. */
    private static void testSelectOfferIsInertOutsideTheLevelUpState() throws Exception {
        System.out.println("[5] selectOffer is inert outside the level-up state");
        for (int state : new int[] { STATE_RUNNING, STATE_PAUSED }) {
            Object w = world();
            putOffer(w, 0, OFFER_STAT, 0, -1);
            set(w, "state", Integer.valueOf(state));
            set(w, "offerCount", Integer.valueOf(4));
            set(w, "selectedOffer", Integer.valueOf(0));

            selectOffer(w);
            expectEq(statCounts(w)[0], 0, "no offer was applied in state " + state);
            expectEq(getInt(w, "state"), state, "the state was left alone");
        }

        // offerCount == 0 also blocks it, even in the level-up state.
        Object w = world();
        putOffer(w, 0, OFFER_STAT, 0, -1);
        set(w, "state", Integer.valueOf(STATE_LEVEL_UP));
        set(w, "offerCount", Integer.valueOf(0));
        set(w, "selectedOffer", Integer.valueOf(0));
        selectOffer(w);
        expectEq(statCounts(w)[0], 0, "no offers available means nothing was applied");
    }

    /**
     * One EXP pickup can cross exactly one threshold. The level-up loop also requires
     * {@code STATE_RUNNING}, so it stops as soon as the state flips, and a huge pickup cannot
     * cascade through several levels without the player choosing an offer between them.
     */
    private static void testExpPickupGrantsExactlyOneLevel() throws Exception {
        System.out.println("[6] an EXP pickup grants exactly one level");
        Object w = world();
        set(w, "state", Integer.valueOf(STATE_RUNNING));
        set(w, "expNeed", Integer.valueOf(79));
        set(w, "offerCount", Integer.valueOf(4));
        set(w, "rerolls", Integer.valueOf(0));
        set(w, "holds", Integer.valueOf(0));

        collectExp(w, 500);

        expectEq(getInt(w, "playerLevel"), 2, "exactly one level was gained");
        expectEq(getInt(w, "state"), STATE_LEVEL_UP, "the run is waiting on an offer");
        expect(getInt(w, "experience") < 500, "the surplus stayed as experience");
    }

    private static void collectExp(Object w, int value) throws Exception {
        Object p = allocate(pickupClass());
        set(p, "type", Integer.valueOf(0));
        set(p, "value", Integer.valueOf(value));
        set(p, "active", Boolean.TRUE);
        set(p, "x", Integer.valueOf(10));
        set(p, "y", Integer.valueOf(10));
        Method m = worldClass().getDeclaredMethod("collectPickup", pickupClass(), long.class);
        m.setAccessible(true);
        m.invoke(w, p, Long.valueOf(1000L));
    }

    /** A reroll spends a charge, applies no offer, and leaves the player choosing. */
    private static void testRerollConsumesWithoutApplying() throws Exception {
        System.out.println("[7] a reroll consumes without applying");
        Object w = world();
        putOffer(w, 4, OFFER_REROLL, 0, -1);
        set(w, "state", Integer.valueOf(STATE_LEVEL_UP));
        set(w, "offerCount", Integer.valueOf(6));
        set(w, "selectedOffer", Integer.valueOf(4));
        set(w, "rerolls", Integer.valueOf(3));

        int before = getInt(w, "rerolls");
        selectOffer(w);
        expectEq(getInt(w, "rerolls"), before - 1, "a reroll charge was spent");
        expectEq(getInt(w, "state"), STATE_LEVEL_UP, "the player is still choosing");
        expectEq(statCounts(w)[0], 0, "no offer was applied by the reroll");
        expectEq(getInt(w, "coins"), 0, "and no coins were granted");
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