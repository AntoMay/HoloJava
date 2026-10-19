package me.holojava.fullgame;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

/** Menu flow and lifecycle-aware screen coordinator for the rebuilt game. */
public class FullGameCanvas extends GameCanvas implements Runnable {
    interface ShutdownRequest { void requestShutdown(); }
    private static final int MENU = 0;
    private static final int CHOOSE_IDOL = 1;   // Choose Your Idol: grid 5 karakter + panel detail
    private static final int CHOOSE_MODE = 2;   // Choose Mode: hanya Stage Mode (simplified)
    private static final int STAGES = 3;
    private static final int SETTINGS = 4;
    private static final int HELP = 5;          // Help screen in Settings
    private static final int PREP = 6;
    private static final int GAME = 7;
    private static final int RESULTS = 8;
    private static final int CONTROLS = 9;
    private static final int SPLASH = 10;
    private static final int SHOP = 11;     // Toko submenu: Beli Upgrade / Armory / Keluar
    private static final int UPGRADES = 12; // Beli Upgrade: the three-tab purchase page
    private static final int ARMORY = 13;   // weapon and item catalogue, view-only while locked
    private static final int LOCKED = 14;   // placeholder page shared by the three shut entries
    private static final int CREDITS = 15;
    // Toko internal step: Beli Upgrade opens this tab submenu (Ability / Stat / Lainnya /
    // Kembali) before the purchase page, so tab choice is a list pick instead of an L/R swipe.
    private static final int SHOPTABS = 16;
    // Old CHARACTERS (2) and MODES (1) removed; flow now: Main -> CHOOSE_IDOL -> CHOOSE_MODE -> STAGES
    /**
     * Main menu rows, in draw order: Papan Peringkat, Pencapaian, Toko, Main, Holo House,
     * Pengaturan, Credits, Keluar. The start-up flow lives behind Main — Main to Mode to
     * Karakter to Stage — so characters and stages are no longer entries of their own. Keluar
     * ends the game straight from the bar, and Pengaturan keeps its own Exit row as well.
     */
    private static final int MAIN_ITEM_COUNT = 8;
    private static final int MAIN_LEADERBOARD = 0;
    private static final int MAIN_ACHIEVEMENTS = 1;
    private static final int MAIN_TOKO = 2;
    private static final int MAIN_PLAY = 3;
    private static final int MAIN_HOLOHOUSE = 4;
    private static final int MAIN_SETTINGS = 5;
    private static final int MAIN_CREDITS = 6;
    private static final int MAIN_EXIT = 7;
    private static final int CHARACTER_PAGE_SIZE = 8;

    /**
     * Coin top-up applied when the game boots, while the coin economy is still being designed.
     *
     * <p>Deliberately kept out of {@code SaveStore} on purpose: that class owns the real balance
     * semantics and its tests pin a fresh install to zero, so the starting capital is a game-layer
     * policy instead of a persistence change. Deleting this constant and the single call to
     * {@code ensureStartingCapital()} restores a zero balance with no other edit.
     */
    static final long SHOP_STARTING_CAPITAL = 0L;
    /** Rows the shop draws per screen; the rest are reached by scrolling. */
    private static final int SHOP_PAGE_SIZE = 5;

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

    // Choose Your Idol grid layout — proporsional untuk 320x240
    private static final int IDOL_COLS = 5;                    // 5 karakter = 5 kolom
    private static final int IDOL_ROWS = 1;

