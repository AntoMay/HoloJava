package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Proves that one gameplay event is processed exactly once.
 *
 * <p>The mission rule under test: a single event must never process damage, death, reward, or
 * pickup more than once. These are the two places where that is easiest to get wrong, because both
 * rely on the <em>call site</em> guarding against a repeat rather than the damage method refusing
 * one:
 *
 * <ul>
 *   <li>{@code damagePlayer} has no internal idempotence; it is protected only by
 *       {@code state != STATE_RUNNING || playerInvulnerable(now)}, and it sets {@code invulUntil}
 *       to {@code now + 800} as a side effect of landing a hit.</li>
 *   <li>{@code damageEnemy} awards {@code kills} and {@code defeatCount} with no internal guard at
 *       all; a second lethal call would pay the reward twice. It is protected only by the
 *       {@code if (!enemy.active) continue} and {@code hitMask} checks in {@code updateShots}.</li>
 * </ul>
 *
 * <p><b>Construction.</b> {@code RunWorld} cannot be built on a desktop JVM: its constructor
 * reaches {@code Image.createImage}, whose MIDP stub throws an {@code Error} that the library's
 * {@code catch (Exception)} does not catch (confirmed, not assumed). Instances here are allocated
 * without running the constructor, and only the fields the exercised methods touch are set.
 *
 * <p><b>Not proven.</b> Anything involving spawned entities, assets, rendering, or the magnet. Those
 * need an emulator or a handset.
 */
public final class RunWorldEventOnceTest {

    private static int checks = 0;
    private static int failures = 0;

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

    /** Reads an int field from a pool object whose class is not the caller's own. */
    private static int getIntOn(Object target, String field) {
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

    private static void setInt(Object target, String field, int value) {
        set(target, field, Integer.valueOf(value));
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Class<?> enemyClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Enemy");
    }

    private static Class<?> shotClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Shot");
    }

    private static Class<?> pickupClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld$Pickup");
    }

    private static Object invoke(Object target, Class<?> param, Object arg, String name)
            throws Exception {
        Method m = worldClass().getDeclaredMethod(name, param);
        m.setAccessible(true);
        return m.invoke(target, arg);
    }

    /** A RunWorld with only the state the damage paths touch. */
    private static Object newWorld() throws Exception {
        Object w = allocate(worldClass());
        set(w, "random", new Random(12345L));
        setInt(w, "worldWidth", 320);
        setInt(w, "worldHeight", 240);
        setInt(w, "state", 0);                 // STATE_RUNNING
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "lifestealUntil", Long.valueOf(0L));
        setInt(w, "playerHp", 100);
        setInt(w, "playerMaxHp", 100);
        setInt(w, "kills", 0);
        setInt(w, "defeatCount", 0);
        setInt(w, "itemCount", 0);
        setInt(w, "defensePercent", 0);
        setInt(w, "shield", 0);
        setInt(w, "defeatCount", 0);
        // Pools the reward and shot paths iterate and write into. An array built by
