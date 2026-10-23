package me.holojava.fullgame;

import java.util.Random;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;

/** Deterministic bounded simulation for one run. No allocation occurs in entity loops. */
final class RunWorld {
    private int worldWidth = 320;
    private int worldHeight = 240;
    // Size of the visible playfield box, taken from Viewport.worldW/worldH. This is the
    // screen-space area the HUD and overlays anchor to, so it is deliberately not worldWidth:
    // a world may later be wider than the view, and a HUD element anchored to the far edge of
    // the world would then be drawn off screen. Both start equal to the world while every stage
    // still leaves its world size unspecified, which is why this changes no drawing today.
    private int viewWidth = Viewport.MIN_W;
    private int viewHeight = Viewport.MIN_H;
    private boolean viewportInitialized;
    static final int STATE_RUNNING = 0;
    static final int STATE_PAUSED = 1;
    static final int STATE_LEVEL_UP = 2;
    static final int STATE_GAME_OVER = 3;
    static final int STATE_VICTORY = 4;

    private static final int OFFER_WEAPON = 1;
    private static final int OFFER_ITEM = 2;
    private static final int OFFER_STAT = 3;
    private static final int OFFER_FOOD = 4;
    private static final int OFFER_COINS = 5;
    private static final int OFFER_REROLL = 6;
    private static final int OFFER_HOLD = 7;

    /** Shot.target spellings. Any value >= 0 is an enemy pool index; the boss needs its own. */
    static final int TARGET_NONE = -1;
    static final int TARGET_BOSS = -2;

    /**
     * Magnet aura radius in px before the pickup bonus. It used to be 22 px *and* double as the
     * collection radius, so a pickup vanished at the aura edge and the pull never showed: the
     * player is 24 px and a pickup is 12 px, so 22 px between centers is already contact. Raised
     * so the pull is visible, with collection moved to its own touch radius below.
     */
    static final int MAGNET_BASE_RADIUS = 56;
    /** A pickup within this many px of the player center is collected. */
    static final int PICKUP_TOUCH_RADIUS = 16;
    /** Pull per tick: normal pickups, and the fast pull of the EXP Magnet and its vacuum. */
    static final int MAGNET_PULL_SPEED = 3;
    static final int MAGNET_PULL_FAST = 8;
    /** How long a collected EXP Magnet vacuums the field (documented: a large-area vacuum). */
    static final int VACUUM_DURATION_MS = 3000;

    private static final int ENEMY_POOL = 48;
    private static final int SHOT_POOL = 128;
    private static final int MELEE_POOL = 24;
    private static final int PICKUP_POOL = 96;

    // HUD bar geometry, in logical world pixels.
    private static final int HP_BAR_X = 4;
    private static final int HP_BAR_Y = 18;
    // Short bar: 66px is 20.6% of the 320px design width. It also defines the slot-row width,
    // so the weapon row stays exactly as long as the HP bar.
    private static final int HP_BAR_W = 66;
    private static final int HP_BAR_H = 10;
    private static final int HP_BAR_INSET = 2;
    private static final int HP_BAR_LOW_PERCENT = 25;
    // Special bar: parked directly under the HP bar, now as wide as the EXP bar (both 76 px;
    // EXP_BAR_W is declared below, so the width is mirrored by hand). Gold #FFCE4A is the fill
    // the EXP bar used to wear, moved here as requested: it still never collides with the HP
    // pair (green/red) or the new light-blue EXP fill, on any stage backdrop.
    private static final int SPECIAL_BAR_X = HP_BAR_X;
    private static final int SPECIAL_BAR_Y = HP_BAR_Y + HP_BAR_H + 2; // 30
    private static final int SPECIAL_BAR_W = HP_BAR_W; // match HP bar width (66)
    private static final int SPECIAL_BAR_H = 6;
    private static final int SPECIAL_BAR_INSET = 1;
    private static final int SPECIAL_READY_R = 255, SPECIAL_READY_G = 206, SPECIAL_READY_B = 74;  // #FFCE4A
    private static final int SPECIAL_TRACK_R = 58, SPECIAL_TRACK_G = 44, SPECIAL_TRACK_B = 92;   // #3A2C5C
    private static final int BOSS_BAR_W = 260;
    private static final int BOSS_BAR_H = 20;
    private static final int BOSS_BAR_INSET = 2;
    private static final int BOSS_PORTRAIT_SIZE = 24;
    private static final int BOSS_PHASE_MAX = 4;

    // Multi-boss support
    private static final int MAX_ACTIVE_BOSSES = 3;
    private Boss[] activeBosses = new Boss[MAX_ACTIVE_BOSSES];
    private int activeBossCount = 0;

    // Compact slot rows: six equal cells spanning HP_BAR_W. The weapon row sits a clear gap
    // under the special bar (so the boxes never touch it); the item row mirrors it one row
    // below and holds the passive skills (items) collected during the run, so the two trays
    // read as one storage block.
    private static final int SLOT_COLS = 6;
    private static final int SLOT_W = HP_BAR_W / SLOT_COLS; // 66 / 6 = 11
    private static final int SLOT_H = 11;
    private static final int SLOT_INSET = 1;
    private static final int WEAPON_SLOT_Y = SPECIAL_BAR_Y + SPECIAL_BAR_H + 7; // 43
    private static final int ITEM_SLOT_Y = WEAPON_SLOT_Y + SLOT_H + 2;          // 56
    private static final int SLOT_FILL = 30;
    private static final int SLOT_FILL_EMPTY = 18;
    private static final int SLOT_BORDER = 78;
    // Neutral ownership marker. Weapon slots carry no weapon-specific art: no HoloJava
    // sprite except bullet (7x7) fits the 9px interior, and no HoloCure icon is below 12x17,
    // so nothing could be shown without scaling, which MIDP cannot do.
    private static final int SLOT_MARK = 175;
    private static final int SLOT_MARK_G = 180;
    private static final int SLOT_MARK_B = 190;

    // Status run block, top-right, right-aligned to the world edge. Order follows the run card:
    // Lv with its EXP bar first, then the Status run label, Coins/Kill, and the wave counter.
    private static final int STATUS_MARGIN = 4;
    private static final int STATUS_LV_Y = 4;
    // EXP bar: light blue, sampled from the original game's own hud_expbar_anim art (#5EDDEE)
    // so the fill reads exactly like HoloCure's EXP bar. Its track is the matching deep blue
    // from hud_expbar_bg (#183684); the black border keeps both legible on light and dark skies.
    // Moved down to 24 (was HP_BAR_Y=18) so it no longer overlaps the Level text drawn at y=4
    // with MEDIUM font (~12px tall).
    private static final int EXP_BAR_W = 76;
    private static final int EXP_BAR_H = 8;
    private static final int EXP_FILL_R = 94, EXP_FILL_G = 221, EXP_FILL_B = 238; // #5EDDEE
    private static final int STATUS_EXP_Y = 24;
    private static final int STATUS_LABEL_Y = STATUS_EXP_Y + EXP_BAR_H + 3; // 35
    private static final int STATUS_ROW_H = 13;
    private static final int STATUS_COINS_Y = STATUS_LABEL_Y + STATUS_ROW_H;  // 38
    private static final int STATUS_WAVE_Y = STATUS_COINS_Y + STATUS_ROW_H;   // 51
    private static final int STATUS_FANS_Y = STATUS_WAVE_Y + STATUS_ROW_H;    // 64

    private final AssetLibrary assets;
    private final ContentCatalog.CharacterDef character;
    private final ContentCatalog.ModeDef mode;
    private final ContentCatalog.StageDef stage;
    private final int characterIndex;
    private final int modeIndex;
    private final int stageIndex;
    private Random random;

    private final Enemy[] enemies = new Enemy[ENEMY_POOL];
    private final Shot[] shots = new Shot[SHOT_POOL];
    private final Melee[] melee = new Melee[MELEE_POOL];
    private final Pickup[] pickups = new Pickup[PICKUP_POOL];
    private final int[] weaponSlots = new int[6];
    private final int[] weaponLevels = new int[6];
    private final long[] weaponReadyAt = new long[6];
    private final int[] itemSlots = new int[6];
    private final int[] itemLevels = new int[6];
    private final boolean[] encounterStarted = new boolean[4];
    private final boolean[] encounterDefeated = new boolean[4];
    private final int[] statOfferCounts = new int[6];
    private final Offer[] offers = new Offer[6];
    private final boolean[] offerUsed = new boolean[64];

    private int state = STATE_RUNNING;
    // Wall-clock ms when the run clock was last suspended (0 while running).
    private long suspendedAt;
    // Total wall-clock ms accumulated in suspensions (popups/pause) to subtract from elapsed.
    private long suspendedTotalMs;
    private long startTime;
    private long lastMinuteScaled = -1L;
    private double scaleA;
    private double scaleB;

    private int px = 130;
    private int py = 95;
    /**
     * Player hitbox used by every contact check. One box for all five characters on purpose: the
     * sprites differ in size, but what decides a hit is the body the player is steering, and a box
     * that changed size per character would make the same approach land differently for each of
     * them. Named here because the contact path used to carry a bare 24.
     */
    private static final int PLAYER_HIT_W = 24;
    private static final int PLAYER_HIT_H = 24;
    private int facingX = 1;
    private int facingY = 0;
    private boolean moving;
    private int playerHp;
    private int playerMaxHp;
    private int playerLevel = 1;
    private int experience;
    private int expNeed;
    private int coins;
    /**
     * Permanent progress store for this run. Retained rather than passed through so a collected
     * coin can be committed at the moment it is picked up. Null is tolerated and simply means the
     * run is not backed by persistence, matching the class rule that gameplay never depends on it.
     */
    private final SaveStore save;
    private int kills;
    private int defeatCount;
    private long invulUntil;
    private long slowEnemiesUntil;
    private long lifestealUntil;
    private long regenTickAt;
    /** HP the Regeneration shop row restores per tick, 1 per level. Set by recomputeStats. */
    private int regenHealPerTick;
    private long shieldReadyAt;
    private int shield;
    // --- Special skill visual effects -----------------------------------
    private static final int MAX_EFFECTS = 8;
    private static final int EFFECT_NONE = 0;
    private static final int EFFECT_SHARK_RING = 1;    // Amelia: expanding ring
    private static final int EFFECT_SLOW_AURA = 2;     // Gura: pulsing aura around player
    private static final int EFFECT_REAPER_HEAL = 3;   // Calliope: green particles upward
    private static final int EFFECT_PHOENIX_RING = 4;  // Kiara: expanding fire rings
    private static final int EFFECT_ONI_SHIELD = 5;    // Ina: shield bubble + shot clear flash
    private int effectType[] = new int[MAX_EFFECTS];
    private int effectX[] = new int[MAX_EFFECTS];
    private int effectY[] = new int[MAX_EFFECTS];
    private int effectRadius[] = new int[MAX_EFFECTS];
    private int effectMaxRadius[] = new int[MAX_EFFECTS];
    private int effectTimer[] = new int[MAX_EFFECTS];
    private int effectMaxTimer[] = new int[MAX_EFFECTS];
    private int effectColorR[] = new int[MAX_EFFECTS];
    private int effectColorG[] = new int[MAX_EFFECTS];
    private int effectColorB[] = new int[MAX_EFFECTS];
    private int weaponCount;
    private int itemCount;

    // Derived stats.
    private int atkPercentBonus;
    private int growthLevel;
    private int skillUpLevel;
    private int hastePercent;
    private int speedPercentBonus;
    private int pickupPercentBonus;
    /** While {@code now < vacuumUntil}, an EXP Magnet hauls every pickup in from anywhere. */
    private long vacuumUntil;
    private int critPercentBonus;
    private int expPercentBonus;
    private int defensePercent;
    /**
     * Ceiling on the shop's own Defense Up contribution, matching the "-3% per level (maks 15%)"
     * the row advertises. The character's built-in {@code defensePercent} is a separate stat and
     * is not counted against this.
     */
    private static final int DEFENSE_UP_MAX_PCT = 15;
    /**
     * Percent added to the coin VALUE of an enemy drop, from the shop's Money Gain Up row
     * (+20% per level, wiki cap +200%). The drop roll itself — {@code rand(def.coinChance) == 0} —
     * is never touched by the shop, so the bonus scales what a dropped coin is worth instead of
     * minting extra rolls. A 10-coin drop becomes 12 at one level and 30 at the cap.
     */
    private int coinDropBonus;
    /**
     * Percent added to the food drop RATE by the shop's Food Drops Up row (+4% per level,
     * 5 levels = +20%). Unlike Money Gain Up this one rolls: the base is 1-in-200 per kill and
     * the bonus shrinks the divisor — 200 at zero bonus, 166 at the cap — so more levels mean
     * more heal pickups on the field, not bigger heals.
     */
    private int foodDropBonus;
    /**
     * Purchased shop upgrade levels for this run, indexed like ContentCatalog.UPGRADES.
     *
     * <p>These hold the whole 34-row vector; a run only consults the rows it can act on (stats,
     * special/reroll/hold gates, limits, marketing, collab ban) and reads the rest as zero.
     * <p>Not final and not initialised at the declaration: most test suites build a RunWorld through
     * {@code Unsafe.allocateInstance}, which skips field initialisers entirely, so a field
     * initialiser would leave this null in exactly the suites that drive {@code recomputeStats}.
     * {@link #upgrades()} allocates on demand instead.
     */
    private int[] upgradeLevels;
    private int baseMaxHp;
    /** baseMaxHp before shop upgrades, so a re-run of recomputeStats cannot compound them. */
    private int characterBaseMaxHp;

    /** Upgrade levels for this run, allocated on first use. See the field's comment. */
    private int[] upgrades() {
        if (upgradeLevels == null) upgradeLevels = new int[ContentCatalog.UPGRADES.length];
        return upgradeLevels;
    }

    // ---- meta-upgrade gates, read from the save at reset ------------------------------------
    // Each default is the value an Unsafe-allocated world (never reset) starts with: the gate
    // open, no bonus levels. A fresh real save reads the same defaults from its own zeros, except
    // for the special, which starts LOCKED until the Special Attack row is bought.
    /** When true the special key does nothing: the Special Attack upgrade has not been bought. */
    private boolean specialLocked;
    /** Collab Ban purchased: collab weapons can no longer be offered, on any mode. */
    private boolean collabBan;
    /** Marketing levels: one extra fan spawned per interval per level. */
    private int marketingLevel;
    /** Weapon Limit challenge levels: each one lowers the weapon cap by one (6 down to 1). */
    private int weaponLimitLevel;
    /** Item Limit challenge levels: each one lowers the item cap by one (6 down to 0). */
    private int itemLimitLevel;

    /** Weapon slots for this run: 6 minus the Weapon Limit challenge levels, floored at 0. */
    int weaponCap() {
        int cap = 6 - weaponLimitLevel;
        return cap < 0 ? 0 : cap;
    }

    /** Item slots for this run: 6 minus the Item Limit challenge levels, floored at 0. */
    int itemCap() {
        int cap = 6 - itemLimitLevel;
        return cap < 0 ? 0 : cap;
    }

    private long nextSpawnAt;
    private long nextWaveAt;
    /** Number of timed wave groups spawned so far; the HUD's "W n" reads it via waveNumber(). */
    private int waveCount;
    private boolean ringEventDone;
    private boolean hordeEventDone;
    private int nextEndlessEncounter;

    private Boss boss = new Boss();
    private int bossEncounterIndex = -1;

    /** Cadence of the passive skill, in milliseconds: it fires by itself this often, no key. */
    private static final long SKILL_COOLDOWN_MS = 15000L;

    /** Cadence of all regeneration, in milliseconds: one tick heals the Regeneration levels. */
    private static final long REGEN_TICK_MS = 5000L;

    /**
     * Whether this run has the character's skill. The skill is always active: Skill Pasif left
     * the shop, so every character gets their skill from the first tick with no purchase. When
     * true it fires on its own every {@link #SKILL_COOLDOWN_MS}; no key is involved and no stacks
     * exist. The field remains the firing gate so a test can still pin the cadence in isolation.
     */
    private boolean skillAcquired;
    private long skillReadyAt;

    /**
     * Charge of the special meter, 0..{@link #SPECIAL_CHARGE_MAX}. SPECIAL orb drops (pickup
     * type 5) bank {@link #SPECIAL_ORB_VALUE} points each; a full meter lets the special key
     * fire, which starts the special active state. During the active state the meter drains
     * slowly over the skill duration. After draining, a cooldown period begins before orbs
     * can refill the meter again. This replaces the old instant-empty behavior.
     */
    private int specialCharge;

    /** Timer for the special-release flash (ms). When >0, the special bar pulses bright. */
    private long specialFlashUntil;

    // Special skill active state (replaces instant empty)
    static final int SPECIAL_STATE_IDLE = 0;
    static final int SPECIAL_STATE_ACTIVE = 1;      // skill running, meter draining
    static final int SPECIAL_STATE_COOLDOWN = 2;    // skill ended, waiting before refill
    private int specialState = SPECIAL_STATE_IDLE;
    private long specialActiveUntil;       // when active state ends (drain complete)
    private long specialCooldownUntil;     // when cooldown ends (refill allowed)
    static final long SPECIAL_ACTIVE_DURATION_MS = 3000L;   // 3s skill duration
    static final long SPECIAL_COOLDOWN_MS = 5000L;          // 5s cooldown before refill
    private int specialDrainStartCharge;   // charge at activation, for smooth drain

    /** Full special meter, in charge points. */
    static final int SPECIAL_CHARGE_MAX = 100;
    /** Charge one SPECIAL orb pickup banks. Four orbs fill a meter. */
    static final int SPECIAL_ORB_VALUE = 25;

    private int rerolls;
    private int holds;
    private int offerCount;
    private int selectedOffer;
    /**
     * The last of the four upgrade slots the cursor rested on, 0..3. Tahan (Hold) stashes this
     * one for the next level-up, so it has to remember which offer the player was looking at
     * before they moved down onto the Hold row itself. It is not the Hold row's own index.
     */
    private int lastUpgradeSlot;
    /**
     * The stashed offer, waiting to reappear at the next level-up. This is the offer itself, not
     * an index: the four slots are re-rolled from scratch each time, so an index would point at
     * whatever fresh offer happened to land there. Null when nothing is stashed.
     */
    private Offer heldOffer;
    private boolean won;

    // Dialogue system
    private static final int MAX_DIALOGUE_LINES = 3;
    private String[] dialogueLines = new String[MAX_DIALOGUE_LINES];
    private int dialogueLineCount = 0;
    private long dialogueShowUntil = 0;
    private static final long DIALOGUE_DURATION_MS = 3000;

    // HUD string cache fields for allocation optimization
    private String cachedHpString;
    private String cachedLvString;
    private String cachedCoinsKillString;
    private String cachedWaveString;
    private String cachedFansString;
    private String cachedBossName;

    private int lastPlayerHp = -1;
    private int lastPlayerMaxHp = -1;
    private int lastPlayerLevel = -1;
    private long lastTimeStringUpdate = 0;
    private String cachedTimeString;
    private int lastCoins = -1;
    private int lastKills = -1;
    private int lastWaveNumber = -1;
    private int lastFansKills = -1;
    private String lastBossName = null;

    RunWorld(AssetLibrary assets, SaveStore save, int characterIndex, int modeIndex, int stageIndex) {
        this.assets = assets;
        this.save = save;
        this.characterIndex = characterIndex;
        this.modeIndex = modeIndex;
        this.stageIndex = stageIndex;
        this.character = ContentCatalog.CHARACTERS[characterIndex];
        this.mode = ContentCatalog.MODES[modeIndex];
        this.random = new Random(0x4A4F4C00L + characterIndex * 977L + stageIndex * 131L);
        this.stage = ContentCatalog.STAGES[stageIndex];
        assets.prepareStage(stageIndex);
        for (int i = 0; i < ENEMY_POOL; i++) enemies[i] = new Enemy();
        for (int i = 0; i < SHOT_POOL; i++) shots[i] = new Shot();
        for (int i = 0; i < MELEE_POOL; i++) melee[i] = new Melee();
        for (int i = 0; i < PICKUP_POOL; i++) pickups[i] = new Pickup();
        for (int i = 0; i < 6; i++) offers[i] = new Offer();
        for (int i = 0; i < 6; i++) { weaponSlots[i] = -1; itemSlots[i] = -1; }
        for (int i = 0; i < 64; i++) offerUsed[i] = false;
        reset(save);
    }

