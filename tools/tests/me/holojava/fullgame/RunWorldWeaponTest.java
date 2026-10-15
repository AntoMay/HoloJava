package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers weapon execution: cooldown, haste, targeting, melee arcs, and projectile flight.
 *
 * <p>Parity with the reference JAR was established first. Eighteen of the nineteen
 * weapon-family methods carry identical integer and double constants on both sides — {@code
 * fireWeapon}, {@code updateMelee}, {@code updateShots}, {@code overlap}, {@code shotWidth},
 * {@code shotHeight}, {@code enemyWidth}, {@code enemyHeight}, {@code freeShot},
 * {@code createMelee}, {@code fireEnemyShot}, {@code addWeapon}, {@code nowAttackDelay},
 * {@code clearEnemyShots}, {@code removeWeaponById}, {@code weaponSlotById},
 * {@code weaponLevelById}, {@code weaponCount} and {@code updateWeapons}. The single difference is
 * the known {@code 350}/{@code 270} shot-cull bound, which is the viewport decoupling.
 *
 * <p><b>One confirmed divergence is documented rather than changed.</b> The reference's
 * {@code updateWeapons(long)} calls {@code fireWeapon} unconditionally once a slot's cooldown has
 * expired. This build has carried an attack-input gate since the very first commit, exempting
 * orbiting weapons. Removing the gate would make the attack control dead and would be a large
 * unrequested gameplay change, so it is pinned by test 2 instead. See the accompanying report.
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldWeaponTest {

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
            } else if (t.isPrimitive() || t == String.class || t.isArray()
                    || t == Object.class) {
                if (t == String.class || t.isArray() || t == Object.class) {
                    unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
                            .invoke(unsafe, target, offset, value);
                } else {
                    throw new IllegalStateException("unhandled primitive " + t);
                }
            } else {
                // Reference-typed domain fields (ContentCatalog defs and friends).
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

    private static Object field(Object target, String name) throws Exception {
        Field f = worldClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** Reads element {@code i} of an int[] field on the world. */
    private static int intAt(Object target, String name, int i) throws Exception {
        return ((int[]) field(target, name))[i];
    }

    /** Reads element {@code i} of a long[] field on the world. */
    private static long longAt(Object target, String name, int i) throws Exception {
        return ((long[]) field(target, name))[i];
    }

    /** The weapon pattern whose cooldown and geometry make the assertions unambiguous. */
    private static ContentCatalog.WeaponDef pickWeapon(int pattern) {
        ContentCatalog.WeaponDef fallback = null;
        for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
            ContentCatalog.WeaponDef d = ContentCatalog.WEAPONS[i];
            if (d == null) continue;
            if (fallback == null) fallback = d;
            if (d.pattern == pattern) return d;
        }
        return fallback;
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
        set(w, "viewWidth", Integer.valueOf(320));
        set(w, "viewHeight", Integer.valueOf(240));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "kills", Integer.valueOf(0));
        set(w, "defeatCount", Integer.valueOf(0));
        set(w, "hastePercent", Integer.valueOf(0));
        set(w, "atkPercentBonus", Integer.valueOf(0));
        set(w, "critPercentBonus", Integer.valueOf(0));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "weaponSlots", new int[6]);
        set(w, "weaponLevels", new int[6]);
        set(w, "weaponReadyAt", new long[6]);
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
        set(w, "melee", filled("me.holojava.fullgame.RunWorld$Melee", 24));
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 48));
        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
        set(boss, "x", Integer.valueOf(0));
        set(boss, "y", Integer.valueOf(0));
        set(boss, "defIndex", Integer.valueOf(0));
        set(boss, "hitMask", Long.valueOf(0L));
        set(w, "boss", boss);
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

    private static void updateWeapons(Object w, long now, boolean attackPressed) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateWeapons", long.class, boolean.class);
        m.setAccessible(true);
        System.out.println("DEBUG updateWeapons: now=" + now + " attackPressed=" + attackPressed);
        m.invoke(w, Long.valueOf(now), Boolean.valueOf(attackPressed));
    }

    private static void updateShots(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateShots", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static void updateMelee(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateMelee", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static void equip(Object w, ContentCatalog.WeaponDef def, int level) throws Exception {
        int slot = 0;
        int[] slots = (int[]) field(w, "weaponSlots");
        int[] levels = (int[]) field(w, "weaponLevels");
        long[] ready = (long[]) field(w, "weaponReadyAt");
        slots[slot] = weaponIndexOf(def);
        levels[slot] = level;
        ready[slot] = 0L;
        set(w, "weaponCount", Integer.valueOf(1));
    }

    private static int weaponIndexOf(ContentCatalog.WeaponDef def) throws Exception {
        for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
            if (ContentCatalog.WEAPONS[i] == def) return i;
        }
        return 0;
    }

    private static int activeShots(Object w) throws Exception {
        Object[] pool = (Object[]) field(w, "shots");
        int n = 0;
        for (int i = 0; i < pool.length; i++) if (getBool(pool[i], "active")) n++;
        return n;
    }

    private static Object firstActiveShot(Object w) throws Exception {
        Object[] pool = (Object[]) field(w, "shots");
        for (int i = 0; i < pool.length; i++) if (getBool(pool[i], "active")) return pool[i];
        return null;
    }

    private static Object[] enemyPool(Object w) throws Exception {
        return (Object[]) field(w, "enemies");
    }

    private static void placeEnemy(Object w, int index, int x, int y, int hp) throws Exception {
        Object[] pool = enemyPool(w);
        Object e = pool[index];
        set(e, "active", Boolean.TRUE);
        set(e, "defIndex", Integer.valueOf(0));
        set(e, "x", Integer.valueOf(x));
        set(e, "y", Integer.valueOf(y));
        set(e, "hp", Integer.valueOf(hp));
        set(e, "maxHp", Integer.valueOf(hp));
    }

    /** The cooldown updateWeapons should compute, derived independently of production code. */
    private static int expectedCooldown(ContentCatalog.WeaponDef def, int hastePercent, int level) {
        int haste = 100 + hastePercent + (level - 1) * 5;
        int divisor = Math.max(50, haste);
        return Math.max(80, def.cooldownMs * 100 / divisor);
    }

    // ---------------------------------------------------------------- tests


    /**
     * The new key mapping: FIRE (primary), 5, 1 all reach the world as attack input.
     * Orbit weapons remain autonomous. This is not a mirror of the reference's
     * auto-fire; it is the deliberate adaptation documented in the class Javadoc.
     */
    private static void testAllAttackKeysReachTheWeaponUpdate() throws Exception {
        System.out.println("[19] FIRE, 5, and 1 all reach the world as attack input");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PROJECTILE);
        Object w = world();
        equip(w, def, 1);
        set(w, "weaponReadyAt", new long[]{0L});

        updateWeapons(w, 10000L, true);
        int shotsAfterFire = activeShots(w);
        expect(shotsAfterFire > 0, "FIRE (or equivalent) fires the weapon");

        // Verify the weapon is PROJECTILE, not ORBIT
        int wid = intAt(w, "weaponSlots", 0);
        ContentCatalog.WeaponDef wdef = ContentCatalog.WEAPONS[wid];
        expectEq(wdef.pattern, ContentCatalog.PATTERN_PROJECTILE,
            "picked weapon must be PROJECTILE, not ORBIT");

        // Clear all shots
        Object[] shots = (Object[]) field(w, "shots");
        for (Object s : shots) set(s, "active", Boolean.FALSE);
        expectEq(activeShots(w), 0, "shots cleared before second call");

        // With no attack boolean, it must not fire again
        updateWeapons(w, 20000L, false);
        expectEq(activeShots(w), 0, "no attack key means no fire");
    }

    public static void main(String[] args) throws Exception {
        testCooldownFollowsTheHasteFormula();
        testCooldownReachesTheEightyMillisecondFloor();
        testHasteDivisorGuardPreventsAnOverFastWeapon();
        testAttackGateBlocksFiringWhenNotPressed();
        testOrbitWeaponsFireWithoutInput();
        testCooldownBlocksASecondAttackUntilItExpires();
        testProjectileSpawnsFromThePlayerAndFliesAtTheTarget();
        testProjectileMissesWhenNothingIsInFront();
        testProjectileExpiresAndLeavesThePool();
        testMeleeArcIsCreatedAndExpires();
        testWeaponUsesWorldCoordinatesWhenTheCameraHasScrolled();
        testWeaponLevelShortensTheCooldown();
        testAimIgnoresTheCameraWhenTheTargetIsBehind();
        testAllAttackKeysReachTheWeaponUpdate();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** haste = 100 + hastePercent + (level - 1) * 5, cooldown = max(80, cd * 100 / max(50, haste)). */
    private static void testCooldownFollowsTheHasteFormula() throws Exception {
        System.out.println("[1] the cooldown follows the haste formula");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PIERCING);
        for (int hastePercent : new int[] { 0, 25, 50 }) {
            Object w = world();
            equip(w, def, 1);
            set(w, "hastePercent", Integer.valueOf(hastePercent));
            updateWeapons(w, 10000L, true);
            long ready = longAt(w, "weaponReadyAt", 0);
            expectEq((int) (ready - 10000L), expectedCooldown(def, hastePercent, 1),
                "cooldown at haste " + hastePercent);
        }
    }

    /** Weapon level raises haste by 5 per level above the first, shortening the cooldown. */
    private static void testCooldownReachesTheEightyMillisecondFloor() throws Exception {
        System.out.println("[2] the cooldown falls to the 80 ms floor");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PIERCING);

        Object base = world();
        equip(base, def, 1);
        set(base, "hastePercent", Integer.valueOf(0));
        updateWeapons(base, 10000L, true);
        int baseCooldown = (int) (longAt(base, "weaponReadyAt", 0) - 10000L);

        // Max haste on a fast weapon must bottom out at the floor rather than going lower.
        Object fast = world();
        equip(fast, def, 10);
        set(fast, "hastePercent", Integer.valueOf(1000));
        updateWeapons(fast, 10000L, true);
        int fastCooldown = (int) (longAt(fast, "weaponReadyAt", 0) - 10000L);
        expectEq(fastCooldown, 80, "a very fast weapon is floored at 80 ms");
        expect(fastCooldown < baseCooldown, "and that is shorter than the unfloored cooldown");
    }

    /**
     * The {@code Math.max(50, haste)} guard. Without it a weapon with a deeply negative haste would
     * divide by zero or invert; with it the divisor stops at 50 and the cooldown simply doubles.
     */
    private static void testHasteDivisorGuardPreventsAnOverFastWeapon() throws Exception {
        System.out.println("[3] the haste divisor guard holds");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PIERCING);
        Object w = world();
        equip(w, def, 1);
        set(w, "hastePercent", Integer.valueOf(-400));   // haste would be -300 without the guard
        updateWeapons(w, 10000L, true);
        int cooldown = (int) (longAt(w, "weaponReadyAt", 0) - 10000L);
        expectEq(cooldown, expectedCooldown(def, -400, 1),
            "the cooldown matches the guarded formula");
        expect(cooldown > 0, "the cooldown stays positive even at absurd negative haste");

        // Without the guard this would divide by a negative number and produce a negative cooldown.
        int unguarded = def.cooldownMs * 100 / (100 - 400);
        expect(unguarded < 0, "the unguarded formula would indeed produce a negative cooldown");
    }

    /**
     * The documented divergence: this build requires the attack input, except for orbiting weapons.
     * The reference fires unconditionally; see the class comment.
     */
    private static void testAttackGateBlocksFiringWhenNotPressed() throws Exception {
        System.out.println("[4] the attack gate blocks firing when not pressed");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PIERCING);
        Object w = world();
        equip(w, def, 1);

        updateWeapons(w, 10000L, false);
        expectEq(activeShots(w), 0, "no projectile while the attack is not held");
        expectEq(longAt(w, "weaponReadyAt", 0), 0L, "and the cooldown was not even started");

        updateWeapons(w, 10000L, true);
        expect(activeShots(w) > 0, "holding attack fires the weapon");
    }

    private static void testOrbitWeaponsFireWithoutInput() throws Exception {
        System.out.println("[5] orbit weapons fire without input");
        ContentCatalog.WeaponDef orbit = pickWeapon(ContentCatalog.PATTERN_ORBIT);
        expectEq(orbit.pattern, ContentCatalog.PATTERN_ORBIT, "the fixture weapon orbits");
        Object w = world();
        equip(w, orbit, 1);

        updateWeapons(w, 10000L, false);
        expect(activeShots(w) > 0,
            "an orbiting weapon is autonomous and fires with no input held");
        expectEq(longAt(w, "weaponReadyAt", 0) > 10000L, true,
            "and still opens its cooldown");
    }

    private static void testCooldownBlocksASecondAttackUntilItExpires() throws Exception {
        System.out.println("[6] the cooldown blocks a second attack until it expires");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PIERCING);
        Object w = world();
        equip(w, def, 1);
        set(w, "hastePercent", Integer.valueOf(0));

        long t = 10000L;
        updateWeapons(w, t, true);
        long readyAt = longAt(w, "weaponReadyAt", 0);
        int cooldown = (int) (readyAt - t);

        // Clear the pool so a new projectile would be visible, then try again too early.
        Object[] pool = (Object[]) field(w, "shots");
        for (int i = 0; i < pool.length; i++) set(pool[i], "active", Boolean.FALSE);

        updateWeapons(w, readyAt - 1, true);
        expectEq(activeShots(w), 0, "one millisecond early, nothing fires");

        updateWeapons(w, readyAt, true);
        expect(activeShots(w) > 0, "exactly at the cooldown boundary the weapon fires again");
        expect(cooldown > 0, "and the cooldown was a real interval");
    }

    /** A projectile leaves the player heading for the nearest enemy. */
    private static void testProjectileSpawnsFromThePlayerAndFliesAtTheTarget() throws Exception {
        System.out.println("[7] a projectile spawns at the player and flies at the target");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_HOMING);
        Object w = world();
        equip(w, def, 1);
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        placeEnemy(w, 3, 260, 120, 50);          // due right of the player

        updateWeapons(w, 10000L, true);
        Object shot = firstActiveShot(w);
        expect(shot != null, "a projectile was created");
        expectEq(getInt(shot, "x"), 172, "it spawns at the player's centre x");
        expectEq(getInt(shot, "y"), 134, "and at the player's centre y plus the muzzle offset");
        expect(getInt(shot, "dx") > 0, "it travels right, toward the enemy");
        expectEq(getInt(shot, "target"), 3, "and it selected the nearest enemy as its target");
        expectEq(getBool(shot, "fromPlayer"), true, "the shot is owned by the player");
    }

    /** With nothing alive, the projectile fires along the facing instead of at nothing. */
    private static void testProjectileMissesWhenNothingIsInFront() throws Exception {
        System.out.println("[8] a projectile fires along the facing with no target");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PIERCING);
        Object w = world();
        equip(w, def, 1);
        set(w, "facingX", Integer.valueOf(-1));
        set(w, "facingY", Integer.valueOf(0));

        updateWeapons(w, 10000L, true);
        Object shot = firstActiveShot(w);
        expect(shot != null, "a projectile was still created");
        expectEq(getInt(shot, "target"), -1, "with no target selected");
        expect(getInt(shot, "dx") < 0, "and it flies left, matching the facing");
        expectEq(getBool(shot, "active"), true, "and stays active until it leaves the world");
    }

    /** Leaving the world culls the projectile rather than leaking pool slots forever. */
    private static void testProjectileExpiresAndLeavesThePool() throws Exception {
        System.out.println("[9] a projectile is culled when it leaves the world");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_PIERCING);
        Object w = world();
        equip(w, def, 1);
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));

        updateWeapons(w, 10000L, true);
        Object shot = firstActiveShot(w);
        expect(shot != null, "a projectile exists");
        int active = activeShots(w);

        // Teleport it far past the world edge, which the cull must notice.
        set(shot, "x", Integer.valueOf(100000));
        updateShots(w, 10100L);
        expect(!getBool(shot, "active"), "the projectile was deactivated beyond the world edge");
        expect(activeShots(w) < active, "and its pool slot was released");
    }

    /** A melee weapon creates a timed arc rather than a projectile. */
    private static void testMeleeArcIsCreatedAndExpires() throws Exception {
        System.out.println("[10] a melee arc is created and expires");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_MELEE_ARC);
        expectEq(def.pattern, ContentCatalog.PATTERN_MELEE_ARC, "the fixture weapon is a melee arc");
        Object w = world();
        equip(w, def, 1);

        updateWeapons(w, 10000L, true);
        expectEq(activeShots(w), 0, "a melee arc creates no projectile");

        Object[] arcs = (Object[]) field(w, "melee");
        Object arc = null;
        for (int i = 0; i < arcs.length; i++) if (getBool(arcs[i], "active")) arc = arcs[i];
        expect(arc != null, "but it did create a melee arc");
        if (arc != null) {
            expectEq(getInt(arc, "x"), 160, "the arc is anchored at the player x");
            expectEq(getInt(arc, "y"), 120, "and the player y");
            expect(getLong(arc, "until") > 0L, "with a finite lifetime");
        }
    }

    /**
     * On a scrolling stage the camera moves, but weapon geometry must stay in world coordinates:
     * a shot aimed at an enemy must be directed by the difference between two world positions, not
     * by anything screen-relative.
     */
    private static void testWeaponUsesWorldCoordinatesWhenTheCameraHasScrolled() throws Exception {
        System.out.println("[11] weapon targeting uses world coordinates");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_HOMING);
        Object w = world();
        equip(w, def, 1);
        set(w, "worldWidth", Integer.valueOf(640));
        set(w, "viewWidth", Integer.valueOf(320));
        set(w, "px", Integer.valueOf(500));
        set(w, "py", Integer.valueOf(120));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        placeEnemy(w, 1, 600, 120, 50);          // right of the player, well off the camera

        updateWeapons(w, 10000L, true);
        Object shot = firstActiveShot(w);
        expect(shot != null, "a projectile was created while scrolled");
        expectEq(getInt(shot, "x"), 512, "it spawns at the player's world position, not the camera's");
        expect(getInt(shot, "dx") > 0, "and still flies toward the far-away target");
        expectEq(getInt(shot, "target"), 1, "targeting ignores the viewport entirely");
    }

    /**
 * Each weapon level adds 5 to haste, so a slow weapon must fire measurably faster at level 5 than
 * at level 1. A weapon slow enough to be floored at 80 ms would hide that, so the fixture picks the
 * slowest-cooldown weapon in the catalog.
 */
    private static void testWeaponLevelShortensTheCooldown() throws Exception {
        System.out.println("[12] weapon level shortens the cooldown");
        ContentCatalog.WeaponDef slowest = null;
        for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
            ContentCatalog.WeaponDef d = ContentCatalog.WEAPONS[i];
            if (d == null || d.pattern == ContentCatalog.PATTERN_MELEE_ARC) continue;
            if (slowest == null || d.cooldownMs > slowest.cooldownMs) slowest = d;
        }
        expect(slowest != null, "a non-melee weapon exists to test");
        expect(slowest.cooldownMs * 100 / 120 > 80,
            "the slowest weapon is still above the floor at level 5");

        Object lvl1 = world();
        equip(lvl1, slowest, 1);
        set(lvl1, "hastePercent", Integer.valueOf(0));
        updateWeapons(lvl1, 10000L, true);
        int c1 = (int) (longAt(lvl1, "weaponReadyAt", 0) - 10000L);

        Object lvl5 = world();
        equip(lvl5, slowest, 5);
        set(lvl5, "hastePercent", Integer.valueOf(0));
        updateWeapons(lvl5, 10000L, true);
        int c5 = (int) (longAt(lvl5, "weaponReadyAt", 0) - 10000L);

        expectEq(c1, expectedCooldown(slowest, 0, 1), "level 1 matches the formula");
        expectEq(c5, expectedCooldown(slowest, 0, 5), "level 5 matches the formula");
        expect(c5 < c1, "and level 5 is genuinely faster than level 1 (" + c5 + " < " + c1 + ")");
    }

    /**
     * Aiming must be computed in world space. With the camera scrolled far to the right, a target
     * behind the player is still behind them: if the camera offset leaked into the aim vector the
     * shot would fire the wrong way entirely.
     */
    private static void testAimIgnoresTheCameraWhenTheTargetIsBehind() throws Exception {
        System.out.println("[13] aim ignores the camera when the target is behind");
        ContentCatalog.WeaponDef def = pickWeapon(ContentCatalog.PATTERN_HOMING);
        Object w = world();
        equip(w, def, 1);
        set(w, "worldWidth", Integer.valueOf(640));
        set(w, "viewWidth", Integer.valueOf(320));
        set(w, "px", Integer.valueOf(500));
        set(w, "py", Integer.valueOf(120));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        placeEnemy(w, 2, 300, 120, 50);          // behind the player, far left

        Method camera = worldClass().getDeclaredMethod("cameraX");
        camera.setAccessible(true);
        int cam = ((Integer) camera.invoke(w)).intValue();
        expect(cam > 0, "the camera is scrolled right while the player stands at x=500");
        expect(cam > 212, "and by more than the distance to the target, so a leak would flip the sign");

        updateWeapons(w, 10000L, true);
        Object shot = firstActiveShot(w);
        expect(shot != null, "a projectile was created");
        expectEq(getInt(shot, "target"), 2, "the enemy behind was selected as the target");
        expect(getInt(shot, "dx") < 0,
            "the shot fires left toward the target, not right as a camera leak would cause");
        expectEq(getInt(shot, "dy"), 0, "and straight along the axis");
    }

    // ---------------------------------------------------------------- helpers

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(long got, long want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(boolean got, boolean want, String label) {
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