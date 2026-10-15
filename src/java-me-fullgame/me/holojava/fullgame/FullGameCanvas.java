package me.holojava.fullgame;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

/** Menu flow and lifecycle-aware screen coordinator for the rebuilt game. */
public class FullGameCanvas extends GameCanvas implements Runnable {
    interface ShutdownRequest { void requestShutdown(); }
    private static final int MENU = 0;
    private static final int MODES = 1;
    private static final int CHARACTERS = 2;
    private static final int STAGES = 3;
    private static final int SETTINGS = 4;
    private static final int PREP = 5;
    private static final int GAME = 6;
    private static final int RESULTS = 7;
    private static final int CONTROLS = 8;
    private static final int SPLASH = 9;
    private static final int CHARACTER_PAGE_SIZE = 8;

    /**
     * Gameplay key bindings.
     *
     * <p>The basic attack lives on 5, on FIRE, and on the GAME_A game action. All three reach the
     * world as a single boolean, so one physical press produces exactly one attack dispatch and a
     * handset that maps GAME_A to a different physical key than 5 or FIRE still works. 1 is kept as
     * an explicit numeric fallback for handsets whose GAME_A is unusable.
     *
     * <p>Pause moved off FIRE to 0, because FIRE is now the primary attack and one key cannot be
     * both. Menu confirm, level-up confirm, cancel/back and the d-pad are untouched. 8 took over the
     * skill binding from 5, which is now the attack; 9 keeps the special.
     */
    private static final int KEY_ATTACK = '5';
    private static final int KEY_ATTACK_ALT = '1';
    private static final int KEY_PAUSE = '0';
    private static final int KEY_SKILL = '8';
    private static final int KEY_SPECIAL = '9';

    // Shared HoloJava UI palette. The page and the panel are both light, so every piece of menu
    // text is drawn in MENU_INK: white only reaches 1.66:1 on MENU_BG and 1.35:1 on MENU_PANEL,
    // while the ink reaches 3.48:1 and 4.27:1. MENU_ACCENT is reserved for non-text selection marks
    // (bars and frames), which stay readable through the "> " marker and the bold weight.
    // Palette white (#FFFFFF) is deliberately not declared: on this palette it has no surface that
    // clears the contrast bar, and an unused constant would be dead code.
    private static final int MENU_BG_R = 117, MENU_BG_G = 213, MENU_BG_B = 247;             // #75D5F7
    private static final int MENU_PANEL_R = 168, MENU_PANEL_G = 231, MENU_PANEL_B = 252;    // #A8E7FC
    private static final int MENU_INK_R = 36, MENU_INK_G = 108, MENU_INK_B = 145;           // #246C91
    private static final int MENU_ACCENT_R = 66, MENU_ACCENT_G = 191, MENU_ACCENT_B = 239;  // #42BFEF
    // Splash sky, drawn from the same palette so the splash no longer sits on neutral greys. The
    // three nested bands keep their original lightness order and stay soft-edged (1.23:1 and
    // 1.27:1 between neighbours, softer than the 1.42:1 and 1.59:1 of the old greys). Both splash
    // labels sit on the outer band only, where MENU_INK measures 4.27:1.
    private static final int SPLASH_SKY_DD_R = 168, SPLASH_SKY_DD_G = 231, SPLASH_SKY_DD_B = 252; // #A8E7FC
    private static final int SPLASH_SKY_D_R = 117, SPLASH_SKY_D_G = 213, SPLASH_SKY_D_B = 247;   // #75D5F7
    private static final int SPLASH_SKY_R = 66, SPLASH_SKY_G = 191, SPLASH_SKY_B = 239;         // #42BFEF
    private static final int MENU_BODY_Y = 40;     // header band height; all content starts below

    // Splash/Main Menu layout, in logical 320x240 pixels.
    private static final int SPLASH_LOGO_X = 10, SPLASH_LOGO_Y = 42;
    private static final int SPLASH_PROMPT_Y = 196;
    private static final int MENU_LOGO_X = 10, MENU_LOGO_Y = 8;
    private static final int MENU_LIST_X = 14, MENU_LIST_Y = 112, MENU_ROW_PITCH = 21;
    // Pause menu. Two entries at the shared 22px list pitch span 44px, so 98 centres them on the
    // 240px page. Index 0 is Resume so the menu always opens on the safe choice.
    private static final String[] PAUSE_ITEMS = { "Resume", "Return to Menu" };
    private static final int PAUSE_LIST_Y = 98;
    private static final int MENU_ACHAN_X = 205, MENU_ACHAN_Y = 40;

    private final AssetLibrary assets;
    private final SaveStore save;
    private final ShutdownRequest shutdownRequest;
    private volatile boolean stopped;
    private volatile boolean threadRunning;
    private volatile boolean applicationPaused;
    private volatile boolean exitRequested;
    private int screen = SPLASH;
    private int menuIndex;
    private int modeIndex;
    private int characterIndex;
    private int stageIndex;
    private int resultIndex;
    private int pauseIndex;
    private boolean fireWasDown;
    private boolean upWasDown;
    private boolean downWasDown;
    private boolean leftWasDown;
    private boolean rightWasDown;
    // keyPressed() runs on the device event thread while update() runs on the game thread, so
    // these three latches must be volatile or the press can be missed entirely. The other flags
    // below follow the same convention for the same reason.
    private volatile boolean skillPressed;
    private volatile boolean specialPressed;
    private boolean attackWasDown;
    private volatile boolean attackPulse;
    private volatile boolean pausePressed;
    private int characterPage;
    private final Viewport viewport = new Viewport();
    private RunWorld world;
    private boolean worldWasVictory;
    private String validationError;

