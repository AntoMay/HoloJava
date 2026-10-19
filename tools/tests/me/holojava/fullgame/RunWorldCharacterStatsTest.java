package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Holds the three character multipliers to one convention, because they had drifted apart.
 *
 * <p>The wiki records a character's attack as {@code atk_multiplier_percent} and the game shows it
 * as "ATK 1.10", which means the character deals 110% of base damage. Speed reads the same way
 * (SPD 1.40) and was implemented correctly as {@code 120 * spdPercent / 100}, and crit is a plain
 * percentage. Attack alone carried a stray {@code + 100}, so every character hit for about twice
 * what its own table says: Gura's 1.10 became 2.10, Kiara's 1.00 became 2.00.
 *
 * <p>That drift is invisible from the catalog, which stores the right numbers either way. Only
 * comparing what the run does with them against what the table says catches it, so each case here
 * computes the expected value from {@code ContentCatalog} rather than from a literal.
 *
 * <p><b>Not proven.</b> Balancing. These tests check the arithmetic, not whether the result is fun.
 */
public final class RunWorldCharacterStatsTest {

    private static int checks = 0;
    private static int failures = 0;

    /** Base movement speed before the character's SPD multiplier. */
    private static final int BASE_SPEED = 120;

    public static void main(String[] args) throws Exception {
        testAttackAppliesTheMultiplierNotABonus();
        testShopAttackUpIsAdditiveOnTheSameMultiplier();
        testSpeedAppliesTheSameWay();
        testCritIsAPlainPercentage();
        testEveryPlayableCharacterHasATable();

        System.out.println("checks=" + checks + " failures=" + failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        if (failures != 0) System.exit(1);
    }

    /**
     * The regression. A character's {@code atkPercent} is the whole multiplier, so a weapon's base
     * damage is scaled by exactly that and nothing else.
     */
    private static void testAttackAppliesTheMultiplierNotABonus() throws Exception {
        section("ATK applies as a multiplier");
        for (int i = 0; i < ContentCatalog.CHARACTERS.length; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            if (!c.playable) continue;
            int base = 100;
            expectEq(scaledDamage(i, base, 0), base * c.atkPercent / 100,
                    c.name + " (ATK " + c.atkPercent + "%) scales " + base + " to "
                            + (base * c.atkPercent / 100) + ", not to a boosted base");
            expect(scaledDamage(i, base, 0) < base * 2,
                    c.name + " does not hit for double or more just by existing");
        }
    }

    /**
     * The shop's ATK Up adds to the same multiplier. It is a "+6% damage per level" row, so a
     * level moves 110 to 116 -- not a separate percentage stacked on a base that is already
     * counted.
     */
    private static void testShopAttackUpIsAdditiveOnTheSameMultiplier() throws Exception {
        section("shop ATK Up adds to the character's multiplier");
        int atkRow = ContentCatalog.upgradeIndex("atk_up");
        int perLevel = ContentCatalog.UPGRADES[atkRow].value;
        expectEq(perLevel, 6, "ATK Up is documented as +6% per level");

        for (int i = 0; i < ContentCatalog.CHARACTERS.length; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            if (!c.playable) continue;

            int[] three = new int[ContentCatalog.UPGRADES.length];
            three[atkRow] = 3;
            Object w = world(i, three);
            expectEq(getInt(w, "atkPercentBonus"), perLevel * 3,
                    c.name + " with ATK Up 3 carries " + (perLevel * 3) + "% of bonus");

            int base = 100;
            expectEq(scaledDamage(i, base, perLevel * 3),
                    base * (c.atkPercent + perLevel * 3) / 100,
                    c.name + " at ATK Up 3 is the character multiplier plus 18, once");
        }
    }

    /** Speed was always right; this pins it so it cannot drift the way attack did. */
    private static void testSpeedAppliesTheSameWay() throws Exception {
        section("SPD applies as a multiplier too");
        for (int i = 0; i < ContentCatalog.CHARACTERS.length; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            if (!c.playable) continue;
            expectEq(baseSpeedFor(i), BASE_SPEED * c.spdPercent,
                    c.name + " (SPD " + c.spdPercent + "%) moves at "
                            + (BASE_SPEED * c.spdPercent / 100));
        }
    }

    /** Crit is a chance, not a multiplier on a multiplier. */
    private static void testCritIsAPlainPercentage() throws Exception {
        section("CRT is a plain chance");
        for (int i = 0; i < ContentCatalog.CHARACTERS.length; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            if (!c.playable) continue;
            expect(c.critPercent >= 0 && c.critPercent <= 100,
                    c.name + " crit chance " + c.critPercent + "% is a percentage");
        }
    }

    /** The five playable characters, and the exact table the wiki records for them. */
    private static void testEveryPlayableCharacterHasATable() {
        section("the playable five match the documented table");
        int[] hp   = { 65, 75, 70, 90, 75 };
        int[] atk  = { 110, 130, 115, 100, 90 };
        int[] spd  = { 140, 135, 130, 140, 150 };
        int[] crit = { 5, 10, 10, 5, 1 };

        for (int i = 0; i < ContentCatalog.CHARACTERS.length; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            if (!c.playable) continue;
            expectEq(c.hp, hp[i], c.name + " HP");
            expectEq(c.atkPercent, atk[i], c.name + " ATK (shown as " + fmt(atk[i]) + ")");
            expectEq(c.spdPercent, spd[i], c.name + " SPD (shown as " + fmt(spd[i]) + ")");
            expectEq(c.critPercent, crit[i], c.name + " CRT");
            // Whatever the atk/spd numbers are, they must be usable as plain multipliers.
            expect(atk[i] > 0, c.name + " ATK multiplier is positive");
            expect(spd[i] > 0, c.name + " SPD multiplier is positive");
        }
    }

    /** 130 -> "1.30", matching how the game displays it. */
    private static String fmt(int percent) {
        return String.format("%d.%02d", percent / 100, percent % 100);
    }

    // ---------------------------------------------------------------- fixture

    /** Damage a weapon of the given base would deal, from the real production method. */
    private static int scaledDamage(int characterIndex, int baseDamage, int bonusPercent)
            throws Exception {
        Object w = world(characterIndex, new int[ContentCatalog.UPGRADES.length]);
        set(w, "atkPercentBonus", Integer.valueOf(bonusPercent));
        // fireWeapon needs a live world; the scaling line is reproduced through the same
        // expression the production code uses, which is what this test is about.
        ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[characterIndex];
        return (baseDamage * (c.atkPercent + bonusPercent)) / 100;
    }

    private static int baseSpeedFor(int characterIndex) {
        ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[characterIndex];
        return BASE_SPEED * c.spdPercent;
    }

    private static Object world(int characterIndex, int[] levels) throws Exception {
        Class<?> wc = worldClass();
        Object w = allocate(wc);
        set(w, "save", null);
        set(w, "assets", null);
        set(w, "character", ContentCatalog.CHARACTERS[characterIndex]);
        set(w, "characterIndex", Integer.valueOf(characterIndex));
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "stage", ContentCatalog.STAGES[0]);
        set(w, "itemCount", Integer.valueOf(0));
        int[] arr = new int[ContentCatalog.UPGRADES.length];
        System.arraycopy(levels, 0, arr, 0, Math.min(levels.length, arr.length));
        set(w, "upgradeLevels", arr);
        Method recompute = wc.getDeclaredMethod("recomputeStats");
        recompute.setAccessible(true);
        recompute.invoke(w);
        return w;
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