    private static final int IDOL_BOX_W = 48;                  // dikecilkan untuk proporsi 320x240
    private static final int IDOL_BOX_H = 56;                  // dikecilkan dari 72
    private static final int IDOL_GRID_X = 4;                  // start x: (320 - (5*48 + 4*8)) / 2 = 4
    private static final int IDOL_GRID_Y = 50;                 // start y (below header, 40+10 margin)
    private static final int IDOL_GAP = 8;                     // gap antar kotak
    // Panel detail (di bawah grid, cukup tinggi untuk sprite + text)
    private static final int IDOL_PANEL_X = 10;
    private static final int IDOL_PANEL_Y = 115;               // di bawah grid (50+56+10 margin = 116)
    private static final int IDOL_PANEL_W = 300;               // hampir full width
    private static final int IDOL_PANEL_H = 110;               // lebih tinggi untuk sprite + text
    // Animasi idle di panel
    private static final int IDOL_ANIM_FPS = 8;                // 8 FPS = 125ms/frame
    private int idolAnimFrame = 0;
    private int idolAnimTimer = 0;
    private int idolDetailScroll = 0; // scroll offset for detail panel

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
    // Recentred for the eight icons: their visual span (sum of widths + seven 23px gaps)
    // is 265px, so (320 - 265) / 2 = 27 keeps the same left/right margin the seven-icon
    // bar used to have at x = 40.
    private static final int TITLE_BAR_X = 27, TITLE_ICON_GAP = 23;
    private static final int TITLE_LABEL_X = 200, TITLE_LABEL_Y = 206, TITLE_LABEL_W = 116;
    // The eight bar entries, in bar order, mirroring MAIN_* above. The bar reads left to right
    // like the reference, so this order is also the sweep order of Up/Down/Left/Right.
    private static final String[] MAIN_ITEMS = { "Papan Peringkat", "Pencapaian", "Toko",
            "Main", "Holo House", "Pengaturan", "Credits", "Keluar" };
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
    /** Looping stage music; silent until a run starts (see BgmAudio for the stage table).
     *  Created on first use: some suites allocate the canvas without its constructor, so no
     *  field initializer may be load-bearing. */
    private BgmAudio bgm;