    public FullGameCanvas(AssetLibrary assets, SaveStore save, String validationError, ShutdownRequest shutdownRequest) {
        super(true);
        this.assets = assets;
        this.save = save;
        this.shutdownRequest = shutdownRequest;
        this.validationError = validationError;
        setFullScreenMode(true);
        characterIndex = save.selectedCharacter();
        if (characterIndex < 0 || characterIndex >= ContentCatalog.CHARACTERS.length) characterIndex = 0;
        modeIndex = 0;
        stageIndex = 0;
    }

    public boolean isRunning() { return threadRunning; }
    public boolean exitRequested() { return exitRequested; }
    public void pauseApplication() {
        applicationPaused = true;
        if (world != null) world.pause();
    }
    public void resumeApplication() {
        applicationPaused = false;
        if (world != null) world.resume();
    }
    public void stopApplication() { stopped = true; }

    public void run() {
        threadRunning = true;
        long last = System.currentTimeMillis();
        long accumulator = 0;
        while (!stopped) {
            if (applicationPaused) {
                sleep(40);
                last = System.currentTimeMillis();
                continue;
            }
            long now = System.currentTimeMillis();
            long delta = now - last;
            if (delta < 0) delta = 0;
            if (delta > 200) delta = 200;
            last = now;
            accumulator += delta;
            int updates = 0;
            while (accumulator >= 40L && updates < 3) {
                update(now);
                accumulator -= 40L;
                updates++;
            }
            render();
            flushGraphics();
            sleep(16);
        }
        threadRunning = false;
    }

    public void keyPressed(int key) {
        // Numeric action keys, declared as constants so the CONTROLS page, the in-game hint and
        // this dispatch cannot drift apart. Every binding here is a one-shot latch consumed by the
        // next update() on the game thread; keyPressed itself runs on the device event thread.
        if (key == KEY_ATTACK) attackPulse = true;
        if (key == KEY_ATTACK_ALT) attackPulse = true;
        if (key == KEY_SKILL) skillPressed = true;
        if (key == KEY_SPECIAL) specialPressed = true;
        if (key == KEY_PAUSE) pausePressed = true;
    }

    private void update(long now) {
        // FIRE is now Pause (reverted from interactive basic attack)
        // Attack comes from keys 5 and 1 only; FIRE triggers pause
        int keys = getKeyStates();
        boolean fire = (keys & FIRE_PRESSED) != 0;
        if (fire) pausePressed = true;
        boolean up = (keys & UP_PRESSED) != 0;
        boolean down = (keys & DOWN_PRESSED) != 0;
        boolean left = (keys & LEFT_PRESSED) != 0;
        boolean right = (keys & RIGHT_PRESSED) != 0;
        boolean attackHeld = (keys & GAME_A_PRESSED) != 0;
        boolean fireEdge = fire && !fireWasDown;
        boolean upEdge = up && !upWasDown;
        boolean downEdge = down && !downWasDown;
        boolean leftEdge = left && !leftWasDown;
        boolean rightEdge = right && !rightWasDown;
        boolean attackEdge = attackHeld && !attackWasDown;
        fireWasDown = fire; upWasDown = up; downWasDown = down; leftWasDown = left;
        attackWasDown = attackHeld;

        if (validationError != null && validationError.length() > 0) return;
        viewport.update(getWidth(), getHeight());
        if (world != null) world.setViewport(viewport);

        if (screen == GAME) {
            if (world.state() == RunWorld.STATE_PAUSED) {
                // The simulation is frozen, so the pause menu owns the navigation keys here. L and R
                // are deliberately not read on this path: they stay player movement in gameplay.
                updatePause(upEdge, downEdge, fireEdge || pausePulse());
            } else {
                if (pausePulse() && world.state() == RunWorld.STATE_RUNNING) { world.pause(); pauseIndex = 0; }
                if (world.state() != RunWorld.STATE_PAUSED) {
                    // FIRE is Pause (reverted from interactive basic attack)
                    // Attack: 5 and 1 only; this control is now used for pause
                    // the one control every MIDP handset is guaranteed to have. The level-up screen
                    // below still confirms on FIRE, and RunWorld.update ignores a non-running state,
                    // so a confirm there never also fires a weapon.
                    world.update(keys, attackHeld || attackEdge || attackPulse,
                            skillPressed, specialPressed, now);
                }
                if (world.state() == RunWorld.STATE_LEVEL_UP) {
                    if (upEdge) world.moveOffer(-1);
                    if (downEdge) world.moveOffer(1);
                    if (fireEdge) world.selectOffer();
                }
                if (world.state() == RunWorld.STATE_GAME_OVER || world.state() == RunWorld.STATE_VICTORY) {
                    handleRunEnd();
                }
            }
            // One-shot latches are dropped on every exit from gameplay. While paused the simulation
            // is not ticked, so 5, 9 and 1 would otherwise survive the pause and fire on the first
            // tick after resuming.
            attackPulse = false;
            skillPressed = false;
            specialPressed = false;
            pausePressed = false;
            return;
        }
        // L/R map onto the two menu roles the d-pad directions do not already own. A direction is
        // Back on the screens that already treat it that way and Select on the rest, so a single
        // press can never both confirm a choice and leave the screen.
        if (screen == SPLASH) updateSplash(skillPressed || fireEdge || leftEdge || rightEdge, keys);
        else if (screen == MENU) updateMain(upEdge, downEdge, fireEdge || leftEdge || rightEdge);
        else if (screen == MODES) updateModes(upEdge, downEdge, fireEdge || leftEdge, rightEdge);
        else if (screen == CHARACTERS) updateCharacters(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == STAGES) updateStages(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == SETTINGS) updateSettings(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == CONTROLS) handleControlsInput(leftEdge || rightEdge, fireEdge);
        else if (screen == PREP) updatePrep(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == RESULTS) updateResults(upEdge, downEdge, fireEdge || leftEdge, rightEdge);
    }

