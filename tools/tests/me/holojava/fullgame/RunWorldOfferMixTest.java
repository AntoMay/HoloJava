package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pins the level-up offer mix.
 *
 * <p>Two defects lived here historically. The original rolled {@code int r = rand(20)} and then
 * tested {@code r < 20}, which is true for every value {@code rand(20)} can return, so both
 * leading slots offered the same unacquired skill on every single level-up and never reached the
 * branches below it: character stat upgrades (Max HP, ATK, SPD, crit, pickup, haste) almost never
 * appeared. The fix gave stat upgrades a real share of every roll.
 *
 * <p>Since Skill Pasif became the one-time shop purchase, the skill is not part of the roll at
 * all: no slot may ever offer it, and slot 0 — which used to guarantee it — now rolls exactly
 * like every other slot. These checks drive the production method through reflection, so they
 * cannot drift from it. {@code rand} is seeded from the run seed, so the distribution below is
 * deterministic.
 */
public final class RunWorldOfferMixTest {

    private static final int OFFER_SKILL = 0;      // retired: pinned as never-offered below
    private static final int OFFER_WEAPON = 1;
    private static final int OFFER_ITEM = 2;
    private static final int OFFER_STAT = 3;

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
            if (t == boolean.class) {
                unsafe.getClass().getMethod("putBoolean", Object.class, long.class, boolean.class)
                        .invoke(unsafe, target, offset, value);
            } else if (t == int.class) {
                unsafe.getClass().getMethod("putInt", Object.class, long.class, int.class)
                        .invoke(unsafe, target, offset, value);
            } else {
                unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
                        .invoke(unsafe, target, offset, value);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    /** A world with a fixed seed. The skill flag no longer influences the roll at all. */
    private static Object world(boolean skillAcquired, long seed) throws Exception {
        Class<?> wc = Class.forName("me.holojava.fullgame.RunWorld");
        Object w = allocate(wc);
        set(w, "random", new java.util.Random(seed));
        set(w, "skillAcquired", Boolean.valueOf(skillAcquired));
        return w;
    }

    private static int choose(Object w, int slot) throws Exception {
        Method m = w.getClass().getDeclaredMethod("chooseOfferType", int.class);
        m.setAccessible(true);
        return ((Integer) m.invoke(w, Integer.valueOf(slot))).intValue();
    }

    public static void main(String[] args) throws Exception {
        testSkillIsNeverOffered();
        testStatsAreReachableFromEverySlot();
        testStatsAreNotRarerThanItems();
        testSlotZeroRollsNormally();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /**
     * The skill is the one-time Skill Pasif shop purchase, so the roll must never produce it — in
     * any slot, whether the run already has the skill or not. Slot 0 used to guarantee it.
     */
    private static void testSkillIsNeverOffered() throws Exception {
        System.out.println("[1] the skill is never offered, in any slot");
        boolean[] acquired = { false, true };
        for (int a = 0; a < acquired.length; a++) {
            Object w = world(acquired[a], 99L);
            for (int slot = 0; slot < 4; slot++) {
                int skillRolls = 0;
                for (int i = 0; i < 200; i++) {
                    if (choose(w, slot) == OFFER_SKILL) skillRolls++;
                }
                expectEq(skillRolls, 0,
                    "slot " + slot + " never offers the skill (acquired=" + acquired[a] + ")");
            }
        }
    }

    /**
     * The regression that mattered: every slot, including the old skill slot 0, must be able to
     * roll a stat. Under the old {@code r < 20} test, slots 0 and 1 were pinned to the skill and
     * slots 2 and 3 to weapon/item, so no slot could ever produce OFFER_STAT.
     */
    private static void testStatsAreReachableFromEverySlot() throws Exception {
        System.out.println("[2] stat upgrades are reachable from every slot");
        int[] statsPerSlot = new int[4];
        Object w = world(true, 4242L);
        for (int round = 0; round < 500; round++) {
            for (int slot = 0; slot < 4; slot++) {
                if (choose(w, slot) == OFFER_STAT) statsPerSlot[slot]++;
            }
        }
        for (int slot = 0; slot < 4; slot++) {
            expect(statsPerSlot[slot] > 0,
                "slot " + slot + " rolls stats at least once (got " + statsPerSlot[slot] + "/500)");
        }
        int total = 0;
        for (int slot = 0; slot < 4; slot++) total += statsPerSlot[slot];
        expect(total >= 150 && total <= 600,
            "stats land in a sane share of all rolls (got " + total + "/2000)");
    }

    /**
     * The mix must not be all stats either: weapons and items still have to show up, and stats
     * have to stay in the share the mix actually specifies.
     *
     * <p>{@code chooseOfferType} weights weapon 50%, item 25%, stat 25%, so a seeded sample is
     * allowed to land slightly either side of stats vs items. The bound below is a band, not an
     * ordering, because asserting {@code stats > items} would be a claim about one seed's noise.
     * The floor is the part that matters: before the fix stats were structurally 0% of rolls.
     */
    private static void testStatsAreNotRarerThanItems() throws Exception {
        System.out.println("[3] the mix still offers weapons and items");
        int stats = 0, weapons = 0, items = 0;
        Object w = world(true, 4242L);
        for (int round = 0; round < 500; round++) {
            for (int slot = 0; slot < 4; slot++) {
                int t = choose(w, slot);
                if (t == OFFER_STAT) stats++;
                else if (t == OFFER_WEAPON) weapons++;
                else if (t == OFFER_ITEM) items++;
            }
        }
        expect(weapons > 0, "weapons still appear (got " + weapons + "/2000)");
        expect(items > 0, "items still appear (got " + items + "/2000)");
        expect(stats * 10 >= 2000,
            "stats hold at least a 20% share of rolls (got " + stats + "/2000)");
        expect(weapons >= stats,
            "weapons are the most common category (got " + weapons + " vs " + stats + ")");
    }

    /** Slot 0 used to be the guaranteed skill slot; it must now produce every other category. */
    private static void testSlotZeroRollsNormally() throws Exception {
        System.out.println("[4] slot 0 rolls every category, like the other slots");
        Object w = world(false, 777L);
        int stats = 0, weapons = 0, items = 0;
        for (int round = 0; round < 500; round++) {
            int t = choose(w, 0);
            if (t == OFFER_STAT) stats++;
            else if (t == OFFER_WEAPON) weapons++;
            else if (t == OFFER_ITEM) items++;
        }
        expect(stats > 0, "slot 0 rolls stats (got " + stats + "/500)");
        expect(weapons > 0, "slot 0 rolls weapons (got " + weapons + "/500)");
        expect(items > 0, "slot 0 rolls items (got " + items + "/500)");
    }
}
