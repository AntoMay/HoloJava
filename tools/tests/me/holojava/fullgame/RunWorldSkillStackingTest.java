package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers stackable character skills.
 *
 * <p>Reported as "taking the skill slot a second time should still do something". It did not: the
 * skill was a one-shot boolean, {@code skillAcquired}, and both the offer generator and the fill
 * step refused once it was set, so the skill could never be offered or taken twice. That same dead
 * flag was half of why stat upgrades were unreachable, since two of the four slots were consumed by a
 * skill offer that could not vary.
 *
 * <p>The skill is now a stack count up to {@code ContentCatalog.SKILL_MAX_STACKS}. Burst skills scale
 * their damage and radius, duration skills scale their window, and the one numeric skill (Undying's
 * shield) scales its value. These checks drive the production methods through reflection.
 */
public final class RunWorldSkillStackingTest {

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

    private static Object get(Object target, String field) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
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

    private static void expectEq(String got, String want, String label) {
        expect(want.equals(got), label + ": expected " + want + " got " + got);
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

    /** A world for one character, holding a single parked enemy and a skill of the given stacks. */
    private static Object world(int characterIndex, int stacks, Object parked, long now) throws Exception {
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
        set(w, "skillAcquired", Boolean.valueOf(stacks > 0));
        set(w, "skillStacks", Integer.valueOf(stacks));
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

    private static void cast(Object w, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("activateSkill", long.class);
        m.setAccessible(true);
        m.invoke(w, Long.valueOf(now));
    }

    private static void applySkillOffer(Object w) throws Exception {
        Object offer = allocate(Class.forName("me.holojava.fullgame.RunWorld$Offer"));
        set(offer, "type", Integer.valueOf(0));       // OFFER_SKILL
        set(offer, "value", Integer.valueOf(0));
        set(offer, "existing", Integer.valueOf(-1));
        set(offer, "newItem", Boolean.FALSE);
        set(offer, "collab", Boolean.FALSE);
        Object[] offers = (Object[]) Array.newInstance(
                Class.forName("me.holojava.fullgame.RunWorld$Offer"), 6);
        Array.set(offers, 0, offer);
        set(w, "offers", offers);
        Method m = worldClass().getDeclaredMethod("applyOffer", int.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(0));
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testTakingTheSkillStacks();
        testStacksAreCapped();
        testBurstSkillScalesWithStacks();
        testDurationSkillScalesWithStacks();
        testShieldSkillScalesWithStacks();
        testPowerFloorsAtOneStack();
        testCooldownIsUnaffectedByStacks();
        testLabelShowsTheLevel();
        testSkillRowReportsTheStackLevel();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** The reported bug: the second, third and later skill offers must each count. */
    private static void testTakingTheSkillStacks() throws Exception {
        System.out.println("[1] taking the skill repeatedly raises its stack count");
        long now = System.currentTimeMillis();
        Object w = world(0, 0, null, now);
        expectEq(getInt(w, "skillStacks"), 0, "a fresh run starts with no stacks");
        for (int i = 1; i <= 3; i++) {
            applySkillOffer(w);
            expectEq(getInt(w, "skillStacks"), i,
                "after taking the skill " + i + " time(s) the stack count is " + i);
        }
        expect((Boolean) get(w, "skillAcquired"), "the skill is held after taking it");
    }

    /** Past the cap, the offer can no longer be applied, so the count cannot run away. */
    private static void testStacksAreCapped() throws Exception {
        System.out.println("[2] the stack count cannot pass the cap");
        long now = System.currentTimeMillis();
        int max = ContentCatalog.SKILL_MAX_STACKS;
        Object w = world(0, 0, null, now);
        for (int i = 0; i < max + 6; i++) applySkillOffer(w);
        expectEq(getInt(w, "skillStacks"), max, "the count stopped at the cap of " + max);
        expect(max >= 2, "the cap allows stacking at all");
    }

    /** Shark Bite scales its damage and radius, so more stacks hit harder and further. */
    private static void testBurstSkillScalesWithStacks() throws Exception {
        System.out.println("[3] a burst skill hits harder with every stack");
        long now = System.currentTimeMillis();
        // One enemy inside the 70px radius of a single stack, outside a doubled radius's difference.
        Object single = world(0, 1, enemy(150, 100, 5000), now);
        cast(single, now);
        int oneStackDamage = 5000 - getInt(enemies(single)[0], "hp");

        Object three = world(0, 3, enemy(150, 100, 5000), now);
        cast(three, now);
        int threeStackDamage = 5000 - getInt(enemies(three)[0], "hp");

        expect(oneStackDamage > 0, "one stack damages the enemy (" + oneStackDamage + ")");
        expect(threeStackDamage > oneStackDamage,
            "three stacks damage harder than one (" + threeStackDamage + " vs " + oneStackDamage + ")");
    }

    /** Hunter's Instinct scales its window rather than its damage, since it has no damage. */
    private static void testDurationSkillScalesWithStacks() throws Exception {
        System.out.println("[4] a duration skill lasts longer with every stack");
        long now = System.currentTimeMillis();
        Object one = world(1, 1, null, now);
        cast(one, now);
        long oneWindow = getLong(one, "slowEnemiesUntil") - now;

        Object three = world(1, 3, null, now);
        cast(three, now);
        long threeWindow = getLong(three, "slowEnemiesUntil") - now;

        expectEq(oneWindow, 3500L, "one stack keeps the original 3.5 second slow");
        expectEq(threeWindow, 10500L, "three stacks stretch the slow to three times as long");
        expect(threeWindow > oneWindow, "more stacks means a longer window");
    }

    /** Undying's shield is the one skill with a numeric value, so it scales directly. */
    private static void testShieldSkillScalesWithStacks() throws Exception {
        System.out.println("[5] a shield skill grants more shield with every stack");
        long now = System.currentTimeMillis();
        Object one = world(2, 1, null, now);
        cast(one, now);
        expectEq(getInt(one, "shield"), 20, "one stack grants the original 20 shield");

        Object four = world(2, 4, null, now);
        cast(four, now);
        expectEq(getInt(four, "shield"), 80, "four stacks grant four times as much shield");
    }

    /**
     * A world whose skill flag was set without a stack count must still cast at the original power.
     * Several suites construct a world that way, and a stack count of 0 must not weaken the skill.
     */
    private static void testPowerFloorsAtOneStack() throws Exception {
        System.out.println("[6] skill power floors at one stack");
        long now = System.currentTimeMillis();
        Object flagOnly = world(2, 0, null, now);
        set(flagOnly, "skillAcquired", Boolean.TRUE);
        cast(flagOnly, now);
        expectEq(getInt(flagOnly, "shield"), 20,
            "a skill held without a stack count still casts at one stack's power");
    }

    /** The cooldown is a balance constant and must not drift with stacks. */
    private static void testCooldownIsUnaffectedByStacks() throws Exception {
        System.out.println("[7] the cooldown stays 15 seconds at any stack count");
        long now = System.currentTimeMillis();
        for (int stacks = 1; stacks <= ContentCatalog.SKILL_MAX_STACKS; stacks++) {
            Object w = world(0, stacks, null, now);
            cast(w, now);
            expectEq(getLong(w, "skillReadyAt") - now, 15000L,
                stacks + " stack(s) keep the 15 second cooldown");
        }
    }

    /** The offer label has to say which level taking it would reach. */
    private static void testLabelShowsTheLevel() throws Exception {
        System.out.println("[8] the skill offer label shows the level it would take you to");
        long now = System.currentTimeMillis();
        Object offer = allocate(Class.forName("me.holojava.fullgame.RunWorld$Offer"));
        set(offer, "type", Integer.valueOf(0));
        set(offer, "value", Integer.valueOf(0));
        set(offer, "existing", Integer.valueOf(-1));
        set(offer, "newItem", Boolean.FALSE);
        set(offer, "collab", Boolean.FALSE);
        Object[] offers = (Object[]) Array.newInstance(
                Class.forName("me.holojava.fullgame.RunWorld$Offer"), 6);
        Array.set(offers, 0, offer);

        Object w = world(0, 2, null, now);
        set(w, "offers", offers);
        set(w, "offerCount", Integer.valueOf(1));
        Method label = worldClass().getDeclaredMethod("offerLabel", int.class);
        label.setAccessible(true);
        expectEq((String) label.invoke(w, Integer.valueOf(0)),
                ContentCatalog.CHARACTERS[0].skill + " Lv3",
                "at two stacks the offer says it would be level 3");
    }

    /**
     * The reported doubt: the six HUD boxes under the weapon row must have a readable function.
     * Cell 0 marks the owned skill, cells 1..4 report stacks 2 up to the cap, and the last cell is
     * the MAX badge — so the row fills up as the skill is stacked instead of staying five dead
     * cells.
     */
    private static void testSkillRowReportsTheStackLevel() throws Exception {
        System.out.println("[9] the skill row cells report the stack level");
        long now = System.currentTimeMillis();
        Method owned = worldClass().getDeclaredMethod("skillSlotOwned", Integer.TYPE);
        owned.setAccessible(true);
        int max = ContentCatalog.SKILL_MAX_STACKS;

        Object fresh = world(0, 0, null, now);
        for (int cell = 0; cell < 6; cell++) {
            expect(!lit(owned, fresh, cell), "with no skill, cell " + cell + " stays dark");
        }

        Object one = world(0, 1, null, now);
        expect(lit(owned, one, 0), "one stack lights the first cell");
        for (int cell = 1; cell < 6; cell++) {
            expect(!lit(owned, one, cell), "at one stack, cell " + cell + " stays dark");
        }

        Object four = world(0, Math.min(4, max), null, now);
        for (int cell = 0; cell <= 3; cell++) {
            expect(lit(owned, four, cell), "four stacks light cells 0..3 (cell " + cell + ")");
        }
        expect(!lit(owned, four, 4), "cell 4 waits for the fifth stack");
        expect(!lit(owned, four, 5), "and the MAX badge waits for the cap");

        Object capped = world(0, max, null, now);
        for (int cell = 0; cell < 6; cell++) {
            expect(lit(owned, capped, cell), "at the cap the row is complete (cell " + cell + ")");
        }

        // A flag set without stacks still casts (skillPower floors at 1), so the skill cell must
        // still read as owned.
        Object flagOnly = world(0, 0, null, now);
        set(flagOnly, "skillAcquired", Boolean.TRUE);
        expect(lit(owned, flagOnly, 0), "a flag-only world still shows its usable skill");
        expect(!lit(owned, flagOnly, 5), "but claims no stacks it does not have");
    }

    private static boolean lit(Method owned, Object w, int cell) throws Exception {
        return ((Boolean) owned.invoke(w, Integer.valueOf(cell))).booleanValue();
    }

    /**
     * A run must never inherit stacks. Driving {@code reset} directly is not possible here because it
     * touches the weapon and item arrays that an {@code Unsafe}-allocated world never had, so this is
     * asserted against a real constructed RunWorld in RunWorldLifecycleIntegrationTest instead.
     */

    private static Object[] enemies(Object w) throws Exception {
        Field f = worldClass().getDeclaredField("enemies");
        f.setAccessible(true);
        return (Object[]) f.get(w);
    }
}