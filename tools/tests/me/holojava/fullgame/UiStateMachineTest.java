package me.holojava.fullgame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Covers the UI state machine in {@link FullGameCanvas}: every screen transition, per-screen input,
 * index bounds, and input latches.
 *
 * <p>No production change was needed to reach this. Each per-screen handler already takes its input
 * as explicit booleans — {@code updateMain(up, down, fire)}, {@code updateCharacters(...)} and so
 * on — so the tests drive the genuine production methods directly. The only canvas method that
 * cannot run headlessly is {@code update(long)}, because it calls the MIDP {@code getKeyStates()}
 * first; the dispatch in it is a plain {@code if / else if} chain over {@code screen} and is
 * covered by calling the handlers the same way.
 *
 * <p>Instances are allocated without running the constructor and written through
 * {@code sun.misc.Unsafe} by real field type. Test-only: nothing under {@code src/} uses
 * {@code sun.*}.
 */
public final class UiStateMachineTest {

    private static int checks = 0;
    private static int failures = 0;

    // Screen constants, mirroring FullGameCanvas.
    private static final int MENU = 0;
    private static final int CHOOSE_IDOL = 1;
    private static final int CHOOSE_MODE = 2;
    private static final int STAGES = 3;
    private static final int SETTINGS = 4;
    private static final int PREP = 5;
    private static final int GAME = 6;
    private static final int RESULTS = 7;
    private static final int CONTROLS = 8;
    private static final int SPLASH = 9;
    private static final int SHOP = 10;
    private static final int UPGRADES = 11;
    private static final int ARMORY = 12;
    private static final int LOCKED = 13;
    private static final int CREDITS = 14;
    private static final int SHOPTABS = 15;

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

    /** Reads FullGameCanvas.save.audioEnabled() through the same reflection seam as getInt. */
    private static boolean audioEnabled(Object target) {
        try {
            Field f = target.getClass().getDeclaredField("save");
            f.setAccessible(true);
            SaveStore save = (SaveStore) f.get(target);
            return save == null || save.audioEnabled();
        } catch (Exception e) {
            throw new IllegalStateException("cannot read audio preference", e);
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

    private static Class<?> canvasClass() throws Exception {
        return Class.forName("me.holojava.fullgame.FullGameCanvas");
    }

    private static final class Fake implements SaveStore.RecordStorage {
        byte[] record = new byte[56];

        public byte[] read(int recordId) {
            return record.clone();
        }

        public boolean write(int recordId, byte[] payload) {
            record = payload.clone();
            return true;
        }
    }

    private static Object canvas(int screen, int menuIndex) throws Exception {
        Object c = allocate(canvasClass());
        set(c, "save", new SaveStore(new Fake()));
        set(c, "assets", null);
        set(c, "shutdownRequest", null);
        set(c, "world", null);
        set(c, "screen", Integer.valueOf(screen));
        set(c, "menuIndex", Integer.valueOf(menuIndex));
        set(c, "modeIndex", Integer.valueOf(0));
        set(c, "characterIndex", Integer.valueOf(0));
        set(c, "stageIndex", Integer.valueOf(0));
        set(c, "resultIndex", Integer.valueOf(0));
        set(c, "pauseIndex", Integer.valueOf(0));
        set(c, "characterPage", Integer.valueOf(0));
        set(c, "worldWasVictory", Boolean.FALSE);
        set(c, "exitRequested", Boolean.FALSE);
        set(c, "stopped", Boolean.FALSE);
        return c;
    }

    private static void call(Object c, String name, Class<?>[] types, Object[] args)
            throws Exception {
        Method m = canvasClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        m.invoke(c, args);
    }

    private static void mainMenu(Object c, boolean up, boolean down, boolean fire) throws Exception {
        call(c, "updateMain", new Class<?>[] { boolean.class, boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire) });
    }

    private static void modes(Object c, boolean up, boolean down, boolean fire, boolean back)
            throws Exception {
        call(c, "updateModes",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(back) });
    }