// java.lang.reflect.Array is null-filled, whereas production fills them with `new Pickup()`, so
// every slot must be materialised here or the loops would read null.
set(w, "pickups", filled(pickupClass(), 96));
        set(w, "enemies", filled(enemyClass(), 48));
        set(w, "shots", filled(shotClass(), 128));
        // The shot loop also consults the single boss slot, which would otherwise read as null.
        set(w, "boss", newBoss());
        return w;
    }

    /** A single inactive boss; enough for the pool loop to short-circuit on it. */
    private static Object newBoss() throws Exception {
        Object b = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(b, "active", Boolean.FALSE);
        setInt(b, "x", 0);
        setInt(b, "y", 0);
        setInt(b, "defIndex", 0);
        setInt(b, "hp", 0);
        set(b, "hitMask", Long.valueOf(0L));
        return b;
    }

    /** An array of the given component type with every slot allocated and deactivated. */
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

    public static void main(String[] args) throws Exception {
        testPlayerHitLandsOnceThenInvulnerabilityHolds();
        testPlayerNotRunningTakesNoDamage();
        testEnemyDeathAwardsExactlyOneKill();
        testDeadEnemyIsSkippedByTheShotLoop();
        testTwoShotsCannotBothPayTheSameKill();
        testPiercingShotHitsAnEnemyOnlyOnce();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ---- 1. Player damage and the invulnerability window -------------------------

    /**
     * The first hit must land and open the window; the second, inside the 800 ms window, must be
     * ignored. If the window did not open, a single touching enemy would drain the player many
     * times per second.
     */
    private static void testPlayerHitLandsOnceThenInvulnerabilityHolds() throws Exception {
        System.out.println("[1] player damage lands once, then invulnerability holds");
        Object w = newWorld();
        long t = 100000L;

        damagePlayer(w, 10, t);
        expectEq(getInt(w, "playerHp"), 90, "the first hit lands");

        damagePlayer(w, 10, t + 100);
        expectEq(getInt(w, "playerHp"), 90, "a second hit inside the window is ignored");

        damagePlayer(w, 10, t + 5000);
        expectEq(getInt(w, "playerHp"), 80, "a hit after the window lands again");
    }

    private static void damagePlayer(Object w, int amount, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("damagePlayer", int.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(amount), Long.valueOf(now));
    }

    // ---- 2. A run that is not running must be immune ------------------------------

    private static void testPlayerNotRunningTakesNoDamage() throws Exception {
        System.out.println("[2] a run that is not running takes no damage");
        Object w = newWorld();
        setInt(w, "state", 2);                  // not STATE_RUNNING
        damagePlayer(w, 25, 100000L);
        expectEq(getInt(w, "playerHp"), 100, "HP is untouched outside a running run");
    }

    // ---- 3. A lethal enemy hit pays exactly one kill ------------------------------

    private static void testEnemyDeathAwardsExactlyOneKill() throws Exception {
        System.out.println("[3] an enemy death awards exactly one kill");
        Object w = newWorld();
        Object enemy = newEnemy(0, 10, 20, 20);

        // Lethal damage, once.
        damageEnemy(w, 0, enemy, 999, false, 0, 1, 0);
        expectEq(getInt(w, "kills"), 1, "one kill is recorded");
        expectEq(getInt(w, "defeatCount"), 1, "one defeat is recorded");
        expect(!isActive(enemy), "the enemy is deactivated");
    }

    private static Object newEnemy(int defIndex, int x, int y, int hp) throws Exception {
        Object e = allocate(enemyClass());
        setInt(e, "defIndex", defIndex);
        setInt(e, "x", x);
        setInt(e, "y", y);
        setInt(e, "hp", hp);
        setInt(e, "maxHp", hp);
        set(e, "active", Boolean.TRUE);
        set(e, "kbUntil", Long.valueOf(0L));
        set(e, "healAt", Long.valueOf(0L));
        return e;
    }

    private static boolean isActive(Object e) throws Exception {
        Field f = e.getClass().getDeclaredField("active");
        f.setAccessible(true);
        return f.getBoolean(e);
    }

    private static void damageEnemy(Object w, int index, Object enemy, int dmg, boolean crit,
            int knockback, int dirX, int dirY) throws Exception {
        Method m = worldClass().getDeclaredMethod("damageEnemy", int.class, enemyClass(),
                int.class, boolean.class, int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(index), enemy, Integer.valueOf(dmg), Boolean.valueOf(crit),
                Integer.valueOf(knockback), Integer.valueOf(dirX), Integer.valueOf(dirY));
    }

    // ---- 4. The dead enemy is skipped by the shot loop ---------------------------

    /**
     * The pool loop must not hand a dead enemy to the damage path again. This is the guard that
     * keeps {@code damageEnemy}, which has no internal idempotence, from paying twice.
     */
    private static void testDeadEnemyIsSkippedByTheShotLoop() throws Exception {
        System.out.println("[4] a dead enemy is skipped by the shot loop");
        Object w = newWorld();
        Object enemy = newEnemy(0, 100, 100, 10);
        Object[] pool = (Object[]) getField(w, "enemies");
        pool[0] = enemy;

        damageEnemy(w, 0, enemy, 999, false, 0, 1, 0);
        expectEq(getInt(w, "kills"), 1, "the kill was awarded once");

        // Aim a live player shot straight at the corpse. The pool loop must refuse to hand a dead
        // enemy to damageEnemy, which has no internal idempotence of its own. Without this shot
        // the loop would never even look at the enemy and the guard would go untested.
        placeShot(w, 0, enemyX(enemy), enemyY(enemy), 10);
        setInt(w, "px", 100);
        setInt(w, "py", 100);
        updateShots(w, 200000L);

        expectEq(getInt(w, "kills"), 1, "the dead enemy did not pay a second kill");
    }

    private static Object getField(Object target, String field) throws Exception {
        Field f = worldClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }

    private static int enemyX(Object enemy) throws Exception {
        return getIntOn(enemy, "x");
    }

    private static int enemyY(Object enemy) throws Exception {
        return getIntOn(enemy, "y");
    }

    /** Puts one live player shot at the given world position into the pool. */
    private static void placeShot(Object w, int slot, int x, int y, int damage) throws Exception {
        Object s = allocate(shotClass());
        setInt(s, "x", x);
        setInt(s, "y", y);
        setInt(s, "damage", damage);
        setInt(s, "pattern", 0);
        setInt(s, "life", 100);
        setInt(s, "knockback", 0);
        set(s, "fromPlayer", Boolean.TRUE);
        set(s, "critical", Boolean.FALSE);
        set(s, "hitMask", Long.valueOf(0L));
        set(s, "bossHit", Boolean.FALSE);
        set(s, "attached", Boolean.FALSE);
        set(s, "returning", Boolean.FALSE);
        set(s, "active", Boolean.TRUE);
        Array.set(getField(w, "shots"), slot, s);
    }

    private static void updateShots(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateShots", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    // ---- 5. Two separate shots must not both pay the same kill -------------------

    /**
     * A shot carries a hit mask, so one shot cannot pay twice. Two shots landing on the same enemy
     * in one frame is a legitimate double hit and must pay one kill, not two.
     */
    private static void testTwoShotsCannotBothPayTheSameKill() throws Exception {
        System.out.println("[5] two shots on one enemy pay one kill");
        Object w = newWorld();
        Object enemy = newEnemy(0, 100, 100, 15);
        Object[] pool = (Object[]) getField(w, "enemies");
        pool[0] = enemy;

        Object shots = getField(w, "shots");
        // Two player shots sitting right on the enemy, each dealing 10.
        for (int i = 0; i < 2; i++) {
            Object s = allocate(shotClass());
            setInt(s, "x", 100);
            setInt(s, "y", 100);
            setInt(s, "damage", 10);
            setInt(s, "pattern", 0);
            setInt(s, "life", 100);
            setInt(s, "knockback", 0);
            set(s, "fromPlayer", Boolean.TRUE);
            set(s, "critical", Boolean.FALSE);
            set(s, "hitMask", Long.valueOf(0L));
            set(s, "bossHit", Boolean.FALSE);
            set(s, "attached", Boolean.FALSE);
            set(s, "returning", Boolean.FALSE);
            set(s, "active", Boolean.TRUE);
            Array.set(shots, i, s);
        }
        setInt(w, "px", 100);
        setInt(w, "py", 100);

        updateShots(w, 300000L);
        // 15 HP against two 10-damage shots: the first leaves 5, the second kills it. One kill.
        expectEq(getIntOn(enemy, "hp"), -5, "both shots landed, so the second one killed it");
        expectEq(getInt(w, "kills"), 1, "the enemy paid exactly one kill");
    }

    // ---- helpers ----------------------------------------------------------------

    /**
     * A piercing shot is not consumed on contact, so it survives to the next frame and would
     * strike the same enemy again every frame without the per-shot hit mask. The mask is the only
     * thing standing between one piercing projectile and an enemy taking damage once per frame for
     * as long as the projectile lives.
     */
    private static void testPiercingShotHitsAnEnemyOnlyOnce() throws Exception {
        System.out.println("[6] a piercing shot hits an enemy only once");
        Object w = newWorld();
        Object enemy = newEnemy(0, 100, 100, 100);
        ((Object[]) getField(w, "enemies"))[0] = enemy;
        setInt(w, "px", 100);
        setInt(w, "py", 100);

        Object shot = allocate(shotClass());
        setInt(shot, "x", 100);
        setInt(shot, "y", 100);
        setInt(shot, "damage", 10);
        setInt(shot, "pattern", 6);          // PATTERN_PIERCING: survives on contact
        setInt(shot, "life", 100);
        setInt(shot, "knockback", 0);
        set(shot, "fromPlayer", Boolean.TRUE);
        set(shot, "critical", Boolean.FALSE);
        set(shot, "hitMask", Long.valueOf(0L));
        set(shot, "bossHit", Boolean.FALSE);
        set(shot, "attached", Boolean.FALSE);
        set(shot, "returning", Boolean.FALSE);
        set(shot, "active", Boolean.TRUE);
        Array.set(getField(w, "shots"), 0, shot);

        updateShots(w, 400000L);
        expectEq(getIntOn(enemy, "hp"), 90, "the piercing shot connected once");
        // Assert the premise the rest of this test rests on: a piercing projectile is not consumed
        // on contact, so it really is still there on the following frames.
        expect(isActive(shot), "the piercing shot survived contact and is still in flight");

        // Two more frames with the same projectile still alive and still overlapping.
        updateShots(w, 401000L);
        updateShots(w, 402000L);
        expectEq(getIntOn(enemy, "hp"), 90, "the enemy was not hit again on later frames");
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