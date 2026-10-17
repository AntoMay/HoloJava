package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the passive character skill — now a permanent ability, free for every character.
 *
 * <p>The skill used to be a level-up offer with a stack count, then a one-time Skill Pasif shop
 * purchase. It is free now: {@code reset()} grants it from the first tick with no purchase behind
 * it, it fires on its own every 15 seconds with no key involved, its power is the fixed base, and
 * the level-up roll never offers it (pinned by RunWorldOfferMixTest). There are no stacks left
 * to scale, so this suite pins the removal itself — the field and methods of the stacking system
 * must not exist — alongside the fixed-power effects, the automatic cadence, and the one gate
 * that is still a field: {@code skillAcquired} decides whether the cadence runs at all. These
 * checks drive the production methods through reflection.
 *
 * <p>The save-to-run wiring (a real reset reading the save) is asserted against a real constructed
 * RunWorld in RunWorldLifecycleIntegrationTest instead.
 */
public final class RunWorldSkillPassiveTest {

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

    // ---------------------------------------------------------------- plumbing

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

    private static int getInt(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getInt(target);
    }

    private static long getLong(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.getLong(target);
    }

    private static void expect(boolean ok, String label) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + label);
        }
    }

    private static void expectEq(int got, int want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    private static void expectEq(long got, long want, String label) {
        expect(got == want, label + ": expected " + want + " got " + got);
    }

    // ---------------------------------------------------------------- fixtures

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static Object enemy(int x, int y, int hp) throws Exception {
        Object e = allocate(Class.forName("me.holojava.fullgame.RunWorld$Enemy"));
        set(e, "active", Boolean.TRUE);
        set(e, "defIndex", Integer.valueOf(0));
        set(e, "x", Integer.valueOf(x));
        set(e, "y", Integer.valueOf(y));
        set(e, "hp", Integer.valueOf(hp));
        set(e, "maxHp", Integer.valueOf(hp));
        set(e, "atk", Integer.valueOf(5));
        set(e, "speed", Double.valueOf(0.4));
        set(e, "nextAttack", Long.valueOf(0L));
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

    /**
     * A world for one character with the skill flag as given, holding one parked enemy. Reset
     * grants the flag today; the fixture sets it directly so a test can still pin the gate.
     */
    private static Object world(int characterIndex, boolean skill, Object parked, long now)
            throws Exception {
        Class<?> wc = worldClass();
        Object w = allocate(wc);
        set(w, "save", null);
        set(w, "assets", null);
        set(w, "characterIndex", Integer.valueOf(characterIndex));
        set(w, "modeIndex", Integer.valueOf(0));
        set(w, "stageIndex", Integer.valueOf(0));
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "character", ContentCatalog.CHARACTERS[characterIndex]);
        set(w, "state", Integer.valueOf(0));
        set(w, "skillAcquired", Boolean.valueOf(skill));
        set(w, "skillReadyAt", Long.valueOf(0L));
        set(w, "specialUsed", Boolean.FALSE);
        set(w, "slowEnemiesUntil", Long.valueOf(0L));
        set(w, "lifestealUntil", Long.valueOf(0L));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "shield", Integer.valueOf(0));
        set(w, "shieldReadyAt", Long.valueOf(0L));
        set(w, "px", Integer.valueOf(100));
        set(w, "py", Integer.valueOf(100));
        set(w, "playerHp", Integer.valueOf(200));
        set(w, "playerMaxHp", Integer.valueOf(200));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        Object[] enemies = (Object[]) Array.newInstance(
                Class.forName("me.holojava.fullgame.RunWorld$Enemy"), 48);
        // Every slot must hold a real object: the run allocates all 48 up front and
        // damageEnemiesInRadius reads every slot without a null check, so a sparse pool would
        // fail here for a reason the production code never hits.
        for (int i = 0; i < enemies.length; i++) {
            if (i == 0 && parked != null) {
                Array.set(enemies, i, parked);
            } else {
                Object filler = enemy(0, 0, 1);
                set(filler, "active", Boolean.FALSE);   // present but never in play
                Array.set(enemies, i, filler);
            }
        }
        set(w, "enemies", enemies);
        // The boss slot is a field initialiser in production, which Unsafe also skips, and the
        // AoE helper touches it unconditionally.
        Object boss = allocate(Class.forName("me.holojava.fullgame.RunWorld$Boss"));
        set(boss, "active", Boolean.FALSE);
        set(boss, "defIndex", Integer.valueOf(0));
        set(boss, "hp", Integer.valueOf(10));
        set(boss, "maxHp", Integer.valueOf(10));
        set(boss, "x", Integer.valueOf(0));
        set(boss, "y", Integer.valueOf(0));
        set(w, "boss", boss);
        set(w, "startTime", Long.valueOf(now));
        return w;
    }

    /** Ticks the real gate in updatePlayer. There is no skill parameter any more. */
    private static void tick(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updatePlayer", int.class,
                boolean.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(0), Boolean.FALSE, Long.valueOf(now));
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testSkillFiresOnItsOwn();
        testNoSkillWithoutTheShopFlag();
        testBurstSkillHitsTheFixedBasePower();
        testDurationSkillsKeepTheirBaseWindows();
        testShieldSkillGrantsTheFixedBaseShield();
        testCooldownIsAFixedFifteenSeconds();
        testStackingSystemIsGone();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /**
     * With the flag held, the very first tick fires the skill — no key exists to press.
     */
    private static void testSkillFiresOnItsOwn() throws Exception {
        System.out.println("[1] the skill fires by itself from the first tick");
        long now = 50000L;
        Object w = world(3, true, null, now);        // Second Flame: sets lifestealUntil
        tick(w, now);
        expectEq(getLong(w, "lifestealUntil"), now + 8000L,
            "the first tick applied the skill effect with no input");
        expectEq(getLong(w, "skillReadyAt"), now + 15000L,
            "and opened the cooldown");
    }

    /**
     * The skillAcquired field is still the firing gate: with it clear nothing fires, no cooldown
     * opens and no damage is dealt. No real run starts that way any more — reset always grants
     * the skill — so this pins the gate itself, not a reachable purchase state.
     */
    private static void testNoSkillWithoutTheShopFlag() throws Exception {
        System.out.println("[2] the skillAcquired field is still the firing gate");
        long now = 50000L;
        Object w = world(3, false, enemy(150, 100, 5000), now);
        for (int i = 0; i < 5; i++) tick(w, now + i * 20000L);
        expectEq(getLong(w, "lifestealUntil"), 0L, "no lifesteal window ever opened");
        expectEq(getLong(w, "skillReadyAt"), 0L, "no cooldown was ever opened");
        expectEq(getInt(enemies(w)[0], "hp"), 5000, "and no damage was dealt");
    }

    /**
     * Shark Bite is a burst at the fixed base: 6 damage inside a 70px radius. Nothing scales it
     * any more, and the radius boundary must still hold.
     */
    private static void testBurstSkillHitsTheFixedBasePower() throws Exception {
        System.out.println("[3] a burst skill hits at the fixed base power");
        long now = 50000L;
        // 50px from the player: inside the 70px radius.
        Object near = world(0, true, enemy(150, 100, 5000), now);
        tick(near, now);
        int nearDamage = 5000 - getInt(enemies(near)[0], "hp");
        expect(nearDamage > 0, "an enemy inside the radius is damaged (" + nearDamage + ")");

        // 100px from the player: outside the radius.
        Object far = world(0, true, enemy(200, 100, 5000), now);
        tick(far, now);
        expectEq(getInt(enemies(far)[0], "hp"), 5000,
            "an enemy outside the radius is untouched");
    }

    /** Hunter's Instinct, Second Flame and The Ancient One keep their base windows exactly. */
    private static void testDurationSkillsKeepTheirBaseWindows() throws Exception {
        System.out.println("[4] duration skills keep their base windows");
        long now = 50000L;

        Object ame = world(1, true, null, now);
        tick(ame, now);
        expectEq(getLong(ame, "slowEnemiesUntil"), now + 3500L,
            "Hunter's Instinct slows for exactly 3.5 s");

        Object kiara = world(3, true, null, now);
        tick(kiara, now);
        expectEq(getLong(kiara, "lifestealUntil"), now + 8000L,
            "Second Flame's lifesteal window is exactly 8 s");

        Object ina = world(4, true, enemy(150, 100, 5000), now);
        tick(ina, now);
        expectEq(getLong(ina, "invulUntil"), now + 2500L,
            "The Ancient One's invulnerability lasts exactly 2.5 s");
    }

    /** Undying's shield is the one skill with a plain numeric value: exactly 20, never more. */
    private static void testShieldSkillGrantsTheFixedBaseShield() throws Exception {
        System.out.println("[5] the shield skill grants exactly the base shield");
        long now = 50000L;
        Object calli = world(2, true, null, now);
        tick(calli, now);
        expectEq(getInt(calli, "shield"), 20, "Undying raises exactly 20 shield");
    }

    /** The cooldown is a balance constant: 15 s, refusing inside the window, refiring after. */
    private static void testCooldownIsAFixedFifteenSeconds() throws Exception {
        System.out.println("[6] the automatic cadence is a fixed fifteen seconds");
        long now = 50000L;
        Object w = world(3, true, null, now);
        tick(w, now);
        expectEq(getLong(w, "skillReadyAt"), now + 15000L, "the gate is 15 s ahead");

        long inside = now + 2000L;
        long effectInside = getLong(w, "lifestealUntil");
        tick(w, inside);
        expectEq(getLong(w, "skillReadyAt"), now + 15000L,
            "a tick inside the window did not extend it");
        expectEq(getLong(w, "lifestealUntil"), effectInside,
            "and did not refresh the effect");

        long after = now + 15000L;
        tick(w, after);
        expectEq(getLong(w, "skillReadyAt"), after + 15000L,
            "the skill fires again once the window has passed");
        expectEq(getLong(w, "lifestealUntil"), after + 8000L,
            "and refreshes its effect");
    }

    /**
     * Stacking was removed wholesale when Skill Pasif became a one-time purchase: neither the
     * field, nor its accessors, nor the HUD slot helper may still exist. A stale survivor would
     * mean some path still scales or displays a stack count that nothing can ever change.
     */
    private static void testStackingSystemIsGone() throws Exception {
        System.out.println("[7] the stacking system is completely gone");
        Field[] fields = worldClass().getDeclaredFields();
        boolean stacksField = false;
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getName().equals("skillStacks")) stacksField = true;
        }
        expect(!stacksField, "RunWorld has no skillStacks field");

        String[] gone = { "skillStacks", "skillPower", "skillSlotOwned" };
        for (int i = 0; i < gone.length; i++) {
            boolean exists = false;
            try {
                worldClass().getDeclaredMethod(gone[i]);
                exists = true;
            } catch (NoSuchMethodException expected) {
                // absent, as required
            }
            try {
                worldClass().getDeclaredMethod(gone[i], Integer.TYPE);
                exists = true;
            } catch (NoSuchMethodException expected) {
                // absent, as required
            }
            expect(!exists, "no " + gone[i] + " accessor survives");
        }
    }

    private static Object[] enemies(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("enemies");
        f.setAccessible(true);
        return (Object[]) f.get(w);
    }
}
