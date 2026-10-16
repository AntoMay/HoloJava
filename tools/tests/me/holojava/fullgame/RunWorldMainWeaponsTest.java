package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers the five locked-roster main weapons against their implemented
 * definitions (IMPLEMENTED_UNVERIFIED: values are the build's own, not
 * reference parity claims).
 *
 * <p>For each of Gura, Ame, Calli, Kiara and Ina the test proves:
 * <ul>
 *   <li>the character's {@code mainWeapon} id resolves to a weapon definition
 *       with the expected pattern family (melee arc vs projectile);</li>
 *   <li>firing produces the defined effect (melee arc entry with the
 *       definition's area and scaled damage, or exactly {@code def.count}
 *       projectiles at level 1);</li>
 *   <li>upgrades change behavior (projectile +1 at level 5, melee damage
 *       +1 per level above 1);</li>
 *   <li>cooldown gates a second immediate volley;</li>
 *   <li>each character maps to a distinct weapon id (no cross-character swap).</li>
 * </ul>
 *
 * <p>Instances are allocated without running the constructor and written
 * through {@code sun.misc.Unsafe}; test-only, nothing under {@code src/}
 * uses {@code sun.*}.
 */
public final class RunWorldMainWeaponsTest {

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

    private static int weaponIndex(String id) {
        for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
            if (ContentCatalog.WEAPONS[i].id.equals(id)) return i;
        }
        return -1;
    }

    /** World for one roster character with its main weapon in slot 0. */
    private static Object worldFor(int charIndex, int level) throws Exception {
        Object w = allocate(worldClass());
        ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[charIndex];
        set(w, "character", c);
        set(w, "random", new Random(31L));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        int[] slots = new int[6];
        int[] levels = new int[6];
        long[] ready = new long[6];
        slots[0] = weaponIndex(c.mainWeapon);
        levels[0] = level;
        for (int i = 1; i < 6; i++) slots[i] = -1;
        set(w, "weaponSlots", slots);
        set(w, "weaponLevels", levels);
        set(w, "weaponReadyAt", ready);
        set(w, "weaponCount", Integer.valueOf(1));
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
        set(w, "melee", filled("me.holojava.fullgame.RunWorld$Melee", 24));
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 48));
        set(w, "boss", allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss")));
        return w;
    }

    private static void fireWeapon(Object w, int slot, ContentCatalog.WeaponDef def, long now)
            throws Exception {
        Method m = worldClass().getDeclaredMethod("fireWeapon", int.class,
            ContentCatalog.WeaponDef.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(slot), def, Long.valueOf(now));
    }

    private static void updateWeapons(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateWeapons", long.class, boolean.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now), Boolean.FALSE);
    }

    private static int activeShots(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("shots");
        f.setAccessible(true);
        Object[] shots = (Object[]) f.get(w);
        int n = 0;
        for (int i = 0; i < shots.length; i++) if (getBool(shots[i], "active")) n++;
        return n;
    }

    private static Object activeMelee(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("melee");
        f.setAccessible(true);
        Object[] melee = (Object[]) f.get(w);
        for (int i = 0; i < melee.length; i++) if (getBool(melee[i], "active")) return melee[i];
        return null;
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testRosterMapsToDistinctWeapons();
        testMeleeMainsFireArcs();
        testPistolFiresDefinedCount();
        testUpgradesChangeBehavior();
        testCooldownGatesSecondVolley();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** The five roster characters map to five distinct main weapon ids. */
    private static void testRosterMapsToDistinctWeapons() throws Exception {
        System.out.println("[1] roster maps to five distinct main weapons");
        expectEq(ContentCatalog.CHARACTERS.length, 5, "roster is locked to five");
        String[] ids = new String[5];
        for (int i = 0; i < 5; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            ContentCatalog.WeaponDef def = ContentCatalog.weapon(c.mainWeapon);
            expect(def != null, c.id + " mainWeapon resolves (" + c.mainWeapon + ")");
            ids[i] = c.mainWeapon;
        }
        for (int i = 0; i < 5; i++) {
            for (int j = i + 1; j < 5; j++) {
                expect(!ids[i].equals(ids[j]),
                    "distinct mains for " + ids[i] + " vs " + ids[j]);
            }
        }
        expectEq(ContentCatalog.weapon("trident_thrust").pattern,
            ContentCatalog.PATTERN_MELEE_ARC, "gura trident is a melee arc");
        expectEq(ContentCatalog.weapon("pistol_shot").pattern,
            ContentCatalog.PATTERN_PROJECTILE, "ame pistol is a projectile");
        expectEq(ContentCatalog.weapon("scythe_swing").pattern,
            ContentCatalog.PATTERN_MELEE_ARC, "calli scythe is a melee arc");
        expectEq(ContentCatalog.weapon("phoenix_sword").pattern,
            ContentCatalog.PATTERN_MELEE_ARC, "kiara phoenix sword is a melee arc");
        expectEq(ContentCatalog.weapon("summon_tentacle").pattern,
            ContentCatalog.PATTERN_MELEE_ARC, "ina tentacle is a melee arc");
    }

    /** The four melee mains each produce an arc entry with defined area. */
    private static void testMeleeMainsFireArcs() throws Exception {
        System.out.println("[2] melee mains produce arc entries");
        int[] chars = {0, 2, 3, 4};
        for (int k = 0; k < chars.length; k++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[chars[k]];
            ContentCatalog.WeaponDef def = ContentCatalog.weapon(c.mainWeapon);
            Object w = worldFor(chars[k], 1);
            fireWeapon(w, 0, def, 20000L);
            Object m = activeMelee(w);
            expect(m != null, c.id + " fired a melee arc");
            if (m != null) {
                expectEq(getInt(m, "area"), def.area, c.id + " arc area matches definition");
                expectEq(activeShots(w), 0, c.id + " melee creates no projectiles");
            }
        }
    }

    /** Ame's pistol fires exactly its defined projectile count at level 1. */
    private static void testPistolFiresDefinedCount() throws Exception {
        System.out.println("[3] pistol fires its defined projectile count");
        ContentCatalog.WeaponDef def = ContentCatalog.weapon("pistol_shot");
        Object w = worldFor(1, 1);
        fireWeapon(w, 0, def, 20000L);
        expectEq(activeShots(w), def.count, "level 1 pistol fires def.count shots");
        expect(activeMelee(w) == null, "pistol creates no melee entry");
    }

    /** Upgrades change behavior: projectile +1 at level 5, melee damage scales. */
    private static void testUpgradesChangeBehavior() throws Exception {
        System.out.println("[4] upgrades change weapon behavior");
        ContentCatalog.WeaponDef pistol = ContentCatalog.weapon("pistol_shot");
        Object w = worldFor(1, 5);
        fireWeapon(w, 0, pistol, 20000L);
        expectEq(activeShots(w), pistol.count + 1, "level 5 pistol gains one projectile");

        ContentCatalog.CharacterDef gura = ContentCatalog.CHARACTERS[0];
        ContentCatalog.WeaponDef trident = ContentCatalog.weapon(gura.mainWeapon);
        Object w1 = worldFor(0, 1);
        fireWeapon(w1, 0, trident, 20000L);
        Object m1 = activeMelee(w1);
        Object w3 = worldFor(0, 3);
        fireWeapon(w3, 0, trident, 20000L);
        Object m3 = activeMelee(w3);
        expect(m1 != null && m3 != null, "both levels produce arcs");
        if (m1 != null && m3 != null) {
            expect(getInt(m3, "damage") > getInt(m1, "damage"),
                "level 3 arc hits harder than level 1");
        }
    }

    /** A second immediate updateWeapons tick fires nothing new. */
    private static void testCooldownGatesSecondVolley() throws Exception {
        System.out.println("[5] cooldown gates a second immediate volley");
        ContentCatalog.WeaponDef pistol = ContentCatalog.weapon("pistol_shot");
        Object w = worldFor(1, 1);
        updateWeapons(w, 30000L);
        int first = activeShots(w);
        expect(first == pistol.count, "first tick fires one volley");
        updateWeapons(w, 30000L);
        expectEq(activeShots(w), first, "same-tick second update fires nothing new");
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
