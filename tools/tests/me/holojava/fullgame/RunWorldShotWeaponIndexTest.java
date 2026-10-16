package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Regression for the shot weapon-index mix-up: fireWeapon stored the
 * inventory slot (0..5) in Shot.weaponIndex, but updateShots resolves
 * homing steering speed via ContentCatalog.weapon(s.weaponIndex), which
 * expects a catalog index. A homing weapon fired from slot 0 steered with
 * the wrong weapon's speed (e.g. slot 0 resolved to trident_thrust).
 *
 * <p>Proves fired shots carry the catalog index and steer with their own
 * weapon's speed. Instances are allocated without running the constructor
 * and written through {@code sun.misc.Unsafe}; test-only, nothing under
 * {@code src/} uses {@code sun.*}.
 */
public final class RunWorldShotWeaponIndexTest {

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

    private static ContentCatalog.WeaponDef weapon(String id) {
        return ContentCatalog.weapon(id);
    }

    /** Minimal world with two weapons equipped and empty pools. */
    private static Object world() throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "random", new Random(7L));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        int[] slots = new int[6];
        int[] levels = new int[6];
        slots[0] = weaponIndex("sausage");
        slots[1] = weaponIndex("bounce_ball");
        slots[2] = weaponIndex("pistol_shot");
        levels[0] = 1;
        levels[1] = 1;
        levels[2] = 1;
        for (int i = 3; i < 6; i++) slots[i] = -1;
        set(w, "weaponSlots", slots);
        set(w, "weaponLevels", levels);
        set(w, "weaponCount", Integer.valueOf(3));
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
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

    private static Object firstActiveShot(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("shots");
        f.setAccessible(true);
        Object[] shots = (Object[]) f.get(w);
        for (int i = 0; i < shots.length; i++) {
            if (getBool(shots[i], "active")) return shots[i];
        }
        return null;
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testFiredShotCarriesCatalogIndex();
        testHomingSteersWithOwnWeaponSpeed();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Shots fired from a slot must carry the catalog index, not the slot. */
    private static void testFiredShotCarriesCatalogIndex() throws Exception {
        System.out.println("[1] fired shots carry the catalog weapon index");
        Object w = world();
        int sausage = weaponIndex("sausage");
        int pistol = weaponIndex("pistol_shot");
        expect(sausage != 0 && pistol != 2,
            "test premise holds: catalog indices differ from slots (sausage=" + sausage
            + " in slot 0, pistol=" + pistol + " in slot 2)");

        fireWeapon(w, 0, weapon("sausage"), 10000L);
        Object s = firstActiveShot(w);
        expect(s != null, "sausage fired a shot from slot 0");
        if (s != null) {
            expectEq(getInt(s, "weaponIndex"), sausage,
                "homing shot carries the sausage catalog index, not slot 0");
        }

        Object w2 = world();
        fireWeapon(w2, 2, weapon("pistol_shot"), 10000L);
        Object s2 = firstActiveShot(w2);
        expect(s2 != null, "pistol fired a shot from slot 2");
        if (s2 != null) {
            expectEq(getInt(s2, "weaponIndex"), pistol,
                "projectile shot carries the pistol catalog index, not slot 2");
        }
    }

    /**
     * A homing shot must steer with its own weapon's speed. Sausage speed is
     * nonzero while slot 0 used to resolve to trident_thrust (speed 0), which
     * left homing shots with zero velocity.
     */
    private static void testHomingSteersWithOwnWeaponSpeed() throws Exception {
        System.out.println("[2] homing shots steer with their own weapon speed");
        Object w = world();
        // Place one active enemy to the right of the player.
        Field ef = worldClass().getDeclaredField("enemies");
        ef.setAccessible(true);
        Object[] enemies = (Object[]) ef.get(w);
        set(enemies[0], "active", Boolean.TRUE);
        set(enemies[0], "x", Integer.valueOf(272));
        set(enemies[0], "y", Integer.valueOf(120));
        set(enemies[0], "defIndex", Integer.valueOf(0));

        fireWeapon(w, 0, weapon("sausage"), 10000L);
        Method update = worldClass().getDeclaredMethod("updateShots", long.class);
        update.setAccessible(true);
        update.invoke(w, Long.valueOf(10001L));

        Object s = firstActiveShot(w);
        expect(s != null, "homing shot is still active after one tick");
        if (s != null) {
            int dx = getInt(s, "dx");
            int dy = getInt(s, "dy");
            int magSq = dx * dx + dy * dy;
            expect(magSq > 0, "homing shot has nonzero steering velocity (got " + dx + "," + dy + ")");
            expect(dx > 0, "homing shot steers toward the enemy on the right");
            int speed = weapon("sausage").speed;
            expect(magSq <= speed * speed,
                "steering magnitude does not exceed sausage speed " + speed);
        }
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
