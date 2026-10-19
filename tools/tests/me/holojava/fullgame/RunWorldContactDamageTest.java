package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Regression cover for the chaser contact-damage gate, which was inverted.
 *
 * <p>Contact damage read
 *
 * <pre>
 *   if (now &lt; invulUntil || now &gt;= e.nextAttack) { /* nothing *&#47; }
 *   else if (overlap(...)) damagePlayer(...)
 * </pre>
 *
 * but {@code nextAttack} is a "not ready until" stamp -- it is set to {@code now + cooldownMs} on
 * spawn and again after every hit, exactly like the bomber, shooter, charger, orbit and boss
 * paths, all of which gate on {@code now >= e.nextAttack}. With the guard the other way round, a
 * chaser could only ever bite during the first second of its life: after that the cooldown had
 * elapsed, {@code now >= e.nextAttack} became true, and the enemy was skipped forever.
 *
 * <p>That is why Stage 1 looked broken while Sawit and Fantasy Island looked fine. Grassy Plains
 * is built from chasers (shrimp, dark shrimp) and nothing else that damages on contact, so a
 * player who kites one past its first second is never touched again and the HP readout never moves.
 * The stages that felt normal lean on bomber/shooter/orbit paths, which were never affected.
 *
 * <p>These drive the real update loop rather than re-implementing the gate, so the fix cannot be
 * satisfied by changing the test.
 *
 * <p><b>Not proven.</b> Rendering and the exact frames of a hit; those need a handset.
 */
public final class RunWorldContactDamageTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testChaserKeepsHurtingAfterItsFirstSecond();
        testChaserStillRespectsInvulnerabilityAndCooldown();
        testEveryStageHasADamageSource();

        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        if (failures != 0) System.exit(1);
    }

    /**
     * The regression itself: an enemy that has been alive long enough for its first cooldown to
     * expire must still be able to damage the player on contact, and must keep doing so. Under the
     * old guard this is exactly the case that silently did nothing.
     */
    private static void testChaserKeepsHurtingAfterItsFirstSecond() throws Exception {
        section("a chaser still damages the player once its first cooldown has expired");
        ContentCatalog.EnemyDef shrimp = ContentCatalog.enemy("shrimp");
        expect(shrimp != null, "the stage 1 chaser exists in the catalog");

        Object w = world(shrimp);
        long t0 = 100000L;

        // Freshly spawned: nextAttack is now + 1000ms, so the enemy is still cooling down.
        setEnemy(w, "nextAttack", Long.valueOf(t0 + 1000L));
        int hp = getInt(w, "playerHp");
        step(w, t0);
        expectEq(getInt(w, "playerHp"), hp, "a freshly spawned enemy is still on cooldown");

        // Cooldown elapsed: this is the contact that the inverted guard swallowed.
        set(w, "invulUntil", Long.valueOf(0L));
        setEnemy(w, "nextAttack", Long.valueOf(t0));
        hp = getInt(w, "playerHp");
        step(w, t0 + 2000L);
        expect(getInt(w, "playerHp") < hp,
                "the chaser bites once its cooldown has expired, not only in its first second");

        // And it keeps biting across several cooldowns, which is the part that never worked at all.
        for (int i = 0; i < 3; i++) {
            set(w, "invulUntil", Long.valueOf(0L));
            int before = getInt(w, "playerHp");
            long t = t0 + 2000L + 5000L * (i + 1);
            setEnemy(w, "nextAttack", Long.valueOf(t - 3000L));   // long expired again
            step(w, t);
            expect(getInt(w, "playerHp") < before,
                    "a chaser keeps dealing damage on contact " + (i + 2) + " (not a one-shot)");
        }
    }

    /**
     * The other half of the gate still has to hold, otherwise the fix above would leave the
     * player being chewed on every frame: the invulnerability window and the enemy cooldown must
     * both suppress the hit.
     */
    private static void testChaserStillRespectsInvulnerabilityAndCooldown() throws Exception {
        section("the invulnerability window and the cooldown still suppress a hit");
        ContentCatalog.EnemyDef shrimp = ContentCatalog.enemy("shrimp");
        long t = 200000L;

        Object shielded = world(shrimp);
        set(shielded, "invulUntil", Long.valueOf(t + 5000L));  // just got hit
        setEnemy(shielded, "nextAttack", Long.valueOf(t - 1000L));  // cooldown long expired
        int hp = getInt(shielded, "playerHp");
        step(shielded, t);
        expectEq(getInt(shielded, "playerHp"), hp, "no damage while the player is invulnerable");

        Object cooling = world(shrimp);
        set(cooling, "invulUntil", Long.valueOf(0L));
        setEnemy(cooling, "nextAttack", Long.valueOf(t + 5000L));   // still on cooldown
        hp = getInt(cooling, "playerHp");
        step(cooling, t);
        expectEq(getInt(cooling, "playerHp"), hp, "no damage while the enemy is on cooldown");
    }

    /**
     * Guards the reason the bug was so visible: Grassy Plains is chaser-only, so when the chaser
     * path is dead the stage has no working damage source at all. This asserts every stage names
     * real enemies, so a typo there would fail here rather than as an empty stage in play.
     */
    private static void testEveryStageHasADamageSource() throws Exception {
        section("every stage's enemies resolve and can damage the player");
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            ContentCatalog.StageDef s = ContentCatalog.STAGES[i];
            String[] ids = { s.enemyA, s.enemyB, s.enemyC };
            for (String id : ids) {
                ContentCatalog.EnemyDef def = ContentCatalog.enemy(id);
                expect(def != null, "stage '" + s.id + "' enemy '" + id + "' resolves");
                if (def != null) {
                    expect(def.atk > 0,
                            "stage '" + s.id + "' enemy '" + id + "' deals damage (atk "
                                    + def.atk + ")");
                }
            }
            // Time Mode stages have no boss by design, so there is nothing to resolve for them.
            if (s.bossA != null && s.bossA.length() > 0) {
                ContentCatalog.BossDef boss = ContentCatalog.boss(s.bossA);
                expect(boss != null, "stage '" + s.id + "' boss '" + s.bossA + "' resolves");
                if (boss != null) {
                    expect(boss.atk > 0,
                            "stage '" + s.id + "' boss deals damage (atk " + boss.atk + ")");
                }
            }
        }
    }

    // ---------------------------------------------------------------- fixture

    /**
     * A world with one live enemy sitting on top of the player, so a single step is enough to
     * decide whether contact damage happens. Everything the update loop touches that is not the
     * subject of a test is set to a value that keeps it out of the way.
     */
    private static Object world(ContentCatalog.EnemyDef def) throws Exception {
        Class<?> wc = worldClass();
        Object w = allocate(wc);
        set(w, "save", null);
        set(w, "assets", null);
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "state", Integer.valueOf(0));                 // STATE_RUNNING
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "px", Integer.valueOf(150));
        set(w, "py", Integer.valueOf(100));
        set(w, "facingX", Integer.valueOf(1));
        set(w, "random", new java.util.Random(1));
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "shield", Integer.valueOf(0));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "viewWidth", Integer.valueOf(320));
        set(w, "viewHeight", Integer.valueOf(240));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "slowEnemiesUntil", Long.valueOf(0L));

        Class<?> enemyClass = Class.forName("me.holojava.fullgame.RunWorld$Enemy");
        Object e = allocate(enemyClass);
        set(e, "defIndex", Integer.valueOf(indexOfEnemy(def.id)));
        set(e, "x", Integer.valueOf(150));
        set(e, "y", Integer.valueOf(100));
        set(e, "speed", Double.valueOf(0.0));               // hold still, straight onto the player
        set(e, "hp", Integer.valueOf(def.hp));
        set(e, "maxHp", Integer.valueOf(def.hp));
        set(e, "lastKnockHit", Long.valueOf(Long.MIN_VALUE / 2));
        Object[] pool = (Object[]) java.lang.reflect.Array.newInstance(enemyClass, 1);
        pool[0] = e;
        set(w, "enemies", pool);
        return w;
    }

    private static int indexOfEnemy(String id) {
        for (int i = 0; i < ContentCatalog.ENEMIES.length; i++) {
            if (ContentCatalog.ENEMIES[i].id.equals(id)) return i;
        }
        throw new IllegalStateException("no enemy " + id);
    }

    /** Sets a field on the world's single live enemy. */
    private static void setEnemy(Object w, String field, Object value) {
        set(((Object[]) get(w, "enemies"))[0], field, value);
    }

    /**
     * Runs one real update tick with the single enemy awake and standing on the player, so the
     * gate decides the outcome on its own.
     */
    private static void step(Object w, long now) throws Exception {
        Object[] pool = (Object[]) get(w, "enemies");
        set(pool[0], "active", Boolean.TRUE);
        Method updateEnemies = worldClass().getDeclaredMethod("updateEnemies", long.class);
        updateEnemies.setAccessible(true);
        updateEnemies.invoke(w, Long.valueOf(now));
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
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
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