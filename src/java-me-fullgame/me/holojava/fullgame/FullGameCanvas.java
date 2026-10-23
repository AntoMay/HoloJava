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
    private static final int SPLASH = 9;
    private static final int SHOP = 10;     // Toko submenu: Beli Upgrade / Armory / Keluar
    private static final int UPGRADES = 11; // Beli Upgrade: the three-tab purchase page
    private static final int ARMORY = 12;   // weapon and item catalogue, view-only while locked
    private static final int LOCKED = 13;   // placeholder page shared by the three shut entries
    private static final int CREDITS = 14;
    // Toko internal step: Beli Upgrade opens this tab submenu (Ability / Stat / Lainnya /
    // Kembali) before the purchase page, so tab choice is a list pick instead of an L/R swipe.
    private static final int SHOPTABS = 15;
    // Old CHARACTERS (2) and MODES (1) removed; flow now: Main -> CHOOSE_IDOL -> CHOOSE_MODE -> STAGES
    /**
     * Main menu rows, in draw order: Papan Peringkat, Pencapaian, Toko, Main, Holo House,
     * Pengaturan, Credits, Keluar. The start-up flow lives behind Main — Main to Mode to
     * Karakter to Stage — so characters and stages are no longer entries of their own. Keluar
     * ends the game straight from the bar, and Pengaturan keeps its own Back row as well.
     *
     * <p>The labels stay in sentence case; it is the pages behind them that carry the upper-case
     * headings.
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
     *
     * <p>The number itself lives in {@link ShopStartingCapital} so a public build can swap it
     * without touching this file; see that class for why it has to be compiled in.
     */
    static final long SHOP_STARTING_CAPITAL = ShopStartingCapital.VALUE;
    /** Rows the shop draws per screen; the rest are reached by scrolling. */
    private static final int SHOP_PAGE_SIZE = 5;
    /**
     * Line pitch on the CREDITS page, and the bounds of its scrolling window.
     *
     * <p>The page scrolls, so unlike the old single-screen version the pitch is free to be as
     * comfortable as the small font allows rather than being squeezed by a fixed block count.
     * {@link #CREDIT_TOP} clears the title band (40px) and {@link #CREDIT_BOTTOM} leaves room for
     * the scroll marker; {@link #creditVisibleRows()} follows from the two.
     */
    private static final int CREDIT_LINE_GAP = 14;
    private static final int CREDIT_TOP = 48;
    private static final int CREDIT_BOTTOM = 222;
    /** Row that draws as a divider rather than text. See {@link #buildCreditRows}. */
    private static final String CREDIT_RULE = "--";

    /**
     * Gameplay key bindings.
     *
     * <p>Basic attack lives on GAME_A and 1. FIRE and 5 are pause in gameplay.
     * Menu confirm uses FIRE, L, R. Level-up confirm uses FIRE.
     * 9 is special, 0 is alternate pause. There is no skill key: every character's skill fires
     * on its own during the run, and the special needs the Special Attack upgrade and a full
     * meter (charged by SPECIAL orb drops) to respond.
     */
    private static final int KEY_ATTACK = '5';       // pause in gameplay (also attack alt in old scheme)
    private static final int KEY_ATTACK_ALT = '1';   // basic attack
    private static final int KEY_PAUSE = '0';        // alternate pause
    private static final int KEY_SPECIAL = '9';

    // Choose Your Idol grid layout — proporsional untuk 320x240
    private static final int IDOL_COLS = 5;                    // 5 karakter = 5 kolom
    private static final int IDOL_ROWS = 1;

    private static final int IDOL_BOX_W = 48;                  // 48px width
    private static final int IDOL_BOX_H = 56;                  // 56px height
    private static final int IDOL_GRID_X = 24;                 // start x: (320 - (5*48 + 4*8)) / 2 = 24
    private static final int IDOL_GRID_Y = 49;                 // 9px gap atas (dari header y=40) dan 9px gap bawah (ke panel y=114)
    private static final int IDOL_GAP = 8;                     // gap antar kotak
    // Panel detail (exact layout per spec)
    private static final int IDOL_PANEL_X = 12;                // 12px gap on the left
    private static final int IDOL_PANEL_Y = 114;               // panel starts below the grid
    private static final int IDOL_PANEL_W = 296;               // 320 - 2*12, so the right gap matches the left
    private static final int IDOL_PANEL_H = 114;               // 240 - 114 - 12: the bottom gap equals the side gaps
    // Status list geometry inside the panel. The name row is 18px, 6px is kept clear of the
    // bottom border, and the rest is the scrollable status list. A 12px line pitch is what lets
    // the seven rows the list needs fit under the name without shrinking the panel.
    private static final int IDOL_NAME_ROW_H = 18;
    private static final int IDOL_BODY_PAD_BOTTOM = 6;
    private static final int IDOL_LINE_H = 12;
    // Horizontal placement inside the panel, measured from the panel's left border.
    // The status column starts at 136px on screen, which is the midpoint between the S and
    // the E in CHOOSE (S ends at 134.7, E starts at 137.0), so the block hangs off the
    // heading right where the word CHOOSE breaks.
    private static final int IDOL_TEXT_X = 124;                 // 12 + 124 = 136 absolute
    private static final int IDOL_SPRITE_X = 28;                // idle sprite left edge inside the panel
    // Ground line the sprites stand on, measured down from the panel's top border. Every sprite has
    // its feet on the last row of its own canvas, so a single line here aligns the whole cast:
    // 76 puts their feet on row 190 of the screen.
    private static final int IDOL_FEET_Y = 76;
    // Per-character horizontal nudge on top of IDOL_SPRITE_X, in the same order as
    // ContentCatalog.CHARACTERS. Calli's canvas is the widest of the five because her scythe is
    // drawn out to one side, which leaves her standing noticeably further right than everyone
    // else even with the same left edge; -12 pulls her back in line. The rest of the art is
    // already centred on its canvas and needs no correction.
    private static final int[] IDOL_SPRITE_DX = { 0, 0, -12, 0, 0 };
    /**
     * Top of every page heading, measured from the top of the screen. Shared by the generic
     * {@code title()} and by CHOOSE YOUR IDOL's own heading so the two cannot drift: the same
     * word must not sit at two different heights depending on which screen drew it.
     */
    private static final int TITLE_TOP = 8;
    /**
     * Top of the coin readout on the Toko pages, measured from the top of the screen. It sits
     * clear below the heading (which reaches about row 24) and just under the 40px title band,
     * leaving the band itself empty. See {@link #drawCoin}.
     */
    private static final int COIN_TOP = 44;
    /**
     * Help body scroll, in rows of {@code lh} (16px). The bindings list ends at row 236, past
     * the 240px screen, so Up/Down scroll it by one row each and {@link #HELP_MAX_SCROLL} is
     * the offset that brings the last line just clear of the bottom edge.
     */
    private static final int HELP_SCROLL_STEP = 16;
    private static final int HELP_MAX_SCROLL = 12;
    private int helpScroll = 0;
    // Animasi idle di panel
    private static final int IDOL_ANIM_FPS = 8;                // 8 FPS = 125ms/frame
    private int idolAnimFrame = 0;
    private int idolAnimTimer = 0;
    private int idolDetailScroll = 0; // scroll offset for detail panel

    /** How many status rows the panel body shows at once. Shared by the draw code and the scroll
     *  clamp so the two can never disagree about where the list ends. */
    private static int idolVisibleLines() {
        return (IDOL_PANEL_H - IDOL_NAME_ROW_H - IDOL_BODY_PAD_BOTTOM) / IDOL_LINE_H;
    }

    /** Largest scroll offset that still fills the window, i.e. the one whose last visible row is
     *  the last row of the list ("(original art)"). Zero when everything already fits. */
    private int idolMaxScroll() {
        int lines = buildIdolDetailLines(ContentCatalog.CHARACTERS[menuIndex]).length;
        int max = lines - idolVisibleLines();
        return max > 0 ? max : 0;
    }

    // Shared HoloJava UI palette — the menu language the original HoloCure speaks: near-black
    // panels framed in plain white, white body text, and the gold sampled from menu_first
    // (#FFDB50) for names and prices. MENU_BG is the title band, MENU_PANEL the content band
    // and every floating popup, MENU_DEEP the backing behind portraits and the coin plate, and
    // MENU_DIM the recessive label grey. Every border, frame and rule is white, and a selected
    // row is a white frame over the row's own ink — the bright cyan accent (#31E0FF) that once
    // marked frames and selection bars is gone entirely.
    private static final int MENU_BG_R = 18, MENU_BG_G = 35, MENU_BG_B = 59;                // #12233B
    private static final int MENU_PANEL_R = 10, MENU_PANEL_G = 20, MENU_PANEL_B = 36;        // #0A1424
    private static final int MENU_INK_R = 255, MENU_INK_G = 255, MENU_INK_B = 255;           // #FFFFFF
    private static final int MENU_GOLD_R = 255, MENU_GOLD_G = 219, MENU_GOLD_B = 80;         // #FFDB50
    private static final int MENU_DEEP_R = 6, MENU_DEEP_G = 16, MENU_DEEP_B = 28;            // #06101C
    private static final int MENU_DIM_R = 143, MENU_DIM_G = 163, MENU_DIM_B = 181;           // #8FA3B5

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
    // Bottom icon bar, rebuilt after the reference's horizontal entry bar. The icons keep their
    // native halved sizes and align on one baseline; the gap is what recentres the bar after any
    // art change, so both stay named constants instead of spreading through the draw code.
    private static final int TITLE_BAR_BASE = 202;
    // Main-menu logo, dropped into the gap in the top roster row (between Marine and Coco).
    private static final int TITLE_LOGO_W = 118, TITLE_LOGO_X = 101, TITLE_LOGO_Y = 22;
    // The 47-strong roster crowd. CAST_X/CAST_Y are screen-space top-left base positions baked
    // by tools/prepare_title_menu_assets.py from the reference rm_Title room origins; index order
    // matches title_char_*.png (top row left->right with the logo gap, then the lower rows).
    // Back rows are drawn first so the front row overlaps them, like the reference crowd.
    private static final int CAST_COUNT = 47;
    private static final int[] CAST_X = {
         12,  30,  54,  68, 195, 230, 238, 262, 280,   8,  32,  50,
         74, 102, 120, 161, 181, 200, 222, 240, 264, 280,  26,  48,
         68,  86, 102, 131, 142, 169, 186, 216, 235, 256, 278,  12,
         39,  64,  81, 104, 131, 155, 162, 194, 227, 240, 269,
    };
    private static final int[] CAST_Y = {
         29,  38,  34,  38,  35,  38,  36,  35,  32,  63,  63,  64,
         68,  70,  69,  69,  71,  69,  71,  67,  59,  59,  87,  86,
         83,  88,  92,  96,  93,  95,  91,  87,  91,  88,  88, 101,
        112, 118, 122, 122, 121, 127, 127, 115, 116, 113, 117,
    };
    // Graduated members carry a sparkle over their head (Update 0.7+ detail), indexed like CAST_*:
    // Coco, Kanata, Shion, Mel, Sana, Fauna, Mumei, Ame, Gura, Aqua.
    private static final boolean[] GRADUATED = {
        false, false, false, false,  true,  true, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false,  true, false, false, false,
        false, false, false, false, false, false, false, false,  true, false, false, false,
         true, false,  true,  true, false,  true,  true, false, false, false,  true,
    };
    // Recentred for the eight icons: their visual span (sum of widths + seven 23px gaps)
    // is 265px, so (320 - 265) / 2 = 27 keeps the same left/right margin the seven-icon
    // bar used to have at x = 40.
    private static final int TITLE_BAR_X = 27, TITLE_ICON_GAP = 23;
    private static final int TITLE_LABEL_X = 200, TITLE_LABEL_Y = 210, TITLE_LABEL_W = 116;
    // The eight bar entries, in bar order, mirroring MAIN_* above. The bar reads left to right
    // like the reference, so this order is also the sweep order of Up/Down/Left/Right.
    //
    // These stay in sentence case. They are the bar's own labels, drawn in the gold box at the
    // bottom right, and upper-casing them changed the look of the title screen itself. The
    // upper-case headings live on the pages you open FROM here (PAPAN PERINGKAT, TOKO,
    // PENGATURAN, ...), which is where the CHOOSE YOUR IDOL title style applies.
    //
    // The names are also baked as images (menu_text_*) by tools/prepare_title_menu_assets.py,
    // which reads the same list from there, so changing a name means re-running that script.
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
    /** Which answer the shop popup has highlighted: SHOP_CHOICE_YES or SHOP_CHOICE_NO. */
    private int shopConfirmChoice = SHOP_CHOICE_YES;
    private static final int SHOP_CHOICE_NO = 0;
    private static final int SHOP_CHOICE_YES = 1;
    /** Heading of the currently open placeholder page (Papan Peringkat / Pencapaian / Holo House). */
    private String lockedTitle = "TERKUNCI";
    /** Scroll offset of the CREDITS page, in rows. Reset whenever the page is opened. */
    private int creditsScroll = 0;
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
        // Deliver key events in addition to key states. A MIDP GameCanvas suppresses keyPressed
        // only when the subclass asks for it, and the numeric keys ('5' pause, '9' special) reach
        // the game through keyPressed while the d-pad polls getKeyStates; requesting suppression
        // therefore froze every numeric key on spec-following emulators (freej2me skipped the
        // dispatch entirely), which is why pressing 9 with a full special meter did nothing.
        super(false);
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
        // Debounced coin credits land before the app goes to the background: a suspend is exactly
        // when a device may never come back the way it left.
        if (save != null) save.flush();
        // Pause BGM instead of stopping: freeze audio so it resumes from the same position
        bgm().pause();
        if (world != null) world.pause();
    }
    public void resumeApplication() {
        applicationPaused = false;
        if (world != null) world.resume();
        if (save != null) bgm().setEnabled(save.audioEnabled());
        if (screen == GAME) {
            // re-entering mid-run: resume stage track from paused position
            bgm().resume();
        } else if (screen != RESULTS) {
            bgm().playMenu(); // same rule as render(): only the results page stays silent
        }
    }
    public void stopApplication() {
        // The session's last debounced credits, pushed out before the thread ends.
        if (save != null) save.flush();
        stopped = true;
    }

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
        // Numeric action keys, declared as constants so the BANTUAN page, the in-game hint and
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
        else if (screen == CREDITS) updateCredits(upEdge, downEdge, fireEdge || leftEdge || rightEdge);
        else if (screen == STAGES) updateStages(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == SETTINGS) updateSettings(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == HELP) updateHelp(upEdge, downEdge, fireEdge, leftEdge, rightEdge);
        else if (screen == PREP) updatePrep(upEdge, downEdge, fireEdge, leftEdge || rightEdge);
        else if (screen == RESULTS) updateResults(upEdge, downEdge, fireEdge || leftEdge, rightEdge);
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
            case MAIN_CREDITS: screen = CREDITS; creditsScroll = 0; break;
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
        // Up/Down scroll the status list, stopping at the top and at the row that puts
        // "(original art)" last in the window, so the list never scrolls into blank space.
        if (up) {
            if (idolDetailScroll > 0) idolDetailScroll--;
            return;
        }
        if (down) {
            int max = idolMaxScroll();
            if (idolDetailScroll < max) idolDetailScroll++;
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
            if (up || down || left || right) {
                // The popup owns the input while it is open: a d-pad press moves the answer,
                // never the list behind it, so cancelling cannot scroll the page underneath.
                shopConfirmChoice = (shopConfirmChoice == SHOP_CHOICE_YES)
                        ? SHOP_CHOICE_NO : SHOP_CHOICE_YES;
            } else if (fire) {
                int target = ContentCatalog.upgradeGroupStart(shopTab) + menuIndex;
                boolean yes = shopConfirmChoice == SHOP_CHOICE_YES;
                closeShopPopup();
                if (yes) confirmShopAction(target);
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

    /**
     * Credits navigation: Up/Down scroll the page, FIRE or L/R leaves it.
     *
     * <p>The scroll is clamped so the last row of the text is the last row reachable -- pressing
     * Down at the bottom stops rather than running on into blank space below the list, the same
     * rule the idol status list follows.
     */
    private void updateCredits(boolean up, boolean down, boolean back) {
        if (up) {
            if (creditsScroll > 0) creditsScroll--;
            return;
        }
        if (down) {
            if (creditsScroll < creditsMaxScroll()) creditsScroll++;
            return;
        }
        if (back) screen = MENU;
    }

    /** Largest scroll offset that still fills the window with text. */
    private int creditsMaxScroll() {
        int rows = buildCreditRows().length;
        int max = rows - creditVisibleRows();
        return max > 0 ? max : 0;
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
            buyConfirm = false;
            openShopPopup(true);
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
        if (!buyConfirm) {                                // first press only asks
            buyConfirm = true;
            openShopPopup(false);
            return;
        }
        buyConfirm = false;
        if (cost > 0 && !save.spendCoins(cost)) return;  // free rows never touch the balance
        save.setUpgradeLevel(upgradeIndex, level + 1);
    }

    /** Opens the asking popup with Ya highlighted, so a single FIRE still confirms. */
    private void openShopPopup(boolean refund) {
        if (refund) { refundConfirm = true; buyConfirm = false; }
        else { buyConfirm = true; refundConfirm = false; }
        shopConfirmChoice = SHOP_CHOICE_YES;
    }

    /** Closes whichever popup is open and resets the answer. */
    private void closeShopPopup() {
        buyConfirm = false;
        refundConfirm = false;
        shopConfirmChoice = SHOP_CHOICE_YES;
    }

    /**
     * Runs the action the popup was asking about. Split from {@link #buyUpgrade} so confirming
     * executes in one press instead of re-arming and waiting for a second one -- with an explicit
     * Ya/Tidak popup, FIRE is the answer, not a request to open the question again.
     */
    private void confirmShopAction(int upgradeIndex) {
        ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(upgradeIndex);
        if (def == null || def.locked) return;
        if (def.buyKind == ContentCatalog.BUY_ACTION) { save.refundAll(); return; }
        if (def.buyKind == ContentCatalog.BUY_TOGGLE) { save.setHardcore(!save.hardcore()); return; }
        int level = save.upgradeLevel(upgradeIndex);
        long cost = ContentCatalog.upgradeCost(def, level);
        if (cost < 0) return;
        if (cost > 0 && !save.spendCoins(cost)) return;
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
        // Audio, Help, Back. The controls used to have their own page; they now live in Help,
        // which already listed the same bindings in Indonesian.
        if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 3);
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
        else if (menuIndex == 1) { screen = HELP; helpScroll = 0; }
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
        if (s.timeStage) return save.isCompletedByAny(2);
        return save.isCompletedByAny(s.unlockRequirement);
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

    /**
     * Shared page background: the Main Menu's own star stage, drawn from the pre-dimmed
     * backdrop so an inner page opens on the same scene the title screen shows. The dim is
     * baked into menu_page_bg.png because MIDP 2.0 cannot darken at runtime; without it the
     * bright stage floor would swallow the white ink. The white rule still closes the header
     * band. When the asset is missing (partial build) the old flat bands stand in, which
     * keeps every page readable without the backdrop.
     */
    private void drawPageBackground(Graphics g, int x, int y) {
        Image bg = assets.menuPageBg();
        if (bg != null) {
            g.drawImage(bg, x, y, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(MENU_BG_R, MENU_BG_G, MENU_BG_B);
            g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
            g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
            g.fillRect(x, y + MENU_BODY_Y, Viewport.MIN_W, Viewport.MIN_H - MENU_BODY_Y);
        }
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.fillRect(x, y + MENU_BODY_Y - 1, Viewport.MIN_W, 1);
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
     * Main Menu, rebuilt after the reference game's title screen: the rainbow-burst backdrop
     * fills the screen (with a baked dark icon bar at the bottom), the full 47-strong roster
     * stands in four staggered rows behind a floating, occasionally glinting HoloCure logo, and
     * the eight entries live in an icon bar along the bottom instead of a vertical list. Each
     * crowd member bobs on its own phase and graduated members sparkle over the head. The
     * selected icon lifts off the bar and bobs on the clock, a white underline marks it for
     * anyone who cannot track the motion, and the gold label box slides the entry's name in at
     * the bottom right. The name comes pre-rendered (menu_text_*) at one notch below MIDP's
     * smallest font, with the drawn SIZE_SMALL string kept as the partial-build fallback. All
     * motion reads clockMs, recorded once per tick by update(long), so this method stays pure
     * and a headless clock of 0 simply freezes every effect at rest.
     */
    private void drawMainMenu(Graphics g, int x, int y) {
        // 1. Rainbow-burst backdrop (full 320x240, with a baked dark icon bar at the bottom).
        Image bg = assets.titleBg();
        if (bg != null) {
            g.drawImage(bg, x, y, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(TITLE_DEEP_R, TITLE_DEEP_G, TITLE_DEEP_B);
            g.fillRect(x, y, Viewport.MIN_W, Viewport.MIN_H);
        }

        // 2. Roster crowd. Back rows first (low CAST index) so the front row overlaps them,
        //    matching the reference. Each member bobs on its own phase for a lively crowd.
        for (int i = 0; i < CAST_COUNT; i++) {
            Image ch = assets.titleChar(i);
            if (ch == null) continue;
            g.drawImage(ch, x + CAST_X[i], y + CAST_Y[i] + charBob(i), Graphics.TOP | Graphics.LEFT);
        }

        // 2b. Sparkle over each graduated member's head, alternating two frames every ~300ms.
        long sparkleTick = clockMs / 300;
        Image sparkle = (sparkleTick % 2 == 0) ? assets.sparkle0() : assets.sparkle1();
        if (sparkle != null) {
            for (int i = 0; i < CAST_COUNT; i++) {
                if (!GRADUATED[i]) continue;
                Image ch = assets.titleChar(i);
                int cw = (ch != null) ? ch.getWidth() : 24;
                int sx = x + CAST_X[i] + cw / 2 - sparkle.getWidth() / 2;
                int sy = y + CAST_Y[i] + charBob(i) - sparkle.getHeight() / 2;
                g.drawImage(sparkle, sx, sy, Graphics.TOP | Graphics.LEFT);
            }
        }

        // 3. HoloCure logo, centred in the top-row gap, bobbing, with a periodic shine sweep.
        Image logo = assets.titleLogo();
        if (logo != null) {
            int logoY = y + TITLE_LOGO_Y + bob(600, 3);
            // Glint for a short window every ~1.6s so the wordmark shines like the reference.
            if ((clockMs % 1600) < 320) {
                Image shine = assets.menuLogoShine();
                if (shine != null) logo = shine;
            }
            g.drawImage(logo, x + TITLE_LOGO_X, logoY, Graphics.TOP | Graphics.LEFT);
        }

        g.setColor(TITLE_TEXT_R, TITLE_TEXT_G, TITLE_TEXT_B);
        drawRight(g, x, y + 4, "version 0.1");

        drawBar(g, x, y);

        // Gold label box with the highlighted entry's name, bottom right.
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

    /** Per-member crowd bob: a small sine offset, phase-shifted by index so the crowd ripples. */
    private int charBob(int index) {
        return (int) (Math.sin((double) clockMs / 800.0 + index * 0.5) * 2);
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
     * CHOOSE YOUR IDOL — grid 5 karakter (tanpa tombol Kembali).
     * Layout: 5 kotak horizontal (48x56), detail panel di bawah dengan scroll.
     * Setiap kotak: portrait di atas backdrop stage (transparan), border putih (dipilih =
     *               frame putih berlapis).
     * Detail panel: border 1px transparan, nama karakter di baris penuh atas, idle sprite
     *               berdiri di garis tanah bersama di kiri, kolom status di kanan (scroll
     *               Atas/Bawah).
     * Semua konstanta layout ada di blok IDOL_* di atas file.
     */
    private void drawChooseIdol(Graphics g, int x, int y) {
        // ===== HEADER: CHOOSE YOUR IDOL (Y=0-40, centred in the dark title band) =====
        // Drawn through the same helper and the same TITLE_TOP as every other page heading, so
        // the reference heading and the pages that follow it cannot drift apart.
        title(g, x, y, "CHOOSE YOUR IDOL");

        // ===== GRID: 5 karakter (Y=30-108, boxes at Y=41-97) =====
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

            // Box: just a frame over the stage backdrop — the mat is gone so the star field
            // shows through behind each portrait; the picked window is a doubled white ring,
            // the idle one a single white edge.
            if (selected) {
                g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
                g.drawRect(x + bx - 2, y + by - 2, boxW + 3, boxH + 3);
                g.drawRect(x + bx, y + by, boxW - 1, boxH - 1);
            } else {
                g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
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

        // ===== DETAIL PANEL: Y=114-240 (no divider line) =====
        ContentCatalog.CharacterDef sel = ContentCatalog.CHARACTERS[menuIndex];
        int px = x + IDOL_PANEL_X;      // 12
        int py = y + IDOL_PANEL_Y;      // 114
        int pw = IDOL_PANEL_W;          // 296
        int ph = IDOL_PANEL_H;          // 114

        // The panel is a bare frame with no fill, matching the boxes above: the stage backdrop
        // runs behind the whole window, and the 1px white edge is what defines it.
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.drawRect(px, py, pw - 1, ph - 1);

        // The name gets its own full-width row across the top of the panel, so the status column
        // below it starts clear of it instead of overprinting it. It takes the gold HoloCure
        // gives item and character names.
        g.setColor(MENU_GOLD_R, MENU_GOLD_G, MENU_GOLD_B);
        drawText(g, px + 8, py + 6, sel.name, Font.STYLE_BOLD, Font.SIZE_SMALL);

        // Everything below the name row: sprite on the left, status column on the right.
        // The breathing room at the bottom comes from the panel's own margin to the screen
        // edge, so the body only needs a small internal pad to clear the border.
        int padSide = 8;
        int bodyTop = py + IDOL_NAME_ROW_H;
        int bodyH = ph - IDOL_NAME_ROW_H - IDOL_BODY_PAD_BOTTOM;

        // Idle sprite on the left of the body, standing on a shared ground line. px/py already carry
        // the canvas origin, so they are used as-is here.
        //
        // The five idle sheets are very different sizes -- Calli is 46px tall, Ame 30px -- and the
        // frames within one loop differ too, so centring each sprite in the body left the whole
        // cast standing at different heights and made characters shuffle every tick. Every sprite
        // has its feet on the last row of its own canvas, so aligning the canvas bottom lines the
        // feet up exactly while leaving each character its own height.
        Image idleSprite = assets.characterIdle(menuIndex, idolAnimFrame);
        if (idleSprite != null) {
            int sy = py + IDOL_FEET_Y - (idleSprite.getHeight() - 1);
            int sx = px + IDOL_SPRITE_X
                    + IDOL_SPRITE_DX[menuIndex % IDOL_SPRITE_DX.length];
            g.drawImage(idleSprite, sx, sy, Graphics.TOP | Graphics.LEFT);
        }

        // The status column starts at x=136, the midpoint between the S and the E of CHOOSE in
        // the title above (S ends at 134.7, E starts at 137.0), so the block reads as sitting
        // directly under the heading right where the word breaks.
        int textX = px + IDOL_TEXT_X;
        int textW = pw - IDOL_TEXT_X - padSide;
        g.clipRect(textX, bodyTop, textW, bodyH);

        // Build detail lines
        String[] lines = buildIdolDetailLines(sel);
        int lineH = IDOL_LINE_H;
        int detailStartY = bodyTop - idolDetailScroll * lineH;
        int maxVisible = idolVisibleLines();

        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        Font rowFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int visible = 0;
        int bodyBottom = bodyTop + bodyH;
        for (int i = 0; i < lines.length && visible < maxVisible; i++) {
            int lineY = detailStartY + i * lineH;
            if (lineY + lineH >= bodyTop && lineY <= bodyBottom) {
                g.setFont(rowFont);
                g.drawString(ellipsize(rowFont, lines[i], textW), textX, lineY,
                        Graphics.TOP | Graphics.LEFT);
                visible++;
            }
        }

        // MIDP clipRect intersects rather than replaces, so this widens the clip back to the
        // whole panel instead of leaving the next frame stuck inside the text column.
        g.clipRect(0, 0, Viewport.MIN_W, Viewport.MIN_H);
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
        // frame geometry is untouched; only its colours changed. The mat uses the deep panel
        // colour because the portraits are bright sprites (median luma 195) and need a dark
        // backing, and the outline uses the white ink. The caption sits below the frame on the
        // dark page, so it stays in the white ink.
        g.setColor(MENU_DEEP_R, MENU_DEEP_G, MENU_DEEP_B);
        g.fillRect(x + CHARS_PORTRAIT_X, y + CHARS_PORTRAIT_Y, CHARS_PORTRAIT_W, CHARS_PORTRAIT_H);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
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
    // unlocked-and-allowed; the dim cyan means unavailable and deliberately recedes on the dark
    // panel. The selected row is not a third colour: it is marked by the "> " arrow, bold style
    // and an outline frame instead. The "[mode]" and "[locked]" suffixes keep the state readable
    // in text regardless of the colour.
    private static final int STAGE_UNLOCKED_R = 255, STAGE_UNLOCKED_G = 255, STAGE_UNLOCKED_B = 255;
    private static final int STAGE_LOCKED_R = 74, STAGE_LOCKED_G = 143, STAGE_LOCKED_B = 176;
    private static final int STAGE_ROW_X = 12;
    private static final int STAGE_ROW_Y = 40;
    private static final int STAGE_ROW_PITCH = 16;
    private static final int STAGE_FRAME_X = 8;
    private static final int STAGE_FRAME_W = 244;
    private static final int STAGE_FRAME_H = 14;

    /**
     * Toko page, reached from the main menu: the submenu Beli Upgrade / Armory / Keluar, and
     * nothing else — the balance deliberately does not appear here, so the page is only ever
     * the three entries. Every purchase now lives one level deeper — the old single
     * "Shop" page is gone, and its Skill Pasif row with it, because the skill is free
     * forever now.
     */
    private void drawShop(Graphics g, int x, int y) {
        title(g, x, y, "TOKO");
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
            // Rows start at 62 rather than 56: the selection frame sits two rows above its text,
            // and 56 would put that frame's top edge at 54, under the coin plate's floor at 55.
            int rowY = y + 62 + slot * 26;
            boolean selected = i == menuIndex;
            if (i < count) drawUpgradeRow(g, x, rowY, start + i, selected, balance);
            else {
                ink(g);
                drawText(g, x + 12, rowY, (selected ? "> " : "  ") + "Kembali",
                        selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            }
            if (selected) {
                g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
                g.drawRect(x + 8, rowY - 2, 288, 16);
            }
        }

        // Detail panel: separator, the highlighted row's full text, and the key hint. The pending
        // question itself lives in the popup below rather than replacing this line.
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.drawLine(x + 8, y + 184, x + 312, y + 184);
        ink(g);
        if (menuIndex >= 0 && menuIndex < count) {
            ContentCatalog.UpgradeDef def = ContentCatalog.upgrade(start + menuIndex);
            int level = shopRowLevel(def, start + menuIndex);
            // The item name takes the HoloCure gold; its level tail stays ink, right after it.
            g.setColor(MENU_GOLD_R, MENU_GOLD_G, MENU_GOLD_B);
            drawText(g, x + 12, y + 190, def.name, Font.STYLE_BOLD, Font.SIZE_SMALL);
            Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            g.drawString("   Lv " + level + "/" + def.maxLevel,
                    x + 12 + bold.stringWidth(def.name), y + 190, Graphics.TOP | Graphics.LEFT);
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
     * Modal shop confirmation, drawn last on the page so it covers the list.
     *
     * <p>It names the action, spells out its effect on the coin balance, and offers an explicit
     * <b>Ya / Tidak</b> pair. The answers used to be implicit -- FIRE bought, any d-pad press
     * cancelled -- which meant the popup never actually asked a question the player could read,
     * and a second look at a purchase was the same gesture as the first. Now the highlighted
     * answer is stated on screen and FIRE commits the one that is highlighted.
     *
     * <p>Ya is highlighted when the popup opens, so a single FIRE still confirms and anyone used
     * to the old gesture is not surprised; a d-pad press toggles the answer while the popup holds
     * the input, so the list behind it cannot move while the question is open.
     */
    private void drawShopPopup(Graphics g, int x, int y, String heading, String[] lines) {
        int w = 248, h = 108;
        int px = x + (Viewport.MIN_W - w) / 2, py = y + 58;
        g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
        g.fillRect(px, py, w, h);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.drawRect(px + 1, py + 1, w - 3, h - 3);
        ink(g);
        drawText(g, px + 12, py + 10, heading, Font.STYLE_BOLD, Font.SIZE_SMALL);
        for (int i = 0; i < lines.length; i++) {
            drawText(g, px + 12, py + 30 + i * 16, lines[i], Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        // The two answers, spaced apart so they read as a choice rather than a sentence, with the
        // highlighted one bold and marked.
        int answersY = py + h - 26;
        boolean yes = shopConfirmChoice == SHOP_CHOICE_YES;
        drawShopAnswer(g, px + 36, answersY, "Tidak", !yes);
        drawShopAnswer(g, px + 148, answersY, "Ya", yes);
    }

    /** One answer in the popup's Ya/Tidak pair. The cursor marks the highlighted one. */
    private void drawShopAnswer(Graphics g, int ax, int ay, String label, boolean selected) {
        ink(g);
        drawText(g, ax, ay, (selected ? "> " : "  ") + label,
                selected ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
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
        String head = label + " Lv" + level + "/" + def.maxLevel;
        String tail = maxed ? " MAX" : (cost == 0 ? "  gratis" : "  " + cost);
        drawText(g, x + 12, rowY, head, style, Font.SIZE_SMALL);
        // The price takes the HoloCure gold when the balance can cover it; a price that cannot
        // be paid keeps the row's own colour, so colour never contradicts the words.
        if (tail.length() > 0) {
            if (affordable && !maxed) g.setColor(MENU_GOLD_R, MENU_GOLD_G, MENU_GOLD_B);
            Font f = Font.getFont(Font.FACE_SYSTEM, style, Font.SIZE_SMALL);
            g.drawString(tail, x + 12 + f.stringWidth(head), rowY, Graphics.TOP | Graphics.LEFT);
        }
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
            // Same 62px start as the upgrade list: the selection frame clears the coin plate.
            int rowY = y + 62 + slot * 22;
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
                g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
                g.drawRect(x + 8, rowY - 2, 288, 16);
            }
        }
        ink(g);
        drawText(g, x + 12, y + 190, "Nonaktifkan senjata/item: butuh Kustomisasi.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        drawText(g, x + 12, y + 208, "Kustomisasi masih terkunci, jadi semua tetap aktif.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    /**
     * Placeholder page for Papan Peringkat, Pencapaian and Holo House. The entry exists, the
     * screen exists and it renders its own title — it simply does not open yet, and says so
     * instead of failing silently.
     *
     * <p>Everything here is centred and laid out from the 240px page height rather than from
     * hand-picked offsets, so the block reads as one composed panel and stays put if the heading
     * grows a line. The word TERKUNCI is bare: no frame around it and no brackets, just the
     * word in the white ink — the big status word reads as a headline, while the small
     * "[Terkunci]" / "[locked]" suffixes on rows keep the recessive locked colour. A box and
     * {@code [ ]} made it look like a button; it is a status.
     */
    private void drawLocked(Graphics g, int x, int y) {
        title(g, x, y, lockedTitle);

        int ty = y + 84;
        ink(g);
        drawCentered(g, ty, "TERKUNCI", Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        ty += 26;

        ink(g);
        drawCentered(g, ty, "Halaman ini sudah dibuat", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += 16;
        drawCentered(g, ty, "tetapi belum dibuka.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += 16;
        drawCentered(g, ty, "Datanya menyusul di update berikutnya.", Font.STYLE_PLAIN, Font.SIZE_SMALL);

        // A single accent rule under the block, centred, closes the composition without needing
        // a "press anything to go back" line.
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        int ruleW = 96;
        g.fillRect(x + (Viewport.MIN_W - ruleW) / 2, y + 214, ruleW, 1);
    }

    /**
     * Credits, as a scrolling list of centred rows.
     *
     * <p>The text is longer than one screen, so it is data rather than a hand-stepped sequence of
     * draw calls: {@link #buildCreditRows()} returns one string per row and the page scrolls by
     * rows. That means adding a credit is a one-line edit that cannot break the layout, where the
     * earlier version had to have its coordinates re-counted.
     *
     * <p>Every row is centred from its measured width, so nothing is positioned by eye. A row
     * equal to {@link #CREDIT_RULE} draws as a short accent rule instead of text, which is how the
     * blocks are separated. The window is {@link #creditVisibleRows()} rows tall; Down stops at
     * {@link #creditsMaxScroll()} so the last credit is the last row reachable, and a position
     * marker shows how much is left below.
     */
    private void drawCredits(Graphics g, int x, int y) {
        title(g, x, y, "CREDITS");

        String[] rows = buildCreditRows();
        int visible = creditVisibleRows();
        int first = creditsScroll;
        if (first > rows.length - visible) first = rows.length - visible;
        if (first < 0) first = 0;

        int ty = y + CREDIT_TOP;
        for (int i = first; i < rows.length && i < first + visible; i++) {
            String row = rows[i];
            if (CREDIT_RULE.equals(row)) {
                creditsRule(g, x, ty);
            } else if (row.length() > 0) {
                boolean heading = creditIsHeading(row);
                if (heading) g.setColor(MENU_GOLD_R, MENU_GOLD_G, MENU_GOLD_B);
                else ink(g);
                drawCentered(g, ty, row, heading ? Font.STYLE_BOLD : Font.STYLE_PLAIN,
                        Font.SIZE_SMALL);
            }
            ty += CREDIT_LINE_GAP;
        }

        drawCreditsScrollMarker(g, x, y, first, rows.length, visible);
    }

    /**
     * The credits text. One entry per row, in reading order.
     *
     * <p>{@link #CREDIT_RULE} separates blocks and an empty string is a deliberate blank line;
     * both are needed because the rules sit a row apart from the text they divide. Headings are
     * recognised by {@link #creditIsHeading}, so a heading can be restyled without reformatting
     * the text.
     */
    private String[] buildCreditRows() {
        return new String[] {
            "HOLOJAVA", "Game Java ME bergaya HoloCure", CREDIT_RULE,
            "PENULIS", "by AntoMay", "github.com/AntoMay", "facebook.com/epoy.saputra.73", CREDIT_RULE,
            "INSPIRASI", "HoloCure, fan game hololive", "oleh kayfstarter", CREDIT_RULE,
            "SUMBER DATA", "holocure.wiki.gg (Update 0.7)",
            "Data karakter, senjata, item,", "harga dan stage disusun dari sana.", CREDIT_RULE,
            "TEKNOLOGI", "Java ME (MIDP 2.0) untuk HP", "MIDP 1.1 untuk perangkat lama", CREDIT_RULE,
            "PROYEK", "Kode, stage dan tata letak", "HoloJava oleh AntoMay", CREDIT_RULE,
            "TOOLS", "Opencode", CREDIT_RULE,
            "TERIMA KASIH", "kepada para Hololive member", "dan komunitas penggemarnya",
            "", "Sampai jumpa di stage berikutnya."
        };
    }

    /** Headings are the short upper-case rows; the rest are ordinary text. */
    private static boolean creditIsHeading(String row) {
        if (row.length() < 3) return false;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c >= 'a' && c <= 'z') return false;
        }
        return true;
    }

    /** How many credit rows fit between {@link #CREDIT_TOP} and the scroll marker. */
    private static int creditVisibleRows() {
        return (CREDIT_BOTTOM - CREDIT_TOP) / CREDIT_LINE_GAP;
    }

    /**
     * A scroll marker under the list.
     *
     * <p>Credits used to fit on one screen, so nothing told the reader there was more. It only
     * appears while there is more below, which is when it is useful; at the very bottom of the
     * list it disappears rather than showing an empty track.
     */
    private void drawCreditsScrollMarker(Graphics g, int x, int y, int first, int total,
            int visible) {
        int more = total - (first + visible);
        if (more <= 0) return;
        int trackW = 96;
        int trackX = x + (Viewport.MIN_W - trackW) / 2;
        int trackY = y + CREDIT_BOTTOM + 4;
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.fillRect(trackX, trackY, trackW, 2);
        int thumbW = trackW * visible / total;
        if (thumbW < 6) thumbW = 6;
        g.fillRect(trackX, trackY, thumbW, 2);
    }

    /** A short centred accent rule between credit blocks. */
    private void creditsRule(Graphics g, int x, int y) {
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        int ruleW = 72;
        g.fillRect(x + (Viewport.MIN_W - ruleW) / 2, y + 5, ruleW, 1);
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
                g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
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
                "Help", "Back" };
        list(g, x, y + 65, items, menuIndex);
    }

    private void updateHelp(boolean up, boolean down, boolean fire, boolean left, boolean right) {
        // The bindings list runs past the bottom of the page, so Up/Down scroll it the way
        // Credits does; the confirming keys (fire, and the L/R pair every submenu uses)
        // still back out to Pengaturan.
        if (left || right || fire) { screen = SETTINGS; helpScroll = 0; return; }
        if (up) helpScroll = helpScroll > HELP_SCROLL_STEP ? helpScroll - HELP_SCROLL_STEP : 0;
        if (down && helpScroll < HELP_MAX_SCROLL) {
            helpScroll += HELP_SCROLL_STEP;
            if (helpScroll > HELP_MAX_SCROLL) helpScroll = HELP_MAX_SCROLL;
        }
    }

    /**
     * BANTUAN — the one place controls are explained.
     *
     * <p>This page absorbed the old separate CONTROLS screen, which Pengaturan used to link to.
     * The bindings below are the union of both: the Indonesian menu/gameplay rows Help already
     * had, plus the D-pad, movement and automatic-fire notes that only Controls carried. Keeping
     * one page means a player who opens Help cannot miss the gameplay half.
     */
    private void drawHelp(Graphics g, int x, int y) {
        title(g, x, y, "BANTUAN");
        ink(g);
        // The bindings run past the bottom of the page, so the body scrolls under the title
        // band: clip to the band below the header rule, then shift every row up by the scroll.
        g.clipRect(x, y + 42, Viewport.MIN_W, Viewport.MIN_H - 42);
        int ty = y + 48 - helpScroll;
        int lh = 16;
        drawText(g, x + 16, ty, "Navigasi Menu:", Font.STYLE_BOLD, Font.SIZE_SMALL);
        ty += lh;
        // D-pad icon (controller_0) + text
        Image dpadIcon = assets.controllerIcons() != null ? assets.controllerIcons()[0] : null;
        if (dpadIcon != null) {
            g.drawImage(dpadIcon, x + 24, ty + 2, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + dpadIcon.getWidth() + 4, ty, "pindah & pilih menu", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } else {
            drawText(g, x + 24, ty, "D-pad: pindah & pilih menu", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        ty += lh;
        // FIRE/5 key icon (keybind_5 or keybind_0 for FIRE)
        Image fireIcon = assets.keybindIcons() != null ? assets.keybindIcons()[5] : null; // keybind_5 = FIRE
        if (fireIcon != null) {
            g.drawImage(fireIcon, x + 24, ty + 1, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + fireIcon.getWidth() + 4, ty, "pilih / konfirmasi", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } else {
            drawText(g, x + 24, ty, "FIRE / 5: pilih / konfirmasi", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        ty += lh;
        // Left/Right arrows (controller_2 = left, controller_3 = right?)
        Image leftIcon = assets.controllerIcons() != null ? assets.controllerIcons()[2] : null;
        Image rightIcon = assets.controllerIcons() != null ? assets.controllerIcons()[3] : null;
        if (leftIcon != null && rightIcon != null) {
            g.drawImage(leftIcon, x + 24, ty + 2, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + leftIcon.getWidth() + 4, ty, "pindah tab / kembali", Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.drawImage(rightIcon, x + 24 + leftIcon.getWidth() + 4 + g.getFont().stringWidth("pindah tab / kembali") + 8, ty + 2, Graphics.TOP | Graphics.LEFT);
        } else {
            drawText(g, x + 24, ty, "Kiri/Kanan: pindah tab / kembali", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        ty += lh + 6;
        drawText(g, x + 16, ty, "Dalam Gameplay:", Font.STYLE_BOLD, Font.SIZE_SMALL);
        ty += lh;
        // D-pad for movement
        Image moveIcon = assets.controllerIcons() != null ? assets.controllerIcons()[0] : null;
        if (moveIcon != null) {
            g.drawImage(moveIcon, x + 24, ty + 2, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + moveIcon.getWidth() + 4, ty, "gerak", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } else {
            drawText(g, x + 24, ty, "D-pad: gerak", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        ty += lh;
        // 1 / GAME A for basic attack
        Image key1Icon = assets.keybindIcons() != null ? assets.keybindIcons()[1] : null;
        if (key1Icon != null) {
            g.drawImage(key1Icon, x + 24, ty + 1, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + key1Icon.getWidth() + 4, ty, "serang dasar", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } else {
            drawText(g, x + 24, ty, "1 / GAME A: serang dasar", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        ty += lh;
        // 9 for Special
        Image key9Icon = assets.keybindIcons() != null ? assets.keybindIcons()[9] : null;
        if (key9Icon != null) {
            g.drawImage(key9Icon, x + 24, ty + 1, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + key9Icon.getWidth() + 4, ty, "Special (butuh upgrade Special Attack)", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } else {
            drawText(g, x + 24, ty, "9: Special (butuh upgrade Special Attack)", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        ty += lh;
        // 5 / FIRE for pause
        Image key5Icon = assets.keybindIcons() != null ? assets.keybindIcons()[5] : null;
        Image key0Icon = assets.keybindIcons() != null ? assets.keybindIcons()[0] : null;
        if (key5Icon != null && key0Icon != null) {
            g.drawImage(key5Icon, x + 24, ty + 1, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + key5Icon.getWidth() + 4, ty, "pause", Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.drawImage(key0Icon, x + 24 + key5Icon.getWidth() + 4 + g.getFont().stringWidth("pause   ") + 4, ty + 1, Graphics.TOP | Graphics.LEFT);
            drawText(g, x + 24 + key5Icon.getWidth() + 4 + g.getFont().stringWidth("pause   ") + 4 + key0Icon.getWidth() + 4, ty, "pause alternatif", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        } else {
            drawText(g, x + 24, ty, "5 / FIRE: pause   0: pause alternatif", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }
        ty += lh + 6;
        drawText(g, x + 16, ty, "Otomatis:", Font.STYLE_BOLD, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "Skill karakter aktif sendiri tiap 15 detik.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "Senjata orbiting & pasif menembak sendiri.", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh + 4;
        drawText(g, x + 16, ty, "Opencode (Pengembangan):", Font.STYLE_BOLD, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "Kode: github.com/AntoMay/HoloJava", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        ty += lh;
        drawText(g, x + 24, ty, "CLI: opencode (AI coding agent)", Font.STYLE_PLAIN, Font.SIZE_SMALL);
        // Restore the full-page clip so the next screen cannot inherit the scrolled band.
        g.clipRect(0, 0, Viewport.MIN_W, Viewport.MIN_H);
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

    /** Level-up popup: a dark panel with the double white frame HoloCure windows wear, floating
     *  over the frozen run behind it. The offer list scrolls inside the box once the rows no
     *  longer fit, so the popup keeps one fixed size no matter how many offers (or Reroll/Tahan
     *  rows) the roll produced. */
    private void drawLevelUp(Graphics g, int x, int y) {
        // No full-screen dim: as requested, only the dark framed panel itself floats over the
        // frozen run. drawPause keeps the full-page treatment; this popup does not.
        int panelW = 248;
        int rowH = 20;
        int rows = 5; // list window: how many offers fit the box before the list scrolls
        int panelH = 34 + rows * rowH + 6;
        int px = x + (Viewport.MIN_W - panelW) / 2;
        int py = y + (Viewport.MIN_H - panelH) / 2;
        // Same panel trio as every menu page, framed in the white every HoloCure window wears.
        g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
        g.fillRect(px, py, panelW, panelH);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.drawRect(px, py, panelW - 1, panelH - 1);
        g.drawRect(px + 2, py + 2, panelW - 5, panelH - 5);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
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
        Font rowFontSel = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        for (int row = 0; row < rows; row++) {
            int i = levelUpScroll + row;
            if (i >= count) break;
            int ry = listY + row * rowH;
            boolean selected = i == sel;
            // The picked offer wears a white frame like every other selected row in the game;
            // no row flips to dark-on-colour text any more.
            if (selected) {
                g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
                g.drawRect(px + 5, ry, panelW - 10, rowH - 2);
            }
            // White ink carries the offers, collab gold keeps the rare pick standing out
            // whether or not the cursor is on it, and the type tag recedes into the label
            // grey. Text is centred inside the frame box so the border reads even.
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            if (world.offerIsCollab(i)) g.setColor(MENU_GOLD_R, MENU_GOLD_G, MENU_GOLD_B);
            g.setFont(selected ? rowFontSel : rowFont);
            int ty = ry + (rowH - 2 - rowFont.getHeight()) / 2;
            g.drawString((selected ? "> " : "  ") + world.offerLabel(i), px + 10, ty,
                    Graphics.TOP | Graphics.LEFT);
            String tag = world.offerType(i);
            g.setFont(rowFont);
            if (selected) g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            else inkDim(g);
            g.drawString(tag, px + panelW - 10 - rowFont.stringWidth(tag), ty,
                    Graphics.TOP | Graphics.LEFT);
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

    /** Pause popup: a centred panel over the frozen run, like the level-up popup.
     *  Two simple choices: Resume / Return to Menu. No full-screen dim. */
    private void drawPause(Graphics g, int x, int y) {
        int panelW = 200;
        int rowH = 22;
        int rows = PAUSE_ITEMS.length;
        int panelH = 30 + rows * rowH + 10;
        int px = x + (Viewport.MIN_W - panelW) / 2;
        int py = y + (Viewport.MIN_H - panelH) / 2;

        // Panel with the level-up popup's double white frame, so both floaters agree.
        g.setColor(MENU_PANEL_R, MENU_PANEL_G, MENU_PANEL_B);
        g.fillRect(px, py, panelW, panelH);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.drawRect(px, py, panelW - 1, panelH - 1);
        g.drawRect(px + 2, py + 2, panelW - 5, panelH - 5);

        // Title
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        Font titleFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        g.setFont(titleFont);
        String titleText = "PAUSED";
        g.drawString(titleText, px + (panelW - titleFont.stringWidth(titleText)) / 2, py + 8,
                Graphics.TOP | Graphics.LEFT);

        // List items: every row in the same white ink, the picked one inside a white frame like
        // the list pages use. Text is centred inside the frame's box (not the bare row) so the
        // border reads even top and bottom at any font height.
        int listY = py + 30;
        Font rowFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font rowFontSel = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            int ry = listY + i * rowH;
            boolean on = i == pauseIndex;
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            if (on) g.drawRect(px + 5, ry, panelW - 10, rowH - 2);
            g.setFont(on ? rowFontSel : rowFont);
            int ty = ry + (rowH - 2 - rowFont.getHeight()) / 2;
            String prefix = on ? "> " : "  ";
            g.drawString(prefix + PAUSE_ITEMS[i], px + 10, ty, Graphics.TOP | Graphics.LEFT);
        }
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

    /**
     * Page heading, centred on the canvas and sitting at the same height as CHOOSE YOUR IDOL.
     *
     * <p>This used to draw at a fixed {@code x + 90}, which only lined up by accident for
     * headings of one particular length — short ones sat visibly left of centre and long ones
     * ran off the right. Centring from the measured width fixes both and makes every page agree
     * with the Choose Your Idol heading, which is the reference the rest of the UI was laid out
     * against.
     *
     * <p>{@code TITLE_TOP} is shared with {@code drawChooseIdol} on purpose: the two must not
     * drift apart, or the same heading sits at two different heights depending on the screen.
     */
    private void title(Graphics g, int x, int y, String text) {
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
        g.setFont(f);
        g.drawString(text, x + (Viewport.MIN_W - f.stringWidth(text)) / 2, y + TITLE_TOP,
                Graphics.TOP | Graphics.LEFT);
    }

    /**
     * Coin balance on the purchase pages (Beli Upgrade, its tab submenu, Armory), top-right of
     * the header: a dark plate behind the white number and no frame around it — the white ring
     * it used to wear read as a button, while the balance is plain status. The Toko menu itself
     * carries no balance at all (see {@link #drawShop}).
     *
     * <p>{@code COIN_TOP} (44) sits below the heading rather than level with it: the heading is
     * {@code TITLE_TOP} (8) in {@link Font#SIZE_LARGE}, which reaches about row 24, and the plate
     * then starts clear of the white rule that closes the header band, so the balance reads as
     * its own element instead of running into the title.
     */
    private void drawCoin(Graphics g, int x, int y, long balance) {
        String text = "Koin: " + balance;
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        int tw = f.stringWidth(text);
        // Plate hugs rows 41-55: its floor stops short of the first list row (56) on the pages
        // that carry one, so it can never overprint a row. It starts three rows above COIN_TOP
        // because the text is drawn three rows below its own top.
        int bx = x + Viewport.MIN_W - tw - 12;
        int by = y + COIN_TOP - 3;
        g.setColor(MENU_DEEP_R, MENU_DEEP_G, MENU_DEEP_B);
        g.fillRect(bx, by, tw + 8, 15);
        g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
        g.setFont(f);
        g.drawString(text, bx + 4, by + 3, Graphics.TOP | Graphics.LEFT);
    }

    /** Shared selectable list. The selected row is marked the same way everywhere: the "> " arrow,
     *  bold style and the white frame the shop and stage lists draw, so every list on the game
     *  agrees on one selection treatment. The palette keeps one text colour on the dark panel,
     *  so the selection is carried by the frame, the marker and the weight rather than by a
     *  second text colour. */
    private void list(Graphics g, int x, int y, String[] items, int selected) {
        for (int i = 0; i < items.length; i++) {
            boolean on = i == selected;
            int rowY = y + i * 22;
            g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B);
            drawText(g, x + 36, rowY, (on ? "> " : "  ") + items[i],
                    on ? Font.STYLE_BOLD : Font.STYLE_PLAIN, Font.SIZE_SMALL);
            if (on) {
                // Same frame box the upgrades and armory lists use: two rows above the text,
                // 16 tall, so the frame hugs the row instead of floating away from it.
                g.drawRect(x + 8, rowY - 2, 288, 16);
            }
        }
    }

    /** Sets the shared body-ink colour. Callers must use this before informational text, because
     *  drawText does not set a colour of its own and would otherwise inherit whatever the last
     *  drawing call left behind. */
    private void ink(Graphics g) { g.setColor(MENU_INK_R, MENU_INK_G, MENU_INK_B); }

    /** Secondary labels: the recessive label grey, one clear step below the body ink, so a label
     *  column reads as a label and its value as the value. */
    private void inkDim(Graphics g) { g.setColor(MENU_DIM_R, MENU_DIM_G, MENU_DIM_B); }

    private void drawText(Graphics g, int x, int y, String text, int style, int size) {
        g.setFont(Font.getFont(Font.FACE_SYSTEM, style, size));
        g.drawString(text, x, y, Graphics.TOP | Graphics.LEFT);
    }

    /**
     * Draws a string horizontally centred on the 320px canvas.
     *
     * <p>The width comes from the font that is actually about to be used rather than from a
     * hand-measured constant, so a line stays centred at any font size or style and cannot drift
     * the way a fixed x offset does. Nothing in the panels behind these pages is a fixed-width
     * column, so centring on the canvas is the same as centring on the visible content.
     */
    private void drawCentered(Graphics g, int y, String text, int style, int size) {
        Font f = Font.getFont(Font.FACE_SYSTEM, style, size);
        g.setFont(f);
        g.drawString(text, (Viewport.MIN_W - f.stringWidth(text)) / 2, y,
                Graphics.TOP | Graphics.LEFT);
    }

    /**
     * Shortens a row so it ends in an ellipsis instead of running past the column edge. The
     * status column is anchored under the title and cannot grow without breaking that
     * alignment, so the few long values ("Special: Spirit of the Oni") are trimmed here rather
     * than being cut off mid-word by the clip.
     */
    private static String ellipsize(Font f, String text, int maxWidth) {
        if (f.stringWidth(text) <= maxWidth) return text;
        int end = text.length();
        while (end > 0 && f.stringWidth(text.substring(0, end) + "...") > maxWidth) end--;
        return text.substring(0, end) + "...";
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
