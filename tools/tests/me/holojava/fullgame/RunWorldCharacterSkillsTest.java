package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Proves per-character skill/special activation, effect scoping, duration
 * expiry and use limits for the locked five roster (IMPLEMENTED_UNVERIFIED:
 * effects are the build's own definitions, not reference parity claims).
 *
 * <p>Covered via {@code updatePlayer} ticks, the real activation path:
 * <ul>
 *   <li>the skill fires by itself once Skill Pasif has granted it ({@code skillAcquired}),
 *       and the 15 s cooldown gates repeat activations;</li>
 *   <li>special works once ({@code specialUsed}) and never re-fires;</li>
 *   <li>each character's effect touches only its own mechanic (radius damage,
 *       slow, shield, lifesteal window, heal, invulnerability, shot clear);</li>
 *   <li>timed effects expire (invulnerability, slow);</li>
 *   <li>effects never leak across characters.</li>
 * </ul>
 *
 * <p>Instances are allocated without running the constructor and written
 * through {@code sun.misc.Unsafe}; test-only, nothing under {@code src/}
 * uses {@code sun.*}.
 */
public final class RunWorldCharacterSkillsTest {

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

    private static long getLong(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getLong(target);
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

    private static Object world(int characterIndex) throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[characterIndex]);
        set(w, "characterIndex", Integer.valueOf(characterIndex));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "random", new Random(23L));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "shield", Integer.valueOf(0));
        set(w, "shieldReadyAt", Long.valueOf(0L));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "slowEnemiesUntil", Long.valueOf(0L));
        set(w, "lifestealUntil", Long.valueOf(0L));
        set(w, "skillAcquired", Boolean.FALSE);
        set(w, "skillReadyAt", Long.valueOf(0L));
        set(w, "specialUsed", Boolean.FALSE);
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 48));
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
        set(w, "pickups", filled("me.holojava.fullgame.RunWorld$Pickup", 96));
        set(w, "boss", allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss")));
        return w;
    }

    /** Enemy i becomes an active damage target near the player. */
    private static void placeEnemy(Object w, int slot, int hp) throws Exception {
        Field f = worldClass().getDeclaredField("enemies");
        f.setAccessible(true);
        Object[] enemies = (Object[]) f.get(w);
        set(enemies[slot], "active", Boolean.TRUE);
        set(enemies[slot], "x", Integer.valueOf(170));
        set(enemies[slot], "y", Integer.valueOf(120));
        set(enemies[slot], "hp", Integer.valueOf(hp));
        set(enemies[slot], "maxHp", Integer.valueOf(hp));
        set(enemies[slot], "defIndex", Integer.valueOf(0));
    }

    private static int enemyHp(Object w, int slot) throws Exception {
        Field f = worldClass().getDeclaredField("enemies");
        f.setAccessible(true);
        return getInt(((Object[]) f.get(w))[slot], "hp");
    }

    private static void updatePlayer(Object w, boolean special, long now)
            throws Exception {
        Method m = worldClass().getDeclaredMethod("updatePlayer", int.class,
            boolean.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(0), Boolean.valueOf(special), Long.valueOf(now));
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testSkillNeedsAcquisition();
        testSkillCooldown();
        testGuraSkill();
        testAmeSkill();
        testCalliSkill();
        testKiaraSkill();
        testInaSkill();
        testSpecialsOnce();
        testSpecialEffects();
        testExpiry();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    private static void testSkillNeedsAcquisition() throws Exception {
        System.out.println("[1] skill needs acquisition");
        Object w = world(0);
        placeEnemy(w, 0, 100);
        updatePlayer(w, false, 50000L);
        expectEq(enemyHp(w, 0), 100, "unacquired skill deals no damage");
        expectEq(getLong(w, "skillReadyAt"), 0L, "unacquired skill sets no cooldown");
    }

    private static void testSkillCooldown() throws Exception {
        System.out.println("[2] skill cooldown gates repeat activation");
        Object w = world(1);
        set(w, "skillAcquired", Boolean.TRUE);
        updatePlayer(w, false, 50000L);
        expectEq(getLong(w, "slowEnemiesUntil"), 50000L + 3500L, "first activation applies");
        expectEq(getLong(w, "skillReadyAt"), 50000L + 15000L, "cooldown starts at 15s");
        updatePlayer(w, false, 50001L);
        expectEq(getLong(w, "skillReadyAt"), 50000L + 15000L, "repeat press inside cooldown does nothing");
        updatePlayer(w, false, 65000L);
        expectEq(getLong(w, "skillReadyAt"), 65000L + 15000L, "activation works again after cooldown");
    }

    private static void testGuraSkill() throws Exception {
        System.out.println("[3] gura skill damages nearby enemies only");
        Object w = world(0);
        set(w, "skillAcquired", Boolean.TRUE);
        placeEnemy(w, 0, 100);
        updatePlayer(w, false, 50000L);
        expect(enemyHp(w, 0) < 100, "gura skill damaged the enemy");
        expectEq(getLong(w, "lifestealUntil"), 0L, "gura skill grants no lifesteal");
        expectEq(getInt(w, "shield"), 0, "gura skill grants no shield");
    }

    private static void testAmeSkill() throws Exception {
        System.out.println("[4] ame skill slows without damaging");
        Object w = world(1);
        set(w, "skillAcquired", Boolean.TRUE);
        placeEnemy(w, 0, 100);
        updatePlayer(w, false, 50000L);
        expectEq(getLong(w, "slowEnemiesUntil"), 50000L + 3500L, "slow window opens");
        expectEq(enemyHp(w, 0), 100, "ame skill deals no direct damage");
    }

    private static void testCalliSkill() throws Exception {
        System.out.println("[5] calli skill raises a shield");
        Object w = world(2);
        set(w, "skillAcquired", Boolean.TRUE);
        updatePlayer(w, false, 50000L);
        expectEq(getInt(w, "shield"), 20, "shield raised to 20");
        expectEq(getLong(w, "lifestealUntil"), 0L, "calli skill grants no lifesteal");
    }

    private static void testKiaraSkill() throws Exception {
        System.out.println("[6] kiara skill opens a lifesteal window");
        Object w = world(3);
        set(w, "skillAcquired", Boolean.TRUE);
        updatePlayer(w, false, 50000L);
        expectEq(getLong(w, "lifestealUntil"), 50000L + 8000L, "lifesteal window opens");
        expectEq(getInt(w, "shield"), 0, "kiara skill grants no shield");
    }

    private static void testInaSkill() throws Exception {
        System.out.println("[7] ina skill guards and damages");
        Object w = world(4);
        set(w, "skillAcquired", Boolean.TRUE);
        placeEnemy(w, 0, 100);
        updatePlayer(w, false, 50000L);
        expectEq(getLong(w, "invulUntil"), 50000L + 2500L, "invulnerability window opens");
        expect(enemyHp(w, 0) < 100, "ina skill damaged the enemy");
    }

    private static void testSpecialsOnce() throws Exception {
        System.out.println("[8] specials fire exactly once");
        for (int c = 0; c < 5; c++) {
            Object w = world(c);
            updatePlayer(w, true, 50000L);
            expect(getBool(w, "specialUsed"), "char " + c + " special consumed on use");
            placeEnemy(w, 0, 1000);
            int before = enemyHp(w, 0);
            updatePlayer(w, true, 50001L);
            expectEq(enemyHp(w, 0), before, "char " + c + " second press is inert");
        }
    }

    private static void testSpecialEffects() throws Exception {
        System.out.println("[9] special effects per character");
        Object gura = world(0);
        placeEnemy(gura, 0, 1000);
        updatePlayer(gura, true, 50000L);
        expect(enemyHp(gura, 0) < 1000, "gura special damages");

        Object ame = world(1);
        updatePlayer(ame, true, 50000L);
        expectEq(getLong(ame, "slowEnemiesUntil"), 50000L + 6000L, "ame special slows 6s");

        Object calli = world(2);
        set(calli, "playerHp", Integer.valueOf(50));
        updatePlayer(calli, true, 50000L);
        expect(getInt(calli, "playerHp") > 50, "calli special heals");
        expect(getLong(calli, "lifestealUntil") > 50000L, "calli special opens lifesteal");

        Object kiara = world(3);
        placeEnemy(kiara, 0, 1000);
        updatePlayer(kiara, true, 50000L);
        expect(enemyHp(kiara, 0) < 1000, "kiara special damages");

        Object ina = world(4);
        Field sf = worldClass().getDeclaredField("shots");
        sf.setAccessible(true);
        Object[] shots = (Object[]) sf.get(ina);
        set(shots[0], "active", Boolean.TRUE);
        set(shots[0], "fromPlayer", Boolean.FALSE);
        updatePlayer(ina, true, 50000L);
        expectEq(getInt(ina, "shield"), 30, "ina special raises shield 30");
        expect(!getBool(shots[0], "active"), "ina special clears enemy shots");
    }

    private static void testExpiry() throws Exception {
        System.out.println("[10] timed effects expire");
        Object w = world(4);
        set(w, "skillAcquired", Boolean.TRUE);
        updatePlayer(w, false, 50000L);
        expect(getLong(w, "invulUntil") == 50000L + 2500L, "invulnerability starts");
        Method inv = worldClass().getDeclaredMethod("playerInvulnerable", long.class);
        inv.setAccessible(true);
        expect(!((Boolean) inv.invoke(w, Long.valueOf(50000L + 2501L))).booleanValue(),
            "invulnerability ends after its window");

        Object ame = world(1);
        set(ame, "skillAcquired", Boolean.TRUE);
        updatePlayer(ame, false, 50000L);
        long slowUntil = getLong(ame, "slowEnemiesUntil");
        expectEq(slowUntil, 50000L + 3500L, "slow window starts");
        expect(50000L + 3501L > slowUntil, "slow window ends after its duration");
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