    /**
     * The controls page has nothing to navigate, so any of the three confirming inputs returns to
     * settings. Split out of {@code update()} unchanged, for the same reason as
     * {@link #handleRunEnd()}: the inline branch sat behind the MIDP {@code getKeyStates()} call
     * and could not otherwise be executed off-device.
     *
     * @param back left or right pressed
     * @param confirm fire pressed
     */
    private void handleControlsInput(boolean back, boolean confirm) {
        if (back || confirm) screen = SETTINGS;
    }

    private void updateMain(boolean up, boolean down, boolean fire) {
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 6);
        if (!fire) return;
        switch (menuIndex) {
            case 0: screen = CHARACTERS; menuIndex = 0; characterPage = 0; break;
            case 1: screen = MODES; menuIndex = modeIndex; break;
            case 2: screen = CHARACTERS; menuIndex = characterIndex; characterPage = characterIndex / CHARACTER_PAGE_SIZE; break;
            case 3: screen = STAGES; menuIndex = 0; break;
            case 4: screen = SETTINGS; menuIndex = 0; break;
            case 5: exitRequested = true; stopped = true; shutdownRequest.requestShutdown(); break;
            default: break;
        }
    }

    private void updateModes(boolean up, boolean down, boolean fire, boolean back) {
        // Stage Mode only for now. Endless and Time remain defined in ContentCatalog.MODES and
        // fully implemented in RunWorld; widening this list is the single point of re-enabling them.
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 2);
        if (back) { screen = MENU; menuIndex = 1; return; }
        if (!fire) return;
        if (menuIndex == 1) { screen = MENU; menuIndex = 1; }
        else { modeIndex = 0; screen = CHARACTERS; menuIndex = characterIndex; characterPage = characterIndex / CHARACTER_PAGE_SIZE; }
    }

    private void updateCharacters(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        int count = ContentCatalog.CHARACTERS.length + 1;
        if (left) {
            if (characterPage > 0) { characterPage--; menuIndex = Math.max(0, menuIndex - CHARACTER_PAGE_SIZE); }
            else { screen = MENU; menuIndex = 0; }
            return;
        }
        if (right) {
            int lastPage = (ContentCatalog.CHARACTERS.length - 1) / CHARACTER_PAGE_SIZE;
            if (characterPage < lastPage) { characterPage++; menuIndex = Math.min(count - 1, menuIndex + CHARACTER_PAGE_SIZE); }
            return;
        }
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), count);
        if (!fire) return;
        if (menuIndex == ContentCatalog.CHARACTERS.length) { screen = MENU; menuIndex = 2; return; }
        characterIndex = menuIndex;
        characterPage = characterIndex / CHARACTER_PAGE_SIZE;
        save.setSelectedCharacter(characterIndex);
        screen = STAGES;
        menuIndex = 0;
    }

    private void updateStages(boolean up, boolean down, boolean fire, boolean left) {
        int[] stages = selectableStages();
        int count = stages.length + 1;
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), count);
        if (left) { screen = CHARACTERS; menuIndex = characterIndex; return; }
        if (!fire) return;
        if (menuIndex == stages.length) { screen = MAIN_SAFE(); menuIndex = 3; return; }
        int candidate = stages[menuIndex];
        if (!stageUnlocked(candidate) || !stageAllowed(candidate)) return;
        stageIndex = candidate;
        screen = PREP;
    }

    /**
     * Stage indices reachable from Stage Mode: normal stages only. Hard variants and the Time
     * stage stay defined in ContentCatalog.STAGES, and stageIndex still stores the real catalog
     * index used by RunWorld and AssetLibrary.prepareStage. Widening this filter is the single
     * point of re-enabling them.
     */
    private static int[] selectableStages() {
        int[] buffer = new int[ContentCatalog.STAGES.length];
        int count = 0;
        for (int i = 0; i < ContentCatalog.STAGES.length; i++) {
            ContentCatalog.StageDef s = ContentCatalog.STAGES[i];
            if (!s.hard && !s.timeStage) buffer[count++] = i;
        }
        int[] result = new int[count];
        System.arraycopy(buffer, 0, result, 0, count);
        return result;
    }

    private int MAIN_SAFE() { return MENU; }

    private void updateSettings(boolean up, boolean down, boolean fire, boolean left) {
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 4);
        if (left) { screen = MENU; menuIndex = 4; return; }
        if (!fire) return;
        if (menuIndex == 0) save.setHardcore(!save.hardcore());
        else if (menuIndex == 1) { /* audio is deliberately disabled on unverified runtimes */ }
        else if (menuIndex == 2) screen = CONTROLS;
        else screen = MENU;
    }

    private void updatePrep(boolean up, boolean down, boolean fire, boolean left) {
        if (left) {
            screen = STAGES;
            int[] stages = selectableStages();
            menuIndex = 0;
            for (int slot = 0; slot < stages.length; slot++) if (stages[slot] == stageIndex) { menuIndex = slot; break; }
            return;
        }
        if (fire) startRun();
    }

    /**
     * Pause menu input. Runs while RunWorld is STATE_PAUSED, so it never competes with the
     * simulation for keys. "Return to Menu" abandons the run exactly the way the Results screen
     * does: the RunWorld reference is dropped and nothing about the abandoned run is saved.
     */
    private void updatePause(boolean up, boolean down, boolean fire) {
        if (up || down) pauseIndex = wrap(pauseIndex + (up ? -1 : 1), PAUSE_ITEMS.length);
        if (!fire) return;
        if (pauseIndex == 0) world.resume();
        else { screen = MENU; menuIndex = 0; world = null; }
    }

    private void updateResults(boolean up, boolean down, boolean fire, boolean back) {
        if (up || down) resultIndex = wrap(resultIndex + (up ? -1 : 1), 2);
        if (back) { screen = MENU; menuIndex = 0; world = null; return; }
        if (!fire) return;
        if (resultIndex == 0) startRun();
        else { screen = MENU; menuIndex = 0; world = null; }
    }

    private void startRun() {
        if (!stageUnlocked(stageIndex) || !stageAllowed(stageIndex)) { screen = STAGES; return; }
        world = new RunWorld(assets, save, characterIndex, modeIndex, stageIndex);
        world.setViewport(viewport);
        screen = GAME;
    }

    /**
     * The run has finished, in either direction. Split out of {@code update()} unchanged so the
     * completion decision is reachable without the MIDP {@code getKeyStates()} call that
 * {@code update()} makes first, which the desktop stub refuses.
     *
     * <p>Completion is recorded under {@code ContentCatalog.stage(stageIndex).order}, not under the
     * stage index: {@link #stageUnlocked(int)} looks those orders up, and they are the same numbers
     * only because the catalog is currently ordered. A stage already marked complete is simply
     * marked complete again, so replaying it is harmless.
     */
    private void handleRunEnd() {
        worldWasVictory = world.state() == RunWorld.STATE_VICTORY;
        if (worldWasVictory && modeIndex == 0) {
            save.setCompleted(characterIndex, ContentCatalog.stage(stageIndex).order);
        }
        screen = RESULTS;
        resultIndex = 0;
    }

    private boolean stageAllowed(int index) {
        ContentCatalog.StageDef s = ContentCatalog.stage(index);
        if (ContentCatalog.MODES[modeIndex].completion == ContentCatalog.COMPLETE_KILL_TARGET) return s.timeStage;
        return !s.timeStage;
    }

    private boolean stageUnlocked(int index) {
        ContentCatalog.StageDef s = ContentCatalog.stage(index);
        if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;
        if (s.timeStage) return save.isCompleted(characterIndex, 2);
        return save.isCompleted(characterIndex, s.unlockRequirement);
    }

    private void render() {
        Graphics g = getGraphics();
        if (g == null) return;
        int w = getWidth(); int h = getHeight();
        g.setColor(0, 0, 0); g.fillRect(0, 0, w, h);
        viewport.update(w, h);
        int ox = (w - Viewport.MIN_W) / 2; int oy = (h - Viewport.MIN_H) / 2;
        if (ox < 0) ox = 0; if (oy < 0) oy = 0;
        if (validationError != null && validationError.length() > 0) {
            drawText(g, ox + 12, oy + 30, "CONTENT VALIDATION ERROR", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
            drawText(g, ox + 12, oy + 70, validationError, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            return;
        }
        if (screen == GAME && world != null) {
            world.setViewport(viewport);
            world.draw(g, viewport.offsetX, viewport.offsetY, System.currentTimeMillis());
            if (world.state() == RunWorld.STATE_LEVEL_UP) drawLevelUp(g, ox, oy);
            if (world.state() == RunWorld.STATE_PAUSED) drawPause(g, ox, oy);
            if (world.state() == RunWorld.STATE_GAME_OVER || world.state() == RunWorld.STATE_VICTORY) { /* results handled next tick */ }
        } else if (screen == RESULTS) drawResults(g, ox, oy);
        else drawMenuScreen(g, ox, oy);
    }

    /** Shared page background: a lighter header band behind the title row and the slightly darker
     *  content band below it. The Main Menu paints its own, logo-driven variant of the same pair. */
    private void drawPageBackground(Graphics g, int x, int y) {
        g.setColor(MENU_BG_R, MENU_BG_G, MENU_BG_B); g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
        g.fillRect(x, y + MENU_BODY_Y, Viewport.MIN_W, Viewport.MIN_H - MENU_BODY_Y);
    }

    private void drawMenuScreen(Graphics g, int x, int y) {
        if (screen == SPLASH) { drawSplash(g, x, y); return; }
        if (screen != MENU) drawPageBackground(g, x, y);
        if (screen == MENU) drawMainMenu(g, x, y);
        else if (screen == MODES) drawModes(g, x, y);
        else if (screen == CHARACTERS) drawCharacters(g, x, y);
        else if (screen == STAGES) drawStages(g, x, y);
        else if (screen == SETTINGS) drawSettings(g, x, y);
        else if (screen == CONTROLS) drawControls(g, x, y);
        else if (screen == PREP) drawPrep(g, x, y);
    }

    /**
     * Splash screen. Drawn entirely inside the canvas as a screen state rather than as a separate
     * Displayable, so the MIDlet lifecycle and the single render thread stay untouched.
     */
    private void drawSplash(Graphics g, int x, int y) {
        g.setColor(SPLASH_SKY_DD_R, SPLASH_SKY_DD_G, SPLASH_SKY_DD_B);
        g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        g.setColor(SPLASH_SKY_D_R, SPLASH_SKY_D_G, SPLASH_SKY_D_B);
        g.fillRect(x, y + 34, Viewport.MIN_W, 150);
        g.setColor(SPLASH_SKY_R, SPLASH_SKY_G, SPLASH_SKY_B);
        g.fillRect(x, y + 60, Viewport.MIN_W, 98);

        Image logo = assets.logo();
        if (logo != null) g.drawImage(logo, x + SPLASH_LOGO_X, y + SPLASH_LOGO_Y, Graphics.TOP | Graphics.LEFT);

        // Both labels fall on the outer band only, where the shared ink measures 4.27:1. White would
        // be 1.35:1 there, so it is not used.
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        drawText(g, x + Viewport.MIN_W / 2, y + 12, "HOLOJAVA", Font.STYLE_BOLD, Font.SIZE_LARGE);
        drawText(g, x + Viewport.MIN_W / 2, y + SPLASH_PROMPT_Y, "PRESS FIRE TO START",
                Font.STYLE_BOLD, Font.SIZE_MEDIUM);
    }

    /**
     * Splash input. FIRE, L and R all leave the splash, and so does key 5 (skillPressed), so the
     * prompt and the accepted keys agree on how to start. The transition is one-way: screen is
     * only ever assigned SPLASH during construction, so there is no path back.
     */
    private void updateSplash(boolean advance, int keys) {
        if (!advance) return;
        clearInputLatches(keys);
        screen = MENU;
        menuIndex = 0;
    }

    /**
     * Drops every pending one-shot key latch and re-seeds the held-key edge state from the keys
     * that are physically down right now. Seeding with the live state (rather than false) stops a
     * still-held FIRE from manufacturing a fresh edge on the next update tick, which would
     * otherwise carry the splash press straight into a Main Menu selection.
     */
    /**
     * Consumes the pause key press latch, exactly like the other one-shot latches.
     */
    private boolean pausePulse() {
        boolean p = pausePressed;
        pausePressed = false;
        return p;
    }

    private void clearInputLatches(int keys) {
        skillPressed = false;
        specialPressed = false;
        attackPulse = false;
        pausePressed = false;
        fireWasDown = (keys & FIRE_PRESSED) != 0;
        upWasDown = (keys & UP_PRESSED) != 0;
        downWasDown = (keys & DOWN_PRESSED) != 0;
        leftWasDown = (keys & LEFT_PRESSED) != 0;
        rightWasDown = (keys & RIGHT_PRESSED) != 0;
        attackWasDown = (keys & GAME_A_PRESSED) != 0;
    }

    /**
     * Main Menu, and the visual reference for every other menu screen: the logo sits above the
     * list on the left, A-chan decorates the right side, and the six entries keep their original
     * order, wrap behaviour and actions. A-chan is one static frame and is never added to the
     * selectable list, so it cannot be picked and cannot move the cursor.
     */
    private void drawMainMenu(Graphics g, int x, int y) {
        g.setColor(MENU_BG_R, MENU_BG_G, MENU_BG_B); g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
        g.fillRect(x, y + 84, Viewport.MIN_W, 108);

        Image logo = assets.menuLogo();
        if (logo != null) g.drawImage(logo, x + MENU_LOGO_X, y + MENU_LOGO_Y, Graphics.TOP | Graphics.LEFT);
        drawMenuAchan(g, x + MENU_ACHAN_X, y + MENU_ACHAN_Y);

        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        drawText(g, x + MENU_LIST_X, y + 88, "HOLOJAVA", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        String[] items = { "Play", "Game Mode", "Characters", "Stage Select", "Settings", "Exit" };
        for (int i = 0; i < items.length; i++) {
            boolean selected = i == menuIndex;
            int rowY = y + MENU_LIST_Y + i * MENU_ROW_PITCH;
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            drawText(g, x + MENU_LIST_X + 4, rowY, (selected ? "> " : "  ") + items[i],
                    selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            if (selected) {
                g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
                g.fillRect(x + MENU_LIST_X, rowY + 2, 2, 12);
            }
        }
    }

    /** Draws the A-chan decoration. One fixed frame, no timer and no frame index: the sprite is
     *  static menu artwork. Purely visual, no input or state is affected. */
    private void drawMenuAchan(Graphics g, int x, int y) {
        Image frame = assets.achanFrame();
        if (frame != null) g.drawImage(frame, x, y, Graphics.TOP | Graphics.LEFT);
    }

    private void drawModes(Graphics g, int x, int y) {
        title(g, x, y, "GAME MODE");
        String[] items = { "Stage Mode", "Back" };
        list(g, x, y + 60, items, menuIndex);
        ink(g);
        drawText(g, x + 20, y + 195, ContentCatalog.MODES[modeIndex].objective, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 20, y + 210, "Endless and Time modes are not available yet.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Character Select layout for the 320x240 logical screen, in logical pixels relative to the
     * centred 320x240 panel: a 65px list on the left, a 72x91 portrait box in the middle, and the
     * remaining right column for the stats panel. MIDP cannot scale images, so the portrait keeps
     * its native 43x37..44x38 size and is only centred inside the 72x91 box.
     */
    private static final int CHARS_LIST_X = 2;
    private static final int CHARS_ROW_Y = 42;
    private static final int CHARS_ROW_PITCH = 24;
    private static final int CHARS_PORTRAIT_X = 69;
    private static final int CHARS_PORTRAIT_Y = 42;
    private static final int CHARS_PORTRAIT_W = 72;
    private static final int CHARS_PORTRAIT_H = 91;
    private static final int CHARS_PANEL_X = 145;
    // Gutter between the right-column labels and their values.
    private static final int CHARS_PANEL_GUTTER = 50;

    // Right-panel text is rebuilt only when the highlighted character changes, so the panel costs
    // no per-frame string allocations.
    private int charsPanelIndex = -1;
    private String charsPanelName = "";
    private String charsPanelHp = "";
    private String charsPanelAtk = "";
    private String charsPanelSpd = "";
    private String charsPanelCrit = "";
    private String charsPanelWeapon = "";
    private String charsPanelSkill = "";
    private String charsPanelSpecial = "";
    private String charsPanelArt = "";

    private void drawCharacters(Graphics g, int x, int y) {
        title(g, x, y, "CHARACTER SELECT");
        int count = ContentCatalog.CHARACTERS.length;
        if (menuIndex >= count) characterPage = count / CHARACTER_PAGE_SIZE;
        else characterPage = menuIndex / CHARACTER_PAGE_SIZE;
        int first = characterPage * CHARACTER_PAGE_SIZE;
        int last = Math.min(count, first + CHARACTER_PAGE_SIZE);

        // Left column: one compact row per character on the current page, then the Back entry.
        // Only the catalog id is listed so the 65px column never truncates a display name.
        for (int i = first; i < last; i++) {
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            boolean on = i == menuIndex;
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            drawText(g, x + CHARS_LIST_X, y + CHARS_ROW_Y + (i - first) * CHARS_ROW_PITCH,
                    (on ? "> " : "  ") + c.id, on ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        if (menuIndex == count) {
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            drawText(g, x + CHARS_LIST_X, y + CHARS_ROW_Y + (last - first) * CHARS_ROW_PITCH,
                    "> Back", Font.STYLE_BOLD, Font.SIZE_SMALL);
        }

        int selected = Math.min(menuIndex, count - 1);
        ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[selected];

        // Middle column: the selected character's portrait, native size, centred in the box. The
        // frame geometry is untouched; only its colours changed. The mat uses the palette ink
        // because the portraits are bright sprites (median luma 195) and need a dark backing, and
        // the outline uses the cyan accent. The caption sits below the frame on the light panel,
        // so it stays in the ink: white there would be 1.35:1.
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.fillRect(x + CHARS_PORTRAIT_X, y + CHARS_PORTRAIT_Y, CHARS_PORTRAIT_W, CHARS_PORTRAIT_H);
        g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
        g.drawRect(x + CHARS_PORTRAIT_X, y + CHARS_PORTRAIT_Y, CHARS_PORTRAIT_W - 1, CHARS_PORTRAIT_H - 1);
        Image portrait = assets.characterPortrait(selected);
        if (portrait != null) {
            g.drawImage(portrait, x + CHARS_PORTRAIT_X + (CHARS_PORTRAIT_W - portrait.getWidth()) / 2,
                    y + CHARS_PORTRAIT_Y + (CHARS_PORTRAIT_H - portrait.getHeight()) / 2,
                    Graphics.TOP | Graphics.LEFT);
        }
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        drawText(g, x + CHARS_PORTRAIT_X, y + CHARS_PORTRAIT_Y + CHARS_PORTRAIT_H + 8,
                c.id, Font.STYLE_BOLD, Font.SIZE_SMALL);

        if (charsPanelIndex != selected) {
            charsPanelIndex = selected;
            charsPanelName = c.name;
            charsPanelHp = String.valueOf(c.hp);
            charsPanelAtk = c.atkPercent + "%";
            charsPanelSpd = c.spdPercent + "%";
            charsPanelCrit = c.critPercent + "%";
            ContentCatalog.WeaponDef weapon = ContentCatalog.weapon(c.mainWeapon);
            charsPanelWeapon = weapon == null ? c.mainWeapon : weapon.name;
            charsPanelSkill = c.skill;
            charsPanelSpecial = c.special;
            charsPanelArt = c.fallbackArt ? "fallback" : "original";
        }

        // Right column: full name, then a label gutter with the stats CharacterDef actually
        // carries, then the loadout. Labels are right-aligned into a fixed gutter so the widest
        // value ("Hunter's Instinct", 17 chars) still fits inside the 175px column.
        // DEF is not shown because ContentCatalog.CharacterDef has no defence field, and
        // RunWorld.defensePercent is a runtime item stat that is only ever reset to 0.
        int px = x + CHARS_PANEL_X;
        int py = y + CHARS_ROW_Y;
        int vx = px + CHARS_PANEL_GUTTER;
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        drawText(g, px, py, charsPanelName, Font.STYLE_BOLD, Font.SIZE_SMALL);
        inkDim(g);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 20, "HP", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 38, "ATK", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 56, "SPD", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 74, "CRT", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ink(g);
        drawText(g, vx, py + 20, charsPanelHp, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 38, charsPanelAtk, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 56, charsPanelSpd, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 74, charsPanelCrit, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        inkDim(g);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 98, "Weapon", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ink(g);
        drawText(g, vx, py + 98, charsPanelWeapon, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        inkDim(g);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 116, "Skill", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ink(g);
        drawText(g, vx, py + 116, charsPanelSkill, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        inkDim(g);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 134, "Special", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ink(g);
        drawText(g, vx, py + 134, charsPanelSpecial, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        inkDim(g);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 158, "Art", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ink(g);
        drawText(g, vx, py + 158, charsPanelArt, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    // Map/stage status colours, taken from the shared palette. The ink always means
    // unlocked-and-allowed; the cyan accent means unavailable and deliberately recedes. The
    // selected row is not a third colour: it is marked by the "> " arrow, bold style and an
    // outline frame instead. The "[mode]" and "[locked]" suffixes keep the state readable in text
    // regardless of the colour.
    private static final int STAGE_UNLOCKED_R = 36, STAGE_UNLOCKED_G = 108, STAGE_UNLOCKED_B = 145;
    private static final int STAGE_LOCKED_R = 66, STAGE_LOCKED_G = 191, STAGE_LOCKED_B = 239;
    private static final int STAGE_ROW_X = 12;
    private static final int STAGE_ROW_Y = 40;
    private static final int STAGE_ROW_PITCH = 16;
    private static final int STAGE_FRAME_X = 8;
    private static final int STAGE_FRAME_W = 244;
    private static final int STAGE_FRAME_H = 14;

    private void drawStages(Graphics g, int x, int y) {
        title(g, x, y, "STAGE SELECT");
        int start = STAGE_ROW_Y;
        int[] stages = selectableStages();
        for (int slot = 0; slot < stages.length; slot++) {
            ContentCatalog.StageDef s = ContentCatalog.STAGES[stages[slot]];
            boolean allowed = stageAllowed(stages[slot]); boolean unlocked = stageUnlocked(stages[slot]);
            boolean selected = slot == menuIndex;
            int rowY = y + start + slot * STAGE_ROW_PITCH;
            // Colour is set before the text so it applies to this row, not the next one.
            if (allowed && unlocked) g.setColor(STAGE_UNLOCKED_R, STAGE_UNLOCKED_G, STAGE_UNLOCKED_B);
            else g.setColor(STAGE_LOCKED_R, STAGE_LOCKED_G, STAGE_LOCKED_B);
            String prefix = selected ? "> " : "  ";
            String suffix = !allowed ? " [mode]" : (!unlocked ? " [locked]" : "");
            drawText(g, x + STAGE_ROW_X, rowY, prefix + s.name + suffix,
                    selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            if (selected) {
                g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
                g.drawRect(x + STAGE_FRAME_X, rowY - 2, STAGE_FRAME_W, STAGE_FRAME_H);
            }
        }
        ink(g);
        drawText(g, x + 12, y + 214, "Hard and Time stages are not available yet.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawSettings(Graphics g, int x, int y) {
        title(g, x, y, "SETTINGS");
        String[] items = { "Hardcore: " + (save.hardcore() ? "ON" : "OFF"),
                "Audio: OFF (runtime not verified)", "Controls", "Back" };
        list(g, x, y + 65, items, menuIndex);
        ink(g);
        drawText(g, x + 20, y + 190, "Hardcore sets Endless player HP to 1.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 20, y + 210, "Persistence is optional RMS storage.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawControls(Graphics g, int x, int y) {
        title(g, x, y, "CONTROLS");
        ink(g);
        drawText(g, x + 24, y + 60, "D-pad: move / menu navigation", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 82, "GAME A / FIRE / 5 / 1: basic attack", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 104, "0: pause   FIRE: select / confirm", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 126, "L and R are extra select / back keys in menus", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 148, "8: skill   9: special attack", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 180, "Orbiting and passive weapons may fire autonomously.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawPrep(Graphics g, int x, int y) {
        title(g, x, y, "RUN PREPARATION");
        ink(g);
        drawText(g, x + 24, y + 62, "Character: " + ContentCatalog.character(characterIndex).name, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 82, "Mode: " + ContentCatalog.mode(modeIndex).name, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 102, "Stage: " + ContentCatalog.stage(stageIndex).name, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 132, ContentCatalog.mode(modeIndex).objective, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 160, "Boss schedule and enemy pool will load on start.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawLevelUp(Graphics g, int x, int y) {
        g.setColor(0, 0, 0); g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        drawText(g, x + 100, y + 14, "LEVEL UP", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        for (int i = 0; i < world.offerCount(); i++) {
            int yy = y + 46 + i * 24;
            boolean selected = i == world.selectedOffer();
            if (world.offerIsCollab(i)) g.setColor(selected ? 255 : 230, selected ? 190 : 138, selected ? 90 : 40);
            else g.setColor(selected ? 255 : 190, selected ? 230 : 190, selected ? 60 : 190);
            drawText(g, x + 24, yy, (selected ? "> " : "  ") + world.offerLabel(i), Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setColor(130, 130, 130);
            drawText(g, x + 240, yy, world.offerType(i), Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
    }

    /** Pause screen, drawn as a full page so it uses the same title, list and selection bar as
     *  every other menu instead of a lone label floating over the frozen gameplay. */
    private void drawPause(Graphics g, int x, int y) {
        drawPageBackground(g, x, y);
        title(g, x, y, "PAUSED");
        list(g, x, y + PAUSE_LIST_Y, PAUSE_ITEMS, pauseIndex);
    }

    private void drawResults(Graphics g, int x, int y) {
        drawPageBackground(g, x, y);
        title(g, x, y, worldWasVictory ? "VICTORY" : "GAME OVER");
        ink(g);
        drawText(g, x + 26, y + 62, "Character: " + ContentCatalog.character(characterIndex).name, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 26, y + 82, "Stage: " + ContentCatalog.stage(stageIndex).name, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 26, y + 102, "Fans defeated: " + world.kills(), Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 26, y + 122, "Level: " + world.playerLevel() + "   Coins: " + world.coins(), Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 26, y + 142, "Time: " + timeString(world.elapsedSeconds()), Font.STYLE_PLAIN, Font.SIZE_SMALL);
        if (worldWasVictory && modeIndex == 0) drawText(g, x + 26, y + 168, "Stage clear recorded for this character.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        String[] items = { "Replay", "Return to Menu" };
        list(g, x, y + 185, items, resultIndex);
    }

    private void title(Graphics g, int x, int y, String text) {
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        drawText(g, x + 90, y + 18, text, Font.STYLE_BOLD, Font.SIZE_LARGE);
    }

    /** Shared selectable list. The selected row is marked the same way everywhere: the "> " arrow,
     *  bold style and a 2px cyan bar, exactly as the Main Menu does it. The palette has only one
     *  text colour that stays readable on the light panel, so the selection is carried by the marker,
     *  the weight and the bar rather than by a second text colour. */
    private void list(Graphics g, int x, int y, String[] items, int selected) {
        for (int i = 0; i < items.length; i++) {
            boolean on = i == selected;
            int rowY = y + i * 22;
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            drawText(g, x + 36, rowY, (on ? "> " : "  ") + items[i],
                    on ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            if (on) {
                g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
                g.fillRect(x, rowY + 2, 2, 12);
            }
        }
    }

    /** Sets the shared body-ink colour. Callers must use this before informational text, because
     *  drawText does not set a colour of its own and would otherwise inherit whatever the last
     *  drawing call left behind. */
    private void ink(Graphics g) { g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B); }

    /** Secondary labels. The palette exposes one readable text colour on the light panel, so this
     *  resolves to the same ink as ink(); labels and values stay separated by their bold/plain
     *  style and by the gutter, not by a second hue. */
    private void inkDim(Graphics g) { g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B); }

    private void drawText(Graphics g, int x, int y, String text, int style, int size) {
        g.setFont(Font.getFont(Font.FACE_SYSTEM, style, size));
        g.drawString(text, x, y, Graphics.TOP | Graphics.LEFT);
    }

    private String timeString(long seconds) {
        long m = seconds / 60L; long s = seconds % 60L;
        return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }

    private int wrap(int value, int max) {
        if (value < 0) return max - 1;
        if (value >= max) return 0;
        return value;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Small wrapper keeps menu portrait access explicit and null-safe. */
    private static final class ImageHolder {
        final javax.microedition.lcdui.Image image;
        ImageHolder(javax.microedition.lcdui.Image image) { this.image = image; }
    }
}
