package me.holojava.fullgame;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Closes the two remaining backlog items:
 *
 * <ul>
 *   <li><b>HOLD-02</b> — the full behaviour of {@code skillAcquired}, {@code skillReadyAt} and
 *       {@code specialUsed}.</li>
 *   <li><b>HOLD-03</b> — the {@code statOfferCounts} boundary, which the backlog flagged as a
 *       possible {@code int} overflow at very high levels.</li>
 * </ul>
 *
 * <p>Parity with the reference JAR was established first, from bytecode: {@code activateSkill},
 * {@code activateSpecial}, {@code applyOffer}, {@code chooseOfferType}, {@code fillOffer} and
 * {@code skillAcquired} all carry identical integer and double constants in both. The reference
 * touches {@code statOfferCounts} only to initialise it, reset it, and index it for the increment —
 * it never reads a counter value either.
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldSkillAndStatTest {

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

    private static Object world(int characterIndex) throws Exception {
        Object w = allocate(worldClass());
        set(w, "character", ContentCatalog.CHARACTERS[characterIndex]);
        set(w, "characterIndex", Integer.valueOf(characterIndex));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "save", null);
        set(w, "random", new Random(11L));
        set(w, "state", Integer.valueOf(0));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));
        set(w, "px", Integer.valueOf(160));
        set(w, "py", Integer.valueOf(120));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "shield", Integer.valueOf(0));
        set(w, "shieldReadyAt", Long.valueOf(0L));
        set(w, "invulUntil", Long.valueOf(0L));
        set(w, "slowEnemiesUntil", Long.valueOf(0L));
        set(w, "lifestealUntil", Long.valueOf(0L));
        set(w, "playerLevel", Integer.valueOf(1));
        set(w, "expNeed", Integer.valueOf(79));
        set(w, "weaponCount", Integer.valueOf(0));
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "statOfferCounts", new int[6]);
        set(w, "offers", filled("me.holojava.fullgame.RunWorld$Offer", 6));
        set(w, "pickups", filled("me.holojava.fullgame.RunWorld$Pickup", 96));
        set(w, "enemies", filled("me.holojava.fullgame.RunWorld$Enemy", 48));
        set(w, "shots", filled("me.holojava.fullgame.RunWorld$Shot", 128));
        set(w, "boss", filled("me.holojava.fullgame.RunWorld$Boss", 1));
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

    private static void invoke(Object w, String name, Class<?> type, Object arg) throws Exception {
        Method m = worldClass().getDeclaredMethod(name, type);
        m.setAccessible(true);
        m.invoke(w, arg);
    }

    /** Places an offer in slot 0 and applies it through the production path. */
    private static void applyOffer(Object w, int type, int value) throws Exception {
        Field f = worldClass().getDeclaredField("offers");
        f.setAccessible(true);
        Object[] offers = (Object[]) f.get(w);
        Object o = offers[0];
        set(o, "type", Integer.valueOf(type));
        set(o, "value", Integer.valueOf(value));
        set(o, "existing", Integer.valueOf(-1));
        invoke(w, "applyOffer", int.class, Integer.valueOf(0));
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testSkillCannotBeUsedBeforeItIsAcquired();
        testSkillOpensAFifteenSecondCooldown();
        testSpecialIsAvailableExactlyOncePerRun();
        testStatOfferIndexAlwaysStaysInRange();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Drives the real gate in updatePlayer, not just the field it reads. */
    private static void press(Object w, boolean skill, boolean special, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updatePlayer", int.class, boolean.class,
                boolean.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(0), Boolean.valueOf(skill), Boolean.valueOf(special),
                Long.valueOf(now));
    }

    /**
     * The skill key does nothing until the skill has been taken. Both halves are driven through
     * {@code updatePlayer}, which is where the gate actually lives.
     */
    private static void testSkillCannotBeUsedBeforeItIsAcquired() throws Exception {
        System.out.println("[1] the skill is inert until acquired");
        Object w = world(3);                       // Second Flame: sets lifestealUntil
        set(w, "skillReadyAt", Long.valueOf(0L));
        long t = 700000L;

        expect(!getBool(w, "skillAcquired"), "no skill at the start of a run");
        press(w, true, false, t);
        expectEq(getLong(w, "lifestealUntil"), 0L, "pressing the skill key did nothing");
        expectEq(getLong(w, "skillReadyAt"), 0L, "and no cooldown was opened");

        applyOffer(w, 0, 0);                       // OFFER_SKILL
        expect(getBool(w, "skillAcquired"), "taking the skill offer grants it");

        press(w, true, false, t);
        expectEq(getLong(w, "skillReadyAt"), t + 15000L,
            "the same key now works and opens the cooldown");
        expectEq(getLong(w, "lifestealUntil"), t + 8000L, "and applies the skill effect");
    }

    /**
     * The 15000 ms cooldown comes from the reference's {@code activateSkill} bytecode. Re-pressing
     * inside it must not refresh the timer, which would let a player chain the skill forever.
     */
    private static void testSkillOpensAFifteenSecondCooldown() throws Exception {
        System.out.println("[2] the skill opens a fifteen second cooldown");
        Object w = world(3);
        set(w, "skillAcquired", Boolean.TRUE);
        set(w, "skillReadyAt", Long.valueOf(0L));

        long t = 100000L;
        press(w, true, false, t);
        expectEq(getLong(w, "skillReadyAt"), t + 15000L, "the gate is now 15 s ahead");
        expectEq(getLong(w, "lifestealUntil"), t + 8000L, "the skill effect was applied");

        // Pressing again inside the window must be refused by the gate, so neither the cooldown
        // nor the effect is refreshed. Asserting the fields alone would miss a dropped gate.
        long insideWindow = t + 2000L;
        press(w, true, false, insideWindow);
        expectEq(getLong(w, "skillReadyAt"), t + 15000L,
            "a press inside the cooldown did not extend it");
        expectEq(getLong(w, "lifestealUntil"), t + 8000L,
            "and did not refresh the skill effect");

        // Once the window has passed, the skill works again.
        long afterWindow = t + 15000L;
        press(w, true, false, afterWindow);
        expectEq(getLong(w, "skillReadyAt"), afterWindow + 15000L,
            "the skill is usable again after the cooldown expires");
        expectEq(getLong(w, "lifestealUntil"), afterWindow + 8000L,
            "and refreshes its effect");
    }

    /** One special per run, and it stays used even across many presses. */
    private static void testSpecialIsAvailableExactlyOncePerRun() throws Exception {
        System.out.println("[3] the special is available exactly once");
        Object w = world(1);                       // Slow Time: sets slowEnemiesUntil
        expect(!getBool(w, "specialUsed"), "the special is unused at the start of a run");

        long t = 500000L;
        press(w, false, true, t);
        expect(getBool(w, "specialUsed"), "using it marks it spent");
        expectEq(getLong(w, "slowEnemiesUntil"), t + 6000L, "and applied its effect");

        // Pressing again through the real gate must do nothing.
        long later = t + 60000L;
        press(w, false, true, later);
        expect(getBool(w, "specialUsed"), "it stays spent");
        expectEq(getLong(w, "slowEnemiesUntil"), t + 6000L,
            "and the effect was not applied a second time");
    }

    /**
     * HOLD-03. {@code statOfferCounts} is six slots and {@code fillOffer} only ever writes a stat
     * index in {@code [0,5]}, so the increment can never go out of bounds. The counter itself is
     * never read anywhere, in this build or the reference, so it cannot overflow in practice
     * either.
     */
    private static void testStatOfferIndexAlwaysStaysInRange() throws Exception {
        System.out.println("[4] the stat offer index always stays in range");
        Object w = world(0);
        Field counterField = worldClass().getDeclaredField("statOfferCounts");
        counterField.setAccessible(true);
        int[] counters = (int[]) counterField.get(w);
        expectEq(counters.length, 6, "the counter array has one slot per stat");

        for (int stat = 0; stat < counters.length; stat++) {
            applyOffer(w, 3, stat);                // OFFER_STAT
            expectEq(counters[stat], 1, "applying stat " + stat + " incremented exactly once");
        }
        for (int i = 0; i < counters.length; i++) expectEq(counters[i], 1, "slot " + i + " held 1");

        // The full weighted set fillOffer can produce stays inside the array.
        expect(ContentCatalog.CHARACTERS.length > 0, "the catalog loaded");
        Method fill = worldClass().getDeclaredMethod("fillOffer", int.class, int.class);
        fill.setAccessible(true);
        Field f = worldClass().getDeclaredField("offers");
        f.setAccessible(true);
        Object[] offers = (Object[]) f.get(w);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int attempt = 0; attempt < 400; attempt++) {
            for (int i = 0; i < offers.length; i++) set(offers[i], "type", Integer.valueOf(-1));
            set(w, "offerUsed", new boolean[64]);
            if (!(Boolean) fill.invoke(w, Integer.valueOf(0), Integer.valueOf(3))) continue;
            int v = getInt(offers[0], "value");
            if (v < min) min = v;
            if (v > max) max = v;
        }
        expect(min >= 0 && max < counters.length,
            "every generated stat index stayed within the array (observed " + min + ".." + max + ")");
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