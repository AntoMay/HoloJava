package me.holojava.fullgame;

/**
 * Deterministic visual contracts for melee effect rendering (no gameplay).
 *
 * <p>Proves, without a renderer:
 * <ul>
 *   <li>{@code fitWithin} caps the longest side at 64 px, preserves aspect
 *       within one pixel, and leaves small/empty frames untouched;</li>
 *   <li>effect anchors are front-sided and symmetric: right starts at the
 *       player center, left ends at it, vertical stays centered;</li>
 *   <li>the overhead weapon lunges only while a melee effect is active.</li>
 * </ul>
 *
 * <p>Hitbox, damage, cooldown, and durations are not touched by these
 * helpers; gameplay suites pin those separately.
 */
public final class RunWorldMeleeVisualTest {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) {
        testCapConstant();
        testFitWithinMeasuredFrames();
        testFitWithinLeavesSmallFrames();
        testAnchorSymmetry();
        testLunge();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    private static void testCapConstant() {
        System.out.println("[1] effect size cap");
        expectEq(AssetLibrary.MELEE_EFFECT_CAP_PX, 64, "longest-side ceiling is 64 px");
    }

    /** Measured source maxima map to the documented downscaled targets. */
    private static void testFitWithinMeasuredFrames() {
        System.out.println("[2] measured frames fit within the cap");
        expectSize(141, 26, 64, 11, "gura thrust");
        expectSize(181, 176, 64, 62, "calli slash");
        expectSize(182, 137, 64, 48, "kiara slash");
        expectSize(117, 50, 64, 27, "ina fx");
    }

    private static void expectSize(int sw, int sh, int dw, int dh, String label) {
        int[] got = AssetLibrary.fitWithin(sw, sh, AssetLibrary.MELEE_EFFECT_CAP_PX);
        expectEq(got[0], dw, label + " width");
        expectEq(got[1], dh, label + " height");
        expect(got[0] <= 64 && got[1] <= 64, label + " inside the cap");
        expect(got[0] == 64 || got[1] == 64, label + " fills the cap on one side");
    }

    private static void testFitWithinLeavesSmallFrames() {
        System.out.println("[3] small and empty frames pass through");
        int[] bullet = AssetLibrary.fitWithin(10, 8, AssetLibrary.MELEE_EFFECT_CAP_PX);
        expectEq(bullet[0], 10, "10px frame width kept");
        expectEq(bullet[1], 8, "8px frame height kept");
        int[] empty = AssetLibrary.fitWithin(1, 1, AssetLibrary.MELEE_EFFECT_CAP_PX);
        expectEq(empty[0], 1, "1x1 marker width kept");
        expectEq(empty[1], 1, "1x1 marker height kept");
        int[] exact = AssetLibrary.fitWithin(64, 40, AssetLibrary.MELEE_EFFECT_CAP_PX);
        expectEq(exact[0], 64, "at-cap width kept");
        expectEq(exact[1], 40, "at-cap height kept");
    }

    private static void testAnchorSymmetry() {
        System.out.println("[4] front anchors are symmetric");
        int cx = 100;
        int fw = 140;
        int right = RunWorld.meleeDrawX(cx, fw, true);
        int left = RunWorld.meleeDrawX(cx, fw, false);
        expectEq(right, 100, "right effect starts at the player center");
        expectEq(left, 100 - 140, "left effect ends at the player center");
        expectEq(right + left, 2 * cx - fw, "anchors mirror around the offset center");
        expectEq(RunWorld.meleeDrawY(80, 50), 55, "vertical stays centered");
        expectEq(RunWorld.meleeDrawX(cx, 1, false), 99, "degenerate 1px frame anchors sanely");
    }

    private static void testLunge() {
        System.out.println("[5] overhead weapon lunge follows effect state");
        expectEq(RunWorld.weaponLungePx(false), 0, "rest position has no offset");
        expectEq(RunWorld.weaponLungePx(true), 6, "active effect lunges 6 px");
    }

    // ---------------------------------------------------------------- helpers

    private static void expectEq(int got, int want, String label) {
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
