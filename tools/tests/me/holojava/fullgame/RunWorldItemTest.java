package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers items: every supported effect, stat recomputation, level caps, and slot limits.
 *
 * <p>Parity with the reference JAR was established first, from bytecode:
 * <ul>
 *   <li>The reference's {@code recomputeStats} compares {@code ItemDef.effect} against exactly
 *       0, 1, 2, 3, 4, 5, 6 and 10 — the same eight this build handles. Effects 7 ({@code SHIELD})
 *       and 9 ({@code FOOD_DROP}) are read nowhere in either build.</li>
 *   <li>The reference carries one catalog item this build does not: {@code uber_sheep}, whose only
 *       effect is 9. Since effect 9 is unimplemented in the reference as well, that item is inert
 *       there too. See the accompanying report; it is a catalog parity gap, not a behaviour
 *       difference, and it was deliberately not added.</li>
 * </ul>
 *
 * <p>The already-known ordering quirk — {@code applyStat} then {@code recomputeStats} wiping the
 * percentage bonuses — is inherited from the reference and is pinned by
 * {@code RunWorldOfferOnceTest}, not restated here. Item-granted bonuses are unaffected by it,
 * because those come from {@code recomputeStats} itself.
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldItemTest {

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

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Object world(int modeIndex) throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "mode", ContentCatalog.MODES[modeIndex]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "save", null);
        set(w, "random", new Random(5L));
        set(w, "state", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "baseMaxHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "regenTickAt", Long.valueOf(0L));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "shield", Integer.valueOf(0));
        set(w, "defensePercent", Integer.valueOf(0));
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "itemSlots", new int[6]);
        set(w, "itemLevels", new int[6]);
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "weaponSlots", new int[6]);
        set(w, "weaponLevels", new int[6]);
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 48));
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

    private static void addItem(Object w, int itemIndex, int level) throws Exception {
        Method m = worldClass().getDeclaredMethod("addItem", int.class, int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(itemIndex), Integer.valueOf(level));
    }

    private static void recompute(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("recomputeStats");
        m.setAccessible(true);
        m.invoke(w);
    }

    private static void damagePlayer(Object w, int amount, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("damagePlayer", int.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(amount), Long.valueOf(now));
    }

    /** Index in the catalog of the first item carrying the given effect, or -1. */
    private static int firstItemWithEffect(int effect) {
        for (int i = 0; i < ContentCatalog.ITEMS.length; i++) {
            ContentCatalog.ItemDef d = ContentCatalog.ITEMS[i];
            if (d != null && d.effect == effect) return i;
        }
        return -1;
    }

    private static ContentCatalog.ItemDef itemAt(int i) {
        return ContentCatalog.ITEMS[i];
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testEveryCatalogEffectIsHandled();
        testAttackPercentItemChangesAttack();
        testSpeedItemChangesSpeedBonus();
        testPickupItemChangesPickupRange();
        testCritItemChangesCritBonus();
        testExpItemChangesExpBonus();
        testMaxHpItemRaisesTheCeiling();
        testEffectsStackAcrossLevelsAndItems();
        testLevelCapIsRespected();
        testSlotLimitIsRespected();
        testRecomputeRebuildsFromScratch();
        testNegateItemCanBlockDamage();
        testCurrentHpIsClampedToTheNewMaximum();
        testEndlessModeKeepsMaximumHpAtOne();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /**
     * No catalog item may carry an effect the engine ignores. This is the check that would have
     * caught a lost or newly added item whose effect has no handler.
     */
    private static void testEveryCatalogEffectIsHandled() {
        System.out.println("[1] every catalog effect has a handler");
        int handled = 0;
        for (int i = 0; i < ContentCatalog.ITEMS.length; i++) {
            ContentCatalog.ItemDef d = ContentCatalog.ITEMS[i];
            expect(d != null, "item " + i + " exists");
            if (d == null) continue;
            boolean supported =
                    d.effect == ContentCatalog.EFFECT_DAMAGE_PCT
                    || d.effect == ContentCatalog.EFFECT_HASTE_PCT
                    || d.effect == ContentCatalog.EFFECT_SPEED_PCT
                    || d.effect == ContentCatalog.EFFECT_MAX_HP
                    || d.effect == ContentCatalog.EFFECT_PICKUP_PCT
                    || d.effect == ContentCatalog.EFFECT_CRIT_PCT
                    || d.effect == ContentCatalog.EFFECT_REGEN
                    || d.effect == ContentCatalog.EFFECT_NEGATE
                    || d.effect == ContentCatalog.EFFECT_EXP_PCT;
            expect(supported, "item '" + d.id + "' effect " + d.effect + " is implemented");
            if (supported) handled++;
        }
        expect(handled == ContentCatalog.ITEMS.length,
            "all " + ContentCatalog.ITEMS.length + " catalog items have a live effect");
    }

    /** Each percentage effect must move its own field and only its own. */
    private static void testAttackPercentItemChangesAttack() throws Exception {
        System.out.println("[2] the attack item changes only the attack bonus");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_DAMAGE_PCT);
        expect(idx >= 0, "a damage item exists");
        ContentCatalog.ItemDef d = itemAt(idx);

        Object w = world(0);
        expectEq(getInt(w, "atkPercentBonus"), 0, "starts at zero");
        addItem(w, idx, 1);
        recompute(w);
        expectEq(getInt(w, "atkPercentBonus"), d.value, "the attack bonus rose by the item value");
        expectEq(getInt(w, "speedPercentBonus"), 0, "and no other bonus moved");
        expectEq(getInt(w, "critPercentBonus"), 0, "crit is untouched");
    }

    private static void testSpeedItemChangesSpeedBonus() throws Exception {
        System.out.println("[3] the speed item changes only the speed bonus");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_SPEED_PCT);
        expect(idx >= 0, "a speed item exists");
        ContentCatalog.ItemDef d = itemAt(idx);
        Object w = world(0);
        addItem(w, idx, 1);
        recompute(w);
        expectEq(getInt(w, "speedPercentBonus"), d.value, "the speed bonus rose by the item value");
        expectEq(getInt(w, "atkPercentBonus"), 0, "attack is untouched");
    }

    private static void testPickupItemChangesPickupRange() throws Exception {
        System.out.println("[4] the pickup item changes only the pickup bonus");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_PICKUP_PCT);
        expect(idx >= 0, "a pickup item exists");
        ContentCatalog.ItemDef d = itemAt(idx);
        Object w = world(0);
        addItem(w, idx, 1);
        recompute(w);
        expectEq(getInt(w, "pickupPercentBonus"), d.value, "the pickup bonus rose by the item value");
        expectEq(getInt(w, "critPercentBonus"), 0, "crit is untouched");
    }

    private static void testCritItemChangesCritBonus() throws Exception {
        System.out.println("[5] the crit item changes only the crit bonus");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_CRIT_PCT);
        expect(idx >= 0, "a crit item exists");
        ContentCatalog.ItemDef d = itemAt(idx);
        Object w = world(0);
        addItem(w, idx, 1);
        recompute(w);
        expectEq(getInt(w, "critPercentBonus"), d.value, "the crit bonus rose by the item value");
    }

    private static void testExpItemChangesExpBonus() throws Exception {
        System.out.println("[6] the exp item changes only the exp bonus");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_EXP_PCT);
        expect(idx >= 0, "an exp item exists");
        ContentCatalog.ItemDef d = itemAt(idx);
        Object w = world(0);
        addItem(w, idx, 1);
        recompute(w);
        expectEq(getInt(w, "expPercentBonus"), d.value, "the exp bonus rose by the item value");
        expectEq(getInt(w, "atkPercentBonus"), 0, "attack is untouched");
    }

    /** Max HP raises the ceiling by value * level, not the current HP. */
    private static void testMaxHpItemRaisesTheCeiling() throws Exception {
        System.out.println("[7] the max-HP item raises the ceiling");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_MAX_HP);
        expect(idx >= 0, "a max-HP item exists");
        ContentCatalog.ItemDef d = itemAt(idx);
        Object w = world(0);
        expectEq(getInt(w, "playerMaxHp"), 100, "starts at the base ceiling");

        addItem(w, idx, 1);
        recompute(w);
        expectEq(getInt(w, "playerMaxHp"), 100 + d.value, "the ceiling rose by one level's value");
        expectEq(getInt(w, "playerHp"), 100, "current HP is untouched while under the new ceiling");
    }

    /** Two level-1 copies stack; one level-2 copy is worth the same as two level-1 copies. */
    private static void testEffectsStackAcrossLevelsAndItems() throws Exception {
        System.out.println("[8] effects stack across levels and across items");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_CRIT_PCT);
        ContentCatalog.ItemDef d = itemAt(idx);

        Object one = world(0);
        addItem(one, idx, 1);
        recompute(one);

        Object twoCopies = world(0);
        addItem(twoCopies, idx, 1);
        addItem(twoCopies, idx, 1);
        recompute(twoCopies);

        Object levelTwo = world(0);
        addItem(levelTwo, idx, 2);
        recompute(levelTwo);

        expectEq(getInt(twoCopies, "critPercentBonus"), 2 * d.value, "two copies stack additively");
        expectEq(getInt(levelTwo, "critPercentBonus"), 2 * d.value,
            "one level-2 item is worth exactly two level-1 items");
        expectEq(getInt(levelTwo, "critPercentBonus"), getInt(twoCopies, "critPercentBonus"),
            "so the two routes agree");
    }

    /** applyOffer must stop raising an item at its maximum level. */
    private static void testLevelCapIsRespected() throws Exception {
        System.out.println("[9] the item level cap is respected");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_CRIT_PCT);
        ContentCatalog.ItemDef d = itemAt(idx);
        expect(d.maxLevel > 0, "the fixture item has a maximum level");

        Object w = world(0);
        Field slots = worldClass().getDeclaredField("itemSlots");
        slots.setAccessible(true);
        Field levels = worldClass().getDeclaredField("itemLevels");
        levels.setAccessible(true);
        ((int[]) slots.get(w))[0] = idx;
        ((int[]) levels.get(w))[0] = d.maxLevel;
        set(w, "itemCount", Integer.valueOf(1));

        Method applyOffer = worldClass().getDeclaredMethod("applyOffer", int.class);
        applyOffer.setAccessible(true);
        // The offer array must be materialised, not left as a null-filled array.
        set(w, "offers", filled("me.holojava.fullgame.RunWorld$Offer", 6));
        Field offersField = worldClass().getDeclaredField("offers");
        offersField.setAccessible(true);
        Object offers = offersField.get(w);
        Object o = Array.get(offers, 0);
        set(o, "type", Integer.valueOf(2));   // OFFER_ITEM, a RunWorld constant
        set(o, "value", Integer.valueOf(idx));
        set(o, "existing", Integer.valueOf(0));
        applyOffer.invoke(w, Integer.valueOf(0));

        expectEq(getInt(w, "itemCount"), 1, "no second copy was added at the cap");
        expectEq(((int[]) levels.get(w))[0], d.maxLevel, "and the level did not exceed the cap");
    }

    /** A full inventory refuses new items rather than overwriting a slot. */
    private static void testSlotLimitIsRespected() throws Exception {
        System.out.println("[10] a full inventory refuses new items");
        Object w = world(0);
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_CRIT_PCT);
        for (int i = 0; i < 6; i++) addItem(w, idx, 1);
        expectEq(getInt(w, "itemCount"), 6, "six items fill the inventory");

        // Overfilling must be refused quietly: no extra slot written, no exception escaping.
        int threw = 0;
        for (int i = 0; i < 4; i++) {
            try {
                addItem(w, idx, 1);
            } catch (Throwable crossingTheSlotLimit) {
                threw++;
            }
        }
        expectEq(threw, 0, "overfilling the inventory raised no error");
        expectEq(getInt(w, "itemCount"), 6, "further items are refused, not slotted");

        Field slots = worldClass().getDeclaredField("itemSlots");
        slots.setAccessible(true);
        int[] s = (int[]) slots.get(w);
        boolean allSame = true;
        for (int i = 0; i < 6; i++) if (s[i] != idx) allSame = false;
        expect(allSame, "and the existing slots were not overwritten");

        addItem(w, -1, 1);
        expectEq(getInt(w, "itemCount"), 6, "an invalid item index is ignored");
    }

    /**
 * recomputeStats must rebuild from the inventory, never accumulate. Every percentage bonus is
 * checked, because the reset line clears all seven at once and a test aimed at only one of them
 * would miss a partial removal.
 */
    private static void testRecomputeRebuildsFromScratch() throws Exception {
        System.out.println("[11] recompute rebuilds rather than accumulating");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_CRIT_PCT);
        ContentCatalog.ItemDef d = itemAt(idx);

        Object w = world(0);
        addItem(w, idx, 1);
        recompute(w);
        int[] once = new int[] { getInt(w, "atkPercentBonus"), getInt(w, "speedPercentBonus"),
                getInt(w, "pickupPercentBonus"), getInt(w, "critPercentBonus"),
                getInt(w, "expPercentBonus") };

        for (int i = 0; i < 5; i++) recompute(w);
        int[] after = new int[] { getInt(w, "atkPercentBonus"), getInt(w, "speedPercentBonus"),
                getInt(w, "pickupPercentBonus"), getInt(w, "critPercentBonus"),
                getInt(w, "expPercentBonus") };
        String[] names = { "attack", "speed", "pickup", "crit", "exp" };
        for (int i = 0; i < names.length; i++) {
            expectEq(after[i], once[i],
                "the " + names[i] + " bonus did not drift over five recomputes");
        }

        // Each percentage bonus must also survive repeated recomputes while its own item is equipped.
        // Without an item equipped those fields are zero, so removing their reset would be
        // invisible; with one, an accumulating recompute doubles the value on the second call.
        int[] effects = { ContentCatalog.EFFECT_DAMAGE_PCT, ContentCatalog.EFFECT_SPEED_PCT,
                ContentCatalog.EFFECT_PICKUP_PCT, ContentCatalog.EFFECT_EXP_PCT };
        String[] fields = { "atkPercentBonus", "speedPercentBonus", "pickupPercentBonus",
                "expPercentBonus" };
        for (int e = 0; e < effects.length; e++) {
            int itemIdx = firstItemWithEffect(effects[e]);
            expect(itemIdx >= 0, "a fixture item exists for " + fields[e]);
            Object acc = world(0);
            addItem(acc, itemIdx, 1);
            recompute(acc);
            int single = getInt(acc, fields[e]);
            for (int i = 0; i < 4; i++) recompute(acc);
            expectEq(getInt(acc, fields[e]), single,
                fields[e] + " stayed flat over five recomputes");
        }

        // Removing the item must zero it again.
        set(w, "itemCount", Integer.valueOf(0));
        recompute(w);
        expectEq(getInt(w, "critPercentBonus"), 0,
            "dropping the item returns the crit bonus to zero");
    }

    /**
     * EFFECT_NEGATE is consumed in damagePlayer, not recomputeStats. It rolls {@code rand(100) <
     * d.value}, so a single trial proves nothing; this sweeps a fixed set of seeds and requires
     * that the item both blocks and fails to block, which can only happen if the check is live.
     */
    private static void testNegateItemCanBlockDamage() throws Exception {
        System.out.println("[12] the negate item can block an incoming hit");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_NEGATE);
        expect(idx >= 0, "a negate item exists");
        ContentCatalog.ItemDef d = itemAt(idx);
        expect(d.value > 0 && d.value < 100, "the negate chance is a real probability");

        int blocked = 0, passed = 0;
        for (int seed = 1; seed <= 60; seed++) {
            Object w = world(0);
            set(w, "random", new Random(seed));
            addItem(w, idx, 1);
            damagePlayer(w, 20, 1000L + seed);
            if (getInt(w, "playerHp") == 100) {
                blocked++;
                expect(getInt(w, "shield") >= 1,
                    "a blocked hit granted a shield charge (seed " + seed + ")");
            } else {
                passed++;
                expectEq(getInt(w, "playerHp"), 100 - Math.max(1, 20),
                    "an unblocked hit did full damage (seed " + seed + ")");
            }
        }
        expect(blocked > 0, "the negate item blocked at least one hit (" + blocked + " of 60)");
        expect(passed > 0, "and failed to block at least one (" + passed + " of 60)");

        // Without the item nothing is ever negated, so the comparison is meaningful.
        int unguardedBlocked = 0;
        for (int seed = 1; seed <= 60; seed++) {
            Object w = world(0);
            set(w, "random", new Random(seed));
            damagePlayer(w, 20, 1000L + seed);
            if (getInt(w, "playerHp") == 100) unguardedBlocked++;
        }
        expectEq(unguardedBlocked, 0,
            "the same sweep without the item never blocked anything");
    }

    /** Raising the maximum must not leave the player above it. */
    private static void testCurrentHpIsClampedToTheNewMaximum() throws Exception {
        System.out.println("[13] current HP is clamped to the new maximum");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_MAX_HP);
        Object w = world(0);
        set(w, "playerHp", Integer.valueOf(100));

        // Recompute with the item present: the ceiling rises, so HP is still under it.
        addItem(w, idx, 1);
        recompute(w);
        expect(getInt(w, "playerHp") <= getInt(w, "playerMaxHp"),
            "HP is not above the raised ceiling");

        // Now shrink the ceiling by dropping the item while HP is high.
        set(w, "playerHp", Integer.valueOf(getInt(w, "playerMaxHp")));
        set(w, "itemCount", Integer.valueOf(0));
        recompute(w);
        expectEq(getInt(w, "playerMaxHp"), 100, "the ceiling returned to the base");
        expectEq(getInt(w, "playerHp"), 100, "and HP was clamped down to match");
    }

    /** Endless mode pins max HP to 1 regardless of items; that override must survive recompute. */
    private static void testEndlessModeKeepsMaximumHpAtOne() throws Exception {
        System.out.println("[14] endless mode pins the maximum HP at one");
        int endless = 1;
        expect("endless".equals(ContentCatalog.mode(endless).id), "mode 1 is Endless Mode");
        int idx = firstItemWithEffect(ContentCatalog.EFFECT_MAX_HP);

        Object w = world(endless);
        set(w, "baseMaxHp", Integer.valueOf(1));
        addItem(w, idx, 3);
        recompute(w);
        expectEq(getInt(w, "playerMaxHp"), 1,
            "a max-HP item cannot lift the ceiling in endless mode");
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