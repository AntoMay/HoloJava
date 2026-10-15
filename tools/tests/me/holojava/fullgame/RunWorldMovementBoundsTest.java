package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.microedition.lcdui.game.GameCanvas;

/**
 * Covers player movement, arena bounds, and their interaction with horizontal scrolling.
 *
 * <p>Parity with the reference JAR was established first, from the bytecode of
 * {@code RunWorld.updatePlayer}:
 * <ul>
 *   <li>Speed is {@code 120.0 * character.spdPercent / 100.0}, then multiplied by
 *       {@code 1.0 + speedPercentBonus / 100.0}.</li>
 *   <li>Input masks are LEFT 4, RIGHT 32, UP 2, DOWN 64 — exactly
 *       {@code javax.microedition.lcdui.game.GameCanvas}'s {@code *_PRESSED} constants.</li>
 *   <li>Diagonal movement normalises by {@code Math.sqrt(dx*dx + dy*dy)} and scales by
 *       {@code speed * 0.04}.</li>
 *   <li>Lower and left bounds clamp at 0; upper bounds clamp at 296 and 216, which are
 *       {@code 320 - 24} and {@code 240 - 24}. This build writes the same bounds as
 *       {@code worldWidth - 24} and {@code worldHeight - 24}, the deliberate viewport
 *       decoupling. At the reference's own 320x240 world the two are the same numbers.</li>
 * </ul>
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class RunWorldMovementBoundsTest {

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

    private static Viewport newViewport() {
        return (Viewport) allocate(Viewport.class);
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

    private static boolean getBool(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getBoolean(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static double getDouble(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getDouble(target);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + field, e);
        }
    }

    private static Class<?> worldClass() throws Exception {
        return Class.forName("me.holojava.fullgame.RunWorld");
    }

    private static final int PLAYER_SIZE = 24;

    /** A world on the given stage at the given viewport size, using production setViewport. */
    private static Object world(int stageIndex, int screenW, int screenH) throws Exception {
        Object w = allocate(worldClass());
        set(w, "stage", ContentCatalog.STAGES[stageIndex]);
        set(w, "mode", ContentCatalog.MODES[0]);
        set(w, "character", ContentCatalog.CHARACTERS[0]);
        set(w, "stageIndex", Integer.valueOf(stageIndex));
        set(w, "characterIndex", Integer.valueOf(0));
        set(w, "state", Integer.valueOf(0));
        set(w, "skillAcquired", Boolean.FALSE);
        set(w, "specialUsed", Boolean.FALSE);
        set(w, "skillReadyAt", Long.valueOf(0L));
        set(w, "slowEnemiesUntil", Long.valueOf(0L));
        set(w, "shield", Integer.valueOf(0));
        set(w, "playerHp", Integer.valueOf(100));
        set(w, "playerMaxHp", Integer.valueOf(100));
        set(w, "worldWidth", Integer.valueOf(320));
        set(w, "worldHeight", Integer.valueOf(240));

        Viewport viewport = newViewport();
        Method update = Viewport.class.getDeclaredMethod("update", int.class, int.class);
        update.setAccessible(true);
        update.invoke(viewport, Integer.valueOf(screenW), Integer.valueOf(screenH));
        Method setViewport = worldClass().getDeclaredMethod("setViewport", Viewport.class);
        setViewport.setAccessible(true);
        setViewport.invoke(w, viewport);
        return w;
    }

    private static void updatePlayer(Object w, int keys, long now) throws Exception {
        Method m = worldClass().getDeclaredMethod("updatePlayer", int.class, boolean.class,
                boolean.class, long.class);
        m.setAccessible(true);
        m.invoke(w, Integer.valueOf(keys), Boolean.FALSE, Boolean.FALSE, Long.valueOf(now));
    }

    private static int cameraX(Object w) throws Exception {
        Method m = worldClass().getDeclaredMethod("cameraX");
        m.setAccessible(true);
        return ((Integer) m.invoke(w)).intValue();
    }

    /** Walks right until the player stops moving, and returns where it ends up. */
    private static int walkToRightEdge(Object w) throws Exception {
        long now = 1000L;
        for (int i = 0; i < 4000; i++) {
            int before = getInt(w, "px");
            updatePlayer(w, GameCanvas.RIGHT_PRESSED, now);
            now += 40L;
            if (getInt(w, "px") == before && i > 5) break;
        }
        return getInt(w, "px");
    }

    private static int walkToLeftEdge(Object w) throws Exception {
        long now = 1000L;
        for (int i = 0; i < 4000; i++) {
            int before = getInt(w, "px");
            updatePlayer(w, GameCanvas.LEFT_PRESSED, now);
            now += 40L;
            if (getInt(w, "px") == before && i > 5) break;
        }
        return getInt(w, "px");
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testInputMasksMatchTheReference();
        testStraightAndDiagonalMovementMatchTheFormula();
        testNonScrollingStageClampsExactlyLikeTheReference();
        testPlayerCannotLeaveTheWorldBounds();
        testScrollingKeepsThePlayerOnScreenEverywhere();
        testCameraNeverRevealsPastTheWorld();
        testCameraDoesNotMoveGameplayCoordinates();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** The four direction bits must be the ones the reference reads. */
    private static void testInputMasksMatchTheReference() {
        System.out.println("[1] the input masks match the reference");
        expectEq(GameCanvas.LEFT_PRESSED, 4, "LEFT");
        expectEq(GameCanvas.RIGHT_PRESSED, 32, "RIGHT");
        expectEq(GameCanvas.UP_PRESSED, 2, "UP");
        expectEq(GameCanvas.DOWN_PRESSED, 64, "DOWN");
    }

    /**
     * Movement must be {@code (int)(dir / len * speed * 0.04)}. A diagonal frame is normalised,
     * so it covers less ground per axis than a straight one.
     */
    private static void testStraightAndDiagonalMovementMatchTheFormula() throws Exception {
        System.out.println("[2] straight and diagonal movement follow the formula");
        Object w = world(0, 320, 240);              // stage1, world 320 wide
        set(w, "px", Integer.valueOf(150));
        set(w, "py", Integer.valueOf(120));

        double speed = 120.0 * ContentCatalog.CHARACTERS[0].spdPercent / 100.0;
        double bonus = 1.0 + 0.0;
        int expectedStraight = (int) (speed * bonus * 0.04);
        expect(expectedStraight > 0, "a straight frame moves a visible distance");

        int before = getInt(w, "px");
        updatePlayer(w, GameCanvas.RIGHT_PRESSED, 1000L);
        expectEq(getInt(w, "px"), before + expectedStraight, "a straight step matches the formula");

        // Diagonal: normalised, so each axis advances by (int)(0.7071 * speed * 0.04).
        double diagonal = (1.0 / Math.sqrt(2.0)) * speed * bonus * 0.04;
        int expectedDiagonal = (int) diagonal;
        before = getInt(w, "px");
        int beforeY = getInt(w, "py");
        updatePlayer(w, GameCanvas.RIGHT_PRESSED | GameCanvas.DOWN_PRESSED, 2000L);
        expectEq(getInt(w, "px"), before + expectedDiagonal, "diagonal x matches the normalised step");
        expectEq(getInt(w, "py"), beforeY + expectedDiagonal, "diagonal y matches the normalised step");
        expect(getBool(w, "moving"), "the world reports the player as moving");
    }

    /**
     * Stage 1 does not scroll, so the world is the viewport and the bounds must land on the
     * reference's 296 and 216 exactly.
     */
    private static void testNonScrollingStageClampsExactlyLikeTheReference() throws Exception {
        System.out.println("[3] a non-scrolling stage clamps exactly like the reference");
        expectEq(ContentCatalog.stage(0).scrollW, 0, "stage1 does not scroll");

        Object w = world(0, 320, 240);
        expectEq(getInt(w, "worldWidth"), 320, "the world is the viewport width");
        expectEq(walkToRightEdge(w), 296, "the right bound is 296, as in the reference");
        expectEq(walkToLeftEdge(w), 0, "the left bound is 0");

        Object v = world(0, 320, 240);
        expectEq(walkToBottomEdge(v), 216, "the bottom bound is 216, as in the reference");
        expectEq(walkToTopEdge(v), 0, "the top bound is 0");
    }

    private static int walkToBottomEdge(Object w) throws Exception {
        long now = 1000L;
        for (int i = 0; i < 4000; i++) {
            int before = getInt(w, "py");
            updatePlayer(w, GameCanvas.DOWN_PRESSED, now);
            now += 40L;
            if (getInt(w, "py") == before && i > 5) break;
        }
        return getInt(w, "py");
    }

    private static int walkToTopEdge(Object w) throws Exception {
        long now = 1000L;
        for (int i = 0; i < 4000; i++) {
            int before = getInt(w, "py");
            updatePlayer(w, GameCanvas.UP_PRESSED, now);
            now += 40L;
            if (getInt(w, "py") == before && i > 5) break;
        }
        return getInt(w, "py");
    }

    /** The player is bounded by the world on both sides, whatever the world size. */
    private static void testPlayerCannotLeaveTheWorldBounds() throws Exception {
        System.out.println("[4] the player cannot leave the world bounds");
        int[][] cases = { { 0, 320, 240 }, { 1, 320, 240 }, { 1, 640, 240 }, { 5, 480, 320 } };
        for (int[] c : cases) {
            int stage = c[0], sw = c[1], sh = c[2];
            Object w = world(stage, sw, sh);
            int worldW = getInt(w, "worldWidth");
            int right = walkToRightEdge(w);
            int left = walkToLeftEdge(w);
            expectEq(right, worldW - PLAYER_SIZE,
                "stage " + stage + " @ " + sw + "x" + sh + " right bound is worldWidth - 24");
            expectEq(left, 0, "stage " + stage + " left bound is 0");
        }
    }

    /**
     * The point of the scrolling stage: the player may walk to world coordinates beyond the
     * viewport, but must never be drawn outside it, because the camera follows and is clamped.
     */
    private static void testScrollingKeepsThePlayerOnScreenEverywhere() throws Exception {
        System.out.println("[5] a scrolling stage keeps the player on screen");
        int stage = 1;                               // Holo Office: the scrolling stage
        expect(ContentCatalog.stage(stage).scrollW > 0, "stage2 scrolls");

        Object w = world(stage, 320, 240);
        int viewW = getInt(w, "viewWidth");
        int worldW = getInt(w, "worldWidth");
        expect(worldW > viewW, "the world is wider than the view");

        // Sweep every position the player can actually occupy. The upper bound is worldWidth - 24
        // because updatePlayer clamps there every frame, so anything beyond it is unreachable
        // rather than merely untested; test 4 proves that clamp by walking there for real.
        int offscreen = 0;
        int worst = -1;
        for (int px = 0; px <= worldW - PLAYER_SIZE; px += 1) {
            set(w, "px", Integer.valueOf(px));
            int cam = cameraX(w);
            int screenX = px - cam;
            if (screenX < 0 || screenX >= viewW) {
                offscreen++;
                worst = px;
            }
        }
        expectEq(offscreen, 0,
            "no reachable position puts the player outside the viewport (world " + worldW
                + ", view " + viewW + ", last offender " + worst + ")");

        // And the same after walking there for real.
        int end = walkToRightEdge(w);
        int cam = cameraX(w);
        int screenX = end - cam;
        expect(screenX >= 0 && screenX < viewW,
            "walking to the far edge leaves the player on screen (x=" + screenX + ")");
    }

    /** The camera must never scroll past the world or before its start. */
    private static void testCameraNeverRevealsPastTheWorld() throws Exception {
        System.out.println("[6] the camera never reveals past the world");
        int stage = 1;
        Object w = world(stage, 320, 240);
        int viewW = getInt(w, "viewWidth");
        int worldW = getInt(w, "worldWidth");

        int bad = 0;
        for (int px = 0; px <= worldW - PLAYER_SIZE; px += 1) {
            set(w, "px", Integer.valueOf(px));
            int cam = cameraX(w);
            if (cam < 0 || cam > worldW - viewW) bad++;
        }
        expectEq(bad, 0, "camera stays within [0, worldWidth - viewWidth] at every reachable position");

        set(w, "px", Integer.valueOf(0));
        expectEq(cameraX(w), 0, "the camera rests at the world start");
        set(w, "px", Integer.valueOf(worldW));
        expectEq(cameraX(w), worldW - viewW, "and stops at the world end");

        // A non-scrolling stage has no travel at all.
        Object flat = world(0, 320, 240);
        set(flat, "px", Integer.valueOf(160));
        expectEq(cameraX(flat), 0, "a non-scrolling stage never moves the camera");
    }

    /**
     * Scrolling is a rendering concern. Moving the camera must not move the player, and gameplay
     * coordinates are read straight from the world, never from the camera.
     */
    private static void testCameraDoesNotMoveGameplayCoordinates() throws Exception {
        System.out.println("[7] scrolling does not disturb gameplay coordinates");
        Object w = world(1, 320, 240);
        set(w, "px", Integer.valueOf(500));

        int pxBefore = getInt(w, "px");
        int pyBefore = getInt(w, "py");
        int camBefore = cameraX(w);
        expect(camBefore > 0, "a player far right pushes the camera");

        // Several idle frames with no input: the camera is derived, never stateful.
        for (int i = 0; i < 5; i++) updatePlayer(w, 0, 2000L + i * 40L);
        expectEq(getInt(w, "px"), pxBefore, "the player did not move without input");
        expectEq(getInt(w, "py"), pyBefore, "and neither did its vertical position");
        expectEq(cameraX(w), camBefore, "the camera is a pure function of the player position");
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