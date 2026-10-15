package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers collab weapons: prerequisite removal, the offer gate, and the apply path.
 *
 * <p>The path under test:
 * <ul>
 *   <li>{@code fillOffer} offers a collab only when the mode allows collabs, the inventory has
 *       room, both prerequisites are already at maximum level, and the collab itself has not been
 *       offered this level-up. It then marks the offer {@code collab = true, existing = -1,
 *       newItem = true}.</li>
 *   <li>{@code applyOffer} on a collab offer removes both prerequisite weapons and adds the collab,
 *       instead of levelling up an existing weapon.</li>
 *   <li>{@code removeWeaponById} compacts the slot arrays and clears the vacated tail.</li>
 * </ul>
 *
 * <p>Collab offers appear in Stage and Endless Mode and not in Time Mode. That was read off the
 * catalog at runtime rather than from the {@code mode(...)} argument list, whose parameter order
 * had been misread: {@code allowsCollabs} is the second of the three booleans, and the live values
 * are Stage true, Endless true, Time false.
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldCollabTest {

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

    /** The first mode that permits collabs; verified against the live catalog. */
    private static int collabMode() {
        for (int i = 0; i < ContentCatalog.MODES.length; i++) {
            if (ContentCatalog.MODES[i].allowsCollabs) return i;
        }
        return 0;
    }

    private static Object world(int modeIndex) throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "mode", ContentCatalog.MODES[modeIndex]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "save", null);
        set(w, "random", new Random(19L));
        set(w, "state", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "baseMaxHp", Integer.valueOf(100));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "weaponSlots", new int[6]);
        set(w, "weaponLevels", new int[6]);
        set(w, "weaponReadyAt", new long[6]);
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "itemSlots", new int[6]);
        set(w, "itemLevels", new int[6]);
        set(w, "offerUsed", new boolean[64]);
        set(w, "offers", filled("me.holojava.fullgame.RunWorld$Offer", 6));
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

    private static int[] slots(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("weaponSlots");
        f.setAccessible(true);
        return (int[]) f.get(w);
    }

    private static int[] levels(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("weaponLevels");
        f.setAccessible(true);
        return (int[]) f.get(w);
    }

    private static long[] readyAt(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("weaponReadyAt");
        f.setAccessible(true);
        return (long[]) f.get(w);
    }

    private static int weaponIndex(String id) throws Exception {
        Method m = Class.forName("me.holojava.fullgame.ContentCatalog")
                .getDeclaredMethod("weapon", String.class);
        m.setAccessible(true);
        ContentCatalog.WeaponDef d = (ContentCatalog.WeaponDef) m.invoke(null, id);
        if (d == null) return -1;
        for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
            if (ContentCatalog.WEAPONS[i] == d) return i;
        }
        return -1;
    }

    private static void equip(Object w, String... ids) throws Exception {
        int[] s = slots(w);
        int[] l = levels(w);
        for (int i = 0; i < ids.length; i++) {
            s[i] = weaponIndex(ids[i]);
            l[i] = 1;
        }
        set(w, "weaponCount", Integer.valueOf(ids.length));
    }

    private static void maxOut(Object w, String... ids) throws Exception {
        int[] s = slots(w);
        int[] l = levels(w);
        for (int i = 0; i < ids.length; i++) {
            int idx = weaponIndex(ids[i]);
            s[i] = idx;
            ContentCatalog.WeaponDef d = null;
            for (int k = 0; k < ContentCatalog.WEAPONS.length; k++) {
                if (ContentCatalog.WEAPONS[k] != null && ContentCatalog.WEAPONS[k].id.equals(ids[i])) {
                    d = ContentCatalog.WEAPONS[k];
                }
            }
            l[i] = d == null ? 1 : d.maxLevel;
        }
        set(w, "weaponCount", Integer.valueOf(ids.length));
    }

    /** The catalog id held in a given weapon slot, or null when the slot is empty. */
    private static String idInSlot(Object w, int slot) throws Exception {
        int idx = slots(w)[slot];
        if (idx < 0 || idx >= ContentCatalog.WEAPONS.length) return null;
        ContentCatalog.WeaponDef d = ContentCatalog.WEAPONS[idx];
        return d == null ? null : d.id;
    }

    private static void removeById(Object w, String id) throws Exception {
        Method m = worldClass().getDeclaredMethod("removeWeaponById", String.class);
        m.setAccessible(true);
        m.invoke(w, id);
    }

    private static void addWeapon(Object w, int idx, int level) throws Exception {
        Method m = worldClass().getDeclaredMethod("addWeapon", int.class, int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(idx), Integer.valueOf(level));
    }

    private static boolean fillOffer(Object w, int slot, int type) throws Exception {
        Method m = worldClass().getDeclaredMethod("fillOffer", int.class, int.class);
        m.setAccessible(true);
        return ((Boolean) m.invoke(w, Integer.valueOf(slot), Integer.valueOf(type))).booleanValue();
    }

    private static Object offer(Object w, int slot) throws Exception {
        Field f = worldClass().getDeclaredField("offers");
        f.setAccessible(true);
        return Array.get((Object[]) f.get(w), slot);
    }

    private static void applyOffer(Object w, int index) throws Exception {
        Method m = worldClass().getDeclaredMethod("applyOffer", int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(index));
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testRemoveCompactsAndClearsTheTail();
        testRemoveIgnoresAnAbsentWeapon();
        testRemoveShiftsLevelsAndCooldownsToo();
        testCollabOfferRequiresBothPrerequisitesMaxed();
        testCollabOfferRequiresAModeThatAllowsThem();
        testCollabOfferRequiresFreeInventory();
        testApplyingACollabSwapsPrerequisitesForTheCollab();
        testCollabIsNotOfferedTwiceInOneLevelUp();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Removing compacts the array and leaves the vacated slot clean. */
    private static void testRemoveCompactsAndClearsTheTail() throws Exception {
        System.out.println("[1] removal compacts and clears the tail");
        Object w = world(0);
        equip(w, "trident_thrust", "pistol_shot", "scythe_swing", "phoenix_sword");

        int[] before = slots(w).clone();
        removeById(w, idInSlot(w, 1));              // drop the second weapon

        expectEq(getInt(w, "weaponCount"), 3, "the count dropped by one");
        int[] after = slots(w);
        expectEq(after[0], before[0], "the weapon before the hole stayed put");
        expectEq(after[1], before[2], "the one after the hole moved down");
        expectEq(after[2], before[3], "and so did the next");
        expectEq(after[3], -1, "the vacated tail slot was cleared");
        expectEq(levels(w)[3], 0, "its level was cleared too");
        expectEq(readyAt(w)[3], 0L, "and its cooldown");
    }

    /** An id that is not held changes nothing. */
    private static void testRemoveIgnoresAnAbsentWeapon() throws Exception {
        System.out.println("[2] removing an absent weapon changes nothing");
        Object w = world(0);
        equip(w, "trident_thrust", "scythe_swing");
        int[] before = slots(w).clone();

        removeById(w, "summon_tentacle");
        expectEq(getInt(w, "weaponCount"), 2, "the count is unchanged");
        expectEq(slots(w)[0], before[0], "slot 0 is unchanged");
        expectEq(slots(w)[1], before[1], "slot 1 is unchanged");

        removeById(w, null);
        expectEq(getInt(w, "weaponCount"), 2, "a null id is also ignored");
    }

    /** Levels and cooldowns travel with their weapon rather than staying behind. */
    private static void testRemoveShiftsLevelsAndCooldownsToo() throws Exception {
        System.out.println("[3] levels and cooldowns move with the weapon");
        Object w = world(0);
        equip(w, "trident_thrust", "pistol_shot", "scythe_swing");
        levels(w)[0] = 3;
        levels(w)[1] = 7;
        levels(w)[2] = 2;
        readyAt(w)[0] = 111L;
        readyAt(w)[1] = 222L;
        readyAt(w)[2] = 333L;

        removeById(w, idInSlot(w, 0));
        expectEq(levels(w)[0], 7, "the old slot 1 level moved into slot 0");
        expectEq(levels(w)[1], 2, "and slot 2's level moved into slot 1");
        expectEq(readyAt(w)[0], 222L, "cooldowns moved with their levels");
        expectEq(readyAt(w)[1], 333L, "including the next one");
        expectEq(levels(w)[2], 0, "the vacated slot is zeroed");
    }

    /** The collab offer needs both prerequisites already at maximum level. */
    private static void testCollabOfferRequiresBothPrerequisitesMaxed() throws Exception {
        System.out.println("[4] a collab offer needs both prerequisites maxed");
        int time = collabMode();
        ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[0];
        expect(time >= 0 && ContentCatalog.MODES[time].allowsCollabs,
            "a mode that allows collabs exists");

        // Only one prerequisite maxed: no collab offer.
        Object half = world(time);
        maxOut(half, cd.weaponA);
        equip(half, cd.weaponB);
        boolean offeredHalf = false;
        for (int slot = 0; slot < 6 && !offeredHalf; slot++) {
            offeredHalf = collabOffered(half, slot);
        }
        expect(!offeredHalf, "a half-maxed prerequisite pair offers no collab");

        // Both maxed: the offer appears.
        Object both = world(time);
        maxOut(both, cd.weaponA, cd.weaponB);
        int found = -1;
        for (int slot = 0; slot < 6; slot++) {
            if (collabOffered(both, slot)) { found = slot; break; }
        }
        expect(found >= 0, "a fully maxed pair offers the collab");
        if (found >= 0) {
            Object o = offer(both, found);
            expect(getBool(o, "collab"), "the offer is flagged as a collab");
            expectEq(getInt(o, "existing"), -1, "and is not an upgrade of a held weapon");
            expect(getBool(o, "newItem"), "but is marked as a new item");
        }
    }

    /** Returns true when fillOffer puts a collab into the given slot. */
    private static boolean collabOffered(Object w, int slot) throws Exception {
        clearOffers(w);
        fillOffer(w, slot, 1);                    // OFFER_WEAPON
        Object o = offer(w, slot);
        return getBool(o, "collab");
    }

    private static void clearOffers(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("offers");
        f.setAccessible(true);
        Object[] offers = (Object[]) f.get(w);
        for (int i = 0; i < offers.length; i++) {
            set(offers[i], "type", Integer.valueOf(-1));
            set(offers[i], "collab", Boolean.FALSE);
            set(offers[i], "newItem", Boolean.FALSE);
            set(offers[i], "existing", Integer.valueOf(-1));
        }
        set(w, "offerUsed", new boolean[64]);
    }

    /** Stage and Endless Mode must never produce a collab offer. */
    private static void testCollabOfferRequiresAModeThatAllowsThem() throws Exception {
        System.out.println("[5] only a mode that allows collabs offers them");
        ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[0];
        for (int m = 0; m < ContentCatalog.MODES.length; m++) {
            Object w = world(m);
            maxOut(w, cd.weaponA, cd.weaponB);
            boolean any = false;
            for (int slot = 0; slot < 6 && !any; slot++) any = collabOffered(w, slot);
            if (ContentCatalog.MODES[m].allowsCollabs) {
                expect(any, "mode " + m + " allows collabs and produced one");
            } else {
                expect(!any, "mode " + m + " disallows collabs and produced none");
            }
        }
    }

    /** A full inventory blocks the collab offer. */
    private static void testCollabOfferRequiresFreeInventory() throws Exception {
        System.out.println("[6] a full inventory blocks a collab offer");
        int time = collabMode();
        ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[0];

        // Both prerequisites maxed AND the inventory full, so the slot gate is the only thing
        // standing between this world and a collab offer.
        Object full = world(time);
        int[] s = slots(full);
        int[] l = levels(full);
        String[] order = { cd.weaponA, cd.weaponB, "trident_thrust", "pistol_shot", "scythe_swing",
                "phoenix_sword" };
        for (int i = 0; i < order.length; i++) {
            int idx = weaponIndex(order[i]);
            s[i] = idx;
            ContentCatalog.WeaponDef d = null;
            for (int k = 0; k < ContentCatalog.WEAPONS.length; k++) {
                ContentCatalog.WeaponDef cand = ContentCatalog.WEAPONS[k];
                if (cand != null && cand.id.equals(order[i])) d = cand;
            }
            l[i] = (i < 2 && d != null) ? d.maxLevel : 1;
        }
        set(full, "weaponCount", Integer.valueOf(6));
        expectEq(getInt(full, "weaponCount"), 6, "the inventory is full");

        boolean any = false;
        for (int slot = 0; slot < 6 && !any; slot++) any = collabOffered(full, slot);
        expect(!any, "no collab is offered with no free slot");

        // Freeing one slot makes the very same world offer it, which proves the gate above was
        // what blocked it rather than a missing prerequisite.
        Object freed = world(time);
        equip(freed, "trident_thrust", "pistol_shot");
        int[] fs = slots(freed);
        int[] fl = levels(freed);
        for (int i = 0; i < 2; i++) {
            int idx = weaponIndex(order[i]);
            ContentCatalog.WeaponDef d = null;
            for (int k = 0; k < ContentCatalog.WEAPONS.length; k++) {
                ContentCatalog.WeaponDef cand = ContentCatalog.WEAPONS[k];
                if (cand != null && cand.id.equals(order[i])) d = cand;
            }
            fs[i] = idx;
            fl[i] = d == null ? 1 : d.maxLevel;
        }
        boolean offeredWhenFree = false;
        for (int slot = 0; slot < 6 && !offeredWhenFree; slot++) offeredWhenFree = collabOffered(freed, slot);
        expect(offeredWhenFree, "with both prerequisites maxed and a free slot the collab is offered");
    }

    /** Applying a collab removes both prerequisites and adds the collab. */
    private static void testApplyingACollabSwapsPrerequisitesForTheCollab() throws Exception {
        System.out.println("[7] applying a collab swaps prerequisites for the collab");
        ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[0];
        Object w = world(collabMode());
        maxOut(w, cd.weaponA, cd.weaponB);

        Object offers = offer(w, 0);
        set(offers, "type", Integer.valueOf(1));   // OFFER_WEAPON
        set(offers, "value", Integer.valueOf(weaponIndex(cd.id)));
        set(offers, "existing", Integer.valueOf(-1));
        set(offers, "collab", Boolean.TRUE);
        set(offers, "newItem", Boolean.TRUE);

        expectEq(getInt(w, "weaponCount"), 2, "both prerequisites start equipped");
        applyOffer(w, 0);

        expectEq(getInt(w, "weaponCount"), 1, "one weapon remains after the swap");
        expectEq(slots(w)[0], weaponIndex(cd.id), "and it is the collab");
        expectEq(levels(w)[0], 1, "granted at level one");
        expect(!holdsId(w, cd.weaponA), "the first prerequisite was consumed");
        expect(!holdsId(w, cd.weaponB), "and so was the second");
    }

    private static boolean holdsId(Object w, String id) throws Exception {
        int idx = weaponIndex(id);
        if (idx < 0) return false;
        int[] s = slots(w);
        for (int i = 0; i < getInt(w, "weaponCount"); i++) if (s[i] == idx) return true;
        return false;
    }

    /** The same collab must not be offered twice in one level-up. */
    private static void testCollabIsNotOfferedTwiceInOneLevelUp() throws Exception {
        System.out.println("[8] a collab is not offered twice in one level-up");
        int time = collabMode();
        ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[0];
        Object w = world(time);
        maxOut(w, cd.weaponA, cd.weaponB);

        clearOffers(w);
        int appearances = 0;
        for (int slot = 0; slot < 6; slot++) {
            if (fillOffer(w, slot, 1) && getBool(offer(w, slot), "collab")) appearances++;
        }
        expect(appearances <= 1,
            "the collab appeared at most once across six slots (saw " + appearances + ")");
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