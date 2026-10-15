package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers HoloBomb's explosion path: selecting the weapon must succeed, and the
 * first explosion must not crash when active enemies sit in high pool slots.
 *
 * <p>Root cause under test: {@code explode()} computed the enemy centre with
 * {@code enemyWidth(poolIndex)} instead of {@code enemyWidth(enemy.defIndex)}.
 * Since the enemy pool (48) is larger than the enemy catalog (13), any active
 * enemy at pool index 13 or above made HoloBomb (and Black Plague) throw
 * {@code ArrayIndexOutOfBoundsException} on its first explosion. Selection via
 * {@code applyOffer} always succeeded; the crash surfaced one tick later when
 * the AOE projectile expired or hit.
 */
public final class RunWorldExplodeTest {

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
        return ((Number) get(target, field)).intValue();
    }

    private static boolean getBool(Object target, String field) {
        return ((Boolean) get(target, field)).booleanValue();
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Object[] filled(Class<?> component, int count) throws Exception {
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
        return (Object[]) array;
    }

    private static Object world() throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "save", null);
        set(w, "random", new Random(77L));
        set(w, "state", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "baseMaxHp", Integer.valueOf(100));
        set(w, "kills", Integer.valueOf(0));
        set(w, "defeatCount", Integer.valueOf(0));
        set(w, "lifestealUntil", Long.valueOf(0L));
        set(w, "expPercentBonus", Integer.valueOf(0));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "weaponSlots", new int[6]);
        set(w, "weaponLevels", new int[6]);
        set(w, "weaponReadyAt", new long[6]);
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "enemies", filled(Class.forName("me.holojava.fullgame.RunWorld$Enemy"), 48));
        set(w, "shots", filled(Class.forName("me.holojava.fullgame.RunWorld$Shot"), 128));
        set(w, "pickups", filled(Class.forName("me.holojava.fullgame.RunWorld$Pickup"), 96));
        set(w, "offers", filled(Class.forName("me.holojava.fullgame.RunWorld$Offer"), 6));
        set(w, "offerUsed", new boolean[64]);
        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
        set(boss, "x", Integer.valueOf(0));
        set(boss, "y", Integer.valueOf(0));
        set(boss, "defIndex", Integer.valueOf(0));
        set(boss, "hitMask", Long.valueOf(0L));
        set(w, "boss", boss);
        return w;
    }

    private static void placeEnemy(Object w, int poolIndex, int x, int y, int hp) throws Exception {
        Object[] pool = (Object[]) get(w, "enemies");
        Object e = pool[poolIndex];
        set(e, "active", Boolean.TRUE);
        set(e, "defIndex", Integer.valueOf(0));
        set(e, "x", Integer.valueOf(x));
        set(e, "y", Integer.valueOf(y));
        set(e, "hp", Integer.valueOf(hp));
        set(e, "maxHp", Integer.valueOf(hp));
    }

    private static void explode(Object w, int x, int y, int radius, int damage) throws Exception {
        Method m = worldClass().getDeclaredMethod("explode", int.class, int.class, int.class, int.class,
                boolean.class);
        m.setAccessible(true);
        try {
            m.invoke(w, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(radius),
                    Integer.valueOf(damage), Boolean.FALSE);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("explode failed", cause);
        }
    }

    private static int weaponIndex(String id) {
        for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
            if (ContentCatalog.WEAPONS[i].id.equals(id)) return i;
        }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        testExplodeAtHighPoolIndexDoesNotCrash();
        testExplodeDamagesOnlyEnemiesInRadius();
        testHoloBombOfferAppliesAndFires();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /**
     * Regression: an active enemy at pool index 20 (beyond the 13-entry enemy
     * catalog) must not crash the explosion. Before the fix this threw
     * ArrayIndexOutOfBoundsException from enemyWidth(poolIndex).
     */
    private static void testExplodeAtHighPoolIndexDoesNotCrash() throws Exception {
        System.out.println("[1] explode with an enemy at high pool index does not crash");
        Object w = world();
        // Pool slot 20 is beyond ContentCatalog.ENEMIES.length (13); defIndex stays valid (0).
        placeEnemy(w, 20, 160, 120, 500);
        boolean threw = false;
        try {
            explode(w, 160, 120, 28, 4);
        } catch (ArrayIndexOutOfBoundsException e) {
            threw = true;
        }
        expect(!threw, "explode did not throw ArrayIndexOutOfBoundsException");
        Object[] pool = (Object[]) get(w, "enemies");
        expectEq(getInt(pool[20], "hp"), 496, "enemy at pool 20 took 4 damage");
    }

    /**
     * The explosion uses the enemy's catalog dimensions for its centre, so an
     * enemy outside the radius is untouched while one inside is damaged.
     */
    private static void testExplodeDamagesOnlyEnemiesInRadius() throws Exception {
        System.out.println("[2] explode damages only enemies inside the radius");
        Object w = world();
        placeEnemy(w, 14, 160, 120, 500);   // at the blast centre
        placeEnemy(w, 30, 0, 0, 500);       // far outside radius 28
        explode(w, 160, 120, 28, 4);
        Object[] pool = (Object[]) get(w, "enemies");
        expect(getInt(pool[14], "hp") < 500, "enemy inside the radius took damage");
        expectEq(getInt(pool[30], "hp"), 500, "enemy outside the radius was untouched");
    }

    /**
     * Selecting HoloBomb via applyOffer succeeds and fireWeapon creates an AOE
     * projectile with the bomb sprite. This pins the selection path as
     * crash-free; the historical crash was at explosion time, not selection.
     */
    private static void testHoloBombOfferAppliesAndFires() throws Exception {
        System.out.println("[3] HoloBomb offer applies and fires an AOE shot");
        Object w = world();
        int holoBomb = weaponIndex("holo_bomb");
        expect(holoBomb >= 0, "holo_bomb exists in the weapon catalog");

        // Simulate the level-up offer path: OFFER_WEAPON with the catalog index.
        set(w, "state", Integer.valueOf(2)); // STATE_LEVEL_UP
        set(w, "offerCount", Integer.valueOf(4));
        set(w, "selectedOffer", Integer.valueOf(0));
        Class<?> offerClass = Class.forName("me.holojava.fullgame.RunWorld$Offer");
        Object o = allocate(offerClass);
        set(o, "type", Integer.valueOf(1)); // OFFER_WEAPON
        set(o, "value", Integer.valueOf(holoBomb));
        set(o, "existing", Integer.valueOf(-1));
        set(o, "collab", Boolean.FALSE);
        set(o, "newItem", Boolean.TRUE);
        Object[] offers = (Object[]) get(w, "offers");
        // offers array may be null-filled elements; ensure slot exists
        if (offers == null || offers.length == 0) throw new IllegalStateException("offers not initialized");
        offers[0] = o;
        Method apply = worldClass().getDeclaredMethod("applyOffer", int.class);
        apply.setAccessible(true);
        apply.invoke(w, Integer.valueOf(0));
        expectEq(getInt(w, "weaponCount"), 1, "HoloBomb was equipped");
        int[] slots = (int[]) get(w, "weaponSlots");
        expectEq(slots[0], holoBomb, "slot 0 holds holo_bomb");

        // Fire it: an AOE shot with the bomb sprite must appear.
        set(w, "state", Integer.valueOf(0)); // STATE_RUNNING
        placeEnemy(w, 5, 220, 120, 500);
        ContentCatalog.WeaponDef def = ContentCatalog.WEAPONS[holoBomb];
        Method fire = worldClass().getDeclaredMethod("fireWeapon", int.class,
                ContentCatalog.WeaponDef.class, long.class);
        fire.setAccessible(true);
        fire.invoke(w, Integer.valueOf(0), def, Long.valueOf(1000L));
        Object[] shots = (Object[]) get(w, "shots");
        boolean found = false;
        for (int i = 0; i < shots.length; i++) {
            if (getBool(shots[i], "active")
                    && "bomb".equals((String) get(shots[i], "sprite"))) {
                found = true;
                break;
            }
        }
        expect(found, "firing HoloBomb created an active bomb shot");
    }

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