    private static void chooseIdol(Object c, boolean up, boolean down, boolean fire, boolean left,
            boolean right) throws Exception {
        call(c, "updateChooseIdol",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class,
                        boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(left), Boolean.valueOf(right) });
    }

    private static void chooseMode(Object c, boolean up, boolean down, boolean fire, boolean left,
            boolean right) throws Exception {
        call(c, "updateChooseMode",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class,
                        boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(left), Boolean.valueOf(right) });
    }

    private static void characters(Object c, boolean up, boolean down, boolean fire, boolean left,
            boolean right) throws Exception {
        call(c, "updateCharacters",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class,
                        boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(left), Boolean.valueOf(right) });
    }

    private static void stages(Object c, boolean up, boolean down, boolean fire, boolean back)
            throws Exception {
        call(c, "updateStages",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(back) });
    }

    private static void settings(Object c, boolean up, boolean down, boolean fire, boolean back)
            throws Exception {
        call(c, "updateSettings",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(back) });
    }

    private static void prep(Object c, boolean up, boolean down, boolean fire, boolean back)
            throws Exception {
        call(c, "updatePrep",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(back) });
    }

    private static void results(Object c, boolean up, boolean down, boolean fire, boolean back)
            throws Exception {
        call(c, "updateResults",
                new Class<?>[] { boolean.class, boolean.class, boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(back) });
    }

    private static void pause(Object c, boolean up, boolean down, boolean fire) throws Exception {
        call(c, "updatePause",
                new Class<?>[] { boolean.class, boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire) });
    }

    /** Drives one of the five-boolean submenu handlers with named arguments. */
    private static void drive(Object c, String name, boolean up, boolean down, boolean fire,
            boolean left, boolean right) throws Exception {
        call(c, name, new Class<?>[] { boolean.class, boolean.class, boolean.class,
                boolean.class, boolean.class },
                new Object[] { Boolean.valueOf(up), Boolean.valueOf(down), Boolean.valueOf(fire),
                        Boolean.valueOf(left), Boolean.valueOf(right) });
    }

    private static void shop(Object c, boolean up, boolean down, boolean fire, boolean left,
            boolean right) throws Exception {
        drive(c, "updateShop", up, down, fire, left, right);
    }

    private static void upgrades(Object c, boolean up, boolean down, boolean fire, boolean left,
            boolean right) throws Exception {
        drive(c, "updateUpgrades", up, down, fire, left, right);
    }

    private static void armory(Object c, boolean up, boolean down, boolean fire, boolean left,
            boolean right) throws Exception {
        drive(c, "updateArmory", up, down, fire, left, right);
    }

    private static void oneBool(Object c, String name, boolean back) throws Exception {
        call(c, name, new Class<?>[] { boolean.class }, new Object[] { Boolean.valueOf(back) });
    }

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testSplashAdvancesToMenu();
        testMainMenuRoutesToEveryScreen();
        testMainMenuIndexWrapsAtBothEnds();
        testExitItemRequestsShutdown();
        testModesScreenRoutesAndBacks();
        testChooseIdolSelectsAndPersists();
        testChooseIdolIndexWraps();
        testStageScreenRefusesLockedStages();
        testStageScreenBackRestoresCharacter();
        testSettingsTogglesAndRoutes();
        testControlsScreenReturnsToSettings();
        testPrepBackRestoresStageSelection();
        testResultsScreenExitsAndBounds();
        testStartRunRefusesALockedStage();
        testLatchesAreClearedAfterLeavingGameplay();
        testIrrelevantInputDoesNothing();
        testPauseIndexWraps();
        testShopSubmenuRoutes();
        testLockedAndCreditsPagesBackOut();
        testPlayFlowWalksDownAndBack();
        testUpgradeTabsCycleAndBackLeaves();

        System.out.println("checks=" + checks + " failures=" + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** SPLASH confirms straight to the main menu and clears the key state it consumed. */
    private static void testSplashAdvancesToMenu() throws Exception {
        System.out.println("[1] splash advances to the menu");
        Object c = canvas(SPLASH, 0);
        call(c, "updateSplash", new Class<?>[] { boolean.class, int.class },
                new Object[] { Boolean.FALSE, Integer.valueOf(0) });
        expectEq(getInt(c, "screen"), SPLASH, "no input leaves the splash up");

        call(c, "updateSplash", new Class<?>[] { boolean.class, int.class },
                new Object[] { Boolean.TRUE, Integer.valueOf(0) });
        expectEq(getInt(c, "screen"), MENU, "confirming advances to the main menu");
        expectEq(getInt(c, "menuIndex"), 0, "and the selection starts at the top");
    }

    /**
     * Each navigable menu entry routes: Papan Peringkat, Pencapaian and Holo House open the
     * placeholder page (with their own titles), Toko opens the shop submenu, Main starts the
     * Main to Mode to Karakter to Stage flow, Pengaturan opens settings and Credits the credits.
     * The eighth entry, Keluar, ends the game instead of routing — covered by test [4].
     */
    private static void testMainMenuRoutesToEveryScreen() throws Exception {
        System.out.println("[2] the main menu routes to every screen");
        int[][] routes = { { 0, LOCKED }, { 1, LOCKED }, { 2, SHOP }, { 3, CHOOSE_IDOL },
                { 4, LOCKED }, { 5, SETTINGS }, { 6, CREDITS } };
        for (int[] r : routes) {
            Object c = canvas(MENU, r[0]);
            mainMenu(c, false, false, true);
            expectEq(getInt(c, "screen"), r[1],
                "menu entry " + r[0] + " routes to screen " + r[1]);
        }
        // Each shut entry carries its own title onto the placeholder page.
        int[] rows = { 0, 1, 4 };
        String[] titles = { "PAPAN PERINGKAT", "PENCAPAIAN", "HOLO HOUSE" };
        Field lockedTitle = canvasClass().getDeclaredField("lockedTitle");
        lockedTitle.setAccessible(true);
        for (int i = 0; i < rows.length; i++) {
            Object c = canvas(MENU, rows[i]);
            mainMenu(c, false, false, true);
            expectEq((String) lockedTitle.get(c), titles[i],
                "menu entry " + rows[i] + " opens its own placeholder title");
        }
    }

    /** Selection wraps in both directions and can never leave the eight-entry range. */
    private static void testMainMenuIndexWrapsAtBothEnds() throws Exception {
        System.out.println("[3] the menu index wraps at both ends");
        Object c = canvas(MENU, 0);
        mainMenu(c, false, false, false);
        expectEq(getInt(c, "menuIndex"), 0, "the index is unchanged without input");

        mainMenu(c, true, false, false);                 // up from 0 wraps to 7
        expectEq(getInt(c, "menuIndex"), 7, "up from the top wraps to the bottom");
        mainMenu(c, false, true, false);                 // down from 7 wraps to 0
        expectEq(getInt(c, "menuIndex"), 0, "down from the bottom wraps to the top");

        // Walk far past both ends and confirm the index is always in range.
        Object walker = canvas(MENU, 0);
        for (int i = 0; i < 40; i++) {
            mainMenu(walker, false, true, false);
            int idx = getInt(walker, "menuIndex");
            expect(idx >= 0 && idx < 8, "index " + idx + " stayed within [0,8)");
        }
    }

    /**
     * Exit lives in two places now: row 7 of the menu bar (Keluar) and row 2 of Pengaturan both
     * set the shutdown flags, while plain navigation never terminates the game from under it.
     */
    private static void testExitItemRequestsShutdown() throws Exception {
        System.out.println("[4] Exit requests shutdown from settings and from the menu");
        Object menuEntry = canvas(MENU, 6);
        mainMenu(menuEntry, false, false, true);
        expectEq(getInt(menuEntry, "screen"), CREDITS, "row 6 is still Credits");
        expect(!getBool(menuEntry, "stopped"), "the menu itself never stops the run loop");

        // Row 7 is Keluar: same shutdown path as the settings Exit row.
        Object menuExit = canvas(MENU, 7);
        ShutdownStub menuStub = new ShutdownStub();
        set(menuExit, "shutdownRequest", menuStub);
        mainMenu(menuExit, false, false, true);
        expect(getBool(menuExit, "stopped"), "Keluar told the run loop to stop");
        expect(getBool(menuExit, "exitRequested"), "and an exit was recorded");
        expect(menuStub.requested, "and the MIDlet was asked to shut down");

        Object c = canvas(SETTINGS, 2);
        // A null request would throw, so give the canvas a working stub instead.
        ShutdownStub stub = new ShutdownStub();
        set(c, "shutdownRequest", stub);
        settings(c, false, false, true, false);
        expect(getBool(c, "stopped"), "the run loop was told to stop");
        expect(getBool(c, "exitRequested"), "and an exit was recorded");
        expect(stub.requested, "and the MIDlet was asked to shut down");
    }

    /** Minimal stand-in for the MIDlet-side shutdown hook; production only calls requestShutdown(). */
    private static final class ShutdownStub implements FullGameCanvas.ShutdownRequest {
        boolean requested;

        public void requestShutdown() {
            requested = true;
        }
    }

    private static void testModesScreenRoutesAndBacks() throws Exception {
        System.out.println("[5] the mode screen routes and backs");
        // Old MODES screen is now CHOOSE_MODE (reached via CHOOSE_IDOL)
        Object back = canvas(CHOOSE_MODE, 0);
        chooseMode(back, false, false, false, true, false);
        expectEq(getInt(back, "screen"), CHOOSE_IDOL, "back returns to Choose Idol");
        expectEq(getInt(back, "menuIndex"), 0, "and lands on first character");

        Object pick = canvas(CHOOSE_MODE, 0);
        chooseMode(pick, false, false, true, false, false);
        expectEq(getInt(pick, "screen"), STAGES, "choosing Stage Mode continues to Stage Select");
        expectEq(getInt(pick, "modeIndex"), 0, "and records the chosen mode");

        // The index is bounded to the one entry this build exposes.
        Object walk = canvas(CHOOSE_MODE, 0);
        for (int i = 0; i < 20; i++) chooseMode(walk, false, true, false, false, false);
        int idx = getInt(walk, "menuIndex");
        expect(idx == 0, "the mode index stayed at 0, saw " + idx);
    }

    /**
     * Choose Idol is the first step: picking a character records it, persists it and
     * continues to Choose Mode.
     */
    private static void testChooseIdolSelectsAndPersists() throws Exception {
        System.out.println("[6] choosing an idol records, persists and continues to choose mode");
        Object c = canvas(CHOOSE_IDOL, 2);
        chooseIdol(c, false, false, true, false, false);
        expectEq(getInt(c, "characterIndex"), 2, "the selection became the character index");
        expectEq(getInt(c, "screen"), CHOOSE_MODE, "and the flow continues to choose mode");
        expectEq(getInt(c, "menuIndex"), 0, "with the mode cursor on Stage Mode");

        Field save = canvasClass().getDeclaredField("save");
        save.setAccessible(true);
        SaveStore s = (SaveStore) save.get(c);
        expectEq(s.selectedCharacter(), 2, "the choice was persisted for the next session");

        // L/R navigation wraps
        Object nav = canvas(CHOOSE_IDOL, 1);
        for (int i = 0; i < 4; i++) chooseIdol(nav, false, false, false, false, true);
        expectEq(getInt(nav, "menuIndex"), 0, "RIGHT wraps around the 5 characters");
        // LEFT at index 0 goes back to MENU (not tested here)
    }

    /** L/R wraps over the 5 character grid. */
    private static void testChooseIdolIndexWraps() throws Exception {
        System.out.println("[7] the choose idol index wraps");
        int count = ContentCatalog.CHARACTERS.length; // 5
        // Test RIGHT wrapping: start at 4, press RIGHT (count+5) times
        Object c = canvas(CHOOSE_IDOL, 4);
        for (int i = 0; i < count + 5; i++) {
            chooseIdol(c, false, false, false, false, true); // right
            int idx = getInt(c, "menuIndex");
            expect(idx >= 0 && idx < count, "index " + idx + " stayed within [0," + count + ")");
        }
        // LEFT at index > 0 goes to previous index; LEFT at 0 goes back to MENU (not tested here)
    }

    /** A locked stage is refused outright: no selection, no transition. */
    private static void testStageScreenRefusesLockedStages() throws Exception {
        System.out.println("[9] the stage screen refuses locked stages");
        int[] stages = selectable();
        // stage2 is gated on order 0, so a fresh save cannot enter it.
        Object fresh = canvas(STAGES, 1);
        set(fresh, "stageIndex", Integer.valueOf(0));
        stages(fresh, false, false, true, false);
        expectEq(getInt(fresh, "screen"), STAGES, "a locked stage does not start a run");
        expectEq(getInt(fresh, "stageIndex"), 0, "and the stage index is untouched");

        Object open = canvas(STAGES, 0);
        set(open, "stageIndex", Integer.valueOf(0));
        stages(open, false, false, true, false);
        expectEq(getInt(open, "screen"), PREP, "the unlocked first stage advances to prep");
        expectEq(getInt(open, "stageIndex"), stages[0], "and records the chosen stage");
    }

    private static int[] selectable() throws Exception {
        Method m = canvasClass().getDeclaredMethod("selectableStages");
        m.setAccessible(true);
        return (int[]) m.invoke(null);
    }

    private static void testStageScreenBackRestoresCharacter() throws Exception {
        System.out.println("[10] the stage screen backs to choose mode");
        Object c = canvas(STAGES, 0);
        set(c, "characterIndex", Integer.valueOf(3));
        stages(c, false, false, false, true);
        expectEq(getInt(c, "screen"), CHOOSE_MODE, "back returns to choose mode");
        expectEq(getInt(c, "menuIndex"), 0, "cursor on Stage Mode");
    }

    private static void testSettingsTogglesAndRoutes() throws Exception {
        System.out.println("[11] settings routes controls, exit and back");
        // Audio is row 0: it stays on the settings page but flips the saved preference.
        Object audio = canvas(SETTINGS, 0);
        boolean before = audioEnabled(audio);
        settings(audio, false, false, true, false);
        expectEq(getInt(audio, "screen"), SETTINGS, "the audio row stays on the settings page");
        expect(audioEnabled(audio) != before, "the audio row toggles the saved preference");
        settings(audio, false, false, true, false);
        expect(audioEnabled(audio) == before, "pressing the audio row again toggles it back");

        Object controls = canvas(SETTINGS, 1);
        settings(controls, false, false, true, false);
        expectEq(getInt(controls, "screen"), CONTROLS, "the controls entry opens the controls page");

        // Hardcore left this page (its shop row toggles the same flag); row 2 is Exit, row 3 Back.
        Object back = canvas(SETTINGS, 3);
        settings(back, false, false, false, true);
        expectEq(getInt(back, "screen"), MENU, "back returns to the menu");
        expectEq(getInt(back, "menuIndex"), 5, "with the cursor on Pengaturan");

        Object backRow = canvas(SETTINGS, 3);
        settings(backRow, false, false, true, false);
        expectEq(getInt(backRow, "screen"), MENU, "the trailing Back row returns to the menu");
        expectEq(getInt(backRow, "menuIndex"), 5, "with the cursor on Pengaturan");

        // The cursor stays inside the four rows no matter how far it walks.
        Object walk = canvas(SETTINGS, 0);
        for (int i = 0; i < 20; i++) settings(walk, false, true, false, false);
        int idx = getInt(walk, "menuIndex");
        expect(idx >= 0 && idx < 4, "the settings index stayed within [0,4), saw " + idx);
    }

    private static void testControlsScreenReturnsToSettings() throws Exception {
        System.out.println("[12] the controls page returns to settings");
        String[] kinds = { "left", "right", "fire" };
        for (int k = 0; k < 3; k++) {
            Object c = canvas(CONTROLS, 0);
            call(c, "handleControlsInput", new Class<?>[] { boolean.class, boolean.class },
                    new Object[] { Boolean.valueOf(k == 0 || k == 1),
                            Boolean.valueOf(k == 2) });
            expectEq(getInt(c, "screen"), SETTINGS,
                kinds[k] + " on the controls page returned to settings");
        }
        // Nothing pressed must leave the page up.
        Object idle = canvas(CONTROLS, 0);
        call(idle, "handleControlsInput", new Class<?>[] { boolean.class, boolean.class },
                new Object[] { Boolean.FALSE, Boolean.FALSE });
        expectEq(getInt(idle, "screen"), CONTROLS, "no input left the controls page alone");
    }

    /** Leaving prep restores the stage cursor rather than dropping the player at the top. */
    private static void testPrepBackRestoresStageSelection() throws Exception {
        System.out.println("[13] leaving prep restores the stage cursor");
        int[] sel = selectable();
        Object c = canvas(PREP, 0);
        set(c, "stageIndex", Integer.valueOf(sel[Math.min(1, sel.length - 1)]));
        prep(c, false, false, false, true);
        expectEq(getInt(c, "screen"), STAGES, "back returns to stage select");
        int expectedSlot = sel.length > 1 ? 1 : 0;
        expectEq(getInt(c, "menuIndex"), expectedSlot,
            "with the cursor on the stage that was being prepared");
    }

    /** Results offers exactly two choices and both bound the cursor. */
    private static void testResultsScreenExitsAndBounds() throws Exception {
        System.out.println("[14] the results screen exits and bounds its cursor");
        Object c = canvas(RESULTS, 0);
        results(c, false, true, false, false);
        expectEq(getInt(c, "resultIndex"), 1, "down moves the results cursor");
        results(c, false, true, false, false);
        expectEq(getInt(c, "resultIndex"), 0, "and it wraps back to the top");

        Object quit = canvas(RESULTS, 0);
        set(quit, "resultIndex", Integer.valueOf(1));
        results(quit, false, false, false, true);
        expectEq(getInt(quit, "screen"), MENU, "back returns to the main menu");

        Object quitByChoice = canvas(RESULTS, 0);
        set(quitByChoice, "resultIndex", Integer.valueOf(1));
        results(quitByChoice, false, false, true, false);
        expectEq(getInt(quitByChoice, "screen"), MENU,
            "choosing the second entry also returns to the menu");

        // The first entry restarts, which calls startRun and constructs a RunWorld. That needs a
        // real AssetLibrary and cannot run headlessly; its refusal path is covered by test 15 and
        // the restart itself by the build on device.
    }

    /** startRun must refuse a locked stage instead of beginning an unplayable run. */
    private static void testStartRunRefusesALockedStage() throws Exception {
        System.out.println("[15] starting a run refuses a locked stage");
        Object c = canvas(STAGES, 0);
        set(c, "stageIndex", Integer.valueOf(1));     // stage2, gated on order 0
        call(c, "startRun", new Class<?>[0], new Object[0]);
        expectEq(getInt(c, "screen"), STAGES, "the flow fell back to stage select");
        Field world = canvasClass().getDeclaredField("world");
        world.setAccessible(true);
        expect(world.get(c) == null, "and no run was created");
    }

    /** Latches dropped on leaving gameplay so a held key cannot leak into the next session. */
    private static void testLatchesAreClearedAfterLeavingGameplay() throws Exception {
        System.out.println("[16] input latches are cleared leaving gameplay");
        Object c = canvas(GAME, 0);
        set(c, "specialPressed", Boolean.TRUE);
        set(c, "attackPulse", Boolean.TRUE);
        set(c, "pausePressed", Boolean.TRUE);
        set(c, "leftWasDown", Boolean.TRUE);

        call(c, "clearInputLatches", new Class<?>[] { int.class },
                new Object[] { Integer.valueOf(0) });
        expect(!getBool(c, "specialPressed"), "the special latch was dropped");
        expect(!getBool(c, "attackPulse"), "the attack pulse was dropped");
        expect(!getBool(c, "pausePressed"), "the pause latch was dropped");
        expect(!getBool(c, "leftWasDown"), "the held-key latch was rebased to not-held");
    }

    private static void testPauseIndexWraps() throws Exception {
        System.out.println("[17b] the pause index wraps over its two entries");
        Object c = canvas(GAME, 0);
        pause(c, false, true, false);
        expectEq(getInt(c, "pauseIndex"), 1, "down moves the pause cursor");
        pause(c, false, true, false);
        expectEq(getInt(c, "pauseIndex"), 0, "and it wraps back to Resume");
        pause(c, true, false, false);
        expectEq(getInt(c, "pauseIndex"), 1, "up from the top wraps to the bottom");
        for (int i = 0; i < 12; i++) pause(c, false, true, false);
        int idx = getInt(c, "pauseIndex");
        expect(idx >= 0 && idx < 2, "the pause index stayed within [0,2), saw " + idx);
    }

    /** A handler ignores input flags that do not belong to its screen. */
    private static void testIrrelevantInputDoesNothing() throws Exception {
        System.out.println("[17] irrelevant input changes nothing");
        Object c = canvas(MENU, 3);
        mainMenu(c, false, false, false);           // no fire: nothing should happen
        expectEq(getInt(c, "screen"), MENU, "no confirm leaves the menu");
        expectEq(getInt(c, "menuIndex"), 3, "and the cursor stays put");

        // fire with no direction must not move the cursor either
        Object hold = canvas(MENU, 2);
        mainMenu(hold, true, true, false);
        expectEq(getInt(hold, "menuIndex"), 1,
            "simultaneous up and down resolve to one step, not two");
    }

    // ---------------------------------------------------------------- new screens

    /** The Toko submenu: Beli Upgrade, Armory, Keluar, plus L/R back to the menu — and the
     *  tab submenu Beli Upgrade opens, which is now the only way onto the purchase page. */
    private static void testShopSubmenuRoutes() throws Exception {
        System.out.println("[18] the Toko submenu routes its three rows and the tab submenu");
        Object buy = canvas(SHOP, 0);
        shop(buy, false, false, true, false, false);
        expectEq(getInt(buy, "screen"), SHOPTABS, "Beli Upgrade opens the tab submenu");
        expectEq(getInt(buy, "menuIndex"), 0, "with the cursor on the tab last used");

        // The remembered tab comes back as the cursor, so L/R out and in restores position.
        Object buyUsed = canvas(SHOP, 0);
        set(buyUsed, "shopTab", Integer.valueOf(2));
        shop(buyUsed, false, false, true, false, false);
        expectEq(getInt(buyUsed, "screen"), SHOPTABS, "Beli Upgrade still opens the tab submenu");
        expectEq(getInt(buyUsed, "menuIndex"), 2, "with the cursor on the last used tab (Lainnya)");

        Object armoryRow = canvas(SHOP, 1);
        shop(armoryRow, false, false, true, false, false);
        expectEq(getInt(armoryRow, "screen"), ARMORY, "Armory opens the catalogue");

        Object leave = canvas(SHOP, 2);
        shop(leave, false, false, true, false, false);
        expectEq(getInt(leave, "screen"), MENU, "Keluar returns to the menu");
        expectEq(getInt(leave, "menuIndex"), 2, "with the cursor back on Toko");

        Object lefted = canvas(SHOP, 1);
        shop(lefted, false, false, false, true, false);
        expectEq(getInt(lefted, "screen"), MENU, "Left also leaves the shop");
        expectEq(getInt(lefted, "menuIndex"), 2, "with the cursor back on Toko");

        Object walked = canvas(SHOP, 0);
        for (int i = 0; i < 10; i++) shop(walked, false, true, false, false, false);
        int idx = getInt(walked, "menuIndex");
        expect(idx >= 0 && idx < 3, "the shop index stayed within [0,3), saw " + idx);

        // The tab submenu: a tab pick opens the purchase page on it, L/R and Kembali leave.
        Object tab0 = canvas(SHOPTABS, 0);
        drive(tab0, "updateShopTabs", false, false, true, false, false);
        expectEq(getInt(tab0, "screen"), UPGRADES, "picking Ability opens the purchase page");
        expectEq(getInt(tab0, "shopTab"), 0, "on the Ability tab");
        expectEq(getInt(tab0, "menuIndex"), 0, "with the cursor at the top of the tab");

        Object tab1 = canvas(SHOPTABS, 1);
        drive(tab1, "updateShopTabs", false, false, true, false, false);
        expectEq(getInt(tab1, "shopTab"), 1, "picking Stat opens the Stat tab");

        Object tabLeft = canvas(SHOPTABS, 1);
        drive(tabLeft, "updateShopTabs", false, false, false, true, false);
        expectEq(getInt(tabLeft, "screen"), SHOP, "Left steps back out to the Toko submenu");
        expectEq(getInt(tabLeft, "menuIndex"), 0, "on the Beli Upgrade row");

        Object tabBack = canvas(SHOPTABS, 3);
        drive(tabBack, "updateShopTabs", false, false, true, false, false);
        expectEq(getInt(tabBack, "screen"), SHOP, "the trailing Kembali row returns to Toko");
        expectEq(getInt(tabBack, "menuIndex"), 0, "on the Beli Upgrade row");

        Object tabWalk = canvas(SHOPTABS, 0);
        for (int i = 0; i < 10; i++) drive(tabWalk, "updateShopTabs", false, true, false, false, false);
        int tabIdx = getInt(tabWalk, "menuIndex");
        expect(tabIdx >= 0 && tabIdx < 4, "the tab index stayed within [0,4), saw " + tabIdx);
    }

    /** The three shut entries and Credits render as screens but back out on any confirm. */
    private static void testLockedAndCreditsPagesBackOut() throws Exception {
        System.out.println("[19] placeholder and credits pages back out on any confirm");
        Object idle = canvas(LOCKED, 0);
        oneBool(idle, "updateLocked", false);
        expectEq(getInt(idle, "screen"), LOCKED, "no input leaves the placeholder up");

        Object c = canvas(LOCKED, 0);
        oneBool(c, "updateLocked", true);
        expectEq(getInt(c, "screen"), MENU, "the placeholder page returns to the menu");
        expectEq(getInt(c, "menuIndex"), 0, "with the cursor still on the shut entry");

        Object house = canvas(LOCKED, 4);
        oneBool(house, "updateLocked", true);
        expectEq(getInt(house, "screen"), MENU, "Holo House backs out the same way");
        expectEq(getInt(house, "menuIndex"), 4, "with the cursor still on Holo House");

        Object d = canvas(CREDITS, 6);
        oneBool(d, "updateCredits", false);
        expectEq(getInt(d, "screen"), CREDITS, "no input leaves the credits up");
        oneBool(d, "updateCredits", true);
        expectEq(getInt(d, "screen"), MENU, "the credits page returns to the menu");
        expectEq(getInt(d, "menuIndex"), 6, "with the cursor still on Credits");
    }

    /** The whole start-up flow in order: Main -> Mode -> Karakter -> Stage, and back up again. */
    private static void testPlayFlowWalksDownAndBack() throws Exception {
        System.out.println("[20] the play flow walks Main -> Choose Idol -> Choose Mode -> Stage");
        Object c = canvas(MENU, 3);
        mainMenu(c, false, false, true);
        expectEq(getInt(c, "screen"), CHOOSE_IDOL, "Main opens Choose Idol");
        chooseIdol(c, false, false, true, false, false);
        expectEq(getInt(c, "screen"), CHOOSE_MODE, "picking an idol opens Choose Mode");
        chooseMode(c, false, false, true, false, false);
        expectEq(getInt(c, "screen"), STAGES, "picking Stage Mode opens Stage Select");
        stages(c, false, false, false, true);
        expectEq(getInt(c, "screen"), CHOOSE_MODE, "back from Stage steps up to Choose Mode");
        chooseMode(c, false, false, false, true, false);
        expectEq(getInt(c, "screen"), CHOOSE_IDOL, "back from Choose Mode steps up to Choose Idol");
        chooseIdol(c, false, false, false, true, false);
        expectEq(getInt(c, "screen"), MENU, "back from Choose Idol returns to the menu");
        expectEq(getInt(c, "menuIndex"), 3, "with the cursor still on Main");
    }

    /** The purchase page's tab is fixed by the submenu: L/R and the trailing row both step back
     *  to it (cursor on the tab just used), and the tab strip is a breadcrumb, never a control. */
    private static void testUpgradeTabsCycleAndBackLeaves() throws Exception {
        System.out.println("[21] the purchase page backs to the tab submenu instead of cycling");
        Object c = canvas(UPGRADES, 0);
        set(c, "shopTab", Integer.valueOf(1));
        upgrades(c, false, false, false, false, true);      // right
        expectEq(getInt(c, "screen"), SHOPTABS, "right steps back to the tab submenu");
        expectEq(getInt(c, "menuIndex"), 1, "with the cursor on the tab just used");
        expectEq(getInt(c, "shopTab"), 1, "and the tab itself unchanged");

        Object l = canvas(UPGRADES, 0);
        set(l, "shopTab", Integer.valueOf(0));
        upgrades(l, false, false, false, true, false);      // left
        expectEq(getInt(l, "screen"), SHOPTABS, "left steps back the same way");
        expectEq(getInt(l, "menuIndex"), 0, "with the cursor on Ability");

        // The trailing Kembali row of the current tab returns to the tab submenu as well.
        int lainnyaRows = ContentCatalog.upgradeGroupCount(2) + 1;
        Object back = canvas(UPGRADES, lainnyaRows - 1);
        set(back, "shopTab", Integer.valueOf(2));
        upgrades(back, false, false, true, false, false);
        expectEq(getInt(back, "screen"), SHOPTABS, "Kembali returns to the tab submenu");
        expectEq(getInt(back, "menuIndex"), 2, "with the cursor on Lainnya");

        // The cursor stays inside the tab's rows plus the trailing row.
        int rows = ContentCatalog.upgradeGroupCount(0) + 1;
        Object walk = canvas(UPGRADES, 0);
        for (int i = 0; i < 30; i++) upgrades(walk, false, true, false, false, false);
        int idx = getInt(walk, "menuIndex");
        expect(idx >= 0 && idx < rows, "the row index stayed within [0," + rows + "), saw " + idx);

        // The Armory backs out to Toko as well, restoring its row.
        Object armoryBack = canvas(ARMORY, 3);
        armory(armoryBack, false, false, false, true, false);
        expectEq(getInt(armoryBack, "screen"), SHOP, "the Armory returns to the Toko submenu");
        expectEq(getInt(armoryBack, "menuIndex"), 1, "on the Armory row");
    }

    // ---------------------------------------------------------------- helpers

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

    private static void expectEq(String got, String want, String label) {
        expect(want.equals(got), label + ": expected \"" + want + "\" got \"" + got + "\"");
    }
}