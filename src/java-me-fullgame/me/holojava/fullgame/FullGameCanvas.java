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

    // Temporary HoloCure-style preview palette for the splash screen and Main Menu only. Every
    // other screen keeps its existing colours until the visual review settles on a final theme.
    private static final int SPLASH_SKY = 150, SPLASH_SKY_D = 190, SPLASH_SKY_DD = 225;
    private static final int SPLASH_INK = 255, SPLASH_INK_DIM = 235;
    private static final int MENU_BG = 236, MENU_BG_LOW = 206;
    private static final int MENU_SELECTED = 255;
    private static final int MENU_ITEM = 70;
    private static final int MENU_ACCENT = 90, MENU_ACCENT_G = 150, MENU_ACCENT_B = 235;

    // Splash/Main Menu layout, in logical 320x240 pixels.
    private static final int SPLASH_LOGO_X = 10, SPLASH_LOGO_Y = 42;
    private static final int SPLASH_PROMPT_Y = 196;
    private static final int SPLASH_HINT_Y = 216;
    private static final int MENU_LOGO_X = 10, MENU_LOGO_Y = 8;
    private static final int MENU_LIST_X = 14, MENU_LIST_Y = 112, MENU_ROW_PITCH = 21;
    private static final int MENU_ACHAN_X = 205, MENU_ACHAN_Y = 40;
    private static final int ACHAN_FRAME_MS = 260;

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
    private int lastMenuScreen = MENU;
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
        // Numeric action keys are deliberately explicit: 5 = skill, 9 = special.
        if (key == '5') skillPressed = true;
        if (key == '9') specialPressed = true;
        if (key == '1') attackPulse = true;
    }

    private void update(long now) {
        int keys = getKeyStates();
        boolean fire = (keys & FIRE_PRESSED) != 0;
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
            if (fireEdge && world.state() == RunWorld.STATE_RUNNING) world.pause();
            else if (fireEdge && world.state() == RunWorld.STATE_PAUSED) world.resume();
            if (world.state() != RunWorld.STATE_PAUSED) {
                world.update(keys, attackHeld || attackEdge || attackPulse,
                        skillPressed, specialPressed, now);
                attackPulse = false;
                skillPressed = false; specialPressed = false;
            }
            if (world.state() == RunWorld.STATE_LEVEL_UP) {
                if (upEdge) world.moveOffer(-1);
                if (downEdge) world.moveOffer(1);
                if (fireEdge) world.selectOffer();
            }
            if (world.state() == RunWorld.STATE_GAME_OVER || world.state() == RunWorld.STATE_VICTORY) {
                worldWasVictory = world.state() == RunWorld.STATE_VICTORY;
                if (worldWasVictory && modeIndex == 0) save.setCompleted(characterIndex, ContentCatalog.stage(stageIndex).order);
                screen = RESULTS;
                resultIndex = 0;
            }
            return;
        }
        if (screen == SPLASH) updateSplash(skillPressed || fireEdge, keys);
        else if (screen == MENU) updateMain(upEdge, downEdge, fireEdge);
        else if (screen == MODES) updateModes(upEdge, downEdge, fireEdge);
        else if (screen == CHARACTERS) updateCharacters(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == STAGES) updateStages(upEdge, downEdge, fireEdge, leftEdge);
        else if (screen == SETTINGS) updateSettings(upEdge, downEdge, fireEdge, leftEdge);
        else if (screen == CONTROLS) { if (leftEdge || fireEdge) screen = SETTINGS; }
        else if (screen == PREP) updatePrep(upEdge, downEdge, fireEdge, leftEdge);
        else if (screen == RESULTS) updateResults(upEdge, downEdge, fireEdge);
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

    private void updateModes(boolean up, boolean down, boolean fire) {
        // Stage Mode only for now. Endless and Time remain defined in ContentCatalog.MODES and
        // fully implemented in RunWorld; widening this list is the single point of re-enabling them.
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 2);
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

    private void updateResults(boolean up, boolean down, boolean fire) {
        if (up || down) resultIndex = wrap(resultIndex + (up ? -1 : 1), 2);
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

    private void drawMenuScreen(Graphics g, int x, int y) {
        if (screen == SPLASH) { drawSplash(g, x, y); return; }
        g.setColor(14, 20, 38); g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
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
        g.setColor(SPLASH_SKY_DD); g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        g.setColor(SPLASH_SKY_D); g.fillRect(x, y + 34, Viewport.MIN_W, 150);
        g.setColor(SPLASH_SKY); g.fillRect(x, y + 60, Viewport.MIN_W, 98);

        Image logo = assets.logo();
        if (logo != null) g.drawImage(logo, x + SPLASH_LOGO_X, y + SPLASH_LOGO_Y, Graphics.TOP | Graphics.LEFT);

        g.setColor(MENU_ACCENT, MENU_ACCENT_G, MENU_ACCENT_B);
        drawText(g, x + Viewport.MIN_W / 2, y + 12, "HOLOJAVA", Font.STYLE_BOLD, Font.SIZE_LARGE);
        g.setColor(SPLASH_INK);
        drawText(g, x + Viewport.MIN_W / 2, y + SPLASH_PROMPT_Y, "PRESS 5 TO START",
                Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        g.setColor(MENU_ITEM);
        drawText(g, x + Viewport.MIN_W / 2, y + SPLASH_HINT_Y, "Save the Fans!",
                Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Splash input. Key 5 (skillPressed) or a FIRE edge both leave the splash, so the prompt and
     * the hardware agree on how to start. The transition is one-way: screen is only ever assigned
     * SPLASH during construction, so there is no path back.
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
    private void clearInputLatches(int keys) {
        skillPressed = false;
        specialPressed = false;
        attackPulse = false;
        fireWasDown = (keys & FIRE_PRESSED) != 0;
        upWasDown = (keys & UP_PRESSED) != 0;
        downWasDown = (keys & DOWN_PRESSED) != 0;
        leftWasDown = (keys & LEFT_PRESSED) != 0;
        rightWasDown = (keys & RIGHT_PRESSED) != 0;
        attackWasDown = (keys & GAME_A_PRESSED) != 0;
    }

    /**
     * Main Menu with a temporary light-blue and white HoloCure theme: the logo sits above the
     * list on the left, A-chan decorates the right side, and the six entries keep their original
     * order, wrap behaviour and actions. A-chan is drawn from a time-derived frame index and is
     * never added to the selectable list, so it cannot be picked and cannot move the cursor.
     */
    private void drawMainMenu(Graphics g, int x, int y) {
        g.setColor(MENU_BG); g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        g.setColor(MENU_BG_LOW); g.fillRect(x, y + 84, Viewport.MIN_W, 108);

        Image logo = assets.menuLogo();
        if (logo != null) g.drawImage(logo, x + MENU_LOGO_X, y + MENU_LOGO_Y, Graphics.TOP | Graphics.LEFT);
        drawMenuAchan(g, x + MENU_ACHAN_X, y + MENU_ACHAN_Y, System.currentTimeMillis());

        g.setColor(MENU_ACCENT, MENU_ACCENT_G, MENU_ACCENT_B);
        drawText(g, x + MENU_LIST_X, y + 88, "HOLOJAVA", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        String[] items = { "Play", "Game Mode", "Characters", "Stage Select", "Settings", "Exit" };
        for (int i = 0; i < items.length; i++) {
            boolean selected = i == menuIndex;
            int rowY = y + MENU_LIST_Y + i * MENU_ROW_PITCH;
            g.setColor(selected ? MENU_SELECTED : MENU_ITEM,
                    selected ? MENU_SELECTED : MENU_ITEM,
                    selected ? MENU_SELECTED : MENU_ITEM);
            drawText(g, x + MENU_LIST_X + 4, rowY, (selected ? "> " : "  ") + items[i],
                    selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            if (selected) {
                g.setColor(MENU_ACCENT, MENU_ACCENT_G, MENU_ACCENT_B);
                g.fillRect(x + MENU_LIST_X, rowY + 2, 2, 12);
            }
        }
    }

    /** Draws the A-chan blink decoration. Purely visual: no input or state is affected. */
    private void drawMenuAchan(Graphics g, int x, int y, long now) {
        Image[] frames = assets.achanFrames();
        if (frames == null || frames.length == 0) return;
        Image frame = frames[(int) ((now / ACHAN_FRAME_MS) % frames.length)];
        if (frame != null) g.drawImage(frame, x, y, Graphics.TOP | Graphics.LEFT);
    }

    private void drawModes(Graphics g, int x, int y) {
        title(g, x, y, "GAME MODE");
        String[] items = { "Stage Mode", "Back" };
        list(g, x, y + 60, items, menuIndex);
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
            g.setColor(on ? 255 : 190, on ? 220 : 190, on ? 60 : 190);
            drawText(g, x + CHARS_LIST_X, y + CHARS_ROW_Y + (i - first) * CHARS_ROW_PITCH,
                    (on ? "> " : "  ") + c.id, on ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        if (menuIndex == count) {
            g.setColor(255, 230, 60);
            drawText(g, x + CHARS_LIST_X, y + CHARS_ROW_Y + (last - first) * CHARS_ROW_PITCH,
                    "> Back", Font.STYLE_BOLD, Font.SIZE_SMALL);
        }

        int selected = Math.min(menuIndex, count - 1);
        ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[selected];

        // Middle column: the selected character's portrait, native size, centred in the box.
        g.setColor(16, 20, 34);
        g.fillRect(x + CHARS_PORTRAIT_X, y + CHARS_PORTRAIT_Y, CHARS_PORTRAIT_W, CHARS_PORTRAIT_H);
        g.setColor(70, 80, 110);
        g.drawRect(x + CHARS_PORTRAIT_X, y + CHARS_PORTRAIT_Y, CHARS_PORTRAIT_W - 1, CHARS_PORTRAIT_H - 1);
        Image portrait = assets.characterPortrait(selected);
        if (portrait != null) {
            g.drawImage(portrait, x + CHARS_PORTRAIT_X + (CHARS_PORTRAIT_W - portrait.getWidth()) / 2,
                    y + CHARS_PORTRAIT_Y + (CHARS_PORTRAIT_H - portrait.getHeight()) / 2,
                    Graphics.TOP | Graphics.LEFT);
        }
        g.setColor(255, 255, 255);
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
        g.setColor(255, 255, 255);
        drawText(g, px, py, charsPanelName, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setColor(150, 150, 160);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 20, "HP", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 38, "ATK", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 56, "SPD", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 74, "CRT", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setColor(190, 190, 190);
        drawText(g, vx, py + 20, charsPanelHp, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 38, charsPanelAtk, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 56, charsPanelSpd, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 74, charsPanelCrit, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setColor(110, 130, 180);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 98, "Weapon", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 98, charsPanelWeapon, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setColor(150, 200, 150);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 116, "Skill", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 116, charsPanelSkill, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 134, "Special", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 134, charsPanelSpecial, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setColor(140, 140, 140);
        drawText(g, vx - CHARS_PANEL_GUTTER, py + 158, "Art", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, vx, py + 158, charsPanelArt, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    // Map/stage status colours. Yellow always means unlocked-and-allowed; dark grey always means
    // unavailable. White is deliberately not a status colour: the selected row is marked by the
    // "> " arrow, bold style and an outline frame instead of a third colour.
    private static final int STAGE_UNLOCKED_R = 255, STAGE_UNLOCKED_G = 240, STAGE_UNLOCKED_B = 120;
    private static final int STAGE_LOCKED = 90;
    private static final int STAGE_FRAME = 170;
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
            else g.setColor(STAGE_LOCKED, STAGE_LOCKED, STAGE_LOCKED);
            String prefix = selected ? "> " : "  ";
            String suffix = !allowed ? " [mode]" : (!unlocked ? " [locked]" : "");
            drawText(g, x + STAGE_ROW_X, rowY, prefix + s.name + suffix,
                    selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            if (selected) {
                g.setColor(STAGE_FRAME, STAGE_FRAME, STAGE_FRAME);
                g.drawRect(x + STAGE_FRAME_X, rowY - 2, STAGE_FRAME_W, STAGE_FRAME_H);
            }
        }
        g.setColor(200, 200, 200);
        drawText(g, x + 12, y + 214, "Hard and Time stages are not available yet.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawSettings(Graphics g, int x, int y) {
        title(g, x, y, "SETTINGS");
        String[] items = { "Hardcore: " + (save.hardcore() ? "ON" : "OFF"),
                "Audio: OFF (runtime not verified)", "Controls", "Back" };
        list(g, x, y + 65, items, menuIndex);
        drawText(g, x + 20, y + 190, "Hardcore sets Endless player HP to 1.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 20, y + 210, "Persistence is optional RMS storage.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawControls(Graphics g, int x, int y) {
        title(g, x, y, "CONTROLS");
        drawText(g, x + 24, y + 60, "D-pad: move / menu navigation", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 82, "GAME A or 1: manual basic attack", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 104, "FIRE: confirm / pause / resume", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 126, "5: skill   9: special attack", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 158, "Orbiting and passive weapons may fire autonomously.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawPrep(Graphics g, int x, int y) {
        title(g, x, y, "RUN PREPARATION");
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

    private void drawPause(Graphics g, int x, int y) {
        // Box shrunk to wrap the single remaining label; the old 62px height was sized for the
        // removed "FIRE resume" hint and would leave an empty band below the text.
        g.setColor(0, 0, 0); g.fillRect(x + 80, y + 99, 160, 40);
        drawText(g, x + 130, y + 110, "PAUSED", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
    }

    private void drawResults(Graphics g, int x, int y) {
        g.setColor(14, 20, 38); g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        title(g, x, y, worldWasVictory ? "VICTORY" : "GAME OVER");
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
        g.setColor(255, 240, 120);
        drawText(g, x + 90, y + 18, text, Font.STYLE_BOLD, Font.SIZE_LARGE);
    }

    private void list(Graphics g, int x, int y, String[] items, int selected) {
        for (int i = 0; i < items.length; i++) {
            g.setColor(i == selected ? 255 : 180, i == selected ? 230 : 180, i == selected ? 60 : 180);
            drawText(g, x + 36, y + i * 22, (i == selected ? "> " : "  ") + items[i], i == selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
    }

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
