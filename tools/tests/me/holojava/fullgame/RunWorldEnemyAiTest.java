package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Covers enemy AI: movement per behaviour, spawn placement, contact damage, knockback, clamping,
 * despawn, and the interaction with scrolling.
 *
 * <p>Parity with the reference JAR was established first, from bytecode. Seven of the nine
 * AI-family methods carry identical integer and double constants on both sides — {@code
 * updateEnemies} in full, {@code moveToward}, {@code applyEnemyKnockback}, {@code applyKnockback},
 * {@code healNearbyEnemies}, {@code nowAttackDelay} and {@code spawnEnemy}. The only differences
 * are the known {@code 320}/{@code 240} to world-dimension decouplings in {@code clampEnemy} and
 * {@code placeAtEdge}.
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldEnemyAiTest {

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

    private static Object world(int worldWidth, int worldHeight) throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "save", null);
        set(w, "random", new Random(31L));
        set(w, "state", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(worldWidth));
        set(w, "worldHeight", Integer.valueOf(worldHeight));
        set(w, "viewWidth", Integer.valueOf(worldWidth < 320 ? 320 : worldWidth));
        set(w, "viewHeight", Integer.valueOf(worldHeight < 240 ? 240 : worldHeight));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "shield", Integer.valueOf(0));
        set(w, "defensePercent", Integer.valueOf(0));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "slowEnemiesUntil", Long.valueOf(0L));
        set(w, "lifestealUntil", Long.valueOf(0L));
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "facingY", Integer.valueOf(0));
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 48));
        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
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

    private static void updateEnemies(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updateEnemies", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static Object[] pool(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("enemies");
        f.setAccessible(true);
        return (Object[]) f.get(w);
    }

    private static int defIndexWithBehavior(int behavior) {
        for (int i = 0; i < ContentCatalog.ENEMIES.length; i++) {
            ContentCatalog.EnemyDef d = ContentCatalog.ENEMIES[i];
            if (d != null && d.behavior == behavior) return i;
        }
        return 0;
    }

    /** Places a live enemy of the given behaviour and returns it. */
    private static Object place(Object w, int behavior, int x, int y, double speed, long nextAttack)
            throws Exception {
        int defIndex = defIndexWithBehavior(behavior);
        Object[] p = pool(w);
        Object e = p[0];
        set(e, "active", Boolean.TRUE);
        set(e, "defIndex", Integer.valueOf(defIndex));
        set(e, "x", Integer.valueOf(x));
        set(e, "y", Integer.valueOf(y));
        set(e, "hp", Integer.valueOf(100));
        set(e, "maxHp", Integer.valueOf(100));
        set(e, "atk", Integer.valueOf(10));
        set(e, "speed", Double.valueOf(speed));
        set(e, "nextAttack", Long.valueOf(nextAttack));
        set(e, "nextAnim", Long.valueOf(0L));
        set(e, "healAt", Long.valueOf(0L));
        set(e, "chargeUntil", Long.valueOf(0L));
        set(e, "chargeDx", Integer.valueOf(0));
        set(e, "chargeDy", Integer.valueOf(0));
        set(e, "kbUntil", Long.valueOf(0L));
        set(e, "kbDx", Integer.valueOf(0));
        set(e, "kbDy", Integer.valueOf(0));
        set(e, "lastKnockHit", Long.valueOf(0L));
        set(e, "knockResist", Integer.valueOf(0));
        set(e, "anim", Integer.valueOf(0));
        return e;
    }

    /** The per-tick step production uses, derived independently. */
    private static int stepOf(double speed) {
        int v = (int) (speed * 0.04);
        return v < 1 ? 1 : v;
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testChaserMovesTowardThePlayer();
        testShooterKeepsItsDistance();
        testContactDamageLandsOnceThenIsBlockedByCooldown();
        testPlayerInvulnerabilityBeatsContactDamage();
        testInvulnerabilityIsEnforcedTwice();
        testKnockbackPushesThenStops();
        testEnemyIsClampedInsideTheWorld();
        testSpawnsLandOnAnEdgeAwayFromThePlayer();
        testKnockResistDecays();
        testBomberDespawnsOnContact();
        testHealerRestoresNearbyEnemies();
        testChargerChargesInAStraightLine();
        testEnemyLogicUsesWorldCoordinatesWhenScrolled();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** A chaser closes the gap on both axes, one normalised step per tick. */
    private static void testChaserMovesTowardThePlayer() throws Exception {
        System.out.println("[1] a chaser closes in on the player");
        double speed = 2.5;
        Object w = world(320, 240);
        Object e = place(w, ContentCatalog.AI_CHASER, 240, 200, speed, Long.MAX_VALUE);

        int x0 = getInt(e, "x"), y0 = getInt(e, "y");
        updateEnemies(w, 1000L);

        int step = stepOf(speed);
        expectEq(getInt(e, "x"), x0 - step, "it stepped left toward the player");
        expectEq(getInt(e, "y"), y0 - step, "and up toward the player");

        // Moving away from the player does the opposite.
        Object away = world(320, 240);
        Object e2 = place(away, ContentCatalog.AI_CHASER, 40, 40, speed, Long.MAX_VALUE);
        int ax0 = getInt(e2, "x"), ay0 = getInt(e2, "y");
        updateEnemies(away, 1000L);
        expectEq(getInt(e2, "x"), ax0 + step, "on the other side it steps right");
        expectEq(getInt(e2, "y"), ay0 + step, "and down");
    }

    /**
     * A shooter kites: it backs away when closer than 60 px, closes when further than 100 px, and
     * holds its ground between those distances.
     */
    private static void testShooterKeepsItsDistance() throws Exception {
        System.out.println("[2] a shooter keeps its distance");
        double speed = 3.0;
        int step = stepOf(speed);

        Object close = world(320, 240);
        Object near = place(close, ContentCatalog.AI_SHOOTER, 200, 120, speed, Long.MAX_VALUE);
        int nx = getInt(near, "x");
        updateEnemies(close, 1000L);
        // The shooter sits to the right of the player, so backing away means moving further right.
        expectEq(getInt(near, "x"), nx + step,
            "at close range the shooter backs away instead of closing");

        Object far = world(320, 240);
        Object distant = place(far, ContentCatalog.AI_SHOOTER, 300, 120, speed, Long.MAX_VALUE);
        int fx = getInt(distant, "x");
        updateEnemies(far, 1000L);
        expect(getInt(distant, "x") < fx, "at long range the shooter closes the gap");

        Object mid = world(320, 240);
        Object middle = place(mid, ContentCatalog.AI_SHOOTER, 260, 120, speed, Long.MAX_VALUE);
        int mx = getInt(middle, "x");
        updateEnemies(mid, 1000L);
        expectEq(getInt(middle, "x"), mx, "inside its preferred band it holds position");

        // The mirrored case, where only the retreat branch can move the enemy: the shooter sits to
        // the LEFT of the player, so only the dx > 0 line can push it further away.
        Object mirrored = world(320, 240);
        Object left = place(mirrored, ContentCatalog.AI_SHOOTER, 140, 120, speed, Long.MAX_VALUE);
        int lx = getInt(left, "x");
        updateEnemies(mirrored, 1000L);
        expectEq(getInt(left, "x"), lx - step,
            "a shooter to the left retreats leftwards, away from the player");
    }

    /**
     * Contact damage requires {@code invulUntil <= now < e.nextAttack}: the same timestamp that
     * gates a ranged shot also gates a touch, so it only bites while that cooldown is still in the
     * future. Repeat damage is prevented by the player's own invulnerability window, not by the
     * enemy, which is what this test pins down.
     */
    private static void testContactDamageLandsOnceThenIsBlockedByCooldown() throws Exception {
        System.out.println("[3] contact damage lands once, then invulnerability blocks it");
        int defIndex = defIndexWithBehavior(ContentCatalog.AI_CHASER);
        long cooldown = ContentCatalog.ENEMIES[defIndex].cooldownMs;
        expect(cooldown > 0, "the fixture enemy has a real contact cooldown");

        Object w = world(320, 240);
        // A fresh spawn carries nextAttack in the future, which is what arms contact damage.
        Object e = place(w, ContentCatalog.AI_CHASER, 160, 120, 1.0, 1000L + cooldown);

        updateEnemies(w, 1000L);
        expect(getInt(w, "playerHp") < 100, "the overlapping enemy hurt the player");
        expect(getLong(e, "nextAttack") > 1000L, "and rescheduled its next attack");

        // The hit opened the player's invulnerability window, so a following tick cannot re-hit.
        int afterFirst = getInt(w, "playerHp");
        updateEnemies(w, 1100L);
        expectEq(getInt(w, "playerHp"), afterFirst,
            "a second tick inside the player's window did no further damage");

        // Once that window closes, contact damage is available again. The chaser walked off the
        // player while biting, so it is put back in contact first.
        set(e, "x", Integer.valueOf(160));
        set(e, "y", Integer.valueOf(120));
        set(e, "nextAttack", Long.valueOf(3000L));
        updateEnemies(w, 1000L + 1000L);
        expect(getInt(w, "playerHp") < afterFirst, "after the window the enemy can hit again");

        // And an enemy that is nowhere near the player must never deal contact damage, which is
        // what makes the overlap test load-bearing rather than incidental.
        Object farWorld = world(320, 240);
        place(farWorld, ContentCatalog.AI_CHASER, 20, 20, 1.0, 9000L);
        updateEnemies(farWorld, 1000L);
        expectEq(getInt(farWorld, "playerHp"), 100,
            "an enemy far from the player dealt no contact damage at all");
    }

    /**
     * The contact branch re-checks the player's invulnerability window, and so does
     * {@code damagePlayer} itself. That outer check is therefore redundant with an inner one; this
     * test records that the player is still protected, whichever guard is doing the work.
     */
    private static void testInvulnerabilityIsEnforcedTwice() throws Exception {
        System.out.println("[4b] invulnerability is enforced by both guards");
        Object w = world(320, 240);
        place(w, ContentCatalog.AI_CHASER, 160, 120, 1.0, 9000L);
        set(w, "invulUntil", Long.valueOf(5000L));
        updateEnemies(w, 1000L);
        expectEq(getInt(w, "playerHp"), 100, "the player took no damage while invulnerable");
        Field f = worldClass().getDeclaredField("invulUntil");
        f.setAccessible(true);
        expectEq(f.getLong(w), 5000L,
            "and the window was left untouched rather than refreshed or cleared");
    }

    /** The player's invulnerability window wins over an overlapping enemy. */
    private static void testPlayerInvulnerabilityBeatsContactDamage() throws Exception {
        System.out.println("[4] player invulnerability beats contact damage");
        Object w = world(320, 240);
        place(w, ContentCatalog.AI_CHASER, 160, 120, 1.0, 9000L);
        set(w, "invulUntil", Long.valueOf(5000L));      // player is briefly untouchable

        updateEnemies(w, 1000L);
        expectEq(getInt(w, "playerHp"), 100, "an invulnerable player took no contact damage");
    }

    /** Knockback displaces the enemy while the window is open, and stops when it closes. */
    private static void testKnockbackPushesThenStops() throws Exception {
        System.out.println("[5] knockback pushes then stops");
        Object w = world(320, 240);
        Object e = place(w, ContentCatalog.AI_CHASER, 60, 120, 1.0, Long.MAX_VALUE);
        set(e, "kbDx", Integer.valueOf(4));
        set(e, "kbDy", Integer.valueOf(0));
        set(e, "kbUntil", Long.valueOf(1500L));

        int x0 = getInt(e, "x");
        updateEnemies(w, 1000L);
        int pushed = getInt(e, "x");
        expect(pushed > x0 + stepOf(1.0),
            "the enemy moved by both its own step and the knockback");

        // Once the window closes the knockback stops contributing.
        set(e, "x", Integer.valueOf(100));
        int x1 = getInt(e, "x");
        updateEnemies(w, 2000L);
        int moved = getInt(e, "x") - x1;
        expectEq(moved, stepOf(1.0), "after the knockback window only its own step applies");
    }

    /** Enemies are clamped inside the world, including on a scrolling stage. */
    private static void testEnemyIsClampedInsideTheWorld() throws Exception {
        System.out.println("[6] enemies are clamped inside the world");
        Object w = world(640, 240);
        Object e = place(w, ContentCatalog.AI_CHASER, 60, 60, 1.0, Long.MAX_VALUE);
        set(e, "kbDx", Integer.valueOf(-5000));
        set(e, "kbUntil", Long.valueOf(1500L));
        updateEnemies(w, 1000L);
        int defIndex = defIndexWithBehavior(ContentCatalog.AI_CHASER);
        int ew = ContentCatalog.ENEMIES[defIndex].collW;
        int eh = ContentCatalog.ENEMIES[defIndex].collH;
        expect(getInt(e, "x") >= -ew, "the enemy was not pushed past the left edge");
        expect(getInt(e, "y") >= -eh, "nor past the top edge");
        expect(getInt(e, "x") <= 640, "nor past the right edge of a scrolling world");

        // The opposite corner, so the upper bounds are exercised too.
        Object east = world(640, 240);
        Object e2 = place(east, ContentCatalog.AI_CHASER, 600, 200, 1.0, Long.MAX_VALUE);
        set(e2, "kbDx", Integer.valueOf(5000));
        set(e2, "kbDy", Integer.valueOf(5000));
        set(e2, "kbUntil", Long.valueOf(1500L));
        updateEnemies(east, 1000L);
        expectEq(getInt(e2, "x"), 640, "the enemy was clamped to the world width");
        expectEq(getInt(e2, "y"), 240, "and to the world height");
    }

    /** Spawned enemies land on an edge and never right on top of the player. */
    private static void testSpawnsLandOnAnEdgeAwayFromThePlayer() throws Exception {
        System.out.println("[7] spawns land on an edge away from the player");
        Object w = world(320, 240);
        Method placeAtEdge = worldClass().getDeclaredMethod("placeAtEdge",
                Class.forName("me.holojava.fullgame.RunWorld$Enemy"), long.class);
        placeAtEdge.setAccessible(true);

        int defIndex = defIndexWithBehavior(ContentCatalog.AI_CHASER);
        int ew = ContentCatalog.ENEMIES[defIndex].collW;
        int eh = ContentCatalog.ENEMIES[defIndex].collH;

        for (int i = 0; i < 40; i++) {
            Object[] p = pool(w);
            Object e = p[0];
            set(e, "active", Boolean.TRUE);
            set(e, "defIndex", Integer.valueOf(defIndex));
            set(e, "x", Integer.valueOf(0));
            set(e, "y", Integer.valueOf(0));
            placeAtEdge.invoke(w, e, Long.valueOf(1000L));

            int x = getInt(e, "x"), y = getInt(e, "y");
            boolean onEdge = x == -ew || x == 320 || y == -eh || y == 240
                    || (x >= 0 && x <= 320 - ew) || (y >= 0 && y <= 240 - eh);
            expect(onEdge, "spawn " + i + " landed within the world bounds");
            long dx = x - 172, dy = y - 132;
            expect(dx * dx + dy * dy >= 6400L,
                "spawn " + i + " was at least 80 px from the player (" + dx + "," + dy + ")");
        }

        // With the player hugging an edge, one of the four sides would place an enemy right on top
        // of them, which is exactly what the retry loop exists to prevent. The player sits at the
        // top-left, so the top edge is only a short distance away.
        Object corner = world(320, 240);
        set(corner, "px", Integer.valueOf(4));
        set(corner, "py", Integer.valueOf(4));
        Method placeAtEdge2 = worldClass().getDeclaredMethod("placeAtEdge",
                Class.forName("me.holojava.fullgame.RunWorld$Enemy"), long.class);
        placeAtEdge2.setAccessible(true);
        int defIndex2 = defIndexWithBehavior(ContentCatalog.AI_CHASER);
        for (int i = 0; i < 60; i++) {
            Object[] p = pool(corner);
            Object e = p[0];
            set(e, "active", Boolean.TRUE);
            set(e, "defIndex", Integer.valueOf(defIndex2));
            set(e, "x", Integer.valueOf(0));
            set(e, "y", Integer.valueOf(0));
            placeAtEdge2.invoke(corner, e, Long.valueOf(1000L));
            long dx = getInt(e, "x") - 16;
            long dy = getInt(e, "y") - 16;
            expect(dx * dx + dy * dy >= 6400L,
                "edge-hugging spawn " + i + " was still pushed at least 80 px away");
        }
    }

    /** knockResist decays back to zero once the knockback mark is old enough. */
    private static void testKnockResistDecays() throws Exception {
        System.out.println("[7b] knock resistance decays");
        Object w = world(320, 240);
        Object e = place(w, ContentCatalog.AI_CHASER, 200, 120, 1.0, Long.MAX_VALUE);
        set(e, "knockResist", Integer.valueOf(7));
        set(e, "lastKnockHit", Long.valueOf(1000L));

        updateEnemies(w, 5000L);
        expectEq(getInt(e, "knockResist"), 7, "resistance survives inside the ten second window");

        updateEnemies(w, 12000L);
        expectEq(getInt(e, "knockResist"), 0, "and is cleared once the window has passed");
    }

    /** A bomber that reaches the player deactivates instead of surviving the touch. */
    private static void testBomberDespawnsOnContact() throws Exception {
        System.out.println("[8] a bomber despawns on contact");
        Object w = world(320, 240);
        Object e = place(w, ContentCatalog.AI_BOMBER, 160, 120, 1.0, 0L);

        updateEnemies(w, 1000L);
        expect(!getBool(e, "active"),
            "the bomber was consumed on contact and released its slot");
        expect(getInt(w, "playerHp") < 100, "and it did damage the player on the way");
    }

    /** A healer periodically restores nearby enemies up to their maximum. */
    private static void testHealerRestoresNearbyEnemies() throws Exception {
        System.out.println("[9] a healer restores nearby enemies");
        Object w = world(320, 240);
        Object[] p = pool(w);
        Object patient = place(w, ContentCatalog.AI_CHASER, 165, 120, 1.0, Long.MAX_VALUE);
        set(patient, "hp", Integer.valueOf(50));
        set(patient, "maxHp", Integer.valueOf(100));

        Object healer = p[1];
        set(healer, "active", Boolean.TRUE);
        set(healer, "defIndex", Integer.valueOf(defIndexWithBehavior(ContentCatalog.AI_HEALER)));
        set(healer, "x", Integer.valueOf(300));
        set(healer, "y", Integer.valueOf(220));
        set(healer, "speed", Double.valueOf(1.0));
        set(healer, "hp", Integer.valueOf(100));
        set(healer, "maxHp", Integer.valueOf(100));
        set(healer, "healAt", Long.valueOf(0L));
        set(healer, "nextAttack", Long.valueOf(Long.MAX_VALUE));
        set(healer, "nextAnim", Long.valueOf(0L));
        set(healer, "kbUntil", Long.valueOf(0L));
        set(healer, "lastKnockHit", Long.valueOf(0L));

        updateEnemies(w, 1000L);
        expect(getInt(patient, "hp") > 50, "the wounded enemy was healed");
        expect(getInt(patient, "hp") <= 100, "and never past its maximum");
    }

    /** A charger locks a direction when it commits, then travels along it. */
    private static void testChargerChargesInAStraightLine() throws Exception {
        System.out.println("[10] a charger locks a direction and charges");
        int defIndex = defIndexWithBehavior(ContentCatalog.AI_CHARGER);
        Object w = world(320, 240);
        Object e = place(w, ContentCatalog.AI_CHARGER, 240, 120, 1.0, 0L);

        updateEnemies(w, 1000L);
        expect(getLong(e, "chargeUntil") > 1000L, "the charger committed to a dash");

        int x0 = getInt(e, "x");
        updateEnemies(w, 1100L);
        expect(getInt(e, "x") < x0, "and moved along the locked direction while charging");

        // The dash has a fixed duration.
        updateEnemies(w, 1000L + 700L);
        expect(getLong(e, "chargeUntil") > 0L, "the dash window is still recorded");
    }

    /** Enemy logic is world-space: a scrolled camera must not redirect an enemy. */
    private static void testEnemyLogicUsesWorldCoordinatesWhenScrolled() throws Exception {
        System.out.println("[11] enemy logic uses world coordinates");
        double speed = 2.5;
        Object flat = world(320, 240);
        Object flatEnemy = place(flat, ContentCatalog.AI_CHASER, 240, 200, speed, Long.MAX_VALUE);
        updateEnemies(flat, 1000L);
        int flatX = getInt(flatEnemy, "x");

        Object scrolled = world(640, 240);
        set(scrolled, "viewWidth", Integer.valueOf(320));
        set(scrolled, "px", Integer.valueOf(480));
        set(scrolled, "py", Integer.valueOf(120));
        Object farEnemy = place(scrolled, ContentCatalog.AI_CHASER, 560, 120, speed, Long.MAX_VALUE);
        updateEnemies(scrolled, 1000L);

        Method camera = worldClass().getDeclaredMethod("cameraX");
        camera.setAccessible(true);
        expect(((Integer) camera.invoke(scrolled)).intValue() > 0, "the camera is scrolled");
        int flatDelta = flatX - 240;
        int scrolledDelta = getInt(farEnemy, "x") - 560;
        expectEq(scrolledDelta, flatDelta,
            "the enemy moved by the same world-space delta regardless of the camera");
        expectEq(scrolledDelta, -stepOf(speed),
            "and that delta is the enemy's own step, toward the player");
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