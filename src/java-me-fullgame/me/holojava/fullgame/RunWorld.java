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

    private static final int OFFER_SKILL = 0;
    private static final int OFFER_WEAPON = 1;
    private static final int OFFER_ITEM = 2;
    private static final int OFFER_STAT = 3;
    private static final int OFFER_FOOD = 4;
    private static final int OFFER_COINS = 5;
    private static final int OFFER_REROLL = 6;
    private static final int OFFER_HOLD = 7;

    private static final int ENEMY_POOL = 48;
    private static final int SHOT_POOL = 128;
    private static final int MELEE_POOL = 24;
    private static final int PICKUP_POOL = 96;

    // HUD bar geometry, in logical world pixels.
    private static final int HP_BAR_X = 4;
    private static final int HP_BAR_Y = 18;
    // Short bar: 66px is 20.6% of the 320px design width. It also defines the slot-row width,
    // so the weapon and skill rows stay exactly as long as the HP bar.
    private static final int HP_BAR_W = 66;
    private static final int HP_BAR_H = 10;
    private static final int HP_BAR_INSET = 2;
    private static final int HP_BAR_LOW_PERCENT = 25;
    private static final int BOSS_BAR_W = 250;
    private static final int BOSS_BAR_H = 18;
    private static final int BOSS_BAR_INSET = 3;

    // Compact slot rows. Both rows are six equal cells spanning HP_BAR_W.
    private static final int SLOT_COLS = 6;
    private static final int SLOT_W = HP_BAR_W / SLOT_COLS; // 66 / 6 = 11
    private static final int SLOT_H = 11;
    private static final int SLOT_INSET = 1;
    private static final int WEAPON_SLOT_Y = 30;
    private static final int SKILL_SLOT_Y = 42;
    private static final int SLOT_FILL = 30;
    private static final int SLOT_FILL_EMPTY = 18;
    private static final int SLOT_BORDER = 78;
    // Neutral ownership marker. Weapon/skill slots carry no weapon-specific art: no HoloJava
    // sprite except bullet (7x7) fits the 9px interior, and no HoloCure icon is below 12x17,
    // so nothing could be shown without scaling, which MIDP cannot do.
    private static final int SLOT_MARK = 175;
    private static final int SLOT_MARK_G = 180;
    private static final int SLOT_MARK_B = 190;

    // Status run block, top-right, right-aligned to the world edge.
    private static final int STATUS_MARGIN = 4;
    private static final int STATUS_TITLE_Y = 4;
    private static final int STATUS_ROW_H = 15;
    private static final int FANS_LINE_Y = 64;

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
    private long startTime;
    private long lastMinuteScaled = -1L;
    private double scaleA;
    private double scaleB;

    private int px = 130;
    private int py = 95;
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
    private long shieldReadyAt;
    private int shield;
    private int weaponCount;
    private int itemCount;

    // Derived stats.
    private int atkPercentBonus;
    private int hastePercent;
    private int speedPercentBonus;
    private int pickupPercentBonus;
    private int critPercentBonus;
    private int expPercentBonus;
    private int defensePercent;
    private int baseMaxHp;

    private long nextSpawnAt;
    private long nextWaveAt;
    private boolean ringEventDone;
    private boolean hordeEventDone;
    private int nextEndlessEncounter;

    private Boss boss = new Boss();
    private int bossEncounterIndex = -1;

    private boolean skillAcquired;
    private long skillReadyAt;
    private boolean specialUsed;

    private int rerolls;
    private int holds;
    private int offerCount;
    private int selectedOffer;
    private int heldOffer = -1;
    private boolean won;

    // HUD string cache fields for allocation optimization
    private String cachedHpString;
    private String cachedLevelExpString;
    private String cachedTimeKillString;
    private String cachedCoinsString;
    private String cachedFansString;
    private String cachedBossName;
    private String cachedControlsHint = "5/FIRE/1 Attack  8 Skill  9 Special  0 Pause";

    private int lastPlayerHp = -1;
    private int lastPlayerMaxHp = -1;
    private int lastPlayerLevel = -1;
    private int lastExperience = -1;
    private int lastExpNeed = -1;
    private long lastTimeStringUpdate = 0;
    private String cachedTimeString;
    private String lastCachedTimeString;
    private int lastKills = -1;
    private int lastCoins = -1;
    private int lastWeaponCount = -1;
    private int lastItemCount = -1;
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
        state = STATE_RUNNING;
        won = false;
        moving = false;
        px = 130; py = 95; facingX = 1; facingY = 0;
        playerLevel = 1; experience = 0; expNeed = requiredForLevel(1);
        coins = 0; kills = 0; defeatCount = 0;
        invulUntil = 0; slowEnemiesUntil = 0; lifestealUntil = 0;
        regenTickAt = startTime + 3000; shieldReadyAt = 0; shield = 0;
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
        for (int i = 0; i < encounterStarted.length; i++) { encounterStarted[i] = false; encounterDefeated[i] = false; }
        boss.active = false; bossEncounterIndex = -1; nextEndlessEncounter = 0;
        ringEventDone = false; hordeEventDone = false;
        nextSpawnAt = startTime + 1000;
        nextWaveAt = startTime + 180000L;
        skillAcquired = false; skillReadyAt = 0; specialUsed = false;
        rerolls = mode.standardLoadout ? 10 : 0;
        holds = 5;
        offerCount = 0; selectedOffer = 0; heldOffer = -1;

        // Reset HUD string cache
        cachedHpString = null;
        cachedLevelExpString = null;
        cachedTimeKillString = null;
        cachedCoinsString = null;
        cachedFansString = null;
        cachedBossName = null;
        lastPlayerHp = -1; lastPlayerMaxHp = -1;
        lastPlayerLevel = -1; lastExperience = -1; lastExpNeed = -1;
        lastTimeStringUpdate = 0; cachedTimeString = null; lastCachedTimeString = null;
        lastKills = -1;
        lastCoins = -1; lastWeaponCount = -1; lastItemCount = -1;
        lastFansKills = -1;
        lastBossName = null;

        addWeapon(ContentCatalog.weaponIndex(character.mainWeapon), 1);
        if (mode.standardLoadout) weaponLevels[0] = ContentCatalog.weapon(0).maxLevel;
        weaponReadyAt[0] = startTime + 500;
        baseMaxHp = character.hp;
        if (mode.id.equals("endless") && save.hardcore()) baseMaxHp = 1;
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
    boolean specialUsed() { return specialUsed; }
    int bossIndex() { return boss.defIndex; }
    int bossHp() { return boss.hp; }
    int bossMaxHp() { return boss.maxHp; }
    String bossName() { return boss.active ? ContentCatalog.BOSSES[boss.defIndex].name : ""; }
    boolean won() { return won; }
    long elapsedSeconds() { return Math.max(0L, (System.currentTimeMillis() - startTime) / 1000L); }

    void pause() { if (state == STATE_RUNNING) state = STATE_PAUSED; }
    void resume() { if (state == STATE_PAUSED) state = STATE_RUNNING; }

    int offerCount() { return offerCount; }
    int selectedOffer() { return selectedOffer; }
    void moveOffer(int delta) {
        if (offerCount == 0) return;
        selectedOffer = (selectedOffer + delta + offerCount) % offerCount;
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
        if (o.type == OFFER_SKILL) return character.skill;
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
        if (t == OFFER_SKILL) return "SKILL";
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
                heldOffer = selectedOffer < 4 ? selectedOffer : heldOffer;
                generateOffers();
                selectedOffer = 0;
                return;
            }
            return;
        }
        applyOffer(index);
        state = STATE_RUNNING;
        invulUntil = System.currentTimeMillis() + 500;
        if (experience >= expNeed && state == STATE_RUNNING) {
            experience -= expNeed;
            playerLevel++;
            expNeed = requiredForLevel(playerLevel);
            state = STATE_LEVEL_UP;
            generateOffers();
        }
    }

    void update(int keys, boolean attackPressed, boolean skillPressed, boolean specialPressed, long now) {
        if (state != STATE_RUNNING) return;
        if (now - startTime > 120000L && lastMinuteScaled != now / 60000L) {
            lastMinuteScaled = now / 60000L;
            updateScaling();
        }
        updatePlayer(keys, skillPressed, specialPressed, now);
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

    private void updatePlayer(int keys, boolean skillPressed, boolean specialPressed, long now) {
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
        if (skillPressed && skillAcquired && now >= skillReadyAt) activateSkill(now);
        if (specialPressed && !specialUsed) activateSpecial(now);
    }

    private void activateSkill(long now) {
        skillReadyAt = now + 15000L;
        if (characterIndex == 0) { // Shark Bite
            damageEnemiesInRadius(70, 6, true);
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
            damageEnemiesInRadius(45, 4, false);
        }
    }

    private void activateSpecial(long now) {
        specialUsed = true;
        if (characterIndex == 0) { // Shark Call
            damageEnemiesInRadius(110, 18, true);
        } else if (characterIndex == 1) { // Slow Time
            slowEnemiesUntil = now + 6000L;
        } else if (characterIndex == 2) { // The Reaper
            playerHp = Math.min(playerMaxHp, playerHp + Math.max(8, playerMaxHp / 4));
            lifestealUntil = now + 10000L;
        } else if (characterIndex == 3) { // Phoenix Fire
            damageEnemiesInRadius(130, 24, true);
        } else { // Spirit of the Oni
            shield = 30;
            shieldReadyAt = now + 10000L;
            clearEnemyShots();
        }
    }

    private void updateTimedEvents(long now) {
        long elapsed = now - startTime;
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
        long elapsed = now - startTime;
        int interval = stage.spawnIntervalMs - (int) (elapsed / 5000L) * 20;
        if (interval < 250) interval = 250;
        if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET) interval = Math.min(interval, 550);
        spawnEnemy(now);
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
            if (now < invulUntil || now >= e.nextAttack) {
                // Contact damage uses the enemy cooldown; the global invulnerability window wins.
            } else if (overlap(px, py, 24, 24, e.x, e.y, w, h)) {
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

    private void updateBossDirector(long now) {
        if (stage.timeStage || stage.bossA == null || stage.bossA.length() == 0) return;
        int elapsedSec = (int) ((now - startTime) / 1000L);
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
        boss.maxHp = (int) Math.min(20000.0, MathUtil.power(def.hp + def.hp * 0.05 * scaleA, 1.0 + scaleB / 50.0));
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
    }

    private long nowAttackDelay() { return System.currentTimeMillis() + 900L; }
    private long nowAnimDelay() { return System.currentTimeMillis() + 180L; }

    private void updateBoss(long now) {
        if (!boss.active) return;
        ContentCatalog.BossDef def = ContentCatalog.BOSSES[boss.defIndex];
        int dx = playerCenterX() - (boss.x + bossWidth() / 2);
        int dy = playerCenterY() - (boss.y + bossHeight() / 2);
        int distSq = dx * dx + dy * dy;
        double speed = boss.speed * (now < slowEnemiesUntil ? 0.35 : 1.0);
        if (def.behavior == ContentCatalog.AI_SHOOTER) {
            if (distSq > 2500) {
                boss.x += MathUtil.clampInt(dx, -2, 2);
                boss.y += MathUtil.clampInt(dy, -1, 1);
            }
            if (now >= boss.nextAttack) {
                int shots = 5 + (int) Math.min(6.0, scaleA / 8.0);
                int originX = boss.x + bossWidth() / 2;
                int originY = boss.y + bossHeight() / 2;
                for (int i = 0; i < shots; i++) {
                    double angle = (Math.PI * 2.0 * i / shots) + (now / 1000.0);
                    int tx = originX + (int) (Math.cos(angle) * 100.0);
                    int ty = originY + (int) (Math.sin(angle) * 100.0);
                    fireEnemyShot(originX, originY, tx, ty, boss.atk, 65);
                }
                boss.nextAttack = now + def.cooldownMs;
            }
        } else if (def.behavior == ContentCatalog.AI_CHARGER) {
            if (now < boss.chargeUntil) {
                boss.x += boss.chargeDx;
                boss.y += boss.chargeDy;
            } else {
                if (dx > 0) boss.x += step(speed * 1.25);
                if (dx < 0) boss.x -= step(speed * 1.25);
                if (dy > 0) boss.y += step(speed * 0.75);
                if (dy < 0) boss.y -= step(speed * 0.75);
                if (distSq < 20000 && now >= boss.nextAttack) {
                    double len = Math.sqrt((double) distSq);
                    boss.chargeDx = len > 0.0 ? (int) (dx / len * 9.0) : facingX;
                    boss.chargeDy = len > 0.0 ? (int) (dy / len * 9.0) : 0;
                    boss.chargeUntil = now + 900L;
                    boss.nextAttack = now + def.cooldownMs;
                }
            }
        } else {
            if (dx > 0) boss.x += step(speed);
            if (dx < 0) boss.x -= step(speed);
            if (dy > 0) boss.y += step(speed);
            if (dy < 0) boss.y -= step(speed);
            if (now >= boss.nextAttack) {
                if (def.behavior == ContentCatalog.AI_BOMBER && distSq < 2000) {
                    damagePlayer(boss.atk, now);
                    defeatBoss();
                    return;
                }
                fireEnemyShot(boss.x + bossWidth() / 2, boss.y + bossHeight() / 2,
                        playerCenterX(), playerCenterY(), boss.atk, 80);
                boss.nextAttack = now + def.cooldownMs;
            }
        }
        if (boss.x < -60) boss.x = -60;
        if (boss.y < -80) boss.y = -80;
        if (boss.x > worldWidth) boss.x = worldWidth;
        if (boss.y > worldHeight) boss.y = worldHeight;
        if (now >= boss.nextAnim) { boss.nextAnim = now + 180L; boss.anim++; }
        if (now >= invulUntil && overlap(px, py, 24, 24, boss.x, boss.y, bossWidth(), bossHeight())) {
            damagePlayer(boss.atk, now);
        }
    }

    private void defeatBoss() {
        // Idempotence guard. The reference has none, but there the clear reward only inflated a
        // run-local counter. That reward now credits a permanent balance, so paying it twice would
        // duplicate coins forever. Every current caller already checks boss.active, so this is
        // unreachable in play today and changes no existing outcome; it exists so that no future
        // caller can duplicate permanent coins by forgetting that check.
        if (!boss.active) return;
        boss.active = false;
        int slot = bossEncounterIndex;
        bossEncounterIndex = -1;
        if (slot >= 0 && slot < encounterDefeated.length) encounterDefeated[slot] = true;
        ContentCatalog.BossDef def = ContentCatalog.BOSSES[boss.defIndex];
        dropPickup(boss.x + bossWidth() / 2, boss.y + bossHeight() / 2, 0, def.exp, System.currentTimeMillis());
        for (int i = 0; i < 5; i++) dropPickup(boss.x + bossWidth() / 2, boss.y + bossHeight() / 2, 1, 10, System.currentTimeMillis());
        if (mode.completion == ContentCatalog.COMPLETE_FINAL_BOSS) {
            // The final boss is the 20:00 encounter (slot 1) on every stage:
            // the unsourced 30:00 duplicate was removed, so hard stages no
            // longer need a third slot to win.
            boolean finalDefeated = encounterDefeated[1];
            if (finalDefeated) {
                // Decision C: the stage-clear reward joins the permanent balance, while the
                // level-up +100 stays run-local. crediting is a transaction, so a refused write
                // leaves the stored balance exactly as it was. This is reached only once per run:
                // defeatBoss clears boss.active on its first line and every caller of
                // damageBoss is gated on it.
                int clearReward = stage.coinClear + defeatCount / 4;
                coins += clearReward;
                if (save != null) save.creditCoins(clearReward);
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
        damage = (damage * (100 + character.atkPercent + atkPercentBonus)) / 100;
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
            s.target = nearestEnemy();
            s.sprite = def.sprite;
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
                if (def.pattern == ContentCatalog.PATTERN_HOMING || s.target >= 0) {
                    if (s.target >= 0) {
                        Enemy t = enemies[s.target];
                        tx = t.x + enemyWidth(t.defIndex) / 2;
                        ty = t.y + enemyHeight(t.defIndex) / 2;
                    } else { tx = ox + facingX * 80; ty = oy; }
                } else {
                    double base = Math.atan2((double) facingY, (double) facingX);
                    if (facingX == 0 && facingY == 0) base = 0.0;
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
            int cx = px + 12;
            int cy = py + 12;
            int mx = cx - m.area / 2;
            int my = cy - m.area / 2;
            for (int e = 0; e < enemies.length; e++) {
                Enemy enemy = enemies[e];
                if (!enemy.active || (m.hitMask & (1L << e)) != 0L) continue;
                if (overlap(mx, my, m.area, m.area, enemy.x, enemy.y, enemyWidth(enemy.defIndex), enemyHeight(enemy.defIndex))) {
                    m.hitMask |= 1L << e;
                    damageEnemy(e, enemy, m.damage, m.critical, m.knockback, px - enemy.x, py - enemy.y);
                }
            }
            if (boss.active && !m.bossHit && overlap(mx, my, m.area, m.area, boss.x, boss.y, bossWidth(), bossHeight())) {
                m.bossHit = true;
                damageBoss(m.damage, m.critical, m.knockback);
            }
        }
    }

    private void updateShots(long now) {
        for (int i = 0; i < shots.length; i++) {
            Shot s = shots[i];
            if (!s.active) continue;
            s.life--;
            s.age++;
            if (s.attached) {
                s.orbitAngle += 0.18;
                s.x = playerCenterX() + (int) (Math.cos(s.orbitAngle) * 30.0);
                s.y = playerCenterY() + (int) (Math.sin(s.orbitAngle) * 30.0);
            } else {
                if (s.pattern == ContentCatalog.PATTERN_HOMING && s.target >= 0 && enemies[s.target].active) {
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
                for (int e = 0; e < enemies.length; e++) {
                    Enemy enemy = enemies[e];
                    if (!enemy.active || (s.hitMask & (1L << e)) != 0L) continue;
                    if (overlap(s.x, s.y, shotWidth(s), shotHeight(s), enemy.x, enemy.y, enemyWidth(enemy.defIndex), enemyHeight(enemy.defIndex))) {
                        s.hitMask |= 1L << e;
                        damageEnemy(e, enemy, s.damage, s.critical, s.knockback, s.dx, s.dy);
                        if (s.pattern == ContentCatalog.PATTERN_AOE) { explode(s.x, s.y, s.area, 0, false); remove = true; }
                        else if (s.pattern != ContentCatalog.PATTERN_PIERCING && s.pattern != ContentCatalog.PATTERN_BOOMERANG) remove = true;
                        if (remove) break;
                    }
                }
                if (!remove && boss.active && !s.bossHit
                        && overlap(s.x, s.y, shotWidth(s), shotHeight(s), boss.x, boss.y, bossWidth(), bossHeight())) {
                    s.bossHit = true;
                    damageBoss(s.damage, s.critical, s.knockback);
                    if (s.pattern != ContentCatalog.PATTERN_PIERCING) remove = true;
                }
                if (remove) {
                    if (s.pattern == ContentCatalog.PATTERN_AOE) explode(s.x, s.y, s.area, s.damage, s.critical);
                    s.active = false;
                }
            } else if (overlap(s.x, s.y, 7, 7, px, py, 24, 24)) {
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
        int radius = 22 + (22 * pickupPercentBonus) / 100;
        for (int i = 0; i < pickups.length; i++) {
            Pickup p = pickups[i];
            if (!p.active) continue;
            if (now - p.born > 30000L) { p.active = false; continue; }
            int dx = playerCenterX() - (p.x + 6);
            int dy = playerCenterY() - (p.y + 6);
            int distSq = dx * dx + dy * dy;
            if (p.type == 3 || distSq <= radius * radius) {
                if (dx > 0) p.x += p.type == 3 ? 8 : 3;
                if (dx < 0) p.x -= p.type == 3 ? 8 : 3;
                if (dy > 0) p.y += p.type == 3 ? 8 : 3;
                if (dy < 0) p.y -= p.type == 3 ? 8 : 3;
            }
            if (p.type == 3 && distSq < 900) collectPickup(p, now);
            else if (distSq <= radius * radius) collectPickup(p, now);
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
                state = STATE_LEVEL_UP;
                generateOffers();
                selectedOffer = 0;
            }
        } else if (p.type == 1) {
            coins += p.value;
            // Only a collected pickup becomes permanent. The run counter above stays run-local and
            // is what the HUD and the results screen show; this is the balance that survives the
            // run. creditCoins is a transaction, so a refused write leaves the stored balance
            // exactly as it was and the run continues regardless. p.active was cleared on the first
            // line of this method, so one pickup can never be committed twice.
            if (save != null) save.creditCoins(p.value);
        } else if (p.type == 2) {
            playerHp = Math.min(playerMaxHp, playerHp + Math.max(5, playerMaxHp / 5));
        } else if (p.type == 4) {
            for (int i = 0; i < weaponCount; i++) {
                ContentCatalog.WeaponDef def = ContentCatalog.weapon(weaponSlots[i]);
                if (weaponLevels[i] < def.maxLevel) {
                    weaponLevels[i]++;
                    break;
                }
            }
        }
    }

    private void updateRegen(long now) {
        if (now >= regenTickAt) {
            regenTickAt = now + 3000L;
            boolean regen = false;
            for (int i = 0; i < itemCount; i++) {
                ContentCatalog.ItemDef d = ContentCatalog.item(itemSlots[i]);
                if (d != null && d.effect == ContentCatalog.EFFECT_REGEN) regen = true;
            }
            if (regen && playerHp < playerMaxHp) playerHp = Math.min(playerMaxHp, playerHp + 2);
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
        if (def.coinChance > 0 && rand(def.coinChance) == 0) dropPickup(enemy.x, enemy.y, 1, 10, now);
        if (rand(200) == 0) dropPickup(enemy.x, enemy.y, 2, 1, now);
        if (rand(1301) == 0) dropPickup(enemy.x, enemy.y, 4, 1, now);
        if (rand(3000) == 0) dropPickup(enemy.x, enemy.y, 3, 0, now);
        if (now < lifestealUntil) playerHp = Math.min(playerMaxHp, playerHp + 1);
    }

    private void damageBoss(int damage, boolean critical, int knockback) {
        boss.hp -= damage;
        if (boss.hp <= 0) defeatBoss();
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
        s.x = x; s.y = y; s.dx = (int) (dx / len * speed); s.dy = (int) (dy / len * speed);
    }

    private void clearEnemyShots() {
        for (int i = 0; i < shots.length; i++) if (shots[i].active && !shots[i].fromPlayer) shots[i].active = false;
    }

    private void spawnWave(int count, boolean ring) {
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
        if (weaponCount < 6) {
            weaponSlots[weaponCount] = weaponIndex;
            weaponLevels[weaponCount] = level;
            weaponCount++;
        }
    }

    private void addItem(int itemIndex, int level) {
        if (itemIndex < 0) return;
        if (itemCount < 6) {
            itemSlots[itemCount] = itemIndex;
            itemLevels[itemCount] = level;
            itemCount++;
        }
    }

    private void recomputeStats() {
        atkPercentBonus = 0; hastePercent = 0; speedPercentBonus = 0;
        pickupPercentBonus = 0; critPercentBonus = 0; expPercentBonus = 0; defensePercent = 0;
        int maxHp = baseMaxHp;
        boolean regen = false;
        for (int i = 0; i < itemCount; i++) {
            ContentCatalog.ItemDef d = ContentCatalog.item(itemSlots[i]);
            if (d == null) continue;
            int v = d.value * itemLevels[i];
            if (d.effect == ContentCatalog.EFFECT_DAMAGE_PCT) atkPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_HASTE_PCT) hastePercent += v;
            if (d.effect == ContentCatalog.EFFECT_SPEED_PCT) speedPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_MAX_HP) maxHp += v;
            if (d.effect == ContentCatalog.EFFECT_PICKUP_PCT) pickupPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_CRIT_PCT) critPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_EXP_PCT) expPercentBonus += v;
            if (d.effect == ContentCatalog.EFFECT_REGEN) regen = true;
        }
        playerMaxHp = maxHp;
        if (mode.id.equals("endless") && baseMaxHp == 1) playerMaxHp = 1;
        if (playerHp > playerMaxHp) playerHp = playerMaxHp;
        if (regen && regenTickAt == 0) regenTickAt = System.currentTimeMillis() + 3000L;
    }

    private void generateOffers() {
        for (int i = 0; i < offers.length; i++) offers[i].clear();
        for (int i = 0; i < offerUsed.length; i++) offerUsed[i] = false;
        offerCount = rerolls > 0 ? 6 : 5;
        for (int slot = 0; slot < 4; slot++) {
            int type = chooseOfferType(slot);
            if (!fillOffer(slot, type)) {
                if (!fillOffer(slot, OFFER_WEAPON)) fillOffer(slot, OFFER_ITEM);
            }
        }
        if (heldOffer >= 0 && heldOffer < 4 && offers[heldOffer].type >= 0) {
            Offer copy = offers[heldOffer];
            for (int i = 3; i > 0; i--) offers[i] = offers[i - 1];
            offers[0] = copy;
        }
        offers[4].type = rerolls > 0 ? OFFER_REROLL : -1;
        offers[4].value = rerolls;
        offers[5].type = holds > 0 ? OFFER_HOLD : -1;
    }

    private int chooseOfferType(int slot) {
        int r = rand(20);
        if (slot < 2) {
            if (r < 20 && !skillAcquired) return OFFER_SKILL;
            if (r < 14) return OFFER_WEAPON;
            if (r < 19) return OFFER_ITEM;
            return OFFER_STAT;
        }
        if ((r & 1) == 0) return OFFER_WEAPON;
        return OFFER_ITEM;
    }

    private boolean fillOffer(int slot, int type) {
        if (type == OFFER_SKILL && !skillAcquired) {
            offers[slot].type = OFFER_SKILL; offers[slot].value = 0; offers[slot].existing = -1;
            offers[slot].newItem = false; return true;
        }
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
            if (weaponCount < 6 && mode.allowsCollabs) {
                for (int c = 0; c < ContentCatalog.COLLABS.length; c++) {
                    ContentCatalog.CollabDef cd = ContentCatalog.COLLABS[c];
                    ContentCatalog.WeaponDef wd = ContentCatalog.weapon(cd.id);
                    // Skip collab rows with no runtime weapon definition (e.g.
                    // documented recipes beyond the implemented catalog) instead
                    // of dereferencing null below.
                    if (wd == null) continue;
                    int a = weaponLevelById(cd.weaponA); int b = weaponLevelById(cd.weaponB);
                    if (a == wd.maxLevel && b == wd.maxLevel && !offerUsed[wd.id.hashCode() & 63]) {
                        offers[slot].type = OFFER_WEAPON; offers[slot].value = ContentCatalog.weaponIndex(wd.id);
                        offers[slot].existing = -1; offers[slot].newItem = true; offers[slot].collab = true;
                        offerUsed[wd.id.hashCode() & 63] = true; return true;
                    }
                }
            }
            if (weaponCount < 6) {
                int start = rand(5);
                for (int i = 0; i < ContentCatalog.WEAPONS.length; i++) {
                    int w = (start + i) % ContentCatalog.WEAPONS.length;
                    ContentCatalog.WeaponDef def = ContentCatalog.WEAPONS[w];
                    if (def.category != 0 || weaponSlotById(def.id) >= 0 || offerUsed[def.id.hashCode() & 63]) continue;
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
            if (itemCount < 6) {
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
        if (o.type == OFFER_SKILL) {
            skillAcquired = true;
        } else if (o.type == OFFER_WEAPON) {
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
     * both at 10:00). Only Stage 1 Hard (index 5) is enabled; other hard
     * stages return null until their stage page is verified. Null means the
     * director skips the slot without recording it.
     */
    private String hardSecondBoss(int stageIndex) {
        if (stageIndex == 5) return "smol_ame";
        return null;
    }

    private int scaledEnemyHp(int base) {
        double value = MathUtil.power(base + base * 0.05 * scaleA, 1.0 + scaleB / 50.0);
        return MathUtil.clampInt(MathUtil.roundEven(value), 1, 100000);
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
    private int bossWidth() { return 42; }
    private int bossHeight() { return 42; }
    private int shotWidth(Shot s) { return s.sprite != null && ("bomb".equals(s.sprite) || "axe".equals(s.sprite)) ? 12 : 7; }
    private int shotHeight(Shot s) { return s.sprite != null && ("bomb".equals(s.sprite) || "axe".equals(s.sprite)) ? 12 : 7; }
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
     * toward the right; the caller negates for left. Hitbox untouched.
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
                g.setColor(255, 100, 100); g.fillRect(x + 3, y + 2, 8, 8);
            } else if (p.type == 3) {
                g.setColor(255, 255, 255); g.fillRect(x + 3, y + 3, 6, 6);
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
            if (e.defIndex == 3 || e.defIndex == 4 || e.defIndex == 11) {
                g.setColor(255, 100, 100); g.drawRect(x - 3, y - 3, enemyWidth(e.defIndex) + 6, enemyHeight(e.defIndex) + 6);
            }
        }
        if (boss.active) {
            Image[] frames = assets.bossFrames(boss.defIndex);
            Image im = frame(frames, boss.anim);
            int x = worldOffX + boss.x; int y = offY + boss.y;
            if (im != null) g.drawImage(im, x, y, Graphics.TOP | Graphics.LEFT);
            else { g.setColor(140, 0, 0); g.fillRect(x, y, bossWidth(), bossHeight()); }
            drawPlayer(g, worldOffX, offY, now);
            drawMelee(g, worldOffX, offY, now);
            drawHud(g, offX, offY, now);
            return;
        }
        drawPlayer(g, worldOffX, offY, now);
        drawMelee(g, worldOffX, offY, now);
        drawHud(g, offX, offY, now);
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
                { 18, 18, 34 }, { 36, 20, 52 } };
        int c = stage.palette % colors.length;
        g.setColor(colors[c][0], colors[c][1], colors[c][2]);
        g.fillRect(offX + cameraX, offY, viewWidth, viewHeight);
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
            // the same frame is anchored to the correct edge. Hitbox untouched.
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
        g.setColor(0, 0, 0);

        // HP line: "HP X/Y"
        if (cachedHpString == null || playerHp != lastPlayerHp || playerMaxHp != lastPlayerMaxHp) {
            cachedHpString = "HP " + playerHp + "/" + playerMaxHp;
            lastPlayerHp = playerHp;
            lastPlayerMaxHp = playerMaxHp;
        }
        g.drawString(cachedHpString, offX + 4, offY + 4, Graphics.TOP | Graphics.LEFT);

        // Player HP bar, directly under the HP text. The ratio is clamped so the fill can never
        // exceed the inner width or go negative; playerMaxHp <= 0 renders as an empty bar.
        double maxHpRatio = (double) playerMaxHp;
        double hpRatio = maxHpRatio > 0.0 ? MathUtil.clamp((double) playerHp / maxHpRatio, 0.0, 1.0) : 0.0;
        int hpFillW = (int) ((HP_BAR_W - 2 * HP_BAR_INSET) * hpRatio);
        g.setColor(20, 20, 20); g.fillRect(offX + HP_BAR_X, offY + HP_BAR_Y, HP_BAR_W, HP_BAR_H);
        g.setColor(90, 30, 30);
        g.fillRect(offX + HP_BAR_X + HP_BAR_INSET, offY + HP_BAR_Y + HP_BAR_INSET,
                HP_BAR_W - 2 * HP_BAR_INSET, HP_BAR_H - 2 * HP_BAR_INSET);
        if (hpRatio * 100.0 <= HP_BAR_LOW_PERCENT) g.setColor(220, 40, 40);
        else g.setColor(40, 190, 60);
        g.fillRect(offX + HP_BAR_X + HP_BAR_INSET, offY + HP_BAR_Y + HP_BAR_INSET, hpFillW, HP_BAR_H - 2 * HP_BAR_INSET);
        g.setColor(0, 0, 0);

        // Weapon row: six equal cells directly under the HP bar, spanning the same width.
        // A cell is marked as owned only when the slot holds a real, in-range weapon entry.
        for (int i = 0; i < SLOT_COLS; i++) {
            boolean owned = false;
            if (i < weaponCount) {
                int wi = weaponSlots[i];
                if (wi >= 0 && wi < ContentCatalog.WEAPONS.length && ContentCatalog.WEAPONS[wi] != null) owned = true;
            }
            drawHudSlot(g, offX + HP_BAR_X + i * SLOT_W, offY + WEAPON_SLOT_Y, owned);
        }

        // Skill row: a character owns exactly one skill, tracked by skillAcquired. Only that
        // one cell can ever be marked; the rest stay empty rather than showing invented skills.
        for (int i = 0; i < SLOT_COLS; i++) {
            drawHudSlot(g, offX + HP_BAR_X + i * SLOT_W, offY + SKILL_SLOT_Y, i == 0 && skillAcquired);
        }
        g.setColor(0, 0, 0);

        // Level & EXP line (drawn in the status run block, top-right)
        if (cachedLevelExpString == null || playerLevel != lastPlayerLevel || experience != lastExperience || expNeed != lastExpNeed) {
            cachedLevelExpString = "Lv " + playerLevel + "  EXP " + experience + "/" + expNeed;
            lastPlayerLevel = playerLevel;
            lastExperience = experience;
            lastExpNeed = expNeed;
        }

        // Time & Kill line (timeString cached per second)
        if (cachedTimeString == null || now - lastTimeStringUpdate >= 1000) {
            cachedTimeString = timeString();
            lastTimeStringUpdate = now;
        }
        if (cachedTimeKillString == null || kills != lastKills || !cachedTimeString.equals(lastCachedTimeString)) {
            cachedTimeKillString = "Time " + cachedTimeString + "  Kill " + kills;
            lastKills = kills;
            lastCachedTimeString = cachedTimeString;
        }

        // Coins, Weapons, Items line (drawn in the status run block, top-right)
        if (cachedCoinsString == null || coins != lastCoins || weaponCount != lastWeaponCount || itemCount != lastItemCount) {
            cachedCoinsString = "Coins " + coins + "  W " + weaponCount + "  I " + itemCount;
            lastCoins = coins;
            lastWeaponCount = weaponCount;
            lastItemCount = itemCount;
        }

        // Status run block, top-right. Graphics.RIGHT anchors each line to the right edge of the
        // visible playfield, so the block tracks the viewport rather than the world: a world wider
        // than the view would otherwise push the whole block off screen.
        int statusX = offX + viewWidth - STATUS_MARGIN;
        g.setColor(0, 0, 0);
        g.drawString("Status run", statusX, offY + STATUS_TITLE_Y, Graphics.TOP | Graphics.RIGHT);
        g.drawString(cachedLevelExpString, statusX, offY + STATUS_TITLE_Y + STATUS_ROW_H,
                Graphics.TOP | Graphics.RIGHT);
        g.drawString(cachedTimeKillString, statusX, offY + STATUS_TITLE_Y + 2 * STATUS_ROW_H,
                Graphics.TOP | Graphics.RIGHT);
        g.drawString(cachedCoinsString, statusX, offY + STATUS_TITLE_Y + 3 * STATUS_ROW_H,
                Graphics.TOP | Graphics.RIGHT);

        // Fans line (Time Mode only), parked below the status block so the two never overlap.
        if (mode.completion == ContentCatalog.COMPLETE_KILL_TARGET) {
            if (cachedFansString == null || kills != lastFansKills) {
                cachedFansString = "Fans " + kills + "/" + mode.targetKills;
                lastFansKills = kills;
            }
            g.drawString(cachedFansString, statusX, offY + FANS_LINE_Y, Graphics.TOP | Graphics.RIGHT);
        }

        // Boss HP bar and name. The bar is centred on the visible playfield, the same axis as the
        // HCENTER name text, so both stay aligned and stay on screen for any view size.
        if (boss.active) {
            int bossBarX = offX + (viewWidth - BOSS_BAR_W) / 2;
            g.setColor(40, 0, 0); g.fillRect(bossBarX, offY + 92, BOSS_BAR_W, BOSS_BAR_H);
            g.setColor(220, 30, 30);
            g.fillRect(bossBarX + BOSS_BAR_INSET, offY + 95,
                    (int) ((BOSS_BAR_W - 2 * BOSS_BAR_INSET) * MathUtil.clamp((double) boss.hp / (double) boss.maxHp, 0.0, 1.0)),
                    BOSS_BAR_H - 2 * BOSS_BAR_INSET);
            g.setColor(255, 255, 255);
            if (cachedBossName == null || !bossName().equals(lastBossName)) {
                cachedBossName = bossName();
                lastBossName = cachedBossName;
            }
            g.drawString(cachedBossName, offX + viewWidth / 2, offY + 96, Graphics.TOP | Graphics.HCENTER);
        }

        // Controls hint (constant string, no cache needed but kept for consistency)
        g.setColor(200, 200, 200);
        g.drawString(cachedControlsHint, offX + viewWidth - 105, offY + viewHeight - 14, Graphics.TOP | Graphics.LEFT);
    }

    /**
     * Draws one 11x11 HUD slot. An owned cell carries a neutral 9x9 marker; an empty cell stays
     * dark. No weapon- or skill-specific artwork is drawn: every available sprite is either a
     * generic projectile shared by several weapons or too large for the 9px interior, and MIDP
     * cannot scale images. Level data stays in weaponLevels and is not faked on screen.
     */
    private void drawHudSlot(Graphics g, int sx, int sy, boolean owned) {
        g.setColor(owned ? SLOT_FILL : SLOT_FILL_EMPTY);
        g.fillRect(sx, sy, SLOT_W, SLOT_H);
        g.setColor(SLOT_BORDER);
        g.drawRect(sx, sy, SLOT_W - 1, SLOT_H - 1);
        if (!owned) return;
        g.setColor(SLOT_MARK, SLOT_MARK_G, SLOT_MARK_B);
        g.fillRect(sx + SLOT_INSET, sy + SLOT_INSET, SLOT_W - 2 * SLOT_INSET, SLOT_H - 2 * SLOT_INSET);
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
    }
    private static final class Shot {
        boolean active; boolean fromPlayer; boolean attached; boolean returning; boolean critical;
        int pattern; int weaponIndex; int damage; int life; int age; int dx; int dy; int x; int y;
        int area; int knockback; int target; long hitMask; boolean bossHit; double orbitAngle; String sprite;
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
    }
    private static final class Offer {
        int type = -1; int value; int existing; boolean newItem; boolean collab;
        void clear() { type = -1; value = 0; existing = -1; newItem = false; collab = false; }
    }
}
