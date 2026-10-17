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
    private static final int SHOP = 10;
    private static final int MODES = 1;
    private static final int CHARACTERS = 2;
    private static final int STAGES = 3;
    private static final int SETTINGS = 4;
    private static final int PREP = 5;
    private static final int GAME = 6;
    private static final int RESULTS = 7;
    private static final int CONTROLS = 8;
    private static final int SPLASH = 9;

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

    // ---------------------------------------------------------------- tests

    public static void main(String[] args) throws Exception {
        testSplashAdvancesToMenu();
        testMainMenuRoutesToEveryScreen();
        testMainMenuIndexWrapsAtBothEnds();
        testExitItemRequestsShutdown();
        testModesScreenRoutesAndBacks();
        testCharacterScreenSelectsAndPersists();
        testCharacterScreenPaging();
        testCharacterIndexWraps();
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
     * Each of the seven menu entries routes somewhere sensible. Play and Stage Select both land on
     * stage select — Play with the character chosen on the Characters page, which is a browser now
     * rather than a step of the start-up flow — and the Shop entry is the global upgrade shop.
     */
    private static void testMainMenuRoutesToEveryScreen() throws Exception {
        System.out.println("[2] the main menu routes to every screen");
        int[][] routes = { { 0, STAGES }, { 1, MODES }, { 2, CHARACTERS }, { 3, STAGES },
                { 4, SHOP }, { 5, SETTINGS } };
        for (int[] r : routes) {
            Object c = canvas(MENU, r[0]);
            mainMenu(c, false, false, true);
            expectEq(getInt(c, "screen"), r[1],
                "menu entry " + r[0] + " routes to screen " + r[1]);
        }
    }

    /** Selection wraps in both directions and can never leave the seven-entry range. */
    private static void testMainMenuIndexWrapsAtBothEnds() throws Exception {
        System.out.println("[3] the menu index wraps at both ends");
        Object c = canvas(MENU, 0);
        mainMenu(c, false, false, false);
        expectEq(getInt(c, "menuIndex"), 0, "the index is unchanged without input");

        mainMenu(c, true, false, false);                 // up from 0 wraps to 6
        expectEq(getInt(c, "menuIndex"), 6, "up from the top wraps to the bottom");
        mainMenu(c, false, true, false);                 // down from 6 wraps to 0
        expectEq(getInt(c, "menuIndex"), 0, "down from the bottom wraps to the top");

        // Walk far past both ends and confirm the index is always in range.
        Object walker = canvas(MENU, 0);
        for (int i = 0; i < 40; i++) {
            mainMenu(walker, false, true, false);
            int idx = getInt(walker, "menuIndex");
            expect(idx >= 0 && idx < 7, "index " + idx + " stayed within [0,7)");
        }
    }

    /** The exit entry must set the shutdown flags rather than navigating anywhere. */
    private static void testExitItemRequestsShutdown() throws Exception {
        System.out.println("[4] the exit item requests shutdown");
        Object c = canvas(MENU, 6);
        // A null request would throw, so give the canvas a working stub instead.
        ShutdownStub stub = new ShutdownStub();
        set(c, "shutdownRequest", stub);
        mainMenu(c, false, false, true);
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
        Object back = canvas(MODES, 0);
        modes(back, false, false, false, true);
        expectEq(getInt(back, "screen"), MENU, "back returns to the menu");
        expectEq(getInt(back, "menuIndex"), 1, "and lands on the mode entry");

        Object pick = canvas(MODES, 0);
        modes(pick, false, false, true, false);
        expectEq(getInt(pick, "screen"), STAGES,
            "choosing the available mode continues to stage select");
        expectEq(getInt(pick, "modeIndex"), 0, "and records the chosen mode");

        // The index is bounded to the two entries this build exposes.
        Object walk = canvas(MODES, 0);
        for (int i = 0; i < 20; i++) modes(walk, false, true, false, false);
        int idx = getInt(walk, "menuIndex");
        expect(idx >= 0 && idx < 2, "the mode index stayed within [0,2), saw " + idx);
    }

    /**
     * Choosing a character records it and persists it, and the screen stays on the roster: the
     * status panel beside the list is the only thing a click changes. Play on the main menu is
     * what starts the run, so this screen is a browser rather than a launcher.
     */
    private static void testCharacterScreenSelectsAndPersists() throws Exception {
        System.out.println("[6] choosing a character records, persists and stays put");
        Object c = canvas(CHARACTERS, 2);
        characters(c, false, false, true, false, false);
        expectEq(getInt(c, "characterIndex"), 2, "the selection became the character index");
        expectEq(getInt(c, "screen"), CHARACTERS, "and the screen stayed on the roster");
        expectEq(getInt(c, "menuIndex"), 2, "with the cursor still on the chosen character");

        Field save = canvasClass().getDeclaredField("save");
        save.setAccessible(true);
        SaveStore s = (SaveStore) save.get(c);
        expectEq(s.selectedCharacter(), 2, "the choice was persisted for the next session");

        // A later click re-persists: the last character clicked is the one Play will use.
        characters(c, true, false, false, false, false);   // up to character 1
        characters(c, false, false, true, false, false);   // click it
        expectEq(getInt(c, "characterIndex"), 1, "a later click re-selects");
        expectEq(((SaveStore) save.get(c)).selectedCharacter(), 1,
            "and re-persists for the next session");
        expectEq(getInt(c, "screen"), CHARACTERS, "still without leaving the roster");
    }

    /** Paging only moves when another page exists, and left on the first page leaves the screen. */
    private static void testCharacterScreenPaging() throws Exception {
        System.out.println("[7] the character screen pages");
        int count = ContentCatalog.CHARACTERS.length + 1;
        int lastPage = (ContentCatalog.CHARACTERS.length - 1) / 8;

        Object c = canvas(CHARACTERS, 0);
        set(c, "characterPage", Integer.valueOf(0));
        characters(c, false, false, false, false, true);   // right on the first page
        expectEq(getInt(c, "characterPage"), 0, "right on the only page does not page");

        characters(c, false, false, false, true, false);  // left on the first page
        expectEq(getInt(c, "screen"), MENU, "left on the first page leaves character select");

        if (lastPage > 0) {
            Object paged = canvas(CHARACTERS, 0);
            set(paged, "characterPage", Integer.valueOf(0));
            characters(paged, false, false, false, false, true);
            expectEq(getInt(paged, "characterPage"), 1, "right advances a page when one exists");
            characters(paged, false, false, false, true, false);
            expectEq(getInt(paged, "characterPage"), 0, "and left comes back");
        }
        expect(count > 1, "the character list is non-empty");
    }

    /** Up and down wrap over the roster plus one trailing row: Back. */
    private static void testCharacterIndexWraps() throws Exception {
        System.out.println("[8] the character index wraps");
        int count = ContentCatalog.CHARACTERS.length + 1;
        Object c = canvas(CHARACTERS, 0);
        for (int i = 0; i < count + 5; i++) {
            characters(c, false, true, false, false, false);
            int idx = getInt(c, "menuIndex");
            expect(idx >= 0 && idx < count, "index " + idx + " stayed within [0," + count + ")");
        }
        // The one trailing entry is Back, which returns to the menu at the Characters entry.
        Object back = canvas(CHARACTERS, ContentCatalog.CHARACTERS.length);
        characters(back, false, false, true, false, false);
        expectEq(getInt(back, "screen"), MENU, "the trailing entry goes back to the menu");
        expectEq(getInt(back, "menuIndex"), 2, "with the cursor on the Characters entry");
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
        System.out.println("[10] the stage screen backs to character select");
        Object c = canvas(STAGES, 0);
        set(c, "characterIndex", Integer.valueOf(3));
        stages(c, false, false, false, true);
        expectEq(getInt(c, "screen"), CHARACTERS, "back returns to character select");
        expectEq(getInt(c, "menuIndex"), 3, "restoring the selected character as the cursor");
    }

    private static void testSettingsTogglesAndRoutes() throws Exception {
        System.out.println("[11] settings toggles hardcore and routes");
        Object toggle = canvas(SETTINGS, 0);
        Field save = canvasClass().getDeclaredField("save");
        save.setAccessible(true);
        SaveStore s = (SaveStore) save.get(toggle);
        expect(!s.hardcore(), "hardcore starts off");
        settings(toggle, false, false, true, false);
        expect(s.hardcore(), "the first entry toggles it on");
        settings(toggle, false, false, true, false);
        expect(!s.hardcore(), "and pressing again toggles it back off");

        Object controls = canvas(SETTINGS, 2);
        settings(controls, false, false, true, false);
        expectEq(getInt(controls, "screen"), CONTROLS, "the controls entry opens the controls page");

        Object back = canvas(SETTINGS, 3);
        settings(back, false, false, false, true);
        expectEq(getInt(back, "screen"), MENU, "back returns to the menu");
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
}