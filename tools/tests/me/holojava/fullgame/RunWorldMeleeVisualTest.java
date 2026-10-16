package me.holojava.fullgame;

/**
 * Deterministic visual contracts for melee effect rendering (no gameplay).
 *
 * <p>Proves, without a renderer:
 * <ul>
 *   <li>{@code fitWithin} caps the longest side at 64 px, preserves aspect
 *       within one pixel, and leaves small/empty frames untouched;</li>
 *   <li>effect anchors are front-sided and symmetric: right starts at the
 *       player center, left ends at it, vertical stays centered.</li>
 * </ul>
 *
 * <p>No floating overhead weapon exists: character frames hold their weapon
 * in hand and the melee effect depicts the swing.
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
        testBodyLunge();
        testMirrorPixels();
        testFacingSelection();

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

    private static void testBodyLunge() {
        System.out.println("[5] body lunges with the swing");
        expectEq(RunWorld.playerLungePx(false), 0, "rest body has no offset");
        expectEq(RunWorld.playerLungePx(true), 3, "attack shifts the body 3 px");
    }

    private static void testMirrorPixels() {
        System.out.println("[6] raw rows mirror horizontally");
        int[] src = {1, 2, 3, 4, 5, 6};
        int[] got = AssetLibrary.mirrorPixels(src, 3, 2);
        int[] want = {3, 2, 1, 6, 5, 4};
        expectEq(got.length, want.length, "mirrored row length kept");
        for (int i = 0; i < want.length; i++) {
            expectEq(got[i], want[i], "pixel " + i + " mirrored");
        }
        int[] single = {9};
        expectEq(AssetLibrary.mirrorPixels(single, 1, 1)[0], 9, "1px row is a fixed point");
    }

    private static void testFacingSelection() {
        System.out.println("[7] left facing picks the mirrored set");
        javax.microedition.lcdui.Image[] normal =
            new javax.microedition.lcdui.Image[1];
        javax.microedition.lcdui.Image[] mirrored =
            new javax.microedition.lcdui.Image[1];
        expect(RunWorld.facingFrames(normal, mirrored, 1) == normal, "right uses normal frames");
        expect(RunWorld.facingFrames(normal, mirrored, 0) == normal, "neutral uses normal frames");
        expect(RunWorld.facingFrames(normal, mirrored, -1) == mirrored, "left uses mirrored frames");
        expect(RunWorld.facingFrames(normal, null, -1) == normal, "left falls back without mirrors");
        expect(!RunWorld.facingLeft(0), "neutral is not left");
        expect(!RunWorld.facingLeft(1), "right is not left");
        expect(RunWorld.facingLeft(-1), "negative is left");
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