    private void reset(SaveStore save) {
        startTime = System.currentTimeMillis();
        suspendedAt = 0L;
        suspendedTotalMs = 0L;
        state = STATE_RUNNING;
        won = false;
        moving = false;
        px = 130; py = 95; facingX = 1; facingY = 0;
        playerLevel = 1; experience = 0; expNeed = requiredForLevel(1);
        coins = 0; kills = 0; defeatCount = 0;
        invulUntil = 0; slowEnemiesUntil = 0; lifestealUntil = 0;
        growthLevel = 0;
        skillUpLevel = 0;
        regenTickAt = startTime + REGEN_TICK_MS; shieldReadyAt = 0; shield = 0;
        weaponCount = 0; itemCount = 0;
        for (int i = 0; i < 6; i++) {
            weaponSlots[i] = -1; itemSlots[i] = -1; weaponLevels[i] = 0; weaponReadyAt[i] = 0;
        }
        for (int i = 0; i < statOfferCounts.length; i++) statOfferCounts[i] = 0;
        for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;
        for (int i = 0; i < enemies.length; i++) enemies[i].active = false;
        for (int i = 0; i < shots.length; i++) shots[i].active = false;
        for (int i = 0; i < melee.length; i++) melee[i].active = false;
        for (int i = 0; i < pickups.length; i++) pickups[i].active = false;
        for (int i = 0; i < MAX_EFFECTS; i++) effectType[i] = EFFECT_NONE;
        vacuumUntil = 0L;
        for (int i = 0; i < encounterStarted.length; i++) { encounterStarted[i] = false; encounterDefeated[i] = false; }
        boss.active = false; bossEncounterIndex = -1; nextEndlessEncounter = 0;
        activeBossCount = 0;
        for (int i = 0; i < MAX_ACTIVE_BOSSES; i++) {
            if (activeBosses[i] != null) {
                activeBosses[i].active = false;
                activeBosses[i].phase = 1;
                activeBosses[i].maxPhase = 1;
                activeBosses[i].phaseTransitionUntil = 0;
                activeBosses[i].isPhaseTransition = false;
            }
        }
        ringEventDone = false; hordeEventDone = false;
        nextSpawnAt = startTime + 1000;
        nextWaveAt = startTime + 180000L;
        // The skill is always active — Skill Pasif left the shop — so it is granted from the
        // first tick with no purchase behind it. Reroll and Tahan (Hold) are the mirror image:
        // they used to come free (10 and 5 charges) and are now bought one charge at a time,
        // so a fresh run starts with none until the shop rows are levelled.
        skillAcquired = true; skillReadyAt = 0; specialCharge = 0;
        specialState = SPECIAL_STATE_IDLE;
        specialActiveUntil = 0L;
        specialCooldownUntil = 0L;
        specialDrainStartCharge = 0;
        rerolls = 0;
        holds = 0;
        offerCount = 0; selectedOffer = 0; heldOffer = null; lastUpgradeSlot = 0;

        // Reset HUD string cache
        cachedHpString = null;
        cachedLvString = null;
        cachedCoinsKillString = null;
        cachedWaveString = null;
        cachedFansString = null;
        cachedBossName = null;
        lastPlayerHp = -1; lastPlayerMaxHp = -1;
        lastPlayerLevel = -1;
        lastTimeStringUpdate = 0; cachedTimeString = null;
        lastCoins = -1; lastKills = -1; lastWaveNumber = -1;
        lastFansKills = -1;
        lastBossName = null;
        waveCount = 0;

        addWeapon(ContentCatalog.weaponIndex(character.mainWeapon), 1);
        if (mode.standardLoadout) weaponLevels[0] = ContentCatalog.weapon(0).maxLevel;
        weaponReadyAt[0] = startTime + 500;
        baseMaxHp = character.hp;
        // Character base HP is kept separately so recomputeStats() can rebuild max HP from scratch
        // each time instead of adding shop passives on top of an already-modified value.
        characterBaseMaxHp = baseMaxHp;
        if (mode.id.equals("endless") && save != null && save.hardcore()) {
            baseMaxHp = 1; characterBaseMaxHp = 1;
        }
        // Shop upgrades are permanent and global: one 34-row vector serves every character, so it
        // is read once here and treated as part of the run. The row gates follow from it: the
        // special starts LOCKED unless Special Attack was bought, while Reroll, Tahan, Marketing,
        // the loadout limits and Collab Ban all read their own purchased levels. The whole block
        // is guarded because most suites use a null save, whose defaults are the unarmed ones
        // above (gate open only where the field's default already says so, no bonus levels).
        if (save != null) {
            int[] levels = upgrades();
            for (int i = 0; i < levels.length; i++) {
                levels[i] = save.upgradeLevel(i);
            }
            rerolls = levels[ContentCatalog.upgradeIndex("reroll")];
            holds = levels[ContentCatalog.upgradeIndex("hold")];
            marketingLevel = levels[ContentCatalog.upgradeIndex("marketing")];
            weaponLimitLevel = levels[ContentCatalog.upgradeIndex("weapon_limit")];
            itemLimitLevel = levels[ContentCatalog.upgradeIndex("item_limit")];
            collabBan = levels[ContentCatalog.upgradeIndex("collab_ban")] > 0;
            specialLocked = levels[ContentCatalog.upgradeIndex("special_attack")] == 0;
        }
        recomputeStats();
        playerHp = playerMaxHp;
        generateOffers();
    }

    /**
     * Resolves the playfield size against the current viewport.
     *
     * The stage owns the world size; the viewport only supplies it for axes the stage leaves
     * unspecified (worldW/worldH <= 0). Every stage is still unspecified today, so each axis
     * resolves exactly as before and this method stays behaviour-neutral. Owning the size on
     * StageDef is what later lets a stage be larger than the screen and lets a camera scroll it.
     */
    void setViewport(Viewport viewport) {
        if (viewport == null) return;
        viewWidth = viewport.worldW;
        viewHeight = viewport.worldH;
        if (stage.scrollW > 0) worldWidth = viewWidth * stage.scrollW;
        else if (stage.worldW > 0) worldWidth = stage.worldW;
        if (stage.worldH <= 0) worldHeight = viewport.worldH;
        if (!viewportInitialized) {
            px = (worldWidth - 24) / 2;
            py = (worldHeight - 24) / 2;
            viewportInitialized = true;
        }
    }

    /**
     * The single horizontal camera value: the world x coordinate shown at the left edge of the
     * visible playfield. It is derived only from the player position and clamped to the world, so
     * the view can never show anything outside the playfield, and it stays 0 whenever the world is
     * not wider than the view. Every stage except Holo Office has an equal world and view today,
     * so those keep an exactly zero offset and render exactly as before.
     *
     * <p>Deliberately not a field: reading it at draw time keeps it consistent with the current
     * px even on the first frame and while the simulation is frozen.
     */
    private int cameraX() {
        int travel = worldWidth - viewWidth;
        if (travel <= 0) return 0;
        int target = playerCenterX() - viewWidth / 2;
        if (target < 0) return 0;
        if (target > travel) return travel;
        return target;
    }

    int worldWidth() { return worldWidth; }
    int worldHeight() { return worldHeight; }

    int state() { return state; }
    int playerHp() { return playerHp; }
    int playerMaxHp() { return playerMaxHp; }
    int playerLevel() { return playerLevel; }
    int experience() { return experience; }
    int expNeed() { return expNeed; }
    int kills() { return kills; }
    int coins() { return coins; }
    int weaponCount() { return weaponCount; }
    int itemCount() { return itemCount; }
    boolean skillAcquired() { return skillAcquired; }
    int specialCharge() { return specialCharge; }
    int bossIndex() { return boss.defIndex; }
    int bossHp() { return boss.hp; }
    int bossMaxHp() { return boss.maxHp; }
    String bossName() { return boss.active ? ContentCatalog.BOSSES[boss.defIndex].name : ""; }
    boolean won() { return won; }
    /** Run elapsed milliseconds at the given wall-clock time, excluding time spent in popups/pause. */
private long elapsedMsAt(long now) {
    long ms = now - startTime - suspendedTotalMs;
    if (suspendedAt != 0L) ms -= now - suspendedAt;
    return ms < 0L ? 0L : ms;
}

long elapsedSeconds() { return elapsedMsAt(System.currentTimeMillis()) / 1000L; }

    /** Waves fired this run, floored at 1: the opening field always counts as wave one. */
    int waveNumber() { return waveCount < 1 ? 1 : waveCount; }

    void pause() {
        if (state == STATE_RUNNING) {
            state = STATE_PAUSED;
            suspendedAt = System.currentTimeMillis();
        }
    }
    void resume() {
        if (state == STATE_PAUSED) {
            long now = System.currentTimeMillis();
            suspendedTotalMs += now - suspendedAt;
            suspendedAt = 0L;
            state = STATE_RUNNING;
        }
    }

    int offerCount() { return offerCount; }
    int selectedOffer() { return selectedOffer; }
    void moveOffer(int delta) {
        if (offerCount == 0) return;
        selectedOffer = (selectedOffer + delta + offerCount) % offerCount;
        // Remember the last upgrade slot the cursor was on. Tahan stashes that one, and the
        // cursor has to be able to sit on the Hold row itself without losing the pick.
        if (selectedOffer < 4) lastUpgradeSlot = selectedOffer;
    }
    String offerLabel(int index) {
        if (index < 0 || index >= offerCount) return "";
        Offer o = offers[index];
        if (o.type == OFFER_WEAPON) {
            ContentCatalog.WeaponDef w = ContentCatalog.weapon(o.value);
            int level = o.existing >= 0 ? weaponLevels[o.existing] : 0;
            return w.name + (o.newItem ? " NEW" : " Lv" + Math.min(w.maxLevel, level + 1));
        }
        if (o.type == OFFER_ITEM) {
            ContentCatalog.ItemDef d = ContentCatalog.item(o.value);
            int level = o.existing >= 0 ? itemLevels[o.existing] : 0;
            return d.name + (o.newItem ? " NEW" : " Lv" + Math.min(d.maxLevel, level + 1));
        }
        if (o.type == OFFER_STAT) return statName(o.value);
        if (o.type == OFFER_FOOD) return "Food +20% HP";
        if (o.type == OFFER_COINS) return "100 HoloCoins";
        if (o.type == OFFER_REROLL) return "Reroll (" + rerolls + ")";
        return "Hold";
    }
    String offerType(int index) {
        if (index < 0 || index >= offerCount) return "";
        int t = offers[index].type;
        if (t == OFFER_WEAPON) return "WEAPON";
        if (t == OFFER_ITEM) return "ITEM";
        if (t == OFFER_STAT) return "STAT";
        if (t == OFFER_FOOD) return "FOOD";
        if (t == OFFER_COINS) return "COIN";
        if (t == OFFER_REROLL) return "META";
        return "META";
    }

    /** True when the level-up option at this index is a collab weapon. Read-only, for UI accents. */
    boolean offerIsCollab(int index) {
        if (index < 0 || index >= offerCount) return false;
        return offers[index].collab;
    }

    void selectOffer() {
        if (state != STATE_LEVEL_UP || offerCount == 0) return;
        int index = selectedOffer;
        if (index >= 4) {
            if (offers[index].type == OFFER_REROLL && rerolls > 0) {
                rerolls--;
                generateOffers();
                selectedOffer = 0;
                return;
            }
            if (offers[index].type == OFFER_HOLD && holds > 0) {
                holds--;
                // Stash a copy of the last upgrade the cursor rested on. The old line read
                // `heldOffer = selectedOffer < 4 ? selectedOffer : heldOffer`, which always
                // took the else branch: selectedOffer IS the Hold row, so it is never below 4,
                // and the charge was spent on nothing. It also kept an index into a list that
                // was about to be re-rolled. Store the offer itself instead.
                if (lastUpgradeSlot >= 0 && lastUpgradeSlot < 4
                        && offers[lastUpgradeSlot].type >= 0) {
                    heldOffer = new Offer();
                    copyOffer(heldOffer, offers[lastUpgradeSlot]);
                }
                // Rebuild only the Reroll/Hold rows. The four upgrades stay put so this
                // level-up still has something to choose from, as it did before.
                rebuildMetaRows();
                return;
            }
            return;
        }
        applyOffer(index);
        if (suspendedAt != 0L) {
            long now = System.currentTimeMillis();
            suspendedTotalMs += now - suspendedAt;
            suspendedAt = 0L;
        }
        state = STATE_RUNNING;
        invulUntil = System.currentTimeMillis() + 500;
        if (experience >= expNeed && state == STATE_RUNNING) {
            experience -= expNeed;
            playerLevel++;
            expNeed = requiredForLevel(playerLevel);
            suspendedAt = System.currentTimeMillis();
            state = STATE_LEVEL_UP;
            generateOffers();
        }
    }