    private BgmAudio bgm() {
        if (bgm == null) bgm = new BgmAudio();
        return bgm;
    }
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
    /** A pending purchase question on the highlighted row: the first FIRE asks, the second buys. */
    private boolean buyConfirm;
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
        bgm().stop(); // music must not keep playing behind a backgrounded MIDlet
        if (world != null) world.pause();
    }
    public void resumeApplication() {
        applicationPaused = false;
        if (world != null) world.resume();
        if (save != null) bgm().setEnabled(save.audioEnabled());
        if (screen == GAME) {
            // re-entering mid-run: the stage track returns, unless Pengaturan turned Audio off
            bgm().play(stageIndex);
        } else if (screen != RESULTS) {
            bgm().playMenu(); // same rule as render(): only the results page stays silent
        }
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
        rightWasDown = right;
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
        else if (screen == CHOOSE_IDOL) updateChooseIdol(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == CHOOSE_MODE) updateChooseMode(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == SHOP) updateShop(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == SHOPTABS) updateShopTabs(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == UPGRADES) updateUpgrades(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == ARMORY) updateArmory(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == LOCKED) updateLocked(fireEdge || leftEdge || rightEdge);
        else if (screen == CREDITS) updateCredits(fireEdge || leftEdge || rightEdge);
        else if (screen == STAGES) updateStages(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == SETTINGS) updateSettings(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == HELP) updateHelp(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
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
            // Main opens the start-up flow: Choose Your Idol -> Choose Mode -> Stage.
            case MAIN_PLAY: screen = CHOOSE_IDOL; menuIndex = characterIndex; idolAnimFrame = 0; idolAnimTimer = 0; break;
            case MAIN_HOLOHOUSE: openLocked("HOLO HOUSE"); break;
            case MAIN_SETTINGS: screen = SETTINGS; menuIndex = 0; break;
            case MAIN_CREDITS: screen = CREDITS; break;
            case MAIN_EXIT:
                exitRequested = true;
                stopped = true;
                shutdownRequest.requestShutdown();
                break;
            default: break;
        }
    }

    /** Opens one of the placeholder pages. menuIndex is left alone so Back lands on the same row. */
    private void openLocked(String title) {
        lockedTitle = title;
        screen = LOCKED;
    }

    /** Stage Select row for the stage a run would start with right now. */
    private int stageRowForSelection() {
        int[] stages = selectableStages();
        for (int slot = 0; slot < stages.length; slot++) if (stages[slot] == stageIndex) return slot;
        return 0;
    }

    /**
     * CHOOSE YOUR IDOL — grid 5 karakter (tanpa Back entry di grid).
     * Layout: 5 kotak horizontal, detail panel di bawah.
     * Navigasi: L/R antar 5 karakter (wrap). Atas/Bawah scroll detail panel.
     * LEFT di index 0 → kembali ke MENU. FIRE pilih karakter → CHOOSE_MODE.
     * Tombol "Kembali" visual di pojok kanan bawah layar.
     */
    private void updateChooseIdol(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        int charCount = ContentCatalog.CHARACTERS.length; // 5
        if (left) {
            if (menuIndex == 0) {
                screen = MENU;
                menuIndex = MAIN_PLAY;
            } else {
                menuIndex = (menuIndex - 1 + charCount) % charCount;
            }
            idolDetailScroll = 0;
            return;
        }
        if (right) {
            menuIndex = (menuIndex + 1) % charCount;
            idolDetailScroll = 0;
            return;
        }
        // Up/Down untuk scroll detail panel
        if (up) {
            if (idolDetailScroll > 0) idolDetailScroll--;
            return;
        }
        if (down) {
            idolDetailScroll++;
            return;
        }
        if (!fire) return;
        // Pilih karakter
        characterIndex = menuIndex;
        save.setSelectedCharacter(characterIndex);
        screen = CHOOSE_MODE;
        menuIndex = 0;
        idolDetailScroll = 0;
    }

    /**
     * CHOOSE MODE — simplified: hanya "Stage Mode".
     * FIRE → STAGES. Back (LEFT) → CHOOSE_IDOL.
     */
    private void updateChooseMode(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        if (left) { backToChooseIdol(); return; }
        if (up || down) {
            // hanya 1 item, no-op
            return;
        }
        if (!fire) return;
        // Stage Mode dipilih
        modeIndex = 0;
        screen = STAGES;
        menuIndex = stageRowForSelection();
    }

    private void backToChooseIdol() {
        screen = CHOOSE_IDOL;
        menuIndex = characterIndex;
    }

    private void backToChooseMode() {
        screen = CHOOSE_MODE;
        menuIndex = 0;
    }

    /**
     * Toko input: three entries — Beli Upgrade, Armory, Keluar. Beli Upgrade opens the tab
     * submenu (the three tabs are a list pick now, not an L/R swipe on the purchase page).
     * Keluar returns to the main menu, and so does L/R from anywhere on the page, matching
     * every other submenu.
     */
    private void updateShop(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        if (left || right) { screen = MENU; menuIndex = MAIN_TOKO; return; }
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 3);
        if (!fire) return;
        if (menuIndex == 0) {
            screen = SHOPTABS;
            menuIndex = shopTab;             // reopen on the tab that was last used
            buyConfirm = false;
            refundConfirm = false;
        }
        else if (menuIndex == 1) { screen = ARMORY; menuIndex = 0; shopScroll = 0; }
        else { screen = MENU; menuIndex = MAIN_TOKO; }
    }

    /**
     * Beli Upgrade's tab submenu: Ability, Stat, Lainnya and a trailing Kembali row. Picking a
     * tab opens the purchase page on it; L/R and Kembali step back out to the Toko submenu.
     */
    private void updateShopTabs(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        if (left || right) { screen = SHOP; menuIndex = 0; return; }
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 4);
        if (!fire) return;
        if (menuIndex == 3) { screen = SHOP; menuIndex = 0; return; }
        shopTab = menuIndex;
        screen = UPGRADES;
        menuIndex = 0;
        shopScroll = 0;
        buyConfirm = false;
        refundConfirm = false;
    }

    /**
     * Beli Upgrade input: the tab is fixed by the submenu that opened this page, so L/R and the
     * trailing Kembali row both step back up to the tab submenu with the cursor on the tab just
     * used, and up/down move the highlight inside the tab. FIRE buys — through a modal popup:
     * while a confirmation popup is open it owns the input, FIRE executes it and any d-pad press
     * only closes it, so cancelling never moves the cursor or navigates away. update() calls
     * this every40ms tick, so the popup branch must react to real edges only: an idle tick with
     * no key at all leaves the popup open — only an actual d-pad press dismisses it.
     */
    private void updateUpgrades(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        int rows = ContentCatalog.upgradeGroupCount(shopTab) + 1;
        if (buyConfirm || refundConfirm) {
            if (fire) buyUpgrade(ContentCatalog.upgradeGroupStart(shopTab) + menuIndex);
            else if (up || down || left || right) {       // a d-pad press closes it in place
                buyConfirm = false;
                refundConfirm = false;
            }
            return;                                        // an idle tick keeps the popup open
        }
        if (left || right) {
            screen = SHOPTABS;
            menuIndex = shopTab;
            return;
        }
        if (up || down) {
            menuIndex = wrap(menuIndex + (up ? -1 : 1), rows);
            keepShopRowVisible(rows);
        }
        if (!fire) return;
        if (menuIndex == rows - 1) {          // the trailing Kembali row
            screen = SHOPTABS;
            menuIndex = shopTab;
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
     * Resolves the FIRE presses on an upgrade row; a purchase now needs two of them.
     *
     * <p>Locked rows are data for the page only: their wiki prices are shown, but no purchase can
     * land. Refund All asks once and then zeroes every row while crediting the full price back in
     * one write. Hardcore toggles the settings bit itself, so the shop and the Pengaturan page can
     * never disagree. A levelled row asks first (buyConfirm) and only the second FIRE spends: it
     * writes the level only after the coin write committed, exactly as before — a storage failure
     * costs the coins, never the level, and a short balance, a maxed row or a free (0-coin) row
     * each leave the record untouched. The asking popup owns the input while it is open (see
     * updateUpgrades), so the confirmation can never be spent from another row.
     */
    private void buyUpgrade(int upgradeIndex) {
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(upgradeIndex);
        if (def == null || def.locked) return;
        if (def.buyKind == ContentCatalog.BUY_ACTION) {
            if (refundConfirm) { refundConfirm = false; save.refundAll(); }
            else refundConfirm = true;
            buyConfirm = false;
            return;
        }
        refundConfirm = false;
        if (def.buyKind == ContentCatalog.BUY_TOGGLE) {
            buyConfirm = false;
            save.setHardcore(!save.hardcore());
            return;
        }
        int level = save.upgradeLevel(upgradeIndex);
        long cost = ContentCatalog.upgradeCost(def, level);
        if (cost < 0) return;                            // already maxed: nothing to ask
        if (!buyConfirm) { buyConfirm = true; return; }  // first press only asks
        buyConfirm = false;
        if (cost > 0 && !save.spendCoins(cost)) return;  // free rows never touch the balance
        save.setUpgradeLevel(upgradeIndex, level + 1);
    }

    private void updateStages(boolean up, boolean down, boolean fire, boolean left) {
        int[] stages = selectableStages();
        int count = stages.length + 1;
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), count);
        // Stage is the last step of the flow, so Back and the trailing row step up to Choose Mode.
        if (left) { backToChooseMode(); return; }
        if (!fire) return;
        if (menuIndex == stages.length) { backToChooseMode(); return; }
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
        // Audio, Controls, Help, Back. Hardcore moved to Shop (tab Lainnya).
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 4);
        if (left) { screen = MENU; menuIndex = MAIN_SETTINGS; return; }
        if (!fire) return;
        if (menuIndex == 0) {
            // Audio row, now live: the MMAPI path is verified, so the row flips the persisted
            // preference and the player in one press — OFF silences at once (setEnabled stops),
            // ON applies on the next play, since this page only opens from menus where nothing
            // is playing. A missing save leaves the session-only toggle, defaulting to ON.
            boolean on = !(save != null && save.audioEnabled());
            if (save != null) save.setAudioEnabled(on);
            bgm().setEnabled(on);
        }
        else if (menuIndex == 1) screen = CONTROLS;
        else if (menuIndex == 2) screen = HELP;
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
        if (save != null) bgm().setEnabled(save.audioEnabled());
        bgm().play(stageIndex);
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
            else levelUpScroll = 0; // popup closed: the next level-up starts at the top row
            if (world.state() == RunWorld.STATE_PAUSED) drawPause(g, ox, oy);
            if (world.state() == RunWorld.STATE_GAME_OVER || world.state() == RunWorld.STATE_VICTORY) { /* results handled next tick */ }
        } else {
            // pre-game screens carry the menu theme; the results page keeps its silence
            if (screen == RESULTS) bgm().stop();
            else bgm().playMenu();
            if (screen == RESULTS) drawResults(g, ox, oy);
            else drawMenuScreen(g, ox, oy);
        }
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
        else if (screen == CHOOSE_IDOL) drawChooseIdol(g, x, y);
        else if (screen == CHOOSE_MODE) drawChooseMode(g, x, y);
        else if (screen == SHOP) drawShop(g, x, y);
        else if (screen == SHOPTABS) drawShopTabs(g, x, y);
        else if (screen == UPGRADES) drawUpgrades(g, x, y);
        else if (screen == ARMORY) drawArmory(g, x, y);
        else if (screen == LOCKED) drawLocked(g, x, y);
        else if (screen == CREDITS) drawCredits(g, x, y);
        else if (screen == STAGES) drawStages(g, x, y);
        else if (screen == SETTINGS) drawSettings(g, x, y);
        else if (screen == HELP) drawHelp(g, x, y);
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
     * the screen, the roster collage stands behind a gently floating logo, and the eight entries
     * live in an icon bar along the bottom instead of a vertical list. The selected icon lifts
     * off the bar and bobs on the clock, a white underline marks it for anyone who cannot track
     * the motion, and the gold label box slides the entry's name in at the bottom right. The name
     * comes pre-rendered (menu_text_*) at one notch below MIDP's smallest font, with the drawn
     * SIZE_SMALL string kept as the partial-build fallback. All motion reads clockMs, recorded
     * once per tick by update(long), so this method stays pure and a headless clock of 0 simply
     * freezes every effect at rest.
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
        drawRight(g, x, y + 4, "version 0.1");

        drawBar(g, x, y);

        // Gold label box with the highlighted entry's name, bottom right. Changing the selection
        // restarts the short slide-in so the label visibly answers the input; the box and its
        // baked text share one coordinate, so they move together.
        if (menuIndex != labelIndex) { labelIndex = menuIndex; labelShownAt = clockMs; }
        int slide = (int) ((clockMs - labelShownAt) / 25L);
        if (slide > 6) slide = 6;
        int labelY = y + TITLE_LABEL_Y + (6 - slide);
        Image label = assets.menuLabel();
        if (label != null) g.drawImage(label, x + TITLE_LABEL_X, labelY, Graphics.TOP | Graphics.LEFT);
        Image labelText = assets.menuText(menuIndex);
        if (labelText != null) {
            g.drawImage(labelText, x + TITLE_LABEL_X, labelY, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(TITLE_TEXT_R, TITLE_TEXT_G, TITLE_TEXT_B);
            Font labelFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
            g.setFont(labelFont);
            g.drawString(MAIN_ITEMS[menuIndex],
                    x + TITLE_LABEL_X + (TITLE_LABEL_W - labelFont.stringWidth(MAIN_ITEMS[menuIndex])) / 2,
                    labelY + 6, Graphics.TOP | Graphics.LEFT);
        }
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
    }

    /**
     * CHOOSE YOUR IDOL — grid 5 karakter + Back entry (6 items).
     * Layout: 6 kotak horizontal (56x72 each), detail panel di bawah dengan scroll.
     * Setiap kotak: hanya portrait + nama, border biru muda. Tidak ada dark panel.
     * Detail panel: border biru muda, konten scroll (Atas/Bawah untuk scroll).
     */

    /**
     * CHOOSE YOUR IDOL — grid 5 karakter (tanpa Back entry).
     * Layout: 5 kotak horizontal (dikecilkan), detail panel di bawah dengan scroll.
     * Setiap kotak: HANYA portrait (gambar karakter), border biru muda.
     * Detail panel: border biru muda, nama karakter di atas, idle sprite kiri bawah,
     *               stat di kanan sprite (scroll Atas/Bawah).
     * Tombol "Kembali" di pojok kanan bawah layar.
     * Idle sprite di pojok kiri bawah panel detail.
     */
    private void drawChooseIdol(Graphics g, int x, int y) {
        title(g, x, y, "CHOOSE YOUR IDOL");
        int charCount = ContentCatalog.CHARACTERS.length; // 5
        int boxW = IDOL_BOX_W;
        int boxH = IDOL_BOX_H;
        int startX = IDOL_GRID_X;
        int startY = IDOL_GRID_Y;

        // Update animasi timer
        idolAnimTimer++;
        if (idolAnimTimer >= (1000 / IDOL_ANIM_FPS) / 33) {
            idolAnimTimer = 0;
            idolAnimFrame = (idolAnimFrame + 1) % 4;
        }

        // Draw 5 character boxes (hanya portrait, tanpa nama)
        for (int i = 0; i < charCount; i++) {
            int bx = startX + i * (boxW + IDOL_GAP);
            int by = startY;
            boolean selected = (i == menuIndex);

            // Box: NO fill, only light blue border
            g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
            if (selected) {
                g.drawRect(x + bx - 2, y + by - 2, boxW + 3, boxH + 3); // thicker selected
            } else {
                g.drawRect(x + bx, y + by, boxW - 1, boxH - 1);
            }

            // Character box: HANYA portrait (centered vertically & horizontally)
            ContentCatalog.CharacterDef c = ContentCatalog.CHARACTERS[i];
            Image portrait = assets.characterPortrait(i);
            if (portrait != null) {
                int px = x + bx + (boxW - portrait.getWidth()) / 2;
                int py = y + by + (boxH - portrait.getHeight()) / 2;
                g.drawImage(portrait, px, py, Graphics.TOP | Graphics.LEFT);
            }
        }

        // Detail panel for selected
        ContentCatalog.CharacterDef sel = ContentCatalog.CHARACTERS[menuIndex];
        int px = x + IDOL_PANEL_X;
        int py = y + IDOL_PANEL_Y;
        int pw = IDOL_PANEL_W;
        int ph = IDOL_PANEL_H;

        // Panel border only (light blue)
        g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
        g.drawRect(px, py, pw - 1, ph - 1);
        g.drawRect(px + 1, py + 1, pw - 3, ph - 3);

        // Character name at top of panel (centered)
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        drawText(g, px + pw / 2, py + 10, sel.name, Font.STYLE_BOLD, Font.SIZE_SMALL);

        // Draw idle sprite at bottom LEFT corner of panel (8px margin)
        Image idleSprite = assets.characterIdle(menuIndex, idolAnimFrame);
        int spriteW = 0;
        int spriteH = 0;
        if (idleSprite != null) {
            spriteW = idleSprite.getWidth();
            spriteH = idleSprite.getHeight();
            int sx = x + px + 8;
            // Center sprite vertically in the text area (py+8 to py+ph-8)
            int textAreaTop = py + 8;
            int textAreaBottom = py + ph - 8;
            int textAreaCenterY = textAreaTop + (textAreaBottom - textAreaTop) / 2;
            int sy = y + textAreaCenterY - spriteH / 2;
            g.drawImage(idleSprite, sx, sy, Graphics.TOP | Graphics.LEFT);
        }

        // Text area starts after sprite (with gap)
        int textX = px + 8 + spriteW + 8;
        int textW = pw - 16 - spriteW - 8;

        // Clip to panel interior for scrolling text (text area only)
        g.clipRect(x + textX, y + py + 4, textW, ph - 8);

        // Build detail lines
        String[] lines = buildIdolDetailLines(sel);
        int lineH = 14;
        int detailStartY = py + 8 - idolDetailScroll * lineH;
        int maxVisible = (ph - 8) / lineH;

        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        int visible = 0;
        for (int i = 0; i < lines.length && visible < maxVisible; i++) {
            int lineY = detailStartY + i * lineH;
            if (lineY + lineH >= py + 8 && lineY <= py + ph - 8) {
                drawText(g, textX, lineY, lines[i], Font.STYLE_PLAIN, Font.SIZE_SMALL);
                visible++;
            }
        }

        g.clipRect(0, 0, Viewport.MIN_W, Viewport.MIN_H); // reset clip
    }
    private String[] buildIdolDetailLines(ContentCatalog.CharacterDef c) {
        ContentCatalog.WeaponDef wp = ContentCatalog.weapon(c.mainWeapon);
        String wpName = wp != null ? wp.name : c.mainWeapon;
        return new String[] {
            "HP: " + c.hp,
            "ATK: " + c.atkPercent + "%",
            "SPD: " + c.spdPercent + "%",
            "CRT: " + c.critPercent + "%",
            "Size: " + c.size,
            "",
            "Weapon: " + wpName,
            "Special: " + c.special,
            "Skill: " + c.skill,
            "",
            "Asset: " + c.assetGroup,
            c.fallbackArt ? "(fallback art)" : "(original art)"
        };
    }

    /**
     * CHOOSE MODE — hanya Stage Mode.
     */
    private void drawChooseMode(Graphics g, int x, int y) {
        title(g, x, y, "CHOOSE MODE");
        String[] items = { "Stage Mode" };
        list(g, x, y + 60, items, menuIndex);
        ink(g);
        drawText(g, x + 20, y + 150, "Hanya Stage Mode tersedia.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 20, y + 170, "Endless & Time coming soon.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
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
        titleCentered(g, x, y, "TOKO");
        drawCoin(g, x, y, save.coinBalance());
        String[] items = { "Beli Upgrade", "Armory", "Keluar" };
        list(g, x, y + 70, items, menuIndex);
        ink(g);
        drawText(g, x + 12, y + 176, "Beli Upgrade: submenu tab Ability / Stat / Lainnya.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 12, y + 194, "Armory: katalog senjata & item.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Beli Upgrade's tab submenu: the three shop tabs as a plain list, entered from the Toko
     * submenu. The purchase page can only be reached through this pick, and steps back to it
     * with the cursor restored onto the tab just used.
     */
    private void drawShopTabs(Graphics g, int x, int y) {
        title(g, x, y, "PILIH TAB");
        drawCoin(g, x, y, save.coinBalance());
        String[] items = { "Ability", "Stat", "Lainnya", "Kembali" };
        list(g, x, y + 70, items, menuIndex);
        ink(g);
        drawText(g, x + 12, y + 176, "Tab belanja per kategori upgrade.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Beli Upgrade page: the purchase list for the tab the submenu opened, five rows at a time
     * with the rest reached by scrolling, and a detail panel pinned to the bottom. Every row is
     * drawn — locked ones too, with their wiki price — so the shop reads like the real thing
     * instead of hiding its content until it exists. Colour follows the rest of the menus: ink
     * when the row can be bought, the recessive cyan accent when it cannot, and words ("MAX",
     * "gratis", "[Terkunci]") so colour is never the only signal. A pending confirmation is a
     * modal popup drawn last, so it covers the list instead of fighting the key hint for a line.
     */
    private void drawUpgrades(Graphics g, int x, int y) {
        title(g, x, y, "BELI UPGRADE");
        long balance = save.coinBalance();
        drawCoin(g, x, y, balance);

        int start = ContentCatalog.upgradeGroupStart(shopTab);
        int count = ContentCatalog.upgradeGroupCount(shopTab);
        int rows = count + 1;
        for (int slot = 0; slot < SHOP_PAGE_SIZE; slot++) {
            int i = shopScroll + slot;
            if (i >= rows) break;
            int rowY = y + 56 + slot * 26;
            boolean selected = i == menuIndex;
            if (i < count) drawUpgradeRow(g, x, rowY, start + i, selected, balance);
            else {
                ink(g);
                drawText(g, x + 12, rowY, (selected ? "> " : "  ") + "Kembali",
                        selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            }
            if (selected) {
                g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
                g.drawRect(x + 8, rowY - 2, 288, 16);
            }
        }

        // Detail panel: separator, the highlighted row's full text, and the key hint. The pending
        // question itself lives in the popup below rather than replacing this line.
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
        ink(g);

        // Confirmation popup, drawn last so it covers the list. updateUpgrades clears the flags
        // on any d-pad press, so the popup and its pending action always describe the row that
        // was highlighted when it opened.
        if (refundConfirm) {
            long worth = save.refundValue();
            drawShopPopup(g, x, y, "Refund All?", new String[] {
                    "Semua level upgrade kembali 0.",
                    "+" + worth + " Koin (sisa " + (balance + worth) + ")",
            });
        } else if (buyConfirm && menuIndex < count) {
            ContentCatalog.UpgradeDef ask = ContentCatalog.upgrade(start + menuIndex);
            int askLevel = shopRowLevel(ask, start + menuIndex);
            long askCost = ContentCatalog.upgradeCost(ask, askLevel);
            drawShopPopup(g, x, y, "Beli " + ask.name + "?", new String[] {
                    "Lv " + askLevel + " -> Lv " + (askLevel + 1)
                            + (askCost > 0 ? "   -" + askCost + " Koin" : "   gratis"),
                    askCost > 0 ? "Koin tersisa: " + (balance - askCost) : "Koin: " + balance,
            });
        }
    }

    /**
     * Modal shop confirmation, drawn last on the page so it covers the list. It names the action
     * and its effect on the coin balance, and states both outcomes in words — FIRE executes, any
     * d-pad press closes it — because the popup is the only place the pending action is announced.
     */
    private void drawShopPopup(Graphics g, int x, int y, String heading, String[] lines) {
        int w = 248, h = 96;
        int px = x + (Viewport.MIN_W - w) / 2, py = y + 64;
        g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
        g.fillRect(px, py, w, h);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.drawRect(px + 1, py + 1, w - 3, h - 3);
        ink(g);
        drawText(g, px + 12, py + 10, heading, Font.STYLE_BOLD, Font.SIZE_SMALL);
        for (int i = 0; i < lines.length; i++) {
            drawText(g, px + 12, py + 30 + i * 16, lines[i], Font.STYLE_PLAIN, Font.SIZE_SMALL);
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
        drawCoin(g, x, y, save.coinBalance());
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
        String[] items = { "Audio: " + (save != null && save.audioEnabled() ? "ON" : "OFF"),
                "Controls", "Help", "Back" };
        list(g, x, y + 65, items, menuIndex);
        ink(g);
        drawText(g, x + 20, y + 190, "Hardcore kini ada di Toko (tab Lainnya).", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    private void updateHelp(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        if (left || right || fire || up || down) {
            screen = SETTINGS;
        }
    }

    private void drawHelp(Graphics g, int x, int y) {
        title(g, x, y, "BANTUAN");
        ink(g);
        int ty = y + 50;
        int lh = 18;
        drawText(g, x + 20, ty, "Navigasi Menu:", Font.STYLE_BOLD, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "Atas/Bawah: pilih item", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "Kiri/Kanan: navigasi tab / kembali", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "FIRE / 5: pilih / konfirmasi", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh * 2;
        drawText(g, x + 20, ty, "Dalam Gameplay:", Font.STYLE_BOLD, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "1 / GAME A: Serang", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "5 / FIRE: Pause", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "9: Special (butuh upgrade)", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "0: Pause alternatif", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh * 2;
        drawText(g, x + 20, ty, "Skill karakter aktif otomatis.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "Orbiting/passive weapon otomatis.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
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

    /** Scroll offset of the level-up offer list, kept in screen space and reset whenever the
     *  popup closes (see render). The window slides so the selected row always stays visible. */
    private int levelUpScroll;

    /** Level-up popup: a themed panel over a dithered dim of the frozen run behind it. The
     *  offer list scrolls inside the box once the rows no longer fit, so the popup keeps one
     *  fixed size no matter how many offers (or Reroll/Tahan rows) the roll produced. */
    private void drawLevelUp(Graphics g, int x, int y) {
        // No full-screen dim: as requested, only the light-blue panel itself floats over the
        // frozen run. drawPause keeps the full-page treatment; this popup does not.
        int panelW = 248;
        int rowH = 20;
        int rows = 5; // list window: how many offers fit the box before the list scrolls
        int panelH = 34 + rows * rowH + 6;
        int px = x + (Viewport.MIN_W - panelW) / 2;
        int py = y + (Viewport.MIN_H - panelH) / 2;
        // Same panel/ink/accent trio as every menu page: the popup wears the game's own tone.
        g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
        g.fillRect(px, py, panelW, panelH);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.drawRect(px, py, panelW - 1, panelH - 1);
        g.drawRect(px + 2, py + 2, panelW - 5, panelH - 5);
        Font titleFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        g.setFont(titleFont);
        g.drawString("LEVEL UP", px + (panelW - titleFont.stringWidth("LEVEL UP")) / 2, py + 8,
                Graphics.TOP | Graphics.LEFT);

        int count = world.offerCount();
        int sel = world.selectedOffer();
        if (sel < levelUpScroll) levelUpScroll = sel;
        if (sel >= levelUpScroll + rows) levelUpScroll = sel - rows + 1;
        if (levelUpScroll > count - rows) levelUpScroll = count - rows;
        if (levelUpScroll < 0) levelUpScroll = 0;

        int listY = py + 34;
        Font rowFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(rowFont);
        for (int row = 0; row < rows; row++) {
            int i = levelUpScroll + row;
            if (i >= count) break;
            int ry = listY + row * rowH;
            boolean selected = i == sel;
            if (selected) {
                g.setColor(MENU_ACCENT_R, MENU_ACCENT_G, MENU_ACCENT_B);
                g.fillRect(px + 5, ry, panelW - 10, rowH - 2);
            }
            // Ink text on the accent selection bar (4.27:1); collabs keep a deeper gold when
            // idle so the rare offer still stands out on the light panel.
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            if (!selected && world.offerIsCollab(i)) g.setColor(176, 116, 20);
            g.drawString((selected ? "> " : "  ") + world.offerLabel(i), px + 10, ry + 5,
                    Graphics.TOP | Graphics.LEFT);
            String tag = world.offerType(i);
            g.setColor(104, 126, 140);
            g.drawString(tag, px + panelW - 10 - rowFont.stringWidth(tag), ry + 5, Graphics.TOP | Graphics.LEFT);
        }
        // Scroll affordances: ink triangles on the right edge while rows sit above/below.
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        int ax = px + panelW - 8;
        if (levelUpScroll > 0) g.fillTriangle(ax, listY + 1, ax - 3, listY + 6, ax + 3, listY + 6);
        if (levelUpScroll + rows < count) {
            int ay = listY + rows * rowH - 7;
            g.fillTriangle(ax, ay + 5, ax - 3, ay, ax + 3, ay);
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

    /** Centred variant of title(), for the short TOKO heading that sat visibly left at x+90. */
    private void titleCentered(Graphics g, int x, int y, String text) {
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        drawText(g, x + (Viewport.MIN_W - f.stringWidth(text)) / 2, y + 18, text,
                Font.STYLE_BOLD, Font.SIZE_LARGE);
    }

    /** Coin balance pinned to the top-right corner of every Toko-family page, in the title band. */
    private void drawCoin(Graphics g, int x, int y, long balance) {
        ink(g);
        drawRight(g, x, y + 20, "Koin: " + balance);
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
