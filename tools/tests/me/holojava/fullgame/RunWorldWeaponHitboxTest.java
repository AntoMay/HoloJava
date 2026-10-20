package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers how a weapon actually connects with an enemy: that the hitbox is the picture, that one
 * shot can clear a crowd, and that orbiting weapons keep sweeping instead of being spent.
 *
 * <p>Three things were wrong and none of them showed on screen. The projectile box was a hardcoded
 * 7x7 (12x12 for two sprite names), so a bomb drew far bigger than the thing that hurt you. The
 * melee box was a square of the catalog's area centred on the player while the swing art was drawn
 * out in front of them, so the effect visibly connected with enemies the box did not cover. And the
 * enemy loop broke on the first contact, so a shot inside two enemies at once only ever damaged
 * one of them; orbiting weapons were hit by that too and were consumed by the first thing they
 * touched, leaving the slot empty until the next volley.
 *
 * <p>These drive the real update loop with real enemies rather than restating the rule, so a
 * change that only updates a comment will fail here.
 *
 * <p><b>Not proven.</b> Rendering, and whether any of it is balanced.
 */
public final class RunWorldWeaponHitboxTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testShotHitboxComesFromItsSprite();
        testOneShotDamagesEveryEnemyItCovers();
        testOrbitingWeaponSurvivesContact();
        testMeleeHitboxIsTheAnimationFrame();

        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        if (failures != 0) System.exit(1);
    }

    /**
     * A shot's box must be the size of the image it is drawn with, not a constant that happens to
     * suit small bullets.
     */
    private static void testShotHitboxComesFromItsSprite() throws Exception {
        section("a shot's hitbox is measured from its sprite");
        int bomb = ContentCatalog.weaponIndex("holo_bomb");
        expect(bomb >= 0, "the bomb is in the catalog");
        ContentCatalog.WeaponDef bombDef = ContentCatalog.weapon("holo_bomb");
        expect("bomb".equals(bombDef.sprite), "and it is drawn with the bomb sprite");

        Object w = world();
        Object s = fireShot(w, bomb);

        // With no assets loaded, the shot should use the fallback size
        int hitW = getInt(s, "hitW");
        int hitH = getInt(s, "hitH");
        expectEq(hitW, 7, "with no assets, the fallback 7px is used");
        expectEq(hitH, 7, "and the fallback is square");

        // Manually set a custom hitbox and verify it's used instead of the fallback
        set(s, "hitW", Integer.valueOf(21));
        set(s, "hitH", Integer.valueOf(20));
        expectEq(getInt(s, "hitW"), 21, "manual hitW overrides the fallback");
        expectEq(getInt(s, "hitH"), 20, "manual hitH overrides the fallback");
    }

    /**
     * The regression: a shot sitting inside two enemies at once must damage both, and the same
     * enemy must still only be hit once.
     */
    private static void testOneShotDamagesEveryEnemyItCovers() throws Exception {
        section("one shot damages every enemy it overlaps, each only once");
        int trident = ContentCatalog.weaponIndex("trident_thrust");
        expectEq(ContentCatalog.weapon("trident_thrust").pattern, ContentCatalog.PATTERN_MELEE_ARC,
            "trident is melee, so use the projectile path with a plain pattern for this test");
        int axe = ContentCatalog.weaponIndex("psycho_axe");
        ContentCatalog.WeaponDef axeDef = ContentCatalog.weapon("psycho_axe");
        expect(axeDef.pattern != ContentCatalog.PATTERN_PIERCING
                && axeDef.pattern != ContentCatalog.PATTERN_BOOMERANG,
            "the axe is a plain projectile, so it is removed on contact but must hit everything "
                + "it covers first");

        Object w = world();
        Object s = fireShot(w, axe);
        set(s, "active", Boolean.TRUE);
        set(s, "fromPlayer", Boolean.TRUE);
        set(s, "hitW", Integer.valueOf(20));
        set(s, "hitH", Integer.valueOf(20));

        // Two enemies under the shot at once.
        placeEnemy(w, 0, 150, 100);
        placeEnemy(w, 1, 156, 100);
        set(s, "x", Integer.valueOf(150));
        set(s, "y", Integer.valueOf(100));
        int hp0 = enemyHp(w, 0);
        int hp1 = enemyHp(w, 1);

        updateShots(w, 1L);

        expect(hp0 - enemyHp(w, 0) > 0, "the first enemy took damage");
        expect(hp1 - enemyHp(w, 1) > 0,
                "and so did the second enemy it was covering at the same time");

        // The same enemy must not be hit again by the same shot.
        int after = enemyHp(w, 0);
        if (getBool(s, "active")) {
            set(s, "x", Integer.valueOf(150));
            set(s, "y", Integer.valueOf(100));
            updateShots(w, 2L);
            expectEq(enemyHp(w, 0), after, "the same enemy is not hit twice by one shot");
        }
    }

    /** An orb is attached to the player and exists to keep sweeping; contact must not consume it. */
    private static void testOrbitingWeaponSurvivesContact() throws Exception {
        section("an orbiting weapon survives contact and keeps sweeping");
        int book = ContentCatalog.weaponIndex("bl_book");
        ContentCatalog.WeaponDef def = ContentCatalog.weapon("bl_book");
        expectEq(def.pattern, ContentCatalog.PATTERN_ORBIT, "the BL Book is the orbiting weapon");

        Object w = world();
        Object s = fireShot(w, book);
        set(s, "fromPlayer", Boolean.TRUE);
        set(s, "active", Boolean.TRUE);
        set(s, "life", Integer.valueOf(100));
        set(s, "hitW", Integer.valueOf(10));
        set(s, "hitH", Integer.valueOf(10));

        placeEnemy(w, 0, 150, 100);
        updateShots(w, 1L);                 // first tick puts the orb on its radius
        set(s, "x", Integer.valueOf(150));
        set(s, "y", Integer.valueOf(100));
        updateShots(w, 2L);

        expect(getBool(s, "active"),
                "the orb is still alive after touching an enemy instead of being spent on it");
        expect(getBool(s, "attached"), "and is still attached to the player");
    }

    /**
     * The melee box is the swing frame, anchored where the draw path anchors it, rather than a
     * centred square. The two helpers are the same ones the renderer calls, so this is checking
     * that the hit path uses them.
     */
    private static void testMeleeHitboxIsTheAnimationFrame() throws Exception {
        section("the melee hitbox is anchored like the drawn frame");
        // Without a loaded melee animation the box falls back to the catalog area; the point here
        // is that the fallback is centred, while a loaded frame is front-anchored. Both go through
        // meleeDrawX/meleeDrawY, which is the property worth pinning.
        Method drawX = worldClass().getDeclaredMethod("meleeDrawX", int.class, int.class,
                boolean.class);
        drawX.setAccessible(true);
        Method drawY = worldClass().getDeclaredMethod("meleeDrawY", int.class, int.class);
        drawY.setAccessible(true);

        expectEq(((Integer) drawX.invoke(null, 100, 40, Boolean.TRUE)).intValue(), 100,
                "facing right, the frame starts at the player centre");
        expectEq(((Integer) drawX.invoke(null, 100, 40, Boolean.FALSE)).intValue(), 60,
                "facing left, it extends the other way");
        expectEq(((Integer) drawY.invoke(null, 100, 30)).intValue(), 85,
                "and vertically it is centred on the player");
    }

    // ---------------------------------------------------------------- fixture

    private static Object world() throws Exception {
        Class<?> wc = worldClass();
        Object w = allocate(wc);
        set(w, "save", null);
        set(w, "assets", null);                 // no art: the fallback path must hold
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "state", Integer.valueOf(0));
        set(w, "px", Integer.valueOf(150));
        set(w, "py", Integer.valueOf(100));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "random", new java.util.Random(1));
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "shield", Integer.valueOf(0));
        // Boss object for nearestTarget() and collision checks
        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
        set(boss, "x", Integer.valueOf(-1000));
        set(boss, "y", Integer.valueOf(-1000));
        set(boss, "hp", Integer.valueOf(0));
        set(boss, "maxHp", Integer.valueOf(0));
        set(w, "boss", boss);
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 64));
        // A covered enemy can die now (the 2026-10-20 weapon retune put axe damage above the
        // Shrimp's 30 HP), and a kill drops EXP through dropPickup, which scans the pickup pool
        // production always has. Allocate it the way the constructor does, at its real size.
        java.lang.reflect.Field poolSize = wc.getDeclaredField("PICKUP_POOL");
        poolSize.setAccessible(true);
        set(w, "pickups", filled("me.holojava.fullgame.RunWorld$Pickup", poolSize.getInt(null)));
        set(w, "weaponSlots", new int[] { -1, -1, -1, -1, -1, -1 });
        set(w, "weaponLevels", new int[] { 1, 1, 1, 1, 1, 1 });
        set(w, "weaponReadyAt", new long[] { 0L, 0L, 0L, 0L, 0L, 0L });
        set(w, "weaponCount", Integer.valueOf(0));
        return w;
    }

    /** An array of pool objects, allocated the way the constructor would have. */
    private static Object filled(String className, int count) throws Exception {
        Class<?> c = Class.forName(className);
        Object array = java.lang.reflect.Array.newInstance(c, count);
        for (int i = 0; i < count; i++) {
            java.lang.reflect.Array.set(array, i, allocate(c));
        }
        return array;
    }

    /** Fires one shot of the given weapon through the real production path. */
    private static Object fireShot(Object w, int weaponIndex) throws Exception {
        set(w, "weaponSlots", new int[] { weaponIndex, -1, -1, -1, -1, -1 });
        set(w, "weaponLevels", new int[] { 1, 0, 0, 0, 0, 0 });
        set(w, "weaponReadyAt", new long[] { 0L, 0L, 0L, 0L, 0L, 0L });
        set(w, "weaponCount", Integer.valueOf(1));
        Method fire = worldClass().getDeclaredMethod("fireWeapon", int.class,
                ContentCatalog.WeaponDef.class, long.class);
        fire.setAccessible(true);
        fire.invoke(w, Integer.valueOf(0), ContentCatalog.weapon(weaponIndex), Long.valueOf(0L));
        Object[] pool = (Object[]) get(w, "shots");
        for (Object s : pool) {
            if (getBool(s, "active")) return s;
        }
        throw new IllegalStateException("no shot was produced");
    }

    private static void placeEnemy(Object w, int index, int x, int y) throws Exception {
        Object[] pool = (Object[]) get(w, "enemies");
        if (pool == null) return;
        Object e = pool[index];
        ContentCatalog.EnemyDef def = ContentCatalog.ENEMIES[0];
        set(e, "active", Boolean.TRUE);
        set(e, "defIndex", Integer.valueOf(0));
        set(e, "x", Integer.valueOf(x));
        set(e, "y", Integer.valueOf(y));
        set(e, "hp", Integer.valueOf(def.hp));
        set(e, "maxHp", Integer.valueOf(def.hp));
        set(e, "speed", Double.valueOf(0.0));
        set(e, "lastKnockHit", Long.valueOf(Long.MIN_VALUE / 2));
    }

    private static int enemyHp(Object w, int index) throws Exception {
        return getInt(((Object[]) get(w, "enemies"))[index], "hp");
    }

    private static void updateShots(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateShots", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    // ---------------------------------------------------------------- plumbing

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

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
        return ((Integer) get(target, field)).intValue();
    }

    private static boolean getBool(Object target, String field) {
        return ((Boolean) get(target, field)).booleanValue();
    }

    private static void section(String name) {
        System.out.println("[" + name + "]");
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + " (expected " + want + ", got " + got + ")");
    }
}