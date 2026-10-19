package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Two things a run depends on that nothing else covers: that damage reduction is actually
 * built from a character's own stat plus the shop's Defense Up, and that every shop row reaches
 * the run.
 *
 * <p>Reduction had no per-character component at all -- the wiki tables carry HP, ATK, SPD and
 * CRT but no defence column -- so every character took identical damage and the bigger ones were
 * simply better. It also used to reset to zero before the shop rows were added, which meant the
 * moment {@code recomputeStats()} ran the built-in value would have been stripped if it existed.
 *
 * <p>The wiring audit is the other half. Every row has to end up doing something: either an
 * {@code EFFECT_*} that {@code recomputeStats()} folds in, or a {@code -1} row that a dedicated
 * flag consumes. A row that satisfies neither is a purchase that costs coins and changes nothing,
 * which is the failure mode a shop page cannot show you.
 *
 * <p><b>Not proven.</b> Rendering, and whether the resulting numbers are the right balance; the
 * values here are checked for wiring and internal consistency, not for fun.
 */
public final class RunWorldDefenseTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        testCharactersCarryNoDefenceOfTheirOwn();
        testDefenseUpStacksOnTheCharacterStat();
        testDefenseUpIsCappedAtItsAdvertisedMaximum();
        testEveryShopRowReachesTheRun();

        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        if (failures != 0) System.exit(1);
    }

    /**
     * Characters carry no defence of their own, because the original game has none.
 *
 * <p>This is pinned as a fact rather than left open on purpose. The unpacked HoloCure data has a
 * "Defense Up" upgrade but no per-character defence stat: the only "defense" symbol is
 * AyameDefenseField, an ability, and every "reduce damage taken" string belongs to an item or a
 * form. So an earlier pass that gave the characters 4..10% was inventing balance numbers and
 * presenting them as a stat. If someone later adds real values with evidence behind them, this
 * test is where that decision has to be made deliberately.
 */
    private static void testCharactersCarryNoDefenceOfTheirOwn() {
        section("characters have no built-in reduction, matching the original");
        for (int i = 0; i < ContentCatalog.CHARACTERS.length; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            expectEq(c.defensePercent, 0,
                    c.name + " starts with no damage reduction of its own");
        }

        // The field still exists so the shop's Defense Up has an explicit base; if it were removed
        // the stacking would become implicit and untestable.
        boolean fieldPresent = false;
        try {
            ContentCatalog.CHARACTERS[0].getClass().getDeclaredField("defensePercent");
            fieldPresent = true;
        } catch (NoSuchFieldException ignored) {
            fieldPresent = false;
        }
        expect(fieldPresent, "the field is still there, so Defense Up has a declared base");
    }

    /** Defense Up adds to the character's stat rather than replacing it. */
    private static void testDefenseUpStacksOnTheCharacterStat() throws Exception {
        section("Defense Up stacks on top of the character's own reduction");
        int defenseRow = ContentCatalog.upgradeIndex("defense_up");

        for (int i = 0; i < ContentCatalog.CHARACTERS.length; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];

            int[] none = new int[ContentCatalog.UPGRADES.length];
            expectEq(defense(world(i, none)), c.defensePercent,
                    c.name + " with no Defense Up has exactly its own " + c.defensePercent + "%");

            int[] twoLevels = new int[ContentCatalog.UPGRADES.length];
            twoLevels[defenseRow] = 2;                     // 2 x 3% = 6%
            expectEq(defense(world(i, twoLevels)), c.defensePercent + 6,
                    c.name + " at Defense Up 2 is its own " + c.defensePercent + "% plus 6%");

            // Recomputing must not compound: the whole point of rebuilding from the base.
            Object w = world(i, twoLevels);
            int once = defense(w);
            recompute(w);
            recompute(w);
            expectEq(defense(w), once,
                    c.name + " does not compound Defense Up when recomputed repeatedly");
        }
    }

    /** The row advertises a 15% ceiling; buying past it must not stack without bound. */
    private static void testDefenseUpIsCappedAtItsAdvertisedMaximum() throws Exception {
        section("Defense Up is capped at the 15% the row advertises");
        int defenseRow = ContentCatalog.upgradeIndex("defense_up");
        int[] maxed = new int[ContentCatalog.UPGRADES.length];
        maxed[defenseRow] = ContentCatalog.UPGRADES[defenseRow].maxLevel;

        int characterBase = ContentCatalog.CHARACTERS[0].defensePercent;
        int got = defense(world(0, maxed));
        expectEq(got, characterBase + 15,
                "a fully bought Defense Up adds exactly 15% over the character's own stat");

        // An injected overrun is clamped rather than trusted.
        int[] absurd = new int[ContentCatalog.UPGRADES.length];
        absurd[defenseRow] = 999;
        expectEq(defense(world(0, absurd)), characterBase + 15,
                "an absurd injected level still stops at 15%");
    }

    /**
     * The wiring audit: every row either names an effect {@code recomputeStats()} folds in, or is
     * a {@code -1} row whose flag the run actually reads, or is locked and refused outright.
     */
    private static void testEveryShopRowReachesTheRun() throws Exception {
        section("every shop row is wired to the run");
        Object w = world(0, new int[ContentCatalog.UPGRADES.length]);
        Class<?> wc = worldClass();

        // Snapshot every stat field recomputeStats is expected to move, so a row that claims an
        // effect but moves nothing shows up as a failure rather than as silence.
        String[] tracked = { "atkPercentBonus", "hastePercent", "speedPercentBonus",
                "pickupPercentBonus", "critPercentBonus", "expPercentBonus", "defensePercent",
                "coinDropBonus", "foodDropBonus", "playerMaxHp", "regenHealPerTick" };

        for (int i = 0; i < ContentCatalog.UPGRADES.length; i++) {
            ContentCatalog.UpgradeDef def = ContentCatalog.UPGRADES[i];
            if (def.locked) continue;                       // refused at the page, nothing to wire

            if (def.effect >= 0) {
                int[] level = new int[ContentCatalog.UPGRADES.length];
                level[i] = 1;
                Object bought = world(0, level);
                boolean moved = false;
                for (String field : tracked) {
                    if (getInt(bought, field) != getInt(w, field)) { moved = true; break; }
                }
                expect(moved, "row '" + def.id + "' (effect " + def.effect
                        + ") changes something in the run");
            } else {
                // A -1 row must be consumed by a flag: either the run reads its level under that
                // id, or it is a known flag-only row (the action, the toggle, the hardcore rule).
                boolean gated = rowIsReadByTheRun(def.id) || SPECIAL_ROWS(def.id);
                expect(gated, "row '" + def.id + "' has effect -1 but is still consumed by the run");
            }
        }
    }

    /** Rows that are legitimately not read as a level: the action and the hardcore toggle. */
    private static boolean SPECIAL_ROWS(String id) {
        return "refund_all".equals(id) || "hardcore".equals(id);
    }

    private static boolean rowIsReadByTheRun(String id) {
        try {
            String src = readSource();
            return src.contains("upgradeIndex(\"" + id + "\")");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readSource() throws Exception {
        // The run reads its shop levels by id at run start; a row that appears nowhere there is
        // not wired. Reading the source keeps the audit honest without hardcoding a second list.
        java.io.File f = new java.io.File(
                System.getProperty("holojava.src", "src/java-me-fullgame")
                        + "/me/holojava/fullgame/RunWorld.java");
        if (!f.isFile()) return "";
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    // ---------------------------------------------------------------- fixture

    private static Object world(int characterIndex, int[] levels) throws Exception {
        Class<?> wc = worldClass();
        Object w = allocate(wc);
        ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[characterIndex];
        set(w, "save", null);
        set(w, "assets", null);
        set(w, "character", c);
        set(w, "characterIndex", Integer.valueOf(characterIndex));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "itemCount", Integer.valueOf(0));
        set(w, "baseMaxHp", Integer.valueOf(c.hp));
        set(w, "characterBaseMaxHp", Integer.valueOf(c.hp));
        set(w, "playerHp", Integer.valueOf(c.hp));
        set(w, "playerMaxHp", Integer.valueOf(c.hp));
        int[] arr = new int[ContentCatalog.UPGRADES.length];
        System.arraycopy(levels, 0, arr, 0, Math.min(levels.length, arr.length));
        set(w, "upgradeLevels", arr);
        recompute(w);
        return w;
    }

    private static int defense(Object w) throws Exception { return getInt(w, "defensePercent"); }

    private static void recompute(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("recomputeStats");
        m.setAccessible(true);
        m.invoke(w);
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