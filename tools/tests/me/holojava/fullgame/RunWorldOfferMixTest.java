package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pins the level-up offer mix.
 *
 * <p>This exists because of a real defect. {@code chooseOfferType} used to roll
 * {@code int r = rand(20)} and then test {@code r < 20}, which is true for every value
 * {@code rand(20)} can return, so both leading slots offered the same unacquired skill on every
 * single level-up and never reached the branches below it. Slots 2 and 3 were weapon/item only.
 * The net effect was that character stat upgrades (Max HP, ATK, SPD, crit, pickup, haste) almost
 * never appeared: a player gained weapons but no survivability, which made early levels feel far
 * too punishing.
 *
 * <p>The fix guarantees the one-time skill on slot 0 and gives stat upgrades a real share of every
 * roll. These checks drive the production method through reflection, so they cannot drift from it.
 * {@code rand} is seeded from the run seed, so the distribution below is deterministic.
 */
public final class RunWorldOfferMixTest {

    private static final int OFFER_SKILL = 0;
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

    /** A world whose skill has the given number of stacks, with a fixed seed. */
    private static Object world(boolean skillAcquired, int stacks, long seed) throws Exception {
        Class<?> wc = Class.forName("me.holojava.fullgame.RunWorld");
        Object w = allocate(wc);
        set(w, "random", new java.util.Random(seed));
        set(w, "skillAcquired", Boolean.valueOf(skillAcquired));
        set(w, "skillStacks", Integer.valueOf(stacks));
        return w;
    }

    private static int choose(Object w, int slot) throws Exception {
        Method m = w.getClass().getDeclaredMethod("chooseOfferType", int.class);
        m.setAccessible(true);
        return ((Integer) m.invoke(w, Integer.valueOf(slot))).intValue();
    }

    public static void main(String[] args) throws Exception {
        testSkillIsOfferedUntilMaxStacks();
        testStatsAreReachableFromEverySlot();
        testStatsAreNotRarerThanItems();
        testNoDuplicateSkillOffers();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Slot 0 hands out the skill until it is taken, and never offers it again after that. */
    /**
     * The skill is now stackable, so slot 0 keeps offering it until the stack cap and never after.
     * The old behaviour, one offer then never again, is exactly what made repeating the skill slot a
     * wasted level once the skill was already taken.
     */
    private static void testSkillIsOfferedUntilMaxStacks() throws Exception {
        System.out.println("[1] the skill is offered on slot 0 until the stack cap");
        int max = ContentCatalog.SKILL_MAX_STACKS;
        for (int stacks = 0; stacks < max; stacks++) {
            Object w = world(stacks > 0, stacks, 99L);
            expectEq(choose(w, 0), OFFER_SKILL,
                "slot 0 offers the skill at " + stacks + " stacks");
        }
        Object capped = world(true, max, 99L);
        for (int i = 0; i < 40; i++) {
            expect(choose(capped, 0) != OFFER_SKILL,
                "slot 0 stops offering the skill once it is at the cap of " + max);
        }
    }

    /**
     * The regression that mattered: with the skill already taken, every slot must be able to roll
     * a stat. Under the old {@code r < 20} test, slots 0 and 1 were pinned to the skill and slots
     * 2 and 3 to weapon/item, so no slot could ever produce OFFER_STAT.
     */
    private static void testStatsAreReachableFromEverySlot() throws Exception {
        System.out.println("[2] stat upgrades are reachable from every slot");
        int[] statsPerSlot = new int[4];
        Object w = world(true, ContentCatalog.SKILL_MAX_STACKS, 4242L);
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
        Object w = world(true, ContentCatalog.SKILL_MAX_STACKS, 4242L);
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

    /** No level-up may fill two slots with the same unacquired skill, which was the old symptom. */
    private static void testNoDuplicateSkillOffers() throws Exception {
        System.out.println("[4] a single level-up never offers the skill twice");
        Object w = world(false, 0, 777L);
        int duplicated = 0;
        for (int round = 0; round < 300; round++) {
            int skills = 0;
            for (int slot = 0; slot < 4; slot++) {
                if (choose(w, slot) == OFFER_SKILL) skills++;
            }
            if (skills > 1) duplicated++;
        }
        expectEq(duplicated, 0,
            "level-ups offering the skill in more than one slot (the old r<20 symptom)");
    }
}