    void update(int keys, boolean attackPressed, boolean specialPressed, long now) {
        if (state != STATE_RUNNING) return;
        updateEffects(now);
        if (elapsedMsAt(now) > 120000L && lastMinuteScaled != now / 60000L) {
            lastMinuteScaled = now / 60000L;
            updateScaling();
        }
        updatePlayer(keys, specialPressed, now);
        updateTimedEvents(now);
        updateSpawns(now);
        updateBossDirector(now);
        updateEnemies(now);
        updateBoss(now);
        updateWeapons(now, attackPressed);
        updateShots(now);
        updateMelee(now);
        updatePickups(now);
        updateRegen(now);
        checkDialogueTriggers(now);
        if (playerHp <= 0) {
            playerHp = 0;
            state = STATE_GAME_OVER;
            return;
        }
        if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET && kills >= mode.targetKills) {
            won = true;
            state = STATE_VICTORY;
        }
    }

    private void updateScaling() {
        long elapsed = System.currentTimeMillis() - startTime;
        long minuteTimer = elapsed / 60000L;
        long hourTimer = elapsed / 3600000L;
        scaleA = Math.max(0.0, (double) (minuteTimer - 23L)) + 37.0 * (double) hourTimer;
        double minutePast30 = elapsed >= 1800000L ? (elapsed - 1800000L) / 60000.0 : 0.0;
        scaleB = minutePast30 + 60.0 * (double) hourTimer;
    }

    private void updatePlayer(int keys, boolean specialPressed, long now) {
        double speed = 120.0 * (double) character.spdPercent / 100.0;
        speed *= 1.0 + (double) speedPercentBonus / 100.0;
        if (now < slowEnemiesUntil) { /* player remains unaffected by enemy slow */ }
        int dx = 0;
        int dy = 0;
        if ((keys & GameCanvas.LEFT_PRESSED) != 0) dx -= 1;
        if ((keys & GameCanvas.RIGHT_PRESSED) != 0) dx += 1;
        if ((keys & GameCanvas.UP_PRESSED) != 0) dy -= 1;
        if ((keys & GameCanvas.DOWN_PRESSED) != 0) dy += 1;
        moving = dx != 0 || dy != 0;
        if (dx < 0) facingX = -1;
        if (dx > 0) facingX = 1;
        if (moving) {
            double len = Math.sqrt((double) (dx * dx + dy * dy));
            px += (int) ((dx / len) * speed * 0.04);
            py += (int) ((dy / len) * speed * 0.04);
            if (dx == 0) facingX = 0;
            if (dy < 0) facingY = -1;
            if (dy > 0) facingY = 1;
        }
        if (px < 0) px = 0;
        if (py < 0) py = 0;
        if (px > worldWidth - 24) px = worldWidth - 24;
        if (py > worldHeight - 24) py = worldHeight - 24;
        // The skill is a permanent ability now: it fires on its own whenever the cooldown is clear.
        if (skillAcquired && now >= skillReadyAt) activateSkill(now);
        // The special is gated on the Special Attack upgrade AND a full meter AND being in IDLE state:
        // during ACTIVE (drain) and COOLDOWN the key is swallowed.
        if (specialPressed && !specialLocked && specialState == SPECIAL_STATE_IDLE
                && specialCharge >= SPECIAL_CHARGE_MAX) activateSpecial(now);
        updateSpecialState(now);
    }

    /** Updates the special meter state machine: ACTIVE (drain) -> COOLDOWN -> IDLE (refill allowed).
     * Handles multiple transitions in one tick if a large time jump occurs. */
    private void updateSpecialState(long now) {
        while (true) {
            switch (specialState) {
                case SPECIAL_STATE_ACTIVE:
                    if (now >= specialActiveUntil) {
                        // Active duration ended: meter empty, start cooldown from the ACTUAL end time
                        // (not from 'now'), so large time jumps don't extend the cooldown.
                        specialCharge = 0;
                        specialState = SPECIAL_STATE_COOLDOWN;
                        specialCooldownUntil = specialActiveUntil + SPECIAL_COOLDOWN_MS;
                        continue; // re-evaluate new state immediately
                    } else {
                        // Drain smoothly from startCharge to 0 over the active duration
                        long elapsed = now - (specialActiveUntil - SPECIAL_ACTIVE_DURATION_MS);
                        float progress = (float) elapsed / (float) SPECIAL_ACTIVE_DURATION_MS;
                        if (progress > 1.0f) progress = 1.0f;
                        specialCharge = specialDrainStartCharge - (int) (specialDrainStartCharge * progress);
                        if (specialCharge < 0) specialCharge = 0;
                    }
                    return; // stay in ACTIVE
                case SPECIAL_STATE_COOLDOWN:
                    if (now >= specialCooldownUntil) {
                        // Cooldown ended: ready for orb refill
                        specialState = SPECIAL_STATE_IDLE;
                        continue; // re-evaluate new state immediately
                    }
                    return; // stay in COOLDOWN
                case SPECIAL_STATE_IDLE:
                default:
                    // In IDLE, orb pickups can refill the meter (handled in updatePickups)
                    return;
            }
        }
    }

    /**
     * Fires the character's skill at its fixed power.
     *
     * <p>The skill is permanent — Skill Pasif left the shop — so its power is fixed: there is
     * nothing to buy and no stacks to scale it. Duration-based skills (Hunter's Instinct, Second
     * Flame) keep their window as the effect, because the window is what they <em>are</em>. The
     * skill fires automatically on the cooldown — the player never presses anything.
     *
     * <p>Burst damage sits on the same enemy-HP scale as the weapon table's 2026-10-20 retune
     * (Shark Bite 60, The Ancient One 40, up from 6/4): on the old scale a permanent 15-second
     * skill could not even dent a minute-0 fan, while 60 one-shots one inside the 70px radius.
     */
    private void activateSkill(long now) {
        skillReadyAt = now + SKILL_COOLDOWN_MS;
        // Skill Up: +4% damage per level, additive up to the 10-level cap (max 40%).
        int skillMul = 100 + Math.min(skillUpLevel * 4, 40);
        if (characterIndex == 0) { // Shark Bite
            damageEnemiesInRadius((70 * skillMul) / 100, 60, true);
        } else if (characterIndex == 1) { // Hunter's Instinct
            slowEnemiesUntil = now + 3500L;
            for (int i = 0; i < enemies.length; i++) if (enemies[i].active) enemies[i].nextAttack = now + 1200L;
        } else if (characterIndex == 2) { // Undying
            shield = 20;
            shieldReadyAt = now + 15000L;
        } else if (characterIndex == 3) { // Second Flame
            lifestealUntil = now + 8000L;
        } else { // The Ancient One
            invulUntil = now + 2500L;
            damageEnemiesInRadius((45 * skillMul) / 100, 40, false);
        }
    }

    /**
     * Empties the meter and spends the special on the character's effect.
     *
     * <p>Called only from the gated site in {@code updatePlayer}: the Special Attack upgrade must
     * be owned and the meter full, or the key never reaches here. The meter — not the run — is
     * what is spent: SPECIAL orb drops refill it, so every full meter buys another cast.
     *
     * <p>Shark Call and Phoenix Fire carry 180/240 damage — the weapon table's 2026-10-20 scale,
     * same factor as the skill bursts above — so a spent meter clears a crowd instead of chipping
     * it. Radii (110/130px) and every other special's effect are unchanged.
     */
    private void activateSpecial(long now) {
        // Enter ACTIVE state: meter will drain over SPECIAL_ACTIVE_DURATION_MS
        specialState = SPECIAL_STATE_ACTIVE;
        specialDrainStartCharge = specialCharge;
        specialActiveUntil = now + SPECIAL_ACTIVE_DURATION_MS;
        specialCooldownUntil = 0L;
        // Trigger a brief release flash on the special bar (200 ms pulse).
        specialFlashUntil = now + 200L;
        // Growth applies to special attacks too: flat bonus per player level per rank.
        int growthBonus = growthLevel * playerLevel;
        if (characterIndex == 0) { // Shark Call
            damageEnemiesInRadius(110 + growthBonus, 180, true);
            spawnEffect(EFFECT_SHARK_RING, px, py, 180, (int) SPECIAL_ACTIVE_DURATION_MS, 0, 180, 255);
        } else if (characterIndex == 1) { // Slow Time
            slowEnemiesUntil = now + 6000L;
            spawnEffect(EFFECT_SLOW_AURA, px, py, 120, (int) SPECIAL_ACTIVE_DURATION_MS, 100, 200, 255);
        } else if (characterIndex == 2) { // The Reaper
            playerHp = Math.min(playerMaxHp, playerHp + Math.max(8, playerMaxHp / 4));
            lifestealUntil = now + 10000L;
            spawnEffect(EFFECT_REAPER_HEAL, px, py, 0, (int) SPECIAL_ACTIVE_DURATION_MS, 0, 255, 100);
        } else if (characterIndex == 3) { // Phoenix Fire
            damageEnemiesInRadius(130 + growthBonus, 240, true);
            spawnEffect(EFFECT_PHOENIX_RING, px, py, 240, (int) SPECIAL_ACTIVE_DURATION_MS, 255, 120, 0);
        } else { // Spirit of the Oni
            shield = 30;
            shieldReadyAt = now + 10000L;
            clearEnemyShots();
            spawnEffect(EFFECT_ONI_SHIELD, px, py, 80, (int) SPECIAL_ACTIVE_DURATION_MS, 180, 100, 255);
        }
    }

    /** Spawns a visual effect at the given position. Finds a free slot or overwrites the oldest. */
    private void spawnEffect(int type, int x, int y, int maxRadius, int duration, int r, int g, int b) {
        if (effectType == null) return; // not initialized (e.g., in headless tests)
        int slot = -1;
        int oldestTimer = -1;
        for (int i = 0; i < MAX_EFFECTS; i++) {
            if (effectType[i] == EFFECT_NONE) { slot = i; break; }
            if (effectTimer[i] > oldestTimer) { oldestTimer = effectTimer[i]; slot = i; }
        }
        if (slot == -1) slot = 0;
        effectType[slot] = type;
        effectX[slot] = x;
        effectY[slot] = y;
        effectRadius[slot] = 0;
        effectMaxRadius[slot] = maxRadius;
        effectTimer[slot] = 0;
        effectMaxTimer[slot] = duration;
        effectColorR[slot] = r;
        effectColorG[slot] = g;
        effectColorB[slot] = b;
    }

    /** Updates all active visual effects. Called once per frame during gameplay. */
    private void updateEffects(long now) {
        if (effectType == null) return; // not initialized (e.g., in headless tests)
        for (int i = 0; i < MAX_EFFECTS; i++) {
            if (effectType[i] == EFFECT_NONE) continue;
            effectTimer[i] += 16; // ~60 FPS tick
            if (effectTimer[i] >= effectMaxTimer[i]) {
                effectType[i] = EFFECT_NONE;
                continue;
            }
            float progress = (float) effectTimer[i] / (float) effectMaxTimer[i];
            switch (effectType[i]) {
                case EFFECT_SHARK_RING:
                case EFFECT_PHOENIX_RING:
                case EFFECT_ONI_SHIELD:
                    effectRadius[i] = (int) (effectMaxRadius[i] * progress);
                    break;
                case EFFECT_SLOW_AURA:
                    // Pulsing aura: radius oscillates
                    effectRadius[i] = (int) (effectMaxRadius[i] * (0.5f + 0.5f * MathUtil.sin(progress * 8f * 3.14159f)));
                    break;
                case EFFECT_REAPER_HEAL:
                    // Particles rise and fade - radius used as particle spread
                    effectRadius[i] = (int) (effectMaxRadius[i] * progress);
                    break;
            }
        }
    }

    /** Draws all active visual effects. Called from draw() during gameplay. */
    private void drawEffects(Graphics g, int offX, int offY) {
        if (effectType == null) return;
        for (int i = 0; i < MAX_EFFECTS; i++) {
            if (effectType[i] == EFFECT_NONE) continue;
            float progress = (float) effectTimer[i] / (float) effectMaxTimer[i];
            int alpha = (int) (255 * (1.0f - progress));
            if (alpha <= 0) continue;
            int cx = offX + effectX[i];
            int cy = offY + effectY[i];
            g.setColor(effectColorR[i], effectColorG[i], effectColorB[i]);
            switch (effectType[i]) {
                case EFFECT_SHARK_RING:
                    // Expanding ring with thickness
                    drawThickCircle(g, cx, cy, effectRadius[i], 3);
                    drawThickCircle(g, cx, cy, effectRadius[i] - 4, 2);
                    break;
                case EFFECT_PHOENIX_RING:
                    // Double expanding rings (fire style)
                    drawThickCircle(g, cx, cy, effectRadius[i], 4);
                    drawThickCircle(g, cx, cy, effectRadius[i] / 2, 2);
                    break;
                case EFFECT_ONI_SHIELD:
                    // Shield bubble + inner glow
                    drawThickCircle(g, cx, cy, effectRadius[i], 3);
                    drawThickCircle(g, cx, cy, effectRadius[i] - 6, 1);
                    break;
                case EFFECT_SLOW_AURA:
                    // Pulsing aura - multiple concentric circles
                    for (int ring = 0; ring < 3; ring++) {
                        int r = effectRadius[i] - ring * 8;
                        if (r > 0) drawThickCircle(g, cx, cy, r, 1);
                    }
                    break;
                case EFFECT_REAPER_HEAL:
                    // Rising green particles
                    for (int p = 0; p < 8; p++) {
                        int px = cx + (rand(20) - 10);
                        int py = cy - (int) (progress * effectMaxRadius[i]) + (rand(10) - 5);
                        g.fillRect(px, py, 2, 4);
                    }
                    break;
            }
        }
    }

    /** Helper: draws a circle outline using Bresenham midpoint algorithm. */
    private void drawThickCircle(Graphics g, int cx, int cy, int radius, int thickness) {
        if (radius <= 0) return;
        int x = 0;
        int y = radius;
        int d = 1 - radius;
        while (x <= y) {
            for (int t = 0; t < thickness; t++) {
                int rt = radius - t;
                if (rt > 0) {
                    g.drawLine(cx - x, cy - rt, cx + x, cy - rt);
                    g.drawLine(cx - x, cy + rt, cx + x, cy + rt);
                    g.drawLine(cx - rt, cy - x, cx + rt, cy - x);
                    g.drawLine(cx - rt, cy + x, cx + rt, cy + x);
                }
            }
            if (d < 0) d += 2 * x + 3;
            else { d += 2 * (x - y) + 5; y--; }
            x++;
        }
    }

    private void updateTimedEvents(long now) {
        long elapsed = elapsedMsAt(now);
        if (elapsed >= 180000L && !ringEventDone) {
            ringEventDone = true;
            spawnWave(16, true);
        }
        if (elapsed >= 900000L && !hordeEventDone) {
            hordeEventDone = true;
            spawnWave(18, false);
        }
        nextWaveAt = now + 30000L;
    }

    private void updateSpawns(long now) {
        if (boss.active) return;
        if (now < nextSpawnAt) return;
        if (activeEnemyCount() >= stage.maxEnemies) return;
        long elapsed = elapsedMsAt(now);
        int interval = stage.spawnIntervalMs - (int) (elapsed / 5000L) * 20;
        if (interval < 250) interval = 250;
        if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET) interval = Math.min(interval, 550);
        spawnEnemy(now);
        // Marketing adds one extra fan per level to this very interval, each one still bound by
        // the stage population cap — it widens the wave, it never overflows it.
        for (int i = 0; i < marketingLevel; i++) {
            if (activeEnemyCount() >= stage.maxEnemies) break;
            spawnEnemy(now);
        }
        nextSpawnAt = now + interval;
    }

    private void spawnEnemy(long now) {
        int index = freeEnemy();
        if (index == -1) return;
        int roll = rand(100);
        String id = roll < 55 ? stage.enemyA : (roll < 85 ? stage.enemyB : stage.enemyC);
        int defIndex = enemyIndex(id);
        if (defIndex == -1) defIndex = 0;
        ContentCatalog.EnemyDef def = ContentCatalog.ENEMIES[defIndex];
        Enemy e = enemies[index];
        e.active = true;
        e.defIndex = defIndex;
        e.maxHp = scaledEnemyHp(def.hp);
        e.hp = e.maxHp;
        e.atk = scaledEnemyAtk(def.atk);
        e.speed = scaledEnemySpeed(def.speed);
        e.nextAttack = now + def.cooldownMs;
        e.anim = rand(3);
        e.nextAnim = now + 150L;
        e.exp = def.exp;
        e.knockResist = 0;
        e.lastKnockHit = 0;
        e.kbUntil = 0;
        e.chargeUntil = 0;
        e.orbitEndsAt = 0;
        e.orbitDir = 0;
        e.moveCarry = 0;
        e.healAt = now + 2000L;
        placeAtEdge(e, now);
    }

    private void placeAtEdge(Enemy e, long now) {
        int w = enemyWidth(e.defIndex);
        int h = enemyHeight(e.defIndex);
        for (int attempt = 0; attempt < 8; attempt++) {
            int side = rand(4);
            if (side == 0) { e.x = rand(Math.max(1, worldWidth - w)); e.y = -h; }
            else if (side == 1) { e.x = worldWidth; e.y = rand(Math.max(1, worldHeight - h)); }
            else if (side == 2) { e.x = rand(Math.max(1, worldWidth - w)); e.y = worldHeight; }
            else { e.x = -w; e.y = rand(Math.max(1, worldHeight - h)); }
            long dx = e.x - playerCenterX();
            long dy = e.y - playerCenterY();
            if (dx * dx + dy * dy >= 6400L || attempt == 7) break;
        }
    }

    private void updateEnemies(long now) {
        boolean slow = now < slowEnemiesUntil;
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i];
            if (!e.active) continue;
            ContentCatalog.EnemyDef def = ContentCatalog.ENEMIES[e.defIndex];
            int w = enemyWidth(e.defIndex);
            int h = enemyHeight(e.defIndex);
            double speed = e.speed * (slow ? 0.35 : 1.0);
            int dx = playerCenterX() - (e.x + w / 2);
            int dy = playerCenterY() - (e.y + h / 2);
            int distSq = dx * dx + dy * dy;

            if (def.behavior == ContentCatalog.AI_SHOOTER) {
                if (distSq < 3600 && dx > 0) e.x -= step(speed);
                if (distSq < 3600 && dx < 0) e.x += step(speed);
                if (distSq > 10000 && dx > 0) e.x += step(speed);
                if (distSq > 10000 && dx < 0) e.x -= step(speed);
                if (distSq > 2500 && dy > 0) e.y += step(speed);
                if (distSq > 2500 && dy < 0) e.y -= step(speed);
                if (now >= e.nextAttack && distSq < 15000) {
                    fireEnemyShot(e.x + w / 2, e.y + h / 2, playerCenterX(), playerCenterY(), e.atk, 70);
                    e.nextAttack = now + def.cooldownMs;
                }
            } else if (def.behavior == ContentCatalog.AI_CHARGER) {
                if (now < e.chargeUntil) {
                    e.x += e.chargeDx;
                    e.y += e.chargeDy;
                } else {
                    moveToward(e, dx, dy, speed);
                    if (distSq < 9000 && now >= e.nextAttack) {
                        double len = Math.sqrt((double) distSq);
                        if (len > 0.0) {
                            e.chargeDx = (int) ((dx / len) * 7.0);
                            e.chargeDy = (int) ((dy / len) * 7.0);
                            if (e.chargeDx == 0) e.chargeDx = facingX;
                            e.chargeUntil = now + 650L;
                            e.nextAttack = now + def.cooldownMs;
                        }
                    }
                }
            } else if (def.behavior == ContentCatalog.AI_ORBIT_CHARGER) {
                updateOrbitCharger(e, def, dx, dy, distSq, speed, now);
            } else if (def.behavior == ContentCatalog.AI_BOMBER) {
                moveToward(e, dx, dy, speed * 0.7);
                if (distSq < 900 && now >= e.nextAttack) {
                    if (playerInvulnerable(now)) { /* hit still consumed */ }
                    else damagePlayer(e.atk, now);
                    e.active = false; // bomber explosion is not an ordinary defeat.
                }
            } else if (def.behavior == ContentCatalog.AI_HEALER) {
                moveToward(e, dx, dy, speed * 0.45);
                if (now >= e.healAt) {
                    healNearbyEnemies(18, 4);
                    e.healAt = now + 2000L;
                }
            } else {
                moveToward(e, dx, dy, speed);
            }

            applyEnemyKnockback(e, now);
            clampEnemy(e, w, h);
            if (now >= e.nextAnim) { e.nextAnim = now + 150L; e.anim = (e.anim + 1) % 3; }
            if (now < invulUntil || now < e.nextAttack) {
                // Contact damage respects the same two gates every other attack path uses: the
                // global invulnerability window, and this enemy's own cooldown. nextAttack is set
                // to now + cooldown when the enemy spawns and again after each hit, so it is a
                // "not ready until" stamp -- the ready state is now >= nextAttack, as used by the
                // bomber, shooter, charger, orbit and boss paths. This guard had it the other way
                // round, which made a chaser deal damage only during the first second of its life
                // and then go permanently harmless.
            } else if (overlap(px, py, PLAYER_HIT_W, PLAYER_HIT_H, e.x, e.y, w, h)) {
                damagePlayer(e.atk, now);
                e.nextAttack = now + def.cooldownMs;
            }
            if (now - e.lastKnockHit > 10000L) e.knockResist = 0;
        }
    }

    private void moveToward(Enemy e, int dx, int dy, double speed) {
        if (dx > 0) e.x += step(speed);
        if (dx < 0) e.x -= step(speed);
        if (dy > 0) e.y += step(speed);
        if (dy < 0) e.y -= step(speed);
    }

    // ---- AI_ORBIT_CHARGER pacing, tuned for Biji Sawit in Arena Hutan Sawit -----------------
    //
    // The stage's swarm enemy circles the player for a few random laps and only then darts, so a
    // horde reads as a milling crowd rather than a wall of simultaneous dashes. Movement is driven
    // by moveEnemyVector at rates derived from the enemy's speed stat instead of step(), because
    // step() floors every speed the catalog's column can express down to 1px per tick and a chase
    // slower than 1px is exactly the nerf this enemy needs. With the current stat (0.48) that works
    // out to ~0.72px/tick approaching against a chaser's 1px, a ~3.8px/tick circling stride, and a
    // 5px/tick closing dart against the plain charger's 7px.
    private static final int ORBIT_RADIUS = 55;          // px held from the player while circling
    private static final int ORBIT_ENGAGE_DIST = 110;    // px at which circling starts
    private static final double APPROACH_PX_PER_SPEED = 1.5;
    private static final double ORBIT_PX_PER_SPEED = 8.0;
    private static final double ORBIT_CHARGE_PX = 5.0;   // px/tick during the closing dart
    private static final long ORBIT_CHARGE_MS = 650L;
    private static final int ORBIT_LAPS_MIN = 2;
    private static final int ORBIT_LAPS_RANDOM = 2;      // laps = MIN + rand(this), i.e. 2 or 3
    private static final double TICKS_PER_SECOND = 60.0;
    private static final long ORBIT_MIN_LAP_MS = 900L;   // floor so a slow tick estimate cannot stall

    /**
     * One orbit-charger tick. Approaches at a nerfed sub-pixel pace, circles the player for a few
     * random laps once close and off cooldown, then commits the same kind of dash an
     * {@link ContentCatalog#AI_CHARGER} makes, and starts the cycle over.
     */
    private void updateOrbitCharger(Enemy e, ContentCatalog.EnemyDef def, int dx, int dy,
            int distSq, double speed, long now) {
        if (now < e.chargeUntil) {
            e.x += e.chargeDx;      // mid-dash: direction was locked when the wind-up ended
            e.y += e.chargeDy;
            return;
        }
        if (e.orbitEndsAt != 0) {
            if (now < e.orbitEndsAt) {
                orbitStep(e, dx, dy, speed * ORBIT_PX_PER_SPEED);
                return;
            }
            // Laps done: wind up and dart at the player, then the cooldown restarts the cycle.
            e.orbitEndsAt = 0;
            double len = Math.sqrt((double) distSq);
            if (len > 0.0) {
                e.chargeDx = (int) ((dx / len) * ORBIT_CHARGE_PX);
                e.chargeDy = (int) ((dy / len) * ORBIT_CHARGE_PX);
                if (e.chargeDx == 0 && e.chargeDy == 0) e.chargeDx = facingX;
                e.chargeUntil = now + ORBIT_CHARGE_MS;
                e.nextAttack = now + def.cooldownMs;
            }
            return;
        }
        moveEnemyVector(e, dx, dy, speed * APPROACH_PX_PER_SPEED);
        if (distSq < ORBIT_ENGAGE_DIST * ORBIT_ENGAGE_DIST && now >= e.nextAttack) {
            // Each approach rolls its own direction and lap count, so a swarm splits into both
            // spin directions at mixed radii instead of marching in one formation.
            e.orbitDir = rand(2) == 0 ? -1 : 1;
            double orbitPx = speed * ORBIT_PX_PER_SPEED;
            long lapMs = (long) (2 * Math.PI * ORBIT_RADIUS / (orbitPx * TICKS_PER_SECOND) * 1000.0);
            if (lapMs < ORBIT_MIN_LAP_MS) lapMs = ORBIT_MIN_LAP_MS;
            e.orbitEndsAt = now + lapMs * (ORBIT_LAPS_MIN + rand(ORBIT_LAPS_RANDOM));
        }
    }

    /** Tangential movement around the player at {@link #ORBIT_RADIUS}, with radial correction. */
    private void orbitStep(Enemy e, int dx, int dy, double pxPerTick) {
        if (dx == 0 && dy == 0) return;
        double len = Math.sqrt((double) (dx * dx + dy * dy));
        double tangentX = -dy * e.orbitDir;
        double tangentY = dx * e.orbitDir;
        double radial = 0.0;
        if (len > 1.0) {
            // Positive radial moves toward the player, so an enemy that drifted wide spirals back
            // in and one that got too close drifts out, holding the circling ring without
            // computing an angle per tick.
            if (len > ORBIT_RADIUS + 12) radial = 0.6;
            else if (len < ORBIT_RADIUS - 12) radial = -0.6;
        }
        moveEnemyVector(e, tangentX + (dx / len) * radial, tangentY + (dy / len) * radial, pxPerTick);
    }

    /**
     * Moves an enemy along a direction vector by a possibly sub-pixel amount, carrying the
     * fractional remainder across ticks so a rate below 1px still advances steadily.
     */
    private void moveEnemyVector(Enemy e, double dirX, double dirY, double pxPerTick) {
        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.001) return;
        double total = e.moveCarry + pxPerTick;
        int whole = (int) total;
        if (whole < 1) { e.moveCarry = total; return; }
        e.moveCarry = total - whole;
        e.x += roundPx((dirX / len) * whole);
        e.y += roundPx((dirY / len) * whole);
    }

    /** Round to nearest, since CLDC's Math has no round(double). */
    private int roundPx(double v) { return (int) Math.floor(v + 0.5); }

    private void updateBossDirector(long now) {
        if (stage.timeStage || stage.bossA == null || stage.bossA.length() == 0) return;
        int elapsedSec = (int) (elapsedMsAt(now) / 1000L);
        if (boss.active) return;
        if (bossEncounterIndex < 0) {
            if (!encounterStarted[0] && elapsedSec >= stage.bossATimeSec) { spawnBoss(0); return; }
            if (!encounterStarted[1] && elapsedSec >= stage.bossBTimeSec) { spawnBoss(1); return; }
            // Hard second 10:00 boss (verified for Stage 1 Hard: Smol Ame).
            // The single Boss slot cannot hold two bosses at once, so the
            // second appears at 10:00 when free, otherwise right after the
            // first falls. Other hard stages stay null until their stage page
            // is verified; no new timings are invented here.
            if (stage.hard && !encounterStarted[3] && elapsedSec >= 600) {
                String second = hardSecondBoss(stageIndex);
                if (second != null) { spawnBossById(second, 3); return; }
            }
        }
        if (mode.completion == ContentCatalog.COMPLETE_ON_DEATH && elapsedSec >= 1200
                && nextEndlessEncounter < 6) {
            spawnBoss(nextEndlessEncounter % 2);
            nextEndlessEncounter++;
        }
    }

    private void spawnBoss(int encounter) {
        String bossId;
        if (encounter == 0) bossId = stage.bossA;
        else if (encounter == 1) bossId = stage.bossB;
        else bossId = hardThirdBoss(stageIndex);
        spawnBossById(bossId, encounter);
    }

    private void spawnBossById(String bossId, int encounter) {
        int index = bossIndex(bossId);
        if (index == -1) return;
        ContentCatalog.BossDef def = ContentCatalog.BOSSES[index];
        boss.active = true;
        bossEncounterIndex = encounter;
        encounterStarted[Math.min(encounter, 3)] = true;
        boss.defIndex = index;
        // The HUD boss bar draws this field the moment boss.active turns true. It once lived in a
        // refresh block inside drawHud that the digit-font refactor deleted, so drawString(null)
        // killed the game thread on the first boss spawn (platforms that reject a null string
        // freeze the run); the label is therefore assigned here, at the only activation site.
        cachedBossName = def.name;
        boss.maxHp = scaledBossHp(def.hp);
        boss.hp = boss.maxHp;
        boss.atk = (int) MathUtil.power(def.atk + 2.0 * scaleA, 1.0 + scaleB / 25.0);
        boss.speed = 0.55 + Math.min(0.5, scaleA * 0.005);
        boss.x = worldWidth / 2 - 20;
        boss.y = -50;
        boss.nextAttack = nowAttackDelay();
        boss.anim = 0;
        boss.nextAnim = nowAnimDelay();
        boss.hitMask = 0L;
        boss.chargeUntil = 0;
        boss.phase = 1;
        boss.maxPhase = 1;
        boss.phaseTransitionUntil = 0;
        boss.isPhaseTransition = false;
    }

    /** Spawns an additional boss (for multi-boss encounters). Returns the boss instance or null if full. */
    Boss spawnAdditionalBoss(String bossId, int x, int y) {
        if (activeBossCount >= MAX_ACTIVE_BOSSES) return null;
        int index = bossIndex(bossId);
        if (index == -1) return null;
        ContentCatalog.BossDef def = ContentCatalog.BOSSES[index];
        // Find free slot
        int slot = -1;
        for (int i = 0; i < MAX_ACTIVE_BOSSES; i++) {
            if (activeBosses[i] == null) {
                activeBosses[i] = new Boss();
                slot = i;
                break;
            } else if (!activeBosses[i].active) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return null;
        Boss bb = activeBosses[slot];
        bb.active = true;
        bb.defIndex = index;
        bb.maxHp = scaledBossHp(def.hp);
        bb.hp = bb.maxHp;
        bb.atk = (int) MathUtil.power(def.atk + 2.0 * scaleA, 1.0 + scaleB / 25.0);
        bb.speed = 0.55 + Math.min(0.5, scaleA * 0.005);
        bb.x = x;
        bb.y = y;
        bb.nextAttack = nowAttackDelay();
        bb.anim = 0;
        bb.nextAnim = nowAnimDelay();
        bb.hitMask = 0L;
        bb.chargeUntil = 0;
        bb.phase = 1;
        bb.maxPhase = 1;
        bb.phaseTransitionUntil = 0;
        bb.isPhaseTransition = false;
        activeBossCount++;
        return bb;
    }

    /** Triggers a phase transition for a boss (visual + stat changes). */
    void triggerBossPhaseTransition(Boss bb, int newPhase, int newMaxPhase) {
        if (bb == null || newPhase <= bb.phase) return;
        bb.phase = newPhase;
        bb.maxPhase = Math.max(bb.maxPhase, newMaxPhase);
        bb.isPhaseTransition = true;
        bb.phaseTransitionUntil = System.currentTimeMillis() + 1000; // 1 second flash
        // Could add stat scaling per phase here
        if (bb.maxPhase > 1) {
            // Scale HP/ATK for new phase
            double phaseMultiplier = 1.0 + (bb.phase - 1) * 0.5; // 50% more per phase
            bb.maxHp = (int) (bb.maxHp * phaseMultiplier);
            bb.hp = bb.maxHp; // Full heal on phase transition
            bb.atk = (int) (bb.atk * (1.0 + (bb.phase - 1) * 0.25));
        }
    }

    /** Checks if any boss should transition to next phase based on HP thresholds. */
    private void checkBossPhaseTransitions(long now) {
        // Primary boss
        if (boss.active && boss.maxPhase > 1) {
            double hpRatio = (double) boss.hp / (double) boss.maxHp;
            int targetPhase = (int) ((1.0 - hpRatio) * boss.maxPhase) + 1;
            if (targetPhase > boss.phase && targetPhase <= boss.maxPhase) {
                triggerBossPhaseTransition(boss, targetPhase, boss.maxPhase);
            }
        }
        // Additional bosses
        if (activeBosses != null) {
            for (int i = 0; i < MAX_ACTIVE_BOSSES; i++) {
                Boss bb = activeBosses[i];
                if (bb != null && bb.active && bb.maxPhase > 1) {
                    double hpRatio = (double) bb.hp / (double) bb.maxHp;
                    int targetPhase = (int) ((1.0 - hpRatio) * bb.maxPhase) + 1;
                    if (targetPhase > bb.phase && targetPhase <= bb.maxPhase) {
                        triggerBossPhaseTransition(bb, targetPhase, bb.maxPhase);
                    }
                }
            }
        }
    }

    private long nowAttackDelay() { return System.currentTimeMillis() + 900L; }
    private long nowAnimDelay() { return System.currentTimeMillis() + 180L; }

    private void updateBoss(long now) {
        // Check phase transitions for all bosses
        checkBossPhaseTransitions(now);

        // Update primary boss
        if (boss.active) {
            updateSingleBoss(boss, now);
        }

        // Update additional bosses
        if (activeBosses != null) {
            for (int i = 0; i < MAX_ACTIVE_BOSSES; i++) {
                Boss bb = activeBosses[i];
                if (bb != null && bb.active) {
                    updateSingleBoss(bb, now);
                }
            }
        }
    }

    /** Updates a single boss instance (primary or additional). */
    private void updateSingleBoss(Boss bb, long now) {
        ContentCatalog.BossDef def = ContentCatalog.BOSSES[bb.defIndex];
        int dx = playerCenterX() - (bb.x + bossWidth(bb) / 2);
        int dy = playerCenterY() - (bb.y + bossHeight(bb) / 2);
        int distSq = dx * dx + dy * dy;
        double speed = bb.speed * (now < slowEnemiesUntil ? 0.35 : 1.0);
        if (def.behavior == ContentCatalog.AI_SHOOTER) {
            if (distSq > 2500) {
                bb.x += MathUtil.clampInt(dx, -2, 2);
                bb.y += MathUtil.clampInt(dy, -1, 1);
            }
            if (now >= bb.nextAttack) {
                int shots = 5 + (int) Math.min(6.0, scaleA / 8.0);
                int originX = bb.x + bossWidth(bb) / 2;
                int originY = bb.y + bossHeight(bb) / 2;
                for (int i = 0; i < shots; i++) {
                    double angle = (Math.PI * 2.0 * i / shots) + (now / 1000.0);
                    int tx = originX + (int) (Math.cos(angle) * 100.0);
                    int ty = originY + (int) (Math.sin(angle) * 100.0);
                    fireEnemyShot(originX, originY, tx, ty, bb.atk, 65);
                }
                bb.nextAttack = now + def.cooldownMs;
            }
        } else if (def.behavior == ContentCatalog.AI_CHARGER) {
            if (now < bb.chargeUntil) {
                bb.x += bb.chargeDx;
                bb.y += bb.chargeDy;
            } else {
                if (dx > 0) bb.x += step(speed * 1.25);
                if (dx < 0) bb.x -= step(speed * 1.25);
                if (dy > 0) bb.y += step(speed * 0.75);
                if (dy < 0) bb.y -= step(speed * 0.75);
                if (distSq < 20000 && now >= bb.nextAttack) {
                    double len = Math.sqrt((double) distSq);
                    bb.chargeDx = len > 0.0 ? (int) (dx / len * 9.0) : facingX;
                    bb.chargeDy = len > 0.0 ? (int) (dy / len * 9.0) : 0;
                    bb.chargeUntil = now + 900L;
                    bb.nextAttack = now + def.cooldownMs;
                }
            }
        } else {
            if (dx > 0) bb.x += step(speed);
            if (dx < 0) bb.x -= step(speed);
            if (dy > 0) bb.y += step(speed);
            if (dy < 0) bb.y -= step(speed);
            if (now >= bb.nextAttack) {
                if (def.behavior == ContentCatalog.AI_BOMBER && distSq < 2000) {
                    damagePlayer(bb.atk, now);
                    defeatBoss(bb, -1);
                    return;
                }
                fireEnemyShot(bb.x + bossWidth(bb) / 2, bb.y + bossHeight(bb) / 2,
                        playerCenterX(), playerCenterY(), bb.atk, 80);
                bb.nextAttack = now + def.cooldownMs;
            }
        }
        if (bb.x < -60) bb.x = -60;
        if (bb.y < -80) bb.y = -80;
        if (bb.x > worldWidth) bb.x = worldWidth;
        if (bb.y > worldHeight) bb.y = worldHeight;
        if (now >= bb.nextAnim) { bb.nextAnim = now + 180L; bb.anim++; }
        if (now >= invulUntil && overlap(px, py, 24, 24, bb.x, bb.y, bossWidth(bb), bossHeight(bb))) {
            damagePlayer(bb.atk, now);
        }
    }



    private int bossWidth() { return bossWidth(boss); }
    private int bossHeight() { return bossHeight(boss); }

    private int bossWidth(Boss bb) {
        if (bb.active && bb.defIndex >= 0 && bb.defIndex < ContentCatalog.BOSSES.length) {
            return ContentCatalog.BOSSES[bb.defIndex].collW;
        }
        return 42;
    }

    private int bossHeight(Boss bb) {
        if (bb.active && bb.defIndex >= 0 && bb.defIndex < ContentCatalog.BOSSES.length) {
            return ContentCatalog.BOSSES[bb.defIndex].collH;
        }
        return 42;
    }

    private void defeatBoss() {
        defeatBoss(boss, bossEncounterIndex);
    }

    /** Defeats a specific boss instance (primary or additional). */
    private void defeatBoss(Boss bb, int encounterSlot) {
        // Idempotence guard.
        if (!bb.active) return;
        bb.active = false;
        if (bb == boss) {
            bossEncounterIndex = -1;
        } else {
            activeBossCount--;
        }
        if (encounterSlot >= 0 && encounterSlot < encounterDefeated.length) encounterDefeated[encounterSlot] = true;
        // A defeated boss is a defeated enemy: the wiki counts bosses and mini-bosses among the
        // enemies defeated (they feed the score and the clear reward's defeatCount/4 term), so
        // both counters move here exactly as damageEnemy does for a regular fan.
        kills++;
        defeatCount++;
        ContentCatalog.BossDef def = ContentCatalog.BOSSES[bb.defIndex];
        dropPickup(bb.x + bossWidth(bb) / 2, bb.y + bossHeight(bb) / 2, 0, def.exp, System.currentTimeMillis());
        for (int i = 0; i < 5; i++) dropPickup(bb.x + bossWidth(bb) / 2, bb.y + bossHeight(bb) / 2, 1, 10, System.currentTimeMillis());
        if (mode.completion == ContentCatalog.COMPLETE_FINAL_BOSS) {
            // The final boss is the 20:00 encounter (slot 1) on every stage:
            // the unsourced 30:00 duplicate was removed, so hard stages no
            // longer need a third slot to win.
            boolean finalDefeated = encounterDefeated[1];
            if (finalDefeated) {
                // Decision C: the stage-clear reward joins the permanent balance, while the
                // level-up +100 stays run-local. Pickup credits are debounced (see
                // SaveStore.COIN_WRITE_INTERVAL_MS), but this one-time grant flushes at once:
                // it is the largest single payout of a run, and it must not depend on where the
                // debounce window happened to stand when the boss fell. A refused write leaves
                // the balance as it is and the run continues regardless. This is reached only
                // once per run: defeatBoss clears boss.active on its first line and every caller
                // of damageBoss is gated on it.
                int clearReward = stage.coinClear + defeatCount / 4;
                coins += clearReward;
                if (save != null) {
                    save.creditCoins(clearReward);
                    save.flush();
                }
                won = true;
                state = STATE_VICTORY;
            }
        }
    }

    private void updateWeapons(long now, boolean attackPressed) {
        for (int s = 0; s < weaponCount; s++) {
            int wid = weaponSlots[s];
            if (wid < 0 || now < weaponReadyAt[s]) continue;
            ContentCatalog.WeaponDef def = ContentCatalog.weapon(wid);
            // Weapons fire when cooldown ready;
            fireWeapon(s, def, now);
            int haste = 100 + hastePercent + (weaponLevels[s] - 1) * 5;
            int cooldown = Math.max(80, def.cooldownMs * 100 / Math.max(50, haste));
            weaponReadyAt[s] = now + cooldown;
        }
    }

    private void fireWeapon(int slot, ContentCatalog.WeaponDef def, long now) {
        int level = weaponLevels[slot];
        int damage = def.damage + Math.max(0, level - 1);
        // atkPercent IS the multiplier, not a bonus on top of one: the wiki records it as
        // atk_multiplier_percent, and the game shows it as "ATK 1.10", i.e. the character deals
        // 110% of base. The two sibling stats confirm the convention -- speed is read as
        // 120 * spdPercent / 100 with no offset, and crit as a straight percentage. This line used
        // to add a spurious 100, so Gura's 1.10 was applied as 2.10 and Kiara's 1.00 as 2.00: every
        // character hit for roughly double what the table says. atkPercentBonus is the shop's
        // "+6% damage per level" and is additive on the same multiplier, so a level of it moves
        // 110 to 116 rather than being added to a base that was already counted twice.
        // Growth: flat damage bonus per player level per rank (0.1 -> 0.3 per level at rank 3),
        // added before the ATK multiplier so it scales with the character stat.
        if (growthLevel > 0 && character != null) damage += growthLevel * playerLevel;
        damage = (damage * (character.atkPercent + atkPercentBonus)) / 100;
        boolean crit = rand(100) < MathUtil.clampInt(character.critPercent + critPercentBonus, 0, 75);
        if (crit) damage *= 2; // critical multiplier is a documented-mechanic approximation.
        int count = def.count + (weaponLevels[slot] >= 5 ? 1 : 0);
        if (def.pattern == ContentCatalog.PATTERN_MELEE_ARC) {
            createMelee(def, damage, crit);
            return;
        }
        for (int i = 0; i < count; i++) {
            int index = freeShot();
            if (index == -1) break;
            Shot s = shots[index];
            s.active = true;
            s.fromPlayer = true;
            s.pattern = def.pattern;
            // Catalog index, not the inventory slot: updateShots resolves
            // steering speed via ContentCatalog.weapon(s.weaponIndex).
            s.weaponIndex = weaponSlots[slot];
            s.damage = damage;
            s.critical = crit;
            s.knockback = def.knockback;
            s.area = def.area;
            s.life = Math.max(8, def.range / 3);
            s.age = 0;
            s.hitMask = 0L;
            s.bossHit = false;
            s.attached = def.pattern == ContentCatalog.PATTERN_ORBIT;
            s.returning = false;
            s.target = nearestTarget();
            s.sprite = def.sprite;
            measureShotBox(s);
            if (s.attached) {
                s.orbitAngle = (Math.PI * 2.0 * i / count) + now / 900.0;
                s.x = playerCenterX(); s.y = playerCenterY();
                s.dx = 0; s.dy = 0;
                s.life = 100 + level * 8;
            } else {
                int ox = playerCenterX();
                int oy = playerCenterY() + 2;
                int tx;
                int ty;
                if (def.pattern == ContentCatalog.PATTERN_HOMING || s.target != TARGET_NONE) {
                    if (s.target == TARGET_BOSS) {
                        tx = boss.x + bossWidth() / 2;
                        ty = boss.y + bossHeight() / 2;
                    } else if (s.target >= 0) {
                        Enemy t = enemies[s.target];
                        tx = t.x + enemyWidth(t.defIndex) / 2;
                        ty = t.y + enemyHeight(t.defIndex) / 2;
                    } else { tx = ox + facingX * 80; ty = oy; }
                } else {
                    // facingAngle is atan2 without the call: CLDC 1.1 has no Math.atan2.
                    double base = facingAngle(facingX, facingY);
                    double spread = count == 1 ? 0.0 : (i - (count - 1) / 2.0) * 0.16;
                    tx = ox + (int) (Math.cos(base + spread) * 100.0);
                    ty = oy + (int) (Math.sin(base + spread) * 100.0);
                }
                double dx = tx - ox;
                double dy = ty - oy;
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len < 0.001) len = 1.0;
                s.dx = (int) (dx / len * def.speed);
                s.dy = (int) (dy / len * def.speed);
                s.x = ox; s.y = oy;
            }
        }
    }

    private void createMelee(ContentCatalog.WeaponDef def, int damage, boolean crit) {
        for (int i = 0; i < MELEE_POOL; i++) {
            Melee m = melee[i];
            if (m.active) continue;
            m.active = true;
            m.until = System.currentTimeMillis() + 160L;
            m.x = px; m.y = py;
            m.range = def.range; m.area = def.area;
            m.damage = damage; m.critical = crit; m.knockback = def.knockback;
            m.weaponIndex = def.id.hashCode();
            m.hitMask = 0L; m.bossHit = false;
            m.animFrame = 0;
            m.animTimer = System.currentTimeMillis();
            m.animDir = facingX >= 0 ? 1 : -1;
            return;
        }
    }

    private void updateMelee(long now) {
        for (int i = 0; i < melee.length; i++) {
            Melee m = melee[i];
            if (!m.active) continue;
            if (now >= m.until) { m.active = false; continue; }
            // The hitbox is the animation frame itself, anchored exactly where drawMelee puts it.
            // It used to be a square of side m.area centred on the player while the art was drawn
            // ahead of the player, so the effect visibly connected with an enemy that the box did
            // not cover, and missed ones it appeared to cover. Same anchoring helpers as the draw
            // path, so the two cannot drift apart.
            int cx = px + 12;
            int cy = py + 12;
            int boxW = m.area;
            int boxH = m.area;
            Image frame = meleeFrameBox(m, now);
            if (frame != null) {
                boxW = frame.getWidth();
                boxH = frame.getHeight();
            }
            int mx = meleeDrawX(cx, boxW, m.animDir >= 0);
            int my = meleeDrawY(cy, boxH);
            for (int e = 0; e < enemies.length; e++) {
                Enemy enemy = enemies[e];
                if (!enemy.active || (m.hitMask & (1L << e)) != 0L) continue;
                if (overlap(mx, my, boxW, boxH, enemy.x, enemy.y, enemyWidth(enemy.defIndex), enemyHeight(enemy.defIndex))) {
                    m.hitMask |= 1L << e;
                    damageEnemy(e, enemy, m.damage, m.critical, m.knockback, px - enemy.x, py - enemy.y);
                }
            }
            if (boss.active && !m.bossHit && overlap(mx, my, boxW, boxH, boss.x, boss.y, bossWidth(), bossHeight())) {
                m.bossHit = true;
                damageBoss(boss, m.damage, m.critical, m.knockback);
            }
            // Check additional bosses
            if (activeBosses != null) {
                for (int bi = 0; bi < MAX_ACTIVE_BOSSES; bi++) {
                    Boss bb = activeBosses[bi];
                    if (bb != null && bb.active && !m.bossHit && overlap(mx, my, boxW, boxH, bb.x, bb.y, bossWidth(bb), bossHeight(bb))) {
                        m.bossHit = true;
                        damageBoss(bb, m.damage, m.critical, m.knockback);
                    }
                }
            }
        }
    }

    private void updateShots(long now) {
        for (int si = 0; si < shots.length; si++) {
            Shot s = shots[si];
            if (!s.active) continue;
            s.life--;
            s.age++;
            if (s.attached) {
                // Each orb keeps its own phase, advanced on its own, so several orbiting weapons
                // sit at different points of the circle instead of stacking on one spot. The
                // radius scales with the weapon's own reach field, so a wider orb sits wider.
                s.orbitAngle += ORBIT_SPEED;
                double r = orbitRadius(s);
                s.x = playerCenterX() + (int) (Math.cos(s.orbitAngle) * r);
                s.y = playerCenterY() + (int) (Math.sin(s.orbitAngle) * r);
            } else {
                if (s.pattern == ContentCatalog.PATTERN_HOMING && s.target == TARGET_BOSS && boss.active) {
                    // Steering uses the same box the hit test uses, so a homing shot converges on
                    // the boss's body rather than on a stale 42x42 corner of the sprite.
                    double bdx = boss.x + bossWidth() / 2 - s.x;
                    double bdy = boss.y + bossHeight() / 2 - s.y;
                    double blen = Math.sqrt(bdx * bdx + bdy * bdy);
                    if (blen > 0.001) {
                        ContentCatalog.WeaponDef bw = ContentCatalog.weapon(s.weaponIndex);
                        s.dx = (int) (bdx / blen * bw.speed);
                        s.dy = (int) (bdy / blen * bw.speed);
                    }
                } else if (s.pattern == ContentCatalog.PATTERN_HOMING && s.target >= 0 && enemies[s.target].active) {
                    Enemy t = enemies[s.target];
                    double dx = t.x - s.x;
                    double dy = t.y - s.y;
                    double len = Math.sqrt(dx * dx + dy * dy);
                    if (len > 0.001) {
                        ContentCatalog.WeaponDef w = ContentCatalog.weapon(s.weaponIndex);
                        s.dx = (int) (dx / len * w.speed);
                        s.dy = (int) (dy / len * w.speed);
                    }
                }
                s.x += s.dx; s.y += s.dy;
                if (s.pattern == ContentCatalog.PATTERN_BOOMERANG && s.age > s.life / 2) {
                    s.dx = playerCenterX() - s.x;
                    s.dy = playerCenterY() - s.y;
                    double len = Math.sqrt((double) (s.dx * s.dx + s.dy * s.dy));
                    if (len > 0.001) { s.dx = (int) (s.dx / len * 6.0); s.dy = (int) (s.dy / len * 6.0); }
                    s.returning = true;
                }
            }
            if (s.life <= 0 || s.x < -30 || s.y < -30 || s.x > worldWidth + 30 || s.y > worldHeight + 30) {
                if (s.pattern == ContentCatalog.PATTERN_AOE) explode(s.x, s.y, s.area, s.damage, s.critical);
                s.active = false;
                continue;
            }
            if (s.fromPlayer) {
                boolean remove = false;
                // No break out of this loop. A shot that is inside two enemies at once used to
                // damage only the first and stop, which read as the weapon hitting "one enemy in
                // turn" no matter how much of it was covered. hitMask already stops the same
                // enemy being hit twice, so walking the whole pool lets one shot clear a crowd it
                // is genuinely overlapping -- and the shot is still removed afterwards unless it
                // is a piercing or boomerang pattern.
                //
                // Orbiting weapons are the exception that matters: they are attached to the player
                // and exist to keep sweeping, so they must survive contact. Without this they were
                // consumed by the first enemy they touched and the slot sat empty until the next
                // volley.
                for (int e = 0; e < enemies.length; e++) {
                    Enemy enemy = enemies[e];
                    if (!enemy.active || (s.hitMask & (1L << e)) != 0L) continue;
                    if (overlap(s.x, s.y, shotWidth(s), shotHeight(s), enemy.x, enemy.y, enemyWidth(enemy.defIndex), enemyHeight(enemy.defIndex))) {
                        s.hitMask |= 1L << e;
                        damageEnemy(e, enemy, s.damage, s.critical, s.knockback, s.dx, s.dy);
                        if (s.pattern == ContentCatalog.PATTERN_AOE) {
                            explode(s.x, s.y, s.area, 0, false);
                            remove = true;
                        } else if (s.attached) {
                            // stays alive and keeps sweeping
                        } else if (s.pattern != ContentCatalog.PATTERN_PIERCING
                                && s.pattern != ContentCatalog.PATTERN_BOOMERANG) {
                            remove = true;
                        }
                    }
                }
                if (!remove && boss.active && !s.bossHit
                        && overlap(s.x, s.y, shotWidth(s), shotHeight(s), boss.x, boss.y, bossWidth(), bossHeight())) {
                    s.bossHit = true;
                    damageBoss(boss, s.damage, s.critical, s.knockback);
                    if (!s.attached && s.pattern != ContentCatalog.PATTERN_PIERCING) remove = true;
                }
                // Check additional bosses
                if (activeBosses != null) {
                    for (int bi = 0; bi < MAX_ACTIVE_BOSSES; bi++) {
                        Boss bb = activeBosses[bi];
                        if (bb != null && bb.active && !s.bossHit
                                && overlap(s.x, s.y, shotWidth(s), shotHeight(s), bb.x, bb.y, bossWidth(bb), bossHeight(bb))) {
                            s.bossHit = true;
                            damageBoss(bb, s.damage, s.critical, s.knockback);
                            if (!s.attached && s.pattern != ContentCatalog.PATTERN_PIERCING) remove = true;
                        }
                    }
                }
                if (remove) {
                    if (s.pattern == ContentCatalog.PATTERN_AOE) explode(s.x, s.y, s.area, s.damage, s.critical);
                    s.active = false;
                }
            } else if (overlap(s.x, s.y, shotWidth(s), shotHeight(s), px, py,
                    PLAYER_HIT_W, PLAYER_HIT_H)) {
                if (!playerInvulnerable(now)) damagePlayer(s.damage, now);
                s.active = false;
            }
        }
    }

    private void explode(int x, int y, int radius, int damage, boolean critical) {
        if (damage > 0) {
            for (int e = 0; e < enemies.length; e++) {
                Enemy enemy = enemies[e];
                if (!enemy.active) continue;
                int cx = enemy.x + enemyWidth(enemy.defIndex) / 2;
                int cy = enemy.y + enemyHeight(enemy.defIndex) / 2;
                int dx = cx - x; int dy = cy - y;
                if (dx * dx + dy * dy <= radius * radius) damageEnemy(e, enemy, damage, critical, 4, x - cx, y - cy);
            }
        }
        for (int i = 0; i < SHOT_POOL; i++) {
            Shot other = shots[i];
            if (other.active && other.pattern == ContentCatalog.PATTERN_AOE && !other.fromPlayer) {
                int dx = other.x - x; int dy = other.y - y;
                if (dx * dx + dy * dy <= radius * radius) other.active = false;
            }
        }
    }

    private void updatePickups(long now) {
        // The aura and the collection radius are deliberately different: the aura starts the
        // pull from a distance, and only the touch radius ends it. When both were 22 px a
        // pickup was collected on the tick it entered the aura, so the pull never showed and
        // pickups behaved as if they had to be walked into — the reported symptom.
        int radius = MAGNET_BASE_RADIUS + (MAGNET_BASE_RADIUS * pickupPercentBonus) / 100;
        boolean vacuuming = now < vacuumUntil;
        for (int i = 0; i < pickups.length; i++) {
            Pickup p = pickups[i];
            if (!p.active) continue;
            if (now - p.born > 30000L) { p.active = false; continue; }
            int dx = playerCenterX() - (p.x + 6);
            int dy = playerCenterY() - (p.y + 6);
            int distSq = dx * dx + dy * dy;
            // The EXP Magnet (type 3) is drawn to the player from anywhere on the field, as is
            // every pickup while a vacuum is running; the rest wait for the aura. Pull speed is
            // per-axis and can overshoot the center by at most the step, which is still inside
            // the touch radius below, so nothing oscillates around the player.
            int step = MAGNET_PULL_SPEED;
            if (p.type == 3 || vacuuming) step = MAGNET_PULL_FAST;
            if (p.type == 3 || vacuuming || distSq <= radius * radius) {
                if (dx > 0) p.x += step;
                if (dx < 0) p.x -= step;
                if (dy > 0) p.y += step;
                if (dy < 0) p.y -= step;
                // Re-measure after the pull so a pickup hauled in this tick is collected now,
                // not one tick later.
                dx = playerCenterX() - (p.x + 6);
                dy = playerCenterY() - (p.y + 6);
                distSq = dx * dx + dy * dy;
            }
            if (p.type == 3 && distSq < 900) collectPickup(p, now);
            else if (distSq <= PICKUP_TOUCH_RADIUS * PICKUP_TOUCH_RADIUS) collectPickup(p, now);
        }
    }

    private void collectPickup(Pickup p, long now) {
        p.active = false;
        if (p.type == 0) {
            int gained = p.value * (100 + expPercentBonus) / 100;
            if (gained < 1) gained = 1;
            experience += gained;
            if (p.value >= 200) { /* documented red tier retained */ }
            while (experience >= expNeed && state == STATE_RUNNING) {
                experience -= expNeed;
                playerLevel++;
                expNeed = requiredForLevel(playerLevel);
                suspendedAt = System.currentTimeMillis();
                state = STATE_LEVEL_UP;
                generateOffers();
                selectedOffer = 0;
            }
        } else if (p.type == 1) {
            coins += p.value;
            // Only a collected pickup becomes permanent. The run counter above stays run-local and
            // is what the HUD and the results screen show; this is the balance that survives the
            // run. The credit lands in memory at once while its write is debounced (see
            // SaveStore.COIN_WRITE_INTERVAL_MS): a burst of pickups costs one write per window
            // instead of one flash write per coin, a refusing store neither drops the coin nor
            // stalls the run, and pause/exit flush the rest. p.active was cleared on the first
            // line of this method, so one pickup can never be committed twice.
            if (save != null) save.creditCoins(p.value);
        } else if (p.type == 2) {
            playerHp = Math.min(playerMaxHp, playerHp + Math.max(5, playerMaxHp / 5));
        } else if (p.type == 3) {
            // Documented EXP Magnet ("all EXP and HoloCoins in large area vacuumed"). The
            // branch did not exist, so the pickup was drawn in and then silently discarded.
            // The vacuum runs for VACUUM_DURATION_MS; updatePickups pulls every pickup fast
            // for that window, and they collect on the touch radius as they arrive.
            vacuumUntil = now + VACUUM_DURATION_MS;
        } else if (p.type == 4) {
            for (int i = 0; i < weaponCount; i++) {
                ContentCatalog.WeaponDef def = ContentCatalog.weapon(weaponSlots[i]);
                if (weaponLevels[i] < def.maxLevel) {
                    weaponLevels[i]++;
                    break;
                }
            }
        } else if (p.type == 5) {
            // SPECIAL orb: only refills in IDLE state (not during active drain or cooldown).
            if (specialState == SPECIAL_STATE_IDLE) {
                specialCharge = Math.min(SPECIAL_CHARGE_MAX, specialCharge + p.value);
            }
        }
    }

    private void updateRegen(long now) {
        if (now >= regenTickAt) {
            // One 5-second cadence for both regen sources — the wiki's Regeneration rate of
            // 1 HP per level per 5 seconds, with a regen item keeping its flat +2 on the same
            // tick. Nothing heals at full HP, so a tick that finds no damage is still cheap.
            regenTickAt = now + REGEN_TICK_MS;
            int heal = regenHealPerTick;
            for (int i = 0; i < itemCount; i++) {
                ContentCatalog.ItemDef d = ContentCatalog.item(itemSlots[i]);
                if (d != null && d.effect == ContentCatalog.EFFECT_REGEN) { heal += 2; break; }
            }
            if (heal > 0 && playerHp < playerMaxHp) playerHp = Math.min(playerMaxHp, playerHp + heal);
        }
        if (shieldReadyAt > 0 && now >= shieldReadyAt) { shield = 15; shieldReadyAt = now + 15000L; }
    }

    private void damagePlayer(int amount, long now) {
        if (state != STATE_RUNNING || playerInvulnerable(now)) return;
        boolean negated = false;
        for (int i = 0; i < itemCount; i++) {
            ContentCatalog.ItemDef d = ContentCatalog.item(itemSlots[i]);
            if (d != null && d.effect == ContentCatalog.EFFECT_NEGATE && rand(100) < d.value) { negated = true; break; }
        }
        if (negated) { shield = Math.max(shield, 1); return; }
        int dealt = Math.max(1, amount * (100 - defensePercent) / 100);
        if (shield > 0) {
            int absorbed = Math.min(shield, dealt);
            shield -= absorbed; dealt -= absorbed;
        }
        playerHp -= dealt;
        invulUntil = now + 800L;
    }

    private boolean playerInvulnerable(long now) { return now < invulUntil; }

    private void damageEnemy(int poolIndex, Enemy enemy, int damage, boolean crit, int knockback, int dirX, int dirY) {
        enemy.hp -= damage;
        applyKnockback(enemy, dirX, dirY, knockback);
        if (enemy.hp > 0) return;
        enemy.active = false;
        kills++;
        defeatCount++;
        ContentCatalog.EnemyDef def = ContentCatalog.ENEMIES[enemy.defIndex];
        long now = System.currentTimeMillis();
        dropPickup(enemy.x + enemyWidth(enemy.defIndex) / 2, enemy.y + enemyHeight(enemy.defIndex) / 2, 0, def.exp, now);
        if (def.coinChance > 0) {
            // The drop roll is the enemy's own 1-in-N and the shop never touches it, so a level of
            // Money Gain Up cannot mint extra rolls: it multiplies the coin VALUE instead
            // (+20% per level, wiki cap +200%, so a 10-coin drop lands 10..30). At zero bonus the
            // value is unchanged, which keeps a no-purchase run's coin curve exactly as it was.
            if (rand(def.coinChance) == 0) {
                dropPickup(enemy.x, enemy.y, 1, 10 + (10 * coinDropBonus) / 100, now);
            }
        }
        // Food (heal pickup): base 1-in-200 per kill, scaled by Food Drops Up as a rate bonus
        // (+4%/level, cap +20% → divisor 166), so buying the row makes food land more often.
        if (rand(Math.max(40, 200 * 100 / (100 + foodDropBonus))) == 0) dropPickup(enemy.x, enemy.y, 2, 1, now);
        if (rand(1301) == 0) dropPickup(enemy.x, enemy.y, 4, 1, now);
        if (rand(3000) == 0) dropPickup(enemy.x, enemy.y, 3, 0, now);
        // SPECIAL orb: the only charge source for the special meter. 1-in-50 per kill banks 25 of
        // the 100-point meter, so roughly 200 kills buy one special on drops alone; the roll sits
        // between food (1-in-40..200) and the rare utility drops, and like every roll here it is
        // independent of the shop. Charge banks even while Special Attack is still locked.
        if (rand(50) == -1) dropPickup(enemy.x, enemy.y, 5, SPECIAL_ORB_VALUE, now); // Disabled: special orbs no longer drop
        if (now < lifestealUntil) playerHp = Math.min(playerMaxHp, playerHp + 1);
    }

    private void damageBoss(int damage, boolean critical, int knockback) {
        damageBoss(boss, damage, critical, knockback);
    }

    private void damageBoss(Boss bb, int damage, boolean critical, int knockback) {
        bb.hp -= damage;
        if (bb.hp <= 0) defeatBoss(bb, bb == boss ? bossEncounterIndex : -1);
    }

    private void applyKnockback(Enemy enemy, int dx, int dy, int strength) {
        if (strength <= 0) return;
        double len = Math.sqrt((double) (dx * dx + dy * dy));
        if (len < 0.001) len = 1.0;
        double resist = 1.0 - ((double) enemy.knockResist / 100.0);
        double force = strength * resist * 0.7;
        enemy.kbDx = (int) (dx / len * force);
        enemy.kbDy = (int) (dy / len * force);
        enemy.kbUntil = System.currentTimeMillis() + 240L;
        enemy.knockResist = Math.min(90, enemy.knockResist + 10);
        enemy.lastKnockHit = System.currentTimeMillis();
    }

    private void applyEnemyKnockback(Enemy e, long now) {
        if (now < e.kbUntil) { e.x += e.kbDx; e.y += e.kbDy; }
    }

    private void healNearbyEnemies(int radius, int amount) {
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i];
            if (!e.active) continue;
            int dx = e.x - px; int dy = e.y - py;
            if (dx * dx + dy * dy <= radius * radius) e.hp = Math.min(e.maxHp, e.hp + amount);
        }
    }

    private void damageEnemiesInRadius(int radius, int damage, boolean knockback) {
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i];
            if (!e.active) continue;
            int dx = e.x - px; int dy = e.y - py;
            if (dx * dx + dy * dy <= radius * radius) damageEnemy(i, e, damage, false, knockback ? 5 : 0, -dx, -dy);
        }
        if (boss.active) {
            int dx = boss.x - px; int dy = boss.y - py;
            if (dx * dx + dy * dy <= radius * radius) damageBoss(damage, false, 0);
        }
    }

    private void fireEnemyShot(int x, int y, int tx, int ty, int damage, int speed) {
        int index = freeShot();
        if (index == -1) return;
        double dx = tx - x; double dy = ty - y;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001) len = 1.0;
        Shot s = shots[index];
        s.active = true; s.fromPlayer = false; s.pattern = ContentCatalog.PATTERN_PROJECTILE;
        s.weaponIndex = -1; s.damage = damage; s.critical = false; s.knockback = 0;
        s.area = 0; s.life = 120; s.age = 0; s.hitMask = 0L; s.bossHit = false;
        s.attached = false; s.returning = false; s.target = -1; s.sprite = "bullet";
        measureShotBox(s);
        s.x = x; s.y = y; s.dx = (int) (dx / len * speed); s.dy = (int) (dy / len * speed);
    }

    private void clearEnemyShots() {
        for (int i = 0; i < shots.length; i++) if (shots[i].active && !shots[i].fromPlayer) shots[i].active = false;
    }

    private void spawnWave(int count, boolean ring) {
        waveCount++;
        for (int i = 0; i < count; i++) {
            int index = freeEnemy();
            if (index == -1) return;
            String id = (i & 1) == 0 ? stage.enemyA : stage.enemyB;
            int defIndex = enemyIndex(id);
            if (defIndex == -1) defIndex = 0;
            ContentCatalog.EnemyDef def = ContentCatalog.ENEMIES[defIndex];
            Enemy e = enemies[index];
            e.active = true; e.defIndex = defIndex; e.maxHp = scaledEnemyHp(def.hp); e.hp = e.maxHp;
            e.atk = scaledEnemyAtk(def.atk); e.speed = scaledEnemySpeed(def.speed) * 1.4;
            e.exp = def.exp; e.anim = i % 3; e.nextAnim = System.currentTimeMillis() + 150L;
            e.nextAttack = System.currentTimeMillis() + 1000L; e.kbUntil = 0; e.knockResist = 0;
            e.lastKnockHit = 0; e.chargeUntil = 0; e.healAt = System.currentTimeMillis() + 2000L;
            e.orbitEndsAt = 0; e.orbitDir = 0; e.moveCarry = 0;
            if (ring) {
                double angle = Math.PI * 2.0 * i / count;
                e.x = playerCenterX() + (int) (Math.cos(angle) * 120.0);
                e.y = playerCenterY() + (int) (Math.sin(angle) * 120.0);
            } else {
                placeAtEdge(e, System.currentTimeMillis());
            }
        }
    }

    private void dropPickup(int x, int y, int type, int value, long now) {
        // Documented EXP merging: combine nearby small drops of the same tier.
        // Merge only when the incoming value fits under the 200 cap. If it does not fit, fall
        // through to the spawn loop below so the overflow is kept in its own pickup instead of
        // being discarded.
        if (type == 0) {
            for (int i = 0; i < pickups.length; i++) {
                Pickup p = pickups[i];
                if (p.active && p.type == 0 && Math.abs(p.x - x) < 8 && Math.abs(p.y - y) < 8
                        && p.value + value <= 200) {
                    p.value += value;
                    return;
                }
            }
        }
        for (int i = 0; i < pickups.length; i++) {
            Pickup p = pickups[i];
            if (p.active) continue;
            p.active = true; p.type = type; p.value = value; p.x = x - 6; p.y = y - 6; p.born = now;
            return;
        }
    }

    private void addWeapon(int weaponIndex, int level) {
        if (weaponIndex < 0) return;
        // The cap is 6 minus the Weapon Limit challenge levels: buying that row is a deliberate
        // self-imposed restriction, so it applies to the starting weapon as well.
        if (weaponCount < weaponCap()) {
            weaponSlots[weaponCount] = weaponIndex;
            weaponLevels[weaponCount] = level;
            weaponCount++;
        }
    }

    private void addItem(int itemIndex, int level) {
        if (itemIndex < 0) return;
        if (itemCount < itemCap()) {
            itemSlots[itemCount] = itemIndex;
            itemLevels[itemCount] = level;
            itemCount++;
        }
    }

    private void recomputeStats() {
        atkPercentBonus = 0; hastePercent = 0; speedPercentBonus = 0;
        growthLevel = 0;
        skillUpLevel = 0;
        pickupPercentBonus = 0; critPercentBonus = 0; expPercentBonus = 0;
        // Reduction is rebuilt from the character's own stat plus the shop's clamped contribution
        // further down, so nothing is carried over from a previous call.
        int shopDefense = 0;
        coinDropBonus = 0; foodDropBonus = 0;
        int maxHp = baseMaxHp;
        for (int i = 0; i < itemCount; i++) {
            ContentCatalog.ItemDef d = ContentCatalog.item(itemSlots[i]);
            if (d == null) continue;
            int v = d.value * itemLevels[i];
            if (d.effect == ContentCatalog.EFFECT_DAMAGE_PCT) atkPercentBonus += v;
            else if (d.effect == ContentCatalog.EFFECT_GROWTH) growthLevel += v;
            if (d.effect == ContentCatalog.EFFECT_HASTE_PCT) hastePercent += v;
            if (d.effect == ContentCatalog.EFFECT_SPEED_PCT) speedPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_MAX_HP) maxHp += v;
            if (d.effect == ContentCatalog.EFFECT_PICKUP_PCT) pickupPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_CRIT_PCT) critPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_EXP_PCT) expPercentBonus += v;
        }

        // Shop upgrades apply underneath the run's items so a purchase is never diluted by which
        // items happened to drop. Max HP is recomputed from characterBaseMaxHp rather than from
        // baseMaxHp so repeated calls cannot compound the same purchase. Rows whose effect is -1
        // (locked rows, and the flag/action rows) fall through every branch and change nothing.
        int[] levels = upgrades();
        int regenHeal = 0;
        for (int i = 0; i < levels.length; i++) {
            ContentCatalog.UpgradeDef p = ContentCatalog.upgrade(i);
            int level = levels[i];
            if (p == null || level <= 0) continue;
            int v = p.value * level;
            if (p.effect == ContentCatalog.EFFECT_MAX_HP) maxHp += characterBaseMaxHp * v / 100;
            else if (p.effect == ContentCatalog.EFFECT_DAMAGE_PCT) atkPercentBonus += v;
            else if (p.effect == ContentCatalog.EFFECT_GROWTH) growthLevel += v;
            else if (p.effect == ContentCatalog.EFFECT_SKILL_UP) skillUpLevel += v;
            else if (p.effect == ContentCatalog.EFFECT_SPEED_PCT) speedPercentBonus += v;
            else if (p.effect == ContentCatalog.EFFECT_CRIT_PCT) critPercentBonus += v;
            else if (p.effect == ContentCatalog.EFFECT_EXP_PCT) expPercentBonus += v;
            else if (p.effect == ContentCatalog.EFFECT_HASTE_PCT) hastePercent += v;
            else if (p.effect == ContentCatalog.EFFECT_DEFENSE_PCT) shopDefense += v;
            else if (p.effect == ContentCatalog.EFFECT_PICKUP_PCT) pickupPercentBonus += v;
            else if (p.effect == ContentCatalog.EFFECT_COIN_PCT) coinDropBonus += v;
            else if (p.effect == ContentCatalog.EFFECT_FOOD_DROP) foodDropBonus += v;
            else if (p.effect == ContentCatalog.EFFECT_REGEN) regenHeal += v;
        }
        regenHealPerTick = regenHeal;
        // Defense Up advertises "-3% damage diterima per level (maks 15%)", so the shop's own
        // contribution is clamped to that rather than to the character's total. Clamping the total
        // instead would silently eat into a character's built-in reduction, which is a different
        // number with a different owner.
        if (shopDefense > DEFENSE_UP_MAX_PCT) shopDefense = DEFENSE_UP_MAX_PCT;
        defensePercent = (character != null ? character.defensePercent : 0) + shopDefense;
        // Money Gain Up tops out at +200% per the wiki, and the coin VALUE is all it moves.
        if (coinDropBonus > 200) coinDropBonus = 200;
        // Hardcore pins Endless to 1 HP and is a deliberate rule, so it outranks a purchased upgrade.
        // Keyed on baseMaxHp, not characterBaseMaxHp, so it still pins exactly as it did before the
        // shop upgrades existed: a Max HP stat can raise baseMaxHp and lift the ceiling, which is
        // the historical behaviour.
        if (mode.id.equals("endless") && baseMaxHp == 1) maxHp = 1;

        playerMaxHp = maxHp;
        if (playerHp > playerMaxHp) playerHp = playerMaxHp;
    }

    private void generateOffers() {
        for (int i = 0; i < offers.length; i++) offers[i].clear();
        for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;
        // A stashed offer is re-inserted as the top row. Reserve its identity first so the
        // fresh roll cannot offer the same weapon/item a second time alongside it.
        Offer stash = heldOffer;
        heldOffer = null;
        if (stash != null && stash.type >= 0) markOfferUsed(stash);
        for (int slot = 0; slot < 4; slot++) {
            int type = chooseOfferType(slot);
            if (!fillOffer(slot, type)) {
                if (!fillOffer(slot, OFFER_WEAPON)) fillOffer(slot, OFFER_ITEM);
            }
        }
        if (stash != null && stash.type >= 0) {
            // Shift the fresh roll down one row and put the stashed offer at the top, where
            // the player can see it. The offer pushed off slot 3 is the one sacrificed, which
            // is the price of carrying one forward.
            for (int i = 3; i > 0; i--) copyOffer(offers[i], offers[i - 1]);
            copyOffer(offers[0], stash);
        }
        rebuildMetaRows();
    }

    /**
     * Rebuilds the Reroll and Hold rows from the current charge counts and clamps the cursor.
     *
     * <p>This used to be the tail of generateOffers, which meant pressing Hold re-rolled all
     * four upgrades the player was choosing between. Holding saves one offer for the *next*
     * level-up; it must not touch the one on screen, so the meta rows are rebuilt on their own.
     */
    private void rebuildMetaRows() {
        for (int i = 4; i < offers.length; i++) offers[i].clear();
        // Reroll and Tahan occupy as many of the two meta slots as the player has charges for.
        // Both start at zero now — the free 10/5 bonuses are gone — so a fresh run shows the
        // plain four upgrade offers only, and the rows appear as the shop rows are levelled.
        int slots = 4;
        if (rerolls > 0) {
            offers[slots].type = OFFER_REROLL;
            offers[slots].value = rerolls;
            slots++;
        }
        if (holds > 0) {
            offers[slots].type = OFFER_HOLD;
            slots++;
        }
        offerCount = slots;
        // Spending the last Hold charge removes the row the cursor was sitting on, so pull the
        // cursor back into the list rather than leaving it pointing at a row that is gone.
        if (selectedOffer >= offerCount) selectedOffer = offerCount - 1;
        if (selectedOffer < 0) selectedOffer = 0;
        if (selectedOffer < 4) lastUpgradeSlot = selectedOffer;
    }

    /** Copies one offer's payload into another. The offer objects are pooled, never reallocated. */
    private static void copyOffer(Offer to, Offer from) {
        to.type = from.type;
        to.value = from.value;
        to.existing = from.existing;
        to.newItem = from.newItem;
        to.collab = from.collab;
    }

    /** Reserves a stashed offer's identity so the fresh roll cannot duplicate it. */
    private void markOfferUsed(Offer o) {
        String id = null;
        if (o.type == OFFER_WEAPON && o.value >= 0 && o.value < ContentCatalog.WEAPONS.length) {
            id = ContentCatalog.WEAPONS[o.value].id;
        } else if (o.type == OFFER_ITEM && o.value >= 0 && o.value < ContentCatalog.ITEMS.length) {
            id = ContentCatalog.ITEMS[o.value].id;
        }
        if (id != null) offerUsed[id.hashCode() & 63] = true;
    }

    /**
     * Picks the offer *category* for one of the four upgrade slots.
     *
     * <p>There is no skill branch at all: the skill is permanent and never appears in the roll.
     * What used to be the slot-0 skill slot rolls normally now, which is what makes stat upgrades
     * reachable every level-up: stats take 25% of rolls in every slot, so Max HP and ATK growth
     * appear regularly instead of being starved by a guaranteed skill offer.
     */
    private int chooseOfferType(int slot) {
        int r = rand(100);
        if (r < 30) return OFFER_WEAPON;
        if (r < 55) return OFFER_ITEM;
        if (r < 80) return OFFER_STAT;
        return OFFER_WEAPON;
    }

    private boolean fillOffer(int slot, int type) {
        if (type == OFFER_STAT) {
            int[] weights = { 2, 3, 4, 3, 4, 2 };
            int total = 0;
            for (int i = 0; i < weights.length; i++) total += weights[i];
            int pick = rand(total);
            for (int i = 0; i < weights.length; i++) {
                pick -= weights[i];
                if (pick < 0) { offers[slot].type = OFFER_STAT; offers[slot].value = i; offers[slot].existing = -1; return true; }
            }
        }
        if (type == OFFER_WEAPON) {
            // Prefer upgrades of an existing weapon half the time.
            if (weaponCount > 0 && rand(2) == 0) {
                int start = rand(weaponCount);
                for (int i = 0; i < weaponCount; i++) {
                    int s = (start + i) % weaponCount;
                    ContentCatalog.WeaponDef w = ContentCatalog.weapon(weaponSlots[s]);
                    if (weaponLevels[s] < w.maxLevel && !offerUsed[w.id.hashCode() & 63]) {
                        offers[slot].type = OFFER_WEAPON; offers[slot].value = weaponSlots[s];
                        offers[slot].existing = s; offers[slot].newItem = false;
                        offerUsed[w.id.hashCode() & 63] = true; return true;
                    }
                }
            }
            // Collab Ban removes the collab branch entirely, next to the mode's own allowance:
            // a bought ban blocks collabs even on Stage Mode, which otherwise allows them.
            if (weaponCount < weaponCap() && mode.allowsCollabs && !collabBan) {
                for (int c = 0; c < ContentCatalog.COLLABS.length; c++) {
                    ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[c];
                    ContentCatalog.WeaponDef wd = ContentCatalog.weapon(cd.id);
                    // Skip collab rows with no runtime weapon definition (e.g.
                    // documented recipes beyond the implemented catalog) instead
                    // of dereferencing null below.
                    if (wd == null) continue;
                    // A collab already owned is gone from the recipe pool: its ingredients
                    // were consumed, so its level test can never pass again and it would only
                    // clutter the roll. See hasCollab.
                    if (hasCollab(cd.id)) continue;
                    int a = weaponLevelById(cd.weaponA); int b = weaponLevelById(cd.weaponB);
                    if (a == wd.maxLevel && b == wd.maxLevel && !offerUsed[wd.id.hashCode() & 63]) {
                        offers[slot].type = OFFER_WEAPON; offers[slot].value = ContentCatalog.weaponIndex(wd.id);
                        offers[slot].existing = -1; offers[slot].newItem = true; offers[slot].collab = true;
                        offerUsed[wd.id.hashCode() & 63] = true; return true;
                    }
                }
            }
            if (weaponCount < weaponCap()) {
                int start = rand(5);
                for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
                    int w = (start + i) % ContentCatalog.WEAPONS.length;
                    ContentCatalog.WeaponDef def = ContentCatalog.WEAPONS[w];
                    // An ingredient whose only recipes are already owned is not offered again:
                    // applying it could never rebuild the collab that consumed it. See
                    // feedsOwnedCollab. This is what let BL Book and Psycho Axe keep appearing
                    // after BL Fujoshi was made.
                    if (def.category != 0 || weaponSlotById(def.id) >= 0
                            || offerUsed[def.id.hashCode() & 63] || feedsOwnedCollab(def.id)) continue;
                    offers[slot].type = OFFER_WEAPON; offers[slot].value = w; offers[slot].existing = -1;
                    offers[slot].newItem = true; offerUsed[def.id.hashCode() & 63] = true; return true;
                }
            }
        }
        if (type == OFFER_ITEM) {
            if (itemCount > 0 && rand(2) == 0) {
                int start = rand(itemCount);
                for (int i = 0; i < itemCount; i++) {
                    int s = (start + i) % itemCount;
                    ContentCatalog.ItemDef d = ContentCatalog.item(itemSlots[s]);
                    if (itemLevels[s] < d.maxLevel && !offerUsed[d.id.hashCode() & 63]) {
                        offers[slot].type = OFFER_ITEM; offers[slot].value = itemSlots[s];
                        offers[slot].existing = s; offers[slot].newItem = false;
                        offerUsed[d.id.hashCode() & 63] = true; return true;
                    }
                }
            }
            if (itemCount < itemCap()) {
                int start = rand(ContentCatalog.ITEMS.length);
                for (int i = 0; i < ContentCatalog.ITEMS.length; i++) {
                    int idx = (start + i) % ContentCatalog.ITEMS.length;
                    ContentCatalog.ItemDef d = ContentCatalog.ITEMS[idx];
                    if (itemSlotById(d.id) >= 0 || offerUsed[d.id.hashCode() & 63]) continue;
                    offers[slot].type = OFFER_ITEM; offers[slot].value = idx; offers[slot].existing = -1;
                    offers[slot].newItem = true; offerUsed[d.id.hashCode() & 63] = true; return true;
                }
            }
        }
        if (type == OFFER_WEAPON) { offers[slot].type = OFFER_COINS; offers[slot].value = 100; return true; }
        if (type == OFFER_ITEM) { offers[slot].type = OFFER_FOOD; offers[slot].value = 0; return true; }
        return false;
    }

    private void applyOffer(int index) {
        if (index < 0 || index >= 4) return;
        Offer o = offers[index];
        if (o.type == OFFER_WEAPON) {
            ContentCatalog.WeaponDef def = ContentCatalog.weapon(o.value);
            if (o.collab) {
                removeWeaponById(def.prerequisiteA);
                removeWeaponById(def.prerequisiteB);
                addWeapon(o.value, 1);
            } else if (o.existing >= 0) {
                ContentCatalog.WeaponDef current = ContentCatalog.weapon(weaponSlots[o.existing]);
                weaponLevels[o.existing] = Math.min(current.maxLevel, weaponLevels[o.existing] + 1);
            } else addWeapon(o.value, 1);
        } else if (o.type == OFFER_ITEM) {
            if (o.existing >= 0) {
                ContentCatalog.ItemDef current = ContentCatalog.item(itemSlots[o.existing]);
                itemLevels[o.existing] = Math.min(current.maxLevel, itemLevels[o.existing] + 1);
            } else addItem(o.value, 1);
            recomputeStats();
        } else if (o.type == OFFER_STAT) {
            statOfferCounts[o.value]++;
            applyStat(o.value);
            recomputeStats();
        } else if (o.type == OFFER_FOOD) {
            playerHp = Math.min(playerMaxHp, playerHp + Math.max(1, playerMaxHp / 5));
        } else if (o.type == OFFER_COINS) {
            coins += 100;
        }
    }

    private void applyStat(int stat) {
        if (stat == 0) { baseMaxHp += Math.max(1, character.hp / 10); playerMaxHp += Math.max(1, character.hp / 10); }
        else if (stat == 1) atkPercentBonus += 8;
        else if (stat == 2) speedPercentBonus += 12;
        else if (stat == 3) critPercentBonus += 3;
        else if (stat == 4) pickupPercentBonus += 20;
        else if (stat == 5) hastePercent += 5;
    }

    private String statName(int stat) {
        if (stat == 0) return "Max HP +10%";
        if (stat == 1) return "ATK +8%";
        if (stat == 2) return "SPD +12%";
        if (stat == 3) return "CRT +3%";
        if (stat == 4) return "Pick Up +20%";
        return "Haste +5%";
    }

    private int requiredForLevel(int level) {
        // The Level Up page explicitly states 79 EXP for level 1 -> 2.
        if (level <= 1) return 79;
        double current = MathUtil.power(4.0 * level, 2.1);
        double next = MathUtil.power(4.0 * (level + 1), 2.1);
        int value = MathUtil.roundEven(next) - MathUtil.roundEven(current);
        return value < 1 ? 1 : value;
    }

    private int weaponLevelById(String id) {
        int s = weaponSlotById(id);
        return s < 0 ? 0 : weaponLevels[s];
    }

    private int weaponSlotById(String id) {
        for (int i = 0; i < weaponCount; i++) if (ContentCatalog.WEAPONS[weaponSlots[i]].id.equals(id)) return i;
        return -1;
    }

    private int itemSlotById(String id) {
        for (int i = 0; i < itemCount; i++) if (ContentCatalog.ITEMS[itemSlots[i]].id.equals(id)) return i;
        return -1;
    }

    private void removeWeaponById(String id) {
        int slot = weaponSlotById(id);
        if (slot < 0) return;
        for (int i = slot; i < weaponCount - 1; i++) {
            weaponSlots[i] = weaponSlots[i + 1]; weaponLevels[i] = weaponLevels[i + 1];
            weaponReadyAt[i] = weaponReadyAt[i + 1];
        }
        weaponCount--;
        weaponSlots[weaponCount] = -1; weaponLevels[weaponCount] = 0; weaponReadyAt[weaponCount] = 0;
    }

    /**
     * True when this collab is already equipped. Its ingredients were consumed on application, so
     * the inventory no longer contains them and the level test alone would not see it.
     */
    private boolean hasCollab(String id) {
        int slot = weaponSlotById(id);
        if (slot < 0) return false;
        return ContentCatalog.WEAPONS[weaponSlots[slot]].category == ContentCatalog.WEAPON_COLLAB;
    }

    /**
     * True when a collab the player owns has consumed this weapon.
     *
     * <p>Once BL Fujoshi is made, BL Book and Psycho Axe leave the inventory but their recipes stay
     * live in the catalog, so the fresh-pick roll kept re-offering them as picks that could never
     * rebuild the collab — the reported bug. The reference rule is absolute: "Weapons that have been
     * used in a collab cannot appear again in the rotation when leveling up or other means"
     * (HoloCure wiki, Collab). So the filter fires as soon as ANY owned collab lists the weapon,
     * even when a sibling recipe still wants it — BL Book also feeds Frozen Sea and Legendary
     * Sausage, Psycho Axe also feeds Blood Lust and Crescent Bardiche, and all of those become
     * unobtainable for the run, exactly as in the reference. A weapon no owned collab consumed is
     * never filtered — it is just a weapon.
     */
    private boolean feedsOwnedCollab(String id) {
        for (int i = 0; i < ContentCatalog.COLLABS.length; i++) {
            ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[i];
            if ((id.equals(cd.weaponA) || id.equals(cd.weaponB)) && hasCollab(cd.id)) return true;
        }
        return false;
    }

    private int freeEnemy() {
        for (int i = 0; i < enemies.length; i++) if (!enemies[i].active) return i;
        return -1;
    }

    private int activeEnemyCount() {
        int n = 0;
        for (int i = 0; i < enemies.length; i++) if (enemies[i].active) n++;
        return n;
    }

    private int freeShot() {
        for (int i = 0; i < shots.length; i++) if (!shots[i].active) return i;
        return -1;
    }

    private int nearestEnemy() {
        int best = -1; long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i];
            if (!e.active) continue;
            long dx = e.x - playerCenterX(); long dy = e.y - playerCenterY();
            long d = dx * dx + dy * dy;
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return best;
    }

    /**
     * Nearest live target for a fired shot: an enemy pool index, {@link #TARGET_BOSS} when the
     * boss is the closest thing alive, or {@link #TARGET_NONE}.
     *
     * <p>The boss used to be invisible to targeting, so during a boss fight every aimed and
     * homing weapon either tracked the adds around it or, with no adds left, flew off in the
     * player's facing direction instead of at the boss.
     */
    int nearestTarget() {
        int best = nearestEnemy();
        long bestDist = Long.MAX_VALUE;
        if (best >= 0) {
            long dx = enemies[best].x - playerCenterX();
            long dy = enemies[best].y - playerCenterY();
            bestDist = dx * dx + dy * dy;
        }
        if (boss.active) {
            long dx = boss.x + bossWidth() / 2 - playerCenterX();
            long dy = boss.y + bossHeight() / 2 - playerCenterY();
            if (dx * dx + dy * dy < bestDist) return TARGET_BOSS;
        }
        return best;
    }

    private int enemyIndex(String id) {
        for (int i = 0; i < ContentCatalog.ENEMIES.length; i++) if (ContentCatalog.ENEMIES[i].id.equals(id)) return i;
        return -1;
    }

    private int bossIndex(String id) {
        for (int i = 0; i < ContentCatalog.BOSSES.length; i++) if (ContentCatalog.BOSSES[i].id.equals(id)) return i;
        return -1;
    }

    private String hardThirdBoss(int stageIndex) {
        if (stageIndex == 5) return "halloween_bae";
        if (stageIndex == 6) return "nodoka";
        if (stageIndex == 7) return "halloween_myth";
        if (stageIndex == 8) return "goriela";
        return stageIndex >= 0 && stageIndex < ContentCatalog.BOSSES.length ? "pekodam" : "pekodam";
    }

    /**
     * Hard second 10:00 boss: the regular 20:00 boss advances to 10:00 on
     * Hard (verified on the wiki Stage 1 (Hard) page: Fubuzilla and Smol Ame
     * both at 10:00, each with its own stats -- hence the *_hard row). Only
     * Stage 1 Hard (index 5) spawns one. The Stage 2 (Hard)/3 (Hard)/4 (Hard)
     * pages, read on 2026-10-20, likewise schedule A-chan, Spiderchama and
     * Area 15 at 10:00, but spawning them is a schedule change this build
     * does not make; their stats rows are not in the catalog either. Null
     * means the director skips the slot without recording it.
     */
    private String hardSecondBoss(int stageIndex) {
        if (stageIndex == 5) return "smol_ame_hard";
        return null;
    }

    private int scaledEnemyHp(int base) {
        double value = MathUtil.power(base + base * 0.05 * scaleA, 1.0 + scaleB / 50.0);
        return MathUtil.clampInt(MathUtil.roundEven(value), 1, 100000);
    }

    /**
     * Boss HP uses the same scaling as enemy HP, under its own 400000 ceiling.
     *
     * <p>It used to be pinned by {@code min(20000, ...)}, which crushed every scaled spawn and
     * every boss above 20000 in the catalog — Halloween Bae's verified 72000 became exactly 20000,
     * so all bosses shared one durability. The ceiling then became the enemy HP cap of 100000,
     * which works only while no catalog boss exceeds it; Goriela's wiki figure of 180000 and
     * WoWo's 110000 both do, so boss HP gets a 400000 ceiling of its own and every catalog boss
     * surfaces its own value. At the normal 10:00/20:00 gates the scaling factors are zero, so
     * documented bosses keep their catalog HP exactly; the ceiling only shapes long Endless
     * repeats.
     */
    private int scaledBossHp(int base) {
        double value = MathUtil.power(base + base * 0.05 * scaleA, 1.0 + scaleB / 50.0);
        return MathUtil.clampInt(MathUtil.roundEven(value), 1, 400000);
    }

    private int scaledEnemyAtk(int base) {
        double value = MathUtil.power(base + 2.0 * scaleA, 1.0 + scaleB / 25.0);
        return MathUtil.clampInt(MathUtil.roundEven(value), 1, 1000);
    }

    private double scaledEnemySpeed(double base) {
        double value = MathUtil.power(base + 0.12 * scaleA, 1.0 + scaleB / 25.0);
        return MathUtil.clamp(value, 0.1, 3.0);
    }

    private int enemyWidth(int index) { return ContentCatalog.ENEMIES[index].collW; }
    private int enemyHeight(int index) { return ContentCatalog.ENEMIES[index].collH; }
    /**
     * Measures a shot's hitbox from the sprite it will be drawn with, once, when it is fired.
     *
     * <p>The box used to be a hardcoded 7x7, widened to 12x12 for two sprite keys by name. That
     * made it fiction: a bomb drew far larger than the box that hurt you, and any weapon sharing a
     * sprite key silently inherited whichever size that name happened to be given. Reading the real
     * image ties the box to the picture.
     *
     * <p>Measured here rather than in the collision loop on purpose: collision runs for every live
     * shot on every tick, so a per-tick asset lookup would be both wasteful and a hard dependency on
     * the asset library, which a headless run does not have. {@code SHOT_HIT_FALLBACK} keeps a
     * partial build playable instead of making every shot intangible.
     */
    private void measureShotBox(Shot s) {
        Image im = s.fromPlayer && assets != null ? assets.projectile(s.sprite) : null;
        if (im == null && !s.fromPlayer && assets != null) im = assets.bullet;
        s.hitW = im != null ? im.getWidth() : SHOT_HIT_FALLBACK;
        s.hitH = im != null ? im.getHeight() : SHOT_HIT_FALLBACK;
    }

    private int shotWidth(Shot s) { return s.hitW > 0 ? s.hitW : SHOT_HIT_FALLBACK; }
    private int shotHeight(Shot s) { return s.hitH > 0 ? s.hitH : SHOT_HIT_FALLBACK; }

    /** Hitbox used when a shot has no sprite to measure. */
    private static final int SHOT_HIT_FALLBACK = 7;

    /**
     * The melee frame that is on screen right now, so the hitbox can be measured from it.
     *
     * <p>Returns null when the character has no melee animation loaded, which is the partial-build
     * case; the caller then falls back to the catalog's area.
     */
    private Image meleeFrameBox(Melee m, long now) {
        AssetLibrary.MeleeAnimation anim = assets.meleeAnim(characterIndex);
        if (anim == null || anim.frames == null || anim.frames.length == 0) return null;
        if (anim.frameDuration <= 0) return anim.frames[0];
        int frameIndex = (int) ((now - m.animTimer) / anim.frameDuration);
        if (frameIndex >= anim.frames.length) frameIndex = anim.frames.length - 1;
        if (frameIndex < 0) frameIndex = 0;
        Image[] set = (m.animDir < 0 && anim.framesM != null) ? anim.framesM : anim.frames;
        if (frameIndex >= set.length) return set.length > 0 ? set[0] : null;
        return set[frameIndex];
    }
    /** Radians per tick an orbiting weapon sweeps. */
    private static final double ORBIT_SPEED = 0.18;
    /** Closest an orb may sit to the player, so a small sprite cannot orbit inside the body. */
    private static final int ORBIT_MIN_RADIUS = 22;
    /** Furthest an orb may sit, so a long-reach weapon does not sweep the whole screen. */
    private static final int ORBIT_MAX_RADIUS = 64;

    /**
     * Orbit radius for one shot, taken from its weapon's reach.
     *
     * <p>Previously every orb sat at a fixed 30px whatever weapon it belonged to, which meant the
     * hitbox of each orb was unrelated to the weapon that produced it. Reading it off the weapon
     * keeps an orb where its own art suggests it should be, and clamping means a small reach cannot
     * put the sprite inside the player and a large one cannot sweep the entire screen.
     */
    private int orbitRadius(Shot s) {
        ContentCatalog.WeaponDef def = ContentCatalog.weapon(s.weaponIndex);
        if (def == null) return ORBIT_MIN_RADIUS;
        int half = def.range / 2;
        if (half < ORBIT_MIN_RADIUS) half = ORBIT_MIN_RADIUS;
        if (half > ORBIT_MAX_RADIUS) half = ORBIT_MAX_RADIUS;
        return half;
    }
    private int playerCenterX() { return px + 12; }
    private int playerCenterY() { return py + 12; }
    private int rand(int n) { return n <= 1 ? 0 : (random.nextInt() & 0x7fffffff) % n; }

    private int step(double logicalPixelsPerTick) {
        int value = (int) (logicalPixelsPerTick * 0.04);
        return value < 1 ? 1 : value;
    }

    private boolean overlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    /**
     * Facing convention, shared by character and effect rendering: only
     * strictly-negative horizontal facing counts as left, matching every
     * existing {@code facingX >= 0} use. Package-visible for tests.
     */
    static boolean facingLeft(int facingX) {
        return facingX < 0;
    }

    /**
     * Picks the mirrored frame set when facing left, falling back to the
     * normal set when no mirror is available. Visual only.
     */
    static Image[] facingFrames(Image[] normal, Image[] mirrored, int facingX) {
        if (facingLeft(facingX) && mirrored != null) return mirrored;
        return normal;
    }

    /**
     * The angle of the facing direction in radians, for aiming a volley when
     * no target can be locked onto. This is {@code Math.atan2(facingY,
     * facingX)}, which CLDC 1.1's {@link Math} does not have (its trig set is
     * sin/cos/tan and the inverse-free helpers), and facing only ever holds
     * -1, 0 or 1 -- so the eight reachable angles are spelled out instead of
     * computed, each an exact multiple of PI/4. (0,0) aims right, matching
     * the atan2(0,0) result the old code special-cased to. Package-visible
     * for tests.
     */
    static double facingAngle(int facingX, int facingY) {
        if (facingY == 0) return facingX < 0 ? Math.PI : 0.0;
        if (facingX == 0) return facingY > 0 ? Math.PI / 2.0 : -Math.PI / 2.0;
        double diagonal = Math.PI / 4.0;
        if (facingX > 0) return facingY > 0 ? diagonal : -diagonal;
        return facingY > 0 ? Math.PI - diagonal : -Math.PI + diagonal;
    }

    /**
     * Horizontal direction from one point toward another, for visuals that
     * track a target (enemies face the player). Ties go right, matching the
     * {@code facingX >= 0} convention everywhere else. Visual only.
     */
    static int faceToward(int fromX, int toX) {
        return toX < fromX ? -1 : 1;
    }

    /**
     * Visual-only melee effect anchors. The effect's leading edge starts at
     * the player center and extends toward the facing direction; the left
     * side uses the load-time mirrored frame. Package-visible
     * for deterministic visual-contract tests; hitbox code does not use these.
     */
    static int meleeDrawX(int cx, int frameW, boolean facingRight) {
        return facingRight ? cx : cx - frameW;
    }

    static int meleeDrawY(int cy, int frameH) {
        return cy - frameH / 2;
    }

    /**
     * Visual-only attack lunge in pixels: while a melee effect is active the
     * whole character (whose frames already hold the weapon) shifts a few
     * pixels toward the facing direction, so the held weapon moves with the
     * swing instead of standing static under the effect. Positive means
     * toward the right; the caller negates for left. The lunge is cosmetic:
     * the melee hitbox is the animation frame, anchored by the same helpers
     * the draw path uses.
     */
    static int playerLungePx(boolean meleeActive) {
        return meleeActive ? 3 : 0;
    }

    private boolean anyMeleeActive() {
        for (int i = 0; i < melee.length; i++) if (melee[i].active) return true;
        return false;
    }

    private void clampEnemy(Enemy e, int w, int h) {
        if (e.x < -w) e.x = -w; if (e.y < -h) e.y = -h;
        if (e.x > worldWidth) e.x = worldWidth; if (e.y > worldHeight) e.y = worldHeight;
    }

    /**
     * Outline three fixed enemy ids in red while drawing, left behind by the 2026-10-19
     * contact-damage debugging and hidden behind this flag on 2026-10-20. It was never a
     * general hitbox overlay — it covered only defIndex 3, 4 and 11 — and it drew on real
     * devices too. Flip to true only while inspecting those enemies; never ship it enabled.
     */
    private static final boolean DEBUG_ENEMY_OUTLINE = false;

    void draw(Graphics g, int offX, int offY, long now) {
        // offX/offY are screen-space viewport offsets (Viewport.offsetX/offsetY). Only the world
        // layers shift by the camera; the HUD below keeps the untouched screen offsets.
        int cameraX = cameraX();
        int worldOffX = offX - cameraX;
        drawBackground(g, worldOffX, offY, cameraX);
        for (int i = 0; i < pickups.length; i++) {
            Pickup p = pickups[i];
            if (!p.active) continue;
            int x = worldOffX + p.x; int y = offY + p.y;
            if (p.type == 0) {
                Image frame = frame(assets.expFrames, (int) ((now - p.born) / 80L));
                if (frame != null) g.drawImage(frame, x, y, Graphics.TOP | Graphics.LEFT);
                else { g.setColor(200, 220, 40); g.fillRect(x, y, 12, 13); }
            } else if (p.type == 1) {
                if (assets.coin != null) g.drawImage(assets.coin, x, y - 3, Graphics.TOP | Graphics.LEFT);
                else { g.setColor(255, 200, 0); g.fillRect(x, y, 10, 10); }
            } else if (p.type == 2) {
                // Ground food: Okayu's onigiri from the unpacked game (spr_OkayuOnigiri_0,
                // 16x14) centred over the 12px pickup box; the red square is the fallback when
                // the art failed to load.
                if (assets.food != null) g.drawImage(assets.food, x - 2, y - 1, Graphics.TOP | Graphics.LEFT);
                else { g.setColor(255, 100, 100); g.fillRect(x + 3, y + 2, 8, 8); }
            } else if (p.type == 3) {
                g.setColor(255, 255, 255); g.fillRect(x + 3, y + 3, 6, 6);
            } else if (p.type == 5) {
                // SPECIAL orb: violet to match the special bar. No dedicated sprite exists in
                // the extraction, so it is drawn plainly like the other utility pickups; art can
                // replace it the same way food uses Okayu's onigiri.
                g.setColor(170, 90, 255); g.fillRect(x + 1, y + 1, 10, 10);
                g.setColor(240, 220, 255); g.fillRect(x + 4, y + 4, 4, 4);
            } else {
                g.setColor(180, 180, 200); g.fillRect(x + 1, y + 4, 10, 6);
            }
        }
        for (int i = 0; i < shots.length; i++) {
            Shot s = shots[i];
            if (!s.active) continue;
            Image im = s.fromPlayer ? assets.projectile(s.sprite) : assets.bullet;
            int x = worldOffX + s.x; int y = offY + s.y;
            if (im != null) g.drawImage(im, x, y, Graphics.TOP | Graphics.LEFT);
            else { g.setColor(s.fromPlayer ? 255 : 255, 255, s.fromPlayer ? 255 : 120); g.fillRect(x, y, 7, 7); }
        }
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i];
            if (!e.active) continue;
            Image[] frames = facingFrames(assets.enemyFrames(e.defIndex),
                assets.enemyFramesM(e.defIndex),
                faceToward(e.x + enemyWidth(e.defIndex) / 2, playerCenterX()));
            Image im = frame(frames, e.anim);
            int x = worldOffX + e.x; int y = offY + e.y;
            if (im != null) g.drawImage(im, x, y, Graphics.TOP | Graphics.LEFT);
            else { g.setColor(180, 0, 0); g.fillRect(x, y, enemyWidth(e.defIndex), enemyHeight(e.defIndex)); }
            if (DEBUG_ENEMY_OUTLINE && (e.defIndex == 3 || e.defIndex == 4 || e.defIndex == 11)) {
                g.setColor(255, 100, 100); g.drawRect(x - 3, y - 3, enemyWidth(e.defIndex) + 6, enemyHeight(e.defIndex) + 6);
            }
        }
        // Draw all active bosses
        if (boss.active) {
            drawBossSprite(g, boss, worldOffX, offY);
        }
        for (int i = 0; i < MAX_ACTIVE_BOSSES; i++) {
            Boss bb = activeBosses[i];
            if (bb != null && bb.active) {
                drawBossSprite(g, bb, worldOffX, offY);
            }
        }
        drawPlayer(g, worldOffX, offY, now);
        drawMelee(g, worldOffX, offY, now);
        drawEffects(g, offX, offY);
        drawHud(g, offX, offY, now);
        drawDialogue(g, offX, offY, now);
    }

    /** Draws a single boss sprite. */
    private void drawBossSprite(Graphics g, Boss bb, int worldOffX, int offY) {
        Image[] frames = facingFrames(assets.bossFrames(bb.defIndex),
                assets.bossFramesM(bb.defIndex),
                faceToward(bb.x + bossWidth(bb) / 2, playerCenterX()));
        Image im = frame(frames, bb.anim);
        int x = worldOffX + bb.x; int y = offY + bb.y;
        if (im != null) {
            // Phase transition flash effect
            if (bb.isPhaseTransition && bb.phaseTransitionUntil > System.currentTimeMillis()) {
                // Draw with white tint overlay
                g.drawImage(im, x, y, Graphics.TOP | Graphics.LEFT);
                g.setColor(255, 255, 255);
                g.fillRect(x, y, im.getWidth(), im.getHeight());
            } else {
                g.drawImage(im, x, y, Graphics.TOP | Graphics.LEFT);
            }
        } else {
            g.setColor(140, 0, 0);
            g.fillRect(x, y, bossWidth(bb), bossHeight(bb));
        }
    }

    /**
     * Paints the palette base over exactly the visible playfield, then repeats the stage floor
     * tile across only the columns the camera can see. The first column is snapped down to a tile
     * boundary so a partial tile still lands on its own boundary and the repetition stays seamless;
     * because cameraX is clamped to worldWidth - viewWidth, the visible range can never run past
     * the world, so no tile is ever drawn outside it. Vertical extent is unchanged: rows are still
     * laid over the playfield height, so there is no vertical scrolling.
     */
    private void drawBackground(Graphics g, int offX, int offY, int cameraX) {
        int[][] colors = { { 22, 48, 30 }, { 42, 44, 54 }, { 46, 28, 34 }, { 58, 42, 30 },
                { 28, 52, 62 }, { 16, 24, 40 }, { 54, 34, 40 }, { 42, 20, 30 },
                { 18, 18, 34 }, { 36, 20, 52 }, { 34, 24, 17 } };
        int c = stage.palette % colors.length;
        g.setColor(colors[c][0], colors[c][1], colors[c][2]);
        g.fillRect(offX + cameraX, offY, viewWidth, viewHeight);

        // A composed arena draws its parts once each, at their grid positions. The reference scene
        // is a single picture with a burning border around a clearing, so repeating one floor tile
        // would destroy it; a part grid reproduces the composition exactly.
        Image[][] parts = assets.stageMap();
        if (parts != null) {
            for (int row = 0; row < parts.length; row++) {
                for (int col = 0; col < parts[row].length; col++) {
                    Image part = parts[row][col];
                    if (part == null) continue;
                    int pw = part.getWidth(); int ph = part.getHeight();
                    int dx = col * pw - cameraX;
                    // Skip anything scrolled fully out of view; the camera is clamped, so the
                    // visible range can never run past the world.
                    if (dx >= viewWidth || dx + pw <= 0) continue;
                    g.drawImage(part, offX + dx, offY + row * ph, Graphics.TOP | Graphics.LEFT);
                }
            }
            return;
        }

        Image tile = assets.tile(stage.tile);
        if (tile != null) {
            int tw = tile.getWidth(); int th = tile.getHeight();
            int firstX = cameraX - (cameraX % tw);
            int lastX = cameraX + viewWidth;
            for (int y = 0; y < viewHeight; y += th)
                for (int x = firstX; x < lastX; x += tw) g.drawImage(tile, offX + x, offY + y, Graphics.TOP | Graphics.LEFT);
        }
    }

    private void drawPlayer(Graphics g, int offX, int offY, long now) {
        AssetLibrary.CharacterAssets ca = assets.character(characterIndex);
        Image[] idle = facingFrames(ca.idle, ca.idleM, facingX);
        Image[] run = facingFrames(ca.run, ca.runM, facingX);
        Image[] frames = moving ? run : idle;
        Image im = frame(frames, (int) (now / (moving ? 90L : 150L)));
        // Attack lunge: the held weapon lives inside the character frames, so
        // the body shifts with the swing while a melee effect is active.
        int lunge = playerLungePx(anyMeleeActive());
        int x = offX + px + (facingX >= 0 ? lunge : -lunge);
        int y = offY + py;
        boolean blink = now < invulUntil && ((now / 100L) % 2L == 0L);
        if (!blink) {
            if (im != null) g.drawImage(im, x, y, Graphics.TOP | Graphics.LEFT);
            else { g.setColor(30, 160, 60); g.fillRect(x, y, 24, 24); }
        }
        // No floating overhead weapon: character frames already hold their
        // weapon in hand, and the melee effect animation depicts the swing.
        // (An earlier revision drew ca.weapon above the head; removed.)
    }

    private void drawMelee(Graphics g, int offX, int offY, long now) {
        for (int i = 0; i < melee.length; i++) {
            Melee m = melee[i];
            if (!m.active) continue;
            // Get the melee animation for this character
            AssetLibrary.MeleeAnimation anim = assets.meleeAnim(characterIndex);
            if (anim == null || anim.frames == null || anim.frames.length == 0) continue;

            // Update animation frame
            long elapsed = now - m.animTimer;
            int frameIndex = (int) (elapsed / anim.frameDuration);
            if (frameIndex >= anim.frames.length) {
                frameIndex = anim.frames.length - 1;
            }
            m.animFrame = frameIndex;

            Image[] set = (m.animDir < 0 && anim.framesM != null) ? anim.framesM : anim.frames;
            Image frame = frameIndex < set.length ? set[frameIndex] : null;
            if (frame == null) continue;

            // Front-anchored to the facing direction: the effect extends ahead
            // of the character instead of straddling the center, and the left
            // side mirrors the right side. MIDP 2.0 cannot mirror images, so
            // the same frame is anchored to the correct edge. updateMelee measures the hitbox
            // from this very frame through meleeDrawX/meleeDrawY, so picture and box agree.
            int cx = offX + px + 12;
            int cy = offY + py + 12;
            int frameW = frame.getWidth();
            int frameH = frame.getHeight();
            int mx = meleeDrawX(cx, frameW, m.animDir >= 0);
            int my = meleeDrawY(cy, frameH);

            g.drawImage(frame, mx, my, Graphics.TOP | Graphics.LEFT);
        }
    }

    private void drawHud(Graphics g, int offX, int offY, long now) {
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(small);
        g.setColor(255, 255, 255); // gameplay HUD text is white, as requested

        // HP line: "HP X/Y"
        if (cachedHpString == null || playerHp != lastPlayerHp || playerMaxHp != lastPlayerMaxHp) {
            cachedHpString = "HP " + playerHp + "/" + playerMaxHp;
            lastPlayerHp = playerHp;
            lastPlayerMaxHp = playerMaxHp;
        }
        g.drawString(cachedHpString, offX + 4, offY + 4, Graphics.TOP | Graphics.LEFT);

        // Player HP bar - use HP bar assets (green/purple) with HP icon
        double maxHpRatio = (double) playerMaxHp;
        double hpRatio = maxHpRatio > 0.0 ? MathUtil.clamp((double) playerHp / maxHpRatio, 0.0, 1.0) : 0.0;
        
        // Draw HP bar background
        Image hpBg = (hpRatio * 100.0 <= HP_BAR_LOW_PERCENT) ? assets.hpPurple() : assets.hpGreen();
        if (hpBg != null) {
            g.drawImage(hpBg, offX + HP_BAR_X, offY + HP_BAR_Y, Graphics.TOP | Graphics.LEFT);
        } else {
            // Fallback to rect drawing
            g.setColor(20, 20, 20); g.fillRect(offX + HP_BAR_X, offY + HP_BAR_Y, HP_BAR_W, HP_BAR_H);
            g.setColor(90, 30, 30);
            g.fillRect(offX + HP_BAR_X + HP_BAR_INSET, offY + HP_BAR_Y + HP_BAR_INSET,
                    HP_BAR_W - 2 * HP_BAR_INSET, HP_BAR_H - 2 * HP_BAR_INSET);
            if (hpRatio * 100.0 <= HP_BAR_LOW_PERCENT) g.setColor(220, 40, 40);
            else g.setColor(40, 190, 60);
            int hpFillW = (int) ((HP_BAR_W - 2 * HP_BAR_INSET) * hpRatio);
            g.fillRect(offX + HP_BAR_X + HP_BAR_INSET, offY + HP_BAR_Y + HP_BAR_INSET, hpFillW, HP_BAR_H - 2 * HP_BAR_INSET);
        }
        
        // HP icon
        Image hpIcon = assets.hpIcon();
        if (hpIcon != null) {
            g.drawImage(hpIcon, offX + HP_BAR_X + HP_BAR_W + 2, offY + HP_BAR_Y, Graphics.TOP | Graphics.LEFT);
        }
        g.setColor(0, 0, 0);

        // Special bar: use SP bar assets
        Image spBg = assets.spbarBg();
        Image spFill = assets.spbarFill();
        Image spFillRainbow = assets.spbarFillRainbow();
        Image spFrame = assets.spbarFrame();
        
        if (spBg != null && spFill != null && spFrame != null) {
            // Draw SP bar background
            g.drawImage(spBg, offX + SPECIAL_BAR_X, offY + SPECIAL_BAR_Y, Graphics.TOP | Graphics.LEFT);
            
            // Release flash: pulse the whole bar bright for ~200 ms after firing.
            boolean flash = now < specialFlashUntil;
            
            // Draw fill based on special state
            if (specialState == SPECIAL_STATE_ACTIVE) {
                // ACTIVE: draw border/outline only, pulsing with drain progress
                int barW = spFill.getWidth() - 2;
                int barH = spBg.getHeight() - 2;
                int fillX = offX + SPECIAL_BAR_X + 1;
                int fillY = offY + SPECIAL_BAR_Y + 1;
                float progress = (float) specialCharge / (float) specialDrainStartCharge;
                if (progress < 0) progress = 0;
                if (progress > 1) progress = 1;
                int currentW = (int) (barW * progress);
                
                // Draw border rectangle (outline only)
                g.setColor(255, 206, 74); // gold/yellow
                // Top and bottom lines
                g.drawLine(fillX, fillY, fillX + currentW, fillY);
                g.drawLine(fillX, fillY + barH, fillX + currentW, fillY + barH);
                // Left and right lines
                g.drawLine(fillX, fillY, fillX, fillY + barH);
                g.drawLine(fillX + currentW, fillY, fillX + currentW, fillY + barH);
                
                // Pulsing inner glow during active
                if ((now / 100) % 2 == 0) {
                    g.setColor(255, 255, 180); // bright flash
                    g.drawLine(fillX + 1, fillY + 1, fillX + currentW - 1, fillY + 1);
                }
            } else {
                // IDLE or COOLDOWN: normal fill
                int specialFillW = (!specialLocked && specialCharge > 0)
                        ? ((spFill.getWidth() - 2) * specialCharge) / SPECIAL_CHARGE_MAX
                        : 0;
                
                if (specialFillW > 0 && !flash) {
                    // Use rainbow fill when meter is full and ready
                    Image fillImg = (specialCharge >= SPECIAL_CHARGE_MAX && spFillRainbow != null) ? spFillRainbow : spFill;
                    int fillX = offX + SPECIAL_BAR_X + 1;
                    int fillY = offY + SPECIAL_BAR_Y + 1;
                    int fillH = spBg.getHeight() - 2;
                    g.setColor(255, 206, 74); // SPECIAL_READY_R/G/B
                    g.fillRect(fillX, fillY, specialFillW, fillH);
                }
            }
            
            // Draw frame overlay
            g.drawImage(spFrame, offX + SPECIAL_BAR_X, offY + SPECIAL_BAR_Y, Graphics.TOP | Graphics.LEFT);
            
            // Cooldown indicator: dim overlay when in cooldown
            if (specialState == SPECIAL_STATE_COOLDOWN) {
                g.setColor(60, 60, 60); // dark overlay
                g.fillRect(offX + SPECIAL_BAR_X + 1, offY + SPECIAL_BAR_Y + 1,
                        spBg.getWidth() - 2, spBg.getHeight() - 2);
            }
        } else {
            // Fallback to rect drawing (original code)
            int specialFillW = (!specialLocked && specialCharge > 0)
                    ? ((SPECIAL_BAR_W - 2 * SPECIAL_BAR_INSET) * specialCharge) / SPECIAL_CHARGE_MAX
                    : 0;
            boolean flash = now < specialFlashUntil;
            g.setColor(20, 20, 20);
            g.fillRect(offX + SPECIAL_BAR_X, offY + SPECIAL_BAR_Y, SPECIAL_BAR_W, SPECIAL_BAR_H);
            if (flash) {
                g.setColor(255, 255, 180);
            } else {
                g.setColor(SPECIAL_TRACK_R, SPECIAL_TRACK_G, SPECIAL_TRACK_B);
            }
            g.fillRect(offX + SPECIAL_BAR_X + SPECIAL_BAR_INSET, offY + SPECIAL_BAR_Y + SPECIAL_BAR_INSET,
                    SPECIAL_BAR_W - 2 * SPECIAL_BAR_INSET, SPECIAL_BAR_H - 2 * SPECIAL_BAR_INSET);
            
            if (specialState == SPECIAL_STATE_ACTIVE) {
                // ACTIVE: border/outline only
                float progress = (float) specialCharge / (float) specialDrainStartCharge;
                if (progress < 0) progress = 0;
                if (progress > 1) progress = 1;
                int currentW = (int) ((SPECIAL_BAR_W - 2 * SPECIAL_BAR_INSET) * progress);
                int x = offX + SPECIAL_BAR_X + SPECIAL_BAR_INSET;
                int y = offY + SPECIAL_BAR_Y + SPECIAL_BAR_INSET;
                int h = SPECIAL_BAR_H - 2 * SPECIAL_BAR_INSET;
                g.setColor(255, 206, 74);
                g.drawLine(x, y, x + currentW, y);
                g.drawLine(x, y + h, x + currentW, y + h);
                g.drawLine(x, y, x, y + h);
                g.drawLine(x + currentW, y, x + currentW, y + h);
            } else if (specialFillW > 0 && !flash) {
                g.setColor(SPECIAL_READY_R, SPECIAL_READY_G, SPECIAL_READY_B);
                g.fillRect(offX + SPECIAL_BAR_X + SPECIAL_BAR_INSET, offY + SPECIAL_BAR_Y + SPECIAL_BAR_INSET,
                        specialFillW, SPECIAL_BAR_H - 2 * SPECIAL_BAR_INSET);
            }
            
            if (specialState == SPECIAL_STATE_COOLDOWN) {
                g.setColor(60, 60, 60);
                g.fillRect(offX + SPECIAL_BAR_X + SPECIAL_BAR_INSET, offY + SPECIAL_BAR_Y + SPECIAL_BAR_INSET,
                        SPECIAL_BAR_W - 2 * SPECIAL_BAR_INSET, SPECIAL_BAR_H - 2 * SPECIAL_BAR_INSET);
            }
        }
        g.setColor(0, 0, 0);

        // Weapon row: six equal cells spanning the HP bar width, under the special bar.
        // A cell is marked as owned only when the slot holds a real, in-range weapon entry.
        for (int i = 0; i < SLOT_COLS; i++) {
            boolean owned = false;
            if (i < weaponCount) {
                int wi = weaponSlots[i];
                if (wi >= 0 && wi < ContentCatalog.WEAPONS.length && ContentCatalog.WEAPONS[wi] != null) owned = true;
            }
            drawHudSlot(g, offX + HP_BAR_X + i * SLOT_W, offY + WEAPON_SLOT_Y, owned, i);
        }

        // Item row: the same six-cell tray under the weapons, holding the passive skills (the
        // items) picked up during the run. Items always fill the first free cells in pickup
        // order, so a plain prefix test marks the acquired ones.
        for (int i = 0; i < SLOT_COLS; i++) {
            drawHudSlot(g, offX + HP_BAR_X + i * SLOT_W, offY + ITEM_SLOT_Y, i < itemCount, i);
        }
        g.setColor(255, 255, 255);

        // Run timer, top-centre like the original game, drawn as normal bold text.
        if (cachedTimeString == null || now - lastTimeStringUpdate >= 1000) {
            cachedTimeString = timeString();
            lastTimeStringUpdate = now;
        }
        Font medium = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        g.setFont(medium);
        g.drawString(cachedTimeString, offX + viewWidth / 2, offY + STATUS_LV_Y, Graphics.TOP | Graphics.HCENTER);
        g.setFont(small);

        // Status run block, top-right: Level as normal text
        int statusX = offX + viewWidth - STATUS_MARGIN;
        if (cachedLvString == null || playerLevel != lastPlayerLevel) {
            cachedLvString = "Lv " + playerLevel;
            lastPlayerLevel = playerLevel;
        }
        g.setColor(255, 255, 255);
        g.drawString(cachedLvString, statusX, offY + STATUS_LV_Y, Graphics.TOP | Graphics.RIGHT);

        // EXP bar: animated frames from asset (90 frames at 76x3)
        Image[] expAnim = assets.expbarAnim();
        Image expBg = assets.expbarBg();
        Image expFrame = assets.expbarFrame();
        Image expShine = assets.expbarShine();
        double expRatio = expNeed > 0 ? MathUtil.clamp((double) experience / (double) expNeed, 0.0, 1.0) : 0.0;
        
        if (expAnim != null && expAnim.length > 0) {
            // Select frame based on experience ratio (0-89)
            int frameIndex = (int) (expRatio * (expAnim.length - 1));
            Image frame = expAnim[frameIndex];
            int expX = statusX - EXP_BAR_W;
            int expY = offY + STATUS_EXP_Y;
            
            if (frame != null) {
                g.drawImage(frame, expX, offY + STATUS_EXP_Y, Graphics.TOP | Graphics.LEFT);
            }
            // Draw frame overlay
            Image expFrameImg = assets.expbarFrame();
            if (expFrameImg != null) {
                g.drawImage(expFrameImg, expX, offY + STATUS_EXP_Y, Graphics.TOP | Graphics.LEFT);
            }
            // Optional shine overlay
            Image expShineImg = assets.expbarShine();
            if (expShineImg != null) {
                g.drawImage(expShineImg, expX, offY + STATUS_EXP_Y, Graphics.TOP | Graphics.LEFT);
            }
        } else {
            // Fallback to rect drawing
            int expX = statusX - EXP_BAR_W;
            g.setColor(24, 54, 132); g.fillRect(expX, offY + STATUS_EXP_Y, EXP_BAR_W, EXP_BAR_H);
            g.setColor(EXP_FILL_R, EXP_FILL_G, EXP_FILL_B);
            g.fillRect(expX + 1, offY + STATUS_EXP_Y + 1, (int) ((EXP_BAR_W - 2) * expRatio), EXP_BAR_H - 2);
            g.setColor(0, 0, 0);
            g.drawRect(expX, offY + STATUS_EXP_Y, EXP_BAR_W - 1, EXP_BAR_H - 1);
        }

        g.setColor(255, 255, 255);
        g.drawString("Status run", statusX, offY + STATUS_LABEL_Y, Graphics.TOP | Graphics.RIGHT);

        // Coins and Kills, as normal text
        if (cachedCoinsKillString == null || coins != lastCoins || kills != lastKills) {
            cachedCoinsKillString = "Coins " + coins + "  Kill " + kills;
            lastCoins = coins;
            lastKills = kills;
        }
        g.setColor(255, 255, 255);
        g.drawString(cachedCoinsKillString, statusX, offY + STATUS_COINS_Y, Graphics.TOP | Graphics.RIGHT);

        // Wave counter: "W <wave> | <stage>" as normal text
        int currentWave = waveNumber();
        if (cachedWaveString == null || currentWave != lastWaveNumber) {
            cachedWaveString = "W " + currentWave + " | " + (stageIndex + 1);
            lastWaveNumber = currentWave;
        }
        g.drawString(cachedWaveString, statusX, offY + STATUS_WAVE_Y, Graphics.TOP | Graphics.RIGHT);

        // Fans line (Time Mode only)
        if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET) {
            if (cachedFansString == null || kills != lastFansKills) {
                cachedFansString = "Fans " + kills + "/" + mode.targetKills;
                lastFansKills = kills;
            }
            g.setColor(255, 255, 255);
            g.drawString(cachedFansString, statusX, offY + STATUS_FANS_Y, Graphics.TOP | Graphics.RIGHT);
        }

        // Boss HP bar and name — enhanced UI with multi-boss & phase support.
        // Collect all active bosses (primary + additional).
        Boss[] bossesToDraw = new Boss[MAX_ACTIVE_BOSSES];
        int drawCount = 0;
        if (boss.active) bossesToDraw[drawCount++] = boss;
        for (int i = 0; i < MAX_ACTIVE_BOSSES && drawCount < MAX_ACTIVE_BOSSES; i++) {
            if (activeBosses[i] != null && activeBosses[i].active) {
                boolean already = false;
                for (int j = 0; j < drawCount; j++) if (bossesToDraw[j] == activeBosses[i]) { already = true; break; }
                if (!already) bossesToDraw[drawCount++] = activeBosses[i];
            }
        }

        if (drawCount > 0) {
            int barY = offY + 90; // Top position for first boss bar
            int barSpacing = BOSS_BAR_H + 8; // Vertical spacing between bars

            for (int b = 0; b < drawCount; b++) {
                Boss bb = bossesToDraw[b];
                if (bb == null || !bb.active) continue;

                int bossBarX = offX + (viewWidth - BOSS_BAR_W) / 2;
                int currentBarY = barY + b * barSpacing;

                // Background panel (dark with subtle border)
                g.setColor(20, 10, 10);
                g.fillRect(bossBarX - 2, currentBarY - 2, BOSS_BAR_W + 4, BOSS_BAR_H + 4);
                g.setColor(60, 20, 20);
                g.drawRect(bossBarX - 2, currentBarY - 2, BOSS_BAR_W + 4, BOSS_BAR_H + 4);

                // Bar background
                g.setColor(40, 0, 0);
                g.fillRect(bossBarX, currentBarY, BOSS_BAR_W, BOSS_BAR_H);

                // HP fill with gradient-like effect (two-tone)
                double bossHpRatio = MathUtil.clamp((double) bb.hp / (double) bb.maxHp, 0.0, 1.0);
                int fillW = (int) ((BOSS_BAR_W - 2 * BOSS_BAR_INSET) * bossHpRatio);
                int fillX = bossBarX + BOSS_BAR_INSET;
                int fillY = currentBarY + BOSS_BAR_INSET;
                int fillH = BOSS_BAR_H - 2 * BOSS_BAR_INSET;

                // Main fill color (red to orange based on HP)
                if (bossHpRatio > 0.5) {
                    g.setColor(220, 60, 30); // High HP: bright red
                } else if (bossHpRatio > 0.25) {
                    g.setColor(255, 140, 30); // Medium HP: orange
                } else {
                    // Low HP: pulsing red warning
                    if ((System.currentTimeMillis() / 200) % 2 == 0) {
                        g.setColor(255, 40, 40);
                    } else {
                        g.setColor(255, 100, 40);
                    }
                }
                if (fillW > 0) g.fillRect(fillX, fillY, fillW, fillH);

                // Highlight top edge for depth
                if (fillW > 0) {
                    g.setColor(255, 180, 100);
                    g.drawLine(fillX, fillY, fillX + fillW - 1, fillY);
                }

                // Phase indicators (small diamonds above bar)
                if (bb.maxPhase > 1) {
                    int phaseIconSize = 4;
                    int phaseStartX = bossBarX + (BOSS_BAR_W - (bb.maxPhase * 8)) / 2;
                    for (int p = 1; p <= bb.maxPhase; p++) {
                        int px = phaseStartX + (p - 1) * 8;
                        int py = currentBarY - 10;
                        if (p < bb.phase) {
                            g.setColor(100, 255, 100); // Completed phases: green
                        } else if (p == bb.phase) {
                            g.setColor(255, 255, 100); // Current phase: yellow
                            if ((System.currentTimeMillis() / 300) % 2 == 0) {
                                g.setColor(255, 255, 255); // Pulse current
                            }
                        } else {
                            g.setColor(80, 80, 80); // Future phases: gray
                        }
                        // Draw small diamond
                        g.fillRect(px, py + 2, 4, 2);
                        g.drawLine(px + 2, py, px + 4, py + 2);
                        g.drawLine(px + 2, py + 4, px + 4, py + 2);
                    }
                }

                // Phase transition flash overlay
                if (bb.isPhaseTransition && bb.phaseTransitionUntil > System.currentTimeMillis()) {
                    long elapsed = bb.phaseTransitionUntil - System.currentTimeMillis();
                    float progress = 1.0f - (float) elapsed / 1000.0f; // 1 second transition
                    int alpha = (int) (200 * (1.0f - progress));
                    g.setColor(255, 255, 255);
                    g.fillRect(bossBarX, currentBarY, BOSS_BAR_W, BOSS_BAR_H);
                }

                // Boss name (with phase suffix if multi-phase)
                String displayName = cachedBossName;
                if (bb.maxPhase > 1) displayName += "  Phase " + bb.phase;
                g.setColor(255, 255, 255);
                g.drawString(displayName, offX + viewWidth / 2, currentBarY - 18, Graphics.TOP | Graphics.HCENTER);

                // HP numbers
                g.drawString(bb.hp + "/" + bb.maxHp, offX + viewWidth / 2, currentBarY + 2, Graphics.TOP | Graphics.HCENTER);

                // Portrait/icon placeholder (small sprite area on left)
                if (bb.portraitOffsetX != 0 || bb.portraitOffsetY != 0) {
                    g.setColor(100, 50, 50);
                    g.fillRect(bossBarX - BOSS_PORTRAIT_SIZE - 4, currentBarY, BOSS_PORTRAIT_SIZE, BOSS_PORTRAIT_SIZE);
                    g.setColor(200, 100, 100);
                    g.drawRect(bossBarX - BOSS_PORTRAIT_SIZE - 4, currentBarY, BOSS_PORTRAIT_SIZE, BOSS_PORTRAIT_SIZE);
                }
            }
        }
    }

    /**
     * Draws one 11x11 HUD slot. An owned cell carries a neutral 9x9 marker; an empty cell stays
     * dark. No weapon- or skill-specific artwork is drawn: every available sprite is either a
     * generic projectile shared by several weapons or too large for the 9px interior, and MIDP
     * cannot scale images. Level data stays in weaponLevels and is not faked on screen.
     */
    private void drawHudSlot(Graphics g, int sx, int sy, boolean owned, int slotIndex) {
        g.setColor(owned ? SLOT_FILL : SLOT_FILL_EMPTY);
        g.fillRect(sx, sy, SLOT_W, SLOT_H);
        g.setColor(SLOT_BORDER);
        g.drawRect(sx, sy, SLOT_W - 1, SLOT_H - 1);
        if (!owned) return;
        g.setColor(SLOT_MARK, SLOT_MARK_G, SLOT_MARK_B);
        g.fillRect(sx + SLOT_INSET, sy + SLOT_INSET, SLOT_W - 2 * SLOT_INSET, SLOT_H - 2 * SLOT_INSET);

        // Draw weapon level icon if this slot has a weapon with level > 1
        if (slotIndex < weaponCount && slotIndex < weaponLevels.length) {
            int level = weaponLevels[slotIndex];
            if (level > 1 && level <= 5) {
                Image[] wLevels = assets.weaponCaseLevels();
                if (wLevels != null && level - 1 < wLevels.length && wLevels[level - 1] != null) {
                    Image lvlIcon = wLevels[level - 1];
                    int iconX = sx + (SLOT_W - lvlIcon.getWidth()) / 2;
                    int iconY = sy + SLOT_H - lvlIcon.getHeight() - 1;
                    g.drawImage(lvlIcon, iconX, iconY, Graphics.TOP | Graphics.LEFT);
                }
            }
        }
    }

    /** Superseded by FullGameCanvas.drawLevelUp, which already draws this overlay in screen space.
     *  Kept in step with it: like every other overlay the panel and its title anchor to the
     *  visible playfield, never to the world. */
    private void drawLevelUp(Graphics g, int offX, int offY) {
        g.setColor(0, 0, 0); g.fillRect(offX, offY, viewWidth, viewHeight);
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font medium = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        g.setFont(medium); g.setColor(255, 255, 255);
        g.drawString("LEVEL UP", offX + viewWidth / 2, offY + 12, Graphics.TOP | Graphics.HCENTER);
        g.setFont(small);
        int y = 44;
        for (int i = 0; i < offerCount; i++) {
            g.setColor(i == selectedOffer ? 255 : 170, i == selectedOffer ? 230 : 170, i == selectedOffer ? 60 : 170);
            g.drawString("> " + offerLabel(i), offX + 24, offY + y, Graphics.TOP | Graphics.LEFT);
            g.setColor(140, 140, 140); g.drawString(offerType(i), offX + 240, offY + y, Graphics.TOP | Graphics.LEFT);
            y += 24;
        }
    }

    private String timeString() {
        long s = elapsedSeconds(); long m = s / 60L; long h = m / 60L; m %= 60L; s %= 60L;
        return h > 0 ? (h + ":" + two(m) + ":" + two(s)) : (m + ":" + two(s));
    }
    private String two(long v) { return v < 10 ? ("0" + v) : String.valueOf(v); }
    private Image frame(Image[] frames, int index) {
        if (frames == null || frames.length == 0) return null;
        int i = index % frames.length; if (i < 0) i += frames.length;
        return frames[i];
    }

    private static final class Enemy {
        boolean active; int defIndex; int x; int y; int hp; int maxHp; int atk; double speed;
        long nextAttack; int anim; long nextAnim; int exp; int knockResist; long lastKnockHit;
        int kbDx; int kbDy; long kbUntil; int chargeDx; int chargeDy; long chargeUntil; long healAt;
        // AI_ORBIT_CHARGER only: orbitEndsAt != 0 means the enemy is circling until that time,
        // orbitDir is the circling direction (+1/-1), and moveCarry holds the sub-pixel remainder
        // so movement slower than 1px per tick still advances over several ticks.
        int orbitDir; long orbitEndsAt; double moveCarry;
    }
    private static final class Shot {
        boolean active; boolean fromPlayer; boolean attached; boolean returning; boolean critical;
        int pattern; int weaponIndex; int damage; int life; int age; int dx; int dy; int x; int y;
        int area; int knockback; int target; long hitMask; boolean bossHit; double orbitAngle; String sprite;
        /**
         * Hitbox measured from the sprite at the moment this shot was fired, so the collision loop
         * never has to reach into the asset library. Two reasons: the collision pass runs for every
         * live shot every tick, and the asset library is not guaranteed to be present -- a headless
         * test builds the same world without one. Measuring once at fire time also pins the box to
         * the frame the shot was drawn with, which is what "the animation is the hitbox" means.
         */
        int hitW; int hitH;
    }
    private static final class Melee {
        boolean active; boolean critical; long until; int x; int y; int range; int area; int damage;
        int knockback; int weaponIndex; long hitMask; boolean bossHit;
        int animFrame; long animTimer; int animDir; int animWeaponType;
    }
    private static final class Pickup {
        boolean active; int type; int value; int x; int y; long born;
    }
    private static final class Boss {
        boolean active; int defIndex; int x; int y; int hp; int maxHp; int atk; double speed;
        long nextAttack; int anim; long nextAnim; int chargeDx; int chargeDy; long chargeUntil; long hitMask;
        // Multi-boss & phase support
        int phase = 1;
        int maxPhase = 1;
        long phaseTransitionUntil = 0;
        boolean isPhaseTransition = false;
        int portraitOffsetX = 0;
        int portraitOffsetY = 0;
    }
    private static final class Offer {
        int type = -1; int value; int existing; boolean newItem; boolean collab;
        void clear() { type = -1; value = 0; existing = -1; newItem = false; collab = false; }
    }

    /** Shows a dialogue line (up to 3 lines stacked). */
    void showDialogue(String line) {
        if (dialogueLines == null) dialogueLines = new String[MAX_DIALOGUE_LINES];
        for (int i = 0; i < MAX_DIALOGUE_LINES - 1; i++) {
            dialogueLines[i] = dialogueLines[i + 1];
        }
        dialogueLines[MAX_DIALOGUE_LINES - 1] = line;
        if (dialogueLineCount < MAX_DIALOGUE_LINES) dialogueLineCount++;
        dialogueShowUntil = System.currentTimeMillis() + DIALOGUE_DURATION_MS;
    }

    private void drawDialogue(Graphics g, int offX, int offY, long now) {
        if (now >= dialogueShowUntil || dialogueLineCount == 0) return;

        g.setColor(255, 255, 255);
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(small);
        int lineH = small.getHeight() + 2;
        for (int i = 0; i < dialogueLineCount; i++) {
            if (dialogueLines[i] != null) {
                int textX = offX + (viewWidth - g.getFont().stringWidth(dialogueLines[i])) / 2;
                int textY = offY + viewHeight - 40 + i * lineH;
                // Subtle shadow for readability
                g.setColor(0, 0, 0);
                g.drawString(dialogueLines[i], textX + 1, textY + 1, Graphics.TOP | Graphics.LEFT);
                g.setColor(255, 255, 255);
                g.drawString(dialogueLines[i], textX, textY, Graphics.TOP | Graphics.LEFT);
            }
        }
    }

    private void checkDialogueTriggers(long now) {
        // Boss appearance notification only
        if (boss.active && boss.hp == boss.maxHp && now > 5000) {
            showDialogue("Boss appeared: " + bossName() + "!");
        }
    }
}
