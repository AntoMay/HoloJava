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
    private static final int SHOP = 10;     // Toko submenu: Beli Upgrade / Armory / Keluar
    private static final int UPGRADES = 11; // Beli Upgrade: the three-tab purchase page
    private static final int ARMORY = 12;   // weapon and item catalogue, view-only while locked
    private static final int LOCKED = 13;   // placeholder page shared by the three shut entries
    private static final int CREDITS = 14;
    /**
     * Main menu rows, in draw order: Papan Peringkat, Pencapaian, Toko, Main, Holo House,
     * Pengaturan, Credits. The start-up flow lives behind Main — Main to Mode to Karakter to
     * Stage — so characters and stages are no longer entries of their own, and Exit lives on the
     * Pengaturan page instead of terminating the game from the menu.
     */
    private static final int MAIN_ITEM_COUNT = 7;
    private static final int MAIN_LEADERBOARD = 0;
    private static final int MAIN_ACHIEVEMENTS = 1;
    private static final int MAIN_TOKO = 2;
    private static final int MAIN_PLAY = 3;
    private static final int MAIN_HOLOHOUSE = 4;
    private static final int MAIN_SETTINGS = 5;
    private static final int MAIN_CREDITS = 6;
    private static final int CHARACTER_PAGE_SIZE = 8;

    /**
     * Coin top-up applied when the game boots, while the coin economy is still being designed.
     *
     * <p>Deliberately kept out of {@code SaveStore} on purpose: that class owns the real balance
     * semantics and its tests pin a fresh install to zero, so the starting capital is a game-layer
     * policy instead of a persistence change. Deleting this constant and the single call to
     * {@code ensureStartingCapital()} restores a zero balance with no other edit.
     */
    static final long SHOP_STARTING_CAPITAL = 9999999L;
    /** Rows the shop draws per screen; the rest are reached by scrolling. */
    private static final int SHOP_PAGE_SIZE = 6;

    /**
     * Gameplay key bindings.
     *
     * <p>Basic attack lives on GAME_A and 1. FIRE and 5 are pause in gameplay.
     * Menu confirm uses FIRE, L, R. Level-up confirm uses FIRE.
     * 9 is special, 0 is alternate pause. There is no skill key: every character's skill fires
     * on its own during the run, and the special needs the Special Attack upgrade to respond.
     */
    private static final int KEY_ATTACK = '5';       // pause in gameplay (also attack alt in old scheme)
    private static final int KEY_ATTACK_ALT = '1';   // basic attack
    private static final int KEY_PAUSE = '0';        // alternate pause
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
    private static final int MENU_BODY_Y = 40;     // header band height; all content starts below

    // Splash/Main Menu layout, in logical 320x240 pixels. Both screens now sit on the reference
    // game's star-stage backdrop (ui/title_bg.png), so their text is palette white — the one
    // screen family where white clears the contrast bar, because the backdrop's dark blue puts
    // it above 14:1 while the light page palette below keeps its ink-on-light rule. TITLE_DEEP
    // is the fallback fill from the same colour family for partial builds without the art.
    private static final int TITLE_TEXT_R = 255, TITLE_TEXT_G = 255, TITLE_TEXT_B = 255;
    private static final int TITLE_DEEP_R = 10, TITLE_DEEP_G = 22, TITLE_DEEP_B = 49;
    private static final int SPLASH_LOGO_X = 10, SPLASH_LOGO_Y = 42;
    private static final int SPLASH_PROMPT_Y = 196;
    // The logo floats over the top of the roster collage, centred: 320 - 176 = 144, so x = 72.
    private static final int MENU_LOGO_X = 72, MENU_LOGO_Y = 2;
    // Bottom icon bar, rebuilt after the reference's horizontal entry bar. The icons keep their
    // native halved sizes and align on one baseline; the gap is what recentres the bar after any
    // art change, so both stay named constants instead of spreading through the draw code.
    private static final int TITLE_CAST_Y = 64;
    private static final int TITLE_BAR_BASE = 202;
    private static final int TITLE_BAR_X = 40, TITLE_ICON_GAP = 23;
    private static final int TITLE_LABEL_X = 200, TITLE_LABEL_Y = 206, TITLE_LABEL_W = 116;
    private static final int TITLE_HINT_Y = 228;
    // The seven bar entries, in bar order, mirroring MAIN_* above. The bar reads left to right
    // like the reference, so this order is also the sweep order of Up/Down/Left/Right.
    private static final String[] MAIN_ITEMS = { "Papan Peringkat", "Pencapaian", "Toko",
            "Main", "Holo House", "Pengaturan", "Credits" };
    // Pause menu. Two entries at the shared 22px list pitch span 44px, so 98 centres them on the
    // 240px page. Index 0 is Resume so the menu always opens on the safe choice.
    private static final String[] PAUSE_ITEMS = { "Resume", "Return to Menu" };
    private static final int PAUSE_LIST_Y = 98;

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
    /** First visible list row, so a long list scrolls while menuIndex stays the highlight. */
    private int shopScroll;
    /** Which shop tab the purchase page shows: 0 Ability, 1 Stat, 2 Lainnya. */
    private int shopTab;
    /** A pending Refund All confirmation, cancelled by any input that moves off the row. */
    private boolean refundConfirm;
    /** Heading of the currently open placeholder page (Papan Peringkat / Pencapaian / Holo House). */
    private String lockedTitle = "TERKUNCI";
    /**
     * Animation clock for the title screens. update(long) records the render loop's tick here
     * once per frame, so the draw code only ever reads it and never calls the clock itself; the
     * value stays 0 in headless tests, which freeze every bob and blink at its start pose.
     */
    private long clockMs;
    /** Which entry the gold label box is currently showing, and when its slide-in started. */
    private int labelIndex = -1;
    private long labelShownAt;
    private boolean fireWasDown;
    private boolean upWasDown;
    private boolean downWasDown;
    private boolean leftWasDown;
    private boolean rightWasDown;
    // keyPressed() runs on the device event thread while update() runs on the game thread, so
    // these latches must be volatile or the press can be missed entirely. The other flags
    // below follow the same convention for the same reason.
    private volatile boolean specialPressed;
    private boolean attackWasDown;
    private volatile boolean attackPulse;
    private volatile boolean pausePressed;
    private volatile boolean pauseFrom5;  // latch for numeric '5' key press (pause in gameplay)
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
        if (key == KEY_ATTACK) {
            attackPulse = true;      // attack in gameplay
            pauseFrom5 = true;       // pause in gameplay (FIRE/5 both pause)
        }
        if (key == KEY_ATTACK_ALT) attackPulse = true;
        if (key == KEY_SPECIAL) specialPressed = true;
        if (key == KEY_PAUSE) pausePressed = true;
    }

    private void update(long now) {
        clockMs = now;
        // FIRE is now Pause (reverted from interactive basic attack)
        // Attack comes from keys 5 and 1 only; FIRE triggers pause
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
            if (world.state() == RunWorld.STATE_PAUSED) {
                // The simulation is frozen, so the pause menu owns the navigation keys here. L and R
                // are deliberately not read on this path: they stay player movement in gameplay.
                updatePause(upEdge, downEdge, fireEdge || pausePulse());
            } else {
                if (world.state() == RunWorld.STATE_RUNNING) {
                    // Pause trigger: FIRE edge or numeric '5' or KEY_PAUSE ('0')
                    boolean pauseTrigger = fireEdge || pauseFrom5 || pausePressed;
                    pauseFrom5 = false;  // consume '5' latch
                    pausePressed = false; // consume '0' latch
                    if (pauseTrigger) {
                        world.pause();
                        pauseIndex = 0;
                    }
                }
                if (world.state() != RunWorld.STATE_PAUSED) {
                    // FIRE is Pause (reverted from interactive basic attack)
                    // Attack: 5 and 1 only; this control is now used for pause
                    // the one control every MIDP handset is guaranteed to have. The level-up screen
                    // below still confirms on FIRE, and RunWorld.update ignores a non-running state,
                    // so a confirm there never also fires a weapon.
                    world.update(keys, attackHeld || attackEdge || attackPulse,
                            specialPressed, now);
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
            specialPressed = false;
            pausePressed = false;
            pauseFrom5 = false;
            return;
        }
        // L/R map onto the two menu roles the d-pad directions do not already own. A direction is
        // Back on the screens that already treat it that way and Select on the rest, so a single
        // press can never both confirm a choice and leave the screen. The Main Menu is the one
        // exception: its entry bar is horizontal like the reference's, so L/R sweep it exactly
        // like Up/Down and only FIRE confirms.
        if (screen == SPLASH) updateSplash(fireEdge || leftEdge || rightEdge, keys);
        else if (screen == MENU) updateMain(upEdge || leftEdge, downEdge || rightEdge, fireEdge);
        else if (screen == MODES) updateModes(upEdge, downEdge, fireEdge || leftEdge, rightEdge);
        else if (screen == CHARACTERS) updateCharacters(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == SHOP) updateShop(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == UPGRADES) updateUpgrades(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == ARMORY) updateArmory(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == LOCKED) updateLocked(fireEdge || leftEdge || rightEdge);
        else if (screen == CREDITS) updateCredits(fireEdge || leftEdge || rightEdge);
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
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), MAIN_ITEM_COUNT);
        if (!fire) return;
        switch (menuIndex) {
            case MAIN_LEADERBOARD: openLocked("PAPAN PERINGKAT"); break;
            case MAIN_ACHIEVEMENTS: openLocked("PENCAPAIAN"); break;
            case MAIN_TOKO: screen = SHOP; menuIndex = 0; shopScroll = 0; refundConfirm = false; break;
            // Main opens the start-up flow: Mode, then Karakter, then Stage. Each step advances
            // on FIRE and steps back one screen, so starting a run is a straight walk down.
            case MAIN_PLAY: screen = MODES; menuIndex = modeIndex; break;
            case MAIN_HOLOHOUSE: openLocked("HOLO HOUSE"); break;
            case MAIN_SETTINGS: screen = SETTINGS; menuIndex = 0; break;
            case MAIN_CREDITS: screen = CREDITS; break;
            default: break;
        }
    }

    /** Opens one of the placeholder pages. menuIndex is left alone so Back lands on the same row. */
    private void openLocked(String title) {
        lockedTitle = title;
        screen = LOCKED;
    }

    private void updateModes(boolean up, boolean down, boolean fire, boolean back) {
        // Stage Mode only for now. Endless and Time remain defined in ContentCatalog.MODES and
        // fully implemented in RunWorld; widening this list is the single point of re-enabling them.
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 2);
        if (back) { screen = MENU; menuIndex = MAIN_PLAY; return; }
        if (!fire) return;
        if (menuIndex == 1) { screen = MENU; menuIndex = MAIN_PLAY; return; }
        // Choosing a mode continues to character select: Main -> Mode -> Karakter -> Stage.
        modeIndex = 0;
        screen = CHARACTERS;
        menuIndex = characterIndex;
        characterPage = characterIndex / CHARACTER_PAGE_SIZE;
    }

    /**
     * Karakter step of the Main to Mode to Karakter to Stage flow. FIRE picks the highlighted
     * character, pins it in the save and continues to Stage Select with the current stage
     * highlighted; L/R page through the roster, with L on the first page and the trailing Back
     * row stepping back up to Game Mode.
     */
    private void updateCharacters(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        int count = ContentCatalog.CHARACTERS.length + 1;
        if (left) {
            if (characterPage > 0) { characterPage--; menuIndex = Math.max(0, menuIndex - CHARACTER_PAGE_SIZE); }
            else backToModes();
            return;
        }
        if (right) {
            int lastPage = (ContentCatalog.CHARACTERS.length - 1) / CHARACTER_PAGE_SIZE;
            if (characterPage < lastPage) { characterPage++; menuIndex = Math.min(count - 1, menuIndex + CHARACTER_PAGE_SIZE); }
            return;
        }
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), count);
        if (!fire) return;
        if (menuIndex == ContentCatalog.CHARACTERS.length) { backToModes(); return; }
        characterIndex = menuIndex;
        characterPage = characterIndex / CHARACTER_PAGE_SIZE;
        save.setSelectedCharacter(characterIndex);
        screen = STAGES;
        menuIndex = stageRowForSelection();
    }

    private void backToModes() {
        screen = MODES;
        menuIndex = modeIndex;
    }

    private void backToCharacters() {
        screen = CHARACTERS;
        menuIndex = characterIndex;
        characterPage = characterIndex / CHARACTER_PAGE_SIZE;
    }

    /** Stage Select row for the stage a run would start with right now. */
    private int stageRowForSelection() {
        int[] stages = selectableStages();
        for (int slot = 0; slot < stages.length; slot++) if (stages[slot] == stageIndex) return slot;
        return 0;
    }

    /**
     * Toko input: three entries — Beli Upgrade, Armory, Keluar. Keluar returns to the main menu,
     * and so does L/R from anywhere on the page, matching every other submenu.
     */
    private void updateShop(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        if (left || right) { screen = MENU; menuIndex = MAIN_TOKO; return; }
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 3);
        if (!fire) return;
        if (menuIndex == 0) { screen = UPGRADES; shopTab = 0; menuIndex = 0; shopScroll = 0; refundConfirm = false; }
        else if (menuIndex == 1) { screen = ARMORY; menuIndex = 0; shopScroll = 0; }
        else { screen = MENU; menuIndex = MAIN_TOKO; }
    }

    /**
     * Beli Upgrade input: L/R cycle the three tabs (Ability, Stat, Lainnya), up/down move the
     * highlight inside the tab — the tab's own rows plus a trailing Kembali row — and FIRE buys.
     * A pending refund confirmation is cancelled by any input that moves the cursor, so the
     * destructive action can only ever land on a second deliberate FIRE on its own row.
     */
    private void updateUpgrades(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        int rows = ContentCatalog.upgradeGroupCount(shopTab) + 1;
        if (left || right) {
            shopTab = (shopTab + (left ? -1 : 1) + 3) % 3;
            refundConfirm = false;
            menuIndex = 0;
            shopScroll = 0;
            return;
        }
        if (up || down) {
            refundConfirm = false;
            menuIndex = wrap(menuIndex + (up ? -1 : 1), rows);
            keepShopRowVisible(rows);
        }
        if (!fire) return;
        if (menuIndex == rows - 1) {          // the trailing Kembali row
            refundConfirm = false;
            screen = SHOP;
            menuIndex = 0;
            return;
        }
        buyUpgrade(ContentCatalog.upgradeGroupStart(shopTab) + menuIndex);
    }

    /**
     * Armory input: browse the whole weapon and item catalogue. Enabling or disabling any row
     * needs the Kustomisasi upgrade, which is locked in this build, so FIRE deliberately does
     * nothing and L/R leave the page — the catalogue is a view, not yet a switchboard.
     */
    private void updateArmory(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        int rows = ContentCatalog.WEAPONS.length + ContentCatalog.ITEMS.length;
        if (left || right) { screen = SHOP; menuIndex = 1; return; }
        if (up || down) {
            menuIndex = wrap(menuIndex + (up ? -1 : 1), rows);
            keepShopRowVisible(rows);
        }
    }

    /** Placeholder pages exist but stay shut: any confirming input returns to the menu. */
    private void updateLocked(boolean back) {
        if (back) screen = MENU;
    }

    private void updateCredits(boolean back) {
        if (back) screen = MENU;
    }

    /** Keeps the highlighted row inside the visible window as a list of the given length scrolls. */
    private void keepShopRowVisible(int rows) {
        int maxScroll = Math.max(0, rows - SHOP_PAGE_SIZE);
        if (menuIndex < shopScroll) shopScroll = menuIndex;
        if (menuIndex >= shopScroll + SHOP_PAGE_SIZE) shopScroll = menuIndex - SHOP_PAGE_SIZE + 1;
        if (shopScroll > maxScroll) shopScroll = maxScroll;
        if (shopScroll < 0) shopScroll = 0;
    }

    /**
     * Resolves one FIRE press on an upgrade row.
     *
     * <p>Locked rows are data for the page only: their wiki prices are shown, but no purchase can
     * land. Refund All asks once and then zeroes every row while crediting the full price back in
     * one write. Hardcore toggles the settings bit itself, so the shop and the Pengaturan page can
     * never disagree. A levelled row spends its coins first and writes the level only after that
     * write committed, exactly as before: a storage failure costs the coins, never the level, and
     * a short balance, a maxed row or a free (0-coin) row each leave the record untouched.
     */
    private void buyUpgrade(int upgradeIndex) {
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(upgradeIndex);
        if (def == null || def.locked) return;
        if (def.buyKind == ContentCatalog.BUY_ACTION) {
            if (refundConfirm) { refundConfirm = false; save.refundAll(); }
            else refundConfirm = true;
            return;
        }
        refundConfirm = false;
        if (def.buyKind == ContentCatalog.BUY_TOGGLE) {
            save.setHardcore(!save.hardcore());
            return;
        }
        int level = save.upgradeLevel(upgradeIndex);
        long cost = ContentCatalog.upgradeCost(def, level);
        if (cost < 0) return;                            // already maxed
        if (cost > 0 && !save.spendCoins(cost)) return;  // free rows never touch the balance
        save.setUpgradeLevel(upgradeIndex, level + 1);
    }

    private void updateStages(boolean up, boolean down, boolean fire, boolean left) {
        int[] stages = selectableStages();
        int count = stages.length + 1;
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), count);
        // Stage is the last step of the flow, so Back and the trailing row step up to Karakter.
        if (left) { backToCharacters(); return; }
        if (!fire) return;
        if (menuIndex == stages.length) { backToCharacters(); return; }
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

    private void updateSettings(boolean up, boolean down, boolean fire, boolean left) {
        // Hardcore, Audio, Controls, Exit, Back. Exit moved here from the main menu so the menu
        // itself is pure navigation and the shutdown path lives with the other risky choices.
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 5);
        if (left) { screen = MENU; menuIndex = MAIN_SETTINGS; return; }
        if (!fire) return;
        if (menuIndex == 0) save.setHardcore(!save.hardcore());
        else if (menuIndex == 1) { /* audio is deliberately disabled on unverified runtimes */ }
        else if (menuIndex == 2) screen = CONTROLS;
        else if (menuIndex == 3) { exitRequested = true; stopped = true; shutdownRequest.requestShutdown(); }
        else { screen = MENU; menuIndex = MAIN_SETTINGS; }
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
        else { screen = MENU; menuIndex = MAIN_PLAY; world = null; }
    }

    private void updateResults(boolean up, boolean down, boolean fire, boolean back) {
        if (up || down) resultIndex = wrap(resultIndex + (up ? -1 : 1), 2);
        if (back) { screen = MENU; menuIndex = MAIN_PLAY; world = null; return; }
        if (!fire) return;
        if (resultIndex == 0) startRun();
        else { screen = MENU; menuIndex = MAIN_PLAY; world = null; }
    }

    private void startRun() {
        if (!stageUnlocked(stageIndex) || !stageAllowed(stageIndex)) { screen = STAGES; return; }
        world = new RunWorld(assets, save, characterIndex, modeIndex, stageIndex);
        world.setViewport(viewport);
        screen = GAME;
        pauseFrom5 = false;  // clear '5' latch so it doesn't trigger pause immediately after start
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
        else if (screen == SHOP) drawShop(g, x, y);
        else if (screen == UPGRADES) drawUpgrades(g, x, y);
        else if (screen == ARMORY) drawArmory(g, x, y);
        else if (screen == LOCKED) drawLocked(g, x, y);
        else if (screen == CREDITS) drawCredits(g, x, y);
        else if (screen == STAGES) drawStages(g, x, y);
        else if (screen == SETTINGS) drawSettings(g, x, y);
        else if (screen == CONTROLS) drawControls(g, x, y);
        else if (screen == PREP) drawPrep(g, x, y);
    }

    /**
     * Splash screen. Drawn entirely inside the canvas as a screen state rather than as a separate
     * Displayable, so the MIDlet lifecycle and the single render thread stay untouched.
     *
     * <p>Shares the star-stage backdrop with the Main Menu so the boot flow reads as one piece
     * with it: the logo drifts gently on the clock, the game name sits below it, and the start
     * prompt blinks on the half-second the way the reference's does. TITLE_DEEP fills in when
     * the backdrop asset is missing, which keeps the white title text above the contrast bar
     * even in a partial build.
     */
    private void drawSplash(Graphics g, int x, int y) {
        Image bg = assets.titleBg();
        if (bg != null) {
            g.drawImage(bg, x, y, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(TITLE_DEEP_R, TITLE_DEEP_G, TITLE_DEEP_B);
            g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        }

        Image logo = assets.logo();
        if (logo != null) {
            g.drawImage(logo, x + SPLASH_LOGO_X, y + SPLASH_LOGO_Y + bob(700, 2),
                    Graphics.TOP | Graphics.LEFT);
        }

        g.setColor(TITLE_TEXT_R, TITLE_TEXT_G, TITLE_TEXT_B);
        drawCentered(g, x, y + 172, "HOLOJAVA", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        // Prompt centered horizontally, near bottom with margin. It blinks so a player waiting on
        // the screen can tell the loop is alive; frozen headless (clockMs 0) it stays visible.
        if ((clockMs / 500L) % 2L == 0L) {
            drawCentered(g, x, y + SPLASH_PROMPT_Y, "PRESS 5 TO START",
                    Font.STYLE_BOLD, Font.SIZE_SMALL);
        }
    }

    /**
     * Splash input. FIRE, L and R all leave the splash, so the prompt and the accepted keys agree
     * on how to start. The transition is one-way: screen is only ever assigned SPLASH during
     * construction, so there is no path back.
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
        specialPressed = false;
        attackPulse = false;
        pausePressed = false;
        pauseFrom5 = false;
        fireWasDown = (keys & FIRE_PRESSED) != 0;
        upWasDown = (keys & UP_PRESSED) != 0;
        downWasDown = (keys & DOWN_PRESSED) != 0;
        leftWasDown = (keys & LEFT_PRESSED) != 0;
        rightWasDown = (keys & RIGHT_PRESSED) != 0;
        attackWasDown = (keys & GAME_A_PRESSED) != 0;
    }

    /**
     * Main Menu, rebuilt after the reference game's title screen: the star-stage backdrop fills
     * the screen, the roster collage stands behind a gently floating logo, and the seven entries
     * live in an icon bar along the bottom instead of a vertical list. The selected icon lifts
     * off the bar and bobs on the clock, a white underline marks it for anyone who cannot track
     * the motion, and the gold label box slides the entry's name in at the bottom right above
     * the confirm hint. All motion reads clockMs, recorded once per tick by update(long), so
     * this method stays pure and a headless clock of 0 simply freezes every effect at rest.
     */
    private void drawMainMenu(Graphics g, int x, int y) {
        Image bg = assets.titleBg();
        if (bg != null) {
            g.drawImage(bg, x, y, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(TITLE_DEEP_R, TITLE_DEEP_G, TITLE_DEEP_B);
            g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        }
        Image cast = assets.menuCast();
        if (cast != null) g.drawImage(cast, x, y + TITLE_CAST_Y, Graphics.TOP | Graphics.LEFT);

        Image logo = assets.menuLogo();
        if (logo != null) {
            g.drawImage(logo, x + MENU_LOGO_X, y + MENU_LOGO_Y + bob(600, 3),
                    Graphics.TOP | Graphics.LEFT);
        }
        g.setColor(TITLE_TEXT_R, TITLE_TEXT_G, TITLE_TEXT_B);
        drawCentered(g, x, y + MENU_LOGO_Y + 76, "HOLOJAVA", Font.STYLE_BOLD, Font.SIZE_SMALL);
        drawRight(g, x, y + 4, "version 0.1");

        drawBar(g, x, y);

        // Gold label box with the highlighted entry's name, bottom right above the hint. Changing
        // the selection restarts the short slide-in so the label visibly answers the input.
        if (menuIndex != labelIndex) { labelIndex = menuIndex; labelShownAt = clockMs; }
        int slide = (int) ((clockMs - labelShownAt) / 25L);
        if (slide > 6) slide = 6;
        int labelY = y + TITLE_LABEL_Y + (6 - slide);
        String name = MAIN_ITEMS[menuIndex];
        Image label = assets.menuLabel();
        if (label != null) g.drawImage(label, x + TITLE_LABEL_X, labelY, Graphics.TOP | Graphics.LEFT);
        g.setColor(TITLE_TEXT_R, TITLE_TEXT_G, TITLE_TEXT_B);
        Font labelFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        g.setFont(labelFont);
        g.drawString(name, x + TITLE_LABEL_X + (TITLE_LABEL_W - labelFont.stringWidth(name)) / 2,
                labelY + 6, Graphics.TOP | Graphics.LEFT);

        drawRight(g, x, y + TITLE_HINT_Y, "SETUJU: 5/FIRE");
    }

    /**
     * The seven-entry icon bar. Icons keep their native (asset-prepared, halved) sizes and align
     * on one baseline, so each entry's slot is its own width plus the shared gap. A missing icon
     * still reserves its slot, which keeps the bar's centre stable if an asset fails to load.
     */
    private void drawBar(Graphics g, int x, int y) {
        int barX = TITLE_BAR_X;
        for (int i = 0; i < MAIN_ITEM_COUNT; i++) {
            Image icon = assets.menuIcon(i);
            int slot = (icon != null ? icon.getWidth() : 12) + TITLE_ICON_GAP;
            if (icon != null) {
                boolean selected = i == menuIndex;
                int lift = selected ? 3 + bob(180, 2) : 0;
                g.drawImage(icon, x + barX, y + TITLE_BAR_BASE - icon.getHeight() - lift,
                        Graphics.TOP | Graphics.LEFT);
                if (selected) {
                    g.setColor(TITLE_TEXT_R, TITLE_TEXT_G, TITLE_TEXT_B);
                    g.fillRect(x + barX - 1, y + TITLE_BAR_BASE + 1, icon.getWidth() + 2, 2);
                }
            }
            barX += slot;
        }
    }

    /** Sinusoidal offset in [-range, range] for the given period, in whole pixels. */
    private int bob(int periodMs, int range) {
        return (int) (Math.sin((double) clockMs / periodMs) * range);
    }

    /** Centred title text; the caller sets the colour first, as drawText does. */
    private void drawCentered(Graphics g, int x, int y, String text, int style, int size) {
        Font f = Font.getFont(Font.FACE_SYSTEM, style, size);
        g.setFont(f);
        g.drawString(text, x + (Viewport.MIN_W - f.stringWidth(text)) / 2, y,
                Graphics.TOP | Graphics.LEFT);
    }

    /** Small right-aligned label against the 320px edge; the caller sets the colour first. */
    private void drawRight(Graphics g, int x, int y, String text) {
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        g.drawString(text, x + Viewport.MIN_W - f.stringWidth(text) - 4, y,
                Graphics.TOP | Graphics.LEFT);
    }

    private void drawModes(Graphics g, int x, int y) {
        title(g, x, y, "GAME MODE");
        String[] items = { "Stage Mode", "Back" };
        list(g, x, y + 60, items, menuIndex);
        ink(g);
        drawText(g, x + 20, y + 195, ContentCatalog.MODES[modeIndex].objective, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 20, y + 210, "Endless & Time belum tersedia. L/R: kembali.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
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
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        int tailY = y + CHARS_ROW_Y + (last - first) * CHARS_ROW_PITCH;
        // One trailing row below the roster: Back. The shop lives on the main menu now.
        drawText(g, x + CHARS_LIST_X, tailY,
                (menuIndex == count ? "> " : "  ") + "Back",
                menuIndex == count ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);

        // The panel follows the SELECTED character — the one a click pinned — not the moving
        // cursor: moving down the list changes nothing until the player clicks, which is exactly
        // the "click to see its status beside the list" behaviour this screen has now.
        int selected = Math.max(0, Math.min(characterIndex, count - 1));
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

    /**
     * Toko page, reached from the main menu: the submenu Beli Upgrade / Armory / Keluar with the
     * live global balance above it. Every purchase now lives one level deeper — the old single
     * "Shop" page is gone, and its Skill Pasif row with it, because the skill is free forever now.
     */
    private void drawShop(Graphics g, int x, int y) {
        title(g, x, y, "TOKO");
        ink(g);
        drawText(g, x + 12, y + 30, "Koin: " + save.coinBalance(), Font.STYLE_PLAIN, Font.SIZE_SMALL);
        String[] items = { "Beli Upgrade", "Armory", "Keluar" };
        list(g, x, y + 70, items, menuIndex);
        ink(g);
        drawText(g, x + 12, y + 176, "Beli Upgrade: tab Ability / Stat / Lainnya.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 12, y + 194, "Armory: katalog senjata & item.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 12, y + 214, "FIRE: pilih    L/R: kembali", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Beli Upgrade page: three tabs over the full 34-row catalog in wiki order, six rows at a
     * time, with a detail panel pinned to the bottom. Every row is drawn — locked ones too, with
     * their wiki price — so the shop reads like the real thing instead of hiding its content
     * until it exists. Colour follows the rest of the menus: ink when the row can be bought,
     * the recessive cyan accent when it cannot, and words ("MAX", "gratis", "[Terkunci]") so
     * colour is never the only signal.
     */
    private void drawUpgrades(Graphics g, int x, int y) {
        title(g, x, y, "BELI UPGRADE");
        long balance = save.coinBalance();
        ink(g);
        drawText(g, x + 12, y + 30, "Koin: " + balance, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        // Tab strip: the active tab wears brackets, so the position is readable without colour.
        String[] tabs = { "Ability", "Stat", "Lainnya" };
        String strip = "";
        for (int t = 0; t < tabs.length; t++) {
            strip += (t == shopTab ? "[" + tabs[t] + "]" : " " + tabs[t] + " ") + "  ";
        }
        ink(g);
        drawText(g, x + 12, y + 46, strip, Font.STYLE_BOLD, Font.SIZE_SMALL);

        int start = ContentCatalog.upgradeGroupStart(shopTab);
        int count = ContentCatalog.upgradeGroupCount(shopTab);
        int rows = count + 1;
        for (int slot = 0; slot < SHOP_PAGE_SIZE; slot++) {
            int i = shopScroll + slot;
            if (i >= rows) break;
            int rowY = y + 66 + slot * 20;
            boolean selected = i == menuIndex;
            if (i < count) drawUpgradeRow(g, x, rowY, start + i, selected, balance);
            else {
                ink(g);
                drawText(g, x + 12, rowY, (selected ? "> " : "  ") + "Kembali",
                        selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            }
            if (selected) {
                g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
                g.drawRect(x + 8, rowY - 2, 288, 14);
            }
        }

        // Detail panel: separator, the highlighted row's full text, and the key hint — replaced
        // by the refund confirmation while one is pending, so the destructive second press is
        // announced on the exact screen where it will land.
        g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
        g.drawLine(x + 8, y + 184, x + 312, y + 184);
        ink(g);
        if (menuIndex >= 0 && menuIndex < count) {
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(start + menuIndex);
            int level = shopRowLevel(def, start + menuIndex);
            drawText(g, x + 12, y + 190, def.name + "   Lv " + level + "/" + def.maxLevel,
                    Font.STYLE_BOLD, Font.SIZE_SMALL);
            drawText(g, x + 12, y + 206, def.desc, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } else {
            drawText(g, x + 12, y + 190, "Kembali ke menu Toko.", Font.STYLE_BOLD, Font.SIZE_SMALL);
            drawText(g, x + 12, y + 206, "Upgrade bersifat permanen.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        if (refundConfirm) {
            g.setColor(STAGE_LOCKED_R, STAGE_LOCKED_G, STAGE_LOCKED_B);
            drawText(g, x + 12, y + 222, "Refund All? FIRE: ya. Geser: batal.",
                    Font.STYLE_BOLD, Font.SIZE_SMALL);
        } else {
            ink(g);
            drawText(g, x + 12, y + 222, "FIRE: beli  L/R: ganti tab  akhir: kembali",
                    Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
    }

    /** Level a row displays: Hardcore reads the settings bit, actions have no level. */
    private int shopRowLevel(ContentCatalog.UpgradeDef def, int upgradeIndex) {
        if (def == null) return 0;
        if (def.buyKind == ContentCatalog.BUY_TOGGLE) return save.hardcore() ? 1 : 0;
        if (def.buyKind == ContentCatalog.BUY_ACTION) return 0;
        return save.upgradeLevel(upgradeIndex);
    }

    /** One purchase row: name, level against cap, and price — or the locked/toggle state. */
    private void drawUpgradeRow(Graphics g, int x, int rowY, int upgradeIndex, boolean selected, long balance) {
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(upgradeIndex);
        if (def == null) return;
        int style = selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN;
        String label = (selected ? "> " : "  ") + def.name;
        if (def.locked) {
            // Not for sale yet, but shown with everything else: the page is complete, only the
            // gameplay behind these rows is missing. "[Terkunci]" says it beyond any colour.
            g.setColor(STAGE_LOCKED_R, STAGE_LOCKED_G, STAGE_LOCKED_B);
            drawText(g, x + 12, rowY, label + " [Terkunci]", style, Font.SIZE_SMALL);
            return;
        }
        if (def.buyKind == ContentCatalog.BUY_ACTION) {
            ink(g);
            drawText(g, x + 12, rowY, label + "  (aksi)", style, Font.SIZE_SMALL);
            return;
        }
        if (def.buyKind == ContentCatalog.BUY_TOGGLE) {
            ink(g);
            drawText(g, x + 12, rowY, label + ": " + (save.hardcore() ? "ON" : "OFF"),
                    style, Font.SIZE_SMALL);
            return;
        }
        int level = save.upgradeLevel(upgradeIndex);
        long cost = ContentCatalog.upgradeCost(def, level);
        boolean maxed = cost < 0;
        boolean affordable = maxed || cost == 0 || balance >= cost;
        if (affordable) ink(g);
        else g.setColor(STAGE_LOCKED_R, STAGE_LOCKED_G, STAGE_LOCKED_B);
        String text = label + " Lv" + level + "/" + def.maxLevel;
        if (maxed) text = text + " MAX";
        else if (cost == 0) text = text + "  gratis";
        else text = text + "  " + cost;
        drawText(g, x + 12, rowY, text, style, Font.SIZE_SMALL);
    }

    /**
     * Armory page: every weapon and item in one scrolling list with its status. Nothing can be
     * switched off in this build — that is what the locked Kustomisasi upgrade unlocks — so the
     * status column states the catalogue rather than offering a dead switch, and the footer
     * explains the lock in words.
     */
    private void drawArmory(Graphics g, int x, int y) {
        title(g, x, y, "ARMORY");
        int weapons = ContentCatalog.WEAPONS.length;
        int rows = weapons + ContentCatalog.ITEMS.length;
        for (int slot = 0; slot < SHOP_PAGE_SIZE; slot++) {
            int i = shopScroll + slot;
            if (i >= rows) break;
            int rowY = y + 56 + slot * 22;
            boolean selected = i == menuIndex;
            String name = i < weapons
                    ? ContentCatalog.WEAPONS[i].name
                    : ContentCatalog.ITEMS[i - weapons].name;
            ink(g);
            drawText(g, x + 12, rowY, (selected ? "> " : "  ") + name,
                    selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setColor(STAGE_UNLOCKED_R, STAGE_UNLOCKED_G, STAGE_UNLOCKED_B);
            drawText(g, x + 214, rowY, "[aktif]", Font.STYLE_PLAIN, Font.SIZE_SMALL);
            if (selected) {
                g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
                g.drawRect(x + 8, rowY - 2, 288, 16);
            }
        }
        ink(g);
        drawText(g, x + 12, y + 190, "Nonaktifkan senjata/item: butuh Kustomisasi.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 12, y + 208, "Kustomisasi masih terkunci, jadi semua tetap aktif.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 12, y + 226, "L/R: kembali", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Placeholder page for Papan Peringkat, Pencapaian and Holo House. The entry exists, the
     * screen exists and it renders its own title — it simply does not open yet, and says so
     * instead of failing silently.
     */
    private void drawLocked(Graphics g, int x, int y) {
        title(g, x, y, lockedTitle);
        g.setColor(STAGE_LOCKED_R, STAGE_LOCKED_G, STAGE_LOCKED_B);
        drawText(g, x + 64, y + 96, "[ TERKUNCI ]", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        ink(g);
        drawText(g, x + 34, y + 140, "Halaman ini sudah dibuat tapi belum dibuka.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 34, y + 160, "Datanya menyusul di update berikutnya.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 34, y + 204, "Kembali: FIRE / L / R", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /** Credits: static text, no state. Sources and thanks, then the same way back as the rest. */
    private void drawCredits(Graphics g, int x, int y) {
        title(g, x, y, "CREDITS");
        ink(g);
        String[] lines = {
                "HOLOJAVA",
                "Game Java ME bergaya HoloCure.",
                "Inspirasi: HoloCure, fan game hololive",
                "oleh kayfstarter.",
                "Data karakter, senjata, item & harga:",
                "holocure.wiki.gg (Update 0.7).",
                "Kode, seni dan stage: proyek HoloJava.",
                "Terima kasih untuk para Hololive member",
                "dan komunitas penggemarnya."
        };
        for (int i = 0; i < lines.length; i++) {
            drawText(g, x + 20, y + 54 + i * 17, lines[i], Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        drawText(g, x + 20, y + 214, "Kembali: FIRE / L / R", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

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
        drawText(g, x + 12, y + 214, "Playing: " + ContentCatalog.character(characterIndex).name
                + ".  Hard and Time stages are not available yet.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawSettings(Graphics g, int x, int y) {
        title(g, x, y, "PENGATURAN");
        String[] items = { "Hardcore: " + (save.hardcore() ? "ON" : "OFF"),
                "Audio: OFF (runtime not verified)", "Controls", "Exit", "Back" };
        list(g, x, y + 65, items, menuIndex);
        ink(g);
        drawText(g, x + 20, y + 190, "Hardcore sets Endless player HP to 1.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 20, y + 210, "Exit keluar dari game. Pilihan lain: kembali.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void drawControls(Graphics g, int x, int y) {
        title(g, x, y, "CONTROLS");
        ink(g);
        drawText(g, x + 24, y + 60, "D-pad: move / menu navigation", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 82, "GAME A / 1: basic attack", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 104, "FIRE / 5: pause in gameplay", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 126, "0: pause (alt)   FIRE: select / confirm in menus", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 148, "9: special (butuh upgrade Special Attack)", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 166, "Skill karakter aktif sendiri tiap 15 detik.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 24, y + 198, "Orbiting and passive weapons may fire autonomously.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
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
